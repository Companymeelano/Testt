package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
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

/** Visitor performance: sales, collection, customers and goals. */
public class VisitorsScreen extends Screen {
    private final Filter filter = new Filter();

    public VisitorsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "visitors"; }

    @Override
    public String title() { return "ویزیتورها"; }

    @Override
    public String glyph() { return "♟"; }

    @Override
    public int accent() { return Theme.WARNING; }

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
        List<Row> perf = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت عملکرد ویزیتورها…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.perf = soft(d.notes, "عملکرد", () -> Repo.exec(c, MasterQueries.visitorsPerf(m, f)));
            if (d.perf == null) throw new Exception(firstNote(d.notes));
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
        content.addView(a.kit.gap(10));

        double sales = 0, collected = 0;
        long invoices = 0;
        if (d.perf != null) {
            for (Row r : d.perf) {
                sales += r.d("sales");
                collected += r.d("collected");
                invoices += r.l("invoices");
            }
        }
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("فروش بازه", Money.compact(sales), "", accent()));
        kpis.add(new Kit.Kpi("وصول فاکتور", Money.compact(collected), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("فاکتورها", Money.fa(String.valueOf(invoices)), "", Theme.INFO));
        kpis.add(new Kit.Kpi("ویزیتورها", Money.fa(String.valueOf(d.perf == null ? 0 : d.perf.size())), "نفر", Theme.VIOLET));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (d.perf == null || d.perf.isEmpty()) {
            content.addView(a.kit.empty("ویزیتوری یافت نشد", null), a.kit.lp(-1, -2));
            return;
        }

        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text("رتبه‌بندی فروش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(8, d.perf.size()); i++)
            pts.add(new Charts.Point(d.perf.get(i).s("name"), d.perf.get(i).d("sales")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        a.kit.addCard(content, c);

        for (Row r : d.perf) {
            final Row row = r;
            double goals = r.d("goals");
            String side = goals > 0 ? Money.pct(r.d("sales") * 100.0 / goals) : Money.compact(r.d("sales"));
            String pill = goals > 0 ? "تحقق هدف" : "فروش";
            boolean off = isOff(r.s("active"));
            View v = a.kit.personRow(row.s("name"),
                    "فروش " + Money.compact(row.d("sales")) + " • " + Money.fa(String.valueOf(row.l("invoices"))) + " فاکتور • " + Money.fa(String.valueOf(row.l("customers"))) + " مشتری"
                            + (off ? " • غیرفعال" : ""),
                    side, pill, goals > 0 && row.d("sales") >= goals ? Theme.SUCCESS : accent(),
                    v2 -> openDetail(row));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }

        renderNotes(content, d.notes);
    }

    private boolean isOff(String active) {
        if (active == null) return false;
        String t = active.trim().toUpperCase();
        return "F".equals(t) || "0".equals(t) || "FALSE".equals(t) || "N".equals(t);
    }

    private int parseId(String id) {
        try {
            return Integer.parseInt(Money.en(id).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------- detail ----------------
    private void openDetail(final Row head) {
        a.kit.toast("در حال دریافت کارنامه…");
        final Filter f = filter.copy();
        final int visId = parseId(head.s("id"));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<Row> trend = new ArrayList<>();
            List<Row> custs = new ArrayList<>();
            if (visId >= 0) {
                try {
                    trend = Repo.exec(c, MasterQueries.visitorTrend(m, visId, f));
                } catch (Exception ignored) { }
                try {
                    Filter cf = f.copy();
                    cf.top = 30;
                    custs = Repo.exec(c, MasterQueries.visitorCustomers(m, visId, cf));
                } catch (Exception ignored) { }
            }
            return new Detail(head, trend, custs);
        }, new Repo.Cb<Detail>() {
            @Override
            public void ok(Detail dt) {
                showDetail(dt, visId);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private static final class Detail {
        final Row head;
        final List<Row> trend;
        final List<Row> custs;

        Detail(Row head, List<Row> trend, List<Row> custs) {
            this.head = head;
            this.trend = trend;
            this.custs = custs;
        }
    }

    private void showDetail(Detail dt, final int visId) {
        Row h = dt.head;
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("ویزیتور", h.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("phone").isEmpty()) body.addView(a.kit.kv("تلفن", Money.fa(h.s("phone")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("فروش بازه", Money.rial(h.d("sales")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("فاکتورها", Money.fa(String.valueOf(h.l("invoices"))), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("وصول فاکتور", Money.rial(h.d("collected")), Theme.SUCCESS), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مشتریان فعال", Money.fa(String.valueOf(h.l("customers"))), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("کل مشتریان", Money.fa(String.valueOf(h.l("allCustomers"))), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.d("goals") > 0) {
            body.addView(a.kit.kv("هدف", Money.rial(h.d("goals")), Theme.TEXT), a.kit.lp(-1, -2));
            double ach = h.d("sales") / h.d("goals");
            body.addView(a.kit.progressLine("تحقق هدف", Money.pct(ach * 100), ach,
                    ach >= 1 ? Theme.SUCCESS : ach >= 0.7 ? Theme.WARNING : Theme.DANGER), a.kit.lp(-1, -2));
        }
        if (!dt.trend.isEmpty()) {
            body.addView(a.kit.text("روند فروش روزانه", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : dt.trend) pts.add(new Charts.Point(Jalali.shortLabel(r.s("day")), r.d("total")));
            final TextView cap = a.kit.text("", 11f, Theme.MUTED, false);
            ch.setData(pts, accent(), Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.rial(p.value)));
            body.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(160)));
            body.addView(cap, a.kit.lp(-1, -2));
        }
        if (!dt.custs.isEmpty()) {
            body.addView(a.kit.text("مشتریان این ویزیتور", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "مشتری", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("sales", "فروش", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("docs", "فاکتور", ReportCatalog.T_NUM),
            };
            body.addView(a.kit.dataTable(cols, dt.custs, null), a.kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        LinearLayout footer = a.kit.h();
        if (visId >= 0) {
            footer.addView(a.kit.btn("فاکتورها", v -> {
                dlg.dismiss();
                Screen s = a.screen("sales");
                Filter nf = s.filter().copy();
                nf.visitor = visId;
                nf.page = 0;
                s.applyFilter(nf);
                a.nav("sales");
            }), a.kit.wlp(1f));
            footer.addView(a.kit.space(8));
        }
        footer.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.wlp(1f));
        body.addView(a.kit.gap(8));
        body.addView(footer, a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }
}
