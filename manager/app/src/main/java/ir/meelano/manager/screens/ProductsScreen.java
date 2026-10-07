package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
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
    private static final int[] PALETTE = {
            Theme.GOLD, Theme.SUCCESS, Theme.INFO, Theme.VIOLET, Theme.WARNING, Theme.DANGER,
    };

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
        Row summary = new Row();
        List<Row> list = new ArrayList<>();
        List<Row> low = new ArrayList<>();
        List<Row> groups = new ArrayList<>();
        List<Row> top = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("آستانه کم‌موجودی: ۵ واحد • گردش کالا بر اساس بازه فیلتر"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت کالاها…"), a.kit.lp(-1, -2));
        final int myTab = tab;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
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
            } else {
                d.top = soft(d.notes, "پرفروش‌ها", () -> Repo.exec(c, Queries.factorTopProducts(m, true, f, 50)));
            }
            if (d.summary == null && d.list == null) throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                build(content, d);
            }

            @Override
            public void fail(String faError) {
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

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("آستانه کم‌موجودی: ۵ واحد • گردش کالا بر اساس بازه فیلتر"), a.kit.lp(-1, -2));
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

        content.addView(a.kit.chips(new String[]{"فهرست", "کم‌موجودی", "ارزش گروه‌ها", "پرفروش‌ها"}, tab, idx -> {
            tab = idx;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (tab == 0) buildList(content, d);
        else if (tab == 1) buildLow(content, d);
        else if (tab == 2) buildGroups(content, d);
        else buildTop(content, d);

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
            View v = a.kit.invRow(r.s("naka"), "کد " + Money.fa(r.s("code").isEmpty() ? shka : r.s("code")),
                    extra, stock, Money.rial(r.d("sale")), v2 -> openDetail(shka));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
        content.addView(a.kit.pager(filter.page, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
    }

    private void buildLow(LinearLayout content, Data d) {
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
                    zero ? Theme.DANGER : Theme.WARNING, v2 -> openDetail(shka));
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
            pts.add(new Charts.Point(d.groups.get(i).s("label"), d.groups.get(i).d("value"), PALETTE[i % PALETTE.length]));
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

    // ---------------- product dossier ----------------
    private void openDetail(final String shka) {
        a.kit.toast("در حال دریافت پرونده کالا…");
        final Filter f = filter.copy();
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Row head = Repo.one(c, MasterQueries.productHeader(m, shka));
            List<Row> turnover = new ArrayList<>();
            try {
                turnover = Repo.exec(c, MasterQueries.productTurnover(m, shka, f));
            } catch (Exception ignored) { }
            return new Detail(head, turnover);
        }, new Repo.Cb<Detail>() {
            @Override
            public void ok(Detail dt) {
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
        final List<Row> turnover;

        Detail(Row head, List<Row> turnover) {
            this.head = head;
            this.turnover = turnover;
        }
    }

    private void showDetail(Detail dt) {
        Row head = dt.head;
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("کالا", head.s("naka"), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("شِکالا", Money.fa(head.s("shka")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!head.s("groupName").isEmpty()) body.addView(a.kit.kv("گروه", head.s("groupName"), Theme.TEXT), a.kit.lp(-1, -2));
        String code = head.has("StuffCode") ? head.s("StuffCode") : head.s("coka");
        if (!code.isEmpty()) body.addView(a.kit.kv("کد", Money.fa(code), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("vahsanj") && !head.s("vahsanj").isEmpty())
            body.addView(a.kit.kv("واحد", head.s("vahsanj"), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("mojkavah")) body.addView(a.kit.kv("موجودی واحد", Money.qty(head.d("mojkavah")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("mojkajoz") && Math.abs(head.d("mojkajoz")) > 0.0005)
            body.addView(a.kit.kv("موجودی جزء", Money.qty(head.d("mojkajoz")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("reopoint") && Math.abs(head.d("reopoint")) > 0.0005)
            body.addView(a.kit.kv("نقطه سفارش", Money.qty(head.d("reopoint")), Theme.WARNING), a.kit.lp(-1, -2));
        double buy = head.has("buy_price") ? head.d("buy_price") : (head.has("pure_buy_price") ? head.d("pure_buy_price") : 0);
        if (buy > 0) body.addView(a.kit.kv("قیمت خرید", Money.rial(buy), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("FinalSalePrice") && head.d("FinalSalePrice") > 0)
            body.addView(a.kit.kv("قیمت فروش", Money.rial(head.d("FinalSalePrice")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (head.has("sharh") && !head.s("sharh").isEmpty())
            body.addView(a.kit.kv("شرح", head.s("sharh"), Theme.MUTED), a.kit.lp(-1, -2));

        if (dt.turnover.isEmpty()) {
            body.addView(a.kit.hint("گردش ثبت‌شده‌ای برای این کالا در بازه فیلتر یافت نشد"), a.kit.lp(-1, -2));
        } else {
            body.addView(a.kit.text("گردش کالا (" + Money.fa(String.valueOf(dt.turnover.size())) + " سند)", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("opLabel", "عملیات", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("inQty", "ورود", ReportCatalog.T_NUM),
                    new ReportCatalog.Col("outQty", "خروج", ReportCatalog.T_NUM),
            };
            body.addView(a.kit.dataTable(cols, dt.turnover, null), a.kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }
}
