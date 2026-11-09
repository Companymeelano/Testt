package ir.meelano.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Base64;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Direct SQL Server profile storage and connection policy for the main Android app.
 * Endpoints are ordinary preferences; the SQL password is encrypted by an app-specific
 * Android Keystore AES-GCM key. No server, database, username, or password is compiled in.
 */
final class SqlConnectionConfig {
    static final int DEFAULT_PORT = 1433;
    static final int FORWARDED_PORT = 5050;
    private static final String PREFS = MainActivity.PREFS_NAME;
    private static final String SECRET_KEY = "sql_password_keystore_v1";
    private static final String KEY_LAN_HOST = "sql_lan_host";
    private static final String KEY_WAN_HOST = "sql_wan_host";
    private static final String KEY_LAN_PORT = "sql_lan_port";
    private static final String KEY_WAN_PORT = "sql_wan_port";
    private static final String KEY_DB = "sql_database";
    private static final String KEY_USER = "sql_user";
    private static final String KEY_PASSWORD = "sql_password_ciphertext";
    private static final String KEY_LAST_ROUTE = "sql_last_route";
    private static final String CIPHER_PREFIX = "g1:";

    private SqlConnectionConfig() { }

    static final class Profile {
        final String kind;
        final String host;
        final int port;
        final String database;
        final String user;
        final String password;

        Profile(String kind, String host, int port, String database, String user, String password) {
            this.kind = kind;
            this.host = host;
            this.port = port;
            this.database = database;
            this.user = user;
            this.password = password;
        }

        boolean complete() {
            return validHost(host) && validPort(port) && validDatabase(database)
                    && user != null && !user.trim().isEmpty()
                    && password != null && !password.isEmpty();
        }
    }

    static List<Profile> candidates(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String db = clean(p.getString(KEY_DB, ""));
        String user = clean(p.getString(KEY_USER, ""));
        String pass = readPassword(p);
        String lan = clean(p.getString(KEY_LAN_HOST, ""));
        String wan = clean(p.getString(KEY_WAN_HOST, ""));
        String lanPort = p.getString(KEY_LAN_PORT, "");
        String wanPort = p.getString(KEY_WAN_PORT, "");

        // Migrate the former single override endpoint without retaining compiled defaults.
        if (lan.isEmpty() && wan.isEmpty()) {
            String legacy = clean(p.getString("db_host_override", ""));
            if (legacy.isEmpty()) legacy = clean(p.getString("db_host", ""));
            if (!legacy.isEmpty()) {
                if (isPrivateAddress(legacy)) {
                    lan = legacy;
                    lanPort = p.getString("db_port_override", p.getString("db_port", ""));
                } else {
                    wan = legacy;
                    wanPort = p.getString("db_port_override", p.getString("db_port", ""));
                }
            }
        }
        if (db.isEmpty() || user.isEmpty() || pass.isEmpty()) return Collections.emptyList();

        List<Profile> out = new ArrayList<>(2);
        boolean lanFirst = preferLan(context, p.getString(KEY_LAST_ROUTE, ""));
        if (lanFirst) {
            add(out, "lan", lan, lanPort, db, user, pass);
            add(out, "wan", wan, wanPort, db, user, pass);
        } else {
            add(out, "wan", wan, wanPort, db, user, pass);
            add(out, "lan", lan, lanPort, db, user, pass);
        }
        return out;
    }

    static Profile selected(Context context) {
        List<Profile> all = candidates(context);
        return all.isEmpty() ? null : all.get(0);
    }

    static Profile draft(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean lanFirst = preferLan(context, p.getString(KEY_LAST_ROUTE, ""));
        String host = lanFirst ? clean(p.getString(KEY_LAN_HOST, "")) : clean(p.getString(KEY_WAN_HOST, ""));
        String portText = lanFirst ? p.getString(KEY_LAN_PORT, "") : p.getString(KEY_WAN_PORT, "");
        if (host.isEmpty()) {
            host = lanFirst ? clean(p.getString(KEY_WAN_HOST, "")) : clean(p.getString(KEY_LAN_HOST, ""));
            portText = lanFirst ? p.getString(KEY_WAN_PORT, "") : p.getString(KEY_LAN_PORT, "");
        }
        if (host.isEmpty()) {
            host = clean(p.getString("db_host_override", p.getString("db_host", "")));
            portText = p.getString("db_port_override", p.getString("db_port", ""));
        }
        String route = isPrivateAddress(host) ? "lan" : "wan";
        return new Profile(route, host, parsePort(portText), clean(p.getString(KEY_DB, "")),
                clean(p.getString(KEY_USER, "")), readPassword(p));
    }

    static String savedPassword(Context context) {
        return readPassword(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    static String savedUsername(Context context) {
        return clean(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_USER, ""));
    }

    static String savedDatabase(Context context) {
        return clean(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DB, ""));
    }

    static boolean testDatabase(String host, int port, String database, String user, String password)
            throws Exception {
        Profile candidate = new Profile(isPrivateAddress(host) ? "lan" : "wan", clean(host), port,
                clean(database), clean(user), password);
        try (java.sql.Connection c = connect(candidate, null)) {
            return c.isValid(5);
        }
    }

    private static void add(List<Profile> out, String kind, String host, String portText,
                            String db, String user, String pass) {
        if (host == null || host.isEmpty()) return;
        Profile profile = new Profile(kind, host, parsePort(portText), db, user, pass);
        if (profile.complete()) out.add(profile);
    }

    private static boolean preferLan(Context context, String lastRoute) {
        String transport = activeTransport(context);
        if ("cell".equals(transport)) return false;
        if ("wifi".equals(transport) || "eth".equals(transport) || "vpn".equals(transport)) return true;
        return !"wan".equals(lastRoute);
    }

    private static String activeTransport(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = network == null || cm == null ? null : cm.getNetworkCapabilities(network);
            if (caps == null) return "unknown";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cell";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "eth";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "vpn";
        } catch (Exception ignored) { }
        return "unknown";
    }

    static boolean saveVerified(Context context, String host, int port, String database,
                                String user, String password, String selectedRoute) {
        String cleanHost = clean(host);
        String cleanDb = clean(database);
        String cleanUser = clean(user);
        if (!validHost(cleanHost) || !validPort(port) || !validDatabase(cleanDb)
                || cleanUser.isEmpty() || password == null || password.isEmpty()) return false;

        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String route = "wan".equals(selectedRoute) ? "wan"
                : "lan".equals(selectedRoute) ? "lan"
                : (isPrivateAddress(cleanHost) ? "lan" : "wan");
        if (!writePassword(p, password)) return false;

        SharedPreferences.Editor edit = p.edit()
                .putString(KEY_DB, cleanDb)
                .putString(KEY_USER, cleanUser)
                .putString(KEY_LAST_ROUTE, route)
                .putString("db_host_override", "")
                .putString("db_port_override", "");
        if ("lan".equals(route)) {
            edit.putString(KEY_LAN_HOST, cleanHost).putString(KEY_LAN_PORT, String.valueOf(port));
        } else {
            edit.putString(KEY_WAN_HOST, cleanHost).putString(KEY_WAN_PORT, String.valueOf(port));
        }
        return edit.commit();
    }

    static void rememberSuccessfulRoute(Context context, String route) {
        try {
            if ("lan".equals(route) || "wan".equals(route)) {
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LAST_ROUTE, route).apply();
            }
        } catch (Exception ignored) { }
    }

    static String[] legacyArray(Context context) {
        Profile p = selected(context);
        return p == null ? new String[]{"", String.valueOf(DEFAULT_PORT), "", "", ""}
                : new String[]{p.host, String.valueOf(p.port), p.database, p.user, p.password};
    }

    static ConnectionResult listDatabases(String host, int port, String user, String password)
            throws Exception {
        if (!validHost(host) || !validPort(port) || user == null || user.trim().isEmpty()
                || password == null || password.isEmpty()) throw new IllegalArgumentException("مشخصات اتصال کامل نیست.");
        probe(host, port, 3500);
        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        String url = jdbcUrl(host, port, "master", "MEELANO-Setup");
        Properties props = properties(user, password);
        List<String> databases = new ArrayList<>();
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, props);
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM sys.databases WHERE database_id > 4 AND state = 0 ORDER BY name")) {
            ps.setQueryTimeout(12);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (validDatabase(name)) databases.add(name.trim());
                }
            }
        }
        return new ConnectionResult(databases);
    }

    static java.sql.Connection connect(Profile profile, String hostOverride) throws Exception {
        if (profile == null || !profile.complete()) throw new IllegalStateException("تنظیم اتصال SQL کامل نیست.");
        String host = hostOverride == null || hostOverride.trim().isEmpty() ? profile.host : hostOverride.trim();
        if (!validHost(host)) throw new IllegalArgumentException("نام یا IP سرور معتبر نیست.");
        probe(host, profile.port, 3500);
        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        java.sql.Connection connection = java.sql.DriverManager.getConnection(
                jdbcUrl(host, profile.port, profile.database, "MEELANO-Android"),
                properties(profile.user, profile.password));
        try (java.sql.Statement st = connection.createStatement()) {
            st.setQueryTimeout(8);
            st.execute("SELECT 1");
        } catch (Exception e) {
            try { connection.close(); } catch (Exception ignored) { }
            throw e;
        }
        // SQL Server 2008 R2 field servers cannot parse TRY_CONVERT/TRY_CAST; the wrapper
        // rewrites every statement of this connection into the guarded 2008 dialect.
        return LegacySql.wrap(connection, LegacySql.probe(connection, host + ":" + profile.port));
    }

    private static String jdbcUrl(String host, int port, String database, String appName) {
        String jdbcHost = host.indexOf(':') >= 0 && !host.startsWith("[") ? "[" + host + "]" : host;
        return "jdbc:jtds:sqlserver://" + jdbcHost + ":" + port + "/" + database
                + ";loginTimeout=8;socketTimeout=45;appName=" + appName + ";";
    }

    private static Properties properties(String user, String pass) {
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("charset", "UTF-8");
        p.setProperty("sendStringParametersAsUnicode", "true");
        return p;
    }

    static void probe(String host, int port, int timeoutMs) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), Math.max(500, timeoutMs));
        } catch (Exception e) {
            if (port == DEFAULT_PORT) throw new Exception("پورت پیش‌فرض ۱۴۳۳ باز نیست؛ اگر مودم پورت جایگزین را به ۱۴۳۳ فوروارد کرده، آن پورت را وارد کنید.", e);
            throw new Exception("اتصال TCP به سرور و پورت انتخاب‌شده برقرار نشد؛ آدرس، پورت و فوروارد مودم را بررسی کنید.", e);
        }
    }

    static List<ServerCandidate> discoverLocalServers() {
        Set<String> networks = local24Networks();
        Set<String> hosts = new LinkedHashSet<>();
        for (String network : networks) for (int i = 1; i <= 254; i++) hosts.add(network + i);
        List<String> all = new ArrayList<>(hosts);
        if (all.isEmpty()) return Collections.emptyList();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(32, all.size()));
        try {
            List<Callable<ServerCandidate>> jobs = new ArrayList<>();
            for (String host : all) jobs.add(() -> {
                for (int port : new int[]{DEFAULT_PORT, FORWARDED_PORT}) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 350);
                        String name = host;
                        try {
                            String resolved = InetAddress.getByName(host).getCanonicalHostName();
                            if (resolved != null && !resolved.trim().isEmpty() && !resolved.equals(host))
                                name = resolved + " · " + host;
                        } catch (Exception ignored) { }
                        return new ServerCandidate(host, port, name);
                    } catch (Exception ignored) { }
                }
                return null;
            });
            List<Future<ServerCandidate>> futures = pool.invokeAll(jobs, 8, TimeUnit.SECONDS);
            List<ServerCandidate> out = new ArrayList<>();
            for (Future<ServerCandidate> future : futures) {
                if (!future.isDone() || future.isCancelled()) continue;
                try {
                    ServerCandidate candidate = future.get();
                    if (candidate != null) out.add(candidate);
                } catch (Exception ignored) { }
                if (out.size() >= 24) break;
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<String> local24Networks() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return out;
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                    String name = ni.getName() == null ? "" : ni.getName().toLowerCase(Locale.ROOT);
                    if (!(name.startsWith("wlan") || name.startsWith("wl") || name.startsWith("eth")
                            || name.startsWith("en") || name.startsWith("ap"))) continue;
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                        String ip = address.getHostAddress();
                        int dot = ip.lastIndexOf('.');
                        if (dot > 0) out.add(ip.substring(0, dot + 1));
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    static boolean isPrivateAddress(String host) {
        String value = clean(host);
        if (value.isEmpty()) return false;
        if (!value.contains(".") && !value.contains(":")) return true; // local Windows/SQL host name
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress();
        } catch (Exception ignored) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.endsWith(".local") || lower.endsWith(".lan");
        }
    }

    static boolean validHost(String host) {
        String value = clean(host);
        return !value.isEmpty() && value.length() <= 253 && !value.contains(";")
                && !value.contains("/") && !value.contains("\\") && !value.contains(" ");
    }

    static boolean validDatabase(String database) {
        if (database == null) return false;
        String value = database.trim();
        if (value.isEmpty() || value.length() > 128 || value.contains(";")
                || value.contains("/") || value.contains("\\")) return false;
        return true;
    }

    static boolean validPort(int port) { return port >= 1 && port <= 65535; }

    static int parsePort(String raw) {
        try {
            String normalized = ir.meelano.android.MeelanoTaxDb.foldDigits(raw == null ? "" : raw.trim());
            if (normalized.isEmpty()) return DEFAULT_PORT;
            int port = Integer.parseInt(normalized);
            return validPort(port) ? port : DEFAULT_PORT;
        } catch (Exception ignored) {
            return DEFAULT_PORT;
        }
    }

    static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String readPassword(SharedPreferences prefs) {
        String stored = prefs.getString(KEY_PASSWORD, "");
        if (stored.isEmpty() || !stored.startsWith(CIPHER_PREFIX)) return "";
        try {
            String[] parts = stored.substring(CIPHER_PREFIX.length()).split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(false), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean writePassword(SharedPreferences prefs, String password) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(true));
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String value = CIPHER_PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            return prefs.edit().putString(KEY_PASSWORD, value).commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static SecretKey secretKey(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(SECRET_KEY)) return (SecretKey) store.getKey(SECRET_KEY, null);
        if (!create) throw new IllegalStateException("SQL credential key is unavailable");
        KeyGenerator generator = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(SECRET_KEY,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                        | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    static final class ServerCandidate {
        final String host;
        final int port;
        final String label;
        ServerCandidate(String host, int port, String label) {
            this.host = host;
            this.port = port;
            this.label = label;
        }
    }

    static final class ConnectionResult {
        final List<String> databases;
        ConnectionResult(List<String> databases) { this.databases = databases; }
    }
}
