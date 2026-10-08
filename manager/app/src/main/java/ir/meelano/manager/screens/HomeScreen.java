package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executive dashboard: greeting, alerts, day KPIs, trends, debtors, visitors, shortcuts. */
public class HomeScreen extends Screen {
    public HomeScreen(ir.meelano.manager.MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "home"; }

    @Override
    public String title() { return "خانه"; }

    @Override
    public String glyph() { return "⌂"; }

    @Override
    public int accent() { return Theme.GOLD; }

    private static final class Data {
        Row salesDay = new Row();
        Row buyDay = new Row();
        Row inDay = new Row();
        Row outDay = new Row();
        Row custSum = new Row();
        List<Row> debtors = new ArrayList<>();
        List<Row> overdue = new ArrayList<>();
        List<Row> salesDaily = new ArrayList<>();
        List<Row> inDaily = new ArrayList<>();
        List<Row> visitors = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت داشبورد مدیریتی…"), a.kit.lp(-1, -2));

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.salesDay = soft(d.notes, "فروش روز", () -> Repo.one(c, Queries.homeSalesDay(m)));
            d.buyDay = soft(d.notes, "خرید روز", () -> Repo.one(c, Queries.homeBuyDay(m)));
            d.inDay = soft(d.notes, "دریافت روز", () -> Repo.one(c, MoneyQueries.darDay(m, 0)));
            d.outDay = soft(d.notes, "پرداخت روز", () -> Repo.one(c, MoneyQueries.darDay(m, 1)));
            d.custSum = soft(d.notes, "خلاصه مشتریان", () -> Repo.one(c, MasterQueries.customersSummary(m)));
            d.debtors = soft(d.notes, "بدهکاران", () -> Repo.exec(c, Queries.topDebtors(m, 5)));
            d.overdue = soft(d.notes, "معوق‌ها", () -> Repo.exec(c, Queries.overdueInvoices(m, 5)));
            final String to = Jalali.todayStr();
            final String from = Jalali.addDays(to, -13);
            d.salesDaily = soft(d.notes, "روند فروش", () -> Repo.exec(c, Queries.factorDaily(m, true, from, to)));
            d.inDaily = soft(d.notes, "روند دریافت", () -> Repo.exec(c, MoneyQueries.darDaily(m, 0, from, to)));
            Filter vf = new Filter();
            vf.preset = Filter.P_LAST30;
            vf.applyPreset();
            d.visitors = soft(d.notes, "ویزیتورها", () -> Repo.exec(c, MasterQueries.visitorsPerf(m, vf)));
            d.dueIn = soft(d.notes, "سررسید دریافتی", () -> Repo.exec(c, MoneyQueries.chequeDue(m, true, 3)));
            d.dueOut = soft(d.notes, "سررسید پرداختی", () -> Repo.exec(c, MoneyQueries.chequeDue(m, false, 3)));
            if (d.salesDay == null && d.inDay == null && d.custSum == null && d.buyDay == null && d.outDay == null)
                throw new Exception(firstNote(d.notes));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                saveCache(d);
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                String[] ts = {""};
                Data cached = loadCached(ts);
                if (cached != null) {
                    build(content, cached);
                    LinearLayout bc = a.kit.card(Theme.WARNING);
                    bc.addView(a.kit.text("📴 حالت آفلاین — آخرین داده ذخیره‌شده", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
                    if (!ts[0].isEmpty())
                        bc.addView(a.kit.kv("آخرین به‌روزرسانی", Money.fa(ts[0]), Theme.WARNING), a.kit.lp(-1, -2));
                    bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
                    bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
                    content.addView(bc, 0);
                    content.addView(a.kit.gap(10), 1);
                    return;
                }
                content.removeAllViews();
                content.addView(greetingHero(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(12));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    // ---------------- offline cache (summary rows + timestamp) ----------------
    private void saveCache(Data d) {
        try {
            Calendar c = Calendar.getInstance();
            String hm = String.format(java.util.Locale.US, "%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            String s = "ts=" + esc(a.kit.todayLine() + " ساعت " + hm)
                    + "|sales=" + rowStr(d.salesDay)
                    + "|buy=" + rowStr(d.buyDay)
                    + "|in=" + rowStr(d.inDay)
                    + "|out=" + rowStr(d.outDay)
                    + "|cust=" + rowStr(d.custSum);
            a.settings.saveHomeCache(s);
            pokeWidget();
        } catch (Exception ignored) { }
    }

    /** Ask the home-screen widget to re-read the fresh cache. */
    private void pokeWidget() {
        try {
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(a);
            android.content.ComponentName cn = new android.content.ComponentName(a,
                    ir.meelano.manager.widget.CashWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                android.content.Intent in = new android.content.Intent(a,
                        ir.meelano.manager.widget.CashWidget.class);
                in.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                in.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                a.sendBroadcast(in);
            }
        } catch (Exception ignored) { }
    }

    private Data loadCached(String[] tsOut) {
        try {
            String s = a.settings.homeCache();
            if (s == null || s.isEmpty()) return null;
            Data d = new Data();
            boolean any = false;
            for (String part : s.split("\\|")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;
                String k = part.substring(0, eq);
                String v = part.substring(eq + 1);
                if ("ts".equals(k)) tsOut[0] = unesc(v);
                else if ("sales".equals(k)) { d.salesDay = rowFrom(v); any = true; }
                else if ("buy".equals(k)) { d.buyDay = rowFrom(v); any = true; }
                else if ("in".equals(k)) { d.inDay = rowFrom(v); any = true; }
                else if ("out".equals(k)) { d.outDay = rowFrom(v); any = true; }
                else if ("cust".equals(k)) { d.custSum = rowFrom(v); any = true; }
            }
            return any ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String rowStr(Row r) {
        if (r == null) return "";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (b.length() > 0) b.append(';');
            b.append(e.getKey()).append('=').append(esc(String.valueOf(e.getValue())));
        }
        return b.toString();
    }

    private static Row rowFrom(String s) {
        Row r = new Row();
        if (s == null || s.isEmpty()) return r;
        for (String p : s.split(";")) {
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            r.put(p.substring(0, eq), unesc(p.substring(eq + 1)));
        }
        return r;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D").replace("|", "%7C");
    }

    private static String unesc(String s) {
        return s.replace("%7C", "|").replace("%3D", "=").replace("%3B", ";").replace("%25", "%");
    }

    private String firstNote(Map<String, String> notes) {
        for (String v : notes.values()) return v;
        return "داده‌ای دریافت نشد";
    }

    private View greetingHero() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = hour < 5 ? "شب بخیر" : (hour < 12 ? "صبح بخیر" : (hour < 15 ? "روز بخیر" : (hour < 19 ? "عصر بخیر" : "شب بخیر")));
        return a.kit.hero("♛", g + " مدیر", a.kit.todayLine() + " • نمای زنده آتیران", Theme.GOLD);
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- day KPIs (tap a tile → that day's detail list) ----
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("فروش " + dayOf(d.salesDay), Money.compactRial(d.salesDay.d("total")),
                Money.fa(String.valueOf(d.salesDay.l("docs"))) + " فاکتور", Theme.GOLD,
                () -> gotoDay("sales", d.salesDay)));
        kpis.add(new Kit.Kpi("خرید " + dayOf(d.buyDay), Money.compactRial(d.buyDay.d("total")),
                Money.fa(String.valueOf(d.buyDay.l("docs"))) + " فاکتور", Theme.INFO,
                () -> gotoDay("buy", d.buyDay)));
        kpis.add(new Kit.Kpi("دریافت " + dayOf(d.inDay), Money.compactRial(d.inDay.d("total")),
                Money.fa(String.valueOf(d.inDay.l("count"))) + " قبض", Theme.SUCCESS,
                () -> gotoDay("dar_in", d.inDay)));
        kpis.add(new Kit.Kpi("پرداخت " + dayOf(d.outDay), Money.compactRial(d.outDay.d("total")),
                Money.fa(String.valueOf(d.outDay.l("count"))) + " قبض", Theme.WARNING,
                () -> gotoDay("dar_out", d.outDay)));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        // ---- alerts ----
        LinearLayout alerts = a.kit.card(Theme.DANGER);
        alerts.addView(a.kit.text("هشدار امروز", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        int n = 0;
        n += alert(alerts, d, n);
        if (n == 0) alerts.addView(a.kit.text("فعلاً هشدار مهمی وجود ندارد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, alerts);

        // ---- 14-day sales trend ----
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.GOLD);
            c.addView(a.kit.sectionHead("روند ۱۴ روزه فروش", "فروش ›", v -> a.nav("sales")), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = filled(d.salesDaily);
            final TextView cap = a.kit.text(pts.isEmpty() ? "" : lastCap(pts), 11f, Theme.MUTED, false);
            ch.setData(pts, Theme.GOLD, Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.rial(p.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(190)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        // ---- receipts mix donut ----
        if (d.inDay != null && d.inDay.d("total") > 0) {
            LinearLayout c = a.kit.card(Theme.SUCCESS);
            c.addView(a.kit.sectionHead("ترکیب دریافت " + dayOf(d.inDay), "دریافت‌ها ›", v -> a.nav("dar_in")), a.kit.lp(-1, -2));
            Charts.Donut dn = new Charts.Donut(a);
            List<Charts.Point> pts = new ArrayList<>();
            pts.add(new Charts.Point("نقد", d.inDay.d("cash"), Theme.SUCCESS));
            pts.add(new Charts.Point("کارت", d.inDay.d("pos"), Theme.INFO));
            pts.add(new Charts.Point("حواله", d.inDay.d("havaleh"), Theme.VIOLET));
            pts.add(new Charts.Point("چک", d.inDay.d("cheque"), Theme.GOLD));
            dn.setData(pts, "جمع دریافت", Money.compactRial(d.inDay.d("total")));
            c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
            a.kit.addCard(content, c);
        }

        // ---- debtors ----
        if (d.debtors != null && !d.debtors.isEmpty()) {
            content.addView(a.kit.sectionHead("بدهکاران اولویت‌دار", "مشتریان ›", v -> gotoDebtors()), a.kit.lp(-1, -2));
            for (Row r : d.debtors) {
                final String code = r.s("code");
                View row = a.kit.personRow(r.s("party"), "کد " + Money.fa(code), Money.rial(r.d("amount")),
                        "مانده بدهی", Theme.DANGER, v -> gotoCustomer(code));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(row, p);
            }
        }

        // ---- visitors ----
        if (d.visitors != null && !d.visitors.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.WARNING);
            c.addView(a.kit.sectionHead("عملکرد ۳۰ روزه ویزیتورها", "ویزیتورها ›", v -> a.nav("visitors")), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(6, d.visitors.size()); i++) {
                Row r = d.visitors.get(i);
                pts.add(new Charts.Point(r.s("name"), r.d("sales")));
            }
            hb.setData(pts, Charts.COMPACT);
            c.addView(hb, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = a.kit.card(Theme.INFO);
            c.addView(a.kit.sectionHead("سررسید ۳ روز آینده", "چک‌ها ›", v -> a.nav("cheques")), a.kit.lp(-1, -2));
            if (d.dueIn != null && !d.dueIn.isEmpty())
                c.addView(a.kit.kv("دریافتی (" + Money.fa(String.valueOf(d.dueIn.size())) + " فقره)", Money.rial(dueInSum), Theme.SUCCESS), a.kit.lp(-1, -2));
            if (d.dueOut != null && !d.dueOut.isEmpty())
                c.addView(a.kit.kv("پرداختی (" + Money.fa(String.valueOf(d.dueOut.size())) + " فقره)", Money.rial(dueOutSum), Theme.DANGER), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        // ---- shortcuts (glyph/title/accent always mirror the destination screen) ----
        content.addView(a.kit.sectionHead("دسترسی سریع", null, null), a.kit.lp(-1, -2));
        String[][] links = {
                {"sales", "فاکتورها، معوق‌ها و تحلیل فروش"},
                {"cheques", "دریافتی، پرداختی و سررسیدها"},
                {"customers", "پرونده کامل و گردش حساب"},
                {"profit", "حاشیه سود و هزینه‌ها"},
                {"reports", "فهرست کامل گزارش‌های مدیریتی"},
        };
        for (int i = 0; i < links.length; i++) {
            final String id = links[i][0];
            Screen s = a.screen(id);
            if (s == null) continue;
            View r = a.kit.navRow(s.glyph(), s.title(), links[i][1], s.accent(), v -> a.nav(id));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }

        renderNotes(content, d.notes);
    }

    private int alert(LinearLayout box, Data d, int n) {
        // overdue top-1
        if (d.overdue != null && !d.overdue.isEmpty()) {
            Row o = d.overdue.get(0);
            long dd = o.l("days");
            // Without dif_date_alan the server sends −1: derive the delay from the due date.
            if (dd < 0) dd = Jalali.diffDays(o.s("dueDate"), Jalali.todayStr());
            String days = dd >= 0 ? " • تأخیر " + Money.fa(String.valueOf(dd)) + " روز" : "";
            box.addView(a.kit.alertRow("فاکتور معوق", o.s("party") + " • " + Money.rial(o.d("amount")) + days,
                    Theme.DANGER, v -> gotoUnsettled()), padTop(n));
            n++;
        }
        // top debtor
        if (d.debtors != null && !d.debtors.isEmpty()) {
            Row r = d.debtors.get(0);
            box.addView(a.kit.alertRow("بدهکار اولویت‌دار", r.s("party") + " • مانده " + Money.rial(r.d("amount")),
                    Theme.WARNING, v -> gotoCustomer(r.s("code"))), padTop(n));
            n++;
        }
        // liquidity
        if (d.salesDay != null && d.buyDay != null && d.salesDay.d("total") > 0 && d.buyDay.d("total") > d.salesDay.d("total") * 1.15) {
            box.addView(a.kit.alertRow("فشار نقدینگی", "خرید روز از فروش جلو زده؛ پرداخت‌ها را کنترل کنید.",
                    Theme.WARNING, v -> a.nav("buy")), padTop(n));
            n++;
        }
        // due cheques
        int dueN = (d.dueIn == null ? 0 : d.dueIn.size()) + (d.dueOut == null ? 0 : d.dueOut.size());
        if (dueN > 0) {
            box.addView(a.kit.alertRow("سررسید چک", Money.fa(String.valueOf(dueN)) + " فقره چک در ۳ روز آینده سررسید می‌شود.",
                    Theme.INFO, v -> a.nav("cheques")), padTop(n));
            n++;
        }
        return n;
    }

    private LinearLayout.LayoutParams padTop(int n) {
        LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(8), 0, 0);
        return p;
    }

    private String dayOf(Row day) {
        if (day == null) return "";
        String d = Jalali.disp(day.s("d"));
        if (d.isEmpty()) return "";
        String today = Jalali.todayStr();
        if (d.equals(today)) return "امروز";
        if (d.equals(Jalali.addDays(today, -1))) return "دیروز";
        return Jalali.shortLabel(d);
    }

    /** Open a section narrowed to the KPI day (custom 1-day range). */
    private void gotoDay(String target, Row day) {
        String d = day == null ? "" : Jalali.disp(day.s("d"));
        Screen s = a.screen(target);
        if (s != null && s.filter() != null && !d.isEmpty()) {
            Filter nf = s.filter().copy();
            nf.preset = Filter.P_CUSTOM;
            nf.from = d;
            nf.to = d;
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav(target);
    }

    private double sum(List<Row> rows) {
        double s = 0;
        if (rows != null) for (Row r : rows) s += r.d("amount");
        return s;
    }

    /** Fill missing days with 0 for a smooth 14-day chart. */
    private List<Charts.Point> filled(List<Row> rows) {
        Map<String, Double> map = new HashMap<>();
        if (rows != null) for (Row r : rows) {
            String k = Jalali.disp(r.s("day"));
            if (!k.isEmpty()) map.put(k, r.d("total"));
        }
        List<Charts.Point> out = new ArrayList<>();
        String to = Jalali.todayStr();
        String from = Jalali.addDays(to, -13);
        int d0 = Jalali.parse(from);
        for (int i = 0; i < 14 && d0 >= 0; i++) {
            String day = Jalali.format(d0 + i);
            Double v = map.get(day);
            out.add(new Charts.Point(Jalali.shortLabel(day), v == null ? 0 : v));
        }
        return out;
    }

    private String lastCap(List<Charts.Point> pts) {
        Charts.Point p = pts.get(pts.size() - 1);
        return Money.fa(p.label) + " • " + Money.rial(p.value);
    }

    private void gotoDebtors() {
        Screen s = a.screen("customers");
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.status = "debt";
            nf.sort = "debt";
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav("customers");
    }

    private void gotoUnsettled() {
        Screen s = a.screen("sales");
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.status = "unsettled";
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav("sales");
    }

    private void gotoCustomer(String code) {
        Screen s = a.screen("customers");
        if (s instanceof CustomersScreen) ((CustomersScreen) s).openDossier(code);
        a.nav("customers");
    }
}
