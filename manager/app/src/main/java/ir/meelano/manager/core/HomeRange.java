package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;

import java.util.List;

/**
 * The Home's calendar windows. All ranges are inclusive Jalali dates, use the phone's
 * local calendar, and are shared by the KPI, trend and month-comparison calculations.
 */
public final class HomeRange {
    public enum Kind { TODAY, YESTERDAY, LAST_7_DAYS, MONTH_TO_DATE }

    public final Kind kind;
    public final String from;
    public final String to;

    private HomeRange(Kind kind, String from, String to) {
        this.kind = kind;
        this.from = Jalali.disp(from);
        this.to = Jalali.disp(to);
    }

    public static HomeRange of(Kind kind, String date) {
        String day = Jalali.disp(date);
        if (day.isEmpty()) return new HomeRange(kind, "", "");
        if (kind == Kind.YESTERDAY) {
            String y = Jalali.addDays(day, -1);
            return new HomeRange(kind, y, y);
        }
        if (kind == Kind.LAST_7_DAYS)
            return new HomeRange(kind, Jalali.addDays(day, -6), day);
        if (kind == Kind.MONTH_TO_DATE)
            return new HomeRange(kind, Jalali.monthStart(day), day);
        return new HomeRange(Kind.TODAY, day, day);
    }

    public static HomeRange today(String date) { return of(Kind.TODAY, date); }
    public static HomeRange yesterday(String date) { return of(Kind.YESTERDAY, date); }
    public static HomeRange last7Days(String date) { return of(Kind.LAST_7_DAYS, date); }
    public static HomeRange monthToDate(String date) { return of(Kind.MONTH_TO_DATE, date); }

    /** Complete previous Jalali month, used to make the chart query window month-safe. */
    public static HomeRange previousFullMonth(String date) {
        HomeRange current = monthToDate(date);
        if (current.from.isEmpty()) return new HomeRange(Kind.MONTH_TO_DATE, "", "");
        String previousEnd = Jalali.addDays(current.from, -1);
        return new HomeRange(Kind.MONTH_TO_DATE, Jalali.monthStart(previousEnd), previousEnd);
    }

    /** Previous month, clipped to the same day number (clamped when that month is shorter). */
    public static HomeRange previousMonthSamePeriod(String date) {
        HomeRange current = monthToDate(date);
        if (current.from.isEmpty()) return new HomeRange(Kind.MONTH_TO_DATE, "", "");
        int currentDay = dayOfMonth(current.to);
        String previousEnd = Jalali.addDays(current.from, -1);
        String previousStart = Jalali.monthStart(previousEnd);
        int previousDays = daysInMonth(previousStart);
        int matchedDays = Math.min(currentDay, previousDays);
        String matchedEnd = Jalali.addDays(previousStart, Math.max(0, matchedDays - 1));
        return new HomeRange(Kind.MONTH_TO_DATE, previousStart, matchedEnd);
    }

    /** Earliest date required for chart, current month-to-date and full prior-month context. */
    public static String salesHistoryStart(String date) {
        HomeRange previous = previousFullMonth(date);
        String fourteenDayStart = Jalali.addDays(Jalali.disp(date), -13);
        if (previous.from.isEmpty()) return fourteenDayStart;
        if (fourteenDayStart == null || fourteenDayStart.isEmpty()) return previous.from;
        return previous.from.compareTo(fourteenDayStart) < 0 ? previous.from : fourteenDayStart;
    }

    /** Aggregate a successful per-day query after normalizing Jalali/Gregorian row dates. */
    public static Row aggregateDay(List<Row> dailyRows, String date) {
        String target = Jalali.disp(date);
        if (dailyRows == null || target.isEmpty()) return null;
        double total = 0;
        long documents = 0;
        for (Row row : dailyRows) {
            if (row == null || !target.equals(Jalali.disp(row.s("day")))) continue;
            total += row.d("total");
            documents += row.l("docs");
        }
        Row result = new Row();
        result.put("d", target);
        result.put("day", target);
        result.put("total", total);
        result.put("docs", documents);
        return result;
    }

    public int calendarDays() {
        int start = Jalali.parse(from);
        int end = Jalali.parse(to);
        return start < 0 || end < start ? 0 : end - start + 1;
    }

    /** Current-month run-rate projection; elapsed days are calendar days, including today. */
    public static double projectMonthEnd(double monthToDateRials, String date) {
        HomeRange month = monthToDate(date);
        if (month.from.isEmpty()) return 0;
        int elapsedDays = dayOfMonth(month.to);
        int totalDays = daysInMonth(month.from);
        if (elapsedDays <= 0 || totalDays <= 0) return 0;
        return monthToDateRials * totalDays / elapsedDays;
    }

    public static int dayOfMonth(String date) {
        String normalized = Jalali.disp(date);
        if (normalized.isEmpty()) return 0;
        try { return Integer.parseInt(normalized.substring(8, 10)); }
        catch (NumberFormatException invalid) { return 0; }
    }

    public static int daysInMonth(String date) {
        String normalized = Jalali.disp(date);
        if (normalized.isEmpty()) return 0;
        try {
            int year = Integer.parseInt(normalized.substring(0, 4));
            int month = Integer.parseInt(normalized.substring(5, 7));
            return Jalali.daysInMonth(year, month);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }
}
