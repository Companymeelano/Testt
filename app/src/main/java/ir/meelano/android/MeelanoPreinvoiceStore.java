package ir.meelano.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** دفترچهٔ محلی پیش‌فاکتورهای خرید نسخهٔ یکپارچه — بدون نوشتن روی دیتابیس میدان. */
final class MeelanoPreinvoiceStore {

    private static final String PREFS = "meelano_purchase_preinvoice";
    private static final String INDEX = "__index";

    private MeelanoPreinvoiceStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<MeelanoPreinvoice.Draft> load(Context c) {
        SharedPreferences p = prefs(c);
        List<MeelanoPreinvoice.Draft> out = new ArrayList<>();
        for (String key : p.getString(INDEX, "").split(",")) {
            if (key.isEmpty()) continue;
            MeelanoPreinvoice.Draft d = MeelanoPreinvoice.unpack(p.getString(key, null));
            if (d != null) out.add(d);
        }
        Collections.sort(out, new Comparator<MeelanoPreinvoice.Draft>() {
            @Override public int compare(MeelanoPreinvoice.Draft a, MeelanoPreinvoice.Draft b) {
                return Long.compare(b.savedAt, a.savedAt);
            }
        });
        return out;
    }

    static void save(Context c, MeelanoPreinvoice.Draft d) {
        if (d.id == null || d.id.isEmpty()) d.id = "pi" + System.currentTimeMillis();
        d.savedAt = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        StringBuilder index = new StringBuilder(p.getString(INDEX, ""));
        if (index.length() == 0 || !("," + index + ",").contains("," + d.id + ",")) {
            if (index.length() > 0) index.append(',');
            index.append(d.id);
        }
        p.edit().putString(d.id, MeelanoPreinvoice.pack(d)).putString(INDEX, index.toString()).apply();
    }

    static void delete(Context c, String id) {
        SharedPreferences p = prefs(c);
        StringBuilder index = new StringBuilder();
        for (String key : p.getString(INDEX, "").split(",")) {
            if (key.isEmpty() || key.equals(id)) continue;
            if (index.length() > 0) index.append(',');
            index.append(key);
        }
        p.edit().remove(id).putString(INDEX, index.toString()).apply();
    }
}
