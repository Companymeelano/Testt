package ir.meelano.manager.core;

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
}
