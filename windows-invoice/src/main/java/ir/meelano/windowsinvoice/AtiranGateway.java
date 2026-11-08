package ir.meelano.windowsinvoice;

import java.util.List;
import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;

/** Persistence boundary. The UI never issues SQL itself. */
public interface AtiranGateway extends AutoCloseable {
    boolean live();
    boolean connected();
    boolean writeEnabled();
    String connectionStatus();
    void testConnection() throws Exception;
    List<Customer> searchCustomers(String query) throws Exception;
    List<Product> searchProducts(String query) throws Exception;
    InvoiceResult submit(InvoiceDraft draft) throws Exception;
    @Override default void close() { }
}
