package ir.meelano.manager.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Stage-1 lock: the palette is exactly spec §3.1 and every contrast promise of §7 is proven. */
public class DerakhshanTokensTest {

    @Test public void paletteIsLockedToTheSpec() {
        assertEquals(0xFF04120D, DerakhshanTokens.OBSIDIAN_EMERALD);
        assertEquals(0xFF062A20, DerakhshanTokens.EMERALD_800);
        assertEquals(0xFF0E4C39, DerakhshanTokens.EMERALD_700);
        assertEquals(0xFF1B7A50, DerakhshanTokens.EMERALD_500);
        assertEquals(0xFF2E9C68, DerakhshanTokens.EMERALD_300);
        assertEquals(0xFFC9A227, DerakhshanTokens.GOLD_500);
        assertEquals(0xFFE8CE7A, DerakhshanTokens.GOLD_300);
        assertEquals(0xFFF7EFC8, DerakhshanTokens.GOLD_100);
        assertEquals(0xFFF7F3EA, DerakhshanTokens.CREAM);
        assertEquals(0xFFEFE6D6, DerakhshanTokens.CREAM_2);
        assertEquals(0xFFB08968, DerakhshanTokens.BRONZE);
        assertEquals(0xFF2E7D5B, DerakhshanTokens.SUCCESS);
        assertEquals(0xFFC98A2E, DerakhshanTokens.WARNING);
        assertEquals(0xFFB4434B, DerakhshanTokens.DANGER);
        assertEquals(0xFF4C6E8A, DerakhshanTokens.INFO);
    }

    @Test public void thereIsExactlyOneLightSourceAt150Degrees() {
        assertEquals(150f, DerakhshanTokens.LIGHT_ANGLE_DEG, 0.001f);
        assertEquals(150f, DerakhshanTokens.LIGHT_ANGLE_DEG, 0.001f);
        // Gold material stops follow the spec exactly: #F7EFC8 → #D9B45A at 55% → #A8821E.
        assertEquals(0xFFF7EFC8, DerakhshanTokens.GOLD_MATERIAL_DARK[0]);
        assertEquals(0xFFD9B45A, DerakhshanTokens.GOLD_MATERIAL_DARK[1]);
        assertEquals(0xFFA8821E, DerakhshanTokens.GOLD_MATERIAL_DARK[2]);
        assertEquals(0.55f, DerakhshanTokens.GOLD_MATERIAL_STOPS[1], 0.0001f);
    }

    @Test public void contrastEngineMatchesWcagReferenceValues() {
        assertEquals(21d, DerakhshanTokens.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.05d);
        assertEquals(1d, DerakhshanTokens.contrastRatio(0xFF123456, 0xFF123456), 0.0001d);
        assertEquals(DerakhshanTokens.contrastRatio(0xFF1F180C, DerakhshanTokens.CREAM),
                DerakhshanTokens.contrastRatio(DerakhshanTokens.CREAM, 0xFF1F180C), 0.0001d);
    }

    @Test public void lightThemeMeetsSevenToOneMainAndFourFiveToSecondary() {
        DerakhshanTokens.Surface s = DerakhshanTokens.LIGHT;
        assertTrue(DerakhshanTokens.contrastRatio(s.ink, s.bg) >= 7d);
        assertTrue(DerakhshanTokens.contrastRatio(s.inkSecondary, s.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(s.inkMeta, s.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(s.ink, s.stone[1]) >= 7d);
        assertTrue(DerakhshanTokens.contrastRatio(s.inkSecondary, s.stone[1]) >= 4.5d);
    }

    @Test public void lightThemeNeverPaintsBrightGoldText() {
        DerakhshanTokens.Surface s = DerakhshanTokens.LIGHT;
        // The spec trap: #C9A227 on cream is only ~2.6:1 — the token must not be gold-500.
        // The spec's #8E6C14 itself measures 4.40:1, so the token sits one step deeper.
        assertFalse(s.goldText == DerakhshanTokens.GOLD_500);
        assertEquals(0xFF856310, s.goldText);
        assertTrue(DerakhshanTokens.contrastRatio(s.goldText, s.bg) >= 4.5d);
        // The deeper hero gradient keeps the large-text 3:1 floor on cream.
        assertTrue(DerakhshanTokens.gradientMeets(s.heroGold, s.bg, 3d));
        assertTrue(DerakhshanTokens.gradientMeets(s.heroGold, s.stone[1], 3d));
    }

    @Test public void darkThemeMeetsContrastOnObsidianAndElevatedEmerald() {
        DerakhshanTokens.Surface s = DerakhshanTokens.DARK;
        assertTrue(DerakhshanTokens.contrastRatio(s.ink, s.bg) >= 7d);
        assertTrue(DerakhshanTokens.contrastRatio(s.inkSecondary, s.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(s.inkMeta, s.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(s.ink, s.bgDeep) >= 7d);
        assertTrue(DerakhshanTokens.contrastRatio(s.goldText, s.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.gradientMeets(s.heroGold, s.bg, 3d));
        assertTrue(DerakhshanTokens.gradientMeets(s.heroGold, s.bgDeep, 3d));
    }

    @Test public void semanticsKeepTextContrastAndWarningSplitsFillFromInk() {
        DerakhshanTokens.Surface light = DerakhshanTokens.LIGHT;
        int stoneWhite = light.stone[0];
        assertTrue(DerakhshanTokens.contrastRatio(light.successText, stoneWhite) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(light.warningText, stoneWhite) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(light.dangerText, stoneWhite) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(light.infoText, stoneWhite) >= 4.5d);
        // The amber hue is a fill, never body text: as the C4 Soft chip (14% over the card)
        // it must still carry the dark warning ink at ≥ 4.5:1 on both card surfaces.
        assertTrue(DerakhshanTokens.contrastRatio(light.warningText,
                DerakhshanTokens.softChip(DerakhshanTokens.WARNING, stoneWhite)) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(light.warningText,
                DerakhshanTokens.softChip(DerakhshanTokens.WARNING, light.bg)) >= 4.5d);

        DerakhshanTokens.Surface dark = DerakhshanTokens.DARK;
        assertTrue(DerakhshanTokens.contrastRatio(dark.successText, dark.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(dark.warningText, dark.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(dark.dangerText, dark.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(dark.infoText, dark.bg) >= 4.5d);
        assertTrue(DerakhshanTokens.contrastRatio(dark.successText, dark.bgDeep) >= 4.5d);
    }

    @Test public void ctaCarriesWhiteTextAcrossTheWholeEmeraldGradient() {
        assertTrue(DerakhshanTokens.gradientMeets(DerakhshanTokens.EMERALD_MATERIAL,
                DerakhshanTokens.ON_EMERALD, 4.5d));
    }

    @Test public void motionStaysInsideThe700msBudgetExceptTheSinglePulse() {
        assertTrue(DerakhshanTokens.DUR_FAST_MS <= 700);
        assertTrue(DerakhshanTokens.DUR_MID_MS <= 700);
        assertTrue(DerakhshanTokens.DUR_SLOW_MS <= 700);
        assertTrue(DerakhshanTokens.CARD_ENTRY_MS <= 700);
        assertTrue(DerakhshanTokens.COUNT_UP_MS >= 500 && DerakhshanTokens.COUNT_UP_MS <= 620);
        assertTrue(DerakhshanTokens.CHART_DRAW_MS <= 700);
        assertTrue(DerakhshanTokens.PRESS_MS <= 220);
        assertTrue(DerakhshanTokens.RELEASE_MS <= 220);
        assertTrue(DerakhshanTokens.TILT_RETURN_MS <= 700);
        // The only long-running motions allowed by §3.5:
        assertEquals(2400, DerakhshanTokens.URGENT_PULSE_MS);
        assertEquals(900, DerakhshanTokens.SHIMMER_MS);
        assertTrue(DerakhshanTokens.TILT_MAX_DEG <= 3f);
        assertEquals(0.22f, DerakhshanTokens.EASE_X1, 0.0001f);
        assertEquals(1.0f, DerakhshanTokens.EASE_Y1, 0.0001f);
        assertEquals(0.36f, DerakhshanTokens.EASE_X2, 0.0001f);
        assertEquals(1.0f, DerakhshanTokens.EASE_Y2, 0.0001f);
    }

    @Test public void componentMetricsFollowSection4() {
        assertEquals(24, DerakhshanTokens.CHIP_HEIGHT_DP);
        assertEquals(8, DerakhshanTokens.CHIP_RADIUS_DP);
        assertEquals(14, DerakhshanTokens.MEDAL_RADIUS_DP);
        assertTrue(DerakhshanTokens.TOUCH_MIN_DP >= 44);
        assertTrue(DerakhshanTokens.BUTTON_PRIMARY_HEIGHT_DP >= 48);
        assertTrue(DerakhshanTokens.BUTTON_GHOST_HEIGHT_DP >= 40);
        assertEquals(2, DerakhshanTokens.GOLD_EDGE_HEIGHT_DP);
        assertEquals(0.34f, DerakhshanTokens.GOLD_EDGE_WIDTH_FRACTION, 0.0001f);
        assertEquals(2.5f, DerakhshanTokens.CHART_LINE_DP, 0.0001f);
        assertEquals(7, DerakhshanTokens.CHART_MA_WINDOW_DAYS);
        assertEquals(54, DerakhshanTokens.SPARKLINE_HEIGHT_DP);
        assertEquals(120, DerakhshanTokens.EMPTY_STATE_ART_DP);
    }

    @Test public void onlyFourShadowLevelsExist() {
        assertTrue(DerakhshanTokens.E1_DP < DerakhshanTokens.E2_DP);
        assertTrue(DerakhshanTokens.E2_DP < DerakhshanTokens.E3_DP);
        assertTrue(DerakhshanTokens.E3_DP < DerakhshanTokens.E4_DP);
        assertTrue(DerakhshanTokens.E4_CSS.contains("inset"));
        assertFalse(DerakhshanTokens.E1_CSS.contains("inset"));
    }

    @Test public void themeSelectsTheRightVelvet() {
        assertTrue(DerakhshanTokens.theme(true).dark);
        assertFalse(DerakhshanTokens.theme(false).dark);
        assertEquals("شب زمرد", DerakhshanTokens.theme(true).name);
        assertEquals("مخمل کرم", DerakhshanTokens.theme(false).name);
    }
}
