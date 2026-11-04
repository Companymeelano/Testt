package ir.meelano.manager.screens;

import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfitScreenCostCoverageTest {
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
}
