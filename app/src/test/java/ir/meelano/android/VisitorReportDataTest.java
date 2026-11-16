package ir.meelano.android;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class VisitorReportDataTest {
    @Test public void missingAndNullAreNotReportedAsZero() throws Exception {
        assertNull(VisitorReportData.count(null, "count"));
        assertNull(VisitorReportData.count(new JSONObject(), "count"));
        assertNull(VisitorReportData.amount(new JSONObject().put("amount", JSONObject.NULL), "amount"));
    }
    @Test public void realZeroAndDecimalsArePreserved() throws Exception {
        JSONObject row = new JSONObject().put("count", 0).put("amount", 123.45);
        assertEquals(Long.valueOf(0), VisitorReportData.count(row, "count"));
        assertEquals(123.45, VisitorReportData.amount(row, "amount"), 0.000001);
    }
    @Test public void stringsAreNotSilentlyConvertedToFinancialValues() throws Exception {
        JSONObject row = new JSONObject().put("count", "2").put("amount", "100");
        assertNull(VisitorReportData.count(row, "count"));
        assertNull(VisitorReportData.amount(row, "amount"));
    }
}
