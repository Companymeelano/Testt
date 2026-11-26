package ir.meelano.manager.data;

import ir.meelano.manager.core.Diag;
import java.math.BigDecimal;
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

    public long l(String key) { return Math.round(d(key)); }

    public int i(String key) { return (int) Math.round(d(key)); }

    /**
     * Exact decimal getter for financial values: preserves full precision (no double rounding)
     * and fails closed — a missing or malformed value returns null instead of a fabricated zero.
     */
    public BigDecimal bd(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(numericText(v)); }
        catch (Exception ignored) { return null; }
    }

    /**
     * Exact integer getter: returns the value only when it is a whole number that fits a long;
     * a fractional, missing, malformed or overflowing value returns null (never rounded).
     */
    public Long exactLong(String key) {
        BigDecimal b = bd(key);
        if (b == null) return null;
        try { return b.longValueExact(); }
        catch (ArithmeticException ignored) { return null; }
    }

    /** Normalizes Persian/Arabic digits and separators so strict decimal parsing can read them. */
    private static String numericText(Object v) {
        String t = String.valueOf(v).trim()
                .replace("٬", "").replace(",", "").replace("ریال", "").replace("تومان", "").trim();
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch == '٫') b.append('.');
            else b.append(ch);
        }
        return b.toString().trim();
    }

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
