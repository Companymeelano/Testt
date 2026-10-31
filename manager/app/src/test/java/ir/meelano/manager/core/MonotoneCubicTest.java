package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MonotoneCubicTest {
    @Test public void increasingSegmentsNeverOvershoot() {
        double[] x = {0, 1, 3, 4, 7};
        double[] y = {0, 2, 2.5, 8, 9};
        double[] m = MonotoneCubic.tangents(x, y);
        for (int i = 0; i < x.length - 1; i++) {
            double low = Math.min(y[i], y[i + 1]);
            double high = Math.max(y[i], y[i + 1]);
            for (int sample = 0; sample <= 100; sample++) {
                double point = x[i] + (x[i + 1] - x[i]) * sample / 100.0;
                double value = MonotoneCubic.interpolate(x[i], x[i + 1], y[i], y[i + 1],
                        m[i], m[i + 1], point);
                assertTrue("overshoot at segment " + i + ": " + value,
                        value >= low - 1e-9 && value <= high + 1e-9);
            }
        }
    }

    @Test public void localExtremumHasZeroTangent() {
        double[] m = MonotoneCubic.tangents(new double[]{0, 1, 2, 3}, new double[]{1, 4, 2, 3});
        assertEquals(0, m[1], 0);
        assertEquals(0, m[2], 0);
    }

    @Test public void twoPointsReduceToStraightLine() {
        double[] m = MonotoneCubic.tangents(new double[]{2, 6}, new double[]{3, 11});
        assertEquals(2, m[0], 0);
        assertEquals(2, m[1], 0);
        assertEquals(7, MonotoneCubic.interpolate(2, 6, 3, 11, m[0], m[1], 4), 1e-9);
    }
}
