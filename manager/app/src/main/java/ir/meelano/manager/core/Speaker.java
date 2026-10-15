package ir.meelano.manager.core;

import android.content.Intent;
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
 * Shared Persian text-to-speech. Checks that the Persian voice data is really
 * installed, offers to install it when it is not, and reports when an utterance
 * finishes so live conversation can start listening again.
 */
public final class Speaker {
    private Speaker() { }

    private static final int UNINIT = 0, READY = 1, NO_PERSIAN = 2, FAILED = 3;
    private static TextToSpeech tts;
    private static int state = UNINIT;
    private static boolean askedInstall = false;
    private static int seq = 0;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> WAITING = new HashMap<>();
    private static final List<Runnable> AFTER_INIT = new ArrayList<>();

    /** True when a Persian voice can be used right now. */
    public static boolean persianReady() {
        return state == READY;
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
                if (state == NO_PERSIAN) offerInstall(a);
                else a.kit.toast("سرویس صدای گوشی در دسترس نیست");
            }
        });
    }

    /** Opens the system screen that installs the Persian voice data. */
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

    /** Explains the missing voice once per session; afterwards only a short toast. */
    public static void offerInstall(final MainActivity a) {
        MAIN.post(() -> {
            if (askedInstall) {
                a.kit.toast("صدای فارسی روی گوشی نصب نیست؛ از تنظیمات «خروجی صدا» نصب کنید");
                return;
            }
            askedInstall = true;
            LinearLayout body = a.kit.v();
            body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(12));
            body.addView(a.kit.text("برای خواندن متن فارسی، بستهٔ صدای فارسی باید روی گوشی نصب باشد. "
                    + "با دکمهٔ زیر آن را نصب کنید (معمولاً از Google Text-to-Speech). "
                    + "بعد از نصب دوباره «🔊 بخوان» را بزنید.", 13f, Theme.TEXT, false), a.kit.lp(-1, -2));
            body.addView(a.kit.gap(12));
            body.addView(a.kit.btnGold("نصب صدای فارسی", v -> openInstall(a)), a.kit.lp(-1, -2));
            a.kit.dialog("صدای فارسی نصب نیست", body, true).show();
        });
    }

    // ---------------- internals ----------------

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

    /** Re-checks after the user installed the voice, without restarting the engine. */
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
