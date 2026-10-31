package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HomeRangeTest {
    @Test public void todayYesterdayRollingWeekAndMonthUseInclusiveJalaliDates() {
        assertRange(HomeRange.today("1405/08/15"), "1405/08/15", "1405/08/15", 1);
        assertRange(HomeRange.yesterday("1405/08/15"), "1405/08/14", "1405/08/14", 1);
        assertRange(HomeRange.last7Days("1405/08/15"), "1405/08/09", "1405/08/15", 7);
        assertRange(HomeRange.monthToDate("1405/08/15"), "1405/08/01", "1405/08/15", 15);
    }

    @Test public void priorMonthComparisonUsesMatchingCalendarPeriodAndClampsEsfand() {
        HomeRange prior = HomeRange.previousMonthSamePeriod("1406/01/31");
        int esfandDays = Jalali.daysInMonth(1405, 12);

        assertEquals("1405/12/01", prior.from);
        assertEquals(Jalali.addDays("1405/12/01", esfandDays - 1), prior.to);
        assertEquals(esfandDays, prior.calendarDays());
    }

    @Test public void fullPreviousMonthAndHistoryWindowCoverMonthBoundary() {
        HomeRange previous = HomeRange.previousFullMonth("1405/08/15");
        assertEquals("1405/07/01", previous.from);
        assertEquals("1405/07/30", previous.to);
        assertEquals(30, previous.calendarDays());
        assertEquals("1405/07/01", HomeRange.salesHistoryStart("1405/08/15"));
    }

    @Test public void monthEndForecastUsesElapsedCalendarDays() {
        assertEquals(3_000d, HomeRange.projectMonthEnd(1_000d, "1405/08/10"), 0.001);
        assertEquals(0d, HomeRange.projectMonthEnd(1_000d, "invalid"), 0.001);
    }

    @Test public void aggregateDayFoldsGregorianAndJalaliRowsIntoOneHomeDay() {
        String jalali = "1405/08/09";
        List<Row> daily = new ArrayList<>();
        daily.add(row(jalali, 100, 1));
        daily.add(row(Jalali.toGregorian(jalali), 250, 2));

        Row aggregate = HomeRange.aggregateDay(daily, jalali);

        assertEquals(jalali, aggregate.s("d"));
        assertEquals(350d, aggregate.d("total"), 0.001);
        assertEquals(3, aggregate.l("docs"));
    }

    private static Row row(String date, double total, long docs) {
        Row row = new Row();
        row.put("day", date);
        row.put("total", total);
        row.put("docs", docs);
        return row;
    }

    private static void assertRange(HomeRange range, String from, String to, int days) {
        assertEquals(from, range.from);
        assertEquals(to, range.to);
        assertEquals(days, range.calendarDays());
        assertTrue(range.to.compareTo(range.from) >= 0);
    }
}
