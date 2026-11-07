package ir.meelano.manager.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeTrendWindowTest {
    @Test public void rangeSelectorOffersFocusedWindowsAndKeepsAFullSeriesOption() {
        assertEquals(Arrays.asList(7, 14, 30, HomeTrendWindow.ALL), HomeTrendWindow.options(62));
        assertEquals(Arrays.asList(7, 14, HomeTrendWindow.ALL), HomeTrendWindow.options(30));
        assertEquals(Arrays.asList(7, HomeTrendWindow.ALL), HomeTrendWindow.options(14));
        assertEquals(Arrays.asList(HomeTrendWindow.ALL), HomeTrendWindow.options(7));
    }

    @Test public void defaultShowsRecentFortnightWithoutDroppingTheFullRangeChoice() {
        assertEquals(14, HomeTrendWindow.preferred(62));
        assertEquals(HomeTrendWindow.ALL, HomeTrendWindow.preferred(14));
        assertTrue(HomeTrendWindow.options(62).contains(HomeTrendWindow.ALL));
    }

    @Test public void visibleWindowIsClampedAndDoesNotChangeAvailableData() {
        assertEquals(14, HomeTrendWindow.visibleDays(14, 62));
        assertEquals(62, HomeTrendWindow.visibleDays(HomeTrendWindow.ALL, 62));
        assertEquals(8, HomeTrendWindow.visibleDays(30, 8));
    }

    @Test public void dateLabelsStayEvenAndWithinTheReadableWidthBudget() {
        assertEquals(Arrays.asList(0, 3, 6, 9),
                HomeTrendWindow.dateLabelIndexes(10, 180d, 60d));
        assertEquals(Arrays.asList(0, 8, 16, 24, 29),
                HomeTrendWindow.dateLabelIndexes(30, 240d, 60d));
        assertEquals(Arrays.asList(0, 1), HomeTrendWindow.dateLabelIndexes(2, 40d, 60d));
        assertTrue(HomeTrendWindow.dateLabelIndexes(1, 0d, 0d).contains(0));
    }
}
