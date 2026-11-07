package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SalesForecastEngineTest {
    @Test public void forecastsSevenDaysFromFourObservedSameWeekdays() {
        String asOf = "1405/07/30";
        String historyStart = Jalali.addDays(asOf, -28);
        List<Row> rows = new ArrayList<>();
        for (int back = 28; back >= 1; back--) {
            String date = Jalali.addDays(asOf, -back);
            int weekday = Jalali.weekdayIndex(date);
            Row row = new Row();
            row.put("day", date);
            row.put("total", 1_000d + weekday * 100d);
            row.put("docs", 1L);
            rows.add(row);
        }
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, asOf, historyStart);

        SalesForecastEngine.Result result = SalesForecastEngine.forecast(snapshot);

        assertTrue(result.available);
        assertEquals(SalesForecastEngine.HORIZON_DAYS, result.days.size());
        assertEquals(Jalali.addDays(asOf, 1), result.days.get(0).date);
        assertEquals(Jalali.addDays(asOf, 7), result.days.get(6).date);
        for (SalesForecastEngine.DayForecast day : result.days) {
            assertEquals(4, day.sampleCount);
            assertEquals(1_000d + day.weekdayIndex * 100d, day.expectedRials, 0.001d);
            assertEquals(day.expectedRials, day.lowerRials, 0.001d);
            assertEquals(day.expectedRials, day.upperRials, 0.001d);
        }
        assertEquals(sumExpected(result), result.expectedTotalRials, 0.001d);
    }

    @Test public void observedFridaysAreIncludedRatherThanTreatedAsMissingOrHolidayZeroes() {
        String asOf = "1405/07/30";
        String historyStart = Jalali.addDays(asOf, -28);
        List<Row> rows = new ArrayList<>();
        for (int back = 28; back >= 1; back--) {
            String date = Jalali.addDays(asOf, -back);
            if (Jalali.weekdayIndex(date) != 6) continue;
            Row row = new Row();
            row.put("day", date);
            row.put("total", back * 100d);
            row.put("docs", 1L);
            rows.add(row);
        }
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, asOf, historyStart);

        SalesForecastEngine.Result result = SalesForecastEngine.forecast(snapshot);

        assertTrue(result.available);
        for (SalesForecastEngine.DayForecast day : result.days) {
            if (day.weekdayIndex != 6) continue;
            assertEquals(4, day.sampleCount);
            assertTrue(day.expectedRials > 0d);
            assertTrue(day.upperRials > day.lowerRials);
        }
    }

    @Test public void insufficientHistoryDoesNotInventASevenDayForecast() {
        String asOf = "1405/07/30";
        List<Row> rows = new ArrayList<>();
        String date = Jalali.addDays(asOf, -1);
        Row row = new Row();
        row.put("day", date);
        row.put("total", 25_000d);
        row.put("docs", 1L);
        rows.add(row);
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, asOf, date);

        SalesForecastEngine.Result result = SalesForecastEngine.forecast(snapshot);

        assertFalse(result.available);
        assertTrue(result.days.isEmpty());
    }

    private static double sumExpected(SalesForecastEngine.Result result) {
        double total = 0d;
        for (SalesForecastEngine.DayForecast day : result.days) total += day.expectedRials;
        return total;
    }
}
