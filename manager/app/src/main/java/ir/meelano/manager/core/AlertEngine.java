package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure Home alert rules. Inputs are supplied by read-only queries; this class never performs I/O. */
public final class AlertEngine {
    public static final double PURCHASE_PRICE_DRIFT_PERCENT = 15.0;
    public static final int DEFAULT_SALES_DROP_PERCENT = 20;
    public static final int DUE_WINDOW_DAYS = 3;

    private AlertEngine() { }

    public enum Severity { CRITICAL, WARNING, INFO }
    public enum Action { PRODUCT_FILE, SALES, PURCHASES, CHEQUES }

    public static final class StockItem {
        public final String code;
        public final String name;
        public final double stockPieces;
        public final double reorderPoint;
        public final boolean reorderPointKnown;

        public StockItem(String code, String name, double stockPieces,
                         double reorderPoint, boolean reorderPointKnown) {
            this.code = safe(code);
            this.name = safe(name);
            this.stockPieces = stockPieces;
            this.reorderPoint = reorderPoint;
            this.reorderPointKnown = reorderPointKnown;
        }
    }

    /** Prices are unit prices from the two latest purchase records for one product. */
    public static final class PriceChange {
        public final String productName;
        public final double previousUnitPrice;
        public final double lastUnitPrice;

        public PriceChange(String productName, double previousUnitPrice, double lastUnitPrice) {
            this.productName = safe(productName);
            this.previousUnitPrice = previousUnitPrice;
            this.lastUnitPrice = lastUnitPrice;
        }
    }

    /** A due record already returned by the bounded, outstanding-cheque query. */
    public static final class DueItem {
        public final boolean incoming;
        public final int daysUntilDue;
        public final double amount;

        public DueItem(boolean incoming, int daysUntilDue, double amount) {
            this.incoming = incoming;
            this.daysUntilDue = daysUntilDue;
            this.amount = amount;
        }
    }

    /** Structured alert data; the UI owns presentation and navigation. */
    public static final class Alert {
        public final String code;
        public final Severity severity;
        public final String subject;
        public final String actionValue;
        public final Action action;
        /** Current value: stock, projected sales, latest unit price, or due amount. */
        public final double value;
        /** Comparison value: reorder point, prior baseline, or previous unit price. */
        public final double referenceValue;
        public final double changePercent;
        public final int count;
        /** Nearest due day for cheque alerts; otherwise -1. */
        public final int daysUntilDue;

        private Alert(String code, Severity severity, String subject, String actionValue, Action action,
                      double value, double referenceValue, double changePercent, int count, int daysUntilDue) {
            this.code = code;
            this.severity = severity;
            this.subject = subject;
            this.actionValue = actionValue;
            this.action = action;
            this.value = value;
            this.referenceValue = referenceValue;
            this.changePercent = changePercent;
            this.count = count;
            this.daysUntilDue = daysUntilDue;
        }
    }

    /**
     * Evaluates available inputs only. Missing queries, missing reorder points, an incomplete
     * comparison baseline, or invalid prices never become zero-value warnings.
     */
    public static List<Alert> evaluate(List<StockItem> stockItems, List<PriceChange> priceChanges,
                                       SalesComparison.DayResult sales, int salesDropPercent,
                                       List<DueItem> dueItems) {
        List<Alert> alerts = new ArrayList<>();
        addStockAlerts(alerts, stockItems);
        addSalesAlert(alerts, sales, salesDropPercent);
        addPriceAlerts(alerts, priceChanges);
        addDueAlerts(alerts, dueItems);
        Collections.sort(alerts, Comparator.comparingInt(a -> a.severity.ordinal()));
        return Collections.unmodifiableList(alerts);
    }

    private static void addStockAlerts(List<Alert> alerts, List<StockItem> items) {
        if (items == null) return;
        for (StockItem item : items) {
            if (item == null || !finite(item.stockPieces)) continue;
            if (item.stockPieces <= 0) {
                alerts.add(new Alert("stock_out", Severity.CRITICAL, item.name, item.code,
                        Action.PRODUCT_FILE, item.stockPieces, item.reorderPoint,
                        Double.NaN, 1, -1));
            } else if (item.reorderPointKnown && finite(item.reorderPoint)
                    && item.reorderPoint > 0 && item.stockPieces <= item.reorderPoint) {
                alerts.add(new Alert("stock_low", Severity.WARNING, item.name, item.code,
                        Action.PRODUCT_FILE, item.stockPieces, item.reorderPoint,
                        Double.NaN, 1, -1));
            }
        }
    }

    private static void addSalesAlert(List<Alert> alerts, SalesComparison.DayResult sales,
                                      int dropThresholdPercent) {
        int threshold = Math.max(0, Math.min(95, dropThresholdPercent));
        if (threshold == 0 || sales == null || !sales.available || !sales.projectionAvailable
                || !sales.hasBaseline()) return;
        double change = sales.changePercent();
        if (!finite(change) || change > -threshold) return;
        alerts.add(new Alert("sales_drop", Severity.WARNING, "", "", Action.SALES,
                sales.projectedRials, sales.sameWeekdayAverageRials, change, 1, -1));
    }

    private static void addPriceAlerts(List<Alert> alerts, List<PriceChange> changes) {
        if (changes == null) return;
        for (PriceChange item : changes) {
            if (item == null || !finite(item.previousUnitPrice) || !finite(item.lastUnitPrice)
                    || item.previousUnitPrice <= 0 || item.lastUnitPrice <= 0) continue;
            double change = (item.lastUnitPrice - item.previousUnitPrice) * 100.0 / item.previousUnitPrice;
            if (!finite(change) || Math.abs(change) < PURCHASE_PRICE_DRIFT_PERCENT) continue;
            alerts.add(new Alert("purchase_price_drift", Severity.WARNING, item.productName, "",
                    Action.PURCHASES, item.lastUnitPrice, item.previousUnitPrice, change, 1, -1));
        }
    }

    private static void addDueAlerts(List<Alert> alerts, List<DueItem> items) {
        if (items == null) return;
        int incomingCount = 0;
        int outgoingCount = 0;
        int incomingNearest = Integer.MAX_VALUE;
        int outgoingNearest = Integer.MAX_VALUE;
        double incomingAmount = 0;
        double outgoingAmount = 0;
        for (DueItem item : items) {
            if (item == null || item.daysUntilDue < 0 || item.daysUntilDue > DUE_WINDOW_DAYS) continue;
            if (item.incoming) {
                incomingCount++;
                incomingNearest = Math.min(incomingNearest, item.daysUntilDue);
                if (finite(item.amount) && item.amount > 0) incomingAmount += item.amount;
            } else {
                outgoingCount++;
                outgoingNearest = Math.min(outgoingNearest, item.daysUntilDue);
                if (finite(item.amount) && item.amount > 0) outgoingAmount += item.amount;
            }
        }
        if (outgoingCount > 0) {
            alerts.add(new Alert("cheque_out_due", Severity.WARNING, "", "", Action.CHEQUES,
                    outgoingAmount, 0, Double.NaN, outgoingCount, outgoingNearest));
        }
        if (incomingCount > 0) {
            alerts.add(new Alert("cheque_in_due", Severity.INFO, "", "", Action.CHEQUES,
                    incomingAmount, 0, Double.NaN, incomingCount, incomingNearest));
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
