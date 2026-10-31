package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable Home sales source. Amounts are always raw Atiran Rials here; display-unit
 * conversion belongs to the formatter at the edge of the UI and must happen exactly once.
 */
public final class SalesSnapshot {
    public static final String SOURCE_UNIT = "RIAL";

    public static final class Day {
        public final String date;
        public final double salesRials;
        public final long documents;

        public Day(String date, double salesRials, long documents) {
            this.date = Jalali.disp(date);
            this.salesRials = salesRials;
            this.documents = documents;
        }
    }

    public static final class Month {
        public final String key;
        public final double salesRials;
        public final long documents;

        Month(String key, double salesRials, long documents) {
            this.key = key;
            this.salesRials = salesRials;
            this.documents = documents;
        }
    }

    private final Map<String, Day> byDay;
    private final String asOfDate;
    private final String historyStart;
    private final boolean available;

    private SalesSnapshot(Map<String, Day> byDay, String asOfDate, String historyStart, boolean available) {
        this.byDay = Collections.unmodifiableMap(new TreeMap<>(byDay));
        this.asOfDate = Jalali.disp(asOfDate);
        this.historyStart = Jalali.disp(historyStart);
        this.available = available;
    }

    /** Build from the read-only `factorDaily` rows, inferring a conservative history start. */
    public static SalesSnapshot fromRows(List<Row> rows, String asOfDate) {
        return fromRows(rows, asOfDate, inferredStart(rows, asOfDate));
    }

    /** Build from `factorDaily` rows and the exact lower bound used by the query. */
    public static SalesSnapshot fromRows(List<Row> rows, String asOfDate, String historyStart) {
        TreeMap<String, Day> merged = new TreeMap<>();
        if (rows != null) {
            for (Row row : rows) {
                if (row == null) continue;
                String day = Jalali.disp(row.s("day"));
                if (day.isEmpty()) continue;
                Day prev = merged.get(day);
                double amount = row.d("total") + (prev == null ? 0 : prev.salesRials);
                long docs = row.l("docs") + (prev == null ? 0 : prev.documents);
                merged.put(day, new Day(day, amount, docs));
            }
        }
        return new SalesSnapshot(merged, asOfDate, historyStart, rows != null);
    }

    private static String inferredStart(List<Row> rows, String asOfDate) {
        String first = "";
        if (rows != null) for (Row row : rows) {
            if (row == null) continue;
            String day = Jalali.disp(row.s("day"));
            if (!day.isEmpty() && (first.isEmpty() || day.compareTo(first) < 0)) first = day;
        }
        return first.isEmpty() ? asOfDate : first;
    }

    public static SalesSnapshot empty(String asOfDate) {
        return new SalesSnapshot(Collections.<String, Day>emptyMap(), asOfDate, "", false);
    }

    public boolean isAvailable() { return available; }
    public String asOfDate() { return asOfDate; }
    public String historyStart() { return historyStart; }

    /** Missing calendar days are known zeroes when the source query succeeded. */
    public Day day(String date) {
        String key = Jalali.disp(date);
        if (key.isEmpty()) return new Day("", 0, 0);
        Day value = byDay.get(key);
        return value == null ? new Day(key, 0, 0) : value;
    }

    /** Raw Rial amount for one Jalali day; zero when no invoice was recorded. */
    public double salesRialsOn(String date) { return day(date).salesRials; }
    public long documentsOn(String date) { return day(date).documents; }

    /** Inclusive calendar range. Invalid or reversed dates return zero. */
    public double salesRialsBetween(String from, String to) {
        return sumBetween(from, to, false);
    }

    public long documentsBetween(String from, String to) {
        return Math.round(sumBetween(from, to, true));
    }

    private double sumBetween(String from, String to, boolean documents) {
        int start = Jalali.parse(Jalali.disp(from));
        int end = Jalali.parse(Jalali.disp(to));
        if (!available || start < 0 || end < start) return 0;
        double total = 0;
        for (Day day : byDay.values()) {
            int date = Jalali.parse(day.date);
            if (date >= start && date <= end) total += documents ? day.documents : day.salesRials;
        }
        return total;
    }

    /** Average over exactly `days` previous calendar days; dates without sales count as zero. */
    public double averageCalendarDaysBefore(String date, int days) {
        if (!available || days <= 0) return 0;
        String end = Jalali.addDays(date, -1);
        String start = Jalali.addDays(date, -days);
        int first = Jalali.parse(start);
        int history = Jalali.parse(historyStart);
        if (first < 0 || history < 0 || first < history) return 0;
        return salesRialsBetween(start, end) / days;
    }

    /**
     * Daily rows from oldest to newest, including missing days as zero. Useful for the
     * fixed 14-day chart; all values remain raw Rials until Charts.MONEY formats them.
     */
    public List<Day> dailySeries(String endDate, int count) {
        if (!available || count <= 0) return Collections.emptyList();
        String end = Jalali.disp(endDate);
        int endDay = Jalali.parse(end);
        if (endDay < 0) return Collections.emptyList();
        List<Day> result = new ArrayList<>();
        int first = endDay - count + 1;
        for (int day = first; day <= endDay; day++) {
            String key = Jalali.format(day);
            result.add(day(key));
        }
        return Collections.unmodifiableList(result);
    }

    /** Monthly buckets, newest first, folded from the same daily source. */
    public List<Month> months() {
        TreeMap<String, Month> byMonth = new TreeMap<>(Collections.reverseOrder());
        for (Day day : byDay.values()) {
            String key = day.date.substring(0, 7);
            Month prev = byMonth.get(key);
            byMonth.put(key, new Month(key,
                    day.salesRials + (prev == null ? 0 : prev.salesRials),
                    day.documents + (prev == null ? 0 : prev.documents)));
        }
        return Collections.unmodifiableList(new ArrayList<>(byMonth.values()));
    }

    /** Sum of the first N calendar days in a Jalali month (inclusive). */
    public double monthToDateRials(String monthKey, int days) {
        if (!available || monthKey == null || !monthKey.matches("\\d{4}/\\d{2}") || days <= 0) return 0;
        try {
            int year = Integer.parseInt(monthKey.substring(0, 4));
            int month = Integer.parseInt(monthKey.substring(5, 7));
            if (month < 1 || month > 12) return 0;
            int limit = Math.min(Jalali.daysInMonth(year, month), days);
            String first = monthKey + "/01";
            String last = monthKey + "/" + (limit < 10 ? "0" : "") + limit;
            return salesRialsBetween(first, last);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    /** Convert back to the existing row shape for KPI/navigation code. */
    public Row row(String date) {
        Day value = day(date);
        Row row = new Row();
        row.put("d", value.date);
        row.put("day", value.date);
        row.put("total", value.salesRials);
        row.put("docs", value.documents);
        return row;
    }
}
