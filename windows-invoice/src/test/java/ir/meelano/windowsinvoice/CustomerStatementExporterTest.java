package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.CustomerModels.CustomerDirectoryRow;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerStatementExporterTest {
    @TempDir Path tempDir;

    @Test void writesAReadableRtlExcelWorkbookWithoutExternalLibraries() throws Exception {
        CustomerDirectoryRow customer = new CustomerDirectoryRow(
                new Customer(42, "مشتری نمونه", "", new BigDecimal("1250")),
                new BigDecimal("5000"), "گروه نمونه", "مسیر نمونه", "ویزیتور نمونه",
                true, false, new BigDecimal("8000"), 3, "1405/08/01");
        LedgerEntry entry = new LedgerEntry("1", "1405/08/01", "", "20", "فروش",
                "شرح نمونه", "S-42", new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("1250"), LedgerCategory.SALES);
        CustomerLedgerReport report = new CustomerLedgerReport(customer, List.of(entry), 1,
                new BigDecimal("250"), new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("1250"), "", "", LedgerCategory.ALL, false);
        Path file = tempDir.resolve("statement.xlsx");

        CustomerStatementExporter.writeXlsx(file, report, List.of(entry), false);

        try (ZipFile workbook = new ZipFile(file.toFile(), StandardCharsets.UTF_8);
             var input = workbook.getInputStream(workbook.getEntry("xl/worksheets/sheet1.xml"))) {
            String sheet = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(workbook.getEntry("xl/workbook.xml") != null);
            assertTrue(sheet.contains("rightToLeft=\"1\""));
            assertTrue(sheet.contains("مشتری نمونه"));
            assertTrue(sheet.contains("1250"));
        }
    }
}
