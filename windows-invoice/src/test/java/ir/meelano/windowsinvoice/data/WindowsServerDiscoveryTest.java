package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        List<String> subnets = WindowsServerDiscovery.localSubnets();
        assertNotNull(subnets);
        for (String subnet : subnets) {
            assertTrue(subnet.endsWith("."), "subnet prefix must end with a dot: " + subnet);
            String[] parts = subnet.split("\\.");
            assertTrue(parts.length == 4);
            assertTrue(WindowsSqlSettings.isPrivateAddress(subnet + "1"),
                    "only private ranges may be scanned: " + subnet);
        }
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
