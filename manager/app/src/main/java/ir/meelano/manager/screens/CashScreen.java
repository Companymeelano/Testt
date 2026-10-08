package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Liquidity forecast: incoming vs outgoing cheques due per day. */
public class CashScreen extends Screen {
    private static final int[] HORIZONS = {7, 14, 30, 60};
    private int horizonIdx = 2;

    public CashScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "cash"; }

    @Override
    public String title() { return "نقدینگی"; }

    @Override
    public String glyph() { return "💧"; }

    @Override
    public int accent() { return Theme.TEAL; }

    @Override
    public String subtitle() {
        return "پیش‌بینی " + Money.fa(String.valueOf(HORIZONS[horizonIdx])) + " روز آینده";
    }

    private static final class Data {
        List<Row> inDaily = new ArrayList<>();
        List<Row> outDaily = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        String[] names = new String[HORIZONS.length];
        for (int i = 0; i < HORIZONS.length; i++) names[i] = Money.fa(String.valueOf(HORIZONS[i])) + " روزه";
        content.addView(a.kit.chips(names, horizonIdx, idx -> {
            horizonIdx = idx;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.loading("در حال محاسبه پیش‌بینی نقدینگی…"), a.kit.lp(-1, -2));
        final int h = HORIZONS[horizonIdx];

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.inDaily = soft(d.notes, "سررسیدهای دریافتی", () -> Repo.exec(c, MoneyQueries.chequeDueDaily(m, true, h)));
            d.outDaily = soft(d.notes, "سررسیدهای پرداختی", () -> Repo.exec(c, MoneyQueries.chequeDueDaily(m, false, h)));
            if (d.inDaily == null && d.outDaily == null) {
                String first = "داده‌ای دریافت نشد";
                for (String v : d.notes.values()) { first = v; break; }
                throw new Exception(first);
            }
            if (d.inDaily == null) d.inDaily = new ArrayList<>();
            if (d.outDaily == null) d.outDaily = new ArrayList<>();
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

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        String[] names = new String[HORIZONS.length];
        for (int i = 0; i < HORIZONS.length; i++) names[i] = Money.fa(String.valueOf(HORIZONS[i])) + " روزه";
        content.addView(a.kit.chips(names, horizonIdx, idx -> {
            horizonIdx = idx;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        renderNotes(content, d.notes);

        // ---- merge days ----
        TreeMap<String, double[]> days = new TreeMap<>();
        for (Row r : d.inDaily) {
            double[] v = dayOf(days, r.s("day"));
            v[0] += r.d("n");
            v[1] += r.d("total");
        }
        for (Row r : d.outDaily) {
            double[] v = dayOf(days, r.s("day"));
            v[2] += r.d("n");
            v[3] += r.d("total");
        }
        double inN = 0, inT = 0, outN = 0, outT = 0;
        for (double[] v : days.values()) {
            inN += v[0]; inT += v[1]; outN += v[2]; outT += v[3];
        }
        double net = inT - outT;

        // ---- KPIs ----
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("ورودی چک‌ها", inT, Money.compactRial(inT),
                Money.fa(String.valueOf(Math.round(inN))) + " فقره", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("خروجی چک‌ها", outT, Money.compactRial(outT),
                Money.fa(String.valueOf(Math.round(outN))) + " فقره", Theme.WARNING));
        kpis.add(new Kit.Kpi("خالص دوره", net, Money.compactRial(net),
                net >= 0 ? "مازاد نقدینگی" : "کسری نقدینگی",
                net >= 0 ? Theme.SUCCESS : Theme.DANGER));
        String cover = outT > 0 ? Money.pct(inT * 100.0 / outT) : "—";
        kpis.add(new Kit.Kpi("نسبت پوشش", Money.fa(cover), "ورودی به خروجی", Theme.INFO));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- daily net chart ----
        final List<Charts.Point> pts = new ArrayList<>();
        for (Map.Entry<String, double[]> e : days.entrySet()) {
            double nv = e.getValue()[1] - e.getValue()[3];
            pts.add(new Charts.Point(Jalali.shortLabel(Jalali.faDate(e.getKey())), nv,
                    nv >= 0 ? Theme.SUCCESS : Theme.DANGER));
        }
        LinearLayout chart = a.kit.card(accent());
        chart.addView(a.kit.chartHead("خالص روزانه (ورودی − خروجی)", () -> {
            Charts.Bars b = new Charts.Bars(a);
            b.setData(pts, Charts.COMPACT);
            return b;
        }), a.kit.lp(-1, -2));
        Charts.Bars bars = new Charts.Bars(a);
        bars.setMinimumHeight(Theme.dp(200));
        bars.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(200)));
        bars.setData(pts, Charts.COMPACT);
        chart.addView(bars, a.kit.lp(-1, Theme.dp(200)));
        a.kit.addCard(content, chart);
        content.addView(a.kit.gap(12));

        // ---- daily table ----
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : days.entrySet()) {
            double[] v = e.getValue();
            Row r = new Row();
            r.put("day", e.getKey());
            r.put("inN", v[0]);
            r.put("inT", v[1]);
            r.put("outN", v[2]);
            r.put("outT", v[3]);
            r.put("net", v[1] - v[3]);
            rows.add(r);
        }
        ReportCatalog.Col[] cols = {
                new ReportCatalog.Col("day", "تاریخ", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("inN", "تعداد ورودی", ReportCatalog.T_NUM),
                new ReportCatalog.Col("inT", "مبلغ ورودی", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("outN", "تعداد خروجی", ReportCatalog.T_NUM),
                new ReportCatalog.Col("outT", "مبلغ خروجی", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("net", "خالص", ReportCatalog.T_MONEY),
        };
        LinearLayout tc = a.kit.card(accent());
        tc.addView(a.kit.title("جدول روزانه"), a.kit.lp(-1, -2));
        tc.addView(a.kit.dataTable(cols, rows, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, tc);
        View note = a.kit.hint("فقط چک‌های پاس‌نشده و برگشت‌نخورده در بازه لحاظ شده‌اند.");
        content.addView(note, a.kit.lp(-1, -2));
    }

    private static double[] dayOf(TreeMap<String, double[]> days, String day) {
        String k = day == null ? "" : day.trim();
        double[] v = days.get(k);
        if (v == null) {
            v = new double[4];
            days.put(k, v);
        }
        return v;
    }
}
