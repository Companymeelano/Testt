package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import ir.meelano.manager.screens.AiSettings;

/**
 * Chooses which service answers, with automatic failover between the three services in use:
 * Gemini, DeepSeek and Groq. Each kind of question has its own order (see the plan below).
 * A service that is rate-limited (429) is paused for 10 minutes; one that rejects its key
 * (401/403) is paused for an hour. Only services with a key are used.
 */
public final class AiRouter {
    private AiRouter() { }

    private static final String PREFS = "meelano_ai";
    private static final long PAUSE_RATE_MS = 10 * 60_000L;
    private static final long PAUSE_AUTH_MS = 60 * 60_000L;

    /*
     * Capability plan. The first service with a key answers; if it fails, is paused, or rejects
     * the request, the next one takes over.
     *   analysis : DeepSeek (deepseek-v4-pro) -> Gemini (2.5 Pro) -> Groq
     *   fast     : Groq (llama 3.3 70B)       -> Gemini (2.5 Flash) -> DeepSeek
     *   writing  : Gemini (2.5 Flash)         -> DeepSeek -> Groq
     *   general  : the service the user selected, then the others
     * Speech is handled by Speaker (Gemini natural voice, then the built-in Persian voice).
     */
    private static final String[] ANALYSIS_ORDER = {AiClient.DEEPSEEK, AiClient.GEMINI, AiClient.GROQ, AiClient.OPENAI};
    private static final String[] FAST_ORDER = {AiClient.GROQ, AiClient.GEMINI, AiClient.DEEPSEEK, AiClient.OPENAI};
    private static final String[] WRITING_ORDER = {AiClient.GEMINI, AiClient.DEEPSEEK, AiClient.GROQ, AiClient.OPENAI};
    private static final String[] GENERAL_ORDER = {AiClient.GEMINI, AiClient.DEEPSEEK, AiClient.GROQ, AiClient.OPENAI};

    public static final class Result {
        public final String text;
        public final String provider;

        Result(String text, String provider) {
            this.text = text;
            this.provider = provider;
        }
    }

    /** True when at least one service has a key. */
    public static boolean anyKey(Context c) {
        for (String[] n : AiClient.NAMES) {
            if (!AiSettings.keyFor(c, n[0]).isEmpty()) return true;
        }
        return false;
    }

    /** Picks the capability from the question: writing, analysis, or a quick answer. */
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

    /** Full answer at once. */
    public static Result complete(Context c, String task, String system, String user) throws Exception {
        return attempt(c, task, null, (provider, base, model, key) ->
                AiClient.complete(provider, base, model, key, system, user));
    }

    /**
     * Streams the answer. Pieces reach {@code onPiece} as they arrive. If a service fails before
     * any text was shown, the next service takes over; once text is on screen it is not replaced.
     */
    public static Result completeStream(Context c, String task, String system, String user,
                                        final AiClient.Delta onPiece) throws Exception {
        final boolean[] started = {false};
        return attempt(c, task, started, (provider, base, model, key) ->
                AiClient.stream(provider, base, model, key, system, user, piece -> {
                    started[0] = true;
                    if (onPiece != null) onPiece.on(piece);
                }));
    }

    private interface Call {
        String run(String provider, String base, String model, String key) throws Exception;
    }

    private static Result attempt(Context c, String task, boolean[] started, Call call) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String active = p.getString("provider", AiClient.GEMINI);
        List<String> order = orderFor(task, active);
        StringBuilder errs = new StringBuilder();
        long now = System.currentTimeMillis();
        for (String provider : order) {
            String key = AiSettings.keyFor(c, provider);
            if (key.isEmpty()) continue;
            if (p.getLong("cool_" + provider, 0L) > now) {
                errs.append(AiClient.nameOf(provider)).append(": موقتاً کنار گذاشته شده؛ ");
                continue;
            }
            String model = AiClient.modelFor(provider, task);
            try {
                return new Result(call.run(provider, "", model, key), provider);
            } catch (AiClient.ApiError e) {
                if (e.code == 429) {
                    p.edit().putLong("cool_" + provider, now + PAUSE_RATE_MS).apply();
                } else if (e.code == 401 || e.code == 403) {
                    p.edit().putLong("cool_" + provider, now + PAUSE_AUTH_MS).apply();
                }
                if (started != null && started[0]) throw e;
                errs.append(AiClient.nameOf(provider)).append(": ").append(e.getMessage()).append("؛ ");
            } catch (Exception e) {
                if (started != null && started[0]) throw e;
                errs.append(AiClient.nameOf(provider)).append(": ").append(e.getMessage()).append("؛ ");
            }
        }
        throw new IllegalStateException(errs.length() == 0
                ? "هیچ کلید هوش مصنوعی ثبت نشده است" : errs.toString());
    }

    /** The service the user picked goes first for general questions; the plan orders the rest. */
    private static List<String> orderFor(String task, String active) {
        List<String> order = new ArrayList<>();
        String[] pref = "analysis".equals(task) ? ANALYSIS_ORDER
                : "writing".equals(task) ? WRITING_ORDER
                : "fast".equals(task) ? FAST_ORDER : GENERAL_ORDER;
        if ("general".equals(task)) order.add(active);
        for (String s : pref) {
            if (!order.contains(s)) order.add(s);
        }
        return order;
    }
}
