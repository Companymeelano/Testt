package ir.meelano.manager.core;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import ir.meelano.licensing.License;

/** Stable per-phone identity that licenses are bound to. */
public final class DeviceId {
    private DeviceId() { }

    public static String fingerprint(Context c) {
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) { }
        String fp = "";
        String board = "";
        try {
            fp = Build.FINGERPRINT;
            board = Build.BOARD;
        } catch (Exception ignored) { }
        return (androidId == null ? "" : androidId) + "|" + fp + "|" + board + "|meelano7";
    }

    /** 8-char code, e.g. «QFDVSZXP». */
    public static String code(Context c) {
        return License.deviceCode(fingerprint(c));
    }

    /** Display form: «QFDV-SZXP». */
    public static String display(Context c) {
        String d = code(c);
        return d.length() == 8 ? d.substring(0, 4) + "-" + d.substring(4) : d;
    }
}
