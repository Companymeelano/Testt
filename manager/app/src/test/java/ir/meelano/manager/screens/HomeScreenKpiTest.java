package ir.meelano.manager.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;
import org.junit.Test;

public class HomeScreenKpiTest {
    @Test
    public void closeoutTilesUseShortStatesAndHelpfulHintsInsteadOfTruncatedCopy() {
        assertEquals("ثبت نشده", HomeScreen.compactCloseoutVariance("شمارش محلی ثبت نشده"));
        assertEquals("ماندهٔ آغاز و شمارش پایان را وارد کنید",
                HomeScreen.closeoutVarianceHint("شمارش محلی ثبت نشده"));
        assertEquals("ناموجود", HomeScreen.compactCloseoutVariance(
                "تفکیک نقد در دادهٔ فروشگاه موجود نیست"));
        assertEquals("تفکیک دریافت و پرداخت نقد در دسترس نیست",
                HomeScreen.closeoutVarianceHint("تفکیک نقد در دادهٔ فروشگاه موجود نیست"));
        assertEquals("کسری ۱۲٬۰۰۰ تومان",
                HomeScreen.compactCloseoutVariance("کسری ۱۲٬۰۰۰ تومان"));
        assertEquals("در برابر ماندهٔ مورد انتظار",
                HomeScreen.closeoutVarianceHint("کسری ۱۲٬۰۰۰ تومان"));
    }

    @Test
    public void zeroCurrentVersusZeroPreviousIsReportedAsUnchangedNotMinusOneHundredPercent() {
        HomeScreen screen = new HomeScreen(null);

        Kit.Kpi kpi = screen.homeKpi("فروش", 0, 0, "فاکتور", true, 0d, null,
                Theme.SECTION_SALES, ir.meelano.manager.R.drawable.mi_point_of_sale,
                true, false, null);

        assertEquals("بدون تغییر · نسبت به روز قبل", kpi.sub);
    }

    @Test
    public void purchaseIncreaseUsesUnfavorableColorAndShowsItsComparisonBase() {
        HomeScreen screen = new HomeScreen(null);

        Kit.Kpi kpi = screen.homeKpi("خرید", 2_000_000, 3, "فاکتور امروز", true,
                1_000_000d, null, Theme.SECTION_PURCHASE,
                ir.meelano.manager.R.drawable.mi_shopping_cart, false, false, null);

        assertEquals(Theme.DANGER, kpi.subColor);
        assertTrue(kpi.sub.contains("+"));
        assertTrue(kpi.detail.contains("مبنای مقایسه (روز قبل)"));
        assertTrue(kpi.detail.contains("تومان") || kpi.detail.contains("ریال"));
    }

    @Test
    public void hugeKpiChangesUseSignedMoneyWhileKeepingTheComparisonBaseVisible() {
        HomeScreen screen = new HomeScreen(null);
        double current = 1_000_000_000_000d;
        double previous = 1d;

        Kit.Kpi kpi = screen.homeKpi("خرید", current, 1, "فاکتور", true,
                previous, null, Theme.SECTION_PURCHASE,
                ir.meelano.manager.R.drawable.mi_shopping_cart, false, false, null);

        assertTrue(kpi.sub.contains(HomeNumberFormatter.signedCompactMoney(current - previous)));
        assertFalse(kpi.sub.contains("٪"));
        assertTrue(kpi.detail.contains("مبنای مقایسه (روز قبل)"));
        assertEquals(HomeNumberFormatter.signedCompactMoney(current - previous),
                HomeScreen.kpiChangeText(current, previous, Double.POSITIVE_INFINITY));
    }

    @Test
    public void zeroReturnsAfterANonzeroPreviousPeriodShowAFavorableDrop() {
        HomeScreen screen = new HomeScreen(null);

        Kit.Kpi kpi = screen.homeKpi("برگشتی", 0, 0, "سند در ۳۰ روز", true,
                500_000d, null, Theme.SECTION_RETURNS,
                ir.meelano.manager.R.drawable.mi_assignment_return, false, true, null);

        assertFalse(kpi.neutralStatus);
        assertTrue(kpi.sub.contains("−۱۰۰"));
        assertTrue(kpi.sub.contains("۳۰روزهٔ پیشین"));
        assertEquals(Theme.SUCCESS, kpi.subColor);
    }

    @Test
    public void negativeGrossProfitIsGivenDangerTone() {
        assertEquals(Theme.DANGER, HomeScreen.grossProfitValueTone(-8_546_444d));
        assertEquals(Theme.TEXT, HomeScreen.grossProfitValueTone(0d));
    }

    @Test
    public void unavailableTrendSourcesCannotExposeAListAction() {
        assertFalse(HomeScreen.shouldShowTrendAction(true, false, false));
        assertFalse(HomeScreen.shouldShowTrendAction(true, true, false));
        assertFalse(HomeScreen.shouldShowTrendAction(false, true, true));
        assertTrue(HomeScreen.shouldShowTrendAction(true, true, true));
    }

    @Test
    public void missingDaySummaryShowsUnavailableInertKpi() {
        HomeScreen screen = new HomeScreen(null);

        Kit.Kpi kpi = screen.dayKpi("دریافت", "dar_in", null, "count", "قبض", 0xFF35C26E);

        assertEquals("دریافت", kpi.label);
        assertEquals("—", kpi.value);
        assertEquals("داده در دسترس نیست", kpi.sub);
        assertNull(kpi.action);
    }
}
