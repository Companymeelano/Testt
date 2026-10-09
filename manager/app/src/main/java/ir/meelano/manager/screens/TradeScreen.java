package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.FisPrint;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sales (sailfact) and Purchases (buyfact) — one screen, two personalities. */
public class TradeScreen extends Screen {
    private final boolean sales;
    private final Filter filter = new Filter();
    private int tab;

    public TradeScreen(MainActivity a, boolean sales) {
        super(a);
        this.sales = sales;
        filter.top = 50;
    }

    @Override
    public String id() { return sales ? "sales" : "buy"; }

    @Override
    public String title() { return sales ? "فروش" : "خرید"; }

    @Override
    public String glyph() { return sales ? "🧾" : "🧺"; }

    @Override
    public int accent() { return sales ? Theme.GOLD : Theme.INFO; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = sales ? "فاکتور، مشتری…" : "فاکتور، طرف‌حساب…";
        c.range = true;
        if (sales) {
            c.visitors = true;
            c.routes = true;
        }
        c.custGroups = true;
        c.statusTitle = "وضعیت تسویه";
        c.status = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "همه"),
                new FilterSheet.Opt("settled", "تسویه‌شده"),
                new FilterSheet.Opt("unsettled", "تسویه‌نشده"),
        };
        c.sortTitle = "مرتب‌سازی فهرست";
        c.sort = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "جدیدترین"),
                new FilterSheet.Opt("total_desc", "مبلغ بیشتر"),
                new FilterSheet.Opt("remain_desc", "مانده بیشتر"),
        };
        return c;
    }

    private String[] tabs() {
        if (sales) return new String[]{"فهرست", "معوق", "ویزیتور", "مشتری", "مسیر", "گروه", "کالا", "پیش‌بینی"};
        return new String[]{"فهرست", "طرف‌حساب", "کالا"};
    }

    private static final class Data {
        int snapTab;
        List<Row> forecast = new ArrayList<>();
        Row summary = new Row();
        List<Row> daily = new ArrayList<>();
        List<Row> list = new ArrayList<>();
        List<Row> group = new ArrayList<>();
        List<Row> topProducts = new ArrayList<>();
        List<Row> overdue = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading(sales ? "در حال دریافت فروش…" : "در حال دریافت خرید…"), a.kit.lp(-1, -2));
        final int myTab = tab;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.snapTab = myTab;
            d.summary = soft(d.notes, "خلاصه", () -> Repo.one(c, Queries.factorSummary(m, sales, f)));
            final String from = f.hasRange() ? f.from : Jalali.addDays(Jalali.todayStr(), -29);
            final String to = f.hasRange() ? f.to : Jalali.todayStr();
            d.daily = soft(d.notes, "روند", () -> Repo.exec(c, Queries.factorDaily(m, sales, from, to)));
            if (myTab == 0) {
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, Queries.factorList(m, sales, f)));
            } else if (sales && myTab == 1) {
                d.overdue = soft(d.notes, "معوق", () -> Repo.exec(c, Queries.overdueInvoices(m, 100)));
            } else if ((!sales && myTab == 1) || (sales && myTab == 3)) {
                d.group = soft(d.notes, "طرف‌حساب", () -> Repo.exec(c, Queries.factorByCustomer(m, sales, f, 50)));
            } else if (sales && myTab == 2) {
                d.group = soft(d.notes, "ویزیتور", () -> Repo.exec(c, Queries.salesByVisitor(m, f, 50)));
            } else if (sales && myTab == 4) {
                d.group = soft(d.notes, "مسیر", () -> Repo.exec(c, Queries.factorByRoute(m, sales, f, 50)));
            } else if (sales && myTab == 5) {
                d.group = soft(d.notes, "گروه", () -> Repo.exec(c, Queries.factorByCustGroup(m, sales, f, 50)));
            } else if (sales && myTab == 7) {
                final String fcastTo = Jalali.todayStr();
                d.forecast = soft(d.notes, "پیش‌بینی", () -> Repo.exec(c, Queries.factorDaily(m, true, Jalali.addDays(fcastTo, -59), fcastTo)));
            } else {
                d.topProducts = soft(d.notes, "کالا", () -> Repo.exec(c, Queries.factorTopProducts(m, sales, f, 50)));
            }
            if (d.summary == null) throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                saveCache(d);
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                Data cached = loadCache(lab);
                if (cached != null) {
                    build(content, cached);
                    offlineBanner(content, lab[0], faError);
                    return;
                }
                content.removeAllViews();
                content.addView(heroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(12));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private void saveCache(Data d) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sum", d.summary);
        m.put("daily", d.daily);
        m.put("list", d.list);
        m.put("group", d.group);
        m.put("top", d.topProducts);
        m.put("od", d.overdue);
        m.put("fc", d.forecast);
        m.put("tab", d.snapTab);
        CacheStore.saveData(a, "cx_trade_" + id(), cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_trade_" + id(), lab);
        if (m == null) return null;
        Data d = new Data();
        d.summary = CacheStore.row(m, "sum");
        d.daily = CacheStore.rows(m, "daily");
        d.list = CacheStore.rows(m, "list");
        d.group = CacheStore.rows(m, "group");
        d.topProducts = CacheStore.rows(m, "top");
        d.overdue = CacheStore.rows(m, "od");
        d.forecast = CacheStore.rows(m, "fc");
        d.snapTab = (int) CacheStore.num(m, "tab");
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        addSearchRow(content);

        Row s = d.summary == null ? new Row() : d.summary;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("جمع", Money.compactRial(s.d("total")), Money.fa(String.valueOf(s.l("docs"))) + " سند", accent()));
        kpis.add(new Kit.Kpi(sales ? "دریافتی" : "پرداختی", Money.compactRial(s.d("paid")), pct(s.d("paid"), s.d("total")) + " وصول", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("تخفیف", Money.compactRial(s.d("discount")), "", Theme.WARNING));
        kpis.add(new Kit.Kpi("مانده", Money.compactRial(Math.max(0, s.d("remain"))), "", Theme.DANGER));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (d.daily != null && !d.daily.isEmpty()) {
            LinearLayout c = a.kit.card(accent());
            c.addView(a.kit.text("روند روزانه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : d.daily) pts.add(new Charts.Point(Jalali.shortLabel(r.s("day")), r.d("total")));
            final TextView cap = a.kit.text("", 11f, Theme.MUTED, false);
            ch.setData(pts, accent(), Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.rial(p.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(175)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        content.addView(a.kit.chips(tabs(), d.snapTab, idx -> {
            tab = idx;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (d.snapTab == 0) buildList(content, d);
        else if (sales && d.snapTab == 1) buildOverdue(content, d);
        else if ((!sales && d.snapTab == 1) || (sales && d.snapTab == 3)) buildGroup(content, d, "طرف‌حساب");
        else if (sales && d.snapTab == 2) buildGroup(content, d, "ویزیتور");
        else if (sales && d.snapTab == 4) buildGroup(content, d, "مسیر");
        else if (sales && d.snapTab == 5) buildGroup(content, d, "گروه مشتری");
        else if (sales && d.snapTab == 7) buildForecast(content, d);
        else buildTopProducts(content, d);

        renderNotes(content, d.notes);
    }

    /** F1: 7-day sales forecast = 30-day average × per-weekday factor. */
    private void buildForecast(LinearLayout content, Data d) {
        if (d.forecast == null || d.forecast.isEmpty()) {
            content.addView(a.kit.empty("داده‌ای برای پیش‌بینی وجود ندارد", null), a.kit.lp(-1, -2));
            return;
        }
        java.util.Map<String, Double> map = new java.util.HashMap<>();
        for (Row r : d.forecast) {
            String k = Jalali.disp(r.s("day"));
            if (!k.isEmpty()) map.put(k, r.d("total"));
        }
        String today = Jalali.todayStr();
        double[] hist = new double[30];
        for (int i = 0; i < 30; i++) {
            Double v = map.get(Jalali.addDays(today, i - 29));
            hist[i] = v == null ? 0 : v;
        }
        double sum = 0;
        for (double v : hist) sum += v;
        double avg = sum / 30.0;
        double[] wSum = new double[7];
        int[] wN = new int[7];
        for (int i = 0; i < 30; i++) {
            int w = Jalali.weekdayIndex(Jalali.addDays(today, i - 29));
            if (w < 0) continue;
            wSum[w] += hist[i];
            wN[w]++;
        }
        double[] proj = new double[7];
        double p7 = 0;
        for (int i = 1; i <= 7; i++) {
            int w = Jalali.weekdayIndex(Jalali.addDays(today, i));
            double f = 1.0;
            if (w >= 0 && wN[w] > 0 && avg > 0) f = (wSum[w] / wN[w]) / avg;
            proj[i - 1] = avg * f;
            p7 += proj[i - 1];
        }
        double last7 = 0, prev7 = 0;
        for (int i = 0; i < 7; i++) last7 += hist[23 + i];
        for (int i = 0; i < 7; i++) prev7 += hist[16 + i];
        double trend = prev7 > 0 ? (last7 - prev7) * 100.0 / prev7 : 0;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("پیش‌بینی ۷ روز", Money.compactRial(p7), "مدل رفتار روز هفته", Theme.GOLD));
        kpis.add(new Kit.Kpi("پیش‌بینی ۳۰ روز", Money.compactRial(avg * 30.0), "میانگین روزانه " + Money.compactRial(avg), Theme.INFO));
        kpis.add(new Kit.Kpi("روند هفته", (trend >= 0 ? "+" : "") + Money.pct(trend), "نسبت به هفته قبل", trend >= 0 ? Theme.SUCCESS : Theme.DANGER));
        kpis.add(new Kit.Kpi("فروش ۳۰ روز", Money.compactRial(sum), "مبنای مدل", accent()));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text("پیش‌بینی ۷ روز آینده", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.Bars bars = new Charts.Bars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 1; i <= 7; i++)
            pts.add(new Charts.Point(Jalali.shortLabel(Jalali.addDays(today, i)), proj[i - 1]));
        bars.setData(pts, Charts.COMPACT);
        c.addView(bars, new LinearLayout.LayoutParams(-1, Theme.dp(200)));
        a.kit.addCard(content, c);

        LinearLayout t = a.kit.card(Theme.GOLD);
        t.addView(a.kit.text("جزئیات پیش‌بینی", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            String day = Jalali.addDays(today, i);
            Row r = new Row();
            r.put("day", day);
            r.put("wd", Jalali.weekday(day));
            r.put("amount", proj[i - 1]);
            rows.add(r);
        }
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("day", "روز", ReportCatalog.T_DATE),
                new ReportCatalog.Col("wd", "روز هفته", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("amount", "پیش‌بینی فروش", ReportCatalog.T_MONEY),
        };
        t.addView(a.kit.dataTable(cols, rows, null), a.kit.lp(-1, -2));
        t.addView(a.kit.hint("مدل ساده: میانگین ۳۰ روز × ضریب رفتار هر روز هفته"), a.kit.lp(-1, -2));
        a.kit.addCard(content, t);
    }

    private void buildList(LinearLayout content, Data d) {
        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("فاکتوری در این بازه یافت نشد", "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : d.list) {
            final String no = r.s("no");
            boolean settled = Math.abs(r.d("remain")) <= AtiranSchema.SETTLE_TOLERANCE
                    || "t".equalsIgnoreCase(r.s("tasvieh").trim());
            String status = settled ? "settled" : "unsettled";
            View v = a.kit.invoiceRow("فاکتور " + Money.fa(no), r.s("customer"),
                    Jalali.shortLabel(r.s("date")), Money.rial(r.d("total")),
                    status, v2 -> openDetailByNo(no));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
        content.addView(a.kit.pager(filter.page, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
    }

    private void buildOverdue(LinearLayout content, Data d) {
        if (d.overdue == null || d.overdue.isEmpty()) {
            content.addView(a.kit.empty("معوقی ثبت نشده است", "فاکتورهای سررسیدگذشته اینجا نمایش داده می‌شوند"), a.kit.lp(-1, -2));
            return;
        }
        double sum = 0;
        for (Row r : d.overdue) sum += r.d("amount");
        LinearLayout c = a.kit.card(Theme.DANGER);
        c.addView(a.kit.kv("جمع معوق", Money.rial(sum), Theme.DANGER), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("تعداد", Money.fa(String.valueOf(d.overdue.size())) + " فاکتور", Theme.DANGER), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
        for (Row r : d.overdue) {
            final String no = r.s("invoice");
            long dd = r.l("days");
            // Without dif_date_alan the server sends −1: derive the delay from the due date.
            if (dd < 0) dd = Jalali.diffDays(r.s("dueDate"), Jalali.todayStr());
            String days = dd >= 0 ? "تأخیر " + Money.fa(String.valueOf(dd)) + " روز • " : "";
            View v = a.kit.alertRow("فاکتور " + Money.fa(no), days + r.s("party") + " • " + Money.rial(r.d("amount")),
                    Theme.DANGER, v2 -> openDetailByNo(no));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
    }

    private void buildGroup(LinearLayout content, Data d, String title) {
        if (d.group == null || d.group.isEmpty()) {
            content.addView(a.kit.empty("داده‌ای برای «" + title + "» یافت نشد", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text(title, 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(12, d.group.size()); i++)
            pts.add(new Charts.Point(d.group.get(i).s("label"), d.group.get(i).d("total")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", title, ReportCatalog.T_TEXT),
                new ReportCatalog.Col("total", "جمع", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("docs", "اسناد", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.group, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private void buildTopProducts(LinearLayout content, Data d) {
        if (d.topProducts == null || d.topProducts.isEmpty()) {
            content.addView(a.kit.empty("کالای پرفروشی یافت نشد", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(Theme.VIOLET);
        c.addView(a.kit.text(sales ? "کالاهای پرفروش" : "کالاهای پرخرید", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(12, d.topProducts.size()); i++)
            pts.add(new Charts.Point(d.topProducts.get(i).s("label"), d.topProducts.get(i).d("total")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("qty", "مقدار", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.topProducts, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private String pct(double part, double whole) {
        if (whole <= 0) return "٪۰";
        return Money.pct(part * 100.0 / whole);
    }

    // ---------------- invoice detail ----------------
    private void openDetailByNo(final String no) {
        a.kit.toast("در حال دریافت فاکتور…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Row head = Repo.one(c, Queries.factorHeader(m, sales, no));
            List<Row> lines = Repo.exec(c, Queries.factorLines(m, sales, no));
            List<Row> dars = new ArrayList<>();
            try {
                dars = Repo.exec(c, MoneyQueries.invoiceDars(m, sales ? 0 : 1, no));
            } catch (Exception ignored) { }
            return new Detail(head, lines, dars);
        }, new Repo.Cb<Detail>() {
            @Override
            public void ok(Detail dt) {
                if (dt == null || dt.head == null || dt.head.s("no").isEmpty()) {
                    a.kit.toast("فاکتور یافت نشد");
                    return;
                }
                showDetail(dt);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private static final class Detail {
        final Row head;
        final List<Row> lines;
        final List<Row> dars;

        Detail(Row head, List<Row> lines, List<Row> dars) {
            this.head = head;
            this.lines = lines;
            this.dars = dars;
        }
    }

    private void showDetail(Detail dt) {
        Row head = dt.head;
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("شماره", Money.fa(head.s("no")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("تاریخ", Jalali.dispFa(head.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv(sales ? "مشتری" : "طرف‌حساب", head.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (sales && !head.s("visitor").isEmpty() && !"بدون ویزیتور".equals(head.s("visitor")))
            body.addView(a.kit.kv("ویزیتور", head.s("visitor"), Theme.TEXT), a.kit.lp(-1, -2));
        String desc = head.has("description") ? head.s("description") : head.s("descrip");
        if (!desc.isEmpty()) body.addView(a.kit.kv("شرح", desc, Theme.MUTED), a.kit.lp(-1, -2));
        if (head.has("tafif") && head.d("tafif") > 0)
            body.addView(a.kit.kv("تخفیف", Money.rial(head.d("tafif")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("tax") && head.d("tax") > 0)
            body.addView(a.kit.kv("مالیات", Money.rial(head.d("tax")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("جمع", Money.rial(head.d("total")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv(sales ? "دریافتی" : "پرداختی", Money.rial(head.d("paid")), Theme.SUCCESS), a.kit.lp(-1, -2));
        String tasvieh = head.has("tasvieh") ? AtiranSchema.tasviehFa(head.s("tasvieh")) : "نامشخص";
        if (!"نامشخص".equals(tasvieh))
            body.addView(a.kit.kv("وضعیت تسویه", tasvieh,
                    "تسویه‌شده".equals(tasvieh) ? Theme.SUCCESS : Theme.WARNING), a.kit.lp(-1, -2));
        double remain = head.d("total") - head.d("paid");
        if (Math.abs(remain) > AtiranSchema.SETTLE_TOLERANCE)
            body.addView(a.kit.kv("مانده", Money.rial(remain), remain > 0 ? Theme.DANGER : Theme.INFO), a.kit.lp(-1, -2));
        body.addView(a.kit.text("مبلغ به حروف: " + Money.words(head.d("total")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        if (!dt.lines.isEmpty()) {
            body.addView(a.kit.text("اقلام فاکتور", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            for (Row r : dt.lines) {
                if (Math.abs(r.d("qtyVah")) < 0.0005 && Math.abs(r.d("qtyJoz")) > 0.0005)
                    r.put("qtyVah", r.d("qtyJoz"));
                if (Math.abs(r.d("vahPrice")) < 0.005 && Math.abs(r.d("jozPrice")) > 0.005)
                    r.put("vahPrice", r.d("jozPrice"));
            }
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qtyVah", "مقدار", ReportCatalog.T_NUM),
                    new ReportCatalog.Col("vahPrice", "فی", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("lineSum", "مبلغ", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, dt.lines, null), a.kit.lp(-1, -2));
        }
        if (!dt.dars.isEmpty()) {
            body.addView(a.kit.text(sales ? "دریافت‌های این فاکتور" : "پرداخت‌های این فاکتور", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("ghno", "قبض", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("total", "مبلغ قبض", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("settled", "تسویه‌شده", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, dt.dars, null), a.kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        final List<Row> fLines = dt.lines;
        final String fNo = head.s("no");
        final String fParty = head.s("customer");
        final String fDate = Jalali.dispFa(head.s("date"));
        LinearLayout footer = a.kit.h();
        footer.addView(a.kit.btn("اشتراک PDF", v -> {
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qtyVah", "مقدار", ReportCatalog.T_NUM),
                    new ReportCatalog.Col("vahPrice", "فی", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("lineSum", "مبلغ", ReportCatalog.T_MONEY),
            };
            a.sharePdf("فاکتور " + fNo, fParty + " • " + fDate, cols, fLines);
        }), a.kit.wlp(1f));
        footer.addView(a.kit.space(8));
        footer.addView(a.kit.btnGhost("🖨 چاپ", Theme.GOLD, v -> FisPrint.print(a, head, fLines, sales)), a.kit.wlp(1f));
        footer.addView(a.kit.space(8));
        footer.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.wlp(1f));
        body.addView(a.kit.gap(8));
        body.addView(footer, a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }
}
