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
        // Arabic kaf/yeh and ZWNJ must fold to Persian forms and a space.
        assertEquals("\u06a9\u062a\u0627\u0628 \u0647\u0627",
                Assistant.norm("\u0643\u062a\u0627\u0628\u200c\u0647\u0627"));
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
        assertTrue(a, a.contains("کم"));
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
    public void averageUsesSevenCalendarDaysBeforeToday() {
        Assistant.Snap s = new Assistant.Snap();
        s.day = "1405/07/23";
        for (int i = 1; i <= 7; i++) {
            s.salesDaily.add(row("day", "1405/07/" + (15 + i), "total", (double) (i * 100), "invalidRows", 0L));
        }
        s.salesDaily.add(row("day", "1405/07/23", "total", 99999.0, "invalidRows", 0L));
        // Days 16..22: 100..700 → average 400; today's value is excluded.
        assertEquals(400.0, Assistant.averageLastWeek(s), 0.0001);
    }

    @Test
    public void invalidTrendRowsDoNotBecomeZeroSales() {
        Assistant.Snap s = new Assistant.Snap();
        s.day = "1405/07/23";
        s.salesDaily.add(row("day", "1405/07/22", "total", 9000.0, "invalidRows", 1L));
        assertEquals(0.0, Assistant.averageLastWeek(s), 0.0001);
    }

    @Test
    public void dailySummaryContainsSalesAndSuggestions() {
        String t = Assistant.dailySummary(snap());
        assertTrue(t, t.contains("خلاصه امروز"));
        assertTrue(t, t.contains("پیشنهاد مدیر"));
    }

    @Test
    public void failedSectionsAreNotReportedAsConfirmedZero() {
        Assistant.Snap s = snap();
        s.notes.put("فروش امروز", "connection unavailable");
        String answer = Assistant.answer(s, "فروش امروز");
        assertTrue(answer, answer.contains("داده در دسترس نیست"));
        assertFalse(answer, answer.contains(Money.compactRial(0)));
        String summary = Assistant.dailySummary(s);
        assertTrue(summary, summary.contains("فروش امروز: داده در دسترس نیست"));
    }

    @Test
    public void missingListAmountsAndInventoryCountsAreNotPresentedAsZero() {
        Assistant.Snap s = snap();
        s.overdue = java.util.Collections.singletonList(row("party", "مشتری نمونه", "days", 3L));
        String overdue = Assistant.answer(s, "معوق‌ها");
        assertTrue(overdue, overdue.contains("داده در دسترس نیست"));
        assertFalse(overdue, overdue.contains(Money.compactRial(0)));

        s.prod = row("out", 2L);
        String stock = Assistant.answer(s, "موجودی کالا");
        assertTrue(stock, stock.contains("کم‌موجود: داده در دسترس نیست"));
    }

    @Test
    public void malformedNumericFieldIsNotPresentedAsZero() {
        Assistant.Snap s = snap();
        s.salesToday = row("total", "not-a-number");
        String answer = Assistant.answer(s, "فروش امروز");
        assertTrue(answer, answer.contains("فروش امروز: داده در دسترس نیست"));
        assertFalse(answer, answer.contains(Money.compactRial(0)));
    }

    @Test
    public void failedEmptyListIsNotPresentedAsNoRecords() {
        Assistant.Snap s = snap();
        s.overdue = java.util.Collections.emptyList();
        s.notes.put("معوق‌ها", "query failed");
        String answer = Assistant.answer(s, "معوق‌ها");
        assertTrue(answer, answer.contains("معوق‌ها: داده در دسترس نیست"));
        assertFalse(answer, answer.contains("ردیفی برنگشت"));
    }
}
