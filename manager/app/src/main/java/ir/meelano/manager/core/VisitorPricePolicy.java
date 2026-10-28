package ir.meelano.manager.core;

import java.util.List;

/** Atiran visitor pricing: DeviceSettings.DefaultPriceGrp 1..5 selects forosh1..forosh5. */
public final class VisitorPricePolicy {
    private VisitorPricePolicy() { }

    public static String column(int defaultPriceGroup) {
        return defaultPriceGroup >= 1 && defaultPriceGroup <= 5
                ? "forosh" + defaultPriceGroup : null;
    }

    /** A price is safe only when exactly one active price row resolves for this product. */
    public static boolean isUnambiguous(int group, long activeRows, double price) {
        return column(group) != null && activeRows == 1 && Double.isFinite(price) && price > 0;
    }

    /**
     * The one price group Atiran stores for a visitor. Atiran may keep several
     * DeviceSettings rows per visitor (one per server or sales line); they must all carry
     * the same group. A row without a usable group, or two different groups, is never
     * guessed between: the result is 0 and the caller refuses to price anything.
     */
    public static int resolveUniqueGroup(List<Integer> groups) {
        if (groups == null || groups.isEmpty()) return 0;
        Integer first = null;
        for (Integer g : groups) {
            if (g == null) return 0;
            if (first == null) first = g;
            else if (!first.equals(g)) return 0;
        }
        return column(first) == null ? 0 : first;
    }
}
