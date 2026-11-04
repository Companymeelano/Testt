package ir.meelano.manager.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartReportEngineTest {
    @Test public void composesThreeGroundedSentencesWithSeparateChequeDirections() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.salesAvailable = true;
        input.salesDayComplete = true;
        input.salesRials = 12_000_000;
        input.documents = 4;
        input.comparisonAvailable = true;
        input.changePercent = 8.5;
        input.productsAvailable = true;
        input.leadingProduct = "پسته اکبری";
        input.leadingProductSalesRials = 5_000_000;
        input.overdueInvoices = 2;
        input.oldestOverdueDays = 3;
        input.dueInCheques = 2;
        input.dueOutCheques = 1;
        input.returnDocuments = 3;

        List<String> result = SmartReportEngine.compose(input);

        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("فروش امروز"));
        assertTrue(result.get(0).contains("همان روز هفته"));
        assertTrue(result.get(1).contains("پسته اکبری"));
        assertTrue(result.get(2).contains("فاکتور معوق"));
        assertTrue(result.get(2).contains("چک دریافتی"));
        assertTrue(result.get(2).contains("چک پرداختی"));
        assertTrue(result.get(2).contains("سند برگشتی"));
        assertFalse(result.get(2).contains("چک در سه روز آینده"));
        for (String sentence : result) assertTrue(sentence.endsWith("."));
    }

    @Test public void partialDaySalesClearlyLabelTheLinearEndOfDayEstimate() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.salesAvailable = true;
        input.salesDayComplete = false;
        input.salesRials = 6_000_000;
        input.documents = 2;
        input.comparisonAvailable = true;
        input.comparisonProjected = true;
        input.projectedSalesRials = 12_000_000;
        input.changePercent = 20d;
        input.productsAvailable = true;

        String first = SmartReportEngine.compose(input).get(0);

        assertTrue(first.contains("تا این لحظه"));
        assertTrue(first.contains("برآورد خطی فروش پایان روز"));
        assertTrue(first.contains(HomeNumberFormatter.signedPercent(20d)));
        assertTrue(first.contains("میانگین همان روز هفته"));
    }

    @Test public void missingSourcesAreCalledOutInsteadOfPresentedAsZeroes() {
        SmartReportEngine.Input input = new SmartReportEngine.Input();

        List<String> result = SmartReportEngine.compose(input);

        assertEquals(3, result.size());
        assertTrue(result.get(0).contains("در دسترس نیست"));
        assertTrue(result.get(1).contains("در دسترس نیست"));
        assertTrue(result.get(2).contains("نتیجهٔ پیگیری کامل نیست"));
        assertTrue(result.get(2).contains("چک‌های دریافتی"));
        assertFalse(result.get(0).contains("۰ تومان"));
        assertEquals(SmartReportEngine.FollowUpPriority.INCOMPLETE,
                SmartReportEngine.followUpPriority(input));
    }

    @Test public void limitedOverduePreviewNeverPretendsToBeTheFullCount() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.overdueInvoices = 5;
        input.overdueCountExact = false;

        String followUp = SmartReportEngine.compose(input).get(2);

        assertTrue(followUp.contains("حداقل"));
        assertTrue(followUp.contains("فهرست بررسی‌شده"));
    }

    @Test public void onlyLongOverdueInvoicesRaiseUrgency() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.overdueInvoices = 1;
        input.oldestOverdueDays = 2;

        assertEquals(SmartReportEngine.FollowUpPriority.FOLLOW_UP,
                SmartReportEngine.followUpPriority(input));

        input.oldestOverdueDays = 7;
        assertEquals(SmartReportEngine.FollowUpPriority.URGENT,
                SmartReportEngine.followUpPriority(input));

        input.oldestOverdueDays = -1;
        assertEquals(SmartReportEngine.FollowUpPriority.FOLLOW_UP,
                SmartReportEngine.followUpPriority(input));
    }

    @Test public void incomingAndOutgoingChequesAreFollowUpButNotUrgentByThemselves() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.dueInCheques = 2;
        input.dueOutCheques = 1;

        assertEquals(SmartReportEngine.FollowUpPriority.FOLLOW_UP,
                SmartReportEngine.followUpPriority(input));
    }

    @Test public void missingOneFollowUpSourcePreventsAFalseClearState() {
        SmartReportEngine.Input input = completeFollowUpSources();
        assertEquals(SmartReportEngine.FollowUpPriority.CLEAR,
                SmartReportEngine.followUpPriority(input));

        input.dueOutAvailable = false;
        assertEquals(SmartReportEngine.FollowUpPriority.INCOMPLETE,
                SmartReportEngine.followUpPriority(input));
    }

    @Test public void emptyButAvailableSourcesCanSafelyReportNoActivity() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.salesAvailable = true;
        input.salesDayComplete = true;
        input.productsAvailable = true;

        List<String> result = SmartReportEngine.compose(input);

        assertTrue(result.get(0).contains("فاکتوری"));
        assertTrue(result.get(1).contains("کالای پیشتازی"));
        assertTrue(result.get(2).contains("در داده‌های کامل پیگیری"));
    }

    private static SmartReportEngine.Input completeFollowUpSources() {
        SmartReportEngine.Input input = new SmartReportEngine.Input();
        input.overdueAvailable = true;
        input.overdueCountExact = true;
        input.dueInAvailable = true;
        input.dueOutAvailable = true;
        input.returnsAvailable = true;
        return input;
    }
}
