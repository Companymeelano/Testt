package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.sections.SectionCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The full Manager menu set is filtered by the signed-in Atiran role. */
class WindowsRoleMenusTest {

    @Test void adminSeesEverySection() {
        assertEquals(WindowsRoleMenus.allIds(), WindowsRoleMenus.idsFor("admin"));
        assertTrue(WindowsRoleMenus.canSubmitInvoice("admin"));
        assertTrue(WindowsRoleMenus.allows("admin", WindowsRoleMenus.USERS));
        assertTrue(WindowsRoleMenus.allows("admin", WindowsRoleMenus.MOADIAN));
    }

    @Test void accountantCannotRegisterInvoices() {
        List<String> menus = WindowsRoleMenus.idsFor("accountant");
        assertTrue(menus.contains(WindowsRoleMenus.CASH));
        assertTrue(menus.contains(WindowsRoleMenus.CHEQUES));
        assertFalse(menus.contains(WindowsRoleMenus.INVOICE));
        assertFalse(WindowsRoleMenus.canSubmitInvoice("accountant"));
    }

    @Test void warehouseHasStockButNoCashOrUsers() {
        List<String> menus = WindowsRoleMenus.idsFor("warehouse");
        assertTrue(menus.contains(WindowsRoleMenus.STOCK_LEDGER));
        assertFalse(menus.contains(WindowsRoleMenus.CASH));
        assertFalse(menus.contains(WindowsRoleMenus.USERS));
        assertFalse(menus.contains(WindowsRoleMenus.INVOICE));
    }

    @Test void salesManagerKeepsSalesAndPersonnelWithoutUserAdministration() {
        List<String> menus = WindowsRoleMenus.idsFor("sales_manager");
        assertTrue(menus.contains(WindowsRoleMenus.INVOICE));
        assertTrue(menus.contains(WindowsRoleMenus.PERSONNEL));
        assertTrue(menus.contains(WindowsRoleMenus.PROFIT));
        assertFalse(menus.contains(WindowsRoleMenus.USERS));
    }

    @Test void visitorAndDistributorScopesDiffer() {
        assertTrue(WindowsRoleMenus.canSubmitInvoice("visitor"));
        assertFalse(WindowsRoleMenus.canSubmitInvoice("distributor"));
        assertFalse(WindowsRoleMenus.idsFor("distributor").contains(WindowsRoleMenus.CASH));
    }

    @Test void unknownRoleFallsBackToSellerAndConnectionIsAlwaysAvailable() {
        List<String> menus = WindowsRoleMenus.idsFor("not-a-role");
        assertEquals(WindowsRoleMenus.idsFor("seller"), menus);
        assertTrue(menus.contains(WindowsRoleMenus.CONNECTION));
        for (String role : List.of("admin", "accountant", "warehouse", "visitor",
                "distributor", "moadian", "sales_manager", "seller", "")) {
            assertTrue(WindowsRoleMenus.idsFor(role).contains(WindowsRoleMenus.CONNECTION));
        }
    }

    @Test void sectionShortcutsUseCtrlShiftSoPlainDigitsStayOnTheInvoice() {
        assertEquals(WindowsRoleMenus.STOCK_LEDGER, WindowsRoleMenus.sectionForDigit(4));
        assertEquals(WindowsRoleMenus.CASH, WindowsRoleMenus.sectionForDigit(5));
        assertEquals(WindowsRoleMenus.CHEQUES, WindowsRoleMenus.sectionForDigit(6));
        assertEquals(WindowsRoleMenus.REPORTS, WindowsRoleMenus.sectionForDigit(7));
        assertEquals(WindowsRoleMenus.PROFIT, WindowsRoleMenus.sectionForDigit(8));
        assertEquals(WindowsRoleMenus.PRODUCTION, WindowsRoleMenus.sectionForDigit(9));
        assertEquals(WindowsRoleMenus.PERSONNEL, WindowsRoleMenus.sectionForDigit(0));
        // Ctrl+1..3 remain customer search, product search and the invoice line table.
        assertEquals("", WindowsRoleMenus.sectionForDigit(1));
        assertEquals("", WindowsRoleMenus.sectionForDigit(2));
        assertEquals("", WindowsRoleMenus.sectionForDigit(3));
        assertEquals("", WindowsRoleMenus.sectionForDigit(-1));
        for (int digit : new int[]{4, 5, 6, 7, 8, 9, 0}) {
            assertTrue(SectionCatalog.isDesktopSection(WindowsRoleMenus.sectionForDigit(digit)),
                    "Ctrl+Shift+" + digit + " does not open a real report");
        }
    }

    @Test void sectionMenuItemsAdvertiseTheirShortcutAndNothingElseDoes() {
        for (WindowsRoleMenus.Item item : WindowsRoleMenus.itemsFor("admin")) {
            if (SectionCatalog.isDesktopSection(item.id)) {
                assertTrue(item.shortcut.contains("Ctrl+Shift+"),
                        item.id + " should advertise a Ctrl+Shift shortcut: " + item.shortcut);
            } else {
                assertFalse(item.shortcut.contains("Ctrl+Shift+"),
                        item.id + " must not claim a section shortcut: " + item.shortcut);
            }
        }
        // Users and access stays an admin-only menu.
        assertTrue(WindowsRoleMenus.itemsFor("admin").stream()
                .anyMatch(item -> item.id.equals(WindowsRoleMenus.USERS)));
        assertTrue(WindowsRoleMenus.itemsFor("accountant").stream()
                .noneMatch(item -> item.id.equals(WindowsRoleMenus.USERS)));
    }

    @Test void menuItemsCarryLabelsForEveryVisibleId() {
        for (WindowsRoleMenus.Item item : WindowsRoleMenus.itemsFor("admin")) {
            assertFalse(item.label.isBlank());
            assertEquals(item.label, WindowsRoleMenus.labelOf(item.id));
        }
        assertEquals(WindowsRoleMenus.allIds().size(), WindowsRoleMenus.itemsFor("admin").size());
    }
}
