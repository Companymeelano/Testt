package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionCsv;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRequest;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks of the preview path an operator sees without a database: every desktop Manager
 * report renders with the catalog's columns and chips, access follows the Atiran role, and the CSV
 * export stays safe for Excel.
 */
class DemoSectionReportTest {

    private final DemoAtiranGateway gateway = new DemoAtiranGateway();

    @Test void everyDesktopReportRendersInPreviewMode() throws Exception {
        assertTrue(gateway.supportsSections());
        int rendered = 0;
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (SectionVariant variant : SectionCatalog.variantsFor(sectionId)) {
                VariantSpec spec = SectionCatalog.spec(sectionId, variant.id());
                SectionReport report = gateway.loadSection(new SectionRequest(sectionId, variant.id(),
                        "admin", SectionFilter.defaults()));
                String where = sectionId + "/" + variant.id();
                assertTrue(report.demo(), where + " must be marked as preview data");
                assertEquals(variant.id(), report.variantId(), where);
                assertEquals(SectionCatalog.titleOf(sectionId), report.title(), where);
                assertEquals(spec.columns(), report.columns(), where);
                assertFalse(report.rows().isEmpty(), where + " rendered no preview rows");
                assertEquals(spec.kpis().size(), report.kpis().size(), where);
                assertTrue(report.note().contains("دادهٔ نمونه"), where + ": " + report.note());
                assertEquals(report.rows().size(), report.totalRows(), where + " spans more than one page");
                assertEquals(1, report.pageCount(), where);
                for (SectionRow row : report.rows()) {
                    assertEquals(spec.columns().size(), row.values().size(), where);
                    for (int index = 0; index < spec.columns().size(); index++) {
                        SectionValue cell = row.at(index);
                        assertFalse(cell.text().isBlank(), where + " column " + index + " is blank");
                        if (spec.columns().get(index).numeric()) {
                            assertTrue(cell.number() != null, where + " column " + index + " has no amount");
                        }
                    }
                }
                boolean informative = false;
                for (SectionKpi kpi : report.kpis()) {
                    assertFalse(kpi.value().isBlank(), where + " KPI «" + kpi.label() + "» is blank");
                    if (!kpi.value().equals("—")) informative = true;
                }
                assertTrue(informative, where + " shows no usable KPI");
                rendered++;
            }
        }
        assertEquals(33, rendered, "every declared report must render in preview mode");
    }

    @Test void derivedCellsAreFilledInThePreviewToo() throws Exception {
        SectionReport cash = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CASH, "daily",
                "admin", SectionFilter.defaults()));
        int net = cash.indexOf("net");
        int receipts = cash.indexOf("receipts");
        int payments = cash.indexOf("payments");
        for (SectionRow row : cash.rows()) {
            assertEquals(row.at(receipts).amount().subtract(row.at(payments).amount()), row.at(net).amount());
        }

        SectionReport users = gateway.loadSection(new SectionRequest(WindowsRoleMenus.USERS, "list",
                "admin", SectionFilter.defaults()));
        int desktopRole = users.indexOf("desktop_role");
        int menus = users.indexOf("menus");
        for (SectionRow row : users.rows()) {
            assertFalse(row.at(desktopRole).key().isBlank(), "the desktop role is not derived");
            assertTrue(row.at(menus).amount().compareTo(BigDecimal.ZERO) > 0, "no menu count is derived");
            assertEquals(WindowsRoleMenus.idsFor(row.at(desktopRole).key()).size(),
                    row.at(menus).amount().intValue());
        }

        SectionReport cheques = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CHEQUES, "incoming",
                "admin", SectionFilter.defaults()));
        int dueState = cheques.indexOf("due_state");
        assertTrue(cheques.rows().stream().anyMatch(row -> !row.at(dueState).text().equals("نامشخص")),
                "no cheque due state is derived");
    }

    @Test void previewAccessFollowsTheAtiranRole() {
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (String role : List.of("admin", "accountant", "warehouse", "visitor",
                    "distributor", "moadian", "sales_manager", "seller")) {
                SectionRequest request = new SectionRequest(sectionId, "", role, SectionFilter.defaults());
                if (WindowsRoleMenus.allows(role, sectionId)) {
                    assertTrue(canLoad(request), role + " should read " + sectionId);
                } else {
                    IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                            () -> gateway.loadSection(request));
                    assertTrue(denied.getMessage().contains("فعال نیست"), denied.getMessage());
                }
            }
        }
        assertFalse(WindowsRoleMenus.allows("accountant", WindowsRoleMenus.USERS));
        assertFalse(WindowsRoleMenus.allows("warehouse", WindowsRoleMenus.CASH));
    }

    private boolean canLoad(SectionRequest request) {
        try {
            gateway.loadSection(request);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Test void sectionsWithoutADesktopImplementationAreRefused() {
        for (String sectionId : List.of(WindowsRoleMenus.MOADIAN, WindowsRoleMenus.INVOICE,
                WindowsRoleMenus.HANDOFF, "not-a-section")) {
            SectionRequest request = new SectionRequest(sectionId, "", "admin", SectionFilter.defaults());
            IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                    () -> gateway.loadSection(request));
            assertFalse(denied.getMessage().isBlank());
        }
        assertThrows(IllegalArgumentException.class, () -> gateway.loadSection(null));
    }

    @Test void anEmptyReportIdFallsBackButAnUnknownOneIsRefused() throws Exception {
        SectionReport report = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CASH, "",
                "admin", SectionFilter.defaults()));
        assertEquals(SectionCatalog.defaultVariant(WindowsRoleMenus.CASH), report.variantId());
        // A stale saved report id fails loudly instead of silently rendering another report.
        IllegalArgumentException denied = assertThrows(IllegalArgumentException.class,
                () -> gateway.loadSection(new SectionRequest(WindowsRoleMenus.CASH, "not-a-report",
                        "admin", SectionFilter.defaults())));
        assertTrue(denied.getMessage().contains("وجود ندارد"), denied.getMessage());
    }

    @Test void theTypedSearchNarrowsThePreviewRows() throws Exception {
        SectionRequest open = new SectionRequest(WindowsRoleMenus.CASH, "receipts", "admin",
                SectionFilter.defaults());
        SectionRequest scoped = new SectionRequest(WindowsRoleMenus.CASH, "receipts", "admin",
                new SectionFilter("بهار", "", "", "", null, 0, 0, 200));
        SectionReport all = gateway.loadSection(open);
        SectionReport filtered = gateway.loadSection(scoped);
        assertTrue(filtered.totalRows() < all.totalRows(), "the preview search filtered nothing");
        assertTrue(filtered.totalRows() > 0);
        for (SectionRow row : filtered.rows()) {
            boolean hit = false;
            for (SectionValue cell : row.values()) if (cell.text().contains("بهار")) hit = true;
            assertTrue(hit, "a row without the search term survived: " + row.values());
        }
        // A search that matches nothing still renders a valid, empty report.
        SectionReport none = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CASH, "receipts",
                "admin", new SectionFilter("zzz-not-in-preview", "", "", "", null, 0, 0, 200)));
        assertEquals(0, none.totalRows());
        assertTrue(none.rows().isEmpty());
    }

    @Test void anOutOfRangePageShowsTheLastPageInsteadOfAnEmptyTable() throws Exception {
        SectionReport report = gateway.loadSection(new SectionRequest(WindowsRoleMenus.REPORTS, "top_customers",
                "admin", new SectionFilter("", "", "", "", null, 0, 9, 20)));
        assertTrue(report.rows().size() > 0);
        assertEquals(0, report.page());
        assertEquals(1, report.pageCount());
    }

    @Test void csvExportIsUtf8BomQuotedAndFormulaSafe() throws Exception {
        SectionReport report = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CHEQUES, "incoming",
                "admin", SectionFilter.defaults()));
        byte[] bytes = SectionCsv.exportBytes(report);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("چک‌های دریافتی و پرداختی"));
        for (SectionColumn column : report.columns()) {
            assertTrue(csv.contains(column.label()), "column " + column.label() + " is missing from the export");
        }
        for (String line : csv.split("\r\n")) {
            assertFalse(line.startsWith("="), "the export can run as a formula: " + line);
            assertFalse(line.startsWith("+") || line.startsWith("@"), "the export can run as a formula: " + line);
        }
        assertTrue(csv.contains("SAYAD-"), "no data row reached the export");
        String name = SectionCsv.fileName(report);
        assertTrue(name.endsWith(".csv"), name);
        assertFalse(name.contains("/") || name.contains("\\") || name.contains(".."), name);
        assertTrue(SectionCsv.summary(report).contains(report.title()));
        assertTrue(SectionCsv.summary(report).contains("ردیف‌ها"));
    }

    @Test void csvExportKeepsAmountsNumericAndLabelsReadable() throws Exception {
        SectionReport report = gateway.loadSection(new SectionRequest(WindowsRoleMenus.CASH, "daily",
                "admin", SectionFilter.defaults()));
        String csv = SectionCsv.export(report);
        // Money cells are exported as plain numbers, never as Persian display text.
        int moneyColumn = report.indexOf("receipts");
        assertTrue(moneyColumn >= 0);
        BigDecimal first = report.rows().get(0).at(moneyColumn).amount();
        assertTrue(csv.contains(first.toPlainString()), "the exported amount is not numeric: " + first);
        assertTrue(csv.contains("خلاصهٔ روزانهٔ صندوق"));
        assertTrue(csv.contains("پیش‌نمایش محلی"), "the export does not say where the data came from");
    }

    @Test void previewLabelsNameTheirSection() {
        assertTrue(DemoSectionData.previewLabel(WindowsRoleMenus.CASH).contains("صندوق و دریافتی‌ها"));
        assertTrue(DemoSectionData.previewLabel(WindowsRoleMenus.STOCK_LEDGER).contains("انبار"));
        assertTrue(DemoSectionData.previewLabel("").contains("پیش‌نمایش"));
        assertTrue(DemoSectionData.previewLabel(null).contains("پیش‌نمایش"));
    }
}
