package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlertEngineTest {
    @Test public void stockOutIsCriticalAndLowStockRequiresAKnownPositiveReorderPoint() {
        List<AlertEngine.StockItem> stock = Arrays.asList(
                new AlertEngine.StockItem("1", "ناموجود", 0, 5, true),
                new AlertEngine.StockItem("2", "کم", 2, 5, true),
                new AlertEngine.StockItem("3", "نقطه سفارش نامعلوم", 1, 0, false),
                new AlertEngine.StockItem("4", "نقطه سفارش صفر", 1, 0, true),
                new AlertEngine.StockItem("5", "موجودی کافی", 9, 8, true));

        List<AlertEngine.Alert> alerts = AlertEngine.evaluate(stock, null, null, 20, null);

        assertEquals(2, alerts.size());
        assertEquals("stock_out", alerts.get(0).code);
        assertEquals(AlertEngine.Severity.CRITICAL, alerts.get(0).severity);
        assertEquals("stock_low", alerts.get(1).code);
        assertEquals("2", alerts.get(1).actionValue);
        assertEquals(AlertEngine.Action.PRODUCT_FILE, alerts.get(1).action);
    }

    @Test public void salesDropUsesProjectedSameWeekdayBaselineAndConfiguredThreshold() {
        String today = "1405/08/09";
        SalesComparison.DayResult fullDay = comparison(today, 70, SalesComparison.MINUTES_PER_DAY);
        List<AlertEngine.Alert> alerts = AlertEngine.evaluate(null, null, fullDay, 20, null);

        assertEquals(1, alerts.size());
        assertEquals("sales_drop", alerts.get(0).code);
        assertEquals(-30.0, alerts.get(0).changePercent, 0.001);
        assertEquals(70.0, alerts.get(0).value, 0.001);
        assertEquals(100.0, alerts.get(0).referenceValue, 0.001);
        assertEquals(AlertEngine.Action.SALES, alerts.get(0).action);
        assertTrue(AlertEngine.evaluate(null, null, fullDay, 0, null).isEmpty());

        SalesComparison.DayResult partialButNotAbnormallyLow = comparison(today, 45, 720);
        assertTrue(AlertEngine.evaluate(null, null, partialButNotAbnormallyLow, 20, null).isEmpty());
        SalesComparison.DayResult unavailable = SalesComparison.evaluate(
                SalesSnapshot.empty(today), today, 720, SalesComparison.DEFAULT_COMPARABLE_WEEKS);
        assertTrue(AlertEngine.evaluate(null, null, unavailable, 20, null).isEmpty());
    }

    @Test public void salesDropIsNotInferredWithoutAComparableWeekdayBaseline() {
        String today = "1405/08/09";
        List<Row> rows = Collections.singletonList(day(today, 0));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, today, today);
        SalesComparison.DayResult result = SalesComparison.evaluate(snapshot, today,
                SalesComparison.MINUTES_PER_DAY, SalesComparison.DEFAULT_COMPARABLE_WEEKS);

        assertFalse(result.hasBaseline());
        assertTrue(AlertEngine.evaluate(null, null, result, 20, null).isEmpty());
    }

    @Test public void purchasePriceDriftUsesAbsoluteFifteenPercentFromLastTwoPurchases() {
        List<AlertEngine.PriceChange> changes = Arrays.asList(
                new AlertEngine.PriceChange("افزایش", 100, 115),
                new AlertEngine.PriceChange("کاهش", 100, 85),
                new AlertEngine.PriceChange("کمتر از آستانه", 100, 114.99),
                new AlertEngine.PriceChange("قیمت نامعتبر", 0, 150));

        List<AlertEngine.Alert> alerts = AlertEngine.evaluate(null, changes, null, 20, null);

        assertEquals(2, alerts.size());
        assertEquals("purchase_price_drift", alerts.get(0).code);
        assertEquals("افزایش", alerts.get(0).subject);
        assertEquals(15.0, alerts.get(0).changePercent, 0.001);
        assertEquals(-15.0, alerts.get(1).changePercent, 0.001);
        assertEquals(AlertEngine.Action.PURCHASES, alerts.get(0).action);
    }

    @Test public void dueAlertsOnlyIncludeAvailableDueRecordsWithinTheThreeDayWindow() {
        List<AlertEngine.DueItem> due = Arrays.asList(
                new AlertEngine.DueItem(true, 0, 20),
                new AlertEngine.DueItem(true, 3, 30),
                new AlertEngine.DueItem(false, 3, 40),
                new AlertEngine.DueItem(false, 4, 100),
                new AlertEngine.DueItem(false, -1, 500));

        List<AlertEngine.Alert> alerts = AlertEngine.evaluate(null, null, null, 20, due);

        assertEquals(2, alerts.size());
        assertEquals("cheque_out_due", alerts.get(0).code);
        assertEquals(AlertEngine.Severity.WARNING, alerts.get(0).severity);
        assertEquals(1, alerts.get(0).count);
        assertEquals(40, alerts.get(0).value, 0.001);
        assertEquals(3, alerts.get(0).daysUntilDue);
        assertEquals("cheque_in_due", alerts.get(1).code);
        assertEquals(AlertEngine.Severity.INFO, alerts.get(1).severity);
        assertEquals(2, alerts.get(1).count);
        assertEquals(50, alerts.get(1).value, 0.001);
        assertEquals(0, alerts.get(1).daysUntilDue);
        assertTrue(AlertEngine.evaluate(null, null, null, 20, new ArrayList<>()).isEmpty());
        assertTrue(AlertEngine.evaluate(null, null, null, 20, null).isEmpty());
    }

    private static SalesComparison.DayResult comparison(String today, double todaySales, int minuteOfDay) {
        List<Row> rows = new ArrayList<>();
        for (int weeks = 1; weeks <= SalesComparison.DEFAULT_COMPARABLE_WEEKS; weeks++) {
            rows.add(day(Jalali.addDays(today, -7 * weeks), 100));
        }
        rows.add(day(today, todaySales));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, today,
                Jalali.addDays(today, -7 * SalesComparison.DEFAULT_COMPARABLE_WEEKS));
        return SalesComparison.evaluate(snapshot, today, minuteOfDay,
                SalesComparison.DEFAULT_COMPARABLE_WEEKS);
    }

    private static Row day(String date, double total) {
        Row row = new Row();
        row.put("day", date);
        row.put("total", total);
        row.put("docs", total > 0 ? 1 : 0);
        return row;
    }
}
