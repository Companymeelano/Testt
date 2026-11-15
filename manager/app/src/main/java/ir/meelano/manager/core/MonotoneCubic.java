package ir.meelano.manager.core;

/** Fritsch–Carlson tangents and Hermite interpolation for an overshoot-free monotone curve. */
public final class MonotoneCubic {
    private MonotoneCubic() { }

    /**
     * Compute shape-preserving derivatives for points whose x values are strictly increasing.
     * Flat or reversing secants force a zero tangent, so an interpolated segment cannot overshoot.
     */
    public static double[] tangents(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length) throw new IllegalArgumentException("point arrays");
        int n = x.length;
        double[] result = new double[n];
        if (n < 2) return result;
        double[] h = new double[n - 1];
        double[] slope = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
            if (!(h[i] > 0) || !finite(h[i]) || !finite(y[i]) || !finite(y[i + 1]))
                throw new IllegalArgumentException("x must increase and points must be finite");
            slope[i] = (y[i + 1] - y[i]) / h[i];
        }
        if (n == 2) {
            result[0] = result[1] = slope[0];
            return result;
        }
        result[0] = endpoint(h[0], h[1], slope[0], slope[1]);
        result[n - 1] = endpoint(h[n - 2], h[n - 3], slope[n - 2], slope[n - 3]);
        for (int i = 1; i < n - 1; i++) {
            double before = slope[i - 1];
            double after = slope[i];
            if (before == 0 || after == 0 || Math.signum(before) != Math.signum(after)) {
                result[i] = 0;
            } else {
                double w1 = 2 * h[i] + h[i - 1];
                double w2 = h[i] + 2 * h[i - 1];
                result[i] = (w1 + w2) / (w1 / before + w2 / after);
            }
        }
        return result;
    }

    /** Evaluate one cubic Hermite segment; t is clamped to [0,1]. */
    public static double interpolate(double x0, double x1, double y0, double y1,
                                     double tangent0, double tangent1, double x) {
        double h = x1 - x0;
        if (!(h > 0)) return y0;
        double t = Math.max(0, Math.min(1, (x - x0) / h));
        double t2 = t * t;
        double t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * y0
                + (t3 - 2 * t2 + t) * h * tangent0
                + (-2 * t3 + 3 * t2) * y1
                + (t3 - t2) * h * tangent1;
    }

    private static double endpoint(double h0, double h1, double d0, double d1) {
        double m = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
        if (m == 0 || d0 == 0 || Math.signum(m) != Math.signum(d0)) return 0;
        if (Math.signum(d0) != Math.signum(d1) && Math.abs(m) > 3 * Math.abs(d0)) return 3 * d0;
        return m;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
