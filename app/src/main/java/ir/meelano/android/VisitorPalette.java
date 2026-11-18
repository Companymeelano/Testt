package ir.meelano.android;

/** Pure colour math used for legible, theme-derived visitor controls. */
final class VisitorPalette {
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private VisitorPalette() { }

    private static double channel(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >>> 16) & 255)
                + 0.7152 * channel((color >>> 8) & 255) + 0.0722 * channel(color & 255);
    }

    static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    static int foreground(int background) {
        return contrast(BLACK, background) >= contrast(WHITE, background) ? BLACK : WHITE;
    }

    static int blend(int from, int to, double fraction) {
        int r = (int) Math.round(((from >>> 16) & 255) * (1 - fraction) + ((to >>> 16) & 255) * fraction);
        int g = (int) Math.round(((from >>> 8) & 255) * (1 - fraction) + ((to >>> 8) & 255) * fraction);
        int b = (int) Math.round((from & 255) * (1 - fraction) + (to & 255) * fraction);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /** Adjust only the foreground until it meets the target against an opaque background. */
    static int ensureContrast(int foreground, int background, double target) {
        int opaque = foreground | 0xff000000;
        if (contrast(opaque, background) >= target) return opaque;
        int end = foreground(background);
        for (int i = 1; i <= 100; i++) {
            int candidate = blend(opaque, end, i / 100.0);
            if (contrast(candidate, background) >= target) return candidate;
        }
        return end;
    }
}
