package ir.meelano.manager.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HomeTrendAnalysisTest {
    @Test public void callsOutDominantPeakButReturnsItsOriginalIndexAndValue() {
        double[] values = {10, 0, 11, 9, 10, 52, 12};

        HomeTrendAnalysis.Peak peak = HomeTrendAnalysis.dominantPeak(values);

        assertEquals(5, peak.index);
        assertEquals(52d, peak.value, 0.001d);
        assertEquals(10.5d, peak.medianPositiveValue, 0.001d);
        assertEquals(52d / 10.5d, peak.multipleOfMedian, 0.001d);
    }

    @Test public void ordinaryVariationOrTooFewActiveDaysIsNotFlagged() {
        assertNull(HomeTrendAnalysis.dominantPeak(new double[]{10, 11, 9, 12, 10, 13}));
        assertNull(HomeTrendAnalysis.dominantPeak(new double[]{0, 0, 0, 0, 100}));
        assertNull(HomeTrendAnalysis.dominantPeak(new double[]{10, 0, 50, 0}));
    }
}
