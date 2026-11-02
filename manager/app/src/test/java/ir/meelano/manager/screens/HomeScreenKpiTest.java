package ir.meelano.manager.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import ir.meelano.manager.ui.Kit;
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
                ir.meelano.manager.ui.Theme.SECTION_SALES,
                ir.meelano.manager.R.drawable.mi_point_of_sale, true, false, null);

        assertEquals("بدون تغییر · نسبت به روز قبل", kpi.sub);
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
