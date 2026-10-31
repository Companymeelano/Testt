package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import ir.meelano.manager.data.Meta;

/**
 * Atiran user login and role session.
 *
 * System users authenticate through `sys_users` and `Roles`/`role`. Field
 * visitors use a separate path: credentials and active state come from
 * `visitors`, while `sys_vis` must link the visitor row to a positive Atiran
 * UserID. Visitor usernames are never searched in `sys_users`.
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
 * Release phases (single app, role editions): company manager + sales manager
 * + warehouse + visitor are live; seller / accountant / distributor / moadian remain gated until
 * their phase opens (see phaseOpen).
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

    /** A visitor username was found, but its visitor credentials/access link did not validate. */
    public static final class VisitorLoginException extends Exception {
        public VisitorLoginException(String message) { super(message); }
    }

    /** One authenticated Atiran identity, resolved from sys_users or visitors + sys_vis. */
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
        /** False when the relevant Atiran account table has no recognized credential column. */
        public boolean pwColFound;
        /** Result of the server-side pwdcompare() probe, when it could be run. */
        public boolean serverCompared;
    }

    // ================= server read (call off the UI thread) =================

    /**
     * Resolve one regular system-user login. Returns null when the username
     * does not exist. Visitor logins are handled separately by fetchVisitorUser.
     * Throws NoTable when sys_users (or its user_name column) is absent.
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
        sb.append(Sql.q(idCol)).append(" AS a_id, ").append(Sql.q(unCol)).append(" AS a_un");
        if (fnCol != null) sb.append(", ").append(Sql.q(fnCol)).append(" AS a_fn");
        if (lnCol != null) sb.append(", ").append(Sql.q(lnCol)).append(" AS a_ln");
        if (roleCol != null) sb.append(", ").append(Sql.q(roleCol)).append(" AS a_role");
        if (actCol != null) sb.append(", ").append(Sql.q(actCol)).append(" AS a_act");
        if (lockCol != null) sb.append(", ").append(Sql.q(lockCol)).append(" AS a_lock");
        if (phoneCol != null) sb.append(", ").append(Sql.q(phoneCol)).append(" AS a_phone");
        if (pwCol != null) sb.append(", ").append(Sql.q(pwCol)).append(" AS a_pw");
        sb.append(" FROM ").append(m.qTable("sys_users"))
                .append(" WHERE ").append(Sql.q(unCol)).append(" = ?");

        AuthUser u = new AuthUser();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = c.prepareStatement(sb.toString());
            ps.setString(1, username == null ? "" : username.trim());
            rs = ps.executeQuery();
            if (!rs.next()) return null;
            try { u.uid = rs.getInt("a_id"); } catch (Exception ignored) { Diag.ignored("AtiranAuth:110", ignored); }
            try { u.userName = str(rs, "a_un"); } catch (Exception ignored) { Diag.ignored("AtiranAuth:111", ignored); }
            String fn = col(rs, "a_fn"), ln = col(rs, "a_ln");
            u.displayName = (fn + " " + ln).trim();
            if (u.displayName.isEmpty()) u.displayName = u.userName;
            try { u.roleId = rs.getInt("a_role"); } catch (Exception ignored) { Diag.ignored("AtiranAuth:115", ignored); }
            if (actCol != null) {
                try {
                    Object o = rs.getObject("a_act");
                    // Match Atiran clients' convention: a NULL active flag is not an explicit disable.
                    u.active = o == null || flagOn(o);
                } catch (Exception ignored) { Diag.ignored("AtiranAuth:120", ignored); }
            }
            if (lockCol != null) {
                try {
                    Object o = rs.getObject("a_lock");
                    u.locked = flagOn(o);
                } catch (Exception ignored) { Diag.ignored("AtiranAuth:126", ignored); }
            }
            u.phone = col(rs, "a_phone");
            u.pwColFound = (pwCol != null);
            if (pwCol != null) {
                try { u.pw = rs.getBytes("a_pw"); } catch (Exception ignored) { Diag.ignored("AtiranAuth:131", ignored); }
            }
        } finally {
            closeQuiet(rs);
            closeQuiet(ps);
        }
        if (roleCol != null) u.roleName = roleNameOf(c, m, u.roleId);
        if (u.roleName == null || u.roleName.trim().isEmpty())
            u.roleName = roleNameForUser(c, m, u.uid, u.roleId);
        u.appRole = resolveAppRole(u.roleId, u.roleName, u.userName);
        return u;
    }

    /**
     * Authenticate a field visitor from Atiran's visitor account and access-link tables.
     * Credential fields live on dbo.visitors; dbo.sys_vis grants the system link and supplies
     * the positive Atiran UserID used for auditing. Do not look up this login in sys_users.
     * Returns null only when no visitor row has this username, allowing the caller to try the
     * regular sys_users flow for non-visitor roles.
     */
    public static AuthUser fetchVisitorUser(Connection c, String username, String typedPassword)
            throws Exception {
        if (c == null || username == null || username.trim().isEmpty()) return null;
        Meta meta = new Meta(c);
        if (!meta.table("visitors")) return null;
        String visitorIdCol = meta.col("visitors", "vis_rdf", "visitor_id", "VisitorID", "shvis", "rdf");
        String loginCol = meta.col("visitors", "Username", "UserName", "username", "user_name", "login", "login_name");
        String passwordCol = meta.col("visitors", "Password", "password", "Pass", "pass", "pwd", "user_password");
        String activeCol = meta.col("visitors", "active", "Active", "is_active", "enabled");
        String displayCol = meta.col("visitors", "vis_name", "visitor_name", "Name", "name", "FullName");
        if (visitorIdCol == null || loginCol == null || passwordCol == null) return null;

        String sql = visitorLookupSql(meta.qTable("visitors"), visitorIdCol, loginCol,
                passwordCol, activeCol, displayCol);

        int visitorId;
        String storedUsername;
        byte[] storedPassword;
        Object activeValue;
        String displayName;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Integer parsedVisitorId = sqlInteger(rs.getObject(1));
                if (parsedVisitorId == null)
                    throw new VisitorLoginException("شناسهٔ ویزیتور در آتیران معتبر نیست.");
                visitorId = parsedVisitorId;
                storedUsername = readSqlText(rs.getObject(2));
                storedPassword = passwordBytes(rs.getObject(3));
                activeValue = rs.getObject(4);
                displayName = readSqlText(rs.getObject(5));
                if (rs.next())
                    throw new VisitorLoginException("برای این نام کاربری چند ردیف ویزیتور پیدا شد؛ پیوندها باید یکتا شوند.");
            }
        }
        if (visitorId <= 0)
            throw new VisitorLoginException("شناسهٔ ویزیتور در آتیران معتبر نیست.");
        if (activeCol == null || !flagOn(activeValue))
            throw new VisitorLoginException("حساب ویزیتور در آتیران غیرفعال است یا وضعیت فعال‌بودن آن قابل تأیید نیست.");
        String loginName = storedUsername == null || storedUsername.trim().isEmpty()
                ? username.trim() : storedUsername.trim();
        if (!verifyPassword(storedPassword, typedPassword, loginName))
            throw new VisitorLoginException("رمز حساب ویزیتور آتیران تأیید نشد.");

        if (!meta.table("sys_vis"))
            throw new VisitorLoginException("جدول دسترسی ویزیتور آتیران (sys_vis) پیدا نشد.");
        String grantVisitorCol = meta.col("sys_vis", "shvis", "vis_rdf", "visitor_id", "visitor");
        String grantUserCol = meta.col("sys_vis", "UserID", "user_id", "userid");
        String grantSystemCol = meta.col("sys_vis", "SysID", "sys_id", "system_id");
        if (grantVisitorCol == null || grantUserCol == null || grantSystemCol == null)
            throw new VisitorLoginException("ستون‌های پیوند ویزیتور در جدول sys_vis آتیران کامل نیستند.");
        String visitorKeyType = meta.columnType("sys_vis", grantVisitorCol);
        String grantSql = visitorGrantSql(meta.qTable("sys_vis"), grantVisitorCol,
                visitorKeyType, grantUserCol, grantSystemCol);
        int atiranUid = -1;
        try (PreparedStatement ps = c.prepareStatement(grantSql)) {
            if (isNumericSqlType(visitorKeyType)) ps.setInt(1, visitorId);
            else ps.setString(1, String.valueOf(visitorId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer linkedUidValue = sqlInteger(rs.getObject(1));
                    Integer linkedSystemValue = sqlInteger(rs.getObject(2));
                    if (linkedSystemValue == null || linkedUidValue == null || linkedUidValue <= 0) continue;
                    int linkedUid = linkedUidValue;
                    if (atiranUid > 0 && atiranUid != linkedUid)
                        throw new VisitorLoginException("پیوند این ویزیتور در sys_vis به چند کاربر آتیران می‌رسد؛ دسترسی حدسی داده نشد.");
                    atiranUid = linkedUid;
                }
            }
        }
        if (atiranUid <= 0)
            throw new VisitorLoginException("برای این ویزیتور دسترسی به سیستم در جدول sys_vis آتیران ثبت نشده است.");

        AuthUser visitor = new AuthUser();
        visitor.uid = atiranUid;
        visitor.userName = loginName;
        visitor.displayName = displayName == null || displayName.trim().isEmpty()
                ? loginName : displayName.trim();
        // The visitors table has no role column; the login name is the only role
        // evidence on this path. Canonical manager panels (Modir, Admin, …) open
        // the manager edition; every other visitor keeps the visitor workspace,
        // unchanged and scoped to its own sys_vis identity.
        String byUser = managerRoleForUserName(loginName);
        visitor.roleName = byUser.isEmpty() ? "ویزیتور" : "مدیر";
        visitor.appRole = byUser.isEmpty() ? RoleStore.VISITOR : byUser;
        visitor.active = true;
        visitor.pwColFound = true;
        visitor.pw = storedPassword;
        return visitor;
    }

    /**
     * SQL Server 2008-compatible visitor lookup. Read credential columns in their native types;
     * only CAST the login key to text for a stable, case-insensitive comparison. TRY_CONVERT
     * is intentionally absent because this query runs before we know which account type is logging in.
     */
    static String visitorLookupSql(String table, String idCol, String loginCol, String passwordCol,
                                   String activeCol, String displayCol) {
        String active = activeCol == null ? "CAST(NULL AS nvarchar(32))" : Sql.q(activeCol);
        String display = displayCol == null ? "CAST(N'' AS nvarchar(220))" : Sql.q(displayCol);
        String loginText = "CAST(" + Sql.q(loginCol) + " AS nvarchar(200))";
        return "SELECT TOP (2) " + Sql.q(idCol) + ", " + Sql.q(loginCol) + ", " + Sql.q(passwordCol)
                + ", " + active + ", " + display + " FROM " + table
                + " WHERE LOWER(LTRIM(RTRIM(" + loginText + ")))="
                + "LOWER(LTRIM(RTRIM(?)))";
    }

    /** Build the visitor grant query without SQL Server 2012-only TRY_CONVERT. */
    static String visitorGrantSql(String table, String visitorCol, String visitorType,
                                  String userCol, String systemCol) {
        return "SELECT " + Sql.q(userCol) + ", " + Sql.q(systemCol) + " FROM " + table
                + " WHERE " + integerKeyPredicate(visitorCol, visitorType);
    }

    /**
     * Match an Atiran integer key using its catalog type. Text-backed legacy IDs are trimmed
     * and compared as text; numeric columns keep a native indexed equality comparison.
     */
    static String integerKeyPredicate(String column, String sqlType) {
        if (isNumericSqlType(sqlType)) return Sql.q(column) + "=?";
        return "LTRIM(RTRIM(CAST(" + Sql.q(column) + " AS nvarchar(100))))=?";
    }

    static boolean isNumericSqlType(String sqlType) {
        if (sqlType == null) return false;
        String type = sqlType.trim().toLowerCase(Locale.US);
        return "tinyint".equals(type) || "smallint".equals(type) || "int".equals(type)
                || "bigint".equals(type) || "decimal".equals(type) || "numeric".equals(type)
                || "money".equals(type) || "smallmoney".equals(type) || "float".equals(type)
                || "real".equals(type) || "bit".equals(type);
    }

    /** Read common JDBC values without SQL-side TRY_CONVERT; invalid IDs fail closed. */
    static Integer sqlInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
        String text;
        if (value instanceof byte[]) text = new String((byte[]) value, StandardCharsets.UTF_8);
        else text = String.valueOf(value);
        text = Money.en(text).trim();
        if (text.isEmpty()) return null;
        try {
            java.math.BigDecimal parsed = new java.math.BigDecimal(text);
            if (parsed.stripTrailingZeros().scale() > 0) return null;
            return parsed.intValueExact();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Convert a JDBC text value; binary-backed credentials remain byte arrays instead. */
    private static String readSqlText(Object value) {
        if (value == null) return "";
        try {
            if (value instanceof java.sql.Clob) {
                java.sql.Clob c = (java.sql.Clob) value;
                long len = c.length();
                if (len > Integer.MAX_VALUE) return "";
                return c.getSubString(1, (int) len).trim();
            }
            if (value instanceof byte[]) return new String((byte[]) value, StandardCharsets.UTF_8).trim();
            return String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Preserve native binary hashes; text passwords are UTF-8 encoded as before. */
    static byte[] passwordBytes(Object value) {
        if (value == null) return null;
        if (value instanceof byte[]) return ((byte[]) value).clone();
        try {
            if (value instanceof java.sql.Blob) {
                java.sql.Blob b = (java.sql.Blob) value;
                long len = b.length();
                if (len > Integer.MAX_VALUE) return null;
                return b.getBytes(1, (int) len);
            }
            if (value instanceof java.sql.Clob) {
                java.sql.Clob c = (java.sql.Clob) value;
                long len = c.length();
                if (len > Integer.MAX_VALUE) return null;
                return c.getSubString(1, (int) len).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return null;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    /** Best-effort role-name lookup over the unknown Roles/role schema. */
    public static String roleNameOf(Connection c, Meta m, int roleId) {
        String[] tables = {"Roles", "role"};
        String[] ids = {"ID", "id", "RoleID", "role_id", "RoleId", "RoleCode",
                "roleCode", "Code", "code", "RoleRdf", "role_rdf", "RollID"};
        String[] nms = {"Name", "name", "RoleName", "role_name", "Title",
                "title", "RoleTitle", "roleTitle", "Role", "role", "RoleText", "RollName"};
        for (String t : tables) {
            String idCol, nmCol;
            try {
                if (!m.table(t)) continue;
                idCol = m.col(t, ids);
                nmCol = m.col(t, nms);
                if (idCol == null || nmCol == null || !m.canSelect(t)) continue;
            } catch (Exception e) {
                continue;
            }
            PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                ps = c.prepareStatement("SELECT TOP 1 " + Sql.q(nmCol) + " FROM " + m.qTable(t)
                        + " WHERE " + Sql.q(idCol) + " = ?");
                ps.setInt(1, roleId);
                rs = ps.executeQuery();
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.trim().isEmpty()) return v.trim();
                }
            } catch (Exception ignored) { Diag.ignored("AtiranAuth:170", ignored); } finally {
                closeQuiet(rs);
                closeQuiet(ps);
            }
        }
        return "";
    }

    /**
     * Role name for one system user, from every role source Atiran may use:
     * 1. the sys_users.role_id → Roles/role name (via {@link #roleNameOf});
     * 2. the userrole mapping table (user_id → RoleId → role name) when the
     *    account carries its role there instead of sys_users.role_id;
     * 3. a role-name column directly on sys_users, if the database has one.
     * Returns "" when none of the sources is readable — the caller then falls
     * back to the user-name evidence, never to a hard login failure.
     */
    public static String roleNameForUser(Connection c, Meta m, int uid, int roleId) {
        if (roleId > 0) {
            String n = roleNameOf(c, m, roleId);
            if (n != null && !n.trim().isEmpty()) return n.trim();
        }
        try {
            if (uid > 0 && m.table("userrole") && m.canSelect("userrole")) {
                String uCol = m.col("userrole", "user_id", "UserID", "userid", "UserId");
                String rCol = m.col("userrole", "RoleId", "role_id", "RoleID", "RollID");
                if (uCol != null && rCol != null) {
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = c.prepareStatement("SELECT " + Sql.q(rCol) + " FROM " + m.qTable("userrole")
                                + " WHERE " + Sql.q(uCol) + " = ?");
                        ps.setInt(1, uid);
                        rs = ps.executeQuery();
                        while (rs.next()) {
                            int rid;
                            try { rid = rs.getInt(1); } catch (Exception ignored) { continue; }
                            if (rid <= 0) continue;
                            String n = roleNameOf(c, m, rid);
                            if (n != null && !n.trim().isEmpty()) return n.trim();
                        }
                    } finally {
                        closeQuiet(rs);
                        closeQuiet(ps);
                    }
                }
            }
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:roleNameForUser", ignored); }
        try {
            String idCol = m.col("sys_users", "user_id");
            String nmCol = m.col("sys_users", "role_name", "RoleName", "roleName", "RoleTitle",
                    "roleTitle", "RollName", "RoleText");
            if (uid > 0 && idCol != null && nmCol != null) {
                PreparedStatement ps = null;
                ResultSet rs = null;
                try {
                    ps = c.prepareStatement("SELECT TOP 1 " + Sql.q(nmCol) + " FROM " + m.qTable("sys_users")
                            + " WHERE " + Sql.q(idCol) + " = ?");
                    ps.setInt(1, uid);
                    rs = ps.executeQuery();
                    if (rs.next()) {
                        String v = rs.getString(1);
                        if (v != null && !v.trim().isEmpty()) return v.trim();
                    }
                } finally {
                    closeQuiet(rs);
                    closeQuiet(ps);
                }
            }
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:roleNameForUser2", ignored); }
        return "";
    }

    // ================= password verification =================

    /** Visitor credentials are stored as text on Atiran's visitors row; verify locally without logging them. */
    public static boolean verifyVisitorPassword(String stored, String typed, String userName) {
        if (stored == null || stored.isEmpty() || typed == null || typed.isEmpty()) return false;
        return verifyPassword(stored.getBytes(StandardCharsets.UTF_8), typed, userName);
    }

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
        String[] algs = {"MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512"};
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
            String[] sAlgs = {"MD5", "SHA-1", "SHA-256", "SHA-512"};
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:262", ignored); }
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
            ps = c.prepareStatement("SELECT TOP 1 pwdcompare(?, " + Sql.q(pw)
                    + ") FROM " + m.qTable("sys_users") + " WHERE " + Sql.q(un) + " = ?");
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
        if (n == 32 || n == 40 || n == 56 || n == 64 || n == 96 || n == 128) return s.toString();
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
     * Sales-manager phrases are tested before the general manager rule so
     * «مدیر فروش» receives the narrow sales role, not company-wide access.
     * Returns "" when the name carries no recognizable role information, so
     * callers can fall through to the next evidence source (see
     * {@link #resolveAppRole}); {@link #mapRole} keeps the historic
     * unknown→SELLER fallback on top of this.
     */
    static String mapRoleCore(int roleId, String roleName) {
        String n = normalizeRoleText(roleName);
        String l = n.toLowerCase(Locale.US);
        boolean salesLead = (has(n, "مدیر") || has(n, "سرپرست") || has(n, "رئیس")
                || l.contains("manager") || l.contains("lead"))
                && (has(n, "فروش") || l.contains("sales") || l.contains("forosh"));
        if (salesLead || l.contains("sales_manager") || l.contains("sales-manager"))
            return RoleStore.SALES_MANAGER;
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
        return "";
    }

    /**
     * Map an Atiran role name to an app role id; unknown or empty names fall
     * back to SELLER (the documented default). Kept for callers that only have
     * a role name; login resolution goes through {@link #resolveAppRole}.
     */
    public static String mapRole(int roleId, String roleName) {
        String r = mapRoleCore(roleId, roleName);
        return r.isEmpty() ? RoleStore.SELLER : r;
    }

    /**
     * Manager identity from the Atiran user name itself. Used only when the
     * role name carries no information. Matches WHOLE names (never
     * substrings), so personal names like «modirnejad» or «ali_modir» never
     * qualify: admin / administrator / sysadmin / sys_admin / modir /
     * modiramel / modir_amel — optionally with a numeric suffix such as
     * admin2 — plus the Persian «مدیر» and «مدیرعامل».
     */
    public static String managerRoleForUserName(String userName) {
        if (userName == null) return "";
        String u = normalizeRoleText(Money.en(userName)).toLowerCase(Locale.US).trim();
        if (u.isEmpty()) return "";
        if (u.equals("مدیر") || u.equals("مدیرعامل")) return RoleStore.ADMIN;
        if (u.matches("^(admin|administrator|sysadmin|sys_admin|modir_amel|modiramel|modir)([._-]?\\d+)?$"))
            return RoleStore.ADMIN;
        return "";
    }

    /**
     * Effective app role from the Atiran evidence, in order:
     * 1. an informative role name (Roles/role/userrole) always wins;
     * 2. when the role name is missing or unknown, canonical manager user
     *    names (Modir, Admin, …) decide — see {@link #managerRoleForUserName};
     * 3. everything else falls back to the documented default (SELLER).
     */
    public static String resolveAppRole(int roleId, String roleName, String userName) {
        String mapped = mapRoleCore(roleId, roleName);
        if (!mapped.isEmpty()) return mapped;
        String byUser = managerRoleForUserName(userName);
        return byUser.isEmpty() ? RoleStore.SELLER : byUser;
    }

    /** Canonicalize the Arabic/Persian code points often mixed in Atiran role labels. */
    private static String normalizeRoleText(String value) {
        if (value == null) return "";
        return value.replace('\u064A', '\u06CC').replace('\u0643', '\u06A9')
                .replace('\u200C', ' ').replace('\u200E', ' ').replace('\u200F', ' ')
                .trim().replaceAll("\\s+", " ");
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

    /** Legacy preference retained for migration; it cannot bypass Atiran authentication. */
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:400", ignored); }
    }

    /** Legacy marker from older builds; it no longer bypasses the app account gate. */
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:415", ignored); }
    }

    public static void clearNoTable(Context c) {
        try {
            prefs(c).edit().putBoolean("no_table", false).apply();
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:421", ignored); }
    }

    /** Require a live Atiran-backed session; synthetic/local user IDs cannot open the shell. */
    public static boolean gate(Context c) {
        try {
            return !validSession(c) || sessionUid(c) <= 0;
        } catch (Exception e) {
            return true;
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:499", ignored); }
    }

    public static void clearSession(Context c) {
        try {
            prefs(c).edit().putLong("s_until", 0).putBoolean("s_boot", false).apply();
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:505", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:521", ignored); }
    }

    public static String startScreen(Context c) {
        try {
            String r = sessionRole(c);
            if (!r.isEmpty()) return RoleStore.homeFor(r);
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:528", ignored); }
        return "home";
    }

    // ---- per-user app PIN (4+ chars, PBKDF2 v2; legacy SHA-256 accepted + upgraded) ----

    private static final String LEGACY_PIN_PREFIX = "meelano_loginpin:";

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
            String norm = Money.en(pin).trim();
            if (!PinHash.verify(norm, saved, LEGACY_PIN_PREFIX)) return false;
            if (PinHash.legacy(saved)) {
                String h = PinHash.hash(norm);
                if (!h.isEmpty()) prefs(c).edit().putString("pin" + uid, h).apply();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setPin(Context c, int uid, String pinOrNull) {
        try {
            String pin = pinOrNull == null ? "" : Money.en(pinOrNull).trim();
            SharedPreferences.Editor e = prefs(c).edit();
            if (pin.length() < 4) e.putString("pin" + uid, "");
            else e.putString("pin" + uid, PinHash.hash(pin)); // "" on failure → pinSet()=false (fail closed)
            e.apply();
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:559", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:580", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:612", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:646", ignored); }
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
     * ONE app — the Atiran role picks the edition. Every defined role is live:
     * the role's own item set (RoleStore) decides what it sees, so a valid
     * Atiran login never dead-ends on an «edition not ready» wall. Only
     * strings that are not roles at all stay blocked.
     */
    public static boolean phaseOpen(Context c, String role) {
        return RoleStore.isValid(role);
    }

    // ---- cached verifier bytes: the real-password check, offline ----

    private static final String PW_ALIAS = "meelano_pw_v1";
    private static final String PW_PREFIX = "g1:";

    /** Keystore-backed AES-GCM key; the key never leaves the Android Keystore. */
    private static javax.crypto.SecretKey pwKey(boolean create) throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(PW_ALIAS)) {
            return (javax.crypto.SecretKey) ks.getKey(PW_ALIAS, null);
        }
        if (!create) return null;
        javax.crypto.KeyGenerator gen = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new android.security.keystore.KeyGenParameterSpec.Builder(PW_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                        | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }

    /**
     * Cache the stored verifier bytes after a successful fetch, encrypted with a
     * Keystore key (not plain prefs). If the Keystore is unavailable the cache is
     * skipped: online login still works, only the offline fallback is unavailable.
     */
    public static void putPwCache(Context c, int uid, byte[] pw) {
        try {
            if (pw == null || pw.length == 0) return;
            javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(javax.crypto.Cipher.ENCRYPT_MODE, pwKey(true));
            byte[] iv = ci.getIV();
            byte[] ct = ci.doFinal(pw);
            String v = PW_PREFIX + android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
                    + ":" + android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP);
            // Drop any legacy plaintext copy written by older builds.
            prefs(c).edit().remove("pwb" + uid).putString("pwk" + uid, v).apply();
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:putPwCache", ignored); }
    }

    public static byte[] cachedPw(Context c, int uid) {
        try {
            prefs(c).edit().remove("pwb" + uid).apply(); // legacy plaintext, if any
            String v = prefs(c).getString("pwk" + uid, "");
            if (!v.startsWith(PW_PREFIX)) return null;
            String[] parts = v.substring(PW_PREFIX.length()).split(":", 2);
            if (parts.length != 2) return null;
            byte[] iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP);
            byte[] ct = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP);
            javax.crypto.Cipher ci = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(javax.crypto.Cipher.DECRYPT_MODE, pwKey(false),
                    new javax.crypto.spec.GCMParameterSpec(128, iv));
            return ci.doFinal(ct);
        } catch (Exception e) {
            Diag.ignored("AtiranAuth:cachedPw", e);
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
        return readSqlText(rs.getObject(alias));
    }

    private static String col(ResultSet rs, String alias) {
        try {
            return readSqlText(rs.getObject(alias));
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeQuiet(Object o) {
        try {
            if (o instanceof ResultSet) ((ResultSet) o).close();
            else if (o instanceof java.sql.Statement) ((java.sql.Statement) o).close();
        } catch (Exception ignored) { Diag.ignored("AtiranAuth:749", ignored); }
    }


}
