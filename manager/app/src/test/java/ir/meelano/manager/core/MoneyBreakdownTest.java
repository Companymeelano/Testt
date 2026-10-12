package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MoneyBreakdownTest {
    @Test public void exactComponentsMatchBookedTotal() {
        MoneyBreakdown b = MoneyBreakdown.calculate(true, 12500, 2500, 3000, 4000, 3000);
        assertTrue(b.available);
        assertEquals(12500, b.componentTotal, 0.0001);
        assertEquals(0, b.unexplained, 0.0001);
        assertTrue(b.matches());
    }

    @Test public void positiveGapIsNotSilentlyClassified() {
        MoneyBreakdown b = MoneyBreakdown.calculate(true, 1000, 200, 200, 200, 200);
        assertEquals(200, b.unexplained, 0.0001);
        assertFalse(b.matches());
    }

    @Test public void componentExcessKeepsItsSign() {
        MoneyBreakdown b = MoneyBreakdown.calculate(true, 1000, 500, 300, 200, 100);
        assertEquals(-100, b.unexplained, 0.0001);
        assertFalse(b.matches());
    }

    @Test public void subRialConversionNoiseIsTolerated() {
        assertTrue(MoneyBreakdown.calculate(true, 1000.49, 1000, 0, 0, 0).matches());
        assertFalse(MoneyBreakdown.calculate(true, 1000.51, 1000, 0, 0, 0).matches());
    }

    @Test public void unavailableOrNonFiniteSchemaDoesNotProduceAZeroReconciliation() {
        MoneyBreakdown unavailable = MoneyBreakdown.calculate(false, 100, 0, 0, 0, 0);
        assertFalse(unavailable.available);
        assertFalse(unavailable.matches());
        assertTrue(Double.isNaN(unavailable.unexplained));

        MoneyBreakdown invalid = MoneyBreakdown.calculate(true, Double.NaN, 0, 0, 0, 0);
        assertFalse(invalid.available);
        assertFalse(invalid.matches());
    }
}
