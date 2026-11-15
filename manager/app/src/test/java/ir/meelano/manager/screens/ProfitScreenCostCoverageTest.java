package ir.meelano.manager.screens;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfitScreenCostCoverageTest {
    @Test public void incompleteCostsUseTheSpecificWarningWhileUnavailableQueriesStayDistinct() {
        assertEquals("بهای کالا ناقص است",
                ProfitScreen.grossProfitCoverageWarningTitle(false));
        assertEquals("سود و زیان نمایش داده نمی‌شود",
                ProfitScreen.grossProfitCoverageWarningTitle(true));
    }

    @Test public void everyDailyRowMustHaveKnownCostAndAValidQualityCount() {
        Row complete = new Row();
        complete.put("qualityIssues", 0L);
        complete.put("sales", "1000");
        complete.put("cogs", "600");
        complete.put("profit", "400");

        assertTrue(ProfitScreen.grossProfitCoverageComplete(
                Arrays.asList(complete), true));

        Row incomplete = new Row();
        incomplete.put("qualityIssues", 1L);
        incomplete.put("sales", "1000");
        incomplete.put("cogs", "0");
        incomplete.put("profit", "1000");
        assertFalse(ProfitScreen.grossProfitCoverageComplete(
                Arrays.asList(incomplete), true));
    }

    @Test public void missingQualityMetadataOrQuerySourcesNeverPassAsComplete() {
        Row legacy = new Row();
        legacy.put("sales", "1000");
        legacy.put("cogs", "0");
        legacy.put("profit", "1000");

        assertFalse(ProfitScreen.grossProfitCoverageComplete(
                Arrays.asList(legacy), true));
        assertFalse(ProfitScreen.grossProfitCoverageComplete(
                Collections.<Row>emptyList(), false));
        assertFalse(ProfitScreen.grossProfitCoverageComplete(null, true));
    }

    @Test public void missingCostDetailsNameAffectedRowsWithoutTreatingThemAsZero() {
        Row incomplete = new Row();
        incomplete.put("day", "1405/08/13");
        incomplete.put("qualityIssues", 2L);

        String details = ProfitScreen.grossProfitCoverageDetails(Arrays.asList(incomplete));

        assertTrue(details.contains("مبلغ صفر جایگزین نمی‌شود"));
        assertTrue(details.contains("ردیف‌های نیازمند بررسی"));
        assertTrue(details.contains(HomeNumberFormatter.integer(2)));
        assertTrue(details.contains(Jalali.dispFa("1405/08/13")));
    }
}
