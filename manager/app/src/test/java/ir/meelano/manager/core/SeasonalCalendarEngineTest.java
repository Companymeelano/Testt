package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SeasonalCalendarEngineTest {
    @Test public void jalaliMonthsMapToFourThreeMonthSeasons() {
        String[] expected = {"بهار", "بهار", "بهار", "تابستان", "تابستان", "تابستان",
                "پاییز", "پاییز", "پاییز", "زمستان", "زمستان", "زمستان"};
        for (int month = 1; month <= 12; month++) {
            assertEquals(expected[month - 1], SeasonalCalendarEngine.seasonName(
                    SeasonalCalendarEngine.seasonIndex(month)));
            int season = SeasonalCalendarEngine.seasonIndex(month);
            assertEquals(month, SeasonalCalendarEngine.monthAt(season,
                    month - SeasonalCalendarEngine.firstMonth(season)));
        }
    }

    @Test public void invalidMonthsAndPositionsHaveNoSeason() {
        assertEquals(-1, SeasonalCalendarEngine.seasonIndex(0));
        assertEquals(-1, SeasonalCalendarEngine.seasonIndex(13));
        assertEquals("", SeasonalCalendarEngine.seasonName(-1));
        assertEquals(0, SeasonalCalendarEngine.firstMonth(4));
        assertEquals(0, SeasonalCalendarEngine.monthAt(0, 3));
    }
}
