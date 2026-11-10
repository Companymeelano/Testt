package ir.meelano.manager.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarketWeatherTest {
    private static String rates(String usd, String gold, String coin, String stale) {
        return "{\"data\":["
                + "{\"symbol\":\"USD_IRR_FREE\",\"price\":" + usd + ",\"currency\":\"IRT\",\"source\":\"tgju\",\"stale\":" + stale + ",\"change_pct\":1.2,\"timestamp_jalali\":\"1405/08/06 12:00\"},"
                + "{\"symbol\":\"GOLD_18K_IRR\",\"price\":" + gold + ",\"currency\":\"IRT\",\"source\":\"tgju\",\"stale\":false,\"change_pct\":-0.5},"
                + "{\"symbol\":\"COIN_EMAMI_IRR\",\"price\":" + coin + ",\"currency\":\"IRT\",\"source\":\"tgju\",\"stale\":false,\"change_pct\":0}]}";
    }

    @Test public void acceptsAllThreeFreshTomanQuotesInRequestedOrder() throws Exception {
        List<MarketWeather.Rate> rows = MarketWeather.parseRates(rates("269500", "26431500", "267675000", "false"));
        assertEquals(3, rows.size());
        assertEquals("دلار آزاد", rows.get(0).label);
        assertEquals(269500L, rows.get(0).toman);
        assertEquals("طلای ۱۸ عیار (گرم)", rows.get(1).label);
        assertEquals("سکه امامی", rows.get(2).label);
        assertEquals(-0.5d, rows.get(1).changePct, 0.0001d);
    }

    @Test public void rejectsStaleMissingWrongCurrencyAndInvalidPrices() {
        assertThrows(() -> MarketWeather.parseRates(rates("269500", "26431500", "267675000", "true")));
        assertThrows(() -> MarketWeather.parseRates(rates("0", "26431500", "267675000", "false")));
        assertThrows(() -> MarketWeather.parseRates(rates("269500", "26431500", "null", "false")));
        assertThrows(() -> MarketWeather.parseRates("{\"data\":[{\"symbol\":\"USD_IRR_FREE\",\"price\":1,\"currency\":\"IRR\",\"source\":\"tgju\"}]}"));
    }

    @Test public void parsesWeatherOnlyWhenValuesArePhysicallyPlausible() throws Exception {
        MarketWeather.Weather w = MarketWeather.parseWeather(
                "{\"current\":{\"temperature_2m\":31.5,\"relative_humidity_2m\":42,\"weather_code\":3,\"time\":\"2026-10-28T14:00\"}}");
        assertEquals(31.5d, w.temp, 0.001d);
        assertEquals(42, w.humidity);
        assertEquals("ابری", MarketWeather.describe(w.code));
        assertThrows(() -> MarketWeather.parseWeather(
                "{\"current\":{\"temperature_2m\":31,\"relative_humidity_2m\":140,\"weather_code\":3}}"));
        assertThrows(() -> MarketWeather.parseWeather(
                "{\"current\":{\"temperature_2m\":1000,\"relative_humidity_2m\":20,\"weather_code\":3}}"));
    }

    @Test public void weatherUrlRejectsImpossibleCoordinates() {
        assertTrue(MarketWeather.weatherUrl(31.3183, 48.6706).contains("latitude=31.3183"));
        assertThrows(() -> MarketWeather.weatherUrl(Double.NaN, 0));
        assertThrows(() -> MarketWeather.weatherUrl(91, 0));
        assertThrows(() -> MarketWeather.weatherUrl(0, 181));
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static void assertThrows(ThrowingRunnable action) {
        boolean threw = false;
        try { action.run(); } catch (Exception expected) { threw = true; }
        assertTrue("expected validation to reject invalid source data", threw);
    }
}
