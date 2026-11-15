package ir.meelano.windowsinvoice.data;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Finds SQL Server endpoints reachable from this Windows machine: the addresses the operator
 * types (server name or IP, default port 1433, or a forwarded port such as 5050) and a bounded
 * scan of the machine's own IPv4 /24 blocks. Also lists the user databases of a reachable server.
 */
public final class WindowsServerDiscovery {
    private WindowsServerDiscovery() { }

    public static final int DEFAULT_PORT = WindowsSqlSettings.DEFAULT_PORT;
    public static final int FORWARDED_PORT = WindowsSqlSettings.FORWARDED_PORT;
    public static final int[] PROBE_PORTS = {DEFAULT_PORT, FORWARDED_PORT};
    private static final int PROBE_TIMEOUT_MS = 400;
    private static final int MAX_RESULTS = 24;

    public static final class Candidate {
        public final String host;
        public final int port;
        public final String label;

        public Candidate(String host, int port, String label) {
            this.host = host;
            this.port = port;
            this.label = label;
        }
    }

    /** Local IPv4 /24 network prefixes of up, non-loopback interfaces. */
    public static List<String> localSubnets() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return new ArrayList<>(out);
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                try {
                    if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                    Enumeration<InetAddress> addresses = network.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!(address instanceof Inet4Address) || !address.isSiteLocalAddress()) continue;
                        String prefix = subnetPrefix(address.getHostAddress());
                        if (!prefix.isEmpty()) out.add(prefix);
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(out);
    }

    /**
     * The /24 scan prefix of a dotted IPv4 address ("192.168.1.42" → "192.168.1."), or "" when the
     * text is not a plain IPv4 address. Pure, so a unit test can pin the derivation without depending
     * on the network interfaces of the machine that runs it.
     */
    public static String subnetPrefix(String ip) {
        String value = ip == null ? "" : ip.trim();
        if (value.isEmpty()) return "";
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return "";
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return "";
            for (int at = 0; at < part.length(); at++) {
                if (part.charAt(at) < '0' || part.charAt(at) > '9') return "";
            }
            if (Integer.parseInt(part) > 255) return "";
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".";
    }

    /** True when host:port accepts a TCP connection. */
    public static boolean reachable(String host, int port) {
        return reachable(host, port, PROBE_TIMEOUT_MS);
    }

    public static boolean reachable(String host, int port, int timeoutMs) {
        if (!WindowsSqlSettings.validHost(host) || !WindowsSqlSettings.validPort(port)) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), Math.max(200, timeoutMs));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** First answering probe port of a typed host (1433, then 5050), or 0 when none answers. */
    public static int openPortOf(String host) {
        for (int port : PROBE_PORTS) if (reachable(host, port, 900)) return port;
        return 0;
    }

    /**
     * Bounded scan of the machine's own /24 blocks on the SQL ports. A typed address is always
     * probed first, so an operator's own entry can never be crowded out.
     */
    public static List<Candidate> discover(String typedHost) {
        Set<String> hosts = new LinkedHashSet<>();
        String typed = WindowsSqlSettings.clean(typedHost);
        if (!typed.isEmpty()) hosts.add(typed);
        for (String subnet : localSubnets())
            for (int octet = 1; octet <= 254; octet++) hosts.add(subnet + octet);
        List<String> all = new ArrayList<>(hosts);
        if (all.isEmpty()) return Collections.emptyList();

        List<Callable<Candidate>> jobs = new ArrayList<>();
        for (String host : all) jobs.add(() -> {
            for (int port : PROBE_PORTS) {
                if (!reachable(host, port)) continue;
                return new Candidate(host, port, describe(host));
            }
            return null;
        });

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(32, jobs.size()));
        try {
            List<Future<Candidate>> futures = pool.invokeAll(jobs, 10, TimeUnit.SECONDS);
            List<Candidate> out = new ArrayList<>();
            for (Future<Candidate> future : futures) {
                if (!future.isDone() || future.isCancelled()) continue;
                try {
                    Candidate candidate = future.get();
                    if (candidate != null) out.add(candidate);
                } catch (Exception ignored) { }
                if (out.size() >= MAX_RESULTS) break;
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        } finally {
            pool.shutdownNow();
        }
    }

    /** User database names visible to the login (via master). Empty when the server or login fails. */
    public static List<String> listDatabases(String host, int port, String user, String password) {
        List<String> out = new ArrayList<>();
        if (!WindowsSqlSettings.validHost(host) || !WindowsSqlSettings.validPort(port)
                || user == null || user.isBlank() || password == null || password.isEmpty()) return out;
        String url = SqlServerUrl.build(host, port, "master", !SqlServerUrl.requiresEncryption(host));
        try (Connection c = DriverManager.getConnection(url, user.trim(), password);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM sys.databases WHERE database_id > 4 AND state = 0 ORDER BY name")) {
            ps.setQueryTimeout(15);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (WindowsSqlSettings.validDatabase(name)) out.add(name.trim());
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Opens the selected database and proves it answers; the caller closes the connection. */
    public static Connection open(String host, int port, String database,
                                  String user, String password) throws Exception {
        String url = SqlServerUrl.build(host, port, database, !SqlServerUrl.requiresEncryption(host));
        Connection connection = DriverManager.getConnection(url, user.trim(), password);
        try (java.sql.Statement st = connection.createStatement()) {
            st.setQueryTimeout(10);
            st.execute("SELECT 1");
        } catch (Exception e) {
            try { connection.close(); } catch (Exception ignored) { }
            throw e;
        }
        return connection;
    }

    private static String describe(String host) {
        try {
            String canonical = InetAddress.getByName(host).getCanonicalHostName();
            if (canonical != null && !canonical.isBlank() && !canonical.equalsIgnoreCase(host))
                return canonical + " · " + host;
        } catch (Exception ignored) { }
        return host;
    }

    /** Ports probed for a typed address: an explicit host:port always wins. */
    public static int resolvePort(String portText, String typedAddress) {
        int typed = typedPort(typedAddress);
        return typed > 0 ? typed : WindowsSqlSettings.parsePort(portText);
    }

    /** Port embedded in a typed address ("1.2.3.4:5050"), or 0 when there is none. */
    public static int typedPort(String value) {
        try {
            String text = WindowsSqlSettings.clean(value);
            int scheme = text.indexOf("://");
            if (scheme >= 0) text = text.substring(scheme + 3);
            int slash = text.indexOf('/');
            if (slash >= 0) text = text.substring(0, slash);
            int colon = text.lastIndexOf(':');
            if (colon <= 0 || text.indexOf(':') != colon || colon < text.lastIndexOf(']')) return 0;
            int port = Integer.parseInt(text.substring(colon + 1).trim().toLowerCase(Locale.ROOT));
            return WindowsSqlSettings.validPort(port) ? port : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Strips scheme, port, path and instance from a typed server address. */
    public static String cleanHost(String value) {
        String text = WindowsSqlSettings.clean(value);
        int scheme = text.indexOf("://");
        if (scheme >= 0) text = text.substring(scheme + 3);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        int colon = text.lastIndexOf(':');
        if (colon > 0 && text.indexOf(':') == colon) text = text.substring(0, colon);
        int backslash = text.indexOf('\\');
        if (backslash > 0) text = text.substring(0, backslash);
        return text.trim();
    }
}
