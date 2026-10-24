package ir.meelano.manager.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostics for errors that the app recovers from. Before this class, many failures
 * were caught and dropped, which made field problems impossible to trace.
 *
 * Privacy rule (same as {@link CrashReporter}): only the exception class and the code
 * location are stored. Exception messages can contain server addresses, queries or
 * account names, so they are never written.
 *
 * Writes never throw, and this class must not call itself from inside its own catch
 * blocks, otherwise a failing disk could recurse.
 */
public final class Diag {
    private static final String TAG = "MeelanoDiag";
    private static final String FILE_NAME = "manager-diag.log";
    private static final long MAX_BYTES = 96L * 1024L;
    private static final long KEEP_BYTES = 48L * 1024L;
    private static volatile File file;

    private Diag() { }

    /** Call once from the Application so records are persisted. Safe to call twice. */
    public static void init(Context context) {
        if (context == null || file != null) return;
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    /** A failure that was recovered from (for example, an optional column or query is missing). */
    public static void ignored(String where, Throwable t) {
        record("ignored", where, t);
    }

    /** A failure that changed what the user sees (a fallback was shown). */
    public static void warn(String where, Throwable t) {
        record("warn", where, t);
    }

    private static void record(String level, String where, Throwable t) {
        String cls = t == null ? "null" : t.getClass().getSimpleName();
        String loc = where == null ? "?" : where;
        try {
            Log.w(TAG, level + " " + loc + " " + cls);
        } catch (Throwable ignoredLog) {
            // Logcat unavailable (plain JVM tests). Nothing else to do.
        }
        File f = file;
        if (f == null) return;
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " | " + level + " | " + loc + " | " + cls + "\n";
        synchronized (Diag.class) {
            try {
                if (f.length() > MAX_BYTES) trim(f);
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                    w.write(line);
                }
            } catch (Throwable ioFailed) {
                // The disk is full or unavailable; Logcat already has the record above.
            }
        }
    }

    /** Keeps the newest KEEP_BYTES of the file, cut at a line boundary. */
    private static void trim(File f) {
        try {
            byte[] all = Files.readAllBytes(f.toPath());
            int from = Math.max(0, all.length - (int) KEEP_BYTES);
            while (from < all.length && all[from] != '\n') from++;
            if (from < all.length) from++;
            Files.write(f.toPath(), java.util.Arrays.copyOfRange(all, from, all.length));
        } catch (Throwable keep) {
            // If trimming fails, the next write simply appends again.
        }
    }

    /** The most recent records (location and exception class only), or "" if none. */
    public static String readRecent(Context context) {
        try {
            File f = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
            if (!f.exists()) return "";
            byte[] all = Files.readAllBytes(f.toPath());
            String s = new String(all, StandardCharsets.UTF_8);
            List<String> lines = java.util.Arrays.asList(s.split("\n"));
            int from = Math.max(0, lines.size() - 60);
            return String.join("\n", lines.subList(from, lines.size())).trim();
        } catch (Throwable read) {
            return "";
        }
    }
}
