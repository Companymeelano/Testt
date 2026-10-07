package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** «Midnight Gold» luxury theme: tokens, fonts and drawable factories. */
public final class Theme {
    private Theme() { }

    public static final int BG = 0xFF0C1220;
    public static final int SURFACE = 0xFF151D31;
    public static final int SURFACE2 = 0xFF1D2742;
    public static final int GOLD = 0xFFD9AE5A;
    public static final int GOLD_SOFT = 0xFFF1D493;
    public static final int TEXT = 0xFFF4EFE4;
    public static final int MUTED = 0xFF9AA3B5;
    public static final int SUCCESS = 0xFF35C26E;
    public static final int DANGER = 0xFFE5484D;
    public static final int WARNING = 0xFFF5A524;
    public static final int INFO = 0xFF4FA3FF;
    public static final int VIOLET = 0xFF9A7BFF;

    private static float density = 3f;
    private static Typeface regular;
    private static Typeface bold;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Regular.ttf");
            bold = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Bold.ttf");
        } catch (Exception ignored) {
            regular = Typeface.DEFAULT;
            bold = Typeface.DEFAULT_BOLD;
        }
    }

    public static int dp(float v) {
        return Math.round(v * density);
    }

    public static Typeface face(boolean isBold) {
        if (isBold) return bold == null ? Typeface.DEFAULT_BOLD : bold;
        return regular == null ? Typeface.DEFAULT : regular;
    }

    public static int alpha(int color, int a) {
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(color), Color.green(color), Color.blue(color));
    }

    // ---------------- drawable factories ----------------
    private static GradientDrawable base(int fill, float radiusDp, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** Standard glass card. */
    public static GradientDrawable card() {
        return base(SURFACE, 20, dp(1), alpha(GOLD, 40));
    }

    /** Card with an accent glow edge. */
    public static GradientDrawable cardAccent(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(accent, 46), alpha(accent, 14), SURFACE});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), alpha(accent, 90));
        return d;
    }

    /** Hero header background. */
    public static GradientDrawable hero(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{alpha(accent, 70), alpha(accent, 22), alpha(SURFACE, 255)});
        d.setCornerRadius(dp(24));
        d.setStroke(dp(1), alpha(accent, 110));
        return d;
    }

    /** KPI tile background. */
    public static GradientDrawable kpi(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(accent, 34), alpha(SURFACE, 255)});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(accent, 70));
        return d;
    }

    public static GradientDrawable goldButton() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFB9822F, GOLD, GOLD_SOFT});
        d.setCornerRadius(dp(16));
        return d;
    }

    public static GradientDrawable ghostButton(int accent) {
        return base(alpha(accent, 26), 16, dp(1), alpha(accent, 110));
    }

    public static GradientDrawable chip(boolean selected, int accent) {
        if (selected) {
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{alpha(accent, 220), alpha(accent, 160)});
            d.setCornerRadius(dp(999));
            return d;
        }
        return base(alpha(SURFACE2, 255), 999, dp(1), alpha(accent, 70));
    }

    public static GradientDrawable pill(int color) {
        return base(alpha(color, 30), 999, dp(1), alpha(color, 120));
    }

    public static GradientDrawable searchBar() {
        return base(SURFACE2, 16, dp(1), alpha(GOLD, 46));
    }

    public static GradientDrawable avatar(int color) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, alpha(color, 140)});
        d.setCornerRadius(dp(999));
        return d;
    }

    public static GradientDrawable bottomBar() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF101828, 0xFF0C1220});
        d.setStroke(dp(1), alpha(GOLD, 44));
        return d;
    }

    public static GradientDrawable dialogBg() {
        GradientDrawable d = base(SURFACE, 26, dp(1), alpha(GOLD, 80));
        return d;
    }

    public static GradientDrawable tableHeader() {
        return base(alpha(GOLD, 30), 12, 0, 0);
    }

    public static void pressable(View v) {
        v.setClickable(true);
        v.setFocusable(true);
    }
}
