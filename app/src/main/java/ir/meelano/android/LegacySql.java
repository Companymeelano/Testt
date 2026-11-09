package ir.meelano.android;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL Server 2008-R2 compatibility layer.
 *
 * <p>Atiran installations still run on SQL Server 2008 R2 in the field. TRY_CONVERT and
 * TRY_CAST were introduced in SQL Server 2012, so on those servers every report fails while
 * parsing - and because the parser resolves the <em>first argument</em> of the unknown
 * function as a call, the server reports «'nvarchar' is not a recognized built-in function
 * name» (or 'decimal'/'int' depending on the target type) instead of naming TRY_CONVERT.
 *
 * <p>Instead of hand-editing hundreds of query fragments, the connection is wrapped here and
 * every statement is rewritten on the way out: TRY_CONVERT/TRY_CAST become CONVERT/CAST
 * equivalents guarded by ISNUMERIC/ISDATE, which keep the same «bad value → NULL» behaviour
 * that the screens rely on. On 2012+ servers nothing changes: the probe detects the capability
 * once per server and the rewrite becomes a pass-through.
 */
public final class LegacySql {

    private LegacySql() { }

    private static final Map<String, Boolean> SERVER_CACHE = new ConcurrentHashMap<>();

    /** Probe string: parses (and runs) only where TRY_CONVERT exists. */
    private static final String PROBE = "SELECT TRY_CONVERT(int,N'1')";

    /**
     * True when the server cannot parse TRY_CONVERT/TRY_CAST (SQL Server 2012 or older).
     * The answer is cached per cache key (host:port), so a pool of connections probes once.
     */
    public static boolean probe(Connection c, String cacheKey) {
        if (cacheKey != null) {
            Boolean known = SERVER_CACHE.get(cacheKey);
            if (known != null) return known;
        }
        boolean legacy;
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(5);
            st.execute(PROBE);
            legacy = false;
        } catch (SQLException e) {
            legacy = isParseError(e);
        } catch (Exception e) {
            legacy = false; // network/permission trouble: keep the modern dialect
        }
        if (cacheKey != null) SERVER_CACHE.put(cacheKey, legacy);
        return legacy;
    }

    /** Msg 195 («not a recognized built-in function name») / Msg 102 (syntax). */
    private static boolean isParseError(SQLException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase(Locale.US);
        return m.contains("is not a recognized built-in function name")
                || m.contains("incorrect syntax")
                || m.contains("must be declared")
                || e.getErrorCode() == 195 || e.getErrorCode() == 102;
    }

    /** Wraps a connection so every statement it prepares is dialect-rewritten when needed. */
    public static Connection wrap(final Connection real, final boolean legacy) {
        if (!legacy) return real;
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                LegacySql.class.getClassLoader(), new Class[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (("prepareStatement".equals(name) || "prepareCall".equals(name))
                            && args != null && args.length > 0 && args[0] instanceof String) {
                        Object[] copy = args.clone();
                        copy[0] = rewrite((String) copy[0]);
                        return wrapStatement(invoke(method, real, copy));
                    }
                    if ("createStatement".equals(name)) {
                        return wrapStatement(invoke(method, real, args));
                    }
                    return invoke(method, real, args);
                });
    }

    /** Rewrites the SQL text argument of execute / executeQuery / executeUpdate / addBatch. */
    private static Object wrapStatement(final Object real) {
        if (real == null) return null;
        return java.lang.reflect.Proxy.newProxyInstance(LegacySql.class.getClassLoader(),
                real.getClass().getInterfaces(), (proxy, method, args) -> {
                    String name = method.getName();
                    if (args != null && args.length > 0 && args[0] instanceof String
                            && ("execute".equals(name) || "executeQuery".equals(name)
                            || "executeUpdate".equals(name) || "executeLargeUpdate".equals(name)
                            || "addBatch".equals(name))) {
                        Object[] copy = args.clone();
                        copy[0] = rewrite((String) copy[0]);
                        return invoke(method, real, copy);
                    }
                    return invoke(method, real, args);
                });
    }

    /** Delegate without wrapping checked JDBC exceptions in InvocationTargetException. */
    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    // ============================== rewrite ==============================

    /** Rewrites TRY_CONVERT / TRY_CAST into 2008-compatible guarded CONVERT / CAST. */
    public static String rewrite(String sql) {
        if (sql == null || sql.isEmpty()) return sql;
        String lower = sql.toLowerCase(Locale.US);
        int hit = indexOfFn(lower, 0);
        if (hit < 0) return rewritePagination(sql);
        StringBuilder out = new StringBuilder(sql.length() + 64);
        int cursor = 0;
        while (hit >= 0) {
            out.append(sql, cursor, hit);
            boolean convert = lower.startsWith("try_convert(", hit);
            int open = hit + (convert ? "TRY_CONVERT".length() : "TRY_CAST".length());
            int close = matchParen(sql, open);
            if (close < 0) { // unbalanced: give up gracefully and keep the original text
                out.append(sql.substring(hit));
                return out.toString();
            }
            String inner = sql.substring(open + 1, close);
            out.append(convert ? rewriteConvert(inner) : rewriteCast(inner));
            cursor = close + 1;
            hit = indexOfFn(lower, cursor);
        }
        out.append(sql, cursor, sql.length());
        return rewritePagination(out.toString());
    }

    /**
     * SQL Server 2008 R2 has no OFFSET/FETCH either. The paginated tail becomes a
     * ROW_NUMBER() window injected into the body's own scope (so «h.[date]»-style order
     * expressions still resolve), filtered by «_rn - offset BETWEEN 1 AND fetch» which keeps
     * the caller's bind order (offset, fetch) untouched.
     */
    static String rewritePagination(String sql) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?is)\\s+OFFSET\\s+(\\?|\\d+)\\s+ROWS\\s+FETCH\\s+NEXT\\s+(\\?|\\d+)\\s+ROWS\\s+ONLY\\s*$")
                .matcher(sql);
        if (!m.find()) return sql;
        int tail = m.start();
        String off = m.group(1);
        String fetch = m.group(2);
        int ob = lastTopLevelOrderBy(sql, tail);
        if (ob < 0) return sql;
        String order = sql.substring(ob + " ORDER BY ".length(), tail).trim();
        String body = sql.substring(0, ob).trim();
        int insert = selectListInsert(body);
        if (insert < 0) return sql;
        String paged = "ROW_NUMBER() OVER (ORDER BY " + order + ") AS _rn, ";
        body = body.substring(0, insert) + paged + body.substring(insert);
        return "SELECT _q.* FROM (" + body + ") _q WHERE _q._rn - " + off
                + " BETWEEN 1 AND " + fetch + " ORDER BY _q._rn";
    }

    /** Index of the last depth-0 « ORDER BY » before {@code limit}, or -1. */
    private static int lastTopLevelOrderBy(String sql, int limit) {
        String lower = sql.toLowerCase(Locale.US);
        int depth = 0, found = -1;
        for (int i = 0; i < limit; i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') { int e = skipQuoted(sql, i); if (e < 0) return -1; i = e; continue; }
            if (ch == '[') { int e = sql.indexOf(']', i); if (e < 0) return -1; i = e; continue; }
            if (ch == '(') { depth++; continue; }
            if (ch == ')') { depth--; continue; }
            if (depth == 0 && lower.startsWith(" order by ", i)) found = i;
        }
        return found;
    }

    /** Insert point for a new select-list item: right after the outer SELECT / SELECT DISTINCT. */
    private static int selectListInsert(String body) {
        String lower = body.toLowerCase(Locale.US);
        int at = firstTopLevelSelect(body);
        if (at < 0) return -1;
        int i = at + "SELECT".length();
        while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t' || body.charAt(i) == '\n')) i++;
        if (lower.startsWith("distinct", i)) {
            i += "DISTINCT".length();
            while (i < body.length() && (body.charAt(i) == ' ' || body.charAt(i) == '\t' || body.charAt(i) == '\n')) i++;
        }
        if (lower.startsWith("top", i)) return -1; // TOP + window would double-limit
        return i;
    }

    /** Index of the first depth-0 SELECT (the outer one; CTE/union-inner selects nest). */
    private static int firstTopLevelSelect(String s) {
        String lower = s.toLowerCase(Locale.US);
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') { int e = skipQuoted(s, i); if (e < 0) return -1; i = e; continue; }
            if (ch == '[') { int e = s.indexOf(']', i); if (e < 0) return -1; i = e; continue; }
            if (ch == '(') { depth++; continue; }
            if (ch == ')') { depth--; continue; }
            if (depth == 0 && lower.startsWith("select", i)) return i;
        }
        return -1;
    }

    private static int indexOfFn(String lower, int from) {
        int a = lower.indexOf("try_convert(", from);
        int b = lower.indexOf("try_cast(", from);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** Index of the ')' matching the '(' at {@code open}, skipping literals and brackets. */
    static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') { i = skipQuoted(s, i); if (i < 0) return -1; continue; }
            if (ch == '[') { int e = s.indexOf(']', i); if (e < 0) return -1; i = e; continue; }
            if (ch == '(') depth++;
            else if (ch == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    /** Index of the closing quote of the literal starting at {@code i} ('' is an escape). */
    private static int skipQuoted(String s, int i) {
        for (int j = i + 1; j < s.length(); j++) {
            if (s.charAt(j) == '\'') {
                if (j + 1 < s.length() && s.charAt(j + 1) == '\'') { j++; continue; }
                return j;
            }
        }
        return -1;
    }

    private static String rewriteConvert(String inner) {
        String[] parts = splitTop(inner, ',');
        if (parts.length < 2) return "CONVERT(" + inner + ")";
        String type = parts[0].trim();
        String expr = rewrite(parts[1].trim());
        String style = parts.length > 2 ? "," + parts[2].trim() : "";
        return guarded(type, expr, style);
    }

    private static String rewriteCast(String inner) {
        int as = indexOfTopAs(inner);
        if (as < 0) return "CAST(" + inner + ")";
        String expr = rewrite(inner.substring(0, as).trim());
        String type = inner.substring(as + 3).trim();
        return guarded(type, expr, "");
    }

    /** Position of the top-level « AS » keyword inside a TRY_CAST argument list. */
    static int indexOfTopAs(String s) {
        int depth = 0;
        for (int i = 0; i < s.length() - 3; i++) {
            char ch = s.charAt(i);
            if (ch == '\'') { int e = skipQuoted(s, i); if (e < 0) return -1; i = e; continue; }
            if (ch == '[') { int e = s.indexOf(']', i); if (e < 0) return -1; i = e; continue; }
            if (ch == '(') { depth++; continue; }
            if (ch == ')') { depth--; continue; }
            if (depth == 0 && (ch == ' ' || ch == '\t' || ch == '\n')
                    && s.regionMatches(true, i + 1, "AS", 0, 2)
                    && (i + 3 >= s.length() || s.charAt(i + 3) == ' ' || s.charAt(i + 3) == '\t'))
                return i;
        }
        return -1;
    }

    /** Splits on commas that are not nested in parentheses or string literals. */
    static String[] splitTop(String s, char sep) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') { int e = skipQuoted(s, i); if (e < 0) break; i = e; continue; }
            if (ch == '[') { int e = s.indexOf(']', i); if (e < 0) break; i = e; continue; }
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == sep && depth == 0) { parts.add(s.substring(start, i)); start = i + 1; }
        }
        parts.add(s.substring(start));
        return parts.toArray(new String[0]);
    }

    /**
     * The 2008-R2 equivalent of one TRY_CONVERT/TRY_CAST, keeping «unparseable → NULL»:
     * text targets need no guard; dates use ISDATE; integers go through decimal so values
     * like «1.5» round instead of raising; decimals use ISNUMERIC directly.
     */
    private static String guarded(String type, String expr, String style) {
        String t = type.toLowerCase(Locale.US).trim();
        if (t.startsWith("nvarchar") || t.startsWith("varchar") || t.startsWith("nchar")
                || t.startsWith("char") || t.startsWith("ntext") || t.startsWith("text"))
            return "CONVERT(" + type + "," + expr + style + ")";
        if (t.startsWith("datetime") || t.startsWith("date") || t.startsWith("smalldatetime")
                || t.startsWith("time"))
            return "(CASE WHEN ISDATE(" + expr + ")=1 THEN CONVERT(" + type + "," + expr + style
                    + ") ELSE NULL END)";
        if (t.equals("int") || t.equals("bigint") || t.equals("smallint") || t.equals("tinyint"))
            return "(CASE WHEN ISNUMERIC(" + expr + ")=1 THEN CONVERT(" + type
                    + ",CONVERT(decimal(38,4)," + expr + ")) ELSE NULL END)";
        if (t.startsWith("decimal") || t.startsWith("numeric") || t.startsWith("float")
                || t.startsWith("real") || t.startsWith("money") || t.startsWith("smallmoney"))
            return "(CASE WHEN ISNUMERIC(" + expr + ")=1 THEN CONVERT(" + type + "," + expr
                    + ") ELSE NULL END)";
        return "CONVERT(" + type + "," + expr + style + ")";
    }
}
