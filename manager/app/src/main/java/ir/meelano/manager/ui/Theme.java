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

    public static int BG = DesignTokens.NIGHT_BG;
    public static int SURFACE = DesignTokens.NIGHT_SURFACE;
    public static int SURFACE2 = DesignTokens.NIGHT_PAPER;
    /** Legacy primary accent; new section surfaces use their own DesignTokens accent. */
    public static int GOLD = DesignTokens.GOLD_LIGHT;
    public static int GOLD_SOFT = DesignTokens.GOLD_LIGHT;
    public static int PRIMARY = DesignTokens.EMERALD;
    public static int TEXT = DesignTokens.NIGHT_TEXT;
    public static int MUTED = DesignTokens.NIGHT_MUTED;
    public static int SUCCESS = 0xFF64C99C;
    public static int DANGER = 0xFFF19AA0;
    public static int WARNING = 0xFFEAB776;
    public static int INFO = 0xFF8ABBDD;
    public static int VIOLET = 0xFFB5A0E5;
    public static int STEEL = 0xFFB8C4BE;
    public static final int NEUTRAL = DesignTokens.NEUTRAL;
    /** Legacy aliases; semantic outcome colors are intentionally not section colors. */
    public static final int SALES_HUE = DesignTokens.SALES;
    public static final int BUY_HUE = DesignTokens.PURCHASE;

    private static boolean light = false;
    /** «آجیل و خشکبار»: the light skin plus nut artwork and nut icons. */
    private static boolean nuts = false;
    public static boolean isNuts() { return nuts; }

    /** MEELANO crest: gold on emerald in the nut theme, the classic black-and-gold badge otherwise. */
    public static int logoRes() { return nuts ? R.drawable.meelano_nuts : R.drawable.meelano_3d; }
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

    /** Deep ink and warm ivory chosen to keep accent controls legible in both skins. */
    private static final int ACCENT_PAPER = DesignTokens.IVORY;

    public static boolean isLight() { return light; }

    /**
     * Rendered 3D gold icons replace the flat vector glyphs in both skins. Returns the drawable to
     * show for a vector glyph id.
     */
    /**
     * Nut theme icons: false = the gold 3D set from the approved reference (plain gold on the
     * ivory and emerald surfaces). true = the emerald medallion set. One flag switches back.
     */
    public static final boolean NUT_MEDALLIONS = false;

    /** Nut-theme medallion icons (emerald medallion, gold rim) for the main sections; 0 = none. */
    private static int medal(int vector) {
        if (vector == R.drawable.mi_home) return R.drawable.icon3d_medal_home;
        if (vector == R.drawable.mi_group) return R.drawable.icon3d_medal_customers;
        if (vector == R.drawable.mi_inventory_2) return R.drawable.icon3d_medal_inventory;
        if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_medal_products;
        if (vector == R.drawable.mi_bar_chart) return R.drawable.icon3d_medal_reports;
        if (vector == R.drawable.mi_settings) return R.drawable.icon3d_medal_settings;
        if (vector == R.drawable.mi_search) return R.drawable.icon3d_medal_search;
        if (vector == R.drawable.mi_cheque) return R.drawable.icon3d_medal_cheque;
        if (vector == R.drawable.mi_calendar_month) return R.drawable.icon3d_medal_dues;
        if (vector == R.drawable.mi_person) return R.drawable.icon3d_medal_account;
        if (vector == R.drawable.mi_refresh) return R.drawable.icon3d_medal_refresh;
        if (vector == R.drawable.mi_contrast) return R.drawable.icon3d_medal_theme;
        if (vector == R.drawable.mi_zoom) return R.drawable.icon3d_medal_zoom;
        if (vector == R.drawable.mi_star_shine) return R.drawable.icon3d_medal_assistant;
        if (vector == R.drawable.mi_widgets) return R.drawable.icon3d_medal_more;
        if (vector == R.drawable.mi_diamond) return R.drawable.icon3d_medal_goldbar;
        if (vector == R.drawable.mi_trending_up) return R.drawable.icon3d_medal_dollar;
        return 0;
    }

    public static int icon3d(int vector) {
        if (NUT_MEDALLIONS && nuts) {
            int m = medal(vector);
            if (m != 0) return m;
        }
        // A coherent 3D set for the command room and financial sections, shared by all three themes.
        // The source artwork is a transparent emerald/gold medallion, so it remains legible on ivory and obsidian.
        if (vector == R.drawable.mi_warehouse) return R.drawable.icon3d_production;
        if (vector == R.drawable.mi_task_alt) return R.drawable.icon3d_closeout;
        if (vector == R.drawable.mi_military_tech) return R.drawable.icon3d_score;
        if (vector == R.drawable.mi_profit) return R.drawable.icon3d_profit;
        if (vector == R.drawable.mi_account_balance || vector == R.drawable.mi_account_balance_wallet) return R.drawable.icon3d_treasury;
        if (vector == R.drawable.mi_directions_car || vector == R.drawable.mi_local_shipping) return R.drawable.icon3d_logistics;
        if (vector == R.drawable.mi_mic) return R.drawable.icon3d_voice;
        if (vector == R.drawable.mi_admin_panel_settings) return R.drawable.icon3d_dark_roles;
        if (vector == R.drawable.mi_request_quote) return R.drawable.icon3d_medal_sales;
        if (vector == R.drawable.mi_add_shopping_cart) return R.drawable.icon3d_medal_purchase;
        if (vector == R.drawable.mi_warning) return R.drawable.icon3d_medal_alert;
        if (!light) {
            // Dark skin: icons rendered on black, with a brighter gold glow.
            if (vector == R.drawable.mi_lightbulb) return R.drawable.icon3d_dark_assistant;
            if (vector == R.drawable.mi_trending_down) return R.drawable.icon3d_dark_cash_out;
            if (vector == R.drawable.mi_assignment_return) return R.drawable.icon3d_dark_refresh;
            if (vector == R.drawable.mi_trending_up) return R.drawable.icon3d_dark_dollar;
            if (vector == R.drawable.mi_diamond) return R.drawable.icon3d_dark_gold;
            if (vector == R.drawable.mi_search) return R.drawable.icon3d_dark_search;
            if (vector == R.drawable.mi_refresh) return R.drawable.icon3d_dark_refresh;
            if (vector == R.drawable.mi_contrast) return R.drawable.icon3d_dark_theme;
            if (vector == R.drawable.mi_zoom) return R.drawable.icon3d_dark_zoom;
            if (vector == R.drawable.mi_point_of_sale) return R.drawable.icon3d_dark_sales;
            if (vector == R.drawable.mi_shopping_cart) return R.drawable.icon3d_dark_purchase;
            if (vector == R.drawable.mi_group) return R.drawable.icon3d_dark_customers;
            if (vector == R.drawable.mi_home) return R.drawable.icon3d_dark_home;
            if (vector == R.drawable.mi_settings) return R.drawable.icon3d_dark_settings;
            if (vector == R.drawable.mi_star_shine) return R.drawable.icon3d_dark_assistant;
            if (vector == R.drawable.mi_receipt_long) return R.drawable.icon3d_dark_cash_in;
            if (vector == R.drawable.mi_payments) return R.drawable.icon3d_dark_cash_out;
            if (vector == R.drawable.mi_cheque) return R.drawable.icon3d_dark_cheque;
            if (vector == R.drawable.mi_inventory_2) return R.drawable.icon3d_dark_inventory;
            if (vector == R.drawable.mi_bar_chart) return R.drawable.icon3d_dark_reports;
            if (vector == R.drawable.mi_person) return R.drawable.icon3d_dark_account;
            if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_dark_products;
            if (vector == R.drawable.mi_widgets) return R.drawable.icon3d_dark_more;
            if (vector == R.drawable.mi_calendar_month) return R.drawable.icon3d_dark_dues;
        }
        if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_products;
        if (vector == R.drawable.mi_star_shine) return R.drawable.icon3d_assistant;
        if (vector == R.drawable.mi_widgets) return R.drawable.icon3d_more;
        if (vector == R.drawable.mi_calendar_month) return R.drawable.icon3d_dues;
        if (vector == R.drawable.mi_zoom) return R.drawable.icon3d_zoom;
        if (vector == R.drawable.mi_sell) return light ? R.drawable.icon3d_light_tag : R.drawable.icon3d_dark_tag;
        if (vector == R.drawable.mi_point_of_sale) return nuts ? R.drawable.icon3d_medal_sales : R.drawable.icon3d_sales;
        if (vector == R.drawable.mi_shopping_cart) return nuts ? R.drawable.icon3d_medal_purchase : R.drawable.icon3d_purchase;
        if (vector == R.drawable.mi_receipt_long) return nuts ? R.drawable.icon3d_medal_cash_in : R.drawable.icon3d_cash_in;
        if (vector == R.drawable.mi_payments) return nuts ? R.drawable.icon3d_medal_cash_out : R.drawable.icon3d_cash_out;
        if (vector == R.drawable.mi_cheque) return R.drawable.icon3d_cheque;
        if (vector == R.drawable.mi_inventory_2) return R.drawable.icon3d_inventory;
        if (vector == R.drawable.mi_group) return R.drawable.icon3d_customers;
        if (vector == R.drawable.mi_bar_chart) return R.drawable.icon3d_reports;
        if (vector == R.drawable.mi_settings) return R.drawable.icon3d_settings;
        if (vector == R.drawable.mi_trending_up) return R.drawable.icon3d_dollar;
        if (vector == R.drawable.mi_diamond) return R.drawable.icon3d_gold;
        if (vector == R.drawable.mi_home) return R.drawable.icon3d_home;
        if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_inventory;
        if (vector == R.drawable.mi_person) return R.drawable.icon3d_account;
        if (vector == R.drawable.mi_search) return R.drawable.icon3d_search;
        if (vector == R.drawable.mi_refresh) return R.drawable.icon3d_refresh;
        if (vector == R.drawable.mi_contrast) return R.drawable.icon3d_theme;
        return vector;
    }

    /** True for bitmap 3D icons, which must not be tinted. */
    public static boolean isIcon3d(int res) {
        if (res == R.drawable.visitor3d_assistant || res == R.drawable.visitor3d_customers
                || res == R.drawable.visitor3d_prefactor || res == R.drawable.visitor3d_products
                || res == R.drawable.visitor3d_receipt) return true;
        // Emerald artwork must not be tinted like a flat glyph or it loses its theme colours.
        if (res == R.drawable.icon3d_emerald_account || res == R.drawable.icon3d_emerald_assistant
                || res == R.drawable.icon3d_emerald_cash_in || res == R.drawable.icon3d_emerald_cash_out
                || res == R.drawable.icon3d_emerald_cheque || res == R.drawable.icon3d_emerald_coin
                || res == R.drawable.icon3d_emerald_customers || res == R.drawable.icon3d_emerald_dollar
                || res == R.drawable.icon3d_emerald_dues || res == R.drawable.icon3d_emerald_gold
                || res == R.drawable.icon3d_emerald_home || res == R.drawable.icon3d_emerald_inventory
                || res == R.drawable.icon3d_emerald_light_tag || res == R.drawable.icon3d_emerald_more
                || res == R.drawable.icon3d_emerald_products || res == R.drawable.icon3d_emerald_purchase
                || res == R.drawable.icon3d_emerald_refresh || res == R.drawable.icon3d_emerald_reports
                || res == R.drawable.icon3d_emerald_sales || res == R.drawable.icon3d_emerald_search
                || res == R.drawable.icon3d_emerald_settings || res == R.drawable.icon3d_emerald_theme
                || res == R.drawable.icon3d_emerald_zoom) return true;
        if (res == R.drawable.icon3d_dark_bell || res == R.drawable.icon3d_medal_alert) return true;
        if (res == R.drawable.icon3d_medal_account || res == R.drawable.icon3d_medal_refresh || res == R.drawable.icon3d_medal_theme || res == R.drawable.icon3d_medal_zoom || res == R.drawable.icon3d_medal_assistant || res == R.drawable.icon3d_medal_more || res == R.drawable.icon3d_medal_goldbar || res == R.drawable.icon3d_medal_dollar || res == R.drawable.icon3d_medal_coin) return true;
        if (res == R.drawable.icon3d_medal_home || res == R.drawable.icon3d_medal_customers
                || res == R.drawable.icon3d_medal_inventory || res == R.drawable.icon3d_medal_products
                || res == R.drawable.icon3d_medal_reports || res == R.drawable.icon3d_medal_settings
                || res == R.drawable.icon3d_medal_search || res == R.drawable.icon3d_medal_cheque
                || res == R.drawable.icon3d_medal_dues) return true;
        return res == R.drawable.icon3d_nut_theme || res == R.drawable.icon3d_production
                || res == R.drawable.icon3d_closeout
                || res == R.drawable.icon3d_treasury || res == R.drawable.icon3d_logistics
                || res == R.drawable.icon3d_voice || res == R.drawable.icon3d_dark_roles
                || res == R.drawable.icon3d_score || res == R.drawable.icon3d_profit
                || res == R.drawable.icon3d_medal_sales || res == R.drawable.icon3d_medal_purchase
                || res == R.drawable.icon3d_medal_cash_in || res == R.drawable.icon3d_medal_cash_out
                || res == R.drawable.icon3d_sales || res == R.drawable.icon3d_purchase
                || res == R.drawable.icon3d_cash_in || res == R.drawable.icon3d_cash_out
                || res == R.drawable.icon3d_cheque || res == R.drawable.icon3d_inventory
                || res == R.drawable.icon3d_customers || res == R.drawable.icon3d_reports
                || res == R.drawable.icon3d_settings || res == R.drawable.icon3d_dollar
                || res == R.drawable.icon3d_gold || res == R.drawable.icon3d_coin
                || res == R.drawable.icon3d_home || res == R.drawable.icon3d_search
                || res == R.drawable.icon3d_account || res == R.drawable.icon3d_refresh
                || res == R.drawable.icon3d_theme || res == R.drawable.icon3d_products
                || res == R.drawable.icon3d_assistant || res == R.drawable.icon3d_more
                || res == R.drawable.icon3d_dues || res == R.drawable.icon3d_zoom
                || res == R.drawable.icon3d_dark_dollar || res == R.drawable.icon3d_dark_gold
                || res == R.drawable.icon3d_dark_search || res == R.drawable.icon3d_dark_refresh
                || res == R.drawable.icon3d_dark_theme || res == R.drawable.icon3d_dark_zoom
                || res == R.drawable.icon3d_dark_coin || res == R.drawable.icon3d_dark_sales
                || res == R.drawable.icon3d_dark_purchase || res == R.drawable.icon3d_dark_customers
                || res == R.drawable.icon3d_dark_home || res == R.drawable.icon3d_dark_settings
                || res == R.drawable.icon3d_dark_assistant
                || res == R.drawable.icon3d_dark_tag || res == R.drawable.icon3d_light_tag
                || res == R.drawable.icon3d_dark_cash_in || res == R.drawable.icon3d_dark_cash_out || res == R.drawable.icon3d_dark_cheque || res == R.drawable.icon3d_dark_inventory || res == R.drawable.icon3d_dark_reports || res == R.drawable.icon3d_dark_account || res == R.drawable.icon3d_dark_products || res == R.drawable.icon3d_dark_more || res == R.drawable.icon3d_dark_dues
                || res == R.drawable.icon3d_alert || res == R.drawable.icon3d_emerald_alert;
    }

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
        // Light and the preserved «آجیل و خشکبار» mode share the ivory/emerald
        // material; Nuts retains its crest, not a second texture or component system.
        if (light) {
            BG = DesignTokens.IVORY;
            SURFACE = DesignTokens.IVORY;
            SURFACE2 = DesignTokens.PAPER;
            GOLD = DesignTokens.GOLD;
            GOLD_SOFT = DesignTokens.GOLD_DARK;
            PRIMARY = DesignTokens.EMERALD;
            TEXT = DesignTokens.INK;
            MUTED = DesignTokens.LIGHT_MUTED;
            SUCCESS = DesignTokens.accessibleAccent(DesignTokens.POSITIVE, false);
            DANGER = DesignTokens.accessibleAccent(DesignTokens.NEGATIVE, false);
            WARNING = DesignTokens.accessibleAccent(DesignTokens.WARNING, false);
            INFO = DesignTokens.accessibleAccent(DesignTokens.INFO, false);
            VIOLET = DesignTokens.accessibleAccent(DesignTokens.RETURNS, false);
            STEEL = 0xFF59675F;
        } else {
            BG = DesignTokens.NIGHT_BG;
            SURFACE = DesignTokens.NIGHT_SURFACE;
            SURFACE2 = DesignTokens.NIGHT_PAPER;
            GOLD = DesignTokens.GOLD_LIGHT;
            GOLD_SOFT = DesignTokens.GOLD_LIGHT;
            PRIMARY = DesignTokens.NIGHT_EMERALD;
            TEXT = DesignTokens.NIGHT_TEXT;
            MUTED = DesignTokens.NIGHT_MUTED;
            SUCCESS = DesignTokens.accessibleAccent(DesignTokens.POSITIVE, true);
            DANGER = DesignTokens.accessibleAccent(DesignTokens.NEGATIVE, true);
            WARNING = DesignTokens.accessibleAccent(DesignTokens.WARNING, true);
            INFO = DesignTokens.accessibleAccent(DesignTokens.INFO, true);
            VIOLET = 0xFFB5A0E5;
            STEEL = 0xFFB8C4BE;
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

    /** Subtle card depth; the surface and hairline, not a glow, carry hierarchy. */
    public static float cardElevation() {
        return dp(light ? 2 : 3);
    }

    /** Low, neutral shadow; card identity comes from surface tint and the section hairline. */
    public static void lift(android.view.View v, float elevationPx) {
        v.setElevation(elevationPx);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                v.setOutlineAmbientShadowColor(light ? 0x220E4B3A : 0x66000000);
                v.setOutlineSpotShadowColor(light ? 0x260E4B3A : 0x44000000);
            } catch (Exception ignored) { Diag.ignored("Theme:lift", ignored); }
        }
    }

    /** Legacy optional glass drawable; shared data cards use flat paper surfaces instead. */
    public static android.graphics.drawable.Drawable glass(int top, int bottom, float radiusDp, int edgeAlpha) {
        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        body.setCornerRadius(dp(radiusDp));
        body.setStroke(dp(1), alpha(GOLD, edgeAlpha));
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(0xFFFFFFFF, light ? 150 : 22), 0x00FFFFFF});
        sheen.setCornerRadius(dp(radiusDp));
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{body, sheen});
    }

    /** Flat screen surface. Nut mode keeps its crest but never tiles artwork behind content. */
    public static android.graphics.drawable.Drawable background() {
        return new android.graphics.drawable.ColorDrawable(BG);
    }

    public static android.graphics.drawable.Drawable card() {
        return base(SURFACE, 20, dp(1), alpha(GOLD_METAL, light ? 42 : 54));
    }

    /** Accent card: solid surface tinted by 7%, with a one-dp section hairline. */
    public static GradientDrawable cardAccent(int accent) {
        int tint = DesignTokens.surfaceTint(accent, !light);
        return base(tint, 20, dp(1), alpha(accent, light ? 72 : 92));
    }

    /** Stable section hue derived from its title; semantic outcomes use separate tokens. */
    public static int sectionAccent(String title) {
        return DesignTokens.sectionAccent(title);
    }

    /** Text-safe companion for a section accent. */
    public static int sectionText(int accent) {
        return DesignTokens.accessibleAccent(accent, !light);
    }

    /** 6–8% wash for icons and accent chips. */
    public static int sectionTint(int accent) {
        return alpha(accent, light ? 20 : 28);
    }

    /** One-pixel accent hairline with a restrained gold highlight. */
    public static GradientDrawable accentHairline(int accent) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{alpha(accent, 42), alpha(GOLD_METAL, 104), alpha(accent, 42)});
    }

    /** Hero header background. */
    public static GradientDrawable hero(int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{alpha(accent, light ? 44 : 70), alpha(accent, light ? 16 : 22), alpha(SURFACE, 255)});
        d.setCornerRadius(dp(24));
        d.setStroke(dp(1), alpha(accent, light ? 90 : 110));
        return d;
    }

    /** Welcome hero is the one data-adjacent surface allowed a restrained gold wash. */
    public static android.graphics.drawable.Drawable executiveHero() {
        int lower = light ? PAPER : SURFACE;
        GradientDrawable hero = new GradientDrawable(GradientDrawable.Orientation.TR_BL,
                new int[]{light ? IVORY : alpha(GOLD_METAL, 22), lower});
        hero.setCornerRadius(dp(20));
        hero.setStroke(dp(1), alpha(GOLD_METAL, light ? 170 : 112));
        return hero;
    }

    /** Accent shared by the dock; the preserved Nuts skin keeps its gold identity. */
    public static int bottomNavAccent() { return nuts ? GOLD : PRIMARY; }

    /** Contrast-safe foreground for the active dock item on its exact tinted surface. */
    public static int selectedNavInk() {
        int accent = bottomNavAccent();
        int surface = DesignTokens.surfaceTint(accent, !light);
        int target = light ? 0xFF000000 : 0xFFFFFFFF;
        int ink = accent;
        for (int i = 0; i < 24 && DesignTokens.contrastRatio(ink, surface) < 4.8; i++)
            ink = DesignTokens.mix(ink, target, 0.08f);
        return ink;
    }

    /** Selected destination: a flat seven-percent accent wash with a one-dp hairline. */
    public static GradientDrawable bottomNavSelected() {
        int accent = bottomNavAccent();
        return base(DesignTokens.surfaceTint(accent, !light), 20, dp(1),
                alpha(accent, light ? 96 : 112));
    }

    /** Quiet active icon capsule; the icon itself uses the contrast-safe dock foreground. */
    public static GradientDrawable bottomNavIconSelected() {
        int accent = bottomNavAccent();
        return base(DesignTokens.surfaceTint(accent, !light), 999, dp(1),
                alpha(accent, light ? 76 : 96));
    }

    /** Small, solid accent marker under the selected bottom-nav label. */
    public static GradientDrawable bottomNavMarker() {
        return base(bottomNavAccent(), 999, 0, 0);
    }

    /** Flat KPI surface with a restrained section tint and hairline. */
    public static GradientDrawable kpi(int accent) {
        return base(DesignTokens.surfaceTint(accent, !light), 20, dp(1),
                alpha(accent, light ? 68 : 86));
    }

    /** Primary button gradient, toned toward a high-contrast ink/paper foreground. */
    public static GradientDrawable goldButton() {
        boolean darkInk = darkAccentInk(GOLD);
        float direction = darkInk ? 1f : -1f;
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(GOLD, direction * 0.14f),
                        shade(GOLD, direction * 0.02f),
                        shade(GOLD, direction * -0.08f)});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(ACCENT_PAPER, 110));
        return d;
    }

    /** Controlled rose-gold for the primary start CTA (v34). */
    public static final int ROSE = 0xFFC2527E;

    /** Primary start button: gold-to-rose sheen, both stops matched to the active ink. */
    public static GradientDrawable startButton() {
        if (nuts) {
            // Nut theme: emerald to antique gold, the same as the main action buttons (no rose).
            GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF1F6F55, 0xFF0B5C44, 0xFF9C7A1A});
            n.setCornerRadius(dp(18));
            n.setStroke(dp(1), alpha(ACCENT_PAPER, 110));
            return n;
        }
        boolean darkInk = darkAccentInk(GOLD);
        int gold = shade(GOLD, darkInk ? 0.04f : -0.08f);
        int rose = shade(ROSE, darkInk ? 0.22f : -0.18f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(gold, 0.10f), gold, rose});
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), alpha(ACCENT_PAPER, 110));
        return d;
    }

    /** Luxury field background with a real focus ring (v34). */
    public static android.graphics.drawable.StateListDrawable fieldBg() {
        GradientDrawable normal = base(SURFACE2, 16, dp(1), alpha(GOLD, light ? 70 : 46));
        GradientDrawable focused = base(SURFACE2, 16, dp(2), GOLD);
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

    /** Header tool row: solid ivory panel in light mode so labels read over the artwork. */
    public static android.graphics.drawable.Drawable toolbarPanel() {
        if (!light) return ghostButton(GOLD);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(18));
        d.setColor(0xF2FFFFFF);
        d.setStroke(dp(1), alpha(GOLD, 110));
        return d;
    }

    /** Nuts keeps its ivory dock and gold outline while sharing the same low-noise surfaces. */
    public static GradientDrawable navDock() {
        return base(SURFACE, 24, dp(1), alpha(bottomNavAccent(), 112));
    }

    /** Flat app dock with a rounded upper edge and a restrained, one-dp section hairline. */
    public static GradientDrawable bottomBar() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(SURFACE);
        float r = dp(22);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        d.setStroke(dp(1), alpha(bottomNavAccent(), light ? 72 : 64));
        return d;
    }

    public static GradientDrawable dialogBg() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(SURFACE, light ? 0.02f : 0.08f), SURFACE});
        d.setCornerRadius(dp(26));
        d.setStroke(dp(1), alpha(GOLD, 90));
        return d;
    }

    /** Nut theme: ivory panel framed in gold, the frame of every data table. */
    public static GradientDrawable nutPanel() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFF7F0DC});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), 0xFFC9A24A);
        return d;
    }

    /** Nut theme: deep emerald header band with a gold hairline, like the dashboard banner. */
    public static GradientDrawable nutTableHeader() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0A3B2E, 0xFF17684F, 0xFF0A3B2E});
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), alpha(0xFFC9A24A, 170));
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

    /** Listener returns false so framework click/keyboard/TalkBack actions remain untouched. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void pressable(View v) {
        v.setClickable(true);
        v.setFocusable(true);
        // Micro-interaction: a soft press-in, a spring back, and a light haptic tick on release.
        // Returns false so clicks and scrolling behave exactly as before.
        try {
            v.setOnTouchListener((view, ev) -> {
                boolean motion = Ui.motionOk(view.getContext());
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        if (motion) view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(110).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        if (motion) view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f)).start();
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        break;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (motion) view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
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
