package ir.meelano.manager.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BankStatementFileTest {
    @Test public void parsesQuotedSemicolonCsvWithPersianDigitsAndSplitAmounts() throws Exception {
        String csv = "تاریخ;شرح;واریز;برداشت;مانده\n"
                + "۱۴۰۴/۰۸/۱۰;\"واریز بابت، فروش\";۱٬۲۳۴٬۰۰۰;—;۲٬۰۰۰٬۰۰۰\n"
                + "۱۴۰۴/۰۸/۱۰;خرید;—;۲۵۰٬۰۰۰;۱٬۷۵۰٬۰۰۰\n";
        BankStatementFile.Table table = read(csv, "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertTrue(mapping.valid());
        mapping.amountsInToman = false;
        mapping.amountFormat = BankStatementFile.AMOUNT_GROUPING_ONLY;
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "۱۴۰۴/۰۸/۱۰", "۱۴۰۴/۰۸/۱۰");

        assertEquals(2, parsed.transactions.size());
        assertEquals(1_234_000, parsed.totalCreditRials, 0.001);
        assertEquals(250_000, parsed.totalDebitRials, 0.001);
        assertEquals("واریز بابت، فروش", parsed.transactions.get(0).description);
        assertEquals(2, parsed.daily.get("1404/08/10").transactions);
        assertEquals(0, parsed.invalidRows);
        BankStatementFile.Parsed summary = BankStatementFile.aggregateOnly(parsed);
        assertTrue(summary.transactions.isEmpty());
        assertEquals(2, summary.transactionCount);
        assertEquals(1_234_000, summary.daily.get("1404/08/10").creditRials, 0.001);
    }

    @Test public void parsesTsvAndTomanUnitIntoRials() throws Exception {
        String tsv = "Date\tAmount\tType\n"
                + "2025-03-21\t۱۲٬۵۰۰\tواریز\n"
                + "2025-03-21\t۲٬۰۰۰\tبرداشت\n";
        BankStatementFile.Table table = read(tsv, "bank.tsv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertTrue(mapping.valid());
        mapping.amountsInToman = true;
        mapping.amountFormat = BankStatementFile.AMOUNT_GROUPING_ONLY;
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1404/01/01", "1404/01/01");

        assertEquals(125_000, parsed.totalCreditRials, 0.001);
        assertEquals(20_000, parsed.totalDebitRials, 0.001);
        assertEquals("1404/01/01", parsed.minDay);
    }

    @Test public void signedAmountRequiresExplicitManagerConfirmationAndReadsSigns() throws Exception {
        String csv = "date,amount\n2026-03-21,100\n2026-03-21,-25\n2026-03-21,0\n";
        BankStatementFile.Table table = read(csv, "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertFalse(mapping.valid());
        mapping.signedAmountDefinesDirection = true;
        assertTrue(mapping.valid());
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(100, parsed.totalCreditRials, 0.001);
        assertEquals(25, parsed.totalDebitRials, 0.001);
        assertEquals(1, parsed.invalidRows);
        assertTrue(parsed.errorSummary.contains("علامت مبلغ صفر"));
    }

    @Test public void filtersMultiAccountExportsByAnExplicitExactAccountValue() throws Exception {
        String csv = "date,account,amount,direction\n"
                + "2026-03-21,11-11,100,credit\n"
                + "2026-03-21,22-22,900,credit\n";
        BankStatementFile.Table table = read(csv, "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertEquals(1, mapping.account);
        mapping.accountValue = "1111";
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.transactionCount);
        assertEquals(1, parsed.filteredAccountRows);
        assertEquals(100, parsed.totalCreditRials, 0.001);
    }

    @Test public void malformedRowsAreCountedAndNeverSilentlySavedAsZero() throws Exception {
        String csv = "date,amount,direction\n2026-03-21,100,credit\nnot-a-date,50,debit\n";
        BankStatementFile.Table table = read(csv, "statement.csv");
        assertEquals(0, table.headerRow);
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertTrue(mapping.valid());
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.invalidRows);
        assertTrue(parsed.errorSummary.contains("ردیف 3"));
        assertTrue(parsed.warnings.toString().contains("ذخیره نکنید"));
    }

    @Test public void rowsOutsideChosenDateRangeAreReportedSeparately() throws Exception {
        String csv = "date,amount,direction\n2026-03-21,100,credit\n2026-03-22,50,debit\n";
        BankStatementFile.Table table = read(csv, "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.transactions.size());
        assertEquals(1, parsed.outsideRangeRows);
        assertEquals(0, parsed.invalidRows);
    }

    @Test public void acceptsAnEmptyButValidStatementForAConfirmedZeroMovementPeriod() throws Exception {
        BankStatementFile.Table table = read("date,amount,direction\n", "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        mapping.signedAmountDefinesDirection = true;
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/02");
        assertTrue(parsed.transactions.isEmpty());
        assertTrue(parsed.daily.isEmpty());
        assertEquals(0, parsed.totalCreditRials, 0);
    }

    @Test public void normalizesJalaliGregorianDmyAndExcelSerialDates() {
        assertEquals("1404/08/10", BankStatementFile.normalizeDate("۱۴۰۴/۰۸/۱۰", BankStatementFile.DATE_AUTO));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("2025-03-21 13:40:00", BankStatementFile.DATE_AUTO));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("21/03/2025", BankStatementFile.DATE_GREGORIAN_DMY));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("21/03/2025", BankStatementFile.DATE_AUTO));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("03/21/2025", BankStatementFile.DATE_GREGORIAN_MDY));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("01/01/1404", BankStatementFile.DATE_JALALI_DMY));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate("01/01/1404", BankStatementFile.DATE_JALALI_MDY));
        assertEquals("", BankStatementFile.normalizeDate("01/02/2025", BankStatementFile.DATE_AUTO));
        assertEquals("", BankStatementFile.normalizeDate("01/02/1404", BankStatementFile.DATE_AUTO));
        assertEquals("", BankStatementFile.normalizeDate("2025-02-31", BankStatementFile.DATE_AUTO));
        long serial = LocalDate.of(2025, 3, 21).toEpochDay() - LocalDate.of(1899, 12, 30).toEpochDay();
        assertEquals("1404/01/01", BankStatementFile.normalizeDate(String.valueOf(serial), BankStatementFile.DATE_EXCEL_SERIAL));
        assertEquals("1404/01/01", BankStatementFile.normalizeDate(serial + ".5", BankStatementFile.DATE_EXCEL_SERIAL));
        assertEquals("", BankStatementFile.normalizeDate("21/03/2025", BankStatementFile.DATE_GREGORIAN_YMD));
        assertEquals("", BankStatementFile.normalizeDate("2025-03-21", BankStatementFile.DATE_JALALI_YMD));
        assertEquals("", BankStatementFile.normalizeDate("not-a-date", BankStatementFile.DATE_AUTO));
    }

    @Test public void parsesCommonSignedAndGroupedAmountFormatsWithoutDoubleNegation() {
        assertEquals(1_234_567, BankStatementFile.parseAmount("۱٬۲۳۴٬۵۶۷ ریال"), 0.001);
        assertEquals(1234.5, BankStatementFile.parseAmount("1,234.50"), 0.001);
        assertEquals(1_234_567, BankStatementFile.parseAmount("1.234.567"), 0.001);
        assertEquals(12.5, BankStatementFile.parseAmount("۱۲٫۵"), 0.001);
        assertEquals(1234.56, BankStatementFile.parseAmount("1.234,56",
                BankStatementFile.AMOUNT_DECIMAL_COMMA), 0.001);
        assertEquals(1234.56, BankStatementFile.parseAmount("1,234.56",
                BankStatementFile.AMOUNT_DECIMAL_DOT), 0.001);
        assertNull(BankStatementFile.parseAmount("1.234,56", BankStatementFile.AMOUNT_DECIMAL_DOT));
        assertNull(BankStatementFile.parseAmount("1,234.56", BankStatementFile.AMOUNT_DECIMAL_COMMA));
        assertEquals(1.234, BankStatementFile.parseAmount("1.234",
                BankStatementFile.AMOUNT_DECIMAL_DOT), 0.001);
        assertEquals(1234, BankStatementFile.parseAmount("1.234",
                BankStatementFile.AMOUNT_GROUPING_ONLY), 0.001);
        assertNull(BankStatementFile.parseAmount("(−۱٬۲۳۴)")); // Conflicting negative markers fail closed.
        assertEquals(-1234, BankStatementFile.parseAmount("(۱٬۲۳۴)",
                BankStatementFile.AMOUNT_GROUPING_ONLY), 0.001);
        assertEquals(-1234, BankStatementFile.parseAmount("-1,234",
                BankStatementFile.AMOUNT_GROUPING_ONLY), 0.001);
        assertNull(BankStatementFile.parseAmount("-1,234")); // Auto mode cannot infer decimal vs grouping.
        assertNull(BankStatementFile.parseAmount("—"));
        assertNull(BankStatementFile.parseAmount("12x"));
        assertNull(BankStatementFile.parseAmount("1,234"));
        assertNull(BankStatementFile.parseAmount("1.234"));
        assertNull(BankStatementFile.parseAmount("1,234.567"));
        assertNull(BankStatementFile.parseAmount("1.234,567"));
        assertEquals(1234, BankStatementFile.parseAmount("1,234", BankStatementFile.AMOUNT_DECIMAL_DOT), 0.001);
        assertEquals(1234, BankStatementFile.parseAmount("1.234", BankStatementFile.AMOUNT_DECIMAL_COMMA), 0.001);
        assertEquals(new java.math.BigDecimal("9007199254740993.12"),
                BankStatementFile.parseAmountExact("9007199254740993.12", BankStatementFile.AMOUNT_AUTO));
    }

    @Test public void unitMustBeExplicitAndMissingOrAmbiguousRowsNeverBecomeZero() throws Exception {
        BankStatementFile.Table unitTable = read("date,amount,direction\n2026-03-21,100,credit\n", "statement.csv");
        BankStatementFile.Mapping unconfirmed = BankStatementFile.Mapping.suggested(unitTable.headers);
        assertFalse(unconfirmed.valid());
        try {
            BankStatementFile.analyze(unitTable, unconfirmed, "1405/01/01", "1405/01/01");
            throw new AssertionError("Expected an explicit currency unit");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("واحد"));
        }

        BankStatementFile.Table malformed = read("date,amount,direction\n"
                + "2026-03-21,100,credit\n"
                + "2026-03-21,,debit\n"
                + "2026-03-21,100,credit/debit\n", "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(malformed);
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(malformed, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.transactionCount);
        assertEquals(2, parsed.invalidRows);
        assertTrue(parsed.errorSummary.contains("مبلغ این ردیف خالی است"));
        assertTrue(parsed.errorSummary.contains("جهت واریز/برداشت نامشخص"));
    }

    @Test public void bothSplitAmountsMissingIsAnInvalidRowRatherThanZeroMovement() throws Exception {
        BankStatementFile.Table table = read("date,credit,debit\n"
                + "2026-03-21,100,\n"
                + "2026-03-21,,\n", "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.transactionCount);
        assertEquals(1, parsed.invalidRows);
        assertTrue(parsed.errorSummary.contains("هر دو مبلغ واریز و برداشت خالی‌اند"));
    }

    @Test public void explicitCurrencyMarkerMustMatchTheSelectedUnit() throws Exception {
        BankStatementFile.Table table = read("date,amount,direction\n"
                + "2026-03-21,100 ریال,credit\n"
                + "2026-03-21,100 تومان,credit\n", "statement.csv");
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1405/01/01", "1405/01/01");
        assertEquals(1, parsed.transactionCount);
        assertEquals(1, parsed.invalidRows);
        assertTrue(parsed.errorSummary.contains("واحد فایل ریال"));
    }

    @Test public void rejectsLegacyXlsAndUnterminatedCsvQuote() throws Exception {
        try {
            BankStatementFile.read(new ByteArrayInputStream(new byte[]{1, 2, 3}), "old.xls");
            throw new AssertionError("Expected .xls to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(".xls"));
        }
        try {
            read("date,amount,direction\n2026-03-21,\"100,credit\n", "bad.csv");
            throw new AssertionError("Expected invalid CSV quoting to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("کوتیشن"));
        }
    }

    @Test public void readsAnXlsxWorksheetWithoutWritingTheSourceFile() throws Exception {
        byte[] bytes = minimalXlsx();
        BankStatementFile.Table table = BankStatementFile.read(new ByteArrayInputStream(bytes), "export.xlsx");
        assertTrue(table.xlsx);
        assertEquals(3, table.rows.size());
        BankStatementFile.Mapping mapping = confirmedMapping(table);
        assertTrue(mapping.valid());
        BankStatementFile.Parsed parsed = BankStatementFile.analyze(table, mapping,
                "1404/01/01", "1404/01/01");
        assertEquals(12_500, parsed.totalCreditRials, 0.001);
        assertEquals(3_000, parsed.totalDebitRials, 0.001);
        assertNotNull(parsed.transactions.get(0).description);
    }


    private static BankStatementFile.Mapping confirmedMapping(BankStatementFile.Table table) {
        BankStatementFile.Mapping mapping = BankStatementFile.Mapping.suggested(table.headers);
        mapping.amountUnitConfirmed = true;
        return mapping;
    }

    private static BankStatementFile.Table read(String text, String name) throws IOException {
        return BankStatementFile.read(new ByteArrayInputStream(text.getBytes("UTF-8")), name);
    }

    private static byte[] minimalXlsx() throws IOException {
        String shared = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>تاریخ</t></si><si><t>شرح</t></si><si><t>واریز</t></si><si><t>برداشت</t></si></sst>";
        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
                + "<c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\"><v>45737</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>فروش</t></is></c>"
                + "<c r=\"C2\"><v>12500</v></c><c r=\"D2\"><v>0</v></c></row>"
                + "<row r=\"3\"><c r=\"A3\"><v>45737</v></c><c r=\"B3\" t=\"inlineStr\"><is><t>خرید</t></is></c>"
                + "<c r=\"C3\"><v>0</v></c><c r=\"D3\"><v>3000</v></c></row>"
                + "</sheetData></worksheet>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "xl/sharedStrings.xml", shared);
            String cover = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                    + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Account summary</t></is></c></row>"
                    + "</sheetData></worksheet>";
            put(zip, "xl/worksheets/sheet1.xml", cover);
            put(zip, "xl/worksheets/sheet2.xml", sheet);
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes("UTF-8"));
        zip.closeEntry();
    }
}
