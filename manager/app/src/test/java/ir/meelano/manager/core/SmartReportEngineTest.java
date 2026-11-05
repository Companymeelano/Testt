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
        input.salesBaselineRials = 11_059_907.834d;
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
        assertTrue(result.get(0).contains("میانگین روزهای هم‌نام"));
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
        input.salesBaselineRials = 10_000_000;
        input.salesElapsedMinutes = 12 * 60;
        input.changePercent = 20d;
        input.productsAvailable = true;

        String first = SmartReportEngine.compose(input).get(0);

        assertTrue(first.contains("تا این لحظه"));
        assertTrue(first.contains("برآورد پایان روز"));
        assertTrue(first.contains("تا ساعت " + HomeNumberFormatter.time(12, 0)));
        assertTrue(first.contains(HomeNumberFormatter.signedPercent(20d)));
        assertTrue(first.contains("میانگین روزهای هم‌نام"));
        assertFalse(first.contains("برآورد خطی"));
    }

    @Test public void veryLargeSalesChangeUsesCurrencyAmountBesideItsBaseline() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.salesAvailable = true;
        input.salesDayComplete = true;
        input.salesRials = 34_000_000_000d;
        input.salesBaselineRials = 1_000_000_000d;
        input.documents = 3;
        input.comparisonAvailable = true;
        input.changePercent = 3_300d;

        String first = SmartReportEngine.compose(input).get(0);

        String change = HomeNumberFormatter.signedCompactMoney(
                input.salesRials - input.salesBaselineRials);
        assertTrue(HomeNumberFormatter.stripIsolates(change).startsWith("+"));
        assertTrue(first.contains(change));
        assertTrue(first.contains("میانگین روزهای هم‌نام"));
        assertFalse(first.contains("٪"));
    }

    @Test public void urgencyBadgeBelongsToTheOverdueRowNotEveryFollowUp() {
        SmartReportEngine.Input input = completeFollowUpSources();
        input.overdueInvoices = 1;
        input.oldestOverdueDays = 8;
        input.dueOutCheques = 2;

        List<SmartReportEngine.FollowUpItem> rows = SmartReportEngine.followUpItems(input);
        SmartReportEngine.FollowUpPriority overdue = null;
        SmartReportEngine.FollowUpPriority dueOut = null;
        for (SmartReportEngine.FollowUpItem row : rows) {
            if ("overdue".equals(row.key)) overdue = row.priority;
            if ("due-out".equals(row.key)) dueOut = row.priority;
        }
        assertEquals(SmartReportEngine.FollowUpPriority.URGENT, overdue);
        assertEquals(SmartReportEngine.FollowUpPriority.FOLLOW_UP, dueOut);
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
