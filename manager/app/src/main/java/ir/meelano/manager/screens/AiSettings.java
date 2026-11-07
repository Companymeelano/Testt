package ir.meelano.manager.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.View;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiBackendClient;
import ir.meelano.manager.core.OfflineAsr;
import ir.meelano.manager.core.OfflineVoice;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.ui.Theme;

/**
 * Settings for the AvalAI-only HTTPS proxy and on-device Persian speech.
 * Provider credentials are never stored in Android; legacy personal keys are purged on upgrade.
 */
public final class AiSettings {
    private AiSettings() { }

    static final String PREFS = "meelano_ai";
    /** Consent scope now explicitly covers the secure AvalAI-only proxy. */
    private static final int CONSENT_VERSION = 7;
    private static final String DIRECT_KEYS_PURGED = "direct_provider_keys_purged_v1";
    private static final String[] LEGACY_PROVIDER_IDS = {
            "openai", "gemini", "deepseek", "groq", "grok", "openrouter", "gapgpt",
            "cerebras", "huggingface", "custom"
    };

    static SharedPreferences prefs(Context a) {
        SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getInt("consent_version", 0) != CONSENT_VERSION) {
            // The provider and voice-processing scope changed; require fresh consent.
            p.edit().putBoolean("consent", false)
                    .putInt("consent_version", CONSENT_VERSION).apply();
        }
        purgeLegacyDirectSettings(a, p);
        return p;
    }

    /** Clears device-held provider credentials and old cloud-TTS options from earlier builds. */
    private static void purgeLegacyDirectSettings(Context c, SharedPreferences p) {
        if (p.getBoolean(DIRECT_KEYS_PURGED, false)) return;
        boolean cleared = SecretBox.put(c, "ai_key", "");
        for (String provider : LEGACY_PROVIDER_IDS) {
            if (!SecretBox.put(c, "ai_key_" + provider, "")) cleared = false;
        }
        if (cleared) {
            SharedPreferences.Editor edit = p.edit().putBoolean(DIRECT_KEYS_PURGED, true)
                    .putBoolean("tts_cloud", false)
                    .remove("provider").remove("baseUrl").remove("tts_model").remove("tts_voice")
                    .remove("ai_backend_enabled").remove("ai_advanced_connection_open");
            for (String provider : LEGACY_PROVIDER_IDS) edit.remove("ai_api_tested_" + provider);
            edit.apply();
        }
    }

    /** Consent and an active server session are both required for any online AI request. */
    static boolean ready(MainActivity a) {
        SharedPreferences p = prefs(a);
        return p.getBoolean("consent", false) && AiBackendClient.hasSession(a);
    }

    static void build(final MainActivity a, final LinearLayout content, final Runnable rerender) {
        final SharedPreferences p = prefs(a);
        final boolean hasSession = AiBackendClient.hasSession(a);

        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("✦ دستیار هوشمند AvalAI", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint(hasSession
                ? "نشست HTTPS آماده است. کلید AvalAI فقط روی سرور نگه‌داری می‌شود و برنامه آن را دریافت نمی‌کند."
                : "برای استفاده از AvalAI، ابتدا سرور امن را تنظیم و وارد شوید. کلید API را در برنامه وارد نکنید؛ فقط روی secret manager سرور قرار دهید."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        boolean ready = ready(a);
        card.addView(a.kit.pill(ready ? "روشن" : "خاموش", ready ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        card.addView(a.kit.gap(8));

        final CheckBox consent = new CheckBox(a);
        consent.setText("با رضایت من، متن پرسش، حداکثر ۲۰ پیام اخیر همین گفتگو و فقط زمینه/نتیجهٔ گزارش یا ابزارِ ازپیش‌تعریف‌شده و فقط‌خواندنی آتیران به پروکسی امن فرستاده می‌شود. در پیکربندی این برنامه، پروکسی فقط AvalAI را صدا می‌زند و در صورت خطا درخواست را متوقف می‌کند؛ اطلاعات به provider دیگری منتقل نمی‌شود. اطلاعات تماس، نشانی و احراز هویت حذف می‌شوند و کل جدول یا پایگاه‌داده ارسال نمی‌شود. پاسخ صوتی با صدای فارسی محلی پخش می‌شود و به سرویس گفتار ابری فرستاده نمی‌شود. نگهداری داده در AvalAI تابع سیاست همان سرویس است.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f * Theme.fontScale());
        consent.setChecked(p.getBoolean("consent", false));
        card.addView(consent, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        // ---- Offline speech recognition (listening) ----
        card.addView(a.kit.text("🎙 شنیدن فارسی آفلاین", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean asrOk = OfflineAsr.installed(a);
        card.addView(a.kit.hint(asrOk
                ? "گفتگوی زنده با صدای شما بدون اینترنت شنیده می‌شود."
                : "یک بار مدل شنیدن فارسی (حدود ۲۰۰ مگابایت) دانلود می‌شود. بعد از آن بدون اینترنت کار می‌کند."),
                a.kit.lp(-1, -2));
        final android.widget.TextView asrStatus = a.kit.text(asrOk ? "✓ آماده است" : "دانلود نشده",
                12f, asrOk ? Theme.SUCCESS : Theme.MUTED, true);
        card.addView(asrStatus, a.kit.lp(-1, -2));
        if (!asrOk) {
            card.addView(a.kit.btnGhost("⬇ دانلود شنیدن فارسی", Theme.VIOLET, v -> {
                if (OfflineAsr.downloading()) { a.kit.toast("دانلود در حال انجام است"); return; }
                asrStatus.setText("در حال دانلود…");
                OfflineAsr.download(a, pct -> asrStatus.setText(
                        ir.meelano.manager.core.HomeNumberFormatter.rtlText(
                                "در حال دانلود " + ir.meelano.manager.core.HomeNumberFormatter.percent(pct))),
                        (t, err) -> {
                    if (err == null) {
                        a.kit.toast("شنیدن فارسی آفلاین آماده شد");
                        if (rerender != null) rerender.run();
                    } else {
                        asrStatus.setText("خطا در دانلود: " + err);
                    }
                });
            }), a.kit.lp(-1, -2));
        }
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
                OfflineVoice.download(a, pct -> vStatus.setText(
                        ir.meelano.manager.core.HomeNumberFormatter.rtlText(
                                "در حال دانلود " + ir.meelano.manager.core.HomeNumberFormatter.percent(pct))),
                        err -> {
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

        card.addView(a.kit.btnGold("✦ ذخیرهٔ رضایت و تنظیمات گفتار", v -> {
            p.edit().putBoolean("consent", consent.isChecked())
                    .putInt("consent_version", CONSENT_VERSION)
                    .apply();
            a.kit.toast("رضایت و تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));

        content.addView(card, a.kit.lp(-1, -2));

        buildBackendCard(a, content, rerender);
    }

    /** Configures the separately deployed PHP proxy without embedding its model key in the APK. */
    private static void buildBackendCard(final MainActivity a, LinearLayout content, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("🔐 سرور AI امن (PHP)", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("این پروکسی از سرور آتیران جداست و فقط AvalAI را صدا می‌زند؛ اگر AvalAI آماده نباشد، درخواست متوقف می‌شود. کلید AvalAI فقط در secret manager هاست می‌ماند و برنامه آن را دریافت نمی‌کند. برنامه فقط با HTTPS وصل می‌شود و نشست را رمزنگاری‌شده نگه می‌دارد."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.hint("نشانی زیر، آدرس پروکسی PHP خودتان است؛ آدرس API و کلید AvalAI باید در محیط امن هاست تنظیم شوند، نه در برنامه."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final EditText endpoint = a.kit.edit("https://your-host/ai-api/api.php", AiBackendClient.savedEndpoint(a));
        endpoint.setSingleLine(true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(endpoint, a.kit.lp(-1, -2));
        final EditText username = a.kit.edit("نام کاربری حساب سرور AI", AiBackendClient.savedUser(a));
        username.setSingleLine(true);
        card.addView(username, a.kit.lp(-1, -2));
        final EditText password = a.kit.edit("رمز ورود سرور AI (ذخیره نمی‌شود)", "", true);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(password, a.kit.lp(-1, -2));

        final boolean hasSession = AiBackendClient.hasSession(a);
        String status = hasSession ? "✓ نشست امن فعال است؛ رمز ورود ذخیره نشده است."
                : "پروکسی AvalAI آمادهٔ تنظیم است؛ تا استقرار PHP و ورود امن اتصال آنلاین برقرار نمی‌شود.";
        card.addView(a.kit.hint(status), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGhost("ذخیره و اعتبارسنجی نشانی HTTPS", Theme.VIOLET, v -> {
            try {
                AiBackendClient.saveEndpoint(a, endpoint.getText().toString(), username.getText().toString());
                a.kit.toast("نشانی امن ذخیره شد");
                if (rerender != null) rerender.run();
            } catch (IllegalArgumentException e) {
                a.kit.toast(e.getMessage() == null ? "نشانی HTTPS معتبر نیست" : e.getMessage());
            }
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        final android.widget.Button[] loginButton = new android.widget.Button[1];
        loginButton[0] = a.kit.btnGold("ورود امن به پروکسی AI", v -> {
            final String url = endpoint.getText().toString();
            final String user = username.getText().toString();
            final String pass = password.getText().toString();
            loginButton[0].setEnabled(false);
            a.kit.toast("در حال ورود امن به سرور AI…");
            new Thread(() -> {
                String message;
                boolean success = false;
                try {
                    AiBackendClient.login(a, url, user, pass);
                    success = true;
                    message = "ورود امن AvalAI موفق شد؛ برای ارسال داده رضایت را ثبت کنید.";
                } catch (Exception e) {
                    message = (e instanceof IllegalArgumentException || e instanceof java.io.IOException)
                            && e.getMessage() != null ? e.getMessage() : "ورود به سرور AI ناموفق بود";
                }
                final boolean ok = success;
                final String result = message;
                a.runOnUiThread(() -> {
                    loginButton[0].setEnabled(true);
                    a.kit.toast(result);
                    if (rerender != null) rerender.run();
                });
            }).start();
        });
        card.addView(loginButton[0], a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        View test = a.kit.btnGhost("آزمایش پاسخ سرور (بدون دادهٔ فروشگاه)", Theme.GOLD, v -> {
            if (!AiBackendClient.hasSession(a)) {
                a.kit.toast("ابتدا به سرور AI وارد شوید");
                return;
            }
            a.kit.toast("در حال آزمایش ارتباط با مدل…");
            new Thread(() -> {
                String message;
                try {
                    AiBackendClient.complete(a, "در یک جمله کوتاه فارسی پاسخ بده.", "فقط بنویس: اتصال آماده است.");
                    message = "پاسخ آزمایشی دریافت شد ✓";
                } catch (Exception e) {
                    message = e.getMessage() == null ? "آزمایش سرور ناموفق بود" : e.getMessage();
                }
                final String result = message;
                a.runOnUiThread(() -> a.kit.toast(result));
            }).start();
        });
        test.setVisibility(hasSession ? View.VISIBLE : View.GONE);
        card.addView(test, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        View logout = a.kit.btnGhost("خروج و حذف نشست امن", Theme.DANGER, v -> {
            AiBackendClient.logout(a);
            a.kit.toast("نشست AI از این دستگاه حذف شد");
            if (rerender != null) rerender.run();
        });
        logout.setVisibility(hasSession ? View.VISIBLE : View.GONE);
        card.addView(logout, a.kit.lp(-1, -2));
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

}
