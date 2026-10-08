package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import ir.meelano.licensing.License;

/**
 * Client-side license vault: stores the activated pack and re-validates it on
 * every launch / resume (signature + device binding + expiry + clock-tamper
 * + optional signature pinning).
 */
public final class LicenseStore {
    private LicenseStore() { }

    public static final class Status {
        public boolean ok;
        /** none | bad | device | expired | clock | pin */
        public String reason = "none";
        public char plan = License.P_TRIAL;
        public long expDay;
        public long daysLeft;
        public String fa = "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_license", Context.MODE_PRIVATE);
    }

    public static boolean unlocked(Context c) {
        try {
            return check(c).ok;
        } catch (Exception e) {
            return false;
        }
    }

    public static Status check(Context c) {
        Status s = new Status();
        String pack;
        long lastSeen;
        try {
            pack = prefs(c).getString("pack", "");
            lastSeen = prefs(c).getLong("last_seen", 0);
        } catch (Exception e) {
            s.reason = "bad";
            s.fa = "خطا در خواندن لایسنس";
            return s;
        }
        if (pack == null || pack.isEmpty()) {
            s.reason = "none";
            s.fa = "برنامه فعال نشده است";
            return s;
        }
        License.Result r = License.parse(pack);
        if (!r.ok) {
            s.reason = "bad";
            s.fa = r.errFa;
            return s;
        }
        String dev;
        try {
            dev = DeviceId.code(c);
        } catch (Exception e) {
            dev = "";
        }
        if (!r.dev.equals(dev)) {
            s.reason = "device";
            s.fa = "این کد متعلق به گوشی دیگری است";
            return s;
        }
        long today = License.today();
        if (today < r.iatDay - 1) {
            s.reason = "clock";
            s.fa = "ساعت گوشی دستکاری شده است";
            return s;
        }
        if (today > r.expDay) {
            s.reason = "expired";
            s.plan = r.plan;
            s.expDay = r.expDay;
            s.fa = "اعتبار لایسنس تمام شده است";
            return s;
        }
        if (lastSeen > 0 && today < lastSeen) {
            s.reason = "clock";
            s.fa = "برگرداندن ساعت گوشی شناسایی شد";
            return s;
        }
        if (!Tamper.signatureOk(c)) {
            s.reason = "pin";
            s.fa = "امضای برنامه معتبر نیست (نصب مجدد از فروشنده)";
            return s;
        }
        try {
            prefs(c).edit().putLong("last_seen", today).apply();
        } catch (Exception ignored) { }
        s.ok = true;
        s.plan = r.plan;
        s.expDay = r.expDay;
        s.daysLeft = r.expDay - today;
        s.fa = r.plan == License.P_PERM ? "دائمی" : ("«" + License.planFa(r.plan) + "»");
        return s;
    }

    /** Validate a typed/pasted pack and, when good, store it. Returns the fresh status. */
    public static Status activate(Context c, String packInput) {
        Status s = new Status();
        License.Result r = License.parse(packInput == null ? "" : packInput);
        if (!r.ok) {
            s.reason = "bad";
            s.fa = r.errFa;
            return s;
        }
        String dev;
        try {
            dev = DeviceId.code(c);
        } catch (Exception e) {
            dev = "";
        }
        if (!r.dev.equals(dev)) {
            s.reason = "device";
            s.fa = "این کد متعلق به گوشی دیگری است";
            return s;
        }
        long today = License.today();
        if (today > r.expDay) {
            s.reason = "expired";
            s.fa = "این کد منقضی شده است؛ از فروشنده کد تازه بگیرید";
            return s;
        }
        if (today < r.iatDay - 1) {
            s.reason = "clock";
            s.fa = "ساعت گوشی درست نیست";
            return s;
        }
        try {
            prefs(c).edit().putString("pack", r.pack)
                    .putString("dev", r.dev)
                    .putLong("exp", r.expDay)
                    .putLong("last_seen", today).apply();
        } catch (Exception e) {
            s.reason = "bad";
            s.fa = "ذخیره لایسنس ممکن نشد";
            return s;
        }
        return check(c);
    }

    public static void clear(Context c) {
        try {
            prefs(c).edit().clear().apply();
        } catch (Exception ignored) { }
    }

    /** Remaining seller-contact, remembered across requests. */
    public static String sellerPhone(Context c) {
        try {
            return prefs(c).getString("seller_phone", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static void setSellerPhone(Context c, String phone) {
        try {
            prefs(c).edit().putString("seller_phone", phone == null ? "" : phone.trim()).apply();
        } catch (Exception ignored) { }
    }
}
