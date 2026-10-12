package ir.meelano.android;

import android.view.View;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * صفحهٔ «اتاق فرمان مالی» — نگاه حسابداری/مدیریتی به اعداد برنامه.
 *
 * <p>داده‌ها از یک تماس واحد {@link ManagerAnalytics#fetch} می‌آید (فروش، خرید، مطالبات، چک‌ها،
 * ریسک اعتباری، نمودار روند) و تحلیل‌ها از {@link MeelanoFinance} و {@link MeelanoWarehouse}.
 * هیچ عددی ساختهٔ ذهن برنامه نیست: هر شاخص یا مستقیماً از جدول آتیران است یا فرمول صریح دارد
 * که در همان کارت توضیح داده می‌شود.</p>
 */
final class MeelanoFinanceUi {
    private MeelanoFinanceUi() { }

    static String tab = "overview";

    private static final String[][] TABS = {
            {"overview", "نمای کل", "◎"},
            {"collect", "وصول مطالبات", "💵"},
            {"checks", "چک و سررسید", "✓"},
            {"pricing", "قیمت‌گذاری و سود", "％"},
            {"cash", "جریان نقدی", "⇅"}
    };

    /** نرخ مالیات بر ارزش افزودهٔ پیش‌فرض (قابل تغییر در تنظیمات). */
    static double vatPercent = 10;

    static void render(final MeelanoUiHost h, final LinearLayout page, final JSONObject metrics, final JSONObject warehouseAnalysis, final JSONArray products) {
        MeelanoPalette p = h.colors();
        page.removeAllViews();
        JSONObject sales = metrics == null ? null : metrics.optJSONObject("sales");
        JSONObject purchases = metrics == null ? null : metrics.optJSONObject("purchases");

        page.addView(MeelanoUiKit.hero(h, "اتاق فرمان مالی",
                "مطالبات، چک‌ها، حاشیهٔ سود و جریان نقدی — یک جا و با فرمول شفاف",
                "◎", p.accent, sales == null ? "بدون داده" : MeelanoCharts.compact(sales.optDouble("total")) + " ریال"), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 9);
        page.addView(MeelanoUiKit.chipBar(h, TABS, tab, new MeelanoUiKit.Pick() {
            @Override public void pick(String key) { tab = key; h.goTo("finance"); }
        }), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        if (metrics == null) {
            page.addView(MeelanoUiKit.emptyState(h, "◎", "دادهٔ مالی در دسترس نیست",
                    "برای این صفحه، اتصال به سرور آتیران لازم است. پس از اتصال دوباره تلاش کنید.",
                    "تلاش دوباره", new Runnable() { @Override public void run() { h.goTo("finance"); } }), MeelanoUiKit.lp(-1, -2));
            return;
        }

        JSONObject receivables = metrics.optJSONObject("receivables");
        JSONObject checks = metrics.optJSONObject("checkBuckets");
        JSONObject totals = warehouseAnalysis == null ? null : warehouseAnalysis.optJSONObject("totals");

        if ("collect".equals(tab)) { renderCollect(h, page, metrics, receivables); return; }
        if ("checks".equals(tab)) { renderChecks(h, page, checks); return; }
        if ("pricing".equals(tab)) { renderPricing(h, page, products); return; }
        if ("cash".equals(tab)) { renderCash(h, page, metrics, receivables, sales, purchases); return; }

        // ---------------- نمای کل
        List<View> tiles = new ArrayList<>();
        double salesTotal = sales == null ? 0 : sales.optDouble("total");
        double prevTotal = sales == null ? 0 : sales.optDouble("prevTotal");
        double delta = prevTotal > 0 ? (salesTotal - prevTotal) / prevTotal * 100.0 : 0;
        tiles.add(MeelanoUiKit.kpi(h, "فروش دوره", h.money(salesTotal),
                prevTotal > 0 ? "دورهٔ قبل: " + h.money(prevTotal) + " (" + h.fa(String.format(java.util.Locale.US, "%.0f", delta)) + "٪)" : "بدون دورهٔ مقایسه",
                "↗", p.success, null));
        tiles.add(MeelanoUiKit.kpi(h, "خرید دوره", h.money(purchases == null ? 0 : purchases.optDouble("total")),
                (purchases == null ? 0 : h.fa(String.valueOf(purchases.optLong("docs")))) + " سند خرید", "↙", p.warning, null));
        tiles.add(MeelanoUiKit.kpi(h, "مطالبات وصول‌نشده", h.money(receivables == null ? 0 : receivables.optDouble("total")),
                (receivables == null ? "۰" : h.fa(String.valueOf(receivables.optLong("count")))) + " حساب بدهکار", "💵", p.danger, null));
        double overTotal = checks != null && checks.optJSONObject("over") != null ? checks.optJSONObject("over").optDouble("total") : 0;
        tiles.add(MeelanoUiKit.kpi(h, "چک سررسیدگذشته", h.money(overTotal),
                checks != null && checks.optJSONObject("over") != null ? h.fa(String.valueOf(checks.optJSONObject("over").optLong("count"))) + " فقره" : "—", "⚠", p.danger, null));
        if (totals != null) {
            tiles.add(MeelanoUiKit.kpi(h, "حاشیهٔ سود ناخالص", h.fa(String.format(java.util.Locale.US, "%.1f", totals.optDouble("grossMarginPercent"))) + "٪",
                    "سود ناخالص: " + h.money(totals.optDouble("grossProfit")), "％", p.success, null));
            tiles.add(MeelanoUiKit.kpi(h, "سرمایهٔ قفل‌شده در انبار", h.money(totals.optDouble("deadValue")),
                    "کالای بی‌فروش: " + h.fa(String.valueOf(totals.optInt("deadCount"))) + " قلم", "⧗", p.tone("warn"), null));
        }
        MeelanoUiKit.kpiGrid(h, page, tiles, 2, 8);

        // ---------------- روند فروش
        JSONArray trend = metrics.optJSONArray("trend");
        if (trend != null && trend.length() > 1) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout card = MeelanoUiKit.card(h, p.info);
            card.addView(MeelanoUiKit.text(h, "▮  روند فروش روزهای اخیر", 12.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            MeelanoUiKit.gap(h, card, 6);
            MeelanoCharts.Area area = new MeelanoCharts.Area(h.ctx(), p.info, p.text, p.muted, h.font(false));
            List<MeelanoCharts.Point> pts = new ArrayList<>();
            for (int i = trend.length() - 1; i >= 0; i--) {
                JSONObject o = trend.optJSONObject(i);
                if (o == null) continue;
                pts.add(new MeelanoCharts.Point(h.fa(o.optString("label", "—").replace('-', '/')), o.optDouble("value"), p.info));
            }
            area.setPoints(pts, MeelanoCharts.RIAL);
            card.addView(area, MeelanoUiKit.lp(-1, h.dp(150)));
            page.addView(card, MeelanoUiKit.lp(-1, -2));
        }

        // ---------------- دسته‌بندی سنی مطالبات
        JSONArray aging = metrics.optJSONArray("aging");
        if (aging != null && aging.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout card = MeelanoUiKit.card(h, p.danger);
            card.addView(MeelanoUiKit.text(h, "⧗  سن مطالبات (بر پایهٔ سررسید اسناد)", 12.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            double sum = 0;
            for (int i = 0; i < aging.length(); i++) sum += aging.optJSONObject(i) == null ? 0 : aging.optJSONObject(i).optDouble("value");
            for (int i = 0; i < aging.length(); i++) {
                JSONObject b = aging.optJSONObject(i);
                if (b == null) continue;
                MeelanoUiKit.gap(h, card, 6);
                double v = b.optDouble("value");
                card.addView(MeelanoUiKit.progress(h, b.optString("label", "—") + " • " + h.fa(String.valueOf(b.optLong("docs"))) + " سند",
                        sum > 0 ? v / sum : 0, h.money(v), i == 0 ? p.success : i == aging.length() - 1 ? p.danger : p.warning), MeelanoUiKit.lp(-1, -2));
            }
            page.addView(card, MeelanoUiKit.lp(-1, -2));
        }

        // ---------------- مالیات دوره (برآورد)
        MeelanoUiKit.gap(h, page, 8);
        LinearLayout tax = MeelanoUiKit.card(h, p.accent2);
        double[] vat = MeelanoFinance.vat(salesTotal, vatPercent, true);
        tax.addView(MeelanoUiKit.kv(h, "فروش دوره (با ارزش افزوده)", h.money(salesTotal), p.text), MeelanoUiKit.lp(-1, -2));
        tax.addView(MeelanoUiKit.kv(h, "اصل فروش (بدون مالیات)", h.money(vat[0]), p.text), MeelanoUiKit.lp(-1, -2));
        tax.addView(MeelanoUiKit.kv(h, "مالیات بر ارزش افزوده (" + h.fa(String.valueOf(Math.round(vatPercent))) + "٪)", h.money(vat[1]), p.accent2), MeelanoUiKit.lp(-1, -2));
        tax.addView(MeelanoUiKit.text(h, "ℹ  محاسبهٔ مالیات این کارت «برآورد مدیریتی» است؛ نرخ را با دکمهٔ زیر تنظیم کنید. برای ارسال رسمی صورتحساب، بخش «مودیان» را ببینید.",
                8.7f, p.muted, false), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, tax, 8);
        tax.addView(MeelanoUiKit.button(h, "⚙  تغییر نرخ مالیات", p.accent2, false, new Runnable() {
            @Override public void run() { vatSheet(h); }
        }), MeelanoUiKit.lp(-1, h.dp(42)));
        page.addView(tax, MeelanoUiKit.lp(-1, -2));

        // ---------------- سود گروه‌ها
        JSONArray groups = warehouseAnalysis == null ? null : warehouseAnalysis.optJSONArray("groups");
        if (groups != null && groups.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < groups.length() && i < 12; i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g == null) continue;
                double margin = g.optDouble("salesValue") > 0 ? g.optDouble("grossProfit") / g.optDouble("salesValue") * 100.0 : 0;
                rows.add(new String[]{g.optString("name", "—"), h.money(g.optDouble("salesValue")),
                        h.money(g.optDouble("grossProfit")), h.fa(String.format(java.util.Locale.US, "%.1f", margin)) + "٪",
                        h.fa(String.format(java.util.Locale.US, "%.2f", g.optDouble("turnover")))});
            }
            page.addView(MeelanoUiKit.table(h, "▤  سود و گردش به تفکیک گروه کالا",
                    new String[]{"گروه", "فروش", "سود ناخالص", "حاشیه", "گردش"}, rows, p.success, null), MeelanoUiKit.lp(-1, -2));
        }
    }

    /** شیت تنظیم نرخ مالیات بر ارزش افزوده (در SharedPreferences ذخیره می‌شود). */
    private static void vatSheet(final MeelanoUiHost h) {
        MeelanoPalette p = h.colors();
        LinearLayout body = MeelanoUiKit.column(h);
        final android.widget.EditText rate = MeelanoUiKit.field(h, "مثلاً ۱۰", String.valueOf(Math.round(vatPercent)), true);
        body.addView(MeelanoUiKit.fieldRow(h, "نرخ مالیات بر ارزش افزوده (٪)", rate), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, body, 10);
        body.addView(MeelanoUiKit.alert(h, "info", "چرا اینجا؟",
                "این نرخ فقط برای «برآورد» داخلی مالیات دوره در داشبورد مالی است. ارسال رسمی صورتحساب‌ها با تنظیمات سامانهٔ مودیان انجام می‌شود."), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.sheet(h, "نرخ مالیات", "％", p.accent2, body, "ذخیره", new Runnable() {
            @Override public void run() {
                double v = MeelanoUiKit.number(rate, vatPercent);
                vatPercent = Math.max(0, Math.min(50, v));
                h.putSetting("vat_percent", String.valueOf(vatPercent));
                h.notify("نرخ مالیات ذخیره شد: " + h.fa(String.valueOf(Math.round(vatPercent))) + "٪");
                h.goTo("finance");
            }
        }, "انصراف");
    }

    // ---------------- وصول مطالبات
    private static void renderCollect(final MeelanoUiHost h, LinearLayout page, JSONObject metrics, JSONObject receivables) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "وصول مطالبات و ریسک اعتباری",
                "اولویت‌بندی تماس با بدهکاران بر پایهٔ سن واقعی اسناد (dbo.dif_date_alan) — تصمیم وصول در یک نگاه",
                "💵", p.danger), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        // منبع اصلی: سندهای وصول‌نشده به تفکیک مشتری؛ در نبود آن، ماندهٔ مشتریان از CUSTOMERS
        JSONArray rows = new JSONArray();
        JSONArray perParty = metrics.optJSONArray("debtorAging");
        boolean realAges = perParty != null && perParty.length() > 0;
        JSONArray source = realAges ? perParty : metrics.optJSONArray("debtors");
        for (int i = 0; source != null && i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) continue;
            JSONObject row = new JSONObject();
            try {
                row.put("party", o.optString("party", "—"));
                row.put("code", o.optString("code", ""));
                row.put("amount", o.optDouble("amount"));
                row.put("days", o.optInt("days", 0));
            } catch (Exception ignored) { }
            rows.put(row);
        }
        JSONObject analysis = MeelanoFinance.analyseReceivables(rows);
        final JSONObject analysisFinal = analysis;

        JSONArray credit = metrics.optJSONArray("credit");
        int blocked = 0;
        for (int i = 0; credit != null && i < credit.length(); i++) {
            JSONObject o = credit.optJSONObject(i);
            if (o != null && o.optBoolean("blocked")) blocked++;
        }

        List<View> tiles = new ArrayList<>();
        tiles.add(MeelanoUiKit.kpi(h, "جمع مطالبات", h.money(receivables == null ? analysis.optDouble("total") : receivables.optDouble("total")),
                h.fa(String.valueOf(analysis.optLong("count"))) + " مشتری بدهکار", "💵", p.danger, null));
        tiles.add(MeelanoUiKit.kpi(h, "معوق بیش از ۳۰ روز", h.money(analysis.optDouble("overdue")),
                h.fa(String.format(java.util.Locale.US, "%.1f", analysis.optDouble("overdueShare"))) + "٪ از کل مطالبات", "⧗", p.warning, null));
        tiles.add(MeelanoUiKit.kpi(h, realAges ? "سن واقعی اسناد" : "سن تقریبی (بدون سند)",
                realAges ? "داده واقعی" : "برآورد", realAges ? "از dbo.sailfact" : "ستون‌های سند در دسترس نبود", "◎", realAges ? p.success : p.warning, null));
        tiles.add(MeelanoUiKit.kpi(h, "حساب‌های مسدودشدهٔ اعتباری", h.fa(String.valueOf(blocked)),
                "از جدول Sys_Mandeh_Customer", "◉", p.warning, null));
        MeelanoUiKit.kpiGrid(h, page, tiles, 2, 8);

        // دسته‌های سنی
        JSONArray buckets = analysis.optJSONArray("buckets");
        if (buckets != null) {
            MeelanoUiKit.gap(h, page, 8);
            LinearLayout card = MeelanoUiKit.card(h, p.danger);
            card.addView(MeelanoUiKit.text(h, "⧗  سبد سنی مطالبات", 12.2f, p.text, true), MeelanoUiKit.lp(-1, -2));
            double total = Math.max(1, analysis.optDouble("total"));
            for (int i = 0; i < buckets.length(); i++) {
                JSONObject b = buckets.optJSONObject(i);
                if (b == null || b.optDouble("total") <= 0) continue;
                MeelanoUiKit.gap(h, card, 6);
                card.addView(MeelanoUiKit.progress(h, b.optString("label") + " • " + h.fa(String.valueOf(b.optLong("count"))) + " مشتری",
                        b.optDouble("total") / total, h.money(b.optDouble("total")) + " (" + h.fa(String.format(java.util.Locale.US, "%.0f", b.optDouble("share"))) + "٪)",
                        i == 0 ? p.success : i >= 4 ? p.danger : p.warning), MeelanoUiKit.lp(-1, -2));
            }
            page.addView(card, MeelanoUiKit.lp(-1, -2));
        }

        // اولویت تماس
        JSONArray priority = analysis.optJSONArray("priority");
        if (priority != null && priority.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            List<String[]> tableRows = new ArrayList<>();
            for (int i = 0; i < priority.length() && i < 20; i++) {
                JSONObject o = priority.optJSONObject(i);
                if (o == null) continue;
                double amount = o.optDouble("amount");
                String action = amount >= 500000000 ? "تماس مدیر مالی + توافق پرداخت" : o.optInt("days") > 90 ? "ارسال اظهارنامه/وکیل" : "پیگیری تلفنی";
                tableRows.add(new String[]{o.optString("party", "—"), h.money(amount),
                        o.optInt("days") > 0 ? h.fa(String.valueOf(o.optInt("days"))) + " روز" : "جاری",
                        h.fa(String.format(java.util.Locale.US, "%.0f", o.optDouble("score"))), action});
            }
            page.addView(MeelanoUiKit.table(h, "⚠  اولویت وصول (امتیاز = مبلغ × ضریب تأخیر)",
                    new String[]{"مشتری", "مانده", "تأخیر", "امتیاز", "اقدام پیشنهادی"}, tableRows, p.danger, new MeelanoUiKit.RowTap() {
                        @Override public void tap(int index, String[] row) {
                            h.notify("پیگیری «" + row[0] + "»: در صفحهٔ مشتریان جست‌وجو و یادداشت تماس ثبت کنید.");
                        }
                    }), MeelanoUiKit.lp(-1, -2));
        }

        if (credit != null && credit.length() > 0) {
            MeelanoUiKit.gap(h, page, 8);
            List<String[]> tableRows = new ArrayList<>();
            for (int i = 0; i < credit.length() && i < 20; i++) {
                JSONObject c = credit.optJSONObject(i);
                if (c == null) continue;
                double mandeh = c.optDouble("mandeh"), etebar = c.optDouble("etebar");
                String state = c.optBoolean("blocked") ? "مسدود" : (etebar > 0 && mandeh > etebar ? "عبور از سقف اعتبار" : "سالم");
                tableRows.add(new String[]{c.optString("party", "—"), h.money(mandeh), etebar > 0 ? h.money(etebar) : "—", state});
            }
            page.addView(MeelanoUiKit.table(h, "◉  ریسک اعتباری مشتریان",
                    new String[]{"مشتری", "مانده", "سقف اعتبار", "وضعیت"}, tableRows, p.warning, null), MeelanoUiKit.lp(-1, -2));
        }

        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.buttonRow(h,
                new String[]{"⬇  خروجی CSV اولویت وصول", "↻  تازه‌سازی داده"},
                new int[]{p.accent, p.info}, new boolean[]{true, false}, new Runnable[]{
                        new Runnable() {
                            @Override public void run() {
                                try {
                                    String csv = MeelanoFinance.receivablesCsv(analysisFinal);
                                    h.shareGeneratedFile("meelano-receivables.csv", "text/csv", csv.getBytes("UTF-8"), "اولویت وصول مطالبات");
                                } catch (Exception e) {
                                    h.notify("خطای خروجی: " + MeelanoWarehouse.shortMsg(e));
                                }
                            }
                        },
                        new Runnable() { @Override public void run() { tab = "overview"; h.goTo("finance"); } }
                }), MeelanoUiKit.lp(-1, h.dp(46)));
    }

    // ---------------- چک‌ها
    private static void renderChecks(final MeelanoUiHost h, LinearLayout page, JSONObject checks) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "چک‌ها و سررسیدها",
                "چک سررسیدگذشته، چک‌های هفت روز آینده و چک‌های جاری — با جمع ریالی هر دسته",
                "✓", p.info), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        if (checks == null || checks.length() == 0) {
            page.addView(MeelanoUiKit.emptyState(h, "✓", "چکی در سیستم ثبت نشده",
                    "جدول چک‌های دریافتی (getchk) در این نسخهٔ آتیران داده‌ای ندارد یا ستون‌های لازم در دسترس نیست.",
                    "مشاهدهٔ جدول چک‌ها", new Runnable() { @Override public void run() { h.goTo("checks"); } }), MeelanoUiKit.lp(-1, -2));
            return;
        }
        String[][] defs = {{"over", "سررسیدگذشته (فوری)", "danger"}, {"soon", "هفت روز آینده", "warn"}, {"ok", "جاری", "success"}};
        List<View> tiles = new ArrayList<>();
        for (String[] d : defs) {
            JSONObject b = checks.optJSONObject(d[0]);
            if (b == null) continue;
            tiles.add(MeelanoUiKit.kpi(h, d[1], h.money(b.optDouble("total")),
                    h.fa(String.valueOf(b.optLong("count"))) + " فقره چک", "✓", p.tone(d[2]), null));
        }
        MeelanoUiKit.kpiGrid(h, page, tiles, 1, 8);
        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.button(h, "▤  مشاهدهٔ جدول کامل چک‌ها", p.accent, false, new Runnable() {
            @Override public void run() { h.goTo("checks"); }
        }), MeelanoUiKit.lp(-1, h.dp(44)));
    }

    // ---------------- قیمت‌گذاری و سود
    private static void renderPricing(final MeelanoUiHost h, LinearLayout page, JSONArray products) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "قیمت‌گذاری هدف‌محور و سود",
                "برای هر کالا: حاشیهٔ سود فعلی، قیمت پیشنهادی برای رسیدن به حاشیهٔ هدف و اثر ریالی روی سود",
                "％", p.success), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        final double target = 20;
        final JSONArray plan = MeelanoFinance.pricingPlan(products, target, 1000);
        JSONObject summary = MeelanoFinance.pricingSummary(plan);
        List<View> tiles = new ArrayList<>();
        tiles.add(MeelanoUiKit.kpi(h, "کالاهای زیر حاشیهٔ هدف", h.fa(String.valueOf(summary.optInt("underpriced"))) + " قلم",
                "حاشیهٔ هدف: " + h.fa(String.valueOf((int) target)) + "٪", "％", p.warning, null));
        tiles.add(MeelanoUiKit.kpi(h, "اثر تجمعی اصلاح قیمت", h.money(summary.optDouble("profitImpact")),
                "بر پایهٔ تعداد فروش ثبت‌شدهٔ هر کالا", "↗", p.success, null));
        MeelanoUiKit.kpiGrid(h, page, tiles, 2, 8);
        MeelanoUiKit.gap(h, page, 8);

        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < plan.length() && i < 20; i++) {
            JSONObject o = plan.optJSONObject(i);
            if (o == null) continue;
            rows.add(new String[]{o.optString("name", "—"),
                    h.money(o.optDouble("cost")),
                    h.money(o.optDouble("price")),
                    h.fa(String.format(java.util.Locale.US, "%.0f", o.optDouble("marginPercent"))) + "٪",
                    h.money(o.optDouble("suggestedPrice")),
                    (o.optDouble("gap") >= 0 ? "+" : "−") + h.money(Math.abs(o.optDouble("gap")))});
        }
        page.addView(MeelanoUiKit.table(h, "％  پیشنهاد قیمت (۲۰٪ حاشیه)",
                new String[]{"کالا", "بهای خرید", "قیمت فعلی", "حاشیهٔ فعلی", "قیمت پیشنهادی", "اختلاف"}, rows, p.success, null), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        page.addView(MeelanoUiKit.button(h, "⬇  خروجی CSV برنامهٔ قیمت", p.accent, false, new Runnable() {
            @Override public void run() {
                try {
                    StringBuilder b = new StringBuilder("\uFEFFکد;نام;بهای خرید;قیمت فعلی;حاشیه;قیمت پیشنهادی\r\n");
                    for (int i = 0; i < plan.length(); i++) {
                        JSONObject o = plan.optJSONObject(i);
                        if (o == null) continue;
                        b.append(o.optString("code")).append(';').append(o.optString("name").replace(';', ',')).append(';')
                                .append(Math.round(o.optDouble("cost"))).append(';').append(Math.round(o.optDouble("price"))).append(';')
                                .append(Math.round(o.optDouble("marginPercent"))).append(';').append(Math.round(o.optDouble("suggestedPrice"))).append("\r\n");
                    }
                    h.shareGeneratedFile("meelano-pricing.csv", "text/csv", b.toString().getBytes("UTF-8"), "برنامهٔ قیمت‌گذاری");
                } catch (Exception e) {
                    h.notify("خطای خروجی: " + MeelanoWarehouse.shortMsg(e));
                }
            }
        }), MeelanoUiKit.lp(-1, h.dp(44)));
    }

    // ---------------- جریان نقدی
    private static void renderCash(final MeelanoUiHost h, LinearLayout page, JSONObject metrics, JSONObject receivables, JSONObject sales, JSONObject purchases) {
        MeelanoPalette p = h.colors();
        page.addView(MeelanoUiKit.sectionHeader(h, "پیش‌بینی جریان نقدی (۴ هفته)",
                "برآورد بر پایهٔ مطالبات وصول‌نشده و میانگین خرید هفتگی — برای دیدن هفته‌های پرخطر",
                "⇅", p.info), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);

        double receivable = receivables == null ? 0 : receivables.optDouble("total");
        double purchaseTotal = purchases == null ? 0 : purchases.optDouble("total");
        double weeklyIn = receivable / 4.0;
        double weeklyOut = purchaseTotal / 4.0;
        double opening = sales == null ? 0 : sales.optDouble("paid");
        JSONObject forecast = MeelanoFinance.cashFlowForecast(opening,
                new double[]{weeklyIn * 0.7, weeklyIn, weeklyIn * 1.1, weeklyIn * 0.9},
                new double[]{weeklyOut, weeklyOut, weeklyOut * 1.2, weeklyOut},
                weeklyOut * 1.5);
        JSONArray weeks = forecast.optJSONArray("weeks");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; weeks != null && i < weeks.length(); i++) {
            JSONObject w = weeks.optJSONObject(i);
            if (w == null) continue;
            rows.add(new String[]{"هفتهٔ " + h.fa(String.valueOf(w.optInt("week"))),
                    h.money(w.optDouble("in")), h.money(w.optDouble("out")),
                    (w.optDouble("net") >= 0 ? "+" : "−") + h.money(Math.abs(w.optDouble("net"))),
                    h.money(w.optDouble("balance")),
                    w.optBoolean("risk") ? "⚠ کمبود نقدینگی" : "سالم"});
        }
        page.addView(MeelanoUiKit.table(h, "⇅  جدول نقدینگی هفتگی",
                new String[]{"هفته", "وصولی", "پرداختی", "خالص", "مانده", "وضعیت"}, rows, p.info, null), MeelanoUiKit.lp(-1, -2));
        MeelanoUiKit.gap(h, page, 8);
        LinearLayout note = MeelanoUiKit.card(h, p.accent2);
        note.addView(MeelanoUiKit.kv(h, "موجودی نقد ابتدای دوره (وصولی ثبت‌شده)", h.money(opening), p.text), MeelanoUiKit.lp(-1, -2));
        note.addView(MeelanoUiKit.kv(h, "کم‌ترین مانده پیش‌بینی‌شده", h.money(forecast.optDouble("minBalance")), p.warning), MeelanoUiKit.lp(-1, -2));
        int shortWeek = forecast.optInt("firstShortfallWeek", -1);
        note.addView(MeelanoUiKit.kv(h, "اولین هفتهٔ پرخطر", shortWeek > 0 ? "هفتهٔ " + h.fa(String.valueOf(shortWeek)) : "هیچ", shortWeek > 0 ? p.danger : p.success), MeelanoUiKit.lp(-1, -2));
        note.addView(MeelanoUiKit.text(h, "ℹ  این پیش‌بینی «برآورد ساده» است: وصولی به‌طور مساوی روی ۴ هفته پخش شده و پرداختی از میانگین خرید دوره گرفته شده. با ثبت سررسید دقیق چک‌ها، دقت آن بالا می‌رود.",
                8.7f, p.muted, false), MeelanoUiKit.lp(-1, -2));
        page.addView(note, MeelanoUiKit.lp(-1, -2));
    }
}
