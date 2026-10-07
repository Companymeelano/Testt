package ir.meelano.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Phase-7H: the glyph→vector mapping is the single source of truth for icon
 * coherence, so it is pinned by unit tests (JVM, no Android runtime needed).
 */
public class MeelanoDesignKitTest {

    @Test
    public void trendGlyphsMapToBrandVectors() {
        assertEquals(R.drawable.lux_trending_up, MeelanoDesignKit.iconRes("↗"));
        assertEquals(R.drawable.mi_trending_down, MeelanoDesignKit.iconRes("▼"));
        assertEquals(R.drawable.mi_trending_up, MeelanoDesignKit.iconRes("▲"));
    }

    @Test
    public void warningAndSuccessGlyphsMap() {
        assertEquals(R.drawable.lux_warning, MeelanoDesignKit.iconRes("!"));
        assertEquals(R.drawable.lux_warning, MeelanoDesignKit.iconRes("⚠"));
        assertEquals(R.drawable.lux_check_circle, MeelanoDesignKit.iconRes("✓"));
        assertEquals(R.drawable.lux_check_circle, MeelanoDesignKit.iconRes("✅"));
    }

    @Test
    public void emojiGlyphsMapToMonochromeVectors() {
        // colour emoji must never reach the screen: every emoji used anywhere maps to a vector
        assertNotEquals(0, MeelanoDesignKit.iconRes("\ud83d\ude9a")); // 🚚
        assertNotEquals(0, MeelanoDesignKit.iconRes("\ud83d\udcb5")); // 💵
        assertNotEquals(0, MeelanoDesignKit.iconRes("☎"));
        assertNotEquals(0, MeelanoDesignKit.iconRes("✉"));
        assertNotEquals(0, MeelanoDesignKit.iconRes("\ud83d\udcca")); // 📊
        assertNotEquals(0, MeelanoDesignKit.iconRes("\ud83d\uded2")); // 🛒
    }

    @Test
    public void unmappedStringsFallBackToText() {
        assertEquals(0, MeelanoDesignKit.iconRes("CEO"));
        assertEquals(0, MeelanoDesignKit.iconRes("SA"));
        assertEquals(0, MeelanoDesignKit.iconRes("360"));
        assertEquals(0, MeelanoDesignKit.iconRes(null));
    }

    @Test
    public void navGlyphsAndLabelsAgree() {
        // every navigation key has both a glyph and a Persian label (no key renders blank)
        String[] keys = {"dashboard", "customers", "products", "reports", "personnel",
                "attendance", "taxpayers", "cameras", "alarm", "settings", "management"};
        for (String k : keys) {
            assertTrue("glyph for " + k, MeelanoDesignKit.glyph(k).length() > 0);
            assertTrue("label for " + k, MeelanoDesignKit.label(k).length() > 0);
        }
    }

    @Test
    public void iconMappingIsStableAcrossCalls() {
        for (int i = 0; i < 50; i++) {
            assertEquals(R.drawable.lux_diamond, MeelanoDesignKit.iconRes("◆"));
        }
    }
}
