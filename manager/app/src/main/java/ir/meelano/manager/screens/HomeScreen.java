package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.FollowUps;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executive dashboard: greeting, alerts, day KPIs, trends, debtors, visitors, shortcuts. */
public class HomeScreen extends Screen {
    public HomeScreen(ir.meelano.manager.MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "home"; }

    @Override
    public String title() { return "خانه"; }

    @Override
    public String glyph() { return "⌂"; }

    @Override
    public int accent() { return Theme.GOLD; }

    private static final class Data {
        String snapshotDay = "";
        Row salesDay = new Row();
        Row buyDay = new Row();
        Row inDay = new Row();
        Row outDay = new Row();
        Row custSum = new Row();
        List<Row> debtors = new ArrayList<>();
        List<Row> overdue = new ArrayList<>();
        List<Row> salesDaily = new ArrayList<>();
        List<Row> inDaily = new ArrayList<>();
        List<Row> topItems = new ArrayList<>();
        List<Row> visitors = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
        /** Sales invoices split by confirmation state (تأییدشده / تأییدنشده). */
        List<Row> saleState = new ArrayList<>();
        /** Purchase invoices split the same way. */
        List<Row> buyState = new ArrayList<>();
        /** Return documents (اسناد برگشتی) of the last 30 days, grouped by type. */
        List<Row> returns = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.gap(6));
        content.addView(a.kit.loading("در حال دریافت داشبورد مدیریتی…"), a.kit.lp(-1, -2));

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.salesDay = soft(d.notes, "فروش روز", () -> Repo.one(c, Queries.homeSalesDay(m)));
            d.buyDay = soft(d.notes, "خرید روز", () -> Repo.one(c, Queries.homeBuyDay(m)));
            d.inDay = soft(d.notes, "دریافت روز", () -> Repo.one(c, MoneyQueries.darDay(m, 0)));
            d.outDay = soft(d.notes, "پرداخت روز", () -> Repo.one(c, MoneyQueries.darDay(m, 1)));
            d.custSum = soft(d.notes, "خلاصه مشتریان", () -> Repo.one(c, MasterQueries.customersSummary(m)));
            d.debtors = soft(d.notes, "بدهکاران", () -> Repo.exec(c, Queries.topDebtors(m, 5)));
            d.overdue = soft(d.notes, "معوق‌ها", () -> Repo.exec(c, Queries.overdueInvoices(m, 5)));
            final String to = Jalali.todayStr();
            d.snapshotDay = to;
            // 31 calendar days let the executive overview compare today against
            // yesterday plus rolling 7- and 30-day baselines; the chart itself stays 14 days.
            final String from = Jalali.addDays(to, -30);
            final String inFrom = Jalali.addDays(to, -13);
            d.salesDaily = soft(d.notes, "روند فروش", () -> Repo.exec(c, Queries.factorDaily(m, true, from, to)));
            d.inDaily = soft(d.notes, "روند دریافت", () -> Repo.exec(c, MoneyQueries.darDaily(m, 0, inFrom, to)));
            Filter todayFilter = new Filter();
            todayFilter.from = to;
            todayFilter.to = to;
            todayFilter.top = 5;
            d.topItems = soft(d.notes, "پرفروش‌ترین کالاهای امروز",
                    () -> Repo.exec(c, Queries.factorTopProducts(m, true, todayFilter, 5)));
            Filter vf = new Filter();
            vf.preset = Filter.P_LAST30;
            vf.applyPreset();
            d.visitors = soft(d.notes, "ویزیتورها", () -> Repo.exec(c, MasterQueries.visitorsPerf(m, vf)));
            d.dueIn = soft(d.notes, "سررسید دریافتی", () -> Repo.exec(c, MoneyQueries.chequeDue(m, true, 3)));
            d.dueOut = soft(d.notes, "سررسید پرداختی", () -> Repo.exec(c, MoneyQueries.chequeDue(m, false, 3)));
            String stFrom = Jalali.addDays(to, -29);
            d.saleState = soft(d.notes, "وضعیت فروش", () -> Repo.exec(c, Queries.factorStatusSummary(m, true, stFrom, to)));
            d.buyState = soft(d.notes, "وضعیت خرید", () -> Repo.exec(c, Queries.factorStatusSummary(m, false, stFrom, to)));
            Filter rf = new Filter();
            rf.from = stFrom;
            rf.to = to;
            d.returns = soft(d.notes, "برگشتی", () -> Repo.exec(c, MasterQueries.returnsByKind(m, rf)));
            if (d.salesDay == null && d.inDay == null && d.custSum == null && d.buyDay == null && d.outDay == null)
                throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                saveCache(d);
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                String[] ts = {""};
                Data cached = loadCached(ts);
                if (cached != null) {
                    build(content, cached);
                    LinearLayout bc = a.kit.card(Theme.WARNING);
                    bc.addView(a.kit.text("📴 حالت آفلاین — آخرین داده ذخیره‌شده", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
                    if (!ts[0].isEmpty())
                        bc.addView(a.kit.kv("آخرین به‌روزرسانی", Money.fa(ts[0]), Theme.WARNING), a.kit.lp(-1, -2));
                    bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
                    bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
                    content.addView(bc, 0);
                    content.addView(a.kit.gap(10), 1);
                    return;
                }
                content.removeAllViews();
                content.addView(greetingHero(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(10));
                        content.addView(a.kit.gap(8));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    // ---------------- offline cache (manager cues, top items, sales history and timestamp) ----------------
    private void saveCache(Data d) {
        try {
            Calendar c = Calendar.getInstance();
            String hm = String.format(java.util.Locale.US, "%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            StringBuilder s = new StringBuilder("ts=")
                    .append(esc(a.kit.todayLine() + " ساعت " + hm))
                    .append("|sd=").append(esc(d.snapshotDay))
                    .append("|sales=").append(rowStr(d.salesDay))
                    .append("|buy=").append(rowStr(d.buyDay))
                    .append("|in=").append(rowStr(d.inDay))
                    .append("|out=").append(rowStr(d.outDay))
                    .append("|cust=").append(rowStr(d.custSum));
            if (d.topItems != null) {
                for (int i = 0; i < Math.min(5, d.topItems.size()); i++)
                    s.append("|top").append(i).append('=').append(rowStr(d.topItems.get(i)));
            }
            if (d.salesDaily != null) {
                for (int i = 0; i < Math.min(31, d.salesDaily.size()); i++)
                    s.append("|trend").append(i).append('=').append(rowStr(d.salesDaily.get(i)));
            }
            a.settings.saveHomeCache(s.toString());
            pokeWidget();
        } catch (Exception ignored) { Diag.ignored("HomeScreen:185", ignored); }
    }

    /** Ask the home-screen widget to re-read the fresh cache. */
    private void pokeWidget() {
        try {
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(a);
            android.content.ComponentName cn = new android.content.ComponentName(a,
                    ir.meelano.manager.widget.CashWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                android.content.Intent in = new android.content.Intent(a,
                        ir.meelano.manager.widget.CashWidget.class);
                in.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                in.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                a.sendBroadcast(in);
            }
        } catch (Exception ignored) { Diag.ignored("HomeScreen:202", ignored); }
    }

    private Data loadCached(String[] tsOut) {
        try {
            String s = a.settings.homeCache();
            if (s == null || s.isEmpty()) return null;
            Data d = new Data();
            boolean any = false;
            for (String part : s.split("\\|")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                String k = part.substring(0, eq);
                String v = part.substring(eq + 1);
                if ("ts".equals(k)) tsOut[0] = unesc(v);
                else if ("sd".equals(k)) d.snapshotDay = unesc(v);
                else if ("sales".equals(k)) { d.salesDay = rowFrom(v); any = true; }
                else if ("buy".equals(k)) { d.buyDay = rowFrom(v); any = true; }
                else if ("in".equals(k)) { d.inDay = rowFrom(v); any = true; }
                else if ("out".equals(k)) { d.outDay = rowFrom(v); any = true; }
                else if ("cust".equals(k)) { d.custSum = rowFrom(v); any = true; }
                else if (k.matches("top[0-4]")) { d.topItems.add(rowFrom(v)); any = true; }
                else if (k.matches("trend(?:[0-9]|[12][0-9]|30)")) { d.salesDaily.add(rowFrom(v)); any = true; }
            }
            return any ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String rowStr(Row r) {
        if (r == null) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (b.length() > 0) b.append(';');
            b.append(e.getKey()).append('=').append(esc(String.valueOf(e.getValue())));
        }
        return b.toString();
    }

    private static Row rowFrom(String s) {
        Row r = new Row();
        if (s == null || s.isEmpty()) return r;
        for (String p : s.split(";")) {
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            r.put(p.substring(0, eq), unesc(p.substring(eq + 1)));
        }
        return r;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D").replace("|", "%7C");
    }

    private static String unesc(String s) {
        return s.replace("%7C", "|").replace("%3D", "=").replace("%3B", ";").replace("%25", "%");
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private String snapshotDay(Data d) {
        String day = d == null ? "" : Jalali.disp(d.snapshotDay);
        return day.isEmpty() ? Jalali.todayStr() : day;
    }

    private String snapshotLabel(Data d) {
        String day = d == null ? "" : Jalali.disp(d.snapshotDay);
        if (day.isEmpty()) return "آخرین داده";
        return day.equals(Jalali.todayStr()) ? "امروز" : "روز " + Jalali.shortLabel(day);
    }

    private View greetingHero() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 5 ? "شب بخیر" : (hour < 12 ? "صبح بخیر"
                : (hour < 15 ? "روز بخیر" : (hour < 19 ? "عصر بخیر" : "شب بخیر")));
        String role = AtiranAuth.sessionRole(a);
        if (role == null || role.isEmpty()) role = RoleStore.current(a);
        String roleName = RoleStore.faName(role);

        LinearLayout hero = a.kit.v();
        hero.setBackground(Theme.executiveHero());
        hero.setPadding(Theme.dp(18), Theme.dp(17), Theme.dp(18), Theme.dp(14));
        Theme.lift(hero, Theme.cardElevation() + Theme.dp(1));

        LinearLayout top = a.kit.h();
        FrameLayout crest = new FrameLayout(a);
        crest.setBackground(Theme.bottomNavIconSelected());
        crest.setElevation(Theme.dp(3));
        ImageView mark = a.kit.icon(R.drawable.mi_admin_panel_settings, Theme.selectedNavInk(), 30);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(Theme.dp(30), Theme.dp(30), Gravity.CENTER);
        crest.addView(mark, mp);
        top.addView(crest, new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58)));
        top.addView(a.kit.space(12));

        LinearLayout copy = a.kit.v();
        TextView overline = a.kit.text("MEELANO  •  اتاق فرمان", 10.5f, Theme.GOLD_SOFT, true);
        overline.setSingleLine(true);
        copy.addView(overline, a.kit.lp(-1, -2));
        TextView headline = a.kit.text(greeting + "، " + roleName, 21.5f, Theme.TEXT, true);
        headline.setMaxLines(1);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hp = a.kit.lp(-1, -2);
        hp.setMargins(0, Theme.dp(2), 0, 0);
        copy.addView(headline, hp);
        TextView description = a.kit.text("تصویر روشن از فروش، مالی و عملیات", 11f, Theme.MUTED, false);
        description.setSingleLine(true);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(description, a.kit.lp(-1, -2));
        top.addView(copy, a.kit.wlp(1f));
        hero.addView(top, a.kit.lp(-1, -2));
        hero.addView(a.kit.gap(13));

        View divider = new View(a);
        divider.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, Theme.alpha(Theme.GOLD, Theme.isLight() ? 120 : 110), 0x00000000}));
        hero.addView(divider, a.kit.lp(-1, Theme.dp(1)));
        hero.addView(a.kit.gap(9));
        LinearLayout footer = a.kit.h();
        footer.addView(a.kit.icon(R.drawable.mi_calendar_month, Theme.GOLD_SOFT, 16),
                new LinearLayout.LayoutParams(Theme.dp(16), Theme.dp(16)));
        footer.addView(a.kit.space(6));
        TextView date = a.kit.text(a.kit.todayLine(), 11.5f, Theme.TEXT, true);
        date.setSingleLine(true);
        footer.addView(date, a.kit.wlp(1f));
        TextView edition = a.kit.text(RoleStore.editionFa(role), 10.5f, Theme.GOLD_SOFT, true);
        edition.setSingleLine(true);
        edition.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
        edition.setBackground(Theme.ghostButton(Theme.GOLD));
        footer.addView(edition, a.kit.lp(-2, -2));
        hero.addView(footer, a.kit.lp(-1, -2));
        return hero;
    }

    /** A compact luxury launchpad to the five high-frequency management destinations. */
    /** Quick-access strip. Lives in the Command Room (اتاق فرمان) so the home screen stays calm. */
    void addExecutiveDock(LinearLayout content) {
        String[][] suggested = {
                {"sales", "فاکتورها", "فروش و پیگیری"},
                {"cash", "خزانه", "نقدینگی و گردش"},
                {"closeout", "بستن روز", "تطبیق مدیریتی"},
                {"anbar", "انبار", "موجودی و عملیات"},
                {"reports", "گزارش‌ها", "تحلیل تصمیم‌ها"},
        };
        List<String[]> available = new ArrayList<>();
        for (String[] item : suggested)
            if (a.screen(item[0]) != null && RoleStore.allowed(a, item[0])) available.add(item);
        if (available.isEmpty()) return;

        content.addView(a.kit.sectionHead("دسترسی فوری", "همه بخش‌ها ›", v -> a.nav("more")),
                a.kit.lp(-1, -2));
        HorizontalScrollView strip = new HorizontalScrollView(a);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        strip.setPadding(Theme.dp(1), Theme.dp(1), Theme.dp(1), Theme.dp(4));
        LinearLayout row = a.kit.h();
        for (String[] item : available) {
            Screen destination = a.screen(item[0]);
            int accent = destination.accent();
            LinearLayout tile = a.kit.h();
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(Theme.dp(10), Theme.dp(9), Theme.dp(10), Theme.dp(9));
            tile.setMinimumHeight(Theme.dp(68));
            tile.setBackground(Theme.cardAccent(accent));

            FrameLayout iconBadge = new FrameLayout(a);
            iconBadge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 34 : 48)));
            int iconRes = MeelanoIcons.iconFor(destination.glyph());
            if (iconRes != 0) {
                ImageView image = a.kit.icon(iconRes, accent, 19);
                iconBadge.addView(image, new FrameLayout.LayoutParams(Theme.dp(22), Theme.dp(22), Gravity.CENTER));
            } else {
                TextView fallback = a.kit.text(destination.glyph(), 19f, accent, true);
                fallback.setGravity(Gravity.CENTER);
                iconBadge.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            }
            tile.addView(iconBadge, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
            tile.addView(a.kit.space(8));

            LinearLayout labels = a.kit.v();
            TextView title = a.kit.text(item[1], 12.5f, Theme.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(title, a.kit.lp(-1, -2));
            TextView sub = a.kit.text(item[2], 10.5f, Theme.MUTED, false);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(sub, a.kit.lp(-1, -2));
            tile.addView(labels, a.kit.wlp(1f));
            tile.setContentDescription(item[1] + "؛ " + item[2] + "؛ باز کردن");
            Theme.pressable(tile);
            tile.setOnClickListener(v -> a.nav(item[0]));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Theme.dp(142), Theme.dp(68));
            tp.setMarginEnd(Theme.dp(8));
            row.addView(tile, tp);
        }
        strip.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        content.addView(strip, a.kit.lp(-1, -2));
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.gap(8));

        // Render each dashboard section into its own container so the lower-priority
        // analysis panels can collapse without changing the user's saved order.
        List<LinearLayout> blocks = new ArrayList<>();
        List<Boolean> essential = new ArrayList<>();
        int detailCount = 0;
        for (String key : orderedKeys()) {
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key);
            if (block.getChildCount() == 0) continue;
            boolean keepVisible = isEssentialHomeSection(key);
            blocks.add(block);
            essential.add(keepVisible);
            if (!keepVisible) detailCount++;
        }

        boolean expanded = a.settings.homeDetailsExpanded();
        boolean toggleAdded = false;
        for (int i = 0; i < blocks.size(); i++) {
            if (!essential.get(i) && !toggleAdded && detailCount > 0) {
                content.addView(homeDetailsToggle(blocks, essential, detailCount, expanded), a.kit.lp(-1, -2));
                toggleAdded = true;
            }
            LinearLayout block = blocks.get(i);
            if (!essential.get(i) && !expanded) block.setVisibility(View.GONE);
            content.addView(block, a.kit.lp(-1, -2));
        }
        renderNotes(content, d.notes);
    }

    private void renderHomeSection(LinearLayout content, Data d, String key) {
        if ("kpis".equals(key)) secKpis(content, d);
        else if ("alerts".equals(key)) secAlerts(content, d);
        else if ("trend".equals(key)) secTrend(content, d);
        else if ("donut".equals(key)) secDonut(content, d);
        else if ("today_top".equals(key)) secTopItems(content, d);
        else if ("debtors".equals(key)) secDebtors(content, d);
        else if ("visitors".equals(key)) secVisitors(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("invstatus".equals(key)) secInvStatus(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
        else if ("shortcuts".equals(key)) secShortcuts(content, d);
    }

    private boolean isEssentialHomeSection(String key) {
        return "kpis".equals(key) || "alerts".equals(key) || "due".equals(key)
                || "invstatus".equals(key) || "returns".equals(key) || "today_top".equals(key);
    }

    private View homeDetailsToggle(final List<LinearLayout> blocks, final List<Boolean> essential,
                                  final int detailCount, boolean expandedInitially) {
        final boolean[] expanded = {expandedInitially};
        final LinearLayout card = a.kit.card(Theme.INFO);
        card.setPadding(Theme.dp(14), Theme.dp(11), Theme.dp(14), Theme.dp(11));
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView icon = a.kit.text("▦", 21f, Theme.INFO, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackground(Theme.avatar(Theme.alpha(Theme.INFO, 50)));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        row.addView(a.kit.space(10));

        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text("جزئیات تکمیلی داشبورد", 13.5f, Theme.TEXT, true);
        TextView subtitle = a.kit.text("", 10.5f, Theme.MUTED, false);
        subtitle.setMaxLines(2);
        copy.addView(title, a.kit.lp(-1, -2));
        copy.addView(subtitle, a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        row.addView(a.kit.space(8));

        android.widget.ImageView action = new android.widget.ImageView(a);
        action.setColorFilter(Theme.INFO);
        action.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        row.addView(action, new LinearLayout.LayoutParams(Theme.dp(28), Theme.dp(36)));
        card.addView(row, a.kit.lp(-1, -2));

        final Runnable refresh = () -> {
            subtitle.setText(expanded[0]
                    ? "نمایش داده می‌شود؛ برای جمع‌کردن لمس کنید"
                    : Money.fa(String.valueOf(detailCount)) + " بخش تحلیلی و میان‌بر • برای نمایش لمس کنید");
            action.setImageResource(expanded[0] ? R.drawable.mi_remove : R.drawable.mi_add);
            card.setContentDescription(expanded[0] ? "جمع‌کردن جزئیات داشبورد" : "نمایش جزئیات داشبورد");
        };
        refresh.run();
        Theme.pressable(card);
        card.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            a.settings.setHomeDetailsExpanded(expanded[0]);
            for (int i = 0; i < blocks.size(); i++) {
                if (!essential.get(i)) blocks.get(i).setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            }
            refresh.run();
        });
        return card;
    }

    /** Dashboard order from settings; unknown keys dropped, missing sections appended. */
    private List<String> orderedKeys() {
        List<String> out = new ArrayList<>();
        String[] known = {"kpis", "alerts", "due", "invstatus", "returns", "today_top", "trend", "debtors", "visitors", "donut", "eod", "shortcuts"};
        for (String k : a.settings.homeOrder().split(",")) {
            String t = k == null ? "" : k.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                for (String k2 : known) if (k2.equals(t)) { out.add(t); break; }
            }
        }
        for (String k2 : known) if (!out.contains(k2)) out.add(k2);
        return out;
    }

    private void secKpis(LinearLayout content, Data d) {
        // ---- day KPIs (tap a tile → that day's detail list) ----
        // soft() deliberately returns null when one optional query fails. Keep the
        // remaining KPIs visible, but make the missing metric explicit and inert.
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(dayKpi("فروش", "sales", d.salesDay, "docs", "فاکتور", Theme.GOLD));
        kpis.add(dayKpi("خرید", "buy", d.buyDay, "docs", "فاکتور", Theme.INFO));
        kpis.add(dayKpi("دریافت", "dar_in", d.inDay, "count", "قبض", Theme.SUCCESS));
        kpis.add(dayKpi("پرداخت", "dar_out", d.outDay, "count", "قبض", Theme.WARNING));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String compareDay = snapshotDay(d);
            String compareLabel = snapshotLabel(d);
            double todaySales = salesOnDay(d.salesDaily, compareDay);
            double yesterday = salesOnDay(d.salesDaily, Jalali.addDays(compareDay, -1));
            double avg7 = averageSalesBefore(d.salesDaily, compareDay, 7);
            double avg30 = averageSalesBefore(d.salesDaily, compareDay, 30);
            LinearLayout compare = a.kit.card(Theme.GOLD);
            compare.addView(a.kit.sectionHead("فروش " + compareLabel + " در مقایسه", "فروش ›", v -> a.nav("sales")),
                    a.kit.lp(-1, -2));
            String delta = avg7 > 0 ? Money.pct((todaySales - avg7) * 100.0 / avg7) : "—";
            int deltaColor = avg7 <= 0 ? Theme.MUTED : (todaySales >= avg7 ? Theme.SUCCESS : Theme.WARNING);
            List<Kit.Kpi> cmp = new ArrayList<>();
            cmp.add(new Kit.Kpi(compareLabel, Money.compactRial(todaySales), "تغییر ۷ روزه " + delta, Theme.GOLD));
            cmp.add(new Kit.Kpi("دیروز", Money.compactRial(yesterday), "", Theme.TEXT));
            cmp.add(new Kit.Kpi("میانگین ۷ روز قبل", Money.compactRial(avg7), "", Theme.INFO));
            cmp.add(new Kit.Kpi("میانگین ۳۰ روز قبل", Money.compactRial(avg30), "", Theme.VIOLET));
            compare.addView(a.kit.kpiGrid(cmp, 2), a.kit.lp(-1, -2));
            if (todaySales <= 0)
                compare.addView(a.kit.hint("برای " + compareLabel + " هنوز فروشی ثبت نشده است. اعداد بالا از روزهای قبل محاسبه شده‌اند."),
                        a.kit.lp(-1, -2));
            a.kit.addCard(content, compare);
        }
    }

    Kit.Kpi dayKpi(String title, String target, Row row, String countKey,
                   String countLabel, int accent) {
        String day = dayOf(row);
        String label = day.isEmpty() ? title : title + " " + day;
        if (row == null) {
            // No row for this day yet: a neutral dash, never an error-looking placeholder.
            return new Kit.Kpi(label, "—", "هنوز ثبت نشده", accent);
        }
        return new Kit.Kpi(label, Money.compactRial(row.d("total")),
                Money.fa(String.valueOf(row.l(countKey))) + " " + countLabel, accent,
                () -> gotoDay(target, row));
    }

    private void secAlerts(LinearLayout content, Data d) {
        // ---- alerts ----
        LinearLayout alerts = a.kit.card(Theme.DANGER);
        alerts.addView(a.kit.text("هشدار امروز", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        int n = 0;
        n += alert(alerts, d, n);
        List<FollowUps.Due> dues = FollowUps.dueToday(a);
        if (!dues.isEmpty()) {
            final FollowUps.Due fd = dues.get(0);
            String who = dues.size() == 1 ? fd.note.name : Money.fa(String.valueOf(dues.size())) + " یادداشت";
            alerts.addView(a.kit.alertRow("پیگیری مشتری", who + " • سررسید یادداشت",
                    Theme.VIOLET, v -> gotoCustomer(fd.code)), padTop(n));
            n++;
        }
        if (n == 0) alerts.addView(a.kit.text("فعلاً هشدار مهمی وجود ندارد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, alerts);
    }

    /**
     * Confirmation state of the last 30 days' invoices. An invoice that never
     * reached Status=1 is still provisional: it sits in the ledger without ever
     * having been finalised, and nobody is told. Surfacing the count with a tap
     * through to the full list is what makes it actionable.
     */
    private void secInvStatus(LinearLayout content, Data d) {
        LinearLayout c = a.kit.card(Theme.WARNING);
        c.addView(a.kit.sectionHead("وضعیت فاکتورهای ۳۰ روز اخیر", "گزارش ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        addState(c, "فروش", d.saleState);
        addState(c, "خرید", d.buyState);
        if ((d.saleState == null || d.saleState.isEmpty()) && (d.buyState == null || d.buyState.isEmpty()))
            c.addView(a.kit.text("داده‌ای برای این بازه یافت نشد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    /** One line per state of one side (sales or purchases): how many, and for how much. */
    private void addState(LinearLayout c, String side, List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;
        long pendingN = 0;
        double pendingSum = 0;
        for (Row r : rows) {
            if (r.i("state") == 1) continue; // 1 = confirmed, the rest are provisional
            pendingN += r.l("docs");
            pendingSum += r.d("total");
        }
        int col = pendingN > 0 ? Theme.WARNING : Theme.SUCCESS;
        String txt = side + ": " + (pendingN > 0
                ? Money.fa(String.valueOf(pendingN)) + " فاکتور تأییدنشده (" + Money.compactMoney(pendingSum) + ")"
                : "همه تأیید شده‌اند");
        c.addView(a.kit.kv(txt, pendingN > 0 ? "نیاز به بررسی" : "✓", col), a.kit.lp(-1, -2));
    }

    /**
     * Return documents of the last 30 days. dbo.back_sanad is the ledger of goods
     * moving back — out of the warehouse, back from a customer, and so on — so a
     * sudden rise here is a quality or logistics problem long before it shows up
     * in the profit figures.
     */
    private void secReturns(LinearLayout content, Data d) {
        LinearLayout c = a.kit.card(Theme.DANGER);
        c.addView(a.kit.sectionHead("برگشتی‌های ۳۰ روز اخیر", "گزارش ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        long n = 0;
        double sum = 0;
        if (d.returns != null) {
            for (Row r : d.returns) {
                n += r.l("docs");
                sum += r.d("total");
            }
        }
        if (n == 0) {
            c.addView(a.kit.kv("هیچ سند برگشتی در این بازه ثبت نشده", "✓", Theme.SUCCESS), a.kit.lp(-1, -2));
        } else {
            c.addView(a.kit.kv(Money.fa(String.valueOf(n)) + " سند برگشتی",
                    Money.compactMoney(sum), Theme.DANGER), a.kit.lp(-1, -2));
            int shown = 0;
            for (Row r : d.returns) {
                if (shown++ >= 3) break;
                String label = r.s("label");
                if (label == null || label.trim().isEmpty()) continue;
                c.addView(a.kit.kv(label,
                        Money.fa(String.valueOf(r.l("docs"))) + " سند • " + Money.compactMoney(r.d("total")),
                        Theme.TEXT), a.kit.lp(-1, -2));
            }
        }
        a.kit.addCard(content, c);
    }

    /** Top five by the snapshot day's invoiced sales, following the dashboard benchmark. */
    private void secTopItems(LinearLayout content, Data d) {
        String period = snapshotLabel(d);
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.sectionHead("پرفروش‌ترین کالاهای " + period, "گزارش کامل ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        if (d.topItems == null || d.topItems.isEmpty()) {
            c.addView(a.kit.text("هنوز کالایی برای " + period + " ثبت نشده است.", 11.5f, Theme.MUTED, false),
                    a.kit.lp(-1, -2));
        } else {
            int show = Math.min(5, d.topItems.size());
            for (int i = 0; i < show; i++) {
                Row item = d.topItems.get(i);
                LinearLayout row = a.kit.h();
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                TextView rank = a.kit.text(Money.fa(String.valueOf(i + 1)), 11f, Theme.GOLD, true);
                rank.setGravity(android.view.Gravity.CENTER);
                rank.setBackground(Theme.avatar(Theme.alpha(Theme.GOLD, 44)));
                row.addView(rank, new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32)));
                row.addView(a.kit.space(8));

                LinearLayout copy = a.kit.v();
                TextView name = a.kit.text(item.s("label"), 12f, Theme.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(name, a.kit.lp(-1, -2));
                String detail = Money.qty(item.d("qty")) + " واحد • "
                        + Money.fa(String.valueOf(item.l("docs"))) + " ردیف فروش";
                copy.addView(a.kit.text(detail, 10f, Theme.MUTED, false), a.kit.lp(-1, -2));
                row.addView(copy, a.kit.wlp(1f));
                row.addView(a.kit.space(8));

                TextView total = a.kit.text(Money.compactRial(item.d("total")), 11.5f, Theme.GOLD, true);
                total.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                total.setMaxLines(1);
                total.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(total, a.kit.lp(-2, -2));
                c.addView(row, a.kit.lp(-1, -2));
                if (i + 1 < show) c.addView(a.kit.gap(8));
            }
            c.addView(a.kit.hint("رتبه‌بندی بر پایهٔ مبلغ فروش " + period), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, c);
    }

    private void secTrend(LinearLayout content, Data d) {
        // ---- 14-day sales trend ----
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String endDay = snapshotDay(d);
            String period = snapshotLabel(d);
            LinearLayout c = a.kit.card(Theme.GOLD);
            c.addView(a.kit.sectionHead("روند ۱۴ روزه فروش تا " + period, "فروش ›", v -> a.nav("sales")), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = filled(d.salesDaily, endDay);
            final TextView cap = a.kit.text(pts.isEmpty() ? "" : lastCap(pts), 11f, Theme.MUTED, false);
            ch.setData(pts, Theme.GOLD, Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.rial(p.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(190)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secDonut(LinearLayout content, Data d) {
        // ---- receipts mix donut ----
        if (d.inDay != null && d.inDay.d("total") > 0) {
            LinearLayout c = a.kit.card(Theme.SUCCESS);
            c.addView(a.kit.sectionHead("ترکیب دریافت " + dayOf(d.inDay), "دریافت‌ها ›", v -> a.nav("dar_in")), a.kit.lp(-1, -2));
            Charts.Donut dn = new Charts.Donut(a);
            List<Charts.Point> pts = new ArrayList<>();
            pts.add(new Charts.Point("نقد", d.inDay.d("cash"), Theme.SUCCESS));
            pts.add(new Charts.Point("کارت", d.inDay.d("pos"), Theme.INFO));
            pts.add(new Charts.Point("حواله", d.inDay.d("havaleh"), Theme.VIOLET));
            pts.add(new Charts.Point("چک", d.inDay.d("cheque"), Theme.GOLD));
            dn.setData(pts, "جمع دریافت", Money.compactRial(d.inDay.d("total")));
            c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
            a.kit.addCard(content, c);
        }
    }

    private void secDebtors(LinearLayout content, Data d) {
        // ---- debtors ----
        if (d.debtors != null && !d.debtors.isEmpty()) {
            content.addView(a.kit.sectionHead("بدهکاران اولویت‌دار", "مشتریان ›", v -> gotoDebtors()), a.kit.lp(-1, -2));
            for (Row r : d.debtors) {
                final String code = r.s("code");
                View row = a.kit.personRow(r.s("party"), "کد " + Money.fa(code), Money.rial(r.d("amount")),
                        "مانده بدهی", Theme.DANGER, v -> gotoCustomer(code));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(row, p);
            }
        }
    }

    private void secVisitors(LinearLayout content, Data d) {
        // ---- visitors ----
        if (d.visitors != null && !d.visitors.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.WARNING);
            c.addView(a.kit.sectionHead("عملکرد ۳۰ روزه ویزیتورها", "ویزیتورها ›", v -> a.nav("visitors")), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(6, d.visitors.size()); i++) {
                Row r = d.visitors.get(i);
                pts.add(new Charts.Point(r.s("name"), r.d("sales")));
            }
            hb.setData(pts, Charts.COMPACT);
            c.addView(hb, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secDue(LinearLayout content, Data d) {
        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = a.kit.card(Theme.INFO);
            c.addView(a.kit.sectionHead("سررسید ۳ روز آینده", "چک‌ها ›", v -> a.nav("cheques")), a.kit.lp(-1, -2));
            if (d.dueIn != null && !d.dueIn.isEmpty())
                c.addView(a.kit.kv("دریافتی (" + Money.fa(String.valueOf(d.dueIn.size())) + " فقره)", Money.rial(dueInSum), Theme.SUCCESS), a.kit.lp(-1, -2));
            if (d.dueOut != null && !d.dueOut.isEmpty())
                c.addView(a.kit.kv("پرداختی (" + Money.fa(String.valueOf(d.dueOut.size())) + " فقره)", Money.rial(dueOutSum), Theme.DANGER), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secEod(LinearLayout content) {
        // ---- end-of-day summary (saved nightly, one-tap share) ----
        String txt;
        String dt;
        try {
            txt = a.settings.eodText();
            dt = a.settings.eodDate();
        } catch (Exception e) {
            return;
        }
        if (txt == null || txt.isEmpty()) return;
        LinearLayout c = a.kit.card(Theme.VIOLET);
        String when = "";
        try {
            when = dt == null || dt.isEmpty() ? "" : Jalali.dispFa(dt);
        } catch (Exception ignored) { Diag.ignored("HomeScreen:800", ignored); }
        c.addView(a.kit.sectionHead("🌙 جمع‌بندی پایان روز" + (when.isEmpty() ? "" : " • " + when),
                null, null), a.kit.lp(-1, -2));
        c.addView(a.kit.text(txt, 13f, Theme.TEXT, false), a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));
        final String share = "🌙 جمع‌بندی پایان روز میلانو" + (when.isEmpty() ? "" : " (" + when + ")")
                + "\n" + txt;
        c.addView(a.kit.btnGhost("↗ اشتراک‌گذاری", Theme.VIOLET, v -> {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(android.content.Intent.EXTRA_TEXT, share);
                a.startActivity(android.content.Intent.createChooser(i, "اشتراک جمع‌بندی روز"));
            } catch (Exception e) {
                a.kit.toast("اشتراک ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private void secShortcuts(LinearLayout content, Data d) {
        // ---- secondary management centers (daily destinations live in the top launchpad) ----
        content.addView(a.kit.sectionHead("مراکز تخصصی", null, null), a.kit.lp(-1, -2));
        String[][] links = {
                {"customers", "پرونده، مانده و گردش حساب"},
                {"cheques", "دریافتی، پرداختی و سررسیدها"},
                {"profit", "حاشیه سود و هزینه‌ها"},
                {"visitors", "عملکرد و وصول ویزیتورها"},
                {"users", "کاربران و فعالیت‌های سیستم"},
        };
        for (int i = 0; i < links.length; i++) {
            final String id = links[i][0];
            Screen s = a.screen(id);
            if (s == null || !RoleStore.allowed(a, id)) continue;
            View r = a.kit.navRow(s.glyph(), s.title(), links[i][1], s.accent(), v -> a.nav(id));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }
    }

    private int alert(LinearLayout box, Data d, int n) {
        // overdue top-1
        if (d.overdue != null && !d.overdue.isEmpty()) {
            Row o = d.overdue.get(0);
            long dd = o.l("days");
            // Without dif_date_alan the server sends −1: derive the delay from the due date.
            if (dd < 0) dd = Jalali.diffDays(o.s("dueDate"), Jalali.todayStr());
            String days = dd >= 0 ? " • تأخیر " + Money.fa(String.valueOf(dd)) + " روز" : "";
            box.addView(a.kit.alertRow("فاکتور معوق", o.s("party") + " • " + Money.rial(o.d("amount")) + days,
                    Theme.DANGER, v -> gotoUnsettled()), padTop(n));
            n++;
        }
        // top debtor
        if (d.debtors != null && !d.debtors.isEmpty()) {
            Row r = d.debtors.get(0);
            box.addView(a.kit.alertRow("بدهکار اولویت‌دار", r.s("party") + " • مانده " + Money.rial(r.d("amount")),
                    Theme.WARNING, v -> gotoCustomer(r.s("code"))), padTop(n));
            n++;
        }
        // liquidity
        if (d.salesDay != null && d.buyDay != null && d.salesDay.d("total") > 0 && d.buyDay.d("total") > d.salesDay.d("total") * 1.15) {
            box.addView(a.kit.alertRow("فشار نقدینگی", "خرید روز از فروش جلو زده؛ پرداخت‌ها را کنترل کنید.",
                    Theme.WARNING, v -> a.nav("buy")), padTop(n));
            n++;
        }
        // due cheques
        int dueN = (d.dueIn == null ? 0 : d.dueIn.size()) + (d.dueOut == null ? 0 : d.dueOut.size());
        if (dueN > 0) {
            box.addView(a.kit.alertRow("سررسید چک", Money.fa(String.valueOf(dueN)) + " فقره چک در ۳ روز آینده سررسید می‌شود.",
                    Theme.INFO, v -> a.nav("cheques")), padTop(n));
            n++;
        }
        return n;
    }

    private LinearLayout.LayoutParams padTop(int n) {
        LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(8), 0, 0);
        return p;
    }

    private String dayOf(Row day) {
        if (day == null) return "";
        String d = Jalali.disp(day.s("d"));
        if (d.isEmpty()) return "";
        String today = Jalali.todayStr();
        if (d.equals(today)) return "امروز";
        if (d.equals(Jalali.addDays(today, -1))) return "دیروز";
        return Jalali.shortLabel(d);
    }

    /** Open a section narrowed to the KPI day (custom 1-day range). */
    private void gotoDay(String target, Row day) {
        String d = day == null ? "" : Jalali.disp(day.s("d"));
        Screen s = a.screen(target);
        if (s != null && s.filter() != null && !d.isEmpty()) {
            Filter nf = s.filter().copy();
            nf.preset = Filter.P_CUSTOM;
            nf.from = d;
            nf.to = d;
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav(target);
    }

    private double sum(List<Row> rows) {
        double s = 0;
        if (rows != null) for (Row r : rows) s += r.d("amount");
        return s;
    }

    private double salesOnDay(List<Row> rows, String day) {
        if (rows == null || day == null || day.isEmpty()) return 0;
        String target = Jalali.disp(day);
        double total = 0;
        for (Row r : rows)
            if (target.equals(Jalali.disp(r.s("day")))) total += r.d("total");
        return total;
    }

    /** Average across calendar days before today, counting days without sales as zero. */
    private double averageSalesBefore(List<Row> rows, String today, int days) {
        if (rows == null || days <= 0) return 0;
        Map<String, Double> byDay = new HashMap<>();
        for (Row r : rows) {
            String d = Jalali.disp(r.s("day"));
            if (!d.isEmpty()) byDay.merge(d, r.d("total"), Double::sum);
        }
        int first = Jalali.parse(Jalali.addDays(today, -days));
        if (first < 0) return 0;
        double total = 0;
        for (int i = 0; i < days; i++) {
            Double amount = byDay.get(Jalali.format(first + i));
            if (amount != null) total += amount;
        }
        return total / days;
    }

    /** Fill missing days with 0 for a smooth 14-day chart ending on the saved snapshot day. */
    private List<Charts.Point> filled(List<Row> rows, String endDay) {
        Map<String, Double> map = new HashMap<>();
        if (rows != null) for (Row r : rows) {
            String k = Jalali.disp(r.s("day"));
            if (!k.isEmpty()) map.merge(k, r.d("total"), Double::sum);
        }
        List<Charts.Point> out = new ArrayList<>();
        String to = Jalali.disp(endDay);
        if (to.isEmpty()) to = Jalali.todayStr();
        String from = Jalali.addDays(to, -13);
        int d0 = Jalali.parse(from);
        for (int i = 0; i < 14 && d0 >= 0; i++) {
            String day = Jalali.format(d0 + i);
            Double v = map.get(day);
            out.add(new Charts.Point(Jalali.shortLabel(day), v == null ? 0 : v));
        }
        return out;
    }

    private String lastCap(List<Charts.Point> pts) {
        Charts.Point p = pts.get(pts.size() - 1);
        return Money.fa(p.label) + " • " + Money.rial(p.value);
    }

    private void gotoDebtors() {
        Screen s = a.screen("customers");
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.status = "debt";
            nf.sort = "debt";
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav("customers");
    }

    private void gotoUnsettled() {
        Screen s = a.screen("sales");
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.status = "unsettled";
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav("sales");
    }

    private void gotoCustomer(String code) {
        Screen s = a.screen("customers");
        if (s instanceof CustomersScreen) ((CustomersScreen) s).openDossier(code);
        a.nav("customers");
    }
}
