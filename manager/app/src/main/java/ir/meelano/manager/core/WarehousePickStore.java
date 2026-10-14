package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Small durable local archive of prepared, scanned warehouse pick waves (not an Atiran write). */
public final class WarehousePickStore {
    private WarehousePickStore() { }
    private static final String PREFS = "meelano_wh_pick_waves";

    public static boolean save(Context c, JSONObject wave) {
        if (c == null || wave == null) return false;
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString("waves", "[]"));
            JSONArray next = new JSONArray();
            wave.put("savedAt", System.currentTimeMillis());
            next.put(wave);
            for (int i = 0; i < old.length() && next.length() < 50; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null) next.put(o);
            }
            p.edit().putString("waves", next.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<JSONObject> list(Context c) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("waves", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) { Diag.ignored("WarehousePickStore:45", ignored); }
        return out;
    }
}
