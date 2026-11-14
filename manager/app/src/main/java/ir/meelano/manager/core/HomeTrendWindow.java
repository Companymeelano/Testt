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

    /** Evenly spaced date-label indexes, capped by readable pixel spacing and five labels. */
    public static List<Integer> dateLabelIndexes(int pointCount, double availableWidth,
                                                 double minimumGap) {
        int count = Math.max(0, pointCount);
        if (count == 0) return Collections.emptyList();
        if (count == 1) return Collections.singletonList(0);
        int maxLabels = 5;
        if (finite(availableWidth) && finite(minimumGap) && availableWidth >= 0d
                && minimumGap > 0d) {
            int byWidth = (int) Math.floor(availableWidth / minimumGap) + 1;
            maxLabels = Math.min(maxLabels, Math.max(2, byWidth));
        }
        int interval = Math.max(1, (int) Math.ceil((count - 1d) / (maxLabels - 1d)));
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < count; index += interval) indexes.add(index);
        int last = count - 1;
        int tail = indexes.get(indexes.size() - 1);
        if (tail != last) {
            // A final label squeezed beside the previous one renders as one unreadable blob
            // (two dates touching at the chart edge). When the tail gap is shorter than a full
            // interval, move the last label to the end instead of crowding the pair together.
            if (indexes.size() >= 2 && last - tail < interval) {
                indexes.set(indexes.size() - 1, last);
            } else {
                indexes.add(last);
            }
        }
        return Collections.unmodifiableList(indexes);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
