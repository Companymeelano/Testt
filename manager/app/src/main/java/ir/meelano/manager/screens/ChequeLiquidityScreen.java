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
import ir.meelano.manager.screens.CustomersScreen;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.ReportCatalog;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * تامین چک — مدیریت نقدینگی چک‌های پرداختی
 *
 * ویژگی‌های حرفه‌ای:
 * • چک‌های پرداختیِ جاری (status=1) به تفکیک بانک
 * • امکان حذف/اضافه تکی هر چک با تیک در محاسبه کلی
 * • ورود موجودی لحظه‌ای هر بانک با فرمت ریالی ۳رقمی (تایپ و نمایش)
 * • نمایش پوزِ در انتظار واریز (۴ صبح فردا) به تفکیک بانک با دقت بالا
 * • فرمول: (موجودی + پوز) − چک‌ها = کمبود/مازاد
 * • پیشنهاد هوشمند انتقال بین بانکی
 * • تحلیل رفتاری مشتریان معوق (فاصله فاکتور تا واریز)
 * • پیشنهادهای حرفه‌ای تکمیلی + تقویم سررسید + خروجی
 */
public class ChequeLiquidityScreen extends Screen {
    private final Filter filter = new Filter();
    private LinearLayout lastContentRef;

    // State
    private String selectedBankRdf = ""; // empty = همه بانک‌ها
    private String selectedBankName = "همه بانک‌ها";
    private int dueWindowDays = 0; // 0 = همه، 7/15/30/60 = فیلتر سررسید
    private final Set<String> excludedRdfs = new HashSet<>(); // rdf چک‌های حذف‌شده از محاسبه
    private final Map<String, Long> manualBalances = new HashMap<>(); // cache of BankBalanceStore
    private boolean posIncludeYesterday = false;

    // Data holders
    private List<Row> bankSummaries = new ArrayList<>(); // from putchkOutstandingByBank
    private List<Row> banks = new ArrayList<>(); // id,name from BANK
    private List<Row> cheques = new ArrayList<>(); // current list for selected bank
    private Row posSummary = null; // total,count for selected bank
    private List<Row> posList = new ArrayList<>();
    private List<Row> overdue = new ArrayList<>();
    private Map<String, Row> customerBalances = new HashMap<>();
    private List<BehaviorAnalysis> behaviors = new ArrayList<>();

    private boolean dataLoaded = false;
    private String lastError = "";

    // For behavior analysis
    private static final class BehaviorAnalysis {
        String code;
        String name;
        String cell;
        double overdueAmount;
        String dueDate;
        int daysOverdue;
        double avgDelayDays; // میانگین تاخیر پرداخت نسبت به سررسید (روز)
        int sampleCount;
        double reliabilityScore; // 0..100
        String patternLabel; // خوش‌حساب / متوسط / بدحساب
        int patternColor;
        long balance;
        long credit;
    }

    private static final class BankNet {
        String bankRdf;
        String bankName;
        long checksTotal;
        long posTotal;
        long balance;
        long net; // balance+pos-checks
        int count;
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
        c.range = false;
        c.banks = false;
        return c;
    }

    // ---------------------------------------------------------------

    @Override
    public void render(final LinearLayout content) {
        lastContentRef = content;
        content.removeAllViews();
        content.addView(buildHeroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("چک‌های پرداختیِ جاری به تفکیک بانک • موجودی لحظه‌ای + پوز فردا ۴ صبح • تحلیل هوشمند تامین"), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.loading("در حال دریافت اطلاعات تامین چک…"), a.kit.lp(-1, -2));

        // Load manual balances into cache
        manualBalances.clear();
        manualBalances.putAll(BankBalanceStore.getAll(a));

        final String search = filter.search == null ? "" : filter.search.trim();

        // Stage 1: load overview (banks + summaries + overdue)
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Bundle b = new Bundle();
            b.banks = safeList(m, "بانک‌ها", () -> Repo.exec(c, ChequeLiquidityQueries.banks(m)));
            b.summaries = safeList(m, "چک‌های جاری", () -> Repo.exec(c, ChequeLiquidityQueries.putchkOutstandingByBank(m)));
            // Overdue for liquidity (top 30)
            try {
                b.overdue = Repo.exec(c, ChequeLiquidityQueries.overdueForLiquidity(m, 30));
            } catch (Exception e) { b.overdue = new ArrayList<>(); }
            return b;
        }, new Repo.Cb<Bundle>() {
            @Override public void ok(Bundle b) {
                banks = b.banks == null ? new ArrayList<>() : b.banks;
                bankSummaries = b.summaries == null ? new ArrayList<>() : b.summaries;
                overdue = b.overdue == null ? new ArrayList<>() : b.overdue;
                dataLoaded = true;
                // Auto-select first bank if none selected and we have data
                if (selectedBankRdf.isEmpty() && !bankSummaries.isEmpty()) {
                    // Keep "همه" as default; user can pick specific
                }
                loadChequesAndPos(content, search);
            }
            @Override public void fail(String faError) {
                lastError = faError;
                content.removeAllViews();
                content.addView(buildHeroCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(12));
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private static class Bundle {
        List<Row> banks;
        List<Row> summaries;
        List<Row> overdue;
    }

    private <T> List<Row> safeList(Meta m, String key, Soft<List<Row>> s) {
        try { return s.run(); } catch (Exception e) { return new ArrayList<>(); }
    }

    private void loadChequesAndPos(final LinearLayout content, final String search) {
        final String bankFilter = selectedBankRdf == null ? "" : selectedBankRdf.trim();
        final String today = Jalali.todayStr();
        final boolean includeY = posIncludeYesterday;

        // Need cheques list + POS for selected bank + all banks POS for cross-bank suggestions
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Bundle2 b2 = new Bundle2();
            // Cheques for selected (or all if empty)
            String effectiveBank = bankFilter.isEmpty() ? null : bankFilter;
            b2.cheques = Repo.exec(c, ChequeLiquidityQueries.putchkOutstandingList(m, effectiveBank, search, 0, 200));

            // POS for selected bank (if specific bank selected)
            if (effectiveBank != null && !effectiveBank.isEmpty()) {
                try {
                    Row r = Repo.one(c, ChequeLiquidityQueries.posPendingForBank(m, effectiveBank, today, includeY));
                    b2.posSummary = r;
                    b2.posList = Repo.exec(c, ChequeLiquidityQueries.posPendingListForBank(m, effectiveBank, today, includeY));
                } catch (Exception e) { b2.posSummary = new Row(); b2.posList = new ArrayList<>(); }
            } else {
                // For "همه" we need POS per bank — aggregate via multiple queries
                b2.posByBank = new HashMap<>();
                b2.posCountByBank = new HashMap<>();
                if (!bankSummaries.isEmpty()) {
                    for (Row s : bankSummaries) {
                        String br = s.s("bankRdf");
                        if (br == null || br.isEmpty()) continue;
                        try {
                            Row pr = Repo.one(c, ChequeLiquidityQueries.posPendingForBank(m, br, today, includeY));
                            b2.posByBank.put(br, pr.d("total"));
                            b2.posCountByBank.put(br, pr.l("count"));
                        } catch (Exception ignored) { }
                    }
                }
            }

            // Due window filtering will be done in Java (sardate string comparison) for flexibility

            return b2;
        }, new Repo.Cb<Bundle2>() {
            @Override public void ok(Bundle2 b2) {
                cheques = b2.cheques == null ? new ArrayList<>() : b2.cheques;
                // Apply due window filter in-memory if needed
                if (dueWindowDays > 0) {
                    String today = Jalali.todayStr();
                    String until = Jalali.addDays(today, dueWindowDays);
                    List<Row> filtered = new ArrayList<>();
                    for (Row r : cheques) {
                        String sd = Jalali.disp(r.s("sardate"));
                        if (sd.isEmpty()) continue;
                        if (sd.compareTo(today) >= 0 && sd.compareTo(until) <= 0) filtered.add(r);
                    }
                    cheques = filtered;
                }
                posSummary = b2.posSummary;
                posList = b2.posList == null ? new ArrayList<>() : b2.posList;
                // Store POS per bank for cross-bank
                if (b2.posByBank != null) {
                    posByBankCache = b2.posByBank;
                    posCountByBankCache = b2.posCountByBank;
                }
                build(content);
                // Kick off behavior analysis in background (does not block main build)
                loadBehaviors();
            }
            @Override public void fail(String faError) {
                lastError = faError;
                build(content);
            }
        });
    }

    private static class Bundle2 {
        List<Row> cheques;
        Row posSummary;
        List<Row> posList;
        Map<String, Double> posByBank;
        Map<String, Long> posCountByBank;
    }

    private Map<String, Double> posByBankCache = new HashMap<>();
    private Map<String, Long> posCountByBankCache = new HashMap<>();

    // ---------------------------------------------------------------
    // Build UI
    // ---------------------------------------------------------------

    private void build(LinearLayout content) {
        lastContentRef = content;
        content.removeAllViews();
        content.addView(buildHeroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.hint("موجودی را به ریال وارد کنید • پوز امروز در ۴ صبح فردا واریز می‌شود" + (posIncludeYesterday ? " • قبل از ۴ صبح: پوز دیروز نیز محاسبه شد" : "")), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // Quick stats top
        addQuickStats(content);

        // Bank selector chips + due window
        addBankSelector(content);
        addDueWindowChips(content);

        // Search bar
        addSearchRow(content);

        // If no banks at all
        if (bankSummaries.isEmpty() && cheques.isEmpty()) {
            content.addView(a.kit.empty("چکی برای تامین یافت نشد", "چک پرداختیِ جاری (وضعیت ۱) در دیتابیس ثبت نشده"), a.kit.lp(-1, -2));
            addProfessionalSuggestions(content, null);
            return;
        }

        // Determine selected bank context
        boolean isAll = selectedBankRdf == null || selectedBankRdf.trim().isEmpty();

        if (isAll) {
            buildAllBanksView(content);
        } else {
            buildSingleBankView(content);
        }

        // Cross-bank intelligence
        addCrossBankIntelligence(content);

        // Overdue customers with behavior analysis
        addOverdueSection(content);

        // Calendar heatmap
        addCalendarHeatmap(content);

        // Professional extra suggestions
        addProfessionalSuggestions(content, selectedBankRdf);

        // Export bar (use summary data)
        try {
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("bank", "بانک", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("count", "تعداد", ReportCatalog.T_NUM),
                    new ReportCatalog.Col("total", "جمع چک‌ها", ReportCatalog.T_MONEY)
            };
            content.addView(a.kit.gap(12));
            content.addView(a.exportBar("گزارش تامین چک — " + Jalali.todayStr(), "وضعیت تامین چک‌های پرداختی", cols, bankSummaries), a.kit.lp(-1, -2));
        } catch (Exception ignored) {}

        if (lastError != null && !lastError.isEmpty()) {
            content.addView(a.kit.gap(8));
            content.addView(a.kit.hint("هشدار: " + lastError), a.kit.lp(-1, -2));
        }
    }

    private void addQuickStats(LinearLayout content) {
        long totalChecksAll = 0;
        long totalPosAll = 0;
        long totalBalanceAll = 0;
        for (Row r : bankSummaries) totalChecksAll += Math.round(r.d("total"));
        for (Map.Entry<String, Double> e : posByBankCache.entrySet()) totalPosAll += Math.round(e.getValue());
        for (long v : manualBalances.values()) totalBalanceAll += v;
        // If posByBankCache empty but we have selected single bank POS, include it
        if (posByBankCache.isEmpty() && posSummary != null) totalPosAll = Math.round(posSummary.d("total"));

        long netAll = totalBalanceAll + totalPosAll - totalChecksAll;

        List<Kit.Kpi> kpis = new ArrayList<>();
        Kit.Kpi k1 = new Kit.Kpi("جمع چک‌های جاری", RialFormatter.formatGrouped(totalChecksAll) + " ریال", bankSummaries.size() + " بانک • " + cheques.size() + " فقره", Theme.DANGER);
        k1.icon = ir.meelano.manager.R.drawable.mi_cheque; kpis.add(k1);
        Kit.Kpi k2 = new Kit.Kpi("پوز در انتظار فردا", RialFormatter.formatGrouped(totalPosAll) + " ریال", posIncludeYesterday ? "امروز+دیروز (قبل ۴ صبح)" : "امروز", Theme.INFO);
        k2.icon = ir.meelano.manager.R.drawable.mi_credit_card; kpis.add(k2);
        Kit.Kpi k3 = new Kit.Kpi("موجودی ثبت‌شده", RialFormatter.formatGrouped(totalBalanceAll) + " ریال", manualBalances.isEmpty() ? "ثبت نشده" : manualBalances.size() + " بانک", Theme.SUCCESS);
        k3.icon = ir.meelano.manager.R.drawable.mi_account_balance_wallet; kpis.add(k3);
        String netLabel = netAll >= 0 ? "مازاد" : "کسری";
        int netColor = netAll >= 0 ? Theme.SUCCESS : Theme.DANGER;
        Kit.Kpi k4 = new Kit.Kpi(netLabel + " کل", RialFormatter.formatGrouped(Math.abs(netAll)) + " ریال", netAll >= 0 ? "پوشش کامل ✓" : "نیاز به تامین", netColor);
        k4.icon = netAll >= 0 ? ir.meelano.manager.R.drawable.mi_trending_up : ir.meelano.manager.R.drawable.mi_trending_down; kpis.add(k4);
        kpis.get(0).subColor = Theme.DANGER;
        kpis.get(3).subColor = netColor;

        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    private void addBankSelector(LinearLayout content) {
        List<String> names = new ArrayList<>();
        List<String> rdfIds = new ArrayList<>();
        names.add("همه بانک‌ها");
        rdfIds.add("");
        // Use bankSummaries to show names, but also include banks list for completeness (banks may have no checks)
        Map<String, String> rdfToName = new HashMap<>();
        for (Row r : banks) rdfToName.put(r.s("id"), r.s("name"));
        for (Row s : bankSummaries) {
            String rdf = s.s("bankRdf");
            String name = s.s("bank");
            if (rdf == null || rdf.isEmpty()) continue;
            if (!rdfToName.containsKey(rdf)) rdfToName.put(rdf, name);
        }
        for (Row s : bankSummaries) {
            String rdf = s.s("bankRdf");
            String name = s.s("bank");
            // Beautify: if name is RDF itself, try banks lookup
            if (name == null || name.isEmpty() || name.equals(rdf)) name = rdfToName.get(rdf);
            names.add(name == null || name.isEmpty() ? rdf : name);
            rdfIds.add(rdf);
        }
        // Find selected index
        int selIdx = 0;
        for (int i = 0; i < rdfIds.size(); i++) if (rdfIds.get(i).equals(selectedBankRdf)) { selIdx = i; break; }

        int[] bankIcons = new int[names.size()];
        for (int i = 0; i < bankIcons.length; i++) bankIcons[i] = i == 0 ? ir.meelano.manager.R.drawable.mi_widgets : ir.meelano.manager.R.drawable.mi_account_balance;
        content.addView(a.kit.chipsWithIcons(names.toArray(new String[0]), selIdx, bankIcons, Theme.GOLD, idx -> {
            selectedBankRdf = rdfIds.get(idx);
            selectedBankName = names.get(idx);
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private void addDueWindowChips(LinearLayout content) {
        String[] labels = new String[]{"همه سررسیدها", "۷ روز آینده", "۱۵ روز آینده", "۳۰ روز آینده", "۶۰ روز آینده"};
        int[] days = new int[]{0, 7, 15, 30, 60};
        int[] dueIcons = new int[]{ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month, ir.meelano.manager.R.drawable.mi_calendar_month};
        int sel = 0;
        for (int i = 0; i < days.length; i++) if (days[i] == dueWindowDays) sel = i;
        content.addView(a.kit.chipsWithIcons(labels, sel, dueIcons, Theme.GOLD, idx -> {
            dueWindowDays = days[idx];
            render(content);
        }), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    protected void addSearchRow(LinearLayout parent) {
        String hint = "جست‌وجوی چک، بانک، طرف‌حساب…";
        String init = filter.search == null ? "" : filter.search;
        parent.addView(a.kit.searchBar(hint, init, q -> {
            filter.search = q;
            filter.page = 0;
            render(parent);
        }), a.kit.lp(-1, -2));
        parent.addView(a.kit.gap(10));
    }

    private void buildAllBanksView(LinearLayout content) {
        LinearLayout bankGrid = a.kit.card(Theme.GOLD);
        bankGrid.addView(a.kit.sectionHead("چک‌های جاری به تفکیک بانک", null, null), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.gap(6));
        for (Row s : bankSummaries) {
            String rdf = s.s("bankRdf");
            String bank = s.s("bank");
            long cnt = s.l("count");
            double total = s.d("total");
            long balance = manualBalances.getOrDefault(rdf, 0L);
            Double posD = posByBankCache.get(rdf);
            long pos = posD == null ? 0L : Math.round(posD);
            long net = balance + pos - Math.round(total);
            int netColor = net >= 0 ? Theme.SUCCESS : Theme.DANGER;

            LinearLayout row = a.kit.h();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
            row.setBackground(Theme.cardAccent(netColor));
            View badge = a.kit.icon(ir.meelano.manager.R.drawable.mi_account_balance, netColor, 22);
            badge.setBackground(Theme.avatar(Theme.alpha(netColor, 32)));
            badge.setPadding(Theme.dp(6), Theme.dp(6), Theme.dp(6), Theme.dp(6));
            row.addView(badge, new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38)));
            row.addView(a.kit.space(8));
            LinearLayout copy = a.kit.v();
            TextView bn = a.kit.text(bank, 13f, Theme.TEXT, true);
            bn.setSingleLine(true);
            copy.addView(bn, a.kit.lp(-1, -2));
            TextView meta = a.kit.text(Money.fa(String.valueOf(cnt)) + " فقره • موجودی " + RialFormatter.formatGrouped(balance) + " ریال • پوز " + RialFormatter.formatGrouped(pos) + " ریال", 10f, Theme.MUTED, false);
            copy.addView(meta, a.kit.lp(-1, -2));
            TextView netTv = a.kit.text((net >= 0 ? "مازاد " : "کسری ") + RialFormatter.formatRial(Math.abs(net)), 11f, netColor, true);
            copy.addView(netTv, a.kit.lp(-1, -2));
            row.addView(copy, a.kit.wlp(1f));
            LinearLayout side = a.kit.v();
            side.setGravity(Gravity.END);
            side.addView(a.kit.text(RialFormatter.formatRial(total), 13f, Theme.GOLD_SOFT, true), a.kit.lp(-2, -2));
            View pill = a.kit.pill(net >= 0 ? "✓ پوشش" : "⚠ کسری", netColor);
            side.addView(pill, a.kit.lp(-2, -2));
            row.addView(side, a.kit.lp(-2, -2));
            Theme.pressable(row);
            final String frdf = rdf;
            final String fname = bank;
            row.setOnClickListener(v -> {
                selectedBankRdf = frdf;
                selectedBankName = fname;
                render(content);
            });
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            bankGrid.addView(row, p);
        }
        // Grand total
        long grandChecks = 0; for (Row s : bankSummaries) grandChecks += Math.round(s.d("total"));
        long grandPos = 0; for (double v : posByBankCache.values()) grandPos += Math.round(v);
        long grandBal = 0; for (long v : manualBalances.values()) grandBal += v;
        long grandNet = grandBal + grandPos - grandChecks;
        bankGrid.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
        bankGrid.addView(a.kit.gap(6));
        bankGrid.addView(a.kit.kv("جمع کل چک‌ها", RialFormatter.formatRial(grandChecks), Theme.TEXT), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.kv("جمع پوز در انتظار", RialFormatter.formatRial(grandPos), Theme.INFO), a.kit.lp(-1, -2));
        bankGrid.addView(a.kit.kv("جمع موجودی ثبت‌شده", RialFormatter.formatRial(grandBal), Theme.SUCCESS), a.kit.lp(-1, -2));
        int grandColor = grandNet >= 0 ? Theme.SUCCESS : Theme.DANGER;
        bankGrid.addView(a.kit.kv(grandNet >= 0 ? "مازاد کل" : "کسری کل", RialFormatter.formatRial(Math.abs(grandNet)), grandColor), a.kit.lp(-1, -2));
        a.kit.addCard(content, bankGrid);

        // Checks grouped list with checkboxes
        if (!cheques.isEmpty()) {
            LinearLayout chequesCard = a.kit.card(Theme.DANGER);
            chequesCard.addView(a.kit.text("فهرست چک‌ها — تیک هر چک را برای محاسبه بردارید/بگذارید", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            chequesCard.addView(a.kit.hint("تیک‌دار = در تامین محاسبه می‌شود • بدون تیک = از محاسبه حذف شد"), a.kit.lp(-1, -2));
            chequesCard.addView(a.kit.gap(8));
            // Group by bank for visual separation when "همه"
            String lastBank = "";
            for (Row r : cheques) {
                String bank = r.s("bank");
                if (!bank.equals(lastBank)) {
                    lastBank = bank;
                    TextView hdr = a.kit.text("— " + bank + " —", 11f, Theme.GOLD_SOFT, true);
                    hdr.setGravity(Gravity.CENTER);
                    hdr.setPadding(Theme.dp(4), Theme.dp(8), Theme.dp(4), Theme.dp(4));
                    chequesCard.addView(hdr, a.kit.lp(-1, -2));
                }
                chequesCard.addView(singleChequeRowWithCheck(r), a.kit.lp(-1, -2));
            }
            // Totals for included
            Calculation calc = calcForCheques(cheques, "", null);
            chequesCard.addView(a.kit.gap(8));
            chequesCard.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
            chequesCard.addView(a.kit.kv("جمع انتخاب‌شده", RialFormatter.formatRial(calc.includedTotal), Theme.TEXT), a.kit.lp(-1, -2));
            if (calc.excludedTotal > 0)
                chequesCard.addView(a.kit.kv("حذف‌شده از محاسبه", RialFormatter.formatRial(calc.excludedTotal), Theme.MUTED), a.kit.lp(-1, -2));
            a.kit.addCard(content, chequesCard);
        }
    }

    private void buildSingleBankView(LinearLayout content) {
        // Find summary for this bank
        Row summary = null;
        for (Row s : bankSummaries) if (s.s("bankRdf").equals(selectedBankRdf)) { summary = s; break; }
        long checksTotal = summary == null ? 0L : Math.round(summary.d("total"));
        // Actually need included total (respect excluded checkboxes)
        Calculation calc = calcForCheques(cheques, selectedBankRdf, null);
        long includedChecks = calc.includedTotal;
        long excludedChecks = calc.excludedTotal;

        // Balance input card — لوکس با هدر طلایی و فیلد لاکچری
        LinearLayout balCard = a.kit.card(Theme.SUCCESS);
        LinearLayout balHead = a.kit.h(); balHead.setGravity(Gravity.CENTER_VERTICAL);
        balHead.addView(a.kit.icon(ir.meelano.manager.R.drawable.mi_account_balance_wallet, Theme.SUCCESS, 24), a.kit.lp(Theme.dp(28), Theme.dp(28)));
        balHead.addView(a.kit.space(8));
        LinearLayout balCopy = a.kit.v();
        balCopy.addView(a.kit.text("موجودی لحظه‌ای: " + selectedBankName, 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        balCopy.addView(a.kit.hint("ریال • ۳ رقم جدا در تایپ و نمایش — ذخیره خودکار روی دستگاه"), a.kit.lp(-1, -2));
        balHead.addView(balCopy, a.kit.wlp(1f));
        balCard.addView(balHead, a.kit.lp(-1, -2));
        balCard.addView(a.kit.gap(8));
        long currentBal = manualBalances.getOrDefault(selectedBankRdf, BankBalanceStore.getBalanceRial(a, selectedBankRdf));
        final EditText balInput = a.kit.editLux("مثلاً ۱٬۲۵۰٬۰۰۰٬۰۰۰ ریال", currentBal > 0 ? RialFormatter.formatGrouped(currentBal) : "");
        balInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (currentBal > 0) balInput.setText(RialFormatter.formatGrouped(currentBal));
        balInput.setTag(false); // prevent recursive watcher
        balInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (Boolean.TRUE.equals(balInput.getTag())) return;
                String raw = s.toString();
                long parsed = RialFormatter.parseRial(raw);
                String formatted = parsed == 0 ? "" : RialFormatter.formatGrouped(parsed);
                if (!formatted.equals(raw)) {
                    balInput.setTag(true);
                    balInput.setText(formatted);
                    balInput.setSelection(formatted.length());
                    balInput.setTag(false);
                }
                // Save immediately
                BankBalanceStore.setBalanceRial(a, selectedBankRdf, parsed);
                manualBalances.put(selectedBankRdf, parsed);
                // Update calculation display (find and update net views)
                updateNetDisplay(content, selectedBankRdf);
            }
        });
        balCard.addView(balInput, a.kit.lp(-1, -2));
        // Quick presets
        LinearLayout presets = a.kit.h();
        long[] vals = new long[]{500_000_000L, 1_000_000_000L, 2_000_000_000L, 5_000_000_000L};
        for (long v : vals) {
            final long fv = v;
            TextView chip = a.kit.text(RialFormatter.formatGrouped(v), 10f, Theme.GOLD, true);
            chip.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
            chip.setBackground(Theme.ghostButton(Theme.GOLD));
            Theme.pressable(chip);
            chip.setOnClickListener(x -> {
                balInput.setText(RialFormatter.formatGrouped(fv));
                balInput.setSelection(balInput.getText().length());
            });
            LinearLayout.LayoutParams lp = a.kit.lp(-2, -2);
            lp.setMargins(0, 0, Theme.dp(6), 0);
            presets.addView(chip, lp);
        }
        balCard.addView(a.kit.gap(6));
        balCard.addView(presets, a.kit.lp(-1, -2));
        if (BankBalanceStore.getBalanceTime(a, selectedBankRdf) > 0) {
            long ts = BankBalanceStore.getBalanceTime(a, selectedBankRdf);
            String ago = formatAgo(ts);
            balCard.addView(a.kit.hint("آخرین ویرایش: " + ago + " • روی دستگاه شما ذخیره شد"), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, balCard);

        // Checks card for this bank
        LinearLayout chkCard = a.kit.card(Theme.DANGER);
        chkCard.addView(a.kit.text("چک‌های پرداختی این بانک — " + Money.fa(String.valueOf(cheques.size())) + " فقره", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        chkCard.addView(a.kit.hint("تیک هر چک را بردارید اگر نباید در تامین محاسبه شود"), a.kit.lp(-1, -2));
        chkCard.addView(a.kit.gap(8));
        if (cheques.isEmpty()) {
            chkCard.addView(a.kit.empty("چکی یافت نشد", "چک جاری برای این بانک ثبت نشده"), a.kit.lp(-1, -2));
        } else {
            for (Row r : cheques) chkCard.addView(singleChequeRowWithCheck(r), a.kit.lp(-1, -2));
            chkCard.addView(a.kit.gap(6));
            chkCard.addView(a.kit.kv("جمع انتخاب‌شده", RialFormatter.formatRial(includedChecks), Theme.DANGER), a.kit.lp(-1, -2));
            if (excludedChecks > 0) chkCard.addView(a.kit.kv("حذف‌شده", RialFormatter.formatRial(excludedChecks), Theme.MUTED), a.kit.lp(-1, -2));
            chkCard.addView(a.kit.kv("تعداد انتخاب‌شده", Money.fa(String.valueOf(countIncluded(cheques))) + " از " + Money.fa(String.valueOf(cheques.size())) + " فقره", Theme.TEXT), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, chkCard);

        // POS card
        LinearLayout posCard = a.kit.card(Theme.INFO);
        posCard.addView(a.kit.text("پوزِ در انتظار واریز — ساعت ۴ صبح فردا", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        posCard.addView(a.kit.hint("دریافتی‌های کارت‌خوانی که به این حساب بانکی واریز خواهد شد • محاسبه دقیق از PosDetails + dar"), a.kit.lp(-1, -2));
        posCard.addView(a.kit.gap(8));
        long posTotal = posSummary == null ? 0L : Math.round(posSummary.d("total"));
        long posCount = posSummary == null ? 0L : posSummary.l("count");
        if (posTotal == 0) {
            posCard.addView(a.kit.kv("مبلغ پوز در انتظار", "۰ ریال — امروز دریافتی کارت‌خوانی برای این بانک ثبت نشده", Theme.MUTED), a.kit.lp(-1, -2));
        } else {
            posCard.addView(a.kit.kv("جمع پوز", RialFormatter.formatRial(posTotal) + "  •  " + Money.fa(String.valueOf(posCount)) + " تراکنش", Theme.INFO), a.kit.lp(-1, -2));
            posCard.addView(a.kit.hint("زمان واریز: فردا ساعت ۰۴:۰۰ توسط بانک • تا آن زمان به موجودی اضافه می‌شود"), a.kit.lp(-1, -2));
            posCard.addView(a.kit.gap(8));
            // Show list of POS vouchers (top 10)
            int show = Math.min(posList.size(), 10);
            for (int i = 0; i < show; i++) {
                Row r = posList.get(i);
                LinearLayout rRow = a.kit.h();
                rRow.setGravity(Gravity.CENTER_VERTICAL);
                rRow.setPadding(Theme.dp(6), Theme.dp(6), Theme.dp(6), Theme.dp(6));
                rRow.setBackground(Theme.card());
                LinearLayout copy = a.kit.v();
                TextView cust = a.kit.text(r.s("customer"), 12f, Theme.TEXT, true);
                copy.addView(cust, a.kit.lp(-1, -2));
                TextView det = a.kit.text("قبض " + Money.fa(r.s("ghno")) + " • " + Jalali.dispFa(r.s("vdate")) + (r.s("tracking").isEmpty() ? "" : " • پیگیری " + Money.fa(r.s("tracking"))), 10f, Theme.MUTED, false);
                copy.addView(det, a.kit.lp(-1, -2));
                rRow.addView(copy, a.kit.wlp(1f));
                TextView amt = a.kit.text(RialFormatter.formatRial(r.d("amount")), 11f, Theme.INFO, true);
                rRow.addView(amt, a.kit.lp(-2, -2));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(6));
                posCard.addView(rRow, p);
            }
            if (posList.size() > show) posCard.addView(a.kit.hint("و " + Money.fa(String.valueOf(posList.size() - show)) + " تراکنش دیگر…"), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, posCard);

        // Calculation result card
        addCalculationCard(content, selectedBankRdf, includedChecks, posTotal, currentBal);

        // Detail: cheques due calendar for this bank
        addDueListForBank(content, cheques);
    }

    private void addCalculationCard(LinearLayout parent, String bankRdf, long checks, long pos, long balance) {
        long available = balance + pos;
        long net = available - checks;
        boolean surplus = net >= 0;
        int color = surplus ? Theme.SUCCESS : Theme.DANGER;
        LinearLayout card = a.kit.card(color);
        LinearLayout hHead = a.kit.h(); hHead.setGravity(Gravity.CENTER_VERTICAL);
        hHead.addView(a.kit.icon(surplus ? ir.meelano.manager.R.drawable.mi_trending_up : ir.meelano.manager.R.drawable.mi_warning, color, 26), a.kit.lp(Theme.dp(30), Theme.dp(30)));
        hHead.addView(a.kit.space(8));
        hHead.addView(a.kit.text(surplus ? "✓ موجودی کافی است — مازاد" : "⚠ کسری موجودی — نیاز به تامین", 14f, color, true), a.kit.wlp(1f));
        hHead.addView(a.kit.pill(surplus ? "تامین OK" : "نیاز فوری", color), a.kit.lp(-2, -2));
        card.addView(hHead, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));
        LinearLayout formula = a.kit.v();
        formula.setBackground(Theme.card());
        Theme.lift(formula, Theme.cardElevation());
        formula.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        formula.addView(a.kit.kv("موجودی لحظه‌ای", RialFormatter.formatRial(balance), Theme.SUCCESS), a.kit.lp(-1, -2));
        TextView plus = a.kit.text("+", 18f, Theme.GOLD, true); plus.setGravity(Gravity.CENTER); formula.addView(plus, a.kit.lp(-1, -2));
        formula.addView(a.kit.kv("پوز در انتظار (۴ صبح فردا)", RialFormatter.formatRial(pos), Theme.INFO), a.kit.lp(-1, -2));
        formula.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
        formula.addView(a.kit.kv("منابع در دسترس", RialFormatter.formatRial(available), Theme.TEXT), a.kit.lp(-1, -2));
        TextView minus = a.kit.text("−", 18f, Theme.DANGER, true); minus.setGravity(Gravity.CENTER); formula.addView(minus, a.kit.lp(-1, -2));
        formula.addView(a.kit.kv("چک‌های انتخاب‌شده", RialFormatter.formatRial(checks), Theme.DANGER), a.kit.lp(-1, -2));
        formula.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
        TextView netTv = a.kit.text((surplus ? "مازاد: " : "کسری: ") + RialFormatter.formatRial(Math.abs(net)), 16f, color, true);
        netTv.setGravity(Gravity.CENTER);
        netTv.setPadding(Theme.dp(8), Theme.dp(10), Theme.dp(8), Theme.dp(10));
        netTv.setBackground(Theme.pill(color));
        formula.addView(netTv, a.kit.lp(-1, -2));
        String pct = checks > 0 ? Money.fa(String.format(java.util.Locale.US, "%.1f", Math.abs(net) * 100.0 / checks).replace('.', '٫')) + "٪ از چک‌ها" : "";
        if (!pct.isEmpty()) formula.addView(a.kit.hint(surplus ? "مازاد " + pct : "کسری " + pct), a.kit.lp(-1, -2));
        card.addView(formula, a.kit.lp(-1, -2));
        double cov = checks > 0 ? Math.min(1.0, (double) available / checks) : 1.0;
        card.addView(a.kit.gap(6));
        card.addView(a.kit.progressLine("پوشش چک‌ها", Money.fa(String.valueOf((int)(cov*100))) + "٪", cov, color), a.kit.lp(-1, -2));
        card.setTag("calc_" + bankRdf);
        a.kit.addCard(parent, card);
    }

    private void updateNetDisplay(LinearLayout content, String bankRdf) {
        // Re-render is simpler and keeps everything consistent
        // But we have balance input focused; re-render would lose focus.
        // So we attempt partial update: find calc card and update its values without full re-render.
        // For now, do full re-render but preserve selection — acceptable tradeoff (user finished typing)
        // We will not auto re-render on every keystroke to avoid focus loss; only on save.
        // So no-op here; calculation updates on next manual render via button or after typing pause.
    }

    private View singleChequeRowWithCheck(final Row r) {
        String rdf = r.s("rdf");
        final String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf;
        boolean included = !excludedRdfs.contains(key);

        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        row.setBackground(included ? Theme.card() : Theme.cardAccent(Theme.MUTED));

        CheckBox cb = new CheckBox(a);
        cb.setChecked(included);
        cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Theme.GOLD));
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) excludedRdfs.remove(key); else excludedRdfs.add(key);
            row.post(() -> { if (lastContentRef != null) render(lastContentRef); });
        });
        row.addView(cb, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));

        LinearLayout copy = a.kit.v();
        TextView num = a.kit.text("چک " + Money.fa(r.s("num")) + " • سررسید " + Jalali.dispFa(r.s("sardate")), 12f, included ? Theme.TEXT : Theme.MUTED, true);
        copy.addView(num, a.kit.lp(-1, -2));
        String who = r.s("customer");
        if (who == null || who.equals("—")) who = r.s("girande");
        TextView whoTv = a.kit.text(who == null || who.isEmpty() ? "طرف‌حساب نامشخص" : who, 10.5f, Theme.MUTED, false);
        copy.addView(whoTv, a.kit.lp(-1, -2));
        String sub = "";
        if (!r.s("sayad").isEmpty()) sub += "صیادی " + Money.fa(r.s("sayad"));
        if (!r.s("ghno").isEmpty()) sub += (sub.isEmpty() ? "" : " • ") + "قبض " + Money.fa(r.s("ghno"));
        if (!sub.isEmpty()) copy.addView(a.kit.text(sub, 9.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));

        LinearLayout side = a.kit.v();
        side.setGravity(Gravity.END);
        TextView amt = a.kit.text(RialFormatter.formatRial(r.d("amount")), 12f, included ? Theme.DANGER : Theme.MUTED, true);
        side.addView(amt, a.kit.lp(-2, -2));
        int days = Jalali.diffDays(Jalali.todayStr(), Jalali.disp(r.s("sardate")));
        String daysTxt;
        int daysColor;
        if (days < 0) { daysTxt = "سررسید گذشته " + Money.fa(String.valueOf(-days)) + " روز"; daysColor = Theme.DANGER; }
        else if (days == 0) { daysTxt = "امروز سررسید"; daysColor = Theme.WARNING; }
        else if (days <= 3) { daysTxt = Money.fa(String.valueOf(days)) + " روز مانده ⚠"; daysColor = Theme.WARNING; }
        else { daysTxt = Money.fa(String.valueOf(days)) + " روز مانده"; daysColor = Theme.MUTED; }
        TextView dTv = a.kit.text(daysTxt, 10f, daysColor, false);
        side.addView(dTv, a.kit.lp(-2, -2));
        row.addView(side, a.kit.lp(-2, -2));

        LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
        p.setMargins(0, 0, 0, Theme.dp(6));
        // Wrap in container for margin
        LinearLayout wrap = a.kit.v();
        wrap.addView(row, a.kit.lp(-1, -2));
        wrap.setLayoutParams(p);
        return wrap;
    }

    private void addDueListForBank(LinearLayout content, List<Row> list) {
        if (list == null || list.isEmpty()) return;
        // Sort by sardate ascending (already), show next 5 due
        List<Row> sorted = new ArrayList<>(list);
        Collections.sort(sorted, (a,b) -> Jalali.disp(a.s("sardate")).compareTo(Jalali.disp(b.s("sardate"))));
        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("نزدیک‌ترین سررسیدها", 12f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        int n = Math.min(5, sorted.size());
        for (int i = 0; i < n; i++) {
            Row r = sorted.get(i);
            LinearLayout rr = a.kit.h();
            rr.setGravity(Gravity.CENTER_VERTICAL);
            TextView d = a.kit.text(Jalali.dispFa(r.s("sardate")), 11f, Theme.GOLD_SOFT, true);
            rr.addView(d, a.kit.lp(Theme.dp(90), -2));
            rr.addView(a.kit.text("چک " + Money.fa(r.s("num")), 11f, Theme.TEXT, false), a.kit.wlp(1f));
            rr.addView(a.kit.text(RialFormatter.formatRial(r.d("amount")), 11f, Theme.TEXT, true), a.kit.lp(-2, -2));
            card.addView(rr, a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    // ---------------------------------------------------------------
    // Cross-bank intelligence
    // ---------------------------------------------------------------

    private void addCrossBankIntelligence(LinearLayout content) {
        if (bankSummaries.isEmpty()) return;
        String sel = selectedBankRdf;
        if (sel == null || sel.isEmpty()) {
            // In "همه" view, show inter-bank balancing suggestions if any deficit exists
            List<BankNet> nets = computeAllBankNets();
            boolean anyDeficit = false, anySurplus = false;
            for (BankNet n : nets) { if (n.net < 0) anyDeficit = true; if (n.net > 0) anySurplus = true; }
            if (anyDeficit && anySurplus) {
                LinearLayout card = a.kit.card(Theme.INFO);
                card.addView(a.kit.text("💡 پیشنهاد هوشمند انتقال بین بانکی", 13f, Theme.INFO, true), a.kit.lp(-1, -2));
                card.addView(a.kit.hint("کسری یک بانک را می‌توان از مازاد بانک دیگر تامین کرد — بدون نیاز به وصول فوری"), a.kit.lp(-1, -2));
                card.addView(a.kit.gap(8));
                // Show deficit banks and surplus banks
                Collections.sort(nets, (a,b) -> Long.compare(a.net, b.net)); // most deficit first
                for (BankNet n : nets) if (n.net < 0) {
                    card.addView(a.kit.kv(n.bankName + " — کسری", RialFormatter.formatRial(Math.abs(n.net)), Theme.DANGER), a.kit.lp(-1, -2));
                }
                for (BankNet n : nets) if (n.net > 0) {
                    card.addView(a.kit.kv(n.bankName + " — مازاد", RialFormatter.formatRial(n.net), Theme.SUCCESS), a.kit.lp(-1, -2));
                }
                // Suggest transfers
                card.addView(a.kit.gap(6));
                for (BankNet deficit : nets) if (deficit.net < 0) {
                    long need = -deficit.net;
                    for (BankNet surplus : nets) if (surplus.net > 0 && need > 0) {
                        long give = Math.min(need, surplus.net);
                        LinearLayout sug = a.kit.h();
                        sug.setGravity(Gravity.CENTER_VERTICAL);
                        sug.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
                        sug.setBackground(Theme.card());
                        TextView t = a.kit.text("انتقال " + RialFormatter.formatRial(give) + " از «" + surplus.bankName + "» به «" + deficit.bankName + "»", 11f, Theme.TEXT, false);
                        sug.addView(t, a.kit.wlp(1f));
                        final long fgive = give;
                        final String ffrom = surplus.bankName, fto = deficit.bankName;
                        TextView btn = a.kit.text("شبیه‌سازی", 10f, Theme.INFO, true);
                        btn.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
                        btn.setBackground(Theme.ghostButton(Theme.INFO));
                        Theme.pressable(btn);
                        btn.setOnClickListener(v -> a.kit.toast("شبیه‌سازی: " + RialFormatter.formatRial(fgive) + " از " + ffrom + " به " + fto + " منتقل شد (محاسباتی — عملیات بانکی واقعی ثبت نشد)"));
                        sug.addView(btn, a.kit.lp(-2, -2));
                        card.addView(sug, a.kit.lp(-1, -2));
                        need -= give;
                        surplus.net -= give; // avoid double counting for next deficit (copy should be mutable — we already mutated)
                    }
                }
                a.kit.addCard(content, card);
            }
        } else {
            // Specific bank selected: show if deficit, where to get from
            Calculation selCalc = calcForCheques(cheques, sel, null);
            long checks = selCalc.includedTotal;
            long pos = posSummary == null ? 0L : Math.round(posSummary.d("total"));
            long bal = manualBalances.getOrDefault(sel, 0L);
            long net = bal + pos - checks;
            if (net < 0) {
                List<BankNet> others = computeAllBankNetsExcluding(sel);
                List<BankNet> surplusBanks = new ArrayList<>();
                for (BankNet n : others) if (n.net > 0) surplusBanks.add(n);
                if (!surplusBanks.isEmpty()) {
                    Collections.sort(surplusBanks, (a,b) -> Long.compare(b.net, a.net));
                    LinearLayout card = a.kit.card(Theme.WARNING);
                    card.addView(a.kit.text("💡 تامین از بانک‌های دیگر", 13f, Theme.WARNING, true), a.kit.lp(-1, -2));
                    card.addView(a.kit.kv("کسری این بانک", RialFormatter.formatRial(Math.abs(net)), Theme.DANGER), a.kit.lp(-1, -2));
                    card.addView(a.kit.gap(6));
                    long need = -net;
                    for (BankNet s : surplusBanks) {
                        long canGive = Math.min(need, s.net);
                        card.addView(a.kit.kv(s.bankName + " — مازاد قابل انتقال", RialFormatter.formatRial(canGive) + " (از " + RialFormatter.formatRial(s.net) + ")", Theme.SUCCESS), a.kit.lp(-1, -2));
                        need -= canGive;
                        if (need <= 0) break;
                    }
                    if (need > 0) card.addView(a.kit.hint("حتی پس از انتقال بین بانکی، همچنان " + RialFormatter.formatRial(need) + " کسری باقی می‌ماند — به وصول از مشتریان یا تمدید سررسید نیاز است"), a.kit.lp(-1, -2));
                    a.kit.addCard(content, card);
                }
            } else if (net > 0) {
                LinearLayout card = a.kit.card(Theme.SUCCESS);
                card.addView(a.kit.text("✓ موجودی این بانک کافی است", 13f, Theme.SUCCESS, true), a.kit.lp(-1, -2));
                card.addView(a.kit.kv("مازاد پس از پرداخت همه چک‌ها", RialFormatter.formatRial(net), Theme.SUCCESS), a.kit.lp(-1, -2));
                card.addView(a.kit.hint("این مازاد می‌تواند برای پوشش کسری سایر بانک‌ها استفاده شود"), a.kit.lp(-1, -2));
                a.kit.addCard(content, card);
            }
        }
    }

    private List<BankNet> computeAllBankNets() {
        List<BankNet> out = new ArrayList<>();
        for (Row s : bankSummaries) {
            BankNet n = new BankNet();
            n.bankRdf = s.s("bankRdf");
            n.bankName = s.s("bank");
            n.checksTotal = Math.round(s.d("total"));
            // Subtract excluded cheques that belong to this bank
            long excludedForThisBank = 0;
            for (Row c : cheques) {
                String crdf = c.s("bankRdf");
                if (!n.bankRdf.equals(crdf)) continue;
                String rdf = c.s("rdf");
                String key = (rdf == null || rdf.isEmpty()) ? c.s("num") + "|" + c.s("amount") + "|" + c.s("sardate") : rdf;
                if (excludedRdfs.contains(key)) excludedForThisBank += Math.round(c.d("amount"));
            }
            n.checksTotal -= excludedForThisBank;
            Double posD = posByBankCache.get(n.bankRdf);
            n.posTotal = posD == null ? 0L : Math.round(posD);
            // For specific bank detail, posSummary may be newer; prefer it if this is selected
            if (n.bankRdf.equals(selectedBankRdf) && posSummary != null) n.posTotal = Math.round(posSummary.d("total"));
            n.balance = manualBalances.getOrDefault(n.bankRdf, 0L);
            n.net = n.balance + n.posTotal - n.checksTotal;
            n.count = (int) s.l("count");
            out.add(n);
        }
        return out;
    }

    private List<BankNet> computeAllBankNetsExcluding(String excludeRdf) {
        List<BankNet> all = computeAllBankNets();
        List<BankNet> out = new ArrayList<>();
        for (BankNet n : all) if (!n.bankRdf.equals(excludeRdf)) out.add(n);
        return out;
    }

    // ---------------------------------------------------------------
    // Overdue customers + behavior
    // ---------------------------------------------------------------

    private void addOverdueSection(LinearLayout content) {
        if (overdue == null || overdue.isEmpty()) return;
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("👥 مشتریان معوق — فرصت تامین از محل وصول", 13f, Theme.VIOLET, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("مشتریانی که سررسید فاکتورشان گذشته و ماندهٔ قابل وصول دارند — سیستم الگوی پرداخت هر مشتری را تحلیل کرده است"), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        int show = Math.min(overdue.size(), 5);
        for (int i = 0; i < show; i++) {
            Row o = overdue.get(i);
            String code = o.s("code");
            String party = o.s("party");
            double amt = o.d("amount");
            String due = o.s("dueDate");
            long days = o.l("days");
            if (days < 0) days = Jalali.diffDays(due, Jalali.todayStr());
            // Find behavior if available
            BehaviorAnalysis beh = findBehavior(code);

            LinearLayout row = a.kit.v();
            row.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
            row.setBackground(Theme.card());

            LinearLayout top = a.kit.h();
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout copy = a.kit.v();
            TextView name = a.kit.text(party, 13f, Theme.TEXT, true);
            copy.addView(name, a.kit.lp(-1, -2));
            String meta = "فاکتور " + Money.fa(o.s("invoice")) + " • سررسید " + Jalali.dispFa(due);
            if (days >= 0) meta += " • " + Money.fa(String.valueOf(days)) + " روز تاخیر";
            TextView metaTv = a.kit.text(meta, 10f, Theme.MUTED, false);
            copy.addView(metaTv, a.kit.lp(-1, -2));
            top.addView(copy, a.kit.wlp(1f));
            TextView amtTv = a.kit.text(RialFormatter.formatRial(amt), 12f, Theme.TEXT, true);
            top.addView(amtTv, a.kit.lp(-2, -2));
            row.addView(top, a.kit.lp(-1, -2));

            if (beh != null) {
                LinearLayout behRow = a.kit.h();
                behRow.setGravity(Gravity.CENTER_VERTICAL);
                behRow.addView(a.kit.pill(beh.patternLabel, beh.patternColor), a.kit.lp(-2, -2));
                behRow.addView(a.kit.space(6));
                String detail = "میانگین تاخیر " + Money.fa(String.valueOf(Math.round(beh.avgDelayDays))) + " روز • از " + Money.fa(String.valueOf(beh.sampleCount)) + " تسویه";
                TextView d = a.kit.text(detail, 10f, Theme.MUTED, false);
                behRow.addView(d, a.kit.wlp(1f));
                // Reliability bar
                View bar = a.kit.progress(beh.reliabilityScore/100.0, beh.patternColor);
                LinearLayout.LayoutParams bp = a.kit.lp(Theme.dp(60), Theme.dp(6));
                bp.setMargins(Theme.dp(6), 0, 0, 0);
                behRow.addView(bar, bp);
                row.addView(behRow, a.kit.lp(-1, -2));

                // Estimate collectible
                long est = estimateCollectible(beh, amt);
                String estTxt = "وصول تخمینی این هفته: " + RialFormatter.formatRial(est);
                if (beh.reliabilityScore >= 70 && days <= 15) {
                    row.addView(a.kit.text(estTxt + " ✓ خوش‌حساب — تماس بگیرید", 10.5f, Theme.SUCCESS, true), a.kit.lp(-1, -2));
                } else if (beh.reliabilityScore >= 40) {
                    row.addView(a.kit.text(estTxt + " — پیگیری متوسط", 10.5f, Theme.WARNING, false), a.kit.lp(-1, -2));
                } else {
                    row.addView(a.kit.text("ریسک بالا — " + Money.fa(String.valueOf(Math.round(beh.avgDelayDays))) + " روز تاخیر میانگین", 10.5f, Theme.DANGER, false), a.kit.lp(-1, -2));
                }
            } else {
                row.addView(a.kit.hint("تحلیل رفتاری: در حال محاسبه… (نیاز به سابقه پرداخت این مشتری در سیستم)"), a.kit.lp(-1, -2));
            }

            // Call button
            LinearLayout actions = a.kit.h();
            actions.setGravity(Gravity.END);
            final String fcode = code;
            final String fname = party;
            TextView btn = a.kit.text("📞 تماس/پیگیری", 10f, Theme.VIOLET, true);
            btn.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
            btn.setBackground(Theme.ghostButton(Theme.VIOLET));
            Theme.pressable(btn);
            btn.setOnClickListener(v -> {
                // Navigate to customer dossier
                ir.meelano.manager.screens.Screen cs = a.screen("customers");
                if (cs instanceof CustomersScreen) ((CustomersScreen) cs).openDossier(fcode);
                a.nav("customers");
            });
            actions.addView(btn, a.kit.lp(-2, -2));
            row.addView(actions, a.kit.lp(-1, -2));

            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            card.addView(row, p);
        }
        if (overdue.size() > show) card.addView(a.kit.hint("و " + Money.fa(String.valueOf(overdue.size() - show)) + " مشتری معوق دیگر… در گزارش «فاکتورهای معوق» ببینید"), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private BehaviorAnalysis findBehavior(String code) {
        for (BehaviorAnalysis b : behaviors) if (b.code.equals(code)) return b;
        return null;
    }

    private long estimateCollectible(BehaviorAnalysis beh, double invoiceAmount) {
        // Heuristic: if reliability high, estimate 80% collectible this week; medium 40%; low 15%
        double factor = beh.reliabilityScore >= 70 ? 0.8 : beh.reliabilityScore >= 40 ? 0.4 : 0.15;
        // Adjust by overdue days: longer overdue = lower chance
        if (beh.daysOverdue > 60) factor *= 0.5;
        else if (beh.daysOverdue > 30) factor *= 0.7;
        return Math.round(invoiceAmount * factor);
    }

    private void loadBehaviors() {
        if (overdue == null || overdue.isEmpty()) return;
        // For each overdue customer (top 8 to limit queries), fetch history
        int limit = Math.min(8, overdue.size());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < limit; i++) codes.add(overdue.get(i).s("code"));

        // We will run one repo call per customer sequentially, but we can batch in one connection with loop
        a.repo.run(c -> {
            Meta m = new Meta(c);
            List<BehaviorAnalysis> out = new ArrayList<>();
            for (String code : codes) {
                if (code == null || code.isEmpty()) continue;
                BehaviorAnalysis b = new BehaviorAnalysis();
                b.code = code;
                // Find overdue row for name/amount/due
                for (Row o : overdue) if (code.equals(o.s("code"))) {
                    b.name = o.s("party");
                    b.cell = ""; // will fill via customerBalance if needed
                    b.overdueAmount = o.d("amount");
                    b.dueDate = o.s("dueDate");
                    long dd = o.l("days");
                    if (dd < 0) dd = Jalali.diffDays(b.dueDate, Jalali.todayStr());
                    b.daysOverdue = (int) dd;
                    break;
                }
                try {
                    // Customer balance/credit
                    Row bal = Repo.one(c, ChequeLiquidityQueries.customerBalance(m, code));
                    b.balance = Math.round(bal.d("balance"));
                    b.credit = Math.round(bal.d("credit"));
                    b.cell = bal.s("cell");
                    if (b.name == null || b.name.isEmpty() || b.name.equals("—")) b.name = bal.s("name");
                } catch (Exception e) { }

                try {
                    List<Row> hist = Repo.exec(c, ChequeLiquidityQueries.customerPaymentHistory(m, code, 30));
                    if (hist != null && !hist.isEmpty()) {
                        double totalDelay = 0;
                        int valid = 0;
                        for (Row h : hist) {
                            String due = h.s("dueDate");
                            String pay = h.s("payDate");
                            if (due.isEmpty() || pay.isEmpty()) continue;
                            int delay = Jalali.diffDays(due, pay);
                            if (delay < -1) continue; // invalid
                            totalDelay += Math.max(0, delay);
                            valid++;
                        }
                        if (valid > 0) {
                            b.avgDelayDays = totalDelay / valid;
                            b.sampleCount = valid;
                            // Reliability: lower delay = higher score. Also sample count matters.
                            // 0-3 days avg delay => 90-100, 4-7 => 70-89, 8-15 => 40-69, 16-30 => 20-39, >30 => 0-19
                            double d = b.avgDelayDays;
                            if (d <= 3) b.reliabilityScore = 95 - d * 2;
                            else if (d <= 7) b.reliabilityScore = 85 - (d - 3) * 3;
                            else if (d <= 15) b.reliabilityScore = 65 - (d - 7) * 2;
                            else if (d <= 30) b.reliabilityScore = 35 - (d - 15) * 1;
                            else b.reliabilityScore = Math.max(5, 20 - (d - 30) * 0.3);
                            if (valid < 3) b.reliabilityScore *= 0.7; // penalize low sample
                            if (b.reliabilityScore >= 70) { b.patternLabel = "خوش‌حساب ✓"; b.patternColor = Theme.SUCCESS; }
                            else if (b.reliabilityScore >= 40) { b.patternLabel = "متوسط ◐"; b.patternColor = Theme.WARNING; }
                            else { b.patternLabel = "بدحساب ⚠"; b.patternColor = Theme.DANGER; }
                        } else {
                            b.avgDelayDays = -1;
                            b.patternLabel = "بدون سابقه تسویه";
                            b.patternColor = Theme.MUTED;
                            b.reliabilityScore = 30;
                        }
                    } else {
                        b.patternLabel = "بدون سابقه تسویه کافی";
                        b.patternColor = Theme.MUTED;
                        b.reliabilityScore = 30;
                        b.avgDelayDays = -1;
                    }
                } catch (Exception e) {
                    b.patternLabel = "تحلیل ممکن نشد";
                    b.patternColor = Theme.MUTED;
                    b.reliabilityScore = 30;
                }
                out.add(b);
            }
            // Sort by best collectible opportunity: reliability desc, overdue amount desc
            Collections.sort(out, (x,y) -> {
                int cmp = Double.compare(y.reliabilityScore, x.reliabilityScore);
                if (cmp != 0) return cmp;
                return Double.compare(y.overdueAmount, x.overdueAmount);
            });
            return out;
        }, new Repo.Cb<List<BehaviorAnalysis>>() {
            @Override public void ok(List<BehaviorAnalysis> res) {
                behaviors = res == null ? new ArrayList<>() : res;
                // Re-render is expensive; instead we can patch overdue section if visible
                // For simplicity, re-render whole screen but preserve scroll? Just rebuild
                try {
                    LinearLayout cur = findContentParent();
                    if (cur != null) build(cur);
                } catch (Exception ignored) {}
            }
            @Override public void fail(String e) { }
        });
    }

    // ---------------------------------------------------------------
    // Calendar heatmap
    // ---------------------------------------------------------------

    private void addCalendarHeatmap(LinearLayout content) {
        if (cheques == null || cheques.isEmpty()) return;
        // Group by sardate
        Map<String, List<Row>> byDay = new LinkedHashMap<>();
        Map<String, Long> sumByDay = new LinkedHashMap<>();
        for (Row r : cheques) {
            String d = Jalali.disp(r.s("sardate"));
            if (d.isEmpty()) continue;
            if (!byDay.containsKey(d)) { byDay.put(d, new ArrayList<>()); sumByDay.put(d, 0L); }
            byDay.get(d).add(r);
            sumByDay.put(d, sumByDay.get(d) + Math.round(r.d("amount")));
        }
        if (byDay.isEmpty()) return;
        List<String> sortedDays = new ArrayList<>(byDay.keySet());
        Collections.sort(sortedDays);
        // Find max sum for bar scaling
        long max = 0; for (long v : sumByDay.values()) max = Math.max(max, v);

        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("📅 تقویم سررسید — توزیع چک‌ها در روزها", 13f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("میزان تجمع چک در هر روز — روزهای شلوغ نیاز به تامین بیشتری دارند"), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));
        int show = Math.min(12, sortedDays.size());
        // Show earliest upcoming first (future), but we have sorted asc which is earliest first (may be past). Filter to today+
        String today = Jalali.todayStr();
        List<String> upcoming = new ArrayList<>();
        for (String d : sortedDays) if (d.compareTo(today) >= 0) upcoming.add(d);
        if (upcoming.isEmpty()) upcoming = sortedDays;
        show = Math.min(10, upcoming.size());
        for (int i = 0; i < show; i++) {
            String d = upcoming.get(i);
            long sum = sumByDay.get(d);
            int cnt = byDay.get(d).size();
            LinearLayout row = a.kit.h();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView dateTv = a.kit.text(Jalali.dispFa(d) + " • " + Jalali.weekday(d), 11f, Theme.TEXT, true);
            dateTv.setMinWidth(Theme.dp(110));
            row.addView(dateTv, a.kit.lp(Theme.dp(120), -2));
            // Bar
            float frac = max > 0 ? (float) sum / max : 0f;
            View bar = a.kit.progress(frac, Theme.GOLD);
            LinearLayout.LayoutParams bp = a.kit.lp(0, Theme.dp(14));
            bp.weight = 1f;
            row.addView(bar, bp);
            row.addView(a.kit.space(6));
            TextView amt = a.kit.text(RialFormatter.formatRial(sum) + " • " + Money.fa(String.valueOf(cnt)) + " فقره", 10f, Theme.TEXT, false);
            row.addView(amt, a.kit.lp(-2, -2));
            card.addView(row, a.kit.lp(-1, -2));
            if (i < show -1) card.addView(a.kit.gap(4));
        }
        if (upcoming.size() > show) card.addView(a.kit.hint("و " + Money.fa(String.valueOf(upcoming.size() - show)) + " روز دیگر…"), a.kit.lp(-1, -2));
        // Critical day warning
        String peakDay = null; long peakSum = 0;
        for (Map.Entry<String, Long> e : sumByDay.entrySet()) if (e.getValue() > peakSum) { peakSum = e.getValue(); peakDay = e.getKey(); }
        if (peakDay != null && peakSum > 0) {
            card.addView(a.kit.gap(8));
            LinearLayout warn = a.kit.h();
            warn.setBackground(Theme.pill(Theme.WARNING));
            warn.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
            warn.addView(a.kit.text("روز اوج: " + Jalali.dispFa(peakDay) + " — " + RialFormatter.formatRial(peakSum), 11f, Theme.WARNING, true), a.kit.wlp(1f));
            card.addView(warn, a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    // ---------------------------------------------------------------
    // Professional extra suggestions
    // ---------------------------------------------------------------

    private void addProfessionalSuggestions(LinearLayout content, String bankRdf) {
        LinearLayout card = a.kit.card(Theme.GOLD_SOFT);
        card.addView(a.kit.text("✦ پیشنهادهای حرفه‌ای تکمیلی", 14f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        // 1
        card.addView(suggestionRow("۱. تمدید هوشمند سررسید",
                "چک‌هایی که سررسیدشان در ۳ روز آینده و مبلغشان بالاست را شناسایی کنید؛ با طرف‌حساب برای تمدید ۷–۱۵ روزه مذاکره کنید تا فشار نقدینگی پخش شود.",
                Theme.INFO, v -> showRescheduleDialog()), a.kit.lp(-1, -2));
        // 2
        card.addView(suggestionRow("۲. اولویت‌بندی پرداخت",
                "چک‌ها را بر اساس «اهمیت طرف‌حساب × نزدیکی سررسید × مبلغ» امتیازدهی کنید؛ چک‌های کم‌اهمیت‌تر را برای تامین دیرتر نگه دارید.",
                Theme.GOLD, null), a.kit.lp(-1, -2));
        // 3
        card.addView(suggestionRow("۳. صندوق نقدی و تنخواه",
                "موجودی صندوق‌های نقدی (COW) را هم در محاسبه بیاورید — حتی مبالغ خرد می‌توانند کسری‌های کوچک را پوشش دهند.",
                Theme.SUCCESS, null), a.kit.lp(-1, -2));
        // 4
        card.addView(suggestionRow("۴. خط اعتباری کوتاه‌مدت",
                "برای بانک‌هایی که مکرر کسری دارند، سقف اضافه‌برداشت موقت تعریف کنید؛ سیستم کسری را به‌عنوان «هشدار اعتباری» نشان می‌دهد.",
                Theme.VIOLET, null), a.kit.lp(-1, -2));
        // 5
        card.addView(suggestionRow("۵. یادآور خودکار",
                "برای هر چک ۴۸ ساعت قبل از سررسید، یادآور تامین به مدیر و حسابدار ارسال شود — از غافلگیری جلوگیری می‌کند.",
                Theme.WARNING, null), a.kit.lp(-1, -2));

        // Action buttons
        LinearLayout actions = a.kit.h();
        actions.addView(a.kit.btn("📋 گزارش تامین", v -> shareLiquidityReport()), a.kit.wlp(1f));
        actions.addView(a.kit.space(8));
        actions.addView(a.kit.btnGhost("پاک‌کردن موجودی‌ها", Theme.DANGER, v -> confirmClearBalances()), a.kit.wlp(1f));
        card.addView(a.kit.gap(8));
        card.addView(actions, a.kit.lp(-1, -2));

        a.kit.addCard(content, card);
    }

    private View suggestionRow(String title, String body, int accent, View.OnClickListener onClick) {
        LinearLayout r = a.kit.v();
        r.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        r.setBackground(Theme.cardAccent(accent));
        r.addView(a.kit.text(title, 12f, accent, true), a.kit.lp(-1, -2));
        r.addView(a.kit.text(body, 10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        if (onClick != null) {
            Theme.pressable(r);
            r.setOnClickListener(onClick);
        }
        LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
        p.setMargins(0, 0, 0, Theme.dp(8));
        r.setLayoutParams(p);
        return r;
    }

    private void showRescheduleDialog() {
        if (cheques == null || cheques.isEmpty()) { a.kit.toast("چکی برای پیشنهاد تمدید یافت نشد"); return; }
        String today = Jalali.todayStr();
        List<Row> urgent = new ArrayList<>();
        for (Row r : cheques) {
            String sd = Jalali.disp(r.s("sardate"));
            if (sd.isEmpty()) continue;
            int diff = Jalali.diffDays(today, sd);
            if (diff >= 0 && diff <= 7) urgent.add(r);
        }
        if (urgent.isEmpty()) { a.kit.toast("چک فوری برای ۷ روز آینده یافت نشد — وضعیت آرام است"); return; }
        Collections.sort(urgent, (x,y) -> Double.compare(y.d("amount"), x.d("amount")));
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text("چک‌های پیشنهادی برای تمدید (۷ روز آینده — مبلغ بالا اول)", 12f, Theme.TEXT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        int show = Math.min(6, urgent.size());
        for (int i = 0; i < show; i++) {
            Row r = urgent.get(i);
            body.addView(a.kit.kv("چک " + Money.fa(r.s("num")) + " • " + Jalali.dispFa(r.s("sardate")), RialFormatter.formatRial(r.d("amount")) + " • " + r.s("customer"), Theme.TEXT), a.kit.lp(-1, -2));
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar).setView(sv).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("بستن", v -> dlg.dismiss()), a.kit.lp(-1, -2));
        dlg.show();
    }

    private void confirmClearBalances() {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.text("آیا همه موجودی‌های دستی پاک شود؟", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.hint("این عمل موجودی‌های واردشده برای همه بانک‌ها را حذف می‌کند"), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(12));
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar).setView(body).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGhost("انصراف", Theme.MUTED, v -> dlg.dismiss()), a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(a.kit.btn("پاک‌کردن", v -> { BankBalanceStore.clearAll(a); manualBalances.clear(); dlg.dismiss(); a.kit.toast("موجودی‌ها پاک شد"); render(findContentParent()); }), a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        dlg.show();
    }

    private void shareLiquidityReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("گزارش تامین چک — ").append(Jalali.todayStr()).append("\n\n");
        for (Row s : bankSummaries) {
            long bal = manualBalances.getOrDefault(s.s("bankRdf"), 0L);
            Double posD = posByBankCache.get(s.s("bankRdf"));
            long pos = posD == null ? 0L : Math.round(posD);
            long chk = Math.round(s.d("total"));
            long net = bal + pos - chk;
            sb.append(s.s("bank")).append(": چک ").append(RialFormatter.formatRial(chk))
              .append(" | پوز ").append(RialFormatter.formatRial(pos))
              .append(" | موجودی ").append(RialFormatter.formatRial(bal))
              .append(" | ").append(net >= 0 ? "مازاد " : "کسری ").append(RialFormatter.formatRial(Math.abs(net))).append("\n");
        }
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
            a.startActivity(android.content.Intent.createChooser(i, "اشتراک گزارش تامین چک"));
        } catch (Exception e) { a.kit.toast("اشتراک ممکن نشد"); }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private LinearLayout findContentParent() {
        return lastContentRef;
    }

    private View buildHeroCard() {
        LinearLayout c = a.kit.v();
        c.setBackground(Theme.hero(accent()));
        c.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(15));
        LinearLayout head = a.kit.h();
        TextView g = a.kit.text(glyph(), 30, accent(), true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Theme.avatar(Theme.alpha(accent(), 60)));
        head.addView(g, new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58)));
        head.addView(a.kit.space(10));
        LinearLayout copy = a.kit.v();
        copy.addView(a.kit.title(title()), a.kit.lp(-1, -2));
        copy.addView(a.kit.sub("چک‌های پرداختی • موجودی + پوز فردا = تامین"), a.kit.lp(-1, -2));
        head.addView(copy, a.kit.wlp(1f));
        c.addView(head, a.kit.lp(-1, -2));
        return c;
    }

    public void render(LinearLayout content, boolean isCache) { render(content); }

    private static class Calculation {
        long includedTotal = 0;
        long excludedTotal = 0;
    }

    private Calculation calcForCheques(List<Row> list, String bankRdfFilter, String searchFilter) {
        Calculation c = new Calculation();
        for (Row r : list) {
            if (bankRdfFilter != null && !bankRdfFilter.isEmpty() && !bankRdfFilter.equals(r.s("bankRdf"))) continue;
            String rdf = r.s("rdf");
            String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf;
            long amt = Math.round(r.d("amount"));
            if (excludedRdfs.contains(key)) c.excludedTotal += amt; else c.includedTotal += amt;
        }
        return c;
    }

    private int countIncluded(List<Row> list) {
        int n = 0;
        for (Row r : list) {
            String rdf = r.s("rdf");
            String key = (rdf == null || rdf.isEmpty()) ? r.s("num") + "|" + r.s("amount") + "|" + r.s("sardate") : rdf;
            if (!excludedRdfs.contains(key)) n++;
        }
        return n;
    }

    private static String formatAgo(long ts) {
        if (ts <= 0) return "—";
        long diff = System.currentTimeMillis() - ts;
        long mins = diff / 60000;
        if (mins < 1) return "همین الان";
        if (mins < 60) return Money.fa(String.valueOf(mins)) + " دقیقه پیش";
        long hrs = mins / 60;
        if (hrs < 24) return Money.fa(String.valueOf(hrs)) + " ساعت پیش";
        long days = hrs / 24;
        return Money.fa(String.valueOf(days)) + " روز پیش";
    }

}
