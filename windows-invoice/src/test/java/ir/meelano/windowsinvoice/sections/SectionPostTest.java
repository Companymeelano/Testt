package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared post-processing step: derived columns, the date window, sorting, KPI chips, paging and the
 * provenance note. The live gateway and the preview gateway both run through here, so this is where a
 * wrong total or a silently hidden row would come from.
 */
class SectionPostTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 25);
    private static final String TODAY = PersianDate.fromGregorian(ANCHOR);
    private static final VariantSpec DAILY = SectionCatalog.spec(WindowsRoleMenus.CASH, "daily");
    private static final VariantSpec USERS = SectionCatalog.spec(WindowsRoleMenus.USERS, "list");

    @Test void derivedColumnsAreComputedFromTheirOwnRow() {
        List<SectionRow> rows = List.of(
                cashRow(0, "1000000", "400000"),
                cashRow(1, "2500000", "900000"));
        SectionReport report = SectionPost.process(DAILY, SectionFilter.defaults(), rows, false, false, TODAY);

        assertEquals(2, report.rows().size());
        int net = DAILY.columnIndex("net");
        assertEquals(new BigDecimal("600000"), report.rows().get(0).at(net).amount());
        assertEquals(new BigDecimal("1600000"), report.rows().get(1).at(net).amount());
        assertEquals("۶۰۰٬۰۰۰", report.rows().get(0).at(net).text());
        // The gateway never selects a derived column, so the raw cell stays empty until derived.
        assertEquals(6, report.rows().get(0).values().size());
        assertEquals(6, report.columns().size());
    }

    @Test void rowsAreSortedAndFilteredOnTheReportDate() {
        List<SectionRow> rows = new ArrayList<>();
        for (int day = 0; day < 45; day++) rows.add(cashRow(day, "1000", "400"));
        SectionReport report = SectionPost.process(DAILY, SectionFilter.defaults(), rows, false, false, TODAY);

        assertEquals(45, report.totalRows());
        assertEquals(SectionMath.digits(TODAY), report.rows().get(0).textAt(0));
        assertEquals(SectionMath.digits(SectionMath.minusDays(TODAY, 1)), report.rows().get(1).textAt(0));
        assertEquals(SectionMath.digits(SectionMath.minusDays(TODAY, 44)), report.rows().get(44).textAt(0));

        // A typed window replaces the catalog default and drops every row outside it.
        SectionFilter window = new SectionFilter("", "", SectionMath.minusDays(TODAY, 9), TODAY, null, 0, 0, 200);
        SectionReport filtered = SectionPost.process(DAILY, window, rows, false, false, TODAY);
        assertEquals(10, filtered.totalRows());
        assertEquals(SectionMath.digits(SectionMath.minusDays(TODAY, 9)), filtered.rows().get(9).textAt(0));
        assertEquals(1, filtered.pageCount(), "ten rows fit on one 200-row page");
    }

    @Test void aReportWithoutADateKeyKeepsUndatedRows() {
        VariantSpec stock = SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "stock");
        List<SectionRow> rows = List.of(stockRow("7101", "0"), stockRow("7102", "-4"), stockRow("7103", "250"));
        SectionFilter window = new SectionFilter("", "", SectionMath.minusDays(TODAY, 9), TODAY, null, 0, 0, 200);
        SectionReport report = SectionPost.process(stock, window, rows, false, false, TODAY);
        assertEquals(3, report.totalRows(), "a stock report has no date column to filter on");
        int state = stock.columnIndex("state");
        assertEquals("اتمام", report.rows().stream().filter(r -> r.textAt(0).equals("7101"))
                .findFirst().orElseThrow().at(state).text());
        assertEquals("کسری", report.rows().stream().filter(r -> r.textAt(0).equals("7102"))
                .findFirst().orElseThrow().at(state).text());
        assertEquals("موجود", report.rows().stream().filter(r -> r.textAt(0).equals("7103"))
                .findFirst().orElseThrow().at(state).text());
    }

    @Test void pagesAreSlicedAndAnOutOfRangePageShowsTheLastOne() {
        List<SectionRow> rows = new ArrayList<>();
        for (int day = 0; day < 45; day++) rows.add(cashRow(day, "1000", "400"));
        SectionReport first = SectionPost.process(DAILY, page(0, 20), rows, false, false, TODAY);
        assertEquals(20, first.rows().size());
        assertEquals(45, first.totalRows());
        assertEquals(3, first.pageCount());
        assertEquals(0, first.page());

        SectionReport second = SectionPost.process(DAILY, page(1, 20), rows, false, false, TODAY);
        assertEquals(20, second.rows().size());
        assertEquals(1, second.page());
        assertFalse(first.rows().get(0).textAt(0).equals(second.rows().get(0).textAt(0)));

        SectionReport last = SectionPost.process(DAILY, page(2, 20), rows, false, false, TODAY);
        assertEquals(5, last.rows().size());

        // A stale page index (rows shrank after a filter change) must not show an empty table.
        SectionReport beyond = SectionPost.process(DAILY, page(9, 20), rows, false, false, TODAY);
        assertEquals(5, beyond.rows().size());
        assertEquals(2, beyond.page());
    }

    @Test void kpiChipsSummarizeTheFilteredRowsNotTheVisiblePage() {
        List<SectionRow> rows = new ArrayList<>();
        for (int day = 0; day < 45; day++) rows.add(cashRow(day, "1000", "400"));
        SectionReport report = SectionPost.process(DAILY, page(0, 20), rows, false, false, TODAY);
        List<SectionKpi> kpis = report.kpis();
        assertEquals(5, kpis.size());
        assertEquals("جمع دریافتی دوره", kpis.get(0).label());
        assertEquals("۴۵٬۰۰۰", kpis.get(0).value());
        assertEquals("۱۸٬۰۰۰", kpis.get(1).value());
        assertEquals("۲۷٬۰۰۰", kpis.get(2).value());
        assertEquals("۱٬۰۰۰", kpis.get(3).value());
        assertEquals("۴۵", kpis.get(4).value());
    }

    @Test void accessColumnsDeriveInCatalogOrder() {
        SectionReport report = SectionPost.process(USERS, SectionFilter.defaults(),
                List.of(userRow("101", "user1", "حسابدار", "t"),
                        userRow("102", "admin", "کارمند", "t"),
                        userRow("103", "user3", "انباردار", "f")),
                false, false, TODAY);
        int desktopRole = USERS.columnIndex("desktop_role");
        int menus = USERS.columnIndex("menus");
        int active = USERS.columnIndex("active");

        // The list is sorted by name, so rows are addressed by their user code.
        SectionRow accountant = rowWith(report, "101");
        SectionRow byUserName = rowWith(report, "102");
        SectionRow warehouse = rowWith(report, "103");
        assertEquals("حسابدار", accountant.at(desktopRole).text());
        assertEquals("accountant", accountant.at(desktopRole).key());
        assertEquals(WindowsRoleMenus.idsFor("accountant").size(), accountant.at(menus).amount().intValue());
        // An unrecognized role name is still resolved through the canonical admin account name.
        assertEquals("مدیر", byUserName.at(desktopRole).text());
        assertEquals(WindowsRoleMenus.idsFor("admin").size(), byUserName.at(menus).amount().intValue());
        assertEquals("انباردار", warehouse.at(desktopRole).text());
        assertEquals("فعال", accountant.at(active).text());
        assertEquals("غیرفعال", warehouse.at(active).text());
        // The access scope column lists the menus this desktop role actually sees.
        assertEquals(WindowsRoleMenus.idsFor("accountant").size(), accountant.at(menus).amount().intValue());

        List<SectionKpi> kpis = report.kpis();
        assertEquals("۳", kpis.get(0).value());
        assertEquals("۲", kpis.get(1).value());
        assertEquals("۱", kpis.get(2).value());
    }

    @Test void theNoteAlwaysCarriesProvenance() {
        List<SectionRow> rows = List.of(cashRow(0, "1000", "400"));
        SectionReport live = SectionPost.process(DAILY, SectionFilter.defaults(), rows, false, false, TODAY);
        assertTrue(live.note().contains("بازهٔ پیش‌فرض"), live.note());
        assertFalse(live.note().contains("نمونه"), live.note());

        SectionReport demo = SectionPost.process(DAILY, SectionFilter.defaults(), rows, false, true, TODAY);
        assertTrue(demo.note().contains("دادهٔ نمونه"), demo.note());
        assertTrue(demo.demo());

        SectionReport truncated = SectionPost.process(DAILY, SectionFilter.defaults(), rows, true, false, TODAY);
        assertTrue(truncated.note().contains("۴۰۰"), truncated.note());
        assertTrue(truncated.truncated());

        SectionReport empty = SectionPost.process(DAILY, SectionFilter.defaults(), List.of(), false, false, TODAY);
        assertTrue(empty.isEmpty());
        assertTrue(empty.note().contains("بازهٔ پیش‌فرض"), empty.note());

        SectionReport idle = SectionPost.process(SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "dead"),
                SectionFilter.defaults(), List.of(), false, false, TODAY);
        assertTrue(idle.note().contains("مبنای رکود"), idle.note());
        assertTrue(idle.note().contains(SectionMath.digits(
                PersianDate.fromGregorian(ANCHOR.minusDays(180)))), idle.note());
    }

    @Test void anUnreadableStructureIsReportedAsAnEmptyReportWithAReason() {
        SectionReport report = SectionPost.unavailable(DAILY, SectionFilter.defaults(),
                "«قبوض دریافت و پرداخت» در این پایگاه داده پیدا نشد.", false);
        assertTrue(report.isEmpty());
        assertEquals(0, report.totalRows());
        assertEquals(DAILY.columns(), report.columns());
        assertEquals("«قبوض دریافت و پرداخت» در این پایگاه داده پیدا نشد.", report.note());
        assertEquals(List.of(), report.kpis());
        assertEquals(1, report.pageCount());
        assertFalse(report.truncated());
    }

    @Test void shortRowsAndMissingCellsNeverBreakAReport() {
        // A gateway that returns fewer cells than the report declares still renders, with blanks.
        SectionRow shortRow = new SectionRow("x", List.of(
                SectionMath.value(DAILY.columns().get(0), TODAY, null),
                new SectionValue("۳", "3", new BigDecimal("3"))));
        SectionReport report = SectionPost.process(DAILY, SectionFilter.defaults(), List.of(shortRow),
                false, false, TODAY);
        assertEquals(6, report.rows().get(0).values().size());
        assertTrue(report.rows().get(0).at(2).isBlank(), "an unread cell stays blank");
        assertEquals("", report.rows().get(0).textAt(2));
        // The derived net column treats a missing source as zero instead of failing the row.
        assertEquals(0, report.rows().get(0).at(DAILY.columnIndex("net")).amount().signum());
    }

    @Test void reportMetadataDescribesWhatWasRead() {
        List<SectionRow> rows = new ArrayList<>();
        for (int day = 0; day < 5; day++) rows.add(cashRow(day, "1000", "400"));
        SectionReport report = SectionPost.process(DAILY, SectionFilter.defaults(), rows, false, false, TODAY);
        assertEquals(WindowsRoleMenus.CASH, report.sectionId());
        assertEquals("daily", report.variantId());
        assertEquals("صندوق و دریافتی‌ها", report.title());
        assertEquals("خلاصهٔ روزانهٔ صندوق", report.variantLabel());
        assertEquals(5, report.totalRows());
        assertEquals(200, report.pageSize());
        assertEquals(1, report.pageCount());
        assertTrue(report.indexOf("net") >= 0);
        assertEquals(-1, report.indexOf("not-a-column"));
        assertEquals(report, report.withRows(report.rows(), report.totalRows(), report.page()));
        assertEquals("یادداشت", report.withNote("یادداشت").note());
    }

    // ------------------------------------------------------------------ fixtures

    /** Finds a rendered row by the id the gateway assigned (the user code here). */
    private static SectionRow rowWith(SectionReport report, String id) {
        for (SectionRow row : report.rows()) if (row.id().equals(id)) return row;
        throw new AssertionError("row " + id + " is missing from the report");
    }

    private static SectionFilter page(int index, int size) {
        return new SectionFilter("", "", "", "", null, 0, index, size);
    }

    /** One cash day row: date, receipts_count, receipts, payments_count, payments (net is derived). */
    private static SectionRow cashRow(int daysAgo, String receipts, String payments) {
        List<SectionColumn> columns = DAILY.columns();
        List<SectionValue> values = new ArrayList<>();
        values.add(SectionMath.value(columns.get(0), SectionMath.minusDays(TODAY, daysAgo), null));
        values.add(SectionMath.value(columns.get(1), null, new BigDecimal("3")));
        values.add(SectionMath.value(columns.get(2), null, new BigDecimal(receipts)));
        values.add(SectionMath.value(columns.get(3), null, new BigDecimal("1")));
        values.add(SectionMath.value(columns.get(4), null, new BigDecimal(payments)));
        values.add(SectionModels.EMPTY);
        return new SectionRow(SectionMath.minusDays(TODAY, daysAgo), values);
    }

    /** One stock row: code, name, group, unit, packs, loose, total, buy_price, value (state is derived). */
    private static SectionRow stockRow(String code, String onHand) {
        VariantSpec spec = SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "stock");
        List<SectionValue> values = new ArrayList<>();
        values.add(SectionMath.value(spec.columns().get(0), code, null));
        values.add(SectionMath.value(spec.columns().get(1), "کالای نمونهٔ " + code, null));
        values.add(SectionMath.value(spec.columns().get(2), "آجیل", null));
        values.add(SectionMath.value(spec.columns().get(3), "کیلوگرم", null));
        values.add(SectionMath.value(spec.columns().get(4), null, new BigDecimal(onHand)));
        values.add(SectionMath.value(spec.columns().get(5), null, BigDecimal.ZERO));
        values.add(SectionMath.value(spec.columns().get(6), null, new BigDecimal(onHand)));
        values.add(SectionMath.value(spec.columns().get(7), null, new BigDecimal("1000")));
        values.add(SectionMath.value(spec.columns().get(8), null, new BigDecimal(onHand).multiply(new BigDecimal("1000"))));
        values.add(SectionModels.EMPTY);
        return new SectionRow(code, values);
    }

    /** One user row; desktop_role and menus are derived inside SectionPost. */
    private static SectionRow userRow(String code, String name, String atiranRole, String active) {
        List<SectionColumn> columns = USERS.columns();
        List<SectionValue> values = new ArrayList<>();
        values.add(SectionMath.value(columns.get(0), code, null));
        values.add(SectionMath.value(columns.get(1), name, null));
        values.add(SectionMath.value(columns.get(2), atiranRole, null));
        values.add(SectionModels.EMPTY);
        values.add(SectionModels.EMPTY);
        values.add(SectionMath.value(columns.get(5), active, null));
        values.add(SectionMath.value(columns.get(6), "", null));
        values.add(SectionMath.value(columns.get(7), "", null));
        values.add(SectionMath.value(columns.get(8), null, BigDecimal.ZERO));
        return new SectionRow(code, values);
    }
}
