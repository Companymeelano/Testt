package ir.meelano.manager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CountUpTest {

    @Test public void easeStartsAtZeroLandsOnOneAndRisesFast() {
        assertEquals(0d, CountUp.ease(0d), 1e-9);
        assertEquals(1d, CountUp.ease(1d), 1e-9);
        assertEquals(1d, CountUp.ease(1.4d), 1e-9);
        assertEquals(0d, CountUp.ease(-0.2d), 1e-9);
        assertTrue(CountUp.ease(0.5d) > 0.5d);
        assertTrue(CountUp.ease(0.3d) < CountUp.ease(0.7d));
    }

    @Test public void valueAtIsEasedAndSafe() {
        assertEquals(0d, CountUp.valueAt(1000d, 0d), 1e-9);
        assertEquals(1000d, CountUp.valueAt(1000d, 1d), 1e-9);
        double mid = CountUp.valueAt(1000d, 0.5d);
        assertTrue(mid > 500d && mid < 1000d);
        assertTrue(Double.isNaN(CountUp.valueAt(Double.NaN, 0.5d)));
    }

    @Test public void parseReadsPersianGroupedAndDecimalNumbers() {
        assertEquals(1234d, CountUp.parse("۱٬۲۳۴"), 1e-9);
        assertEquals(1234567d, CountUp.parse("۱٬۲۳۴٬۵۶۷ تومان"), 1e-9);
        assertEquals(17.4d, CountUp.parse("۱۷٫۴"), 1e-6);
        assertEquals(0d, CountUp.parse("۰ تومان"), 1e-9);
        assertTrue(Double.isNaN(CountUp.parse("—")));
        assertTrue(Double.isNaN(CountUp.parse("داده در دسترس نیست")));
        assertTrue(Double.isNaN(CountUp.parse(null)));
    }

    @Test public void countableSkipsZeroAndNonNumbers() {
        assertTrue(CountUp.countable("۱٬۲۳۴ تومان"));
        assertFalse(CountUp.countable("۰ تومان"));
        assertFalse(CountUp.countable("—"));
        assertFalse(CountUp.countable(""));
    }

    @Test public void sweepKeepsFormattingAndLandsOnTheExactFinalText() {
        String money = "۱٬۲۳۴٬۵۶۷ تومان";
        assertEquals("۰ تومان", CountUp.sweep(money, 0d));
        assertEquals(money, CountUp.sweep(money, 1d));
        String mid = CountUp.sweep(money, 0.5d);
        assertTrue("grouping lost: " + mid, mid.contains("٬"));
        assertTrue("unit lost: " + mid, mid.endsWith(" تومان"));
        assertFalse("mid frame already final: " + mid, mid.equals(money));

        String compact = "۱۷٫۴م";
        assertEquals("۰٫۰م", CountUp.sweep(compact, 0d));
        assertEquals(compact, CountUp.sweep(compact, 1d));
        String half = CountUp.sweep(compact, 0.5d);
        assertTrue("decimal lost: " + half, half.contains("٫") && half.endsWith("م"));

        assertEquals("—", CountUp.sweep("—", 0.5d));
        assertEquals("", CountUp.sweep("", 0.5d));
        // Isolates around the number survive the sweep untouched.
        String isolated = "\u2066۲٬۰۰۰\u2069 ریال";
        String swept = CountUp.sweep(isolated, 0.5d);
        assertTrue(swept.startsWith("\u2066") && swept.endsWith("\u2069 ریال"));
    }

    @Test public void sweepFramesNeverExceedTheTarget() {
        String money = "۹۹۹ تومان";
        for (double t = 0d; t <= 1d; t += 0.1d) {
            double frame = CountUp.parse(CountUp.sweep(money, t));
            assertTrue("frame over target at " + t + ": " + frame, frame <= 999d + 1e-6);
        }
    }
}
