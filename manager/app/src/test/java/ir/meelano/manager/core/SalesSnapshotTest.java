package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SalesSnapshotTest {
    private final int originalUnit = Money.unit();

    @After public void restoreUnit() { Money.setUnit(originalUnit); }

    @Test public void dailyAndChartTotalsShareRawRialSource() {
        List<Row> rows = new ArrayList<>();
        rows.add(row("1405/07/01", 1_250_000_000d, 2));
        rows.add(row("1405/07/02", 3_000_000_000d, 4));

        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, "1405/07/02");

        assertTrue(snapshot.isAvailable());
        assertEquals(SalesSnapshot.SOURCE_UNIT, "RIAL");
        assertEquals(3_000_000_000d, snapshot.salesRialsOn("1405/07/02"), 0.001);
        assertEquals(0d, snapshot.salesRialsOn("1405/07/03"), 0.001);
        assertEquals(4, snapshot.documentsOn("1405/07/02"));
        assertEquals(4_250_000_000d, snapshot.salesRialsBetween("1405/07/01", "1405/07/02"), 0.001);
        assertEquals(6, snapshot.documentsBetween("1405/07/01", "1405/07/02"));
    }

    @Test public void tomanConversionHappensOnlyAtDisplayBoundary() {
        SalesSnapshot snapshot = SalesSnapshot.fromRows(
                java.util.Collections.singletonList(row("1405/07/02", 4_250_000_000d, 1)), "1405/07/02");
        Money.setUnit(Money.UNIT_TOMAN);

        double rawRials = snapshot.salesRialsOn("1405/07/02");
        assertEquals(4_250_000_000d, rawRials, 0.001);
        assertEquals(425_000_000d, Money.toUnit(rawRials), 0.001);
        assertEquals("۴۲۵ میلیون", Money.compact(Money.toUnit(rawRials)));
        assertEquals("۴۲۵ میلیون تومان", Money.compactRial(rawRials));
    }

    @Test public void chartSeriesFillsMissingDaysWithZeroAndKeepsRawRials() {
        List<Row> rows = new ArrayList<>();
        rows.add(row("1405/07/01", 12_000_000d, 1));
        rows.add(row("1405/07/03", 30_000_000d, 2));
        SalesSnapshot snapshot = SalesSnapshot.fromRows(rows, "1405/07/03");

        List<SalesSnapshot.Day> series = snapshot.dailySeries("1405/07/03", 3);

        assertEquals(3, series.size());
        assertEquals("1405/07/01", series.get(0).date);
        assertEquals(12_000_000d, series.get(0).salesRials, 0.001);
        assertEquals("1405/07/02", series.get(1).date);
        assertEquals(0d, series.get(1).salesRials, 0.001);
        assertEquals(30_000_000d, series.get(2).salesRials, 0.001);
    }

    @Test public void nullRowsMeansUnavailableRatherThanKnownZeroSales() {
        SalesSnapshot snapshot = SalesSnapshot.fromRows(null, "1405/07/02");

        assertFalse(snapshot.isAvailable());
        assertEquals(0d, snapshot.salesRialsOn("1405/07/02"), 0.001);
        assertTrue(snapshot.dailySeries("1405/07/02", 14).isEmpty());
    }

    private static Row row(String date, double total, long docs) {
        Row row = new Row();
        row.put("day", date);
        row.put("total", total);
        row.put("docs", docs);
        return row;
    }
}
