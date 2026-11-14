package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discovery helpers must stay bounded and must never probe outside the machine's own LAN blocks. */
class WindowsServerDiscoveryTest {

    @Test void probesBothTheDefaultAndTheForwardedPort() {
        assertTrue(WindowsServerDiscovery.PROBE_PORTS.length == 2);
        assertTrue(WindowsServerDiscovery.PROBE_PORTS[0] == 1433);
        assertTrue(WindowsServerDiscovery.PROBE_PORTS[1] == 5050);
    }

    @Test void invalidTargetsAreNeverProbed() {
        assertFalse(WindowsServerDiscovery.reachable("", 1433));
        assertFalse(WindowsServerDiscovery.reachable(null, 1433));
        assertFalse(WindowsServerDiscovery.reachable("192.168.1.10", 0));
        assertFalse(WindowsServerDiscovery.reachable("192.168.1.10", 70000));
        assertFalse(WindowsServerDiscovery.reachable("host;drop table x", 1433));
    }

    @Test void localSubnetScanStaysInsideSiteLocalIpv4Blocks() {
        // Depends on the machine's interfaces, so it validates whatever the runner actually has:
        // a /24 prefix is three numeric octets plus a trailing dot, and only inside a private range.
        List<String> subnets = WindowsServerDiscovery.localSubnets();
        assertNotNull(subnets);
        for (String subnet : subnets) {
            assertTrue(subnet.endsWith("."), "subnet prefix must end with a dot: " + subnet);
            String[] parts = subnet.split("\\.");
            assertEquals(3, parts.length, "a /24 scan prefix is three octets: " + subnet);
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                assertTrue(octet >= 0 && octet <= 255, "octet out of range: " + subnet);
            }
            assertTrue(WindowsSqlSettings.isPrivateAddress(subnet + "1"),
                    "only private ranges may be scanned: " + subnet);
            assertTrue(WindowsSqlSettings.isPrivateAddress(subnet + "254"),
                    "only private ranges may be scanned: " + subnet);
        }
    }

    @Test void scanPrefixesAreDerivedFromTheAddressTextAlone() {
        assertEquals("192.168.1.", WindowsServerDiscovery.subnetPrefix("192.168.1.42"));
        assertEquals("10.0.7.", WindowsServerDiscovery.subnetPrefix("10.0.7.254"));
        assertEquals("172.16.30.", WindowsServerDiscovery.subnetPrefix(" 172.16.30.9 "));
        assertEquals("", WindowsServerDiscovery.subnetPrefix(""));
        assertEquals("", WindowsServerDiscovery.subnetPrefix(null));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("192.168.1"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("192.168.1.42.7"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("192.168.1."));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("999.1.1.1"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("192.168.01x.1.1"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("host;drop table x"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("fe80::1"));
        assertEquals("", WindowsServerDiscovery.subnetPrefix("srv01.local"));
    }

    @Test void candidatesCarryAValidHostAndPort() {
        // No live scanning in unit tests: the scan itself is exercised on a machine with a LAN.
        WindowsServerDiscovery.Candidate candidate =
                new WindowsServerDiscovery.Candidate("192.168.1.10", 5050, "SRV01 · 192.168.1.10");
        assertTrue(WindowsSqlSettings.validHost(candidate.host));
        assertTrue(WindowsSqlSettings.validPort(candidate.port));
        assertFalse(candidate.label.isBlank());
    }

    @Test void listingDatabasesRequiresACompleteLoginAndNeverConnectsWithoutOne() {
        assertTrue(WindowsServerDiscovery.listDatabases("192.0.2.1", 1433, "", "").isEmpty());
        assertTrue(WindowsServerDiscovery.listDatabases("192.0.2.1", 1433, "user", null).isEmpty());
        assertTrue(WindowsServerDiscovery.listDatabases("", 1433, "user", "pass").isEmpty());
        assertTrue(WindowsServerDiscovery.listDatabases("192.0.2.1", 0, "user", "pass").isEmpty());
    }
}
