package ir.meelano.manager.screens;

import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Charts;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * پروندهٔ کالا: مشخصات، موجودی، نمودار گردش، و کاردکس کامل. هر حرکت را می‌توان باز کرد و اگر
 * حرکت مربوط به فاکتور فروش یا خرید باشد، همان فاکتور نمایش داده می‌شود.
 */
public class ProductFileScreen extends Screen {
    private static String pendingShka;

    /** Opens the file of one product (called from the product list). */
    public static void open(MainActivity a, String shka) {
        pendingShka = shka;
        a.nav("product_file");
    }

    public ProductFileScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "product_file"; }

    @Override
    public String title() { return "پرونده کالا"; }

    @Override
    public String glyph() { return "▦"; }

    @Override
    public int accent() { return Theme.VIOLET; }

    private static final class Data {
        Row head;
        List<Row> kardex = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        final String shka = pendingShka;
        if (shka == null || shka.trim().isEmpty()) {
            content.addView(a.kit.empty("کالایی انتخاب نشده", "از فهرست کالاها یک کالا را باز کنید"), a.kit.lp(-1, -2));
            return;
        }
        content.addView(a.kit.loading("در حال دریافت پروندهٔ کالا…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Meta m = new Meta(c);
            Data d = new Data();
            d.head = Repo.one(c, MasterQueries.productHeader(m, shka));
            d.kardex = soft(d.notes, "گردش", () -> Repo.exec(c, MasterQueries.productKardex(m, shka, 1500)));
            if (d.kardex == null) d.kardex = new ArrayList<>();
            if (d.head == null) throw new Exception("کالا یافت نشد");
            return d;
        }, new Repo.Cb<Data>() {
            @Override
            public void ok(Data d) {
                build(content, d);
            }

            @Override
            public void fail(String faError) {
                content.removeAllViews();
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        Row h = d.head;
        String name = h.s("naka");
        String group = h.s("groupName");
        content.addView(a.kit.hero(glyph(), name.isEmpty() ? "کالا" : name,
                (group.isEmpty() ? "" : group + "  •  ") + "شِکالا " + Money.fa(h.s("shka")), accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- movement totals (counting units) ----
        double in = 0, out = 0;
        for (Row r : d.kardex) {
            if (r.l("outFlag") == 1) out += Math.abs(r.d("qty"));
            else in += Math.abs(r.d("qty"));
        }
        double stock = h.d("mojkavah");
        List<Kit.Kpi> kpis = new ArrayList<>();
        kpis.add(new Kit.Kpi("موجودی کنونی", Money.qty(stock), h.s("vahsanj"), Theme.VIOLET));
        kpis.add(new Kit.Kpi("ورود (کل ثبت‌شده)", Money.qty(in), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("خروج (کل ثبت‌شده)", Money.qty(out), "", Theme.DANGER));
        kpis.add(new Kit.Kpi("تعداد حرکت", Money.fa(String.valueOf(d.kardex.size())), "سند", Theme.INFO));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- specification card ----
        LinearLayout spec = a.kit.card(Theme.VIOLET);
        spec.addView(a.kit.text("مشخصات کالا", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String code = h.has("StuffCode") ? h.s("StuffCode") : h.s("coka");
        if (!code.isEmpty()) spec.addView(a.kit.kv("کد", Money.fa(code), Theme.TEXT), a.kit.lp(-1, -2));
        if (!h.s("vahsanj").isEmpty()) spec.addView(a.kit.kv("واحد", h.s("vahsanj"), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.has("mojkajoz") && Math.abs(h.d("mojkajoz")) > 0.0005)
            spec.addView(a.kit.kv("موجودی جزء", Money.qty(h.d("mojkajoz")), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.has("reopoint") && Math.abs(h.d("reopoint")) > 0.0005)
            spec.addView(a.kit.kv("نقطه سفارش", Money.qty(h.d("reopoint")), Theme.WARNING), a.kit.lp(-1, -2));
        double buy = h.has("buy_price") ? h.d("buy_price") : (h.has("pure_buy_price") ? h.d("pure_buy_price") : 0);
        if (buy > 0) spec.addView(a.kit.kv("قیمت خرید", Money.rial(buy), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.has("FinalSalePrice") && h.d("FinalSalePrice") > 0)
            spec.addView(a.kit.kv("قیمت فروش", Money.rial(h.d("FinalSalePrice")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (h.has("sharh") && !h.s("sharh").isEmpty())
            spec.addView(a.kit.kv("شرح", h.s("sharh"), Theme.MUTED), a.kit.lp(-1, -2));
        a.kit.addCard(content, spec);

        if (d.kardex.isEmpty()) {
            content.addView(a.kit.empty("گردشی برای این کالا ثبت نشده", "کاردکس خالی است یا دسترسی به دفتر انبار وجود ندارد"),
                    a.kit.lp(-1, -2));
            renderNotes(content, d.notes);
            return;
        }

        // ---- running net movement over time (oldest first) ----
        List<Row> asc = new ArrayList<>(d.kardex);
        java.util.Collections.reverse(asc);
        List<Charts.Point> run = new ArrayList<>();
        double acc = 0;
        for (Row r : asc) {
            double q = Math.abs(r.d("qty"));
            acc += r.l("outFlag") == 1 ? -q : q;
            run.add(new Charts.Point(Jalali.shortLabel(r.s("date")), acc));
        }
        LinearLayout ch = a.kit.card(Theme.VIOLET);
        ch.addView(a.kit.text("روند تجمعی موجودی در دفتر گردش", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        ch.addView(a.kit.hint("نمودار مقدار خالص ورود منهای خروج از اولین حرکت ثبت‌شدهٔ این کالا"), a.kit.lp(-1, -2));
        Charts.Area area = new Charts.Area(a);
        area.setData(run, Theme.VIOLET, Charts.COMPACT);
        ch.addView(area, a.kit.lp(-1, Theme.dp(190)));
        a.kit.addCard(content, ch);

        // ---- distribution by operation ----
        Map<String, Double> byOp = new LinkedHashMap<>();
        for (Row r : d.kardex) {
            String k = r.s("opLabel").isEmpty() ? "عملیات" : r.s("opLabel");
            byOp.put(k, (byOp.containsKey(k) ? byOp.get(k) : 0) + Math.abs(r.d("qty")));
        }
        List<Charts.Point> pts = new ArrayList<>();
        for (Map.Entry<String, Double> e : byOp.entrySet()) pts.add(new Charts.Point(e.getKey(), e.getValue()));
        LinearLayout dc = a.kit.card(Theme.GOLD);
        dc.addView(a.kit.text("ترکیب گردش بر اساس عملیات", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        Charts.Donut donut = new Charts.Donut(a);
        donut.setData(pts, "گردش", Money.fa(String.valueOf(d.kardex.size())) + " سند");
        dc.addView(donut, a.kit.lp(-1, -2));
        a.kit.addCard(content, dc);

        // ---- movement list (newest first, tap a movement) ----
        content.addView(a.kit.sectionHead("کاردکس کالا", Money.fa(String.valueOf(d.kardex.size())) + " حرکت", null),
                a.kit.lp(-1, -2));
        content.addView(a.kit.gap(6));
        int shown = 0;
        for (final Row r : d.kardex) {
            if (shown++ >= 300) break;
            boolean outFlag = r.l("outFlag") == 1;
            String opLabel = r.s("opLabel").isEmpty() ? AtiranSchema.actNameFallback(r.s("act")) : r.s("opLabel");
            String mid = (r.s("doc").isEmpty() ? "بدون شماره سند" : "سند " + Money.fa(r.s("doc")))
                    + (r.s("warehouse").isEmpty() ? "" : "  •  انبار " + Money.fa(r.s("warehouse")));
            View row = a.kit.docRow(opLabel, mid, Jalali.shortLabel(r.s("date")),
                    (outFlag ? "−" : "+") + Money.qty(Math.abs(r.d("qty"))),
                    outFlag ? "خروج" : "ورود", outFlag ? Theme.DANGER : Theme.SUCCESS,
                    v -> openMovement(r));
            LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
            p.setMargins(0, 0, 0, Theme.dp(8));
            content.addView(row, p);
        }
        if (d.kardex.size() > shown)
            content.addView(a.kit.hint("برای " + Money.fa(String.valueOf(shown)) + " حرکت اول نمایش داده شد"), a.kit.lp(-1, -2));
        renderNotes(content, d.notes);
    }

    /** Sales and purchase acts link to their invoice; the rest show the document number only. */
    private static String invoiceScreenFor(int act) {
        if (act == 20 || act == 22) return "sales";
        if (act == 11) return "buy";
        return null;
    }

    private void openMovement(final Row r) {
        LinearLayout body = a.kit.v();
        String opLabel = r.s("opLabel").isEmpty() ? AtiranSchema.actNameFallback(r.s("act")) : r.s("opLabel");
        boolean outFlag = r.l("outFlag") == 1;
        body.addView(a.kit.kv("تاریخ", Jalali.dispFa(r.s("date")), Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("عملیات", opLabel, Theme.TEXT), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("جهت", outFlag ? "خروج از انبار" : "ورود به انبار",
                outFlag ? Theme.DANGER : Theme.SUCCESS), a.kit.lp(-1, -2));
        body.addView(a.kit.kv("مقدار", Money.qty(Math.abs(r.d("qty"))), Theme.TEXT), a.kit.lp(-1, -2));
        if (r.d("price") > 0) body.addView(a.kit.kv("فی", Money.rial(r.d("price")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        if (!r.s("warehouse").isEmpty()) body.addView(a.kit.kv("انبار", Money.fa(r.s("warehouse")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("doc").isEmpty()) body.addView(a.kit.kv("شماره سند", Money.fa(r.s("doc")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("descrip").isEmpty()) body.addView(a.kit.kv("شرح", r.s("descrip"), Theme.MUTED), a.kit.lp(-1, -2));
        final String screenId = invoiceScreenFor(r.l("act"));
        final String doc = r.s("doc");
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        if (screenId != null && !doc.isEmpty()) {
            body.addView(a.kit.gap(10), a.kit.lp(-1, -2));
            body.addView(a.kit.btn("نمایش فاکتور " + Money.fa(doc), v -> {
                if (holder[0] != null) holder[0].dismiss();
                Screen s = a.screen(screenId);
                if (s instanceof TradeScreen) ((TradeScreen) s).openInvoice(doc);
            }), a.kit.lp(-1, -2));
        } else {
            body.addView(a.kit.hint("این حرکت به فاکتور فروش یا خرید مربوط نیست؛ فقط شماره سند ثبت شده است."), a.kit.lp(-1, -2));
        }
        holder[0] = a.kit.dialog("گردش کالا", body, false);
        holder[0].show();
    }
}
