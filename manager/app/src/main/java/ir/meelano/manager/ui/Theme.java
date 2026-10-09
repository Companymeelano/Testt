package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Settings;

/**
 * Luxury theme engine: two skins («Midnight Gold» dark, «Ivory Royal» light)
 * × six accent colors, applied to the WHOLE app at runtime.
 *
 * Colors are plain static fields (not constants) on purpose: every screen,
 * chart and drawable reads them fresh on each render, so switching theme +
 * re-render is all it takes. GOLD / GOLD_SOFT always mean «the current
 * accent», whatever the user picked.
 */
public final class Theme {
    private Theme() { }

    // ---------------- dynamic palette (set by apply) ----------------
    public static int BG = 0xFF0C1220;
    public static int SURFACE = 0xFF151D31;
    public static int SURFACE2 = 0xFF1D2742;
    /** Current accent (gold by default). */
    public static int GOLD = 0xFFD9AE5A;
    /** Current accent, soft variant (readable on the background). */
    public static int GOLD_SOFT = 0xFFF1D493;
    public static int TEXT = 0xFFF4EFE4;
    public static int MUTED = 0xFF9AA3B5;
    public static int SUCCESS = 0xFF35C26E;
    public static int DANGER = 0xFFE5484D;
    public static int WARNING = 0xFFF5A524;
    public static int INFO = 0xFF4FA3FF;
    public static int VIOLET = 0xFF9A7BFF;
    public static int TEAL = 0xFF3ED6C5;
    public static int STEEL = 0xFF7C8DA6;

    private static boolean light = false;
    /** Mirror of !light for call sites (charts, tables). */
    public static boolean DARK = true;
    private static String accentKey = "gold";
    private static float fontScale = 1f;
    private static float density = 3f;
    private static Typeface regular;
    private static Typeface bold;

    /** Accent: key, Persian label, dark, darkSoft, light, lightSoft. */
    private static final String[][] ACCENTS = {
            {"gold", "طلایی", "FFD9AE5A", "FFF1D493", "FFA86F14", "FF7A5410"},
            {"emerald", "زمردی", "FF3ED598", "FF9BF0C8", "FF0E9F6E", "FF046C4E"},
            {"sapphire", "یاقوت آبی", "FF4FA3FF", "FFB3D4FF", "FF1C64F2", "FF1E429F"},
            {"ruby", "یاقوتی", "FFF2617A", "FFFFB3C0", "FFD61F4A", "FF8F0F2E"},
            {"violet", "بنفش سلطنتی", "FF9A7BFF", "FFCDBFFF", "FF7C3AED", "FF5B21B6"},
            {"teal", "فیروزه‌ای", "FF3ED6C5", "FFA7F3EA", "FF0E9E8F", "FF065F56"},
    };

    public static String[][] accents() { return ACCENTS; }

    public static String accentLabel(String key) {
        for (String[] a : ACCENTS) if (a[0].equals(key)) return a[1];
        return ACCENTS[0][1];
    }

    /** Raw accent color (dark variant) for swatch previews. */
    public static int accentPreview(String key) {
        for (String[] a : ACCENTS) if (a[0].equals(key)) return (int) Long.parseLong(a[2], 16);
        return (int) Long.parseLong(ACCENTS[0][2], 16);
    }

    public static boolean isLight() { return light; }

    public static String accentKey() { return accentKey; }

    /** 1.0 normal, 1.3 large-text mode (applied in Kit.text). */
    public static float fontScale() { return fontScale; }

    /** Seasonal accent key from the Jalali date (v14 «تم مناسبتی»). */
    public static String seasonAccentKey() {
        try {
            String t = Jalali.todayStr();
            int m = Integer.parseInt(t.substring(5, 7));
            int d = Integer.parseInt(t.substring(8, 10));
            if ((m == 9 && d == 30) || (m == 10 && d == 1)) return "ruby"; // شب یلدا 🍉 (۳۰ آذر)
            if ((m == 12 && d >= 29) || (m == 1 && d <= 13)) return "gold"; // نوروز 🌸
            if (m >= 1 && m <= 3) return "emerald";                           // بهار
            if (m >= 4 && m <= 6) return "teal";                              // تابستان
            if (m >= 7 && m <= 9) return "ruby";                              // پاییز
            return "sapphire";                                               // زمستان
        } catch (Exception e) {
            return "gold";
        }
    }

    /** Seasonal glyph shown next to the company name (empty when seasonal mode is off). */
    public static String seasonGlyph(android.content.Context c) {
        try {
            if (!new Settings(c).seasonalOn()) return "";
            String t = Jalali.todayStr();
            int m = Integer.parseInt(t.substring(5, 7));
            int d = Integer.parseInt(t.substring(8, 10));
            if ((m == 12 && d >= 29) || (m == 1 && d <= 1)) return "🍉";
            if (m == 1 && d <= 13) return "🌸";
            if (m >= 1 && m <= 3) return "🌸";
            if (m >= 4 && m <= 6) return "☀";
            if (m >= 7 && m <= 9) return "🍂";
            return "❄";
        } catch (Exception e) {
            return "";
        }
    }

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Regular.ttf");
            bold = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Bold.ttf");
        } catch (Exception ignored) {
            regular = Typeface.DEFAULT;
            bold = Typeface.DEFAULT_BOLD;
        }
        apply(c);
    }

    /** Re-read theme settings and repaint the whole palette. Call before any render. */
    public static void apply(Context c) {
        Settings s = new Settings(c);
        light = "light".equals(s.themeMode());
        DARK = !light;
        int zi = 1;
        try {
            zi = s.zoomIdx();
            if (s.bigFont()) zi = 3;
        } catch (Exception ignored) { }
        fontScale = zi == 0 ? 0.9f : zi == 2 ? 1.15f : zi == 3 ? 1.3f : 1f;
        accentKey = s.themeAccent();
        if (s.seasonalOn() && "gold".equals(accentKey)) accentKey = seasonAccentKey();
        int dark = 0xFFD9AE5A, darkSoft = 0xFFF1D493, lite = 0xFFA86F14, liteSoft = 0xFF7A5410;
        for (String[] a : ACCENTS) {
            if (a[0].equals(accentKey)) {
                dark = (int) Long.parseLong(a[2], 16);
                darkSoft = (int) Long.parseLong(a[3], 16);
                lite = (int) Long.parseLong(a[4], 16);
                liteSoft = (int) Long.parseLong(a[5], 16);
            }
        }
        if (light) {
            BG = 0xFFF6F0E1;
            SURFACE = 0xFFFFFDF6;
            SURFACE2 = 0xFFF1E4C8;
            GOLD = lite;
            GOLD_SOFT = liteSoft;
            TEXT = 0xFF1B2333;
            MUTED = 0xFF5C6678;
            SUCCESS = 0xFF15924F;
            DANGER = 0xFFCE363C;
            WARNING = 0xFFD97E06;
            INFO = 0xFF2A7BDC;
            VIOLET = 0xFF7A5AF8;
            TEAL = 0xFF0C9B8C;
            STEEL = 0xFF5F6B82;
        } else {
            BG = 0xFF0C1220;
            SURFACE = 0xFF151D31;
            SURFACE2 = 0xFF1D2742;
            GOLD = dark;
            GOLD_SOFT = darkSoft;
            TEXT = 0xFFF4EFE4;
            MUTED = 0xFF9AA3B5;
            SUCCESS = 0xFF35C26E;
            DANGER = 0xFFE5484D;
            WARNING = 0xFFF5A524;
            INFO = 0xFF4FA3FF;
            VIOLET = 0xFF9A7BFF;
            TEAL = 0xFF3ED6C5;
            STEEL = 0xFF7C8DA6;
        }
    }

    /** Text color readable ON the accent gradient (gold → dark ink, jewel tones → white). */
    public static int onAccent() {
        return "gold".equals(accentKey) ? 0xFF1A1206 : 0xFFFFFFFF;
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

    /** Mix color toward white (amt 0..1) or black (amt -1..0). */
    public static int shade(int color, float amt) {
        float r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        if (amt >= 0) {
            r += (255 - r) * amt;
            g += (255 - g) * amt;
            b += (255 - b) * amt;
        } else {
            r *= (1 + amt);
            g *= (1 + amt);
            b *= (1 + amt);
        }
        return Color.rgb(Math.round(r), Math.round(g), Math.round(b));
    }

    // ---------------- system bars ----------------
    public static int statusBar() {
        return light ? 0xFFF6F0E1 : 0xFF0C1220;
    }

    /** Navigation-bar color: champagne in light mode so the dark system keys stay visible. */
    public static int navBar() {
        return light ? 0xFFEFE0C2 : 0xFF0C1220;
    }

    // ---------------- drawable factories ----------------
    private static GradientDrawable base(int fill, float radiusDp, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** One controlled lift for every card. Deliberately small: depth comes from spacing too. */
    public static float cardElevation() {
        return dp(light ? 2 : 3);
    }

    /** Standard glass card. */
    public static GradientDrawable card() {
        return base(SURFACE, 20, dp(1), alpha(light ? TEXT : GOLD, light ? 26 : 40));
    }

    /** Card with an accent glow edge. */
    public static GradientDrawable cardAccent(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(accent, light ? 30 : 46), alpha(accent, light ? 10 : 14), SURFACE});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), alpha(accent, light ? 70 : 90));
        return d;
    }

    /** Hero header background. */
    public static GradientDrawable hero(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{alpha(accent, light ? 44 : 70), alpha(accent, light ? 16 : 22), alpha(SURFACE, 255)});
        d.setCornerRadius(dp(24));
        d.setStroke(dp(1), alpha(accent, light ? 90 : 110));
        return d;
    }

    /** KPI tile background. */
    public static GradientDrawable kpi(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(accent, light ? 26 : 34), alpha(SURFACE, 255)});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(accent, light ? 60 : 70));
        return d;
    }

    /** Primary button gradient, built from the current accent. */
    public static GradientDrawable goldButton() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{shade(GOLD, -0.18f), GOLD, shade(GOLD, 0.28f)});
        d.setCornerRadius(dp(16));
        return d;
    }

    /** Controlled rose-gold for the primary start CTA (v34). */
    public static final int ROSE = 0xFFC2527E;

    /** Primary start button: restrained gold-to-rose diagonal, same radius as gold buttons. */
    public static GradientDrawable startButton() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{shade(GOLD, -0.10f), ROSE});
        d.setCornerRadius(dp(16));
        return d;
    }

    /** Luxury field background with a real focus ring (v34). */
    public static android.graphics.drawable.StateListDrawable fieldBg() {
        GradientDrawable normal = base(SURFACE2, 16, dp(1), alpha(GOLD, light ? 70 : 46));
        GradientDrawable focused = base(SURFACE2, 16, dp(2), TEAL);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Field background in the error state (v34). */
    public static GradientDrawable fieldBgError() {
        return base(SURFACE2, 16, dp(2), DANGER);
    }

    public static GradientDrawable ghostButton(int accent) {
        return base(alpha(accent, light ? 18 : 26), 16, dp(1), alpha(accent, light ? 130 : 110));
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
        return base(alpha(color, light ? 22 : 30), 999, dp(1), alpha(color, 120));
    }

    public static GradientDrawable searchBar() {
        return base(SURFACE2, 16, dp(1), alpha(GOLD, light ? 70 : 46));
    }

    public static GradientDrawable avatar(int color) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, alpha(color, 140)});
        d.setCornerRadius(dp(999));
        return d;
    }

    public static GradientDrawable bottomBar() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0xFFFFFDF6, 0xFFF2E4C6} : new int[]{0xFF101828, 0xFF0C1220});
        d.setStroke(dp(1), alpha(GOLD, 44));
        return d;
    }

    public static GradientDrawable dialogBg() {
        GradientDrawable d = base(SURFACE, 26, dp(1), alpha(GOLD, 80));
        return d;
    }

    public static GradientDrawable tableHeader() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{alpha(GOLD, light ? 90 : 60), alpha(GOLD, light ? 40 : 26)});
        d.setCornerRadius(dp(12));
        return d;
    }

    /** Color-dot swatch for the theme picker. */
    public static GradientDrawable swatch(int color, boolean selected) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, shade(color, -0.22f)});
        d.setCornerRadius(dp(999));
        if (selected) d.setStroke(dp(3), TEXT);
        return d;
    }

    public static void pressable(View v) {
        v.setClickable(true);
        v.setFocusable(true);
        try {
            // Never nest ripples: repaint passes (e.g. the bottom bar) call this repeatedly.
            if (v.getBackground() instanceof android.graphics.drawable.RippleDrawable) return;
            // Touch feedback: wrap the current background in a ripple (minSdk 26, always available).
            android.graphics.drawable.Drawable bg = v.getBackground();
            android.content.res.ColorStateList csl =
                    android.content.res.ColorStateList.valueOf(alpha(TEXT, 64));
            android.graphics.drawable.Drawable mask =
                    bg == null ? new android.graphics.drawable.ColorDrawable(0xFFFFFFFF) : null;
            v.setBackground(new android.graphics.drawable.RippleDrawable(csl, bg, mask));
        } catch (Exception ignored) { }
    }
}
