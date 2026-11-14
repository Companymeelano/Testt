package ir.meelano.manager.core;

import java.util.Locale;

/**
 * تجزیهٔ عدد فارسی/عربی با حفظ متن خام کاربر.
 * - ارقام فارسی ۰-۹ و عربی ٠-٩ به لاتین نگاشت می‌شوند.
 * - جداکنندهٔ هزارگان (٬ ، , و فاصلهٔ باریک) حذف می‌شود.
 * - جداکنندهٔ اعشار (٫ . ٫) به نقطه یکسان می‌شود.
 * - متن خالی → null (فیلد خالی، نه صفر)؛ متن ناقص «1.» حفظ می‌شود اما مقدار عددی ندارد.
 * - متن کاملاً نامعتبر → null + پیام خطا، نه 0 بی‌صدا.
 */
public final class PersianNumber {
    private PersianNumber() {}

    public static final class Result {
        public final String raw;      // همان که کاربر تایپ کرد (بدون trim اضافی)
        public final Double value;    // null اگر خالی یا نامعتبر
        public final String error;    // null اگر OK
        Result(String raw, Double value, String error) { this.raw = raw; this.value = value; this.error = error; }
        public boolean isEmpty() { return raw == null || raw.trim().isEmpty(); }
        public boolean isValid() { return error == null && value != null; }
    }

    /** فارسی/عربی → لاتین + یکسان‌سازی جداکننده‌ها */
    static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch == '٫' || ch == '٬' || ch == '،' || ch == '٬') {
                // ٫ (U+066B) اعشار، ٬ (U+066C) هزارگان، ، (U+060C) فارسی
                if (ch == '٫') b.append('.');
                else b.append(','); // هزارگان → بعداً حذف
            } else b.append(ch);
        }
        return b.toString();
    }

    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Result(raw, null, null);
        String n = normalize(raw).trim();
        // حذف جداکنندهٔ هزارگان (کاما و فاصله)
        n = n.replace(",", "").replace("\u00A0", "").replace("\u202F", "").replace(" ", "");
        if (n.isEmpty()) return new Result(raw, null, null);
        // اعشار ناقص «1.» یا «.5» را بپذیر اما مقدار نداشته باشد تا کاربر ادامه دهد
        if (n.equals(".") || n.equals("-") || n.equals("-.")) return new Result(raw, null, null);
        if (n.endsWith(".")) {
            // «12.» → معتبرِ ناقص، برای live compute همان 12 حساب کن اما error نده
            String withoutDot = n.substring(0, n.length() - 1);
            try { return new Result(raw, Double.parseDouble(withoutDot), null); } catch (Exception e) { /* fallthrough */ }
        }
        if (n.startsWith(".")) n = "0" + n;
        if (n.startsWith("-.")) n = "-0." + n.substring(2);
        try {
            double v = Double.parseDouble(n);
            if (Double.isNaN(v) || Double.isInfinite(v)) return new Result(raw, null, "عدد خارج از محدوده است");
            return new Result(raw, v, null);
        } catch (NumberFormatException e) {
            return new Result(raw, null, "عدد نامعتبر است");
        }
    }

    /** مقدار عددی یا fallback؛ برای محاسبات زنده که خالی=0 تلقی می‌شود */
    public static double valueOrZero(String raw) {
        Result r = parse(raw);
        return r.value != null ? r.value : 0;
    }

    public static String formatFa(double v) {
        if (v == Math.rint(v)) return Money.fa(String.valueOf((long) v));
        return Money.fa(String.format(Locale.US, "%.2f", v));
    }
}
