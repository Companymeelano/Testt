package ir.meelano.manager.data;

import android.content.Context;
import android.content.SharedPreferences;

import ir.meelano.manager.core.Money;

/** App settings: connection overrides + optional PIN lock. */
public final class Settings {
    private final SharedPreferences p;

    public Settings(Context c) {
        p = c.getSharedPreferences("meelano_manager7", Context.MODE_PRIVATE);
    }

    public String host() { return p.getString("db_host", "").trim(); }
    public String port() { return p.getString("db_port", "").trim(); }
    public String db() { return p.getString("db_name", "").trim(); }
    public String user() { return p.getString("db_user", "").trim(); }
    public String pass() { return p.getString("db_pass", ""); }

    public String effHost() { String h = host(); return h.isEmpty() ? Atiran.defaultHost() : h; }
    public String effDb() { String d = db(); return d.isEmpty() ? Atiran.defaultDb() : d; }
    public String effUser() { String u = user(); return u.isEmpty() ? Atiran.defaultUser() : u; }
    public String effPass() { String s = pass(); return s.isEmpty() ? Atiran.defaultPass() : s; }
    public int effPort() {
        try {
            int v = Integer.parseInt(Money.en(port()).trim());
            return v > 0 ? v : Atiran.DEFAULT_PORT;
        } catch (Exception ignored) {
            return Atiran.DEFAULT_PORT;
        }
    }

    public void saveConnection(String host, String port, String db, String user, String pass) {
        p.edit().putString("db_host", host == null ? "" : host.trim())
                .putString("db_port", port == null ? "" : port.trim())
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .putString("db_pass", pass == null ? "" : pass).apply();
    }

    public boolean directConn() { return p.getBoolean("net_direct", false); }

    public void setDirectConn(boolean on) { p.edit().putBoolean("net_direct", on).apply(); }

    public boolean pinEnabled() { return p.getBoolean("pin_on", false); }
    public String pinHash() { return p.getString("pin_hash", ""); }

    public void setPin(String pinOrNull) {
        String pin = pinOrNull == null ? null : Money.en(pinOrNull).trim();
        if (pin == null || pin.length() < 4) {
            p.edit().putBoolean("pin_on", false).putString("pin_hash", "").apply();
        } else {
            p.edit().putBoolean("pin_on", true).putString("pin_hash", sha(pin)).apply();
        }
    }

    public boolean checkPin(String pin) {
        if (!pinEnabled()) return true;
        return pin != null && sha(Money.en(pin).trim()).equals(pinHash());
    }

    private static String sha(String s) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(("meelano7:" + s).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "x" + s;
        }
    }
}
