package ir.meelano.manager.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DashboardKpiLabelTest {
    @Test public void dashboardComparisonBadgeKeepsTheNumberAndDropsOnlyItsVisualBasis() {
        assertEquals("۱۰۰٪", Kit.compactDashboardDelta("۱۰۰٪ · نسبت به روز قبل"));
        assertEquals("شروع از صفر", Kit.compactDashboardDelta("شروع از صفر · نسبت به ۳۰ روز قبل"));
        assertEquals("۰٪", Kit.compactDashboardDelta("۰٪"));
    }

    @Test public void dashboardComparisonBadgeHandlesNullOrBlankWithoutInventingAValue() {
        assertEquals("", Kit.compactDashboardDelta(null));
        assertEquals("", Kit.compactDashboardDelta("  "));
    }
}
