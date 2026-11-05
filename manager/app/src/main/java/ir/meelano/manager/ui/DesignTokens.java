package ir.meelano.manager.ui;

/**
 * Stable, platform-independent design tokens for the Meelano visual system.
 * Android Views consume these values through {@link Theme}; pure color math
 * stays here so palette and contrast rules can be unit-tested on the JVM.
 */
public final class DesignTokens {
    private DesignTokens() { }

    // Executive Royal brand palette. Keep these values aligned with the product brief.
    public static final int EMERALD_DEEP = 0xFF0D3028; // Deep Emerald
    public static final int EMERALD = 0xFF123D32;      // Royal Emerald
    public static final int GOLD = 0xFFB69A60;         // Champagne Gold
    public static final int GOLD_LIGHT = 0xFFE9DFC7;   // Soft Champagne
    /** Accessible companion ink for champagne labels on the light surface. */
    public static final int GOLD_DARK = 0xFF73582A;
    public static final int IVORY = 0xFFFAF7EF;
    public static final int PAPER = 0xFFF2EEE4;        // Soft Surface
    public static final int INK = 0xFF1D2924;          // Primary Text
    public static final int LIGHT_MUTED = 0xFF69746D;  // Secondary Text
    public static final int BORDER = 0xFFE4D9C0;

    // Midnight Emerald surfaces. The first two are the same exact brand greens above;
    // NIGHT_PAPER is a restrained raised surface for nested controls and charts.
    public static final int NIGHT_BG = EMERALD_DEEP;
    public static final int NIGHT_SURFACE = EMERALD;
    public static final int NIGHT_EMERALD = 0xFF2D9A73;
    public static final int NIGHT_PAPER = 0xFF19483D;
    public static final int NIGHT_TEXT = 0xFFF6F4EC;
    public static final int NIGHT_MUTED = 0xFFB8C5BE;

    // Quiet Fintech surfaces: neutral canvas, one emerald action color, no decorative gold slabs.
    // The legacy brand palette above stays available for Nuts identity and exported documents.
    public static final int APP_LIGHT_BG = 0xFFF4F5F1;
    public static final int APP_LIGHT_SURFACE = 0xFFFFFFFF;
    public static final int APP_LIGHT_SURFACE_2 = 0xFFF8F9F6;
    public static final int APP_LIGHT_TEXT = 0xFF18231E;
    public static final int APP_LIGHT_MUTED = 0xFF68736C;
    public static final int APP_LIGHT_BORDER = 0xFFE1E6E1;
    public static final int APP_LIGHT_ACCENT = 0xFF176149;
    public static final int APP_LIGHT_ACCENT_SOFT = 0xFFEAF2ED;
    public static final int APP_DARK_BG = 0xFF101814;
    public static final int APP_DARK_SURFACE = 0xFF18231E;
    public static final int APP_DARK_SURFACE_2 = 0xFF1D2A24;
    public static final int APP_DARK_TEXT = 0xFFF1F3EE;
    public static final int APP_DARK_MUTED = 0xFFA6B0A9;
    public static final int APP_DARK_BORDER = 0xFF2D3A33;
    public static final int APP_DARK_ACCENT = 0xFF70B99A;
    public static final int APP_DARK_ACCENT_SOFT = 0xFF20372C;
    public static final int APP_LIGHT_STEEL = 0xFF59675F;
    public static final int APP_DARK_STEEL = 0xFFB8C4BE;
    public static final int ROSE = 0xFFC2527E;

    // Section accents: hue identifies the section, never the outcome of a metric.
    public static final int SALES = EMERALD;
    public static final int PURCHASE = 0xFF2B6A8F;
    public static final int RECEIVE = GOLD;
    public static final int PAY = 0xFFA8444B;
    public static final int RETURNS = 0xFF7459A8;
    public static final int ALERT = 0xFFC4702F;
    public static final int MARKET = 0xFFA8864F;
    public static final int WAREHOUSE = 0xFF1F8A8A;
    public static final int CLOSEOUT = 0xFF7D5E3C;
    public static final int CASH_POSITION = 0xFF3F6F78;
    public static final int TOP_PERFORMERS = 0xFF4E715E;
    public static final int SALES_GOAL = 0xFF596E9C;
    public static final int GROSS_PROFIT = 0xFF2D7468;
    public static final int SEASONAL_CALENDAR = 0xFF8A5D47;
    public static final int QUICK_ACTIONS = 0xFF62679A;
    public static final int SALES_FORECAST = 0xFF4D7694;

    // Outcome colors are independent of the section palette and text-safe after accessibility adjustment.
    public static final int POSITIVE = 0xFF26784F;
    public static final int NEGATIVE = 0xFFB33A42;
    public static final int WARNING = 0xFF9A5F12;
    public static final int INFO = 0xFF396B8A;
    public static final int NEUTRAL = 0xFF746B59;

    public static int sectionAccent(String title) {
        String s = normalize(title);
        if (containsAny(s, "بستن روز", "تطبیق صندوق", "closeout")) return CLOSEOUT;
        if (containsAny(s, "موقعیت نقدی", "نقد در دست", "cash position")) return CASH_POSITION;
        if (containsAny(s, "هدف فروش", "sales goal")) return SALES_GOAL;
        if (containsAny(s, "سود ناخالص", "gross profit")) return GROSS_PROFIT;
        if (containsAny(s, "تقویم فصلی", "فصلی خشکبار", "seasonal calendar")) return SEASONAL_CALENDAR;
        if (containsAny(s, "میان‌بر", "میانبر", "quick action")) return QUICK_ACTIONS;
        if (containsAny(s, "پیش‌بینی فروش", "پیشبینی فروش", "sales forecast")) return SALES_FORECAST;
        if (containsAny(s, "برترین", "top performer")) return TOP_PERFORMERS;
        if (containsAny(s, "خرید", "purchase", "بهای خرید")) return PURCHASE;
        if (containsAny(s, "دریافت", "وصول", "receive", "cash in")) return RECEIVE;
        if (containsAny(s, "پرداخت", "نقدینگی", "cash out")) return PAY;
        if (containsAny(s, "برگشت", "مرجوع", "return")) return RETURNS;
        if (containsAny(s, "هشدار", "اخطار", "alert")) return ALERT;
        if (containsAny(s, "بازار", "نرخ", "market", "ارز", "طلا")) return MARKET;
        if (containsAny(s, "انبار", "موجودی", "کالا", "warehouse", "inventory")) return WAREHOUSE;
        if (containsAny(s, "فروش", "sale", "صندوق")) return SALES;
        if (containsAny(s, "مشتری", "گزارش", "تحلیل", "روند", "customer", "report")) return PURCHASE;
        return EMERALD_DEEP;
    }

    /** Quiet 4.5% accent wash over a solid palette surface; no texture or transparent glass. */
    public static int surfaceTint(int accent, boolean dark) {
        int base = dark ? NIGHT_SURFACE : IVORY;
        return mix(base, accent, 0.045f);
    }

    /**
     * Quiet page-hero surface: a restrained brand wash makes the shared heading feel intentional
     * without turning it into a large colored panel. The slightly stronger dark-mode wash preserves
     * separation from the midnight canvas while keeping the same flat, low-noise visual language.
     */
    public static int heroSurface(int accent, boolean dark) {
        int base = dark ? APP_DARK_SURFACE : APP_LIGHT_SURFACE;
        int safeAccent = accent == 0 ? (dark ? APP_DARK_ACCENT : APP_LIGHT_ACCENT) : accent;
        return mix(base, safeAccent, dark ? 0.10f : 0.035f);
    }

    /** Fine accent-tinted edge for a page hero; other cards keep the neutral shared border. */
    public static int heroBorder(int accent, boolean dark) {
        int base = dark ? APP_DARK_BORDER : APP_LIGHT_BORDER;
        int safeAccent = accent == 0 ? (dark ? APP_DARK_ACCENT : APP_LIGHT_ACCENT) : accent;
        return mix(base, safeAccent, dark ? 0.22f : 0.20f);
    }

    /**
     * Near-neutral dashboard metric surface. A 2.5% wash keeps the selected section hue present
     * without the pastel blocks that make a dense financial dashboard feel noisy; the accent is
     * carried by a distinct outline and its vector icon instead.
     */
    public static int dashboardMetricSurface(int accent, boolean dark) {
        int base = dark ? NIGHT_SURFACE : IVORY;
        return mix(base, accent, 0.025f);
    }

    /** Find a text-safe variant of an accent without changing the accent used by its icon. */
    public static int accessibleAccent(int accent, boolean dark) {
        // Section copy can appear on either the main surface or a lightly tinted card.
        // Resolve against the slightly more demanding raised surface to keep it legible.
        return textSafeColor(accent, dark ? NIGHT_PAPER : PAPER, dark);
    }

    /** Highest-contrast palette ink for text rendered directly on a solid accent fill. */
    public static int accentTextColor(int accent) {
        int best = INK;
        double bestRatio = contrastRatio(best, accent);
        double ratio = contrastRatio(IVORY, accent);
        if (ratio > bestRatio) {
            best = IVORY;
            bestRatio = ratio;
        }
        ratio = contrastRatio(0xFF000000, accent);
        if (ratio > bestRatio) {
            best = 0xFF000000;
            bestRatio = ratio;
        }
        ratio = contrastRatio(0xFFFFFFFF, accent);
        if (ratio > bestRatio) best = 0xFFFFFFFF;
        return best;
    }

    /**
     * Adjust a requested foreground to a 4.8:1 contrast target on its actual surface. This is
     * useful for tinted chips/cards as well as untinted palette surfaces; icon accents stay intact.
     */
    public static int textSafeColor(int preferred, int background, boolean dark) {
        int target = dark ? 0xFFFFFFFF : 0xFF000000;
        int result = preferred;
        for (int i = 0; i < 24 && contrastRatio(result, background) < 4.8; i++) {
            result = mix(result, target, 0.08f);
        }
        if (contrastRatio(result, background) < 4.8) return target;
        return result;
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static double contrastRatio(int foreground, int background) {
        double a = luminance(foreground);
        double b = luminance(background);
        double hi = Math.max(a, b);
        double lo = Math.min(a, b);
        return (hi + 0.05) / (lo + 0.05);
    }

    private static double luminance(int color) {
        double r = channel((color >>> 16) & 0xFF);
        double g = channel((color >>> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.replace('ي', 'ی').replace('ك', 'ک').toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }
}
