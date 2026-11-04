package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** HTTPS client for the optional, separately deployed PHP AI proxy. */
public final class AiBackendClient {
    private AiBackendClient() { }

    private static final String PREFS = "meelano_ai";
    private static final String ENDPOINT = "ai_backend_endpoint";
    private static final String USER = "ai_backend_user";
    private static final String EXPIRES = "ai_backend_expires";
    private static final String TOKEN = "ai_backend_token";
    private static final String ENABLED = "ai_backend_enabled";
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 65_000;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String savedEndpoint(Context c) {
        return prefs(c).getString(ENDPOINT, "");
    }

    public static String savedUser(Context c) {
        return prefs(c).getString(USER, "");
    }

    public static boolean selected(Context c) {
        return prefs(c).getBoolean(ENABLED, false);
    }

    public static void select(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(ENABLED, enabled).apply();
    }

    /** Saves only public connection metadata; changing the host invalidates the old bearer token. */
    public static void saveEndpoint(Context c, String endpoint, String username) {
        String url = AiEndpoint.normalize(endpoint);
        String user = username == null ? "" : username.trim();
        if (user.length() > 128) throw new IllegalArgumentException("نام کاربری بیش از حد طولانی است");
        SharedPreferences p = prefs(c);
        if (!url.equals(p.getString(ENDPOINT, "")) || !user.equals(p.getString(USER, ""))) clearSession(c);
        p.edit().putString(ENDPOINT, url).putString(USER, user).apply();
    }

    /** True only for a valid HTTPS endpoint, an encrypted token, and an unexpired session. */
    public static boolean hasSession(Context c) {
        try {
            String endpoint = AiEndpoint.normalize(savedEndpoint(c));
            String token = SecretBox.get(c, TOKEN);
            long expires = prefs(c).getLong(EXPIRES, 0L);
            return !endpoint.isEmpty() && !token.isEmpty()
                    && expires > System.currentTimeMillis() + 30_000L;
        } catch (Exception e) {
            return false;
        }
    }

    /** Authenticates over HTTPS; the password is used only for this request and is never saved. */
    public static void login(Context c, String endpoint, String username, String password) throws Exception {
        String url = AiEndpoint.normalize(endpoint);
        String user = username == null ? "" : username.trim();
        if (user.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("نام کاربری و رمز ورود سرور AI را وارد کنید");
        }
        if (user.length() > 128 || password.length() > 512) {
            throw new IllegalArgumentException("نام کاربری یا رمز ورود بیش از حد طولانی است");
        }
        saveEndpoint(c, url, user);
        clearSession(c);
        JSONObject request = new JSONObject();
        request.put("action", "login");
        request.put("user", user);
        request.put("password", password);
        JSONObject response = post(url, null, request);
        String token = response.optString("token", "");
        long expiresIn = response.optLong("expiresIn", 0L);
        if (token.length() < 32 || token.length() > 8192 || expiresIn < 60 || expiresIn > 86_400) {
            throw new IOException("پاسخ ورود سرور AI معتبر نیست");
        }
        if (!SecretBox.put(c, TOKEN, token)) {
            throw new IOException("ذخیرهٔ رمزنگاری‌شدهٔ نشست AI ممکن نشد");
        }
        prefs(c).edit()
                .putString(ENDPOINT, url)
                .putString(USER, user)
                .putLong(EXPIRES, System.currentTimeMillis() + expiresIn * 1000L)
                .apply();
    }

    /** Uses the generic server route for small connection checks and legacy callers. */
    public static String complete(Context c, String system, String message) throws Exception {
        return complete(c, "general", system, message);
    }

    /** Sends one bounded chat request; the PHP service owns provider keys and failover. */
    public static String complete(Context c, String task, String system, String message) throws Exception {
        if (!hasSession(c)) {
            clearSession(c);
            throw new IOException("نشست سرور AI منقضی است؛ دوباره وارد شوید");
        }
        String endpoint = AiEndpoint.normalize(savedEndpoint(c));
        String token = SecretBox.get(c, TOKEN);
        String routeTask = normalizedTask(task);
        if (message == null || message.trim().isEmpty() || message.length() > 24_000) {
            throw new IllegalArgumentException("متن درخواست AI خالی یا بیش از حد طولانی است");
        }
        JSONObject request = new JSONObject();
        request.put("action", "chat");
        request.put("task", routeTask);
        request.put("system", bounded(system, 8_000));
        request.put("message", message);
        try {
            JSONObject response = post(endpoint, token, request);
            String reply = response.optString("reply", "").trim();
            if (reply.isEmpty() || reply.length() > 32_000) {
                throw new IOException("پاسخ سرور AI خالی یا بیش از حد طولانی است");
            }
            return reply;
        } catch (AiBackendException e) {
            if (e.statusCode == 401 || e.statusCode == 403) clearSession(c);
            throw e;
        }
    }

    /** Removes the encrypted bearer token and expiry; no password was persisted. */
    public static void logout(Context c) {
        clearSession(c);
        prefs(c).edit().putBoolean(ENABLED, false).apply();
    }

    private static void clearSession(Context c) {
        SecretBox.put(c, TOKEN, "");
        prefs(c).edit().remove(EXPIRES).apply();
    }

    private static String normalizedTask(String task) {
        String value = task == null ? "" : task.trim().toLowerCase(Locale.ROOT);
        if (!"analysis".equals(value) && !"fast".equals(value)
                && !"writing".equals(value) && !"general".equals(value)) {
            throw new IllegalArgumentException("نوع درخواست هوش مصنوعی معتبر نیست");
        }
        return value;
    }

    private static String bounded(String text, int maxChars) {
        String value = text == null ? "" : text.trim();
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static JSONObject post(String endpoint, String bearer, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (bearer != null && !bearer.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BODY_BYTES) throw new IOException("درخواست AI بیش از حد بزرگ است");
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            byte[] responseBytes = readLimited(stream, MAX_BODY_BYTES);
            if (status < 200 || status >= 300) {
                String message = "درخواست سرور AI ناموفق بود";
                if (status == 401 || status == 403) message = "ورود به سرور AI معتبر نیست؛ دوباره وارد شوید";
                else if (status == 429) message = "محدودیت درخواست سرور AI؛ کمی بعد دوباره تلاش کنید";
                else if (status >= 500) message = "سرویس AI موقتاً پاسخ‌گو نیست";
                throw new AiBackendException(status, message);
            }
            JSONObject result = new JSONObject(new String(responseBytes, StandardCharsets.UTF_8));
            if (!result.optBoolean("ok", false)) {
                throw new IOException("پاسخ سرور AI موفق اعلام نشد");
            }
            return result;
        } catch (AiBackendException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException || e instanceof IOException) throw e;
            throw new IOException("ارتباط امن با سرور AI برقرار نشد؛ نشانی HTTPS و اتصال شبکه را بررسی کنید");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static byte[] readLimited(InputStream stream, int limit) throws IOException {
        if (stream == null) return new byte[0];
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > limit) throw new IOException("پاسخ سرور AI بیش از حد بزرگ است");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static final class AiBackendException extends IOException {
        final int statusCode;
        AiBackendException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
