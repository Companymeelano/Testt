package ir.meelano.manager.data;

import ir.meelano.manager.core.Diag;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;

/** One database row: column name → value, with forgiving typed getters. */
public final class Row extends LinkedHashMap<String, Object> {
    public Row() { }

    public String s(String key) { return s(key, ""); }

    public String s(String key, String def) {
        Object v = get(key);
        if (v == null) return def;
        String t = String.valueOf(v).trim();
        return t.isEmpty() ? def : t;
    }

    public double d(String key) {
        Object v = get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v == null) return 0;
        String t = String.valueOf(v).trim()
                .replace("٬", "").replace(",", "").replace("ریال", "").replace("تومان", "").trim();
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch == '٫') b.append('.');
            else b.append(ch);
        }
        try { return Double.parseDouble(b.toString().trim()); }
        catch (Exception ignored) { return 0; }
    }

    /** Exact decimal getter for financial paths; null means missing or malformed, never zero. */
    public BigDecimal bd(String key) {
        Object value = get(key);
        if (value == null) return null;
        try {
            if (value instanceof BigDecimal) return (BigDecimal) value;
            if (value instanceof BigInteger) return new BigDecimal((BigInteger) value);
            if (value instanceof Double || value instanceof Float) {
                double number = ((Number) value).doubleValue();
                return Double.isFinite(number) ? BigDecimal.valueOf(number) : null;
            }
            if (value instanceof Number) return new BigDecimal(value.toString());
            String raw = String.valueOf(value).trim();
            if (raw.isEmpty()) return null;
            StringBuilder normalized = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char ch = raw.charAt(i);
                if (ch >= '۰' && ch <= '۹') normalized.append((char) ('0' + (ch - '۰')));
                else if (ch >= '٠' && ch <= '٩') normalized.append((char) ('0' + (ch - '٠')));
                else if (ch == '٫') normalized.append('.');
                else if (ch == '−') normalized.append('-');
                else normalized.append(ch);
            }
            return new BigDecimal(normalized.toString());
        } catch (Exception invalid) {
            return null;
        }
    }

    /** Exact integral getter; null means missing, fractional, out of range, or malformed. */
    public Long exactLong(String key) {
        BigDecimal value = bd(key);
        if (value == null) return null;
        try { return value.longValueExact(); }
        catch (Exception invalid) { return null; }
    }

    public long l(String key) { return Math.round(d(key)); }

    public int i(String key) { return (int) Math.round(d(key)); }

    public boolean has(String key) { return containsKey(key) && get(key) != null; }

    /** Raw bytes (varbinary/image columns) or null. */
    public byte[] bytes(String key) {
        try {
            Object o = get(key);
            if (o instanceof byte[]) return (byte[]) o;
        } catch (Exception ignored) { Diag.ignored("Row:46", ignored); }
        return null;
    }
}
