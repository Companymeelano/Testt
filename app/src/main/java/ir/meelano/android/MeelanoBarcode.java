package ir.meelano.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * موتور بارکد «پخش درخشان» — Code128 (خودکار B/C)، Code39 و EAN-13، بدون کتابخانهٔ بیرونی.
 *
 * <p>کاربردها: برچسب انبار (کد کالا، شمارهٔ سری، تاریخ انقضا)، بارکد فاکتور و رسید فروشگاه،
 * و کدگذاری عددی روی برچسب قفسه. جدول‌های الگو از کتابخانهٔ مرجع python-barcode استخراج و
 * بی‌واسطه تولید شده‌اند و منطق کدگذاری با یک کدگشا در tools/barcode_reference_check.py
 * آزمون رفت‌وبرگشت می‌شود.</p>
 */
final class MeelanoBarcode {
    private MeelanoBarcode() { }

    static final int KIND_CODE128 = 1;
    static final int KIND_CODE39 = 2;
    static final int KIND_EAN13 = 3;

    /** الگوهای Code128 برای مقادیر ۰ تا ۱۰۵ (هر الگو ۱۱ ماژول). */
    private static final String[] C128 = {
            "11011001100", "11001101100", "11001100110", "10010011000", "10010001100", "10001001100",
            "10011001000", "10011000100", "10001100100", "11001001000", "11001000100", "11000100100",
            "10110011100", "10011011100", "10011001110", "10111001100", "10011101100", "10011100110",
            "11001110010", "11001011100", "11001001110", "11011100100", "11001110100", "11101101110",
            "11101001100", "11100101100", "11100100110", "11101100100", "11100110100", "11100110010",
            "11011011000", "11011000110", "11000110110", "10100011000", "10001011000", "10001000110",
            "10110001000", "10001101000", "10001100010", "11010001000", "11000101000", "11000100010",
            "10110111000", "10110001110", "10001101110", "10111011000", "10111000110", "10001110110",
            "11101110110", "11010001110", "11000101110", "11011101000", "11011100010", "11011101110",
            "11101011000", "11101000110", "11100010110", "11101101000", "11101100010", "11100011010",
            "11101111010", "11001000010", "11110001010", "10100110000", "10100001100", "10010110000",
            "10010000110", "10000101100", "10000100110", "10110010000", "10110000100", "10011010000",
            "10011000010", "10000110100", "10000110010", "11000010010", "11001010000", "11110111010",
            "11000010100", "10001111010", "10100111100", "10010111100", "10010011110", "10111100100",
            "10011110100", "10011110010", "11110100100", "11110010100", "11110010010", "11011011110",
            "11011110110", "11110110110", "10101111000", "10100011110", "10001011110", "10111101000",
            "10111100010", "11110101000", "11110100010", "10111011110", "10111101110", "11101011110",
            "11110101110", "11010000100", "11010010000", "11010011100",
    };
    /** الگوی پایانی استاندارد: ۱۱ ماژول کاراکتر STOP + نوار پایانی «11» (۱۳ ماژول). */
    private static final String C128_STOP = "1100011101011";
    private static final int C128_START_B = 104, C128_START_C = 105, C128_TO_C = 99, C128_TO_B = 100;

    /** الگوهای Code39 به ترتیب حروف «0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%» (هر الگو ۱۵ ماژول). */
    private static final String[] C39 = {
            "101000111011101", "111010001010111", "101110001010111", "111011100010101", "101000111010111",
            "111010001110101", "101110001110101", "101000101110111", "111010001011101", "101110001011101",
            "111010100010111", "101110100010111", "111011101000101", "101011100010111", "111010111000101",
            "101110111000101", "101010001110111", "111010100011101", "101110100011101", "101011100011101",
            "111010101000111", "101110101000111", "111011101010001", "101011101000111", "111010111010001",
            "101110111010001", "101010111000111", "111010101110001", "101110101110001", "101011101110001",
            "111000101010111", "100011101010111", "111000111010101", "100010111010111", "111000101110101",
            "100011101110101", "100010101110111", "111000101011101", "100011101011101", "100010001000101",
            "100010001010001", "100010100010001", "101000100010001",
    };
    private static final String C39_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";
    private static final String C39_EDGE = "100010111011101";
    private static final String C39_GAP = "0";

    /** EAN-13: مجموعه‌های A(چپ فرد)، B(چپ زوج)، C(راست) و الگوی زوجیت رقم اول. */
    private static final String[] EAN_A = {"0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011",};
    private static final String[] EAN_B = {"0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111",};
    private static final String[] EAN_C = {"1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100",};
    private static final String[] EAN_PARITY = {"AAAAAA", "AABABB", "AABBAB", "AABBBA", "ABAABB", "ABBAAB", "ABBBAA", "ABABAB", "ABABBA", "ABBABA"};
    private static final String EAN_EDGE = "101", EAN_MIDDLE = "01010";


    // ------------------------------------------------------------ API
    /** انتخاب هوشمند نوع بارکد: ۱۲/۱۳ رقم معتبر ⇒ EAN-13، در غیر این‌صورت Code128. */
    static int kindFor(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 12 && t.matches("\\d{12}") && ean13Valid(t + ean13Check(t))) return KIND_EAN13;
        if (t.length() == 13 && t.matches("\\d{13}") && ean13Valid(t)) return KIND_EAN13;
        return KIND_CODE128;
    }

    /** الگوی ماژول‌ها (۱ = میلهٔ سیاه) برای هر نوع بارکد. */
    static String modules(String text, int kind) {
        try {
            if (kind == KIND_EAN13) {
                String t = text == null ? "" : text.trim();
                if (t.length() == 12) t = t + ean13Check(t);
                return ean13(t);
            }
            if (kind == KIND_CODE39) return code39(text, false);
            return code128(text);
        } catch (Exception e) {
            try { return code128(text); } catch (Exception ignored) { return ""; }
        }
    }

    /** Code128 با انتخاب خودکار مجموعهٔ B/C (رقم‌ها به‌صورت دوتایی و فشرده کد می‌شوند). */
    static String code128(String data) {
        String s = data == null ? "" : data;
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int[] values = new int[s.length() + 2];
        int n = 0;
        int i = 0;
        boolean modeC = false;
        int digitRun = leadingDigits(s, 0);
        if (s.length() >= 4 && digitRun == s.length() && s.length() % 2 == 0) {
            modeC = true;
            values[n++] = C128_START_C;
        } else if (digitRun >= 4 && digitRun % 2 == 0) {
            values[n++] = C128_START_C;
            modeC = true;
        } else {
            values[n++] = C128_START_B;
        }
        while (i < s.length()) {
            if (modeC) {
                if (i + 1 < s.length() && isDigit(s.charAt(i)) && isDigit(s.charAt(i + 1))) {
                    values[n++] = (s.charAt(i) - '0') * 10 + (s.charAt(i + 1) - '0');
                    i += 2;
                } else {
                    values[n++] = C128_TO_B;
                    modeC = false;
                }
            } else {
                int run = leadingDigits(s, i);
                if (run >= 6 && run % 2 == 0) {
                    values[n++] = C128_TO_C;
                    modeC = true;
                } else {
                    char c = s.charAt(i);
                    if (c < 32 || c > 126) throw new IllegalArgumentException("کاراکتر غیرمجاز در Code128: " + c);
                    values[n++] = c - 32;
                    i++;
                }
            }
        }
        int sum = values[0];
        for (int k = 1; k < n; k++) sum += values[k] * k;
        int check = sum % 103;
        out.append(C128[values[0]]);
        for (int k = 1; k < n; k++) out.append(C128[values[k]]);
        out.append(C128[check]);
        out.append(C128_STOP);
        return out.toString();
    }

    /** Code39 (حروف بزرگ، ارقام و - . space $ / + %) با ستارهٔ شروع/پایان. */
    static String code39(String data, boolean withChecksum) {
        String s = (data == null ? "" : data).toUpperCase(java.util.Locale.US);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (C39_ALPHABET.indexOf(c) < 0) throw new IllegalArgumentException("کاراکتر غیرمجاز در Code39: " + c);
            body.append(c);
        }
        if (withChecksum) {
            int sum = 0;
            for (int i = 0; i < body.length(); i++) sum += C39_ALPHABET.indexOf(body.charAt(i));
            body.append(C39_ALPHABET.charAt(sum % 43));
        }
        StringBuilder out = new StringBuilder(C39_EDGE);
        out.append(C39_GAP);
        for (int i = 0; i < body.length(); i++) {
            out.append(C39[C39_ALPHABET.indexOf(body.charAt(i))]);
            out.append(C39_GAP);
        }
        out.append(C39_EDGE);
        return out.toString();
    }

    /** رقم کنترل EAN-13 برای ۱۲ رقم اول. */
    static int ean13Check(String twelve) {
        String t = twelve == null ? "" : twelve.trim();
        if (t.length() > 12) t = t.substring(0, 12);
        int sum = 0;
        for (int i = 0; i < t.length(); i++) {
            int d = t.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    static boolean ean13Valid(String code13) {
        String t = code13 == null ? "" : code13.trim();
        if (t.length() != 13 || !t.matches("\\d{13}")) return false;
        return (t.charAt(12) - '0') == ean13Check(t.substring(0, 12));
    }

    /** EAN-13 کامل (۱۳ رقم؛ اگر ۱۲ رقم بدهید رقم کنترل اضافه می‌شود). */
    static String ean13(String code) {
        String t = code == null ? "" : code.trim();
        if (t.length() == 12) t = t + ean13Check(t);
        if (t.length() != 13 || !t.matches("\\d{13}")) throw new IllegalArgumentException("EAN-13 باید ۱۳ رقم باشد");
        int first = t.charAt(0) - '0';
        String parity = EAN_PARITY[first];
        StringBuilder out = new StringBuilder(EAN_EDGE);
        for (int i = 1; i <= 6; i++) {
            int d = t.charAt(i) - '0';
            out.append(parity.charAt(i - 1) == 'A' ? EAN_A[d] : EAN_B[d]);
        }
        out.append(EAN_MIDDLE);
        for (int i = 7; i <= 12; i++) out.append(EAN_C[t.charAt(i) - '0']);
        out.append(EAN_EDGE);
        return out.toString();
    }

    // ------------------------------------------------------------ رسم
    /** رسم بارکد در محدودهٔ داده‌شده. در صورت true بودن با متن، ۲۸٪ پایین برای شمارهٔ خوانا است. */
    static void draw(Canvas canvas, String text, int kind, RectF area, int barColor, int bgColor, boolean withText, float textSize) {
        String modules = modules(text, kind);
        if (modules.isEmpty()) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (bgColor != Color.TRANSPARENT) {
            p.setColor(bgColor);
            canvas.drawRect(area, p);
        }
        float textHeight = withText ? area.height() * 0.28f : 0;
        float barHeight = area.height() - textHeight;
        float unit = area.width() / modules.length();
        p.setColor(barColor);
        int i = 0;
        while (i < modules.length()) {
            if (modules.charAt(i) != '1') { i++; continue; }
            int start = i;
            while (i < modules.length() && modules.charAt(i) == '1') i++;
            canvas.drawRect(area.left + start * unit, area.top, area.left + i * unit, area.top + barHeight, p);
        }
        if (withText) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(barColor);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(textSize > 0 ? textSize : Math.max(8f, barHeight * 0.22f));
            String human = text == null ? "" : MeelanoCharts.fa(text.trim());
            canvas.drawText(human, area.centerX(), area.bottom - textHeight * 0.18f, t);
        }
    }

    /** بارکد به‌صورت Bitmap (برای PDF، اشتراک‌گذاری و چاپ برچسب). */
    static Bitmap bitmap(String text, int kind, int widthPx, int heightPx, int barColor, int bgColor, boolean withText) {
        String modules = modules(text, kind);
        if (modules.isEmpty()) return null;
        int w = Math.max(32, widthPx), h = Math.max(16, heightPx);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(bgColor == Color.TRANSPARENT ? Color.WHITE : bgColor);
        draw(canvas, text, kind, new RectF(0, 0, w, h), barColor, Color.TRANSPARENT, withText, 0);
        return bmp;
    }

    /** رشتهٔ ماژول‌ها برای آزمون (اشتراک با مرجع پایتون). */
    static String toBinary(String text, int kind) { return modules(text, kind); }

    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    private static int leadingDigits(String s, int from) {
        int i = from;
        while (i < s.length() && isDigit(s.charAt(i))) i++;
        return i - from;
    }
}
