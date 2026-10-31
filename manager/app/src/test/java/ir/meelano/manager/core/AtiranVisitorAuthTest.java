package ir.meelano.manager.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AtiranVisitorAuthTest {
    @Test public void visitorLookupUsesSql2008CompatibleSyntax() {
        String sql = AtiranAuth.visitorLookupSql("[dbo].[visitors]", "vis_rdf", "Username",
                "Password", "active", "vis_name");

        assertFalse(sql.toUpperCase(java.util.Locale.US).contains("TRY_CONVERT"));
        assertFalse(sql.toUpperCase(java.util.Locale.US).contains("TRY_CAST"));
        assertTrue(sql.contains("CAST([Username] AS nvarchar(200))"));
        assertTrue(sql.contains("SELECT TOP (2) [vis_rdf], [Username], [Password], [active], [vis_name]"));
        assertTrue(sql.contains("LOWER(LTRIM(RTRIM(?)))"));
    }

    @Test public void visitorLookupKeepsOptionalColumnsSafeWhenAbsent() {
        String sql = AtiranAuth.visitorLookupSql("[dbo].[visitors]", "rdf", "username",
                "password", null, null);

        assertFalse(sql.toUpperCase(java.util.Locale.US).contains("TRY_CONVERT"));
        assertTrue(sql.contains("CAST(NULL AS nvarchar(32))"));
        assertTrue(sql.contains("CAST(N'' AS nvarchar(220))"));
    }

    @Test public void visitorGrantUsesNativeNumericComparisonOrLegacyCast() {
        String numeric = AtiranAuth.visitorGrantSql("[dbo].[sys_vis]", "shvis", "int",
                "UserID", "SysID");
        String text = AtiranAuth.visitorGrantSql("[dbo].[sys_vis]", "shvis", "varchar",
                "UserID", "SysID");

        assertEquals("SELECT [UserID], [SysID] FROM [dbo].[sys_vis] WHERE [shvis]=?", numeric);
        assertTrue(text.endsWith("WHERE LTRIM(RTRIM(CAST([shvis] AS nvarchar(100))))=?"));
        assertFalse((numeric + text).toUpperCase(java.util.Locale.US).contains("TRY_CONVERT"));
        assertTrue(AtiranAuth.isNumericSqlType("INT"));
        assertFalse(AtiranAuth.isNumericSqlType("varchar"));
    }

    @Test public void readsVisitorIdsSafelyInJavaInsteadOfSqlTryConvert() {
        assertEquals(Integer.valueOf(17), AtiranAuth.sqlInteger(" ۱۷ "));
        assertEquals(Integer.valueOf(17), AtiranAuth.sqlInteger(new java.math.BigDecimal("17.000")));
        assertNull(AtiranAuth.sqlInteger("not-an-id"));
        assertNull(AtiranAuth.sqlInteger("17.5"));
    }

    @Test public void preservesTextAndBinaryPasswordBytes() {
        String typed = "visitor-secret-۱۲۳";
        byte[] expectedText = typed.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedText, AtiranAuth.passwordBytes(typed));

        byte[] storedHash = new byte[]{0x01, 0x0f, (byte) 0xfe, (byte) 0xa5};
        assertArrayEquals(storedHash, AtiranAuth.passwordBytes(storedHash));
        assertFalse(storedHash == AtiranAuth.passwordBytes(storedHash));
    }

    @Test public void verifiesVisitorTextCredentialWithoutSysUserHashColumn() {
        String sample = "visitor-test-secret";
        assertTrue(AtiranAuth.verifyVisitorPassword(sample, sample, "sample-visitor"));
        assertFalse(AtiranAuth.verifyVisitorPassword(sample, "different-test-secret", "sample-visitor"));
    }

    @Test public void verifiesStandardHexHashStoredInVisitorsPassword() throws Exception {
        String sample = "visitor-hash-test";
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(sample.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        assertTrue(AtiranAuth.verifyVisitorPassword(hex.toString(), sample, "sample-visitor"));
        assertFalse(AtiranAuth.verifyVisitorPassword("", sample, "sample-visitor"));
    }
}
