package ir.meelano.manager.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import ir.meelano.manager.ui.Kit;
import org.junit.Test;

public class HomeScreenKpiTest {
    @Test
    public void missingDaySummaryShowsUnavailableInertKpi() {
        HomeScreen screen = new HomeScreen(null);

        Kit.Kpi kpi = screen.dayKpi("دریافت", "dar_in", null, "count", "قبض", 0xFF35C26E);

        assertEquals("دریافت", kpi.label);
        assertEquals("—", kpi.value);
        assertEquals("هنوز ثبت نشده", kpi.sub);
        assertNull(kpi.action);
    }
}
