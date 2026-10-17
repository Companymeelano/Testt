package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Accounting reports over Atiran's accounting schema:
 * accounting.Document (سند) → accounting.DocumentDetails (ردیف سند: کل / معین / تفصیلی، بدهکار / بستانکار).
 *
 * Classes follow the standard Iranian chart by the first digit of the Kol code:
 * 1 دارایی (debit-natural), 2 بدهی, 3 حقوق صاحبان سهام, 4 درآمد, 5 هزینه (credit-natural for 2–4).
 * Every query checks its tables and columns first and reports a Persian "پیدا نشد" message when absent.
 */
public final class AccountingReports {
    private AccountingReports() { }

    /** Line-level columns used by the voucher detail dialog. */
    public static final ReportCatalog.Col[] LINE_COLS = {
            new ReportCatalog.Col("kol", "کل", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("moein", "معین", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("tafsil", "تفصیلی", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("descr", "شرح", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("debit", "بدهکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("credit", "بستانکار", ReportCatalog.T_MONEY),
    };

    /** Columns of the balance reports (chart of accounts and class reports). */
    public static final ReportCatalog.Col[] BALANCE_COLS = {
            new ReportCatalog.Col("code", "کد", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("label", "عنوان", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("debit", "بدهکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("credit", "بستانکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("balance", "مانده", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("docs", "ردیف", ReportCatalog.T_NUM),
    };

    // ------------------------------------------------------------------ helpers

    private static String table(Meta m, String name, String faWhat) throws Queries.Missing {
        String q = m.qTable(name);
        if (q == null) throw new Queries.Missing("«" + faWhat + "» در دیتابیس پیدا نشد");
        return q;
    }

    private static String findCol(Set<String> cols, String... candidates) {
        return Sql.pick(cols, candidates);
    }

    /** Range condition on a Jalali date column, or "" when the filter has no range. */
    private static String dateWhere(String dateExpr, Filter f, List<Object> binds) {
        String dc = Sql.dateCond(dateExpr, f.from, f.to, binds);
        return dc == null || dc.isEmpty() ? "" : " AND " + dc;
    }

    /** Free-text search over the given expressions (no-op when the search box is empty). */
    private static String searchWhere(Filter f, List<Object> binds, String... exprs) {
        String s = f.search == null ? "" : f.search.trim();
        if (s.isEmpty() || exprs.length == 0) return "";
        StringBuilder b = new StringBuilder(" AND (");
        for (int i = 0; i < exprs.length; i++) {
            if (i > 0) b.append(" OR ");
            b.append(exprs[i]).append(" LIKE ?");
            binds.add("%" + s + "%");
        }
        return b.append(")").toString();
    }

    /** Excludes soft-deleted rows when the table carries an Active flag. */
    private static String activeWhere(Set<String> cols, String alias) {
        String a = findCol(cols, "Active");
        if (a == null) return "";
        return " AND (" + alias + "." + Sql.q(a) + " IS NULL OR " + alias + "." + Sql.q(a) + "<>0)";
    }

    /** Shared FROM: details joined to their voucher, with the required columns resolved. */
    private static final class Src {
        final String doc;      // [accounting].[Document]
        final String det;      // [accounting].[DocumentDetails]
        final String dDocId, dDate, dNo, dDesc;
        final String rDocId, rBed, rBes, rKolCode, rKolName, rMoeinCode, rMoeinName,
                rTafsil, rDesc, rRowId;
        final Set<String> dCols, rCols;

        Src(Meta m) throws Queries.Missing {
            doc = table(m, "Document", "سند حسابداری");
            det = table(m, "DocumentDetails", "ردیف سند حسابداری");
            dCols = m.columns("Document");
            rCols = m.columns("DocumentDetails");
            dDocId = need(dCols, "DocID", "شناسه سند");
            dDate = need(dCols, "DoDate", "تاریخ سند");
            dNo = need(dCols, "DocNumber", "شماره سند");
            dDesc = findCol(dCols, "DocDesc");
            rDocId = need(rCols, "DocID", "شناسه سند");
            rBed = need(rCols, "Bed", "بدهکار");
            rBes = need(rCols, "Bes", "بستانکار");
            rKolCode = need(rCols, "kolCode", "کد کل");
            rKolName = need(rCols, "kolName", "نام کل");
            rMoeinCode = need(rCols, "moeinCode", "کد معین");
            rMoeinName = need(rCols, "moeinName", "نام معین");
            rTafsil = findCol(rCols, "tafsilName");
            rDesc = findCol(rCols, "Desc");
            rRowId = findCol(rCols, "RowID");
        }

        private static String need(Set<String> cols, String name, String fa) throws Queries.Missing {
            String c = Sql.pick(cols, name);
            if (c == null) throw new Queries.Missing("ستون «" + fa + "» پیدا نشد");
            return c;
        }

        /** Column reference with alias, e.g. dd.[Bed]. */
        static String ref(String alias, String col) {
            return alias + "." + Sql.q(col);
        }

        String from() {
            return " FROM " + det + " dd JOIN " + doc + " d ON d." + Sql.q(dDocId) + "=dd." + Sql.q(rDocId);
        }
    }

    // ------------------------------------------------------------------ reports

    /** Chart of accounts with balances per معین (تراز آزمایشی به تفکیک سرفصل). */
    public static Queries.Q chart(Meta m, Filter f) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        String where = " WHERE 1=1" + activeWhere(s.rCols, "dd")
                + dateWhere(Sql.date10("d", s.dDate), f, binds)
                + searchWhere(f, binds, "dd." + Sql.q(s.rMoeinName), "dd." + Sql.q(s.rKolName),
                "dd." + Sql.q(s.rMoeinCode));
        String sql = "SELECT RTRIM(LTRIM(dd." + Sql.q(s.rMoeinCode) + ")) AS code,"
                + " dd." + Sql.q(s.rMoeinName) + " AS label,"
                + " dd." + Sql.q(s.rKolName) + " AS kol,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBed) + ",0)) AS debit,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBes) + ",0)) AS credit,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBed) + ",0)-ISNULL(dd." + Sql.q(s.rBes) + ",0)) AS balance,"
                + " COUNT_BIG(1) AS docs"
                + s.from() + where
                + " GROUP BY RTRIM(LTRIM(dd." + Sql.q(s.rMoeinCode) + ")), dd." + Sql.q(s.rMoeinName)
                + ", dd." + Sql.q(s.rKolName)
                + " ORDER BY code";
        return new Queries.Q(sql, binds);
    }

    /**
     * Totals per کل for one class. classDigit: "1" دارایی, "2" بدهی, "3" حقوق صاحبان سهام,
     * "4" درآمد, "5" هزینه. Balance is shown in the class's natural sign.
     */
    public static Queries.Q byClass(Meta m, Filter f, String classDigit) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        boolean debitNatural = "1".equals(classDigit) || "5".equals(classDigit);
        String natural = debitNatural
                ? "SUM(ISNULL(dd." + Sql.q(s.rBed) + ",0)-ISNULL(dd." + Sql.q(s.rBes) + ",0))"
                : "SUM(ISNULL(dd." + Sql.q(s.rBes) + ",0)-ISNULL(dd." + Sql.q(s.rBed) + ",0))";
        binds.add(classDigit);
        String where = " WHERE LEFT(LTRIM(RTRIM(dd." + Sql.q(s.rKolCode) + ")),1)=?"
                + activeWhere(s.rCols, "dd")
                + dateWhere(Sql.date10("d", s.dDate), f, binds)
                + searchWhere(f, binds, "dd." + Sql.q(s.rKolName), "dd." + Sql.q(s.rKolCode));
        String sql = "SELECT RTRIM(LTRIM(dd." + Sql.q(s.rKolCode) + ")) AS code,"
                + " dd." + Sql.q(s.rKolName) + " AS label,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBed) + ",0)) AS debit,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBes) + ",0)) AS credit,"
                + " " + natural + " AS balance,"
                + " COUNT_BIG(1) AS docs"
                + s.from() + where
                + " GROUP BY RTRIM(LTRIM(dd." + Sql.q(s.rKolCode) + ")), dd." + Sql.q(s.rKolName)
                + " ORDER BY code";
        return new Queries.Q(sql, binds);
    }

    /** Voucher list: number, date, description, line count and totals. Tap a row for its lines. */
    public static Queries.Q journal(Meta m, Filter f) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        String descExpr = s.dDesc == null ? "N''" : "d." + Sql.q(s.dDesc);
        String where = " WHERE 1=1" + activeWhere(s.dCols, "d")
                + dateWhere(Sql.date10("d", s.dDate), f, binds)
                + searchWhere(f, binds, descExpr, "CAST(d." + Sql.q(s.dNo) + " AS nvarchar(20))");
        String sql = "SELECT TOP (500) d." + Sql.q(s.dDocId) + " AS docId,"
                + " d." + Sql.q(s.dNo) + " AS no,"
                + " d." + Sql.q(s.dDate) + " AS date,"
                + " MAX(COALESCE(CAST(" + descExpr + " AS nvarchar(300)),N'')) AS descr,"
                + " COUNT_BIG(dd." + Sql.q(s.rDocId) + ") AS lines,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBed) + ",0)) AS debit,"
                + " SUM(ISNULL(dd." + Sql.q(s.rBes) + ",0)) AS credit"
                + " FROM " + s.doc + " d LEFT JOIN " + s.det + " dd ON dd." + Sql.q(s.rDocId) + "=d." + Sql.q(s.dDocId)
                + where
                + " GROUP BY d." + Sql.q(s.dDocId) + ", d." + Sql.q(s.dNo) + ", d." + Sql.q(s.dDate)
                + " ORDER BY d." + Sql.q(s.dNo) + " DESC";
        return new Queries.Q(sql, binds);
    }

    /** Lines of one voucher (detail dialog). */
    public static Queries.Q docLines(Meta m, long docId) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        binds.add(docId);
        String tafsil = s.rTafsil == null ? "N''" : "dd." + Sql.q(s.rTafsil);
        String desc = s.rDesc == null ? "N''" : "dd." + Sql.q(s.rDesc);
        String order = s.rRowId == null ? "" : " ORDER BY dd." + Sql.q(s.rRowId);
        String sql = "SELECT dd." + Sql.q(s.rKolName) + " AS kol,"
                + " dd." + Sql.q(s.rMoeinName) + " AS moein,"
                + " COALESCE(CAST(" + tafsil + " AS nvarchar(200)),N'') AS tafsil,"
                + " COALESCE(CAST(" + desc + " AS nvarchar(300)),N'') AS descr,"
                + " ISNULL(dd." + Sql.q(s.rBed) + ",0) AS debit,"
                + " ISNULL(dd." + Sql.q(s.rBes) + ",0) AS credit"
                + " FROM " + s.det + " dd WHERE dd." + Sql.q(s.rDocId) + "=?" + order;
        return new Queries.Q(sql, binds);
    }

    /** Sarfasl (سرفصل) ledger: turnover per سرفصل / زیرسرفصل from the reporting view. */
    public static Queries.Q sarfaslLedger(Meta m, Filter f) throws Queries.Missing {
        String v = table(m, "VW_ReportGardeshAmaliatZirSarFasl", "گردش سرفصل‌ها");
        Set<String> cols = m.columns("VW_ReportGardeshAmaliatZirSarFasl");
        String dateC = need(cols, "date", "تاریخ");
        String bed = need(cols, "bed", "بدهکار");
        String bes = need(cols, "bes", "بستانکار");
        String sar = need(cols, "NameSarfasl", "نام سرفصل");
        String zir = need(cols, "NameZirsarfasl", "نام زیرسرفصل");
        List<Object> binds = new ArrayList<>();
        String where = " WHERE 1=1"
                + dateWhere(Sql.date10("v", dateC), f, binds)
                + searchWhere(f, binds, "v." + Sql.q(sar), "v." + Sql.q(zir));
        String sql = "SELECT v." + Sql.q(sar) + " AS sarfasl, v." + Sql.q(zir) + " AS zir,"
                + " SUM(ISNULL(v." + Sql.q(bed) + ",0)) AS debit,"
                + " SUM(ISNULL(v." + Sql.q(bes) + ",0)) AS credit,"
                + " SUM(ISNULL(v." + Sql.q(bed) + ",0)-ISNULL(v." + Sql.q(bes) + ",0)) AS balance,"
                + " COUNT_BIG(1) AS docs"
                + " FROM " + v + " v" + where
                + " GROUP BY v." + Sql.q(sar) + ", v." + Sql.q(zir)
                + " ORDER BY v." + Sql.q(sar) + ", v." + Sql.q(zir);
        return new Queries.Q(sql, binds);
    }

    private static String need(Set<String> cols, String name, String fa) throws Queries.Missing {
        String c = Sql.pick(cols, name);
        if (c == null) throw new Queries.Missing("ستون «" + fa + "» پیدا نشد");
        return c;
    }
}
