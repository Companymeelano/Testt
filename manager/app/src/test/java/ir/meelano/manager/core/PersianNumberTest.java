package ir.meelano.manager.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class PersianNumberTest {
    @Test public void faDigits() { assertEquals(1234.0, PersianNumber.parse("۱۲۳۴").value, 0.001); }
    @Test public void arDigits() { assertEquals(56.0, PersianNumber.parse("٥٦").value, 0.001); }
    @Test public void persianDecimal() {
        assertEquals(12.5, PersianNumber.parse("۱۲٫۵").value, 0.001);
        assertEquals(12.5, PersianNumber.parse("12٫5").value, 0.001);
    }
    @Test public void thousandSeparatorsStripped() {
        assertEquals(1000000.0, PersianNumber.parse("1٬000٬000").value, 0.001);
        assertEquals(1000000.0, PersianNumber.parse("1,000,000").value, 0.001);
    }
    @Test public void incompleteDecimalKeepsPartial() {
        assertEquals(12.0, PersianNumber.parse("12.").value, 0.001);
        assertNull(PersianNumber.parse("abc").value);
        assertNotNull(PersianNumber.parse("abc").error);
    }
    @Test public void emptyIsNullNoError() {
        assertNull(PersianNumber.parse("").value);
        assertNull(PersianNumber.parse("").error);
        assertNull(PersianNumber.parse(null).value);
    }
    @Test public void valueOrZero() { assertEquals(0.0, PersianNumber.valueOrZero(""), 0.001); assertEquals(0.0, PersianNumber.valueOrZero("xyz"), 0.001); }
}
