package ir.meelano.manager.core;

import java.net.Inet4Address;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Smart LAN discovery (v31): the customer types ONE server address and the
 * app finds the reachable SQL Server itself — the typed host first, then the
 * same host octet transplanted into the phone's own subnet, then the gateway
 * and the usual server octets. No permission needed (plain interface
 * enumeration + TCP probes). Port 1433 stays hidden from the customer.
 */
public final class LanDiscover {
    private LanDiscover() { }

    /** SQL Server port — hidden from the customer, always this. */
    public static final int PORT = 1433;

    /** Common forwarded port (router forwards 5050 → 1433 on the dedicated IP). */
    public static final int PORT_FWD = 5050;

    /** Ports probed while discovering a server: the default first, then the forwarded one. */
    public static final int[] PROBE_PORTS = {PORT, PORT_FWD};

    /**
     * Port typed as part of the address ("1.2.3.4:5050"), or 0 when the
     * address carries none. The customer may type the forwarded port into
     * the address itself — it is honored instead of being dropped.
     */
    public static int typedPort(String in) {
        try {
            String t = in == null ? "" : in.trim();
            int s = t.indexOf("://");
            if (s >= 0) t = t.substring(s + 3);
            int slash = t.indexOf('/');
            if (slash >= 0) t = t.substring(0, slash);
            t = t.trim();
            int colon = t.lastIndexOf(':');
            if (colon > 0 && t.indexOf(':') == colon && colon > t.lastIndexOf(']')) {
                int v = Integer.parseInt(ir.meelano.manager.core.Money.en(
                        t.substring(colon + 1).trim()).trim());
                return (v >= 1 && v <= 65535) ? v : 0;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static final int TCP_MS = 600;
    private static final String[] COMMON = {
            "1", "2", "3", "5", "7", "10", "11", "20", "21",
            "100", "101", "110", "200", "201", "254"};

    /** Strip scheme / port / path / spaces from a typed server address. */
    public static String clean(String in) {
        try {
            String t = in == null ? "" : in.trim();
            int s = t.indexOf("://");
            if (s >= 0) t = t.substring(s + 3);
            int slash = t.indexOf('/');
            if (slash >= 0) t = t.substring(0, slash);
            t = t.trim();
            // host:port or host\instance — keep the host part only
            int colon = t.lastIndexOf(':');
            if (colon > 0 && t.indexOf(':') == colon) t = t.substring(0, colon);
            int bs = t.indexOf('\\');
            if (bs > 0) t = t.substring(0, bs);
            return t.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Local /24 bases (e.g. "192.168.1.") from real LAN interfaces only
     * (wlan/eth/ap — never mobile rmnet or VPN tun). No permission needed.
     */
    public static List<String> localSubnets() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            if (nis == null) return new ArrayList<>(out);
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                    String nm = ni.getName() == null ? "" : ni.getName().toLowerCase(java.util.Locale.US);
                    if (!(nm.startsWith("wlan") || nm.startsWith("wl")
                            || nm.startsWith("eth") || nm.startsWith("enp")
                            || nm.startsWith("enx") || nm.startsWith("ens")
                            || nm.startsWith("ap"))) continue;
                    Enumeration<InetAddress> ads = ni.getInetAddresses();
                    while (ads.hasMoreElements()) {
                        InetAddress ad = ads.nextElement();
                        if (!(ad instanceof Inet4Address)) continue;
                        if (!ad.isSiteLocalAddress()) continue;
                        String ip = ad.getHostAddress();
                        int last = ip == null ? -1 : ip.lastIndexOf('.');
                        if (last > 0) out.add(ip.substring(0, last + 1));
                    }
                } catch (Exception ignored) { Diag.ignored("LanDiscover:85", ignored); }
            }
        } catch (Exception ignored) { Diag.ignored("LanDiscover:87", ignored); }
        return new ArrayList<>(out);
    }

    /**
     * Ordered TCP-responding SQL candidates. Bounded (~4s): the typed host
     * first, then transplanted / gateway / common octets in the phone's
     * own subnet(s). Call off the UI thread.
     */
    public static List<String> find(String typedIp, int port) {
        List<String> ordered = new ArrayList<>();
        try {
            String t = clean(typedIp);
            Set<String> cands = new LinkedHashSet<>();
            if (!t.isEmpty()) cands.add(t);
            String hostOct = lastOctet(t);
            for (String sub : localSubnets()) {
                if (hostOct != null && !(sub + hostOct).equals(t)) cands.add(sub + hostOct);
                for (String o : COMMON) {
                    String h = sub + o;
                    if (!h.equals(t)) cands.add(h);
                }
            }
            List<String> all = new ArrayList<>(cands);
            if (all.isEmpty()) return ordered;
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(24, all.size()));
            try {
                List<Callable<Boolean>> tasks = new ArrayList<>();
                for (final String h : all) {
                    tasks.add(() -> SmartLink.probeTcp(h, port, TCP_MS) == null);
                }
                List<Future<Boolean>> fs = pool.invokeAll(tasks, 4, TimeUnit.SECONDS);
                for (int i = 0; i < all.size() && i < fs.size(); i++) {
                    try {
                        Future<Boolean> f = fs.get(i);
                        if (f.isDone() && !f.isCancelled() && Boolean.TRUE.equals(f.get()))
                            ordered.add(all.get(i));
                    } catch (Exception ignored) { Diag.ignored("LanDiscover:124", ignored); }
                    if (ordered.size() >= 8) break;
                }
            } finally {
                pool.shutdownNow();
            }
        } catch (Exception ignored) { Diag.ignored("LanDiscover:130", ignored); }
        return ordered;
    }

    /** First answering SQL port of a host (1433, else 5050), or 0 when none. */
    public static int openPortOf(String host) {
        for (int port : PROBE_PORTS) {
            if (SmartLink.probeTcp(host, port, 700) == null) return port;
        }
        return 0;
    }

    /**
     * User database names on a reachable server (via master). Empty when
     * the login fails or nothing is visible. Call off the UI thread.
     */
    public static java.util.List<String> listDatabases(String host, int port,
            String user, String pass) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Connection c = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            c = ir.meelano.manager.data.Atiran.open(host, port, "master",
                    user, pass, 3000, 6, false);
            ps = c.prepareStatement("SELECT name FROM sys.databases "
                    + "WHERE database_id > 4 AND state = 0 ORDER BY name");
            rs = ps.executeQuery();
            while (rs.next()) {
                String nm = null;
                try {
                    nm = rs.getString(1);
                } catch (Exception ignored) { Diag.ignored("LanDiscover:154", ignored); }
                if (nm != null && !nm.trim().isEmpty()) out.add(nm.trim());
            }
        } catch (Exception ignored) { Diag.ignored("LanDiscover:157", ignored); }
        try {
            if (rs != null) rs.close();
        } catch (Exception ignored) { Diag.ignored("LanDiscover:160", ignored); }
        try {
            if (ps != null) ps.close();
        } catch (Exception ignored) { Diag.ignored("LanDiscover:163", ignored); }
        try {
            if (c != null) c.close();
        } catch (Exception ignored) { Diag.ignored("LanDiscover:166", ignored); }
        return out;
    }

    private static String lastOctet(String ipv4) {
        try {
            if (ipv4 == null) return null;
            String[] p = ipv4.split("\\.");
            if (p.length != 4) return null;
            int v = Integer.parseInt(p[3]);
            if (v < 0 || v > 254) return null;
            for (int i = 0; i < 3; i++) Integer.parseInt(p[i]);
            return p[3];
        } catch (Exception e) {
            return null;
        }
    }
}
