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
    private String groupBucket(boolean inc, String st, String back, long hasBank) {
        return inc ? AtiranSchema.chequeInBucket(st, back) : AtiranSchema.chequeOutBucket(st);
    }

    /** Persian label for one cheque row (prefers the DB status label when present). */
    private String rowLabel(boolean inc, Row r) {
        if (!r.s("statusLabel").isEmpty()) return r.s("statusLabel");
        return inc ? AtiranSchema.chequeInStatusFa(r.s("st"), r.s("back"))
                : AtiranSchema.chequeOutStatusFa(r.s("st"));
    }

    private static final class Data {
        List<Row> groups = new ArrayList<>();
        List<Row> list = new ArrayList<>();
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
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.groups = soft(d.notes, "خلاصه وضعیت‌ها", () -> Repo.exec(c, MoneyQueries.chequeGroups(m, myIn, f)));
            if ("due".equals(myBucket)) {
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
        final boolean myIn = incoming;
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("مبنای بازه تاریخی: تاریخ " + (incoming ? "دریافت" : "صدور") + " چک"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        content.addView(a.kit.chips(new String[]{"↓ دریافتی", "↑ پرداختی"}, incoming ? 0 : 1, idx -> {
            incoming = idx == 0;
            bucket = "";
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        Map<String, Agg> byBucket = new LinkedHashMap<>();
        Agg all = new Agg();
        if (d.groups != null) {
            for (Row r : d.groups) {
                String b = groupBucket(myIn, r.s("st"), r.s("back"), r.l("hasBank"));
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
            if (bs[i][0].equals(bucket)) sel = i;
        }
        content.addView(a.kit.chips(names, sel, idx -> {
            bucket = bs[idx][0];
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (!"due".equals(bucket)) {
            Agg g = bucket.isEmpty() ? all : byBucket.get(bucket);
            if (g != null && g.count > 0) {
                LinearLayout c = a.kit.card(accent());
                if (!bucket.isEmpty())
                    c.addView(a.kit.kv("وضعیت", bucketLabel(bucket), Theme.TEXT), a.kit.lp(-1, -2));
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
                String pill = "due".equals(bucket) ? ("سررسید " + Jalali.shortLabel(row.s("sardate")))
                        : rowLabel(myIn, row);
                View v = a.kit.docRow("چک " + Money.fa(row.s("num")), row.s("customer"),
                        Jalali.shortLabel(row.s("sardate")), Money.rial(row.d("amount")),
                        pill, v2 -> openDetail(row));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
            if (!"due".equals(bucket)) {
                final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
                content.addView(a.kit.pager(filter.page, hasMore,
                        () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                        () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
            }
        }

        renderNotes(content, d.notes);
    }

    private void openDetail(final Row r) {
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("شماره", Money.fa(r.s("num")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مبلغ", Money.rial(r.d("amount")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("سررسید", Jalali.dispFa(r.s("sardate")), Theme.TEXT), a.kit.lp(-1, -2));
        int dd = daysUntil(r.s("sardate"));
        if (dd != Integer.MIN_VALUE) {
            String t = dd < 0 ? "گذشته (" + Money.fa(String.valueOf(-dd)) + " روز)" : (dd == 0 ? "امروز" : Money.fa(String.valueOf(dd)) + " روز مانده");
            body.addView(a.kit.kv("وضعیت سررسید", t, dd < 0 ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.kv(incoming ? "مشتری" : "طرف‌حساب", r.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("bank").isEmpty()) body.addView(a.kit.kv("بانک", r.s("bank"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("branch").isEmpty()) body.addView(a.kit.kv(incoming ? "شعبه" : "گیرنده", r.s("branch"), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("وضعیت", rowLabel(incoming, r), Theme.TEXT), a.kit.lp(-1, -2));
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
                String target = incoming ? "dar_in" : "dar_out";
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
