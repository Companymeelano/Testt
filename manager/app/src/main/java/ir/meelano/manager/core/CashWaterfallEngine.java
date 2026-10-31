package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, read-only till-flow model. The ending amount is expected, not a bank balance. */
public final class CashWaterfallEngine {
    private CashWaterfallEngine() { }

    public static final class Step {
        public final String label;
        /** Cumulative amount before this movement; totals start at zero. */
        public final double startRials;
        /** Cumulative amount after this movement; totals end at the displayed total. */
        public final double endRials;
        public final boolean total;

        private Step(String label, double startRials, double endRials, boolean total) {
            this.label = label;
            this.startRials = startRials;
            this.endRials = endRials;
            this.total = total;
        }

        public double deltaRials() { return endRials - startRials; }
        public double lowRials() { return Math.min(startRials, endRials); }
        public double highRials() { return Math.max(startRials, endRials); }
    }

    /**
     * Builds opening → cash receipts → cash payments → expected closing. Call only when
     * all three source amounts are available; missing balances are never silently zero-filled.
     */
    public static List<Step> build(double openingRials, double cashReceiptsRials,
                                   double cashPaymentsRials) {
        if (!valid(openingRials) || !valid(cashReceiptsRials) || !valid(cashPaymentsRials)
                || openingRials < 0 || cashReceiptsRials < 0 || cashPaymentsRials < 0)
            return Collections.emptyList();
        double afterReceipts = openingRials + cashReceiptsRials;
        double closing = afterReceipts - cashPaymentsRials;
        if (!valid(afterReceipts) || !valid(closing)) return Collections.emptyList();
        List<Step> steps = new ArrayList<>(4);
        steps.add(new Step("آغاز نقد", 0, openingRials, true));
        steps.add(new Step("دریافت نقد", openingRials, afterReceipts, false));
        steps.add(new Step("پرداخت نقد", afterReceipts, closing, false));
        steps.add(new Step("ماندهٔ مورد انتظار", 0, closing, true));
        return Collections.unmodifiableList(steps);
    }

    private static boolean valid(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
