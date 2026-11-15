package ir.meelano.manager.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises the real SharedPreferences connection migration without contacting any server. */
@RunWith(AndroidJUnit4.class)
public class ConnectionSettingsMigrationTest {
    private Context scoped;
    private SharedPreferences settingsPrefs;
    private SharedPreferences secretsPrefs;

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String namespace = "connection-test-" + UUID.randomUUID();
        scoped = new ContextWrapper(target) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(namespace + "-" + name, mode);
            }
        };
        settingsPrefs = scoped.getSharedPreferences("meelano_manager7", Context.MODE_PRIVATE);
        secretsPrefs = scoped.getSharedPreferences("meelano_secrets", Context.MODE_PRIVATE);
    }

    @After
    public void tearDown() {
        if (settingsPrefs != null) settingsPrefs.edit().clear().commit();
        if (secretsPrefs != null) secretsPrefs.edit().clear().commit();
    }

    @Test
    public void savingAForwardedPublicEndpointResetsTheOldPinnedLanRoute() {
        settingsPrefs.edit()
                .putString("db_lan", "192.168.1.20")
                .putString("db_port_lan", "1433")
                .putString("link_mode", "lan")
                .apply();

        Settings settings = new Settings(scoped);
        settings.saveSmart("", "198.51.100.25", "", "5050", "sac", "", "");

        assertEquals("auto", settings.linkMode());
        assertEquals("198.51.100.25", settings.wan());
        assertEquals(5050, settings.effPortFor("wan"));
        assertEquals(1433, settings.effPortFor("lan"));
    }

    @Test
    public void staleModeFromAnOlderInstallIsRepairedAndMissingModesCannotBeSelected() {
        settingsPrefs.edit()
                .putString("db_lan", "")
                .putString("db_wan", "198.51.100.25")
                .putString("db_port_wan", "5050")
                .putString("link_mode", "lan")
                .apply();

        Settings settings = new Settings(scoped);
        assertEquals("auto", settings.linkMode());
        assertEquals("auto", settingsPrefs.getString("link_mode", ""));
        assertFalse(settings.setLinkMode("lan"));
        assertEquals("auto", settings.linkMode());
        assertTrue(settings.setLinkMode("wan"));
        assertEquals("wan", settings.linkMode());
    }

    @Test
    public void updatingOneEndpointPreservesTheOtherProfilesLegacySharedPort() {
        settingsPrefs.edit()
                .putString("db_lan", "192.168.1.20")
                .putString("db_port", "1433")
                .putString("link_mode", "lan")
                .apply();

        Settings settings = new Settings(scoped);
        settings.placeHost("198.51.100.25", "5050", "sac", "", "");

        assertEquals("5050", settings.port());
        assertEquals(1433, settings.effPortFor("lan"));
        assertEquals(5050, settings.effPortFor("wan"));
    }

    @Test
    public void successfulCardProfileMarksItsRouteAndResetsManualPin() {
        settingsPrefs.edit()
                .putString("db_lan", "192.168.1.20")
                .putString("link_mode", "lan")
                .apply();

        Settings settings = new Settings(scoped);
        settings.placeHost("198.51.100.25", "5050", "sac", "", "");

        assertEquals("auto", settings.linkMode());
        assertEquals("wan", settings.linkLast());
        assertEquals(5050, settings.effPortFor("wan"));
    }
}
