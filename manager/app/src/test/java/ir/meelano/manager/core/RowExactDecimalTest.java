package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RowExactDecimalTest {
    @Test public void financialDecimalGetterPreservesPrecisionAndFailsClosed() {
        Row row = new Row();
        row.put("amount", new BigDecimal("9007199254740993.50"));
        assertEquals(new BigDecimal("9007199254740993.50"), row.bd("amount"));

        row.put("malformed", "not-a-number");
        row.put("fractionalCount", new BigDecimal("1.5"));
        assertNull(row.bd("malformed"));
        assertNull(row.bd("missing"));
        assertNull(row.exactLong("fractionalCount"));
    }
}
