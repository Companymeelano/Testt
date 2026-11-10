package ir.meelano.manager.ui;

import ir.meelano.manager.R;
import ir.meelano.manager.core.Diag;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Settings;

/**
 * Shared flat theme engine for the whole Java/View app. Light and dark modes use neutral surfaces
 * with an emerald action accent; Nuts remains a distinct selectable skin. Colors stay mutable so
 * each screen, chart and drawable can repaint after a theme change without changing business data.
 */
public final class Theme {
    private Theme() { }

    // Shared 4dp layout rhythm and nested card radii for the Java/View design system.
    public static final int SPACE_UNIT_DP = 4;
    public static final int CARD_PADDING_HORIZONTAL_DP = 16;
    public static final int CARD_PADDING_VERTICAL_DP = 12;
    public static final int CARD_RADIUS_DP = 16;
    public static final int DASHBOARD_CARD_RADIUS_DP = 20;
    public static final int DASHBOARD_METRIC_RADIUS_DP = 16;
    public static final int DASHBOARD_BADGE_RADIUS_DP = 12;

    /** Clamp a requested gap to the nearest 4dp step; zero remains zero for optional spaces. */
    public static int snapSpacingDp(int requestedDp) {
        if (requestedDp <= 0) return 0;
        return Math.max(SPACE_UNIT_DP,
                Math.round(requestedDp / (float) SPACE_UNIT_DP) * SPACE_UNIT_DP);
    }

    // ---------------- dynamic palette (set by apply) ----------------
    public static final int EMERALD_DEEP = DesignTokens.EMERALD_DEEP;
    public static final int EMERALD = DesignTokens.EMERALD;
    public static final int GOLD_METAL = DesignTokens.GOLD;
    public static final int GOLD_LIGHT = DesignTokens.GOLD_LIGHT;
    public static final int GOLD_DARK = DesignTokens.GOLD_DARK;
    public static final int IVORY = DesignTokens.IVORY;
    public static final int PAPER = DesignTokens.PAPER;
    public static final int INK = DesignTokens.INK;
    public static final int SECTION_SALES = DesignTokens.SALES;
    public static final int SECTION_PURCHASE = DesignTokens.PURCHASE;
    public static final int SECTION_RECEIVE = DesignTokens.RECEIVE;
    public static final int SECTION_PAY = DesignTokens.PAY;
    public static final int SECTION_RETURNS = DesignTokens.RETURNS;
    public static final int SECTION_ALERT = DesignTokens.ALERT;
    public static final int SECTION_MARKET = DesignTokens.MARKET;
    public static final int SECTION_WAREHOUSE = DesignTokens.WAREHOUSE;
    public static final int SECTION_CLOSEOUT = DesignTokens.CLOSEOUT;
    public static final int SECTION_CASH_POSITION = DesignTokens.CASH_POSITION;
    public static final int SECTION_TOP_PERFORMERS = DesignTokens.TOP_PERFORMERS;
    public static final int SECTION_SALES_GOAL = DesignTokens.SALES_GOAL;
    public static final int SECTION_GROSS_PROFIT = DesignTokens.GROSS_PROFIT;
    public static final int SECTION_SEASONAL_CALENDAR = DesignTokens.SEASONAL_CALENDAR;
    public static final int SECTION_QUICK_ACTIONS = DesignTokens.QUICK_ACTIONS;
    public static final int SECTION_SALES_FORECAST = DesignTokens.SALES_FORECAST;

    public static int BG = DesignTokens.APP_DARK_BG;
    public static int SURFACE = DesignTokens.APP_DARK_SURFACE;
    public static int SURFACE2 = DesignTokens.APP_DARK_SURFACE_2;
    /** Compatibility alias used by existing screens; light/dark resolve to emerald, Nuts to gold. */
    public static int GOLD = DesignTokens.APP_DARK_ACCENT;
    public static int GOLD_SOFT = DesignTokens.APP_DARK_ACCENT;
    public static int PRIMARY = DesignTokens.APP_DARK_ACCENT;
    public static int TEXT = DesignTokens.APP_DARK_TEXT;
    public static int MUTED = DesignTokens.APP_DARK_MUTED;
    public static int BORDER = DesignTokens.APP_DARK_BORDER;
    public static int ACCENT_SOFT = DesignTokens.APP_DARK_ACCENT_SOFT;
    public static int SUCCESS = DesignTokens.textSafeColor(
            DesignTokens.POSITIVE, DesignTokens.APP_DARK_SURFACE, true);
    public static int DANGER = DesignTokens.textSafeColor(
            DesignTokens.NEGATIVE, DesignTokens.APP_DARK_SURFACE, true);
    public static int WARNING = DesignTokens.textSafeColor(
            DesignTokens.WARNING, DesignTokens.APP_DARK_SURFACE, true);
    public static int INFO = DesignTokens.textSafeColor(
            DesignTokens.INFO, DesignTokens.APP_DARK_SURFACE, true);
    public static int VIOLET = DesignTokens.textSafeColor(
            DesignTokens.RETURNS, DesignTokens.APP_DARK_SURFACE, true);
    public static int STEEL = DesignTokens.APP_DARK_STEEL;
    public static final int NEUTRAL = DesignTokens.NEUTRAL;
    /** Legacy aliases; semantic outcome colors are intentionally not section colors. */
    public static final int SALES_HUE = DesignTokens.SALES;
    public static final int BUY_HUE = DesignTokens.PURCHASE;

    private static boolean light = false;
    /** «آجیل و خشکبار»: the light skin plus nut artwork and nut icons. */
    private static boolean nuts = false;
    public static boolean isNuts() { return nuts; }

    /** Shared flat brand mark; its tint is chosen by the surface using it. */
    public static int logoRes() { return R.drawable.mi_diamond; }
    /** Mirror of !light for call sites (charts, tables). */
    public static boolean DARK = true;
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

    public static boolean isLight() { return light; }

    /** Compatibility flag retained for callers of the previous icon treatment. */
    public static final boolean NUT_MEDALLIONS = false;

    /** Legacy hook: return the flat vector unchanged so every theme can tint it. */
    public static int icon3d(int vector) { return vector; }

    /** Flat vector glyphs are always tintable. */
    public static boolean isIcon3d(int res) { return false; }

    /** 1.0 normal, 1.3 large-text mode (applied in Kit.text). */
    public static float fontScale() { return fontScale; }

    /** Screen-aware type multiplier (1.00 on small phones, up to 1.18 on large ones). */
    public static float typeScale() { return typeScale; }

    /** Legacy compatibility hook: seasonal themes are gone; the three permanent looks remain. */
    public static String seasonGlyph(android.content.Context c) { return ""; }

    public static void init(Context c) {
        android.util.DisplayMetrics dm0 = c.getResources().getDisplayMetrics();
        density = dm0.density;
        float wDp = density > 0 ? dm0.widthPixels / density : 360f;
        typeScale = wDp < 380f ? 1.00f : wDp < 440f ? 1.06f : wDp < 520f ? 1.12f : 1.18f;
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Regular.ttf");
            bold = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Bold.ttf");
        } catch (Exception ignored) {
            // The platform sans family has a broader Arabic/Persian fallback chain than
            // Typeface.DEFAULT on OEM builds where the bundled Vazirmatn asset is missing.
            regular = systemTypeface(false);
            bold = systemTypeface(true);
        }
        apply(c);
    }

    /** Re-read theme settings and repaint the whole palette. Call before any render. */
    public static void apply(Context c) {
        Settings s = new Settings(c);
        String tm = s.themeMode();
        nuts = "nuts".equals(tm);
        light = "light".equals(tm) || nuts;
        DARK = !light;
        int zi = 1;
        try {
            zi = s.zoomIdx();
            if (s.bigFont()) zi = 3;
        } catch (Exception ignored) { Diag.ignored("Theme:154", ignored); }
        fontScale = zi == 0 ? 0.9f : zi == 2 ? 1.15f : zi == 3 ? 1.3f : 1f;

        if (light) {
            BG = DesignTokens.APP_LIGHT_BG;
            SURFACE = DesignTokens.APP_LIGHT_SURFACE;
            SURFACE2 = DesignTokens.APP_LIGHT_SURFACE_2;
            PRIMARY = nuts ? DesignTokens.EMERALD : DesignTokens.APP_LIGHT_ACCENT;
            GOLD = nuts ? DesignTokens.GOLD : PRIMARY;
            GOLD_SOFT = nuts ? DesignTokens.GOLD_DARK : PRIMARY;
            TEXT = DesignTokens.APP_LIGHT_TEXT;
            MUTED = DesignTokens.APP_LIGHT_MUTED;
            BORDER = DesignTokens.APP_LIGHT_BORDER;
            ACCENT_SOFT = nuts ? DesignTokens.PAPER : DesignTokens.APP_LIGHT_ACCENT_SOFT;
            SUCCESS = DesignTokens.textSafeColor(DesignTokens.POSITIVE, SURFACE, false);
            DANGER = DesignTokens.textSafeColor(DesignTokens.NEGATIVE, SURFACE, false);
            WARNING = DesignTokens.textSafeColor(DesignTokens.WARNING, SURFACE, false);
            INFO = DesignTokens.textSafeColor(DesignTokens.INFO, SURFACE, false);
            VIOLET = DesignTokens.textSafeColor(DesignTokens.RETURNS, SURFACE, false);
            STEEL = DesignTokens.APP_LIGHT_STEEL;
        } else {
            BG = DesignTokens.APP_DARK_BG;
            SURFACE = DesignTokens.APP_DARK_SURFACE;
            SURFACE2 = DesignTokens.APP_DARK_SURFACE_2;
            PRIMARY = DesignTokens.APP_DARK_ACCENT;
            GOLD = PRIMARY;
            GOLD_SOFT = PRIMARY;
            TEXT = DesignTokens.APP_DARK_TEXT;
            MUTED = DesignTokens.APP_DARK_MUTED;
            BORDER = DesignTokens.APP_DARK_BORDER;
            ACCENT_SOFT = DesignTokens.APP_DARK_ACCENT_SOFT;
            SUCCESS = DesignTokens.textSafeColor(DesignTokens.POSITIVE, SURFACE, true);
            DANGER = DesignTokens.textSafeColor(DesignTokens.NEGATIVE, SURFACE, true);
            WARNING = DesignTokens.textSafeColor(DesignTokens.WARNING, SURFACE, true);
            INFO = DesignTokens.textSafeColor(DesignTokens.INFO, SURFACE, true);
            VIOLET = DesignTokens.textSafeColor(DesignTokens.RETURNS, SURFACE, true);
            STEEL = DesignTokens.APP_DARK_STEEL;
        }
    }

    /** Flat brand-paper / emerald-night backdrop; decoration stays in headers and empty states. */
    public static GradientDrawable backdrop() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(BG);
        return d;
    }

    /** Choose the foreground with the stronger contrast against an accent surface. */
    public static int onAccent(int background) {
        return DesignTokens.accentTextColor(background);
    }

    /** Text color readable on the current accent. */
    public static int onAccent() {
        return onAccent(GOLD);
    }

    /** Conservative WCAG-style luminance split for the light and dark accent inks. */
    public static boolean darkAccentInk(int background) {
        return Color.luminance(background) >= 0.195f;
    }

    public static int dp(float v) {
        return Math.round(v * density);
    }

    public static Typeface face(boolean isBold) {
        if (isBold) return bold == null ? systemTypeface(true) : bold;
        return regular == null ? systemTypeface(false) : regular;
    }

    /** Use the bundled face when it covers the name; otherwise let Android's sans fallback draw it. */
    public static Typeface faceForText(String text, boolean isBold) {
        Typeface preferred = face(isBold);
        if (text == null || text.isEmpty()) return preferred;
        try {
            Paint probe = new Paint();
            probe.setTypeface(preferred);
            // Check code points separately: a Persian word is several glyphs shaped as
            // a run, so Paint.hasGlyph("علی") is not a valid whole-name coverage test.
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                        || Character.getType(cp) == Character.FORMAT) continue;
                if (!probe.hasGlyph(new String(Character.toChars(cp)))) return systemTypeface(isBold);
            }
        } catch (Exception ignored) {
            return systemTypeface(isBold);
        }
        return preferred;
    }

    private static Typeface systemTypeface(boolean isBold) {
        try {
            Typeface system = Typeface.create("sans-serif", isBold ? Typeface.BOLD : Typeface.NORMAL);
            if (system != null) return system;
        } catch (Exception ignored) { Diag.ignored("Theme:face", ignored); }
        return isBold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
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
    /** Dark system-bar icons on light skins (time, battery, nav keys); light icons on dark skins. */
    public static void lightBars(android.view.Window w) {
        try {
            android.view.View dv = w.getDecorView();
            int vis = dv.getSystemUiVisibility();
            if (light) vis |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else vis &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            dv.setSystemUiVisibility(vis);
        } catch (Exception ignored) { }
    }

    public static int statusBar() {
        return BG;
    }

    /** Match the system navigation area to the active flat palette surface. */
    public static int navBar() {
        return BG;
    }

    // ---------------- drawable factories ----------------
    private static GradientDrawable base(int fill, float radiusDp, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** Cards stay in the same plane; borders and surfaces establish hierarchy. */
    public static float cardElevation() { return 0f; }

    /** Compatibility hook for older builders; the approved visual system has no card shadows. */
    public static void lift(android.view.View v, float elevationPx) {
        if (v != null) v.setElevation(0f);
    }

    /** Legacy glass API now returns a flat palette surface to avoid reintroducing gradients. */
    public static android.graphics.drawable.Drawable glass(int top, int bottom, float radiusDp, int edgeAlpha) {
        return base(SURFACE, radiusDp, dp(1), BORDER);
    }

    /** Flat screen surface. Nut mode keeps its crest but never tiles artwork behind content. */
    public static android.graphics.drawable.Drawable background() {
        return new android.graphics.drawable.ColorDrawable(BG);
    }

    public static android.graphics.drawable.Drawable card() {
        return royalSurface();
    }

    /** Flat neutral section card; the icon and heading carry section identity. */
    public static GradientDrawable cardAccent(int accent) {
        return royalSurface();
    }

    /**
     * The signature royal surface: a whisper of champagne melting into the card face from the
     * top edge, closed by a gold-threaded hairline. Cards stay in the same plane; the sheen,
     * not a shadow, carries the luxury.
     */
    private static GradientDrawable royalSurface() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, GOLD_LIGHT, light ? 0.22f : 0.07f), SURFACE});
        d.setCornerRadius(dp(CARD_RADIUS_DP));
        d.setStroke(dp(1), DesignTokens.mix(BORDER, GOLD_METAL, light ? 0.40f : 0.26f));
        return d;
    }

    /** Quiet champagne wash for tiles that have nothing to show yet (empty states). */
    public static GradientDrawable emptyState(int accent) {
        int hue = accent == 0 ? GOLD_METAL : accent;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, hue, light ? 0.05f : 0.09f), SURFACE});
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), alpha(hue, light ? 96 : 72));
        return d;
    }

    /** Dashboard surface tint levels are shared with contrast checks and muted-ink resolution. */
    public static float dashboardCardTopTint() { return light ? 0.03f : 0.12f; }
    public static float dashboardCardBottomTint() { return light ? 0.01f : 0.06f; }
    public static float dashboardMetricTopTint() { return light ? 0.045f : 0.12f; }
    public static float dashboardMetricBottomTint() { return light ? 0.018f : 0.055f; }

    /**
     * Quiet, softly tinted card used only by the home dashboard. Other screens keep the shared
     * neutral card so the home refresh does not leak into the rest of the application.
     */
    public static GradientDrawable dashboardCard(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{DesignTokens.mix(SURFACE, hue, dashboardCardTopTint()),
                        DesignTokens.mix(SURFACE, hue, dashboardCardBottomTint())});
        card.setCornerRadius(dp(DASHBOARD_CARD_RADIUS_DP));
        card.setStroke(dp(1), DesignTokens.mix(BORDER, hue, light ? 0.20f : 0.28f));
        return card;
    }

    /** Restrained inset tile: accent stays in the icon and label instead of filling each card. */
    public static GradientDrawable dashboardMetric(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        GradientDrawable tile = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{DesignTokens.mix(SURFACE, hue, dashboardMetricTopTint()),
                        DesignTokens.mix(SURFACE, hue, dashboardMetricBottomTint())});
        tile.setCornerRadius(dp(DASHBOARD_METRIC_RADIUS_DP));
        tile.setStroke(dp(1), DesignTokens.mix(BORDER, hue, light ? 0.32f : 0.40f));
        return tile;
    }

    /** Small raised jewel plate for dashboard icons; foreground color is resolved separately. */
    public static GradientDrawable dashboardBadge(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        float topTint = light ? 0.23f : 0.42f;
        float bottomTint = light ? 0.12f : 0.27f;
        GradientDrawable badge = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{DesignTokens.mix(SURFACE, hue, topTint),
                        DesignTokens.mix(SURFACE, hue, bottomTint)});
        badge.setCornerRadius(dp(DASHBOARD_BADGE_RADIUS_DP));
        badge.setStroke(dp(1), DesignTokens.mix(BORDER, hue, light ? 0.42f : 0.52f));
        return badge;
    }

    /** Accent ink that remains legible on the dashboard's tinted icon plates and card surfaces. */
    public static int dashboardAccentInk(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        int referenceSurface = DesignTokens.mix(SURFACE, hue, light ? 0.23f : 0.42f);
        return DesignTokens.textSafeColor(hue, referenceSurface, !light);
    }

    /** Secondary copy ink adjusted for the strongest part of an inset dashboard tile. */
    public static int dashboardMutedInk(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        int referenceSurface = DesignTokens.mix(SURFACE, hue, dashboardMetricTopTint());
        return DesignTokens.textSafeColor(MUTED, referenceSurface, !light);
    }

    /** Semantic success/warning ink adjusted for the inset surface without changing its meaning. */
    public static int dashboardSemanticInk(int accent, int preferred) {
        int hue = accent == 0 ? PRIMARY : accent;
        int referenceSurface = DesignTokens.mix(SURFACE, hue, dashboardMetricTopTint());
        return DesignTokens.textSafeColor(preferred, referenceSurface, !light);
    }

    /** Secondary copy ink for labels placed directly on a home-card surface. */
    public static int dashboardCardMutedInk(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        int referenceSurface = DesignTokens.mix(SURFACE, hue, dashboardCardTopTint());
        return DesignTokens.textSafeColor(MUTED, referenceSurface, !light);
    }

    /** Contrast-safe foreground for a compact, filled dashboard action. */
    public static int dashboardActionInk(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        double blackContrast = DesignTokens.contrastRatio(Color.BLACK, hue);
        double whiteContrast = DesignTokens.contrastRatio(Color.WHITE, hue);
        return blackContrast >= whiteContrast ? Color.BLACK : Color.WHITE;
    }

    /** Filled, lightly shaded action used by dashboard section headings only. */
    public static GradientDrawable dashboardAction(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        int ink = dashboardActionInk(hue);
        int safeShade = ink == Color.BLACK ? Color.WHITE : Color.BLACK;
        GradientDrawable action = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{hue, DesignTokens.mix(hue, safeShade, 0.035f)});
        action.setCornerRadius(dp(12));
        action.setStroke(dp(1), alpha(ink, 30));
        return action;
    }

    /** Deep emerald welcome panel with a restrained champagne edge; home-only by design. */
    public static GradientDrawable dashboardWelcome() {
        int deep = DesignTokens.EMERALD_DEEP;
        int royal = DesignTokens.mix(DesignTokens.EMERALD, PRIMARY, nuts ? 0.12f : 0.08f);
        GradientDrawable welcome = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{royal, DesignTokens.EMERALD, deep});
        welcome.setCornerRadius(dp(20));
        welcome.setStroke(dp(1), alpha(DesignTokens.GOLD, nuts ? 188 : 112));
        return welcome;
    }

    /** Readable text and secondary ink for the deep emerald dashboard welcome panel. */
    public static int dashboardWelcomeText() {
        return DesignTokens.accentTextColor(DesignTokens.EMERALD_DEEP);
    }

    public static int dashboardWelcomeMuted() {
        return DesignTokens.mix(dashboardWelcomeText(), DesignTokens.GOLD_LIGHT, 0.17f);
    }

    /** Stable section hue derived from its title; semantic outcomes use separate tokens. */
    public static int sectionAccent(String title) {
        return DesignTokens.sectionAccent(title);
    }

    /** Text-safe companion for a section accent. */
    public static int sectionText(int accent) {
        if (accent == SUCCESS || accent == DANGER || accent == WARNING || accent == INFO)
            return DesignTokens.textSafeColor(accent, SURFACE, !light);
        int foreground = nuts ? (accent == DesignTokens.GOLD ? DesignTokens.GOLD_DARK : PRIMARY) : PRIMARY;
        return DesignTokens.textSafeColor(foreground, SURFACE, !light);
    }

    /** Theme-aligned accent surface for small icons and chips. */
    public static int sectionTint(int accent) {
        return ACCENT_SOFT;
    }

    /** One-pixel neutral divider; semantic color remains in the icon or label. */
    public static GradientDrawable accentHairline(int accent) {
        // Royal divider: a gold-threaded rule that fades out at both ends instead of a
        // hard border line.
        GradientDrawable rule = new GradientDrawable();
        int hue = DesignTokens.mix(accent == 0 ? PRIMARY : accent, GOLD_METAL, 0.42f);
        rule.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        rule.setColors(new int[]{alpha(hue, 0), alpha(hue, light ? 125 : 95), alpha(hue, 0)});
        return rule;
    }

    /** Gold-ringed medallion that carries a section icon; circular at 34dp. */
    public static GradientDrawable medallion(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(hue, GOLD_LIGHT, light ? 0.42f : 0.22f), hue,
                        DesignTokens.mix(hue, GOLD_DARK, light ? 0.20f : 0.32f)});
        d.setCornerRadius(dp(17));
        d.setStroke(dp(1), alpha(DesignTokens.mix(GOLD_LIGHT, hue, 0.25f), light ? 155 : 105));
        return d;
    }

    /** Shared, flat hero surface with a quiet section tint and a fine matching edge. */
    public static GradientDrawable hero(int accent) {
        int hue = accent == 0 ? PRIMARY : accent;
        return base(DesignTokens.heroSurface(hue, !light), 18, dp(1),
                DesignTokens.heroBorder(hue, !light));
    }

    /** Welcome header uses the same calm surface and border as the rest of the shell. */
    public static android.graphics.drawable.Drawable executiveHero() {
        // The shell's welcome header wears the royal surface too: champagne wash from the
        // top edge and a gold-threaded hairline, tuned separately for light and dark.
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, GOLD_LIGHT, light ? 0.26f : 0.08f), SURFACE});
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), DesignTokens.mix(BORDER, GOLD_METAL, light ? 0.42f : 0.28f));
        return d;
    }

    /** Accent shared by the dock; the preserved Nuts skin keeps its gold identity. */
    public static int bottomNavAccent() { return nuts ? GOLD_DARK : PRIMARY; }

    /** Contrast-safe foreground for the active dock item on its exact tinted surface. */
    public static int selectedNavInk() {
        return DesignTokens.textSafeColor(bottomNavAccent(), ACCENT_SOFT, !light);
    }

    /** Selected destination: a quiet, flat surface behind the active item. */
    public static GradientDrawable bottomNavSelected() {
        return base(ACCENT_SOFT, 10, 0, 0);
    }

    /** Quiet active icon capsule; the icon itself uses the contrast-safe dock foreground. */
    public static GradientDrawable bottomNavIconSelected() {
        return base(ACCENT_SOFT, 10, 0, 0);
    }

    /** Small, solid accent marker under the selected bottom-nav label. */
    public static GradientDrawable bottomNavMarker() {
        return base(bottomNavAccent(), 999, 0, 0);
    }

    /** Flat neutral KPI surface with a one-pixel border. */
    public static GradientDrawable kpi(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, GOLD_LIGHT, light ? 0.28f : 0.09f), SURFACE});
        d.setCornerRadius(dp(CARD_RADIUS_DP));
        int hue = accent == 0 ? GOLD_METAL : accent;
        d.setStroke(dp(1), DesignTokens.mix(BORDER, hue, 0.45f));
        return d;
    }


    /** Legacy primary-button factory; resolves to a solid accent fill. */
    public static GradientDrawable goldButton() {
        // Brushed-metal sheen: a soft highlight across the top of the approved accent fill.
        // The body tone stays the accent itself so onAccent() contrast remains exact.
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(GOLD, 0xFFFFFFFF, light ? 0.20f : 0.13f), GOLD,
                        DesignTokens.mix(GOLD, GOLD_DARK, 0.16f)});
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), alpha(GOLD_DARK, 130));
        return d;
    }

    /** Existing expense-category accent; primary CTAs use the approved emerald/champagne palette. */
    public static final int ROSE = DesignTokens.ROSE;

    /** Solid primary action; the foreground is chosen separately for sufficient contrast. */
    public static GradientDrawable startButton() {
        int hue = nuts ? DesignTokens.EMERALD : PRIMARY;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(hue, 0xFFFFFFFF, light ? 0.18f : 0.12f), hue,
                        DesignTokens.mix(hue, 0xFF000000, 0.14f)});
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), alpha(DesignTokens.mix(hue, 0xFF000000, 0.35f), 130));
        return d;
    }

    /** Flat field surface with a visible focus border. */
    public static android.graphics.drawable.StateListDrawable fieldBg() {
        GradientDrawable normal = base(SURFACE, 10, dp(1), BORDER);
        GradientDrawable focused = base(SURFACE, 10, dp(2), PRIMARY);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    /** Field background in the error state (v34). */
    public static GradientDrawable fieldBgError() {
        return base(SURFACE, 10, dp(2), DANGER);
    }

    /** Flat back button used across screens, with a neutral border. */
    public static GradientDrawable backPill() {
        return base(SURFACE, 10, dp(1), BORDER);
    }

    /**
     * Wraps a button background with pressed and disabled states. The shape and colours stay the
     * same; pressed is a little softer and disabled is dimmed, so the state is visible at a glance.
     */
    public static android.graphics.drawable.Drawable stated(GradientDrawable normal) {
        android.graphics.drawable.Drawable.ConstantState cs = normal.getConstantState();
        if (cs == null) return normal;
        GradientDrawable pressed = (GradientDrawable) cs.newDrawable().mutate();
        pressed.setAlpha(205);
        GradientDrawable disabled = (GradientDrawable) cs.newDrawable().mutate();
        disabled.setAlpha(105);
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static GradientDrawable ghostButton(int accent) {
        // Ghost actions keep their quiet surface but gain the royal finish: a faint accent
        // wash from the top and a gold-threaded hairline instead of the plain border.
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, accent, light ? 0.07f : 0.13f), SURFACE});
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), DesignTokens.mix(BORDER, GOLD_METAL, light ? 0.38f : 0.24f));
        return d;
    }

    public static GradientDrawable chip(boolean selected, int accent) {
        if (selected) {
            // Metallic state: the accent wears a brushed highlight and settles into shade,
            // closed by a champagne thread; onAccent() text keeps its exact contrast.
            GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{DesignTokens.mix(accent, 0xFFFFFFFF, light ? 0.22f : 0.14f), accent,
                            DesignTokens.mix(accent, 0xFF000000, 0.13f)});
            d.setCornerRadius(dp(11));
            d.setStroke(dp(1), alpha(DesignTokens.mix(GOLD_LIGHT, accent, 0.30f), light ? 150 : 100));
            return d;
        }
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(SURFACE, GOLD_LIGHT, light ? 0.18f : 0.06f), SURFACE});
        d.setCornerRadius(dp(11));
        d.setStroke(dp(1), DesignTokens.mix(BORDER, GOLD_METAL, light ? 0.34f : 0.22f));
        return d;
    }

    public static GradientDrawable pill(int color) {
        return base(alpha(color, light ? 18 : 30), 8, 0, 0);
    }

    public static GradientDrawable searchBar() {
        return base(SURFACE, 12, dp(1), BORDER);
    }

    /** Semi-transparent glyph capsule for secondary icons. */
    public static GradientDrawable avatar(int color) {
        return base(color, 12, 0, 0);
    }

    /** Opaque, flat accent tile for a selected mark or compact status. */
    public static GradientDrawable accentAvatar(int accent) {
        // The header medallion gets the same brushed-metal treatment as the primary
        // buttons: highlight on top, shade at the bottom, a champagne thread around.
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{DesignTokens.mix(accent, GOLD_LIGHT, light ? 0.36f : 0.18f), accent,
                        DesignTokens.mix(accent, GOLD_DARK, 0.22f)});
        d.setCornerRadius(dp(10));
        d.setStroke(dp(1), alpha(GOLD_LIGHT, light ? 140 : 90));
        return d;
    }

    /** Header and page share one full-width surface so the top chrome does not look inset. */
    public static android.graphics.drawable.Drawable toolbarPanel() {
        return new android.graphics.drawable.ColorDrawable(BG);
    }

    /** Compatibility factory for the shared flat dock surface. */
    public static GradientDrawable navDock() {
        return base(SURFACE, 0, 0, 0);
    }

    /** Flat app dock surface, continuous with the screen background. */
    public static GradientDrawable bottomBar() {
        return base(BG, 0, 0, 0);
    }

    public static GradientDrawable dialogBg() {
        return base(SURFACE, 18, dp(1), BORDER);
    }

    /** Flat panel surface retained for the selectable Nuts theme. */
    public static GradientDrawable nutPanel() {
        return base(SURFACE, 14, dp(1), BORDER);
    }

    /** Neutral table-header surface used by the Nuts theme. */
    public static GradientDrawable nutTableHeader() {
        return base(SURFACE2, 8, dp(1), BORDER);
    }

    public static GradientDrawable tableHeader() {
        return base(SURFACE2, 8, dp(1), BORDER);
    }

    /** Color-dot swatch for the theme picker. */
    public static GradientDrawable swatch(int color, boolean selected) {
        return base(color, 10, selected ? dp(2) : 0, selected ? TEXT : Color.TRANSPARENT);
    }

    /** Listener returns false so framework click/keyboard/TalkBack actions remain untouched. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void pressable(View v) {
        v.setClickable(true);
        v.setFocusable(true);
        // Micro-interaction: a soft press-in, a spring back, and a light haptic tick on release.
        // Read the system animation scale once per gesture (ACTION_DOWN), not on every MOVE.
        // Returns false so clicks and scrolling behave exactly as before.
        try {
            final boolean[] gestureMotion = {true};
            v.setOnTouchListener((view, ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        gestureMotion[0] = Ui.motionOk(view.getContext());
                        if (gestureMotion[0])
                            view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(110).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        if (gestureMotion[0])
                            view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        break;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (gestureMotion[0])
                            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        break;
                    default:
                        break;
                }
                return false;
            });
        } catch (Exception ignored) { Diag.ignored("Theme:press", ignored); }
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
