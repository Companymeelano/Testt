package ir.meelano.manager.core;

import java.math.BigDecimal;

/**
 * Compares the two movement directions in a real bank statement with Atiran's read-only
 * bank ledger. This is a daily flow reconciliation, not a balance or transaction-level match.
 * Every monetary comparison is decimal-exact; doubles are retained only for legacy display APIs.
 */
public final class BankStatementReconciliation {
    public static final BigDecimal TOLERANCE_RIALS_EXACT = new BigDecimal("0.5");
    public static final double TOLERANCE_RIALS = 0.5d;
    private static final BigDecimal MAX_COMPARABLE_RIALS = BankStatementFile.MAX_AMOUNT_RIALS;

    public enum State {
        MATCH,
        VARIANCE,
        NEEDS_STATEMENT,
        LEDGER_UNAVAILABLE,
        INVALID
    }

    public static final class Result {
        public final State state;
        /** Exact statement-minus-ledger values; null when comparison is unavailable. */
        public final BigDecimal inflowVarianceRialsExact;
        public final BigDecimal outflowVarianceRialsExact;
        public final BigDecimal netVarianceRialsExact;
        /** Display compatibility only; never use these fields to determine the result. */
        public final double inflowVarianceRials;
        public final double outflowVarianceRials;
        public final double netVarianceRials;

        private Result(State state, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {
            this.state = state;
            this.inflowVarianceRialsExact = inflow;
            this.outflowVarianceRialsExact = outflow;
            this.netVarianceRialsExact = net;
            this.inflowVarianceRials = inflow == null ? Double.NaN : inflow.doubleValue();
            this.outflowVarianceRials = outflow == null ? Double.NaN : outflow.doubleValue();
            this.netVarianceRials = net == null ? Double.NaN : net.doubleValue();
        }

        public boolean comparable() {
            return state == State.MATCH || state == State.VARIANCE;
        }

        public boolean matches() { return state == State.MATCH; }
    }

    private BankStatementReconciliation() { }

    /** Compatibility overload for older double-based callers. */
    public static Result compare(boolean ledgerAvailable, double ledgerInRials, double ledgerOutRials,
                                 boolean statementAvailable, double statementInRials,
                                 double statementOutRials) {
        if (!Double.isFinite(ledgerInRials) || !Double.isFinite(ledgerOutRials)
                || !Double.isFinite(statementInRials) || !Double.isFinite(statementOutRials))
            return unavailable(State.INVALID);
        return compare(ledgerAvailable, BigDecimal.valueOf(ledgerInRials), BigDecimal.valueOf(ledgerOutRials),
                statementAvailable, BigDecimal.valueOf(statementInRials), BigDecimal.valueOf(statementOutRials));
    }

    public static Result compare(boolean ledgerAvailable, BigDecimal ledgerInRials, BigDecimal ledgerOutRials,
                                 boolean statementAvailable, BigDecimal statementInRials,
                                 BigDecimal statementOutRials) {
        if (!ledgerAvailable) return unavailable(State.LEDGER_UNAVAILABLE);
        if (!validAmount(ledgerInRials) || !validAmount(ledgerOutRials))
            return unavailable(State.INVALID);
        if (!statementAvailable) return unavailable(State.NEEDS_STATEMENT);
        if (!validAmount(statementInRials) || !validAmount(statementOutRials))
            return unavailable(State.INVALID);

        BigDecimal in = statementInRials.subtract(ledgerInRials);
        BigDecimal out = statementOutRials.subtract(ledgerOutRials);
        BigDecimal net = in.subtract(out);
        if (!validDifference(in) || !validDifference(out) || !validDifference(net))
            return unavailable(State.INVALID);
        boolean match = in.abs().compareTo(TOLERANCE_RIALS_EXACT) <= 0
                && out.abs().compareTo(TOLERANCE_RIALS_EXACT) <= 0;
        return new Result(match ? State.MATCH : State.VARIANCE, in, out, net);
    }

    private static boolean validAmount(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.scale() <= 4
                && value.precision() <= 24 && value.compareTo(MAX_COMPARABLE_RIALS) <= 0;
    }

    private static boolean validDifference(BigDecimal value) {
        return value != null && value.scale() <= 4 && value.precision() <= 25
                && value.abs().compareTo(MAX_COMPARABLE_RIALS.multiply(BigDecimal.valueOf(2))) <= 0;
    }

    private static Result unavailable(State state) {
        return new Result(state, null, null, null);
    }
}
