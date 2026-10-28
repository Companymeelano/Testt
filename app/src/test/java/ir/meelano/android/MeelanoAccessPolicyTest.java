package ir.meelano.android;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MeelanoAccessPolicyTest {
    @Test public void visitorGetsSalesAndReadOnlyProductAndCustomerTools() {
        Set<String> p = MeelanoAccessPolicy.defaultPermissions("visitor");
        assertTrue(p.contains("cart_submit"));
        assertTrue(p.contains("customer_detail"));
        assertTrue(p.contains("product_detail"));
        assertTrue(p.contains("visitor_receipt"));
        assertTrue(p.contains("assistant"));
        assertFalse(p.contains("warehouse"));
        assertFalse(p.contains("management_access"));
        assertFalse(p.contains("tax_send"));
    }

    @Test public void unifiedHomeUsesRoleWithoutChangingTheSharedEdition() {
        assertTrue("dashboard".equals(MeelanoAccessPolicy.unifiedHomePage("manager", true)));
        assertTrue("dashboard".equals(MeelanoAccessPolicy.unifiedHomePage("admin", true)));
        assertTrue("visitor_dashboard".equals(MeelanoAccessPolicy.unifiedHomePage("visitor", false)));
        assertTrue("warehouse".equals(MeelanoAccessPolicy.unifiedHomePage("warehouse", false)));
    }

    @Test public void warehouseRoleGetsWarehouseToolsButNotSalesOrManagement() {
        Set<String> p = MeelanoAccessPolicy.defaultPermissions("warehouse");
        assertTrue(p.contains("warehouse"));
        assertTrue(p.contains("products"));
        assertTrue(p.contains("showcase"));
        assertFalse(p.contains("cart_submit"));
        assertFalse(p.contains("management_access"));
    }

    @Test public void nonVisitorRolesDoNotInheritVisitorSalesPermissions() {
        assertFalse(MeelanoAccessPolicy.defaultPermissions("accountant").contains("cart_submit"));
        assertFalse(MeelanoAccessPolicy.defaultPermissions("warehouse").contains("cart_submit"));
        assertFalse(MeelanoAccessPolicy.defaultPermissions("employee").contains("visitor_receipt"));
        assertFalse(MeelanoAccessPolicy.allows("accountant", MeelanoAccessPolicy.defaultPermissions("accountant"), "cart_submit"));
        assertFalse(MeelanoAccessPolicy.allows("warehouse", MeelanoAccessPolicy.defaultPermissions("warehouse"), "visitor_receipt"));
    }

    @Test public void explicitGrantsAreScopedAndAdminRolesRemainPrivileged() {
        assertTrue(MeelanoAccessPolicy.allows("visitor", Set.of("visitor_receipt"), "visitor_receipt"));
        assertFalse(MeelanoAccessPolicy.allows("visitor", Set.of("visitor_receipt"), "management_access"));
        assertTrue(MeelanoAccessPolicy.allows("admin", Set.of(), "management_access"));
        assertTrue(MeelanoAccessPolicy.allows("manager", Set.of("warehouse"), "warehouse"));
        assertTrue(MeelanoAccessPolicy.allows("manager", Set.of("production"), "production"));
        assertFalse(MeelanoAccessPolicy.allows("manager", Set.of("warehouse"), "management_access"));
        assertFalse(MeelanoAccessPolicy.allows("manager", Set.of(), "warehouse"));
    }

    @Test public void unknownAtiranIdentityDoesNotBecomeAdminByName() {
        assertTrue("visitor".equals(MeelanoAccessPolicy.fallbackRole(true)));
        assertTrue("user".equals(MeelanoAccessPolicy.fallbackRole(false)));
        assertFalse(MeelanoAccessPolicy.allows(MeelanoAccessPolicy.fallbackRole(false), Set.of(), "management_access"));
    }

    @Test public void privilegedRolesRequireWholeRoleNameNotSubstring() {
        assertTrue("admin".equals(MeelanoAccessPolicy.canonicalRole("System Administrator")));
        assertTrue("manager".equals(MeelanoAccessPolicy.canonicalRole("مدیر فروش")));
        assertTrue("user".equals(MeelanoAccessPolicy.canonicalRole("non-admin")));
        assertTrue("user".equals(MeelanoAccessPolicy.canonicalRole("assistant manager")));
        assertFalse(MeelanoAccessPolicy.allows("assistant manager", Set.of(), "cart_submit"));
    }
}
