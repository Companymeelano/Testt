package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.CustomerModels.BalanceFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerStatusFilter;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
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

    @Test void customerDirectoryAndStatementsAreClearlySyntheticAndReadOnly() throws Exception {
        DemoAtiranGateway gateway = new DemoAtiranGateway();
        var directory = gateway.listCustomers(CustomerListFilter.defaults());

        assertEquals(4, directory.total());
        var creditor = gateway.listCustomers(new CustomerListFilter("", BalanceFilter.CREDITORS,
                CustomerStatusFilter.ALL, "", "", "", null, null,
                CustomerModels.CustomerSort.NAME, 0, 80));
        assertEquals(1, creditor.rows().size());
        assertEquals(1104, creditor.rows().get(0).customer().code());
        assertTrue(creditor.rows().get(0).blocked());

        var customer = directory.rows().stream().filter(row -> row.customer().code() == 1101).findFirst().orElseThrow();
        CustomerLedgerReport report = gateway.customerLedger(customer,
                new LedgerFilter("", "", LedgerCategory.ALL, 0, 200));
        assertEquals(0, customer.customer().balance().compareTo(report.closingBalance()));
        assertTrue(report.entries().stream().anyMatch(row -> row.category() == LedgerCategory.RECEIPTS));
        assertTrue(report.entries().stream().anyMatch(row -> row.category() == LedgerCategory.PURCHASES));
        assertTrue(report.entries().stream().anyMatch(row -> row.category() == LedgerCategory.PAYMENTS));
        assertTrue(report.entries().stream().anyMatch(row -> row.category() == LedgerCategory.CHEQUES));
        assertFalse(gateway.writeEnabled());
    }
}
