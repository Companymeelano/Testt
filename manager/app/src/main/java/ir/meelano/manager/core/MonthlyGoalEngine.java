package ir.meelano.manager.core;

/** Pure monthly sales-goal math. Values remain in Atiran Rials; display conversion is UI-only. */
public final class MonthlyGoalEngine {
    private MonthlyGoalEngine() { }

    public static final class Result {
        public final boolean available;
        public final double salesRials;
        public final double targetRials;
        public final double achievementPercent;
        /** Progress is clamped for the circular gauge; the spoken percentage stays exact. */
        public final double gaugePercent;
        public final double projectedMonthRials;
        public final double projectedPercent;
        public final int elapsedCalendarDays;
        public final int monthCalendarDays;

        private Result(boolean available, double salesRials, double targetRials,
                       double achievementPercent, double gaugePercent,
                       double projectedMonthRials, double projectedPercent,
                       int elapsedCalendarDays, int monthCalendarDays) {
            this.available = available;
            this.salesRials = salesRials;
            this.targetRials = targetRials;
            this.achievementPercent = achievementPercent;
            this.gaugePercent = gaugePercent;
            this.projectedMonthRials = projectedMonthRials;
            this.projectedPercent = projectedPercent;
            this.elapsedCalendarDays = elapsedCalendarDays;
            this.monthCalendarDays = monthCalendarDays;
        }
    }

    /**
     * Evaluate month-to-date sales and a calendar-day run rate. Elapsed days are whole
     * Jalali calendar days (00:00–24:00); this intentionally does not interpolate by time.
     */
    public static Result evaluate(double salesRials, double targetRials,
                                  int elapsedCalendarDays, int monthCalendarDays) {
        if (!finite(salesRials) || !finite(targetRials) || targetRials <= 0
                || elapsedCalendarDays < 1 || monthCalendarDays < elapsedCalendarDays) {
            return new Result(false, salesRials, targetRials, Double.NaN,
                    0d, Double.NaN, Double.NaN,
                    elapsedCalendarDays, monthCalendarDays);
        }
        double achieved = salesRials / targetRials * 100d;
        double projected = salesRials / elapsedCalendarDays * monthCalendarDays;
        double projectedPct = projected / targetRials * 100d;
        if (!finite(achieved) || !finite(projected) || !finite(projectedPct)) {
            return new Result(false, salesRials, targetRials, Double.NaN,
                    0d, Double.NaN, Double.NaN,
                    elapsedCalendarDays, monthCalendarDays);
        }
        return new Result(true, salesRials, targetRials, achieved,
                Math.max(0d, Math.min(100d, achieved)), projected, projectedPct,
                elapsedCalendarDays, monthCalendarDays);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
