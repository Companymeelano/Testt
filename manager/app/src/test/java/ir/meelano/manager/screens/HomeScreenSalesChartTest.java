package ir.meelano.manager.screens;

import ir.meelano.manager.core.SalesSnapshot;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeScreenSalesChartTest {
    @Test public void latestLoadedDayIsTheDefaultChartSelection() {
        SalesSnapshot.Day first = new SalesSnapshot.Day("1405/08/11", 100d, 1);
        SalesSnapshot.Day latest = new SalesSnapshot.Day("1405/08/13", 0d, 0);

        assertEquals(latest.date, HomeScreen.latestSalesChartDate(Arrays.asList(first, latest)));
    }

    @Test public void emptySalesAreDifferentFromUnavailableHistory() {
        String window = "۷ روز";
        assertEquals("در این بازه فاکتوری ثبت نشده است · ۷ روز",
                HomeScreen.salesChartEmptyState(Arrays.asList(
                        new SalesSnapshot.Day("1405/08/12", 0d, 0),
                        new SalesSnapshot.Day("1405/08/13", 0d, 0)), window));
        assertEquals("تاریخچهٔ فروش برای این بازه در دسترس نیست",
                HomeScreen.salesChartEmptyState(Collections.<SalesSnapshot.Day>emptyList(), window));
        assertEquals("", HomeScreen.salesChartEmptyState(Arrays.asList(
                new SalesSnapshot.Day("1405/08/13", 100d, 1)), window));
        assertFalse(HomeScreen.salesSeriesHasActivity(Arrays.asList(
                new SalesSnapshot.Day("1405/08/13", 0d, 0))));
        assertTrue(HomeScreen.salesSeriesHasActivity(Arrays.asList(
                new SalesSnapshot.Day("1405/08/13", 0d, 1))));
    }
}
