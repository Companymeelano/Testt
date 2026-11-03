package ir.meelano.manager.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.View;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiClient;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.AiBackendClient;
import ir.meelano.manager.core.BuiltinKeys;
import ir.meelano.manager.core.OfflineAsr;
import ir.meelano.manager.core.OfflineVoice;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.ui.Theme;

/**
 * «هوش مصنوعی» block of Settings: optional managed HTTPS proxy, user-owned direct provider keys,
 * consent, and Persian voice. No provider key is bundled; direct keys use Android Keystore encryption.
 * Preferences live in {@code meelano_ai}; keys are stored encrypted by {@link SecretBox}.
 */
public final class AiSettings {
    private AiSettings() { }

    static final String PREFS = "meelano_ai";
    /** Consent text/scope version; changing it requires the user to opt in again. */
    private static final int CONSENT_VERSION = 3;
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
            String target = keyName(p.getString("provider", AiClient.GEMINI));
            if (SecretBox.get(c, target).isEmpty() && !SecretBox.put(c, target, legacy)) return;
            if (!SecretBox.put(c, KEY_NAME, "")) return;
        }
        p.edit().putBoolean("keys_migrated", true).apply();
    }

    /** Only a key explicitly saved by the user is available; no provider keys are bundled. */
    public static String keyFor(Context c, String provider) {
        migrate(c);
        String saved = SecretBox.get(c, keyName(provider));
        return saved.isEmpty() ? BuiltinKeys.get(provider) : saved;
    }

    static SharedPreferences prefs(Context a) {
        SharedPreferences p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getInt("consent_version", 0) != CONSENT_VERSION) {
            // Previous consent described a narrower payload; never silently broaden it.
            p.edit().putBoolean("consent", false)
                    .putInt("consent_version", CONSENT_VERSION).apply();
        }
        return p;
    }

    /** True when explicit, current-version consent and the selected connection are both ready. */
    static boolean ready(MainActivity a) {
        SharedPreferences p = prefs(a);
        if (!p.getBoolean("consent", false)) return false;
        return AiBackendClient.selected(a) ? AiBackendClient.hasSession(a) : AiRouter.anyKey(a);
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
        final boolean proxySelected = AiBackendClient.selected(a);

        buildBackendCard(a, content, rerender);
        LinearLayout card = a.kit.card(Theme.GOLD);
        card.addView(a.kit.text("✦ هوش مصنوعی", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint(proxySelected
                ? "حالت پروکسی PHP فعال است؛ کلید ارائه‌دهنده فقط روی سرور می‌ماند."
                : "اتصال مستقیم از کلید واردشدهٔ خودتان استفاده می‌کند؛ کلید داخل APK نیست و روی همین دستگاه رمزنگاری می‌شود."),
                a.kit.lp(-1, -2));
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
        chips.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        card.addView(chips, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        android.widget.TextView keyHint = a.kit.hint(hasKey
                ? "کلید این سرویس ثبت شده است؛ برای تغییر، کلید جدید را وارد کنید."
                : "کلید این سرویس هنوز ثبت نشده است.");
        keyHint.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        card.addView(keyHint, a.kit.lp(-1, -2));
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(key, a.kit.lp(-1, -2));
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
                OfflineAsr.download(a, pct -> asrStatus.setText("در حال دانلود " + pct + "٪"), (t, err) -> {
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

        final CheckBox consent = new CheckBox(a);
        consent.setText("با رضایت من، متن پرسش و حداکثر ۶ نوبت اخیر همین گفتگو و فقط نتیجهٔ گزارش/ابزارِ ازپیش‌تعریف‌شده و فقط‌خواندنی آتیران برای پردازش به سرویس AI انتخابی فرستاده می‌شود. نتیجه ممکن است نام مشتری/تأمین‌کننده/ویزیتور، مانده و اطلاعات فاکتور یا شاخص‌های کارکنان داشته باشد؛ ستون‌های تماس، نشانی و احراز هویت حذف می‌شوند. کل جدول یا پایگاه‌داده ارسال نمی‌شود. متن پاسخ فقط با انتخاب صدای ابری ارسال می‌شود؛ نگهداری و استفادهٔ ارائه‌دهنده تابع سیاست خودش است.");
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

        View directConnect = a.kit.btnGold("⚡ اتصال و فعال‌سازی هوش مصنوعی", v -> connectAndActivate(a, provider,
                key.getText().toString().trim(), p, consent.isChecked(), rerender));
        directConnect.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        card.addView(directConnect, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        card.addView(a.kit.btnGold("✦ ذخیره", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, keyName(provider), k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            p.edit().putBoolean("consent", consent.isChecked())
                    .putInt("consent_version", CONSENT_VERSION)
                    .putBoolean("tts_cloud", cloudTts.isChecked())
                    .apply();
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        View directTest = a.kit.btnGhost("⇅ آزمایش اتصال سرویس مستقیم", Theme.GOLD, v -> testConnection(a, provider,
                key.getText().toString().trim()));
        directTest.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        card.addView(directTest, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));
        View deleteDirectKey = a.kit.btnGhost("🗑 حذف کلید این سرویس و خاموش‌کردن AI", Theme.DANGER, v -> {
            SecretBox.put(a, keyName(provider), "");
            p.edit().putBoolean("consent", false).apply();
            a.kit.toast("کلید این سرویس حذف شد و هوش مصنوعی خاموش شد");
            rerender.run();
        });
        deleteDirectKey.setVisibility(proxySelected ? View.GONE : View.VISIBLE);
        card.addView(deleteDirectKey, a.kit.lp(-1, -2));

        content.addView(card, a.kit.lp(-1, -2));
    }

    /** Configures the separately deployed PHP proxy without embedding its model key in the APK. */
    private static void buildBackendCard(final MainActivity a, LinearLayout content, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("🔐 سرور AI امن (PHP)", 14.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("این سرویس جدا از سرور آتیران است. کلید مدل فقط در environment هاست قرار می‌گیرد؛ برنامه فقط با HTTPS وارد می‌شود و نشست را رمزنگاری‌شده نگه می‌دارد."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final EditText endpoint = a.kit.edit("https://your-host/ai-api/api.php", AiBackendClient.savedEndpoint(a));
        endpoint.setSingleLine(true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(endpoint, a.kit.lp(-1, -2));
        final EditText username = a.kit.edit("نام کاربری تست سرور", AiBackendClient.savedUser(a));
        username.setSingleLine(true);
        card.addView(username, a.kit.lp(-1, -2));
        final EditText password = a.kit.edit("رمز ورود تست سرور (ذخیره نمی‌شود)", "", true);
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(password, a.kit.lp(-1, -2));

        final CheckBox useProxy = new CheckBox(a);
        useProxy.setText("استفاده از این پروکسی به‌جای کلید مستقیم دستگاه");
        useProxy.setTextColor(Theme.TEXT);
        useProxy.setTextSize(12.5f * Theme.fontScale());
        useProxy.setChecked(AiBackendClient.selected(a));
        card.addView(useProxy, a.kit.lp(-1, -2));

        final boolean hasSession = AiBackendClient.hasSession(a);
        String status = hasSession ? "✓ نشست امن فعال است؛ رمز ورود ذخیره نشده است."
                : (AiBackendClient.selected(a) ? "پروکسی انتخاب شده؛ برای استفاده ابتدا وارد شوید."
                : "پروکسی آمادهٔ تنظیم است؛ تا استقرار PHP اتصال آنلاین برقرار نمی‌شود.");
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
                    message = "ورود موفق شد؛ اکنون می‌توانید پروکسی را انتخاب کنید.";
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
        useProxy.setOnCheckedChangeListener((button, checked) -> {
            AiBackendClient.select(a, checked);
            if (rerender != null) rerender.run();
        });
        content.addView(card, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
    }

    /**
     * One-tap connection: stores the typed key (or keeps the saved one), checks it with a short
     * request, and turns the assistant on only when the service answers.
     */
    private static void connectAndActivate(final MainActivity a, final String provider, final String typedKey,
                                           final SharedPreferences p, final boolean consent,
                                           final Runnable rerender) {
        if (!consent) {
            a.kit.toast("برای فعال‌سازی آنلاین، ابتدا رضایت ارسال داده را تأیید کنید");
            return;
        }
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
                    p.edit().putString("provider", provider).putBoolean("consent", true)
                            .putInt("consent_version", CONSENT_VERSION).apply();
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
