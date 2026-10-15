package ir.meelano.manager.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
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
 * Uses a configured generative model (Gemini, OpenAI, Grok or a custom
 * OpenAI-compatible server) when the user has added a key and allowed it;
 * otherwise the built-in data engine answers. Voice in and out, daily summary,
 * and a minute refresh while the screen is open.
 */
public final class AssistantScreen extends Screen {

    private static final String[] QUICK = {
            "خلاصه امروز", "فروش امروز", "دریافت و پرداخت", "بدهکاران", "معوق‌ها",
            "چک‌های نزدیک سررسید", "موجودی و کسری", "ویزیتورها", "پیشنهاد اقدام", "راهنما"
    };
    private static final String PREFS = "meelano_assistant";
    private static final String AI_PREFS = "meelano_ai";
    private static final String KEY_NAME = "ai_key";

    static final String SYSTEM_PROMPT =
            "تو دستیار ارشد مدیریت یک فروشگاه ایرانی هستی؛ مدیری بسیار باتجربه در فروش، خرید، کالا، انبار، "
            + "دریافت و پرداخت، چک، بدهکاری و ویزیتورها. فقط بر اساس «داده‌های فروشگاه» پاسخ بده، "
            + "هیچ عددی را حدس نزن و اگر داده‌ای نبود صریح بگو. پاسخ فارسی، کوتاه، دقیق و قابل اقدام باشد؛ "
            + "در پایان یک یا دو پیشنهاد عملی بده. مبالغ را با جداکننده هزارگان و واحد ریال بنویس.";

    private static TextToSpeech tts;
    private static boolean ttsReady = false;
    private static String pendingSpeech = null;

    private final List<String[]> msgs = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private Assistant.Snap snap;
    private boolean loading = false;
    private boolean aiBusy = false;
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
        return "✦";
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
        redraw();
        refresh(false, () -> {
            String rule = Assistant.answer(snap, q);
            if (aiReady()) {
                askAi(q, rule);
            } else {
                msgs.add(new String[]{"bot", rule});
                if (speakOn()) speak(rule);
            }
        });
    }

    // ================= generative AI =================

    private SharedPreferences aiPrefs() {
        return a.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE);
    }

    private boolean aiReady() {
        return aiPrefs().getBoolean("consent", false) && !SecretBox.get(a, KEY_NAME).isEmpty();
    }

    private void askAi(final String q, final String rule) {
        final String provider = aiPrefs().getString("provider", AiClient.GEMINI);
        final String model = aiPrefs().getString("model", "");
        final String custom = aiPrefs().getString("baseUrl", "");
        final String key = SecretBox.get(a, KEY_NAME);
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
                    msgs.add(new String[]{"ai", fr});
                    if (speakOn()) speak(fr);
                } else {
                    String note = "\n\n⚠ هوش مصنوعی پاسخ نداد: " + fe + "\nپاسخ داده‌محور:";
                    msgs.add(new String[]{"bot", note + "\n" + rule});
                }
                redraw();
            });
        }).start();
    }

    private void showAiSettings() {
        final SharedPreferences p = aiPrefs();
        final String[] prov = {p.getString("provider", AiClient.GEMINI)};
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(18), Theme.dp(6), Theme.dp(18), Theme.dp(12));
        body.addView(a.kit.hint("مدل هوش مصنوعی را انتخاب کنید. کلید روی گوشی رمزگذاری می‌شود و هرگز در متن برنامه ذخیره نمی‌شود."),
                a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        LinearLayout provRow = a.kit.h();
        final AlertDialog[] box = new AlertDialog[1];
        String[][] options = {
                {AiClient.GEMINI, "Gemini"}, {AiClient.OPENAI, "OpenAI"},
                {AiClient.GROK, "Grok"}, {AiClient.CUSTOM, "سفارشی"}
        };
        for (String[] o : options) {
            boolean on = o[0].equals(prov[0]);
            provRow.addView(on ? a.kit.btnGold(o[1], v -> { }) : a.kit.btnGhost(o[1], Theme.GOLD, v -> { }),
                    weight());
        }
        body.addView(provRow, a.kit.lp(-1, -2));
        // Provider buttons are rebuilt on change: reopen the dialog with the chosen provider.
        for (int i = 0; i < options.length; i++) {
            final String id = options[i][0];
            View btn = provRow.getChildAt(i);
            btn.setOnClickListener(v -> {
                try {
                    if (box[0] != null) box[0].dismiss();
                } catch (Exception e) {
                    Diag.ignored("AssistantScreen:dlg", e);
                }
                saveProvider(id);
                showAiSettings();
            });
        }
        body.addView(a.kit.gap(8));
        final EditText model = a.kit.edit("نام مدل، مثلاً gemini-2.5-flash", p.getString("model", ""));
        body.addView(a.kit.hint("نام مدل"), a.kit.lp(-1, -2));
        body.addView(model, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        final EditText baseUrl = a.kit.edit("https://…/v1 (فقط برای سفارشی)", p.getString("baseUrl", ""));
        if (AiClient.CUSTOM.equals(prov[0])) {
            body.addView(a.kit.hint("آدرس سرویس"), a.kit.lp(-1, -2));
            body.addView(baseUrl, a.kit.lp(-1, -2));
            body.addView(a.kit.gap(6));
        }
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(a.kit.hint(SecretBox.get(a, KEY_NAME).isEmpty()
                ? "کلید: هنوز ثبت نشده" : "کلید: ثبت شده (برای تغییر، کلید جدید را وارد کنید)"), a.kit.lp(-1, -2));
        body.addView(key, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(8));
        final CheckBox consent = new CheckBox(a);
        consent.setText("اجازه می‌دهم خلاصهٔ عددی فروشگاه (فروش، مانده، چک و معوق‌ها) برای پاسخ هوشمند به همین سرویس فرستاده شود.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f);
        consent.setChecked(p.getBoolean("consent", false));
        body.addView(consent, a.kit.lp(-1, -2));
        body.addView(a.kit.gap(10));
        body.addView(a.kit.btnGold("✦ ذخیره", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, KEY_NAME, k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            p.edit().putString("provider", prov[0])
                    .putString("model", model.getText().toString().trim())
                    .putString("baseUrl", baseUrl.getText().toString().trim())
                    .putBoolean("consent", consent.isChecked())
                    .apply();
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            closeDialog(box[0]);
            redraw();
        }), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("⟳ آزمایش اتصال", Theme.TEAL, v -> testConnection(prov[0],
                model.getText().toString().trim(), baseUrl.getText().toString().trim(),
                key.getText().toString().trim())), a.kit.lp(-1, -2));
        body.addView(a.kit.gap(6));
        body.addView(a.kit.btnGhost("🗑 حذف کلید", Theme.DANGER, v -> {
            SecretBox.put(a, KEY_NAME, "");
            p.edit().putBoolean("consent", false).apply();
            a.kit.toast("کلید حذف شد و پاسخ هوشمند خاموش شد");
            closeDialog(box[0]);
            redraw();
        }), a.kit.lp(-1, -2));
        box[0] = a.kit.dialog("✦ تنظیم هوش مصنوعی", a.kit.scrollWrap(body, 480), true);
        box[0].show();
    }

    private void saveProvider(String id) {
        aiPrefs().edit().putString("provider", id).apply();
    }

    private void closeDialog(AlertDialog d) {
        try {
            if (d != null) d.dismiss();
        } catch (Exception e) {
            Diag.ignored("AssistantScreen:close", e);
        }
    }

    private void testConnection(String provider, String model, String base, String key) {
        String k = key.isEmpty() ? SecretBox.get(a, KEY_NAME) : key;
        a.kit.toast("در حال آزمایش اتصال…");
        new Thread(() -> {
            String msg;
            try {
                String r = AiClient.complete(provider, base, model, k, "پاسخ کوتاه بده.", "فقط بنویس: آمادهٔ کمک هستم.");
                msg = "اتصال برقرار است ✓ " + (r.length() > 60 ? r.substring(0, 60) + "…" : r);
            } catch (Exception e) {
                msg = "اتصال ناموفق: " + (e.getMessage() == null ? "خطا" : e.getMessage());
            }
            final String fm = msg;
            a.runOnUiThread(() -> a.kit.toast(fm));
        }).start();
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
                : "پاسخ‌ها از موتور داخلی و داده‌های زندهٔ فروشگاه ساخته می‌شوند؛ هیچ داده‌ای بیرون نمی‌رود مگر اینکه هوش مصنوعی را فعال کنید."),
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
                ? "✦ هوش مصنوعی فعال • " + aiPrefs().getString("model", "")
                : "✦ تنظیم هوش مصنوعی (Gemini / OpenAI / Grok)";
        return a.kit.btnGold(label, v -> showAiSettings());
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
        TextView icon = a.kit.text("✦", 28f, Theme.GOLD, true);
        card.addView(icon, a.kit.lp(-2, -2));
        LinearLayout col = a.kit.v();
        col.setPadding(Theme.dp(12), 0, Theme.dp(8), 0);
        col.addView(a.kit.text("دستیار هوشمند میلانو", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
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
