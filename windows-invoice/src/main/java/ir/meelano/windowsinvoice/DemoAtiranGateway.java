package ir.meelano.windowsinvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import ir.meelano.windowsinvoice.CustomerModels.BalanceFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerDirectoryRow;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerPage;
import ir.meelano.windowsinvoice.CustomerModels.CustomerSort;
import ir.meelano.windowsinvoice.CustomerModels.CustomerStatusFilter;
import ir.meelano.windowsinvoice.CustomerModels.DetailField;
import ir.meelano.windowsinvoice.CustomerModels.DetailLine;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
import ir.meelano.windowsinvoice.CustomerModels.TransactionDetails;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRequest;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionPost;

/** Clearly labelled preview data. It cannot submit or persist an invoice. */
public final class DemoAtiranGateway implements AtiranGateway {
    private final List<Customer> customers = List.of(
            new Customer(1101, "فروشگاه نمونهٔ بهار", "۰۹۱۲•••۱۲۳۴", new BigDecimal("12500000")),
            new Customer(1102, "خشکبار مهرگان (نمونه)", "۰۹۳۵•••۶۷۸۹", new BigDecimal("0")),
            new Customer(1103, "سوپرمارکت سبزینه (نمونه)", "", new BigDecimal("4860000")),
            new Customer(1104, "پخش آفتاب (نمونه)", "۰۹۱۰•••۴۴۵۵", new BigDecimal("-1700000"))
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

    @Override public CustomerPage listCustomers(CustomerListFilter filter) {
        CustomerListFilter f = filter == null ? CustomerListFilter.defaults() : filter;
        String query = normalize(f.query());
        List<CustomerDirectoryRow> rows = new ArrayList<>();
        for (Customer customer : customers) {
            BigDecimal balance = customer.balance();
            boolean active = customer.code() != 1104;
            boolean blocked = customer.code() == 1104;
            String group = customer.code() == 1101 ? "فروش زنجیره‌ای" : customer.code() == 1102 ? "خرده‌فروشی"
                    : customer.code() == 1103 ? "سوپرمارکت" : "پخش منطقه‌ای";
            String route = customer.code() == 1101 || customer.code() == 1104 ? "مسیر نمونهٔ شمال" : "مسیر نمونهٔ مرکز";
            String visitor = customer.code() == 1103 ? "ویزیتور نمونهٔ ۲" : "ویزیتور نمونهٔ ۱";
            BigDecimal sales = balance.signum() > 0 ? balance.multiply(new BigDecimal("2.4"))
                    : customer.code() == 1102 ? new BigDecimal("8400000") : new BigDecimal("3100000");
            long invoices = customer.code() == 1101 ? 14 : customer.code() == 1102 ? 8 : customer.code() == 1103 ? 5 : 3;
            String lastSale = demoDate(customer.code() == 1101 ? 1 : customer.code() == 1102 ? 9 : 22);
            CustomerDirectoryRow row = new CustomerDirectoryRow(customer, new BigDecimal("25000000"), group,
                    route, visitor, active, blocked, sales, invoices, lastSale);
            if (!query.isEmpty() && !normalize(customer.name()).contains(query)
                    && !Long.toString(customer.code()).contains(query) && !normalize(customer.phone()).contains(query)) continue;
            if (f.balance() == BalanceFilter.DEBTORS && balance.compareTo(BigDecimal.ONE) <= 0) continue;
            if (f.balance() == BalanceFilter.CREDITORS && balance.compareTo(BigDecimal.ONE.negate()) >= 0) continue;
            if (f.balance() == BalanceFilter.SETTLED && balance.abs().compareTo(BigDecimal.ONE) > 0) continue;
            if (f.status() == CustomerStatusFilter.ACTIVE && !active) continue;
            if (f.status() == CustomerStatusFilter.BLOCKED && !blocked) continue;
            if (f.status() == CustomerStatusFilter.INACTIVE && (active || blocked)) continue;
            if (!normalize(group).contains(normalize(f.groupQuery())) && !f.groupQuery().isBlank()) continue;
            if (!normalize(route).contains(normalize(f.routeQuery())) && !f.routeQuery().isBlank()) continue;
            if (!normalize(visitor).contains(normalize(f.visitorQuery())) && !f.visitorQuery().isBlank()) continue;
            if (f.minBalance() != null && balance.compareTo(f.minBalance()) < 0) continue;
            if (f.maxBalance() != null && balance.compareTo(f.maxBalance()) > 0) continue;
            rows.add(row);
        }
        Comparator<CustomerDirectoryRow> order = switch (f.sort()) {
            case DEBT -> Comparator.comparing((CustomerDirectoryRow r) -> r.customer().balance()).reversed();
            case SALES -> Comparator.comparing(CustomerDirectoryRow::salesTotal).reversed();
            case RECENT -> Comparator.comparing(CustomerDirectoryRow::lastSaleDate).reversed();
            default -> Comparator.comparing(r -> normalize(r.customer().name()));
        };
        rows.sort(order.thenComparingLong(r -> r.customer().code()));
        long total = rows.size();
        int start = Math.min(rows.size(), f.page() * f.pageSize());
        int end = Math.min(rows.size(), start + f.pageSize());
        return new CustomerPage(rows.subList(start, end), total, f.page(), f.pageSize());
    }

    @Override public CustomerLedgerReport customerLedger(CustomerDirectoryRow customer, LedgerFilter filter) {
        if (customer == null) throw new IllegalArgumentException("مشتری را انتخاب کنید.");
        LedgerFilter f = filter == null ? new LedgerFilter("", "", LedgerCategory.ALL, 0, 200) : filter;
        List<LedgerEntry> raw = sampleLedger(customer.customer().code(), customer.customer().balance());
        raw.sort(Comparator.comparing(LedgerEntry::date).thenComparing(LedgerEntry::time).thenComparing(LedgerEntry::rowId));
        BigDecimal running = BigDecimal.ZERO;
        List<LedgerEntry> complete = new ArrayList<>();
        for (LedgerEntry item : raw) {
            running = running.add(item.debit()).subtract(item.credit());
            complete.add(new LedgerEntry(item.rowId(), item.date(), item.time(), item.operationCode(),
                    item.operation(), item.description(), item.documentNo(), item.debit(), item.credit(),
                    running, item.category()));
        }
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal closing = opening;
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        List<LedgerEntry> filtered = new ArrayList<>();
        for (LedgerEntry item : complete) {
            if (!f.fromJalali().isEmpty() && item.date().compareTo(f.fromJalali()) < 0) {
                opening = item.balance();
                closing = item.balance();
                continue;
            }
            if (!f.toJalali().isEmpty() && item.date().compareTo(f.toJalali()) > 0) continue;
            closing = item.balance();
            if (f.category() == LedgerCategory.ALL || item.category() == f.category()) {
                debitTotal = debitTotal.add(item.debit());
                creditTotal = creditTotal.add(item.credit());
                filtered.add(item);
            }
        }
        if (f.toJalali().isEmpty() && (complete.isEmpty() || complete.get(complete.size() - 1).date().compareTo(f.fromJalali()) >= 0))
            closing = running;
        filtered.sort(Comparator.comparing(LedgerEntry::date).thenComparing(LedgerEntry::time)
                .thenComparing(LedgerEntry::rowId).reversed());
        long total = filtered.size();
        int start = Math.min(filtered.size(), f.page() * f.pageSize());
        int end = Math.min(filtered.size(), start + f.pageSize());
        return new CustomerLedgerReport(customer, filtered.subList(start, end), total, opening,
                debitTotal, creditTotal, closing, f.fromJalali(), f.toJalali(), f.category(), false);
    }

    @Override public TransactionDetails customerTransactionDetails(CustomerDirectoryRow customer, LedgerEntry entry) {
        List<DetailField> fields = List.of(
                new DetailField("مشتری", customer.customer().name()),
                new DetailField("کد مشتری", Long.toString(customer.customer().code())),
                new DetailField("تاریخ", entry.date() + "  " + entry.time()),
                new DetailField("نوع عملیات", entry.category().label()),
                new DetailField("شرح عملیات", entry.operation()),
                new DetailField("شماره سند", entry.documentNo()),
                new DetailField("بدهکار", entry.debit().toPlainString()),
                new DetailField("بستانکار", entry.credit().toPlainString()),
                new DetailField("مانده پس از سند", entry.balance().toPlainString()),
                new DetailField("شرح", entry.description()),
                new DetailField("وضعیت داده", "نمونهٔ نمایشی؛ سند واقعی آتیران نیست")
        );
        List<DetailLine> lines = entry.category() == LedgerCategory.SALES
                ? List.of(new DetailLine("کالای نمونه", "۲", "۱٬۸۴۰٬۰۰۰", new BigDecimal("3680000"))) : List.of();
        return new TransactionDetails(entry.operation().isBlank() ? entry.category().label() : entry.operation(), fields, lines);
    }

    private static List<LedgerEntry> sampleLedger(long code, BigDecimal currentBalance) {
        BigDecimal scale = code == 1101 ? new BigDecimal("1.0") : code == 1103 ? new BigDecimal("0.6") : new BigDecimal("0.4");
        List<LedgerEntry> rows = new ArrayList<>();
        rows.add(sampleEntry(code, 50, "20", "فاکتور فروش", "فروش کالای نمونه", "فاکتور", "SF-" + code + "-01", new BigDecimal("7800000").multiply(scale), BigDecimal.ZERO, LedgerCategory.SALES));
        rows.add(sampleEntry(code, 44, "1", "دریافت نقدی", "دریافت بابت فاکتور", "قبض دریافت", "RC-" + code + "-01", BigDecimal.ZERO, new BigDecimal("2100000").multiply(scale), LedgerCategory.RECEIPTS));
        rows.add(sampleEntry(code, 35, "31", "فاکتور خرید", "خرید کالای نمونه", "فاکتور خرید", "BF-" + code + "-02", BigDecimal.ZERO, new BigDecimal("950000").multiply(scale), LedgerCategory.PURCHASES));
        rows.add(sampleEntry(code, 28, "3", "چک دریافتی", "چک بابت مانده حساب", "چک", "CH-" + code + "-07", BigDecimal.ZERO, new BigDecimal("1250000").multiply(scale), LedgerCategory.CHEQUES));
        rows.add(sampleEntry(code, 20, "32", "پرداخت بانکی", "پرداخت به حساب فروشنده", "قبض پرداخت", "PY-" + code + "-03", new BigDecimal("420000").multiply(scale), BigDecimal.ZERO, LedgerCategory.PAYMENTS));
        rows.add(sampleEntry(code, 13, "20", "فاکتور فروش", "فروش سفارش جدید", "فاکتور", "SF-" + code + "-02", new BigDecimal("3650000").multiply(scale), BigDecimal.ZERO, LedgerCategory.SALES));
        rows.add(sampleEntry(code, 7, "1", "دریافت کارت‌خوان", "تسویه بخشی از مانده", "قبض دریافت", "RC-" + code + "-02", BigDecimal.ZERO, new BigDecimal("1850000").multiply(scale), LedgerCategory.RECEIPTS));
        rows.add(sampleEntry(code, 2, "8", "اصلاح/گردش حساب", "ثبت نمونه برای نمایش فیلترها", "سند", "AD-" + code + "-01", new BigDecimal("125000").multiply(scale), BigDecimal.ZERO, LedgerCategory.OTHER));
        BigDecimal recentNet = BigDecimal.ZERO;
        for (LedgerEntry row : rows) recentNet = recentNet.add(row.debit()).subtract(row.credit());
        BigDecimal broughtForward = currentBalance.subtract(recentNet);
        rows.add(sampleEntry(code, 400, "0", "ماندهٔ حساب قبلی", "ماندهٔ انتقالی نمونه برای تراز گردش", "مانده", "BF-" + code,
                broughtForward.max(BigDecimal.ZERO), broughtForward.negate().max(BigDecimal.ZERO), LedgerCategory.OTHER));
        return rows;
    }

    private static LedgerEntry sampleEntry(long code, int daysAgo, String opCode, String operation,
                                           String description, String prefix, String documentNo,
                                           BigDecimal debit, BigDecimal credit, LedgerCategory category) {
        return new LedgerEntry(prefix + "-" + code, demoDate(daysAgo), "10:30", opCode, operation,
                description, documentNo, debit, credit, BigDecimal.ZERO, category);
    }

    private static String demoDate(int daysAgo) {
        return PersianDate.fromGregorian(LocalDate.now(ZoneId.of("Asia/Tehran")).minusDays(daysAgo));
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

    @Override public boolean supportsSections() { return true; }

    /**
     * Preview data for every desktop section. The role check is the same one the live gateway applies,
     * so an operator sees exactly the reports their Atiran role would open.
     */
    @Override public SectionReport loadSection(SectionRequest request) {
        SectionRequest ask = requireAllowed(request);
        VariantSpec spec = SectionCatalog.spec(ask.sectionId(), ask.variantId());
        String today = PersianDate.today();
        List<SectionRow> rows = DemoSectionData.rows(spec, ask.filter(), today);
        return SectionPost.process(spec, ask.filter(), rows, false, true, today);
    }

    private static SectionRequest requireAllowed(SectionRequest request) {
        if (request == null) throw new IllegalArgumentException("گزارش مورد نظر را انتخاب کنید.");
        String sectionId = request.sectionId().toLowerCase(java.util.Locale.ROOT);
        if (!SectionCatalog.isDesktopSection(sectionId))
            throw new IllegalArgumentException("بخش «" + WindowsRoleMenus.labelOf(sectionId)
                    + "» در نسخهٔ دسکتاپ پیاده‌سازی نشده است.");
        if (!WindowsRoleMenus.allows(request.role(), sectionId))
            throw new IllegalArgumentException("این گزارش برای نقش «" + request.role() + "» فعال نیست.");
        return new SectionRequest(sectionId, request.variantId(), request.role(), request.filter());
    }

    @Override public InvoiceResult submit(InvoiceDraft draft) {
        throw new UnsupportedOperationException("حالت پیش‌نمایش اجازهٔ ثبت یا ذخیرهٔ فاکتور را نمی‌دهد.");
    }
}
