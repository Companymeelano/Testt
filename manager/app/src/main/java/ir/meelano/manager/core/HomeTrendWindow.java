package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Available, non-destructive zoom windows for the Home daily trend charts. */
public final class HomeTrendWindow {
    /** Sentinel for every day in the loaded source window. */
    public static final int ALL = -1;

    private HomeTrendWindow() { }

    /** Offer common focused windows and always retain an explicit way back to the full series. */
    public static List<Integer> options(int availableDays) {
        int count = Math.max(0, availableDays);
        List<Integer> out = new ArrayList<>();
        if (count > 7) out.add(7);
        if (count > 14) out.add(14);
        if (count > 30) out.add(30);
        if (count > 0) out.add(ALL);
        return Collections.unmodifiableList(out);
    }

    /** Prefer a useful recent fortnight while keeping the entire source available on demand. */
    public static int preferred(int availableDays) {
        int count = Math.max(0, availableDays);
        return count > 14 ? 14 : ALL;
    }

    public static String label(int days) {
        return days == ALL ? "همه" : HomeNumberFormatter.integer(Math.max(0, days)) + " روز";
    }

    /** Number of points to display without changing or discarding the original series. */
    public static int visibleDays(int requestedDays, int availableDays) {
        int count = Math.max(0, availableDays);
        return requestedDays == ALL ? count : Math.min(count, Math.max(0, requestedDays));
    }
}
