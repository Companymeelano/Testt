package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Purchase and customer reports. Amounts come only from columns that are verified in
 * the Atiran sample schema: invoice header totals (buyfact.all / tax / tafif / shmo /
 * DATE, sailfact.all) and line quantities (TEDVAH). Purchase-line money columns are not
 * used, because subbuyfact has no verified amount column (see AdvancedReports).
 * Customer data comes from CUSTOMERS (SHMO, MONAME, cell, addre, kind,
 * PersonalityType, black_list, Lat/Lng, man) joined on SHMO.
 */
public final class BuyCustomerReports {
    private BuyCustomerReports() { }

    /** Date-filtered, de-duplicated header of a purchase or sales invoice (alias h). */
    static final class Head {
        String sql;
        String dateCol;
        String numberCol;
        final List<Object> binds = new ArrayList<>();
    }

    static Head head(Meta m, Filter f, String table) throws Queries.Missing {
        return head(m, f, table, "h");
    }

    static Head head(Meta m, Filter f, String table, String alias) throws Queries.Missing {
        Head h = new Head();
        boolean buy = "buyfact".equals(table);
        Set<String> cols = m.columns(table);
        h.dateCol = buy ? m.must(table, "تاریخ", "DATE", "date") : m.must(table, "تاریخ", "date");
        h.numberCol = buy ? m.must(table, "شماره فاکتور", "shfackh", "shfac")
                : m.must(table, "شماره فاکتور", "shfacfo");
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", h.dateCol), f.from, f.to, h.binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        h.sql = Sql.dedupe(table, h.numberCol, alias, inner);
        return h;
    }

    /** Customer name expression with its join (alias c), keyed by the header party column h.shmo. */
    private static String partyJoin(Meta m) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        return " LEFT JOIN dbo.CUSTOMERS c ON TRY_CONVERT(nvarchar(60),c.[" + shmo
                + "])=TRY_CONVERT(nvarchar(60),h.[shmo])";
    }

    private static String partyName(Meta m) {
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        return "COALESCE(" + (name == null ? "" : Sql.txt("c", name, 200) + ",")
                + "TRY_CONVERT(nvarchar(60),h.[shmo]),N'—')";
    }

    // =====================================================================================
    // PURCHASE
    // =====================================================================================

    /** Daily purchase totals with tax and discount: day,total,tax,discount,docs. */
    public static Queries.Q buyDailyTotals(Meta m, Filter f) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        String tax = m.col("buyfact", "tax");
        String tafif = m.col("buyfact", "tafif");
        String day = Sql.date10("h", h.dateCol);
        return new Queries.Q("SELECT " + day + " AS day"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS total"
                + ", ISNULL(SUM(" + Sql.num("h", tax) + "),0) AS tax"
                + ", ISNULL(SUM(" + Sql.num("h", tafif) + "),0) AS discount"
                + ", COUNT_BIG(1) AS docs FROM " + h.sql
                + " GROUP BY " + day + " ORDER BY 1", h.binds);
    }

    /** Monthly purchases with average invoice: month,total,docs,avg. */
    public static Queries.Q buyMonthlyTotals(Meta m, Filter f) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        String month = Sql.month7("h", h.dateCol);
        return new Queries.Q("SELECT " + month + " AS month"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS total"
                + ", COUNT_BIG(1) AS docs"
                + ", CAST(ISNULL(SUM(" + Sql.num("h", all) + "),0)/NULLIF(COUNT_BIG(1),0) AS decimal(19,2)) AS avg"
                + " FROM " + h.sql + " GROUP BY " + month + " ORDER BY 1", h.binds);
    }

    /** Largest purchase invoices: no,date,party,total. */
    public static Queries.Q buyTopInvoices(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") "
                + Sql.txt("h", h.numberCol, 60) + " AS no"
                + ", " + Sql.date10("h", h.dateCol) + " AS date"
                + ", " + partyName(m) + " AS party"
                + ", " + Sql.num("h", all) + " AS total"
                + " FROM " + h.sql + partyJoin(m)
                + " ORDER BY total DESC", combined(h.binds));
    }

    /** Share of each supplier in purchases: label,total,docs,share(%). */
    public static Queries.Q buySupplierShare(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        String party = partyName(m);
        Queries.Q inner = new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + party + " AS label"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS total, COUNT_BIG(1) AS docs"
                + " FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + party + " ORDER BY 2 DESC", h.binds);
        return new Queries.Q("SELECT t.label AS label, t.total AS total, t.docs AS docs"
                + ", CAST(100.0*t.total/NULLIF(SUM(t.total) OVER (),0) AS decimal(9,1)) AS share"
                + " FROM (" + inner.sql + ") t ORDER BY t.total DESC", inner.binds);
    }

    /** Purchase discount received per supplier: label,total,discount,docs. */
    public static Queries.Q buyDiscountBySupplier(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        String tafif = m.col("buyfact", "tafif");
        String party = partyName(m);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + party + " AS label"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS total"
                + ", ISNULL(SUM(" + Sql.num("h", tafif) + "),0) AS discount"
                + ", COUNT_BIG(1) AS docs FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + party + " ORDER BY 3 DESC", h.binds);
    }

    /** Products bought in the period with the number of invoices and quantity: label,docs,qty. */
    public static Queries.Q buyProductDocs(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        Set<String> sub = m.columns("subbuyfact");
        if (sub.isEmpty()) throw new Queries.Missing("«اقلام فاکتور خرید» در دیتابیس پیدا نشد");
        String link = m.must("subbuyfact", "شماره فاکتور", "shfackh", "shfac");
        String shka = m.must("subbuyfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subbuyfact", "تعداد", "TEDVAH", "tedvah");
        String invName = m.col("inventory", "naka", "NAKA");
        String invShka = m.col("inventory", "SHKA", "shka");
        boolean useInv = invName != null && invShka != null;
        String label = "COALESCE(" + (useInv ? Sql.txt("i", invName, 250) + "," : "")
                + Sql.txt("d", shka, 120) + ",N'—')";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", COUNT(DISTINCT TRY_CONVERT(nvarchar(60),h.[" + h.numberCol + "])) AS docs"
                + ", ISNULL(SUM(" + Sql.numNull("d", qty) + "),0) AS qty"
                + " FROM " + h.sql
                + " JOIN dbo.subbuyfact d ON TRY_CONVERT(nvarchar(100),d.[" + link
                + "])=TRY_CONVERT(nvarchar(100),h.[" + h.numberCol + "])"
                + (!useInv ? ""
                : " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka
                + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])")
                + " GROUP BY " + label + " ORDER BY 3 DESC", h.binds);
    }

    /** Products with stock on hand that were not bought in the period: label,stock,unit. */
    public static Queries.Q buyDormantStock(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String link = m.must("subbuyfact", "شماره فاکتور", "shfackh", "shfac");
        String lineShka = m.must("subbuyfact", "کد کالا", "SHKA", "shka");
        String invShka = m.must("inventory", "SHKA", "shka");
        String invName = m.col("inventory", "naka", "NAKA");
        String stock = m.must("inventory", "mojkavah");
        String unit = m.col("inventory", "vahsanj");
        String label = "COALESCE(" + (invName == null ? "" : Sql.txt("i", invName, 250) + ",")
                + Sql.txt("i", invShka, 120) + ",N'—')";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", " + Sql.numNull("i", stock) + " AS stock"
                + ", " + (unit == null ? "CAST(NULL AS nvarchar(40))" : Sql.txt("i", unit, 40)) + " AS unit"
                + " FROM dbo.inventory i WHERE TRY_CONVERT(decimal(19,3),i.[" + stock + "]) > 0"
                + " AND NOT EXISTS (SELECT 1 FROM " + h.sql + " JOIN dbo.subbuyfact d ON TRY_CONVERT(nvarchar(100),d.["
                + link + "])=TRY_CONVERT(nvarchar(100),h.[" + h.numberCol + "])"
                + " WHERE TRY_CONVERT(nvarchar(100),d.[" + lineShka + "])=TRY_CONVERT(nvarchar(100),i.[" + invShka + "]))"
                + " ORDER BY stock DESC", h.binds);
    }

    /** Last purchase per supplier: label,docs,total,last. */
    public static Queries.Q buyPartyLast(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String all = m.must("buyfact", "مبلغ", "AllFel", "all");
        String party = partyName(m);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + party + " AS label"
                + ", COUNT_BIG(1) AS docs"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS total"
                + ", MAX(" + Sql.date10("h", h.dateCol) + ") AS last"
                + " FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + party + " ORDER BY 4 DESC", h.binds);
    }

    /** Stock cover: quantity bought in the period against stock on hand: label,bought,stock,cover. */
    public static Queries.Q buyStockCover(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "buyfact");
        String link = m.must("subbuyfact", "شماره فاکتور", "shfackh", "shfac");
        String shka = m.must("subbuyfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subbuyfact", "تعداد", "TEDVAH", "tedvah");
        String invShka = m.must("inventory", "SHKA", "shka");
        String invName = m.col("inventory", "naka", "NAKA");
        String stock = m.must("inventory", "mojkavah");
        String label = "COALESCE(" + (invName == null ? "" : Sql.txt("i", invName, 250) + ",")
                + Sql.txt("d", shka, 120) + ",N'—')";
        Queries.Q inner = new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", ISNULL(SUM(" + Sql.numNull("d", qty) + "),0) AS bought"
                + ", MAX(" + Sql.numNull("i", stock) + ") AS stock"
                + " FROM " + h.sql
                + " JOIN dbo.subbuyfact d ON TRY_CONVERT(nvarchar(100),d.[" + link
                + "])=TRY_CONVERT(nvarchar(100),h.[" + h.numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka
                + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])"
                + " GROUP BY " + label + " ORDER BY 2 DESC", h.binds);
        return new Queries.Q("SELECT t.label AS label, t.bought AS bought, t.stock AS stock"
                + ", CAST(t.stock/NULLIF(t.bought,0) AS decimal(9,2)) AS cover"
                + " FROM (" + inner.sql + ") t ORDER BY cover DESC", inner.binds);
    }

    /** Monthly purchases against sales: month,buy,sales. */
    public static Queries.Q buyVsSalesMonthly(Meta m, Filter f) throws Queries.Missing {
        Head b = head(m, f, "buyfact", "b");
        Head s = head(m, f, "sailfact", "s");
        String buyAll = m.must("buyfact", "مبلغ", "AllFel", "all");
        String saleAll = m.must("sailfact", "مبلغ", "all_fel", "all");
        String bm = Sql.month7("b", b.dateCol);
        String sm = Sql.month7("s", s.dateCol);
        List<Object> binds = new ArrayList<>(b.binds);
        binds.addAll(s.binds);
        return new Queries.Q("SELECT u.month AS month, SUM(u.buy) AS buy, SUM(u.sales) AS sales FROM ("
                + "SELECT " + bm + " AS month, " + Sql.num("b", buyAll) + " AS buy"
                + ", CAST(0 AS decimal(19,2)) AS sales FROM " + b.sql + " "
                + "UNION ALL SELECT " + sm + " AS month, CAST(0 AS decimal(19,2)) AS buy"
                + ", " + Sql.num("s", saleAll) + " AS sales FROM " + s.sql + ") u"
                + " GROUP BY u.month ORDER BY 1", binds);
    }

    // =====================================================================================
    // CUSTOMERS
    // =====================================================================================

    /** Sales by customer kind code: label,customers,total,docs. */
    public static Queries.Q custByKind(Meta m, Filter f) throws Queries.Missing {
        return custGroup(m, f, "kind", 60);
    }

    /** Sales by personality type: label,customers,total,docs. */
    public static Queries.Q custByPersonality(Meta m, Filter f) throws Queries.Missing {
        return custGroup(m, f, "PersonalityType", 60);
    }

    private static Queries.Q custGroup(Meta m, Filter f, String attr, int len) throws Queries.Missing {
        Head h = head(m, f, "sailfact");
        String amount = m.must("sailfact", "مبلغ", "all_fel", "all");
        String col = m.col("CUSTOMERS", attr);
        if (col == null) throw new Queries.Missing("ستون «" + attr + "» در «مشتریان» پیدا نشد");
        String label = "COALESCE(" + Sql.txt("c", col, len) + ",N'نامشخص')";
        return new Queries.Q("SELECT " + label + " AS label"
                + ", COUNT(DISTINCT TRY_CONVERT(nvarchar(60),h.[shmo])) AS customers"
                + ", ISNULL(SUM(" + Sql.num("h", amount) + "),0) AS total"
                + ", COUNT_BIG(1) AS docs FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + label + " ORDER BY 3 DESC", h.binds);
    }

    /** Active customers without a mobile number: name,code,addre. */
    public static Queries.Q custNoCell(Meta m, int top) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String cell = m.col("CUSTOMERS", "cell", "mobile", "Mobile");
        String addr = m.col("CUSTOMERS", "addre");
        if (cell == null) throw new Queries.Missing("ستون شماره همراه در «مشتریان» پیدا نشد");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") "
                + (name == null ? Sql.txt("c", shmo, 60) : Sql.txt("c", name, 200)) + " AS name"
                + ", " + Sql.txt("c", shmo, 60) + " AS code"
                + ", " + (addr == null ? "CAST(NULL AS nvarchar(200))" : Sql.txt("c", addr, 200)) + " AS addre"
                + " FROM dbo.CUSTOMERS c WHERE NULLIF(LTRIM(RTRIM(" + Sql.txt("c", cell, 40) + ")),N'') IS NULL"
                + " ORDER BY 1", new ArrayList<>());
    }

    /** Customers on the black list with their balance: name,code,cell,balance. */
    public static Queries.Q custBlacklist(Meta m, int top) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String cell = m.col("CUSTOMERS", "cell", "mobile", "Mobile");
        String black = m.col("CUSTOMERS", "black_list");
        String bal = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        if (black == null) throw new Queries.Missing("ستون لیست سیاه در «مشتریان» پیدا نشد");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") "
                + (name == null ? Sql.txt("c", shmo, 60) : Sql.txt("c", name, 200)) + " AS name"
                + ", " + Sql.txt("c", shmo, 60) + " AS code"
                + ", " + (cell == null ? "CAST(NULL AS nvarchar(40))" : Sql.txt("c", cell, 40)) + " AS cell"
                + ", " + Sql.numNull("c", bal) + " AS balance"
                + " FROM dbo.CUSTOMERS c WHERE TRY_CONVERT(int,c.[" + black + "])=1"
                + " ORDER BY balance DESC", new ArrayList<>());
    }

    /** Customers with no saved map position (Lat/Lng empty or zero): name,code,addre. */
    public static Queries.Q custNoLocation(Meta m, int top) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String lat = m.col("CUSTOMERS", "Lat");
        String lng = m.col("CUSTOMERS", "Lng");
        String addr = m.col("CUSTOMERS", "addre");
        if (lat == null || lng == null) throw new Queries.Missing("مختصات مکانی مشتری در دیتابیس پیدا نشد");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") "
                + (name == null ? Sql.txt("c", shmo, 60) : Sql.txt("c", name, 200)) + " AS name"
                + ", " + Sql.txt("c", shmo, 60) + " AS code"
                + ", " + (addr == null ? "CAST(NULL AS nvarchar(200))" : Sql.txt("c", addr, 200)) + " AS addre"
                + " FROM dbo.CUSTOMERS c WHERE ISNULL(TRY_CONVERT(float,c.[" + lat + "]),0)=0"
                + " OR ISNULL(TRY_CONVERT(float,c.[" + lng + "]),0)=0"
                + " ORDER BY 1", new ArrayList<>());
    }

    /** Purchase frequency per customer in the period: name,docs,total,avg,first,last. */
    public static Queries.Q custFrequency(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "sailfact");
        String amount = m.must("sailfact", "مبلغ", "all_fel", "all");
        String date = Sql.date10("h", h.dateCol);
        String party = "COALESCE(" + (m.col("CUSTOMERS", "MONAME") == null ? ""
                : Sql.txt("c", m.col("CUSTOMERS", "MONAME"), 200) + ",")
                + "TRY_CONVERT(nvarchar(60),h.[shmo]),N'—')";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + party + " AS name"
                + ", COUNT_BIG(1) AS docs"
                + ", ISNULL(SUM(" + Sql.num("h", amount) + "),0) AS total"
                + ", CAST(ISNULL(SUM(" + Sql.num("h", amount) + "),0)/NULLIF(COUNT_BIG(1),0) AS decimal(19,2)) AS avg"
                + ", MIN(" + date + ") AS first, MAX(" + date + ") AS last"
                + " FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + party + " ORDER BY 2 DESC", h.binds);
    }

    /** Customers who bought for the first time in each month of the period: month,customers. */
    public static Queries.Q custNewMonthly(Meta m, Filter f) throws Queries.Missing {
        Head h = head(m, f, "sailfact");
        String date = Sql.date10("h", h.dateCol);
        return new Queries.Q("SELECT LEFT(x.first,7) AS month, COUNT(*) AS customers FROM ("
                + "SELECT TRY_CONVERT(nvarchar(60),h.[shmo]) AS k, MIN(" + date + ") AS first FROM " + h.sql
                + " GROUP BY TRY_CONVERT(nvarchar(60),h.[shmo])) x"
                + " GROUP BY LEFT(x.first,7) ORDER BY 1", h.binds);
    }

    /** Customers with at least two invoices, by average invoice amount: name,docs,avg,total. */
    public static Queries.Q custAvgInvoice(Meta m, Filter f, int top) throws Queries.Missing {
        Head h = head(m, f, "sailfact");
        String amount = m.must("sailfact", "مبلغ", "all_fel", "all");
        String party = "COALESCE(" + (m.col("CUSTOMERS", "MONAME") == null ? ""
                : Sql.txt("c", m.col("CUSTOMERS", "MONAME"), 200) + ",")
                + "TRY_CONVERT(nvarchar(60),h.[shmo]),N'—')";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + party + " AS name"
                + ", COUNT_BIG(1) AS docs"
                + ", CAST(ISNULL(SUM(" + Sql.num("h", amount) + "),0)/NULLIF(COUNT_BIG(1),0) AS decimal(19,2)) AS avg"
                + ", ISNULL(SUM(" + Sql.num("h", amount) + "),0) AS total"
                + " FROM " + h.sql + partyJoin(m)
                + " GROUP BY " + party + " HAVING COUNT_BIG(1) >= 2 ORDER BY 3 DESC", h.binds);
    }

    private static List<Object> combined(List<Object> binds) {
        return new ArrayList<>(binds);
    }
}
