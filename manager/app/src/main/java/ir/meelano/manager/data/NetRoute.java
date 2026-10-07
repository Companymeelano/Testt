package ir.meelano.manager.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * VPN awareness + optional direct routing.
 *
 * When a VPN (فیلترشکن) routes ALL traffic into its tunnel, a SQL Server on the
 * local network / national intranet becomes unreachable and every screen fails.
 * Android lets an app pin its own sockets to a chosen network, so this helper:
 *  - detects an active VPN transport ({@link #isVpnActive}),
 *  - optionally binds the process to the best NON-VPN network (Wi-Fi first)
 *    while a connection is opened ({@link #bindDirect}/{@link #unbind}).
 *
 * Binding is reference-counted: overlapping background tasks share one binding
 * and the last one to finish releases it.
 */
public final class NetRoute {
    private NetRoute() { }

    private static int binds = 0;

    /** True when any active network is a VPN transport. */
    public static boolean isVpnActive(Context ctx) {
        try {
            ConnectivityManager cm = cm(ctx);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    /**
     * Bind this process to the best non-VPN network (Wi-Fi › Ethernet › cellular).
     * Returns true when bound — the caller MUST call {@link #unbind} in a finally block.
     */
    public static synchronized boolean bindDirect(Context ctx) {
        try {
            ConnectivityManager cm = cm(ctx);
            if (cm == null) return false;
            Network best = null;
            int bestScore = -1;
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null) continue;
                if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;
                if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                int score;
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score = 3;
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) score = 2;
                else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) score = 1;
                else continue;
                if (score > bestScore) {
                    bestScore = score;
                    best = n;
                }
            }
            if (best == null) return false;
            if (binds == 0 && !cm.bindProcessToNetwork(best)) return false;
            binds++;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Release one direct-routing binding (see {@link #bindDirect}). */
    public static synchronized void unbind(Context ctx) {
        try {
            if (binds > 0) binds--;
            if (binds == 0) {
                ConnectivityManager cm = cm(ctx);
                if (cm != null) cm.bindProcessToNetwork(null);
            }
        } catch (Exception ignored) { }
    }

    /** True when the raw exception smells like a network/routing failure (pre-diagnosis). */
    public static boolean looksLikeNetwork(Exception e) {
        if (e == null) return false;
        String l = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.US);
        return l.contains("timeout") || l.contains("timed out") || l.contains("refused")
                || l.contains("unreachable") || l.contains("no route") || l.contains("network")
                || l.contains("unknown host") || l.contains("no such host")
                || l.contains("reset") || l.contains("broken pipe") || l.contains("econn");
    }

    private static ConnectivityManager cm(Context ctx) {
        if (ctx == null) return null;
        try {
            return (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        } catch (Exception ignored) {
            return null;
        }
    }
}
