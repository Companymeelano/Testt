package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the generative-AI services the assistant can use.
 *
 * <p>Every provider is one immutable {@link Spec}. Adding a future service is a new entry in
 * {@link #ALL} — no other class needs to change. {@link AiClient} reads its wire metadata
 * (base URL, default/analysis model, speech model, wire format) from here, and the settings
 * screen lists every entry so the user can pick any of them as primary or fallback.
 *
 * <p>Two wire formats exist and are NOT interchangeable:
 * <ul>
 *   <li>{@link #WIRE_OPENAI} — OpenAI-compatible {@code /chat/completions} (OpenAI, GapGPT,
 *       Groq, DeepSeek, xAI, OpenRouter, Cerebras, Hugging Face, custom).</li>
 *   <li>{@link #WIRE_GEMINI} — Google's native {@code :generateContent} / {@code :streamGenerateContent}
 *       with {@code x-goog-api-key}. Gemini never goes through the OpenAI path.</li>
 * </ul>
 *
 * <p>Default primary is GapGPT (per the product requirement): it is used first when it has a
 * key and answers; otherwise the router falls through the user's configured order. Presence of
 * a base URL here does not imply an active account or a valid key — that is verified per service
 * by the connection test in settings.
 */
public final class AiProviders {
    private AiProviders() { }

    public static final String WIRE_OPENAI = "openai";
    public static final String WIRE_GEMINI = "gemini";

    /** One service. {@code baseUrl} is empty only for the user-defined custom service. */
    public static final class Spec {
        public final String id;
        public final String name;
        public final String baseUrl;
        public final String wire;
        public final String defaultModel;
        public final String analysisModel;
        public final String ttsModel;
        public final boolean speech;

        Spec(String id, String name, String baseUrl, String wire,
             String defaultModel, String analysisModel, String ttsModel, boolean speech) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.wire = wire;
            this.defaultModel = defaultModel;
            this.analysisModel = analysisModel;
            this.ttsModel = ttsModel;
            this.speech = speech;
        }

        public boolean isGemini() { return WIRE_GEMINI.equals(wire); }
    }

    private static Spec openai(String id, String name, String url, String model, String analysis) {
        return new Spec(id, name, url, WIRE_OPENAI, model, analysis, "", false);
    }

    /**
     * Ordered registry. GapGPT is first (default primary). The order is also the default
     * fallback order when the user has not configured a custom one.
     */
    public static final Spec[] ALL = {
            openai(AiClient.GAPGPT, "GapGPT", "https://api.gapgpt.app/v1", "gpt-4o-mini", "gpt-4o"),
            openai(AiClient.GROQ, "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "llama-3.3-70b-versatile"),
            openai(AiClient.DEEPSEEK, "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "deepseek-reasoner"),
            new Spec(AiClient.GEMINI, "Gemini", "https://generativelanguage.googleapis.com/v1beta",
                    WIRE_GEMINI, "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-preview-tts", true),
            new Spec(AiClient.OPENAI, "ChatGPT", "https://api.openai.com/v1",
                    WIRE_OPENAI, "gpt-4o-mini", "gpt-4o", "tts-1", true),
            openai(AiClient.GROK, "Grok (xAI)", "https://api.x.ai/v1", "grok-3-mini", "grok-3-mini"),
            openai(AiClient.OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openai/gpt-4o"),
            openai(AiClient.CEREBRAS, "Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", "llama-3.3-70b"),
            openai(AiClient.HUGGINGFACE, "Hugging Face", "https://router.huggingface.co/v1",
                    "meta-llama/Llama-3.3-70B-Instruct", "meta-llama/Llama-3.3-70B-Instruct"),
            new Spec(AiClient.CUSTOM, "سرویس دلخواه", "", WIRE_OPENAI, "", "", "", false),
    };

    /** The product requirement: GapGPT is the default primary service. */
    public static String defaultPrimary() { return AiClient.GAPGPT; }

    public static Spec byId(String id) {
        if (id != null) {
            for (Spec s : ALL) if (s.id.equals(id)) return s;
        }
        return null;
    }

    public static boolean isKnown(String id) { return byId(id) != null; }

    /** Display name, or the raw id when unknown. */
    public static String nameOf(String id) {
        Spec s = byId(id);
        return s != null ? s.name : (id == null ? "" : id);
    }

    /** {id, name} pairs for UI chips, in registry order. */
    public static String[][] names() {
        String[][] out = new String[ALL.length][2];
        for (int i = 0; i < ALL.length; i++) {
            out[i][0] = ALL[i].id;
            out[i][1] = ALL[i].name;
        }
        return out;
    }

    /** All provider ids in registry (default fallback) order. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Spec s : ALL) out.add(s.id);
        return out;
    }

    /** Base URL for a provider; custom uses the user-typed value. */
    public static String baseUrl(String id, String custom) {
        Spec s = byId(id);
        if (s == null) return custom == null ? "" : custom.trim();
        if (AiClient.CUSTOM.equals(id)) return custom == null ? "" : custom.trim();
        return s.baseUrl;
    }

    public static String wire(String id) {
        Spec s = byId(id);
        return s == null ? WIRE_OPENAI : s.wire;
    }

    public static String defaultModel(String id) {
        Spec s = byId(id);
        return s == null ? "" : s.defaultModel;
    }

    /** Model for a task: analysis uses the stronger model where one exists. */
    public static String modelFor(String id, String task) {
        Spec s = byId(id);
        if (s == null) return "";
        if ("analysis".equals(task) && s.analysisModel != null && !s.analysisModel.isEmpty()) {
            return s.analysisModel;
        }
        return s.defaultModel;
    }

    public static boolean hasSpeech(String id) {
        Spec s = byId(id);
        return s != null && s.speech;
    }

    public static String ttsModel(String id) {
        Spec s = byId(id);
        return s == null ? "" : s.ttsModel;
    }
}