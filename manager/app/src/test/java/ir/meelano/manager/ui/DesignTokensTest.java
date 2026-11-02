package ir.meelano.manager.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DesignTokensTest {
    @Test public void paletteMatchesTheExecutiveRoyalBrief() {
        assertEquals(0xFF0D3028, DesignTokens.EMERALD_DEEP);
        assertEquals(0xFF123D32, DesignTokens.EMERALD);
        assertEquals(0xFFB69A60, DesignTokens.GOLD);
        assertEquals(0xFFE9DFC7, DesignTokens.GOLD_LIGHT);
        assertEquals(0xFFFAF7EF, DesignTokens.IVORY);
        assertEquals(0xFFF2EEE4, DesignTokens.PAPER);
        assertEquals(0xFF1D2924, DesignTokens.INK);
        assertEquals(0xFF69746D, DesignTokens.LIGHT_MUTED);
        assertEquals(0xFFE4D9C0, DesignTokens.BORDER);
        assertEquals(DesignTokens.EMERALD_DEEP, DesignTokens.NIGHT_BG);
        assertEquals(DesignTokens.EMERALD, DesignTokens.NIGHT_SURFACE);
        assertEquals(0xFF2D9A73, DesignTokens.NIGHT_EMERALD);
    }

    @Test public void approvedSecondaryInkMeetsAaOnRoyalIvoryAndSafeVariantCoversSoftSurface() {
        assertTrue("approved secondary ink must meet WCAG AA on Ivory",
                DesignTokens.contrastRatio(DesignTokens.LIGHT_MUTED, DesignTokens.IVORY) >= 4.5);
        int safe = DesignTokens.textSafeColor(DesignTokens.LIGHT_MUTED, DesignTokens.PAPER, false);
        assertTrue("raised Soft Surface uses a contrast-safe secondary ink",
                DesignTokens.contrastRatio(safe, DesignTokens.PAPER) >= 4.8);
        assertTrue("metallic gold keeps a darker, accessible text companion",
                DesignTokens.contrastRatio(DesignTokens.GOLD_DARK, DesignTokens.PAPER) >= 4.5);
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
        assertEquals(DesignTokens.TOP_PERFORMERS, DesignTokens.sectionAccent("مشتری‌های برترین"));
        assertEquals(DesignTokens.SALES_GOAL, DesignTokens.sectionAccent("هدف فروش ماهانه"));
        assertEquals(DesignTokens.GROSS_PROFIT, DesignTokens.sectionAccent("سود ناخالص"));
        assertEquals(DesignTokens.SEASONAL_CALENDAR, DesignTokens.sectionAccent("تقویم فصلی خشکبار"));
        assertEquals(DesignTokens.QUICK_ACTIONS, DesignTokens.sectionAccent("میان‌برهای سریع"));
        assertEquals(DesignTokens.SALES_FORECAST, DesignTokens.sectionAccent("پیش‌بینی فروش ۷روزه"));
    }

    @Test public void accessibleSectionTextPassesAaOnBothPaletteSurfaces() {
        int[] accents = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.ALERT,
                DesignTokens.MARKET, DesignTokens.WAREHOUSE, DesignTokens.CLOSEOUT,
                DesignTokens.CASH_POSITION, DesignTokens.TOP_PERFORMERS, DesignTokens.SALES_GOAL,
                DesignTokens.GROSS_PROFIT, DesignTokens.SEASONAL_CALENDAR,
                DesignTokens.QUICK_ACTIONS, DesignTokens.SALES_FORECAST};
        for (int accent : accents) {
            assertTrue("light contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, false),
                            DesignTokens.PAPER) >= 4.8);
            assertTrue("dark contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accessibleAccent(accent, true),
                            DesignTokens.NIGHT_PAPER) >= 4.8);
        }
    }

    @Test public void dashboardMetricSurfacesStayQuietAndReadableInBothSkins() {
        int[] accents = {DesignTokens.SALES, DesignTokens.PURCHASE, DesignTokens.RECEIVE,
                DesignTokens.PAY, DesignTokens.RETURNS, DesignTokens.CLOSEOUT,
                DesignTokens.CASH_POSITION};
        for (int accent : accents) {
            int lightSurface = DesignTokens.dashboardMetricSurface(accent, false);
            int darkSurface = DesignTokens.dashboardMetricSurface(accent, true);
            assertTrue("light dashboard metric text contrast",
                    DesignTokens.contrastRatio(DesignTokens.INK, lightSurface) >= 4.8);
            assertTrue("light dashboard muted-label contrast",
                    DesignTokens.contrastRatio(DesignTokens.textSafeColor(
                            DesignTokens.LIGHT_MUTED, lightSurface, false), lightSurface) >= 4.8);
            assertTrue("dark dashboard metric text contrast",
                    DesignTokens.contrastRatio(DesignTokens.NIGHT_TEXT, darkSurface) >= 4.8);
            assertTrue("dark dashboard muted-label contrast",
                    DesignTokens.contrastRatio(DesignTokens.NIGHT_MUTED, darkSurface) >= 4.8);
            assertTrue("dashboard metric uses less tint than a standard card",
                    lightSurface != DesignTokens.surfaceTint(accent, false));
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
                DesignTokens.CLOSEOUT, DesignTokens.CASH_POSITION,
                DesignTokens.TOP_PERFORMERS, DesignTokens.SALES_GOAL, DesignTokens.GROSS_PROFIT,
                DesignTokens.SEASONAL_CALENDAR,
                DesignTokens.QUICK_ACTIONS, DesignTokens.SALES_FORECAST};
        for (int accent : accents) {
            assertTrue("solid accent text contrast for " + Integer.toHexString(accent),
                    DesignTokens.contrastRatio(DesignTokens.accentTextColor(accent), accent) >= 4.5);
        }
    }

    @Test public void mutedCopyPassesAaOnBothPreviewCards() {
        int lightCard = DesignTokens.surfaceTint(DesignTokens.EMERALD, false);
        int darkCard = DesignTokens.surfaceTint(DesignTokens.NIGHT_EMERALD, true);
        assertTrue("light muted copy contrast",
                DesignTokens.contrastRatio(DesignTokens.textSafeColor(
                        DesignTokens.LIGHT_MUTED, lightCard, false), lightCard) >= 4.8);
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
                DesignTokens.CASH_POSITION, DesignTokens.TOP_PERFORMERS, DesignTokens.SALES_GOAL,
                DesignTokens.GROSS_PROFIT, DesignTokens.SEASONAL_CALENDAR,
                DesignTokens.QUICK_ACTIONS, DesignTokens.SALES_FORECAST};
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
