package ir.meelano.manager.screens;

import ir.meelano.manager.data.CustomerPhotos;
import ir.meelano.manager.core.Diag;
import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import ir.meelano.manager.core.CacheStore;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.FollowUps;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.SmsIo;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.FisPrint;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Customers: buckets, balances, and the full dossier (ledger + invoices + vouchers + cheques). */
public class CustomersScreen extends Screen {
    private final Filter filter = new Filter();
    /** Avatar slots waiting for their picture (SHMO code → image view). Rebuilt on every render. */
    private final java.util.Map<String, android.widget.ImageView> avatarSlots = new java.util.HashMap<>();
    /** Queued debtor-SMS, flushed once SEND_SMS is granted. */
    private String pendSmsPhone = "";
    private String pendSmsText = "";

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
        // Date range is off: the customer query has no date clause, so showing the
        // preset chips and the two date fields was a control that did nothing.
        c.range = false;
        c.visitors = true;
        c.routes = true;
        c.custGroups = true;
        // Status is deliberately NOT offered here: the six buckets are already
        // on the screen as one-tap chips above the list. Having both meant the
        // same choice in two places, and one of the two was always stale.
        c.status = null;
        c.sortTitle = "مرتب‌سازی";
        c.sort = new FilterSheet.Opt[]{
                new FilterSheet.Opt("", "نام"),
                new FilterSheet.Opt("top", "بیشترین خرید"),
                new FilterSheet.Opt("debt", "بیشترین بدهی"),
                new FilterSheet.Opt("balance_asc", "کمترین مانده"),
        };
        return c;
    }

    private static final ReportCatalog.Col[] LEDGER_COLS = new ReportCatalog.Col[]{
            new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
            new ReportCatalog.Col("opLabel", "عملیات", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("bed", "بدهکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("bes", "بستانکار", ReportCatalog.T_MONEY),
    };

    private static final String[][] BUCKETS = {
            {"", "همه"}, {"debt", "بدهکار"}, {"credit", "بستانکار"},
            {"settled", "تسویه"}, {"no_buy", "بدون خرید"}, {"blocked", "مسدود"},
    };

    private static final class Data {
        Row summary = new Row();
        List<Row> list = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    /** Gold hero for the customers page: medallion, title, period subtitle and the filter bar. */
    @Override
    protected View heroCard() {
        LinearLayout w = a.kit.v();
        LinearLayout hero = a.kit.h();
        hero.setGravity(android.view.Gravity.CENTER_VERTICAL);
        hero.setBackground(Theme.hero(Theme.GOLD));
        hero.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        android.widget.TextView medal = a.kit.text("👥", 24, Theme.onAccent(Theme.GOLD), true);
        medal.setGravity(android.view.Gravity.CENTER);
        medal.setBackground(Theme.accentAvatar(Theme.GOLD));
        hero.addView(medal, new LinearLayout.LayoutParams(Theme.dp(60), Theme.dp(60)));
        hero.addView(a.kit.space(12));
        LinearLayout copy = a.kit.v();
        copy.addView(a.kit.text(title(), 21f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String sub = subtitle();
        if (sub != null && !sub.isEmpty())
            copy.addView(a.kit.text(sub, 12f, Theme.GOLD_SOFT, false), a.kit.lp(-1, -2));
        hero.addView(copy, a.kit.wlp(1f));
        w.addView(hero, a.kit.lp(-1, -2));
        if (filterConfig() != null && filter() != null) {
            w.addView(a.kit.gap(8));
            Filter f = filter();
            w.addView(a.kit.filterBar(f.describeFa(), !f.isDefault(), v -> a.openFilter()), a.kit.lp(-1, -2));
        }
        return w;
    }

    /** Small tinted label used on customer cards (code, route). */
    private android.widget.TextView tag(String s, int color) {
        android.widget.TextView t = a.kit.text(s, 10.5f, color, true);
        t.setSingleLine(true);
        t.setBackground(Theme.pill(Theme.alpha(color, Theme.isLight() ? 36 : 44)));
        t.setPadding(Theme.dp(8), Theme.dp(2), Theme.dp(8), Theme.dp(2));
        return t;
    }

    /**
     * Customer row as a card: avatar ring in the status colour, name on its own line,
     * code and route tags, then the balance in full width with a status pill. Nothing
     * is squeezed beside the name, so long names and amounts stay readable.
     */
    private View customerCard(final Row r, final String code, final double bal) {
        final int col = bal > 0.5 ? Theme.DANGER : (bal < -0.5 ? Theme.SUCCESS : Theme.GOLD);
        final String name = r.s("name").isEmpty() ? "—" : r.s("name");
        LinearLayout card = a.kit.v();
        card.setBackground(Theme.cardAccent(col));
        card.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));

        LinearLayout top = a.kit.h();
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.view.View av = CustomerPhotos.avatar(a, code, a.kit.initials(name), col, 50, avatarSlots);
        top.addView(av, new LinearLayout.LayoutParams(Theme.dp(50), Theme.dp(50)));
        top.addView(a.kit.space(12));
        LinearLayout copy = a.kit.v();
        android.widget.TextView n = a.kit.text(name, 15f, Theme.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(n, a.kit.lp(-1, -2));
        LinearLayout meta = a.kit.h();
        meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
        meta.addView(tag("کد " + Money.fa(code), Theme.GOLD_SOFT));
        String route = r.s("route");
        if (!route.isEmpty()) {
            meta.addView(a.kit.space(6));
            meta.addView(tag(route, Theme.GOLD));
        }
        LinearLayout.LayoutParams mp = a.kit.lp(-1, -2);
        mp.setMargins(0, Theme.dp(5), 0, 0);
        copy.addView(meta, mp);
        top.addView(copy, a.kit.wlp(1f));
        card.addView(top, a.kit.lp(-1, -2));

        View div = a.kit.divider();
        LinearLayout.LayoutParams dp2 = a.kit.lp(-1, Theme.dp(1));
        dp2.setMargins(0, Theme.dp(10), 0, Theme.dp(8));
        card.addView(div, dp2);

        LinearLayout bottom = a.kit.h();
        bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout amt = a.kit.v();
        amt.addView(a.kit.text(bal > 0.5 ? "مانده بدهی" : (bal < -0.5 ? "مانده بستانکاری" : "وضعیت حساب"),
                10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        String amount = bal > 0.5 ? Money.rial(bal) : (bal < -0.5 ? Money.rial(-bal) : "تسویه");
        amt.addView(a.kit.text(amount, 15f, col, true), a.kit.lp(-1, -2));
        bottom.addView(amt, a.kit.wlp(1f));
        String pillText = bal > 0.5 ? "بدهکار" : (bal < -0.5 ? "بستانکار" : "تسویه");
        bottom.addView(a.kit.pill(pillText, col), a.kit.lp(-2, -2));
        card.addView(bottom, a.kit.lp(-1, -2));

        Theme.pressable(card);
        card.setOnClickListener(v2 -> openDossier(code));
        return card;
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
        m.put("sum", d.summary);
        m.put("list", d.list);
        CacheStore.saveData(a, "cx_cust", cacheNow(), m);
    }

    private Data loadCache(String[] lab) {
        java.util.Map<String, Object> m = CacheStore.loadData(a, "cx_cust", lab);
        if (m == null) return null;
        Data d = new Data();
        d.summary = CacheStore.row(m, "sum");
        d.list = CacheStore.rows(m, "list");
        return d;
    }
    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addSearchRow(content);

        Row s = d.summary == null ? new Row() : d.summary;
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("مشتریان", Money.fa(String.valueOf(s.l("total"))), "", accent()));
        kpis.add(new Kit.Kpi("مانده بدهی", Money.compactRial(s.d("debtorsSum")), Money.fa(String.valueOf(s.l("debtorsN"))) + " نفر", Theme.DANGER));
        kpis.add(new Kit.Kpi("مانده بستانکاری", Money.compactRial(s.d("creditorsSum")), Money.fa(String.valueOf(s.l("creditorsN"))) + " نفر", Theme.SUCCESS));
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

        // While a search is active the query ignores paging and returns every
        // match, so a pager here would promise pages that cannot exist.
        final boolean searching = filter.search != null && !filter.search.trim().isEmpty();

        if (d.list == null || d.list.isEmpty()) {
            content.addView(a.kit.empty("مشتری‌ای یافت نشد",
                    searching ? "عبارت جستجو را تغییر دهید" : "فیلتر را تغییر دهید"), a.kit.lp(-1, -2));
        } else {
            if (searching) {
                content.addView(a.kit.text(
                        Money.fa(String.valueOf(d.list.size())) + " مشتری با جستجوی «"
                                + filter.search.trim() + "» یافت شد"
                                + (d.list.size() >= 500 ? " (فقط ۵۰۰ مورد اول)" : ""),
                        12f, Theme.MUTED, false), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
            }
            avatarSlots.clear();
            List<String> codes = new java.util.ArrayList<>();
            for (Row r : d.list) {
                final String code = r.s("code");
                codes.add(code);
                double bal = r.d("balance");
                View v = customerCard(r, code, bal);
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(v, p);
            }
            // One batched query for the pictures of this page; rows fill in when they arrive.
            CustomerPhotos.refresh(a.repo, a, codes, () -> CustomerPhotos.fill(avatarSlots, a));
            if (!searching) {
                final boolean hasMore = d.list.size() >= Math.max(1, filter.top);
                content.addView(a.kit.pager(filter.page, hasMore,
                        () -> { filter.page = Math.max(0, filter.page - 1); render(content); },
                        () -> { filter.page = filter.page + 1; render(content); }), a.kit.lp(-1, -2));
            }
        }

        renderNotes(content, d.notes);
    }

    @Override
    public void onSmsPermission(boolean granted) {
        String d = pendSmsPhone;
        String t = pendSmsText;
        pendSmsPhone = "";
        pendSmsText = "";
        if (granted && t != null && !t.isEmpty()) doDebtSend(d, t);
        else if (!granted) a.kit.toast("بدون دسترسی پیامک، ارسال ممکن نیست");
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
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:359", ignored); }
            try {
                dz.ledger = Repo.exec(c, MasterQueries.customerTurnover(m, code, df));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:362", ignored); }
            try {
                dz.invoices = Repo.exec(c, MasterQueries.partyInvoices(m, true, code, df));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:365", ignored); }
            try {
                dz.darsIn = Repo.exec(c, MasterQueries.customerDars(m, code, 0, df));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:368", ignored); }
            try {
                dz.darsOut = Repo.exec(c, MasterQueries.customerDars(m, code, 1, df));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:371", ignored); }
            try {
                dz.chqIn = Repo.exec(c, MasterQueries.customerCheques(m, code, true));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:374", ignored); }
            try {
                dz.chqOut = Repo.exec(c, MasterQueries.customerCheques(m, code, false));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:377", ignored); }
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
        final AlertDialog[] dlgH = new AlertDialog[1];
        Row h = dz.header;
        LinearLayout body = a.kit.v();
        final java.util.Map<String, android.widget.ImageView> dossierSlots = new java.util.HashMap<>();
        LinearLayout avRow = a.kit.h();
        avRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        avRow.addView(CustomerPhotos.avatar(a, dz.code, a.kit.initials(h.s("name")), Theme.GOLD, 72, dossierSlots),
                new LinearLayout.LayoutParams(Theme.dp(72), Theme.dp(72)));
        body.addView(avRow, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        CustomerPhotos.refresh(a.repo, a, java.util.Collections.singletonList(dz.code),
                () -> CustomerPhotos.fill(dossierSlots, a));
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
            body.addView(a.kit.text("خروجی کامل گردش حساب", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.exportBar("گردش حساب " + h.s("name"), "کد " + dz.code, LEDGER_COLS, dz.ledger), a.kit.lp(-1, -2));
            body.addView(a.kit.gap(8));
        }

        // ---- follow-up notes ----
        final String folCode = dz.code;
        final String folName = h.s("name");
        List<FollowUps.Note> folNotes = FollowUps.list(a, folCode);
        LinearLayout fc = a.kit.card(Theme.VIOLET);
        fc.addView(a.kit.text("یادداشت پیگیری", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (folNotes.isEmpty()) {
            fc.addView(a.kit.hint("یادداشتی ثبت نشده است"), a.kit.lp(-1, -2));
        } else {
            for (int i = 0; i < folNotes.size(); i++) {
                final int fIdx = i;
                FollowUps.Note fn = folNotes.get(i);
                LinearLayout nr = a.kit.h();
                nr.addView(a.kit.text(Jalali.dispFa(fn.date) + " • " + fn.text, 12f, Theme.TEXT, false), a.kit.wlp(1f));
                nr.addView(a.kit.btnGhost("✕", Theme.DANGER, v -> {
                    FollowUps.remove(a, folCode, fIdx);
                    if (dlgH[0] != null) dlgH[0].dismiss();
                    openDossier(folCode);
                }), a.kit.lp(-2, -2));
                fc.addView(nr, a.kit.lp(-1, -2));
            }
        }
        fc.addView(a.kit.btnGhost("+ یادداشت جدید", Theme.VIOLET, v -> noteDialog(folCode, folName, dlgH)), a.kit.lp(-1, -2));
        body.addView(fc, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));

        // ---- credit score (0..100 gauge + breakdown) ----
        int score = 100;
        List<String> why = new ArrayList<>();
        if (blocked) {
            score = 5;
            why.add("مشتری مسدود است");
        } else {
            int bounced = 0;
            for (Row r : dz.chqIn) {
                String b = r.s("back").trim().toUpperCase(java.util.Locale.US);
                if ("T".equals(b) || "1".equals(b) || "TRUE".equals(b)) bounced++;
            }
            if (bounced > 0) {
                int pen = Math.min(45, bounced * 15);
                score -= pen;
                why.add(Money.fa(String.valueOf(bounced)) + " چک برگشتی (−" + Money.fa(String.valueOf(pen)) + ")");
            }
            if (h.d("cred") > 0) {
                double use2 = bal > 0 ? bal / h.d("cred") : 0;
                if (use2 > 1) { score -= 25; why.add("تخطی از سقف اعتبار (−۲۵)"); }
                else if (use2 > 0.8) { score -= 12; why.add("مصرف بالای اعتبار (−۱۲)"); }
                else if (use2 > 0.5) { score -= 6; why.add("مصرف نیمی از اعتبار (−۶)"); }
            }
            double invT = 0, invP = 0;
            for (Row r : dz.invoices) { invT += r.d("total"); invP += r.d("paid"); }
            if (invT > 0) {
                double un = (invT - invP) / invT;
                if (un > 0.5) { score -= 20; why.add("بیش از نیمی از فاکتورها وصول نشده (−۲۰)"); }
                else if (un > 0.3) { score -= 12; why.add("وصول‌نشدن بخشی از فاکتورها (−۱۲)"); }
                else if (un > 0.1) { score -= 6; why.add("مانده جزئی فاکتورها (−۶)"); }
            }
            if (bal < -0.5) score = Math.min(100, score + 5);
        }
        score = Math.max(5, Math.min(100, score));
        String grade = score >= 85 ? "عالی" : score >= 70 ? "خوب" : score >= 50 ? "متوسط" : score >= 30 ? "ضعیف" : "پرخطر";
        int gCol = score >= 70 ? Theme.SUCCESS : score >= 50 ? Theme.GOLD : score >= 30 ? Theme.WARNING : Theme.DANGER;
        body.addView(a.kit.gap(6));
        LinearLayout cc = a.kit.card(gCol);
        cc.addView(a.kit.text("⭐ امتیاز اعتباری", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        ir.meelano.manager.ui.Charts.Gauge gauge = new ir.meelano.manager.ui.Charts.Gauge(a);
        cc.addView(gauge, a.kit.lp(-1, Theme.dp(168)));
        gauge.set(score / 100.0, Money.fa(String.valueOf(score)) + " • " + grade, gCol, "امتیاز از ۱۰۰");
        for (String w : why) cc.addView(a.kit.hint("• " + w), a.kit.lp(-1, -2));
        if (why.isEmpty()) cc.addView(a.kit.hint("• بدون نکته منفی — خوش‌حساب"), a.kit.lp(-1, -2));
        body.addView(cc, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));

        if (!dz.ledger.isEmpty()) {
            for (Row r : dz.ledger)
                if (r.s("opLabel").isEmpty()) r.put("opLabel", AtiranSchema.actNameFallback(r.s("op")));
            body.addView(a.kit.text("گردش حساب (" + Money.fa(String.valueOf(dz.ledger.size())) + " سند)", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.hint("برای مشاهده شرح کامل هر سند، روی ردیف آن بزنید"), a.kit.lp(-1, -2));
            body.addView(a.kit.dataTable(LEDGER_COLS, cap(dz.ledger, 50), this::ledgerDialog), a.kit.lp(-1, -2));
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
            body.addView(a.kit.hint("برای مشاهده اقلام و تسویه‌ها، روی هر فاکتور بزنید"), a.kit.lp(-1, -2));
            body.addView(a.kit.dataTable(cols, cap(dz.invoices, 30), r -> openInvoiceDetail(r.s("no"))), a.kit.lp(-1, -2));
        }

        if (!dz.darsIn.isEmpty()) {
            body.addView(a.kit.text("دریافت‌ها (" + Money.fa(String.valueOf(dz.darsIn.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.hint("برای تفکیک نقد / کارت / حواله / چک، روی هر قبض بزنید"), a.kit.lp(-1, -2));
            body.addView(darTable(dz.darsIn, 0), a.kit.lp(-1, -2));
        }
        if (!dz.darsOut.isEmpty()) {
            body.addView(a.kit.text("پرداخت‌ها (" + Money.fa(String.valueOf(dz.darsOut.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.hint("برای تفکیک نقد / کارت / حواله / چک، روی هر قبض بزنید"), a.kit.lp(-1, -2));
            body.addView(darTable(dz.darsOut, 1), a.kit.lp(-1, -2));
        }
        if (!dz.chqIn.isEmpty()) {
            for (Row r : dz.chqIn) r.put("stLabel", inLabel(r));
            body.addView(a.kit.text("چک‌های دریافتی (" + Money.fa(String.valueOf(dz.chqIn.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.hint("برای جزئیات کامل، روی هر چک بزنید"), a.kit.lp(-1, -2));
            body.addView(chqTable(dz.chqIn), a.kit.lp(-1, -2));
        }
        if (!dz.chqOut.isEmpty()) {
            for (Row r : dz.chqOut) r.put("stLabel", outLabel(r));
            body.addView(a.kit.text("چک‌های پرداختی (" + Money.fa(String.valueOf(dz.chqOut.size())) + ")", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.hint("برای جزئیات کامل، روی هر چک بزنید"), a.kit.lp(-1, -2));
            body.addView(chqTable(dz.chqOut), a.kit.lp(-1, -2));
        }

        String cell0 = SmsIo.cleanPhone(h.s("cell"));
        if (cell0.length() >= 10 && bal > 0.5) {
            final String dCell = cell0;
            final String dName = h.s("name");
            final double dBal = bal;
            body.addView(a.kit.btnGhost("✉ یادآوری بدهی با پیامک", Theme.GOLD, v ->
                    debtDialog(dCell, dName, dBal)), a.kit.lp(-1, -2));
            body.addView(a.kit.gap(6));
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        dlgH[0] = dlg;
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
            try {
                android.view.WindowManager.LayoutParams lp = dlg.getWindow().getAttributes();
                lp.width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.94);
                dlg.getWindow().setAttributes(lp);
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:562", ignored); }
        }
        LinearLayout footer = a.kit.h();
        if (!dz.ledger.isEmpty()) {
            final List<Row> fLedger = dz.ledger;
            final String fName = h.s("name");
            final String fCode = dz.code;
            footer.addView(a.kit.btn("اشتراک گردش", v -> {
                a.sharePdf("گردش حساب " + fName, "کد " + fCode, LEDGER_COLS, fLedger);
            }), a.kit.wlp(1f));
            footer.addView(a.kit.space(8));
        }
        final String fCode2 = dz.code;
        footer.addView(a.kit.btn("فاکتورها", v -> {
            dlg.dismiss();
            Screen s = a.screen("sales");
            if (s != null && s.filter() != null) {
                Filter nf = s.filter().copy();
                nf.search = fCode2;
                nf.page = 0;
                s.applyFilter(nf);
            }
            a.nav("sales");
        }), a.kit.wlp(1f));
        footer.addView(a.kit.space(8));
        footer.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.wlp(1f));
        body.addView(a.kit.gap(8));
        body.addView(footer, a.kit.lp(-1, -2));
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        dlg.show();
    }

    private void debtDialog(String cell, String name, double bal) {
        String msg = "«" + (name.isEmpty() ? "مشتری گرامی" : name) + "» سلام؛ مانده بدهی شما "
                + Money.rial(bal) + " است. لطفاً جهت تسویه اقدام فرمایید. — " + shopLine();
        LinearLayout nb = a.kit.v();
        nb.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        final android.widget.EditText te = a.kit.edit("متن پیامک…", msg);
        try {
            te.setMinLines(3);
        } catch (Exception ignored) { Diag.ignored("CustomersScreen:602", ignored); }
        nb.addView(te, a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        nb.addView(a.kit.kv("گیرنده", Money.fa(cell), Theme.TEXT), a.kit.lp(-1, -2));
        final android.app.AlertDialog[] box = new android.app.AlertDialog[1];
        nb.addView(a.kit.btn("✉ ارسال پیامک", v -> {
            String t = te.getText().toString().trim();
            if (t.isEmpty()) {
                a.kit.toast("متن پیامک را وارد کنید");
                return;
            }
            box[0].dismiss();
            sendDebtSms(cell, t);
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("یادآوری بدهی", nb, true);
        box[0].show();
    }

    private String shopLine() {
        try {
            String nm = Company.get(a).displayName(a);
            return nm.isEmpty() ? "مدیریت فروشگاه" : nm;
        } catch (Exception e) {
            return "مدیریت فروشگاه";
        }
    }

    private void sendDebtSms(String dest, String msg) {
        if (!SmsIo.canSend(a)) {
            pendSmsPhone = dest;
            pendSmsText = msg;
            a.kit.toast("برای ارسال پیامک، دسترسی را تأیید کنید");
            SmsIo.askSend(a);
            return;
        }
        doDebtSend(dest, msg);
    }

    private void doDebtSend(String dest, String msg) {
        a.kit.toast("در حال ارسال پیامک…");
        SmsIo.send(a, dest, msg, new SmsIo.Cb() {
            @Override
            public void ok() {
                a.kit.toast("✓ یادآوری ارسال شد");
            }

            @Override
            public void fail(String fa) {
                a.kit.toast(fa);
            }
        });
    }

    private void noteDialog(final String code, final String name, final AlertDialog[] dlgH) {
        LinearLayout nb = a.kit.v();
        nb.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        final android.widget.EditText te = a.kit.edit("متن یادداشت…", "");
        nb.addView(te, a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        final String[] date = {Jalali.todayStr()};
        final android.widget.TextView dl = a.kit.text("یادآوری: " + Jalali.dispFa(date[0]), 12.5f, Theme.TEXT, true);
        nb.addView(dl, a.kit.lp(-1, -2));
        nb.addView(a.kit.btnGhost("انتخاب تاریخ یادآوری", Theme.VIOLET, v ->
                a.kit.dateDialog("تاریخ یادآوری", date[0], picked -> {
                    date[0] = picked;
                    dl.setText("یادآوری: " + Jalali.dispFa(picked));
                })), a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        nb.addView(a.kit.btn("ثبت یادداشت", v -> {
            String t = te.getText().toString().trim();
            if (t.isEmpty()) {
                a.kit.toast("متن یادداشت را وارد کنید");
                return;
            }
            FollowUps.add(a, code, date[0], name, t);
            a.kit.toast("یادداشت ثبت شد");
            box[0].dismiss();
            if (dlgH[0] != null) dlgH[0].dismiss();
            openDossier(code);
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("یادداشت پیگیری", nb, true);
        box[0].show();
    }

    // ---------------- drill-down: ledger / invoice / voucher / cheque ----------------

    private void ledgerDialog(Row r) {
        LinearLayout nb = a.kit.v();
        nb.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        nb.addView(a.kit.kv("تاریخ", Jalali.dispFa(r.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        nb.addView(a.kit.kv("عملیات", r.s("opLabel").isEmpty() ? r.s("op") : r.s("opLabel"), Theme.TEXT), a.kit.lp(-1, -2));
        if (r.d("bed") > 0) nb.addView(a.kit.kv("بدهکار", Money.rial(r.d("bed")), Theme.DANGER), a.kit.lp(-1, -2));
        if (r.d("bes") > 0) nb.addView(a.kit.kv("بستانکار", Money.rial(r.d("bes")), Theme.SUCCESS), a.kit.lp(-1, -2));
        if (!r.s("ghno").isEmpty()) nb.addView(a.kit.kv("شماره سند", Money.fa(r.s("ghno")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("descrip").isEmpty()) nb.addView(a.kit.kv("شرح", r.s("descrip"), Theme.MUTED), a.kit.lp(-1, -2));
        if (!r.s("doneDate").isEmpty()) nb.addView(a.kit.kv("تاریخ ثبت", Jalali.dispFa(r.s("doneDate")), Theme.MUTED), a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        nb.addView(a.exportBar("سند گردش حساب", Jalali.dispFa(r.s("date")), LEDGER_COLS,
                java.util.Collections.singletonList(r)), a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        nb.addView(a.kit.btn("بستن", v -> box[0].dismiss()), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("جزئیات سند", nb, true);
        box[0].show();
    }

    private void openInvoiceDetail(final String no) {
        if (no == null || no.isEmpty()) return;
        a.kit.toast("در حال دریافت فاکتور…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Row head = Repo.one(c, Queries.factorHeader(m, true, no));
            List<Row> lines = Repo.exec(c, Queries.factorLines(m, true, no));
            List<Row> dars = new ArrayList<>();
            try {
                dars = Repo.exec(c, MoneyQueries.invoiceDars(m, 0, no));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:719", ignored); }
            return new InvDetail(head, lines, dars);
        }, new Repo.Cb<InvDetail>() {
            @Override
            public void ok(InvDetail dt) {
                if (dt == null || dt.head == null || dt.head.s("no").isEmpty()) {
                    a.kit.toast("فاکتور یافت نشد");
                    return;
                }
                showInvoiceDetail(dt);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private static final class InvDetail {
        final Row head;
        final List<Row> lines;
        final List<Row> dars;

        InvDetail(Row head, List<Row> lines, List<Row> dars) {
            this.head = head;
            this.lines = lines;
            this.dars = dars;
        }
    }

    private void showInvoiceDetail(InvDetail dt) {
        Row head = dt.head;
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        body.addView(a.kit.kv("شماره", Money.fa(head.s("no")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("تاریخ", Jalali.dispFa(head.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مشتری", head.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!head.s("visitor").isEmpty() && !"بدون ویزیتور".equals(head.s("visitor")))
            body.addView(a.kit.kv("ویزیتور", head.s("visitor"), Theme.TEXT), a.kit.lp(-1, -2));
        String desc = head.has("description") ? head.s("description") : head.s("descrip");
        if (!desc.isEmpty()) body.addView(a.kit.kv("شرح", desc, Theme.MUTED), a.kit.lp(-1, -2));
        if (head.has("tafif") && head.d("tafif") > 0)
            body.addView(a.kit.kv("تخفیف", Money.rial(head.d("tafif")), Theme.TEXT), a.kit.lp(-1, -2));
        if (head.has("tax") && head.d("tax") > 0)
            body.addView(a.kit.kv("مالیات", Money.rial(head.d("tax")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("جمع", Money.rial(head.d("total")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("دریافتی", Money.rial(head.d("paid")), Theme.SUCCESS), a.kit.lp(-1, -2));
        String tasvieh = head.has("tasvieh") ? AtiranSchema.tasviehFa(head.s("tasvieh")) : "نامشخص";
        if (!"نامشخص".equals(tasvieh))
            body.addView(a.kit.kv("وضعیت تسویه", tasvieh,
                    "تسویه‌شده".equals(tasvieh) ? Theme.SUCCESS : Theme.WARNING), a.kit.lp(-1, -2));
        double remain = head.d("total") - head.d("paid");
        if (Math.abs(remain) > AtiranSchema.SETTLE_TOLERANCE)
            body.addView(a.kit.kv("مانده", Money.rial(remain), remain > 0 ? Theme.DANGER : Theme.INFO), a.kit.lp(-1, -2));
        body.addView(a.kit.text("مبلغ به حروف: " + Money.words(head.d("total")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        ReportCatalog.Col[] lineCols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("naka", "کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("qtyVah", "مقدار", ReportCatalog.T_NUM),
                new ReportCatalog.Col("vahPrice", "فی", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("lineSum", "مبلغ", ReportCatalog.T_MONEY),
        };
        if (!dt.lines.isEmpty()) {
            for (Row r : dt.lines) {
                if (Math.abs(r.d("qtyVah")) < 0.0005 && Math.abs(r.d("qtyJoz")) > 0.0005)
                    r.put("qtyVah", r.d("qtyJoz"));
                if (Math.abs(r.d("vahPrice")) < 0.005 && Math.abs(r.d("jozPrice")) > 0.005)
                    r.put("vahPrice", r.d("jozPrice"));
            }
            body.addView(a.kit.text("اقلام فاکتور", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            body.addView(a.kit.dataTable(lineCols, dt.lines, null), a.kit.lp(-1, -2));
        }
        if (!dt.dars.isEmpty()) {
            body.addView(a.kit.text("دریافت‌های این فاکتور", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] dcols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("ghno", "قبض", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("total", "مبلغ قبض", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("settled", "تسویه‌شده", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(dcols, dt.dars, r -> openDarDetail(0, r.s("ghno"))), a.kit.lp(-1, -2));
        }
        final String fNo = head.s("no");
        final String fParty = head.s("customer");
        final String fDate = Jalali.dispFa(head.s("date"));
        final List<Row> fLines = dt.lines;
        final Row fHead = head;
        body.addView(a.kit.gap(8));
        if (!fLines.isEmpty())
            body.addView(a.exportBar("فاکتور " + fNo, fParty + " • " + fDate, lineCols, fLines), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        LinearLayout row = a.kit.h();
        final AlertDialog[] box = new AlertDialog[1];
        row.addView(a.kit.btnGhost("🖨 چاپ حرارتی", Theme.GOLD, v -> FisPrint.print(a, fHead, fLines, true)), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btn("بستن", v -> box[0].dismiss()), a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("فاکتور " + Money.fa(fNo), body, true);
        box[0].show();
    }

    private void openDarDetail(final int p, final String ghno) {
        if (ghno == null || ghno.isEmpty()) return;
        a.kit.toast("در حال دریافت قبض…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Row head = Repo.one(c, MoneyQueries.darHeader(m, p, ghno));
            List<Row> pos = new ArrayList<>();
            try {
                pos = Repo.exec(c, MoneyQueries.darPos(m, p, ghno));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:829", ignored); }
            List<Row> chqs = new ArrayList<>();
            try {
                chqs = Repo.exec(c, MoneyQueries.darCheques(m, p, ghno));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:833", ignored); }
            List<Row> invs = new ArrayList<>();
            try {
                invs = Repo.exec(c, MoneyQueries.darSettled(m, p, ghno));
            } catch (Exception ignored) { Diag.ignored("CustomersScreen:837", ignored); }
            return new DarDetail(head, pos, chqs, invs);
        }, new Repo.Cb<DarDetail>() {
            @Override
            public void ok(DarDetail dt) {
                showDarDetail(p, dt);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private static final class DarDetail {
        final Row head;
        final List<Row> pos;
        final List<Row> chqs;
        final List<Row> invs;

        DarDetail(Row head, List<Row> pos, List<Row> chqs, List<Row> invs) {
            this.head = head;
            this.pos = pos;
            this.chqs = chqs;
            this.invs = invs;
        }
    }

    private void showDarDetail(final int p, DarDetail dt) {
        Row head = dt.head == null ? new Row() : dt.head;
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        body.addView(a.kit.kv("شماره قبض", Money.fa(head.s("ghno")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("تاریخ", Jalali.dispFa(head.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!head.s("customer").isEmpty()) body.addView(a.kit.kv("طرف‌حساب", head.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        if (!head.s("kind").isEmpty()) body.addView(a.kit.kv("نوع", head.s("kind"), Theme.TEXT), a.kit.lp(-1, -2));
        // ---- separated type breakdown (cash / card / havaleh / cheque) ----
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("نقد", Money.compactRial(head.d("cash")), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("کارت", Money.compactRial(head.d("pos")), "", Theme.INFO));
        kpis.add(new Kit.Kpi("حواله", Money.compactRial(head.d("havaleh")), "", Theme.VIOLET));
        kpis.add(new Kit.Kpi("چک" + (head.l("chkCount") > 0 ? " (" + Money.fa(String.valueOf(head.l("chkCount"))) + ")" : ""),
                Money.compactRial(head.d("cheque")), "", Theme.WARNING));
        body.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(4));
        body.addView(a.kit.kv("جمع قبض", Money.rial(head.d("total")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (!head.s("descrip").isEmpty()) body.addView(a.kit.kv("شرح", head.s("descrip"), Theme.MUTED), a.kit.lp(-1, -2));
        if (!head.s("username").isEmpty()) body.addView(a.kit.kv("کاربر", head.s("username"), Theme.MUTED), a.kit.lp(-1, -2));
        body.addView(a.kit.text("مبلغ به حروف: " + Money.words(head.d("total")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
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
            final boolean inChq = p == 0;
            for (Row r : dt.chqs)
                r.put("stLabel", inChq ? AtiranSchema.chequeInStatusFa(r.s("st"), r.s("back"))
                        : AtiranSchema.chequeOutStatusFa(r.s("st")));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("num", "شماره", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("sardate", "سررسید", ReportCatalog.T_DATE),
                    new ReportCatalog.Col("stLabel", "وضعیت", ReportCatalog.T_TEXT),
            };
            body.addView(a.kit.dataTable(cols, dt.chqs, this::chequeDialog), a.kit.lp(-1, -2));
        }
        if (!dt.invs.isEmpty()) {
            body.addView(a.kit.text(p == 0 ? "فاکتورهای تسویه‌شده" : "فاکتورهای خرید تسویه‌شده", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("no", "فاکتور", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("customer", "طرف‌حساب", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("paidSettled", "تسویه‌شده", ReportCatalog.T_MONEY),
            };
            body.addView(a.kit.dataTable(cols, dt.invs, r -> openInvoiceDetail(r.s("no"))), a.kit.lp(-1, -2));
            body.addView(a.kit.gap(4));
            body.addView(a.exportBar("تسویه‌های قبض " + head.s("ghno"), Jalali.dispFa(head.s("date")), cols, dt.invs), a.kit.lp(-1, -2));
        } else if (!dt.pos.isEmpty()) {
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                    new ReportCatalog.Col("tracking", "پیگیری", ReportCatalog.T_TEXT),
            };
            body.addView(a.kit.gap(4));
            body.addView(a.exportBar("قبض " + head.s("ghno"), Jalali.dispFa(head.s("date")), cols, dt.pos), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.btn("بستن", v -> box[0].dismiss()), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog((p == 0 ? "قبض دریافت " : "قبض پرداخت ") + Money.fa(head.s("ghno")), body, true);
        box[0].show();
    }

    private void chequeDialog(Row r) {
        LinearLayout nb = a.kit.v();
        nb.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        nb.addView(a.kit.kv("شماره چک", Money.fa(r.s("num")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("bank").isEmpty()) nb.addView(a.kit.kv("بانک", r.s("bank"), Theme.TEXT), a.kit.lp(-1, -2));
        nb.addView(a.kit.kv("مبلغ", Money.rial(r.d("amount")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (!r.s("sardate").isEmpty()) nb.addView(a.kit.kv("سررسید", Jalali.dispFa(r.s("sardate")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("stLabel").isEmpty()) nb.addView(a.kit.kv("وضعیت", r.s("stLabel"), Theme.INFO), a.kit.lp(-1, -2));
        nb.addView(a.kit.text("مبلغ به حروف: " + Money.words(r.d("amount")), 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("num", "شماره", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("sardate", "سررسید", ReportCatalog.T_DATE),
                new ReportCatalog.Col("stLabel", "وضعیت", ReportCatalog.T_TEXT),
        };
        nb.addView(a.exportBar("چک " + r.s("num"), "", cols,
                java.util.Collections.singletonList(r)), a.kit.lp(-1, -2));
        nb.addView(a.kit.gap(8));
        final AlertDialog[] box = new AlertDialog[1];
        nb.addView(a.kit.btn("بستن", v -> box[0].dismiss()), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("جزئیات چک", nb, true);
        box[0].show();
    }

    private List<Row> cap(List<Row> rows, int n) {
        if (rows.size() <= n) return rows;
        return new ArrayList<>(rows.subList(0, n));
    }

    private View darTable(List<Row> rows0, final int p) {
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("ghno", "قبض", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("date", "تاریخ", ReportCatalog.T_DATE),
                new ReportCatalog.Col("total", "مبلغ", ReportCatalog.T_MONEY),
        };
        return a.kit.dataTable(cols, cap(rows0, 30), r -> openDarDetail(p, r.s("ghno")));
    }

    private View chqTable(List<Row> rows0) {
        ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                new ReportCatalog.Col("num", "شماره", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("sardate", "سررسید", ReportCatalog.T_DATE),
                new ReportCatalog.Col("amount", "مبلغ", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("stLabel", "وضعیت", ReportCatalog.T_TEXT),
        };
        return a.kit.dataTable(cols, cap(rows0, 30), this::chequeDialog);
    }

    private String inLabel(Row r) {
        return AtiranSchema.chequeInStatusFa(r.s("st"), r.s("back"));
    }

    private String outLabel(Row r) {
        return AtiranSchema.chequeOutStatusFa(r.s("st"));
    }
}
