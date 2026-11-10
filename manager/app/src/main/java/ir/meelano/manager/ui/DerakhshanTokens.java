package ir.meelano.manager.ui;

/**
 * «درخشان ۲.۰» — the jewel-in-velvet design tokens.
 *
 * Pure data, no platform types: both the Android manager (light "مخمل کرم" / dark "شب زمرد")
 * and the Windows desk mirror these values. Nothing in this class repaints an existing
 * screen by itself; stages 2–6 wire the tokens into components and pages.
 *
 * Non-negotiables encoded here (spec §2/§3/§7):
 *  - one light source, always 150°;
 *  - four shadow levels only (e1–e4);
 *  - four semantic colors only (success/warning/danger/info) — no purple, no brown CTAs;
 *  - Persian digits everywhere (formatting lives in HomeNumberFormatter);
 *  - light theme never paints gold text brighter than #8E6C14 (2.6:1 trap of #C9A227);
 *  - every motion ≤ 700ms except the single "urgent" pulse marker and the once-per-session shimmer.
 */
public final class DerakhshanTokens {

    private DerakhshanTokens() { }

    // ---------------------------------------------------------------- palette (spec §3.1)
    public static final int OBSIDIAN_EMERALD = 0xFF04120D;
    public static final int EMERALD_800      = 0xFF062A20;
    public static final int EMERALD_700      = 0xFF0E4C39;
    public static final int EMERALD_500      = 0xFF1B7A50;
    public static final int EMERALD_300      = 0xFF2E9C68;
    public static final int GOLD_500         = 0xFFC9A227;
    public static final int GOLD_300         = 0xFFE8CE7A;
    public static final int GOLD_100         = 0xFFF7EFC8;
    public static final int CREAM            = 0xFFF7F3EA;
    public static final int CREAM_2          = 0xFFEFE6D6;
    public static final int BRONZE           = 0xFFB08968;

    /** The only four semantic hues; warningText/goldText variants keep text contrast legal. */
    public static final int SUCCESS = 0xFF2E7D5B;
    public static final int WARNING = 0xFFC98A2E;
    public static final int DANGER  = 0xFFB4434B;
    public static final int INFO    = 0xFF4C6E8A;

    // ---------------------------------------------------------------- light source (§3.3)
    /** Every gradient and highlight comes from this single direction. */
    public static final float LIGHT_ANGLE_DEG = 150f;

    // ---------------------------------------------------------------- materials (§3.4)
    /** Stone: the main data card, light theme (165° from top-right). */
    public static final int[] STONE_LIGHT = {0xFFFFFFFF, 0xFFF0EBE0};
    public static final float STONE_ANGLE = 165f;
    /** Stone, dark theme: the same cut of polished obsidian-emerald. */
    public static final int[] STONE_DARK = {0xFF0C2A20, 0xFF071C15};

    /** Molten gold for hero numbers and medals (150°), dark theme stops per spec. */
    public static final int[] GOLD_MATERIAL_DARK = {0xFFF7EFC8, 0xFFD9B45A, 0xFFA8821E};
    public static final float[] GOLD_MATERIAL_STOPS = {0f, 0.55f, 1f};
    /** Light theme hero gold runs deeper so the 42px gradient stays ≥ 3:1 on cream. */
    public static final int[] GOLD_MATERIAL_LIGHT = {0xFFA97F16, 0xFF856310, 0xFF6E5210};

    /** Emerald CTA (160°). */
    public static final int[] EMERALD_MATERIAL = {0xFF1B7A50, 0xFF0A3A2C};
    public static final float EMERALD_ANGLE = 160f;
    public static final int ON_EMERALD = 0xFFFFFFFF;

    /** Glass over colored backgrounds: 6% white, 18px blur, 140% saturate, 12% border. */
    public static final int GLASS_FILL_DARK   = 0x0FFFFFFF;   // rgba(255,255,255,.06)
    public static final int GLASS_BORDER_DARK = 0x1FFFFFFF;   // rgba(255,255,255,.12)
    public static final float GLASS_BLUR_DP = 18f;
    public static final float GLASS_SATURATE = 1.40f;
    public static final int GLASS_FILL_LIGHT   = 0x99FFFFFF;  // rgba(255,255,255,.60)
    public static final int GLASS_BORDER_LIGHT = 0x33FFFFFF;  // rgba(255,255,255,.20)

    /** Brand gold edge: 2px tall, 34% of the card width, anchored inset-inline-end, glowing. */
    public static final int GOLD_EDGE_HEIGHT_DP = 2;
    public static final float GOLD_EDGE_WIDTH_FRACTION = 0.34f;
    public static final int GOLD_EDGE_COLOR = GOLD_500;
    public static final int GOLD_EDGE_GLOW = 0x8CC9A227;      // rgba(201,162,39,.55)
    public static final float GOLD_EDGE_GLOW_DP = 12f;

    /** Grain + aurora: 3–5% noise over the page and a static golden/emerald halo at the top. */
    public static final float GRAIN_FRACTION = 0.04f;

    // ---------------------------------------------------------------- elevation (§3.3)
    /** Only four shadow levels exist; e4 adds the inner top highlight (sheet/modal). */
    public static final String E1_CSS = "0 1px 2px rgba(0,0,0,.28), 0 2px 8px rgba(0,0,0,.18)";
    public static final String E2_CSS = "0 2px 6px rgba(0,0,0,.30), 0 12px 28px rgba(0,0,0,.26)";
    public static final String E3_CSS = "0 4px 12px rgba(0,0,0,.34), 0 24px 60px rgba(0,0,0,.34)";
    public static final String E4_CSS = E3_CSS + ", inset 0 1px 0 rgba(255,255,255,.16)";
    /** Native approximations: ambient elevation + shared inner highlight/shadow layers. */
    public static final float E1_DP = 2f;
    public static final float E2_DP = 6f;
    public static final float E3_DP = 10f;
    public static final float E4_DP = 12f;
    public static final int INNER_TOP_HIGHLIGHT = 0x33FFFFFF; // inset 0 1px 0 rgba(255,255,255,.20)
    public static final int INNER_BOTTOM_SHADE  = 0x2E000000;
    /** The single floating card of a page: lifted 4dp, e3, 25% gold hairline. */
    public static final float FLOATING_LIFT_DP = 4f;
    public static final int FLOATING_GOLD_HAIRLINE = 0x40C9A227; // rgba(201,162,39,.25)

    // ---------------------------------------------------------------- typography (§3.2)
    public static final float TYPE_HERO_SP = 42f;      // 40–44, weight 800, tabular
    public static final int TYPE_HERO_WEIGHT = 800;
    public static final float TYPE_HERO_LETTER_SP_EM = -1.2f / 42f;
    public static final float TYPE_KPI_SP = 30f;       // 28–32, weight 800, tabular
    public static final int TYPE_KPI_WEIGHT = 800;
    public static final float TYPE_H2_SP = 17.5f;      // 17–18, weight 800
    public static final int TYPE_H2_WEIGHT = 800;
    public static final float TYPE_BODY_SP = 14f;      // weight 400, line-height 1.9
    public static final float TYPE_BODY_LINE_HEIGHT = 1.9f;
    public static final float TYPE_LABEL_SP = 12f;     // weight 600
    public static final int TYPE_LABEL_WEIGHT = 600;
    public static final float TYPE_META_SP = 11.5f;    // weight 500, 60% ink
    public static final float TYPE_META_INK_FRACTION = 0.60f;
    public static final float TYPE_UNIT_SP = 12f;      // always smaller/dimmer than the figure
    public static final float TYPE_UNIT_INK_FRACTION = 0.55f;

    // ---------------------------------------------------------------- motion (§3.5)
    /** cubic-bezier(.22,1,.36,1) control points — the house easing. */
    public static final float EASE_X1 = 0.22f;
    public static final float EASE_Y1 = 1.0f;
    public static final float EASE_X2 = 0.36f;
    public static final float EASE_Y2 = 1.0f;
    public static final int DUR_FAST_MS = 220;
    public static final int DUR_MID_MS = 420;
    public static final int DUR_SLOW_MS = 700;         // nothing exceeds this…
    public static final int CARD_ENTRY_MS = 420;
    public static final int CARD_ENTRY_STAGGER_MS = 60;
    public static final float CARD_ENTRY_RISE_DP = 12f;
    public static final int COUNT_UP_MS = 560;         // 500–620, easeOutCubic, tabular
    public static final int CHART_DRAW_MS = 700;
    public static final int CHART_POINT_STAGGER_MS = 30;
    public static final int PRESS_MS = 90;
    public static final float PRESS_SCALE = 0.975f;
    public static final int RELEASE_MS = 220;
    public static final int URGENT_PULSE_MS = 2400;    // …except the single urgent marker…
    public static final float URGENT_PULSE_FROM = 0.30f;
    public static final float URGENT_PULSE_TO = 0.50f;
    public static final int SHIMMER_MS = 900;          // …and the once-per-session shimmer.
    public static final float TILT_MAX_DEG = 3f;
    public static final float TILT_EMA = 0.08f;
    public static final int TILT_RETURN_MS = 600;
    public static final int DOCK_TAB_LIFT_MS = 320;    // spring, 6dp lift
    public static final float DOCK_TAB_LIFT_DP = 6f;

    // ---------------------------------------------------------------- component metrics (§4)
    public static final int MEDAL_RADIUS_DP = 14;      // C3 rounded-square, metallic
    public static final int MEDAL_INNER_HIGHLIGHT = 0xFFFFFBEA; // inset 0 1px 0 #FFFBEA
    public static final int MEDAL_INNER_SHADE = 0x597A5C14;     // inset 0 -3px 6px rgba(122,92,20,.35)
    public static final int CHIP_HEIGHT_DP = 24;       // C4 status chip, radius 8 (never a pill)
    public static final int CHIP_RADIUS_DP = 8;
    public static final float CHIP_SOFT_FILL_FRACTION = 0.14f;
    public static final int CHIP_DOT_DP = 6;
    public static final int TOUCH_MIN_DP = 44;         // §2.7
    public static final int BUTTON_PRIMARY_HEIGHT_DP = 48;
    public static final int BUTTON_GHOST_HEIGHT_DP = 40;
    public static final int GHOST_GOLD_BORDER = 0x73E8CE7A; // rgba(232,206,122,.45)
    public static final float DISABLED_OPACITY = 0.40f;
    public static final int FOCUS_RING = GOLD_500;
    public static final int FOCUS_RING_WIDTH_DP = 2;
    public static final int FOCUS_RING_OFFSET_DP = 2;
    public static final float QUICK_TILE_HERO_SCALE = 1.25f; // C8 first tile
    public static final float QUICK_TILE_HOVER_LIFT_DP = 4f;
    public static final float QUICK_TILE_ACTIVE_SINK_DP = 2f;
    public static final int SPARKLINE_HEIGHT_DP = 54;  // C1
    public static final float CHART_LINE_DP = 2.5f;    // C12
    public static final int CHART_POINT_DP = 4;
    public static final int CHART_POINT_HOVER_DP = 8;
    public static final int CHART_POINT_HOVER_RING_DP = 12;
    public static final int CHART_POINT_PEAK_DP = 9;
    public static final int CHART_GRID_LINES = 3;
    public static final float CHART_GRID_OPACITY = 0.03f;
    public static final int CHART_MA_WINDOW_DAYS = 7;  // golden dashed moving average
    public static final int ALERT_EDGE_DP = 3;         // C13 border-inline-start, amber
    public static final int EMPTY_STATE_ART_DP = 120;  // C11

    // ---------------------------------------------------------------- themes (§3.1 surfaces)
    /** One resolved surface set per theme; components read fields, never raw palette. */
    public static final class Surface {
        public final boolean dark;
        public final String name;
        public final int bg;
        public final int bgDeep;
        public final int[] stone;
        public final int ink;
        public final int inkSecondary;
        public final int inkMeta;
        public final int hairline;
        public final int glassFill;
        public final int glassBorder;
        public final int goldText;      // the only legal gold ink for this theme
        public final int[] heroGold;    // hero-number gradient stops
        public final int successText;
        public final int warningText;
        public final int dangerText;
        public final int infoText;
        Surface(boolean dark, String name, int bg, int bgDeep, int[] stone, int ink,
                int inkSecondary, int inkMeta, int hairline, int glassFill, int glassBorder,
                int goldText, int[] heroGold, int successText, int warningText,
                int dangerText, int infoText) {
            this.dark = dark; this.name = name; this.bg = bg; this.bgDeep = bgDeep;
            this.stone = stone; this.ink = ink; this.inkSecondary = inkSecondary;
            this.inkMeta = inkMeta; this.hairline = hairline; this.glassFill = glassFill;
            this.glassBorder = glassBorder; this.goldText = goldText; this.heroGold = heroGold;
            this.successText = successText; this.warningText = warningText;
            this.dangerText = dangerText; this.infoText = infoText;
        }
    }

    /** مخمل کرم — cream velvet (default). Gold stays an edge/ink of lines, never bright text. */
    public static final Surface LIGHT = new Surface(false, "مخمل کرم",
            CREAM, CREAM_2, STONE_LIGHT,
            0xFF1F180C,   // ink: ≥ 7:1 on cream
            0xFF4E422C,   // secondary ink: ≥ 4.5:1
            0xFF5F5138,   // meta ink (spec paints it at 60%; token keeps the solid ≥4.5 hue)
            0x1A1F180C,   // hairline rgba(31,24,12,.10)
            GLASS_FILL_LIGHT, GLASS_BORDER_LIGHT,
            0xFF856310,   // light-theme gold ink: the spec's #8E6C14 measures 4.40:1 on cream,
                          // one step deeper to clear its own mandated 4.5:1 floor (#C9A227 is 2.6:1)
            GOLD_MATERIAL_LIGHT,
            SUCCESS,      // #2E7D5B reads ≥ 4.5:1 on stone white
            0xFF8A5A12,   // deeper amber for warning *text*; #C98A2E stays the fill/icon hue
            DANGER,
            INFO);

    /** شب زمرد — emerald night. Champagne gold ink is legal on obsidian. */
    public static final Surface DARK = new Surface(true, "شب زمرد",
            OBSIDIAN_EMERALD, EMERALD_800, STONE_DARK,
            0xFFF4EFDF,   // ink: ≥ 7:1 on obsidian
            0xFFCFC6AE,   // secondary ink
            0xFFA99F87,   // meta ink
            0x1FF4EFDF,   // hairline rgba(244,239,223,.12)
            GLASS_FILL_DARK, GLASS_BORDER_DARK,
            GOLD_300,
            GOLD_MATERIAL_DARK,
            0xFF57B389,   // lifted success for dark surfaces
            0xFFE0A94E,   // lifted warning
            0xFFE08A90,   // lifted danger
            0xFF8FB0C9);  // lifted info

    public static Surface theme(boolean dark) { return dark ? DARK : LIGHT; }

    // ---------------------------------------------------------------- contrast engine (§7)
    /** WCAG relative luminance of an opaque color. */
    public static double luminance(int argb) {
        double r = channel((argb >> 16) & 0xFF);
        double g = channel((argb >> 8) & 0xFF);
        double b = channel(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255d;
        return c <= 0.04045d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }

    /** WCAG contrast ratio (1..21) of two opaque colors. */
    public static double contrastRatio(int argbA, int argbB) {
        double la = luminance(argbA);
        double lb = luminance(argbB);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05d) / (lo + 0.05d);
    }

    /** True when every stop of a gradient keeps the minimum ratio on the given background. */
    public static boolean gradientMeets(int[] stops, int background, double minimum) {
        if (stops == null) return false;
        for (int stop : stops) {
            if (contrastRatio(stop, background) < minimum) return false;
        }
        return true;
    }

    /** Plain channel blend of two opaque colors (t = share of tint). */
    public static int blend(int base, int tint, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round((((base >> 16) & 0xFF) * (1f - k)) + (((tint >> 16) & 0xFF) * k));
        int g = Math.round((((base >> 8) & 0xFF) * (1f - k)) + (((tint >> 8) & 0xFF) * k));
        int b = Math.round(((base & 0xFF) * (1f - k)) + ((tint & 0xFF) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** C4 Soft chip: the semantic hue at 14% over the card surface carries the dark ink. */
    public static int softChip(int hue, int surface) {
        return blend(surface, hue, CHIP_SOFT_FILL_FRACTION);
    }
}
