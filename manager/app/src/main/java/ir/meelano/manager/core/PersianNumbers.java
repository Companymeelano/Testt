package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads numbers in Persian words, so the offline voice says amounts clearly
 * (for example "۱۲۰۰۰" becomes "دوازده هزار"). Used only for speech, never for display.
 */
public final class PersianNumbers {
    private PersianNumbers() { }

    private static final String[] ONES = {"", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"};
    private static final String[] TEENS = {"ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده",
            "شانزده", "هفده", "هجده", "نوزده"};
    private static final String[] TENS = {"", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"};
    private static final String[] HUNDREDS = {"", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد",
            "هفتصد", "هشتصد", "نهصد"};
    private static final String[] SCALES = {"", "هزار", "میلیون", "میلیارد", "تریلیون"};

    /** Replaces every whole number in the text (Latin or Persian digits, thousands commas) with words. */
    public static String speakable(String text) {
        if (text == null || text.isEmpty()) return "";
        String t = toLatinDigits(text);
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = t.length();
        while (i < n) {
            char ch = t.charAt(i);
            if (isDigit(ch)) {
                StringBuilder digits = new StringBuilder();
                int j = i;
                while (j < n) {
                    char c = t.charAt(j);
                    if (isDigit(c)) {
                        digits.append(c);
                    } else if (c == ',' && j + 1 < n && isDigit(t.charAt(j + 1))) {
                        // thousands separator inside a number
                    } else {
                        break;
                    }
                    j++;
                }
                out.append(words(digits.toString()));
                i = j;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String toLatinDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u06F0' && c <= '\u06F9') {
                b.append((char) ('0' + (c - '\u06F0')));
            } else if (c >= '\u0660' && c <= '\u0669') {
                b.append((char) ('0' + (c - '\u0660')));
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /** Words for a string of ASCII digits. */
    static String words(String digits) {
        String s = digits.replaceFirst("^0+(?=\\d)", "");
        if (s.isEmpty() || "0".equals(s)) return "صفر";
        if (s.length() > 15) {
            // Too long to name: read it digit by digit.
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (i > 0) b.append(' ');
                int d = s.charAt(i) - '0';
                b.append(d == 0 ? "صفر" : ONES[d]);
            }
            return b.toString();
        }
        List<String> parts = new ArrayList<>();
        int groups = (s.length() + 2) / 3;
        for (int g = groups - 1; g >= 0; g--) {
            int end = s.length() - g * 3;
            int start = Math.max(0, end - 3);
            int v = Integer.parseInt(s.substring(start, end));
            if (v == 0) continue;
            String w = threeDigits(v);
            if (g > 0) w = (v == 1 && g == 1) ? SCALES[1] : w + " " + SCALES[g];
            parts.add(w);
        }
        return join(parts);
    }

    /** Joins with " و " (plain loop: works on every Android API level). */
    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) b.append(" و ");
            b.append(parts.get(i));
        }
        return b.toString();
    }

    private static String threeDigits(int v) {
        int h = v / 100;
        int r = v % 100;
        List<String> p = new ArrayList<>();
        if (h > 0) p.add(HUNDREDS[h]);
        if (r >= 10 && r < 20) {
            p.add(TEENS[r - 10]);
        } else if (r >= 20) {
            String tens = TENS[r / 10];
            if (r % 10 > 0) tens = tens + " و " + ONES[r % 10];
            p.add(tens);
        } else if (r > 0) {
            p.add(ONES[r]);
        }
        return join(p);
    }
}
