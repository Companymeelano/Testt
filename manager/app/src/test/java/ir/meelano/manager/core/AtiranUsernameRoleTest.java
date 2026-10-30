package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The login role evidence chain: an informative Atiran role name always wins;
 * when the role name is missing or unknown, canonical manager user names
 * (Modir, Admin, …) decide; everything else falls back to the documented
 * default (SELLER). Whole-name matching only — personal names such as
 * «modirnejad» must never be promoted.
 */
public class AtiranUsernameRoleTest {

    @Test
    public void canonicalManagerUserNamesMapToAdmin() {
        assertEquals("Modir", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("Modir"));
        assertEquals("modir", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("modir"));
        assertEquals("Admin", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("Admin"));
        assertEquals("ADMIN", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("ADMIN"));
        assertEquals("admin2", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("admin2"));
        assertEquals("modir_amel", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("modir_amel"));
        assertEquals("administrator", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("administrator"));
        assertEquals("spaces", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("  Modir  "));
        assertEquals("persian", RoleStore.ADMIN, AtiranAuth.managerRoleForUserName("مدیر"));
    }

    @Test
    public void personalNamesNeverMatchTheManagerRule() {
        assertEquals("", AtiranAuth.managerRoleForUserName("modirnejad"));
        assertEquals("", AtiranAuth.managerRoleForUserName("modir_nejad"));
        assertEquals("", AtiranAuth.managerRoleForUserName("ali_modir"));
        assertEquals("", AtiranAuth.managerRoleForUserName("admin_helper"));
        assertEquals("", AtiranAuth.managerRoleForUserName("visitor-admin"));
        assertEquals("", AtiranAuth.managerRoleForUserName("reza"));
        assertEquals("", AtiranAuth.managerRoleForUserName(""));
        assertEquals("", AtiranAuth.managerRoleForUserName(null));
    }

    @Test
    public void informativeRoleNamesAlwaysWin() {
        // Even for a canonical manager user name, an explicit Atiran role name decides.
        assertEquals(RoleStore.VISITOR,
                AtiranAuth.resolveAppRole(0, "ویزیتور", "Modir"));
        assertEquals(RoleStore.ACCOUNTANT,
                AtiranAuth.resolveAppRole(0, "حسابدار", "Admin"));
        assertEquals(RoleStore.SALES_MANAGER,
                AtiranAuth.resolveAppRole(0, "مدیر فروش", "admin"));
        assertEquals(RoleStore.ADMIN,
                AtiranAuth.resolveAppRole(0, "مدیر مجموعه", "reza"));
    }

    @Test
    public void uninformativeRoleNamesFallThroughToTheUserNameEvidence() {
        // Missing / unknown role names (the state that used to hard-fail login).
        assertEquals(RoleStore.ADMIN, AtiranAuth.resolveAppRole(0, "", "Modir"));
        assertEquals(RoleStore.ADMIN, AtiranAuth.resolveAppRole(0, null, "Admin"));
        assertEquals(RoleStore.ADMIN, AtiranAuth.resolveAppRole(0, "کاربر ارشد", "admin"));
        // Unknown role + ordinary user name → documented default, never a dead end.
        assertEquals(RoleStore.SELLER, AtiranAuth.resolveAppRole(0, "", "reza"));
        assertEquals(RoleStore.SELLER, AtiranAuth.resolveAppRole(0, "کاربر", "mojtaba"));
    }

    @Test
    public void visitorPathKeepsEveryOrdinaryVisitorUntouched() {
        // The visitor workspace is scoped to the sys_vis identity; only the
        // canonical manager panels change edition.
        assertEquals("", AtiranAuth.managerRoleForUserName("saeed"));
        assertEquals("", AtiranAuth.managerRoleForUserName("ویزیتور نمونه"));
        assertFalse("manager rule is never a substring match",
                !AtiranAuth.managerRoleForUserName("modirnejad").isEmpty());
        assertTrue("pure visitor fallback stays VISITOR",
                AtiranAuth.managerRoleForUserName("saeed").isEmpty());
    }
}
