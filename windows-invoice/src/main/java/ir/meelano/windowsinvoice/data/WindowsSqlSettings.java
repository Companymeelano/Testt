package ir.meelano.windowsinvoice.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Per-Windows-user SQL connection profile. Endpoint metadata is stored under LocalAppData;
 * the SQL password is protected with the current Windows user's DPAPI via PowerShell SecureString.
 */
public final class WindowsSqlSettings {
    public static final int DEFAULT_PORT = 1433;
    public static final int FORWARDED_PORT = 5050;
    private static final String CONFIG_NAME = "connection.properties";
    private static final String PASSWORD_NAME = "sql-login.xml";
    private static final String POWERSHELL = "powershell.exe";
    private static final String SAVE_SCRIPT = "$ErrorActionPreference='Stop';"
            + "$b64=[Console]::In.ReadToEnd().Trim();"
            + "$bytes=[Convert]::FromBase64String($b64);"
            + "$text=[Text.Encoding]::UTF8.GetString($bytes);"
            + "$sec=ConvertTo-SecureString -String $text -AsPlainText -Force;"
            + "$sec | Export-Clixml -LiteralPath $env:MEELANO_CREDENTIAL_PATH -Force;"
            + "[Console]::Out.Write('OK')";
    private static final String LOAD_SCRIPT = "$ErrorActionPreference='Stop';"
            + "$sec=Import-Clixml -LiteralPath $env:MEELANO_CREDENTIAL_PATH;"
            + "$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec);"
            + "try{$text=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr);"
            + "$bytes=[Text.Encoding]::UTF8.GetBytes($text);"
            + "[Console]::Out.Write([Convert]::ToBase64String($bytes))}"
            + "finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}";

    private WindowsSqlSettings() { }

    public static final class Endpoint {
        public final String route;
        public final String host;
        public final int port;
        public final String database;
        public final String username;
        public final String password;

        public Endpoint(String route, String host, int port, String database,
                        String username, String password) {
            this.route = route;
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }

        public boolean complete() {
            return validHost(host) && validPort(port) && validDatabase(database)
                    && username != null && !username.isBlank()
                    && password != null && !password.isEmpty();
        }
    }

    public static final class Draft {
        public final String lanHost;
        public final int lanPort;
        public final String wanHost;
        public final int wanPort;
        public final String database;
        public final String username;
        public final String password;
        public final String lastRoute;

        Draft(String lanHost, int lanPort, String wanHost, int wanPort,
              String database, String username, String password, String lastRoute) {
            this.lanHost = lanHost;
            this.lanPort = lanPort;
            this.wanHost = wanHost;
            this.wanPort = wanPort;
            this.database = database;
            this.username = username;
            this.password = password;
            this.lastRoute = lastRoute;
        }
    }

    public static Draft draft() {
        Properties p = loadProperties();
        return new Draft(get(p, "lan.host"), port(p, "lan.port"), get(p, "wan.host"), port(p, "wan.port"),
                get(p, "database"), get(p, "username"), readPassword(), get(p, "lastRoute"));
    }

    /** Endpoints in transport-preferred order; the previous successful route is only a fallback tie-breaker. */
    public static List<Endpoint> candidates() {
        Draft d = draft();
        if (d.database.isBlank() || d.username.isBlank() || d.password.isEmpty()) return Collections.emptyList();
        boolean preferLan = hasLocalAddress();
        if ("lan".equals(d.lastRoute) && preferLan) preferLan = true;
        if ("wan".equals(d.lastRoute) && !hasLocalAddress()) preferLan = false;
        List<Endpoint> out = new ArrayList<>(2);
        if (preferLan) {
            add(out, "lan", d.lanHost, d.lanPort, d, d.password);
            add(out, "wan", d.wanHost, d.wanPort, d, d.password);
        } else {
            add(out, "wan", d.wanHost, d.wanPort, d, d.password);
            add(out, "lan", d.lanHost, d.lanPort, d, d.password);
        }
        return out;
    }

    public static boolean saveVerified(String host, int port, String database,
                                      String username, String password, String route) {
        String h = clean(host);
        String db = clean(database);
        String user = clean(username);
        if (!validHost(h) || !validPort(port) || !validDatabase(db)
                || user.isEmpty() || password == null || password.isEmpty()) return false;
        if (!isWindows()) return false;
        if (!savePassword(password)) return false;

        Properties p = loadProperties();
        String chosen = "lan".equals(route) ? "lan" : "wan".equals(route) ? "wan"
                : (isPrivateAddress(h) ? "lan" : "wan");
        p.setProperty("database", db);
        p.setProperty("username", user);
        p.setProperty("lastRoute", chosen);
        p.setProperty(chosen + ".host", h);
        p.setProperty(chosen + ".port", String.valueOf(port));
        return writeProperties(p);
    }

    /** Writes endpoint metadata only; the SQL password never appears in this file. */
    private static boolean writeProperties(Properties p) {
        try {
            Path target = configPath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(CONFIG_NAME + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                p.store(out, "MEELANO Windows SQL endpoint settings (no credential values)");
            }
            replaceAtomically(temp, target);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Last signed-in Atiran account name (not a secret) for prefilling the login form. */
    public static String atiranUser() {
        return get(loadProperties(), "atiran.user");
    }

    public static void rememberAtiranUser(String user) {
        String value = clean(user);
        if (value.isEmpty()) return;
        Properties p = loadProperties();
        p.setProperty("atiran.user", value);
        writeProperties(p);
    }

    public static void rememberSuccessfulRoute(String route) {
        if (!"lan".equals(route) && !"wan".equals(route)) return;
        Properties p = loadProperties();
        p.setProperty("lastRoute", route);
        writeProperties(p);
    }

    /** Removes the saved endpoint and the DPAPI-protected SQL credential (fresh one-time setup). */
    public static void clear() {
        try { Files.deleteIfExists(passwordPath()); } catch (IOException ignored) { }
        Properties p = loadProperties();
        for (String key : List.of("lan.host", "lan.port", "wan.host", "wan.port",
                "database", "username", "lastRoute")) p.remove(key);
        writeProperties(p);
    }

    /** True when endpoint metadata exists but the DPAPI credential cannot be read back. */
    public static boolean credentialMissing() {
        Draft d = draft();
        return !d.database.isBlank() && !d.username.isBlank() && d.password.isEmpty();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean validPort(int port) { return port > 0 && port <= 65535; }

    public static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(foldDigits(value == null ? "" : value.trim()));
            return validPort(parsed) ? parsed : DEFAULT_PORT;
        } catch (Exception ignored) {
            return DEFAULT_PORT;
        }
    }

    public static boolean validHost(String value) {
        String host = clean(value);
        if (host.isEmpty() || host.length() > 253 || host.contains(";") || host.contains("/")
                || host.contains("\\") || host.contains(" ")) return false;
        return true;
    }

    public static boolean validDatabase(String value) {
        String db = clean(value);
        return !db.isEmpty() && db.length() <= 128 && !db.contains(";")
                && !db.contains("/") && !db.contains("\\");
    }

    public static String clean(String value) { return value == null ? "" : value.trim(); }

    public static boolean isPrivateAddress(String host) {
        String value = clean(host);
        if (value.isEmpty()) return false;
        if (!value.contains(".") && !value.contains(":")) return true;
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(value);
            return address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress();
        } catch (Exception ignored) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.endsWith(".local") || lower.endsWith(".lan");
        }
    }

    private static void add(List<Endpoint> out, String route, String host, int port,
                            Draft d, String password) {
        if (host == null || host.isBlank()) return;
        Endpoint endpoint = new Endpoint(route, host, port, d.database, d.username, password);
        if (endpoint.complete()) out.add(endpoint);
    }

    private static boolean hasLocalAddress() {
        try {
            var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                var network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                var addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && address.isSiteLocalAddress()) return true;
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static Properties loadProperties() {
        Properties p = new Properties();
        Path path = configPath();
        if (!Files.isRegularFile(path)) return p;
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException ignored) { }
        return p;
    }

    private static String get(Properties p, String key) {
        return p.getProperty(key, "").trim();
    }

    private static int port(Properties p, String key) {
        return parsePort(get(p, key));
    }

    private static String readPassword() {
        if (!isWindows() || !Files.isRegularFile(passwordPath())) return "";
        try {
            Process process = startPowerShell(LOAD_SCRIPT);
            String encoded = readAll(process.getInputStream()).trim();
            int exit = process.waitFor();
            if (exit != 0 || encoded.isEmpty()) return "";
            return new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean savePassword(String password) {
        try {
            Path path = passwordPath();
            Files.createDirectories(path.getParent());
            Process process = startPowerShell(SAVE_SCRIPT);
            byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
            String encoded = java.util.Base64.getEncoder().encodeToString(bytes);
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(encoded.getBytes(StandardCharsets.US_ASCII));
            }
            String response = readAll(process.getInputStream()).trim();
            String error = readAll(process.getErrorStream());
            int exit = process.waitFor();
            java.util.Arrays.fill(bytes, (byte) 0);
            return exit == 0 && "OK".equals(response) && Files.isRegularFile(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Process startPowerShell(String script) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(POWERSHELL, "-NoLogo", "-NoProfile",
                "-NonInteractive", "-Command", script);
        builder.redirectErrorStream(false);
        builder.environment().put("MEELANO_CREDENTIAL_PATH", passwordPath().toString());
        return builder.start();
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = in.read(buffer)) >= 0) out.write(buffer, 0, count);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path appDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Path.of(local, "MEELANO", "WindowsInvoice");
        return Path.of(System.getProperty("user.home"), ".config", "MEELANO", "WindowsInvoice");
    }

    private static Path configPath() { return appDirectory().resolve(CONFIG_NAME); }
    private static Path passwordPath() { return appDirectory().resolve(PASSWORD_NAME); }

    private static String foldDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u06f0' && c <= '\u06f9') c = (char) ('0' + c - '\u06f0');
            else if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + c - '\u0660');
            out.append(c);
        }
        return out.toString();
    }
}
