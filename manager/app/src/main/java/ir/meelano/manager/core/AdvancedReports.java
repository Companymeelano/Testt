package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sales, purchase and visitor reports built on the same verified Atiran columns used by the
 * existing reports (sailfact/subsailfact, buyfact/subbuyfact, inventory, visitors).
 * Every money figure is a SQL aggregate over those tables; none is computed on the device.
 */
public final class AdvancedReports {
    private AdvancedReports() {}

    /** Visitor profit: docs, sales, COGS (buy-price basis), profit, margin %. */
    public static Queries.Q visitorProfit(Meta m, Filter f, int top) throws Queries.Missing {
        String visCol = m.must("sailfact", "ویزیتور", "vis_rdf", "VisitorID");
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String sum = m.must("subsailfact", "جمع ردیف", "LINESUM", "linesum");
        String buy = MasterQueries.cogsBuyCol(m);
        String invShka = MasterQueries.cogsShka(m);
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String label = Queries.visNameExpr(m, "h", visCol);
        String sales = "ISNULL(SUM(" + Sql.num("d", sum) + "),0)";
        String cogs = "ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + buy + "]),0)),0)";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", COUNT(DISTINCT h.[" + numberCol + "]) AS docs"
                + ", " + sales + " AS sales, " + cogs + " AS cogs, (" + sales + "-" + cogs + ") AS profit"
                + ", CASE WHEN " + sales + "<>0 THEN ROUND((" + sales + "-" + cogs + ")*100.0/" + sales + ",1) ELSE 0 END AS margin"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])"
                + Queries.visJoin(m, "h", visCol)
                + " GROUP BY " + label + " ORDER BY 3 DESC", binds);
    }

    /** Visitor collection: docs, invoiced, collected, remaining, collection rate %. */
    public static Queries.Q visitorCollection(Meta m, Filter f, int top) throws Queries.Missing {
        String visCol = m.must("sailfact", "ویزیتور", "vis_rdf", "VisitorID");
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String amount = m.must("sailfact", "مبلغ", "all");
        String paid = m.must("sailfact", "پرداخت‌شده", "MabDaryaftFactor", "Daryaft");
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String label = Queries.visNameExpr(m, "h", visCol);
        String total = "ISNULL(SUM(" + Sql.num("h", amount) + "),0)";
        String got = "ISNULL(SUM(" + Sql.num("h", paid) + "),0)";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", COUNT(DISTINCT h.[" + numberCol + "]) AS docs"
                + ", " + total + " AS total, " + got + " AS collected, (" + total + "-" + got + ") AS remain"
                + ", CASE WHEN " + total + "<>0 THEN ROUND(" + got + "*100.0/" + total + ",1) ELSE 0 END AS rate"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + Queries.visJoin(m, "h", visCol)
                + " GROUP BY " + label + " ORDER BY 3 DESC", binds);
    }

    /** Per-product unit-price spread on sales lines (discount leakage): min, max, avg, spread %. */
    public static Queries.Q salePriceSpread(Meta m, Filter f, int top) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String sum = m.must("subsailfact", "جمع ردیف", "LINESUM", "linesum");
        String invName = m.col("inventory", "naka", "NAKA");
        String invShka = m.col("inventory", "SHKA", "shka");
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        boolean useInv = invName != null && invShka != null;
        String label = "COALESCE(" + (useInv ? Sql.txt("i", invName, 250) + "," : "") + Sql.txt("d", shka, 120) + ",N'—')";
        String unit = "CAST(" + Sql.num("d", sum) + " AS decimal(19,4)) / NULLIF(" + Sql.numNull("d", qty) + ",0)";
        String inv = useInv ? " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])" : "";
        String u = "SELECT TRY_CONVERT(nvarchar(100),d.[" + shka + "]) AS k, " + label + " AS label, (" + unit + ") AS unit, "
                + Sql.numNull("d", qty) + " AS qty, h.[" + numberCol + "] AS doc"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + inv + " WHERE " + Sql.numNull("d", qty) + " > 0";
        String sql = "SELECT TOP (" + Queries.clampTop(top) + ") MAX(u.label) AS label"
                + ", COUNT(DISTINCT u.doc) AS docs, SUM(u.qty) AS qty"
                + ", MIN(u.unit) AS minUnit, MAX(u.unit) AS maxUnit, AVG(u.unit) AS avgUnit"
                + ", CAST(100.0*(MAX(u.unit)-MIN(u.unit))/NULLIF(MIN(u.unit),0) AS decimal(19,1)) AS spread"
                + " FROM (" + u + ") u WHERE u.unit IS NOT NULL AND u.unit > 0"
                + " GROUP BY u.k HAVING COUNT(*) >= 2 AND MIN(u.unit) > 0"
                + " ORDER BY 7 DESC";
        return new Queries.Q(sql, binds);
    }

    /** Supplier price gap: each supplier's average unit price per product vs the average of all suppliers. */
    public static Queries.Q supplierPriceGap(Meta m, Filter f, int top) throws Queries.Missing {
        Set<String> cols = m.columns("buyfact");
        String dateCol = m.must("buyfact", "تاریخ", "DATE", "date");
        String numberCol = m.must("buyfact", "شماره فاکتور", "shfackh", "shfac");
        String partyCol = m.must("buyfact", "طرف‌حساب", "shmo", "SHMO");
        String linkCol = m.must("subbuyfact", "شماره فاکتور", "shfackh", "shfac");
        String shka = m.must("subbuyfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subbuyfact", "تعداد", "TEDVAH", "tedvah");
        // The purchase-line amount is not in the verified sample schema (subbuyfact has no LINESUM).
        // Refuse to guess: the report stays unavailable until the real column is confirmed.
        String sum = Sql.pick(m.columns("subbuyfact"), "LINESUM", "linesum");
        if (sum == null) throw new Queries.Missing("ستون مبلغ خط خرید در «اقلام فاکتور خرید» تأیید نشده است");
        String invName = m.col("inventory", "naka", "NAKA");
        String invShka = m.col("inventory", "SHKA", "shka");
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        boolean useInv = invName != null && invShka != null;
        String prod = "COALESCE(" + (useInv ? Sql.txt("i", invName, 250) + "," : "") + Sql.txt("d", shka, 120) + ",N'—')";
        String inv = useInv ? " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])" : "";
        String u = "SELECT TRY_CONVERT(nvarchar(100),d.[" + shka + "]) AS k, TRY_CONVERT(nvarchar(100),h.[" + partyCol + "]) AS p"
                + ", " + prod + " AS prod, " + Queries.custNameExpr(m, "h", partyCol) + " AS sup"
                + ", " + Sql.num("d", sum) + " AS amt, " + Sql.numNull("d", qty) + " AS qty"
                + " FROM " + Sql.dedupe("buyfact", numberCol, "h", inner)
                + " JOIN dbo.subbuyfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + inv + Queries.custJoin(m, "h", partyCol)
                + " WHERE " + Sql.numNull("d", qty) + " > 0";
        String sql = "WITH u AS (" + u + "), pa AS ("
                + " SELECT k, p, MAX(prod) AS prod, MAX(sup) AS sup, SUM(amt) AS amt, SUM(qty) AS qty"
                + " FROM u GROUP BY k, p),"
                + " mk AS (SELECT k, SUM(amt) AS amt, SUM(qty) AS qty, COUNT(DISTINCT p) AS sups FROM u GROUP BY k)"
                + " SELECT TOP (" + Queries.clampTop(top) + ") MAX(pa.sup) AS label, MAX(pa.prod) AS product"
                + ", CAST(pa.amt/NULLIF(pa.qty,0) AS decimal(19,2)) AS supUnit"
                + ", CAST(mk.amt/NULLIF(mk.qty,0) AS decimal(19,2)) AS marketUnit"
                + ", CAST(100.0*(pa.amt/NULLIF(pa.qty,0)-mk.amt/NULLIF(mk.qty,0))/NULLIF(mk.amt/NULLIF(mk.qty,0),0) AS decimal(19,1)) AS gap"
                + ", pa.qty AS qty, mk.sups AS sups"
                + " FROM pa JOIN mk ON mk.k=pa.k"
                + " WHERE mk.sups >= 2 AND mk.amt/NULLIF(mk.qty,0) > 0"
                + " GROUP BY pa.k, pa.p, pa.amt, pa.qty, mk.amt, mk.qty, mk.sups"
                + " ORDER BY ABS(100.0*(pa.amt/NULLIF(pa.qty,0)-mk.amt/NULLIF(mk.qty,0))/NULLIF(mk.amt/NULLIF(mk.qty,0),0)) DESC";
        return new Queries.Q(sql, binds);
    }
}
