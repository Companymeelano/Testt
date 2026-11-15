package ir.meelano.manager.core;

/**
 * Pure planner behind the C12 LuxChart: it decides when a single outlier (the 4.2-billion
 * purchase peak in the field data) must break the axis instead of flattening every other
 * day into the baseline, and it builds the golden 7-day moving average series.
 */
public final class LuxChartPlan {

    private LuxChartPlan() { }

    /** Result of planning one series. */
    public static final class Plan {
        /** Top of the visible axis (nice-rounded). */
        public final double axisMax;
        /** True when the peak lives above the axis and needs the "out of frame" badge. */
        public final boolean broken;
        /** The outlier value when broken, otherwise the series maximum. */
        public final double peak;
        /** Centered-free trailing moving average, same length as the input (NaN while warm-up). */
        public final double[] movingAverage;

        Plan(double axisMax, boolean broken, double peak, double[] movingAverage) {
            this.axisMax = axisMax;
            this.broken = broken;
            this.peak = peak;
            this.movingAverage = movingAverage;
        }
    }

    /** An outlier is a point more than three times the runner-up; anything less stays in frame. */
    public static final double OUTLIER_RATIO = 3d;

    public static Plan plan(double[] values, int maWindow) {
        double[] v = values == null ? new double[0] : values;
        double max = 0d;
        double runnerUp = 0d;
        for (double x : v) {
            double a = Math.abs(x);
            if (a > max) {
                runnerUp = max;
                max = a;
            } else if (a > runnerUp) {
                runnerUp = a;
            }
        }
        boolean broken = max > runnerUp * OUTLIER_RATIO && runnerUp > 0d;
        double basis = broken ? runnerUp : max;
        double axisMax = niceCeil(basis * 1.15d);
        if (!(axisMax > 0d)) axisMax = 1d;
        return new Plan(axisMax, broken, max, movingAverage(v, maWindow));
    }

    /** Trailing moving average; entries before the window fills are NaN. */
    public static double[] movingAverage(double[] values, int window) {
        int n = values == null ? 0 : values.length;
        int w = Math.max(1, window);
        double[] out = new double[n];
        double sum = 0d;
        for (int i = 0; i < n; i++) {
            sum += values[i];
            if (i >= w) sum -= values[i - w];
            out[i] = i >= w - 1 ? sum / w : Double.NaN;
        }
        return out;
    }

    /** Rounds up to the nearest 1/2/2.5/5 × 10^k so axis ticks stay human. */
    public static double niceCeil(double value) {
        if (!(value > 0d) || Double.isInfinite(value)) return 0d;
        double pow = Math.pow(10d, Math.floor(Math.log10(value)));
        double unit = value / pow;
        double nice = unit <= 1d ? 1d : unit <= 2d ? 2d : unit <= 2.5d ? 2.5d : unit <= 5d ? 5d : 10d;
        return nice * pow;
    }

    /** Weekday-average baseline for the tooltip: mean of values at the same index mod 7. */
    public static double weekdayAverage(double[] values, int index) {
        if (values == null || values.length == 0) return Double.NaN;
        int slot = ((index % 7) + 7) % 7;
        double sum = 0d;
        int n = 0;
        for (int i = slot; i < values.length; i += 7) {
            sum += values[i];
            n++;
        }
        return n == 0 ? Double.NaN : sum / n;
    }
}
