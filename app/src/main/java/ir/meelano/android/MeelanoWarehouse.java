package ir.meelano.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * موتور هوش انبار «پخش درخشان» — لایهٔ محاسباتی خالص (بدون هیچ وابستگی به اندروید).
 *
 * <p>ورودی این کلاس، همان JSON کالاهای موجود در برنامه است ({@code queryProducts}) که با
 * کلیدهای فارسی می‌آید: کد، نام، بارکد، قیمت_فروش، قیمت_فروش۲، بهای_خرید، موجودی، واحد،
 * تعداد_در_بسته، گروه، تعداد_فروش، مبلغ_فروش، تعداد_خرید، مبلغ_خرید، گردش_خالص.
 * بنابراین هیچ کوئری جدیدی برای تحلیل لازم نیست و همهٔ اعداد از دادهٔ واقعی آتیران می‌آید.</p>
 *
 * <p>خروجی {@link #analyse} آمادهٔ نمایش است: KPIها، طبقه‌بندی ABC، ماتریس ABC×گردش،
 * نقطهٔ سفارش و مقدار سفارش پیشنهادی (EOQ)، کالاهای راکد، سود ناخالص، و هشدارهای فارسی.</p>
 *
 * <p>صداقت محاسباتی: هر فرمولی که به «بازهٔ زمانی» نیاز دارد (نقطهٔ سفارش، EOQ، روزهای پوشش)
 * فقط وقتی اجرا می‌شود که طول بازهٔ فروش واقعاً معلوم باشد ({@link Params#historyDays}); در غیر
 * این‌صورت به‌جای عدد ساختگی، کلاس گردش نسبت‌محور (فروش‌رفته نسبت به موجودی) گزارش می‌شود.</p>
 */
final class MeelanoWarehouse {
    private MeelanoWarehouse() { }

    // ================================================================== پارامترها
    /** پارامترهای قابل تنظیم انبار — همه از صفحهٔ تنظیمات انبار قابل تغییرند. */
    static final class Params {
        /** هزینهٔ نگهداری سالانه به درصد ارزش کالا (اجاره، سرمایهٔ خواب، بیمه، ضایعات). */
        double holdingCostPercent = 22.0;
        /** هزینهٔ هر بار سفارش‌گذاری (ریال) — برای EOQ. */
        double orderCost = 500_000;
        /** ضریب سطح سرویس (۹۵٪ ≈ ۱٫۶۵). */
        double serviceLevelZ = 1.65;
        /** مهلت تأمین (روز) از لحظهٔ سفارش تا رسیدن کالا. */
        double leadTimeDays = 14;
        /** انحراف معیار تقاضا در طول مهلت تأمین (روز) — برای ذخیرهٔ اطمینان. */
        double leadTimeStdevDays = 3.5;
        /** تعداد روزهای کاری سال (برای تبدیل فروش با زه به روز). */
        double workingDaysPerYear = 300;
        /** طول بازهٔ واقعی دادهٔ فروش (روز). صفر = نامعلوم ⇒ فرمول‌های زمان‌محور خاموش. */
        double historyDays = 0;
        /** مرز کلاس A در تحلیل پارتو (٪ ارزش). */
        double abcACut = 80.0;
        /** مرز کلاس B در تحلیل پارتو (٪ ارزش). */
        double abcBCut = 95.0;
        /** پوشش موجودی کمتر از این تعداد روز ⇒ کالای «کم». */
        double lowCoverDays = 10.0;
        /** اگر موجودی بیش از این تعداد روز فروش نرود ⇒ راکد. */
        double deadCoverDays = 120.0;
        /** سهم مفروض فروش از موجودی برای برآورد کالای کندگرد در حالت بازهٔ نامعلوم. */
        double slowRatio = 0.35;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("holdingCostPercent", holdingCostPercent);
                o.put("orderCost", orderCost);
                o.put("serviceLevelZ", serviceLevelZ);
                o.put("leadTimeDays", leadTimeDays);
                o.put("leadTimeStdevDays", leadTimeStdevDays);
                o.put("workingDaysPerYear", workingDaysPerYear);
                o.put("historyDays", historyDays);
                o.put("lowCoverDays", lowCoverDays);
                o.put("deadCoverDays", deadCoverDays);
            } catch (Exception ignored) { }
            return o;
        }

        static Params fromJson(JSONObject o) {
            Params p = new Params();
            if (o == null) return p;
            p.holdingCostPercent = o.optDouble("holdingCostPercent", p.holdingCostPercent);
            p.orderCost = o.optDouble("orderCost", p.orderCost);
            p.serviceLevelZ = o.optDouble("serviceLevelZ", p.serviceLevelZ);
            p.leadTimeDays = o.optDouble("leadTimeDays", p.leadTimeDays);
            p.leadTimeStdevDays = o.optDouble("leadTimeStdevDays", p.leadTimeStdevDays);
            p.workingDaysPerYear = o.optDouble("workingDaysPerYear", p.workingDaysPerYear);
            p.historyDays = o.optDouble("historyDays", p.historyDays);
            p.lowCoverDays = o.optDouble("lowCoverDays", p.lowCoverDays);
            p.deadCoverDays = o.optDouble("deadCoverDays", p.deadCoverDays);
            return p;
        }
    }

    // ================================================================== یک کالا
    /** یک ردیف انبار: کالا + محاسبات انبارداری آن. */
    static final class Item {
        String code = "", name = "", group = "", unit = "";
        double stock, buyPrice, sellPrice, sellPrice2, pack = 1;
        double salesQty, salesAmount, buyQty, buyAmount;
        /** کلاس ABC بر اساس ارزش مصرف/فروش. */
        String abc = "C";
        /** کلاس گردش: F (تندگرد) / M (متوسط) / S (کندگرد) / D (راکد). */
        String rot = "S";
        boolean hasHistory;
        double valueCost, valueSell, grossProfit, marginPercent, soldRatio, coverDays;
        double safetyStock, reorderPoint, eoq, suggestedOrder, annualDemand;
        double holdingCostYear, deadCapitalRisk;
        int rank;

        double stockValueCost() { return stock * buyPrice; }
        double stockValueSell() { return stock * sellPrice; }
        /** حاشیهٔ سود هر واحد. */
        double unitMargin() { return sellPrice - buyPrice; }
        /** درصد حاشیهٔ سود. */
        double priceMarginPercent() { return sellPrice > 0 ? (sellPrice - buyPrice) / sellPrice * 100.0 : 0; }
        /** بهرهٔ سرمایهٔ انبار: سود ناخالص تقسیم بر ارزش موجودی به بهای خرید. */
        double roiPercent() {
            double v = stockValueCost();
            return v > 0 ? grossProfit / v * 100.0 : 0;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("code", code);
                o.put("name", name);
                o.put("group", group);
                o.put("unit", unit);
                o.put("stock", stock);
                o.put("pack", pack);
                o.put("buyPrice", buyPrice);
                o.put("sellPrice", sellPrice);
                o.put("sellPrice2", sellPrice2);
                o.put("salesQty", salesQty);
                o.put("salesAmount", salesAmount);
                o.put("buyQty", buyQty);
                o.put("buyAmount", buyAmount);
                o.put("abc", abc);
                o.put("rot", rot);
                o.put("hasHistory", hasHistory);
                o.put("valueCost", valueCost);
                o.put("valueSell", valueSell);
                o.put("grossProfit", grossProfit);
                o.put("marginPercent", marginPercent);
                o.put("soldRatio", soldRatio);
                o.put("coverDays", coverDays);
                o.put("safetyStock", safetyStock);
                o.put("reorderPoint", reorderPoint);
                o.put("eoq", eoq);
                o.put("suggestedOrder", suggestedOrder);
                o.put("annualDemand", annualDemand);
                o.put("holdingCostYear", holdingCostYear);
                o.put("deadCapitalRisk", deadCapitalRisk);
                o.put("roiPercent", roiPercent());
                o.put("unitMargin", unitMargin());
                o.put("rank", rank);
            } catch (Exception ignored) { }
            return o;
        }

        /** ساخت از همان JSON کالاهای برنامه (کلیدهای فارسی + کلیدهای انگلیسی به‌عنوان پشتیبان). */
        static Item fromJson(JSONObject p) {
            Item it = new Item();
            if (p == null) return it;
            it.code = str(p, "کد", "code");
            it.name = str(p, "نام", "name");
            it.group = str(p, "گروه", "group");
            it.unit = str(p, "واحد", "unit");
            it.stock = num(p, "موجودی", "stock");
            it.buyPrice = num(p, "بهای_خرید", "buyPrice");
            it.sellPrice = num(p, "قیمت_فروش", "sellPrice");
            it.sellPrice2 = num(p, "قیمت_فروش۲", "sellPrice2");
            it.pack = num(p, "تعداد_در_بسته", "pack");
            if (it.pack <= 0) it.pack = 1;
            it.salesQty = num(p, "تعداد_فروش", "salesQty");
            it.salesAmount = num(p, "مبلغ_فروش", "salesAmount");
            it.buyQty = num(p, "تعداد_خرید", "buyQty");
            it.buyAmount = num(p, "مبلغ_خرید", "buyAmount");
            it.hasHistory = it.salesQty > 0 || it.buyQty > 0;
            // اگر مبلغ فروش نبود ولی تعداد داشت، با قیمت فروش تخمین می‌زنیم تا تحلیل خالی نماند.
            if (it.salesAmount <= 0 && it.salesQty > 0 && it.sellPrice > 0) it.salesAmount = it.salesQty * it.sellPrice;
            if (it.buyAmount <= 0 && it.buyQty > 0 && it.buyPrice > 0) it.buyAmount = it.buyQty * it.buyPrice;
            return it;
        }

        private static String str(JSONObject o, String a, String b) {
            String v = o.optString(a, "");
            if (v == null || v.trim().isEmpty() || "null".equalsIgnoreCase(v.trim())) v = o.optString(b, "");
            return v == null ? "" : v.trim();
        }

        private static double num(JSONObject o, String a, String b) {
            double v = o.optDouble(a, Double.NaN);
            if (Double.isNaN(v)) v = o.optDouble(b, 0);
            return Double.isNaN(v) ? 0 : v;
        }
    }

    // ================================================================== فرمول‌های پایه
    /** میانگین متحرک ساده روی سری تقاضا. */
    static double[] movingAverage(double[] series, int window) {
        if (series == null || series.length == 0) return new double[0];
        int w = Math.max(1, Math.min(window, series.length));
        double[] out = new double[series.length - w + 1];
        double sum = 0;
        for (int i = 0; i < series.length; i++) {
            sum += series[i];
            if (i >= w) sum -= series[i - w];
            if (i >= w - 1) out[i - w + 1] = sum / w;
        }
        return out;
    }

    /** هموارسازی نمایی (پیش‌بینی تقاضا) — آلفا بین ۰ و ۱. */
    static double exponentialSmoothing(double[] series, double alpha) {
        if (series == null || series.length == 0) return 0;
        double a = Math.max(0.05, Math.min(0.95, alpha));
        double s = series[0];
        for (int i = 1; i < series.length; i++) s = a * series[i] + (1 - a) * s;
        return s;
    }

    /** انحراف معیار نمونه (برای ذخیرهٔ اطمینان). */
    static double stdev(double[] series) {
        if (series == null || series.length < 2) return 0;
        double sum = 0;
        for (double v : series) sum += v;
        double mean = sum / series.length;
        double acc = 0;
        for (double v : series) acc += (v - mean) * (v - mean);
        return Math.sqrt(acc / (series.length - 1));
    }

    /** ذخیرهٔ اطمینان = z × σ تقاضا در طول مهلت تأمین. */
    static double safetyStock(double z, double sigmaDaily, double leadTimeDays) {
        double sigmaLt = Math.abs(sigmaDaily) * Math.sqrt(Math.max(0, leadTimeDays));
        return Math.max(0, z) * sigmaLt;
    }

    /** نقطهٔ سفارش = تقاضای میانگین در مهلت تأمین + ذخیرهٔ اطمینان. */
    static double reorderPoint(double dailyDemand, double leadTimeDays, double safety) {
        return Math.max(0, dailyDemand) * Math.max(0, leadTimeDays) + Math.max(0, safety);
    }

    /** مقدار سفارش اقتصادی (ویلسون) — بدون تخفیف پله‌ای. */
    static double eoq(double annualDemand, double orderCost, double unitCost, double holdingPercent) {
        double holding = Math.max(1e-6, unitCost * Math.max(0.1, holdingPercent) / 100.0);
        if (annualDemand <= 0 || orderCost <= 0) return 0;
        return Math.sqrt(2.0 * annualDemand * orderCost / holding);
    }

    /** ضریب گردش انبار = بهای تمام‌شدهٔ کالای فروش‌رفته ÷ میانگین ارزش موجودی. */
    static double turnoverRatio(double cogs, double averageInventoryValue) {
        return averageInventoryValue <= 0 ? 0 : cogs / averageInventoryValue;
    }

    /** تعداد روزی که موجودی فعلی کفایت می‌کند (۰ = نامعلوم). */
    static double daysOfCover(double stock, double salesQty, double historyDays) {
        if (historyDays <= 0 || salesQty <= 0) return 0;
        double daily = salesQty / historyDays;
        if (daily <= 0) return 0;
        return stock / daily;
    }

    /** نقطهٔ سربه‌سر (تعداد واحد) = هزینهٔ ثابت ÷ حاشیهٔ سهم هر واحد. */
    static double breakEvenUnits(double fixedCost, double price, double variableCost) {
        double cm = price - variableCost;
        return cm <= 0 ? Double.POSITIVE_INFINITY : fixedCost / cm;
    }

    /** تقسیم مبلغ به اصل و مالیات بر ارزش افزوده (نرخ پیش‌فرض ۱۰٪). */
    static double[] vatSplit(double gross, double vatPercent, boolean grossIncludesVat) {
        double rate = vatPercent / 100.0;
        if (grossIncludesVat) {
            double base = gross / (1 + rate);
            return new double[]{base, gross - base};
        }
        return new double[]{gross, gross * rate};
    }

    /** دسته‌بندی سنی مطالبات: ۰-۳۰، ۳۱-۶۰، ۶۱-۹۰، ۹۱-۱۸۰، +۱۸۰ */
    static JSONArray agingBuckets(double[] amounts, int[] ageDays) {
        String[] labels = {"جاری (۰-۳۰)", "۳۱ تا ۶۰", "۶۱ تا ۹۰", "۹۱ تا ۱۸۰", "بیش از ۱۸۰"};
        int[] counts = new int[labels.length];
        double[] totals = new double[labels.length];
        int n = Math.min(amounts == null ? 0 : amounts.length, ageDays == null ? 0 : ageDays.length);
        for (int i = 0; i < n; i++) {
            int d = ageDays[i];
            int idx = d <= 30 ? 0 : d <= 60 ? 1 : d <= 90 ? 2 : d <= 180 ? 3 : 4;
            counts[idx]++;
            totals[idx] += amounts[i];
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < labels.length; i++) {
            JSONObject o = new JSONObject();
            try { o.put("label", labels[i]); o.put("count", counts[i]); o.put("total", totals[i]); } catch (Exception ignored) { }
            out.put(o);
        }
        return out;
    }

    // ================================================================== تحلیل کامل
    /**
     * تحلیل کامل سبد کالای انبار از روی JSON کالاها.
     *
     * @param products همان آرایهٔ کالاهای برنامه (کلیدهای فارسی)
     * @param p        پارامترهای انبار (هزینهٔ نگهداری، مهلت تأمین، بازهٔ فروش …)
     */
    static JSONObject analyse(JSONArray products, Params p) {
        Params par = p == null ? new Params() : p;
        JSONObject out = new JSONObject();
        List<Item> items = new ArrayList<>();
        JSONArray errors = new JSONArray();
        try {
            for (int i = 0; products != null && i < products.length(); i++) {
                JSONObject raw = products.optJSONObject(i);
                if (raw == null) continue;
                Item it = Item.fromJson(raw);
                if (it.code.isEmpty() && it.name.isEmpty()) continue;
                items.add(it);
            }
        } catch (Exception e) {
            errors.put("خواندن کالاها: " + shortMsg(e));
        }

        // ---------- کلاس گردش (نسبت‌محور؛ زمان‌محور فقط وقتی بازه معلوم است) ----------
        for (Item it : items) {
            double soldRatio = (it.stock + it.salesQty) > 0 ? it.salesQty / (it.stock + it.salesQty) : 0;
            it.soldRatio = soldRatio;
            it.coverDays = daysOfCover(it.stock, it.salesQty, par.historyDays);
            if (!it.hasHistory && it.stock <= 0) it.rot = "D";
            else if (it.salesQty <= 0) it.rot = it.stock > 0 ? "D" : "S";
            else if (par.historyDays > 0) {
                double cover = it.coverDays;
                it.rot = cover <= par.lowCoverDays ? "F" : cover <= par.deadCoverDays ? "M" : "S";
            } else {
                if (it.stock <= 0 || it.salesQty >= it.stock) it.rot = "F";
                else if (it.salesQty >= it.stock * par.slowRatio) it.rot = "M";
                else it.rot = "S";
            }
        }

        // ---------- تحلیل پارتو / ABC روی «ارزش مصرف» (مبلغ فروش؛ در نبود آن ارزش موجودی) ----------
        double[] weights = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            double w = it.salesAmount > 0 ? it.salesAmount : it.stockValueCost();
            weights[i] = Math.max(0, w);
        }
        Integer[] order = new Integer[items.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final double[] wRef = weights;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return Double.compare(wRef[b], wRef[a]); }
        });
        double totalWeight = 0;
        for (double w : weights) totalWeight += w;
        double running = 0;
        int rank = 1;
        for (Integer idx : order) {
            Item it = items.get(idx);
            it.rank = rank++;
            double share = totalWeight > 0 ? weights[idx] / totalWeight * 100.0 : 0;
            running += share;
            it.abc = running <= par.abcACut || rank <= Math.max(1, items.size() / 20) ? "A"
                    : running <= par.abcBCut ? "B" : "C";
        }

        // ---------- شاخص‌های انبارداری هر کالا ----------
        double stockValueCost = 0, stockValueSell = 0, salesAmount = 0, buyAmount = 0, cogs = 0, grossProfit = 0, holding = 0;
        double stockUnits = 0;
        int lowCount = 0, deadCount = 0, zeroCount = 0, negativeCount = 0, noPriceCount = 0, noCostCount = 0;
        for (Item it : items) {
            it.valueCost = it.stockValueCost();
            it.valueSell = it.stockValueSell();
            it.grossProfit = it.salesAmount - it.salesQty * it.buyPrice;
            it.marginPercent = it.priceMarginPercent();
            it.holdingCostYear = it.valueCost * par.holdingCostPercent / 100.0;
            it.deadCapitalRisk = "D".equals(it.rot) ? it.valueCost : 0;

            stockValueCost += it.valueCost;
            stockValueSell += it.valueSell;
            stockUnits += it.stock;
            salesAmount += it.salesAmount;
            buyAmount += it.buyAmount;
            cogs += it.salesQty * it.buyPrice;
            grossProfit += it.grossProfit;
            holding += it.holdingCostYear;
            if (it.stock <= 0) zeroCount++;
            if (it.stock < 0) negativeCount++;
            if (it.buyPrice <= 0) noCostCount++;
            if (it.sellPrice <= 0) noPriceCount++;
            if ("D".equals(it.rot)) deadCount++;
            if (par.historyDays > 0 ? (it.coverDays > 0 && it.coverDays <= par.lowCoverDays)
                                    : ("F".equals(it.rot) && it.stock > 0)) lowCount++;
        }

        // ---------- برنامهٔ تأمین (فقط با بازهٔ معلوم؛ در غیر این‌صورت پوشش نسبی) ----------
        List<Item> reorder = new ArrayList<>();
        for (Item it : items) {
            if (par.historyDays > 0) {
                double daily = it.salesQty / par.historyDays;
                double sigmaDaily = daily * 0.45; // برآورد محافظه‌کارانهٔ نوسان تقاضا
                it.annualDemand = daily * par.workingDaysPerYear;
                it.safetyStock = safetyStock(par.serviceLevelZ, sigmaDaily, par.leadTimeDays);
                it.reorderPoint = reorderPoint(daily, par.leadTimeDays, it.safetyStock);
                it.eoq = eoq(it.annualDemand, par.orderCost, Math.max(it.buyPrice, 1), par.holdingCostPercent);
                double target = it.reorderPoint + it.eoq;
                it.suggestedOrder = Math.max(0, target - it.stock);
                if (it.stock <= it.reorderPoint && (it.salesQty > 0 || it.stock > 0)) reorder.add(it);
            } else if (it.salesQty > 0 && it.stock <= 0) {
                // حتی بدون بازهٔ زمانی، «کالای پرفروشِ تمام‌شده» قطعاً باید سفارش شود.
                it.reorderPoint = 0;
                it.suggestedOrder = Math.max(1, it.salesQty * 0.25);
                reorder.add(it);
            }
        }
        Collections.sort(reorder, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                double sa = a.stock <= 0 ? 0 : (a.salesQty > 0 ? a.stock / a.salesQty : 1e9);
                double sb = b.stock <= 0 ? 0 : (b.salesQty > 0 ? b.stock / b.salesQty : 1e9);
                return Double.compare(sa, sb);
            }
        });

        // ---------- گروه‌ها ----------
        Map<String, double[]> byGroup = new LinkedHashMap<>();
        for (Item it : items) {
            String g = it.group.isEmpty() ? "بدون گروه" : it.group;
            double[] accv = byGroup.get(g);
            if (accv == null) { accv = new double[]{0, 0, 0, 0}; byGroup.put(g, accv); }
            accv[0] += 1;                 // تعداد
            accv[1] += it.valueCost;      // ارزش موجودی
            accv[2] += it.salesAmount;    // فروش
            accv[3] += it.grossProfit;    // سود ناخالص
        }
        JSONArray groups = new JSONArray();
        List<Map.Entry<String, double[]>> groupList = new ArrayList<>(byGroup.entrySet());
        Collections.sort(groupList, new Comparator<Map.Entry<String, double[]>>() {
            @Override public int compare(Map.Entry<String, double[]> a, Map.Entry<String, double[]> b) {
                return Double.compare(b.getValue()[1], a.getValue()[1]);
            }
        });
        for (Map.Entry<String, double[]> e : groupList) {
            JSONObject o = new JSONObject();
            double[] v = e.getValue();
            try {
                o.put("name", e.getKey());
                o.put("items", (int) v[0]);
                o.put("stockValue", v[1]);
                o.put("salesValue", v[2]);
                o.put("grossProfit", v[3]);
                o.put("share", stockValueCost > 0 ? v[1] / stockValueCost * 100.0 : 0);
                o.put("turnover", turnoverRatio(v[2], v[1]));
            } catch (Exception ignored) { }
            groups.put(o);
        }

        // ---------- ABC / ماتریس ABC×گردش ----------
        JSONArray abc = new JSONArray();
        double[][] abcAgg = new double[3][3]; // [0]=تعداد، [1]=ارزش موجودی، [2]=فروش
        double[][] matrixAgg = new double[12][3]; // 3×4 خانه: [تعداد، ارزش، فروش]
        for (Item it : items) {
            int ai = "A".equals(it.abc) ? 0 : "B".equals(it.abc) ? 1 : 2;
            abcAgg[ai][0] += 1;
            abcAgg[ai][1] += it.valueCost;
            abcAgg[ai][2] += it.salesAmount;
            int ri = "F".equals(it.rot) ? 0 : "M".equals(it.rot) ? 1 : "S".equals(it.rot) ? 2 : 3;
            int cell = ai * 4 + ri;
            matrixAgg[cell][0] += 1;
            matrixAgg[cell][1] += it.valueCost;
            matrixAgg[cell][2] += it.salesAmount;
        }
        String[] abcKeys = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            JSONObject o = new JSONObject();
            try {
                o.put("key", abcKeys[i]);
                o.put("items", (int) abcAgg[i][0]);
                o.put("stockValue", abcAgg[i][1]);
                o.put("salesValue", abcAgg[i][2]);
                o.put("valueShare", stockValueCost > 0 ? abcAgg[i][1] / stockValueCost * 100.0 : 0);
                o.put("salesShare", salesAmount > 0 ? abcAgg[i][2] / salesAmount * 100.0 : 0);
            } catch (Exception ignored) { }
            abc.put(o);
        }
        JSONArray matrix = new JSONArray();
        String[] abcLabel = {"A", "B", "C"};
        String[] rotLabel = {"F", "M", "S", "D"};
        for (int a = 0; a < 3; a++) {
            for (int r = 0; r < 4; r++) {
                JSONObject o = new JSONObject();
                double[] v = matrixAgg[a * 4 + r];
                try {
                    o.put("abc", abcLabel[a]);
                    o.put("rot", rotLabel[r]);
                    o.put("items", (int) v[0]);
                    o.put("stockValue", v[1]);
                    o.put("salesValue", v[2]);
                } catch (Exception ignored) { }
                matrix.put(o);
            }
        }

        // ---------- فهرست‌های کاربردی ----------
        List<Item> dead = new ArrayList<>();
        List<Item> top = new ArrayList<>(items);
        List<Item> worst = new ArrayList<>(items);
        List<Item> valuable = new ArrayList<>(items);
        for (Item it : items) if ("D".equals(it.rot) && it.valueCost > 0) dead.add(it);
        Collections.sort(top, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Double.compare(b.salesAmount, a.salesAmount); }
        });
        Collections.sort(worst, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                int c = Double.compare(b.deadCapitalRisk, a.deadCapitalRisk);
                return c != 0 ? c : Double.compare(b.valueCost, a.valueCost);
            }
        });
        Collections.sort(valuable, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Double.compare(b.valueCost, a.valueCost); }
        });
        Collections.sort(dead, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Double.compare(b.valueCost, a.valueCost); }
        });

        // ---------- هشدارها ----------
        JSONArray alerts = new JSONArray();
        addAlert(alerts, "danger", "کالای راکد", formatFa(deadCount) + " قلم کالا در انبار مانده و هیچ فروشی ندارد؛ " +
                MeelanoCharts.rial(sumValue(dead, true)) + " سرمایهٔ خواب.");
        addAlert(alerts, "warn", "زیر نقطهٔ سفارش", formatFa(reorder.size()) + " قلم کالا به تأمین نیاز دارد" +
                (par.historyDays > 0 ? "" : " (بازهٔ فروش نامعلوم؛ پیشنهاد سفارش تقریبی است)") + ".");
        addAlert(alerts, "warn", "بدون بهای خرید", formatFa(noCostCount) + " قلم کالا بهای خرید ثبت‌شده ندارد؛ سود و ارزش‌گذاری آن‌ها قابل اتکا نیست.");
        addAlert(alerts, "info", "موجودی صفر یا منفی", formatFa(zeroCount) + " قلم موجودی صفر" + (negativeCount > 0 ? " و " + formatFa(negativeCount) + " قلم منفی" : "") + " دارد.");
        if (holding > 0) addAlert(alerts, "info", "هزینهٔ نگهداری سالانه",
                MeelanoCharts.rial(holding) + " (با فرض " + formatFa((int) Math.round(par.holdingCostPercent)) + "٪ هزینهٔ نگهداری) — کاهش موجودی راکد مستقیماً سود است.");

        // ---------- خروجی ----------
        JSONObject totals = new JSONObject();
        try {
            totals.put("items", items.size());
            totals.put("stockUnits", stockUnits);
            totals.put("stockValueCost", stockValueCost);
            totals.put("stockValueSell", stockValueSell);
            totals.put("potentialMargin", stockValueSell - stockValueCost);
            totals.put("salesAmount", salesAmount);
            totals.put("buyAmount", buyAmount);
            totals.put("cogs", cogs);
            totals.put("grossProfit", grossProfit);
            totals.put("grossMarginPercent", salesAmount > 0 ? grossProfit / salesAmount * 100.0 : 0);
            totals.put("turnover", turnoverRatio(cogs, Math.max(stockValueCost, 1)));
            totals.put("historyDays", par.historyDays);
            totals.put("dio", (par.historyDays > 0 && cogs > 0) ? stockValueCost / (cogs / par.historyDays) : 0);
            totals.put("holdingCostYear", holding);
            totals.put("deadCount", deadCount);
            totals.put("deadValue", sumValue(dead, true));
            totals.put("lowCount", lowCount);
            totals.put("zeroCount", zeroCount);
            totals.put("negativeCount", negativeCount);
            totals.put("noCostCount", noCostCount);
            totals.put("noPriceCount", noPriceCount);
            totals.put("reorderCount", reorder.size());
            totals.put("reorderValue", sumOrder(reorder));
            totals.put("abcAItems", (int) abcAgg[0][0]);
            totals.put("serviceLevel", serviceLevelLabel(par.serviceLevelZ));
        } catch (Exception e) {
            errors.put("جمع‌بندی: " + shortMsg(e));
        }
        try {
            out.put("ok", true);
            out.put("params", par.toJson());
            out.put("totals", totals);
            out.put("abc", abc);
            out.put("matrix", matrix);
            out.put("groups", groups);
            out.put("reorder", toArray(reorder, 60));
            out.put("dead", toArray(dead, 60));
            out.put("top", toArray(top, 25));
            out.put("worst", toArray(worst, 25));
            out.put("valuable", toArray(valuable, 25));
            out.put("alerts", alerts);
            out.put("errors", errors);
            out.put("generatedAt", System.currentTimeMillis());
        } catch (Exception e) {
            try { out.put("ok", false); out.put("errors", errors.put("خروجی: " + shortMsg(e))); } catch (Exception ignored) { }
        }
        return out;
    }

    private static JSONArray toArray(List<Item> list, int cap) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < list.size() && i < cap; i++) arr.put(list.get(i).toJson());
        return arr;
    }

    private static double sumValue(List<Item> list, boolean cost) {
        double s = 0;
        for (Item it : list) s += cost ? it.valueCost : it.valueSell;
        return s;
    }

    private static double sumOrder(List<Item> list) {
        double s = 0;
        for (Item it : list) s += it.suggestedOrder * Math.max(it.buyPrice, 0);
        return s;
    }

    private static void addAlert(JSONArray arr, String level, String title, String text) {
        JSONObject o = new JSONObject();
        try { o.put("level", level); o.put("title", title); o.put("text", text); } catch (Exception ignored) { }
        arr.put(o);
    }

    private static String serviceLevelLabel(double z) {
        if (z >= 2.33) return "۹۹٪";
        if (z >= 2.05) return "۹۸٪";
        if (z >= 1.88) return "۹۷٪";
        if (z >= 1.65) return "۹۵٪";
        if (z >= 1.28) return "۹۰٪";
        if (z >= 1.04) return "۸۵٪";
        return "۸۰؜٪";
    }

    /** CSV قابل بازکردن در اکسل با BOM (فارسی سالم). */
    static String toCsv(JSONObject analysis) {
        StringBuilder b = new StringBuilder();
        b.append('\uFEFF');
        b.append("کد;نام;گروه;واحد;موجودی;بهای خرید;قیمت فروش;ارزش موجودی (بهای خرید);فروش (مبلغ);سود ناخالص;کلاس ABC;کلاس گردش;نقطه سفارش;پیشنهاد سفارش;هزینه نگهداری سالانه\r\n");
        JSONArray all = new JSONArray();
        if (analysis != null) {
            JSONArray a = analysis.optJSONArray("valuable");
            JSONArray b2 = analysis.optJSONArray("reorder");
            JSONArray c = analysis.optJSONArray("dead");
            if (a != null) for (int i = 0; i < a.length(); i++) all.put(a.opt(i));
            if (b2 != null) for (int i = 0; i < b2.length(); i++) all.put(b2.opt(i));
            if (c != null) for (int i = 0; i < c.length(); i++) all.put(c.opt(i));
        }
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            b.append(csv(o.optString("code"))).append(';')
                    .append(csv(o.optString("name"))).append(';')
                    .append(csv(o.optString("group"))).append(';')
                    .append(csv(o.optString("unit"))).append(';')
                    .append(plain(o.optDouble("stock", 0))).append(';')
                    .append(plain(o.optDouble("buyPrice", 0))).append(';')
                    .append(plain(o.optDouble("sellPrice", 0))).append(';')
                    .append(plain(o.optDouble("valueCost", 0))).append(';')
                    .append(plain(o.optDouble("salesAmount", 0))).append(';')
                    .append(plain(o.optDouble("grossProfit", 0))).append(';')
                    .append(csv(o.optString("abc"))).append(';')
                    .append(csv(o.optString("rot"))).append(';')
                    .append(plain(o.optDouble("reorderPoint", 0))).append(';')
                    .append(plain(o.optDouble("suggestedOrder", 0))).append(';')
                    .append(plain(o.optDouble("holdingCostYear", 0))).append("\r\n");
        }
        return b.toString();
    }

    private static String csv(String v) {
        String s = v == null ? "" : v.replace(';', ',');
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    private static String plain(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.3f", v);
    }

    static String formatFa(int v) { return MeelanoCharts.fa(String.valueOf(v)); }

    static String shortMsg(Throwable e) {
        if (e == null) return "خطای نامشخص";
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }
}
