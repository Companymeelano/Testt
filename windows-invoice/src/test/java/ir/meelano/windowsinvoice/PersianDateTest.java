package ir.meelano.windowsinvoice;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersianDateTest {
    @Test void convertsNowruzAndLeapDayBoundaries() {
        assertEquals("1403/01/01", PersianDate.fromGregorian(LocalDate.of(2024, 3, 20)));
        assertEquals("1403/12/30", PersianDate.fromGregorian(LocalDate.of(2025, 3, 20)));
        assertEquals("1404/01/01", PersianDate.fromGregorian(LocalDate.of(2025, 3, 21)));
    }

    @Test void matchesExpectedLocalInvoiceDate() {
        assertEquals("1405/08/16", PersianDate.fromGregorian(LocalDate.of(2026, 11, 7)));
    }

    @Test void validatesMonthLengthsAndEsfandLeapDay() {
        org.junit.jupiter.api.Assertions.assertTrue(PersianDate.isValidJalaliDate("1403/12/30"));
        org.junit.jupiter.api.Assertions.assertFalse(PersianDate.isValidJalaliDate("1404/12/30"));
        org.junit.jupiter.api.Assertions.assertFalse(PersianDate.isValidJalaliDate("1405/07/31"));
    }
}
