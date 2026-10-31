package ir.meelano.manager.core;

import ir.meelano.manager.data.Row;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AtiranSpecialReportsTest {
    @Test public void expiryWatchKeepsExpiredAndNearExpiryItemsInRiskOrder() {
        String today = Jalali.todayStr();
        List<Row> rows = new ArrayList<>();
        rows.add(item("future", Jalali.addDays(today, 150)));
        rows.add(item("soon", Jalali.addDays(today, 30)));
        rows.add(item("expired", Jalali.addDays(today, -1)));

        List<Row> result = AtiranSpecialReports.mapExpiry(rows);
        assertEquals(2, result.size());
        assertEquals("expired", result.get(0).s("code"));
        assertEquals("منقضی‌شده", result.get(0).s("status"));
        assertEquals("soon", result.get(1).s("code"));
        assertTrue(result.get(1).s("status").contains("۳۰ روز"));
    }

    @Test public void attendanceAuditLabelsEventsAndPreservesManagerEditsAndVoids() {
        Row row = new Row();
        row.put("event", "in");
        row.put("source", "manager");
        row.put("biometric", "1");
        row.put("voided", 1);
        row.put("editedBy", "مدیر");
        row.put("editedAt", "2026-10-20 10:00:00");
        row.put("note", "اصلاح ساعت");
        List<Row> rows = new ArrayList<>();
        rows.add(row);

        AtiranSpecialReports.mapAttendance(rows);
        assertEquals("ورود", row.s("event"));
        assertEquals("ثبت/اصلاح مدیر", row.s("source"));
        assertEquals("تأیید زیستی", row.s("bio"));
        assertTrue(row.s("audit").contains("باطل‌شده"));
        assertTrue(row.s("audit").contains("مدیر"));
        assertTrue(row.s("audit").contains("اصلاح ساعت"));
    }

    private static Row item(String code, String expiration) {
        Row row = new Row();
        row.put("code", code);
        row.put("expiry", expiration);
        return row;
    }
}
