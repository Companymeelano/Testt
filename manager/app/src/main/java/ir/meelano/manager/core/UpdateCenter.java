package ir.meelano.manager.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.ui.Kit;
import ir.meelano.manager.ui.Theme;
import ir.meelano.licensing.License;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Smart in-app update, tied to the license: only phones with an ACTIVE
 * license (bought or trial — {@link LicenseStore#unlocked}) are ever told
 * about a new release, and only when one actually exists.
 *
 * Trust chain (every link verified before anything is installed):
 * HTTPS release page → descriptor MAC signed with the license secret
 * (binds versionCode to the APK's SHA-256) → APK host allow-list →
 * SHA-256 of the downloaded file → package name → versionCode → signer
 * equality with the installed app. Any failure stops with a clear Persian
 * message; nothing half-installed is ever possible.
 */
public final class UpdateCenter {
    private UpdateCenter() { }

    /** startActivityForResult code for the unknown-sources settings screen. */
    public static final int REQ_UNKNOWN = 907;

    private static final String PREFS = "meelano_update";
    private static boolean autoDone = false;
    private static volatile boolean cancelled = false;
    private static File pendingApk = null;

    /** Verified release descriptor from version.json. */
    public static final class Info {
        public int code;
        public String name = "", apk = "", sha256 = "", url = "", msg = "";
        public long size;
    }

    // ================= entry points =================

    /**
     * Silent daily check for licensed users. Call once the shell is up
     * (MainActivity is only reachable when unlocked, which is exactly the
     * «buyers + trial» audience). Speaks ONLY when an update truly exists.
     */
    public static void autoCheck(Activity a) {
        if (autoDone) return;
        autoDone = true;
        try {
            if (isDebug(a) || !LicenseStore.unlocked(a)) return;
            long last = prefs(a).getLong("last_check", 0);
            if (System.currentTimeMillis() - last < UpdateConfig.AUTO_INTERVAL_MS) return;
            if (!online(a)) return;
            final Context app = a.getApplicationContext();
            new Thread(() -> {
                Info info = fetch(app);
                if (info == null) return;
                markChecked(app);
                purgeStale(app, info.code);
                if (info.code <= currentCode(app)) return;
                SharedPreferences p = prefs(app);
                if (info.code == p.getInt("dismissed_code", -1)
                        && System.currentTimeMillis() - p.getLong("dismissed_at", 0)
                        < UpdateConfig.AUTO_INTERVAL_MS) return;
                run(a, () -> showUpdateDialog(a, info));
            }).start();
        } catch (Exception ignored) { }
    }

    /** Manual check from Settings: always talks to the user. */
    public static void manualCheck(Activity a) {
        try {
            if (isDebug(a)) {
                toast(a, "در نسخه توسعه‌دهنده، بروزرسانی غیرفعال است");
                return;
            }
            if (!LicenseStore.unlocked(a)) {
                toast(a, "ابتدا برنامه را فعال کنید");
                return;
            }
            if (!online(a)) {
                toast(a, "به اینترنت وصل نیستید؛ اتصال را بررسی کنید");
                return;
            }
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(14));
            TextView t = kit.text("در حال بررسی نسخه جدید…", 14f, Theme.TEXT, false);
            t.setGravity(Gravity.CENTER);
            body.addView(t, kit.lp(-1, -2));
            AlertDialog d = kit.dialog("⬆ بررسی بروزرسانی", body, true);
            d.show();
            Context app = a.getApplicationContext();
            new Thread(() -> {
                Info info = fetch(app);
                run(a, () -> {
                    try {
                        d.dismiss();
                    } catch (Exception ignored) { }
                    if (finished(a)) return;
                    if (info == null) {
                        toast(a, "بررسی ممکن نشد؛ کمی بعد دوباره تلاش کنید");
                        return;
                    }
                    markChecked(app);
                    purgeStale(app, info.code);
                    if (info.code <= currentCode(app)) {
                        toast(a, "✦ شما آخرین نسخه را دارید");
                        return;
                    }
                    showUpdateDialog(a, info);
                });
            }).start();
        } catch (Exception e) {
            toast(a, "بررسی ممکن نشد");
        }
    }

    /** Forward MainActivity.onActivityResult here. */
    public static void onUnknownSourcesReturn(Activity a) {
        try {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                toast(a, "بدون این اجازه، نصب نسخه جدید ممکن نیست");
                return;
            }
            if (pendingApk != null && pendingApk.exists()) fireInstall(a, pendingApk);
            else toast(a, "فایل نصب پیدا نشد؛ دوباره تلاش کنید");
        } catch (Exception e) {
            toast(a, "نصب ممکن نشد؛ دوباره تلاش کنید");
        }
    }

    /** «آخرین بررسی نسخه جدید: امروز» for the Settings screen. */
    public static String lastCheckLine(Context c) {
        try {
            long last = prefs(c).getLong("last_check", 0);
            if (last <= 0) return "آخرین بررسی نسخه جدید: هنوز بررسی نشده";
            long days = (System.currentTimeMillis() - last) / 86400000L;
            String when = days <= 0 ? "امروز" : (days == 1 ? "دیروز"
                    : Money.fa(String.valueOf(days)) + " روز پیش");
            return "آخرین بررسی نسخه جدید: " + when;
        } catch (Exception e) {
            return "";
        }
    }

    // ================= descriptor =================

    /** Download + fully verify version.json. Null = nothing to do. Never throws. */
    private static Info fetch(Context c) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(UpdateConfig.META_URL).openConnection();
            con.setConnectTimeout(UpdateConfig.CONNECT_TIMEOUT_MS);
            con.setReadTimeout(UpdateConfig.READ_TIMEOUT_MS);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "MeelanoManager/" + currentCode(c));
            con.connect();
            if (con.getResponseCode() != 200) return null; // 404 = no published release yet
            String json = readUpTo(con, 65536);
            if (json == null) return null;
            JSONObject m = new JSONObject(json).optJSONObject("manager");
            if (m == null) return null;
            Info i = new Info();
            i.code = m.optInt("code", 0);
            i.name = m.optString("name", "").trim();
            i.apk = m.optString("apk", "").trim();
            i.size = m.optLong("size", 0);
            i.sha256 = m.optString("sha256", "").trim();
            i.url = m.optString("url", "").trim();
            i.msg = m.optString("msg", "").trim();
            if (i.code <= 0 || i.name.isEmpty() || i.apk.isEmpty() || i.url.isEmpty()) return null;
            if (!i.url.startsWith("https://")) return null;
            String host;
            try {
                host = new URL(i.url).getHost().toLowerCase(Locale.US);
            } catch (Exception e) {
                return null;
            }
            if (!host.equals("github.com") && !host.endsWith(".github.com")
                    && !host.equals("objects.githubusercontent")
                    && !host.equals("release-assets.githubusercontent")) return null;
            if (i.size < 0 || i.size > UpdateConfig.MAX_APK_BYTES) return null;
            if (!License.updateVerify(i.code, i.sha256, m.optString("mac", ""))) return null;
            return i;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (con != null) con.disconnect();
            } catch (Exception ignored) { }
        }
    }

    private static String readUpTo(HttpURLConnection con, int max) {
        try {
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int total = 0, n;
            while ((n = in.read(buf)) >= 0) {
                total += Math.max(0, n);
                if (total > max) return null;
                if (n > 0) out.write(buf, 0, n);
            }
            try {
                in.close();
            } catch (Exception ignored) { }
            String s = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            return s.trim().isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    // ================= offer dialog =================

    private static void showUpdateDialog(Activity a, Info info) {
        try {
            if (finished(a)) return;
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            body.addView(kit.hero("⬆", "نسخه " + Money.fa(info.name) + " منتشر شد",
                    info.msg.isEmpty()
                            ? "پیشنهاد می‌کنیم همین حالا به‌روزرسانی کنید؛ دانلود امن و نصب، خودکار انجام می‌شود."
                            : info.msg,
                    Theme.GOLD), kit.lp(-1, -2));
            body.addView(kit.gap(10));
            LinearLayout card = kit.card();
            card.addView(kit.kv("نسخه فعلی", Money.fa(currentName(a)), Theme.TEXT), kit.lp(-1, -2));
            card.addView(kit.kv("نسخه جدید", Money.fa(info.name), Theme.GOLD_SOFT), kit.lp(-1, -2));
            card.addView(kit.kv("حجم دانلود", faSize(info.size), Theme.TEXT), kit.lp(-1, -2));
            body.addView(card, kit.lp(-1, -2));
            body.addView(kit.gap(10));
            final AlertDialog[] box = new AlertDialog[1];
            body.addView(kit.btnGold("⬆ دریافت و نصب نسخه جدید", v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
                startDownload(a, info);
            }), kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.btnGhost("بعداً", Theme.MUTED, v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
            }), kit.lp(-1, -2));
            AlertDialog d = kit.dialog("⬆ بروزرسانی میلانو", kit.scrollWrap(body, 440), true);
            box[0] = d;
            d.setOnDismissListener(dd -> recordDismiss(a, info.code));
            d.show();
        } catch (Exception ignored) { }
    }

    // ================= download =================

    private static void startDownload(Activity a, Info info) {
        Kit kit;
        AlertDialog d;
        TextView stage, pct;
        ProgressBar bar;
        try {
            kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            stage = kit.text("آماده‌سازی…", 13.5f, Theme.TEXT, true);
            stage.setGravity(Gravity.CENTER);
            body.addView(stage, kit.lp(-1, -2));
            body.addView(kit.gap(8));
            bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            try {
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.GOLD));
            } catch (Exception ignored) { }
            body.addView(bar, new LinearLayout.LayoutParams(-1, Theme.dp(10)));
            pct = kit.text("", 12f, Theme.MUTED, false);
            pct.setGravity(Gravity.CENTER);
            body.addView(pct, kit.lp(-1, -2));
            body.addView(kit.gap(6));
            body.addView(kit.btnGhost("انصراف", Theme.DANGER, v -> cancelled = true), kit.lp(-1, -2));
            d = kit.dialog("⬆ دریافت نسخه " + Money.fa(info.name), body, false);
            d.show();
        } catch (Exception e) {
            toast(a, "نمایش پیشرفت ممکن نشد");
            return;
        }
        cancelled = false;
        Context app = a.getApplicationContext();
        final AlertDialog fd = d;
        final TextView fStage = stage, fPct = pct;
        final ProgressBar fBar = bar;
        new Thread(() -> downloadRun(a, app, info, fd, fStage, fBar, fPct)).start();
    }

    /** Background worker: download (×3 retries) → verify → install. */
    private static void downloadRun(Activity a, Context app, Info info, AlertDialog d,
                                    TextView stage, ProgressBar bar, TextView pct) {
        File dir = new File(app.getCacheDir(), "updates");
        try {
            dir.mkdirs();
        } catch (Exception ignored) { }
        File target = new File(dir, "update-" + info.code + ".apk");
        // A previously downloaded file that still verifies is reused as-is.
        boolean have = target.exists() && target.length() > 1024
                && info.sha256.equalsIgnoreCase(sha256Of(target));
        if (!have) {
            boolean ok = false;
            for (int attempt = 1; attempt <= 3 && !ok; attempt++) {
                if (cancelled) break;
                final int at = attempt;
                run(a, () -> {
                    try {
                        stage.setText(at > 1
                                ? ("تلاش مجدد (" + Money.fa(String.valueOf(at)) + " از ۳)…")
                                : "در حال دریافت نسخه جدید…");
                    } catch (Exception ignored) { }
                });
                ok = downloadFile(info.url, info.size, target, a, bar, pct);
            }
            if (cancelled) {
                closeQuiet(a, d);
                toast(app, "دریافت لغو شد");
                return;
            }
            if (!ok) {
                closeQuiet(a, d);
                toast(app, "دانلود کامل نشد؛ اینترنت را بررسی کنید و دوباره تلاش کنید");
                return;
            }
            setStage(a, stage, "بررسی صحت فایل…");
            if (!info.sha256.equalsIgnoreCase(sha256Of(target))) {
                try {
                    target.delete();
                } catch (Exception ignored) { }
                closeQuiet(a, d);
                toast(app, "فایل دریافتی خراب است؛ دوباره تلاش کنید");
                return;
            }
        } else {
            setStage(a, stage, "فایل آماده است؛ بررسی نهایی…");
        }
        int v = verifyApk(app, target, info);
        if (v == APK_SIGNER) {
            // Signer differs (e.g. first migration from a test install): guide, don't fail cryptically.
            closeQuiet(a, d);
            run(a, () -> signerDialog(a, info, target));
            return;
        }
        if (v != APK_OK) {
            try {
                target.delete();
            } catch (Exception ignored) { }
            closeQuiet(a, d);
            toast(app, "فایل نسخه جدید معتبر نیست؛ دوباره تلاش کنید");
            return;
        }
        closeQuiet(a, d);
        run(a, () -> beginInstall(a, app, target));
    }

    private static boolean downloadFile(String url, long expectSize, File target,
                                        Activity a, ProgressBar bar, TextView pct) {
        File part = new File(target.getAbsolutePath() + ".part");
        HttpURLConnection con = null;
        try {
            try {
                part.delete();
            } catch (Exception ignored) { }
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(UpdateConfig.CONNECT_TIMEOUT_MS);
            con.setReadTimeout(UpdateConfig.READ_TIMEOUT_MS);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", "MeelanoManager/" + currentCode(a));
            con.connect();
            if (con.getResponseCode() != 200) return false;
            long total = con.getContentLength();
            if (expectSize > 0 && total > 0 && total != expectSize) return false;
            if (total > UpdateConfig.MAX_APK_BYTES) return false;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                in = con.getInputStream();
                out = new FileOutputStream(part);
                byte[] buf = new byte[65536];
                long done = 0;
                int n;
                long lastUi = 0;
                final long fTotal = total > 0 ? total : expectSize;
                while ((n = in.read(buf)) >= 0) {
                    if (cancelled) return false;
                    if (n > 0) {
                        out.write(buf, 0, n);
                        done += n;
                    }
                    if (done > UpdateConfig.MAX_APK_BYTES) return false;
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 200) {
                        lastUi = now;
                        final long fDone = done;
                        run(a, () -> {
                            try {
                                if (fTotal > 0) {
                                    int p = (int) Math.min(100, fDone * 100 / fTotal);
                                    try {
                                        bar.setIndeterminate(false);
                                    } catch (Exception ignored) { }
                                    bar.setProgress(p);
                                    pct.setText(Money.fa(String.valueOf(p)) + "٪ • "
                                            + faSize(fDone) + " از " + faSize(fTotal));
                                } else {
                                    try {
                                        bar.setIndeterminate(true);
                                    } catch (Exception ignored) { }
                                    pct.setText(faSize(fDone));
                                }
                            } catch (Exception ignored) { }
                        });
                    }
                }
                try {
                    out.flush();
                } catch (Exception ignored) { }
                if (expectSize > 0 && done != expectSize) return false;
                if (done < 1024) return false;
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) { }
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) { }
                if (cancelled) {
                    try {
                        part.delete();
                    } catch (Exception ignored) { }
                }
            }
            if (cancelled) return false;
            try {
                target.delete();
            } catch (Exception ignored) { }
            if (!part.renameTo(target)) {
                if (!copy(part, target)) return false;
                try {
                    part.delete();
                } catch (Exception ignored) { }
            }
            return target.exists() && target.length() > 1024;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (con != null) con.disconnect();
            } catch (Exception ignored) { }
            try {
                if (part.exists() && (!target.exists() || target.length() <= 1024)) part.delete();
            } catch (Exception ignored) { }
        }
    }

    // ================= APK verification =================

    private static final int APK_OK = 0;
    private static final int APK_BAD = 1;
    private static final int APK_SIGNER = 2;

    /** Package + version + signer of the downloaded APK. Never throws. */
    private static int verifyApk(Context app, File apk, Info info) {
        try {
            PackageManager pm = app.getPackageManager();
            @SuppressWarnings("deprecation")
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            if (pi == null || pi.packageName == null) return APK_BAD;
            if (!pi.packageName.equals(app.getPackageName())) return APK_BAD;
            int ac = archiveCode(pi);
            if (ac != info.code) return APK_BAD;
            if (!signerMatches(app, pm, pi)) return APK_SIGNER;
            return APK_OK;
        } catch (Exception e) {
            return APK_BAD;
        }
    }

    @SuppressWarnings("deprecation")
    private static int archiveCode(PackageInfo pi) {
        try {
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return -1;
        }
    }

    /** The downloaded APK must carry the same signer or Android would refuse it. */
    private static boolean signerMatches(Context app, PackageManager pm, PackageInfo archive) {
        try {
            byte[] a = firstSigner(app, pm, null);
            byte[] b = firstSigner(app, pm, archive);
            if (a == null || b == null || a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static byte[] firstSigner(Context app, PackageManager pm, PackageInfo archive) {
        try {
            Signature[] sigs = null;
            if (archive != null) {
                if (Build.VERSION.SDK_INT >= 28 && archive.signingInfo != null)
                    sigs = archive.signingInfo.getApkContentsSigners();
                else sigs = archive.signatures;
            } else if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo cur = pm.getPackageInfo(app.getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (cur != null && cur.signingInfo != null) sigs = cur.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo cur = pm.getPackageInfo(app.getPackageName(), PackageManager.GET_SIGNATURES);
                if (cur != null) sigs = cur.signatures;
            }
            if (sigs == null || sigs.length == 0 || sigs[0] == null) return null;
            return sigs[0].toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // ================= install =================

    private static void beginInstall(Activity a, Context app, File apk) {
        try {
            if (finished(a)) {
                toast(app, "فایل آماده است؛ از تنظیمات » بررسی بروزرسانی ادامه دهید");
                return;
            }
            File pub = new File(ShareProvider.shareDir(app), "meelano-update.apk");
            if (!copy(apk, pub)) {
                toast(a, "آماده‌سازی نصب ممکن نشد؛ دوباره تلاش کنید");
                return;
            }
            pendingApk = pub;
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                Kit kit = new Kit(a);
                LinearLayout body = kit.v();
                body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
                body.addView(kit.text(
                        "برای نصب نسخه جدید، در صفحه بعد گزینه «اجازه نصب از این منبع» را برای «میلانو» روشن کنید و برگردید؛ نصب خودکار ادامه پیدا می‌کند.",
                        13.5f, Theme.TEXT, false), kit.lp(-1, -2));
                body.addView(kit.gap(10));
                final AlertDialog[] box = new AlertDialog[1];
                body.addView(kit.btnGold("باز کردن تنظیمات", v -> {
                    try {
                        if (box[0] != null) box[0].dismiss();
                    } catch (Exception ignored) { }
                    openUnknownSettings(a);
                }), kit.lp(-1, -2));
                body.addView(kit.gap(8));
                body.addView(kit.btnGhost("انصراف", Theme.MUTED, v -> {
                    try {
                        if (box[0] != null) box[0].dismiss();
                    } catch (Exception ignored) { }
                }), kit.lp(-1, -2));
                AlertDialog d = kit.dialog("⬆ یک اجازه لازم است", body, true);
                box[0] = d;
                d.show();
                return;
            }
            fireInstall(a, pub);
        } catch (Exception e) {
            toast(a, "نصب ممکن نشد؛ دوباره تلاش کنید");
        }
    }

    private static void openUnknownSettings(Activity a) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivityForResult(i, REQ_UNKNOWN);
        } catch (Exception e) {
            toast(a, "باز کردن تنظیمات ممکن نشد");
        }
    }

    private static void fireInstall(Context c, File apk) {
        try {
            ShareProvider.install(c, apk);
            toast(c, "نصب شروع شد؛ پس از پایان، «باز کردن» را بزنید");
        } catch (Exception e) {
            toast(c, "اجرای نصب ممکن نشد؛ دوباره تلاش کنید");
        }
    }

    /**
     * First-migration guide: the installed app was signed with a test key,
     * so Android cannot replace it directly. The user shares the new APK to
     * themselves first, then reinstalls exactly once — every later update
     * is a smooth one-tap install.
     */
    private static void signerDialog(Activity a, Info info, File apk) {
        try {
            if (finished(a)) return;
            Kit kit = new Kit(a);
            LinearLayout body = kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
            body.addView(kit.text(
                    "نسخه " + Money.fa(info.name) + " با امضای رسمی و امن میلانو منتشر شده، ولی نصب فعلی شما آزمایشی است و اندروید اجازه جایگزینی مستقیم نمی‌دهد. فقط همین یک‌بار این سه قدم را انجام دهید؛ از این به بعد همه بروزرسانی‌ها با یک لمس نصب می‌شوند:",
                    13.5f, Theme.TEXT, false), kit.lp(-1, -2));
            body.addView(kit.gap(8));
            LinearLayout card = kit.card(Theme.GOLD);
            card.addView(kit.text("۱) با دکمه زیر، فایل نصب جدید را برای خودتان بفرستید (پیام‌رسان، بلوتوث یا هر راه دیگر).",
                    13f, Theme.TEXT, false), kit.lp(-1, -2));
            card.addView(kit.text("۲) همین نسخه فعلی را از گوشی حذف (Uninstall) کنید.",
                    13f, Theme.TEXT, false), kit.lp(-1, -2));
            card.addView(kit.text("۳) فایل را نصب کنید و با همان کد فعال‌سازی قبلی (از پیامک فروشنده) وارد شوید؛ کد شما مخصوص همین گوشی است و همچنان معتبر می‌ماند.",
                    13f, Theme.TEXT, false), kit.lp(-1, -2));
            body.addView(card, kit.lp(-1, -2));
            body.addView(kit.gap(10));
            final AlertDialog[] box = new AlertDialog[1];
            body.addView(kit.btnGold("اشتراک فایل نصب جدید", v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
                shareApk(a, apk, info);
            }), kit.lp(-1, -2));
            body.addView(kit.gap(8));
            body.addView(kit.btnGhost("فهمیدم", Theme.MUTED, v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception ignored) { }
            }), kit.lp(-1, -2));
            AlertDialog d = kit.dialog("⬆ نصب یک‌باره نسخه رسمی", kit.scrollWrap(body, 440), true);
            box[0] = d;
            d.show();
        } catch (Exception ignored) { }
    }

    private static void shareApk(Activity a, File apk, Info info) {
        try {
            File pub = new File(ShareProvider.shareDir(a), "meelano-" + info.name + ".apk");
            if (!copy(apk, pub)) {
                toast(a, "اشتراک ممکن نشد؛ دوباره تلاش کنید");
                return;
            }
            ShareProvider.share(a, pub, "application/vnd.android.package-archive",
                    "فایل نصب میلانو " + info.name);
        } catch (Exception e) {
            toast(a, "اشتراک ممکن نشد؛ دوباره تلاش کنید");
        }
    }

    // ================= helpers =================

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    private static void markChecked(Context c) {
        try {
            prefs(c).edit().putLong("last_check", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    private static void recordDismiss(Context c, int code) {
        try {
            prefs(c).edit().putInt("dismissed_code", code)
                    .putLong("dismissed_at", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    /** Drop downloaded files from older checks; keep the current one for reuse. */
    private static void purgeStale(Context c, int keepCode) {
        try {
            File dir = new File(c.getCacheDir(), "updates");
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                String n = f.getName();
                if ((n.startsWith("update-") && (n.endsWith(".apk") || n.endsWith(".part")))
                        && !n.equals("update-" + keepCode + ".apk")) {
                    try {
                        f.delete();
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
    }

    private static void run(Activity a, Runnable r) {
        try {
            a.runOnUiThread(r);
        } catch (Exception ignored) { }
    }

    private static boolean finished(Activity a) {
        try {
            return a == null || a.isFinishing() || a.isDestroyed();
        } catch (Exception e) {
            return true;
        }
    }

    private static void closeQuiet(Activity a, AlertDialog d) {
        run(a, () -> {
            try {
                d.dismiss();
            } catch (Exception ignored) { }
        });
    }

    private static void setStage(Activity a, TextView stage, String s) {
        run(a, () -> {
            try {
                stage.setText(s);
            } catch (Exception ignored) { }
        });
    }

    private static void toast(Context c, String m) {
        try {
            final Context app = c.getApplicationContext();
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(app, m, Toast.LENGTH_LONG).show();
                } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    @SuppressWarnings("deprecation")
    private static int currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi == null) return 0;
            if (Build.VERSION.SDK_INT >= 28) return (int) pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String currentName(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null || v.isEmpty() ? "—" : v;
        } catch (Exception e) {
            return "—";
        }
    }

    private static boolean isDebug(Context c) {
        try {
            return c.getPackageName().endsWith(".debug");
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public static String faSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1048576) return Money.fa(String.valueOf(bytes / 1024)) + " کیلوبایت";
        String s = String.format(Locale.US, "%.1f", bytes / 1048576.0);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return Money.fa(s).replace('.', '٫') + " مگابایت";
    }

    private static String sha256Of(File f) {
        FileInputStream in = null;
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            in = new FileInputStream(f);
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
            byte[] h = d.digest();
            StringBuilder b = new StringBuilder(h.length * 2);
            for (byte x : h) b.append(String.format(Locale.US, "%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "";
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
        }
    }

    private static boolean copy(File from, File to) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
            try {
                if (out != null) out.close();
            } catch (Exception ignored) { }
        }
    }
}
