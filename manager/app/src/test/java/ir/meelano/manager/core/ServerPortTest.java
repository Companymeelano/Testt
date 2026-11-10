package ir.meelano.manager.core;

import ir.meelano.manager.data.Settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Server-port handling: the default is 1433, custom/forwarded ports
 * (5050 → 1433 on the dedicated IP) are accepted, and anything invalid
 * falls back to the default instead of breaking the connection.
 */
public class ServerPortTest {

    @Test
    public void defaultPortIs1433() {
        assertEquals(1433, Settings.parsePort(""));
        assertEquals(1433, Settings.parsePort(null));
        assertEquals(1433, Settings.parsePort("0"));
        assertEquals(1433, Settings.parsePort("70000"));
        assertEquals(1433, Settings.parsePort("abc"));
        assertEquals(1433, Settings.parsePort("1433.5"));
    }

    @Test
    public void customAndForwardedPortsAreAccepted() {
        assertEquals(5050, Settings.parsePort("5050"));
        assertEquals(5050, Settings.parsePort(" 5050 "));
        assertEquals(5050, Settings.parsePort("۵۰۵۰")); // Persian digits
        assertEquals(1433, Settings.parsePort("1433"));
        assertEquals(65535, Settings.parsePort("65535"));
    }

    @Test
    public void typedAddressCarriesItsPort() {
        assertEquals(5050, LanDiscover.typedPort("1.2.3.4:5050"));
        assertEquals(5050, LanDiscover.typedPort("http://1.2.3.4:5050/atiran"));
        assertEquals(5050, LanDiscover.typedPort("1.2.3.4:۵۰۵۰"));
        assertEquals(0, LanDiscover.typedPort("1.2.3.4"));
        assertEquals(0, LanDiscover.typedPort("1.2.3.4:0"));
        assertEquals(0, LanDiscover.typedPort("1.2.3.4:70000"));
        assertEquals(0, LanDiscover.typedPort("1.2.3.4:abc"));
        assertEquals(0, LanDiscover.typedPort(""));
        assertEquals(0, LanDiscover.typedPort(null));
    }

    @Test
    public void typedPortNeverCorruptsTheHost() {
        assertEquals("1.2.3.4", LanDiscover.clean("1.2.3.4:5050"));
        assertEquals("1.2.3.4", LanDiscover.clean("http://1.2.3.4:5050/atiran"));
    }
}
