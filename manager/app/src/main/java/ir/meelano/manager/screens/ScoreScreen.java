package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplier scorecard (v29): purchase volume + open pre-invoices per
 * supplier, ranked. Best-effort over the buy tables.
 */
public class ScoreScreen extends Screen {
    public ScoreScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "score"; }

    @Override
    public String title() { return "امتیاز تأمین‌کننده"; }

    @Override
    public String glyph() { return "🏅"; }

    @Override
    public int accent() { return Theme.TEAL; }

    private List<Row> rows = new ArrayList<>();

    @Override
    public void render(LinearLayout content) {
        content.removeAllViews();
        LinearLayout hero = a.kit.card(Theme.TEAL);
        hero.addView(a.kit.text("🏅 امتیاز تأمین‌کننده", 17f, Theme.TEXT, true),
                a.kit.lp(-1, -2));
        hero.addView(a.kit.text("حجم خرید و پیش‌فاکتورهای باز هر تأمین‌کننده",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        content.addView(hero, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout box = a.kit.v();
        content.addView(box, a.kit.lp(-1, -2));
        box.addView(a.kit.hint("در حال محاسبه…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whSupplierStats(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> list) {
                rows = list == null ? new ArrayList<Row>() : list;
                box.removeAllViews();
                if (rows.isEmpty()) {
                    box.addView(a.kit.empty("تأمین‌کننده‌ای یافت نشد", null), a.kit.lp(-1, -2));
                    return;
                }
                double total = 0;
                boolean anyBuy = false;
                for (Row r : rows) {
                    total += r.d("buySum");
                    if (r.d("buySum") > 0 || r.l("buyN") > 0) anyBuy = true;
                }
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("تأمین‌کننده", Money.fa(String.valueOf(rows.size())), "",
                        Theme.TEAL));
                kpis.add(new Kit.Kpi("جمع خریدها", Money.fa(Money.compact(total)), "تومان",
                        Theme.GOLD));
                box.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
                box.addView(a.kit.gap(10));
                if (!anyBuy)
                    box.addView(a.kit.hint("ریز خرید در این دیتابیس در دسترس نیست؛ مانده‌حساب‌ها نمایش داده شد"),
                            a.kit.lp(-1, -2));
                String[] medals = {"🥇", "🥈", "🥉"};
                for (int i = 0; i < rows.size(); i++) {
                    Row r = rows.get(i);
                    String medal = i < 3 ? medals[i] + " " : "";
                    String sub = Money.fa(String.valueOf(r.l("buyN"))) + " خرید • "
                            + Money.fa(Money.compact(r.d("buySum"))) + " تومان";
                    if (r.l("pishN") > 0)
                        sub += " • " + Money.fa(String.valueOf(r.l("pishN"))) + " پیش‌فاکتور باز";
                    View v = a.kit.personRow(medal + r.s("name"), sub,
                            "مانده " + Money.fa(Money.compact(r.d("bal"))),
                            "#" + Money.fa(String.valueOf(i + 1)), Theme.TEAL, null);
                    LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                    p.setMargins(0, 0, 0, Theme.dp(10));
                    box.addView(v, p);
                }
                box.addView(a.kit.btnGhost("🧾 گزارش PDF", Theme.INFO, v -> pdf()),
                        a.kit.lp(-1, -2));
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                box.addView(a.kit.empty("محاسبه ممکن نشد", faError), a.kit.lp(-1, -2));
            }
        });
    }

    private void pdf() {
        try {
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                Row o = new Row();
                o.put("name", r.s("name"));
                o.put("buy", Money.fa(String.valueOf(r.l("buyN"))) + " / "
                        + Money.fa(Money.compact(r.d("buySum"))));
                o.put("pish", Money.fa(String.valueOf(r.l("pishN"))));
                o.put("bal", Money.fa(Money.compact(r.d("bal"))));
                out.add(o);
            }
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "تأمین‌کننده", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("buy", "تعداد / مبلغ خرید", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("pish", "پیش‌فاکتور باز", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("bal", "مانده", ReportCatalog.T_TEXT),
            };
            a.sharePdf("امتیاز تأمین‌کنندگان", "", cols, out);
        } catch (Exception e) {
            a.kit.toast("ساخت گزارش ممکن نشد");
        }
    }
}
