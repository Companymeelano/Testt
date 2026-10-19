package ir.meelano.manager.core;

/** Built-in API keys for the assistant. Used when the user has not entered a key of their own. */
public final class BuiltinKeys {
    private BuiltinKeys() {}

    /* Order follows the list supplied by the owner. */
    static final String OPENAI_KEY = "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";
    static final String GEMINI_KEY = "AIzaSyARwYMgJaVKt6LlU1-ZwTC4fThrlqAX1RE";
    static final String GROQ_KEY = "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";
    static final String GAPGPT_KEY = "sk-5sXGCqdxigjppVtvqAdogjk78B1K7ZhkbM1DdPTUNjFVP7qC";
    static final String DEEPSEEK_KEY = "sk-3530d712eaaa4b5aafcc815891b3e619";
    static final String OPENROUTER_KEY = "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";
    static final String CEREBRAS_KEY = "csk-hd5ex5dvff49x4ew9rph5dmmh9jtj5rvvnw364n6pxe83vf8";
    static final String HUGGINGFACE_KEY = "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";

    /** Built-in key for a provider id, or an empty string when none is embedded. */
    public static String get(String provider) {
        if (provider == null) return "";
        switch (provider) {
            case AiClient.OPENAI: return OPENAI_KEY;
            case AiClient.GEMINI: return GEMINI_KEY;
            case AiClient.GROQ: return GROQ_KEY;
            case AiClient.GAPGPT: return GAPGPT_KEY;
            case AiClient.DEEPSEEK: return DEEPSEEK_KEY;
            case AiClient.OPENROUTER: return OPENROUTER_KEY;
            case AiClient.CEREBRAS: return CEREBRAS_KEY;
            case AiClient.HUGGINGFACE: return HUGGINGFACE_KEY;
            default: return "";
        }
    }
}
