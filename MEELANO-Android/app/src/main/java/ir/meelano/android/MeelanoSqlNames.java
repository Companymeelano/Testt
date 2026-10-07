package ir.meelano.android;

import java.util.regex.Pattern;

/**
 * Guard for the «همهٔ جداول آتیران» browser.
 *
 * The catalogue page shows every table and view of Atiran2, so unlike the old fixed
 * {@code SAFE_TABLES} whitelist the name that reaches a statement is only known at runtime.
 * A name is spliced into {@code dbo.[name]} only after this check AND after it has been
 * confirmed to exist in {@code sys.objects}; everything else is refused before any SQL is built.
 */
final class MeelanoSqlNames {

    /** A plain T‑SQL identifier: letters/digits/underscore, first character not a digit. */
    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");

    private MeelanoSqlNames() { }

    static boolean isSafeIdentifier(String name) {
        return name != null && IDENT.matcher(name).matches();
    }

    /** Quoted form used inside statements — the caller must already have passed {@link #isSafeIdentifier}. */
    static String quote(String name) {
        if (!isSafeIdentifier(name)) throw new IllegalArgumentException("unsafe identifier");
        return "[" + name + "]";
    }
}
