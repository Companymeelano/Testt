package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Endpoint classification and JDBC URL construction for the Windows client. */
class SqlServerUrlTest {

    @Test void defaultAndForwardedPortsAreAccepted() {
        assertEquals(1433, WindowsSqlSettings.DEFAULT_PORT);
        assertEquals(5050, WindowsSqlSettings.FORWARDED_PORT);
        assertEquals(5050, WindowsSqlSettings.parsePort("5050"));
        assertEquals(5050, WindowsSqlSettings.parsePort("۵۰۵۰"));
        assertEquals(1433, WindowsSqlSettings.parsePort(""));
        assertEquals(1433, WindowsSqlSettings.parsePort("70000"));
        assertEquals(1433, WindowsSqlSettings.parsePort("abc"));
    }

    @Test void typedAddressPortWinsOverThePortField() {
        assertEquals(5050, WindowsServerDiscovery.resolvePort("1433", "203.0.113.7:5050"));
        assertEquals(5050, WindowsServerDiscovery.resolvePort("", "203.0.113.7:5050"));
        assertEquals(1433, WindowsServerDiscovery.resolvePort("1433", "192.168.1.10"));
        assertEquals(5050, WindowsServerDiscovery.resolvePort("not-a-port", "192.168.1.10:5050"));
        assertEquals(0, WindowsServerDiscovery.typedPort("192.168.1.10"));
        assertEquals(0, WindowsServerDiscovery.typedPort("192.168.1.10:0"));
    }

    @Test void typedHostIsCleanedBeforeConnecting() {
        assertEquals("192.168.1.10", WindowsServerDiscovery.cleanHost("192.168.1.10:5050"));
        assertEquals("srv01", WindowsServerDiscovery.cleanHost("srv01\\ATIRAN"));
        assertEquals("192.168.1.10", WindowsServerDiscovery.cleanHost("  192.168.1.10  "));
    }

    @Test void localEndpointMayRunWithoutEncryption() {
        String url = SqlServerUrl.build("192.168.1.10", 1433, "AtiranDb", false);
        assertTrue(url.startsWith("jdbc:sqlserver://192.168.1.10:1433;databaseName=AtiranDb;"));
        assertTrue(url.contains("encrypt=false"));
        assertFalse(SqlServerUrl.requiresEncryption("192.168.1.10"));
        assertTrue(SqlServerUrl.urlIsAcceptable(url, "192.168.1.10"));
    }

    @Test void internetEndpointAlwaysKeepsEncryptionAndCertificateValidation() {
        String url = SqlServerUrl.build("203.0.113.7", 5050, "AtiranDb", false);
        assertTrue(url.contains("encrypt=true"));
        assertTrue(url.contains("trustServerCertificate=false"));
        assertTrue(SqlServerUrl.requiresEncryption("203.0.113.7"));
        assertFalse(SqlServerUrl.urlIsAcceptable(
                "jdbc:sqlserver://203.0.113.7:5050;databaseName=AtiranDb;encrypt=false", "203.0.113.7"));
        assertFalse(SqlServerUrl.urlIsAcceptable(
                "jdbc:sqlserver://203.0.113.7:5050;databaseName=AtiranDb;trustServerCertificate=true",
                "203.0.113.7"));
    }

    @Test void invalidEndpointValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SqlServerUrl.build("", 1433, "AtiranDb", true));
        assertThrows(IllegalArgumentException.class, () -> SqlServerUrl.build("srv", 0, "AtiranDb", true));
        assertThrows(IllegalArgumentException.class, () -> SqlServerUrl.build("srv", 1433, "", true));
        assertThrows(IllegalArgumentException.class,
                () -> SqlServerUrl.build("srv;drop table x", 1433, "AtiranDb", true));
        assertFalse(WindowsSqlSettings.validHost("host with space"));
        assertFalse(WindowsSqlSettings.validDatabase("db/name"));
        assertFalse(WindowsSqlSettings.validPort(65536));
    }

    @Test void hostNamesAndPrivateRangesAreClassified() {
        assertTrue(WindowsSqlSettings.isPrivateAddress("192.168.1.10"));
        assertTrue(WindowsSqlSettings.isPrivateAddress("10.0.0.5"));
        assertTrue(WindowsSqlSettings.isPrivateAddress("SRV01"));
        assertTrue(WindowsSqlSettings.isPrivateAddress("srv01.local"));
        assertFalse(WindowsSqlSettings.isPrivateAddress("203.0.113.7"));
        assertFalse(WindowsSqlSettings.isPrivateAddress(""));
    }

    @Test void savedUrlHostIsParsedBackForPolicyChecks() {
        assertEquals("203.0.113.7",
                AtiranSqlGateway.hostOf("jdbc:sqlserver://203.0.113.7:5050;databaseName=AtiranDb;encrypt=true"));
        assertEquals("192.168.1.10",
                AtiranSqlGateway.hostOf("jdbc:sqlserver://[192.168.1.10]:1433;databaseName=AtiranDb"));
        assertEquals("srv01", AtiranSqlGateway.hostOf("jdbc:sqlserver://srv01:1433;databaseName=AtiranDb"));
        assertEquals("", AtiranSqlGateway.hostOf("not-a-jdbc-url"));
    }
}
