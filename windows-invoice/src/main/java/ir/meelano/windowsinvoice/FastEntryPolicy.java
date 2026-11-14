package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.Product;

/** Exact-identifier rules for keyboard and barcode quick entry. */
final class FastEntryPolicy {
    private FastEntryPolicy() { }

    static boolean isExactCustomerCode(String query, Customer customer) {
        if (customer == null) return false;
        String digits = numericIdentifier(query);
        return !digits.isEmpty() && digits.equals(Long.toString(customer.code()));
    }

    static boolean canAutoAddProduct(String query, Product product) {
        if (product == null || !product.hasUnambiguousPrice()) return false;
        String digits = numericIdentifier(query);
        return !digits.isEmpty() && (digits.equals(Long.toString(product.code()))
                || digits.equals(numericIdentifier(product.barcode())));
    }

    private static String numericIdentifier(String value) {
        if (value == null) return "";
        String normalized = normalizeDigits(value).trim();
        if (!normalized.matches("[0-9][0-9\\s,٬-]*")) return "";
        return normalized.replaceAll("[^0-9]", "");
    }

    private static String normalizeDigits(String value) {
        return value.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    }
}
