package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «دستیار میلانو»: a data-grounded manager assistant.
 *
 * It never sends shop data to a third party. Every answer is built from a live
 * snapshot of the store database (sales, purchases, receipts, payments, cheques,
 * debtors, overdue invoices, stock, visitors), using the same queries as the
 * home dashboard. Each part of the snapshot loads independently, so a missing
 * table only removes that part of the answer instead of failing the whole one.
 */
public final class Assistant {
    private Assistant() { }

    /** Snapshots older than this are reloaded when the assistant is used. */
    public static final long FRESH_MS = 60_000L;

    private interface Get<T> {
        T get() throws Exception;
    }

    /** Everything the assistant knows at one moment. */
    public static final class Snap {
        public String day = "";
        public long loadedAt = 0L;
        public Row salesToday = new Row();
        public Row salesYesterday = new Row();
        public Row buyToday = new Row();
        public Row inToday = new Row();
        public Row outToday = new Row();
        public Row prod = new Row();
        public List<Row> salesDaily = new ArrayList<>();
        public List<Row> debtors = new ArrayList<>();
        public List<Row> overdue = new ArrayList<>();
        public List<Row> dueIn = new ArrayList<>();
        public List<Row> dueOut = new ArrayList<>();
        public List<Row> visitors = new ArrayList<>();
        /** Warehouse documents waiting for sync (filled by the UI thread). */
        public int pendingSync = 0;
        /** Which parts failed to load, for an honest «اطلاعات کامل نیست» note. */
        public Map<String, String> notes = new LinkedHashMap<>();
    }

    // ================= loading =================

    /** Reads the whole store snapshot. Call from the database thread. */
    public static Snap load(Connection c) throws Exception {
        Meta m = new Meta(c);
        Snap s = new Snap();
        final String to = Jalali.todayStr();
        final String yest = Jalali.addDays(to, -1);
        s.day = to;
        s.loadedAt = System.currentTimeMillis();

        s.salesToday = soft(s.notes, "فروش امروز", new Row(), () -> Repo.one(c, Queries.salesOn(m, true, to)));
        s.salesYesterday = soft(s.notes, "فروش دیروز", new Row(), () -> Repo.one(c, Queries.salesOn(m, true, yest)));
        s.buyToday = soft(s.notes, "خرید امروز", new Row(), () -> Repo.one(c, Queries.homeBuyDay(m)));
        s.inToday = soft(s.notes, "دریافت امروز", new Row(), () -> Repo.one(c, MoneyQueries.darDay(m, 0)));
        s.outToday = soft(s.notes, "پرداخت امروز", new Row(), () -> Repo.one(c, MoneyQueries.darDay(m, 1)));
        s.prod = soft(s.notes, "کالاها", new Row(), () -> Repo.one(c, MasterQueries.productsSummary(m)));
        s.salesDaily = soft(s.notes, "روند فروش", new ArrayList<Row>(),
                () -> Repo.exec(c, Queries.factorDaily(m, true, Jalali.addDays(to, -30), to)));
        s.debtors = soft(s.notes, "بدهکاران", new ArrayList<Row>(), () -> Repo.exec(c, Queries.topDebtors(m, 5)));
        s.overdue = soft(s.notes, "معوق‌ها", new ArrayList<Row>(), () -> Repo.exec(c, Queries.overdueInvoices(m, 10)));
        s.dueIn = soft(s.notes, "چک دریافتی", new ArrayList<Row>(), () -> Repo.exec(c, MoneyQueries.chequeDue(m, true, 3)));
        s.dueOut = soft(s.notes, "چک پرداختی", new ArrayList<Row>(), () -> Repo.exec(c, MoneyQueries.chequeDue(m, false, 3)));
        s.visitors = soft(s.notes, "ویزیتورها", new ArrayList<Row>(), () -> {
            Filter vf = new Filter();
            vf.preset = Filter.P_LAST30;
            vf.applyPreset();
            return Repo.exec(c, MasterQueries.visitorsPerf(m, vf));
        });
        return s;
    }

    private static <T> T soft(Map<String, String> notes, String label, T def, Get<T> g) {
        try {
            T v = g.get();
            return v == null ? def : v;
        } catch (Exception e) {
            notes.put(label, e.getMessage() == null ? "خطا" : e.getMessage());
            Diag.ignored("Assistant:" + label, e);
            return def;
        }
    }

    // ================= reasoning =================

    /** Answers one Persian question from the snapshot. Pure: no I/O, no Android. */
    public static String answer(Snap s, String question) {
        String q = norm(question);
        if (q.isEmpty()) return help();
        StringBuilder b = new StringBuilder();
        if (has(q, "راهنما", "کمک", "چیکار", "چه کار", "چی بپرس", "چه سوالی", "راهنمایی")) {
            return help();
        }
        if (has(q, "پیشنهاد", "چه کنم", "اقدام", "مدیر")) {
            return joinSuggestions(suggestions(s), "پیشنهاد اقدام امروز");
        }
        if (has(q, "خلاصه", "گزارش امروز", "وضعیت امروز", "امروز چطور", "روزانه")) {
            return dailySummary(s);
        }
        if (has(q, "معوق", "سررسید", "سر رسید", "فاکتور عقب")) {
            b.append(overdueText(s));
        } else if (has(q, "بدهکار", "مطالبه", "طلب", "وصول")) {
            b.append(debtorText(s));
        } else if (has(q, "چک")) {
            b.append(chequeText(s));
        } else if (has(q, "موجودی", "کالا", "انبار", "کم‌موجود", "کم موجود", "ناموجود")) {
            b.append(stockText(s));
        } else if (has(q, "ویزیتور", "ویزیت", "بازاریاب")) {
            b.append(visitorText(s));
        } else if (has(q, "خرید")) {
            b.append("خرید امروز: ").append(money(s.buyToday.d("total")));
        } else if (has(q, "پرداخت")) {
            b.append("پرداخت امروز: ").append(money(s.outToday.d("total")))
                    .append("\nچک پرداختی تا ۳ روز آینده: ").append(count(s.dueOut.size()))
                    .append(" مورد (").append(money(sumAmount(s.dueOut))).append(")");
        } else if (has(q, "دریافت", "وصولی", "نقد")) {
            b.append("دریافت امروز: ").append(money(s.inToday.d("total")))
                    .append("\n• نقد: ").append(money(s.inToday.d("cash")))
                    .append("\n• چک: ").append(money(s.inToday.d("cheque")))
                    .append("\n• پوز: ").append(money(s.inToday.d("pos")))
                    .append("\n• حواله: ").append(money(s.inToday.d("havaleh")));
        } else if (has(q, "فروش", "فاکتور")) {
            b.append(salesText(s));
        } else if (has(q, "سود", "حاشیه")) {
            b.append("گزارش سود در این دستیار فعلاً نیست. از منوی «بیشتر» ← «سود، هزینه و حاشیه سود» استفاده کنید.");
        } else {
            return "متوجه نشدم منظورتان چیست.\n\n" + help();
        }
        if (!s.notes.isEmpty()) {
            b.append("\n\n⚠ بعضی اطلاعات در دسترس نبود: ").append(join(s.notes.keySet()));
        }
        return b.toString();
    }

    /** Short proactive list, most urgent first. */
    public static List<String> suggestions(Snap s) {
        List<String> out = new ArrayList<>();
        if (!s.overdue.isEmpty()) {
            out.add("امروز با " + count(s.overdue.size()) + " فاکتور معوق (" + money(sumAmount(s.overdue))
                    + ") تماس بگیرید؛ بیشترین مبلغ مربوط به «" + s.overdue.get(0).s("party") + "» است.");
        }
        if (!s.debtors.isEmpty()) {
            Row t = s.debtors.get(0);
            out.add("بیشترین بدهکار «" + t.s("party") + "» با مانده " + money(t.d("amount"))
                    + " است؛ برای وصول، پیگیری یا قرار پرداخت اقدام کنید.");
        }
        if (!s.dueIn.isEmpty()) {
            out.add("تا ۳ روز آینده " + count(s.dueIn.size()) + " چک دریافتی سررسید دارد (" + money(sumAmount(s.dueIn))
                    + "). پیش از سررسید حساب بانکی را بررسی کنید.");
        }
        if (!s.dueOut.isEmpty()) {
            out.add("تا ۳ روز آینده " + count(s.dueOut.size()) + " چک پرداختی دارید (" + money(sumAmount(s.dueOut))
                    + "). موجودی کافی را تأیید کنید.");
        }
        long out0 = s.prod.l("out"), low = s.prod.l("low");
        if (out0 > 0 || low > 0) {
            out.add("کالاهای ناموجود: " + count(out0) + "، کم‌موجود: " + count(low)
                    + ". برای کالاهای پرفروش سفارش خرید ثبت کنید.");
        }
        if (s.pendingSync > 0) {
            out.add(count(s.pendingSync) + " سند انبار هنوز همگام‌سازی نشده است؛ اتصال به سرور را بررسی کنید.");
        }
        double today = s.salesToday.d("total");
        double avg = averageLastWeek(s);
        if (avg > 0 && today < avg * 0.5 && java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 14) {
            out.add("فروش امروز کمتر از نصف میانگین ۷ روز گذشته است (" + money(avg)
                    + "). کمپین تخفیف یا پیگیری مشتریان غیرفعال را در نظر بگیرید.");
        }
        if (out.isEmpty()) {
            out.add("امروز هشدار جدی‌ای نداریم. وقت خوبی برای بررسی ویزیتورها و مشتریان غیرفعال است.");
        }
        return out;
    }

    /** Full daily summary text (also used for the notification). */
    public static String dailySummary(Snap s) {
        StringBuilder b = new StringBuilder();
        b.append("📊 خلاصه امروز (").append(s.day).append(")\n");
        b.append("• فروش: ").append(money(s.salesToday.d("total")))
                .append(" (").append(count(s.salesToday.l("docs"))).append(" فاکتور)\n");
        b.append("• دیروز: ").append(money(s.salesYesterday.d("total"))).append("\n");
        b.append("• دریافت: ").append(money(s.inToday.d("total")))
                .append(" • پرداخت: ").append(money(s.outToday.d("total"))).append("\n");
        b.append("• خرید: ").append(money(s.buyToday.d("total"))).append("\n");
        b.append("• معوق‌ها: ").append(count(s.overdue.size()))
                .append(" فاکتور (").append(money(sumAmount(s.overdue))).append(")");
        b.append("\n\n✦ پیشنهاد مدیر:\n");
        int i = 1;
        for (String p : suggestions(s)) {
            if (i > 3) break;
            b.append(i++).append(". ").append(p).append("\n");
        }
        if (!s.notes.isEmpty()) {
            b.append("\n⚠ بعضی بخش‌ها در دسترس نبود: ").append(join(s.notes.keySet()));
        }
        return b.toString().trim();
    }

    /** Human-readable help listing what can be asked. */
    public static String help() {
        return "می‌توانید بپرسید:\n"
                + "• فروش امروز / فروش دیروز\n"
                + "• خرید، دریافت، پرداخت\n"
                + "• بدهکاران، معوق‌ها، چک‌های نزدیک سررسید\n"
                + "• موجودی، کالاهای ناموجود و کم‌موجود\n"
                + "• ویزیتورها\n"
                + "• خلاصه امروز، پیشنهاد اقدام\n"
                + "پاسخ‌ها از اطلاعات زندهٔ همین فروشگاه ساخته می‌شوند.";
    }

    // ================= helpers =================

    private static String salesText(Snap s) {
        double today = s.salesToday.d("total");
        double yest = s.salesYesterday.d("total");
        double avg = averageLastWeek(s);
        StringBuilder b = new StringBuilder();
        b.append("فروش امروز: ").append(money(today)).append(" (")
                .append(count(s.salesToday.l("docs"))).append(" فاکتور)");
        b.append("\nدیروز: ").append(money(yest));
        if (avg > 0) {
            long pct = Math.round((today - avg) / avg * 100.0);
            b.append("\nمیانگین ۷ روز گذشته: ").append(money(avg))
                    .append(" • نسبت به میانگین: ").append(pct >= 0 ? "+" : "")
                    .append(Money.fa(String.valueOf(pct))).append("٪");
        }
        b.append("\nاگر امروز هنوز ساعات کمی گذشته، عدد کامل نیست.");
        return b.toString();
    }

    private static String overdueText(Snap s) {
        if (s.overdue.isEmpty()) return "فاکتور معوقی ثبت نشده است.";
        StringBuilder b = new StringBuilder("فاکتورهای معوق (")
                .append(count(s.overdue.size())).append(" مورد، ").append(money(sumAmount(s.overdue))).append("):");
        for (int i = 0; i < s.overdue.size() && i < 5; i++) {
            Row r = s.overdue.get(i);
            b.append("\n• ").append(r.s("party")).append(" — ").append(money(r.d("amount")))
                    .append(" (").append(Money.fa(String.valueOf(r.l("days")))).append(" روز تأخیر)");
        }
        return b.toString();
    }

    private static String debtorText(Snap s) {
        if (s.debtors.isEmpty()) return "بدهکار با مانده قابل توجهی ثبت نشده است.";
        StringBuilder b = new StringBuilder("بدهکاران اولویت‌دار:");
        for (Row r : s.debtors) {
            b.append("\n• ").append(r.s("party")).append(" — ").append(money(r.d("amount")));
        }
        return b.toString();
    }

    private static String chequeText(Snap s) {
        return "چک دریافتی تا ۳ روز آینده: " + count(s.dueIn.size()) + " مورد (" + money(sumAmount(s.dueIn)) + ")\n"
                + "چک پرداختی تا ۳ روز آینده: " + count(s.dueOut.size()) + " مورد (" + money(sumAmount(s.dueOut)) + ")";
    }

    private static String stockText(Snap s) {
        return "کل کالاها: " + count(s.prod.l("count")) + " • فعال: " + count(s.prod.l("active")) + "\n"
                + "ناموجود: " + count(s.prod.l("out")) + " • کم‌موجود: " + count(s.prod.l("low"))
                + " • نیازمند سفارش: " + count(s.prod.l("reorderN")) + "\n"
                + "ارزش موجودی: " + money(s.prod.d("value"));
    }

    private static String visitorText(Snap s) {
        List<Row> rows = new ArrayList<>();
        for (Row r : s.visitors) {
            if (!r.s("name").isEmpty()) rows.add(r);
        }
        if (rows.isEmpty()) return "اطلاعات ویزیتورها در ۳۰ روز گذشته ثبت نشده است.";
        StringBuilder b = new StringBuilder("عملکرد ویزیتورها (۳۰ روز گذشته):");
        for (int i = 0; i < rows.size() && i < 5; i++) {
            Row r = rows.get(i);
            b.append("\n• ").append(r.s("name")).append(" — ").append(money(r.d("sales")));
        }
        return b.toString();
    }

    /** Average daily sales of the 7 days before today, from the 30-day series. */
    static double averageLastWeek(Snap s) {
        List<Row> rows = s.salesDaily;
        if (rows == null || rows.isEmpty()) return 0;
        List<Row> past = new ArrayList<>();
        for (Row r : rows) {
            String d = r.s("day");
            if (d.isEmpty()) continue;
            if (d.equals(s.day)) continue;
            past.add(r);
        }
        int from = Math.max(0, past.size() - 7);
        double sum = 0;
        int n = 0;
        for (int i = from; i < past.size(); i++) {
            sum += past.get(i).d("total");
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private static double sumAmount(List<Row> rows) {
        double t = 0;
        for (Row r : rows) t += r.d("amount");
        return t;
    }

    private static String joinSuggestions(List<String> items, String title) {
        StringBuilder b = new StringBuilder(title).append(":");
        int i = 1;
        for (String p : items) b.append("\n").append(i++).append(". ").append(p);
        return b.toString();
    }

    private static String money(double v) {
        return Money.compactRial(v);
    }

    private static String count(long n) {
        return Money.fa(String.valueOf(n));
    }

    private static String join(Iterable<String> xs) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (b.length() > 0) b.append("، ");
            b.append(x);
        }
        return b.toString();
    }

    /** Normalizes Persian text for keyword matching (Arabic letters, ZWNJ, case). */
    static String norm(String t) {
        if (t == null) return "";
        return t.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
                .replace('ۀ', 'ه').replace('ة', 'ه')
                .trim().toLowerCase(Locale.ROOT);
    }

    private static boolean has(String q, String... words) {
        for (String w : words) {
            if (q.contains(norm(w))) return true;
        }
        return false;
    }
}
