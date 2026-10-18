package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extra profit & loss reports. Every query uses the same joins and column
 * resolution as the existing profit reports in MasterQueries (sailfact header,
 * subsailfact lines, inventory buy price as COGS basis, kagroup for groups), so
 * the schema names are checked at runtime through Meta and never hardcoded.
 */
public final class ProfitReports {
    private ProfitReports() { }

    /** Shared pieces: date-filtered, de-duplicated sales header and its lines joined to inventory. */
    private static final class Src {
        String head;        // derived table "h" (sales headers in range)
        String lineJoin;    // JOIN subsailfact d ... LEFT JOIN inventory i
        String sum;         // line amount expression (sales)
        String qty;         // line quantity expression
        String buy;         // inventory buy price column
        String shka;        // line product key
        String invName;     // inventory name column (may be null)
        String grp;         // inventory group key (may be null)
        String grpName;     // kagroup name (may be null)
        String grpKey;      // kagroup key (may be null)
        String dateCol;     // sailfact date column
        String numberCol;   // sailfact number column
        Set<String> cols;   // sailfact columns
        List<Object> binds = new ArrayList<>();
    }

    private static Src build(Meta m, Filter f, boolean needGroup) throws Queries.Missing {
        Src s = new Src();
        s.cols = m.columns("sailfact");
        s.dateCol = m.must("sailfact", "تاریخ", "date");
        s.numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        s.shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String sum = m.must("subsailfact", "جمع ردیف", "LINESUM", "linesum");
        s.qty = qty;
        s.sum = sum;
        s.buy = MasterQueries.cogsBuyCol(m);
        String invShka = MasterQueries.cogsShka(m);
        s.invName = m.col("inventory", "naka", "NAKA");
        if (needGroup) {
            s.grp = m.col("inventory", "group_rdf");
            s.grpName = m.col("kagroup", "group_name");
            s.grpKey = m.col("kagroup", "group_rdf");
            if (s.grp == null || s.grpName == null || s.grpKey == null || !m.table("kagroup"))
                throw new Queries.Missing("گروه کالا در دیتابیس پیدا نشد");
        }
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", s.dateCol), f.from, f.to, s.binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(s.cols, "x") + Sql.softAnd(s.cols, "x");
        s.head = Sql.dedupe("sailfact", s.numberCol, "h", inner);
        s.lineJoin = " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol
                + "])=TRY_CONVERT(nvarchar(100),h.[" + s.numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka
                + "])=TRY_CONVERT(nvarchar(100),d.[" + s.shka + "])";
        if (needGroup) {
            s.lineJoin += " LEFT JOIN dbo.kagroup g ON TRY_CONVERT(nvarchar(50),g.[" + s.grpKey
                    + "])=TRY_CONVERT(nvarchar(50),i.[" + s.grp + "])";
        }
        return s;
    }

    private static String salesExpr(Src s) {
        return "ISNULL(SUM(" + Sql.num("d", s.sum) + "),0)";
    }

    private static String cogsExpr(Src s) {
        return "ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + s.qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.["
                + s.buy + "]),0)),0)";
    }

    private static String productLabel(Src s) {
        return "COALESCE(" + (s.invName == null ? "" : Sql.txt("i", s.invName, 250) + ",")
                + Sql.txt("d", s.shka, 120) + ",N'—')";
    }

    /** Profit by product group: label,sales,cogs,profit (buy-price basis). */
    public static Queries.Q byGroup(Meta m, Filter f, int top) throws Queries.Missing {
        Src s = build(m, f, true);
        String label = "COALESCE(" + Sql.txt("g", s.grpName, 150) + ",N'بدون گروه')";
        String sales = salesExpr(s);
        String cogs = cogsExpr(s);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", " + sales + " AS sales, " + cogs + " AS cogs, (" + sales + "-" + cogs + ") AS profit"
                + " FROM " + s.head + s.lineJoin
                + " GROUP BY " + label + " ORDER BY 4 DESC", s.binds);
    }

    /** Share of each group in the total profit: label,profit,share(%). */
    public static Queries.Q groupShare(Meta m, Filter f, int top) throws Queries.Missing {
        Queries.Q inner = byGroup(m, f, top);
        return new Queries.Q("SELECT t.label AS label, t.profit AS profit"
                + ", CAST(100.0*t.profit/NULLIF(SUM(t.profit) OVER (),0) AS decimal(9,1)) AS share"
                + " FROM (" + inner.sql + ") t ORDER BY t.profit DESC", inner.binds);
    }

    /** Products sold below cost in the period: label,qty,sales,cogs,profit (profit < 0). */
    public static Queries.Q lossProducts(Meta m, Filter f, int top) throws Queries.Missing {
        Src s = build(m, f, false);
        String label = productLabel(s);
        String sales = salesExpr(s);
        String cogs = cogsExpr(s);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + s.qty + "])),0) AS qty"
                + ", " + sales + " AS sales, " + cogs + " AS cogs, (" + sales + "-" + cogs + ") AS profit"
                + " FROM " + s.head + s.lineJoin
                + " GROUP BY " + label
                + " HAVING (" + sales + "-" + cogs + ") < 0 ORDER BY 5 ASC", s.binds);
    }

    /** Products counted in margin bands: band,products,sales,profit. */
    public static Queries.Q marginBands(Meta m, Filter f) throws Queries.Missing {
        Src s = build(m, f, false);
        String sales = salesExpr(s);
        String cogs = cogsExpr(s);
        String per = "SELECT TRY_CONVERT(nvarchar(100),d.[" + s.shka + "]) AS k, " + sales + " AS sales"
                + ", (" + sales + "-" + cogs + ") AS profit"
                + " FROM " + s.head + s.lineJoin + " GROUP BY TRY_CONVERT(nvarchar(100),d.[" + s.shka + "])";
        String band = "CASE WHEN sales<=0 THEN N'بدون فروش' WHEN profit<0 THEN N'زیان'"
                + " WHEN profit*100.0/sales<10 THEN N'کمتر از ۱۰٪'"
                + " WHEN profit*100.0/sales<25 THEN N'۱۰ تا ۲۵٪'"
                + " WHEN profit*100.0/sales<40 THEN N'۲۵ تا ۴۰٪' ELSE N'بالای ۴۰٪' END";
        return new Queries.Q("SELECT band AS band, COUNT_BIG(1) AS products, ISNULL(SUM(sales),0) AS sales"
                + ", ISNULL(SUM(profit),0) AS profit FROM (SELECT " + band + " AS band, sales, profit FROM ("
                + per + ") p) b GROUP BY band ORDER BY 4 DESC", s.binds);
    }

    /** Daily discount given vs sales: day,sales,discount,docs. */
    public static Queries.Q discountDaily(Meta m, Filter f) throws Queries.Missing {
        Src s = build(m, f, false);
        String tafif = m.col("sailfact", "tafif");
        if (tafif == null) throw new Queries.Missing("ستون تخفیف فاکتور فروش در دیتابیس پیدا نشد");
        String all = m.must("sailfact", "مبلغ", "all_fel", "all");
        String day = Sql.date10("h", s.dateCol);
        return new Queries.Q("SELECT " + day + " AS day"
                + ", ISNULL(SUM(" + Sql.num("h", all) + "),0) AS sales"
                + ", ISNULL(SUM(" + Sql.num("h", tafif) + "),0) AS discount"
                + ", COUNT_BIG(1) AS docs FROM " + s.head
                + " GROUP BY " + day + " ORDER BY 1", s.binds);
    }

    /** Monthly profit with running total: day(=YYYY/MM),sales,cogs,profit,cum. */
    public static Queries.Q cumulativeMonthly(Meta m, Filter f) throws Queries.Missing {
        Queries.Q monthly = MasterQueries.profitMonthly(m, f);
        String base = monthly.sql;
        String tail = " ORDER BY day";
        if (!base.endsWith(tail)) throw new Queries.Missing("ساختار گزارش ماهانه سود تغییر کرده است");
        base = base.substring(0, base.length() - tail.length());
        return new Queries.Q("SELECT t.day AS day, t.sales AS sales, t.cogs AS cogs, t.profit AS profit"
                + ", SUM(t.profit) OVER (ORDER BY t.day ROWS UNBOUNDED PRECEDING) AS cum"
                + " FROM (" + base + ") t ORDER BY t.day", monthly.binds);
    }

    /** Average selling price against purchase cost per product: label,qty,avgSell,buy,realization(%). */
    public static Queries.Q priceRealization(Meta m, Filter f, int top) throws Queries.Missing {
        Src s = build(m, f, false);
        String label = productLabel(s);
        String sql = "SELECT TOP (" + Queries.clampTop(top) + ") t.label AS label, t.qty AS qty"
                + ", CAST(t.sales/NULLIF(t.qty,0) AS decimal(19,2)) AS avgSell"
                + ", t.buy AS buy"
                + ", CAST(CASE WHEN t.buy>0 THEN 100.0*(t.sales/NULLIF(t.qty,0))/t.buy END AS decimal(9,1)) AS realization"
                + " FROM (SELECT " + label + " AS label"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + s.qty + "])),0) AS qty"
                + ", " + salesExpr(s) + " AS sales"
                + ", MAX(COALESCE(TRY_CONVERT(decimal(19,2),i.[" + s.buy + "]),0)) AS buy"
                + " FROM " + s.head + s.lineJoin
                + " GROUP BY " + label + ") t"
                + " WHERE t.qty > 0 ORDER BY realization DESC";
        return new Queries.Q(sql, s.binds);
    }
}
