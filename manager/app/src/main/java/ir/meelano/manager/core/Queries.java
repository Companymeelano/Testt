package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * THE query registry: every SELECT the app runs is built here, on top of
 * {@link AtiranSchema} and resolved at runtime through {@link Meta}.
 * Screens never hand-write SQL. All queries are read-only, parameterised and
 * SQL-Server-2014 compatible.
 */
public final class Queries {
    private Queries() { }

    /** A built, parameterised query. */
    public static final class Q {
        public final String sql;
        public final List<Object> binds = new ArrayList<>();

        public Q(String sql) { this.sql = sql; }

        public Q(String sql, List<Object> binds) {
            this.sql = sql;
            if (binds != null) this.binds.addAll(binds);
        }
    }

    /** A required table/column is absent. Message is already Persian. */
    public static final class Missing extends Exception {
        public Missing(String fa) { super(fa); }
    }

    // =====================================================================================
    // Shared builders (also used by MoneyQueries / MasterQueries).
    // =====================================================================================
    public static int clampTop(int top) {
        return Math.max(20, Math.min(500, top <= 0 ? 200 : top));
    }

    public static String pageClause(List<Object> binds, int page, int top) {
        top = clampTop(top);
        binds.add(Math.max(0, page) * top);
        binds.add(top);
        return " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    }

    /** Persian/Arabic ي ك variants of a search term. */
    public static List<String> searchVariants(String s) {
        List<String> out = new ArrayList<>();
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return out;
        out.add(t);
        String v = t.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه');
        if (!v.equals(t)) out.add(v);
        String v2 = t.replace('ی', 'ي').replace('ک', 'ك');
        if (!v2.equals(t) && !v2.equals(v)) out.add(v2);
        return out;
    }

    /** (expr LIKE N'%v%' OR …) across expressions × variants. */
    public static String searchCond(List<Object> binds, String search, String... exprs) {
        List<String> vars = searchVariants(search);
        if (vars.isEmpty() || exprs == null || exprs.length == 0) return "";
        List<String> parts = new ArrayList<>();
        for (String e : exprs) {
            if (e == null || e.isEmpty()) continue;
            for (String v : vars) { parts.add(e + " LIKE N'%' + ? + N'%'"); binds.add(v); }
        }
        return parts.isEmpty() ? "" : "(" + Sql.join(parts, " OR ") + ")";
    }

    /** LEFT JOIN CUSTOMERS name expression (falls back to the raw code). */
    public static String custNameExpr(Meta m, String alias, String shmoCol) {
        return custNameExpr(m, alias, shmoCol, "cu");
    }

    public static String custNameExpr(Meta m, String alias, String shmoCol, String joinAlias) {
        String key = m.col("CUSTOMERS", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String code = Sql.txt(alias, shmoCol, 120);
        if (key == null) return "COALESCE(" + code + ",N'—')";
        String nm = name == null ? "CAST(NULL AS nvarchar(250))" : Sql.txt(joinAlias, name, 250);
        return "COALESCE(" + nm + "," + code + ",N'—')";
    }

    public static String custJoin(Meta m, String alias, String shmoCol) {
        return custJoin(m, alias, shmoCol, "cu");
    }

    public static String custJoin(Meta m, String alias, String shmoCol, String joinAlias) {
        String key = m.col("CUSTOMERS", "SHMO", "shmo");
        if (key == null || shmoCol == null || !m.table("CUSTOMERS")) return "";
        return " LEFT JOIN dbo.CUSTOMERS " + joinAlias + " ON TRY_CONVERT(nvarchar(100)," + joinAlias + ".[" + key + "])=TRY_CONVERT(nvarchar(100)," + alias + ".[" + shmoCol + "])";
    }

    /** LEFT JOIN visitors name expression (falls back to «بدون ویزیتور»). */
    public static String visNameExpr(Meta m, String alias, String visCol) {
        return visNameExpr(m, alias, visCol, "v");
    }

    public static String visNameExpr(Meta m, String alias, String visCol, String joinAlias) {
        String name = m.col("visitors", "vis_name", "name", "Name", "VisitorName");
        String nm = name == null ? "CAST(NULL AS nvarchar(150))" : Sql.txt(joinAlias, name, 150);
        return "COALESCE(" + nm + ",N'بدون ویزیتور')";
    }

    public static String visJoin(Meta m, String alias, String visCol) {
        return visJoin(m, alias, visCol, "v");
    }

    public static String visJoin(Meta m, String alias, String visCol, String joinAlias) {
        String key = m.col("visitors", "vis_rdf", "rdf", "RDF", "id", "ID");
        if (key == null || visCol == null || !m.table("visitors")) return "";
        return " LEFT JOIN dbo.visitors " + joinAlias + " ON TRY_CONVERT(nvarchar(100)," + joinAlias + ".[" + key + "])=TRY_CONVERT(nvarchar(100)," + alias + ".[" + visCol + "])";
    }

    /** ActNames label for an act_id column (NULL when unresolvable — UI falls back). */
    public static String actLabelExpr(Meta m, String alias, String actCol, String joinAlias) {
        String id = m.col("ActNames", "ActID", "act_id", "ActId", "ID", "id");
        String nm = m.colFlex("ActNames", "name", "ActName", "title", "onvan", "description");
        if (id == null || nm == null || actCol == null || !m.table("ActNames")) return "CAST(NULL AS nvarchar(200))";
        return "TRY_CONVERT(nvarchar(200)," + joinAlias + ".[" + nm + "])";
    }

    public static String actLabelJoin(Meta m, String alias, String actCol, String joinAlias) {
        String id = m.col("ActNames", "ActID", "act_id", "ActId", "ID", "id");
        String nm = m.colFlex("ActNames", "name", "ActName", "title", "onvan", "description");
        if (id == null || nm == null || actCol == null || !m.table("ActNames")) return "";
        return " LEFT JOIN dbo.ActNames " + joinAlias + " ON TRY_CONVERT(nvarchar(50)," + joinAlias + ".[" + id + "])=TRY_CONVERT(nvarchar(50)," + alias + ".[" + actCol + "])";
    }

    // =====================================================================================
    // HOME — day blocks anchored on the latest date WITH data (returned as «d»).
    // =====================================================================================
    /** Sales day block: d,total,docs,parties,paid,discount,tax. */
    public static Q homeSalesDay(Meta m) throws Missing {
        return factorDay(m, true, null);
    }

    /** Purchase day block. */
    public static Q homeBuyDay(Meta m) throws Missing {
        return factorDay(m, false, null);
    }

    private static Q factorDay(Meta m, boolean sales, String day) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = Sql.pick(cols, sales ? "shfacfo" : "shfackh");
        String partyCol = Sql.pick(cols, "shmo", "SHMO");
        String paidCol = sales ? Sql.pick(cols, "MabDaryaftFactor", "Daryaft") : Sql.pick(cols, "MablaghPardakht", "Pardakht");
        String discountCol = Sql.pick(cols, "tafif", "takhfif");
        String taxCol = Sql.pick(cols, "tax", "Tax");
        String d = day != null ? day : m.latestDate(table, dateCol);
        List<Object> binds = new ArrayList<>();
        String inner = "WHERE " + Sql.date10("x", dateCol) + "=?";
        binds.add(d == null ? "" : d);
        inner += Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe(table, numberCol, "h", inner);
        String sql = "SELECT " + Sql.lit(d == null ? "" : d) + " AS d"
                + ", ISNULL(SUM(" + Sql.num("h", amountCol) + "),0) AS total"
                + ", COUNT_BIG(1) AS docs"
                + ", " + (partyCol == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT h.[" + partyCol + "])") + " AS parties"
                + ", ISNULL(SUM(" + Sql.num("h", paidCol) + "),0) AS paid"
                + ", ISNULL(SUM(" + Sql.num("h", discountCol) + "),0) AS discount"
                + ", ISNULL(SUM(" + Sql.num("h", taxCol) + "),0) AS tax"
                + " FROM " + src;
        return new Q(sql, binds);
    }

    /** Per-day sales/purchases in a range: day,total,docs (UI fills missing days with 0). */
    public static Q factorDaily(Meta m, boolean sales, String from, String to) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = Sql.pick(cols, sales ? "shfacfo" : "shfackh");
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), from, to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe(table, numberCol, "h", inner);
        String day = Sql.date10("h", dateCol);
        return new Q("SELECT " + day + " AS day, ISNULL(SUM(" + Sql.num("h", amountCol) + "),0) AS total"
                + ", COUNT_BIG(1) AS docs FROM " + src + " GROUP BY " + day + " ORDER BY " + day, binds);
    }

    /** Top debtors: code,party,amount. */
    public static Q topDebtors(Meta m, int top) throws Missing {
        String bal = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String label = name == null ? Sql.txt("c", shmo, 120) : "COALESCE(" + Sql.txt("c", name, 250) + "," + Sql.txt("c", shmo, 120) + ")";
        return new Q("SELECT TOP (" + clampTop(top) + ") " + Sql.txt("c", shmo, 100) + " AS code, "
                + label + " AS party, TRY_CONVERT(decimal(19,2),c.[" + bal + "]) AS amount"
                + " FROM dbo.CUSTOMERS c WHERE TRY_CONVERT(decimal(19,2),c.[" + bal + "])>0 ORDER BY 3 DESC");
    }

    /**
     * Overdue invoices: code,party,amount,dueDate,days(-1 when unknown),visitor,invoice.
     * Uses dbo.dif_date_alan when present, else Jalali string comparison (days computed on device).
     */
    public static Q overdueInvoices(Meta m, int top) throws Missing {
        Set<String> sail = m.columns("sailfact");
        m.must("sailfact", "تاریخ تسویه", "t_date");
        m.must("sailfact", "وضعیت تسویه", "tasvieh");
        m.must("sailfact", "مبلغ", "all");
        String shmo = Sql.pick(sail, "shmo", "SHMO");
        String number = Sql.pick(sail, "shfacfo");
        String visitorId = Sql.pick(sail, "vis_rdf", "VisitorID");
        String nameExpr = custNameExpr(m, "s", shmo, "c");
        String joinSql = custJoin(m, "s", shmo, "c") + visJoin(m, "s", visitorId, "v");
        String visitorExpr = visitorId == null ? "N'بدون ویزیتور'" : visNameExpr(m, "s", visitorId, "v");
        String numExpr = number == null ? "CAST(NULL AS nvarchar(80))" : "TRY_CONVERT(nvarchar(80),s.[" + number + "])";
        String codeExpr = shmo == null ? "CAST(NULL AS nvarchar(100))" : "TRY_CONVERT(nvarchar(100),s.[" + shmo + "])";
        List<String> where = new ArrayList<>();
        where.add("s.[tasvieh]='f'");
        where.add("NULLIF(s.[t_date],'') IS NOT NULL");
        boolean hasFn = m.function("dif_date_alan");
        String daysExpr;
        if (hasFn) {
            where.add("dbo.dif_date_alan(s.[t_date])<0");
            daysExpr = "-dbo.dif_date_alan(s.[t_date])";
        } else {
            where.add("LEFT(TRY_CONVERT(nvarchar(30),s.[t_date]),10)<" + Sql.lit(Jalali.todayStr()));
            daysExpr = "CAST(-1 AS bigint)";
        }
        String sql = "SELECT TOP (" + clampTop(top) + ") " + codeExpr + " AS code, " + nameExpr + " AS party"
                + ", TRY_CONVERT(decimal(19,2),s.[all]) AS amount"
                + ", LEFT(TRY_CONVERT(nvarchar(30),s.[t_date]),10) AS dueDate, " + daysExpr + " AS days"
                + ", " + visitorExpr + " AS visitor, " + numExpr + " AS invoice"
                + " FROM dbo.sailfact s" + joinSql + " WHERE " + Sql.join(where, " AND ")
                + Sql.activeAnd(sail, "s")
                + (hasFn ? " ORDER BY -dbo.dif_date_alan(s.[t_date]) DESC" : " ORDER BY s.[t_date]");
        return new Q(sql);
    }

    /** Customers with no sales invoice at all: code,party. */
    public static Q inactiveCustomers(Meta m, int top) throws Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String sailShmo = m.must("sailfact", "کد مشتری", "shmo", "SHMO");
        String label = name == null ? Sql.txt("c", shmo, 120)
                : "COALESCE(" + Sql.txt("c", name, 250) + "," + Sql.txt("c", shmo, 120) + ")";
        return new Q("SELECT TOP (" + clampTop(top) + ") " + Sql.txt("c", shmo, 100) + " AS code, "
                + label + " AS party FROM dbo.CUSTOMERS c WHERE NOT EXISTS (SELECT 1 FROM dbo.sailfact s"
                + " WHERE TRY_CONVERT(nvarchar(100),s.[" + sailShmo + "])=TRY_CONVERT(nvarchar(100),c.[" + shmo + "]))"
                + " ORDER BY " + label);
    }

    /** Unsettled invoices grouped by due month (for aging when dif_date_alan is absent): month,amount,count. */
    public static Q unsettledByMonth(Meta m) throws Missing {
        m.must("sailfact", "تاریخ تسویه", "t_date");
        m.must("sailfact", "وضعیت تسویه", "tasvieh");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        Set<String> cols = m.columns("sailfact");
        String mon = Sql.month7("s", "t_date");
        return new Q("SELECT " + mon + " AS month, ISNULL(SUM(" + Sql.num("s", amountCol) + "),0) AS amount"
                + ", COUNT_BIG(1) AS count FROM dbo.sailfact s WHERE s.[tasvieh]='f'"
                + " AND NULLIF(s.[t_date],'') IS NOT NULL" + Sql.activeAnd(cols, "s")
                + " GROUP BY " + mon + " ORDER BY " + mon);
    }

    /** Aging buckets via dif_date_alan (only call when the function exists): k,amount,count. */
    public static Q agingBuckets(Meta m) throws Missing {
        if (!m.function("dif_date_alan")) throw new Missing("تابع محاسبه سررسید در دیتابیس نیست");
        m.must("sailfact", "تاریخ تسویه", "t_date");
        m.must("sailfact", "وضعیت تسویه", "tasvieh");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        return new Q("SELECT k AS k, ISNULL(SUM(amount),0) AS amount, COUNT_BIG(1) AS count FROM (SELECT"
                + " CASE WHEN dbo.dif_date_alan([t_date])<0 THEN 0 WHEN dbo.dif_date_alan([t_date])<=30 THEN 1"
                + " WHEN dbo.dif_date_alan([t_date])<=60 THEN 2 WHEN dbo.dif_date_alan([t_date])<=90 THEN 3 ELSE 4 END AS k"
                + ", TRY_CONVERT(decimal(19,2),[all]) AS amount FROM dbo.sailfact"
                + " WHERE [tasvieh]='f' AND NULLIF([t_date],'') IS NOT NULL) g GROUP BY k ORDER BY k");
    }

    // =====================================================================================
    // SALES + PURCHASES
    // =====================================================================================
    /** Summary: total,docs,parties,paid,discount,tax,remain. */
    public static Q factorSummary(Meta m, boolean sales, Filter f) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = Sql.pick(cols, sales ? "shfacfo" : "shfackh");
        String partyCol = Sql.pick(cols, "shmo", "SHMO");
        String paidCol = sales ? Sql.pick(cols, "MabDaryaftFactor", "Daryaft") : Sql.pick(cols, "MablaghPardakht", "Pardakht");
        String discountCol = Sql.pick(cols, "tafif", "takhfif");
        String taxCol = Sql.pick(cols, "tax", "Tax");
        String visCol = sales ? Sql.pick(cols, "vis_rdf", "VisitorID") : null;
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        if (sales && f.visitor >= 0 && visCol != null) { conds.add("TRY_CONVERT(int,x.[" + visCol + "])=?"); binds.add(f.visitor); }
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe(table, numberCol, "h", inner);
        String total = "ISNULL(SUM(" + Sql.num("h", amountCol) + "),0)";
        String paid = "ISNULL(SUM(" + Sql.num("h", paidCol) + "),0)";
        return new Q("SELECT " + total + " AS total, COUNT_BIG(1) AS docs"
                + ", " + (partyCol == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT h.[" + partyCol + "])") + " AS parties"
                + ", " + paid + " AS paid"
                + ", ISNULL(SUM(" + Sql.num("h", discountCol) + "),0) AS discount"
                + ", ISNULL(SUM(" + Sql.num("h", taxCol) + "),0) AS tax"
                + ", (" + total + "-" + paid + ") AS remain FROM " + src, binds);
    }

    /**
     * Invoice list: no,date,code,customer,visitor,total,paid,remain,settled,tasvieh,dueDate,desc.
     * status filter: "" all | "settled" | "unsettled".
     */
    public static Q factorList(Meta m, boolean sales, Filter f) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = sales ? m.must(table, "شماره فاکتور", "shfacfo") : m.must(table, "شماره فاکتور", "shfackh", "shfac");
        String partyCol = Sql.pick(cols, "shmo", "SHMO");
        String paidCol = sales ? Sql.pick(cols, "MabDaryaftFactor", "Daryaft") : Sql.pick(cols, "MablaghPardakht", "Pardakht");
        String tasviehCol = Sql.pick(cols, "tasvieh");
        String dueCol = Sql.pick(cols, "t_date");
        String descCol = Sql.pick(cols, "description", "Explain");
        String visCol = sales ? Sql.pick(cols, "vis_rdf", "VisitorID") : null;
        String custShmo = m.col("CUSTOMERS", "SHMO", "shmo");
        String custRoute = m.col("CUSTOMERS", "RDF_masir", "rdf_masir");
        String custGroup = m.col("CUSTOMERS", "group_rdf");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        if (sales && f.visitor >= 0 && visCol != null) { conds.add("TRY_CONVERT(int,h.[" + visCol + "])=?"); binds.add(f.visitor); }
        boolean needCust = f.route >= 0 || f.custGroup >= 0 || (f.search != null && !f.search.trim().isEmpty());
        String join = custJoin(m, "h", partyCol, "cu") + (sales ? visJoin(m, "h", visCol, "v") : "");
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        if (f.route >= 0 && custRoute != null && custShmo != null)
            { conds.add("TRY_CONVERT(int,cu.[" + custRoute + "])=?"); binds.add(f.route); needCust = true; }
        if (f.custGroup >= 0 && custGroup != null && custShmo != null)
            { conds.add("TRY_CONVERT(int,cu.[" + custGroup + "])=?"); binds.add(f.custGroup); needCust = true; }
        String totalExpr = Sql.num("h", amountCol);
        String paidExpr = Sql.num("h", paidCol);
        if ("settled".equals(f.status)) conds.add("(ABS((" + totalExpr + ")-(" + paidExpr + "))<=1" + (tasviehCol == null ? "" : " OR h.[" + tasviehCol + "]='t'") + ")");
        if ("unsettled".equals(f.status)) conds.add("(ABS((" + totalExpr + ")-(" + paidExpr + "))>1" + (tasviehCol == null ? "" : " AND ISNULL(h.[" + tasviehCol + "],'f')<>'t'") + ")");
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(Sql.txt("h", numberCol, 80));
            if (descCol != null) exprs.add(Sql.txt("h", descCol, 500));
            if (custName != null && custShmo != null) { exprs.add(Sql.txt("cu", custName, 250)); needCust = true; }
            String sc = searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String orderBy = "h.[" + dateCol + "] DESC, h.[" + numberCol + "] DESC";
        if ("total_desc".equals(f.sort)) orderBy = totalExpr + " DESC";
        if ("remain_desc".equals(f.sort)) orderBy = "((" + totalExpr + ")-(" + paidExpr + ")) DESC";
        if ("total_desc".equals(f.sort) || "remain_desc".equals(f.sort)) orderBy += ", h.[" + dateCol + "] DESC";
        String sql = "SELECT " + Sql.txt("h", numberCol, 80) + " AS no"
                + ", " + Sql.date10("h", dateCol) + " AS date"
                + ", " + (partyCol == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", partyCol, 100)) + " AS code"
                + ", " + custNameExpr(m, "h", partyCol, "cu") + " AS customer"
                + ", " + (sales ? (visCol == null ? "N'بدون ویزیتور'" : visNameExpr(m, "h", visCol, "v")) : "N'—'") + " AS visitor"
                + ", " + totalExpr + " AS total, " + paidExpr + " AS paid"
                + ", ((" + totalExpr + ")-(" + paidExpr + ")) AS remain"
                + ", " + (tasviehCol == null ? "N''" : Sql.txt("h", tasviehCol, 10)) + " AS tasvieh"
                + ", " + (dueCol == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", dueCol)) + " AS dueDate"
                + ", " + (descCol == null ? "CAST(NULL AS nvarchar(500))" : Sql.txt("h", descCol, 500)) + " AS descrip"
                + " FROM " + Sql.dedupe(table, numberCol, "h", "") + join
                + where + Sql.activeAnd(cols, "h").replaceFirst(" AND ", conds.isEmpty() ? " WHERE " : " AND ")
                + " ORDER BY " + orderBy
                + pageClause(binds, f.page, f.top);
        return new Q(sql, binds);
    }

    /** Invoice header (all key fields + joined names). */
    public static Q factorHeader(Meta m, boolean sales, String no) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = sales ? m.must(table, "شماره فاکتور", "shfacfo") : m.must(table, "شماره فاکتور", "shfackh", "shfac");
        String partyCol = Sql.pick(cols, "shmo", "SHMO");
        String paidCol = sales ? Sql.pick(cols, "MabDaryaftFactor", "Daryaft") : Sql.pick(cols, "MablaghPardakht", "Pardakht");
        String visCol = sales ? Sql.pick(cols, "vis_rdf", "VisitorID") : null;
        String[] extra = {"tafif", "tax", "avarez", "barbari", "sumlineall", "tasvieh", "t_date", "done_date",
                "description", "Status", "TaeedUser", "TaeedDate", "tdf", "moname", "shpish", "time_"};
        StringBuilder sb = new StringBuilder("SELECT TOP (1) ");
        sb.append(Sql.txt("h", numberCol, 80)).append(" AS no, ").append(Sql.date10("h", dateCol)).append(" AS date, ");
        sb.append(partyCol == null ? "CAST(NULL AS nvarchar(100))" : Sql.txt("h", partyCol, 100)).append(" AS code, ");
        sb.append(custNameExpr(m, "h", partyCol, "cu")).append(" AS customer, ");
        sb.append(sales ? (visCol == null ? "N'بدون ویزیتور'" : visNameExpr(m, "h", visCol, "v")) : "N'—'").append(" AS visitor, ");
        sb.append(Sql.num("h", amountCol)).append(" AS total, ").append(Sql.num("h", paidCol)).append(" AS paid");
        for (String e : extra) {
            String c = Sql.pick(cols, e);
            sb.append(", ");
            if (c == null) sb.append("CAST(NULL AS nvarchar(400))");
            else if (e.equals("description") || e.equals("TaeedUser") || e.equals("TaeedDate") || e.equals("moname")
                    || e.equals("shpish") || e.equals("time_") || e.equals("tasvieh")) sb.append(Sql.txt("h", c, 400));
            else if (e.equals("t_date") || e.equals("done_date")) sb.append(Sql.date10("h", c));
            else if (e.equals("Status")) sb.append("TRY_CONVERT(int,h.[").append(c).append("])");
            else sb.append(Sql.num("h", c));
            sb.append(" AS ").append(e);
        }
        List<Object> binds = new ArrayList<>();
        binds.add(no);
        sb.append(" FROM dbo.[").append(table).append("] h").append(custJoin(m, "h", partyCol, "cu"));
        if (sales) sb.append(visJoin(m, "h", visCol, "v"));
        sb.append(" WHERE TRY_CONVERT(nvarchar(80),h.[").append(numberCol).append("])=?");
        return new Q(sb.toString(), binds);
    }

    /** Invoice lines: shka,naka,qtyVah,qtyJoz,vahPrice,jozPrice,lineSum,discount,tax. */
    public static Q factorLines(Meta m, boolean sales, String no) throws Missing {
        String line = sales ? "subsailfact" : "subbuyfact";
        String head = sales ? "sailfact" : "buyfact";
        if (!m.table(line)) {
            // No lines table: return the header as a single pseudo-line.
            String amountCol = m.must(head, "مبلغ", "all");
            String numberCol = sales ? m.must(head, "شماره فاکتور", "shfacfo") : m.must(head, "شماره فاکتور", "shfackh", "shfac");
            List<Object> binds = new ArrayList<>();
            binds.add(no);
            return new Q("SELECT TOP (1) CAST(NULL AS nvarchar(60)) AS shka, N'اقلام تفکیک‌نشده' AS naka"
                    + ", CAST(NULL AS decimal(19,3)) AS qtyVah, CAST(NULL AS decimal(19,3)) AS qtyJoz"
                    + ", CAST(NULL AS decimal(19,2)) AS vahPrice, CAST(NULL AS decimal(19,2)) AS jozPrice"
                    + ", " + Sql.num("h", amountCol) + " AS lineSum, CAST(0 AS decimal(19,2)) AS discount"
                    + ", CAST(0 AS decimal(19,2)) AS tax FROM dbo.[" + head + "] h"
                    + " WHERE TRY_CONVERT(nvarchar(80),h.[" + numberCol + "])=?", binds);
        }
        Set<String> cols = m.columns(line);
        String linkCol = sales ? Sql.pick(cols, "shfacfo") : Sql.pick(cols, "shfackh", "shfac");
        if (linkCol == null) throw new Missing("ستون ارتباطی اقلام در «" + AtiranSchema.faTitle(line) + "» پیدا نشد");
        String shka = Sql.pick(cols, "SHKA", "shka");
        String lineName = Sql.pick(cols, "naka", "NAKA");
        String invName = m.col("inventory", "naka", "NAKA");
        String qtyV = Sql.pick(cols, "TEDVAH", "tedvah");
        String qtyJ = Sql.pick(cols, "TEDJOZ", "tedjoz");
        String vp = Sql.pick(cols, "VAHPRICE", "vahprice");
        String jp = Sql.pick(cols, "JOZPRICE", "jozprice");
        String sum = Sql.pick(cols, "LINESUM", "linesum");
        String disc = Sql.pick(cols, "TafifLine", "litakhma", "TafifAghlam");
        String tax = Sql.pick(cols, "tax", "Tax");
        String nameExpr = "COALESCE(" + (invName == null ? "" : Sql.txt("i", invName, 250) + ",")
                + (lineName == null ? "" : Sql.txt("d", lineName, 250) + ",")
                + (shka == null ? "N'—'" : Sql.txt("d", shka, 120) + ",N'—'") + ")";
        List<Object> binds = new ArrayList<>();
        binds.add(no);
        String sql = "SELECT " + (shka == null ? "CAST(NULL AS nvarchar(60))" : Sql.txt("d", shka, 60)) + " AS shka"
                + ", " + nameExpr + " AS naka"
                + ", " + numNull("d", qtyV, "decimal(19,3)") + " AS qtyVah"
                + ", " + numNull("d", qtyJ, "decimal(19,3)") + " AS qtyJoz"
                + ", " + numNull("d", vp, "decimal(19,2)") + " AS vahPrice"
                + ", " + numNull("d", jp, "decimal(19,2)") + " AS jozPrice"
                + ", " + Sql.num("d", sum) + " AS lineSum"
                + ", " + Sql.num("d", disc) + " AS discount"
                + ", " + Sql.num("d", tax) + " AS tax"
                + " FROM dbo.[" + line + "] d"
                + (shka != null && invName != null && m.table("inventory")
                ? " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[shka])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])" : "")
                + " WHERE TRY_CONVERT(nvarchar(80),d.[" + linkCol + "])=?"
                + " ORDER BY d.[" + linkCol + "]";
        return new Q(sql, binds);
    }

    private static String numNull(String alias, String col, String type) {
        if (col == null) return "CAST(NULL AS " + type + ")";
        return "TRY_CONVERT(" + type + "," + alias + ".[" + col + "])";
    }

    // ---- breakdowns: label,total,docs ----
    private static Q factorBreakdown(Meta m, boolean sales, Filter f, String kind, int top) throws Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = Sql.pick(cols, sales ? "shfacfo" : "shfackh");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe(table, numberCol, "h", inner);
        String label, join = "", group;
        if ("visitor".equals(kind) && sales) {
            String visCol = m.must(table, "ویزیتور", "vis_rdf", "VisitorID");
            label = visNameExpr(m, "h", visCol); join = visJoin(m, "h", visCol); group = label;
        } else if ("customer".equals(kind)) {
            String partyCol = m.must(table, "طرف‌حساب", "shmo", "SHMO");
            label = custNameExpr(m, "h", partyCol); join = custJoin(m, "h", partyCol); group = label;
        } else if ("route".equals(kind)) {
            String partyCol = m.must(table, "طرف‌حساب", "shmo", "SHMO");
            String routeCol = m.col("CUSTOMERS", "RDF_masir", "rdf_masir");
            String routeName = m.col("masir", "name");
            String routeKey = m.col("masir", "rdf_masir");
            join = custJoin(m, "h", partyCol);
            if (routeCol != null && routeName != null && routeKey != null && m.table("masir")) {
                join += " LEFT JOIN dbo.masir ms ON TRY_CONVERT(nvarchar(50),ms.[" + routeKey + "])=TRY_CONVERT(nvarchar(50),cu.[" + routeCol + "])";
                label = "COALESCE(" + Sql.txt("ms", routeName, 120) + ",N'بدون مسیر')";
            } else if (routeCol != null) {
                label = "COALESCE(" + Sql.txt("cu", routeCol, 120) + ",N'بدون مسیر')";
            } else throw new Missing("ستون مسیر در «مشتریان» پیدا نشد");
            group = label;
        } else if ("custgroup".equals(kind)) {
            String partyCol = m.must(table, "طرف‌حساب", "shmo", "SHMO");
            String gCol = m.col("CUSTOMERS", "group_rdf");
            String gName = m.col("custgroup", "group_name");
            String gKey = m.col("custgroup", "group_rdf");
            join = custJoin(m, "h", partyCol);
            if (gCol != null && gName != null && gKey != null && m.table("custgroup")) {
                join += " LEFT JOIN dbo.custgroup cg ON TRY_CONVERT(nvarchar(50),cg.[" + gKey + "])=TRY_CONVERT(nvarchar(50),cu.[" + gCol + "])";
                label = "COALESCE(" + Sql.txt("cg", gName, 120) + ",N'بدون گروه')";
            } else throw new Missing("ارتباط گروه مشتری قابل تشخیص نیست");
            group = label;
        } else throw new Missing("نوع تفکیک نامعتبر است");
        return new Q("SELECT TOP (" + clampTop(top) + ") " + label + " AS label"
                + ", ISNULL(SUM(" + Sql.num("h", amountCol) + "),0) AS total, COUNT_BIG(1) AS docs"
                + " FROM " + src + join + " GROUP BY " + group + " ORDER BY 2 DESC", binds);
    }

    public static Q salesByVisitor(Meta m, Filter f, int top) throws Missing {
        return factorBreakdown(m, true, f, "visitor", top);
    }

    public static Q factorByCustomer(Meta m, boolean sales, Filter f, int top) throws Missing {
        return factorBreakdown(m, sales, f, "customer", top);
    }

    public static Q factorByRoute(Meta m, boolean sales, Filter f, int top) throws Missing {
        return factorBreakdown(m, sales, f, "route", top);
    }

    public static Q factorByCustGroup(Meta m, boolean sales, Filter f, int top) throws Missing {
        return factorBreakdown(m, sales, f, "custgroup", top);
    }

    /** Top products by sales/purchase lines: label,qty,total,docs. */
    public static Q factorTopProducts(Meta m, boolean sales, Filter f, int top) throws Missing {
        String head = sales ? "sailfact" : "buyfact";
        String line = sales ? "subsailfact" : "subbuyfact";
        Set<String> cols = m.columns(head);
        String dateCol = sales ? m.must(head, "تاریخ", "date") : m.must(head, "تاریخ", "DATE", "date");
        String numberCol = sales ? m.must(head, "شماره فاکتور", "shfacfo") : m.must(head, "شماره فاکتور", "shfackh", "shfac");
        Set<String> sub = m.columns(line);
        if (sub.isEmpty()) throw new Missing("«" + AtiranSchema.faTitle(line) + "» در دیتابیس پیدا نشد");
        String linkCol = sales ? m.must(line, "شماره فاکتور", "shfacfo") : m.must(line, "شماره فاکتور", "shfackh", "shfac");
        String shka = m.must(line, "کد کالا", "SHKA", "shka");
        String sum = Sql.pick(sub, "LINESUM", "linesum");
        String qty = Sql.pick(sub, "TEDVAH", "tedvah");
        String invName = m.col("inventory", "naka", "NAKA");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND "))
                + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe(head, numberCol, "h", inner);
        String label = "COALESCE(" + (invName == null ? "" : Sql.txt("i", invName, 250) + ",") + Sql.txt("d", shka, 120) + ",N'—')";
        return new Q("SELECT TOP (" + clampTop(top) + ") " + label + " AS label"
                + ", ISNULL(SUM(" + numNull("d", qty, "decimal(19,3)") + "),0) AS qty"
                + ", ISNULL(SUM(" + Sql.num("d", sum) + "),0) AS total, COUNT_BIG(1) AS docs"
                + " FROM " + src
                + " JOIN dbo.[" + line + "] d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + (numberCol == null ? linkCol : numberCol) + "])"
                + (invName == null ? "" : " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[shka])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])")
                + " GROUP BY " + label + " ORDER BY 3 DESC", binds);
    }
}
