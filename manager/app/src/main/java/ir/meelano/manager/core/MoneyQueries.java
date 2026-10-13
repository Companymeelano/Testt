package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Receipts (dar p=0), payments (dar p=1) and cheques (getchk / putchk).
 * See {@link Queries} for the shared builders.
 */
public final class MoneyQueries {
    private MoneyQueries() { }

    // =====================================================================================
    // dar — receipts & payments. PK(ghno, p, Rdf_).
    // =====================================================================================
    private static final class Dar {
        String ghno, shmo, date, done, desc, cash, pos, havaleh, cheque, chkCount, shfac, kind, user, isFinal;
    }

    private static Dar darCols(Meta m) throws Queries.Missing {
        if (!m.table("dar")) throw new Queries.Missing("«قبوض دریافت و پرداخت» در دیتابیس پیدا نشد");
        Dar d = new Dar();
        d.ghno = m.must("dar", "شماره قبض", "ghno", "GHNO");
        d.shmo = m.col("dar", "shmo", "SHMO");
        d.date = m.must("dar", "تاریخ", "date", "DATE");
        d.done = m.col("dar", "donedate", "done_date", "DoneDate");
        d.desc = m.col("dar", "DarDesc", "darDesc", "description", "Explain");
        d.cash = m.col("dar", "MabNaghdi", "mabNaghdi", "cash", "naghd");
        d.pos = m.col("dar", "MabPos", "mabPos");
        d.havaleh = m.col("dar", "MabHavaleh", "mabHavaleh");
        d.cheque = m.col("dar", "SumMabcheck", "sumMabcheck", "MabCheck", "mabCheck");
        d.chkCount = m.col("dar", "TedadChk", "tedadChk");
        d.shfac = m.col("dar", "shfacfo", "shfac");
        d.kind = m.col("dar", "darDescriptionTypeID", "DescriptionReceiveId");
        d.user = m.col("dar", "user", "UserName", "username");
        d.isFinal = m.col("dar", "IsFinal", "isFinal");
        return d;
    }

    /**
     * Receipt / payment amount.
     *
     * Atiran's own Daryaft procedure stores the voucher total in dar.mab, and that is the
     * number Atiran itself shows. Summing the four components (cash + POS + transfer +
     * cheques) agrees with it for receipts written through this app, but receipts entered
     * directly in Atiran can also carry a discount (tafif) or OtherPrice, and then the sum
     * of components no longer matches the booked total. Prefer mab, fall back to the sum.
     */
    private static String darTotal(String a, Dar d, Meta m) {
        String parts = Sql.num(a, d.cash) + "+" + Sql.num(a, d.pos) + "+"
                + Sql.num(a, d.havaleh) + "+" + Sql.num(a, d.cheque);
        String mab = m.col("dar", "mab", "Mab", "MAB");
        if (mab == null) return "(" + parts + ")";
        String ref = (a == null || a.trim().isEmpty()) ? "[" + mab + "]" : a + ".[" + mab + "]";
        return "COALESCE(TRY_CONVERT(decimal(19,2)," + ref + "),(" + parts + "))";
    }

    /**
     * Reconcile the voucher's payment-method breakdown against Atiran's detail tables.
     *
     * Daryaft/Pardakht mark transfer rows with IsHavaleh and retain revisions in Rdf_.
     * Their cheque triggers write one getchk/putchk row per cheque. Child rows are the
     * reliable split when present. Legacy vouchers with no detail rows keep
     * their header values as a safe fallback. The booked total remains dar.mab.
     */
    private static final class DarParts {
        String cash, pos, havaleh, cheque, chkCount;
    }

    private static DarParts darParts(Meta m, Dar d, String a, int p) {
        DarParts x = new DarParts();
        x.cash = Sql.num(a, d.cash);
        // Atiran's Daryaft and Pardakht procedures both select the latest Rdf_ per IsHavaleh.
        boolean detailSide = p == 0 || p == 1;
        x.pos = detailSide ? posDetailSum(m, a, p, false, Sql.num(a, d.pos)) : Sql.num(a, d.pos);
        x.havaleh = detailSide ? posDetailSum(m, a, p, true, Sql.num(a, d.havaleh)) : Sql.num(a, d.havaleh);
        x.cheque = chequeDetailSum(m, a, p, Sql.num(a, d.cheque));
        x.chkCount = chequeDetailCount(m, a, p, d.chkCount == null
                ? "CAST(0 AS bigint)" : "COALESCE(TRY_CONVERT(bigint," + a + ".[" + d.chkCount + "]),0)");
        return x;
    }

    private static String posFlagValue(String alias, String flag) {
        return "UPPER(LTRIM(RTRIM(COALESCE(" + Sql.txt(alias, flag, 10) + ",N''))))";
    }

    private static String posHavalehFlag(String alias, String flag) {
        return posFlagValue(alias, flag) + " IN (N'1',N'T',N'TRUE',N'Y',N'YES')";
    }

    private static String posKindExpr(String alias, String flag) {
        if (flag == null) return "N'کارت/حواله'";
        String value = posFlagValue(alias, flag);
        return "CASE WHEN " + posHavalehFlag(alias, flag) + " THEN N'حواله'"
                + " WHEN " + value + " IN (N'0',N'F',N'FALSE',N'N',N'NO') THEN N'کارت'"
                + " ELSE N'نامشخص' END";
    }

    /** Atiran retains revisions in PosDetails; its procedures read MAX(Rdf_) per ghno/p/type. */
    private static String posLatestVersionCondition(Meta m, String alias, String gh, String pp, String flag) {
        String rdf = m.col("PosDetails", "Rdf_", "rdf_");
        if (rdf == null || gh == null || pp == null || flag == null) return "";
        return "TRY_CONVERT(bigint," + alias + ".[" + rdf + "])=(SELECT MAX(TRY_CONVERT(bigint,pd2.["
                + rdf + "])) FROM dbo.PosDetails pd2 WHERE TRY_CONVERT(nvarchar(60),pd2.[" + gh
                + "])=TRY_CONVERT(nvarchar(60)," + alias + ".[" + gh + "]) AND TRY_CONVERT(int,pd2.[" + pp
                + "])=TRY_CONVERT(int," + alias + ".[" + pp + "]) AND " + posFlagValue("pd2", flag)
                + "=" + posFlagValue(alias, flag) + ")";
    }

    private static String posDetailSum(Meta m, String parent, int p, boolean havaleh, String fallback) {
        if (!m.table("PosDetails")) return fallback;
        String gh = m.col("PosDetails", "ghno", "GHNO");
        String pp = m.col("PosDetails", "p");
        String amount = m.col("PosDetails", "MabPos", "mabPos", "mablagh");
        String flag = m.col("PosDetails", "IsHavaleh", "isHavaleh");
        // Without both the side and the discriminator, do not label mixed turnover as POS.
        if (gh == null || pp == null || amount == null || flag == null) return fallback;
        String side = posHavalehFlag("pd", flag);
        String test = havaleh ? side : "NOT (" + side + ")";
        String value = posFlagValue("pd", flag);
        String known = "(" + side + " OR " + value + " IN (N'0',N'F',N'FALSE',N'N',N'NO'))";
        String match = "TRY_CONVERT(nvarchar(60),pd.[" + gh + "])=TRY_CONVERT(nvarchar(60),"
                + parent + ".[ghno]) AND TRY_CONVERT(int,pd.[" + pp + "])=TRY_CONVERT(int," + parent + ".[p])";
        String latest = posLatestVersionCondition(m, "pd", gh, pp, flag);
        String latestWhere = latest.isEmpty() ? "" : " AND " + latest;
        String sum = "(SELECT SUM(CASE WHEN " + test + " THEN " + Sql.num("pd", amount)
                + " ELSE CAST(0 AS decimal(19,2)) END) FROM dbo.PosDetails pd WHERE " + match + latestWhere + ")";
        String unknown = "EXISTS(SELECT 1 FROM dbo.PosDetails pd WHERE " + match + latestWhere + " AND NOT " + known + ")";
        return "CASE WHEN " + unknown + " THEN " + fallback + " ELSE COALESCE(" + sum + "," + fallback + ") END";
    }

    private static Chq optionalVoucherCheques(Meta m, int p) {
        try { return chq(m, p == 0); }
        catch (Queries.Missing ignored) { return null; }
    }

    private static String chequeDetailSum(Meta m, String parent, int p, String fallback) {
        Chq c = optionalVoucherCheques(m, p);
        if (c == null || c.ghno == null || c.amount == null || c.from == null) return fallback;
        String match = "TRY_CONVERT(nvarchar(60),cq.[" + c.ghno + "])=TRY_CONVERT(nvarchar(60),"
                + parent + ".[ghno])";
        return "COALESCE((SELECT SUM(" + Sql.num("cq", c.amount) + ") FROM " + c.from + " cq WHERE "
                + match + ")," + fallback + ")";
    }

    private static String chequeDetailCount(Meta m, String parent, int p, String fallback) {
        Chq c = optionalVoucherCheques(m, p);
        if (c == null || c.ghno == null || c.from == null) return fallback;
        String match = "TRY_CONVERT(nvarchar(60),cq.[" + c.ghno + "])=TRY_CONVERT(nvarchar(60),"
                + parent + ".[ghno])";
        // SUM returns NULL for no linked rows, allowing the header count to remain the fallback.
        return "COALESCE((SELECT SUM(CONVERT(bigint,1)) FROM " + c.from + " cq WHERE " + match + ")," + fallback + ")";
    }

    private static String darKindExpr(Meta m, String a, Dar d) {
        if (d.kind == null || !m.table("darDescriptionType")) return "N''";
        String id = m.col("darDescriptionType", "rowId", "RowId", "ID", "id");
        String nm = m.colFlex("darDescriptionType", "name", "title", "description", "onvan");
        if (id == null || nm == null) return "N''";
        return "COALESCE((SELECT TOP (1) " + Sql.txt("k", nm, 120) + " FROM dbo.darDescriptionType k"
                + " WHERE TRY_CONVERT(nvarchar(50),k.[" + id + "])=TRY_CONVERT(nvarchar(50)," + a + ".[" + d.kind + "])),N'')";
    }

    /** Day block anchored on latest dar.date for p: d,total,cash,pos,havaleh,cheque,count. */
    public static Queries.Q darDay(Meta m, int p) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        String day = "";
        try (java.sql.PreparedStatement ps = m.connection().prepareStatement(
                "SELECT MAX(LEFT(TRY_CONVERT(nvarchar(30),[" + d.date + "]),10)) FROM dbo.dar WHERE [p]=?")) {
            ps.setInt(1, p);
            try (java.sql.ResultSet r = ps.executeQuery()) {
                if (r.next() && r.getString(1) != null) day = r.getString(1).trim();
            }
        } catch (Exception ignored) { }
        DarParts parts = darParts(m, d, "h", p);
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(day);
        String rows = darRows(d, m, parts, cols,
                "h.[p]=? AND " + Sql.dateKey(Sql.date10("h", d.date)) + "=?");
        String sql = "SELECT " + Sql.lit(day) + " AS d, ISNULL(SUM(x.tot),0) AS total"
                + ", ISNULL(SUM(x.cash),0) AS cash"
                + ", ISNULL(SUM(x.pos),0) AS pos"
                + ", ISNULL(SUM(x.havaleh),0) AS havaleh"
                + ", ISNULL(SUM(x.cheque),0) AS cheque"
                + ", COUNT_BIG(1) AS count FROM (" + rows + ") x";
        return new Queries.Q(sql, binds);
    }

    /**
     * One row per voucher. The correlated detail lookups (PosDetails, getchk/putchk)
     * are scalar subqueries; SQL Server rejects them inside SUM(). They therefore live
     * in this derived table and every aggregate runs in the outer query.
     */
    private static String darRows(Dar d, Meta m, DarParts parts, Set<String> cols, String where) {
        return "SELECT " + darTotal("h", d, m) + " AS tot"
                + ", " + parts.cash + " AS cash"
                + ", " + parts.pos + " AS pos"
                + ", " + parts.havaleh + " AS havaleh"
                + ", " + parts.cheque + " AS cheque"
                + " FROM dbo.dar h WHERE " + where
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h");
    }

    /** Day block for an explicit Atiran «YYYY/MM/DD» (morning report): d,total,count. */
    public static Queries.Q darOn(Meta m, int p, String day) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(day == null ? "" : day);
        String sql = "SELECT " + Sql.lit(day == null ? "" : day) + " AS d, ISNULL(SUM(" + darTotal("h", d, m) + "),0) AS total"
                + ", COUNT_BIG(1) AS count FROM dbo.dar h WHERE h.[p]=?"
                + " AND " + Sql.dateKey(Sql.date10("h", d.date)) + "=?"
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h");
        return new Queries.Q(sql, binds);
    }

    /** Currently bounced incoming cheques (all-time): count,total. Notify diffs the count. */
    public static Queries.Q bouncedTotal(Meta m) throws Queries.Missing {
        Chq c = chq(m, true);
        if (c.back == null) throw new Queries.Missing("ستون برگشتی در «چک‌های دریافتی» پیدا نشد");
        String back = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "]))))";
        return new Queries.Q("SELECT COUNT_BIG(1) AS count, ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM " + c.from + " h WHERE " + back + " IN (N'T',N'1',N'TRUE')",
                new ArrayList<>());
    }

    /** Per-day totals in a range: day,total,count. */
    public static Queries.Q darDaily(Meta m, int p, String from, String to) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        List<String> conds = new ArrayList<>();
        conds.add("h.[p]=?");
        String dc = Sql.dateCond(Sql.date10("h", d.date), from, to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String day = Sql.dateKey(Sql.date10("h", d.date));
        return new Queries.Q("SELECT " + day + " AS day, ISNULL(SUM(" + darTotal("h", d, m) + "),0) AS total"
                + ", COUNT_BIG(1) AS count FROM dbo.dar h WHERE " + Sql.join(conds, " AND ")
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h") + " GROUP BY " + day + " ORDER BY " + day, binds);
    }

    private static boolean posDetailsCanSplit(Meta m) {
        if (!m.table("PosDetails")) return false;
        return m.col("PosDetails", "ghno", "GHNO") != null
                && m.col("PosDetails", "p") != null
                && m.col("PosDetails", "MabPos", "mabPos", "mablagh") != null
                && m.col("PosDetails", "IsHavaleh", "isHavaleh") != null;
    }

    private static boolean chequeDetailsAvailable(Meta m, int p) {
        Chq c = optionalVoucherCheques(m, p);
        return c != null && c.ghno != null && c.amount != null && c.from != null;
    }

    /** True only when the database can supply all four payment components, not guessed zeros. */
    private static boolean darBreakdownAvailable(Meta m, Dar d, int p) {
        boolean cash = d.cash != null;
        boolean cardAndTransfer = (d.pos != null && d.havaleh != null) || posDetailsCanSplit(m);
        boolean cheque = d.cheque != null || chequeDetailsAvailable(m, p);
        return cash && cardAndTransfer && cheque;
    }

    /** Summary: total,cash,pos,havaleh,cheque,count,breakdownAvailable. */
    public static Queries.Q darSummary(Meta m, Filter f, int p) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        List<String> conds = new ArrayList<>();
        conds.add("h.[p]=?");
        String dc = Sql.dateCond(Sql.date10("h", d.date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        DarParts parts = darParts(m, d, "h", p);
        int breakdownAvailable = darBreakdownAvailable(m, d, p) ? 1 : 0;
        String rows = darRows(d, m, parts, cols, Sql.join(conds, " AND "));
        return new Queries.Q("SELECT ISNULL(SUM(x.tot),0) AS total"
                + ", ISNULL(SUM(x.cash),0) AS cash"
                + ", ISNULL(SUM(x.pos),0) AS pos"
                + ", ISNULL(SUM(x.havaleh),0) AS havaleh"
                + ", ISNULL(SUM(x.cheque),0) AS cheque"
                + ", CAST(" + breakdownAvailable + " AS int) AS breakdownAvailable"
                + ", COUNT_BIG(1) AS count FROM (" + rows + ") x", binds);
    }

    /** Voucher list: ghno,date,code,customer,total,cash,pos,havaleh,cheque,chkCount,desc,kind,linkNo,multiCount. */
    public static Queries.Q darList(Meta m, Filter f, int p) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        String multi = p == 0 ? "DaryaftMultiFactor" : "PardakhtMultiFactor";
        String multiGh = m.col(multi, "GhnoDar", "ghnoDar", "ghno");
        boolean hasMulti = multiGh != null && m.table(multi);
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        List<String> conds = new ArrayList<>();
        conds.add("h.[p]=?");
        String dc = Sql.dateCond(Sql.date10("h", d.date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(Sql.txt("h", d.ghno, 60));
            if (d.desc != null) exprs.add(Sql.txt("h", d.desc, 500));
            if (custName != null && d.shmo != null) exprs.add(Sql.txt("cu", custName, 250));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = " WHERE " + Sql.join(conds, " AND ");
        DarParts parts = darParts(m, d, "h", p);
        String sql = "SELECT " + Sql.txt("h", d.ghno, 60) + " AS ghno"
                + ", " + Sql.date10("h", d.date) + " AS date"
                + ", " + (d.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", d.shmo, 100)) + " AS code"
                + ", " + Queries.custNameExpr(m, "h", d.shmo, "cu") + " AS customer"
                + ", " + darTotal("h", d, m) + " AS total"
                + ", " + parts.cash + " AS cash, " + parts.pos + " AS pos"
                + ", " + parts.havaleh + " AS havaleh, " + parts.cheque + " AS cheque"
                + ", " + parts.chkCount + " AS chkCount"
                + ", " + (d.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", d.desc, 500) + ",N'')") + " AS descrip"
                + ", " + darKindExpr(m, "h", d) + " AS kind"
                + ", " + (d.shfac == null ? "CAST(NULL AS nvarchar(80))" : Sql.txt("h", d.shfac, 80)) + " AS linkNo"
                + ", " + (hasMulti ? "ISNULL(mc.cnt,0)" : "CAST(0 AS int)") + " AS multiCount"
                + " FROM dbo.dar h" + Queries.custJoin(m, "h", d.shmo, "cu")
                + (hasMulti ? " LEFT JOIN (SELECT [" + multiGh + "] g, COUNT_BIG(1) cnt FROM dbo.[" + multi + "] GROUP BY [" + multiGh + "]) mc"
                + " ON TRY_CONVERT(nvarchar(60),mc.g)=TRY_CONVERT(nvarchar(60),h.[" + d.ghno + "])" : "")
                + where + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h")
                + " ORDER BY h.[" + d.date + "] DESC, h.[" + d.ghno + "] DESC"
                + Queries.pageClause(binds, f.page, f.top);
        return new Queries.Q(sql, binds);
    }

    /** Voucher header: all fields + customer + kind + done date. */
    public static Queries.Q darHeader(Meta m, int p, String ghno) throws Queries.Missing {
        Dar d = darCols(m);
        DarParts parts = darParts(m, d, "h", p);
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(ghno);
        String sql = "SELECT TOP (1) " + Sql.txt("h", d.ghno, 60) + " AS ghno"
                + ", " + Sql.date10("h", d.date) + " AS date"
                + ", " + (d.done == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", d.done)) + " AS doneDate"
                + ", " + (d.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", d.shmo, 100)) + " AS code"
                + ", " + Queries.custNameExpr(m, "h", d.shmo, "cu") + " AS customer"
                + ", " + darTotal("h", d, m) + " AS total"
                + ", " + parts.cash + " AS cash, " + parts.pos + " AS pos"
                + ", " + parts.havaleh + " AS havaleh, " + parts.cheque + " AS cheque"
                + ", " + parts.chkCount + " AS chkCount"
                + ", " + (d.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", d.desc, 500) + ",N'')") + " AS descrip"
                + ", " + darKindExpr(m, "h", d) + " AS kind"
                + ", " + (d.user == null ? "N''" : "COALESCE(" + Sql.txt("h", d.user, 120) + ",N'')") + " AS username"
                + ", " + (d.isFinal == null ? "N''" : "COALESCE(" + Sql.txt("h", d.isFinal, 10) + ",N'')") + " AS isFinal"
                + ", " + (d.shfac == null ? "CAST(NULL AS nvarchar(80))" : Sql.txt("h", d.shfac, 80)) + " AS linkNo"
                + " FROM dbo.dar h" + Queries.custJoin(m, "h", d.shmo, "cu")
                + " WHERE h.[p]=? AND TRY_CONVERT(nvarchar(60),h.[" + d.ghno + "])=?";
        return new Queries.Q(sql, binds);
    }

    /** Card/transfer rows of a voucher: amount,bankRef,bank,desc,tracking,isHavaleh. */
    public static Queries.Q darPos(Meta m, int p, String ghno) throws Queries.Missing {
        if (!m.table("PosDetails")) throw new Queries.Missing("«جزئیات کارت/حواله» در دیتابیس پیدا نشد");
        String gh = m.must("PosDetails", "شماره قبض", "ghno", "GHNO");
        String pp = m.col("PosDetails", "p");
        String amount = m.must("PosDetails", "مبلغ", "MabPos", "mabPos", "mablagh");
        String bankRef = m.col("PosDetails", "PosBankRdf", "BankRdf", "bankrdf");
        String desc = m.col("PosDetails", "PosDesc", "posDesc", "description");
        String track = m.col("PosDetails", "ShPeigiri", "shPeigiri", "tracking");
        String hav = m.col("PosDetails", "IsHavaleh", "isHavaleh");
        String typeExpr = posKindExpr("d", hav);
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        List<Object> binds = new ArrayList<>();
        binds.add(ghno);
        String whereP = "TRY_CONVERT(nvarchar(60),d.[" + gh + "])=?";
        if (pp != null) { whereP += " AND d.[" + pp + "]=?"; binds.add(p); }
        String latest = posLatestVersionCondition(m, "d", gh, pp, hav);
        if (!latest.isEmpty()) whereP += " AND " + latest;
        String sql = "SELECT " + Sql.num("d", amount) + " AS amount"
                + ", " + (bankRef == null ? "CAST(NULL AS nvarchar(60))" : Sql.txt("d", bankRef, 60)) + " AS bankRef"
                + ", " + (bankRef == null || bankKey == null || bankName == null ? "N''"
                : "COALESCE(" + Sql.txt("b", bankName, 150) + ",N'')") + " AS bank"
                + ", " + typeExpr + " AS kind"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("d", desc, 400) + ",N'')") + " AS descrip"
                + ", " + (track == null ? "N''" : "COALESCE(" + Sql.txt("d", track, 120) + ",N'')") + " AS tracking"
                + ", " + (hav == null ? "N''" : "COALESCE(" + Sql.txt("d", hav, 10) + ",N'')") + " AS isHavaleh"
                + " FROM dbo.PosDetails d"
                + (bankRef == null || bankKey == null || !m.table("BANK") ? ""
                : " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),d.[" + bankRef + "])")
                + " WHERE " + whereP;
        return new Queries.Q(sql, binds);
    }

    /** Cheques attached to a voucher, including Atiran's prior-years archive. */
    public static Queries.Q darCheques(Meta m, int p, String ghno) throws Queries.Missing {
        Chq c = chq(m, p == 0);
        if (c.ghno == null)
            throw new Queries.Missing("ستون شماره قبض در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        binds.add(ghno);
        String order = c.sardate == null ? "" : " ORDER BY c.[" + c.sardate + "]";
        return new Queries.Q("SELECT " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("c", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("c", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + Sql.num("c", c.amount) + " AS amount"
                + ", " + (c.sardate == null ? "CAST(NULL AS nvarchar(10))" : chequeDateDisplay("c", c.sardate)) + " AS sardate"
                + ", " + (c.getdate == null ? "CAST(NULL AS nvarchar(10))" : chequeDateDisplay("c", c.getdate)) + " AS getdate"
                + ", " + (c.st == null ? "N''" : "COALESCE(" + Sql.txt("c", c.st, 40) + ",N'')") + " AS st"
                + ", " + (c.back == null ? "N''" : "COALESCE(" + Sql.txt("c", c.back, 10) + ",N'')") + " AS back"
                + " FROM " + c.from + " c WHERE TRY_CONVERT(nvarchar(60),c.[" + c.ghno + "])=?" + order, binds);
    }

    /** Invoices settled by a voucher: no,date,customer,total,paidSettled. */
    public static Queries.Q darSettled(Meta m, int p, String ghno) throws Queries.Missing {
        boolean incoming = p == 0;
        String head = incoming ? "sailfact" : "buyfact";
        String multi = incoming ? "DaryaftMultiFactor" : "PardakhtMultiFactor";
        String headNo = incoming ? m.must(head, "شماره فاکتور", "shfacfo")
                : m.must(head, "شماره فاکتور", "shfackh", "shfac");
        String headDate = incoming ? m.col(head, "date") : m.col(head, "DATE", "date");
        String headAmount = m.col(head, "all");
        String headShmo = m.col(head, "shmo", "SHMO");
        String multiGh = m.col(multi, "GhnoDar", "ghnoDar", "ghno");
        String multiNo = m.col(multi, "Shfacfo", "shfacfo", "shfackh", "shfac");
        String multiPrice = m.col(multi, "Price", "price");
        List<Object> binds = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (multiGh != null && multiNo != null && m.table(multi)) {
            parts.add("SELECT " + Sql.txt("h", headNo, 80) + " AS no"
                    + ", " + Sql.date10("h", headDate) + " AS date"
                    + ", " + Queries.custNameExpr(m, "h", headShmo, "cu") + " AS customer"
                    + ", " + Sql.num("h", headAmount) + " AS total"
                    + ", " + Sql.num("mf", multiPrice) + " AS paidSettled"
                    + " FROM dbo.[" + multi + "] mf JOIN dbo.[" + head + "] h"
                    + " ON TRY_CONVERT(nvarchar(80),h.[" + headNo + "])=TRY_CONVERT(nvarchar(80),mf.[" + multiNo + "])"
                    + Queries.custJoin(m, "h", headShmo, "cu")
                    + " WHERE TRY_CONVERT(nvarchar(60),mf.[" + multiGh + "])=?");
            binds.add(ghno);
        }
        // Direct single-invoice link dar.shfacfo.
        Dar d = darCols(m);
        if (d.shfac != null) {
            parts.add("SELECT " + Sql.txt("h", headNo, 80) + " AS no"
                    + ", " + Sql.date10("h", headDate) + " AS date"
                    + ", " + Queries.custNameExpr(m, "h", headShmo, "cu") + " AS customer"
                    + ", " + Sql.num("h", headAmount) + " AS total"
                    + ", CAST(0 AS decimal(19,2)) AS paidSettled"
                    + " FROM dbo.dar dr JOIN dbo.[" + head + "] h"
                    + " ON TRY_CONVERT(nvarchar(80),h.[" + headNo + "])=TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "])"
                    + Queries.custJoin(m, "h", headShmo, "cu")
                    + " WHERE dr.[p]=? AND TRY_CONVERT(nvarchar(60),dr.[" + d.ghno + "])=?"
                    + " AND NULLIF(TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "]),N'0') IS NOT NULL");
            binds.add(p);
            binds.add(ghno);
        }
        if (parts.isEmpty()) throw new Queries.Missing("ارتباط قبض با فاکتور قابل تشخیص نیست");
        return new Queries.Q(Sql.join(parts, " UNION ") + " ORDER BY 2 DESC", binds);
    }

    /** Vouchers linked to one invoice: ghno,date,total,settled. */
    public static Queries.Q invoiceDars(Meta m, int p, String no) throws Queries.Missing {
        Dar d = darCols(m);
        String multi = p == 0 ? "DaryaftMultiFactor" : "PardakhtMultiFactor";
        String multiGh = m.col(multi, "GhnoDar", "ghnoDar", "ghno");
        String multiNo = m.col(multi, "Shfacfo", "shfacfo", "shfackh", "shfac");
        String multiPrice = m.col(multi, "Price", "price");
        List<Object> binds = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (d.shfac != null) {
            parts.add("SELECT " + Sql.txt("dr", d.ghno, 60) + " AS ghno, " + Sql.date10("dr", d.date) + " AS date"
                    + ", " + darTotal("dr", d, m) + " AS total, CAST(0 AS decimal(19,2)) AS settled"
                    + " FROM dbo.dar dr WHERE dr.[p]=? AND TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "])=?"
                    + " AND NULLIF(TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "]),N'0') IS NOT NULL");
            binds.add(p);
            binds.add(no);
        }
        if (multiGh != null && multiNo != null && m.table(multi)) {
            parts.add("SELECT " + Sql.txt("dr", d.ghno, 60) + " AS ghno, " + Sql.date10("dr", d.date) + " AS date"
                    + ", " + darTotal("dr", d, m) + " AS total, " + Sql.num("mf", multiPrice) + " AS settled"
                    + " FROM dbo.[" + multi + "] mf JOIN dbo.dar dr"
                    + " ON TRY_CONVERT(nvarchar(60),dr.[" + d.ghno + "])=TRY_CONVERT(nvarchar(60),mf.[" + multiGh + "]) AND dr.[p]=?"
                    + " WHERE TRY_CONVERT(nvarchar(80),mf.[" + multiNo + "])=?");
            binds.add(p);
            binds.add(no);
        }
        if (parts.isEmpty()) throw new Queries.Missing("ارتباط فاکتور با قبوض قابل تشخیص نیست");
        return new Queries.Q(Sql.join(parts, " UNION ") + " ORDER BY 2 DESC", binds);
    }

    /** Card and transfer turnover by bank: bank,kind,total,count. p<0 = both sides. */
    public static Queries.Q posByBank(Meta m, Filter f, int p) throws Queries.Missing {
        if (!m.table("PosDetails")) throw new Queries.Missing("«جزئیات کارت/حواله» در دیتابیس پیدا نشد");
        String amount = m.must("PosDetails", "مبلغ", "MabPos", "mabPos", "mablagh");
        String bankRef = m.must("PosDetails", "بانک", "PosBankRdf", "BankRdf", "bankrdf");
        String date = m.col("PosDetails", "date", "Date", "done_date", "DoneDate");
        String gh = m.col("PosDetails", "ghno", "GHNO");
        String pp = m.col("PosDetails", "p");
        String hav = m.col("PosDetails", "IsHavaleh", "isHavaleh");
        String kind = posKindExpr("d", hav);
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        String label = bankKey != null && bankName != null && m.table("BANK")
                ? "COALESCE(" + Sql.txt("b", bankName, 150) + "," + Sql.txt("d", bankRef, 60) + ",N'—')"
                : "COALESCE(" + Sql.txt("d", bankRef, 60) + ",N'—')";
        String join = bankKey != null && m.table("BANK")
                ? " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),d.[" + bankRef + "])" : "";
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (p >= 0 && pp != null) { conds.add("d.[" + pp + "]=?"); binds.add(p); }
        String latest = posLatestVersionCondition(m, "d", gh, pp, hav);
        if (!latest.isEmpty()) conds.add(latest);
        if (date != null) {
            String dc = Sql.dateCond(Sql.date10("d", date), f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT " + label + " AS bank, " + kind + " AS kind"
                + ", ISNULL(SUM(" + Sql.num("d", amount) + "),0) AS total"
                + ", COUNT_BIG(1) AS count FROM dbo.PosDetails d" + join + where
                + " GROUP BY " + label + ", " + kind + " ORDER BY 3 DESC", binds);
    }

    private static String chequeDateSortKey(Meta m, String alias, String column) {
        if (column == null) return "CAST(NULL AS bigint)";
        String raw = Sql.date10(alias, column);
        String jalaliKey = Sql.dateKey(raw);
        String persianToJulian = m.qFunction("UDF_Persian_To_Julian");
        if (persianToJulian == null) return jalaliKey;
        String year = "TRY_CONVERT(int,SUBSTRING(" + jalaliKey + ",1,4))";
        String month = "TRY_CONVERT(int,SUBSTRING(" + jalaliKey + ",6,2))";
        String day = "TRY_CONVERT(int,SUBSTRING(" + jalaliKey + ",9,2))";
        String ref = alias + ".[" + column + "]";
        String gregText = "CASE WHEN " + raw + " LIKE N'[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'"
                + " THEN STUFF(STUFF(" + raw + ",5,0,N'-'),8,0,N'-') ELSE " + raw + " END";
        String greg = "TRY_CONVERT(date," + gregText + ",23)";
        String fallbackGreg = "TRY_CONVERT(date," + ref + ")";
        String epoch = "CONVERT(date,N'00010101',112)";
        String gregKey = "CONVERT(bigint,DATEDIFF(day," + epoch + ",";
        String gregKeyEnd = ")+1721426)";
        return "CASE WHEN " + year + " BETWEEN 1300 AND 1600 AND " + month + " BETWEEN 1 AND 12 AND "
                + day + " BETWEEN 1 AND 31 THEN " + persianToJulian + "(" + year + "," + month + "," + day + ")"
                + " WHEN " + greg + " IS NOT NULL THEN " + gregKey + greg + gregKeyEnd
                + " WHEN " + fallbackGreg + " IS NOT NULL THEN " + gregKey + fallbackGreg + gregKeyEnd
                + " ELSE CAST(NULL AS bigint) END";
    }

    private static String chequeDateDisplay(String alias, String column) {
        if (column == null) return "CAST(NULL AS nvarchar(10))";
        String ref = alias + ".[" + column + "]";
        String raw = Sql.date10(alias, column);
        String normalized = Sql.dateKey(raw);
        String year = "TRY_CONVERT(int,SUBSTRING(" + normalized + ",1,4))";
        String gregorian = "COALESCE(TRY_CONVERT(date," + ref + ",23),TRY_CONVERT(date," + ref + ",111)"
                + ",TRY_CONVERT(date,TRY_CONVERT(nvarchar(30)," + ref + "),112),TRY_CONVERT(date," + ref + "))";
        String gregorianText = "CONVERT(nvarchar(10)," + gregorian + ",23)";
        return "CASE WHEN " + year + " BETWEEN 1300 AND 1600 THEN " + normalized
                + " WHEN " + gregorian + " IS NOT NULL THEN " + gregorianText + " ELSE " + raw + " END";
    }

    private static String chequeDateRange(String alias, String column, String from, String to, List<Object> binds) {
        if (column == null) return "";
        String ref = alias + ".[" + column + "]";
        String raw = "LEFT(TRY_CONVERT(nvarchar(30)," + ref + "),10)";
        String year = "TRY_CONVERT(int,SUBSTRING(" + raw + ",1,4))";
        String jalaliValue = "CASE WHEN " + year + " BETWEEN 1300 AND 1600 THEN REPLACE(" + raw
                + ",N'-',N'/') ELSE " + raw + " END";
        List<String> ranges = new ArrayList<>();
        String jalali = Sql.dateCond(jalaliValue, from, to, binds);
        if (!jalali.isEmpty()) ranges.add(jalali);

        String gregorianValue = "COALESCE(TRY_CONVERT(date," + ref + ",23),TRY_CONVERT(date," + ref + ",111)"
                + ",TRY_CONVERT(date,TRY_CONVERT(nvarchar(30)," + ref + "),112),TRY_CONVERT(date," + ref + "))";
        String gregorianExpr = "CONVERT(nvarchar(10)," + gregorianValue + ",23)";
        String gregorian = Sql.dateCond(gregorianExpr, Jalali.toGregorian(from), Jalali.toGregorian(to), binds);
        if (!gregorian.isEmpty()) ranges.add(gregorian);
        return ranges.isEmpty() ? "" : "(" + Sql.join(ranges, " OR ") + ")";
    }

    // =====================================================================================
    // Cheques. Source = VW when fully usable, else the base table.
    // =====================================================================================
    public static final class Chq {
        String table, from, amount, num, bank, branch, acc, sardate, getdate, st, back, ourBank, ghno;
        String shmo, sayad, desc, kharjTo, kharjDate, naghdDate, typeId, typeName;
        String custName, custCode, statusLabel;
        boolean isView;
    }

    /** Resolve cheque source + columns (view preferred). */
    public static Chq chq(Meta m, boolean incoming) throws Queries.Missing {
        String base = incoming ? "getchk" : "putchk";
        String vw = incoming ? "VW_getchk" : "VW_Putchk";
        Chq c = new Chq();
        String use = base;
        if (m.table(vw)) {
            boolean amountAndStatus = incoming
                    ? m.col(vw, "getchkmab") != null && m.col(vw, "chk_satus") != null
                    : m.col(vw, "putchkmab") != null && m.col(vw, "putchk_status") != null;
            boolean datesAndVoucher = m.col(vw, "ghno") != null
                    && m.col(vw, "sardate", "sarresid", "due_date", "duedate") != null
                    && m.col(vw, incoming ? "getdate" : "putdate", "DateOfReceipt", "done_date", "date") != null;
            // A view is useful only if it preserves both distinct dates and the voucher key.
            // Otherwise resolve against getchk/putchk so filters/details cannot silently
            // substitute the due date for the receipt/issue date.
            if (amountAndStatus && datesAndVoucher) use = vw;
        }
        if (!m.table(use)) throw new Queries.Missing("«" + AtiranSchema.faTitle(base) + "» در دیتابیس پیدا نشد");
        c.table = use;
        c.isView = !use.equals(base);
        if (incoming) {
            c.amount = m.must(use, "مبلغ چک", "getchkmab", "mablagh", "Mablagh", "amount");
            c.st = m.must(use, "وضعیت چک", "chk_satus", "status", "vaziat");
            c.num = m.col(use, "shgetchk", "serial", "number", "shomare");
            c.bank = m.col(use, "getchbank");
            c.branch = m.col(use, "getchkshobe");
            c.acc = m.col(use, "getchkshhes");
            c.sardate = m.col(use, "sardate", "sarresid", "due_date", "duedate");
            c.getdate = m.col(use, "getdate", "DateOfReceipt", "done_date", "date");
            c.back = m.col(use, "back");
            c.ourBank = m.col(use, "our_bankrdf", "bankrdf");
            c.shmo = m.col(use, "shmo");
            c.sayad = m.col(use, "ShenaseSayad");
            c.desc = m.col(use, "getchkdis", "Description", "DisGetCheck");
            c.ghno = m.col(use, "ghno");
            c.kharjTo = m.col(use, "kharj_shmo");
            c.kharjDate = m.col(use, "kharj_date", "kharj_done_date");
            c.naghdDate = m.col(use, "naghddate", "naghddonedate");
            c.typeId = m.col(use, "CheckTypeID");
            if (c.isView) {
                c.custName = m.col(use, "CustomerName", "VIRTUALNAME");
                c.custCode = m.col(use, "customerCode");
                c.statusLabel = m.col(use, "CheckStatus", "state");
            }
        } else {
            c.amount = m.must(use, "مبلغ چک", "putchkmab", "mablagh", "Mablagh", "amount");
            c.st = m.must(use, "وضعیت چک", "putchk_status", "status", "vaziat");
            c.num = m.col(use, "shputchk", "serial", "number", "shomare");
            c.bank = m.col(use, "bankrdf");
            c.sardate = m.col(use, "sardate", "sarresid", "due_date", "duedate");
            c.getdate = m.col(use, "putdate", "DateOfReceipt", "done_date", "date");
            c.back = m.col(use, "back");
            c.shmo = m.col(use, "shmo");
            c.sayad = m.col(use, "ShenaseSayad");
            c.desc = m.col(use, "putchkdis", "Desc", "Description");
            c.ghno = m.col(use, "ghno");
            c.typeId = m.col(use, "CheckTypeID");
            c.branch = m.col(use, "girande");
            if (c.isView) {
                c.custName = m.col(use, "CustomerName", "VIRTUALNAME");
                c.custCode = m.col(use, "customerCode");
                c.statusLabel = m.col(use, "CheckStatus", "state");
            }
        }
        c.from = chequeFrom(m, c, incoming);
        return c;
    }

    /**
     * Atiran keeps cheques in two places: the live table and its prior-years archive
     * (getchk 76 rows vs NGetchk 438; putchk 202 vs NPutchk 1026 on the 1405/07/14 backup).
     * Reading only the live table reported roughly one cheque in six, which is why the
     * cheque sections looked wrong. This returns a FROM source spanning both.
     *
     * It degrades to the live table alone whenever the archive is missing, or whenever the
     * archive does not expose the amount and status columns every report depends on — so a
     * differently-shaped archive can never inject phantom rows or crash a screen.
     */
    private static String chequeBankFilter(Chq c, int bank, List<Object> binds) throws Queries.Missing {
        if (bank < 0) return "";
        if (c.ourBank == null)
            throw new Queries.Missing("ستون بانک برای اعمال فیلتر در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        binds.add(bank);
        return "TRY_CONVERT(int,h.[" + c.ourBank + "])=?";
    }

    private static String validChequeStatus(String alias, String statusColumn) {
        // Match Atiran's cheque views: void/deleted codes 6, 7, and 8 are omitted.
        if (statusColumn == null) return "1=1";
        String status = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40)," + alias + ".[" + statusColumn + "])))";
        return "(" + status + " NOT IN (N'6',N'7',N'8') OR " + status + " IS NULL)";
    }

    private static String chequeArchiveColumn(Meta m, String arch, Chq c, String name, boolean incoming) {
        if (name == null) return null;
        if (c.sardate != null && c.sardate.equalsIgnoreCase(name))
            return m.col(arch, "sardate", "sarresid", "due_date", "duedate");
        if (c.getdate != null && c.getdate.equalsIgnoreCase(name))
            return incoming ? m.col(arch, "getdate", "DateOfReceipt", "done_date", "date")
                    : m.col(arch, "putdate", "DateOfReceipt", "done_date", "date");
        return m.col(arch, name);
    }

    private static String chequeFrom(Meta m, Chq c, boolean incoming) {
        String live = "dbo.[" + c.table + "]";
        String liveStatus = m.col(c.table, c.st);
        String activeLive = "(SELECT * FROM " + live + " liveq WHERE "
                + validChequeStatus("liveq", liveStatus) + ")";
        String arch = incoming ? "NGetchk" : "NPutchk";
        if (!m.table(arch)) return activeLive;
        // Every cheque report needs an amount and a status. Without both on the archive we
        // ignore it, so a differently-shaped archive can never inject phantom rows.
        if (m.col(arch, c.amount) == null || m.col(arch, c.st) == null) return activeLive;

        String[][] cols = {
                {c.amount, "decimal(19,2)"}, {c.st, "nvarchar(80)"},
                {c.num, "nvarchar(120)"}, {c.bank, "nvarchar(200)"},
                {c.branch, "nvarchar(200)"}, {c.acc, "nvarchar(200)"},
                {c.sardate, "nvarchar(40)"}, {c.getdate, "nvarchar(40)"},
                {c.back, "nvarchar(20)"}, {c.ourBank, "nvarchar(80)"},
                {c.shmo, "nvarchar(120)"}, {c.sayad, "nvarchar(120)"},
                {c.desc, "nvarchar(2000)"}, {c.ghno, "nvarchar(120)"},
                {c.kharjTo, "nvarchar(120)"}, {c.kharjDate, "nvarchar(40)"},
                {c.naghdDate, "nvarchar(40)"}, {c.typeId, "int"},
                {c.custName, "nvarchar(400)"}, {c.custCode, "nvarchar(120)"},
                {c.statusLabel, "nvarchar(200)"}, {"rdf", "nvarchar(120)"},
        };
        StringBuilder a = new StringBuilder("(SELECT ");
        StringBuilder b = new StringBuilder(" UNION ALL SELECT ");
        boolean first = true;
        for (String[] col : cols) {
            String name = col[0], type = col[1];
            if (name == null || name.trim().isEmpty()) continue;
            if (!first) { a.append(", "); b.append(", "); }
            first = false;
            String liveCol = m.col(c.table, name);
            String archiveCol = chequeArchiveColumn(m, arch, c, name, incoming);
            a.append("TRY_CAST(").append(liveCol != null ? "[" + liveCol + "]" : "NULL")
                    .append(" AS ").append(type).append(") AS [").append(name).append("]");
            b.append("TRY_CAST(").append(archiveCol != null ? "[" + archiveCol + "]" : "NULL")
                    .append(" AS ").append(type).append(")");
        }
        String liveWhere = validChequeStatus("liveq", m.col(c.table, c.st));
        String archiveWhere = validChequeStatus("archq", m.col(arch, c.st));
        return first ? activeLive : a + " FROM " + live + " liveq WHERE " + liveWhere
                + b + " FROM dbo.[" + arch + "] archq WHERE " + archiveWhere + ")";
    }

    private static String chqStatusLabel(Meta m, Chq c, String a) {
        if (c.statusLabel != null) return "COALESCE(" + Sql.txt(a, c.statusLabel, 120) + ",N'')";
        // NOTE: CheckTypes holds cheque TYPES (keyed by CheckTypeID, 2 rows) — never statuses.
        // Joining it on the status code produced wrong labels, so there is no DB fallback:
        // the UI maps codes via AtiranSchema (validated against Atiran's procedures).
        return "N''";
    }

    /** Grouped statuses for bucket cards: st,back,hasBank,count,total. */
    public static Queries.Q chequeGroups(Meta m, boolean incoming, Filter f) throws Queries.Missing {
        Chq c = chq(m, incoming);
        if (c.getdate == null)
            throw new Queries.Missing("ستون تاریخ دریافت/صدور در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (c.getdate != null) {
            // Match Jalali text, Gregorian datetime, and bare-digit Atiran date variants.
            String dc = chequeDateRange("h", c.getdate, f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        String bankFilter = chequeBankFilter(c, f.bank, binds);
        if (!bankFilter.isEmpty()) conds.add(bankFilter);
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        boolean needCust = c.custName == null && c.shmo != null && custName != null
                && f.search != null && !f.search.trim().isEmpty();
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            if (c.num != null) exprs.add(Sql.txt("h", c.num, 80));
            if (c.bank != null) exprs.add(Sql.txt("h", c.bank, 150));
            if (c.sayad != null) exprs.add(Sql.txt("h", c.sayad, 60));
            if (c.custName != null) exprs.add(Sql.txt("h", c.custName, 250));
            else if (needCust) exprs.add(Sql.txt("cu", custName, 250));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String join = needCust ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String back = c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')";
        String bank = c.ourBank == null ? "CAST(0 AS int)"
                : "CASE WHEN TRY_CONVERT(int,h.[" + c.ourBank + "])>0 THEN 1 ELSE 0 END";
        return new Queries.Q("SELECT COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st, " + back + " AS back"
                + ", " + bank + " AS hasBank, COUNT_BIG(1) AS count"
                + ", ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM " + c.from + " h" + join + where + " GROUP BY COALESCE(" + Sql.txt("h", c.st, 40) + ",N''), "
                + back + ", " + bank, binds);
    }

    /**
     * Cheque list. bucket: "" all; incoming: sandogh/bank/vosool/esterdad/kharj/bargashti/sayer;
     * outgoing: jari/pas/sefid/sayer. Codes follow Atiran's own procedures (see AtiranSchema).
     */
    public static Queries.Q chequeList(Meta m, boolean incoming, Filter f, String bucket) throws Queries.Missing {
        Chq c = chq(m, incoming);
        if (c.getdate == null)
            throw new Queries.Missing("ستون تاریخ دریافت/صدور در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (c.getdate != null) {
            // Match Jalali text, Gregorian datetime, and bare-digit Atiran date variants.
            String dc = chequeDateRange("h", c.getdate, f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        String back = c.back == null ? null : "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "]))))";
        String b = bucket == null ? "" : bucket;
        String backIsT = back == null ? null : back + " IN (N'T',N'1',N'TRUE')";
        String backNotT = back == null ? null : "(" + back + " NOT IN (N'T',N'1',N'TRUE') OR " + back + " IS NULL)";
        if (incoming) {
            // Proc truth: 1 in-hand • 2 at bank • 3 collected • 4 returned • 5 spent • back='t' bounced.
            if ("sandogh".equals(b)) { conds.add(st + "=N'1'"); if (backNotT != null) conds.add(backNotT); }
            else if ("bank".equals(b)) conds.add(st + "=N'2'");
            else if ("vosool".equals(b)) conds.add(st + "=N'3'");
            else if ("esterdad".equals(b)) conds.add(st + "=N'4'");
            else if ("kharj".equals(b)) conds.add(st + "=N'5'");
            else if ("bargashti".equals(b)) {
                if (backIsT == null) throw new Queries.Missing("ستون برگشتی در «چک‌های دریافتی» پیدا نشد");
                conds.add(backIsT);
            } else if ("sayer".equals(b)) {
                conds.add("(" + st + " NOT IN (N'1',N'2',N'3',N'4',N'5')" + (backIsT == null ? "" : " AND NOT (" + backIsT + ")") + ")");
            }
        } else {
            // Proc truth: 0 blank • 1 issued • 2 cleared. (No bounce flow exists for putchk.)
            if ("jari".equals(b)) conds.add(st + "=N'1'");
            else if ("pas".equals(b)) conds.add(st + "=N'2'");
            else if ("sefid".equals(b)) conds.add(st + "=N'0'");
            else if ("sayer".equals(b)) conds.add(st + " NOT IN (N'0',N'1',N'2')");
        }
        String bankFilter = chequeBankFilter(c, f.bank, binds);
        if (!bankFilter.isEmpty()) conds.add(bankFilter);
        String customerCol = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            if (c.num != null) exprs.add(Sql.txt("h", c.num, 80));
            if (c.bank != null) exprs.add(Sql.txt("h", c.bank, 150));
            if (c.sayad != null) exprs.add(Sql.txt("h", c.sayad, 60));
            if (c.custName != null) exprs.add(Sql.txt("h", c.custName, 250));
            else if (customerCol != null && c.shmo != null) exprs.add(Sql.txt("cu", customerCol, 250));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        boolean needCust = c.custName == null && c.shmo != null && customerCol != null;
        String join = needCust ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        String codeExpr = c.custCode != null ? Sql.txt("h", c.custCode, 100)
                : (c.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", c.shmo, 100));
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        // The list is filtered by receipt/issue date, so sort by that same visible date.
        // Normalize Jalali and Gregorian values to one ordinal when Atiran's converter exists.
        String eventDate = c.getdate != null ? c.getdate : c.sardate;
        String orderKey = eventDate == null ? "CAST(NULL AS bigint)" : chequeDateSortKey(m, "h", eventDate);
        String orderCol = "CASE WHEN " + orderKey + " IS NULL THEN 1 ELSE 0 END, " + orderKey + " DESC";
        String sql = "SELECT " + Sql.txt("h", "rdf", 60) + " AS rdf"
                + ", " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("h", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + (c.branch == null ? "N''" : "COALESCE(" + Sql.txt("h", c.branch, 200) + ",N'')") + " AS branch"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + (c.sardate == null ? "CAST(NULL AS nvarchar(10))" : chequeDateDisplay("h", c.sardate)) + " AS sardate"
                + ", " + (c.getdate == null ? "CAST(NULL AS nvarchar(10))" : chequeDateDisplay("h", c.getdate)) + " AS getdate"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + ", " + (c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')") + " AS back"
                + ", " + chqStatusLabel(m, c, "h") + " AS statusLabel"
                + ", " + codeExpr + " AS code, " + custExpr + " AS customer"
                + ", " + (c.sayad == null ? "N''" : "COALESCE(" + Sql.txt("h", c.sayad, 60) + ",N'')") + " AS sayad"
                + ", " + (c.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", c.desc, 500) + ",N'')") + " AS descrip"
                + ", " + (c.ghno == null ? "N''" : "COALESCE(" + Sql.txt("h", c.ghno, 60) + ",N'')") + " AS ghno"
                + " FROM " + c.from + " h" + join + where
                + " ORDER BY " + orderCol + Queries.pageClause(binds, f.page, f.top);
        // «rdf» may be named differently on the view; fall back gracefully.
        if (m.col(c.table, "rdf") == null) sql = sql.replace(Sql.txt("h", "rdf", 60) + " AS rdf", "CAST(NULL AS nvarchar(60)) AS rdf");
        return new Queries.Q(sql, binds);
    }

    /** Date-bounded cheque list for a closeout; never degrade to an all-time list. */
    public static Queries.Q chequeListOn(Meta m, boolean incoming, Filter f) throws Queries.Missing {
        Chq c = chq(m, incoming);
        if (c.getdate == null)
            throw new Queries.Missing("ستون تاریخ ثبت در «" + AtiranSchema.faTitle(c.table)
                    + "» پیدا نشد؛ فهرست همه‌زمانی به‌عنوان روزانه نمایش داده نمی‌شود");
        return chequeList(m, incoming, f, "");
    }

    /** Shared WHERE for outstanding cheques due within [today, today+daysAhead]. */
    private static final class DueCtx {
        Chq c;
        List<String> conds;
        List<Object> binds;
        String sar;
    }

    private static DueCtx dueCtx(Meta m, boolean incoming, int daysAhead) throws Queries.Missing {
        return dueCtx(m, incoming, daysAhead, "", "");
    }

    private static DueCtx dueCtx(Meta m, boolean incoming, int daysAhead, String from, String to) throws Queries.Missing {
        DueCtx x = new DueCtx();
        x.c = chq(m, incoming);
        if (x.c.sardate == null)
            throw new Queries.Missing("ستون سررسید در «" + AtiranSchema.faTitle(x.c.table) + "» پیدا نشد");
        String today = Jalali.todayStr();
        String until = Jalali.addDays(today, Math.max(0, daysAhead));
        x.binds = new ArrayList<>();
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + x.c.st + "])))";
        x.conds = new ArrayList<>();
        // Due dates may be Jalali text or Gregorian datetime — match the same range in both.
        x.sar = Sql.date10("h", x.c.sardate);
        boolean explicitRange = (from != null && !from.trim().isEmpty()) || (to != null && !to.trim().isEmpty());
        String jalaliFrom = explicitRange ? from : today;
        String jalaliTo = explicitRange ? to : until;
        String dueRange = chequeDateRange("h", x.c.sardate, jalaliFrom, jalaliTo, x.binds);
        if (!dueRange.isEmpty()) x.conds.add(dueRange);
        // Outstanding = still with us: incoming 1/2 (not bounced), outgoing 1 (issued).
        x.conds.add(incoming ? st + " IN (N'1',N'2')" : st + "=N'1'");
        if (incoming && x.c.back != null) {
            String bk = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + x.c.back + "]))))";
            // NULL back flags (legacy rows) must stay visible: NOT IN alone would drop them.
            x.conds.add("(" + bk + " NOT IN (N'T',N'1',N'TRUE') OR " + bk + " IS NULL)");
        }
        // Skip placeholder due dates («--», «1499/12/29» on blank cheques).
        x.conds.add("(" + x.sar + " NOT LIKE N'--%' AND " + x.sar + " NOT LIKE N'149%' AND " + x.sar + " NOT LIKE N'15%' AND " + x.sar + " NOT LIKE N'16%')");
        return x;
    }

    /** Cheques due within daysAhead (still outstanding). A supplied custom range is a due-date range. */
    public static Queries.Q chequeDue(Meta m, boolean incoming, int daysAhead) throws Queries.Missing {
        return chequeDue(m, incoming, daysAhead, null);
    }

    public static Queries.Q chequeDue(Meta m, boolean incoming, int daysAhead, Filter f) throws Queries.Missing {
        String from = f != null && f.preset == Filter.P_CUSTOM ? f.from : "";
        String to = f != null && f.preset == Filter.P_CUSTOM ? f.to : "";
        DueCtx x = dueCtx(m, incoming, daysAhead, from, to);
        Chq c = x.c;
        String customerCol = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        if (f != null) {
            String bankFilter = chequeBankFilter(c, f.bank, x.binds);
            if (!bankFilter.isEmpty()) x.conds.add(bankFilter);
            if (f.search != null && !f.search.trim().isEmpty()) {
                List<String> exprs = new ArrayList<>();
                if (c.num != null) exprs.add(Sql.txt("h", c.num, 80));
                if (c.bank != null) exprs.add(Sql.txt("h", c.bank, 150));
                if (c.sayad != null) exprs.add(Sql.txt("h", c.sayad, 60));
                if (c.custName != null) exprs.add(Sql.txt("h", c.custName, 250));
                else if (customerCol != null && c.shmo != null) exprs.add(Sql.txt("cu", customerCol, 250));
                String sc = Queries.searchCond(x.binds, f.search, exprs.toArray(new String[0]));
                if (!sc.isEmpty()) x.conds.add(sc);
            }
        }
        boolean needCust = c.custName == null && c.shmo != null && customerCol != null;
        String join = needCust ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        String codeExpr = c.custCode != null ? Sql.txt("h", c.custCode, 100)
                : (c.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", c.shmo, 100));
        String rdfExpr = m.col(c.table, "rdf") == null ? "CAST(NULL AS nvarchar(60))" : Sql.txt("h", "rdf", 60);
        String sql = "SELECT " + rdfExpr + " AS rdf"
                + ", " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("h", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + (c.branch == null ? "N''" : "COALESCE(" + Sql.txt("h", c.branch, 200) + ",N'')") + " AS branch"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + chequeDateDisplay("h", c.sardate) + " AS sardate"
                + ", " + (c.getdate == null ? "CAST(NULL AS nvarchar(10))" : chequeDateDisplay("h", c.getdate)) + " AS getdate"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + ", " + (c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')") + " AS back"
                + ", " + chqStatusLabel(m, c, "h") + " AS statusLabel"
                + ", " + codeExpr + " AS code, " + custExpr + " AS customer"
                + ", " + (c.sayad == null ? "N''" : "COALESCE(" + Sql.txt("h", c.sayad, 60) + ",N'')") + " AS sayad"
                + ", " + (c.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", c.desc, 500) + ",N'')") + " AS descrip"
                + ", " + (c.ghno == null ? "N''" : "COALESCE(" + Sql.txt("h", c.ghno, 60) + ",N'')") + " AS ghno"
                + " FROM " + c.from + " h" + join + " WHERE " + Sql.join(x.conds, " AND ")
                + " ORDER BY CASE WHEN " + chequeDateSortKey(m, "h", c.sardate) + " IS NULL THEN 1 ELSE 0 END, "
                + chequeDateSortKey(m, "h", c.sardate);
        return new Queries.Q(sql, x.binds);
    }

    /** Daily cash-flow forecast: per-day count + total of outstanding cheques due in the window. */
    public static Queries.Q chequeDueDaily(Meta m, boolean incoming, int daysAhead) throws Queries.Missing {
        DueCtx x = dueCtx(m, incoming, daysAhead);
        Chq c = x.c;
        String sortKey = chequeDateSortKey(m, "h", c.sardate);
        return new Queries.Q("SELECT MIN(" + chequeDateDisplay("h", c.sardate) + ") AS day, COUNT(*) AS n, ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM " + c.from + " h WHERE " + Sql.join(x.conds, " AND ")
                + " GROUP BY " + sortKey + " ORDER BY " + sortKey, x.binds);
    }
}
