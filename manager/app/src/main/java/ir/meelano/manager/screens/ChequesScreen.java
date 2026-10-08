package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Received (getchk) and issued (putchk) cheques with status buckets and due tracking. */
public class ChequesScreen extends Screen {
    private boolean incoming = true;
    private String bucket = "";
    private boolean calMode;
    private int calY;
    private int calM;
    private final Filter filter = new Filter();

    public ChequesScreen(MainActivity a) {
        super(a);
        filter.top = 50;
    }

    @Override
    public String id() { return "cheques"; }

    @Override
    public String title() { return "چک‌ها"; }

    @Override
    public String glyph() { return "◉"; }

    @Override
    public int accent() { return Theme.INFO; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = "شماره، مشتری، شناسه صیادی…";
        c.range = true;
        c.banks = true;
        return c;
    }

    private String[][] buckets() {
        // Keys mirror AtiranSchema.chequeIn/OutBucket (codes validated against Atiran's procedures).
        if (incoming) return new String[][]{
                {"", "همه"}, {"sandogh", "صندوق"}, {"bank", "در بانک"}, {"vosool", "وصول‌شده"},
                {"kharj", "خرج‌شده"}, {"esterdad", "استرداد"}, {"bargashti", "برگشتی"},
                {"sayer", "سایر"}, {"due", "⌛ سررسید ۳۰ روز"}};
        return new String[][]{
                {"", "همه"}, {"jari", "جاری"}, {"pas", "پاس‌شده"}, {"sefid", "سفید"},
                {"sayer", "سایر"}, {"due", "⌛ سررسید ۳۰ روز"}};
    }

    private String bucketLabel(String b) {
        for (String[] x : buckets()) if (x[0].equals(b)) return x[1];
        return b;
    }

    /** Map a raw (st,back,hasBank) group row to our bucket key. */
    private String groupBucket(boolean inc, String st, String back) {
        return inc ? AtiranSchema.chequeInBucket(st, back) : AtiranSchema.chequeOutBucket(st);
    }

    /** Persian label for one cheque row (prefers the DB status label when present). */
    private String rowLabel(boolean inc, Row r) {
        if (!r.s("statusLabel").isEmpty()) return r.s("statusLabel");
        return inc ? AtiranSchema.chequeInStatusFa(r.s("st"), r.s("back"))
                : AtiranSchema.chequeOutStatusFa(r.s("st"));
    }

    private static final class Data {
        boolean snapIn;
        String snapBucket = "";
        List<Row> groups = new ArrayList<>();
        List<Row> list = new ArrayList<>();
        List<Row> cal = new ArrayList<>();
        boolean snapCal;
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("مبنای بازه تاریخی: تاریخ " + (incoming ? "دریافت" : "صدور") + " چک"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت چک‌ها…"), a.kit.lp(-1, -2));
        final boolean myIn = incoming;
        final String myBucket = bucket;
        final boolean myCal = calMode;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.snapIn = myIn;
            d.snapBucket = myBucket;
            d.snapCal = myCal;
            d.groups = soft(d.notes, "خلاصه وضعیت‌ها", () -> Repo.exec(c, MoneyQueries.chequeGroups(m, myIn, f)));
            if (myCal) {
                d.cal = soft(d.notes, "تقویم", () -> Repo.exec(c, MoneyQueries.chequeDue(m, myIn, 62)));
            } else if ("due".equals(myBucket)) {
                d.list = soft(d.notes, "سررسیدها", () -> Repo.exec(c, MoneyQueries.chequeDue(m, myIn, 30)));
            } else {
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MoneyQueries.chequeList(m, myIn, f, myBucket)));
            }
            if (d.list == null && d.groups == null) throw new Exception(firstNote(d.notes));
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

    private static final class Agg {
        long count;
        double total;
    }

    private void build(LinearLayout content, Data d) {
        final boolean myIn = d.snapIn;
        final String myBucket = d.snapBucket == null ? "" : d.snapBucket;
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("مبنای بازه تاریخی: تاریخ " + (myIn ? "دریافت" : "صدور") + " چک"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        content.addView(a.kit.chips(new String[]{"↓ دریافتی", "↑ پرداختی"}, myIn ? 0 : 1, idx -> {
            incoming = idx == 0;
            bucket = "";
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.chips(new String[]{"فهرست", "تقویم سررسید"}, calMode ? 1 : 0, idx -> {
            calMode = idx == 1;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        if (d.snapCal) {
            buildCalendar(content, d, myIn);
            return;
        }

        Map<String, Agg> byBucket = new LinkedHashMap<>();
        Agg all = new Agg();
        if (d.groups != null) {
            for (Row r : d.groups) {
                String b = groupBucket(myIn, r.s("st"), r.s("back"));
                Agg g = byBucket.get(b);
                if (g == null) {
                    g = new Agg();
                    byBucket.put(b, g);
                }
                g.count += r.l("count");
                g.total += r.d("total");
                all.count += r.l("count");
                all.total += r.d("total");
            }
        }
        String[][] bs = buckets();
        String[] names = new String[bs.length];
        int sel = 0;
        for (int i = 0; i < bs.length; i++) {
            names[i] = bs[i][1];
            Agg g = byBucket.get(bs[i][0]);
            if (g != null && !bs[i][0].isEmpty() && !"due".equals(bs[i][0]))
                names[i] += " " + Money.fa(String.valueOf(g.count));
            if (bs[i][0].equals(myBucket)) sel = i;
        }
        content.addView(a.kit.chips(names, sel, idx -> {
            bucket = bs[idx][0];
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (!"due".equals(myBucket)) {
            Agg g = myBucket.isEmpty() ? all : byBucket.get(myBucket);
            if (g != null && g.count > 0) {
                LinearLayout c = a.kit.card(accent());
                if (!myBucket.isEmpty())
                    c.addView(a.kit.kv("وضعیت", bucketLabel(myBucket), Theme.TEXT), a.kit.lp(-1, -2));
                c.addView(a.kit.kv("تعداد", Money.fa(String.valueOf(g.count)) + " فقره", Theme.TEXT), a.kit.lp(-1, -2));
                c.addView(a.kit.kv("جمع", Money.rial(g.total), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
                a.kit.addCard(content, c);
            }
        }

        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("چکی یافت نشد", "وضعیت یا فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
        } else {
            for (Row r : d.list) {
                final Row row = r;
                String pill = "due".equals(myBucket) ? ("سررسید " + Jalali.shortLabel(row.s("sardate")))
                        : rowLabel(myIn, row);
                View v = a.kit.docRow("چک " + Money.fa(row.s("num")), row.s("customer"),
                        Jalali.shortLabel(row.s("sardate")), Money.rial(row.d("amount")),
                        pill, v2 -> openDetail(row, myIn));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
            if (!"due".equals(myBucket)) {
                final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
                content.addView(a.kit.pager(filter.page, hasMore,
                        () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                        () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
            }
        }

        renderNotes(content, d.notes);
    }

    /** F2: Jalali due-date calendar for the current side (Saturday-first grid). */
    private void buildCalendar(LinearLayout content, Data d, final boolean myIn) {
        if (calY <= 0) {
            String t = Jalali.todayStr();
            try {
                calY = Integer.parseInt(t.substring(0, 4));
                calM = Integer.parseInt(t.substring(5, 7));
            } catch (Exception e) {
                calY = 1405; calM = 1;
            }
        }
        java.util.Map<String, java.util.List<Row>> byDay = new java.util.LinkedHashMap<>();
        if (d.cal != null) for (Row r : d.cal) {
            String day = Jalali.disp(r.s("sardate"));
            if (day.isEmpty()) continue;
            if (!byDay.containsKey(day)) byDay.put(day, new java.util.ArrayList<Row>());
            byDay.get(day).add(r);
        }
        String title = Jalali.MONTHS[calM - 1] + " " + Money.fa(String.valueOf(calY));
        LinearLayout nav = a.kit.h();
        nav.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nav.addView(a.kit.btnGhost("›", Theme.GOLD, v -> { shiftMonth(-1); render(content); }), a.kit.lp(Theme.dp(52), -2));
        android.widget.TextView mt = a.kit.text(title, 15f, Theme.TEXT, true);
        mt.setGravity(android.view.Gravity.CENTER);
        nav.addView(mt, a.kit.wlp(1f));
        nav.addView(a.kit.btnGhost("‹", Theme.GOLD, v -> { shiftMonth(1); render(content); }), a.kit.lp(Theme.dp(52), -2));
        content.addView(nav, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(6));
        String[] wds = {"ش", "ی", "د", "س", "چ", "پ", "ج"};
        LinearLayout hw = a.kit.h();
        for (String w : wds) {
            android.widget.TextView tv = a.kit.text(w, 11f, Theme.MUTED, true);
            tv.setGravity(android.view.Gravity.CENTER);
            hw.addView(tv, a.kit.wlp(1f));
        }
        content.addView(hw, a.kit.lp(-1, -2));
        int dim = Jalali.daysInMonth(calY, calM);
        int lead = Jalali.weekdayIndex(String.format(java.util.Locale.US, "%04d/%02d/01", calY, calM));
        if (lead < 0) lead = 0;
        String today = Jalali.todayStr();
        double mSum = 0;
        int mN = 0;
        for (int week = 0; week < 6; week++) {
            boolean any = false;
            LinearLayout row = a.kit.h();
            for (int col = 0; col < 7; col++) {
                int dayNo = week * 7 + col - lead + 1;
                if (dayNo < 1 || dayNo > dim) {
                    row.addView(a.kit.space(4), a.kit.wlp(1f));
                } else {
                    any = true;
                    final String key = String.format(java.util.Locale.US, "%04d/%02d/%02d", calY, calM, dayNo);
                    final java.util.List<Row> rows = byDay.get(key);
                    double tot = 0;
                    if (rows != null) {
                        for (Row r : rows) tot += r.d("amount");
                        mSum += tot;
                        mN += rows.size();
                    }
                    boolean isToday = key.equals(today);
                    LinearLayout cell = a.kit.v();
                    cell.setGravity(android.view.Gravity.CENTER);
                    android.widget.TextView dn = a.kit.text(Money.fa(String.valueOf(dayNo)), 13f,
                            isToday ? Theme.GOLD : Theme.TEXT, rows != null || isToday);
                    dn.setGravity(android.view.Gravity.CENTER);
                    cell.addView(dn, a.kit.lp(-1, -2));
                    android.widget.TextView am = a.kit.text(rows == null ? "" : Money.compactRial(tot), 9f,
                            rows == null ? Theme.MUTED : Theme.INFO, false);
                    am.setGravity(android.view.Gravity.CENTER);
                    cell.addView(am, a.kit.lp(-1, -2));
                    cell.setBackground(rows != null ? Theme.pill(Theme.INFO) : (isToday ? Theme.pill(Theme.GOLD) : null));
                    int pad = Theme.dp(4);
                    cell.setPadding(pad, pad, pad, pad);
                    if (rows != null) {
                        Theme.pressable(cell);
                        final double fTot = tot;
                        cell.setOnClickListener(v -> showDayDialog(key, rows, fTot, myIn));
                    }
                    row.addView(cell, a.kit.wlp(1f));
                }
            }
            if (!any && week > 0) break;
            content.addView(row, a.kit.lp(-1, -2));
        }
        LinearLayout c = a.kit.card(accent());
        c.addView(a.kit.kv("سررسید " + title, Money.fa(String.valueOf(mN)) + " فقره", Theme.TEXT), a.kit.lp(-1, -2));
        c.addView(a.kit.kv("جمع مبالغ", Money.rial(mSum), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
        renderNotes(content, d.notes);
    }

    private void shiftMonth(int delta) {
        calM += delta;
        while (calM < 1) { calM += 12; calY--; }
        while (calM > 12) { calM -= 12; calY++; }
    }

    private void showDayDialog(String key, java.util.List<Row> rows, double tot, final boolean inc) {
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("روز", Jalali.dispFa(key) + " • " + Jalali.weekday(key), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("جمع", Money.rial(tot), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        for (final Row r : rows) {
            View v = a.kit.docRow("چک " + Money.fa(r.s("num")), r.s("customer"),
                    "", Money.rial(r.d("amount")), rowLabel(inc, r), v2 -> openDetail(r, inc));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            body.addView(v, p);
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

    private void openDetail(final Row r, final boolean inc) {
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("شماره", Money.fa(r.s("num")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مبلغ", Money.rial(r.d("amount")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("سررسید", Jalali.dispFa(r.s("sardate")), Theme.TEXT), a.kit.lp(-1, -2));
        int dd = daysUntil(r.s("sardate"));
        if (dd != Integer.MIN_VALUE) {
            String t = dd < 0 ? "گذشته (" + Money.fa(String.valueOf(-dd)) + " روز)" : (dd == 0 ? "امروز" : Money.fa(String.valueOf(dd)) + " روز مانده");
            body.addView(a.kit.kv("وضعیت سررسید", t, dd < 0 ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.kv(inc ? "مشتری" : "طرف‌حساب", r.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("bank").isEmpty()) body.addView(a.kit.kv("بانک", r.s("bank"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("branch").isEmpty()) body.addView(a.kit.kv(inc ? "شعبه" : "گیرنده", r.s("branch"), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("وضعیت", rowLabel(inc, r), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("sayad").isEmpty()) body.addView(a.kit.kv("شناسه صیادی", Money.fa(r.s("sayad")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!Jalali.disp(r.s("getdate")).isEmpty())
            body.addView(a.kit.kv(incoming ? "تاریخ دریافت" : "تاریخ صدور", Jalali.dispFa(r.s("getdate")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("descrip").isEmpty()) body.addView(a.kit.kv("شرح", r.s("descrip"), Theme.MUTED), a.kit.lp(-1, -2));
        body.addView(a.kit.text("مبلغ به حروف: " + Money.words(r.d("amount")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        LinearLayout footer = a.kit.h();
        if (!r.s("ghno").isEmpty() && !"0".equals(r.s("ghno"))) {
            final String ghno = r.s("ghno");
            footer.addView(a.kit.btn("مشاهده در قبوض", v -> {
                dlg.dismiss();
                String target = inc ? "dar_in" : "dar_out";
                Screen s = a.screen(target);
                if (s != null && s.filter() != null) {
                    Filter nf = s.filter().copy();
                    nf.search = ghno;
                    nf.page = 0;
                    s.applyFilter(nf);
                }
                a.nav(target);
            }), a.kit.wlp(1f));
            footer.addView(a.kit.space(8));
        }
        footer.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.wlp(1f));
        body.addView(a.kit.gap(8));
        body.addView(footer, a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }

    private int daysUntil(String due) {
        String norm = Jalali.disp(due);
        if (norm.isEmpty()) return Integer.MIN_VALUE;
        int td = Jalali.parse(Jalali.todayStr());
        int bd = Jalali.parse(norm);
        if (td < 0 || bd < 0) return Integer.MIN_VALUE;
        return bd - td;
    }
}
