package ir.meelano.manager.core;

/**
 * Advanced filter shared by EVERY section: Jalali date range (preset or custom),
 * search text, visitor / route / customer-group / kala-group / warehouse / bank / user,
 * status key, sorting and row limit.
 */
public final class Filter {
    public static final int P_CUSTOM = 0;
    public static final int P_TODAY = 1;
    public static final int P_YESTERDAY = 2;
    public static final int P_LAST7 = 3;
    public static final int P_LAST30 = 4;
    public static final int P_MONTH = 5;
    public static final int P_YEAR = 6;
    public static final int P_ALL = 7;

    public int preset = P_LAST30;
    /** Atiran «YYYY/MM/DD» (inclusive). Empty = no bound. */
    public String from = "";
    public String to = "";
    public String search = "";
    public int visitor = -1;
    public int route = -1;
    public int custGroup = -1;
    public int kalaGroup = -1;
    public int warehouse = -1;
    public int bank = -1;
    public int user = -1;
    /** Screen-specific status key (e.g. cheque bucket, settled/unsettled). */
    public String status = "";
    public String sort = "";
    public int top = 200;
    public int page = 0;

    public Filter() {
        applyPreset();
    }

    public Filter copy() {
        Filter f = new Filter();
        f.copyFrom(this);
        return f;
    }

    public void copyFrom(Filter o) {
        preset = o.preset; from = o.from; to = o.to; search = o.search;
        visitor = o.visitor; route = o.route; custGroup = o.custGroup;
        kalaGroup = o.kalaGroup; warehouse = o.warehouse; bank = o.bank; user = o.user;
        status = o.status; sort = o.sort; top = o.top; page = o.page;
    }

    /** Fill from/to from the preset, using the Jalali calendar. */
    public void applyPreset() {
        String today = Jalali.todayStr();
        switch (preset) {
            case P_TODAY: from = today; to = today; break;
            case P_YESTERDAY: from = Jalali.addDays(today, -1); to = from; break;
            case P_LAST7: from = Jalali.addDays(today, -6); to = today; break;
            case P_LAST30: from = Jalali.addDays(today, -29); to = today; break;
            case P_MONTH: from = Jalali.monthStart(today); to = today; break;
            case P_YEAR: from = Jalali.yearStart(today); to = today; break;
            case P_ALL: from = ""; to = ""; break;
            default: break;
        }
    }

    public boolean hasRange() {
        return (from != null && !from.trim().isEmpty()) || (to != null && !to.trim().isEmpty());
    }

    public boolean isDefault() {
        return preset == P_LAST30 && (search == null || search.trim().isEmpty())
                && visitor < 0 && route < 0 && custGroup < 0 && kalaGroup < 0
                && warehouse < 0 && bank < 0 && user < 0
                && (status == null || status.isEmpty()) && (sort == null || sort.isEmpty());
    }

    /** Short Persian label for the range, e.g. «۳۰ روز گذشته» or «۱۴۰۵/۰۶/۰۱ تا ۱۴۰۵/۰۷/۱۴». */
    public String rangeFa() {
        switch (preset) {
            case P_TODAY: return "امروز";
            case P_YESTERDAY: return "دیروز";
            case P_LAST7: return "۷ روز گذشته";
            case P_LAST30: return "۳۰ روز گذشته";
            case P_MONTH: return "از اول " + Jalali.monthName(Jalali.todayStr());
            case P_YEAR: return "سال مالی جاری";
            case P_ALL: return "همه تاریخ‌ها";
            default:
                if (!hasRange()) return "همه تاریخ‌ها";
                boolean f = from != null && !from.trim().isEmpty();
                boolean t = to != null && !to.trim().isEmpty();
                if (f && !t) return "از " + Money.fa(from);
                if (t && !f) return "تا " + Money.fa(to);
                return Money.fa(from) + " تا " + Money.fa(to);
        }
    }

    /** One-line summary for the in-content filter bar, e.g. «۳۰ روز گذشته • «احمد» • ۲ فیلتر». */
    public String describeFa() {
        StringBuilder b = new StringBuilder(rangeFa());
        if (search != null && !search.trim().isEmpty()) b.append(" • «").append(search.trim()).append("»");
        int n = 0;
        if (visitor >= 0) n++;
        if (route >= 0) n++;
        if (custGroup >= 0) n++;
        if (kalaGroup >= 0) n++;
        if (warehouse >= 0) n++;
        if (bank >= 0) n++;
        if (user >= 0) n++;
        if (status != null && !status.isEmpty()) n++;
        if (sort != null && !sort.isEmpty()) n++;
        if (n > 0) b.append(" • ").append(Money.fa(String.valueOf(n))).append(" فیلتر");
        return b.toString();
    }

    public static String presetName(int p) {
        switch (p) {
            case P_TODAY: return "امروز";
            case P_YESTERDAY: return "دیروز";
            case P_LAST7: return "۷ روز گذشته";
            case P_LAST30: return "۳۰ روز گذشته";
            case P_MONTH: return "ماه جاری";
            case P_YEAR: return "سال مالی جاری";
            case P_ALL: return "همه";
            default: return "دلخواه";
        }
    }
}
