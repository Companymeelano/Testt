package ir.meelano.android;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * دفترچهٔ محلی «تایید سامانهٔ چک‌ها» در اپ یکپارچه — ثبت مستقیم داخل برنامه.
 *
 * <p>یک کلید تنظیمات، آبجکتی از «کلید چک → مهرزمان|کاربر|یادداشت» را نگه می‌دارد؛ بدون شبکه
 * کار می‌کند و هیچ نوشتنی روی دیتابیس میدان انجام نمی‌دهد.</p>
 */
final class MeelanoChequeRegistry {
    private static final String KEY = "cheque_approval_registry_v1";

    private MeelanoChequeRegistry() { }

    static Map<String, MeelanoChequePlan.Entry> load(MeelanoUiHost h) {
        Map<String, MeelanoChequePlan.Entry> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(h.setting(KEY, "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = o.optString(k, "");
                int i = v.indexOf('|');
                int j = v.indexOf('|', i + 1);
                if (i <= 0 || j <= i) continue;
                out.put(k, new MeelanoChequePlan.Entry(Long.parseLong(v.substring(0, i)),
                        v.substring(i + 1, j), v.substring(j + 1)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void confirm(MeelanoUiHost h, String key, String user, String note) {
        if (key == null || key.isEmpty()) return;
        Map<String, MeelanoChequePlan.Entry> all = load(h);
        all.put(key, new MeelanoChequePlan.Entry(System.currentTimeMillis(), user, note));
        h.putSetting(KEY, encode(all));
    }

    static void revoke(MeelanoUiHost h, String key) {
        if (key == null || key.isEmpty()) return;
        Map<String, MeelanoChequePlan.Entry> all = load(h);
        all.remove(key);
        h.putSetting(KEY, encode(all));
    }

    private static String encode(Map<String, MeelanoChequePlan.Entry> all) {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, MeelanoChequePlan.Entry> e : all.entrySet()) {
            String user = e.getValue().user.replace('|', ' ');
            String note = e.getValue().note.replace('|', ' ').replace('\n', ' ');
            try {
                o.put(e.getKey(), e.getValue().ts + "|" + user + "|" + note);
            } catch (Exception ignored) { }
        }
        return o.toString();
    }
}
