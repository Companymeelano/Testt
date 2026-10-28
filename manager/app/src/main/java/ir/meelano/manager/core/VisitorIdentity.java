package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the authenticated Atiran account to the Atiran visitor row behind it.
 *
 * Atiran grants a visitor's system access through dbo.sys_vis (UserID → shvis) and keeps the
 * login name in dbo.visitors.Username. The session id the app receives is exactly that
 * sys_vis.UserID, so the account is resolved by walking the same links in reverse:
 *   1. sys_vis   : UserID = session id → the visitor ids this account owns
 *   2. visitors  : Username = the Atiran login name → the visitor that owns the login
 * The primary visitor must be unambiguous; every id the account owns is kept so customer and
 * report scoping stays inside the signed-in account. Nothing is ever guessed from a name.
 */
public final class VisitorIdentity {
    private VisitorIdentity() { }

    public static final class Identity {
        /** The Atiran system user id (sys_users.user_id, taken from sys_vis.UserID). */
        public final int userId;
        /** The visitor that owns the account; every write is attributed to it. */
        public final int visitorId;
        /** Every visitor id this Atiran account owns; read scoping uses the whole set. */
        public final int[] visitorIds;
        public final String username;
        public final String displayName;

        Identity(int userId, int visitorId, int[] visitorIds, String username, String displayName) {
            this.userId = userId;
            this.visitorId = visitorId;
            this.visitorIds = visitorIds == null || visitorIds.length == 0
                    ? new int[]{visitorId} : visitorIds;
            this.username = username == null ? "" : username.trim();
            this.displayName = displayName == null ? "" : displayName.trim();
        }
    }

    /**
     * Resolves the visitor behind the session.
     * @param uid the Atiran system user id of the session (sys_vis.UserID)
     * @param username the Atiran login name of the session (visitors.Username)
     */
    public static Identity resolve(Connection c, int uid, String username) throws Exception {
        if (uid <= 0)
            throw new Exception("نشست آتیران شناسهٔ کاربر معتبری ندارد؛ دوباره وارد شوید.");
        Meta m = new Meta(c);
        if (!m.table("visitors")) throw new Exception("جدول ویزیتورهای آتیران پیدا نشد.");
        String id = m.col("visitors", "vis_rdf", "visitor_id", "VisitorID", "shvis", "rdf");
        String name = m.col("visitors", "vis_name", "name", "Name", "VisitorName");
        String active = m.col("visitors", "active", "Active", "is_active", "enabled");
        String login = m.col("visitors", "Username", "UserName", "username", "user_name", "login", "login_name");
        if (id == null) throw new Exception("شناسهٔ ویزیتور در جدول آتیران پیدا نشد.");
        if (active == null)
            throw new Exception("وضعیت فعال‌بودن ویزیتور در آتیران قابل بررسی نیست؛ دسترسی حدسی داده نشد.");

        // 1) the Atiran access grant: sys_vis.UserID (the session id) → sys_vis.shvis.
        List<Integer> bySystem = visitorIdsFromSysVis(c, m, uid);
        // 2) the login name, matched exactly as Atiran matches it when the visitor signs in.
        List<Integer> byLogin = new ArrayList<>();
        if (login != null && username != null && !username.trim().isEmpty())
            byLogin = visitorIdsByLogin(c, m, id, login, username.trim());

        Set<Integer> owned = new LinkedHashSet<>();
        owned.addAll(bySystem);
        owned.addAll(byLogin);
        if (owned.isEmpty())
            throw new Exception("برای این حساب آتیران ردیف ویزیتور پیدا نشد؛ مدیر آتیران باید پیوند حساب به ویزیتور را برقرار کند.");

        int primary = pickPrimary(byLogin, bySystem);
        String[] row = visitorRow(c, m, id, name, active, primary);
        if (!isActive(row[1]))
            throw new Exception("ردیف ویزیتور در آتیران غیرفعال است.");
        int[] ids = new int[owned.size()];
        int i = 0;
        for (Integer v : owned) ids[i++] = v;
        return new Identity(uid, primary, ids, username, row[0]);
    }

    /**
     * The visitor that owns the account. The login name wins because Atiran itself refuses a
     * duplicate login; the sys_vis grant is the fallback when the login name is not stored.
     * Two different links pointing at two different visitors is a data problem, not a guess.
     */
    static int pickPrimary(List<Integer> byLogin, List<Integer> bySystem) throws Exception {
        if (byLogin != null && byLogin.size() == 1) return byLogin.get(0);
        if (byLogin != null && byLogin.size() > 1)
            throw new Exception("برای این نام کاربری چند ردیف ویزیتور پیدا شد؛ پیوندها باید یکتا شوند.");
        int sys = bySystem == null ? 0 : bySystem.size();
        if (sys == 1) return bySystem.get(0);
        if (sys == 0)
            throw new Exception("برای این حساب آتیران ردیف ویزیتور پیدا نشد؛ مدیر آتیران باید پیوند حساب به ویزیتور را برقرار کند.");
        throw new Exception("این حساب آتیران به چند ویزیتور وصل است؛ پیوند را در آتیران یکتا کنید.");
    }

    /** The visitor ids the Atiran system user owns, through the sys_vis access table. */
    private static List<Integer> visitorIdsFromSysVis(Connection c, Meta m, int uid) {
        List<Integer> out = new ArrayList<>();
        try {
            if (!m.table("sys_vis")) return out;
            String shvis = m.col("sys_vis", "shvis", "vis_rdf", "visitor_id", "visitor");
            String user = m.col("sys_vis", "UserID", "user_id", "userid");
            if (shvis == null || user == null) return out;
            String sql = "SELECT DISTINCT TRY_CONVERT(int," + q(shvis) + ") FROM " + m.qTable("sys_vis")
                    + " WHERE TRY_CONVERT(int," + q(user) + ")=? AND TRY_CONVERT(int," + q(shvis) + ") IS NOT NULL";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int v = rs.getInt(1);
                        if (!rs.wasNull() && v > 0 && !out.contains(v)) out.add(v);
                    }
                }
            }
        } catch (Exception e) {
            // sys_vis is optional for the identity: the login name can still resolve the visitor.
            Diag.ignored("VisitorIdentity:sys_vis", e);
        }
        return out;
    }

    /** The visitor id that owns the Atiran login name (exact match, like Atiran's own login). */
    private static List<Integer> visitorIdsByLogin(Connection c, Meta m, String idCol, String loginCol,
                                                   String username) throws Exception {
        List<Integer> out = new ArrayList<>();
        String sql = "SELECT DISTINCT TRY_CONVERT(int," + q(idCol) + ") FROM " + m.qTable("visitors")
                + " WHERE LOWER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(200)," + q(loginCol)
                + "))))=LOWER(LTRIM(RTRIM(?))) AND TRY_CONVERT(int," + q(idCol) + ") IS NOT NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int v = rs.getInt(1);
                    if (!rs.wasNull() && v > 0 && !out.contains(v)) out.add(v);
                }
            }
        }
        return out;
    }

    /** The visitor row itself: display name and the active marker. */
    private static String[] visitorRow(Connection c, Meta m, String idCol, String nameCol,
                                       String activeCol, int visitorId) throws Exception {
        String sql = "SELECT "
                + (nameCol == null ? "CAST(N'' AS nvarchar(220))" : "TRY_CONVERT(nvarchar(220)," + q(nameCol) + ")")
                + "," + q(activeCol)
                + " FROM " + m.qTable("visitors") + " WHERE TRY_CONVERT(int," + q(idCol) + ")=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, visitorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Exception("ردیف ویزیتور در آتیران پیدا نشد.");
                return new String[]{rs.getString(1), rs.getObject(2) == null ? "" : String.valueOf(rs.getObject(2))};
            }
        }
    }

    private static String q(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    static boolean isActive(Object value) {
        if (value == null) return false;
        String s = String.valueOf(value).trim();
        return "1".equals(s) || "T".equalsIgnoreCase(s) || "TRUE".equalsIgnoreCase(s)
                || "Y".equalsIgnoreCase(s) || "YES".equalsIgnoreCase(s) || "ACTIVE".equalsIgnoreCase(s);
    }
}
