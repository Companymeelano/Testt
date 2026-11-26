package ir.meelano.manager.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * نگهداری موجودی لحظه‌ای هر حساب بانکی (ورود دستی کاربر).
 * مبلغ به ریال ذخیره می‌شود و با SharedPreferences باقی می‌ماند.
 */
public final class BankBalanceStore {
    private static final String PREFS = "meelano_bank_balances_v1";

    private BankBalanceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** موجودی ذخیره‌شده برای یک بانک (RDF بانک) — 0 اگر ثبت نشده. */
    public static long getBalanceRial(Context c, String bankRdf) {
        if (bankRdf == null) return 0L;
        String key = "bal_" + bankRdf.trim();
        try { return prefs(c).getLong(key, 0L); } catch (Exception e) { return 0L; }
    }

    public static void setBalanceRial(Context c, String bankRdf, long rial) {
        if (bankRdf == null) return;
        String key = "bal_" + bankRdf.trim();
        prefs(c).edit().putLong(key, rial).putLong(key + "_ts", System.currentTimeMillis()).apply();
    }

    /** زمان آخرین ویرایش (میلی‌ثانیه) — 0 اگر هرگز ثبت نشده. */
    public static long getBalanceTime(Context c, String bankRdf) {
        if (bankRdf == null) return 0L;
        String key = "bal_" + bankRdf.trim() + "_ts";
        try { return prefs(c).getLong(key, 0L); } catch (Exception e) { return 0L; }
    }

    /** همه موجودی‌های ذخیره‌شده. */
    public static Map<String, Long> getAll(Context c) {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("bal_") && !k.endsWith("_ts")) {
                String rdf = k.substring(4);
                if (e.getValue() instanceof Long) out.put(rdf, (Long) e.getValue());
                else try { out.put(rdf, Long.parseLong(String.valueOf(e.getValue()))); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public static void clear(Context c, String bankRdf) {
        if (bankRdf == null) return;
        String key = "bal_" + bankRdf.trim();
        prefs(c).edit().remove(key).remove(key + "_ts").apply();
    }

    public static void clearAll(Context c) {
        prefs(c).edit().clear().apply();
    }
}
