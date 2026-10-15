package ir.meelano.manager.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiClient;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.ui.Theme;

/**
 * «هوش مصنوعی» block of the Settings screen: provider, model, key, consent and test.
 * Preferences live in {@code meelano_ai}; the key is stored encrypted by {@link SecretBox}.
 */
final class AiSettings {
    private AiSettings() { }

    static final String PREFS = "meelano_ai";
    static final String KEY_NAME = "ai_key";

    static SharedPreferences prefs(MainActivity a) {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the user has both a key and the consent switch on. */
    static boolean ready(MainActivity a) {
        return prefs(a).getBoolean("consent", false) && !SecretBox.get(a, KEY_NAME).isEmpty();
    }

    static void build(final MainActivity a, final LinearLayout content, final Runnable rerender) {
        final SharedPreferences p = prefs(a);
        final String provider = p.getString("provider", AiClient.GEMINI);
        final boolean hasKey = !SecretBox.get(a, KEY_NAME).isEmpty();

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
        LinearLayout provRow = a.kit.h();
        String[][] options = {
                {AiClient.GEMINI, "Gemini"}, {AiClient.OPENAI, "OpenAI"},
                {AiClient.GROK, "Grok"}, {AiClient.CUSTOM, "سفارشی"}
        };
        for (final String[] o : options) {
            boolean on = o[0].equals(provider);
            provRow.addView(a.kit.chip(o[1], on, Theme.GOLD, v -> {
                p.edit().putString("provider", o[0]).apply();
                rerender.run();
            }), a.kit.wlp(1f));
        }
        card.addView(provRow, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.hint("نام مدل (از داشبورد همان سرویس کپی کنید)"), a.kit.lp(-1, -2));
        final EditText model = a.kit.edit("مثلاً نام مدل Gemini / GPT / Grok", p.getString("model", ""));
        card.addView(model, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        final EditText baseUrl = a.kit.edit("https://…/v1", p.getString("baseUrl", ""));
        if (AiClient.CUSTOM.equals(provider)) {
            card.addView(a.kit.hint("آدرس سرویس (سازگار با OpenAI)"), a.kit.lp(-1, -2));
            card.addView(baseUrl, a.kit.lp(-1, -2));
            card.addView(a.kit.gap(6));
        }

        card.addView(a.kit.hint(hasKey ? "کلید: ثبت شده (برای تغییر، کلید جدید را وارد کنید)"
                : "کلید: هنوز ثبت نشده"), a.kit.lp(-1, -2));
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(key, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final CheckBox consent = new CheckBox(a);
        consent.setText("اجازه می‌دهم خلاصهٔ عددی فروشگاه (فروش، مانده، چک و معوق‌ها) برای پاسخ هوشمند به همین سرویس فرستاده شود.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f);
        consent.setChecked(p.getBoolean("consent", false));
        card.addView(consent, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        card.addView(a.kit.btnGold("✦ ذخیره", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, KEY_NAME, k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            p.edit().putString("model", model.getText().toString().trim())
                    .putString("baseUrl", baseUrl.getText().toString().trim())
                    .putBoolean("consent", consent.isChecked())
                    .apply();
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("⟳ آزمایش اتصال", Theme.TEAL, v -> testConnection(a, provider,
                model.getText().toString().trim(), baseUrl.getText().toString().trim(),
                key.getText().toString().trim())), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("🗑 حذف کلید و خاموش کردن", Theme.DANGER, v -> {
            SecretBox.put(a, KEY_NAME, "");
            p.edit().putBoolean("consent", false).apply();
            a.kit.toast("کلید حذف شد و هوش مصنوعی خاموش شد");
            rerender.run();
        }), a.kit.lp(-1, -2));

        content.addView(card, a.kit.lp(-1, -2));
    }

    private static void testConnection(final MainActivity a, final String provider, final String model,
                                       final String base, final String typedKey) {
        final String k = typedKey.isEmpty() ? SecretBox.get(a, KEY_NAME) : typedKey;
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
