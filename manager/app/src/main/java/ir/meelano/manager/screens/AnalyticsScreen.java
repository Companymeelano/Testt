package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** One-screen sales dashboard: KPIs, target gauge, 3D bars and donuts, built on existing reports. */
public class AnalyticsScreen extends Screen {
    private final Filter filter = new Filter();

    public AnalyticsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "analytics"; }

    @Override
    public String title() { return "داشبورد تحلیلی"; }

    @Override
    public String glyph() { return "◈"; }

    @Override
    public int accent() { return Theme.VIOLET; }

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
        List<Row> status = new ArrayList<>();
        List<Row> visitors = new ArrayList<>();
        List<Row> groups = new ArrayList<>();
        List<Row> returns = new ArrayList<>();
        List<Row> aging = new ArrayList<>();
        List<Row> perf = new ArrayList<>();
        java.util.Map<String, String> notes = new java.util.LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال ساخت داشبورد…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            Row s = soft(d.notes, "فروش", () -> Repo.one(c, Queries.factorSummary(m, true, f)));
            if (s != null) d.sales = s;
            List<Row> st = soft(d.notes, "وضعیت فروش", () -> Repo.exec(c, Queries.factorStatusSummary(m, true, f.from, f.to)));
            if (st != null) d.status = st;
            List<Row> vis = soft(d.notes, "ویزیتور", () -> Repo.exec(c, Queries.salesByVisitor(m, f, 8)));
            if (vis != null) d.visitors = vis;
            List<Row> grp = soft(d.notes, "گروه کالا", () -> Repo.exec(c, Queries.factorByProductGroup(m, true, f, 8)));
            if (grp != null) d.groups = grp;
            List<Row> ret = soft(d.notes, "برگشتی", () -> Repo.exec(c, MasterQueries.returnsByKind(m, f)));
            if (ret != null) d.returns = ret;
            List<Row> ag = soft(d.notes, "سن بدهی", () -> Repo.exec(c, MasterQueries.agingBuckets(m)));
            if (ag != null) d.aging = ag;
            List<Row> pf = soft(d.notes, "هدف", () -> Repo.exec(c, MasterQueries.visitorsPerf(m, f)));
            if (pf != null) d.perf = pf;
            if (s == null && pf == null) throw new Exception(firstNote(d.notes));
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

    private String firstNote(java.util.Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        double total = d.sales.d("total");
        double paid = d.sales.d("paid");
        double remain = d.sales.d("remain");
        long docs = d.sales.l("docs");

        double retTotal = 0;
        long retDocs = 0;
        for (Row r : d.returns) {
            retTotal += r.d("total");
            retDocs += r.l("docs");
        }
        double voidTotal = 0;
        long voidDocs = 0;
        double okTotal = 0;
        for (Row r : d.status) {
            if (r.l("state") == 1) okTotal += r.d("total");
            else {
                voidTotal += r.d("total");
                voidDocs += r.l("docs");
            }
        }
        double goals = 0, perfSales = 0;
        for (Row r : d.perf) {
            goals += r.d("goals");
            perfSales += r.d("sales");
        }
        double frac = goals > 0 ? perfSales / goals : 0;

        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("فروش خالص", Money.compactRial(total), Money.fa(String.valueOf(docs)) + " فاکتور", Theme.VIOLET));
        kpis.add(new Kit.Kpi("وصول شده", Money.compactRial(paid), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("مانده فروش", Money.compactRial(remain), "", Theme.WARNING));
        kpis.add(new Kit.Kpi("برگشتی", Money.compactRial(retTotal), Money.fa(String.valueOf(retDocs)) + " سند", Theme.DANGER));
        kpis.add(new Kit.Kpi("تأییدنشده / ابطالی", Money.fa(String.valueOf(voidDocs)) + " فاکتور",
                Money.compactRial(voidTotal), Theme.GOLD));
        kpis.add(new Kit.Kpi("تحقق هدف", goals > 0 ? Money.pct(frac * 100) : "—", "", Theme.INFO));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- target gauge ----
        LinearLayout tg = a.kit.card(Theme.GOLD);
        tg.addView(a.kit.text("تحقق هدف فروش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        tg.addView(a.kit.hint(goals > 0
                ? "فروش " + Money.compactRial(perfSales) + " از هدف " + Money.compactRial(goals)
                : "برای این بازه هدفی تعریف نشده است"), a.kit.lp(-1, -2));
        Charts.Gauge gauge = new Charts.Gauge(a);
        gauge.set(frac, goals > 0 ? Money.pct(frac * 100) : "—",
                frac >= 1 ? Theme.SUCCESS : Theme.GOLD, "تحقق هدف");
        tg.addView(gauge, a.kit.lp(-1, Theme.dp(170)));
        a.kit.addCard(content, tg);

        // ---- sales by visitor (3D bars) ----
        if (!d.visitors.isEmpty()) {
            LinearLayout vc = a.kit.card(accent());
            vc.addView(a.kit.text("فروش به تفکیک ویزیتور", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Bars bars = new Charts.Bars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(8, d.visitors.size()); i++) {
                Row r = d.visitors.get(i);
                pts.add(new Charts.Point(r.s("label"), r.d("total")));
            }
            bars.setData(pts, Charts.COMPACT);
            vc.addView(bars, a.kit.lp(-1, Theme.dp(220)));
            a.kit.addCard(content, vc);
        }

        // ---- product groups + confirmation status (donuts) ----
        if (!d.groups.isEmpty()) {
            LinearLayout gc = a.kit.card(Theme.INFO);
            gc.addView(a.kit.text("سهم گروه‌های کالا از فروش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Donut donut = new Charts.Donut(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(6, d.groups.size()); i++) {
                Row r = d.groups.get(i);
                pts.add(new Charts.Point(r.s("label"), r.d("total")));
            }
            donut.setData(pts, "گروه کالا", Money.compactRial(total));
            gc.addView(donut, a.kit.lp(-1, Theme.dp(230)));
            a.kit.addCard(content, gc);
        }

        if (okTotal + voidTotal > 0) {
            LinearLayout sc = a.kit.card(Theme.DANGER);
            sc.addView(a.kit.text("تأییدشده در برابر تأییدنشده / ابطالی", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            sc.addView(a.kit.hint("فاکتورهای ابطالی یا تأییدنشده در این بازه: "
                    + Money.fa(String.valueOf(voidDocs)) + " مورد به ارزش " + Money.compactRial(voidTotal)), a.kit.lp(-1, -2));
            Charts.Donut sd = new Charts.Donut(a);
            List<Charts.Point> pts = new ArrayList<>();
            pts.add(new Charts.Point("تأییدشده", okTotal, Theme.SUCCESS));
            pts.add(new Charts.Point("تأییدنشده / ابطالی", voidTotal, Theme.DANGER));
            sd.setData(pts, "وضعیت", Money.compactRial(okTotal + voidTotal));
            sc.addView(sd, a.kit.lp(-1, Theme.dp(220)));
            a.kit.addCard(content, sc);
        }

        // ---- returns by kind ----
        if (!d.returns.isEmpty()) {
            LinearLayout rc = a.kit.card(Theme.DANGER);
            rc.addView(a.kit.text("برگشتی‌ها بر اساس نوع", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(6, d.returns.size()); i++) {
                Row r = d.returns.get(i);
                pts.add(new Charts.Point(r.s("label"), r.d("total")));
            }
            hb.setData(pts, Charts.COMPACT);
            rc.addView(hb, a.kit.lp(-1, -2));
            a.kit.addCard(content, rc);
        }

        // ---- debt aging ----
        if (!d.aging.isEmpty()) {
            LinearLayout ac = a.kit.card(Theme.WARNING);
            ac.addView(a.kit.text("سن بدهی فروش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Bars ab = new Charts.Bars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : d.aging) pts.add(new Charts.Point(r.s("bucket"), r.d("amount")));
            ab.setData(pts, Charts.COMPACT);
            ac.addView(ab, a.kit.lp(-1, Theme.dp(200)));
            a.kit.addCard(content, ac);
        }

        content.addView(a.kit.btnGhost("🧾  گزارش‌های کامل فروش", Theme.GOLD, v -> a.nav("sales")), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(a.kit.btnGhost("📊  همه گزارش‌ها", Theme.VIOLET, v -> a.nav("reports")), a.kit.lp(-1, -2));

        renderNotes(content, d.notes);
    }
}
