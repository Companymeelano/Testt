package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;

import ir.meelano.manager.MainActivity;
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
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Global search across customers, products, invoices and cheques (with voice input). */
public class SearchScreen extends Screen {
    private String q = "";

    public SearchScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "search"; }

    @Override
    public String title() { return "جستجوی سراسری"; }

    @Override
    public String glyph() { return "⌕"; }

    @Override
    public int accent() { return Theme.GOLD; }

    private static final class Data {
        List<Row> cust = new ArrayList<>();
        List<Row> prod = new ArrayList<>();
        List<Row> inv = new ArrayList<>();
        List<Row> chq = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.searchBar("نام، کد، شماره فاکتور یا چک…", q, query -> {
            q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                a.kit.toast("عبارت جستجو را وارد کنید");
                return;
            }
            runQuery(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.hint("در مشتریان، کالاها، فاکتورهای فروش و چک‌های دریافتی جستجو می‌شود"), a.kit.lp(-1, -2));
    }

    private void runQuery(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.searchBar("نام، کد، شماره فاکتور یا چک…", q, query -> {
            q = query == null ? "" : query.trim();
            if (!q.isEmpty()) runQuery(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.loading("در حال جستجو…"), a.kit.lp(-1, -2));
        final String query = q;

        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            Filter qf = new Filter();
            qf.preset = Filter.P_ALL;
            qf.applyPreset();
            qf.search = query;
            qf.top = 5;
            qf.page = 0;
            d.cust = soft(d.notes, "مشتریان", () -> Repo.exec(c, MasterQueries.customersList(m, qf)));
            d.prod = soft(d.notes, "کالاها", () -> Repo.exec(c, MasterQueries.productsList(m, qf)));
            d.inv = soft(d.notes, "فاکتورها", () -> Repo.exec(c, Queries.factorList(m, true, qf)));
            d.chq = soft(d.notes, "چک‌ها", () -> Repo.exec(c, MoneyQueries.chequeList(m, true, qf, "")));
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                saveCache(d);
                build(content, d, query);
            }

            @Override
            public void fail(String faError) {
                String[] lab = {""};
                Data cached = loadCache(lab);
                if (cached != null) {
                    build(content, cached, query);
                    offlineBanner(content, lab[0], faError);
                    return;
                }
                content.removeAllViews();
                content.addView(heroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(10));
                content.addView(a.kit.error(faError, () -> runQuery(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private void saveCache(Data d) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("cust", d.cust);
        m.put("prod", d.prod);
        m.put("inv", d.inv);
        m.put("chq", d.chq);
        CacheStore.saveData(a, "cx_search", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_search", lab);
        if (m == null) return null;
        Data d = new Data();
        d.cust = CacheStore.rows(m, "cust");
        d.prod = CacheStore.rows(m, "prod");
        d.inv = CacheStore.rows(m, "inv");
        d.chq = CacheStore.rows(m, "chq");
        return d;
    }
    private void build(LinearLayout content, Data d, final String query) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.searchBar("نام، کد، شماره فاکتور یا چک…", query, query2 -> {
            q = query2 == null ? "" : query2.trim();
            if (!q.isEmpty()) runQuery(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        boolean any = false;
        if (d.cust != null && !d.cust.isEmpty()) {
            any = true;
            content.addView(a.kit.sectionHead("مشتریان", "همه ›", v -> gotoFiltered("customers", query)), a.kit.lp(-1, -2));
            for (Row r : d.cust) {
                final String code = r.s("code");
                double bal = r.d("balance");
                int col = bal > 0.5 ? Theme.DANGER : (bal < -0.5 ? Theme.SUCCESS : Theme.MUTED);
                View v = a.kit.personRow(r.s("name"), "کد " + Money.fa(code),
                        Math.abs(bal) > 0.5 ? Money.rial(Math.abs(bal)) : "تسویه",
                        bal > 0.5 ? "بدهکار" : (bal < -0.5 ? "بستانکار" : "مانده"), col,
                        v2 -> {
                            Screen s = a.screen("customers");
                            if (s instanceof CustomersScreen) ((CustomersScreen) s).openDossier(code);
                            a.nav("customers");
                        });
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
        }

        if (d.prod != null && !d.prod.isEmpty()) {
            any = true;
            content.addView(a.kit.sectionHead("کالاها", "همه ›", v -> gotoFiltered("products", query)), a.kit.lp(-1, -2));
            for (Row r : d.prod) {
                final String shka = r.s("shka");
                String code = r.s("code");
                if (code.isEmpty()) code = shka;
                View v = a.kit.invRow(r.s("naka"), "کد " + Money.fa(code), r.s("groupName"),
                        "موجودی " + Money.qty(r.d("vah")), Money.rial(r.d("sale")),
                        v2 -> {
                            Screen s = a.screen("products");
                            if (s instanceof ProductsScreen) ((ProductsScreen) s).openProduct(shka);
                            a.nav("products");
                        });
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
        }

        if (d.inv != null && !d.inv.isEmpty()) {
            any = true;
            content.addView(a.kit.sectionHead("فاکتورها", "همه ›", v -> gotoFiltered("sales", query)), a.kit.lp(-1, -2));
            for (Row r : d.inv) {
                final String no = r.s("no");
                boolean settled = Math.abs(r.d("remain")) <= AtiranSchema.SETTLE_TOLERANCE
                        || "t".equalsIgnoreCase(r.s("tasvieh").trim());
                View v = a.kit.invoiceRow("فاکتور " + Money.fa(no), r.s("customer"),
                        Jalali.shortLabel(r.s("date")), Money.rial(r.d("total")),
                        settled ? "settled" : "unsettled", v2 -> gotoFiltered("sales", no));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
        }

        if (d.chq != null && !d.chq.isEmpty()) {
            any = true;
            content.addView(a.kit.sectionHead("چک‌ها", "همه ›", v -> gotoFiltered("cheques", query)), a.kit.lp(-1, -2));
            for (Row r : d.chq) {
                final String num = r.s("num");
                View v = a.kit.docRow("چک " + Money.fa(num), r.s("customer"),
                        Jalali.shortLabel(r.s("sardate")), Money.rial(r.d("amount")),
                        "سررسید " + Jalali.shortLabel(r.s("sardate")), v2 -> gotoFiltered("cheques", num));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
        }

        if (!any)
            content.addView(a.kit.empty("نتیجه‌ای یافت نشد", "عبارت دیگری را امتحان کنید"), a.kit.lp(-1, -2));

        renderNotes(content, d.notes);
    }

    private void gotoFiltered(String target, String query) {
        Screen s = a.screen(target);
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.search = query;
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav(target);
    }
}
