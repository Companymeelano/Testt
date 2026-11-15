package ir.meelano.manager.ui;

/** Pure policy for the operating-system animation and reduced-motion switches. */
public final class MotionPolicy {
    private MotionPolicy() { }

    /**
     * Decorative motion is allowed only when the framework and every relevant system scale
     * permit it. A disabled animation scale is treated as reduce motion, not as an error.
     */
    public static boolean animationsEnabled(boolean frameworkEnabled,
                                            float animatorScale,
                                            float transitionScale,
                                            float windowScale) {
        return frameworkEnabled
                && isPositiveFinite(animatorScale)
                && isPositiveFinite(transitionScale)
                && isPositiveFinite(windowScale);
    }

    private static boolean isPositiveFinite(float scale) {
        return scale > 0f && !Float.isNaN(scale) && !Float.isInfinite(scale);
    }
}
