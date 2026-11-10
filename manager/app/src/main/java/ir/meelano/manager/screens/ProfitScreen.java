package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profit & loss: sales − COGS (buy-price basis) − discounts − other costs. */
public class ProfitScreen extends Screen {
    private final Filter filter = new Filter();

    public ProfitScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "profit"; }

    @Override
    public String title() { return "سود و زیان"; }

    @Override
    public String glyph() { return Theme.isLight() ? "↗" : "💹"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.search = false;
        c.range = true;
        return c;
    }

    private static final class Data {
        Row sales = new Row();
        Row cogs = new Row();
        List<Row> daily = new ArrayList<>();
        List<Row> byProduct = new ArrayList<>();
        List<Row> byCust = new ArrayList<>();
        List<Row> costs = new ArrayList<>();
        boolean grossProfitCoverageComplete;
        Map<String, String> notes = new LinkedHashMap<>();
    }

    static boolean grossProfitCoverageComplete(List<Row> daily, boolean summaryAvailable) {
        if (!summaryAvailable || daily == null) return false;
        for (Row row : daily) {
            Long issues = row == null ? null : row.exactLong("qualityIssues");
            if (issues == null || issues != 0L || row.bd("sales") == null
                    || row.bd("cogs") == null || row.bd("profit") == null) return false;
        }
        return true;
    }

    static String grossProfitCoverageWarningTitle(boolean queryIncomplete) {
        return queryIncomplete ? "سود و زیان نمایش داده نمی‌شود" : "بهای کالا ناقص است";
    }

    static String grossProfitCoverageDetails(List<Row> daily) {
        StringBuilder message = new StringBuilder("برای نمایش معتبر سود و حاشیهٔ سود، هر ردیف فروش باید به کالایی با بهای خرید معتبر و غیرمنفی وصل باشد؛ ردیف ناقص با مبلغ صفر جایگزین نمی‌شود.");
        if (daily == null) return message.append("\nریز روزانه در دسترس نیست.").toString();

        long totalIssues = 0L;
        int affectedDays = 0;
        boolean metadataMissing = false;
        List<String> examples = new ArrayList<>();
        for (Row row : daily) {
            Long count = row == null ? null : row.exactLong("qualityIssues");
            if (count == null || count < 0L) {
                metadataMissing = true;
                continue;
            }
            if (count == 0L) continue;
            totalIssues = count > Long.MAX_VALUE - totalIssues ? Long.MAX_VALUE : totalIssues + count;
            affectedDays++;
            if (examples.size() < 5) {
                String date = row.s("day").isEmpty() ? "تاریخ نامشخص" : Jalali.dispFa(row.s("day"));
                examples.add(date + " · " + HomeNumberFormatter.integer(count) + " ردیف");
            }
        }
        if (totalIssues > 0L) {
            message.append("\nردیف‌های نیازمند بررسی: ")
                    .append(HomeNumberFormatter.integer(totalIssues)).append('.');
            if (!examples.isEmpty()) {
                message.append("\nنمونهٔ روزها: ");
                for (int i = 0; i < examples.size(); i++) {
                    if (i > 0) message.append("؛ ");
                    message.append(examples.get(i));
                }
                if (affectedDays > examples.size()) message.append("؛ و روزهای دیگر");
            }
        }
        if (metadataMissing)
            message.append("\nجزئیات برخی روزها در دادهٔ موجود نیست؛ مقدار ناقص صفر فرض نشده است.");
        return message.toString();
    }

    private void showGrossProfitCoverageDetails(Data data, boolean queryIncomplete) {
        String title = queryIncomplete ? "جزئیات دادهٔ سود و زیان" : "جزئیات بهای کالا";
        String detail = queryIncomplete
                ? "دادهٔ لازم برای محاسبهٔ کامل در دسترس نیست؛ تا دریافت دادهٔ معتبر، سود و حاشیهٔ سود نمایش داده نمی‌شود."
                : grossProfitCoverageDetails(data == null ? null : data.daily);
        AlertDialog.Builder dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(HomeNumberFormatter.rtlText(detail))
                .setNegativeButton("بستن", null);
        if (RoleStore.allowed(a, "products"))
            dialog.setPositiveButton("فهرست کالاها", (ignored, which) -> a.nav("products"));
        dialog.show();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال محاسبه سود و زیان…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.sales = soft(d.notes, "فروش", () -> Repo.one(c, Queries.factorSummary(m, true, f)));
            d.cogs = soft(d.notes, "بهای تمام‌شده", () -> Repo.one(c, MasterQueries.profitCogs(m, f)));
            d.daily = soft(d.notes, "روند", () -> Repo.exec(c, MasterQueries.profitDaily(m, f)));
            d.byProduct = soft(d.notes, "سود کالاها", () -> Repo.exec(c, MasterQueries.profitByProduct(m, f, 30)));
            d.byCust = soft(d.notes, "سود مشتریان", () -> Repo.exec(c, MasterQueries.profitByCustomer(m, f, 20)));
            d.costs = soft(d.notes, "هزینه‌ها", () -> Repo.exec(c, MasterQueries.profitCosts(m, f)));
            d.grossProfitCoverageComplete = grossProfitCoverageComplete(d.daily,
                    d.sales != null && d.cogs != null);
            if (d.sales == null && d.cogs == null) throw new Exception(firstNote(d.notes));
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
        m.put("sales", d.sales);
        m.put("cogs", d.cogs);
        m.put("daily", d.daily);
        m.put("byProduct", d.byProduct);
        m.put("byCust", d.byCust);
        m.put("costs", d.costs);
        CacheStore.saveData(a, "cx_profit", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_profit", lab);
        if (m == null) return null;
        Data d = new Data();
        d.sales = CacheStore.row(m, "sales");
        d.cogs = CacheStore.row(m, "cogs");
        d.daily = CacheStore.rows(m, "daily");
        d.byProduct = CacheStore.rows(m, "byProduct");
        d.byCust = CacheStore.rows(m, "byCust");
        d.costs = CacheStore.rows(m, "costs");
        d.grossProfitCoverageComplete = grossProfitCoverageComplete(d.daily,
                d.sales != null && d.cogs != null);
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("مبنای بهای تمام‌شده: قیمت خرید روز کالا • سود خالص تقریبی است"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        if (!d.grossProfitCoverageComplete) {
            java.math.BigDecimal knownSales = d.sales == null ? null : d.sales.bd("total");
            if (knownSales != null) {
                List<Kit.Kpi> safeKpis = new ArrayList<>();
                safeKpis.add(new Kit.Kpi("فروش", Money.compactRial(knownSales.doubleValue()),
                        "مبلغ ثبت‌شده", Theme.GOLD));
                content.addView(a.kit.kpiGrid(safeKpis, 1), a.kit.lp(-1, -2));
            }
            boolean queryIncomplete = d.daily == null || d.sales == null || d.cogs == null;
            LinearLayout warning = a.kit.card(Theme.WARNING);
            warning.addView(a.kit.text(grossProfitCoverageWarningTitle(queryIncomplete),
                    13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            warning.addView(a.kit.hint(queryIncomplete
                    ? "دادهٔ لازم برای محاسبهٔ کامل در دسترس نیست؛ سود صفر فرض نشده است."
                    : "بهای خرید یک یا چند ردیف فروش ناموجود یا نامعتبر است؛ سود و حاشیهٔ سود نمایش داده نمی‌شود."),
                    a.kit.lp(-1, -2));
            Button details = a.kit.btnGhost("جزئیات", Theme.WARNING,
                    v -> showGrossProfitCoverageDetails(d, queryIncomplete));
            details.setContentDescription("جزئیات وضعیت بهای کالا و ردیف‌های فروش");
            LinearLayout.LayoutParams detailsParams = a.kit.lp(-2, -2);
            detailsParams.topMargin = Theme.dp(6);
            warning.addView(details, detailsParams);
            a.kit.addCard(content, warning);
            renderNotes(content, d.notes);
            return;
        }

        double sales = d.sales == null ? 0 : d.sales.d("total");
        double discount = d.sales == null ? 0 : d.sales.d("discount");
        double cogs = d.cogs == null ? 0 : d.cogs.d("cogs");
        double costs = 0;
        if (d.costs != null) for (Row r : d.costs) costs += r.d("total");
        double gross = sales - cogs;
        double net = gross - discount - costs;

        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("فروش", Money.compactRial(sales), "", Theme.GOLD));
        kpis.add(new Kit.Kpi("بهای تمام‌شده", Money.compactRial(cogs), "", Theme.INFO));
        kpis.add(new Kit.Kpi("سود ناویژه", Money.compactRial(gross), sales > 0 ? Money.pct(gross * 100.0 / sales) + " حاشیه" : "", gross >= 0 ? Theme.SUCCESS : Theme.DANGER));
        kpis.add(new Kit.Kpi("سود خالص ≈", Money.compactRial(net), "پس از تخفیف و هزینه‌ها", net >= 0 ? Theme.SUCCESS : Theme.DANGER));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (d.daily != null && !d.daily.isEmpty()) {
            LinearLayout c = a.kit.card(accent());
            c.addView(a.kit.text("سود روزانه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : d.daily)
                pts.add(new Charts.Point(HomeNumberFormatter.dateShortLabel(r.s("day")),
                        r.d("sales") - r.d("cogs")));
            final TextView cap = a.kit.text("", 11f, Theme.MUTED, false);
            ch.setData(pts, accent(), Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(ir.meelano.manager.core.HomeNumberFormatter.rtlText(
                    Money.fa(p.label) + " • " + Money.compactRial(p.value))));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(175)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        if (d.byProduct != null && !d.byProduct.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.VIOLET);
            c.addView(a.kit.text("سود به تفکیک کالا", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(12, d.byProduct.size()); i++)
                pts.add(new Charts.Point(d.byProduct.get(i).s("label"), d.byProduct.get(i).d("profit")));
            hb.setData(pts, Charts.COMPACT);
            c.addView(hb, a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("label", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("sales", "فروش", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("cogs", "بهای تمام‌شده", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("profit", "سود", ReportCatalog.T_MONEY),
            };
            c.addView(a.kit.dataTable(cols, d.byProduct, null), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        if (d.byCust != null && !d.byCust.isEmpty()) {
            for (Row r : d.byCust) {
                double sl = r.d("sales");
                r.put("margin", sl > 0 ? Money.pct(r.d("profit") * 100.0 / sl) : "—");
            }
            LinearLayout c = a.kit.card(Theme.SUCCESS);
            c.addView(a.kit.text("سود به تفکیک مشتری", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(12, d.byCust.size()); i++)
                pts.add(new Charts.Point(d.byCust.get(i).s("label"), d.byCust.get(i).d("profit")));
            hb.setData(pts, Charts.COMPACT);
            c.addView(hb, a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("label", "مشتری", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("docs", "فاکتور", ReportCatalog.T_NUM),
                    new ReportCatalog.Col("sales", "فروش", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("profit", "سود", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("margin", "حاشیه", ReportCatalog.T_TEXT),
            };
            c.addView(a.kit.dataTable(cols, d.byCust, null), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        if (d.costs != null && !d.costs.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.DANGER);
            c.addView(a.kit.kv("سایر هزینه‌ها", Money.compactRial(costs), Theme.DANGER), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("label", "سرفصل", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("total", "جمع", ReportCatalog.T_MONEY),
            };
            c.addView(a.kit.dataTable(cols, d.costs, null), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        renderNotes(content, d.notes);
    }
}
