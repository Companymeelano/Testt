package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Descriptive-only analysis for unusually dominant days; it never removes a valid point. */
public final class HomeTrendAnalysis {
    private static final double SPIKE_MULTIPLIER = 4d;
    private static final int MIN_POSITIVE_DAYS = 3;

    private HomeTrendAnalysis() { }

    public static final class Peak {
        public final int index;
        public final double value;
        public final double medianPositiveValue;
        public final double multipleOfMedian;

        private Peak(int index, double value, double medianPositiveValue, double multipleOfMedian) {
            this.index = index;
            this.value = value;
            this.medianPositiveValue = medianPositiveValue;
            this.multipleOfMedian = multipleOfMedian;
        }
    }

    /**
     * Flags a peak only when it is at least four times the median of three or more
     * non-zero days. The returned index points into the untouched input series.
     */
    public static Peak dominantPeak(double[] values) {
        if (values == null || values.length < 5) return null;
        List<Double> positive = new ArrayList<>();
        int peakIndex = -1;
        double peak = 0d;
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (!finite(value) || value <= 0d) continue;
            positive.add(value);
            if (peakIndex < 0 || value > peak) {
                peakIndex = i;
                peak = value;
            }
        }
        if (positive.size() < MIN_POSITIVE_DAYS || peakIndex < 0) return null;
        Collections.sort(positive);
        int middle = positive.size() / 2;
        double median = positive.size() % 2 == 0
                ? positive.get(middle - 1) / 2d + positive.get(middle) / 2d
                : positive.get(middle);
        if (!(median > 0d) || !finite(median)) return null;
        double multiple = peak / median;
        if (!finite(multiple) || multiple < SPIKE_MULTIPLIER) return null;
        return new Peak(peakIndex, peak, median, multiple);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
