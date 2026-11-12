package ir.meelano.windowsinvoice.data;

/** همان منطق PersianNumberِ اندروید برای ویندوز. */
public final class PersianNumber {
    private PersianNumber() {}
    public static final class Result {
        public final String raw; public final Double value; public final String error;
        Result(String raw, Double value, String error) { this.raw = raw; this.value = value; this.error = error; }
        public boolean isEmpty() { return raw == null || raw.trim().isEmpty(); }
        public boolean isValid() { return error == null && value != null; }
    }
    static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch == '٫') b.append('.');
            else if (ch == '٬' || ch == '،') b.append(',');
            else b.append(ch);
        }
        return b.toString();
    }
    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Result(raw, null, null);
        String n = normalize(raw).trim().replace(",", "").replace("\u00A0", "").replace("\u202F", "").replace(" ", "");
        if (n.isEmpty()) return new Result(raw, null, null);
        if (n.equals(".") || n.equals("-") || n.equals("-.")) return new Result(raw, null, null);
        if (n.endsWith(".")) {
            String withoutDot = n.substring(0, n.length() - 1);
            try { return new Result(raw, Double.parseDouble(withoutDot), null); } catch (Exception e) {}
        }
        if (n.startsWith(".")) n = "0" + n;
        if (n.startsWith("-.")) n = "-0." + n.substring(2);
        try {
            double v = Double.parseDouble(n);
            if (Double.isNaN(v) || Double.isInfinite(v)) return new Result(raw, null, "عدد خارج از محدوده است");
            return new Result(raw, v, null);
        } catch (NumberFormatException e) { return new Result(raw, null, "عدد نامعتبر است"); }
    }
    public static double valueOrZero(String raw) { Result r = parse(raw); return r.value != null ? r.value : 0; }
}
