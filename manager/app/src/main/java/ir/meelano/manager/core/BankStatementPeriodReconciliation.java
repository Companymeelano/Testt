package ir.meelano.manager.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact daily, direction-by-direction comparison of a manually supplied bank export with
 * Atiran's read-only bank ledger. This checks flow totals, not balances or individual transactions.
 * Missing statement dates mean zero movement only after explicit confirmation of complete coverage.
 */
public final class BankStatementPeriodReconciliation {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public static final class DailyFlow {
        public final BigDecimal inflowRialsExact;
        public final BigDecimal outflowRialsExact;
        public final double inflowRials;
        public final double outflowRials;
        public final long transactions;

        public DailyFlow(double inflowRials, double outflowRials, int transactions) {
            this(Double.isFinite(inflowRials) ? BigDecimal.valueOf(inflowRials) : null,
                    Double.isFinite(outflowRials) ? BigDecimal.valueOf(outflowRials) : null, transactions);
        }

        public DailyFlow(BigDecimal inflowRials, BigDecimal outflowRials, long transactions) {
            this.inflowRialsExact = inflowRials;
            this.outflowRialsExact = outflowRials;
            this.inflowRials = inflowRials == null ? Double.NaN : inflowRials.doubleValue();
            this.outflowRials = outflowRials == null ? Double.NaN : outflowRials.doubleValue();
            this.transactions = transactions;
        }
    }

    public static final class DayResult {
        public final String day;
        public final BankStatementReconciliation.Result comparison;
        public final BigDecimal statementInflowRialsExact;
        public final BigDecimal statementOutflowRialsExact;
        public final BigDecimal ledgerInflowRialsExact;
        public final BigDecimal ledgerOutflowRialsExact;
        public final double statementInflowRials;
        public final double statementOutflowRials;
        public final double ledgerInflowRials;
        public final double ledgerOutflowRials;
        public final long statementTransactions;
        public final long ledgerTransactions;

        private DayResult(String day, BankStatementReconciliation.Result comparison,
                          BigDecimal statementInflowRials, BigDecimal statementOutflowRials,
                          BigDecimal ledgerInflowRials, BigDecimal ledgerOutflowRials,
                          long statementTransactions, long ledgerTransactions) {
            this.day = day;
            this.comparison = comparison;
            this.statementInflowRialsExact = statementInflowRials;
            this.statementOutflowRialsExact = statementOutflowRials;
            this.ledgerInflowRialsExact = ledgerInflowRials;
            this.ledgerOutflowRialsExact = ledgerOutflowRials;
            this.statementInflowRials = asDouble(statementInflowRials);
            this.statementOutflowRials = asDouble(statementOutflowRials);
            this.ledgerInflowRials = asDouble(ledgerInflowRials);
            this.ledgerOutflowRials = asDouble(ledgerOutflowRials);
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
        public final BigDecimal statementInflowRialsExact;
        public final BigDecimal statementOutflowRialsExact;
        public final BigDecimal ledgerInflowRialsExact;
        public final BigDecimal ledgerOutflowRialsExact;
        public final BigDecimal inflowVarianceRialsExact;
        public final BigDecimal outflowVarianceRialsExact;
        /** Display compatibility only; exact totals above determine all comparisons. */
        public final double statementInflowRials;
        public final double statementOutflowRials;
        public final double ledgerInflowRials;
        public final double ledgerOutflowRials;
        public final double inflowVarianceRials;
        public final double outflowVarianceRials;
        public final long statementTransactions;
        public final long ledgerTransactions;

        private Report(String fromDay, String toDay, boolean statementComplete,
                       List<DayResult> days, int matchedDays, int varianceDays,
                       int unavailableDays, int bankOnlyDays, int ledgerOnlyDays,
                       BigDecimal statementInflowRials, BigDecimal statementOutflowRials,
                       BigDecimal ledgerInflowRials, BigDecimal ledgerOutflowRials,
                       BigDecimal inflowVarianceRials, BigDecimal outflowVarianceRials,
                       long statementTransactions, long ledgerTransactions) {
            this.fromDay = fromDay;
            this.toDay = toDay;
            this.statementComplete = statementComplete;
            this.days = Collections.unmodifiableList(days);
            this.matchedDays = matchedDays;
            this.varianceDays = varianceDays;
            this.unavailableDays = unavailableDays;
            this.bankOnlyDays = bankOnlyDays;
            this.ledgerOnlyDays = ledgerOnlyDays;
            this.statementInflowRialsExact = statementInflowRials;
            this.statementOutflowRialsExact = statementOutflowRials;
            this.ledgerInflowRialsExact = ledgerInflowRials;
            this.ledgerOutflowRialsExact = ledgerOutflowRials;
            this.inflowVarianceRialsExact = inflowVarianceRials;
            this.outflowVarianceRialsExact = outflowVarianceRials;
            this.statementInflowRials = asDouble(statementInflowRials);
            this.statementOutflowRials = asDouble(statementOutflowRials);
            this.ledgerInflowRials = asDouble(ledgerInflowRials);
            this.ledgerOutflowRials = asDouble(ledgerOutflowRials);
            this.inflowVarianceRials = asDouble(inflowVarianceRials);
            this.outflowVarianceRials = asDouble(outflowVarianceRials);
            this.statementTransactions = statementTransactions;
            this.ledgerTransactions = ledgerTransactions;
        }

        public boolean fullyComparable() { return statementComplete && unavailableDays == 0; }
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
        if (statement.invalidRows > 0) throw new IOException("صورت‌حساب ردیف نامعتبر دارد؛ جمع روزانه قابل‌اعتماد نیست");
        if (ledgerByDay == null) throw new IOException("گردش read-only آتیران در دسترس نیست");

        Map<String, DailyFlow> normalizedLedger = new LinkedHashMap<>();
        for (Map.Entry<String, DailyFlow> entry : ledgerByDay.entrySet()) {
            if (entry.getValue() == null) throw new IOException("گردش یک روز از آتیران خالی است؛ صفر فرض نشده است");
            String day = Jalali.disp(entry.getKey());
            int dayNumber = Jalali.parse(day);
            if (dayNumber < fromNumber || dayNumber > toNumber)
                throw new IOException("یک تاریخ از گردش آتیران نامعتبر یا بیرون از بازه است");
            if (normalizedLedger.containsKey(day))
                throw new IOException("گردش آتیران یک روز را چندبار برگرداند؛ از جمع‌کردن مبهم داده خودداری شد");
            normalizedLedger.put(day, entry.getValue());
        }

        List<DayResult> days = new ArrayList<>(span);
        int matches = 0;
        int variances = 0;
        int unavailable = 0;
        int bankOnly = 0;
        int ledgerOnly = 0;
        BigDecimal statementIn = ZERO;
        BigDecimal statementOut = ZERO;
        BigDecimal ledgerIn = ZERO;
        BigDecimal ledgerOut = ZERO;
        long statementCount = 0;
        long ledgerCount = 0;
        boolean totalsKnown = statementComplete;

        for (int offset = 0; offset < span; offset++) {
            String day = Jalali.format(fromNumber + offset);
            BankStatementFile.DailyTotal bank = statement.daily.get(day);
            DailyFlow atiran = normalizedLedger.get(day);
            BigDecimal bankIn = bank == null ? ZERO : bank.creditRialsExact;
            BigDecimal bankOut = bank == null ? ZERO : bank.debitRialsExact;
            BigDecimal atiranIn = atiran == null ? ZERO : atiran.inflowRialsExact;
            BigDecimal atiranOut = atiran == null ? ZERO : atiran.outflowRialsExact;
            long bankTx = bank == null ? 0L : bank.transactions;
            long atiranTx = atiran == null ? 0L : atiran.transactions;
            boolean bankValid = valid(bankIn) && valid(bankOut) && bankTx >= 0;
            boolean ledgerValid = atiran == null || valid(atiran);
            BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                    ledgerValid, atiranIn, atiranOut, statementComplete && bankValid, bankIn, bankOut);
            if (result.state == BankStatementReconciliation.State.MATCH) matches++;
            else if (result.state == BankStatementReconciliation.State.VARIANCE) {
                variances++;
                if (bankIn.add(bankOut).signum() > 0 && atiranIn.add(atiranOut).signum() <= 0) bankOnly++;
                if (atiranIn.add(atiranOut).signum() > 0 && bankIn.add(bankOut).signum() <= 0) ledgerOnly++;
            } else {
                unavailable++;
                totalsKnown = false;
            }
            days.add(new DayResult(day, result, bankValid ? bankIn : null, bankValid ? bankOut : null,
                    ledgerValid ? atiranIn : null, ledgerValid ? atiranOut : null,
                    bankValid ? bankTx : -1, ledgerValid ? atiranTx : -1));
            if (bankValid) {
                statementIn = statementIn.add(bankIn);
                statementOut = statementOut.add(bankOut);
                if (bankTx > Long.MAX_VALUE - statementCount)
                    throw new IOException("تعداد تراکنش‌های فایل از محدودهٔ شمارش عبور کرد");
                statementCount += bankTx;
            }
            if (ledgerValid) {
                ledgerIn = ledgerIn.add(atiranIn);
                ledgerOut = ledgerOut.add(atiranOut);
                if (atiranTx > Long.MAX_VALUE - ledgerCount)
                    throw new IOException("تعداد تراکنش‌های دفتر از محدودهٔ شمارش عبور کرد");
                ledgerCount += atiranTx;
            }
        }
        if (!bounded(statementIn) || !bounded(statementOut) || !bounded(ledgerIn) || !bounded(ledgerOut))
            throw new IOException("جمع دوره از محدودهٔ مبلغ مجاز عبور کرده؛ نتیجه قابل‌اعتماد نیست");
        if (!totalsKnown) {
            statementIn = null;
            statementOut = null;
            ledgerIn = null;
            ledgerOut = null;
        }
        BigDecimal inVariance = totalsKnown ? statementIn.subtract(ledgerIn) : null;
        BigDecimal outVariance = totalsKnown ? statementOut.subtract(ledgerOut) : null;
        return new Report(from, to, statementComplete, days, matches, variances,
                unavailable, bankOnly, ledgerOnly, statementIn, statementOut,
                ledgerIn, ledgerOut, inVariance, outVariance, statementCount, ledgerCount);
    }

    private static boolean valid(DailyFlow value) {
        return value != null && valid(value.inflowRialsExact) && valid(value.outflowRialsExact)
                && value.transactions >= 0
                && !(value.transactions == 0 && (value.inflowRialsExact.signum() > 0 || value.outflowRialsExact.signum() > 0));
    }

    private static boolean valid(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.scale() <= 4
                && value.precision() <= 24 && value.compareTo(BankStatementFile.MAX_AMOUNT_RIALS) <= 0;
    }

    private static boolean bounded(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.scale() <= 4
                && value.precision() <= 24 && value.compareTo(BankStatementFile.MAX_AMOUNT_RIALS) <= 0;
    }

    private static double asDouble(BigDecimal value) {
        return value == null ? Double.NaN : value.doubleValue();
    }
}
