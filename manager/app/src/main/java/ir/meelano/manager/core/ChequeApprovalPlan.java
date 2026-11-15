package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * برنامهٔ هوشمند «تایید سامانهٔ چک‌ها» — گزارش تیک‌خورده/تیک‌نخورده.
 *
 * <p>این کلاس کاملاً خالص است (بدون Android): ردیف‌های چک و دفترچهٔ محلی تایید را می‌گیرد و
 * یک برنامهٔ گزارش می‌سازد که هر سه کلاینت (اندروید مدیر، اپ یکپارچه، ویندوز) همان اعداد را
 * نشان می‌دهند. «تایید سامانه» یعنی کاربر ثبت صیادی/سامانه‌ای چک را در برنامه تایید کرده است؛
 * بنابراین معیار گزارش، دفترچهٔ محلی است نه حدس از وضعیت بانک.</p>
 *
 * <p>هوشمندی گزارش: نرخ تایید، جمع ریالی هر دو کاسه، معوق‌های تیک‌نخورده، سررسید‌های نزدیک
 * (۷ روز)، نزدیک‌ترین سررسید باز و سه طرف‌حسابی که بیشترین چک تاییدنشده را دارند.</p>
 */
public final class ChequeApprovalPlan {

    private ChequeApprovalPlan() { }

    /** یک ردیف چک برای برنامهٔ گزارش (از فهرست دریافتی/پرداختی آتیران). */
    public static final class Row {
        public final boolean incoming;
        public final String num;
        public final String sayad;
        public final String customer;
        public final double amount;
        public final String due;          // نمایش جلالی سررسید
        public final String statusLabel;  // برچسب وضعیت منبع داده

        public Row(boolean incoming, String num, String sayad, String customer,
                   double amount, String due, String statusLabel) {
            this.incoming = incoming;
            this.num = num == null ? "" : num.trim();
            this.sayad = sayad == null ? "" : sayad.trim();
            this.customer = customer == null ? "" : customer.trim();
            this.amount = amount;
            this.due = due == null ? "" : due.trim();
            this.statusLabel = statusLabel == null ? "" : statusLabel.trim();
        }

        public String key() { return keyOf(incoming, num, sayad); }
    }

    /** یک ثبت تایید در دفترچهٔ محلی برنامه. */
    public static final class Entry {
        public final long ts;
        public final String user;
        public final String note;

        public Entry(long ts, String user, String note) {
            this.ts = ts;
            this.user = user == null ? "" : user.trim();
            this.note = note == null ? "" : note.trim();
        }
    }

    /** خروجش برنامهٔ گزارش. */
    public static final class Plan {
        public final List<Row> confirmed = new ArrayList<>();
        public final List<Row> pending = new ArrayList<>();
        public final Map<String, Entry> entries = new HashMap<>();
        final Set<String> confirmedKeys = new HashSet<>();
        public double confirmedSum;
        public double pendingSum;
        public int pendingOverdue;      // تیک‌نخورده با سررسید گذشته
        public int pendingDueSoon;      // تیک‌نخورده با سررسید امروز..۷ روز
        public Row nearestPending;      // نزدیک‌ترین سررسید پیشِ رو؛ وگرنه کهنه‌ترین معوق
        public int nearestPendingDays = Integer.MAX_VALUE;
        private Row fallbackOverdue;
        private int fallbackOverdueDays = Integer.MAX_VALUE;
        public final List<String> topPending = new ArrayList<>(); // «نام ×تعداد»

        public int confirmedCount() { return confirmed.size(); }

        public int pendingCount() { return pending.size(); }

        public int ratePercent() {
            int total = confirmed.size() + pending.size();
            return total == 0 ? 0 : (int) Math.round(100d * confirmed.size() / total);
        }

        public boolean isConfirmed(String key) { return confirmedKeys.contains(key); }

        public Entry entryOf(String key) { return entries.get(key); }
    }

    /**
     * کلید یکتای یک چک: جهت + شناسهٔ صیادی (در صورت وجود) + شمارهٔ چک.
     * صیادی ترجیح دارد چون شمارهٔ چک در بانک‌های مختلف می‌تواند تکراری باشد.
     */
    public static String keyOf(boolean incoming, String num, String sayad) {
        String s = sayad == null ? "" : sayad.trim();
        String n = num == null ? "" : num.trim();
        return (incoming ? "in:" : "out:") + (s.isEmpty() ? n : s) + ":" + n;
    }

    /** سررسید (خام یا نمایشی) را به شمارهٔ روز جلالی می‌برد؛ نامعتبر → ‎-1. */
    public static int dueKey(String due) {
        if (due == null || due.trim().isEmpty()) return -1;
        int viaDisp = Jalali.parse(Jalali.disp(due));
        return viaDisp >= 0 ? viaDisp : Jalali.parse(due.trim());
    }

    /**
     * ساخت برنامهٔ گزارش.
     *
     * @param rows     ردیف‌های چک هر دو جهت (فقط ردیف‌های نمای فعلی نه — کل مجموعهٔ بارگیری‌شده)
     * @param registry دفترچهٔ محلی تایید (کلید → ثبت)
     * @param today    شمارهٔ روز جلالی امروز (خروجی {@link Jalali#today()})
     */
    public static Plan plan(List<Row> rows, Map<String, Entry> registry, int today) {
        Plan p = new Plan();
        if (registry != null) p.entries.putAll(registry);
        Map<String, Integer> pendingByCustomer = new LinkedHashMap<>();
        if (rows != null) {
            for (Row r : rows) {
                Entry e = p.entries.get(r.key());
                if (e != null) {
                    p.confirmed.add(r);
                    p.confirmedKeys.add(r.key());
                    p.confirmedSum += r.amount;
                } else {
                    p.pending.add(r);
                    p.pendingSum += r.amount;
                    int dk = dueKey(r.due);
                    if (dk >= 0) {
                        int days = dk - today;
                        if (days < 0) {
                            p.pendingOverdue++;
                            if (days < p.fallbackOverdueDays) {
                                p.fallbackOverdueDays = days;
                                p.fallbackOverdue = r;
                            }
                        } else {
                            if (days <= 7) p.pendingDueSoon++;
                            if (days < p.nearestPendingDays) {
                                p.nearestPendingDays = days;
                                p.nearestPending = r;
                            }
                        }
                    }
                    String who = r.customer.isEmpty() ? "بدون طرف حساب" : r.customer;
                    pendingByCustomer.merge(who, 1, Integer::sum);
                }
            }
        }
        if (p.nearestPending == null && p.fallbackOverdue != null) {
            p.nearestPending = p.fallbackOverdue;
            p.nearestPendingDays = p.fallbackOverdueDays;
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(pendingByCustomer.entrySet());
        sorted.sort((x, y) -> y.getValue() != x.getValue()
                ? Integer.compare(y.getValue(), x.getValue())
                : x.getKey().compareTo(y.getKey()));
        for (int i = 0; i < sorted.size() && i < 3; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            p.topPending.add(e.getKey() + " ×" + e.getValue());
        }
        return p;
    }

    /** روزهای مانده تا سررسید (منفی = گذشته)؛ سررسید نامعتبر → Integer.MIN_VALUE. */
    public static int daysUntil(String dueDisplay, int today) {
        int dk = dueKey(dueDisplay);
        return dk < 0 || today < 0 ? Integer.MIN_VALUE : dk - today;
    }
}
