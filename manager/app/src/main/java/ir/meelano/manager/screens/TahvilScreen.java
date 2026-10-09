package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.core.WarehouseWriter;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Invoice handover (v28): sales invoices to walk-in customers and to the
 * distributor, with a gorgeous PDF receipt. The warehouse only DELIVERS
 * here — nothing is typed except who received it.
 */
public class TahvilScreen extends Screen {
    public TahvilScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "tahvil"; }

    @Override
    public String title() { return "تحویل فاکتور"; }

    @Override
    public String glyph() { return "🛻"; }

    @Override
    public int accent() { return Theme.SUCCESS; }

    private List<Row> factors = new ArrayList<>();
    private LinearLayout content;

    @Override
    public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        content.addView(topCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        final LinearLayout box = a.kit.v();
        content.addView(box, a.kit.lp(-1, -2));
        box.addView(a.kit.hint("در حال دریافت فاکتورها…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whDeliveries(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                factors = rows == null ? new ArrayList<Row>() : rows;
                box.removeAllViews();
                if (factors.isEmpty()) {
                    box.addView(a.kit.empty("فاکتوری یافت نشد", null), a.kit.lp(-1, -2));
                    return;
                }
                List<Kit.Kpi> kpis = new ArrayList<>();
                kpis.add(new Kit.Kpi("فاکتورها", Money.fa(String.valueOf(factors.size())), "فقره",
                        Theme.SUCCESS));
                box.addView(a.kit.kpiGrid(kpis, 1), a.kit.lp(-1, -2));
                box.addView(a.kit.gap(10));
                for (Row r : factors) {
                    final Row row = r;
                    String no = row.s("id");
                    boolean handed = WarehouseWriter.handedToday(a, no);
                    String sub = row.s("cust");
                    if (!row.s("visitor").isEmpty()) sub += " • " + row.s("visitor");
                    sub += " • " + Money.fa(Jalali.disp(row.s("dt")));
                    View v = a.kit.personRow("فاکتور " + Money.fa(no),
                            sub.isEmpty() ? "—" : sub,
                            Money.fa(Money.compact(row.d("amount"))) + " تومان",
                            handed ? "✓ تحویل شد" : "منتظر تحویل",
                            handed ? Theme.SUCCESS : Theme.GOLD,
                            v2 -> openHandover(row));
                    LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                    p.setMargins(0, 0, 0, Theme.dp(10));
                    box.addView(v, p);
                }
            }

            @Override
            public void fail(String faError) {
                box.removeAllViews();
                box.addView(a.kit.empty("دریافت فاکتورها ممکن نشد", faError), a.kit.lp(-1, -2));
            }
        });
    }

    private View topCard() {
        LinearLayout card = a.kit.card(Theme.SUCCESS);
        card.addView(a.kit.text("🛻 تحویل فاکتور", 17f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.text("فاکتور را به مشتری حضوری یا موزع تحویل بدهید و رسید بگیرید",
                12f, Theme.MUTED, false), a.kit.lp(-1, -2));
        return card;
    }

    // ================= handover =================

    private void openHandover(final Row f) {
        a.kit.toast("در حال دریافت اقلام…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whDeliveryItems(m, f.s("id")));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> items) {
                handoverDialog(f, items == null ? new ArrayList<Row>() : items);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void handoverDialog(final Row f, final List<Row> items) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        body.addView(a.kit.kv("فاکتور", Money.fa(f.s("id")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مشتری", f.s("cust").isEmpty() ? "—" : f.s("cust"), Theme.TEXT),
                a.kit.lp(-1, -2));
        if (!f.s("phone").isEmpty())
            body.addView(a.kit.kv("تلفن", Money.fa(f.s("phone")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مبلغ", Money.fa(Money.compact(f.d("amount"))) + " تومان", Theme.TEXT),
                a.kit.lp(-1, -2));
        if (!items.isEmpty()) {
            body.addView(a.kit.text("اقلام (" + Money.fa(String.valueOf(items.size())) + ")",
                    13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
            };
            body.addView(a.kit.dataTable(cols, items, null), a.kit.lp(-1, -2));
        } else {
            body.addView(a.kit.hint("قلمی برای این فاکتور یافت نشد"), a.kit.lp(-1, -2));
        }
        body.addView(a.kit.gap(8));
        body.addView(a.kit.text("تحویل به", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final String[] mode = {"in"}; // in = walk-in customer, out = distributor
        final String[] distName = {""};
        final String[] distCell = {""};
        LinearLayout row = a.kit.h();
        final android.widget.Button[] bIn = new android.widget.Button[1];
        final android.widget.Button[] bOut = new android.widget.Button[1];
        final Runnable paint = () -> {
            // Rebuild is simplest: recreate the two buttons via visibility trick.
            bIn[0].setText("🧍 مشتری حضوری" + ("in".equals(mode[0]) ? " ✓" : ""));
            bOut[0].setText("🛻 موزع" + ("out".equals(mode[0]) ? " ✓" : ""));
        };
        bIn[0] = a.kit.btn("🧍 مشتری حضوری ✓", v -> {
            mode[0] = "in";
            paint.run();
        });
        bOut[0] = a.kit.btnGhost("🛻 موزع", Theme.GOLD, v -> {
            mode[0] = "out";
            paint.run();
            pickDistributor(distName, distCell, () -> {
            });
        });
        row.addView(bIn[0], a.kit.wlp(1f));
        row.addView(a.kit.space(8));
        row.addView(bOut[0], a.kit.wlp(1f));
        body.addView(row, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.gap(8));
        body.addView(a.kit.btn("ثبت تحویل + رسید PDF", v -> {
            String receiver;
            if ("out".equals(mode[0])) {
                if (distName[0].isEmpty()) {
                    a.kit.toast("موزع را انتخاب کنید");
                    return;
                }
                receiver = "موزع: " + distName[0]
                        + (distCell[0].isEmpty() ? "" : " (" + distCell[0] + ")");
            } else {
                receiver = "مشتری حضوری: " + (f.s("cust").isEmpty() ? "—" : f.s("cust"));
            }
            try {
                box[0].dismiss();
            } catch (Exception ignored) { }
            String keeper = AtiranAuth.sessionName(a);
            if (keeper.isEmpty()) keeper = AtiranAuth.sessionUser(a);
            WarehouseWriter.markHanded(a, f.s("id"), f.s("cust"), receiver, keeper);
            String sub = "مشتری: " + f.s("cust") + "  •  " + receiver
                    + "  •  تحویل‌دهنده: " + keeper + "  •  تاریخ: " + Jalali.todayStr()
                    + "  •  مبلغ: " + Money.compact(f.d("amount")) + " تومان";
            ReportCatalog.Col[] cols = new ReportCatalog.Col[]{
                    new ReportCatalog.Col("name", "کالا", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_TEXT),
                    new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
            };
            a.sharePdf("رسید تحویل فاکتور " + f.s("id"), sub, cols, items);
            if (content != null) render(content);
        }), a.kit.lp(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setView(sv).create();
        if (dlg.getWindow() != null) dlg.getWindow().setBackgroundDrawable(Theme.dialogBg());
        box[0] = dlg;
        dlg.show();
    }

    private void pickDistributor(final String[] name, final String[] cell, final Runnable done) {
        a.kit.toast("در حال دریافت موزع‌ها…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, MasterQueries.whVisitors(m));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    a.kit.toast("موزعی یافت نشد");
                    return;
                }
                LinearLayout b = a.kit.v();
                b.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
                final AlertDialog[] box = new AlertDialog[1];
                for (Row r : rows) {
                    final Row row = r;
                    String label = row.s("name")
                            + (row.s("cell").isEmpty() ? "" : " • " + row.s("cell"));
                    b.addView(a.kit.btnGhost(label, Theme.GOLD, v -> {
                        name[0] = row.s("name");
                        cell[0] = row.s("cell");
                        try {
                            box[0].dismiss();
                        } catch (Exception ignored) { }
                        a.kit.toast("موزع: " + name[0]);
                        done.run();
                    }), a.kit.lp(-1, -2));
                }
                box[0] = a.kit.dialog("انتخاب موزع", a.kit.scrollWrap(b, 420), true);
                box[0].show();
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }
}
