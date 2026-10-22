package ir.meelano.manager.screens;

import android.text.TextUtils;
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
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.FollowUps;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.RoleStore;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.R;
import ir.meelano.manager.core.Queries;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executive dashboard: greeting, alerts, day KPIs, trends, debtors, visitors, shortcuts. */
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
        Row salesDay = new Row();
        Row buyDay = new Row();
        Row inDay = new Row();
        Row outDay = new Row();
        Row custSum = new Row();
        List<Row> debtors = new ArrayList<>();
        List<Row> overdue = new ArrayList<>();
        List<Row> salesDaily = new ArrayList<>();
        List<Row> buyDaily = new ArrayList<>();
        List<Row> outDaily = new ArrayList<>();
        List<Row> inDaily = new ArrayList<>();
        List<Row> topItems = new ArrayList<>();
        List<Row> visitors = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
        /** Sales invoices split by confirmation state (تأییدشده / تأییدنشده). */
        List<Row> saleState = new ArrayList<>();
        /** Purchase invoices split the same way. */
        List<Row> buyState = new ArrayList<>();
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

    /**
     * The gold clock artwork (drawn without hands) with the hour and minute hands drawn live,
     * set to the phone's current time. Plain View: no drawable layering, so it always paints.
     */
    static final class ClockView extends View {
        private static final float IMG_W = 1104f, IMG_H = 616f;
        private static final float CX = 317f, CY = 309f;   // clock centre in artwork space
        private static final float RING = 168f;            // dial radius (vertical) in artwork space
        private static final float X_SQUASH = 0.79f;       // the dial is seen slightly turned
        private final android.graphics.Bitmap bmp;
        private final android.graphics.Paint art = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Paint fade = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint hand = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final android.graphics.RectF dst = new android.graphics.RectF();
        private final android.graphics.Paint rim = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF panel = new android.graphics.RectF();

        ClockView(android.content.Context c, android.graphics.Bitmap bmp) {
            super(c);
            this.bmp = bmp;
            hand.setStyle(android.graphics.Paint.Style.FILL);
            rim.setStyle(android.graphics.Paint.Style.STROKE);
            rim.setStrokeWidth(Theme.dp(1.6f));
            rim.setColor(Theme.GOLD);
            rim.setShadowLayer(Theme.dp(6), 0, 0, Theme.alpha(Theme.GOLD, 150));
            setLayerType(View.LAYER_TYPE_SOFTWARE, rim); // the glow needs a software layer
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            int h = getHeight();
            if (bmp == null || h <= 0) return;
            float s = h / IMG_H;
            float w = IMG_W * s;
            dst.set(0, 0, w, h);
            c.drawBitmap(bmp, null, dst, art);
            // Dissolve the artwork's right edge into the card colour so no box edge shows.
            fade.setShader(new android.graphics.LinearGradient(w * 0.6f, 0, w, 0,
                    0x000A0D13, 0xFF0A0D13, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(w * 0.6f, 0, w, h, fade);
            // Gold frame around the clock picture itself.
            float inset = Theme.dp(4);
            panel.set(inset, inset, w * 0.6f - inset / 2f, h - inset);
            c.drawRoundRect(panel, Theme.dp(14), Theme.dp(14), rim);

            java.util.Calendar now = java.util.Calendar.getInstance();
            float minute = now.get(java.util.Calendar.MINUTE) + now.get(java.util.Calendar.SECOND) / 60f;
            float hour = now.get(java.util.Calendar.HOUR) + minute / 60f;
            float cx = CX * s, cy = CY * s;
            drawHand(c, cx, cy, hour / 12f, RING * 0.52f * s, 5.2f * s, 0xFFFFE9A8);
            drawHand(c, cx, cy, minute / 60f, RING * 0.84f * s, 3.4f * s, 0xFFFFD97A);
            hand.setColor(0xFFFFF3CC);
            c.drawCircle(cx, cy, 6.5f * s, hand);
            hand.setColor(0xFF8A6418);
            c.drawCircle(cx, cy, 3.2f * s, hand);
        }

        private void drawHand(android.graphics.Canvas c, float cx, float cy, float turn, float len, float wid, int color) {
            double ang = turn * Math.PI * 2;
            float dx = (float) Math.sin(ang) * X_SQUASH, dy = (float) -Math.cos(ang);
            float tipX = cx + dx * len, tipY = cy + dy * len;
            float tailX = cx - dx * len * 0.18f, tailY = cy - dy * len * 0.18f;
            float nx = -dy * wid, ny = dx * wid;
            path.reset();
            path.moveTo(tipX, tipY);
            path.lineTo(cx + nx, cy + ny);
            path.lineTo(tailX, tailY);
            path.lineTo(cx - nx, cy - ny);
            path.close();
            hand.setColor(0x66000000);
            c.save();
            c.translate(wid * 0.6f, wid * 0.6f);
            c.drawPath(path, hand);
            c.restore();
            hand.setColor(color);
            c.drawPath(path, hand);
        }
    }

    private static android.graphics.Bitmap clockBitmap;

    /**
     * «در حال به‌روزرسانی» banner: clock artwork on the left, status text on the right.
     * Shown on every dashboard load, cached or not.
     */
    private View updateBanner(String savedTs) {
        return updateBanner(savedTs, false);
    }

    private View updateBanner(String savedTs, boolean done) {
        android.content.Context c = a;
        if (clockBitmap == null) {
            try {
                clockBitmap = android.graphics.BitmapFactory.decodeResource(c.getResources(), R.drawable.home_clock_face);
            } catch (Exception e) { Diag.ignored("Home:clockArt", e); }
        }
        FrameLayout box = new FrameLayout(c);
        android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E121A, 0xFF07090E});
        base.setCornerRadius(Theme.dp(22));
        base.setStroke(Theme.dp(1), Theme.alpha(Theme.GOLD, 120));
        box.setBackground(base);
        box.setMinimumHeight(Theme.dp(124));
        box.addView(new ClockView(c, clockBitmap), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        // Text sits on the right; the end side keeps room for the clock artwork.
        text.setPaddingRelative(Theme.dp(16), Theme.dp(14), Theme.dp(118), Theme.dp(14));
        if (done) {
            text.addView(a.kit.text("✓ داشبورد به‌روز است", 12f, Theme.GOLD_SOFT, true), new LinearLayout.LayoutParams(-1, -2));
            text.addView(a.kit.text("ساعت به‌روزرسانی: " + savedTs, 11f, Theme.MUTED, false), new LinearLayout.LayoutParams(-1, -2));
        } else {
            text.addView(a.kit.text("⟳ در حال به‌روزرسانی داشبورد…", 12f, Theme.TEXT, true), new LinearLayout.LayoutParams(-1, -2));
            if (savedTs != null && !savedTs.isEmpty()) {
                text.addView(a.kit.text("آخرین داده ذخیره‌شده: " + Money.fa(savedTs), 11f, Theme.MUTED, false), new LinearLayout.LayoutParams(-1, -2));
            }
        }
        box.addView(text, new FrameLayout.LayoutParams(-1, -2));
        return box;
    }

    @Override
    public void render(final LinearLayout content) {
        final int token = a.renderGen();
        // Smart first paint: the last saved dashboard appears at once and live data replaces it when ready.
        final String[] cachedTs = {""};
        final Data cached = loadCached(cachedTs);
        if (cached != null) {
            build(content, cached);
            // build() already put a banner on top: replace it with the updating state (one banner only).
            content.removeViewAt(0);
            content.addView(updateBanner(cachedTs[0]), 0);
        } else {
            content.removeAllViews();
            content.addView(updateBanner(""), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(10));
            content.addView(greetingHero(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(10));
            content.addView(a.kit.gap(6));
            content.addView(a.kit.loading("در حال دریافت داشبورد مدیریتی…"), a.kit.lp(-1, -2));
        }

        // Two groups run in parallel on their own connections (the runner has three workers).
        final Data shared = new Data();
        final int[] left = {2};
        final boolean[] kpiOk = {false};
        final String[] kpiErr = {null};
        final Runnable settle = () -> {
            if (a.renderGen() != token) return;
            if (kpiOk[0]) {
                saveCache(shared);
                build(content, shared);
            } else {
                showFailure(content, kpiErr[0], cached);
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
                done[0].run();
            }

            @Override
            public void fail(String faError) {
                shared.notes.put("نمودارها", faError == null ? "" : faError);
                done[0].run();
            }
        });
    }

    /** KPI group: day totals, customers, debtors, invoice states and returns. */
    private Data loadKpis(final int token, java.sql.Connection c0) throws Exception {
        final Link L = new Link(c0);
        Data d = new Data();
        final String to = Jalali.todayStr();
        d.snapshotDay = to;
        d.salesDay = liveL(token, L, d.notes, "فروش روز", () -> Repo.one(L.c, Queries.homeSalesDay(L.m)));
        d.buyDay = liveL(token, L, d.notes, "خرید روز", () -> Repo.one(L.c, Queries.homeBuyDay(L.m)));
        d.inDay = liveL(token, L, d.notes, "دریافت روز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 0)));
        d.outDay = liveL(token, L, d.notes, "پرداخت روز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 1)));
        d.custSum = liveL(token, L, d.notes, "خلاصه مشتریان", () -> Repo.one(L.c, MasterQueries.customersSummary(L.m)));
        d.debtors = liveL(token, L, d.notes, "بدهکاران", () -> Repo.exec(L.c, Queries.topDebtors(L.m, 5)));
        d.overdue = liveL(token, L, d.notes, "معوق‌ها", () -> Repo.exec(L.c, Queries.overdueInvoices(L.m, 5)));
        String stFrom = Jalali.addDays(to, -29);
        d.saleState = liveL(token, L, d.notes, "وضعیت فروش", () -> Repo.exec(L.c, Queries.factorStatusSummary(L.m, true, stFrom, to)));
        d.buyState = liveL(token, L, d.notes, "وضعیت خرید", () -> Repo.exec(L.c, Queries.factorStatusSummary(L.m, false, stFrom, to)));
        Filter rf = new Filter();
        rf.from = stFrom;
        rf.to = to;
        d.returns = liveL(token, L, d.notes, "برگشتی", () -> Repo.exec(L.c, MasterQueries.returnsByKind(L.m, rf)));
        if (d.salesDay == null && d.inDay == null && d.custSum == null && d.buyDay == null && d.outDay == null)
            throw new Exception(firstNote(d.notes));
        return d;
    }

    /** Chart group: daily series, top items, visitors and cheque due lists. */
    private Data loadCharts(final int token, java.sql.Connection c0) throws Exception {
        final Link L = new Link(c0);
        Data d = new Data();
        final String to = Jalali.todayStr();
        final String from = Jalali.addDays(to, -30);
        final String inFrom = Jalali.addDays(to, -13);
        d.salesDaily = liveL(token, L, d.notes, "روند فروش", () -> Repo.exec(L.c, Queries.factorDaily(L.m, true, from, to)));
        d.inDaily = liveL(token, L, d.notes, "روند دریافت", () -> Repo.exec(L.c, MoneyQueries.darDaily(L.m, 0, inFrom, to)));
        d.buyDaily = liveL(token, L, d.notes, "روند خرید", () -> Repo.exec(L.c, Queries.factorDaily(L.m, false, from, to)));
        d.outDaily = liveL(token, L, d.notes, "روند پرداخت", () -> Repo.exec(L.c, MoneyQueries.darDaily(L.m, 1, inFrom, to)));
        Filter todayFilter = new Filter();
        todayFilter.from = to;
        todayFilter.to = to;
        todayFilter.top = 5;
        d.topItems = liveL(token, L, d.notes, "پرفروش‌ترین کالاهای امروز",
                () -> Repo.exec(L.c, Queries.factorTopProducts(L.m, true, todayFilter, 5)));
        Filter vf = new Filter();
        vf.preset = Filter.P_LAST30;
        vf.applyPreset();
        d.visitors = liveL(token, L, d.notes, "ویزیتورها", () -> Repo.exec(L.c, MasterQueries.visitorsPerf(L.m, vf)));
        d.dueIn = liveL(token, L, d.notes, "سررسید دریافتی", () -> Repo.exec(L.c, MoneyQueries.chequeDue(L.m, true, 3)));
        d.dueOut = liveL(token, L, d.notes, "سررسید پرداختی", () -> Repo.exec(L.c, MoneyQueries.chequeDue(L.m, false, 3)));
        return d;
    }

    private static void mergeKpis(Data s, Data g) {
        s.snapshotDay = g.snapshotDay;
        s.salesDay = g.salesDay;
        s.buyDay = g.buyDay;
        s.inDay = g.inDay;
        s.outDay = g.outDay;
        s.custSum = g.custSum;
        s.debtors = g.debtors;
        s.overdue = g.overdue;
        s.saleState = g.saleState;
        s.buyState = g.buyState;
        s.returns = g.returns;
        s.notes.putAll(g.notes);
    }

    private static void mergeCharts(Data s, Data g) {
        s.salesDaily = g.salesDaily;
        s.inDaily = g.inDaily;
        s.buyDaily = g.buyDaily;
        s.outDaily = g.outDaily;
        s.topItems = g.topItems;
        s.visitors = g.visitors;
        s.dueIn = g.dueIn;
        s.dueOut = g.dueOut;
        s.notes.putAll(g.notes);
    }

    private void showFailure(final LinearLayout content, String faError, Data cached) {
        if (cached != null) {
            build(content, cached);
            LinearLayout bc = a.kit.card(Theme.GOLD);
            bc.addView(a.kit.text("📴 حالت آفلاین — آخرین داده ذخیره‌شده", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
            bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
            content.addView(bc, 0);
            content.addView(a.kit.gap(10), 1);
            return;
        }
        content.removeAllViews();
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.gap(8));
        content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
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
                    .append("|cust=").append(rowStr(d.custSum));
            if (d.topItems != null) {
                for (int i = 0; i < Math.min(5, d.topItems.size()); i++)
                    s.append("|top").append(i).append('=').append(rowStr(d.topItems.get(i)));
            }
            if (d.salesDaily != null) {
                for (int i = 0; i < Math.min(31, d.salesDaily.size()); i++)
                    s.append("|trend").append(i).append('=').append(rowStr(d.salesDaily.get(i)));
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
                else if ("sales".equals(k)) { d.salesDay = rowFrom(v); any = true; }
                else if ("buy".equals(k)) { d.buyDay = rowFrom(v); any = true; }
                else if ("in".equals(k)) { d.inDay = rowFrom(v); any = true; }
                else if ("out".equals(k)) { d.outDay = rowFrom(v); any = true; }
                else if ("cust".equals(k)) { d.custSum = rowFrom(v); any = true; }
                else if (k.matches("top[0-4]")) { d.topItems.add(rowFrom(v)); any = true; }
                else if (k.matches("trend(?:[0-9]|[12][0-9]|30)")) { d.salesDaily.add(rowFrom(v)); any = true; }
            }
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
        return day.equals(Jalali.todayStr()) ? "امروز" : "روز " + Jalali.shortLabel(day);
    }

    /** Depth layer behind the hero: two gold light sources, a glossy top sheen, a thin ring and a rim highlight. */
    private static final class HeroDecor extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float radius;

        HeroDecor(Context c, float radiusPx) {
            super(c);
            this.radius = radiusPx;
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            boolean light = Theme.isLight();
            box.set(0, 0, w, h);
            g.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(box, radius, radius, android.graphics.Path.Direction.CW);
            g.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(w * 0.92f, h * 0.02f, w * 0.60f,
                    Theme.alpha(Theme.GOLD, light ? 70 : 110), Theme.alpha(Theme.GOLD, 0), Shader.TileMode.CLAMP));
            g.drawRect(box, p);
            p.setShader(new RadialGradient(w * 0.06f, h * 1.02f, w * 0.45f,
                    Theme.alpha(Theme.GOLD_SOFT, light ? 40 : 46), Theme.alpha(Theme.GOLD_SOFT, 0), Shader.TileMode.CLAMP));
            g.drawRect(box, p);
            p.setShader(new LinearGradient(0, 0, 0, h * 0.5f,
                    Theme.alpha(0xFFFFFFFF, light ? 110 : 26), 0x00FFFFFF, Shader.TileMode.CLAMP));
            g.drawRect(box, p);
            p.setShader(null);
            g.restore();
            // Rim: a light top edge makes the card read as a raised, glossy panel.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dp(1));
            p.setColor(Theme.alpha(0xFFFFFFFF, light ? 150 : 34));
            box.set(Theme.dp(0.5f), Theme.dp(0.5f), w - Theme.dp(0.5f), h - Theme.dp(0.5f));
            g.drawRoundRect(box, radius, radius, p);
        }
    }

    /** «شنبه، ۲۵ مهر ۱۴۰۵» in Persian digits; falls back to the short line when the date is unusual. */
    private String dateLine() {
        String t = Jalali.todayStr();
        String[] p = t.split("/");
        if (p.length != 3) return a.kit.todayLine();
        try {
            int day = Integer.parseInt(p[2].trim());
            return Jalali.weekday(t) + "، " + Money.fa(String.valueOf(day)) + " " + Jalali.monthName(t)
                    + " " + Money.fa(p[0].trim());
        } catch (NumberFormatException e) {
            return a.kit.todayLine();
        }
    }

    private View greetingHero() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 5 ? "شب بخیر" : (hour < 12 ? "صبح بخیر"
                : (hour < 15 ? "روز بخیر" : (hour < 19 ? "عصر بخیر" : "شب بخیر")));
        String role = AtiranAuth.sessionRole(a);
        if (role == null || role.isEmpty()) role = RoleStore.current(a);
        String roleName = RoleStore.faName(role);

        final LinearLayout hero = a.kit.v();
        hero.setBackground(Theme.executiveHero());
        hero.setPadding(Theme.dp(18), Theme.dp(17), Theme.dp(18), Theme.dp(14));
        Theme.lift(hero, Theme.cardElevation() + Theme.dp(2));

        FrameLayout wrap = new FrameLayout(a);
        wrap.addView(new HeroDecor(a, Theme.dp(28)), new FrameLayout.LayoutParams(-1, -1));
        wrap.addView(hero, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout top = a.kit.h();
        top.setGravity(Gravity.CENTER_VERTICAL);

        // Crest: the company logo from the local cache (synced from the database), or a fine default mark.
        FrameLayout crest = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setCornerRadius(Theme.dp(18));
        frame.setColor(Theme.BG);
        frame.setStroke(Theme.dp(2), Theme.GOLD);
        crest.setBackground(frame);
        crest.setElevation(Theme.dp(3));
        android.graphics.Bitmap logo = ir.meelano.manager.data.Company.logo(a);
        if (logo != null) {
            ImageView li = new ImageView(a);
            li.setScaleType(ImageView.ScaleType.CENTER_CROP);
            li.setImageBitmap(logo);
            li.setClipToOutline(true);
            li.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.dp(16));
                }
            });
            crest.addView(li, new FrameLayout.LayoutParams(Theme.dp(50), Theme.dp(50), Gravity.CENTER));
        } else {
            ImageView mark = new ImageView(a);
            mark.setImageResource(R.drawable.meelano_3d);
            mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mark.setClipToOutline(true);
            mark.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.dp(16));
                }
            });
            crest.addView(mark, new FrameLayout.LayoutParams(Theme.dp(50), Theme.dp(50), Gravity.CENTER));
        }
        top.addView(crest, new LinearLayout.LayoutParams(Theme.dp(66), Theme.dp(66)));
        top.addView(a.kit.space(12));

        LinearLayout copy = a.kit.v();
        TextView overline = a.kit.text("MEELANO  •  اتاق فرمان", 10.5f, Theme.GOLD_SOFT, true);
        overline.setSingleLine(true);
        copy.addView(overline, a.kit.lp(-1, -2));
        TextView headline = a.kit.text(greeting + "، " + roleName, 26f, Theme.TEXT, true);
        headline.setMaxLines(1);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hp = a.kit.lp(-1, -2);
        hp.setMargins(0, Theme.dp(2), 0, 0);
        copy.addView(headline, hp);
        TextView description = a.kit.text("تصویر روشن از فروش، مالی و عملیات", 11f, Theme.MUTED, false);
        description.setSingleLine(true);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(description, a.kit.lp(-1, -2));
        top.addView(copy, a.kit.wlp(1f));

        hero.addView(top, a.kit.lp(-1, -2));
        hero.addView(a.kit.gap(13));

        // No hairline under the greeting: the card's own spacing separates the header from the rates.
        hero.addView(a.kit.gap(14));
        // Rates (dollar, gold, coin) and Tehran weather live inside the welcome card.
        hero.addView(marketCard(), a.kit.lp(-1, -2));
        hero.addView(a.kit.gap(10));
        LinearLayout footer = a.kit.h();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(a.kit.icon(R.drawable.mi_calendar_month, Theme.GOLD_SOFT, 18),
                new LinearLayout.LayoutParams(Theme.dp(18), Theme.dp(18)));
        footer.addView(a.kit.space(6));
        TextView date = a.kit.text(dateLine(), 12f, Theme.TEXT, true);
        date.setSingleLine(true);
        date.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
        date.setBackground(Theme.ghostButton(Theme.GOLD));
        footer.addView(date, a.kit.lp(-2, -2));
        footer.addView(a.kit.space(1), a.kit.wlp(1f));
        TextView edition = a.kit.text(RoleStore.editionFa(role), 12f, Theme.GOLD_SOFT, true);
        edition.setSingleLine(true);
        edition.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(10), Theme.dp(5));
        edition.setBackground(Theme.ghostButton(Theme.GOLD));
        footer.addView(edition, a.kit.lp(-2, -2));
        hero.addView(footer, a.kit.lp(-1, -2));

        // Gentle float, only when the phone allows motion; it stops as soon as the card leaves the screen.
        if (ir.meelano.manager.ui.Ui.motionOk(a)) {
            final android.animation.ObjectAnimator float1 = android.animation.ObjectAnimator.ofFloat(
                    hero, View.TRANSLATION_Y, 0f, -Theme.dp(3));
            float1.setDuration(3600);
            float1.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            float1.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
            float1.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            float1.start();
            wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) { }

                @Override
                public void onViewDetachedFromWindow(View v) { float1.cancel(); }
            });
        }
        return wrap;
    }

    /** A compact luxury launchpad to the five high-frequency management destinations. */
    /** Compact rates + Tehran weather. Saved values show at once; the refresh runs in the background. */
    /** Place name shown with the weather: the device's city, or Tehran when no position is available. */
    private String weatherPlace = MarketWeather.AHVAZ_NAME;
    /** True when the weather shown comes from the device position (not the Tehran fallback). */
    private boolean weatherFromDevice = false;

    private static final String PLACE_KEY = "weather_place";
    private static final String DEVICE_KEY = "weather_device";

    private View marketCard() {
        final LinearLayout card = a.kit.card(Theme.GOLD);
        final int gen = a.renderGen();
        // Show the last saved values and place straight away; the live refresh replaces them below.
        String savedPlace = MarketWeather.cached(a, PLACE_KEY);
        if (savedPlace != null && !savedPlace.isEmpty()) weatherPlace = savedPlace;
        weatherFromDevice = "1".equals(MarketWeather.cached(a, DEVICE_KEY));
        renderMarket(card, MarketWeather.cached(a, MarketWeather.ratesKey()),
                MarketWeather.cached(a, MarketWeather.weatherKey()), false, false);
        new Thread(() -> {
            // Position: last known or one quick fix (worker thread). Denied or unavailable means Tehran.
            android.location.Location loc = null;
            try { loc = ir.meelano.manager.core.DeviceLocation.resolve(a, 6000); } catch (Exception e) { Diag.ignored("Home:location", e); }
            final boolean fromDevice = loc != null;
            final double lat = fromDevice ? loc.getLatitude() : MarketWeather.AHVAZ_LAT;
            final double lon = fromDevice ? loc.getLongitude() : MarketWeather.AHVAZ_LON;
            String place = fromDevice ? ir.meelano.manager.core.DeviceLocation.placeName(a, lat, lon) : null;
            if (place == null) place = fromDevice ? "موقعیت شما" : MarketWeather.AHVAZ_NAME + " (پیش‌فرض)";

            String rRaw = null;
            String wRaw = null;
            try { rRaw = MarketWeather.get(MarketWeather.RATES_URL); } catch (Exception e) { Diag.ignored("Home:ratesFetch", e); }
            try { wRaw = MarketWeather.get(MarketWeather.weatherUrl(lat, lon)); } catch (Exception e) { Diag.ignored("Home:weatherFetch", e); }
            final boolean rLive = rRaw != null;
            final boolean wLive = wRaw != null;
            if (rLive) MarketWeather.save(a, MarketWeather.ratesKey(), rRaw);
            if (wLive) {
                // Weather is stored with its place, so a saved value is never shown under another city's name.
                MarketWeather.save(a, MarketWeather.weatherKey(), wRaw);
                MarketWeather.save(a, PLACE_KEY, place);
                MarketWeather.save(a, DEVICE_KEY, fromDevice ? "1" : "0");
            }
            final String fr = rLive ? rRaw : MarketWeather.cached(a, MarketWeather.ratesKey());
            final String fw = wLive ? wRaw : MarketWeather.cached(a, MarketWeather.weatherKey());
            final String fp = wLive ? place : (MarketWeather.cached(a, PLACE_KEY) == null ? place : MarketWeather.cached(a, PLACE_KEY));
            final boolean fd = wLive ? fromDevice : "1".equals(MarketWeather.cached(a, DEVICE_KEY));
            a.runOnUiThread(() -> {
                if (a.renderGen() != gen) return;
                weatherPlace = fp;
                weatherFromDevice = fd;
                renderMarket(card, fr, fw, rLive, wLive);
            });
        }, "market-refresh").start();
        return card;
    }


    private void renderMarket(LinearLayout card, String rJson, String wJson, boolean rLive, boolean wLive) {
        card.removeAllViews();
        List<MarketWeather.Rate> rates = null;
        MarketWeather.Weather w = null;
        try { if (rJson != null) rates = MarketWeather.parseRates(rJson); } catch (Exception e) { Diag.warn("Home:parseRates", e); }
        try { if (wJson != null) w = MarketWeather.parseWeather(wJson); } catch (Exception e) { Diag.warn("Home:parseWeather", e); }

        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(a.kit.text("بازار و هوای امروز", 14f, Theme.GOLD_SOFT, true), a.kit.wlp(1f));
        boolean live = rLive || wLive;
        head.addView(a.kit.pill(live ? "به‌روز" : "ذخیره‌شده", live ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        card.addView(head, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        if (rates == null || rates.isEmpty()) {
            card.addView(a.kit.hint("قیمت‌ها در دسترس نیست؛ اتصال اینترنت را بررسی کنید."), a.kit.lp(-1, -2));
        } else {
            LinearLayout row = a.kit.h();
            for (int i = 0; i < rates.size(); i++) {
                if (i > 0) row.addView(a.kit.space(6));
                row.addView(rateTile(rates.get(i)), a.kit.wlp(1f));
            }
            card.addView(row, a.kit.lp(-1, -2));
        }

        if (w != null) {
            card.addView(a.kit.gap(8));
            LinearLayout wr = a.kit.h();
            wr.setGravity(Gravity.CENTER_VERTICAL);
            // Gold vector icon instead of a text glyph (visual only; same weather code mapping).
            android.widget.ImageView wi = new android.widget.ImageView(a);
            int wRes = w.code <= 1 ? R.drawable.mi_light_mode
                    : w.code <= 3 ? R.drawable.mi_wb_twilight
                    : (w.code >= 95 ? R.drawable.mi_bolt
                    : (w.code >= 51 ? R.drawable.mi_beach_access : R.drawable.mi_wb_twilight));
            try { wi.setImageResource(wRes); } catch (Exception ignored) { Diag.ignored("HomeScreen:weatherIcon", ignored); }
            wi.setColorFilter(Theme.GOLD);
            wr.addView(wi, a.kit.lp(Theme.dp(44), Theme.dp(44)));
            wr.addView(a.kit.space(8));
            LinearLayout wc = a.kit.v();
            TextView line = a.kit.text(weatherPlace + " • " + Money.fa(String.valueOf(Math.round(w.temp))) + "°  "
                    + MarketWeather.describe(w.code), 12f, Theme.TEXT, true);
            line.setMaxLines(1);
            line.setEllipsize(TextUtils.TruncateAt.END);
            wc.addView(line, a.kit.lp(-1, -2));
            wc.addView(a.kit.text("رطوبت " + Money.fa(String.valueOf(w.humidity)) + "٪", 10.5f, Theme.MUTED, false),
                    a.kit.lp(-1, -2));
            wr.addView(wc, a.kit.wlp(1f));
            card.addView(wr, a.kit.lp(-1, -2));
        }

        if (!weatherFromDevice && w != null) {
            // Without the device position the card says so and offers the permission again.
            android.widget.Button loc = a.kit.btnGhost("📍 موقعیت من", Theme.GOLD, v -> a.requestLocationAccess());
            loc.setSingleLine(true);
            loc.setTextSize(11f * Theme.fontScale());
            loc.setPadding(Theme.dp(10), Theme.dp(4), Theme.dp(10), Theme.dp(4));
            loc.setMinHeight(Theme.dp(30));
            LinearLayout.LayoutParams lp = a.kit.lp(-2, -2);
            lp.setMargins(0, Theme.dp(6), 0, 0);
            lp.gravity = Gravity.END;
            card.addView(loc, lp);
        }

        String stamp = rates != null && !rates.isEmpty() ? rates.get(0).asOf : "";
        // Gold hairline above the source line, as in the reference market card.
        View rule = new View(a);
        rule.setBackgroundColor(Theme.alpha(Theme.GOLD, 90));
        card.addView(rule, a.kit.lp(-1, Theme.dp(1)));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.hint("به‌روزرسانی: " + Money.fa(stamp == null || stamp.isEmpty() ? "—" : stamp)
                + " • تومان • TGJU"), a.kit.lp(-1, -2));
    }

    private View rateTile(MarketWeather.Rate r) {
        LinearLayout t = a.kit.v();
        t.setBackground(Theme.kpi(Theme.GOLD));
        t.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        // Gold icon per instrument, as in the reference: coin, gold bar, dollar trend.
        String lbl = r.label == null ? "" : r.label;
        int tileIcon = lbl.contains("دلار") ? R.drawable.mi_trending_up
                : (lbl.contains("طلا") ? R.drawable.mi_diamond
                : (Theme.isLight() ? R.drawable.icon3d_coin : R.drawable.mi_payments));
        t.addView(a.kit.icon(tileIcon, Theme.GOLD, 24), new LinearLayout.LayoutParams(Theme.dp(24), Theme.dp(24)));
        TextView label = a.kit.text(r.label, 10.5f, Theme.TEXT, false);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMinLines(2);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(label, a.kit.lp(-1, -2));
        // Prices are always in toman (the source's unit), whatever unit the rest of the app uses.
        TextView price = a.kit.text(Money.fa(Money.num(r.toman)), 18f, Theme.TEXT, true);
        price.setMaxLines(1);
        price.setGravity(Gravity.CENTER_HORIZONTAL);
        price.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        t.addView(price, a.kit.lp(-1, -2));
        TextView unit = a.kit.text("تومان", 9.5f, Theme.GOLD_SOFT, true);
        unit.setMaxLines(1);
        unit.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(unit, a.kit.lp(-1, -2));
        int color = r.changePct > 0 ? Theme.SUCCESS : (r.changePct < 0 ? Theme.DANGER : Theme.MUTED);
        String arrow = r.changePct > 0 ? "↗ " : (r.changePct < 0 ? "↘ " : "");
        TextView ch = a.kit.text(arrow + Money.fa(String.format(Locale.US, "%.1f", Math.abs(r.changePct))) + "٪",
                10f, color, true);
        ch.setMaxLines(1);
        ch.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(ch, a.kit.lp(-1, -2));
        t.setContentDescription(r.label + "، " + Money.fa(Money.num(r.toman)) + " تومان");
        return t;
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        // The clock banner stays on top after live data arrives; it now reports the refresh time.
        String nowTs = Money.fa(String.format(Locale.US, "%02d:%02d",
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)));
        content.addView(updateBanner(nowTs, true), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.gap(8));

        // Render each dashboard section into its own container so the lower-priority
        // analysis panels can collapse without changing the user's saved order.
        List<LinearLayout> blocks = new ArrayList<>();
        List<Boolean> essential = new ArrayList<>();
        int detailCount = 0;
        for (String key : orderedKeys()) {
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key);
            if (block.getChildCount() == 0) continue;
            boolean keepVisible = isEssentialHomeSection(key);
            blocks.add(block);
            essential.add(keepVisible);
            if (!keepVisible) detailCount++;
        }

        boolean expanded = a.settings.homeDetailsExpanded();
        boolean toggleAdded = false;
        for (int i = 0; i < blocks.size(); i++) {
            if (!essential.get(i) && !toggleAdded && detailCount > 0) {
                content.addView(homeDetailsToggle(blocks, essential, detailCount, expanded), a.kit.lp(-1, -2));
                toggleAdded = true;
            }
            LinearLayout block = blocks.get(i);
            if (!essential.get(i) && !expanded) block.setVisibility(View.GONE);
            content.addView(block, a.kit.lp(-1, -2));
        }
        renderNotes(content, d.notes);
    }

    private void renderHomeSection(LinearLayout content, Data d, String key) {
        if ("kpis".equals(key)) secKpis(content, d);
        else if ("alerts".equals(key)) secAlerts(content, d);
        else if ("trend".equals(key)) secTrend(content, d);
        else if ("donut".equals(key)) secDonut(content, d);
        else if ("today_top".equals(key)) secTopItems(content, d);
        else if ("debtors".equals(key)) secDebtors(content, d);
        else if ("visitors".equals(key)) secVisitors(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("invstatus".equals(key)) secInvStatus(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
        else if ("shortcuts".equals(key)) secShortcuts(content, d);
    }

    private boolean isEssentialHomeSection(String key) {
        return "kpis".equals(key) || "alerts".equals(key) || "due".equals(key)
                || "invstatus".equals(key) || "returns".equals(key) || "today_top".equals(key);
    }

    private View homeDetailsToggle(final List<LinearLayout> blocks, final List<Boolean> essential,
                                  final int detailCount, boolean expandedInitially) {
        final boolean[] expanded = {expandedInitially};
        final LinearLayout card = a.kit.card(Theme.INFO);
        card.setPadding(Theme.dp(14), Theme.dp(11), Theme.dp(14), Theme.dp(11));
        LinearLayout row = a.kit.h();
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView icon = a.kit.text("▦", 21f, Theme.INFO, true);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setBackground(Theme.avatar(Theme.alpha(Theme.INFO, 50)));
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        row.addView(a.kit.space(10));

        LinearLayout copy = a.kit.v();
        TextView title = a.kit.text("جزئیات تکمیلی داشبورد", 13.5f, Theme.TEXT, true);
        TextView subtitle = a.kit.text("", 10.5f, Theme.MUTED, false);
        subtitle.setMaxLines(2);
        copy.addView(title, a.kit.lp(-1, -2));
        copy.addView(subtitle, a.kit.lp(-1, -2));
        row.addView(copy, a.kit.wlp(1f));
        row.addView(a.kit.space(8));

        android.widget.ImageView action = new android.widget.ImageView(a);
        action.setColorFilter(Theme.INFO);
        action.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        row.addView(action, new LinearLayout.LayoutParams(Theme.dp(28), Theme.dp(36)));
        card.addView(row, a.kit.lp(-1, -2));

        final Runnable refresh = () -> {
            subtitle.setText(expanded[0]
                    ? "نمایش داده می‌شود؛ برای جمع‌کردن لمس کنید"
                    : Money.fa(String.valueOf(detailCount)) + " بخش تحلیلی و میان‌بر • برای نمایش لمس کنید");
            action.setImageResource(expanded[0] ? R.drawable.mi_remove : R.drawable.mi_add);
            card.setContentDescription(expanded[0] ? "جمع‌کردن جزئیات داشبورد" : "نمایش جزئیات داشبورد");
        };
        refresh.run();
        Theme.pressable(card);
        card.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            a.settings.setHomeDetailsExpanded(expanded[0]);
            for (int i = 0; i < blocks.size(); i++) {
                if (!essential.get(i)) blocks.get(i).setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            }
            refresh.run();
        });
        return card;
    }

    /** Dashboard order from settings; unknown keys dropped, missing sections appended. */
    private List<String> orderedKeys() {
        List<String> out = new ArrayList<>();
        String[] known = {"kpis", "alerts", "due", "invstatus", "returns", "today_top", "trend", "debtors", "visitors", "donut", "eod", "shortcuts"};
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
        // ---- day KPIs (tap a tile → that day's detail list) ----
        // soft() deliberately returns null when one optional query fails. Keep the
        // remaining KPIs visible, but make the missing metric explicit and inert.
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(dayKpi("فروش", "sales", d.salesDay, "docs", "فاکتور", Theme.GOLD));
        kpis.add(dayKpi("خرید", "buy", d.buyDay, "docs", "فاکتور", Theme.GOLD));
        kpis.add(dayKpi("دریافت", "dar_in", d.inDay, "count", "قبض", Theme.GOLD));
        kpis.add(dayKpi("پرداخت", "dar_out", d.outDay, "count", "قبض", Theme.GOLD));
        // Sparklines: last 14 days per tile (days without a row count as 0). Each tile
        // draws only from its own daily query; a missing query simply leaves the tile plain.
        kpis.get(0).icon = R.drawable.mi_point_of_sale;
        kpis.get(1).icon = R.drawable.mi_shopping_cart;
        kpis.get(2).icon = R.drawable.mi_receipt_long;
        kpis.get(3).icon = R.drawable.mi_payments;
        attachSpark(kpis.get(0), d.salesDaily, d);
        attachSpark(kpis.get(1), d.buyDaily, d);
        attachSpark(kpis.get(2), d.inDaily, d);
        attachSpark(kpis.get(3), d.outDaily, d);
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String compareDay = snapshotDay(d);
            String compareLabel = snapshotLabel(d);
            double todaySales = salesOnDay(d.salesDaily, compareDay);
            double yesterday = salesOnDay(d.salesDaily, Jalali.addDays(compareDay, -1));
            double avg7 = averageSalesBefore(d.salesDaily, compareDay, 7);
            double avg30 = averageSalesBefore(d.salesDaily, compareDay, 30);
            LinearLayout compare = a.kit.card(Theme.GOLD);
            compare.addView(a.kit.sectionHead("فروش " + compareLabel + " در مقایسه", "فروش ›", v -> a.nav("sales")),
                    a.kit.lp(-1, -2));
            String delta = avg7 > 0 ? Money.pct((todaySales - avg7) * 100.0 / avg7) : "—";
            int deltaColor = avg7 <= 0 ? Theme.MUTED : (todaySales >= avg7 ? Theme.SUCCESS : Theme.WARNING);
            List<Kit.Kpi> cmp = new ArrayList<>();
            cmp.add(new Kit.Kpi(compareLabel, Money.compactRial(todaySales), "تغییر ۷ روزه " + delta, Theme.GOLD));
            cmp.add(new Kit.Kpi("دیروز", Money.compactRial(yesterday), "", Theme.TEXT));
            cmp.add(new Kit.Kpi("میانگین ۷ روز قبل", Money.compactRial(avg7), "", Theme.GOLD_SOFT));
            cmp.add(new Kit.Kpi("میانگین ۳۰ روز قبل", Money.compactRial(avg30), "", Theme.GOLD_SOFT));
            compare.addView(a.kit.kpiGrid(cmp, 2), a.kit.lp(-1, -2));
            if (todaySales <= 0)
                compare.addView(a.kit.hint("برای " + compareLabel + " هنوز فروشی ثبت نشده است. اعداد بالا از روزهای قبل محاسبه شده‌اند."),
                        a.kit.lp(-1, -2));
            a.kit.addCard(content, compare);
        }
    }

    Kit.Kpi dayKpi(String title, String target, Row row, String countKey,
                   String countLabel, int accent) {
        String day = dayOf(row);
        String label = day.isEmpty() ? title : title + " " + day;
        if (row == null) {
            // No row for this day yet: a neutral dash, never an error-looking placeholder.
            return new Kit.Kpi(label, "—", "هنوز ثبت نشده", accent);
        }
        return new Kit.Kpi(label, Money.compactRial(row.d("total")),
                Money.fa(String.valueOf(row.l(countKey))) + " " + countLabel, accent,
                () -> gotoDay(target, row));
    }

    private void secAlerts(LinearLayout content, Data d) {
        // ---- alerts ----
        LinearLayout alerts = a.kit.card(Theme.GOLD);
        alerts.addView(a.kit.text("هشدار امروز", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        int n = 0;
        n += alert(alerts, d, n);
        List<FollowUps.Due> dues = FollowUps.dueToday(a);
        if (!dues.isEmpty()) {
            final FollowUps.Due fd = dues.get(0);
            String who = dues.size() == 1 ? fd.note.name : Money.fa(String.valueOf(dues.size())) + " یادداشت";
            alerts.addView(a.kit.alertRow("پیگیری مشتری", who + " • سررسید یادداشت",
                    Theme.GOLD, v -> gotoCustomer(fd.code)), padTop(n));
            n++;
        }
        if (n == 0) alerts.addView(a.kit.text("فعلاً هشدار مهمی وجود ندارد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, alerts);
    }

    /**
     * Confirmation state of the last 30 days' invoices. An invoice that never
     * reached Status=1 is still provisional: it sits in the ledger without ever
     * having been finalised, and nobody is told. Surfacing the count with a tap
     * through to the full list is what makes it actionable.
     */
    private void secInvStatus(LinearLayout content, Data d) {
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.sectionHead("وضعیت فاکتورهای ۳۰ روز اخیر", "گزارش ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        addState(c, "فروش", d.saleState);
        addState(c, "خرید", d.buyState);
        if ((d.saleState == null || d.saleState.isEmpty()) && (d.buyState == null || d.buyState.isEmpty()))
            c.addView(a.kit.text("داده‌ای برای این بازه یافت نشد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, c);
    }

    /** One line per state of one side (sales or purchases): how many, and for how much. */
    private void addState(LinearLayout c, String side, List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;
        long pendingN = 0;
        double pendingSum = 0;
        for (Row r : rows) {
            if (r.i("state") == 1) continue; // 1 = confirmed, the rest are provisional
            pendingN += r.l("docs");
            pendingSum += r.d("total");
        }
        int col = pendingN > 0 ? Theme.WARNING : Theme.SUCCESS;
        String txt = side + ": " + (pendingN > 0
                ? Money.fa(String.valueOf(pendingN)) + " فاکتور تأییدنشده (" + Money.compactMoney(pendingSum) + ")"
                : "همه تأیید شده‌اند");
        c.addView(a.kit.kv(txt, pendingN > 0 ? "نیاز به بررسی" : "✓", col), a.kit.lp(-1, -2));
    }

    /**
     * Return documents of the last 30 days. dbo.back_sanad is the ledger of goods
     * moving back — out of the warehouse, back from a customer, and so on — so a
     * sudden rise here is a quality or logistics problem long before it shows up
     * in the profit figures.
     */
    private void secReturns(LinearLayout content, Data d) {
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.sectionHead("برگشتی‌های ۳۰ روز اخیر", "گزارش ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        long n = 0;
        double sum = 0;
        if (d.returns != null) {
            for (Row r : d.returns) {
                n += r.l("docs");
                sum += r.d("total");
            }
        }
        if (n == 0) {
            c.addView(a.kit.kv("هیچ سند برگشتی در این بازه ثبت نشده", "✓", Theme.SUCCESS), a.kit.lp(-1, -2));
        } else {
            c.addView(a.kit.kv(Money.fa(String.valueOf(n)) + " سند برگشتی",
                    Money.compactMoney(sum), Theme.GOLD), a.kit.lp(-1, -2));
            int shown = 0;
            for (Row r : d.returns) {
                if (shown++ >= 3) break;
                String label = r.s("label");
                if (label == null || label.trim().isEmpty()) continue;
                c.addView(a.kit.kv(label,
                        Money.fa(String.valueOf(r.l("docs"))) + " سند • " + Money.compactMoney(r.d("total")),
                        Theme.TEXT), a.kit.lp(-1, -2));
            }
        }
        a.kit.addCard(content, c);
    }

    /** Top five by the snapshot day's invoiced sales, following the dashboard benchmark. */
    private void secTopItems(LinearLayout content, Data d) {
        String period = snapshotLabel(d);
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(a.kit.sectionHead("پرفروش‌ترین کالاهای " + period, "گزارش کامل ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        if (d.topItems == null || d.topItems.isEmpty()) {
            c.addView(a.kit.text("هنوز کالایی برای " + period + " ثبت نشده است.", 11.5f, Theme.MUTED, false),
                    a.kit.lp(-1, -2));
        } else {
            int show = Math.min(5, d.topItems.size());
            for (int i = 0; i < show; i++) {
                Row item = d.topItems.get(i);
                LinearLayout row = a.kit.h();
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                TextView rank = a.kit.text(Money.fa(String.valueOf(i + 1)), 11f, Theme.GOLD, true);
                rank.setGravity(android.view.Gravity.CENTER);
                rank.setBackground(Theme.avatar(Theme.alpha(Theme.GOLD, 44)));
                row.addView(rank, new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32)));
                row.addView(a.kit.space(8));

                LinearLayout copy = a.kit.v();
                TextView name = a.kit.text(item.s("label"), 12f, Theme.TEXT, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(name, a.kit.lp(-1, -2));
                String detail = Money.qty(item.d("qty")) + " واحد • "
                        + Money.fa(String.valueOf(item.l("docs"))) + " ردیف فروش";
                copy.addView(a.kit.text(detail, 10f, Theme.MUTED, false), a.kit.lp(-1, -2));
                row.addView(copy, a.kit.wlp(1f));
                row.addView(a.kit.space(8));

                TextView total = a.kit.text(Money.compactRial(item.d("total")), 11.5f, Theme.GOLD, true);
                total.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                total.setMaxLines(1);
                total.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(total, a.kit.lp(-2, -2));
                c.addView(row, a.kit.lp(-1, -2));
                if (i + 1 < show) c.addView(a.kit.gap(8));
            }
            c.addView(a.kit.hint("رتبه‌بندی بر پایهٔ مبلغ فروش " + period), a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, c);
    }

    private void secTrend(LinearLayout content, Data d) {
        // ---- 14-day sales trend ----
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String endDay = snapshotDay(d);
            String period = snapshotLabel(d);
            LinearLayout c = a.kit.card(Theme.GOLD);
            c.addView(a.kit.sectionHead("روند ۱۴ روزه فروش تا " + period, "فروش ›", v -> a.nav("sales")), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = filled(d.salesDaily, endDay);
            final TextView cap = a.kit.text(pts.isEmpty() ? "" : lastCap(pts), 11f, Theme.MUTED, false);
            ch.setData(pts, Theme.GOLD, Charts.COMPACT);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.rial(p.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(190)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secDonut(LinearLayout content, Data d) {
        // ---- receipts mix donut ----
        if (d.inDay != null && d.inDay.d("total") > 0) {
            LinearLayout c = a.kit.card(Theme.SUCCESS);
            c.addView(a.kit.sectionHead("ترکیب دریافت " + dayOf(d.inDay), "دریافت‌ها ›", v -> a.nav("dar_in")), a.kit.lp(-1, -2));
            Charts.Donut dn = new Charts.Donut(a);
            List<Charts.Point> pts = new ArrayList<>();
            pts.add(new Charts.Point("نقد", d.inDay.d("cash"), Theme.SUCCESS));
            pts.add(new Charts.Point("کارت", d.inDay.d("pos"), Theme.INFO));
            pts.add(new Charts.Point("حواله", d.inDay.d("havaleh"), Theme.GOLD));
            pts.add(new Charts.Point("چک", d.inDay.d("cheque"), Theme.GOLD));
            dn.setData(pts, "جمع دریافت", Money.compactRial(d.inDay.d("total")));
            c.addView(dn, new LinearLayout.LayoutParams(-1, Theme.dp(300)));
            a.kit.addCard(content, c);
        }
    }

    private void secDebtors(LinearLayout content, Data d) {
        // ---- debtors ----
        if (d.debtors != null && !d.debtors.isEmpty()) {
            content.addView(a.kit.sectionHead("بدهکاران اولویت‌دار", "مشتریان ›", v -> gotoDebtors()), a.kit.lp(-1, -2));
            final java.util.Map<String, android.widget.ImageView> slots = new java.util.HashMap<>();
            final List<String> codes = new ArrayList<>();
            for (Row r : d.debtors) {
                final String code = r.s("code");
                final String party = r.s("party");
                codes.add(code);
                View av = ir.meelano.manager.data.CustomerPhotos.avatar(a, code, a.kit.initials(party), Theme.GOLD, 48, slots);
                View row = a.kit.personRow(party, "کد " + Money.fa(code), Money.rial(r.d("amount")),
                        "مانده بدهی", Theme.GOLD, v -> gotoCustomer(code), av);
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, 0, 0, Theme.dp(10));
                content.addView(row, p);
            }
            ir.meelano.manager.data.CustomerPhotos.refresh(a.repo, a, codes,
                    () -> ir.meelano.manager.data.CustomerPhotos.fill(slots, a));
        }
    }

    private void secVisitors(LinearLayout content, Data d) {
        // ---- visitors ----
        if (d.visitors != null && !d.visitors.isEmpty()) {
            LinearLayout c = a.kit.card(Theme.GOLD);
            c.addView(a.kit.sectionHead("عملکرد ۳۰ روزه ویزیتورها", "ویزیتورها ›", v -> a.nav("visitors")), a.kit.lp(-1, -2));
            Charts.HBars hb = new Charts.HBars(a);
            List<Charts.Point> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(6, d.visitors.size()); i++) {
                Row r = d.visitors.get(i);
                pts.add(new Charts.Point(r.s("name"), r.d("sales")));
            }
            hb.setData(pts, Charts.COMPACT);
            c.addView(hb, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secDue(LinearLayout content, Data d) {
        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = a.kit.card(Theme.INFO);
            c.addView(a.kit.sectionHead("سررسید ۳ روز آینده", "چک‌ها ›", v -> a.nav("cheques")), a.kit.lp(-1, -2));
            if (d.dueIn != null && !d.dueIn.isEmpty())
                c.addView(a.kit.kv("دریافتی (" + Money.fa(String.valueOf(d.dueIn.size())) + " فقره)", Money.rial(dueInSum), Theme.SUCCESS), a.kit.lp(-1, -2));
            if (d.dueOut != null && !d.dueOut.isEmpty())
                c.addView(a.kit.kv("پرداختی (" + Money.fa(String.valueOf(d.dueOut.size())) + " فقره)", Money.rial(dueOutSum), Theme.GOLD), a.kit.lp(-1, -2));
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
        LinearLayout c = a.kit.card(Theme.GOLD);
        String when = "";
        try {
            when = dt == null || dt.isEmpty() ? "" : Jalali.dispFa(dt);
        } catch (Exception ignored) { Diag.ignored("HomeScreen:800", ignored); }
        c.addView(a.kit.sectionHead("🌙 جمع‌بندی پایان روز" + (when.isEmpty() ? "" : " • " + when),
                null, null), a.kit.lp(-1, -2));
        c.addView(a.kit.text(txt, 13f, Theme.TEXT, false), a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));
        final String share = "🌙 جمع‌بندی پایان روز میلانو" + (when.isEmpty() ? "" : " (" + when + ")")
                + "\n" + txt;
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

    private void secShortcuts(LinearLayout content, Data d) {
        // ---- secondary management centers (daily destinations live in the top launchpad) ----
        content.addView(a.kit.sectionHead("مراکز تخصصی", null, null), a.kit.lp(-1, -2));
        String[][] links = {
                {"customers", "پرونده، مانده و گردش حساب"},
                {"cheques", "دریافتی، پرداختی و سررسیدها"},
                {"profit", "حاشیه سود و هزینه‌ها"},
                {"visitors", "عملکرد و وصول ویزیتورها"},
                {"users", "کاربران و فعالیت‌های سیستم"},
        };
        for (int i = 0; i < links.length; i++) {
            final String id = links[i][0];
            Screen s = a.screen(id);
            if (s == null || !RoleStore.allowed(a, id)) continue;
            View r = a.kit.navRow(s.glyph(), s.title(), links[i][1], s.accent(), v -> a.nav(id));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(10));
            content.addView(r, p);
        }
    }

    private int alert(LinearLayout box, Data d, int n) {
        // overdue top-1
        if (d.overdue != null && !d.overdue.isEmpty()) {
            Row o = d.overdue.get(0);
            long dd = o.l("days");
            // Without dif_date_alan the server sends −1: derive the delay from the due date.
            if (dd < 0) dd = Jalali.diffDays(o.s("dueDate"), Jalali.todayStr());
            String days = dd >= 0 ? " • تأخیر " + Money.fa(String.valueOf(dd)) + " روز" : "";
            box.addView(a.kit.alertRow("فاکتور معوق", o.s("party") + " • " + Money.rial(o.d("amount")) + days,
                    Theme.GOLD, v -> gotoUnsettled()), padTop(n));
            n++;
        }
        // top debtor
        if (d.debtors != null && !d.debtors.isEmpty()) {
            Row r = d.debtors.get(0);
            box.addView(a.kit.alertRow("بدهکار اولویت‌دار", r.s("party") + " • مانده " + Money.rial(r.d("amount")),
                    Theme.GOLD, v -> gotoCustomer(r.s("code"))), padTop(n));
            n++;
        }
        // liquidity
        if (d.salesDay != null && d.buyDay != null && d.salesDay.d("total") > 0 && d.buyDay.d("total") > d.salesDay.d("total") * 1.15) {
            box.addView(a.kit.alertRow("فشار نقدینگی", "خرید روز از فروش جلو زده؛ پرداخت‌ها را کنترل کنید.",
                    Theme.GOLD, v -> a.nav("buy")), padTop(n));
            n++;
        }
        // due cheques
        int dueN = (d.dueIn == null ? 0 : d.dueIn.size()) + (d.dueOut == null ? 0 : d.dueOut.size());
        if (dueN > 0) {
            box.addView(a.kit.alertRow("سررسید چک", Money.fa(String.valueOf(dueN)) + " فقره چک در ۳ روز آینده سررسید می‌شود.",
                    Theme.INFO, v -> a.nav("cheques")), padTop(n));
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
        return Jalali.shortLabel(d);
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

    private void attachSpark(Kit.Kpi tile, List<Row> rows, Data d) {
        if (rows == null || rows.isEmpty()) return;
        try {
            String endDay = snapshotDay(d);
            double[] series = new double[14];
            for (int i = 0; i < series.length; i++)
                series[i] = salesOnDay(rows, Jalali.addDays(endDay, i - (series.length - 1)));
            tile.spark = series;
        } catch (Exception e) {
            Diag.ignored("Home:sparkline", e);
        }
    }

    private double salesOnDay(List<Row> rows, String day) {
        if (rows == null || day == null || day.isEmpty()) return 0;
        String target = Jalali.disp(day);
        double total = 0;
        for (Row r : rows)
            if (target.equals(Jalali.disp(r.s("day")))) total += r.d("total");
        return total;
    }

    /** Average across calendar days before today, counting days without sales as zero. */
    private double averageSalesBefore(List<Row> rows, String today, int days) {
        if (rows == null || days <= 0) return 0;
        Map<String, Double> byDay = new HashMap<>();
        for (Row r : rows) {
            String d = Jalali.disp(r.s("day"));
            if (!d.isEmpty()) byDay.merge(d, r.d("total"), Double::sum);
        }
        int first = Jalali.parse(Jalali.addDays(today, -days));
        if (first < 0) return 0;
        double total = 0;
        for (int i = 0; i < days; i++) {
            Double amount = byDay.get(Jalali.format(first + i));
            if (amount != null) total += amount;
        }
        return total / days;
    }

    /** Fill missing days with 0 for a smooth 14-day chart ending on the saved snapshot day. */
    private List<Charts.Point> filled(List<Row> rows, String endDay) {
        Map<String, Double> map = new HashMap<>();
        if (rows != null) for (Row r : rows) {
            String k = Jalali.disp(r.s("day"));
            if (!k.isEmpty()) map.merge(k, r.d("total"), Double::sum);
        }
        List<Charts.Point> out = new ArrayList<>();
        String to = Jalali.disp(endDay);
        if (to.isEmpty()) to = Jalali.todayStr();
        String from = Jalali.addDays(to, -13);
        int d0 = Jalali.parse(from);
        for (int i = 0; i < 14 && d0 >= 0; i++) {
            String day = Jalali.format(d0 + i);
            Double v = map.get(day);
            out.add(new Charts.Point(Jalali.shortLabel(day), v == null ? 0 : v));
        }
        return out;
    }

    private String lastCap(List<Charts.Point> pts) {
        Charts.Point p = pts.get(pts.size() - 1);
        return Money.fa(p.label) + " • " + Money.rial(p.value);
    }

    private void gotoDebtors() {
        Screen s = a.screen("customers");
        if (s != null && s.filter() != null) {
            Filter nf = s.filter().copy();
            nf.status = "debt";
            nf.sort = "debt";
            nf.page = 0;
            s.applyFilter(nf);
        }
        a.nav("customers");
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
