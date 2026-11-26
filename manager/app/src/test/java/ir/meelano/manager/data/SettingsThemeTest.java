package ir.meelano.manager.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettingsThemeTest {
    @Test public void themeNormalizationKeepsEverySupportedSkinAndFailsSafely() {
        assertEquals("dark", Settings.normalizeThemeMode("dark"));
        assertEquals("light", Settings.normalizeThemeMode("light"));
        assertEquals("luxury", Settings.normalizeThemeMode("luxury"));
        assertEquals("luxury", Settings.normalizeThemeMode("nuts"));
        assertEquals("luxury", Settings.normalizeThemeMode(null));
        assertEquals("luxury", Settings.normalizeThemeMode("unknown"));
    }
}
