package ir.meelano.manager.ui;

import android.content.Context;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;

/** Small responsive / motion / accessibility helpers (v34). */
public final class Ui {
    private Ui() {
    }

    /** Screen width in dp (fallback 360). */
    public static int widthDp(Context c) {
        try {
            DisplayMetrics m = c.getResources().getDisplayMetrics();
            if (m != null && m.density > 0) return (int) (m.widthPixels / m.density);
        } catch (Exception ignored) {
        }
        return 360;
    }

    /** False when the user turned system animations off: decorative loops must freeze. */
    public static boolean motionOk(Context c) {
        try {
            float s = Settings.Global.getFloat(c.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return s != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    /** Screen-reader announcement (validation errors, copy result). */
    public static void announce(View v, String text) {
        try {
            if (v != null && text != null) v.announceForAccessibility(text);
        } catch (Exception ignored) {
        }
    }
}
