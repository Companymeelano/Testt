package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared flat surfaces and small icon treatments for the license and setup flows. */
public final class Card3D {
    private Card3D() { }

    /** Mount a simple surface card with the standard vertical rhythm. */
    public static LinearLayout card(Context ctx, int accent) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(surface());
        card.setPadding(Theme.dp(14), Theme.dp(13), Theme.dp(14), Theme.dp(13));
        return card;
    }

    /** Mount a card into a vertical list with consistent spacing. */
    public static void mount(LinearLayout parent, android.view.View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, Theme.dp(10));
        parent.addView(card, params);
    }

    /** Compact numbered step heading; the number stays visible without a glossy badge. */
    public static LinearLayout stepRow(Context ctx, Kit kit, String numeral, int accent, String title) {
        LinearLayout row = kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = kit.text(numeral, 12.5f, Theme.sectionText(accent), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(surface());
        int size = Theme.dp(30);
        row.addView(badge, new LinearLayout.LayoutParams(size, size));
        row.addView(kit.space(9));
        TextView heading = kit.text(title, 14f, Theme.TEXT, true);
        row.addView(heading, kit.wlp(1f));
        return row;
    }

    /** Small, theme-tinted vector icon on an understated neutral tile. */
    public static ImageView glyph(Context ctx, int iconRes, int accent, int sizeDp) {
        ImageView icon = new ImageView(ctx);
        int size = Theme.dp(sizeDp);
        icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        icon.setBackground(surface());
        icon.setImageResource(iconRes);
        icon.setColorFilter(Theme.sectionText(accent));
        int padding = Theme.dp(Math.max(4, sizeDp / 7));
        icon.setPadding(padding, padding, padding, padding);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    /** Flat setup hero; title, subtitle and status copy remain unchanged. */
    public static LinearLayout hero(Context ctx, Kit kit, int iconRes, int accent,
                                    String title, String sub, String pill, int pillColor) {
        LinearLayout card = card(ctx, accent);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = glyph(ctx, iconRes, accent, 40);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Theme.dp(40), Theme.dp(40));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(icon, iconParams);

        TextView heading = kit.text(title, 18f, Theme.TEXT, true);
        heading.setGravity(Gravity.CENTER);
        card.addView(heading, kit.lp(-1, -2));
        TextView subtitle = kit.text(sub, 12f, Theme.MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = kit.lp(-1, -2);
        subtitleParams.setMargins(0, Theme.dp(3), 0, Theme.dp(6));
        card.addView(subtitle, subtitleParams);

        TextView status = kit.text(pill, 12f, Theme.sectionText(pillColor), true);
        status.setGravity(Gravity.CENTER);
        status.setBackground(Theme.pill(pillColor));
        status.setPadding(Theme.dp(12), Theme.dp(5), Theme.dp(12), Theme.dp(5));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.gravity = Gravity.CENTER_HORIZONTAL;
        status.setMinHeight(Theme.dp(32));
        card.addView(status, statusParams);
        return card;
    }

    private static Drawable surface() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.SURFACE);
        background.setCornerRadius(Theme.dp(12));
        background.setStroke(Theme.dp(1), Theme.BORDER);
        return background;
    }
}
