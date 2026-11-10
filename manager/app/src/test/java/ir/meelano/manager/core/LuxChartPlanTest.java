package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LuxChartPlanTest {

    @Test public void calmSeriesKeepTheirPeakInFrame() {
        LuxChartPlan.Plan p = LuxChartPlan.plan(new double[]{10, 12, 9, 14, 11}, 7);
        assertFalse(p.broken);
        assertEquals(14d, p.peak, 1e-9);
        assertEquals(LuxChartPlan.niceCeil(14d * 1.15d), p.axisMax, 1e-9);
    }

    @Test public void aThreeTimesRunnerUpPeakBreaksTheAxis() {
        // The field data's 4,295,318,029-toman purchase peak vs a ~500m runner-up.
        double[] v = {500, 480, 520, 4295, 510, 495};
        LuxChartPlan.Plan p = LuxChartPlan.plan(v, 7);
        assertTrue(p.broken);
        assertEquals(4295d, p.peak, 1e-9);
        // axis is planned from the runner-up (520), never from the 4295 peak
        assertEquals(LuxChartPlan.niceCeil(520d * 1.15d), p.axisMax, 1e-9);
        assertTrue("axis must sit near the runner-up, not the peak", p.axisMax < p.peak / 2d);
        assertTrue(p.axisMax >= 520d * 1.15d - 1e-9);
    }

    @Test public void movingAverageWarmsUpThenSmooths() {
        double[] ma = LuxChartPlan.movingAverage(new double[]{1, 2, 3, 4, 5}, 3);
        assertTrue(Double.isNaN(ma[0]));
        assertTrue(Double.isNaN(ma[1]));
        assertEquals(2d, ma[2], 1e-9);
        assertEquals(3d, ma[3], 1e-9);
        assertEquals(4d, ma[4], 1e-9);
    }

    @Test public void niceCeilStaysHuman() {
        assertEquals(1d, LuxChartPlan.niceCeil(0.9d), 1e-9);
        assertEquals(2d, LuxChartPlan.niceCeil(1.4d), 1e-9);
        assertEquals(2.5d, LuxChartPlan.niceCeil(2.2d), 1e-9);
        assertEquals(5d, LuxChartPlan.niceCeil(4.4d), 1e-9);
        assertEquals(10d, LuxChartPlan.niceCeil(9.9d), 1e-9);
        assertEquals(20d, LuxChartPlan.niceCeil(16d), 1e-9);
        assertEquals(0d, LuxChartPlan.niceCeil(0d), 1e-9);
    }

    @Test public void weekdayBaselineAveragesTheSameSlot() {
        double[] v = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        // index 0 and 7 share weekday slot 0 → (1+8)/2
        assertEquals(4.5d, LuxChartPlan.weekdayAverage(v, 0), 1e-9);
        assertEquals(4.5d, LuxChartPlan.weekdayAverage(v, 7), 1e-9);
        // index 1 and 8 share weekday slot 1 → (2+9)/2
        assertEquals(5.5d, LuxChartPlan.weekdayAverage(v, 1), 1e-9);
    }
}
