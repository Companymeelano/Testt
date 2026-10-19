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

    /** Per-capability preference: the first service with a saved key handles the task. */
    private static final String[] ANALYSIS_ORDER = {
            AiClient.DEEPSEEK, AiClient.GEMINI, AiClient.OPENAI, AiClient.OPENROUTER,
            AiClient.GAPGPT, AiClient.GROQ, AiClient.CEREBRAS, AiClient.HUGGINGFACE, AiClient.GROK};
    private static final String[] FAST_ORDER = {
            AiClient.GROQ, AiClient.CEREBRAS, AiClient.GEMINI, AiClient.OPENAI, AiClient.DEEPSEEK,
            AiClient.OPENROUTER, AiClient.GAPGPT, AiClient.HUGGINGFACE, AiClient.GROK};
    private static final String[] WRITING_ORDER = {
            AiClient.GEMINI, AiClient.OPENAI, AiClient.GAPGPT, AiClient.OPENROUTER,
            AiClient.DEEPSEEK, AiClient.GROQ, AiClient.CEREBRAS, AiClient.HUGGINGFACE, AiClient.GROK};

    /** Picks the capability from the question: numbers and reasoning, writing, or a quick answer. */
    public static String taskFor(String question) {
        String q = question == null ? "" : question.replace('ي', 'ی').replace('ك', 'ک');
        String[] analysis = {"چرا", "تحلیل", "گزارش", "پیش‌بینی", "پیش بینی", "روند", "سود", "مقایسه",
                "ریسک", "توصیه", "علت", "راهکار", "بررسی"};
        String[] writing = {"پیام", "متن", "نامه", "یادداشت", "بنویس", "پیامک", "ایمیل", "اطلاعیه"};
        for (String w : writing) if (q.contains(w)) return "writing";
        for (String w : analysis) if (q.contains(w)) return "analysis";
        return q.trim().split("\\s+").length <= 6 ? "fast" : "general";
    }

    public static Result complete(Context c, String system, String user) throws Exception {
        return complete(c, "general", system, user);
    }

    public static Result complete(Context c, String task, String system, String user) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = p.getString("provider", AiClient.GEMINI);
        List<String> order = new ArrayList<>();
        if ("general".equals(task)) {
            order.add(active);
            for (String s : PRIORITY) {
                if (!s.equals(active)) order.add(s);
            }
        } else {
            String[] pref = "analysis".equals(task) ? ANALYSIS_ORDER
                    : "writing".equals(task) ? WRITING_ORDER : FAST_ORDER;
            for (String s : pref) order.add(s);
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
            boolean isActive = provider.equals(active) && "general".equals(task);
            String savedModel = p.getString("model", "").trim();
            String model = isActive && !savedModel.isEmpty() ? savedModel : AiClient.modelFor(provider, task);
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
