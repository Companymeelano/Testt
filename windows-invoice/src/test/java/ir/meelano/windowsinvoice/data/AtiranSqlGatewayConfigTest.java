package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtiranSqlGatewayConfigTest {
    @Test void noConnectionSettingsMeansDemoModeIsAllowed() {
        assertFalse(AtiranSqlGateway.hasAnyAtiranSetting(Map.of()));
    }

    @Test void anyPartialOrWriteSettingPreventsSilentDemoFallback() {
        assertTrue(AtiranSqlGateway.hasAnyAtiranSetting(Map.of("ATIRAN_SQL_USER", "configured")));
        assertTrue(AtiranSqlGateway.hasAnyAtiranSetting(Map.of("ATIRAN_ALLOW_INVOICE_WRITES", "true")));
        assertTrue(AtiranSqlGateway.hasAnyAtiranSetting(Map.of("ATIRAN_SQL_PASSWORD", " ")));
    }

    @Test void blankNonSecretValuesDoNotCountAsConfigured() {
        assertFalse(AtiranSqlGateway.hasAnyAtiranSetting(Map.of("ATIRAN_SQL_USER", "  ")));
        assertFalse(AtiranSqlGateway.hasAnyAtiranSetting(Map.of("ATIRAN_ALLOW_INVOICE_WRITES", "")));
    }

    @Test void escapesSqlServerLikeWildcardsAndEscapeCharacter() {
        assertEquals("a\\%b\\_c\\[d\\\\e", AtiranSqlGateway.escapeLike("a%b_c[d\\e"));
    }
}
