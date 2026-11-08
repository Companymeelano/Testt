package ir.meelano.windowsinvoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable data exchanged by the desktop UI and the Atiran gateway. */
public final class InvoiceModels {
    private InvoiceModels() { }

    public record Customer(long code, String name, String phone, BigDecimal balance) {
        public Customer {
            if (code <= 0) throw new IllegalArgumentException("Customer code must be positive");
            name = Objects.requireNonNullElse(name, "");
            phone = Objects.requireNonNullElse(phone, "");
            balance = balance == null ? BigDecimal.ZERO : balance;
        }
        @Override public String toString() { return name + "  ·  " + code; }
    }

    public record Product(long code, String name, String barcode, String unit,
                          BigDecimal unitPrice, int unitsPerPack, String packageName,
                          int activePriceRows) {
        public Product {
            if (code <= 0) throw new IllegalArgumentException("Product code must be positive");
            name = Objects.requireNonNullElse(name, "");
            barcode = Objects.requireNonNullElse(barcode, "");
            unit = Objects.requireNonNullElse(unit, "عدد");
            unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
            unitsPerPack = Math.max(1, unitsPerPack);
            packageName = Objects.requireNonNullElse(packageName, "");
        }
        public boolean hasUnambiguousPrice() {
            return activePriceRows == 1 && unitPrice.signum() > 0;
        }
        @Override public String toString() { return name; }
    }

    public record InvoiceLine(Product product, BigDecimal quantity,
                              BigDecimal discountPercent, BigDecimal taxPercent) {
        public InvoiceLine {
            Objects.requireNonNull(product, "product");
            quantity = quantity == null ? BigDecimal.ZERO : quantity;
            discountPercent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
            taxPercent = taxPercent == null ? BigDecimal.ZERO : taxPercent;
        }
    }

    public record InvoiceDraft(Customer customer, List<InvoiceLine> lines, String jalaliDate,
                               String notes, BigDecimal globalDiscount, BigDecimal freight,
                               int settlementDays, int warehouseId, String uniqueId) {
        public InvoiceDraft {
            Objects.requireNonNull(customer, "customer");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            jalaliDate = Objects.requireNonNullElse(jalaliDate, "").trim();
            notes = Objects.requireNonNullElse(notes, "");
            globalDiscount = globalDiscount == null ? BigDecimal.ZERO : globalDiscount;
            freight = freight == null ? BigDecimal.ZERO : freight;
            uniqueId = Objects.requireNonNullElse(uniqueId, "");
        }
    }

    public record InvoiceResult(long invoiceNumber, boolean duplicate, boolean confirmed,
                                BigDecimal total, BigDecimal customerBalance, String note) {
        public InvoiceResult {
            total = total == null ? BigDecimal.ZERO : total;
            customerBalance = customerBalance == null ? BigDecimal.ZERO : customerBalance;
            note = Objects.requireNonNullElse(note, "");
        }
    }
}
