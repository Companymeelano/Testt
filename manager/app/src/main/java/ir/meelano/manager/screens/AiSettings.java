package ir.meelano.manager.screens;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.AiClient;
import ir.meelano.manager.core.AiProviders;
import ir.meelano.manager.core.AiRouter;
import ir.meelano.manager.core.OfflineAsr;
import ir.meelano.manager.core.OfflineVoice;
import ir.meelano.manager.core.SecretBox;
import ir.meelano.manager.core.Speaker;
import ir.meelano.manager.ui.Theme;

/**
 * «هوش مصنوعی» block of the Settings screen.
 *
 * <p>Every registered service ({@link AiProviders}) can be the primary or a fallback. For the
 * selected primary the user sets the model, the API key (stored encrypted by {@link SecretBox}),
 * runs an independent connection test and sees a live status pill. A fallback-order editor,
 * voice/language/behaviour preferences, the offline Persian speech models and the data-consent
 * switch complete the block. No key is ever embedded in the app.
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
            SecretBox.put(c, keyName(p.getString("provider", AiProviders.defaultPrimary())), legacy);
            SecretBox.put(c, KEY_NAME, "");
        }
        p.edit().putBoolean("keys_migrated", true).apply();
    }

    /** The key for one provider: only the user's own key (no embedded fallback). */
    public static String keyFor(Context c, String provider) {
        migrate(c);
        return SecretBox.get(c, keyName(provider));
    }

    static SharedPreferences prefs(Context a) {
        return a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the user has both a key and the consent switch on. */
    static boolean ready(MainActivity a) {
        return prefs(a).getBoolean("consent", false) && AiRouter.anyKey(a);
    }

    static void build(final MainActivity a, final LinearLayout content, final Runnable rerender) {
        final SharedPreferences p = prefs(a);
        final String provider = AiRouter.primary(a);

        // ---- header: on/off + primary picker + status ----
        LinearLayout head = a.kit.card(Theme.GOLD);
        head.addView(a.kit.text("✦ هوش مصنوعی", 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
        head.addView(a.kit.hint("سرویس اصلی را انتخاب کنید، مدل و کلید آن را وارد کنید و اتصال را آزمایش کنید. "
                + "در صورت خطا، سرویس‌های جایگزین به‌ترتیبی که تعیین می‌کنید وارد عمل می‌شوند. "
                + "هیچ کلیدی در برنامه جاسازی نشده است؛ کلید فقط از ورود شما ذخیره می‌شود."), a.kit.lp(-1, -2));
        head.addView(a.kit.gap(8));
        boolean ready = ready(a);
        head.addView(a.kit.pill(ready ? "روشن" : "خاموش", ready ? Theme.SUCCESS : Theme.MUTED), a.kit.lp(-2, -2));
        head.addView(a.kit.gap(10));

        LinearLayout primaryRow = a.kit.h();
        primaryRow.addView(a.kit.text("سرویس اصلی: " + AiClient.nameOf(provider), 13.5f, Theme.TEXT, true), a.kit.wlp(1f));
        primaryRow.addView(a.kit.btnGhost("تغییر سرویس", Theme.GOLD, v -> pickPrimary(a, rerender)), a.kit.lp(-2, -2));
        head.addView(primaryRow, a.kit.lp(-1, -2));
        head.addView(a.kit.gap(8));
        head.addView(statusPill(a, provider), a.kit.lp(-2, -2));
        head.addView(a.kit.gap(4));
        head.addView(a.kit.hint(statusDetail(a, provider)), a.kit.lp(-1, -2));
        content.addView(head, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- per-service config: model + key + test ----
        content.addView(serviceCard(a, provider, p, rerender), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- fallback order ----
        content.addView(fallbackCard(a, p, rerender), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- voice / language / behaviour ----
        content.addView(voiceCard(a, p, rerender), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- offline speech models ----
        content.addView(asrCard(a, rerender), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));
        content.addView(ttsCard(a, p, rerender), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(10));

        // ---- consent + activate ----
        LinearLayout consentCard = a.kit.card(Theme.GOLD);
        final CheckBox consent = new CheckBox(a);
        consent.setText("اجازه می‌دهم خلاصهٔ عددی فروشگاه (فروش، مانده، چک و معوق‌ها) و متن پاسخ‌ها برای هوش مصنوعی و صدای طبیعی به همین سرویس فرستاده شود.");
        consent.setTextColor(Theme.TEXT);
        consent.setTextSize(12.5f * Theme.fontScale());
        consent.setChecked(p.getBoolean("consent", false));
        consentCard.addView(consent, a.kit.lp(-1, -2));
        consentCard.addView(a.kit.gap(10));
        consentCard.addView(a.kit.btnGold("⚡ اتصال و فعال‌سازی هوش مصنوعی", v -> {
            String k = keyFor(a, provider);
            if (k.isEmpty()) { a.kit.toast("ابتدا کلید سرویس اصلی را وارد و ذخیره کنید"); return; }
            activate(a, provider, k, consent.isChecked(), p, rerender);
        }), a.kit.lp(-1, -2));
        consentCard.addView(a.kit.gap(6));
        consentCard.addView(a.kit.btnGhost("✦ ذخیرهٔ رضایت", Theme.GOLD, v -> {
            p.edit().putBoolean("consent", consent.isChecked()).apply();
            a.kit.toast("تنظیمات هوش مصنوعی ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        content.addView(consentCard, a.kit.lp(-1, -2));
    }

    // ================= primary picker =================

    private static void pickPrimary(final MainActivity a, final Runnable rerender) {
        LinearLayout body = a.kit.v();
        body.setPadding(Theme.dp(16), Theme.dp(6), Theme.dp(16), Theme.dp(12));
        ScrollView scroll = new ScrollView(a);
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        LinearLayout list = a.kit.v();
        String current = AiRouter.primary(a);
        for (final String[] o : AiClient.NAMES) {
            boolean hasKey = !keyFor(a, o[0]).isEmpty();
            String label = o[1] + (o[0].equals(current) ? "  ✓" : "") + (hasKey ? "  🔑" : "");
            list.addView(a.kit.btnGhost(label, o[0].equals(current) ? Theme.GOLD : Theme.STEEL, v -> {
                AiRouter.setPrimary(a, o[0]);
                if (holder[0] != null) holder[0].dismiss();
                rerender.run();
            }), a.kit.lp(-1, -2));
            list.addView(a.kit.gap(6));
        }
        scroll.addView(list, a.kit.lp(-1, -2));
        body.addView(scroll, a.kit.lp(-1, -2));
        holder[0] = a.kit.dialog("انتخاب سرویس اصلی", body, true);
        holder[0].show();
    }

    // ================= status =================

    private static View statusPill(MainActivity a, String provider) {
        long cool = AiRouter.cooldownRemaining(a, provider);
        if (cool > 0) {
            long mins = (cool + 59_000L) / 60_000L;
            return a.kit.pill("موقتاً متوقف (" + mins + " دقیقه)", Theme.MUTED);
        }
        String kind = AiRouter.statusOf(a, provider);
        int color = AiClient.KIND_OK.equals(kind) ? Theme.SUCCESS
                : (AiClient.KIND_AUTH.equals(kind) || AiClient.KIND_RATE.equals(kind) || AiClient.KIND_CLIENT.equals(kind))
                ? Theme.DANGER : Theme.MUTED;
        return a.kit.pill(AiClient.kindLabel(kind), color);
    }

    private static String statusDetail(MainActivity a, String provider) {
        boolean hasKey = !keyFor(a, provider).isEmpty();
        StringBuilder b = new StringBuilder();
        b.append(hasKey ? "کلید این سرویس ثبت شده است." : "کلید این سرویس هنوز ثبت نشده است.");
        long t = AiRouter.statusTime(a, provider);
        if (t > 0) {
            long mins = Math.max(0, (System.currentTimeMillis() - t) / 60_000L);
            b.append(" آخرین آزمایش: ").append(mins <= 0 ? "لحظاتی پیش" : mins + " دقیقه پیش").append(".");
        }
        return b.toString();
    }

    // ================= per-service config =================

    private static View serviceCard(final MainActivity a, final String provider,
                                    final SharedPreferences p, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("⚙ تنظیمات " + AiClient.nameOf(provider), 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("مدل پیش‌فرض: " + safeDefaultModel(provider)
                + " — می‌توانید مدل دیگری وارد کنید. کلید به‌صورت رمزنگاری‌شده ذخیره می‌شود."), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        // custom base URL
        final EditText[] baseRef = new EditText[1];
        if (AiClient.CUSTOM.equals(provider)) {
            card.addView(a.kit.text("نشانی سرویس (HTTPS)", 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
            baseRef[0] = a.kit.edit("https://example.com/v1", p.getString("baseUrl", ""));
            card.addView(baseRef[0], a.kit.lp(-1, -2));
            card.addView(a.kit.gap(8));
        }

        card.addView(a.kit.text("مدل", 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText model = a.kit.edit(safeDefaultModel(provider), AiRouter.modelOverride(a, provider));
        card.addView(model, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.text("کلید API", 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final EditText key = a.kit.edit("کلید API را اینجا وارد کنید", "", true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(key, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        card.addView(a.kit.btnGold("✦ ذخیره مدل و کلید", v -> {
            String k = key.getText().toString().trim();
            if (!k.isEmpty() && !SecretBox.put(a, keyName(provider), k)) {
                a.kit.toast("ذخیرهٔ امن کلید ممکن نشد");
                return;
            }
            AiRouter.setModelOverride(a, provider, model.getText().toString().trim());
            if (baseRef[0] != null) p.edit().putString("baseUrl", baseRef[0].getText().toString().trim()).apply();
            a.kit.toast("تنظیمات " + AiClient.nameOf(provider) + " ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        final TextView testResult = a.kit.text("", 12f, Theme.MUTED, false);
        card.addView(a.kit.btnGhost("⟳ آزمایش اتصال این سرویس", Theme.GOLD, v -> {
            String typed = key.getText().toString().trim();
            final String k = typed.isEmpty() ? keyFor(a, provider) : typed;
            final String m = model.getText().toString().trim();
            testResult.setText("در حال آزمایش…");
            new Thread(() -> {
                final String msg = AiRouter.testService(a, provider, k, m);
                a.runOnUiThread(() -> {
                    testResult.setText(msg);
                    rerender.run();
                });
            }).start();
        }), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(4));
        card.addView(testResult, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        card.addView(a.kit.btnGhost("🗑 حذف کلید این سرویس", Theme.DANGER, v -> {
            SecretBox.put(a, keyName(provider), "");
            AiRouter.resetStatus(a, provider);
            a.kit.toast("کلید " + AiClient.nameOf(provider) + " حذف شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        return card;
    }

    private static String safeDefaultModel(String provider) {
        String m = AiClient.defaultModel(provider);
        return m.isEmpty() ? "نام مدل را وارد کنید" : m;
    }

    // ================= fallback order =================

    private static View fallbackCard(final MainActivity a, final SharedPreferences p, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.STEEL);
        card.addView(a.kit.text("↕ ترتیب سرویس‌های جایگزین", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.hint("اگر سرویس اصلی خطا بدهد، این سرویس‌ها به‌ترتیب زیر آزمایش می‌شوند. "
                + "سرویس اصلی همیشه اول است. روی نام هر سرویس بزنید تا مدل/کلید همان سرویس را جداگانه تنظیم کنید."),
                a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        final String primary = AiRouter.primary(a);
        final List<String> order = AiRouter.mergeOrder(primary, AiRouter.configuredOrder(a));
        order.remove(primary);
        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            final String id = order.get(i);
            LinearLayout row = a.kit.h();
            boolean hasKey = !keyFor(a, id).isEmpty();
            TextView name = a.kit.text((i + 1) + ". " + AiClient.nameOf(id) + (hasKey ? " 🔑" : " (بدون کلید)"),
                    12.5f, hasKey ? Theme.TEXT : Theme.MUTED, false);
            Theme.pressable(name);
            name.setOnClickListener(v -> configureDialog(a, id, p, rerender));
            row.addView(name, a.kit.wlp(1f));
            row.addView(a.kit.btnGhost("↑", Theme.GOLD, v -> move(a, order, idx, -1, rerender)), a.kit.lp(-2, -2));
            row.addView(a.kit.space(6));
            row.addView(a.kit.btnGhost("↓", Theme.GOLD, v -> move(a, order, idx, 1, rerender)), a.kit.lp(-2, -2));
            card.addView(row, a.kit.lp(-1, -2));
            card.addView(a.kit.gap(4));
        }
        card.addView(a.kit.btnGhost("⟲ بازنشانی ترتیب پیش‌فرض", Theme.MUTED, v -> {
            AiRouter.setConfiguredOrder(a, new ArrayList<String>());
            a.kit.toast("ترتیب جایگزین‌ها بازنشانی شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        return card;
    }

    /** Per-service model/key/test dialog, reachable from a fallback row without changing primary. */
    private static void configureDialog(final MainActivity a, final String id,
                                        final SharedPreferences p, final Runnable rerender) {
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        Runnable close = () -> {
            if (holder[0] != null) holder[0].dismiss();
            rerender.run();
        };
        View card = serviceCard(a, id, p, close);
        ScrollView sc = new ScrollView(a);
        sc.addView(card, a.kit.lp(-1, -2));
        holder[0] = a.kit.dialog("تنظیمات " + AiClient.nameOf(id), sc, true);
        holder[0].show();
    }

    private static void move(MainActivity a, List<String> order, int idx, int dir, Runnable rerender) {
        int j = idx + dir;
        if (j < 0 || j >= order.size()) return;
        String tmp = order.get(idx);
        order.set(idx, order.get(j));
        order.set(j, tmp);
        AiRouter.setConfiguredOrder(a, order);
        rerender.run();
    }

    // ================= voice / language / behaviour =================

    private static View voiceCard(final MainActivity a, final SharedPreferences p, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.VIOLET);
        card.addView(a.kit.text("🔊 صدا، زبان و رفتار دستیار", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(6));

        final CheckBox speak = new CheckBox(a);
        speak.setText("خواندن پاسخ‌های دستیار با صدا");
        speak.setTextColor(Theme.TEXT);
        speak.setTextSize(12.5f * Theme.fontScale());
        speak.setChecked(a.getSharedPreferences("meelano_assistant", Context.MODE_PRIVATE).getBoolean("speak", false));
        card.addView(speak, a.kit.lp(-1, -2));

        final CheckBox cloudTts = new CheckBox(a);
        cloudTts.setText("استفاده از صدای طبیعی ابری (Gemini/ChatGPT) به‌جای صدای آفلاین");
        cloudTts.setTextColor(Theme.TEXT);
        cloudTts.setTextSize(12.5f * Theme.fontScale());
        cloudTts.setChecked(p.getBoolean("tts_cloud", false));
        card.addView(cloudTts, a.kit.lp(-1, -2));
        card.addView(a.kit.hint("صدای ابری به اینترنت و کلید سرویس نیاز دارد و متن پاسخ را به همان سرویس می‌فرستد. "
                + "صدای آفلاین فارسی بدون اینترنت کار می‌کند."), a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.text("زبان پاسخ", 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String lang = p.getString("reply_lang", "fa");
        LinearLayout langRow = a.kit.h();
        langRow.addView(a.kit.chip("فارسی", "fa".equals(lang), Theme.GOLD, v -> {
            p.edit().putString("reply_lang", "fa").apply(); rerender.run();
        }), a.kit.wlp(1f));
        langRow.addView(a.kit.space(8));
        langRow.addView(a.kit.chip("English", "en".equals(lang), Theme.GOLD, v -> {
            p.edit().putString("reply_lang", "en").apply(); rerender.run();
        }), a.kit.wlp(1f));
        card.addView(langRow, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(8));

        card.addView(a.kit.text("سبک پاسخ", 12.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        String style = p.getString("reply_style", "full");
        LinearLayout styleRow = a.kit.h();
        styleRow.addView(a.kit.chip("کوتاه و گفتاری", "short".equals(style), Theme.GOLD, v -> {
            p.edit().putString("reply_style", "short").apply(); rerender.run();
        }), a.kit.wlp(1f));
        styleRow.addView(a.kit.space(8));
        styleRow.addView(a.kit.chip("کامل و تحلیلی", "full".equals(style), Theme.GOLD, v -> {
            p.edit().putString("reply_style", "full").apply(); rerender.run();
        }), a.kit.wlp(1f));
        card.addView(styleRow, a.kit.lp(-1, -2));
        card.addView(a.kit.gap(10));

        card.addView(a.kit.btnGold("✦ ذخیرهٔ صدا و رفتار", v -> {
            a.getSharedPreferences("meelano_assistant", Context.MODE_PRIVATE)
                    .edit().putBoolean("speak", speak.isChecked()).apply();
            p.edit().putBoolean("tts_cloud", cloudTts.isChecked()).apply();
            a.kit.toast("تنظیمات صدا و رفتار ذخیره شد");
            rerender.run();
        }), a.kit.lp(-1, -2));
        return card;
    }

    // ================= offline speech models =================

    private static View asrCard(final MainActivity a, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.STEEL);
        card.addView(a.kit.text("🎙 شنیدن فارسی آفلاین", 13.5f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean asrOk = OfflineAsr.installed(a);
        card.addView(a.kit.hint(asrOk
                ? "گفتگوی زنده با صدای شما بدون اینترنت شنیده می‌شود."
                : "یک بار مدل شنیدن فارسی دانلود می‌شود. بعد از آن بدون اینترنت کار می‌کند."), a.kit.lp(-1, -2));
        final TextView asrStatus = a.kit.text(asrOk ? "✓ آماده است" : "دانلود نشده",
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
        return card;
    }

    private static View ttsCard(final MainActivity a, final SharedPreferences p, final Runnable rerender) {
        LinearLayout card = a.kit.card(Theme.STEEL);
        card.addView(a.kit.text("🗣 صدای فارسی آفلاین (پیش‌فرض)", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        final boolean vInstalled = OfflineVoice.installed(a);
        final boolean vBest = OfflineVoice.bestInstalled(a);
        card.addView(a.kit.hint(vBest
                ? "صدای فارسی روی گوشی است و بدون اینترنت پخش می‌شود."
                : (vInstalled
                ? "صدای زن فارسی هنوز دانلود نشده است؛ تا آن زمان صدای فعلی خوانده می‌شود."
                : "یک بار صدای فارسی از GitHub دانلود می‌شود. بعد از آن بدون اینترنت کار می‌کند.")), a.kit.lp(-1, -2));
        final TextView vStatus = a.kit.text(vBest ? "✓ آماده است"
                : (vInstalled ? "صدای فعلی؛ صدای زن را دانلود کنید" : "دانلود نشده"),
                12f, vBest ? Theme.SUCCESS : Theme.MUTED, true);
        card.addView(vStatus, a.kit.lp(-1, -2));
        if (!vBest) {
            card.addView(a.kit.btnGhost("⬇ دانلود صدای زن فارسی", Theme.VIOLET, v -> {
                if (OfflineVoice.downloading()) { a.kit.toast("دانلود در حال انجام است"); return; }
                vStatus.setText("در حال دانلود…");
                OfflineVoice.download(a, pct -> vStatus.setText("در حال دانلود " + pct + "٪"), err -> {
                    if (err == null) {
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
        return card;
    }

    // ================= activate =================

    /** One-tap activation: tests the primary service and turns the assistant on only if it answers. */
    private static void activate(final MainActivity a, final String provider, final String key,
                                 final boolean consent, final SharedPreferences p, final Runnable rerender) {
        a.kit.toast("در حال اتصال به " + AiClient.nameOf(provider) + "…");
        new Thread(() -> {
            final String msg = AiRouter.testService(a, provider, key, null);
            final boolean ok = AiClient.KIND_OK.equals(AiRouter.statusOf(a, provider));
            a.runOnUiThread(() -> {
                if (ok) {
                    p.edit().putString("provider", provider).putBoolean("consent", consent).apply();
                    a.kit.toast("اتصال برقرار شد و هوش مصنوعی فعال است ✓");
                } else {
                    a.kit.toast(msg);
                }
                if (rerender != null) rerender.run();
            });
        }).start();
    }
}