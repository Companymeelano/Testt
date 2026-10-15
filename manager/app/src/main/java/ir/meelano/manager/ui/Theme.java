package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
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
    /** Fixed hues for sales and purchases: they must stand out from the gold/theme accent. */
    public static final int SALES_HUE = 0xFFFF8A3D;
    public static final int BUY_HUE = 0xFFFF5FA2;

    private static boolean light = false;
    /** Mirror of !light for call sites (charts, tables). */
    public static boolean DARK = true;
    private static String accentKey = "platinum";
    private static float fontScale = 1f;
    private static float density = 3f;
    /**
     * Type grows slightly with the screen so labels are not lost on the large, dense
     * displays of high-end phones - a 13sp line that reads fine on a 5" screen looks lost on
     * a 6.8" one. Only type is scaled; layout metrics stay in plain dp so nothing can
     * overflow horizontally.
     */
    private static float typeScale = 1f;
    private static Typeface regular;
    private static Typeface bold;

    /** Accent: key, Persian label, dark, darkSoft, light, lightSoft. */
    private static final String[][] ACCENTS = {
            {"platinum", "پلاتینی", "FFE3E6EC", "FFF7F8FA", "FF4B5563", "FF374151"},
            {"gold", "طلایی", "FFE0B765", "FFF5DFA6", "FF8A6320", "FF7A5612"},
            {"emerald", "زمردی", "FF3ED598", "FF9BF0C8", "FF087A53", "FF046C4E"},
            {"sapphire", "یاقوت آبی", "FF4FA3FF", "FFB3D4FF", "FF1C60E0", "FF1E429F"},
            {"ruby", "یاقوتی", "FFF2617A", "FFFFB3C0", "FFC51E46", "FF8F0F2E"},
            {"violet", "بنفش سلطنتی", "FF9A7BFF", "FFCDBFFF", "FF7C3AED", "FF5B21B6"},
            {"teal", "فیروزه‌ای", "FF3ED6C5", "FFA7F3EA", "FF08776E", "FF065F56"},
    };

    /** Deep ink and warm ivory chosen to keep accent controls legible in both skins. */
    private static final int ACCENT_INK = 0xFF111827;
    private static final int ACCENT_PAPER = 0xFFFFFCF5;

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

    /** Screen-aware type multiplier (1.00 on small phones, up to 1.18 on large ones). */
    public static float typeScale() { return typeScale; }

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
        android.util.DisplayMetrics dm0 = c.getResources().getDisplayMetrics();
        density = dm0.density;
        float wDp = density > 0 ? dm0.widthPixels / density : 360f;
        typeScale = wDp < 380f ? 1.00f : wDp < 440f ? 1.06f : wDp < 520f ? 1.12f : 1.18f;
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
        } catch (Exception ignored) { Diag.ignored("Theme:154", ignored); }
        fontScale = zi == 0 ? 0.9f : zi == 2 ? 1.15f : zi == 3 ? 1.3f : 1f;
        accentKey = s.themeAccent();
        if (s.seasonalOn() && ("platinum".equals(accentKey) || "gold".equals(accentKey))) accentKey = seasonAccentKey();
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
            // «عاج سلطنتی»: warm pearl background, near-white cards, clear separation.
            BG = 0xFFF7F3EA;
            SURFACE = 0xFFFFFDF8;
            SURFACE2 = 0xFFF3EBDA;
            GOLD = lite;
            GOLD_SOFT = liteSoft;
            TEXT = 0xFF141B2D;
            MUTED = 0xFF5A6378;
            SUCCESS = 0xFF1B7A4B;
            DANGER = 0xFFB3261E;
            WARNING = 0xFF9A5B00;
            INFO = 0xFF1F5FAD;
            VIOLET = 0xFF6343D6;
            TEAL = 0xFF007268;
            STEEL = 0xFF546177;
        } else {
            // «گرافیت پلاتینی»: neutral graphite-black surfaces, so every accent (platinum, gold…) reads as one family.
            BG = 0xFF090A0D;
            SURFACE = 0xFF14161C;
            SURFACE2 = 0xFF1E2129;
            GOLD = dark;
            GOLD_SOFT = darkSoft;
            TEXT = 0xFFF4F5F7;
            MUTED = 0xFF9EA5B1;
            SUCCESS = 0xFF3DD68C;
            DANGER = 0xFFFF5C61;
            WARNING = 0xFFFFB020;
            INFO = 0xFF5AA9FF;
            VIOLET = 0xFF9A7BFF;
            TEAL = 0xFF3ED6C5;
            STEEL = 0xFF7C8DA6;
        }
    }

    /** Choose the foreground with the stronger contrast against an accent surface. */
    public static int onAccent(int background) {
        return darkAccentInk(background) ? ACCENT_INK : ACCENT_PAPER;
    }

    /** Text color readable on the current accent. */
    public static int onAccent() {
        return onAccent(GOLD);
    }

    /** Conservative WCAG-style luminance split for the light and dark accent inks. */
    public static boolean darkAccentInk(int background) {
        return Color.luminance(background) >= 0.195f;
    }

    /** Foreground for the selected navigation capsule (neutral in light mode). */
    public static int selectedNavInk() {
        return light ? GOLD : onAccent(GOLD);
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

    /** Standard glass card with a restrained top sheen and warm hairline edge. */
    public static GradientDrawable card() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(SURFACE, light ? 0.012f : 0.07f), SURFACE,
                        shade(SURFACE, light ? -0.012f : -0.045f)});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), alpha(GOLD, light ? 44 : 48));
        return d;
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

    /** Executive dashboard hero: layered midnight/champagne glass with a quiet metallic edge. */
    public static GradientDrawable executiveHero() {
        int accent = GOLD;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(accent, light ? 56 : 82), alpha(SURFACE, 250), BG});
        d.setCornerRadius(dp(28));
        d.setStroke(dp(1), alpha(accent, light ? 150 : 125));
        return d;
    }

    /** Selected destination on the premium bottom navigation rail. */
    public static GradientDrawable bottomNavSelected() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(GOLD, light ? 34 : 28), alpha(GOLD, light ? 15 : 12)});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), alpha(GOLD, light ? 105 : 90));
        return d;
    }

    /** Active icon capsule in the bottom navigation. */
    public static GradientDrawable bottomNavIconSelected() {
        // Ivory Royal uses a neutral champagne capsule with the accent itself as
        // the foreground; Midnight Gold uses a luminous accent capsule and ink.
        // Three-stop gold: champagne highlight → accent → deep shade, with a bright rim.
        int[] colors = light
                ? new int[]{shade(SURFACE2, 0.05f), SURFACE2, shade(SURFACE2, -0.03f)}
                : new int[]{shade(GOLD, 0.22f), GOLD, shade(GOLD, -0.2f)};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), alpha(0xFFFFFFFF, light ? 110 : 120));
        return d;
    }

    /** Small active marker under each bottom-nav label. */
    public static GradientDrawable bottomNavMarker() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{shade(GOLD, -0.08f), GOLD, shade(GOLD, 0.20f)});
        d.setCornerRadius(dp(99));
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

    /** Primary button gradient, toned toward a high-contrast ink/paper foreground. */
    public static GradientDrawable goldButton() {
        boolean darkInk = darkAccentInk(GOLD);
        float direction = darkInk ? 1f : -1f;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{shade(GOLD, direction * 0.04f),
                        shade(GOLD, direction * 0.12f),
                        shade(GOLD, direction * 0.07f)});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(ACCENT_PAPER, 82));
        return d;
    }

    /** Controlled rose-gold for the primary start CTA (v34). */
    public static final int ROSE = 0xFFC2527E;

    /** Primary start button: gold-to-rose sheen, both stops matched to the active ink. */
    public static GradientDrawable startButton() {
        boolean darkInk = darkAccentInk(GOLD);
        int gold = shade(GOLD, darkInk ? 0.04f : -0.08f);
        int rose = shade(ROSE, darkInk ? 0.22f : -0.18f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{gold, rose});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(ACCENT_PAPER, 82));
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

    /** The one back button used on every screen: accent-gradient capsule with a light rim. */
    public static GradientDrawable backPill() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{shade(GOLD, 0.08f), GOLD, shade(GOLD, -0.22f)});
        d.setCornerRadius(dp(30));
        d.setStroke(dp(1), alpha(0xFFFFFFFF, light ? 150 : 90));
        return d;
    }

    public static GradientDrawable ghostButton(int accent) {
        return base(alpha(accent, light ? 18 : 26), 16, dp(1), alpha(accent, light ? 130 : 110));
    }

    public static GradientDrawable chip(boolean selected, int accent) {
        if (selected) {
            float relief = darkAccentInk(accent) ? 0.08f : -0.08f;
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{accent, shade(accent, relief)});
            d.setCornerRadius(dp(999));
            d.setStroke(dp(1), alpha(onAccent(accent), 86));
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

    /** Semi-transparent glyph capsule for secondary icons. */
    public static GradientDrawable avatar(int color) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, alpha(color, 140)});
        d.setCornerRadius(dp(999));
        d.setStroke(dp(1), alpha(color, light ? 110 : 90));
        return d;
    }

    /** Opaque accent capsule with both gradient stops matched to its foreground. */
    public static GradientDrawable accentAvatar(int accent) {
        float relief = darkAccentInk(accent) ? 0.08f : -0.08f;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accent, shade(accent, relief)});
        d.setCornerRadius(dp(999));
        d.setStroke(dp(1), alpha(onAccent(accent), 86));
        return d;
    }

    public static GradientDrawable bottomBar() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0xFFFFFDF6, 0xFFF2E4C6} : new int[]{0xFF101828, 0xFF0C1220});
        // Rounded top edge with a gold hairline: the bar reads as a floating luxury dock.
        float r = dp(22);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        d.setStroke(dp(1), alpha(GOLD, 70));
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
        } catch (Exception ignored) { Diag.ignored("Theme:491", ignored); }
    }
}
