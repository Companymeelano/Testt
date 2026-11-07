package ir.meelano.manager.core;

import java.math.BigDecimal;

/**
 * A conservative check that compares a booked receipt/payment total with its four
 * classified components. It never invents missing components: callers must pass
 * {@code available=false} unless the live Atiran schema can provide a complete split.
 */
public final class MoneyBreakdown {
    /** Legacy tolerance retained for the double-only compatibility overload. */
    public static final double TOLERANCE = 0.5d;

    public final boolean available;
    public final double bookedTotal;
    public final double componentTotal;
    /** Positive means the booked total exceeds the classified components. */
    public final double unexplained;

    private MoneyBreakdown(boolean available, double bookedTotal,
            double componentTotal, double unexplained) {
        this.available = available;
        this.bookedTotal = bookedTotal;
        this.componentTotal = componentTotal;
        this.unexplained = unexplained;
    }

    public static MoneyBreakdown calculate(boolean available, double bookedTotal,
            double cash, double card, double transfer, double cheque) {
        if (!available || !finite(bookedTotal) || !finite(cash) || !finite(card)
                || !finite(transfer) || !finite(cheque)) {
            return new MoneyBreakdown(false, bookedTotal, Double.NaN, Double.NaN);
        }
        double components = cash + card + transfer + cheque;
        if (!finite(components)) return new MoneyBreakdown(false, bookedTotal, Double.NaN, Double.NaN);
        return new MoneyBreakdown(true, bookedTotal, components, bookedTotal - components);
    }

    public boolean matches() {
        return available && Math.abs(unexplained) <= TOLERANCE;
    }

    /** Exact half-Rial threshold for the dashboard's read-only financial controls. */
    public static final BigDecimal TOLERANCE_EXACT = new BigDecimal("0.5");

    /** Exact decimal result used by money controls; null values never become zero. */
    public static final class Exact {
        public final boolean available;
        public final BigDecimal bookedTotal;
        public final BigDecimal componentTotal;
        /** Positive means the booked total exceeds the classified components. */
        public final BigDecimal unexplained;

        private Exact(boolean available, BigDecimal bookedTotal,
                      BigDecimal componentTotal, BigDecimal unexplained) {
            this.available = available;
            this.bookedTotal = bookedTotal;
            this.componentTotal = componentTotal;
            this.unexplained = unexplained;
        }

        public boolean matches() {
            return available && unexplained.abs().compareTo(TOLERANCE_EXACT) <= 0;
        }

        public boolean exactlyEqual() {
            return available && unexplained.signum() == 0;
        }
    }

    /**
     * Exact counterpart for financial paths. A missing, malformed, or negative source amount is
     * unavailable; it cannot be treated as a zero component or a successful reconciliation.
     */
    public static Exact calculateExact(boolean available, BigDecimal bookedTotal,
            BigDecimal cash, BigDecimal card, BigDecimal transfer, BigDecimal cheque) {
        if (!available || !validExact(bookedTotal) || !validExact(cash) || !validExact(card)
                || !validExact(transfer) || !validExact(cheque))
            return new Exact(false, bookedTotal, null, null);
        BigDecimal components = cash.add(card).add(transfer).add(cheque);
        return new Exact(true, bookedTotal, components, bookedTotal.subtract(components));
    }

    private static boolean validExact(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.precision() <= 38
                && value.scale() <= 18;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
