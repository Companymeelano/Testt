package ir.meelano.manager.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CashWaterfallEngineTest {
    @Test public void waterfallAccumulatesOnlyCashMovements() {
        List<CashWaterfallEngine.Step> steps = CashWaterfallEngine.build(1_000, 500, 250);
        assertEquals(4, steps.size());
        assertEquals(1_000, steps.get(0).endRials, 0.001);
        assertEquals(1_000, steps.get(1).startRials, 0.001);
        assertEquals(1_500, steps.get(1).endRials, 0.001);
        assertEquals(1_500, steps.get(2).startRials, 0.001);
        assertEquals(1_250, steps.get(2).endRials, 0.001);
        assertEquals(1_250, steps.get(3).endRials, 0.001);
        assertTrue(steps.get(0).total);
        assertTrue(!steps.get(1).total && !steps.get(2).total);
        assertTrue(steps.get(3).total);
    }

    @Test public void invalidOrNegativeSourceAmountsProduceNoWaterfall() {
        assertTrue(CashWaterfallEngine.build(Double.NaN, 1, 1).isEmpty());
        assertTrue(CashWaterfallEngine.build(1, Double.POSITIVE_INFINITY, 1).isEmpty());
        assertTrue(CashWaterfallEngine.build(1, 2, -1).isEmpty());
    }
}
