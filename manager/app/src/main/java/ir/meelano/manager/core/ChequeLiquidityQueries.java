package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;

/**
 * کوئری‌های تامین چک: چک‌های پرداختیِ جاری، پوز در انتظار تسویه، مانده مشتریان معوق و الگوی رفتاری.
 */
public final class ChequeLiquidityQueries {
    private ChequeLiquidityQueries() {}

    // ---------------------------------------------------------------
    // Banks
    // ---------------------------------------------------------------

    /** فهرست حساب‌های بانکی: id (RDF), name */
    public static Queries.Q banks(Meta m) throws Queries.Missing {
        return MasterQueries.lookupBanks(m);
    }

    // ---------------------------------------------------------------
    // Putchk — outstanding (جاری) — grouped by bank
    // ---------------------------------------------------------------

    /** خلاصه چک‌های جاری به تفکیک بانک: bankRdf, bank, count, total */
    public static Queries.Q putchkOutstandingByBank(Meta m) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, false); // putchk
        String bankRdf = c.bank; // bankrdf
        if (bankRdf == null) throw new Queries.Missing("ستون بانک در «چک‌های پرداختی» پیدا نشد");
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        String label = bankKey != null && bankName != null && m.table("BANK")
                ? "COALESCE(" + Sql.txt("b", bankName, 150) + "," + Sql.txt("h", bankRdf, 80) + ",N'—')"
                : "COALESCE(" + Sql.txt("h", bankRdf, 80) + ",N'—')";
        String join = bankKey != null && m.table("BANK")
                ? " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),h.[" + bankRdf + "])" : "";

        // Outstanding = putchk_status = '1' (جاری)
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        String where = " WHERE " + st + "=N'1'";
        // Exclude placeholder dates? No need to filter; amount must be >0
        return new Queries.Q("SELECT " + Sql.txt("h", bankRdf, 80) + " AS bankRdf, " + label + " AS bank"
                + ", COUNT_BIG(1) AS count, ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM " + c.from + " h" + join + where
                + " GROUP BY " + Sql.txt("h", bankRdf, 80) + ", " + label
                + " ORDER BY total DESC", new ArrayList<>());
    }

    /** لیست چک‌های جاری یک بانک (یا همه بانک‌ها اگر bankRdf == null/empty): rdf, num, bankRdf, bank, amount, sardate, getdate, customer, girande, sayad, descrip, st */
    public static Queries.Q putchkOutstandingList(Meta m, String bankRdfFilter, String search, int page, int top) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, false);
        String bankRdf = c.bank;
        if (bankRdf == null) throw new Queries.Missing("ستون بانک در «چک‌های پرداختی» پیدا نشد");
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        boolean needCust = c.custName == null && c.shmo != null && custName != null;
        String customerCol = m.col("CUSTOMERS", "MONAME", "Name", "CusName");

        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();

        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        conds.add(st + "=N'1'");

        if (bankRdfFilter != null && !bankRdfFilter.trim().isEmpty()) {
            conds.add("TRY_CONVERT(nvarchar(60),h.[" + bankRdf + "])=?");
            binds.add(bankRdfFilter.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            if (c.num != null) exprs.add(Sql.txt("h", c.num, 80));
            if (c.bank != null) exprs.add(Sql.txt("b", bankName != null ? bankName : c.bank, 150));
            if (c.sayad != null) exprs.add(Sql.txt("h", c.sayad, 60));
            if (c.custName != null) exprs.add(Sql.txt("h", c.custName, 250));
            else if (customerCol != null && c.shmo != null) exprs.add(Sql.txt("cu", customerCol, 250));
            String sc = Queries.searchCond(binds, search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }

        String join = "";
        if (bankKey != null && m.table("BANK"))
            join += " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),h.[" + bankRdf + "])";
        if (needCust)
            join += Queries.custJoin(m, "h", c.shmo, "cu");

        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        String codeExpr = c.custCode != null ? Sql.txt("h", c.custCode, 100)
                : (c.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", c.shmo, 100));

        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");

        // Sort by sardate (due date) ascending — closest first
        String orderKey = c.sardate == null ? "CAST(NULL AS bigint)" : "TRY_CONVERT(bigint,REPLACE(" + Sql.txt("h", c.sardate, 20) + ",N'/',N''))";
        // Use actual display helper for dates; ordering via sortKey helper
        String orderCol = c.sardate == null ? "h.[" + c.amount + "]" : orderKey;

        String sql = "SELECT " + (m.col(c.table, "rdf") == null ? "CAST(NULL AS nvarchar(60))" : Sql.txt("h", "rdf", 60)) + " AS rdf"
                + ", " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + Sql.txt("h", bankRdf, 80) + " AS bankRdf"
                + ", " + (bankName != null && bankKey != null ? "COALESCE(" + Sql.txt("b", bankName, 150) + ",N'—')" : "COALESCE(" + Sql.txt("h", bankRdf, 80) + ",N'—')") + " AS bank"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                // Keep raw sardate for sorting, and formatted disp for display
                + ", " + (c.sardate == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", c.sardate)) + " AS sardate"
                + ", " + (c.getdate == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", c.getdate)) + " AS getdate"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + ", " + codeExpr + " AS code, " + custExpr + " AS customer"
                + ", " + (c.branch == null ? "N''" : "COALESCE(" + Sql.txt("h", c.branch, 200) + ",N'')") + " AS girande"
                + ", " + (c.sayad == null ? "N''" : "COALESCE(" + Sql.txt("h", c.sayad, 60) + ",N'')") + " AS sayad"
                + ", " + (c.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", c.desc, 500) + ",N'')") + " AS descrip"
                + ", " + (c.ghno == null ? "N''" : "COALESCE(" + Sql.txt("h", c.ghno, 60) + ",N'')") + " AS ghno"
                + " FROM " + c.from + " h" + join + where
                + " ORDER BY " + orderCol + " ASC"
                + Queries.pageClause(binds, page, top);
        return new Queries.Q(sql, binds);
    }

    /** جمع چک‌های جاری یک بانک (برای محاسبه سریع بدون صفحه‌بندی). */
    public static Queries.Q putchkOutstandingTotalForBank(Meta m, String bankRdf) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, false);
        String colBank = c.bank;
        if (colBank == null) throw new Queries.Missing("ستون بانک در «چک‌های پرداختی» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        String where = " WHERE " + st + "=N'1'";
        if (bankRdf != null && !bankRdf.trim().isEmpty()) {
            where += " AND TRY_CONVERT(nvarchar(60),h.[" + colBank + "])=?";
            binds.add(bankRdf.trim());
        }
        return new Queries.Q("SELECT COUNT_BIG(1) AS count, ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM " + c.from + " h" + where, binds);
    }

    // ---------------------------------------------------------------
    // POS — pending (در انتظار واریز ۴ صبح فردا)
    // ---------------------------------------------------------------

    /**
     * پوزهای در انتظار تسویه برای یک بانک خاص.
     * مبنا: dar.date = امروز (شمسی) و PosDetails.IsHavaleh = false (کارت).
     * اگر ساعت فعلی قبل از ۴ صبح باشد، پوزهای دیروز هنوز تسویه نشده‌اند — هر دو روز را نشان می‌دهیم.
     */
    public static Queries.Q posPendingForBank(Meta m, String bankRdf, String todayJalali, boolean includeYesterday) throws Queries.Missing {
        if (!m.table("PosDetails")) throw new Queries.Missing("«جزئیات کارت/حواله» در دیتابیس پیدا نشد");
        String amount = m.must("PosDetails", "مبلغ", "MabPos", "mabPos", "mablagh");
        String bankRef = m.must("PosDetails", "بانک", "PosBankRdf", "BankRdf", "bankrdf");
        String gh = m.col("PosDetails", "ghno", "GHNO");
        String pp = m.col("PosDetails", "p");
        String hav = m.col("PosDetails", "IsHavaleh", "isHavaleh");
        String darDate = m.col("dar", "date", "DATE");
        String darGh = m.col("dar", "ghno", "GHNO");
        String darP = m.col("dar", "p");
        String darRdf = m.col("dar", "Rdf_");
        if (gh == null || pp == null || darDate == null || darGh == null || darP == null)
            throw new Queries.Missing("ارتباط پوز با قبض قابل تشخیص نیست");

        List<Object> binds = new ArrayList<>();
        // Filter by bank
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(60),d.[" + bankRef + "])=?");
        binds.add(bankRdf);

        // Only card (IsHavaleh = 0/false)
        if (hav != null) {
            String flag = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(10),d.[" + hav + "]),N''))))";
            conds.add("(" + flag + " IN (N'0',N'F',N'FALSE',N'N',N'NO') OR " + flag + " = N'')");
        }

        // Latest Rdf_ per (ghno,p,IsHavaleh) — like posLatestVersionCondition
        String rdf = m.col("PosDetails", "Rdf_", "rdf_");
        if (rdf != null && gh != null && pp != null && hav != null) {
            String flagVal = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(20),d.[" + hav + "]),N''))))";
            String flagVal2 = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(20),pd2.[" + hav + "]),N''))))";
            String latest = "TRY_CONVERT(bigint,d.[" + rdf + "])=(SELECT MAX(TRY_CONVERT(bigint,pd2.[" + rdf + "])) FROM dbo.PosDetails pd2 WHERE TRY_CONVERT(nvarchar(60),pd2.[" + gh + "])=TRY_CONVERT(nvarchar(60),d.[" + gh + "]) AND TRY_CONVERT(int,pd2.[" + pp + "])=TRY_CONVERT(int,d.[" + pp + "]) AND " + flagVal2 + "=" + flagVal + ")";
            conds.add(latest);
        }

        // Date filter via dar
        String dateExpr = Sql.date10("dv", "vdate");
        if (includeYesterday) {
            String yesterday = Jalali.addDays(todayJalali, -1);
            // (vdate = today OR vdate = yesterday)
            String todayCond = "(" + Sql.dateKey(dateExpr) + "=?)";
            binds.add(todayJalali);
            String yCond = "(" + Sql.dateKey(dateExpr) + "=?)";
            binds.add(yesterday);
            conds.add("(" + todayCond + " OR " + yCond + ")");
        } else {
            conds.add("(" + Sql.dateKey(dateExpr) + "=?)");
            binds.add(todayJalali);
        }

        // dar apply: get voucher date
        String orderByRdf = darRdf == null ? "(SELECT NULL)" : "TRY_CONVERT(bigint,dh.[" + darRdf + "]) DESC";
        String darApply = " OUTER APPLY (SELECT TOP (1) dh.[" + darDate + "] AS vdate FROM dbo.dar dh"
                + " WHERE TRY_CONVERT(nvarchar(60),dh.[" + darGh + "])=TRY_CONVERT(nvarchar(60),d.[" + gh + "])"
                + " AND TRY_CONVERT(int,dh.[" + darP + "])=TRY_CONVERT(int,d.[" + pp + "])"
                + " ORDER BY " + orderByRdf + ") dv";

        // Customer name for display
        String shmo = m.col("dar", "shmo", "SHMO");
        String custJoin = "";
        String custExpr = "N'—'";
        if (shmo != null) {
            custJoin = " LEFT JOIN dbo.dar dh2 ON TRY_CONVERT(nvarchar(60),dh2.[" + darGh + "])=TRY_CONVERT(nvarchar(60),d.[" + gh + "]) AND TRY_CONVERT(int,dh2.[" + darP + "])=TRY_CONVERT(int,d.[" + pp + "])";
            // Use subquery for customer? Simpler: keep N'—' and fill via separate query for list
        }

        // Aggregate
        return new Queries.Q("SELECT ISNULL(SUM(" + Sql.num("d", amount) + "),0) AS total, COUNT_BIG(1) AS count"
                + " FROM dbo.PosDetails d" + darApply + " WHERE " + Sql.join(conds, " AND "), binds);
    }

    /** لیست جزئی پوزهای در انتظار برای نمایش: amount, bankRef, ghno, date, shmo/customer */
    public static Queries.Q posPendingListForBank(Meta m, String bankRdf, String todayJalali, boolean includeYesterday) throws Queries.Missing {
        if (!m.table("PosDetails")) throw new Queries.Missing("«جزئیات کارت/حواله» در دیتابیس پیدا نشد");
        String amount = m.must("PosDetails", "مبلغ", "MabPos", "mabPos", "mablagh");
        String bankRef = m.must("PosDetails", "بانک", "PosBankRdf", "BankRdf", "bankrdf");
        String gh = m.col("PosDetails", "ghno", "GHNO");
        String pp = m.col("PosDetails", "p");
        String hav = m.col("PosDetails", "IsHavaleh", "isHavaleh");
        String desc = m.col("PosDetails", "PosDesc", "posDesc", "description");
        String track = m.col("PosDetails", "ShPeigiri", "shPeigiri", "tracking");
        String darDate = m.col("dar", "date", "DATE");
        String darGh = m.col("dar", "ghno", "GHNO");
        String darP = m.col("dar", "p");
        String darRdf = m.col("dar", "Rdf_");
        String darShmo = m.col("dar", "shmo", "SHMO");
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String custKey = m.col("CUSTOMERS", "SHMO", "shmo");

        if (gh == null || pp == null || darDate == null) throw new Queries.Missing("ارتباط پوز با قبض قابل تشخیص نیست");

        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(60),d.[" + bankRef + "])=?");
        binds.add(bankRdf);
        if (hav != null) {
            String flag = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(10),d.[" + hav + "]),N''))))";
            conds.add("(" + flag + " IN (N'0',N'F',N'FALSE',N'N',N'NO') OR " + flag + " = N'')");
        }
        String rdf = m.col("PosDetails", "Rdf_", "rdf_");
        if (rdf != null && gh != null && pp != null && hav != null) {
            String flagVal = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(20),d.[" + hav + "]),N''))))";
            String flagVal2 = "UPPER(LTRIM(RTRIM(COALESCE(TRY_CONVERT(nvarchar(20),pd2.[" + hav + "]),N''))))";
            String latest = "TRY_CONVERT(bigint,d.[" + rdf + "])=(SELECT MAX(TRY_CONVERT(bigint,pd2.[" + rdf + "])) FROM dbo.PosDetails pd2 WHERE TRY_CONVERT(nvarchar(60),pd2.[" + gh + "])=TRY_CONVERT(nvarchar(60),d.[" + gh + "]) AND TRY_CONVERT(int,pd2.[" + pp + "])=TRY_CONVERT(int,d.[" + pp + "]) AND " + flagVal2 + "=" + flagVal + ")";
            conds.add(latest);
        }

        String orderByRdf = darRdf == null ? "(SELECT NULL)" : "TRY_CONVERT(bigint,dh.[" + darRdf + "]) DESC";
        String darApply = " OUTER APPLY (SELECT TOP (1) dh.[" + darDate + "] AS vdate, dh.[" + (darShmo != null ? darShmo : darGh) + "] AS vshmo FROM dbo.dar dh"
                + " WHERE TRY_CONVERT(nvarchar(60),dh.[" + darGh + "])=TRY_CONVERT(nvarchar(60),d.[" + gh + "])"
                + " AND TRY_CONVERT(int,dh.[" + darP + "])=TRY_CONVERT(int,d.[" + pp + "])"
                + " ORDER BY " + orderByRdf + ") dv";

        String darApplyExpr = Sql.date10("dv", "vdate");
        if (includeYesterday) {
            String yesterday = Jalali.addDays(todayJalali, -1);
            binds.add(todayJalali);
            binds.add(yesterday);
            conds.add("(" + Sql.dateKey(darApplyExpr) + "=? OR " + Sql.dateKey(darApplyExpr) + "=?)");
        } else {
            binds.add(todayJalali);
            conds.add(Sql.dateKey(darApplyExpr) + "=?");
        }

        String join = "";
        if (custKey != null && custName != null) {
            join = " LEFT JOIN dbo.CUSTOMERS cu ON TRY_CONVERT(nvarchar(100),cu.[" + custKey + "])=TRY_CONVERT(nvarchar(100),dv.vshmo)";
        }

        String custExpr = custName != null ? "COALESCE(" + Sql.txt("cu", custName, 250) + ",N'—')" : "N'—'";

        String sql = "SELECT " + Sql.num("d", amount) + " AS amount, " + Sql.txt("d", gh, 60) + " AS ghno, "
                + darApplyExpr + " AS vdate, "
                + (desc == null ? "N''" : "COALESCE(" + Sql.txt("d", desc, 400) + ",N'')") + " AS descrip, "
                + (track == null ? "N''" : "COALESCE(" + Sql.txt("d", track, 120) + ",N'')") + " AS tracking, "
                + custExpr + " AS customer"
                + " FROM dbo.PosDetails d" + darApply + join
                + " WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY " + Sql.num("d", amount) + " DESC";
        return new Queries.Q(sql, binds);
    }

    // ---------------------------------------------------------------
    // Overdue customers — for collection suggestions
    // ---------------------------------------------------------------

    /** فاکتورهای معوق با ماندهٔ قابل وصول — برای پیشنهاد تامین از محل وصول */
    public static Queries.Q overdueForLiquidity(Meta m, int top) throws Queries.Missing {
        // Use existing overdue logic with more columns: code, party, amount, dueDate, days, visitor, invoice
        return Queries.overdueInvoices(m, top);
    }

    /**
     * سابقه پرداختی یک مشتری: فاکتورهای تسویه‌شده + تاریخ سررسید + تاریخ واریز (از DaryaftMultiFactor+dar).
     * ستون‌ها: invoice, dueDate, payDate, amount, delayDays (payDate - dueDate)
     * فقط فاکتورهایی که از طریق DaryaftMultiFactor تسویه شده‌اند.
     */
    public static Queries.Q customerPaymentHistory(Meta m, String shmoVal, int top) throws Queries.Missing {
        String shmoCol = m.must("sailfact", "کد مشتری", "shmo", "SHMO");
        String sNo = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String sDate = m.col("sailfact", "date");
        String sDue = m.col("sailfact", "t_date");
        String sTas = m.col("sailfact", "tasvieh");
        String sAmount = m.col("sailfact", "all", "all_fel");
        String mfTable = m.qTable("DaryaftMultiFactor");
        String mfGh = m.col("DaryaftMultiFactor", "GhnoDar", "ghnoDar", "ghno");
        String mfNo = m.col("DaryaftMultiFactor", "Shfacfo", "shfacfo", "shfackh", "shfac");
        String darGh = m.col("dar", "ghno", "GHNO");
        String darDate = m.col("dar", "date", "DATE");
        String darP = m.col("dar", "p");
        if (mfTable == null || mfGh == null || mfNo == null || darGh == null || darDate == null)
            throw new Queries.Missing("ارتباط فاکتور با واریز برای تحلیل رفتار پرداخت قابل تشخیص نیست");

        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        // Only settled invoices with a due date
        String sql = "SELECT TOP (" + Queries.clampTop(top) + ") "
                + Sql.txt("s", sNo, 80) + " AS invoice, "
                + Sql.date10("s", sDue) + " AS dueDate, "
                + Sql.date10("d", darDate) + " AS payDate, "
                + Sql.num("mf", m.col("DaryaftMultiFactor", "Price", "price")) + " AS paid, "
                + Sql.num("s", sAmount) + " AS total"
                + " FROM dbo.sailfact s"
                + " JOIN dbo.DaryaftMultiFactor mf ON TRY_CONVERT(nvarchar(80),mf.[" + mfNo + "])=TRY_CONVERT(nvarchar(80),s.[" + sNo + "])"
                + " JOIN dbo.dar d ON TRY_CONVERT(nvarchar(60),d.[" + darGh + "])=TRY_CONVERT(nvarchar(60),mf.[" + mfGh + "]) AND TRY_CONVERT(int,d.[" + darP + "])=0"
                + " WHERE TRY_CONVERT(nvarchar(100),s.[" + shmoCol + "])=? AND s.[tasvieh]='t' AND NULLIF(s.[" + sDue + "],'') IS NOT NULL"
                + Sql.activeAnd(m.columns("sailfact"), "s")
                + " ORDER BY s.[" + sDate + "] DESC";
        return new Queries.Q(sql, binds);
    }

    /** مانده و اعتبار یک مشتری (برای تشخیص «مانده به‌دردبخور») */
    public static Queries.Q customerBalance(Meta m, String shmoVal) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String moname = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String man = m.col("CUSTOMERS", "man", "Balance", "Mandeh");
        String cred = m.col("CUSTOMERS", "cred", "Credit", "Etebar");
        String cell = m.col("CUSTOMERS", "cell", "Mobile", "mobile");
        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        String sql = "SELECT TOP (1) " + Sql.txt("c", shmo, 100) + " AS code, "
                + (moname == null ? "N'—'" : "COALESCE(" + Sql.txt("c", moname, 250) + ",N'—')") + " AS name, "
                + (man == null ? "CAST(0 AS decimal(19,2))" : Sql.num("c", man)) + " AS balance, "
                + (cred == null ? "CAST(0 AS decimal(19,2))" : Sql.num("c", cred)) + " AS credit, "
                + (cell == null ? "N''" : "COALESCE(" + Sql.txt("c", cell, 60) + ",N'')") + " AS cell"
                + " FROM dbo.CUSTOMERS c WHERE TRY_CONVERT(nvarchar(100),c.[" + shmo + "])=?";
        return new Queries.Q(sql, binds);
    }
}
