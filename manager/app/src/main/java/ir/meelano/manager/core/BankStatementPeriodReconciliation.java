package ir.meelano.manager.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily, direction-by-direction comparison of a manually supplied bank export with
 * Atiran's read-only bank ledger for the same account and Jalali date range.
 * Missing statement dates mean zero movement only after the manager confirms that
 * the selected export is complete for the whole range.
 */
public final class BankStatementPeriodReconciliation {
    public static final class DailyFlow {
        public final double inflowRials;
        public final double outflowRials;
        public final int transactions;

        public DailyFlow(double inflowRials, double outflowRials, int transactions) {
            this.inflowRials = inflowRials;
            this.outflowRials = outflowRials;
            this.transactions = transactions;
        }
    }

    public static final class DayResult {
        public final String day;
        public final BankStatementReconciliation.Result comparison;
        public final double statementInflowRials;
        public final double statementOutflowRials;
        public final double ledgerInflowRials;
        public final double ledgerOutflowRials;
        public final int statementTransactions;
        public final int ledgerTransactions;

        private DayResult(String day, BankStatementReconciliation.Result comparison,
                          double statementInflowRials, double statementOutflowRials,
                          double ledgerInflowRials, double ledgerOutflowRials,
                          int statementTransactions, int ledgerTransactions) {
            this.day = day;
            this.comparison = comparison;
            this.statementInflowRials = statementInflowRials;
            this.statementOutflowRials = statementOutflowRials;
            this.ledgerInflowRials = ledgerInflowRials;
            this.ledgerOutflowRials = ledgerOutflowRials;
            this.statementTransactions = statementTransactions;
            this.ledgerTransactions = ledgerTransactions;
        }
    }

    public static final class Report {
        public final String fromDay;
        public final String toDay;
        public final boolean statementComplete;
        public final List<DayResult> days;
        public final int matchedDays;
        public final int varianceDays;
        public final int unavailableDays;
        public final int bankOnlyDays;
        public final int ledgerOnlyDays;
        public final double statementInflowRials;
        public final double statementOutflowRials;
        public final double ledgerInflowRials;
        public final double ledgerOutflowRials;
        public final double inflowVarianceRials;
        public final double outflowVarianceRials;
        public final int statementTransactions;
        public final int ledgerTransactions;

        private Report(String fromDay, String toDay, boolean statementComplete,
                       List<DayResult> days, int matchedDays, int varianceDays,
                       int unavailableDays, int bankOnlyDays, int ledgerOnlyDays,
                       double statementInflowRials, double statementOutflowRials,
                       double ledgerInflowRials, double ledgerOutflowRials,
                       double inflowVarianceRials, double outflowVarianceRials,
                       int statementTransactions, int ledgerTransactions) {
            this.fromDay = fromDay;
            this.toDay = toDay;
            this.statementComplete = statementComplete;
            this.days = Collections.unmodifiableList(days);
            this.matchedDays = matchedDays;
            this.varianceDays = varianceDays;
            this.unavailableDays = unavailableDays;
            this.bankOnlyDays = bankOnlyDays;
            this.ledgerOnlyDays = ledgerOnlyDays;
            this.statementInflowRials = statementInflowRials;
            this.statementOutflowRials = statementOutflowRials;
            this.ledgerInflowRials = ledgerInflowRials;
            this.ledgerOutflowRials = ledgerOutflowRials;
            this.inflowVarianceRials = inflowVarianceRials;
            this.outflowVarianceRials = outflowVarianceRials;
            this.statementTransactions = statementTransactions;
            this.ledgerTransactions = ledgerTransactions;
        }

        public boolean fullyComparable() { return unavailableDays == 0; }
        public boolean fullyMatched() { return fullyComparable() && varianceDays == 0; }
    }

    private BankStatementPeriodReconciliation() { }

    public static Report compare(String fromDay, String toDay,
                                 BankStatementFile.Parsed statement,
                                 Map<String, DailyFlow> ledgerByDay,
                                 boolean statementComplete) throws IOException {
        String from = Jalali.disp(fromDay);
        String to = Jalali.disp(toDay);
        int fromNumber = Jalali.parse(from);
        int toNumber = Jalali.parse(to);
        if (fromNumber < 0 || toNumber < fromNumber)
            throw new IOException("بازهٔ مقایسه معتبر نیست");
        int span = toNumber - fromNumber + 1;
        if (span > BankStatementFile.MAX_PERIOD_DAYS)
            throw new IOException("بازهٔ مقایسه بیش از ۳۶۶ روز است");
        if (statement == null) throw new IOException("صورت‌حساب خوانده نشده است");
        if (ledgerByDay == null) throw new IOException("گردش read-only آتیران در دسترس نیست");

        List<DayResult> days = new ArrayList<>(span);
        int matches = 0;
        int variances = 0;
        int unavailable = 0;
        int bankOnly = 0;
        int ledgerOnly = 0;
        double statementIn = 0;
        double statementOut = 0;
        double ledgerIn = 0;
        double ledgerOut = 0;
        int statementCount = 0;
        int ledgerCount = 0;

        for (int offset = 0; offset < span; offset++) {
            String day = Jalali.format(fromNumber + offset);
            BankStatementFile.DailyTotal bank = statement.daily.get(day);
            DailyFlow atiran = ledgerByDay.get(day);
            double bankIn = bank == null ? 0 : bank.creditRials;
            double bankOut = bank == null ? 0 : bank.debitRials;
            double atiranIn = atiran == null ? 0 : atiran.inflowRials;
            double atiranOut = atiran == null ? 0 : atiran.outflowRials;
            int bankTx = bank == null ? 0 : bank.transactions;
            int atiranTx = atiran == null ? 0 : atiran.transactions;
            boolean ledgerDayAvailable = atiran == null || valid(atiran);
            BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                    ledgerDayAvailable, atiranIn, atiranOut,
                    statementComplete, bankIn, bankOut);
            if (result.state == BankStatementReconciliation.State.MATCH) matches++;
            else if (result.state == BankStatementReconciliation.State.VARIANCE) {
                variances++;
                if (bankIn + bankOut > 0 && atiranIn + atiranOut <= 0) bankOnly++;
                if (atiranIn + atiranOut > 0 && bankIn + bankOut <= 0) ledgerOnly++;
            } else unavailable++;
            days.add(new DayResult(day, result, bankIn, bankOut, atiranIn, atiranOut,
                    bankTx, atiranTx));
            statementIn += bankIn;
            statementOut += bankOut;
            ledgerIn += atiranIn;
            ledgerOut += atiranOut;
            statementCount += bankTx;
            ledgerCount += atiranTx;
        }
        double inVariance = statementComplete ? statementIn - ledgerIn : Double.NaN;
        double outVariance = statementComplete ? statementOut - ledgerOut : Double.NaN;
        return new Report(from, to, statementComplete, days, matches, variances,
                unavailable, bankOnly, ledgerOnly, statementIn, statementOut,
                ledgerIn, ledgerOut, inVariance, outVariance, statementCount, ledgerCount);
    }

    private static boolean valid(DailyFlow value) {
        return value != null && finiteNonNegative(value.inflowRials)
                && finiteNonNegative(value.outflowRials) && value.transactions >= 0;
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0d;
    }
}
