package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ReportCatalogRoleTest {
    @Test public void salesManagerGetsSalesAndFieldPerformanceReports() {
        assertTrue(ReportCatalog.availableToRole(ReportCatalog.byId("sales_daily"), RoleStore.SALES_MANAGER));
        assertTrue(ReportCatalog.availableToRole(ReportCatalog.byId("visitor_scorecard"), RoleStore.SALES_MANAGER));
        assertTrue(ReportCatalog.availableToRole(ReportCatalog.byId("uniq_retention"), RoleStore.SALES_MANAGER));
        assertTrue(ReportCatalog.availableToRole(ReportCatalog.byId("sales_by_route"), RoleStore.SALES_MANAGER));
    }

    @Test public void salesManagerDoesNotGetCompanyFinanceProfitOrSystemReports() {
        assertFalse(ReportCatalog.availableToRole(ReportCatalog.byId("profit_daily"), RoleStore.SALES_MANAGER));
        assertFalse(ReportCatalog.availableToRole(ReportCatalog.byId("invoice_profit"), RoleStore.SALES_MANAGER));
        assertFalse(ReportCatalog.availableToRole(ReportCatalog.byId("sf_entries"), RoleStore.SALES_MANAGER));
        assertFalse(ReportCatalog.availableToRole(ReportCatalog.byId("buy_daily"), RoleStore.SALES_MANAGER));
        assertFalse(ReportCatalog.availableToRole(ReportCatalog.byId("users"), RoleStore.SALES_MANAGER));
    }

    @Test public void salesReportsAreGroupedIntoTheirManagerSections() {
        assertEquals("فروش", ReportCatalog.byId("today_top").section);
        assertEquals("فروش", ReportCatalog.byId("golden_hours").section);
        assertEquals("فروش", ReportCatalog.byId("uniq_basket").section);
        assertEquals("مشتریان", ReportCatalog.byId("fleeing_customers").section);
        assertEquals("مشتریان", ReportCatalog.byId("uniq_retention").section);
        assertEquals("مشتریان", ReportCatalog.byId("uniq_declining").section);
        assertEquals("تحلیل ویزیتور و مسیر", ReportCatalog.byId("visitor_scorecard").section);
    }

    @Test public void companyManagerRetainsTheEntireReportCatalog() {
        for (ReportCatalog.Spec report : ReportCatalog.ALL) {
            assertNotNull(report);
            assertTrue(report.id, ReportCatalog.availableToRole(report, RoleStore.ADMIN));
        }
    }
}
