package ir.meelano.manager.core;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreinvoiceExportTest {
    @Test public void fractionalQuantitiesArePreservedInExports() {
        PurchasePreinvoice.Draft d = new PurchasePreinvoice.Draft();
        d.lines.add(new PurchasePreinvoice.Line("1001", "کالا", "کیلوگرم", 2.5, 100, 0));
        PurchasePreinvoice.Totals t = PurchasePreinvoice.compute(d);
        assertTrue(PreinvoiceExport.summaryText(d, t).contains("۲.۵"));
        assertTrue(new String(PreinvoiceExport.csv(d, t), StandardCharsets.UTF_8).contains(";2.5;"));
        assertTrue(new String(PreinvoiceExport.doc(d, t), StandardCharsets.UTF_8).contains(">2.5<"));
        assertTrue(new String(PreinvoiceExport.xls(d, t), StandardCharsets.UTF_8).contains(">2.5<"));
    }

    @Test public void timestampIsConvertedToItsLocalJalaliDate() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2024, Calendar.MARCH, 20, 12, 0, 0);
        String expected = Jalali.dispFa(Jalali.format(Jalali.gregorianDay(2024, 3, 20)));
        assertEquals(expected, PreinvoiceExport.dateText(c.getTimeInMillis()));
    }
}
