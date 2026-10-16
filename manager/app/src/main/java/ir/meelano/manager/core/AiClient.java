package ir.meelano.manager.core;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final String GROQ = "groq";
    public static final String DEEPSEEK = "deepseek";
    public static final String OPENROUTER = "openrouter";
    public static final String GAPGPT = "gapgpt";
    public static final String CEREBRAS = "cerebras";
    public static final String HUGGINGFACE = "huggingface";
    public static final String CUSTOM = "custom";

    /** Display names in the order the chooser shows them. */
    public static final String[][] NAMES = {
            {GEMINI, "Gemini"}, {OPENAI, "OpenAI"}, {GROQ, "Groq"}, {CEREBRAS, "Cerebras"},
            {HUGGINGFACE, "Hugging Face"}, {DEEPSEEK, "DeepSeek"}, {OPENROUTER, "OpenRouter"},
            {GAPGPT, "GapGPT"}, {GROK, "Grok"}, {CUSTOM, "سفارشی"}
    };

    public static String nameOf(String provider) {
        for (String[] n : NAMES) if (n[0].equals(provider)) return n[1];
        return provider == null ? "" : provider;
    }

    /** An HTTP failure from the provider, carrying the status code so routing can react. */
    public static final class ApiError extends IllegalStateException {
        public final int code;

        ApiError(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** Recommended default model per preset (the user may change it in settings). */
    public static String defaultModel(String provider) {
        if (GEMINI.equals(provider)) return "gemini-2.5-flash";
        if (OPENAI.equals(provider)) return "gpt-4o-mini";
        if (GROK.equals(provider)) return "grok-3-mini";
        if (CEREBRAS.equals(provider)) return "llama-3.3-70b";
        if (HUGGINGFACE.equals(provider)) return "meta-llama/Llama-3.3-70B-Instruct";
        if (GROQ.equals(provider)) return "llama-3.3-70b-versatile";
        if (DEEPSEEK.equals(provider)) return "deepseek-chat";
        if (OPENROUTER.equals(provider)) return "openai/gpt-4o-mini";
        if (GAPGPT.equals(provider)) return "gpt-4o-mini";
        return "";
    }

    public static final int TIMEOUT_MS = 45_000;
    private static final int MAX_REPLY = 60_000;

    /** Base URL for the provider (custom uses the value the user typed). */
    public static String baseUrl(String provider, String custom) {
        if (GEMINI.equals(provider)) return "https://generativelanguage.googleapis.com/v1beta";
        if (OPENAI.equals(provider)) return "https://api.openai.com/v1";
        if (GROK.equals(provider)) return "https://api.x.ai/v1";
        if (GROQ.equals(provider)) return "https://api.groq.com/openai/v1";
        if (DEEPSEEK.equals(provider)) return "https://api.deepseek.com/v1";
        if (OPENROUTER.equals(provider)) return "https://openrouter.ai/api/v1";
        if (GAPGPT.equals(provider)) return "https://api.gapgpt.app/v1";
        if (CEREBRAS.equals(provider)) return "https://api.cerebras.ai/v1";
        if (HUGGINGFACE.equals(provider)) return "https://router.huggingface.co/v1";
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

    /** Synthesized speech: bytes plus file extension (wav or mp3). */
    public static final class Audio {
        public final byte[] data;
        public final String ext;

        Audio(byte[] data, String ext) {
            this.data = data;
            this.ext = ext;
        }
    }

    /**
     * Natural voice from the same provider. Gemini returns PCM (wrapped as WAV); OpenAI-compatible
     * servers return MP3. Grok has no speech API here, so it throws.
     */
    public static Audio speech(String provider, String baseUrl, String model, String voice, String key,
                               String text) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new IllegalStateException("کلید هوش مصنوعی ثبت نشده است");
        if (model == null || model.trim().isEmpty()) throw new IllegalStateException("نام مدل صدا را وارد کنید");
        if (GROK.equals(provider)) throw new IllegalStateException("این سرویس صدای طبیعی ندارد");
        String base = baseUrl(provider, baseUrl);
        String v = voice == null ? "" : voice.trim();
        if (GEMINI.equals(provider)) {
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(new JSONObject().put("parts",
                    new JSONArray().put(new JSONObject().put("text", text)))));
            JSONObject voiceCfg = new JSONObject().put("prebuiltVoiceConfig",
                    new JSONObject().put("voiceName", v.isEmpty() ? "Kore" : v));
            body.put("generationConfig", new JSONObject()
                    .put("responseModalities", new JSONArray().put("AUDIO"))
                    .put("speechConfig", new JSONObject().put("voiceConfig", voiceCfg)));
            JSONObject res = post(base + "/models/" + model.trim() + ":generateContent", body,
                    "x-goog-api-key", key.trim());
            JSONArray cands = res.optJSONArray("candidates");
            if (cands == null || cands.length() == 0) throw new IllegalStateException("صدایی از سرویس نرسید");
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject inl = parts.getJSONObject(i).optJSONObject("inlineData");
                    if (inl == null) continue;
                    byte[] pcm = Base64.decode(inl.getString("data"), Base64.DEFAULT);
                    return new Audio(wav(pcm, rateOf(inl.optString("mimeType", ""))), "wav");
                }
            }
            throw new IllegalStateException("صدایی از سرویس نرسید");
        }
        JSONObject body = new JSONObject();
        body.put("model", model.trim());
        body.put("voice", v.isEmpty() ? "alloy" : v);
        body.put("input", text);
        body.put("response_format", "mp3");
        return new Audio(postBinary(base + "/audio/speech", body, "Authorization", "Bearer " + key.trim()), "mp3");
    }

    private static final Pattern RATE = Pattern.compile("rate=(\\d+)");

    private static int rateOf(String mime) {
        Matcher m = RATE.matcher(mime == null ? "" : mime);
        return m.find() ? Integer.parseInt(m.group(1)) : 24000;
    }

    /** 16-bit mono PCM to a WAV file. */
    static byte[] wav(byte[] pcm, int rate) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        b.putInt(36 + pcm.length);
        b.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) 1);
        b.putInt(rate);
        b.putInt(rate * 2);
        b.putShort((short) 2);
        b.putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII));
        b.putInt(pcm.length);
        b.put(pcm);
        return b.array();
    }

    private static byte[] postBinary(String url, JSONObject body, String authHeader, String authValue) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty(authHeader, authValue);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            if (code >= 400) {
                read(con.getErrorStream());
                if (code == 401 || code == 403) throw new ApiError(code, "کلید یا دسترسی سرویس معتبر نیست (" + code + ")");
                throw new ApiError(code, "خطای سرویس صدا (" + code + ")");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = con.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 20_000_000) throw new IllegalStateException("فایل صوتی بیش از حد بزرگ است");
                }
            }
            if (out.size() == 0) throw new IllegalStateException("صدایی از سرویس نرسید");
            return out.toByteArray();
        } finally {
            con.disconnect();
        }
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
            if (code == 401 || code == 403) throw new ApiError(code, "کلید یا دسترسی سرویس معتبر نیست (" + code + ")");
            if (code == 429) throw new ApiError(code, "سقف درخواست سرویس پر شده؛ کمی بعد دوباره تلاش کنید");
            if (code >= 400) throw new ApiError(code, "خطای سرویس هوش مصنوعی (" + code + ")");
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
