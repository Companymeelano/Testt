package ir.meelano.manager.core;

import java.math.BigDecimal;

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

    /** Exact opening float + cash receipts − cash payments, preserving every decimal Rial. */
    public static BigDecimal expectedClosingExact(BigDecimal openingRials,
            BigDecimal cashReceiptsRials, BigDecimal cashPaymentsRials) {
        if (openingRials == null || cashReceiptsRials == null || cashPaymentsRials == null) return null;
        return openingRials.add(cashReceiptsRials).subtract(cashPaymentsRials);
    }

    /** Exact counted cash − expected cash. Positive = over; negative = short. */
    public static BigDecimal varianceExact(BigDecimal openingRials, BigDecimal cashReceiptsRials,
            BigDecimal cashPaymentsRials, BigDecimal countedClosingRials) {
        if (countedClosingRials == null) return null;
        BigDecimal expected = expectedClosingExact(openingRials, cashReceiptsRials, cashPaymentsRials);
        return expected == null ? null : countedClosingRials.subtract(expected);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
