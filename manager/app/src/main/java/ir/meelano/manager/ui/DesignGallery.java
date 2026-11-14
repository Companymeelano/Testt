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

/** Visual-only previews of the shared shell; no business or financial sample data is shown. */
public final class DesignGallery {
    private DesignGallery() { }

    public static void addPreviews(MainActivity activity, LinearLayout content) {
        addPanel(activity, content, new Palette(false));
        addPanel(activity, content, new Palette(true));
        addDerakhshan(activity, content, DerakhshanTokens.theme(false), "درخشان ۲.۰ — مخمل کرم");
        addDerakhshan(activity, content, DerakhshanTokens.theme(true), "درخشان ۲.۰ — شب زمرد");
    }

    /**
     * Component gallery for the Derakhshan 2.0 kit (C1–C13): every component is rendered
     * once per theme surface with neutral demo values, so the six button states
     * (default/hover/pressed/loading/disabled/focus-visible), the dock, segmented control,
     * gauge, balance card, empty state, LuxChart outlier handling and alert strip are all
     * visible in-app without touching business screens.
     */
    private static void addDerakhshan(MainActivity a, LinearLayout parent,
                                      DerakhshanTokens.Surface s, String title) {
        LinearLayout panel = vertical(a);
        panel.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        panel.setBackground(rounded(s.bg, s.hairline, 16));

        panel.addView(text(a, title, 14f, s.ink, true), match());
        panel.addView(gap(a, 6));
        panel.addView(text(a, "کامپوننت‌های C1–C13 · مقادیر نمایشی · شش حالت دکمه: پیش‌فرض / لمسی / فشار / بارگذاری / غیرفعال / فوکوس",
                10.5f, s.inkSecondary, false), match());
        panel.addView(gap(a, 12));

        // C1 hero data card (+ C3 medal, C4 chip, C5 actions, LuxSpark inline)
        panel.addView(LuxKit.heroDataCard(a, s, R.drawable.mi_sell, DerakhshanTokens.EMERALD_500,
                        "فروش دیروز", "فعال", LuxKit.CHIP_SOFT,
                        "۴۰ فاکتور • میانگین هم‌نام ۳۳۸٬۰۶۸٬۱۹۸",
                        "۴۹۴٬۰۰۰٬۰۰", "تومان", "+۴۶٫۱٪", DerakhshanTokens.SUCCESS,
                        new double[]{2d, 4d, 3d, 6d, 5d, 8d, 7d, 10d},
                        "ثبت فاکتور", null, "نمایش جزئیات", null),
                match());
        panel.addView(gap(a, 10));

        // C2 stat row (three KPIs)
        panel.addView(LuxKit.statRow(a, s,
                        new String[]{"دریافت نقد", "پرداخت", "خالص روز"},
                        new String[]{"۳۲۰٬۰۰۰٬۰۰", "۱۸۰٬۰۰۰٬۰۰", "۱۴۰٬۰۰۰٬۰۰"},
                        new String[]{"+۱۲٪", "−۴٪", null},
                        new int[]{DerakhshanTokens.SUCCESS, DerakhshanTokens.DANGER, 0}),
                match());
        panel.addView(gap(a, 10));

        // C3 medal set + C4 chip families
        LinearLayout row = horizontal(a);
        int[] families = {DerakhshanTokens.EMERALD_500, DerakhshanTokens.GOLD_500,
                DerakhshanTokens.SUCCESS, DerakhshanTokens.WARNING,
                DerakhshanTokens.DANGER, DerakhshanTokens.INFO};
        int[] glyph = {R.drawable.mi_home, R.drawable.mi_star_shine, R.drawable.mi_group,
                R.drawable.mi_widgets, R.drawable.mi_inventory_2, R.drawable.mi_diamond};
        for (int i = 0; i < families.length; i++) {
            row.addView(LuxKit.medalIcon(a, s, glyph[i], families[i], 40));
            if (i < families.length - 1) row.addView(gapW(a, 8));
        }
        panel.addView(row, match());
        panel.addView(gap(a, 10));
        LinearLayout chipRow = horizontal(a);
        String[] chipNames = {"نرم", "توپر", "طلایی", "خطی"};
        int[] chipKinds = {LuxKit.CHIP_SOFT, LuxKit.CHIP_SOLID, LuxKit.CHIP_GOLD, LuxKit.CHIP_GHOST};
        for (int i = 0; i < chipKinds.length; i++) {
            chipRow.addView(LuxKit.statusChip(a, s, chipNames[i], chipKinds[i],
                    i == 2 ? DerakhshanTokens.GOLD_500 : DerakhshanTokens.EMERALD_500, false));
            if (i < chipKinds.length - 1) chipRow.addView(gapW(a, 8));
        }
        panel.addView(chipRow, match());
        panel.addView(gap(a, 12));

        // C5 buttons: all six states, both variants
        panel.addView(text(a, "C5 · شش حالت دکمه", 11.5f, s.inkSecondary, true), match());
        panel.addView(gap(a, 6));
        LinearLayout b1 = horizontal(a);
        b1.addView(LuxKit.luxButton(a, s, "پیش‌فرض", LuxKit.PRIMARY, null), wrapM());
        b1.addView(gapW(a, 8));
        android.widget.Button loading = LuxKit.luxButton(a, s, "در حال ارسال", LuxKit.PRIMARY, null);
        LuxKit.setLoading(loading, true);
        b1.addView(loading, wrapM());
        b1.addView(gapW(a, 8));
        android.widget.Button disabled = LuxKit.luxButton(a, s, "غیرفعال", LuxKit.PRIMARY, null);
        disabled.setEnabled(false);
        disabled.setAlpha(DerakhshanTokens.DISABLED_OPACITY);
        b1.addView(disabled, wrapM());
        b1.addView(gapW(a, 8));
        android.widget.Button focused = LuxKit.luxButton(a, s, "فوکوس", LuxKit.PRIMARY, null);
        b1.addView(focused, wrapM());
        focused.requestFocus();
        panel.addView(b1, wrapM());
        panel.addView(gap(a, 8));
        LinearLayout b2 = horizontal(a);
        b2.addView(LuxKit.luxButton(a, s, "طلایی", LuxKit.GOLD, null), wrapM());
        b2.addView(gapW(a, 8));
        b2.addView(LuxKit.luxButton(a, s, "خط طلایی", LuxKit.GHOST_GOLD, null), wrapM());
        b2.addView(gapW(a, 8));
        b2.addView(LuxKit.luxButton(a, s, "آرام", LuxKit.QUIET, null), wrapM());
        panel.addView(b2, wrapM());
        panel.addView(gap(a, 12));

        // C7 segmented control
        final String[] segOptions = {"همه", "فعال", "بدون منتسب"};
        final TextView segValue = text(a, "انتخاب: همه", 10.5f, s.inkSecondary, false);
        panel.addView(LuxKit.segmented(a, s, segOptions, 0,
                index -> segValue.setText("انتخاب: " + segOptions[index])), match());
        panel.addView(gap(a, 4));
        panel.addView(segValue, match());
        panel.addView(gap(a, 12));

        // C8 quick tile hero + two tiles
        panel.addView(LuxKit.quickTile(a, s, R.drawable.mi_star_shine,
                DerakhshanTokens.EMERALD_500, "صدور سریع", "میان‌بر روز", true, null), match());
        panel.addView(gap(a, 8));
        LinearLayout tiles = horizontal(a);
        tiles.addView(LuxKit.quickTile(a, s, R.drawable.mi_group, DerakhshanTokens.GOLD_500,
                "مشتری", "", false, null), tileLp(1f));
        tiles.addView(gapW(a, 8));
        tiles.addView(LuxKit.quickTile(a, s, R.drawable.mi_inventory_2,
                DerakhshanTokens.EMERALD_500, "انبار", "", false, null), tileLp(1f));
        panel.addView(tiles, match());
        panel.addView(gap(a, 12));

        // C9 gauge (count-up inside the ring)
        panel.addView(LuxKit.gauge(a, s, 78d, 100d, "تحقق هدف ماه"), match());
        panel.addView(gap(a, 12));

        // C10 balance card
        panel.addView(LuxKit.balanceCard(a, s, "دریافت امروز", "۳۲۰٬۰۰۰٬۰۰",
                "پرداخت امروز", "۱۸۰٬۰۰۰٬۰۰", "ثبت دریافت", null, "ثبت پرداخت", null), match());
        panel.addView(gap(a, 12));

        // C11 empty state (single line + action)
        panel.addView(LuxKit.emptyState(a, s, R.drawable.mi_widgets,
                DerakhshanTokens.INFO, "موردی برای نمایش نیست",
                "با تغییر فیلترها نتیجهٔ بیشتری ببینید.", "پاک‌سازی فیلترها", null), match());
        panel.addView(gap(a, 12));

        // C12 LuxChart with an outlier day (broken axis + peak badge + MA + tooltip on touch)
        LuxChartView chart = new LuxChartView(a, s);
        chart.setData(new double[]{494d, 520d, 480d, 610d, 540d, 4295d, 500d, 530d, 470d, 590d},
                new String[]{"۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۱۰"});
        LinearLayout chartFrame = vertical(a);
        chartFrame.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        chartFrame.setBackground(rounded(s.stone[0], s.hairline, 12));
        chartFrame.addView(text(a, "روند ۱۰ روز · لمس برای جزئیات", 11f, s.inkSecondary, true), match());
        chartFrame.addView(gap(a, 6));
        chartFrame.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(190)));
        panel.addView(chartFrame, match());
        panel.addView(gap(a, 12));

        // C13 alert strip
        panel.addView(LuxKit.alertStrip(a, s, "بیش از ۳۰۰ قلم با هشدار موجودی",
                "بررسی پیشنهادی روزانه", "نمایش", null), match());
        panel.addView(gap(a, 10));

        // C6 command dock
        panel.addView(LuxKit.commandDock(a, s, new int[]{R.drawable.mi_home, R.drawable.mi_group,
                        R.drawable.mi_receipt_long, R.drawable.mi_settings},
                new String[]{"داشبورد", "مشتریان", "اسناد", "تنظیم"}, 0, null),
                match());

        LinearLayout.LayoutParams params = match();
        params.bottomMargin = Theme.dp(14);
        parent.addView(panel, params);
    }

    private static View gapW(MainActivity a, int dp) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(dp), 1));
        return v;
    }

    private static LinearLayout.LayoutParams tileLp(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    private static LinearLayout.LayoutParams wrapM() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static void addPanel(MainActivity a, LinearLayout parent, Palette palette) {
        LinearLayout panel = vertical(a);
        panel.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        panel.setBackground(rounded(palette.surface, palette.border, 14));

        TextView heading = text(a, palette.dark ? "حالت تیره" : "حالت روشن",
                14f, palette.text, true);
        panel.addView(heading, match());
        TextView note = text(a, "پیش‌نمایش ظاهری · بدون دادهٔ مالی نمونه",
                10.5f, palette.muted, false);
        panel.addView(note, match());
        panel.addView(gap(a, 10));
        panel.addView(headerPreview(a, palette), match());
        panel.addView(gap(a, 10));
        panel.addView(dockPreview(a, palette), match());
        panel.addView(gap(a, 10));
        panel.addView(componentPreview(a, palette), match());

        LinearLayout.LayoutParams params = match();
        params.bottomMargin = Theme.dp(10);
        parent.addView(panel, params);
    }

    private static View headerPreview(MainActivity a, Palette p) {
        LinearLayout card = vertical(a);
        card.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        card.setBackground(rounded(p.surface2, p.border, 12));
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout brand = horizontal(a);
        FrameLayout mark = new FrameLayout(a);
        mark.setBackground(rounded(p.accent, 0, 10));
        TextView glyph = text(a, "م", 15f, p.onAccent, true);
        glyph.setGravity(Gravity.CENTER);
        mark.addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        brand.addView(mark, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        brand.addView(space(a, 8));
        LinearLayout copy = vertical(a);
        copy.addView(text(a, "میلانو مدیریت", 14f, p.text, true), match());
        copy.addView(text(a, "پیش‌نمایش سربرگ", 10f, p.muted, false), match());
        brand.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(brand, match());
        card.addView(gap(a, 8));
        card.addView(statusPreview(a, p), match());
        return card;
    }

    private static View dockPreview(MainActivity a, Palette p) {
        LinearLayout dock = horizontal(a);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));
        dock.setBackground(rounded(p.surface2, p.border, 12));
        String[] labels = {"خانه", "کالاها", "مشتریان", "دستیار", "اتاق فرمان"};
        int[] icons = {R.drawable.mi_home, R.drawable.mi_inventory_2,
                R.drawable.mi_group, R.drawable.mi_star_shine, R.drawable.mi_widgets};
        for (int i = 0; i < labels.length; i++) {
            boolean selected = i == 0;
            LinearLayout tab = vertical(a);
            tab.setGravity(Gravity.CENTER);
            tab.setMinimumHeight(Theme.dp(52));
            ImageView icon = icon(a, icons[i], selected ? p.accent : p.muted, 18);
            tab.addView(icon, new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
            TextView label = text(a, labels[i], 9.5f,
                    selected ? p.accent : p.muted, selected);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            tab.addView(label, match());
            View marker = new View(a);
            marker.setBackgroundColor(selected ? p.accent : 0x00000000);
            LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(
                    Theme.dp(18), Theme.dp(2));
            markerParams.topMargin = Theme.dp(2);
            tab.addView(marker, markerParams);
            dock.addView(tab, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        dock.setContentDescription("پیش‌نمایش ناوبری پنج‌مقصدی");
        dock.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return dock;
    }

    private static View componentPreview(MainActivity a, Palette p) {
        LinearLayout group = vertical(a);
        group.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        group.setBackground(rounded(p.surface2, p.border, 12));
        group.addView(heroPreview(a, p), match());
        group.addView(gap(a, 6));
        group.addView(sectionHeader(a, p), match());
        group.addView(gap(a, 6));
        group.addView(text(a, "کارت‌های مالی بدون دادهٔ نمونه", 10f, p.muted, false), match());
        LinearLayout metrics = horizontal(a);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.addView(metric(a, p, "فروش", "—"), new LinearLayout.LayoutParams(0, -2, 1f));
        metrics.addView(metric(a, p, "اسناد", "—"), new LinearLayout.LayoutParams(0, -2, 1f));
        group.addView(metrics, match());
        group.addView(gap(a, 6));
        group.addView(text(a, "رنگ‌های معنایی", 10f, p.muted, false), match());
        LinearLayout semantic = horizontal(a);
        semantic.addView(delta(a, "نمونهٔ مثبت", DesignTokens.POSITIVE, p),
                new LinearLayout.LayoutParams(0, Theme.dp(34), 1f));
        semantic.addView(space(a, 6));
        semantic.addView(delta(a, "نمونهٔ هشدار", DesignTokens.NEGATIVE, p),
                new LinearLayout.LayoutParams(0, Theme.dp(34), 1f));
        group.addView(semantic, match());
        group.addView(gap(a, 6));
        group.addView(text(a, "بازهٔ زمانی", 10f, p.muted, false), match());
        group.addView(segmented(a, p), match());
        group.addView(gap(a, 6));
        group.addView(emptyRow(a, p), match());
        group.addView(gap(a, 6));
        group.addView(statusPreview(a, p), match());
        group.addView(gap(a, 6));
        group.addView(skeletonPreview(a, p), match());
        group.addView(gap(a, 6));
        View rule = new View(a);
        rule.setBackgroundColor(p.border);
        group.addView(rule, new LinearLayout.LayoutParams(-1, Theme.dp(1)));
        return group;
    }

    /** Preview the same softly tinted page hero used across the app's sections. */
    private static View heroPreview(MainActivity a, Palette p) {
        LinearLayout hero = horizontal(a);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(Theme.dp(10), Theme.dp(9), Theme.dp(10), Theme.dp(9));
        hero.setBackground(rounded(DesignTokens.heroSurface(p.accent, p.dark),
                DesignTokens.heroBorder(p.accent, p.dark), 18));
        hero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        FrameLayout mark = new FrameLayout(a);
        mark.setBackground(rounded(p.accent, 0, 10));
        ImageView brand = icon(a, R.drawable.mi_diamond, p.onAccent, 20);
        mark.addView(brand, new FrameLayout.LayoutParams(
                Theme.dp(20), Theme.dp(20), Gravity.CENTER));
        hero.addView(mark, new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38)));
        hero.addView(space(a, 8));

        LinearLayout copy = vertical(a);
        copy.addView(text(a, "عنوان صفحه", 12.5f, p.text, true), match());
        copy.addView(text(a, "سرصفحهٔ مشترک، آرام و هماهنگ", 9.5f, p.muted, false), match());
        hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return hero;
    }

    private static View sectionHeader(MainActivity a, Palette p) {
        LinearLayout header = vertical(a);
        LinearLayout row = horizontal(a);
        row.addView(icon(a, R.drawable.mi_bar_chart, p.accent, 18),
                new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
        row.addView(space(a, 7));
        row.addView(text(a, "شاخص‌های مالی", 13f, p.text, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(row, match());
        View rule = new View(a);
        rule.setBackgroundColor(p.border);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, Theme.dp(1));
        ruleParams.topMargin = Theme.dp(5);
        header.addView(rule, ruleParams);
        return header;
    }

    private static View metric(MainActivity a, Palette p, String label, String value) {
        LinearLayout tile = vertical(a);
        tile.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
        TextView title = text(a, label, 10f, p.muted, false);
        tile.addView(title, match());
        tile.addView(text(a, value, 16f, p.text, true), match());
        tile.addView(text(a, "داده‌ای در پیش‌نمایش وجود ندارد", 9.5f, p.muted, false), match());
        return tile;
    }

    private static View delta(MainActivity a, String label, int tone, Palette p) {
        TextView text = text(a, label, 10.5f,
                DesignTokens.textSafeColor(tone, p.surface2, p.dark), true);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private static View segmented(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));
        row.setBackground(rounded(p.surface, p.border, 10));
        String[] labels = {"روز", "هفته", "ماه"};
        for (int i = 0; i < labels.length; i++) {
            boolean selected = i == 1;
            TextView segment = text(a, labels[i], 10.5f,
                    selected ? p.onAccent : p.text, selected);
            segment.setGravity(Gravity.CENTER);
            segment.setPadding(Theme.dp(4), Theme.dp(7), Theme.dp(4), Theme.dp(7));
            if (selected) segment.setBackground(rounded(p.accent, 0, 8));
            row.addView(segment, new LinearLayout.LayoutParams(0, Theme.dp(40), 1f));
        }
        return row;
    }

    private static View emptyRow(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Theme.dp(40));
        row.addView(icon(a, R.drawable.mi_inbox, p.muted, 18),
                new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
        row.addView(space(a, 8));
        row.addView(text(a, "در نبود داده، این بخش حالت خالی نشان می‌دهد",
                10.5f, p.muted, false), new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static View statusPreview(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Theme.dp(42));
        row.addView(icon(a, R.drawable.mi_sync, p.accent, 18),
                new LinearLayout.LayoutParams(Theme.dp(20), Theme.dp(20)));
        row.addView(space(a, 7));
        LinearLayout copy = vertical(a);
        copy.addView(text(a, "پیش‌نمایش وضعیت اتصال", 10.5f, p.text, true), match());
        copy.addView(text(a, "این متن وضعیت واقعی سرویس نیست", 9.5f, p.muted, false), match());
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private static View skeletonPreview(MainActivity a, Palette p) {
        LinearLayout row = horizontal(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Theme.dp(38));
        row.addView(skeletonBar(a, p, 0.42f), new LinearLayout.LayoutParams(0, Theme.dp(8), 0.42f));
        return row;
    }

    private static View skeletonBar(MainActivity a, Palette p, float fraction) {
        View bar = new View(a);
        bar.setBackground(rounded(p.surface, p.border, 6));
        bar.setMinimumWidth(Theme.dp(Math.round(200 * fraction)));
        return bar;
    }

    private static ImageView icon(MainActivity a, int drawable, int tint, int sizeDp) {
        ImageView image = new ImageView(a);
        image.setImageResource(drawable);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setColorFilter(tint);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private static TextView text(MainActivity a, String value, float sp, int color, boolean bold) {
        TextView text = new TextView(a);
        text.setText(value == null ? "" : value);
        float scale = sp < 12f ? Theme.fontScale() : Theme.fontScale() * Theme.typeScale();
        text.setTextSize(Math.max(sp < 9f ? 9f : sp, 10f) * scale);
        text.setTextColor(color);
        text.setTypeface(Theme.face(bold));
        text.setFontFeatureSettings("tnum");
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setLineSpacing(Theme.dp(1), 1f);
        return text;
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

    private static LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }

    private static GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(Theme.dp(radiusDp));
        if (stroke != 0) drawable.setStroke(Theme.dp(1), stroke);
        return drawable;
    }

    private static final class Palette {
        final boolean dark;
        final int background;
        final int surface;
        final int surface2;
        final int text;
        final int muted;
        final int border;
        final int accent;
        final int onAccent;

        Palette(boolean dark) {
            this.dark = dark;
            background = dark ? DesignTokens.APP_DARK_BG : DesignTokens.APP_LIGHT_BG;
            surface = dark ? DesignTokens.APP_DARK_SURFACE : DesignTokens.APP_LIGHT_SURFACE;
            surface2 = dark ? DesignTokens.APP_DARK_SURFACE_2 : DesignTokens.APP_LIGHT_SURFACE_2;
            text = dark ? DesignTokens.APP_DARK_TEXT : DesignTokens.APP_LIGHT_TEXT;
            muted = dark ? DesignTokens.APP_DARK_MUTED : DesignTokens.APP_LIGHT_MUTED;
            border = dark ? DesignTokens.APP_DARK_BORDER : DesignTokens.APP_LIGHT_BORDER;
            accent = dark ? DesignTokens.APP_DARK_ACCENT : DesignTokens.APP_LIGHT_ACCENT;
            onAccent = DesignTokens.accentTextColor(accent);
        }
    }
}
