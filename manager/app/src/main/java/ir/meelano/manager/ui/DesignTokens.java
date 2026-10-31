package ir.meelano.manager.ui;

/**
 * Stable, platform-independent design tokens for the Meelano visual system.
 * Android Views consume these values through {@link Theme}; pure color math
 * stays here so palette and contrast rules can be unit-tested on the JVM.
 */
public final class DesignTokens {
    private DesignTokens() { }

    // Brand palette
    public static final int EMERALD_DEEP = 0xFF0E4B3A;
    public static final int EMERALD = 0xFF137A5A;
    public static final int GOLD = 0xFFC8A24A;
    public static final int GOLD_LIGHT = 0xFFE7CF8E;
    public static final int GOLD_DARK = 0xFF8F6F22;
    public static final int IVORY = 0xFFFBF7EE;
    public static final int PAPER = 0xFFF4EEDC;
    public static final int INK = 0xFF1B2B26;

    // Emerald-night surfaces
    public static final int NIGHT_BG = 0xFF071A14;
    public static final int NIGHT_SURFACE = 0xFF0E241D;
    public static final int NIGHT_PAPER = 0xFF15372B;
    public static final int NIGHT_TEXT = 0xFFF6F4EC;
    public static final int NIGHT_MUTED = 0xFFB8C5BE;

    // Section accents: hue identifies the section, never the outcome of a metric.
    public static final int SALES = EMERALD;
    public static final int PURCHASE = 0xFF2B6A8F;
    public static final int RECEIVE = GOLD;
    public static final int PAY = 0xFFA8444B;
    public static final int RETURNS = 0xFF7459A8;
    public static final int ALERT = 0xFFC4702F;
    public static final int MARKET = 0xFFA8864F;
    public static final int WAREHOUSE = 0xFF1F8A8A;

    // Outcome colors are independent of the section palette and text-safe after accessibility adjustment.
    public static final int POSITIVE = 0xFF26784F;
    public static final int NEGATIVE = 0xFFB33A42;
    public static final int WARNING = 0xFF9A5F12;
    public static final int INFO = 0xFF396B8A;
    public static final int NEUTRAL = 0xFF746B59;

    public static int sectionAccent(String title) {
        String s = normalize(title);
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

    /** 7% accent wash over a solid palette surface; no texture or transparent glass. */
    public static int surfaceTint(int accent, boolean dark) {
        int base = dark ? NIGHT_SURFACE : IVORY;
        return mix(base, accent, 0.07f);
    }

    /** Find a text-safe variant of an accent without changing the accent used by its icon. */
    public static int accessibleAccent(int accent, boolean dark) {
        int background = dark ? NIGHT_SURFACE : IVORY;
        int target = dark ? 0xFFFFFFFF : 0xFF000000;
        int result = accent;
        for (int i = 0; i < 24 && contrastRatio(result, background) < 4.5; i++) {
            result = mix(result, target, 0.08f);
        }
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
