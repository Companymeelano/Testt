package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import ir.meelano.manager.data.Meta;

/**
 * Atiran user login (v26).
 *
 * After license + connection setup, the app asks for the Atiran username and
 * password. The user row comes from Atiran's own `sys_users` table
 * (user_id / user_name / user_password / role_id / active / IsLocked …) and
 * the in-app role is derived from the Atiran role name (Roles/role tables).
 *
 * Password reality: Atiran stores `user_password` as varbinary(50) — a
 * proprietary binary hash whose algorithm is NOT documented. So verification
 * throws everything plausible at the stored bytes (v27):
 *  - server side: SQL pwdcompare() (covers SQL-native pwdencrypt values);
 *  - client side: every standard hash (MD5 / SHA-1 / SHA-224 / SHA-256 /
 *    SHA-384 × UTF-8 / UTF-16LE / UTF-16BE / windows-1256, raw and hex
 *    forms) plus username-salted variants. If Atiran used any of these,
 *    the REAL Atiran password just works — zero setup.
 * v27 is strict: login accepts ONLY the real Atiran password (no app-PIN
 * fallback at the gate). The stored verifier bytes are cached per user so
 * the same real-password check also works OFFLINE after the first online
 * login. The per-user app PIN (Users section) stays as managed tooling for
 * later phases.
 * Release phases (single app, role editions): admin + warehouse are live
 * (phases 1-2); seller / accountant / visitor / distributor / moadian get
 * a «coming soon» gate until their phase opens (see phaseOpen).
 *
 * Everything here is READ-ONLY against the server: we never write
 * IsLoggedIn / LoginDetails / PINs back to Atiran. PINs, sessions and the
 * username index live hashed in the app's own private prefs.
 */
public final class AtiranAuth {
    private AtiranAuth() { }

    /** Thrown when this database cannot do Atiran login at all. */
    public static final class NoTable extends Exception {
        public NoTable(String m) { super(m); }
    }

    /** One Atiran login identity, resolved from sys_users (+ role name). */
    public static final class AuthUser {
        public int uid;
        public String userName = "";
        public String displayName = "";
        public String phone = "";
        public String roleName = "";
        public int roleId;
        /** App role id after keyword mapping (before manager override). */
        public String appRole = RoleStore.SELLER;
        public boolean active = true;
        public boolean locked;
        public byte[] pw;
        /** False when sys_users has no user_password column we could recognise. */
        public boolean pwColFound;
        /** Result of the server-side pwdcompare() probe, when it could be run. */
        public boolean serverCompared;
    }

    // ================= server read (call off the UI thread) =================

    /**
     * Resolve one username to its Atiran identity. Returns null when the
     * username does not exist. Throws NoTable when sys_users (or its
     * user_name column) is absent, other Exceptions for connection errors.
     */
    public static AuthUser fetchUser(Connection c, String username) throws Exception {
        Meta m = new Meta(c);
        if (!m.table("sys_users")) throw new NoTable("sys_users");
        String idCol = m.col("sys_users", "user_id");
        String unCol = m.col("sys_users", "user_name");
        if (idCol == null || unCol == null) throw new NoTable("sys_users.user_name");
        String fnCol = m.col("sys_users", "user_fname");
        String lnCol = m.col("sys_users", "user_lname");
        String roleCol = m.col("sys_users", "role_id");
        String actCol = m.col("sys_users", "active");
        String lockCol = m.col("sys_users", "IsLocked", "islocked", "is_locked");
        String phoneCol = m.col("sys_users", "phone");
        String pwCol = m.col("sys_users", "user_password");

        StringBuilder sb = new StringBuilder("SELECT TOP 1 ");
        sb.append(idCol).append(" AS a_id, ").append(unCol).append(" AS a_un");
        if (fnCol != null) sb.append(", ").append(fnCol).append(" AS a_fn");
        if (lnCol != null) sb.append(", ").append(lnCol).append(" AS a_ln");
        if (roleCol != null) sb.append(", ").append(roleCol).append(" AS a_role");
        if (actCol != null) sb.append(", ").append(actCol).append(" AS a_act");
        if (lockCol != null) sb.append(", ").append(lockCol).append(" AS a_lock");
        if (phoneCol != null) sb.append(", ").append(phoneCol).append(" AS a_phone");
        if (pwCol != null) sb.append(", ").append(pwCol).append(" AS a_pw");
        sb.append(" FROM sys_users WHERE ").append(unCol).append(" = ?");

        AuthUser u = new AuthUser();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            ps.setString(1, username == null ? "" : username.trim());
            rs = ps.executeQuery();
            if (!rs.next()) return null;
            try { u.uid = rs.getInt("a_id"); } catch (Exception ignored) { }
            try { u.userName = str(rs, "a_un"); } catch (Exception ignored) { }
            String fn = col(rs, "a_fn"), ln = col(rs, "a_ln");
            u.displayName = (fn + " " + ln).trim();
            if (u.displayName.isEmpty()) u.displayName = u.userName;
            try { u.roleId = rs.getInt("a_role"); } catch (Exception ignored) { }
            if (actCol != null) {
                try {
                    Object o = rs.getObject("a_act");
                    u.active = flagOn(o);
                } catch (Exception ignored) { }
            }
            if (lockCol != null) {
                try {
                    Object o = rs.getObject("a_lock");
                    u.locked = flagOn(o);
                } catch (Exception ignored) { }
            }
            u.phone = col(rs, "a_phone");
            u.pwColFound = (pwCol != null);
            if (pwCol != null) {
                try { u.pw = rs.getBytes("a_pw"); } catch (Exception ignored) { }
            }
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        if (roleCol != null) u.roleName = roleNameOf(c, m, u.roleId);
        u.appRole = mapRole(u.roleId, u.roleName);
        return u;
    }

    /** Best-effort role-name lookup over the unknown Roles/role schema. */
    public static String roleNameOf(Connection c, Meta m, int roleId) {
        String[] tables = {"Roles", "role"};
        String[] ids = {"ID", "id", "RoleID", "role_id", "RoleId", "RoleCode",
                "roleCode", "Code", "code", "RoleRdf", "role_rdf"};
        String[] nms = {"Name", "name", "RoleName", "role_name", "Title",
                "title", "RoleTitle", "roleTitle", "Role", "role", "RoleText"};
        for (String t : tables) {
            String idCol, nmCol;
            try {
                if (!m.table(t)) continue;
                idCol = m.col(t, ids);
                nmCol = m.col(t, nms);
                if (idCol == null || nmCol == null) continue;
            } catch (Exception e) {
                continue;
            }
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                ps = c.prepareStatement("SELECT TOP 1 " + nmCol + " FROM " + t
                        + " WHERE " + idCol + " = ?");
                ps.setInt(1, roleId);
                rs = ps.executeQuery();
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.trim().isEmpty()) return v.trim();
                }
            } catch (Exception ignored) {
            } finally {
                closeQuiet(rs);
                closeQuiet(ps);
            }
        }
        return "";
    }

    // ================= password verification =================

    /**
     * Compare the typed password with Atiran's stored bytes through every
     * plausible standard transform (plain, salted, raw, hex). Pure in-memory
     * equality — no writes, no lockout risk. Returns false for truly custom
     * hashes (the tools/crack_probe.py flow then takes over).
     */
    public static boolean verifyPassword(byte[] stored, String typed) {
        return verifyPassword(stored, typed, "");
    }

    public static boolean verifyPassword(byte[] stored, String typed, String userName) {
        if (stored == null || stored.length == 0 || typed == null) return false;
        String[] texts = {typed, typed.trim()};
        String[] charsets = {"UTF-8", "UTF-16LE", "UTF-16BE", "windows-1256"};
        String[] algs = {"MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384"};
        try {
            for (String t : texts) {
                for (String cs : charsets) {
                    byte[] raw;
                    try {
                        raw = t.getBytes(cs);
                    } catch (Exception e) {
                        continue;
                    }
                    if (eq(stored, raw)) return true; // plain bytes
                    for (String alg : algs) {
                        if (eq(stored, digest(alg, raw))) return true;
                    }
                }
            }
            // Username-salted variants: salt+pw and pw+salt.
            String[] salts = saltVariants(userName);
            String[] sAlgs = {"MD5", "SHA-1", "SHA-256"};
            String[] sCs = {"UTF-8", "UTF-16LE"};
            for (String t : texts) {
                for (String salt : salts) {
                    if (salt.isEmpty()) continue;
                    String[] combos = {salt + t, t + salt};
                    for (String combo : combos) {
                        for (String cs : sCs) {
                            byte[] raw;
                            try {
                                raw = combo.getBytes(cs);
                            } catch (Exception e) {
                                continue;
                            }
                            for (String alg : sAlgs) {
                                if (eq(stored, digest(alg, raw))) return true;
                            }
                        }
                    }
                }
            }
            // Stored as ASCII hex of a standard digest (plain + salted)?
            String hex = asciiHex(stored);
            if (hex != null) {
                for (String t : texts) {
                    for (String cs : charsets) {
                        byte[] raw = t.getBytes(cs);
                        for (String alg : algs) {
                            byte[] d = digest(alg, raw);
                            if (d != null && hex.equalsIgnoreCase(toHex(d))) return true;
                        }
                    }
                }
                for (String t : texts) {
                    for (String salt : salts) {
                        if (salt.isEmpty()) continue;
                        String[] combos = {salt + t, t + salt};
                        for (String combo : combos) {
                            for (String cs : sCs) {
                                byte[] raw = combo.getBytes(cs);
                                for (String alg : sAlgs) {
                                    byte[] d = digest(alg, raw);
                                    if (d != null && hex.equalsIgnoreCase(toHex(d))) return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    /**
     * Server-side check: if Atiran stored the value with SQL-native
     * pwdencrypt(), pwdcompare() verifies it with zero algorithm knowledge.
     * Returns false for anything else (or any error) — always safe to try.
     */
    public static boolean verifyPasswordServer(Connection c, String username, String typed) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            Meta m = new Meta(c);
            if (!m.table("sys_users")) return false;
            String un = m.col("sys_users", "user_name");
            String pw = m.col("sys_users", "user_password");
            if (un == null || pw == null || typed == null || typed.isEmpty()) return false;
            ps = c.prepareStatement("SELECT TOP 1 pwdcompare(?, " + pw
                    + ") FROM sys_users WHERE " + un + " = ?");
            ps.setString(1, typed);
            ps.setString(2, username == null ? "" : username.trim());
            rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) == 1;
        } catch (Exception e) {
            return false;
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
    }

    private static boolean eq(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    private static byte[] digest(String alg, byte[] raw) {
        try {
            return java.security.MessageDigest.getInstance(alg).digest(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String[] saltVariants(String userName) {
        String u = userName == null ? "" : userName.trim();
        if (u.isEmpty()) return new String[]{""};
        String up = u.toUpperCase(Locale.US), lo = u.toLowerCase(Locale.US);
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(u);
        if (!out.contains(up)) out.add(up);
        if (!out.contains(lo)) out.add(lo);
        return out.toArray(new String[0]);
    }

    private static String asciiHex(byte[] b) {
        if (b == null || b.length == 0 || b.length > 128) return null;
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            char ch = (char) (x & 0xFF);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) return null;
            s.append(ch);
        }
        int n = s.length();
        if (n == 32 || n == 40 || n == 56 || n == 64 || n == 96) return s.toString();
        return null;
    }

    private static String toHex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    // ================= Atiran role -> app role =================

    /**
     * Map an Atiran role name to an app role id by Persian/English keywords.
     * Unknown or empty names fall back to SELLER (a safe middle); the
     * manager can pin the exact role per user (override) in Users.
     */
    public static String mapRole(int roleId, String roleName) {
        String n = roleName == null ? "" : roleName.trim();
        String l = n.toLowerCase(Locale.US);
        if (has(n, "مدیر") || l.contains("admin") || l.contains("modir")
                || l.contains("manage") || l.contains("sysadmin")) return RoleStore.ADMIN;
        if (has(n, "حساب") || l.contains("account") || l.contains("hesab"))
            return RoleStore.ACCOUNTANT;
        if (has(n, "انبار") || l.contains("anbar") || l.contains("warehouse")
                || l.contains("stock")) return RoleStore.WAREHOUSE;
        if (has(n, "ویزیتور") || has(n, "بازاریاب") || l.contains("visitor")
                || l.contains("visit")) return RoleStore.VISITOR;
        if (has(n, "موزع") || has(n, "توزیع") || has(n, "پخش") || has(n, "راننده")
                || l.contains("distribut") || l.contains("driver") || l.contains("moze")
                || l.contains("pakhsh")) return RoleStore.DISTRIBUTOR;
        if (has(n, "مودی") || has(n, "مودیان") || has(n, "مالیات")
                || l.contains("moadi") || l.contains("tax")) return RoleStore.MOADIAN;
        if (has(n, "فروش") || l.contains("sell") || l.contains("forosh"))
            return RoleStore.SELLER;
        return RoleStore.SELLER;
    }

    private static boolean has(String s, String sub) {
        return s != null && s.contains(sub);
    }

    /**
     * True when a name reads as a distributor, using exactly the keywords mapRole uses to
     * assign the DISTRIBUTOR role. The warehouse hands goods to distributors, so the picker
     * filters the visitor list down to them instead of offering every name on file.
     */
    public static boolean isDistributorName(String n) {
        return RoleStore.DISTRIBUTOR.equals(mapRole(0, n));
    }

    // ================= local stores (private prefs) =================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_login", Context.MODE_PRIVATE);
    }

    /** Master switch (default ON). Admins can turn the login screen off. */
    public static boolean enabled(Context c) {
        try {
            return prefs(c).getBoolean("login_on", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static void setEnabled(Context c, boolean on) {
        try {
            prefs(c).edit().putBoolean("login_on", on).apply();
        } catch (Exception ignored) { }
    }

    /** True when this database cannot do Atiran login (no sys_users). */
    public static boolean noTable(Context c) {
        try {
            return prefs(c).getBoolean("no_table", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setNoTable(Context c) {
        try {
            prefs(c).edit().putBoolean("no_table", true).apply();
        } catch (Exception ignored) { }
    }

    public static void clearNoTable(Context c) {
        try {
            prefs(c).edit().putBoolean("no_table", false).apply();
        } catch (Exception ignored) { }
    }

    /** Should MainActivity bounce to LoginActivity right now? */
    public static boolean gate(Context c) {
        try {
            return enabled(c) && !noTable(c) && !validSession(c);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasSession(Context c) {
        return validSession(c);
    }

    public static boolean validSession(Context c) {
        try {
            long until = prefs(c).getLong("s_until", 0);
            return until > System.currentTimeMillis()
                    && !sessionRole(c).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Session role id, or "" when there is no valid session. */
    public static String sessionRole(Context c) {
        try {
            long until = prefs(c).getLong("s_until", 0);
            if (until <= System.currentTimeMillis()) return "";
            String r = prefs(c).getString("s_role", "");
            return RoleStore.isValid(r) ? r : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String sessionName(Context c) {
        try {
            return prefs(c).getString("s_name", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static String sessionUser(Context c) {
        try {
            return prefs(c).getString("s_user", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean sessionBoot(Context c) {
        try {
            return prefs(c).getBoolean("s_boot", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** Remembered = 30 days, otherwise 12 hours. Bootstrap = 12 hours. */
    public static void saveSession(Context c, int uid, String userName,
            String displayName, String role, boolean remember, boolean boot) {
        try {
            long days = remember ? 30L * 24 : 12;
            prefs(c).edit()
                    .putInt("s_uid", uid)
                    .putString("s_user", userName == null ? "" : userName)
                    .putString("s_name", displayName == null ? "" : displayName)
                    .putString("s_role", RoleStore.isValid(role) ? role : RoleStore.SELLER)
                    .putLong("s_until", System.currentTimeMillis()
                            + days * 3600L * 1000L)
                    .putBoolean("s_boot", boot)
                    .apply();
        } catch (Exception ignored) { }
    }

    public static void clearSession(Context c) {
        try {
            prefs(c).edit().putLong("s_until", 0).putBoolean("s_boot", false).apply();
        } catch (Exception ignored) { }
    }

    public static int sessionUid(Context c) {
        try {
            return prefs(c).getInt("s_uid", -999);
        } catch (Exception e) {
            return -999;
        }
    }

    /** Push the session role into RoleStore (call once per launch). */
    public static void applySessionRole(Context c) {
        try {
            String r = sessionRole(c);
            if (!r.isEmpty()) RoleStore.setCurrent(c, r);
        } catch (Exception ignored) { }
    }

    public static String startScreen(Context c) {
        try {
            String r = sessionRole(c);
            if (!r.isEmpty()) return RoleStore.homeFor(r);
        } catch (Exception ignored) { }
        return "home";
    }

    // ---- per-user app PIN (4+ chars, salted SHA-256) ----

    public static boolean pinSet(Context c, int uid) {
        try {
            return !prefs(c).getString("pin" + uid, "").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkPin(Context c, int uid, String pin) {
        try {
            String saved = prefs(c).getString("pin" + uid, "");
            if (saved.isEmpty() || pin == null) return false;
            return sha(Money.en(pin).trim()).equals(saved);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setPin(Context c, int uid, String pinOrNull) {
        try {
            String pin = pinOrNull == null ? "" : Money.en(pinOrNull).trim();
            SharedPreferences.Editor e = prefs(c).edit();
            if (pin.length() < 4) e.putString("pin" + uid, "");
            else e.putString("pin" + uid, sha(pin));
            e.apply();
        } catch (Exception ignored) { }
    }

    // ---- manager role override per user ----

    /** Explicit app role for this user, or "" = follow the auto mapping. */
    public static String overrideRole(Context c, int uid) {
        try {
            String r = prefs(c).getString("role_ov" + uid, "");
            return RoleStore.isValid(r) ? r : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void setOverrideRole(Context c, int uid, String roleOrNull) {
        try {
            SharedPreferences.Editor e = prefs(c).edit();
            if (RoleStore.isValid(roleOrNull)) e.putString("role_ov" + uid, roleOrNull);
            else e.remove("role_ov" + uid);
            e.apply();
        } catch (Exception ignored) { }
    }

    /** Effective app role = override if set, else the auto-mapped role. */
    public static String effectiveRole(Context c, int uid, String autoRole) {
        String ov = overrideRole(c, uid);
        if (!ov.isEmpty()) return ov;
        return RoleStore.isValid(autoRole) ? autoRole : RoleStore.SELLER;
    }

    // ---- staff link per user (visitor/warehouse codes, for later scoping) ----

    /** Stored as "v=12&a=3" (either part optional). */
    public static String staffLink(Context c, int uid) {
        try {
            return prefs(c).getString("link" + uid, "");
        } catch (Exception e) {
            return "";
        }
    }

    public static void setStaffLink(Context c, int uid, String visCode, String anbarCode) {
        try {
            String v = visCode == null ? "" : Money.en(visCode).trim();
            String a = anbarCode == null ? "" : Money.en(anbarCode).trim();
            String val = "";
            if (!v.isEmpty()) val += "v=" + v;
            if (!a.isEmpty()) val += (val.isEmpty() ? "" : "&") + "a=" + a;
            SharedPreferences.Editor e = prefs(c).edit();
            if (val.isEmpty()) e.remove("link" + uid);
            else e.putString("link" + uid, val);
            e.apply();
        } catch (Exception ignored) { }
    }

    public static String faStaffLink(Context c, int uid) {
        String link = staffLink(c, uid);
        if (link.isEmpty()) return "—";
        String v = part(link, "v="), a = part(link, "a=");
        StringBuilder b = new StringBuilder();
        if (!v.isEmpty()) b.append("ویزیتور ").append(Money.fa(v));
        if (!a.isEmpty()) {
            if (b.length() > 0) b.append(" • ");
            b.append("انبار ").append(Money.fa(a));
        }
        return b.length() == 0 ? "—" : b.toString();
    }

    private static String part(String link, String key) {
        for (String p : link.split("&")) {
            if (p.startsWith(key)) return p.substring(key.length());
        }
        return "";
    }

    // ---- offline cache: username -> uid + display ----

    public static void putCache(Context c, int uid, String userName,
            String displayName, String role) {
        try {
            String key = "name_" + canon(userName);
            prefs(c).edit()
                    .putInt(key, uid)
                    .putString("nm" + uid, displayName == null ? "" : displayName)
                    .putString("rl" + uid, role == null ? "" : role)
                    .apply();
        } catch (Exception ignored) { }
    }

    /** Cached uid for this username, or -999 when unknown. */
    public static int cachedUid(Context c, String userName) {
        try {
            return prefs(c).getInt("name_" + canon(userName), -999);
        } catch (Exception e) {
            return -999;
        }
    }

    public static String cachedName(Context c, int uid) {
        try {
            return prefs(c).getString("nm" + uid, "");
        } catch (Exception e) {
            return "";
        }
    }

    public static String cachedRole(Context c, int uid) {
        try {
            String r = prefs(c).getString("rl" + uid, "");
            return RoleStore.isValid(r) ? r : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String canon(String u) {
        return u == null ? "" : u.trim().toLowerCase(Locale.US);
    }

    // ================= release phases (v27) =================

    /**
     * ONE app — the Atiran role picks the edition. Admin + warehouse are
     * live; every other role waits for its phase. Opening a phase is a
     * deliberate one-line change here, never an accident.
     */
    public static boolean phaseOpen(Context c, String role) {
        if (RoleStore.ADMIN.equals(role)) return true;
        if (RoleStore.WAREHOUSE.equals(role)) return true; // PHASE 2 (v28)
        return false;
    }

    // ---- cached verifier bytes: the real-password check, offline ----

    /** Cache the stored bytes after any successful fetch (same exposure as the baked-in SQL login). */
    public static void putPwCache(Context c, int uid, byte[] pw) {
        try {
            if (pw == null || pw.length == 0) return;
            prefs(c).edit().putString("pwb" + uid, toHex(pw)).apply();
        } catch (Exception ignored) { }
    }

    public static byte[] cachedPw(Context c, int uid) {
        try {
            String h = prefs(c).getString("pwb" + uid, "");
            if (h.isEmpty() || (h.length() % 2) != 0) return null;
            byte[] b = new byte[h.length() / 2];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= small helpers =================

    private static boolean flagOn(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        String t = String.valueOf(o).trim().toUpperCase(Locale.US);
        if (t.isEmpty()) return false;
        if ("TRUE".equals(t) || "T".equals(t) || "Y".equals(t)
                || "YES".equals(t) || "1".equals(t)) return true;
        if (t.contains("فعال") && !t.contains("غیر")) return true;
        if ("فعال".equals(t) || "✓".equals(t)) return true;
        return false;
    }

    private static String str(ResultSet rs, String alias) throws Exception {
        Object o = rs.getObject(alias);
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String col(ResultSet rs, String alias) {
        try {
            Object o = rs.getObject(alias);
            return o == null ? "" : String.valueOf(o).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeQuiet(Object o) {
        try {
            if (o instanceof ResultSet) ((ResultSet) o).close();
            else if (o instanceof java.sql.Statement) ((java.sql.Statement) o).close();
        } catch (Exception ignored) { }
    }

    private static String sha(String s) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(("meelano_loginpin:" + s).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "x" + s;
        }
    }
}
