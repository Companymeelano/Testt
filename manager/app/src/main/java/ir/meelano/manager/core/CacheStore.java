package ir.meelano.manager.core;

import android.content.Context;

import ir.meelano.manager.data.Row;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk cache of screen data (one small file per screen part) for the offline
 * mode: when a query fails, the screen rebuilds from the last successful load
 * and shows an «offline» banner instead of a dead error page.
 */
public final class CacheStore {
    private CacheStore() { }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "mcache");
        try {
            if (!d.exists()) d.mkdirs();
        } catch (Exception ignored) { }
        return d;
    }

    private static String fn(String key) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            b.append(Character.isLetterOrDigit(ch) ? ch : '_');
        }
        return b + ".txt";
    }

    /** Save a whole screen state: values may be Row / List<Row> / Number / String. */
    public static void saveData(Context c, String prefix, String label, Map<String, Object> parts) {
        try {
            if (parts == null || parts.isEmpty()) return;
            StringBuilder idx = new StringBuilder();
            idx.append(label == null ? "" : label.replace("\n", " ")).append('\n');
            for (Map.Entry<String, Object> e : parts.entrySet()) {
                String name = e.getKey();
                Object v = e.getValue();
                if (v instanceof Row) {
                    write(new File(dir(c), fn(prefix + "_" + name)), rowStr((Row) v));
                    idx.append("r ").append(name).append('\n');
                } else if (v instanceof List) {
                    StringBuilder b = new StringBuilder();
                    List<?> rows = (List<?>) v;
                    int cap = Math.min(rows.size(), 400);
                    for (int i = 0; i < cap; i++) {
                        Object o = rows.get(i);
                        if (o instanceof Row) b.append(rowStr((Row) o)).append('\n');
                    }
                    write(new File(dir(c), fn(prefix + "_" + name)), b.toString());
                    idx.append("l ").append(name).append('\n');
                } else if (v instanceof Number || v instanceof String) {
                    Row r = new Row();
                    r.put("v", String.valueOf(v));
                    write(new File(dir(c), fn(prefix + "_" + name)), rowStr(r));
                    idx.append("r ").append(name).append('\n');
                }
            }
            write(new File(dir(c), fn(prefix + "_index")), idx.toString());
        } catch (Exception ignored) { }
    }

    /** Load a screen state saved with {@link #saveData} (null when nothing cached). */
    public static Map<String, Object> loadData(Context c, String prefix, String[] labelOut) {
        try {
            String idx = read(new File(dir(c), fn(prefix + "_index")));
            if (idx == null || idx.isEmpty()) return null;
            String[] lines = idx.split("\n");
            if (lines.length == 0) return null;
            if (labelOut != null && labelOut.length > 0) labelOut[0] = lines[0];
            Map<String, Object> out = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String ln = lines[i];
                if (ln.length() < 3) continue;
                char t = ln.charAt(0);
                String name = ln.substring(2);
                String body = read(new File(dir(c), fn(prefix + "_" + name)));
                if (body == null) continue;
                if (t == 'l') {
                    List<Row> rows = new ArrayList<>();
                    for (String rl : body.split("\n")) {
                        if (!rl.isEmpty()) rows.add(rowFrom(rl));
                    }
                    out.put(name, rows);
                } else {
                    out.put(name, rowFrom(body));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    public static Row row(Map<String, Object> m, String name) {
        try {
            Object o = m.get(name);
            return o instanceof Row ? (Row) o : new Row();
        } catch (Exception e) {
            return new Row();
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Row> rows(Map<String, Object> m, String name) {
        try {
            Object o = m.get(name);
            return o instanceof List ? (List<Row>) o : new ArrayList<Row>();
        } catch (Exception e) {
            return new ArrayList<Row>();
        }
    }

    public static long num(Map<String, Object> m, String name) {
        try {
            return row(m, name).l("v");
        } catch (Exception e) {
            return 0;
        }
    }

    private static String rowStr(Row r) {
        if (r == null) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (b.length() > 0) b.append(';');
            b.append(e.getKey()).append('=').append(esc(String.valueOf(e.getValue())));
        }
        return b.toString();
    }

    private static Row rowFrom(String s) {
        Row r = new Row();
        if (s == null || s.isEmpty()) return r;
        for (String p : s.split(";")) {
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            r.put(p.substring(0, eq), unesc(p.substring(eq + 1)));
        }
        return r;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D")
                .replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D");
    }

    private static String unesc(String s) {
        return s.replace("%0D", "\r").replace("%0A", "\n").replace("%7C", "|")
                .replace("%3D", "=").replace("%3B", ";").replace("%25", "%");
    }

    private static void write(File f, String s) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(s.getBytes("UTF-8"));
            } finally {
                try {
                    out.close();
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private static String read(File f) {
        try {
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                return new String(b.toByteArray(), "UTF-8");
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            return null;
        }
    }
}
