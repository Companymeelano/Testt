package ir.meelano.manager.screens;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiClient;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.Assistant;
import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Notify;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.core.WarehouseSync;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.Card3D;
import ir.meelano.manager.ui.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * «دستیار میلانو»: a Persian manager chat over the live store data.
 * Uses the configured generative model (set in Settings → هوش مصنوعی) when it is
 * enabled; otherwise the built-in data engine answers. Supports typed questions,
 * voice input, read-aloud replies, and a live voice conversation mode.
 */
public final class AssistantScreen extends Screen {

    private static final String[] QUICK = {
            "خلاصه امروز", "فروش امروز", "بدهکاران", "پیشنهاد اقدام"
    };
    private static final String PREFS = "meelano_assistant";

    static final String SYSTEM_PROMPT =
            "تو «میلانو» هستی، دستیار هوشمند و خونگرم این فروشگاه، و مثل یک گفتگوی دوستانه با یک آدم فارسی حرف می‌زنی، "
            + "مشابه Gemini و ChatGPT: طبیعی، روان، دقیق و محاوره‌ای. "
            + "پاسخ‌ها برای خواندن با صدای بلند نوشته می‌شوند، پس: بدون Markdown، بدون علامت ستاره و "
            + "خط تیره و بدون فهرست شماره‌دار؛ جملات کوتاه و روان. "
            + "سؤال‌های عمومی را مثل یک دستیار باهوش کامل و درست جواب بده. "
            + "اگر سؤال درباره‌ی فروشگاه بود، فقط بر اساس «داده‌های فروشگاه» جواب بده و هیچ عددی را حدس نزن؛ "
            + "اگر داده نبود، ساده و صادقانه بگو. "
            + "اول جواب مستقیم سؤال را بده، بعد اگر لازم بود یک پیشنهاد عملی کوتاه. "
            + "مبلغ‌ها را با جداکننده هزارگان و «ریال» بگو و از تکرار سلام و معرفی خودت خودداری کن.";

    /** Set by the command room («اتاق فرمان») before opening the assistant with a question. */
    public static String pendingQuestion = null;

    private final List<String[]> msgs = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private Assistant.Snap snap;
    private boolean loading = false;
    /** Increments on every data request; a watchdog uses it to drop stale timers. */
    private long reqId = 0;
    /** Human-readable reason of the last failed read, or null when the last read worked. */
    private String lastError = null;
    private static final long LOAD_TIMEOUT_MS = 20000;
    private boolean aiBusy = false;
    private LinearLayout root;
    private String draft = "";

    // live conversation
    private boolean liveOn = false;
    private boolean listening = false;
    private SpeechRecognizer recognizer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Keeps the snapshot fresh every minute while the assistant is open. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // The content container is shared by every screen: only refresh while the assistant is on display.
            if (root == null || !root.isAttachedToWindow() || !"assistant".equals(a.currentScreenId())) return;
            refresh(true, null);
            handler.postDelayed(this, Assistant.FRESH_MS);
        }
    };

    public AssistantScreen(MainActivity a) {
        super(a);
    }

    @Override public String id() { return "assistant"; }
    @Override public String title() { return "دستیار میلانو"; }
    @Override public String glyph() { return "✦"; }
    @Override public int accent() { return Theme.GOLD; }

    @Override
    public void render(LinearLayout content) {
        root = content;
        if (msgs.isEmpty()) {
            msgs.add(new String[]{"bot", "سلام! من دستیار مدیریت فروشگاه شما هستم. "
                    + "اطلاعات فروش، خرید، کالا، انبار، دریافت، پرداخت، چک‌ها و بدهکاران را از پایگاه‌داده می‌خوانم. "
                    + "هر سؤالی دارید بپرسید، یکی از پرسش‌های آماده را بزنید، یا «گفتگوی زنده» را روشن کنید."});
        }
        build();
        refresh(false, null);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, Assistant.FRESH_MS);
        if (pendingQuestion != null) {
            String q = pendingQuestion;
            pendingQuestion = null;
            ask(q);
        }
    }

    // ================= data =================

    /** Reloads the snapshot when it is stale (or forced), then runs the queued actions. */
    private void refresh(boolean force, Runnable after) {
        boolean fresh = snap != null && System.currentTimeMillis() - snap.loadedAt < Assistant.FRESH_MS;
        if (after != null) pending.add(after);
        if (loading) return;
        if (fresh && !force) {
            flushPending();
            return;
        }
        loading = true;
        final long my = ++reqId;
        redraw();
        handler.postDelayed(() -> {
            if (!loading || my != reqId) return;
            // The database did not answer in time: stop spinning and offer a retry.
            loading = false;
            lastError = "پاسخ پایگاه‌داده دیر رسید";
            if (snap == null) pending.clear();
            redraw();
        }, LOAD_TIMEOUT_MS);
        a.repo.run(c -> Assistant.load(c), new Repo.Cb<Assistant.Snap>() {
            @Override
            public void ok(Assistant.Snap v) {
                loading = false;
                lastError = null;
                try {
                    v.pendingSync = WarehouseSync.pendingCount(a);
                } catch (Exception e) {
                    Diag.ignored("AssistantScreen:pending", e);
                }
                snap = v;
                flushPending();
            }

            @Override
            public void fail(String faError) {
                loading = false;
                lastError = faError == null || faError.isEmpty() ? "خطای ناشناخته" : faError;
                if (snap == null) {
                    // No data at all yet: answering now would invent zeros, so ask the user to retry.
                    pending.clear();
                    redraw();
                    return;
                }
                flushPending();
            }
        });
    }

    private void flushPending() {
        List<Runnable> run = new ArrayList<>(pending);
        pending.clear();
        for (Runnable r : run) {
            try {
                r.run();
            } catch (Exception e) {
                Diag.ignored("AssistantScreen:flush", e);
            }
        }
        redraw();
    }

    private void ask(String question) {
        final String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            if (liveOn) listenOnce();
            return;
        }
        draft = "";
        msgs.add(new String[]{"user", q});
        redraw();
        refresh(false, () -> {
            String rule = Assistant.answer(snap, q);
            if (aiReady()) {
                askAi(q, rule);
            } else {
                addAnswer("bot", rule);
            }
        });
    }

    /** Adds an assistant reply; in live mode it is read aloud and then listening resumes. */
    private void addAnswer(String role, String text) {
        msgs.add(new String[]{role, text});
        redraw();
        if (liveOn) {
            Speaker.speak(a, text, this::listenOnce);
        } else if (speakOn()) {
            Speaker.speak(a, text, null);
        }
    }

    // ================= generative AI =================

    private boolean aiReady() {
        return AiSettings.ready(a);
    }

    /** The answer currently being written, and the text view that shows it (updated in place). */
    private String[] liveSlot;
    private TextView liveBody;

    private void askAi(final String q, final String rule) {
        final String ctx = Assistant.contextBlock(snap);
        aiBusy = true;
        final String[] slot = {"ai", "…"};
        liveSlot = slot;
        liveBody = null;
        msgs.add(slot);
        redraw();
        new Thread(() -> {
            final StringBuilder acc = new StringBuilder();
            final long[] lastShown = {0};
            String err = null;
            String reply = null;
            try {
                AiRouter.Result r = AiRouter.completeStream(a, AiRouter.taskFor(q), SYSTEM_PROMPT,
                        "داده‌های فروشگاه (امروز):\n" + ctx + "\nسؤال مدیر: " + q, piece -> {
                            acc.append(piece);
                            long now = System.currentTimeMillis();
                            if (now - lastShown[0] < 70) return;
                            lastShown[0] = now;
                            final String shown = acc.toString();
                            a.runOnUiThread(() -> {
                                if (liveBody != null && liveSlot == slot) {
                                    slot[1] = shown;
                                    liveBody.setText(shown);
                                }
                            });
                        });
                reply = r.text;
            } catch (Exception e) {
                err = e.getMessage() == null ? "خطای ناشناخته" : e.getMessage();
            }
            final String fr = reply, fe = err;
            a.runOnUiThread(() -> {
                aiBusy = false;
                liveSlot = null;
                liveBody = null;
                if (fr != null) {
                    slot[0] = "ai";
                    slot[1] = fr;
                } else {
                    slot[0] = "bot";
                    slot[1] = "هوش مصنوعی الان جواب نداد (" + fe + ")؛ با اطلاعات خود برنامه جواب می‌دهم.\n" + rule;
                }
                redraw();
                String spoken = slot[1];
                if (liveOn) {
                    Speaker.speak(a, spoken, this::listenOnce);
                } else if (speakOn()) {
                    Speaker.speak(a, spoken, null);
                }
            });
        }).start();
    }

    // ================= live conversation =================

    private void toggleLive() {
        if (liveOn) {
            stopLive("گفتگوی زنده متوقف شد");
            return;
        }
        if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestMic(granted -> {
                if (granted) toggleLive();
                else a.kit.toast("برای گفتگوی زنده دسترسی میکروفون لازم است");
            });
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(a)) {
            a.kit.toast("تشخیص گفتار در این گوشی پشتیبانی نمی‌شود");
            return;
        }
        liveOn = true;
        if (!speakOn()) setSpeakOn(true);
        a.kit.toast("گفتگوی زنده روشن شد؛ سؤالتان را بگویید");
        listenOnce();
    }

    private void stopLive(String note) {
        liveOn = false;
        listening = false;
        try {
            if (recognizer != null) {
                recognizer.stopListening();
                recognizer.cancel();
                recognizer.destroy();
            }
        } catch (Exception e) {
            Diag.ignored("AssistantScreen:stopLive", e);
        }
        recognizer = null;
        if (note != null) a.kit.toast(note);
        redraw();
    }

    private void listenOnce() {
        if (!liveOn) return;
        if (root == null || !root.isAttachedToWindow()) {
            stopLive(null);
            return;
        }
        if (aiBusy) return; // answer still coming; listenOnce runs again after it is spoken
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(a);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onPartialResults(Bundle partialResults) { }
                    @Override public void onEvent(int eventType, Bundle params) { }

                    @Override
                    public void onResults(Bundle results) {
                        listening = false;
                        List<String> got = results == null ? null
                                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        String text = got == null || got.isEmpty() ? "" : got.get(0).trim();
                        if (text.isEmpty()) {
                            listenOnce();
                        } else {
                            ask(text);
                        }
                    }

                    @Override
                    public void onError(int error) {
                        listening = false;
                        if (!liveOn) return;
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            handler.postDelayed(AssistantScreen.this::listenOnce, 500);
                        } else {
                            stopLive("گفتگوی زنده متوقف شد؛ دوباره روشن کنید");
                        }
                    }
                });
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            listening = true;
            recognizer.startListening(i);
            redraw();
        } catch (Exception e) {
            Diag.ignored("AssistantScreen:listen", e);
            stopLive("شنیدن صدا ممکن نشد");
        }
    }

    // ================= UI =================

    private void redraw() {
        if (root == null || !"assistant".equals(a.currentScreenId())) return;
        try {
            build();
        } catch (Exception e) {
            Diag.ignored("AssistantScreen:redraw", e);
        }
    }

    private void build() {
        root.removeAllViews();
        root.addView(hero(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(10));
        root.addView(quickChips(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(8));
        root.addView(speakChip(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(10));
        if (liveOn) {
            root.addView(liveBar(), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(10));
        }
        if (loading && snap == null) {
            root.addView(a.kit.loading("در حال خواندن اطلاعات فروشگاه…"), a.kit.lp(-1, -2));
        } else if (!loading && (snap == null || snap.loadedAt <= 0) && lastError != null) {
            root.addView(a.kit.error("پایگاه‌داده پاسخ نداد (" + lastError + ")", () -> {
                lastError = null;
                refresh(true, null);
            }), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(10));
        }
        for (String[] m : msgs) {
            root.addView(bubble(m[0], m[1], m == liveSlot), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(8));
        }
        if (aiBusy) {
            root.addView(a.kit.loading("هوش مصنوعی در حال تحلیل…"), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(8));
        }
        root.addView(inputRow(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(6));
        root.addView(a.kit.hint(aiReady()
                ? "پاسخ‌ها با مدل هوش مصنوعی شما و از روی داده‌های زندهٔ فروشگاه ساخته می‌شوند."
                : "پاسخ‌ها از موتور داخلی و داده‌های زندهٔ فروشگاه ساخته می‌شوند؛ هیچ داده‌ای بیرون نمی‌رود."),
                a.kit.lp(-1, -2));
    }

    private View hero() {
        String status;
        boolean hasData = snap != null && snap.loadedAt > 0;
        if (loading) {
            status = "در حال اتصال به پایگاه‌داده…";
        } else if (!hasData) {
            status = lastError != null ? "اتصال برقرار نشد؛ پایین صفحه را بزنید" : "در حال اتصال به پایگاه‌داده…";
        } else {
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(snap.loadedAt));
            status = "داده‌های زنده • آخرین خواندن ساعت " + Money.fa(time)
                    + (lastError != null ? " • اتصال موقتاً قطع است" : " • نوسازی هر دقیقه");
        }
        return a.kit.hero("✦", "دستیار هوشمند میلانو", status, Theme.GOLD);
    }

    /** Quick questions as a 2×2 grid: every label is fully visible, no horizontal cut-off. */
    private View quickChips() {
        LinearLayout grid = a.kit.v();
        for (int i = 0; i < QUICK.length; i += 2) {
            LinearLayout row = a.kit.h();
            for (int j = i; j < Math.min(i + 2, QUICK.length); j++) {
                final String q = QUICK[j];
                if (j > i) row.addView(a.kit.space(8));
                row.addView(a.kit.btnGhost(q, Theme.GOLD, v -> ask(q)), a.kit.wlp(1f));
            }
            grid.addView(row, a.kit.lp(-1, -2));
            if (i + 2 < QUICK.length) grid.addView(a.kit.gap(8));
        }
        return grid;
    }

    /** One quiet control: whether answers are read aloud. Data refreshes on its own. */
    private View speakChip() {
        boolean on = speakOn();
        return a.kit.btnGhost(on ? "🔊  پاسخ‌ها با صدا" : "🔈  پاسخ‌ها بی‌صدا",
                on ? Theme.GOLD : Theme.GOLD_SOFT, v -> {
            setSpeakOn(!on);
            redraw();
        });
    }

    private View aiRow() {
        boolean ready = aiReady();
        String label = ready
                ? "✦ هوش مصنوعی فعال"
                : "✦ هوش مصنوعی خاموش — تنظیم در «تنظیمات»";
        return a.kit.btnGhost(label, Theme.GOLD, v -> {
            SettingsScreen.pendingSection = "ai";
            a.nav("settings");
        });
    }

    private View liveBar() {
        LinearLayout bar = Card3D.card(a, Theme.GOLD);
        LinearLayout row = a.kit.h();
        String state = listening ? "🎙 در حال شنیدن… سؤالتان را بگویید" : "✦ در حال پاسخ یا خواندن…";
        row.addView(a.kit.text(state, 12.5f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("■ توقف", Theme.DANGER, v -> toggleLive()), a.kit.lp(-2, -2));
        bar.addView(row, a.kit.lp(-1, -2));
        return bar;
    }

    private LinearLayout.LayoutParams weightBy(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, w);
        p.setMargins(Theme.dp(3), 0, Theme.dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(Theme.dp(3), 0, Theme.dp(3), 0);
        return p;
    }

    private View bubble(String role, String text, boolean live) {
        boolean user = "user".equals(role);
        boolean ai = "ai".equals(role);
        int accent = user ? Theme.STEEL : (ai ? Theme.VIOLET : Theme.GOLD);
        LinearLayout card = Card3D.card(a, accent);
        String who = user ? "🙋 شما" : (ai ? "✦ هوش مصنوعی" : "🤖 دستیار");
        card.addView(a.kit.text(who, 11.5f, accent == Theme.GOLD ? Theme.GOLD_SOFT : accent, true), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(4));
        TextView body = a.kit.text(text, 13.5f, Theme.TEXT, false);
        body.setLineSpacing(Theme.dp(3), 1f);
        body.setTextIsSelectable(true);
        card.addView(body, a.kit.lp(-1, -2));
        if (live) liveBody = body;
        if (!user && !live) {
            card.addView(a.kit.gap(6));
            boolean reading = Speaker.isSpeaking();
            card.addView(a.kit.btnGhost(reading ? "■ توقف" : "🔊 بخوان", Theme.GOLD, v -> {
                if (Speaker.isSpeaking()) {
                    Speaker.stopAll();
                    redraw();
                    return;
                }
                a.kit.toast("در حال آماده‌سازی صدا…");
                Speaker.speak(a, text, this::redraw);
                redraw();
            }), a.kit.lp(-2, -2));
        }
        return card;
    }

    private View inputRow() {
        LinearLayout box = a.kit.v();
        final EditText input = a.kit.edit("مثلاً: فروش امروز چقدر است؟", draft);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { draft = s.toString(); }
        });
        box.addView(input, a.kit.lp(-1, -2));
        box.addView(a.kit.gap(6));
        // Row 1: send (wide) and voice. Row 2: live conversation, full width, so its label never wraps.
        LinearLayout row = a.kit.h();
        android.widget.Button send = a.kit.btnGold("➤ ارسال", v -> ask(input.getText().toString()));
        send.setSingleLine(true);
        row.addView(send, weightBy(1.6f));
        android.widget.Button voice = a.kit.btnGhost("🎤 صوتی", Theme.GOLD, v -> a.startVoiceSearch(text -> {
            draft = text;
            ask(text);
        }));
        voice.setSingleLine(true);
        row.addView(voice, weightBy(1f));
        box.addView(row, a.kit.lp(-1, -2));
        box.addView(a.kit.gap(8));
        android.widget.Button live = a.kit.btnGhost(liveOn ? "■ پایان گفتگوی زنده" : "🎙 گفتگوی زنده", Theme.VIOLET,
                v -> toggleLive());
        live.setSingleLine(true);
        box.addView(live, a.kit.lp(-1, -2));
        return box;
    }

    // ================= summary =================

    private void sendSummary() {
        refresh(true, () -> {
            String text = Assistant.dailySummary(snap);
            Notify.sendSummary(a, text);
            msgs.add(new String[]{"bot", text});
            a.kit.toast("خلاصه امروز به اعلان‌ها ارسال شد");
        });
    }

    // ================= voice out preference =================

    private boolean speakOn() {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("speak", false);
    }

    private void setSpeakOn(boolean on) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("speak", on).apply();
    }
}
