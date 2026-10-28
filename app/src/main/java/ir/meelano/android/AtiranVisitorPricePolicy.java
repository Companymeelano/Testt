package ir.meelano.android;

/** Confirmed Atiran rule for visitor defaults: DefaultPriceGrp 1..5 selects forosh1..forosh5. */
final class AtiranVisitorPricePolicy {
    private AtiranVisitorPricePolicy() { }

    static String priceColumn(int defaultPriceGroup) {
        return defaultPriceGroup >= 1 && defaultPriceGroup <= 5
                ? "forosh" + defaultPriceGroup
                : null;
    }

    /** forosh_price is keyed by VarietyID; without exactly one active row the product rate is ambiguous. */
    static boolean hasUnambiguousPrice(int defaultPriceGroup, long activePriceRows, double price) {
        return priceColumn(defaultPriceGroup) != null && activePriceRows == 1 && price > 0;
    }
}
