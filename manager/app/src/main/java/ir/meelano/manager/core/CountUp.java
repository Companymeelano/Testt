package ir.meelano.manager.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure count-up engine for the luxury amount animation: a formatted final text is swept
 * from zero to its target while keeping the exact Persian formatting, grouping character,
 * decimal count and any surrounding words ("۱٬۲۳۴٬۵۶۷ تومان" counts digit-group by
 * digit-group and lands on the identical string). No Android types, fully unit-testable.
 */
public final class CountUp {

    private CountUp() { }

    /** First countable number of a formatted string: optional sign, grouped digits, decimals. */
    private static final Pattern NUMBER = Pattern.compile(
            "([\\u2212+\\-])?([0-9\u06F0-\u06F9\u0660-\u0669][0-9\u06F0-\u06F9\u0660-\u0669\u066C,\u066B.]*)");

    /** Out-cubic easing: a confident rise with a gentle, expensive-feeling landing. */
    public static double ease(double t) {
        if (Double.isNaN(t) || t <= 0d) return 0d;
        if (t >= 1d) return 1d;
        double inv = 1d - t;
        return 1d - inv * inv * inv;
    }

    /** Eased frame value for a target at linear progress t (0..1). */
    public static double valueAt(double target, double t) {
        if (Double.isNaN(target) || Double.isInfinite(target)) return target;
        return target * ease(t);
    }

    /** True when s contains a positive countable number worth animating. */
    public static boolean countable(String s) {
        double target = parse(s);
        return !Double.isNaN(target) && Math.abs(target) > 0d;
    }

    /** Value of the first number in s (Persian/Arabic/Latin digits, ٬ or , grouping, ٫ or . decimals). */
    public static double parse(String s) {
        Matcher m = firstNumber(s);
        return m == null ? Double.NaN : tokenValue(m.group(2), m.group(1));
    }

    /**
     * The formatted text as it should read at progress t: at t=0 the number is zero,
     * at t>=1 the input is returned untouched, in between the number is re-rendered
     * with the same grouping character and decimal count as the final text.
     */
    public static String sweep(String finalText, double t) {
        if (finalText == null || finalText.isEmpty()) return finalText == null ? "" : finalText;
        if (Double.isNaN(t) || t >= 1d) return finalText;
        Matcher m = firstNumber(finalText);
        if (m == null) return finalText;
        String digits = m.group(2);
        String sign = m.group(1);
        double target = tokenValue(digits, sign);
        if (Double.isNaN(target)) return finalText;
        double progress = Math.max(0d, t);
        String frame = formatLike(digits, valueAt(Math.abs(target), progress));
        if (frame == null) return finalText;
        String rendered = (sign != null && target < 0d ? sign : "") + frame;
        return finalText.substring(0, m.start()) + rendered + finalText.substring(m.end());
    }

    private static Matcher firstNumber(String s) {
        if (s == null) return null;
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            // A lone separator or a dot is not a number; require at least one real digit.
            if (m.group(2) != null && !m.group(2).isEmpty()) return m;
        }
        return null;
    }

    private static double tokenValue(String digits, String sign) {
        if (digits == null) return Double.NaN;
        StringBuilder plain = new StringBuilder();
        boolean decimalSeen = false;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            int d = digitValue(c);
            if (d >= 0) {
                plain.append((char) ('0' + d));
            } else if (c == '\u066B' || c == '.') {
                if (decimalSeen) return Double.NaN;
                decimalSeen = true;
                plain.append('.');
            } else if (c != '\u066C' && c != ',') {
                return Double.NaN;
            }
        }
        if (plain.length() == 0 || ".".contentEquals(plain)) return Double.NaN;
        try {
            double value = Double.parseDouble(plain.toString());
            return "\u2212".equals(sign) || "-".equals(sign) ? -value : value;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int digitValue(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= '\u06F0' && c <= '\u06F9') return c - '\u06F0';
        if (c >= '\u0660' && c <= '\u0669') return c - '\u0660';
        return -1;
    }

    /** Re-render a value with the grouping character and decimal count observed in the model. */
    private static String formatLike(String model, double value) {
        if (model == null || Double.isNaN(value) || Double.isInfinite(value)) return null;
        char group = model.indexOf('\u066C') >= 0 ? '\u066C' : model.indexOf(',') >= 0 ? ',' : 0;
        int decimals = 0;
        int dot = model.indexOf('\u066B');
        if (dot < 0) dot = model.indexOf('.');
        if (dot >= 0) {
            for (int i = dot + 1; i < model.length(); i++) {
                if (digitValue(model.charAt(i)) >= 0) decimals++;
            }
        }
        boolean persian = model.indexOf('\u06F0') >= 0 || model.indexOf('\u0660') >= 0
                || model.indexOf('\u066C') >= 0 || model.indexOf('\u066B') >= 0;
        double rounded = Math.floor(value * Math.pow(10d, decimals) + 0.5d) / Math.pow(10d, decimals);
        long whole = (long) rounded;
        long fraction = Math.round((rounded - whole) * Math.pow(10d, decimals));
        StringBuilder out = new StringBuilder();
        String wholeDigits = Long.toString(Math.abs(whole));
        for (int i = 0; i < wholeDigits.length(); i++) {
            if (group != 0 && i > 0 && (wholeDigits.length() - i) % 3 == 0) out.append(group);
            out.append(persian ? (char) ('\u06F0' + (wholeDigits.charAt(i) - '0')) : wholeDigits.charAt(i));
        }
        if (decimals > 0) {
            out.append(persian ? '\u066B' : '.');
            String fractionDigits = Long.toString(fraction);
            for (int pad = fractionDigits.length(); pad < decimals; pad++) {
                out.append(persian ? '\u06F0' : '0');
            }
            for (int i = 0; i < fractionDigits.length() && i < decimals; i++) {
                out.append(persian ? (char) ('\u06F0' + (fractionDigits.charAt(i) - '0')) : fractionDigits.charAt(i));
            }
        }
        return out.toString();
    }
}
