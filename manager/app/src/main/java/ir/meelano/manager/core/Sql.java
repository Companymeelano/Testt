package ir.meelano.manager.core;

import java.util.List;
import java.util.Set;

/**
 * Safe SQL fragments for Atiran (SQL Server 2014 compatible).
 * Every helper is null-safe: a missing column degrades to a neutral expression
 * instead of producing invalid SQL.
 */
public final class Sql {
    private Sql() { }

    /** [col] */
    public static String q(String col) {
        return col == null ? "NULL" : "[" + col.replace("]", "") + "]";
    }

    private static String ref(String alias, String col) {
        if (col == null || col.trim().isEmpty()) return "NULL";
        return (alias == null || alias.trim().isEmpty() ? "" : alias + ".") + q(col);
    }

    /** Money-safe number: COALESCE(TRY_CONVERT(decimal(19,2), …), 0). */
    public static String num(String alias, String col) {
        if (col == null || col.trim().isEmpty()) return "CAST(0 AS decimal(19,2))";
        return "COALESCE(TRY_CONVERT(decimal(19,2)," + ref(alias, col) + "),0)";
    }

    /** Nullable number (for SUM without turning NULL into 0 twice). */
    public static String numNull(String alias, String col) {
        if (col == null || col.trim().isEmpty()) return "CAST(NULL AS decimal(19,2))";
        return "TRY_CONVERT(decimal(19,2)," + ref(alias, col) + ")";
    }

    /** Text of a column. */
    public static String txt(String alias, String col, int len) {
        if (col == null || col.trim().isEmpty()) return "CAST(NULL AS nvarchar(" + len + "))";
        return "TRY_CONVERT(nvarchar(" + len + ")," + ref(alias, col) + ")";
    }

    /**
     * First 10 chars of a date column. Atiran stores Jalali dates as char(10)
     * «YYYY/MM/DD» (zero-padded), so plain string comparison is chronological.
     */
    public static String date10(String alias, String col) {
        if (col == null || col.trim().isEmpty()) return "CAST(NULL AS nvarchar(10))";
        return "LEFT(TRY_CONVERT(nvarchar(30)," + ref(alias, col) + "),10)";
    }

    /** «YYYY/MM» of a date column. */
    public static String month7(String alias, String col) {
        if (col == null || col.trim().isEmpty()) return "CAST(NULL AS nvarchar(7))";
        return "LEFT(TRY_CONVERT(nvarchar(30)," + ref(alias, col) + "),7)";
    }

    /**
     * Date-range condition on an Atiran date expression. Appends bind values.
     * Empty when the filter has no range (means: all dates).
     */
    public static String dateCond(String dateExpr, String from, String to, List<Object> binds) {
        boolean f = from != null && !from.trim().isEmpty();
        boolean t = to != null && !to.trim().isEmpty();
        if (!f && !t) return "";
        if (f && t) {
            if (from.compareTo(to) > 0) { String x = from; from = to; to = x; }
            binds.add(from.trim()); binds.add(to.trim());
            return "(" + dateExpr + ">=? AND " + dateExpr + "<=?)";
        }
        if (f) { binds.add(from.trim()); return "(" + dateExpr + ">=?)"; }
        binds.add(to.trim());
        return "(" + dateExpr + "<=?)";
    }

    /** AND active='t' (Atiran's active flag), or "" when there is no such column. */
    public static String activeAnd(Set<String> cols, String alias) {
        String c = pick(cols, "active", "Active");
        if (c == null) return "";
        String p = alias == null || alias.trim().isEmpty() ? "" : alias + ".";
        String field = p + q(c);
        return " AND (UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + ")))) IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
    }

    /** AND not-deleted / not-cancelled guards, or "" when no such columns exist. */
    public static String softAnd(Set<String> cols, String alias) {
        StringBuilder b = new StringBuilder();
        String p = alias == null || alias.trim().isEmpty() ? "" : alias + ".";
        String del = pickFlex(cols, "deleted", "is_deleted", "isdeleted");
        if (del != null) b.append(" AND ").append(falseLike(p + q(del)));
        String cancel = pickFlex(cols, "cancel", "canceled", "cancelled", "is_cancel", "void");
        if (cancel != null) b.append(" AND ").append(falseLike(p + q(cancel)));
        return b.toString();
    }

    private static String falseLike(String field) {
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(40)," + field + "))))";
        return "(" + n + " IS NULL OR " + n + " IN (N'',N'0',N'F',N'FALSE',N'N',N'NO') OR TRY_CONVERT(int," + field + ")=0)";
    }

    /**
     * De-duplicated factor source: (SELECT DISTINCT x.* FROM dbo.[table] x WHERE …) alias.
     * Inner conditions must reference the x. alias.
     */
    public static String dedupe(String table, String numberCol, String alias, String innerWhere) {
        String a = alias == null || alias.trim().isEmpty() ? "h" : alias.trim();
        String where = innerWhere == null ? "" : innerWhere.trim();
        if (numberCol == null || numberCol.trim().isEmpty())
            return "dbo.[" + table + "] " + a + (where.isEmpty() ? "" : " " + where.replace("x.", a + "."));
        return "(SELECT DISTINCT x.* FROM dbo.[" + table + "] x " + where + ") " + a;
    }

    // ---------------- column picking (case-insensitive) ----------------
    public static String pick(Set<String> cols, String... candidates) {
        if (cols == null || candidates == null) return null;
        for (String cand : candidates) {
            if (cand == null) continue;
            for (String c : cols) if (c != null && c.equalsIgnoreCase(cand)) return c;
        }
        return null;
    }

    public static String pickFlex(Set<String> cols, String... candidates) {
        String exact = pick(cols, candidates);
        if (exact != null) return exact;
        if (cols == null || candidates == null) return null;
        for (String cand : candidates) {
            String n = norm(cand);
            if (n.isEmpty()) continue;
            for (String c : cols) if (c != null && norm(c).equals(n)) return c;
        }
        return null;
    }

    private static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toLowerCase(java.util.Locale.US).toCharArray())
            if (Character.isLetterOrDigit(ch)) b.append(ch);
        return b.toString();
    }

    public static String join(List<String> parts, String sep) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(p);
        }
        return b.toString();
    }

    /** N'…' literal with quotes escaped (only for trusted internal values, never user input). */
    public static String lit(String s) {
        return "N'" + (s == null ? "" : s.replace("'", "''")) + "'";
    }
}
