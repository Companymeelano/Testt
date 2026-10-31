package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.PersianText;
import ir.meelano.manager.core.ProductStockStatus;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
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

/** Products: catalogue, stock, low/zero alerts, group value, turnover dossier. */
public class ProductsScreen extends Screen {
    private final Filter filter = new Filter();
    private int tab;

    public ProductsScreen(MainActivity a) {
        super(a);
        filter.top = 50;
        filter.status = "active";
    }

    @Override
    public String id() { return "products"; }

    @Override
    public String title() { return "کالاها"; }

    @Override
    public String glyph() { return "▦"; }

    @Override
    public int accent() { return Theme.VIOLET; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = "نام، کد یا شِکالا…";
        c.range = true;
        c.warehouses = true;
        c.kalaGroups = true;
        c.statusTitle = "وضعیت موجودی";
        c.status = new FilterSheet.Opt[]{
                // FilterSheet adds «همه»; active is the safe default for the product catalogue.
                new FilterSheet.Opt("active", "فعال"),
                new FilterSheet.Opt("inactive", "غیرفعال"),
                new FilterSheet.Opt("low", "کم‌موجودی"),
                new FilterSheet.Opt("out", "ناموجود"),
                new FilterSheet.Opt("ok", "موجودی سالم"),
                new FilterSheet.Opt("reorder", "زیر نقطه سفارش"),
        };
        c.sortTitle = "مرتب‌سازی";
        c.sort = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "نام کالا"),
                new FilterSheet.Opt("stock_asc", "کمترین موجودی"),
                new FilterSheet.Opt("stock_desc", "بیشترین موجودی"),
                new FilterSheet.Opt("price_desc", "گران‌ترین"),
        };
        return c;
    }

    private static final class Data {
        int snapTab;
        String listFilterKey = "";
        long listTotal = -1;
        Row summary = new Row();
        List<Row> list = new ArrayList<>();
        List<Row> low = new ArrayList<>();
        List<Row> groups = new ArrayList<>();
        List<Row> top = new ArrayList<>();
        List<Row> dead = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("آستانه کم‌موجودی: ۵ واحد • گردش کالا بر اساس بازه فیلتر"), a.kit.lp(-1, -2));
        content.addView(a.kit.btnGhost("▦ اسکن بارکد کالا", Theme.VIOLET, v -> a.startScan(code -> {
            filter.search = code;
            filter.page = 0;
            a.refreshChrome();
            render(content);
        })), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت کالاها…"), a.kit.lp(-1, -2));
        final int myTab = tab;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.snapTab = myTab;
            d.summary = soft(d.notes, "خلاصه", () -> Repo.one(c, MasterQueries.productsSummary(m)));
            if (myTab == 0) {
                d.listFilterKey = productFilterKey(f);
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MasterQueries.productsList(m, f)));
                Row total = soft(d.notes, "شمارش فهرست", () -> Repo.one(c, MasterQueries.productsCount(m, f)));
                if (total != null) d.listTotal = Math.max(0, total.l("total"));
            } else if (myTab == 1) {
                final Filter lf = f.copy();
                lf.status = "low";
                lf.sort = "stock_asc";
                lf.top = 300;
                lf.page = 0;
                d.low = soft(d.notes, "کم‌موجودی", () -> Repo.exec(c, MasterQueries.productsList(m, lf)));
            } else if (myTab == 2) {
                d.groups = soft(d.notes, "ارزش گروه‌ها", () -> Repo.exec(c, MasterQueries.stockValueByGroup(m)));
            } else if (myTab == 3) {
                d.top = soft(d.notes, "پرفروش‌ها", () -> Repo.exec(c, Queries.factorTopProducts(m, true, f, 50)));
            } else {
                d.dead = soft(d.notes, "راکد", () -> Repo.exec(c, MasterQueries.deadStock(m, 90, 100)));
            }
            if (d.summary == null && d.list == null) throw new Exception(firstNote(d.notes));
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

    private static String productFilterKey(Filter filter) {
        Filter f = filter == null ? new Filter() : filter;
        StringBuilder key = new StringBuilder();
        appendKeyPart(key, f.status);
        appendKeyPart(key, f.search);
        appendKeyPart(key, String.valueOf(f.kalaGroup));
        appendKeyPart(key, String.valueOf(f.warehouse));
        appendKeyPart(key, f.sort);
        appendKeyPart(key, String.valueOf(f.top));
        appendKeyPart(key, String.valueOf(f.page));
        return key.toString();
    }

    private static void appendKeyPart(StringBuilder key, String value) {
        String part = value == null ? "" : value;
        key.append(part.length()).append(':').append(part).append(';');
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private void saveCache(Data d) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sum", d.summary);
        m.put("list", d.list);
        m.put("low", d.low);
        m.put("groups", d.groups);
        m.put("top", d.top);
        m.put("dead", d.dead);
        m.put("tab", d.snapTab);
        if (d.snapTab == 0) m.put("listFilterKey", d.listFilterKey);
        if (d.listTotal >= 0) m.put("total", d.listTotal);
        CacheStore.saveData(a, "cx_prod", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_prod", lab);
        if (m == null) return null;
        Data d = new Data();
        d.summary = CacheStore.row(m, "sum");
        d.list = CacheStore.rows(m, "list");
        d.low = CacheStore.rows(m, "low");
        d.groups = CacheStore.rows(m, "groups");
        d.top = CacheStore.rows(m, "top");
        d.dead = CacheStore.rows(m, "dead");
        d.listTotal = m.containsKey("total") ? CacheStore.num(m, "total") : -1;
        d.listFilterKey = String.valueOf(m.get("listFilterKey") == null ? "" : m.get("listFilterKey"));
        d.snapTab = (int) CacheStore.num(m, "tab");
        if (d.snapTab == 0 && !productFilterKey(filter).equals(d.listFilterKey)) return null;
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("آستانه کم‌موجودی: ۵ واحد • گردش کالا بر اساس بازه فیلتر"), a.kit.lp(-1, -2));
        content.addView(a.kit.btnGhost("▦ اسکن بارکد کالا", Theme.VIOLET, v -> a.startScan(code -> {
            filter.search = code;
            filter.page = 0;
            a.refreshChrome();
            render(content);
        })), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        Row s = d.summary == null ? new Row() : d.summary;
        boolean activeKnown = s.i("activeKnown") != 0;
        boolean reorderKnown = s.i("reorderKnown") != 0;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi(activeKnown ? "کالای فعال" : "اقلام",
                Money.num(activeKnown ? s.l("active") : s.l("count")), "نمایش فهرست",
                accent(), activeKnown ? () -> showListStatus(content, "active") : null));
        if (activeKnown) {
            long inactive = Math.max(0, s.l("count") - s.l("active"));
            kpis.add(new Kit.Kpi("کالای غیرفعال", Money.num(inactive), "نمایش فهرست",
                    Theme.MUTED, () -> showListStatus(content, "inactive")));
        }
        Kit.Kpi stockValue = new Kit.Kpi("ارزش موجودی", Money.compactRial(s.d("value")), "به قیمت خرید", Theme.GOLD);
        stockValue.fullValue = Money.rialText(s.d("value"));
        kpis.add(stockValue);
        kpis.add(new Kit.Kpi("ناموجود", Money.num(s.l("out")), "فهرست ناموجودها", Theme.DANGER,
                () -> showListStatus(content, "out")));
        kpis.add(new Kit.Kpi("کم‌موجودی", Money.num(s.l("low")), "فهرست کم‌موجودها", Theme.WARNING,
                () -> showListStatus(content, "low")));
        if (reorderKnown) kpis.add(new Kit.Kpi("نقطه سفارش", Money.num(s.l("reorderN")), "زیر نقطه سفارش", Theme.INFO,
                () -> showListStatus(content, "reorder")));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        if (!activeKnown) {
            content.addView(a.kit.hint("ستون وضعیت فعال در این دیتابیس موجود نیست؛ شمارش کلی به‌جای شمارش فعال نمایش داده می‌شود."), a.kit.lp(-1, -2));
        }
        content.addView(a.kit.gap(12));

        content.addView(a.kit.chips(new String[]{"فهرست", "کم‌موجودی", "ارزش گروه‌ها", "پرفروش‌ها", "راکد"}, d.snapTab, idx -> {
            tab = idx;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (d.snapTab == 0) buildList(content, d);
        else if (d.snapTab == 1) buildLow(content, d);
        else if (d.snapTab == 2) buildGroups(content, d);
        else if (d.snapTab == 3) buildTop(content, d);
        else buildDead(content, d);

        renderNotes(content, d.notes);
    }

    private void showListStatus(LinearLayout content, String status) {
        tab = 0;
        filter.status = status;
        filter.page = 0;
        render(content);
    }

    private void buildList(LinearLayout content, Data d) {
        List<Row> rows = d.list == null ? new ArrayList<>() : d.list;
        int pageSize = Queries.clampTop(filter.top);
        long pageOffset = (long) Math.max(0, filter.page) * pageSize;
        if (d.listTotal >= 0) {
            long first = rows.isEmpty() ? 0 : pageOffset + 1;
            long last = rows.isEmpty() ? 0 : Math.min(d.listTotal, pageOffset + rows.size());
            content.addView(a.kit.hint("نمایش " + Money.num(first) + " تا " + Money.num(last)
                    + " از " + Money.num(d.listTotal) + " کالا"), a.kit.lp(-1, -2));
        } else {
            content.addView(a.kit.hint(Money.num(rows.size()) + " کالا در این صفحه • شمارش کل در دسترس نیست"), a.kit.lp(-1, -2));
        }
        if (rows.isEmpty()) {
            boolean laterPage = d.listTotal > 0 && filter.page > 0;
            content.addView(a.kit.empty(laterPage ? "در این صفحه کالایی نیست" : "کالایی یافت نشد",
                    laterPage ? "صفحه قبل یا فیلتر دیگری را امتحان کنید" : "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
            if (filter.page > 0) content.addView(a.kit.pager(filter.page, d.listTotal, pageSize,
                    d.listTotal >= 0, false,
                    () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                    () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : rows) {
            final String shka = r.s("shka");
            String name = PersianText.cleanDisplayLabel(r.s("naka"));
            if (name.isEmpty()) name = "بدون نام کالا";
            String unit = PersianText.cleanDisplayLabel(r.s("unit"));
            String group = PersianText.cleanGroupLabel(r.s("groupName"));
            String extra = group;
            if (!unit.isEmpty()) extra += (extra.isEmpty() ? "" : " • ") + unit;

            boolean warehouse = filter.warehouse >= 0;
            double majorQty = warehouse ? r.d("anbarQty") : r.d("vah");
            double looseQty = warehouse ? r.d("anbarJoz") : r.d("joz");
            double pieces = warehouse ? r.d("anbarPieces") : r.d("stockPieces");
            boolean stockKnown = warehouse ? (r.has("anbarQty") || r.has("anbarJoz"))
                    : (r.has("vah") || r.has("stockPieces"));
            String stock = stockKnown ? (warehouse ? "انبار " : "موجودی ") + Money.qtyText(majorQty)
                    : "موجودی نامشخص";
            if (stockKnown && !unit.isEmpty()) stock += " " + unit;
            if (stockKnown && Math.abs(looseQty) > 0.000001d)
                stock += " + " + Money.qtyText(looseQty) + " عدد";

            ProductStockStatus.Band band = ProductStockStatus.classify(stockKnown, majorQty, pieces,
                    r.d("reorderPoint"), AtiranSchema.LOW_STOCK_VAH);
            boolean activeKnownForRow = r.i("activeKnown") != 0;
            boolean inactive = activeKnownForRow && !ProductStockStatus.isActive(r.s("active"));
            String status = inactive ? "غیرفعال" : ProductStockStatus.label(band);
            int tone = inactive ? Theme.MUTED : stockTone(band);

            String code = PersianText.cleanDisplayLabel(r.s("code"));
            if (code.isEmpty() || "--".equals(code) || "—".equals(code) || "-".equals(code)) code = shka;
            boolean saleKnown = r.i("saleKnown") != 0;
            boolean priceWarning = !saleKnown || !r.has("sale") || r.d("sale") <= 0;
            String price;
            if (!saleKnown) price = "قیمت فروش نامشخص";
            else if (!r.has("sale") || r.d("sale") <= 0)
                price = r.d("sale") < 0 ? "قیمت فروش نامعتبر" : "قیمت فروش ثبت نشده";
            else price = Money.rialText(r.d("sale"));
            int priceTone = r.has("sale") && r.d("sale") < 0 ? Theme.DANGER
                    : priceWarning ? Theme.WARNING : Theme.GOLD_SOFT;

            View v = a.kit.invRowTone(name, "کد کالا " + Money.ltrIsolate(Money.fa(code)), extra, stock,
                    price, status, tone, priceTone, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        boolean hasMore = d.listTotal >= 0
                ? pageOffset + rows.size() < d.listTotal
                : rows.size() >= pageSize;
        content.addView(a.kit.pager(filter.page, d.listTotal, pageSize, d.listTotal >= 0, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { if (hasMore) { filter.page = filter.page + 1; render(content); } }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        ReportCatalog.Col[] expCols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("shka", "کد کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("groupName", "گروه", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("vah", "موجودی", ReportCatalog.T_NUM),
                new ReportCatalog.Col("sale", "قیمت فروش", ReportCatalog.T_MONEY),
        };
        content.addView(a.exportBar("فهرست کالاها", "", expCols, rows), a.kit.lp(-1, -2));
    }

    private int stockTone(ProductStockStatus.Band band) {
        switch (band) {
            case NEGATIVE:
            case OUT: return Theme.DANGER;
            case REORDER: return Theme.INFO;
            case LOW: return Theme.WARNING;
            case NORMAL: return Theme.SUCCESS;
            case UNKNOWN:
            default: return Theme.MUTED;
        }
    }

    private void buildLow(LinearLayout content, Data d) {
        if (d.summary != null && d.summary.l("reorderN") > 0) {
            LinearLayout rc = a.kit.card(Theme.WARNING);
            rc.addView(a.kit.kv("زیر نقطه سفارش", Money.fa(String.valueOf(d.summary.l("reorderN"))) + " قلم", Theme.WARNING), a.kit.lp(-1, -2));
            rc.addView(a.kit.hint("فهرست کامل را با فیلتر «زیر نقطه سفارش» در تب فهرست ببینید"), a.kit.lp(-1, -2));
            a.kit.addCard(content, rc);
        }
        if (d.low == null || d.low.isEmpty()) {
            content.addView(a.kit.empty("کالای کم‌موجودی وجود ندارد", "موجودی همه اقلام بالای آستانه است"), a.kit.lp(-1, -2));
            return;
        }
        int cap = Math.min(d.low.size(), 120);
        for (int i = 0; i < cap; i++) {
            Row r = d.low.get(i);
            final String shka = r.s("shka");
            boolean zero = r.d("vah") <= 0;
            View v = a.kit.alertRow(r.s("naka"), "کد " + Money.fa(shka) + " • موجودی " + Money.qty(r.d("vah")),
                    zero ? Theme.DANGER : Theme.WARNING, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        if (d.low.size() > cap)
            content.addView(a.kit.hint("+" + Money.fa(String.valueOf(d.low.size() - cap)) + " قلم دیگر…"), a.kit.lp(-1, -2));
    }

    private void buildGroups(LinearLayout content, Data d) {
        if (d.groups == null || d.groups.isEmpty()) {
            content.addView(a.kit.empty("ارزش گروهی قابل محاسبه نیست", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text("ارزش موجودی به تفکیک گروه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.Donut dn = new Charts.Donut(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(8, d.groups.size()); i++)
            pts.add(new Charts.Point(d.groups.get(i).s("label"), d.groups.get(i).d("value"), Charts.palette(i)));
        double sum = 0;
        for (Row r : d.groups) sum += r.d("value");
        dn.setData(pts, "ارزش کل", Money.compactRial(sum));
        c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", "گروه", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("value", "ارزش", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("count", "اقلام", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.groups, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private void buildTop(LinearLayout content, Data d) {
        if (d.top == null || d.top.isEmpty()) {
            content.addView(a.kit.empty("فروشی در این بازه ثبت نشده است", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.text("پرفروش‌ترین کالاها", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(12, d.top.size()); i++)
            pts.add(new Charts.Point(d.top.get(i).s("label"), d.top.get(i).d("total")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("label", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("qty", "مقدار", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.top, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private void buildDead(LinearLayout content, Data d) {
        if (d.dead == null || d.dead.isEmpty()) {
            content.addView(a.kit.empty("کالای راکدی وجود ندارد", "همه اقلام موجود در ۹۰ روز گذشته فروش داشته‌اند"), a.kit.lp(-1, -2));
            return;
        }
        double stuck = 0;
        for (Row r : d.dead) stuck += r.d("buyValue");
        LinearLayout c = a.kit.card(Theme.WARNING);
        c.addView(a.kit.kv("اقلام راکد (۹۰ روز بدون فروش)", Money.fa(String.valueOf(d.dead.size())) + " قلم", Theme.TEXT), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("سرمایه خوابیده ≈", Money.rial(stuck), Theme.WARNING), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
        int cap = Math.min(d.dead.size(), 120);
        for (int i = 0; i < cap; i++) {
            Row r = d.dead.get(i);
            final String shka = r.s("shka");
            String last = r.s("lastSale");
            String sub = "کد " + Money.fa(shka) + " • موجودی " + Money.qty(r.d("vah"))
                    + (last.isEmpty() ? " • هرگز فروش نرفته" : " • آخرین فروش " + Jalali.shortLabel(last));
            View v = a.kit.alertRow(r.s("label"), sub, last.isEmpty() ? Theme.DANGER : Theme.WARNING, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        if (d.dead.size() > cap)
            content.addView(a.kit.hint("+" + Money.fa(String.valueOf(d.dead.size() - cap)) + " قلم دیگر…"), a.kit.lp(-1, -2));
    }

    // ---------------- product dossier ----------------
    public void openProduct(final String shka) {
        ProductFileScreen.open(a, shka);
    }
}
