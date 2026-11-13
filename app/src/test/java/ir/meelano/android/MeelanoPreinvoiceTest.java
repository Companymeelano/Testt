package ir.meelano.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** محاسبهٔ پیش‌فاکتور خرید: پله‌ها، ترتیب تخفیف‌ها، مالیات دوحالتی، گردکردن و بسته‌بندی. */
public class MeelanoPreinvoiceTest {

    private static MeelanoPreinvoice.Draft draft(double qty, double price, double ref) {
        MeelanoPreinvoice.Draft d = new MeelanoPreinvoice.Draft();
        d.lines.add(new MeelanoPreinvoice.Line("1001", "کالای نمونه", "عدد", qty, price, ref));
        return d;
    }

    @Test
    public void tieredQuantityPicksWinningStep() {
        MeelanoPreinvoice.Draft d = draft(60, 100_000, 0);
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(
                MeelanoPreinvoice.DiscType.TIERED_QTY, 0, 0, "10:5,50:10"));
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(600_000, t.rowDisc, 0.51);   // ۱۰٪ برد برنده برای ۶۰ عدد
        assertEquals(5_400_000, t.grand - t.tax - t.duty, 1.01);
    }

    @Test
    public void discountOrderIsPerUnitThenPercents() {
        MeelanoPreinvoice.Draft d = draft(10, 100_000, 0);
        MeelanoPreinvoice.Line l = d.lines.get(0);
        l.discs.add(new MeelanoPreinvoice.Disc(MeelanoPreinvoice.DiscType.ROW_PERCENT, 10, 0, ""));
        l.discs.add(new MeelanoPreinvoice.Disc(MeelanoPreinvoice.DiscType.ROW_PER_UNIT, 20_000, 0, ""));
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        // 1,000,000 - 200,000 = 800,000 سپس ۱۰٪ ⇒ 720,000 خالص
        assertEquals(280_000, t.rowDisc, 0.51);
        assertEquals(720_000, l.net, 0.51);
    }

    @Test
    public void taxAddedOnNetPlusDuty() {
        MeelanoPreinvoice.Draft d = draft(1, 1_000_000, 0);
        d.lines.get(0).dutyPct = 2;
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(100_000, t.tax, 0.51);
        assertEquals(20_000, t.duty, 0.51);
        assertEquals(1_120_000, t.grand, 0.51);
    }

    @Test
    public void inclusiveTaxIsExtractedNotAdded() {
        MeelanoPreinvoice.Draft d = draft(1, 1_100_000, 0);
        d.taxMode = MeelanoPreinvoice.TAX_INCLUSIVE;
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(100_000, t.tax, 0.51);
        assertEquals(1_100_000, t.grand, 0.51);
    }

    @Test
    public void invoicePercentSpreadsProRata() {
        MeelanoPreinvoice.Draft d = draft(2, 500_000, 0);
        d.lines.get(0).taxPct = 0;
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(
                MeelanoPreinvoice.DiscType.INVOICE_PERCENT, 5, 0, ""));
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(50_000, t.invDisc, 0.51);
        assertEquals(950_000, t.grand, 0.51);
    }

    @Test
    public void roundingToThousand() {
        MeelanoPreinvoice.Draft d = draft(3, 333_333.7, 0);
        d.rounding = "thousand";
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(1_000_000, t.goods, 0.01);
    }

    @Test
    public void diffAgainstReferenceIsLive() {
        MeelanoPreinvoice.Draft d = draft(3, 120_000, 100_000);
        MeelanoPreinvoice.compute(d);
        assertEquals(60_000, d.lines.get(0).diffAmt, 0.51);
        assertEquals(20, d.lines.get(0).diffPct, 0.001);
    }

    @Test
    public void cashDiscountAppliesAfterGrand() {
        MeelanoPreinvoice.Draft d = draft(1, 1_000_000, 0);
        d.lines.get(0).taxPct = 0;
        d.cashDiscPct = 2.5;
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(25_000, t.cashDisc, 0.51);
        assertEquals(975_000, t.payable, 0.51);
    }

    @Test
    public void packUnpackRoundTripKeepsPersianAndDiscs() {
        MeelanoPreinvoice.Draft d = draft(4, 250_000, 240_000);
        d.supplierName = "تامین‌کنندهٔ نمونه | ؛";
        d.docNo = "PI-7";
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(
                MeelanoPreinvoice.DiscType.TIERED_AMOUNT, 0, 0, "500000:3,1000000:6"));
        String packed = MeelanoPreinvoice.pack(d);
        assertTrue(packed.indexOf('\n') < 0);
        MeelanoPreinvoice.Draft back = MeelanoPreinvoice.unpack(packed);
        assertEquals("PI-7", back.docNo);
        assertEquals(1, back.lines.size());
        assertEquals(1, back.lines.get(0).discs.size());
        assertEquals(MeelanoPreinvoice.DiscType.TIERED_AMOUNT, back.lines.get(0).discs.get(0).type);
        assertEquals("500000:3,1000000:6", back.lines.get(0).discs.get(0).spec);
    }

    @Test
    public void promoCapLimitsDiscount() {
        MeelanoPreinvoice.Draft d = draft(10, 1_000_000, 0);
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(
                MeelanoPreinvoice.DiscType.PROMO_PERCENT, 50, 2_000_000, "نوروز"));
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(2_000_000, t.rowDisc, 0.51);
    }

    @Test public void fractionalQuantity() {
        MeelanoPreinvoice.Draft d = draft(2.5, 100_000, 0);
        d.lines.get(0).taxPct = 0;
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(250_000, t.goods, 0.01);
    }
    @Test public void zeroQuantity() {
        MeelanoPreinvoice.Draft d = draft(0, 0, 100_000);
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(0, t.goods, 0.01);
        assertEquals(0, d.lines.get(0).diffAmt, 0.01);
    }
    @Test public void veryLargeAmount() {
        MeelanoPreinvoice.Draft d = draft(1000, 1_000_000_000, 0);
        d.lines.get(0).taxPct = 0;
        assertEquals(1_000_000_000_000.0, MeelanoPreinvoice.compute(d).goods, 1.0);
    }
    @Test public void discountOver100Capped() {
        MeelanoPreinvoice.Draft d = draft(1, 100_000, 0);
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(MeelanoPreinvoice.DiscType.ROW_PERCENT, 150, 0, ""));
        d.lines.get(0).taxPct = 0;
        MeelanoPreinvoice.Totals t = MeelanoPreinvoice.compute(d);
        assertEquals(100_000, t.rowDisc, 0.01);
        assertEquals(0, d.lines.get(0).net, 0.01);
    }
    @Test public void multipleLines() {
        MeelanoPreinvoice.Draft d = new MeelanoPreinvoice.Draft();
        d.lines.add(new MeelanoPreinvoice.Line("A", "کالا A", "عدد", 2, 500_000, 0));
        d.lines.add(new MeelanoPreinvoice.Line("B", "کالا B", "عدد", 3, 200_000, 0));
        d.lines.get(0).discs.add(new MeelanoPreinvoice.Disc(MeelanoPreinvoice.DiscType.ROW_PERCENT, 10, 0, ""));
        d.lines.get(1).discs.add(new MeelanoPreinvoice.Disc(MeelanoPreinvoice.DiscType.ROW_PER_UNIT, 10_000, 0, ""));
        d.lines.get(0).taxPct = 10; d.lines.get(1).taxPct = 10;
        assertEquals(1_617_000, MeelanoPreinvoice.compute(d).grand, 1.0);
    }
    @Test public void allRoundingModes() {
        MeelanoPreinvoice.Draft d = draft(1, 123_456.7, 0);
        d.lines.get(0).taxPct = 0;
        d.rounding = "rial"; assertEquals(123457, MeelanoPreinvoice.compute(d).goods, 0.5);
        d.rounding = "toman"; assertEquals(123460, MeelanoPreinvoice.compute(d).goods, 0.5);
        d.rounding = "hundred"; assertEquals(123500, MeelanoPreinvoice.compute(d).goods, 0.5);
        d.rounding = "thousand"; assertEquals(123000, MeelanoPreinvoice.compute(d).goods, 0.5);
    }
}
