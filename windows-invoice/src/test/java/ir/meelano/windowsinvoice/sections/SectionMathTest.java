package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog.Derivation;
import ir.meelano.windowsinvoice.sections.SectionCatalog.Kpi;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The maths and formatting shared by the live gateway, the preview gateway and the section window.
 * A mistake here would show the operator a wrong total, a wrong due state or a wrong margin, so each
 * rule is pinned: Jalali conversion round-trips against {@link PersianDate}, unknown Atiran codes stay
 * visible, and derived cells tolerate missing source columns.
 */
class SectionMathTest {

    /** Anchored on the app's own calendar, so the assertions never depend on a hardcoded mapping. */
    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 25);
    private static final String TODAY = PersianDate.fromGregorian(ANCHOR);

    // ------------------------------------------------------------------ numbers and digits

    @Test void amountsAreGroupedWithPersianDigitsAndSeparators() {
        assertEquals("۱٬۲۵۰٬۰۰۰", SectionMath.money(new BigDecimal("1250000")));
        assertEquals("۱٬۵۰۰٫۵", SectionMath.money(new BigDecimal("1500.50")));
        assertEquals("-۴٬۸۶۰٬۰۰۰", SectionMath.money(new BigDecimal("-4860000")));
        assertEquals("۰", SectionMath.money(null));
        assertEquals("۱٬۲۵۰", SectionMath.count(1250));
        assertEquals("۱۲٫۵٪", SectionMath.percent(new BigDecimal("12.5")));
        assertEquals("—", SectionMath.percent(null));
        // The display text always maps back to the same machine-readable number.
        assertEquals("1250000", SectionMath.latinDigits(SectionMath.money(new BigDecimal("1250000")))
                .replace(",", "").replace("٬", ""));
    }

    @Test void persianAndArabicDigitsAreNormalizedBothWays() {
        assertEquals("1405/08/03", SectionMath.latinDigits("۱۴۰۵/۰۸/۰۳"));
        assertEquals("1405/08/03", SectionMath.latinDigits("١٤٠٥/٠٨/٠٣"));
        assertEquals("۱۴۰۵/۰۸/۰۳", SectionMath.digits("1405/08/03"));
        assertEquals("یک", SectionMath.normalize("  يك  "));
        assertEquals("", SectionMath.normalize(null));
        assertEquals("", SectionMath.digits(null));
    }

    // ------------------------------------------------------------------ Jalali dates

    @Test void typedDatesAreNormalizedToZeroPaddedJalali() {
        assertEquals("1405/08/03", SectionMath.normalizeJalali("۱۴۰۵-۸-۳"));
        assertEquals("1405/08/03", SectionMath.normalizeJalali("1405.8.3"));
        assertEquals("1405/08/03", SectionMath.normalizeJalali(" 1405/8/3 "));
        assertEquals("1405/8", SectionMath.normalizeJalali("1405/8"));
        assertEquals(TODAY, SectionMath.normalizeJalali(SectionMath.digits(TODAY)));
        assertEquals(TODAY, SectionMath.normalizeJalali(oneDigit(TODAY)));
        assertEquals("", SectionMath.normalizeJalali(null));
        // Anything that is not a three-part date stays unusable, so the filter rejects it.
        assertFalse(PersianDate.isValidJalaliDate(SectionMath.normalizeJalali("not-a-date")));
    }

    @Test void jalaliConversionRoundTripsThroughPersianDate() {
        for (int offset = -800; offset <= 800; offset += 7) {
            LocalDate gregorian = ANCHOR.plusDays(offset);
            String jalali = PersianDate.fromGregorian(gregorian);
            assertEquals(gregorian, SectionMath.jalaliToGregorian(jalali),
                    jalali + " did not convert back to " + gregorian);
        }
        assertEquals(ANCHOR, SectionMath.jalaliToGregorian(TODAY));
        assertNull(SectionMath.jalaliToGregorian("1405/13/01"));
        assertNull(SectionMath.jalaliToGregorian("1405/00/10"));
        assertNull(SectionMath.jalaliToGregorian("not-a-date"));
        assertNull(SectionMath.jalaliToGregorian(""));
    }

    @Test void gregorianTextFromAtiranIsShownAsJalali() {
        // A stored clock is kept: a login or voucher time is part of what the operator audits.
        assertEquals(TODAY + " 14:32", SectionMath.jalaliText("2026-10-25 14:32:10.000"));
        assertEquals(TODAY, SectionMath.jalaliText("2026-10-25"));
        assertEquals(TODAY, SectionMath.jalaliText(PersianDate.fromGregorian(ANCHOR)));
        assertEquals(TODAY, SectionMath.jalaliText(oneDigit(TODAY)));
        assertEquals(TODAY, SectionMath.jalaliText(SectionMath.digits(TODAY)));
        assertEquals("", SectionMath.jalaliText(""));
        assertEquals("", SectionMath.jalaliText(null));
    }

    @Test void dayDistanceAndRangeChecksUseWholeDays() {
        String tenDaysAgo = SectionMath.minusDays(TODAY, 10);
        assertEquals(0, SectionMath.daysBetween(TODAY, TODAY).intValue());
        assertEquals(10, SectionMath.daysBetween(tenDaysAgo, TODAY).intValue());
        assertEquals(-10, SectionMath.daysBetween(TODAY, tenDaysAgo).intValue());
        assertNull(SectionMath.daysBetween("", TODAY));
        String from = SectionMath.minusDays(TODAY, 5);
        String to = SectionMath.plusDays(TODAY, 5);
        assertTrue(SectionMath.withinRange(TODAY, from, to));
        assertTrue(SectionMath.withinRange(from, from, to));
        assertTrue(SectionMath.withinRange(to, from, to));
        assertFalse(SectionMath.withinRange(SectionMath.minusDays(TODAY, 6), from, to));
        assertFalse(SectionMath.withinRange(SectionMath.plusDays(TODAY, 6), from, to));
        // Open bounds keep every dated row; an empty date only survives an unfiltered report.
        assertTrue(SectionMath.withinRange(TODAY, "", ""));
        assertFalse(SectionMath.withinRange("", from, ""));
        assertTrue(SectionMath.withinRange("", "", ""));
    }

    // ------------------------------------------------------------------ Atiran value mappings

    @Test void chequeAndSettlementCodesBecomePersianLabels() {
        assertEquals("نزد صندوق", SectionMath.mapValue("cheque_in", "1"));
        assertEquals("نزد بانک", SectionMath.mapValue("cheque_in", "۲"));
        assertEquals("وصول‌شده", SectionMath.mapValue("cheque_in", "3"));
        assertEquals("استرداد", SectionMath.mapValue("cheque_in", "4"));
        assertEquals("خرج‌شده", SectionMath.mapValue("cheque_in", "5"));
        assertEquals("سفید", SectionMath.mapValue("cheque_out", "0"));
        assertEquals("جاری", SectionMath.mapValue("cheque_out", "1"));
        assertEquals("پاس‌شده", SectionMath.mapValue("cheque_out", "2"));
        assertEquals("نقد", SectionMath.mapValue("settlement", "1"));
        assertEquals("چک", SectionMath.mapValue("settlement", "3"));
        assertEquals("کارت/حواله", SectionMath.mapValue("settlement", "4"));
        assertEquals("ترکیبی", SectionMath.mapValue("settlement", "5"));
        assertEquals("نامشخص", SectionMath.mapValue("settlement", "0"));
        assertEquals("نامشخص", SectionMath.mapValue("settlement", ""));
    }

    @Test void unknownCodesStayVisibleInsteadOfBeingDropped() {
        assertEquals("سایر (۹)", SectionMath.mapValue("cheque_in", "9"));
        assertEquals("سایر (۴۴)", SectionMath.mapValue("cheque_out", "44"));
        assertEquals("برگشتی", SectionMath.mapValue("bounced", "T"));
        assertEquals("برگشتی", SectionMath.mapValue("bounced", "true"));
        assertEquals("عادی", SectionMath.mapValue("bounced", "0"));
        assertEquals("عادی", SectionMath.mapValue("bounced", ""));
        assertEquals("فعال", SectionMath.mapValue("active", "Y"));
        assertEquals("غیرفعال", SectionMath.mapValue("active", "f"));
        assertEquals("بله", SectionMath.mapValue("yes_no", "1"));
        assertEquals("خیر", SectionMath.mapValue("yes_no", "n"));
        assertEquals("اتمام", SectionMath.mapValue("stock_state", "0"));
        assertEquals("کسری", SectionMath.mapValue("stock_state", "-4"));
        assertEquals("موجود", SectionMath.mapValue("stock_state", "12"));
        // An unmapped mapping is passed through unchanged, never blanked.
        assertEquals("raw", SectionMath.mapValue("", "raw"));
        assertEquals("raw", SectionMath.mapValue(null, "raw"));
    }

    // ------------------------------------------------------------------ cell construction

    @Test void cellsCarryDisplayTextSortKeyAndAmount() {
        SectionValue money = SectionMath.value(SectionColumn.money("amount", "مبلغ", 1), null, new BigDecimal("1500.50"));
        assertEquals("۱٬۵۰۰٫۵", money.text());
        assertEquals("1500.5", money.key());
        assertEquals(new BigDecimal("1500.50"), money.amount());

        SectionValue quantity = SectionMath.value(SectionColumn.number("qty", "تعداد", 1), null, new BigDecimal("12.5"));
        assertEquals("۱۲٫۵", quantity.text());
        assertEquals(new BigDecimal("12.5"), quantity.amount());

        SectionValue date = SectionMath.value(SectionColumn.date("date", "تاریخ", 1), "2026-10-25", null);
        assertEquals(SectionMath.digits(TODAY), date.text());
        assertEquals(TODAY, date.key());
        SectionValue stamped = SectionMath.value(SectionColumn.date("at", "زمان", 1), "2026-10-25 08:05:09", null);
        assertEquals(SectionMath.digits(TODAY) + " ۰۸:۰۵", stamped.text());
        assertTrue(stamped.key().startsWith(TODAY));

        SectionValue status = SectionMath.value(SectionColumn.status("state", "وضعیت", 1, "active"), "t", null);
        assertEquals("فعال", status.text());
        assertEquals("فعال", status.key());

        SectionValue text = SectionMath.value(SectionColumn.text("name", "نام", 1), "  فروشگاه   بهار ", null);
        assertEquals("فروشگاه بهار", text.text());

        SectionValue blank = SectionMath.value(SectionColumn.text("name", "نام", 1), "", null);
        assertEquals("—", blank.text());
        assertEquals("", blank.key());
        // An empty cell shows the placeholder and still counts as blank, like the shared EMPTY value.
        assertTrue(blank.isBlank());
        assertTrue(SectionModels.EMPTY.isBlank());
        assertFalse(money.isBlank());
    }

    // ------------------------------------------------------------------ derived columns

    @Test void derivedMoneyMarginRateAndStockCells() {
        Map<String, SectionValue> row = new HashMap<>();
        row.put("receipts", new SectionValue("", "", new BigDecimal("8400000")));
        row.put("payments", new SectionValue("", "", new BigDecimal("2600000")));
        row.put("net", new SectionValue("", "", new BigDecimal("1000000")));
        row.put("cogs", new SectionValue("", "", new BigDecimal("750000")));
        row.put("collected", new SectionValue("", "", new BigDecimal("250000")));
        row.put("gross", new SectionValue("", "", new BigDecimal("1000000")));

        SectionValue net = SectionMath.derive(Derivation.NET_AMOUNT, row, List.of("receipts", "payments"), null, TODAY);
        assertEquals("۵٬۸۰۰٬۰۰۰", net.text());
        assertEquals(new BigDecimal("5800000"), net.amount());

        SectionValue margin = SectionMath.derive(Derivation.MARGIN_PERCENT, row, List.of("net", "cogs"), null, TODAY);
        assertEquals("۲۵٪", margin.text());
        assertEquals(new BigDecimal("25.00"), margin.amount());

        SectionValue rate = SectionMath.derive(Derivation.RATE_PERCENT, row, List.of("collected", "gross"), null, TODAY);
        assertEquals("۲۵٪", rate.text());

        // A missing source column never breaks the row: the value is treated as zero.
        SectionValue missing = SectionMath.derive(Derivation.NET_AMOUNT, row, List.of("nope", "payments"), null, TODAY);
        assertEquals(new BigDecimal("-2600000"), missing.amount());
        // Zero turnover reports "—" rather than an infinite or zero margin.
        assertEquals("—", SectionMath.derive(Derivation.MARGIN_PERCENT, new HashMap<>(),
                List.of("net", "cogs"), null, TODAY).text());
        assertEquals(SectionModels.EMPTY, SectionMath.derive(Derivation.NONE, row, List.of("net"), null, TODAY));
    }

    @Test void stockStateUsesTheReorderPointOnlyWhenKnown() {
        assertEquals("اتمام", SectionMath.stockState(BigDecimal.ZERO));
        assertEquals("کسری", SectionMath.stockState(new BigDecimal("-3")));
        assertEquals("رو به اتمام", SectionMath.stockState(BigDecimal.ONE));
        assertEquals("موجود", SectionMath.stockState(new BigDecimal("40")));
        assertEquals("نیاز به سفارش", SectionMath.stockStateWithPoint(new BigDecimal("8"), new BigDecimal("10")));
        assertEquals("رو به اتمام", SectionMath.stockStateWithPoint(new BigDecimal("12"), new BigDecimal("10")));
        assertEquals("موجود", SectionMath.stockStateWithPoint(new BigDecimal("400"), new BigDecimal("10")));
        // No reorder point: fall back to the plain quantity rule instead of an invented threshold.
        assertEquals("اتمام", SectionMath.stockStateWithPoint(BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(SectionMath.needsReorder(new BigDecimal("8"), new BigDecimal("10")));
        assertTrue(SectionMath.needsReorder(new BigDecimal("-1"), BigDecimal.ZERO));
        assertFalse(SectionMath.needsReorder(new BigDecimal("400"), new BigDecimal("10")));

        Map<String, SectionValue> row = new HashMap<>();
        row.put("total", new SectionValue("", "", BigDecimal.ZERO));
        row.put("point", new SectionValue("", "", new BigDecimal("10")));
        assertEquals("نیاز به سفارش",
                SectionMath.derive(Derivation.STOCK_STATE, row, List.of("total", "point"), null, TODAY).text());
        Map<String, SectionValue> noPoint = new HashMap<>();
        noPoint.put("total", new SectionValue("", "", BigDecimal.ZERO));
        assertEquals("اتمام",
                SectionMath.derive(Derivation.STOCK_STATE, noPoint, List.of("total"), null, TODAY).text());
    }

    @Test void dateDerivedCellsAgeingDueStateAndDaysAgo() {
        SectionValue ageing = SectionMath.derive(Derivation.AGEING_BUCKET,
                Map.of("last", new SectionValue("", SectionMath.minusDays(TODAY, 44), null)),
                List.of("last"), null, TODAY);
        assertEquals("۳۱ تا ۶۰ روز", ageing.text());
        assertEquals(44, ageing.amount().intValue());

        assertEquals("جاری", SectionMath.ageingBucket(TODAY, TODAY).text());
        assertEquals("۱ تا ۳۰ روز", SectionMath.ageingBucket(SectionMath.minusDays(TODAY, 14), TODAY).text());
        assertEquals("۳۱ تا ۶۰ روز", SectionMath.ageingBucket(SectionMath.minusDays(TODAY, 45), TODAY).text());
        assertEquals("۶۱ تا ۹۰ روز", SectionMath.ageingBucket(SectionMath.minusDays(TODAY, 75), TODAY).text());
        assertEquals("۹۱ تا ۱۸۰ روز", SectionMath.ageingBucket(SectionMath.minusDays(TODAY, 156), TODAY).text());
        assertEquals("بیش از ۱۸۰ روز", SectionMath.ageingBucket(SectionMath.minusDays(TODAY, 400), TODAY).text());
        assertEquals("نامشخص", SectionMath.ageingBucket("", TODAY).text());
        // A future last-activity date is reported as the current bucket, never as a negative age.
        assertEquals("جاری", SectionMath.ageingBucket(SectionMath.plusDays(TODAY, 3), TODAY).text());

        assertEquals("سررسید گذشته (۱۰ روز)", SectionMath.dueState(SectionMath.minusDays(TODAY, 10), TODAY).text());
        assertEquals("سررسید امروز", SectionMath.dueState(TODAY, TODAY).text());
        assertEquals("تا ۷ روز (۵ روز)", SectionMath.dueState(SectionMath.plusDays(TODAY, 5), TODAY).text());
        assertEquals("سررسید آینده (۴۰ روز)", SectionMath.dueState(SectionMath.plusDays(TODAY, 40), TODAY).text());
        assertEquals("نامشخص", SectionMath.dueState("", TODAY).text());

        SectionValue daysAgo = SectionMath.derive(Derivation.DAYS_AGO,
                Map.of("last", new SectionValue("", SectionMath.minusDays(TODAY, 10), null)),
                List.of("last"), null, TODAY);
        assertEquals("۱۰ روز", daysAgo.text());
        assertEquals("10", daysAgo.key());
        assertEquals("نامشخص", SectionMath.daysAgo("", TODAY).text());
        assertEquals("۰ روز", SectionMath.daysAgo(SectionMath.plusDays(TODAY, 4), TODAY).text());
    }

    @Test void accessColumnsAreDerivedFromTheAtiranRole() {
        Map<String, SectionValue> user = new HashMap<>();
        user.put("role", new SectionValue("حسابدار", "حسابدار", null));
        user.put("name", new SectionValue("user1", "user1", null));
        SectionValue desktopRole = SectionMath.derive(Derivation.DESKTOP_ROLE, user, List.of("role", "name"), null, TODAY);
        assertEquals("حسابدار", desktopRole.text());
        assertEquals("accountant", desktopRole.key());

        Map<String, SectionValue> withRole = new HashMap<>(user);
        withRole.put("desktop_role", desktopRole);
        SectionValue menus = SectionMath.derive(Derivation.MENU_COUNT, withRole, List.of("desktop_role"), null, TODAY);
        assertEquals(WindowsRoleMenus.idsFor("accountant").size(), menus.amount().intValue());
        assertEquals(Integer.toString(WindowsRoleMenus.idsFor("accountant").size()), menus.key());

        SectionValue scope = SectionMath.derive(Derivation.MENU_SCOPE, withRole, List.of("desktop_role"), null, TODAY);
        assertTrue(scope.text().contains("صندوق و دریافتی‌ها"));
        assertFalse(scope.text().contains("مدیریت کاربران و دسترسی"));
        assertEquals("accountant", scope.key());

        // An unrecognized desktop role falls back to the seller scope instead of an empty cell.
        Map<String, SectionValue> unknown = new HashMap<>();
        unknown.put("desktop_role", new SectionValue("کارمند", "not-a-role", null));
        assertEquals(Integer.toString(WindowsRoleMenus.idsFor("seller").size()),
                SectionMath.derive(Derivation.MENU_COUNT, unknown, List.of("desktop_role"), null, TODAY).key());
        // A missing role column is tolerated.
        assertEquals(Integer.toString(WindowsRoleMenus.idsFor("seller").size()),
                SectionMath.derive(Derivation.MENU_COUNT, new HashMap<>(), List.of("desktop_role"), null, TODAY).key());
    }

    // ------------------------------------------------------------------ KPI chips

    @Test void kpiChipsSummarizeTheFilteredRows() {
        List<Map<String, SectionValue>> rows = List.of(
                kpiRow("1000", "700", "800", "برگشتی"),
                kpiRow("2000", "1900", "1500", "نزد بانک"),
                kpiRow("0", "0", "0", "وصول‌شده"));
        List<SectionKpi> kpis = SectionMath.kpis(List.of(
                Kpi.count("تعداد سند"),
                Kpi.sum("جمع مبلغ", "amount"),
                Kpi.avg("میانگین", "amount"),
                Kpi.max("بیشترین", "amount"),
                Kpi.diff("خالص", "amount", "cost"),
                Kpi.ratio("نرخ وصول٪", "collected", "amount"),
                Kpi.margin("حاشیهٔ سود٪", "amount", "cost"),
                Kpi.matches("برگشتی", "status", "برگشتی"),
                Kpi.below("کم‌سود", "margin", "با آستانه")),
                rows, 7, new BigDecimal("10"));

        assertEquals(9, kpis.size());
        assertEquals("۷", kpis.get(0).value());                       // COUNT uses the filtered total, not the page
        assertEquals("۳٬۰۰۰", kpis.get(1).value());                  // SUM
        assertEquals("۱٬۰۰۰", kpis.get(2).value());                  // AVG
        assertEquals("۲٬۰۰۰", kpis.get(3).value());                  // MAX
        assertEquals("۴۰۰", kpis.get(4).value());                    // SUM_DIFF 3000 − 2600
        assertEquals("۷۶٫۷٪", kpis.get(5).value());                  // 2300 / 3000
        assertEquals("۱۳٫۳٪", kpis.get(6).value());                  // (3000 − 2600) / 3000
        assertEquals("۱", kpis.get(7).value());                      // COUNT_MATCHES
        assertEquals("۲", kpis.get(8).value());                      // COUNT_BELOW: 5 % and the zero-turnover row
        assertEquals("با آستانه", kpis.get(8).hint());
    }

    @Test void kpisWithoutRowsShowADashInsteadOfZero() {
        List<SectionKpi> kpis = SectionMath.kpis(List.of(
                Kpi.count("تعداد"), Kpi.avg("میانگین", "amount"), Kpi.max("بیشترین", "amount"),
                Kpi.sum("جمع", "amount"), Kpi.margin("حاشیه٪", "amount", "cost")), List.of(), 0, null);
        assertEquals("۰", kpis.get(0).value());
        assertEquals("—", kpis.get(1).value());
        assertEquals("—", kpis.get(2).value());
        assertEquals("۰", kpis.get(3).value());
        assertEquals("—", kpis.get(4).value());
        assertEquals(List.of(), SectionMath.kpis(null, List.of(), 0, null));
    }

    private static Map<String, SectionValue> kpiRow(String amount, String cost, String collected, String status) {
        Map<String, SectionValue> row = new HashMap<>();
        row.put("amount", new SectionValue("", amount, new BigDecimal(amount)));
        row.put("cost", new SectionValue("", cost, new BigDecimal(cost)));
        row.put("collected", new SectionValue("", collected, new BigDecimal(collected)));
        row.put("status", new SectionValue(status, status, null));
        row.put("margin", new SectionValue("", "", SectionMath.marginRate(new BigDecimal(amount), new BigDecimal(cost))));
        return row;
    }

    // ------------------------------------------------------------------ report windows

    @Test void typedWindowWinsOverTheCatalogDefault() {
        VariantSpec daily = SectionCatalog.spec(WindowsRoleMenus.CASH, "daily");
        SectionMath.DateWindow typed = SectionMath.window(daily,
                new SectionFilter("", "", "1405/07/01", "1405/07/30", null, 0, 0, 200), TODAY);
        assertEquals("1405/07/01", typed.from());
        assertEquals("1405/07/30", typed.to());
        assertFalse(typed.defaulted());
        assertEquals("بازهٔ انتخابی: ۱۴۰۵/۰۷/۰۱ تا ۱۴۰۵/۰۷/۳۰", SectionMath.windowLabel(typed));

        SectionMath.DateWindow defaulted = SectionMath.window(daily, SectionFilter.defaults(), TODAY);
        assertTrue(defaulted.defaulted());
        assertEquals(TODAY, defaulted.to());
        assertEquals(PersianDate.fromGregorian(ANCHOR.minusDays(62)), defaulted.from());
        assertTrue(SectionMath.windowLabel(defaulted).startsWith("بازهٔ پیش‌فرض:"));
        assertTrue(SectionMath.windowLabel(defaulted).contains(SectionMath.digits(TODAY)));

        // A report with no day window stays open, so nothing is silently hidden.
        assertTrue(SectionMath.window(SectionCatalog.spec(WindowsRoleMenus.CASH, "receipts"),
                SectionFilter.defaults(), TODAY).isEmpty());
        assertTrue(SectionMath.window(daily, null, "").isEmpty());
    }

    @Test void dueReportsLookForwardAndIdleReportsLookBack() {
        VariantSpec due = SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "due");
        SectionMath.DateWindow window = SectionMath.window(due, SectionFilter.defaults(), TODAY);
        assertEquals(TODAY, window.from());
        assertEquals(PersianDate.fromGregorian(ANCHOR.plusDays(30)), window.to());
        assertTrue(window.defaulted());
        // A typed window replaces the forward-looking default.
        assertEquals("1405/07/01", SectionMath.window(due,
                new SectionFilter("", "", "1405/07/01", "1405/07/30", null, 0, 0, 200), TODAY).from());

        VariantSpec dead = SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "dead");
        assertEquals(PersianDate.fromGregorian(ANCHOR.minusDays(180)),
                SectionMath.idleCutoff(dead, SectionFilter.defaults(), TODAY));
        assertEquals(PersianDate.fromGregorian(ANCHOR.minusDays(90)),
                SectionMath.idleCutoff(dead, new SectionFilter("", "", "", "", null, 90, 0, 200), TODAY));
        assertEquals("", SectionMath.idleCutoff(dead, SectionFilter.defaults(), ""));
        assertEquals("", SectionMath.idleCutoff(noWindow(), SectionFilter.defaults(), TODAY));

        assertEquals(PersianDate.fromGregorian(ANCHOR.plusDays(10)), SectionMath.plusDays(TODAY, 10));
        assertEquals(PersianDate.fromGregorian(ANCHOR.minusDays(10)), SectionMath.minusDays(TODAY, 10));
        assertEquals("", SectionMath.plusDays("not-a-date", 3));
        assertEquals("", SectionMath.minusDays("", 3));
        // Crossing a year boundary stays a valid Jalali date.
        String nextYear = SectionMath.plusDays(PersianDate.fromGregorian(ANCHOR.plusDays(120)), 120);
        assertTrue(PersianDate.isValidJalaliDate(nextYear), nextYear + " is not a valid Jalali date");
    }

    /** A report with no day window at all (the receipt list). */
    private static VariantSpec noWindow() {
        return SectionCatalog.spec(WindowsRoleMenus.CASH, "receipts");
    }

    /** "1405/08/03" → "1405/8/3", the un-padded form some Atiran installs store. */
    private static String oneDigit(String jalali) {
        String[] parts = jalali.split("/");
        return parts[0] + "/" + Integer.parseInt(parts[1]) + "/" + Integer.parseInt(parts[2]);
    }

    // ------------------------------------------------------------------ export safety

    @Test void exportedCellsCanNeverBecomeSpreadsheetFormulas() {
        assertEquals("'=SUM(A1:A9)", SectionMath.csvSafe("=SUM(A1:A9)"));
        assertEquals("'+1234", SectionMath.csvSafe("+1234"));
        assertEquals("'-cmd", SectionMath.csvSafe("-cmd"));
        assertEquals("'@import", SectionMath.csvSafe("@import"));
        assertEquals("'\tcmd", SectionMath.csvSafe("\tcmd"));
        assertEquals("فروشگاه", SectionMath.csvSafe("فروشگاه"));
        assertEquals("", SectionMath.csvSafe(""));
        assertEquals("", SectionMath.csvSafe(null));
    }
}
