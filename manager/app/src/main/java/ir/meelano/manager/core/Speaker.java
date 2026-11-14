package ir.meelano.manager.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared Persian text-to-speech for the whole app.
 *
 * Google's Text-to-Speech does not offer Persian on many phones, so the speaker probes every
 * installed TTS engine and uses the first one that really speaks Persian. When none does, it
 * guides the user to install eSpeak NG (which supports Persian). Android requires the user to
 * confirm every install; the app opens the store page and re-checks when the user returns.
 */
public final class Speaker {
    private Speaker() { }

    /** eSpeak NG: open-source engine with Persian (fa). Play and F-Droid. */
    public static final String ESPEAK_PKG = "com.reecedunn.espeak";
    public static final String ESPEAK_FDROID = "https://f-droid.org/en/packages/com.reecedunn.espeak/";
    /** Engines we prefer when several are installed (they support Persian). */
    private static final List<String> PREFERRED = Arrays.asList(ESPEAK_PKG, "com.redzoc.ramees.tts.espeak",
            "com.googlecode.eyesfree.espeak");
    private static final long PROBE_TIMEOUT_MS = 4000;

    private static final int UNINIT = 0, PROBING = 1, READY = 2, NO_PERSIAN = 3;
    private static TextToSpeech tts;
    private static String engineName = "";
    private static int state = UNINIT;
    private static boolean setupShown = false;
    private static android.app.AlertDialog setupDialog;
    private static int seq = 0;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Runnable> WAITING = new HashMap<>();
    private static final List<Runnable> AFTER_PROBE = new ArrayList<>();

    /** True when a Persian voice can be used right now. */
    public static boolean persianReady() {
        return state == READY;
    }

    /** Name of the engine that speaks Persian (empty when none). */
    public static String engine() {
        return engineName;
    }

    /** One-line Persian status for the Settings voice row. */
    public static String voiceStatusFa(Context c) {
        try {
            if (OfflineVoice.installed(c)) return "🔊 صدای فارسی: آماده (موتور آفلاین برنامه)";
        } catch (Exception e) {
            Diag.ignored("Speaker:status", e);
        }
        if (state == READY) return "🔊 صدای فارسی: آماده (" + engineName + ")";
        if (state == NO_PERSIAN) return "🔇 صدای فارسی: راه‌اندازی نشده · راهنما";
        return "🔊 صدای فارسی: در حال بررسی…";
    }

    /**
     * Called on every resume. The first time the app opens without a Persian voice it shows the
     * setup step. When the user returns from the store, it probes the engines again and closes
     * the step by itself once Persian works.
     */
    public static void onResume(final MainActivity a) {
        if (OfflineVoice.installed(a)) { // built-in Persian voice is ready: no setup step
            clearSetupFlags(a);
            return;
        }
        if (state == UNINIT) {
            probe(a, () -> {
                if (state == NO_PERSIAN) autoSetupOnce(a);
            });
            return;
        }
        if (state == NO_PERSIAN) {
            probe(a, () -> {
                if (state == READY) {
                    closeSetup();
                    clearSetupFlags(a);
                    a.kit.toast("صدای فارسی آماده است (" + engineName + ")");
                } else if (setupDialog != null && setupDialog.isShowing()) {
                    return; // the user is already looking at the setup step
                } else {
                    autoSetupOnce(a);
                }
            });
        }
    }

    /**
     * The setup step pops up by itself at most once per install. A user who chose «continue
     * without sound» is never nagged again on resumes; the step stays reachable from Settings.
     */
    private static void autoSetupOnce(MainActivity a) {
        if (skipped(a) || prompted(a)) return;
        setPrompted(a, true);
        showSetup(a);
    }

    /** Opens the setup step from Settings (explicit user action, ignores the skip flag). */
    public static void openSetup(MainActivity a) {
        if (OfflineVoice.installed(a)) {
            a.kit.toast("صدای فارسی آماده است (موتور آفلاین برنامه)");
            return;
        }
        if (state == READY) {
            a.kit.toast("صدای فارسی آماده است (" + engineName + ")");
            return;
        }
        showSetup(a);
    }

    private static android.content.SharedPreferences speakerPrefs(Context c) {
        return c.getSharedPreferences("meelano_speaker", Context.MODE_PRIVATE);
    }

    private static boolean skipped(Context c) {
        try {
            return speakerPrefs(c).getBoolean("setup_skipped", false);
        } catch (Exception e) {
            Diag.ignored("Speaker:skipped", e);
            return false;
        }
    }

    private static void setSkipped(Context c, boolean value) {
        try {
            speakerPrefs(c).edit().putBoolean("setup_skipped", value).apply();
        } catch (Exception e) {
            Diag.ignored("Speaker:setSkipped", e);
        }
    }

    private static boolean prompted(Context c) {
        try {
            return speakerPrefs(c).getBoolean("setup_prompted", false);
        } catch (Exception e) {
            Diag.ignored("Speaker:prompted", e);
            return false;
        }
    }

    private static void setPrompted(Context c, boolean value) {
        try {
            speakerPrefs(c).edit().putBoolean("setup_prompted", value).apply();
        } catch (Exception e) {
            Diag.ignored("Speaker:setPrompted", e);
        }
    }

    private static void clearSetupFlags(Context c) {
        try {
            speakerPrefs(c).edit().remove("setup_skipped").remove("setup_prompted").apply();
        } catch (Exception e) {
            Diag.ignored("Speaker:clearFlags", e);
        }
    }

    /**
     * Speaks the text (symbols removed). {@code onDone} runs on the main thread when the speech
     * ends, or at once when nothing could be spoken.
     */
    public static void speak(final MainActivity a, final String text, final Runnable onDone) {
        final String clean = clean(text);
        if (clean.isEmpty()) {
            post(onDone);
            return;
        }
        speaking = true;
        final Runnable finished = () -> {
            speaking = false;
            if (onDone != null) onDone.run();
        };
        // Speech stays on-device; assistant replies are never forwarded to a cloud TTS provider.
        speakLocal(a, clean, finished);
    }

    /** True while an answer is being read aloud (or prepared for reading). */
    private static volatile boolean speaking = false;

    public static boolean isSpeaking() {
        return speaking;
    }

    /** Stops whatever is being read, at once. */
    public static void stopAll() {
        speaking = false;
        stopPlayer();
    }

    private static void speakLocal(final MainActivity a, final String clean, final Runnable onDone) {
        stopPlayer();
        if (OfflineVoice.installed(a)) {
            OfflineVoice.speak(a, clean, onDone);
            return;
        }
        whenProbed(a, () -> {
            if (state == READY && tts != null) {
                String id = "m" + (++seq);
                if (onDone != null) {
                    synchronized (WAITING) {
                        WAITING.put(id, onDone);
                    }
                }
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id);
            } else {
                post(onDone);
                a.kit.toast("صدای فارسی آماده نیست؛ از تنظیمات → صدای فارسی می‌توانید موتور را نصب کنید");
            }
        });
    }

    /** Stops both supported on-device Persian speech engines immediately. */
    public static void stopPlayer() {
        OfflineVoice.stop();
        try {
            if (tts != null) tts.stop();
        } catch (Exception e) {
            Diag.ignored("Speaker:stop", e);
        }
    }

    // ================= setup step =================

    private static void showSetup(final MainActivity a) {
        if (setupDialog != null && setupDialog.isShowing()) return;
        setupShown = true;

        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(12));
        body.addView(a.kit.text("موتور صدای این گوشی زبان فارسی ندارد. برای خواندن متن فارسی و گفتگوی زنده، "
                + "موتور رایگان eSpeak NG را نصب کنید. بعد از نصب و برگشت به برنامه، خودکار بررسی می‌شود. "
                + "اگر «بدون صدا» را انتخاب کنید این راهنما دیگر خودکار باز نمی‌شود و از تنظیمات → صدای فارسی در دسترس است.",
                13f, Theme.TEXT, false), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(12));
        body.addView(a.kit.btnGold("۱. نصب eSpeak NG از فروشگاه", v -> openStore(a)), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("صفحهٔ F-Droid (جایگزین)", Theme.GOLD, v -> openUrl(a, ESPEAK_FDROID)),
                a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("⇅ بررسی مجدد", Theme.GOLD, v -> probe(a, () -> {
            if (state == READY) {
                closeSetup();
                a.kit.toast("صدای فارسی آماده است (" + engineName + ")");
            } else {
                a.kit.toast("هنوز موتور فارسی پیدا نشد");
            }
        })), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("فعلاً بدون صدا ادامه بده", Theme.MUTED, v -> {
            setSkipped(a, true);
            closeSetup();
            a.kit.toast("باشه؛ این راهنما دیگر خودکار باز نمی‌شود (تنظیمات → صدای فارسی)");
        }), a.kit.lp(-1, -2));

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

    /** Opens the eSpeak NG page in the store (the user taps install). */
    public static void openStore(MainActivity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + ESPEAK_PKG)));
        } catch (Exception e) {
            openUrl(a, "https://play.google.com/store/apps/details?id=" + ESPEAK_PKG);
        }
    }

    private static void openUrl(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            a.kit.toast("مرورگر یا فروشگاه پیدا نشد");
        }
    }

    // ================= engine probing =================

    private static void whenProbed(final MainActivity a, final Runnable ready) {
        if (state == READY || state == NO_PERSIAN) {
            ready.run();
            return;
        }
        synchronized (AFTER_PROBE) {
            AFTER_PROBE.add(ready);
        }
        if (state == UNINIT) probe(a, null);
    }

    /** Tries engines one by one until one speaks Persian. Calls {@code done} on the main thread. */
    private static void probe(final MainActivity a, final Runnable done) {
        if (done != null) {
            synchronized (AFTER_PROBE) {
                AFTER_PROBE.add(done);
            }
        }
        if (state == PROBING) return;
        state = PROBING;
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception e) {
                Diag.ignored("Speaker:shutdown", e);
            }
            tts = null;
        }
        List<String> candidates = candidates(a);
        tryEngine(a, candidates, 0);
    }

    /** null = system default engine first, then preferred installed engines, then the rest. */
    private static List<String> candidates(MainActivity a) {
        List<String> out = new ArrayList<>();
        out.add(null);
        List<String> installed = new ArrayList<>();
        try {
            List<ResolveInfo> services = a.getPackageManager().queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
            for (ResolveInfo ri : services) {
                if (ri.serviceInfo != null && !installed.contains(ri.serviceInfo.packageName)) {
                    installed.add(ri.serviceInfo.packageName);
                }
            }
        } catch (Exception e) {
            Diag.ignored("Speaker:services", e);
        }
        for (String p : PREFERRED) if (installed.contains(p)) out.add(p);
        for (String p : installed) if (!out.contains(p)) out.add(p);
        return out;
    }

    private static void tryEngine(final MainActivity a, final List<String> candidates, final int idx) {
        if (idx >= candidates.size()) {
            finishProbe(NO_PERSIAN, null, "");
            return;
        }
        final String pkg = candidates.get(idx);
        final boolean[] settled = {false};
        final TextToSpeech[] holder = new TextToSpeech[1];
        final Runnable next = () -> {
            if (settled[0]) return;
            settled[0] = true;
            if (holder[0] != null) {
                try {
                    holder[0].shutdown();
                } catch (Exception e) {
                    Diag.ignored("Speaker:drop", e);
                }
            }
            tryEngine(a, candidates, idx + 1);
        };
        MAIN.postDelayed(() -> {
            if (!settled[0]) next.run();
        }, PROBE_TIMEOUT_MS);
        try {
            TextToSpeech.OnInitListener listener = status -> {
                if (settled[0]) return;
                if (status == TextToSpeech.SUCCESS && holder[0] != null && setPersian(holder[0])) {
                    settled[0] = true;
                    holder[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { }
                        @Override public void onDone(String id) { fire(id); }
                        @Override public void onError(String id) { fire(id); }
                        @Override public void onError(String id, int code) { fire(id); }
                    });
                    String name = pkg == null ? "موتور پیش‌فرض" : pkg;
                    finishProbe(READY, holder[0], name);
                } else {
                    next.run();
                }
            };
            holder[0] = pkg == null
                    ? new TextToSpeech(a.getApplicationContext(), listener)
                    : new TextToSpeech(a.getApplicationContext(), listener, pkg);
        } catch (Exception e) {
            Diag.ignored("Speaker:engine", e);
            next.run();
        }
    }

    private static void finishProbe(int result, TextToSpeech engine, String name) {
        tts = engine;
        engineName = name;
        state = result;
        List<Runnable> run;
        synchronized (AFTER_PROBE) {
            run = new ArrayList<>(AFTER_PROBE);
            AFTER_PROBE.clear();
        }
        for (Runnable r : run) MAIN.post(r);
    }

    private static boolean setPersian(TextToSpeech t) {
        try {
            int r = t.setLanguage(new Locale("fa", "IR"));
            if (r < TextToSpeech.LANG_AVAILABLE) r = t.setLanguage(new Locale("fa"));
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
