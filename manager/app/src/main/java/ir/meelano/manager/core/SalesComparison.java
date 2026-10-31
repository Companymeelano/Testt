package ir.meelano.manager.core;

/** Same-weekday baseline and calendar-day run-rate for a Home sales day. */
public final class SalesComparison {
    public static final int DEFAULT_COMPARABLE_WEEKS = 4;
    public static final int MINUTES_PER_DAY = 24 * 60;

    private SalesComparison() { }

    public static final class DayResult {
        public final String date;
        public final int weekdayIndex;
        public final boolean weeklyHoliday;
        public final boolean available;
        public final boolean complete;
        public final boolean projectionAvailable;
        public final double actualRials;
        public final double projectedRials;
        public final double sameWeekdayAverageRials;
        public final int comparableDays;

        private DayResult(String date, int weekdayIndex, boolean available, boolean complete,
                          boolean projectionAvailable, double actualRials, double projectedRials,
                          double sameWeekdayAverageRials, int comparableDays) {
            this.date = date;
            this.weekdayIndex = weekdayIndex;
            this.weeklyHoliday = weekdayIndex == 6; // Friday only; official holidays are not inferred.
            this.available = available;
            this.complete = complete;
            this.projectionAvailable = projectionAvailable;
            this.actualRials = actualRials;
            this.projectedRials = projectedRials;
            this.sameWeekdayAverageRials = sameWeekdayAverageRials;
            this.comparableDays = comparableDays;
        }

        public boolean hasBaseline() { return comparableDays > 0 && sameWeekdayAverageRials > 0; }

        public double changePercent() {
            if (!hasBaseline() || !projectionAvailable) return Double.NaN;
            return (projectedRials - sameWeekdayAverageRials) * 100.0 / sameWeekdayAverageRials;
        }
    }

    /**
     * Compare the date with up to `weeks` previous occurrences of the same weekday.
     * `minuteOfDay` is elapsed local time from 00:00 (0..1440); at 0 no projection is
     * claimed, during an incomplete day the projection uses the full 1,440-minute day.
     */
    public static DayResult evaluate(SalesSnapshot snapshot, String date, int minuteOfDay, int weeks) {
        String day = Jalali.disp(date);
        int weekday = Jalali.weekdayIndex(day);
        if (snapshot == null || !snapshot.isAvailable() || day.isEmpty() || weekday < 0)
            return new DayResult(day, weekday, false, false, false, 0, 0, 0, 0);

        int minutes = Math.max(0, Math.min(MINUTES_PER_DAY, minuteOfDay));
        boolean complete = minutes >= MINUTES_PER_DAY;
        boolean projectionAvailable = minutes > 0;
        double actual = snapshot.salesRialsOn(day);
        double projected = projectionAvailable
                ? (complete ? actual : actual * MINUTES_PER_DAY / minutes) : 0;

        int today = Jalali.parse(day);
        int historyStart = Jalali.parse(snapshot.historyStart());
        int samples = Math.max(0, weeks);
        int count = 0;
        double total = 0;
        if (today >= 0 && historyStart >= 0) {
            for (int i = 1; i <= samples; i++) {
                int candidate = today - 7 * i;
                if (candidate < historyStart) break;
                total += snapshot.salesRialsOn(Jalali.format(candidate));
                count++;
            }
        }
        double baseline = count == 0 ? 0 : total / count;
        return new DayResult(day, weekday, true, complete, projectionAvailable,
                actual, projected, baseline, count);
    }

    public static boolean isWeeklyHoliday(String date) {
        return Jalali.weekdayIndex(date) == 6;
    }
}
