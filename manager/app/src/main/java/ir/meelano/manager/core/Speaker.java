package ir.meelano.manager.core;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared Persian text-to-speech for the whole app.
 *
 * Android does not let an app install system packages silently: the user confirms each
 * install. This class does everything else automatically: it detects a missing Persian
 * voice, opens the Google Text-to-Speech install page, asks the system to download the
 * Persian voice data, and re-checks when the user comes back, then continues.
 */
public final class Speaker {
    private Speaker() { }

    static final String GOOGLE_TTS = "com.google.android.tts";

    private static final int UNINIT = 0, READY = 1, NO_PERSIAN = 2, FAILED = 3;
    private static TextToSpeech tts;
    private static int state = UNINIT;
    private static boolean setupShown = false;
    private static android.app.AlertDialog setupDialog;
    private static int seq = 0;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> WAITING = new HashMap<>();
    private static final List<Runnable> AFTER_INIT = new ArrayList<>();

    /** True when a Persian voice can be used right now. */
    public static boolean persianReady() {
        return state == READY;
    }

    /**
     * Called on every resume. The first time the app opens without a Persian voice it shows
     * the setup step (blocking until the user continues). When the user returns from the
     * system install screens, it re-checks and closes the step by itself.
     */
    public static void onResume(final MainActivity a) {
        if (state == UNINIT) {
            ensure(a, () -> {
                if (state == NO_PERSIAN) showSetup(a);
            });
            return;
        }
        if (state == NO_PERSIAN) {
            recheck();
            if (state == READY) {
                closeSetup();
                a.kit.toast("صدای فارسی آماده است");
            } else if (setupDialog == null || !setupDialog.isShowing()) {
                showSetup(a);
            }
        }
    }

    /**
     * Speaks the text (symbols removed). {@code onDone} runs on the main thread when the
     * speech ends, or at once when nothing could be spoken.
     */
    public static void speak(final MainActivity a, final String text, final Runnable onDone) {
        final String clean = clean(text);
        if (clean.isEmpty()) {
            post(onDone);
            return;
        }
        ensure(a, () -> {
            if (state == NO_PERSIAN) recheck();
            if (state == READY) {
                String id = "m" + (++seq);
                if (onDone != null) {
                    synchronized (WAITING) {
                        WAITING.put(id, onDone);
                    }
                }
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id);
            } else {
                post(onDone);
                if (state == NO_PERSIAN && !setupShown) showSetup(a);
                else a.kit.toast("صدای فارسی روی گوشی نصب نیست؛ از «تنظیمات ← هوش مصنوعی» راه‌اندازی کنید");
            }
        });
    }

    // ================= setup step =================

    private static void showSetup(final MainActivity a) {
        if (setupDialog != null && setupDialog.isShowing()) return;
        setupShown = true;
        final boolean googleOk = googleTtsInstalled(a);

        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(12));
        body.addView(a.kit.text("برای خواندن متن فارسی و گفتگوی زنده، بستهٔ صدای فارسی لازم است. "
                + "برنامه مراحل را به ترتیب باز می‌کند؛ فقط کافی است هر بار «نصب» را تأیید کنید.",
                13f, Theme.TEXT, false), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(10));
        body.addView(a.kit.text((googleOk ? "✓ " : "۱. ") + "موتور صدای Google (Text-to-Speech)"
                + (googleOk ? " — نصب است" : " — نصب نیست"), 12.5f,
                googleOk ? Theme.SUCCESS : Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.text("۲. بستهٔ صدای فارسی", 12.5f, Theme.GOLD_SOFT, true), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(12));

        if (!googleOk) {
            body.addView(a.kit.btnGold("۱. باز کردن صفحهٔ نصب موتور صدای Google", v -> openPlay(a)),
                    a.kit.lp(-1, -2));
            body.addView(a.kit.gap(6));
        }
        body.addView(a.kit.btnGold("۲. دانلود و نصب صدای فارسی", v -> openInstall(a)), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("⟳ بررسی مجدد", Theme.TEAL, v -> {
            recheck();
            if (state == READY) {
                closeSetup();
                a.kit.toast("صدای فارسی آماده است");
            } else {
                a.kit.toast("هنوز صدای فارسی نصب نشده است");
            }
        }), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("فعلاً بدون صدا ادامه بده", Theme.MUTED, v -> closeSetup()), a.kit.lp(-1, -2));

        setupDialog = a.kit.dialog("راه‌اندازی صدای فارسی", body, false);
        setupDialog.setCancelable(false);
        setupDialog.show();
    }

    private static void closeSetup() {
        try {
            if (setupDialog != null) setupDialog.dismiss();
        } catch (Exception e) {
            Diag.ignored("Speaker:close", e);
        }
        setupDialog = null;
    }

    /** Opens the Google Text-to-Speech page in Play (the user taps install). */
    public static void openPlay(MainActivity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GOOGLE_TTS)));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_TTS)));
            } catch (Exception e2) {
                a.kit.toast("فروشگاه برنامه پیدا نشد");
            }
        }
    }

    /** Asks the system to download/install the Persian voice data (user confirms). */
    public static void openInstall(MainActivity a) {
        try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            if (i.resolveActivity(a.getPackageManager()) != null) {
                a.startActivity(i);
                return;
            }
        } catch (Exception e) {
            Diag.ignored("Speaker:install", e);
        }
        try {
            a.startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception e) {
            a.kit.toast("تنظیمات خروجی صدا روی این گوشی پیدا نشد");
        }
    }

    static boolean googleTtsInstalled(MainActivity a) {
        try {
            a.getPackageManager().getPackageInfo(GOOGLE_TTS, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ================= engine =================

    private static void ensure(final MainActivity a, final Runnable ready) {
        if (state != UNINIT) {
            ready.run();
            return;
        }
        synchronized (AFTER_INIT) {
            AFTER_INIT.add(ready);
            if (tts != null) return;
        }
        tts = new TextToSpeech(a.getApplicationContext(), status -> {
            int s = FAILED;
            if (status == TextToSpeech.SUCCESS) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { fire(id); }
                    @Override public void onError(String id) { fire(id); }
                    @Override public void onError(String id, int code) { fire(id); }
                });
                s = setPersian() ? READY : NO_PERSIAN;
            }
            state = s;
            List<Runnable> run;
            synchronized (AFTER_INIT) {
                run = new ArrayList<>(AFTER_INIT);
                AFTER_INIT.clear();
            }
            for (Runnable r : run) MAIN.post(r);
        });
    }

    /** Re-checks the installed voices without restarting the engine. */
    private static void recheck() {
        if (tts != null && setPersian()) state = READY;
    }

    private static boolean setPersian() {
        try {
            int r = tts.setLanguage(new Locale("fa", "IR"));
            if (r < TextToSpeech.LANG_AVAILABLE) r = tts.setLanguage(new Locale("fa"));
            return r >= TextToSpeech.LANG_AVAILABLE;
        } catch (Exception e) {
            Diag.ignored("Speaker:lang", e);
            return false;
        }
    }

    private static void fire(String id) {
        Runnable r;
        synchronized (WAITING) {
            r = WAITING.remove(id);
        }
        post(r);
    }

    private static void post(Runnable r) {
        if (r != null) MAIN.post(r);
    }

    /** Keeps letters, digits, punctuation and spaces; drops emoji and symbols. */
    static String clean(String text) {
        if (text == null) return "";
        return text.replace("•", "، ")
                .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Zs}\\u200c]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
