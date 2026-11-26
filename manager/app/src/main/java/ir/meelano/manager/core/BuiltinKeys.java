package ir.meelano.manager.core;

/**
 * No API keys are embedded in the app.
 *
 * <p>Earlier builds shipped built-in Gemini/Groq keys here. That violated the security rule that
 * keys must never live in source, the APK, or any public file, so the embedded values were removed.
 * The assistant now uses ONLY the key the user enters in Settings → هوش مصنوعی, stored encrypted
 * by {@link SecretBox} (AES-GCM in the Android Keystore). Without a user key, the offline
 * rule-based engine still answers; the online model is simply not used.
 *
 * <p>IMPORTANT for the owner: any key that was ever embedded in a previous source/APK is
 * compromised and must be revoked/rotated in the provider's console. Removing it here does not
 * un-leak copies already distributed.
 *
 * <p>This class is kept only so existing callers compile; it always returns an empty string.
 */
public final class BuiltinKeys {
    private BuiltinKeys() { }

    /** Always empty: the app never falls back to an embedded key. */
    public static String get(String provider) {
        return "";
    }
}