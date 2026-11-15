package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * دفترچهٔ محلی «تایید سامانهٔ چک‌ها» — ثبت مستقیم داخل برنامه.
 *
 * <p>ثبت تایید یک چک (صیادی/سامانه) روی دستگاه کاربر نگه داشته می‌شود: کلید چک → زمان ثبت،
 * کاربر ثبت‌کننده و یادداشت. بدون شبکه کار می‌کند، با حذف برنامه از بین نمی‌رود (backup rules
 * آن را پوشش می‌دهند) و هیچ نوشتنی روی دیتابیس میدان انجام نمی‌دهد — منبع دادهٔ مالی دست‌نخور
 * می‌ماند و فقط «وضعیت تایید» به‌صورت یک لایهٔ جداگانهٔ برنامه ثبت می‌شود.</p>
 */
public final class ChequeRegistry {

    private static final String PREFS = "meelano_cheque_approval_registry";

    private ChequeRegistry() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** خواندن کل دفترچه به‌صورت برنامهٔ گزارش‌پسند. */
    public static Map<String, ChequeApprovalPlan.Entry> load(Context c) {
        Map<String, ChequeApprovalPlan.Entry> out = new HashMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            String v = String.valueOf(e.getValue());
            int i = v.indexOf('|');
            int j = v.indexOf('|', i + 1);
            if (i <= 0 || j <= i) continue;
            long ts;
            try {
                ts = Long.parseLong(v.substring(0, i));
            } catch (NumberFormatException ex) {
                continue;
            }
            out.put(e.getKey(), new ChequeApprovalPlan.Entry(ts, v.substring(i + 1, j), v.substring(j + 1)));
        }
        return out;
    }

    /** ثبت تایید یک چک در سامانه (داخل برنامه)، با مهر زمان و نام کاربر نشست. */
    public static void confirm(Context c, String key, String user, String note) {
        if (key == null || key.isEmpty()) return;
        String safeUser = user == null ? "" : user.replace('|', ' ');
        String safeNote = note == null ? "" : note.replace('|', ' ').replace('\n', ' ');
        prefs(c).edit().putString(key,
                System.currentTimeMillis() + "|" + safeUser + "|" + safeNote).apply();
    }

    /** لغو یک تایید ثبت‌شده. */
    public static void revoke(Context c, String key) {
        if (key == null || key.isEmpty()) return;
        prefs(c).edit().remove(key).apply();
    }

    public static boolean has(Context c, String key) {
        return key != null && prefs(c).contains(key);
    }

    public static int size(Context c) {
        return prefs(c).getAll().size();
    }
}
