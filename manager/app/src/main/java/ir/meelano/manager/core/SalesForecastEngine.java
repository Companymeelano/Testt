package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Same-weekday, seven-calendar-day sales forecast with a historical spread band. */
public final class SalesForecastEngine {
    public static final int HORIZON_DAYS = 7;
    public static final int HISTORY_DAYS = 28;
    public static final int MIN_SAMPLES = 2;

    private SalesForecastEngine() { }

    public static final class DayForecast {
        public final String date;
        public final int weekdayIndex;
        public final int sampleCount;
        public final double expectedRials;
        public final double lowerRials;
        public final double upperRials;

        private DayForecast(String date, int weekdayIndex, int sampleCount,
                            double expectedRials, double lowerRials, double upperRials) {
            this.date = date;
            this.weekdayIndex = weekdayIndex;
            this.sampleCount = sampleCount;
            this.expectedRials = expectedRials;
            this.lowerRials = lowerRials;
            this.upperRials = upperRials;
        }
    }

    public static final class Result {
        public final boolean available;
        public final List<DayForecast> days;
        public final double expectedTotalRials;
        public final double lowerTotalRials;
        public final double upperTotalRials;

        private Result(boolean available, List<DayForecast> days,
                       double expectedTotalRials, double lowerTotalRials,
                       double upperTotalRials) {
            this.available = available;
            this.days = Collections.unmodifiableList(new ArrayList<>(days));
            this.expectedTotalRials = expectedTotalRials;
            this.lowerTotalRials = lowerTotalRials;
            this.upperTotalRials = upperTotalRials;
        }
    }

    /**
     * For each future date, use the preceding four weeks' observed values for that same
     * weekday. The band is expected value ± sample standard deviation, not a guarantee or
     * a probabilistic confidence interval. Friday is treated like every other weekday and
     * public holidays are never inferred.
     */
    public static Result forecast(SalesSnapshot snapshot) {
        if (snapshot == null || !snapshot.isAvailable()) return unavailable();
        String asOf = Jalali.disp(snapshot.asOfDate());
        String historyStart = Jalali.disp(snapshot.historyStart());
        int asOfDay = Jalali.parse(asOf);
        int historyDay = Jalali.parse(historyStart);
        if (asOfDay < 0 || historyDay < 0) return unavailable();

        List<DayForecast> result = new ArrayList<>();
        double expectedTotal = 0d;
        double lowerTotal = 0d;
        double upperTotal = 0d;
        for (int offset = 1; offset <= HORIZON_DAYS; offset++) {
            String date = Jalali.addDays(asOf, offset);
            int weekday = Jalali.weekdayIndex(date);
            if (weekday < 0) return unavailable();
            double[] samples = new double[4];
            int count = 0;
            for (int back = 1; back <= HISTORY_DAYS; back++) {
                int candidate = asOfDay - back;
                if (candidate < historyDay) break;
                String candidateDate = Jalali.format(candidate);
                if (Jalali.weekdayIndex(candidateDate) != weekday) continue;
                double value = snapshot.salesRialsOn(candidateDate);
                if (!finite(value)) continue;
                if (count < samples.length) samples[count++] = value;
            }
            if (count < MIN_SAMPLES) return unavailable();
            double mean = 0d;
            for (int i = 0; i < count; i++) mean += samples[i];
            mean /= count;
            double squared = 0d;
            for (int i = 0; i < count; i++) {
                double diff = samples[i] - mean;
                squared += diff * diff;
            }
            double deviation = count <= 1 ? 0d : Math.sqrt(squared / (count - 1));
            double lower = mean - deviation;
            double upper = mean + deviation;
            if (!finite(mean) || !finite(lower) || !finite(upper)) return unavailable();
            DayForecast day = new DayForecast(date, weekday, count, mean, lower, upper);
            result.add(day);
            expectedTotal += mean;
            lowerTotal += lower;
            upperTotal += upper;
        }
        if (!finite(expectedTotal) || !finite(lowerTotal) || !finite(upperTotal)) return unavailable();
        return new Result(true, result, expectedTotal, lowerTotal, upperTotal);
    }

    private static Result unavailable() {
        return new Result(false, Collections.<DayForecast>emptyList(),
                Double.NaN, Double.NaN, Double.NaN);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
