package ir.meelano.manager.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;

/**
 * Lightweight anti-tamper signals for the licensed product.
 * Root = soft warning only (never blocks). Signature pinning = hard block,
 * but ONLY after the seller sets APP_SIG_PIN (see the licensing docs).
 */
public final class Tamper {
    private Tamper() { }

    /**
     * Expected app-signature hash (first 16 hex chars, uppercase). Empty =
     * pinning disabled (lets the seller test debug builds). After signing the
     * production APK, read the hash from the license screen footer and paste
     * it here, then rebuild both apps.
     */
    public static final String APP_SIG_PIN = "";

    /** True on rooted / test-keys phones. Never throws. */
    public static boolean isRooted() {
        try {
            String tags = Build.TAGS;
            if (tags != null && tags.contains("test-keys")) return true;
        } catch (Exception ignored) { }
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/data/local/su", "/system/app/Superuser.apk", "/system/etc/.installed_su_daemon",
        };
        try {
            for (String p : paths) if (new File(p).exists()) return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** First 16 hex chars of the SHA-256 of the signing certificate ("" when unknown). */
    public static String appSignatureHash(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            String pkg = c.getPackageName();
            Signature[] sigs = null;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi != null && pi.signingInfo != null) sigs = pi.signingInfo.getApkContentsSigners();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                if (pi != null) sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0 || sigs[0] == null) return "";
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(sigs[0].toByteArray());
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(8, h.length); i++) b.append(String.format("%02X", h[i]));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** False only when pinning is configured AND the signature does not match. */
    public static boolean signatureOk(Context c) {
        if (APP_SIG_PIN == null || APP_SIG_PIN.isEmpty()) return true;
        String h = appSignatureHash(c);
        return !h.isEmpty() && h.equalsIgnoreCase(APP_SIG_PIN);
    }
}
