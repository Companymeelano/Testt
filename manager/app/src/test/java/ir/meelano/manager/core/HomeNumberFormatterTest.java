package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomeNumberFormatterTest {
    @Test public void homeNumbersUsePersianDigitsAndArabicSeparators() {
        assertEquals("\u2066۱۲٬۳۴۵٫۶\u2069", HomeNumberFormatter.decimal(12345.6));
        assertEquals("\u2066۰\u2069", HomeNumberFormatter.decimal(0));
        assertEquals("\u2066۰\u2069", HomeNumberFormatter.decimal(-0.01));
    }

    @Test public void compactValuesHaveAtMostOneFractionDigitAndKeepZeroReadable() {
        assertEquals("\u2066۱٫۲\u2069 میلیون", HomeNumberFormatter.compactUnit(1_234_567));
        assertEquals("\u2066۰\u2069", HomeNumberFormatter.compactUnit(0));
    }

    @Test public void compactAndFullMoneyUseSelectedUnitWithoutChangingStoredRials() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals("\u2066۱٫۳\u2069 هزار تومان", HomeNumberFormatter.compactMoney(12_500));
            assertEquals("\u2066۱٬۲۵۰\u2069 تومان", HomeNumberFormatter.money(12_500));
            assertEquals("\u2066۱٫۳\u2069 هزار", HomeNumberFormatter.chartMoney(12_500));
            Money.setUnit(Money.UNIT_RIAL);
            assertEquals("\u2066۱۲٬۵۰۰\u2069 ریال", HomeNumberFormatter.money(12_500));
        } finally {
            Money.setUnit(old);
        }
    }

    @Test public void signsPercentAndDirectionStayInsideBidiIsolates() {
        assertEquals("\u2066+۱۲٫۳٪\u2069", HomeNumberFormatter.signedPercent(12.34));
        assertEquals("\u2066−۱۲٫۳٪\u2069", HomeNumberFormatter.signedPercent(-12.34));
        assertEquals("\u2066↗ +۱۲٫۳٪\u2069", HomeNumberFormatter.directionalPercent(12.34));
        assertEquals("\u2066↘ −۱۲٫۳٪\u2069", HomeNumberFormatter.directionalPercent(-12.34));
        assertEquals("\u2066۰٪\u2069", HomeNumberFormatter.percent(0));
        assertEquals("\u2066۰٪\u2069", HomeNumberFormatter.signedPercent(-0.01));
        assertEquals(HomeNumberFormatter.time(8, 5), HomeNumberFormatter.time(HomeNumberFormatter.time(8, 5)));
    }

    @Test public void timeAndExternalLabelsAreLocalized() {
        assertEquals("\u2066۰۸:۰۵\u2069", HomeNumberFormatter.time(8, 5));
        assertEquals("پرداخت ۱۲۳۴ / ۵۶۷۸", HomeNumberFormatter.localizeText("پرداخت 1234 / ٥٦٧٨"));
    }
}
