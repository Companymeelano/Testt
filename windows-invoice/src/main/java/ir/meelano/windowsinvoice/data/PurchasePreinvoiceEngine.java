package ir.meelano.windowsinvoice.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * موتور خالص «پیش‌فاکتور خرید» — بدون Android، بدون شبکه.
 *
 * <p>یک سند خرید برای کالاهایی که هنوز فاکتور خریدشان در سامانه ثبت نشده: قیمت هر قلم
 * محاسبه می‌شود، اختلاف قیمت با قیمت مرجع (آخرین خرید/قیمت سامانه) لحظه‌ای دیده می‌شود،
 * شش نوع تخفیف (از جمله دو تخفیف پله‌ای) به ترتیب حسابداری اعمال می‌شوند و مالیات و عوارض
 * بر مبنای خالصِ پس از تخفیف محاسبه می‌گردد. خروجی همان اعدادی است که نسخهٔ یکپارچهٔ اندروید
 * و کلاینت ویندوز نشان می‌دهند، چون هر سه همین قرارداد محاسباتی را پیاده می‌کنند.</p>
 *
 * <p>قلم هیچ‌جا به دیتابیس میدان نوشته نمی‌شود؛ پیش‌فاکتور یک سند محلیِ برنامه است
 * ({@link PurchasePreinvoiceStoreWin}) و فقط از طریق کاربر به اشتراک گذاشته می‌شود.</p>
 */
public final class PurchasePreinvoiceEngine {

    private PurchasePreinvoiceEngine() { }

    // --------------------------------------------------------------- انواع تخفیف
    /** شش نوع تخفیف رایج نرم‌افزارهای حسابداری؛ دو مورد پله‌ای هستند. */
    public enum DiscType {
        /** درصد روی جمع ردیف (تخفیف نقدی ردیف). */
        ROW_PERCENT("درصد ردیف"),
        /** مبلغ ریالی ثابت به ازای هر واحد. */
        ROW_PER_UNIT("ریالی هر واحد"),
        /** پله‌ای تعداد: «10:5,50:10» یعنی ≥۱۰ → ۵٪ و ≥۵۰ → ۱۰٪. */
        TIERED_QTY("پله‌ای تعداد"),
        /** پله‌ای مبلغ ردیف: پله‌ها بر جمع ناخالص ردیف. */
        TIERED_AMOUNT("پله‌ای مبلغ"),
        /** درصد روی کل فاکتور، به نسبت خالص هر ردیف تقسیم می‌شود. */
        INVOICE_PERCENT("درصد کل فاکتور"),
        /** کد تخفیف فصلی/تبلیغاتی با سقف ریالی روی ردیف. */
        PROMO_PERCENT("کد تخفیف");

        public final String fa;
        DiscType(String fa) { this.fa = fa; }
    }

    /** یک قانون تخفیف روی ردیف (یا کل سند برای INVOICE_PERCENT). */
    public static final class Disc {
        public DiscType type;
        public double v1;      // درصد یا مبلغ هر واحد
        public double v2;      // سقف ریالی (PROMO) ؛ ۰ یعنی بدون سقف
        public String spec;    // پله‌ها «10:5,50:10» یا نام کد تخفیف

        public Disc(DiscType type, double v1, double v2, String spec) {
            this.type = type; this.v1 = v1; this.v2 = v2; this.spec = spec == null ? "" : spec;
        }

        String pack() {
            return type.name() + ":" + num(v1) + ":" + num(v2) + ":" + clean(spec).replace(',', '~');
        }

        static Disc unpack(String s) {
            String[] p = s.split(":", 4);
            if (p.length < 3) return null;
            try {
                return new Disc(DiscType.valueOf(p[0]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]), p.length > 3 ? p[3].replace('~', ',') : "");
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    /** یک قلم پیش‌فاکتور با همهٔ اجزای حسابداری. */
    public static final class Line {
        public String code = "";      // کد کالا
        public String name = "";      // نام کالا
        public String unit = "عدد";   // واحد شمارش
        public double qty;
        public double price;          // قیمت واحد خرید (ریال)
        public double refPrice;       // قیمت مرجع برای محاسبهٔ اختلاف (ریال)
        public double taxPct = 10;    // مالیات بر ارزش افزوده
        public double dutyPct;        // عوارض
        public final List<Disc> discs = new ArrayList<>();
        // محاسبات (پس از compute):
        public double gross, rowDisc, invDisc, net, taxAmt, dutyAmt, total, diffAmt, diffPct;

        public Line() { }

        public Line(String code, String name, String unit, double qty, double price, double refPrice) {
            this.code = code; this.name = name; this.unit = unit;
            this.qty = qty; this.price = price; this.refPrice = refPrice;
        }

        String pack() {
            StringBuilder d = new StringBuilder();
            for (Disc x : discs) { if (d.length() > 0) d.append(','); d.append(x.pack()); }
            return clean(code) + "|" + clean(name) + "|" + clean(unit) + "|" + num(qty) + "|"
                    + num(price) + "|" + num(refPrice) + "|" + num(taxPct) + "|" + num(dutyPct) + "|" + d;
        }

        static Line unpack(String s) {
            String[] p = s.split("\\|", -1);
            if (p.length < 9) return null;
            Line l = new Line();
            l.code = p[0]; l.name = p[1]; l.unit = p[2].isEmpty() ? "عدد" : p[2];
            l.qty = dbl(p[3]); l.price = dbl(p[4]); l.refPrice = dbl(p[5]);
            l.taxPct = dbl(p[6]); l.dutyPct = dbl(p[7]);
            for (String d : p[8].split(",")) {
                if (d.isEmpty()) continue;
                Disc x = Disc.unpack(d);
                if (x != null) l.discs.add(x);
            }
            return l;
        }
    }

    /** حالت مالیات: افزوده‌شده روی خالص، یا قیمت واحد شامل مالیات. */
    public static final String TAX_ADD = "add";
    public static final String TAX_INCLUSIVE = "incl";
    /** گردکردن‌ها: بدون گرد / ریال / تومان / صد ریال / هزار ریال. */
    public static final String[] ROUNDINGS = {"none", "rial", "toman", "hundred", "thousand"};

    /** سند پیش‌فاکتور: سربرگ فروشنده/خریدار + اقلام + تنظیمات محاسبه. */
    public static final class Draft {
        public String id = "";
        public String docNo = "";
        public long dateTs = System.currentTimeMillis();
        public int dueDays;
        public String taxMode = TAX_ADD;
        public String rounding = "none";
        public double cashDiscPct;                 // تخفیف نقدی پس از جمع کل
        public String supplierName = "", supplierCode = "", supplierNatId = "";
        public String supplierPhone = "", supplierAddress = "";
        public String buyerName = "", buyerCode = "";
        public String note = "";
        public String status = "draft";            // draft | sent | approved
        public long savedAt;
        public String savedBy = "";
        public final List<Line> lines = new ArrayList<>();
    }

    /** جمع‌های سند پس از محاسبه. */
    public static final class Totals {
        public double goods, rowDisc, invDisc, discSum, discPct, tax, duty, grand, cashDisc, payable, diff;
    }

    // --------------------------------------------------------------- محاسبه
    public static double round(double v, String mode) {
        if ("toman".equals(mode)) return Math.round(v / 10d) * 10d;
        if ("hundred".equals(mode)) return Math.round(v / 100d) * 100d;
        if ("thousand".equals(mode)) return Math.round(v / 1000d) * 1000d;
        if ("rial".equals(mode)) return Math.rint(v);
        return v;
    }

    /** درصد پلهٔ برنده: بزرگ‌ترین آستانهٔ ≤ مقدار. */
    static double tierPercent(String spec, double value) {
        double best = 0; double bestAt = -1;
        for (String step : spec.split(",")) {
            String[] kv = step.split(":");
            if (kv.length < 2) continue;
            double at = dbl(kv[0]), pct = dbl(kv[1]);
            if (value >= at && at >= bestAt) { bestAt = at; best = pct; }
        }
        return best;
    }

    /** تخفیف ردیف (بدون درصد کل فاکتور) به ترتیب ثابت حسابداری. */
    /** ترتیب ثابت حسابداری بدون توجه به ترتیب ورود کاربر. */
    static int rank(Disc d) {
        switch (d.type) {
            case ROW_PER_UNIT: return 0;
            case TIERED_QTY: return 1;
            case TIERED_AMOUNT: return 2;
            case ROW_PERCENT: return 3;
            default: return 4;
        }
    }

    public static double rowDiscount(Line l) {
        double gross = l.qty * l.price;
        double disc = 0;
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

    /** درصد «درصد کل فاکتور» سند (جمع انواع INVOICE_PERCENT). */
    public static double invoiceDiscPct(Draft d) {
        double pct = 0;
        for (Line l : d.lines) for (Disc x : l.discs)
            if (x.type == DiscType.INVOICE_PERCENT) pct += x.v1;
        return pct;
    }

    /** محاسبهٔ یک ردیف با توجه به حالت مالیات و گردکردن؛ مقادیر مشتق روی ردیف نوشته می‌شوند. */
    public static void computeLine(Line l, String taxMode, String rounding) {
        l.gross = round(l.qty * l.price, rounding);
        l.rowDisc = round(rowDiscount(l), rounding);
        double afterRow = l.gross - l.rowDisc;
        // سهم تخفیف کل فاکتور اینجا لازم نیست؛ در totals با درصد سند محاسبه می‌شود.
        l.net = round(afterRow, rounding);
        double t = Math.max(0, l.taxPct) / 100d, du = Math.max(0, l.dutyPct) / 100d;
        if (TAX_INCLUSIVE.equals(taxMode)) {
            double base = l.net / (1d + t);
            l.taxAmt = round(l.net - base, rounding);
            l.dutyAmt = round(base * du, rounding);
            l.total = round(l.net + l.dutyAmt, rounding);
        } else {
            l.taxAmt = round(l.net * t, rounding);
            l.dutyAmt = round(l.net * du, rounding);
            l.total = round(l.net + l.taxAmt + l.dutyAmt, rounding);
        }
        double ref = l.refPrice * l.qty;
        l.diffAmt = round(l.gross - ref, rounding);
        l.diffPct = ref > 0 ? (l.gross - ref) / ref * 100d : 0;
    }

    /** محاسبهٔ کل سند: همهٔ ردیف‌ها + جمع‌ها + تخفیف نقدی پایانی. */
    public static Totals compute(Draft d) {
        Totals t = new Totals();
        double ip = invoiceDiscPct(d);
        for (Line l : d.lines) {
            computeLine(l, d.taxMode, d.rounding);
            l.invDisc = round(l.net * ip / 100d, d.rounding);
            t.goods += l.gross;
            t.rowDisc += l.rowDisc;
            t.invDisc += l.invDisc;
            t.tax += l.taxAmt;
            t.duty += l.dutyAmt;
            t.diff += l.diffAmt;
        }
        // مالیات/عوارض/جمع کل بر مبنای خالصِ پس از تخفیف کل فاکتور بازمحاسبه می‌شود.
        double taxSum = 0, dutySum = 0, grand = 0;
        for (Line l : d.lines) {
            double netAfterInv = l.net - l.invDisc;
            double tPct = Math.max(0, l.taxPct) / 100d, dPct = Math.max(0, l.dutyPct) / 100d;
            double tax, duty, total;
            if (TAX_INCLUSIVE.equals(d.taxMode)) {
                double base = l.net / (1d + tPct);           // مالیات از داخل قیمت بیرون کشیده شد
                double baseAfter = base * (netAfterInv / (l.net == 0 ? 1 : l.net));
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

    // --------------------------------------------------------------- بسته‌بندی متنی (بدون JSON)
    static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    static String num(double v) { return String.format(Locale.US, "%.2f", v); }

    static double dbl(String s) {
        try { return Double.parseDouble(s); } catch (RuntimeException e) { return 0; }
    }

    public static String pack(Draft d) {
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

    public static Draft unpack(String s) {
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
