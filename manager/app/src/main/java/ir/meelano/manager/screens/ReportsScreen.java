package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import ir.meelano.manager.core.CacheStore;
import ir.meelano.manager.core.AtiranSpecialReports;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.VisitorScorecard;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.AtiranAuth;
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
    public String glyph() { return Theme.isLight() ? "▤" : "📊"; }

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
                || "visitor_scorecard".equals(sel.id) || "expiry_watch".equals(sel.id)
                || "staff_attendance".equals(sel.id) || sel.id.startsWith("sf_");
        if (!sel.needsRange && !search) return null;
        FilterSheet.Config c = new FilterSheet.Config();
        c.search = search;
        c.searchHint = "visitor_scorecard".equals(sel.id) ? "جستجوی نام ویزیتور…"
                : ("expiry_watch".equals(sel.id) ? "جستجوی نام یا کد کالا…"
                : ("staff_attendance".equals(sel.id) ? "جستجوی نام یا شناسه پرسنل…" : "جستجوی مشتری / کالا / فاکتور…"));
        c.visitors = "visitor_scorecard".equals(sel.id);
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
        if ("دریافتی‌ها و پرداخت".equals(section)) return Theme.SUCCESS;
        if ("چک‌ها".equals(section)) return Theme.INFO;
        if ("عملکرد صندوق".equals(section)) return Theme.GOLD;
        if ("خزانه‌داری".equals(section)) return Theme.GOLD;
        if ("کالا و انبار".equals(section)) return Theme.VIOLET;
        if ("مشتریان".equals(section)) return Theme.SUCCESS;
        if ("ویزیتورها".equals(section)) return Theme.WARNING;
        if ("سود و زیان".equals(section)) return Theme.GOLD;
        if ("بانک و صندوق".equals(section)) return Theme.INFO;
        if ("پرسنل و نظارت".equals(section)) return Theme.STEEL;
        if ("هوشمند".equals(section)) return Theme.VIOLET;
        if ("هزینه‌ها".equals(section)) return Theme.ROSE;
        if ("برگشتی و ابطالی".equals(section)) return Theme.DANGER;
        if ("تحلیل ویزیتور و مسیر".equals(section)) return Theme.WARNING;
        if ("سرفصل‌ها".equals(section)) return Theme.GOLD_SOFT;
        return Theme.MUTED;
    }

    private int sectionIcon(String section) {
        if ("ویژه مدیر".equals(section)) return R.drawable.mi_military_tech;
        if ("فروش".equals(section)) return R.drawable.mi_point_of_sale;
        if ("خرید".equals(section)) return R.drawable.mi_shopping_cart;
        if ("دریافتی‌ها و پرداخت".equals(section)) return R.drawable.mi_payments;
        if ("چک‌ها".equals(section)) return R.drawable.mi_cheque;
        if ("عملکرد صندوق".equals(section)) return R.drawable.mi_account_balance_wallet;
        if ("خزانه‌داری".equals(section)) return R.drawable.mi_account_balance;
        if ("کالا و انبار".equals(section)) return R.drawable.mi_inventory_2;
        if ("مشتریان".equals(section)) return R.drawable.mi_group;
        if ("ویزیتورها".equals(section)) return R.drawable.mi_directions_car;
        if ("سود و زیان".equals(section)) return R.drawable.mi_profit;
        if ("بانک و صندوق".equals(section)) return R.drawable.mi_account_balance;
        if ("پرسنل و نظارت".equals(section)) return R.drawable.mi_admin_panel_settings;
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
        if (!restored) {
            restored = true;
            restoreState();
        }
        if (pendingCategory != null) {
            selCat = pendingCategory;
            sel = null;
            pendingCategory = null;
        }
        String role = reportRole();
        if (sel != null && !ReportCatalog.availableToRole(sel, role)) sel = null;
        if (selCat != null && !hasVisibleSection(selCat, role)) selCat = null;
        if (sel == null) renderList(content);
        else renderReport(content, sel);
        saveState();
    }

    private static final String STATE_PREFS = "reports_state";
    private boolean restored = false;

    private String reportRole() {
        String role = AtiranAuth.sessionRole(a);
        if (role != null && !role.isEmpty()) return role;
        return RoleStore.enabled(a) ? RoleStore.current(a) : RoleStore.ADMIN;
    }

    private List<ReportCatalog.Spec> visibleReports() {
        List<ReportCatalog.Spec> out = new ArrayList<>();
        String role = reportRole();
        for (ReportCatalog.Spec spec : ReportCatalog.ALL)
            if (ReportCatalog.availableToRole(spec, role)) out.add(spec);
        return out;
    }

    private boolean hasVisibleSection(String section, String role) {
        for (ReportCatalog.Spec spec : ReportCatalog.ALL)
            if (section.equals(spec.section) && ReportCatalog.availableToRole(spec, role)) return true;
        if (ReportCatalog.TREASURY.equals(section)) {
            for (ReportCatalog.Spec spec : ReportCatalog.ALL)
                if (ReportCatalog.isTreasurySection(spec.section)
                        && ReportCatalog.availableToRole(spec, role)) return true;
        }
        return false;
    }

    /** Restores the last section, report, period and search so returning to Reports picks up where the user left off. */
    private void restoreState() {
        try {
            android.content.SharedPreferences p = a.getSharedPreferences(STATE_PREFS, android.content.Context.MODE_PRIVATE);
            String rep = p.getString("rep", "");
            String cat = p.getString("cat", "");
            if (!rep.isEmpty()) sel = ReportCatalog.byId(rep);
            if (sel == null && !cat.isEmpty()) selCat = cat;
            int preset = p.getInt("preset", -1);
            if (preset >= 0) filter.preset = preset;
            filter.from = p.getString("from", filter.from);
            filter.to = p.getString("to", filter.to);
            filter.search = p.getString("search", filter.search);
        } catch (Exception ignored) {
            Diag.ignored("ReportsScreen:restore", ignored);
        }
    }

    private void saveState() {
        try {
            a.getSharedPreferences(STATE_PREFS, android.content.Context.MODE_PRIVATE).edit()
                    .putString("rep", sel == null ? "" : sel.id)
                    .putString("cat", selCat == null ? "" : selCat)
                    .putInt("preset", filter.preset)
                    .putString("from", filter.from == null ? "" : filter.from)
                    .putString("to", filter.to == null ? "" : filter.to)
                    .putString("search", filter.search == null ? "" : filter.search)
                    .apply();
        } catch (Exception ignored) {
            Diag.ignored("ReportsScreen:save", ignored);
        }
    }

    private void renderList(final LinearLayout content) {
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(),
                Money.fa(String.valueOf(visibleReports().size())) + " گزارش در دسترس این نقش • جست‌وجوی سریع و دسته‌بندی‌شده",
                accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        if (RoleStore.allowed(a, "analytics"))
            content.addView(a.kit.btnGhost("📈  داشبورد تحلیلی فروش", Theme.VIOLET,
                    v -> a.nav("analytics")), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout tr = a.kit.h();
        tr.addView(a.kit.btnGhost("🧾  فاکتورهای فروش", Theme.SALES_HUE, v -> a.nav("sales")), a.kit.wlp(1f));
        if (RoleStore.allowed(a, "buy")) {
            tr.addView(a.kit.space(8));
            tr.addView(a.kit.btnGhost("🧺  فاکتورهای خرید", Theme.BUY_HUE, v -> a.nav("buy")), a.kit.wlp(1f));
        }
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
            for (ReportCatalog.Spec spec : visibleReports()) {
                if (ReportCatalog.isTreasurySection(spec.section)) {
                    // All treasury sub-sections live behind one «خزانه‌داری» card.
                    if (!secs.contains(ReportCatalog.TREASURY)) { secs.add(ReportCatalog.TREASURY); counts.add(0); }
                    int tx = secs.indexOf(ReportCatalog.TREASURY);
                    counts.set(tx, counts.get(tx) + 1);
                    continue;
                }
                int ix = secs.indexOf(spec.section);
                if (ix < 0) {
                    secs.add(spec.section);
                    counts.add(1);
                } else counts.set(ix, counts.get(ix) + 1);
            }
            sortSectionsByPriority(secs, counts);
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

        if (ReportCatalog.TREASURY.equals(selCat)) {
            // The treasury group: its sub-sections, each opening its own report list.
            content.addView(a.kit.btnGhost("‹ همه دسته‌ها", Theme.GOLD, v -> {
                selCat = null;
                render(contentRef);
            }), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(10));
            int treasuryTotal = 0;
            for (ReportCatalog.Spec spec : visibleReports())
                if (ReportCatalog.isTreasurySection(spec.section)) treasuryTotal++;
            content.addView(a.kit.hero("▤", ReportCatalog.TREASURY,
                    "بخش‌های مالی و بانکی • " + Money.fa(String.valueOf(treasuryTotal)) + " گزارش",
                    sectionAccent(selCat)), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(12));
            java.util.List<String> subs = new java.util.ArrayList<>();
            java.util.List<Integer> subCounts = new java.util.ArrayList<>();
            for (ReportCatalog.Spec spec : visibleReports()) {
                if (!ReportCatalog.isTreasurySection(spec.section)) continue;
                int ix = subs.indexOf(spec.section);
                if (ix < 0) { subs.add(spec.section); subCounts.add(1); }
                else subCounts.set(ix, subCounts.get(ix) + 1);
            }
            for (int i = 0; i < subs.size(); i++) {
                if (i > 0) content.addView(a.kit.gap(10));
                content.addView(catCard(subs.get(i), subCounts.get(i)), a.kit.lp(-1, -2));
            }
            return;
        }

        final boolean inTreasury = ReportCatalog.isTreasurySection(selCat);
        content.addView(a.kit.btnGhost(inTreasury ? "‹ " + ReportCatalog.TREASURY : "‹ همه دسته‌ها", Theme.GOLD, v -> {
            selCat = inTreasury ? ReportCatalog.TREASURY : null;
            render(contentRef);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hero("▤", selCat,
                "گزارش‌های این دسته • " + Money.fa(String.valueOf(countInSection(selCat))) + " مورد",
                sectionAccent(selCat)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        addReportRows(content, filteredReports("", selCat));
    }

    /** Stable manager order: controls, sales/customer growth and field team, then supply and finance. */
    private static void sortSectionsByPriority(List<String> sections, List<Integer> counts) {
        String[] priority = {"ویژه مدیر", "فروش", "مشتریان", "تحلیل ویزیتور و مسیر",
                "خرید", ReportCatalog.TREASURY, "چک‌ها", "کالا و انبار", "سرفصل‌ها",
                "سود و زیان", "هزینه‌ها", "پرسنل و نظارت", "برگشتی و ابطالی",
                "هوشمند", "گزارش‌های اختصاصی"};
        for (int i = 0; i < sections.size(); i++) {
            for (int j = i + 1; j < sections.size(); j++) {
                if (sectionPriority(sections.get(j), priority) < sectionPriority(sections.get(i), priority)) {
                    String section = sections.get(i);
                    sections.set(i, sections.get(j));
                    sections.set(j, section);
                    Integer count = counts.get(i);
                    counts.set(i, counts.get(j));
                    counts.set(j, count);
                }
            }
        }
    }

    private static int sectionPriority(String section, String[] priority) {
        for (int i = 0; i < priority.length; i++) if (priority[i].equals(section)) return i;
        return priority.length;
    }

    private int countInSection(String section) {
        int n = 0;
        for (ReportCatalog.Spec spec : visibleReports())
            if (section.equals(spec.section)) n++;
        return n;
    }

    private List<ReportCatalog.Spec> filteredReports(String query, String section) {
        List<ReportCatalog.Spec> out = new ArrayList<>();
        String q = normalize(query);
        for (ReportCatalog.Spec spec : visibleReports()) {
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
        int reportVector = reportIcon(spec);
        int reportArt = Theme.icon3d(reportVector);
        if (!Theme.isIcon3d(reportArt)) {
            int categoryArt = Theme.icon3d(sectionIcon(spec.section));
            if (Theme.isIcon3d(categoryArt)) reportArt = categoryArt;
        }
        icon.setImageResource(reportArt);
        if (Theme.isIcon3d(reportArt)) icon.clearColorFilter();
        else icon.setColorFilter(color);
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
            case "visitor_scorecard": return R.drawable.mi_badge;
            case "expiry_watch": return R.drawable.mi_hourglass_top;
            case "staff_attendance": return R.drawable.mi_schedule;
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
            int art = Theme.icon3d(sectionIcon(section));
            iv.setImageResource(art);
            if (Theme.isIcon3d(art)) iv.clearColorFilter();
            else iv.setColorFilter(sectionAccent(section));
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
        // Offline copy is keyed by the range and search, so a cached report never shows another period.
        final String cacheKey = cacheKey(spec.id, f);
        final Repo.Cb<List<Row>> cb = new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("rows", rows);
                CacheStore.saveData(a, cacheKey, cacheNow(), sm);
                buildReport(content, spec, rows);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                java.util.Map<String, Object> cm = CacheStore.loadData(a, cacheKey, lab);
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
        };
        // Offline mode: the report comes from the last nightly copy on the phone, never from the server.
        if (a.settings.offlineMode()) cb.fail(OFFLINE_MISS);
        else a.repo.run(c -> {
            Meta m = new Meta(c);
            return applyMaps(m, spec.id, Repo.exec(c, ReportCatalog.query(m, spec.id, f)), f);
        }, cb);
    }

    /** Shown when offline and no nightly copy of this report exists yet. */
    static final String OFFLINE_MISS = "حالت آفلاین: این گزارش در بکاپ شبانه روی گوشی نیست؛ یک‌بار با اتصال باز کنید";

    /** Report-specific shaping shared by the live screen and the nightly offline copy. */
    public static List<Row> applyMaps(Meta m, String id, List<Row> rows) {
        return applyMaps(m, id, rows, null);
    }

    public static List<Row> applyMaps(Meta m, String id, List<Row> rows, Filter filter) {
        if ("visitor_scorecard".equals(id)) rows = VisitorScorecard.score(rows, filter);
        if ("expiry_watch".equals(id)) rows = AtiranSpecialReports.mapExpiry(rows);
        if ("staff_attendance".equals(id)) rows = AtiranSpecialReports.mapAttendance(rows);
        if ("yoy_sales".equals(id)) rows = mapYoY(rows);
        if ("aging".equals(id)) mapAging(rows, m);
        if ("unsettled".equals(id)) mapUnsettled(rows);
        if ("fleeing_customers".equals(id)) mapFleeing(rows);
        if ("dying_stock".equals(id)) rows = mapDying(rows);
        if ("fresh_debtors".equals(id)) rows = mapFresh(rows);
        return rows;
    }

    /** Key of an offline report copy: id plus range and search, so periods never mix. */
    public static String cacheKey(String id, Filter f) {
        String visitor = "visitor_scorecard".equals(id) ? String.valueOf(f.visitor) : "";
        return "cx_rep_" + id + "_"
                + Integer.toHexString((f.from + "|" + f.to + "|" + f.search + "|" + f.status + "|" + visitor).hashCode());
    }

    /** Without dif_date_alan the server sends days=−1: derive the delay from the due date. */
    static void mapUnsettled(List<Row> rows) {
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
    static void mapFleeing(List<Row> rows) {
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
    static void mapAging(List<Row> rows, Meta m) {
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
    static List<Row> mapDying(List<Row> rows) {
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
    static List<Row> mapFresh(List<Row> rows) {
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
    static List<Row> mapYoY(List<Row> rows) {
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

    static String monthBucket(String month) {
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
                boolean acc = "سرفصل‌ها".equals(spec.section);
                String x = acc ? Charts.trim(chartLabel(spec, r.s(spec.chartX)), 8) : chartLabel(spec, r.s(spec.chartX));
                double yv = r.d(spec.chartY);
                if (acc) {
                    // Gold family for the chart of accounts; negative balances stay in the danger colour.
                    pts.add(new Charts.Point(x, yv, yv < 0 ? Theme.DANGER : Charts.goldTone(i)));
                } else if (spec.chart == ReportCatalog.C_DONUT)
                    pts.add(new Charts.Point(x, yv, Charts.palette(i)));
                else pts.add(new Charts.Point(x, yv));
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
