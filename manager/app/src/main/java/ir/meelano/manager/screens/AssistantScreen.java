package ir.meelano.manager.screens;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Assistant;
import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Notify;
import ir.meelano.manager.core.WarehouseSync;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.ui.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** «دستیار میلانو»: a Persian chat over the live store data, with voice in/out and a daily summary. */
public final class AssistantScreen extends Screen {

    private static final String[] QUICK = {
            "خلاصه امروز", "فروش امروز", "دریافت و پرداخت", "بدهکاران", "معوق‌ها",
            "چک‌های نزدیک سررسید", "موجودی و کسری", "ویزیتورها", "پیشنهاد اقدام", "راهنما"
    };
    private static final String PREFS = "meelano_assistant";

    private static TextToSpeech tts;
    private static boolean ttsReady = false;
    private static String pendingSpeech = null;

    private final List<String[]> msgs = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private Assistant.Snap snap;
    private boolean loading = false;
    private LinearLayout root;
    private String draft = "";

    /** Keeps the snapshot fresh every minute while the assistant is open; stops when it is closed. */
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (root == null || !root.isAttachedToWindow()) return;
            refresh(true, null);
            ticker.postDelayed(this, Assistant.FRESH_MS);
        }
    };

    public AssistantScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() {
        return "assistant";
    }

    @Override
    public String title() {
        return "دستیار میلانو";
    }

    @Override
    public String glyph() {
        return "🤖";
    }

    @Override
    public int accent() {
        return Theme.GOLD;
    }

    @Override
    public void render(LinearLayout content) {
        root = content;
        if (msgs.isEmpty()) {
            msgs.add(new String[]{"bot", "سلام! من دستیار مدیریت فروشگاه شما هستم. "
                    + "اطلاعات فروش، خرید، کالا، انبار، دریافت، پرداخت، چک‌ها و بدهکاران را از پایگاه‌داده می‌خوانم. "
                    + "هر سؤالی دارید بپرسید یا یکی از پرسش‌های آماده را بزنید."});
        }
        build();
        refresh(false, null);
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, Assistant.FRESH_MS);
    }

    // ================= data =================

    /** Reloads the snapshot when it is stale (or forced), then runs after. */
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
        if (q.isEmpty()) return;
        draft = "";
        msgs.add(new String[]{"user", q});
        refresh(false, () -> {
            String answer = Assistant.answer(snap, q);
            msgs.add(new String[]{"bot", answer});
            if (speakOn()) speak(answer);
        });
        redraw();
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
        root.addView(a.kit.gap(10));
        if (loading && snap == null) {
            root.addView(a.kit.loading("در حال خواندن اطلاعات فروشگاه…"), a.kit.lp(-1, -2));
        }
        for (String[] m : msgs) {
            root.addView(bubble(m[0], m[1]), a.kit.lp(-1, -2));
            root.addView(a.kit.gap(8));
        }
        root.addView(inputRow(), a.kit.lp(-1, -2));
        root.addView(a.kit.gap(6));
        root.addView(a.kit.hint("پاسخ‌ها از اطلاعات زندهٔ فروشگاه ساخته می‌شوند و هیچ داده‌ای به سرویس بیرونی فرستاده نمی‌شود."),
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
        return a.kit.hero("🤖", "دستیار هوشمند میلانو", status, Theme.GOLD);
    }

    private View quickChips() {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = a.kit.h();
        row.setPadding(0, 0, 0, 0);
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

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(Theme.dp(3), 0, Theme.dp(3), 0);
        return p;
    }

    private View bubble(String role, String text) {
        boolean user = "user".equals(role);
        LinearLayout card = a.kit.card(user ? Theme.TEAL : Theme.GOLD);
        TextView who = a.kit.text(user ? "🙋 شما" : "🤖 دستیار", 11.5f, user ? Theme.TEAL : Theme.GOLD_SOFT, true);
        card.addView(who, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(4));
        TextView body = a.kit.text(text, 13.5f, Theme.TEXT, false);
        body.setLineSpacing(Theme.dp(3), 1f);
        body.setTextIsSelectable(true);
        card.addView(body, a.kit.lp(-1, -2));
        if (!user) {
            card.addView(a.kit.gap(6));
            card.addView(a.kit.btnGhost("🔊 بخوان", Theme.GOLD, v -> speak(text)), a.kit.lp(-2, -2));
        }
        return card;
    }

    private View inputRow() {
        LinearLayout box = a.kit.v();
        final EditText input = a.kit.edit("مثلاً: فروش امروز چقدر است؟", draft);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                draft = s.toString();
            }
        });
        box.addView(input, a.kit.lp(-1, -2));
        box.addView(a.kit.gap(6));
        LinearLayout row = a.kit.h();
        row.addView(a.kit.btnGold("➤ ارسال", v -> ask(input.getText().toString())), weight());
        row.addView(a.kit.btnGhost("🎤 صوتی", Theme.TEAL, v -> a.startVoiceSearch(text -> {
            draft = text;
            ask(text);
        })), weight());
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

    // ================= voice out =================

    private boolean speakOn() {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("speak", false);
    }

    private void setSpeakOn(boolean on) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("speak", on).apply();
    }

    private void speak(String text) {
        try {
            String clean = text.replace("•", " ").replace("✦", " ").replace("📊", " ")
                    .replace("⚠", " ").replace("📨", " ");
            if (tts == null) {
                pendingSpeech = clean;
                tts = new TextToSpeech(a.getApplicationContext(), status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int r = tts.setLanguage(new Locale("fa"));
                        ttsReady = r >= TextToSpeech.LANG_AVAILABLE;
                        if (ttsReady && pendingSpeech != null) {
                            tts.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "meelano-assistant");
                        }
                    }
                    pendingSpeech = null;
                });
                return;
            }
            if (!ttsReady) {
                a.kit.toast("صدای فارسی روی این گوشی نصب نیست");
                return;
            }
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "meelano-assistant");
        } catch (Exception e) {
            Diag.ignored("AssistantScreen:speak", e);
        }
    }

    // ================= home entry =================

    /** Gradient entry card shown on the home screen. */
    public static View entryCard(MainActivity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.executiveHero());
        card.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        card.setElevation(Theme.cardElevation());
        TextView icon = a.kit.text("🤖", 28f, Theme.TEXT, false);
        card.addView(icon, a.kit.lp(-2, -2));
        LinearLayout col = a.kit.v();
        col.setPadding(Theme.dp(12), 0, Theme.dp(8), 0);
        col.addView(a.kit.text("دستیار هوشمند میلانو", 15f, Theme.GOLD, true), a.kit.lp(-1, -2));
        col.addView(a.kit.text("پرسش به فارسی، خلاصهٔ روزانه، پیشنهاد اقدام و خواندن با صدا", 11.5f, Theme.MUTED, false),
                a.kit.lp(-1, -2));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = a.kit.text("‹", 22f, Theme.GOLD, true);
        card.addView(go, a.kit.lp(-2, -2));
        card.setOnClickListener(v -> a.nav("assistant"));
        Theme.pressable(card);
        return card;
    }
}
