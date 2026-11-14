package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonthlyGoalEngineTest {
    @Test public void calendarDayRunRateProjectsToMonthEnd() {
        MonthlyGoalEngine.Result result = MonthlyGoalEngine.evaluate(
                600d, 1_200d, 15, 30);

        assertTrue(result.available);
        assertEquals(50d, result.achievementPercent, 0.0001d);
        assertEquals(50d, result.gaugePercent, 0.0001d);
        assertEquals(1_200d, result.projectedMonthRials, 0.0001d);
        assertEquals(100d, result.projectedPercent, 0.0001d);
        assertEquals(15, result.elapsedCalendarDays);
        assertEquals(30, result.monthCalendarDays);
    }

    @Test public void gaugeClampsButAchievementRemainsExactAboveTarget() {
        MonthlyGoalEngine.Result result = MonthlyGoalEngine.evaluate(
                1_250d, 1_000d, 10, 31);

        assertTrue(result.available);
        assertEquals(125d, result.achievementPercent, 0.0001d);
        assertEquals(100d, result.gaugePercent, 0.0001d);
        assertEquals(387.5d, result.projectedPercent, 0.0001d);
    }

    @Test public void negativeNetSalesAreNotRewrittenByTheGoalEngine() {
        MonthlyGoalEngine.Result result = MonthlyGoalEngine.evaluate(
                -100d, 1_000d, 2, 30);

        assertTrue(result.available);
        assertEquals(-100d, result.salesRials, 0.0001d);
        assertEquals(-10d, result.achievementPercent, 0.0001d);
        assertEquals(-1_500d, result.projectedMonthRials, 0.0001d);
        assertEquals(0d, result.gaugePercent, 0.0001d);
    }

    @Test public void zeroOrInvalidTargetsAndCalendarRangesAreUnavailable() {
        assertFalse(MonthlyGoalEngine.evaluate(100d, 0d, 1, 30).available);
        assertFalse(MonthlyGoalEngine.evaluate(100d, -1d, 1, 30).available);
        assertFalse(MonthlyGoalEngine.evaluate(100d, 500d, 0, 30).available);
        assertFalse(MonthlyGoalEngine.evaluate(100d, 500d, 31, 30).available);
        assertFalse(MonthlyGoalEngine.evaluate(Double.NaN, 500d, 1, 30).available);
    }
}
