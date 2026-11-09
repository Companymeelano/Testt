package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the SQL Server 2008 R2 dialect rewrite: field servers reject TRY_CONVERT/TRY_CAST at
 * parse time («'nvarchar' is not a recognized built-in function name»), so every statement
 * must leave the connection without those tokens while keeping «bad value → NULL» semantics.
 */
public class LegacySqlTest {

    @Test
    public void textTargetBecomesPlainConvert() {
        assertEquals("SELECT CONVERT(nvarchar(60),h.[ghno]) AS k",
                LegacySql.rewrite("SELECT TRY_CONVERT(nvarchar(60),h.[ghno]) AS k"));
    }

    @Test
    public void textTargetKeepsStyle() {
        assertEquals("CONVERT(nvarchar(30),x.[d],121)",
                LegacySql.rewrite("TRY_CONVERT(nvarchar(30),x.[d],121)"));
    }

    @Test
    public void integerTargetIsGuardedAndRoundsThroughDecimal() {
        assertEquals("(CASE WHEN ISNUMERIC(h.[p])=1 THEN CONVERT(int,CONVERT(decimal(38,4),h.[p])) ELSE NULL END)",
                LegacySql.rewrite("TRY_CONVERT(int,h.[p])"));
    }

    @Test
    public void decimalTargetIsGuardedWithIsNumeric() {
        assertEquals("(CASE WHEN ISNUMERIC(d.[mab])=1 THEN CONVERT(decimal(19,2),d.[mab]) ELSE NULL END)",
                LegacySql.rewrite("TRY_CONVERT(decimal(19,2),d.[mab])"));
    }

    @Test
    public void dateTargetIsGuardedWithIsDate() {
        assertEquals("(CASE WHEN ISDATE(x)=1 THEN CONVERT(date,x,112) ELSE NULL END)",
                LegacySql.rewrite("TRY_CONVERT(date,x,112)"));
    }

    @Test
    public void tryCastBecomesGuardedConvert() {
        assertEquals("(SELECT CONVERT(nvarchar(80),[st]) FROM dbo.getchk)",
                LegacySql.rewrite("(SELECT TRY_CAST([st] AS nvarchar(80)) FROM dbo.getchk)"));
        assertEquals("(CASE WHEN ISNUMERIC([q])=1 THEN CONVERT(int,CONVERT(decimal(38,4),[q])) ELSE NULL END)",
                LegacySql.rewrite("TRY_CAST([q] AS int)"));
    }

    @Test
    public void nestedTryConvertsAreRewrittenInsideOut() {
        String in = "TRY_CONVERT(date,TRY_CONVERT(nvarchar(30),h.[d]),112)";
        String out = LegacySql.rewrite(in);
        assertEquals("(CASE WHEN ISDATE(CONVERT(nvarchar(30),h.[d]))=1 THEN CONVERT(date,CONVERT(nvarchar(30),h.[d]),112) ELSE NULL END)",
                out);
        assertFalse(out.contains("TRY_"));
    }

    @Test
    public void stringLiteralsWithParensAndCommasStayIntact() {
        String in = "SELECT TRY_CONVERT(nvarchar(60),N'a,b(c''d') AS t, TRY_CONVERT(int,x.[n]) AS n";
        String out = LegacySql.rewrite(in);
        assertTrue(out.contains("N'a,b(c''d')"));
        assertTrue(out.contains("CONVERT(nvarchar(60),N'a,b(c''d')"));
        assertTrue(out.contains("ISNUMERIC(x.[n])=1"));
        assertFalse(out.contains("TRY_"));
    }

    @Test
    public void bracketedNamesWithDotsAreNotSplit() {
        String out = LegacySql.rewrite("TRY_CONVERT(nvarchar(100),d.[sh . ka, x])");
        assertEquals("CONVERT(nvarchar(100),d.[sh . ka, x])", out);
    }

    @Test
    public void offsetFetchBecomesRowNumberWindow() {
        String in = "SELECT h.[date] AS d FROM dbo.sal h WHERE h.[p]=?"
                + " ORDER BY h.[date] DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        assertEquals("SELECT _q.* FROM (SELECT ROW_NUMBER() OVER (ORDER BY h.[date] DESC) AS _rn,"
                        + " h.[date] AS d FROM dbo.sal h WHERE h.[p]=?) _q"
                        + " WHERE _q._rn - ? BETWEEN 1 AND ? ORDER BY _q._rn",
                LegacySql.rewrite(in));
    }

    @Test
    public void unpagedSelectKeepsItsOrder() {
        String sql = "SELECT b FROM t ORDER BY 2 DESC";
        assertEquals(sql, LegacySql.rewrite(sql));
    }

    @Test
    public void untouchedSqlPassesThrough() {
        String sql = "SELECT CONVERT(int,x) , CAST(y AS nvarchar(10)) FROM t WHERE z=N'ok'";
        assertEquals(sql, LegacySql.rewrite(sql));
    }

    @Test
    public void wholeDashboardShapesLoseEveryTryToken() {
        String sql = "SELECT COALESCE(TRY_CONVERT(decimal(19,2),h.[mab]),0) AS m,"
                + " LEFT(TRY_CONVERT(nvarchar(30),h.[d]),10) AS day,"
                + " (SELECT COUNT(1) FROM dbo.getchk q WHERE TRY_CONVERT(int,q.[bank])=?"
                + " AND (TRY_CONVERT(nvarchar(40),q.[st]) NOT IN (N'6',N'7',N'8') OR TRY_CONVERT(nvarchar(40),q.[st]) IS NULL)) AS chq";
        String out = LegacySql.rewrite(sql);
        assertFalse(out.contains("TRY_"));
        assertTrue(out.contains("COALESCE((CASE WHEN ISNUMERIC(h.[mab])=1 THEN CONVERT(decimal(19,2),h.[mab]) ELSE NULL END),0)"));
        assertTrue(out.contains("LEFT(CONVERT(nvarchar(30),h.[d]),10)"));
    }
}
