package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog.Kpi;
import ir.meelano.windowsinvoice.sections.SectionCatalog.SectionSpec;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog is the single source of truth for what a desktop section shows, so every internal
 * reference (derived columns, KPI columns, sort and date keys) has to resolve inside its own report.
 */
class SectionCatalogTest {

    @Test void everyDesktopManagerSectionIsDeclared() {
        assertEquals(List.of(WindowsRoleMenus.STOCK_LEDGER, WindowsRoleMenus.CASH, WindowsRoleMenus.CHEQUES,
                WindowsRoleMenus.REPORTS, WindowsRoleMenus.PROFIT, WindowsRoleMenus.PRODUCTION,
                WindowsRoleMenus.PERSONNEL, WindowsRoleMenus.USERS), SectionCatalog.DESKTOP_SECTIONS);
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            assertTrue(SectionCatalog.isDesktopSection(sectionId));
            assertFalse(SectionCatalog.variantsFor(sectionId).isEmpty(), sectionId + " has no report");
            assertFalse(SectionCatalog.titleOf(sectionId).isBlank());
            assertFalse(SectionCatalog.captionOf(sectionId).isBlank());
        }
    }

    @Test void invoiceAndMoadianAreNotSections() {
        assertFalse(SectionCatalog.isDesktopSection(WindowsRoleMenus.INVOICE));
        assertFalse(SectionCatalog.isDesktopSection(WindowsRoleMenus.MOADIAN));
        assertFalse(SectionCatalog.isDesktopSection(WindowsRoleMenus.CONNECTION));
        assertFalse(SectionCatalog.isDesktopSection(""));
        assertFalse(SectionCatalog.isDesktopSection(null));
    }

    @Test void variantIdsAreUniqueInsideASection() {
        for (String sectionId : SectionCatalog.sectionIds()) {
            Set<String> ids = new HashSet<>();
            for (SectionVariant variant : SectionCatalog.variantsFor(sectionId)) {
                assertTrue(ids.add(variant.id()), sectionId + "/" + variant.id() + " is declared twice");
                assertFalse(variant.label().isBlank());
            }
            assertEquals(SectionCatalog.variantsFor(sectionId).size(), ids.size());
        }
    }

    @Test void everyReportColumnReferenceResolves() {
        for (String sectionId : SectionCatalog.sectionIds()) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                List<String> keys = new ArrayList<>();
                for (SectionColumn column : spec.columns()) {
                    assertFalse(column.key().isBlank(), spec.id() + " has a blank column key");
                    assertFalse(column.label().isBlank(), spec.id() + "/" + column.key() + " has no label");
                    assertTrue(column.width() > 0, spec.id() + "/" + column.key() + " has no width");
                    assertTrue(keys.add(column.key()), spec.id() + " repeats column " + column.key());
                }
                assertTrue(keys.size() >= 3, spec.id() + " has too few columns to be a report");
                if (!spec.dateKey().isEmpty()) assertTrue(keys.contains(spec.dateKey()),
                        spec.id() + " sorts/filters on a missing date column " + spec.dateKey());
                if (!spec.sortKey().isEmpty()) assertTrue(keys.contains(spec.sortKey()),
                        spec.id() + " sorts on a missing column " + spec.sortKey());
                for (SectionCatalog.Derived derived : spec.derived()) {
                    assertTrue(keys.contains(derived.columnKey()),
                            spec.id() + " derives into a missing column " + derived.columnKey());
                    assertFalse(derived.sources().isEmpty(), spec.id() + "/" + derived.columnKey() + " has no source");
                }
                for (Kpi kpi : spec.kpis()) {
                    assertFalse(kpi.label().isBlank());
                    if (!kpi.columnKey().isEmpty()) assertTrue(keys.contains(kpi.columnKey()),
                            spec.id() + " KPI «" + kpi.label() + "» reads a missing column " + kpi.columnKey());
                    if (!kpi.secondKey().isEmpty()) assertTrue(keys.contains(kpi.secondKey()),
                            spec.id() + " KPI «" + kpi.label() + "» reads a missing column " + kpi.secondKey());
                }
                assertTrue(spec.maxRows() >= 20 && spec.maxRows() <= 20000, spec.id() + " has an unsafe row cap");
            }
        }
    }

    @Test void sectionTitlesMatchTheMenuCatalog() {
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            assertEquals(WindowsRoleMenus.labelOf(sectionId), SectionCatalog.titleOf(sectionId));
            SectionSpec spec = SectionCatalog.section(sectionId);
            assertNotNull(spec);
            assertEquals(SectionCatalog.defaultVariant(sectionId), spec.defaultVariant());
            assertFalse(spec.caption().isBlank());
        }
    }

    @Test void anEmptyReportIdFallsBackButAnUnknownOneIsRefused() {
        assertEquals(SectionCatalog.defaultVariant(WindowsRoleMenus.CASH),
                SectionCatalog.spec(WindowsRoleMenus.CASH, "").id());
        assertEquals(SectionCatalog.defaultVariant(WindowsRoleMenus.CASH),
                SectionCatalog.spec(WindowsRoleMenus.CASH, null).id());
        assertEquals("daily", SectionCatalog.spec(WindowsRoleMenus.CASH, "daily").id());
        // A stale saved report id must fail loudly instead of silently showing another report.
        assertThrows(IllegalArgumentException.class,
                () -> SectionCatalog.spec(WindowsRoleMenus.CASH, "not-a-report"));
        assertThrows(IllegalArgumentException.class, () -> SectionCatalog.spec("not-a-section", "daily"));
        assertEquals("", SectionCatalog.titleOf("not-a-section"));
    }

    @Test void reportsVisibleToARoleFollowItsMenus() {
        assertTrue(SectionCatalog.sectionsFor("admin").containsAll(SectionCatalog.DESKTOP_SECTIONS));
        assertFalse(SectionCatalog.sectionsFor("accountant").contains(WindowsRoleMenus.USERS));
        assertFalse(SectionCatalog.sectionsFor("warehouse").contains(WindowsRoleMenus.CASH));
        assertTrue(SectionCatalog.sectionsFor("warehouse").contains(WindowsRoleMenus.STOCK_LEDGER));
        assertTrue(SectionCatalog.sectionsFor("not-a-role").contains(WindowsRoleMenus.CASH));
        for (String role : List.of("admin", "accountant", "warehouse", "visitor",
                "distributor", "moadian", "sales_manager", "seller", "")) {
            for (String sectionId : SectionCatalog.sectionsFor(role)) {
                assertTrue(WindowsRoleMenus.allows(role, sectionId),
                        role + " would see section " + sectionId + " it may not open");
            }
        }
    }

    @Test void desktopRoleIsDerivedFromTheAtiranRoleNameAndUser() {
        assertEquals("admin", SectionCatalog.desktopRole("مدیر", "u1"));
        assertEquals("sales_manager", SectionCatalog.desktopRole("مدیر فروش", "u1"));
        assertEquals("accountant", SectionCatalog.desktopRole("حسابدار", "u2"));
        assertEquals("warehouse", SectionCatalog.desktopRole("انباردار", "u3"));
        assertEquals("visitor", SectionCatalog.desktopRole("ویزیتور", "u4"));
        // An unrecognized role name still resolves through the canonical admin account name…
        assertEquals("admin", SectionCatalog.desktopRole("کارمند", "admin"));
        // …and otherwise falls back to the documented seller scope, never to an empty role.
        assertEquals("seller", SectionCatalog.desktopRole("کارمند", "accountant"));
        assertEquals("seller", SectionCatalog.desktopRole("", ""));
        assertEquals("حسابدار", SectionCatalog.desktopRoleLabel("accountant"));
        assertEquals("کاربر", SectionCatalog.desktopRoleLabel("not-a-role"));
        assertTrue(SectionCatalog.menuScope("accountant").contains("صندوق و دریافتی‌ها"));
        assertFalse(SectionCatalog.menuScope("accountant").contains("مدیریت کاربران و دسترسی"));
        assertFalse(SectionCatalog.menuScope("").isBlank());
    }

    @Test void thresholdsAndDayWindowsHaveSafeBounds() {
        VariantSpec lowMargin = SectionCatalog.spec(WindowsRoleMenus.PROFIT, "low_margin");
        assertTrue(lowMargin.supportsThreshold());
        assertEquals(new BigDecimal("10"), SectionCatalog.thresholdOf(lowMargin, null));
        assertEquals(new BigDecimal("25"), SectionCatalog.thresholdOf(lowMargin, new BigDecimal("25")));
        // An out-of-range percent is rejected by the filter itself, never passed on to the report.
        assertThrows(IllegalArgumentException.class, () -> new SectionModels.SectionFilter(
                "", "", "", "", new BigDecimal("400"), 0, 0, 200));
        assertThrows(IllegalArgumentException.class, () -> new SectionModels.SectionFilter(
                "", "", "", "", new BigDecimal("-5"), 0, 0, 200));
        assertEquals(BigDecimal.ZERO,
                SectionCatalog.thresholdOf(SectionCatalog.spec(WindowsRoleMenus.CASH, "daily"), null));
        VariantSpec due = SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "due");
        assertTrue(due.supportsDays());
        assertTrue(due.dueWindow());
        assertEquals(30, SectionCatalog.daysOf(due, 0));
        assertEquals(45, SectionCatalog.daysOf(due, 45));
        assertEquals(30, SectionCatalog.daysOf(due, -5));
        assertEquals(0, SectionCatalog.daysOf(SectionCatalog.spec(WindowsRoleMenus.CASH, "receipts"), 90));
    }

    @Test void reportsThatCannotUseADateWindowSaySo() {
        assertFalse(SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "stock").supportsDateRange());
        assertTrue(SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "kardex").supportsDateRange());
        assertEquals("", SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "stock").dateKey());
        assertEquals("date", SectionCatalog.spec(WindowsRoleMenus.CASH, "daily").dateKey());
    }
}
