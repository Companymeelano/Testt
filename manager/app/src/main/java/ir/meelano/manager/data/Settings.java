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

    /** Inside-profile port ("" → shared port → 1433). */
    public String portLan() { return p.getString("db_port_lan", "").trim(); }

    /** Outside-profile port ("" → shared port → 1433) — e.g. 5050 forwarded to 1433 on the dedicated IP. */
    public String portWan() { return p.getString("db_port_wan", "").trim(); }

    /**
     * Parse a port text (Persian digits accepted): 1..65535, else the
     * default 1433. Pure — safe to unit-test.
     */
    public static int parsePort(String v) {
        try {
            int n = Integer.parseInt(Money.en(v == null ? "" : v.trim()).trim());
            return (n >= 1 && n <= 65535) ? n : Atiran.DEFAULT_PORT;
        } catch (Exception ignored) {
            return Atiran.DEFAULT_PORT;
        }
    }

    /** Effective port for one smart-link profile (see {@link #effPort}). */
    public int effPortFor(String kind) {
        String per = ir.meelano.manager.core.SmartLink.WAN.equals(kind) ? portWan() : portLan();
        if (!per.isEmpty()) return parsePort(per);
        return effPort();
    }
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
        return parsePort(port());
    }

    /**
     * Forget the saved server endpoints and selected database so setup starts
     * fresh. Preserve the SQL driver/login preferences and all license/account
     * state; changing an endpoint never changes the direct SQL auth method.
     */
    public void clearSavedServer() {
        p.edit().remove("db_host").remove("db_lan").remove("db_wan")
                .remove("db_name").remove("link_last")
                .remove("db_port_lan").remove("db_port_wan")
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
        saveSmart(lan, wan, port, port, db, user, pass);
    }

    /**
     * Save both smart-link profiles with their own ports (either may be empty;
     * empty = the default 1433, or the shared port when one was saved before).
     * The dedicated/public address often answers on a forwarded port
     * (e.g. 5050 → 1433) while the shop LAN keeps 1433.
     */
    public void saveSmart(String lan, String wan, String lanPort, String wanPort,
                          String db, String user, String pass) {
        p.edit().putString("db_lan", lan == null ? "" : lan.trim())
                .putString("db_wan", wan == null ? "" : wan.trim())
                .putString("db_port_lan", lanPort == null ? "" : lanPort.trim())
                .putString("db_port_wan", wanPort == null ? "" : wanPort.trim())
                .putString("db_port", "")
                .putString("db_name", db == null ? "" : db.trim())
                .putString("db_user", user == null ? "" : user.trim())
                .apply();
        savePass(pass);
    }

    /**
     * Store one address into the matching profile (private → inside,
     * public → outside) without touching the other profile. The port is
     * stored per-profile too (a forwarded port on the dedicated IP does
     * not have to match the shop LAN port).
     */
    public void placeHost(String host, String port, String db, String user, String pass) {
        String h = host == null ? "" : host.trim();
        SharedPreferences.Editor e = p.edit();
        if (!h.isEmpty()) {
            Boolean priv = ir.meelano.manager.core.SmartLink.isPrivate(h);
            if (priv != null && !priv) {
                e.putString("db_wan", h);
                e.putString("db_port_wan", port == null ? "" : port.trim());
            } else {
                e.putString("db_lan", h);
                e.putString("db_port_lan", port == null ? "" : port.trim());
            }
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

    /** Legacy single-iteration verifier namespace (fixed prefix), kept only for verification. */
    private static final String LEGACY_PIN_PREFIX = "meelano7:";

    public void setPin(String pinOrNull) {
        String pin = pinOrNull == null ? null : Money.en(pinOrNull).trim();
        if (pin == null || pin.length() < 4) {
            p.edit().putBoolean("pin_on", false).putString("pin_hash", "").apply();
            return;
        }
        String h = ir.meelano.manager.core.PinHash.hash(pin);
        if (h.isEmpty()) {
            // Hashing unavailable: fail closed — never store something derived from the PIN.
            p.edit().putBoolean("pin_on", false).putString("pin_hash", "").apply();
            return;
        }
        p.edit().putBoolean("pin_on", true).putString("pin_hash", h).apply();
    }

    public boolean checkPin(String pin) {
        if (!pinEnabled()) return true;
        if (pin == null) return false;
        String norm = Money.en(pin).trim();
        String saved = pinHash();
        if (!ir.meelano.manager.core.PinHash.verify(norm, saved, LEGACY_PIN_PREFIX)) return false;
        if (ir.meelano.manager.core.PinHash.legacy(saved)) {
            String h = ir.meelano.manager.core.PinHash.hash(norm);
            if (!h.isEmpty()) p.edit().putString("pin_hash", h).apply();
        }
        return true;
    }

    // ---------------- luxury theme ----------------
    /** "dark" (Midnight Gold), "light" (Ivory Royal), or "nuts" (Nut & Dry Fruit). */
    public String themeMode() {
        // Mark the older migration as complete without replacing an explicit user selection.
        // The previous v39 migration wrote "nuts" unconditionally and overwrote a saved dark/light choice.
        if (!p.getBoolean("theme_dark_default_v38", false))
            p.edit().putBoolean("theme_dark_default_v38", true).apply();
        if (!p.getBoolean("theme_nuts_default_v39", false)) {
            android.content.SharedPreferences.Editor edit = p.edit();
            if (!p.contains("theme_mode")) edit.putString("theme_mode", "nuts");
            edit.putBoolean("theme_nuts_default_v39", true).apply();
        }
        return normalizeThemeMode(p.getString("theme_mode", "nuts"));
    }

    /** Pure, fail-safe theme normalization; exposed for regression tests. */
    public static String normalizeThemeMode(String mode) {
        return "light".equals(mode) ? "light" : "dark".equals(mode) ? "dark" : "nuts";
    }

    public void setThemeMode(String mode) {
        p.edit().putString("theme_mode", normalizeThemeMode(mode)).apply();
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
    /** Orderable lower Home modules; sales Hero and KPI stay pinned at the top. */
    public static final String HOME_ORDER_DEFAULT = "alerts,cash_mix,market,due,returns,eod";
    /** The simple dashboard starts with exactly five primary sections: greeting, sales, KPI, alerts, and receipt mix. */
    public static final String HOME_SECTIONS_DEFAULT = "alerts,cash_mix,market";
    private static final String[] HOME_SECTION_KEYS = {"alerts", "cash_mix", "market", "due", "returns", "eod"};

    public String homeOrder() {
        String stored = p.getString("home_order", null);
        if (stored == null || stored.trim().isEmpty()) return HOME_ORDER_DEFAULT;
        if ("kpis,alerts,due,invstatus,returns,today_top,trend,debtors,visitors,donut,eod,shortcuts".equals(stored.trim())
                || "kpis,alerts,trend,donut,debtors,visitors,due,invstatus,returns,shortcuts".equals(stored.trim())
                || "kpis,trend,donut,today_top,alerts,invstatus,returns,due,debtors,visitors,eod,shortcuts".equals(stored.trim())) {
            p.edit().putString("home_order", HOME_ORDER_DEFAULT).apply();
            return HOME_ORDER_DEFAULT;
        }
        StringBuilder clean = new StringBuilder();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String[] active = HOME_ORDER_DEFAULT.split(",");
        for (String key : stored.split(",")) {
            String candidate = key == null ? "" : key.trim();
            boolean known = false;
            for (String valid : active) if (valid.equals(candidate)) { known = true; break; }
            if (known && seen.add(candidate)) {
                if (clean.length() > 0) clean.append(',');
                clean.append(candidate);
            }
        }
        for (String valid : active) {
            if (seen.add(valid)) {
                if (clean.length() > 0) clean.append(',');
                clean.append(valid);
            }
        }
        String normalized = clean.toString();
        if (!normalized.equals(stored)) p.edit().putString("home_order", normalized).apply();
        return normalized;
    }

    public void setHomeOrder(String csv) {
        p.edit().putString("home_order", csv == null ? HOME_ORDER_DEFAULT : csv).apply();
    }

    /** Selected lower Home modules; an empty stored value intentionally hides them all. */
    public String homeSections() {
        String stored = p.getString("home_sections", null);
        return normalizeHomeSections(stored == null ? HOME_SECTIONS_DEFAULT : stored);
    }

    public void setHomeSections(String csv) {
        p.edit().putString("home_sections", normalizeHomeSections(csv == null ? "" : csv)).apply();
    }

    private static String normalizeHomeSections(String csv) {
        StringBuilder normalized = new StringBuilder();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (csv != null) for (String key : csv.split(",")) {
            String candidate = key == null ? "" : key.trim();
            boolean known = false;
            for (String valid : HOME_SECTION_KEYS) if (valid.equals(candidate)) { known = true; break; }
            if (known && seen.add(candidate)) {
                if (normalized.length() > 0) normalized.append(',');
                normalized.append(candidate);
            }
        }
        return normalized.toString();
    }

    /** Simple mode is the compact five-section dashboard; full mode shows every selected module. */
    public boolean homeSimpleMode() { return p.getBoolean("home_simple", true); }

    public void setHomeSimpleMode(boolean simple) {
        p.edit().putBoolean("home_simple", simple).apply();
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
