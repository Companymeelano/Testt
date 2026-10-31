package ir.meelano.manager.core;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import ir.meelano.licensing.License;

/**
 * Automatic re-activation after uninstall/reinstall.
 *
 * The signed license pack (M1…) is bound to the phone's own device code
 * (the serial identity read from the device settings — see {@link DeviceId})
 * and carries its own issue/expiry day numbers. A pack found again in the
 * SMS inbox therefore restores exactly the same license: the time already
 * spent still counts, the expiry day is never reset, and a pack minted for
 * another phone is ignored.
 *
 * Reading the inbox is used ONLY to spot M1… license tokens; no message is
 * stored, shown, or sent anywhere.
 */
public final class LicenseRecovery {
    private LicenseRecovery() { }

    /** First verifiable, device-bound M1… pack token in free text; null otherwise. */
    public static String packIn(String body, String dev) {
        try {
            if (body == null || body.isEmpty() || dev == null || dev.isEmpty()) return null;
            for (String tok : body.split("\\s+")) {
                String n = License.normalize(tok);
                if (n.length() == License.PACK_LEN && n.startsWith("M1")) {
                    License.Result r = License.parse(n);
                    if (r.ok && r.dev.equals(dev)) return r.pack;
                }
            }
        } catch (Exception ignored) { Diag.ignored("LicenseRecovery:packIn", ignored); }
        return null;
    }

    /**
     * Best device-bound pack in the SMS inbox (the one that expires last),
     * or "" when none. Newest messages are scanned first; the scan stops
     * after a bounded number of rows.
     */
    public static String findSmsPack(Context c, String dev) {
        String best = "";
        long bestExp = -1;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse("content://sms"),
                    new String[]{"body", "date"}, null, null, "date DESC");
            if (cur == null) return "";
            int scanned = 0;
            while (cur.moveToNext() && scanned < 400) {
                scanned++;
                String pack = packIn(cur.getString(0), dev);
                if (pack == null || pack.isEmpty()) continue;
                License.Result r = License.parse(pack);
                if (!r.ok) continue;
                if (r.expDay > bestExp) {
                    bestExp = r.expDay;
                    best = r.pack;
                }
            }
        } catch (Exception ignored) {
            Diag.ignored("LicenseRecovery:findSmsPack", ignored);
        } finally {
            try {
                if (cur != null) cur.close();
            } catch (Exception ignored) { Diag.ignored("LicenseRecovery:close", ignored); }
        }
        return best;
    }

    /**
     * One silent recovery attempt — only when the app is not activated and
     * the READ_SMS permission is already granted. Returns the restored pack
     * ("" when nothing was recoverable). Never throws.
     */
    public static String tryRecover(Context c) {
        try {
            if (c == null) return "";
            Context app = c.getApplicationContext();
            if (LicenseStore.check(app).ok) return "";
            if (!canReadSms(app)) return "";
            String dev = DeviceId.code(app);
            if (dev.isEmpty()) return "";
            String pack = findSmsPack(app, dev);
            if (pack.isEmpty()) return "";
            LicenseStore.Status s = LicenseStore.activate(app, pack);
            return s.ok ? pack : "";
        } catch (Exception ignored) {
            Diag.ignored("LicenseRecovery:tryRecover", ignored);
            return "";
        }
    }

    /** True when the inbox may be read (READ_SMS granted; pre-M is install-time). */
    public static boolean canReadSms(Context c) {
        try {
            return c.checkSelfPermission(android.Manifest.permission.READ_SMS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            Diag.ignored("LicenseRecovery:canReadSms", ignored);
            return true;
        }
    }
}
