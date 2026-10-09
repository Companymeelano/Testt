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

    /** Inside-network address (shop Wi-Fi / cable): raw, never displayed. */
    public String lan() { return p.getString("db_lan", "").trim(); }

    /** Outside-network address (mobile data / public IP): raw, never displayed. */
    public String wan() { return p.getString("db_wan", "").trim(); }

    /** Server address with all but the last segment masked (safe to display). */
    public String maskedHost() {
        return mask(effHost().trim());
    }

    /** Masked inside address ("" when unset). */
    public String maskedLan() { return lan().isEmpty() ? "" : mask(lan()); }

    /** Masked outside address ("" when unset). */
    public String maskedWan() { return wan().isEmpty() ? "" : mask(wan()); }

    private static String mask(String h) {
        return ir.meelano.manager.core.SmartLink.maskHost(h);
    }

    /** True once the seller's connection card has been applied on this phone. */
    public boolean connConfigured() {
        return !lan().isEmpty() || !wan().isEmpty() || !host().isEmpty();
    }
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

    /** Save both smart-link profiles at once (either address may be empty). */
    public void saveSmart(String lan, String wan, String port, String db, String user, String pass) {
        p.edit().putString("db_lan", lan == null ? "" : lan.trim())
                .putString("db_wan", wan == null ? "" : wan.trim())
                .putString("db_port", port == null ? "" : port.trim())
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .putString("db_pass", pass == null ? "" : pass).apply();
    }

    /**
     * Store one address into the matching profile (private → inside,
     * public → outside) without touching the other profile.
     */
    public void placeHost(String host, String port, String db, String user, String pass) {
        String h = host == null ? "" : host.trim();
        SharedPreferences.Editor e = p.edit();
        if (!h.isEmpty()) {
            Boolean priv = ir.meelano.manager.core.SmartLink.isPrivate(h);
            if (priv != null && !priv) e.putString("db_wan", h);
            else e.putString("db_lan", h);
        }
        e.putString("db_port", port == null ? "" : port.trim())
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .putString("db_pass", pass == null ? "" : pass).apply();
    }

    /** One-time migration of the legacy single address into lan/wan. */
    public void migrateLegacyHost() {
        try {
            if (!lan().isEmpty() || !wan().isEmpty()) return;
            String h = host();
            if (h.isEmpty()) return;
            placeHost(h, port(), db(), user(), pass());
        } catch (Exception ignored) { }
    }

    /** Smart-link mode: "auto" (default), "lan" or "wan". */
    public String linkMode() {
        String m = p.getString("link_mode", "auto");
        if ("lan".equals(m) || "wan".equals(m)) return m;
        return "auto";
    }

    public void setLinkMode(String mode) {
        p.edit().putString("link_mode", "lan".equals(mode) || "wan".equals(mode) ? mode : "auto").apply();
    }

    /** Profile that worked last ("lan" / "wan" / ""). */
    public String linkLast() { return p.getString("link_last", ""); }

    public void setLinkLast(String kind) {
        p.edit().putString("link_last", "wan".equals(kind) ? "wan" : ("lan".equals(kind) ? "lan" : "")).apply();
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

    // ---------------- font size + seasonal theme (v14) ----------------
    public boolean bigFont() { return p.getBoolean("big_font", false); }

    public void setBigFont(boolean on) { p.edit().putBoolean("big_font", on).apply(); }

    public boolean seasonalOn() { return p.getBoolean("seasonal_on", true); }

    public void setSeasonalOn(boolean on) { p.edit().putBoolean("seasonal_on", on).apply(); }

    // ---------------- morning report + backup reminder ----------------
    public boolean morningOn() { return p.getBoolean("morning_on", true); }

    public void setMorningOn(boolean on) { p.edit().putBoolean("morning_on", on).apply(); }

    public boolean weeklyOn() { return p.getBoolean("weekly_on", true); }

    public void setWeeklyOn(boolean on) { p.edit().putBoolean("weekly_on", on).apply(); }

    /** Smart-export backup bookkeeping. */
    public String backupLast() { return p.getString("backup_last", ""); }

    public String backupSize() { return p.getString("backup_size", ""); }

    public void saveBackupInfo(String whenFa, String sizeFa) {
        p.edit().putString("backup_last", whenFa == null ? "" : whenFa)
                .putString("backup_size", sizeFa == null ? "" : sizeFa).apply();
    }

    public boolean backupOn() { return p.getBoolean("backup_on", true); }

    public void setBackupOn(boolean on) { p.edit().putBoolean("backup_on", on).apply(); }

    /** Last seen bounced-cheque count (-1 = never checked): only increases notify. */
    public long lastBouncedN() { return p.getLong("last_bounced_n", -1); }

    public void setLastBouncedN(long n) { p.edit().putLong("last_bounced_n", n).apply(); }

    // ---------------- fingerprint ----------------
    public boolean fpOn() { return p.getBoolean("fp_on", false); }

    public void setFpOn(boolean on) { p.edit().putBoolean("fp_on", on).apply(); }

    /** Warehouse direct posting to Atiran (default OFF = local drafts only). */
    public boolean whDirect() { return p.getBoolean("wh_direct", false); }

    public void setWhDirect(boolean on) { p.edit().putBoolean("wh_direct", on).apply(); }

    // ---------------- home dashboard order ----------------
    public static final String HOME_ORDER_DEFAULT = "kpis,alerts,trend,donut,debtors,visitors,due,shortcuts";

    public String homeOrder() {
        String v = p.getString("home_order", HOME_ORDER_DEFAULT);
        return v == null || v.trim().isEmpty() ? HOME_ORDER_DEFAULT : v;
    }

    public void setHomeOrder(String csv) {
        p.edit().putString("home_order", csv == null ? HOME_ORDER_DEFAULT : csv).apply();
    }

    // ---------------- stock alerts + auto backup (v18) ----------------
    public boolean stockOn() { return p.getBoolean("stock_on", true); }

    public void setStockOn(boolean on) { p.edit().putBoolean("stock_on", on).apply(); }

    public boolean abOn() { return p.getBoolean("ab_on", false); }

    public void setAbOn(boolean on) { p.edit().putBoolean("ab_on", on).apply(); }

    /** Nightly auto-backup hour, 0..23 (default 2 AM). */
    public int abHour() {
        try {
            int h = p.getInt("ab_hour", 2);
            return h < 0 || h > 23 ? 2 : h;
        } catch (Exception e) {
            return 2;
        }
    }

    public void setAbHour(int h) {
        p.edit().putInt("ab_hour", h < 0 ? 0 : (h > 23 ? 23 : h)).apply();
    }

    // ---------------- end-of-day summary + sales guard (v21) ----------------
    public boolean eodOn() { return p.getBoolean("eod_on", true); }

    public void setEodOn(boolean on) { p.edit().putBoolean("eod_on", on).apply(); }

    public String eodText() { return p.getString("eod_text", ""); }

    public String eodDate() { return p.getString("eod_date", ""); }

    public void saveEod(String text, String date) {
        p.edit().putString("eod_text", text == null ? "" : text)
                .putString("eod_date", date == null ? "" : date).apply();
    }

    public boolean guardOn() { return p.getBoolean("guard_on", false); }

    public void setGuardOn(boolean on) { p.edit().putBoolean("guard_on", on).apply(); }

    /** Last seen bounced-cheque count for the guard (-1 = never checked). */
    public long guardBounced() { return p.getLong("guard_bounced", -1); }

    public void setGuardBounced(long n) { p.edit().putLong("guard_bounced", n).apply(); }

    /** Already-notified big invoices («S|no;B|no…», pruned to 200). */
    public String guardSeen() { return p.getString("guard_seen", ""); }

    public void setGuardSeen(String v) { p.edit().putString("guard_seen", v == null ? "" : v).apply(); }

    /** Text zoom level: 0 = 90٪, 1 = 100٪, 2 = 115٪, 3 = 130٪. */
    public int zoomIdx() {
        try {
            int z = p.getInt("zoom_idx", 1);
            return z < 0 || z > 3 ? 1 : z;
        } catch (Exception e) {
            return 1;
        }
    }

    public void setZoomIdx(int z) { p.edit().putInt("zoom_idx", z < 0 ? 0 : (z > 3 ? 3 : z)).apply(); }
}
