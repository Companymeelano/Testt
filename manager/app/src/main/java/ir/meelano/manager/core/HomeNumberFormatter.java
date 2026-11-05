package ir.meelano.manager.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.BigDecimal;
import java.util.Locale;

/** Shared Persian number, date, time, and RTL text formatter for app display surfaces. */
public final class HomeNumberFormatter {
    private static final char LRI = '\u2066';
    private static final char RLI = '\u2067';
    private static final char FSI = '\u2068';
    private static final char PDI = '\u2069';
    private static final char NNBSP = '\u202F';

    private HomeNumberFormatter() { }

    /** Convert ASCII and Arabic-Indic digits to the Persian digit set. */
    public static String persianDigits(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') result.append((char) ('۰' + (ch - '0')));
            else if (ch >= '\u0660' && ch <= '\u0669') result.append((char) ('۰' + (ch - '\u0660')));
            else result.append(ch);
        }
        return result.toString();
    }

    /**
     * Localize free-form display text and isolate every numeric run. Typed values should still
     * use the methods below so grouping, decimal separators, signs and units are unambiguous.
     */
    public static String localizeText(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        return isolateNumericRuns(normalizeText(value), null);
    }

    /** Alias used at the final Android text-view boundary. */
    public static String rtlText(String value) {
        return localizeText(value);
    }

    /** Output offsets for mapping existing text spans after digit localization and isolation. */
    public static int[] localizedOffsetMap(String value) {
        if (value == null) return new int[]{0};
        int[] map = new int[value.length() + 1];
        isolateNumericRuns(normalizeText(value), map);
        return map;
    }

    private static String normalizeText(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean digitBefore = i > 0 && isDigit(value.charAt(i - 1));
            boolean digitAfter = i + 1 < value.length() && isDigit(value.charAt(i + 1));
            if (ch == ',') normalized.append(digitBefore && digitAfter ? '٬' : '،');
            else if ((ch == '.' || ch == '\u066B') && digitBefore && digitAfter) normalized.append('٫');
            else if (ch == '%') normalized.append('٪');
            else if (ch == '-' && digitAfter && !digitBefore) normalized.append('−');
            else normalized.append(ch);
        }
        return persianDigits(normalized.toString());
    }

    private static String isolateNumericRuns(String value, int[] offsetMap) {
        StringBuilder result = new StringBuilder(value.length() + 12);
        if (offsetMap != null) offsetMap[0] = 0;
        for (int i = 0; i < value.length();) {
            char ch = value.charAt(i);
            if (isolateStart(ch)) {
                int end = findIsolateEnd(value, i);
                int outputStart = result.length();
                result.append(value, i, end);
                if (offsetMap != null) {
                    for (int boundary = i; boundary <= end; boundary++)
                        offsetMap[boundary] = outputStart + boundary - i;
                }
                i = end;
                continue;
            }
            if (!isNumericStart(value, i)) {
                if (offsetMap != null) offsetMap[i] = result.length();
                result.append(ch);
                i++;
                if (offsetMap != null) offsetMap[i] = result.length();
                continue;
            }
            int end = i;
            if (isSign(ch)) end++;
            while (end < value.length()) {
                char current = value.charAt(end);
                if (isDigit(current)) {
                    end++;
                    continue;
                }
                if (isNumberSeparator(current) && end + 1 < value.length()
                        && isDigit(value.charAt(end + 1))) {
                    end += 2;
                    continue;
                }
                if ((current == '٪' || current == '%') && end > i) end++;
                break;
            }
            result.append(LRI);
            if (offsetMap != null) offsetMap[i] = result.length();
            result.append(value, i, end);
            if (offsetMap != null) {
                for (int boundary = i + 1; boundary <= end; boundary++)
                    offsetMap[boundary] = result.length() - (end - boundary);
            }
            result.append(PDI);
            if (offsetMap != null) offsetMap[end] = result.length();
            i = end;
        }
        if (offsetMap != null) offsetMap[value.length()] = result.length();
        return result.toString();
    }

    private static boolean isNumericStart(String value, int index) {
        char ch = value.charAt(index);
        if (isDigit(ch)) return true;
        return isSign(ch) && index + 1 < value.length() && isDigit(value.charAt(index + 1));
    }

    private static boolean isSign(char value) {
        return value == '+' || value == '-' || value == '−';
    }

    private static boolean isNumberSeparator(char value) {
        return value == '٬' || value == '٫' || value == '/' || value == ':'
                || value == '.' || value == '-' || value == '−';
    }

    private static boolean isolateStart(char value) {
        return value == LRI || value == RLI || value == FSI;
    }

    private static int findIsolateEnd(String value, int start) {
        int depth = 0;
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isolateStart(ch)) depth++;
            else if (ch == PDI && --depth == 0) return i + 1;
        }
        return value.length();
    }

    private static boolean isDigit(char value) {
        return (value >= '0' && value <= '9')
                || (value >= '\u0660' && value <= '\u0669')
                || (value >= '\u06F0' && value <= '\u06F9');
    }

    /**
     * Clean a name copied from account metadata before placing it in the Home greeting.
     * Invalid UTF-8 replacement glyphs and control characters should never become a row of
     * visible tofu boxes; callers can fall back to the active role label when this is empty.
     */
    public static String cleanDisplayName(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder cleaned = new StringBuilder(value.length());
        boolean spacePending = false;
        boolean hasNameCharacter = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == 0xFFFD || codePoint == 0xFFFC || Character.isISOControl(codePoint)
                    || isBidiControl(codePoint)
                    || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                spacePending = cleaned.length() > 0;
                continue;
            }
            if (Character.isWhitespace(codePoint) || codePoint == 0x00A0) {
                spacePending = cleaned.length() > 0;
                continue;
            }
            if (spacePending) {
                cleaned.append(' ');
                spacePending = false;
            }
            cleaned.appendCodePoint(codePoint);
            if (Character.isLetterOrDigit(codePoint)) hasNameCharacter = true;
        }
        if (!hasNameCharacter) return "";
        int length = cleaned.length();
        while (length > 0 && cleaned.charAt(length - 1) == ' ') length--;
        return cleaned.substring(0, length);
    }

    /** Strip Unicode bidirectional formatting controls from account names, but keep ZWNJ/ZWJ. */
    private static boolean isBidiControl(int codePoint) {
        return codePoint == 0x061C || codePoint == 0x200E || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    /** Grouped integer, isolated from surrounding RTL prose. */
    public static String integer(long value) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return isolate(localizeFormatted(format.format(value)));
    }

    /** Rounded whole number, grouped and isolated. */
    public static String rounded(double value) {
        return isolate(formatNumber(value, 0));
    }

    /** Number with up to three fractional digits, used for quantities. */
    public static String quantity(double value) {
        return isolate(quantityRaw(value));
    }

    /** Quantity with an explicit sign held next to the number in one LTR isolate. */
    public static String signedQuantity(double value) {
        if (!isFinite(value)) return isolate("—");
        String sign = value < 0d ? "−" : "+";
        return isolate(sign + quantityRaw(Math.abs(value)));
    }

    private static String quantityRaw(double value) {
        if (!isFinite(value)) return "—";
        if (Math.abs(value - Math.round(value)) < 0.0005d) return formatNumber(value, 0);
        DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(3);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        if (Math.abs(value) < 0.0005d) value = 0d;
        return localizeFormatted(format.format(value));
    }

    /** Number with at most one fractional digit, grouped and isolated. */
    public static String decimal(double value) {
        return isolate(formatNumber(value, 1));
    }

    /** A compact value that is already in the selected display unit, without a currency name. */
    public static String compactUnit(double value) {
        return isolatePhrase(compactUnitRaw(value));
    }

    /** Compact Rial amount in the app's selected display unit (Toman or Rial). */
    public static String compactMoney(double rials) {
        return isolatePhrase(compactUnitRaw(Money.toUnit(rials)) + NNBSP + Money.unitName());
    }

    /** Full Rial amount in the selected display unit (Toman or Rial). */
    public static String money(double rials) {
        return isolatePhrase(formatNumber(Money.toUnit(rials), 0) + NNBSP + Money.unitName());
    }

    /** Exact Rial amount formatted without converting through binary floating point. */
    public static String money(BigDecimal rials) {
        if (rials == null) return isolatePhrase("—" + NNBSP + Money.unitName());
        BigDecimal display = Money.isToman()
                ? rials.divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP)
                : rials.setScale(0, java.math.RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return isolatePhrase(localizeFormatted(format.format(display)) + NNBSP + Money.unitName());
    }

    /** Exact-enough variance display: unlike normal totals, do not hide fractional Rials by rounding. */
    public static String preciseMoney(BigDecimal rials) {
        if (rials == null) return isolatePhrase("—" + NNBSP + Money.unitName());
        BigDecimal display = Money.isToman() ? rials.movePointLeft(1) : rials;
        DecimalFormat format = new DecimalFormat("#,##0.#####", DecimalFormatSymbols.getInstance(Locale.US));
        format.setMaximumFractionDigits(Money.isToman() ? 5 : 4);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return isolatePhrase(localizeFormatted(format.format(display)) + NNBSP + Money.unitName());
    }

    /** Compact Rial value for chart labels; Canvas draws this as an isolated plot annotation. */
    public static String chartMoney(double rials) {
        return compactUnitRaw(Money.toUnit(rials));
    }

    private static String compactUnitRaw(double value) {
        if (!isFinite(value)) return "—";
        double absolute = Math.abs(value);
        double scale = 1d;
        String suffix = "";
        if (absolute >= 1_000_000_000_000d) {
            scale = 1_000_000_000_000d;
            suffix = " هزار میلیارد";
        } else if (absolute >= 1_000_000_000d) {
            scale = 1_000_000_000d;
            suffix = " میلیارد";
        } else if (absolute >= 1_000_000d) {
            scale = 1_000_000d;
            suffix = " میلیون";
        } else if (absolute >= 1_000d) {
            scale = 1_000d;
            suffix = " هزار";
        }
        double scaled = value / scale;
        int decimals = Math.abs(scaled) >= 100d ? 0 : 1;
        return formatNumber(scaled, decimals) + suffix;
    }

    /** Scale divisor for a chart axis, calculated in the selected display currency. */
    public static double chartScale(double maxRials) {
        double value = Math.abs(Money.toUnit(maxRials));
        if (value >= 1_000_000_000_000d) return 1_000_000_000_000d;
        if (value >= 1_000_000_000d) return 1_000_000_000d;
        if (value >= 1_000_000d) return 1_000_000d;
        if (value >= 1_000d) return 1_000d;
        return 1d;
    }

    /** Unit label paired with a scaled chart axis so the axis never shows a misleading peak. */
    public static String chartScaleLabel(double scale) {
        String suffix = scale >= 1_000_000_000_000d ? "هزار میلیارد"
                : scale >= 1_000_000_000d ? "میلیارد"
                : scale >= 1_000_000d ? "میلیون"
                : scale >= 1_000d ? "هزار" : "";
        return suffix.isEmpty() ? Money.unitName() : suffix + " " + Money.unitName();
    }

    /** Compact axis tick in the scale selected by {@link #chartScale(double)}. */
    public static String chartTick(double rials, double scale) {
        if (!isFinite(rials) || !isFinite(scale) || scale <= 0d) return "—";
        double value = Money.toUnit(rials) / scale;
        return formatNumber(value, Math.abs(value) >= 100d ? 0 : 1);
    }

    /**
     * Numeric part of a comparison: percentage with a positive baseline when readable,
     * otherwise a clear zero-baseline state or a signed currency difference.
     */
    public static String changeValue(double currentRials, double baselineRials, double percent) {
        if (!isFinite(currentRials) || !isFinite(baselineRials)) return "—";
        if (baselineRials <= 0d) {
            if (baselineRials == 0d && currentRials > 0d) return "شروع از صفر";
            if (baselineRials == 0d && currentRials == 0d) return "بدون تغییر";
            return "مبنای نامعتبر";
        }
        if (!isFinite(percent) || Math.abs(percent) > 999d)
            return signedCompactMoney(currentRials - baselineRials);
        return signedPercent(percent);
    }

    public static String changeLabel(double currentRials, double baselineRials, double percent,
                                     String baselineLabel) {
        String baseline = baselineLabel == null || baselineLabel.trim().isEmpty()
                ? "مبنای مقایسه" : baselineLabel.trim();
        if (!isFinite(currentRials) || !isFinite(baselineRials)) return "مقایسه در دسترس نیست";
        if (baselineRials <= 0d) return "مبنای مقایسه صفر است؛ درصد تغییر محاسبه نمی‌شود";
        double difference = currentRials - baselineRials;
        if (Math.abs(difference) < 0.5d) return "بدون تغییر نسبت به " + baseline;
        return changeValue(currentRials, baselineRials, percent) + " نسبت به " + baseline;
    }

    /** Signed compact currency variance used when a relative percentage would be misleading. */
    public static String signedCompactMoney(double differenceRials) {
        if (!isFinite(differenceRials)) return isolatePhrase("—");
        if (Math.abs(differenceRials) < 0.5d) return compactMoney(0d);
        String sign = differenceRials > 0d ? "+" : "−";
        return isolatePhrase(sign + compactUnitRaw(Money.toUnit(Math.abs(differenceRials)))
                + NNBSP + Money.unitName());
    }

    /** Percentage, preserving a negative sign and placing the Persian percent mark with the value. */
    public static String percent(double value) {
        if (!isFinite(value)) return isolate("—٪");
        return isolate(formatNumber(value, 1) + "٪");
    }

    /** Percentage magnitude, for labels whose prose already says «increase», «drop», or similar. */
    public static String percentMagnitude(double value) {
        if (!isFinite(value)) return isolate("—٪");
        return isolate(formatNumber(Math.abs(value), 1) + "٪");
    }

    /** A change percentage with an explicit plus or minus sign, held in one LTR isolate. */
    public static String signedPercent(double value) {
        if (!isFinite(value)) return isolate("—٪");
        if (Math.abs(value) < 0.05d) return isolate("۰٪");
        String sign = value > 0d ? "+" : "−";
        return isolate(sign + formatNumber(Math.abs(value), 1) + "٪");
    }

    /** Arrow, sign, digits and percent mark form one bidi-isolated directional change. */
    public static String directionalPercent(double value) {
        if (!isFinite(value)) return isolate("—٪");
        if (Math.abs(value) < 0.05d) return isolate("۰٪");
        String direction = value > 0d ? "↗ +" : "↘ −";
        return isolate(direction + formatNumber(Math.abs(value), 1) + "٪");
    }

    /**
     * Human-facing Jalali date used in prose and cards: «۱۳ آبان ۱۴۰۵».
     * Invalid date strings are safely localized rather than silently rewritten.
     */
    public static String dateText(String value) {
        String normalized = Jalali.disp(value);
        if (normalized == null || normalized.isEmpty()) return localizeText(value);
        String[] parts = normalized.split("/");
        if (parts.length != 3) return localizeText(normalized);
        String month = Jalali.monthName(normalized);
        if (month == null || month.isEmpty()) return localizeText(normalized);
        return isolatePhrase(persianDigits(String.valueOf(Integer.parseInt(parts[2])))
                + " " + month + " " + persianDigits(parts[0]));
    }

    /** Compact Jalali date for dense table columns: «۱۴۰۵/۰۸/۱۳». */
    public static String dateTable(String value) {
        String normalized = Jalali.disp(value);
        if (normalized == null || normalized.isEmpty()) return localizeText(value);
        return isolate(persianDigits(normalized.replace('-', '/')));
    }

    /** Full human-facing Jalali date and optional clock time for prose/cards. */
    public static String dateTime(String value) {
        String normalized = Jalali.disp(value);
        if (normalized == null || normalized.isEmpty()) return localizeText(value);
        String date = dateText(normalized);
        String clock = clockAfterDate(value);
        return clock.isEmpty() ? date : date + "، ساعت " + time(clock);
    }

    /** Compact Jalali date and optional clock time for dense table/report cells. */
    public static String dateTimeTable(String value) {
        String normalized = Jalali.disp(value);
        if (normalized == null || normalized.isEmpty()) return localizeText(value);
        String date = dateTable(normalized);
        String clock = clockAfterDate(value);
        return clock.isEmpty() ? date : date + " · " + time(clock);
    }

    private static String clockAfterDate(String value) {
        if (value == null || value.isEmpty()) return "";
        String text = Money.en(value.trim());
        int separator = Math.max(text.indexOf('T'), text.indexOf(' '));
        if (separator < 0 || separator + 1 >= text.length()) return "";
        String[] tokens = text.substring(separator + 1).trim().split("\\s+");
        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon < 1 || colon + 2 >= token.length()) continue;
            int hourStart = colon >= 2 && isAsciiDigit(token.charAt(colon - 2))
                    ? colon - 2 : colon - 1;
            if (hourStart < 0 || !isAsciiDigits(token, hourStart, colon)
                    || !isAsciiDigits(token, colon + 1, colon + 3)) continue;
            try {
                int hour = Integer.parseInt(token.substring(hourStart, colon));
                int minute = Integer.parseInt(token.substring(colon + 1, colon + 3));
                if (hour < 0 || hour >= 24 || minute < 0 || minute >= 60) continue;
                int secondsColon = colon + 3;
                if (secondsColon + 2 < token.length() && token.charAt(secondsColon) == ':'
                        && isAsciiDigits(token, secondsColon + 1, secondsColon + 3)) {
                    int second = Integer.parseInt(token.substring(secondsColon + 1, secondsColon + 3));
                    if (second >= 0 && second < 60)
                        return String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second);
                }
                return String.format(Locale.US, "%02d:%02d", hour, minute);
            } catch (NumberFormatException ignored) {
                // Skip non-clock tokens such as a malformed timestamp suffix.
            }
        }
        return "";
    }

    private static boolean isAsciiDigits(String value, int start, int end) {
        if (start < 0 || end > value.length() || start >= end) return false;
        for (int i = start; i < end; i++) if (!isAsciiDigit(value.charAt(i))) return false;
        return true;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    /** Short chart label formatted as a single date phrase: «۱۳ آبان». */
    public static String dateShortLabel(String value) {
        String normalized = Jalali.disp(value);
        if (normalized == null || normalized.isEmpty()) return localizeText(value);
        String[] parts = normalized.split("/");
        if (parts.length != 3) return localizeText(normalized);
        String month = Jalali.monthName(normalized);
        if (month == null || month.isEmpty()) return localizeText(normalized);
        return isolatePhrase(persianDigits(String.valueOf(Integer.parseInt(parts[2])))
                + " " + month);
    }

    /** Remove isolate controls for internal suffix parsing; do not use for rendering. */
    public static String stripIsolates(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != LRI && ch != RLI && ch != FSI && ch != PDI) result.append(ch);
        }
        return result.toString();
    }

    /** Explicit LTR isolate for product codes and other non-numeric identifiers. */
    public static String isolateLtr(String value) {
        return isolate(value);
    }

    /** Fixed-width clock time (HH:mm) in Persian digits and one LTR isolate. */
    public static String time(int hour, int minute) {
        String hh = hour < 10 ? "0" + hour : String.valueOf(hour);
        String mm = minute < 10 ? "0" + minute : String.valueOf(minute);
        return time(hh + ":" + mm);
    }

    /** Localize an already formatted clock token while keeping its colon and order stable. */
    public static String time(String value) {
        String normalized = persianDigits(value);
        if (normalized.length() >= 2 && normalized.charAt(0) == LRI
                && normalized.charAt(normalized.length() - 1) == PDI) return normalized;
        return isolate(normalized);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String formatNumber(double value, int maxFractionDigits) {
        if (!isFinite(value)) return "—";
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(maxFractionDigits == 0 ? "#,##0" : "#,##0.#", symbols);
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(maxFractionDigits);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        // Avoid a visually negative zero after rounding a tiny negative value.
        if (Math.abs(value) < (maxFractionDigits == 0 ? 0.5d : 0.05d)) value = 0d;
        return localizeFormatted(format.format(value));
    }

    private static String localizeFormatted(String value) {
        return persianDigits(value.replace(',', '٬').replace('.', '٫').replace('-', '−'));
    }

    private static String isolate(String value) {
        return LRI + (value == null ? "" : value) + PDI;
    }

    private static String isolatePhrase(String value) {
        return FSI + (value == null ? "" : value) + PDI;
    }
}
