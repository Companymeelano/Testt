package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the SQL Server 2008 R2 dialect rewrite used by {@link AtiranSqlGateway}: field servers
 * reject TRY_CONVERT/TRY_CAST at parse time, so statements must leave the gateway without
 * those tokens while keeping «bad value → NULL» semantics.
 */
class LegacySqlTest {

    @Test
    void textTargetBecomesPlainConvert() {
        assertEquals("SELECT CONVERT(nvarchar(60),h.[ghno]) AS k",
                LegacySql.rewrite("SELECT TRY_CONVERT(nvarchar(60),h.[ghno]) AS k"));
    }

    @Test
    void integerTargetIsGuardedAndRoundsThroughDecimal() {
        assertEquals("(CASE WHEN ISNUMERIC(h.[p])=1 THEN CONVERT(int,CONVERT(decimal(38,4),h.[p])) ELSE NULL END)",
                LegacySql.rewrite("TRY_CONVERT(int,h.[p])"));
    }

    @Test
    void dateTargetIsGuardedWithIsDate() {
        assertEquals("(CASE WHEN ISDATE(x)=1 THEN CONVERT(date,x,112) ELSE NULL END)",
                LegacySql.rewrite("TRY_CONVERT(date,x,112)"));
    }

    @Test
    void tryCastBecomesGuardedConvert() {
        assertEquals("CONVERT(nvarchar(80),[st])",
                LegacySql.rewrite("TRY_CAST([st] AS nvarchar(80))"));
        assertEquals("(CASE WHEN ISNUMERIC([q])=1 THEN CONVERT(int,CONVERT(decimal(38,4),[q])) ELSE NULL END)",
                LegacySql.rewrite("TRY_CAST([q] AS int)"));
    }

    @Test
    void nestedTryConvertsAreRewrittenInsideOut() {
        String out = LegacySql.rewrite("TRY_CONVERT(date,TRY_CONVERT(nvarchar(30),h.[d]),112)");
        assertFalse(out.contains("TRY_"));
        assertTrue(out.startsWith("(CASE WHEN ISDATE(CONVERT(nvarchar(30),h.[d]))=1"));
    }

    @Test
    void stringLiteralsWithParensAndCommasStayIntact() {
        String out = LegacySql.rewrite("SELECT TRY_CONVERT(nvarchar(60),N'a,b(c''d') AS t, TRY_CONVERT(int,x.[n]) AS n");
        assertTrue(out.contains("N'a,b(c''d')"));
        assertFalse(out.contains("TRY_"));
    }

    @Test
    void offsetFetchBecomesRowNumberWindow() {
        String in = "SELECT c.[name] AS n FROM dbo.CUSTOMERS c WHERE c.[p]=?"
                + " ORDER BY c.[name] ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        String out = LegacySql.rewrite(in);
        assertTrue(out.startsWith("SELECT _q.* FROM (SELECT ROW_NUMBER() OVER (ORDER BY c.[name] ASC) AS _rn,"));
        assertTrue(out.endsWith(") _q WHERE _q._rn - ? BETWEEN 1 AND ? ORDER BY _q._rn"));
        assertFalse(out.contains("OFFSET"));
    }

    @Test
    void untouchedSqlPassesThrough() {
        String sql = "SELECT CONVERT(int,x) , CAST(y AS nvarchar(10)) FROM t WHERE z=N'ok'";
        assertEquals(sql, LegacySql.rewrite(sql));
    }
}
