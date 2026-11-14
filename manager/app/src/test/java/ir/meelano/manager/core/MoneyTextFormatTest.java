package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoneyTextFormatTest {
    @Test public void displayMoneyKeepsNegativeSignWithDigitsAndUsesSelectedUnit() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals("\u2066−۱٬۲۵۰\u2069 تومان", Money.rialText(-12500));
            Money.setUnit(Money.UNIT_RIAL);
            assertEquals("\u2066۱۲٬۵۰۰\u2069 ریال", Money.rialText(12500));
        } finally {
            Money.setUnit(old);
        }
    }

    @Test public void zeroAndFractionalQuantitiesRemainVisibleInsideIsolates() {
        assertEquals("\u2066۰\u2069", Money.qtyText(0));
        assertEquals("\u2066−۱٬۲۳۴٫۵\u2069", Money.qtyText(-1234.5));
    }
}
