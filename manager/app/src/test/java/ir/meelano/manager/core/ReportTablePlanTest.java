package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportTablePlanTest {

    @Test public void preferredWidthsSurviveWhenTheyFit() {
        double[] w = ReportTablePlan.widths(new double[]{100, 150, 120}, new double[]{40, 50, 45}, 500d);
        assertEquals(100d, w[0], 1e-9);
        assertEquals(150d, w[1], 1e-9);
        assertEquals(120d, w[2], 1e-9);
    }

    @Test public void overWideTablesSqueezeBetweenMinimumAndPreferredAndFillThePage() {
        double[] pref = {200, 200, 200, 200};
        double[] min = {50, 50, 50, 50};
        double[] w = ReportTablePlan.widths(pref, min, 500d);
        double sum = 0;
        for (double v : w) sum += v;
        assertEquals(500d, sum, 1e-6);
        for (int i = 0; i < w.length; i++) {
            assertTrue("column " + i + " below its minimum", w[i] >= 50d - 1e-9);
            assertTrue("column " + i + " above its preferred", w[i] <= 200d + 1e-9);
        }
    }

    @Test public void unequalNeedsKeepTheirProportions() {
        // Slack over each minimum stays proportional to (preferred − minimum):
        // pref−min = {60, 240} ⇒ the hungry column gets 4× the slack of the slim one.
        double[] w = ReportTablePlan.widths(new double[]{100, 300}, new double[]{40, 60}, 300d);
        assertEquals(80d, w[0], 1e-6);
        assertEquals(220d, w[1], 1e-6);
        assertEquals(4d, (w[1] - 60d) / (w[0] - 40d), 1e-6);
    }

    @Test public void impossibleMinimumsAreClampedToPreferredThenSplitProportionally() {
        // Minimums bigger than the preferred width clamp down first, then the available
        // width is split in proportion to those clamped minimums.
        double[] w = ReportTablePlan.widths(new double[]{200, 100}, new double[]{300, 150}, 200d);
        assertEquals(200d * 200d / 300d, w[0], 1e-6);
        assertEquals(100d * 200d / 300d, w[1], 1e-6);
    }

    @Test public void fontLadderWalksDownBeforeCuttingDigits() {
        assertEquals(0, ReportTablePlan.fontStep(80d, 100d));
        assertEquals(1, ReportTablePlan.fontStep(105d, 100d));
        assertEquals(2, ReportTablePlan.fontStep(118d, 100d));
        assertEquals(3, ReportTablePlan.fontStep(140d, 100d));
        assertEquals(3, ReportTablePlan.fontStep(400d, 100d));
        assertEquals(0, ReportTablePlan.fontStep(0d, 10d));
    }

    @Test public void landscapeTriggersOnWideTablesOrHugeContent() {
        assertTrue(ReportTablePlan.landscape(10, 700d, 732d));
        assertTrue(ReportTablePlan.landscape(4, 1200d, 732d));
        assertFalse(ReportTablePlan.landscape(4, 700d, 732d));
        assertFalse(ReportTablePlan.landscape(7, 700d, 732d));
    }
}
