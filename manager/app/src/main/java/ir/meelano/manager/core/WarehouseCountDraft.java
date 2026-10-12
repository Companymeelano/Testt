package ir.meelano.manager.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** JSON codec for the in-progress warehouse count session kept in private app storage. */
public final class WarehouseCountDraft {
    public static final String PREFS = "meelano_wh_count_session";
    public static final String KEY = "active_v1";
    private static final int VERSION = 1;

    private WarehouseCountDraft() { }

    public static final class State {
        public final String day;
        public final String anbarId;
        public final String anbarName;
        public final JSONArray lines;
        public final long updatedAt;

        private State(String day, String anbarId, String anbarName, JSONArray lines, long updatedAt) {
            this.day = day;
            this.anbarId = anbarId;
            this.anbarName = anbarName;
            this.lines = lines;
            this.updatedAt = updatedAt;
        }

        public boolean hasWork() {
            return (lines != null && lines.length() > 0)
                    || !anbarId.isEmpty() || !anbarName.isEmpty();
        }
    }

    /** Encode a defensive copy; invalid/empty dates fall back through Jalali's normalizer. */
    public static String encode(String day, String anbarId, String anbarName,
            JSONArray lines, long updatedAt) {
        try {
            JSONObject o = new JSONObject();
            o.put("schemaVersion", VERSION);
            o.put("day", Jalali.normalizeDate(day));
            o.put("anbarId", safe(anbarId));
            o.put("anbarName", safe(anbarName));
            o.put("lines", copy(lines));
            o.put("updatedAt", Math.max(0L, updatedAt));
            return o.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Corrupt or unsupported local JSON is treated as an empty draft, never as a crash. */
    public static State decode(String raw, String fallbackDay) {
        String fallback = Jalali.normalizeDate(fallbackDay);
        try {
            if (raw == null || raw.trim().isEmpty()) return empty(fallback);
            JSONObject o = new JSONObject(raw);
            int version = o.optInt("schemaVersion", VERSION);
            if (version != VERSION) return empty(fallback);
            JSONArray arr = o.optJSONArray("lines");
            return new State(
                    Jalali.normalizeDate(o.optString("day", fallback)),
                    safe(o.optString("anbarId", "")),
                    safe(o.optString("anbarName", "")),
                    copy(arr),
                    Math.max(0L, o.optLong("updatedAt", 0L)));
        } catch (Exception ignored) {
            return empty(fallback);
        }
    }

    private static State empty(String day) {
        return new State(Jalali.normalizeDate(day), "", "", new JSONArray(), 0L);
    }

    private static JSONArray copy(JSONArray source) {
        if (source == null) return new JSONArray();
        try {
            return new JSONArray(source.toString());
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
