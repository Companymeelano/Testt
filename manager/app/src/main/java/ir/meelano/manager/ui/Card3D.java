package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Beveled 3D card kit for the license experience: every panel is a little
 * slab — a soft drop shadow, an accent «side wall» peeking at the bottom,
 * and a face with a top-down sheen. Built purely from the {@link Theme}
 * grammar so Yalda / light modes keep working untouched.
 */
public final class Card3D {
    private Card3D() { }

    /** A tactile slab card with an accent bevel. Add children with your own params. */
    public static LinearLayout card(Context ctx, int accent) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(slab(accent));
        try {
            c.setElevation(Theme.dp(4));
        } catch (Exception ignored) { }
        c.setPadding(Theme.dp(14), Theme.dp(13), Theme.dp(14), Theme.dp(13));
        return c;
    }

    /** Mount a card into a vertical list with the standard rhythm. */
    public static void mount(LinearLayout parent, android.view.View card) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, Theme.dp(12));
        parent.addView(card, p);
    }

    /** Step header: a glossy circular numeral badge + a bold title (RTL-safe). */
    public static LinearLayout stepRow(Context ctx, Kit kit, String numeral, int accent, String title) {
        LinearLayout row = kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = kit.text(numeral, 15, 0xFFFFFFFF, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.shade(accent, 0.30f), accent, Theme.shade(accent, -0.30f)});
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(Theme.dp(1), Theme.alpha(0xFFFFFFFF, Theme.isLight() ? 120 : 70));
        badge.setBackground(bg);
        try {
            badge.setElevation(Theme.dp(3));
        } catch (Exception ignored) { }
        int s = Theme.dp(30);
        row.addView(badge, new LinearLayout.LayoutParams(s, s));
        row.addView(kit.space(10));
        TextView t = kit.text(title, 14.5f, Theme.TEXT, true);
        row.addView(t, kit.wlp(1f));
        return row;
    }

    /** Small 3D glyph chip used inside step cards (SMS bolt, chat, …). */
    public static ImageView glyph(Context ctx, int iconRes, int glow, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        int sz = Theme.dp(sizeDp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        iv.setBackground(radialGlow(glow, sizeDp));
        iv.setImageResource(iconRes);
        int pad = Theme.dp(Math.max(4, sizeDp / 7));
        iv.setPadding(pad, pad, pad, pad);
        return iv;
    }

    /**
     * Hero panel: radial glow + big 3D icon + title + subtitle + status pill.
     */
    public static LinearLayout hero(Context ctx, Kit kit, int iconRes, int glow,
                                    String title, String sub, String pill, int pillColor) {
        LinearLayout c = card(ctx, glow);
        c.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView iv = glyph(ctx, iconRes, glow, 84);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(84), Theme.dp(84));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(iv, ip);

        TextView t = kit.text(title, 20, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, kit.lp(-1, -2));
        TextView s = kit.text(sub, 12.5f, Theme.MUTED, false);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = kit.lp(-1, -2);
        sp.setMargins(0, Theme.dp(2), 0, Theme.dp(8));
        c.addView(s, sp);

        TextView p = kit.text(pill, 13, pillColor, true);
        p.setGravity(Gravity.CENTER);
        p.setBackground(Theme.pill(pillColor));
        int px = Theme.dp(16), py = Theme.dp(6);
        p.setPadding(px, py, px, py);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-2, -2);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(p, pp);
        return c;
    }

    // ---------- layers ----------

    private static Drawable slab(int accent) {
        boolean light = Theme.isLight();
        // 0 — soft drop shadow peeking below.
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(Theme.alpha(0xFF000000, light ? 50 : 110));
        shadow.setCornerRadius(Theme.dp(20));
        // 1 — accent «side wall», peeking at the bottom like a slab edge.
        GradientDrawable edge = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.alpha(accent, light ? 120 : 170),
                        Theme.alpha(accent, light ? 200 : 235)});
        edge.setCornerRadius(Theme.dp(20));
        // 2 — face with a top-down sheen.
        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.shade(Theme.SURFACE, light ? 0.10f : 0.14f),
                        Theme.SURFACE,
                        Theme.shade(Theme.SURFACE, light ? -0.08f : -0.25f)});
        face.setCornerRadius(Theme.dp(18));
        face.setStroke(Theme.dp(1), Theme.alpha(accent, light ? 100 : 130));
        LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, edge, face});
        int s = Theme.dp(3);
        layers.setLayerInset(0, s, Theme.dp(6), s, 0);
        layers.setLayerInset(1, 0, 0, 0, Theme.dp(2));
        layers.setLayerInset(2, Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(4));
        return layers;
    }

    private static Drawable radialGlow(int color, int sizeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Theme.dp(sizeDp * 0.75f));
        g.setColors(new int[]{Theme.alpha(color, Theme.isLight() ? 70 : 110),
                Theme.alpha(color, 0)});
        return g;
    }
}
