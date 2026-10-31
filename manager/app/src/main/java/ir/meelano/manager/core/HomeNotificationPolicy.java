package ir.meelano.manager.core;

/** Pure local-notification rules shared by scheduling and tests. */
public final class HomeNotificationPolicy {
    private HomeNotificationPolicy() { }

    /** Quiet interval is half-open; equal endpoints mean disabled, and overnight ranges wrap. */
    public static boolean isQuietTime(boolean enabled, int startMinute, int endMinute,
                                      int currentMinute) {
        if (!enabled) return false;
        int start = clampMinute(startMinute);
        int end = clampMinute(endMinute);
        int now = clampMinute(currentMinute);
        if (start == end) return false;
        return start < end ? now >= start && now < end : now >= start || now < end;
    }

    /** MainActivity routes these known internal destinations after a notification tap. */
    public static String screenFor(AlertEngine.Action action) {
        if (action == null) return "";
        switch (action) {
            case PRODUCT_FILE: return "products";
            case SALES: return "sales";
            case PURCHASES: return "buy";
            case CHEQUES: return "cheques";
            default: return "";
        }
    }

    private static int clampMinute(int minute) {
        return Math.max(0, Math.min(23 * 60 + 59, minute));
    }
}
