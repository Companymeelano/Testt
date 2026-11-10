package ir.meelano.manager.core;

import android.os.Handler;
import android.os.Looper;

import ir.meelano.manager.MainActivity;

import java.util.ArrayDeque;

/**
 * Reads a streamed answer sentence by sentence, so speech starts as soon as the first sentence
 * is complete instead of after the whole answer. Each sentence is spoken with {@link Speaker};
 * the next one starts when the previous one has finished. All calls run on the main thread.
 */
public final class SpeechQueue {
    private final MainActivity a;
    private final Runnable onAllDone;
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int spokenUpTo = 0;
    private boolean busy = false;
    private boolean finished = false;
    private boolean cancelled = false;
    private boolean doneFired = false;

    /** {@code onAllDone} runs on the main thread after the last sentence (may be null). */
    public SpeechQueue(MainActivity a, Runnable onAllDone) {
        this.a = a;
        this.onAllDone = onAllDone;
    }

    /** Feeds the text so far. {@code complete} is true when the answer has ended. */
    public void feed(String text, boolean complete) {
        if (cancelled || text == null) return;
        int i = spokenUpTo;
        while (true) {
            int end = sentenceEnd(text, i);
            if (end < 0) break;
            enqueue(text.substring(i, end));
            i = end;
        }
        if (complete && i < text.length()) enqueue(text.substring(i));
        spokenUpTo = i;
        if (complete) finished = true;
        pump();
    }

    /** Stops this queue: nothing more is spoken and onAllDone is not called. */
    public void cancel() {
        cancelled = true;
        pending.clear();
        Speaker.stopAll();
    }

    /** Index just after the first sentence end at or after {@code from}, or -1 if none yet. */
    private static int sentenceEnd(String s, int from) {
        for (int k = from; k < s.length(); k++) {
            char c = s.charAt(k);
            boolean stop = c == '\n' || c == '.' || c == '!' || c == '?' || c == '؟' || c == '…';
            if (!stop) continue;
            // A full stop inside a number (۲.۵) or a decimal is not a sentence end.
            if (c == '.' && k + 1 < s.length() && Character.isDigit(s.charAt(k + 1))) continue;
            if (c != '\n' && (k + 1 >= s.length() || !Character.isWhitespace(s.charAt(k + 1)))) continue;
            return k + 1;
        }
        return -1;
    }

    private void enqueue(String seg) {
        if (seg.trim().isEmpty()) return;
        pending.add(seg.trim());
    }

    private void pump() {
        if (cancelled || busy) return;
        if (!pending.isEmpty()) {
            busy = true;
            String next = pending.poll();
            Speaker.speak(a, next, () -> {
                busy = false;
                pump();
            });
            return;
        }
        if (finished && !doneFired) {
            doneFired = true;
            if (onAllDone != null) main.post(onAllDone);
        }
    }
}
