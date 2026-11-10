package ir.meelano.manager.core;

/**
 * A conservative check that compares a booked receipt/payment total with its four
 * classified components. It never invents missing components: callers must pass
 * {@code available=false} unless the live Atiran schema can provide a complete split.
 */
public final class MoneyBreakdown {
    /** Half a Rial accommodates binary/SQL decimal conversion noise without hiding real gaps. */
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

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
