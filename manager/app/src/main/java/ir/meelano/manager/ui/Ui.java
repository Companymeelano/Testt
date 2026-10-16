package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
import android.content.Context;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Small responsive / motion / accessibility helpers (v34). */
public final class Ui {
    private Ui() {
    }

    /** Screen width in dp (fallback 360). */
    public static int widthDp(Context c) {
        try {
            DisplayMetrics m = c.getResources().getDisplayMetrics();
            if (m != null && m.density > 0) return (int) (m.widthPixels / m.density);
        } catch (Exception ignored) { Diag.ignored("Ui:18", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Ui:38", ignored); }
    }

    /**
     * Physical feedback for a completed action (save, submit, signature done). Uses the
     * system CONFIRM / REJECT effects on Android 11+ and a tap otherwise. Respects the
     * user's haptics setting through the system: nothing is forced when vibration is off.
     */
    public static void haptic(View v, boolean success) {
        if (v == null) return;
        try {
            int effect = HapticFeedbackConstants.KEYBOARD_TAP;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                effect = success ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT;
            } else if (!success) {
                effect = HapticFeedbackConstants.LONG_PRESS;
            }
            v.performHapticFeedback(effect);
        } catch (Exception ignored) { Diag.ignored("Ui:haptic", ignored); }
    }

    /**
     * Staggered reveal of the first cards of a freshly opened screen: each child rises and
     * fades in a few milliseconds after the previous one. Skipped when animations are off.
     */
    public static void reveal(ViewGroup g) {
        if (g == null || !motionOk(g.getContext())) return;
        try {
            int n = Math.min(g.getChildCount(), 10);
            float lift = g.getResources().getDisplayMetrics().density * 16f;
            for (int i = 0; i < n; i++) {
                View c = g.getChildAt(i);
                c.animate().cancel();
                c.setAlpha(0f);
                c.setTranslationY(lift);
                c.animate().alpha(1f).translationY(0f).setStartDelay(i * 42L)
                        .setDuration(340).setInterpolator(new DecelerateInterpolator(1.4f)).start();
            }
        } catch (Exception ignored) { Diag.ignored("Ui:reveal", ignored); }
    }
}
