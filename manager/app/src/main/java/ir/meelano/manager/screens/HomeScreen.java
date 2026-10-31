package ir.meelano.manager.screens;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.ViewGroup;
import java.util.Locale;
import ir.meelano.manager.core.MarketWeather;
import ir.meelano.manager.core.Diag;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.AlertEngine;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.FollowUps;
import ir.meelano.manager.core.HomeRange;
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.SalesComparison;
import ir.meelano.manager.core.SalesSnapshot;
import ir.meelano.manager.core.SyncState;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.HomePaymentMixBar;
import ir.meelano.manager.ui.HomeSalesChart;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.MeelanoIcons;
import ir.meelano.manager.ui.Theme;

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
        List<Row> salesDaily = new ArrayList<>();
        SalesSnapshot salesSnapshot;
        boolean salesTrendAvailable;
        String salesHistoryFrom = "";
        List<Row> topItems = new ArrayList<>();
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
        Map<String, String> notes = new LinkedHashMap<>();
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

        /** Probes the link with SELECT 1 (jTDS does not implement Connection.isValid). */
        void ensure(Repo repo) {
            boolean alive;
            try (java.sql.Statement st = c.createStatement()) {
                st.setQueryTimeout(5);
                st.execute("SELECT 1");
                alive = true;
            } catch (Throwable e) {
                alive = false;
            }
            if (alive) return;
            try {
                reopen(repo);
            } catch (Throwable e) {
                Diag.ignored("HomeScreen:ensure", e);
            }
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

    /** Like live(), but on a dropped link it reopens the connection and runs the section once more. */
    private <T> T liveL(final int token, final Link L, Map<String, String> notes, String key, Soft<T> t) {
        if (a.renderGen() != token) return null;
        L.ensure(a.repo);
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
        if (cached != null) {
            syncWasRefreshing = true;
            build(content, cached, SyncState.REFRESHING, cachedTs[0]);
        } else {
            content.removeAllViews();
            syncWasRefreshing = true;
            content.addView(syncStatus("", SyncState.REFRESHING, null, null), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
            content.addView(greetingHero(cached), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
            if (RoleStore.allowed(a, "sales")) {
                content.addView(salesHero(null, SyncState.REFRESHING), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
            }
            // Market data is independent of the Atiran dashboard query and can render immediately.
            content.addView(marketCard(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
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
        s.returns = g.returns;
        s.notes.putAll(g.notes);
    }

    private static void mergeCharts(Data s, Data g) {
        s.salesDaily = g.salesDaily;
        s.salesTrendAvailable = g.salesTrendAvailable;
        s.salesHistoryFrom = g.salesHistoryFrom;
        s.topItems = g.topItems;
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
            LinearLayout bc = a.kit.card(Theme.SECTION_ALERT);
            bc.addView(a.kit.text("جزئیات اتصال", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
            bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
            content.addView(bc, 2);
            content.addView(a.kit.gap(8), 3);
            localizeHomeNumbers(content);
            return;
        }
        content.removeAllViews();
        content.addView(syncStatus("", failureState, faError, v -> render(content)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(greetingHero(null), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        if (RoleStore.allowed(a, "sales")) {
            content.addView(salesHero(null, failureState), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
        }
        content.addView(marketCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
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
                    .append("|dueoutok=").append(d.dueOutAvailable ? "1" : "0");
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
                else if (k.matches("top[0-4]")) { d.topItems.add(rowFrom(v)); any = true; }
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
        String name = AtiranAuth.sessionName(a);
        if (name == null || name.trim().isEmpty()) name = AtiranAuth.sessionUser(a);
        if (name == null || name.trim().isEmpty()) name = RoleStore.faName(currentRole());
        name = HomeNumberFormatter.localizeText(name == null ? "مدیر" : name.trim());

        LinearLayout card = a.kit.card(Theme.SECTION_SALES);
        card.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(10));
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(a.kit.iconBadge(R.drawable.mi_wb_twilight, Theme.SECTION_SALES, 36),
                a.kit.lp(Theme.dp(36), Theme.dp(36)));
        row.addView(a.kit.space(10));

        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text(greeting + "، " + name, 16f, Theme.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        copy.addView(title, a.kit.lp(-1, -2));
        String summary = greetingSummary(data);
        TextView detail = a.kit.text(dateLine() + "  ·  " + summary, 10.5f, Theme.MUTED, false);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(detail, a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        card.addView(row, a.kit.lp(-1, -2));
        card.setContentDescription(greeting + "، " + name + "؛ " + dateLine() + "؛ " + summary);
        return card;
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
    private int selectedSalesRange;

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
        final LinearLayout card = a.kit.card(Theme.SECTION_SALES);
        card.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(12));
        card.addView(a.kit.sectionHeader("فروش", R.drawable.mi_point_of_sale,
                Theme.SECTION_SALES, "جزئیات ›", navAction("sales")), a.kit.lp(-1, -2));

        final LinearLayout amountPanel = a.kit.v();
        final android.animation.ValueAnimator[] amountAnimator = {null};
        final HomeSalesChart.Area chart = new HomeSalesChart.Area(a);
        View selector = salesRangeSelector(card, amountPanel, data, chart, amountAnimator);
        LinearLayout.LayoutParams selectorParams = a.kit.lp(-1, -2);
        selectorParams.topMargin = Theme.dp(8);
        card.addView(selector, selectorParams);

        LinearLayout.LayoutParams amountParams = a.kit.lp(-1, -2);
        amountParams.topMargin = Theme.dp(8);
        card.addView(amountPanel, amountParams);
        renderSalesSummary(amountPanel, data, selectedSalesRange, false, amountAnimator);

        if (data != null && data.salesSnapshot != null && data.salesSnapshot.isAvailable()) {
            String end = snapshotDay(data);
            List<SalesSnapshot.Day> series = data.salesSnapshot.dailySeries(end, 14);
            double average = 0;
            for (SalesSnapshot.Day day : series) average += day.salesRials;
            if (!series.isEmpty()) average /= series.size();
            chart.setData(series, average);
            chart.setSelectedDate(selectedSalesRange == 1 ? Jalali.addDays(end, -1) : end);
            final TextView chartHint = a.kit.text("۱۴ روز · قدیمی‌تر از راست به چپ · جمعه با زمینهٔ کم‌رنگ",
                    9.5f, Theme.MUTED, false);
            chartHint.setGravity(Gravity.END);
            chart.setSelectionListener(day -> chartHint.setText(Jalali.weekday(day.date) + "، "
                    + Jalali.dispFa(day.date) + " · " + HomeNumberFormatter.compactMoney(day.salesRials)));
            LinearLayout.LayoutParams chartParams = a.kit.lp(-1, Theme.dp(188));
            chartParams.topMargin = Theme.dp(8);
            card.addView(chart, chartParams);
            card.addView(chartHint, a.kit.lp(-1, -2));
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
                    9.5f, Theme.MUTED, false);
            age.setGravity(Gravity.END);
            card.addView(age, a.kit.lp(-1, -2));
        }
        return card;
    }

    private View salesRangeSelector(final LinearLayout card, final LinearLayout amountPanel,
                                    final Data data, final HomeSalesChart.Area chart,
                                    final android.animation.ValueAnimator[] amountAnimator) {
        final View[] selector = new View[1];
        selector[0] = a.kit.segmentedControl(SALES_RANGES, selectedSalesRange, null,
                Theme.SECTION_SALES, index -> {
                    selectedSalesRange = index;
                    renderSalesSummary(amountPanel, data, index, true, amountAnimator);
                    if (data != null && data.salesSnapshot != null && data.salesSnapshot.isAvailable()) {
                        String end = snapshotDay(data);
                        chart.setSelectedDate(index == 1 ? Jalali.addDays(end, -1) : end);
                    }
                    int position = card.indexOfChild(selector[0]);
                    if (position >= 0) {
                        View old = selector[0];
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) old.getLayoutParams();
                        card.removeViewAt(position);
                        selector[0] = salesRangeSelector(card, amountPanel, data, chart, amountAnimator);
                        card.addView(selector[0], position, params);
                    }
                });
        selector[0].setTag("sales-range-selector");
        return selector[0];
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
                    ? "فروش در این دریافت در دسترس نیست" : "دادهٔ فروش موجود نیست";
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
        TextView unit = a.kit.text(ir.meelano.manager.core.Money.unitName(), 10.5f, Theme.MUTED, false);
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
                10.5f, Theme.MUTED, false);
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
            TextView noBaseline = a.kit.text("مبنای مقایسه کافی نیست", 9.5f, Theme.MUTED, false);
            noBaseline.setGravity(Gravity.END);
            detail.addView(noBaseline, a.kit.lp(-2, -2));
        }
        panel.addView(detail, a.kit.lp(-1, -2));
        TextView comparison = a.kit.text(display.comparisonLabel, 9.5f, Theme.MUTED, false);
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
        final LinearLayout card = a.kit.card(Theme.SECTION_MARKET);
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
        try { if (wJson != null) w = MarketWeather.parseWeather(wJson); } catch (Exception e) { Diag.warn("Home:parseWeather", e); }

        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(a.kit.text("بازار و هوای امروز", 14f,
                Theme.sectionText(Theme.SECTION_MARKET), true), a.kit.wlp(1f));
        android.widget.Button refresh = a.kit.btnGhost("⟳", Theme.GOLD, v -> a.nav("home"));
        refresh.setContentDescription("تازه‌سازی قیمت بازار و آب‌وهوا");
        refresh.setMinWidth(Theme.dp(48));
        refresh.setMinHeight(Theme.dp(48));
        head.addView(refresh, a.kit.lp(Theme.dp(48), Theme.dp(48)));
        head.addView(a.kit.space(4));
        String marketState = rates == null || rates.isEmpty() ? "قیمت ناموجود"
                : rLive ? "قیمت آنلاین" : "قیمت ذخیره‌شده";
        int marketStateColor = rates == null || rates.isEmpty() ? Theme.WARNING : rLive ? Theme.SUCCESS : Theme.MUTED;
        head.addView(a.kit.pill(marketState, marketStateColor), a.kit.lp(-2, -2));
        card.addView(head, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));
        if (rates != null && rates.size() > 2) {
            TextView tickerHint = a.kit.text("برای دیدن نرخ سکه، افقی به چپ بکشید ←", 10f, Theme.MUTED, false);
            tickerHint.setSingleLine(true);
            tickerHint.setEllipsize(TextUtils.TruncateAt.END);
            tickerHint.setGravity(Gravity.END);
            card.addView(tickerHint, a.kit.lp(-1, -2));
            card.addView(a.kit.gap(4));
        }

        if (rates == null || rates.isEmpty()) {
            card.addView(a.kit.hint("قیمت‌ها در دسترس نیست؛ اتصال اینترنت را بررسی کنید."), a.kit.lp(-1, -2));
        } else {
            HorizontalScrollView ticker = new HorizontalScrollView(a);
            ticker.setHorizontalScrollBarEnabled(false);
            ticker.setFillViewport(false);
            ticker.setClipToPadding(false);
            ticker.setContentDescription("قیمت‌های بازار؛ برای دیدن همهٔ کالاها افقی پیمایش کنید");
            LinearLayout row = a.kit.h();
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < rates.size(); i++) {
                if (i > 0) row.addView(a.kit.space(8));
                int screenDp = Math.round(a.getResources().getDisplayMetrics().widthPixels
                        / a.getResources().getDisplayMetrics().density);
                // Show a slim edge of the third tile so horizontal scrolling is discoverable.
                int tileWidth = Math.max(116, Math.min(156, (screenDp - 96) / 2));
                row.addView(rateTile(rates.get(i)), new LinearLayout.LayoutParams(Theme.dp(tileWidth), -2));
            }
            ticker.addView(row, new android.view.ViewGroup.LayoutParams(-2, -2));
            card.addView(ticker, a.kit.lp(-1, -2));
        }

        if (w != null) {
            card.addView(a.kit.gap(8));
            LinearLayout wr = a.kit.h();
            wr.setGravity(Gravity.CENTER_VERTICAL);
            int wRes = w.code <= 1 ? R.drawable.mi_light_mode
                    : w.code <= 3 ? R.drawable.mi_wb_twilight
                    : (w.code >= 95 ? R.drawable.mi_bolt
                    : (w.code >= 51 ? R.drawable.mi_beach_access : R.drawable.mi_wb_twilight));
            wr.addView(a.kit.iconBadge(wRes, Theme.SECTION_MARKET, 36),
                    a.kit.lp(Theme.dp(36), Theme.dp(36)));
            wr.addView(a.kit.space(8));
            LinearLayout wc = a.kit.v();
            TextView line = a.kit.text(weatherPlace + " • " + HomeNumberFormatter.integer(Math.round(w.temp)) + "°  "
                    + MarketWeather.describe(w.code), 12f, Theme.TEXT, true);
            line.setMaxLines(1);
            line.setEllipsize(TextUtils.TruncateAt.END);
            wc.addView(line, a.kit.lp(-1, -2));
            LinearLayout weatherMeta = a.kit.h();
            weatherMeta.setGravity(Gravity.CENTER_VERTICAL);
            weatherMeta.addView(a.kit.text("رطوبت " + HomeNumberFormatter.percent(w.humidity), 10.5f, Theme.MUTED, false),
                    a.kit.wlp(1f));
            weatherMeta.addView(a.kit.pill(wLive ? "آب‌وهوا زنده" : "آب‌وهوا ذخیره‌شده",
                    wLive ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
            wc.addView(weatherMeta, a.kit.lp(-1, -2));
            wr.addView(wc, a.kit.wlp(1f));
            card.addView(wr, a.kit.lp(-1, -2));
        } else {
            boolean locationEnabled = MarketWeather.weatherLocationEnabled(a);
            boolean locationGranted = ir.meelano.manager.core.DeviceLocation.granted(a);
            String weatherHint = !locationEnabled
                    ? "آب‌وهوا بر اساس موقعیت خاموش است؛ برای فعال‌سازی، «استفاده از موقعیت من» را انتخاب کنید. تا آن زمان موقعیت شما ارسال نمی‌شود."
                    : !locationGranted
                    ? "مجوز موقعیت در دسترس نیست؛ بدون اجازهٔ شما هیچ موقعیتی ارسال نمی‌شود."
                    : "موقعیت تازه‌ای به‌دست نیامد؛ برای تلاش دوباره تازه‌سازی کنید. موقعیت فقط برای دریافت آب‌وهوا استفاده می‌شود.";
            card.addView(a.kit.hint(weatherHint), a.kit.lp(-1, -2));
        }

        boolean locationEnabled = MarketWeather.weatherLocationEnabled(a);
        boolean locationGranted = ir.meelano.manager.core.DeviceLocation.granted(a);
        boolean removeLocation = locationEnabled && locationGranted;
        android.widget.Button loc = a.kit.btnGhost(removeLocation
                        ? "خاموش‌کردن موقعیت" : "📍 استفاده از موقعیت من",
                Theme.GOLD, v -> {
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
                ? "خاموش‌کردن آب‌وهوا با موقعیت و حذف دادهٔ ذخیره‌شده؛ مجوز سیستم‌عامل را می‌توان در تنظیمات گوشی لغو کرد."
                : "فعال‌کردن آب‌وهوا با موقعیت دستگاه؛ بدون اجازهٔ شما موقعیتی ارسال نمی‌شود.");
        loc.setSingleLine(true);
        loc.setTextSize(11f * Theme.fontScale());
        loc.setPadding(Theme.dp(10), Theme.dp(4), Theme.dp(10), Theme.dp(4));
        loc.setMinHeight(Theme.dp(48));
        LinearLayout.LayoutParams lp = a.kit.lp(-2, -2);
        lp.setMargins(0, Theme.dp(6), 0, 0);
        lp.gravity = Gravity.END;
        card.addView(loc, lp);

        String stamp = rates != null && !rates.isEmpty() ? rates.get(0).asOf : "";
        card.addView(a.kit.goldHairline(), a.kit.lp(-1, Theme.dp(1)));
        card.addView(a.kit.gap(6));
        String changeBasis = rates != null && !rates.isEmpty() && rates.get(0).comparesWithPreviousFetch
                ? "تغییر نسبت به دریافت قبلی" : "درصد تغییر اعلامی TGJU";
        card.addView(a.kit.hint("منبع قیمت: TGJU · واحد: تومان · " + changeBasis
                + "\nزمان منبع: "
                + HomeNumberFormatter.localizeText(stamp == null || stamp.isEmpty() ? "نامشخص" : stamp)
                + " · دریافت برنامه: " + marketFreshness(MarketWeather.ratesKey(), rLive)), a.kit.lp(-1, -2));
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
        // The market card belongs to the manager's board; icons are vector and 24dp duotone.
        String label = r.label == null ? "" : r.label;
        int tileIcon = label.contains("دلار") ? R.drawable.mi_trending_up
                : (label.contains("طلا") ? R.drawable.mi_diamond : R.drawable.mi_account_balance_wallet);
        // Prices are always in toman (the source's unit), whatever unit the rest of the app uses.
        // A zero price means the source has no figure — never show it as a real «۰ تومان».
        boolean hasPrice = r.toman > 0;
        LinearLayout tile = (LinearLayout) a.kit.kpiChip(label,
                hasPrice ? HomeNumberFormatter.rounded(r.toman) : "ناموجود",
                hasPrice ? "تومان" : "", Theme.SECTION_MARKET, tileIcon);
        if (hasPrice) {
            int direction = Math.abs(r.changePct) < 0.05 ? 0 : r.changePct > 0 ? 1 : -1;
            int tone = direction > 0 ? Theme.SUCCESS : direction < 0 ? Theme.DANGER : Theme.MUTED;
            String badgeText = direction == 0 ? "بدون تغییر"
                    : HomeNumberFormatter.percentMagnitude(r.changePct);
            String comparisonBasis = r.comparesWithPreviousFetch
                    ? "نسبت به دریافت قبلی" : "بر اساس تغییر اعلام‌شده توسط TGJU";
            String spokenDelta = direction == 0 ? "بدون تغییر " + comparisonBasis
                    : (direction > 0 ? "افزایش " : "کاهش ")
                    + HomeNumberFormatter.decimal(Math.abs(r.changePct)) + " درصد " + comparisonBasis;
            View badge = a.kit.deltaBadge(badgeText, direction, tone, spokenDelta);
            LinearLayout.LayoutParams dp = a.kit.lp(-2, -2);
            dp.gravity = Gravity.CENTER_HORIZONTAL;
            dp.topMargin = Theme.dp(6);
            tile.addView(badge, dp);
        }
        String comparisonBasis = r.comparesWithPreviousFetch
                ? "نسبت به دریافت قبلی" : "بر اساس تغییر اعلام‌شده توسط TGJU";
        String trendDescription = !hasPrice ? "قیمت در دسترس نیست"
                : Math.abs(r.changePct) < 0.05 ? "بدون تغییر " + comparisonBasis
                : (r.changePct > 0 ? "افزایش " : "کاهش ")
                + HomeNumberFormatter.decimal(Math.abs(r.changePct)) + " درصد " + comparisonBasis;
        String previousPrice = r.comparesWithPreviousFetch
                ? "؛ نرخ قبلی " + HomeNumberFormatter.rounded(r.previousToman) + " تومان" : "";
        tile.setContentDescription(label + "، " + (hasPrice
                ? HomeNumberFormatter.rounded(r.toman) + " تومان، " + trendDescription + previousPrice
                : trendDescription));
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
        content.addView(status, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(greetingHero(d), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        if (RoleStore.allowed(a, "sales")) {
            content.addView(salesHero(d, syncState), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(10));
        }
        LinearLayout kpiSection = a.kit.v();
        secKpis(kpiSection, d);
        if (kpiSection.getChildCount() > 0) content.addView(kpiSection, a.kit.lp(-1, -2));

        // The former "dashboard details" accordion is gone. Operational and analytical
        // sections now have a clear, compact hierarchy and remain directly discoverable.
        for (String key : orderedKeys()) {
            if ("sales_insight".equals(key) || "kpis".equals(key) || "returns".equals(key)
                    || "due".equals(key) || "eod".equals(key))
                continue; // Sales and returns are consolidated; cheque due is surfaced by AlertEngine.
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key);
            if (block.getChildCount() == 0) continue;
            content.addView(block, a.kit.lp(-1, -2));
        }
        // Market/weather stays independent of Atiran SQL and below the business snapshot.
        content.addView(marketCard(), a.kit.lp(-1, -2));
        renderNotes(content, d.notes);
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

    private void renderHomeSection(LinearLayout content, Data d, String key) {
        if ("kpis".equals(key)) secKpis(content, d);
        else if ("alerts".equals(key)) secAlerts(content, d);
        else if ("sales_insight".equals(key)) secSalesInsight(content, d);
        else if ("cash_mix".equals(key)) secCashMix(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
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
            salesOrder.add("kpis");
            salesOrder.add("alerts");
            salesOrder.add("sales_insight");
            return salesOrder;
        }
        List<String> out = new ArrayList<>();
        String[] known = {"kpis", "alerts", "due", "returns", "sales_insight", "cash_mix", "eod"};
        for (String k : a.settings.homeOrder().split(",")) {
            String t = k == null ? "" : k.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                for (String k2 : known) if (k2.equals(t)) { out.add(t); break; }
            }
        }
        for (String k2 : known) if (!out.contains(k2)) out.add(k2);
        return out;
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
        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.sectionHeader("شاخص‌های مالی", R.drawable.mi_account_balance_wallet,
                Theme.GOLD, null, null), a.kit.lp(-1, -2));
        TextView subtitle = a.kit.text("خرید، دریافت و پرداخت " + snapshotLabel(d) + " · برگشتی در ۳۰ روز اخیر",
                9.5f, Theme.MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(subtitle, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Theme.dp(18));
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout strip = a.kit.h();
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        int tileWidth = ir.meelano.manager.ui.Ui.widthDp(a) >= 600 ? 168 : 148;
        for (int i = 0; i < tiles.size(); i++) {
            View tile = a.kit.kpiTile(tiles.get(i));
            LinearLayout.LayoutParams p = a.kit.lp(Theme.dp(tileWidth), -2);
            if (i < tiles.size() - 1) p.setMarginEnd(Theme.dp(8));
            strip.addView(tile, p);
        }
        scroll.addView(strip, new HorizontalScrollView.LayoutParams(-2, -2));
        scroll.setContentDescription("شاخص‌های مالی؛ برای دیدن همهٔ شاخص‌ها افقی بکشید");
        card.addView(scroll, a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    private Kit.Kpi homeKpi(String label, double amountRials, long documents, String countLabel,
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
                sub = "۰٪" + comparisonBasis;
            }
        } else {
            sub = "مبنای مقایسه کافی نیست";
        }
        Kit.Kpi tile = new Kit.Kpi(label,
                available ? ir.meelano.manager.core.Money.toUnit(amountRials) : Double.NaN,
                amount + (unit.isEmpty() ? "" : " " + unit), sub, accent, action);
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

    /** Select a stable jewel hue by section; outcome colors stay reserved for deltas only. */
    private int dashboardAccent(int vector) {
        if (vector == R.drawable.mi_shopping_cart || vector == R.drawable.mi_add_shopping_cart)
            return Theme.SECTION_PURCHASE;
        if (vector == R.drawable.mi_receipt_long || vector == R.drawable.mi_cheque)
            return Theme.SECTION_RECEIVE;
        if (vector == R.drawable.mi_payments || vector == R.drawable.mi_trending_down)
            return Theme.SECTION_PAY;
        if (vector == R.drawable.mi_assignment_return) return Theme.SECTION_RETURNS;
        if (vector == R.drawable.mi_warning || vector == R.drawable.mi_error)
            return Theme.SECTION_ALERT;
        if (vector == R.drawable.mi_diamond || vector == R.drawable.mi_trending_up
                || vector == R.drawable.mi_account_balance_wallet)
            return Theme.SECTION_MARKET;
        if (vector == R.drawable.mi_inventory_2 || vector == R.drawable.mi_grid_view
                || vector == R.drawable.mi_warehouse)
            return Theme.SECTION_WAREHOUSE;
        return Theme.SECTION_SALES;
    }

    /** Vector icon in a quiet accent circle; data cards never use the 3D artwork set. */
    private ImageView dashboardArt(int iconVector) {
        int accent = dashboardAccent(iconVector);
        ImageView image = a.kit.icon(iconVector, Theme.sectionText(accent), 24);
        image.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 26 : 40)));
        int inset = Theme.dp(6);
        image.setPadding(inset, inset, inset, inset);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    /** Dashboard sections use the same accessible SectionHeader as every other screen. */
    private View dashboardSectionHead(String title, int iconVector, int accent,
                                      String actionLabel, View.OnClickListener action) {
        return a.kit.sectionHeader(title, iconVector, accent, actionLabel, action);
    }

    /** Section heading: a duotone icon, title, optional subtitle and action. */
    private View boardHead(String title, String subtitle, int iconVector, View end) {
        LinearLayout box = a.kit.v();
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(3), Theme.dp(4), Theme.dp(3), Theme.dp(7));
        int accent = dashboardAccent(iconVector);
        row.addView(dashboardArt(iconVector),
                new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
        row.addView(a.kit.space(8));
        TextView label = a.kit.text(title, 15f, Theme.sectionText(accent), true);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, a.kit.wlp(1f));
        if (end != null) row.addView(end, a.kit.lp(-2, -2));
        box.addView(row, a.kit.lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = a.kit.text(subtitle, 11f, Theme.MUTED, false);
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
        chip.setPadding(Theme.dp(12), Theme.dp(2), Theme.dp(12), Theme.dp(2));
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
        row.setBackground(Theme.kpi(accent));
        row.setPadding(Theme.dp(12), Theme.dp(10), Theme.dp(12), Theme.dp(10));
        row.addView(dashboardArt(iconVector), a.kit.lp(Theme.dp(42), Theme.dp(42)));
        row.addView(a.kit.space(10));
        LinearLayout copy = a.kit.v();
        TextView l = a.kit.text(label, 11f, Theme.MUTED, false);
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
            LinearLayout tile = a.kit.v();
            tile.setBackground(Theme.kpi(accent));
            tile.setPadding(Theme.dp(10), Theme.dp(12), Theme.dp(10), Theme.dp(12));
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView ic = dashboardArt(m.icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36));
            ip.gravity = Gravity.CENTER_HORIZONTAL;
            ip.bottomMargin = Theme.dp(6);
            tile.addView(ic, ip);
            TextView l = a.kit.text(m.label, 11f, Theme.MUTED, false);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(l, a.kit.lp(-1, -2));
            tile.addView(a.kit.amountLine(m.value, 15f, Gravity.CENTER), a.kit.lp(-1, -2));
            if (m.note != null && !m.note.isEmpty()) {
                int noteColor = m.note.contains("ناقص") ? Theme.MUTED : Theme.SUCCESS;
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
        LinearLayout card = a.kit.card(Theme.SECTION_ALERT);
        card.setPadding(Theme.dp(13), Theme.dp(10), Theme.dp(13), Theme.dp(12));
        card.addView(a.kit.sectionHeader("مرکز توجه", R.drawable.mi_priority_high,
                Theme.SECTION_ALERT, null, null), a.kit.lp(-1, -2));

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
            focus.setBackground(Theme.kpi(Theme.SECTION_ALERT));
            LinearLayout heading = a.kit.h();
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = a.kit.text(primary.title, 12.5f, Theme.TEXT, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            heading.addView(title, a.kit.wlp(1f));
            heading.addView(statusChip(attentionLabel(primary.priority), primary.accent),
                    a.kit.lp(-2, -2));
            focus.addView(heading, a.kit.lp(-1, -2));
            TextView detail = a.kit.text(primary.detail, 10.5f, Theme.MUTED, false);
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
        LinearLayout card = a.kit.card(Theme.SECTION_RETURNS);
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
            LinearLayout trend = a.kit.card(Theme.SECTION_SALES);
            trend.addView(boardHead("روند ۱۴ روزه فروش تا " + snapshotLabel(d),
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
            Charts.Area chart = new Charts.Area(a);
            TextView caption = a.kit.text(points.isEmpty() ? "" : lastCap(points), 10f, Theme.MUTED, false);
            chart.setData(points, Theme.SECTION_SALES, HomeNumberFormatter::chartMoney);
            chart.setListener((idx, point) -> caption.setText(HomeNumberFormatter.localizeText(point.label) + " • " + HomeNumberFormatter.money(point.value)));
            trend.addView(chart, new LinearLayout.LayoutParams(-1, Theme.dp(126)));
            trend.addView(caption, a.kit.lp(-1, -2));
            a.kit.addCard(content, trend);
        }

        if (hasProducts || hasMonths) {
            LinearLayout card = a.kit.card(Theme.SECTION_SALES);
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
        LinearLayout card = a.kit.card(Theme.SECTION_RECEIVE);
        card.setPadding(Theme.dp(13), Theme.dp(10), Theme.dp(13), Theme.dp(11));
        card.addView(a.kit.sectionHeader("ترکیب دریافت", R.drawable.mi_receipt_long,
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
        item.setBackground(Theme.kpi(Theme.SECTION_RECEIVE));
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
        item.addView(a.kit.text(HomeNumberFormatter.compactMoney(amount), 8.5f, Theme.MUTED, false),
                a.kit.lp(-1, -2));
        item.setContentDescription(label + "، "
                + HomeNumberFormatter.percent(denominator > 0 ? amount * 100d / denominator : 0)
                + "، " + HomeNumberFormatter.money(amount));
        return item;
    }

    private View insightSubhead(String title, int icon, int accent) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art = dashboardArt(icon);
        row.addView(art, a.kit.lp(Theme.dp(32), Theme.dp(32)));
        row.addView(a.kit.space(6));
        row.addView(a.kit.text(title, 11f, Theme.sectionText(accent), true), a.kit.wlp(1f));
        return row;
    }

    private View insightMetric(String label, String value, int accent) {
        LinearLayout tile = a.kit.v();
        tile.setPadding(Theme.dp(9), Theme.dp(7), Theme.dp(9), Theme.dp(7));
        tile.setBackground(Theme.kpi(accent));
        TextView title = a.kit.text(label, 9.5f, Theme.MUTED, false);
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
            LinearLayout c = a.kit.card(Theme.SECTION_ALERT);
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
        LinearLayout c = a.kit.card(Theme.SECTION_SALES);
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

    /** Fill missing days with zero for the 14-day chart, sourced only from SalesSnapshot. */
    private List<Charts.Point> filled(SalesSnapshot snapshot, String endDay) {
        List<Charts.Point> out = new ArrayList<>();
        if (snapshot == null || !snapshot.isAvailable()) return out;
        String to = Jalali.disp(endDay);
        if (to.isEmpty()) to = Jalali.todayStr();
        for (SalesSnapshot.Day day : snapshot.dailySeries(to, 14))
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
