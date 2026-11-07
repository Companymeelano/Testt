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
 * Public market and weather data for the home card. No API key or writes to the store database.
 * Rates: iran-market «popular.json» (TGJU quotes, IRT/toman); the previous valid quote is kept locally.
 * Weather: Open-Meteo current conditions only after an in-app opt-in, Android permission, and a fresh device fix.
 * The local weather cache and opt-in can be cleared from the Home card; no city is guessed or sent by default.
 */
public final class MarketWeather {
    private MarketWeather() { }

    public static final String RATES_URL =
            "https://raw.githubusercontent.com/iran-market/iran-market.github.io/main/data/popular.json";
    /** Open-Meteo forecast for the given coordinates (current conditions, local timezone). */
    public static String weatherUrl(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            throw new IllegalArgumentException("Invalid weather coordinates");
        return String.format(java.util.Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto", lat, lon);
    }

    private static final String PREFS = "market_cache_v1";
    private static final String KEY_RATES = "rates";
    private static final String KEY_PREVIOUS_RATES = "rates_previous";
    private static final String KEY_WEATHER = "weather";
    private static final String KEY_WEATHER_LOCATION_ENABLED = "weather_location_enabled";
    private static final String KEY_WEATHER_PLACE = "weather_place";
    private static final String KEY_WEATHER_DEVICE = "weather_device";

    /** One displayed price: Persian label, toman price, change percent, Jalali timestamp. */
    public static final class Rate {
        public final String label;
        public final long toman;
        public final double changePct;
        public final String asOf;
        public final long previousToman;
        public final boolean comparesWithPreviousFetch;

        Rate(String label, long toman, double changePct, String asOf) {
            this(label, toman, changePct, asOf, 0L, false);
        }

        Rate(String label, long toman, double changePct, String asOf,
             long previousToman, boolean comparesWithPreviousFetch) {
            this.label = label;
            this.toman = toman;
            this.changePct = changePct;
            this.asOf = asOf;
            this.previousToman = previousToman;
            this.comparesWithPreviousFetch = comparesWithPreviousFetch;
        }
    }

    /** Current weather for a fresh, explicitly permissioned device position. */
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

    /** GET a small public JSON document as UTF-8. Response size is bounded before parsing. */
    public static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setUseCaches(false);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "Meelano-Manager/36 market-card");
        try {
            int code = con.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + code);
            int declared = con.getContentLength();
            if (declared > 1024 * 1024) throw new IllegalStateException("Market response is too large");
            try (InputStream in = con.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(1024, declared));
                byte[] buf = new byte[4096];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 1024 * 1024) throw new IllegalStateException("Market response is too large");
                    out.write(buf, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------------ parsing

    /** Requires all three requested TGJU instruments; malformed or stale responses never replace cache. */
    public static List<Rate> parseRates(String json) throws Exception {
        JSONArray data = new JSONObject(json).getJSONArray("data");
        if (data.length() > 500) throw new IllegalStateException("too many market rows");
        List<Rate> out = new ArrayList<>();
        String[][] want = {
                {"USD_IRR_FREE", "دلار آزاد"},
                {"GOLD_18K_IRR", "طلای ۱۸ عیار (گرم)"},
                {"COIN_EMAMI_IRR", "سکه امامی"},
        };
        for (String[] w : want) {
            JSONObject found = null;
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.optJSONObject(i);
                if (o == null || !w[0].equals(o.optString("symbol"))) continue;
                if (found != null) throw new IllegalStateException("duplicate market symbol: " + w[0]);
                found = o;
            }
            if (found == null) throw new IllegalStateException("missing market symbol: " + w[0]);
            if (found.optBoolean("stale", false)) throw new IllegalStateException("stale market quote: " + w[0]);
            if (!"IRT".equalsIgnoreCase(found.optString("currency", "")))
                throw new IllegalStateException("unexpected market currency: " + w[0]);
            double rawPrice = found.optDouble("price", Double.NaN);
            double change = found.optDouble("change_pct", 0d);
            if (!Double.isFinite(rawPrice) || rawPrice <= 0 || rawPrice >= Long.MAX_VALUE
                    || !Double.isFinite(change))
                throw new IllegalStateException("invalid market values: " + w[0]);
            String source = found.optString("source", "");
            if (!"tgju".equalsIgnoreCase(source))
                throw new IllegalStateException("unexpected quote source: " + w[0]);
            out.add(new Rate(w[1], Math.round(rawPrice), change,
                    found.optString("timestamp_jalali", "")));
        }
        return out;
    }

    /** Compare valid fresh quotes with the previous app fetch, falling back to source change_pct. */
    public static List<Rate> parseRates(String json, String previousJson) throws Exception {
        List<Rate> current = parseRates(json);
        if (previousJson == null || previousJson.trim().isEmpty()) return current;
        final List<Rate> previous;
        try {
            previous = parseRates(previousJson);
        } catch (Exception invalidPrevious) {
            return current;
        }
        List<Rate> compared = new ArrayList<>(current.size());
        for (int i = 0; i < current.size(); i++) {
            Rate now = current.get(i);
            Rate old = previous.get(i); // Both parsers enforce the same three-symbol order.
            double pct = ((double) now.toman - (double) old.toman) * 100.0 / old.toman;
            compared.add(new Rate(now.label, now.toman, pct, now.asOf, old.toman, true));
        }
        return compared;
    }

    public static Weather parseWeather(String json) throws Exception {
        JSONObject cur = new JSONObject(json).getJSONObject("current");
        double temp = cur.getDouble("temperature_2m");
        int humidity = cur.optInt("relative_humidity_2m", -1);
        int code = cur.optInt("weather_code", -1);
        if (!Double.isFinite(temp) || temp < -100 || temp > 70 || humidity < 0 || humidity > 100
                || code < 0 || code > 99)
            throw new IllegalStateException("invalid current weather values");
        return new Weather(temp, humidity, code, cur.optString("time", ""));
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
        synchronized (MarketWeather.class) {
            SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            if (KEY_RATES.equals(key)) {
                String previous = prefs.getString(KEY_RATES, null);
                if (previous != null && !previous.isEmpty()) edit.putString(KEY_PREVIOUS_RATES, previous);
            }
            edit.putString(key, raw)
                    .putLong(key + "_saved_at", System.currentTimeMillis())
                    .apply();
        }
    }

    /** Last successful rate response, retained locally as the baseline for the next fetch. */
    public static String previousRates(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREVIOUS_RATES, null);
    }

    /** Weather access is an explicit local opt-in, independent of Android's permission state. */
    public static boolean weatherLocationEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WEATHER_LOCATION_ENABLED, false);
    }

    /** Disable location weather and erase its cached response/place; Android permission remains user-controlled. */
    public static void setWeatherLocationEnabled(Context c, boolean enabled) {
        synchronized (MarketWeather.class) {
            SharedPreferences.Editor edit = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_WEATHER_LOCATION_ENABLED, enabled);
            if (!enabled) {
                edit.remove(KEY_WEATHER)
                        .remove(KEY_WEATHER + "_saved_at")
                        .remove(KEY_WEATHER_PLACE)
                        .remove(KEY_WEATHER_DEVICE);
            }
            edit.apply();
        }
    }

    /** Save a weather response only while the app-level location opt-in remains enabled. */
    public static boolean saveWeather(Context c, String raw, String place) {
        if (raw == null || raw.isEmpty()) return false;
        synchronized (MarketWeather.class) {
            SharedPreferences prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!prefs.getBoolean(KEY_WEATHER_LOCATION_ENABLED, false) || !DeviceLocation.granted(c))
                return false;
            prefs.edit()
                    .putString(KEY_WEATHER, raw)
                    .putLong(KEY_WEATHER + "_saved_at", System.currentTimeMillis())
                    .putString(KEY_WEATHER_PLACE, place == null ? "" : place)
                    .putString(KEY_WEATHER_DEVICE, "1")
                    .apply();
            return true;
        }
    }

    public static String weatherPlace(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WEATHER_PLACE, null);
    }

    public static boolean weatherWasFromDevice(Context c) {
        return "1".equals(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_WEATHER_DEVICE, "0"));
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
