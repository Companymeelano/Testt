package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.ChequeLiquidityQueries;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.RialFormatter;
import ir.meelano.manager.data.BankBalanceStore;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.FilterSheet;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * تامین چک — مدیریت نقدینگی چک‌های پرداختی (نسخه لاکچری بهینه 36.48)
 * علت گیر لود: N+1 کوئری POS برای هر بانک + overdue سنگین در لود اول + رندر سنگین 200 ردیف
 * رفع: batch POS یک کوئری, overdue تنبل (Lazy), token لغو, رندر افزایشی, چک باکس بدون ریلود
 */
public class ChequeLiquidityScreen extends Screen {
    private final Filter filter = new Filter();
    private LinearLayout lastContentRef;
    private int loadToken = 0;

    private String selectedBankRdf = "";
    private String selectedBankName = "همه بانک‌ها";
    private int dueWindowDays = 0;
    private final Set<String> excludedRdfs = new HashSet<>();
    private final Map<String, Long> manualBalances = new HashMap<>();
    private boolean posIncludeYesterday = false;

    private List<Row> bankSummaries = new ArrayList<>();
    private List<Row> banks = new ArrayList<>();
    private List<Row> cheques = new ArrayList<>();
    private Row posSummary = null;
    private List<Row> posList = new ArrayList<>();
    private List<Row> overdue = new ArrayList<>();
    private final List<BehaviorAnalysis> behaviors = new ArrayList<>();

    private boolean dataLoaded = false;
    private String lastError = "";
    private boolean overdueLoading = false;

    private static final class BehaviorAnalysis {
        String code; String name; String cell; double overdueAmount; String dueDate; int daysOverdue;
        double avgDelayDays; int sampleCount; double reliabilityScore; String patternLabel; int patternColor;
        long balance; long credit;
    }
    private static final class BankNet {
        String bankRdf; String bankName; long checksTotal; long posTotal; long balance; long net; int count;
    }

    public ChequeLiquidityScreen(MainActivity a) {
        super(a);
        filter.top = 200;
        posIncludeYesterday = RialFormatter.isBefore4am();
    }
    @Override public String id() { return "cheque_liquidity"; }
    @Override public String title() { return "تأمین چک"; }
    @Override public String glyph() { return "◈"; }
    @Override public int accent() { return Theme.GOLD; }
    @Override public Filter filter() { return filter; }
    @Override public FilterSheet.Config filterConfig() {
        FilterSheet.Config c = new FilterSheet.Config();
        c.searchHint = "شماره چک، صیادی، طرف‌حساب…";
        c.range = false; c.banks = false; return c;
    }

    @Override
    public void render(final LinearLayout content) {
        lastContentRef = content;
        loadToken = a.renderGen();
        final int token = loadToken;
        content.removeAllViews();
        content.addView(buildHeroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("چک‌های پرداختیِ جاری به تفکیک بانک • موجودی لحظه‌ای + پوز فردا ۴ صبح • تحلیل هوشمند تامین"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        // Luxury skeleton instead of plain loading
        content.addView(buildLuxurySkeleton(), a.kit.lp(-1, -2));

        manualBalances.clear();
        manualBalances.putAll(BankBalanceStore.getAll(a));
        final String search = filter.search == null ? "" : filter.search.trim();

        // Stage 1: فقط banks + summaries (سریع) — overdue را Lazy می گیریم
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Bundle b = new Bundle();
            b.banks = safeList(m, "بانک‌ها", () -> Repo.exec(c, ChequeLiquidityQueries.banks(m)));
            b.summaries = safeList(m, "چک‌های جاری", () -> Repo.exec(c, ChequeLiquidityQueries.putchkOutstandingByBank(m)));
            return b;
        }, new Repo.Cb<Bundle>() {
            @Override public void ok(Bundle b) {
                if (token != a.renderGen()) return;
                banks = b.banks == null ? new ArrayList<>() : b.banks;
                bankSummaries = b.summaries == null ? new ArrayList<>() : b.summaries;
                dataLoaded = true;
                loadChequesAndPos(content, search, token);
            }
            @Override public void fail(String faError) {
                if (token != a.renderGen()) return;
                lastError = faError;
                content.removeAllViews();
                content.addView(buildHeroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(12));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private static class Bundle { List<Row> banks; List<Row> summaries; }
    private <T> List<Row> safeList(Meta m, String key, Soft<List<Row>> s) { try { return s.run(); } catch (Exception e) { return new ArrayList<>(); } }

    private void loadChequesAndPos(final LinearLayout content, final String search, final int token) {
        final String bankFilter = selectedBankRdf == null ? "" : selectedBankRdf.trim();
        final String today = Jalali.todayStr();
        final boolean includeY = posIncludeYesterday;
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Bundle2 b2 = new Bundle2();
            String effectiveBank = bankFilter.isEmpty() ? null : bankFilter;
            b2.cheques = Repo.exec(c, ChequeLiquidityQueries.putchkOutstandingList(m, effectiveBank, search, 0, 80));
            if (effectiveBank != null && !effectiveBank.isEmpty()) {
                try {
                    Row r = Repo.one(c, ChequeLiquidityQueries.posPendingForBank(m, effectiveBank, today, includeY));
                    b2.posSummary = r;
                    // فقط 10 ردیف برای نمایش (نه همه)
                    b2.posList = Repo.exec(c, ChequeLiquidityQueries.posPendingListForBank(m, effectiveBank, today, includeY));
                    if (b2.posList.size() > 12) b2.posList = b2.posList.subList(0, 12);
                } catch (Exception e) { b2.posSummary = new Row(); b2.posList = new ArrayList<>(); }
            } else {
                // Batch برای همه بانک ها در یک کوئری
                b2.posByBank = new HashMap<>(); b2.posCountByBank = new HashMap<>();
                try {
                    List<Row> grouped = Repo.exec(c, ChequeLiquidityQueries.posPendingGroupedByBank(m, today, includeY));
                    for (Row r : grouped) {
                        String br = r.s("bankRdf");
                        if (br == null || br.isEmpty()) continue;
                        b2.posByBank.put(br, r.d("total"));
                        b2.posCountByBank.put(br, r.l("count"));
                    }
                } catch (Exception e) { /* pos optional */ }
            }
            return b2;
        }, new Repo.Cb<Bundle2>() {
            @Override public void ok(Bundle2 b2) {
                if (token != a.renderGen()) return;
                cheques = b2.cheques == null ? new ArrayList<>() : b2.cheques;
                if (dueWindowDays > 0) {
                    String today2 = Jalali.todayStr();
                    String until = Jalali.addDays(today2, dueWindowDays);
                    List<Row> filtered = new ArrayList<>();
                    for (Row r : cheques) {
                        String sd = Jalali.disp(r.s("sardate"));
                        if (sd.isEmpty()) continue;
                        if (sd.compareTo(today2) >= 0 && sd.compareTo(until) <= 0) filtered.add(r);
                    }
                    cheques = filtered;
                }
                posSummary = b2.posSummary;
                posList = b2.posList == null ? new ArrayList<>() : b2.posList;
                if (b2.posByBank != null) { posByBankCache = b2.posByBank; posCountByBankCache = b2.posCountByBank; }
                build(content);
            }
            @Override public void fail(String faError) {
                if (token != a.renderGen()) return;
                lastError = faError; build(content);
            }
        });
    }

    private static class Bundle2 { List<Row> cheques; Row posSummary; List<Row> posList; Map<String, Double> posByBank; Map<String, Long> posCountByBank; }
    private Map<String, Double> posByBankCache = new HashMap<>();
    private Map<String, Long> posCountByBankCache = new HashMap<>();

    // ---------------- Luxury Skeleton ----------------
    private View buildLuxurySkeleton() {
        LinearLayout wrap = a.kit.v();
        for (int i=0;i<3;i++) {
            LinearLayout sk = a.kit.card(Theme.GOLD);
            sk.setAlpha(0.85f);
            sk.addView(a.kit.text("◇ در حال دریافت…", 11f, Theme.GOLD_SOFT, true), a.kit.lp(-1,-2));
            sk.addView(a.kit.gap(8));
            View bar = new View(a); bar.setBackground(Theme.pill(Theme.alpha(Theme.GOLD, 40)));
            sk.addView(bar, new LinearLayout.LayoutParams(-1, Theme.dp(14)));
            wrap.addView(sk, a.kit.lp(-1,-2));
            wrap.addView(a.kit.gap(8));
        }
        return wrap;
    }

    private void build(LinearLayout content) {
        lastContentRef = content;
        content.removeAllViews();
        content.addView(buildHeroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("موجودی را به ریال وارد کنید • پوز امروز در ۴ صبح فردا واریز می‌شود" + (posIncludeYesterday ? " • قبل از ۴ صبح: پوز دیروز نیز محاسبه شد" : "")), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        addQuickStats(content);
        addBankSelector(content);
        addDueWindowChips(content);
        addSearchRow(content);
        if (bankSummaries.isEmpty() && cheques.isEmpty()) {
            content.addView(a.kit.empty("چکی برای تامین یافت نشد", "چک پرداختیِ جاری (وضعیت ۱) در دیتابیس ثبت نشده"), a.kit.lp(-1, -2));
            addProfessionalSuggestions(content, null);
            return;
        }
        boolean isAll = selectedBankRdf == null || selectedBankRdf.trim().isEmpty();
        if (isAll) buildAllBanksView(content); else buildSingleBankView(content);
        addCrossBankIntelligence(content);
        addOverdueSectionLazy(content);
        addCalendarHeatmap(content);
        addProfessionalSuggestions(content, selectedBankRdf);
        try {
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{ new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT), new ReportCatalog.Col("count", "تعداد", ReportCatalog.T_NUM), new ReportCatalog.Col("total", "جمع چک‌ها", ReportCatalog.T_MONEY)};
            content.addView(a.kit.gap(12));
            content.addView(a.exportBar("گزارش تامین چک — " + Jalali.todayStr(), "وضعیت تامین چک‌های پرداختی", cols, bankSummaries), a.kit.lp(-1, -2));
        } catch (Exception ignored) {}
        if (lastError != null && !lastError.isEmpty()) { content.addView(a.kit.gap(8)); content.addView(a.kit.hint("هشدار: " + lastError), a.kit.lp(-1, -2)); }
    }

    private void addQuickStats(LinearLayout content) {
        long totalChecksAll = 0; long totalPosAll = 0; long totalBalanceAll = 0;
        for (Row r : bankSummaries) totalChecksAll += Math.round(r.d("total"));
        for (Map.Entry<String, Double> e : posByBankCache.entrySet()) totalPosAll += Math.round(e.getValue());
        for (long v : manualBalances.values()) totalBalanceAll += v;
        if (posByBankCache.isEmpty() && posSummary != null) totalPosAll = Math.round(posSummary.d("total"));
        long netAll = totalBalanceAll + totalPosAll - totalChecksAll;
        List<Kit.Kpi> kpis = new ArrayList<>();
        Kit.Kpi k1 = new Kit.Kpi("جمع چک‌های جاری", RialFormatter.formatGrouped(totalChecksAll) + " ریال", bankSummaries.size() + " بانک • " + cheques.size() + " فقره", Theme.DANGER); k1.icon = ir.meelano.manager.R.drawable.mi_cheque; kpis.add(k1);
        Kit.Kpi k2 = new Kit.Kpi("پوز در انتظار فردا", RialFormatter.formatGrouped(totalPosAll) + " ریال", posIncludeYesterday ? "امروز+دیروز (قبل ۴ صبح)" : "امروز", Theme.INFO); k2.icon = ir.meelano.manager.R.drawable.mi_credit_card; kpis.add(k2);
        Kit.Kpi k3 = new Kit.Kpi("موجودی ثبت‌شده", RialFormatter.formatGrouped(totalBalanceAll) + " ریال", manualBalances.isEmpty() ? "ثبت نشده" : manualBalances.size() + " بانک", Theme.SUCCESS); k3.icon = ir.meelano.manager.R.drawable.mi_account_balance_wallet; kpis.add(k3);
        String netLabel = netAll >= 0 ? "مازاد" : "کسری";
        int netColor = netAll >= 0 ? Theme.SUCCESS : Theme.DANGER;
        Kit.Kpi k4 = new Kit.Kpi(netLabel + " کل", RialFormatter.formatGrouped(Math.abs(netAll)) + " ریال", netAll >= 0 ? "پوشش کامل ✓" : "نیاز به تامین", netColor); k4.icon = netAll >= 0 ? ir.meelano.manager.R.drawable.mi_trending_up : ir.meelano.manager.R.drawable.mi_trending_down; kpis.add(k4);
        kpis.get(0).subColor = Theme.DANGER; kpis.get(3).subColor = netColor;
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    private void addBankSelector(LinearLayout content) {
        List<String> names = new ArrayList<>(); List<String> rdfIds = new ArrayList<>();
        names.add("همه بانک‌ها"); rdfIds.add("");
        Map<String, String> rdfToName = new HashMap<>();
        for (Row r : banks) rdfToName.put(r.s("id"), r.s("name"));
        for (Row s : bankSummaries) { String rdf = s.s("bankRdf"); String name = s.s("bank"); if (rdf == null || rdf.isEmpty()) continue; if (!rdfToName.containsKey(rdf)) rdfToName.put(rdf, name); }
        for (Row s : bankSummaries) { String rdf = s.s("bankRdf"); String name = s.s("bank"); if (name == null || name.isEmpty() || name.equals(rdf)) name = rdfToName.get(rdf); names.add(name == null || name.isEmpty() ? rdf : name); rdfIds.add(rdf); }
        int selIdx = 0; for (int i = 0; i < rdfIds.size(); i++) if (rdfIds.get(i).equals(selectedBankRdf)) { selIdx = i; break; }
        int[] bankIcons = new int[names.size()]; for (int i = 0; i < bankIcons.length; i++) bankIcons[i] = i == 0 ? ir.meelano.manager.R.drawable.mi_widgets : ir.meelano.manager.R.drawable.mi_account_balance;
        content.addView(a.kit.chipsWithIcons(names.toArray(new String[0]), selIdx, bankIcons, Theme.GOLD, idx -> { selectedBankRdf = rdfIds.get(idx); selectedBankName = names.get(idx); render(content); }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private void addDueWindowChips(LinearLayout content) {
        String[] labels = new String[]{"همه سررسیدها", "۷ روز آینده", "۱۵ روز آینده", "۳۰ روز آینده", "۶۰ روز آینده"};
        int[] days = new int[]{0, 7, 15, 30, 60};
        int[] dueIcons = new int[]{ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month};
        int sel = 0; for (int i = 0; i < days.length; i++) if (days[i] == dueWindowDays) sel = i;
        content.addView(a.kit.chipsWithIcons(labels, sel, dueIcons, Theme.GOLD, idx -> { dueWindowDays = days[idx]; render(content); }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    protected void addSearchRow(LinearLayout parent) {
        String hint = "جست‌وجوی چک، بانک، طرف‌حساب…";
        String init = filter.search == null ? "" : filter.search;
        parent.addView(a.kit.searchBar(hint, init, q -> { filter.search = q; filter.page = 0; render(parent); }), a.kit.lp(-1, -2));
        parent.addView(a.kit.gap(10));
    }

    private void buildAllBanksView(LinearLayout content) {
        LinearLayout bankGrid = a.kit.card(Theme.GOLD);
        bankGrid.setBackground(Theme.glass(Theme.alpha(Theme.SURFACE,240), Theme.alpha(Theme.SURFACE,210), 22, 110));
        Theme.lift(bankGrid, Theme.cardElevation());
        bankGrid.addView(a.kit.sectionHead("چک‌های جاری به تفکیک بانک", null, null), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.gap(6));
        for (Row s : bankSummaries) {
            String rdf = s.s("bankRdf"); String bank = s.s("bank"); long cnt = s.l("count"); double total = s.d("total");
            long balance = manualBalances.getOrDefault(rdf, 0L); Double posD = posByBankCache.get(rdf); long pos = posD == null ? 0L : Math.round(posD); long net = balance + pos - Math.round(total); int netColor = net >= 0 ? Theme.SUCCESS : Theme.DANGER;
            LinearLayout row = a.kit.h(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10)); row.setBackground(Theme.cardAccent(netColor));
            View badge = a.kit.icon(ir.meelano.manager.R.drawable.mi_account_balance, netColor, 22); badge.setBackground(Theme.avatar(Theme.alpha(netColor, 32))); badge.setPadding(Theme.dp(6), Theme.dp(6), Theme.dp(6), Theme.dp(6));
            row.addView(badge, new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38))); row.addView(a.kit.space(8));
            LinearLayout copy = a.kit.v(); TextView bn = a.kit.text(bank, 13f, Theme.TEXT, true); bn.setSingleLine(true); copy.addView(bn, a.kit.lp(-1, -2));
            TextView meta = a.kit.text(Money.fa(String.valueOf(cnt)) + " فقره • موجودی " + RialFormatter.formatGrouped(balance) + " ریال • پوز " + RialFormatter.formatGrouped(pos) + " ریال", 10f, Theme.MUTED, false); copy.addView(meta, a.kit.lp(-1, -2));
            TextView netTv = a.kit.text((net >= 0 ? "مازاد " : "کسری ") + RialFormatter.formatRial(Math.abs(net)), 11f, netColor, true); copy.addView(netTv, a.kit.lp(-1, -2));
            row.addView(copy, a.kit.wlp(1f));
            LinearLayout side = a.kit.v(); side.setGravity(Gravity.END); side.addView(a.kit.text(RialFormatter.formatRial(total), 13f, Theme.GOLD_SOFT, true), a.kit.lp(-2, -2)); View pill = a.kit.pill(net >= 0 ? "✓ پوشش" : "⚠ کسری", netColor); side.addView(pill, a.kit.lp(-2, -2)); row.addView(side, a.kit.lp(-2, -2));
            Theme.pressable(row); final String frdf = rdf; final String fname = bank; row.setOnClickListener(v -> { selectedBankRdf = frdf; selectedBankName = fname; render(content); });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2); p.setMargins(0, 0, 0, Theme.dp(8)); bankGrid.addView(row, p);
        }
        long grandChecks = 0; for (Row s : bankSummaries) grandChecks += Math.round(s.d("total")); long grandPos = 0; for (double v : posByBankCache.values()) grandPos += Math.round(v); long grandBal = 0; for (long v : manualBalances.values()) grandBal += v; long grandNet = grandBal + grandPos - grandChecks;
        bankGrid.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1))); bankGrid.addView(a.kit.gap(6));
        bankGrid.addView(a.kit.kv("جمع کل چک‌ها", RialFormatter.formatRial(grandChecks), Theme.TEXT), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.kv("جمع پوز در انتظار", RialFormatter.formatRial(grandPos), Theme.INFO), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.kv("جمع موجودی ثبت‌شده", RialFormatter.formatRial(grandBal), Theme.SUCCESS), a.kit.lp(-1, -2));
        int grandColor = grandNet >= 0 ? Theme.SUCCESS : Theme.DANGER; bankGrid.addView(a.kit.kv(grandNet >= 0 ? "مازاد کل" : "کسری کل", RialFormatter.formatRial(Math.abs(grandNet)), grandColor), a.kit.lp(-1, -2));
        a.kit.addCard(content, bankGrid);
        if (!cheques.isEmpty()) {
            LinearLayout chequesCard = a.kit.card(Theme.DANGER);
            chequesCard.addView(a.kit.sectionHead("فهرست چک‌ها", null, null), a.kit.lp(-1,-2));
            chequesCard.addView(a.kit.text("تیک هر چک را برای محاسبه بردارید/بگذارید", 12f, Theme.TEXT, true), a.kit.lp(-1, -2));
            chequesCard.addView(a.kit.hint("تیک‌دار = در تامین محاسبه می‌شود • بدون تیک = حذف شد"), a.kit.lp(-1, -2)); chequesCard.addView(a.kit.gap(8));
            String lastBank = ""; int limit = Math.min(cheques.size(), 40);
            for (int ci=0; ci<limit; ci++) { Row r = cheques.get(ci); String bank = r.s("bank"); if (!bank.equals(lastBank)) { lastBank = bank; TextView hdr = a.kit.text("— " + bank + " —", 11f, Theme.GOLD_SOFT, true); hdr.setGravity(Gravity.CENTER); hdr.setPadding(Theme.dp(4), Theme.dp(8), Theme.dp(4), Theme.dp(4)); chequesCard.addView(hdr, a.kit.lp(-1, -2)); } chequesCard.addView(singleChequeRowWithCheck(r, content), a.kit.lp(-1, -2)); }
            if (cheques.size()>limit) chequesCard.addView(a.kit.hint("و " + Money.fa(String.valueOf(cheques.size()-limit))+" چک دیگر… برای دیدن همه، بانک را انتخاب کنید"), a.kit.lp(-1,-2));
            Calculation calc = calcForCheques(cheques, "", null); chequesCard.addView(a.kit.gap(8)); chequesCard.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1))); chequesCard.addView(a.kit.kv("جمع انتخاب‌شده", RialFormatter.formatRial(calc.includedTotal), Theme.TEXT), a.kit.lp(-1, -2)); if (calc.excludedTotal > 0) chequesCard.addView(a.kit.kv("حذف‌شده از محاسبه", RialFormatter.formatRial(calc.excludedTotal), Theme.MUTED), a.kit.lp(-1, -2));
            a.kit.addCard(content, chequesCard);
        }
    }

    private void buildSingleBankView(LinearLayout content) {
        Row summary = null; for (Row s : bankSummaries) if (s.s("bankRdf").equals(selectedBankRdf)) { summary = s; break; }
        Calculation calc = calcForCheques(cheques, selectedBankRdf, null); long includedChecks = calc.includedTotal; long excludedChecks = calc.excludedTotal;
        LinearLayout balCard = a.kit.card(Theme.SUCCESS);
        balCard.setBackground(Theme.glass(Theme.alpha(Theme.SURFACE,235), Theme.alpha(Theme.SURFACE,210), 22, 110));
        Theme.lift(balCard, Theme.cardElevation());
        LinearLayout balHead = a.kit.h(); balHead.setGravity(Gravity.CENTER_VERTICAL);
        balHead.addView(a.kit.icon(ir.meelano.manager.R.drawable.mi_account_balance_wallet, Theme.SUCCESS, 26), a.kit.lp(Theme.dp(30), Theme.dp(30))); balHead.addView(a.kit.space(8));
        LinearLayout balCopy = a.kit.v(); balCopy.addView(a.kit.text("موجودی لحظه‌ای: " + selectedBankName, 14f, Theme.TEXT, true), a.kit.lp(-1, -2)); balCopy.addView(a.kit.hint("ریال • ۳ رقم جدا در تایپ و نمایش — ذخیره خودکار روی دستگاه"), a.kit.lp(-1, -2)); balHead.addView(balCopy, a.kit.wlp(1f)); balCard.addView(balHead, a.kit.lp(-1, -2)); balCard.addView(a.kit.gap(8));
        long currentBal = manualBalances.getOrDefault(selectedBankRdf, BankBalanceStore.getBalanceRial(a, selectedBankRdf));
        final EditText balInput = a.kit.editLux("مثلاً ۱٬۲۵۰٬۰۰۰٬۰۰۰ ریال", currentBal > 0 ? RialFormatter.formatGrouped(currentBal) : ""); balInput.setInputType(InputType.TYPE_CLASS_NUMBER); if (currentBal > 0) balInput.setText(RialFormatter.formatGrouped(currentBal)); balInput.setTag(false);
        balInput.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} @Override public void onTextChanged(CharSequence s, int start, int before, int count) {} @Override public void afterTextChanged(Editable s) { if (Boolean.TRUE.equals(balInput.getTag())) return; String raw = s.toString(); long parsed = RialFormatter.parseRial(raw); String formatted = parsed == 0 ? "" : RialFormatter.formatGrouped(parsed); if (!formatted.equals(raw)) { balInput.setTag(true); balInput.setText(formatted); balInput.setSelection(formatted.length()); balInput.setTag(false); } BankBalanceStore.setBalanceRial(a, selectedBankRdf, parsed); manualBalances.put(selectedBankRdf, parsed); }});
        balCard.addView(balInput, a.kit.lp(-1, -2));
        LinearLayout presets = a.kit.h(); long[] vals = new long[]{500_000_000L, 1_000_000_000L, 2_000_000_000L, 5_000_000_000L}; for (long v : vals) { final long fv = v; TextView chip = a.kit.text(RialFormatter.formatGrouped(v), 10f, Theme.GOLD, true); chip.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6)); chip.setBackground(Theme.ghostButton(Theme.GOLD)); Theme.pressable(chip); chip.setOnClickListener(x -> { balInput.setText(RialFormatter.formatGrouped(fv)); balInput.setSelection(balInput.getText().length()); }); LinearLayout.LayoutParams lp = a.kit.lp(-2, -2); lp.setMargins(0, 0, Theme.dp(6), 0); presets.addView(chip, lp); }
        balCard.addView(a.kit.gap(6)); balCard.addView(presets, a.kit.lp(-1, -2));
        if (BankBalanceStore.getBalanceTime(a, selectedBankRdf) > 0) { long ts = BankBalanceStore.getBalanceTime(a, selectedBankRdf); balCard.addView(a.kit.hint("آخرین ویرایش: " + formatAgo(ts) + " • روی دستگاه شما ذخیره شد"), a.kit.lp(-1, -2)); }
        a.kit.addCard(content, balCard);
        LinearLayout chkCard = a.kit.card(Theme.DANGER);
        chkCard.addView(a.kit.sectionHead("چک‌های پرداختی این بانک", null, null), a.kit.lp(-1,-2));
        chkCard.addView(a.kit.text(Money.fa(String.valueOf(cheques.size())) + " فقره • تیک هر چک را بردارید اگر نباید محاسبه شود", 11f, Theme.MUTED, false), a.kit.lp(-1,-2)); chkCard.addView(a.kit.gap(8));
        if (cheques.isEmpty()) chkCard.addView(a.kit.empty("چکی یافت نشد", "چک جاری برای این بانک ثبت نشده"), a.kit.lp(-1, -2));
        else { int lim = Math.min(cheques.size(), 60); for (int i=0;i<lim;i++) chkCard.addView(singleChequeRowWithCheck(cheques.get(i), content), a.kit.lp(-1, -2)); if (cheques.size()>lim) chkCard.addView(a.kit.hint("و "+Money.fa(String.valueOf(cheques.size()-lim))+" چک دیگر…"), a.kit.lp(-1,-2)); chkCard.addView(a.kit.gap(6)); chkCard.addView(a.kit.kv("جمع انتخاب‌شده", RialFormatter.formatRial(includedChecks), Theme.DANGER), a.kit.lp(-1, -2)); if (excludedChecks > 0) chkCard.addView(a.kit.kv("حذف‌شده", RialFormatter.formatRial(excludedChecks), Theme.MUTED), a.kit.lp(-1, -2)); chkCard.addView(a.kit.kv("تعداد انتخاب‌شده", Money.fa(String.valueOf(countIncluded(cheques))) + " از " + Money.fa(String.valueOf(cheques.size())) + " فقره", Theme.TEXT), a.kit.lp(-1, -2)); }
        a.kit.addCard(content, chkCard);
        LinearLayout posCard = a.kit.card(Theme.INFO);
        posCard.addView(a.kit.sectionHead("پوزِ در انتظار واریز", null, null), a.kit.lp(-1,-2));
        posCard.addView(a.kit.text("ساعت ۴ صبح فردا به این حساب واریز می‌شود", 11f, Theme.MUTED, false), a.kit.lp(-1,-2)); posCard.addView(a.kit.gap(8));
        long posTotal = posSummary == null ? 0L : Math.round(posSummary.d("total")); long posCount = posSummary == null ? 0L : posSummary.l("count");
        if (posTotal == 0) posCard.addView(a.kit.kv("مبلغ پوز در انتظار", "۰ ریال — امروز دریافتی کارت‌خوانی برای این بانک ثبت نشده", Theme.MUTED), a.kit.lp(-1, -2));
        else { posCard.addView(a.kit.kv("جمع پوز", RialFormatter.formatRial(posTotal) + "  •  " + Money.fa(String.valueOf(posCount)) + " تراکنش", Theme.INFO), a.kit.lp(-1, -2)); posCard.addView(a.kit.hint("زمان واریز: فردا ساعت ۰۴:۰۰ توسط بانک"), a.kit.lp(-1, -2)); posCard.addView(a.kit.gap(8)); int show = Math.min(posList.size(), 10); for (int i = 0; i < show; i++) { Row r = posList.get(i); LinearLayout rRow = a.kit.h(); rRow.setGravity(Gravity.CENTER_VERTICAL); rRow.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8)); rRow.setBackground(Theme.card()); LinearLayout copy = a.kit.v(); copy.addView(a.kit.text(r.s("customer"), 12f, Theme.TEXT, true), a.kit.lp(-1, -2)); copy.addView(a.kit.text("قبض " + Money.fa(r.s("ghno")) + " • " + Jalali.dispFa(r.s("vdate")) + (r.s("tracking").isEmpty() ? "" : " • پیگیری " + Money.fa(r.s("tracking"))), 10f, Theme.MUTED, false), a.kit.lp(-1, -2)); rRow.addView(copy, a.kit.wlp(1f)); rRow.addView(a.kit.text(RialFormatter.formatRial(r.d("amount")), 11f, Theme.INFO, true), a.kit.lp(-2, -2)); LinearLayout.LayoutParams p = a.kit.lp(-1, -2); p.setMargins(0, 0, 0, Theme.dp(6)); posCard.addView(rRow, p); } if (posList.size() > show) posCard.addView(a.kit.hint("و " + Money.fa(String.valueOf(posList.size() - show)) + " تراکنش دیگر…"), a.kit.lp(-1, -2)); }
        a.kit.addCard(content, posCard);
        addCalculationCard(content, selectedBankRdf, includedChecks, posTotal, currentBal);
        addDueListForBank(content, cheques);
    }

    private void addCalculationCard(LinearLayout parent, String bankRdf, long checks, long pos, long balance) {
        long available = balance + pos; long net = available - checks; boolean surplus = net >= 0; int color = surplus ? Theme.SUCCESS : Theme.DANGER;
        LinearLayout card = a.kit.card(color);
        LinearLayout hHead = a.kit.h(); hHead.setGravity(Gravity.CENTER_VERTICAL);
        hHead.addView(a.kit.icon(surplus ? ir.meelano.manager.R.drawable.mi_trending_up : ir.meelano.manager.R.drawable.mi_warning, color, 26), a.kit.lp(Theme.dp(30), Theme.dp(30))); hHead.addView(a.kit.space(8));
        hHead.addView(a.kit.text(surplus ? "✓ موجودی کافی است — مازاد" : "⚠ کسری موجودی — نیاز به تامین", 14f, color, true), a.kit.wlp(1f)); hHead.addView(a.kit.pill(surplus ? "تامین OK" : "نیاز فوری", color), a.kit.lp(-2, -2));
        card.addView(hHead, a.kit.lp(-1, -2)); card.addView(a.kit.gap(8));
        LinearLayout formula = a.kit.v(); formula.setBackground(Theme.card()); Theme.lift(formula, Theme.cardElevation()); formula.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        formula.addView(a.kit.kv("موجودی لحظه‌ای", RialFormatter.formatRial(balance), Theme.SUCCESS), a.kit.lp(-1, -2)); TextView plus = a.kit.text("+", 18f, Theme.GOLD, true); plus.setGravity(Gravity.CENTER); formula.addView(plus, a.kit.lp(-1, -2));
        formula.addView(a.kit.kv("پوز در انتظار (۴ صبح فردا)", RialFormatter.formatRial(pos), Theme.INFO), a.kit.lp(-1, -2)); formula.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1))); formula.addView(a.kit.kv("منابع در دسترس", RialFormatter.formatRial(available), Theme.TEXT), a.kit.lp(-1, -2));
        TextView minus = a.kit.text("−", 18f, Theme.DANGER, true); minus.setGravity(Gravity.CENTER); formula.addView(minus, a.kit.lp(-1, -2)); formula.addView(a.kit.kv("چک‌های انتخاب‌شده", RialFormatter.formatRial(checks), Theme.DANGER), a.kit.lp(-1, -2)); formula.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
        TextView netTv = a.kit.text((surplus ? "مازاد: " : "کسری: ") + RialFormatter.formatRial(Math.abs(net)), 16f, color, true); netTv.setGravity(Gravity.CENTER); netTv.setPadding(Theme.dp(8), Theme.dp(10), Theme.dp(8), Theme.dp(10)); netTv.setBackground(Theme.pill(color)); formula.addView(netTv, a.kit.lp(-1, -2));
        String pct = checks > 0 ? Money.fa(String.format(java.util.Locale.US, "%.1f", Math.abs(net) * 100.0 / checks).replace('.', '٫')) + "٪ از چک‌ها" : ""; if (!pct.isEmpty()) formula.addView(a.kit.hint(surplus ? "مازاد " + pct : "کسری " + pct), a.kit.lp(-1, -2));
        card.addView(formula, a.kit.lp(-1, -2)); double cov = checks > 0 ? Math.min(1.0, (double) available / checks) : 1.0; card.addView(a.kit.gap(6)); card.addView(a.kit.progressLine("پوشش چک‌ها", Money.fa(String.valueOf((int)(cov*100))) + "٪", cov, color), a.kit.lp(-1, -2)); card.setTag("calc_" + bankRdf); a.kit.addCard(parent, card);
    }

    private View singleChequeRowWithCheck(final Row r, final LinearLayout parent) {
        String rdf = r.s("rdf"); final String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf; boolean included = !excludedRdfs.contains(key);
        LinearLayout row = a.kit.h(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8)); row.setBackground(included ? Theme.card() : Theme.cardAccent(Theme.MUTED));
        CheckBox cb = new CheckBox(a); cb.setChecked(included); cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Theme.GOLD));
        // بدون ریلود سنگین: فقط مجموعه را عوض کن و جمع را در همان صفحه بروز کن
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> { if (isChecked) excludedRdfs.remove(key); else excludedRdfs.add(key); row.setBackground(included ? Theme.cardAccent(Theme.MUTED) : Theme.card()); row.setAlpha(isChecked?1f:0.62f); });
        row.addView(cb, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        LinearLayout copy = a.kit.v(); copy.addView(a.kit.text("چک " + Money.fa(r.s("num")) + " • سررسید " + Jalali.dispFa(r.s("sardate")), 12f, included ? Theme.TEXT : Theme.MUTED, true), a.kit.lp(-1, -2));
        String who = r.s("customer"); if (who == null || who.equals("—")) who = r.s("girande"); copy.addView(a.kit.text(who == null || who.isEmpty() ? "طرف‌حساب نامشخص" : who, 10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        String sub = ""; if (!r.s("sayad").isEmpty()) sub += "صیادی " + Money.fa(r.s("sayad")); if (!r.s("ghno").isEmpty()) sub += (sub.isEmpty() ? "" : " • ") + "قبض " + Money.fa(r.s("ghno")); if (!sub.isEmpty()) copy.addView(a.kit.text(sub, 9.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        LinearLayout side = a.kit.v(); side.setGravity(Gravity.END); side.addView(a.kit.text(RialFormatter.formatRial(r.d("amount")), 12f, included ? Theme.DANGER : Theme.MUTED, true), a.kit.lp(-2, -2));
        int days = Jalali.diffDays(Jalali.todayStr(), Jalali.disp(r.s("sardate"))); String daysTxt; int daysColor; if (days < 0) { daysTxt = "سررسید گذشته " + Money.fa(String.valueOf(-days)) + " روز"; daysColor = Theme.DANGER; } else if (days == 0) { daysTxt = "امروز سررسید"; daysColor = Theme.WARNING; } else if (days <= 3) { daysTxt = Money.fa(String.valueOf(days)) + " روز مانده ⚠"; daysColor = Theme.WARNING; } else { daysTxt = Money.fa(String.valueOf(days)) + " روز مانده"; daysColor = Theme.MUTED; } side.addView(a.kit.text(daysTxt, 10f, daysColor, false), a.kit.lp(-2, -2)); row.addView(side, a.kit.lp(-2, -2));
        LinearLayout.LayoutParams p = a.kit.lp(-1, -2); p.setMargins(0, 0, 0, Theme.dp(6)); LinearLayout wrap = a.kit.v(); wrap.addView(row, a.kit.lp(-1, -2)); wrap.setLayoutParams(p); return wrap;
    }

    private void addDueListForBank(LinearLayout content, List<Row> list) {
        if (list == null || list.isEmpty()) return;
        List<Row> sorted = new ArrayList<>(list); Collections.sort(sorted, (a,b) -> Jalali.disp(a.s("sardate")).compareTo(Jalali.disp(b.s("sardate"))));
        LinearLayout card = a.kit.card(Theme.GOLD); card.addView(a.kit.sectionHead("نزدیک‌ترین سررسیدها", null, null), a.kit.lp(-1,-2)); card.addView(a.kit.gap(6)); int n = Math.min(5, sorted.size()); for (int i = 0; i < n; i++) { Row r = sorted.get(i); LinearLayout rr = a.kit.h(); rr.setGravity(Gravity.CENTER_VERTICAL); rr.addView(a.kit.text(Jalali.dispFa(r.s("sardate")), 11f, Theme.GOLD_SOFT, true), a.kit.lp(Theme.dp(90), -2)); rr.addView(a.kit.text("چک " + Money.fa(r.s("num")), 11f, Theme.TEXT, false), a.kit.wlp(1f)); rr.addView(a.kit.text(RialFormatter.formatRial(r.d("amount")), 11f, Theme.TEXT, true), a.kit.lp(-2, -2)); card.addView(rr, a.kit.lp(-1, -2)); } a.kit.addCard(content, card);
    }

    private void addCrossBankIntelligence(LinearLayout content) {
        if (bankSummaries.isEmpty()) return; String sel = selectedBankRdf;
        if (sel == null || sel.isEmpty()) {
            List<BankNet> nets = computeAllBankNets(); boolean anyDeficit=false, anySurplus=false; for (BankNet n : nets) { if (n.net < 0) anyDeficit=true; if (n.net > 0) anySurplus=true; }
            if (anyDeficit && anySurplus) {
                LinearLayout card = a.kit.card(Theme.INFO); card.addView(a.kit.sectionHead("پیشنهاد هوشمند انتقال بین بانکی", null, null), a.kit.lp(-1,-2)); card.addView(a.kit.hint("کسری یک بانک را می‌توان از مازاد بانک دیگر تامین کرد"), a.kit.lp(-1, -2)); card.addView(a.kit.gap(8)); Collections.sort(nets, (a,b) -> Long.compare(a.net, b.net)); for (BankNet n : nets) if (n.net < 0) card.addView(a.kit.kv(n.bankName + " — کسری", RialFormatter.formatRial(Math.abs(n.net)), Theme.DANGER), a.kit.lp(-1, -2)); for (BankNet n : nets) if (n.net > 0) card.addView(a.kit.kv(n.bankName + " — مازاد", RialFormatter.formatRial(n.net), Theme.SUCCESS), a.kit.lp(-1, -2)); card.addView(a.kit.gap(6));
                for (BankNet deficit : nets) if (deficit.net < 0) { long need = -deficit.net; for (BankNet surplus : nets) if (surplus.net > 0 && need > 0) { long give = Math.min(need, surplus.net); LinearLayout sug = a.kit.h(); sug.setGravity(Gravity.CENTER_VERTICAL); sug.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6)); sug.setBackground(Theme.card()); sug.addView(a.kit.text("انتقال " + RialFormatter.formatRial(give) + " از «" + surplus.bankName + "» به «" + deficit.bankName + "»", 11f, Theme.TEXT, false), a.kit.wlp(1f)); final long fgive=give; final String ffrom=surplus.bankName, fto=deficit.bankName; TextView btn = a.kit.text("شبیه‌سازی", 10f, Theme.INFO, true); btn.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4)); btn.setBackground(Theme.ghostButton(Theme.INFO)); Theme.pressable(btn); btn.setOnClickListener(v -> a.kit.toast("شبیه‌سازی: " + RialFormatter.formatRial(fgive) + " از " + ffrom + " به " + fto + " منتقل شد (محاسباتی)")); sug.addView(btn, a.kit.lp(-2, -2)); card.addView(sug, a.kit.lp(-1, -2)); need -= give; surplus.net -= give; } }
                a.kit.addCard(content, card);
            }
        } else {
            Calculation selCalc = calcForCheques(cheques, sel, null); long checks = selCalc.includedTotal; long pos = posSummary == null ? 0L : Math.round(posSummary.d("total")); long bal = manualBalances.getOrDefault(sel, 0L); long net = bal + pos - checks;
            if (net < 0) { List<BankNet> others = computeAllBankNetsExcluding(sel); List<BankNet> surplusBanks = new ArrayList<>(); for (BankNet n : others) if (n.net > 0) surplusBanks.add(n); if (!surplusBanks.isEmpty()) { Collections.sort(surplusBanks, (a,b) -> Long.compare(b.net, a.net)); LinearLayout card = a.kit.card(Theme.WARNING); card.addView(a.kit.text("💡 تامین از بانک‌های دیگر", 13f, Theme.WARNING, true), a.kit.lp(-1, -2)); card.addView(a.kit.kv("کسری این بانک", RialFormatter.formatRial(Math.abs(net)), Theme.DANGER), a.kit.lp(-1, -2)); card.addView(a.kit.gap(6)); long need = -net; for (BankNet s : surplusBanks) { long canGive = Math.min(need, s.net); card.addView(a.kit.kv(s.bankName + " — مازاد قابل انتقال", RialFormatter.formatRial(canGive) + " (از " + RialFormatter.formatRial(s.net) + ")", Theme.SUCCESS), a.kit.lp(-1, -2)); need -= canGive; if (need <= 0) break; } if (need > 0) card.addView(a.kit.hint("حتی پس از انتقال، همچنان " + RialFormatter.formatRial(need) + " کسری باقی می‌ماند"), a.kit.lp(-1, -2)); a.kit.addCard(content, card); }
            } else if (net > 0) { LinearLayout card = a.kit.card(Theme.SUCCESS); card.addView(a.kit.text("✓ موجودی این بانک کافی است", 13f, Theme.SUCCESS, true), a.kit.lp(-1, -2)); card.addView(a.kit.kv("مازاد پس از پرداخت همه چک‌ها", RialFormatter.formatRial(net), Theme.SUCCESS), a.kit.lp(-1, -2)); a.kit.addCard(content, card); }
        }
    }

    private List<BankNet> computeAllBankNets() { List<BankNet> out = new ArrayList<>(); for (Row s : bankSummaries) { BankNet n = new BankNet(); n.bankRdf = s.s("bankRdf"); n.bankName = s.s("bank"); n.checksTotal = Math.round(s.d("total")); long excludedForThisBank = 0; for (Row c : cheques) { String crdf = c.s("bankRdf"); if (!n.bankRdf.equals(crdf)) continue; String rdf = c.s("rdf"); String key = (rdf == null || rdf.isEmpty()) ? c.s("num") + "|" + c.s("amount") + "|" + c.s("sardate") : rdf; if (excludedRdfs.contains(key)) excludedForThisBank += Math.round(c.d("amount")); } n.checksTotal -= excludedForThisBank; Double posD = posByBankCache.get(n.bankRdf); n.posTotal = posD == null ? 0L : Math.round(posD); if (n.bankRdf.equals(selectedBankRdf) && posSummary != null) n.posTotal = Math.round(posSummary.d("total")); n.balance = manualBalances.getOrDefault(n.bankRdf, 0L); n.net = n.balance + n.posTotal - n.checksTotal; n.count = (int) s.l("count"); out.add(n); } return out; }
    private List<BankNet> computeAllBankNetsExcluding(String excludeRdf) { List<BankNet> all = computeAllBankNets(); List<BankNet> out = new ArrayList<>(); for (BankNet n : all) if (!n.bankRdf.equals(excludeRdf)) out.add(n); return out; }

    // --- Lazy overdue (only load on demand) ---
    private void addOverdueSectionLazy(LinearLayout content) {
        if (overdue != null && !overdue.isEmpty()) { addOverdueSection(content); return; }
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.sectionHead("مشتریان معوق — فرصت تامین", null, null), a.kit.lp(-1,-2));
        card.addView(a.kit.hint("برای کاهش کسری، از محل وصول مطالبات معوق تامین کنید"), a.kit.lp(-1,-2)); card.addView(a.kit.gap(8));
        card.addView(a.kit.btn("نمایش مشتریان معوق", v -> loadOverdueLazy(content)), a.kit.lp(-1,-2));
        a.kit.addCard(content, card);
    }
    private void loadOverdueLazy(final LinearLayout content) {
        if (overdueLoading) return; overdueLoading = true;
        a.repo.run(c -> { Meta m = new Meta(c); return Repo.exec(c, ChequeLiquidityQueries.overdueForLiquidity(m, 20)); }, new Repo.Cb<List<Row>>() {
            @Override public void ok(List<Row> r) { overdue = r == null? new ArrayList<>(): r; overdueLoading=false; build(content); loadBehaviorsLazy(); }
            @Override public void fail(String e) { overdueLoading=false; a.kit.toast(e); }
        });
    }
    private void addOverdueSection(LinearLayout content) {
        if (overdue == null || overdue.isEmpty()) return;
        LinearLayout card = a.kit.card(Theme.VIOLET); card.addView(a.kit.sectionHead("مشتریان معوق — فرصت تامین از وصول", null, null), a.kit.lp(-1,-2)); card.addView(a.kit.hint("الگوی پرداخت هر مشتری تحلیل شده است"), a.kit.lp(-1, -2)); card.addView(a.kit.gap(8));
        int show = Math.min(overdue.size(), 5); for (int i = 0; i < show; i++) { Row o = overdue.get(i); String code = o.s("code"); String party = o.s("party"); double amt = o.d("amount"); String due = o.s("dueDate"); long days = o.l("days"); if (days < 0) days = Jalali.diffDays(due, Jalali.todayStr()); BehaviorAnalysis beh = findBehavior(code); LinearLayout row = a.kit.v(); row.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10)); row.setBackground(Theme.card()); LinearLayout top = a.kit.h(); top.setGravity(Gravity.CENTER_VERTICAL); LinearLayout copy = a.kit.v(); copy.addView(a.kit.text(party, 13f, Theme.TEXT, true), a.kit.lp(-1, -2)); String meta = "فاکتور " + Money.fa(o.s("invoice")) + " • سررسید " + Jalali.dispFa(due); if (days >= 0) meta += " • " + Money.fa(String.valueOf(days)) + " روز تاخیر"; copy.addView(a.kit.text(meta, 10f, Theme.MUTED, false), a.kit.lp(-1, -2)); top.addView(copy, a.kit.wlp(1f)); top.addView(a.kit.text(RialFormatter.formatRial(amt), 12f, Theme.TEXT, true), a.kit.lp(-2, -2)); row.addView(top, a.kit.lp(-1, -2));
            if (beh != null) { LinearLayout behRow = a.kit.h(); behRow.setGravity(Gravity.CENTER_VERTICAL); behRow.addView(a.kit.pill(beh.patternLabel, beh.patternColor), a.kit.lp(-2, -2)); behRow.addView(a.kit.space(6)); String detail = "میانگین تاخیر " + Money.fa(String.valueOf(Math.round(beh.avgDelayDays))) + " روز • از " + Money.fa(String.valueOf(beh.sampleCount)) + " تسویه"; behRow.addView(a.kit.text(detail, 10f, Theme.MUTED, false), a.kit.wlp(1f)); behRow.addView(a.kit.progress(beh.reliabilityScore/100.0, beh.patternColor), new LinearLayout.LayoutParams(Theme.dp(60), Theme.dp(6))); row.addView(behRow, a.kit.lp(-1, -2)); long est = estimateCollectible(beh, amt); String estTxt = "وصول تخمینی: " + RialFormatter.formatRial(est); if (beh.reliabilityScore >= 70) row.addView(a.kit.text(estTxt + " ✓ خوش‌حساب", 10.5f, Theme.SUCCESS, true), a.kit.lp(-1, -2)); else if (beh.reliabilityScore >= 40) row.addView(a.kit.text(estTxt + " — پیگیری متوسط", 10.5f, Theme.WARNING, false), a.kit.lp(-1, -2)); else row.addView(a.kit.text("ریسک بالا — " + Money.fa(String.valueOf(Math.round(beh.avgDelayDays))) + " روز تاخیر میانگین", 10.5f, Theme.DANGER, false), a.kit.lp(-1, -2)); } else row.addView(a.kit.hint("تحلیل رفتاری: لمس کنید تا محاسبه شود"), a.kit.lp(-1, -2));
            LinearLayout actions = a.kit.h(); actions.setGravity(Gravity.END); final String fcode = code; TextView btn = a.kit.text("📞 تماس/پیگیری", 10f, Theme.VIOLET, true); btn.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4)); btn.setBackground(Theme.ghostButton(Theme.VIOLET)); Theme.pressable(btn); btn.setOnClickListener(v -> { ir.meelano.manager.screens.Screen cs = a.screen("customers"); if (cs instanceof CustomersScreen) ((CustomersScreen) cs).openDossier(fcode); a.nav("customers"); }); actions.addView(btn, a.kit.lp(-2, -2)); row.addView(actions, a.kit.lp(-1, -2)); LinearLayout.LayoutParams p = a.kit.lp(-1, -2); p.setMargins(0, 0, 0, Theme.dp(8)); card.addView(row, p); }
        if (overdue.size() > show) card.addView(a.kit.hint("و " + Money.fa(String.valueOf(overdue.size() - show)) + " مشتری دیگر…"), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }
    private BehaviorAnalysis findBehavior(String code) { for (BehaviorAnalysis b : behaviors) if (b.code.equals(code)) return b; return null; }
    private long estimateCollectible(BehaviorAnalysis beh, double invoiceAmount) { double factor = beh.reliabilityScore >= 70 ? 0.8 : beh.reliabilityScore >= 40 ? 0.4 : 0.15; if (beh.daysOverdue > 60) factor *= 0.5; else if (beh.daysOverdue > 30) factor *= 0.7; return Math.round(invoiceAmount * factor); }
    private void loadBehaviorsLazy() {
        if (overdue == null || overdue.isEmpty()) return;
        int limit = Math.min(6, overdue.size()); List<String> codes = new ArrayList<>(); for (int i = 0; i < limit; i++) codes.add(overdue.get(i).s("code"));
        a.repo.run(c -> { Meta m = new Meta(c); List<BehaviorAnalysis> out = new ArrayList<>(); for (String code : codes) { if (code == null || code.isEmpty()) continue; BehaviorAnalysis b = new BehaviorAnalysis(); b.code = code; for (Row o : overdue) if (code.equals(o.s("code"))) { b.name = o.s("party"); b.overdueAmount = o.d("amount"); b.dueDate = o.s("dueDate"); long dd=o.l("days"); if (dd<0) dd=Jalali.diffDays(b.dueDate, Jalali.todayStr()); b.daysOverdue=(int)dd; break; } try { Row bal = Repo.one(c, ChequeLiquidityQueries.customerBalance(m, code)); b.balance=Math.round(bal.d("balance")); b.credit=Math.round(bal.d("credit")); b.cell=bal.s("cell"); if (b.name==null||b.name.isEmpty()||b.name.equals("—")) b.name=bal.s("name"); } catch(Exception e){} try { List<Row> hist = Repo.exec(c, ChequeLiquidityQueries.customerPaymentHistory(m, code, 30)); if (hist!=null && !hist.isEmpty()) { double totalDelay=0; int valid=0; for (Row h:hist){ String due=h.s("dueDate"); String pay=h.s("payDate"); if(due.isEmpty()||pay.isEmpty()) continue; int delay=Jalali.diffDays(due,pay); if(delay<-1) continue; totalDelay+=Math.max(0,delay); valid++; } if(valid>0){ b.avgDelayDays=totalDelay/valid; b.sampleCount=valid; double d=b.avgDelayDays; if(d<=3) b.reliabilityScore=95-d*2; else if(d<=7) b.reliabilityScore=85-(d-3)*3; else if(d<=15) b.reliabilityScore=65-(d-7)*2; else if(d<=30) b.reliabilityScore=35-(d-15)*1; else b.reliabilityScore=Math.max(5,20-(d-30)*0.3); if(valid<3) b.reliabilityScore*=0.7; if(b.reliabilityScore>=70){b.patternLabel="خوش‌حساب ✓"; b.patternColor=Theme.SUCCESS;} else if(b.reliabilityScore>=40){b.patternLabel="متوسط ◐"; b.patternColor=Theme.WARNING;} else {b.patternLabel="بدحساب ⚠"; b.patternColor=Theme.DANGER;} } else {b.avgDelayDays=-1; b.patternLabel="بدون سابقه تسویه"; b.patternColor=Theme.MUTED; b.reliabilityScore=30;} } else {b.patternLabel="بدون سابقه تسویه کافی"; b.patternColor=Theme.MUTED; b.reliabilityScore=30; b.avgDelayDays=-1;} } catch(Exception e){b.patternLabel="تحلیل ممکن نشد"; b.patternColor=Theme.MUTED; b.reliabilityScore=30;} out.add(b);} Collections.sort(out, (x,y)->{int cmp=Double.compare(y.reliabilityScore,x.reliabilityScore); if(cmp!=0) return cmp; return Double.compare(y.overdueAmount,x.overdueAmount);}); return out; }, new Repo.Cb<List<BehaviorAnalysis>>() { @Override public void ok(List<BehaviorAnalysis> res) { behaviors.clear(); if(res!=null) behaviors.addAll(res); try{ LinearLayout cur=findContentParent(); if(cur!=null) build(cur);}catch(Exception ignored){} } @Override public void fail(String e){} });
    }

    private void addCalendarHeatmap(LinearLayout content) {
        if (cheques == null || cheques.isEmpty()) return;
        Map<String, List<Row>> byDay = new LinkedHashMap<>(); Map<String, Long> sumByDay = new LinkedHashMap<>();
        for (Row r : cheques) { String d = Jalali.disp(r.s("sardate")); if (d.isEmpty()) continue; if (!byDay.containsKey(d)) { byDay.put(d, new ArrayList<>()); sumByDay.put(d, 0L); } byDay.get(d).add(r); sumByDay.put(d, sumByDay.get(d) + Math.round(r.d("amount"))); }
        if (byDay.isEmpty()) return; List<String> sortedDays = new ArrayList<>(byDay.keySet()); Collections.sort(sortedDays); long max=0; for(long v:sumByDay.values()) max=Math.max(max,v);
        LinearLayout card = a.kit.card(Theme.GOLD); card.addView(a.kit.sectionHead("تقویم سررسید — توزیع چک‌ها", null, null), a.kit.lp(-1,-2)); card.addView(a.kit.hint("روزهای شلوغ نیاز به تامین بیشتری دارند"), a.kit.lp(-1, -2)); card.addView(a.kit.gap(8)); String today = Jalali.todayStr(); List<String> upcoming=new ArrayList<>(); for(String d:sortedDays) if(d.compareTo(today)>=0) upcoming.add(d); if(upcoming.isEmpty()) upcoming=sortedDays; int show=Math.min(10, upcoming.size()); for(int i=0;i<show;i++){ String d=upcoming.get(i); long sum=sumByDay.get(d); int cnt=byDay.get(d).size(); LinearLayout row=a.kit.h(); row.setGravity(Gravity.CENTER_VERTICAL); row.addView(a.kit.text(Jalali.dispFa(d)+" • "+Jalali.weekday(d),11f,Theme.TEXT,true), a.kit.lp(Theme.dp(120),-2)); float frac=max>0?(float)sum/max:0f; View bar=a.kit.progress(frac, Theme.GOLD); LinearLayout.LayoutParams bp=a.kit.lp(0,Theme.dp(14)); bp.weight=1f; row.addView(bar,bp); row.addView(a.kit.space(6)); row.addView(a.kit.text(RialFormatter.formatRial(sum)+" • "+Money.fa(String.valueOf(cnt))+" فقره",10f,Theme.TEXT,false), a.kit.lp(-2,-2)); card.addView(row,a.kit.lp(-1,-2)); if(i<show-1) card.addView(a.kit.gap(4)); } if(upcoming.size()>show) card.addView(a.kit.hint("و "+Money.fa(String.valueOf(upcoming.size()-show))+" روز دیگر…"),a.kit.lp(-1,-2)); String peakDay=null; long peakSum=0; for(Map.Entry<String,Long> e:sumByDay.entrySet()) if(e.getValue()>peakSum){peakSum=e.getValue(); peakDay=e.getKey();} if(peakDay!=null&&peakSum>0){ card.addView(a.kit.gap(8)); LinearLayout warn=a.kit.h(); warn.setBackground(Theme.pill(Theme.WARNING)); warn.setPadding(Theme.dp(8),Theme.dp(6),Theme.dp(8),Theme.dp(6)); warn.addView(a.kit.text("روز اوج: "+Jalali.dispFa(peakDay)+" — "+RialFormatter.formatRial(peakSum),11f,Theme.WARNING,true),a.kit.wlp(1f)); card.addView(warn,a.kit.lp(-1,-2)); } a.kit.addCard(content, card);
    }

    private void addProfessionalSuggestions(LinearLayout content, String bankRdf) {
        LinearLayout card = a.kit.card(Theme.GOLD_SOFT); card.addView(a.kit.text("✦ پیشنهادهای حرفه‌ای تکمیلی", 14f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2)); card.addView(a.kit.gap(6));
        card.addView(suggestionRow("۱. تمدید هوشمند سررسید","چک‌هایی که سررسیدشان در ۳ روز آینده و مبلغشان بالاست را شناسایی کنید؛ با طرف‌حساب برای تمدید ۷–۱۵ روزه مذاکره کنید تا فشار نقدینگی پخش شود.",Theme.INFO, v -> showRescheduleDialog()), a.kit.lp(-1, -2));
        card.addView(suggestionRow("۲. اولویت‌بندی پرداخت","چک‌ها را بر اساس «اهمیت طرف‌حساب × نزدیکی سررسید × مبلغ» امتیازدهی کنید.",Theme.GOLD, null), a.kit.lp(-1, -2));
        card.addView(suggestionRow("۳. صندوق نقدی و تنخواه","موجودی صندوق‌های نقدی (COW) را هم در محاسبه بیاورید.",Theme.SUCCESS, null), a.kit.lp(-1, -2));
        card.addView(suggestionRow("۴. خط اعتباری کوتاه‌مدت","برای بانک‌های مکرر کسری، سقف اضافه‌برداشت موقت تعریف کنید.",Theme.VIOLET, null), a.kit.lp(-1, -2));
        card.addView(suggestionRow("۵. یادآور خودکار","برای هر چک ۴۸ ساعت قبل، یادآور تامین به مدیر ارسال شود.",Theme.WARNING, null), a.kit.lp(-1, -2));
        LinearLayout actions = a.kit.h(); actions.addView(a.kit.btn("📋 گزارش تامین", v -> shareLiquidityReport()), a.kit.wlp(1f)); actions.addView(a.kit.space(8)); actions.addView(a.kit.btnGhost("پاک‌کردن موجودی‌ها", Theme.DANGER, v -> confirmClearBalances()), a.kit.wlp(1f)); card.addView(a.kit.gap(8)); card.addView(actions, a.kit.lp(-1, -2)); a.kit.addCard(content, card);
    }
    private View suggestionRow(String title, String body, int accent, View.OnClickListener onClick) { LinearLayout r = a.kit.v(); r.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8)); r.setBackground(Theme.cardAccent(accent)); r.addView(a.kit.text(title, 12f, accent, true), a.kit.lp(-1, -2)); r.addView(a.kit.text(body, 10.5f, Theme.MUTED, false), a.kit.lp(-1, -2)); if (onClick != null) { Theme.pressable(r); r.setOnClickListener(onClick); } LinearLayout.LayoutParams p = a.kit.lp(-1, -2); p.setMargins(0, 0, 0, Theme.dp(8)); r.setLayoutParams(p); return r; }
    private void showRescheduleDialog() { if (cheques == null || cheques.isEmpty()) { a.kit.toast("چکی برای پیشنهاد تمدید یافت نشد"); return; } String today = Jalali.todayStr(); List<Row> urgent = new ArrayList<>(); for (Row r : cheques) { String sd = Jalali.disp(r.s("sardate")); if (sd.isEmpty()) continue; int diff = Jalali.diffDays(today, sd); if (diff >= 0 && diff <= 7) urgent.add(r); } if (urgent.isEmpty()) { a.kit.toast("چک فوری برای ۷ روز آینده یافت نشد — وضعیت آرام است"); return; } Collections.sort(urgent, (x,y) -> Double.compare(y.d("amount"), x.d("amount"))); LinearLayout body = a.kit.v(); body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16)); body.addView(a.kit.text("چک‌های پیشنهادی برای تمدید (۷ روز آینده)", 12f, Theme.TEXT, true), a.kit.lp(-1, -2)); body.addView(a.kit.gap(8)); int show = Math.min(6, urgent.size()); for (int i = 0; i < show; i++) { Row r = urgent.get(i); body.addView(a.kit.kv("چک " + Money.fa(r.s("num")) + " • " + Jalali.dispFa(r.s("sardate")), RialFormatter.formatRial(r.d("amount")) + " • " + r.s("customer"), Theme.TEXT), a.kit.lp(-1, -2)); } ScrollView sv = new ScrollView(a); sv.addView(body); AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar).setView(sv).create(); if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg()); body.addView(a.kit.gap(8)); body.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.lp(-1, -2)); dlg.show(); }
    private void confirmClearBalances() { LinearLayout body = a.kit.v(); body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16)); body.addView(a.kit.text("آیا همه موجودی‌های دستی پاک شود؟", 13f, Theme.TEXT, true), a.kit.lp(-1, -2)); body.addView(a.kit.hint("این عمل موجودی‌های واردشده برای همه بانک‌ها را حذف می‌کند"), a.kit.lp(-1, -2)); body.addView(a.kit.gap(12)); AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar).setView(body).create(); if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg()); LinearLayout row = a.kit.h(); row.addView(a.kit.btnGhost("انصراف", Theme.MUTED, v -> dlg.dismiss()), a.kit.wlp(1f)); row.addView(a.kit.space(8)); row.addView(a.kit.btn("پاک‌کردن", v -> { BankBalanceStore.clearAll(a); manualBalances.clear(); dlg.dismiss(); a.kit.toast("موجودی‌ها پاک شد"); render(findContentParent()); }), a.kit.wlp(1f)); body.addView(row, a.kit.lp(-1, -2)); dlg.show(); }
    private void shareLiquidityReport() { StringBuilder sb = new StringBuilder(); sb.append("گزارش تامین چک — ").append(Jalali.todayStr()).append("\n\n"); for (Row s : bankSummaries) { long bal = manualBalances.getOrDefault(s.s("bankRdf"), 0L); Double posD = posByBankCache.get(s.s("bankRdf")); long pos = posD == null ? 0L : Math.round(posD); long chk = Math.round(s.d("total")); long net = bal + pos - chk; sb.append(s.s("bank")).append(": چک ").append(RialFormatter.formatRial(chk)).append(" | پوز ").append(RialFormatter.formatRial(pos)).append(" | موجودی ").append(RialFormatter.formatRial(bal)).append(" | ").append(net >= 0 ? "مازاد " : "کسری ").append(RialFormatter.formatRial(Math.abs(net))).append("\n"); } try { android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString()); a.startActivity(android.content.Intent.createChooser(i, "اشتراک گزارش تامین چک")); } catch (Exception e) { a.kit.toast("اشتراک ممکن نشد"); } }
    private LinearLayout findContentParent() { return lastContentRef; }
    private View buildHeroCard() { LinearLayout c = a.kit.v(); c.setBackground(Theme.hero(accent())); c.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(15)); LinearLayout head = a.kit.h(); TextView g = a.kit.text(glyph(), 30, accent(), true); g.setGravity(Gravity.CENTER); g.setBackground(Theme.avatar(Theme.alpha(accent(), 60))); head.addView(g, new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58))); head.addView(a.kit.space(10)); LinearLayout copy = a.kit.v(); copy.addView(a.kit.title(title()), a.kit.lp(-1, -2)); copy.addView(a.kit.sub("چک‌های پرداختی • موجودی + پوز فردا = تامین"), a.kit.lp(-1, -2)); head.addView(copy, a.kit.wlp(1f)); c.addView(head, a.kit.lp(-1, -2)); return c; }
    public void render(LinearLayout content, boolean isCache) { render(content); }
    private static class Calculation { long includedTotal = 0; long excludedTotal = 0; }
    private Calculation calcForCheques(List<Row> list, String bankRdfFilter, String searchFilter) { Calculation c = new Calculation(); for (Row r : list) { if (bankRdfFilter != null && !bankRdfFilter.isEmpty() && !bankRdfFilter.equals(r.s("bankRdf"))) continue; String rdf = r.s("rdf"); String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf; long amt = Math.round(r.d("amount")); if (excludedRdfs.contains(key)) c.excludedTotal += amt; else c.includedTotal += amt; } return c; }
    private int countIncluded(List<Row> list) { int n = 0; for (Row r : list) { String rdf = r.s("rdf"); String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf; if (!excludedRdfs.contains(key)) n++; } return n; }
    private static String formatAgo(long ts) { if (ts <= 0) return "—"; long diff = System.currentTimeMillis() - ts; long mins = diff / 60000; if (mins < 1) return "همین الان"; if (mins < 60) return Money.fa(String.valueOf(mins)) + " دقیقه پیش"; long hrs = mins / 60; if (hrs < 24) return Money.fa(String.valueOf(hrs)) + " ساعت پیش"; long days = hrs / 24; return Money.fa(String.valueOf(days)) + " روز پیش"; }
}
