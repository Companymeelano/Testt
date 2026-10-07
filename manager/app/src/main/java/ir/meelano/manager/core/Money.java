package ir.meelano.manager.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Persian money / number formatting. Amounts are always shown in full rials. */
public final class Money {
    private Money() { }

    /** Latin digits → Persian digits. */
    public static String fa(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('۰' + (ch - '0')) : ch);
        return b.toString();
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

    private static String group(long v) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(v).replace(',', '٬').replace('-', '−');
    }

    /** «۱۲٬۴۵۰٬۰۰۰ ریال» */
    public static String rial(double v) {
        return fa(group(Math.round(v))) + " ریال";
    }

    /** «۱۲٬۴۵۰٬۰۰۰» */
    public static String num(double v) {
        return fa(group(Math.round(v)));
    }

    public static String num(long v) {
        return fa(group(v));
    }

    /** Quantity with up to 3 decimals, trimmed: «۱۲٫۵». */
    public static String qty(double v) {
        if (Math.abs(v - Math.round(v)) < 0.0005) return fa(group(Math.round(v)));
        String s = String.format(java.util.Locale.US, "%.3f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        int dot = s.indexOf('.');
        String ip = dot < 0 ? s : s.substring(0, dot);
        String fp = dot < 0 ? "" : s.substring(dot);
        return fa(groupStr(ip) + fp.replace('.', '٫'));
    }

    private static String groupStr(String ip) {
        boolean neg = ip.startsWith("-");
        if (neg) ip = ip.substring(1);
        StringBuilder b = new StringBuilder();
        int n = ip.length();
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) b.append('٬');
            b.append(ip.charAt(i));
        }
        return neg ? "−" + b : b.toString();
    }

    /** Compact amount for chart axes: «۱۲٫۴ میلیون». */
    public static String compact(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 1e12) s = trim(v / 1e12) + " هزار میلیارد";
        else if (a >= 1e9) s = trim(v / 1e9) + " میلیارد";
        else if (a >= 1e6) s = trim(v / 1e6) + " میلیون";
        else if (a >= 1e3) s = trim(v / 1e3) + " هزار";
        else s = trim(v);
        return fa(s);
    }

    /** Compact amount WITH the Rial unit for tiles/cards: «۱۲٫۴ میلیون ریال». */
    public static String compactRial(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 1e12) s = trim(v / 1e12) + " هزار میلیارد ریال";
        else if (a >= 1e9) s = trim(v / 1e9) + " میلیارد ریال";
        else if (a >= 1e6) s = trim(v / 1e6) + " میلیون ریال";
        else if (a >= 1e3) s = trim(v / 1e3) + " هزار ریال";
        else s = trim(v) + " ریال";
        return fa(s);
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, Math.abs(v) >= 100 ? "%.0f" : "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', '٫').replace('-', '−');
    }

    /** «٪۱۲٫۵» */
    public static String pct(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return "٪" + fa(s.replace('.', '٫').replace('-', '−'));
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

    /** 52500000 → «پنجاه و دو میلیون و پانصد هزار ریال» */
    public static String words(double value) {
        long v = Math.abs(Math.round(value));
        if (v == 0) return "صفر ریال";
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
        return (value < 0 ? "منفی " : "") + b + " ریال";
    }
}
