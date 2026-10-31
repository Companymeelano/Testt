package ir.meelano.manager.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettingsThemeTest {
    @Test public void themeNormalizationKeepsEverySupportedSkinAndFailsSafely() {
        assertEquals("dark", Settings.normalizeThemeMode("dark"));
        assertEquals("light", Settings.normalizeThemeMode("light"));
        assertEquals("nuts", Settings.normalizeThemeMode("nuts"));
        assertEquals("nuts", Settings.normalizeThemeMode(null));
        assertEquals("nuts", Settings.normalizeThemeMode("unknown"));
    }
}
