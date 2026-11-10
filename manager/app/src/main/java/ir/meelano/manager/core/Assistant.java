package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «دستیار میلانو»: a data-grounded manager assistant.
 *
 * Rule-based answers are built on-device from a predefined, read-only live
 * snapshot of the store database (sales, purchases, receipts, payments, cheques,
 * debtors, overdue invoices, stock, visitors). When online AI is explicitly enabled,
 * AssistantScreen sends only the selected tool/report facts needed for that turn,
 * not this entire snapshot. Each part loads independently, and failed sections are
 * recorded rather than treated as confirmed zero values.
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
        markInvalidAmounts(s.notes, "فروش امروز", s.salesToday);
        markInvalidAmounts(s.notes, "فروش دیروز", s.salesYesterday);
        markInvalidAmounts(s.notes, "خرید امروز", s.buyToday);
        if (s.salesDaily != null) for (Row row : s.salesDaily) {
            Long invalidRows = row == null ? null : row.exactLong("invalidRows");
            if (row == null || invalidRows == null || invalidRows > 0) {
                s.notes.put("روند فروش", "در یک یا چند ردیف مقدار فروش ناقص یا نامعتبر است");
                break;
            }
        }
        return s;
    }

    private static void markInvalidAmounts(Map<String, String> notes, String label, Row row) {
        Long invalidRows = row == null ? null : row.exactLong("invalidRows");
        if (invalidRows == null || invalidRows > 0) {
            notes.put(label, "مبلغ در یک یا چند ردیف ناقص یا قابل‌خواندن نیست");
        }
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
        if (s == null) return "دادهٔ کسب‌وکار اکنون در دسترس نیست؛ عددی را صفر فرض نمی‌کنم. اتصال را بررسی و دوباره تلاش کنید.";
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
            if (unavailable(s, "خرید امروز") || !hasMoney(s.buyToday, "total")) b.append(unavailableText("خرید امروز"));
            else b.append("خرید امروز: ").append(moneyField(s.buyToday, "total"));
        } else if (has(q, "پرداخت")) {
            if (unavailable(s, "پرداخت امروز") || !hasMoney(s.outToday, "total")) b.append(unavailableText("پرداخت امروز"));
            else b.append("پرداخت امروز: ").append(moneyField(s.outToday, "total"));
            if (unavailable(s, "چک پرداختی")) b.append("\nچک پرداختی تا ۳ روز آینده: داده در دسترس نیست");
            else b.append("\nچک پرداختی تا ۳ روز آینده: ").append(count(s.dueOut.size()))
                    .append(" مورد (").append(sumAmountOrUnavailable(s.dueOut)).append(")");
        } else if (has(q, "دریافت", "وصولی", "نقد")) {
            if (unavailable(s, "دریافت امروز") || !hasMoney(s.inToday, "total")) b.append(unavailableText("دریافت امروز"));
            else b.append("دریافت امروز: ").append(moneyField(s.inToday, "total"))
                    .append("\n• نقد: ").append(valueOrUnavailable(s.inToday, "cash"))
                    .append("\n• چک: ").append(valueOrUnavailable(s.inToday, "cheque"))
                    .append("\n• پوز: ").append(valueOrUnavailable(s.inToday, "pos"))
                    .append("\n• حواله: ").append(valueOrUnavailable(s.inToday, "havaleh"));
        } else if (has(q, "فروش", "فاکتور")) {
            b.append(salesText(s));
        } else if (has(q, "سود", "حاشیه")) {
            b.append("گزارش سود در این دستیار فعلاً نیست. از منوی «بیشتر» ← «سود، هزینه و حاشیه سود» استفاده کنید.");
        } else {
            return "متوجه نشدم منظورتان چیست.\n\n" + help();
        }
        if (s.notes != null && !s.notes.isEmpty()) {
            b.append("\n\n⚠ بعضی اطلاعات در دسترس نبود: ").append(join(s.notes.keySet()));
        } else if (hasMissingFields(s)) {
            b.append("\n\n⚠ بعضی فیلدهای منبع موجود نیست؛ آن‌ها صفر یا نتیجهٔ قطعی فرض نشده‌اند.");
        }
        return b.toString();
    }

    /** True when a section failed to load; callers must not interpret its default row as zero. */
    public static boolean unavailable(Snap s, String label) {
        return s == null || s.notes != null && s.notes.containsKey(label);
    }

    private static String unavailableText(String label) {
        return label + ": داده در دسترس نیست؛ مقدار صفر فرض نمی‌شود.";
    }

    static boolean hasMoney(Row row, String key) {
        BigDecimal amount = row == null ? null : row.bd(key);
        return amount != null && Double.isFinite(amount.doubleValue());
    }

    static boolean hasCount(Row row, String key) {
        return row != null && row.exactLong(key) != null;
    }

    static String moneyField(Row row, String key) {
        BigDecimal amount = row == null ? null : row.bd(key);
        if (amount == null || !Double.isFinite(amount.doubleValue())) return "داده در دسترس نیست";
        return money(amount.doubleValue());
    }

    static String countField(Row row, String key) {
        Long value = row == null ? null : row.exactLong(key);
        return value == null ? "داده در دسترس نیست" : count(value);
    }

    private static String valueOrUnavailable(Row row, String key) {
        return moneyField(row, key);
    }

    private static String countOrUnavailable(Row row, String key) {
        return countField(row, key);
    }

    private static String moneyOrUnavailable(Row row, String key) {
        return moneyField(row, key);
    }

    /** Short proactive list, most urgent first; failed sections are never treated as empty. */
    public static List<String> suggestions(Snap s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            out.add("دادهٔ کسب‌وکار در دسترس نیست؛ پیشنهاد عددی ارائه نمی‌شود.");
            return out;
        }
        if (!unavailable(s, "معوق‌ها") && !s.overdue.isEmpty()) {
            String party = s.overdue.get(0).s("party");
            if (party.isEmpty()) party = "نام در گزارش موجود نیست";
            out.add("امروز با " + count(s.overdue.size()) + " فاکتور معوق (" + sumAmountOrUnavailable(s.overdue)
                    + ") پیگیری کنید؛ اولویت نخست: «" + party + "».");
        }
        if (!unavailable(s, "بدهکاران") && !s.debtors.isEmpty()) {
            Row t = s.debtors.get(0);
            String party = t.s("party");
            if (party.isEmpty()) party = "نام در گزارش موجود نیست";
            out.add("بیشترین بدهکارِ گزارش «" + party + "» با مانده " + moneyOrUnavailable(t, "amount")
                    + " است؛ برای وصول، پیگیری یا قرار پرداخت اقدام کنید.");
        }
        if (!unavailable(s, "چک دریافتی") && !s.dueIn.isEmpty()) {
            out.add("تا ۳ روز آینده " + count(s.dueIn.size()) + " چک دریافتی سررسید دارد (" + sumAmountOrUnavailable(s.dueIn)
                    + "). پیش از سررسید حساب بانکی را بررسی کنید.");
        }
        if (!unavailable(s, "چک پرداختی") && !s.dueOut.isEmpty()) {
            out.add("تا ۳ روز آینده " + count(s.dueOut.size()) + " چک پرداختی دارید (" + sumAmountOrUnavailable(s.dueOut)
                    + "). موجودی کافی را تأیید کنید.");
        }
        if (!unavailable(s, "کالاها") && hasCount(s.prod, "out") && hasCount(s.prod, "low")) {
            long out0 = s.prod.exactLong("out"), low = s.prod.exactLong("low");
            if (out0 > 0 || low > 0) {
                out.add("کالاهای ناموجود: " + count(out0) + "، کم‌موجود: " + count(low)
                        + ". برای کالاهای پرفروش سفارش خرید ثبت کنید.");
            }
        }
        if (s.pendingSync > 0) {
            out.add(count(s.pendingSync) + " سند انبار هنوز همگام‌سازی نشده است؛ اتصال به سرور را بررسی کنید.");
        }
        if (!unavailable(s, "فروش امروز") && hasMoney(s.salesToday, "total") && !unavailable(s, "روند فروش")) {
            double today = s.salesToday.bd("total").doubleValue();
            double avg = averageLastWeek(s);
            if (avg > 0 && today < avg * 0.5 && java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 14) {
                out.add("فروش امروز کمتر از نصف میانگین ۷ روز گذشته است (" + money(avg)
                        + "). کمپین تخفیف یا پیگیری مشتریان غیرفعال را در نظر بگیرید.");
            }
        }
        if (out.isEmpty()) {
            if (s.notes != null && !s.notes.isEmpty()) {
                out.add("بعضی داده‌ها بارگذاری نشد؛ بنابراین نبودِ هشدار را نمی‌توان قطعی دانست: " + join(s.notes.keySet()));
            } else if (hasMissingFields(s)) {
                out.add("فیلدهای لازم در بعضی ردیف‌ها موجود نیست؛ نبودِ هشدار را نمی‌توان قطعی دانست.");
            } else {
                out.add("بر اساس داده‌های در دسترس هشدار مشخصی پیدا نشد؛ برای اطمینان گزارش‌های تکمیلی را بررسی کنید.");
            }
        }
        return out;
    }

    private static boolean hasMissingFields(Snap s) {
        if (!unavailable(s, "فروش امروز") && !hasMoney(s.salesToday, "total")) return true;
        if (!unavailable(s, "فروش دیروز") && !hasMoney(s.salesYesterday, "total")) return true;
        if (!unavailable(s, "خرید امروز") && !hasMoney(s.buyToday, "total")) return true;
        if (!unavailable(s, "دریافت امروز") && !hasMoney(s.inToday, "total")) return true;
        if (!unavailable(s, "پرداخت امروز") && !hasMoney(s.outToday, "total")) return true;
        if (!unavailable(s, "کالاها") && (!hasCount(s.prod, "out") || !hasCount(s.prod, "low"))) return true;
        if (!unavailable(s, "روند فروش")) {
            if (s.salesDaily == null) return true;
            for (Row row : s.salesDaily) {
                Long invalidRows = row == null ? null : row.exactLong("invalidRows");
                BigDecimal total = row == null ? null : row.bd("total");
                if (row == null || !row.has("day") || total == null || !Double.isFinite(total.doubleValue())
                        || Jalali.parse(row.s("day")) < 0 || invalidRows == null || invalidRows > 0) return true;
            }
        }
        return false;
    }

    /** Full daily summary text (also used for the notification), with missing sections explicit. */
    public static String dailySummary(Snap s) {
        if (s == null) return "دادهٔ فروشگاه در دسترس نیست؛ خلاصهٔ عددی ساخته نشد.";
        StringBuilder b = new StringBuilder();
        b.append("📊 خلاصه امروز (").append(s.day == null || s.day.isEmpty() ? "تاریخ نامشخص" : s.day).append(")\n");
        if (unavailable(s, "فروش امروز") || !hasMoney(s.salesToday, "total")) b.append("• فروش امروز: داده در دسترس نیست\n");
        else {
            b.append("• فروش: ").append(moneyField(s.salesToday, "total"));
            if (hasCount(s.salesToday, "docs")) b.append(" (").append(countField(s.salesToday, "docs")).append(" فاکتور)");
            else b.append(" (تعداد فاکتور در منبع موجود نیست)");
            b.append("\n");
        }
        if (unavailable(s, "فروش دیروز") || !hasMoney(s.salesYesterday, "total")) b.append("• فروش دیروز: داده در دسترس نیست\n");
        else b.append("• دیروز: ").append(moneyField(s.salesYesterday, "total")).append("\n");
        if (unavailable(s, "دریافت امروز") || !hasMoney(s.inToday, "total")) b.append("• دریافت: داده در دسترس نیست");
        else b.append("• دریافت: ").append(moneyField(s.inToday, "total"));
        if (unavailable(s, "پرداخت امروز") || !hasMoney(s.outToday, "total")) b.append(" • پرداخت: داده در دسترس نیست\n");
        else b.append(" • پرداخت: ").append(moneyField(s.outToday, "total")).append("\n");
        if (unavailable(s, "خرید امروز") || !hasMoney(s.buyToday, "total")) b.append("• خرید: داده در دسترس نیست\n");
        else b.append("• خرید: ").append(moneyField(s.buyToday, "total")).append("\n");
        if (unavailable(s, "معوق‌ها")) b.append("• معوق‌ها: داده در دسترس نیست");
        else b.append("• معوق‌ها: ").append(count(s.overdue.size()))
                .append(" فاکتور (").append(sumAmountOrUnavailable(s.overdue)).append(")");
        b.append("\n\n✦ پیشنهاد مدیر:\n");
        int i = 1;
        for (String p : suggestions(s)) {
            if (i > 3) break;
            b.append(i++).append(". ").append(p).append("\n");
        }
        if (s.notes != null && !s.notes.isEmpty()) {
            b.append("\n⚠ بعضی بخش‌ها در دسترس نبود: ").append(join(s.notes.keySet()));
        } else if (hasMissingFields(s)) {
            b.append("\n⚠ بعضی فیلدهای منبع موجود نیست؛ مقدار آن‌ها صفر فرض نمی‌شود.");
        }
        return b.toString().trim();
    }

    /** Plain-text facts for a generative model; unavailable fields are never printed as zero. */
    public static String contextBlock(Snap s) {
        if (s == null) return "دادهٔ فروشگاه در دسترس نیست.";
        StringBuilder b = new StringBuilder();
        b.append("تاریخ امروز (شمسی): ").append(s.day == null || s.day.isEmpty() ? "نامشخص" : s.day).append("\n");
        if (s.loadedAt > 0) b.append("زمان خواندن داده: ").append(Money.fa(String.valueOf(s.loadedAt))).append("\n");
        appendField(b, s, "فروش امروز", "فروش امروز", s.salesToday, "total", true);
        appendField(b, s, "فروش دیروز", "فروش دیروز", s.salesYesterday, "total", true);
        if (unavailable(s, "روند فروش")) b.append("روند فروش: داده در دسترس نیست\n");
        else {
            double avg = averageLastWeek(s);
            if (avg > 0) b.append("میانگین فروش روزانه ۷ روز گذشته: ").append(money(avg)).append("\n");
            else b.append("روند فروش: ردیف کافی برای میانگین در دسترس نیست\n");
        }
        appendField(b, s, "خرید امروز", "خرید امروز", s.buyToday, "total", true);
        appendField(b, s, "دریافت امروز", "دریافت امروز", s.inToday, "total", true);
        appendField(b, s, "پرداخت امروز", "پرداخت امروز", s.outToday, "total", true);
        appendList(b, s, "معوق‌ها", "فاکتورهای معوق", s.overdue, "party", "amount");
        appendList(b, s, "بدهکاران", "بدهکاران اولویت‌دار", s.debtors, "party", "amount");
        appendList(b, s, "چک دریافتی", "چک دریافتی تا سه روز آینده", s.dueIn, "party", "amount");
        appendList(b, s, "چک پرداختی", "چک پرداختی تا سه روز آینده", s.dueOut, "party", "amount");
        if (unavailable(s, "کالاها")) b.append("کالاها: داده در دسترس نیست\n");
        else {
            b.append("کالاها: کل ").append(countOrUnavailable(s.prod, "count"))
                    .append("، فعال ").append(countOrUnavailable(s.prod, "active"))
                    .append("، ناموجود ").append(countOrUnavailable(s.prod, "out"))
                    .append("، کم‌موجود ").append(countOrUnavailable(s.prod, "low"))
                    .append("، ارزش موجودی ").append(moneyOrUnavailable(s.prod, "value")).append("\n");
        }
        b.append("سندهای انبار منتظر همگام‌سازی (دستگاه): ").append(count(s.pendingSync)).append("\n");
        if (unavailable(s, "ویزیتورها")) b.append("عملکرد ویزیتورها: داده در دسترس نیست\n");
        else {
            b.append("عملکرد ویزیتورها (۳۰ روز):\n");
            for (Row r : s.visitors) {
                if (r.s("name").isEmpty()) continue;
                b.append("  - ").append(r.s("name")).append(": ").append(moneyOrUnavailable(r, "sales")).append("\n");
            }
        }
        if (s.notes != null && !s.notes.isEmpty()) b.append("بخش‌های بارگذاری‌نشده: ").append(join(s.notes.keySet())).append("\n");
        else if (hasMissingFields(s)) b.append("بعضی فیلدهای منبع ناموجود یا نامعتبرند و صفر فرض نشده‌اند.\n");
        return b.toString();
    }

    private static void appendField(StringBuilder out, Snap snap, String section, String label,
                                    Row row, String key, boolean isMoney) {
        if (unavailable(snap, section)) {
            out.append(label).append(": داده در دسترس نیست\n");
            return;
        }
        out.append(label).append(": ").append(isMoney ? moneyField(row, key) : countField(row, key)).append("\n");
    }

    private static void appendList(StringBuilder out, Snap snap, String section, String label,
                                   List<Row> rows, String nameKey, String amountKey) {
        if (unavailable(snap, section)) {
            out.append(label).append(": داده در دسترس نیست\n");
            return;
        }
        out.append(label).append(": ").append(count(rows.size())).append(" مورد\n");
        for (Row row : rows) {
            String name = row.s(nameKey);
            String amount = moneyOrUnavailable(row, amountKey);
            out.append("  - ").append(name.isEmpty() ? "نام در منبع موجود نیست" : name)
                    .append(": ").append(amount).append("\n");
        }
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
        StringBuilder b = new StringBuilder();
        boolean todayAvailable = !unavailable(s, "فروش امروز") && hasMoney(s.salesToday, "total");
        if (!todayAvailable) {
            b.append(unavailableText("فروش امروز"));
        } else {
            b.append("فروش امروز: ").append(moneyField(s.salesToday, "total"));
            if (hasCount(s.salesToday, "docs")) b.append(" (").append(countField(s.salesToday, "docs")).append(" فاکتور)");
            else b.append(" (تعداد فاکتور در منبع موجود نیست)");
        }
        if (unavailable(s, "فروش دیروز") || !hasMoney(s.salesYesterday, "total")) {
            b.append("\nفروش دیروز: داده در دسترس نیست");
        } else {
            b.append("\nدیروز: ").append(moneyField(s.salesYesterday, "total"));
        }
        if (unavailable(s, "روند فروش")) {
            b.append("\nمیانگین ۷ روز گذشته: داده در دسترس نیست");
        } else {
            double avg = averageLastWeek(s);
            if (avg > 0) {
                b.append("\nمیانگین ۷ روز گذشته: ").append(money(avg));
                if (todayAvailable) {
                    long pct = Math.round((s.salesToday.bd("total").doubleValue() - avg) / avg * 100.0);
                    b.append(" • نسبت به میانگین: ").append(pct >= 0 ? "+" : "")
                            .append(Money.fa(String.valueOf(pct))).append("٪");
                }
            } else {
                b.append("\nبرای محاسبهٔ میانگین ۷ روزه ردیف کافی در منبع نیست.");
            }
        }
        if (todayAvailable) b.append("\nاگر امروز هنوز ساعات کمی گذشته، عدد کامل نیست.");
        return b.toString();
    }

    private static String overdueText(Snap s) {
        if (unavailable(s, "معوق‌ها")) return unavailableText("معوق‌ها");
        if (s.overdue.isEmpty()) return "در گزارش معوق‌ها ردیفی برنگشت.";
        StringBuilder b = new StringBuilder("فاکتورهای معوق (")
                .append(count(s.overdue.size())).append(" مورد، ").append(sumAmountOrUnavailable(s.overdue)).append("):");
        for (int i = 0; i < s.overdue.size() && i < 5; i++) {
            Row r = s.overdue.get(i);
            String party = r.s("party");
            if (party.isEmpty()) party = "نام در منبع موجود نیست";
            b.append("\n• ").append(party).append(" — ").append(moneyOrUnavailable(r, "amount"));
            if (hasCount(r, "days")) b.append(" (").append(countField(r, "days")).append(" روز تأخیر)");
            else b.append(" (میزان تأخیر در منبع موجود نیست)");
        }
        return b.toString();
    }

    private static String debtorText(Snap s) {
        if (unavailable(s, "بدهکاران")) return unavailableText("بدهکاران");
        if (s.debtors.isEmpty()) return "در گزارش بدهکاران ردیفی برنگشت.";
        StringBuilder b = new StringBuilder("بدهکاران اولویت‌دار:");
        for (Row r : s.debtors) {
            String party = r.s("party");
            if (party.isEmpty()) party = "نام در منبع موجود نیست";
            b.append("\n• ").append(party).append(" — ").append(moneyOrUnavailable(r, "amount"));
        }
        return b.toString();
    }

    private static String chequeText(Snap s) {
        StringBuilder b = new StringBuilder();
        if (unavailable(s, "چک دریافتی")) b.append("چک دریافتی تا ۳ روز آینده: داده در دسترس نیست");
        else b.append("چک دریافتی تا ۳ روز آینده: ").append(count(s.dueIn.size()))
                .append(" مورد (").append(sumAmountOrUnavailable(s.dueIn)).append(")");
        if (unavailable(s, "چک پرداختی")) b.append("\nچک پرداختی تا ۳ روز آینده: داده در دسترس نیست");
        else b.append("\nچک پرداختی تا ۳ روز آینده: ").append(count(s.dueOut.size()))
                .append(" مورد (").append(sumAmountOrUnavailable(s.dueOut)).append(")");
        return b.toString();
    }

    private static String stockText(Snap s) {
        if (unavailable(s, "کالاها")) return unavailableText("کالاها");
        return "کل کالاها: " + countOrUnavailable(s.prod, "count") + " • فعال: " + countOrUnavailable(s.prod, "active") + "\n"
                + "ناموجود: " + countOrUnavailable(s.prod, "out") + " • کم‌موجود: " + countOrUnavailable(s.prod, "low")
                + " • نیازمند سفارش: " + countOrUnavailable(s.prod, "reorderN") + "\n"
                + "ارزش موجودی: " + moneyOrUnavailable(s.prod, "value");
    }

    private static String visitorText(Snap s) {
        if (unavailable(s, "ویزیتورها")) return unavailableText("ویزیتورها");
        List<Row> rows = new ArrayList<>();
        for (Row r : s.visitors) {
            if (!r.s("name").isEmpty()) rows.add(r);
        }
        if (rows.isEmpty()) return s.visitors.isEmpty()
                ? "در گزارش عملکرد ویزیتورها در ۳۰ روز گذشته ردیفی برنگشت."
                : "ردیف‌های ویزیتور برگشت، اما نام قابل‌نمایشی در منبع نبود.";
        StringBuilder b = new StringBuilder("عملکرد ویزیتورها (۳۰ روز گذشته):");
        for (int i = 0; i < rows.size() && i < 5; i++) {
            Row r = rows.get(i);
            b.append("\n• ").append(r.s("name")).append(" — ").append(moneyOrUnavailable(r, "sales"));
        }
        return b.toString();
    }

    /** Average over the seven prior calendar days; missing dates are zero only after a successful query. */
    static double averageLastWeek(Snap s) {
        if (s == null || unavailable(s, "روند فروش") || s.salesDaily == null || s.day == null
                || Jalali.parse(s.day) < 0) return 0;
        for (Row row : s.salesDaily) {
            Long invalidRows = row == null ? null : row.exactLong("invalidRows");
            BigDecimal total = row == null ? null : row.bd("total");
            if (row == null || !row.has("day") || total == null || !Double.isFinite(total.doubleValue())
                    || row.s("day").isEmpty() || Jalali.parse(row.s("day")) < 0
                    || invalidRows == null || invalidRows > 0) return 0;
        }
        SalesSnapshot source = SalesSnapshot.fromRows(s.salesDaily, s.day, Jalali.addDays(s.day, -30));
        double average = source.averageCalendarDaysBefore(s.day, 7);
        return Double.isFinite(average) ? average : 0;
    }

    private static String sumAmountOrUnavailable(List<Row> rows) {
        if (rows == null) return "داده در دسترس نیست";
        BigDecimal total = BigDecimal.ZERO;
        for (Row row : rows) {
            BigDecimal amount = row == null ? null : row.bd("amount");
            if (amount == null) return "داده در دسترس نیست";
            total = total.add(amount);
        }
        if (!Double.isFinite(total.doubleValue())) return "داده در دسترس نیست";
        return money(total.doubleValue());
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
