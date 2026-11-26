package ir.meelano.manager.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * فرمت‌کننده ریالی با جداکننده سه‌رقمی «٬».
 * ورودی/خروجی همیشه ریال است — تبدیل تومان انجام نمی‌شود.
 */
public final class RialFormatter {
    private RialFormatter() {}

    /** عدد ریالی (long) → «۱۲٬۵۰۰٬۰۰۰ ریال» با ارقام فارسی */
    public static String formatRial(long rial) {
        return formatRial((double) rial, true);
    }

    public static String formatRial(double rial) {
        return formatRial(rial, true);
    }

    public static String formatRial(double rial, boolean withUnit) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        String grouped = f.format(Math.round(rial)).replace(',', '٬').replace('-', '−');
        String fa = Money.fa(grouped);
        return withUnit ? fa + " ریال" : fa;
    }

    /** «۱۲٬۵۰۰٬۰۰۰» بدون واحد — برای ورودی فیلد */
    public static String formatGrouped(long rial) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        String grouped = f.format(rial).replace(',', '٬').replace('-', '−');
        return Money.fa(grouped);
    }

    /** متن ورودی (شامل «٬» و ارقام فارسی) → long ریالی. خالی = 0. */
    public static long parseRial(String text) {
        if (text == null) return 0L;
        String t = Money.en(text).replace("٬", "").replace(",", "").replace(" ", "").replace("ریال", "").replace("تومان", "").trim();
        if (t.isEmpty() || t.equals("−") || t.equals("-")) return 0L;
        // Keep minus sign, digits and dot (dot may be from decimal — round)
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '.') b.append(ch);
        }
        String s = b.toString();
        if (s.isEmpty()) return 0L;
        try {
            double d = Double.parseDouble(s);
            return Math.round(d);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** متن ریالی → double (برای مبالغ اعشاری اگر لازم بود). */
    public static double parseRialDouble(String text) {
        if (text == null) return 0d;
        String t = Money.en(text).replace("٬", "").replace(",", "").replace(" ", "").replace("ریال", "").replace("تومان", "").trim();
        if (t.isEmpty()) return 0d;
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '.') b.append(ch);
        try { return Double.parseDouble(b.toString()); } catch (Exception e) { return 0d; }
    }

    /** آیا ساعت فعلی قبل از ۴ صبح است؟ (برای تصمیم دیروز/امروز پوز) */
    public static boolean isBefore4am() {
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            int h = c.get(java.util.Calendar.HOUR_OF_DAY);
            return h < 4;
        } catch (Exception e) { return false; }
    }

    /** درصد کمبود / مازاد نسبت به چک‌ها (برای نمایش گرافیکی) */
    public static double shortagePct(long shortageRial, long totalChecks) {
        if (totalChecks <= 0) return 0;
        return (shortageRial * 100.0) / totalChecks;
    }
}
