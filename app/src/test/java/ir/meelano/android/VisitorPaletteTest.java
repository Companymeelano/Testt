package ir.meelano.android;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class VisitorPaletteTest {
    @Test public void primaryControlColoursReachSevenToOne() {
        int[] themes = {0xff007bff, 0xffcea44a, 0xff12856a, 0xff8aa8f8, 0xff292b32, 0xfff9e8ae};
        for (int theme : themes) {
            int text = VisitorPalette.foreground(theme);
            int background = VisitorPalette.ensureContrast(theme, text, 7.0);
            assertTrue(VisitorPalette.contrast(text, background) >= 7.0);
        }
    }
    @Test public void secondaryTextRemainsLegibleOnBothLightAndDarkSurfaces() {
        int[] surfaces = {0xffffffff, 0xffddecff, 0xff12161f, 0xff211b15};
        for (int surface : surfaces) {
            int background = VisitorPalette.blend(surface, 0xff0080ff, .07);
            int text = VisitorPalette.ensureContrast(0xff477eae, background, 7.0);
            assertTrue(VisitorPalette.contrast(text, background) >= 7.0);
        }
    }
}
