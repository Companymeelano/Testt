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
 * Built-in Persian voice: sherpa-onnx (offline VITS) with the Piper «amir» Persian model.
 *
 * The engine ships inside the APK. The model (about 60 MB) is downloaded once, on the user's
 * request, from the sherpa-onnx GitHub release, and then works without internet.
 */
public final class OfflineVoice {
    private OfflineVoice() { }

    public static final String MODEL_NAME = "vits-piper-fa_IR-amir-medium";
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

    private static File root(Context c) {
        return new File(c.getFilesDir(), "tts/" + MODEL_NAME);
    }

    /** True when the model files are on the device. */
    public static boolean installed(Context c) {
        File d = root(c.getApplicationContext());
        return find(d, ".onnx") != null && new File(d, "tokens.txt").isFile()
                && new File(d, "espeak-ng-data").isDirectory();
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
                if (find(stage, ".onnx") == null || !new File(stage, "tokens.txt").isFile()) {
                    throw new IOException("فایل‌های مدل ناقص است");
                }
                deleteRec(root(app));
                File parent = root(app).getParentFile();
                if (parent != null) parent.mkdirs();
                if (!stage.renameTo(root(app))) throw new IOException("ذخیرهٔ مدل ناموفق بود");
                synchronized (LOCK) { engine = null; }
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

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    private static OfflineTts engine(Context app) {
        synchronized (LOCK) {
            if (engine != null) return engine;
            File d = root(app);
            // sherpa-onnx AAR config classes are Kotlin data classes: plain setters from Java.
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(find(d, ".onnx").getAbsolutePath());
            vits.setTokens(new File(d, "tokens.txt").getAbsolutePath());
            vits.setDataDir(new File(d, "espeak-ng-data").getAbsolutePath());
            OfflineTtsModelConfig model = new OfflineTtsModelConfig();
            model.setVits(vits);
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
            try {
                OfflineTts t = engine(app);
                GeneratedAudio audio;
                synchronized (t) { audio = t.generate(text, 0, 1.0f); }
                samples = audio.getSamples();
                rate = audio.getSampleRate();
            } catch (Throwable e) {
                samples = null;
                ir.meelano.manager.core.Diag.ignored("OfflineVoice:speak", e);
            }
            final float[] s = samples;
            final int sr = rate;
            MAIN.post(() -> {
                if (my != seq || s == null) {
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
            if (onDone != null) onDone.run();
        }
    }

    private static void stopTrack() {
        AudioTrack t = track;
        track = null;
        if (t == null) return;
        try { t.stop(); } catch (Exception ignored) { }
        try { t.release(); } catch (Exception ignored) { }
    }

    /** Stops any offline speech that is playing. */
    public static void stop() {
        synchronized (LOCK) { seq++; }
        stopTrack();
    }
}
