package ir.meelano.manager;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Company;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.data.Settings;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Shop TV mode: landscape, fullscreen, auto-rotating KPI slides with live Atiran data. */
public class TvActivity extends Activity {
    private static final long SLIDE_MS = 12000;
    private static final long REFRESH_MS = 5 * 60 * 1000;
    private static final int SLIDES = 6;

    private Kit kit;
    private Repo repo;
    private Settings settings;
    private LinearLayout slideBox;
    private LinearLayout dots;
    private TextView clock;
    private View progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int slide;
    private TvData data;
    private boolean alive;

    private static final class TvData {
        Row salesDay = new Row();
        Row inDay = new Row();
        Row bounced = new Row();
        Row prodSum = new Row();
        List<Row> daily = new ArrayList<>();
        List<Row> top = new ArrayList<>();
        List<Row> debtors = new ArrayList<>();
        List<Row> dueIn = new ArrayList<>();
        List<Row> dueOut = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.apply(this);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Exception ignored) { }
        settings = new Settings(this);
        kit = new Kit(this);
        repo = new Repo(this, settings);
        alive = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);
        root.setPadding(Theme.dp(24), Theme.dp(16), Theme.dp(24), Theme.dp(16));

        LinearLayout head = kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(kit.logo(54), new LinearLayout.LayoutParams(Theme.dp(54), Theme.dp(54)));
        head.addView(kit.space(12));
        String shop = Company.get(this).displayName(this);
        TextView name = kit.text(shop, 22, Theme.TEXT, true);
        head.addView(name, kit.wlp(1f));
        clock = kit.text("", 20, Theme.GOLD_SOFT, true);
        head.addView(clock, kit.lp(-2, -2));
        root.addView(head, kit.lp(-1, -2));
        progress = new View(this);
        progress.setBackgroundColor(Theme.GOLD);
        progress.setPivotX(0f);
        progress.setScaleX(0f);
        root.addView(progress, new LinearLayout.LayoutParams(-1, Theme.dp(3)));
        root.addView(kit.gap(8));

        ScrollView sv = new ScrollView(this);
        slideBox = kit.v();
        sv.addView(slideBox);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(kit.gap(8));

        dots = kit.h();
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, kit.lp(-1, -2));
        TextView exit = kit.text("برای خروج، دکمه برگشت را لمس کنید • لمس صفحه: اسلاید بعدی", 11f, Theme.MUTED, false);
        exit.setGravity(Gravity.CENTER);
        root.addView(exit, kit.lp(-1, -2));
        root.setOnClickListener(v -> nextSlide());

        setContentView(root);
        tickClock();
        load();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
                nextSlide();
                handler.postDelayed(this, SLIDE_MS);
            }
        }, SLIDE_MS);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
                load();
                handler.postDelayed(this, REFRESH_MS);
            }
        }, REFRESH_MS);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception ignored) { }
        try {
            repo.close();
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void nextSlide() {
        slide = (slide + 1) % SLIDES;
        renderSlide();
    }

    private void tickClock() {
        if (!alive) return;
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            String hm = String.format(java.util.Locale.US, "%02d:%02d",
                    c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
            clock.setText(Money.fa(hm) + " • " + kit.todayLine());
        } catch (Exception ignored) { }
        handler.postDelayed(() -> tickClock(), 20000);
    }

    private void load() {
        repo.run(c -> {
            Meta m = new Meta(c);
            TvData d = new TvData();
            try {
                d.salesDay = Repo.one(c, Queries.homeSalesDay(m));
            } catch (Exception ignored) { }
            try {
                d.inDay = Repo.one(c, MoneyQueries.darDay(m, 0));
            } catch (Exception ignored) { }
            try {
                String to = Jalali.todayStr();
                d.daily = Repo.exec(c, Queries.factorDaily(m, true, Jalali.addDays(to, -13), to));
            } catch (Exception ignored) { }
            try {
                Filter f = new Filter();
                f.preset = Filter.P_LAST30;
                f.applyPreset();
                d.top = Repo.exec(c, Queries.factorTopProducts(m, true, f, 8));
            } catch (Exception ignored) { }
            try {
                d.debtors = Repo.exec(c, Queries.topDebtors(m, 5));
            } catch (Exception ignored) { }
            try {
                d.dueIn = Repo.exec(c, MoneyQueries.chequeDue(m, true, 1));
            } catch (Exception ignored) { }
            try {
                d.dueOut = Repo.exec(c, MoneyQueries.chequeDue(m, false, 1));
            } catch (Exception ignored) { }
            try {
                d.bounced = Repo.one(c, MoneyQueries.bouncedTotal(m));
            } catch (Exception ignored) { }
            try {
                d.prodSum = Repo.one(c, MasterQueries.productsSummary(m));
            } catch (Exception ignored) { }
            return d;
        }, new Repo.Cb<TvData>() {
            @Override
            public void ok(TvData d) {
                data = d;
                renderSlide();
            }

            @Override
            public void fail(String faError) {
                slideBox.removeAllViews();
                slideBox.addView(kit.error(faError == null ? "خطا در دریافت داده" : faError, () -> load()), kit.lp(-1, -2));
            }
        });
    }

    private void renderSlide() {
        if (!alive || slideBox == null) return;
        slideBox.removeAllViews();
        paintDots();
        if (data == null) {
            slideBox.addView(kit.loading("در حال دریافت داده…"), kit.lp(-1, -2));
            return;
        }
        slideBox.setAlpha(0f);
        slideBox.animate().alpha(1f).setDuration(400).start();
        if (progress != null) {
            progress.animate().cancel();
            progress.setScaleX(0f);
            progress.animate().scaleX(1f).setDuration(SLIDE_MS)
                    .setInterpolator(new android.view.animation.LinearInterpolator()).start();
        }
        if (slide == 0) slideSales();
        else if (slide == 1) slideTop();
        else if (slide == 2) slideDebtors();
        else if (slide == 3) slideDue();
        else if (slide == 4) slideAlerts();
        else slideShop();
    }

    private void paintDots() {
        dots.removeAllViews();
        for (int i = 0; i < SLIDES; i++) {
            TextView t = kit.text("●", 12, i == slide ? Theme.GOLD : Theme.MUTED, true);
            t.setPadding(Theme.dp(6), 0, Theme.dp(6), 0);
            dots.addView(t, kit.lp(-2, -2));
        }
    }

    private void slideSales() {
        slideBox.addView(big("فروش امروز", Money.rial(data.salesDay.d("total")),
                Money.fa(String.valueOf(data.salesDay.l("docs"))) + " فاکتور • دریافت امروز " + Money.compactRial(data.inDay.d("total")),
                Theme.GOLD), kit.lp(-1, -2));
        slideBox.addView(kit.gap(10));
        if (data.daily != null && !data.daily.isEmpty()) {
            LinearLayout c = kit.card(Theme.GOLD);
            c.addView(kit.text("روند ۱۴ روزه فروش", 16f, Theme.TEXT, true), kit.lp(-1, -2));
            Charts.Area ch = new Charts.Area(this);
            List<Charts.Point> pts = new ArrayList<>();
            for (Row r : data.daily)
                pts.add(new Charts.Point(Jalali.shortLabel(r.s("day")), r.d("total")));
            ch.setData(pts, Theme.GOLD, Charts.COMPACT);
            c.addView(ch, new LinearLayout.LayoutParams(-1, Theme.dp(220)));
            slideBox.addView(c, kit.lp(-1, -2));
        }
    }

    private void slideTop() {
        slideBox.addView(big("پرفروش‌ترین کالاهای ۳۰ روز", "", "", Theme.SUCCESS), kit.lp(-1, -2));
        slideBox.addView(kit.gap(10));
        if (data.top == null || data.top.isEmpty()) {
            slideBox.addView(kit.empty("فروشی ثبت نشده است", null), kit.lp(-1, -2));
            return;
        }
        LinearLayout c = kit.card(Theme.SUCCESS);
        Charts.HBars hb = new Charts.HBars(this);
        List<Charts.Point> pts = new ArrayList<>();
        for (Row r : data.top) pts.add(new Charts.Point(r.s("label"), r.d("total")));
        hb.setData(pts, Charts.COMPACT);
        c.addView(hb, kit.lp(-1, -2));
        slideBox.addView(c, kit.lp(-1, -2));
    }

    private void slideDebtors() {
        double sum = 0;
        if (data.debtors != null) for (Row r : data.debtors) sum += r.d("amount");
        slideBox.addView(big("بدهکاران اولویت‌دار", Money.compactRial(sum), "جمع ۵ بدهکار بزرگ", Theme.DANGER), kit.lp(-1, -2));
        slideBox.addView(kit.gap(10));
        if (data.debtors == null || data.debtors.isEmpty()) {
            slideBox.addView(kit.empty("بدهکاری ثبت نشده است", null), kit.lp(-1, -2));
            return;
        }
        for (Row r : data.debtors) {
            View v = kit.personRow(r.s("party"), "کد " + Money.fa(r.s("code")),
                    Money.rial(r.d("amount")), "مانده بدهی", Theme.DANGER, null);
            LinearLayout.LayoutParams p = kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            slideBox.addView(v, p);
        }
    }

    private void slideDue() {
        double inSum = 0, outSum = 0;
        if (data.dueIn != null) for (Row r : data.dueIn) inSum += r.d("amount");
        if (data.dueOut != null) for (Row r : data.dueOut) outSum += r.d("amount");
        int n = (data.dueIn == null ? 0 : data.dueIn.size()) + (data.dueOut == null ? 0 : data.dueOut.size());
        slideBox.addView(big("سررسید امروز و فردا", Money.fa(String.valueOf(n)) + " فقره چک", "", Theme.INFO), kit.lp(-1, -2));
        slideBox.addView(kit.gap(10));
        LinearLayout row = kit.h();
        row.addView(big("دریافتی", Money.compactRial(inSum),
                Money.fa(String.valueOf(data.dueIn == null ? 0 : data.dueIn.size())) + " فقره", Theme.SUCCESS), kit.wlp(1f));
        row.addView(kit.space(10));
        row.addView(big("خروجی", Money.compactRial(outSum),
                Money.fa(String.valueOf(data.dueOut == null ? 0 : data.dueOut.size())) + " فقره", Theme.WARNING), kit.wlp(1f));
        slideBox.addView(row, kit.lp(-1, -2));
    }

    private void slideAlerts() {
        long bouncedN = data.bounced == null ? 0 : data.bounced.l("count");
        double bouncedSum = data.bounced == null ? 0 : data.bounced.d("total");
        long low = data.prodSum == null ? 0 : data.prodSum.l("low");
        long out = data.prodSum == null ? 0 : data.prodSum.l("out");
        int dueN = (data.dueIn == null ? 0 : data.dueIn.size()) + (data.dueOut == null ? 0 : data.dueOut.size());
        slideBox.addView(big("هشدارهای امروز", "", "", Theme.DANGER), kit.lp(-1, -2));
        slideBox.addView(kit.gap(10));
        LinearLayout row = kit.h();
        row.addView(big("چک برگشتی", Money.fa(String.valueOf(bouncedN)) + " فقره",
                Money.compactRial(bouncedSum), Theme.DANGER), kit.wlp(1f));
        row.addView(kit.space(10));
        row.addView(big("سررسید امروز و فردا", Money.fa(String.valueOf(dueN)) + " فقره", "", Theme.WARNING), kit.wlp(1f));
        row.addView(kit.space(10));
        row.addView(big("کم‌موجودی / ناموجود", Money.fa(String.valueOf(low)) + " / " + Money.fa(String.valueOf(out)),
                "قلم کالا", Theme.VIOLET), kit.wlp(1f));
        slideBox.addView(row, kit.lp(-1, -2));
    }

    private void slideShop() {
        String shop = Company.get(this).displayName(this);
        LinearLayout c = kit.card(Theme.GOLD);
        c.setGravity(Gravity.CENTER);
        c.addView(kit.logo(110), new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110)));
        TextView nm = kit.text(shop, 34, Theme.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        c.addView(nm, kit.lp(-1, -2));
        TextView t1 = kit.text("مدیریت میلانو • داشبورد مدیریتی آتیران", 16f, Theme.MUTED, true);
        t1.setGravity(Gravity.CENTER);
        c.addView(t1, kit.lp(-1, -2));
        TextView t2 = kit.text(kit.todayLine(), 15f, Theme.GOLD_SOFT, false);
        t2.setGravity(Gravity.CENTER);
        c.addView(t2, kit.lp(-1, -2));
        slideBox.addView(c, kit.lp(-1, -2));
    }

    /** Big TV banner: title + giant value + subtitle. */
    private View big(String title, String value, String sub, int accent) {
        LinearLayout c = kit.card(accent);
        c.setGravity(Gravity.CENTER);
        c.addView(kit.text(title, 18f, Theme.MUTED, true), kit.lp(-1, -2));
        if (value != null && !value.isEmpty()) {
            TextView v = kit.text(value, 44, accent, true);
            v.setGravity(Gravity.CENTER);
            c.addView(v, kit.lp(-1, -2));
        }
        if (sub != null && !sub.isEmpty()) {
            TextView s = kit.text(sub, 16f, Theme.TEXT, false);
            s.setGravity(Gravity.CENTER);
            c.addView(s, kit.lp(-1, -2));
        }
        return c;
    }
}
