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

    private static String darTotal(String a, Dar d) {
        return "(" + Sql.num(a, d.cash) + "+" + Sql.num(a, d.pos) + "+" + Sql.num(a, d.havaleh) + "+" + Sql.num(a, d.cheque) + ")";
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
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(day);
        String sql = "SELECT " + Sql.lit(day) + " AS d, ISNULL(SUM(" + darTotal("h", d) + "),0) AS total"
                + ", ISNULL(SUM(" + Sql.num("h", d.cash) + "),0) AS cash"
                + ", ISNULL(SUM(" + Sql.num("h", d.pos) + "),0) AS pos"
                + ", ISNULL(SUM(" + Sql.num("h", d.havaleh) + "),0) AS havaleh"
                + ", ISNULL(SUM(" + Sql.num("h", d.cheque) + "),0) AS cheque"
                + ", COUNT_BIG(1) AS count FROM dbo.dar h WHERE h.[p]=?"
                + " AND " + Sql.date10("h", d.date) + "=?"
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h");
        return new Queries.Q(sql, binds);
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
        String day = Sql.date10("h", d.date);
        return new Queries.Q("SELECT " + day + " AS day, ISNULL(SUM(" + darTotal("h", d) + "),0) AS total"
                + ", COUNT_BIG(1) AS count FROM dbo.dar h WHERE " + Sql.join(conds, " AND ")
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h") + " GROUP BY " + day + " ORDER BY " + day, binds);
    }

    /** Summary: total,cash,pos,havaleh,cheque,count. */
    public static Queries.Q darSummary(Meta m, Filter f, int p) throws Queries.Missing {
        Dar d = darCols(m);
        Set<String> cols = m.columns("dar");
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        List<String> conds = new ArrayList<>();
        conds.add("h.[p]=?");
        String dc = Sql.dateCond(Sql.date10("h", d.date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        return new Queries.Q("SELECT ISNULL(SUM(" + darTotal("h", d) + "),0) AS total"
                + ", ISNULL(SUM(" + Sql.num("h", d.cash) + "),0) AS cash"
                + ", ISNULL(SUM(" + Sql.num("h", d.pos) + "),0) AS pos"
                + ", ISNULL(SUM(" + Sql.num("h", d.havaleh) + "),0) AS havaleh"
                + ", ISNULL(SUM(" + Sql.num("h", d.cheque) + "),0) AS cheque"
                + ", COUNT_BIG(1) AS count FROM dbo.dar h WHERE " + Sql.join(conds, " AND ")
                + Sql.activeAnd(cols, "h") + Sql.softAnd(cols, "h"), binds);
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
        String sql = "SELECT " + Sql.txt("h", d.ghno, 60) + " AS ghno"
                + ", " + Sql.date10("h", d.date) + " AS date"
                + ", " + (d.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", d.shmo, 100)) + " AS code"
                + ", " + Queries.custNameExpr(m, "h", d.shmo, "cu") + " AS customer"
                + ", " + darTotal("h", d) + " AS total"
                + ", " + Sql.num("h", d.cash) + " AS cash, " + Sql.num("h", d.pos) + " AS pos"
                + ", " + Sql.num("h", d.havaleh) + " AS havaleh, " + Sql.num("h", d.cheque) + " AS cheque"
                + ", " + (d.chkCount == null ? "CAST(0 AS int)" : "TRY_CONVERT(int,h.[" + d.chkCount + "])") + " AS chkCount"
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
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(ghno);
        String sql = "SELECT TOP (1) " + Sql.txt("h", d.ghno, 60) + " AS ghno"
                + ", " + Sql.date10("h", d.date) + " AS date"
                + ", " + (d.done == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", d.done)) + " AS doneDate"
                + ", " + (d.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", d.shmo, 100)) + " AS code"
                + ", " + Queries.custNameExpr(m, "h", d.shmo, "cu") + " AS customer"
                + ", " + darTotal("h", d) + " AS total"
                + ", " + Sql.num("h", d.cash) + " AS cash, " + Sql.num("h", d.pos) + " AS pos"
                + ", " + Sql.num("h", d.havaleh) + " AS havaleh, " + Sql.num("h", d.cheque) + " AS cheque"
                + ", " + (d.chkCount == null ? "CAST(0 AS int)" : "TRY_CONVERT(int,h.[" + d.chkCount + "])") + " AS chkCount"
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
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        List<Object> binds = new ArrayList<>();
        binds.add(ghno);
        String whereP = "TRY_CONVERT(nvarchar(60),d.[" + gh + "])=?";
        if (pp != null) { whereP += " AND d.[" + pp + "]=?"; binds.add(p); }
        String sql = "SELECT " + Sql.num("d", amount) + " AS amount"
                + ", " + (bankRef == null ? "CAST(NULL AS nvarchar(60))" : Sql.txt("d", bankRef, 60)) + " AS bankRef"
                + ", " + (bankRef == null || bankKey == null || bankName == null ? "N''"
                : "COALESCE(" + Sql.txt("b", bankName, 150) + ",N'')") + " AS bank"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("d", desc, 400) + ",N'')") + " AS descrip"
                + ", " + (track == null ? "N''" : "COALESCE(" + Sql.txt("d", track, 120) + ",N'')") + " AS tracking"
                + ", " + (hav == null ? "N''" : "COALESCE(" + Sql.txt("d", hav, 10) + ",N'')") + " AS isHavaleh"
                + " FROM dbo.PosDetails d"
                + (bankRef == null || bankKey == null || !m.table("BANK") ? ""
                : " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),d.[" + bankRef + "])")
                + " WHERE " + whereP;
        return new Queries.Q(sql, binds);
    }

    /** Cheques attached to a voucher (from getchk/putchk by ghno). */
    public static Queries.Q darCheques(Meta m, int p, String ghno) throws Queries.Missing {
        boolean incoming = p == 0;
        String table = incoming ? "getchk" : "putchk";
        if (!m.table(table)) throw new Queries.Missing("«" + AtiranSchema.faTitle(table) + "» در دیتابیس پیدا نشد");
        String gh = m.must(table, "شماره قبض", "ghno", "GHNO");
        String amount = incoming ? m.must(table, "مبلغ چک", "getchkmab") : m.must(table, "مبلغ چک", "putchkmab");
        String num = m.col(table, incoming ? "shgetchk" : "shputchk", "serial", "number");
        String bank = m.col(table, incoming ? "getchbank" : "bankrdf");
        String sar = m.col(table, "sardate");
        String st = m.col(table, incoming ? "chk_satus" : "putchk_status", "status");
        List<Object> binds = new ArrayList<>();
        binds.add(ghno);
        return new Queries.Q("SELECT " + (num == null ? "N'—'" : "COALESCE(" + Sql.txt("c", num, 80) + ",N'—')") + " AS num"
                + ", " + (bank == null ? "N''" : "COALESCE(" + Sql.txt("c", bank, 150) + ",N'')") + " AS bank"
                + ", " + Sql.num("c", amount) + " AS amount"
                + ", " + (sar == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("c", sar)) + " AS sardate"
                + ", " + (st == null ? "N''" : "COALESCE(" + Sql.txt("c", st, 40) + ",N'')") + " AS st"
                + " FROM dbo.[" + table + "] c WHERE TRY_CONVERT(nvarchar(60),c.[" + gh + "])=?", binds);
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
                    + ", " + darTotal("dr", d) + " AS total, CAST(0 AS decimal(19,2)) AS settled"
                    + " FROM dbo.dar dr WHERE dr.[p]=? AND TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "])=?"
                    + " AND NULLIF(TRY_CONVERT(nvarchar(80),dr.[" + d.shfac + "]),N'0') IS NOT NULL");
            binds.add(p);
            binds.add(no);
        }
        if (multiGh != null && multiNo != null && m.table(multi)) {
            parts.add("SELECT " + Sql.txt("dr", d.ghno, 60) + " AS ghno, " + Sql.date10("dr", d.date) + " AS date"
                    + ", " + darTotal("dr", d) + " AS total, " + Sql.num("mf", multiPrice) + " AS settled"
                    + " FROM dbo.[" + multi + "] mf JOIN dbo.dar dr"
                    + " ON TRY_CONVERT(nvarchar(60),dr.[" + d.ghno + "])=TRY_CONVERT(nvarchar(60),mf.[" + multiGh + "]) AND dr.[p]=?"
                    + " WHERE TRY_CONVERT(nvarchar(80),mf.[" + multiNo + "])=?");
            binds.add(p);
            binds.add(no);
        }
        if (parts.isEmpty()) throw new Queries.Missing("ارتباط فاکتور با قبوض قابل تشخیص نیست");
        return new Queries.Q(Sql.join(parts, " UNION ") + " ORDER BY 2 DESC", binds);
    }

    /** POS turnover by bank: bank,total,count. p<0 = both receipt & payment. */
    public static Queries.Q posByBank(Meta m, Filter f, int p) throws Queries.Missing {
        if (!m.table("PosDetails")) throw new Queries.Missing("«جزئیات کارت/حواله» در دیتابیس پیدا نشد");
        String amount = m.must("PosDetails", "مبلغ", "MabPos", "mabPos", "mablagh");
        String bankRef = m.must("PosDetails", "بانک", "PosBankRdf", "BankRdf", "bankrdf");
        String date = m.col("PosDetails", "date", "Date", "done_date", "DoneDate");
        String pp = m.col("PosDetails", "p");
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
        if (date != null) {
            String dc = Sql.dateCond(Sql.date10("d", date), f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT " + label + " AS bank, ISNULL(SUM(" + Sql.num("d", amount) + "),0) AS total"
                + ", COUNT_BIG(1) AS count FROM dbo.PosDetails d" + join + where
                + " GROUP BY " + label + " ORDER BY 2 DESC", binds);
    }

    // =====================================================================================
    // Cheques. Source = VW when fully usable, else the base table.
    // =====================================================================================
    public static final class Chq {
        String table, amount, num, bank, branch, acc, sardate, getdate, st, back, ourBank, ghno;
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
            boolean ok = incoming
                    ? m.col(vw, "getchkmab") != null && m.col(vw, "chk_satus") != null
                    : m.col(vw, "putchkmab") != null && m.col(vw, "putchk_status") != null;
            if (ok) use = vw;
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
        return c;
    }

    private static String chqStatusLabel(Meta m, Chq c, String a) {
        if (c.statusLabel != null) return "COALESCE(" + Sql.txt(a, c.statusLabel, 120) + ",N'')";
        // CheckTypes(ID, Desciption) joined on the raw status (legacy behaviour).
        String id = m.col("CheckTypes", "ID", "id");
        String nm = m.col("CheckTypes", "Desciption", "Description", "name");
        if (id != null && nm != null && m.table("CheckTypes"))
            return "COALESCE((SELECT TOP (1) " + Sql.txt("t", nm, 120) + " FROM dbo.CheckTypes t"
                    + " WHERE TRY_CONVERT(nvarchar(50),t.[" + id + "])=TRY_CONVERT(nvarchar(50)," + a + ".[" + c.st + "])),N'')";
        return "N''";
    }

    /** Grouped statuses for bucket cards: st,back,hasBank,count,total. */
    public static Queries.Q chequeGroups(Meta m, boolean incoming, Filter f) throws Queries.Missing {
        Chq c = chq(m, incoming);
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (c.getdate != null) {
            // Cheque dates may be Jalali text or Gregorian datetime — match the range in both.
            String dc = Sql.dateCond(Sql.date10("h", c.getdate), f.from, f.to, binds);
            String dg = Sql.dateCond(Sql.date10("h", c.getdate), Jalali.toGregorian(f.from), Jalali.toGregorian(f.to), binds);
            if (!dc.isEmpty() && !dg.isEmpty()) conds.add("(" + dc + " OR " + dg + ")");
            else if (!dc.isEmpty()) conds.add(dc);
            else if (!dg.isEmpty()) conds.add(dg);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String back = c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')";
        String bank = c.ourBank == null ? "CAST(0 AS int)"
                : "CASE WHEN TRY_CONVERT(int,h.[" + c.ourBank + "])>0 THEN 1 ELSE 0 END";
        return new Queries.Q("SELECT COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st, " + back + " AS back"
                + ", " + bank + " AS hasBank, COUNT_BIG(1) AS count"
                + ", ISNULL(SUM(" + Sql.num("h", c.amount) + "),0) AS total"
                + " FROM dbo.[" + c.table + "] h" + where + " GROUP BY COALESCE(" + Sql.txt("h", c.st, 40) + ",N''), "
                + back + ", " + bank, binds);
    }

    /**
     * Cheque list. bucket: "" all; incoming: sandogh/bank/vosool/kharj/bargashti;
     * outgoing: jari/pas/bargashti/enteghal/sefid.
     */
    public static Queries.Q chequeList(Meta m, boolean incoming, Filter f, String bucket) throws Queries.Missing {
        Chq c = chq(m, incoming);
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (c.getdate != null) {
            // Cheque dates may be Jalali text or Gregorian datetime — match the range in both.
            String dc = Sql.dateCond(Sql.date10("h", c.getdate), f.from, f.to, binds);
            String dg = Sql.dateCond(Sql.date10("h", c.getdate), Jalali.toGregorian(f.from), Jalali.toGregorian(f.to), binds);
            if (!dc.isEmpty() && !dg.isEmpty()) conds.add("(" + dc + " OR " + dg + ")");
            else if (!dc.isEmpty()) conds.add(dc);
            else if (!dg.isEmpty()) conds.add(dg);
        }
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        String back = c.back == null ? null : "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "]))))";
        String banked = c.ourBank == null ? null : "ISNULL(TRY_CONVERT(int,h.[" + c.ourBank + "]),0)>0";
        String b = bucket == null ? "" : bucket;
        if (incoming) {
            if ("sandogh".equals(b)) { conds.add(st + "=N'0'"); if (banked != null) conds.add("NOT (" + banked + ")"); if (back != null) conds.add(back + " NOT IN (N'T',N'1')"); }
            else if ("bank".equals(b)) {
                if (banked == null) throw new Queries.Missing("ستون بانک ما در «چک‌های دریافتی» پیدا نشد");
                conds.add(banked); conds.add(st + "=N'0'");
            }
            else if ("vosool".equals(b)) conds.add(st + "=N'1'");
            else if ("kharj".equals(b)) conds.add(st + "=N'3'");
            else if ("bargashti".equals(b)) conds.add("(" + st + "=N'2'" + (back == null ? "" : " OR " + back + " IN (N'T',N'1')") + ")");
        } else {
            if ("jari".equals(b)) conds.add(st + "=N'0'");
            else if ("pas".equals(b)) conds.add(st + "=N'1'");
            else if ("bargashti".equals(b)) conds.add("(" + st + "=N'2'" + (back == null ? "" : " OR " + back + " IN (N'T',N'1')") + ")");
            else if ("enteghal".equals(b)) conds.add(st + "=N'3'");
            else if ("sefid".equals(b)) conds.add(st + "=N'4'");
        }
        if (f.bank >= 0 && c.ourBank != null) { conds.add("TRY_CONVERT(int,h.[" + c.ourBank + "])=?"); binds.add(f.bank); }
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            if (c.num != null) exprs.add(Sql.txt("h", c.num, 80));
            if (c.bank != null) exprs.add(Sql.txt("h", c.bank, 150));
            if (c.sayad != null) exprs.add(Sql.txt("h", c.sayad, 60));
            if (c.custName != null) exprs.add(Sql.txt("h", c.custName, 250));
            else {
                String nm = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
                if (nm != null && c.shmo != null) exprs.add(Sql.txt("cu", nm, 250));
            }
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        boolean needCust = !c.isView && c.shmo != null;
        String join = needCust ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        String codeExpr = c.custCode != null ? Sql.txt("h", c.custCode, 100)
                : (c.shmo == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", c.shmo, 100));
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String orderCol = c.sardate != null ? "CASE WHEN h.[" + c.sardate + "] IS NULL THEN 1 ELSE 0 END, h.[" + c.sardate + "] DESC"
                : (c.getdate != null ? "CASE WHEN h.[" + c.getdate + "] IS NULL THEN 1 ELSE 0 END, h.[" + c.getdate + "] DESC" : "1");
        String sql = "SELECT " + Sql.txt("h", "rdf", 60) + " AS rdf"
                + ", " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("h", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + (c.branch == null ? "N''" : "COALESCE(" + Sql.txt("h", c.branch, 200) + ",N'')") + " AS branch"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + (c.sardate == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", c.sardate)) + " AS sardate"
                + ", " + (c.getdate == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", c.getdate)) + " AS getdate"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + ", " + (c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')") + " AS back"
                + ", " + chqStatusLabel(m, c, "h") + " AS statusLabel"
                + ", " + codeExpr + " AS code, " + custExpr + " AS customer"
                + ", " + (c.sayad == null ? "N''" : "COALESCE(" + Sql.txt("h", c.sayad, 60) + ",N'')") + " AS sayad"
                + ", " + (c.desc == null ? "N''" : "COALESCE(" + Sql.txt("h", c.desc, 500) + ",N'')") + " AS descrip"
                + ", " + (c.ghno == null ? "N''" : "COALESCE(" + Sql.txt("h", c.ghno, 60) + ",N'')") + " AS ghno"
                + " FROM dbo.[" + c.table + "] h" + join + where
                + " ORDER BY " + orderCol + Queries.pageClause(binds, f.page, f.top);
        // «rdf» may be named differently on the view; fall back gracefully.
        if (m.col(c.table, "rdf") == null) sql = sql.replace(Sql.txt("h", "rdf", 60) + " AS rdf", "CAST(NULL AS nvarchar(60)) AS rdf");
        return new Queries.Q(sql, binds);
    }

    /** Cheques due within daysAhead (still outstanding): same columns as the list. */
    public static Queries.Q chequeDue(Meta m, boolean incoming, int daysAhead) throws Queries.Missing {
        Chq c = chq(m, incoming);
        if (c.sardate == null) throw new Queries.Missing("ستون سررسید در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        String today = Jalali.todayStr();
        String until = Jalali.addDays(today, Math.max(0, daysAhead));
        List<Object> binds = new ArrayList<>();
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        List<String> conds = new ArrayList<>();
        // Due dates may be Jalali text or Gregorian datetime — match the window in both.
        String sar = Sql.date10("h", c.sardate);
        String d1 = Sql.dateCond(sar, today, until, binds);
        String d2 = Sql.dateCond(sar, Jalali.todayGregorian(), Jalali.toGregorian(until), binds);
        conds.add("(" + d1 + " OR " + d2 + ")");
        conds.add(st + "=N'0'");
        if (c.back != null) conds.add("UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "])))) NOT IN (N'T',N'1')");
        boolean needCust = !c.isView && c.shmo != null;
        String join = needCust ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        return new Queries.Q("SELECT " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("h", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + Sql.date10("h", c.sardate) + " AS sardate"
                + ", " + custExpr + " AS customer"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + " FROM dbo.[" + c.table + "] h" + join + " WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY h.[" + c.sardate + "]", binds);
    }
}
