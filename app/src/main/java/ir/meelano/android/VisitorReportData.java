package ir.meelano.android;

import org.json.JSONObject;

/** Selects already-computed report values without inventing totals or merging data sources. */
final class VisitorReportData {
    private VisitorReportData() { }

    static Long count(JSONObject row, String key) {
        if (row == null || !row.has(key) || row.isNull(key)) return null;
        Object value = row.opt(key);
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    static Double amount(JSONObject row, String key) {
        if (row == null || !row.has(key) || row.isNull(key)) return null;
        Object value = row.opt(key);
        if (!(value instanceof Number)) return null;
        double result = ((Number) value).doubleValue();
        return Double.isNaN(result) || Double.isInfinite(result) ? null : result;
    }
}
