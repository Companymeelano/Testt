package ir.meelano.manager.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small, private runtime-diagnostic breadcrumb for the standalone manager. It records the
 * exception type and code frames, but deliberately omits exception messages,
 * database addresses, queries, account names and credentials. The original
 * Android uncaught-exception handler still receives every crash.
 */
public final class CrashReporter {
    private static final String FILE_NAME = "manager-last-crash.txt";
    private static final int MAX_REPORT_BYTES = 16 * 1024;
    private static final AtomicBoolean WRITING = new AtomicBoolean(false);
    private static volatile boolean installed;

    private CrashReporter() { }

    public static synchronized void install(Context context) {
        if (installed || context == null) return;
        installed = true;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeReport(app, thread, error, "uncaught", true);
            } catch (Throwable ignored) {
                // A diagnostic failure must never replace the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /** Persist a handled UI/render failure so support can inspect it without the app exiting. */
    public static void recordHandled(Context context, String area, Throwable error) {
        try {
            if (context == null || error == null) return;
            writeReport(context.getApplicationContext(), Thread.currentThread(), error,
                    "handled:" + safe(area), false);
        } catch (Throwable ignored) {
            // Diagnostic logging must never break the recovery path.
        }
    }

    private static void writeReport(Context context, Thread thread, Throwable error,
                                    String source, boolean fatal) {
        if (context == null || error == null || !WRITING.compareAndSet(false, true)) return;
        try {
            File dir = context.getNoBackupFilesDir();
            if (dir == null || (!dir.exists() && !dir.mkdirs())) return;
            String report = format(context, thread, error, source, fatal);
            byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_REPORT_BYTES) {
                byte[] clipped = new byte[MAX_REPORT_BYTES];
                System.arraycopy(bytes, 0, clipped, 0, clipped.length);
                bytes = clipped;
            }
            try (FileOutputStream out = new FileOutputStream(new File(dir, FILE_NAME), false)) {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }
        } catch (Throwable ignored) {
            // Continue to Android's normal fatal-exception handling.
        } finally {
            WRITING.set(false);
        }
    }

    private static String format(Context context, Thread thread, Throwable error,
                                 String source, boolean fatal) {
        StringBuilder b = new StringBuilder(2048);
        b.append("MEELANO Manager diagnostics\n");
        b.append("fatal: ").append(fatal ? "yes" : "no").append('\n');
        b.append("source: ").append(safe(source)).append('\n');
        b.append("time: ").append(new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date())).append('\n');
        b.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        b.append("thread: ").append(thread == null ? "unknown" : safe(thread.getName())).append('\n');
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            b.append("app: ").append(safe(info.versionName)).append(" (").append(code).append(")\n");
        } catch (Throwable ignored) {
            b.append("app: unknown\n");
        }

        Throwable current = error;
        for (int cause = 0; current != null && cause < 4; cause++) {
            if (cause > 0) b.append("caused by: ");
            else b.append("exception: ");
            // Never call getMessage(): SQL/JDBC messages may contain private values.
            b.append(current.getClass().getName()).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int count = Math.min(frames == null ? 0 : frames.length, 16);
            for (int i = 0; i < count; i++) {
                StackTraceElement frame = frames[i];
                if (frame == null) continue;
                b.append("  at ").append(frame.getClassName()).append('.')
                        .append(frame.getMethodName()).append('(');
                String file = frame.getFileName();
                b.append(file == null ? "Unknown Source" : file);
                if (frame.getLineNumber() >= 0) b.append(':').append(frame.getLineNumber());
                b.append(")\n");
                if (b.length() > MAX_REPORT_BYTES - 256) return b.toString();
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return b.toString();
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static File reportFile(Context context) {
        if (context == null) return null;
        File dir = context.getNoBackupFilesDir();
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    public static boolean hasReport(Context context) {
        try {
            File file = reportFile(context);
            return file != null && file.isFile() && file.length() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Return only the bounded, local diagnostic report; never uploads anything. */
    public static String readReport(Context context) {
        File file = reportFile(context);
        if (file == null || !file.isFile()) return "";
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int total = 0;
            int n;
            while (total < MAX_REPORT_BYTES && (n = in.read(buffer, 0,
                    Math.min(buffer.length, MAX_REPORT_BYTES - total))) >= 0) {
                if (n == 0) continue;
                out.write(buffer, 0, n);
                total += n;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clearReport(Context context) {
        try {
            File file = reportFile(context);
            if (file != null && file.isFile()) file.delete();
        } catch (Exception ignored) { }
    }
}
