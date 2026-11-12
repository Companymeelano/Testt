package ir.meelano.windowsinvoice.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** محاسبهٔ پیش‌فاکتور خرید: پله‌ها، ترتیب تخفیف‌ها، مالیات دوحالتی، گردکردن و بسته‌بندی. */
public class PurchasePreinvoiceEngineTest {

    private static PurchasePreinvoiceEngine.Draft draft(double qty, double price, double ref) {
        PurchasePreinvoiceEngine.Draft d = new PurchasePreinvoiceEngine.Draft();
        d.lines.add(new PurchasePreinvoiceEngine.Line("1001", "کالای نمونه", "عدد", qty, price, ref));
        return d;
    }

    @Test
    public void tieredQuantityPicksWinningStep() {
        PurchasePreinvoiceEngine.Draft d = draft(60, 100_000, 0);
        d.lines.get(0).discs.add(new PurchasePreinvoiceEngine.Disc(
                PurchasePreinvoiceEngine.DiscType.TIERED_QTY, 0, 0, "10:5,50:10"));
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(600_000, t.rowDisc, 0.51);   // ۱۰٪ برد برنده برای ۶۰ عدد
        assertEquals(5_400_000, t.grand - t.tax - t.duty, 1.01);
    }

    @Test
    public void discountOrderIsPerUnitThenPercents() {
        PurchasePreinvoiceEngine.Draft d = draft(10, 100_000, 0);
        PurchasePreinvoiceEngine.Line l = d.lines.get(0);
        l.discs.add(new PurchasePreinvoiceEngine.Disc(PurchasePreinvoiceEngine.DiscType.ROW_PERCENT, 10, 0, ""));
        l.discs.add(new PurchasePreinvoiceEngine.Disc(PurchasePreinvoiceEngine.DiscType.ROW_PER_UNIT, 20_000, 0, ""));
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        // 1,000,000 - 200,000 = 800,000 سپس ۱۰٪ ⇒ 720,000 خالص
        assertEquals(280_000, t.rowDisc, 0.51);
        assertEquals(720_000, l.net, 0.51);
    }

    @Test
    public void taxAddedOnNetPlusDuty() {
        PurchasePreinvoiceEngine.Draft d = draft(1, 1_000_000, 0);
        d.lines.get(0).dutyPct = 2;
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(100_000, t.tax, 0.51);
        assertEquals(20_000, t.duty, 0.51);
        assertEquals(1_120_000, t.grand, 0.51);
    }

    @Test
    public void inclusiveTaxIsExtractedNotAdded() {
        PurchasePreinvoiceEngine.Draft d = draft(1, 1_100_000, 0);
        d.taxMode = PurchasePreinvoiceEngine.TAX_INCLUSIVE;
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(100_000, t.tax, 0.51);
        assertEquals(1_100_000, t.grand, 0.51);
    }

    @Test
    public void invoicePercentSpreadsProRata() {
        PurchasePreinvoiceEngine.Draft d = draft(2, 500_000, 0);
        d.lines.get(0).taxPct = 0;
        d.lines.get(0).discs.add(new PurchasePreinvoiceEngine.Disc(
                PurchasePreinvoiceEngine.DiscType.INVOICE_PERCENT, 5, 0, ""));
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(50_000, t.invDisc, 0.51);
        assertEquals(950_000, t.grand, 0.51);
    }

    @Test
    public void roundingToThousand() {
        PurchasePreinvoiceEngine.Draft d = draft(3, 333_333.7, 0);
        d.rounding = "thousand";
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(1_000_000, t.goods, 0.01);
    }

    @Test
    public void diffAgainstReferenceIsLive() {
        PurchasePreinvoiceEngine.Draft d = draft(3, 120_000, 100_000);
        PurchasePreinvoiceEngine.compute(d);
        assertEquals(60_000, d.lines.get(0).diffAmt, 0.51);
        assertEquals(20, d.lines.get(0).diffPct, 0.001);
    }

    @Test
    public void cashDiscountAppliesAfterGrand() {
        PurchasePreinvoiceEngine.Draft d = draft(1, 1_000_000, 0);
        d.lines.get(0).taxPct = 0;
        d.cashDiscPct = 2.5;
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(25_000, t.cashDisc, 0.51);
        assertEquals(975_000, t.payable, 0.51);
    }

    @Test
    public void packUnpackRoundTripKeepsPersianAndDiscs() {
        PurchasePreinvoiceEngine.Draft d = draft(4, 250_000, 240_000);
        d.supplierName = "تامین‌کنندهٔ نمونه | ؛";
        d.docNo = "PI-7";
        d.lines.get(0).discs.add(new PurchasePreinvoiceEngine.Disc(
                PurchasePreinvoiceEngine.DiscType.TIERED_AMOUNT, 0, 0, "500000:3,1000000:6"));
        String packed = PurchasePreinvoiceEngine.pack(d);
        assertTrue(packed.indexOf('\n') < 0);
        PurchasePreinvoiceEngine.Draft back = PurchasePreinvoiceEngine.unpack(packed);
        assertEquals("PI-7", back.docNo);
        assertEquals(1, back.lines.size());
        assertEquals(1, back.lines.get(0).discs.size());
        assertEquals(PurchasePreinvoiceEngine.DiscType.TIERED_AMOUNT, back.lines.get(0).discs.get(0).type);
        assertEquals("500000:3,1000000:6", back.lines.get(0).discs.get(0).spec);
    }

    @Test
    public void promoCapLimitsDiscount() {
        PurchasePreinvoiceEngine.Draft d = draft(10, 1_000_000, 0);
        d.lines.get(0).discs.add(new PurchasePreinvoiceEngine.Disc(
                PurchasePreinvoiceEngine.DiscType.PROMO_PERCENT, 50, 2_000_000, "نوروز"));
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertEquals(2_000_000, t.rowDisc, 0.51);
    }
}
