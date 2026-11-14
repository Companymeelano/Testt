package ir.meelano.manager.core;

/**
 * Pure column planner for the PDF/PNG/XLSX table exporters.
 *
 * The old exporter gave every column a fixed width by type and then scaled the whole
 * table down, which squeezed ten-column aging reports until money values were cut
 * mid-digit ("تومان۱٬۷۹٬۸۹٬۶۶…"). This planner allocates the available width between
 * each column's minimum (its heading) and its preferred width (widest real content),
 * so money columns keep the room their digits need and only text columns give up space.
 */
public final class ReportTablePlan {

    private ReportTablePlan() { }

    /** Cell font ladder, largest first; the planner walks down it before cutting digits. */
    public static final float[] FONT_STEPS = {11.5f, 10.5f, 9.5f, 8.5f};

    /**
     * Widths that sum to at most {@code available}: exact preferred widths when they fit,
     * otherwise a proportional squeeze between each column's minimum and preferred width,
     * and a proportional split of the minimums when even those cannot fit (the caller then
     * walks the font ladder).
     */
    public static double[] widths(double[] preferred, double[] minimum, double available) {
        int n = preferred == null ? 0 : preferred.length;
        double[] out = new double[n];
        if (n == 0 || !(available > 0d)) return out;
        double sumPref = 0d;
        double sumMin = 0d;
        double[] min = new double[n];
        for (int i = 0; i < n; i++) {
            double p = Math.max(0d, preferred[i]);
            double m = Math.max(0d, Math.min(minimum == null ? 0d : minimum[i], p));
            min[i] = m;
            sumPref += p;
            sumMin += m;
        }
        if (sumPref <= available) {
            for (int i = 0; i < n; i++) out[i] = preferred[i];
            return out;
        }
        if (sumMin >= available) {
            for (int i = 0; i < n; i++) out[i] = min[i] * available / sumMin;
            return out;
        }
        double scale = (available - sumMin) / (sumPref - sumMin);
        for (int i = 0; i < n; i++) out[i] = min[i] + (preferred[i] - min[i]) * scale;
        return out;
    }

    /**
     * Index into {@link #FONT_STEPS} of the first size at which {@code neededWidth}
     * (measured at FONT_STEPS[0]) fits {@code allocated}; the last step when none fits
     * and the caller must switch the column to its compact number form.
     */
    public static int fontStep(double neededWidth, double allocated) {
        if (!(neededWidth > 0d)) return 0;
        double base = FONT_STEPS[0];
        for (int i = 0; i < FONT_STEPS.length; i++) {
            if (neededWidth * (FONT_STEPS[i] / base) <= allocated) return i;
        }
        return FONT_STEPS.length - 1;
    }

    /** True when the table is wide enough to deserve a landscape page. */
    public static boolean landscape(int columnCount, double preferredTotal, double portraitWidth) {
        return columnCount >= 8 || preferredTotal > portraitWidth * 1.15d;
    }
}
