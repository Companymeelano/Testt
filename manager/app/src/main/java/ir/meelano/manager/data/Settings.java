package ir.meelano.manager.data;

import ir.meelano.manager.core.Diag;
import android.content.Context;
import android.content.SharedPreferences;

import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.SecretBox;

/** App settings: connection + PIN lock + luxury theme + notifications + offline cache. */
public final class Settings {
    private final SharedPreferences p;
    private final Context app;

    /** Name of the Keystore-encrypted SQL password inside the SecretBox store. */
    private static final String PASS_SECRET = "db_pass_enc";

    public Settings(Context c) {
        app = c.getApplicationContext();
        p = c.getSharedPreferences("meelano_manager7", Context.MODE_PRIVATE);
        // Scrub any plain-text SQL password left by older builds (or by the old fallback).
        // It is moved into the Keystore store when possible and never kept in plain text.
        if (p.contains("db_pass")) {
            String legacy = p.getString("db_pass", "");
            if (!legacy.isEmpty()) SecretBox.put(app, PASS_SECRET, legacy);
            p.edit().remove("db_pass").apply();
        }
    }

    public String host() { return p.getString("db_host", "").trim(); }

    /** Inside-network address (shop Wi-Fi / cable): raw, never displayed. */
    public String lan() { return p.getString("db_lan", "").trim(); }

    /** Outside-network address (mobile data / public IP): raw, never displayed. */
    public String wan() { return p.getString("db_wan", "").trim(); }

    /** The last successful saved endpoint, falling back to the other profile or legacy host. */
    public String savedHost() {
        String last = linkLast();
        if ("wan".equals(last) && !wan().isEmpty()) return wan();
        if ("lan".equals(last) && !lan().isEmpty()) return lan();
        if (!lan().isEmpty()) return lan();
        if (!wan().isEmpty()) return wan();
        return host();
    }

    /** Server address with all but the last segment masked (safe to display). */
    public String maskedHost() {
        String h = savedHost();
        return mask(h.isEmpty() ? effHost().trim() : h.trim());
    }

    /** Masked inside address ("" when unset). */
    public String maskedLan() { return lan().isEmpty() ? "" : mask(lan()); }

    /** Masked outside address ("" when unset). */
    public String maskedWan() { return wan().isEmpty() ? "" : mask(wan()); }

    private static String mask(String h) {
        return ir.meelano.manager.core.SmartLink.maskHost(h);
    }

    /** True only after both a server address and an explicitly selected DB are saved. */
    public boolean connConfigured() {
        boolean hostSet = !lan().isEmpty() || !wan().isEmpty() || !host().isEmpty();
        return hostSet && !db().isEmpty();
    }
    public String port() { return p.getString("db_port", "").trim(); }
    public String db() { return p.getString("db_name", "").trim(); }
    public String user() { return p.getString("db_user", "").trim(); }
    /**
     * SQL password: read from the Android-Keystore encrypted store. A value saved by
     * an older build in plain SharedPreferences is moved into the encrypted store once.
     */
    public String pass() {
        return SecretBox.get(app, PASS_SECRET);
    }

    /**
     * Stores the SQL password only in the Keystore-encrypted store. If the Keystore cannot
     * encrypt, the password is NOT written in plain text; the user simply re-enters it.
     */
    private void savePass(String pass) {
        String v = pass == null ? "" : pass;
        p.edit().remove("db_pass").apply();
        if (!SecretBox.put(app, PASS_SECRET, v) && !v.isEmpty()) {
            ir.meelano.manager.core.Diag.ignored("Settings:savePass", new IllegalStateException("keystore unavailable"));
        }
    }

    public String effHost() { String h = host(); return h.isEmpty() ? Atiran.defaultHost() : h; }
    public String effDb() { String d = db(); return d.isEmpty() ? Atiran.defaultDb() : d; }
    // The SQL login is fixed (AdminAn) for every database, so a stale stored login cannot block the connection.
    public String effUser() { return Atiran.defaultUser(); }
    public String effPass() { return Atiran.defaultPass(); }
    public int effPort() {
        try {
            int v = Integer.parseInt(Money.en(port()).trim());
            return v > 0 ? v : Atiran.DEFAULT_PORT;
        } catch (Exception ignored) {
            return Atiran.DEFAULT_PORT;
        }
    }

    /**
     * Forget the saved server endpoints and selected database so setup starts
     * fresh. Preserve the SQL driver/login preferences and all license/account
     * state; changing an endpoint never changes the direct SQL auth method.
     */
    public void clearSavedServer() {
        p.edit().remove("db_host").remove("db_lan").remove("db_wan")
                .remove("db_name").remove("link_last")
                .putString("link_mode", "auto").apply();
    }

    public void saveConnection(String host, String port, String db, String user, String pass) {
        p.edit().putString("db_host", host == null ? "" : host.trim())
                .putString("db_port", port == null ? "" : port.trim())
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .apply();
        savePass(pass);
    }

    /** Save both smart-link profiles at once (either address may be empty). */
    public void saveSmart(String lan, String wan, String port, String db, String user, String pass) {
        p.edit().putString("db_lan", lan == null ? "" : lan.trim())
                .putString("db_wan", wan == null ? "" : wan.trim())
                .putString("db_port", port == null ? "" : port.trim())
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .apply();
        savePass(pass);
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
                .apply();
        savePass(pass);
    }

    /** One-time migration of the legacy single address into lan/wan. */
    public void migrateLegacyHost() {
        try {
            if (!lan().isEmpty() || !wan().isEmpty()) return;
            String h = host();
            if (h.isEmpty()) return;
            placeHost(h, port(), db(), user(), pass());
        } catch (Exception ignored) { Diag.ignored("Settings:127", ignored); }
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
        // Dark («Midnight Gold») is the default. One-time migration: installs that were
        // still on the old light default switch to dark once; a later manual choice is kept.
        if (!p.getBoolean("theme_dark_default_v38", false)) {
            p.edit().putString("theme_mode", "dark").putBoolean("theme_dark_default_v38", true).apply();
        }
        // v39: «آجیل و خشکبار» is the default for everyone once; a later manual choice is kept.
        if (!p.getBoolean("theme_nuts_default_v39", false)) {
            p.edit().putString("theme_mode", "nuts").putBoolean("theme_nuts_default_v39", true).apply();
        }
        String m = p.getString("theme_mode", "nuts");
        return "light".equals(m) ? "light" : "nuts".equals(m) ? "nuts" : "dark";
    }

    public void setThemeMode(String mode) {
        String v = "light".equals(mode) ? "light" : "nuts".equals(mode) ? "nuts" : "dark";
        p.edit().putString("theme_mode", v).apply();
    }

    /** Accent key: gold / emerald / sapphire / ruby / violet / teal. */
    public String themeAccent() {
        String k = p.getString("theme_accent", "platinum");
        for (String ok : new String[]{"platinum", "gold", "emerald", "sapphire", "ruby", "violet", "teal"})
            if (ok.equals(k)) return k;
        return "platinum";
    }

    public void setThemeAccent(String key) {
        p.edit().putString("theme_accent", key == null ? "platinum" : key).apply();
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
    /** Day KPIs and the sales comparison first (as in the reference design), then trends, then alerts. */
    public static final String HOME_ORDER_DEFAULT = "alerts,due,kpis,today_top,returns,trend,donut,invstatus,debtors,visitors,eod,shortcuts";
    private static final String HOME_ORDER_V2_DEFAULT = "kpis,trend,donut,today_top,alerts,invstatus,returns,due,debtors,visitors,eod,shortcuts";
    private static final String HOME_ORDER_V1_DEFAULT = "kpis,alerts,due,invstatus,returns,today_top,trend,debtors,visitors,donut,eod,shortcuts";
    private static final String HOME_ORDER_PREVIOUS_DEFAULT = "kpis,alerts,trend,donut,debtors,visitors,due,invstatus,returns,shortcuts";

    public String homeOrder() {
        String v = p.getString("home_order", null);
        if (v == null || v.trim().isEmpty()) return HOME_ORDER_DEFAULT;
        // Move installations still using the untouched old default to the new
        // manager-first order, while preserving any order the user customized.
        if (HOME_ORDER_V1_DEFAULT.equals(v.trim()) || HOME_ORDER_PREVIOUS_DEFAULT.equals(v.trim())
                || HOME_ORDER_V2_DEFAULT.equals(v.trim())) {
            p.edit().putString("home_order", HOME_ORDER_DEFAULT).apply();
            return HOME_ORDER_DEFAULT;
        }
        return v;
    }

    public void setHomeOrder(String csv) {
        p.edit().putString("home_order", csv == null ? HOME_ORDER_DEFAULT : csv).apply();
    }

    /** Whether the manager dashboard's lower-priority analysis cards are expanded. */
    public boolean homeDetailsExpanded() { return p.getBoolean("home_details_expanded", false); }

    public void setHomeDetailsExpanded(boolean expanded) {
        p.edit().putBoolean("home_details_expanded", expanded).apply();
    }

    // ---------------- stock alerts + auto backup (v18) ----------------
    public boolean stockOn() { return p.getBoolean("stock_on", true); }

    public void setStockOn(boolean on) { p.edit().putBoolean("stock_on", on).apply(); }

    public boolean abOn() { return p.getBoolean("ab_on", false); }

    public void setAbOn(boolean on) { p.edit().putBoolean("ab_on", on).apply(); }

    /** Offline mode: reports are read from the last nightly copy kept on the phone. */
    public boolean offlineMode() { return p.getBoolean("offline_mode", false); }

    public void setOfflineMode(boolean on) { p.edit().putBoolean("offline_mode", on).apply(); }

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

    /**
     * Display unit for amounts: {@link ir.meelano.manager.core.Money#UNIT_TOMAN}
     * (default) or {@link ir.meelano.manager.core.Money#UNIT_RIAL}. Atiran always
     * stores Rials; this only changes how amounts are shown.
     */
    public int moneyUnit() { return p.getInt("money_unit", ir.meelano.manager.core.Money.UNIT_TOMAN); }

    public void setMoneyUnit(int u) { p.edit().putInt("money_unit", u).apply(); }

    /** Alert the warehouse keeper the moment a new sales invoice is registered. */
    public boolean whNewOn() { return p.getBoolean("wh_new_on", true); }

    public void setWhNewOn(boolean on) { p.edit().putBoolean("wh_new_on", on).apply(); }

    /** Sales invoice numbers already notified today («no;no…», pruned to 300). */
    public String whSeen() { return p.getString("wh_seen", ""); }

    public void setWhSeen(String v) { p.edit().putString("wh_seen", v == null ? "" : v).apply(); }

    /**
     * Jalali day the {@link #whSeen()} set belongs to. Empty means "never
     * primed", which makes the first run seed the set silently instead of
     * firing a notification for every invoice the day already had.
     */
    public String whSeenDay() { return p.getString("wh_seen_day", ""); }

    public void setWhSeenDay(String v) { p.edit().putString("wh_seen_day", v == null ? "" : v).apply(); }

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
