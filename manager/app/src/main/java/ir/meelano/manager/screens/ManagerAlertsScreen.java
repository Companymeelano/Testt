package ir.meelano.manager.screens;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import ir.meelano.manager.MainActivity;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MasterQueries;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Repo;
import ir.meelano.manager.data.Row;
import ir.meelano.manager.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/** Manager-configurable exceptions with one-tap routes to the relevant work screen. */
public class ManagerAlertsScreen extends Screen {
    private static final String PREFS = "meelano_manager_alerts";
    private static final int DEFAULT_DROP = 20;
    private static final int DEFAULT_MARGIN = 0;
    private static final int DEFAULT_COVER = 7;

    private EditText salesDrop;
    private EditText marginFloor;
    private EditText stockCover;
    private LinearLayout content;
    private LinearLayout results;
    private int checkSequence;

    private static final class AlertItem {
        String title;
        String detail;
        String screen;
        int accent;
        AlertItem(String title, String detail, String screen, int accent) {
            this.title = title;
            this.detail = detail;
            this.screen = screen;
            this.accent = accent;
        }
    }

    private static final class Data {
        Row today = new Row();
        Row previous = new Row();
        List<Row> margins = new ArrayList<>();
        List<Row> stocks = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
    }

    public ManagerAlertsScreen(MainActivity a) { super(a); }
    @Override public String id() { return "manager_alerts"; }
    @Override public String title() { return "هشدارهای مدیر"; }
    @Override public String glyph() { return "⚑"; }
    @Override public int accent() { return Theme.WARNING; }

    @Override public void render(LinearLayout content) {
        this.content = content;
        content.removeAllViews();
        content.addView(a.kit.hero(glyph(), title(),
                "آستانه‌ها را تعیین کنید؛ هر هشدار مستقیم به صفحهٔ اقدام می‌رود", accent()), a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        LinearLayout rules = a.kit.card(Theme.WARNING);
        rules.addView(a.kit.text("آستانه‌های قابل تنظیم", 14f, Theme.TEXT, true), a.kit.lp(-1, -2));
        rules.addView(a.kit.hint("عدد صفر هر هشدار را خاموش می‌کند؛ تغییرها فقط روی همین دستگاه ذخیره می‌شوند."),
                a.kit.lp(-1, -2));
        salesDrop = a.kit.editNum("٪ افت فروش نسبت به میانگین ۷ روز قبل (پیش‌فرض ۲۰؛ صفر=خاموش)",
                String.valueOf(a.getSharedPreferences(PREFS, 0).getInt("sales_drop", DEFAULT_DROP)));
        marginFloor = a.kit.editNum("حداقل حاشیه سود کالا٪ (صفر=خاموش)",
                String.valueOf(a.getSharedPreferences(PREFS, 0).getInt("margin_floor", DEFAULT_MARGIN)));
        stockCover = a.kit.editNum("حداقل پوشش موجودی به روز (پیش‌فرض ۷؛ صفر=خاموش)",
                String.valueOf(a.getSharedPreferences(PREFS, 0).getInt("stock_cover", DEFAULT_COVER)));
        salesDrop.setInputType(InputType.TYPE_CLASS_NUMBER);
        marginFloor.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        stockCover.setInputType(InputType.TYPE_CLASS_NUMBER);
        rules.addView(salesDrop, a.kit.lp(-1, -2));
        rules.addView(marginFloor, a.kit.lp(-1, -2));
        rules.addView(stockCover, a.kit.lp(-1, -2));
        rules.addView(a.kit.btnGold("ذخیره آستانه‌ها و بررسی همین حالا", v -> saveAndCheck()), a.kit.lp(-1, -2));
        rules.addView(a.kit.btnGhost("بررسی دوباره", Theme.INFO, v -> check()), a.kit.lp(-1, -2));
        content.addView(rules, a.kit.lp(-1, -2));
        content.addView(a.kit.gap(8));
        content.addView(a.kit.hint("افت فروش با میانگین ۷ روز تقویمیِ کاملِ پیشین سنجیده می‌شود. حاشیه سود بر پایه بهای خرید ثبت‌شده است؛ پوشش موجودی بر میانگین فروش ۶۰ روز اخیر محاسبه می‌شود."),
                a.kit.lp(-1, -2));
        results = a.kit.v();
        content.addView(results, a.kit.lp(-1, -2));
        check();
    }

    private int parse(EditText e, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(Money.en(e.getText().toString()).trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception ignored) { return fallback; }
    }

    private void saveAndCheck() {
        int drop = parse(salesDrop, DEFAULT_DROP, 0, 95);
        int margin = parse(marginFloor, DEFAULT_MARGIN, -100, 100);
        int cover = parse(stockCover, DEFAULT_COVER, 0, 365);
        a.getSharedPreferences(PREFS, 0).edit()
                .putInt("sales_drop", drop)
                .putInt("margin_floor", margin)
                .putInt("stock_cover", cover)
                .apply();
        salesDrop.setText(String.valueOf(drop));
        marginFloor.setText(String.valueOf(margin));
        stockCover.setText(String.valueOf(cover));
        a.kit.toast("آستانه‌های هشدار ذخیره شد");
        check();
    }

    private void check() {
        if (content == null) return;
        final int drop = a.getSharedPreferences(PREFS, 0).getInt("sales_drop", DEFAULT_DROP);
        final int margin = a.getSharedPreferences(PREFS, 0).getInt("margin_floor", DEFAULT_MARGIN);
        final int cover = a.getSharedPreferences(PREFS, 0).getInt("stock_cover", DEFAULT_COVER);
        final int sequence = ++checkSequence;
        final String today = Jalali.todayStr();
        results.removeAllViews();
        results.addView(a.kit.loading("در حال محاسبه هشدارها…"), a.kit.lp(-1, -2));
        a.repo.run(c -> {
            Data d = new Data();
            Meta m = new Meta(c);
            ir.meelano.manager.core.Filter fToday = dayFilter(today, today);
            String yesterday = Jalali.addDays(today, -1);
            ir.meelano.manager.core.Filter fPrev = dayFilter(Jalali.addDays(today, -7), yesterday);
            d.today = part(d, "فروش امروز", conn -> Repo.one(conn,
                    Queries.factorSummary(m, true, fToday)), new Row(), c);
            d.previous = part(d, "فروش ۷ روز قبل", conn -> Repo.one(conn,
                    Queries.factorSummary(m, true, fPrev)), new Row(), c);
            String from = today.substring(0, 7) + "/01";
            ir.meelano.manager.core.Filter fMonth = dayFilter(from, today);
            fMonth.top = 500;
            d.margins = part(d, "حاشیه سود", conn -> Repo.exec(conn,
                    MasterQueries.lowMarginProducts(m, fMonth, 500)), new ArrayList<Row>(), c);
            ir.meelano.manager.core.Filter fStock = new ir.meelano.manager.core.Filter();
            fStock.from = today;
            fStock.to = today;
            fStock.top = 500;
            d.stocks = part(d, "پوشش موجودی", conn -> Repo.exec(conn,
                    MasterQueries.stockForecast(m, fStock)), new ArrayList<Row>(), c);
            return d;
        }, new Repo.Cb<Data>() {
            @Override public void ok(Data d) {
                if (content == null || sequence != checkSequence) return;
                showResults(d, drop, margin, cover);
            }
            @Override public void fail(String faError) {
                if (content == null || sequence != checkSequence) return;
                results.removeAllViews();
                LinearLayout card = a.kit.card(Theme.DANGER);
                card.addView(a.kit.text("بررسی هشدارها ناموفق بود", 13f, Theme.TEXT, true), a.kit.lp(-1, -2));
                card.addView(a.kit.hint(faError), a.kit.lp(-1, -2));
                card.addView(a.kit.btnGhost("⟳ تلاش دوباره", Theme.DANGER, v -> check()), a.kit.lp(-1, -2));
                results.addView(card, a.kit.lp(-1, -2));
            }
        });
    }

    private ir.meelano.manager.core.Filter dayFilter(String from, String to) {
        ir.meelano.manager.core.Filter f = new ir.meelano.manager.core.Filter();
        f.from = from;
        f.to = to;
        f.top = 500;
        f.page = 0;
        return f;
    }

    private <T> T part(Data d, String label, Repo.Task<T> task, T fallback, java.sql.Connection c) {
        try { return task.run(c); }
        catch (Exception e) {
            d.notes.add(label + ": " + (e.getMessage() == null ? "اطلاعات در دسترس نیست" : e.getMessage()));
            return fallback;
        }
    }

    private void showResults(Data d, int dropThreshold, int marginThreshold, int coverThreshold) {
        LinearLayout box = a.kit.card(Theme.INFO);
        box.addView(a.kit.sectionHead("نتیجهٔ بررسی زنده • " + Money.fa(Jalali.todayStr()), null, null),
                a.kit.lp(-1, -2));
        List<AlertItem> alerts = new ArrayList<>();
        double today = d.today.d("total");
        double average7 = d.previous.d("total") / 7.0;
        boolean salesAvailable = !hasNote(d, "فروش امروز") && !hasNote(d, "فروش ۷ روز قبل");
        if (dropThreshold > 0 && salesAvailable && average7 > 0) {
            double drop = (average7 - today) * 100.0 / average7;
            if (drop >= dropThreshold) {
                alerts.add(new AlertItem("افت فروش امروز: " + Money.pct(drop),
                        "امروز " + Money.compactRial(today) + " • میانگین هر روز از ۷ روز قبل "
                                + Money.compactRial(average7), "sales", Theme.DANGER));
            }
        }
        if (marginThreshold != 0) {
            int shown = 0;
            for (Row r : d.margins) {
                double sales = r.d("sales");
                if (sales <= 0) continue;
                double pct = r.d("profit") * 100.0 / sales;
                if (pct < marginThreshold) {
                    alerts.add(new AlertItem("حاشیه سود پایین: " + r.s("label"),
                            "حاشیه " + Money.pct(pct) + " • فروش " + Money.compactRial(sales)
                                    + " • سود " + Money.compactRial(r.d("profit")), "products", Theme.WARNING));
                    shown++;
                    if (shown >= 5) break;
                }
            }
        }
        if (coverThreshold > 0) {
            int shown = 0;
            for (Row r : d.stocks) {
                if (!r.has("daysLeft") || r.d("daily") <= 0) continue;
                double days = r.d("daysLeft");
                if (days <= coverThreshold) {
                    alerts.add(new AlertItem("پوشش موجودی کم: " + r.s("label"),
                            "حدود " + Money.fa(String.format(java.util.Locale.US, "%.1f", days))
                                    + " روز باقی‌مانده • موجودی " + Money.fa(String.format(
                                    java.util.Locale.US, "%.2f", r.d("vah"))), "products", Theme.WARNING));
                    shown++;
                    if (shown >= 8) break;
                }
            }
        }
        if (alerts.isEmpty()) {
            boolean incomplete = !d.notes.isEmpty();
            box.addView(a.kit.text(incomplete ? "⚠ بررسی هشدارها کامل نشد" : "✓ موردی از آستانه‌های فعال عبور نکرده است",
                    13f, incomplete ? Theme.WARNING : Theme.SUCCESS, true), a.kit.lp(-1, -2));
            box.addView(a.kit.hint(salesAvailable
                    ? "فروش امروز " + Money.compactRial(today)
                            + " • میانگین روزانهٔ ۷ روز قبل " + Money.compactRial(average7)
                    : "مقایسهٔ فروش نامشخص است؛ دادهٔ ناقص به‌عنوان فروش صفر تفسیر نمی‌شود."),
                    a.kit.lp(-1, -2));
        } else {
            box.addView(a.kit.text(Money.fa(String.valueOf(alerts.size())) + " هشدار نیازمند اقدام", 13f,
                    Theme.WARNING, true), a.kit.lp(-1, -2));
            for (AlertItem item : alerts) {
                View row = a.kit.navRow("⚠", item.title, item.detail, item.accent, v -> a.nav(item.screen));
                LinearLayout.LayoutParams p = a.kit.lp(-1, -2);
                p.setMargins(0, Theme.dp(6), 0, 0);
                box.addView(row, p);
            }
        }
        if (!d.notes.isEmpty()) {
            box.addView(a.kit.gap(6));
            box.addView(a.kit.hint("بعضی هشدارها کامل بررسی نشدند: " + join(d.notes)), a.kit.lp(-1, -2));
        }
        results.addView(box, a.kit.lp(-1, -2));
    }

    private boolean hasNote(Data d, String prefix) {
        for (String note : d.notes) if (note != null && note.startsWith(prefix + ":")) return true;
        return false;
    }

    private String join(List<String> v) {
        StringBuilder b = new StringBuilder();
        for (String s : v) {
            if (b.length() > 0) b.append(" • ");
            b.append(s);
        }
        return b.toString();
    }
}
