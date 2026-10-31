package ir.meelano.manager.core;

/** Jalali calendar quarters for the Home seasonal planner; contains no harvest or holiday claims. */
public final class SeasonalCalendarEngine {
    private SeasonalCalendarEngine() { }

    private static final String[] SEASONS = {"بهار", "تابستان", "پاییز", "زمستان"};

    /** Returns a 0-based season index for Jalali month 1..12, or -1 for an invalid month. */
    public static int seasonIndex(int jalaliMonth) {
        if (jalaliMonth < 1 || jalaliMonth > 12) return -1;
        return (jalaliMonth - 1) / 3;
    }

    public static String seasonName(int index) {
        return index < 0 || index >= SEASONS.length ? "" : SEASONS[index];
    }

    public static int firstMonth(int index) {
        return index < 0 || index >= SEASONS.length ? 0 : index * 3 + 1;
    }

    public static int monthAt(int seasonIndex, int position) {
        if (seasonIndex < 0 || seasonIndex >= SEASONS.length || position < 0 || position > 2) return 0;
        return firstMonth(seasonIndex) + position;
    }
}
