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
    /** Banner ink: dark-skin colour, or a light ink on the emerald banner in the light skin. */
    private static int bannerInk(int dark, int light) {
        // Cups skin: the banner is ivory, so it takes the skin's own ink (dark argument) instead of the dark-green pair.
        if (Theme.isCups()) return dark;
        return Theme.isLight() ? light : dark;
    }

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
                Theme.isCups() ? new int[]{0xFFFFFFFF, 0xFFFBEBDD}
                        : Theme.isLight() ? new int[]{0xFF17684F, 0xFF0A3B2E} : new int[]{0xFF0E121A, 0xFF07090E});
        base.setCornerRadius(Theme.dp(22));
        base.setStroke(Theme.dp(1), Theme.isCups() ? Theme.alpha(Theme.GOLD, 150)
                : Theme.isLight() ? 0xFFC9A24A : Theme.alpha(Theme.GOLD, 120));
        box.setBackground(base);
        box.setMinimumHeight(Theme.dp(96));
        if (Theme.isNuts()) {
            // Nut theme: the nut-and-gold artwork fills the left side of the banner.
            android.widget.ImageView nuts = new android.widget.ImageView(c);
            nuts.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            nuts.setImageResource(R.drawable.light_banner_nuts);
            box.addView(nuts, new FrameLayout.LayoutParams(-1, -1));
            View scrim = new View(c);
            scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                    new int[]{0xE60A3B2E, 0xA60A3B2E, 0x000A3B2E}));
            box.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        } else if (!Theme.isLight()) {
            box.addView(new ClockView(c, clockBitmap), new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        // Text sits on the right; the end side keeps room for the clock artwork.
        text.setPaddingRelative(Theme.dp(16), Theme.dp(14), Theme.dp(118), Theme.dp(14));
        if (done) {
            text.addView(a.kit.text("✓ داشبورد به‌روز است", 12f, bannerInk(Theme.GOLD_SOFT, 0xFFF3D98C), true), new LinearLayout.LayoutParams(-1, -2));
            text.addView(a.kit.text("ساعت به‌روزرسانی: " + savedTs, 11f, bannerInk(Theme.MUTED, 0xFFD3E6DD), false), new LinearLayout.LayoutParams(-1, -2));
        } else {
            text.addView(a.kit.text("⟳ در حال به‌روزرسانی داشبورد…", 12f, bannerInk(Theme.TEXT, 0xFFFFF4D6), true), new LinearLayout.LayoutParams(-1, -2));
            if (savedTs != null && !savedTs.isEmpty()) {
                text.addView(a.kit.text("آخرین داده ذخیره‌شده: " + Money.fa(savedTs), 11f, bannerInk(Theme.MUTED, 0xFFD3E6DD), false), new LinearLayout.LayoutParams(-1, -2));
            }
        }
        box.addView(text, new FrameLayout.LayoutParams(-1, -2));
        if (!done) addShimmer(box);
        return box;
    }

    /** A soft gold band sweeps across the card while it waits for live data (light, one animator). */
    private void addShimmer(final FrameLayout box) {
        final View band = new View(a);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Theme.alpha(Theme.GOLD, 0), Theme.alpha(Theme.GOLD, 60), Theme.alpha(Theme.GOLD, 0)});
        band.setBackground(g);
        band.setClickable(false);
        band.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(band, new FrameLayout.LayoutParams(Theme.dp(120), -1));
        final android.animation.ObjectAnimator[] sweepRef = new android.animation.ObjectAnimator[1];
        box.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (r - l <= 0 || sweepRef[0] != null) return;
            sweepRef[0] = android.animation.ObjectAnimator.ofFloat(band, View.TRANSLATION_X, -Theme.dp(120), r - l);
            sweepRef[0].setDuration(1600);
            sweepRef[0].setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            sweepRef[0].start();
        });
        // Stop the repeating animation when the card leaves the screen.
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) {
                if (sweepRef[0] != null) sweepRef[0].cancel();
            }
        });
    }

    @Override
    public void render(final LinearLayout content) {
        // Faint gold dot grid behind the dashboard (both skins; very low contrast, decorative only).
        if (!(content.getBackground() instanceof DotGrid)) content.setBackground(new DotGrid());
        final int token = a.renderGen();
        // Smart first paint: the last saved dashboard appears at once and live data replaces it when ready.
        final String[] cachedTs = {""};
        final Data cached = loadCached(cachedTs);
        if (cached != null) {
            build(content, cached);
            // build() already put a banner on top: replace it with the updating state (one banner only).
            content.removeViewAt(0);
            // The banner changes state with a short cross-fade instead of a jump.
            View updating = updateBanner(cachedTs[0]);
            updating.setAlpha(0f);
            updating.animate().alpha(1f).setDuration(280).start();
            content.addView(updating, 0);
        } else {
            content.removeAllViews();
            bannerWasLoading = true;
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
        hero.setBackground(heroBackground());
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
            mark.setImageResource(Theme.logoRes());
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
        FrameLayout crestWrap = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable halo = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.alpha(Theme.GOLD, 90), Theme.alpha(Theme.GOLD, 0)});
        halo.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        View haloView = new View(a);
        haloView.setBackground(halo);
        crestWrap.addView(haloView, new FrameLayout.LayoutParams(-1, -1));
        crestWrap.addView(crest, new FrameLayout.LayoutParams(Theme.dp(66), Theme.dp(66), Gravity.CENTER));
        top.addView(crestWrap, new LinearLayout.LayoutParams(Theme.dp(78), Theme.dp(78)));
        top.addView(a.kit.space(12));

        LinearLayout copy = a.kit.v();
        TextView overline = a.kit.text("میلانو  •  اتاق فرمان", 10.5f, Theme.GOLD_SOFT, true);
        overline.setSingleLine(true);
        copy.addView(overline, a.kit.lp(-1, -2));
        TextView headline = a.kit.text(greeting + "، " + roleName, 26f, Theme.TEXT, true);
        headline.setMaxLines(1);
        // Long names shrink to fit the line instead of being cut off.
        headline.setAutoSizeTextTypeUniformWithConfiguration(16, 26, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
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
            // One price size for the whole row; a long figure (gold) shrinks every tile, so none gets cut.
            float psz = 15f;
            for (MarketWeather.Rate r : rates) {
                if (Money.fa(Money.num(r.toman)).length() > 8) psz = 12f;
            }
            LinearLayout row = a.kit.h();
            for (int i = 0; i < rates.size(); i++) {
                if (i > 0) row.addView(a.kit.space(6));
                row.addView(rateTile(rates.get(i), psz), a.kit.wlp(1f));
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

    private View rateTile(MarketWeather.Rate r, float priceSp) {
        LinearLayout t = a.kit.v();
        t.setBackground(Theme.kpi(Theme.GOLD));
        t.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        // Gold icon per instrument, as in the reference: coin, gold bar, dollar trend.
        String lbl = r.label == null ? "" : r.label;
        int tileIcon = lbl.contains("دلار") ? Theme.icon3d(R.drawable.mi_trending_up)
                : (lbl.contains("طلا") ? Theme.icon3d(R.drawable.mi_diamond)
                : (Theme.isLight() ? R.drawable.icon3d_coin : R.drawable.icon3d_dark_coin));
        int tile = Theme.isNuts() ? 64 : 42;
        t.addView(a.kit.icon(tileIcon, Theme.GOLD, tile), new LinearLayout.LayoutParams(Theme.dp(tile), Theme.dp(tile)));
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
        // Tabular figures: digits keep their width when the price updates.
        price.setFontFeatureSettings("tnum");
        // One fixed size for every tile, so the prices line up across the row.
        price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, priceSp);
        LinearLayout.LayoutParams pp = a.kit.lp(-1, -2);
        pp.setMargins(0, Theme.dp(6), 0, 0);
        t.addView(price, pp);
        // Unit is smaller and quieter than the figure.
        TextView unit = a.kit.text("تومان", 9f, Theme.MUTED, false);
        unit.setMaxLines(1);
        unit.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(unit, a.kit.lp(-1, -2));
        int color = r.changePct > 0 ? Theme.SUCCESS : (r.changePct < 0 ? Theme.DANGER : Theme.MUTED);
        String arrow = r.changePct > 0 ? "↗ " : (r.changePct < 0 ? "↘ " : "");
        TextView ch = a.kit.text(arrow + Money.fa(String.format(Locale.US, "%.1f", Math.abs(r.changePct))) + "٪",
                10f, color, true);
        ch.setMaxLines(1);
        ch.setGravity(Gravity.CENTER_HORIZONTAL);
        ch.setBackground(Theme.pill(color));
        ch.setPadding(Theme.dp(8), Theme.dp(2), Theme.dp(8), Theme.dp(2));
        LinearLayout.LayoutParams cp = a.kit.lp(-2, -2);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.setMargins(0, Theme.dp(6), 0, 0);
        t.addView(ch, cp);
        t.setContentDescription(r.label + "، " + Money.fa(Money.num(r.toman)) + " تومان");
        return t;
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        // The clock banner stays on top after live data arrives; it now reports the refresh time.
        String nowTs = Money.fa(String.format(Locale.US, "%02d:%02d",
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)));
        View doneBanner = updateBanner(nowTs, true);
        // Loading → done: fade the single card into its finished state (only after the first entry).
        if (bannerWasLoading && entered) {
            doneBanner.setAlpha(0f);
            doneBanner.animate().alpha(1f).setDuration(320).start();
        }
        bannerWasLoading = false;
        content.addView(doneBanner, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(a.kit.gap(8));

        // Every section is shown directly, in the saved (or default) priority order.
        for (String key : orderedKeys()) renderHomeSection(content, d, key);
        renderNotes(content, d.notes);
        staggerIn(content);
    }

    /**
     * TRIAL (light skin only): a soft gold silk drape in the corner of the welcome card.
     * Set HERO_SILK_TRIAL to false to return to the plain hero background.
     */
    private static final boolean HERO_SILK_TRIAL = false;

    private android.graphics.drawable.Drawable heroBackground() {
        android.graphics.drawable.Drawable base = Theme.executiveHero();
        if (!HERO_SILK_TRIAL || !Theme.isLight()) return base;
        try {
            android.graphics.drawable.Drawable silk = a.getDrawable(R.drawable.home_hero_light);
            if (silk == null) return base;
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{base, silk});
            ld.setLayerGravity(1, Gravity.END | Gravity.TOP);
            ld.setLayerWidth(1, Theme.dp(260));
            ld.setLayerHeight(1, Theme.dp(142));
            return ld;
        } catch (Exception e) {
            Diag.ignored("HomeScreen:heroSilk", e);
            return base;
        }
    }

    /** Decorative dot grid drawn behind the home content. */
    private static final class DotGrid extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint dot = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float step = Theme.dp(22);

        DotGrid() {
            dot.setColor(Theme.alpha(Theme.GOLD, Theme.isLight() ? 26 : 16));
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            float r = Theme.dp(1f);
            for (float y = b.top + step; y < b.bottom; y += step)
                for (float x = b.left + step; x < b.right; x += step)
                    c.drawCircle(x, y, r, dot);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** True while the clock card shows the loading state (so the finished state can fade in). */
    private boolean bannerWasLoading;

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
        else if ("trend".equals(key)) secTrend(content, d);
        else if ("compare".equals(key)) secCompare(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
    }

    /** Dashboard order from settings; unknown keys dropped, missing sections appended. */
    private List<String> orderedKeys() {
        List<String> out = new ArrayList<>();
        String[] known = {"kpis", "compare", "alerts", "due", "returns", "trend", "eod"};
        for (String k : a.settings.homeOrder().split(",")) {
            String t = k == null ? "" : k.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                for (String k2 : known) if (k2.equals(t)) { out.add(t); break; }
            }
        }
        for (String k2 : known) if (!out.contains(k2)) out.add(k2);
        // Older saved orders have no "compare": keep it right after the KPI row.
        out.remove("compare");
        out.add(out.indexOf("kpis") + 1, "compare");
        return out;
    }

    private void secKpis(LinearLayout content, Data d) {
        // ---- day KPIs (tap a tile → that day's detail list) ----
        // soft() deliberately returns null when one optional query fails. Keep the
        // remaining KPIs visible, but make the missing metric explicit and inert.
        List<Kit.Kpi> kpis = new ArrayList<>();
        // All four tiles read the same day (the snapshot day) from their daily series,
        // so sales, purchases, receipts and payments never show different dates.
        String anchor = snapshotDay(d);
        String anchorLbl = anchorLabel(anchor);
        kpis.add(dayKpiAt("فروش", "sales", dayRowAt(d.salesDaily, anchor), "docs", "فاکتور", Theme.GOLD, anchorLbl));
        kpis.add(dayKpiAt("خرید", "buy", dayRowAt(d.buyDaily, anchor), "docs", "فاکتور", Theme.GOLD, anchorLbl));
        kpis.add(dayKpiAt("دریافت", "dar_in", dayRowAt(d.inDaily, anchor), "count", "قبض", Theme.GOLD, anchorLbl));
        kpis.add(dayKpiAt("پرداخت", "dar_out", dayRowAt(d.outDaily, anchor), "count", "قبض", Theme.GOLD, anchorLbl));
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
        // Breathing room under the header toolbar so the first KPI row does not touch it.
        LinearLayout.LayoutParams kg = a.kit.lp(-1, -2);
        kg.topMargin = Theme.dp(12);
        content.addView(a.kit.kpiGrid(kpis, 2), kg);
        content.addView(a.kit.gap(12));
    }

    /** Average-sales comparison: its own card, so the manager can open it directly. */
    private void secCompare(LinearLayout content, Data d) {
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            // Same day as the KPI tiles (the latest day with sales), so the two never disagree.
            String compareDay = snapshotDay(d);
            String compareLabel = anchorLabel(compareDay);
            boolean partialDay = compareDay.equals(Jalali.todayStr());
            double todaySales = salesOnDay(d.salesDaily, compareDay);
            double yesterday = salesOnDay(d.salesDaily, Jalali.addDays(compareDay, -1));
            double avg7 = averageSalesBefore(d.salesDaily, compareDay, 7);
            double avg30 = averageSalesBefore(d.salesDaily, compareDay, 30);
            LinearLayout compare = a.kit.card(Theme.GOLD);
            compare.addView(sectionBanner(R.drawable.mi_bar_chart, "فروش " + compareLabel + " در مقایسه", "میانگین و مقایسه با روزهای قبل", v -> a.nav("sales")),
                    a.kit.lp(-1, -2));
            double deltaPct = avg7 > 0 ? (todaySales - avg7) * 100.0 / avg7 : 0;
            String delta = avg7 > 0 ? (deltaPct > 0 ? "+" : deltaPct < 0 ? "−" : "")
                    + Money.fa(String.format(Locale.US, "%.1f", Math.abs(deltaPct)).replace('.', '٫')) + "٪" : "—";
            int deltaColor = avg7 <= 0 ? Theme.MUTED : (todaySales >= avg7 ? Theme.SUCCESS : Theme.WARNING);
            List<Kit.Kpi> cmp = new ArrayList<>();
            // A day that is still running is not comparable yet: show the time instead of a false drop.
            String nowFa = Money.fa(String.format(Locale.US, "%02d:%02d",
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE)));
            String sub = partialDay ? "تا " + nowFa + " • ناقص" : "تغییر ۷ روزه " + delta;
            cmp.add(new Kit.Kpi(compareLabel, Money.compactRial(todaySales), sub, Theme.GOLD));
            cmp.add(new Kit.Kpi("دیروز", Money.compactRial(yesterday), "", Theme.isCups() ? Theme.INFO : Theme.TEXT));
            cmp.add(new Kit.Kpi("میانگین ۷ روز قبل", Money.compactRial(avg7), "", Theme.GOLD_SOFT));
            cmp.add(new Kit.Kpi("میانگین ۳۰ روز قبل", Money.compactRial(avg30), "", Theme.GOLD_SOFT));
            // Emerald 3D icon per tile, the same family as the KPI row.
            cmp.get(0).icon = R.drawable.mi_point_of_sale;
            cmp.get(1).icon = R.drawable.mi_calendar_month;
            cmp.get(2).icon = R.drawable.mi_trending_up;
            cmp.get(3).icon = R.drawable.mi_bar_chart;
            compare.addView(a.kit.kpiGrid(cmp, 2), a.kit.lp(-1, -2));
            if (todaySales <= 0)
                compare.addView(a.kit.hint("برای " + compareLabel + " هنوز فروشی ثبت نشده است. اعداد بالا از روزهای قبل محاسبه شده‌اند."),
                        a.kit.lp(-1, -2));
            a.kit.addCard(content, compare);
        }
    }

    /** The row for one day of a daily series; a day without data is an explicit zero row. */
    private Row dayRowAt(List<Row> rows, String day) {
        String target = Jalali.disp(day);
        if (rows != null) {
            for (Row r : rows) {
                if (target.equals(Jalali.disp(r.s("day")))) {
                    Row x = new Row();
                    x.putAll(r);
                    x.put("d", target);
                    return x;
                }
            }
        }
        Row empty = new Row();
        empty.put("d", target);
        empty.put("total", 0.0);
        empty.put("docs", 0L);
        empty.put("count", 0L);
        return empty;
    }

    /** "امروز" for the current day, otherwise the short Jalali label. */
    private String anchorLabel(String day) {
        return day.equals(Jalali.todayStr()) ? "امروز" : Jalali.shortLabel(day);
    }

    Kit.Kpi dayKpiAt(String title, String target, Row row, String countKey,
                     String countLabel, int accent, String dayLabel) {
        return new Kit.Kpi(title + " " + dayLabel, Money.compactRial(row.d("total")),
                Money.fa(String.valueOf(row.l(countKey))) + " " + countLabel, accent,
                () -> gotoDay(target, row));
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

    /** Summary tile: a 3D icon on a soft gold pill, with a label and a bold value. */
    private View summaryTile(int vector, String label, String value, int color) {
        LinearLayout t = a.kit.h();
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Theme.dp(10), Theme.dp(10), Theme.dp(10), Theme.dp(10));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Theme.alpha(Theme.GOLD, 20));
        bg.setCornerRadius(Theme.dp(16));
        t.setBackground(bg);
        ImageView iv = new ImageView(a);
        iv.setImageResource(Theme.chrome3d(vector));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        t.addView(iv, new LinearLayout.LayoutParams(Theme.dp(46), Theme.dp(46)));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(10), 0, 0, 0);
        col.addView(a.kit.text(label, 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        TextView v = a.kit.text(value, 14f, color, true);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(v, a.kit.lp(-1, -2));
        t.addView(col, a.kit.wlp(1f));
        return t;
    }

    /** Section header with a 3D icon, a title, a short subtitle and a tap-through chevron. */
    private View sectionBanner(int vector, String title, String sub, View.OnClickListener onClick) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(a);
        iv.setImageResource(Theme.chrome3d(vector));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Theme.alpha(Theme.GOLD, 34));
        iv.setBackground(bg);
        row.addView(iv, new LinearLayout.LayoutParams(Theme.dp(42), Theme.dp(42)));
        row.addView(a.kit.space(10));
        LinearLayout col = a.kit.v();
        col.addView(a.kit.text(title, 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        col.addView(a.kit.text(sub, 11f, Theme.MUTED, false), a.kit.lp(-1, -2));
        row.addView(col, a.kit.wlp(1f));
        TextView chev = a.kit.text("›", 20f, Theme.GOLD, true);
        chev.setGravity(Gravity.CENTER);
        chev.setVisibility(onClick == null ? View.GONE : View.VISIBLE);
        row.addView(chev, a.kit.lp(Theme.dp(18), -2));
        if (onClick != null) row.setOnClickListener(onClick);
        return row;
    }

    /** Compact tile for a breakdown item: icon above, label and value below, centred. */
    private View miniTile(int vector, String label, String value) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(Theme.dp(6), Theme.dp(8), Theme.dp(6), Theme.dp(8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Theme.alpha(Theme.GOLD, 18));
        bg.setCornerRadius(Theme.dp(14));
        t.setBackground(bg);
        ImageView iv = new ImageView(a);
        iv.setImageResource(Theme.chrome3d(vector));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        t.addView(iv, new LinearLayout.LayoutParams(Theme.dp(34), Theme.dp(34)));
        TextView l = a.kit.text(label, 10.5f, Theme.MUTED, false);
        l.setGravity(Gravity.CENTER);
        l.setSingleLine(true);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(l, a.kit.lp(-1, -2));
        TextView v = a.kit.text(value, 12.5f, Theme.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(v, a.kit.lp(-1, -2));
        return t;
    }

    private void secAlerts(LinearLayout content, Data d) {
        // ---- alerts ----
        LinearLayout alerts = a.kit.card(Theme.GOLD);
        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView alertIcon = new android.widget.ImageView(a);
        alertIcon.setImageResource(Theme.chrome3d(R.drawable.mi_zoom));
        alertIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        alertIcon.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        head.addView(alertIcon, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
        head.addView(a.kit.space(10));
        head.addView(a.kit.text("هشدار امروز", 14.5f, Theme.TEXT, true), a.kit.wlp(1f));
        final TextView badge = a.kit.text("", 11f, Theme.GOLD, true);
        badge.setPadding(Theme.dp(9), Theme.dp(3), Theme.dp(9), Theme.dp(3));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(Theme.alpha(Theme.GOLD, 36));
        pill.setCornerRadius(Theme.dp(12));
        badge.setBackground(pill);
        head.addView(badge, a.kit.lp(-2, -2));
        alerts.addView(head, a.kit.lp(-1, -2));
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
        badge.setText(n == 0 ? "✓ بدون هشدار" : Money.fa(String.valueOf(n)) + " مورد");
        if (n == 0) alerts.addView(a.kit.text("فعلاً هشدار مهمی وجود ندارد.", 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, alerts);
    }

    /**
     * Return documents of the last 30 days. dbo.back_sanad is the ledger of goods
     * moving back — out of the warehouse, back from a customer, and so on — so a
     * sudden rise here is a quality or logistics problem long before it shows up
     * in the profit figures.
     */
    private void secReturns(LinearLayout content, Data d) {
        LinearLayout c = a.kit.card(Theme.GOLD);
        c.addView(sectionBanner(R.drawable.icon3d_return, "برگشتی‌های ۳۰ روز اخیر", "ابطال و برگشت، ۳۰ روز گذشته", v -> a.nav("reports")),
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
            c.addView(summaryTile(R.drawable.mi_refresh, "برگشتی ۳۰ روز اخیر",
                    Money.fa(String.valueOf(n)) + " سند • " + Money.compactMoney(sum), Theme.GOLD), a.kit.lp(-1, -2));
            LinearLayout grid = a.kit.h();
            int shown = 0;
            for (Row r : d.returns) {
                if (shown >= 3) break;
                String label = r.s("label");
                if (label == null || label.trim().isEmpty()) continue;
                int retIcon = label.contains("ابطال") ? R.drawable.icon3d_cancel
                        : label.contains("فروش") ? R.drawable.icon3d_return
                        : label.contains("مشتری") ? R.drawable.mi_group : R.drawable.icon3d_return;
                if (shown++ > 0) grid.addView(a.kit.space(6));
                grid.addView(miniTile(retIcon, label, Money.fa(String.valueOf(r.l("docs"))) + " سند"), a.kit.wlp(1f));
            }
            if (shown > 0) c.addView(grid, a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, c);
    }

    private void secTrend(LinearLayout content, Data d) {
        // ---- 14-day sales trend ----
        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String endDay = snapshotDay(d);
            String period = snapshotLabel(d);
            LinearLayout c = a.kit.card(Theme.GOLD);
            c.addView(sectionBanner(R.drawable.icon3d_trend, "روند ۱۴ روزه فروش تا " + period, "فروش روزانه، ۱۴ روز اخیر", v -> a.nav("sales")), a.kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(a);
            List<Charts.Point> pts = filled(d.salesDaily, endDay);
            final TextView cap = a.kit.text(pts.isEmpty() ? "" : lastCap(pts), 11f, Theme.MUTED, false);
            ch.setData(pts, Theme.GOLD, Charts.MONEY);
            ch.setListener((idx, p) -> cap.setText(Money.fa(p.label) + " • " + Money.compactRial(p.value)));
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(190)));
            c.addView(cap, a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void secDue(LinearLayout content, Data d) {
        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = a.kit.card(Theme.INFO);
            c.addView(sectionBanner(R.drawable.mi_cheque, "سررسید ۳ روز آینده", "چک‌های دریافتی و پرداختی", v -> a.nav("cheques")), a.kit.lp(-1, -2));
            LinearLayout row = a.kit.h();
            boolean hasIn = d.dueIn != null && !d.dueIn.isEmpty();
            boolean hasOut = d.dueOut != null && !d.dueOut.isEmpty();
            if (hasIn) row.addView(summaryTile(R.drawable.mi_cheque, "دریافتی • " + Money.fa(String.valueOf(d.dueIn.size())) + " فقره",
                    Money.compactRial(dueInSum), Theme.SUCCESS), a.kit.wlp(1f));
            if (hasIn && hasOut) row.addView(a.kit.space(8));
            if (hasOut) row.addView(summaryTile(R.drawable.mi_cheque, "پرداختی • " + Money.fa(String.valueOf(d.dueOut.size())) + " فقره",
                    Money.compactRial(dueOutSum), Theme.GOLD), a.kit.wlp(1f));
            c.addView(row, a.kit.lp(-1, -2));
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
        c.addView(sectionBanner(R.drawable.mi_calendar_month, "جمع‌بندی پایان روز",
                when.isEmpty() ? "خلاصه روز جاری" : when, null), a.kit.lp(-1, -2));
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

    private int alert(LinearLayout box, Data d, int n) {
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
        return Money.fa(p.label) + " • " + Money.compactRial(p.value);
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
