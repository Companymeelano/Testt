package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionMath;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Clearly labelled preview rows for every desktop Manager section.
 *
 * The demo gateway uses this so an operator without a database can still see the exact shape, columns,
 * chips and filters of each report. Nothing here is a real Atiran document: every sample row is derived
 * from a fixed table of preview values and the report itself is marked as preview data.
 */
public final class DemoSectionData {
    private DemoSectionData() { }

    private static final String[] CUSTOMERS = {
            "فروشگاه نمونهٔ بهار", "خشکبار مهرگان (نمونه)", "سوپرمارکت سبزینه (نمونه)",
            "پخش آفتاب (نمونه)", "هایپر ستاره (نمونه)", "بازرگانی پارسا (نمونه)"};
    private static final String[] PRODUCTS = {
            "پستهٔ اکبری ممتاز", "بادام درختی شور", "کشمش سبز قلمی", "فندق خام ممتاز",
            "مخلوط آجیل ویژه", "انجیر خشک استهبان"};
    private static final String[] GROUPS = {"آجیل", "خشکبار", "مخلوط"};
    private static final String[] VISITORS = {"ویزیتور نمونهٔ ۱", "ویزیتور نمونهٔ ۲", "ویزیتور نمونهٔ ۳"};
    private static final String[] BANKS = {"بانک نمونهٔ ملت · ۴۴۲۱", "بانک نمونهٔ ملی · ۱۱۰۷",
            "بانک نمونهٔ صادرات · ۲۲۳۳"};
    private static final String[] ROUTES = {"مسیر نمونهٔ شمال", "مسیر نمونهٔ مرکز", "مسیر نمونهٔ جنوب"};
    private static final String[] USERS = {"مدیر نمونه", "حسابدار نمونه", "انباردار نمونه", "فروشندهٔ نمونه"};
    private static final String[] USER_ROLES = {"مدیر", "حسابدار", "انبار", "فروشنده"};

    /** Sample rows for one report, in the SQL column order of the catalog definition. */
    public static List<SectionRow> rows(VariantSpec spec, SectionFilter filter, String today) {
        String key = spec.sectionId() + "/" + spec.id();
        List<Object[]> raw;
        switch (key) {
            case "cash/daily": raw = cashDaily(today); break;
            case "cash/receipts": raw = cashVouchers(today, 0); break;
            case "cash/payments": raw = cashVouchers(today, 1); break;
            case "cash/banks": raw = cashBanks(); break;
            case "cheques/incoming": raw = cheques(today, true, false, false); break;
            case "cheques/outgoing": raw = cheques(today, false, false, false); break;
            case "cheques/due": raw = cheques(today, true, true, true); break;
            case "cheques/bounced": raw = cheques(today, true, false, true); break;
            case "stock_ledger/stock": raw = stock(); break;
            case "stock_ledger/kardex": raw = kardex(today); break;
            case "stock_ledger/alerts": raw = stockAlerts(today); break;
            case "stock_ledger/dead": raw = stockDead(today); break;
            case "reports/sales_daily": raw = salesDaily(today); break;
            case "reports/sales_monthly": raw = salesMonthly(); break;
            case "reports/top_customers": raw = topCustomers(today); break;
            case "reports/top_products": raw = topProducts(); break;
            case "reports/aging": raw = aging(today); break;
            case "profit/daily": raw = profitDaily(today); break;
            case "profit/monthly": raw = profitMonthly(); break;
            case "profit/by_product": raw = profitByProduct(false); break;
            case "profit/low_margin": raw = profitByProduct(true); break;
            case "profit/costs": raw = costs(today); break;
            case "production/documents": raw = productionDocuments(today); break;
            case "production/formulation": raw = formulation(); break;
            case "production/costs": raw = productionCosts(today); break;
            case "production/count_diff": raw = countDifference(); break;
            case "personnel/visitors": raw = visitors(); break;
            case "personnel/daily": raw = visitorDaily(today); break;
            case "personnel/customers": raw = visitorCustomers(today); break;
            case "personnel/routes": raw = routes(); break;
            case "users/list": raw = users(today); break;
            case "users/roles": raw = roles(); break;
            case "users/logins": raw = logins(today); break;
            default: raw = List.of(); break;
        }
        SectionFilter f = filter == null ? SectionFilter.defaults() : filter;
        List<SectionRow> rows = new ArrayList<>();
        for (Object[] values : raw) {
            SectionRow row = build(spec, values);
            if (matches(row, spec, f)) rows.add(row);
        }
        return rows;
    }

    private static SectionRow build(VariantSpec spec, Object[] values) {
        List<String> keys = SectionSqlKeys.of(spec);
        if (values.length != keys.size())
            throw new IllegalStateException("دادهٔ نمونهٔ «" + spec.sectionId() + "/" + spec.id() + "» "
                    + values.length + " مقدار دارد اما گزارش " + keys.size() + " ستون می‌خواند.");
        List<SectionValue> cells = new ArrayList<>(spec.columns().size());
        int source = 0;
        for (SectionColumn column : spec.columns()) {
            if (spec.isDerived(column.key())) {
                cells.add(new SectionValue("", "", null));
                continue;
            }
            Object raw = source < values.length ? values[source] : "";
            source++;
            if (column.numeric()) cells.add(SectionMath.value(column, null, decimal(raw)));
            else cells.add(SectionMath.value(column, text(raw), null));
        }
        String id = keys.isEmpty() ? "" : cells.get(spec.columnIndex(keys.get(0))).key();
        return new SectionRow(id, cells);
    }

    /** The demo applies the typed search itself; the date window is applied by SectionPost. */
    private static boolean matches(SectionRow row, VariantSpec spec, SectionFilter filter) {
        String query = SectionMath.normalize(filter.query());
        if (query.isEmpty()) return true;
        for (SectionValue value : row.values()) {
            if (value.key().contains(query) || SectionMath.normalize(value.text()).contains(query)) return true;
        }
        return false;
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal amount) return amount;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(text.replace(",", "")); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String daysAgo(String today, int days) {
        return SectionMath.minusDays(today, days);
    }

    // ------------------------------------------------------------------ صندوق

    private static List<Object[]> cashDaily(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] receipts = {48200000, 31500000, 62750000, 27400000, 55100000, 39800000, 44650000};
        int[] payments = {12400000, 8600000, 21300000, 0, 17500000, 9200000, 15800000};
        for (int i = 0; i < receipts.length; i++) {
            rows.add(new Object[]{daysAgo(today, i * 3), 4 + (i % 3), receipts[i], 1 + (i % 3), payments[i]});
        }
        return rows;
    }

    private static List<Object[]> cashVouchers(String today, int direction) {
        List<Object[]> rows = new ArrayList<>();
        String[] methods = {"1", "4", "3", "1", "5", "2"};
        int[] amounts = {12500000, 8400000, 25000000, 3200000, 17800000, 46000000};
        for (int i = 0; i < 6; i++) {
            rows.add(new Object[]{(direction == 0 ? "DR-" : "DP-") + (1400 + i * 7),
                    daysAgo(today, i * 4 + 1), CUSTOMERS[i % CUSTOMERS.length], methods[i], amounts[i],
                    direction == 0 ? "بابت فاکتور فروش نمونه" : "پرداخت به تأمین‌کنندهٔ نمونه"});
        }
        return rows;
    }

    private static List<Object[]> cashBanks() {
        List<Object[]> rows = new ArrayList<>();
        int[] deposits = {184000000, 96500000, 42300000};
        int[] withdrawals = {61000000, 38500000, 12700000};
        int[] depositCounts = {12, 7, 4};
        int[] withdrawalCounts = {5, 3, 2};
        for (int i = 0; i < BANKS.length; i++) {
            rows.add(new Object[]{BANKS[i], depositCounts[i], deposits[i], withdrawalCounts[i], withdrawals[i]});
        }
        return rows;
    }

    // ------------------------------------------------------------------ چک‌ها

    /**
     * Preview cheques. {@code shortShape} matches the collection and bounced reports, which show fewer
     * columns than the full incoming/outgoing lists; the shape has to follow the catalog exactly.
     */
    private static List<Object[]> cheques(String today, boolean incoming, boolean dueOnly, boolean shortShape) {
        List<Object[]> rows = new ArrayList<>();
        int[] amounts = {18500000, 9200000, 42000000, 6750000, 31400000, 12900000};
        String[] inStatus = {"1", "2", "3", "1", "5", "2"};
        String[] outStatus = {"1", "2", "1", "0", "1", "2"};
        for (int i = 0; i < 6; i++) {
            String due = dueOnly ? SectionMath.plusDays(today, i * 5 + 1) : daysAgo(today, -(i * 9 - 20));
            String number = incoming ? "۱۴۰" + i + "۵" : "۲۲۰" + i + "۴";
            String sayad = "SAYAD-" + ((incoming ? 440000 : 770000) + i * (incoming ? 137 : 91));
            String party = CUSTOMERS[(i + (incoming ? 0 : 2)) % CUSTOMERS.length];
            String bank = BANKS[(i + (incoming ? 0 : 1)) % BANKS.length];
            String status = incoming ? inStatus[i] : outStatus[i];
            String issued = daysAgo(today, (incoming ? 30 : 25) + i * (incoming ? 6 : 5));
            if (shortShape) {
                rows.add(new Object[]{number, party, bank, amounts[i], due, status});
            } else if (incoming) {
                rows.add(new Object[]{number, sayad, party, bank, amounts[i], issued, due, status,
                        i == 4 ? "t" : "f"});
            } else {
                rows.add(new Object[]{number, sayad, party, bank, amounts[i], issued, due, status});
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ انبار

    private static List<Object[]> stock() {
        List<Object[]> rows = new ArrayList<>();
        int[] packs = {42, 18, 0, 126, 7, 3};
        int[] loose = {5, 0, 0, 12, 3, 1};
        int[] prices = {1650000, 880000, 0, 540000, 1120000, 395000};
        for (int i = 0; i < PRODUCTS.length; i++) {
            BigDecimal value = BigDecimal.valueOf((long) packs[i] * 10 + loose[i]).multiply(BigDecimal.valueOf(prices[i]));
            rows.add(new Object[]{7101 + i, PRODUCTS[i], GROUPS[i % GROUPS.length], "کیلوگرم",
                    packs[i], loose[i], packs[i] * 10 + loose[i], prices[i], value});
        }
        return rows;
    }

    private static List<Object[]> kardex(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] inQty = {120, 0, 60, 0, 240, 0};
        int[] outQty = {0, 45, 0, 80, 0, 15};
        String[] ops = {"خرید", "فروش", "ورود انبار", "حوالهٔ خروج", "تولید", "برگشت از مشتری"};
        for (int i = 0; i < 6; i++) {
            rows.add(new Object[]{daysAgo(today, i * 5 + 1), 7101 + (i % PRODUCTS.length),
                    PRODUCTS[i % PRODUCTS.length], "SF-" + (2200 + i * 11), ops[i],
                    i % 2 == 0 ? "انبار مرکزی (نمونه)" : "انبار فروش (نمونه)", inQty[i], outQty[i]});
        }
        return rows;
    }

    private static List<Object[]> stockAlerts(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] onHand = {0, 4, 9, -3, 12};
        int[] points = {20, 15, 25, 10, 30};
        for (int i = 0; i < 5; i++) {
            rows.add(new Object[]{7102 + i, PRODUCTS[(i + 1) % PRODUCTS.length], GROUPS[i % GROUPS.length],
                    onHand[i], points[i], Math.max(0, points[i] - onHand[i]), daysAgo(today, 12 + i * 21)});
        }
        return rows;
    }

    private static List<Object[]> stockDead(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] onHand = {38, 12, 64, 5};
        int[] prices = {1650000, 540000, 395000, 1120000};
        for (int i = 0; i < 4; i++) {
            rows.add(new Object[]{7104 + i, PRODUCTS[(i + 3) % PRODUCTS.length], GROUPS[i % GROUPS.length],
                    onHand[i], BigDecimal.valueOf((long) onHand[i] * prices[i]), daysAgo(today, 220 + i * 40)});
        }
        return rows;
    }

    // ------------------------------------------------------------------ گزارش‌ها

    private static List<Object[]> salesDaily(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] gross = {62400000, 48100000, 71350000, 39800000, 55250000, 66900000, 43700000};
        for (int i = 0; i < gross.length; i++) {
            int discount = gross[i] / 40;
            int tax = gross[i] / 11;
            int collected = gross[i] - discount - (i % 3 == 0 ? gross[i] / 5 : 0);
            rows.add(new Object[]{daysAgo(today, i * 2), 3 + (i % 5), gross[i] - discount, discount, tax, collected});
        }
        return rows;
    }

    private static List<Object[]> salesMonthly() {
        List<Object[]> rows = new ArrayList<>();
        String[] months = {"1405/07", "1405/06", "1405/05", "1405/04", "1405/03", "1405/02"};
        int[] gross = {412000000, 388500000, 455300000, 301200000, 276400000, 344900000};
        for (int i = 0; i < months.length; i++) {
            int discount = gross[i] / 45;
            rows.add(new Object[]{months[i], 42 - i * 3, gross[i] - discount, discount, gross[i] - gross[i] / 6});
        }
        return rows;
    }

    private static List<Object[]> topCustomers(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] gross = {184500000, 142300000, 98600000, 76400000, 51200000, 32800000};
        int[] balance = {12500000, 0, 4860000, -1700000, 8300000, 0};
        for (int i = 0; i < CUSTOMERS.length; i++) {
            rows.add(new Object[]{1101 + i, CUSTOMERS[i], 14 - i * 2, gross[i], gross[i] - gross[i] / 7,
                    balance[i], daysAgo(today, i * 6 + 1)});
        }
        return rows;
    }

    private static List<Object[]> topProducts() {
        List<Object[]> rows = new ArrayList<>();
        int[] qty = {820, 640, 510, 430, 380, 210};
        int[] prices = {1840000, 920000, 565000, 1175000, 1320000, 395000};
        for (int i = 0; i < PRODUCTS.length; i++) {
            BigDecimal gross = BigDecimal.valueOf((long) qty[i] * prices[i]);
            BigDecimal cogs = gross.multiply(new BigDecimal("0.74")).setScale(0, java.math.RoundingMode.HALF_UP);
            rows.add(new Object[]{7101 + i, PRODUCTS[i], GROUPS[i % GROUPS.length], qty[i], 18 - i * 2, gross, cogs});
        }
        return rows;
    }

    private static List<Object[]> aging(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] balance = {48600000, 22400000, 12500000, 8300000, 4100000, 1900000};
        int[] ages = {5, 42, 96, 150, 240, 12};
        for (int i = 0; i < balance.length; i++) {
            rows.add(new Object[]{1101 + i, CUSTOMERS[i % CUSTOMERS.length], balance[i], 6 - (i % 4),
                    daysAgo(today, ages[i])});
        }
        return rows;
    }

    // ------------------------------------------------------------------ سود

    private static List<Object[]> profitDaily(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] net = {58200000, 44800000, 67300000, 36100000, 51900000, 62400000, 40700000};
        for (int i = 0; i < net.length; i++) {
            int cogs = (int) (net[i] * 0.72);
            rows.add(new Object[]{daysAgo(today, i * 2), 3 + (i % 5), net[i], cogs});
        }
        return rows;
    }

    private static List<Object[]> profitMonthly() {
        List<Object[]> rows = new ArrayList<>();
        String[] months = {"1405/07", "1405/06", "1405/05", "1405/04", "1405/03", "1405/02"};
        int[] net = {398000000, 371200000, 440800000, 288600000, 265100000, 331400000};
        for (int i = 0; i < months.length; i++) {
            rows.add(new Object[]{months[i], 42 - i * 3, net[i], (int) (net[i] * (0.70 + i * 0.01))});
        }
        return rows;
    }

    private static List<Object[]> profitByProduct(boolean lowMargin) {
        List<Object[]> rows = new ArrayList<>();
        int[] qty = {820, 640, 510, 430, 380, 210};
        int[] prices = {1840000, 920000, 565000, 1175000, 1320000, 395000};
        double[] costRatio = {0.62, 0.94, 0.71, 0.97, 0.68, 0.88};
        for (int i = 0; i < PRODUCTS.length; i++) {
            BigDecimal net = BigDecimal.valueOf((long) qty[i] * prices[i]);
            BigDecimal cogs = net.multiply(BigDecimal.valueOf(costRatio[i])).setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal margin = net.signum() == 0 ? BigDecimal.ZERO
                    : net.subtract(cogs).multiply(new BigDecimal("100")).divide(net, 2, java.math.RoundingMode.HALF_UP);
            if (lowMargin && margin.compareTo(new BigDecimal("10")) > 0) continue;
            rows.add(lowMargin
                    ? new Object[]{7101 + i, PRODUCTS[i], qty[i], net, cogs}
                    : new Object[]{7101 + i, PRODUCTS[i], GROUPS[i % GROUPS.length], qty[i], net, cogs});
        }
        return rows;
    }

    private static List<Object[]> costs(String today) {
        List<Object[]> rows = new ArrayList<>();
        String[] kinds = {"هزینهٔ جانبی", "هزینهٔ غیرمستقیم", "کسری انبار", "هزینهٔ جانبی"};
        String[] titles = {"کرایهٔ حمل نمونه", "اجارهٔ انبار نمونه", "کسری شمارش نمونه", "بسته‌بندی نمونه"};
        int[] amounts = {4200000, 18500000, 1250000, 3600000};
        for (int i = 0; i < 4; i++) {
            rows.add(new Object[]{daysAgo(today, i * 11 + 2), kinds[i], titles[i], "EC-" + (310 + i * 5), amounts[i]});
        }
        return rows;
    }

    // ------------------------------------------------------------------ تولید

    private static List<Object[]> productionDocuments(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] qty = {120, 80, 240, 60};
        int[] cost = {184000000, 96500000, 268000000, 71200000};
        for (int i = 0; i < 4; i++) {
            rows.add(new Object[]{"PR-" + (501 + i * 3), daysAgo(today, i * 14 + 3),
                    PRODUCTS[i % PRODUCTS.length], qty[i], cost[i],
                    BigDecimal.valueOf(cost[i]).divide(BigDecimal.valueOf(qty[i]), 2, java.math.RoundingMode.HALF_UP),
                    "سند تولید نمونهٔ بدون سری"});
        }
        return rows;
    }

    private static List<Object[]> formulation() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int qty = 20 + i * 5;
            int price = 380000 + i * 120000;
            rows.add(new Object[]{"مخلوط آجیل ویژه", PRODUCTS[i % PRODUCTS.length], qty, "کیلوگرم",
                    price, BigDecimal.valueOf((long) qty * price)});
        }
        return rows;
    }

    private static List<Object[]> productionCosts(String today) {
        List<Object[]> rows = new ArrayList<>();
        String[] titles = {"دستمزد تولید نمونه", "سربار کارگاه نمونه", "بسته‌بندی تولید نمونه"};
        int[] amounts = {12400000, 8600000, 3200000};
        for (int i = 0; i < 3; i++) {
            rows.add(new Object[]{"PR-" + (501 + i * 3), daysAgo(today, i * 14 + 3), titles[i], amounts[i],
                    "سرفصل بهای تولید نمونه"});
        }
        return rows;
    }

    private static List<Object[]> countDifference() {
        List<Object[]> rows = new ArrayList<>();
        int[] system = {420, 180, 640, 95};
        int[] counted = {415, 182, 610, 95};
        int[] prices = {1650000, 880000, 395000, 1120000};
        for (int i = 0; i < 4; i++) {
            int diff = counted[i] - system[i];
            if (diff == 0) continue;
            rows.add(new Object[]{7101 + i, PRODUCTS[i], "انبار مرکزی (نمونه)", system[i], counted[i],
                    BigDecimal.valueOf(Math.abs((long) diff * prices[i]))});
        }
        return rows;
    }

    // ------------------------------------------------------------------ پرسنل

    private static List<Object[]> visitors() {
        List<Object[]> rows = new ArrayList<>();
        int[] sales = {284000000, 196500000, 142800000};
        int[] goals = {300000000, 180000000, 200000000};
        for (int i = 0; i < VISITORS.length; i++) {
            rows.add(new Object[]{31 + i, VISITORS[i], "۰۹۱۲•••" + (1000 + i * 111), i == 2 ? "f" : "t",
                    28 - i * 6, sales[i], sales[i] - sales[i] / (6 + i), 14 - i * 3, goals[i]});
        }
        return rows;
    }

    private static List<Object[]> visitorDaily(String today) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(new Object[]{daysAgo(today, i * 3 + 1), VISITORS[i % VISITORS.length], 2 + (i % 4),
                    18400000 + i * 4300000, 12600000 + i * 2900000, 3 + (i % 3)});
        }
        return rows;
    }

    private static List<Object[]> visitorCustomers(String today) {
        List<Object[]> rows = new ArrayList<>();
        int[] sales = {84500000, 62300000, 41800000, 28600000, 19400000, 12800000};
        int[] balance = {12500000, 0, 4860000, -1700000, 8300000, 0};
        for (int i = 0; i < CUSTOMERS.length; i++) {
            rows.add(new Object[]{1101 + i, CUSTOMERS[i], VISITORS[i % VISITORS.length], 12 - i,
                    sales[i], balance[i], daysAgo(today, i * 7 + 2)});
        }
        return rows;
    }

    private static List<Object[]> routes() {
        List<Object[]> rows = new ArrayList<>();
        int[] customers = {86, 64, 41};
        int[] sales = {312000000, 218500000, 142300000};
        for (int i = 0; i < ROUTES.length; i++) {
            rows.add(new Object[]{ROUTES[i], "منطقهٔ نمونهٔ " + (i + 1), customers[i], customers[i] - 12 - i * 4,
                    48 - i * 11, sales[i]});
        }
        return rows;
    }

    // ------------------------------------------------------------------ کاربران

    private static List<Object[]> users(String today) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < USERS.length; i++) {
            rows.add(new Object[]{101 + i, USERS[i], USER_ROLES[i], i == 3 ? "f" : "t",
                    "۰۲۱•••" + (4400 + i * 17), daysAgo(today, i) + " 08:" + (12 + i * 7), 8 - i * 2});
        }
        return rows;
    }

    private static List<Object[]> roles() {
        List<Object[]> rows = new ArrayList<>();
        int[] counts = {1, 1, 1, 3, 1, 1};
        String[] names = {"مدیر", "حسابدار", "انبار", "فروشنده", "ویزیتور", "مودیان"};
        for (int i = 0; i < names.length; i++) {
            rows.add(new Object[]{i + 1, names[i], counts[i]});
        }
        return rows;
    }

    private static List<Object[]> logins(String today) {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(new Object[]{USERS[i % USERS.length], daysAgo(today, i) + " 07:" + (18 + i * 6),
                    "DESKTOP-SAMPLE" + (i + 1), "192.0.2." + (10 + i), i == 0 ? "ورود موفق نمونه" : ""});
        }
        return rows;
    }

    /** Column keys the SQL (and therefore the demo) actually supplies, i.e. excluding derived columns. */
    static final class SectionSqlKeys {
        private SectionSqlKeys() { }

        static List<String> of(VariantSpec spec) {
            List<String> keys = new ArrayList<>();
            for (SectionColumn column : spec.columns()) {
                if (!spec.isDerived(column.key())) keys.add(column.key());
            }
            return keys;
        }
    }

    /** Preview label appended to the report note, so sample data can never be mistaken for Atiran. */
    public static String previewLabel(String sectionId) {
        return "پیش‌نمایش «" + SectionCatalog.titleOf(sectionId == null ? "" : sectionId.toLowerCase(Locale.ROOT))
                + "» با دادهٔ نمونهٔ محلی";
    }
}
