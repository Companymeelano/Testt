package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DemoAtiranGatewayTest {
    @Test void demoDataIsSearchableButNeverConnectedOrWritable() throws Exception {
        DemoAtiranGateway gateway = new DemoAtiranGateway();

        assertFalse(gateway.live());
        assertFalse(gateway.connected());
        assertFalse(gateway.writeEnabled());
        assertFalse(gateway.searchCustomers("بهار").isEmpty());
        assertFalse(gateway.searchProducts("626100000001").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> gateway.submit(null));
    }
}
