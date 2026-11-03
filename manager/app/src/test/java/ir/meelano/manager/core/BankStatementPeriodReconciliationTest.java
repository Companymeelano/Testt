package ir.meelano.manager.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankStatementPeriodReconciliationTest {
    @Test public void completeStatementComparesEveryDayAndTreatsAbsentDaysAsConfirmedZero() throws Exception {
        BankStatementFile.Parsed statement = parsed(
                "date,amount,direction\n2026-03-21,100,credit\n2026-03-22,25,debit\n",
                "1405/01/01", "1405/01/02");
        Map<String, BankStatementPeriodReconciliation.DailyFlow> ledger = new LinkedHashMap<>();
        ledger.put("1405/01/01", new BankStatementPeriodReconciliation.DailyFlow(100, 0, 1));
        ledger.put("1405/01/03", new BankStatementPeriodReconciliation.DailyFlow(0, 50, 1));
        BankStatementPeriodReconciliation.Report report = BankStatementPeriodReconciliation.compare(
                "1405/01/01", "1405/01/03", statement, ledger, true);

        assertEquals(3, report.days.size());
        assertEquals(1, report.matchedDays);
        assertEquals(2, report.varianceDays);
        assertEquals(0, report.unavailableDays);
        assertEquals(1, report.bankOnlyDays);
        assertEquals(1, report.ledgerOnlyDays);
        assertFalse(report.fullyMatched());
        assertEquals(2, report.statementTransactions);
        assertEquals(2, report.ledgerTransactions);
        assertEquals(-25, report.outflowVarianceRials, 0.001);
    }

    @Test public void incompleteStatementDoesNotAssumeMissingDaysAreZeroOrReturnVariance() throws Exception {
        BankStatementFile.Parsed statement = parsed(
                "date,amount,direction\n2026-03-21,100,credit\n",
                "1405/01/01", "1405/01/02");
        Map<String, BankStatementPeriodReconciliation.DailyFlow> ledger = new LinkedHashMap<>();
        ledger.put("1405/01/01", new BankStatementPeriodReconciliation.DailyFlow(90, 0, 1));
        BankStatementPeriodReconciliation.Report report = BankStatementPeriodReconciliation.compare(
                "1405/01/01", "1405/01/02", statement, ledger, false);

        assertFalse(report.fullyComparable());
        assertFalse(report.fullyMatched());
        assertEquals(2, report.unavailableDays);
        assertTrue(Double.isNaN(report.inflowVarianceRials));
        assertEquals(BankStatementReconciliation.State.NEEDS_STATEMENT,
                report.days.get(0).comparison.state);
    }

    @Test public void oppositeDailyErrorsRemainVisibleEvenWhenPeriodTotalsCancel() throws Exception {
        BankStatementFile.Parsed statement = parsed(
                "date,amount,direction\n2026-03-21,100,credit\n2026-03-22,90,credit\n",
                "1405/01/01", "1405/01/02");
        Map<String, BankStatementPeriodReconciliation.DailyFlow> ledger = new LinkedHashMap<>();
        ledger.put("1405/01/01", new BankStatementPeriodReconciliation.DailyFlow(90, 0, 1));
        ledger.put("1405/01/02", new BankStatementPeriodReconciliation.DailyFlow(100, 0, 1));
        BankStatementPeriodReconciliation.Report report = BankStatementPeriodReconciliation.compare(
                "1405/01/01", "1405/01/02", statement, ledger, true);

        assertEquals(0, report.inflowVarianceRials, 0.001);
        assertEquals(2, report.varianceDays);
        assertFalse(report.fullyMatched());
    }

    private static BankStatementFile.Parsed parsed(String csv, String from, String to) throws Exception {
        BankStatementFile.Table table = BankStatementFile.read(
                new ByteArrayInputStream(csv.getBytes("UTF-8")), "test.csv");
        BankStatementFile.Mapping mapping = BankStatementFile.Mapping.suggested(table.headers);
        return BankStatementFile.analyze(table, mapping, from, to);
    }
}
