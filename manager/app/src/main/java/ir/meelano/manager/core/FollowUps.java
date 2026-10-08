package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Customer follow-up notes with reminder dates (stored locally, keyed by customer code). */
public final class FollowUps {
    private FollowUps() { }

    public static final class Note {
        /** Jalali «YYYY/MM/DD» reminder date. */
        public final String date;
        /** Customer name snapshot (for the home alert list). */
        public final String name;
        public final String text;

        public Note(String date, String name, String text) {
            this.date = date == null ? "" : date;
            this.name = name == null ? "" : name;
            this.text = text == null ? "" : text;
        }
    }

    public static final class Due {
        public final String code;
        public final Note note;

        Due(String code, Note note) {
            this.code = code;
            this.note = note;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_follow7", Context.MODE_PRIVATE);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "");
    }

    private static String unesc(String s) {
        if (s == null) return "";
        return s.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%");
    }

    private static String key(String code) {
        return "c_" + (code == null ? "" : code.trim());
    }

    public static List<Note> list(Context c, String code) {
        List<Note> out = new ArrayList<>();
        try {
            String raw = prefs(c).getString(key(code), "");
            if (raw == null || raw.isEmpty()) return out;
            for (String line : raw.split("\n")) {
                if (line.isEmpty()) continue;
                String[] p = line.split("\\|", 3);
                if (p.length < 3) continue;
                String date = Jalali.disp(p[0]);
                if (date.isEmpty()) continue;
                out.add(new Note(date, unesc(p[1]), unesc(p[2])));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static void add(Context c, String code, String date, String name, String text) {
        try {
            List<Note> all = list(c, code);
            String d = Jalali.disp(date);
            all.add(new Note(d.isEmpty() ? Jalali.todayStr() : d, name, text));
            StringBuilder b = new StringBuilder();
            for (Note n : all) {
                if (b.length() > 0) b.append('\n');
                b.append(n.date).append('|').append(esc(n.name)).append('|').append(esc(n.text));
            }
            prefs(c).edit().putString(key(code), b.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static void remove(Context c, String code, int idx) {
        try {
            List<Note> all = list(c, code);
            if (idx < 0 || idx >= all.size()) return;
            all.remove(idx);
            if (all.isEmpty()) {
                prefs(c).edit().remove(key(code)).apply();
                return;
            }
            StringBuilder b = new StringBuilder();
            for (Note n : all) {
                if (b.length() > 0) b.append('\n');
                b.append(n.date).append('|').append(esc(n.name)).append('|').append(esc(n.text));
            }
            prefs(c).edit().putString(key(code), b.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Notes due today or overdue, oldest first. */
    public static List<Due> dueToday(Context c) {
        List<Due> out = new ArrayList<>();
        try {
            String today = Jalali.todayStr();
            Map<String, ?> all = prefs(c).getAll();
            if (all == null) return out;
            for (String k : all.keySet()) {
                if (k == null || !k.startsWith("c_")) continue;
                String code = k.substring(2);
                for (Note n : list(c, code)) {
                    if (!n.date.isEmpty() && n.date.compareTo(today) <= 0) out.add(new Due(code, n));
                }
            }
            Collections.sort(out, new Comparator<Due>() {
                @Override
                public int compare(Due a, Due b) {
                    return a.note.date.compareTo(b.note.date);
                }
            });
        } catch (Exception ignored) { }
        return out;
    }
}
