package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastEntryPolicyTest {
    private final Customer customer = new Customer(1101, "مشتری نمونه", "", BigDecimal.ZERO);
    private final Product product = new Product(7101, "کالای نمونه", "626100000001", "عدد",
            new BigDecimal("1000"), 1, "", 1);

    @Test void recognizesExactCustomerCodeInPersianOrLatinDigits() {
        assertTrue(FastEntryPolicy.isExactCustomerCode("1101", customer));
        assertTrue(FastEntryPolicy.isExactCustomerCode("۱۱۰۱", customer));
        assertTrue(FastEntryPolicy.isExactCustomerCode("۱٬۱۰۱", customer));
        assertFalse(FastEntryPolicy.isExactCustomerCode("مشتری 1101", customer));
        assertFalse(FastEntryPolicy.isExactCustomerCode("110", customer));
    }

    @Test void autoAddRequiresTheWholeBarcodeOrProductCode() {
        assertTrue(FastEntryPolicy.canAutoAddProduct("626100000001", product));
        assertTrue(FastEntryPolicy.canAutoAddProduct("۶۲۶۱۰۰۰۰۰۰۰۱", product));
        assertTrue(FastEntryPolicy.canAutoAddProduct("7101", product));
        assertFalse(FastEntryPolicy.canAutoAddProduct("6261000", product));
        assertFalse(FastEntryPolicy.canAutoAddProduct("item 626100000001", product));
    }

    @Test void ambiguousPricesAndNullProductsAreNeverAutoAdded() {
        Product ambiguous = new Product(7101, "کالای مبهم", "626100000001", "عدد",
                new BigDecimal("1000"), 1, "", 2);

        assertFalse(FastEntryPolicy.canAutoAddProduct("626100000001", ambiguous));
        assertFalse(FastEntryPolicy.canAutoAddProduct("626100000001", null));
    }
}
