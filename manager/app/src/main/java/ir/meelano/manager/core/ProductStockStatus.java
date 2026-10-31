package ir.meelano.manager.core;

/** Pure stock-band classification used by the product list and covered by unit tests. */
public final class ProductStockStatus {
    private static final double EPSILON = 0.000001d;

    private ProductStockStatus() { }

    public enum Band {
        UNKNOWN,
        NEGATIVE,
        OUT,
        REORDER,
        LOW,
        NORMAL
    }

    /**
     * Classify stock using Atiran's piece-based reorder point and the existing five-major-unit
     * low-stock threshold. The caller passes a warehouse quantity as both quantity measures only
     * when no comparable per-warehouse reorder point is available.
     */
    public static Band classify(boolean known, double majorUnits, double pieces,
                                double reorderPointPieces, double lowThresholdMajorUnits) {
        if (!known || !finite(majorUnits) || !finite(pieces)) return Band.UNKNOWN;
        if (pieces < -EPSILON) return Band.NEGATIVE;
        if (pieces <= EPSILON) return Band.OUT;
        if (finite(reorderPointPieces) && reorderPointPieces > EPSILON
                && pieces <= reorderPointPieces + EPSILON) return Band.REORDER;
        if (majorUnits <= lowThresholdMajorUnits + EPSILON) return Band.LOW;
        return Band.NORMAL;
    }

    /** Mirrors the active flag values used by MasterQueries.boolTrue. */
    public static boolean isActive(String value) {
        if (value == null) return false;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return "T".equals(normalized) || "TRUE".equals(normalized) || "Y".equals(normalized)
                || "YES".equals(normalized) || "1".equals(normalized);
    }

    public static String label(Band band) {
        if (band == null) return "نامشخص";
        switch (band) {
            case NEGATIVE: return "کسری موجودی";
            case OUT: return "ناموجود";
            case REORDER: return "نقطه سفارش";
            case LOW: return "موجودی کم";
            case NORMAL: return "موجود";
            case UNKNOWN:
            default: return "نامشخص";
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
