package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Atiran role names/accounts map to the same desktop scopes the Android Manager app uses. */
class AtiranRolesTest {

    @Test void persianRoleNamesMapToApplicationRoles() {
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.mapRoleName(0, "مدیرعامل"));
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.mapRoleName(0, "مدیر کل"));
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.mapRoleName(0, "Administrator"));
        assertEquals(AtiranRoles.SALES_MANAGER, AtiranRoles.mapRoleName(0, "مدیر فروش"));
        assertEquals(AtiranRoles.SALES_MANAGER, AtiranRoles.mapRoleName(0, "سرپرست فروش"));
        assertEquals(AtiranRoles.ACCOUNTANT, AtiranRoles.mapRoleName(0, "حسابدار"));
        assertEquals(AtiranRoles.ACCOUNTANT, AtiranRoles.mapRoleName(0, "واحد مالی"));
        assertEquals(AtiranRoles.WAREHOUSE, AtiranRoles.mapRoleName(0, "انباردار"));
        assertEquals(AtiranRoles.VISITOR, AtiranRoles.mapRoleName(0, "ویزیتور"));
        assertEquals(AtiranRoles.DISTRIBUTOR, AtiranRoles.mapRoleName(0, "نماینده پخش"));
        assertEquals(AtiranRoles.MOADIAN, AtiranRoles.mapRoleName(0, "کاربر مودیان"));
        assertEquals(AtiranRoles.SELLER, AtiranRoles.mapRoleName(0, "فروشنده"));
    }

    @Test void salesManagerIsNotPromotedToCompanyWideAdmin() {
        assertEquals(AtiranRoles.SALES_MANAGER, AtiranRoles.mapRoleName(0, "مدیر فروش"));
        assertNot(AtiranRoles.ADMIN, AtiranRoles.mapRoleName(0, "مدیر فروش"));
    }

    private static void assertNot(String unexpected, String actual) {
        assertFalse(unexpected.equals(actual), "role should not be " + unexpected);
    }

    @Test void informativeRoleNameWinsOverTheAccountName() {
        assertEquals(AtiranRoles.ACCOUNTANT, AtiranRoles.resolve(0, "حسابدار", "admin"));
        assertEquals(AtiranRoles.WAREHOUSE, AtiranRoles.resolve(0, "انبار", "modir"));
    }

    @Test void canonicalManagerAccountsAreRecognizedOnlyAsWholeNames() {
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.managerRoleForUserName("admin"));
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.managerRoleForUserName("Modir_Amel"));
        assertEquals(AtiranRoles.ADMIN, AtiranRoles.managerRoleForUserName("admin2"));
        assertEquals(AtiranRoles.UNKNOWN, AtiranRoles.managerRoleForUserName("modirnejad"));
        assertEquals(AtiranRoles.UNKNOWN, AtiranRoles.managerRoleForUserName("ali_modir"));
        assertEquals(AtiranRoles.UNKNOWN, AtiranRoles.managerRoleForUserName(""));
        assertEquals(AtiranRoles.UNKNOWN, AtiranRoles.managerRoleForUserName(null));
    }

    @Test void unrecognizedRolesFallBackToSeller() {
        assertEquals(AtiranRoles.SELLER, AtiranRoles.resolve(0, "", "some.user"));
        assertEquals(AtiranRoles.SELLER, AtiranRoles.resolve(0, "نقش نامشخص", "some.user"));
        assertTrue(AtiranRoles.isKnown(AtiranRoles.SELLER));
        assertFalse(AtiranRoles.isKnown(AtiranRoles.UNKNOWN));
        assertFalse(AtiranRoles.isKnown("anything-else"));
    }
}
