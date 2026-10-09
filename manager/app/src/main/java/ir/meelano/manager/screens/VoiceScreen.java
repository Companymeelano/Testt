package ir.meelano.manager.screens;

import android.speech.tts.TextToSpeech;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Filter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.MoneyQueries;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.List;
import java.util.Locale;

/** Persian voice assistant: tap the mic, ask in Persian, hear the answer. */
public class VoiceScreen extends Screen {
    private String lastQ = "";
    private String lastA = "";
    private static TextToSpeech tts;

    public VoiceScreen(MainActivity a) {
        super(a);
    }

    @Override
    public String id() { return "voice"; }

    @Override
    public String title() { return "دستیار صوتی"; }

    @Override
    public String glyph() { return "🎙"; }

    @Override
    public int accent() { return Theme.TEAL; }

    @Override
    public void render(final LinearLayout content) {
        content.removeAllViews();
        content.addView(heroCard(), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(12));
        content.addView(a.kit.btnGold("🎙 بپرسید…", v ->
                a.startVoiceSearch(q -> ask(content, q))), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(a.kit.hint("مثلاً: «فروش امروز چقدر بود؟» • «بدهکارها کی‌اند؟» • «چک‌های نزدیک سررسید» • «موجودی انبار» • «دریافت امروز»"),
                a.kit.lp(-1, -2));
        if (!lastA.isEmpty()) {
            content.addView(a.kit.gap(10));
            LinearLayout c = a.kit.card(accent());
            if (!lastQ.isEmpty())
                c.addView(a.kit.text("«" + lastQ + "»", 12.5f, Theme.MUTED, false), a.kit.lp(-1, -2));
            c.addView(a.kit.text(lastA, 15f, Theme.TEXT, true), a.kit.lp(-1, -2));
            c.addView(a.kit.gap(6));
            c.addView(a.kit.btnGhost("🔊 پخش دوباره", Theme.TEAL, v -> speak(lastA)), a.kit.lp(-1, -2));
            a.kit.addCard(content, c);
        }
    }

    private void ask(final LinearLayout content, String q) {
        lastQ = q == null ? "" : q.trim();
        if (lastQ.isEmpty()) return;
        String t = lastQ;
        if (t.contains("فروش") || t.contains("فروخت")) runDay(content, 0);
        else if (t.contains("خرید")) runDay(content, 1);
        else if (t.contains("دریافت") || t.contains("وصول")) runDay(content, 2);
        else if (t.contains("پرداخت")) runDay(content, 3);
        else if (t.contains("بدهکار") || t.contains("بدهی") || t.contains("طلب")) runDebtors(content);
        else if (t.contains("چک") || t.contains("سررسید")) runDue(content);
        else if (t.contains("موجودی") || t.contains("انبار") || t.contains("کمبود") || t.contains("ناموجود")) runStock(content);
        else {
            lastA = "می‌توانید بپرسید: فروش امروز، خرید امروز، دریافت و پرداخت امروز، بدهکاران، چک‌های نزدیک سررسید، یا موجودی انبار.";
            speak(lastA);
            render(content);
        }
    }

    /** 0 = sales, 1 = buy, 2 = receipts, 3 = payments (latest day row). */
    private void runDay(final LinearLayout content, final int kind) {
        a.kit.toast("در حال پرسش از آتیران…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            if (kind == 0) return Repo.one(c, Queries.homeSalesDay(m));
            if (kind == 1) return Repo.one(c, Queries.homeBuyDay(m));
            return Repo.one(c, MoneyQueries.darDay(m, kind == 2 ? 0 : 1));
        }, new Repo.Cb<Row>() {
            @Override
            public void ok(Row r) {
                String day = dayFa(r == null ? "" : r.s("d"));
                String amt = Money.words(r == null ? 0 : r.d("total"));
                long n = r == null ? 0 : (kind < 2 ? r.l("docs") : r.l("count"));
                String what = kind == 0 ? "فروش" : kind == 1 ? "خرید"
                        : kind == 2 ? "دریافت" : "پرداخت";
                String unit = kind < 2 ? "فاکتور" : "قبض";
                lastA = what + " " + day + " " + amt + " ریال"
                        + (n > 0 ? " در " + Money.fa(String.valueOf(n)) + " " + unit : "") + " است.";
                speak(lastA);
                render(content);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void runDebtors(final LinearLayout content) {
        a.kit.toast("در حال پرسش از آتیران…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.exec(c, Queries.topDebtors(m, 3));
        }, new Repo.Cb<List<Row>>() {
            @Override
            public void ok(List<Row> rows) {
                if (rows == null || rows.isEmpty()) {
                    lastA = "بدهکاری ثبت نشده است؛ همه حساب‌ها تسویه است.";
                } else {
                    Row r = rows.get(0);
                    lastA = "بزرگ‌ترین بدهکار «" + r.s("party") + "» با مانده "
                            + Money.words(r.d("amount")) + " ریال است."
                            + (rows.size() > 1 ? " " + Money.fa(String.valueOf(rows.size())) + " بدهکار اولویت‌دار در فهرست است." : "");
                }
                speak(lastA);
                render(content);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void runDue(final LinearLayout content) {
        a.kit.toast("در حال پرسش از آتیران…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            int n = 0;
            try {
                n += Repo.exec(c, MoneyQueries.chequeDue(m, true, 3)).size();
            } catch (Exception ignored) { }
            try {
                n += Repo.exec(c, MoneyQueries.chequeDue(m, false, 3)).size();
            } catch (Exception ignored) { }
            return n;
        }, new Repo.Cb<Integer>() {
            @Override
            public void ok(Integer n) {
                int v = n == null ? 0 : n;
                lastA = v == 0 ? "در سه روز آینده سررسیدی ثبت نشده است."
                        : Money.fa(String.valueOf(v)) + " فقره چک در سه روز آینده سررسید می‌شود.";
                speak(lastA);
                render(content);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private void runStock(final LinearLayout content) {
        a.kit.toast("در حال پرسش از آتیران…");
        a.repo.run(c -> {
            Meta m = new Meta(c);
            return Repo.one(c, MasterQueries.productsSummary(m));
        }, new Repo.Cb<Row>() {
            @Override
            public void ok(Row r) {
                long low = r == null ? 0 : r.l("low");
                long out = r == null ? 0 : r.l("out");
                if (low + out == 0) lastA = "موجودی همه کالاها سالم است؛ کمبودی ثبت نشده.";
                else lastA = Money.fa(String.valueOf(out)) + " قلم ناموجود و "
                        + Money.fa(String.valueOf(low)) + " قلم کم‌موجود در انبار است.";
                speak(lastA);
                render(content);
            }

            @Override
            public void fail(String faError) {
                a.kit.toast(faError);
            }
        });
    }

    private String dayFa(String d) {
        try {
            String day = Jalali.disp(d == null ? "" : d);
            if (day.isEmpty()) return "امروز";
            String today = Jalali.todayStr();
            if (day.equals(today)) return "امروز";
            if (day.equals(Jalali.addDays(today, -1))) return "دیروز";
            return Jalali.shortLabel(day);
        } catch (Exception e) {
            return "امروز";
        }
    }

    /** Speak Persian (graceful when the phone has no Persian voice). */
    private void speak(final String text) {
        try {
            if (text == null || text.isEmpty()) return;
            if (tts == null) {
                tts = new TextToSpeech(a.getApplicationContext(), status -> {
                    try {
                        if (status != TextToSpeech.SUCCESS) return;
                        int fa = tts.isLanguageAvailable(new Locale("fa"));
                        if (fa >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(new Locale("fa"));
                        else a.kit.toast("صدای فارسی روی این گوشی نیست؛ پاسخ نمایش داده شد");
                    } catch (Exception ignored) { }
                });
            }
            try {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "meelano-voice");
            } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }
}
