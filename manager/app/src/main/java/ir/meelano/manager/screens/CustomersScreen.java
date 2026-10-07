package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
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

/** Customers: buckets, balances, and the full dossier (ledger + invoices + vouchers + cheques). */
public class CustomersScreen extends Screen {
    private final Filter filter = new Filter();

    public CustomersScreen(MainActivity a) {
        super(a);
        filter.sort = "debt";
        filter.top = 50;
    }

    @Override
    public String id() { return "customers"; }

    @Override
    public String title() { return "مشتریان"; }

    @Override
    public String glyph() { return "♙"; }

    @Override
    public int accent() { return Theme.SUCCESS; }

    @Override
    public Filter filter() { return filter; }

    @Override
    public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = "نام، کد یا موبایل…";
        c.visitors = true;
        c.routes = true;
        c.custGroups = true;
        c.statusTitle = "وضعیت حساب";
        c.status = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "همه"),
                new FilterSheet.Opt("debt", "بدهکار"),
                new FilterSheet.Opt("credit", "بستانکار"),
                new FilterSheet.Opt("settled", "تسویه"),
                new FilterSheet.Opt("no_buy", "بدون خرید"),
                new FilterSheet.Opt("blocked", "مسدود"),
        };
        c.sortTitle = "مرتب‌سازی";
        c.sort = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "نام"),
                new FilterSheet.Opt("top", "بیشترین خرید"),
                new FilterSheet.Opt("debt", "بیشترین بدهی"),
                new FilterSheet.Opt("balance_asc", "کمترین مانده"),
        };
        return c;
    }

    private static final String[][] BUCKETS = {
            {"", "همه"}, {"debt", "بدهکار"}, {"credit", "بستانکار"},
            {"settled", "تسویه"}, {"no_buy", "بدون خرید"}, {"blocked", "مسدود"},
    };

    private static final class Data {
        Row summary = new Row();
        List<Row> list = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت مشتریان…"), a.kit.lp(-1, -2));
        final Filter f = filter.copy();

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.summary = soft(d.notes, "خلاصه", () -> Repo.one(c, MasterQueries.customersSummary(m)));
            d.list = soft(d.notes, "فهرست", () -> Repo.exec(c, MasterQueries.customersList(m, f)));
            if (d.list == null) throw new Exception(firstNote(d.notes));
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

        Row s = d.summary == null ? new Row() : d.summary;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("مشتریان", Money.fa(String.valueOf(s.l("total"))), "", accent()));
        kpis.add(new Kit.Kpi("مانده بدهی", Money.compact(s.d("debtorsSum")), Money.fa(String.valueOf(s.l("debtorsN"))) + " نفر", Theme.DANGER));
        kpis.add(new Kit.Kpi("مانده بستانکاری", Money.compact(s.d("creditorsSum")), Money.fa(String.valueOf(s.l("creditorsN"))) + " نفر", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("مسدود", Money.fa(String.valueOf(s.l("blockedN"))), "نفر", Theme.WARNING));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));

        String[] names = new String[BUCKETS.length];
        int sel = 0;
        for (int i = 0; i < BUCKETS.length; i++) {
            names[i] = BUCKETS[i][1];
            if (BUCKETS[i][0].equals(filter.status == null ? "" : filter.status)) sel = i;
        }
        content.addView(a.kit.chips(names, sel, idx -> {
            filter.status = BUCKETS[idx][0];
            filter.page = 0;
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("مشتری‌ای یافت نشد", "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
        } else {
            for (Row r : d.list) {
                final String code = r.s("code");
                double bal = r.d("balance");
                int col = bal > 0.5 ? Theme.DANGER : (bal < -0.5 ? Theme.SUCCESS : Theme.MUTED);
                String side = bal > 0.5 ? Money.rial(bal) : (bal < -0.5 ? Money.rial(-bal) : "تسویه");
                String sub = "کد " + Money.fa(code) + " • فروش " + Money.compact(r.d("salesTotal"));
                if (!r.s("route").isEmpty()) sub += " • " + r.s("route");
                View v = a.kit.personRow(r.s("name"), sub, side,
                        bal > 0.5 ? "بدهکار" : (bal < -0.5 ? "بستانکار" : "مانده"), col, v2 -> openDossier(code));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
            final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
            content.addView(a.kit.pager(filter.page, hasMore,
                    () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                    () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
        }

        renderNotes(content, d.notes);
    }

    // ---------------- dossier ----------------
    private static final class Dossier {
        String code = "";
        Row header = new Row();
        List<Row> ledger = new ArrayList<>();
        List<Row> invoices = new ArrayList<>();
        List<Row> darsIn = new ArrayList<>();
        List<Row> darsOut = new ArrayList<>();
        List<Row> chqIn = new ArrayList<>();
        List<Row> chqOut = new ArrayList<>();
    }

    /** Open the full customer file by code (also used for cross-navigation from Home). */
    public void openDossier(final String code) {
        a.kit.toast("در حال دریافت پرونده…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Dossier dz = new Dossier();
            dz.code = code;
            Filter df = new Filter();
            df.preset = Filter.P_ALL;
            df.applyPreset();
            df.top = 300;
            df.page = 0;
            try {
                dz.header = Repo.one(c, MasterQueries.customerHeader(m, code));
            } catch (Exception ignored) { }
            try {
                dz.ledger = Repo.exec(c, MasterQueries.customerTurnover(m, code, df));
            } catch (Exception ignored) { }
            try {
                dz.invoices = Repo.exec(c, MasterQueries.partyInvoices(m, true, code, df));
            } catch (Exception ignored) { }
            try {
                dz.darsIn = Repo.exec(c, MasterQueries.customerDars(m, code, 0, df));
            } catch (Exception ignored) { }
            try {
                dz.darsOut = Repo.exec(c, MasterQueries.customerDars(m, code, 1, df));
            } catch (Exception ignored) { }
            try {
                dz.chqIn = Repo.exec(c, MasterQueries.customerCheques(m, code, true));
            } catch (Exception ignored) { }
            try {
                dz.chqOut = Repo.exec(c, MasterQueries.customerCheques(m, code, false));
            } catch (Exception ignored) { }
            return dz;
        }, new Repo.Cb<Dossier>() {
            @Override
            public void ok(Dossier dz) {
                showDossier(dz);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void showDossier(Dossier dz) {
        Row h = dz.header;
        LinearLayout body = a.kit.v();
        body.addView(a.kit.kv("مشتری", h.s("name").isEmpty() ? "—" : h.s("name"), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("کد", Money.fa(dz.code), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("cell").isEmpty()) body.addView(a.kit.kv("موبایل", Money.fa(h.s("cell")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("tell1").isEmpty()) body.addView(a.kit.kv("تلفن", Money.fa(h.s("tell1")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("routeName").isEmpty()) body.addView(a.kit.kv("مسیر", h.s("routeName"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("visitorName").isEmpty()) body.addView(a.kit.kv("ویزیتور", h.s("visitorName"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("groupName").isEmpty()) body.addView(a.kit.kv("گروه", h.s("groupName"), Theme.TEXT), a.kit.lp(-1, -2));
        double bal = h.d("balance");
        body.addView(a.kit.kv("مانده حساب",
                bal > 0.5 ? Money.rial(bal) + " بدهکار" : (bal < -0.5 ? Money.rial(-bal) + " بستانکار" : "تسویه"),
                bal > 0.5 ? Theme.DANGER : (bal < -0.5 ? Theme.SUCCESS : Theme.MUTED)), a.kit.lp(-1, -2));
        if (h.d("cred") > 0) {
            body.addView(a.kit.kv("سقف اعتبار", Money.rial(h.d("cred")), Theme.TEXT), a.kit.lp(-1, -2));
            double use = bal > 0 ? bal / h.d("cred") : 0;
            body.addView(a.kit.progressLine("مصرف اعتبار", Money.pct(use * 100),
                    use, use > 1 ? Theme.DANGER : use > 0.8 ? Theme.WARNING : Theme.SUCCESS), a.kit.lp(-1, -2));
        }
        boolean blocked = h.l("black_list") != 0;
        body.addView(a.kit.kv("وضعیت", blocked ? "⛔ مسدود" : "✓ فعال", blocked ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        if (!h.s("addre").isEmpty()) body.addView(a.kit.kv("آدرس", h.s("addre"), Theme.MUTED), a.kit.lp(-1, -2));

        if (!dz.ledger.isEmpty()) {
            body.addView(a.kit.text("گردش حساب (" + Money.fa(String.valueOf(dz.ledger.size())) + " سند)", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("opLabel", "عملیات", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("bed", "بدهکار", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("bes", "بستانکار", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, cap(dz.ledger, 50), null), a.kit.lp(-1, -2));
            if (dz.ledger.size() > 50)
                body.addView(a.kit.hint("+" + Money.fa(String.valueOf(dz.ledger.size() - 50)) + " سند قدیمی‌تر…"), a.kit.lp(-1, -2));
        }

        if (!dz.invoices.isEmpty()) {
            body.addView(a.kit.text("فاکتورهای فروش (" + Money.fa(String.valueOf(dz.invoices.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("no", "فاکتور", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("total", "جمع", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("paid", "دریافتی", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, cap(dz.invoices, 30), null), a.kit.lp(-1, -2));
        }

        if (!dz.darsIn.isEmpty()) {
            body.addView(a.kit.text("دریافت‌ها (" + Money.fa(String.valueOf(dz.darsIn.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(darTable(dz.darsIn), a.kit.lp(-1, -2));
        }
        if (!dz.darsOut.isEmpty()) {
            body.addView(a.kit.text("پرداخت‌ها (" + Money.fa(String.valueOf(dz.darsOut.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(darTable(dz.darsOut), a.kit.lp(-1, -2));
        }
        if (!dz.chqIn.isEmpty()) {
            for (Row r : dz.chqIn) r.put("stLabel", inLabel(r.s("st")));
            body.addView(a.kit.text("چک‌های دریافتی (" + Money.fa(String.valueOf(dz.chqIn.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(chqTable(dz.chqIn), a.kit.lp(-1, -2));
        }
        if (!dz.chqOut.isEmpty()) {
            for (Row r : dz.chqOut) r.put("stLabel", outLabel(r.s("st")));
            body.addView(a.kit.text("چک‌های پرداختی (" + Money.fa(String.valueOf(dz.chqOut.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(chqTable(dz.chqOut), a.kit.lp(-1, -2));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        LinearLayout footer = a.kit.h();
        if (!dz.ledger.isEmpty()) {
            final List<Row> fLedger = dz.ledger;
            final String fName = h.s("name");
            final String fCode = dz.code;
            footer.addView(a.kit.btn("اشتراک گردش", v -> {
                ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                        new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                        new ReportCatalog.Col("opLabel", "عملیات", ReportCatalog.T_TEXT),
                        new ReportCatalog.Col("bed", "بدهکار", ReportCatalog.T_MONEY),
                        new ReportCatalog.Col("bes", "بستانکار", ReportCatalog.T_MONEY),
                };
                a.sharePdf("گردش حساب " + fName, "کد " + fCode, cols, fLedger);
            }), a.kit.wlp(1f));
            footer.addView(a.kit.space(8));
        }
        final String fCode2 = dz.code;
        footer.addView(a.kit.btn("فاکتورها", v -> {
            dlg.dismiss();
            Screen s = a.screen("sales");
            Filter nf = s.filter().copy();
            nf.search = fCode2;
            nf.page = 0;
            s.applyFilter(nf);
            a.nav("sales");
        }), a.kit.wlp(1f));
        footer.addView(a.kit.space(8));
        footer.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.wlp(1f));
        body.addView(a.kit.gap(8));
        body.addView(footer, a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }

    private List<Row> cap(List<Row> rows, int n) {
        if (rows.size() <= n) return rows;
        return new ArrayList<>(rows.subList(0, n));
    }

    private View darTable(List<Row> rows0) {
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("ghno", "قبض", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
        };
        return a.kit.dataTable(cols, cap(rows0, 30), null);
    }

    private View chqTable(List<Row> rows0) {
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("num", "شماره", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("sardate", "سررسید", ReportCatalog.T_DATE),
                new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("stLabel", "وضعیت", ReportCatalog.T_TEXT),
        };
        return a.kit.dataTable(cols, cap(rows0, 30), null);
    }

    private String inLabel(String st) {
        String s = st == null ? "" : st.trim();
        if ("0".equals(s)) return "صندوق";
        if ("1".equals(s)) return "وصول‌شده";
        if ("2".equals(s)) return "برگشتی";
        if ("3".equals(s)) return "خرج‌شده";
        return s.isEmpty() ? "—" : s;
    }

    private String outLabel(String st) {
        String s = st == null ? "" : st.trim();
        if ("0".equals(s)) return "جاری";
        if ("1".equals(s)) return "پاس‌شده";
        if ("2".equals(s)) return "برگشتی";
        if ("3".equals(s)) return "انتقال";
        if ("4".equals(s)) return "سفید";
        return s.isEmpty() ? "—" : s;
    }
}
