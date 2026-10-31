package ir.meelano.manager.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartReportEngineTest {
    @Test public void composesThreeGroundedSentencesFromAvailableInputs() {
        SmartReportEngine.Input input = new SmartReportEngine.Input();
        input.salesAvailable = true;
        input.salesRials = 12_000_000;
        input.documents = 4;
        input.comparisonAvailable = true;
        input.changePercent = 8.5;
        input.productsAvailable = true;
        input.leadingProduct = "پسته اکبری";
        input.leadingProductSalesRials = 5_000_000;
        input.overdueAvailable = true;
        input.overdueInvoices = 2;
        input.dueChequesAvailable = true;
        input.dueCheques = 1;
        input.returnsAvailable = true;
        input.returnDocuments = 3;

        List<String> result = SmartReportEngine.compose(input);

        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("فروش امروز"));
        assertTrue(result.get(0).contains("همان روز هفته"));
        assertTrue(result.get(1).contains("پسته اکبری"));
        assertTrue(result.get(2).contains("فاکتور معوق"));
        assertTrue(result.get(2).contains("چک در سه روز آینده"));
        assertTrue(result.get(2).contains("سند برگشتی"));
        for (String sentence : result) assertTrue(sentence.endsWith("."));
    }

    @Test public void missingSourcesAreCalledOutInsteadOfPresentedAsZeroes() {
        SmartReportEngine.Input input = new SmartReportEngine.Input();

        List<String> result = SmartReportEngine.compose(input);

        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("در دسترس نیست"));
        assertTrue(result.get(1).contains("در دسترس نیست"));
        assertTrue(result.get(2).contains("دادهٔ پیگیری کامل نیست"));
        assertFalse(result.get(0).contains("۰ تومان"));
    }

    @Test public void emptyButAvailableSourcesCanSafelyReportNoActivity() {
        SmartReportEngine.Input input = new SmartReportEngine.Input();
        input.salesAvailable = true;
        input.productsAvailable = true;
        input.overdueAvailable = true;
        input.dueChequesAvailable = true;

        List<String> result = SmartReportEngine.compose(input);

        assertTrue(result.get(0).contains("فاکتوری"));
        assertTrue(result.get(1).contains("کالای پیشتازی"));
        assertTrue(result.get(2).contains("مورد فوری ثبت نشده"));
    }
}
