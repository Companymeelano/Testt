package ir.meelano.manager.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** گزارش تیک‌خورده/تیک‌نخورده باید دقیقاً همان چیزی را بگوید که دفترچه ثبت کرده است. */
public class ChequeApprovalPlanTest {

    private static ChequeApprovalPlan.Row row(boolean in, String num, String sayad, String cust,
                                              double amount, String due) {
        return new ChequeApprovalPlan.Row(in, num, sayad, cust, amount, due, "");
    }

    private static List<ChequeApprovalPlan.Row> sample() {
        List<ChequeApprovalPlan.Row> rows = new ArrayList<>();
        rows.add(row(true, "1001", "S1", "الف", 10_000_000d, "1405/08/01")); // overdue vs today 1405/08/10
        rows.add(row(true, "1002", "S2", "ب", 20_000_000d, "1405/08/12"));   // due soon (2 days)
        rows.add(row(true, "1003", "", "ب", 30_000_000d, "1405/09/01"));     // no sayad, far
        rows.add(row(false, "2001", "S4", "ج", 5_000_000d, "1405/07/01"));   // outgoing overdue
        return rows;
    }

    @Test
    public void splitsConfirmedAndPendingByRegistry() {
        Map<String, ChequeApprovalPlan.Entry> reg = new HashMap<>();
        reg.put(ChequeApprovalPlan.keyOf(true, "1002", "S2"),
                new ChequeApprovalPlan.Entry(123L, "مدیر", "صیادی"));
        ChequeApprovalPlan.Plan p = ChequeApprovalPlan.plan(sample(), reg, Jalali.parse("1405/08/10"));
        assertEquals(1, p.confirmedCount());
        assertEquals(3, p.pendingCount());
        assertEquals(20_000_000d, p.confirmedSum, 0.01);
        assertEquals(45_000_000d, p.pendingSum, 0.01);
        assertEquals(25, p.ratePercent());
        assertTrue(p.isConfirmed(ChequeApprovalPlan.keyOf(true, "1002", "S2")));
        assertFalse(p.isConfirmed(ChequeApprovalPlan.keyOf(true, "1003", "")));
    }

    @Test
    public void overdueAndDueSoonAndNearestAreComputed() {
        ChequeApprovalPlan.Plan p = ChequeApprovalPlan.plan(sample(), new HashMap<>(), Jalali.parse("1405/08/10"));
        assertEquals(2, p.pendingOverdue);      // 1405/08/01 and 1405/07/01
        assertEquals(1, p.pendingDueSoon);      // 1405/08/12
        assertEquals("1002", p.nearestPending.num);   // next upcoming due wins over overdue rows
        assertEquals(2, p.nearestPendingDays);
    }

    @Test
    public void topPendingCustomersRanked() {
        ChequeApprovalPlan.Plan p = ChequeApprovalPlan.plan(sample(), new HashMap<>(), Jalali.parse("1405/08/10"));
        assertEquals(3, p.topPending.size());
        assertTrue(p.topPending.get(0).startsWith("ب ×"));
    }

    @Test
    public void sayadWinsOverNumberInKey() {
        assertEquals("in:S7:1001", ChequeApprovalPlan.keyOf(true, "1001", "S7"));
        assertEquals("out:1001:1001", ChequeApprovalPlan.keyOf(false, "1001", "  "));
    }

    @Test
    public void emptyPlanIsZeroNotNaN() {
        ChequeApprovalPlan.Plan p = ChequeApprovalPlan.plan(new ArrayList<>(), new HashMap<>(), Jalali.parse("1405/08/10"));
        assertEquals(0, p.ratePercent());
        assertEquals(0d, p.pendingSum, 0.001);
        assertEquals(null, p.nearestPending);
    }
}
