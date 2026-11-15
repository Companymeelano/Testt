package ir.meelano.windowsinvoice.data;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class PersianNumberTest {
    @Test void faDigits() { assertEquals(1234.0, PersianNumber.parse("۱۲۳۴").value, 0.001); }
    @Test void thousandSeparators() { assertEquals(1000000.0, PersianNumber.parse("1٬000٬000").value, 0.001); }
    @Test void incomplete() { assertEquals(12.0, PersianNumber.parse("12.").value, 0.001); }
    @Test void invalidGivesError() { assertNull(PersianNumber.parse("abc").value); assertNotNull(PersianNumber.parse("abc").error); }
}
