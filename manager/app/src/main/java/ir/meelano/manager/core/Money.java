package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.List;

/** Persian money / number formatting. Atiran stores Rials; the UI uses the selected display unit. */
public final class Money {
    private Money() { }

    /**
     * Display unit. Atiran stores every amount in Rials, but Iranian speakers
     * read and say Tomans, so a column of Rial figures is routinely misread by a
     * factor of ten. Defaulting to Toman and converting on the way out keeps the
     * stored data untouched while making the tables readable.
     */
    public static final int UNIT_RIAL = 0;
    public static final int UNIT_TOMAN = 1;
    private static int unit = UNIT_TOMAN;

    public static void setUnit(int u) { unit = (u == UNIT_RIAL) ? UNIT_RIAL : UNIT_TOMAN; }
    public static int unit() { return unit; }
    public static boolean isToman() { return unit == UNIT_TOMAN; }

    /** Rial amount → amount in the selected display unit. */
    public static double toUnit(double rial) { return unit == UNIT_TOMAN ? rial / 10.0 : rial; }

    /** «تومان» or «ریال». */
    public static String unitName() { return unit == UNIT_TOMAN ? "تومان" : "ریال"; }

    /** Latin or Arabic-Indic digits → Persian digits through the shared display formatter. */
    public static String fa(String s) {
        return HomeNumberFormatter.persianDigits(s);
    }

    /** Persian digits → Latin digits (for parsing user input). */
    public static String en(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else b.append(ch);
        }
        return b.toString();
    }

    /** Full Rial amount in the selected display unit, RTL-safe and kept next to its unit. */
    public static String rial(double v) {
        return HomeNumberFormatter.money(v);
    }

    /** Shared text-safe money display; retained as a compatibility alias for existing callers. */
    public static String rialText(double v) {
        return HomeNumberFormatter.money(v);
    }

    /** Isolate a numeric run for correct bidi ordering in RTL controls. */
    public static String ltrIsolate(String value) {
        return HomeNumberFormatter.isolateLtr(value);
    }

    /** Text-safe quantity, including a visible zero and stable sign. */
    public static String qtyText(double value) {
        return HomeNumberFormatter.quantity(value);
    }

    /** Compact amount with the current unit for tiles/cards. */
    public static String compactMoney(double v) {
        return HomeNumberFormatter.compactMoney(v);
    }

    /** Rounded, grouped whole number. */
    public static String num(double v) {
        return HomeNumberFormatter.rounded(v);
    }

    public static String num(long v) {
        return HomeNumberFormatter.integer(v);
    }

    /** Quantity with an explicit sign inside one LTR isolate. */
    public static String signedQty(double v) {
        return HomeNumberFormatter.signedQuantity(v);
    }

    /** Quantity with up to three fractional digits. */
    public static String qty(double v) {
        return HomeNumberFormatter.quantity(v);
    }

    /** Compact amount for chart axes and inline values. */
    public static String compact(double v) {
        return HomeNumberFormatter.compactUnit(v);
    }

    /** Compact amount with the current display unit. */
    public static String compactRial(double v) {
        return HomeNumberFormatter.compactMoney(v);
    }

    /** Percentage with Persian digits, decimal separator, and trailing percent mark. */
    public static String pct(double v) {
        return HomeNumberFormatter.percent(v);
    }

    // ---------------- amount in words ----------------
    private static final String[] W_ONES = {"", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده",
            "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"};
    private static final String[] W_TENS = {"", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"};
    private static final String[] W_HUNDREDS = {"", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"};
    private static final String[] W_SCALE = {"", " هزار", " میلیون", " میلیارد", " هزار میلیارد"};

    private static String words999(int n) {
        StringBuilder b = new StringBuilder();
        if (n >= 100) { b.append(W_HUNDREDS[n / 100]); n %= 100; }
        if (n >= 20) { if (b.length() > 0) b.append(" و "); b.append(W_TENS[n / 10]); n %= 10; }
        if (n > 0) { if (b.length() > 0) b.append(" و "); b.append(W_ONES[n]); }
        return b.toString();
    }

    /** Amount in words follows the selected display unit (database values stay in Rials). */
    public static String words(double value) {
        double selected = toUnit(value);
        long v = Math.abs(Math.round(selected));
        if (v == 0) return "صفر " + unitName();
        if (v >= 1_000_000_000_000_000L) return rial(value);
        List<String> parts = new ArrayList<>();
        int scale = 0;
        while (v > 0) {
            int chunk = (int) (v % 1000);
            if (chunk > 0) parts.add(0, words999(chunk) + W_SCALE[scale]);
            v /= 1000; scale++;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) b.append(" و "); b.append(parts.get(i)); }
        return (selected < 0 ? "منفی " : "") + b + " " + unitName();
    }
}
