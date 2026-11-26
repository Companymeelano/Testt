package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import ir.meelano.manager.screens.AiSettings;

/**
 * Chooses which service answers and fails over between them.
 *
 * <p>The user's primary service goes first (default: GapGPT, per the product requirement), then
 * the user-configured fallback order, then any remaining registered service. Only services that
 * have a key are used. An invalid key (401/403) pauses that service for an hour; a rate limit
 * (429) pauses it for ten minutes; a transient server error (5xx) pauses it briefly. Auth errors
 * are never confused with transient network problems, and each service is tried at most once per
 * request, so there is no endless retry loop.
 *
 * <p>The ordering and cooldown decisions are pure functions ({@link #mergeOrder},
 * {@link #cooldownMs}) so they can be unit-tested without Android. The Android shell reads
 * preferences, records per-service status for the settings screen, and performs the HTTP call.
 */
public final class AiRouter {
    private AiRouter() { }

    static final String PREFS = "meelano_ai";
    private static final long PAUSE_RATE_MS = 10 * 60_000L;
    private static final long PAUSE_AUTH_MS = 60 * 60_000L;
    private static final long PAUSE_SERVER_MS = 2 * 60_000L;

    public static final class Result {
        public final String text;
        public final String provider;

        Result(String text, String provider) {
            this.text = text;
            this.provider = provider;
        }
    }

    // ================= pure, testable logic =================

    /**
     * Merges the primary service, the user's configured fallback order and the registry into one
     * de-duplicated list. The primary is always first; unknown ids are ignored. Pure.
     */
    public static List<String> mergeOrder(String primary, List<String> configured) {
        List<String> order = new ArrayList<>();
        addKnown(order, primary);
        if (configured != null) for (String s : configured) addKnown(order, s);
        for (String s : AiProviders.ids()) addKnown(order, s);
        return order;
    }

    private static void addKnown(List<String> order, String id) {
        if (id != null && AiProviders.isKnown(id) && !order.contains(id)) order.add(id);
    }

    /** Cooldown for an error kind; auth is paused longest, network errors are retryable (0). Pure. */
    public static long cooldownMs(String kind) {
        if (AiClient.KIND_AUTH.equals(kind)) return PAUSE_AUTH_MS;
        if (AiClient.KIND_RATE.equals(kind)) return PAUSE_RATE_MS;
        if (AiClient.KIND_SERVER.equals(kind)) return PAUSE_SERVER_MS;
        return 0L;
    }

    // ================= preference helpers (used by settings) =================

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The user's primary service (default GapGPT). */
    public static String primary(Context c) {
        String p = prefs(c).getString("provider", AiProviders.defaultPrimary());
        return AiProviders.isKnown(p) ? p : AiProviders.defaultPrimary();
    }

    public static void setPrimary(Context c, String provider) {
        if (AiProviders.isKnown(provider)) prefs(c).edit().putString("provider", provider).apply();
    }

    /** The user-configured fallback order (empty = registry default). */
    public static List<String> configuredOrder(Context c) {
        return parseOrder(prefs(c).getString("provider_order", ""));
    }

    public static void setConfiguredOrder(Context c, List<String> order) {
        StringBuilder b = new StringBuilder();
        if (order != null) {
            for (String s : order) {
                if (s == null || !AiProviders.isKnown(s)) continue;
                if (b.length() > 0) b.append(',');
                b.append(s);
            }
        }
        prefs(c).edit().putString("provider_order", b.toString()).apply();
    }

    static List<String> parseOrder(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Per-service model override; empty means use the registry default for the task. */
    public static String modelOverride(Context c, String provider) {
        return prefs(c).getString("model_" + provider, "").trim();
    }

    public static void setModelOverride(Context c, String provider, String model) {
        prefs(c).edit().putString("model_" + provider, model == null ? "" : model.trim()).apply();
    }

    /** Effective model for a provider/task: user override first, then registry default. */
    static String modelFor(Context c, String provider, String task) {
        String override = modelOverride(c, provider);
        return override.isEmpty() ? AiClient.modelFor(provider, task) : override;
    }

    /** Last recorded status kind for a service ("untested" when never tested). */
    public static String statusOf(Context c, String provider) {
        return prefs(c).getString("status_" + provider, "untested");
    }

    public static long statusTime(Context c, String provider) {
        return prefs(c).getLong("status_time_" + provider, 0L);
    }

    /** Remaining cooldown in ms (0 when the service is usable now). */
    public static long cooldownRemaining(Context c, String provider) {
        long until = prefs(c).getLong("cool_" + provider, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    static void recordStatus(SharedPreferences p, String provider, String kind) {
        p.edit().putString("status_" + provider, kind)
                .putLong("status_time_" + provider, System.currentTimeMillis()).apply();
    }

    /** Clears a cooldown and status so a fresh connection test starts clean. */
    public static void resetStatus(Context c, String provider) {
        prefs(c).edit().remove("cool_" + provider)
                .remove("status_" + provider).remove("status_time_" + provider).apply();
    }

    /**
     * Independent connection test for one service. Records the status kind and applies the same
     * cooldown policy as live routing, so the settings pill always reflects reality. Returns a
     * short, non-secret Persian result message. Runs on the calling (background) thread.
     */
    public static String testService(Context c, String provider, String key, String model) {
        SharedPreferences p = prefs(c);
        resetStatus(c, provider);
        if (key == null || key.trim().isEmpty()) {
            recordStatus(p, provider, AiClient.KIND_AUTH);
            return "کلید این سرویس وارد نشده است.";
        }
        String m = (model == null || model.trim().isEmpty()) ? modelFor(c, provider, "fast") : model.trim();
        String base = AiClient.CUSTOM.equals(provider) ? p.getString("baseUrl", "") : "";
        try {
            AiClient.complete(provider, base, m, key.trim(), "پاسخ کوتاه بده.", "فقط بنویس: آمادهٔ کمک هستم.");
            recordStatus(p, provider, AiClient.KIND_OK);
            return "اتصال برقرار است ✓";
        } catch (AiClient.ApiError e) {
            String kind = AiClient.classify(e.code);
            long cd = cooldownMs(kind);
            if (cd > 0) p.edit().putLong("cool_" + provider, System.currentTimeMillis() + cd).apply();
            recordStatus(p, provider, kind);
            return "اتصال ناموفق: " + e.getMessage();
        } catch (Exception e) {
            recordStatus(p, provider, AiClient.KIND_NET);
            return "اتصال ناموفق: " + (e.getMessage() == null ? "خطای شبکه" : e.getMessage());
        }
    }

    // ================= routing =================

    /** True when at least one service has a key. */
    public static boolean anyKey(Context c) {
        for (String[] n : AiClient.NAMES) {
            if (!AiSettings.keyFor(c, n[0]).isEmpty()) return true;
        }
        return false;
    }

    /** Picks the capability from the question: writing, analysis, or a quick answer. */
    public static String taskFor(String question) {
        return AiTask.forQuestion(question);
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
        SharedPreferences p = prefs(c);
        List<String> order = mergeOrder(primary(c), configuredOrder(c));
        StringBuilder errs = new StringBuilder();
        long now = System.currentTimeMillis();
        for (String provider : order) {
            String key = AiSettings.keyFor(c, provider);
            if (key.isEmpty()) continue;
            if (p.getLong("cool_" + provider, 0L) > now) {
                errs.append(AiClient.nameOf(provider)).append(": موقتاً کنار گذاشته شده؛ ");
                continue;
            }
            String model = modelFor(c, provider, task);
            String base = AiClient.CUSTOM.equals(provider) ? p.getString("baseUrl", "") : "";
            try {
                String text = call.run(provider, base, model, key);
                recordStatus(p, provider, AiClient.KIND_OK);
                return new Result(text, provider);
            } catch (AiClient.ApiError e) {
                String kind = AiClient.classify(e.code);
                long cd = cooldownMs(kind);
                if (cd > 0) p.edit().putLong("cool_" + provider, now + cd).apply();
                recordStatus(p, provider, kind);
                if (started != null && started[0]) throw e;
                errs.append(AiClient.nameOf(provider)).append(": ").append(e.getMessage()).append("؛ ");
            } catch (Exception e) {
                recordStatus(p, provider, AiClient.KIND_NET);
                if (started != null && started[0]) throw e;
                errs.append(AiClient.nameOf(provider)).append(": ")
                        .append(e.getMessage() == null ? "خطای شبکه" : e.getMessage()).append("؛ ");
            }
        }
        throw new IllegalStateException(errs.length() == 0
                ? "هیچ کلید هوش مصنوعی ثبت نشده است؛ در تنظیمات، کلید سرویس را وارد کنید."
                : errs.toString());
    }
}