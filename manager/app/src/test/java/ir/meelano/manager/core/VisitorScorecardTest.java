package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisitorScorecardTest {
    @Test public void proratesOverlappingGoalAndKeepsCoachingSignalsSeparate() {
        Filter filter = new Filter();
        filter.preset = Filter.P_CUSTOM;
        filter.from = "1405/01/06";
        filter.to = "1405/01/10";

        Row raw = row("1", 50, 2, 1, 3, 40, 100, 1, 1,
                "1405/01/01", "1405/01/10");
        List<Row> result = VisitorScorecard.score(java.util.Collections.singletonList(raw), filter);

        assertEquals(1, result.size());
        Row scored = result.get(0);
        assertEquals(50d, scored.d("target"), 0.001d);
        assertEquals("۱۰۰٪", scored.s("score"));
        assertEquals("۱۰۰٪", scored.s("attainment"));
        assertEquals("۳۳٪", scored.s("coverage"));
        assertEquals("+۲۵٪", scored.s("growth"));
        assertTrue(scored.s("action").contains("پوشش مشتری پایین"));
        assertTrue(scored.s("action").contains("هدف تفکیکی لحاظ نشد"));
        assertFalse(scored.containsKey("goalFrom"));
    }

    @Test public void dimensionSpecificOnlyAndUnboundedReportsDoNotInventScores() {
        Filter bounded = new Filter();
        bounded.preset = Filter.P_CUSTOM;
        bounded.from = "1405/01/01";
        bounded.to = "1405/01/10";
        Row scoped = row("2", 100, 3, 2, 4, 0, 200, 0, 1,
                "1405/01/01", "1405/01/10");
        Row result = VisitorScorecard.score(java.util.Collections.singletonList(scoped), bounded).get(0);
        assertEquals("—", result.s("score"));
        assertEquals("هدف تفکیک‌شده؛ مبنای عمومی ندارد", result.s("action"));

        Filter all = new Filter();
        all.preset = Filter.P_ALL;
        all.from = "";
        all.to = "";
        Row generic = row("3", 100, 3, 2, 4, 0, 200, 1, 0,
                "1405/01/01", "1405/01/10");
        Row unbounded = VisitorScorecard.score(java.util.Collections.singletonList(generic), all).get(0);
        assertEquals("—", unbounded.s("score"));
        assertTrue(unbounded.s("action").contains("بازه نامحدود"));
    }

    @Test public void sortsScoredVisitorsAheadOfVisitorsWithoutComparableTargets() {
        Filter filter = new Filter();
        filter.preset = Filter.P_CUSTOM;
        filter.from = "1405/01/01";
        filter.to = "1405/01/10";
        List<Row> rows = new ArrayList<>();
        rows.add(row("1", 20, 1, 1, 1, 0, 100, 1, 0, "1405/01/01", "1405/01/10"));
        rows.add(row("2", 90, 2, 1, 1, 0, 100, 1, 0, "1405/01/01", "1405/01/10"));
        Row noTarget = row("3", 500, 2, 2, 3, 0, 0, 0, 0, "", "");
        rows.add(noTarget);

        List<Row> result = VisitorScorecard.score(rows, filter);
        assertEquals("2", result.get(0).s("id"));
        assertEquals("1", result.get(1).s("id"));
        assertEquals("3", result.get(2).s("id"));
    }

    private static Row row(String id, double sales, long invoices, long soldCustomers,
                           long assigned, double previousSales, double goal, long generic,
                           long scoped, String from, String to) {
        Row row = new Row();
        row.put("id", id);
        row.put("name", "ویزیتور " + id);
        row.put("active", "true");
        row.put("sales", sales);
        row.put("invoices", invoices);
        row.put("soldCustomers", soldCustomers);
        row.put("assignedCustomers", assigned);
        row.put("previousSales", previousSales);
        row.put("goalAmount", goal);
        row.put("genericGoals", generic);
        row.put("scopedGoals", scoped);
        row.put("goalFrom", from);
        row.put("goalTo", to);
        return row;
    }
}
