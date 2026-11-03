package ir.meelano.manager.screens;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.ViewGroup;
import java.util.Locale;
import ir.meelano.manager.core.MarketWeather;
import ir.meelano.manager.core.Diag;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.AlertEngine;
import ir.meelano.manager.core.BankStatementReconciliation;
import ir.meelano.manager.core.BankStatementFile;
import ir.meelano.manager.core.BankStatementPeriodReconciliation;
import ir.meelano.manager.core.CashCloseMath;
import ir.meelano.manager.core.FinanceReports;
import ir.meelano.manager.core.MoneyBreakdown;
import ir.meelano.manager.core.CashWaterfallEngine;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.FollowUps;
import ir.meelano.manager.core.HomeRange;
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.MonthlyGoalEngine;
import ir.meelano.manager.core.SeasonalCalendarEngine;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.SalesComparison;
import ir.meelano.manager.core.SalesForecastEngine;
import ir.meelano.manager.core.SalesSnapshot;
import ir.meelano.manager.core.SmartReportEngine;
import ir.meelano.manager.core.SyncState;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.HomeCashWaterfall;
import ir.meelano.manager.ui.HomeGoalGauge;
import ir.meelano.manager.ui.HomeForecastChart;
import ir.meelano.manager.ui.HomePaymentMixBar;
import ir.meelano.manager.ui.HomeSalesChart;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.SmartReportImage;
import ir.meelano.manager.ui.Theme;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executive dashboard: greeting, daily cash flow, operational alerts and compact sales analysis. */
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
        String snapshotDay = "";
        Row salesDay;
        Row buyDay;
        Row inDay;
        Row outDay;
        List<Row> buyDaily = new ArrayList<>();
        List<Row> inDaily = new ArrayList<>();
        List<Row> outDaily = new ArrayList<>();
        List<Row> inMix = new ArrayList<>();
        List<Row> returnsPrevious = new ArrayList<>();
        boolean buyTrendAvailable;
        boolean inTrendAvailable;
        boolean outTrendAvailable;
        boolean inMixAvailable;
        boolean returnsAvailable;
        boolean returnsPreviousAvailable;
        List<Row> overdue = new ArrayList<>();
        boolean overdueAvailable;
        List<Row> salesDaily = new ArrayList<>();
        SalesSnapshot salesSnapshot;
        boolean salesTrendAvailable;
        String salesHistoryFrom = "";
        List<Row> topItems = new ArrayList<>();
        boolean topItemsAvailable;
        List<Row> topItemsMonth = new ArrayList<>();
        List<Row> topCustomersToday = new ArrayList<>();
        List<Row> topCustomersMonth = new ArrayList<>();
        boolean topItemsMonthAvailable;
        boolean topCustomersTodayAvailable;
        boolean topCustomersMonthAvailable;
        List<Row> stockAlerts = new ArrayList<>();
        List<Row> purchasePriceTrend = new ArrayList<>();
        boolean stockAlertsAvailable;
        boolean reorderPointAvailable;
        boolean purchasePriceTrendAvailable;
        List<Row> monthlySales = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
        boolean dueInAvailable;
        boolean dueOutAvailable;
        /** Return documents (اسناد برگشتی) of the last 30 days, grouped by type. */
        List<Row> returns = new ArrayList<>();
        List<Row> returnsToday = new ArrayList<>();
        boolean returnsTodayAvailable;
        Row cashReceipts;
        Row cashPayments;
        boolean cashReceiptsAvailable;
        boolean cashPaymentsAvailable;
        List<Row> bankAccounts = new ArrayList<>();
        boolean bankAccountsAvailable;
        List<Row> bankTurnover = new ArrayList<>();
        boolean bankTurnoverAvailable;
        List<Row> overpaidVouchers = new ArrayList<>();
        boolean overpaidVouchersAvailable;
        List<Row> unbalancedJournals = new ArrayList<>();
        boolean unbalancedJournalsAvailable;
        List<Row> grossProfitDaily = new ArrayList<>();
        boolean grossProfitAvailable;
        Map<String, String> notes = new LinkedHashMap<>();
    }

    /** In-memory-only import draft. It is discarded after review; only daily totals can be saved. */
    private static final class BankImportDraft {
        BankStatementFile.Table table;
        BankStatementFile.Mapping mapping;
        BankStatementFile.Parsed parsed;
        BankStatementPeriodReconciliation.Report report;
        Data dashboardData;
        LinearLayout content;
        String fromDay = "";
        String toDay = "";
        String bankId = "";
        String bankName = "";
        int renderToken;
        long comparedAt;
    }

    /** Runs one dashboard query only while this render is still on screen; once the user has moved on, the rest are skipped. */
    private <T> T live(final int token, Map<String, String> notes, String key, Soft<T> t) {
        if (a.renderGen() != token) return null;
        return soft(notes, key, t);
    }

    /** Holds the dashboard's live connection; reopens it if the link dropped during the long load. */
    private static final class Link {
        java.sql.Connection c;
        Meta m;

        Link(java.sql.Connection c) {
            this.c = c;
            this.m = new Meta(c);
        }

        void reopen(Repo repo) throws Exception {
            java.sql.Connection old = c;
            c = repo.reopen();
            m = new Meta(c);
            if (old != null) {
                try { old.close(); } catch (Exception e) { Diag.ignored("HomeScreen:close", e); }
            }
        }

        /** True when the failure came from the connection itself (not from the query). */
        static boolean dropped(Throwable e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof java.sql.SQLException) {
                    String st = ((java.sql.SQLException) t).getSQLState();
                    if (st != null && (st.startsWith("08") || st.equals("HY010"))) return true;
                }
                String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.US);
                if (msg.contains("connection object is closed") || msg.contains("connection is closed")
                        || msg.contains("communication link") || msg.contains("connection reset")) return true;
            }
            return false;
        }
    }

    /** Run the query directly; on a dropped link reopen once and retry instead of probing every query. */
    private <T> T liveL(final int token, final Link L, Map<String, String> notes, String key, Soft<T> t) {
        if (a.renderGen() != token) return null;
        try {
            return t.run();
        } catch (Exception first) {
            if (!Link.dropped(first)) {
                notes.put(key, first instanceof Queries.Missing ? first.getMessage() : ir.meelano.manager.data.Atiran.diagnose(first));
                return null;
            }
            try {
                L.reopen(a.repo);
                return t.run();
            } catch (Exception again) {
                notes.put(key, again instanceof Queries.Missing ? again.getMessage() : ir.meelano.manager.data.Atiran.diagnose(again));
                return null;
            }
        }
    }

    /** Compact sync status: data age and refresh state stay visible without a tall hero banner. */
    private View syncStatus(String lastReceived, SyncState state, String errorDetail,
                            View.OnClickListener retry) {
        String timestamp = lastReceived == null ? "" : lastReceived.trim();
        int accent;
        int iconRes;
        String title;
        String detail;
        switch (state) {
            case FRESH:
                accent = Theme.SUCCESS;
                iconRes = R.drawable.mi_check_circle;
                title = "داشبورد به‌روز است";
                detail = timestamp.isEmpty() ? "اطلاعات فروشگاه دریافت شد" : "آخرین دریافت · " + timestamp;
                break;
            case REFRESHING:
                accent = Theme.GOLD;
                iconRes = R.drawable.mi_sync;
                title = "در حال همگام‌سازی";
                detail = timestamp.isEmpty() ? "داده‌های فروشگاه در حال دریافت است"
                        : "آخرین دریافت · " + timestamp;
                break;
            case STALE:
                accent = Theme.WARNING;
                iconRes = R.drawable.mi_history;
                title = "تازه‌سازی انجام نشد";
                detail = timestamp.isEmpty() ? "داده‌ای برای نمایش ذخیره نشده است"
                        : "آخرین دریافت · " + timestamp;
                break;
            case OFFLINE:
                accent = Theme.MUTED;
                iconRes = R.drawable.mi_wifi_off;
                title = "آفلاین · دادهٔ ذخیره‌شده";
                detail = timestamp.isEmpty() ? "شبکه در دسترس نیست؛ برای تلاش دوباره بکشید"
                        : "آخرین دریافت · " + timestamp;
                break;
            case ERROR:
            default:
                accent = Theme.DANGER;
                iconRes = R.drawable.mi_error;
                title = "خطا در دریافت داشبورد";
                detail = errorDetail == null || errorDetail.trim().isEmpty()
                        ? "برای تلاش دوباره، صفحه را پایین بکشید"
                        : errorDetail.trim();
                break;
        }

        return a.kit.syncPill(iconRes, title, detail, accent,
                state == SyncState.REFRESHING, retry);
    }

    @Override
    public void render(final LinearLayout content) {

        final int token = a.renderGen();
        // Smart first paint: the last saved dashboard appears at once and live data replaces it when ready.
        final String[] cachedTs = {""};
        final Data cached = loadCached(cachedTs);
        // Instrumentation-only preview: render its seeded cache without attempting a live DB refresh.
        // The intent flag is ignored in non-debug builds, so normal users always get live refreshes.
        if (cached != null && (a.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && a.getIntent() != null
                && a.getIntent().getBooleanExtra("ir.meelano.manager.HOME_SCREENSHOT_PREVIEW", false)) {
            syncWasRefreshing = false;
            build(content, cached, SyncState.FRESH, cachedTs[0]);
            return;
        }
        if (cached != null) {
            syncWasRefreshing = true;
            build(content, cached, SyncState.REFRESHING, cachedTs[0]);
        } else {
            content.removeAllViews();
            syncWasRefreshing = true;
            content.addView(greetingHero(cached), a.kit.lp(-1, -2));
            content.addView(syncStatus("", SyncState.REFRESHING, null, null), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(4));
            addTopQuickActions(content);
            if (RoleStore.allowed(a, "sales")) {
                content.addView(salesHero(null, SyncState.REFRESHING), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
            }
            // Market data is independent of Atiran; keep its first paint within the user's Home layout.
            if (homeSectionVisible("market")) {
                content.addView(marketCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
            }
            content.addView(a.kit.loading("در حال دریافت داشبورد مدیریتی…"), a.kit.lp(-1, -2));
        }
        localizeHomeNumbers(content);

        // Two groups run in parallel on their own connections (the runner has three workers).
        final Data shared = new Data();
        final int[] left = {2};
        final boolean[] kpiOk = {false};
        final boolean[] chartsOk = {false};
        final String[] kpiErr = {null};
        final Runnable settle = () -> {
            if (a.renderGen() != token) return;
            try {
                rebuildSalesSnapshot(shared);
                if (kpiOk[0] || (chartsOk[0] && shared.salesSnapshot != null
                        && shared.salesSnapshot.isAvailable())) {
                    saveCache(shared);
                    build(content, shared, SyncState.FRESH, currentTimeLabel());
                } else {
                    showFailure(content, kpiErr[0], cached, cachedTs[0]);
                }
            } catch (RuntimeException renderError) {
                // Data/query failures are already reported by Repo. Keep an unexpected
                // dashboard rendering error from terminating the main/UI thread.
                a.reportRenderFailure(content, "HomeScreen:settle", renderError);
            }
        };
        final Runnable[] done = {null};
        done[0] = () -> { if (--left[0] == 0) settle.run(); };

        a.repo.run(c0 -> loadKpis(token, c0), new Repo.Cb<Data>() {
            @Override
            public void ok(Data g) {
                if (a.renderGen() != token) return;
                mergeKpis(shared, g);
                kpiOk[0] = true;
                done[0].run();
            }

            @Override
            public void fail(String faError) {
                kpiErr[0] = faError;
                done[0].run();
            }
        });
        a.repo.run(c0 -> loadCharts(token, c0), new Repo.Cb<Data>() {
            @Override
            public void ok(Data g) {
                if (a.renderGen() != token) return;
                mergeCharts(shared, g);
                chartsOk[0] = true;
                done[0].run();
            }

            @Override
            public void fail(String faError) {
                shared.notes.put("نمودارها", faError == null ? "" : faError);
                done[0].run();
            }
        });
    }

    /** Daily operating figures, overdue alerts and active returns. */
    private Data loadKpis(final int token, java.sql.Connection c0) throws Exception {
        final Link L = new Link(c0);
        Data d = new Data();
        HomeRange todayRange = HomeRange.today(Jalali.todayStr());
        final String to = todayRange.to;
        d.snapshotDay = todayRange.to;
        // Sales are deliberately not queried through a separate latest-data KPI path:
        // the daily snapshot below is the only source for every Home sales amount.
        // Purchases, receipts and payments use this same explicit calendar day.
        HomeRange sparkRange = HomeRange.last7Days(todayRange.to);
        d.buyDaily = liveL(token, L, d.notes, "خرید ۷ روزه",
                () -> Repo.exec(L.c, Queries.factorDaily(L.m, false, sparkRange.from, sparkRange.to)));
        d.buyTrendAvailable = d.buyDaily != null;
        d.buyDay = HomeRange.aggregateDay(d.buyDaily, todayRange.to);
        d.inDaily = liveL(token, L, d.notes, "دریافت ۷ روزه",
                () -> Repo.exec(L.c, MoneyQueries.darDaily(L.m, 0, sparkRange.from, sparkRange.to)));
        d.inTrendAvailable = d.inDaily != null;
        d.outDaily = liveL(token, L, d.notes, "پرداخت ۷ روزه",
                () -> Repo.exec(L.c, MoneyQueries.darDaily(L.m, 1, sparkRange.from, sparkRange.to)));
        d.outTrendAvailable = d.outDaily != null;
        d.inDay = liveL(token, L, d.notes, "دریافت امروز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 0, todayRange.to)));
        Filter todayMix = new Filter();
        todayMix.from = todayRange.from;
        todayMix.to = todayRange.to;
        d.inMix = liveL(token, L, d.notes, "ترکیب دریافت امروز",
                () -> Repo.exec(L.c, MoneyQueries.paymentMix(L.m, todayMix)));
        d.inMixAvailable = d.inMix != null;
        d.outDay = liveL(token, L, d.notes, "پرداخت امروز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 1, todayRange.to)));
        d.overdue = liveL(token, L, d.notes, "معوق‌ها", () -> Repo.exec(L.c, Queries.overdueInvoices(L.m, 5)));
        d.overdueAvailable = d.overdue != null;
        String stFrom = Jalali.addDays(to, -29);
        Filter rf = new Filter();
        rf.from = stFrom;
        rf.to = to;
        d.returns = liveL(token, L, d.notes, "برگشتی", () -> Repo.exec(L.c, MasterQueries.returnsByKind(L.m, rf)));
        d.returnsAvailable = d.returns != null;
        Filter previousReturns = new Filter();
        previousReturns.from = Jalali.addDays(stFrom, -30);
        previousReturns.to = Jalali.addDays(stFrom, -1);
        d.returnsPrevious = liveL(token, L, d.notes, "برگشتی دورهٔ قبل",
                () -> Repo.exec(L.c, MasterQueries.returnsByKind(L.m, previousReturns)));
        d.returnsPreviousAvailable = d.returnsPrevious != null;
        boolean canCloseout = RoleStore.allowed(a, "closeout");
        boolean needsCashFlow = (canCloseout && homeSectionSelected("closeout"))
                || (RoleStore.allowed(a, "cash") && homeSectionSelected("cash_position"));
        if (needsCashFlow) {
            d.cashReceipts = liveL(token, L, d.notes, "تفکیک نقد دریافتی امروز",
                    () -> Repo.one(L.c, MoneyQueries.darSummary(L.m, todayMix, 0)));
            d.cashReceiptsAvailable = d.cashReceipts != null && d.cashReceipts.l("breakdownAvailable") > 0;
            d.cashPayments = liveL(token, L, d.notes, "تفکیک نقد پرداختی امروز",
                    () -> Repo.one(L.c, MoneyQueries.darSummary(L.m, todayMix, 1)));
            d.cashPaymentsAvailable = d.cashPayments != null && d.cashPayments.l("breakdownAvailable") > 0;
        }
        if (canCloseout && (homeSectionSelected("closeout") || homeSectionSelected("gross_profit"))) {
            Filter profitRange = new Filter();
            profitRange.from = Jalali.monthStart(to);
            profitRange.to = to;
            d.grossProfitDaily = liveL(token, L, d.notes, "سود ناخالص برآوردی",
                    () -> Repo.exec(L.c, MasterQueries.profitDaily(L.m, profitRange)));
            d.grossProfitAvailable = d.grossProfitDaily != null;
        }
        if (canCloseout && homeSectionSelected("closeout")) {
            d.returnsToday = liveL(token, L, d.notes, "برگشتی امروز",
                    () -> Repo.exec(L.c, MasterQueries.returnsByKindOn(L.m, todayMix)));
            d.returnsTodayAvailable = d.returnsToday != null;

            // Statement reconciliation is deliberately manager-only, date-scoped and read-only.
            // A missing date/direction column must never turn an unbounded total into a daily zero.
            d.bankAccounts = liveL(token, L, d.notes, "حساب‌های بانکی",
                    () -> Repo.exec(L.c, MasterQueries.lookupBanks(L.m)));
            d.bankAccountsAvailable = d.bankAccounts != null;
            boolean hasBankDate = L.m.colFlex("ban_act", "act_date", "done_act") != null;
            boolean hasBankIn = L.m.colFlex("ban_act", "act_bes", "bes", "bestankar") != null;
            boolean hasBankOut = L.m.colFlex("ban_act", "act_bed", "bed", "bedehkar") != null;
            if (!hasBankDate) {
                d.notes.put("گردش بانک روزانه", "ستون تاریخ معتبر در دفتر بانک پیدا نشد؛ گردش روزانه نمایش داده نمی‌شود.");
            } else if (!hasBankIn || !hasBankOut) {
                d.notes.put("گردش بانک روزانه", "ستون واریز یا برداشت در دفتر بانک پیدا نشد؛ تطبیق بانکی در دسترس نیست.");
            } else {
                d.bankTurnover = liveL(token, L, d.notes, "گردش بانک روزانه",
                        () -> Repo.exec(L.c, MasterQueries.bankTurnover(L.m, todayMix)));
                d.bankTurnoverAvailable = d.bankTurnover != null;
            }
            d.overpaidVouchers = liveL(token, L, d.notes, "قبض‌های دارای اضافه‌پرداخت",
                    () -> Repo.exec(L.c, FinanceReports.overpaidVouchers(L.m, todayMix, 5)));
            d.overpaidVouchersAvailable = d.overpaidVouchers != null;
            d.unbalancedJournals = liveL(token, L, d.notes, "اسناد حسابداری نامتوازن",
                    () -> Repo.exec(L.c, FinanceReports.unbalancedJournals(L.m, todayMix, 5)));
            d.unbalancedJournalsAvailable = d.unbalancedJournals != null;
        }
        if (d.salesDay == null && d.inDay == null && d.buyDay == null && d.outDay == null)
            throw new Exception(firstNote(d.notes));
        return d;
    }

    /** Chart group: daily/monthly sales, top items and cheque due lists. */
    private Data loadCharts(final int token, java.sql.Connection c0) throws Exception {
        final Link L = new Link(c0);
        Data d = new Data();
        final String to = Jalali.todayStr();
        final String from = chartsFrom(to);
        d.salesHistoryFrom = from;
        d.salesDaily = liveL(token, L, d.notes, "روند فروش", () -> Repo.exec(L.c, Queries.factorDaily(L.m, true, from, to)));
        d.salesTrendAvailable = d.salesDaily != null;
        Filter todayFilter = new Filter();
        todayFilter.from = to;
        todayFilter.to = to;
        todayFilter.top = 5;
        d.topItems = liveL(token, L, d.notes, "پرفروش‌ترین کالاهای امروز",
                () -> Repo.exec(L.c, Queries.factorTopProducts(L.m, true, todayFilter, 5)));
        d.topItemsAvailable = d.topItems != null;
        if (homeSectionSelected("top_performers") && RoleStore.allowed(a, "sales")) {
            Filter monthFilter = new Filter();
            monthFilter.from = Jalali.monthStart(to);
            monthFilter.to = to;
            monthFilter.top = 5;
            d.topCustomersToday = liveL(token, L, d.notes, "مشتری‌های برتر امروز",
                    () -> Repo.exec(L.c, Queries.factorByCustomer(L.m, true, todayFilter, 5)));
            d.topCustomersTodayAvailable = d.topCustomersToday != null;
            d.topCustomersMonth = liveL(token, L, d.notes, "مشتری‌های برتر ماه",
                    () -> Repo.exec(L.c, Queries.factorByCustomer(L.m, true, monthFilter, 5)));
            d.topCustomersMonthAvailable = d.topCustomersMonth != null;
            d.topItemsMonth = liveL(token, L, d.notes, "کالاهای برتر ماه",
                    () -> Repo.exec(L.c, Queries.factorTopProducts(L.m, true, monthFilter, 5)));
            d.topItemsMonthAvailable = d.topItemsMonth != null;
        }
        if (RoleStore.allowed(a, "products")) {
            d.reorderPointAvailable = L.m.col("inventory", "reopoint", "reorder_point") != null;
            d.stockAlerts = liveL(token, L, d.notes, "هشدار موجودی کالا",
                    () -> Repo.exec(L.c, MasterQueries.stockAlerts(L.m, 20)));
            d.stockAlertsAvailable = d.stockAlerts != null;
        } else {
            d.stockAlerts = null;
            d.stockAlertsAvailable = false;
        }
        // No date bounds: the query compares each item's two most recent purchase records,
        // rather than requiring both purchases to fall inside an arbitrary dashboard range.
        if (RoleStore.allowed(a, "buy")) {
            Filter priceHistory = new Filter();
            priceHistory.from = "";
            priceHistory.to = "";
            d.purchasePriceTrend = liveL(token, L, d.notes, "انحراف بهای خرید",
                    () -> Repo.exec(L.c, Queries.buyPriceTrend(L.m, priceHistory, 20)));
            d.purchasePriceTrendAvailable = d.purchasePriceTrend != null;
        } else {
            d.purchasePriceTrend = null;
            d.purchasePriceTrendAvailable = false;
        }
        // Monthly comparison is built from this same daily snapshot after both parallel
        // query groups settle, so it cannot drift from the chart or KPI.
        d.dueIn = liveL(token, L, d.notes, "سررسید دریافتی", () -> Repo.exec(L.c, MoneyQueries.chequeDue(L.m, true, 3)));
        d.dueInAvailable = d.dueIn != null;
        d.dueOut = liveL(token, L, d.notes, "سررسید پرداختی", () -> Repo.exec(L.c, MoneyQueries.chequeDue(L.m, false, 3)));
        d.dueOutAvailable = d.dueOut != null;
        return d;
    }

    /** History includes the full previous month plus the current Jalali month-to-date. */
    private static String chartsFrom(String to) {
        String start = HomeRange.salesHistoryStart(to);
        return start == null || start.isEmpty() ? Jalali.addDays(to, -13) : start;
    }

    /** Build every Home sales figure and month bucket from the one read-only daily snapshot. */
    private static void rebuildSalesSnapshot(Data d) {
        if (d == null) return;
        if (!d.salesTrendAvailable || d.salesDaily == null) {
            d.salesSnapshot = SalesSnapshot.empty(d.snapshotDay);
            d.salesDay = null;
            d.monthlySales = new ArrayList<>();
            return;
        }
        d.salesSnapshot = d.salesHistoryFrom == null || d.salesHistoryFrom.isEmpty()
                ? SalesSnapshot.fromRows(d.salesDaily, d.snapshotDay)
                : SalesSnapshot.fromRows(d.salesDaily, d.snapshotDay, d.salesHistoryFrom);
        d.salesDay = d.salesSnapshot.row(d.snapshotDay);
        List<Row> months = new ArrayList<>();
        for (SalesSnapshot.Month month : d.salesSnapshot.months()) {
            Row row = new Row();
            row.put("month", month.key);
            row.put("total", month.salesRials);
            row.put("docs", month.documents);
            months.add(row);
        }
        d.monthlySales = months;
    }

    private static void mergeKpis(Data s, Data g) {
        s.snapshotDay = g.snapshotDay;
        s.buyDay = g.buyDay;
        if (g.inDay != null) s.inDay = g.inDay;
        s.outDay = g.outDay;
        s.buyDaily = g.buyDaily;
        s.inDaily = g.inDaily;
        s.outDaily = g.outDaily;
        if (g.inMix != null) {
            s.inMix = g.inMix;
            s.inMixAvailable = g.inMixAvailable;
        }
        s.returnsPrevious = g.returnsPrevious;
        s.buyTrendAvailable = g.buyTrendAvailable;
        s.inTrendAvailable = g.inTrendAvailable;
        s.outTrendAvailable = g.outTrendAvailable;
        s.returnsAvailable = g.returnsAvailable;
        s.returnsPreviousAvailable = g.returnsPreviousAvailable;
        s.overdue = g.overdue;
        s.overdueAvailable = g.overdueAvailable;
        s.returns = g.returns;
        s.returnsToday = g.returnsToday;
        s.returnsTodayAvailable = g.returnsTodayAvailable;
        s.cashReceipts = g.cashReceipts;
        s.cashPayments = g.cashPayments;
        s.cashReceiptsAvailable = g.cashReceiptsAvailable;
        s.cashPaymentsAvailable = g.cashPaymentsAvailable;
        s.bankAccounts = g.bankAccounts;
        s.bankAccountsAvailable = g.bankAccountsAvailable;
        s.bankTurnover = g.bankTurnover;
        s.bankTurnoverAvailable = g.bankTurnoverAvailable;
        s.overpaidVouchers = g.overpaidVouchers;
        s.overpaidVouchersAvailable = g.overpaidVouchersAvailable;
        s.unbalancedJournals = g.unbalancedJournals;
        s.unbalancedJournalsAvailable = g.unbalancedJournalsAvailable;
        s.grossProfitDaily = g.grossProfitDaily;
        s.grossProfitAvailable = g.grossProfitAvailable;
        s.notes.putAll(g.notes);
    }

    private static void mergeCharts(Data s, Data g) {
        s.salesDaily = g.salesDaily;
        s.salesTrendAvailable = g.salesTrendAvailable;
        s.salesHistoryFrom = g.salesHistoryFrom;
        s.topItems = g.topItems;
        s.topItemsAvailable = g.topItemsAvailable;
        s.topItemsMonth = g.topItemsMonth;
        s.topCustomersToday = g.topCustomersToday;
        s.topCustomersMonth = g.topCustomersMonth;
        s.topItemsMonthAvailable = g.topItemsMonthAvailable;
        s.topCustomersTodayAvailable = g.topCustomersTodayAvailable;
        s.topCustomersMonthAvailable = g.topCustomersMonthAvailable;
        s.stockAlerts = g.stockAlerts;
        s.stockAlertsAvailable = g.stockAlertsAvailable;
        s.reorderPointAvailable = g.reorderPointAvailable;
        s.purchasePriceTrend = g.purchasePriceTrend;
        s.purchasePriceTrendAvailable = g.purchasePriceTrendAvailable;
        s.monthlySales = g.monthlySales;
        s.dueIn = g.dueIn;
        s.dueOut = g.dueOut;
        s.dueInAvailable = g.dueInAvailable;
        s.dueOutAvailable = g.dueOutAvailable;
        s.notes.putAll(g.notes);
    }

    private void showFailure(final LinearLayout content, String faError, Data cached, String cachedTs) {
        SyncState failureState = SyncState.afterFailure(cached != null,
                ir.meelano.manager.core.Net.online(a));
        if (cached != null) {
            build(content, cached, failureState, cachedTs);
            LinearLayout bc = homeCard(Theme.SECTION_ALERT);
            bc.addView(a.kit.text("جزئیات اتصال", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
            bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
            content.addView(bc, 2);
            content.addView(a.kit.gap(8), 3);
            localizeHomeNumbers(content);
            return;
        }
        content.removeAllViews();
        content.addView(greetingHero(null), a.kit.lp(-1, -2));
        content.addView(syncStatus("", failureState, faError, v -> render(content)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(4));
        addTopQuickActions(content);
        if (RoleStore.allowed(a, "sales")) {
            content.addView(salesHero(null, failureState), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
        }
        if (homeSectionVisible("market")) {
            content.addView(marketCard(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
        }
        content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
        localizeHomeNumbers(content);
    }

    // ---------------- offline cache (manager cues, top items, sales history and timestamp) ----------------
    private void saveCache(Data d) {
        try {
            Calendar c = Calendar.getInstance();
            String hm = String.format(java.util.Locale.US, "%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            StringBuilder s = new StringBuilder("ts=")
                    .append(esc(a.kit.todayLine() + " ساعت " + hm))
                    .append("|sd=").append(esc(d.snapshotDay))
                    .append("|sales=").append(rowStr(d.salesDay))
                    .append("|buy=").append(rowStr(d.buyDay))
                    .append("|in=").append(rowStr(d.inDay))
                    .append("|out=").append(rowStr(d.outDay))
                    .append("|buytrendok=").append(d.buyTrendAvailable ? "1" : "0")
                    .append("|intrendok=").append(d.inTrendAvailable ? "1" : "0")
                    .append("|outtrendok=").append(d.outTrendAvailable ? "1" : "0")
                    .append("|inmixok=").append(d.inMixAvailable ? "1" : "0")
                    .append("|returnsok=").append(d.returnsAvailable ? "1" : "0")
                    .append("|returnsprevok=").append(d.returnsPreviousAvailable ? "1" : "0")
                    .append("|trendok=").append(d.salesSnapshot != null && d.salesSnapshot.isAvailable() ? "1" : "0")
                    .append("|trendfrom=").append(esc(d.salesHistoryFrom == null ? "" : d.salesHistoryFrom))
                    .append("|stockok=").append(d.stockAlertsAvailable ? "1" : "0")
                    .append("|reorderok=").append(d.reorderPointAvailable ? "1" : "0")
                    .append("|priceok=").append(d.purchasePriceTrendAvailable ? "1" : "0")
                    .append("|dueinok=").append(d.dueInAvailable ? "1" : "0")
                    .append("|dueoutok=").append(d.dueOutAvailable ? "1" : "0")
                    .append("|topok=").append(d.topItemsAvailable ? "1" : "0")
                    .append("|topmonthok=").append(d.topItemsMonthAvailable ? "1" : "0")
                    .append("|topcusttodayok=").append(d.topCustomersTodayAvailable ? "1" : "0")
                    .append("|topcustmonthok=").append(d.topCustomersMonthAvailable ? "1" : "0")
                    .append("|overdueok=").append(d.overdueAvailable ? "1" : "0")
                    .append("|retodayok=").append(d.returnsTodayAvailable ? "1" : "0")
                    .append("|cashinok=").append(d.cashReceiptsAvailable ? "1" : "0")
                    .append("|cashoutok=").append(d.cashPaymentsAvailable ? "1" : "0")
                    .append("|bankaccountsok=").append(d.bankAccountsAvailable ? "1" : "0")
                    .append("|bankturnok=").append(d.bankTurnoverAvailable ? "1" : "0")
                    .append("|overpaidvok=").append(d.overpaidVouchersAvailable ? "1" : "0")
                    .append("|unbalancedjok=").append(d.unbalancedJournalsAvailable ? "1" : "0")
                    .append("|gpok=").append(d.grossProfitAvailable ? "1" : "0");
            if (d.buyDaily != null) for (int i = 0; i < Math.min(7, d.buyDaily.size()); i++)
                s.append("|buytrend").append(i).append('=').append(rowStr(d.buyDaily.get(i)));
            if (d.inDaily != null) for (int i = 0; i < Math.min(7, d.inDaily.size()); i++)
                s.append("|intrend").append(i).append('=').append(rowStr(d.inDaily.get(i)));
            if (d.outDaily != null) for (int i = 0; i < Math.min(7, d.outDaily.size()); i++)
                s.append("|outtrend").append(i).append('=').append(rowStr(d.outDaily.get(i)));
            if (d.inMix != null) for (int i = 0; i < Math.min(4, d.inMix.size()); i++)
                s.append("|inmix").append(i).append('=').append(rowStr(d.inMix.get(i)));
            if (d.returns != null) for (int i = 0; i < Math.min(20, d.returns.size()); i++)
                s.append("|ret").append(i).append('=').append(rowStr(d.returns.get(i)));
            if (d.returnsPrevious != null) for (int i = 0; i < Math.min(20, d.returnsPrevious.size()); i++)
                s.append("|retprev").append(i).append('=').append(rowStr(d.returnsPrevious.get(i)));
            if (d.topItems != null) {
                for (int i = 0; i < Math.min(5, d.topItems.size()); i++)
                    s.append("|top").append(i).append('=').append(rowStr(d.topItems.get(i)));
            }
            if (d.topItemsMonth != null) for (int i = 0; i < Math.min(5, d.topItemsMonth.size()); i++)
                s.append("|topmonth").append(i).append('=').append(rowStr(d.topItemsMonth.get(i)));
            if (d.topCustomersToday != null) for (int i = 0; i < Math.min(5, d.topCustomersToday.size()); i++)
                s.append("|topcusttoday").append(i).append('=').append(rowStr(d.topCustomersToday.get(i)));
            if (d.topCustomersMonth != null) for (int i = 0; i < Math.min(5, d.topCustomersMonth.size()); i++)
                s.append("|topcustmonth").append(i).append('=').append(rowStr(d.topCustomersMonth.get(i)));
            if (d.overdue != null) {
                for (int i = 0; i < Math.min(5, d.overdue.size()); i++)
                    s.append("|overdue").append(i).append('=').append(rowStr(cacheFields(d.overdue.get(i),
                            "code", "party", "amount", "dueDate", "days", "visitor", "invoice")));
            }
            if (d.cashReceipts != null) s.append("|cashin=").append(rowStr(d.cashReceipts));
            if (d.cashPayments != null) s.append("|cashout=").append(rowStr(d.cashPayments));
            if (d.bankAccounts != null) for (int i = 0; i < Math.min(50, d.bankAccounts.size()); i++)
                s.append("|bankacct").append(i).append('=').append(rowStr(cacheFields(d.bankAccounts.get(i), "id", "name")));
            if (d.bankTurnover != null) for (int i = 0; i < Math.min(50, d.bankTurnover.size()); i++)
                s.append("|bankturn").append(i).append('=').append(rowStr(cacheFields(d.bankTurnover.get(i), "bank", "in", "out", "count", "bankId")));
            if (d.overpaidVouchers != null) for (int i = 0; i < Math.min(5, d.overpaidVouchers.size()); i++)
                s.append("|overpaidv").append(i).append('=').append(rowStr(cacheFields(d.overpaidVouchers.get(i), "no", "kind", "party", "date", "total", "paid", "excess")));
            if (d.unbalancedJournals != null) for (int i = 0; i < Math.min(5, d.unbalancedJournals.size()); i++)
                s.append("|unbalancedj").append(i).append('=').append(rowStr(cacheFields(d.unbalancedJournals.get(i), "sanad", "date", "rows", "debit", "credit", "diff")));
            if (d.returnsToday != null) {
                for (int i = 0; i < Math.min(20, d.returnsToday.size()); i++)
                    s.append("|retoday").append(i).append('=').append(rowStr(d.returnsToday.get(i)));
            }
            if (d.grossProfitDaily != null) {
                for (int i = 0; i < Math.min(36, d.grossProfitDaily.size()); i++)
                    s.append("|gp").append(i).append('=').append(rowStr(d.grossProfitDaily.get(i)));
            }
            if (d.stockAlerts != null) {
                for (int i = 0; i < Math.min(20, d.stockAlerts.size()); i++)
                    s.append("|stock").append(i).append('=').append(rowStr(cacheFields(d.stockAlerts.get(i),
                            "shka", "name", "stockPieces", "reorderPoint", "reorderKnown", "isOut")));
            }
            if (d.purchasePriceTrend != null) {
                for (int i = 0; i < Math.min(20, d.purchasePriceTrend.size()); i++)
                    s.append("|price").append(i).append('=').append(rowStr(cacheFields(d.purchasePriceTrend.get(i),
                            "label", "lastUnit", "prevUnit", "change")));
            }
            if (d.dueIn != null) {
                for (int i = 0; i < Math.min(20, d.dueIn.size()); i++)
                    s.append("|duein").append(i).append('=').append(rowStr(cacheFields(d.dueIn.get(i),
                            "amount", "sardate")));
            }
            if (d.dueOut != null) {
                for (int i = 0; i < Math.min(20, d.dueOut.size()); i++)
                    s.append("|dueout").append(i).append('=').append(rowStr(cacheFields(d.dueOut.get(i),
                            "amount", "sardate")));
            }
            if (d.salesDaily != null) {
                for (int i = 0; i < Math.min(62, d.salesDaily.size()); i++)
                    s.append("|trend").append(i).append('=').append(rowStr(d.salesDaily.get(i)));
            }
            if (d.monthlySales != null) {
                for (int i = 0; i < Math.min(12, d.monthlySales.size()); i++)
                    s.append("|month").append(i).append('=').append(rowStr(d.monthlySales.get(i)));
            }
            a.settings.saveHomeCache(s.toString());
            pokeWidget();
        } catch (Exception ignored) { Diag.ignored("HomeScreen:185", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("HomeScreen:202", ignored); }
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
                else if ("sd".equals(k)) d.snapshotDay = unesc(v);
                else if ("sales".equals(k)) { d.salesDay = v.isEmpty() ? null : rowFrom(v); any = true; }
                else if ("buy".equals(k)) { d.buyDay = v.isEmpty() ? null : rowFrom(v); any = true; }
                else if ("in".equals(k)) { d.inDay = v.isEmpty() ? null : rowFrom(v); any = true; }
                else if ("out".equals(k)) { d.outDay = v.isEmpty() ? null : rowFrom(v); any = true; }
                else if ("buytrendok".equals(k)) d.buyTrendAvailable = "1".equals(v);
                else if ("intrendok".equals(k)) d.inTrendAvailable = "1".equals(v);
                else if ("outtrendok".equals(k)) d.outTrendAvailable = "1".equals(v);
                else if ("inmixok".equals(k)) d.inMixAvailable = "1".equals(v);
                else if ("returnsok".equals(k)) d.returnsAvailable = "1".equals(v);
                else if ("returnsprevok".equals(k)) d.returnsPreviousAvailable = "1".equals(v);
                else if (k.matches("buytrend[0-6]")) { d.buyDaily.add(rowFrom(v)); any = true; }
                else if (k.matches("intrend[0-6]")) { d.inDaily.add(rowFrom(v)); any = true; }
                else if (k.matches("outtrend[0-6]")) { d.outDaily.add(rowFrom(v)); any = true; }
                else if (k.matches("inmix[0-3]")) { d.inMix.add(rowFrom(v)); any = true; }
                else if (k.matches("ret[0-9]|ret1[0-9]")) { d.returns.add(rowFrom(v)); any = true; }
                else if (k.matches("retprev[0-9]|retprev1[0-9]")) { d.returnsPrevious.add(rowFrom(v)); any = true; }
                else if ("trendok".equals(k)) d.salesTrendAvailable = "1".equals(v);
                else if ("trendfrom".equals(k)) d.salesHistoryFrom = Jalali.disp(unesc(v));
                else if ("stockok".equals(k)) d.stockAlertsAvailable = "1".equals(v);
                else if ("reorderok".equals(k)) d.reorderPointAvailable = "1".equals(v);
                else if ("priceok".equals(k)) d.purchasePriceTrendAvailable = "1".equals(v);
                else if ("dueinok".equals(k)) d.dueInAvailable = "1".equals(v);
                else if ("dueoutok".equals(k)) d.dueOutAvailable = "1".equals(v);
                else if ("topok".equals(k)) d.topItemsAvailable = "1".equals(v);
                else if ("topmonthok".equals(k)) d.topItemsMonthAvailable = "1".equals(v);
                else if ("topcusttodayok".equals(k)) d.topCustomersTodayAvailable = "1".equals(v);
                else if ("topcustmonthok".equals(k)) d.topCustomersMonthAvailable = "1".equals(v);
                else if ("overdueok".equals(k)) d.overdueAvailable = "1".equals(v);
                else if ("retodayok".equals(k)) d.returnsTodayAvailable = "1".equals(v);
                else if ("cashinok".equals(k)) d.cashReceiptsAvailable = "1".equals(v);
                else if ("cashoutok".equals(k)) d.cashPaymentsAvailable = "1".equals(v);
                else if ("bankaccountsok".equals(k)) d.bankAccountsAvailable = "1".equals(v);
                else if ("bankturnok".equals(k)) d.bankTurnoverAvailable = "1".equals(v);
                else if ("overpaidvok".equals(k)) d.overpaidVouchersAvailable = "1".equals(v);
                else if ("unbalancedjok".equals(k)) d.unbalancedJournalsAvailable = "1".equals(v);
                else if ("gpok".equals(k)) d.grossProfitAvailable = "1".equals(v);
                else if ("cashin".equals(k)) { d.cashReceipts = rowFrom(v); any = true; }
                else if ("cashout".equals(k)) { d.cashPayments = rowFrom(v); any = true; }
                else if (k.matches("bankacct(?:[0-9]|[1-4][0-9])")) { d.bankAccounts.add(rowFrom(v)); any = true; }
                else if (k.matches("bankturn(?:[0-9]|[1-4][0-9])")) { d.bankTurnover.add(rowFrom(v)); any = true; }
                else if (k.matches("overpaidv[0-4]")) { d.overpaidVouchers.add(rowFrom(v)); any = true; }
                else if (k.matches("unbalancedj[0-4]")) { d.unbalancedJournals.add(rowFrom(v)); any = true; }
                else if (k.matches("topmonth[0-4]")) { d.topItemsMonth.add(rowFrom(v)); any = true; }
                else if (k.matches("topcusttoday[0-4]")) { d.topCustomersToday.add(rowFrom(v)); any = true; }
                else if (k.matches("topcustmonth[0-4]")) { d.topCustomersMonth.add(rowFrom(v)); any = true; }
                else if (k.matches("top[0-4]")) { d.topItems.add(rowFrom(v)); any = true; }
                else if (k.matches("overdue[0-4]")) { d.overdue.add(rowFrom(v)); any = true; }
                else if (k.matches("retoday(?:[0-9]|1[0-9])")) { d.returnsToday.add(rowFrom(v)); any = true; }
                else if (k.matches("gp(?:[0-9]|[12][0-9]|3[0-5])")) { d.grossProfitDaily.add(rowFrom(v)); any = true; }
                else if (k.matches("stock(?:[0-9]|1[0-9])")) { d.stockAlerts.add(rowFrom(v)); any = true; }
                else if (k.matches("price(?:[0-9]|1[0-9])")) { d.purchasePriceTrend.add(rowFrom(v)); any = true; }
                else if (k.matches("duein(?:[0-9]|1[0-9])")) { d.dueIn.add(rowFrom(v)); any = true; }
                else if (k.matches("dueout(?:[0-9]|1[0-9])")) { d.dueOut.add(rowFrom(v)); any = true; }
                else if (k.matches("trend(?:[0-9]|[1-5][0-9]|6[01])")) {
                    d.salesDaily.add(rowFrom(v)); d.salesTrendAvailable = true; any = true;
                }
                else if (k.matches("month(?:[0-9]|1[01])")) { d.monthlySales.add(rowFrom(v)); any = true; }
            }
            rebuildSalesSnapshot(d);
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

    private static Row cacheFields(Row source, String... keys) {
        Row row = new Row();
        if (source == null || keys == null) return row;
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) row.put(key, source.get(key));
        }
        return row;
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

    private String snapshotDay(Data d) {
        String day = d == null ? "" : Jalali.disp(d.snapshotDay);
        return day.isEmpty() ? Jalali.todayStr() : day;
    }

    private String snapshotLabel(Data d) {
        String day = d == null ? "" : Jalali.disp(d.snapshotDay);
        if (day.isEmpty()) return "آخرین داده";
        return day.equals(Jalali.todayStr()) ? "امروز"
                : HomeNumberFormatter.localizeText("روز " + Jalali.shortLabel(day));
    }

    private String monthLabel(String monthStart) {
        String day = Jalali.disp(monthStart);
        if (day.isEmpty()) return "ماه نامشخص";
        return Jalali.monthName(day) + " " + HomeNumberFormatter.localizeText(day.substring(0, 4));
    }

    /** «شنبه، ۲۵ مهر ۱۴۰۵» in Persian digits; falls back to the short line when the date is unusual. */
    private String dateLine() {
        String t = Jalali.todayStr();
        String[] p = t.split("/");
        if (p.length != 3) return HomeNumberFormatter.localizeText(a.kit.todayLine());
        try {
            int day = Integer.parseInt(p[2].trim());
            return Jalali.weekday(t) + "، " + HomeNumberFormatter.integer(day) + " " + Jalali.monthName(t)
                    + " " + HomeNumberFormatter.localizeText(p[0].trim());
        } catch (NumberFormatException e) {
            return HomeNumberFormatter.localizeText(a.kit.todayLine());
        }
    }

    /** Small greeting: keep the user's name and today's Persian date visible without repeating the brand mark. */
    private View greetingHero(Data data) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 5 ? "شب بخیر" : (hour < 12 ? "صبح بخیر"
                : (hour < 15 ? "روز بخیر" : (hour < 19 ? "عصر بخیر" : "شب بخیر")));
        String name = HomeNumberFormatter.cleanDisplayName(AtiranAuth.sessionName(a));
        if (name.isEmpty()) name = HomeNumberFormatter.cleanDisplayName(AtiranAuth.sessionUser(a));
        if (name.isEmpty()) name = HomeNumberFormatter.cleanDisplayName(RoleStore.faName(currentRole()));
        if (name.isEmpty()) name = "مدیر";
        name = HomeNumberFormatter.localizeText(name);

        LinearLayout intro = a.kit.v();
        intro.setTag("home-greeting");
        intro.setBackground(Theme.dashboardWelcome());
        intro.setElevation(Theme.dp(4f));
        intro.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        intro.setClipToPadding(false);
        intro.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));

        LinearLayout row = a.kit.h();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(dashboardArt(R.drawable.mi_diamond, Theme.GOLD_METAL),
                a.kit.lp(Theme.dp(42), Theme.dp(42)));
        row.addView(a.kit.space(10));
        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text(greeting + "، " + name, 17f,
                Theme.dashboardWelcomeText(), true);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        copy.addView(title, a.kit.lp(-1, -2));
        String summary = greetingSummary(data);
        TextView detail = a.kit.text(dateLine() + "  ·  " + summary, 10.5f,
                Theme.dashboardWelcomeMuted(), false);
        detail.setGravity(Gravity.END);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(detail, a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        intro.addView(row, a.kit.lp(-1, -2));
        intro.setContentDescription(greeting + "، " + name + "؛ " + dateLine() + "؛ " + summary);
        return intro;
    }

    /** One sentence of context, not another presentation of the sales total. */
    private String greetingSummary(Data data) {
        if (data == null) return "نمای کلی فروشگاه آماده می‌شود";
        if (data.overdue != null && !data.overdue.isEmpty() && RoleStore.allowed(a, "sales"))
            return "یک فاکتور معوق در مرکز توجه است";
        if (data.notes != null && !data.notes.isEmpty())
            return "برخی داده‌ها موقتاً در دسترس نیستند";
        if (data.salesSnapshot != null && data.salesSnapshot.isAvailable()
                && Jalali.todayStr().equals(snapshotDay(data))
                && data.salesSnapshot.documentsOn(snapshotDay(data)) == 0)
            return "هنوز فاکتوری برای امروز ثبت نشده است";
        return "نمای امروز در کارت‌های زیر جمع شده است";
    }

    private static final String[] SALES_RANGES = {"امروز", "دیروز", "۷ روز", "ماه"};
    private static final int CHART_RANGE_WEEK = 7;
    private static final int CHART_RANGE_FORTNIGHT = 14;
    private static final int CHART_RANGE_MONTH = -1;
    private static final int CHART_RANGE_HISTORY = -2;
    private static final String[] PERFORMER_RANGES = {"امروز", "این ماه"};
    private int selectedSalesRange;
    private int selectedChartRange = CHART_RANGE_FORTNIGHT;
    private int selectedPerformerRange;
    private int selectedSeasonalMonth;
    private boolean quickActionsExpanded;

    private static final class SalesDisplay {
        String date;
        String rangeLabel;
        String comparisonLabel;
        String forecastLabel;
        double amountRials;
        long documents;
        double changePct = Double.NaN;
        double forecastRials;
        boolean showForecast;
    }

    /** All figures come from the same read-only daily snapshot used by the existing Home queries. */
    private SalesDisplay salesDisplay(Data data, int rangeIndex) {
        if (data == null || data.salesSnapshot == null || !data.salesSnapshot.isAvailable()) return null;
        SalesSnapshot snapshot = data.salesSnapshot;
        String end = snapshotDay(data);
        SalesDisplay display = new SalesDisplay();
        display.date = end;
        switch (rangeIndex) {
            case 1: {
                String date = Jalali.addDays(end, -1);
                if (!hasHistory(snapshot, date)) return null;
                SalesComparison.DayResult comparison = SalesComparison.evaluate(snapshot, date,
                        SalesComparison.MINUTES_PER_DAY, SalesComparison.DEFAULT_COMPARABLE_WEEKS);
                display.date = date;
                display.rangeLabel = "دیروز";
                display.comparisonLabel = "همین روز هفته‌های گذشته";
                display.amountRials = comparison.actualRials;
                display.documents = snapshot.documentsOn(date);
                display.changePct = comparison.changePercent();
                break;
            }
            case 2: {
                HomeRange current = HomeRange.last7Days(end);
                if (!hasHistory(snapshot, current.from)) return null;
                String previousFrom = Jalali.addDays(current.from, -7);
                String previousTo = Jalali.addDays(current.to, -7);
                display.rangeLabel = "۷ روز گذشته";
                display.comparisonLabel = "۷ روز پیش از این بازه";
                display.amountRials = snapshot.salesRialsBetween(current.from, current.to);
                display.documents = snapshot.documentsBetween(current.from, current.to);
                if (hasHistory(snapshot, previousFrom)) {
                    double previous = snapshot.salesRialsBetween(previousFrom, previousTo);
                    if (previous > 0) display.changePct = (display.amountRials - previous) * 100d / previous;
                }
                break;
            }
            case 3: {
                HomeRange current = HomeRange.monthToDate(end);
                if (!hasHistory(snapshot, current.from)) return null;
                HomeRange previous = HomeRange.previousMonthSamePeriod(end);
                display.rangeLabel = "ماه تا امروز";
                display.comparisonLabel = "ماه قبل تا همین روز";
                display.amountRials = snapshot.salesRialsBetween(current.from, current.to);
                display.documents = snapshot.documentsBetween(current.from, current.to);
                if (hasHistory(snapshot, previous.from)) {
                    double previousTotal = snapshot.salesRialsBetween(previous.from, previous.to);
                    if (previousTotal > 0) display.changePct = (display.amountRials - previousTotal) * 100d / previousTotal;
                }
                if (end.equals(Jalali.todayStr())) {
                    display.forecastRials = HomeRange.projectMonthEnd(display.amountRials, end);
                    display.forecastLabel = "پیش‌بینی پایان ماه";
                    display.showForecast = true;
                }
                break;
            }
            case 0:
            default: {
                int elapsed = end.equals(Jalali.todayStr()) ? minuteOfDay() : SalesComparison.MINUTES_PER_DAY;
                SalesComparison.DayResult comparison = SalesComparison.evaluate(snapshot, end,
                        elapsed, SalesComparison.DEFAULT_COMPARABLE_WEEKS);
                display.rangeLabel = end.equals(Jalali.todayStr()) ? "امروز" : "آخرین روز";
                display.comparisonLabel = "همین روز در هفته‌های قبل";
                display.amountRials = comparison.actualRials;
                display.documents = snapshot.documentsOn(end);
                display.changePct = comparison.changePercent();
                if (comparison.projectionAvailable && !comparison.complete) {
                    display.forecastRials = comparison.projectedRials;
                    display.forecastLabel = "پیش‌بینی پایان روز · بر پایهٔ روز تقویمی ۰۰ تا ۲۴";
                    display.showForecast = true;
                }
                break;
            }
        }
        return display;
    }

    private static boolean hasHistory(SalesSnapshot snapshot, String from) {
        int lower = Jalali.parse(from);
        int availableFrom = Jalali.parse(snapshot == null ? "" : snapshot.historyStart());
        return lower >= 0 && availableFrom >= 0 && lower >= availableFrom;
    }

    private static int minuteOfDay() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private View salesHero(final Data data, final SyncState syncState) {
        final LinearLayout card = homeCard(Theme.SECTION_SALES);
        card.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(12));
        View.OnClickListener salesAction = navAction("sales");
        View salesDetails = salesAction == null ? null : dashboardHeaderAction("جزئیات",
                Theme.SECTION_SALES, "جزئیات فروش؛ باز کردن", salesAction);
        card.addView(boardHead("فروش", null, R.drawable.mi_point_of_sale, salesDetails,
                Theme.SECTION_SALES), a.kit.lp(-1, -2));

        final LinearLayout amountPanel = a.kit.v();
        amountPanel.setBackground(Theme.dashboardMetric(Theme.SECTION_SALES));
        amountPanel.setElevation(Theme.dp(1f));
        amountPanel.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        amountPanel.setPadding(Theme.dp(12), Theme.dp(9), Theme.dp(12), Theme.dp(9));
        final android.animation.ValueAnimator[] amountAnimator = {null};
        final HomeSalesChart.Area chart = new HomeSalesChart.Area(a);
        final TextView[] chartHint = {null};
        View selector = salesRangeSelector(card, amountPanel, data, amountAnimator);
        LinearLayout.LayoutParams selectorParams = a.kit.lp(-1, -2);
        selectorParams.topMargin = Theme.dp(8);
        card.addView(selector, selectorParams);

        LinearLayout.LayoutParams amountParams = a.kit.lp(-1, -2);
        amountParams.topMargin = Theme.dp(8);
        card.addView(amountPanel, amountParams);
        renderSalesSummary(amountPanel, data, selectedSalesRange, false, amountAnimator);
        if (syncState == SyncState.REFRESHING && data != null && data.salesSnapshot != null
                && data.salesSnapshot.isAvailable()) {
            TextView age = a.kit.text("نمایش آخرین دادهٔ ذخیره‌شده تا پایان همگام‌سازی",
                    9.5f, Theme.dashboardCardMutedInk(Theme.SECTION_SALES), false);
            age.setGravity(Gravity.END);
            card.addView(age, a.kit.lp(-1, -2));
        }

        if (data != null && data.salesSnapshot != null && data.salesSnapshot.isAvailable()) {
            chart.setTag("home-sales-chart");
            chart.setSelectionListener(day -> {
                if (chartHint[0] != null) chartHint[0].setText(salesPointLabel(day));
            });
            chartHint[0] = a.kit.text("روند فروش روزانه · لمس نمودار برای جزئیات",
                    9.5f, Theme.dashboardCardMutedInk(Theme.SECTION_SALES), false);
            chartHint[0].setGravity(Gravity.END);
            refreshSalesChart(chart, chartHint, data, selectedChartRange);
            TextView rangeTitle = a.kit.text("بازهٔ نمودار فروش", 9.5f,
                    Theme.dashboardCardMutedInk(Theme.SECTION_SALES), true);
            rangeTitle.setGravity(Gravity.END);
            LinearLayout.LayoutParams rangeTitleParams = a.kit.lp(-1, -2);
            rangeTitleParams.topMargin = Theme.dp(8);
            card.addView(rangeTitle, rangeTitleParams);
            View chartRanges = salesChartRangeSelector(card, chart, data, chartHint);
            card.addView(chartRanges, a.kit.lp(-1, -2));
            LinearLayout.LayoutParams chartParams = a.kit.lp(-1, Theme.dp(188));
            chartParams.topMargin = Theme.dp(8);
            card.addView(chart, chartParams);
            card.addView(chartHint[0], a.kit.lp(-1, -2));
        } else {
            String problem = data != null && data.notes != null && data.notes.containsKey("روند فروش")
                    ? "گزارش فروش در دسترس نیست" : "هنوز داده‌ای برای روند فروش ثبت نشده است";
            LinearLayout empty = a.kit.v();
            empty.addView(a.kit.emptyRow(problem), a.kit.lp(-1, -2));
            if (syncState == SyncState.REFRESHING)
                empty.addView(a.kit.loading("در حال دریافت روند فروش"), a.kit.lp(-1, -2));
            card.addView(empty, a.kit.lp(-1, -2));
        }

        if (syncState == SyncState.STALE || syncState == SyncState.OFFLINE) {
            TextView age = a.kit.text(syncState == SyncState.OFFLINE
                    ? "نمایش آخرین دادهٔ ذخیره‌شده" : "فروش بر اساس آخرین دریافت موفق",
                    9.5f, Theme.dashboardCardMutedInk(Theme.SECTION_SALES), false);
            age.setGravity(Gravity.END);
            card.addView(age, a.kit.lp(-1, -2));
        }
        return card;
    }

    private View salesRangeSelector(final LinearLayout card, final LinearLayout amountPanel,
                                    final Data data,
                                    final android.animation.ValueAnimator[] amountAnimator) {
        final View[] selector = new View[1];
        selector[0] = a.kit.segmentedControl(SALES_RANGES, selectedSalesRange, null,
                Theme.SECTION_SALES, index -> {
                    selectedSalesRange = index;
                    renderSalesSummary(amountPanel, data, index, true, amountAnimator);
                    int position = card.indexOfChild(selector[0]);
                    if (position >= 0) {
                        View old = selector[0];
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) old.getLayoutParams();
                        card.removeViewAt(position);
                        selector[0] = salesRangeSelector(card, amountPanel, data, amountAnimator);
                        card.addView(selector[0], position, params);
                    }
                });
        selector[0].setTag("sales-range-selector");
        return selector[0];
    }

    /** Time-window options are exposed only when the successful snapshot covers them. */
    private List<Integer> salesChartRanges(Data data) {
        List<Integer> ranges = new ArrayList<>();
        if (data == null || data.salesSnapshot == null || !data.salesSnapshot.isAvailable()) return ranges;
        String end = snapshotDay(data);
        int endDay = Jalali.parse(end);
        int historyStart = Jalali.parse(data.salesSnapshot.historyStart());
        if (endDay < 0 || historyStart < 0 || endDay < historyStart) return ranges;
        int historyDays = endDay - historyStart + 1;
        if (historyDays >= CHART_RANGE_WEEK) ranges.add(CHART_RANGE_WEEK);
        if (historyDays >= CHART_RANGE_FORTNIGHT) ranges.add(CHART_RANGE_FORTNIGHT);
        int monthStart = Jalali.parse(Jalali.monthStart(end));
        boolean hasFullMonthToDate = monthStart >= 0 && monthStart >= historyStart && monthStart <= endDay;
        if (hasFullMonthToDate) ranges.add(CHART_RANGE_MONTH);
        if (historyDays > CHART_RANGE_FORTNIGHT && !hasFullMonthToDate)
            ranges.add(CHART_RANGE_HISTORY);
        if (ranges.isEmpty() && historyDays > 0) ranges.add(CHART_RANGE_HISTORY);
        return ranges;
    }

    private int preferredSalesChartRange(List<Integer> ranges) {
        if (ranges.contains(CHART_RANGE_FORTNIGHT)) return CHART_RANGE_FORTNIGHT;
        if (ranges.contains(CHART_RANGE_WEEK)) return CHART_RANGE_WEEK;
        return ranges.isEmpty() ? CHART_RANGE_HISTORY : ranges.get(ranges.size() - 1);
    }

    private String salesChartRangeLabel(Data data, int range) {
        if (range == CHART_RANGE_WEEK) return "۷ روز";
        if (range == CHART_RANGE_FORTNIGHT) return "۱۴ روز";
        if (range == CHART_RANGE_MONTH) return "ماه";
        return HomeNumberFormatter.integer(salesChartHistoryDays(data)) + " روز";
    }

    private String[] salesChartRangeLabels(Data data, List<Integer> ranges) {
        String[] labels = new String[ranges.size()];
        for (int i = 0; i < ranges.size(); i++) labels[i] = salesChartRangeLabel(data, ranges.get(i));
        return labels;
    }

    private int salesChartHistoryDays(Data data) {
        if (data == null || data.salesSnapshot == null || !data.salesSnapshot.isAvailable()) return 0;
        int end = Jalali.parse(snapshotDay(data));
        int start = Jalali.parse(data.salesSnapshot.historyStart());
        return end < 0 || start < 0 || end < start ? 0 : end - start + 1;
    }

    /** Every chart window is clipped to the successful read-only snapshot's actual history. */
    private List<SalesSnapshot.Day> salesChartSeries(Data data, int range) {
        if (data == null || data.salesSnapshot == null || !data.salesSnapshot.isAvailable())
            return java.util.Collections.emptyList();
        SalesSnapshot snapshot = data.salesSnapshot;
        String end = snapshotDay(data);
        int endDay = Jalali.parse(end);
        int historyStart = Jalali.parse(snapshot.historyStart());
        if (endDay < 0 || historyStart < 0 || endDay < historyStart)
            return java.util.Collections.emptyList();
        int requested;
        if (range == CHART_RANGE_WEEK) requested = CHART_RANGE_WEEK;
        else if (range == CHART_RANGE_FORTNIGHT) requested = CHART_RANGE_FORTNIGHT;
        else if (range == CHART_RANGE_MONTH) {
            int monthStart = Jalali.parse(Jalali.monthStart(end));
            if (monthStart < historyStart || monthStart > endDay) return java.util.Collections.emptyList();
            requested = endDay - monthStart + 1;
        } else requested = endDay - historyStart + 1;
        if (requested <= 0) return java.util.Collections.emptyList();
        int actual = Math.min(requested, endDay - historyStart + 1);
        return actual <= 0 ? java.util.Collections.emptyList() : snapshot.dailySeries(end, actual);
    }

    private String salesChartWindowLabel(Data data, int range, int availableDays) {
        String asOf = snapshotLabel(data);
        if (range == CHART_RANGE_MONTH) return "ماه جاری تا " + asOf;
        if (range == CHART_RANGE_HISTORY)
            return HomeNumberFormatter.integer(availableDays) + " روز از تاریخچهٔ موجود";
        return HomeNumberFormatter.integer(availableDays) + " روز تا " + asOf;
    }

    private View salesChartRangeSelector(final LinearLayout card, final HomeSalesChart.Area chart,
                                         final Data data, final TextView[] chartHint) {
        final List<Integer> ranges = salesChartRanges(data);
        if (ranges.isEmpty()) {
            View hidden = new View(a);
            hidden.setVisibility(View.GONE);
            return hidden;
        }
        if (!ranges.contains(selectedChartRange))
            selectedChartRange = preferredSalesChartRange(ranges);
        int selected = ranges.indexOf(selectedChartRange);
        final View[] selector = new View[1];
        selector[0] = a.kit.segmentedControl(salesChartRangeLabels(data, ranges), selected, null,
                Theme.SECTION_SALES, index -> {
                    selectedChartRange = ranges.get(index);
                    refreshSalesChart(chart, chartHint, data, selectedChartRange);
                    int position = card.indexOfChild(selector[0]);
                    if (position >= 0) {
                        View old = selector[0];
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) old.getLayoutParams();
                        card.removeViewAt(position);
                        selector[0] = salesChartRangeSelector(card, chart, data, chartHint);
                        card.addView(selector[0], position, params);
                    }
                });
        selector[0].setTag("sales-chart-range-selector");
        return selector[0];
    }

    private boolean salesSeriesHasActivity(List<SalesSnapshot.Day> series) {
        if (series == null) return false;
        for (SalesSnapshot.Day day : series)
            if (day != null && (Math.abs(day.salesRials) >= 0.005d || day.documents > 0)) return true;
        return false;
    }

    private String salesPointLabel(SalesSnapshot.Day day) {
        if (day == null) return "جزئیات نقطهٔ انتخاب‌شده در دسترس نیست";
        return Jalali.weekday(day.date) + "، " + Jalali.dispFa(day.date) + " · "
                + HomeNumberFormatter.compactMoney(day.salesRials) + " · "
                + HomeNumberFormatter.integer(day.documents) + " فاکتور";
    }

    private void refreshSalesChart(HomeSalesChart.Area chart, TextView[] chartHint,
                                   Data data, int range) {
        List<Integer> ranges = salesChartRanges(data);
        if (ranges.isEmpty()) {
            chart.setData(java.util.Collections.<SalesSnapshot.Day>emptyList(), 0,
                    "تاریخچهٔ موجود نیست");
            if (chartHint != null && chartHint.length > 0 && chartHint[0] != null)
                chartHint[0].setText("تاریخچهٔ فروش برای این بازه در دسترس نیست");
            return;
        }
        if (!ranges.contains(range)) {
            range = preferredSalesChartRange(ranges);
            selectedChartRange = range;
        }
        List<SalesSnapshot.Day> series = salesChartSeries(data, range);
        double average = 0;
        for (SalesSnapshot.Day day : series) average += day.salesRials;
        if (!series.isEmpty()) average /= series.size();
        String window = salesChartWindowLabel(data, range, series.size());
        chart.setData(series, average, window);
        chart.setSelectedDate(snapshotDay(data));
        if (chartHint != null && chartHint.length > 0 && chartHint[0] != null) {
            String hint = series.isEmpty() ? "تاریخچهٔ فروش برای این بازه در دسترس نیست"
                    : !salesSeriesHasActivity(series) ? "در این بازه فاکتوری ثبت نشده است"
                    : window + " · لمس نمودار برای جزئیات";
            chartHint[0].setText(hint);
        }
    }

    private void renderSalesSummary(LinearLayout panel, Data data, int range,
                                    boolean animate, android.animation.ValueAnimator[] animator) {
        if (animator[0] != null) {
            animator[0].cancel();
            animator[0] = null;
        }
        panel.removeAllViews();
        SalesDisplay display = salesDisplay(data, range);
        if (display == null) {
            String message = data != null && data.notes != null && data.notes.containsKey("روند فروش")
                    ? "فروش در این دریافت در دسترس نیست"
                    : data != null && data.salesSnapshot != null && data.salesSnapshot.isAvailable()
                    ? "تاریخچهٔ کافی برای این بازه در دسترس نیست"
                    : "دادهٔ فروش موجود نیست";
            panel.addView(a.kit.emptyRow(message), a.kit.lp(-1, -2));
            return;
        }

        LinearLayout valueRow = a.kit.h();
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView amount = a.kit.text(HomeNumberFormatter.compactUnit(0), 29f, Theme.TEXT, true);
        amount.setSingleLine(true);
        amount.setEllipsize(TextUtils.TruncateAt.END);
        amount.setFontFeatureSettings("tnum");
        amount.setContentDescription(HomeNumberFormatter.money(display.amountRials));
        valueRow.addView(amount, a.kit.wlp(1f));
        TextView unit = a.kit.text(ir.meelano.manager.core.Money.unitName(), 10.5f,
                Theme.dashboardMutedInk(Theme.SECTION_SALES), false);
        unit.setSingleLine(true);
        unit.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueRow.addView(a.kit.space(5));
        valueRow.addView(unit, a.kit.lp(-2, -2));
        panel.addView(valueRow, a.kit.lp(-1, -2));

        if (animate && ir.meelano.manager.ui.Ui.motionOk(a)) {
            final double target = ir.meelano.manager.core.Money.toUnit(display.amountRials);
            animator[0] = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator[0].setDuration(600);
            animator[0].setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator[0].addUpdateListener(animation -> amount.setText(HomeNumberFormatter.compactUnit(
                    target * (float) animation.getAnimatedValue())));
            animator[0].start();
        } else {
            amount.setText(HomeNumberFormatter.compactUnit(
                    ir.meelano.manager.core.Money.toUnit(display.amountRials)));
        }

        LinearLayout detail = a.kit.h();
        detail.setGravity(Gravity.CENTER_VERTICAL);
        TextView documents = a.kit.text(HomeNumberFormatter.integer(display.documents) + " فاکتور",
                10.5f, Theme.dashboardMutedInk(Theme.SECTION_SALES), false);
        documents.setContentDescription(HomeNumberFormatter.integer(display.documents) + " فاکتور در "
                + display.rangeLabel);
        detail.addView(documents, a.kit.wlp(1f));
        if (!Double.isNaN(display.changePct) && !Double.isInfinite(display.changePct)) {
            int direction = Math.abs(display.changePct) < 0.05d ? 0 : display.changePct > 0 ? 1 : -1;
            int tone = direction > 0 ? Theme.SUCCESS : direction < 0 ? Theme.DANGER : Theme.MUTED;
            TextView delta = a.kit.deltaBadge(HomeNumberFormatter.signedPercent(display.changePct),
                    direction, tone, "تغییر " + HomeNumberFormatter.signedPercent(display.changePct)
                            + "؛ " + display.comparisonLabel);
            detail.addView(delta, a.kit.lp(-2, -2));
        } else {
            TextView noBaseline = a.kit.text("مبنای مقایسه کافی نیست", 9.5f,
                    Theme.dashboardMutedInk(Theme.SECTION_SALES), false);
            noBaseline.setGravity(Gravity.END);
            detail.addView(noBaseline, a.kit.lp(-2, -2));
        }
        panel.addView(detail, a.kit.lp(-1, -2));
        TextView comparison = a.kit.text(display.comparisonLabel, 9.5f,
                Theme.dashboardMutedInk(Theme.SECTION_SALES), false);
        comparison.setGravity(Gravity.END);
        panel.addView(comparison, a.kit.lp(-1, -2));

        if (display.showForecast) {
            LinearLayout forecast = a.kit.h();
            forecast.setGravity(Gravity.CENTER_VERTICAL);
            forecast.setPadding(Theme.dp(9), Theme.dp(5), Theme.dp(9), Theme.dp(5));
            forecast.setBackground(Theme.pill(Theme.SECTION_SALES));
            TextView projected = a.kit.text(display.forecastLabel + "  ·  "
                    + HomeNumberFormatter.compactMoney(display.forecastRials), 9.5f,
                    Theme.sectionText(Theme.SECTION_SALES), false);
            projected.setMaxLines(2);
            projected.setEllipsize(TextUtils.TruncateAt.END);
            forecast.addView(projected, a.kit.wlp(1f));
            LinearLayout.LayoutParams fp = a.kit.lp(-1, -2);
            fp.topMargin = Theme.dp(6);
            panel.addView(forecast, fp);
        }
    }

    /** Compact rates + optional device-location weather. Cached values show while refresh runs. */
    private String weatherPlace = "";
    /** True only when the user opted in and the weather card uses a device fix. */
    private boolean weatherFromDevice = false;
    /** Ticker details and optional weather stay tucked away until the manager opens them. */
    private boolean marketExpanded;

    private LinearLayout marketView;
    private int marketViewGeneration = Integer.MIN_VALUE;
    private boolean marketRefreshInFlight;

    private synchronized boolean beginMarketRefresh() {
        if (marketRefreshInFlight) return false;
        marketRefreshInFlight = true;
        return true;
    }

    private synchronized void endMarketRefresh() { marketRefreshInFlight = false; }

    private View marketCard() {
        final int gen = a.renderGen();
        // A cache paint and its later database paint belong to one render; keep one ticker request/view.
        if (marketView != null && marketViewGeneration == gen) return marketView;
        final LinearLayout card = homeCard(Theme.SECTION_MARKET);
        marketView = card;
        marketViewGeneration = gen;
        // Device weather requires both an explicit in-app opt-in and Android permission.
        boolean locationEnabled = MarketWeather.weatherLocationEnabled(a);
        if (locationEnabled && !ir.meelano.manager.core.DeviceLocation.granted(a)) {
            MarketWeather.setWeatherLocationEnabled(a, false);
            locationEnabled = false;
        }
        if (!locationEnabled && (MarketWeather.cached(a, MarketWeather.weatherKey()) != null
                || MarketWeather.weatherWasFromDevice(a) || MarketWeather.weatherPlace(a) != null)) {
            // Erase location-derived cache left by an earlier version or a revoked permission.
            MarketWeather.setWeatherLocationEnabled(a, false);
        }
        String savedPlace = locationEnabled ? MarketWeather.weatherPlace(a) : null;
        boolean savedFromDevice = locationEnabled && MarketWeather.weatherWasFromDevice(a);
        String savedWeather = savedFromDevice && savedPlace != null && !savedPlace.isEmpty()
                ? MarketWeather.cached(a, MarketWeather.weatherKey()) : null;
        weatherFromDevice = savedWeather != null;
        weatherPlace = savedWeather == null ? "" : savedPlace;
        renderMarket(card, MarketWeather.cached(a, MarketWeather.ratesKey()), savedWeather, false, false);
        if (!beginMarketRefresh()) return card;
        new Thread(() -> {
            try {
                // Location and weather requests happen only after the user opts in.
                android.location.Location loc = null;
                if (MarketWeather.weatherLocationEnabled(a)) {
                    try { loc = ir.meelano.manager.core.DeviceLocation.resolve(a, 6000); }
                    catch (Exception e) { Diag.ignored("Home:location", e); }
                }
                final boolean fromDevice = loc != null;
                final double lat = fromDevice ? loc.getLatitude() : 0d;
                final double lon = fromDevice ? loc.getLongitude() : 0d;
                String place = fromDevice ? ir.meelano.manager.core.DeviceLocation.placeName(a, lat, lon) : null;
                if (place == null && fromDevice) place = "موقعیت شما";

                String rRaw = null;
                String wRaw = null;
                try {
                    String candidate = MarketWeather.get(MarketWeather.RATES_URL);
                    MarketWeather.parseRates(candidate); // never replace a known-good cache with malformed/stale JSON
                    rRaw = candidate;
                } catch (Exception e) { Diag.ignored("Home:ratesFetch", e); }
                // A fresh permissioned fix is never inferred or transmitted without the app-level opt-in.
                if (fromDevice && MarketWeather.weatherLocationEnabled(a)) try {
                    String candidate = MarketWeather.get(MarketWeather.weatherUrl(lat, lon));
                    MarketWeather.parseWeather(candidate);
                    wRaw = candidate;
                } catch (Exception e) { Diag.ignored("Home:weatherFetch", e); }
                final boolean rLive = rRaw != null;
                if (rLive) MarketWeather.save(a, MarketWeather.ratesKey(), rRaw);
                if (MarketWeather.weatherLocationEnabled(a)
                        && !ir.meelano.manager.core.DeviceLocation.granted(a)) {
                    MarketWeather.setWeatherLocationEnabled(a, false);
                }
                boolean wLive = wRaw != null && MarketWeather.weatherLocationEnabled(a);
                if (wLive) wLive = MarketWeather.saveWeather(a, wRaw, place);
                final boolean weatherLive = wLive;
                final String fr = rLive ? rRaw : MarketWeather.cached(a, MarketWeather.ratesKey());
                final String fw = weatherLive ? wRaw : MarketWeather.cached(a, MarketWeather.weatherKey());
                final String fp = weatherLive ? place : MarketWeather.weatherPlace(a);
                final boolean fd = weatherLive || MarketWeather.weatherWasFromDevice(a);
                a.runOnUiThread(() -> {
                    if (!"home".equals(a.currentScreenId())) return;
                    LinearLayout target = marketView;
                    if (target == null || target.getParent() == null) return;
                    weatherPlace = fp;
                    weatherFromDevice = fd;
                    try {
                        renderMarket(target, fr, fw, rLive, weatherLive);
                    } catch (RuntimeException renderError) {
                        Diag.warn("HomeScreen:marketRender", renderError);
                        ir.meelano.manager.core.CrashReporter.recordHandled(
                                a, "HomeScreen:marketRender", renderError);
                        try {
                            target.removeAllViews();
                            target.addView(a.kit.hint("نمایش اطلاعات بازار موقتاً ممکن نشد؛ سایر بخش‌های برنامه در دسترس‌اند."),
                                    a.kit.lp(-1, -2));
                        } catch (RuntimeException fallbackError) {
                            Diag.warn("HomeScreen:marketRenderFallback", fallbackError);
                        }
                    }
                });
            } catch (Exception refreshError) {
                // This independent ticker/weather worker must never take down the app.
                Diag.warn("HomeScreen:marketRefresh", refreshError);
            } finally {
                endMarketRefresh();
            }
        }, "market-refresh").start();
        return card;
    }


    private void renderMarket(LinearLayout card, String rJson, String wJson, boolean rLive, boolean wLive) {
        card.removeAllViews();
        List<MarketWeather.Rate> rates = null;
        MarketWeather.Weather w = null;
        try {
            if (rJson != null) rates = MarketWeather.parseRates(rJson, MarketWeather.previousRates(a));
        } catch (Exception e) { Diag.warn("Home:parseRates", e); }
        try { if (wJson != null) w = MarketWeather.parseWeather(wJson); }
        catch (Exception e) { Diag.warn("Home:parseWeather", e); }

        boolean hasRates = rates != null && !rates.isEmpty();
        String state = !hasRates ? "ناموجود" : rLive ? "آنلاین" : "ذخیره‌شده";
        int stateTone = !hasRates ? Theme.WARNING : rLive ? Theme.SUCCESS : Theme.MUTED;
        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.addView(a.kit.iconBadge(R.drawable.mi_storefront, Theme.SECTION_MARKET, 30),
                a.kit.lp(Theme.dp(30), Theme.dp(30)));
        head.addView(a.kit.space(7));
        LinearLayout title = a.kit.v();
        title.addView(a.kit.text("بازار", 13f, Theme.sectionText(Theme.SECTION_MARKET), true),
                a.kit.lp(-1, -2));
        title.addView(a.kit.text("دلار · طلای ۱۸ · سکه", 9f, Theme.MUTED, false), a.kit.lp(-1, -2));
        head.addView(title, a.kit.wlp(1f));
        View status = a.kit.pill(state, stateTone);
        status.setContentDescription("وضعیت قیمت بازار: " + state);
        head.addView(status, a.kit.lp(-2, -2));
        Button toggle = a.kit.btnGhost(marketExpanded ? "جمع‌کردن" : "نمایش",
                Theme.SECTION_MARKET, v -> {
                    marketExpanded = !marketExpanded;
                    renderMarket(card, rJson, wJson, rLive, wLive);
                });
        toggle.setTextSize(10.5f * Theme.fontScale());
        toggle.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        toggle.setContentDescription(marketExpanded
                ? "جمع‌کردن جزئیات بازار و آب‌وهوا" : "نمایش جزئیات بازار و آب‌وهوا");
        toggle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams toggleParams = a.kit.lp(-2, Theme.dp(48));
        toggleParams.setMarginEnd(Theme.dp(4));
        head.addView(toggle, toggleParams);
        Button refresh = a.kit.btnGhost("⟳", Theme.SECTION_MARKET, v -> a.nav("home"));
        refresh.setContentDescription("تازه‌سازی قیمت‌های بازار؛ نماد تازه‌سازی");
        refresh.setMinWidth(Theme.dp(48));
        refresh.setMinHeight(Theme.dp(48));
        head.addView(refresh, a.kit.lp(Theme.dp(48), Theme.dp(48)));
        card.addView(head, a.kit.lp(-1, -2));

        if (!hasRates) {
            card.addView(a.kit.emptyRow("قیمت‌های بازار موقتاً در دسترس نیستند"), a.kit.lp(-1, -2));
            card.addView(a.kit.text("اتصال را بررسی کنید؛ آخرین قیمت معتبر هنگام دسترس‌بودن نمایش داده می‌شود.",
                    9f, Theme.MUTED, false), a.kit.lp(-1, -2));
        } else {
            HorizontalScrollView ticker = new HorizontalScrollView(a);
            ticker.setHorizontalScrollBarEnabled(false);
            ticker.setFillViewport(false);
            ticker.setClipToPadding(false);
            ticker.setHorizontalFadingEdgeEnabled(true);
            ticker.setFadingEdgeLength(Theme.dp(12));
            ticker.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            ticker.setContentDescription("قیمت دلار آزاد، طلای ۱۸ عیار و سکه امامی");
            LinearLayout row = a.kit.h();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            int screenDp = Math.round(a.getResources().getDisplayMetrics().widthPixels
                    / a.getResources().getDisplayMetrics().density);
            int tileWidth = Math.max(94, Math.min(122, (screenDp - 64) / 3));
            for (int i = 0; i < rates.size(); i++) {
                if (i > 0) row.addView(a.kit.space(6));
                row.addView(rateTile(rates.get(i)), new LinearLayout.LayoutParams(Theme.dp(tileWidth), -2));
            }
            ticker.addView(row, new android.view.ViewGroup.LayoutParams(-2, -2));
            LinearLayout.LayoutParams tickerParams = a.kit.lp(-1, -2);
            tickerParams.topMargin = Theme.dp(6);
            card.addView(ticker, tickerParams);
        }

        String stamp = hasRates ? rates.get(0).asOf : "";
        String sourceTime = stamp == null || stamp.trim().isEmpty()
                ? "زمان منبع نامشخص" : "زمان منبع " + HomeNumberFormatter.localizeText(stamp);
        String basis = hasRates && rates.get(0).comparesWithPreviousFetch
                ? "تغییر نسبت به دریافت قبلی" : "درصد تغییر اعلامی TGJU";
        TextView freshness = a.kit.text("منبع TGJU · واحد تومان · " + sourceTime + " · "
                + marketFreshness(MarketWeather.ratesKey(), rLive), 9f, Theme.MUTED, false);
        freshness.setMaxLines(2);
        freshness.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams freshnessParams = a.kit.lp(-1, -2);
        freshnessParams.topMargin = Theme.dp(5);
        card.addView(freshness, freshnessParams);

        if (marketExpanded) {
            card.addView(a.kit.divider(), a.kit.lp(-1, Theme.dp(1)));
            card.addView(a.kit.gap(6));
            if (w != null) {
                LinearLayout wr = a.kit.h();
                wr.setGravity(Gravity.CENTER_VERTICAL);
                int wRes = w.code <= 1 ? R.drawable.mi_light_mode
                        : w.code <= 3 ? R.drawable.mi_wb_twilight
                        : (w.code >= 95 ? R.drawable.mi_bolt
                        : (w.code >= 51 ? R.drawable.mi_beach_access : R.drawable.mi_wb_twilight));
                wr.addView(a.kit.iconBadge(wRes, Theme.SECTION_MARKET, 32),
                        a.kit.lp(Theme.dp(32), Theme.dp(32)));
                wr.addView(a.kit.space(8));
                LinearLayout wc = a.kit.v();
                String place = weatherPlace == null || weatherPlace.isEmpty() ? "موقعیت شما" : weatherPlace;
                TextView line = a.kit.text(place + " · " + HomeNumberFormatter.integer(Math.round(w.temp)) + "° · "
                        + MarketWeather.describe(w.code), 11.5f, Theme.TEXT, true);
                line.setMaxLines(1);
                line.setEllipsize(TextUtils.TruncateAt.END);
                wc.addView(line, a.kit.lp(-1, -2));
                LinearLayout weatherMeta = a.kit.h();
                weatherMeta.setGravity(Gravity.CENTER_VERTICAL);
                weatherMeta.addView(a.kit.text("رطوبت " + HomeNumberFormatter.percent(w.humidity),
                        9.5f, Theme.MUTED, false), a.kit.wlp(1f));
                weatherMeta.addView(a.kit.pill(wLive ? "زنده" : "ذخیره‌شده",
                        wLive ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
                wc.addView(weatherMeta, a.kit.lp(-1, -2));
                wr.addView(wc, a.kit.wlp(1f));
                card.addView(wr, a.kit.lp(-1, -2));
            } else {
                boolean locationEnabled = MarketWeather.weatherLocationEnabled(a);
                boolean locationGranted = ir.meelano.manager.core.DeviceLocation.granted(a);
                String weatherHint = !locationEnabled
                        ? "آب‌وهوا خاموش است؛ موقعیت دستگاه بدون انتخاب شما ارسال نمی‌شود."
                        : !locationGranted
                        ? "مجوز موقعیت در دسترس نیست؛ بدون اجازهٔ شما موقعیتی ارسال نمی‌شود."
                        : "موقعیت تازه‌ای به‌دست نیامد؛ برای تلاش دوباره تازه‌سازی کنید.";
                card.addView(a.kit.hint(weatherHint), a.kit.lp(-1, -2));
            }

            boolean locationEnabled = MarketWeather.weatherLocationEnabled(a);
            boolean locationGranted = ir.meelano.manager.core.DeviceLocation.granted(a);
            boolean removeLocation = locationEnabled && locationGranted;
            Button loc = a.kit.btnGhost(removeLocation ? "خاموش‌کردن آب‌وهوا" : "فعال‌سازی آب‌وهوا",
                    Theme.SECTION_MARKET, v -> {
                        if (removeLocation) {
                            MarketWeather.setWeatherLocationEnabled(a, false);
                            weatherPlace = "";
                            weatherFromDevice = false;
                            a.nav("home");
                        } else if (locationGranted) {
                            MarketWeather.setWeatherLocationEnabled(a, true);
                            a.nav("home");
                        } else {
                            a.requestLocationAccess();
                        }
                    });
            loc.setContentDescription(removeLocation
                    ? "خاموش‌کردن آب‌وهوا با موقعیت و حذف دادهٔ ذخیره‌شده"
                    : "فعال‌کردن آب‌وهوا با موقعیت دستگاه؛ ارسال موقعیت تنها با اجازهٔ شما انجام می‌شود");
            loc.setSingleLine(true);
            loc.setTextSize(10.5f * Theme.fontScale());
            loc.setPadding(Theme.dp(10), Theme.dp(4), Theme.dp(10), Theme.dp(4));
            loc.setMinHeight(Theme.dp(48));
            LinearLayout.LayoutParams locationParams = a.kit.lp(-2, -2);
            locationParams.setMargins(0, Theme.dp(5), 0, 0);
            locationParams.gravity = Gravity.END;
            card.addView(loc, locationParams);

            TextView detail = a.kit.text("مبنای تغییر: " + basis + " · قیمت‌های بازار فقط خواندنی‌اند.",
                    9f, Theme.MUTED, false);
            detail.setMaxLines(2);
            LinearLayout.LayoutParams detailParams = a.kit.lp(-1, -2);
            detailParams.topMargin = Theme.dp(4);
            card.addView(detail, detailParams);
        }
        localizeHomeNumbers(card);
    }

    /** A truthful local cache timestamp, separately reported from the source's own timestamp. */
    private String marketFreshness(String key, boolean live) {
        long savedAt = MarketWeather.savedAt(a, key);
        if (savedAt <= 0) return live ? "تازه دریافت شد" : "زمان دریافت ثبت نشده";
        Calendar saved = Calendar.getInstance();
        saved.setTimeInMillis(savedAt);
        int[] jalali = Jalali.fromDay(Jalali.gregorianDay(saved.get(Calendar.YEAR), saved.get(Calendar.MONTH) + 1,
                saved.get(Calendar.DAY_OF_MONTH)));
        String date = String.format(Locale.US, "%04d/%02d/%02d", jalali[0], jalali[1], jalali[2]);
        String today = Jalali.todayStr();
        String day = date.equals(today) ? "امروز" : date.equals(Jalali.addDays(today, -1))
                ? "دیروز" : Jalali.shortLabel(date);
        String time = HomeNumberFormatter.time(saved.get(Calendar.HOUR_OF_DAY), saved.get(Calendar.MINUTE));
        return (live ? "هم‌اکنون · " : "ذخیره‌شده · ") + day + " " + time;
    }

    private View rateTile(MarketWeather.Rate r) {
        String label = r.label == null ? "" : r.label;
        String shortLabel = label.contains("دلار") ? "دلار آزاد"
                : label.contains("طلا") ? "طلای ۱۸" : "سکه امامی";
        int tileIcon = label.contains("دلار") ? R.drawable.mi_trending_up
                : label.contains("طلا") ? R.drawable.mi_diamond : R.drawable.mi_account_balance_wallet;
        boolean hasPrice = r.toman > 0;
        LinearLayout tile = a.kit.v();
        tile.setGravity(Gravity.CENTER);
        tile.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tile.setPadding(Theme.dp(7), Theme.dp(6), Theme.dp(7), Theme.dp(7));
        tile.setBackground(Theme.dashboardMetric(Theme.SECTION_MARKET));
        tile.setElevation(Theme.dp(1f));
        tile.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

        LinearLayout heading = a.kit.h();
        heading.setGravity(Gravity.CENTER);
        heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        heading.addView(dashboardArt(tileIcon, Theme.SECTION_MARKET),
                a.kit.lp(Theme.dp(28), Theme.dp(28)));
        heading.addView(a.kit.space(4));
        TextView name = a.kit.text(shortLabel, 9.5f, Theme.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(name, a.kit.lp(-2, -2));
        tile.addView(heading, a.kit.lp(-1, -2));

        String priceText = hasPrice ? HomeNumberFormatter.rounded(r.toman) + " تومان" : "ناموجود";
        LinearLayout.LayoutParams priceParams = a.kit.lp(-1, -2);
        priceParams.topMargin = Theme.dp(3);
        View price = a.kit.amountLine(priceText, 12.5f, Gravity.CENTER);
        tile.addView(price, priceParams);
        if (hasPrice) {
            int direction = Math.abs(r.changePct) < 0.05 ? 0 : r.changePct > 0 ? 1 : -1;
            int tone = direction > 0 ? Theme.SUCCESS : direction < 0 ? Theme.DANGER : Theme.MUTED;
            String badgeText = direction == 0 ? "بدون تغییر" : HomeNumberFormatter.percentMagnitude(r.changePct);
            String comparisonBasis = r.comparesWithPreviousFetch
                    ? "نسبت به دریافت قبلی" : "بر اساس تغییر اعلام‌شده توسط TGJU";
            String spokenDelta = direction == 0 ? "بدون تغییر " + comparisonBasis
                    : (direction > 0 ? "افزایش " : "کاهش ")
                    + HomeNumberFormatter.decimal(Math.abs(r.changePct)) + " درصد " + comparisonBasis;
            View badge = a.kit.deltaBadge(badgeText, direction, tone, spokenDelta);
            LinearLayout.LayoutParams badgeParams = a.kit.lp(-2, -2);
            badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
            badgeParams.topMargin = Theme.dp(4);
            tile.addView(badge, badgeParams);
        } else {
            TextView unavailable = a.kit.text("قیمت ناموجود", 9f,
                    Theme.dashboardMutedInk(Theme.SECTION_MARKET), false);
            unavailable.setGravity(Gravity.CENTER);
            tile.addView(unavailable, a.kit.lp(-1, -2));
        }

        String comparisonBasis = r.comparesWithPreviousFetch
                ? "نسبت به دریافت قبلی" : "بر اساس تغییر اعلام‌شده توسط TGJU";
        String trendDescription = !hasPrice ? "قیمت در دسترس نیست"
                : Math.abs(r.changePct) < 0.05 ? "بدون تغییر " + comparisonBasis
                : (r.changePct > 0 ? "افزایش " : "کاهش ")
                + HomeNumberFormatter.decimal(Math.abs(r.changePct)) + " درصد " + comparisonBasis;
        String previousPrice = r.comparesWithPreviousFetch
                ? "؛ نرخ قبلی " + HomeNumberFormatter.rounded(r.previousToman) + " تومان" : "";
        String sourceTime = r.asOf == null || r.asOf.trim().isEmpty()
                ? "" : "؛ زمان منبع " + HomeNumberFormatter.localizeText(r.asOf);
        tile.setContentDescription(shortLabel + "، " + (hasPrice
                ? HomeNumberFormatter.rounded(r.toman) + " تومان، " + trendDescription + previousPrice
                : trendDescription) + sourceTime);
        return tile;
    }

    private String currentTimeLabel() {
        Calendar now = Calendar.getInstance();
        return HomeNumberFormatter.localizeText(a.kit.todayLine()) + " ساعت "
                + HomeNumberFormatter.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    private void build(LinearLayout content, Data d, SyncState syncState, String syncTime) {
        content.removeAllViews();
        View.OnClickListener retry = syncState == SyncState.STALE || syncState == SyncState.OFFLINE
                || syncState == SyncState.ERROR ? v -> render(content) : null;
        View status = syncStatus(syncTime, syncState, null, retry);
        // The compact status changes state in place after the first cache/live transition.
        if (syncWasRefreshing && entered) {
            status.setAlpha(0f);
            status.animate().alpha(1f).setDuration(220).start();
        }
        syncWasRefreshing = false;
        content.addView(greetingHero(d), a.kit.lp(-1, -2));
        content.addView(status, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(4));
        addTopQuickActions(content);
        if (RoleStore.allowed(a, "sales")) {
            content.addView(salesHero(d, syncState), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(10));
        }
        LinearLayout kpiSection = a.kit.v();
        secKpis(kpiSection, d);
        if (kpiSection.getChildCount() > 0) content.addView(kpiSection, a.kit.lp(-1, -2));

        // Sales and KPIs stay pinned above; lower modules follow the saved visibility/order.
        for (String key : orderedKeys()) {
            if ("sales_insight".equals(key) || "kpis".equals(key) || !homeSectionVisible(key)) continue;
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key, syncTime);
            if (block.getChildCount() == 0) continue;
            content.addView(block, a.kit.lp(-1, -2));
        }
        renderNotes(content, d.notes);
        View safeTail = a.kit.gap(20);
        safeTail.setTag("home-bottom-safe-space");
        content.addView(safeTail, a.kit.lp(-1, Theme.dp(20)));
        localizeHomeNumbers(content);
        staggerIn(content);
    }

    /** Convert any remaining server/cache digits in Home labels and accessibility text. */
    private void localizeHomeNumbers(View root) {
        if (root == null) return;
        CharSequence description = root.getContentDescription();
        CharSequence localizedDescription = localizeHomeText(description);
        if (localizedDescription != description) root.setContentDescription(localizedDescription);
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            CharSequence original = text.getText();
            CharSequence localized = localizeHomeText(original);
            if (localized != original) text.setText(localized, TextView.BufferType.SPANNABLE);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) localizeHomeNumbers(group.getChildAt(i));
        }
    }

    private static CharSequence localizeHomeText(CharSequence value) {
        if (value == null) return null;
        String source = value.toString();
        String localized = HomeNumberFormatter.localizeText(source);
        if (source.equals(localized)) return value;
        SpannableString result = new SpannableString(localized);
        if (value instanceof Spanned) {
            TextUtils.copySpansFrom((Spanned) value, 0, source.length(), Object.class, result, 0);
        }
        return result;
    }

    /** True while cached/empty data is being refreshed so the sync row can fade to its final state. */
    private boolean syncWasRefreshing;

    /** One-time staggered fade and slide for the first home cards (skipped when animations are off). */
    private boolean entered;

    private void staggerIn(LinearLayout content) {
        if (entered) return;
        entered = true;
        try {
            if (android.provider.Settings.Global.getFloat(a.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return;
        } catch (Exception ignored) { Diag.ignored("HomeScreen:stagger", ignored); }
        float dy = Theme.dp(14);
        int n = Math.min(content.getChildCount(), 8);
        for (int i = 0; i < n; i++) {
            View v = content.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 45L).setDuration(320).start();
        }
    }

    private void renderHomeSection(LinearLayout content, Data d, String key, String syncTime) {
        if ("smart_report".equals(key)) secSmartReport(content, d, syncTime);
        else if ("closeout".equals(key)) secHomeCloseout(content, d);
        else if ("cash_position".equals(key)) secCashPosition(content, d);
        else if ("top_performers".equals(key)) secTopPerformers(content, d);
        else if ("sales_goal".equals(key)) secMonthlySalesGoal(content, d);
        else if ("gross_profit".equals(key)) secGrossProfit(content, d);
        else if ("seasonal_calendar".equals(key)) secSeasonalCalendar(content);
        else if ("quick_actions".equals(key)) secQuickActions(content);
        else if ("forecast_7d".equals(key)) secSalesForecast(content, d);
        else if ("kpis".equals(key)) secKpis(content, d);
        else if ("alerts".equals(key)) secAlerts(content, d);
        else if ("sales_insight".equals(key)) secSalesInsight(content, d);
        else if ("cash_mix".equals(key)) secCashMix(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
        else if ("market".equals(key)) content.addView(marketCard(), a.kit.lp(-1, -2));
    }

    private String currentRole() {
        String role = AtiranAuth.sessionRole(a);
        if (role != null && !role.isEmpty()) return role;
        return RoleStore.enabled(a) ? RoleStore.current(a) : RoleStore.ADMIN;
    }

    private boolean isSalesManager() {
        return RoleStore.SALES_MANAGER.equals(currentRole());
    }

    /** Dashboard order from settings; unknown keys dropped, missing sections appended. */
    private List<String> orderedKeys() {
        if (isSalesManager()) {
            List<String> salesOrder = new ArrayList<>();
            salesOrder.add("alerts");
            return salesOrder;
        }
        List<String> out = new ArrayList<>();
        String[] known = ir.meelano.manager.data.Settings.HOME_ORDER_DEFAULT.split(",");
        for (String k : a.settings.homeOrder().split(",")) {
            String t = k == null ? "" : k.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                for (String k2 : known) if (k2.equals(t)) { out.add(t); break; }
            }
        }
        for (String k2 : known) if (!out.contains(k2)) out.add(k2);
        return out;
    }

    private boolean homeSectionSelected(String key) {
        if (key == null) return false;
        for (String selected : a.settings.homeSections().split(","))
            if (key.equals(selected.trim())) return true;
        return false;
    }

    /** In simple mode, the first three selected lower modules are visible; remaining features stay tucked away. */
    private boolean homeSectionVisible(String key) {
        if (!homeSectionSelected(key)) return false;
        if (!a.settings.homeSimpleMode()) return true;
        int shown = 0;
        for (String ordered : orderedKeys()) {
            if (!homeSectionSelected(ordered)) continue;
            if (ordered.equals(key)) return shown < 3;
            shown++;
        }
        return false;
    }

    private void secKpis(LinearLayout content, Data d) {
        final String today = snapshotDay(d);
        List<Kit.Kpi> tiles = new ArrayList<>();

        if (RoleStore.allowed(a, "buy")) {
            boolean available = d.buyDay != null;
            double amount = available ? d.buyDay.d("total") : 0;
            Double previous = d.buyTrendAvailable
                    ? dailyAmount(d.buyDaily, Jalali.addDays(today, -1)) : null;
            tiles.add(homeKpi("خرید", amount, available ? d.buyDay.l("docs") : 0,
                    "فاکتور " + snapshotLabel(d), available, previous,
                    kpiSpark(d.buyDaily, today, d.buyTrendAvailable),
                    Theme.SECTION_PURCHASE, R.drawable.mi_shopping_cart, true, false,
                    () -> a.nav("buy")));
        }
        if (RoleStore.allowed(a, "dar_in")) {
            boolean available = d.inDay != null;
            double amount = available ? d.inDay.d("total") : 0;
            Double previous = d.inTrendAvailable
                    ? dailyAmount(d.inDaily, Jalali.addDays(today, -1)) : null;
            tiles.add(homeKpi("دریافت", amount, available ? d.inDay.l("count") : 0,
                    "قبض " + snapshotLabel(d), available, previous,
                    kpiSpark(d.inDaily, today, d.inTrendAvailable),
                    Theme.SECTION_RECEIVE, R.drawable.mi_receipt_long, true, false,
                    () -> a.nav("dar_in")));
        }
        if (RoleStore.allowed(a, "dar_out")) {
            boolean available = d.outDay != null;
            double amount = available ? d.outDay.d("total") : 0;
            Double previous = d.outTrendAvailable
                    ? dailyAmount(d.outDaily, Jalali.addDays(today, -1)) : null;
            tiles.add(homeKpi("پرداخت", amount, available ? d.outDay.l("count") : 0,
                    "قبض " + snapshotLabel(d), available, previous,
                    kpiSpark(d.outDaily, today, d.outTrendAvailable),
                    Theme.SECTION_PAY, R.drawable.mi_payments, false, false,
                    () -> a.nav("dar_out")));
        }
        if (RoleStore.allowed(a, "sales") || RoleStore.allowed(a, "reports")) {
            double amount = sumField(d.returns, "total");
            long documents = sumLongField(d.returns, "docs");
            boolean available = d.returnsAvailable;
            Double previous = d.returnsPreviousAvailable
                    ? sumField(d.returnsPrevious, "total") : null;
            double[] spark = available && d.returnsPreviousAvailable
                    ? new double[]{sumField(d.returnsPrevious, "total"), amount} : null;
            View.OnClickListener open = navAction(RoleStore.allowed(a, "reports") ? "reports" : "sales");
            tiles.add(homeKpi("برگشتی", amount, documents, "سند در ۳۰ روز", available,
                    previous, spark, Theme.SECTION_RETURNS, R.drawable.mi_assignment_return,
                    false, true, open == null ? null : () -> open.onClick(null)));
        }

        if (tiles.isEmpty()) return;
        LinearLayout card = homeCard(Theme.SECTION_SALES);
        card.setTag("home-financial-kpis");
        card.addView(boardHead("شاخص‌های مالی", null, R.drawable.mi_account_balance_wallet,
                null, Theme.SECTION_SALES), a.kit.lp(-1, -2));
        TextView subtitle = a.kit.text("جریان مالی " + snapshotLabel(d)
                        + " · اسناد برگشتی در ۳۰ روز گذشته",
                10f, Theme.dashboardCardMutedInk(Theme.SECTION_SALES), false);
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(subtitle, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        // A complete RTL grid replaces the clipped horizontal carousel: every metric is visible
        // at a glance, while wide screens naturally open to four columns through Kit.kpiGrid.
        card.addView(a.kit.kpiGrid(tiles, 2), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    Kit.Kpi homeKpi(String label, double amountRials, long documents, String countLabel,
                            boolean available, Double previousRials, double[] spark, int accent,
                            int icon, boolean increaseIsGood, boolean returnsMetric, Runnable action) {
        String amount = available
                ? HomeNumberFormatter.compactUnit(ir.meelano.manager.core.Money.toUnit(amountRials)) : "—";
        String unit = available ? ir.meelano.manager.core.Money.unitName() : "";
        String detail = available ? HomeNumberFormatter.integer(documents) + " " + countLabel
                : "داده در دسترس نیست";
        String sub;
        String comparisonBasis = returnsMetric ? " · نسبت به ۳۰ روز قبل" : " · نسبت به روز قبل";
        int subTone = Theme.MUTED;
        boolean neutralReturn = returnsMetric && available && documents == 0;
        if (!available) {
            sub = "گزارش در دسترس نیست";
        } else if (neutralReturn) {
            sub = "✓ بدون برگشتی";
        } else if (previousRials != null) {
            double previous = previousRials;
            if (previous > 0) {
                double change = (amountRials - previous) * 100d / previous;
                int direction = Math.abs(change) < 0.05d ? 0 : change > 0 ? 1 : -1;
                if (direction == 0) sub = "۰٪" + comparisonBasis;
                else {
                    sub = HomeNumberFormatter.signedPercent(change) + comparisonBasis;
                    boolean favorable = (direction > 0) == increaseIsGood;
                    subTone = favorable ? Theme.SUCCESS : Theme.DANGER;
                }
            } else if (amountRials > 0) {
                sub = "▲ شروع از صفر" + comparisonBasis;
                subTone = increaseIsGood ? Theme.SUCCESS : Theme.DANGER;
            } else {
                sub = "بدون تغییر" + comparisonBasis;
            }
        } else {
            sub = "مبنای مقایسه کافی نیست";
        }
        Kit.Kpi tile = new Kit.Kpi(label,
                available ? ir.meelano.manager.core.Money.toUnit(amountRials) : Double.NaN,
                amount + (unit.isEmpty() ? "" : " " + unit), sub, accent, action);
        tile.dashboardStyle = true;
        tile.fullValue = available ? HomeNumberFormatter.money(amountRials) : "داده در دسترس نیست";
        tile.detail = detail;
        tile.neutralStatus = neutralReturn;
        tile.icon = icon;
        tile.spark = spark;
        tile.sparkDescription = returnsMetric ? "مقایسهٔ دو بازهٔ ۳۰روزهٔ اسناد برگشتی"
                : "روند هفت‌روزهٔ " + label;
        tile.subColor = subTone;
        return tile;
    }

    private double[] kpiSpark(List<Row> rows, String end, boolean available) {
        if (!available || rows == null) return null;
        double[] values = new double[7];
        for (int i = 0; i < values.length; i++)
            values[i] = dailyAmount(rows, Jalali.addDays(end, i - values.length + 1));
        return values;
    }

    private static double dailyAmount(List<Row> rows, String date) {
        if (rows == null || date == null || date.isEmpty()) return 0;
        double amount = 0;
        String target = Jalali.disp(date);
        for (Row row : rows) {
            if (row == null) continue;
            String rowDate = Jalali.disp(row.s("day"));
            if (rowDate.isEmpty()) rowDate = Jalali.disp(row.s("d"));
            if (target.equals(rowDate)) amount += row.d("total");
        }
        return amount;
    }

    private static double sumField(List<Row> rows, String field) {
        if (rows == null) return 0;
        double total = 0;
        for (Row row : rows) if (row != null) total += row.d(field);
        return total;
    }

    private static long sumLongField(List<Row> rows, String field) {
        if (rows == null) return 0;
        long total = 0;
        for (Row row : rows) if (row != null) total += row.l(field);
        return total;
    }

    /**
     * Home-only elevated card factory. The shared Kit card stays neutral on every other screen;
     * only the dashboard gets a coloured gradient, champagne edge, and shallow lift.
     */
    private LinearLayout homeCard(int accent) {
        LinearLayout card = a.kit.card(accent);
        card.setBackground(Theme.dashboardCard(accent));
        card.setElevation(Theme.dp(3.5f));
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        card.setClipToPadding(false);
        card.setClipChildren(false);
        return card;
    }

    /** Select a stable jewel hue by section; outcome colors stay reserved for deltas only. */
    private int dashboardAccent(int vector) {
        if (vector == R.drawable.mi_point_of_sale) return Theme.SECTION_CLOSEOUT;
        if (vector == R.drawable.mi_account_balance_wallet) return Theme.SECTION_CASH_POSITION;
        if (vector == R.drawable.mi_military_tech) return Theme.SECTION_TOP_PERFORMERS;
        if (vector == R.drawable.mi_track_changes) return Theme.SECTION_SALES_GOAL;
        if (vector == R.drawable.mi_profit) return Theme.SECTION_GROSS_PROFIT;
        if (vector == R.drawable.mi_calendar_month) return Theme.SECTION_SEASONAL_CALENDAR;
        if (vector == R.drawable.mi_bolt) return Theme.SECTION_MARKET;
        if (vector == R.drawable.mi_add) return Theme.SECTION_QUICK_ACTIONS;
        if (vector == R.drawable.mi_shopping_cart || vector == R.drawable.mi_add_shopping_cart)
            return Theme.SECTION_PURCHASE;
        if (vector == R.drawable.mi_receipt_long || vector == R.drawable.mi_cheque)
            return Theme.SECTION_RECEIVE;
        if (vector == R.drawable.mi_payments || vector == R.drawable.mi_trending_down)
            return Theme.SECTION_PAY;
        if (vector == R.drawable.mi_assignment_return) return Theme.SECTION_RETURNS;
        if (vector == R.drawable.mi_group || vector == R.drawable.mi_person)
            return Theme.SECTION_TOP_PERFORMERS;
        if (vector == R.drawable.mi_warning || vector == R.drawable.mi_error)
            return Theme.SECTION_ALERT;
        if (vector == R.drawable.mi_diamond || vector == R.drawable.mi_trending_up)
            return Theme.SECTION_MARKET;
        if (vector == R.drawable.mi_inventory_2 || vector == R.drawable.mi_grid_view
                || vector == R.drawable.mi_warehouse)
            return Theme.SECTION_WAREHOUSE;
        return Theme.SECTION_SALES;
    }

    /** Raised, theme-aware plate around an existing flat vector; hue follows dashboard section. */
    private ImageView dashboardArt(int iconVector) {
        return dashboardArt(iconVector, dashboardAccent(iconVector));
    }

    private ImageView dashboardArt(int iconVector, int accent) {
        ImageView image = a.kit.icon(iconVector, Theme.dashboardAccentInk(accent), 21);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setBackground(Theme.dashboardBadge(accent));
        int inset = Theme.dp(4);
        image.setPadding(inset, inset, inset, inset);
        image.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        image.setElevation(Theme.dp(1.25f));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    /** Small but fully accessible action used at the end of a dashboard section heading. */
    private Button dashboardHeaderAction(String label, int accent, String spoken,
                                         View.OnClickListener action) {
        Button button = a.kit.btnGhost(label, accent, action);
        button.setTextSize(10.5f * Theme.fontScale());
        button.setTextColor(Theme.dashboardActionInk(accent));
        button.setBackground(Theme.dashboardAction(accent));
        Theme.pressable(button);
        button.setElevation(Theme.dp(1.5f));
        button.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        button.setMinWidth(Theme.dp(48));
        button.setMinHeight(Theme.dp(48));
        button.setContentDescription(spoken);
        return button;
    }

    /** Dashboard-only section heading with a contrast-safe filled action. */
    private View dashboardSectionHead(String title, int iconVector, int accent,
                                      String actionLabel, View.OnClickListener action) {
        View end = null;
        if (actionLabel != null && action != null) {
            String label = actionLabel.replace("›", "").replace("‹", "").trim();
            end = dashboardHeaderAction(label, accent, label + "؛ باز کردن", action);
        }
        return boardHead(title, null, iconVector, end, accent);
    }

    /** Section heading: a duotone icon, title, optional subtitle and action. */
    private View boardHead(String title, String subtitle, int iconVector, View end) {
        return boardHead(title, subtitle, iconVector, end, dashboardAccent(iconVector));
    }

    private View boardHead(String title, String subtitle, int iconVector, View end, int accent) {
        LinearLayout box = a.kit.v();
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(3), Theme.dp(4), Theme.dp(3), Theme.dp(7));
        row.addView(dashboardArt(iconVector, accent),
                new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        row.addView(a.kit.space(8));
        TextView label = a.kit.text(title, 15f, Theme.dashboardAccentInk(accent), true);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, a.kit.wlp(1f));
        if (end != null) row.addView(end, a.kit.lp(-2, -2));
        box.addView(row, a.kit.lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = a.kit.text(subtitle, 11f,
                    Theme.dashboardCardMutedInk(accent), false);
            sub.setGravity(Gravity.START);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(sub, a.kit.lp(-1, -2));
        }
        return box;
    }

    /** The compact «‹» chip at the far end of a section head. */
    private View chevronChip(String label, View.OnClickListener onClick) {
        TextView chip = a.kit.text("‹", 20f, Theme.GOLD, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Theme.dp(12), Theme.dp(4), Theme.dp(12), Theme.dp(4));
        chip.setMinWidth(Theme.dp(48));
        chip.setMinHeight(Theme.dp(48));
        chip.setBackground(Theme.ghostButton(Theme.GOLD));
        chip.setContentDescription(label == null ? "باز کردن" : label + "؛ باز کردن");
        Theme.pressable(chip);
        if (onClick != null) chip.setOnClickListener(onClick);
        return chip;
    }

    /**
     * Role-aware chevron chip: null (the head simply shows no chip) when the current
     * role cannot open the target screen — each person only sees their own items.
     */
    private View navChip(String label, String screenId) {
        if (!RoleStore.allowed(a, screenId)) return null;
        return chevronChip(label, v -> a.nav(screenId));
    }

    /**
     * Role-aware action for a section head: null when the role cannot open the target.
     * {@link #dashboardSectionHead} hides the action whenever the listener is null.
     */
    private View.OnClickListener navAction(String screenId) {
        return RoleStore.allowed(a, screenId) ? v -> a.nav(screenId) : null;
    }

    /** A small status chip: how many alerts are open, or that there are none. */
    private View statusChip(String label, int color) {
        TextView chip = a.kit.text(label, 10.5f, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(Theme.dp(12), Theme.dp(6), Theme.dp(12), Theme.dp(6));
        chip.setBackground(Theme.pill(color));
        return chip;
    }

    /** A soft summary row: a duotone vector badge, a quiet label and a neutral value. */
    private LinearLayout statRow(int iconVector, String label, String value, int accent) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.dashboardMetric(accent));
        row.setElevation(Theme.dp(1f));
        row.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        row.setPadding(Theme.dp(12), Theme.dp(10), Theme.dp(12), Theme.dp(10));
        row.addView(dashboardArt(iconVector), a.kit.lp(Theme.dp(42), Theme.dp(42)));
        row.addView(a.kit.space(10));
        LinearLayout copy = a.kit.v();
        TextView l = a.kit.text(label, 11f, Theme.dashboardMutedInk(accent), false);
        l.setGravity(Gravity.START);
        copy.addView(l, a.kit.lp(-1, -2));
        copy.addView(a.kit.amountLine(value, 14f, Gravity.START), a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        return row;
    }

    /** One compact tile of the reference board: badge, label, value and an optional note. */
    private static final class Mini {
        final int icon; final String label; final String value; final String note; final int valueColor;
        Mini(int icon, String label, String value, String note, int valueColor) {
            this.icon = icon; this.label = label; this.value = value; this.note = note; this.valueColor = valueColor;
        }
    }

    /** A grid of compact tiles; every column shares the row evenly. */
    private View miniGrid(List<Mini> tiles, int cols, int accent) {
        LinearLayout root = a.kit.v();
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = a.kit.h();
                LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
                if (i > 0) rp.setMargins(0, Theme.dp(10), 0, 0);
                root.addView(row, rp);
            }
            Mini m = tiles.get(i);
            int tileAccent = dashboardAccent(m.icon);
            LinearLayout tile = a.kit.v();
            tile.setBackground(Theme.dashboardMetric(tileAccent));
            tile.setElevation(Theme.dp(1f));
            tile.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            tile.setPadding(Theme.dp(10), Theme.dp(12), Theme.dp(10), Theme.dp(12));
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView ic = dashboardArt(m.icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36));
            ip.gravity = Gravity.CENTER_HORIZONTAL;
            ip.bottomMargin = Theme.dp(6);
            tile.addView(ic, ip);
            TextView l = a.kit.text(m.label, 11f, Theme.dashboardMutedInk(tileAccent), false);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(l, a.kit.lp(-1, -2));
            tile.addView(a.kit.amountLine(m.value, 15f, Gravity.CENTER), a.kit.lp(-1, -2));
            if (m.note != null && !m.note.isEmpty()) {
                int noteColor = m.note.contains("ناقص") ? Theme.dashboardMutedInk(tileAccent)
                        : Theme.dashboardSemanticInk(tileAccent, Theme.SUCCESS);
                TextView n = a.kit.text(m.note, 10.5f, noteColor, true);
                n.setGravity(Gravity.CENTER_HORIZONTAL);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                tile.addView(n, a.kit.lp(-1, -2));
            }
            LinearLayout.LayoutParams tp = a.kit.wlp(1f);
            if (i % cols != 0) tp.setMargins(0, 0, Theme.dp(8), 0);
            row.addView(tile, tp);
        }
        return root;
    }

    /** Theme-aware, section-coloured summary grid for cash and closeout modules. */
    private View dashboardMetricGrid(List<Mini> metrics, int columns, int accent) {
        LinearLayout root = a.kit.v();
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (metrics == null || metrics.isEmpty()) return root;
        int span = Math.max(1, columns);
        for (int start = 0; start < metrics.size();) {
            int count = Math.min(span, metrics.size() - start);
            LinearLayout row = a.kit.h();
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowParams = a.kit.lp(-1, -2);
            if (start > 0) rowParams.topMargin = Theme.dp(8);
            root.addView(row, rowParams);

            for (int offset = 0; offset < count; offset++) {
                Mini metric = metrics.get(start + offset);
                LinearLayout tile = a.kit.v();
                int metricAccent = dashboardAccent(metric.icon);
                tile.setBackground(Theme.dashboardMetric(metricAccent));
                tile.setElevation(Theme.dp(1f));
                tile.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                tile.setPadding(Theme.dp(10), Theme.dp(9), Theme.dp(10), Theme.dp(9));
                tile.setMinimumHeight(Theme.dp(78));

                LinearLayout heading = a.kit.h();
                heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                heading.setGravity(Gravity.CENTER_VERTICAL);
                ImageView mark = dashboardArt(metric.icon, dashboardAccent(metric.icon));
                heading.addView(mark, new LinearLayout.LayoutParams(Theme.dp(28), Theme.dp(28)));
                heading.addView(a.kit.space(6));
                TextView label = a.kit.text(metric.label, 10.5f,
                        Theme.dashboardMutedInk(metricAccent), false);
                label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                heading.addView(label, a.kit.wlp(1f));
                tile.addView(heading, a.kit.lp(-1, -2));

                int valueColor = metric.valueColor == 0 ? Theme.TEXT : metric.valueColor;
                View amount = a.kit.amountLine(metric.value, 14f, Gravity.START, valueColor);
                if (amount instanceof TextView && android.os.Build.VERSION.SDK_INT >= 26) {
                    float scale = Theme.fontScale() * Theme.typeScale();
                    ((TextView) amount).setAutoSizeTextTypeUniformWithConfiguration(
                            Math.max(9, Math.round(9f * scale)),
                            Math.max(10, Math.round(14f * scale)), 1,
                            android.util.TypedValue.COMPLEX_UNIT_SP);
                }
                tile.addView(amount, a.kit.lp(-1, -2));
                if (metric.note != null && !metric.note.trim().isEmpty()) {
                    TextView note = a.kit.text(metric.note, 9.5f,
                            Theme.dashboardMutedInk(metricAccent), false);
                    note.setGravity(Gravity.START);
                    note.setMaxLines(2);
                    note.setEllipsize(TextUtils.TruncateAt.END);
                    tile.addView(note, a.kit.lp(-1, -2));
                }
                String spoken = metric.label + "، " + metric.value
                        + (metric.note == null || metric.note.trim().isEmpty() ? "" : "؛ " + metric.note);
                tile.setContentDescription(spoken);
                if (android.os.Build.VERSION.SDK_INT >= 26) tile.setTooltipText(spoken);

                row.addView(tile, a.kit.wlp(span / (float) count));
                if (offset < count - 1) row.addView(a.kit.space(8));
            }
            start += count;
        }
        return root;
    }

    private int currentMinuteOfDay() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** «تا ۱۱:۵۵ • ناقص»: the day is still running, so the number is partial. */
    private String partialTodayNote() {
        Calendar c = Calendar.getInstance();
        return "تا " + HomeNumberFormatter.time(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                + " • ناقص";
    }

    Kit.Kpi dayKpi(String title, String target, Row row, String countKey,
                   String countLabel, int accent) {
        String day = dayOf(row);
        String label = day.isEmpty() ? title : title + " " + day;
        if (row == null) {
            // The explicit-day aggregate failed or is unavailable; never present it as a zero.
            Kit.Kpi unavailable = new Kit.Kpi(label, "—", "داده در دسترس نیست", accent);
            unavailable.subColor = Theme.MUTED;
            return unavailable;
        }
        return new Kit.Kpi(label, HomeNumberFormatter.compactMoney(row.d("total")),
                HomeNumberFormatter.integer(row.l(countKey)) + " " + countLabel, accent,
                () -> gotoDay(target, row));
    }

    private static final class HomeAttention {
        final int priority;
        final int accent;
        final String title;
        final String detail;
        final Runnable action;

        HomeAttention(int priority, int accent, String title, String detail, Runnable action) {
            this.priority = priority;
            this.accent = accent;
            this.title = title;
            this.detail = detail;
            this.action = action;
        }
    }

    /** One copper-accented focus card, led by the highest-priority AlertEngine result. */
    private void secAlerts(LinearLayout content, Data d) {
        List<HomeAttention> items = homeAttentionItems(d);
        List<String> unavailable = unavailableAlertSources(d);
        LinearLayout card = homeCard(Theme.SECTION_ALERT);
        card.setPadding(Theme.dp(13), Theme.dp(10), Theme.dp(13), Theme.dp(12));
        card.addView(boardHead("مرکز توجه", null, R.drawable.mi_priority_high,
                null, Theme.SECTION_ALERT), a.kit.lp(-1, -2));

        if (items.isEmpty()) {
            String status = unavailable.isEmpty() ? "بدون هشدار مهم"
                    : "بررسی هشدارها کامل نیست";
            int tone = unavailable.isEmpty() ? Theme.SUCCESS : Theme.WARNING;
            LinearLayout state = a.kit.h();
            state.setGravity(Gravity.CENTER_VERTICAL);
            state.addView(statusChip(status, tone), a.kit.lp(-2, -2));
            card.addView(state, a.kit.lp(-1, -2));
            card.addView(a.kit.emptyRow(unavailable.isEmpty()
                    ? "فعلاً مورد مهمی برای پیگیری نیست"
                    : "از داده‌های در دسترس، هشدار مهمی پیدا نشد"), a.kit.lp(-1, -2));
        } else {
            HomeAttention primary = items.get(0);
            LinearLayout focus = a.kit.v();
            focus.setPadding(Theme.dp(11), Theme.dp(10), Theme.dp(11), Theme.dp(10));
            focus.setBackground(Theme.dashboardMetric(Theme.SECTION_ALERT));
            focus.setElevation(Theme.dp(1f));
            focus.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            LinearLayout heading = a.kit.h();
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = a.kit.text(primary.title, 12.5f, Theme.TEXT, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            heading.addView(title, a.kit.wlp(1f));
            heading.addView(statusChip(attentionLabel(primary.priority), primary.accent),
                    a.kit.lp(-2, -2));
            focus.addView(heading, a.kit.lp(-1, -2));
            TextView detail = a.kit.text(primary.detail, 10.5f,
                    Theme.dashboardMutedInk(Theme.SECTION_ALERT), false);
            detail.setMaxLines(3);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detailParams = a.kit.lp(-1, -2);
            detailParams.topMargin = Theme.dp(4);
            focus.addView(detail, detailParams);
            if (primary.action != null) {
                android.widget.Button action = a.kit.btnGhost("بررسی مورد ›", primary.accent,
                        v -> primary.action.run());
                LinearLayout.LayoutParams actionParams = a.kit.lp(-2, -2);
                actionParams.topMargin = Theme.dp(6);
                focus.addView(action, actionParams);
            }
            focus.setContentDescription(primary.title + "؛ " + primary.detail + "؛ اولویت "
                    + attentionLabel(primary.priority));
            card.addView(focus, a.kit.lp(-1, -2));

            int shown = Math.min(3, items.size());
            for (int i = 1; i < shown; i++) {
                final HomeAttention item = items.get(i);
                View.OnClickListener action = item.action == null ? null : v -> item.action.run();
                LinearLayout.LayoutParams rowParams = a.kit.lp(-1, -2);
                rowParams.topMargin = Theme.dp(6);
                card.addView(a.kit.alertRow(item.title, item.detail, item.accent, action), rowParams);
            }
            if (items.size() > shown) {
                TextView more = a.kit.text("+" + HomeNumberFormatter.integer(items.size() - shown)
                        + " هشدار دیگر در فهرست امروز", 9.5f, Theme.MUTED, false);
                more.setGravity(Gravity.END);
                LinearLayout.LayoutParams moreParams = a.kit.lp(-1, -2);
                moreParams.topMargin = Theme.dp(4);
                card.addView(more, moreParams);
            }
        }
        if (!unavailable.isEmpty())
            card.addView(a.kit.hint("بررسی ناقص بخش‌های: " + joinAlertSources(unavailable)), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private String attentionLabel(int priority) {
        return priority == 0 ? "فوری" : priority == 1 ? "توجه" : "اطلاع";
    }

    private List<HomeAttention> homeAttentionItems(Data d) {
        List<HomeAttention> items = new ArrayList<>();
        for (AlertEngine.Alert alert : homeEngineAlerts(d)) {
            int priority = alert.severity == AlertEngine.Severity.CRITICAL ? 0
                    : alert.severity == AlertEngine.Severity.WARNING ? 1 : 2;
            items.add(new HomeAttention(priority, severityColor(alert.severity),
                    engineAlertTitle(alert), engineAlertDetail(alert), () -> openHomeAlert(alert, d)));
        }

        // Keep the existing manager-only operational cues, but place them in the same priority list.
        if (d.overdue != null && !d.overdue.isEmpty() && RoleStore.allowed(a, "sales")) {
            Row invoice = d.overdue.get(0);
            long days = invoice.l("days");
            if (days < 0) days = Jalali.diffDays(invoice.s("dueDate"), Jalali.todayStr());
            String delay = days >= 0 ? " · تأخیر " + HomeNumberFormatter.integer(days) + " روز" : "";
            items.add(new HomeAttention(1, Theme.WARNING, "فاکتور معوق",
                    invoice.s("party") + " · " + HomeNumberFormatter.compactMoney(invoice.d("amount")) + delay,
                    this::gotoUnsettled));
        }
        if (!isSalesManager() && RoleStore.allowed(a, "buy") && d.salesDay != null && d.buyDay != null
                && d.salesDay.d("total") > 0 && d.buyDay.d("total") > d.salesDay.d("total") * 1.15) {
            items.add(new HomeAttention(1, Theme.WARNING, "فشار نقدینگی",
                    "خرید روز از فروش جلو زده؛ پرداخت‌ها را کنترل کنید.", () -> a.nav("buy")));
        }
        List<FollowUps.Due> due = FollowUps.dueToday(a);
        if (!due.isEmpty() && RoleStore.allowed(a, "customers")) {
            final FollowUps.Due first = due.get(0);
            String subject = due.size() == 1 ? first.note.name
                    : HomeNumberFormatter.integer(due.size()) + " یادداشت";
            items.add(new HomeAttention(2, Theme.INFO, "پیگیری مشتری",
                    subject + " · یادداشت سررسیدشده", () -> gotoCustomer(first.code)));
        }
        java.util.Collections.sort(items, (left, right) -> Integer.compare(left.priority, right.priority));
        return items;
    }

    /** Build AlertEngine input from the same successful, read-only dashboard snapshots. */
    private List<AlertEngine.Alert> homeEngineAlerts(Data d) {
        List<AlertEngine.StockItem> stock = null;
        if (RoleStore.allowed(a, "products") && d.stockAlertsAvailable) {
            stock = new ArrayList<>();
            if (d.stockAlerts != null) for (Row r : d.stockAlerts) {
                stock.add(new AlertEngine.StockItem(r.s("shka"), r.s("name"), r.d("stockPieces"),
                        r.d("reorderPoint"), r.l("reorderKnown") == 1));
            }
        }

        List<AlertEngine.PriceChange> prices = null;
        if (RoleStore.allowed(a, "buy") && d.purchasePriceTrendAvailable) {
            prices = new ArrayList<>();
            if (d.purchasePriceTrend != null) for (Row r : d.purchasePriceTrend) {
                prices.add(new AlertEngine.PriceChange(r.s("label"), r.d("prevUnit"), r.d("lastUnit")));
            }
        }

        SalesComparison.DayResult sales = null;
        if (RoleStore.allowed(a, "sales") && d.salesSnapshot != null && d.salesSnapshot.isAvailable()) {
            String day = snapshotDay(d);
            int minute = day.equals(Jalali.todayStr()) ? currentMinuteOfDay() : SalesComparison.MINUTES_PER_DAY;
            sales = SalesComparison.evaluate(d.salesSnapshot, day, minute,
                    SalesComparison.DEFAULT_COMPARABLE_WEEKS);
        }

        List<AlertEngine.DueItem> due = null;
        if (RoleStore.allowed(a, "cheques")) {
            due = new ArrayList<>();
            String today = Jalali.todayStr();
            if (d.dueInAvailable) addDueItems(due, d.dueIn, true, today);
            if (d.dueOutAvailable) addDueItems(due, d.dueOut, false, today);
        }

        List<AlertEngine.Alert> evaluated = AlertEngine.evaluate(stock, prices, sales,
                ManagerAlertsScreen.salesDropThreshold(a), due);
        List<AlertEngine.Alert> visible = new ArrayList<>();
        for (AlertEngine.Alert alert : evaluated) if (canOpenAlert(alert)) visible.add(alert);
        return visible;
    }

    private void addDueItems(List<AlertEngine.DueItem> target, List<Row> rows,
                             boolean incoming, String today) {
        if (target == null || rows == null) return;
        for (Row row : rows) {
            String date = Jalali.disp(row.s("sardate"));
            if (date.isEmpty()) continue;
            int days = Jalali.diffDays(today, date);
            target.add(new AlertEngine.DueItem(incoming, days, row.d("amount")));
        }
    }

    private boolean canOpenAlert(AlertEngine.Alert alert) {
        if (alert == null) return false;
        switch (alert.action) {
            case PRODUCT_FILE: return RoleStore.allowed(a, "products");
            case SALES: return RoleStore.allowed(a, "sales");
            case PURCHASES: return RoleStore.allowed(a, "buy");
            case CHEQUES: return RoleStore.allowed(a, "cheques");
            default: return false;
        }
    }

    private int severityColor(AlertEngine.Severity severity) {
        if (severity == AlertEngine.Severity.CRITICAL) return Theme.DANGER;
        if (severity == AlertEngine.Severity.WARNING) return Theme.WARNING;
        return Theme.INFO;
    }

    private String engineAlertTitle(AlertEngine.Alert alert) {
        String product = alert.subject == null || alert.subject.isEmpty()
                ? "کالای " + HomeNumberFormatter.localizeText(alert.actionValue) : alert.subject;
        switch (alert.code) {
            case "stock_out": return "ناموجود: " + product;
            case "stock_low": return "کم‌موجودی: " + product;
            case "sales_drop": return "افت غیرعادی فروش امروز";
            case "purchase_price_drift": return "انحراف بهای خرید: " + product;
            case "cheque_out_due": return "سررسید چک پرداختی";
            case "cheque_in_due": return "سررسید چک دریافتی";
            default: return "هشدار مدیریتی";
        }
    }

    private String engineAlertDetail(AlertEngine.Alert alert) {
        switch (alert.code) {
            case "stock_out":
                return "کد " + HomeNumberFormatter.localizeText(alert.actionValue) + " • موجودی " + HomeNumberFormatter.decimal(alert.value);
            case "stock_low":
                return "کد " + HomeNumberFormatter.localizeText(alert.actionValue) + " • موجودی " + HomeNumberFormatter.decimal(alert.value)
                        + " • نقطه سفارش " + HomeNumberFormatter.decimal(alert.referenceValue);
            case "sales_drop":
                return "پیش‌بینی پایان روز " + HomeNumberFormatter.compactMoney(alert.value)
                        + " • میانگین روز مشابه هفته " + HomeNumberFormatter.compactMoney(alert.referenceValue)
                        + " • افت " + HomeNumberFormatter.percentMagnitude(alert.changePercent);
            case "purchase_price_drift":
                return "دو خرید آخر: " + HomeNumberFormatter.compactMoney(alert.referenceValue) + " ← "
                        + HomeNumberFormatter.compactMoney(alert.value) + " • تغییر " + HomeNumberFormatter.signedPercent(alert.changePercent);
            case "cheque_out_due":
            case "cheque_in_due":
                String dueDay = alert.daysUntilDue == 0 ? "امروز"
                        : "تا " + HomeNumberFormatter.integer(alert.daysUntilDue) + " روز";
                String amount = alert.value > 0 ? " • مبلغ " + HomeNumberFormatter.compactMoney(alert.value) : "";
                return HomeNumberFormatter.integer(alert.count) + " فقره • " + dueDay + amount;
            default:
                return "برای بررسی، این بخش را باز کنید.";
        }
    }

    private void openHomeAlert(AlertEngine.Alert alert, Data d) {
        if (alert == null) return;
        switch (alert.action) {
            case PRODUCT_FILE:
                ProductFileScreen.open(a, alert.actionValue);
                break;
            case SALES:
                Row today = new Row();
                today.put("d", snapshotDay(d));
                gotoDay("sales", today);
                break;
            case PURCHASES:
                a.nav("buy");
                break;
            case CHEQUES:
                a.nav("cheques");
                break;
            default:
                break;
        }
    }

    private List<String> unavailableAlertSources(Data d) {
        List<String> missing = new ArrayList<>();
        if (RoleStore.allowed(a, "products") && !d.stockAlertsAvailable) missing.add("موجودی کالا");
        else if (RoleStore.allowed(a, "products") && !d.reorderPointAvailable) missing.add("نقطه سفارش کالا");
        if (RoleStore.allowed(a, "buy") && !d.purchasePriceTrendAvailable) missing.add("دو خرید آخر");
        if (RoleStore.allowed(a, "sales") && (d.salesSnapshot == null || !d.salesSnapshot.isAvailable()))
            missing.add("مقایسه فروش");
        if (RoleStore.allowed(a, "cheques") && (!d.dueInAvailable || !d.dueOutAvailable))
            missing.add("سررسید چک");
        return missing;
    }

    private String joinAlertSources(List<String> sources) {
        StringBuilder joined = new StringBuilder();
        for (String source : sources) {
            if (joined.length() > 0) joined.append("، ");
            joined.append(source);
        }
        return joined.toString();
    }

    /**
     * Return documents of the last 30 days. dbo.back_sanad is the ledger of goods
     * moving back — out of the warehouse, back from a customer, and so on — so a
     * sudden rise here is a quality or logistics problem long before it shows up
     * in the profit figures.
     */
    private void secReturns(LinearLayout content, Data d) {
        LinearLayout card = homeCard(Theme.SECTION_RETURNS);
        card.addView(boardHead("برگشتی‌های ۳۰ روز اخیر", "ابطال و برگشت، ۳۰ روز گذشته",
                R.drawable.mi_assignment_return, navChip("گزارش برگشتی‌ها", "reports")),
                a.kit.lp(-1, -2));
        long docs = 0;
        double total = 0;
        if (d.returns != null) for (Row r : d.returns) {
            docs += r.l("docs");
            total += r.d("total");
        }
        if (docs == 0) {
            card.addView(a.kit.emptyRow("سند برگشتی فعالی در این بازه ثبت نشده"), a.kit.lp(-1, -2));
        } else {
            // One wide summary line, then one compact tile per kind of return.
            card.addView(statRow(R.drawable.mi_assignment_return, "برگشتی ۳۰ روز اخیر",
                    HomeNumberFormatter.integer(docs) + " سند • " + HomeNumberFormatter.compactMoney(total), Theme.SECTION_RETURNS),
                    a.kit.lp(-1, -2));
            card.addView(a.kit.gap(10));
            List<Mini> tiles = new ArrayList<>();
            int shown = 0;
            for (Row r : d.returns) {
                if (shown++ >= 3) break;
                String label = r.s("label");
                if (label == null || label.trim().isEmpty()) continue;
                tiles.add(new Mini(returnIcon(label), label,
                        HomeNumberFormatter.integer(r.l("docs")) + " سند", HomeNumberFormatter.compactMoney(r.d("total")), Theme.TEXT));
            }
            if (!tiles.isEmpty())
                card.addView(miniGrid(tiles, 3, Theme.SECTION_RETURNS), a.kit.lp(-1, -2));
        }
        card.addView(a.kit.text("ابطالی‌های فاکتور در آمار اسناد برگشتی لحاظ نمی‌شوند.",
                9.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    /** Vector icon for a return kind: a cancelled document or a returned sale. */
    private int returnIcon(String label) {
        String s = label == null ? "" : label;
        if (s.contains("ابطال")) return R.drawable.mi_sell;
        return R.drawable.mi_receipt_long;
    }

    /** Compact sales pulse: one useful daily chart, ranked products and month-over-month context. */
    private void secSalesInsight(LinearLayout content, Data d) {
        boolean hasDaily = d.salesSnapshot != null && d.salesSnapshot.isAvailable();
        boolean hasProducts = d.topItems != null && !d.topItems.isEmpty();
        boolean hasMonths = hasDaily;
        if (!hasDaily && !hasProducts && !hasMonths) return;

        if (hasDaily) {
            String endDay = snapshotDay(d);
            List<Charts.Point> points = filled(d.salesSnapshot, endDay);
            SalesComparison.DayResult dayComparison = SalesComparison.evaluate(d.salesSnapshot, endDay,
                    endDay.equals(Jalali.todayStr()) ? currentMinuteOfDay() : SalesComparison.MINUTES_PER_DAY,
                    SalesComparison.DEFAULT_COMPARABLE_WEEKS);
            double today = dayComparison.actualRials;
            LinearLayout trend = homeCard(Theme.SECTION_SALES);
            String period = points.isEmpty() ? "تاریخچهٔ موجود"
                    : points.size() == 14 ? "۱۴ روزه"
                    : HomeNumberFormatter.integer(points.size()) + " روز از تاریخچهٔ موجود";
            trend.addView(boardHead("روند " + period + " فروش تا " + snapshotLabel(d),
                    "فروش روزانه، روز به روز", R.drawable.mi_bar_chart,
                    navChip("گزارش فروش", "sales")), a.kit.lp(-1, -2));
            if (dayComparison.hasBaseline() && dayComparison.projectionAvailable) {
                double change = dayComparison.changePercent();
                int deltaDirection = Math.abs(change) < 0.05 ? 0 : change > 0 ? 1 : -1;
                int color = !dayComparison.complete ? Theme.INFO
                        : deltaDirection > 0 ? Theme.SUCCESS
                        : deltaDirection < 0 ? Theme.DANGER : Theme.MUTED;
                String direction = deltaDirection > 0 ? "بالاتر"
                        : deltaDirection < 0 ? "پایین‌تر" : "هم‌سطح";
                String basis = dayComparison.weeklyHoliday ? "جمعه‌های پیشین" : Jalali.weekday(endDay) + "های پیشین";
                String metric = dayComparison.complete ? "فروش " : "پیش‌بینی امروز ";
                double comparisonValue = dayComparison.complete ? today : dayComparison.projectedRials;
                LinearLayout pulse = a.kit.h();
                pulse.setGravity(Gravity.CENTER_VERTICAL);
                pulse.setPadding(Theme.dp(9), Theme.dp(7), Theme.dp(9), Theme.dp(7));
                pulse.setBackground(Theme.kpi(color));
                pulse.addView(a.kit.text(metric + snapshotLabel(d), 10.5f, Theme.MUTED, false), a.kit.lp(-2, -2));
                pulse.addView(a.kit.space(7));
                pulse.addView(a.kit.text(HomeNumberFormatter.compactMoney(comparisonValue),
                        11.5f, Theme.TEXT, true), a.kit.wlp(1f));
                String deltaLabel = HomeNumberFormatter.percentMagnitude(change) + " " + direction + " از " + basis;
                View delta = a.kit.deltaBadge(deltaLabel, deltaDirection, color,
                        "تغییر " + direction + " نسبت به " + basis + "؛ "
                                + HomeNumberFormatter.percentMagnitude(change));
                pulse.addView(delta, a.kit.lp(-2, -2));
                trend.addView(pulse, a.kit.lp(-1, -2));
                trend.addView(a.kit.gap(4));
            }
            boolean hasActivity = false;
            for (Charts.Point point : points)
                if (point != null && Math.abs(point.value) >= 0.005d) { hasActivity = true; break; }
            if (points.isEmpty()) {
                trend.addView(a.kit.emptyRow("تاریخچهٔ فروش برای این بازه در دسترس نیست"),
                        a.kit.lp(-1, -2));
            } else if (!hasActivity) {
                trend.addView(a.kit.emptyRow("در این بازه فاکتوری ثبت نشده است"),
                        a.kit.lp(-1, -2));
            } else {
                Charts.Area chart = new Charts.Area(a);
                TextView caption = a.kit.text(lastCap(points), 10f, Theme.MUTED, false);
                chart.setData(points, Theme.SECTION_SALES, HomeNumberFormatter::chartMoney);
                chart.setListener((idx, point) -> caption.setText(HomeNumberFormatter.localizeText(point.label)
                        + " • " + HomeNumberFormatter.money(point.value)));
                trend.addView(chart, new LinearLayout.LayoutParams(-1, Theme.dp(126)));
                trend.addView(caption, a.kit.lp(-1, -2));
            }
            a.kit.addCard(content, trend);
        }

        if (hasProducts || hasMonths) {
            LinearLayout card = homeCard(Theme.SECTION_SALES);
            card.addView(dashboardSectionHead("نبض فروش", R.drawable.mi_bar_chart,
                    Theme.SECTION_SALES, "فروش ›", navAction("sales")), a.kit.lp(-1, -2));
            if (hasProducts) {
                card.addView(insightSubhead("پرفروش‌های " + snapshotLabel(d), R.drawable.mi_grid_view, Theme.SECTION_SALES),
                        a.kit.lp(-1, -2));
                int shown = Math.min(3, d.topItems.size());
                double max = 0;
                for (int i = 0; i < shown; i++) max = Math.max(max, d.topItems.get(i).d("total"));
                for (int i = 0; i < shown; i++) {
                    Row item = d.topItems.get(i);
                    String label = item.s("label");
                    if (label == null || label.trim().isEmpty()) label = "کالای بدون نام";
                    card.addView(a.kit.progressLine(HomeNumberFormatter.integer(i + 1) + " • " + label,
                            HomeNumberFormatter.compactMoney(item.d("total")), max > 0 ? item.d("total") / max : 0, Theme.SECTION_SALES),
                            a.kit.lp(-1, -2));
                    TextView quantity = a.kit.text(HomeNumberFormatter.decimal(item.d("qty")) + " واحد • "
                            + HomeNumberFormatter.integer(item.l("docs")) + " ردیف", 9.5f, Theme.MUTED, false);
                    quantity.setGravity(Gravity.END);
                    card.addView(quantity, a.kit.lp(-1, -2));
                }
            }
            if (hasMonths) {
                String today = Jalali.todayStr();
                HomeRange currentRange = HomeRange.monthToDate(today);
                HomeRange previousRange = HomeRange.previousMonthSamePeriod(today);
                double currentMtd = d.salesSnapshot.salesRialsBetween(currentRange.from, currentRange.to);
                double previousComparable = d.salesSnapshot.salesRialsBetween(previousRange.from, previousRange.to);
                int elapsed = HomeRange.dayOfMonth(today);
                int monthDays = HomeRange.daysInMonth(today);
                double forecast = HomeRange.projectMonthEnd(currentMtd, today);
                String currentLabel = monthLabel(currentRange.from) + " • روز ۱ تا " + HomeNumberFormatter.integer(elapsed);
                String previousLabel = monthLabel(previousRange.from) + " • هم‌بازه";

                if (hasProducts) card.addView(a.kit.gap(7));
                card.addView(insightSubhead("مقایسهٔ ماهانه", R.drawable.mi_sell, Theme.SECTION_SALES),
                        a.kit.lp(-1, -2));
                LinearLayout months = a.kit.h();
                months.addView(insightMetric(currentLabel, HomeNumberFormatter.compactMoney(currentMtd), Theme.SECTION_SALES),
                        a.kit.wlp(1f));
                months.addView(a.kit.space(7));
                months.addView(insightMetric(previousLabel, HomeNumberFormatter.compactMoney(previousComparable), Theme.SECTION_SALES),
                        a.kit.wlp(1f));
                card.addView(months, a.kit.lp(-1, -2));

                if (previousComparable > 0) {
                    double change = (currentMtd - previousComparable) * 100.0 / previousComparable;
                    int direction = Math.abs(change) < 0.05 ? 0 : change > 0 ? 1 : -1;
                    int color = direction > 0 ? Theme.SUCCESS
                            : direction < 0 ? Theme.DANGER : Theme.MUTED;
                    String deltaLabel = direction == 0 ? "تغییر هم‌بازه: بدون تغییر"
                            : "تغییر هم‌بازه: " + HomeNumberFormatter.percentMagnitude(change);
                    card.addView(a.kit.deltaBadge(deltaLabel, direction, color,
                            "تغییر هم‌بازه " + HomeNumberFormatter.signedPercent(change)),
                            a.kit.lp(-1, -2));
                } else {
                    card.addView(a.kit.text("برای این مقایسه، فروش هم‌بازه‌ای در ماه قبل ثبت نشده است.",
                            10f, Theme.MUTED, false), a.kit.lp(-1, -2));
                }

                String forecastText = "پیش‌بینی پایان " + monthLabel(currentRange.from) + " بر پایهٔ "
                        + HomeNumberFormatter.integer(elapsed) + " روز از " + HomeNumberFormatter.integer(monthDays)
                        + " روز تقویمی: " + HomeNumberFormatter.compactMoney(forecast);
                card.addView(a.kit.text(forecastText, 10f, Theme.INFO, true), a.kit.lp(-1, -2));
            }
            a.kit.addCard(content, card);
        }
    }

    /** Compact, accessible receipt mix: one stacked strip with concise amount/percentage chips. */
    private void secCashMix(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "dar_in")) return;
        LinearLayout card = homeCard(Theme.SECTION_RECEIVE);
        card.setPadding(Theme.dp(13), Theme.dp(10), Theme.dp(13), Theme.dp(11));
        card.addView(dashboardSectionHead("ترکیب دریافت", R.drawable.mi_receipt_long,
                Theme.SECTION_RECEIVE, "دریافت‌ها ›", navAction("dar_in")), a.kit.lp(-1, -2));

        if (d.inDay == null || !d.inMixAvailable || d.inMix == null) {
            card.addView(a.kit.emptyRow("تفکیک روش‌های دریافت در دسترس نیست"), a.kit.lp(-1, -2));
            card.addView(a.kit.hint("منبع دریافت ناقص است؛ مبلغ به اجزای ساختگی تقسیم نمی‌شود."),
                    a.kit.lp(-1, -2));
            a.kit.addCard(content, card);
            return;
        }
        double total = Math.max(0, d.inDay.d("total"));
        if (total <= 0) {
            card.addView(a.kit.emptyRow("برای " + snapshotLabel(d) + " دریافتی ثبت نشده است"),
                    a.kit.lp(-1, -2));
            a.kit.addCard(content, card);
            return;
        }

        double cash = 0, cardAmount = 0, transfer = 0, cheque = 0;
        for (Row row : d.inMix) {
            if (row == null) continue;
            String label = row.s("label");
            double amount = Math.max(0, row.d("amount"));
            if (label.contains("نقد")) cash += amount;
            else if (label.contains("حواله")) transfer += amount;
            else if (label.contains("کارت")) cardAmount += amount;
            else if (label.contains("چک")) cheque += amount;
        }
        double known = cash + cardAmount + transfer + cheque;
        double other = Math.max(0, total - known);
        double denominator = known > total ? known : total;
        boolean mismatch = Math.abs(total - known) >= 500_000;

        List<String> labels = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        labels.add("نقد"); amounts.add(cash); colors.add(Theme.SECTION_RECEIVE);
        labels.add("کارت"); amounts.add(cardAmount); colors.add(Theme.SECTION_PURCHASE);
        labels.add("حواله"); amounts.add(transfer); colors.add(Theme.SECTION_MARKET);
        labels.add("چک"); amounts.add(cheque); colors.add(Theme.SECTION_ALERT);
        if (other > 0) {
            labels.add("سایر"); amounts.add(other); colors.add(Theme.MUTED);
        }
        double[] segments = new double[amounts.size()];
        int[] accents = new int[amounts.size()];
        StringBuilder accessible = new StringBuilder("ترکیب دریافت ").append(snapshotLabel(d));
        for (int i = 0; i < amounts.size(); i++) {
            segments[i] = amounts.get(i);
            accents[i] = colors.get(i);
            if (i > 0) accessible.append("؛ ");
            accessible.append(labels.get(i)).append(' ')
                    .append(HomeNumberFormatter.percent(denominator > 0 ? amounts.get(i) * 100d / denominator : 0))
                    .append("، ").append(HomeNumberFormatter.compactMoney(amounts.get(i)));
        }

        LinearLayout totalRow = a.kit.h();
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView period = a.kit.text("جمع " + snapshotLabel(d), 10f, Theme.MUTED, false);
        totalRow.addView(period, a.kit.wlp(1f));
        totalRow.addView(a.kit.amountLine(HomeNumberFormatter.compactMoney(total),
                15f, Gravity.END), a.kit.lp(-2, -2));
        card.addView(totalRow, a.kit.lp(-1, -2));

        HomePaymentMixBar bar = new HomePaymentMixBar(a);
        bar.setSegments(segments, accents, accessible.toString());
        LinearLayout.LayoutParams barParams = a.kit.lp(-1, Theme.dp(9));
        barParams.topMargin = Theme.dp(8);
        card.addView(bar, barParams);

        HorizontalScrollView legendScroll = new HorizontalScrollView(a);
        legendScroll.setHorizontalScrollBarEnabled(false);
        legendScroll.setHorizontalFadingEdgeEnabled(true);
        legendScroll.setFadingEdgeLength(Theme.dp(14));
        legendScroll.setClipToPadding(false);
        legendScroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout legend = a.kit.h();
        legend.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < labels.size(); i++) {
            View item = mixLegendItem(labels.get(i), amounts.get(i), denominator, colors.get(i));
            LinearLayout.LayoutParams itemParams = a.kit.lp(Theme.dp(104), -2);
            if (i < labels.size() - 1) itemParams.setMarginEnd(Theme.dp(6));
            legend.addView(item, itemParams);
        }
        legendScroll.addView(legend, new HorizontalScrollView.LayoutParams(-2, -2));
        legendScroll.setContentDescription("تفکیک دریافت به نقد، کارت، حواله و چک");
        LinearLayout.LayoutParams legendParams = a.kit.lp(-1, -2);
        legendParams.topMargin = Theme.dp(7);
        card.addView(legendScroll, legendParams);
        if (mismatch) {
            TextView note = a.kit.text("مجموع اجزا با کل قبض اختلاف دارد؛ درصدها بر پایهٔ مجموع اجزای قابل تفکیک‌اند.",
                    9f, Theme.MUTED, false);
            note.setMaxLines(2);
            LinearLayout.LayoutParams noteParams = a.kit.lp(-1, -2);
            noteParams.topMargin = Theme.dp(4);
            card.addView(note, noteParams);
        }
        a.kit.addCard(content, card);
    }

    private View mixLegendItem(String label, double amount, double denominator, int color) {
        LinearLayout item = a.kit.v();
        item.setGravity(Gravity.CENTER);
        item.setPadding(Theme.dp(6), Theme.dp(5), Theme.dp(6), Theme.dp(5));
        item.setBackground(Theme.dashboardMetric(Theme.SECTION_RECEIVE));
        item.setElevation(Theme.dp(1f));
        item.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        LinearLayout heading = a.kit.h();
        heading.setGravity(Gravity.CENTER);
        TextView dot = a.kit.text("●", 8f, color, true);
        heading.addView(dot, a.kit.lp(-2, -2));
        heading.addView(a.kit.space(4));
        heading.addView(a.kit.text(label, 9.5f, Theme.TEXT, true), a.kit.lp(-2, -2));
        item.addView(heading, a.kit.lp(-1, -2));
        item.addView(a.kit.text(HomeNumberFormatter.percent(
                denominator > 0 ? amount * 100d / denominator : 0), 10f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        item.addView(a.kit.text(HomeNumberFormatter.compactMoney(amount), 8.5f,
                        Theme.dashboardMutedInk(Theme.SECTION_RECEIVE), false),
                a.kit.lp(-1, -2));
        item.setContentDescription(label + "، "
                + HomeNumberFormatter.percent(denominator > 0 ? amount * 100d / denominator : 0)
                + "، " + HomeNumberFormatter.money(amount));
        return item;
    }

    private View insightSubhead(String title, int icon, int accent) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art = dashboardArt(icon, accent);
        row.addView(art, a.kit.lp(Theme.dp(32), Theme.dp(32)));
        row.addView(a.kit.space(6));
        row.addView(a.kit.text(title, 11f, Theme.dashboardAccentInk(accent), true), a.kit.wlp(1f));
        return row;
    }

    private View insightMetric(String label, String value, int accent) {
        LinearLayout tile = a.kit.v();
        tile.setPadding(Theme.dp(9), Theme.dp(7), Theme.dp(9), Theme.dp(7));
        tile.setBackground(Theme.dashboardMetric(accent));
        tile.setElevation(Theme.dp(1f));
        tile.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        TextView title = a.kit.text(label, 9.5f, Theme.dashboardMutedInk(accent), false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title, a.kit.lp(-1, -2));
        tile.addView(a.kit.amountLine(value, 11.5f, Gravity.START), a.kit.lp(-1, -2));
        return tile;
    }

    private void secDue(LinearLayout content, Data d) {
        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = homeCard(Theme.SECTION_ALERT);
            c.addView(boardHead("سررسید ۳ روز آینده", "چک‌های دریافتی و پرداختی",
                    R.drawable.mi_cheque, navChip("گزارش چک‌ها", "cheques")), a.kit.lp(-1, -2));
            if (d.dueIn != null && !d.dueIn.isEmpty()) {
                c.addView(statRow(R.drawable.mi_cheque, "دریافتی " + HomeNumberFormatter.integer(d.dueIn.size()) + " فقره",
                        HomeNumberFormatter.money(dueInSum), Theme.SECTION_RECEIVE), a.kit.lp(-1, -2));
                c.addView(a.kit.gap(8));
            }
            if (d.dueOut != null && !d.dueOut.isEmpty())
                c.addView(statRow(R.drawable.mi_cheque, "پرداختی " + HomeNumberFormatter.integer(d.dueOut.size()) + " فقره",
                        HomeNumberFormatter.money(dueOutSum), Theme.SECTION_PAY), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secCashPosition(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "cash")) return;
        final String day = snapshotDay(d);
        boolean receiptsAvailable = d != null && d.cashReceiptsAvailable && d.cashReceipts != null;
        boolean paymentsAvailable = d != null && d.cashPaymentsAvailable && d.cashPayments != null;
        boolean flowAvailable = receiptsAvailable && paymentsAvailable;
        Double opening = parseLocalRials(a.settings.closeoutCashOpening(day));
        double receipts = receiptsAvailable ? d.cashReceipts.d("cash") : Double.NaN;
        double payments = paymentsAvailable ? d.cashPayments.d("cash") : Double.NaN;
        List<CashWaterfallEngine.Step> steps = flowAvailable && opening != null
                ? CashWaterfallEngine.build(opening, receipts, payments)
                : java.util.Collections.emptyList();

        LinearLayout card = homeCard(Theme.SECTION_CASH_POSITION);
        card.setTag("home-cash-position");
        Button entry = dashboardHeaderAction("ثبت صندوق", Theme.SECTION_CASH_POSITION,
                "ثبت ماندهٔ آغاز و شمارش پایان صندوق؛ فقط روی این دستگاه",
                v -> showCloseoutCashDialog(content, d, day));
        card.addView(boardHead("موقعیت نقدی", "گردش صندوق به‌صورت آبشاری • " + day,
                R.drawable.mi_account_balance_wallet, entry, Theme.SECTION_CASH_POSITION),
                a.kit.lp(-1, -2));

        List<Mini> metrics = new ArrayList<>();
        metrics.add(new Mini(R.drawable.mi_receipt_long, "دریافت نقد امروز",
                receiptsAvailable ? HomeNumberFormatter.money(receipts) : "—",
                receiptsAvailable ? "ورودی ثبت‌شده" : "تفکیک نقد موجود نیست", Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_payments, "پرداخت نقد امروز",
                paymentsAvailable ? HomeNumberFormatter.money(payments) : "—",
                paymentsAvailable ? "خروجی ثبت‌شده" : "تفکیک نقد موجود نیست", Theme.TEXT));
        if (!steps.isEmpty()) {
            double expectedClosing = steps.get(steps.size() - 1).endRials;
            metrics.add(new Mini(R.drawable.mi_account_balance_wallet, "ماندهٔ نقد مورد انتظار",
                    HomeNumberFormatter.money(expectedClosing), "برآورد پایان روز", Theme.TEXT));
        }
        String counted = a.settings.closeoutCashCounted(day);
        if (counted != null && !counted.trim().isEmpty()) {
            String variance = closeoutVarianceLabel(d, day);
            metrics.add(new Mini(R.drawable.mi_point_of_sale, "اختلاف با شمارش",
                    compactCloseoutVariance(variance), closeoutVarianceHint(variance), Theme.TEXT));
        }
        card.addView(dashboardMetricGrid(metrics, 2, Theme.SECTION_CASH_POSITION),
                a.kit.lp(-1, -2));

        if (steps.isEmpty()) {
            String nextStep = !flowAvailable
                    ? "برای نمودار، تفکیک نقد دریافت و پرداخت باید در دسترس باشد."
                    : (opening == null ? "با ثبت ماندهٔ نقد آغاز روز، نمودار گردش نمایش داده می‌شود."
                            : "گردش نقد این روز قابل محاسبه نیست؛ داده‌های همان روز را تازه‌سازی کنید.");
            card.addView(a.kit.hint(nextStep + " ماندهٔ محاسباتی، موجودی حساب بانکی نیست."),
                    a.kit.lp(-1, -2));
        } else {
            HomeCashWaterfall waterfall = new HomeCashWaterfall(a);
            waterfall.setSteps(steps);
            card.addView(waterfall, a.kit.lp(-1, Theme.dp(196)));
            card.addView(a.kit.hint("ماندهٔ پایان = ماندهٔ آغاز + دریافت نقد − پرداخت نقد؛ این رقم موجودی حساب بانکی نیست."),
                    a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    private void secTopPerformers(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "sales")) return;
        final int selected = selectedPerformerRange == 1 ? 1 : 0;
        final boolean month = selected == 1;
        final String period = month ? "این ماه" : "امروز";
        LinearLayout card = homeCard(Theme.SECTION_TOP_PERFORMERS);
        card.addView(boardHead("برترین‌ها", "پنج مشتری و پنج کالای برتر • " + period,
                R.drawable.mi_military_tech, null), a.kit.lp(-1, -2));
        card.addView(a.kit.segmentedControl(PERFORMER_RANGES, selected, null,
                Theme.SECTION_TOP_PERFORMERS, index -> {
                    selectedPerformerRange = index == 1 ? 1 : 0;
                    content.removeAllViews();
                    secTopPerformers(content, d);
                    localizeHomeNumbers(content);
                }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        List<Row> customers = month ? d.topCustomersMonth : d.topCustomersToday;
        boolean customersAvailable = d != null && (month
                ? d.topCustomersMonthAvailable : d.topCustomersTodayAvailable);
        card.addView(topPerformerSubhead("مشتری‌های برتر", R.drawable.mi_person_search),
                a.kit.lp(-1, -2));
        addTopPerformerRows(card, customers, customersAvailable, false, "مشتری");
        card.addView(a.kit.gap(8));

        List<Row> products = month ? d.topItemsMonth : d.topItems;
        boolean productsAvailable = d != null && (month
                ? d.topItemsMonthAvailable : d.topItemsAvailable);
        card.addView(topPerformerSubhead("کالاهای برتر", R.drawable.mi_inventory_2),
                a.kit.lp(-1, -2));
        addTopPerformerRows(card, products, productsAvailable, true, "کالا");
        a.kit.addCard(content, card);
    }

    private View topPerformerSubhead(String title, int icon) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = a.kit.icon(icon, Theme.sectionText(Theme.SECTION_TOP_PERFORMERS), 22);
        image.setBackground(Theme.avatar(Theme.alpha(Theme.SECTION_TOP_PERFORMERS,
                Theme.isLight() ? 24 : 38)));
        int inset = Theme.dp(5);
        image.setPadding(inset, inset, inset, inset);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image, a.kit.lp(Theme.dp(30), Theme.dp(30)));
        row.addView(a.kit.space(6));
        TextView label = a.kit.text(title, 11.5f,
                Theme.sectionText(Theme.SECTION_TOP_PERFORMERS), true);
        row.addView(label, a.kit.wlp(1f));
        return row;
    }

    private void addTopPerformerRows(LinearLayout card, List<Row> rows,
                                     boolean available, boolean products, String noun) {
        if (!available) {
            card.addView(a.kit.emptyRow("فهرست " + noun + " برای این بازه در دسترس نیست."),
                    a.kit.lp(-1, -2));
            return;
        }
        if (rows == null || rows.isEmpty()) {
            String empty = "مشتری".equals(noun)
                    ? "برای این بازه هنوز مشتری‌ای در فروش ثبت نشده است."
                    : "برای این بازه هنوز کالایی در فروش ثبت نشده است.";
            card.addView(a.kit.emptyRow(empty), a.kit.lp(-1, -2));
            return;
        }
        int count = Math.min(5, rows.size());
        for (int i = 0; i < count; i++) {
            Row item = rows.get(i);
            if (item == null) continue;
            card.addView(topPerformerRow(item, i + 1, products), a.kit.lp(-1, -2));
            if (i + 1 < count) card.addView(a.kit.gap(4));
        }
    }

    private View topPerformerRow(Row item, int rank, boolean product) {
        String name = item.s("label", "بدون نام");
        long docs = item.l("docs");
        String sub = product
                ? HomeNumberFormatter.compactUnit(item.d("qty")) + " واحد • "
                        + HomeNumberFormatter.integer(docs) + " فاکتور"
                : HomeNumberFormatter.integer(docs) + " فاکتور";
        double total = item.d("total");

        LinearLayout row = a.kit.h();
        row.setMinimumHeight(Theme.dp(56));
        row.setPadding(Theme.dp(5), Theme.dp(5), Theme.dp(5), Theme.dp(5));
        row.setBackground(Theme.dashboardMetric(Theme.SECTION_TOP_PERFORMERS));
        row.setElevation(Theme.dp(1f));
        row.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        TextView rankView = a.kit.text(HomeNumberFormatter.integer(rank), 11f,
                Theme.onAccent(Theme.SECTION_TOP_PERFORMERS), true);
        rankView.setGravity(Gravity.CENTER);
        rankView.setBackground(Theme.chip(true, Theme.SECTION_TOP_PERFORMERS));
        rankView.setContentDescription("رتبهٔ " + HomeNumberFormatter.integer(rank));
        row.addView(rankView, new LinearLayout.LayoutParams(Theme.dp(30), Theme.dp(30)));
        row.addView(a.kit.space(8));

        LinearLayout info = a.kit.v();
        TextView title = a.kit.text(name, 11f, Theme.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView detail = a.kit.text(sub, 9.5f,
                Theme.dashboardMutedInk(Theme.SECTION_TOP_PERFORMERS), false);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title, a.kit.lp(-1, -2));
        info.addView(detail, a.kit.lp(-1, -2));
        row.addView(info, a.kit.wlp(1f));

        View amount = a.kit.amountLine(HomeNumberFormatter.compactMoney(total), 10.5f,
                Gravity.END);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                Theme.dp(112), -2);
        amountParams.setMarginStart(Theme.dp(6));
        row.addView(amount, amountParams);
        row.setContentDescription("رتبهٔ " + HomeNumberFormatter.integer(rank) + "، " + name
                + "، " + sub + "، " + HomeNumberFormatter.money(total));
        return row;
    }

    private void secSeasonalCalendar(LinearLayout content) {
        String today = Jalali.disp(Jalali.todayStr());
        if (today.length() < 7) return;
        final int year;
        final int currentMonth;
        try {
            year = Integer.parseInt(today.substring(0, 4));
            currentMonth = Integer.parseInt(today.substring(5, 7));
        } catch (Exception invalid) {
            return;
        }
        final int selectedMonth = selectedSeasonalMonth >= 1 && selectedSeasonalMonth <= 12
                ? selectedSeasonalMonth : currentMonth;
        final int season = SeasonalCalendarEngine.seasonIndex(selectedMonth);
        if (season < 0) return;
        final int accent = Theme.SECTION_SEASONAL_CALENDAR;
        LinearLayout card = homeCard(accent);
        String yearLabel = HomeNumberFormatter.localizeText(String.valueOf(year));
        card.addView(boardHead("تقویم فصلی خشکبار", "گاه‌شمار جلالی " + yearLabel
                        + " • برنامه‌ریزی محلی خرید و موجودی",
                R.drawable.mi_calendar_month, null), a.kit.lp(-1, -2));

        String[] seasonNames = {"بهار", "تابستان", "پاییز", "زمستان"};
        card.addView(a.kit.segmentedControl(seasonNames, season, null, accent, index -> {
            selectedSeasonalMonth = SeasonalCalendarEngine.firstMonth(index);
            content.removeAllViews();
            secSeasonalCalendar(content);
            localizeHomeNumbers(content);
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        LinearLayout months = a.kit.h();
        months.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int position = 0; position < 3; position++) {
            final int month = SeasonalCalendarEngine.monthAt(season, position);
            String date = String.format(Locale.US, "%04d/%02d/01", year, month);
            String name = Jalali.monthName(date);
            final boolean chosen = month == selectedMonth;
            TextView monthChip = a.kit.text(name, 11f,
                    chosen ? Theme.onAccent(accent) : Theme.TEXT, true);
            monthChip.setGravity(Gravity.CENTER);
            monthChip.setMinHeight(Theme.dp(48));
            monthChip.setBackground(Theme.chip(chosen, accent));
            monthChip.setContentDescription(name + " " + yearLabel + (chosen ? "، انتخاب‌شده" : ""));
            monthChip.setSelected(chosen);
            monthChip.setFocusable(true);
            Theme.pressable(monthChip);
            monthChip.setOnClickListener(v -> {
                selectedSeasonalMonth = month;
                content.removeAllViews();
                secSeasonalCalendar(content);
                localizeHomeNumbers(content);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            if (position > 0) params.setMarginStart(Theme.dp(6));
            months.addView(monthChip, params);
        }
        card.addView(months, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        String monthKey = String.format(Locale.US, "%04d/%02d", year, selectedMonth);
        String monthName = Jalali.monthName(monthKey + "/01");
        String note = a.settings.homeSeasonalPlan(monthKey);
        card.addView(a.kit.text("برنامهٔ " + monthName + " " + yearLabel,
                12f, Theme.dashboardAccentInk(accent), true), a.kit.lp(-1, -2));
        if (note == null || note.trim().isEmpty()) {
            card.addView(a.kit.emptyRow("برای این ماه هنوز برنامهٔ محلی ثبت نشده است."),
                    a.kit.lp(-1, -2));
        } else {
            TextView plan = a.kit.text(note, 11.5f, Theme.TEXT, false);
            plan.setPadding(Theme.dp(10), Theme.dp(8), Theme.dp(10), Theme.dp(8));
            plan.setBackground(Theme.dashboardMetric(accent));
            plan.setElevation(Theme.dp(1f));
            plan.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            plan.setContentDescription("برنامهٔ محلی " + monthName + " " + yearLabel + ": " + note);
            card.addView(plan, a.kit.lp(-1, -2));
        }
        card.addView(a.kit.btnGhost(note == null || note.trim().isEmpty()
                        ? "ثبت برنامهٔ این ماه" : "ویرایش برنامهٔ این ماه", accent,
                v -> showSeasonalPlanDialog(content, monthKey, note)), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("این گاه‌شمار فقط ماه‌های جلالی را در چهار فصل گروه‌بندی می‌کند؛ فصل برداشت، قیمت، تعطیلی یا مناسبت رسمی حدس زده نمی‌شود. یادداشت‌ها فقط روی همین دستگاه می‌مانند."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private void showSeasonalPlanDialog(LinearLayout content, String monthKey, String currentNote) {
        LinearLayout form = a.kit.v();
        form.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(8));
        form.addView(a.kit.hint("یادداشت برنامهٔ خرید، نگهداری یا آماده‌سازی؛ فقط در تنظیمات محلی ذخیره می‌شود."),
                a.kit.lp(-1, -2));
        android.widget.EditText input = a.kit.edit("یادداشت این ماه", currentNote);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setContentDescription("یادداشت فصلی خشکبار برای " + monthKey);
        form.addView(input, a.kit.lp(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle("برنامهٔ فصلی خشکبار")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setNeutralButton("پاک‌کردن", null)
                .setPositiveButton("ذخیره روی دستگاه", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                a.settings.setHomeSeasonalPlan(monthKey, input.getText().toString());
                dialog.dismiss();
                render(content);
            });
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                a.settings.setHomeSeasonalPlan(monthKey, "");
                dialog.dismiss();
                render(content);
            });
        });
        dialog.show();
    }

    /** Compact, role-filtered shortcuts shown above the greeting without changing saved Home modules. */
    private void addTopQuickActions(LinearLayout content) {
        View shortcuts = topQuickActions();
        if (shortcuts == null) return;
        content.addView(shortcuts, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private View topQuickActions() {
        String[] targets = {"sales", "buy", "reports", "customers"};
        String[] labels = {"فروش", "خرید", "گزارش‌ها", "مشتریان"};
        int[] icons = {R.drawable.mi_point_of_sale, R.drawable.mi_shopping_cart,
                R.drawable.mi_bar_chart, R.drawable.mi_group};
        List<Integer> allowed = new ArrayList<>();
        for (int i = 0; i < targets.length; i++)
            if (RoleStore.allowed(a, targets[i])) allowed.add(i);
        if (allowed.isEmpty()) return null;

        LinearLayout card = homeCard(Theme.SECTION_QUICK_ACTIONS);
        card.setTag("home-quick-actions");
        card.setPadding(Theme.dp(12), Theme.dp(8), Theme.dp(12), Theme.dp(8));
        card.addView(boardHead("دسترسی سریع", null, R.drawable.mi_bolt,
                null, Theme.SECTION_QUICK_ACTIONS), a.kit.lp(-1, -2));
        TextView summary = a.kit.text(
                HomeNumberFormatter.integer(allowed.size()) + " مقصد مجاز برای نقش شما",
                10f, Theme.dashboardCardMutedInk(Theme.SECTION_QUICK_ACTIONS), false);
        LinearLayout.LayoutParams summaryParams = a.kit.lp(-1, -2);
        summaryParams.topMargin = Theme.dp(3);
        card.addView(summary, summaryParams);

        LinearLayout row = a.kit.h();
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = a.kit.lp(-1, -2);
        rowParams.topMargin = Theme.dp(5);
        for (int index : allowed)
            row.addView(topQuickAction(labels[index], targets[index], icons[index]), a.kit.wlp(1f));
        card.addView(row, rowParams);
        return card;
    }

    private View topQuickAction(String label, String screenId, int iconVector) {
        LinearLayout item = a.kit.v();
        item.setGravity(Gravity.CENTER);
        item.setMinimumWidth(Theme.dp(48));
        item.setMinimumHeight(Theme.dp(56));
        item.setPadding(Theme.dp(2), Theme.dp(3), Theme.dp(2), Theme.dp(3));
        item.setTag("home-quick-action-" + screenId);
        item.setContentDescription(label + "؛ باز کردن بخش " + label);

        ImageView symbol = dashboardArt(iconVector, dashboardAccent(iconVector));
        item.addView(symbol, a.kit.lp(Theme.dp(28), Theme.dp(28)));
        TextView title = a.kit.text(label, 10.5f, Theme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams titleParams = a.kit.lp(-1, -2);
        titleParams.topMargin = Theme.dp(3);
        item.addView(title, titleParams);
        Theme.pressable(item);
        item.setOnClickListener(v -> a.nav(screenId));
        return item;
    }

    private void secQuickActions(LinearLayout content) {
        final int accent = Theme.SECTION_QUICK_ACTIONS;
        String[] targets = {"sales", "search", "customers", "reports"};
        String[] labels = {"فروش‌ها", "جستجو", "مشتری‌ها", "گزارش‌ها"};
        int[] icons = {R.drawable.mi_sell, R.drawable.mi_search,
                R.drawable.mi_person, R.drawable.mi_bar_chart};
        List<Integer> allowed = new ArrayList<>();
        for (int i = 0; i < targets.length; i++)
            if (RoleStore.allowed(a, targets[i])) allowed.add(i);

        LinearLayout card = homeCard(accent);
        card.setTag("home-quick-actions-section");
        final List<View> actionRows = new ArrayList<>();
        final TextView[] toggleLabel = new TextView[1];
        View.OnClickListener toggleAction = allowed.size() > 2 ? v -> {
            quickActionsExpanded = !quickActionsExpanded;
            for (int i = 0; i < actionRows.size(); i++)
                actionRows.get(i).setVisibility(quickActionsExpanded || i == 0
                        ? View.VISIBLE : View.GONE);
            if (toggleLabel[0] != null) {
                toggleLabel[0].setText(quickActionsExpanded ? "کمتر" : "همه");
                toggleLabel[0].setContentDescription(quickActionsExpanded
                        ? "نمایش کمتر از میان‌برهای سریع"
                        : "نمایش همهٔ میان‌برهای سریع");
            }
        } : null;
        View toggle = toggleAction == null ? null : dashboardHeaderAction(
                quickActionsExpanded ? "کمتر" : "همه", accent,
                quickActionsExpanded ? "نمایش کمتر از میان‌برهای سریع"
                        : "نمایش همهٔ میان‌برهای سریع", toggleAction);
        View header = boardHead("میان‌برهای سریع", null, R.drawable.mi_bolt, toggle, accent);
        if (allowed.size() > 2 && header instanceof ViewGroup) {
            ViewGroup headerRow = (ViewGroup) ((ViewGroup) header).getChildAt(0);
            View possibleToggle = headerRow.getChildAt(headerRow.getChildCount() - 1);
            if (possibleToggle instanceof TextView) {
                toggleLabel[0] = (TextView) possibleToggle;
                toggleLabel[0].setContentDescription(quickActionsExpanded
                        ? "نمایش کمتر از میان‌برهای سریع"
                        : "نمایش همهٔ میان‌برهای سریع");
            }
        }
        card.addView(header, a.kit.lp(-1, -2));
        TextView summary = a.kit.text(
                HomeNumberFormatter.integer(allowed.size()) + " مقصد در دسترس نقش شما",
                10f, Theme.dashboardCardMutedInk(accent), false);
        LinearLayout.LayoutParams summaryParams = a.kit.lp(-1, -2);
        summaryParams.topMargin = Theme.dp(3);
        card.addView(summary, summaryParams);
        if (allowed.isEmpty()) {
            card.addView(a.kit.emptyRow("برای نقش فعلی مقصد میان‌بر مجازی وجود ندارد."),
                    a.kit.lp(-1, -2));
        } else {
            int rowCount = (allowed.size() + 1) / 2;
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                LinearLayout row = a.kit.h();
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setVisibility(quickActionsExpanded || rowIndex == 0 ? View.VISIBLE : View.GONE);
                LinearLayout.LayoutParams rowParams = a.kit.lp(-1, -2);
                rowParams.topMargin = Theme.dp(4);
                int first = allowed.get(rowIndex * 2);
                row.addView(quickActionNode(labels[first], targets[first], icons[first]),
                        a.kit.wlp(1f));
                int secondPosition = rowIndex * 2 + 1;
                if (secondPosition < allowed.size()) {
                    row.addView(a.kit.space(8));
                    int second = allowed.get(secondPosition);
                    row.addView(quickActionNode(labels[second], targets[second], icons[second]),
                            a.kit.wlp(1f));
                } else {
                    row.addView(new View(a), a.kit.wlp(1f));
                }
                actionRows.add(row);
                card.addView(row, rowParams);
            }
        }
        a.kit.addCard(content, card);
    }

    private View quickActionNode(String label, String screenId, int icon) {
        LinearLayout item = a.kit.h();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        item.setMinimumHeight(Theme.dp(48));
        item.setPadding(Theme.dp(6), Theme.dp(5), Theme.dp(6), Theme.dp(5));
        ImageView symbol = dashboardArt(icon, dashboardAccent(icon));
        item.addView(symbol, a.kit.lp(Theme.dp(28), Theme.dp(28)));
        item.addView(a.kit.space(7));
        TextView title = a.kit.text(label, 11f, Theme.TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(title, a.kit.wlp(1f));
        item.setContentDescription(label + "؛ بازکردن صفحهٔ " + label);
        item.setFocusable(true);
        Theme.pressable(item);
        item.setOnClickListener(v -> a.nav(screenId));
        return item;
    }

    private void secSalesForecast(LinearLayout content, Data d) {
        final int accent = Theme.SECTION_SALES_FORECAST;
        SalesForecastEngine.Result forecast = d == null || d.salesSnapshot == null
                ? SalesForecastEngine.forecast(null) : SalesForecastEngine.forecast(d.salesSnapshot);
        LinearLayout card = homeCard(accent);
        card.addView(boardHead("پیش‌بینی فروش هفت‌روزه",
                "روزهای تقویمی آینده • روزهای نزدیک‌تر سمت راست",
                R.drawable.mi_bar_chart, null, accent), a.kit.lp(-1, -2));
        if (!forecast.available) {
            card.addView(a.kit.emptyRow("برای پیش‌بینی، دست‌کم دو مشاهده از هر روز هفته در روند فروش لازم است."),
                    a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.text("برآورد نقطه‌ای مجموع هفت روز", 10.5f, Theme.MUTED, false),
                    a.kit.lp(-1, -2));
            card.addView(a.kit.amountLine(HomeNumberFormatter.money(forecast.expectedTotalRials),
                    16f, Gravity.START), a.kit.lp(-1, -2));
            card.addView(a.kit.gap(3));
            card.addView(a.kit.text("دامنهٔ نوسان تجربی مجموع: "
                            + HomeNumberFormatter.money(forecast.lowerTotalRials) + " تا "
                            + HomeNumberFormatter.money(forecast.upperTotalRials),
                    10.5f, Theme.TEXT, false), a.kit.lp(-1, -2));
            HomeForecastChart chart = new HomeForecastChart(a);
            chart.setData(forecast.days, accent);
            card.addView(chart, new LinearLayout.LayoutParams(-1, Theme.dp(220)));
            card.addView(a.kit.hint("نوار رنگی میانگین روزهای هم‌نام ± انحراف معیار نمونه در چهار هفتهٔ گذشته است؛ جمعه‌ها از دادهٔ همان روز محاسبه می‌شوند و تعطیلات رسمی استنباط نمی‌شوند."),
                    a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    private void secGrossProfit(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "closeout")) return;
        LinearLayout card = homeCard(Theme.SECTION_GROSS_PROFIT);
        card.addView(boardHead("سود ناخالص", "ماه جاری تا " + snapshotDay(d) + " • برآوردی",
                R.drawable.mi_profit, null), a.kit.lp(-1, -2));
        if (d == null || !d.grossProfitAvailable || d.grossProfitDaily == null) {
            card.addView(a.kit.emptyRow("مبنای بهای خرید برای برآورد سود ناخالص در دسترس نیست."),
                    a.kit.lp(-1, -2));
        } else if (d.grossProfitDaily.isEmpty()) {
            card.addView(a.kit.emptyRow("برای ماه جاری فروش قابل محاسبه‌ای ثبت نشده است."),
                    a.kit.lp(-1, -2));
        } else {
            double sales = sumField(d.grossProfitDaily, "sales");
            double cogs = sumField(d.grossProfitDaily, "cogs");
            double profit = sumField(d.grossProfitDaily, "profit");
            double todayProfit = grossProfitOnDay(d, snapshotDay(d));
            card.addView(statRow(R.drawable.mi_sell, "فروش ماه تا امروز",
                    HomeNumberFormatter.money(sales), Theme.SECTION_GROSS_PROFIT), a.kit.lp(-1, -2));
            card.addView(a.kit.gap(6));
            card.addView(statRow(R.drawable.mi_shopping_cart, "بهای تمام‌شدهٔ برآوردشده",
                    HomeNumberFormatter.money(cogs), Theme.SECTION_GROSS_PROFIT), a.kit.lp(-1, -2));
            card.addView(a.kit.gap(6));
            card.addView(statRow(R.drawable.mi_profit, "سود ناخالص ماه تا امروز",
                    HomeNumberFormatter.money(profit), Theme.SECTION_GROSS_PROFIT), a.kit.lp(-1, -2));
            card.addView(a.kit.gap(6));
            card.addView(statRow(R.drawable.mi_event, "سود ناخالص امروز",
                    HomeNumberFormatter.money(todayProfit), Theme.SECTION_GROSS_PROFIT),
                    a.kit.lp(-1, -2));
            card.addView(a.kit.hint("این برآورد از مبلغ فروش و بهای خرید ذخیره‌شده ساخته می‌شود؛ هزینه‌های عملیاتی، مالی و مالیاتی را ندارد. در کوئری فعلی، بهای خریدِ خالی صفر محسوب می‌شود؛ پس با قیمت خرید ناقص، سود کم‌اتکاست."),
                    a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    private void secMonthlySalesGoal(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "sales")) return;
        final String day = snapshotDay(d);
        final String monthStart = Jalali.monthStart(day);
        final String monthKey = monthStart != null && monthStart.length() >= 7
                ? monthStart.substring(0, 7) : "";
        final String periodLabel = monthLabel(monthStart);
        Double parsedTarget = parseLocalRials(a.settings.homeSalesGoalRials(monthKey));
        final Double target = parsedTarget != null && parsedTarget > 0d ? parsedTarget : null;
        SalesSnapshot snapshot = d == null ? null : d.salesSnapshot;
        boolean salesAvailable = snapshot != null && snapshot.isAvailable();

        LinearLayout card = homeCard(Theme.SECTION_SALES_GOAL);
        card.addView(boardHead("هدف فروش ماهانه", periodLabel + " • تنظیم محلی روی این دستگاه",
                R.drawable.mi_track_changes, null), a.kit.lp(-1, -2));

        if (target == null) {
            card.addView(a.kit.emptyRow("هدف فروش این ماه هنوز ثبت نشده است."),
                    a.kit.lp(-1, -2));
        } else if (!salesAvailable) {
            card.addView(a.kit.emptyRow("فروش ماه تا امروز در دادهٔ فعلی در دسترس نیست."),
                    a.kit.lp(-1, -2));
        } else {
            int elapsedDays = jalaliDayNumber(day);
            int monthDays = jalaliDaysInMonth(monthStart);
            double actual = snapshot.monthToDateRials(monthKey, elapsedDays);
            MonthlyGoalEngine.Result result = MonthlyGoalEngine.evaluate(
                    actual, target, elapsedDays, monthDays);
            if (!result.available) {
                card.addView(a.kit.emptyRow("برای این ماه، هدف یا بازهٔ روزهای تقویمی معتبر نیست."),
                        a.kit.lp(-1, -2));
            } else {
                FrameLayout gaugeFrame = new FrameLayout(a);
                gaugeFrame.setContentDescription("هدف فروش " + periodLabel + "، فروش تا امروز "
                        + HomeNumberFormatter.money(result.salesRials) + "، هدف "
                        + HomeNumberFormatter.money(result.targetRials) + "، تحقق "
                        + HomeNumberFormatter.percent(result.achievementPercent) + "، برآورد پایان ماه "
                        + HomeNumberFormatter.money(result.projectedMonthRials));
                gaugeFrame.setFocusable(true);
                HomeGoalGauge ring = new HomeGoalGauge(a);
                ring.setProgress(result.gaugePercent, Theme.SECTION_SALES_GOAL);
                FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(
                        Theme.dp(164), Theme.dp(164), Gravity.CENTER);
                gaugeFrame.addView(ring, ringParams);

                LinearLayout center = a.kit.v();
                center.setGravity(Gravity.CENTER);
                center.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                TextView percent = a.kit.text(HomeNumberFormatter.percent(result.achievementPercent),
                        19f, Theme.TEXT, true);
                percent.setGravity(Gravity.CENTER);
                TextView caption = a.kit.text("تحقق هدف", 10f, Theme.MUTED, false);
                caption.setGravity(Gravity.CENTER);
                center.addView(percent, a.kit.lp(-1, -2));
                center.addView(caption, a.kit.lp(-1, -2));
                FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                        -2, -2, Gravity.CENTER);
                gaugeFrame.addView(center, centerParams);
                card.addView(gaugeFrame, a.kit.lp(-1, Theme.dp(176)));

                card.addView(a.kit.kv("فروش ماه تا روز " + HomeNumberFormatter.integer(elapsedDays),
                        HomeNumberFormatter.money(result.salesRials), Theme.TEXT), a.kit.lp(-1, -2));
                card.addView(a.kit.kv("هدف ثبت‌شدهٔ ماه", HomeNumberFormatter.money(result.targetRials),
                        Theme.TEXT), a.kit.lp(-1, -2));
                card.addView(a.kit.kv("برآورد پایان ماه با نرخ روز تقویمی",
                        HomeNumberFormatter.money(result.projectedMonthRials), Theme.TEXT),
                        a.kit.lp(-1, -2));
                card.addView(a.kit.hint("برآورد خطی است: فروش ماه تا امروز ÷ روزهای تقویمی گذشته × تعداد روزهای ماه؛ جمعه و تعطیلات رسمی جداگانه از محاسبه کم نشده‌اند."),
                        a.kit.lp(-1, -2));
            }
        }

        card.addView(a.kit.btnGhost(target == null ? "ثبت هدف این ماه" : "ویرایش هدف این ماه",
                Theme.SECTION_SALES_GOAL, v -> showMonthlySalesGoalDialog(content, monthKey, target)),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private int jalaliDayNumber(String day) {
        String normalized = Jalali.disp(day);
        if (normalized.length() < 10) return 0;
        try { return Integer.parseInt(normalized.substring(8, 10)); }
        catch (Exception invalid) { return 0; }
    }

    private int jalaliDaysInMonth(String monthStart) {
        String normalized = Jalali.disp(monthStart);
        if (normalized.length() < 7) return 0;
        try {
            return Jalali.daysInMonth(Integer.parseInt(normalized.substring(0, 4)),
                    Integer.parseInt(normalized.substring(5, 7)));
        } catch (Exception invalid) {
            return 0;
        }
    }

    private String goalRialsFromDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String normalized = ir.meelano.manager.core.Money.en(raw).replace(",", "")
                .replace("٬", "").replace('٫', '.').trim();
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(normalized);
            if (amount.signum() <= 0) return null;
            if (ir.meelano.manager.core.Money.isToman()) amount = amount.multiply(java.math.BigDecimal.TEN);
            return amount.stripTrailingZeros().toPlainString();
        } catch (Exception invalid) {
            return null;
        }
    }

    private void showMonthlySalesGoalDialog(LinearLayout content, String monthKey, Double currentRials) {
        LinearLayout form = a.kit.v();
        form.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(8));
        form.addView(a.kit.hint("هدف فقط در تنظیمات همین دستگاه و برای این ماه ذخیره می‌شود؛ هیچ رکوردی در دیتابیس فروشگاه نوشته نمی‌شود."),
                a.kit.lp(-1, -2));
        String current = "";
        if (currentRials != null) {
            current = String.format(Locale.US, "%.1f",
                    ir.meelano.manager.core.Money.toUnit(currentRials));
            if (current.endsWith(".0")) current = current.substring(0, current.length() - 2);
        }
        android.widget.EditText input = a.kit.edit("مبلغ هدف به "
                + ir.meelano.manager.core.Money.unitName(), current);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setContentDescription("مبلغ هدف ماهانه به "
                + ir.meelano.manager.core.Money.unitName());
        form.addView(input, a.kit.lp(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle("هدف فروش ماهانه")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setNeutralButton("پاک‌کردن هدف", null)
                .setPositiveButton("ذخیره", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String rials = goalRialsFromDisplay(input.getText().toString());
                if (rials == null) {
                    input.setError("مبلغ معتبر و بزرگ‌تر از صفر وارد کنید");
                    return;
                }
                a.settings.setHomeSalesGoalRials(monthKey, rials);
                dialog.dismiss();
                render(content);
            });
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                a.settings.setHomeSalesGoalRials(monthKey, "");
                dialog.dismiss();
                render(content);
            });
        });
        dialog.show();
    }

    private void secHomeCloseout(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "closeout")) return;
        final String day = snapshotDay(d);
        LinearLayout card = homeCard(Theme.SECTION_CLOSEOUT);
        card.setTag("home-closeout-card");
        Button entry = dashboardHeaderAction("ثبت صندوق", Theme.SECTION_CLOSEOUT,
                "ثبت ماندهٔ آغاز و شمارش پایان صندوق؛ فقط روی این دستگاه",
                v -> showCloseoutCashDialog(content, d, day));
        card.addView(boardHead("بستن روز", "خلاصهٔ عملکرد و تطبیق صندوق • " + day,
                R.drawable.mi_point_of_sale, entry, Theme.SECTION_CLOSEOUT), a.kit.lp(-1, -2));

        boolean salesAvailable = d.salesSnapshot != null && d.salesSnapshot.isAvailable()
                && RoleStore.allowed(a, "sales");
        String salesNote = salesAvailable
                ? HomeNumberFormatter.integer(d.salesSnapshot.documentsOn(day)) + " فاکتور"
                : "داده در دسترس نیست";
        String receiveNote = d.inDay == null ? "داده در دسترس نیست"
                : HomeNumberFormatter.integer(d.inDay.l("count")) + " قبض";
        String paymentNote = d.outDay == null ? "داده در دسترس نیست"
                : HomeNumberFormatter.integer(d.outDay.l("count")) + " قبض";
        long returnDocs = sumLongField(d.returnsToday, "docs");
        String returnNote = d.returnsTodayAvailable
                ? HomeNumberFormatter.integer(returnDocs) + " سند" : "داده در دسترس نیست";
        String profitNote = d.grossProfitAvailable ? "برآورد بر پایهٔ بهای ثبت‌شده"
                : "مبنای بهای تمام‌شده موجود نیست";
        String variance = closeoutVarianceLabel(d, day);
        String varianceDisplay = compactCloseoutVariance(variance);
        String varianceHint = closeoutVarianceHint(variance);

        List<Mini> metrics = new ArrayList<>();
        metrics.add(new Mini(R.drawable.mi_sell, "فروش روز",
                salesAvailable ? HomeNumberFormatter.money(d.salesSnapshot.salesRialsOn(day)) : "—",
                salesNote, Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_receipt_long, "دریافت ثبت‌شده",
                d.inDay == null ? "—" : HomeNumberFormatter.money(d.inDay.d("total")),
                receiveNote, Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_payments, "پرداخت ثبت‌شده",
                d.outDay == null ? "—" : HomeNumberFormatter.money(d.outDay.d("total")),
                paymentNote, Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_assignment_return, "برگشتی امروز",
                d.returnsTodayAvailable ? HomeNumberFormatter.money(sumField(d.returnsToday, "total")) : "—",
                returnNote, Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_bar_chart, "سود ناخالص برآوردی",
                d.grossProfitAvailable ? HomeNumberFormatter.money(grossProfitOnDay(d, day)) : "—",
                profitNote, Theme.TEXT));
        metrics.add(new Mini(R.drawable.mi_point_of_sale, "اختلاف شمارش صندوق",
                varianceDisplay, varianceHint, Theme.TEXT));
        int metricColumns = ir.meelano.manager.ui.Ui.widthDp(a) >= 600 ? 3 : 2;
        card.addView(dashboardMetricGrid(metrics, metricColumns, Theme.SECTION_CLOSEOUT),
                a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        LinearLayout exports = a.kit.h();
        exports.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button share = a.kit.btnGhost("اشتراک PDF", Theme.SECTION_CLOSEOUT,
                v -> shareCloseoutReport(d, day, false));
        share.setTextSize(11f * Theme.fontScale());
        share.setContentDescription("اشتراک گزارش پایان روز به‌صورت PDF");
        Button print = a.kit.btnGhost("چاپ گزارش", Theme.SECTION_CLOSEOUT,
                v -> shareCloseoutReport(d, day, true));
        print.setTextSize(11f * Theme.fontScale());
        print.setContentDescription("چاپ گزارش پایان روز");
        exports.addView(share, a.kit.wlp(1f));
        exports.addView(a.kit.space(8));
        exports.addView(print, a.kit.wlp(1f));
        card.addView(exports, a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
        secFinancialReconciliation(content, d);
    }

    /**
     * Manager-only daily controls. Internal ledgers and a manager-entered bank statement stay
     * distinct; no inferred zero or direct sales-minus-receipts verdict is presented as fact.
     */
    private void secFinancialReconciliation(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "closeout")) return;
        final String day = snapshotDay(d);
        LinearLayout card = homeCard(Theme.SECTION_CLOSEOUT);
        card.setTag("home-financial-reconciliation");
        card.addView(boardHead("مغایرت‌گیری مالی", "کنترل‌های دفتر روزانهٔ آتیران • " + day
                        + " • نمایش به " + ir.meelano.manager.core.Money.unitName(),
                R.drawable.mi_fact_check, navChip("گزارش‌ها", "reports"), Theme.SECTION_CLOSEOUT),
                a.kit.lp(-1, -2));

        int[] summary = financialControlCounts(d, day);
        String headline;
        String status;
        int tone;
        if (summary[0] > 0) {
            headline = "برای رسیدگی: " + HomeNumberFormatter.integer(summary[0]) + " اختلاف ثبت‌شده";
            status = "نیازمند رسیدگی";
            tone = Theme.DANGER;
        } else if (summary[1] == 0 && summary[2] > 0) {
            headline = "در کنترل‌های تکمیل‌شده اختلافی پیدا نشد";
            status = "کنترل‌شده";
            tone = Theme.SUCCESS;
        } else if (summary[2] == 0) {
            headline = "برای نتیجه‌گیری دادهٔ کافی در دسترس نیست";
            status = "نامشخص";
            tone = Theme.WARNING;
        } else {
            headline = "تطبیق ناقص است؛ ورودی‌های واقعی را تکمیل کنید";
            status = "ناقص";
            tone = Theme.WARNING;
        }
        card.addView(financialAuditLine("home-financial-audit-summary", headline,
                HomeNumberFormatter.integer(summary[2]) + " کنترل اجراشده • "
                        + HomeNumberFormatter.integer(summary[1]) + " مورد/منبع نیازمند تکمیل؛ "
                        + "کنترل ناقص به‌معنی نبود مغایرت نیست.", status, tone), a.kit.lp(-1, -2));

        String salesValue = d.salesSnapshot != null && d.salesSnapshot.isAvailable()
                ? HomeNumberFormatter.money(d.salesSnapshot.salesRialsOn(day)) : "دادهٔ فروش در دسترس نیست";
        String receiptValue = d.inDay != null ? HomeNumberFormatter.money(d.inDay.d("total"))
                : "دادهٔ دریافت در دسترس نیست";
        card.addView(financialAuditLine("home-sales-receipts-control", "فروش و دریافت • مقایسهٔ نیازمند پیوند فاکتور",
                "فروش روز: " + salesValue + " • دریافت ثبت‌شدهٔ روز: " + receiptValue
                        + ". دریافت امروز می‌تواند تسویهٔ فاکتورهای روزهای قبل باشد؛ تفاضل این دو به‌تنهایی مغایرت قطعی نیست.",
                "نیازمند تطبیق فاکتور", Theme.WARNING), a.kit.lp(-1, -2));

        card.addView(a.kit.text("کنترل جمع و تفکیک قبوض", 12f,
                Theme.dashboardAccentInk(Theme.SECTION_CLOSEOUT), true), a.kit.lp(-1, -2));
        card.addView(moneyBreakdownLine("home-receipt-breakdown", "ترکیب دریافت",
                d.cashReceipts, d.cashReceiptsAvailable), a.kit.lp(-1, -2));
        card.addView(moneyBreakdownLine("home-payment-breakdown", "ترکیب پرداخت",
                d.cashPayments, d.cashPaymentsAvailable), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("فرمول کنترل جمع: مبلغ ثبت‌شده − (نقد + کارت‌خوان + حواله + چک). این کنترل در سطح جمع روز است و جای بررسی ریز قبض‌ها را نمی‌گیرد؛ دادهٔ تفکیک‌نشده صفر فرض نمی‌شود."),
                a.kit.lp(-1, -2));

        card.addView(a.kit.text("تطبیق هر حساب بانکی با صورت‌حساب واقعی", 12f,
                Theme.dashboardAccentInk(Theme.SECTION_CLOSEOUT), true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("دفتر آتیران از گردش ثبت‌شدهٔ بانکی خوانده می‌شود؛ برنامه به بانک وصل نمی‌شود. از اپ بانکی خروجی CSV، TSV یا .xlsx بگیرید و همین‌جا انتخاب کنید؛ فایل خام و ریز تراکنش‌ها ذخیره یا ارسال نمی‌شوند. مقایسه پس از تأیید کامل‌بودن بازه انجام می‌شود."),
                a.kit.lp(-1, -2));
        Button importStatement = dashboardHeaderAction("ورود فایل صورت‌حساب", Theme.SECTION_CLOSEOUT,
                "انتخاب فایل خروجی بانک برای تحلیل محلی و تطبیق read-only با گردش آتیران",
                v -> showBankStatementImport(content, d));
        LinearLayout bankActions = a.kit.h();
        bankActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bankActions.addView(importStatement, a.kit.wlp(1f));
        card.addView(bankActions, a.kit.lp(-1, -2));
        List<Row> accounts = bankAccountsForHome(d);
        if (!d.bankTurnoverAvailable) {
            card.addView(financialAuditLine("home-bank-ledger-unavailable", "گردش بانکی روزانه",
                    "دادهٔ تاریخ‌دار و کامل برای دفتر بانک در دسترس نیست؛ مقایسه انجام نشد.",
                    "نامشخص", Theme.WARNING), a.kit.lp(-1, -2));
        } else if (accounts.isEmpty()) {
            card.addView(financialAuditLine("home-bank-accounts-empty", "حساب بانکی",
                    d.bankAccountsAvailable ? "حسابی برای این روز در فهرست بانک آتیران پیدا نشد."
                            : "فهرست حساب‌های بانک در دسترس نیست.", "نامشخص", Theme.WARNING),
                    a.kit.lp(-1, -2));
        } else {
            for (Row account : accounts)
                card.addView(bankReconciliationRow(content, d, day, account), a.kit.lp(-1, -2));
        }

        card.addView(a.kit.text("استثناهای قابل رسیدگی", 12f,
                Theme.dashboardAccentInk(Theme.SECTION_CLOSEOUT), true), a.kit.lp(-1, -2));
        addExceptionRows(card, d);

        String cashLabel = closeoutVarianceLabel(d, day);
        boolean cashEntered = d.cashReceiptsAvailable && d.cashPaymentsAvailable
                && parseLocalRials(a.settings.closeoutCashOpening(day)) != null
                && parseLocalRials(a.settings.closeoutCashCounted(day)) != null;
        card.addView(financialAuditLine("home-cash-count-control", "تطبیق شمارش صندوق",
                cashLabel + " • فرمول: ماندهٔ آغاز + دریافت نقد − پرداخت نقد، در برابر شمارش پایان.",
                cashEntered ? (cashLabel.startsWith("بدون اختلاف") ? "مطابق" : "رسیدگی") : "ثبت/داده ناقص",
                cashEntered && cashLabel.startsWith("بدون اختلاف") ? Theme.SUCCESS
                        : cashEntered ? Theme.DANGER : Theme.WARNING), a.kit.lp(-1, -2));
        Button cashEntry = dashboardHeaderAction("ثبت شمارش صندوق", Theme.SECTION_CLOSEOUT,
                "ثبت ماندهٔ آغاز و شمارش پایان صندوق برای " + day + "؛ فقط روی همین دستگاه",
                v -> showCloseoutCashDialog(content, d, day));
        LinearLayout actions = a.kit.h();
        actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        actions.addView(cashEntry, a.kit.wlp(1f));
        card.addView(actions, a.kit.lp(-1, -2));

        card.addView(a.kit.text("اسناد برگشتی امروز", 12f,
                Theme.dashboardAccentInk(Theme.SECTION_CLOSEOUT), true), a.kit.lp(-1, -2));
        if (!d.returnsTodayAvailable) {
            card.addView(a.kit.hint("گزارش اسناد برگشتی در دسترس نیست؛ صفر تلقی نشده است."), a.kit.lp(-1, -2));
        } else if (d.returnsToday == null || d.returnsToday.isEmpty()) {
            card.addView(a.kit.hint("در منبع اسناد برگشتی فعال، ردیفی برای این روز برنگشت."), a.kit.lp(-1, -2));
        } else {
            for (int i = 0; i < Math.min(8, d.returnsToday.size()); i++) {
                Row returned = d.returnsToday.get(i);
                card.addView(financialAuditLine("home-return-kind-" + i,
                        returned.s("label").isEmpty() ? "نوع سند برگشتی" : returned.s("label"),
                        HomeNumberFormatter.money(returned.d("total")) + " • "
                                + HomeNumberFormatter.integer(returned.l("docs")) + " سند",
                        "گزارش‌شده", Theme.SECTION_RETURNS), a.kit.lp(-1, -2));
            }
        }
        card.addView(a.kit.hint("برگشتی‌ها بر اساس نوع جدا نمایش داده شده‌اند و بدون تشخیص ماهیت مالی از فروش کم نشده‌اند؛ ابطال فاکتور و تسویهٔ مطالبات نیز باید در گزارش همان سند کنترل شود."),
                a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private int[] financialControlCounts(Data d, String day) {
        int issues = 0;
        int incomplete = 0;
        int checked = 0;
        MoneyBreakdown receipts = breakdown(d.cashReceipts, d.cashReceiptsAvailable);
        MoneyBreakdown payments = breakdown(d.cashPayments, d.cashPaymentsAvailable);
        if (receipts.available) { checked++; if (!receipts.matches()) issues++; } else incomplete++;
        if (payments.available) { checked++; if (!payments.matches()) issues++; } else incomplete++;
        // Daily sales/receipts are not linked by settled invoice, and voided invoice effects
        // are not exposed as a reliable daily aggregate by the current read-only schema.
        incomplete += 2;
        if (d.returnsTodayAvailable) checked++; else incomplete++;

        if (d.overpaidVouchersAvailable) {
            checked++;
            issues += d.overpaidVouchers == null ? 0 : d.overpaidVouchers.size();
            if (d.overpaidVouchers != null && d.overpaidVouchers.size() >= 5) incomplete++;
        } else incomplete++;
        if (d.unbalancedJournalsAvailable) {
            checked++;
            issues += d.unbalancedJournals == null ? 0 : d.unbalancedJournals.size();
            if (d.unbalancedJournals != null && d.unbalancedJournals.size() >= 5) incomplete++;
        } else incomplete++;

        if (d.bankTurnoverAvailable && (d.bankAccountsAvailable || !d.bankTurnover.isEmpty())) {
            List<Row> accounts = bankAccountsForHome(d);
            if (accounts.isEmpty()) incomplete++;
            for (Row account : accounts) {
                Double in = parseLocalRials(a.settings.homeBankStatementInRials(day, account.s("id")));
                Double out = parseLocalRials(a.settings.homeBankStatementOutRials(day, account.s("id")));
                if (in == null || out == null) { incomplete++; continue; }
                Row ledger = bankMovement(d, account.s("id"));
                BankStatementReconciliation.Result result = BankStatementReconciliation.compare(true,
                        ledger == null ? 0 : ledger.d("in"), ledger == null ? 0 : ledger.d("out"),
                        true, in, out);
                checked++;
                if (!result.matches()) issues++;
            }
        } else incomplete++;

        Double opening = parseLocalRials(a.settings.closeoutCashOpening(day));
        Double counted = parseLocalRials(a.settings.closeoutCashCounted(day));
        if (d.cashReceiptsAvailable && d.cashPaymentsAvailable && opening != null && counted != null) {
            double variance = CashCloseMath.variance(opening, d.cashReceipts.d("cash"),
                    d.cashPayments.d("cash"), counted);
            if (Double.isFinite(variance)) { checked++; if (Math.abs(variance) > 0.5d) issues++; }
            else incomplete++;
        } else incomplete++;
        return new int[]{issues, incomplete, checked};
    }

    private MoneyBreakdown breakdown(Row row, boolean available) {
        if (row == null) return MoneyBreakdown.calculate(false, 0, 0, 0, 0, 0);
        return MoneyBreakdown.calculate(available, row.d("total"), row.d("cash"), row.d("pos"),
                row.d("havaleh"), row.d("cheque"));
    }

    private View moneyBreakdownLine(String tag, String title, Row row, boolean available) {
        MoneyBreakdown result = breakdown(row, available);
        if (!result.available) return financialAuditLine(tag, title,
                "تفکیک مبلغ در دادهٔ فروشگاه موجود نیست؛ صفر یا تطبیق فرض نشده است.",
                "نامشخص", Theme.WARNING);
        String details = "ثبت " + HomeNumberFormatter.money(result.bookedTotal)
                + " • اجزا " + HomeNumberFormatter.money(result.componentTotal);
        String state;
        int tone;
        if (result.matches()) {
            details += " • اختلاف جمعی صفر";
            state = "برابر";
            tone = Theme.SUCCESS;
        } else {
            details += " • " + (result.unexplained > 0 ? "کسری اجزا " : "اضافهٔ اجزا ")
                    + HomeNumberFormatter.money(Math.abs(result.unexplained));
            state = "رسیدگی";
            tone = Theme.DANGER;
        }
        return financialAuditLine(tag, title, details, state, tone);
    }

    private View financialAuditLine(String tag, String title, String detail, String status, int tone) {
        LinearLayout row = a.kit.v();
        row.setTag(tag);
        row.setBackground(Theme.dashboardMetric(Theme.SECTION_CLOSEOUT));
        row.setElevation(Theme.dp(1f));
        row.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        row.setPadding(Theme.dp(12), Theme.dp(9), Theme.dp(12), Theme.dp(9));
        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = a.kit.text(title, 11.5f, Theme.dashboardAccentInk(Theme.SECTION_CLOSEOUT), true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(label, a.kit.wlp(1f));
        head.addView(statusChip(status, tone), a.kit.lp(-2, -2));
        row.addView(head, a.kit.lp(-1, -2));
        TextView copy = a.kit.text(detail, 10.5f, Theme.dashboardCardMutedInk(Theme.SECTION_CLOSEOUT), false);
        copy.setGravity(Gravity.START);
        copy.setMaxLines(4);
        copy.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams cp = a.kit.lp(-1, -2);
        cp.topMargin = Theme.dp(4);
        row.addView(copy, cp);
        row.setContentDescription(title + "؛ " + status + "؛ " + detail);
        return row;
    }

    private List<Row> bankAccountsForHome(Data d) {
        List<Row> accounts = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (d.bankAccounts != null) for (Row source : d.bankAccounts) {
            if (source == null) continue;
            String id = source.s("id").trim();
            if (id.isEmpty() || !seen.add(id)) continue;
            Row account = new Row();
            account.put("id", id);
            account.put("name", source.s("name"));
            accounts.add(account);
        }
        if (d.bankTurnover != null) for (Row movement : d.bankTurnover) {
            if (movement == null) continue;
            String id = movement.s("bankId").trim();
            if (id.isEmpty() || !seen.add(id)) continue;
            Row account = new Row();
            account.put("id", id);
            account.put("name", movement.s("bank"));
            accounts.add(account);
        }
        return accounts;
    }

    private Row bankMovement(Data d, String bankId) {
        if (d == null || d.bankTurnover == null || bankId == null) return null;
        for (Row row : d.bankTurnover)
            if (row != null && bankId.equals(row.s("bankId").trim())) return row;
        return null;
    }

    private View bankReconciliationRow(LinearLayout content, Data d, String day, Row account) {
        String id = account.s("id").trim();
        String name = account.s("name").trim();
        if (name.isEmpty()) name = "حساب بانکی";
        String title = name + (id.isEmpty() ? "" : " • کد آتیران " + id);
        Row ledger = bankMovement(d, id);
        double ledgerIn = ledger == null ? 0 : ledger.d("in");
        double ledgerOut = ledger == null ? 0 : ledger.d("out");
        Double statementIn = parseLocalRials(a.settings.homeBankStatementInRials(day, id));
        Double statementOut = parseLocalRials(a.settings.homeBankStatementOutRials(day, id));
        boolean statementReady = statementIn != null && statementOut != null;
        BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                d.bankTurnoverAvailable, ledgerIn, ledgerOut, statementReady,
                statementIn == null ? 0 : statementIn, statementOut == null ? 0 : statementOut);
        String badge;
        int tone;
        switch (result.state) {
            case MATCH: badge = "مطابق"; tone = Theme.SUCCESS; break;
            case VARIANCE: badge = "مغایرت"; tone = Theme.DANGER; break;
            case NEEDS_STATEMENT: badge = "صورت‌حساب ثبت نشده"; tone = Theme.WARNING; break;
            case LEDGER_UNAVAILABLE: badge = "دفتر ناموجود"; tone = Theme.WARNING; break;
            default: badge = "مبلغ نامعتبر"; tone = Theme.DANGER; break;
        }
        String detail = d.bankTurnoverAvailable
                ? "دفتر آتیران: واریز " + HomeNumberFormatter.money(ledgerIn)
                    + " • برداشت " + HomeNumberFormatter.money(ledgerOut)
                : "دفتر آتیران: گردش تاریخ‌دار در دسترس نیست";
        if (statementReady) {
            detail += "\nصورت‌حساب: واریز " + HomeNumberFormatter.money(statementIn)
                    + " • برداشت " + HomeNumberFormatter.money(statementOut);
            if (result.comparable()) detail += "\n" + bankVariancePiece("واریز", result.inflowVarianceRials)
                    + " • " + bankVariancePiece("برداشت", result.outflowVarianceRials);
            String source = a.settings.homeBankStatementSource(day, id);
            if (!source.isEmpty()) detail += "\nمنبع: " + source;
            long updated = a.settings.homeBankStatementUpdatedAt(day, id);
            if (updated > 0) {
                String time = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new java.util.Date(updated));
                detail += " • به‌روزرسانی محلی " + ir.meelano.manager.core.Money.fa(time);
            }
        } else {
            detail += "\nصورت‌حساب واقعی: ثبت نشده؛ برای ورود ارقام واقعی لمس کنید.";
        }
        LinearLayout row = (LinearLayout) financialAuditLine("home-bank-statement-entry", title,
                detail, badge, tone);
        if (row.getChildCount() > 1 && row.getChildAt(1) instanceof TextView) {
            TextView detailView = (TextView) row.getChildAt(1);
            detailView.setMaxLines(6);
            detailView.setEllipsize(null);
        }
        row.setMinimumHeight(Theme.dp(64));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + "؛ " + badge + "؛ " + detail + "؛ ویرایش صورت‌حساب روزانه");
        Theme.pressable(row);
        row.setOnClickListener(v -> showHomeBankStatementEditor(content, day, account));
        return row;
    }

    private String bankVariancePiece(String direction, double variance) {
        if (Math.abs(variance) <= BankStatementReconciliation.TOLERANCE_RIALS)
            return direction + " برابر";
        String relation = variance > 0 ? "صورت‌حساب بیش از دفتر" : "دفتر بیش از صورت‌حساب";
        return direction + "، " + relation + " " + HomeNumberFormatter.money(Math.abs(variance));
    }

    private void showHomeBankStatementEditor(LinearLayout content, String day, Row account) {
        String bankId = account.s("id").trim();
        if (bankId.isEmpty()) {
            a.kit.toast("شناسهٔ حساب بانکی برای ذخیرهٔ محلی در دسترس نیست");
            return;
        }
        String bankName = account.s("name").trim();
        if (bankName.isEmpty()) bankName = "حساب بانکی";
        LinearLayout form = a.kit.v();
        form.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(8));
        form.addView(a.kit.hint("جمع واقعی واریز و برداشت صورت‌حساب را برای همین روز به ریال وارد کنید. هر دو مبلغ لازم‌اند؛ صفر را فقط وقتی واقعاً صفر است وارد کنید. داده فقط روی این دستگاه ذخیره می‌شود و به آتیران فرستاده نمی‌شود."), a.kit.lp(-1, -2));
        android.widget.EditText inflow = a.kit.edit("واریز صورت‌حساب به ریال",
                a.settings.homeBankStatementInRials(day, bankId));
        android.widget.EditText outflow = a.kit.edit("برداشت صورت‌حساب به ریال",
                a.settings.homeBankStatementOutRials(day, bankId));
        int numberInput = android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
        inflow.setInputType(numberInput);
        outflow.setInputType(numberInput);
        inflow.setContentDescription("جمع واریز واقعی صورت‌حساب به ریال برای " + bankName + " در " + day);
        outflow.setContentDescription("جمع برداشت واقعی صورت‌حساب به ریال برای " + bankName + " در " + day);
        form.addView(inflow, a.kit.lp(-1, -2));
        form.addView(outflow, a.kit.lp(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle("صورت‌حساب واقعی • " + bankName + " • " + day)
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setNeutralButton("پاک‌کردن ثبت", null)
                .setPositiveButton("ذخیره روی دستگاه", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (parseLocalRials(inflow.getText().toString()) == null
                        || parseLocalRials(outflow.getText().toString()) == null) {
                    a.kit.toast("واریز و برداشت را جداگانه، با عدد صفر یا بیشتر، وارد کنید");
                    return;
                }
                if (!a.settings.setHomeBankStatementRials(day, bankId,
                        inflow.getText().toString(), outflow.getText().toString())) {
                    a.kit.toast("مبلغ صورت‌حساب معتبر نیست؛ مبلغ را به ریال بررسی کنید");
                    return;
                }
                dialog.dismiss();
                render(content);
            });
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                a.settings.clearHomeBankStatement(day, bankId);
                dialog.dismiss();
                render(content);
            });
        });
        dialog.show();
    }

    private void showBankStatementImport(LinearLayout content, Data d) {
        if (!RoleStore.allowed(a, "closeout")) {
            a.kit.toast("این ابزار فقط برای نقش مدیر مجاز است");
            return;
        }
        List<Row> accounts = bankAccountsForHome(d);
        if (accounts.isEmpty()) {
            a.kit.toast("فهرست حساب‌های بانکی آتیران در دسترس نیست؛ ابتدا اتصال read-only را تازه کنید");
            return;
        }
        new android.app.AlertDialog.Builder(a)
                .setTitle("ورود آفلاین صورت‌حساب")
                .setMessage("از برنامهٔ بانک، خروجی CSV، TSV یا Excel (.xlsx) بگیرید. فایل در همین دستگاه خوانده می‌شود؛ هیچ اتصال بانکی یا ارسال فایل انجام نمی‌شود. برای مقایسه، بازه و کامل‌بودن صورت‌حساب را خودتان تأیید می‌کنید.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتخاب فایل", (dialog, which) -> {
                    final int token = a.renderGen();
                    a.startBankStatementPick(uri -> {
                        if (!bankImportScreenCurrent(token)) return;
                        readBankStatement(uri, content, d, token);
                    });
                })
                .show();
    }

    private boolean bankImportScreenCurrent(int token) {
        return !a.isFinishing() && a.renderGen() == token;
    }

    private void readBankStatement(Uri uri, LinearLayout content, Data data, int token) {
        if (uri == null) {
            a.kit.toast("فایلی انتخاب نشد");
            return;
        }
        android.app.AlertDialog progress = bankImportProgress("فایل فقط برای تحلیل محلی خوانده می‌شود…");
        progress.show();
        Thread worker = new Thread(() -> {
            BankStatementFile.Table table = null;
            String error = null;
            try {
                String displayName = bankStatementDisplayName(uri);
                InputStream stream = a.getContentResolver().openInputStream(uri);
                if (stream == null) throw new IOException("فایل انتخاب‌شده قابل‌خواندن نیست");
                table = BankStatementFile.read(stream, displayName);
            } catch (Exception failure) {
                String message = failure instanceof IOException ? failure.getMessage() : null;
                error = message == null || message.trim().isEmpty()
                        ? "فایل باز نشد؛ CSV، TSV یا .xlsx را دوباره انتخاب کنید" : message;
            }
            final BankStatementFile.Table result = table;
            final String failureText = error;
            a.runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (!bankImportScreenCurrent(token)) return;
                if (failureText != null) {
                    showBankImportMessage("خواندن فایل ممکن نشد", failureText);
                    return;
                }
                BankImportDraft draft = new BankImportDraft();
                draft.table = result;
                draft.mapping = BankStatementFile.Mapping.suggested(result.headers);
                draft.dashboardData = data;
                draft.content = content;
                draft.renderToken = token;
                BankStatementFile.DateRange range;
                try { range = BankStatementFile.suggestedRange(result, draft.mapping); }
                catch (IOException ignored) { range = null; }
                draft.fromDay = range != null && range.available() ? range.fromDay : Jalali.todayStr();
                draft.toDay = range != null && range.available() ? range.toDay : draft.fromDay;
                showBankStatementMapping(draft);
            });
        }, "bank-statement-local-read");
        worker.start();
    }

    /** Provider display name is used only for format detection and is never retained or logged. */
    private String bankStatementDisplayName(Uri uri) {
        String name = "";
        Cursor cursor = null;
        try {
            cursor = a.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) name = cursor.getString(column);
            }
        } catch (Exception ignored) {
            // The byte signature and the parser's content checks remain the primary format checks.
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Exception ignored) { }
        }
        String mime = "";
        try { mime = String.valueOf(a.getContentResolver().getType(uri)); }
        catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) name = "statement";
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".xls") && !lower.endsWith(".xlsx")) return name;
        if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")
                || lower.endsWith(".xlsx")) return name;
        if (mime.contains("spreadsheetml.sheet")) return name + ".xlsx";
        if (mime.equals("application/vnd.ms-excel")) return name + ".xls";
        return name;
    }

    private android.app.AlertDialog bankImportProgress(String message) {
        LinearLayout body = a.kit.h();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(Theme.dp(24), Theme.dp(18), Theme.dp(24), Theme.dp(18));
        ProgressBar progress = new ProgressBar(a);
        progress.setIndeterminate(true);
        body.addView(progress, new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        TextView label = a.kit.text(message, 12f, Theme.TEXT, false);
        label.setPadding(Theme.dp(14), 0, 0, 0);
        body.addView(label, a.kit.wlp(1f));
        return new android.app.AlertDialog.Builder(a).setView(body).setCancelable(false).create();
    }

    private void showBankImportMessage(String title, String message) {
        if (a.isFinishing()) return;
        new android.app.AlertDialog.Builder(a).setTitle(title)
                .setMessage(message == null ? "عملیات کامل نشد" : message)
                .setPositiveButton("باشه", null).show();
    }

    private void showBankStatementMapping(BankImportDraft draft) {
        if (draft == null || draft.table == null || !bankImportScreenCurrent(draft.renderToken)) return;
        if (draft.mapping == null) draft.mapping = BankStatementFile.Mapping.suggested(draft.table.headers);
        BankStatementFile.Mapping initial = draft.mapping;
        ScrollView scroll = new ScrollView(a);
        LinearLayout form = a.kit.v();
        form.setPadding(Theme.dp(20), Theme.dp(8), Theme.dp(20), Theme.dp(12));
        form.addView(a.kit.hint("ستون‌ها را بازبینی کنید. خروجی‌های بانکی یکسان نیستند؛ پیشنهادها را با سربرگ فایل تطبیق دهید. هیچ ردیف خصوصی به گزارش یا ذخیرهٔ محلی منتقل نمی‌شود."), a.kit.lp(-1, -2));

        Spinner dateColumn = bankColumnSpinner(form, "ستون تاریخ تراکنش", draft.table.headers, initial.date);
        Spinner creditColumn = bankColumnSpinner(form, "ستون واریز / بستانکار (در صورت وجود)", draft.table.headers, initial.credit);
        Spinner debitColumn = bankColumnSpinner(form, "ستون برداشت / بدهکار (در صورت وجود)", draft.table.headers, initial.debit);
        Spinner amountColumn = bankColumnSpinner(form, "ستون مبلغ تکی (در صورت نبود دو ستون بالا)", draft.table.headers, initial.amount);
        Spinner directionColumn = bankColumnSpinner(form, "ستون جهت تراکنش (واریز/برداشت)", draft.table.headers, initial.direction);
        Spinner balanceColumn = bankColumnSpinner(form, "ستون مانده (اختیاری؛ برای کنترل جمع استفاده نمی‌شود)", draft.table.headers, initial.balance);
        Spinner descriptionColumn = bankColumnSpinner(form, "ستون شرح (اختیاری؛ ذخیره نمی‌شود)", draft.table.headers, initial.description);
        Spinner referenceColumn = bankColumnSpinner(form, "ستون پیگیری (اختیاری؛ ذخیره نمی‌شود)", draft.table.headers, initial.reference);
        Spinner accountColumn = bankColumnSpinner(form, "ستون شماره حساب / شبا (اختیاری)", draft.table.headers, initial.account);
        android.widget.EditText accountFilter = a.kit.edit("مقدار دقیق حساب برای فیلتر (اختیاری)", initial.accountValue);
        accountFilter.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        accountFilter.setContentDescription("مقدار دقیق حساب یا شبا برای فیلتر محلی؛ این مقدار ذخیره نمی‌شود");
        form.addView(accountFilter, a.kit.lp(-1, -2));
        form.addView(a.kit.hint("اگر فایل چندحسابی است، ستون حساب و مقدار دقیق حساب موردنظر را وارد کنید تا ردیف‌های سایر حساب‌ها کنار گذاشته شوند. در غیر این صورت، پیش از ادامه تأیید می‌کنید که فایل فقط مربوط به حساب انتخابی است."), a.kit.lp(-1, -2));

        form.addView(a.kit.text("قالب تاریخ", 11.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String[] dateLabels = {"تشخیص خودکار", "شمسی: سال/ماه/روز", "میلادی: سال/ماه/روز", "میلادی: روز/ماه/سال", "شمارهٔ روز Excel"};
        Spinner dateFormat = new Spinner(a);
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, dateLabels);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateFormat.setAdapter(dateAdapter);
        dateFormat.setSelection(initial.dateFormat >= 0 && initial.dateFormat < dateLabels.length ? initial.dateFormat : 0);
        dateFormat.setContentDescription("قالب تاریخ صورت‌حساب بانکی");
        form.addView(dateFormat, a.kit.lp(-1, -2));
        form.addView(a.kit.text("قالب جداکنندهٔ مبلغ", 11.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String[] amountLabels = {"تشخیص خودکار (مثل ۱٬۲۳۴ یا ۱,۲۳۴.۵۰)",
                "ممیز نقطه (مثل ۱,۲۳۴.۵۰)", "ممیز ویرگول (مثل ۱.۲۳۴,۵۰)",
                "عدد صحیح؛ نقطه/ویرگول فقط جداکنندهٔ هزارگان"};
        Spinner amountFormat = new Spinner(a);
        ArrayAdapter<String> amountAdapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, amountLabels);
        amountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        amountFormat.setAdapter(amountAdapter);
        amountFormat.setSelection(initial.amountFormat >= 0 && initial.amountFormat < amountLabels.length
                ? initial.amountFormat : BankStatementFile.AMOUNT_AUTO);
        amountFormat.setContentDescription("قالب ممیز اعشاری و جداکنندهٔ هزارگان در مبلغ بانک");
        form.addView(amountFormat, a.kit.lp(-1, -2));

        form.addView(a.kit.text("واحد مبلغ خروجی بانک", 11.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        RadioGroup unit = new RadioGroup(a);
        unit.setOrientation(RadioGroup.VERTICAL);
        RadioButton rials = new RadioButton(a);
        rials.setId(View.generateViewId());
        rials.setText("ریال (بدون ضریب تبدیل)");
        rials.setTextColor(Theme.TEXT);
        RadioButton tomans = new RadioButton(a);
        tomans.setId(View.generateViewId());
        tomans.setText("تومان (تبدیل به ریال × ۱۰)");
        tomans.setTextColor(Theme.TEXT);
        unit.addView(rials, a.kit.lp(-1, -2));
        unit.addView(tomans, a.kit.lp(-1, -2));
        unit.check(initial.amountsInToman ? tomans.getId() : rials.getId());
        unit.setContentDescription("انتخاب واحد مبلغ در فایل بانک");
        form.addView(unit, a.kit.lp(-1, -2));
        CheckBox signedDirection = new CheckBox(a);
        signedDirection.setText("در فایل تک‌مبلغی، علامت مثبت/منفی جهت واریز/برداشت را تعیین می‌کند");
        signedDirection.setTextColor(Theme.TEXT);
        signedDirection.setChecked(initial.signedAmountDefinesDirection);
        signedDirection.setContentDescription("تأیید استفاده از علامت مبلغ به‌عنوان جهت تراکنش");
        form.addView(signedDirection, a.kit.lp(-1, -2));

        form.addView(a.kit.hint("بازه را دقیقاً برابر دورهٔ کامل فایل بانکی تنظیم کنید، حتی اگر روز آغاز یا پایان گردش نداشته باشد. روزهای خارج از این بازه کنار گذاشته می‌شوند."), a.kit.lp(-1, -2));
        android.widget.EditText fromInput = a.kit.edit("از تاریخ شمسی (مثال ۱۴۰۵/۰۱/۰۱)", draft.fromDay);
        android.widget.EditText toInput = a.kit.edit("تا تاریخ شمسی (مثال ۱۴۰۵/۰۱/۳۱)", draft.toDay);
        fromInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        toInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        fromInput.setContentDescription("تاریخ شروع کامل دورهٔ صورت‌حساب بانکی");
        toInput.setContentDescription("تاریخ پایان کامل دورهٔ صورت‌حساب بانکی");
        form.addView(fromInput, a.kit.lp(-1, -2));
        form.addView(toInput, a.kit.lp(-1, -2));
        scroll.addView(form);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle("تنظیم ستون‌ها و بازه")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("تحلیل و پیش‌نمایش", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            initial.date = bankColumnValue(dateColumn);
            initial.credit = bankColumnValue(creditColumn);
            initial.debit = bankColumnValue(debitColumn);
            initial.amount = bankColumnValue(amountColumn);
            initial.direction = bankColumnValue(directionColumn);
            initial.balance = bankColumnValue(balanceColumn);
            initial.description = bankColumnValue(descriptionColumn);
            initial.reference = bankColumnValue(referenceColumn);
            initial.account = bankColumnValue(accountColumn);
            initial.accountValue = accountFilter.getText().toString().trim();
            initial.dateFormat = dateFormat.getSelectedItemPosition();
            initial.amountFormat = amountFormat.getSelectedItemPosition();
            initial.amountsInToman = unit.getCheckedRadioButtonId() == tomans.getId();
            initial.signedAmountDefinesDirection = signedDirection.isChecked();
            if (!initial.valid()) {
                a.kit.toast("ستون تاریخ و دو ستون مبلغ یا مبلغ همراه جهت را انتخاب کنید");
                return;
            }
            String from = Jalali.disp(fromInput.getText().toString());
            String to = Jalali.disp(toInput.getText().toString());
            if (from.isEmpty() || to.isEmpty() || Jalali.parse(to) < Jalali.parse(from)) {
                a.kit.toast("بازهٔ شمسی معتبر نیست؛ تاریخ شروع و پایان را بررسی کنید");
                return;
            }
            draft.mapping = initial;
            draft.fromDay = from;
            draft.toDay = to;
            android.widget.Button analyze = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            analyze.setEnabled(false);
            android.app.AlertDialog progress = bankImportProgress("تاریخ و مبلغ ردیف‌ها در حافظه بررسی می‌شود…");
            progress.show();
            Thread worker = new Thread(() -> {
                BankStatementFile.Parsed parsed = null;
                String error = null;
                try { parsed = BankStatementFile.analyze(draft.table, initial, from, to); }
                catch (Exception failure) {
                    error = failure.getMessage() == null || failure.getMessage().trim().isEmpty()
                            ? "ساختار فایل با نگاشت انتخاب‌شده سازگار نیست" : failure.getMessage();
                }
                final BankStatementFile.Parsed result = parsed;
                final String failureText = error;
                a.runOnUiThread(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (!bankImportScreenCurrent(draft.renderToken)) {
                        if (dialog.isShowing()) dialog.dismiss();
                        return;
                    }
                    analyze.setEnabled(true);
                    if (failureText != null) {
                        showBankImportMessage("تحلیل انجام نشد", failureText);
                        return;
                    }
                    draft.parsed = result;
                    dialog.dismiss();
                    showBankStatementPreview(draft);
                });
            }, "bank-statement-local-analysis");
            worker.start();
        }));
        dialog.show();
    }

    private Spinner bankColumnSpinner(LinearLayout form, String label, List<String> headers, int selected) {
        form.addView(a.kit.text(label, 11.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        List<String> choices = new ArrayList<>();
        choices.add("— انتخاب نشده —");
        for (int i = 0; i < headers.size(); i++)
            choices.add(Money.fa(String.valueOf(i + 1)) + " • " + headers.get(i));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = new Spinner(a);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected >= 0 && selected < headers.size() ? selected + 1 : 0);
        spinner.setContentDescription(label);
        form.addView(spinner, a.kit.lp(-1, -2));
        return spinner;
    }

    private int bankColumnValue(Spinner spinner) {
        return spinner == null ? -1 : spinner.getSelectedItemPosition() - 1;
    }

    private void showBankStatementPreview(BankImportDraft draft) {
        if (draft == null || draft.parsed == null || !bankImportScreenCurrent(draft.renderToken)) return;
        BankStatementFile.Parsed parsed = draft.parsed;
        StringBuilder text = new StringBuilder();
        text.append("دورهٔ انتخاب‌شده: ").append(Money.fa(draft.fromDay)).append(" تا ")
                .append(Money.fa(draft.toDay)).append("\n")
                .append("تراکنش‌های پذیرفته‌شده: ").append(HomeNumberFormatter.integer(parsed.transactionCount)).append("\n")
                .append("جمع واریز فایل: ").append(HomeNumberFormatter.money(parsed.totalCreditRials)).append("\n")
                .append("جمع برداشت فایل: ").append(HomeNumberFormatter.money(parsed.totalDebitRials)).append("\n")
                .append("ردیف‌های خالی/بدون گردش کنارگذاشته‌شده: ").append(HomeNumberFormatter.integer(parsed.skippedRows)).append("\n")
                .append("ردیف‌های بیرون از بازه و مستثناشده: ").append(HomeNumberFormatter.integer(parsed.outsideRangeRows)).append("\n")
                .append("ردیف‌های حساب‌های دیگر که فیلتر شدند: ").append(HomeNumberFormatter.integer(parsed.filteredAccountRows)).append("\n")
                .append("واحد پایهٔ محاسبه: ریال؛ نمایش با واحد فعلی برنامه.\n\n")
                .append("فایل خام و شرح/شمارهٔ تراکنش ذخیره نمی‌شود. پس از تأیید، فقط جمع روزانهٔ واریز و برداشت روی همین دستگاه می‌ماند.\n");
        if (parsed.invalidRows > 0) {
            text.append("\nذخیره و مقایسه مسدود است: ").append(HomeNumberFormatter.integer(parsed.invalidRows))
                    .append(" ردیف نیازمند اصلاح یا نگاشت دوباره است.\n");
            if (parsed.errorSummary != null && !parsed.errorSummary.isEmpty())
                text.append(parsed.errorSummary).append('\n');
        }
        if (parsed.warnings != null) for (String warning : parsed.warnings)
            if (warning != null && !warning.trim().isEmpty()) text.append("\n• ").append(warning);

        ScrollView scroll = new ScrollView(a);
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(12));
        TextView details = a.kit.text(text.toString(), 12f, Theme.TEXT, false);
        details.setTextIsSelectable(true);
        details.setContentDescription("پیش‌نمایش جمع‌ها، ردیف‌های خطادار و محدودیت‌های فایل صورت‌حساب");
        body.addView(details, a.kit.lp(-1, -2));
        scroll.addView(body);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(a)
                .setTitle("پیش‌نمایش محلی صورت‌حساب")
                .setView(scroll)
                .setNegativeButton("بستن و دورریختن", (dialog, which) -> discardBankImportDraft(draft));
        if (parsed.invalidRows > 0) {
            builder.setPositiveButton("بازگشت به نگاشت", (dialog, which) -> showBankStatementMapping(draft));
        } else {
            builder.setNeutralButton("ویرایش ستون‌ها", (dialog, which) -> showBankStatementMapping(draft));
            builder.setPositiveButton("انتخاب حساب و مقایسه", (dialog, which) -> showBankStatementConfirmation(draft));
        }
        android.app.AlertDialog preview = builder.create();
        preview.setOnCancelListener(dialog -> discardBankImportDraft(draft));
        preview.show();
    }

    private void showBankStatementConfirmation(BankImportDraft draft) {
        if (draft == null || draft.parsed == null || draft.parsed.invalidRows > 0
                || !bankImportScreenCurrent(draft.renderToken)) return;
        List<Row> accounts = bankAccountsForHome(draft.dashboardData);
        if (accounts.isEmpty()) {
            showBankImportMessage("حساب بانکی در دسترس نیست", "فهرست حساب‌های آتیران برای انتخاب حساب پیدا نشد؛ فایل ذخیره نشد.");
            return;
        }
        ScrollView scroll = new ScrollView(a);
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(12));
        body.addView(a.kit.hint("حساب مقصد را با دقت انتخاب کنید. مقایسه از Query فقط‌خواندنی آتیران و همان بازهٔ شمسی استفاده می‌کند؛ هیچ تغییری در DB/API انجام نمی‌شود."), a.kit.lp(-1, -2));
        List<String> labels = new ArrayList<>();
        for (Row account : accounts) {
            String name = account.s("name").trim();
            String id = account.s("id").trim();
            labels.add((name.isEmpty() ? "حساب بانکی" : name) + " • کد " + id);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner accountSpinner = new Spinner(a);
        accountSpinner.setAdapter(adapter);
        accountSpinner.setContentDescription("انتخاب حساب آتیران برای تطبیق با فایل بانک");
        body.addView(accountSpinner, a.kit.lp(-1, -2));
        TextView period = a.kit.text("بازهٔ دقیق: " + Money.fa(draft.fromDay) + " تا " + Money.fa(draft.toDay),
                12f, Theme.TEXT, true);
        period.setPadding(0, Theme.dp(10), 0, Theme.dp(5));
        body.addView(period, a.kit.lp(-1, -2));
        CheckBox complete = new CheckBox(a);
        complete.setText("تأیید می‌کنم فایل کامل و فقط مربوط به همین حساب آتیران است؛ روز بدون ردیف واقعاً گردش صفر داشته است (یا ردیف‌های حساب‌های دیگر با فیلتر دقیق کنار گذاشته شده‌اند).");
        complete.setTextColor(Theme.TEXT);
        complete.setContentDescription("تأیید پوشش کامل و تک‌حسابی صورت‌حساب برای تمام روزهای بازه؛ روزهای بدون ردیف فقط با این تأیید صفر تلقی می‌شوند");
        body.addView(complete, a.kit.lp(-1, -2));
        body.addView(a.kit.hint("اگر خروجی ناقص است یا پوشش کامل را نمی‌دانید، تأیید نکنید؛ روزهای غایب نباید صفر فرض شوند. در آن حالت هیچ مقایسه یا ذخیره‌ای انجام نمی‌شود."), a.kit.lp(-1, -2));
        scroll.addView(body);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle("تأیید دوره و حساب")
                .setView(scroll)
                .setNegativeButton("لغو", null)
                .setPositiveButton("اجرای مقایسهٔ read-only", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!complete.isChecked()) {
                a.kit.toast("برای جلوگیری از فرض صفر، ابتدا کامل‌بودن کل بازه را تأیید کنید یا لغو کنید");
                return;
            }
            int selected = accountSpinner.getSelectedItemPosition();
            if (selected < 0 || selected >= accounts.size()) {
                a.kit.toast("حساب بانکی انتخاب نشده است");
                return;
            }
            Row account = accounts.get(selected);
            draft.bankId = account.s("id").trim();
            draft.bankName = account.s("name").trim();
            if (draft.bankId.isEmpty()) {
                a.kit.toast("شناسهٔ حساب آتیران در دسترس نیست");
                return;
            }
            dialog.dismiss();
            compareBankStatementWithLedger(draft);
        }));
        dialog.show();
    }

    private void compareBankStatementWithLedger(BankImportDraft draft) {
        if (draft == null || a.repo == null || !bankImportScreenCurrent(draft.renderToken)) {
            showBankImportMessage("مقایسه انجام نشد", "اتصال read-only آتیران در دسترس نیست؛ فایل ذخیره نشده است.");
            return;
        }
        android.app.AlertDialog progress = bankImportProgress("گردش روزانهٔ همان حساب از آتیران به‌صورت فقط‌خواندنی خوانده می‌شود…");
        progress.show();
        final String bankId = draft.bankId;
        final String from = draft.fromDay;
        final String to = draft.toDay;
        a.repo.run(connection -> Repo.exec(connection,
                        MasterQueries.bankTurnoverDaily(new Meta(connection), bankId, from, to)),
                new Repo.Cb<List<Row>>() {
                    @Override public void ok(List<Row> rows) {
                        if (progress.isShowing()) progress.dismiss();
                        if (!bankImportScreenCurrent(draft.renderToken)) return;
                        try {
                            Map<String, BankStatementPeriodReconciliation.DailyFlow> ledger = bankLedgerFlows(rows, draft);
                            draft.report = BankStatementPeriodReconciliation.compare(from, to,
                                    draft.parsed, ledger, true);
                            draft.comparedAt = System.currentTimeMillis();
                            draft.parsed = BankStatementFile.aggregateOnly(draft.parsed);
                            draft.table = null;
                            draft.mapping = null;
                            draft.dashboardData = null;
                            showBankStatementReport(draft);
                        } catch (Exception invalid) {
                            showBankImportMessage("دادهٔ گردش برای نتیجه‌گیری کافی نیست",
                                    invalid.getMessage() == null ? "روز یا مبلغی از گردش آتیران قابل‌اعتبارسنجی نبود؛ هیچ فایل یا جمعی ذخیره نشد."
                                            : invalid.getMessage());
                        }
                    }

                    @Override public void fail(String error) {
                        if (progress.isShowing()) progress.dismiss();
                        if (!bankImportScreenCurrent(draft.renderToken)) return;
                        showBankImportMessage("گردش آتیران در دسترس نیست",
                                "Query فقط‌خواندنی برای همین حساب و بازه کامل نشد؛ نتیجه نامشخص است و هیچ فایلی ذخیره نشده است. پس از بررسی اتصال یا دسترسی، دوباره تلاش کنید.");
                    }
                });
    }

    private Map<String, BankStatementPeriodReconciliation.DailyFlow> bankLedgerFlows(
            List<Row> rows, BankImportDraft draft) throws IOException {
        Map<String, BankStatementPeriodReconciliation.DailyFlow> result = new LinkedHashMap<>();
        int from = Jalali.parse(draft.fromDay);
        int to = Jalali.parse(draft.toDay);
        if (rows == null) throw new IOException("گردش روزانهٔ آتیران برنگشت؛ صفر فرض نشده است.");
        for (Row row : rows) {
            if (row == null) continue;
            if (row.l("invalidIn") > 0 || row.l("invalidOut") > 0 || row.l("negativeRows") > 0)
                throw new IOException("در گردش آتیران مبلغ تبدیل‌ناپذیر یا منفی وجود دارد؛ دادهٔ روزانه برای تطبیق معتبر نیست.");
            String day = Jalali.disp(row.s("day"));
            int dayNumber = Jalali.parse(day);
            if (dayNumber < from || dayNumber > to)
                throw new IOException("یک تاریخ از گردش آتیران نامعتبر یا بیرون از بازه است؛ مقایسه لغو شد.");
            double inflow = row.d("in");
            double outflow = row.d("out");
            long txCount = row.l("count");
            if (!Double.isFinite(inflow) || !Double.isFinite(outflow) || inflow < 0 || outflow < 0
                    || txCount < 0 || txCount > Integer.MAX_VALUE)
                throw new IOException("مبلغ یا تعداد تراکنش آتیران خارج از محدودهٔ معتبر است.");
            if (result.containsKey(day))
                throw new IOException("گردش آتیران یک روز را چندبار برگرداند؛ از جمع‌کردن مبهم داده خودداری شد.");
            result.put(day, new BankStatementPeriodReconciliation.DailyFlow(inflow, outflow, (int) txCount));
        }
        return result;
    }

    private void showBankStatementReport(BankImportDraft draft) {
        if (draft == null || draft.report == null || !bankImportScreenCurrent(draft.renderToken)) return;
        BankStatementPeriodReconciliation.Report report = draft.report;
        StringBuilder text = new StringBuilder();
        text.append("حساب آتیران: ").append(draft.bankName.isEmpty() ? "حساب بانکی" : draft.bankName)
                .append(" • کد ").append(draft.bankId).append('\n')
                .append("بازهٔ شمسی: ").append(Money.fa(report.fromDay)).append(" تا ")
                .append(Money.fa(report.toDay)).append('\n')
                .append("منبع بانکی: خروجی دستی انتخاب‌شده • منبع دفتر: Query فقط‌خواندنی آتیران\n")
                .append("زمان تکمیل Query (ساعت دستگاه): ").append(bankImportTimestamp(draft.comparedAt)).append('\n')
                .append("پوشش کامل فایل برای کل دوره: تأیید مدیر؛ روز بدون ردیف فقط با همین فرض صفر است.\n")
                .append("واحد پایهٔ محاسبه: ریال؛ واحد نمایش فعلی برنامه: ").append(Money.unitName()).append(".\n\n")
                .append("صورت‌حساب: واریز ").append(HomeNumberFormatter.money(report.statementInflowRials))
                .append(" • برداشت ").append(HomeNumberFormatter.money(report.statementOutflowRials)).append('\n')
                .append("دفتر آتیران: واریز ").append(HomeNumberFormatter.money(report.ledgerInflowRials))
                .append(" • برداشت ").append(HomeNumberFormatter.money(report.ledgerOutflowRials)).append('\n')
                .append("اختلاف کل واریز: ").append(bankVariancePiece("واریز", report.inflowVarianceRials)).append('\n')
                .append("اختلاف کل برداشت: ").append(bankVariancePiece("برداشت", report.outflowVarianceRials)).append("\n\n")
                .append("روزهای برابر: ").append(HomeNumberFormatter.integer(report.matchedDays))
                .append(" • روزهای دارای اختلاف: ").append(HomeNumberFormatter.integer(report.varianceDays))
                .append(" • روزهای نامشخص: ").append(HomeNumberFormatter.integer(report.unavailableDays)).append('\n')
                .append("روز با گردش فقط در فایل: ").append(HomeNumberFormatter.integer(report.bankOnlyDays))
                .append(" • روز با گردش فقط در دفتر: ").append(HomeNumberFormatter.integer(report.ledgerOnlyDays)).append('\n')
                .append("تراکنش‌های فایل پس از فیلتر حساب: ").append(HomeNumberFormatter.integer(draft.parsed.transactionCount))
                .append(" • ردیف‌های حساب‌های دیگرِ فیلترشده: ").append(HomeNumberFormatter.integer(draft.parsed.filteredAccountRows)).append('\n')
                .append("ردیف‌های دفتر آتیران: ").append(HomeNumberFormatter.integer(report.ledgerTransactions)).append("\n\n")
                .append("فرمول روزانه: اختلاف واریز = واریز صورت‌حساب − واریز دفتر؛ اختلاف برداشت = برداشت صورت‌حساب − برداشت دفتر. هر دو جهت جداگانه و برای تک‌تک روزها سنجیده شده‌اند؛ اختلاف خالص برای اعلام تطبیق به‌کار نمی‌رود. آستانهٔ هر جهت: نیم ریال (۰٫۵ ریال).\n")
                .append("این گزارش تطبیق گردش روزانه است، نه تطبیق ماندهٔ ابتدا/پایان یا تأیید قطعی حسابداری. کنترل صندوق جداگانه با ماندهٔ آغاز + دریافت نقد − پرداخت نقد در برابر شمارش پایان انجام می‌شود؛ از گردش بانک به‌تنهایی مبلغ صندوق استنتاج نشده است. فقط جمع‌های روزانهٔ تأییدشده ذخیره می‌شوند؛ فایل خام و ریز تراکنش‌ها ذخیره نمی‌شوند.\n");
        if (report.varianceDays > 0) {
            text.append("\nجزئیات روزهای دارای اختلاف (حداکثر ۲۰ روز):\n");
            int shown = 0;
            for (BankStatementPeriodReconciliation.DayResult day : report.days) {
                if (day.comparison.state != BankStatementReconciliation.State.VARIANCE) continue;
                text.append(Money.fa(day.day)).append(" — ")
                        .append(bankVariancePiece("واریز", day.comparison.inflowVarianceRials)).append("؛ ")
                        .append(bankVariancePiece("برداشت", day.comparison.outflowVarianceRials)).append('\n');
                if (++shown >= 20) break;
            }
            if (report.varianceDays > 20) text.append("روزهای بیشتر در جمع روزهای دارای اختلاف آمده‌اند؛ جزئیات فقط ۲۰ روز نخست را نشان می‌دهد.\n");
        } else if (report.fullyMatched()) {
            text.append("\nدر بازهٔ تأییدشده، جمع واریز و برداشت هر روز در محدودهٔ آستانه برابر است.");
        }

        ScrollView scroll = new ScrollView(a);
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(12));
        TextView details = a.kit.text(text.toString(), 12f, Theme.TEXT, false);
        details.setTextIsSelectable(true);
        details.setContentDescription("گزارش کامل تطبیق روزانهٔ صورت‌حساب فایل با دفتر read-only آتیران");
        body.addView(details, a.kit.lp(-1, -2));
        scroll.addView(body);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(a)
                .setTitle(report.fullyMatched() ? "گزارش تطبیق روزانه • برابر" : "گزارش مغایرت‌گیری روزانه")
                .setView(scroll)
                .setNegativeButton("بستن", (ignored, which) -> discardBankImportDraft(draft))
                .setPositiveButton(report.fullyComparable() ? "ذخیره فقط جمع‌های روزانه" : "ذخیره در دسترس نیست", null)
                .create();
        dialog.setOnCancelListener(ignored -> discardBankImportDraft(draft));
        dialog.setOnShowListener(ignored -> {
            android.widget.Button save = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            if (!report.fullyComparable() || draft.parsed.invalidRows > 0) {
                save.setEnabled(false);
                return;
            }
            save.setContentDescription("ذخیرهٔ جمع‌های روزانهٔ تأییدشده روی همین دستگاه؛ فایل و ریز تراکنش‌ها ذخیره نمی‌شوند");
            save.setOnClickListener(v -> {
                Map<String, String[]> batch = bankStatementDailyBatch(draft);
                boolean saved = batch != null && a.settings.setHomeBankStatementRialsBatch(
                        draft.bankId, draft.fromDay, draft.toDay, batch);
                if (!saved) {
                    a.kit.toast("ذخیرهٔ جمع‌های روزانه کامل نشد؛ داده‌ای به آتیران فرستاده نشده است");
                    return;
                }
                LinearLayout content = draft.content;
                dialog.dismiss();
                discardBankImportDraft(draft);
                a.kit.toast("فقط جمع‌های روزانهٔ صورت‌حساب روی همین دستگاه ذخیره شد");
                render(content);
            });
        });
        dialog.show();
    }

    private Map<String, String[]> bankStatementDailyBatch(BankImportDraft draft) {
        if (draft == null || draft.parsed == null || draft.parsed.invalidRows > 0
                || draft.report == null || !draft.report.fullyComparable()) return null;
        int from = Jalali.parse(draft.fromDay);
        int to = Jalali.parse(draft.toDay);
        if (from < 0 || to < from || to - from + 1 > BankStatementFile.MAX_PERIOD_DAYS) return null;
        Map<String, String[]> batch = new LinkedHashMap<>();
        for (int offset = 0; offset <= to - from; offset++) {
            String day = Jalali.format(from + offset);
            BankStatementFile.DailyTotal daily = draft.parsed.daily.get(day);
            double in = daily == null ? 0d : daily.creditRials;
            double out = daily == null ? 0d : daily.debitRials;
            if (!Double.isFinite(in) || !Double.isFinite(out) || in < 0 || out < 0) return null;
            batch.put(day, new String[]{bankRialsStorageValue(in), bankRialsStorageValue(out)});
        }
        return batch;
    }

    private String bankRialsStorageValue(double amount) {
        return java.math.BigDecimal.valueOf(amount)
                .setScale(4, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private String bankImportTimestamp(long timestamp) {
        String value = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                .format(new java.util.Date(timestamp));
        return Money.fa(value);
    }

    private void discardBankImportDraft(BankImportDraft draft) {
        if (draft == null) return;
        draft.table = null;
        draft.mapping = null;
        draft.parsed = null;
        draft.report = null;
        draft.dashboardData = null;
        draft.content = null;
        draft.bankName = "";
        draft.bankId = "";
    }

    private void addExceptionRows(LinearLayout card, Data d) {
        if (!d.overpaidVouchersAvailable) {
            card.addView(financialAuditLine("home-overpaid-vouchers", "قبض‌های دارای اضافه‌پرداخت",
                    "گزارش در دسترس نیست؛ نبود نتیجه به‌معنی نبود مورد نیست.", "نامشخص", Theme.WARNING),
                    a.kit.lp(-1, -2));
        } else if (d.overpaidVouchers == null || d.overpaidVouchers.isEmpty()) {
            card.addView(financialAuditLine("home-overpaid-vouchers", "قبض‌های دارای اضافه‌پرداخت",
                    "در کنترل قبض‌های فعال امروز موردی با نقد و چک بیش از مبلغ قبض برنگشت.",
                    "بدون مورد در این کنترل", Theme.SUCCESS), a.kit.lp(-1, -2));
        } else {
            for (int i = 0; i < d.overpaidVouchers.size(); i++) {
                Row item = d.overpaidVouchers.get(i);
                card.addView(financialAuditLine("home-overpaid-voucher-" + i,
                        "قبض " + item.s("no") + " • " + item.s("kind"),
                        item.s("party") + " • مبلغ قبض " + HomeNumberFormatter.money(item.d("total"))
                                + " • نقد و چک " + HomeNumberFormatter.money(item.d("paid"))
                                + " • اضافه " + HomeNumberFormatter.money(item.d("excess")),
                        "بازبینی سند", Theme.DANGER), a.kit.lp(-1, -2));
            }
            if (d.overpaidVouchers.size() >= 5)
                card.addView(a.kit.hint("فهرست فقط ۵ مورد نخست را نشان می‌دهد؛ ممکن است موارد بیشتری وجود داشته باشد."), a.kit.lp(-1, -2));
        }

        if (!d.unbalancedJournalsAvailable) {
            card.addView(financialAuditLine("home-unbalanced-journals", "اسناد حسابداری نامتوازن",
                    "گزارش در دسترس نیست؛ نبود نتیجه به‌معنی توازن قطعی نیست.", "نامشخص", Theme.WARNING),
                    a.kit.lp(-1, -2));
        } else if (d.unbalancedJournals == null || d.unbalancedJournals.isEmpty()) {
            card.addView(financialAuditLine("home-unbalanced-journals", "اسناد حسابداری نامتوازن",
                    "در کنترل اسناد ثبت‌شدهٔ این روز، سند نامتوازن برنگشت.",
                    "بدون مورد در این کنترل", Theme.SUCCESS), a.kit.lp(-1, -2));
        } else {
            for (int i = 0; i < d.unbalancedJournals.size(); i++) {
                Row item = d.unbalancedJournals.get(i);
                card.addView(financialAuditLine("home-unbalanced-journal-" + i,
                        "سند حسابداری " + item.s("sanad"),
                        "بدهکار " + HomeNumberFormatter.money(item.d("debit"))
                                + " • بستانکار " + HomeNumberFormatter.money(item.d("credit"))
                                + " • اختلاف " + HomeNumberFormatter.money(item.d("diff")),
                        "بازبینی سند", Theme.DANGER), a.kit.lp(-1, -2));
            }
            if (d.unbalancedJournals.size() >= 5)
                card.addView(a.kit.hint("فهرست فقط ۵ سند نخست را نشان می‌دهد؛ ممکن است موارد بیشتری وجود داشته باشد."), a.kit.lp(-1, -2));
        }
    }

    private double grossProfitOnDay(Data d, String day) {
        if (d == null || d.grossProfitDaily == null) return 0;
        double total = 0;
        String target = Jalali.disp(day);
        for (Row row : d.grossProfitDaily) {
            if (row != null && target.equals(Jalali.disp(row.s("day")))) total += row.d("profit");
        }
        return total;
    }

    private String closeoutVarianceLabel(Data d, String day) {
        if (d == null || !d.cashReceiptsAvailable || !d.cashPaymentsAvailable)
            return "تفکیک نقد در دادهٔ فروشگاه موجود نیست";
        String openingText = a.settings.closeoutCashOpening(day);
        String countedText = a.settings.closeoutCashCounted(day);
        Double opening = parseLocalRials(openingText);
        Double counted = parseLocalRials(countedText);
        if (opening == null || counted == null) return "شمارش محلی ثبت نشده";
        double variance = CashCloseMath.variance(opening, d.cashReceipts.d("cash"),
                d.cashPayments.d("cash"), counted);
        if (Double.isNaN(variance) || Double.isInfinite(variance)) return "قابل محاسبه نیست";
        String direction = variance > 0 ? "مازاد " : variance < 0 ? "کسری " : "بدون اختلاف ";
        return direction + HomeNumberFormatter.money(Math.abs(variance));
    }

    /** Short face value for narrow closeout tiles; full wording remains in the export report. */
    static String compactCloseoutVariance(String fullValue) {
        String value = fullValue == null ? "" : fullValue.trim();
        if ("شمارش محلی ثبت نشده".equals(value)) return "ثبت نشده";
        if ("قابل محاسبه نیست".equals(value)) return "نامشخص";
        if (value.startsWith("تفکیک نقد")) return "ناموجود";
        return value;
    }

    /** One concise instruction line explains abbreviated closeout states without ellipses. */
    static String closeoutVarianceHint(String fullValue) {
        String value = fullValue == null ? "" : fullValue.trim();
        if ("شمارش محلی ثبت نشده".equals(value))
            return "ماندهٔ آغاز و شمارش پایان را وارد کنید";
        if ("قابل محاسبه نیست".equals(value))
            return "دادهٔ نقد برای تطبیق کافی نیست";
        if (value.startsWith("تفکیک نقد"))
            return "تفکیک دریافت و پرداخت نقد در دسترس نیست";
        return "در برابر ماندهٔ مورد انتظار";
    }

    private Double parseLocalRials(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String normalized = ir.meelano.manager.core.Money.en(raw).replace(",", "")
                .replace("٬", "").replace('٫', '.').trim();
        try {
            double value = Double.parseDouble(normalized);
            return value >= 0 && !Double.isNaN(value) && !Double.isInfinite(value) ? value : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    private void showCloseoutCashDialog(LinearLayout content, Data d, String day) {
        android.widget.LinearLayout form = a.kit.v();
        form.setPadding(Theme.dp(20), Theme.dp(10), Theme.dp(20), Theme.dp(8));
        form.addView(a.kit.hint("مبالغ به ریال وارد می‌شوند و فقط روی این دستگاه برای همین روز می‌مانند. هیچ تغییری در دفتر فروشگاه ثبت نمی‌شود."), a.kit.lp(-1, -2));
        android.widget.EditText opening = a.kit.edit("موجودی نقد آغاز روز به ریال",
                a.settings.closeoutCashOpening(day));
        android.widget.EditText counted = a.kit.edit("شمارش نقد پایان روز به ریال",
                a.settings.closeoutCashCounted(day));
        int numberInput = android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
        opening.setInputType(numberInput);
        counted.setInputType(numberInput);
        opening.setContentDescription("موجودی نقد آغاز روز به ریال");
        counted.setContentDescription("شمارش نقد پایان روز به ریال");
        form.addView(opening, a.kit.lp(-1, -2));
        form.addView(counted, a.kit.lp(-1, -2));
        new android.app.AlertDialog.Builder(a).setTitle("ثبت شمارش صندوق • " + day).setView(form)
                .setPositiveButton("ذخیره روی دستگاه", (dialog, which) -> {
                    Double open = parseLocalRials(opening.getText().toString());
                    Double close = parseLocalRials(counted.getText().toString());
                    if (open == null || close == null) {
                        a.kit.toast("هر دو مبلغ را به‌صورت عدد صفر یا بیشتر وارد کنید");
                        return;
                    }
                    a.settings.setCloseoutCashOpening(day, opening.getText().toString());
                    a.settings.setCloseoutCashCounted(day, counted.getText().toString());
                    render(content);
                }).setNegativeButton("انصراف", null).show();
    }

    private void shareCloseoutReport(Data d, String day, boolean print) {
        java.util.List<Row> rows = new java.util.ArrayList<>();
        addCloseoutExportRow(rows, "فروش", d.salesSnapshot != null && d.salesSnapshot.isAvailable()
                ? HomeNumberFormatter.money(d.salesSnapshot.salesRialsOn(day)) : "داده در دسترس نیست");
        addCloseoutExportRow(rows, "دریافت ثبت‌شده", d.inDay == null ? "داده در دسترس نیست"
                : HomeNumberFormatter.money(d.inDay.d("total")));
        addCloseoutExportRow(rows, "پرداخت ثبت‌شده", d.outDay == null ? "داده در دسترس نیست"
                : HomeNumberFormatter.money(d.outDay.d("total")));
        addCloseoutExportRow(rows, "برگشتی امروز", d.returnsTodayAvailable
                ? HomeNumberFormatter.money(sumField(d.returnsToday, "total")) + " • "
                    + HomeNumberFormatter.integer(sumLongField(d.returnsToday, "docs")) + " سند"
                : "داده در دسترس نیست");
        addCloseoutExportRow(rows, "سود ناخالص برآوردی", d.grossProfitAvailable
                ? HomeNumberFormatter.money(grossProfitOnDay(d, day)) : "مبنای بهای تمام‌شده موجود نیست");
        addCloseoutExportRow(rows, "اختلاف شمارش صندوق", closeoutVarianceLabel(d, day));
        ir.meelano.manager.core.ReportCatalog.Col[] columns = {
                new ir.meelano.manager.core.ReportCatalog.Col("label", "شرح", ir.meelano.manager.core.ReportCatalog.T_TEXT),
                new ir.meelano.manager.core.ReportCatalog.Col("value", "مبلغ / وضعیت", ir.meelano.manager.core.ReportCatalog.T_TEXT)
        };
        String subtitle = day + " • خلاصهٔ خواندنی از دفتر و شمارش محلی دستگاه";
        if (print) a.printPdf("گزارش بستن روز", subtitle, columns, rows);
        else a.sharePdf("گزارش بستن روز", subtitle, columns, rows);
    }

    private void addCloseoutExportRow(java.util.List<Row> rows, String label, String value) {
        Row row = new Row();
        row.put("label", label);
        row.put("value", value);
        rows.add(row);
    }

    private void secSmartReport(LinearLayout content, Data d, String syncTime) {
        if (!RoleStore.allowed(a, "sales")) return;
        final SmartReportEngine.Input report = smartReportInput(d);
        final List<String> sentences = SmartReportEngine.compose(report);
        final SmartReportEngine.FollowUpPriority priority = SmartReportEngine.followUpPriority(report);
        LinearLayout card = homeCard(Theme.SECTION_SALES);
        card.setTag("home-smart-report");
        card.setPadding(Theme.dp(12), Theme.dp(9), Theme.dp(12), Theme.dp(12));
        Button share = dashboardHeaderAction("اشتراک", Theme.SECTION_SALES,
                "اشتراک تصویر برندشدهٔ گزارش هوشمند امروز", v -> {
                    try {
                        a.shareImage(SmartReportImage.create(a,
                                d == null ? Jalali.todayStr() : d.snapshotDay, sentences),
                                "گزارش هوشمند امروز میلانو");
                    } catch (Exception e) {
                        a.kit.toast("ساخت تصویر ممکن نشد");
                    }
                });
        share.setTag("home-smart-report-share");
        String period = snapshotLabel(d);
        card.addView(boardHead("گزارش هوشمند " + period,
                "سه نکته بر پایهٔ داده‌های ثبت‌شده", R.drawable.mi_star_shine,
                share, Theme.SECTION_SALES), a.kit.lp(-1, -2));

        LinearLayout meta = a.kit.h();
        meta.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        String received = syncTime == null ? "" : syncTime.trim();
        String timestamp = received.isEmpty() ? "زمان آخرین دریافت ثبت نشده"
                : "آخرین دریافت موفق · " + received;
        TextView updated = a.kit.text(timestamp, 9.5f,
                Theme.dashboardCardMutedInk(Theme.SECTION_SALES), false);
        updated.setMaxLines(2);
        updated.setEllipsize(TextUtils.TruncateAt.END);
        updated.setContentDescription(timestamp);
        meta.addView(updated, a.kit.wlp(1f));

        int priorityTone = reportPriorityTone(priority);
        String priorityLabel = reportPriorityLabel(priority);
        TextView priorityChip = a.kit.text(priorityLabel, 9.5f,
                Theme.sectionText(priorityTone), true);
        priorityChip.setGravity(Gravity.CENTER);
        priorityChip.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        priorityChip.setBackground(Theme.pill(priorityTone));
        priorityChip.setContentDescription("اولویت پیگیری: " + priorityLabel);
        meta.addView(priorityChip, a.kit.lp(-2, -2));
        LinearLayout.LayoutParams metaParams = a.kit.lp(-1, -2);
        metaParams.topMargin = Theme.dp(4);
        card.addView(meta, metaParams);
        card.addView(a.kit.gap(6));

        String[] headings = {"نتیجهٔ فروش", "کالای پیشتاز", "پیگیری‌های شناسایی‌شده"};
        int[] icons = {R.drawable.mi_point_of_sale, R.drawable.mi_inventory_2, R.drawable.mi_warning};
        int[] tones = {Theme.SECTION_SALES, Theme.SECTION_WAREHOUSE, Theme.SECTION_ALERT};
        View.OnClickListener[] details = {
                navAction("sales"),
                navAction("products"),
                navAction(RoleStore.allowed(a, "dues") ? "dues" : "reports")
        };
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            LinearLayout line = a.kit.h();
            line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            line.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(Theme.dp(56));
            line.setPadding(Theme.dp(8), Theme.dp(7), Theme.dp(8), Theme.dp(7));
            line.setBackground(Theme.dashboardMetric(tones[i]));
            line.setElevation(Theme.dp(1f));
            line.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            line.addView(dashboardArt(icons[i], tones[i]),
                    new LinearLayout.LayoutParams(Theme.dp(34), Theme.dp(34)));
            line.addView(a.kit.space(8));

            LinearLayout copy = a.kit.v();
            TextView label = a.kit.text(headings[i], 10.5f,
                    Theme.dashboardMutedInk(tones[i]), true);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(label, a.kit.lp(-1, -2));
            TextView detail = a.kit.text(sentence, 11.5f, Theme.TEXT, false);
            detail.setMaxLines(3);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(detail, a.kit.lp(-1, -2));
            line.addView(copy, a.kit.wlp(1f));
            line.setContentDescription(headings[i] + "؛ " + sentence
                    + (details[i] == null ? "" : "؛ لمس برای مشاهدهٔ جزئیات"));
            final View.OnClickListener detailAction = details[i];
            if (detailAction != null) {
                Theme.pressable(line);
                line.setOnClickListener(detailAction);
            }
            LinearLayout.LayoutParams lineParams = a.kit.lp(-1, -2);
            if (i > 0) lineParams.topMargin = Theme.dp(6);
            card.addView(line, lineParams);
        }
        a.kit.addCard(content, card);
    }

    private int reportPriorityTone(SmartReportEngine.FollowUpPriority priority) {
        if (priority == SmartReportEngine.FollowUpPriority.URGENT) return Theme.DANGER;
        if (priority == SmartReportEngine.FollowUpPriority.FOLLOW_UP) return Theme.WARNING;
        if (priority == SmartReportEngine.FollowUpPriority.CLEAR) return Theme.SUCCESS;
        return Theme.MUTED;
    }

    private String reportPriorityLabel(SmartReportEngine.FollowUpPriority priority) {
        if (priority == SmartReportEngine.FollowUpPriority.URGENT) return "پیگیری فوری";
        if (priority == SmartReportEngine.FollowUpPriority.FOLLOW_UP) return "نیازمند پیگیری";
        if (priority == SmartReportEngine.FollowUpPriority.CLEAR) return "بدون اقدام فوری";
        return "دادهٔ پیگیری ناقص";
    }

    private SmartReportEngine.Input smartReportInput(Data d) {
        SmartReportEngine.Input input = new SmartReportEngine.Input();
        if (d != null && d.salesSnapshot != null && d.salesSnapshot.isAvailable()
                && RoleStore.allowed(a, "sales")) {
            String today = snapshotDay(d);
            input.salesAvailable = true;
            input.salesRials = d.salesSnapshot.salesRialsOn(today);
            input.documents = d.salesSnapshot.documentsOn(today);
            int elapsed = today.equals(Jalali.todayStr())
                    ? minuteOfDay() : SalesComparison.MINUTES_PER_DAY;
            SalesComparison.DayResult comparison = SalesComparison.evaluate(d.salesSnapshot, today,
                    elapsed, SalesComparison.DEFAULT_COMPARABLE_WEEKS);
            input.comparisonAvailable = comparison.hasBaseline() && comparison.projectionAvailable;
            input.changePercent = comparison.changePercent();
        }
        if (d != null && RoleStore.allowed(a, "sales")) {
            input.productsAvailable = d.topItemsAvailable;
            if (d.topItemsAvailable && d.topItems != null && !d.topItems.isEmpty()) {
                Row leading = d.topItems.get(0);
                input.leadingProduct = leading.s("label");
                input.leadingProductSalesRials = leading.d("total");
            }
            input.overdueAvailable = d.overdueAvailable;
            input.overdueInvoices = d.overdue == null ? 0 : d.overdue.size();
            input.dueChequesAvailable = d.dueInAvailable && d.dueOutAvailable;
            input.dueCheques = (d.dueIn == null ? 0 : d.dueIn.size())
                    + (d.dueOut == null ? 0 : d.dueOut.size());
            input.returnsAvailable = d.returnsAvailable;
            input.returnDocuments = sumLongField(d.returns, "docs");
        }
        return input;
    }

    private void secEod(LinearLayout content) {
        // ---- end-of-day summary (saved nightly, one-tap share) ----
        String txt;
        String dt;
        try {
            txt = a.settings.eodText();
            dt = a.settings.eodDate();
        } catch (Exception e) {
            return;
        }
        if (txt == null || txt.isEmpty()) return;
        LinearLayout c = homeCard(Theme.SECTION_SALES);
        String when = "";
        try {
            when = dt == null || dt.isEmpty() ? "" : Jalali.dispFa(dt);
        } catch (Exception ignored) { Diag.ignored("HomeScreen:800", ignored); }
        c.addView(a.kit.sectionHead("🌙 جمع‌بندی پایان روز" + (when.isEmpty() ? "" : " • " + when),
                null, null), a.kit.lp(-1, -2));
        String displayText = HomeNumberFormatter.localizeText(txt);
        TextView summary = a.kit.text(displayText, 12f, Theme.TEXT, false);
        summary.setMaxLines(3);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(summary, a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));
        final String share = "🌙 جمع‌بندی پایان روز میلانو" + (when.isEmpty() ? "" : " (" + when + ")")
                + "\n" + displayText;
        c.addView(a.kit.btnGhost("↗ اشتراک‌گذاری", Theme.GOLD, v -> {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(android.content.Intent.EXTRA_TEXT, share);
                a.startActivity(android.content.Intent.createChooser(i, "اشتراک جمع‌بندی روز"));
            } catch (Exception e) {
                a.kit.toast("اشتراک ممکن نشد");
            }
        }), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    private String dayOf(Row day) {
        if (day == null) return "";
        String d = Jalali.disp(day.s("d"));
        if (d.isEmpty()) return "";
        String today = Jalali.todayStr();
        if (d.equals(today)) return "امروز";
        if (d.equals(Jalali.addDays(today, -1))) return "دیروز";
        return HomeNumberFormatter.localizeText(Jalali.shortLabel(d));
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

    private double salesOnDay(Data data, String day) {
        return data == null || data.salesSnapshot == null ? 0
                : data.salesSnapshot.salesRialsOn(day);
    }

    /** Average across calendar days before the selected day; no-sale days count as zero. */
    private double averageSalesBefore(Data data, String day, int days) {
        return data == null || data.salesSnapshot == null ? 0
                : data.salesSnapshot.averageCalendarDaysBefore(day, days);
    }

    /** Fill missing calendar days only inside the successful snapshot's queried history. */
    private List<Charts.Point> filled(SalesSnapshot snapshot, String endDay) {
        List<Charts.Point> out = new ArrayList<>();
        if (snapshot == null || !snapshot.isAvailable()) return out;
        String to = Jalali.disp(endDay);
        if (to.isEmpty()) to = Jalali.todayStr();
        int end = Jalali.parse(to);
        int start = Jalali.parse(snapshot.historyStart());
        if (end < 0 || start < 0 || end < start) return out;
        int availableDays = Math.min(14, end - start + 1);
        for (SalesSnapshot.Day day : snapshot.dailySeries(to, availableDays))
            out.add(new Charts.Point(HomeNumberFormatter.localizeText(Jalali.shortLabel(day.date)), day.salesRials));
        return out;
    }

    private String lastCap(List<Charts.Point> pts) {
        Charts.Point p = pts.get(pts.size() - 1);
        return HomeNumberFormatter.localizeText(p.label) + " • " + HomeNumberFormatter.money(p.value);
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
