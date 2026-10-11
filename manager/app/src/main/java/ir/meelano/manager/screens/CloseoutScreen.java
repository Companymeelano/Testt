package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AtiranAuth;
import ir.meelano.manager.core.CloseoutDb;
import ir.meelano.manager.core.HandoverDb;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Atiran;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/** Manager's day-close checklist: reconciles separate Atiran books without inventing links. */
public class CloseoutScreen extends Screen {
    private String day = Jalali.todayStr();
    private LinearLayout content;
    private Data current;

    private static final class Data {
        Row sales = new Row();
        Row receipts = new Row();
        Row payments = new Row();
        Row deliveries = new Row();
        List<Row> salesStatus = new ArrayList<>();
        List<Row> returns = new ArrayList<>();
        List<Row> incomingChecks = new ArrayList<>();
        List<Row> outgoingChecks = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
        JSONObject closed;
        boolean sharedHandover;
        boolean closeTable;
    }

    public CloseoutScreen(MainActivity a) { super(a); }
    @Override public String id() { return "closeout"; }
    @Override public String title() { return "بستن روز"; }
    @Override public String glyph() { return "☑"; }
    @Override public int accent() { return Theme.GOLD; }

    @Override public void render(LinearLayout content) {
        this.content = content;
        load();
    }

    private void load() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(),
                "تطبیق برای " + Money.fa(day) + " • مبالغ با واحد انتخاب‌شده", accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout top = a.kit.card(Theme.STEEL);
        LinearLayout row = a.kit.h();
        row.addView(a.kit.text("تاریخ: " + Money.fa(day), 13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("انتخاب", Theme.GOLD, v -> a.kit.dateDialog(
                "تاریخ بستن روز", day, picked -> { day = Jalali.normalizeDate(picked); load(); })), a.kit.lp(-2, -2));
        top.addView(row, a.kit.lp(-1, -2));
        top.addView(a.kit.gap(6));
        top.addView(a.kit.btn("⟳ تازه‌سازی", v -> load()), a.kit.lp(-1, -2));
        content.addView(top, a.kit.lp(-1, -2));
        content.addView(a.kit.loading("در حال خواندن دفاتر روز…"), a.kit.lp(-1, -2));

        final String selected = day;
        a.repo.run(c -> loadData(c, selected), new Repo.Cb<Data>() {
            @Override public void ok(Data d) {
                if (content == null || !selected.equals(day)) return;
                current = d;
                build(d);
            }
            @Override public void fail(String faError) {
                if (content == null || !selected.equals(day)) return;
                content.removeAllViews();
                content.addView(a.kit.hero(glyph(), title(), "تطبیق برای " + Money.fa(day), accent()),
                        a.kit.lp(-1, -2));
                content.addView(a.kit.gap(8));
                content.addView(a.kit.error(faError, () -> load()), a.kit.lp(-1, -2));
            }
        });
    }

    private Data loadData(Connection c, String selected) {
        final Data d = new Data();
        Meta m = new Meta(c);
        ir.meelano.manager.core.Filter f = new ir.meelano.manager.core.Filter();
        f.from = selected;
        f.to = selected;
        f.top = 500;
        f.page = 0;
        d.sales = safe(d, "فروش", conn -> Repo.one(conn, Queries.factorSummary(m, true, f)), new Row(), c);
        d.receipts = safe(d, "دریافت‌ها", conn -> Repo.one(conn, MoneyQueries.darSummary(m, f, 0)), new Row(), c);
        d.payments = safe(d, "پرداخت‌ها", conn -> Repo.one(conn, MoneyQueries.darSummary(m, f, 1)), new Row(), c);
        d.salesStatus = safe(d, "وضعیت تأیید فاکتورها", conn -> Repo.exec(conn,
                Queries.factorStatusSummary(m, true, selected, selected)), new ArrayList<Row>(), c);
        d.returns = safe(d, "اسناد برگشتی", conn -> Repo.exec(conn, MasterQueries.returnsByKindOn(m, f)),
                new ArrayList<Row>(), c);
        d.incomingChecks = safe(d, "چک‌های دریافتیِ ثبت‌شده در روز", conn -> Repo.exec(conn,
                MoneyQueries.chequeListOn(m, true, f)), new ArrayList<Row>(), c);
        d.outgoingChecks = safe(d, "چک‌های پرداختیِ ثبت‌شده در روز", conn -> Repo.exec(conn,
                MoneyQueries.chequeListOn(m, false, f)), new ArrayList<Row>(), c);
        HandoverDb.ensure(c);
        d.sharedHandover = HandoverDb.available(c);
        d.deliveries = safe(d, "فاکتورهای روز و وضعیت تحویل", conn -> Repo.one(conn,
                MasterQueries.whDeliverySummary(m, selected, d.sharedHandover)), new Row(), c);
        CloseoutDb.ensure(c);
        d.closeTable = CloseoutDb.available(c);
        if (d.closeTable) d.closed = CloseoutDb.find(c, selected);
        else d.notes.put("ثبت بستن روز", "جدول جانبی Meelano قابل خواندن نیست؛ تطبیق فقط نمایش داده می‌شود.");
        return d;
    }

    private <T> T safe(Data d, String label, Repo.Task<T> task, T fallback, Connection c) {
        try {
            return task.run(c);
        } catch (Exception e) {
            String message = e.getMessage();
            d.notes.put(label, message == null || message.trim().isEmpty()
                    ? Atiran.diagnose(e) : message);
            return fallback;
        }
    }

    private void build(Data d) {
        content.removeAllViews();
        LinearLayout top = a.kit.card(Theme.STEEL);
        LinearLayout row = a.kit.h();
        row.addView(a.kit.text("تطبیق روز " + Money.fa(day), 13f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("تغییر تاریخ", Theme.GOLD, v -> a.kit.dateDialog(
                "تاریخ بستن روز", day, picked -> { day = Jalali.normalizeDate(picked); load(); })), a.kit.lp(-2, -2));
        top.addView(row, a.kit.lp(-1, -2));
        top.addView(a.kit.gap(6));
        top.addView(a.kit.btn("⟳ تازه‌سازی", v -> load()), a.kit.lp(-1, -2));
        content.addView(top, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        if (!d.notes.isEmpty()) {
            LinearLayout warning = a.kit.card(Theme.WARNING);
            warning.addView(a.kit.text("برخی دفاتر در دسترس نبودند", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
            for (Map.Entry<String, String> e : d.notes.entrySet())
                warning.addView(a.kit.kv(e.getKey(), e.getValue(), Theme.WARNING), a.kit.lp(-1, -2));
            content.addView(warning, a.kit.lp(-1, -2));
            content.addView(a.kit.gap(8));
        }

        LinearLayout sales = a.kit.card(Theme.GOLD);
        sales.addView(a.kit.sectionHead("فروش و تأیید فاکتور", "فاکتورها ›", v -> a.nav("sales")), a.kit.lp(-1, -2));
        sales.addView(a.kit.kv("مجموع فروش", Money.compactRial(d.sales.d("total")), Theme.GOLD), a.kit.lp(-1, -2));
        sales.addView(a.kit.kv("تعداد فاکتور", Money.fa(String.valueOf(d.sales.l("docs"))) + " فقره",
                Theme.TEXT), a.kit.lp(-1, -2));
        sales.addView(a.kit.kv("دریافتیِ ثبت‌شده روی فاکتورها", Money.compactRial(d.sales.d("paid")),
                Theme.SUCCESS), a.kit.lp(-1, -2));
        sales.addView(a.kit.kv("ماندهٔ اعلام‌شده روی فاکتورها", Money.compactRial(d.sales.d("remain")),
                Theme.WARNING), a.kit.lp(-1, -2));
        for (Row r : d.salesStatus) {
            sales.addView(a.kit.kv(r.s("label"), Money.fa(String.valueOf(r.l("docs"))) + " فاکتور • "
                    + Money.compactRial(r.d("total")), Theme.INFO), a.kit.lp(-1, -2));
        }
        content.addView(sales, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        LinearLayout money = a.kit.card(Theme.SUCCESS);
        money.addView(a.kit.sectionHead("دریافت و پرداختِ ثبت‌شده در روز", null, null), a.kit.lp(-1, -2));
        money.addView(a.kit.kv("قبض‌های دریافت", Money.fa(String.valueOf(d.receipts.l("count")))
                + " قبض • " + Money.compactRial(d.receipts.d("total")), Theme.SUCCESS), a.kit.lp(-1, -2));
        money.addView(a.kit.kv("ترکیب دریافت", "نقد " + Money.compactRial(d.receipts.d("cash"))
                + " • کارت " + Money.compactRial(d.receipts.d("pos"))
                + " • حواله " + Money.compactRial(d.receipts.d("havaleh"))
                + " • چک " + Money.compactRial(d.receipts.d("cheque")), Theme.TEXT), a.kit.lp(-1, -2));
        money.addView(a.kit.kv("قبض‌های پرداخت", Money.fa(String.valueOf(d.payments.l("count")))
                + " قبض • " + Money.compactRial(d.payments.d("total")), Theme.DANGER), a.kit.lp(-1, -2));
        money.addView(a.kit.kv("ترکیب پرداخت", "نقد " + Money.compactRial(d.payments.d("cash"))
                + " • کارت " + Money.compactRial(d.payments.d("pos"))
                + " • حواله " + Money.compactRial(d.payments.d("havaleh"))
                + " • چک " + Money.compactRial(d.payments.d("cheque")), Theme.TEXT), a.kit.lp(-1, -2));
        double difference = d.receipts.d("total") - d.sales.d("paid");
        money.addView(a.kit.hint("فاصلهٔ تشخیصی دریافت قبض‌ها با «دریافتیِ روی فاکتورهای همان روز»: "
                + Money.compactRial(difference)
                + " • این اختلاف الزاماً خطا نیست؛ قبض می‌تواند بابت بدهی قدیمی یا فروش روز دیگری باشد."),
                a.kit.lp(-1, -2));
        content.addView(money, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));

        addChecks("چک‌های دریافتی ثبت‌شده", d.incomingChecks, Theme.INFO);
        addChecks("چک‌های پرداختی ثبت‌شده", d.outgoingChecks, Theme.DANGER);
        addReturns(d.returns);
        addDeliveries(d);
        addCloseAction(d);
        content.addView(a.kit.gap(8));
        content.addView(a.kit.hint("بستن روز یک ثبت مدیریتی در جدول جانبی Meelano است؛ اعداد و اسناد آتیران تغییر نمی‌کنند. "
                + "مبالغ با واحد «" + Money.unitName() + "» نمایش داده شده‌اند."), a.kit.lp(-1, -2));
    }

    private void addChecks(String title, List<Row> rows, int accent) {
        LinearLayout card = a.kit.card(accent);
        long count = rows == null ? 0 : rows.size();
        double total = 0;
        if (rows != null) for (Row r : rows) total += r.d("amount");
        card.addView(a.kit.sectionHead(title, "چک‌ها ›", v -> a.nav("cheques")), a.kit.lp(-1, -2));
        card.addView(a.kit.kv("جمع", Money.fa(String.valueOf(count)) + " فقره • "
                + Money.compactRial(total), accent), a.kit.lp(-1, -2));
        if (rows == null || rows.isEmpty()) card.addView(a.kit.hint("موردی برای این روز ثبت نشده است"), a.kit.lp(-1, -2));
        else {
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                Row r = rows.get(i);
                card.addView(a.kit.kv("چک " + r.s("num") + " • " + r.s("customer"),
                        Money.compactRial(r.d("amount")) + (r.s("st").isEmpty() ? "" : " • وضعیت " + r.s("st")),
                        accent), a.kit.lp(-1, -2));
            }
            if (rows.size() > 5) card.addView(a.kit.hint("و "
                    + Money.fa(String.valueOf(rows.size() - 5)) + " مورد دیگر در فهرست چک‌ها"), a.kit.lp(-1, -2));
        }
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private void addReturns(List<Row> rows) {
        LinearLayout card = a.kit.card(Theme.WARNING);
        long n = 0;
        double sum = 0;
        if (rows != null) for (Row r : rows) { n += r.l("docs"); sum += r.d("total"); }
        card.addView(a.kit.sectionHead("اسناد برگشتی", "برگشتی‌ها ›", v -> a.nav("reports")), a.kit.lp(-1, -2));
        card.addView(a.kit.kv("تعداد و مبلغ", Money.fa(String.valueOf(n)) + " سند • "
                + Money.compactRial(sum), Theme.WARNING), a.kit.lp(-1, -2));
        if (rows == null || rows.isEmpty()) card.addView(a.kit.hint("برگشتی‌ای برای این روز پیدا نشد"), a.kit.lp(-1, -2));
        else for (Row r : rows)
            card.addView(a.kit.kv(r.s("label"), Money.fa(String.valueOf(r.l("docs")))
                    + " سند • " + Money.compactRial(r.d("total")), Theme.TEXT), a.kit.lp(-1, -2));
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private void addDeliveries(Data d) {
        Row r = d.deliveries == null ? new Row() : d.deliveries;
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.sectionHead("تحویل فاکتورهای روز", "تحویل‌ها ›", v -> a.nav("tahvil")), a.kit.lp(-1, -2));
        card.addView(a.kit.kv("کل فاکتورها", Money.fa(String.valueOf(r.l("totalDocs")))
                + " فقره • " + Money.compactRial(r.d("totalAmount")), Theme.TEXT), a.kit.lp(-1, -2));
        if (d.sharedHandover) {
            card.addView(a.kit.kv("تحویل‌شده", Money.fa(String.valueOf(r.l("deliveredDocs")))
                    + " • " + Money.compactRial(r.d("deliveredAmount")), Theme.SUCCESS), a.kit.lp(-1, -2));
            card.addView(a.kit.kv("باز / نیازمند پیگیری", Money.fa(String.valueOf(r.l("openDocs")))
                    + " • " + Money.compactRial(r.d("openAmount")), r.l("openDocs") > 0 ? Theme.WARNING : Theme.SUCCESS),
                    a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.hint("وضعیت تحویلِ دستگاه‌های دیگر در دسترس نیست؛ تعداد کل فاکتورها دقیق است اما تحویل‌های باز را نمی‌توان مرکزی تطبیق داد."),
                    a.kit.lp(-1, -2));
        }
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
    }

    private void addCloseAction(Data d) {
        LinearLayout card = a.kit.card(d.closed == null ? Theme.GOLD : Theme.SUCCESS);
        if (d.closed != null) {
            long ts = d.closed.optLong("closedAt", 0);
            card.addView(a.kit.text("روز ثبت شده است", 14f, Theme.SUCCESS, true), a.kit.lp(-1, -2));
            card.addView(a.kit.kv("ثبت‌کننده", d.closed.optString("username", "—"), Theme.TEXT), a.kit.lp(-1, -2));
            card.addView(a.kit.kv("زمان ثبت", ts > 0 ? "ساعت " + Money.fa(new java.text.SimpleDateFormat(
                    "HH:mm", java.util.Locale.US).format(new java.util.Date(ts))) : "—", Theme.TEXT),
                    a.kit.lp(-1, -2));
            String note = d.closed.optString("note", "");
            if (!note.isEmpty()) card.addView(a.kit.kv("یادداشت", note, Theme.MUTED), a.kit.lp(-1, -2));
            card.addView(a.kit.hint("اگر ارقام پس از ثبت تغییر کرده‌اند، ثبت دوباره یک snapshot به‌روز جایگزین می‌کند."),
                    a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.text("تطبیق را بررسی و روز را ثبت کنید", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
            card.addView(a.kit.hint("وجود اختلاف یا فاکتور تحویل‌نشده مانع ثبت نیست؛ موارد باز در snapshot باقی می‌مانند."),
                    a.kit.lp(-1, -2));
        }
        boolean canClose = d.closeTable;
        card.addView(a.kit.btn(canClose ? (d.closed == null ? "☑ تأیید و بستن روز" : "⟳ ثبت اصلاحیهٔ بستن روز")
                : "ثبت مرکزی در دسترس نیست", v -> { if (canClose) confirmClose(d); }), a.kit.lp(-1, -2));
        if (!canClose) card.addView(a.kit.hint("جدول جانبی قابل نوشتن نیست؛ پس بدون تأیید سرور، روز بسته اعلام نمی‌شود."),
                a.kit.lp(-1, -2));
        content.addView(card, a.kit.lp(-1, -2));
    }

    private void confirmClose(final Data d) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(16), Theme.dp(14));
        body.addView(a.kit.text("ثبت جمع‌بندی " + Money.fa(day), 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.hint("فروش " + Money.compactRial(d.sales.d("total"))
                + " • دریافت " + Money.compactRial(d.receipts.d("total"))
                + " • پرداخت " + Money.compactRial(d.payments.d("total"))), a.kit.lp(-1, -2));
        Row deliveries = d.deliveries == null ? new Row() : d.deliveries;
        if (d.sharedHandover && deliveries.l("openDocs") > 0)
            body.addView(a.kit.kv("هشدار تحویل", Money.fa(String.valueOf(deliveries.l("openDocs")))
                    + " فاکتور هنوز باز است.", Theme.WARNING), a.kit.lp(-1, -2));
        body.addView(a.kit.text("یادداشت بستن (اختیاری)", 12f, Theme.MUTED, true), a.kit.lp(-1, -2));
        final EditText note = a.kit.edit("یادداشت برای مدیر بعدی…", "");
        body.addView(note, a.kit.lp(-1, -2));
        final AlertDialog[] box = new AlertDialog[1];
        body.addView(a.kit.btnGold("ثبت بستن روز", v -> {
            final String closedDay = day;
            JSONObject snapshot = snapshot(d);
            String who = AtiranAuth.sessionName(a);
            if (who.isEmpty()) who = AtiranAuth.sessionUser(a);
            if (who.isEmpty()) who = "مدیر";
            final String username = who;
            final String memo = note.getText().toString().trim();
            box[0].dismiss();
            a.repo.run(c -> CloseoutDb.close(c, closedDay, username, memo, snapshot), new Repo.Cb<Boolean>() {
                @Override public void ok(Boolean saved) {
                    a.kit.toast(Boolean.TRUE.equals(saved) ? "بستن روز در سرور ثبت شد" : "ثبت سروری انجام نشد؛ روز بسته اعلام نشد");
                    load();
                }
                @Override public void fail(String faError) {
                    a.kit.toast("ثبت بستن روز انجام نشد: " + faError);
                }
            });
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("تأیید بستن روز", a.kit.scrollWrap(body, 420), true);
        box[0].show();
    }

    private JSONObject snapshot(Data d) {
        JSONObject o = new JSONObject();
        try {
            o.put("day", day);
            o.put("sales", d.sales.d("total"));
            o.put("salesDocs", d.sales.l("docs"));
            o.put("invoicePaid", d.sales.d("paid"));
            o.put("invoiceRemain", d.sales.d("remain"));
            o.put("receipts", d.receipts.d("total"));
            o.put("receiptCount", d.receipts.l("count"));
            o.put("payments", d.payments.d("total"));
            o.put("paymentCount", d.payments.l("count"));
            o.put("receivedCheques", sum(d.incomingChecks));
            o.put("receivedChequeCount", d.incomingChecks.size());
            o.put("paidCheques", sum(d.outgoingChecks));
            o.put("paidChequeCount", d.outgoingChecks.size());
            long returnCount = 0;
            double returnAmount = 0;
            for (Row r : d.returns) { returnCount += r.l("docs"); returnAmount += r.d("total"); }
            o.put("returns", returnAmount);
            o.put("returnCount", returnCount);
            Row del = d.deliveries == null ? new Row() : d.deliveries;
            o.put("deliveryTotal", del.l("totalDocs"));
            o.put("deliveryAmount", del.d("totalAmount"));
            o.put("deliveryStatusShared", d.sharedHandover);
            if (d.sharedHandover) {
                o.put("delivered", del.l("deliveredDocs"));
                o.put("openDeliveries", del.l("openDocs"));
                o.put("openDeliveryAmount", del.d("openAmount"));
            }
            o.put("moneyUnit", Money.unitName());
        } catch (Exception ignored) { }
        return o;
    }

    private static double sum(List<Row> rows) {
        double s = 0;
        if (rows != null) for (Row r : rows) s += r.d("amount");
        return s;
    }
}
