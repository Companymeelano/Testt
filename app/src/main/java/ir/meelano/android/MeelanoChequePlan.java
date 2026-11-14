package ir.meelano.android;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * برنامهٔ هوشمند «تایید سامانهٔ چک‌ها» برای اپ یکپارچه — آینهٔ همان موتور در manager.
 *
 * <p>خالص و بدون Android: ردیف‌های چک و دفترچهٔ محلی تایید را می‌گیرد و دو کاسهٔ
 * تیک‌خورده/تیک‌نخورده را با جمع ریالی، نرخ تایید، معوق‌ها، سررسید نزدیک و سه طرف‌حساب
 * پرتکرار برمی‌گرداند تا هر صفحه و هر خروجی چاپ همان اعداد را نشان دهند.</p>
 */
final class MeelanoChequePlan {
    private MeelanoChequePlan() { }

    static final class Row {
        final boolean incoming;
        final String num;
        final String sayad;
        final String customer;
        final double amount;
        final String due;

        Row(boolean incoming, String num, String sayad, String customer, double amount, String due) {
            this.incoming = incoming;
            this.num = num == null ? "" : num.trim();
            this.sayad = sayad == null ? "" : sayad.trim();
            this.customer = customer == null ? "" : customer.trim();
            this.amount = amount;
            this.due = due == null ? "" : due.trim();
        }

        String key() { return keyOf(incoming, num, sayad); }
    }

    static final class Entry {
        final long ts;
        final String user;
        final String note;

        Entry(long ts, String user, String note) {
            this.ts = ts;
            this.user = user == null ? "" : user.trim();
            this.note = note == null ? "" : note.trim();
        }
    }

    static final class Plan {
        final List<Row> confirmed = new ArrayList<>();
        final List<Row> pending = new ArrayList<>();
        final Map<String, Entry> entries = new HashMap<>();
        private final Set<String> confirmedKeys = new HashSet<>();
        double confirmedSum;
        double pendingSum;
        int pendingOverdue;
        int pendingDueSoon;
        Row nearestPending;
        int nearestPendingDays = Integer.MAX_VALUE;
        private Row fallbackOverdue;
        private int fallbackOverdueDays = Integer.MAX_VALUE;
        final List<String> topPending = new ArrayList<>();

        int confirmedCount() { return confirmed.size(); }

        int pendingCount() { return pending.size(); }

        int ratePercent() {
            int total = confirmed.size() + pending.size();
            return total == 0 ? 0 : (int) Math.round(100d * confirmed.size() / total);
        }

        boolean isConfirmed(String key) { return confirmedKeys.contains(key); }

        Entry entryOf(String key) { return entries.get(key); }
    }

    /** کلید یکتا: جهت + شناسهٔ صیادی (در صورت وجود) + شمارهٔ چک. */
    static String keyOf(boolean incoming, String num, String sayad) {
        String s = sayad == null ? "" : sayad.trim();
        String n = num == null ? "" : num.trim();
        return (incoming ? "in:" : "out:") + (s.isEmpty() ? n : s) + ":" + n;
    }

    /** سررسید خام (میلادی یا جلالی) را به نمایش جلالی نرمال می‌کند؛ نامعتبر → رشتهٔ خالی. */
    static String normalizeDue(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        int j = MeelanoJalali.parse(t);
        if (j >= 0) return MeelanoJalali.format(j);
        String[] p = t.split("[/-]");
        if (p.length < 3) return "";
        try {
            int y = Integer.parseInt(p[0].trim()), m = Integer.parseInt(p[1].trim());
            String dd = p[2].trim();
            int d = Integer.parseInt(dd.length() > 2 ? dd.substring(0, 2) : dd);
            if (y < 1700 || y > 2200 || m < 1 || m > 12 || d < 1 || d > 31) return "";
            return MeelanoJalali.format(MeelanoJalali.gregorianDay(y, m, d));
        } catch (Exception e) {
            return "";
        }
    }

    /** سررسید نمایشی/خام را به شمارهٔ روز جلالی می‌برد؛ نامعتبر → ‎-1. */
    static int dueKey(String due) {
        return due == null || due.trim().isEmpty() ? -1 : MeelanoJalali.parse(due.trim());
    }

    static Plan plan(List<Row> rows, Map<String, Entry> registry, int today) {
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
                    Integer prev = pendingByCustomer.get(who);
                    pendingByCustomer.put(who, prev == null ? 1 : prev + 1);
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
}
