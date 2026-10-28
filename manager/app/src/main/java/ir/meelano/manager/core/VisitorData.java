package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared Atiran queries for the field-sales workspace: owner scoping, the active marker,
 * the visitor's default price group and the read timeouts. The phone and the server-side
 * rules use this one implementation, so the two can never drift apart.
 */
public final class VisitorData {
    private VisitorData() { }

    /** How long a read may wait for a lock before the app answers with a clear message. */
    private static final int LOCK_TIMEOUT_MS = 12000;
    /** How long a single statement may run before the app gives up on it. */
    private static final int QUERY_TIMEOUT_SEC = 30;

    /**
     * Atiran's own active marker: a row counts only on a positive value
     * ('T', 'TRUE', 'Y', 'YES', '1' or the integer 1). Same rule as the rest of the app.
     */
    public static String activePredicate(String alias, String column) {
        String f = field(alias, column);
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + f + "))))";
        return "(" + n + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + f + ")=1)";
    }

    /** The visitor ids an account owns, ready for a WHERE clause. */
    public static final class Owner {
        public final String predicate;
        public final boolean numeric;
        public final int[] ids;

        Owner(String predicate, boolean numeric, int[] ids) {
            this.predicate = predicate;
            this.numeric = numeric;
            this.ids = ids;
        }

        /** Binds the owner placeholders, starting at the given parameter index. */
        public void bind(PreparedStatement ps, int first) throws SQLException {
            for (int i = 0; i < ids.length; i++) {
                if (numeric) ps.setInt(first + i, ids[i]);
                else ps.setString(first + i, String.valueOf(ids[i]));
            }
        }
    }

    /**
     * Owner scope for a visitor column. When Atiran stores the id as a number the predicate
     * compares the column directly, so the database can use an index instead of scanning
     * the whole table; a text column is compared as text, the way Atiran stores it.
     */
    public static Owner owner(String alias, String column, String sqlType, int[] ids) {
        String f = field(alias, column);
        boolean numeric = isNumeric(sqlType);
        StringBuilder b = new StringBuilder();
        b.append(numeric ? f : "TRY_CONVERT(nvarchar(100)," + f + ")").append(" IN (");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) b.append(',');
            b.append('?');
        }
        b.append(')');
        return new Owner(b.toString(), numeric, ids);
    }

    /** The visitor ids of a signed-in account. */
    public static int[] ids(VisitorIdentity.Identity identity) {
        if (identity == null || identity.visitorIds == null || identity.visitorIds.length == 0)
            return new int[]{identity == null ? 0 : identity.visitorId};
        return identity.visitorIds;
    }

    /**
     * The price group Atiran stores for the visitor: every DeviceSettings row of that
     * visitor must carry the same group. Several rows are allowed (one per server or line),
     * but two different groups are never guessed between.
     */
    public static int defaultPriceGroup(Connection c, int[] visitorIds) throws Exception {
        Meta m = new Meta(c);
        if (!m.table("DeviceSettings"))
            throw new Exception("تنظیم قیمت ویزیتور (DeviceSettings) در آتیران پیدا نشد.");
        String v = m.col("DeviceSettings", "VisitorId", "VisitorID", "vis_rdf");
        String g = m.col("DeviceSettings", "DefaultPriceGrp", "DefaultPriceGroup");
        if (v == null || g == null)
            throw new Exception("ستون‌های VisitorId و DefaultPriceGrp در DeviceSettings پیدا نشد.");
        Owner owner = owner(null, v, m.columnType("DeviceSettings", v), visitorIds);
        String sql = "SELECT TRY_CONVERT(int," + q(g) + ") FROM " + m.qTable("DeviceSettings")
                + " WHERE " + owner.predicate;
        List<Integer> groups = new ArrayList<>();
        try (PreparedStatement ps = read(c, sql)) {
            owner.bind(ps, 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object v2 = rs.getObject(1);
                    if (v2 == null) groups.add(null);
                    else groups.add(Integer.valueOf(((Number) v2).intValue()));
                }
            }
        }
        int group = VisitorPricePolicy.resolveUniqueGroup(groups);
        if (group == 0)
            throw new Exception("DefaultPriceGrp باید یک مقدار یکتا از ۱ تا ۵ باشد.");
        return group;
    }

    /**
     * The Atiran flags that switch a product on for field sales. Capillary (مویرگ) sales
     * come first; the online flag is only used when the shop keeps that one.
     */
    public static String[] fieldSalesColumns(Meta m) {
        List<String> cols = new ArrayList<>();
        String capillary = m.col("inventory", "ActiveCapillarySales", "active_capillary_sales",
                "CapillarySales", "capillary_sales");
        if (capillary != null) cols.add(capillary);
        String online = m.col("inventory", "ActiveOnlineSales", "active_online_sales",
                "OnlineSales", "online_sales");
        if (online != null) cols.add(online);
        return cols.toArray(new String[0]);
    }

    /**
     * Field-sales predicate: the product must be switched on in at least one of the Atiran
     * sales flags. With no flag column at all the shop keeps no such switch, so nothing is
     * filtered out.
     */
    public static String fieldSalesPredicate(String alias, String[] columns) {
        if (columns == null || columns.length == 0) return "";
        StringBuilder b = new StringBuilder();
        for (String col : columns) {
            if (b.length() > 0) b.append(" OR ");
            b.append(activePredicate(alias, col));
        }
        return "(" + b + ")";
    }

    /** Prepares the connection for a bounded read (a busy Atiran server must not hang). */
    public static void beginRead(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(10);
            st.execute("SET LOCK_TIMEOUT " + LOCK_TIMEOUT_MS);
        }
    }

    /** A read statement with a query timeout, so a stuck server never drops the socket. */
    public static PreparedStatement read(Connection c, String sql) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setQueryTimeout(QUERY_TIMEOUT_SEC);
        return ps;
    }

    private static boolean isNumeric(String sqlType) {
        if (sqlType == null) return false;
        String t = sqlType.toLowerCase(Locale.US);
        return "int".equals(t) || "bigint".equals(t) || "smallint".equals(t) || "tinyint".equals(t)
                || "decimal".equals(t) || "numeric".equals(t) || "float".equals(t) || "real".equals(t)
                || "money".equals(t) || "smallmoney".equals(t);
    }

    /**
     * Equality between two id columns. Two numeric columns are compared directly, so the
     * database can seek an index; anything else is compared as text, the way Atiran
     * stores it in older installations.
     */
    public static String idEquals(String leftField, String leftType, String rightField, String rightType) {
        if (isNumeric(leftType) && isNumeric(rightType)) return leftField + "=" + rightField;
        return "TRY_CONVERT(nvarchar(100)," + leftField + ")=TRY_CONVERT(nvarchar(100)," + rightField + ")";
    }

    /** The expression to read a name column as text, without a conversion when it is text already. */
    public static String textExpr(String alias, String column, String sqlType) {
        String f = field(alias, column);
        if (sqlType != null) {
            String t = sqlType.toLowerCase(Locale.US);
            if (t.startsWith("nchar") || t.startsWith("nvarchar") || t.startsWith("char")
                    || t.startsWith("varchar") || t.startsWith("text") || t.startsWith("ntext")) return f;
        }
        return "TRY_CONVERT(nvarchar(500)," + f + ")";
    }

    /** The expression to sort a name column by. */
    public static String orderText(String alias, String column, String sqlType) {
        return textExpr(alias, column, sqlType);
    }

    private static String field(String alias, String column) {
        String p = (alias == null || alias.trim().isEmpty()) ? "" : alias + ".";
        return p + "[" + (column == null ? "" : column.replace("]", "]]")) + "]";
    }

    private static String q(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }
}
