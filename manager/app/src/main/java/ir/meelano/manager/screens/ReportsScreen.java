package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import ir.meelano.manager.core.CacheStore;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Reports center: 52 management reports, search, category landing, charts, tables and export. */
public class ReportsScreen extends Screen {
    private final Filter filter = new Filter();
    private ReportCatalog.Spec sel;
    /** Set by the command room to open one report group directly. */
    public static String pendingCategory = null;
    private String selCat;
    /** Browse search is separate from the report's date/status filter. */
    private String browseQuery = "";
    private LinearLayout contentRef;

    public ReportsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "reports"; }

    @Override
    public String title() { return "گزارشات"; }

    @Override
    public String glyph() { return "▤"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        if (sel == null) return null;
        boolean search = "fleeing_customers".equals(sel.id) || "lost_basket".equals(sel.id)
                || "cheque_reliability".equals(sel.id) || "stock_forecast".equals(sel.id)
                || "dying_stock".equals(sel.id) || "fresh_debtors".equals(sel.id)
                || "order_suggest".equals(sel.id) || "invoice_profit".equals(sel.id)
                || sel.id.startsWith("sf_");
        if (!sel.needsRange && !search) return null;
        FilterSheet.Config c = new FilterSheet.Config();
        c.search = search;
        c.searchHint = "جستجوی مشتری / کالا / فاکتور…";
        c.range = sel.needsRange;
        return c;
    }

    @Override
    public boolean onBack() {
        if (sel == null && selCat == null) return false;
        if (sel != null) sel = null;
        else selCat = null;
        a.refreshChrome();
        if (contentRef != null) render(contentRef);
        return true;
    }

    private int sectionAccent(String section) {
        if ("ویژه مدیر".equals(section)) return Theme.GOLD;
        if ("فروش".equals(section)) return Theme.SALES_HUE;
        if ("خرید".equals(section)) return Theme.BUY_HUE;
        if ("دریافت و پرداخت".equals(section)) return Theme.SUCCESS;
        if ("چک‌ها".equals(section)) return Theme.INFO;
        if ("کالا و انبار".equals(section)) return Theme.VIOLET;
        if ("مشتریان".equals(section)) return Theme.SUCCESS;
        if ("ویزیتورها".equals(section)) return Theme.WARNING;
        if ("سود و زیان".equals(section)) return Theme.GOLD;
        if ("بانک و صندوق".equals(section)) return Theme.INFO;
        if ("کاربران و نظارت".equals(section)) return Theme.STEEL;
        if ("هوشمند".equals(section)) return Theme.TEAL;
        if ("هزینه‌ها".equals(section)) return Theme.ROSE;
        if ("برگشتی و ابطالی".equals(section)) return Theme.DANGER;
        if ("تحلیل ویزیتور و مسیر".equals(section)) return Theme.WARNING;
        if ("سرفصل‌ها".equals(section)) return Theme.TEAL;
        return Theme.MUTED;
    }

    private int sectionIcon(String section) {
        if ("ویژه مدیر".equals(section)) return R.drawable.mi_military_tech;
        if ("فروش".equals(section)) return R.drawable.mi_point_of_sale;
        if ("خرید".equals(section)) return R.drawable.mi_shopping_cart;
        if ("دریافت و پرداخت".equals(section)) return R.drawable.mi_payments;
        if ("چک‌ها".equals(section)) return R.drawable.mi_receipt_long;
        if ("کالا و انبار".equals(section)) return R.drawable.mi_inventory_2;
        if ("مشتریان".equals(section)) return R.drawable.mi_group;
        if ("ویزیتورها".equals(section)) return R.drawable.mi_directions_car;
        if ("سود و زیان".equals(section)) return R.drawable.mi_diamond;
        if ("بانک و صندوق".equals(section)) return R.drawable.mi_account_balance;
        if ("کاربران و نظارت".equals(section)) return R.drawable.mi_admin_panel_settings;
        if ("هوشمند".equals(section)) return R.drawable.mi_lightbulb;
        if ("هزینه‌ها".equals(section)) return R.drawable.mi_trending_down;
        if ("برگشتی و ابطالی".equals(section)) return R.drawable.mi_assignment_return;
        if ("تحلیل ویزیتور و مسیر".equals(section)) return R.drawable.mi_bar_chart;
        if ("سرفصل‌ها".equals(section)) return R.drawable.mi_account_balance;
        return R.drawable.mi_widgets;
    }

    @Override
    public void render(final LinearLayout content) {
        contentRef = content;
        if (pendingCategory != null) {
            selCat = pendingCategory;
            sel = null;
            pendingCategory = null;
        }
        if (sel == null) renderList(content);
        else renderReport(content, sel);
    }

    private void renderList(final LinearLayout content) {
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(),
                Money.fa(String.valueOf(ReportCatalog.ALL.length)) + " گزارش مدیریتی • جست‌وجوی سریع و دسته‌بندی‌شده",
                accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.btnGhost("📈  داشبورد تحلیلی فروش", Theme.VIOLET, v -> a.nav("analytics")), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout tr = a.kit.h();
        tr.addView(a.kit.btnGhost("🧾  فاکتورهای فروش", Theme.SALES_HUE, v -> a.nav("sales")), a.kit.wlp(1f));
        tr.addView(a.kit.space(8));
        tr.addView(a.kit.btnGhost("🧺  فاکتورهای خرید", Theme.BUY_HUE, v -> a.nav("buy")), a.kit.wlp(1f));
        content.addView(tr, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.searchBar("جست‌وجوی نام، کاربرد یا دستهٔ گزارش…", browseQuery, q -> {
            browseQuery = q == null ? "" : q.trim();
            selCat = null;
            render(contentRef);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (!normalize(browseQuery).isEmpty()) {
            List<ReportCatalog.Spec> results = filteredReports(browseQuery, null);
            content.addView(a.kit.sectionHead("نتایج جست‌وجو",
                    Money.fa(String.valueOf(results.size())) + " گزارش", null), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(6));
            if (results.isEmpty()) {
                content.addView(a.kit.empty("گزارشی با این عبارت پیدا نشد",
                        "نام گزارش، دسته یا کاربرد آن را کوتاه‌تر جست‌وجو کنید."), a.kit.lp(-1, -2));
                return;
            }
            addReportRows(content, results);
            return;
        }

        if (selCat == null) {
            java.util.List<String> secs = new java.util.ArrayList<>();
            java.util.List<Integer> counts = new java.util.ArrayList<>();
            for (ReportCatalog.Spec spec : ReportCatalog.ALL) {
                int ix = secs.indexOf(spec.section);
                if (ix < 0) {
                    secs.add(spec.section);
                    counts.add(1);
                } else counts.set(ix, counts.get(ix) + 1);
            }
            int cols = a.kit.isWide() ? 3 : 2;
            LinearLayout grid = a.kit.v();
            LinearLayout row = null;
            for (int i = 0; i < secs.size(); i++) {
                if (i % cols == 0) {
                    row = a.kit.h();
                    LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
                    if (i > 0) rp.setMargins(0, Theme.dp(10), 0, 0);
                    grid.addView(row, rp);
                } else {
                    row.addView(a.kit.space(10));
                }
                row.addView(catCard(secs.get(i), counts.get(i)), a.kit.wlp(1f));
            }
            if (secs.size() % cols != 0 && row != null)
                row.addView(a.kit.space(10), a.kit.wlp(1f));
            content.addView(grid, a.kit.lp(-1, -2));
            return;
        }

        content.addView(a.kit.btnGhost("‹ همه دسته‌ها", Theme.GOLD, v -> {
            selCat = null;
            render(contentRef);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hero("▤", selCat,
                "گزارش‌های این دسته • " + Money.fa(String.valueOf(countInSection(selCat))) + " مورد",
                sectionAccent(selCat)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        addReportRows(content, filteredReports("", selCat));
    }

    private int countInSection(String section) {
        int n = 0;
        for (ReportCatalog.Spec spec : ReportCatalog.ALL)
            if (section.equals(spec.section)) n++;
        return n;
    }

    private List<ReportCatalog.Spec> filteredReports(String query, String section) {
        List<ReportCatalog.Spec> out = new ArrayList<>();
        String q = normalize(query);
        for (ReportCatalog.Spec spec : ReportCatalog.ALL) {
            if (section != null && !section.equals(spec.section)) continue;
            if (!q.isEmpty() && !normalize(spec.title + " " + spec.desc + " " + spec.section + " " + spec.id).contains(q))
                continue;
            out.add(spec);
        }
        return out;
    }

    private void addReportRows(LinearLayout content, List<ReportCatalog.Spec> rows) {
        for (final ReportCatalog.Spec spec : rows) {
            View r = reportRow(spec);
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(9));
            content.addView(r, p);
        }
    }

    /** Search and category browse use the same Persian-aware normalization. */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "")
                .toLowerCase(java.util.Locale.ROOT).trim();
    }

    private View reportRow(final ReportCatalog.Spec spec) {
        final int color = sectionAccent(spec.section);
        LinearLayout c = a.kit.h();
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Theme.card());
        c.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        Theme.lift(c, Theme.cardElevation());

        ImageView icon = new ImageView(a);
        icon.setImageResource(reportIcon(spec));
        icon.setColorFilter(color);
        icon.setBackground(Theme.avatar(Theme.alpha(color, 48)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        c.addView(icon, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
        c.addView(a.kit.space(10));

        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text(spec.title, 13.5f, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, a.kit.lp(-1, -2));
        String meta = spec.section + " • " + (spec.needsRange ? "بازه‌دار" : "گزارش کلی");
        TextView detail = a.kit.text(meta + " • " + spec.desc, 10.5f, Theme.MUTED, false);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(detail, a.kit.lp(-1, -2));
        c.addView(copy, a.kit.wlp(1f));
        c.addView(a.kit.space(6));

        ImageView arrow = new ImageView(a);
        arrow.setImageResource(R.drawable.mi_chevron_left);
        arrow.setColorFilter(Theme.MUTED);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        c.addView(arrow, new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(40)));
        c.setContentDescription(spec.title + "؛ " + spec.section + "؛ بازه‌دار: "
                + (spec.needsRange ? "بله" : "خیر") + "؛ باز کردن گزارش");
        Theme.pressable(c);
        c.setOnClickListener(v -> {
            sel = spec;
            a.refreshChrome();
            render(contentRef);
        });
        return c;
    }

    /** One purpose-matched vector icon for each report entry. */
    private int reportIcon(ReportCatalog.Spec spec) {
        switch (spec.id) {
            case "today_top": return R.drawable.mi_point_of_sale;
            case "cheques_today": return R.drawable.mi_schedule;
            case "dying_stock": return R.drawable.mi_hourglass_top;
            case "fresh_debtors": return R.drawable.mi_person_search;
            case "fleeing_customers": return R.drawable.mi_directions_car;
            case "lost_basket": return R.drawable.mi_shopping_bag;
            case "cheque_reliability": return R.drawable.mi_verified_user;
            case "visitor_yield": return R.drawable.mi_badge;
            case "golden_hours": return R.drawable.mi_bolt;
            case "sleeping_capital": return R.drawable.mi_account_balance_wallet;
            case "stock_forecast": return R.drawable.mi_trending_down;
            case "order_suggest": return R.drawable.mi_add_shopping_cart;
            case "invoice_profit": return R.drawable.mi_request_quote;
            case "sales_daily": return R.drawable.mi_calendar_month;
            case "sales_top_products": return R.drawable.mi_star_fill;
            case "sales_by_customer": return R.drawable.mi_group;
            case "sales_monthly": return R.drawable.mi_event;
            case "yoy_sales": return R.drawable.mi_compare_arrows;
            case "sales_by_visitor": return R.drawable.mi_directions_car;
            case "sales_by_route": return R.drawable.mi_location_on;
            case "sales_by_group": return R.drawable.mi_category;
            case "unsettled": return R.drawable.mi_assignment_late;
            case "aging": return R.drawable.mi_history;
            case "buy_daily": return R.drawable.mi_shopping_cart;
            case "buy_top_products": return R.drawable.mi_shopping_cart_fill;
            case "buy_monthly": return R.drawable.mi_work_history;
            case "buy_by_party": return R.drawable.mi_storefront;
            case "in_daily": return R.drawable.mi_download;
            case "out_daily": return R.drawable.mi_upload;
            case "pos_by_bank": return R.drawable.mi_credit_card;
            case "cheque_due_in": return R.drawable.mi_schedule;
            case "cheque_due_out": return R.drawable.mi_timer;
            case "cheque_bounced_in": return R.drawable.mi_error;
            case "out_of_stock": return R.drawable.mi_block;
            case "low_stock": return R.drawable.mi_warning;
            case "stock_value": return R.drawable.mi_inventory_2;
            case "debtors": return R.drawable.mi_person_search;
            case "creditors": return R.drawable.mi_account_balance_wallet;
            case "new_customers": return R.drawable.mi_person;
            case "inactive_customers": return R.drawable.mi_visibility_off;
            case "visitor_perf": return R.drawable.mi_directions_car;
            case "users": return R.drawable.mi_admin_panel_settings;
            case "changes": return R.drawable.mi_history;
            case "profit_daily": return R.drawable.mi_trending_up;
            case "profit_by_product": return R.drawable.mi_sell;
            case "profit_costs": return R.drawable.mi_trending_down;
            case "sales_notconfirmed": return R.drawable.mi_pending;
            case "buy_notconfirmed": return R.drawable.mi_assignment_late;
            case "returns_by_kind": return R.drawable.mi_assignment_return;
            case "returns_list": return R.drawable.mi_receipt_long;
            case "bank_turnover": return R.drawable.mi_account_balance;
            case "cows": return R.drawable.mi_store;
            default: return sectionIcon(spec.section);
        }
    }

    private View catCard(final String section, int count) {
        LinearLayout c = a.kit.card(sectionAccent(section));
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setMinimumHeight(Theme.dp(122));
        ImageView iv = new ImageView(a);
        try {
            iv.setImageResource(sectionIcon(section));
            iv.setColorFilter(sectionAccent(section));
        } catch (Exception ignored) { Diag.ignored("ReportsScreen:349", ignored); }
        int sz = Theme.dp(44);
        c.addView(iv, new LinearLayout.LayoutParams(sz, sz));
        c.addView(a.kit.gap(6));
        android.widget.TextView t = a.kit.text(section, 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(t, a.kit.lp(-1, -2));
        android.widget.TextView u = a.kit.text(Money.fa(String.valueOf(count)) + " گزارش", 11.5f, Theme.MUTED, false);
        u.setGravity(Gravity.CENTER);
        u.setSingleLine(true);
        c.addView(u, a.kit.lp(-1, -2));
        c.setContentDescription(section + "؛ " + Money.fa(String.valueOf(count)) + " گزارش");
        c.addView(a.kit.gap(2));
        Theme.pressable(c);
        c.setOnClickListener(v -> {
            selCat = section;
            render(contentRef);
        });
        return c;
    }

    /** In-content filter bar under the report hero (v12: filters live inside each report). */
    private void filterBarInto(LinearLayout content, ReportCatalog.Spec spec) {
        if (!spec.needsRange || filter() == null) return;
        Filter f = filter();
        content.addView(a.kit.filterBar(f.describeFa(), !f.isDefault(), v -> a.openFilter()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    private void renderReport(final LinearLayout content, final ReportCatalog.Spec spec) {
        // Returns open on «امروز» (daily) instead of the generic 30-day window.
        if (("returns_by_kind".equals(spec.id) || "returns_list".equals(spec.id))
                && filter.preset == Filter.P_LAST30) {
            filter.defaultPreset = Filter.P_TODAY;
            filter.preset = Filter.P_TODAY;
            filter.applyPreset();
        }
        content.removeAllViews();
        content.addView(a.kit.btnGhost("‹ بازگشت به فهرست", Theme.GOLD, v -> onBack()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hero("▤", spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, sectionAccent(spec.section)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        filterBarInto(content, spec);
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال تهیه گزارش…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<Row> rows = Repo.exec(c, ReportCatalog.query(m, spec.id, f));
            if ("yoy_sales".equals(spec.id)) rows = mapYoY(rows);
            if ("aging".equals(spec.id)) mapAging(rows, m);
            if ("unsettled".equals(spec.id)) mapUnsettled(rows);
            if ("fleeing_customers".equals(spec.id)) mapFleeing(rows);
            if ("dying_stock".equals(spec.id)) rows = mapDying(rows);
            if ("fresh_debtors".equals(spec.id)) rows = mapFresh(rows);
            return rows;
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("rows", rows);
                CacheStore.saveData(a, "cx_rep_" + spec.id, cacheNow(), sm);
                buildReport(content, spec, rows);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                java.util.Map<String, Object> cm = CacheStore.loadData(a, "cx_rep_" + spec.id, lab);
                if (cm != null) {
                    buildReport(content, spec, CacheStore.rows(cm, "rows"));
                    offlineBanner(content, lab[0], faError);
                    return;
                }
                content.removeAllViews();
                content.addView(a.kit.btnGhost("‹ بازگشت به فهرست", Theme.GOLD, v -> onBack()), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(10));
                content.addView(a.kit.hero("▤", spec.title, spec.desc, sectionAccent(spec.section)), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
            filterBarInto(content, spec);
                content.addView(a.kit.gap(12));
                String msg = faError == null || faError.isEmpty() ? "گزارش قابل تهیه نیست" : faError;
                boolean schemaGap = faError != null && (faError.contains("پیدا نشد")
                        || faError.contains("تأیید نشده") || faError.contains("قابل تشخیص"));
                content.addView(schemaGap ? a.kit.unavailable(msg) : a.kit.error(msg, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    /** Without dif_date_alan the server sends days=−1: derive the delay from the due date. */
    private void mapUnsettled(List<Row> rows) {
        if (rows == null) return;
        String today = Jalali.todayStr();
        for (Row r : rows) {
            if (r.l("days") < 0) {
                int dd = Jalali.diffDays(r.s("dueDate"), today);
                r.put("days", dd < 0 ? 0 : dd);
            }
        }
    }

    /** Fleeing customers: the server sends daysAway=−1; derive it from the last-buy date. */
    private void mapFleeing(List<Row> rows) {
        if (rows == null) return;
        String today = Jalali.todayStr();
        for (Row r : rows) {
            if (r.l("daysAway") < 0) {
                int dd = Jalali.diffDays(r.s("lastBuy"), today);
                r.put("daysAway", dd < 0 ? 0 : dd);
            }
        }
    }

    /** Normalize aging rows (k-based or month-based) into bucket/amount/count. */
    private void mapAging(List<Row> rows, Meta m) {
        boolean useK = m.function("dif_date_alan");
        for (Row r : rows) {
            if (useK && r.has("k")) {
                int k = r.i("k");
                String b = k <= 0 ? "معوق (سررسید گذشته)" : k == 1 ? "۰ تا ۳۰ روز" : k == 2 ? "۳۱ تا ۶۰ روز"
                        : k == 3 ? "۶۱ تا ۹۰ روز" : "بیش از ۹۰ روز";
                r.put("bucket", b);
            } else if (r.has("month")) {
                r.put("bucket", monthBucket(r.s("month")));
            } else {
                r.put("bucket", "—");
            }
        }
    }

    /** Urgent only: items running out within 7 days (or already out), soonest first. */
    private List<Row> mapDying(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        if (rows == null) return out;
        for (Row r : rows) {
            double dl = r.d("daysLeft");
            if ((dl >= 0 && dl <= 7) || r.d("vah") <= 0) out.add(r);
        }
        java.util.Collections.sort(out, (x, y) -> Double.compare(x.d("daysLeft"), y.d("daysLeft")));
        return out;
    }

    /** Fresh debtors: bought within the last 30 days. */
    private List<Row> mapFresh(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        if (rows == null) return out;
        String cut = "";
        try {
            cut = Jalali.addDays(Jalali.todayStr(), -30);
        } catch (Exception ignored) { Diag.ignored("ReportsScreen:501", ignored); }
        for (Row r : rows) {
            String ls = Jalali.disp(r.s("lastSale"));
            if (!ls.isEmpty() && ls.compareTo(cut) >= 0) out.add(r);
        }
        return out;
    }

    /** Merge ~24 monthly rows into 12 month rows: this year vs last year + growth %. */
    private List<Row> mapYoY(List<Row> rows) {
        List<Row> out = new ArrayList<>();
        if (rows == null) return out;
        String y1 = "";
        for (Row r : rows) {
            String k = mon7(r.s("month"));
            if (k.length() == 7 && k.compareTo(y1) > 0) y1 = k.substring(0, 4);
        }
        if (y1.isEmpty()) return out;
        int y0;
        try {
            y0 = Integer.parseInt(y1) - 1;
        } catch (Exception e) {
            return out;
        }
        for (int m = 1; m <= 12; m++) {
            String mm = (m < 10 ? "0" : "") + m;
            double t = 0, l = 0;
            for (Row r : rows) {
                String k = mon7(r.s("month"));
                if ((y1 + "/" + mm).equals(k)) t += r.d("total");
                else if ((y0 + "/" + mm).equals(k)) l += r.d("total");
            }
            double g = l > 0.5 ? (t - l) * 100.0 / l : (t > 0.5 ? 100 : 0);
            Row r = new Row();
            String lab = Jalali.monthName(y1 + "/" + mm + "/01");
            r.put("mlab", lab.isEmpty() ? mm : lab);
            r.put("thisY", t);
            r.put("lastY", l);
            r.put("growth", Math.round(g * 10) / 10.0);
            out.add(r);
        }
        return out;
    }

    private static String mon7(String s) {
        s = s == null ? "" : s.trim();
        return s.length() >= 7 ? s.substring(0, 7) : "";
    }

    private String monthBucket(String month) {
        try {
            String t = Jalali.todayStr();
            int cy = Integer.parseInt(t.substring(0, 4));
            int cm = Integer.parseInt(t.substring(5, 7));
            int y = Integer.parseInt(month.substring(0, 4));
            int mm = Integer.parseInt(month.substring(5, 7));
            int diff = (cy * 12 + cm) - (y * 12 + mm);
            String rel = diff < 0 ? "آینده" : diff == 0 ? "ماه جاری" : diff == 1 ? "۱ ماه پیش" : Money.fa(String.valueOf(diff)) + " ماه پیش";
            return Money.fa(month) + " (" + rel + ")";
        } catch (Exception e) {
            return Money.fa(month);
        }
    }

    private void buildReport(LinearLayout content, final ReportCatalog.Spec spec, final List<Row> rows) {
        content.removeAllViews();
        content.addView(a.kit.btnGhost("‹ بازگشت به فهرست", Theme.GOLD, v -> onBack()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hero("▤", spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, sectionAccent(spec.section)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        filterBarInto(content, spec);
        content.addView(a.kit.gap(12));

        if (rows == null || rows.isEmpty()) {
            content.addView(a.kit.empty("ردیفی برای این گزارش یافت نشد", spec.needsRange ? "بازه را تغییر دهید" : null), a.kit.lp(-1, -2));
            return;
        }

        content.addView(a.exportBar(spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, spec.cols, rows), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (spec.chart != ReportCatalog.C_NONE && !spec.chartX.isEmpty() && !spec.chartY.isEmpty()) {
            LinearLayout c = a.kit.card(sectionAccent(spec.section));
            final List<Charts.Point> pts = new ArrayList<>();
            int cap = spec.chart == ReportCatalog.C_DONUT ? 8 : 31;
            for (int i = 0; i < Math.min(cap, rows.size()); i++) {
                Row r = rows.get(i);
                String x = chartLabel(spec, r.s(spec.chartX));
                if (spec.chart == ReportCatalog.C_DONUT)
                    pts.add(new Charts.Point(x, r.d(spec.chartY), Charts.palette(i)));
                else pts.add(new Charts.Point(x, r.d(spec.chartY)));
            }
            c.addView(a.kit.chartHead("نمودار", () -> {
                if (spec.chart == ReportCatalog.C_LINE) {
                    Charts.Area fh = new Charts.Area(a);
                    fh.setData(pts, sectionAccent(spec.section), Charts.COMPACT);
                    return fh;
                } else if (spec.chart == ReportCatalog.C_BARS) {
                    Charts.Bars fb = new Charts.Bars(a);
                    fb.setData(pts, Charts.COMPACT);
                    return fb;
                }
                Charts.Donut fd = new Charts.Donut(a);
                double fsum = 0;
                for (Row rr : rows) fsum += rr.d(spec.chartY);
                fd.setData(pts, "جمع", Money.compactRial(fsum));
                return fd;
            }), a.kit.lp(-1, -2));
            if (spec.chart == ReportCatalog.C_LINE) {
                Charts.Area ch = new Charts.Area(a);
                ch.setData(pts, sectionAccent(spec.section), Charts.COMPACT);
                c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(190)));
            } else if (spec.chart == ReportCatalog.C_BARS) {
                Charts.Bars ch = new Charts.Bars(a);
                ch.setData(pts, Charts.COMPACT);
                c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(220)));
            } else {
                Charts.Donut dn = new Charts.Donut(a);
                double sum = 0;
                for (Row r : rows) sum += r.d(spec.chartY);
                dn.setData(pts, "جمع", Money.compactRial(sum));
                c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
            }
            a.kit.addCard(content, c);
        }

        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.kv("تعداد ردیف", Money.fa(String.valueOf(rows.size())), Theme.TEXT), a.kit.lp(-1, -2));
        for (ReportCatalog.Col col : spec.cols) {
            if (col.type != ReportCatalog.T_MONEY) continue;
            double sum = 0;
            for (Row r : rows) sum += r.d(col.key);
            c.addView(a.kit.kv("جمع " + col.title, Money.rial(sum), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        }
        int show = Math.min(rows.size(), 300);
        final boolean callable = "fleeing_customers".equals(spec.id) || "cheque_reliability".equals(spec.id);
        c.addView(a.kit.dataTable(spec.cols, new ArrayList<>(rows.subList(0, show)),
                callable ? this::contactDialog : null), a.kit.lp(-1, -2));
        if (rows.size() > show)
            c.addView(a.kit.hint("+" + Money.fa(String.valueOf(rows.size() - show)) + " ردیف دیگر در PDF…"), a.kit.lp(-1, -2));
        LinearLayout.LayoutParams fp = a.kit.lp(-1, -2);
        fp.setMargins(0, Theme.dp(10), 0, 0);
        c.addView(a.exportBar(spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, spec.cols, rows), fp);
        a.kit.addCard(content, c);
    }

    /** Tap-to-call: phone + SMS actions for customer rows that carry a mobile number. */
    private void contactDialog(Row r) {
        String name = r.has("customer") ? r.s("customer") : r.s("name");
        String cell = ir.meelano.manager.core.Money.en(r.s("cell")).replaceAll("[^0-9+]", "");
        if (cell.isEmpty()) {
            a.kit.toast("شماره همراهی برای این مشتری ثبت نشده است");
            return;
        }
        android.widget.LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text(name.isEmpty() ? "مشتری" : name, 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.text(ir.meelano.manager.core.Money.fa(cell), 13f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(10));
        final android.app.AlertDialog[] box = new android.app.AlertDialog[1];
        android.widget.LinearLayout row = a.kit.h();
        row.addView(a.kit.btn("📞 تماس", v -> {
            box[0].dismiss();
            try {
                a.startActivity(new android.content.Intent(android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + cell)));
            } catch (Exception e) {
                a.kit.toast("تماس ممکن نشد");
            }
        }), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btnGhost("✉ پیامک", Theme.INFO, v -> {
            box[0].dismiss();
            try {
                a.startActivity(new android.content.Intent(android.content.Intent.ACTION_SENDTO,
                        android.net.Uri.parse("smsto:" + cell)));
            } catch (Exception e) {
                a.kit.toast("پیامک ممکن نشد");
            }
        }), a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("پیگیری مشتری", body, true);
        box[0].show();
    }

    private String chartLabel(ReportCatalog.Spec spec, String x) {
        if (x == null) return "";
        try {
            if ("day".equals(spec.chartX)) return Jalali.shortLabel(x);
            if ("month".equals(spec.chartX)) return Jalali.monthName(x + "/01") + " " + Money.fa(x.substring(0, 4));
        } catch (Exception ignored) { Diag.ignored("ReportsScreen:691", ignored); }
        String t = Money.fa(x);
        return t.length() > 16 ? t.substring(0, 16) + "…" : t;
    }
}
