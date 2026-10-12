package ir.meelano.android;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * لایهٔ دادهٔ انبار — فقط خواندنی (READ-ONLY) روی پایگاه آتیران.
 *
 * <p>هر بلوک به‌صورت مستقل و در try/catch اجرا می‌شود و در صورت نبود جدول/ستون، فقط همان بلوک
 * حذف می‌شود؛ نه اتصال و نه صفحهٔ انبار. نام ستون‌ها در زمان اجرا از متادیتای واقعی سرور
 * استخراج می‌شود (همان سیاست ManagerAnalytics)، پس روی نسخه‌های مختلف آتیران هم کار می‌کند.</p>
 *
 * <p>جدول‌های واقعی که در پشتیبان‌های پروژه شناسایی شدند: {@code anbars} (انبارها + انباردار)،
 * {@code inventory_anbars}، {@code Inventory_Anbars_Variety} (موجودی کالا در هر انبار)،
 * {@code ka_act} (کارت گردش کالا: act_id، done_date، tedvah، price، RdfAnbar)،
 * {@code Batch} (سری‌ها و تاریخ انقضا: ExpDate، BatchLocation) و {@code sailfact}.</p>
 */
final class MeelanoWarehouseRepo {
    private MeelanoWarehouseRepo() { }

    // ---------------------------------------------------------------- ستون‌ها
    private static String productKey(Set<String> cols) {
        return ManagerAnalytics.resolveFlexible(cols, "SHKA", "shka", "RdfKala", "kala_rdf", "shkala", "StuffCode",
                "KalaID", "ProductID", "product_code", "کد_کالا", "کالا");
    }

    private static String warehouseKey(Set<String> cols) {
        return ManagerAnalytics.resolveFlexible(cols, "rdf_anbars", "RdfAnbar", "rdf_anbar", "AnbarID", "anbar_id",
                "sh_anbar", "shAnbar", "anbar", "Anbar", "CodAnb", "codanb", "store_id", "warehouse_id");
    }

    private static String quantityCol(Set<String> cols) {
        return ManagerAnalytics.resolveFlexible(cols, "mojoodi", "Mojoodi", "tedad_bal", "tedad", "Tedad", "qty",
                "quantity", "mande", "Mandeh", "man", "balance", "onhand", "remain", "موجودی", "مانده", "تعداد");
    }

    private static String dateCol(Set<String> cols) {
        return ManagerAnalytics.resolveFlexible(cols, "done_date", "Done_Date", "date", "Date", "t_date", "tarikh",
                "sanad_date", "created_at", "تاریخ");
    }

    // ---------------------------------------------------------------- انبارها
    /**
     * آیا می‌توان موجودی را به تفکیک انبار از دفتر انبار آتیران خواند؟
     *
     * <p>در آتیران، جدول {@code ka_act} هر گردش کالا را با ستون انبار ({@code RdfAnbar}) نگه می‌دارد؛
     * کسری/مازاد انبار هم با همان علامت رویهٔ خودِ آتیران ({@code UpdateMojodiInventory}) حساب می‌شود.
     * این دقیق‌ترین منبع موجودی «هر کالا در هر انبار» است و به جدول خلاصهٔ
     * {@code Inventory_Anbars_Variety} (که ممکن است به‌روز نباشد) ترجیح داده می‌شود.</p>
     */
    private static boolean atiranLedger(Connection c, Set<String> ka, Set<String> inv) {
        return ManagerAnalytics.resolve(ka, "shka") != null && ManagerAnalytics.resolve(ka, "act_id") != null
                && ManagerAnalytics.resolve(ka, "tedvah") != null && ManagerAnalytics.resolve(ka, "tedjoz") != null
                && ManagerAnalytics.resolve(ka, "active") != null
                && ManagerAnalytics.resolveFlexible(ka, "RdfAnbar", "rdf_anbar", "rdf_anbars", "AnbarID", "anbar", "sh_anbar") != null
                && ManagerAnalytics.resolve(inv, "shka") != null && ManagerAnalytics.resolve(inv, "mohvah") != null;
    }

    /** تعداد کالا، جمع تعداد و ارزش موجودی هر انبار — مستقیماً از دفتر انبار (ka_act × anbars). */
    private static String atiranWarehouseSql(String aName, String aKey, String aKeeper, String kWh, String invKey,
                                             String invQty, Set<String> invCols) {
        String sign = "CASE WHEN k.act_id IN (" + MainActivity.ATIRAN_STOCK_OUT_ACTS + ") THEN -1 ELSE 1 END";
        String qty = "(CAST(ISNULL(k.tedvah,0) AS decimal(19,3))*ISNULL(NULLIF(TRY_CONVERT(decimal(19,3),i.[" + invQty + "]),0),1)"
                + "+CAST(ISNULL(k.tedjoz,0) AS decimal(19,3)))*" + sign;
        String cost = ManagerAnalytics.resolveFlexible(invCols, "pure_buy_price", "UnPureBuyPrice", "BuyPrice", "buy_price",
                "LastBuyPrice", "PurchasePrice", "cost", "cost_price", "بهای_خرید", "قیمت_خرید");
        String price = cost == null ? "CAST(0 AS decimal(19,2))" : "ISNULL(TRY_CONVERT(decimal(19,2),i.[" + cost + "]),0)";
        String keeper = aKeeper == null ? "CAST(NULL AS nvarchar(120))" : "TRY_CONVERT(nvarchar(120),a.[" + aKeeper + "])";
        return "SELECT TRY_CONVERT(nvarchar(150),a.[" + aName + "]), TRY_CONVERT(nvarchar(150),a.[" + aKey + "]), " + keeper
                + ", COUNT_BIG(DISTINCT g.shka), ISNULL(SUM(g.qty),0), ISNULL(SUM(g.value),0)"
                + " FROM (SELECT m.wh wh, m.shka shka, SUM(m.qty) qty, SUM(m.qty * m.price) value FROM (SELECT k.shka shka,"
                + " k.[" + kWh + "] wh, " + qty + " qty, " + price + " price FROM dbo.ka_act k"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invKey + "])=TRY_CONVERT(nvarchar(100),k.shka)"
                + " WHERE k.active='t') m GROUP BY m.wh, m.shka) g"
                + " JOIN dbo.anbars a ON TRY_CONVERT(nvarchar(100),a.[" + aKey + "])=TRY_CONVERT(nvarchar(100),g.wh)"
                + " GROUP BY TRY_CONVERT(nvarchar(150),a.[" + aName + "]), TRY_CONVERT(nvarchar(150),a.[" + aKey + "]), " + keeper
                + " ORDER BY 6 DESC, 1";
    }

    /** فهرست انبارها با تعداد قلم، جمع تعداد و ارزش ریالی موجودی (در صورت وجود بهای خرید). */
    static JSONArray warehouses(Connection c) throws Exception {
        JSONArray arr = new JSONArray();
        Set<String> an = ManagerAnalytics.columns(c, "anbars");
        String aKey = ManagerAnalytics.resolveFlexible(an, "rdf_anbar", "RDF", "rdf", "ID", "anbar_id", "cod");
        String aName = ManagerAnalytics.resolveFlexible(an, "name", "Name", "onvan", "title", "نام", "نام_انبار");
        String aKeeper = ManagerAnalytics.resolveFlexible(an, "anbardar", "Anbardar", "keeper", "masool", "user_rdf",
                "anbardar_rdf", "sys_user", "انباردار");
        if (aKey == null || aName == null) return arr;

        Set<String> invCols = ManagerAnalytics.columns(c, "inventory");
        String invKey = productKey(invCols);
        Set<String> ka = ManagerAnalytics.columns(c, "ka_act");
        String sql;

        if (invKey != null && atiranLedger(c, ka, invCols)) {
            // مسیر دقیق: دفتر انبار آتیران (ka_act) به تفکیک انبار
            sql = atiranWarehouseSql(aName, aKey, aKeeper,
                    ManagerAnalytics.resolveFlexible(ka, "RdfAnbar", "rdf_anbar", "rdf_anbars", "AnbarID", "anbar", "sh_anbar"),
                    invKey, ManagerAnalytics.resolve(invCols, "mohvah"), invCols);
        } else {
            // مسیر عمومی: جدول خلاصهٔ «کالا × انبار» در صورت وجود
            String stockTable = null, sProduct = null, sWarehouse = null, sQty = null;
            for (String t : new String[]{"Inventory_Anbars_Variety", "Inventory_Anbars", "inventory_anbars", "InvantoryAnbar", "Inventory_Anbar", "InvAnbar", "AnbarStock"}) {
                if (!ManagerAnalytics.tableExists(c, t)) continue;
                Set<String> cols = ManagerAnalytics.columns(c, t);
                String p = productKey(cols), w = warehouseKey(cols), q = quantityCol(cols);
                if (p != null && w != null && q != null) { stockTable = t; sProduct = p; sWarehouse = w; sQty = q; break; }
            }
            String priceCost = ManagerAnalytics.resolveFlexible(invCols, "pure_buy_price", "UnPureBuyPrice", "BuyPrice", "buy_price",
                    "LastBuyPrice", "PurchasePrice", "cost", "cost_price", "بهای_خرید", "قیمت_خرید");
            String costJoin = (stockTable != null && priceCost != null && invKey != null)
                    ? " LEFT JOIN dbo.inventory k ON TRY_CONVERT(nvarchar(100),k.[" + invKey + "])=TRY_CONVERT(nvarchar(100),s.[" + sProduct + "])" : "";
            String qtyExpr = stockTable == null ? null : "ISNULL(SUM(TRY_CONVERT(decimal(19,3),s.[" + sQty + "])),0)";
            String valueExpr = (stockTable != null && priceCost != null && invKey != null)
                    ? ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),s.[" + sQty + "]) * ISNULL(TRY_CONVERT(decimal(19,2),k.[" + priceCost + "]),0)),0)"
                    : ", CAST(0 AS decimal(19,2))";
            String keepExpr = aKeeper == null ? "CAST(NULL AS nvarchar(120))" : "TRY_CONVERT(nvarchar(120),a.[" + aKeeper + "])";
            if (stockTable != null) {
                sql = "SELECT TRY_CONVERT(nvarchar(150),a.[" + aName + "]), TRY_CONVERT(nvarchar(150),a.[" + aKey + "]), " + keepExpr
                        + ", COUNT_BIG(DISTINCT TRY_CONVERT(nvarchar(100),s.[" + sProduct + "]))"
                        + ", " + qtyExpr + valueExpr
                        + " FROM dbo.anbars a LEFT JOIN dbo.[" + stockTable + "] s"
                        + " ON TRY_CONVERT(nvarchar(100),s.[" + sWarehouse + "])=TRY_CONVERT(nvarchar(100),a.[" + aKey + "])"
                        + costJoin
                        + " GROUP BY TRY_CONVERT(nvarchar(150),a.[" + aName + "]), TRY_CONVERT(nvarchar(150),a.[" + aKey + "]), " + keepExpr
                        + " ORDER BY 5 DESC, 1";
            } else {
                sql = "SELECT TRY_CONVERT(nvarchar(150),a.[" + aName + "]), TRY_CONVERT(nvarchar(150),a.[" + aKey + "]), " + keepExpr
                        + ", CAST(0 AS bigint), CAST(0 AS decimal(19,3)), CAST(0 AS decimal(19,2))"
                        + " FROM dbo.anbars a ORDER BY 1";
            }
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    JSONObject o = new JSONObject();
                    o.put("name", safe(r.getString(1), "انبار بدون نام"));
                    o.put("key", safe(r.getString(2), ""));
                    o.put("keeper", safe(r.getString(3), ""));
                    o.put("items", r.getLong(4));
                    o.put("qty", r.getDouble(5));
                    o.put("value", r.getDouble(6));
                    arr.put(o);
                }
            }
        }
        return arr;
    }

    // ---------------------------------------------------------------- موجودی به تفکیک انبار
    /** پرمعامله‌ترین ردیف‌های «کالا × انبار» (برای نمایش جدول و انتقال بین انبارها). */
    static JSONArray stockRows(Connection c, int cap) throws Exception {
        JSONArray arr = new JSONArray();
        String stockTable = null, sProduct = null, sWarehouse = null, sQty = null;
        for (String t : new String[]{"Inventory_Anbars_Variety", "Inventory_Anbars", "inventory_anbars", "InvantoryAnbar", "Inventory_Anbar"}) {
            if (!ManagerAnalytics.tableExists(c, t)) continue;
            Set<String> cols = ManagerAnalytics.columns(c, t);
            String p = productKey(cols), w = warehouseKey(cols), q = quantityCol(cols);
            if (p != null && w != null && q != null) { stockTable = t; sProduct = p; sWarehouse = w; sQty = q; break; }
        }
        if (stockTable == null) return arr;
        Set<String> an = ManagerAnalytics.columns(c, "anbars");
        String aKey = ManagerAnalytics.resolveFlexible(an, "rdf_anbar", "RDF", "rdf", "ID", "cod");
        String aName = ManagerAnalytics.resolveFlexible(an, "name", "Name", "onvan", "title", "نام");
        Set<String> inv = ManagerAnalytics.columns(c, "inventory");
        String iKey = productKey(inv);
        String iName = ManagerAnalytics.resolveFlexible(inv, "naka", "Name", "KalaName", "ProductName", "نام", "نام_کالا");
        String iCode = ManagerAnalytics.resolveFlexible(inv, "StuffCode", "Code", "Barcode", "بارکد", "کد_کالا");
        String joinInv = iKey == null ? "" : " LEFT JOIN dbo.inventory k ON TRY_CONVERT(nvarchar(100),k.[" + iKey + "])=TRY_CONVERT(nvarchar(100),s.[" + sProduct + "])";
        String joinAn = (aKey == null || aName == null) ? "" : " LEFT JOIN dbo.anbars a ON TRY_CONVERT(nvarchar(100),a.[" + aKey + "])=TRY_CONVERT(nvarchar(100),s.[" + sWarehouse + "])";
        String sql = "SELECT TOP (" + Math.max(1, cap) + ") TRY_CONVERT(nvarchar(120),s.[" + sProduct + "])"
                + (iName == null ? ", CAST(NULL AS nvarchar(250))" : ", TRY_CONVERT(nvarchar(250),k.[" + iName + "])")
                + (iCode == null ? ", CAST(NULL AS nvarchar(80))" : ", TRY_CONVERT(nvarchar(80),k.[" + iCode + "])")
                + (aName == null ? ", CAST(NULL AS nvarchar(150))" : ", TRY_CONVERT(nvarchar(150),a.[" + aName + "])")
                + ", TRY_CONVERT(decimal(19,3),s.[" + sQty + "])"
                + " FROM dbo.[" + stockTable + "] s" + joinInv + joinAn
                + " WHERE TRY_CONVERT(decimal(19,3),s.[" + sQty + "]) IS NOT NULL"
                + " ORDER BY ABS(TRY_CONVERT(decimal(19,3),s.[" + sQty + "])) DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    JSONObject o = new JSONObject();
                    o.put("code", safe(r.getString(1), ""));
                    o.put("name", safe(r.getString(2), ""));
                    o.put("barcode", safe(r.getString(3), ""));
                    o.put("warehouse", safe(r.getString(4), "—"));
                    o.put("qty", r.getDouble(5));
                    arr.put(o);
                }
            }
        }
        return arr;
    }

    // ---------------------------------------------------------------- بازهٔ واقعی فروش
    /** اولین/آخرین تاریخ فروش و تعداد روز داده، تا فرمول‌های زمان‌محور روی داده واقعی بنشینند. */
    static JSONObject salesWindow(Connection c) throws Exception {
        JSONObject o = new JSONObject();
        Set<String> sail = ManagerAnalytics.columns(c, "sailfact");
        String date = ManagerAnalytics.resolveFlexible(sail, "date", "Date", "t_date", "tarikh", "fact_date", "تاریخ");
        if (date == null) return o;
        String and = ManagerAnalytics.activeAnd(sail, "s");
        String sql = "SELECT MIN(TRY_CONVERT(date,s.[" + date + "])), MAX(TRY_CONVERT(date,s.[" + date + "])), COUNT_BIG(1)"
                + " FROM dbo.sailfact s WHERE TRY_CONVERT(date,s.[" + date + "]) IS NOT NULL" + and;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            try (ResultSet r = ps.executeQuery()) {
                if (r.next()) {
                    String first = r.getString(1) == null ? "" : r.getString(1);
                    String last = r.getString(2) == null ? "" : r.getString(2);
                    o.put("first", first);
                    o.put("last", last);
                    o.put("documents", r.getLong(3));
                    if (!first.isEmpty() && !last.isEmpty()) {
                        try (PreparedStatement ps2 = c.prepareStatement("SELECT DATEDIFF(day, TRY_CONVERT(date,'" + first + "'), TRY_CONVERT(date,'" + last + "')) + 1")) {
                            ps2.setQueryTimeout(10);
                            try (ResultSet r2 = ps2.executeQuery()) { if (r2.next()) o.put("days", r2.getInt(1)); }
                        }
                    }
                }
            }
        }
        return o;
    }

    // ---------------------------------------------------------------- کارت گردش کالا (kardex)
    /** گردش یک کالا از {@code ka_act}: تاریخ، نوع سند، تعداد، فی، انبار و شماره سند. */
    static JSONArray kardex(Connection c, String code, int cap) throws Exception {
        JSONArray arr = new JSONArray();
        Set<String> cols = ManagerAnalytics.columns(c, "ka_act");
        String key = productKey(cols);
        if (key == null) return arr;
        String d = dateCol(cols);
        String qty = ManagerAnalytics.resolveFlexible(cols, "tedvah", "TEDVAH", "tedad", "Tedad", "qty", "quantity");
        String price = ManagerAnalytics.resolveFlexible(cols, "price", "Price", "fee", "nerkh", "fi", "mablagh", "فی", "قیمت");
        String act = ManagerAnalytics.resolveFlexible(cols, "act_id", "ActID", "noe", "type", "kind");
        String actDis = ManagerAnalytics.resolveFlexible(cols, "act_dis", "ActDis", "sharh", "desc", "title", "شرح");
        String anbar = ManagerAnalytics.resolveFlexible(cols, "RdfAnbar", "rdf_anbar", "anbar", "sh_anbar");
        String doc = ManagerAnalytics.resolveFlexible(cols, "shfac", "ShFac", "sh_sanad", "sanad", "ghno", "sh_back_sanad");
        // تعداد به «واحد شمارش» تبدیل می‌شود (tedvah×mohvah + tedjoz) و روی آن علامت رویداد انبار
        // (همان فهرست act_idـی که رویهٔ UpdateMojodiInventory استفاده می‌کند) اعمال می‌گردد.
        Set<String> invCols = ManagerAnalytics.columns(c, "inventory");
        String invKey = productKey(invCols);
        String mohvah = ManagerAnalytics.resolveFlexible(invCols, "mohvah", "MOJVAH", "moh_vah", "zarib");
        boolean tedPieces = act != null && qty != null && mohvah != null && invKey != null
                && ManagerAnalytics.resolve(cols, "tedvah") != null && ManagerAnalytics.resolve(cols, "tedjoz") != null;
        String sign = act == null ? "1" : "CASE WHEN k.[" + act + "] IN (" + MainActivity.ATIRAN_STOCK_OUT_ACTS + ") THEN -1 ELSE 1 END";
        StringBuilder sel = new StringBuilder();
        sel.append("TOP (").append(Math.max(1, cap)).append(") ");
        sel.append(d == null ? "CAST(NULL AS date)" : "TRY_CONVERT(date,k.[" + d + "])");
        String qtyExpr;
        if (tedPieces) {
            qtyExpr = "(CAST(ISNULL(k.tedvah,0) AS decimal(19,3))*ISNULL(NULLIF(TRY_CONVERT(decimal(19,3),i.[" + mohvah + "]),0),1)+CAST(ISNULL(k.tedjoz,0) AS decimal(19,3)))*" + sign;
        } else if (qty == null) {
            qtyExpr = "CAST(NULL AS decimal(19,3))";
        } else if (act != null) {
            qtyExpr = "TRY_CONVERT(decimal(19,3),k.[" + qty + "])*" + sign;
        } else {
            qtyExpr = "TRY_CONVERT(decimal(19,3),k.[" + qty + "])";
        }
        sel.append(", ").append(qtyExpr);
        sel.append(price == null ? ", CAST(NULL AS decimal(19,2))" : ", TRY_CONVERT(decimal(19,2),k.[" + price + "])");
        sel.append(act == null ? ", CAST(NULL AS int)" : ", TRY_CONVERT(int,k.[" + act + "])");
        sel.append(actDis == null ? ", CAST(NULL AS nvarchar(200))" : ", TRY_CONVERT(nvarchar(200),k.[" + actDis + "])");
        sel.append(anbar == null ? ", CAST(NULL AS nvarchar(60))" : ", TRY_CONVERT(nvarchar(60),k.[" + anbar + "])");
        sel.append(doc == null ? ", CAST(NULL AS nvarchar(80))" : ", TRY_CONVERT(nvarchar(80),k.[" + doc + "])");
        String where = "";
        boolean filtered = code != null && !code.trim().isEmpty();
        if (filtered) where = " WHERE TRY_CONVERT(nvarchar(100),k.[" + key + "])=?";
        String order = d == null ? "" : " ORDER BY 1 DESC";
        String invJoin = tedPieces
                ? " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invKey + "])=TRY_CONVERT(nvarchar(100),k.[" + key + "])"
                : "";
        String sql = "SELECT " + sel + " FROM dbo.ka_act k" + invJoin + where + order;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            if (filtered) ps.setString(1, code.trim());
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    JSONObject o = new JSONObject();
                    o.put("date", r.getString(1) == null ? "" : r.getString(1));
                    o.put("qty", r.getDouble(2));
                    o.put("price", r.getDouble(3));
                    o.put("act", r.getObject(4) == null ? 0 : r.getInt(4));
                    int act = r.getObject(4) == null ? 0 : r.getInt(4);
                    boolean out = ("," + MainActivity.ATIRAN_STOCK_OUT_ACTS + ",").contains("," + act + ",");
                    o.put("act", act);
                    o.put("out", out);
                    o.put("direction", out ? -1 : 1);
                    String kind = safe(r.getString(5), "");
                    o.put("kind", kind);
                    o.put("reason", actLabel(act, out, kind));
                    o.put("warehouse", safe(r.getString(6), ""));
                    o.put("document", safe(r.getString(7), ""));
                    arr.put(o);
                }
            }
        }
        return arr;
    }

    /** جمع ورود/خروج هر کالا در کل تاریخچه (از ka_act) — برای ستون «گردش واقعی» در جدول انبار. */
    static JSONArray movementTotals(Connection c, int cap) throws Exception {
        JSONArray arr = new JSONArray();
        Set<String> cols = ManagerAnalytics.columns(c, "ka_act");
        String key = productKey(cols);
        String qty = ManagerAnalytics.resolveFlexible(cols, "tedvah", "TEDVAH", "tedad", "Tedad", "qty");
        if (key == null || qty == null) return arr;
        String q = "TRY_CONVERT(decimal(19,3),k.[" + qty + "])";
        String sql = "SELECT TOP (" + Math.max(1, cap) + ") TRY_CONVERT(nvarchar(100),k.[" + key + "])"
                + ", ISNULL(SUM(CASE WHEN " + q + ">0 THEN " + q + " ELSE 0 END),0)"
                + ", ISNULL(SUM(CASE WHEN " + q + "<0 THEN -" + q + " ELSE 0 END),0)"
                + ", COUNT_BIG(1) FROM dbo.ka_act k GROUP BY TRY_CONVERT(nvarchar(100),k.[" + key + "])"
                + " ORDER BY 2 DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    JSONObject o = new JSONObject();
                    o.put("code", safe(r.getString(1), ""));
                    o.put("inQty", r.getDouble(2));
                    o.put("outQty", r.getDouble(3));
                    o.put("documents", r.getLong(4));
                    arr.put(o);
                }
            }
        }
        return arr;
    }

    // ---------------------------------------------------------------- سری‌ها و تاریخ انقضا
    /** سری‌های نزدیک به انقضا (Batch.ExpDate) — در صورت وجود جدول و ستون‌ها. */
    static JSONArray expiringBatches(Connection c, int cap) throws Exception {
        JSONArray arr = new JSONArray();
        String table = ManagerAnalytics.tableExists(c, "Batch") ? "Batch"
                : ManagerAnalytics.tableExists(c, "Batchs") ? "Batchs"
                : ManagerAnalytics.tableExists(c, "BatchLocation") ? "BatchLocation" : null;
        if (table == null) return arr;
        Set<String> cols = ManagerAnalytics.columns(c, table);
        // نام ستون‌های واقعی آتیران برای تاریخ انقضا (jalali برای نمایش، gregorian برای مرتب‌سازی)
        String exp = ManagerAnalytics.resolveFlexible(cols, "ExpDate", "ExpDa", "exp_date", "ExpireDate", "expire", "DateExpiration",
                "tarikh_engheza", "تاریخ_انقضا", "ExpDateG", "ExpDateGregor", "ExpDateGregorian");
        if (exp == null) return arr;
        String expSort = ManagerAnalytics.resolveFlexible(cols, "ExpDateG", "ExpDateGregor", "ExpDateGregorian", "ExpDate");
        String qty = quantityCol(cols);
        String key = productKey(cols);
        String loc = ManagerAnalytics.resolveFlexible(cols, "BatchLocation", "location", "loc", "sh_batch", "batch_no", "batch");
        Set<String> inv = ManagerAnalytics.columns(c, "inventory");
        String iKey = productKey(inv);
        String iName = ManagerAnalytics.resolveFlexible(inv, "naka", "Name", "KalaName", "ProductName", "نام", "نام_کالا");
        String joinInv = (key == null || iKey == null) ? "" : " LEFT JOIN dbo.inventory k ON TRY_CONVERT(nvarchar(100),k.[" + iKey + "])=TRY_CONVERT(nvarchar(100),b.[" + key + "])";
        String sql = "SELECT TOP (" + Math.max(1, cap) + ") TRY_CONVERT(nvarchar(100),b.[" + exp + "])"
                + (key == null ? ", CAST(NULL AS nvarchar(80))" : ", TRY_CONVERT(nvarchar(80),b.[" + key + "])")
                + (iName == null ? ", CAST(NULL AS nvarchar(250))" : ", TRY_CONVERT(nvarchar(250),k.[" + iName + "])")
                + (loc == null ? ", CAST(NULL AS nvarchar(80))" : ", TRY_CONVERT(nvarchar(80),b.[" + loc + "])")
                + (qty == null ? ", CAST(NULL AS decimal(19,3))" : ", TRY_CONVERT(decimal(19,3),b.[" + qty + "])")
                + " FROM dbo.[" + table + "] b" + joinInv
                + " WHERE TRY_CONVERT(nvarchar(100),b.[" + exp + "]) IS NOT NULL"
                + (expSort == null || expSort.equals(exp) ? " ORDER BY 1" : " ORDER BY TRY_CONVERT(nvarchar(100),b.[" + expSort + "])");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    JSONObject o = new JSONObject();
                    o.put("expiry", safe(r.getString(1), ""));
                    o.put("code", safe(r.getString(2), ""));
                    o.put("name", safe(r.getString(3), ""));
                    o.put("location", safe(r.getString(4), ""));
                    o.put("qty", r.getDouble(5));
                    arr.put(o);
                }
            }
        }
        return arr;
    }

    // ---------------------------------------------------------------- اسنپ‌شات کامل
    /**
     * همهٔ بلوک‌های انبار در یک اتصال. هر بلوک مستقل خطا می‌گیرد و در کلید {@code errors} می‌نویسد.
     *
     * @param focusCode کد کالایی که کارت گردش آن خواسته شده (می‌تواند خالی باشد)
     */
    static JSONObject snapshot(Connection c, String focusCode, boolean withStockRows, int stockRowCap) {
        JSONObject out = new JSONObject();
        JSONArray errors = new JSONArray();
        try {
            out.put("warehouses", warehouses(c));
        } catch (Exception e) {
            errors.put("انبارها: " + MeelanoWarehouse.shortMsg(e));
        }
        if (withStockRows) {
            try {
                out.put("stockRows", stockRows(c, stockRowCap <= 0 ? 120 : stockRowCap));
            } catch (Exception e) {
                errors.put("موجودی به تفکیک انبار: " + MeelanoWarehouse.shortMsg(e));
            }
        }
        try {
            out.put("salesWindow", salesWindow(c));
        } catch (Exception e) {
            errors.put("بازهٔ فروش: " + MeelanoWarehouse.shortMsg(e));
        }
        try {
            out.put("movements", movementTotals(c, 400));
        } catch (Exception e) {
            errors.put("گردش کالاها: " + MeelanoWarehouse.shortMsg(e));
        }
        if (focusCode != null && !focusCode.trim().isEmpty()) {
            try {
                out.put("kardex", kardex(c, focusCode, 40));
            } catch (Exception e) {
                errors.put("کارت گردش کالا: " + MeelanoWarehouse.shortMsg(e));
            }
        }
        try {
            out.put("expiring", expiringBatches(c, 60));
        } catch (Exception e) {
            errors.put("سری‌ها/تاریخ انقضا: " + MeelanoWarehouse.shortMsg(e));
        }
        try { out.put("errors", errors); } catch (Exception ignored) { }
        return out;
    }

    /**
     * ترجمهٔ گروه رویداد انبار به متن فارسی.
     *
     * <p>معنای قطعی رویدادها از رویهٔ {@code dbo.UpdateMojodiInventory} آتیران است؛ برای کدهایی که
     * معنای قطعی ندارند فقط جهت (ورود/خروج) نمایش داده می‌شود تا عددی اشتباه به کاربر نشان ندهیم.</p>
     */
    static String actLabel(int act, boolean out, String description) {
        switch (act) {
            case 11: return "خرید از تأمین‌کننده";
            case 47: return "موجودی اولیه";
            case 25: return "افزایش موجودی";
            case 17: case 27: return "برگشت کالا";
            case 86: return "دریافت از تولید";
            case 20: case 22: return "فروش";
            case 18: return "ضایعات/کسری";
            case 26: return "برگشت به تأمین‌کننده";
            case 85: return "مصرف در تولید";
            case 5: case 19: case 48: case 133: return out ? "خروج انبار" : "ورود انبار";
            default:
                if (description != null && !description.trim().isEmpty()) return description.trim();
                return out ? "خروج انبار" : "ورود انبار";
        }
    }

    private static String safe(String v, String fallback) {
        if (v == null) return fallback;
        String t = v.replace('ي', 'ی').replace('ك', 'ک').trim();
        return t.isEmpty() ? fallback : t;
    }
}
