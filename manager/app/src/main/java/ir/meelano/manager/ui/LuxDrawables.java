package ir.meelano.manager.ui;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;



/**
 * «درخشان ۲.۰» drawable factory — every surface of the jewel-in-velvet language.
 *
 * The spec's light source (150°/165° from the top-right) maps to Android's TL_BR
 * orientation, the closest of the eight fixed axes; straight-top materials use TOP_BOTTOM.
 * All colors come from {@link DerakhshanTokens}; nothing here invents a hue.
 */
public final class LuxDrawables {

    private LuxDrawables() { }

    private static final GradientDrawable.Orientation SHEEN =
            GradientDrawable.Orientation.TL_BR;          // ≈ the 150–165° house light
    private static final GradientDrawable.Orientation DOWN =
            GradientDrawable.Orientation.TOP_BOTTOM;

    /** C1/stone: the main data card material. */
    public static GradientDrawable stone(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable(SHEEN, s.stone);
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), s.hairline);
        return d;
    }

    /** Glass over colored backgrounds (hero card, dock, tooltip). */
    public static GradientDrawable glass(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(s.glassFill);
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), s.glassBorder);
        return d;
    }

    /** C3 medal frame: metallic gradient of the semantic family with a lit top edge. */
    public static GradientDrawable medal(DerakhshanTokens.Surface s, int family) {
        GradientDrawable d = new GradientDrawable(DOWN, new int[]{
                DerakhshanTokens.blend(family, 0xFFFFFFFF, s.dark ? 0.28f : 0.45f),
                family,
                DerakhshanTokens.blend(family, 0xFF000000, s.dark ? 0.34f : 0.22f)});
        d.setCornerRadius(dp(DerakhshanTokens.MEDAL_RADIUS_DP));
        d.setStroke(dp(1), alpha(DerakhshanTokens.blend(DerakhshanTokens.GOLD_100, family, 0.35f),
                s.dark ? 110 : 150));
        return d;
    }

    /** The 120dp empty-state art disc: soft family glow, never a gray circle. */
    public static GradientDrawable medalArt(DerakhshanTokens.Surface s, int family) {
        GradientDrawable d = new GradientDrawable(DOWN, new int[]{
                DerakhshanTokens.blend(s.stone[0], family, s.dark ? 0.22f : 0.16f),
                DerakhshanTokens.blend(s.stone[1], family, s.dark ? 0.10f : 0.06f)});
        d.setCornerRadius(dp(60));
        d.setStroke(dp(1), alpha(family, s.dark ? 90 : 70));
        return d;
    }

    /** C4 status chip kinds: 0 soft, 1 solid (urgent only), 2 gold, 3 ghost. */
    public static GradientDrawable chipBg(DerakhshanTokens.Surface s, int kind, int family) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(DerakhshanTokens.CHIP_RADIUS_DP));
        if (kind == 1) {
            d.setColor(family);
        } else if (kind == 2) {
            d.setOrientation(SHEEN);
            d.setColors(s.dark ? DerakhshanTokens.GOLD_MATERIAL_DARK : DerakhshanTokens.GOLD_MATERIAL_LIGHT);
            d.setStroke(dp(1), alpha(DerakhshanTokens.GOLD_300, 120));
        } else if (kind == 3) {
            d.setColor(0x00000000);
            d.setStroke(dp(1), s.hairline);
        } else {
            d.setColor(DerakhshanTokens.softChip(family, s.stone[0]));
        }
        return d;
    }

    /**
     * C5 button backgrounds with the mandated state set: default / hovered / pressed /
     * focused (gold ring) / disabled (the view also drops to 40% alpha). Loading swaps
     * the icon for a spinner at the call site, the background stays put.
     */
    public static StateListDrawable buttonBg(DerakhshanTokens.Surface s, int kind) {
        StateListDrawable states = new StateListDrawable();
        if (kind == 0 || kind == 1) {
            int[] body = kind == 0 ? DerakhshanTokens.EMERALD_MATERIAL : goldStops(s);
            states.addState(new int[]{android.R.attr.state_pressed}, metal(s, body, -0.10f));
            states.addState(new int[]{android.R.attr.state_hovered}, metal(s, body, 0.10f));
            states.addState(new int[]{android.R.attr.state_focused}, ringed(s, body));
            states.addState(new int[]{-android.R.attr.state_enabled}, flat(s, body, 0.35f));
            states.addState(new int[]{}, metal(s, body, 0f));
        } else if (kind == 2) {
            states.addState(new int[]{android.R.attr.state_pressed}, wash(s, DerakhshanTokens.GOLD_300, 0.16f));
            states.addState(new int[]{android.R.attr.state_hovered}, wash(s, DerakhshanTokens.GOLD_300, 0.10f));
            states.addState(new int[]{android.R.attr.state_focused}, ghostRing(s));
            states.addState(new int[]{}, ghostGold(s));
        } else {
            states.addState(new int[]{android.R.attr.state_pressed}, wash(s, s.ink, 0.10f));
            states.addState(new int[]{android.R.attr.state_hovered}, wash(s, s.ink, 0.06f));
            states.addState(new int[]{android.R.attr.state_focused}, ghostRing(s));
            states.addState(new int[]{}, plain(0x00000000));
        }
        return states;
    }

    /** C6 dock: glass slab with a 1px lit top edge. */
    public static GradientDrawable dockGlass(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(s.glassFill);
        d.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        d.setStroke(dp(1), s.glassBorder);
        return d;
    }

    /** C7 segmented control: quiet track + sliding metallic indicator. */
    public static GradientDrawable segTrack(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(DerakhshanTokens.blend(s.stone[1], s.ink, s.dark ? 0.10f : 0.05f));
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), s.hairline);
        return d;
    }

    public static GradientDrawable segIndicator(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable(DOWN, new int[]{
                DerakhshanTokens.blend(DerakhshanTokens.EMERALD_500, 0xFFFFFFFF, s.dark ? 0.18f : 0.28f),
                DerakhshanTokens.EMERALD_700});
        d.setCornerRadius(dp(10));
        d.setStroke(dp(1), alpha(DerakhshanTokens.GOLD_300, s.dark ? 90 : 120));
        return d;
    }

    /** C8 quick tiles: hero tile wears emerald metal, the rest wear stone. */
    public static GradientDrawable quickTile(DerakhshanTokens.Surface s, boolean hero) {
        if (hero) {
            GradientDrawable d = new GradientDrawable(SHEEN, DerakhshanTokens.EMERALD_MATERIAL);
            d.setCornerRadius(dp(18));
            d.setStroke(dp(1), alpha(DerakhshanTokens.GOLD_300, 110));
            return d;
        }
        return stone(s);
    }

    /** C13 alert strip: soft amber field; the 3dp edge is a child view (border-inline-start). */
    public static GradientDrawable alertBg(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(DerakhshanTokens.softChip(DerakhshanTokens.WARNING, s.stone[0]));
        d.setCornerRadius(dp(12));
        return d;
    }

    /** C12 glass tooltip. */
    public static GradientDrawable tooltipGlass(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(s.dark ? 0xD804120D : 0xE6FFFFFF);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), s.glassBorder);
        return d;
    }

    /** C9 gauge track hairline. */
    public static int gaugeTrackColor(DerakhshanTokens.Surface s) {
        return DerakhshanTokens.blend(s.stone[1], s.ink, s.dark ? 0.16f : 0.10f);
    }

    // ---------------------------------------------------------------- internals
    private static int[] goldStops(DerakhshanTokens.Surface s) {
        return s.dark ? DerakhshanTokens.GOLD_MATERIAL_DARK : DerakhshanTokens.GOLD_MATERIAL_LIGHT;
    }

    private static GradientDrawable metal(DerakhshanTokens.Surface s, int[] body, float lift) {
        int top = DerakhshanTokens.blend(body[0], 0xFFFFFFFF,
                Math.max(0f, Math.min(1f, (s.dark ? 0.16f : 0.24f) + lift)));
        int bottom = DerakhshanTokens.blend(body[body.length - 1], 0xFF000000,
                Math.max(0f, Math.min(1f, 0.14f - lift)));
        GradientDrawable d = new GradientDrawable(DOWN, new int[]{top, body[0], bottom});
        d.setCornerRadius(dp(12));
        return d;
    }

    private static GradientDrawable ringed(DerakhshanTokens.Surface s, int[] body) {
        GradientDrawable d = metal(s, body, 0f);
        d.setStroke(dp(DerakhshanTokens.FOCUS_RING_WIDTH_DP), DerakhshanTokens.FOCUS_RING);
        return d;
    }

    private static GradientDrawable flat(DerakhshanTokens.Surface s, int[] body, float tint) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(DerakhshanTokens.blend(s.stone[1], body[0], tint));
        d.setCornerRadius(dp(12));
        return d;
    }

    private static GradientDrawable wash(DerakhshanTokens.Surface s, int hue, float fraction) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(DerakhshanTokens.blend(s.stone[0], hue, fraction));
        d.setCornerRadius(dp(12));
        return d;
    }

    private static GradientDrawable ghostGold(DerakhshanTokens.Surface s) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), DerakhshanTokens.GHOST_GOLD_BORDER);
        return d;
    }

    private static GradientDrawable ghostRing(DerakhshanTokens.Surface s) {
        GradientDrawable d = ghostGold(s);
        d.setStroke(dp(DerakhshanTokens.FOCUS_RING_WIDTH_DP), DerakhshanTokens.FOCUS_RING);
        return d;
    }

    private static GradientDrawable plain(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        return d;
    }

    private static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
    }

    private static int dp(float v) {
        return Theme.dp(v);
    }
}
