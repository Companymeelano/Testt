package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;

/**
 * Management reports that accounting packages usually do not offer. They use only the
 * verified sample-schema columns and the same header/line join pattern as the rest of the
 * manager (sailfact + subsailfact + inventory + CUSTOMERS). Date windows that are computed
 * in the app are embedded as validated literals («YYYY/MM/DD» digits only).
 */
public final class UniqueReports {
    private UniqueReports() { }

    private static String lit(String jalali) throws Queries.Missing {
        if (jalali == null || !jalali.matches("\\d{4}/\\d{2}/\\d{2}"))
            throw new Queries.Missing("تاریخ گزارش نامعتبر است");
        return "N'" + jalali + "'";
    }

    private static String custName(Meta m, String alias) throws Queries.Missing {
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        return name == null ? Sql.txt(alias, shmo, 60) : "COALESCE(" + Sql.txt(alias, name, 200) + ","
                + Sql.txt(alias, shmo, 60) + ")";
    }

    /**
     * Month-by-month customer retention: of the customers who buy this month, how many also
     * bought last month. The first month of the period has no earlier data inside the range.
     */
    public static Queries.Q customerRetention(Meta m, Filter f) throws Queries.Missing {
        BuyCustomerReports.Head h = BuyCustomerReports.head(m, f, "sailfact");
        String date = Sql.date10("h", h.dateCol);
        return new Queries.Q("WITH s AS (SELECT DISTINCT LEFT(" + date + ",7) AS mo"
                + ", TRY_CONVERT(nvarchar(60),h.[shmo]) AS k FROM " + h.sql + ")"
                + ", ix AS (SELECT mo, k, TRY_CONVERT(int,LEFT(mo,4))*12+TRY_CONVERT(int,SUBSTRING(mo,6,2)) AS idx"
                + " FROM s)"
                + " SELECT cu.mo AS month, COUNT(*) AS active"
                + ", SUM(CASE WHEN pv.k IS NULL THEN 0 ELSE 1 END) AS back"
                + ", CAST(100.0*SUM(CASE WHEN pv.k IS NULL THEN 0 ELSE 1 END)/NULLIF(COUNT(*),0) AS decimal(9,1)) AS rate"
                + " FROM ix cu LEFT JOIN ix pv ON pv.k=cu.k AND pv.idx=cu.idx-1"
                + " GROUP BY cu.mo ORDER BY 1", h.binds);
    }

    /** Products that are sold together in the same invoice: first,second,together,pct. */
    public static Queries.Q marketBasket(Meta m, Filter f, int top) throws Queries.Missing {
        BuyCustomerReports.Head h = BuyCustomerReports.head(m, f, "sailfact");
        String link = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String invShka = m.must("inventory", "SHKA", "shka");
        String invName = m.must("inventory", "naka", "NAKA");
        return new Queries.Q("WITH ln AS (SELECT TRY_CONVERT(nvarchar(100),d.[" + link + "]) AS inv"
                + ", TRY_CONVERT(nvarchar(100),d.[" + shka + "]) AS k"
                + " FROM dbo.subsailfact d JOIN " + h.sql
                + " ON TRY_CONVERT(nvarchar(100),d.[" + link + "])=TRY_CONVERT(nvarchar(100),h.[" + h.numberCol + "])"
                + " GROUP BY TRY_CONVERT(nvarchar(100),d.[" + link + "]), TRY_CONVERT(nvarchar(100),d.[" + shka + "]))"
                + " SELECT TOP (" + Queries.clampTop(top) + ")"
                + " COALESCE(" + Sql.txt("ia", invName, 250) + ",t.a) AS first"
                + ", COALESCE(" + Sql.txt("ib", invName, 250) + ",t.b) AS second"
                + ", t.together AS together"
                + ", CAST(100.0*t.together/NULLIF((SELECT COUNT(DISTINCT inv) FROM ln),0) AS decimal(9,1)) AS pct"
                + " FROM (SELECT x.k AS a, y.k AS b, COUNT(*) AS together FROM ln x"
                + " JOIN ln y ON x.inv=y.inv AND x.k<y.k GROUP BY x.k, y.k HAVING COUNT(*)>=2) t"
                + " LEFT JOIN dbo.inventory ia ON TRY_CONVERT(nvarchar(100),ia.[" + invShka + "])=t.a"
                + " LEFT JOIN dbo.inventory ib ON TRY_CONVERT(nvarchar(100),ib.[" + invShka + "])=t.b"
                + " ORDER BY together DESC", h.binds);
    }

    /** Customers close to their credit limit: name,code,credit,balance,used(%). */
    public static Queries.Q creditAlerts(Meta m, int top) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String cred = m.must("CUSTOMERS", "سقف اعتبار", "cred", "etebar", "credit");
        String man = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        String c = "TRY_CONVERT(decimal(19,2),c.[" + cred + "])";
        String b = "TRY_CONVERT(decimal(19,2),c.[" + man + "])";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + custName(m, "c") + " AS name"
                + ", " + Sql.txt("c", shmo, 60) + " AS code"
                + ", " + Sql.numNull("c", cred) + " AS credit"
                + ", " + Sql.numNull("c", man) + " AS balance"
                + ", CAST(100.0*" + b + "/NULLIF(" + c + ",0) AS decimal(9,1)) AS used"
                + " FROM dbo.CUSTOMERS c WHERE " + c + ">0 AND " + b + ">=0.8*" + c
                + " ORDER BY used DESC", new ArrayList<>());
    }

    /**
     * Customers whose purchases fell by at least half: the last 90 days against the 90 days
     * before that. Windows are computed from today in the app.
     */
    public static Queries.Q decliningCustomers(Meta m, int top) throws Queries.Missing {
        String today = Jalali.todayStr();
        String curStart = Jalali.addDays(today, -90);
        String prevStart = Jalali.addDays(today, -180);
        String prevEnd = Jalali.addDays(today, -91);
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String amount = m.must("sailfact", "مبلغ", "all");
        String shmo = m.must("sailfact", "طرف‌حساب", "shmo", "SHMO");
        java.util.Set<String> cols = m.columns("sailfact");
        String x = Sql.date10("x", dateCol);
        String inner = "WHERE " + x + ">=" + lit(prevStart) + " AND " + x + "<=" + lit(today)
                + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe("sailfact", numberCol, "h", inner);
        String d = Sql.date10("h", dateCol);
        String amt = Sql.num("h", amount);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + custName(m, "c") + " AS name"
                + ", t.k AS code, t.prevTotal AS prevTotal, t.curTotal AS curTotal"
                + ", CAST(100.0*(t.prevTotal-t.curTotal)/NULLIF(t.prevTotal,0) AS decimal(9,1)) AS dropPct"
                + " FROM (SELECT TRY_CONVERT(nvarchar(60),h.[" + shmo + "]) AS k"
                + ", ISNULL(SUM(CASE WHEN " + d + ">=" + lit(prevStart) + " AND " + d + "<=" + lit(prevEnd)
                + " THEN " + amt + " ELSE 0 END),0) AS prevTotal"
                + ", ISNULL(SUM(CASE WHEN " + d + ">=" + lit(curStart) + " THEN " + amt + " ELSE 0 END),0) AS curTotal"
                + " FROM " + src + " GROUP BY TRY_CONVERT(nvarchar(60),h.[" + shmo + "])) t"
                + " LEFT JOIN dbo.CUSTOMERS c ON TRY_CONVERT(nvarchar(60),c.[" + m.must("CUSTOMERS", "کد", "SHMO", "shmo")
                + "])=t.k"
                + " WHERE t.prevTotal>0 AND t.curTotal<0.5*t.prevTotal"
                + " ORDER BY dropPct DESC", new ArrayList<>());
    }

    /**
     * Customer financial record taken from the system's own view VW_CustomerOperatingRecord
     * (lifetime values, not date-filtered by the view): balance, credit, invoice total, gain
     * and receipts by method. Values are read as the database computes them, never recomputed.
     */
    public static Queries.Q customerRecord(Meta m, int top) throws Queries.Missing {
        String v = "VW_CustomerOperatingRecord";
        if (!m.table(v)) throw new Queries.Missing("نمای کارنامه مشتری در این دیتابیس نیست");
        String shmo = m.must(v, "کد", "SHMO", "shmo");
        String name = m.must(v, "نام", "moname", "MONAME");
        String mandeh = m.must(v, "مانده", "Mandeh", "mandeh");
        String etebar = m.col(v, "Etebar", "etebar");
        String sum = m.col(v, "Sum_facttor", "sum_facttor");
        String cnt = m.col(v, "Ted_facttor", "ted_facttor");
        String sood = m.col(v, "sood", "Sood");
        String naghdi = m.col(v, "Mab_Daryafti_Naghdi");
        String pos = m.col(v, "Mab_Daryafti_POS");
        String chk = m.col(v, "Mab_Daryafti_Chek");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("v", name, 200) + " AS name"
                + ", " + Sql.txt("v", shmo, 60) + " AS code"
                + ", " + Sql.num("v", mandeh) + " AS balance"
                + ", " + Sql.num("v", etebar) + " AS credit"
                + ", " + Sql.num("v", cnt) + " AS invoices"
                + ", " + Sql.num("v", sum) + " AS sales"
                + ", " + Sql.num("v", sood) + " AS gain"
                + ", " + Sql.num("v", naghdi) + " AS cash"
                + ", " + Sql.num("v", pos) + " AS pos"
                + ", " + Sql.num("v", chk) + " AS cheque"
                + " FROM dbo.[" + v + "] v ORDER BY balance DESC", new ArrayList<>());
    }
}
