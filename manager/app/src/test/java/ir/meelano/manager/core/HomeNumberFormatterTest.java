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
        assertEquals("\u2068۱٫۲ میلیون\u2069", HomeNumberFormatter.compactUnit(1_234_567));
        assertEquals("\u2068۰\u2069", HomeNumberFormatter.compactUnit(0));
    }

    @Test public void compactAndFullMoneyUseSelectedUnitWithoutChangingStoredRials() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals("\u2068۱٫۳ هزار\u202Fتومان\u2069", HomeNumberFormatter.compactMoney(12_500));
            assertEquals("\u2068۱٬۲۵۰\u202Fتومان\u2069", HomeNumberFormatter.money(12_500));
            assertEquals("۱٫۳ هزار", HomeNumberFormatter.chartMoney(12_500));
            Money.setUnit(Money.UNIT_RIAL);
            assertEquals("\u2068۱۲٬۵۰۰\u202Fریال\u2069", HomeNumberFormatter.money(12_500));
        } finally {
            Money.setUnit(old);
        }
    }

    @Test public void preciseBankVarianceDisplayDoesNotRoundAwayFractionalRials() {
        int old = Money.unit();
        try {
            Money.setUnit(Money.UNIT_RIAL);
            assertEquals("\u2068۰٫۵۱\u202Fریال\u2069", HomeNumberFormatter.preciseMoney(new BigDecimal("0.51")));
            Money.setUnit(Money.UNIT_TOMAN);
            assertEquals("\u2068۰٫۰۵۱\u202Fتومان\u2069", HomeNumberFormatter.preciseMoney(new BigDecimal("0.51")));
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
        assertEquals("پرداخت \u2066۱۲۳۴\u2069 / \u2066۵۶۷۸\u2069",
                HomeNumberFormatter.localizeText("پرداخت 1234 / ٥٦٧٨"));
        assertEquals("مبلغ \u2066۱٬۲۳۴٫۵٪\u2069، تاریخ \u2066۱۴۰۵/۰۸/۰۴\u2069 تا \u2066۱۴۰۵/۰۸/۳۰\u2069",
                HomeNumberFormatter.localizeText("مبلغ 1,234.5%, تاریخ 1405/08/04 تا 1405/08/30"));
        assertEquals("\u2066−۵٪\u2069", HomeNumberFormatter.localizeText("-5%"));
        assertEquals(HomeNumberFormatter.localizeText("مبلغ 12,345"),
                HomeNumberFormatter.localizeText(HomeNumberFormatter.localizeText("مبلغ 12,345")));
        int[] offsetMap = HomeNumberFormatter.localizedOffsetMap("قیمت 123 ریال");
        assertTrue(offsetMap["قیمت ".length()] > "قیمت ".length());
        assertEquals(HomeNumberFormatter.localizeText("قیمت 123 ریال").length(),
                offsetMap["قیمت 123 ریال".length()]);
    }

    @Test public void datesUseTextAndTableFormatsWithoutMixingTheTwo() {
        assertEquals("\u2068۱۳ آبان ۱۴۰۵\u2069", HomeNumberFormatter.dateText("1405/08/13"));
        assertEquals("\u2066۱۴۰۵/۰۸/۱۳\u2069", HomeNumberFormatter.dateTable("1405/08/13"));
        assertEquals("\u2068۱۳ آبان\u2069", HomeNumberFormatter.dateShortLabel("1405/08/13"));
        assertEquals("\u2068۱۳ آبان ۱۴۰۵\u2069، ساعت \u2066۰۸:۰۵:۵۹\u2069",
                HomeNumberFormatter.dateTime("1405/08/13 08:05:59"));
        assertEquals("\u2066۱۴۰۵/۰۸/۱۳\u2069 · \u2066۰۸:۰۵:۰۰\u2069",
                HomeNumberFormatter.dateTimeTable("1405/08/13T08:05:00"));
        assertEquals(HomeNumberFormatter.dateText("1405/08/13"), Jalali.dispFa("1405/08/13"));
        assertEquals(HomeNumberFormatter.dateTable("1405/08/13"), Jalali.dispTableFa("1405/08/13"));
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
