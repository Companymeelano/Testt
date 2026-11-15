package ir.meelano.manager.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MotionPolicyTest {
    @Test public void allowsMotionWhenFrameworkAndAllScalesAreEnabled() {
        assertTrue(MotionPolicy.animationsEnabled(true, 1f, 1f, 1f));
        assertTrue(MotionPolicy.animationsEnabled(true, 0.5f, 0.75f, 1.25f));
    }

    @Test public void anySystemReducedMotionSwitchDisablesDecorativeMotion() {
        assertFalse(MotionPolicy.animationsEnabled(false, 1f, 1f, 1f));
        assertFalse(MotionPolicy.animationsEnabled(true, 0f, 1f, 1f));
        assertFalse(MotionPolicy.animationsEnabled(true, 1f, 0f, 1f));
        assertFalse(MotionPolicy.animationsEnabled(true, 1f, 1f, 0f));
    }

    @Test public void invalidSystemScalesFailClosed() {
        assertFalse(MotionPolicy.animationsEnabled(true, Float.NaN, 1f, 1f));
        assertFalse(MotionPolicy.animationsEnabled(true, 1f, Float.POSITIVE_INFINITY, 1f));
        assertFalse(MotionPolicy.animationsEnabled(true, 1f, 1f, -1f));
    }
}
