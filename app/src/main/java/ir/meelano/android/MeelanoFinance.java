package ir.meelano.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * موتور مالی و حسابداری «پخش درخشان» — محاسبات خالص، بدون وابستگی به اندروید.
 *
 * <p>تمرکز این موتور روی چیزهایی است که یک مدیر مالی ایرانی هر روز لازم دارد و در نسخهٔ
 * قبلی برنامه جایی نداشتند:</p>
 * <ul>
 *   <li>دسته‌بندی سنی مطالبات و <b>اولویت وصول</b> (کدام مشتری اول تماس بگیرد؟)</li>
 *   <li>چرخهٔ تبدیل نقد (DSO + DIO − DPO) و سرمایهٔ در گردش قفل‌شده</li>
 *   <li>پیش‌بینی جریان نقدی هفته‌های آینده بر پایهٔ سررسید چک‌ها و الگوی وصول</li>
 *   <li>قیمت‌گذاری هدف‌محور: «برای ۲۰٪ حاشیهٔ سود، این کالا باید چند فروخته شود؟»</li>
 *   <li>نقطهٔ سربه‌سر، حاشیهٔ سهم و مالیات بر ارزش افزودهٔ دوره</li>
 * </ul>
 */
final class MeelanoFinance {
    private MeelanoFinance() { }

    /** مرزهای دستهٔ سنی مطالبات (روز). */
    static final int[] AGING_EDGES = {30, 60, 90, 180};
    static final String[] AGING_LABELS = {"جاری (۰-۳۰ روز)", "۳۱ تا ۶۰ روز", "۶۱ تا ۹۰ روز", "۹۱ تا ۱۸۰ روز", "بیش از ۱۸۰ روز"};

    /** ایندکس دستهٔ سنی برای تعداد روز معوق. */
    static int agingIndex(int daysOverdue) {
        if (daysOverdue <= AGING_EDGES[0]) return 0;
        if (daysOverdue <= AGING_EDGES[1]) return 1;
        if (daysOverdue <= AGING_EDGES[2]) return 2;
        if (daysOverdue <= AGING_EDGES[3]) return 3;
        return 4;
    }

    /**
     * امتیاز اولویت وصول: مبلغ × وزن معوق‌بودن.
     * مشتری ۹۰ روز معوق با ۱۰ میلیون، مهم‌تر از مشتری جاری با ۵۰ میلیون است.
     */
    static double collectionScore(double amount, int daysOverdue) {
        double weight = 1.0;
        if (daysOverdue > 180) weight = 3.0;
        else if (daysOverdue > 90) weight = 2.2;
        else if (daysOverdue > 60) weight = 1.7;
        else if (daysOverdue > 30) weight = 1.3;
        else if (daysOverdue > 0) weight = 1.1;
        return Math.max(0, amount) * weight;
    }

    /** دستهٔ ریسک یک مانده بر اساس سن. */
    static String riskLevel(int daysOverdue) {
        if (daysOverdue > 180) return "danger";
        if (daysOverdue > 90) return "warn";
        if (daysOverdue > 30) return "info";
        return "success";
    }

    /** میانگین دورهٔ وصول مطالبات (روز). */
    static double dso(double receivables, double periodSales, int periodDays) {
        if (periodSales <= 0 || periodDays <= 0) return 0;
        return receivables / (periodSales / periodDays);
    }

    /** دورهٔ گردش موجودی (روز). */
    static double dio(double inventoryValue, double cogs, int periodDays) {
        if (cogs <= 0 || periodDays <= 0) return 0;
        return inventoryValue / (cogs / periodDays);
    }

    /** چرخهٔ تبدیل نقد = DSO + DIO − DPO. */
    static double cashConversionCycle(double dso, double dio, double dpo) {
        return dso + dio - dpo;
    }

    /**
     * قیمت پیشنهادی برای رسیدن به حاشیهٔ سود هدف.
     * حاشیه = (فروش − بهای خرید) ÷ فروش.
     */
    static double priceForMargin(double cost, double targetMarginPercent) {
        double m = Math.max(0, Math.min(95, targetMarginPercent)) / 100.0;
        if (cost <= 0) return 0;
        return cost / (1 - m);
    }

    /** حاشیهٔ سود فعلی یک کالا (٪). */
    static double marginPercent(double cost, double price) {
        if (price <= 0) return 0;
        return (price - cost) / price * 100.0;
    }

    /** گرد کردن مبلغ به مضرب مشخص (پیش‌فرض ۱۰۰۰ ریال) — قیمت‌های تمیز و قابل خواندن. */
    static double roundTo(double value, double step) {
        double s = step <= 0 ? 1000 : step;
        return Math.round(value / s) * s;
    }

    /** نقطهٔ سربه‌سر مقداری. */
    static double breakEvenUnits(double fixedCost, double price, double variableCost) {
        double cm = price - variableCost;
        return cm <= 0 ? Double.POSITIVE_INFINITY : fixedCost / cm;
    }

    /** نقطهٔ سربه‌سر ریالی. */
    static double breakEvenValue(double fixedCost, double price, double variableCost) {
        double u = breakEvenUnits(fixedCost, price, variableCost);
        return Double.isInfinite(u) ? 0 : u * price;
    }

    /** درجهٔ اهرم عملیاتی = حاشیهٔ سهم ÷ سود عملیاتی. */
    static double operatingLeverage(double contribution, double profit) {
        if (profit == 0) return 0;
        return contribution / profit;
    }

    /**
     * پیش‌بینی جریان نقدی هفته‌به‌هفته.
     *
     * @param opening       موجودی نقد ابتدای دوره
     * @param weeklyIn      وصولی مورد انتظار هر هفته
     * @param weeklyOut     پرداختی مورد انتظار هر هفته
     * @param safetyBuffer  حداقل نقدینگی مطمئن (هشدار زیر آن)
     */
    static JSONObject cashFlowForecast(double opening, double[] weeklyIn, double[] weeklyOut, double safetyBuffer) {
        JSONObject out = new JSONObject();
        JSONArray weeks = new JSONArray();
        int n = Math.max(weeklyIn == null ? 0 : weeklyIn.length, weeklyOut == null ? 0 : weeklyOut.length);
        double balance = opening, minBalance = opening;
        int firstShortfall = -1;
        for (int i = 0; i < n; i++) {
            double in = weeklyIn != null && i < weeklyIn.length ? weeklyIn[i] : 0;
            double o = weeklyOut != null && i < weeklyOut.length ? weeklyOut[i] : 0;
            balance += in - o;
            minBalance = Math.min(minBalance, balance);
            if (balance < safetyBuffer && firstShortfall < 0) firstShortfall = i + 1;
            JSONObject w = new JSONObject();
            try {
                w.put("week", i + 1);
                w.put("in", in);
                w.put("out", o);
                w.put("net", in - o);
                w.put("balance", balance);
                w.put("risk", balance < safetyBuffer);
            } catch (Exception ignored) { }
            weeks.put(w);
        }
        try {
            out.put("opening", opening);
            out.put("weeks", weeks);
            out.put("closing", balance);
            out.put("minBalance", minBalance);
            out.put("firstShortfallWeek", firstShortfall);
            out.put("safetyBuffer", safetyBuffer);
        } catch (Exception ignored) { }
        return out;
    }

    /** مالیات بر ارزش افزودهٔ یک مبلغ (نرخ پیش‌فرض ۱۰٪). */
    static double[] vat(double amount, double percent, boolean amountIncludesVat) {
        return MeelanoWarehouse.vatSplit(amount, percent, amountIncludesVat);
    }

    /**
     * تحلیل مطالبات: دسته‌بندی سنی + اولویت وصول.
     *
     * @param rows آرایه‌ای از مشتریان با کلیدهای {@code party}، {@code amount} و {@code days}
     */
    static JSONObject analyseReceivables(JSONArray rows) {
        JSONObject out = new JSONObject();
        double[] totals = new double[AGING_LABELS.length];
        int[] counts = new int[AGING_LABELS.length];
        List<JSONObject> priorities = new ArrayList<>();
        double total = 0, overdue = 0;
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            double amount = r.optDouble("amount", r.optDouble("total", 0));
            int days = r.optInt("days", r.optInt("age", 0));
            int idx = agingIndex(days);
            totals[idx] += amount;
            counts[idx]++;
            total += amount;
            if (days > AGING_EDGES[0]) overdue += amount;
            JSONObject p = new JSONObject();
            try {
                p.put("party", r.optString("party", r.optString("name", "—")));
                p.put("code", r.optString("code", r.optString("shmo", "")));
                p.put("amount", amount);
                p.put("days", days);
                p.put("score", collectionScore(amount, days));
                p.put("risk", riskLevel(days));
            } catch (Exception ignored) { }
            priorities.add(p);
        }
        Collections.sort(priorities, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return Double.compare(b.optDouble("score"), a.optDouble("score")); }
        });
        JSONArray buckets = new JSONArray();
        for (int i = 0; i < AGING_LABELS.length; i++) {
            JSONObject b = new JSONObject();
            try {
                b.put("label", AGING_LABELS[i]);
                b.put("count", counts[i]);
                b.put("total", totals[i]);
                b.put("share", total > 0 ? totals[i] / total * 100.0 : 0);
            } catch (Exception ignored) { }
            buckets.put(b);
        }
        JSONArray top = new JSONArray();
        for (int i = 0; i < priorities.size() && i < 40; i++) top.put(priorities.get(i));
        try {
            out.put("total", total);
            out.put("overdue", overdue);
            out.put("overdueShare", total > 0 ? overdue / total * 100.0 : 0);
            out.put("count", rows == null ? 0 : rows.length());
            out.put("buckets", buckets);
            out.put("priority", top);
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * پیشنهاد قیمت‌گذاری هدف‌محور برای فهرست کالاها.
     *
     * @param products      آرایهٔ کالاها (کلیدهای فارسی همان JSON برنامه)
     * @param targetMargin  حاشیهٔ سود هدف (٪)
     * @param roundStep     گرد کردن قیمت (ریال)
     */
    static JSONArray pricingPlan(JSONArray products, double targetMargin, double roundStep) {
        JSONArray out = new JSONArray();
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            double cost = p.optDouble("بهای_خرید", p.optDouble("buyPrice", 0));
            double price = p.optDouble("قیمت_فروش", p.optDouble("sellPrice", 0));
            double sold = p.optDouble("تعداد_فروش", p.optDouble("salesQty", 0));
            if (cost <= 0) continue;
            double current = marginPercent(cost, price);
            double suggested = roundTo(priceForMargin(cost, targetMargin), roundStep);
            if (suggested <= 0) continue;
            double gap = suggested - price;
            double impact = gap * sold;
            JSONObject o = new JSONObject();
            try {
                o.put("code", p.optString("کد", p.optString("code", "")));
                o.put("name", p.optString("نام", p.optString("name", "—")));
                o.put("cost", cost);
                o.put("price", price);
                o.put("marginPercent", current);
                o.put("suggestedPrice", suggested);
                o.put("suggestedMargin", marginPercent(cost, suggested));
                o.put("gap", gap);
                o.put("salesQty", sold);
                o.put("profitImpact", impact);
                o.put("needsChange", Math.abs(gap) >= Math.max(1000, price * 0.005));
                o.put("underpriced", current + 1.0 < targetMargin);
            } catch (Exception ignored) { }
            list.add(o);
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return Double.compare(Math.abs(b.optDouble("profitImpact")), Math.abs(a.optDouble("profitImpact"))); }
        });
        for (int i = 0; i < list.size() && i < 60; i++) out.put(list.get(i));
        return out;
    }

    /** جمع اثر مالی یک برنامهٔ قیمت‌گذاری. */
    static JSONObject pricingSummary(JSONArray plan) {
        JSONObject o = new JSONObject();
        double impact = 0, underpriced = 0, overpriced = 0;
        int changes = 0;
        for (int i = 0; plan != null && i < plan.length(); i++) {
            JSONObject p = plan.optJSONObject(i);
            if (p == null) continue;
            impact += p.optDouble("profitImpact");
            if (p.optBoolean("needsChange")) changes++;
            if (p.optBoolean("underpriced")) underpriced++;
            else if (p.optDouble("gap") > 0) overpriced++;
        }
        try {
            o.put("profitImpact", impact);
            o.put("changes", changes);
            o.put("underpriced", underpriced);
            o.put("items", plan == null ? 0 : plan.length());
        } catch (Exception ignored) { }
        return o;
    }

    /** CSV دسته‌بندی سنی و اولویت وصول. */
    static String receivablesCsv(JSONObject analysis) {
        StringBuilder b = new StringBuilder();
        b.append('\uFEFF');
        b.append("مشتری;مانده;روز معوق;امتیاز اولویت;دسته ریسک\r\n");
        JSONArray priority = analysis == null ? null : analysis.optJSONArray("priority");
        for (int i = 0; priority != null && i < priority.length(); i++) {
            JSONObject p = priority.optJSONObject(i);
            if (p == null) continue;
            b.append(p.optString("party", "")).append(';')
                    .append(Math.round(p.optDouble("amount"))).append(';')
                    .append(p.optInt("days")).append(';')
                    .append(Math.round(p.optDouble("score"))).append(';')
                    .append(p.optString("risk")).append("\r\n");
        }
        return b.toString();
    }
}
