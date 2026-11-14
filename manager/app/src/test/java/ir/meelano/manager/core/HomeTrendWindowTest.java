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
        // The final label moves to the last point instead of crowding index 24 (gap 5 < interval 8),
        // because two touching date labels at the edge render as one unreadable blob.
        assertEquals(Arrays.asList(0, 8, 16, 29),
                HomeTrendWindow.dateLabelIndexes(30, 240d, 60d));
        assertEquals(Arrays.asList(0, 1), HomeTrendWindow.dateLabelIndexes(2, 40d, 60d));
        assertTrue(HomeTrendWindow.dateLabelIndexes(1, 0d, 0d).contains(0));
    }

    @Test public void dateLabelsNeverCrowdTheLastTwoPointsTogether() {
        // The live two-week dashboard window: 12 and 13 used to sit side by side and collide.
        java.util.List<Integer> picked = HomeTrendWindow.dateLabelIndexes(14, 320d, 52d);
        assertEquals(Integer.valueOf(13), picked.get(picked.size() - 1));
        for (int i = 1; i < picked.size(); i++) {
            assertTrue("labels " + picked + " crowd at index " + i,
                    picked.get(i) - picked.get(i - 1) >= 3);
        }
    }
}
