package ir.meelano.manager.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankStatementReconciliationTest {
    @Test public void exactStatementAndLedgerTotalsMatchByDirection() {
        BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                true, 1_250_000, 340_000, true, 1_250_000, 340_000);
        assertEquals(BankStatementReconciliation.State.MATCH, result.state);
        assertTrue(result.matches());
        assertEquals(0, result.netVarianceRials, 0.0001);
    }

    @Test public void oppositeDirectionErrorsCannotCancelIntoFalseMatch() {
        BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                true, 100, 100, true, 90, 90);
        assertEquals(BankStatementReconciliation.State.VARIANCE, result.state);
        assertEquals(-10, result.inflowVarianceRials, 0.0001);
        assertEquals(-10, result.outflowVarianceRials, 0.0001);
        assertEquals(0, result.netVarianceRials, 0.0001);
        assertFalse(result.matches());
    }

    @Test public void exactDecimalComparisonKeepsSubRialAndLargeValuesDistinct() {
        BigDecimal large = new BigDecimal("9007199254740993");
        BankStatementReconciliation.Result halfRial = BankStatementReconciliation.compare(
                true, large, BigDecimal.ZERO, true, large.add(new BigDecimal("0.50")), BigDecimal.ZERO);
        assertEquals(BankStatementReconciliation.State.MATCH, halfRial.state);
        assertEquals(new BigDecimal("0.50"), halfRial.inflowVarianceRialsExact);

        BankStatementReconciliation.Result overHalf = BankStatementReconciliation.compare(
                true, large, BigDecimal.ZERO, true, large.add(new BigDecimal("0.51")), BigDecimal.ZERO);
        assertEquals(BankStatementReconciliation.State.VARIANCE, overHalf.state);
        assertEquals(new BigDecimal("0.51"), overHalf.inflowVarianceRialsExact);
    }

    @Test public void missingStatementIsNotAssumedToBeZero() {
        BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                true, 100, 25, false, 0, 0);
        assertEquals(BankStatementReconciliation.State.NEEDS_STATEMENT, result.state);
        assertFalse(result.comparable());
        assertTrue(Double.isNaN(result.inflowVarianceRials));
    }

    @Test public void missingDateScopedLedgerIsUnavailable() {
        BankStatementReconciliation.Result result = BankStatementReconciliation.compare(
                false, 0, 0, true, 100, 25);
        assertEquals(BankStatementReconciliation.State.LEDGER_UNAVAILABLE, result.state);
        assertFalse(result.comparable());
    }

    @Test public void toleranceAndInvalidAmountsAreExplicit() {
        assertTrue(BankStatementReconciliation.compare(true, 100, 0, true, 100.49, 0).matches());
        assertFalse(BankStatementReconciliation.compare(true, 100, 0, true, 100.51, 0).matches());
        assertEquals(BankStatementReconciliation.State.INVALID,
                BankStatementReconciliation.compare(true, Double.NaN, 0, true, 0, 0).state);
        assertEquals(BankStatementReconciliation.State.INVALID,
                BankStatementReconciliation.compare(true, 0, -1, true, 0, 0).state);
    }
}
