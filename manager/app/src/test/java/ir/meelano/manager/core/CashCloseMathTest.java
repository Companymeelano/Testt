package ir.meelano.manager.core;

import java.math.BigDecimal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CashCloseMathTest {
    @Test public void expectedTillUsesOnlyCashReceiptsAndPayments() {
        assertEquals(1_250, CashCloseMath.expectedClosing(1_000, 500, 250), 0.001);
    }

    @Test public void varianceKeepsDirectionAndZeroIsAValidCount() {
        assertEquals(50, CashCloseMath.variance(1_000, 500, 250, 1_300), 0.001);
        assertEquals(-25, CashCloseMath.variance(1_000, 500, 250, 1_225), 0.001);
        assertEquals(-1_250, CashCloseMath.variance(1_000, 500, 250, 0), 0.001);
    }

    @Test public void exactCashClosePreservesLargeFractionalRialsAndRejectsMissingInputs() {
        BigDecimal opening = new BigDecimal("9007199254740993.50");
        BigDecimal receipts = new BigDecimal("100.25");
        BigDecimal payments = new BigDecimal("50.10");
        BigDecimal counted = new BigDecimal("9007199254741043.64");
        assertEquals(new BigDecimal("9007199254741043.65"),
                CashCloseMath.expectedClosingExact(opening, receipts, payments));
        assertEquals(new BigDecimal("-0.01"),
                CashCloseMath.varianceExact(opening, receipts, payments, counted));
        assertNull(CashCloseMath.expectedClosingExact(opening, null, payments));
        assertNull(CashCloseMath.varianceExact(opening, receipts, payments, null));
    }

    @Test public void invalidOrMissingInputsAreUnavailableRatherThanZero() {
        assertTrue(Double.isNaN(CashCloseMath.expectedClosing(Double.NaN, 0, 0)));
        assertTrue(Double.isNaN(CashCloseMath.variance(1_000, 100, 10, Double.NaN)));
    }
}
