package ir.meelano.manager.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiClient;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.OfflineVoice;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.ui.Theme;

/**
 * «هوش مصنوعی» block of the Settings screen: provider, model, key, consent and test.
 * Preferences live in {@code meelano_ai}; the key is stored encrypted by {@link SecretBox}.
 */
public final class AiSettings {
    private AiSettings() { }

    static final String PREFS = "meelano_ai";
    /** Legacy single-key slot (migrated once into the per-provider slot). */
    static final String KEY_NAME = "ai_key";

    /** Each service keeps its own encrypted key, so switching provider never sends the wrong key. */
    public static String keyName(String provider) {
        return "ai_key_" + provider;
    }

    /** Moves the legacy key to the provider that was active when it was saved (runs once). */
    private static void migrate(android.content.Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("keys_migrated", false)) return;
        String legacy = SecretBox.get(c, KEY_NAME);
        if (!legacy.isEmpty()) {
            SecretBox.put(c, keyName(p.getString("provider", AiClient.GEMINI)), legacy);
            SecretBox.put(c, KEY_NAME, "");
        }
        p.edit().putBoolean("keys_migrated", true).apply();
    }

    /** The stored key for one provider («» when none). */
    public static String keyFor(android.content.Context c, String provider) {
        migrate(c);
        return SecretBox.get(c, keyName(provider));
    }

    /** Names of every service that has a key saved, in the order shown in the chooser. */
    static String configuredList(android.content.Context c) {
        StringBuilder b = new StringBuilder();
        for (String[] n : AiClient.NAMES) {
            if (!keyFor(c, n[0]).isEmpty()) {
                if (b.length() > 0) b.append("، ");
                b.append(n[1]);
            }
        }
        return b.length() == 0 ? "هیچ کلیدی ثبت نشده" : "کلید ثبت‌شده: " + b;
    }

    static SharedPreferences prefs(MainActivity a) {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the user has both a key and the consent switch on. */
    static boolean ready(MainActivity a) {
        return prefs(a).getBoolean("consent", false) && AiRouter.anyKey(a);
    }

    static void build(final MainActivity a, final LinearLayout content, final Runnable rerender) {
        final SharedPreferences p = prefs(a);
        final String provider = p.getString("provider", AiClient.GEMINI);
        final boolean hasKey = !keyFor(a, provider).isEmpty();

        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("✦ هوش مصنوعی", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("دستیار با یک مدل هوش مصنوعی پاسخ‌های دقیق‌تر و طبیعی‌تری می‌دهد. "
                + "اگر خاموش باشد یا خطا بدهد، دستیار از موتور داخلی خودش استفاده می‌کند."), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        LinearLayout status = a.kit.h();
        boolean ready = ready(a);
        status.addView(a.kit.pill(ready ? "روشن" : "خاموش", ready ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        card.addView(status, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.hint("سرویس"), a.kit.lp(-1, -2));
        String[][] options = AiClient.NAMES;
        for (int row = 0; row < options.length; row += 4) {
            LinearLayout provRow = a.kit.h();
            for (int i = row; i < Math.min(row + 4, options.length); i++) {
                final String[] o = options[i];
                boolean on = o[0].equals(provider);
                // Four per row: tighter padding and size so names like "OpenRouter" are not cut.
                android.widget.TextView pc = a.kit.chip(o[1], on, Theme.GOLD, v -> {
                    p.edit().putString("provider", o[0]).apply();
                    if (p.getString("model", "").trim().isEmpty() && !AiClient.defaultModel(o[0]).isEmpty()) {
                        p.edit().putString("model", AiClient.defaultModel(o[0])).apply();
                    }
                    rerender.run();
                });
                pc.setTextSize(10.5f);
                pc.setPadding(Theme.dp(4), Theme.dp(8), Theme.dp(4), Theme.dp(8));
                provRow.addView(pc, a.kit.wlp(1f));
            }
            card.addView(provRow, a.kit.lp(-1, -2));
            card.addView(a.kit.gap(4));
        }
        card.addView(a.kit.gap(8));

        card.addView(a.kit.hint("نام مدل (از داشبورد همان سرویس کپی کنید)"), a.kit.lp(-1, -2));
        final EditText model = a.kit.edit("مثلاً " + AiClient.defaultModel(provider),
                p.getString("model", ""));
        card.addView(model, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        final EditText baseUrl = a.kit.edit("https://…/v1", p.getString("baseUrl", ""));
        if (AiClient.CUSTOM.equals(provider)) {
            card.addView(a.kit.hint("آدرس سرویس (سازگار با OpenAI)"), a.kit.lp(-1, -2));
            card.addView(baseUrl, a.kit.lp(-1, -2));
            card.addView(a.kit.gap(6));
        }

        card.addView(a.kit.hint((hasKey ? "کلید این سرویس: ثبت شده (برای تغییر، کلید جدید را وارد کنید)"
                : "کلید این سرویس: هنوز ثبت نشده") + "\n" + configuredList(a)), a.kit.lp(-1, -2));
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(key, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final CheckBox consent = new CheckBox(a);
        consent.setText("اجازه می‌دهم خلاصهٔ عددی فروشگاه (فروش، مانده، چک و معوق‌ها) و متن پاسخ‌ها برای هوش مصنوعی و صدای طبیعی به همین سرویس فرستاده شود.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f);
        consent.setChecked(p.getBoolean("consent", false));
        card.addView(consent, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        // ---- built-in Persian voice (offline, one-time model download) ----
        card.addView(a.kit.text("🗣 صدای فارسی داخل برنامه", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean vInstalled = OfflineVoice.installed(a);
        card.addView(a.kit.hint(vInstalled
                ? "مدل صدای فارسی روی گوشی است. گفتار بدون اینترنت و بدون نصب برنامهٔ دیگر پخش می‌شود."
                : "یک بار مدل صدای فارسی (حدود ۶۰ مگابایت) از GitHub دانلود می‌شود. بعد از آن بدون اینترنت کار می‌کند."),
                a.kit.lp(-1, -2));
        final android.widget.TextView vStatus = a.kit.text(vInstalled ? "✓ آماده است" : "دانلود نشده",
                12f, vInstalled ? Theme.SUCCESS : Theme.MUTED, true);
        card.addView(vStatus, a.kit.lp(-1, -2));
        if (!vInstalled) {
            card.addView(a.kit.btnGhost("⬇ دانلود صدای فارسی", Theme.VIOLET, v -> {
                if (OfflineVoice.downloading()) { a.kit.toast("دانلود در حال انجام است"); return; }
                vStatus.setText("در حال دانلود…");
                OfflineVoice.download(a, pct -> vStatus.setText("در حال دانلود " + pct + "٪"), err -> {
                    if (err == null) {
                        a.kit.toast("صدای فارسی آماده شد");
                        if (rerender != null) rerender.run();
                    } else {
                        vStatus.setText("خطا در دانلود: " + err);
                    }
                });
            }), a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.btnGhost("🔈 شنیدن نمونهٔ صدای فارسی", Theme.SUCCESS, v ->
                    OfflineVoice.speak(a, "سلام، من دستیار هوشمند میلانو هستم. امروز چه کاری برایتان انجام دهم؟", null)),
                    a.kit.lp(-1, -2));
        }
        card.addView(a.kit.gap(10));

        card.addView(a.kit.text("🔊 صدای طبیعی", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("صدای گوشی برای فارسی کیفیت کمی دارد. با این گزینه، پاسخ‌ها با صدای طبیعی همین سرویس خوانده می‌شوند "
                + "(نیاز به اینترنت و همان کلید). اگر خطا بدهد، صدای گوشی استفاده می‌شود. Grok صدای طبیعی ندارد."),
                a.kit.lp(-1, -2));
        final CheckBox cloudTts = new CheckBox(a);
        cloudTts.setText("استفاده از صدای طبیعی سرویس انتخابی");
        cloudTts.setTextColor(Theme.TEXT);
        cloudTts.setTextSize(12.5f);
        cloudTts.setChecked(p.getBoolean("tts_cloud", true));
        card.addView(cloudTts, a.kit.lp(-1, -2));
        card.addView(a.kit.hint("مدل صدا (مدل TTS سرویس شما)"), a.kit.lp(-1, -2));
        final EditText ttsModel = a.kit.edit("مثلاً gpt-4o-mini-tts یا مدل TTS جمینای", p.getString("tts_model", ""));
        card.addView(ttsModel, a.kit.lp(-1, -2));
        card.addView(a.kit.hint("نام صدا (اختیاری)"), a.kit.lp(-1, -2));
        final EditText ttsVoice = a.kit.edit("مثلاً alloy (OpenAI) یا Kore (Gemini)", p.getString("tts_voice", ""));
        card.addView(ttsVoice, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("🔈 ذخیره و شنیدن نمونه", Theme.VIOLET, v -> {
            saveTts(p, cloudTts, ttsModel, ttsVoice);
            Speaker.speak(a, "سلام، این نمونهٔ صدای دستیار میلانو است. فروش امروز آماده است.", null);
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        card.addView(a.kit.btnGold("✦ ذخیره", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, keyName(provider), k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            p.edit().putString("model", model.getText().toString().trim())
                    .putString("baseUrl", baseUrl.getText().toString().trim())
                    .putBoolean("consent", consent.isChecked())
                    .apply();
            saveTts(p, cloudTts, ttsModel, ttsVoice);
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("⟳ آزمایش اتصال", Theme.TEAL, v -> testConnection(a, provider,
                model.getText().toString().trim(), baseUrl.getText().toString().trim(),
                key.getText().toString().trim())), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("🗑 حذف کلید و خاموش کردن", Theme.DANGER, v -> {
            SecretBox.put(a, keyName(provider), "");
            p.edit().putBoolean("consent", false).apply();
            a.kit.toast("کلید این سرویس حذف شد و هوش مصنوعی خاموش شد");
            rerender.run();
        }), a.kit.lp(-1, -2));

        content.addView(card, a.kit.lp(-1, -2));
    }

    private static void saveTts(SharedPreferences p, CheckBox on, EditText model, EditText voice) {
        p.edit().putBoolean("tts_cloud", on.isChecked())
                .putString("tts_model", model.getText().toString().trim())
                .putString("tts_voice", voice.getText().toString().trim())
                .apply();
    }

    private static void testConnection(final MainActivity a, final String provider, final String model,
                                       final String base, final String typedKey) {
        final String k = typedKey.isEmpty() ? keyFor(a, provider) : typedKey;
        a.kit.toast("در حال آزمایش اتصال…");
        new Thread(() -> {
            String msg;
            try {
                String r = AiClient.complete(provider, base, model, k, "پاسخ کوتاه بده.",
                        "فقط بنویس: آمادهٔ کمک هستم.");
                msg = "اتصال برقرار است ✓ " + (r.length() > 60 ? r.substring(0, 60) + "…" : r);
            } catch (Exception e) {
                msg = "اتصال ناموفق: " + (e.getMessage() == null ? "خطا" : e.getMessage());
            }
            final String fm = msg;
            a.runOnUiThread(() -> a.kit.toast(fm));
        }).start();
    }
}
