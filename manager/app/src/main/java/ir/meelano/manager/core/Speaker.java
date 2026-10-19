package ir.meelano.manager.core;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.ui.Theme;

import java.io.File;
import java.io.FileOutputStream;
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

    /**
     * Called on every resume. The first time the app opens without a Persian voice it shows the
     * setup step. When the user returns from the store, it probes the engines again and closes
     * the step by itself once Persian works.
     */
    public static void onResume(final MainActivity a) {
        if (OfflineVoice.installed(a)) return; // built-in Persian voice is ready: no setup step
        if (state == UNINIT) {
            probe(a, () -> {
                if (state == NO_PERSIAN) showSetup(a);
            });
            return;
        }
        if (state == NO_PERSIAN) {
            probe(a, () -> {
                if (state == READY) {
                    closeSetup();
                    a.kit.toast("صدای فارسی آماده است (" + engineName + ")");
                } else if (setupDialog == null || !setupDialog.isShowing()) {
                    showSetup(a);
                }
            });
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
        if (cloudOn(a)) {
            speakCloud(a, clean, onDone);
            return;
        }
        speakLocal(a, clean, onDone);
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
                if (!setupShown) showSetup(a);
                else a.kit.toast("صدای فارسی روی گوشی نیست؛ صدای طبیعی را در تنظیمات هوش مصنوعی فعال کنید");
            }
        });
    }

    // ================= natural (cloud) voice =================

    /** Cloud voice is used when the user enabled it and the AI key + consent are in place. */
    public static boolean cloudOn(MainActivity a) {
        android.content.SharedPreferences p = a.getSharedPreferences("meelano_ai", android.content.Context.MODE_PRIVATE);
        String provider = p.getString("provider", "gemini");
        if (!p.getBoolean("tts_cloud", true) || !p.getBoolean("consent", false)) return false;
        // Natural speech needs a speech-capable service with a key (active or not).
        for (String s : new String[]{provider, AiClient.GEMINI, AiClient.OPENAI}) {
            if (AiClient.hasSpeech(s) && !ir.meelano.manager.screens.AiSettings.keyFor(a, s).isEmpty()) return true;
        }
        return false;
    }

    private static MediaPlayer player;
    private static Runnable playerDone;

    private static void speakCloud(final MainActivity a, final String clean, final Runnable onDone) {
        final android.content.SharedPreferences p = a.getSharedPreferences("meelano_ai", android.content.Context.MODE_PRIVATE);
        // The voice comes from a service that has natural speech: the active one if it has speech,
        // otherwise the first speech-capable service with a saved key.
        String active = p.getString("provider", AiClient.GEMINI);
        String provider = null;
        for (String s : new String[]{active, AiClient.GEMINI, AiClient.OPENAI}) {
            if (AiClient.hasSpeech(s) && !ir.meelano.manager.screens.AiSettings.keyFor(a, s).isEmpty()) {
                provider = s;
                break;
            }
        }
        final String chosen = provider;
        if (chosen == null) {
            speakLocal(a, clean, onDone);
            return;
        }
        final String base = chosen.equals(active) ? p.getString("baseUrl", "") : "";
        final String model = chosen.equals(active) ? p.getString("tts_model", "") : "";
        // A saved voice name belongs to the active service only; other services use their default voice.
        final String voice = chosen.equals(active) ? p.getString("tts_voice", "") : "";
        final String key = ir.meelano.manager.screens.AiSettings.keyFor(a, chosen);
        stopPlayer();
        new Thread(() -> {
            try {
                AiClient.Audio au = AiClient.speech(chosen, base, model, voice, key, clean);
                File f = new File(a.getCacheDir(), "assistant_reply." + au.ext);
                try (FileOutputStream fo = new FileOutputStream(f)) {
                    fo.write(au.data);
                }
                a.runOnUiThread(() -> play(f, onDone));
            } catch (Exception e) {
                Diag.ignored("Speaker:cloud", e);
                a.runOnUiThread(() -> {
                    a.kit.toast("صدای طبیعی در دسترس نبود؛ صدای گوشی استفاده شد");
                    speakLocal(a, clean, onDone);
                });
            }
        }).start();
    }

    private static void play(File f, Runnable onDone) {
        stopPlayer();
        try {
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            playerDone = onDone;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnCompletionListener(m -> stopPlayer());
            mp.setOnErrorListener((m, w, e) -> {
                stopPlayer();
                return true;
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            Diag.ignored("Speaker:play", e);
            stopPlayer();
        }
    }

    /** Stops the natural voice and releases the player; the pending callback still runs. */
    public static void stopPlayer() {
        OfflineVoice.stop();
        Runnable done = playerDone;
        playerDone = null;
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception e) {
            Diag.ignored("Speaker:stop", e);
        }
        player = null;
        post(done);
    }

    // ================= setup step =================

    private static void showSetup(final MainActivity a) {
        if (setupDialog != null && setupDialog.isShowing()) return;
        setupShown = true;

        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(12));
        body.addView(a.kit.text("موتور صدای این گوشی زبان فارسی ندارد. برای خواندن متن فارسی و گفتگوی زنده، "
                + "موتور رایگان eSpeak NG را نصب کنید. بعد از نصب و برگشت به برنامه، خودکار بررسی می‌شود.",
                13f, Theme.TEXT, false), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(12));
        body.addView(a.kit.btnGold("۱. نصب eSpeak NG از فروشگاه", v -> openStore(a)), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("صفحهٔ F-Droid (جایگزین)", Theme.GOLD, v -> openUrl(a, ESPEAK_FDROID)),
                a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("⟳ بررسی مجدد", Theme.GOLD, v -> probe(a, () -> {
            if (state == READY) {
                closeSetup();
                a.kit.toast("صدای فارسی آماده است (" + engineName + ")");
            } else {
                a.kit.toast("هنوز موتور فارسی پیدا نشد");
            }
        })), a.kit.lp(-1, -2));
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
