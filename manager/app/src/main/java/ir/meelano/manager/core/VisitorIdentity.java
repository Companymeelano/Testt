package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Resolves the authenticated Atiran account to exactly one active Atiran visitor row. */
public final class VisitorIdentity {
    private VisitorIdentity() { }

    public static final class Identity {
        public final int userId;
        public final int visitorId;
        public final String username;
        public final String displayName;

        Identity(int userId, int visitorId, String username, String displayName) {
            this.userId = userId;
            this.visitorId = visitorId;
            this.username = username == null ? "" : username.trim();
            this.displayName = displayName == null ? "" : displayName.trim();
        }
    }

    /**
     * Never guesses identity from a person's name. Prefer the explicit visitors.UserID link;
     * use an exact username match only when the database has no usable UserID link.
     */
    public static Identity resolve(Connection c, int uid, String username) throws Exception {
        if (uid <= 0 || username == null || username.trim().isEmpty())
            throw new Exception("نشست آتیران شناسهٔ کاربر معتبری ندارد؛ دوباره وارد شوید.");
        Meta m = new Meta(c);
        if (!m.table("visitors")) throw new Exception("جدول ویزیتورهای آتیران پیدا نشد.");
        String id = m.col("visitors", "vis_rdf", "visitor_id", "VisitorID", "rdf");
        String userId = m.col("visitors", "UserID", "user_id", "userid");
        String userName = m.col("visitors", "Username", "user_name", "username");
        String name = m.col("visitors", "vis_name", "name", "Name", "VisitorName");
        String active = m.col("visitors", "active", "Active");
        if (id == null) throw new Exception("شناسهٔ ویزیتور در جدول آتیران پیدا نشد.");
        if (active == null) throw new Exception("وضعیت فعال‌بودن ویزیتور در آتیران قابل بررسی نیست؛ دسترسی حدسی داده نشد.");

        String predicate;
        if (userId != null && userName != null) predicate = "(TRY_CONVERT(int," + q(userId) + ")=? OR LOWER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(160)," + q(userName)
                + "))))=LOWER(LTRIM(RTRIM(?))))";
        else if (userId != null) predicate = "TRY_CONVERT(int," + q(userId) + ")=?";
        else if (userName != null) predicate = "LOWER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(160)," + q(userName)
                + "))))=LOWER(LTRIM(RTRIM(?)))";
        else throw new Exception("پیوند دقیق حساب کاربری به ویزیتور در آتیران موجود نیست؛ دسترسی حدسی داده نشد.");

        String sql = "SELECT TOP (2) TRY_CONVERT(int," + q(id) + ") AS visitor_id"
                + (name == null ? ",CAST(N'' AS nvarchar(220))" : ",TRY_CONVERT(nvarchar(220)," + q(name) + ")")
                + (active == null ? ",CAST(1 AS int)" : "," + q(active))
                + " FROM " + m.qTable("visitors") + " WHERE " + predicate;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null && userName != null) { ps.setInt(1, uid); ps.setString(2, username.trim()); }
            else if (userId != null) ps.setInt(1, uid);
            else ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Exception("برای این حساب آتیران ردیف ویزیتور پیدا نشد.");
                int visitorId = rs.getInt(1);
                String displayName = rs.getString(2);
                Object activeValue = rs.getObject(3);
                if (!isActive(activeValue)) throw new Exception("ردیف ویزیتور در آتیران غیرفعال است.");
                if (rs.next()) throw new Exception("برای این حساب چند ردیف ویزیتور پیدا شد؛ مدیر آتیران باید پیوند را یکتا کند.");
                if (visitorId <= 0) throw new Exception("شناسهٔ ویزیتور آتیران معتبر نیست.");
                return new Identity(uid, visitorId, username, displayName);
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
