package ir.meelano.manager.screens;

import ir.meelano.manager.core.Diag;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.HomeNumberFormatter;
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
import java.util.Collections;
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
    public String glyph() { return Theme.isLight() ? "♟" : "♙"; }

    @Override
    public int accent() { return Theme.WARNING; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.search = true;
        c.searchHint = "نام ویزیتور…";
        c.range = true;
        // Smart segments: derived from the fetched performance rows, never from new SQL.
        c.statusTitle = "بخش هوشمند";
        c.status = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "همه"),
                new FilterSheet.Opt("active", "فعال در بازه"),
                new FilterSheet.Opt("off", "غیرفعال"),
                new FilterSheet.Opt("no_cust", "بدون مشتری منتسب"),
                new FilterSheet.Opt("goal", "تحقق‌کنندهٔ هدف"),
        };
        c.sortTitle = "مرتب‌سازی";
        c.sort = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "فروش"),
                new FilterSheet.Opt("invoices", "فاکتور"),
                new FilterSheet.Opt("customers", "مشتری منتسب"),
                new FilterSheet.Opt("achieve", "تحقق هدف"),
        };
        return c;
    }

    private static final String[][] SEGMENTS = {
            {"", "همه"}, {"active", "فعال در بازه"}, {"off", "غیرفعال"},
            {"no_cust", "بدون مشتری منتسب"}, {"goal", "تحقق‌کنندهٔ هدف"},
    };

    private static final ReportCatalog.Col[] VISITOR_COLS = new ReportCatalog.Col[]{
            new ReportCatalog.Col("name", "ویزیتور", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("customers", "منتسب", ReportCatalog.T_NUM),
            new ReportCatalog.Col("invoices", "فاکتور", ReportCatalog.T_NUM),
            new ReportCatalog.Col("sales", "فروش", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("collected", "وصول‌شده", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("goals", "هدف", ReportCatalog.T_MONEY),
    };

    /** Client-side smart filter: segment + search over the fetched rows (no new queries). */
    private List<Row> smartPerf(List<Row> src, Filter f) {
        List<Row> out = new ArrayList<>();
        if (src == null) return out;
        String seg = f == null || f.status == null ? "" : f.status;
        String q = f == null || f.search == null ? "" : f.search.trim();
        for (Row r : src) {
            if (!matchesSegment(r, seg)) continue;
            if (!q.isEmpty() && !r.s("name", "").contains(q)) continue;
            out.add(r);
        }
        String sort = f == null || f.sort == null ? "" : f.sort;
        Collections.sort(out, (x, y) -> {
            switch (sort) {
                case "invoices": return Long.compare(y.l("invoices"), x.l("invoices"));
                case "customers": return Long.compare(y.l("customers"), x.l("customers"));
                case "achieve": return Double.compare(achieve(y), achieve(x));
                default: return Double.compare(y.d("sales"), x.d("sales"));
            }
        });
        return out;
    }

    private static boolean matchesSegment(Row r, String seg) {
        if (seg == null || seg.isEmpty()) return true;
        boolean off = isOff(r.s("active"));
        if ("active".equals(seg)) return !off;
        if ("off".equals(seg)) return off;
        if ("no_cust".equals(seg)) return r.l("customers") <= 0;
        if ("goal".equals(seg)) return r.d("goals") > 0 && r.d("sales") >= r.d("goals");
        return true;
    }

    private static double achieve(Row r) {
        return r.d("goals") > 0 ? r.d("sales") / r.d("goals") : -1d;
    }

    private String segmentLabel() {
        String st = filter == null || filter.status == null ? "" : filter.status;
        for (String[] s : SEGMENTS) if (s[0].equals(st)) return s[1];
        return SEGMENTS[0][1];
    }

    private String sortLabel() {
        String so = filter == null || filter.sort == null ? "" : filter.sort;
        if ("invoices".equals(so)) return "فاکتور";
        if ("customers".equals(so)) return "مشتری منتسب";
        if ("achieve".equals(so)) return "تحقق هدف";
        return "فروش";
    }

    private String shareSubtitle() {
        String sub = "بخش: " + segmentLabel() + " • مرتب: " + sortLabel();
        if (filter != null && filter.search != null && !filter.search.trim().isEmpty())
            sub += " • جستجو: " + filter.search.trim();
        return sub;
    }

    private static final class Data {
        List<Row> perf = new ArrayList<>();
        List<Row> aging = new ArrayList<>();
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
            d.aging = soft(d.notes, "سن بدهی", () -> Repo.exec(c, MasterQueries.agingVisitors(m)));
            if (d.aging == null) d.aging = new ArrayList<>();
            if (d.perf == null) throw new Exception(firstNote(d.notes));
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
        m.put("perf", d.perf);
        CacheStore.saveData(a, "cx_vis", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_vis", lab);
        if (m == null) return null;
        Data d = new Data();
        d.perf = CacheStore.rows(m, "perf");
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        final List<Row> perf = smartPerf(d.perf, filter);

        double sales = 0, collected = 0;
        long invoices = 0;
        for (Row r : perf) {
            sales += r.d("sales");
            collected += r.d("collected");
            invoices += r.l("invoices");
        }
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("فروش بازه", Money.compactRial(sales), "", accent()));
        kpis.add(new Kit.Kpi("وصول فاکتور", Money.compactRial(collected), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("فاکتورها", Money.fa(String.valueOf(invoices)), "", Theme.INFO));
        kpis.add(new Kit.Kpi("ویزیتورها", Money.fa(String.valueOf(perf.size())), "نفر", Theme.VIOLET));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // One-tap smart segments with live counts, mirroring the filter sheet choices.
        String[] segNames = new String[SEGMENTS.length];
        int segSel = 0;
        String current = filter.status == null ? "" : filter.status;
        for (int i = 0; i < SEGMENTS.length; i++) {
            int n = 0;
            if (d.perf != null) for (Row r : d.perf) if (matchesSegment(r, SEGMENTS[i][0])) n++;
            segNames[i] = SEGMENTS[i][1] + " (" + Money.fa(String.valueOf(n)) + ")";
            if (SEGMENTS[i][0].equals(current)) segSel = i;
        }
        content.addView(a.kit.chips(segNames, segSel, idx -> {
            filter.status = SEGMENTS[idx][0];
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // Sharing follows the chosen smart filters: PDF, image and Excel of the same rows.
        if (!perf.isEmpty()) {
            final List<Row> shared = perf;
            LinearLayout shareRow = a.kit.h();
            shareRow.addView(a.kit.btnGhost("اشتراک PDF", accent(),
                    v -> a.sharePdf("بازده ویزیتور", shareSubtitle(), VISITOR_COLS, shared)), a.kit.wlp(1f));
            shareRow.addView(a.kit.space(8));
            shareRow.addView(a.kit.btnGhost("تصویر", accent(),
                    v -> a.sharePng("بازده ویزیتور", shareSubtitle(), VISITOR_COLS, shared)), a.kit.wlp(1f));
            shareRow.addView(a.kit.space(8));
            shareRow.addView(a.kit.btnGhost("اکسل", accent(),
                    v -> a.shareXlsx("بازده ویزیتور", shareSubtitle(), VISITOR_COLS, shared)), a.kit.wlp(1f));
            content.addView(shareRow, a.kit.lp(-1, -2));
            content.addView(a.kit.gap(12));
        }

        if (perf.isEmpty()) {
            content.addView(a.kit.empty("ویزیتوری یافت نشد",
                    "بخش هوشمند یا جستجو را تغییر دهید"), a.kit.lp(-1, -2));
            return;
        }

        if (d.aging != null && !d.aging.isEmpty()) {
            double over60 = 0, all = 0;
            for (Row r : d.aging) {
                all += r.d("total");
                over60 += r.d("b3") + r.d("b4") + r.d("b5");
            }
            LinearLayout ag = a.kit.card(Theme.DANGER);
            ag.addView(a.kit.text("سن بدهی ویزیتورها", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ag.addView(a.kit.hint("مانده فاکتورهای تسویه‌نشده به تفکیک ویزیتور. بیش از ۶۰ روز از سررسید گذشته: "
                    + Money.compactRial(over60) + " از " + Money.compactRial(all)), a.kit.lp(-1, -2));
            Charts.HBars agBars = new Charts.HBars(a);
            List<Charts.Point> agPts = new ArrayList<>();
            for (int i = 0; i < Math.min(8, d.aging.size()); i++)
                agPts.add(new Charts.Point(d.aging.get(i).s("visitor"), d.aging.get(i).d("total")));
            agBars.setData(agPts, Charts.COMPACT);
            ag.addView(agBars, a.kit.lp(-1, -2));
            a.kit.addCard(content, ag);
        }
        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.text("رتبه‌بندی فروش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.HBars hb = new Charts.HBars(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(8, perf.size()); i++)
            pts.add(new Charts.Point(perf.get(i).s("name"), perf.get(i).d("sales")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, a.kit.lp(-1, -2));
        a.kit.addCard(content, c);

        // ---- leaderboard: goal achievement first, then sales ----
        List<Row> rank = new ArrayList<>(perf);
        Collections.sort(rank, (x, y) -> {
            double ax = x.d("goals") > 0 ? x.d("sales") / x.d("goals") : -1;
            double ay = y.d("goals") > 0 ? y.d("sales") / y.d("goals") : -1;
            if (ax != ay) return Double.compare(ay, ax);
            return Double.compare(y.d("sales"), x.d("sales"));
        });
        LinearLayout lb = a.kit.card(Theme.GOLD);
        lb.addView(a.kit.text("جدول امتیاز ویزیتورها", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String[] medals = {"1", "2", "3"};
        for (int i = 0; i < Math.min(10, rank.size()); i++) {
            final Row rr = rank.get(i);
            String medal = i < 3 ? medals[i] : String.valueOf(i + 1);
            String ach = rr.d("goals") > 0 ? Money.pct(rr.d("sales") * 100.0 / rr.d("goals")) : "—";
            View rv = a.kit.personRow(Money.fa(medal) + " • " + rr.s("name"),
                    "فروش " + Money.compactRial(rr.d("sales")) + " • تحقق هدف " + ach,
                    Money.fa(String.valueOf(rr.l("invoices"))) + " فاکتور",
                    "رتبه " + Money.fa(String.valueOf(i + 1)),
                    i == 0 ? Theme.GOLD : accent(), v2 -> openDetail(rr));
            LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
            rp.setMargins(0, 0, 0, Theme.dp(8));
            lb.addView(rv, rp);
        }
        a.kit.addCard(content, lb);

        for (Row r : perf) {
            final Row row = r;
            double goals = r.d("goals");
            String side = goals > 0 ? Money.pct(r.d("sales") * 100.0 / goals) : Money.compactRial(r.d("sales"));
            String pill = goals > 0 ? "تحقق هدف" : "فروش";
            boolean off = isOff(r.s("active"));
            View v = a.kit.personRow(row.s("name"),
                    "فروش " + Money.compactRial(row.d("sales")) + " • " + Money.fa(String.valueOf(row.l("invoices"))) + " فاکتور • " + Money.fa(String.valueOf(row.l("customers"))) + " مشتری"
                            + (off ? " • غیرفعال" : ""),
                    side, pill, goals > 0 && row.d("sales") >= goals ? Theme.SUCCESS : accent(),
                    v2 -> openDetail(row));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }

        renderNotes(content, d.notes);
    }

    private static boolean isOff(String active) {
        if (active == null) return false;
        String t = active.trim().toUpperCase(java.util.Locale.US);
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
                } catch (Exception ignored) { Diag.ignored("VisitorsScreen:252", ignored); }
                try {
                    Filter cf = f.copy();
                    cf.top = 30;
                    custs = Repo.exec(c, MasterQueries.visitorCustomers(m, visId, cf));
                } catch (Exception ignored) { Diag.ignored("VisitorsScreen:257", ignored); }
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
            Charts.Gauge gg = new Charts.Gauge(a);
            gg.set(ach, Money.pct(ach * 100));
            body.addView(gg, new LinearLayout.LayoutParams(-1, Theme.dp(170)));
        }
        if (!dt.trend.isEmpty()) {
            body.addView(a.kit.text("روند فروش روزانه", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : dt.trend) pts.add(new Charts.Point(
                    HomeNumberFormatter.dateShortLabel(r.s("day")), r.d("total")));
            final TextView cap = a.kit.text("", 11f, Theme.MUTED, false);
            ch.setData(pts, accent(), Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(ir.meelano.manager.core.HomeNumberFormatter.rtlText(
                    Money.fa(p.label) + " • " + Money.compactRial(p.value))));
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
                if (s != null && s.filter() != null) {
                    Filter nf = s.filter().copy();
                    nf.visitor = visId;
                    nf.page = 0;
                    s.applyFilter(nf);
                }
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
