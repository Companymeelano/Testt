package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceModelsTest {
    @Test void draftTakesAnImmutableSnapshotOfItsLines() {
        Customer customer = new Customer(12, "مشتری", "", BigDecimal.ZERO);
        Product product = new Product(14, "کالا", "", "عدد", BigDecimal.TEN, 1, "", 1);
        List<InvoiceModels.InvoiceLine> input = new ArrayList<>();
        InvoiceDraft draft = new InvoiceDraft(customer, input, "1405/08/16", "", BigDecimal.ZERO,
                BigDecimal.ZERO, 0, 0, "stable-id");

        input.add(new InvoiceModels.InvoiceLine(product, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(draft.lines().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> draft.lines().add(
                new InvoiceModels.InvoiceLine(product, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test void productPriceMustHaveOneActiveAtiranRow() {
        Product exact = new Product(1, "exact", "", "عدد", BigDecimal.ONE, 1, "", 1);
        Product ambiguous = new Product(2, "ambiguous", "", "عدد", BigDecimal.ONE, 1, "", 2);

        assertTrue(exact.hasUnambiguousPrice());
        assertFalse(ambiguous.hasUnambiguousPrice());
    }
}
