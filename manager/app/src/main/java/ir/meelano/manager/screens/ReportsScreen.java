package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Reports center: 44 management reports with charts, tables and PDF sharing. */
public class ReportsScreen extends Screen {
    private final Filter filter = new Filter();
    private ReportCatalog.Spec sel;
    private LinearLayout contentRef;

    public ReportsScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "reports"; }

    @Override
    public String title() { return "گزارشات"; }

    @Override
    public String glyph() { return "▤"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        if (sel == null) return null;
        boolean search = "fleeing_customers".equals(sel.id) || "lost_basket".equals(sel.id)
                || "cheque_reliability".equals(sel.id) || "stock_forecast".equals(sel.id)
                || "order_suggest".equals(sel.id) || "invoice_profit".equals(sel.id);
        if (!sel.needsRange && !search) return null;
        FilterSheet.Config c = new FilterSheet.Config();
        c.search = search;
        c.searchHint = "جستجوی مشتری / کالا / فاکتور…";
        c.range = sel.needsRange;
        return c;
    }

    @Override
    public boolean onBack() {
        if (sel == null) return false;
        sel = null;
        a.refreshChrome();
        if (contentRef != null) render(contentRef);
        return true;
    }

    private int sectionAccent(String section) {
        if ("فروش".equals(section)) return Theme.GOLD;
        if ("خرید".equals(section)) return Theme.INFO;
        if ("دریافت و پرداخت".equals(section)) return Theme.SUCCESS;
        if ("چک‌ها".equals(section)) return Theme.INFO;
        if ("کالا و انبار".equals(section)) return Theme.VIOLET;
        if ("مشتریان".equals(section)) return Theme.SUCCESS;
        if ("ویزیتورها".equals(section)) return Theme.WARNING;
        if ("سود و زیان".equals(section)) return Theme.GOLD;
        if ("بانک و صندوق".equals(section)) return Theme.INFO;
        if ("کاربران و نظارت".equals(section)) return Theme.STEEL;
        if ("هوشمند".equals(section)) return Theme.TEAL;
        return Theme.MUTED;
    }

    @Override
    public void render(final LinearLayout content) {
        contentRef = content;
        if (sel == null) renderList(content);
        else renderReport(content, sel);
    }

    private void renderList(LinearLayout content) {
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(), "فهرست کامل گزارش‌های مدیریتی آتیران", accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        String lastSection = "";
        for (final ReportCatalog.Spec s : ReportCatalog.ALL) {
            if (!s.section.equals(lastSection)) {
                lastSection = s.section;
                content.addView(a.kit.sectionHead(s.section, null, null), a.kit.lp(-1, -2));
            }
            View r = a.kit.navRow("▤", s.title, s.desc + (s.needsRange ? " • بازه‌دار" : ""), sectionAccent(s.section), v -> {
                sel = s;
                a.refreshChrome();
                render(contentRef);
            });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }
    }

    /** In-content filter bar under the report hero (v12: filters live inside each report). */
    private void filterBarInto(LinearLayout content, ReportCatalog.Spec spec) {
        if (!spec.needsRange || filter() == null) return;
        Filter f = filter();
        content.addView(a.kit.filterBar(f.describeFa(), !f.isDefault(), v -> a.openFilter()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    private void renderReport(final LinearLayout content, final ReportCatalog.Spec spec) {
        content.removeAllViews();
        content.addView(a.kit.btnGhost("‹ بازگشت به فهرست", Theme.GOLD, v -> onBack()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hero("▤", spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, sectionAccent(spec.section)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        filterBarInto(content, spec);
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال تهیه گزارش…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<Row> rows = Repo.exec(c, ReportCatalog.query(m, spec.id, f));
            if ("yoy_sales".equals(spec.id)) rows = mapYoY(rows);
            if ("aging".equals(spec.id)) mapAging(rows, m);
            if ("unsettled".equals(spec.id)) mapUnsettled(rows);
            if ("fleeing_customers".equals(spec.id)) mapFleeing(rows);
            return rows;
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("rows", rows);
                CacheStore.saveData(a, "cx_rep_" + spec.id, cacheNow(), sm);
                buildReport(content, spec, rows);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                java.util.Map<String, Object> cm = CacheStore.loadData(a, "cx_rep_" + spec.id, lab);
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
                content.addView(a.kit.error(msg, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    /** Without dif_date_alan the server sends days=−1: derive the delay from the due date. */
    private void mapUnsettled(List<Row> rows) {
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
    private void mapFleeing(List<Row> rows) {
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
    private void mapAging(List<Row> rows, Meta m) {
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

    /** Merge ~24 monthly rows into 12 month rows: this year vs last year + growth %. */
    private List<Row> mapYoY(List<Row> rows) {
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

    private String monthBucket(String month) {
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

        if (spec.chart != ReportCatalog.C_NONE && !spec.chartX.isEmpty() && !spec.chartY.isEmpty()) {
            LinearLayout c = a.kit.card(sectionAccent(spec.section));
            final List<Charts.Point> pts = new ArrayList<>();
            int cap = spec.chart == ReportCatalog.C_DONUT ? 8 : 31;
            for (int i = 0; i < Math.min(cap, rows.size()); i++) {
                Row r = rows.get(i);
                String x = chartLabel(spec, r.s(spec.chartX));
                if (spec.chart == ReportCatalog.C_DONUT)
                    pts.add(new Charts.Point(x, r.d(spec.chartY), Charts.palette(i)));
                else pts.add(new Charts.Point(x, r.d(spec.chartY)));
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
        c.addView(a.kit.btn("اشتراک PDF گزارش", v ->
                a.sharePdf(spec.title, spec.needsRange ? filter.rangeFa() : spec.desc, spec.cols, rows)), fp);
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
        } catch (Exception ignored) { }
        String t = Money.fa(x);
        return t.length() > 16 ? t.substring(0, 16) + "…" : t;
    }
}
