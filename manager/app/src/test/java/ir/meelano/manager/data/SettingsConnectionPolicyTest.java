package ir.meelano.manager.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Pure connection-policy checks; no Android preferences or live SQL server required. */
public class SettingsConnectionPolicyTest {
    @Test
    public void missingPinnedRouteFallsBackToAuto() {
        assertEquals("auto", Settings.normalizeLinkMode("lan", false, true));
        assertEquals("auto", Settings.normalizeLinkMode("wan", true, false));
        assertEquals("auto", Settings.normalizeLinkMode("unknown", true, true));
    }

    @Test
    public void existingPinnedRouteIsPreserved() {
        assertEquals("lan", Settings.normalizeLinkMode("lan", true, false));
        assertEquals("wan", Settings.normalizeLinkMode("wan", false, true));
        assertEquals("auto", Settings.normalizeLinkMode("auto", true, true));
    }
}
