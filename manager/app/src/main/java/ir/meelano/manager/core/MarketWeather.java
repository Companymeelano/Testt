package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Public market and weather data for the home card. No API key, no writes.
 * Rates: iran-market «popular.json» (TGJU prices, toman, refreshed every 30 min).
 * Weather: Open-Meteo current conditions for the device location, falling back to the store in Ahvaz.
 * The last good response of each is cached so the card still shows values offline.
 */
public final class MarketWeather {
    private MarketWeather() { }

    public static final String RATES_URL =
            "https://raw.githubusercontent.com/iran-market/iran-market.github.io/main/data/popular.json";
    /** Fallback when the device position is unavailable: Ahvaz (اهواز). */
    public static final double AHVAZ_LAT = 31.3183;
    public static final double AHVAZ_LON = 48.6706;
    public static final String AHVAZ_NAME = "اهواز";

    /** Open-Meteo forecast for the given coordinates (current conditions, local timezone). */
    public static String weatherUrl(double lat, double lon) {
        return String.format(java.util.Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto", lat, lon);
    }

    private static final String PREFS = "market_cache_v1";
    private static final String KEY_RATES = "rates";
    private static final String KEY_WEATHER = "weather";

    /** One displayed price: Persian label, toman price, change percent, Jalali timestamp. */
    public static final class Rate {
        public final String label;
        public final long toman;
        public final double changePct;
        public final String asOf;

        Rate(String label, long toman, double changePct, String asOf) {
            this.label = label;
            this.toman = toman;
            this.changePct = changePct;
            this.asOf = asOf;
        }
    }

    /** Current Tehran weather. */
    public static final class Weather {
        public final double temp;
        public final int humidity;
        public final int code;
        public final String asOf;

        Weather(double temp, int humidity, int code, String asOf) {
            this.temp = temp;
            this.humidity = humidity;
            this.code = code;
            this.asOf = asOf;
        }
    }

    // ------------------------------------------------------------------ network

    /** GET a URL as UTF-8 text with short timeouts. Throws on any HTTP or network error. */
    public static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("Accept", "application/json");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------------ parsing

    /** Picks the four cards we show from popular.json. Unknown symbols are ignored. */
    public static List<Rate> parseRates(String json) throws Exception {
        JSONArray data = new JSONObject(json).getJSONArray("data");
        List<Rate> out = new ArrayList<>();
        String[][] want = {
                {"USD_IRR_FREE", "دلار آزاد"},
                {"GOLD_18K_IRR", "طلای ۱۸ عیار (گرم)"},
                {"COIN_EMAMI_IRR", "سکه امامی"},
        };
        for (String[] w : want) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.getJSONObject(i);
                if (!w[0].equals(o.optString("symbol"))) continue;
                out.add(new Rate(w[1], Math.round(o.getDouble("price")),
                        o.optDouble("change_pct", 0), o.optString("timestamp_jalali", "")));
                break;
            }
        }
        if (out.isEmpty()) throw new IllegalStateException("no rates");
        return out;
    }

    public static Weather parseWeather(String json) throws Exception {
        JSONObject cur = new JSONObject(json).getJSONObject("current");
        return new Weather(cur.getDouble("temperature_2m"), cur.optInt("relative_humidity_2m", 0),
                cur.optInt("weather_code", 0), cur.optString("time", ""));
    }

    /** Persian description of a WMO weather code (Open-Meteo). */
    public static String describe(int code) {
        if (code == 0) return "آفتابی";
        if (code == 1) return "کم‌ابری";
        if (code == 2) return "پاره‌ابری";
        if (code == 3) return "ابری";
        if (code == 45 || code == 48) return "مه";
        if (code >= 51 && code <= 57) return "نم‌نم باران";
        if (code >= 61 && code <= 67) return "باران";
        if (code >= 71 && code <= 77) return "برف";
        if (code >= 80 && code <= 82) return "رگبار";
        if (code >= 85 && code <= 86) return "بارش برف";
        if (code >= 95) return "رعد و برق";
        return "—";
    }

    public static String glyph(int code) {
        if (code == 0 || code == 1) return "☀";
        if (code == 2 || code == 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if (code >= 95) return "⚡";
        if (code >= 71 && code <= 86) return "❄";
        return "☂";
    }

    // ------------------------------------------------------------------ cache

    public static void save(Context c, String key, String raw) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, raw)
                .putLong(key + "_saved_at", System.currentTimeMillis())
                .apply();
    }

    /** Last saved raw response for key, or null. */
    public static String cached(Context c, String key) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString(key, null);
    }

    /** Local wall-clock time of the last successful response save; zero means an old cache without a timestamp. */
    public static long savedAt(Context c, String key) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(key + "_saved_at", 0L);
    }

    public static String ratesKey() { return KEY_RATES; }
    public static String weatherKey() { return KEY_WEATHER; }
}
