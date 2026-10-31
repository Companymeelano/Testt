package ir.meelano.manager.core;

/** Pure arithmetic for a locally entered till count; never writes to Atiran. */
public final class CashCloseMath {
    private CashCloseMath() { }

    /** Opening float + cash receipts − cash payments. Card, transfer and cheques are excluded. */
    public static double expectedClosing(double openingRials, double cashReceiptsRials,
                                          double cashPaymentsRials) {
        if (!finite(openingRials) || !finite(cashReceiptsRials) || !finite(cashPaymentsRials))
            return Double.NaN;
        return openingRials + cashReceiptsRials - cashPaymentsRials;
    }

    /** Counted cash − expected cash. Positive = over; negative = short. */
    public static double variance(double openingRials, double cashReceiptsRials,
                                  double cashPaymentsRials, double countedClosingRials) {
        if (!finite(countedClosingRials)) return Double.NaN;
        double expected = expectedClosing(openingRials, cashReceiptsRials, cashPaymentsRials);
        return finite(expected) ? countedClosingRials - expected : Double.NaN;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
