package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.InvoiceModels.Customer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Read-only customer directory and account-ledger models for the F11 customer center. */
public final class CustomerModels {
    private CustomerModels() { }

    public enum BalanceFilter {
        ALL("همه مانده‌ها"), DEBTORS("بدهکاران"), CREDITORS("بستانکاران"), SETTLED("تسویه‌شده‌ها");
        private final String label;
        BalanceFilter(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public enum CustomerStatusFilter {
        ALL("همه وضعیت‌ها"), ACTIVE("فعال"), BLOCKED("مسدود"), INACTIVE("غیرفعال");
        private final String label;
        CustomerStatusFilter(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public enum CustomerSort {
        NAME("نام الفبایی"), DEBT("بیشترین بدهی"), SALES("بیشترین فروش"), RECENT("آخرین فعالیت");
        private final String label;
        CustomerSort(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public enum LedgerCategory {
        ALL("همه گردش‌ها"), SALES("فروش‌ها"), PURCHASES("خریدها"),
        RECEIPTS("دریافت‌ها"), PAYMENTS("پرداخت‌ها"), CHEQUES("چک‌ها"), OTHER("سایر عملیات");
        private final String label;
        LedgerCategory(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }

        public static LedgerCategory classify(String operation, String description) {
            String text = ((operation == null ? "" : operation) + " "
                    + (description == null ? "" : description)).replace('ي', 'ی').replace('ك', 'ک');
            if (text.contains("چک")) return CHEQUES;
            if (text.contains("فروش")) return SALES;
            if (text.contains("خرید") || text.contains("خريد")) return PURCHASES;
            if (text.contains("دریافت") || text.contains("دريافت") || text.contains("وصول")) return RECEIPTS;
            if (text.contains("پرداخت") || text.contains("پرداختنی")) return PAYMENTS;
            return OTHER;
        }
    }

    /** Filters are read-only and server-side; page is zero-based. Null balance bounds mean unbounded. */
    public record CustomerListFilter(String query, BalanceFilter balance, CustomerStatusFilter status,
                                     String groupQuery, String routeQuery, String visitorQuery,
                                     BigDecimal minBalance, BigDecimal maxBalance,
                                     CustomerSort sort, int page, int pageSize) {
        public CustomerListFilter {
            query = clean(query);
            balance = Objects.requireNonNullElse(balance, BalanceFilter.ALL);
            status = Objects.requireNonNullElse(status, CustomerStatusFilter.ALL);
            groupQuery = clean(groupQuery);
            routeQuery = clean(routeQuery);
            visitorQuery = clean(visitorQuery);
            sort = Objects.requireNonNullElse(sort, CustomerSort.NAME);
            page = Math.max(0, page);
            pageSize = Math.max(20, Math.min(200, pageSize));
            if (minBalance != null && maxBalance != null && minBalance.compareTo(maxBalance) > 0)
                throw new IllegalArgumentException("حداقل مانده نباید از حداکثر بیشتر باشد.");
        }

        public static CustomerListFilter defaults() {
            return new CustomerListFilter("", BalanceFilter.ALL, CustomerStatusFilter.ALL,
                    "", "", "", null, null, CustomerSort.NAME, 0, 80);
        }

        public CustomerListFilter onPage(int value) {
            return new CustomerListFilter(query, balance, status, groupQuery, routeQuery, visitorQuery,
                    minBalance, maxBalance, sort, value, pageSize);
        }
    }

    public record CustomerDirectoryRow(Customer customer, BigDecimal creditLimit,
                                      String groupName, String routeName, String visitorName,
                                      boolean active, boolean blocked, BigDecimal salesTotal,
                                      long invoiceCount, String lastSaleDate) {
        public CustomerDirectoryRow {
            Objects.requireNonNull(customer, "customer");
            creditLimit = zero(creditLimit);
            groupName = clean(groupName);
            routeName = clean(routeName);
            visitorName = clean(visitorName);
            salesTotal = zero(salesTotal);
            invoiceCount = Math.max(0, invoiceCount);
            lastSaleDate = clean(lastSaleDate);
        }

        public String statusLabel() { return blocked ? "مسدود" : active ? "فعال" : "غیرفعال"; }
        public String balanceLabel() {
            int sign = customer.balance().compareTo(BigDecimal.ONE);
            if (sign > 0) return "بدهکار";
            if (customer.balance().compareTo(BigDecimal.ONE.negate()) < 0) return "بستانکار";
            return "تسویه";
        }
    }

    public record CustomerPage(List<CustomerDirectoryRow> rows, long total,
                               int page, int pageSize) {
        public CustomerPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
            total = Math.max(0, total);
            page = Math.max(0, page);
            pageSize = Math.max(1, pageSize);
        }
        public int pageCount() { return (int) Math.max(1, (total + pageSize - 1) / pageSize); }
    }

    /** Jalali bounds are inclusive; an empty bound means open-ended. */
    public record LedgerFilter(String fromJalali, String toJalali, LedgerCategory category,
                               int page, int pageSize) {
        public LedgerFilter {
            fromJalali = normalizeDate(fromJalali);
            toJalali = normalizeDate(toJalali);
            category = Objects.requireNonNullElse(category, LedgerCategory.ALL);
            page = Math.max(0, page);
            pageSize = Math.max(20, Math.min(500, pageSize));
            if (!fromJalali.isEmpty() && !PersianDate.isValidJalaliDate(fromJalali))
                throw new IllegalArgumentException("تاریخ شروع معتبر را به شکل ۱۴۰۵/۰۸/۰۱ وارد کنید.");
            if (!toJalali.isEmpty() && !PersianDate.isValidJalaliDate(toJalali))
                throw new IllegalArgumentException("تاریخ پایان معتبر را به شکل ۱۴۰۵/۰۸/۳۰ وارد کنید.");
            if (!fromJalali.isEmpty() && !toJalali.isEmpty() && fromJalali.compareTo(toJalali) > 0)
                throw new IllegalArgumentException("تاریخ شروع نباید بعد از تاریخ پایان باشد.");
        }

        public LedgerFilter firstPage(int size) {
            return new LedgerFilter(fromJalali, toJalali, category, 0, size);
        }
    }

    /** A single row from cust_act; debit minus credit advances the account's running balance. */
    public record LedgerEntry(String rowId, String date, String time, String operationCode,
                              String operation, String description, String documentNo,
                              BigDecimal debit, BigDecimal credit, BigDecimal balance,
                              LedgerCategory category) {
        public LedgerEntry {
            rowId = clean(rowId);
            date = clean(date);
            time = clean(time);
            operationCode = clean(operationCode);
            operation = clean(operation);
            description = clean(description);
            documentNo = clean(documentNo);
            debit = zero(debit);
            credit = zero(credit);
            balance = zero(balance);
            category = Objects.requireNonNullElse(category, LedgerCategory.OTHER);
        }
    }

    /** A date/category-filtered report; balances are the full account balance, not only the selected category. */
    public record CustomerLedgerReport(CustomerDirectoryRow customer, List<LedgerEntry> entries,
                                       long totalEntries, BigDecimal openingBalance,
                                       BigDecimal debitTotal, BigDecimal creditTotal,
                                       BigDecimal closingBalance, String fromJalali,
                                       String toJalali, LedgerCategory category,
                                       boolean truncated) {
        public CustomerLedgerReport {
            Objects.requireNonNull(customer, "customer");
            entries = entries == null ? List.of() : List.copyOf(entries);
            totalEntries = Math.max(0, totalEntries);
            openingBalance = zero(openingBalance);
            debitTotal = zero(debitTotal);
            creditTotal = zero(creditTotal);
            closingBalance = zero(closingBalance);
            fromJalali = clean(fromJalali);
            toJalali = clean(toJalali);
            category = Objects.requireNonNullElse(category, LedgerCategory.ALL);
        }
    }

    public record DetailField(String label, String value) {
        public DetailField {
            label = clean(label);
            value = clean(value);
        }
    }

    public record DetailLine(String item, String quantity, String unitPrice, BigDecimal total) {
        public DetailLine {
            item = clean(item);
            quantity = clean(quantity);
            unitPrice = clean(unitPrice);
            total = zero(total);
        }
    }

    /** Read-only linked voucher/invoice detail. Unsupported links still include the complete ledger row. */
    public record TransactionDetails(String title, List<DetailField> fields, List<DetailLine> lines) {
        public TransactionDetails {
            title = clean(title);
            fields = fields == null ? List.of() : List.copyOf(fields);
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    private static String normalizeDate(String value) {
        String date = clean(value).replace('-', '/').replace('.', '/')
                .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
        String[] parts = date.split("/", -1);
        if (parts.length == 3 && parts[0].matches("[0-9]{4}")
                && parts[1].matches("[0-9]{1,2}") && parts[2].matches("[0-9]{1,2}")) {
            try {
                return String.format(java.util.Locale.ROOT, "%04d/%02d/%02d",
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) { }
        }
        return date;
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
