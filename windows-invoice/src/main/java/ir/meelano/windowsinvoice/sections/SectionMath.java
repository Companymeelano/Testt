package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formatting, Jalali date arithmetic, Atiran value mappings and the derived/KPI calculations shared
 * by the SQL gateway, the demo gateway and the desktop section window. Keeping this in one pure-Java
 * class means a live database and the offline preview always show identical labels and totals.
 */
public final class SectionMath {
    private SectionMath() { }

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Atiran's receipt-cheque status codes, as documented by its own procedures. */
    private static final String[] CHEQUE_IN = {"", "نزد صندوق", "نزد بانک", "وصول‌شده", "استرداد", "خرج‌شده"};
    /** Atiran's payment-cheque status codes. */
    private static final String[] CHEQUE_OUT = {"سفید", "جاری", "پاس‌شده"};
    /** Debt ageing buckets; labels match the Android Manager ageing report. */
    public static final String[] AGEING_LABELS = {"جاری", "۱ تا ۳۰ روز", "۳۱ تا ۶۰ روز", "۶۱ تا ۹۰ روز",
            "۹۱ تا ۱۸۰ روز", "بیش از ۱۸۰ روز"};

    // --------------------------------------------------------------------------- text & numbers

    /** Persian digits for display; every user-facing number in the desktop client uses this. */
    public static String digits(String value) {
        if (value == null) return "";
        return value.replace('0', '۰').replace('1', '۱').replace('2', '۲').replace('3', '۳')
                .replace('4', '۴').replace('5', '۵').replace('6', '۶').replace('7', '۷')
                .replace('8', '۸').replace('9', '۹');
    }

    /** Latin digits plus a single decimal point; used for filtering, sorting and CSV output. */
    public static String latinDigits(String value) {
        if (value == null) return "";
        return value.replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
                .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
                .replace('۸', '8').replace('۹', '9')
                .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
                .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
                .replace('٨', '8').replace('٩', '9').replace('٫', '.').replace('٬', ',');
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return latinDigits(value.trim().toLowerCase(Locale.ROOT).replace('ي', 'ی').replace('ك', 'ک'));
    }

    /** Grouped money with Persian digits and the Persian thousands separator, e.g. ۱٬۲۵۰٬۰۰۰. */
    public static String money(BigDecimal value) {
        return digits(group(scale(value, 2)));
    }

    /** Grouped quantity with up to three decimals (Atiran sells in packs and loose pieces). */
    public static String quantity(BigDecimal value) {
        return digits(group(scale(value, 3)));
    }

    /** Percent with the Persian decimal separator, e.g. ۱۲٫۵٪. */
    public static String percent(BigDecimal value) {
        if (value == null) return "—";
        return digits(scale(value, 1).toPlainString().replace('.', '٫')) + "٪";
    }

    public static String count(long value) {
        return digits(group(BigDecimal.valueOf(value)));
    }

    private static BigDecimal scale(BigDecimal value, int fractionDigits) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        BigDecimal scaled = amount.setScale(fractionDigits, RoundingMode.HALF_UP).stripTrailingZeros();
        return scaled.scale() < 0 ? scaled.setScale(0, RoundingMode.UNNECESSARY) : scaled;
    }

    private static String group(BigDecimal value) {
        String plain = value.toPlainString();
        boolean negative = plain.startsWith("-");
        if (negative) plain = plain.substring(1);
        int dot = plain.indexOf('.');
        String integer = dot < 0 ? plain : plain.substring(0, dot);
        // The Persian decimal separator keeps display consistent with percent(); latinDigits() maps it back.
        String fraction = dot < 0 ? "" : plain.substring(dot).replace('.', '٫');
        StringBuilder grouped = new StringBuilder();
        int seen = 0;
        for (int i = integer.length() - 1; i >= 0; i--) {
            if (seen > 0 && seen % 3 == 0) grouped.append('٬');
            grouped.append(integer.charAt(i));
            seen++;
        }
        return (negative ? "-" : "") + grouped.reverse() + fraction;
    }

    // --------------------------------------------------------------------------- Jalali dates

    /**
     * Normalizes any typed date to yyyy/MM/dd with Latin digits. Accepts Persian digits and
     * -, . or / separators; anything that is not a three-part date is returned trimmed.
     */
    public static String normalizeJalali(String value) {
        String text = latinDigits(value == null ? "" : value.trim()).replace('-', '/').replace('.', '/');
        String[] parts = text.split("/", -1);
        if (parts.length == 3 && parts[0].matches("[0-9]{4}") && parts[1].matches("[0-9]{1,2}")
                && parts[2].matches("[0-9]{1,2}")) {
            try {
                return String.format(Locale.ROOT, "%04d/%02d/%02d", Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) { }
        }
        return text.trim();
    }

    /**
     * Displays an Atiran date as Jalali yyyy/MM/dd. Atiran stores Jalali text, but installations that
     * keep Gregorian datetimes are converted instead of being shown as a foreign calendar. A stored
     * clock (HH:mm) is kept, because a login or voucher time is part of what the operator audits.
     */
    public static String jalaliText(String raw) {
        String value = latinDigits(raw == null ? "" : raw.trim());
        if (value.isEmpty()) return "";
        String[] halves = value.split("\\s+", 2);
        String token = halves[0].replace('-', '/').replace('.', '/');
        String clock = "";
        if (halves.length > 1 && halves[1].matches("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?.*")) {
            String[] parts = halves[1].split(":");
            clock = " " + String.format(Locale.ROOT, "%02d:%02d",
                    Integer.parseInt(parts[0]) % 24, Integer.parseInt(parts[1]) % 60);
        }
        String[] parts = token.split("/");
        if (parts.length == 3) {
            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    if (year >= 1300 && year <= 1500)
                        return String.format(Locale.ROOT, "%04d/%02d/%02d", year, month, day) + clock;
                    if (year >= 1900 && year <= 2100)
                        return PersianDate.fromGregorian(LocalDate.of(year, month, day)) + clock;
                }
            } catch (RuntimeException ignored) { }
        }
        return (value.length() > 10 ? value.substring(0, 10) : value) + clock;
    }

    /** Jalali date to a Gregorian {@link LocalDate}, or null when the text is not a usable date. */
    public static LocalDate jalaliToGregorian(String jalali) {
        String value = normalizeJalali(jalali);
        if (!value.matches("[0-9]{4}/[0-9]{2}/[0-9]{2}")) return null;
        try {
            int jy = Integer.parseInt(value.substring(0, 4));
            int jm = Integer.parseInt(value.substring(5, 7));
            int jd = Integer.parseInt(value.substring(8, 10));
            if (jm < 1 || jm > 12 || jd < 1 || jd > 31) return null;
            // jdf.scr.ir 33-year-cycle conversion: Jalali -> days -> proleptic Gregorian y/m/d.
            // The constant is pinned by PersianDate: 1390/01/01 = 2011-03-21 and 1400/01/01 = 2021-03-21.
            jy += 1595;
            long days = -356118 + (365L * jy) + ((jy / 33) * 13) + (((jy % 33) + 3) / 4) + jd
                    + ((jm < 7) ? (jm - 1) * 31 : ((jm - 7) * 30) + 186);
            int gy = (int) (400 * (days / 146097));
            days %= 146097;
            if (days > 36524) {
                gy += (int) (100 * (days / 36524));
                days %= 36524;
                if (days >= 365) days++;
            }
            gy += (int) (4 * (days / 1461));
            days %= 1461;
            if (days > 365) {
                gy += (int) ((days - 1) / 365);
                days = (days - 1) % 365;
            }
            int gd = (int) days + 1;
            int[] monthLengths = {0, 31, (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0) ? 29 : 28,
                    31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            int gm = 1;
            while (gm <= 12 && gd > monthLengths[gm]) { gd -= monthLengths[gm]; gm++; }
            if (gm > 12) return null;
            return LocalDate.of(gy, gm, gd);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Whole days from {@code fromJalali} to {@code toJalali}; null when either side is not a date. */
    public static Integer daysBetween(String fromJalali, String toJalali) {
        LocalDate from = jalaliToGregorian(fromJalali);
        LocalDate to = jalaliToGregorian(toJalali);
        if (from == null || to == null) return null;
        return (int) (to.toEpochDay() - from.toEpochDay());
    }

    /** True when {@code dateJalali} falls inside the inclusive bounds; empty bounds are open-ended. */
    public static boolean withinRange(String dateJalali, String fromJalali, String toJalali) {
        String value = normalizeJalali(dateJalali);
        if (value.isEmpty()) return fromJalali.isEmpty() && toJalali.isEmpty();
        if (!fromJalali.isEmpty() && value.compareTo(fromJalali) < 0) return false;
        return toJalali.isEmpty() || value.compareTo(toJalali) <= 0;
    }

    // --------------------------------------------------------------------------- Atiran mappings

    /**
     * Maps a raw Atiran code to the Persian label the operator expects. Unknown codes are shown as
     * "سایر (code)" instead of being silently dropped, so a schema change stays visible.
     */
    public static String mapValue(String mapping, String raw) {
        String value = raw == null ? "" : raw.trim();
        String code = latinDigits(value);
        switch (mapping == null ? "" : mapping) {
            case "cheque_in": {
                String upper = code.toUpperCase(Locale.ROOT);
                int index = parseInt(upper);
                if (index >= 1 && index < CHEQUE_IN.length) return CHEQUE_IN[index];
                if (upper.startsWith("T") || upper.equals("1")) return CHEQUE_IN[1];
                return other(value);
            }
            case "cheque_out": {
                int index = parseInt(code);
                if (index >= 0 && index < CHEQUE_OUT.length) return CHEQUE_OUT[index];
                return other(value);
            }
            case "bounced":
                return truthy(code) ? "برگشتی" : "عادی";
            case "active":
                return truthy(code) ? "فعال" : "غیرفعال";
            case "yes_no":
                return truthy(code) ? "بله" : "خیر";
            case "stock_state":
                return stockState(numberOf(value));
            case "settlement": {
                int index = parseInt(code);
                if (index == 1) return "نقد";
                if (index == 2) return "نسیه";
                if (index == 3) return "چک";
                if (index == 4) return "کارت/حواله";
                if (index == 5) return "ترکیبی";
                if (index == 0) return "نامشخص";
                return other(value);
            }
            default:
                return value;
        }
    }

    private static String other(String value) {
        return value == null || value.isEmpty() ? "نامشخص" : "سایر (" + digits(value) + ")";
    }

    private static boolean truthy(String value) {
        String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return text.equals("T") || text.equals("1") || text.equals("TRUE") || text.equals("Y")
                || text.equals("YES") || text.equals("فعال");
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); }
        catch (RuntimeException ignored) { return Integer.MIN_VALUE; }
    }

    private static BigDecimal numberOf(String value) {
        try { return new BigDecimal(latinDigits(value == null ? "" : value.trim()).replace(",", "")); }
        catch (RuntimeException ignored) { return BigDecimal.ZERO; }
    }

    /** Stock state from the on-hand quantity, using Atiran's pack/loose quantity as a whole number. */
    public static String stockState(BigDecimal quantity) {
        BigDecimal value = quantity == null ? BigDecimal.ZERO : quantity;
        if (value.signum() < 0) return "کسری";
        if (value.compareTo(BigDecimal.ZERO) == 0) return "اتمام";
        if (value.compareTo(BigDecimal.ONE) <= 0) return "رو به اتمام";
        return "موجود";
    }

    // --------------------------------------------------------------------------- derived columns

    /** Finalizes one cell: applies the column mapping and the per-kind display format. */
    public static SectionValue value(SectionColumn column, String rawText, BigDecimal rawNumber) {
        String text = rawText == null ? "" : rawText.trim().replaceAll("\\s+", " ");
        switch (column.kind()) {
            case MONEY:
                return new SectionValue(money(rawNumber), scale(rawNumber, 2).toPlainString(), rawNumber);
            case NUMBER:
                return new SectionValue(quantity(rawNumber), scale(rawNumber, 3).toPlainString(), rawNumber);
            case DATE: {
                String jalali = jalaliText(text);
                return new SectionValue(jalali.isEmpty() ? "—" : digits(jalali), jalali, null);
            }
            case STATUS: {
                String label = mapValue(column.mapping(), text);
                return new SectionValue(label.isEmpty() ? "—" : label, normalize(label), null);
            }
            default:
                return new SectionValue(text.isEmpty() ? "—" : text, normalize(text), null);
        }
    }

    /** A derived column value computed from other columns of the same row. */
    public static SectionValue derive(SectionCatalog.Derivation kind, Map<String, SectionValue> row,
                                      List<String> sources, BigDecimal threshold, String todayJalali) {
        switch (kind) {
            case NONE:
                return SectionModels.EMPTY;
            case AGEING_BUCKET:
                return ageingBucket(key(row, sources, 0), todayJalali);
            case CHEQUE_DUE_STATE:
                return dueState(key(row, sources, 0), todayJalali);
            case MARGIN_PERCENT:
                return marginValue(amount(row, sources, 0), amount(row, sources, 1));
            case NET_AMOUNT:
                return netValue(amount(row, sources, 0), amount(row, sources, 1));
            case STOCK_STATE: {
                BigDecimal onHand = amount(row, sources, 0);
                // No reorder point (column absent, or zero on this row) falls back to the quantity rule,
                // so an empty shelf reads "اتمام" instead of being compared against an invented point.
                BigDecimal point = sources.size() > 1 ? amount(row, sources, 1) : BigDecimal.ZERO;
                return new SectionValue(stockStateWithPoint(onHand, point), normalize(stockStateWithPoint(onHand, point)), onHand);
            }
            case RATE_PERCENT:
                return rateValue(amount(row, sources, 0), amount(row, sources, 1));
            case DAYS_AGO:
                return daysAgo(key(row, sources, 0), todayJalali);
            case DESKTOP_ROLE: {
                String atiranRole = key(row, sources, 0);
                String userName = sources.size() > 1 ? key(row, sources, 1) : "";
                String desktopRole = SectionCatalog.desktopRole(atiranRole, userName);
                return new SectionValue(SectionCatalog.desktopRoleLabel(desktopRole), desktopRole, null);
            }
            case MENU_COUNT: {
                int menus = WindowsRoleMenus.idsFor(desktopRoleOf(row, sources)).size();
                return new SectionValue(count(menus), Integer.toString(menus), BigDecimal.valueOf(menus));
            }
            case MENU_SCOPE:
                return new SectionValue(SectionCatalog.menuScope(desktopRoleOf(row, sources)),
                        desktopRoleOf(row, sources), null);
            default:
                return SectionModels.EMPTY;
        }
    }

    /**
     * Desktop role id for the menu derivations. Their source is the derived «desktop_role» column, so
     * an unrecognized value falls back to re-deriving from the role text; derivations run in catalog
     * order, which guarantees the role column is already computed.
     */
    private static String desktopRoleOf(Map<String, SectionValue> row, List<String> sources) {
        String value = key(row, sources, 0);
        if (value.isEmpty()) return ir.meelano.windowsinvoice.data.AtiranRoles.SELLER;
        if (ir.meelano.windowsinvoice.data.AtiranRoles.isKnown(value)) return value;
        return SectionCatalog.desktopRole(value, "");
    }

    private static String key(Map<String, SectionValue> row, List<String> sources, int index) {
        if (sources == null || index >= sources.size()) return "";
        SectionValue value = row.get(sources.get(index));
        if (value == null) return "";
        return value.key().isEmpty() ? value.text() : value.key();
    }

    private static BigDecimal amount(Map<String, SectionValue> row, List<String> sources, int index) {
        if (sources == null || index >= sources.size()) return BigDecimal.ZERO;
        SectionValue value = row.get(sources.get(index));
        return value == null ? BigDecimal.ZERO : value.amount();
    }

    /** Debt ageing bucket from the last activity date; an unknown date stays "نامشخص". */
    public static SectionValue ageingBucket(String lastActivityJalali, String todayJalali) {
        Integer days = daysBetween(normalizeJalali(lastActivityJalali), todayJalali);
        if (days == null) return new SectionValue("نامشخص", "", null);
        int age = Math.max(0, days);
        int index = age <= 0 ? 0 : age <= 30 ? 1 : age <= 60 ? 2 : age <= 90 ? 3 : age <= 180 ? 4 : 5;
        String label = AGEING_LABELS[index];
        return new SectionValue(label, normalize(label), BigDecimal.valueOf(age));
    }

    /** Cheque due state: overdue days, the seven-day collection window, or a future due date. */
    public static SectionValue dueState(String dueJalali, String todayJalali) {
        Integer days = daysBetween(todayJalali, normalizeJalali(dueJalali));
        if (days == null) return new SectionValue("نامشخص", "", null);
        String label;
        if (days < 0) label = "سررسید گذشته (" + digits(Integer.toString(-days)) + " روز)";
        else if (days == 0) label = "سررسید امروز";
        else if (days <= 7) label = "تا ۷ روز (" + digits(Integer.toString(days)) + " روز)";
        else label = "سررسید آینده (" + digits(Integer.toString(days)) + " روز)";
        return new SectionValue(label, normalize(label), BigDecimal.valueOf(days));
    }

    /** Whole days since a Jalali date, shown as "۱۲ روز پیش"; null becomes "نامشخص". */
    public static SectionValue daysAgo(String jalali, String todayJalali) {
        Integer days = daysBetween(normalizeJalali(jalali), todayJalali);
        if (days == null) return new SectionValue("نامشخص", "", null);
        String label = digits(Integer.toString(Math.max(0, days))) + " روز";
        return new SectionValue(label, Integer.toString(Math.max(0, days)), BigDecimal.valueOf(days));
    }

    /** (sales − cost) / sales × 100 as a percent cell; a zero turnover reports "—" instead of ∞. */
    public static SectionValue marginValue(BigDecimal sales, BigDecimal cost) {
        if (sales == null || sales.signum() == 0) return new SectionValue("—", "", BigDecimal.ZERO);
        BigDecimal profit = sales.subtract(cost == null ? BigDecimal.ZERO : cost);
        BigDecimal rate = profit.multiply(HUNDRED).divide(sales.abs(), 2, RoundingMode.HALF_UP);
        return new SectionValue(percent(rate), rate.toPlainString(), rate);
    }

    /** part / whole × 100 as a percent cell (collection rate, goal achievement, …). */
    public static SectionValue rateValue(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return new SectionValue("—", "", BigDecimal.ZERO);
        BigDecimal rate = (part == null ? BigDecimal.ZERO : part).multiply(HUNDRED)
                .divide(whole.abs(), 2, RoundingMode.HALF_UP);
        return new SectionValue(percent(rate), rate.toPlainString(), rate);
    }

    /** Incoming minus outgoing as a signed money cell (cash box net, warehouse movement balance). */
    public static SectionValue netValue(BigDecimal incoming, BigDecimal outgoing) {
        BigDecimal net = (incoming == null ? BigDecimal.ZERO : incoming)
                .subtract(outgoing == null ? BigDecimal.ZERO : outgoing);
        return new SectionValue(money(net), net.toPlainString(), net);
    }

    /** Stock state against a reorder point; a missing point falls back to the plain quantity rule. */
    public static String stockStateWithPoint(BigDecimal quantity, BigDecimal reorderPoint) {
        BigDecimal onHand = quantity == null ? BigDecimal.ZERO : quantity;
        if (onHand.signum() < 0) return "کسری";
        if (reorderPoint != null && reorderPoint.signum() > 0) {
            if (onHand.compareTo(reorderPoint) <= 0) return "نیاز به سفارش";
            if (onHand.compareTo(reorderPoint.multiply(new BigDecimal("1.5"))) <= 0) return "رو به اتمام";
            return "موجود";
        }
        return stockState(onHand);
    }

    /** True when a stock row needs attention (shortage or at/below the reorder point). */
    public static boolean needsReorder(BigDecimal quantity, BigDecimal reorderPoint) {
        String state = stockStateWithPoint(quantity, reorderPoint);
        return state.equals("کسری") || state.equals("نیاز به سفارش");
    }

    // --------------------------------------------------------------------------- KPI chips

    /** Computes the KPI chips of one report from its filtered rows. */
    public static List<SectionKpi> kpis(List<SectionCatalog.Kpi> specs, List<Map<String, SectionValue>> rows,
                                        long totalRows, BigDecimal threshold) {
        List<SectionKpi> result = new ArrayList<>();
        if (specs == null) return List.of();
        for (SectionCatalog.Kpi spec : specs) result.add(kpi(spec, rows, totalRows, threshold));
        return List.copyOf(result);
    }

    private static SectionKpi kpi(SectionCatalog.Kpi spec, List<Map<String, SectionValue>> rows,
                                  long totalRows, BigDecimal threshold) {
        List<BigDecimal> first = columnValues(rows, spec.columnKey());
        List<BigDecimal> second = columnValues(rows, spec.secondKey());
        switch (spec.op()) {
            case COUNT:
                return new SectionKpi(spec.label(), count(totalRows), spec.hint());
            case COUNT_NONZERO:
                return new SectionKpi(spec.label(), count(countNonZero(first)), spec.hint());
            case SUM:
                return new SectionKpi(spec.label(), money(sum(first)), spec.hint());
            case AVG:
                return new SectionKpi(spec.label(), first.isEmpty() ? "—" : money(avg(first)), spec.hint());
            case MAX:
                return new SectionKpi(spec.label(), first.isEmpty() ? "—" : money(max(first)), spec.hint());
            case SUM_DIFF:
                return new SectionKpi(spec.label(), money(sum(first).subtract(sum(second))), spec.hint());
            case RATIO_PERCENT:
                return new SectionKpi(spec.label(), percent(ratio(sum(first), sum(second))), spec.hint());
            case MARGIN_OF:
                return new SectionKpi(spec.label(), percent(marginRate(sum(first), sum(second))), spec.hint());
            case COUNT_BELOW: {
                BigDecimal limit = threshold == null ? BigDecimal.ONE : threshold;
                long matches = 0;
                for (BigDecimal value : first) if (value.compareTo(limit) <= 0) matches++;
                return new SectionKpi(spec.label(), count(matches), spec.hint());
            }
            case COUNT_MATCHES: {
                long matches = 0;
                for (Map<String, SectionValue> row : rows) {
                    SectionValue value = row.get(spec.columnKey());
                    if (value != null && !value.text().isEmpty() && value.text().equals(spec.match())) matches++;
                }
                return new SectionKpi(spec.label(), count(matches), spec.hint());
            }
            default:
                return new SectionKpi(spec.label(), "—", spec.hint());
        }
    }

    private static List<BigDecimal> columnValues(List<Map<String, SectionValue>> rows, String key) {
        List<BigDecimal> values = new ArrayList<>();
        if (key == null || key.isEmpty()) return values;
        for (Map<String, SectionValue> row : rows) {
            SectionValue value = row.get(key);
            if (value != null) values.add(value.amount());
        }
        return values;
    }

    public static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) if (value != null) total = total.add(value);
        return total;
    }

    public static BigDecimal avg(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return BigDecimal.ZERO;
        return sum(values).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal max(List<BigDecimal> values) {
        BigDecimal best = null;
        for (BigDecimal value : values) if (value != null && (best == null || value.compareTo(best) > 0)) best = value;
        return best == null ? BigDecimal.ZERO : best;
    }

    public static long countNonZero(List<BigDecimal> values) {
        long matches = 0;
        for (BigDecimal value : values) if (value != null && value.signum() != 0) matches++;
        return matches;
    }

    /** (sales − cost) / sales × 100 over aggregated totals, or null when sales are zero. */
    public static BigDecimal marginRate(BigDecimal sales, BigDecimal cost) {
        return ratio(profit(sales, cost), sales);
    }

    /** part / whole × 100 with two decimals, or null when the denominator is zero. */
    public static BigDecimal ratio(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return (part == null ? BigDecimal.ZERO : part).multiply(HUNDRED).divide(whole.abs(), 2, RoundingMode.HALF_UP);
    }

    /** Profit = sales − cost, and its margin percent; used by the profit KPIs and the CSV footer. */
    public static BigDecimal profit(BigDecimal sales, BigDecimal cost) {
        return (sales == null ? BigDecimal.ZERO : sales).subtract(cost == null ? BigDecimal.ZERO : cost);
    }

    // --------------------------------------------------------------------------- report windows

    /** The inclusive Jalali window a report actually reads, and whether it came from the catalog default. */
    public record DateWindow(String from, String to, boolean defaulted) {
        public boolean isEmpty() { return from.isEmpty() && to.isEmpty(); }
    }

    /** Jalali date shifted by whole days; returns "" when the input is not a usable date. */
    public static String plusDays(String jalali, int days) {
        LocalDate date = jalaliToGregorian(jalali);
        return date == null ? "" : PersianDate.fromGregorian(date.plusDays(days));
    }

    public static String minusDays(String jalali, int days) { return plusDays(jalali, -days); }

    /**
     * Resolves the window for a report: an explicitly typed range wins; otherwise the catalog default
     * is used. A due-date report looks forward from today, a normal report looks back.
     */
    public static DateWindow window(SectionCatalog.VariantSpec spec, SectionModels.SectionFilter filter, String today) {
        if (filter != null && filter.hasDateRange())
            return new DateWindow(filter.fromJalali(), filter.toJalali(), false);
        int days = SectionCatalog.daysOf(spec, filter == null ? 0 : filter.days());
        if (days <= 0 || !spec.supportsDateRange()) return new DateWindow("", "", false);
        String todayJalali = normalizeJalali(today);
        if (todayJalali.isEmpty()) return new DateWindow("", "", false);
        if (spec.dueWindow()) return new DateWindow(todayJalali, plusDays(todayJalali, days), true);
        return new DateWindow(minusDays(todayJalali, days), todayJalali, true);
    }

    /**
     * Cut-off date for "idle since" reports (dead stock): rows whose last activity is older than this
     * Jalali date are reported. Empty when the report has no look-back window.
     */
    public static String idleCutoff(SectionCatalog.VariantSpec spec, SectionModels.SectionFilter filter, String today) {
        int days = SectionCatalog.daysOf(spec, filter == null ? 0 : filter.days());
        if (days <= 0) return "";
        String todayJalali = normalizeJalali(today);
        return todayJalali.isEmpty() ? "" : minusDays(todayJalali, days);
    }

    /** Persian text describing the effective window, shown in the report note. */
    public static String windowLabel(DateWindow window) {
        if (window == null || window.isEmpty()) return "";
        String from = window.from().isEmpty() ? "ابتدا" : digits(window.from());
        String to = window.to().isEmpty() ? "امروز" : digits(window.to());
        return (window.defaulted() ? "بازهٔ پیش‌فرض: " : "بازهٔ انتخابی: ") + from + " تا " + to;
    }

    /**
     * Neutralizes a cell for CSV/Excel export: a leading formula character is prefixed with a single
     * quote so an exported report can never run as a formula in the operator's spreadsheet.
     */
    public static String csvSafe(String value) {
        String text = value == null ? "" : value;
        if (text.isEmpty()) return "";
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r')
            return "'" + text;
        return text;
    }
}
