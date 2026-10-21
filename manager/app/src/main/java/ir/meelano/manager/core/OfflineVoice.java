package ir.meelano.manager.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Built-in Persian voice: sherpa-onnx (offline Matcha, female «Khadijah» model) with the vocos vocoder.
 *
 * The engine ships inside the APK. The model (about 130 MB with its vocoder) is downloaded once, on the user's
 * request, from the sherpa-onnx GitHub release, and then works without internet.
 */
public final class OfflineVoice {
    private OfflineVoice() { }

    /**
     * Default Persian voice: the female «Khadijah» Matcha model from the sherpa-onnx release.
     * Matcha needs a separate vocoder file, which is fetched together with the model.
     */
    public static final String MODEL_NAME = "matcha-tts-fa_en-khadijah";
    public static final String VOCODER_NAME = "vocos-22khz-univ.onnx";
    public static final String VOCODER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/" + VOCODER_NAME;
    /** Earlier voices; still used on devices that downloaded them, until the default voice is installed. */
    static final String[] OLDER_NAMES = {
            "vits-piper-fa-haaniye_low",
            "vits-piper-fa_IR-reza_ibrahim-medium",
            "vits-piper-fa_IR-amir-medium"};
    public static final String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" + MODEL_NAME + ".tar.bz2";

    public interface Progress { void onProgress(int percent); }

    /** Called on the main thread; error is null on success. */
    public interface Done { void run(String error); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static OfflineTts engine;
    private static AudioTrack track;
    private static volatile boolean downloading = false;
    private static int seq = 0;

    private static File rootOf(Context c, String name) {
        return new File(c.getFilesDir(), "tts/" + name);
    }

    private static File root(Context c) {
        return rootOf(c, MODEL_NAME);
    }

    private static boolean isMatcha(File d) {
        return d.getName().startsWith("matcha-");
    }

    /** The acoustic model: the first .onnx file that is not the vocoder. */
    private static File acoustic(File d) {
        File[] files = d.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".onnx") && !f.getName().startsWith("vocos")) return f;
        }
        return null;
    }

    /** True when all files needed by the given kind of model are present. */
    private static boolean filesOk(File d, boolean matcha) {
        if (acoustic(d) == null || !new File(d, "tokens.txt").isFile()) return false;
        boolean lexicon = new File(d, "lexicon.txt").isFile();
        boolean espeak = new File(d, "espeak-ng-data").isDirectory();
        if (!lexicon && !espeak) return false;
        return !matcha || new File(d, VOCODER_NAME).isFile();
    }

    private static boolean complete(File d) {
        return filesOk(d, isMatcha(d));
    }

    /** True when the best Persian voice is on the device. */
    public static boolean bestInstalled(Context c) {
        return complete(root(c.getApplicationContext()));
    }

    /** The first complete earlier voice on the device, or null. */
    private static File olderDir(Context app) {
        for (String n : OLDER_NAMES) {
            File d = rootOf(app, n);
            if (complete(d)) return d;
        }
        return null;
    }

    /** True when any Persian voice (the default one or an earlier one) is on the device. */
    public static boolean installed(Context c) {
        Context app = c.getApplicationContext();
        return complete(root(app)) || olderDir(app) != null;
    }

    /** The folder of the voice to use: the default one when present, otherwise an earlier one. */
    private static File modelDir(Context c) {
        File best = root(c);
        if (complete(best)) return best;
        File older = olderDir(c);
        return older != null ? older : best;
    }

    public static boolean downloading() { return downloading; }

    private static File find(File dir, String suffix) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) if (f.isFile() && f.getName().endsWith(suffix)) return f;
        return null;
    }

    /** Downloads and unpacks the model in the background. */
    public static void download(Context c, final Progress progress, final Done done) {
        if (downloading) return;
        downloading = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            String err = null;
            File archive = new File(app.getCacheDir(), MODEL_NAME + ".tar.bz2");
            File stage = new File(app.getFilesDir(), "tts/" + MODEL_NAME + ".part");
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(MODEL_URL).openConnection();
                con.setInstanceFollowRedirects(true);
                con.setConnectTimeout(20000);
                con.setReadTimeout(30000);
                int code = con.getResponseCode();
                if (code / 100 != 2) throw new IOException("HTTP " + code);
                long total = con.getContentLengthLong();
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(archive)) {
                    byte[] buf = new byte[64 * 1024];
                    long got = 0;
                    int n;
                    int last = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        if (total > 0 && progress != null) {
                            int pct = (int) (got * 100 / total);
                            if (pct != last) {
                                last = pct;
                                MAIN.post(() -> progress.onProgress(pct));
                            }
                        }
                    }
                }
                deleteRec(stage);
                stage.mkdirs();
                try (InputStream in = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(archive)));
                     TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
                    TarArchiveEntry e;
                    while ((e = tar.getNextTarEntry()) != null) {
                        String name = e.getName();
                        int slash = name.indexOf('/');
                        if (slash < 0) continue;
                        String rel = name.substring(slash + 1);
                        if (rel.isEmpty() || rel.contains("..")) continue;
                        File f = new File(stage, rel);
                        if (e.isDirectory()) { f.mkdirs(); continue; }
                        File parent = f.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (OutputStream o = new FileOutputStream(f)) {
                            byte[] buf = new byte[64 * 1024];
                            int n;
                            while ((n = tar.read(buf)) > 0) o.write(buf, 0, n);
                        }
                    }
                }
                if (MODEL_NAME.startsWith("matcha-")) fetchTo(VOCODER_URL, new File(stage, VOCODER_NAME));
                if (!filesOk(stage, MODEL_NAME.startsWith("matcha-"))) {
                    throw new IOException("فایل‌های مدل ناقص است");
                }
                deleteRec(root(app));
                File parent = root(app).getParentFile();
                if (parent != null) parent.mkdirs();
                if (!stage.renameTo(root(app))) throw new IOException("ذخیرهٔ مدل ناموفق بود");
                synchronized (LOCK) { engine = null; }
                // The better voice replaces the earlier one, so the old files are removed.
                for (String n : OLDER_NAMES) deleteRec(rootOf(app, n));
            } catch (Exception ex) {
                err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            } finally {
                archive.delete();
                deleteRec(stage);
                downloading = false;
            }
            final String fe = err;
            MAIN.post(() -> { if (done != null) done.run(fe); });
        }, "tts-download").start();
    }

    /** Downloads one file into dest (used for the vocoder). */
    private static void fetchTo(String url, File dest) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        int code = con.getResponseCode();
        if (code / 100 != 2) throw new IOException("HTTP " + code);
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    private static OfflineTts engine(Context app) {
        synchronized (LOCK) {
            if (engine != null) return engine;
            File d = modelDir(app);
            // sherpa-onnx AAR config classes are Kotlin data classes: plain setters from Java.
            OfflineTtsModelConfig model = new OfflineTtsModelConfig();
            if (isMatcha(d)) {
                OfflineTtsMatchaModelConfig mt = new OfflineTtsMatchaModelConfig();
                mt.setAcousticModel(acoustic(d).getAbsolutePath());
                mt.setVocoder(new File(d, VOCODER_NAME).getAbsolutePath());
                mt.setTokens(new File(d, "tokens.txt").getAbsolutePath());
                File lexicon = new File(d, "lexicon.txt");
                if (lexicon.isFile()) mt.setLexicon(lexicon.getAbsolutePath());
                else mt.setDataDir(new File(d, "espeak-ng-data").getAbsolutePath());
                model.setMatcha(mt);
            } else {
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
                vits.setModel(acoustic(d).getAbsolutePath());
                vits.setTokens(new File(d, "tokens.txt").getAbsolutePath());
                vits.setDataDir(new File(d, "espeak-ng-data").getAbsolutePath());
                model.setVits(vits);
            }
            model.setNumThreads(3);
            model.setProvider("cpu");
            OfflineTtsConfig cfg = new OfflineTtsConfig();
            cfg.setModel(model);
            cfg.setMaxNumSentences(1);
            engine = new OfflineTts(null, cfg);
            return engine;
        }
    }

    /** Speaks the text. {@code onDone} runs on the main thread when speech ends (or fails). */
    public static void speak(Context c, final String text, final Runnable onDone) {
        final Context app = c.getApplicationContext();
        // stop() bumps the sequence; read it afterwards so this utterance is the current one.
        stop();
        final int my;
        synchronized (LOCK) { my = seq; }
        new Thread(() -> {
            float[] samples = null;
            int rate = 22050;
            String problem = null;
            try {
                OfflineTts t = engine(app);
                GeneratedAudio audio;
                // Amounts are read as words so the voice says them clearly.
                synchronized (t) { audio = t.generate(PersianNumbers.speakable(text), 0, 1.0f); }
                samples = audio.getSamples();
                rate = audio.getSampleRate();
                if (samples == null || samples.length == 0) problem = "متنی برای خواندن تولید نشد";
            } catch (Throwable e) {
                samples = null;
                problem = e.getMessage() == null ? e.toString() : e.getMessage();
                Diag.warn("OfflineVoice:speak", e);
                // Rebuild the engine on the next utterance in case this one left it unusable.
                synchronized (LOCK) { engine = null; }
            }
            final float[] s = samples;
            final int sr = rate;
            final String fp = problem;
            MAIN.post(() -> {
                if (my != seq) {
                    if (onDone != null) onDone.run();
                    return;
                }
                if (s == null || s.length == 0) {
                    // Say so on screen instead of failing silently.
                    android.widget.Toast.makeText(app, "صدای فارسی داخلی خطا داد: " + fp,
                            android.widget.Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                    return;
                }
                play(s, sr, onDone);
            });
        }, "tts-speak").start();
    }

    private static void play(float[] s, int sr, final Runnable onDone) {
        stopTrack();
        if (s.length == 0) { if (onDone != null) onDone.run(); return; }
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(s.length * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(s, 0, s.length, AudioTrack.WRITE_BLOCKING);
            track = t;
            t.play();
            long ms = s.length * 1000L / sr + 150;
            MAIN.postDelayed(() -> {
                if (track == t) stopTrack();
                if (onDone != null) onDone.run();
            }, ms);
        } catch (Exception e) {
            Diag.warn("OfflineVoice:play", e);
            if (onDone != null) onDone.run();
        }
    }

    private static void stopTrack() {
        AudioTrack t = track;
        track = null;
        if (t == null) return;
        try { t.stop(); } catch (Exception e) { Diag.ignored("OfflineVoice:stop", e); }
        try { t.release(); } catch (Exception e) { Diag.ignored("OfflineVoice:release", e); }
    }

    /** Stops any offline speech that is playing. */
    public static void stop() {
        synchronized (LOCK) { seq++; }
        stopTrack();
    }
}
