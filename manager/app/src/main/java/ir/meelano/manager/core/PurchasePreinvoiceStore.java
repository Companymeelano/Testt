package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * دفترچهٔ محلی پیش‌فاکتورهای خرید — ثبت مستقیم داخل برنامه.
 *
 * <p>هر پیش‌فاکتور به‌صورت یک رشتهٔ فشرده ({@link PurchasePreinvoice#pack}) زیر کلید خودش
 * نگه داشته می‌شود؛ بدون شبکه کار می‌کند و هیچ نوشتنی به دیتابیس میدان نمی‌فرستد.</p>
 */
public final class PurchasePreinvoiceStore {

    private static final String PREFS = "meelano_purchase_preinvoice";
    private static final String INDEX = "__index";

    private PurchasePreinvoiceStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<PurchasePreinvoice.Draft> load(Context c) {
        SharedPreferences p = prefs(c);
        List<PurchasePreinvoice.Draft> out = new ArrayList<>();
        for (String key : p.getString(INDEX, "").split(",")) {
            if (key.isEmpty()) continue;
            PurchasePreinvoice.Draft d = PurchasePreinvoice.unpack(p.getString(key, null));
            if (d != null) out.add(d);
        }
        Collections.sort(out, new Comparator<PurchasePreinvoice.Draft>() {
            @Override public int compare(PurchasePreinvoice.Draft a, PurchasePreinvoice.Draft b) {
                return Long.compare(b.savedAt, a.savedAt);
            }
        });
        return out;
    }

    public static void save(Context c, PurchasePreinvoice.Draft d) {
        if (d.id == null || d.id.isEmpty()) d.id = "pi" + System.currentTimeMillis();
        d.savedAt = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        StringBuilder index = new StringBuilder(p.getString(INDEX, ""));
        if (index.length() == 0 || !("," + index + ",").contains("," + d.id + ",")) {
            if (index.length() > 0) index.append(',');
            index.append(d.id);
        }
        p.edit().putString(d.id, PurchasePreinvoice.pack(d)).putString(INDEX, index.toString()).apply();
    }

    public static void delete(Context c, String id) {
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
