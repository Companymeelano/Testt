package ir.meelano.manager.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Optional generative-AI backend for the assistant. Two wire formats cover the
 * providers people actually use: Google Gemini (native REST) and the
 * OpenAI-compatible chat format (OpenAI, xAI Grok, or any compatible server).
 * The key is passed in per call and never stored here.
 */
public final class AiClient {
    private AiClient() { }

    public static final String GEMINI = "gemini";
    public static final String OPENAI = "openai";
    public static final String GROK = "grok";
    public static final String CUSTOM = "custom";

    public static final int TIMEOUT_MS = 45_000;
    private static final int MAX_REPLY = 60_000;

    /** Base URL for the provider (custom uses the value the user typed). */
    public static String baseUrl(String provider, String custom) {
        if (GEMINI.equals(provider)) return "https://generativelanguage.googleapis.com/v1beta";
        if (OPENAI.equals(provider)) return "https://api.openai.com/v1";
        if (GROK.equals(provider)) return "https://api.x.ai/v1";
        return custom == null ? "" : custom.trim();
    }

    /** One completion. Throws with a Persian-friendly message on any failure. */
    public static String complete(String provider, String baseUrl, String model, String key,
                                  String system, String user) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new IllegalStateException("کلید هوش مصنوعی ثبت نشده است");
        if (model == null || model.trim().isEmpty()) throw new IllegalStateException("نام مدل را وارد کنید");
        String base = baseUrl(provider, baseUrl);
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            throw new IllegalStateException("آدرس سرویس معتبر نیست");
        }
        if (GEMINI.equals(provider)) return gemini(base, model.trim(), key.trim(), system, user);
        return openAiCompatible(base, model.trim(), key.trim(), system, user);
    }

    private static String gemini(String base, String model, String key, String system, String user) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", system))));
        body.put("contents", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));
        JSONObject res = post(base + "/models/" + model + ":generateContent", body, "x-goog-api-key", key);
        JSONArray cands = res.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new IllegalStateException("پاسخی از سرویس نرسید");
        JSONArray parts = cands.getJSONObject(0).optJSONObject("content") == null
                ? null : cands.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
        StringBuilder out = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) out.append(parts.getJSONObject(i).optString("text", ""));
        }
        return finish(out.toString());
    }

    private static String openAiCompatible(String base, String model, String key, String system, String user)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user").put("content", user)));
        JSONObject res = post(base + "/chat/completions", body, "Authorization", "Bearer " + key);
        JSONArray choices = res.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("پاسخی از سرویس نرسید");
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        return finish(msg == null ? "" : msg.optString("content", ""));
    }

    private static String finish(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new IllegalStateException("پاسخ خالی بود");
        return t.length() > MAX_REPLY ? t.substring(0, MAX_REPLY) : t;
    }

    private static JSONObject post(String url, JSONObject body, String authHeader, String authValue) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty(authHeader, authValue);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = con.getOutputStream()) {
                os.write(data);
            }
            int code = con.getResponseCode();
            String text = read(code >= 400 ? con.getErrorStream() : con.getInputStream());
            if (code == 401 || code == 403) throw new IllegalStateException("کلید یا دسترسی سرویس معتبر نیست (" + code + ")");
            if (code == 429) throw new IllegalStateException("سقف درخواست سرویس پر شده؛ کمی بعد دوباره تلاش کنید");
            if (code >= 400) throw new IllegalStateException("خطای سرویس هوش مصنوعی (" + code + ")");
            return new JSONObject(text);
        } finally {
            con.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) >= 0) {
                out.write(buf, 0, n);
                if (out.size() > 2_000_000) break;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
