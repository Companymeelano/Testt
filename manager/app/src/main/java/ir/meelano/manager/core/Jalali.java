package ir.meelano.manager.core;

import java.util.Calendar;
import java.util.Locale;

/** Persian (Jalali) calendar arithmetic for Atiran's "1405/07/06" dates (day numbers, +/- days, month names). */
public final class Jalali {
    private Jalali() { }

    private static final int[] BREAKS = {-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178};
    public static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};

    /** {leap offset, gregorian year, march day} of the Jalali year (jalaali-js algorithm). */
    private static int[] jalCal(int jy) {
        int bl = BREAKS.length, gy = jy + 621, leapJ = -14, jp = BREAKS[0], jm = 0, jump = 0, n;
        for (int i = 1; i < bl; i++) {
            jm = BREAKS[i]; jump = jm - jp;
            if (jy < jm) break;
            leapJ = leapJ + div(jump, 33) * 8 + div(mod(jump, 33), 4);
            jp = jm;
        }
        n = jy - jp;
        leapJ = leapJ + div(n, 33) * 8 + div(mod(n, 33) + 3, 4);
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1;
        int leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150;
        int march = 20 + leapJ - leapG;
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33;
        int leap = mod(mod(n + 1, 33) - 1, 4);
        if (leap == -1) leap = 4;
        return new int[]{leap, gy, march};
    }

    // Truncating division, exactly as in the reference algorithm (not floor).
    private static int div(int a, int b) { return a / b; }
    private static int mod(int a, int b) { return a - (a / b) * b; }

    private static int g2d(int gy, int gm, int gd) {
        int d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408;
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752;
        return d;
    }

    private static int[] d2g(int jdn) {
        int j = 4 * jdn + 139361631;
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908;
        int i = div(mod(j, 1461), 4) * 5 + 308;
        int gd = div(mod(i, 153), 5) + 1;
        int gm = mod(div(i, 153), 12) + 1;
        int gy = div(j, 1461) - 100100 + div(8 - gm, 6);
        return new int[]{gy, gm, gd};
    }

    /** Julian day number of a Gregorian date (same scale as toDay). */
    public static int gregorianDay(int gy, int gm, int gd) { return g2d(gy, gm, gd); }

    /** Julian day number of a Jalali date. */
    public static int toDay(int jy, int jm, int jd) {
        int[] r = jalCal(jy);
        return g2d(r[1], 3, r[2]) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1;
    }

    public static int[] fromDay(int jdn) {
        int[] g = d2g(jdn);
        int gy = g[0];
        int jy = gy - 621;
        int[] r = jalCal(jy);
        int jdn1f = g2d(gy, 3, r[2]);
        int k = jdn - jdn1f;
        if (k >= 0) {
            if (k <= 185) return new int[]{jy, 1 + div(k, 31), mod(k, 31) + 1};
            k -= 186;
        } else {
            jy -= 1; k += 179;
            if (r[0] == 1) k += 1;
        }
        return new int[]{jy, 7 + div(k, 30), mod(k, 30) + 1};
    }

    /** Day number of "1405/07/06" (digits may be Persian); -1 when the text is not a date. */
    public static int parse(String text) {
        int[] ymd = splitYmd(text);
        if (ymd == null) return -1;
        int y = ymd[0], m = ymd[1], d = ymd[2];
        if (y < 1300 || y > 1600 || m < 1 || m > 12 || d < 1 || d > daysInMonth(y, m)) return -1;
        return toDay(y, m, d);
    }

    /** Split digits out of a date-like text (fa/ar digits accepted); null when unparsable. */
    private static int[] splitYmd(String text) {
        if (text == null) return null;
        StringBuilder b = new StringBuilder();
        for (char ch : text.trim().toCharArray()) {
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else b.append(ch);
        }
        String t = b.toString().trim();
        // Bare «YYYYMMDD» (some Atiran columns store dates without separators).
        if (t.length() >= 8 && isDigit8(t.substring(0, 8)) && (t.length() == 8 || !Character.isDigit(t.charAt(8)))) {
            try {
                return new int[]{Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(4, 6)), Integer.parseInt(t.substring(6, 8))};
            } catch (Exception e) { return null; }
        }
        String[] p = t.split("[/\\-]");
        if (p.length < 3) return null;
        try {
            String dd = p[2].trim();
            if (dd.length() > 2) dd = dd.substring(0, 2);
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(dd)};
        } catch (Exception e) { return null; }
    }

    private static boolean isDigit8(String s) {
        if (s == null || s.length() != 8) return false;
        for (int i = 0; i < 8; i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    // =====================================================================================
    // Central display normalizer — EVERY date shown in the UI must go through disp().
    // Atiran stores dates as Jalali text («1405/07/06»), bare digits («14050706»),
    // Gregorian datetimes («2026-10-07 10:30:00») or placeholders («--», «1499/12/29»).
    // disp() folds all of them into «YYYY/MM/DD», or "" when there is no real date.
    // =====================================================================================
    /** Years beyond today+8 are Atiran placeholders (e.g. putchk «1499/12/29»), not real dates. */
    public static boolean isPlaceholderYear(int jy) {
        int[] now = fromDay(today());
        return jy < 1300 || jy > now[0] + 8;
    }

    /** Normalize ANY Atiran date representation to Jalali «YYYY/MM/DD»; "" when none. */
    public static String disp(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty() || t.startsWith("--")) return "";
        // Gregorian leading date («2026-10-07…») → Jalali; time suffix dropped.
        String head = t.length() >= 10 ? t.substring(0, 10) : t;
        if (head.length() == 10 && head.charAt(4) == '-' && head.charAt(7) == '-') {
            boolean greg = true;
            for (int i = 0; i < 10; i++) {
                if (i == 4 || i == 7) continue;
                char ch = head.charAt(i);
                if (ch < '0' || ch > '9') { greg = false; break; }
            }
            if (greg) {
                try {
                    int y = Integer.parseInt(head.substring(0, 4));
                    int mo = Integer.parseInt(head.substring(5, 7));
                    int d = Integer.parseInt(head.substring(8, 10));
                    if (mo < 1 || mo > 12 || d < 1 || d > 31) return "";
                    return format(g2d(y, mo, d));
                } catch (Exception e) {
                    return "";
                }
            }
            return "";
        }
        // Jalali text (any separator, optional time suffix, fa/ar digits, YYYYMMDD).
        String tok = t.split("[\\sT]")[0];
        int[] ymd = splitYmd(tok);
        if (ymd == null) return "";
        int y = ymd[0], mo = ymd[1], d = ymd[2];
        if (isPlaceholderYear(y) || mo < 1 || mo > 12 || d < 1 || d > daysInMonth(y, mo)) return "";
        return String.format(Locale.US, "%04d/%02d/%02d", y, mo, d);
    }

    /** disp() with Persian digits, or «—» when there is no real date. Never "". */
    public static String dispFa(String raw) {
        String d = disp(raw);
        return d.isEmpty() ? "—" : Money.fa(d);
    }

    /** Days in a Jalali month (leap Esfand handled via day arithmetic, no leap tables). */
    public static int daysInMonth(int jy, int jm) {
        if (jm <= 0) return 30;
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        return toDay(jy + 1, 1, 1) - toDay(jy, 12, 1) == 30 ? 30 : 29;
    }

    /** Clamp any date-like text to a valid Jalali «YYYY/MM/DD» (today when unparsable). */
    public static String normalizeDate(String text) {
        int[] ymd = splitYmd(text);
        if (ymd == null) return todayStr();
        int y = Math.max(1300, Math.min(1600, ymd[0]));
        int m = Math.max(1, Math.min(12, ymd[1]));
        int d = Math.max(1, Math.min(daysInMonth(y, m), ymd[2]));
        return String.format(Locale.US, "%04d/%02d/%02d", y, m, d);
    }

    public static String format(int day) {
        int[] j = fromDay(day);
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
    }

    public static int today() {
        Calendar c = Calendar.getInstance();
        return g2d(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static String addDays(String date, int days) {
        int d = parse(date);
        return d < 0 ? date : format(d + days);
    }

    /** "1405/07/14" for today. */
    /** Days from a to b (b − a); −1 when either side is unparseable. Accepts any Atiran date form. */
    public static int diffDays(String a, String b) {
        int da = parse(disp(a));
        int db = parse(disp(b));
        return da < 0 || db < 0 ? -1 : db - da;
    }

    public static String todayStr() {
        return format(today());
    }

    /** "1405/07/01" for the month of the given date. */
    public static String monthStart(String date) {
        int d = parse(date);
        if (d < 0) return date;
        int[] j = fromDay(d);
        return String.format(Locale.US, "%04d/%02d/01", j[0], j[1]);
    }

    /** "1405/01/01" for the Jalali year of the given date (start of the fiscal year). */
    public static String yearStart(String date) {
        int d = parse(date);
        if (d < 0) return date;
        int[] j = fromDay(d);
        return String.format(Locale.US, "%04d/01/01", j[0]);
    }

    public static String monthName(String date) {
        int d = parse(date);
        if (d < 0) return "";
        int[] j = fromDay(d);
        return MONTHS[j[1] - 1];
    }

    /** Short label for charts: "۶ مهر". Accepts any Atiran date form (incl. Gregorian). */
    public static String shortLabel(String date) {
        String norm = disp(date);
        int d = norm.isEmpty() ? -1 : parse(norm);
        if (d < 0) return date == null ? "" : date;
        int[] j = fromDay(d);
        return j[2] + " " + MONTHS[j[1] - 1];
    }

    private static final String[] WEEKDAYS = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"};

    public static String weekday(String date) {
        int d = parse(date);
        if (d < 0) return "";
        // Julian day 0 was a Monday; Saturday is the first day of the Persian week.
        return WEEKDAYS[((d + 2) % 7 + 7) % 7];
    }

    /**
     * Display helper: converts a leading Gregorian date («2026-10-07…», as stored by
     * datetime columns) to Jalali; Jalali text passes through untouched. Any suffix
     * (e.g. a time part) is kept. Never throws.
     */
    public static String faDate(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() < 10) return t;
        String head = t.substring(0, 10);
        if (head.length() != 10 || head.charAt(4) != '-' || head.charAt(7) != '-') return t;
        for (int i = 0; i < 10; i++) {
            if (i == 4 || i == 7) continue;
            char ch = head.charAt(i);
            if (ch < '0' || ch > '9') return t;
        }
        try {
            int y = Integer.parseInt(head.substring(0, 4));
            int m = Integer.parseInt(head.substring(5, 7));
            int d = Integer.parseInt(head.substring(8, 10));
            if (m < 1 || m > 12 || d < 1 || d > 31) return t;
            return format(g2d(y, m, d)) + t.substring(10);
        } catch (Exception e) {
            return t;
        }
    }

    /** Jalali «YYYY/MM/DD» → Gregorian «YYYY-MM-DD» (for datetime columns); "" when invalid. */
    public static String toGregorian(String jalali) {
        int day = parse(jalali);
        if (day < 0) return "";
        int[] g = d2g(day);
        return String.format(Locale.US, "%04d-%02d-%02d", g[0], g[1], g[2]);
    }

    /** Today's Gregorian date «YYYY-MM-DD». */
    public static String todayGregorian() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
