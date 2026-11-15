package ir.meelano.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * موتور خالص «پیش‌فاکتور خرید» نسخهٔ یکپارچه — همان قرارداد محاسباتی کلاینت مدیر و ویندوز:
 * شش نوع تخفیف (دو پله‌ای)، مالیات افزوده‌شده/شامل، عوارض، گردکردن، اختلاف لحظه‌ای با قیمت
 * مرجع و بسته‌بندی متنی بدون JSON برای دفترچهٔ محلی.
 */
final class MeelanoPreinvoice {

    private MeelanoPreinvoice() { }

    enum DiscType {
        ROW_PERCENT("درصد ردیف"), ROW_PER_UNIT("ریالی هر واحد"), TIERED_QTY("پله‌ای تعداد"),
        TIERED_AMOUNT("پله‌ای مبلغ"), INVOICE_PERCENT("درصد کل فاکتور"), PROMO_PERCENT("کد تخفیف");
        final String fa;
        DiscType(String fa) { this.fa = fa; }
    }

    static final class Disc {
        DiscType type; double v1, v2; String spec = "";
        Disc(DiscType t, double v1, double v2, String spec) {
            this.type = t; this.v1 = v1; this.v2 = v2; this.spec = spec == null ? "" : spec;
        }
        String pack() { return type.name() + ":" + num(v1) + ":" + num(v2) + ":" + clean(spec).replace(',', '~'); }
        static Disc unpack(String s) {
            String[] p = s.split(":", 4);
            if (p.length < 3) return null;
            try { return new Disc(DiscType.valueOf(p[0]), dbl(p[1]), dbl(p[2]), p.length > 3 ? p[3].replace('~', ',') : ""); }
            catch (RuntimeException e) { return null; }
        }
    }

    static final class Line {
        String code = "", name = "", unit = "عدد";
        double qty, price, refPrice, taxPct = 10, dutyPct;
        final List<Disc> discs = new ArrayList<>();
        double gross, rowDisc, invDisc, net, taxAmt, dutyAmt, total, diffAmt, diffPct;

        Line() { }

        Line(String code, String name, String unit, double qty, double price, double refPrice) {
            this.code = code; this.name = name; this.unit = unit;
            this.qty = qty; this.price = price; this.refPrice = refPrice;
        }

        String pack() {
            StringBuilder d = new StringBuilder();
            for (Disc x : discs) { if (d.length() > 0) d.append(','); d.append(x.pack()); }
            return clean(code) + "|" + clean(name) + "|" + clean(unit) + "|" + num(qty) + "|" + num(price)
                    + "|" + num(refPrice) + "|" + num(taxPct) + "|" + num(dutyPct) + "|" + d;
        }
        static Line unpack(String s) {
            String[] p = s.split("\\|", -1);
            if (p.length < 9) return null;
            Line l = new Line();
            l.code = p[0]; l.name = p[1]; l.unit = p[2].isEmpty() ? "عدد" : p[2];
            l.qty = dbl(p[3]); l.price = dbl(p[4]); l.refPrice = dbl(p[5]);
            l.taxPct = dbl(p[6]); l.dutyPct = dbl(p[7]);
            for (String d : p[8].split(",")) if (!d.isEmpty()) {
                Disc x = Disc.unpack(d);
                if (x != null) l.discs.add(x);
            }
            return l;
        }
    }

    static final String TAX_ADD = "add";
    static final String TAX_INCLUSIVE = "incl";
    static final String[] ROUNDINGS = {"none", "rial", "toman", "hundred", "thousand"};

    static final class Draft {
        String id = "", docNo = "";
        long dateTs = System.currentTimeMillis();
        int dueDays;
        String taxMode = TAX_ADD, rounding = "none";
        double cashDiscPct;
        String supplierName = "", supplierCode = "", supplierNatId = "";
        String supplierPhone = "", supplierAddress = "";
        String buyerName = "", buyerCode = "";
        String note = "", status = "draft", savedBy = "";
        long savedAt;
        final List<Line> lines = new ArrayList<>();
    }

    static final class Totals {
        double goods, rowDisc, invDisc, discSum, discPct, tax, duty, grand, cashDisc, payable, diff;
    }

    static double round(double v, String mode) {
        if ("toman".equals(mode)) return Math.round(v / 10d) * 10d;
        if ("hundred".equals(mode)) return Math.round(v / 100d) * 100d;
        if ("thousand".equals(mode)) return Math.round(v / 1000d) * 1000d;
        if ("rial".equals(mode)) return Math.rint(v);
        return v;
    }

    static double tierPercent(String spec, double value) {
        double best = 0, bestAt = -1;
        for (String step : spec.split(",")) {
            String[] kv = step.split(":");
            if (kv.length < 2) continue;
            double at = dbl(kv[0]), pct = dbl(kv[1]);
            if (value >= at && at >= bestAt) { bestAt = at; best = pct; }
        }
        return best;
    }

    static int rank(Disc d) {
        switch (d.type) {
            case ROW_PER_UNIT: return 0;
            case TIERED_QTY: return 1;
            case TIERED_AMOUNT: return 2;
            case ROW_PERCENT: return 3;
            default: return 4;
        }
    }

    static double rowDiscount(Line l) {
        double gross = l.qty * l.price, disc = 0;
        List<Disc> order = new ArrayList<>(l.discs);
        order.sort((x, y) -> Integer.compare(rank(x), rank(y)));
        for (Disc d : order) {
            if (d.type == DiscType.INVOICE_PERCENT) continue;
            if (d.type == DiscType.ROW_PER_UNIT) disc += d.v1 * l.qty;
            else if (d.type == DiscType.TIERED_QTY) disc += (l.qty * l.price - disc) * tierPercent(d.spec, l.qty) / 100d;
            else if (d.type == DiscType.TIERED_AMOUNT) disc += (gross - disc) * tierPercent(d.spec, gross) / 100d;
            else if (d.type == DiscType.ROW_PERCENT) disc += (gross - disc) * d.v1 / 100d;
            else if (d.type == DiscType.PROMO_PERCENT) {
                double x = (gross - disc) * d.v1 / 100d;
                if (d.v2 > 0) x = Math.min(x, d.v2);
                disc += x;
            }
        }
        return Math.min(disc, gross);
    }

    static double invoiceDiscPct(Draft d) {
        double pct = 0;
        for (Line l : d.lines) for (Disc x : l.discs) if (x.type == DiscType.INVOICE_PERCENT) pct += x.v1;
        return pct;
    }

    static Totals compute(Draft d) {
        Totals t = new Totals();
        double ip = invoiceDiscPct(d);
        for (Line l : d.lines) {
            l.gross = round(l.qty * l.price, d.rounding);
            l.rowDisc = round(rowDiscount(l), d.rounding);
            l.net = round(l.gross - l.rowDisc, d.rounding);
            l.invDisc = round(l.net * ip / 100d, d.rounding);
            double ref = l.refPrice * l.qty;
            l.diffAmt = round(l.gross - ref, d.rounding);
            l.diffPct = ref > 0 ? (l.gross - ref) / ref * 100d : 0;
            t.goods += l.gross; t.rowDisc += l.rowDisc; t.invDisc += l.invDisc; t.diff += l.diffAmt;
        }
        double taxSum = 0, dutySum = 0, grand = 0;
        for (Line l : d.lines) {
            double netAfterInv = l.net - l.invDisc;
            double tPct = Math.max(0, l.taxPct) / 100d, dPct = Math.max(0, l.dutyPct) / 100d;
            double tax, duty, total;
            if (TAX_INCLUSIVE.equals(d.taxMode)) {
                double base = l.net / (1d + tPct);
                double baseAfter = l.net == 0 ? 0 : base * (netAfterInv / l.net);
                tax = netAfterInv - baseAfter;
                duty = baseAfter * dPct;
                total = netAfterInv + duty;
            } else {
                tax = netAfterInv * tPct;
                duty = netAfterInv * dPct;
                total = netAfterInv + tax + duty;
            }
            l.taxAmt = round(tax, d.rounding);
            l.dutyAmt = round(duty, d.rounding);
            l.total = round(total, d.rounding);
            taxSum += l.taxAmt; dutySum += l.dutyAmt; grand += l.total;
        }
        t.tax = round(taxSum, d.rounding);
        t.duty = round(dutySum, d.rounding);
        t.grand = round(grand, d.rounding);
        t.discSum = round(t.rowDisc + t.invDisc, d.rounding);
        t.discPct = t.goods > 0 ? t.discSum / t.goods * 100d : 0;
        t.cashDisc = round(t.grand * Math.max(0, d.cashDiscPct) / 100d, d.rounding);
        t.payable = round(t.grand - t.cashDisc, d.rounding);
        t.diff = round(t.diff, d.rounding);
        return t;
    }

    static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    static String num(double v) { return String.format(Locale.US, "%.2f", v); }

    static double dbl(String s) {
        try { return Double.parseDouble(s); } catch (RuntimeException e) { return 0; }
    }

    static String pack(Draft d) {
        StringBuilder sb = new StringBuilder("v1");
        sb.append('|').append(clean(d.id)).append('|').append(clean(d.docNo)).append('|').append(d.dateTs)
          .append('|').append(d.dueDays).append('|').append(clean(d.taxMode)).append('|').append(clean(d.rounding))
          .append('|').append(num(d.cashDiscPct))
          .append('|').append(clean(d.supplierName)).append('|').append(clean(d.supplierCode))
          .append('|').append(clean(d.supplierNatId)).append('|').append(clean(d.supplierPhone))
          .append('|').append(clean(d.supplierAddress))
          .append('|').append(clean(d.buyerName)).append('|').append(clean(d.buyerCode))
          .append('|').append(clean(d.note)).append('|').append(clean(d.status))
          .append('|').append(d.savedAt).append('|').append(clean(d.savedBy));
        for (Line l : d.lines) sb.append(';').append(l.pack());
        return sb.toString();
    }

    static Draft unpack(String s) {
        if (s == null || !s.startsWith("v1|")) return null;
        int linesAt = s.indexOf(';');
        String head = linesAt < 0 ? s : s.substring(0, linesAt);
        String[] p = head.split("\\|", -1);
        if (p.length < 19) return null;
        Draft d = new Draft();
        d.id = p[1]; d.docNo = p[2];
        try { d.dateTs = Long.parseLong(p[3]); } catch (RuntimeException e) { }
        try { d.dueDays = Integer.parseInt(p[4]); } catch (RuntimeException e) { }
        d.taxMode = p[5]; d.rounding = p[6]; d.cashDiscPct = dbl(p[7]);
        d.supplierName = p[8]; d.supplierCode = p[9]; d.supplierNatId = p[10];
        d.supplierPhone = p[11]; d.supplierAddress = p[12];
        d.buyerName = p[13]; d.buyerCode = p[14];
        d.note = p[15]; d.status = p[16];
        try { d.savedAt = Long.parseLong(p[17]); } catch (RuntimeException e) { }
        d.savedBy = p[18];
        if (linesAt >= 0) for (String ls : s.substring(linesAt + 1).split(";")) {
            if (ls.isEmpty()) continue;
            Line l = Line.unpack(ls);
            if (l != null) d.lines.add(l);
        }
        return d;
    }
}
