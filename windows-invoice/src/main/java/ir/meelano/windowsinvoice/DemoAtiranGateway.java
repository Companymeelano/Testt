package ir.meelano.windowsinvoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;

/** Clearly labelled preview data. It cannot submit or persist an invoice. */
public final class DemoAtiranGateway implements AtiranGateway {
    private final List<Customer> customers = List.of(
            new Customer(1101, "فروشگاه نمونهٔ بهار", "۰۹۱۲•••۱۲۳۴", new BigDecimal("12500000")),
            new Customer(1102, "خشکبار مهرگان (نمونه)", "۰۹۳۵•••۶۷۸۹", new BigDecimal("0")),
            new Customer(1103, "سوپرمارکت سبزینه (نمونه)", "", new BigDecimal("4860000"))
    );
    private final List<Product> products = List.of(
            product(7101, "پستهٔ اکبری ممتاز", "626100000001", "کیلوگرم", "1840000", "بستهٔ ۱۰ کیلویی"),
            product(7102, "بادام درختی شور", "626100000002", "کیلوگرم", "920000", "بستهٔ ۵ کیلویی"),
            product(7103, "کشمش سبز قلمی", "626100000003", "کیلوگرم", "565000", "بستهٔ ۵ کیلویی"),
            product(7104, "فندق خام ممتاز", "626100000004", "کیلوگرم", "1175000", "بستهٔ ۵ کیلویی"),
            product(7105, "مخلوط آجیل ویژه", "626100000005", "کیلوگرم", "1320000", "بستهٔ ۳ کیلویی")
    );

    private static Product product(long code, String name, String barcode, String unit,
                                   String price, String packageName) {
        return new Product(code, name, barcode, unit, new BigDecimal(price), 1, packageName, 1);
    }

    @Override public boolean live() { return false; }
    @Override public boolean connected() { return false; }
    @Override public boolean writeEnabled() { return false; }
    @Override public String connectionStatus() { return "پیش‌نمایش نمایشی · ثبت در آتیران خاموش"; }
    @Override public void testConnection() { throw new IllegalStateException("این نسخه در حالت پیش‌نمایش است."); }

    @Override public List<Customer> searchCustomers(String query) {
        String q = normalize(query);
        return customers.stream().filter(c -> q.isEmpty() || normalize(c.name()).contains(q)
                || Long.toString(c.code()).contains(q) || normalize(c.phone()).contains(q)).collect(Collectors.toList());
    }

    @Override public List<Product> searchProducts(String query) {
        String q = normalize(query);
        return products.stream().filter(p -> q.isEmpty() || normalize(p.name()).contains(q)
                || Long.toString(p.code()).contains(q) || normalize(p.barcode()).contains(q)).collect(Collectors.toList());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String s = value.trim().toLowerCase(Locale.ROOT).replace('ي','ی').replace('ك','ک');
        return s.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    }

    @Override public InvoiceResult submit(InvoiceDraft draft) {
        throw new UnsupportedOperationException("حالت پیش‌نمایش اجازهٔ ثبت یا ذخیرهٔ فاکتور را نمی‌دهد.");
    }
}
