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
        assertEquals(0xFF52655D, DesignTokens.LIGHT_MUTED);
        assertEquals(0xFF2D9A73, DesignTokens.NIGHT_EMERALD);
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
        assertEquals(DesignTokens.CLOSEOUT, DesignTokens.sectionAccent("بستن روز"));
        assertEquals(DesignTokens.CASH_POSITION, DesignTokens.sectionAccent("موقعیت نقدی"));
    }

    @Test public void accessibleSectionTextPassesAaOnBothPaletteSurfaces() {
        int[] accents = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT,
                DesignTokens.MARKET, DesignTokens.WAREHOUSE, DesignTokens.CLOSEOUT,
                DesignTokens.CASH_POSITION};
        for (int accent : accents) {
            assertTrue("light contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, false),
                            DesignTokens.IVORY) >= 4.8);
            assertTrue("dark contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, true),
                            DesignTokens.NIGHT_SURFACE) >= 4.8);
        }
    }

    @Test public void tintedComponentPreviewTextPassesAaInLightAndDarkModes() {
        int[] tones = {DesignTokens.EMERALD, DesignTokens.POSITIVE, DesignTokens.NEGATIVE,
                DesignTokens.WARNING, DesignTokens.INFO};
        for (int tone : tones) {
            int lightSurface = DesignTokens.surfaceTint(tone, false);
            int darkSurface = DesignTokens.surfaceTint(tone, true);
            assertTrue("light tinted surface contrast for " + Integer.toHexString(tone),
                    DesignTokens.contrastRatio(
                            DesignTokens.textSafeColor(tone, lightSurface, false), lightSurface) >= 4.8);
            assertTrue("dark tinted surface contrast for " + Integer.toHexString(tone),
                    DesignTokens.contrastRatio(
                            DesignTokens.textSafeColor(tone, darkSurface, true), darkSurface) >= 4.8);
        }
    }

    @Test public void solidAccentLabelsChooseHighContrastInkAcrossPalette() {
        int[] accents = {DesignTokens.EMERALD, DesignTokens.NIGHT_EMERALD, DesignTokens.GOLD,
                DesignTokens.GOLD_LIGHT, DesignTokens.GOLD_DARK, DesignTokens.PURCHASE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT, DesignTokens.WAREHOUSE,
                DesignTokens.CLOSEOUT, DesignTokens.CASH_POSITION};
        for (int accent : accents) {
            assertTrue("solid accent text contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accentTextColor(accent), accent) >= 4.5);
        }
    }

    @Test public void mutedCopyPassesAaOnBothPreviewCards() {
        int lightCard = DesignTokens.surfaceTint(DesignTokens.EMERALD, false);
        int darkCard = DesignTokens.surfaceTint(DesignTokens.NIGHT_EMERALD, true);
        assertTrue("light muted copy contrast",
                DesignTokens.contrastRatio(DesignTokens.LIGHT_MUTED, lightCard) >= 4.8);
        assertTrue("dark muted copy contrast",
                DesignTokens.contrastRatio(DesignTokens.NIGHT_MUTED, darkCard) >= 4.8);
    }

    @Test public void selectedDockInkPassesAaOnBothPreviewSurfaces() {
        int[] accents = {DesignTokens.EMERALD, DesignTokens.NIGHT_EMERALD};
        boolean[] darkModes = {false, true};
        for (int i = 0; i < accents.length; i++) {
            boolean dark = darkModes[i];
            int surface = DesignTokens.surfaceTint(accents[i], dark);
            int dockInk = DesignTokens.textSafeColor(accents[i], surface, dark);
            assertTrue("selected dock ink contrast",
                    DesignTokens.contrastRatio(dockInk, surface) >= 4.8);
            int segmentInk = DesignTokens.accentTextColor(accents[i]);
            assertTrue("selected segmented-control ink contrast",
                    DesignTokens.contrastRatio(segmentInk, accents[i]) >= 4.8);
        }
    }

    @Test public void outcomePaletteIsSeparateFromSectionPalette() {
        int[] outcomes = {DesignTokens.POSITIVE, DesignTokens.NEGATIVE, DesignTokens.WARNING,
                DesignTokens.INFO, DesignTokens.NEUTRAL};
        int[] sections = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT,
                DesignTokens.MARKET, DesignTokens.WAREHOUSE, DesignTokens.CLOSEOUT,
                DesignTokens.CASH_POSITION};
        for (int outcome : outcomes) for (int section : sections)
            assertTrue("outcome and section hues are distinct",
                    outcome != section);
        for (int outcome : outcomes) {
            assertTrue("light semantic contrast", DesignTokens.contrastRatio(
                    DesignTokens.accessibleAccent(outcome, false), DesignTokens.IVORY) >= 4.8);
            assertTrue("dark semantic contrast", DesignTokens.contrastRatio(
                    DesignTokens.accessibleAccent(outcome, true), DesignTokens.NIGHT_SURFACE) >= 4.8);
        }
        int tint = DesignTokens.surfaceTint(DesignTokens.EMERALD, false);
        assertEquals(0xFF, (tint >>> 24) & 0xFF);
        assertTrue(tint != DesignTokens.IVORY);
    }
}
