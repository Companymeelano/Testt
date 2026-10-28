package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtiranAuthPhaseTest {
    @Test public void companySalesWarehouseAndVisitorRolesAreLiveInTheSameApp() {
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.ADMIN));
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.SALES_MANAGER));
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.WAREHOUSE));
        assertTrue(AtiranAuth.phaseOpen(null, RoleStore.VISITOR));
    }

    @Test public void atiranSalesManagerMapsToTheNarrowRoleBeforeGenericManager() {
        org.junit.Assert.assertEquals(RoleStore.SALES_MANAGER, AtiranAuth.mapRole(0, "مدیر فروش"));
        org.junit.Assert.assertEquals(RoleStore.ADMIN, AtiranAuth.mapRole(0, "مدیر مجموعه"));
        org.junit.Assert.assertEquals(RoleStore.SELLER, AtiranAuth.mapRole(0, "فروشنده"));
    }

    @Test public void mapsPersianAndArabicLetterVariantsInAtiranRoleNames() {
        org.junit.Assert.assertEquals(RoleStore.ADMIN, AtiranAuth.mapRole(0, "مدير مجموعه"));
        org.junit.Assert.assertEquals(RoleStore.VISITOR, AtiranAuth.mapRole(0, "ويزيتور"));
        org.junit.Assert.assertEquals(RoleStore.SALES_MANAGER, AtiranAuth.mapRole(0, "مدير فروش"));
    }

    @Test public void unfinishedRolesRemainExplicitlyGated() {
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.SELLER));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.ACCOUNTANT));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.DISTRIBUTOR));
        assertFalse(AtiranAuth.phaseOpen(null, RoleStore.MOADIAN));
        assertFalse(AtiranAuth.phaseOpen(null, "unknown"));
    }
}
