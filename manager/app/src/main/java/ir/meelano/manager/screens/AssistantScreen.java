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
            "خلاصه امروز", "فروش امروز", "دریافت و پرداخت", "بدهکاران", "معوق‌ها",
            "چک‌های نزدیک سررسید", "موجودی و کسری", "ویزیتورها", "پیشنهاد اقدام", "راهنما"
    };
    private static final String PREFS = "meelano_assistant";

    static final String SYSTEM_PROMPT =
            "تو دستیار ارشد مدیریت یک فروشگاه ایرانی هستی؛ مدیری بسیار باتجربه در فروش، خرید، کالا، انبار، "
            + "دریافت و پرداخت، چک، بدهکاری و ویزیتورها. فقط بر اساس «داده‌های فروشگاه» پاسخ بده، "
            + "هیچ عددی را حدس نزن و اگر داده‌ای نبود صریح بگو. پاسخ فارسی، کوتاه، دقیق و قابل اقدام باشد؛ "
            + "در پایان یک یا دو پیشنهاد عملی بده. مبالغ را با جداکننده هزارگان و واحد ریال بنویس.";

    /** Set by the command room («اتاق فرمان») before opening the assistant with a question. */
    public static String pendingQuestion = null;

    private final List<String[]> msgs = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private Assistant.Snap snap;
    private boolean loading = false;
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
            if (root == null || !root.isAttachedToWindow()) return;
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
        redraw();
        a.repo.run(c -> Assistant.load(c), new Repo.Cb<Assistant.Snap>() {
            @Override
            public void ok(Assistant.Snap v) {
                loading = false;
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
                if (snap == null) snap = new Assistant.Snap();
                snap.notes.put("اتصال به پایگاه‌داده", faError == null ? "خطا" : faError);
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

    private void askAi(final String q, final String rule) {
        final String provider = AiSettings.prefs(a).getString("provider", AiClient.GEMINI);
        final String model = AiSettings.prefs(a).getString("model", "");
        final String custom = AiSettings.prefs(a).getString("baseUrl", "");
        final String key = SecretBox.get(a, AiSettings.KEY_NAME);
        final String ctx = Assistant.contextBlock(snap);
        aiBusy = true;
        redraw();
        new Thread(() -> {
            String reply = null;
            String err = null;
            try {
                reply = AiClient.complete(provider, custom, model, key, SYSTEM_PROMPT,
                        "داده‌های فروشگاه (امروز):\n" + ctx + "\nسؤال مدیر: " + q);
            } catch (Exception e) {
                err = e.getMessage() == null ? "خطای ناشناخته" : e.getMessage();
            }
            final String fr = reply, fe = err;
            a.runOnUiThread(() -> {
                aiBusy = false;
                if (fr != null) {
                    addAnswer("ai", fr);
                } else {
                    addAnswer("bot", "⚠ هوش مصنوعی پاسخ نداد: " + fe + "\nپاسخ داده‌محور:\n" + rule);
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
        if (root == null) return;
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
        root.addView(a.kit.gap(10));
        root.addView(actionRow(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(8));
        root.addView(aiRow(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(10));
        if (liveOn) {
            root.addView(liveBar(), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(10));
        }
        if (loading && snap == null) {
            root.addView(a.kit.loading("در حال خواندن اطلاعات فروشگاه…"), a.kit.lp(-1, -2));
        }
        for (String[] m : msgs) {
            root.addView(bubble(m[0], m[1]), a.kit.lp(-1, -2));
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
        if (snap == null || loading) {
            status = "در حال اتصال به پایگاه‌داده…";
        } else {
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(snap.loadedAt));
            status = "داده‌های زنده • آخرین خواندن ساعت " + Money.fa(time) + " • نوسازی هر دقیقه";
        }
        return a.kit.hero("✦", "دستیار هوشمند میلانو", status, Theme.GOLD);
    }

    private View quickChips() {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = a.kit.h();
        for (String q : QUICK) {
            row.addView(a.kit.btnGhost(q, Theme.GOLD, v -> ask(q)), a.kit.lp(-2, -2));
        }
        sv.addView(row);
        return sv;
    }

    private View actionRow() {
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGhost("⟳ بروزرسانی", Theme.TEAL, v -> refresh(true, null)), weight());
        row.addView(a.kit.btnGhost("📨 ارسال خلاصه", Theme.GOLD, v -> sendSummary()), weight());
        boolean on = speakOn();
        row.addView(a.kit.btnGhost(on ? "🔊 صدا: روشن" : "🔈 صدا: خاموش", Theme.VIOLET, v -> {
            setSpeakOn(!on);
            redraw();
        }), weight());
        return row;
    }

    private View aiRow() {
        boolean ready = aiReady();
        String label = ready
                ? "✦ هوش مصنوعی فعال • " + AiSettings.prefs(a).getString("model", "")
                : "✦ هوش مصنوعی خاموش — تنظیم در «تنظیمات»";
        return a.kit.btnGhost(label, Theme.GOLD, v -> {
            SettingsScreen.pendingSection = "ai";
            a.nav("settings");
        });
    }

    private View liveBar() {
        LinearLayout bar = Card3D.card(a, Theme.TEAL);
        LinearLayout row = a.kit.h();
        String state = listening ? "🎙 در حال شنیدن… سؤالتان را بگویید" : "✦ در حال پاسخ یا خواندن…";
        row.addView(a.kit.text(state, 12.5f, Theme.TEXT, true), a.kit.wlp(1f));
        row.addView(a.kit.btnGhost("■ توقف", Theme.DANGER, v -> toggleLive()), a.kit.lp(-2, -2));
        bar.addView(row, a.kit.lp(-1, -2));
        return bar;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(Theme.dp(3), 0, Theme.dp(3), 0);
        return p;
    }

    private View bubble(String role, String text) {
        boolean user = "user".equals(role);
        boolean ai = "ai".equals(role);
        int accent = user ? Theme.TEAL : (ai ? Theme.VIOLET : Theme.GOLD);
        LinearLayout card = Card3D.card(a, accent);
        String who = user ? "🙋 شما" : (ai ? "✦ هوش مصنوعی" : "🤖 دستیار");
        card.addView(a.kit.text(who, 11.5f, accent == Theme.GOLD ? Theme.GOLD_SOFT : accent, true), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(4));
        TextView body = a.kit.text(text, 13.5f, Theme.TEXT, false);
        body.setLineSpacing(Theme.dp(3), 1f);
        body.setTextIsSelectable(true);
        card.addView(body, a.kit.lp(-1, -2));
        if (!user) {
            card.addView(a.kit.gap(6));
            card.addView(a.kit.btnGhost("🔊 بخوان", Theme.GOLD, v -> Speaker.speak(a, text, null)), a.kit.lp(-2, -2));
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
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGold("➤ ارسال", v -> ask(input.getText().toString())), weight());
        row.addView(a.kit.btnGhost("🎤 صوتی", Theme.TEAL, v -> a.startVoiceSearch(text -> {
            draft = text;
            ask(text);
        })), weight());
        row.addView(a.kit.btnGhost(liveOn ? "■ گفتگو" : "🎙 گفتگوی زنده", Theme.VIOLET, v -> toggleLive()), weight());
        box.addView(row, a.kit.lp(-1, -2));
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
