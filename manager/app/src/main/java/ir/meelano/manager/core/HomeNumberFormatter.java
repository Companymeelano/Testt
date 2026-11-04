package ir.meelano.manager.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.BigDecimal;
import java.util.Locale;

/** One RTL-safe number formatter for text rendered on the Home dashboard. */
public final class HomeNumberFormatter {
    private static final char LRI = '\u2066';
    private static final char PDI = '\u2069';

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
     * Apply Persian digits to arbitrary Home labels (names, timestamps and error details).
     * Numeric values should use one of the typed formatting methods below for grouping,
     * decimal separators, signs and bidi isolation.
     */
    public static String localizeText(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean digitBefore = i > 0 && isDigit(value.charAt(i - 1));
            boolean digitAfter = i + 1 < value.length() && isDigit(value.charAt(i + 1));
            if (ch == ',') normalized.append(digitBefore && digitAfter ? '٬' : '،');
            else if (ch == '.' && digitBefore && digitAfter) normalized.append('٫');
            else if (ch == '%') normalized.append('٪');
            else if (ch == '-' && digitAfter && !digitBefore) normalized.append('−');
            else normalized.append(ch);
        }
        return persianDigits(normalized.toString());
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

    /** Number with at most one fractional digit, grouped and isolated. */
    public static String decimal(double value) {
        return isolate(formatNumber(value, 1));
    }

    /** A compact value that is already in the selected display unit, without a currency name. */
    public static String compactUnit(double value) {
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
        return isolate(formatNumber(scaled, decimals)) + suffix;
    }

    /** Compact Rial amount in the app's selected display unit (Toman or Rial). */
    public static String compactMoney(double rials) {
        return compactUnit(Money.toUnit(rials)) + " " + Money.unitName();
    }

    /** Full Rial amount in the selected display unit (Toman or Rial). */
    public static String money(double rials) {
        return isolate(formatNumber(Money.toUnit(rials), 0)) + " " + Money.unitName();
    }

    /** Exact Rial amount formatted without converting through binary floating point. */
    public static String money(BigDecimal rials) {
        if (rials == null) return isolate("—") + " " + Money.unitName();
        BigDecimal display = Money.isToman()
                ? rials.divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP)
                : rials.setScale(0, java.math.RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return isolate(localizeFormatted(format.format(display))) + " " + Money.unitName();
    }

    /** Exact-enough variance display: unlike normal totals, do not hide fractional Rials by rounding. */
    public static String preciseMoney(BigDecimal rials) {
        if (rials == null) return isolate("—") + " " + Money.unitName();
        BigDecimal display = Money.isToman() ? rials.movePointLeft(1) : rials;
        DecimalFormat format = new DecimalFormat("#,##0.#####", DecimalFormatSymbols.getInstance(Locale.US));
        format.setMaximumFractionDigits(Money.isToman() ? 5 : 4);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return isolate(localizeFormatted(format.format(display))) + " " + Money.unitName();
    }

    /** Compact Rial value for chart labels, without repeating the currency unit. */
    public static String chartMoney(double rials) {
        return compactUnit(Money.toUnit(rials));
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
        if (!isFinite(differenceRials)) return "—";
        if (Math.abs(differenceRials) < 0.5d) return compactMoney(0d);
        return (differenceRials > 0d ? "+ " : "− ")
                + compactMoney(Math.abs(differenceRials));
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
}
