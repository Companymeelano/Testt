package ir.meelano.manager.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Network-state helpers (activation works on any connection; only the server
 * link may need the shop Wi-Fi when the seller's server is LAN-only).
 * No permissions beyond INTERNET/ACCESS_NETWORK_STATE (both install-time).
 */
public final class Net {
    private Net() { }

    private static NetworkCapabilities caps(Context c) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network n = cm.getActiveNetwork();
            if (n == null) return null;
            return cm.getNetworkCapabilities(n);
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the phone is on Wi-Fi (network status row). */
    public static boolean wifi(Context c) {
        try {
            NetworkCapabilities nc = caps(c);
            return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    /** True when any usable network (Wi-Fi / mobile / ethernet) is active. */
    public static boolean online(Context c) {
        try {
            NetworkCapabilities nc = caps(c);
            return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return false;
        }
    }
}
