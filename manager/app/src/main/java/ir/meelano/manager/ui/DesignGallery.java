package ir.meelano.manager.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;

/** Isolated Light/Dark previews for the shared shell and its reusable components. */
public final class DesignGallery {
    private DesignGallery() { }

    public static void addPreviews(MainActivity activity, LinearLayout content) {
        addPanel(activity, content, new Palette(false));
        addPanel(activity, content, new Palette(true));
    }

    private static void addPanel(MainActivity a, LinearLayout parent, Palette p) {
        LinearLayout panel = vertical(a);
        panel.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        panel.setBackground(rounded(p.background, p.border, 20));
        panel.setElevation(Theme.dp(1));

        TextView theme = text(a, p.dark ? "DARK · شب زمردی" : "LIGHT · عاج سلطنتی",
                13f, p.text, true);
        theme.setGravity(Gravity.CENTER_VERTICAL);
        theme.setPadding(Theme.dp(10), Theme.dp(7), Theme.dp(10), Theme.dp(7));
        theme.setBackground(rounded(p.surfaceAccent(DesignTokens.EMERALD), p.border, 14));
        panel.addView(theme, matchWrap());
        panel.addView(gap(a, 10));

        panel.addView(text(a, "هدر باز · حالت فشردهٔ ۵۶ dp", 11.5f, p.muted, true), matchWrap());
        panel.addView(gap(a, 5));
        panel.addView(headerPreview(a, p), matchWrap());
        panel.addView(gap(a, 10));

        panel.addView(text(a, "داک پنج‌مقصدی", 11.5f, p.muted, true), matchWrap());
        panel.addView(gap(a, 5));
        panel.addView(dockPreview(a, p), matchWrap());
        panel.addView(gap(a, 10));

        panel.addView(text(a, "گالری کامپوننت‌ها", 11.5f, p.muted, true), matchWrap());
        panel.addView(gap(a, 5));
        panel.addView(componentPreview(a, p), matchWrap());

        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = Theme.dp(12);
        parent.addView(panel, params);
    }

    private static View headerPreview(MainActivity a, Palette p) {
        LinearLayout card = vertical(a);
        card.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        card.setBackground(rounded(p.surface, p.border, 18));
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout brand = horizontal(a);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(a);
        mark.setBackground(gradient(DesignTokens.GOLD, DesignTokens.GOLD_LIGHT, 14));
        TextView m = text(a, "M", 19f, contrastText(DesignTokens.GOLD), true);
        m.setGravity(Gravity.CENTER);
        m.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mark.addView(m, new FrameLayout.LayoutParams(-1, -1));
        brand.addView(mark, new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));
        brand.addView(space(a, 9));

        LinearLayout copy = vertical(a);
        TextView name = text(a, "میلانو مدیریت", 16f, p.text, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name, matchWrap());
        TextView subtitle = text(a, "مدیریت فروشگاه", 10f, p.muted, false);
        subtitle.setMaxLines(1);
        copy.addView(subtitle, matchWrap());
        brand.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(brand, matchWrap());
        card.addView(gap(a, 8));
        card.addView(syncPill(a, p), matchWrap());
        return card;
    }

    private static View dockPreview(MainActivity a, Palette p) {
        LinearLayout dock = horizontal(a);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(Theme.dp(4), Theme.dp(5), Theme.dp(4), Theme.dp(5));
        dock.setBackground(rounded(p.surface, p.border, 20));
        String[] labels = {"خانه", "کالاها", "مشتریان", "دستیار", "اتاق فرمان"};
        int[] icons = {R.drawable.mi_home_fill, R.drawable.mi_inventory_2,
                R.drawable.mi_group, R.drawable.mi_star_shine, R.drawable.mi_widgets};
        int widthDp = a.getResources().getDisplayMetrics().widthPixels * 160
                / a.getResources().getDisplayMetrics().densityDpi;
        // Preserve the real 48dp tab targets inside the narrower inset gallery frame.
        int previewDockRoomDp = widthDp - 72;
        boolean showBackUtility = previewDockRoomDp >= (12 + 52 + labels.length * 48);
        if (showBackUtility) {
            FrameLayout back = new FrameLayout(a);
            back.setBackground(rounded(p.surfaceAccent(p.accent), p.border, 18));
            ImageView arrow = icon(a, R.drawable.mi_arrow_forward,
                    p.safeText(p.accent, p.surfaceAccent(p.accent)));
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            back.addView(arrow, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
            back.setContentDescription("بازگشت به صفحهٔ قبل؛ ابزار جدا از پنج مقصد");
            dock.addView(back, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(52)));
        }
        for (int i = 0; i < labels.length; i++) {
            final boolean selected = i == 0;
            LinearLayout tab = vertical(a);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(Theme.dp(2), Theme.dp(3), Theme.dp(2), Theme.dp(3));
            tab.setBackground(selected
                    ? rounded(p.surfaceAccent(p.accent), p.border, 16)
                    : transparentRounded(16));
            FrameLayout badge = new FrameLayout(a);
            ImageView icon = icon(a, icons[i], selected ? p.safeText(p.accent, p.surfaceAccent(p.accent)) : p.muted);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            badge.addView(icon, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
            tab.addView(badge, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(32)));
            TextView label = text(a, labels[i], 9.2f,
                    selected ? p.safeText(p.accent, p.surfaceAccent(p.accent)) : p.muted, selected);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            tab.addView(label, matchWrap());
            tab.setSelected(selected);
            tab.setContentDescription(labels[i] + (selected ? "، بخش فعال در پیش‌نمایش" : "، مقصد در پیش‌نمایش"));
            tab.setMinimumHeight(Theme.dp(56));
            dock.addView(tab, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        dock.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return dock;
    }

    private static View componentPreview(MainActivity a, Palette p) {
        LinearLayout group = vertical(a);
        group.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        group.setBackground(rounded(p.surface, p.border, 18));

        group.addView(sectionHeaderSample(a, p), matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "LuxCard · KpiChip"), matchWrap());
        group.addView(gap(a, 4));
        LinearLayout metrics = horizontal(a);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.addView(metric(a, p, "فروش امروز", "۸٬۴۲۰", "تومان"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        metrics.addView(space(a, 8));
        metrics.addView(metric(a, p, "سفارش‌ها", "۱۲۶", "فاکتور"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        group.addView(metrics, matchWrap());
        group.addView(gap(a, 8));

        group.addView(componentLabel(a, p, "DeltaBadge · نشانگر معنایی"), matchWrap());
        group.addView(gap(a, 4));
        LinearLayout deltas = horizontal(a);
        deltas.setGravity(Gravity.CENTER_VERTICAL);
        deltas.addView(delta(a, p, "▲ ۱۲٪", DesignTokens.POSITIVE, "افزایش ۱۲ درصد"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        deltas.addView(space(a, 8));
        deltas.addView(delta(a, p, "▼ ۴٪", DesignTokens.NEGATIVE, "کاهش ۴ درصد"),
                new LinearLayout.LayoutParams(0, -2, 1f));
        group.addView(deltas, matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "SegmentedControl"), matchWrap());
        group.addView(gap(a, 4));
        group.addView(segmented(a, p), matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "EmptyRow"), matchWrap());
        group.addView(gap(a, 4));
        group.addView(emptyRow(a, p), matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "SyncPill"), matchWrap());
        group.addView(gap(a, 4));
        group.addView(syncPill(a, p), matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "Skeleton / Shimmer"), matchWrap());
        group.addView(gap(a, 4));
        group.addView(skeleton(a, p), matchWrap());
        group.addView(gap(a, 8));
        group.addView(componentLabel(a, p, "GoldHairline"), matchWrap());
        group.addView(gap(a, 4));
        group.addView(goldHairline(a), new LinearLayout.LayoutParams(-1, Theme.dp(1)));
        return group;
    }

    private static View sectionHeaderSample(MainActivity a, Palette p) {
        LinearLayout header = vertical(a);
        LinearLayout row = horizontal(a);
        ImageView icon = icon(a, R.drawable.mi_bar_chart, p.safeText(p.accent, p.surface));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
        row.addView(space(a, 7));
        TextView title = text(a, "شاخص‌های امروز", 12.5f,
                p.safeText(p.accent, p.surface), true);
        title.setMaxLines(2);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setContentDescription("SectionHeader؛ شاخص‌های امروز");
        header.addView(row, matchWrap());
        View line = new View(a);
        int mid = DesignTokens.mix(p.surface, p.accent, 0.55f);
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{p.surface, mid, p.accent}));
        LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(-1, Theme.dp(1));
        rule.topMargin = Theme.dp(5);
        header.addView(line, rule);
        return header;
    }

    private static TextView componentLabel(MainActivity a, Palette p, String label) {
        TextView text = text(a, label, 9.5f, p.muted, true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private static View metric(MainActivity a, Palette p, String label, String value, String unit) {
        LinearLayout card = vertical(a);
        card.setPadding(Theme.dp(9), Theme.dp(9), Theme.dp(9), Theme.dp(9));
        card.setBackground(rounded(p.surfaceAccent(p.accent), p.border, 16));
        TextView title = text(a, label, 9.8f, p.muted, false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, matchWrap());

        LinearLayout amount = horizontal(a);
        amount.setGravity(Gravity.CENTER_VERTICAL);
        TextView figure = text(a, value, 18f, p.text, true);
        figure.setSingleLine(true);
        figure.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        amount.addView(figure, new LinearLayout.LayoutParams(0, -2, 1f));
        amount.addView(space(a, 3));
        TextView currency = text(a, unit, 8.8f, p.muted, false);
        currency.setSingleLine(true);
        currency.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        amount.addView(currency, wrapWrap());
        String spoken = label + "، " + value + " " + unit;
        amount.setContentDescription(spoken);
        card.addView(amount, matchWrap());
        return card;
    }

    private static View delta(MainActivity a, Palette p, String label, int tone, String spoken) {
        int fill = p.surfaceAccent(tone);
        TextView badge = text(a, label, 10.5f, p.safeText(tone, fill), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
        badge.setMinHeight(Theme.dp(36));
        badge.setBackground(rounded(fill, p.border, 18));
        badge.setContentDescription(spoken);
        return badge;
    }

    private static View segmented(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));
        row.setBackground(rounded(p.background, p.border, 16));
        String[] labels = {"روز", "هفته", "ماه"};
        for (int i = 0; i < labels.length; i++) {
            boolean selected = i == 1;
            TextView segment = text(a, labels[i], 10.5f,
                    selected ? contrastText(p.accent) : p.text, selected);
            segment.setGravity(Gravity.CENTER);
            segment.setMaxLines(2);
            segment.setPadding(Theme.dp(3), Theme.dp(7), Theme.dp(3), Theme.dp(7));
            segment.setBackground(selected
                    ? rounded(p.accent, p.border, 12)
                    : transparentRounded(12));
            segment.setContentDescription(labels[i] + (selected ? "، بازهٔ انتخاب‌شده" : "، بازهٔ زمانی"));
            row.addView(segment, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    private static View emptyRow(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(8), Theme.dp(5), Theme.dp(8), Theme.dp(5));
        FrameLayout mark = new FrameLayout(a);
        mark.setBackground(rounded(p.surfaceAccent(DesignTokens.POSITIVE), p.border, 14));
        ImageView icon = icon(a, R.drawable.mi_check_circle, p.safeText(
                DesignTokens.POSITIVE, p.surfaceAccent(DesignTokens.POSITIVE)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mark.addView(icon, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        row.addView(mark, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        row.addView(space(a, 8));
        TextView message = text(a, "همه‌چیز مرتب است", 11f, p.muted, false);
        message.setMaxLines(2);
        message.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(message, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setContentDescription("حالت خالی؛ همه‌چیز مرتب است");
        return row;
    }

    private static View syncPill(MainActivity a, Palette p) {
        int accent = p.accent;
        LinearLayout pill = horizontal(a);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Theme.dp(9), Theme.dp(6), Theme.dp(9), Theme.dp(6));
        pill.setMinimumHeight(Theme.dp(48));
        int fill = p.surfaceAccent(accent);
        pill.setBackground(rounded(fill, p.border, 16));

        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(rounded(p.surface, p.border, 12));
        ImageView icon = icon(a, R.drawable.mi_check_circle, p.safeText(accent, p.surface));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        badge.addView(icon, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        pill.addView(badge, new LinearLayout.LayoutParams(Theme.dp(34), Theme.dp(34)));
        pill.addView(space(a, 7));

        LinearLayout copy = vertical(a);
        TextView title = text(a, "همگام‌سازی آماده است", 10.8f, p.text, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, matchWrap());
        TextView detail = text(a, "آخرین بررسی: اکنون", 9.2f, p.muted, false);
        detail.setMaxLines(1);
        copy.addView(detail, matchWrap());
        copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pill.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        pill.setContentDescription("همگام‌سازی آماده است؛ آخرین بررسی اکنون");
        return pill;
    }

    private static View skeleton(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(9), Theme.dp(8), Theme.dp(9), Theme.dp(8));
        row.setBackground(rounded(p.background, p.border, 14));
        View circle = new View(a);
        circle.setBackground(rounded(p.surfaceAccent(p.accent), p.border, 16));
        row.addView(circle, new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32)));
        row.addView(space(a, 8));
        LinearLayout bars = vertical(a);
        bars.addView(skeletonBar(a, p, 0.82f), new LinearLayout.LayoutParams(-1, Theme.dp(8)));
        bars.addView(gap(a, 5));
        bars.addView(skeletonBar(a, p, 0.56f), new LinearLayout.LayoutParams(-1, Theme.dp(6)));
        row.addView(bars, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setContentDescription("پیش‌نمایش اسکلت بارگذاری؛ حرکت ندارد");
        return row;
    }

    private static View skeletonBar(MainActivity a, Palette p, float width) {
        View bar = new View(a);
        bar.setBackground(rounded(DesignTokens.mix(p.surface, p.muted, p.dark ? 0.14f : 0.12f), 0, 8));
        bar.setMinimumWidth(Theme.dp(Math.round(190 * width)));
        bar.setAlpha(0.88f);
        return bar;
    }

    private static View goldHairline(MainActivity a) {
        View line = new View(a);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{DesignTokens.GOLD_DARK, DesignTokens.GOLD, DesignTokens.GOLD_LIGHT,
                        DesignTokens.GOLD, DesignTokens.GOLD_DARK});
        line.setBackground(gradient);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return line;
    }

    private static TextView text(MainActivity a, String value, float sp, int color, boolean bold) {
        TextView text = new TextView(a);
        text.setText(value == null ? "" : value);
        float scale = sp < 12f ? Theme.fontScale() : Theme.fontScale() * Theme.typeScale();
        float baseSize = sp < 9f ? 9f : sp;
        text.setTextSize(baseSize * scale);
        text.setTextColor(color);
        text.setTypeface(Theme.face(bold));
        text.setFontFeatureSettings("tnum");
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setLineSpacing(Theme.dp(1), 1f);
        return text;
    }

    private static ImageView icon(MainActivity a, int drawable, int tint) {
        ImageView image = new ImageView(a);
        image.setImageResource(drawable);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setColorFilter(tint);
        return image;
    }

    private static LinearLayout vertical(MainActivity a) {
        LinearLayout view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return view;
    }

    private static LinearLayout horizontal(MainActivity a) {
        LinearLayout view = new LinearLayout(a);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return view;
    }

    private static View space(MainActivity a, int dp) {
        View space = new View(a);
        space.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(dp), 1));
        return space;
    }

    private static View gap(MainActivity a, int dp) {
        View gap = new View(a);
        gap.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(dp)));
        return gap;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private static GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(Theme.dp(radiusDp));
        if (stroke != 0) drawable.setStroke(Theme.dp(1), stroke);
        return drawable;
    }

    private static GradientDrawable transparentRounded(int radiusDp) {
        return rounded(0x00000000, 0, radiusDp);
    }

    private static GradientDrawable gradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(Theme.dp(radiusDp));
        return drawable;
    }

    private static int contrastText(int background) {
        return DesignTokens.accentTextColor(background);
    }

    private static final class Palette {
        final boolean dark;
        final int background;
        final int surface;
        final int text;
        final int muted;
        final int border;
        final int accent;

        Palette(boolean dark) {
            this.dark = dark;
            background = dark ? DesignTokens.NIGHT_BG : DesignTokens.IVORY;
            surface = dark ? DesignTokens.NIGHT_SURFACE : DesignTokens.IVORY;
            text = dark ? DesignTokens.NIGHT_TEXT : DesignTokens.INK;
            muted = dark ? DesignTokens.NIGHT_MUTED : DesignTokens.LIGHT_MUTED;
            border = dark ? 0xFF315244 : 0xFFE5DFD2;
            accent = dark ? DesignTokens.NIGHT_EMERALD : DesignTokens.EMERALD;
        }

        int surfaceAccent(int tone) {
            return DesignTokens.surfaceTint(tone, dark);
        }

        int safeText(int preferred, int actualSurface) {
            return DesignTokens.textSafeColor(preferred, actualSurface, dark);
        }
    }
}
