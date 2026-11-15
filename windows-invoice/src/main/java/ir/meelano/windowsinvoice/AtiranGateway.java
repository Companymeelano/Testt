package ir.meelano.windowsinvoice;

import java.util.List;
import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import ir.meelano.windowsinvoice.CustomerModels.CustomerDirectoryRow;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerPage;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
import ir.meelano.windowsinvoice.CustomerModels.TransactionDetails;
import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRequest;

/** Persistence boundary. The UI never issues SQL itself. */
public interface AtiranGateway extends AutoCloseable {
    boolean live();
    boolean connected();
    boolean writeEnabled();
    String connectionStatus();
    void testConnection() throws Exception;
    List<Customer> searchCustomers(String query) throws Exception;
    List<Product> searchProducts(String query) throws Exception;

    /** Read-only customer directory and statement APIs; demo mode returns explicitly synthetic sample data. */
    default CustomerPage listCustomers(CustomerListFilter filter) throws Exception {
        throw new UnsupportedOperationException("فهرست مشتریان در این درگاه پیاده‌سازی نشده است.");
    }
    default CustomerLedgerReport customerLedger(CustomerDirectoryRow customer, LedgerFilter filter) throws Exception {
        throw new UnsupportedOperationException("گردش مشتری در این درگاه پیاده‌سازی نشده است.");
    }
    default TransactionDetails customerTransactionDetails(CustomerDirectoryRow customer, LedgerEntry entry) throws Exception {
        return new TransactionDetails(entry.operation(), java.util.List.of(), java.util.List.of());
    }

    /**
     * Read-only Manager section report: cash box, cheques, stock ledger, sales/finance reports, profit,
     * production, personnel and users. Implementations must refuse a section the caller's role may not
     * open, and must never write to Atiran from a section read.
     */
    default SectionReport loadSection(SectionRequest request) throws Exception {
        throw new UnsupportedOperationException("گزارش‌های «"
                + SectionCatalog.titleOf(request == null ? "" : request.sectionId())
                + "» در این درگاه پیاده‌سازی نشده است.");
    }

    /** Section reports this gateway can serve; the desktop uses it to keep unavailable menus honest. */
    default boolean supportsSections() { return false; }

    InvoiceResult submit(InvoiceDraft draft) throws Exception;
    @Override default void close() { }
}
