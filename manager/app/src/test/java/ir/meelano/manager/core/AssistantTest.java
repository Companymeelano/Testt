package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import java.util.Arrays;
import org.junit.Test;

public class AssistantTest {

    private static Row row(Object... kv) {
        Row r = new Row();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    private static Assistant.Snap snap() {
        Assistant.Snap s = new Assistant.Snap();
        s.day = "1405/07/23";
        s.salesToday = row("total", 1_000_000.0, "docs", 4L);
        s.salesYesterday = row("total", 2_000_000.0, "docs", 6L);
        s.overdue = Arrays.asList(row("party", "علی", "amount", 300_000.0, "days", 12L));
        s.debtors = Arrays.asList(row("party", "رضا", "amount", 900_000.0));
        s.prod = row("count", 50L, "active", 45L, "low", 3L, "out", 2L, "reorderN", 4L, "value", 5_000_000.0);
        return s;
    }

    @Test
    public void normalizesArabicLettersAndZwnj() {
        assertEquals("کتاب ها", Assistant.norm("كتاب\u200cها"));
    }

    @Test
    public void overdueQuestionListsTheOverdueParty() {
        String a = Assistant.answer(snap(), "فاکتورهای معوق چطور؟");
        assertTrue(a, a.contains("علی"));
    }

    @Test
    public void stockQuestionReportsOutAndLow() {
        String a = Assistant.answer(snap(), "موجودی و کالای ناموجود");
        assertTrue(a, a.contains("ناموجود"));
        assertTrue(a, a.contains("کم‌موجود"));
    }

    @Test
    public void unknownQuestionFallsBackToHelp() {
        String a = Assistant.answer(snap(), "xyz بی‌ربط");
        assertTrue(a, a.startsWith("متوجه نشدم"));
    }

    @Test
    public void suggestionsAreUrgentFirstAndNeverEmpty() {
        java.util.List<String> sug = Assistant.suggestions(snap());
        assertFalse(sug.isEmpty());
        assertTrue(sug.get(0), sug.get(0).contains("معوق"));
        assertTrue(Assistant.suggestions(new Assistant.Snap()).size() >= 1);
    }

    @Test
    public void averageUsesSevenDaysBeforeToday() {
        Assistant.Snap s = new Assistant.Snap();
        s.day = "1405/07/23";
        for (int i = 1; i <= 10; i++) {
            s.salesDaily.add(row("day", "1405/07/" + (10 + i), "total", (double) (i * 100)));
        }
        s.salesDaily.add(row("day", "1405/07/23", "total", 99999.0));
        // Days 4..10 (the last 7 before today): 400..1000 → average 700.
        assertEquals(700.0, Assistant.averageLastWeek(s), 0.0001);
    }

    @Test
    public void dailySummaryContainsSalesAndSuggestions() {
        String t = Assistant.dailySummary(snap());
        assertTrue(t, t.contains("خلاصه امروز"));
        assertTrue(t, t.contains("پیشنهاد مدیر"));
    }
}
