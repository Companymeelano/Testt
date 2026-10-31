package ir.meelano.manager.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
        return persianDigits(value);
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

    /** Compact Rial value for chart axes, without repeating the currency unit. */
    public static String chartMoney(double rials) {
        return compactUnit(Money.toUnit(rials));
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
