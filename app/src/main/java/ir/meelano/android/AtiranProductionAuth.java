package ir.meelano.android;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One-operation identity verification for manager-only production writes. */
final class AtiranProductionAuth {
    static final String ADMIN = "admin";
    private AtiranProductionAuth() { }

    static final class AuthUser {
        int uid;
        String userName = "";
        String roleName = "";
        String appRole = "user";
        boolean active = true;
        boolean locked;
        byte[] pw;
    }

    static AuthUser fetchUser(Connection c, String username) throws Exception {
        ProductionMeta meta = new ProductionMeta(c);
        if (!meta.table("sys_users")) throw new Exception("جدول کاربران آتیران (sys_users) پیدا نشد.");
        String table = meta.qTable("sys_users");
        String id = meta.col("sys_users", "user_id", "UserID", "userid", "id", "ID");
        String name = meta.col("sys_users", "user_name", "UserName", "username", "login", "login_name");
        String role = meta.col("sys_users", "role_id", "RoleID", "RoleId");
        String active = meta.col("sys_users", "active", "Active", "is_active", "enabled");
        String locked = meta.col("sys_users", "IsLocked", "islocked", "is_locked", "Locked");
        String password = meta.col("sys_users", "user_password", "UserPassword", "password", "Password");
        if (id == null || name == null) throw new Exception("شناسه یا نام کاربری در sys_users آتیران پیدا نشد.");
        String sql = "SELECT TOP (1) " + ProductionMeta.quote(id) + " AS uid," + ProductionMeta.quote(name) + " AS uname," +
                (role == null ? "CAST(NULL AS int)" : ProductionMeta.quote(role)) + " AS role_id," +
                (active == null ? "CAST(NULL AS int)" : ProductionMeta.quote(active)) + " AS is_active," +
                (locked == null ? "CAST(NULL AS int)" : ProductionMeta.quote(locked)) + " AS is_locked," +
                (password == null ? "CAST(NULL AS varbinary(1))" : ProductionMeta.quote(password)) + " AS pw " +
                "FROM " + table + " WHERE " + ProductionMeta.quote(name) + "=?";
        AuthUser user = new AuthUser();
        int roleId = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                user.uid = rs.getInt("uid");
                user.userName = safe(rs.getString("uname"));
                roleId = rs.getInt("role_id");
                if (active != null) user.active = flag(rs.getObject("is_active"), true);
                if (locked != null) user.locked = flag(rs.getObject("is_locked"), false);
                if (password != null) user.pw = rs.getBytes("pw");
            }
        }
        user.roleName = roleName(c, meta, roleId);
        user.appRole = mapRole(user.roleName);
        return user;
    }

    static int authenticateManager(Connection c, String username, String typedPassword) throws Exception {
        String name = safe(username).trim();
        if (name.isEmpty() || typedPassword == null || typedPassword.isEmpty())
            throw new Exception("نام کاربری و رمز عبور مدیر آتیران را وارد کنید.");
        AuthUser user = fetchUser(c, name);
        if (user == null) throw new Exception("این نام کاربری در sys_users آتیران پیدا نشد.");
        if (!user.active || user.locked) throw new Exception("حساب آتیران غیرفعال یا قفل است.");
        if (!ADMIN.equals(mapRole(user.roleName))) throw new Exception("برای ثبت تولید، حساب فعال دارای نقش مدیر آتیران لازم است.");
        if (!verifyPasswordServer(c, user.userName, typedPassword) && !verifyPassword(user.password, typedPassword, user.userName))
            throw new Exception("رمز عبور حساب مدیر آتیران تأیید نشد.");
        if (user.uid <= 0) throw new Exception("شناسهٔ کاربر مدیر آتیران معتبر نیست.");
        return user.uid;
    }

    static int resolveManagerId(Connection c, int uid) throws Exception {
        if (uid <= 0) throw new Exception("حساب محلی شناسهٔ آتیران ندارد؛ برای این عملیات با حساب مدیر آتیران دوباره احراز هویت کنید.");
        ProductionMeta meta = new ProductionMeta(c);
        String id = meta.col("sys_users", "user_id", "UserID", "userid", "id", "ID");
        String name = meta.col("sys_users", "user_name", "UserName", "username", "login", "login_name");
        if (id == null || name == null) throw new Exception("ساختار sys_users آتیران قابل بررسی نیست.");
        String username;
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) " + ProductionMeta.quote(name) + " FROM " + meta.qTable("sys_users") + " WHERE " + ProductionMeta.quote(id) + "=?")) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new Exception("کاربر نشست در آتیران پیدا نشد."); username = rs.getString(1); }
        }
        AuthUser user = fetchUser(c, username);
        if (user == null || user.uid != uid || !user.active || user.locked) throw new Exception("کاربر نشست در آتیران دیگر فعال نیست.");
        if (!ADMIN.equals(mapRole(user.roleName))) throw new Exception("ثبت تولید فقط با نقش مدیر آتیران مجاز است.");
        return uid;
    }

    private static String roleName(Connection c, ProductionMeta meta, int roleId) {
        String[] tables = {"Roles", "role"};
        String[] ids = {"ID", "id", "RoleID", "role_id", "RoleId", "RoleCode", "roleCode", "Code", "code", "RoleRdf", "role_rdf", "RollID"};
        String[] names = {"Name", "name", "RoleName", "role_name", "Title", "title", "RoleTitle", "roleTitle", "Role", "RoleText", "RollName"};
        for (String table : tables) {
            try {
                if (!meta.table(table)) continue;
                String id = meta.col(table, ids), name = meta.col(table, names);
                if (id == null || name == null) continue;
                try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) " + ProductionMeta.quote(name) + " FROM " + meta.qTable(table) + " WHERE " + ProductionMeta.quote(id) + "=?")) {
                    ps.setInt(1, roleId);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { String value=rs.getString(1); if(value!=null&&!value.trim().isEmpty())return value.trim(); } }
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    static String mapRole(String value) {
        String n = safe(value).toLowerCase(Locale.US);
        if (n.contains("مدیر") || n.contains("مدير") || n.contains("admin") || n.contains("modir") || n.contains("manage") || n.contains("sysadmin")) return ADMIN;
        return "user";
    }

    static boolean verifyPasswordServer(Connection c, String username, String typed) {
        try {
            ProductionMeta meta = new ProductionMeta(c);
            String name = meta.col("sys_users", "user_name", "UserName", "username");
            String password = meta.col("sys_users", "user_password", "UserPassword");
            if (name == null || password == null) return false;
            try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) pwdcompare(?," + ProductionMeta.quote(password) + ") FROM " + meta.qTable("sys_users") + " WHERE " + ProductionMeta.quote(name) + "=?")) {
                ps.setString(1, typed); ps.setString(2, username);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
            }
        } catch (Exception ignored) { return false; }
    }

    static boolean verifyPassword(byte[] stored, String typed, String username) {
        if (stored == null || stored.length == 0 || typed == null) return false;
        String[] texts = {typed, typed.trim()};
        String[] charsets = {"UTF-8", "UTF-16LE", "UTF-16BE", "windows-1256"};
        String[] algorithms = {"MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384"};
        try {
            for (String text : texts) for (String charset : charsets) {
                byte[] raw = text.getBytes(charset);
                if (equal(stored, raw)) return true;
                for (String algorithm : algorithms) if (equal(stored, digest(algorithm, raw))) return true;
            }
            List<String> salts = new ArrayList<>();
            String u = safe(username).trim();
            if (!u.isEmpty()) { salts.add(u); if(!u.equals(u.toUpperCase(Locale.US)))salts.add(u.toUpperCase(Locale.US)); if(!u.equals(u.toLowerCase(Locale.US)))salts.add(u.toLowerCase(Locale.US)); }
            for (String text : texts) for (String salt : salts) for (String combo : new String[]{salt+text,text+salt})
                for (String charset : new String[]{"UTF-8","UTF-16LE"}) for (String algorithm : new String[]{"MD5","SHA-1","SHA-256"})
                    if (equal(stored,digest(algorithm,combo.getBytes(charset)))) return true;
            String hex = asciiHex(stored);
            if (hex != null) for (String text : texts) for (String charset : charsets) for (String algorithm : algorithms)
                if (hex.equalsIgnoreCase(hex(digest(algorithm,text.getBytes(charset))))) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private static byte[] digest(String algorithm, byte[] input) {
        try { return MessageDigest.getInstance(algorithm).digest(input); } catch (Exception ignored) { return null; }
    }
    private static boolean equal(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff=0; for(int i=0;i<a.length;i++)diff|=a[i]^b[i]; return diff==0;
    }
    private static String hex(byte[] bytes) {
        if(bytes==null)return ""; StringBuilder out=new StringBuilder(); for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255)); return out.toString();
    }
    private static String asciiHex(byte[] bytes) {
        if(bytes==null||bytes.length>128)return null; StringBuilder out=new StringBuilder();
        for(byte b:bytes){char c=(char)(b&255);if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')||(c>='A'&&c<='F')))return null;out.append(c);}
        int n=out.length();return n==32||n==40||n==56||n==64||n==96?out.toString():null;
    }
    private static boolean flag(Object value, boolean defaultValue) {
        if(value==null)return defaultValue; String v=String.valueOf(value).trim().toLowerCase(Locale.US);
        return !(v.equals("0")||v.equals("false")||v.equals("f")||v.equals("n")||v.equals("no")||v.equals("inactive")||v.equals("disabled")||v.equals("locked"));
    }
    private static String safe(String value) { return value == null ? "" : value.replace('\u0000',' ').trim(); }
}
