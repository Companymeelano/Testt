package ir.meelano.manager.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DesignTokensTest {
    @Test public void paletteMatchesTheApprovedBrandValues() {
        assertEquals(0xFF0E4B3A, DesignTokens.EMERALD_DEEP);
        assertEquals(0xFF137A5A, DesignTokens.EMERALD);
        assertEquals(0xFFC8A24A, DesignTokens.GOLD);
        assertEquals(0xFFE7CF8E, DesignTokens.GOLD_LIGHT);
        assertEquals(0xFF8F6F22, DesignTokens.GOLD_DARK);
        assertEquals(0xFFFBF7EE, DesignTokens.IVORY);
        assertEquals(0xFFF4EEDC, DesignTokens.PAPER);
        assertEquals(0xFF1B2B26, DesignTokens.INK);
    }

    @Test public void sectionHuesAreIndependentAndLocalized() {
        assertEquals(DesignTokens.SALES, DesignTokens.sectionAccent("فروش امروز"));
        assertEquals(DesignTokens.PURCHASE, DesignTokens.sectionAccent("خرید"));
        assertEquals(DesignTokens.RECEIVE, DesignTokens.sectionAccent("دریافت"));
        assertEquals(DesignTokens.PAY, DesignTokens.sectionAccent("پرداخت"));
        assertEquals(DesignTokens.RETURNS, DesignTokens.sectionAccent("برگشتی‌ها"));
        assertEquals(DesignTokens.ALERT, DesignTokens.sectionAccent("هشدارها"));
        assertEquals(DesignTokens.MARKET, DesignTokens.sectionAccent("بازار"));
        assertEquals(DesignTokens.WAREHOUSE, DesignTokens.sectionAccent("موجودی انبار"));
    }

    @Test public void accessibleSectionTextPassesAaOnBothPaletteSurfaces() {
        int[] accents = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT,
                DesignTokens.MARKET, DesignTokens.WAREHOUSE};
        for (int accent : accents) {
            assertTrue("light contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, false),
                            DesignTokens.IVORY) >= 4.5);
            assertTrue("dark contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, true),
                            DesignTokens.NIGHT_SURFACE) >= 4.5);
        }
    }

    @Test public void outcomePaletteIsSeparateFromSectionPalette() {
        assertEquals(DesignTokens.EMERALD, DesignTokens.POSITIVE);
        assertEquals(DesignTokens.PAY, DesignTokens.NEGATIVE);
        assertEquals(DesignTokens.ALERT, DesignTokens.WARNING);
        assertEquals(0xFF746B59, DesignTokens.NEUTRAL);
        int tint = DesignTokens.surfaceTint(DesignTokens.EMERALD, false);
        assertEquals(0xFF, (tint >>> 24) & 0xFF);
        assertTrue(tint != DesignTokens.IVORY);
    }
}
