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
import ir.meelano.manager.core.BuiltinKeys;
import ir.meelano.manager.core.OfflineVoice;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.ui.Theme;

/**
 * «هوش مصنوعی» block of the Settings screen. Three services, one key each, consent, and the
 * Persian voice. Models are chosen per task in code, so there is nothing else to configure.
 * Preferences live in {@code meelano_ai}; keys are stored encrypted by {@link SecretBox}.
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
    private static void migrate(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("keys_migrated", false)) return;
        String legacy = SecretBox.get(c, KEY_NAME);
        if (!legacy.isEmpty()) {
            SecretBox.put(c, keyName(p.getString("provider", AiClient.GEMINI)), legacy);
            SecretBox.put(c, KEY_NAME, "");
        }
        p.edit().putBoolean("keys_migrated", true).apply();
    }

    /** The key for one provider: the user's own key when saved, otherwise the built-in key. */
    public static String keyFor(Context c, String provider) {
        migrate(c);
        String saved = SecretBox.get(c, keyName(provider));
        return saved.isEmpty() ? BuiltinKeys.get(provider) : saved;
    }

    static SharedPreferences prefs(Context a) {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the user has both a key and the consent switch on. */
    static boolean ready(MainActivity a) {
        return prefs(a).getBoolean("consent", false) && AiRouter.anyKey(a);
    }

    private static boolean isKnown(String provider) {
        for (String[] n : AiClient.NAMES) if (n[0].equals(provider)) return true;
        return false;
    }

    static void build(final MainActivity a, final LinearLayout content, final Runnable rerender) {
        final SharedPreferences p = prefs(a);
        String saved = p.getString("provider", AiClient.GEMINI);
        final String provider = isKnown(saved) ? saved : AiClient.GEMINI;
        final boolean hasKey = !keyFor(a, provider).isEmpty();

        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("✦ هوش مصنوعی", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("سه سرویس کافی است: Gemini برای گفتار و پاسخ‌های روزمره، DeepSeek برای تحلیل، "
                + "Groq برای پاسخ سریع. اگر یکی خطا بدهد، بقیه جایگزین می‌شوند."), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        boolean ready = ready(a);
        card.addView(a.kit.pill(ready ? "روشن" : "خاموش", ready ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        card.addView(a.kit.gap(8));

        LinearLayout chips = a.kit.h();
        for (final String[] o : AiClient.NAMES) {
            android.widget.TextView pc = a.kit.chip(o[1], o[0].equals(provider), Theme.GOLD, v -> {
                p.edit().putString("provider", o[0]).apply();
                rerender.run();
            });
            pc.setTextSize(11f * Theme.fontScale());
            chips.addView(pc, a.kit.wlp(1f));
        }
        card.addView(chips, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.hint(hasKey
                ? "کلید این سرویس ثبت شده است؛ برای تغییر، کلید جدید را وارد کنید."
                : "کلید این سرویس هنوز ثبت نشده است."), a.kit.lp(-1, -2));
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(key, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final CheckBox consent = new CheckBox(a);
        consent.setText("اجازه می‌دهم خلاصهٔ عددی فروشگاه (فروش، مانده، چک و معوق‌ها) و متن پاسخ‌ها برای هوش مصنوعی و صدای طبیعی به همین سرویس فرستاده شود.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f * Theme.fontScale());
        consent.setChecked(p.getBoolean("consent", false));
        card.addView(consent, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        // ---- Persian voice: built-in, offline, one-time download ----
        card.addView(a.kit.text("🗣 صدای فارسی (پیش‌فرض)", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean vInstalled = OfflineVoice.installed(a);
        final boolean vBest = OfflineVoice.bestInstalled(a);
        card.addView(a.kit.hint(vBest
                ? "صدای فارسی روی گوشی است و بدون اینترنت پخش می‌شود. این زبان پیش‌فرض صدای دستیار است."
                : (vInstalled
                ? "صدای زن فارسی هنوز دانلود نشده است؛ تا آن زمان صدای فعلی خوانده می‌شود."
                : "یک بار صدای فارسی (حدود ۱۳۰ مگابایت) از GitHub دانلود می‌شود. بعد از آن بدون اینترنت کار می‌کند.")),
                a.kit.lp(-1, -2));
        final android.widget.TextView vStatus = a.kit.text(vBest ? "✓ آماده است"
                : (vInstalled ? "صدای فعلی؛ صدای زن را دانلود کنید" : "دانلود نشده"),
                12f, vBest ? Theme.SUCCESS : Theme.MUTED, true);
        card.addView(vStatus, a.kit.lp(-1, -2));
        if (!vBest) {
            card.addView(a.kit.btnGhost(vInstalled ? "⬇ دانلود صدای زن فارسی" : "⬇ دانلود صدای زن فارسی",
                    Theme.VIOLET, v -> {
                if (OfflineVoice.downloading()) { a.kit.toast("دانلود در حال انجام است"); return; }
                vStatus.setText("در حال دانلود…");
                OfflineVoice.download(a, pct -> vStatus.setText("در حال دانلود " + pct + "٪"), err -> {
                    if (err == null) {
                        // Persian becomes the default voice as soon as it is installed.
                        p.edit().putBoolean("speak", true).apply();
                        a.kit.toast("صدای فارسی آماده شد");
                        if (rerender != null) rerender.run();
                    } else {
                        vStatus.setText("خطا در دانلود: " + err);
                    }
                });
            }), a.kit.lp(-1, -2));
        } else {
            card.addView(a.kit.btnGhost("🔈 شنیدن نمونهٔ صدای فارسی", Theme.SUCCESS, v -> {
                a.kit.toast("در حال آماده‌سازی صدا…");
                Speaker.speak(a, "سلام، من دستیار هوشمند میلانو هستم. امروز چه کاری برایتان انجام دهم؟", null);
            }), a.kit.lp(-1, -2));
        }
        card.addView(a.kit.gap(8));

        card.addView(a.kit.text("🔊 صدای طبیعی Gemini (اختیاری)", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("به اینترنت و کلید Gemini نیاز دارد و متن پاسخ‌ها را به سرویس می‌فرستد. "
                + "اگر خاموش باشد، همیشه صدای داخلی فارسی خوانده می‌شود."), a.kit.lp(-1, -2));
        final CheckBox cloudTts = new CheckBox(a);
        cloudTts.setText("استفاده از صدای طبیعی Gemini");
        cloudTts.setTextColor(Theme.TEXT);
        cloudTts.setTextSize(12.5f * Theme.fontScale());
        cloudTts.setChecked(p.getBoolean("tts_cloud", false));
        card.addView(cloudTts, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        card.addView(a.kit.btnGold("⚡ اتصال و فعال‌سازی هوش مصنوعی", v -> connectAndActivate(a, provider,
                key.getText().toString().trim(), p, rerender)), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGold("✦ ذخیره", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, keyName(provider), k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            p.edit().putBoolean("consent", consent.isChecked())
                    .putBoolean("tts_cloud", cloudTts.isChecked())
                    .apply();
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("⟳ آزمایش اتصال", Theme.GOLD, v -> testConnection(a, provider,
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

    /**
     * One-tap connection: stores the typed key (or keeps the saved one), checks it with a short
     * request, and turns the assistant on only when the service answers.
     */
    private static void connectAndActivate(final MainActivity a, final String provider, final String typedKey,
                                           final SharedPreferences p, final Runnable rerender) {
        final String k = typedKey.isEmpty() ? keyFor(a, provider) : typedKey;
        if (k.isEmpty()) {
            a.kit.toast("ابتدا کلید API را وارد کنید");
            return;
        }
        if (!typedKey.isEmpty() && !SecretBox.put(a, keyName(provider), typedKey)) {
            a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
            return;
        }
        a.kit.toast("در حال اتصال به " + AiClient.nameOf(provider) + "…");
        new Thread(() -> {
            String msg;
            boolean ok = false;
            try {
                AiClient.complete(provider, "", AiClient.modelFor(provider, "fast"), k,
                        "پاسخ کوتاه بده.", "فقط بنویس: آمادهٔ کمک هستم.");
                ok = true;
                msg = "اتصال برقرار شد و هوش مصنوعی فعال است ✓";
            } catch (Exception e) {
                msg = "اتصال ناموفق: " + (e.getMessage() == null ? "خطا" : e.getMessage());
            }
            final boolean fok = ok;
            final String fm = msg;
            a.runOnUiThread(() -> {
                if (fok) {
                    p.edit().putString("provider", provider).putBoolean("consent", true).apply();
                }
                a.kit.toast(fm);
                if (rerender != null) rerender.run();
            });
        }).start();
    }

    private static void testConnection(final MainActivity a, final String provider, final String typedKey) {
        final String k = typedKey.isEmpty() ? keyFor(a, provider) : typedKey;
        a.kit.toast("در حال آزمایش اتصال…");
        new Thread(() -> {
            String msg;
            try {
                String r = AiClient.complete(provider, "", AiClient.modelFor(provider, "fast"), k,
                        "پاسخ کوتاه بده.", "فقط بنویس: آمادهٔ کمک هستم.");
                msg = "اتصال برقرار است ✓ " + (r.length() > 60 ? r.substring(0, 60) + "…" : r);
            } catch (Exception e) {
                msg = "اتصال ناموفق: " + (e.getMessage() == null ? "خطا" : e.getMessage());
            }
            final String fm = msg;
            a.runOnUiThread(() -> a.kit.toast(fm));
        }).start();
    }
}
