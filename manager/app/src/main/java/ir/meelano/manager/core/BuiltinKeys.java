package ir.meelano.manager.core;

/** Built-in API keys for the assistant. Used when the user has not entered a key of their own. */
public final class BuiltinKeys {
    private BuiltinKeys() {}

    /* Order follows the list supplied by the owner. */
    static final String GEMINI_KEY = "AIzaSyARwYMgJaVKt6LlU1-ZwTC4fThrlqAX1RE";
    static final String GROQ_KEY = "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";
    static final String DEEPSEEK_KEY = "sk-3530d712eaaa4b5aafcc815891b3e619";

    /** Built-in key for a provider id, or an empty string when none is embedded. */
    public static String get(String provider) {
        if (provider == null) return "";
        switch (provider) {
            case AiClient.GEMINI: return GEMINI_KEY;
            case AiClient.GROQ: return GROQ_KEY;
            case AiClient.DEEPSEEK: return DEEPSEEK_KEY;
            default: return "";
        }
    }
}
