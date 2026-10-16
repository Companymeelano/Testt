package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
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
                new FilterSheet.Opt("", "همه"),
                new FilterSheet.Opt("low", "کم‌موجودی"),
                new FilterSheet.Opt("out", "ناموجود"),
                new FilterSheet.Opt("ok", "موجودی سالم"),
                new FilterSheet.Opt("inactive", "غیرفعال"),
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
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MasterQueries.productsList(m, f)));
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
        d.snapTab = (int) CacheStore.num(m, "tab");
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
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("اقلام", Money.fa(String.valueOf(s.l("count"))), "", accent()));
        kpis.add(new Kit.Kpi("ارزش موجودی", Money.compactRial(s.d("value")), "به قیمت خرید", Theme.GOLD));
        kpis.add(new Kit.Kpi("ناموجود", Money.fa(String.valueOf(s.l("out"))), "قلم", Theme.DANGER));
        kpis.add(new Kit.Kpi("کم‌موجودی", Money.fa(String.valueOf(s.l("low"))), "قلم", Theme.WARNING));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
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

    private void buildList(LinearLayout content, Data d) {
        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("کالایی یافت نشد", "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : d.list) {
            final String shka = r.s("shka");
            String stock = "موجودی " + Money.qty(r.d("vah"));
            if (filter.warehouse >= 0 && r.has("anbarQty"))
                stock = "انبار " + Money.qty(r.d("anbarQty"));
            String extra = r.s("groupName");
            if (!r.s("unit").isEmpty()) extra += (extra.isEmpty() ? "" : " • ") + r.s("unit");
            // Stock tone uses the same threshold the screen states above (5 units).
            double qtyNow = filter.warehouse >= 0 && r.has("anbarQty") ? r.d("anbarQty") : r.d("vah");
            String status = qtyNow <= 0 ? "ناموجود" : (qtyNow <= 5 ? "کم‌موجود" : "موجود");
            int tone = qtyNow <= 0 ? Theme.DANGER : (qtyNow <= 5 ? Theme.WARNING : Theme.SUCCESS);
            View v = a.kit.invRowTone(r.s("naka"), "کد " + Money.fa(r.s("code").isEmpty() ? shka : r.s("code")),
                    extra, stock, Money.rial(r.d("sale")), status, tone, v2 -> openProduct(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
        content.addView(a.kit.pager(filter.page, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        ReportCatalog.Col[] expCols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("shka", "کد", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("groupName", "گروه", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("vah", "موجودی", ReportCatalog.T_NUM),
                new ReportCatalog.Col("sale", "قیمت فروش", ReportCatalog.T_MONEY),
        };
        content.addView(a.exportBar("فهرست کالاها", "", expCols, d.list), a.kit.lp(-1, -2));
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
