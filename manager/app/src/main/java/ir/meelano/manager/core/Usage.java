package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import ir.meelano.licensing.License;

/**
 * Local usage ledger: counts app opens and foreground minutes, remembers the
 * last-use day. The compact report rides along inside every license request
 * (and can be shared on demand), so the seller sees «last seen / total hours /
 * opens» in the admin app — the honest offline answer to «is my customer
 * using the app?». A single session is capped at 8 h so a forgotten
 * foreground screen can't inflate the numbers.
 */
public final class Usage {
    private Usage() { }

    private static final long SESS_CAP_MS = 8L * 3600 * 1000;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_usage", Context.MODE_PRIVATE);
    }

    /** Called once per MainActivity launch (after the license gate passes). */
    public static void opened(Context c) {
        try {
            SharedPreferences p = prefs(c);
            p.edit().putInt("opens", p.getInt("opens", 0) + 1)
                    .putLong("last_day", License.today())
                    .putLong("sess_start", 0)
                    .apply();
        } catch (Exception ignored) { }
    }

    /** Called from onResume: (re)starts the foreground timer. */
    public static void touch(Context c) {
        try {
            SharedPreferences p = prefs(c);
            if (p.getLong("sess_start", 0) <= 0) {
                p.edit().putLong("sess_start", System.currentTimeMillis()).apply();
            }
        } catch (Exception ignored) { }
    }

    /** Called from onPause: banks the foreground time of this session. */
    public static void paused(Context c) {
        try {
            SharedPreferences p = prefs(c);
            long start = p.getLong("sess_start", 0);
            if (start <= 0) return;
            long delta = System.currentTimeMillis() - start;
            if (delta < 0) delta = 0;
            if (delta > SESS_CAP_MS) delta = SESS_CAP_MS;
            p.edit().putLong("total_min", p.getLong("total_min", 0) + delta / 60000)
                    .putLong("last_day", License.today())
                    .putLong("sess_start", 0)
                    .apply();
        } catch (Exception ignored) { }
    }

    public static long totalMin(Context c) {
        try {
            return Math.max(0, prefs(c).getLong("total_min", 0));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int opens(Context c) {
        try {
            return Math.max(0, prefs(c).getInt("opens", 0));
        } catch (Exception e) {
            return 0;
        }
    }

    public static long lastDay(Context c) {
        try {
            long d = prefs(c).getLong("last_day", 0);
            return d <= 0 ? License.today() : d;
        } catch (Exception e) {
            return License.today();
        }
    }

    /** Report line for request messages / on-demand sharing. */
    public static String reportLine(Context c, String dev8) {
        try {
            return License.useLine(dev8, totalMin(c), opens(c), lastDay(c));
        } catch (Exception e) {
            return "";
        }
    }

    /** Friendly one-liner for the license screen: «۱۲ بار باز شدن • ۳ ساعت کار». */
    public static String faSummary(Context c) {
        try {
            long min = totalMin(c);
            int op = opens(c);
            String use = min < 60
                    ? Money.fa(String.valueOf(min)) + " دقیقه کار"
                    : Money.fa(String.valueOf(min / 60)) + " ساعت کار";
            return Money.fa(String.valueOf(op)) + " بار باز شدن • " + use;
        } catch (Exception e) {
            return "";
        }
    }
}
