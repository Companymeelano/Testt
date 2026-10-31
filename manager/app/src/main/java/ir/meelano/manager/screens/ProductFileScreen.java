package ir.meelano.manager.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.PersianText;
import ir.meelano.manager.core.ProductStockStatus;
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

    /** Visible way back to the screen the file was opened from (no hidden gesture needed). */
    private void addBack(LinearLayout content) {
        content.addView(a.kit.btnGhost("‹ بازگشت", Theme.VIOLET, v -> a.onBackPressed()), a.kit.lp(-2, -2));
        content.addView(a.kit.gap(6));
    }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        addBack(content);
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
                addBack(content);
                content.addView(a.kit.error(faError, () -> render(content)), a.kit.lp(-1, -2));
            }
        });
    }

    private void build(LinearLayout content, Data d) {
        content.removeAllViews();
        addBack(content);
        Row h = d.head;
        String name = PersianText.cleanDisplayLabel(h.s("naka"));
        if (name.isEmpty()) name = "کالا";
        String group = PersianText.cleanGroupLabel(h.s("groupName"));
        String code = PersianText.cleanDisplayLabel(h.s("StuffCode"));
        if (code.isEmpty()) code = PersianText.cleanDisplayLabel(h.s("coka"));
        if (code.isEmpty()) code = h.s("shka");
        String heroSub = (group.isEmpty() ? "" : group + "  •  ")
                + "کد کالا " + Money.ltrIsolate(Money.fa(code));
        content.addView(a.kit.hero(glyph(), name, heroSub, accent()), a.kit.lp(-1, -2));

        boolean stockKnown = h.has("mojkavah");
        double stock = h.d("mojkavah");
        double packSize = h.has("mohvah") ? h.d("mohvah") : 1d;
        double loose = h.d("mojkajoz");
        double pieces = stockKnown ? stock * packSize + loose : 0;
        ProductStockStatus.Band stockBand = ProductStockStatus.classify(stockKnown, stock, pieces,
                h.d("reopoint"), AtiranSchema.LOW_STOCK_VAH);
        LinearLayout badges = a.kit.h();
        badges.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        badges.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        badges.addView(a.kit.stockChip(ProductStockStatus.label(stockBand), stockTone(stockBand)), a.kit.lp(-2, -2));
        if (h.i("activeKnown") != 0) {
            badges.addView(a.kit.space(6));
            boolean active = ProductStockStatus.isActive(h.s("active"));
            badges.addView(a.kit.stockChip(active ? "فعال" : "غیرفعال", active ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        }
        content.addView(badges, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- movement totals (counting units) ----
        double in = 0, out = 0;
        for (Row r : d.kardex) {
            if (r.l("outFlag") == 1) out += Math.abs(r.d("qty"));
            else in += Math.abs(r.d("qty"));
        }
        String unit = PersianText.cleanDisplayLabel(h.s("vahsanj"));
        String stockText = stockKnown ? Money.qtyText(stock) : "نامشخص";
        if (stockKnown && Math.abs(loose) > 0.000001d) stockText += " + " + Money.qtyText(loose) + " عدد";
        List<Kit.Kpi> kpis = new ArrayList<>();
        Kit.Kpi currentStock = new Kit.Kpi("موجودی کنونی", stockText, unit, Theme.VIOLET);
        currentStock.fullValue = stockText;
        kpis.add(currentStock);
        kpis.add(new Kit.Kpi("ورود (ثبت‌شده)", Money.qtyText(in), "", Theme.SUCCESS));
        kpis.add(new Kit.Kpi("خروج (ثبت‌شده)", Money.qtyText(out), "", Theme.DANGER));
        kpis.add(new Kit.Kpi("تعداد حرکت", Money.num(d.kardex.size()), "سند", Theme.INFO));
        content.addView(a.kit.kpiGrid(kpis, 2), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- specification card ----
        LinearLayout spec = a.kit.card(Theme.VIOLET);
        spec.addView(a.kit.text("مشخصات کالا", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        if (!code.isEmpty()) spec.addView(a.kit.kv("کد کالا", Money.ltrIsolate(Money.fa(code)), Theme.TEXT), a.kit.lp(-1, -2));
        if (!unit.isEmpty()) spec.addView(a.kit.kv("واحد", unit, Theme.TEXT), a.kit.lp(-1, -2));
        if (h.has("mojkajoz") && Math.abs(loose) > 0.0005)
            spec.addView(a.kit.kv("موجودی جزء", Money.qtyText(loose), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.has("reopoint") && Math.abs(h.d("reopoint")) > 0.0005)
            spec.addView(a.kit.kv("نقطه سفارش", Money.qtyText(h.d("reopoint")), Theme.WARNING), a.kit.lp(-1, -2));
        double buy = h.has("buy_price") ? h.d("buy_price") : (h.has("pure_buy_price") ? h.d("pure_buy_price") : 0);
        if (buy > 0) spec.addView(a.kit.kv("قیمت خرید", Money.rialText(buy), Theme.TEXT), a.kit.lp(-1, -2));
        if (h.i("saleKnown") != 0) {
            if (h.has("FinalSalePrice") && h.d("FinalSalePrice") > 0)
                spec.addView(a.kit.kv("قیمت فروش", Money.rialText(h.d("FinalSalePrice")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
            else spec.addView(a.kit.kv("هشدار قیمت", "قیمت فروش ثبت نشده", Theme.WARNING), a.kit.lp(-1, -2));
        }
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
                    Money.signedQty(outFlag ? -Math.abs(r.d("qty")) : Math.abs(r.d("qty"))),
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

    private int stockTone(ProductStockStatus.Band band) {
        switch (band) {
            case NEGATIVE:
            case OUT: return Theme.DANGER;
            case REORDER: return Theme.INFO;
            case LOW: return Theme.WARNING;
            case NORMAL: return Theme.SUCCESS;
            case UNKNOWN:
            default: return Theme.MUTED;
        }
    }

    /** Sales and purchase acts link to their invoice; the rest show the document number only. */
    private static String invoiceScreenFor(int act) {
        if (act == 20 || act == 22) return "sales";
        if (act == 11) return "buy";
        return null;
    }

    private void openMovement(final Row r) {
        String opLabel = r.s("opLabel").isEmpty() ? AtiranSchema.actNameFallback(r.s("act")) : r.s("opLabel");
        boolean outFlag = r.l("outFlag") == 1;
        double q = Math.abs(r.d("qty"));
        int accent = outFlag ? Theme.DANGER : Theme.SUCCESS;
        LinearLayout body = a.kit.v();

        // header: direction pill, operation name and the document date
        LinearLayout head = a.kit.h();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(a.kit.pill(outFlag ? "خروج از انبار" : "ورود به انبار", accent), a.kit.lp(-2, -2));
        head.addView(a.kit.space(8));
        head.addView(a.kit.text(opLabel, 15f, Theme.TEXT, true), a.kit.wlp(1f));
        body.addView(head, a.kit.lp(-1, -2));
        body.addView(a.kit.text(Jalali.dispFa(r.s("date")), 11.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));

        // facts — quantity first, then price and the line value it adds up to
        body.addView(a.kit.kv("مقدار", Money.qtyText(q), accent), a.kit.lp(-1, -2));
        if (r.d("price") > 0) {
            body.addView(a.kit.kv("فی", Money.rialText(r.d("price")), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
            body.addView(a.kit.kv("ارزش", Money.rialText(r.d("price") * q), Theme.GOLD_SOFT), a.kit.lp(-1, -2));
        }
        if (!r.s("warehouse").isEmpty())
            body.addView(a.kit.kv("انبار", Money.fa(r.s("warehouse")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("doc").isEmpty())
            body.addView(a.kit.kv("شماره سند", Money.fa(r.s("doc")), Theme.TEXT), a.kit.lp(-1, -2));
        if (!r.s("descrip").isEmpty()) {
            LinearLayout note = a.kit.card(Theme.MUTED);
            note.addView(a.kit.text("شرح", 10.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
            note.addView(a.kit.text(r.s("descrip"), 12f, Theme.TEXT, false), a.kit.lp(-1, -2));
            body.addView(note, a.kit.lp(-1, -2));
        }

        // actions — the sheet is always dismissible and links to the invoice when there is one
        final String screenId = invoiceScreenFor((int) r.l("act"));
        final String doc = r.s("doc");
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        if (screenId == null || doc.isEmpty())
            body.addView(a.kit.hint("این حرکت به فاکتور فروش یا خرید مربوط نیست؛ فقط شماره سند ثبت شده است."), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(10), a.kit.lp(-1, -2));
        LinearLayout acts = a.kit.h();
        if (screenId != null && !doc.isEmpty()) {
            acts.addView(a.kit.btn("نمایش فاکتور " + Money.fa(doc), v -> {
                if (holder[0] != null) holder[0].dismiss();
                Screen sc = a.screen(screenId);
                if (sc instanceof TradeScreen) ((TradeScreen) sc).openInvoice(doc);
            }), a.kit.wlp(1f));
            acts.addView(a.kit.space(8));
        }
        acts.addView(a.kit.btn("بستن", v -> {
            if (holder[0] != null) holder[0].dismiss();
        }), a.kit.wlp(1f));
        body.addView(acts, a.kit.lp(-1, -2));

        holder[0] = a.kit.dialog("گردش کالا", body, true);
        holder[0].show();
    }
}
