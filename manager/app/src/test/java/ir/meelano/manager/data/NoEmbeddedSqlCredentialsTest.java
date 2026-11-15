package ir.meelano.manager.data;

import ir.meelano.licensing.License;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The SQL endpoint and login are provisioned once on the device: no server, database,
 * username or password may be compiled into the app or carried by a connection card.
 */
public class NoEmbeddedSqlCredentialsTest {

    @Test
    public void atiranExposesNoCompiledEndpointOrLoginDefaults() {
        for (Method method : Atiran.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse("compiled SQL default found: " + name,
                    name.equals("defaultHost") || name.equals("defaultDb")
                            || name.equals("defaultUser") || name.equals("defaultPass"));
        }
        assertEquals(1433, Atiran.DEFAULT_PORT);
    }

    @Test
    public void licensingCarriesNoSharedSqlLogin() {
        for (Field field : License.class.getDeclaredFields()) {
            String name = field.getName();
            assertFalse("shared SQL credential found: " + name,
                    name.equals("SQL_USER") || name.equals("SQL_PASS"));
        }
    }

    @Test
    public void connectionCardCarriesEndpointOnly() {
        License.NetProfile profile = new License.NetProfile();
        profile.host = "192.168.1.10";
        profile.port = "1433";
        profile.db = "AtiranDb";
        String card = License.netLine("ABCD1234", profile);

        License.DbProfile parsed = License.parseAnyCard(card, "ABCD1234");
        assertTrue(parsed != null);
        assertEquals("192.168.1.10", parsed.host);
        assertEquals("1433", parsed.port);
        assertEquals("AtiranDb", parsed.db);
        assertTrue("card must not carry a SQL username", parsed.user.isEmpty());
        assertTrue("card must not carry a SQL password", parsed.pass.isEmpty());
    }

    @Test
    public void forwardedPortSurvivesTheEndpointCard() {
        License.NetProfile profile = new License.NetProfile();
        profile.host = "203.0.113.7";
        profile.port = "5050";
        profile.db = "AtiranDb";
        License.DbProfile parsed = License.parseAnyCard(License.netLine("WXYZ9876", profile), "WXYZ9876");
        assertTrue(parsed != null);
        assertEquals("5050", parsed.port);
        assertEquals(5050, Settings.parsePort(parsed.port));
    }

    @Test
    public void endpointCardsAreBoundToOneDevice() {
        License.NetProfile profile = new License.NetProfile();
        profile.host = "192.168.1.10";
        profile.port = "1433";
        profile.db = "AtiranDb";
        String card = License.netLine("ABCD1234", profile);
        assertEquals(null, License.parseAnyCard(card, "ZZZZZZZZ"));
    }
}
