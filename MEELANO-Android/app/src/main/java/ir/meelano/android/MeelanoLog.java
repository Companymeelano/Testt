package ir.meelano.android;

/**
 * Tiny in-memory ring of the latest errors/warnings (phase 7H). The app historically swallowed
 * 300+ exceptions silently; now the important ones (DB watchdog, page failures, report-section
 * failures) leave a readable trail that the diagnostics page shows — no adb needed for support.
 */
final class MeelanoLog {
    private MeelanoLog() {}

    private static final java.util.ArrayDeque<String> RING = new java.util.ArrayDeque<>();
    private static final int MAX = 40;

    static synchronized void err(String tag, Throwable e) {
        put("E", tag, e == null ? "" : String.valueOf(e.getMessage()));
    }

    static synchronized void warn(String tag, String msg) {
        put("W", tag, msg);
    }

    private static void put(String level, String tag, String msg) {
        String line = level + " " + tag + ": " + (msg == null ? "" : msg);
        if (line.length() > 220) line = line.substring(0, 220) + "…";
        RING.addFirst(line);
        while (RING.size() > MAX) RING.removeLast();
    }

    static synchronized java.util.List<String> snapshot() {
        return new java.util.ArrayList<>(RING);
    }
}
