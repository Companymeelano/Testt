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
        List<Row> purchaseDaily = liveL(token, L, d.notes, "خرید امروز",
                () -> Repo.exec(L.c, Queries.factorDaily(L.m, false, todayRange.from, todayRange.to)));
        d.buyDay = HomeRange.aggregateDay(purchaseDaily, todayRange.to);
        d.inDay = liveL(token, L, d.notes, "دریافت امروز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 0, todayRange.to)));
        d.outDay = liveL(token, L, d.notes, "پرداخت امروز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 1, todayRange.to)));
        d.overdue = liveL(token, L, d.notes, "معوق‌ها", () -> Repo.exec(L.c, Queries.overdueInvoices(L.m, 5)));
        String stFrom = Jalali.addDays(to, -29);
        Filter rf = new Filter();
        rf.from = stFrom;
        rf.to = to;
        d.returns = liveL(token, L, d.notes, "برگشتی", () -> Repo.exec(L.c, MasterQueries.returnsByKind(L.m, rf)));
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
        s.inDay = g.inDay;
        s.outDay = g.outDay;
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
                    .append("|trendok=").append(d.salesSnapshot != null && d.salesSnapshot.isAvailable() ? "1" : "0")
                    .append("|trendfrom=").append(esc(d.salesHistoryFrom == null ? "" : d.salesHistoryFrom))
                    .append("|stockok=").append(d.stockAlertsAvailable ? "1" : "0")
                    .append("|reorderok=").append(d.reorderPointAvailable ? "1" : "0")
                    .append("|priceok=").append(d.purchasePriceTrendAvailable ? "1" : "0")
                    .append("|dueinok=").append(d.dueInAvailable ? "1" : "0")
                    .append("|dueoutok=").append(d.dueOutAvailable ? "1" : "0");
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
        // The requested live market/weather strip sits directly under the manager greeting.
        // It is independent of Atiran SQL, so it remains visible when the business link is offline.
        content.addView(marketCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // The former "dashboard details" accordion is gone. Operational and analytical
        // sections now have a clear, compact hierarchy and remain directly discoverable.
        for (String key : orderedKeys()) {
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key);
            if (block.getChildCount() == 0) continue;
            content.addView(block, a.kit.lp(-1, -2));
        }
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
        LinearLayout card = a.kit.card(Theme.GOLD);
        // Every daily KPI uses the same explicit HomeRange.today() date; do not label
        // one metric with its own latest-transaction day while comparing another date.
        String kpiDay = dayOf(d.salesDay != null ? d.salesDay : d.inDay);
        card.addView(dashboardSectionHead(kpiDay.isEmpty() ? "گردش روز" : "گردش " + kpiDay,
                R.drawable.mi_account_balance_wallet,
                Theme.GOLD, "فروش ›", navAction("sales")), a.kit.lp(-1, -2));
        card.addView(a.kit.text(isSalesManager()
                        ? "فروش امروز تا پایان شب ناقص است؛ لمس شاخص، ریز همان روز را باز می‌کند."
                        : "چهار شاخص مالی امروز؛ فروش روز جاری تا ساعت ۲۴ کامل نمی‌شود.",
                10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        // The board reads خرید، فروش، پرداخت، دریافت - the order of the reference.
        // Tiles whose target screen the current role cannot open are not shown at all:
        // each person sees exactly their own items (the tap opens that day of the target).
        java.util.List<Kit.Kpi> tiles = new java.util.ArrayList<>();
        String[] tileTargets = {"buy", "sales", "dar_out", "dar_in"};
        String[] tileTitles = {"خرید", "فروش", "پرداخت", "دریافت"};
        Row[] tileRows = {d.buyDay, d.salesDay, d.outDay, d.inDay};
        String[] tileCountKeys = {"docs", "docs", "count", "count"};
        String[] tileCountLabels = {"فاکتور", "فاکتور", "قبض", "قبض"};
        int[] icons = {R.drawable.mi_shopping_cart, R.drawable.mi_point_of_sale,
                R.drawable.mi_payments, R.drawable.mi_receipt_long};
        int[] tileAccents = {Theme.SECTION_PURCHASE, Theme.SECTION_SALES,
                Theme.SECTION_PAY, Theme.SECTION_RECEIVE};
        for (int i = 0; i < tileTargets.length; i++) {
            if (isSalesManager() && !"sales".equals(tileTargets[i])) continue;
            if (!RoleStore.allowed(a, tileTargets[i])) continue;
            Kit.Kpi kpi = dayKpi(tileTitles[i], tileTargets[i], tileRows[i],
                    tileCountKeys[i], tileCountLabels[i], tileAccents[i]);
            kpi.icon = icons[i];
            kpi.emeraldIcon = true;
            // Document counts are informational; an in-progress day is not a success state.
            kpi.subColor = Theme.MUTED;
            tiles.add(kpi);
        }
        if (!tiles.isEmpty()) card.addView(a.kit.kpiGrid(tiles, 2), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);

        // The comparison card: the day against the recent days, as compact tiles.
        if (d.salesSnapshot != null && d.salesSnapshot.isAvailable()) {
            String compareDay = snapshotDay(d);
            boolean running = compareDay.equals(Jalali.todayStr());
            int elapsedMinute = running ? currentMinuteOfDay() : SalesComparison.MINUTES_PER_DAY;
            SalesComparison.DayResult dayComparison = SalesComparison.evaluate(d.salesSnapshot, compareDay,
                    elapsedMinute, SalesComparison.DEFAULT_COMPARABLE_WEEKS);
            double todaySales = dayComparison.actualRials;
            HomeRange yesterdayRange = HomeRange.yesterday(compareDay);
            HomeRange last7Range = HomeRange.last7Days(compareDay);
            double yesterday = salesOnDay(d, yesterdayRange.to);
            double last7Sales = d.salesSnapshot.salesRialsBetween(last7Range.from, last7Range.to);
            double avg30 = averageSalesBefore(d, compareDay, 30);
            String weekday = Jalali.weekday(compareDay);
            LinearLayout compare = a.kit.card(Theme.SECTION_SALES);
            compare.addView(boardHead("فروش " + snapshotLabel(d) + " در مقایسه",
                    "میانگین و مقایسه با روزهای قبل", R.drawable.mi_bar_chart,
                    navChip("گزارش فروش", "sales")), a.kit.lp(-1, -2));
            List<Mini> cmp = new ArrayList<>();
            String todayNote = running ? partialTodayNote()
                    : (dayComparison.hasBaseline() ? "مقایسهٔ روز کامل" : "بدون مبنای هم‌روز");
            cmp.add(new Mini(R.drawable.mi_point_of_sale, snapshotLabel(d),
                    HomeNumberFormatter.compactMoney(todaySales), todayNote, Theme.TEXT));
            cmp.add(new Mini(R.drawable.mi_calendar_month, "دیروز", HomeNumberFormatter.compactMoney(yesterday), null, Theme.TEXT));
            cmp.add(new Mini(R.drawable.mi_bar_chart, "فروش ۷ روز اخیر", HomeNumberFormatter.compactMoney(last7Sales),
                    running ? "روز جاری ناقص است" : null, Theme.TEXT));
            cmp.add(new Mini(R.drawable.mi_bar_chart, "میانگین ۳۰ روز", HomeNumberFormatter.compactMoney(avg30), null, Theme.TEXT));
            cmp.add(new Mini(R.drawable.mi_calendar_month, "میانگین ۴ " + weekday + " گذشته",
                    HomeNumberFormatter.compactMoney(dayComparison.sameWeekdayAverageRials), "روزهای هم‌هفته", Theme.TEXT));
            if (dayComparison.projectionAvailable) {
                String forecastNote = dayComparison.hasBaseline()
                        ? "در برابر " + weekday + "ها: " + HomeNumberFormatter.signedPercent(dayComparison.changePercent())
                        : "مبنای هم‌روز کافی نیست";
                if (running) forecastNote = "برآورد ناقص · " + forecastNote;
                cmp.add(new Mini(R.drawable.mi_trending_up, "پیش‌بینی پایان روز",
                        HomeNumberFormatter.compactMoney(dayComparison.projectedRials), forecastNote, Theme.TEXT));
            }
            compare.addView(miniGrid(cmp, 2, Theme.SECTION_SALES), a.kit.lp(-1, -2));
            a.kit.addCard(content, compare);
        }
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

    private void secAlerts(LinearLayout content, Data d) {
        // ---- alerts ----
        LinearLayout alerts = a.kit.card(Theme.SECTION_ALERT);
        LinearLayout rows = a.kit.v();
        int n = addEngineAlerts(rows, d, 0);
        n = alert(rows, d, n);
        List<String> unavailableSources = unavailableAlertSources(d);
        List<FollowUps.Due> dues = FollowUps.dueToday(a);
        if (!dues.isEmpty() && RoleStore.allowed(a, "customers")) {
            final FollowUps.Due fd = dues.get(0);
            String who = dues.size() == 1 ? fd.note.name : HomeNumberFormatter.integer(dues.size()) + " یادداشت";
            rows.addView(a.kit.alertRow("پیگیری مشتری", who + " • سررسید یادداشت",
                    Theme.SECTION_ALERT, v -> gotoCustomer(fd.code)), padTop(n));
            n++;
        }
        // The badge distinguishes a clean check from a partial check; missing sources are
        // never presented as confirmation that the business has no alerts.
        boolean incomplete = !unavailableSources.isEmpty();
        String badge = n == 0 ? (incomplete ? "بررسی ناقص" : "بدون هشدار")
                : HomeNumberFormatter.integer(n) + " مورد";
        int badgeTone = n == 0 && !incomplete ? Theme.SUCCESS : Theme.WARNING;
        alerts.addView(boardHead("هشدار امروز", null, R.drawable.mi_warning,
                statusChip(badge, badgeTone)), a.kit.lp(-1, -2));
        // Each alert view still belongs to the temporary rows container. Detach it before
        // inserting it into the card; Android rejects a ViewGroup child with two parents.
        while (rows.getChildCount() > 0) {
            View row = rows.getChildAt(0);
            rows.removeViewAt(0);
            alerts.addView(row);
        }
        if (n == 0) alerts.addView(a.kit.text(incomplete
                        ? "وضعیت بدون هشدار تأیید نشد؛ بعضی داده‌ها در دسترس نیستند."
                        : "فعلاً هشدار مهمی وجود ندارد.",
                11.5f, incomplete ? Theme.WARNING : Theme.MUTED, false), a.kit.lp(-1, -2));
        if (incomplete) alerts.addView(a.kit.hint("بررسی ناقص بخش‌های: "
                        + joinAlertSources(unavailableSources)), a.kit.lp(-1, -2));
        a.kit.addCard(content, alerts);
    }

    /** Convert the dashboard's available, read-only query results into shared alert rules. */
    private int addEngineAlerts(LinearLayout rows, Data d, int firstIndex) {
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
        int shown = 0;
        int index = firstIndex;
        for (AlertEngine.Alert item : visible) {
            if (shown >= 6) break;
            rows.addView(a.kit.alertRow(engineAlertTitle(item), engineAlertDetail(item),
                    severityColor(item.severity), v -> openHomeAlert(item, d)), padTop(index++));
            shown++;
        }
        if (visible.size() > shown) {
            rows.addView(a.kit.hint("+" + HomeNumberFormatter.integer(visible.size() - shown)
                    + " هشدار دیگر در فهرست امروز"), padTop(index));
        }
        return index - firstIndex;
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

    /** Payment mix is easier to scan as four proportional rails than as another large chart. */
    private void secCashMix(LinearLayout content, Data d) {
        if (d.inDay == null || d.inDay.d("total") <= 0) return;
        LinearLayout card = a.kit.card(Theme.SECTION_RECEIVE);
        card.addView(dashboardSectionHead("ترکیب دریافت " + dayOf(d.inDay), R.drawable.mi_receipt_long,
                Theme.SECTION_RECEIVE, "دریافت‌ها ›", navAction("dar_in")), a.kit.lp(-1, -2));
        double total = d.inDay.d("total");
        double cash = d.inDay.d("cash"), pos = d.inDay.d("pos"),
                havaleh = d.inDay.d("havaleh"), cheque = d.inDay.d("cheque");
        card.addView(a.kit.text("جمع " + HomeNumberFormatter.compactMoney(total),
                11f, Theme.TEXT, true), a.kit.lp(-1, -2));
        addPaymentMix(card, "نقد", cash, total, Theme.SECTION_RECEIVE);
        addPaymentMix(card, "کارت", pos, total, Theme.SECTION_RECEIVE);
        addPaymentMix(card, "حواله", havaleh, total, Theme.SECTION_RECEIVE);
        addPaymentMix(card, "چک", cheque, total, Theme.SECTION_RECEIVE);
        // The booked voucher total (dar.mab) can carry a discount or other items beyond the
        // four means of payment — show the difference instead of leaving the rails unexplained.
        double other = total - (cash + pos + havaleh + cheque);
        if (Math.abs(other) >= 500_000) {
            addPaymentMix(card, other > 0 ? "سایر (تخفیف، کسورات و اقلام دیگر)" : "تعدیل/اضافهٔ سند",
                    Math.abs(other), total, Theme.SECTION_RECEIVE);
            card.addView(a.kit.text("تفاوت مجموع اجزا با کل قبض، ناشی از تخفیف یا اقلام دیگر است.",
                    9f, Theme.MUTED, false), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    private void addPaymentMix(LinearLayout card, String label, double amount, double total, int color) {
        if (amount <= 0) return;
        String share = total > 0 ? " · " + HomeNumberFormatter.percent(amount * 100.0 / total) : "";
        card.addView(a.kit.progressLine(label, HomeNumberFormatter.compactMoney(amount) + share, amount / total, color),
                a.kit.lp(-1, -2));
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

    private int alert(LinearLayout box, Data d, int n) {
        // overdue top-1
        if (d.overdue != null && !d.overdue.isEmpty() && RoleStore.allowed(a, "sales")) {
            Row o = d.overdue.get(0);
            long dd = o.l("days");
            // Without dif_date_alan the server sends −1: derive the delay from the due date.
            if (dd < 0) dd = Jalali.diffDays(o.s("dueDate"), Jalali.todayStr());
            String days = dd >= 0 ? " • تأخیر " + HomeNumberFormatter.integer(dd) + " روز" : "";
            box.addView(a.kit.alertRow("فاکتور معوق", o.s("party") + " • " + HomeNumberFormatter.money(o.d("amount")) + days,
                    Theme.GOLD, v -> gotoUnsettled()), padTop(n));
            n++;
        }
        // Company liquidity is visible only to the company manager.
        if (!isSalesManager() && RoleStore.allowed(a, "buy") && d.salesDay != null && d.buyDay != null
                && d.salesDay.d("total") > 0 && d.buyDay.d("total") > d.salesDay.d("total") * 1.15) {
            box.addView(a.kit.alertRow("فشار نقدینگی", "خرید روز از فروش جلو زده؛ پرداخت‌ها را کنترل کنید.",
                    Theme.GOLD, v -> a.nav("buy")), padTop(n));
            n++;
        }
        return n;
    }

    private LinearLayout.LayoutParams padTop(int n) {
        LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
        p.setMargins(0, Theme.dp(8), 0, 0);
        return p;
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
