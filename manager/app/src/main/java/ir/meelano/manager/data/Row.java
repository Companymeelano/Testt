package ir.meelano.manager.data;

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

    public boolean has(String key) { return containsKey(key) && get(key) != null; }
}
