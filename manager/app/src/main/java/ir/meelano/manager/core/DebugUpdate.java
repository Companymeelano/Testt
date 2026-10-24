package ir.meelano.manager.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import ir.meelano.manager.BuildConfig;
import ir.meelano.manager.ShareProvider;

/**
 * Update channel for the debug edition (the one installed from the session branch).
 *
 * On every cold start the app reads {@code dist/update.json} from the public repository.
 * When that build id differs from the one compiled into this app, the user is offered the
 * APK straight from GitHub. Before anything is installed the file must pass: the size, the
 * SHA-256 published next to it, the package name, and the signer equal to the installed app.
 *
 * Everything here is best-effort: no network, a bad file or a timeout only skips the check.
 * Nothing in this class throws into the UI.
 */
public final class DebugUpdate {
    private DebugUpdate() { }

    /** Public descriptor written by the CI on every build. */
    public static final String MANIFEST_URL =
            "https://github.com/Companymeelano/Testt/raw/arena/fc6fe9b2-testt/dist/update.json";

    /** Only downloads from this repository are accepted. */
    private static final String ALLOWED_PREFIX = "https://github.com/Companymeelano/Testt/";
    private static final String PREFS = "meelano_debug_update";
    private static final long INTERVAL_MS = 6L * 60 * 60 * 1000;
    private static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final int CONNECT_MS = 10000;
    private static final int READ_MS = 30000;

    private static volatile boolean checkedThisRun = false;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** The descriptor, after validation. */
    static final class Manifest {
        String build = "";
        String sha256 = "";
        String url = "";
        String name = "";
        String notes = "";
        long size;
    }

    /** Called once per cold start, after the license gate. Silent unless a newer build exists. */
    public static void checkOnOpen(Activity a) {
        try {
            if (!BuildConfig.DEBUG || checkedThisRun) return;
            checkedThisRun = true;
            long last = prefs(a).getLong("last", 0L);
            if (System.currentTimeMillis() - last < INTERVAL_MS) return;
            run(a, false);
        } catch (Exception e) {
            Diag.ignored("DebugUpdate:open", e);
        }
    }

    /** Settings button: always answers the user. */
    public static void checkNow(Activity a) {
        try {
            if (!BuildConfig.DEBUG) {
                toast(a, "این بخش فقط در نسخه توسعه فعال است");
                return;
            }
            run(a, true);
        } catch (Exception e) {
            Diag.ignored("DebugUpdate:now", e);
        }
    }

    private static void run(final Activity a, final boolean manual) {
        final Context app = a.getApplicationContext();
        if (!online(app)) {
            if (manual) toast(app, "اتصال اینترنت برقرار نیست");
            return;
        }
        new Thread(() -> {
            Manifest m = null;
            try {
                m = fetch();
            } catch (Exception e) {
                Diag.ignored("DebugUpdate:fetch", e);
            }
            prefs(app).edit().putLong("last", System.currentTimeMillis()).apply();
            if (m == null) {
                if (manual) toast(app, "بررسی به‌روزرسانی ممکن نشد؛ بعداً دوباره تلاش کنید");
                return;
            }
            if (m.build.equals(BuildConfig.BUILD_ID)) {
                if (manual) toast(app, "شما آخرین نسخه را دارید");
                return;
            }
            final Manifest found = m;
            UI.post(() -> {
                if (!a.isFinishing()) offer(a, found);
            });
        }, "debug-update-check").start();
    }

    private static void offer(final Activity a, final Manifest m) {
        try {
            String body = "نسخه: " + m.name + "\nحجم: " + UpdateCenter.faSize(m.size)
                    + "\n\nدریافت از گیت‌هاب، بررسی امضا و سپس نصب روی همین گوشی؛ داده‌ها حفظ می‌شوند.";
            if (!m.notes.isEmpty()) body += "\n\n" + m.notes;
            new AlertDialog.Builder(a)
                    .setTitle("نسخه جدید آماده است")
                    .setMessage(body)
                    .setPositiveButton("دریافت و نصب", (d, w) -> download(a, m))
                    .setNegativeButton("بعداً", null)
                    .show();
        } catch (Exception e) {
            Diag.ignored("DebugUpdate:offer", e);
        }
    }

    private static void download(final Activity a, final Manifest m) {
        final Context app = a.getApplicationContext();
        toast(app, "دریافت نسخه جدید از گیت‌هاب شروع شد…");
        new Thread(() -> {
            File apk = null;
            try {
                File dir = new File(app.getCacheDir(), "debug-update");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                String tag = m.build.substring(0, Math.min(8, m.build.length()));
                apk = new File(dir, "update-" + tag + ".apk");
                String got = downloadTo(m.url, apk);
                if (m.size > 0 && apk.length() != m.size) throw new IOException("size mismatch");
                if (!got.equalsIgnoreCase(m.sha256)) throw new IOException("sha256 mismatch");
                final File ready = apk;
                UI.post(() -> install(a, ready));
            } catch (Exception e) {
                Diag.ignored("DebugUpdate:download", e);
                toast(app, "دریافت کامل نشد یا فایل معتبر نیست؛ دوباره تلاش کنید");
                if (apk != null) {
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                }
            }
        }, "debug-update-download").start();
    }

    /** Runs on the UI thread. Installs only a package that matches this app's name and signer. */
    private static void install(Activity a, File apk) {
        try {
            PackageManager pm = a.getPackageManager();
            String pkg = a.getPackageName();
            PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            PackageInfo cur = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            if (archive == null || !pkg.equals(archive.packageName)) {
                toast(a, "فایل دریافتی برای این برنامه نیست");
                return;
            }
            if (!sameSigner(archive, cur)) {
                // Different signing key: Android cannot replace the app in place. Hand the file
                // to the share sheet so the user can install it once (the same path as before).
                toast(a, "امضای این نسخه با نسخه نصب‌شده فرق دارد؛ فایل را برای نصب یک‌باره می‌فرستم");
                ShareProvider.share(a, apk, "application/vnd.android.package-archive", "MEELANO Manager");
                return;
            }
            ShareProvider.install(a, apk);
        } catch (Exception e) {
            Diag.ignored("DebugUpdate:install", e);
            toast(a, "اجرای نصب ممکن نشد؛ دوباره تلاش کنید");
        }
    }

    private static boolean sameSigner(PackageInfo archive, PackageInfo cur) {
        if (archive.signatures == null || cur.signatures == null
                || archive.signatures.length == 0 || cur.signatures.length == 0) return false;
        return Arrays.equals(archive.signatures[0].toByteArray(), cur.signatures[0].toByteArray());
    }

    /** Streams the file, hashing as it goes. Returns the SHA-256 in hex. */
    private static String downloadTo(String url, File out) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() / 100 != 2) throw new IOException("http " + c.getResponseCode());
            if (c.getContentLengthLong() > MAX_BYTES) throw new IOException("too large");
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IOException("no sha256");
            }
            long total = 0;
            try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("too large");
                    os.write(buf, 0, n);
                    md.update(buf, 0, n);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } finally {
            c.disconnect();
        }
    }

    /** Reads and validates the descriptor. Null on any problem. */
    private static Manifest fetch() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
        try {
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                    if (sb.length() > 16 * 1024) return null;
                }
            }
            JSONObject o = new JSONObject(sb.toString());
            Manifest m = new Manifest();
            m.build = o.optString("build", "").trim();
            m.sha256 = o.optString("sha256", "").trim().toLowerCase(Locale.US);
            m.url = o.optString("url", "").trim();
            m.name = o.optString("name", "").trim();
            m.notes = o.optString("notes", "").trim();
            m.size = o.optLong("size", 0L);
            boolean ok = m.build.matches("[0-9a-f]{7,40}")
                    && m.sha256.matches("[0-9a-f]{64}")
                    && m.url.startsWith(ALLOWED_PREFIX)
                    && m.size > 0 && m.size <= MAX_BYTES;
            return ok ? m : null;
        } catch (Exception e) {
            Diag.ignored("DebugUpdate:parse", e);
            return null;
        } finally {
            c.disconnect();
        }
    }

    private static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void toast(final Context c, final String msg) {
        UI.post(() -> {
            try {
                Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {
                // A toast that cannot be shown is not worth any crash.
            }
        });
    }
}
