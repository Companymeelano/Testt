package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import ir.meelano.manager.screens.AiSettings;

/**
 * Chooses which generative-AI service answers, with automatic failover.
 * The active service is tried first, then the others in a fixed quality order.
 * A service that is rate-limited (429) is paused for 10 minutes; one that
 * rejects its key or its quota (401/403) is paused for an hour. Only services
 * with a saved key are used, so nothing is sent to a service the user did not set up.
 */
public final class AiRouter {
    private AiRouter() { }

    private static final String PREFS = "meelano_ai";
    private static final long PAUSE_RATE_MS = 10 * 60_000L;
    private static final long PAUSE_AUTH_MS = 60 * 60_000L;

    /** Fallback order after the active service: fast and capable first. */
    private static final String[] PRIORITY = {
            AiClient.GEMINI, AiClient.GROQ, AiClient.CEREBRAS, AiClient.OPENAI, AiClient.DEEPSEEK,
            AiClient.OPENROUTER, AiClient.GAPGPT, AiClient.HUGGINGFACE, AiClient.GROK
    };

    public static final class Result {
        public final String text;
        public final String provider;

        Result(String text, String provider) {
            this.text = text;
            this.provider = provider;
        }
    }

    /** True when at least one service has a key saved. */
    public static boolean anyKey(Context c) {
        for (String p : PRIORITY) {
            if (!AiSettings.keyFor(c, p).isEmpty()) return true;
        }
        return false;
    }

    public static Result complete(Context c, String system, String user) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = p.getString("provider", AiClient.GEMINI);
        List<String> order = new ArrayList<>();
        order.add(active);
        for (String s : PRIORITY) {
            if (!s.equals(active)) order.add(s);
        }
        StringBuilder errs = new StringBuilder();
        long now = System.currentTimeMillis();
        for (String provider : order) {
            String key = AiSettings.keyFor(c, provider);
            if (key.isEmpty()) continue;
            if (p.getLong("cool_" + provider, 0L) > now) {
                errs.append(AiClient.nameOf(provider)).append(": موقتاً کنار گذاشته شده؛ ");
                continue;
            }
            boolean isActive = provider.equals(active);
            String savedModel = p.getString("model", "").trim();
            String model = isActive && !savedModel.isEmpty() ? savedModel : AiClient.defaultModel(provider);
            String base = isActive ? p.getString("baseUrl", "") : "";
            try {
                return new Result(AiClient.complete(provider, base, model, key, system, user), provider);
            } catch (AiClient.ApiError e) {
                if (e.code == 429) {
                    p.edit().putLong("cool_" + provider, now + PAUSE_RATE_MS).apply();
                } else if (e.code == 401 || e.code == 403) {
                    p.edit().putLong("cool_" + provider, now + PAUSE_AUTH_MS).apply();
                }
                errs.append(AiClient.nameOf(provider)).append(": ").append(e.getMessage()).append("؛ ");
            } catch (Exception e) {
                errs.append(AiClient.nameOf(provider)).append(": ").append(e.getMessage()).append("؛ ");
            }
        }
        throw new IllegalStateException(errs.length() == 0
                ? "هیچ کلید هوش مصنوعی ثبت نشده است" : errs.toString());
    }
}
