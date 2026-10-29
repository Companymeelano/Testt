package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The naming rule of the 3D artwork. The nut look shows the emerald set, so every picture
 * must be able to find its emerald twin; otherwise a section keeps the small gold medallion
 * and looks tiny next to the emerald pictures.
 */
public class IconArtTest {
    @Test public void aMedallionFindsItsEmeraldTwin() {
        assertEquals("icon3d_emerald_sales", IconArt.emeraldName("icon3d_medal_sales"));
        assertEquals("icon3d_emerald_purchase", IconArt.emeraldName("icon3d_medal_purchase"));
        assertEquals("icon3d_emerald_cash_in", IconArt.emeraldName("icon3d_medal_cash_in"));
    }

    @Test public void aPictureFindsItsEmeraldTwin() {
        assertEquals("icon3d_emerald_home", IconArt.emeraldName("icon3d_home"));
        assertEquals("icon3d_emerald_customers", IconArt.emeraldName("icon3d_customers"));
    }

    @Test public void aFlatGlyphHasNoTwin() {
        assertEquals("", IconArt.emeraldName("mi_home"));
        assertEquals("", IconArt.emeraldName(null));
        assertEquals("", IconArt.emeraldName(""));
        assertFalse(IconArt.isArtwork("mi_home"));
        assertTrue(IconArt.isArtwork("icon3d_home"));
        assertTrue(IconArt.isEmerald("icon3d_emerald_home"));
        assertFalse(IconArt.isEmerald("icon3d_home"));
    }
}
