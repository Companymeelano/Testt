package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test public void invalidOrMissingInputsAreUnavailableRatherThanZero() {
        assertTrue(Double.isNaN(CashCloseMath.expectedClosing(Double.NaN, 0, 0)));
        assertTrue(Double.isNaN(CashCloseMath.variance(1_000, 100, 10, Double.NaN)));
    }
}
