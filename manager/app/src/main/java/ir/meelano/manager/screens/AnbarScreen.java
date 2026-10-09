package ir.meelano.manager.screens;

import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Warehouse home (v28, phase 2): today's pulse + doors to the warehouse flows. */
public class AnbarScreen extends Screen {
    public AnbarScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "anbar"; }

    @Override
    public String title() { return "انبار"; }

    @Override
    public String glyph() { return "📦"; }

    @Override
    public int accent() { return Theme.GOLD; }

    @Override
    public void render(LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout kpiBox = a.kit.v();
        content.addView(kpiBox, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.text("کارهای انبار", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(6));
        String[][] links = {
                {"tahvil", "تحویل فاکتور به مشتری و موزع + رسید"},
                {"resid", "رسید کالا: خرید و برگشتی‌ها (فقط تعداد)"},
                {"products", "کالاها و موجودی"},
                {"customers", "مشتریان و تأمین‌کنندگان"},
        };
        for (String[] l : links) {
            final String lid = l[0];
            Screen s = a.screen(lid);
            if (s == null) continue;
            content.addView(a.kit.navRow(s.glyph(), s.title(), l[1], s.accent(), v -> a.nav(lid)),
                    a.kit.lp(-1, -2));
        }
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whTodayStats(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                Row r = (rows == null || rows.isEmpty()) ? null : rows.get(0);
                long n = r == null ? 0 : r.l("salesN");
                double sum = r == null ? 0 : r.d("salesSum");
                long pish = r == null ? 0 : r.l("pishN");
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("فاکتور امروز", Money.fa(String.valueOf(n)), "فقره", Theme.GOLD));
                kpis.add(new Kit.Kpi("مبلغ امروز", Money.fa(Money.compact(sum)), "تومان", Theme.SUCCESS));
                kpis.add(new Kit.Kpi("پیش‌فاکتور خرید باز", Money.fa(String.valueOf(pish)), "سند", Theme.INFO));
                kpiBox.removeAllViews();
                kpiBox.addView(a.kit.kpiGrid(kpis, 3), a.kit.lp(-1, -2));
            }

            @Override
            public void fail(String faError) {
                kpiBox.removeAllViews();
                kpiBox.addView(a.kit.hint("آمار امروز در دسترس نیست (آفلاین؟)"), a.kit.lp(-1, -2));
            }
        });
    }

    private android.view.View heroCard() {
        LinearLayout card = a.kit.card(Theme.GOLD);
        String keeper = AtiranAuth.sessionName(a);
        if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
        card.addView(a.kit.text("📦 انبار", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String sub = "امروز " + Money.fa(Jalali.todayStr());
        if (!keeper.isEmpty()) sub += " • انباردار: " + keeper;
        card.addView(a.kit.text(sub, 12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }
}
