package ir.meelano.windowsinvoice.data;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PurchasePreinvoiceExportWinTest {
    @Test public void fractionalQuantitiesArePreservedInExports() {
        PurchasePreinvoiceEngine.Draft d = new PurchasePreinvoiceEngine.Draft();
        d.lines.add(new PurchasePreinvoiceEngine.Line("1001", "کالا", "کیلوگرم", 2.5, 100, 0));
        PurchasePreinvoiceEngine.Totals t = PurchasePreinvoiceEngine.compute(d);
        assertTrue(PurchasePreinvoiceExportWin.summary(d, t).contains("۲.۵"));
        assertTrue(new String(PurchasePreinvoiceExportWin.csv(d, t), StandardCharsets.UTF_8).contains(";2.5;"));
        assertTrue(new String(PurchasePreinvoiceExportWin.doc(d, t), StandardCharsets.UTF_8).contains(">2.5<"));
        assertTrue(new String(PurchasePreinvoiceExportWin.xls(d, t), StandardCharsets.UTF_8).contains(">2.5<"));
    }

    @Test public void persianDigitsRetainNegativeSigns() {
        assertEquals("-۱۲۳", PurchasePreinvoiceExportWin.faNum(-123));
    }
}
