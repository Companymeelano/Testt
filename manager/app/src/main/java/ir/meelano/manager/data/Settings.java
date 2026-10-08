package ir.meelano.manager.data;

import android.content.Context;
import android.content.SharedPreferences;

import ir.meelano.manager.core.Money;

/** App settings: connection + PIN lock + luxury theme + notifications + offline cache. */
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

    // ---------------- luxury theme ----------------
    /** "dark" (Midnight Gold) or "light" (Ivory Royal). */
    public String themeMode() {
        String m = p.getString("theme_mode", "dark");
        return "light".equals(m) ? "light" : "dark";
    }

    public void setThemeMode(String mode) {
        p.edit().putString("theme_mode", "light".equals(mode) ? "light" : "dark").apply();
    }

    /** Accent key: gold / emerald / sapphire / ruby / violet / teal. */
    public String themeAccent() {
        String k = p.getString("theme_accent", "gold");
        for (String ok : new String[]{"gold", "emerald", "sapphire", "ruby", "violet", "teal"})
            if (ok.equals(k)) return k;
        return "gold";
    }

    public void setThemeAccent(String key) {
        p.edit().putString("theme_accent", key == null ? "gold" : key).apply();
    }

    // ---------------- smart notifications ----------------
    public boolean notifOn() { return p.getBoolean("notif_on", true); }

    public void setNotifOn(boolean on) { p.edit().putBoolean("notif_on", on).apply(); }

    // ---------------- shop card ----------------
    public String shopName() { return p.getString("shop_name", "").trim(); }

    public void setShopName(String name) {
        p.edit().putString("shop_name", name == null ? "" : name.trim()).apply();
    }

    public String shopPhone() { return p.getString("shop_phone", "").trim(); }

    public void setShopPhone(String phone) {
        p.edit().putString("shop_phone", phone == null ? "" : phone.trim()).apply();
    }

    public String shopAddr() { return p.getString("shop_addr", "").trim(); }

    public void setShopAddr(String addr) {
        p.edit().putString("shop_addr", addr == null ? "" : addr.trim()).apply();
    }

    // ---------------- offline home cache (compact JSON) ----------------
    public String homeCache() { return p.getString("home_cache", ""); }

    public void saveHomeCache(String json) {
        p.edit().putString("home_cache", json == null ? "" : json).apply();
    }

    // ---------------- morning report + backup reminder ----------------
    public boolean morningOn() { return p.getBoolean("morning_on", true); }

    public void setMorningOn(boolean on) { p.edit().putBoolean("morning_on", on).apply(); }

    public boolean backupOn() { return p.getBoolean("backup_on", true); }

    public void setBackupOn(boolean on) { p.edit().putBoolean("backup_on", on).apply(); }

    /** Last seen bounced-cheque count (-1 = never checked): only increases notify. */
    public long lastBouncedN() { return p.getLong("last_bounced_n", -1); }

    public void setLastBouncedN(long n) { p.edit().putLong("last_bounced_n", n).apply(); }

    // ---------------- fingerprint ----------------
    public boolean fpOn() { return p.getBoolean("fp_on", false); }

    public void setFpOn(boolean on) { p.edit().putBoolean("fp_on", on).apply(); }

    // ---------------- home dashboard order ----------------
    public static final String HOME_ORDER_DEFAULT = "kpis,alerts,trend,donut,debtors,visitors,due,shortcuts";

    public String homeOrder() {
        String v = p.getString("home_order", HOME_ORDER_DEFAULT);
        return v == null || v.trim().isEmpty() ? HOME_ORDER_DEFAULT : v;
    }

    public void setHomeOrder(String csv) {
        p.edit().putString("home_order", csv == null ? HOME_ORDER_DEFAULT : csv).apply();
    }
}
