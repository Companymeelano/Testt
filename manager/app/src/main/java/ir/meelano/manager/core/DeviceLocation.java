package ir.meelano.manager.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device position for the weather card. Uses the last known fix when it is recent, otherwise asks
 * the network provider once. Everything here degrades to "no location" (the caller then uses the configured
 * store-city fallback and labels it) when permission is missing, location is off, or no fix arrives in time.
 */
public final class DeviceLocation {
    private DeviceLocation() { }

    /** Last known fix older than this is not trusted without a fresh request. */
    private static final long RECENT_MS = 6L * 60 * 60 * 1000;

    public static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Best last-known fix from any provider, or null. */
    public static Location lastKnown(Context c) {
        if (!granted(c)) return null;
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Blocking: call from a worker thread only. Returns a recent fix, a fresh one within waitMs,
     * or the stale last-known fix, or null.
     */
    public static Location resolve(Context c, long waitMs) {
        Location last = lastKnown(c);
        if (usableForWeather(last, System.currentTimeMillis())) return last;
        // Never send a months-old last-known coordinate to the weather provider as if it were current.
        if (!granted(c)) return null;
        final LocationManager lm;
        try {
            lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        } catch (Exception e) {
            return null;
        }
        if (lm == null) return null;
        String provider = null;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        } catch (Exception e) {
            return null;
        }
        if (provider == null) return null;

        final Location[] out = {null};
        final CountDownLatch latch = new CountDownLatch(1);
        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                out[0] = loc;
                latch.countDown();
            }
        };
        final String p = provider;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                lm.requestSingleUpdate(p, listener, Looper.getMainLooper());
            } catch (SecurityException e) {
                latch.countDown();
            }
        });
        try {
            latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            lm.removeUpdates(listener);
        } catch (Exception ignored) {
            // nothing to remove
        }
        return usableForWeather(out[0], System.currentTimeMillis()) ? out[0] : null;
    }

    /** Reject invalid, stale, future-dated, or too imprecise fixes before sending coordinates for weather. */
    static boolean usableForWeather(Location location, long nowMs) {
        if (location == null) return false;
        double lat = location.getLatitude(), lon = location.getLongitude();
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            return false;
        long age = nowMs - location.getTime();
        if (age < -60_000L || age > RECENT_MS) return false;
        if (location.hasAccuracy()) {
            float accuracy = location.getAccuracy();
            if (!Float.isFinite(accuracy) || accuracy < 0 || accuracy > 50_000f) return false;
        }
        return true;
    }

    /** City or district name for the coordinates, or null when the geocoder has no answer. Worker thread only. */
    public static String placeName(Context c, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return null;
            List<Address> list = new Geocoder(c, new Locale("fa", "IR")).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            String name = a.getLocality();
            if (name == null || name.isEmpty()) name = a.getSubAdminArea();
            if (name == null || name.isEmpty()) name = a.getAdminArea();
            return name == null || name.isEmpty() ? null : name;
        } catch (Exception e) {
            return null;
        }
    }
}
