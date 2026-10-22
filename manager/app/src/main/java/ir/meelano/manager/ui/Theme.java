package ir.meelano.manager.ui;

import ir.meelano.manager.R;
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
    public static int STEEL = 0xFF7C8DA6;
    /** Fixed hues for sales and purchases: they must stand out from the gold/theme accent. */
    public static final int SALES_HUE = 0xFFFF8A3D;
    public static final int BUY_HUE = 0xFFFF5FA2;

    private static boolean light = false;
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
    private static final int ACCENT_INK = 0xFF111827;
    private static final int ACCENT_PAPER = 0xFFFFFCF5;

    public static boolean isLight() { return light; }

    /**
     * Light skin uses rendered 3D gold icons in place of the flat vector glyphs. Dark skin keeps
     * the vectors. Returns the drawable to show for a vector glyph id.
     */
    public static int icon3d(int vector) {
        if (!light) return vector;
        if (vector == R.drawable.mi_point_of_sale) return R.drawable.icon3d_sales;
        if (vector == R.drawable.mi_shopping_cart) return R.drawable.icon3d_purchase;
        if (vector == R.drawable.mi_receipt_long) return R.drawable.icon3d_cash_in;
        if (vector == R.drawable.mi_payments) return R.drawable.icon3d_cash_out;
        return vector;
    }

    /** True for bitmap 3D icons, which must not be tinted. */
    public static boolean isIcon3d(int res) {
        return res == R.drawable.icon3d_sales || res == R.drawable.icon3d_purchase
                || res == R.drawable.icon3d_cash_in || res == R.drawable.icon3d_cash_out;
    }

    /** 1.0 normal, 1.3 large-text mode (applied in Kit.text). */
    public static float fontScale() { return fontScale; }

    /** Screen-aware type multiplier (1.00 on small phones, up to 1.18 on large ones). */
    public static float typeScale() { return typeScale; }

    /** Seasonal themes were removed: the app has exactly two curated themes now. */
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
        // Exactly two curated themes; the accent is part of each theme, not a user choice.
        int dark = 0xFFD4AF37, darkSoft = 0xFFF0D98C, lite = 0xFF9C7A1A, liteSoft = 0xFF5C4510;
        if (light) {
            // «عاج سلطنتی»: warm pearl background, near-white cards, clear separation.
            // Reference light skin: pearl-white base, champagne panels, metallic gold accents.
            BG = 0xFFFBF7EE;
            SURFACE = 0xFFFFFFFF;
            SURFACE2 = 0xFFF6EEDA;
            GOLD = 0xFF9C6B12;
            GOLD_SOFT = 0xFFC9A24A;
            TEXT = 0xFF14181F;
            MUTED = 0xFF6B6657;
            SUCCESS = 0xFF1B7A4B;
            DANGER = 0xFFB3261E;
            WARNING = 0xFF9A5B00;
            INFO = 0xFF1F5FAD;
            VIOLET = 0xFF6343D6;
            STEEL = 0xFF546177;
        } else {
            // «ابسیدین و طلا»: near-black obsidian surfaces, glass cards and a bright polished gold.
            BG = 0xFF07090E;
            SURFACE = 0xFF0E121A;
            SURFACE2 = 0xFF161B26;
            // Brighter, more luminous gold so every accent reads clearly on the obsidian surfaces.
            GOLD = 0xFFFFD45C;
            GOLD_SOFT = 0xFFFFEDB0;
            TEXT = 0xFFFAF6EC;
            MUTED = 0xFF9AA3B5;
            SUCCESS = 0xFF34D399;
            DANGER = 0xFFF87171;
            WARNING = 0xFFFBBF24;
            INFO = 0xFF60A5FA;
            VIOLET = 0xFF9A7BFF;
            STEEL = 0xFF7C8DA6;
        }
    }

    /**
     * Shared page backdrop for the licence and login screens. Dark: a cool platinum light
     * falling onto graphite black. Light: a warm gold glow over the ivory pearl base.
     */
    public static GradientDrawable backdrop() {
        int glow = light ? alpha(GOLD, 52) : alpha(GOLD, 40);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{glow, BG});
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
        return light ? 0xFFF5F1E6 : 0xFF0F172A;
    }

    /** Navigation-bar color: champagne in light mode so the dark system keys stay visible. */
    public static int navBar() {
        return light ? 0xFFEBE2CC : 0xFF07090E;
    }

    // ---------------- drawable factories ----------------
    private static GradientDrawable base(int fill, float radiusDp, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** Elevation for cards: deeper in dark mode, where the gold glow carries the depth. */
    public static float cardElevation() {
        return dp(light ? 4 : 10);
    }

    /**
     * Applies elevation and, on Android 9+, a tinted shadow: a soft black ambient shadow plus a
     * faint gold spot-glow on dark, so raised cards read as lit objects rather than grey boxes.
     */
    public static void lift(android.view.View v, float elevationPx) {
        v.setElevation(elevationPx);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                v.setOutlineAmbientShadowColor(light ? 0x2A3A2A10 : 0xCC000000);
                v.setOutlineSpotShadowColor(light ? 0x406B5210 : alpha(GOLD, 120));
            } catch (Exception ignored) { Diag.ignored("Theme:lift", ignored); }
        }
    }

    /**
     * Glass surface: a translucent body with a bright top sheen fading out and a fine gold
     * edge. Used by every card, so the whole app shares one material.
     */
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

    /** Standard glass card: translucent navy (or ivory) body with a top sheen and gold edge. */
    private static android.graphics.Bitmap paperGrain;

    /**
     * Screen background. Dark theme: the flat night colour. Light theme: ivory with a very
     * faint, repeating linen grain (a few percent alpha), so large cream areas feel like
     * paper instead of a flat digital fill. The grain tile is generated once and cached.
     */
    public static android.graphics.drawable.Drawable background() {
        android.graphics.drawable.ColorDrawable base = new android.graphics.drawable.ColorDrawable(BG);
        if (!light) {
            // Obsidian screen with a soft gold light from the top-left corner, as in the reference.
            try {
                GradientDrawable glow = new GradientDrawable();
                glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                glow.setColors(new int[]{alpha(GOLD, 34), alpha(GOLD, 0)});
                glow.setGradientCenter(0.06f, 0.02f);
                glow.setGradientRadius(dp(460));
                return new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{base, glow});
            } catch (Exception e) {
                return base;
            }
        }
        try {
            if (paperGrain == null) {
                int size = 96;
                paperGrain = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                java.util.Random rnd = new java.util.Random(7);
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++) {
                        // Fine horizontal and vertical fibres, very low alpha.
                        int fibre = ((x % 3 == 0) || (y % 4 == 0)) ? 5 : 0;
                        int a = Math.min(9, fibre + rnd.nextInt(4));
                        paperGrain.setPixel(x, y, (a << 24) | 0x5A4A2A);
                    }
            }
            android.graphics.drawable.BitmapDrawable tile = new android.graphics.drawable.BitmapDrawable(
                    null, paperGrain);
            tile.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
            return new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, tile});
        } catch (Exception e) {
            return base;
        }
    }

    public static android.graphics.drawable.Drawable card() {
        if (light) {
            return glass(0xFFFFFDF8, 0xFFF4ECDA, 22, 70);
        }
        return glass(alpha(SURFACE, 242), alpha(SURFACE, 214), 22, 92);
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
    public static android.graphics.drawable.Drawable executiveHero() {
        int accent = GOLD;
        if (light) {
            // Champagne-gold hero: a clearly golden sheen fading into ivory, with a brighter bronze rim.
            return glass(alpha(accent, 120), 0xFFF1E2C2, 28, 200);
        }
        // Obsidian hero with a soft gold light falling from the top-left corner.
        return glass(alpha(accent, 44), alpha(SURFACE, 250), 28, 150);
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
        d.setCornerRadius(dp(22));
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
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{alpha(accent, 34), alpha(SURFACE, 255)}
                        : new int[]{alpha(accent, 44), alpha(SURFACE, 255), shade(SURFACE, -0.06f)});
        d.setCornerRadius(dp(20));
        d.setStroke(dp(1), alpha(accent, light ? 70 : 84));
        return d;
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

    public static GradientDrawable bottomBar() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0xFFFFFDF6, 0xFFEFE6CF} : new int[]{0xFF0E121A, 0xFF07090E});
        // Rounded top edge with a gold hairline: the bar reads as a floating luxury dock.
        float r = dp(22);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        d.setStroke(dp(1), alpha(GOLD, light ? 70 : 120));
        return d;
    }

    public static GradientDrawable dialogBg() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{shade(SURFACE, light ? 0.02f : 0.08f), SURFACE});
        d.setCornerRadius(dp(26));
        d.setStroke(dp(1), alpha(GOLD, 90));
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
