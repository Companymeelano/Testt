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
    /**
     * Normalise Atiran's date text to a sortable «YYYY/MM/DD».
     *
     * Most rows are stored zero-padded, but a real minority is not («1403/5/12»). Range
     * filters compare these as text, and «1403/5/12» sorts after «1403/12/29», so those
     * rows fell outside every range and quietly vanished from the reports.
     */
    public static String dateKey(String dateExpr) {
        String d = dateExpr;
        return "(CASE"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9]/[0-9]'"
                + " THEN LEFT(" + d + ",5)+N'0'+SUBSTRING(" + d + ",6,1)+N'/0'+SUBSTRING(" + d + ",8,1)"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9]/[0-9][0-9]'"
                + " THEN LEFT(" + d + ",5)+N'0'+SUBSTRING(" + d + ",6,4)"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9][0-9]/[0-9]'"
                + " THEN LEFT(" + d + ",8)+N'0'+SUBSTRING(" + d + ",9,1)"
                + " ELSE " + d + " END)";
    }

    public static String dateCond(String dateExpr, String from, String to, List<Object> binds) {
        boolean f = from != null && !from.trim().isEmpty();
        boolean t = to != null && !to.trim().isEmpty();
        if (!f && !t) return "";
        String dk = dateKey(dateExpr);
        if (f && t) {
            if (from.compareTo(to) > 0) { String x = from; from = to; to = x; }
            binds.add(from.trim()); binds.add(to.trim());
            return "(" + dk + ">=? AND " + dk + "<=?)";
        }
        if (f) { binds.add(from.trim()); return "(" + dk + ">=?)"; }
        binds.add(to.trim());
        return "(" + dk + "<=?)";
    }

    /** AND active='t' (Atiran's active flag), or "" when there is no such column. */
    public static String activeAnd(Set<String> cols, String alias) {
        String c = pick(cols, "active", "Active");
        if (c == null) return "";
        String p = alias == null || alias.trim().isEmpty() ? "" : alias + ".";
        String field = p + q(c);
        // Atiran's own procedures are the reference here, and they are strict:
        //   dbo.dar      -> "WHERE p=0 AND Active = 1"   (integer 1)
        //   dbo.sailfact -> "WHERE active = 't'"          (text 't')
        // So a row counts ONLY on a positive active marker. An earlier change relaxed this
        // into a deny-list, which let NULL / 0 / unexpected values through and would have
        // counted deleted vouchers as real money. Restored deliberately.
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + "))))";
        return " AND (" + n + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
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

    /**
     * Spacing-insensitive, Persian/Arabic-tolerant key for filtering lists in memory.
     * «علي رضايي» and «علی رضایی» collapse to the same key, Persian digits match Latin
     * ones, and every non-alphanumeric character is dropped so punctuation never blocks a
     * match. Used by the searchable pickers.
     */
    /**
     * Spacing-insensitive, Persian/Arabic-tolerant key for filtering lists in memory.
     * Collapses the Arabic and Persian spellings of the same letter, folds Persian and
     * Arabic digits onto Latin ones and drops everything that is not alphanumeric, so
     * punctuation can never block a match. Used by the searchable pickers.
     */
    public static String searchKey(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == 'ي' || ch == 'ی') b.append('ی');
            else if (ch == 'ك' || ch == 'ک') b.append('ک');
            else if (ch == 'ة') b.append('ه');
            else if (ch == 'ؤ') b.append('و');
            else if (ch == 'إ' || ch == 'أ' || ch == 'آ') b.append('ا');
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (Character.isLetterOrDigit(ch)) b.append(Character.toLowerCase(ch));
        }
        return b.toString();
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
