package ir.meelano.windowsinvoice.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Atiran account login on Windows: the operator signs in with their Atiran user name and
 * password against the selected database, and the resulting role drives the desktop menus.
 * Read-only: no row is inserted or updated, and credentials are never logged or persisted here.
 */
public final class AtiranLogin {
    private AtiranLogin() { }

    private static final String[] USER_TABLES = {
            "sysusers", "sys_users", "users", "user", "personnel", "staff", "karmand"};
    private static final String[] VISITOR_TABLES = {"visitors", "visitor", "bazaryab", "vis"};
    private static final String[] ROLE_TABLES = {"roles", "role", "userroles", "accesslevel", "groups"};
    private static final String[] ID_COLS = {"id", "userid", "user_id", "shsysuser", "shno", "code", "rowid"};
    private static final String[] NAME_COLS = {"username", "user_name", "login", "login_name", "name",
            "uname", "karbar", "codemelli", "mobile", "cell"};
    private static final String[] PASS_COLS = {"password", "pass", "pwd", "passwd", "userpass", "user_pass",
            "ramz", "kalameobor"};
    private static final String[] DISPLAY_COLS = {"fullname", "full_name", "name", "moname", "title",
            "firstname", "lastname", "fname", "lname"};
    private static final String[] ROLE_ID_COLS = {"roleid", "role_id", "groupid", "group_id", "level",
            "accesslevel", "userlevel", "shrole"};
    private static final String[] ROLE_NAME_COLS = {"rolename", "role_name", "name", "title", "groupname",
            "levelname", "description"};
    private static final String[] ACTIVE_COLS = {"active", "isactive", "enabled", "status"};
    private static final String[] LOCKED_COLS = {"locked", "islocked", "blocked", "disabled"};
    private static final String[] WAREHOUSE_COLS = {"anbar", "warehouse", "warehouseid", "warehouse_id", "whid"};

    /** The signed-in Atiran identity plus the resolved desktop role. */
    public static final class Session {
        public final String username;
        public final String displayName;
        public final String roleName;
        public final String appRole;
        public final int userId;
        public final int visitorId;
        public final int warehouseId;
        public final String source;

        Session(String username, String displayName, String roleName, String appRole,
                int userId, int visitorId, int warehouseId, String source) {
            this.username = username;
            this.displayName = displayName;
            this.roleName = roleName;
            this.appRole = appRole;
            this.userId = userId;
            this.visitorId = visitorId;
            this.warehouseId = warehouseId;
            this.source = source;
        }

        public String title() {
            String name = displayName == null || displayName.isBlank() ? username : displayName;
            return roleName == null || roleName.isBlank() ? name : name + " · " + roleName;
        }
    }

    /**
     * Verifies the Atiran account inside the selected database and resolves its role.
     * Throws with a Persian message when the account, password, or schema is unusable.
     */
    public static Session login(WindowsSqlSettings.Endpoint endpoint,
                                String atiranUser, String atiranPass) throws Exception {
        String user = atiranUser == null ? "" : atiranUser.trim();
        if (user.isEmpty() || atiranPass == null || atiranPass.isEmpty())
            throw new IllegalArgumentException("نام کاربری و رمز آتیران را وارد کن.");
        if (endpoint == null || !endpoint.complete())
            throw new IllegalStateException("اتصال SQL ذخیره‌شده کامل نیست؛ ابتدا سرور و دیتابیس را تنظیم کن.");

        try (Connection c = WindowsServerDiscovery.open(endpoint.host, endpoint.port,
                endpoint.database, endpoint.username, endpoint.password)) {
            Set<String> tables = tables(c);
            Session system = loginInTables(c, tables, USER_TABLES, "sysUsers", user, atiranPass);
            Session visitor = loginInTables(c, tables, VISITOR_TABLES, "visitors", user, atiranPass);
            if (system == null && visitor == null)
                throw new IllegalStateException("حساب آتیران با این نام کاربری و رمز پیدا نشد.");
            Session primary = system != null ? system : visitor;
            Session merged = primary;
            if (system != null && visitor != null && system.visitorId <= 0)
                merged = new Session(system.username, blankTo(system.displayName, visitor.displayName),
                        system.roleName, system.appRole, system.userId, visitor.visitorId,
                        system.warehouseId, system.source);
            if (visitor != null && system == null && merged.visitorId <= 0)
                merged = new Session(visitor.username, visitor.displayName, visitor.roleName,
                        visitor.appRole, visitor.userId, visitor.userId, visitor.warehouseId,
                        visitor.source);
            return merged;
        }
    }

    private static Session loginInTables(Connection c, Set<String> tables, String[] candidates,
                                         String kind, String user, String pass) {
        for (String candidate : candidates) {
            String table = firstExisting(tables, candidate);
            if (table == null) continue;
            try {
                Session session = loginInTable(c, table, kind, user, pass);
                if (session != null) return session;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static Session loginInTable(Connection c, String table, String kind,
                                        String user, String pass) throws Exception {
        Set<String> columns = columnNames(c, table);
        String idCol = firstColumn(columns, ID_COLS);
        String userCol = firstColumn(columns, NAME_COLS);
        String passCol = firstColumn(columns, PASS_COLS);
        if (userCol == null) return null;
        if (passCol == null && !"visitors".equals(kind)) return null;

        String displayCol = firstColumn(columns, DISPLAY_COLS);
        String roleIdCol = firstColumn(columns, ROLE_ID_COLS);
        String activeCol = firstColumn(columns, ACTIVE_COLS);
        String lockedCol = firstColumn(columns, LOCKED_COLS);
        String warehouseCol = firstColumn(columns, WAREHOUSE_COLS);

        StringBuilder select = new StringBuilder("SELECT ");
        List<String> selected = new ArrayList<>();
        if (idCol != null) selected.add(idCol);
        selected.add(userCol);
        if (passCol != null) selected.add(passCol);
        if (displayCol != null) selected.add(displayCol);
        if (roleIdCol != null) selected.add(roleIdCol);
        if (activeCol != null) selected.add(activeCol);
        if (lockedCol != null) selected.add(lockedCol);
        if (warehouseCol != null) selected.add(warehouseCol);
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) select.append(", ");
            select.append('[').append(selected.get(i)).append(']');
        }
        select.append(" FROM [dbo].[").append(table).append("] WHERE LOWER(LTRIM(RTRIM(CONVERT(nvarchar(200),[")
                .append(userCol).append("]))) = LOWER(LTRIM(RTRIM(?)))");

        try (PreparedStatement ps = c.prepareStatement(select.toString())) {
            ps.setQueryTimeout(15);
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (activeCol != null && flagOff(readText(rs, activeCol))) continue;
                    if (lockedCol != null && flagOn(readText(rs, lockedCol))) continue;
                    byte[] stored = passCol == null ? null : readBytes(rs, passCol);
                    boolean verified = passCol == null
                            ? verifyServerSide(c, table, userCol, passCol, user, pass)
                            : verify(stored, pass, user, c, table, passCol, userCol);
                    if (!verified) continue;

                    int userId = idCol == null ? 0 : readInt(rs, idCol);
                    String display = displayCol == null ? "" : readText(rs, displayCol);
                    int roleId = roleIdCol == null ? 0 : readInt(rs, roleIdCol);
                    int warehouse = warehouseCol == null ? 0 : Math.max(0, readInt(rs, warehouseCol));
                    String roleName = roleId > 0 ? roleNameById(c, tables(c), roleId) : "";
                    String appRole = AtiranRoles.resolve(roleId, roleName, user);
                    int visitorId = "visitors".equals(kind) ? Math.max(userId, 0) : visitorIdFor(c, user, display);
                    return new Session(user, display, roleName, appRole, userId, visitorId, warehouse, table);
                }
            }
        }
        return null;
    }

    /** SQL-native check for values stored with pwdencrypt(); safe to attempt and never logs. */
    private static boolean verifyServerSide(Connection c, String table, String userCol,
                                            String passCol, String user, String pass) {
        if (passCol == null) return false;
        String sql = "SELECT CASE WHEN pwdcompare(?, CONVERT(sql_variant,[" + passCol + "])) = 1 THEN 1 ELSE 0 END"
                + " FROM [dbo].[" + table + "] WHERE LOWER(LTRIM(RTRIM(CONVERT(nvarchar(200),["
                + userCol + "]))) = LOWER(LTRIM(RTRIM(?)))";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, pass);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean verify(byte[] stored, String typed, String user, Connection c,
                                  String table, String passCol, String userCol) {
        if (stored == null || stored.length == 0) return false;
        if (verifyServerSide(c, table, userCol, passCol, user, typed)) return true;
        String[] charsets = {"UTF-8", "UTF-16LE", "windows-1256", "ISO-8859-1"};
        String[] algorithms = {"MD5", "SHA-1", "SHA-256", "SHA-512"};
        String[] texts = {typed, typed.trim()};
        try {
            for (String text : texts) {
                for (String charset : charsets) {
                    byte[] raw;
                    try {
                        raw = text.getBytes(charset);
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (constantTimeEquals(stored, raw)) return true;
                    for (String algorithm : algorithms) {
                        byte[] digest = digest(algorithm, raw);
                        if (digest != null && constantTimeEquals(stored, digest)) return true;
                        String hex = toHex(digest);
                        if (hex != null && hex.equalsIgnoreCase(asciiHex(stored))) return true;
                    }
                }
            }
            for (String text : texts) {
                for (String salt : new String[]{user, user == null ? "" : user.toLowerCase(Locale.ROOT)}) {
                    if (salt == null || salt.isEmpty()) continue;
                    for (String combined : new String[]{salt + text, text + salt}) {
                        for (String charset : new String[]{"UTF-8", "UTF-16LE"}) {
                            byte[] raw;
                            try {
                                raw = combined.getBytes(charset);
                            } catch (Exception ignored) {
                                continue;
                            }
                            for (String algorithm : algorithms) {
                                byte[] digest = digest(algorithm, raw);
                                if (digest != null && constantTimeEquals(stored, digest)) return true;
                                String hex = toHex(digest);
                                if (hex != null && hex.equalsIgnoreCase(asciiHex(stored))) return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static String roleNameById(Connection c, Set<String> tables, int roleId) {
        for (String candidate : ROLE_TABLES) {
            String table = firstExisting(tables, candidate);
            if (table == null) continue;
            try {
                Set<String> columns = columnNames(c, table);
                String idCol = firstColumn(columns, ID_COLS);
                String nameCol = firstColumn(columns, ROLE_NAME_COLS);
                if (idCol == null || nameCol == null) continue;
                String sql = "SELECT TOP (1) [" + nameCol + "] FROM [dbo].[" + table + "] WHERE [" + idCol + "] = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setQueryTimeout(10);
                    ps.setInt(1, roleId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String name = readText(rs, nameCol);
                            if (name != null && !name.isBlank()) return name.trim();
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        return "";
    }

    private static int visitorIdFor(Connection c, String user, String display) {
        Set<String> tables = tables(c);
        String table = null;
        for (String candidate : VISITOR_TABLES) {
            table = firstExisting(tables, candidate);
            if (table != null) break;
        }
        if (table == null) return 0;
        try {
            Set<String> columns = columnNames(c, table);
            String idCol = firstColumn(columns, ID_COLS);
            String userCol = firstColumn(columns, NAME_COLS);
            String displayCol = firstColumn(columns, DISPLAY_COLS);
            if (idCol == null || (userCol == null && displayCol == null)) return 0;
            String matchCol = userCol != null ? userCol : displayCol;
            String value = userCol != null ? user : display;
            if (value == null || value.isBlank()) return 0;
            String sql = "SELECT TOP (1) [" + idCol + "] FROM [dbo].[" + table
                    + "] WHERE LOWER(LTRIM(RTRIM(CONVERT(nvarchar(200),[" + matchCol
                    + "]))) = LOWER(LTRIM(RTRIM(?)))";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(10);
                ps.setString(1, value);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Math.max(0, readInt(rs, idCol)) : 0;
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Set<String> tables(Connection c) {
        Set<String> out = new LinkedHashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) out.add(name.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static Set<String> columnNames(Connection c, String table) {
        Set<String> out = new LinkedHashSet<>();
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(10);
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name != null && !name.isBlank()) out.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String firstExisting(Set<String> tables, String candidate) {
        return tables.contains(candidate.toLowerCase(Locale.ROOT)) ? candidate : null;
    }

    private static String firstColumn(Set<String> columns, String[] candidates) {
        for (String candidate : candidates)
            if (columns.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        for (String column : columns) {
            String normalized = column.replace("_", "");
            for (String candidate : candidates)
                if (normalized.equals(candidate.replace("_", ""))) return column;
        }
        return null;
    }

    private static String readText(ResultSet rs, String column) {
        try {
            String value = rs.getString(column);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int readInt(ResultSet rs, String column) {
        try {
            int value = rs.getInt(column);
            return rs.wasNull() ? 0 : value;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static byte[] readBytes(ResultSet rs, String column) {
        try {
            Object value = rs.getObject(column);
            if (value == null) return null;
            if (value instanceof byte[]) return ((byte[]) value).clone();
            if (value instanceof java.sql.Blob) {
                java.sql.Blob blob = (java.sql.Blob) value;
                long length = blob.length();
                if (length <= 0 || length > 4096) return null;
                return blob.getBytes(1, (int) length);
            }
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean flagOn(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("t") || v.equals("true") || v.equals("y") || v.equals("yes");
    }

    private static boolean flagOff(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("0") || v.equals("f") || v.equals("false") || v.equals("n") || v.equals("no");
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }

    private static byte[] digest(String algorithm, byte[] input) {
        try {
            return MessageDigest.getInstance(algorithm).digest(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toHex(byte[] value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private static String asciiHex(byte[] value) {
        if (value == null) return null;
        Set<Character> hexDigits = new HashSet<>();
        for (char c : "0123456789abcdefABCDEF".toCharArray()) hexDigits.add(c);
        String text = new String(value, StandardCharsets.US_ASCII).trim();
        if (text.length() % 2 != 0) return null;
        for (int i = 0; i < text.length(); i++) if (!hexDigits.contains(text.charAt(i))) return null;
        return text;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
