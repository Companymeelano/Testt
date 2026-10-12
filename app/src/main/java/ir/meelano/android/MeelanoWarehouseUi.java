package ir.meelano.android;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * صفحهٔ «انبار هوشمند» — مرکز فرمان انبارداری حرفه‌ای در برنامه.
 *
 * <p>شش تب دارد و همه از دادهٔ واقعی آتیران تغذیه می‌شود:</p>
 * <ul>
 *   <li><b>نمای کل:</b> ارزش موجودی (بهای خرید و فروش)، تعداد اقلام، هزینهٔ نگهداری سالانه،
 *       هشدارهای هوشمند و نمودار سهم ABC.</li>
 *   <li><b>تحلیل ABC:</b> پارتو بر پایهٔ ارزش مصرف + ماتریس ABC×گردش (۱۲ خانه) برای تصمیم
 *       «کدام کالا باید همیشه موجود باشد و کدام باید کم شود».</li>
 *   <li><b>تأمین و سفارش:</b> نقطهٔ سفارش، ذخیرهٔ اطمینان و مقدار سفارش اقتصادی (EOQ) با
 *       مبلغ پیشنهادی خرید.</li>
 *   <li><b>سرمایهٔ راکد:</b> کالاهای بی‌فروش با ریال سرمایهٔ خواب و اقدام پیشنهادی.</li>
 *   <li><b>گردش کالا:</b> کارت گردش واقعی از جدول {@code ka_act} (تاریخ، تعداد، فی، انبار، سند).</li>
 *   <li><b>برچسب و بارکد:</b> ساخت برگهٔ برچسب A4 با QR و بارکد برای قفسه و کالا.</li>
 * </ul>
 */
final class MeelanoWarehouseUi {
    private MeelanoWarehouseUi() { }

    /** تب فعال (بین بازسازی‌های صفحه حفظ می‌شود). */
    static String tab = "cockpit";
    /** کد کالایی که کارت گردش آن باز است. */
    static String focusCode = "";
    static String focusName = "";
    /** شمار صفحهٔ فهرست جاری (برای فهرست‌های بلند). */
    static int pageIndex = 0;
    private static final int PAGE_SIZE = 12;

    private static final String[][] TABS = {
            {"cockpit", "نمای کل", "◎"},
            {"abc", "تحلیل ABC", "▮"},
            {"supply", "تأمین و سفارش", "⇅"},
            {"dead", "سرمایهٔ راکد", "⧗"},
            {"kardex", "گردش کالا", "⌛"},
            {"labels", "برچسب و بارکد", "▩"}
    };

    // ================================================================ رندر اصلی
    /**
     * @param page  ظرف صفحه (content در MainActivity)
     * @param data  {"analysis": تحلیلی انبار، "snapshot": دادهٔ انبار از سرور (می‌تواند نباشد),
     *              "products": آرایهٔ کالاها}
     */
    static void render(final MeelanoUiHost h, final LinearLayout page, final JSONObject data) {
        MeelanoPalette p = h.colors();
        page.removeAllViews();
        JSONObject analysis = data == null ? null : data.optJSONObject("analysis");
        JSONObject totals = analysis == null ? null : analysis.optJSONObject("totals");
        JSONObject snapshot = data == null ? null : data.optJSONObject("snapshot");
        JSONArray products = data == null ? null : data.optJSONArray("products");

        // ---------- هدر
        String badge = totals == null ? "بدون داده" : h.fa(String.valueOf(totals.optInt("items"))) + " قلم";
        page.addView(MeelanoUiKit.hero(h, "انبار هوشمند",
                "تحلیل موجودی، نقطهٔ سفارش، کالای راکد و برچسب بارکد — همه بر پایهٔ دادهٔ زندهٔ آتیران",
                "◈", p.accent, badge), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 9);

        // ---------- تب‌ها
        page.addView(MeelanoUiKit.chipBar(h, TABS, tab, new MeelanoUiKit.Pick() {
            @Override public void pick(String key) {
                tab = key;
                pageIndex = 0;
                h.goTo("warehouse");
            }
        }), MeelanoUiKit.lp(-1, -2));

        if (totals == null || totals.optInt("items", 0) == 0) {
            MeelanoUiKit.gap(h, page, 8);
            page.addView(MeelanoUiKit.emptyState(h, "◈", "دادهٔ کالا در دسترس نیست",
                    "برای تحلیل انبار، ابتدا فهرست کالاها از سرور آتیران خوانده می‌شود. اتصال را بررسی و دوباره تلاش کنید.",
                    "تلاش دوباره", new Runnable() { @Override public void run() { h.goTo("warehouse"); } }), MeelanoUiKit.lp(-1, -2));
            return;
        }

        MeelanoUiKit.gap(h, page, 9);
        if ("abc".equals(tab)) renderAbc(h, page, analysis, products);
        else if ("supply".equals(tab)) renderSupply(h, page, analysis);
        else if ("dead".equals(tab)) renderDead(h, page, analysis);
        else if ("kardex".equals(tab)) renderKardex(h, page, data, products);
        else if ("labels".equals(tab)) renderLabels(h, page, products);
        else renderCockpit(h, page, analysis, snapshot, products);

        // ---------- پانوشت: خطاهای جزئی سرور (بدون ترساندن کاربر)
        JSONArray errors = analysis.optJSONArray("errors");
        if (snapshot != null && snapshot.optJSONArray("errors") != null) {
            JSONArray se = snapshot.optJSONArray("errors");
            if (se.length() > 0) errors = se;
        }
        if (errors != null && errors.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout err = MeelanoUiKit.card(h, p.warning);
            err.addView(MeelanoUiKit.text(h, "▤  بخش‌هایی که سرور آتیران پاسخ نداد", 10.6f, p.warning, true), MeelanoUiKit.lp(-1, -2));
            for (int i = 0; i < errors.length() && i < 4; i++) {
                TextView t = MeelanoUiKit.text(h, "• " + errors.optString(i, ""), 9.1f, p.muted, false);
                t.setMaxLines(3);
                err.addView(t, MeelanoUiKit.lp(-1, -2));
            }
            page.addView(err, MeelanoUiKit.lp(-1, -2));
        }
    }

    // ================================================================ ۱) نمای کل
    private static void renderCockpit(final MeelanoUiHost h, LinearLayout page, JSONObject analysis, JSONObject snapshot, JSONArray products) {
        MeelanoPalette p = h.colors();
        final JSONObject totals = analysis.optJSONObject("totals");

        List<View> tiles = new ArrayList<>();
        tiles.add(MeelanoUiKit.kpi(h, "ارزش موجودی (بهای خرید)", h.money(totals.optDouble("stockValueCost")),
                "ارزش فروش: " + h.money(totals.optDouble("stockValueSell")), "◎", p.accent, null));
        tiles.add(MeelanoUiKit.kpi(h, "سود بالقوهٔ موجودی", h.money(totals.optDouble("potentialMargin")),
                "اختلاف بهای خرید و قیمت فروش", "↗", p.success, null));
        tiles.add(MeelanoUiKit.kpi(h, "کالای راکد", h.fa(String.valueOf(totals.optInt("deadCount"))) + " قلم",
                "سرمایهٔ خواب: " + h.money(totals.optDouble("deadValue")), "⧗", p.danger, new Runnable() {
                    @Override public void run() { tab = "dead"; h.goTo("warehouse"); }
                }));
        tiles.add(MeelanoUiKit.kpi(h, "نیازمند تأمین", h.fa(String.valueOf(totals.optInt("reorderCount"))) + " قلم",
                "برآورد خرید: " + h.money(totals.optDouble("reorderValue")), "⇅", p.warning, new Runnable() {
                    @Override public void run() { tab = "supply"; h.goTo("warehouse"); }
                }));
        double turnover = totals.optDouble("turnover");
        tiles.add(MeelanoUiKit.kpi(h, "گردش انبار", turnover <= 0 ? "—" : h.fa(String.format(java.util.Locale.US, "%.2f", turnover)) + " بار",
                "بهای فروش‌رفته ÷ ارزش موجودی", "⟳", p.info, null));
        tiles.add(MeelanoUiKit.kpi(h, "هزینهٔ نگهداری سالانه", h.money(totals.optDouble("holdingCostYear")),
                "با فرض " + h.fa(String.valueOf(Math.round(analysis.optJSONObject("params").optDouble("holdingCostPercent", 22)))) + "٪ ارزش کالا", "☷", p.tone("warn"), null));
        MeelanoUiKit.kpiGrid(h, page, tiles, 2, 8);

        // ---------- هشدارها
        JSONArray alerts = analysis.optJSONArray("alerts");
        if (alerts != null && alerts.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout box = MeelanoUiKit.card(h, p.accent);
            box.addView(MeelanoUiKit.text(h, "⚠  هشدارهای هوشمند انبار", 12.4f, p.text, true), MeelanoUiKit.lp(-1, -2));
            for (int i = 0; i < alerts.length(); i++) {
                JSONObject a = alerts.optJSONObject(i);
                if (a == null) continue;
                MeelanoUiKit.gap(h, box, 6);
                box.addView(MeelanoUiKit.alert(h, a.optString("level", "info"), a.optString("title", ""), a.optString("text", "")), MeelanoUiKit.lp(-1, -2));
            }
            page.addView(box, MeelanoUiKit.lp(-1, -2));
        }

        // ---------- نمودار ABC
        JSONArray abc = analysis.optJSONArray("abc");
        if (abc != null && abc.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout chartCard = MeelanoUiKit.card(h, p.accent);
            chartCard.addView(MeelanoUiKit.text(h, "▮  سهم ارزش موجودی به تفکیک کلاس ABC", 12.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            MeelanoUiKit.gap(h, chartCard, 7);
            MeelanoCharts.Donut donut = new MeelanoCharts.Donut(h.ctx(), p.accent, p.text, p.muted, h.font(false));
            List<MeelanoCharts.Point> pts = new ArrayList<>();
            int[] tones = {p.accent, p.info, p.muted};
            double sum = 0;
            for (int i = 0; i < abc.length(); i++) {
                JSONObject o = abc.optJSONObject(i);
                if (o == null) continue;
                double v = o.optDouble("stockValue");
                sum += v;
                pts.add(new MeelanoCharts.Point("کلاس " + o.optString("key") + " • " + h.fa(String.valueOf(o.optInt("items"))) + " قلم", v, tones[Math.min(i, tones.length - 1)]));
            }
            donut.setPoints(pts, MeelanoCharts.RIAL);
            donut.setCenterTitle(MeelanoCharts.compact(sum));
            chartCard.addView(donut, MeelanoUiKit.lp(-1, h.dp(178)));
            for (int i = 0; i < abc.length(); i++) {
                JSONObject o = abc.optJSONObject(i);
                if (o == null) continue;
                MeelanoUiKit.gap(h, chartCard, 5);
                chartCard.addView(MeelanoUiKit.progress(h, "کلاس " + o.optString("key") + " (" + h.fa(String.valueOf(o.optInt("items"))) + " قلم)",
                        o.optDouble("valueShare") / 100.0, h.fa(String.format(java.util.Locale.US, "%.1f", o.optDouble("valueShare"))) + "٪", tones[Math.min(i, tones.length - 1)]), MeelanoUiKit.lp(-1, -2));
            }
            page.addView(chartCard, MeelanoUiKit.lp(-1, -2));
        }

        // ---------- انبارها (از سرور)
        JSONArray warehouses = snapshot == null ? null : snapshot.optJSONArray("warehouses");
        if (warehouses != null && warehouses.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < warehouses.length() && i < 20; i++) {
                JSONObject w = warehouses.optJSONObject(i);
                if (w == null) continue;
                rows.add(new String[]{w.optString("name", "—"), h.fa(String.valueOf(w.optInt("items"))) + " قلم",
                        h.grouped(w.optDouble("qty")), h.money(w.optDouble("value")), w.optString("keeper", "—")});
            }
            page.addView(MeelanoUiKit.table(h, "◈  انبارها و ارزش موجودی هر انبار",
                    new String[]{"انبار", "اقلام", "تعداد", "ارزش (بهای خرید)", "انباردار"}, rows, p.accent, null), MeelanoUiKit.lp(-1, -2));
        }

        // ---------- پرارزش‌ترین کالاها
        JSONArray valuable = analysis.optJSONArray("valuable");
        if (valuable != null && valuable.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            page.addView(productTable(h, "◎  پرارزش‌ترین کالاهای انبار", valuable, 12, null), MeelanoUiKit.lp(-1, -2));
        }

        // ---------- خروجی‌ها
        MeelanoUiKit.gap(h, page, 9);
        LinearLayout actions = MeelanoUiKit.buttonRow(h,
                new String[][]{{"csv", "⬇  خروجی CSV"}, {"pdf", "🧾  گزارش متنی"}, {"label", "🏷  برچسب کالا"}},
                new int[]{p.accent, p.info, p.success},
                new boolean[]{true, false, false},
                new Runnable[]{
                        new Runnable() { @Override public void run() { exportCsv(h, analysis); } },
                        new Runnable() { @Override public void run() { exportSummary(h, analysis); } },
                        new Runnable() { @Override public void run() { tab = "labels"; h.goTo("warehouse"); } }
                });
        page.addView(actions, MeelanoUiKit.lp(-1, -2));

        // ---------- پارامترهای تحلیل (قابل تنظیم توسط انباردار/مدیر)
        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.buttonRow(h,
                new String[][]{{"params", "⚙  پارامترهای تحلیل"}, {"kardex", "⌛  کارت کالا"}},
                new int[]{p.accent2, p.info}, new boolean[]{false, false},
                new Runnable[]{
                        new Runnable() { @Override public void run() { paramsSheet(h, analysis); } },
                        new Runnable() { @Override public void run() { tab = "kardex"; h.goTo("warehouse"); } }
                }), MeelanoUiKit.lp(-1, -2));
    }

    /**
     * شیت «پارامترهای تحلیل انبار»: هزینهٔ نگهداری، زمان تأمین، هزینهٔ سفارش و آستانه‌ها.
     *
     * <p>همان پارامترهایی که فرمول‌های EOQ/نقطهٔ سفارش/کالای راکد به آن‌ها وابسته‌اند؛ با ذخیره‌شدن
     * در SharedPreferences، تحلیل بعدی با اعداد واقعی همان شرکت اجرا می‌شود.</p>
     */
    private static void paramsSheet(final MeelanoUiHost h, JSONObject analysis) {
        MeelanoPalette p = h.colors();
        final MeelanoWarehouse.Params current = MeelanoWarehouse.Params.fromJson(analysis == null ? null : analysis.optJSONObject("params"));
        LinearLayout body = MeelanoUiKit.column(h);
        final android.widget.EditText holding = MeelanoUiKit.field(h, "مثلاً ۲۲", fmt(current.holdingCostPercent), true);
        final android.widget.EditText lead = MeelanoUiKit.field(h, "مثلاً ۱۴", fmt(current.leadTimeDays), true);
        final android.widget.EditText leadSd = MeelanoUiKit.field(h, "مثلاً ۳٫۵", fmt(current.leadTimeStdevDays), true);
        final android.widget.EditText orderCost = MeelanoUiKit.field(h, "مثلاً ۵۰۰۰۰۰", fmt(current.orderCost), true);
        final android.widget.EditText dead = MeelanoUiKit.field(h, "مثلاً ۱۲۰", fmt(current.deadCoverDays), true);
        body.addView(MeelanoUiKit.fieldRow(h, "هزینهٔ نگهداری سالانه (٪ ارزش کالا)", holding), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 9);
        body.addView(MeelanoUiKit.fieldRow(h, "زمان تأمین از تأمین‌کننده (روز)", lead), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 9);
        body.addView(MeelanoUiKit.fieldRow(h, "نوسان زمان تأمین (روز)", leadSd), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 9);
        body.addView(MeelanoUiKit.fieldRow(h, "هزینهٔ هر سفارش خرید (ریال)", orderCost), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 9);
        body.addView(MeelanoUiKit.fieldRow(h, "آستانهٔ کالای راکد (روز پوشش)", dead), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 10);
        body.addView(MeelanoUiKit.alert(h, "info", "این اعداد چه‌کار می‌کنند؟",
                "هزینهٔ نگهداری و هزینهٔ سفارش، اندازهٔ اقتصادی سفارش (EOQ) را می‌سازند؛ زمان تأمین و نوسان آن، نقطهٔ سفارش و ذخیرهٔ اطمینان را. آستانهٔ راکد هم تعیین می‌کند چه کالایی «سرمایهٔ خواب» شمرده شود."), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.sheet(h, "پارامترهای تحلیل انبار", "⚙", p.accent, body, "ذخیره و تحلیل دوباره", new Runnable() {
            @Override public void run() {
                current.holdingCostPercent = clamp(MeelanoUiKit.number(holding, current.holdingCostPercent), 1, 90);
                current.leadTimeDays = clamp(MeelanoUiKit.number(lead, current.leadTimeDays), 0, 365);
                current.leadTimeStdevDays = clamp(MeelanoUiKit.number(leadSd, current.leadTimeStdevDays), 0, 120);
                current.orderCost = clamp(MeelanoUiKit.number(orderCost, current.orderCost), 0, 10_000_000_000d);
                current.deadCoverDays = clamp(MeelanoUiKit.number(dead, current.deadCoverDays), 15, 720);
                h.putSetting("warehouse_params", current.toJson().toString());
                h.notify("پارامترها ذخیره شد؛ تحلیل با اعداد تازه اجرا می‌شود.");
                h.goTo("warehouse");
            }
        }, "انصراف");
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** عدد برای نمایش در کادر ورودی (بدون جداکننده و با رقم لاتین، تا کاربر بتواند ویرایش کند). */
    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    // ================================================================ ۲) تحلیل ABC
    private static void renderAbc(final MeelanoUiHost h, LinearLayout page, JSONObject analysis, JSONArray products) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "تحلیل پارتو (ABC) و ماتریس گردش",
                "کلاس A = ۸۰٪ ارزش، B = ۱۵٪، C = ۵٪ — به‌همراه سرعت گردش هر کالا (تندگرد/متوسط/کندگرد/راکد)",
                "▮", p.accent), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        JSONArray matrix = analysis.optJSONArray("matrix");
        if (matrix != null && !matrix.isEmpty()) {
            LinearLayout card = MeelanoUiKit.card(h, p.accent);
            card.addView(MeelanoUiKit.text(h, "خانه‌های تصمیم (A×گردش)", 12.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            MeelanoUiKit.gap(h, card, 6);
            String[] rots = {"F", "M", "S", "D"};
            String[] rotNames = {"تندگرد", "متوسط", "کندگرد", "راکد"};
            for (int a = 0; a < 3; a++) {
                LinearLayout r = MeelanoUiKit.row(h);
                r.setGravity(Gravity.TOP);
                for (int ri = 0; ri < 4; ri++) {
                    JSONObject cell = null;
                    for (int m = 0; m < matrix.length(); m++) {
                        JSONObject o = matrix.optJSONObject(m);
                        if (o == null) continue;
                        if (("ABC".charAt(a) + "").equals(o.optString("abc")) && rots[ri].equals(o.optString("rot"))) { cell = o; break; }
                    }
                    int tone = cellTone(p, a, ri);
                    double value = cell == null ? 0 : cell.optDouble("stockValue");
                    int items = cell == null ? 0 : cell.optInt("items");
                    LinearLayout box = MeelanoUiKit.column(h);
                    box.setBackground(MeelanoUiKit.solid(p.alpha(tone, p.light ? 26 : 46), h.dp(12)));
                    box.setPadding(h.dp(7), h.dp(6), h.dp(7), h.dp(6));
                    box.addView(MeelanoUiKit.text(h, "کلاس " + "ABC".charAt(a) + " • " + rotNames[ri], 8.6f, p.mix(tone, p.text, 0.3f), true), MeelanoUiKit.lp(-1, -2));
                    box.addView(MeelanoUiKit.text(h, h.fa(String.valueOf(items)) + " قلم", 10.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
                    box.addView(MeelanoUiKit.text(h, value > 0 ? MeelanoCharts.compact(value) : "—", 8.6f, p.muted, false), MeelanoUiKit.lp(-1, -2));
                    LinearLayout.LayoutParams cl = MeelanoUiKit.lp(0, -2, 1f);
                    if (ri > 0) cl.leftMargin = h.dp(5);
                    r.addView(box, cl);
                }
                LinearLayout.LayoutParams rl = MeelanoUiKit.lp(-1, -2);
                if (a > 0) rl.topMargin = h.dp(5);
                card.addView(r, rl);
            }
            MeelanoUiKit.gap(h, card, 6);
            card.addView(MeelanoUiKit.text(h, "راهنمای تصمیم: A•تندگرد = هرگز نباید تمام شود • A•راکد = پول خوابیده در انبار، فوری اقدام شود • C•تندگرد = سود کم اما گردش‌ساز • C•راکد = حذف از سبد خرید",
                    8.8f, p.muted, false), MeelanoUiKit.lp(-1, -2));
            page.addView(card, MeelanoUiKit.lp(-1, -2));
        }

        JSONArray groups = analysis.optJSONArray("groups");
        if (groups != null && groups.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < groups.length() && i < 20; i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g == null) continue;
                rows.add(new String[]{g.optString("name", "—"), h.fa(String.valueOf(g.optInt("items"))) + " قلم",
                        h.money(g.optDouble("stockValue")), h.money(g.optDouble("salesValue")),
                        h.fa(String.format(java.util.Locale.US, "%.1f", g.optDouble("share"))) + "٪"});
            }
            page.addView(MeelanoUiKit.table(h, "▤  تفکیک گروه کالا", new String[]{"گروه", "اقلام", "ارزش موجودی", "فروش کل", "سهم انبار"}, rows, p.accent, null), MeelanoUiKit.lp(-1, -2));
        }

        JSONArray top = analysis.optJSONArray("top");
        if (top != null && top.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            page.addView(productTable(h, "↗  پرفروش‌ترین کالاها", top, 10, null), MeelanoUiKit.lp(-1, -2));
        }
        JSONArray worst = analysis.optJSONArray("worst");
        if (worst != null && worst.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            page.addView(productTable(h, "↘  بی‌گردش‌ترین کالاها (سرمایهٔ خواب)", worst, 10, null), MeelanoUiKit.lp(-1, -2));
        }
    }

    private static int cellTone(MeelanoPalette p, int abcIndex, int rotIndex) {
        if (rotIndex == 3) return p.danger;              // راکد
        if (abcIndex == 0 && rotIndex == 0) return p.success;  // A و تندگرد: طلایی‌ترین وضعیت
        if (abcIndex == 2 && rotIndex == 0) return p.info;
        if (rotIndex >= 2) return p.warning;
        return p.accent2;
    }

    // ================================================================ ۳) تأمین و سفارش
    private static void renderSupply(final MeelanoUiHost h, LinearLayout page, JSONObject analysis) {
        MeelanoPalette p = h.colors();
        JSONObject params = analysis.optJSONObject("params");
        boolean timed = params != null && params.optDouble("historyDays", 0) > 0;
        page.addView(MeelanoUiKit.sectionHeader(h, "برنامهٔ تأمین و سفارش",
                timed ? "نقطهٔ سفارش، ذخیرهٔ اطمینان و Economic Order Quantity بر پایهٔ بازهٔ واقعی فروش"
                        : "برای محاسبهٔ دقیق نقطهٔ سفارش، طول بازهٔ فروش لازم است؛ اکنون کالاهای پرفروشِ تمام‌شده و روند گردش نمایش داده می‌شود",
                "⇅", p.warning), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        JSONArray reorder = analysis.optJSONArray("reorder");
        if (reorder == null || reorder.length() == 0) {
            page.addView(MeelanoUiKit.emptyState(h, "✓", "هیچ کالایی زیر نقطهٔ سفارش نیست",
                    "موجودی همهٔ کالاهای فعال بالای حد سفارش است. این وضعیت مطلوب است.", null, null), MeelanoUiKit.lp(-1, -2));
            return;
        }
        int shown = Math.min(reorder.length(), PAGE_SIZE);
        double orderValue = 0;
        List<String[]> rows = new ArrayList<>();
        JSONArray slice = new JSONArray();
        for (int i = pageIndex * PAGE_SIZE; i < reorder.length() && i < shown + pageIndex * PAGE_SIZE; i++) {
            JSONObject o = reorder.optJSONObject(i);
            if (o == null) continue;
            double qty = o.optDouble("suggestedOrder");
            orderValue += qty * o.optDouble("buyPrice");
            rows.add(new String[]{o.optString("name", o.optString("code", "—")),
                    h.grouped(o.optDouble("stock")),
                    o.optDouble("reorderPoint") > 0 ? h.grouped(o.optDouble("reorderPoint")) : "—",
                    qty > 0 ? h.grouped(qty) : "—",
                    h.money(qty * o.optDouble("buyPrice"))});
            slice.put(o);
        }
        page.addView(MeelanoUiKit.table(h, "⇅  کالاهای نیازمند تأمین (" + h.fa(String.valueOf(reorder.length())) + " قلم)",
                new String[]{"کالا", "موجودی", "حد سفارش", "پیشنهاد سفارش", "مبلغ برآوردی"}, rows, p.warning, null), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        LinearLayout sum = MeelanoUiKit.card(h, p.warning);
        sum.addView(MeelanoUiKit.kv(h, "مبلغ برآوردی این صفحه", h.money(orderValue), p.warning), MeelanoUiKit.lp(-1, -2));
        sum.addView(MeelanoUiKit.kv(h, "تعداد کالای نمایش‌داده‌شده", h.fa(String.valueOf(rows.size())) + " قلم", p.accent), MeelanoUiKit.lp(-1, -2));
        if (!timed) sum.addView(MeelanoUiKit.text(h, "ℹ  با انتخاب بازهٔ فروش (۹۰ روز/۶ ماه/یک سال) در نسخهٔ بعدی، EOQ و ذخیرهٔ اطمینان دقیق محاسبه می‌شود. تا آن زمان پیشنهاد سفارش بر پایهٔ نسبت فروش به موجودی است.",
                8.8f, p.muted, false), MeelanoUiKit.lp(-1, -2));
        page.addView(sum, MeelanoUiKit.lp(-1, -2));
        pager(h, page, reorder.length());
    }

    // ================================================================ ۴) سرمایهٔ راکد
    private static void renderDead(final MeelanoUiHost h, LinearLayout page, JSONObject analysis) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "سرمایهٔ راکد انبار",
                "کالاهایی که در انبار مانده‌اند و فروشی ندارند — هر ریال از این موجودی، ریال خوابیده است",
                "⧗", p.danger), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        JSONArray dead = analysis.optJSONArray("dead");
        if (dead == null || dead.length() == 0) {
            page.addView(MeelanoUiKit.emptyState(h, "✓", "کالای راکد ندارید",
                    "همهٔ کالاهای موجود سابقهٔ فروش دارند. گردش انبار در وضعیت سالمی است.", null, null), MeelanoUiKit.lp(-1, -2));
            return;
        }
        JSONObject totals = analysis.optJSONObject("totals");
        LinearLayout kpi = MeelanoUiKit.kpi(h, "سرمایهٔ خواب", h.money(totals.optDouble("deadValue")),
                h.fa(String.valueOf(totals.optInt("deadCount"))) + " قلم کالای بی‌فروش", "⧗", p.danger, null);
        MeelanoUiKit.kpiGrid(h, page, java.util.Collections.<View>singletonList(kpi), 1, 8);
        MeelanoUiKit.gap(h, page, 8);

        List<String[]> rows = new ArrayList<>();
        for (int i = pageIndex * PAGE_SIZE; i < dead.length() && i < PAGE_SIZE * (pageIndex + 1); i++) {
            JSONObject o = dead.optJSONObject(i);
            if (o == null) continue;
            double value = o.optDouble("valueCost");
            double margin = o.optDouble("marginPercent");
            String action = margin >= 18 ? "فروش ویژه با تخفیف تا " + h.fa(String.valueOf(Math.max(3, Math.round(margin / 2)))) + "٪"
                    : margin > 0 ? "تسویه با قیمت تمام‌شده + " + h.fa(String.valueOf(Math.max(3, Math.round(margin)))) + "٪"
                    : "تسویه/تهاتر یا خروج از سبد خرید";
            rows.add(new String[]{o.optString("name", o.optString("code", "—")), h.grouped(o.optDouble("stock")),
                    h.money(value), o.optDouble("sellPrice") > 0 ? h.money(o.optDouble("sellPrice")) : "بدون قیمت", action});
        }
        page.addView(MeelanoUiKit.table(h, "⧗  فهرست اقدام", new String[]{"کالا", "موجودی", "سرمایهٔ خواب", "قیمت فروش", "اقدام پیشنهادی"}, rows, p.danger, new MeelanoUiKit.RowTap() {
            @Override public void tap(int index, String[] row) {
                h.notify("برای این کالا می‌توانید از صفحهٔ کالاها پیش‌فاکتور با تخفیف بسازید.");
            }
        }), MeelanoUiKit.lp(-1, -2));
        pager(h, page, dead.length());
    }

    // ================================================================ ۵) کارت گردش کالا
    private static void renderKardex(final MeelanoUiHost h, LinearLayout page, JSONObject data, JSONArray products) {
        MeelanoPalette p = h.colors();
        JSONObject snapshot = data.optJSONObject("snapshot");
        page.addView(MeelanoUiKit.sectionHeader(h, "کارت گردش کالا (Kardex)",
                "ورود و خروج واقعی هر کالا از اسناد انبار آتیران، با فی و شمارهٔ سند",
                "⌛", p.info), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        // انتخاب کالا از میان پرارزش‌ترین‌ها (بدون تایپ‌کردن)
        JSONArray valuable = data.optJSONObject("analysis") == null ? null : data.optJSONObject("analysis").optJSONArray("valuable");
        if (valuable != null && valuable.length() > 0) {
            LinearLayout picker = MeelanoUiKit.card(h, p.info);
            picker.addView(MeelanoUiKit.text(h, "⌕  یک کالا را انتخاب کنید", 11.6f, p.text, true), MeelanoUiKit.lp(-1, -2));
            final List<String[]> codes = new ArrayList<>();
            LinearLayout chips = MeelanoUiKit.row(h);
            chips.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < valuable.length() && i < 8; i++) {
                final JSONObject o = valuable.optJSONObject(i);
                if (o == null) continue;
                final String code = o.optString("code", "");
                codes.add(new String[]{code, o.optString("name", "")});
                TextView choice = MeelanoUiKit.text(h, "◈  " + o.optString("name", code) + "   •   " + h.fa(code), 9.7f, p.text, false);
                choice.setPadding(h.dp(9), h.dp(8), h.dp(9), h.dp(8));
                choice.setBackground(MeelanoUiKit.stroke(p.alpha(p.info, p.light ? 20 : 38), h.dp(12), p.alpha(p.info, 80)));
                choice.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        focusCode = code;
                        h.goTo("warehouse");
                    }
                });
                MeelanoUiKit.press(choice, h);
                LinearLayout.LayoutParams cl = MeelanoUiKit.lp(-1, -2);
                cl.topMargin = h.dp(5);
                chips.addView(choice, cl);
            }
            picker.addView(chips, MeelanoUiKit.lp(-1, -2));
            page.addView(picker, MeelanoUiKit.lp(-1, -2));
            MeelanoUiKit.gap(h, page, 8);
        }

        JSONArray kardex = snapshot == null ? null : snapshot.optJSONArray("kardex");
        if (focusCode == null || focusCode.isEmpty()) {
            page.addView(MeelanoUiKit.emptyState(h, "⌛", "کالایی انتخاب نشده",
                    "برای دیدن کارت گردش، یکی از کالاهای بالا را لمس کنید.", null, null), MeelanoUiKit.lp(-1, -2));
            return;
        }
        if (kardex == null || kardex.length() == 0) {
            page.addView(MeelanoUiKit.emptyState(h, "▤", "گردشی برای کالای " + h.fa(focusCode) + " ثبت نشده",
                    "یا جدول گردش کالا در نسخهٔ آتیران شما وجود ندارد، یا این کالا هنوز سندی نخورده است.",
                    "انتخاب کالای دیگر", new Runnable() { @Override public void run() { focusCode = ""; h.goTo("warehouse"); } }), MeelanoUiKit.lp(-1, -2));
            return;
        }
        List<String[]> rows = new ArrayList<>();
        double in = 0, out = 0;
        for (int i = 0; i < kardex.length(); i++) {
            JSONObject m = kardex.optJSONObject(i);
            if (m == null) continue;
            double qty = m.optDouble("qty");
            int dir = m.optInt("direction", qty >= 0 ? 1 : -1);
            double amount = Math.abs(qty);
            if (dir >= 0) in += amount; else out += amount;
            String reason = m.optString("reason", dir >= 0 ? "ورود انبار" : "خروج انبار");
            rows.add(new String[]{h.fa(m.optString("date", "—").replace('-', '/')),
                    (dir >= 0 ? "▲ " : "▼ ") + reason,
                    h.grouped(amount),
                    m.optDouble("price") > 0 ? h.money(m.optDouble("price")) : "—",
                    m.optString("warehouse", "—"),
                    m.optString("document", "—")});
        }
        LinearLayout summary = MeelanoUiKit.card(h, p.info);
        summary.addView(MeelanoUiKit.kv(h, "کالا", focusName.isEmpty() ? h.fa(focusCode) : focusName, p.text), MeelanoUiKit.lp(-1, -2));
        summary.addView(MeelanoUiKit.kv(h, "جمع ورود در این فهرست", h.grouped(in), p.success), MeelanoUiKit.lp(-1, -2));
        summary.addView(MeelanoUiKit.kv(h, "جمع خروج در این فهرست", h.grouped(out), p.danger), MeelanoUiKit.lp(-1, -2));
        summary.addView(MeelanoUiKit.kv(h, "تعداد اسناد", h.fa(String.valueOf(rows.size())), p.accent), MeelanoUiKit.lp(-1, -2));
        page.addView(summary, MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.table(h, "⌛  گردش کالا (ورود/خروج از دفتر انبار آتیران)",
                new String[]{"تاریخ", "علت گردش", "تعداد (واحد شمارش)", "فی", "انبار", "سند"}, rows, p.info, null), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.text(h, "ℹ  علت گردش از کد رویداد انبار آتیران (act_id) خوانده می‌شود؛ برای کدهای ناشناخته فقط جهت نمایش داده می‌شود. تعداد هم به واحد شمارش (کارتن × واحد + تکی) تبدیل شده است.",
                8.7f, p.muted, false), MeelanoUiKit.lp(-1, -2));
    }

    // ================================================================ ۶) برچسب و بارکد
    private static void renderLabels(final MeelanoUiHost h, LinearLayout page, JSONArray products) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "برچسب، بارکد و QR",
                "برگهٔ A4 برچسب (۳×۸) با بارکد Code128 و QR — آمادهٔ چاپ روی برچسب چسبی یا کاغذ ساده",
                "🏷", p.success), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        LinearLayout help = MeelanoUiKit.card(h, p.success);
        help.addView(MeelanoUiKit.kv(h, "اندازهٔ هر برچسب", "۶۳ × ۳۴ میلی‌متر (سازگار با A4)", p.success), MeelanoUiKit.lp(-1, -2));
        help.addView(MeelanoUiKit.kv(h, "محتویات برچسب", "نام کالا • کد • بارکد Code128 • QR شناسه", p.text), MeelanoUiKit.lp(-1, -2));
        help.addView(MeelanoUiKit.text(h, "ℹ  بارکد تولیدشده با استاندارد Code128 و EAN-13 ساخته می‌شود و با بارکدخوان‌های معمول انبار خوانده می‌شود؛ کد QR هم شناسهٔ سری/تاریخ انقضا را در خود دارد.",
                8.8f, p.muted, false), MeelanoUiKit.lp(-1, -2));
        page.addView(help, MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        if (products == null || products.length() == 0) {
            page.addView(MeelanoUiKit.emptyState(h, "▤", "فهرست کالا در دسترس نیست",
                    "برای ساخت برچسب، فهرست کالاها لازم است. اتصال را بررسی کنید.", null, null), MeelanoUiKit.lp(-1, -2));
            return;
        }
        final List<JSONObject> chosen = new ArrayList<>();
        LinearLayout list = MeelanoUiKit.card(h, p.accent);
        list.addView(MeelanoUiKit.text(h, "کالاهای پیشنهادی برای برچسب (پرارزش‌ترین‌ها)", 11.6f, p.text, true), MeelanoUiKit.lp(-1, -2));
        int shown = 0;
        for (int i = 0; i < products.length() && shown < 10; i++) {
            final JSONObject o = products.optJSONObject(i);
            if (o == null) continue;
            double stock = o.optDouble("موجودی", o.optDouble("stock", 0));
            if (stock <= 0) continue;
            shown++;
            chosen.add(o);
            LinearLayout line = MeelanoUiKit.row(h);
            line.setPadding(0, h.dp(6), 0, h.dp(6));
            String name = o.optString("نام", o.optString("name", "—"));
            String code = o.optString("بارکد", "");
            if (code.trim().isEmpty()) code = o.optString("کد", "");
            LinearLayout copy = MeelanoUiKit.column(h);
            copy.addView(MeelanoUiKit.text(h, name, 10.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            copy.addView(MeelanoUiKit.text(h, "کد " + h.fa(code) + " • موجودی " + h.grouped(stock), 8.8f, p.muted, false), MeelanoUiKit.lp(-1, -2));
            line.addView(copy, MeelanoUiKit.lp(0, -2, 1f));
            final String codeFinal = code;
            line.addView(MeelanoUiKit.button(h, "🏷 چاپ", p.success, false, new Runnable() {
                @Override public void run() {
                    printLabels(h, codeFinal, name, 24);
                }
            }), MeelanoUiKit.lp(h.dp(78), h.dp(38)));
            list.addView(line, MeelanoUiKit.lp(-1, -2));
        }
        page.addView(list, MeelanoUiKit.lp(-1, -2));

        if (!chosen.isEmpty()) {
            MeelanoUiKit.gap(h, page, 9);
            page.addView(MeelanoUiKit.button(h, "🏷  چاپ برگهٔ کامل (۲۴ برچسب از ۱۰ کالای بالا)", p.accent, true, new Runnable() {
                @Override public void run() { printSheet(h, chosen); }
            }), MeelanoUiKit.lp(-1, h.dp(46)));
        }
    }

    private static void printLabels(MeelanoUiHost h, String code, String name, int copies) {
        try {
            byte[] pdf = MeelanoLabelPrint.singleLabelPdf(h.ctx(), code, name, copies, h.colors(), h.font(false), h.font(true));
            if (pdf == null) { h.notify("ساخت برچسب ممکن نشد."); return; }
            h.shareGeneratedFile("meelano-label-" + safeName(code) + ".pdf", "application/pdf", pdf, "برچسب " + name);
        } catch (Exception e) {
            h.notify("خطای ساخت برچسب: " + MeelanoWarehouse.shortMsg(e));
        }
    }

    private static void printSheet(MeelanoUiHost h, List<JSONObject> items) {
        try {
            List<MeelanoLabelPrint.Label> labels = new ArrayList<>();
            for (JSONObject o : items) {
                String name = o.optString("نام", o.optString("name", "—"));
                String code = o.optString("بارکد", "");
                if (code.trim().isEmpty()) code = o.optString("کد", "");
                double price = o.optDouble("قیمت_فروش", 0);
                labels.add(new MeelanoLabelPrint.Label(code, name, price, 2));
            }
            byte[] pdf = MeelanoLabelPrint.sheetPdf(h.ctx(), labels, h.colors(), h.font(false), h.font(true));
            if (pdf == null) { h.notify("ساخت برگهٔ برچسب ممکن نشد."); return; }
            h.shareGeneratedFile("meelano-labels-sheet.pdf", "application/pdf", pdf, "برگهٔ برچسب انبار");
        } catch (Exception e) {
            h.notify("خطای ساخت برگهٔ برچسب: " + MeelanoWarehouse.shortMsg(e));
        }
    }

    // ================================================================ کمکی‌ها
    private static LinearLayout productTable(final MeelanoUiHost h, String title, JSONArray items, int cap, final String[] extraHeaders) {
        MeelanoPalette p = h.colors();
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < items.length() && i < cap; i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String abc = o.optString("abc", "");
            String rot = rotLabel(o.optString("rot", ""));
            rows.add(new String[]{o.optString("name", o.optString("code", "—")),
                    h.grouped(o.optDouble("stock")),
                    h.money(o.optDouble("valueCost")),
                    h.money(o.optDouble("salesAmount")),
                    h.fa(String.valueOf(Math.round(o.optDouble("marginPercent")))) + "٪",
                    abc + " • " + rot});
        }
        return MeelanoUiKit.table(h, title, new String[]{"کالا", "موجودی", "ارزش انبار", "فروش", "حاشیهٔ سود", "کلاس"}, rows, p.accent,
                new MeelanoUiKit.RowTap() {
                    @Override public void tap(int index, String[] row) { h.notify("برای جزئیات کامل، کالا را در صفحهٔ «کالا» باز کنید."); }
                });
    }

    private static String rotLabel(String rot) {
        if ("F".equals(rot)) return "تندگرد";
        if ("M".equals(rot)) return "متوسط";
        if ("S".equals(rot)) return "کندگرد";
        if ("D".equals(rot)) return "راکد";
        return "—";
    }

    private static void pager(final MeelanoUiHost h, LinearLayout page, int total) {
        int pages = (int) Math.ceil(total / (double) PAGE_SIZE);
        if (pages <= 1) return;
        MeelanoPalette p = h.colors();
        MeelanoUiKit.gap(h, page, 8);
        LinearLayout bar = MeelanoUiKit.row(h);
        bar.addView(MeelanoUiKit.text(h, "صفحهٔ " + h.fa(String.valueOf(pageIndex + 1)) + " از " + h.fa(String.valueOf(pages)), 9.6f, p.muted, true), MeelanoUiKit.lp(0, -2, 1f));
        if (pageIndex > 0) bar.addView(MeelanoUiKit.button(h, "› قبلی", p.accent, false, new Runnable() {
            @Override public void run() { pageIndex = Math.max(0, pageIndex - 1); h.goTo("warehouse"); }
        }), MeelanoUiKit.lp(h.dp(86), h.dp(38)));
        if (pageIndex < pages - 1) bar.addView(MeelanoUiKit.button(h, "بعدی ‹", p.accent, true, new Runnable() {
            @Override public void run() { pageIndex = pageIndex + 1; h.goTo("warehouse"); }
        }), MeelanoUiKit.lp(h.dp(86), h.dp(38)));
        page.addView(bar, MeelanoUiKit.lp(-1, -2));
    }

    private static void exportCsv(MeelanoUiHost h, JSONObject analysis) {
        try {
            String csv = MeelanoWarehouse.toCsv(analysis);
            h.shareGeneratedFile("meelano-anbar-" + System.currentTimeMillis() + ".csv", "text/csv",
                    csv.getBytes("UTF-8"), "خروجی انبار (CSV)");
        } catch (Exception e) {
            h.notify("خطای خروجی CSV: " + MeelanoWarehouse.shortMsg(e));
        }
    }

    private static void exportSummary(MeelanoUiHost h, JSONObject analysis) {
        try {
            JSONObject t = analysis.optJSONObject("totals");
            StringBuilder b = new StringBuilder();
            b.append("گزارش انبار «پخش درخشان»\n");
            b.append("تاریخ گزارش: ").append(h.lastRefresh("warehouse")).append("\n\n");
            b.append("• تعداد کالا: ").append(h.fa(String.valueOf(t.optInt("items")))).append(" قلم\n");
            b.append("• ارزش موجودی (بهای خرید): ").append(h.money(t.optDouble("stockValueCost"))).append("\n");
            b.append("• ارزش فروش موجودی: ").append(h.money(t.optDouble("stockValueSell"))).append("\n");
            b.append("• سود بالقوهٔ موجودی: ").append(h.money(t.optDouble("potentialMargin"))).append("\n");
            b.append("• کالای راکد: ").append(h.fa(String.valueOf(t.optInt("deadCount")))).append(" قلم — ").append(h.money(t.optDouble("deadValue"))).append("\n");
            b.append("• زیر نقطهٔ سفارش: ").append(h.fa(String.valueOf(t.optInt("reorderCount")))).append(" قلم\n");
            b.append("• هزینهٔ نگهداری سالانه: ").append(h.money(t.optDouble("holdingCostYear"))).append("\n");
            JSONArray alerts = analysis.optJSONArray("alerts");
            if (alerts != null && alerts.length() > 0) {
                b.append("\nهشدارها:\n");
                for (int i = 0; i < alerts.length(); i++) {
                    JSONObject a = alerts.optJSONObject(i);
                    if (a != null) b.append("— ").append(a.optString("title")).append(": ").append(a.optString("text")).append("\n");
                }
            }
            h.shareGeneratedFile("meelano-anbar-report.txt", "text/plain", b.toString().getBytes("UTF-8"), "گزارش انبار");
        } catch (Exception e) {
            h.notify("خطای گزارش: " + MeelanoWarehouse.shortMsg(e));
        }
    }

    private static String safeName(String raw) {
        String s = raw == null ? "kala" : raw.trim();
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isLetterOrDigit(c) || c == '-' || c == '_') b.append(c);
        return b.length() == 0 ? "kala" : b.toString();
    }

    /** رنگ متن خوانا روی یک سطح رنگی. */
    static int readable(MeelanoPalette p, int accent) { return p.onColorFor(accent == 0 ? Color.BLACK : accent); }
}
