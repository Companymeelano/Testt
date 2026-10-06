package ir.meelano.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guards the SQL shape that reaches the customer's SQL Server 2014.
 *
 * Three dashboard releases failed in the field because of the factor "dedupe" clause, each in a
 * different way: {@code SELECT DISTINCT x.*} (text/image columns), {@code ROW_NUMBER() OVER}
 * ("Incorrect syntax near 'mx'"), a derived-table alias ("Incorrect syntax near 'h'"), and finally a
 * correlated latest-revision subquery that parsed but ran once per row and starved the dashboard for
 * minutes. These tests pin the shape that survived — a plain table reference — and prove that the
 * per-statement timeout that replaced the missing ceiling is really applied.
 */
public class ManagerAnalyticsSqlTest {

    private static final Set<String> SAIL = new HashSet<>(Arrays.asList(
            "rdf__", "shfacfo", "date", "all", "shmo", "MabDaryaftFactor", "active", "deleted"));

    private static String source(String where) {
        return ManagerAnalytics.dedupeFactorSource("sailfact", SAIL, "shfacfo", "h", where);
    }

    @Test
    public void factorSourceIsAPlainTableWithTheAliasRewritten() {
        String sql = source("WHERE x.[date]=N'1403/05/01' AND (x.[active]=N'T')");
        assertEquals("dbo.[sailfact] h WHERE h.[date]=N'1403/05/01' AND (h.[active]=N'T')", sql);
    }

    @Test
    public void factorSourceEmitsNoWhereClauseWhenThereIsNoFilter() {
        assertEquals("dbo.[sailfact] h", source(""));
        assertEquals("dbo.[sailfact] h", source(null));
        assertEquals("dbo.[sailfact] sf", ManagerAnalytics.dedupeFactorSource("sailfact", SAIL, "shfacfo", "sf", "  "));
    }

    /** The four constructs the customer's server rejected or could not execute in time must stay out. */
    @Test
    public void factorSourceAvoidsEveryConstructThatFailedInTheField() {
        for (String where : new String[]{"", "WHERE x.[date] IS NOT NULL AND (x.[deleted] IS NULL)"}) {
            String upper = source(where).toUpperCase(Locale.US);
            assertFalse("DISTINCT is rejected over text/image columns", upper.contains("DISTINCT"));
            assertFalse("ROW_NUMBER/OVER is mis-parsed by this server", upper.contains("ROW_NUMBER"));
            assertFalse("window functions are mis-parsed by this server", upper.contains("OVER("));
            assertFalse("derived-table aliases are mis-parsed by this server", upper.contains("(SELECT"));
            assertFalse("correlated revision subquery runs once per row", upper.contains("SELECT MAX("));
            assertFalse("no revision guard should remain", upper.contains("ISNULL(Y."));
        }
    }

    @Test
    public void factorSourceKeepsParenthesesBalanced() {
        String sql = source("WHERE (x.[date]>=N'1403-01-01' OR LEFT(x.[date],4)=N'1403') AND (x.[active]=N'T')");
        int depth = 0;
        for (char ch : sql.toCharArray()) {
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            assertTrue("unbalanced parentheses in: " + sql, depth >= 0);
        }
        assertEquals("unbalanced parentheses in: " + sql, 0, depth);
    }

    /**
     * The dedupe clause was only ever a defence against double-counting cancelled documents; that job
     * belongs to these cheap, sargable predicates, so losing the clause must not lose the protection.
     */
    @Test
    public void cancelledAndDeletedDocumentsAreStillFilteredOut() {
        String soft = ManagerAnalytics.softDeleteCondition(SAIL, "h");
        assertTrue("deleted flag must be honoured", soft.contains("h.[deleted]"));
        String active = ManagerAnalytics.activeAnd(SAIL, "h");
        assertTrue("active flag must be honoured", active.contains("h.[active]"));
    }

    /**
     * The root cause of the on-device "Incorrect syntax near '(' / 'GROUP' / 'g'" errors: the status
     * branch of {@code softDeleteCondition} emitted an unbalanced parenthesis whenever a table carried a
     * status/state column, silently breaking the whole WHERE clause. It must stay balanced.
     */
    @Test
    public void statusFilterIsParenBalanced() {
        for (Set<String> cols : Arrays.asList(
                new HashSet<>(Arrays.asList("status")),
                new HashSet<>(Arrays.asList("state", "deleted")),
                new HashSet<>(Arrays.asList("Status", "active", "cancel")))) {
            String cond = ManagerAnalytics.softDeleteCondition(cols, "h");
            int depth = 0;
            for (char ch : cond.toCharArray()) {
                if (ch == '(') depth++;
                else if (ch == ')') depth--;
                assertTrue("unbalanced: " + cond, depth >= 0);
            }
            assertEquals("softDeleteCondition must be paren-balanced: " + cond, 0, depth);
        }
    }

    // ---------------------------------------------------------------- statement timeout

    private static final List<String> STATEMENT_CALLS = new ArrayList<>();

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == void.class) return null;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return Boolean.FALSE;
    }

    private static Object proxy(Class<?> iface, InvocationHandler h) {
        return Proxy.newProxyInstance(ManagerAnalyticsSqlTest.class.getClassLoader(), new Class<?>[]{iface}, h);
    }

    private static InvocationHandler recorder() {
        return new InvocationHandler() {
            @Override public Object invoke(Object p, Method m, Object[] args) {
                String name = m.getName();
                if ("setQueryTimeout".equals(name)) STATEMENT_CALLS.add("setQueryTimeout=" + args[0]);
                if ("prepareStatement".equals(name)) return proxy(PreparedStatement.class, this);
                if ("executeQuery".equals(name)) return proxy(ResultSet.class, this);
                if ("next".equals(name)) return Boolean.FALSE;
                if ("isClosed".equals(name)) return Boolean.FALSE;
                if ("hashCode".equals(name)) return System.identityHashCode(p);
                if ("equals".equals(name)) return p == (args == null ? null : args[0]);
                if ("toString".equals(name)) return "sqlProxy";
                return defaultValue(m.getReturnType());
            }
        };
    }

    /** Every metadata/business statement must carry a server-side timeout; none did before. */
    @Test
    public void everyStatementCarriesAServerSideTimeout() throws Exception {
        STATEMENT_CALLS.clear();
        Connection c = (Connection) proxy(Connection.class, recorder());
        Set<String> cols = ManagerAnalytics.columns(c, "sailfact");
        assertNotNull(cols);
        assertFalse("no statement was prepared", STATEMENT_CALLS.isEmpty());
        for (String call : STATEMENT_CALLS) {
            assertTrue("statement was not given a timeout", call.startsWith("setQueryTimeout="));
            int seconds = Integer.parseInt(call.substring("setQueryTimeout=".length()));
            assertTrue("timeout must be short enough to release the page, was " + seconds, seconds > 0 && seconds <= 10);
        }
    }
}
