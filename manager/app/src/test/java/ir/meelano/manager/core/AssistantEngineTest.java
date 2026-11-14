package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ir.meelano.manager.data.Row;
import java.util.Arrays;
import org.junit.Test;

/** Persian router tests: matching, offline refusal, yesterday rules, follow-ups, context bound. */
public class AssistantEngineTest {

    private static Row row(Object... kv) {
        Row r = new Row();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    private static Assistant.Snap snap() {
        Assistant.Snap s = new Assistant.Snap();
        s.day = "1405/07/30";
        s.loadedAt = System.currentTimeMillis();
        s.salesToday = row("total", 1_000_000.0, "docs", 4L);
        s.salesYesterday = row("total", 2_000_000.0, "docs", 6L);
        s.debtors = Arrays.asList(row("party", "رضا", "amount", 900_000.0));
        s.overdue = Arrays.asList(row("party", "علی", "amount", 300_000.0, "days", 12L));
        s.prod = row("count", 50L, "active", 45L, "low", 3L, "out", 2L, "reorderN", 4L, "value", 5_000_000.0);
        return s;
    }

    private static boolean hasDigit(String t) {
        return t.matches("(?s).*[0-9۰-۹٠-٩].*");
    }

    @Test
    public void normalizesArabicLettersZwnjAndDigits() {
        assertEquals("کتاب ها 12", AssistantEngine.normalize("كتاب\u200cها ١٢"));
        assertEquals("12", AssistantEngine.normalize("۱۲"));
        assertEquals("12", AssistantEngine.normalize("١٢"));
    }

    @Test
    public void matchesMainIntents() {
        assertEquals(AssistantEngine.Tool.SALES, AssistantEngine.match("فروش امروز چطوره؟"));
        assertEquals(AssistantEngine.Tool.DEBTORS, AssistantEngine.match("بدهکارها کیا هستن"));
        assertEquals(AssistantEngine.Tool.OVERDUE, AssistantEngine.match("فاکتورهای معوق"));
        assertEquals(AssistantEngine.Tool.CASH_IN, AssistantEngine.match("دریافت امروز"));
        assertEquals(AssistantEngine.Tool.CASH_OUT, AssistantEngine.match("پرداخت امروز"));
        assertEquals(AssistantEngine.Tool.STOCK, AssistantEngine.match("کالای ناموجود"));
        assertEquals(AssistantEngine.Tool.HELP, AssistantEngine.match("راهنما"));
    }

    @Test
    public void chequePaymentIsChequesNotPayment() {
        // "چک پرداختی" must go to the cheque report; the cheque rule is checked before payment.
        assertEquals(AssistantEngine.Tool.CHEQUES, AssistantEngine.match("چک پرداختی"));
    }

    @Test
    public void unknownTextIsNotHandledSoNoFakeAnswerIsGiven() {
        assertNull(AssistantEngine.match("xyz بی‌ربط"));
        AssistantEngine.Reply r = AssistantEngine.respond(snap(), "xyz بی‌ربط", null);
        assertFalse(r.handled());
    }

    @Test
    public void dataAnswerComesFromSnapshot() {
        AssistantEngine.Reply r = AssistantEngine.respond(snap(), "فروش امروز", null);
        assertTrue(r.handled());
        assertTrue(r.text(), r.text().contains(Money.compactRial(1_000_000.0)));
    }

    @Test
    public void serverDownRefusesDataAndInventsNoNumbers() {
        AssistantEngine.Reply r = AssistantEngine.respond(null, "بدهکارها", null);
        assertTrue(r.handled());
        assertTrue(r.text(), r.text().contains("سرور در دسترس نیست"));
        assertFalse(r.text(), hasDigit(r.text()));
    }

    @Test
    public void helpWorksOffline() {
        AssistantEngine.Reply r = AssistantEngine.respond(null, "راهنما", null);
        assertTrue(r.handled());
        assertEquals(Assistant.help(), r.text());
    }

    @Test
    public void yesterdayOnlyForSales() {
        AssistantEngine.Reply sales = AssistantEngine.respond(snap(), "فروش دیروز", null);
        assertTrue(sales.text(), sales.text().contains(Money.compactRial(2_000_000.0)));

        AssistantEngine.Reply cash = AssistantEngine.respond(snap(), "دریافت دیروز", null);
        assertTrue(cash.text(), cash.text().contains("فقط فروش دیروز"));
        assertFalse(cash.text(), cash.text().contains(Money.compactRial(2_000_000.0)));
    }

    @Test
    public void followUpDayWordReusesLastTool() {
        AssistantEngine.Session s = new AssistantEngine.Session();
        AssistantEngine.respond(snap(), "فروش امروز", s);
        AssistantEngine.Reply r = AssistantEngine.respond(snap(), "دیروز؟", s);
        assertEquals(AssistantEngine.Tool.SALES, r.tool());
        assertTrue(r.text(), r.text().contains(Money.compactRial(2_000_000.0)));
    }

    @Test
    public void sessionContextIsBounded() {
        AssistantEngine.Session s = new AssistantEngine.Session();
        for (int i = 0; i < 20; i++) AssistantEngine.respond(snap(), "بدهکار", s);
        assertEquals(AssistantEngine.Session.MAX_TURNS, s.size());
    }

    @Test
    public void staleSnapshotIsSaidToBeOld() {
        Assistant.Snap s = snap();
        s.loadedAt = System.currentTimeMillis() - 10 * 60_000L;
        AssistantEngine.Reply r = AssistantEngine.respond(s, "بدهکار", null);
        assertTrue(r.text(), r.text().contains("دقیقه پیش"));
    }

    @Test
    public void everyDataToolHasPermissionScreen() {
        for (AssistantEngine.Tool t : AssistantEngine.Tool.values()) {
            if (t == AssistantEngine.Tool.HELP) {
                assertNull(t.screen);
            } else {
                assertNotNull(t.name(), t.screen);
            }
        }
    }

    // ---- Negative and offline cases (Phase 9) ----

    @Test
    public void emptyAndBlankInputIsNotHandled() {
        assertFalse(AssistantEngine.respond(snap(), "", null).handled());
        assertFalse(AssistantEngine.respond(snap(), "   \t  ", null).handled());
    }

    @Test
    public void sqlOrCodeLikeTextIsNotExecutedAndNotHandled() {
        // The model never produces SQL or code: such text matches no rule and gets no answer.
        assertFalse(AssistantEngine.respond(snap(), "DROP TABLE dbo.sales; --", null).handled());
        assertFalse(AssistantEngine.respond(snap(), "SELECT * FROM dbo.Customers", null).handled());
        assertFalse(AssistantEngine.respond(snap(), "Runtime.getRuntime().exec('rm -rf /')", null).handled());
    }

    @Test
    public void veryLongInputDoesNotBreakTheRouter() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("فروش ");
        AssistantEngine.Reply r = AssistantEngine.respond(snap(), sb.toString(), null);
        assertNotNull(r);
        assertEquals(AssistantEngine.Tool.SALES, r.tool());
    }

    @Test
    public void everyDataToolRefusesWhenServerIsDownWithoutNumbers() {
        for (AssistantEngine.Tool t : AssistantEngine.Tool.values()) {
            if (t == AssistantEngine.Tool.HELP) continue;
            AssistantEngine.Reply r = AssistantEngine.respond(null, t.canonical, null);
            assertTrue(t.name(), r.handled());
            assertTrue(t.name() + ": " + r.text(), r.text().contains("سرور در دسترس نیست"));
            assertFalse(t.name() + ": " + r.text(), hasDigit(r.text()));
        }
    }

    @Test
    public void helpStillAnswersWhenOffline() {
        AssistantEngine.Reply r = AssistantEngine.respond(null, "راهنما", null);
        assertTrue(r.handled());
        assertEquals(AssistantEngine.Tool.HELP, r.tool());
    }

    @Test
    public void mixedLatinAndPersianDigitsStillRouteToTheSameTool() {
        assertNotNull(AssistantEngine.match("فروش امروز"));
        assertEquals(AssistantEngine.match("فروش امروز"), AssistantEngine.match("فروش امروز ۱۴۰۵"));
    }
}
