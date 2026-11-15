package ir.meelano.windowsinvoice.data;

import java.util.Locale;

/**
 * Builds the SQL Server JDBC URL for a saved endpoint and classifies the endpoint as
 * inside-network or internet-facing. Inside-network endpoints may be configured without
 * transport encryption (a local test server); an internet-facing endpoint always keeps
 * encryption and certificate validation on, so the app never ships a plaintext public route.
 */
public final class SqlServerUrl {
    private SqlServerUrl() { }

    public static String build(String host, int port, String database, boolean encrypt) {
        String jdbcHost = host == null ? "" : host.trim();
        // A host is validated too: a semicolon would otherwise let a typed address inject
        // extra JDBC properties (encrypt=…, trustServerCertificate=…) into the URL.
        if (!WindowsSqlSettings.validHost(jdbcHost) || !WindowsSqlSettings.validPort(port)
                || !WindowsSqlSettings.validDatabase(database))
            throw new IllegalArgumentException("مشخصات اتصال SQL معتبر نیست.");
        if (jdbcHost.indexOf(':') >= 0 && !jdbcHost.startsWith("[")) jdbcHost = "[" + jdbcHost + "]";
        boolean secure = encrypt || !WindowsSqlSettings.isPrivateAddress(jdbcHost);
        return "jdbc:sqlserver://" + jdbcHost + ":" + port
                + ";databaseName=" + database.trim()
                + ";encrypt=" + (secure ? "true" : "false")
                + ";trustServerCertificate=" + (secure ? "false" : "true")
                + ";loginTimeout=8;socketTimeout=60;applicationName=MEELANO-Windows;";
    }

    /** True when the endpoint is not a private/loopback/link-local address. */
    public static boolean isPublic(String host) {
        return !WindowsSqlSettings.isPrivateAddress(host);
    }

    /** Encryption is mandatory for internet-facing endpoints and optional inside the LAN. */
    public static boolean requiresEncryption(String host) {
        return isPublic(host);
    }

    /** Rejects a URL that would disable protection on an internet-facing endpoint. */
    public static boolean urlIsAcceptable(String url, String host) {
        if (url == null) return false;
        if (!url.toLowerCase(Locale.ROOT).startsWith("jdbc:sqlserver://")) return false;
        if (!requiresEncryption(host)) return true;
        String lower = url.toLowerCase(Locale.ROOT);
        return !propertyEquals(lower, "encrypt", "false")
                && !propertyEquals(lower, "trustservercertificate", "true");
    }

    private static boolean propertyEquals(String url, String name, String value) {
        for (String property : url.split(";")) {
            int equals = property.indexOf('=');
            if (equals > 0 && property.substring(0, equals).trim().equalsIgnoreCase(name)
                    && property.substring(equals + 1).trim().equalsIgnoreCase(value)) return true;
        }
        return false;
    }
}
