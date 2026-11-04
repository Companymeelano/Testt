package ir.meelano.manager.core;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test public void preciseBankVarianceDisplayDoesNotRoundAwayFractionalRials() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_RIAL);
            assertEquals("\u2066۰٫۵۱\u2069 ریال", HomeNumberFormatter.preciseMoney(new BigDecimal("0.51")));
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals("\u2066۰٫۰۵۱\u2069 تومان", HomeNumberFormatter.preciseMoney(new BigDecimal("0.51")));
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
        assertEquals("مبلغ ۱٬۲۳۴٫۵٪، تاریخ ۱۴۰۵/۰۸/۰۴ تا ۱۴۰۵/۰۸/۳۰",
                HomeNumberFormatter.localizeText("مبلغ 1,234.5%, تاریخ 1405/08/04 تا 1405/08/30"));
        assertEquals("−۵٪", HomeNumberFormatter.localizeText("-5%"));
    }

    @Test public void chartScaleAndLargeChangeUseActualAmounts() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals(1_000_000_000_000d,
                    HomeNumberFormatter.chartScale(12_000_000_000_000d), 0.1d);
            assertEquals("۱٫۲", HomeNumberFormatter.chartTick(
                    12_000_000_000_000d, 1_000_000_000_000d));
            String readableChange = HomeNumberFormatter.changeLabel(34_000_000_000d,
                    4_300_000_000d, 690d, "فروش دورهٔ قبل");
            assertTrue(readableChange.contains(HomeNumberFormatter.signedPercent(690d)));
            assertTrue(readableChange.contains("فروش دورهٔ قبل"));

            String extremeChange = HomeNumberFormatter.changeLabel(34_000_000_000d,
                    1_000_000_000d, 3_300d, "میانگین روزهای هم‌نام");
            assertTrue(extremeChange.contains(HomeNumberFormatter.signedCompactMoney(33_000_000_000d)));
            assertTrue(extremeChange.contains("میانگین روزهای هم‌نام"));
            assertFalse(extremeChange.contains("٪"));
            assertTrue(HomeNumberFormatter.changeLabel(100d, 0d, Double.NaN, "مبنای قبل")
                    .contains("صفر است"));
        } finally {
            Money.setUnit(old);
        }
    }

    @Test public void comparisonSwitchesToSignedMoneyOnlyAboveReadablePercentRange() {
        assertEquals(HomeNumberFormatter.signedPercent(999d),
                HomeNumberFormatter.changeValue(1_099d, 100d, 999d));
        assertEquals(HomeNumberFormatter.signedCompactMoney(1_000d),
                HomeNumberFormatter.changeValue(1_100d, 100d, 1_000d));
        assertEquals(HomeNumberFormatter.signedCompactMoney(1_000d),
                HomeNumberFormatter.changeValue(1_100d, 100d, Double.POSITIVE_INFINITY));
        assertEquals("شروع از صفر", HomeNumberFormatter.changeValue(1d, 0d, Double.NaN));
        assertEquals("شروع از صفر", HomeNumberFormatter.changeValue(1d, 0d, 10_000d));
        assertFalse(HomeNumberFormatter.changeValue(1d, 0d, 10_000d).contains("٪"));
    }

    @Test public void displayNameDropsReplacementGlyphsAndCollapsesWhitespace() {
        assertEquals("علی محمد", HomeNumberFormatter.cleanDisplayName("علی\uFFFD\u00A0محمد"));
        assertEquals("Admin 12", HomeNumberFormatter.cleanDisplayName(" Admin  12 "));
        assertEquals("علی اکبر", HomeNumberFormatter.cleanDisplayName("علی" + (char) 0x202E + "اکبر"));
        assertEquals("علی‌اکبر", HomeNumberFormatter.cleanDisplayName("علی" + (char) 0x200C + "اکبر"));
        assertEquals("", HomeNumberFormatter.cleanDisplayName("\uFFFD\uFFFC\u0000"));
        assertEquals("", HomeNumberFormatter.cleanDisplayName("   "));
    }
}
