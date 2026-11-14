package ir.meelano.windowsinvoice;

import java.time.LocalDate;
import java.time.ZoneId;

/** Small, dependency-free Jalali date conversion used for Atiran's yyyy/MM/dd date text. */
public final class PersianDate {
    private static final int[] GREGORIAN_DAYS = {0,31,59,90,120,151,181,212,243,273,304,334};
    private static final int[] JALALI_BREAKS = {-61,9,38,199,426,686,756,818,1111,1181,1210,1635,2060,2097,2192,2262,2324,2394,2456,3178};
    private PersianDate() { }

    public static String today() {
        return fromGregorian(LocalDate.now(ZoneId.of("Asia/Tehran")));
    }

    public static String fromGregorian(LocalDate date) {
        int gy = date.getYear();
        int gm = date.getMonthValue();
        int gd = date.getDayOfMonth();
        int jy;
        int days;
        if (gy > 1600) {
            jy = 979;
            gy -= 1600;
        } else {
            jy = 0;
            gy -= 621;
        }
        int gyForLeap = gm > 2 ? gy + 1 : gy;
        days = 365 * gy + (gyForLeap + 3) / 4 - (gyForLeap + 99) / 100
                + (gyForLeap + 399) / 400 - 80 + gd + GREGORIAN_DAYS[gm - 1];
        jy += 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return String.format(java.util.Locale.ROOT, "%04d/%02d/%02d", jy, jm, jd);
    }

    public static boolean isValidJalaliDate(String value) {
        if (value == null || !value.matches("[0-9]{4}/[0-9]{2}/[0-9]{2}")) return false;
        try {
            String[] parts = value.split("/");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            if (year < JALALI_BREAKS[0] || year >= JALALI_BREAKS[JALALI_BREAKS.length - 1]
                    || month < 1 || month > 12 || day < 1) return false;
            int maxDay = month <= 6 ? 31 : month <= 11 ? 30 : (isLeapYear(year) ? 30 : 29);
            return day <= maxDay;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isLeapYear(int year) {
        int previousBreak = JALALI_BREAKS[0];
        int jump = 0;
        for (int i = 1; i < JALALI_BREAKS.length; i++) {
            int nextBreak = JALALI_BREAKS[i];
            jump = nextBreak - previousBreak;
            if (year < nextBreak) break;
            previousBreak = nextBreak;
        }
        int yearsAfterBreak = year - previousBreak;
        if (jump - yearsAfterBreak < 6)
            yearsAfterBreak = yearsAfterBreak - jump + ((jump + 4) / 33) * 33;
        int leap = ((yearsAfterBreak + 1) % 33 - 1) % 4;
        if (leap == -1) leap = 4;
        return leap == 0;
    }
}
