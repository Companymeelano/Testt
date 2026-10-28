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
        Row salesDay = new Row();
        Row buyDay = new Row();
        Row inDay = new Row();
        Row outDay = new Row();
        List<Row> overdue = new ArrayList<>();
        List<Row> salesDaily = new ArrayList<>();
        List<Row> topItems = new ArrayList<>();
        List<Row> monthlySales = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
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
        return Theme.isLight() ? light : dark;
    }

    private enum BannerState { LOADING, UPDATED, OFFLINE }

    /** Compact, truthful sync status; cached data is never presented as freshly updated. */
    private View updateBanner(String savedTs, BannerState state, View.OnClickListener retry) {
        LinearLayout box = a.kit.h();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Theme.dp(13), Theme.dp(9), Theme.dp(13), Theme.dp(9));
        box.setMinimumHeight(Theme.dp(64));
        int accent = state == BannerState.OFFLINE ? Theme.WARNING
                : state == BannerState.UPDATED ? Theme.SUCCESS : Theme.GOLD;
        android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                Theme.isLight() ? new int[]{0xFFF8F5EA, 0xFFF1EBDD} : new int[]{0xFF11151D, 0xFF0B0E14});
        surface.setCornerRadius(Theme.dp(18));
        surface.setStroke(Theme.dp(1), Theme.alpha(accent, Theme.isLight() ? 110 : 90));
        box.setBackground(surface);

        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 25 : 34)));
        if (state == BannerState.LOADING) {
            android.widget.ProgressBar spinner = new android.widget.ProgressBar(a, null,
                    android.R.attr.progressBarStyleSmall);
            spinner.setIndeterminate(true);
            if (android.os.Build.VERSION.SDK_INT >= 21) spinner.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(accent));
            badge.addView(spinner, new FrameLayout.LayoutParams(Theme.dp(26), Theme.dp(26), Gravity.CENTER));
        } else {
            android.widget.ImageView icon = new android.widget.ImageView(a);
            icon.setImageResource(state == BannerState.UPDATED ? R.drawable.mi_check_circle : R.drawable.mi_wifi_off);
            icon.setColorFilter(accent);
            icon.setContentDescription(null);
            badge.addView(icon, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        }
        box.addView(badge, new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38)));
        box.addView(a.kit.space(11));

        LinearLayout copy = a.kit.v();
        String title;
        String detail;
        if (state == BannerState.LOADING) {
            title = "در حال تازه‌سازی اطلاعات";
            detail = savedTs == null || savedTs.isEmpty()
                    ? "آخرین اطلاعات فروشگاه در حال دریافت است…"
                    : "دادهٔ ذخیره‌شده نمایش داده می‌شود · آخرین دریافت: " + Money.fa(savedTs);
        } else if (state == BannerState.UPDATED) {
            title = "داشبورد به‌روز شد";
            detail = "آخرین دریافت زنده: " + Money.fa(savedTs == null || savedTs.isEmpty() ? "—" : savedTs);
        } else if (savedTs == null || savedTs.isEmpty()) {
            title = "اتصال برقرار نشد؛ دادهٔ ذخیره‌شده‌ای موجود نیست";
            detail = "برای دریافت اطلاعات، اتصال را بررسی کنید و دوباره تلاش کنید.";
        } else {
            title = "اتصال برقرار نشد؛ دادهٔ ذخیره‌شده نمایش داده می‌شود";
            detail = "آخرین دریافت موفق: " + Money.fa(savedTs);
        }
        TextView heading = a.kit.text(title, 12.5f, Theme.TEXT, true);
        heading.setMaxLines(1);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading, a.kit.lp(-1, -2));
        TextView subtitle = a.kit.text(detail, 10.5f, Theme.MUTED, false);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(subtitle, a.kit.lp(-1, -2));
        box.addView(copy, a.kit.wlp(1f));
        box.setContentDescription(title + ". " + detail
                + (retry == null ? "" : " برای تلاش دوباره لمس کنید."));
        if (retry != null) {
            box.setFocusable(true);
            Theme.pressable(box);
            box.setOnClickListener(retry);
        }
        return box;
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
            bannerWasLoading = true;
            build(content, cached, BannerState.LOADING, cachedTs[0]);
        } else {
            content.removeAllViews();
            bannerWasLoading = true;
            content.addView(updateBanner("", BannerState.LOADING, null), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
            content.addView(greetingHero(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
            // Market data is independent of the Atiran dashboard query and can render immediately.
            content.addView(marketCard(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
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
                build(content, shared, BannerState.UPDATED, currentTimeLabel());
            } else {
                showFailure(content, kpiErr[0], cached, cachedTs[0]);
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

    /** Daily operating figures, overdue alerts and active returns. */
    private Data loadKpis(final int token, java.sql.Connection c0) throws Exception {
        final Link L = new Link(c0);
        Data d = new Data();
        final String to = Jalali.todayStr();
        d.snapshotDay = to;
        d.salesDay = liveL(token, L, d.notes, "فروش روز", () -> Repo.one(L.c, Queries.homeSalesDay(L.m)));
        d.buyDay = liveL(token, L, d.notes, "خرید روز", () -> Repo.one(L.c, Queries.homeBuyDay(L.m)));
        d.inDay = liveL(token, L, d.notes, "دریافت روز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 0)));
        d.outDay = liveL(token, L, d.notes, "پرداخت روز", () -> Repo.one(L.c, MoneyQueries.darDay(L.m, 1)));
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
        final String from = Jalali.addDays(to, -30);
        d.salesDaily = liveL(token, L, d.notes, "روند فروش", () -> Repo.exec(L.c, Queries.factorDaily(L.m, true, from, to)));
        Filter todayFilter = new Filter();
        todayFilter.from = to;
        todayFilter.to = to;
        todayFilter.top = 5;
        d.topItems = liveL(token, L, d.notes, "پرفروش‌ترین کالاهای امروز",
                () -> Repo.exec(L.c, Queries.factorTopProducts(L.m, true, todayFilter, 5)));
        d.monthlySales = liveL(token, L, d.notes, "فروش ماهانه",
                () -> Repo.exec(L.c, MasterQueries.monthly(L.m, true, 12)));
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
        s.overdue = g.overdue;
        s.returns = g.returns;
        s.notes.putAll(g.notes);
    }

    private static void mergeCharts(Data s, Data g) {
        s.salesDaily = g.salesDaily;
        s.topItems = g.topItems;
        s.monthlySales = g.monthlySales;
        s.dueIn = g.dueIn;
        s.dueOut = g.dueOut;
        s.notes.putAll(g.notes);
    }

    private void showFailure(final LinearLayout content, String faError, Data cached, String cachedTs) {
        if (cached != null) {
            build(content, cached, BannerState.OFFLINE, cachedTs);
            LinearLayout bc = a.kit.card(Theme.GOLD);
            bc.addView(a.kit.text("جزئیات اتصال", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            bc.addView(a.kit.kv("خطا", faError == null ? "" : faError, Theme.MUTED), a.kit.lp(-1, -2));
            bc.addView(a.kit.btn("تلاش مجدد", v -> render(content)), a.kit.lp(-1, -2));
            content.addView(bc, 2);
            content.addView(a.kit.gap(8), 3);
            return;
        }
        content.removeAllViews();
        content.addView(updateBanner("", BannerState.OFFLINE, v -> render(content)), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(marketCard(), a.kit.lp(-1, -2));
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
                    .append("|out=").append(rowStr(d.outDay));
            if (d.topItems != null) {
                for (int i = 0; i < Math.min(5, d.topItems.size()); i++)
                    s.append("|top").append(i).append('=').append(rowStr(d.topItems.get(i)));
            }
            if (d.salesDaily != null) {
                for (int i = 0; i < Math.min(31, d.salesDaily.size()); i++)
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
                else if ("sales".equals(k)) { d.salesDay = rowFrom(v); any = true; }
                else if ("buy".equals(k)) { d.buyDay = rowFrom(v); any = true; }
                else if ("in".equals(k)) { d.inDay = rowFrom(v); any = true; }
                else if ("out".equals(k)) { d.outDay = rowFrom(v); any = true; }
                else if (k.matches("top[0-4]")) { d.topItems.add(rowFrom(v)); any = true; }
                else if (k.matches("trend(?:[0-9]|[12][0-9]|30)")) { d.salesDaily.add(rowFrom(v)); any = true; }
                else if (k.matches("month(?:[0-9]|1[01])")) { d.monthlySales.add(rowFrom(v)); any = true; }
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
        hero.setPadding(Theme.dp(16), Theme.dp(12), Theme.dp(16), Theme.dp(11));
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
            crest.addView(li, new FrameLayout.LayoutParams(Theme.dp(44), Theme.dp(44), Gravity.CENTER));
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
            crest.addView(mark, new FrameLayout.LayoutParams(Theme.dp(44), Theme.dp(44), Gravity.CENTER));
        }
        FrameLayout crestWrap = new FrameLayout(a);
        android.graphics.drawable.GradientDrawable halo = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.alpha(Theme.GOLD, 90), Theme.alpha(Theme.GOLD, 0)});
        halo.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        View haloView = new View(a);
        haloView.setBackground(halo);
        crestWrap.addView(haloView, new FrameLayout.LayoutParams(-1, -1));
        crestWrap.addView(crest, new FrameLayout.LayoutParams(Theme.dp(56), Theme.dp(56), Gravity.CENTER));
        top.addView(crestWrap, new LinearLayout.LayoutParams(Theme.dp(64), Theme.dp(64)));
        top.addView(a.kit.space(9));

        LinearLayout copy = a.kit.v();
        TextView overline = a.kit.text("میلانو زرین‌فرمان  •  اتاق فرمان", 10.5f, Theme.GOLD_SOFT, true);
        overline.setSingleLine(true);
        copy.addView(overline, a.kit.lp(-1, -2));
        TextView headline = a.kit.text(greeting + "، " + roleName, 23f, Theme.TEXT, true);
        headline.setMaxLines(1);
        // Long names shrink to fit the line instead of being cut off.
        headline.setAutoSizeTextTypeUniformWithConfiguration(16, 23, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams hp = a.kit.lp(-1, -2);
        hp.setMargins(0, Theme.dp(2), 0, 0);
        copy.addView(headline, hp);
        TextView description = a.kit.text("تصویر روشن از فروش، مالی و عملیات", 11f, Theme.MUTED, false);
        description.setSingleLine(true);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(description, a.kit.lp(-1, -2));
        top.addView(copy, a.kit.wlp(1f));

        hero.addView(top, a.kit.lp(-1, -2));
        hero.addView(a.kit.gap(9));
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
    private LinearLayout marketView;
    private int marketViewGeneration = Integer.MIN_VALUE;

    private View marketCard() {
        final int gen = a.renderGen();
        // A cache paint and its later database paint belong to one render; keep one ticker request/view.
        if (marketView != null && marketViewGeneration == gen) return marketView;
        final LinearLayout card = a.kit.card(Theme.GOLD);
        marketView = card;
        marketViewGeneration = gen;
        // Show the last saved values and place straight away; the live refresh replaces them below.
        String savedPlace = MarketWeather.cached(a, PLACE_KEY);
        if (savedPlace != null && !savedPlace.isEmpty()) weatherPlace = savedPlace;
        weatherFromDevice = "1".equals(MarketWeather.cached(a, DEVICE_KEY));
        renderMarket(card, MarketWeather.cached(a, MarketWeather.ratesKey()),
                MarketWeather.cached(a, MarketWeather.weatherKey()), false, false);
        new Thread(() -> {
            // Position: last known or one quick fix (worker thread). Denied/unavailable falls back to the store city.
            android.location.Location loc = null;
            try { loc = ir.meelano.manager.core.DeviceLocation.resolve(a, 6000); } catch (Exception e) { Diag.ignored("Home:location", e); }
            final boolean fromDevice = loc != null;
            final double lat = fromDevice ? loc.getLatitude() : MarketWeather.AHVAZ_LAT;
            final double lon = fromDevice ? loc.getLongitude() : MarketWeather.AHVAZ_LON;
            String place = fromDevice ? ir.meelano.manager.core.DeviceLocation.placeName(a, lat, lon) : null;
            if (place == null) place = fromDevice ? "موقعیت شما" : MarketWeather.AHVAZ_NAME + " · موقعیت فروشگاه";

            String rRaw = null;
            String wRaw = null;
            try {
                String candidate = MarketWeather.get(MarketWeather.RATES_URL);
                MarketWeather.parseRates(candidate); // never replace a known-good cache with malformed JSON
                rRaw = candidate;
            } catch (Exception e) { Diag.ignored("Home:ratesFetch", e); }
            try {
                String candidate = MarketWeather.get(MarketWeather.weatherUrl(lat, lon));
                MarketWeather.parseWeather(candidate);
                wRaw = candidate;
            } catch (Exception e) { Diag.ignored("Home:weatherFetch", e); }
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
        String marketState = rates == null || rates.isEmpty() ? "قیمت ناموجود"
                : rLive ? "قیمت آنلاین" : "قیمت ذخیره‌شده";
        int marketStateColor = rates == null || rates.isEmpty() ? Theme.WARNING : rLive ? Theme.SUCCESS : Theme.MUTED;
        head.addView(a.kit.pill(marketState, marketStateColor), a.kit.lp(-2, -2));
        card.addView(head, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

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
                int tileWidth = Math.max(138, Math.min(176, (screenDp - 66) / 2));
                row.addView(rateTile(rates.get(i)), new LinearLayout.LayoutParams(Theme.dp(tileWidth), -2));
            }
            ticker.addView(row, new android.view.ViewGroup.LayoutParams(-2, -2));
            card.addView(ticker, a.kit.lp(-1, -2));
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
            try {
                if (!Theme.isLight()) {
                    // A single 3D weather emblem identifies the section; the text below carries
                    // the actual changing condition, so the icon never misstates the forecast.
                    wi.setImageResource(R.drawable.icon3d_weather);
                } else {
                    wi.setImageResource(wRes);
                    wi.setColorFilter(Theme.GOLD);
                }
            } catch (Exception ignored) { Diag.ignored("HomeScreen:weatherIcon", ignored); }
            wr.addView(wi, a.kit.lp(Theme.dp(44), Theme.dp(44)));
            wr.addView(a.kit.space(8));
            LinearLayout wc = a.kit.v();
            TextView line = a.kit.text(weatherPlace + " • " + Money.fa(String.valueOf(Math.round(w.temp))) + "°  "
                    + MarketWeather.describe(w.code), 12f, Theme.TEXT, true);
            line.setMaxLines(1);
            line.setEllipsize(TextUtils.TruncateAt.END);
            wc.addView(line, a.kit.lp(-1, -2));
            LinearLayout weatherMeta = a.kit.h();
            weatherMeta.setGravity(Gravity.CENTER_VERTICAL);
            weatherMeta.addView(a.kit.text("رطوبت " + Money.fa(String.valueOf(w.humidity)) + "٪", 10.5f, Theme.MUTED, false),
                    a.kit.wlp(1f));
            weatherMeta.addView(a.kit.pill(wLive ? "آب‌وهوا زنده" : "آب‌وهوا ذخیره‌شده",
                    wLive ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
            wc.addView(weatherMeta, a.kit.lp(-1, -2));
            wr.addView(wc, a.kit.wlp(1f));
            card.addView(wr, a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.hint("اطلاعات آب‌وهوا فعلاً در دسترس نیست."), a.kit.lp(-1, -2));
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
        View rule = new View(a);
        rule.setBackgroundColor(Theme.alpha(Theme.GOLD, 48));
        card.addView(rule, a.kit.lp(-1, Theme.dp(1)));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.hint("منبع قیمت: TGJU · واحد: تومان\nزمان منبع: "
                + Money.fa(stamp == null || stamp.isEmpty() ? "نامشخص" : stamp)
                + " · دریافت برنامه: " + marketFreshness(MarketWeather.ratesKey(), rLive)), a.kit.lp(-1, -2));
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
        String time = String.format(Locale.US, "%02d:%02d", saved.get(Calendar.HOUR_OF_DAY), saved.get(Calendar.MINUTE));
        return (live ? "هم‌اکنون · " : "ذخیره‌شده · ") + day + " " + Money.fa(time);
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
        price.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams pp = a.kit.lp(-1, -2);
        pp.setMargins(0, Theme.dp(6), 0, 0);
        t.addView(price, pp);
        // Unit is smaller and quieter than the figure.
        TextView unit = a.kit.text("تومان", 9f, Theme.MUTED, false);
        unit.setMaxLines(1);
        unit.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(unit, a.kit.lp(-1, -2));
        int color = r.changePct > 0 ? Theme.SUCCESS : (r.changePct < 0 ? Theme.DANGER : Theme.MUTED);
        String arrow = r.changePct > 0 ? "↗ +" : (r.changePct < 0 ? "↘ −" : "");
        String changeText = Math.abs(r.changePct) < 0.05 ? "بدون تغییر"
                : arrow + Money.fa(String.format(Locale.US, "%.1f", Math.abs(r.changePct))) + "٪";
        TextView ch = a.kit.text(changeText, 10f, color, true);
        ch.setMaxLines(1);
        ch.setGravity(Gravity.CENTER_HORIZONTAL);
        ch.setBackground(Theme.pill(color));
        ch.setPadding(Theme.dp(8), Theme.dp(2), Theme.dp(8), Theme.dp(2));
        LinearLayout.LayoutParams cp = a.kit.lp(-2, -2);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.setMargins(0, Theme.dp(6), 0, 0);
        t.addView(ch, cp);
        String trendDescription = Math.abs(r.changePct) < 0.05 ? "بدون تغییر"
                : (r.changePct > 0 ? "افزایش " : "کاهش ")
                + Money.fa(String.format(Locale.US, "%.1f", Math.abs(r.changePct))) + " درصد";
        t.setContentDescription(r.label + "، " + Money.fa(Money.num(r.toman)) + " تومان، " + trendDescription);
        return t;
    }

    private String currentTimeLabel() {
        Calendar now = Calendar.getInstance();
        return a.kit.todayLine() + " ساعت " + Money.fa(String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
    }

    private void build(LinearLayout content, Data d, BannerState bannerState, String bannerTime) {
        content.removeAllViews();
        View status = updateBanner(bannerTime, bannerState,
                bannerState == BannerState.OFFLINE ? v -> render(content) : null);
        // The compact status changes state in place after the first cache/live transition.
        if (bannerWasLoading && entered) {
            status.setAlpha(0f);
            status.animate().alpha(1f).setDuration(220).start();
        }
        bannerWasLoading = false;
        content.addView(status, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(greetingHero(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        // The former "dashboard details" accordion is gone. Operational and analytical
        // sections now have a clear, compact hierarchy and remain directly discoverable.
        boolean marketAdded = false;
        for (String key : orderedKeys()) {
            LinearLayout block = a.kit.v();
            renderHomeSection(block, d, key);
            if (block.getChildCount() == 0) continue;
            content.addView(block, a.kit.lp(-1, -2));
            // Let the operational KPIs lead; market and weather remain visible but secondary.
            if ("kpis".equals(key)) {
                content.addView(a.kit.gap(6));
                content.addView(marketCard(), a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
                marketAdded = true;
            }
        }
        if (!marketAdded) {
            content.addView(marketCard(), a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
        }
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
        else if ("sales_insight".equals(key)) secSalesInsight(content, d);
        else if ("cash_mix".equals(key)) secCashMix(content, d);
        else if ("due".equals(key)) secDue(content, d);
        else if ("returns".equals(key)) secReturns(content, d);
        else if ("eod".equals(key)) secEod(content);
    }

    /** Dashboard order from settings; unknown keys dropped, missing sections appended. */
    private List<String> orderedKeys() {
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
        card.addView(dashboardSectionHead("گردش امروز", R.drawable.mi_account_balance_wallet,
                Theme.GOLD, "فروش ›", v -> a.nav("sales")), a.kit.lp(-1, -2));
        card.addView(a.kit.text("چهار شاخص اصلی؛ لمس هر کارت، ریز همان روز را باز می‌کند.",
                10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        Kit.Kpi[] items = {
                dayKpi("فروش", "sales", d.salesDay, "docs", "فاکتور", Theme.GOLD),
                dayKpi("خرید", "buy", d.buyDay, "docs", "فاکتور", Theme.VIOLET),
                dayKpi("دریافت", "dar_in", d.inDay, "count", "قبض", Theme.SUCCESS),
                dayKpi("پرداخت", "dar_out", d.outDay, "count", "قبض", Theme.INFO)
        };
        int[] icons = {R.drawable.mi_point_of_sale, R.drawable.mi_shopping_cart,
                R.drawable.mi_receipt_long, R.drawable.mi_payments};
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = a.kit.h();
            row.addView(flowTile(items[i], icons[i]), a.kit.wlp(1f));
            row.addView(a.kit.space(8));
            row.addView(flowTile(items[i + 1], icons[i + 1]), a.kit.wlp(1f));
            LinearLayout.LayoutParams rp = a.kit.lp(-1, -2);
            if (i > 0) rp.topMargin = Theme.dp(8);
            card.addView(row, rp);
        }

        if (d.salesDaily != null && !d.salesDaily.isEmpty()) {
            String compareDay = snapshotDay(d);
            double todaySales = salesOnDay(d.salesDaily, compareDay);
            double yesterday = salesOnDay(d.salesDaily, Jalali.addDays(compareDay, -1));
            double avg7 = averageSalesBefore(d.salesDaily, compareDay, 7);
            double avg30 = averageSalesBefore(d.salesDaily, compareDay, 30);
            String delta = avg7 > 0 ? Money.pct((todaySales - avg7) * 100.0 / avg7) : "—";
            int deltaColor = avg7 <= 0 ? Theme.MUTED : (todaySales >= avg7 ? Theme.SUCCESS : Theme.WARNING);
            card.addView(a.kit.gap(8));
            card.addView(a.kit.divider());
            TextView compare = a.kit.text("فروش " + snapshotLabel(d) + " • " + Money.compactRial(todaySales)
                    + "   |   تغییر نسبت به میانگین ۷ روز: " + delta, 10.5f, deltaColor, true);
            card.addView(compare, a.kit.lp(-1, -2));
            TextView history = a.kit.text("دیروز " + Money.compactRial(yesterday)
                    + "   •   میانگین ۷ روز " + Money.compactRial(avg7)
                    + "   •   میانگین ۳۰ روز " + Money.compactRial(avg30), 9.8f, Theme.MUTED, false);
            history.setMaxLines(2);
            card.addView(history, a.kit.lp(-1, -2));
        }
        a.kit.addCard(content, card);
    }

    private View flowTile(Kit.Kpi metric, int iconVector) {
        LinearLayout tile = a.kit.v();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(Theme.dp(9), Theme.dp(8), Theme.dp(9), Theme.dp(8));
        tile.setBackground(Theme.kpi(metric.accent));
        LinearLayout heading = a.kit.h();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(a.kit.icon(iconVector, metric.accent, 23), a.kit.lp(Theme.dp(28), Theme.dp(28)));
        heading.addView(a.kit.space(5));
        TextView label = a.kit.text(metric.label, 10.5f, Theme.MUTED, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(label, a.kit.wlp(1f));
        tile.addView(heading, a.kit.lp(-1, -2));
        TextView value = a.kit.text(metric.value, 14f, Theme.TEXT, true);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(value, a.kit.lp(-1, -2));
        TextView note = a.kit.text(metric.sub, 9.5f, metric.accent, false);
        note.setSingleLine(true);
        note.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(note, a.kit.lp(-1, -2));
        if (metric.action != null) {
            tile.setContentDescription(metric.label + "، " + metric.value + "، " + metric.sub);
            Theme.pressable(tile);
            tile.setOnClickListener(v -> metric.action.run());
        }
        return tile;
    }

    /** Dashboard-only art mapping keeps gold and nut identities distinct without changing other screens. */
    private int dashboardIcon(int vector) {
        if (Theme.isNuts()) {
            if (vector == R.drawable.mi_warning) return R.drawable.icon3d_medal_alert;
            if (vector == R.drawable.mi_account_balance_wallet) return R.drawable.icon3d_medal_coin;
            if (vector == R.drawable.mi_assignment_return) return R.drawable.icon3d_medal_refresh;
            if (vector == R.drawable.mi_sell) return R.drawable.icon3d_medal_dollar;
            if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_medal_products;
            if (vector == R.drawable.mi_bar_chart) return R.drawable.icon3d_medal_reports;
            if (vector == R.drawable.mi_receipt_long) return R.drawable.icon3d_medal_cash_in;
            if (vector == R.drawable.mi_calendar_month) return R.drawable.icon3d_medal_dues;
        }
        if (Theme.DARK) {
            if (vector == R.drawable.mi_account_balance_wallet) return R.drawable.icon3d_treasury;
            if (vector == R.drawable.mi_warning) return R.drawable.icon3d_dark_bell;
            if (vector == R.drawable.mi_assignment_return) return R.drawable.icon3d_dark_refresh;
            if (vector == R.drawable.mi_sell) return R.drawable.icon3d_dark_tag;
            if (vector == R.drawable.mi_grid_view) return R.drawable.icon3d_dark_products;
            if (vector == R.drawable.mi_bar_chart) return R.drawable.icon3d_dark_reports;
            if (vector == R.drawable.mi_receipt_long) return R.drawable.icon3d_dark_cash_in;
            if (vector == R.drawable.mi_calendar_month) return R.drawable.icon3d_dark_dues;
        }
        int themed = Theme.icon3d(vector);
        if (Theme.isIcon3d(themed)) return themed;
        if (vector == R.drawable.mi_warning) return R.drawable.icon3d_dark_bell;
        if (vector == R.drawable.mi_account_balance_wallet) return R.drawable.icon3d_coin;
        if (vector == R.drawable.mi_assignment_return) return R.drawable.icon3d_refresh;
        return R.drawable.icon3d_gold;
    }

    private ImageView dashboardArt(int iconVector) {
        ImageView image = new ImageView(a);
        image.setImageResource(dashboardIcon(iconVector));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    /** Explicitly sized, untinted 3D header icon for each dashboard card. */
    private View dashboardSectionHead(String title, int iconVector, int accent,
                                      String actionLabel, View.OnClickListener action) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(3), Theme.dp(4), Theme.dp(3), Theme.dp(7));
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 38 : 48)));
        badge.addView(dashboardArt(iconVector),
                new FrameLayout.LayoutParams(Theme.dp(30), Theme.dp(30), Gravity.CENTER));
        row.addView(badge, new LinearLayout.LayoutParams(Theme.dp(38), Theme.dp(38)));
        row.addView(a.kit.space(8));
        TextView label = a.kit.text(title, 14f, accent, true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, a.kit.wlp(1f));
        if (actionLabel != null && action != null) {
            TextView button = a.kit.text(actionLabel, 10.5f, accent, true);
            button.setPadding(Theme.dp(9), Theme.dp(6), Theme.dp(9), Theme.dp(6));
            button.setBackground(Theme.ghostButton(accent));
            button.setContentDescription(actionLabel.replace("›", "") + "؛ باز کردن");
            Theme.pressable(button);
            button.setOnClickListener(action);
            row.addView(a.kit.space(6));
            row.addView(button, a.kit.lp(-2, -2));
        }
        return row;
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
        alerts.addView(dashboardSectionHead("هشدار امروز", R.drawable.mi_warning,
                Theme.DANGER, null, null), a.kit.lp(-1, -2));
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
     * Return documents of the last 30 days. dbo.back_sanad is the ledger of goods
     * moving back — out of the warehouse, back from a customer, and so on — so a
     * sudden rise here is a quality or logistics problem long before it shows up
     * in the profit figures.
     */
    private void secReturns(LinearLayout content, Data d) {
        LinearLayout card = a.kit.card(Theme.DANGER);
        card.addView(dashboardSectionHead("برگشتی‌های فعال • ۳۰ روز", R.drawable.mi_assignment_return,
                Theme.DANGER, "گزارش ›", v -> a.nav("reports")),
                a.kit.lp(-1, -2));
        long docs = 0;
        double total = 0;
        double max = 0;
        if (d.returns != null) for (Row r : d.returns) {
            docs += r.l("docs");
            total += r.d("total");
            max = Math.max(max, r.d("total"));
        }
        if (docs == 0) {
            card.addView(a.kit.kv("سند برگشتی فعالی در این بازه ثبت نشده", "✓", Theme.SUCCESS), a.kit.lp(-1, -2));
        } else {
            LinearLayout summary = a.kit.h();
            summary.setGravity(Gravity.CENTER_VERTICAL);
            summary.addView(dashboardArt(R.drawable.mi_assignment_return),
                    a.kit.lp(Theme.dp(30), Theme.dp(30)));
            summary.addView(a.kit.space(7));
            summary.addView(a.kit.text(Money.fa(String.valueOf(docs)) + " سند برگشتی",
                    12f, Theme.TEXT, true), a.kit.wlp(1f));
            summary.addView(a.kit.text(Money.compactMoney(total), 12f, Theme.DANGER, true), a.kit.lp(-2, -2));
            card.addView(summary, a.kit.lp(-1, -2));
            int shown = 0;
            for (Row r : d.returns) {
                if (shown++ >= 3) break;
                String label = r.s("label");
                if (label == null || label.trim().isEmpty()) continue;
                String value = Money.fa(String.valueOf(r.l("docs"))) + " سند • " + Money.compactMoney(r.d("total"));
                card.addView(a.kit.progressLine(label, value, max > 0 ? r.d("total") / max : 0, Theme.DANGER),
                        a.kit.lp(-1, -2));
            }
        }
        card.addView(a.kit.text("ابطالی‌های فاکتور در آمار اسناد برگشتی لحاظ نمی‌شوند.",
                9.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        a.kit.addCard(content, card);
    }

    /** Compact sales pulse: one useful daily chart, ranked products and month-over-month context. */
    private void secSalesInsight(LinearLayout content, Data d) {
        boolean hasDaily = d.salesDaily != null && !d.salesDaily.isEmpty();
        boolean hasProducts = d.topItems != null && !d.topItems.isEmpty();
        boolean hasMonths = d.monthlySales != null && !d.monthlySales.isEmpty();
        if (!hasDaily && !hasProducts && !hasMonths) return;

        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(dashboardSectionHead("نبض فروش", R.drawable.mi_bar_chart,
                Theme.GOLD, "فروش ›", v -> a.nav("sales")), a.kit.lp(-1, -2));

        if (hasDaily) {
            String endDay = snapshotDay(d);
            List<Charts.Point> points = filled(d.salesDaily, endDay);
            double today = salesOnDay(d.salesDaily, endDay);
            double avg7 = averageSalesBefore(d.salesDaily, endDay, 7);
            if (avg7 > 0) {
                double change = (today - avg7) * 100.0 / avg7;
                int color = today >= avg7 ? Theme.SUCCESS : Theme.WARNING;
                String direction = today >= avg7 ? "بالاتر" : "پایین‌تر";
                LinearLayout pulse = a.kit.h();
                pulse.setGravity(Gravity.CENTER_VERTICAL);
                pulse.setPadding(Theme.dp(9), Theme.dp(7), Theme.dp(9), Theme.dp(7));
                pulse.setBackground(Theme.kpi(color));
                pulse.addView(a.kit.text("فروش " + snapshotLabel(d), 10.5f, Theme.MUTED, false), a.kit.wlp(1f));
                pulse.addView(a.kit.text(Money.pct(Math.abs(change)) + " " + direction + " از میانگین ۷ روز",
                        10.5f, color, true), a.kit.lp(-2, -2));
                card.addView(pulse, a.kit.lp(-1, -2));
                card.addView(a.kit.gap(4));
            }
            Charts.Area chart = new Charts.Area(a);
            TextView caption = a.kit.text(points.isEmpty() ? "" : lastCap(points), 10f, Theme.MUTED, false);
            chart.setData(points, Theme.GOLD, Charts.COMPACT);
            chart.setListener((idx, point) -> caption.setText(Money.fa(point.label) + " • " + Money.rial(point.value)));
            card.addView(chart, new LinearLayout.LayoutParams(-1, Theme.dp(126)));
            card.addView(caption, a.kit.lp(-1, -2));
        }

        if (hasProducts) {
            if (hasDaily) card.addView(a.kit.gap(7));
            card.addView(insightSubhead("پرفروش‌های " + snapshotLabel(d), R.drawable.mi_grid_view, Theme.GOLD),
                    a.kit.lp(-1, -2));
            int shown = Math.min(3, d.topItems.size());
            double max = 0;
            for (int i = 0; i < shown; i++) max = Math.max(max, d.topItems.get(i).d("total"));
            for (int i = 0; i < shown; i++) {
                Row item = d.topItems.get(i);
                String label = item.s("label");
                if (label == null || label.trim().isEmpty()) label = "کالای بدون نام";
                card.addView(a.kit.progressLine(Money.fa(String.valueOf(i + 1)) + " • " + label,
                        Money.compactRial(item.d("total")), max > 0 ? item.d("total") / max : 0, Theme.GOLD),
                        a.kit.lp(-1, -2));
                TextView quantity = a.kit.text(Money.qty(item.d("qty")) + " واحد • "
                        + Money.fa(String.valueOf(item.l("docs"))) + " ردیف", 9.5f, Theme.MUTED, false);
                quantity.setGravity(Gravity.END);
                card.addView(quantity, a.kit.lp(-1, -2));
            }
        }

        if (hasMonths) {
            Row current = d.monthlySales.get(0);
            Row previous = d.monthlySales.size() > 1 ? d.monthlySales.get(1) : null;
            String currentMonth = current.s("month");
            String previousMonth = previous == null ? null : previous.s("month");
            if (currentMonth != null || previous != null) {
                card.addView(a.kit.gap(7));
                card.addView(insightSubhead("مقایسهٔ ماهانه", R.drawable.mi_sell, Theme.GOLD),
                        a.kit.lp(-1, -2));
                LinearLayout months = a.kit.h();
                months.addView(insightMetric(currentMonth == null ? "ماه جاری" : currentMonth.replace('-', '/'),
                        Money.compactRial(current.d("total")), Theme.GOLD), a.kit.wlp(1f));
                if (previous != null) {
                    months.addView(a.kit.space(7));
                    months.addView(insightMetric(previousMonth == null ? "ماه قبل" : previousMonth.replace('-', '/'),
                            Money.compactRial(previous.d("total")), Theme.INFO), a.kit.wlp(1f));
                }
                card.addView(months, a.kit.lp(-1, -2));
                if (previous != null && previous.d("total") > 0) {
                    double change = (current.d("total") - previous.d("total")) * 100.0 / previous.d("total");
                    int color = current.d("total") >= previous.d("total") ? Theme.SUCCESS : Theme.WARNING;
                    card.addView(a.kit.text("تغییر نسبت به ماه قبل: " + Money.pct(change),
                            10f, color, true), a.kit.lp(-1, -2));
                }
            }
        }
        a.kit.addCard(content, card);
    }

    /** Payment mix is easier to scan as four proportional rails than as another large chart. */
    private void secCashMix(LinearLayout content, Data d) {
        if (d.inDay == null || d.inDay.d("total") <= 0) return;
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(dashboardSectionHead("ترکیب دریافت " + dayOf(d.inDay), R.drawable.mi_receipt_long,
                Theme.SUCCESS, "دریافت‌ها ›", v -> a.nav("dar_in")), a.kit.lp(-1, -2));
        card.addView(a.kit.text("جمع " + Money.compactRial(d.inDay.d("total")),
                11f, Theme.TEXT, true), a.kit.lp(-1, -2));
        addPaymentMix(card, "نقد", d.inDay.d("cash"), d.inDay.d("total"), Theme.SUCCESS);
        addPaymentMix(card, "کارت", d.inDay.d("pos"), d.inDay.d("total"), Theme.INFO);
        addPaymentMix(card, "حواله", d.inDay.d("havaleh"), d.inDay.d("total"), Theme.GOLD);
        addPaymentMix(card, "چک", d.inDay.d("cheque"), d.inDay.d("total"), Theme.VIOLET);
        a.kit.addCard(content, card);
    }

    private void addPaymentMix(LinearLayout card, String label, double amount, double total, int color) {
        if (amount <= 0) return;
        card.addView(a.kit.progressLine(label, Money.compactRial(amount), amount / total, color),
                a.kit.lp(-1, -2));
    }

    private View insightSubhead(String title, int icon, int accent) {
        LinearLayout row = a.kit.h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView art = dashboardArt(icon);
        row.addView(art, a.kit.lp(Theme.dp(25), Theme.dp(25)));
        row.addView(a.kit.space(6));
        row.addView(a.kit.text(title, 11f, accent, true), a.kit.wlp(1f));
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
        TextView amount = a.kit.text(value, 11.5f, accent, true);
        amount.setSingleLine(true);
        amount.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(amount, a.kit.lp(-1, -2));
        return tile;
    }

    private void secDue(LinearLayout content, Data d) {
        // ---- due cheques ----
        double dueInSum = sum(d.dueIn);
        double dueOutSum = sum(d.dueOut);
        if ((d.dueIn != null && !d.dueIn.isEmpty()) || (d.dueOut != null && !d.dueOut.isEmpty())) {
            LinearLayout c = a.kit.card(Theme.INFO);
            c.addView(dashboardSectionHead("سررسید ۳ روز آینده", R.drawable.mi_calendar_month,
                    Theme.INFO, "چک‌ها ›", v -> a.nav("cheques")), a.kit.lp(-1, -2));
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
        TextView summary = a.kit.text(txt, 12f, Theme.TEXT, false);
        summary.setMaxLines(3);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(summary, a.kit.lp(-1, -2));
        c.addView(a.kit.gap(6));
        final String share = "🌙 جمع‌بندی پایان روز میلانو زرین‌فرمان" + (when.isEmpty() ? "" : " (" + when + ")")
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
