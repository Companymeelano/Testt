package ir.meelano.manager.core;

/**
 * No provider credentials are shipped with the application.
 *
 * Users may configure their own provider key through AiSettings (encrypted by SecretBox),
 * or use the separately deployed managed backend with its key exclusively in server-side
 * environment configuration. This compatibility class intentionally always returns an empty value.
 */
public final class BuiltinKeys {
    private BuiltinKeys() { }

    public static String get(String provider) {
        return "";
    }
}
