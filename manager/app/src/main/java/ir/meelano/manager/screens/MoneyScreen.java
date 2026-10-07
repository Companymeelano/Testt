package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
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

/** Receipts (dar p=0) and Payments (dar p=1). */
public class MoneyScreen extends Screen {
    private static final int[] PALETTE = {
            Theme.GOLD, Theme.SUCCESS, Theme.INFO, Theme.VIOLET, Theme.WARNING, Theme.DANGER,
    };

    private final int p;
    private final Filter filter = new Filter();
    private int tab;

    public MoneyScreen(MainActivity a, int p) {
        super(a);
        this.p = p;
        filter.top = 50;
    }

    @Override
    public String id() { return p == 0 ? "dar_in" : "dar_out"; }

    @Override
    public String title() { return p == 0 ? "دریافت‌ها" : "پرداخت‌ها"; }

    @Override
    public String glyph() { return p == 0 ? "↓" : "↑"; }

    @Override
    public int accent() { return p == 0 ? Theme.SUCCESS : Theme.WARNING; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = "شماره قبض، طرف‌حساب…";
        c.range = true;
        return c;
    }

    private static final class Data {
        Row summary = new Row();
        List<Row> daily = new ArrayList<>();
        List<Row> list = new ArrayList<>();
        List<Row> banks = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت " + title() + "…"), a.kit.lp(-1, -2));
        final int myTab = tab;
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.summary = soft(d.notes, "خلاصه", () -> Repo.one(c, MoneyQueries.darSummary(m, f, p)));
            final String from = f.hasRange() ? f.from : Jalali.addDays(Jalali.todayStr(), -29);
            final String to = f.hasRange() ? f.to : Jalali.todayStr();
            d.daily = soft(d.notes, "روند", () -> Repo.exec(c, MoneyQueries.darDaily(m, p, from, to)));
            if (myTab == 0) {
                d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MoneyQueries.darList(m, f, p)));
            } else {
                d.banks = soft(d.notes, "بانک‌ها", () -> Repo.exec(c, MoneyQueries.posByBank(m, f, p)));
            }
            if (d.summary == null) throw new Exception(firstNote(d.notes));
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
        content.addView(a.kit.gap(12));
        addSearchRow(content);

        Row s = d.summary == null ? new Row() : d.summary;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("جمع", Money.compactRial(s.d("total")), Money.fa(String.valueOf(s.l("count"))) + " قبض", accent()));
        kpis.add(new Kit.Kpi("نقد", Money.compactRial(s.d("cash")), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("کارت", Money.compactRial(s.d("pos")), "", Theme.INFO));
        kpis.add(new Kit.Kpi("حواله", Money.compactRial(s.d("havaleh")), "", Theme.VIOLET));
        kpis.add(new Kit.Kpi("چک", Money.compactRial(s.d("cheque")), "", Theme.GOLD));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        if (d.daily != null && !d.daily.isEmpty()) {
            LinearLayout c = a.kit.card(accent());
            c.addView(a.kit.text("روند روزانه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : d.daily) pts.add(new Charts.Point(Jalali.shortLabel(r.s("day")), r.d("total")));
            final TextView cap = a.kit.text("", 11f, Theme.MUTED, false);
            ch.setData(pts, accent(), Charts.COMPACT);
            ch.setListener((idx, pt) -> cap.setText(Money.fa(pt.label) + " • " + Money.rial(pt.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(175)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }

        content.addView(a.kit.chips(new String[]{"فهرست", "گردش کارت بانک‌ها"}, tab, idx -> {
            tab = idx;
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (tab == 0) buildList(content, d);
        else buildBanks(content, d);

        renderNotes(content, d.notes);
    }

    private void buildList(LinearLayout content, Data d) {
        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("قبضی در این بازه یافت نشد", "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
            return;
        }
        for (Row r : d.list) {
            final String ghno = r.s("ghno");
            String pill = r.s("kind");
            if (pill.isEmpty()) pill = "—";
            View v = a.kit.docRow("قبض " + Money.fa(ghno), r.s("customer"),
                    Jalali.shortLabel(r.s("date")), Money.rial(r.d("total")),
                    pill, accent(), v2 -> openDetail(ghno));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(v, p);
        }
        final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
        content.addView(a.kit.pager(filter.page, hasMore,
                () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
    }

    private void buildBanks(LinearLayout content, Data d) {
        if (d.banks == null || d.banks.isEmpty()) {
            content.addView(a.kit.empty("گردش کارتی در این بازه ثبت نشده است", null), a.kit.lp(-1, -2));
            return;
        }
        LinearLayout c = a.kit.card(Theme.INFO);
        c.addView(a.kit.text("گردش کارت به تفکیک بانک", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.Donut dn = new Charts.Donut(a);
        List<Charts.Point> pts = new ArrayList<>();
        for (int i = 0; i < Math.min(8, d.banks.size()); i++)
            pts.add(new Charts.Point(d.banks.get(i).s("bank"), d.banks.get(i).d("total"), PALETTE[i % PALETTE.length]));
        double sum = 0;
        for (Row r : d.banks) sum += r.d("total");
        dn.setData(pts, "جمع گردش", Money.compactRial(sum));
        c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("count", "تعداد", ReportCatalog.T_NUM),
        };
        c.addView(a.kit.dataTable(cols, d.banks, null), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    // ---------------- voucher detail ----------------
    private void openDetail(final String ghno) {
        a.kit.toast("در حال دریافت قبض…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Row head = Repo.one(c, MoneyQueries.darHeader(m, p, ghno));
            List<Row> pos = new ArrayList<>();
            try {
                pos = Repo.exec(c, MoneyQueries.darPos(m, p, ghno));
            } catch (Exception ignored) { }
            List<Row> chqs = new ArrayList<>();
            try {
                chqs = Repo.exec(c, MoneyQueries.darCheques(m, p, ghno));
            } catch (Exception ignored) { }
            List<Row> invs = new ArrayList<>();
            try {
                invs = Repo.exec(c, MoneyQueries.darSettled(m, p, ghno));
            } catch (Exception ignored) { }
            return new Detail(head, pos, chqs, invs);
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
        final List<Row> pos;
        final List<Row> chqs;
        final List<Row> invs;

        Detail(Row head, List<Row> pos, List<Row> chqs, List<Row> invs) {
            this.head = head;
            this.pos = pos;
            this.chqs = chqs;
            this.invs = invs;
        }
    }

    private void showDetail(Detail dt) {
        Row head = dt.head;
        final AlertDialog[] dlgHolder = new AlertDialog[1];
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("شماره قبض", Money.fa(head.s("ghno")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("تاریخ", Money.fa(head.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("طرف‌حساب", head.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!head.s("kind").isEmpty()) body.addView(a.kit.kv("نوع", head.s("kind"), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.d("cash") > 0) body.addView(a.kit.kv("نقد", Money.rial(head.d("cash")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.d("pos") > 0) body.addView(a.kit.kv("کارت", Money.rial(head.d("pos")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.d("havaleh") > 0) body.addView(a.kit.kv("حواله", Money.rial(head.d("havaleh")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.d("cheque") > 0)
            body.addView(a.kit.kv("چک (" + Money.fa(String.valueOf(head.l("chkCount"))) + " فقره)", Money.rial(head.d("cheque")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("جمع", Money.rial(head.d("total")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (!head.s("descrip").isEmpty()) body.addView(a.kit.kv("شرح", head.s("descrip"), Theme.MUTED), a.kit.lp(-1, -2));
        if (!head.s("username").isEmpty()) body.addView(a.kit.kv("کاربر", head.s("username"), Theme.MUTED), a.kit.lp(-1, -2));
        String doneDate = head.s("doneDate");
        if (!doneDate.isEmpty() && !doneDate.equals(head.s("date")))
            body.addView(a.kit.kv("تاریخ ثبت", Money.fa(doneDate), Theme.MUTED), a.kit.lp(-1, -2));
        body.addView(a.kit.text("مبلغ به حروف: " + Money.words(head.d("total")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        if (!head.s("linkNo").isEmpty()) {
            final String linkNo = head.s("linkNo");
            final String target = p == 0 ? "sales" : "buy";
            body.addView(a.kit.btnGhost("🧾 فاکتور مرتبط " + Money.fa(linkNo), accent(), v -> {
                if (dlgHolder[0] != null) dlgHolder[0].dismiss();
                Screen s = a.screen(target);
                if (s != null) {
                    Filter nf = s.filter().copy();
                    nf.search = linkNo;
                    nf.page = 0;
                    s.applyFilter(nf);
                }
                a.nav(target);
            }), a.kit.lp(-1, -2));
        }

        if (!dt.pos.isEmpty()) {
            body.addView(a.kit.text("کارت / حواله", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("tracking", "پیگیری", ReportCatalog.T_TEXT),
            };
            body.addView(a.kit.dataTable(cols, dt.pos, null), a.kit.lp(-1, -2));
        }
        if (!dt.chqs.isEmpty()) {
            body.addView(a.kit.text("چک‌های این قبض", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("num", "شماره", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("sardate", "سررسید", ReportCatalog.T_DATE),
            };
            body.addView(a.kit.dataTable(cols, dt.chqs, null), a.kit.lp(-1, -2));
        }
        if (!dt.invs.isEmpty()) {
            body.addView(a.kit.text("فاکتورهای تسویه‌شده", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("no", "فاکتور", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("customer", "طرف‌حساب", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("paidSettled", "تسویه‌شده", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, dt.invs, null), a.kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        dlgHolder[0] = dlg;
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }
}
