package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SalesComparisonTest {
    @Test public void runRateUsesSameWeekdayAndFullCalendarDay() {
        String today = "1405/08/15";
        List<Row> rows = new ArrayList<>();
        rows.add(row(Jalali.addDays(today, -7), 100, 1));
        rows.add(row(Jalali.addDays(today, -14), 200, 1));
        rows.add(row(Jalali.addDays(today, -21), 300, 1));
        rows.add(row(Jalali.addDays(today, -28), 400, 1));
        rows.add(row(Jalali.addDays(today, -1), 900, 1)); // not a same-weekday sample
        rows.add(row(today, 600, 1));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, today, "1405/07/01");

        SalesComparison.DayResult result = SalesComparison.evaluate(snapshot, today, 12 * 60, 4);

        assertTrue(result.available);
        assertFalse(result.complete);
        assertTrue(result.projectionAvailable);
        assertEquals(600d, result.actualRials, 0.001);
        assertEquals(1_200d, result.projectedRials, 0.001);
        assertEquals(250d, result.sameWeekdayAverageRials, 0.001);
        assertEquals(4, result.comparableDays);
        assertEquals(380d, result.changePercent(), 0.001);
    }

    @Test public void missingTransactionsCountAsZeroWithinTheLoadedHistoryWindow() {
        String today = "1405/08/15";
        List<Row> rows = new ArrayList<>();
        rows.add(row(Jalali.addDays(today, -7), 100, 1));
        rows.add(row(Jalali.addDays(today, -21), 300, 1));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, today, "1405/07/01");

        SalesComparison.DayResult result = SalesComparison.evaluate(snapshot, today, 1_440, 4);

        assertTrue(result.complete);
        assertEquals(100d, result.sameWeekdayAverageRials, 0.001);
        assertEquals(4, result.comparableDays);
        assertEquals(0d, result.projectedRials, 0.001);
        assertEquals(-100d, result.changePercent(), 0.001);
    }

    @Test public void midnightHasNoProjectionAndCompletedDayEqualsActual() {
        String today = "1405/08/15";
        List<Row> rows = new ArrayList<>();
        rows.add(row(today, 1_000, 1));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, today, "1405/07/01");

        SalesComparison.DayResult midnight = SalesComparison.evaluate(snapshot, today, 0, 4);
        SalesComparison.DayResult complete = SalesComparison.evaluate(snapshot, today, 1_440, 4);

        assertFalse(midnight.projectionAvailable);
        assertTrue(Double.isNaN(midnight.changePercent()));
        assertTrue(complete.complete);
        assertEquals(1_000d, complete.projectedRials, 0.001);
    }

    @Test public void onlyFridayIsClassifiedAsWeeklyHoliday() {
        String firstOfMonth = "1405/08/01";
        int toFriday = (6 - Jalali.weekdayIndex(firstOfMonth) + 7) % 7;
        String friday = Jalali.addDays(firstOfMonth, toFriday);
        String nextDay = Jalali.addDays(friday, 1);

        assertTrue(SalesComparison.isWeeklyHoliday(friday));
        assertFalse(SalesComparison.isWeeklyHoliday(nextDay));
        assertEquals(6, Jalali.weekdayIndex(friday));
    }

    private static Row row(String date, double amount, long docs) {
        Row row = new Row();
        row.put("day", date);
        row.put("total", amount);
        row.put("docs", docs);
        return row;
    }
}
