package ir.meelano.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Secure, user-confirmed updater for the signed all-in-one release APK. */
final class MeelanoAppUpdater {
    static final int REQUEST_INSTALL_SOURCE = 9471;
    private static final String PREFS = "meelano_app_update";
    private static final String KEY_MANIFEST = "verified_manifest";
    private static final String KEY_LAST_ATTEMPT = "last_check_attempt";
    private static final String KEY_STAGED_CODE = "staged_version_code";
    private static final long CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000;
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static volatile boolean cancelDownload;

    private MeelanoAppUpdater() { }

    static JSONObject pending(Context context) {
        try {
            String raw = prefs(context).getString(KEY_MANIFEST, "");
            if (raw.isEmpty()) return null;
            MeelanoUpdateManifest.parse(raw, installedVersionCode(context));
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    static void open(Activity activity, JSONObject json) {
        if (!supportedRelease(activity)) {
            toast(activity, "به‌روزرسانی فقط برای نسخهٔ انتشارِ اصلی فعال است.");
            return;
        }
        try {
            MeelanoUpdateManifest info = MeelanoUpdateManifest.parse(json.toString(), installedVersionCode(activity));
            showAvailable(activity, info);
        } catch (Exception e) {
            toast(activity, "اطلاعات نسخه معتبر نیست؛ دوباره نسخه‌های جدید را بررسی کنید.");
        }
    }

    static void check(Activity activity, boolean manual, Runnable refreshUi) {
        if (!supportedRelease(activity)) {
            if (manual) toast(activity, "به‌روزرسانی فقط برای نسخهٔ انتشارِ اصلی فعال است.");
            return;
        }
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(activity);
        if (!manual && now - p.getLong(KEY_LAST_ATTEMPT, 0) < CHECK_INTERVAL_MS) return;
        if (!CHECKING.compareAndSet(false, true)) {
            if (manual) toast(activity, "بررسی نسخه در حال انجام است.");
            return;
        }
        p.edit().putLong(KEY_LAST_ATTEMPT, now).apply();
        if (manual) toast(activity, "در حال بررسی نسخهٔ جدید…");
        new Thread(() -> {
            String raw = null;
            MeelanoUpdateManifest update = null;
            Exception failure = null;
            try {
                raw = readManifest();
                update = MeelanoUpdateManifest.parse(raw, 0);
                p.edit().putString(KEY_MANIFEST, raw).apply();
            } catch (Exception e) {
                failure = e;
            } finally {
                CHECKING.set(false);
            }
            final MeelanoUpdateManifest latest = update;
            final boolean failed = failure != null;
            run(activity, () -> {
                if (refreshUi != null) refreshUi.run();
                if (failed) {
                    if (manual) toast(activity, "بررسی به‌روزرسانی انجام نشد؛ اتصال اینترنت را بررسی کنید.");
                    return;
                }
                if (latest.versionCode <= installedVersionCode(activity)) {
                    if (manual) toast(activity, "برنامه به‌روز است.");
                    return;
                }
                showAvailable(activity, latest);
            });
        }, "meelano-update-check").start();
    }

    static boolean handleActivityResult(Activity activity, int requestCode) {
        if (requestCode != REQUEST_INSTALL_SOURCE) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            toast(activity, "اجازهٔ نصب از این منبع فعال نشد.");
            return true;
        }
        JSONObject update = pending(activity);
        if (update == null) {
            toast(activity, "اطلاعات نسخهٔ به‌روزرسانی پیدا نشد؛ دوباره بررسی کنید.");
            return true;
        }
        int code = update.optInt("versionCode", 0);
        if (prefs(activity).getInt(KEY_STAGED_CODE, 0) != code) {
            toast(activity, "فایل آمادهٔ نصب پیدا نشد؛ دوباره دانلود کنید.");
            return true;
        }
        try {
            MeelanoUpdateManifest info = MeelanoUpdateManifest.parse(update.toString(), installedVersionCode(activity));
            install(activity, info, apkFile(activity, code));
        } catch (Exception e) {
            toast(activity, "اعتبار فایل نصب قابل تأیید نیست؛ دوباره دانلود کنید.");
        }
        return true;
    }

    private static void showAvailable(Activity activity, MeelanoUpdateManifest info) {
        if (activity.isFinishing()) return;
        String message = "نسخهٔ " + info.versionName + " آماده است.\n"
                + "حجم: " + formatBytes(info.size) + "\n\n"
                + "فایل پیش از نصب از نظر تمامیت، بسته و گواهی امضا بررسی می‌شود. نصب نهایی را Android تأیید می‌کند.";
        new AlertDialog.Builder(activity)
                .setTitle("نسخهٔ جدید MEELANO")
                .setMessage(message)
                .setNegativeButton("بعداً", null)
                .setPositiveButton("دریافت و نصب", (d, which) -> download(activity, info))
                .show();
    }

    private static void download(Activity activity, MeelanoUpdateManifest info) {
        if (!DOWNLOADING.compareAndSet(false, true)) {
            toast(activity, "دریافت نسخه در حال انجام است.");
            return;
        }
        cancelDownload = false;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 28, 48, 16);
        TextView label = new TextView(activity);
        label.setText("در حال آماده‌سازی دریافت امن…");
        label.setGravity(Gravity.CENTER);
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.topMargin = 20;
        box.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(progress, progressLp);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("دریافت نسخهٔ جدید")
                .setView(box)
                .setNegativeButton("لغو", (d, which) -> cancelDownload = true)
                .create();
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            File apk = null;
            Exception failure = null;
            try {
                apk = downloadAndVerify(activity, info, percent -> run(activity, () -> {
                    if (!dialog.isShowing()) return;
                    progress.setProgress(percent);
                    label.setText("دریافت و بررسی امنیتی… " + (percent / 10) + "%");
                }));
            } catch (Exception e) {
                failure = e;
            } finally {
                DOWNLOADING.set(false);
            }
            final File result = apk;
            final Exception error = failure;
            run(activity, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (error != null || result == null) {
                    if (cancelDownload) toast(activity, "دریافت لغو شد.");
                    else toast(activity, "دریافت/بررسی فایل ناموفق بود؛ چیزی نصب نشد.");
                    return;
                }
                prefs(activity).edit().putInt(KEY_STAGED_CODE, info.versionCode).apply();
                new AlertDialog.Builder(activity)
                        .setTitle("فایل تأیید شد")
                        .setMessage("نسخهٔ " + info.versionName + " با گواهی رسمی بررسی شد. برای ادامه، نصب را تأیید کنید.")
                        .setNegativeButton("بعداً", null)
                        .setPositiveButton("ادامهٔ نصب", (d, which) -> installOrRequestPermission(activity, info, result))
                        .show();
            });
        }, "meelano-update-download").start();
    }

    private interface ProgressCallback { void update(int tenthsPercent); }

    private static File downloadAndVerify(Context context, MeelanoUpdateManifest info,
                                          ProgressCallback callback) throws Exception {
        File directory = MeelanoShareProvider.shareDir(context);
        File partial = new File(directory, "meelano-update-" + info.versionCode + ".part");
        File apk = apkFile(context, info.versionCode);
        HttpURLConnection connection = null;
        try {
            connection = openReleaseConnection(info.url);
            long responseLength = connection.getContentLengthLong();
            if (responseLength > 0 && responseLength != info.size) throw new IllegalStateException("unexpected content length");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial, false)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelDownload) throw new InterruptedException("cancelled");
                    copied += count;
                    if (copied > info.size || copied > MeelanoUpdateManifest.MAX_APK_BYTES) throw new IllegalStateException("APK too large");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    if (callback != null) callback.update((int) Math.min(1000, copied * 1000L / info.size));
                }
                output.flush();
                output.getFD().sync();
            }
            if (copied != info.size || !hex(digest.digest()).equalsIgnoreCase(info.sha256))
                throw new SecurityException("APK digest mismatch");
            verifyApk(context, partial, info);
            if (apk.exists() && !apk.delete()) throw new IllegalStateException("cannot replace staged APK");
            if (!partial.renameTo(apk)) throw new IllegalStateException("cannot finalize APK");
            purgeOldApks(directory, apk);
            return apk;
        } finally {
            if (connection != null) connection.disconnect();
            if (partial.exists()) //noinspection ResultOfMethodCallIgnored
                partial.delete();
        }
    }

    private static HttpURLConnection openReleaseConnection(String address) throws Exception {
        URL current = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())
                    || (current.getPort() != -1 && current.getPort() != 443)
                    || !allowedDownloadHost(current.getHost()))
                throw new SecurityException("untrusted redirect host");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setInstanceFollowRedirects(false);
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) return connection;
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirects == 5) throw new IllegalStateException("invalid redirect");
                current = new URL(current, location);
                continue;
            }
            connection.disconnect();
            throw new IllegalStateException("HTTP " + code);
        }
        throw new IllegalStateException("too many redirects");
    }

    private static boolean allowedDownloadHost(String host) {
        return "github.com".equalsIgnoreCase(host)
                || "release-assets.githubusercontent.com".equalsIgnoreCase(host)
                || "objects.githubusercontent.com".equalsIgnoreCase(host);
    }

    private static String readManifest() throws Exception {
        URL url = new URL(MeelanoUpdateManifest.META_URL);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !"raw.githubusercontent.com".equalsIgnoreCase(url.getHost()))
            throw new SecurityException("untrusted metadata URL");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IllegalStateException("manifest HTTP " + status);
            long length = connection.getContentLengthLong();
            if (length > 16_384) throw new IllegalStateException("manifest too large");
            try (InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 16_384) throw new IllegalStateException("manifest too large");
                    output.write(buffer, 0, count);
                }
                return output.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void verifyApk(Context context, File file, MeelanoUpdateManifest info) throws Exception {
        if (!file.isFile() || file.length() != info.size) throw new SecurityException("APK file size mismatch");
        String fileDigest = digestFile(file);
        if (!fileDigest.equalsIgnoreCase(info.sha256)) throw new SecurityException("APK digest mismatch");
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo apkInfo = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (apkInfo == null || !info.packageName.equals(apkInfo.packageName)) throw new SecurityException("unexpected APK package");
        long code = Build.VERSION.SDK_INT >= 28 ? apkInfo.getLongVersionCode() : apkInfo.versionCode;
        if (code != info.versionCode || !info.versionName.equals(apkInfo.versionName)) throw new SecurityException("APK version mismatch");
        PackageInfo installed = pm.getPackageInfo(info.packageName, flags);
        String[] apkSigners = signerDigests(apkInfo);
        String[] installedSigners = signerDigests(installed);
        if (apkSigners.length != 1 || installedSigners.length != 1
                || !MeelanoUpdateManifest.RELEASE_SIGNER_SHA256.equals(apkSigners[0])
                || !apkSigners[0].equals(installedSigners[0])
                || !info.signerSha256.equals(apkSigners[0]))
            throw new SecurityException("APK signing certificate mismatch");
    }

    private static String[] signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) return new String[0];
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null) return new String[0];
        String[] values = new String[signatures.length];
        for (int i = 0; i < signatures.length; i++) {
            values[i] = hex(MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray()));
        }
        java.util.Arrays.sort(values);
        return values;
    }

    private static String digestFile(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static void installOrRequestPermission(Activity activity, MeelanoUpdateManifest info, File apk) {
        try {
            verifyApk(activity, apk, info);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                prefs(activity).edit().putInt(KEY_STAGED_CODE, info.versionCode).apply();
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(permission, REQUEST_INSTALL_SOURCE);
                toast(activity, "اجازهٔ «نصب برنامه‌های ناشناس» را برای MEELANO فعال کنید و بازگردید.");
                return;
            }
            install(activity, info, apk);
        } catch (Exception e) {
            toast(activity, "نصب شروع نشد؛ اعتبار فایل دوباره بررسی و رد شد.");
        }
    }

    private static void install(Activity activity, MeelanoUpdateManifest info, File apk) throws Exception {
        verifyApk(activity, apk, info);
        Uri content = MeelanoShareProvider.uriFor(activity, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(content, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static void purgeOldApks(File directory, File keep) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.equals(keep) || !file.getName().startsWith("meelano-update-")
                    || !file.getName().endsWith(".apk")) continue;
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File apkFile(Context context, int code) {
        return new File(MeelanoShareProvider.shareDir(context), "meelano-update-" + code + ".apk");
    }

    private static boolean supportedRelease(Context context) {
        return !BuildConfig.DEBUG && MeelanoUpdateManifest.PACKAGE_NAME.equals(context.getPackageName());
    }

    private static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void run(Activity activity, Runnable action) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) action.run();
        });
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
