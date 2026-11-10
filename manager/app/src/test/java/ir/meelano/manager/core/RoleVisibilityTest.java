package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The unified one-app promise: the manager edition reaches every section of the
 * command room, and every other role reaches exactly its own items - the same
 * policy the toolbar, the dashboard chips and «اتاق فرمان» filter their items with.
 */
public class RoleVisibilityTest {

    @Test
    public void managerReachesEveryCommandRoomSection() {
        for (CommandRoom.Group g : CommandRoom.groups()) {
            for (CommandRoom.Entry e : g.entries) {
                assertTrue(e.id, RoleStore.allowedForRole(RoleStore.ADMIN, e.id));
            }
        }
    }

    @Test
    public void visitorOnlyReachesItsOwnWorkspace() {
        assertTrue(RoleStore.allowedForRole(RoleStore.VISITOR, "visitor_work"));
        assertTrue(RoleStore.allowedForRole(RoleStore.VISITOR, "more"));
        for (String id : new String[]{"home", "sales", "buy", "settings", "search",
                "users", "cheques", "reports", "products", "customers"}) {
            assertFalse(id, RoleStore.allowedForRole(RoleStore.VISITOR, id));
        }
    }

    @Test
    public void roleTabsStayInsideTheirOwnAllowedList() {
        for (String role : RoleStore.ALL) {
            for (String tab : RoleStore.tabsFor(role)) {
                assertTrue(role + "/" + tab, RoleStore.allowedForRole(role, tab));
            }
        }
    }

    @Test
    public void toolbarToolsAreRoleFiltered() {
        // «جستجو» and «تنظیمات» are the two nav tools of the shared toolbar.
        assertTrue(RoleStore.allowedForRole(RoleStore.ADMIN, "search"));
        assertTrue(RoleStore.allowedForRole(RoleStore.ADMIN, "settings"));
        assertFalse(RoleStore.allowedForRole(RoleStore.SALES_MANAGER, "settings"));
        assertFalse(RoleStore.allowedForRole(RoleStore.SELLER, "settings"));
        assertFalse(RoleStore.allowedForRole(RoleStore.ACCOUNTANT, "settings"));
        assertTrue(RoleStore.allowedForRole(RoleStore.WAREHOUSE, "settings"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR, "search"));
        assertFalse(RoleStore.allowedForRole(RoleStore.VISITOR, "settings"));
    }

    @Test
    public void mapRoleReadsAtiranRoleNames() {
        assertTrue("manager", RoleStore.ADMIN.equals(AtiranAuth.mapRole(0, "مدیر")));
        assertTrue("sales manager", RoleStore.SALES_MANAGER.equals(AtiranAuth.mapRole(0, "مدیر فروش")));
        assertTrue("visitor", RoleStore.VISITOR.equals(AtiranAuth.mapRole(0, "ویزیتور")));
        assertTrue("accountant", RoleStore.ACCOUNTANT.equals(AtiranAuth.mapRole(0, "حسابدار")));
        assertTrue("warehouse", RoleStore.WAREHOUSE.equals(AtiranAuth.mapRole(0, "انباردار")));
    }
}
