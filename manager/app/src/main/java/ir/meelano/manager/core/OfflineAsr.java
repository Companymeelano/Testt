package ir.meelano.manager.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

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
 * Offline Persian speech recognition: a multilingual Whisper (base) model from the sherpa-onnx
 * release, run on the phone. The model is downloaded once; after that listening needs no internet.
 *
 * Listening records from the microphone and stops after the speaker pauses, so the answer starts
 * as soon as the question is finished.
 */
public final class OfflineAsr {
    private OfflineAsr() { }

    public static final String MODEL_NAME = "sherpa-onnx-whisper-base";
    public static final String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" + MODEL_NAME + ".tar.bz2";

    private static final int RATE = 16000;
    private static final long MAX_UTTERANCE_MS = 20_000L;
    private static final long NO_SPEECH_MS = 8_000L;
    private static final long END_SILENCE_MS = 800L;

    public interface Progress { void onProgress(int percent); }

    /** Main thread. error is null on success; text may be empty when nothing was heard. */
    public interface Result { void run(String text, String error); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static OfflineRecognizer recognizer;
    private static volatile boolean downloading = false;
    private static volatile int session = 0;

    private static File root(Context c) {
        return new File(c.getFilesDir(), "asr/" + MODEL_NAME);
    }

    /** The file of a given kind (encoder, decoder, tokens); int8 versions are preferred. */
    private static File pick(File dir, String part, String suffix) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        File best = null;
        for (File f : files) {
            String n = f.getName();
            if (!f.isFile() || !n.contains(part) || !n.endsWith(suffix)) continue;
            if (best == null || (n.contains("int8") && !best.getName().contains("int8"))) best = f;
        }
        return best;
    }

    private static boolean complete(File d) {
        return pick(d, "encoder", ".onnx") != null && pick(d, "decoder", ".onnx") != null
                && pick(d, "tokens", ".txt") != null;
    }

    /** True when the offline recognizer is on the device. */
    public static boolean installed(Context c) {
        return complete(root(c.getApplicationContext()));
    }

    public static boolean downloading() { return downloading; }

    /** Downloads and unpacks the model in the background. */
    public static void download(Context c, final Progress progress, final Result done) {
        if (downloading) return;
        downloading = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            String err = null;
            File archive = new File(app.getCacheDir(), MODEL_NAME + ".tar.bz2");
            File stage = new File(app.getFilesDir(), "asr/" + MODEL_NAME + ".part");
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
                if (!complete(stage)) throw new IOException("فایل‌های تشخیص گفتار ناقص است");
                deleteRec(root(app));
                File parent = root(app).getParentFile();
                if (parent != null) parent.mkdirs();
                if (!stage.renameTo(root(app))) throw new IOException("ذخیرهٔ مدل ناموفق بود");
                synchronized (LOCK) { recognizer = null; }
            } catch (Exception ex) {
                err = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            } finally {
                archive.delete();
                deleteRec(stage);
                downloading = false;
            }
            final String fe = err;
            MAIN.post(() -> { if (done != null) done.run("", fe); });
        }, "asr-download").start();
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    private static OfflineRecognizer recognizer(Context app) {
        synchronized (LOCK) {
            if (recognizer != null) return recognizer;
            File d = root(app);
            OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
            whisper.setEncoder(pick(d, "encoder", ".onnx").getAbsolutePath());
            whisper.setDecoder(pick(d, "decoder", ".onnx").getAbsolutePath());
            whisper.setLanguage("fa");
            whisper.setTask("transcribe");
            OfflineModelConfig model = new OfflineModelConfig();
            model.setWhisper(whisper);
            model.setTokens(pick(d, "tokens", ".txt").getAbsolutePath());
            model.setNumThreads(3);
            model.setProvider("cpu");
            FeatureConfig feat = new FeatureConfig();
            feat.setSampleRate(RATE);
            feat.setFeatureDim(80);
            OfflineRecognizerConfig cfg = new OfflineRecognizerConfig();
            cfg.setFeatConfig(feat);
            cfg.setModelConfig(model);
            cfg.setDecodingMethod("greedy_search");
            recognizer = new OfflineRecognizer(null, cfg);
            return recognizer;
        }
    }

    /** Stops the current listening session; its callback is not called. */
    public static void cancel() {
        session++;
    }

    /**
     * Listens until the speaker pauses, then transcribes. {@code done} runs on the main thread.
     * Must be called with the RECORD_AUDIO permission granted.
     */
    public static void listen(final Context c, final Result done) {
        final Context app = c.getApplicationContext();
        final int my = ++session;
        new Thread(() -> {
            String text = "";
            String err = null;
            try {
                short[] pcm = record(my);
                if (pcm != null && pcm.length > RATE / 4 && my == session) {
                    float[] samples = new float[pcm.length];
                    for (int i = 0; i < pcm.length; i++) samples[i] = pcm[i] / 32768f;
                    OfflineRecognizer rec = recognizer(app);
                    OfflineStream stream = rec.createStream();
                    try {
                        stream.acceptWaveform(samples, RATE);
                        rec.decode(stream);
                        text = rec.getResult(stream).getText();
                    } finally {
                        stream.release();
                    }
                    text = text == null ? "" : text.trim();
                }
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String ft = text, fe = err;
            final boolean live = my == session;
            MAIN.post(() -> { if (live && done != null) done.run(ft, fe); });
        }, "asr-listen").start();
    }

    /** Records one utterance with a simple energy detector. Returns null when cancelled. */
    @SuppressLint("MissingPermission")
    private static short[] record(int my) {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            throw new IllegalStateException("میکروفون در دسترس نیست");
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        short[] chunk = new short[RATE / 10];
        long start = System.currentTimeMillis();
        long heardAt = -1, lastLoud = -1;
        double floor = -1;
        try {
            rec.startRecording();
            while (my == session) {
                int n = rec.read(chunk, 0, chunk.length);
                if (n <= 0) continue;
                long now = System.currentTimeMillis();
                double sum = 0;
                for (int i = 0; i < n; i++) sum += (double) chunk[i] * chunk[i];
                double rms = Math.sqrt(sum / n) / 32768.0;
                // Noise floor from the first moments, then speech must clearly exceed it.
                if (floor < 0) floor = rms;
                else if (heardAt < 0) floor = floor * 0.9 + rms * 0.1;
                double threshold = Math.max(0.012, floor * 3.0);
                boolean loud = rms > threshold;
                if (heardAt >= 0 || loud) {
                    for (int i = 0; i < n; i++) {
                        out.write(chunk[i] & 0xff);
                        out.write((chunk[i] >> 8) & 0xff);
                    }
                }
                if (loud) {
                    if (heardAt < 0) heardAt = now;
                    lastLoud = now;
                }
                if (heardAt >= 0 && now - lastLoud >= END_SILENCE_MS) break;
                if (heardAt < 0 && now - start >= NO_SPEECH_MS) break;
                if (now - start >= MAX_UTTERANCE_MS) break;
            }
        } finally {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
        }
        if (my != session) return null;
        byte[] b = out.toByteArray();
        short[] pcm = new short[b.length / 2];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
        return pcm;
    }
}
