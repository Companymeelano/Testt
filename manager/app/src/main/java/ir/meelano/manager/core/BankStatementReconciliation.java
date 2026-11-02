package ir.meelano.manager.core;

/**
 * Compares the two movement directions in a real bank statement with Atiran's recorded
 * bank ledger for the same account and day. This is a flow reconciliation, not a balance
 * reconciliation; it requires an actual statement entered by the manager.
 */
public final class BankStatementReconciliation {
    public static final double TOLERANCE_RIALS = 0.5d;

    public enum State {
        MATCH,
        VARIANCE,
        NEEDS_STATEMENT,
        LEDGER_UNAVAILABLE,
        INVALID
    }

    public static final class Result {
        public final State state;
        /** Statement minus ledger, kept separate so opposite errors cannot cancel. */
        public final double inflowVarianceRials;
        public final double outflowVarianceRials;
        /** Signed net movement difference; informational only, never used to declare a match. */
        public final double netVarianceRials;

        private Result(State state, double inflowVarianceRials, double outflowVarianceRials,
                       double netVarianceRials) {
            this.state = state;
            this.inflowVarianceRials = inflowVarianceRials;
            this.outflowVarianceRials = outflowVarianceRials;
            this.netVarianceRials = netVarianceRials;
        }

        public boolean comparable() {
            return state == State.MATCH || state == State.VARIANCE;
        }

        public boolean matches() { return state == State.MATCH; }
    }

    private BankStatementReconciliation() { }

    public static Result compare(boolean ledgerAvailable, double ledgerInRials, double ledgerOutRials,
                                 boolean statementAvailable, double statementInRials,
                                 double statementOutRials) {
        if (!ledgerAvailable)
            return unavailable(State.LEDGER_UNAVAILABLE);
        if (!finiteNonNegative(ledgerInRials) || !finiteNonNegative(ledgerOutRials))
            return unavailable(State.INVALID);
        if (!statementAvailable)
            return unavailable(State.NEEDS_STATEMENT);
        if (!finiteNonNegative(statementInRials) || !finiteNonNegative(statementOutRials))
            return unavailable(State.INVALID);

        double in = statementInRials - ledgerInRials;
        double out = statementOutRials - ledgerOutRials;
        double net = in - out;
        if (!Double.isFinite(in) || !Double.isFinite(out) || !Double.isFinite(net))
            return unavailable(State.INVALID);
        boolean match = Math.abs(in) <= TOLERANCE_RIALS && Math.abs(out) <= TOLERANCE_RIALS;
        return new Result(match ? State.MATCH : State.VARIANCE, in, out, net);
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0d;
    }

    private static Result unavailable(State state) {
        return new Result(state, Double.NaN, Double.NaN, Double.NaN);
    }
}
