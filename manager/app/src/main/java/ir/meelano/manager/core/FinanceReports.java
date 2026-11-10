package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Receipts/payments and chart-of-accounts (سرفصل) reports. Joins follow the foreign keys
 * in the Atiran sample schema:
 *   act_zirsarfasls.rdf_zirsarfasls -> zirsarfasls.rdf
 *   zirsarfasls.rdf_sarfasl        -> sarfasls.rdf
 *   sarfasls.GroupSarfaslID        -> GroupSarfasl.GroupSarfaslID
 * Receipts/payments use dar (p = 0 receipt, p = 1 payment; mab = voucher total;
 * naghd = cash; mabcheck = cheques; TakhfifDaramad / TakhfifHazineh = discounts).
 * Debit = bed, credit = bes.
 */
public final class FinanceReports {
    private FinanceReports() { }

    private static String sumIf(String cond, String expr) {
        return "ISNULL(SUM(CASE WHEN " + cond + " THEN " + expr + " ELSE 0 END),0)";
    }

    // =====================================================================================
    // RECEIPTS / PAYMENTS
    // =====================================================================================

    /** Monthly receipts/payments with the discounts granted on each: month,receipts,payments,discountIn,discountOut. */
    public static Queries.Q discountMonthly(Meta m, Filter f) throws Queries.Missing {
        String dateCol = m.must("dar", "تاریخ", "date", "DATE");
        String p = m.must("dar", "نوع", "p");
        String mab = m.must("dar", "مبلغ", "mab", "Mab", "MAB");
        String tdIn = m.col("dar", "TakhfifDaramad");
        String tdOut = m.col("dar", "TakhfifHazineh");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String where = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(m.columns("dar"), "h");
        String month = Sql.month7("h", dateCol);
        String rcv = "TRY_CONVERT(int,h.[" + p + "])=0";
        String pay = "TRY_CONVERT(int,h.[" + p + "])=1";
        return new Queries.Q("SELECT " + month + " AS month"
                + ", " + sumIf(rcv, Sql.num("h", mab)) + " AS receipts"
                + ", " + sumIf(pay, Sql.num("h", mab)) + " AS payments"
                + ", " + (tdIn == null ? "CAST(0 AS decimal(19,2))" : "ISNULL(SUM(" + Sql.num("h", tdIn) + "),0)") + " AS discountIn"
                + ", " + (tdOut == null ? "CAST(0 AS decimal(19,2))" : "ISNULL(SUM(" + Sql.num("h", tdOut) + "),0)") + " AS discountOut"
                + " FROM dbo.dar h " + where + " GROUP BY " + month + " ORDER BY 1", binds);
    }

    /** Net cash flow per month with a running balance: month,receipts,payments,net,cum. */
    public static Queries.Q netCashMonthly(Meta m, Filter f) throws Queries.Missing {
        String dateCol = m.must("dar", "تاریخ", "date", "DATE");
        String p = m.must("dar", "نوع", "p");
        String mab = m.must("dar", "مبلغ", "mab", "Mab", "MAB");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String where = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(m.columns("dar"), "h");
        String month = Sql.month7("h", dateCol);
        String inner = "SELECT " + month + " AS month"
                + ", " + sumIf("TRY_CONVERT(int,h.[" + p + "])=0", Sql.num("h", mab)) + " AS receipts"
                + ", " + sumIf("TRY_CONVERT(int,h.[" + p + "])=1", Sql.num("h", mab)) + " AS payments"
                + " FROM dbo.dar h " + where + " GROUP BY " + month;
        return new Queries.Q("SELECT t.month AS month, t.receipts AS receipts, t.payments AS payments"
                + ", (t.receipts-t.payments) AS net"
                + ", SUM(t.receipts-t.payments) OVER (ORDER BY t.month ROWS UNBOUNDED PRECEDING) AS cum"
                + " FROM (" + inner + ") t ORDER BY t.month", binds);
    }

    /** Cash versus cheque mix of receipts per month: month,cash,cheque,cashShare(%). */
    public static Queries.Q cashMixMonthly(Meta m, Filter f) throws Queries.Missing {
        String dateCol = m.must("dar", "تاریخ", "date", "DATE");
        String p = m.must("dar", "نوع", "p");
        String naghd = m.must("dar", "نقد", "naghd");
        String chk = m.must("dar", "چک", "mabcheck");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String where = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + " AND TRY_CONVERT(int,h.[" + p + "])=0" + Sql.activeAnd(m.columns("dar"), "h");
        String month = Sql.month7("h", dateCol);
        String cash = "ISNULL(SUM(" + Sql.num("h", naghd) + "),0)";
        String cheq = "ISNULL(SUM(" + Sql.num("h", chk) + "),0)";
        return new Queries.Q("SELECT " + month + " AS month, " + cash + " AS cash, " + cheq + " AS cheque"
                + ", CAST(100.0*" + cash + "/NULLIF(" + cash + "+" + cheq + ",0) AS decimal(9,1)) AS cashShare"
                + " FROM dbo.dar h " + where + " GROUP BY " + month + " ORDER BY 1", binds);
    }

    /**
     * Receipts whose cash plus cheque part is larger than the voucher total. POS can only add
     * to the total, so a cash+cheque excess is always an inconsistent voucher.
     */
    public static Queries.Q overpaidVouchers(Meta m, Filter f, int top) throws Queries.Missing {
        String dateCol = m.must("dar", "تاریخ", "date", "DATE");
        String gh = m.must("dar", "شماره قبض", "ghno", "GHNO");
        String p = m.must("dar", "نوع", "p");
        String mab = m.must("dar", "مبلغ", "mab", "Mab", "MAB");
        String naghd = m.must("dar", "نقد", "naghd");
        String chk = m.must("dar", "چک", "mabcheck");
        String shmo = m.col("dar", "shmo", "SHMO");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String where = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(m.columns("dar"), "h");
        String paid = "(" + Sql.num("h", naghd) + "+" + Sql.num("h", chk) + ")";
        String party = shmo == null ? "CAST(NULL AS nvarchar(200))"
                : "COALESCE(" + Sql.txt("c", m.col("CUSTOMERS", "MONAME", "Name"), 200) + ",N'—')";
        String join = shmo == null ? "" : " LEFT JOIN dbo.CUSTOMERS c ON TRY_CONVERT(nvarchar(60),c.[SHMO])=TRY_CONVERT(nvarchar(60),h.[" + shmo + "])";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("h", gh, 60) + " AS no"
                + ", CASE WHEN TRY_CONVERT(int,h.[" + p + "])=0 THEN N'دریافت' ELSE N'پرداخت' END AS kind"
                + ", " + party + " AS party"
                + ", " + Sql.date10("h", dateCol) + " AS date"
                + ", " + Sql.num("h", mab) + " AS total"
                + ", " + paid + " AS paid"
                + ", " + paid + "-" + Sql.num("h", mab) + " AS excess"
                + " FROM dbo.dar h" + join + " " + where
                + " AND " + paid + ">" + Sql.num("h", mab) + "+0.5"
                + " ORDER BY excess DESC", binds);
    }

    // =====================================================================================
    // CHART OF ACCOUNTS (سرفصل)
    // =====================================================================================

    private static final String ACT_FROM = " FROM dbo.act_zirsarfasls a"
            + " JOIN dbo.zirsarfasls z ON TRY_CONVERT(nvarchar(60),z.[rdf])=TRY_CONVERT(nvarchar(60),a.[rdf_zirsarfasls])"
            + " JOIN dbo.sarfasls s ON TRY_CONVERT(nvarchar(60),s.[rdf])=TRY_CONVERT(nvarchar(60),z.[rdf_sarfasl])"
            + " JOIN dbo.GroupSarfasl g ON TRY_CONVERT(nvarchar(60),g.[GroupSarfaslID])=TRY_CONVERT(nvarchar(60),s.[GroupSarfaslID])";

    private static List<Object> actDateBinds(Meta m, Filter f, List<String> conds, String dateCol) throws Queries.Missing {
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("a", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        return binds;
    }

    /** Debit, credit and balance per chart group: group,debit,credit,balance,rows. */
    public static Queries.Q groupTotals(Meta m, Filter f) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String bed = m.must("act_zirsarfasls", "بدهکار", "bed");
        String bes = m.must("act_zirsarfasls", "بستانکار", "bes");
        List<String> conds = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, conds, dateCol);
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String gname = m.must("GroupSarfasl", "نام گروه", "GroupSarfaslName");
        return new Queries.Q("SELECT " + Sql.txt("g", gname, 200) + " AS grp"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0) AS debit"
                + ", ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS credit"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0)-ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS balance"
                + ", COUNT_BIG(1) AS rows"
                + ACT_FROM + where + " GROUP BY " + Sql.txt("g", gname, 200) + " ORDER BY 2 DESC", binds);
    }

    /** Monthly debit and credit per chart group: month,grp,debit,credit,net. */
    public static Queries.Q monthlyByGroup(Meta m, Filter f) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String bed = m.must("act_zirsarfasls", "بدهکار", "bed");
        String bes = m.must("act_zirsarfasls", "بستانکار", "bes");
        List<String> conds = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, conds, dateCol);
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String gname = m.must("GroupSarfasl", "نام گروه", "GroupSarfaslName");
        String month = Sql.month7("a", dateCol);
        return new Queries.Q("SELECT " + month + " AS month, " + Sql.txt("g", gname, 200) + " AS grp"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0) AS debit"
                + ", ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS credit"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0)-ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS net"
                + ACT_FROM + where + " GROUP BY " + month + ", " + Sql.txt("g", gname, 200) + " ORDER BY 1, 2", binds);
    }

    /** Movements hidden from the standard report (ShowInReport = 0), per sub-account: zir,rows,debit,credit. */
    public static Queries.Q hiddenMovements(Meta m, Filter f, int top) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String bed = m.must("act_zirsarfasls", "بدهکار", "bed");
        String bes = m.must("act_zirsarfasls", "بستانکار", "bes");
        String show = m.must("act_zirsarfasls", "نمایش در گزارش", "ShowInReport");
        String zname = m.must("zirsarfasls", "نام زیرسرفصل", "name");
        List<String> conds = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, conds, dateCol);
        conds.add("TRY_CONVERT(int,a.[" + show + "])=0");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("z", zname, 200) + " AS zir"
                + ", COUNT_BIG(1) AS rows"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0) AS debit"
                + ", ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS credit"
                + ACT_FROM + " WHERE " + Sql.join(conds, " AND ")
                + " GROUP BY " + Sql.txt("z", zname, 200) + " ORDER BY 3 DESC", binds);
    }

    /** Active sub-accounts that had no movement in the period: zir,sarfasl,grp. */
    public static Queries.Q dormantSubAccounts(Meta m, Filter f, int top) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String zname = m.must("zirsarfasls", "نام زیرسرفصل", "name");
        String active = m.col("zirsarfasls", "Active");
        String sname = m.must("sarfasls", "نام سرفصل", "name");
        String gname = m.must("GroupSarfasl", "نام گروه", "GroupSarfaslName");
        List<String> inner = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, inner, dateCol);
        String innerWhere = (inner.isEmpty() ? "1=1" : Sql.join(inner, " AND "))
                .replace("a.", "a2.");
        // The EXISTS check reuses the same date window, written against a2.
        List<String> conds = new ArrayList<>();
        conds.add(active == null ? "1=1" : "TRY_CONVERT(int,z.[" + active + "])=1");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("z", zname, 200) + " AS zir"
                + ", " + Sql.txt("s", sname, 200) + " AS sarfasl"
                + ", " + Sql.txt("g", gname, 200) + " AS grp"
                + " FROM dbo.zirsarfasls z"
                + " JOIN dbo.sarfasls s ON TRY_CONVERT(nvarchar(60),s.[rdf])=TRY_CONVERT(nvarchar(60),z.[rdf_sarfasl])"
                + " JOIN dbo.GroupSarfasl g ON TRY_CONVERT(nvarchar(60),g.[GroupSarfaslID])=TRY_CONVERT(nvarchar(60),s.[GroupSarfaslID])"
                + " WHERE " + Sql.join(conds, " AND ")
                + " AND NOT EXISTS (SELECT 1 FROM dbo.act_zirsarfasls a2 WHERE TRY_CONVERT(nvarchar(60),a2.[rdf_zirsarfasls])"
                + "=TRY_CONVERT(nvarchar(60),z.[rdf])"
                + (innerWhere.equals("1=1") ? "" : " AND " + innerWhere)
                + ")"
                + " ORDER BY 1", binds);
    }

    /** Journal documents whose debit and credit do not match: sanad,date,rows,debit,credit,diff. */
    public static Queries.Q unbalancedJournals(Meta m, Filter f, int top) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String sanad = m.must("act_zirsarfasls", "شماره سند", "sanadno");
        String bed = m.must("act_zirsarfasls", "بدهکار", "bed");
        String bes = m.must("act_zirsarfasls", "بستانکار", "bes");
        List<String> conds = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, conds, dateCol);
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String dSum = "ISNULL(SUM(" + Sql.num("a", bed) + "),0)";
        String cSum = "ISNULL(SUM(" + Sql.num("a", bes) + "),0)";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("a", sanad, 40) + " AS sanad"
                + ", MIN(" + Sql.date10("a", dateCol) + ") AS date"
                + ", COUNT_BIG(1) AS rows"
                + ", " + dSum + " AS debit, " + cSum + " AS credit"
                + ", " + dSum + "-" + cSum + " AS diff"
                + " FROM dbo.act_zirsarfasls a" + where
                + " GROUP BY " + Sql.txt("a", sanad, 40)
                + " HAVING ABS(" + dSum + "-" + cSum + ")>=1 ORDER BY ABS(" + dSum + "-" + cSum + ") DESC", binds);
    }

    /** Largest journal documents by debit: sanad,date,rows,debit,credit. */
    public static Queries.Q biggestJournals(Meta m, Filter f, int top) throws Queries.Missing {
        String dateCol = m.must("act_zirsarfasls", "تاریخ", "date");
        String sanad = m.must("act_zirsarfasls", "شماره سند", "sanadno");
        String bed = m.must("act_zirsarfasls", "بدهکار", "bed");
        String bes = m.must("act_zirsarfasls", "بستانکار", "bes");
        List<String> conds = new ArrayList<>();
        List<Object> binds = actDateBinds(m, f, conds, dateCol);
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("a", sanad, 40) + " AS sanad"
                + ", MIN(" + Sql.date10("a", dateCol) + ") AS date"
                + ", COUNT_BIG(1) AS rows"
                + ", ISNULL(SUM(" + Sql.num("a", bed) + "),0) AS debit"
                + ", ISNULL(SUM(" + Sql.num("a", bes) + "),0) AS credit"
                + " FROM dbo.act_zirsarfasls a" + where
                + " GROUP BY " + Sql.txt("a", sanad, 40) + " ORDER BY 4 DESC", binds);
    }
}
