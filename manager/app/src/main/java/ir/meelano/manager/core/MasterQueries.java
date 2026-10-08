package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Products, customers, visitors, users, profit and misc reports. */
public final class MasterQueries {
    private MasterQueries() { }

    /** True-expression for Atiran boolean columns ('t'/'f', 1/0, yes/no …). */
    private static String boolTrue(String field) {
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + "))))";
        return "(" + n + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
    }

    // =====================================================================================
    // PRODUCTS — inventory / inventory_anbars / ka_act.
    // =====================================================================================
    /** Summary: count,active,low,out,value. */
    public static Queries.Q productsSummary(Meta m) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String active = m.col("inventory", "active", "Active");
        String vah = m.must("inventory", "موجودی", "mojkavah", "MojKavah");
        String buy = m.col("inventory", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String actCond = active == null ? "" : " AND " + boolTrue("[" + active + "]");
        String reo = m.col("inventory", "reopoint", "reorder_point");
        return new Queries.Q("SELECT COUNT_BIG(1) AS count"
                + ", SUM(CASE WHEN 1=1" + actCond + " THEN 1 ELSE 0 END) AS active"
                + ", SUM(CASE WHEN TRY_CONVERT(decimal(19,3),[" + vah + "])>0 AND TRY_CONVERT(decimal(19,3),[" + vah + "])<=" + AtiranSchema.LOW_STOCK_VAH + " THEN 1 ELSE 0 END) AS low"
                + ", SUM(CASE WHEN TRY_CONVERT(decimal(19,3),[" + vah + "])<=0 THEN 1 ELSE 0 END) AS out"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),[" + vah + "])*COALESCE(TRY_CONVERT(decimal(19,2)," + (buy == null ? "NULL" : "[" + buy + "]") + "),0)),0) AS value"
                + (reo == null ? ", CAST(0 AS bigint) AS reorderN" : ", SUM(CASE WHEN TRY_CONVERT(decimal(19,2),[" + reo + "])>0 AND TRY_CONVERT(decimal(19,3),[" + vah + "])<=TRY_CONVERT(decimal(19,2),[" + reo + "]) THEN 1 ELSE 0 END) AS reorderN")
                + " FROM dbo.inventory");
    }

    /** Product list: shka,naka,code,groupName,unit,vah,joz,buy,sale,active,anbarQty. */
    public static Queries.Q productsList(Meta m, Filter f) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String naka = m.must("inventory", "نام کالا", "naka", "NAKA");
        String code = m.col("inventory", "StuffCode", "coka", "code");
        String grp = m.col("inventory", "group_rdf");
        String unit = m.col("inventory", "vahsanj");
        String vah = m.col("inventory", "mojkavah", "MojKavah");
        String joz = m.col("inventory", "mojkajoz", "MojKajoz");
        String buy = m.col("inventory", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String sale = m.col("inventory", "FinalSalePrice", "sale_price", "forosh_price");
        String reo = m.col("inventory", "reopoint", "reorder_point");
        String active = m.col("inventory", "active", "Active");
        String grpName = m.col("kagroup", "group_name");
        String grpKey = m.col("kagroup", "group_rdf");
        boolean needGrp = (f.kalaGroup >= 0 && grp != null) || (grpName != null && grpKey != null && grp != null);
        // Warehouse filter via inventory_anbars (best-effort columns).
        String anShka = m.col("inventory_anbars", "shka", "SHKA");
        String anWh = m.colFlex("inventory_anbars", "rdf_anbar", "anbar", "warehouse", "anbarrdf");
        String anQty = m.colFlex("inventory_anbars", "mojkavah", "mojodi", "tedad", "qty", "meghdar");
        boolean useAnbar = f.warehouse >= 0 && anShka != null && anWh != null;
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (vah != null) {
            if ("low".equals(f.status)) conds.add("(TRY_CONVERT(decimal(19,3),i.[" + vah + "])>0 AND TRY_CONVERT(decimal(19,3),i.[" + vah + "])<=" + AtiranSchema.LOW_STOCK_VAH + ")");
            if ("out".equals(f.status)) conds.add("TRY_CONVERT(decimal(19,3),i.[" + vah + "])<=0");
            if ("ok".equals(f.status)) conds.add("TRY_CONVERT(decimal(19,3),i.[" + vah + "])>" + AtiranSchema.LOW_STOCK_VAH);
            if ("reorder".equals(f.status)) {
                if (vah == null || reo == null)
                    throw new Queries.Missing("ستون نقطه سفارش در «کالاها» پیدا نشد");
                conds.add("(TRY_CONVERT(decimal(19,2),i.[" + reo + "])>0 AND TRY_CONVERT(decimal(19,3),i.[" + vah + "])<=TRY_CONVERT(decimal(19,2),i.[" + reo + "]))");
            }
        }
        if ("inactive".equals(f.status) && active != null)
            conds.add("UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20),i.[" + active + "])))) NOT IN (N'T',N'1',N'TRUE',N'Y')");
        if (f.kalaGroup >= 0 && grp != null) { conds.add("TRY_CONVERT(int,i.[" + grp + "])=?"); binds.add(f.kalaGroup); }
        if (useAnbar) { conds.add("TRY_CONVERT(int,ab.[" + anWh + "])=?"); binds.add(f.warehouse); }
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(Sql.txt("i", naka, 250));
            if (code != null) exprs.add(Sql.txt("i", code, 120));
            exprs.add(Sql.txt("i", shka, 60));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String order = "i.[" + naka + "]";
        if ("stock_asc".equals(f.sort) && vah != null) order = "TRY_CONVERT(decimal(19,3),i.[" + vah + "]) ASC";
        if ("stock_desc".equals(f.sort) && vah != null) order = "TRY_CONVERT(decimal(19,3),i.[" + vah + "]) DESC";
        if ("price_desc".equals(f.sort) && sale != null) order = "TRY_CONVERT(decimal(19,2),i.[" + sale + "]) DESC";
        String sql = "SELECT " + Sql.txt("i", shka, 60) + " AS shka"
                + ", " + Sql.txt("i", naka, 250) + " AS naka"
                + ", " + (code == null ? "N''" : "COALESCE(" + Sql.txt("i", code, 120) + ",N'')") + " AS code"
                + ", " + (needGrp && grpName != null ? "COALESCE(" + Sql.txt("g", grpName, 150) + ",N'')" : "N''") + " AS groupName"
                + ", " + (unit == null ? "N''" : "COALESCE(" + Sql.txt("i", unit, 60) + ",N'')") + " AS unit"
                + ", " + (vah == null ? "CAST(NULL AS decimal(19,3))" : "TRY_CONVERT(decimal(19,3),i.[" + vah + "])") + " AS vah"
                + ", " + (joz == null ? "CAST(NULL AS decimal(19,3))" : "TRY_CONVERT(decimal(19,3),i.[" + joz + "])") + " AS joz"
                + ", " + (buy == null ? "CAST(NULL AS decimal(19,2))" : "TRY_CONVERT(decimal(19,2),i.[" + buy + "])") + " AS buy"
                + ", " + (sale == null ? "CAST(NULL AS decimal(19,2))" : "TRY_CONVERT(decimal(19,2),i.[" + sale + "])") + " AS sale"
                + ", " + (active == null ? "N''" : "COALESCE(" + Sql.txt("i", active, 10) + ",N'')") + " AS active"
                + ", " + (useAnbar && anQty != null ? "TRY_CONVERT(decimal(19,3),ab.[" + anQty + "])" : "CAST(NULL AS decimal(19,3))") + " AS anbarQty"
                + " FROM dbo.inventory i"
                + (needGrp ? " LEFT JOIN dbo.kagroup g ON TRY_CONVERT(nvarchar(50),g.[" + grpKey + "])=TRY_CONVERT(nvarchar(50),i.[" + grp + "])" : "")
                + (useAnbar ? " JOIN dbo.inventory_anbars ab ON TRY_CONVERT(nvarchar(100),ab.[" + anShka + "])=TRY_CONVERT(nvarchar(100),i.[" + shka + "])" : "")
                + where + " ORDER BY " + order + Queries.pageClause(binds, f.page, f.top);
        return new Queries.Q(sql, binds);
    }

    /** Full product header + group name. */
    public static Queries.Q productHeader(Meta m, String shkaVal) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String naka = m.must("inventory", "نام کالا", "naka", "NAKA");
        String grp = m.col("inventory", "group_rdf");
        String grpName = m.col("kagroup", "group_name");
        String grpKey = m.col("kagroup", "group_rdf");
        String[] colsWanted = {"StuffCode", "coka", "vahsanj", "mohvah", "mojkavah", "mojkajoz", "reopoint",
                "bastebandi", "buy_price", "pure_buy_price", "buyjoz", "FinalSalePrice", "inventory_price",
                "MODPAR", "maxtafnaghd", "active", "sharh", "min_sef", "barbari_vahed", "ptax"};
        StringBuilder sb = new StringBuilder("SELECT TOP (1) ");
        sb.append(Sql.txt("i", shka, 60)).append(" AS shka, ").append(Sql.txt("i", naka, 250)).append(" AS naka");
        for (String w : colsWanted) {
            String c = m.col("inventory", w);
            sb.append(", ");
            if (c == null) sb.append("CAST(NULL AS nvarchar(200))");
            else if (w.equals("sharh") || w.equals("vahsanj") || w.equals("bastebandi") || w.equals("active")
                    || w.equals("StuffCode") || w.equals("coka")) sb.append(Sql.txt("i", c, 400));
            else sb.append("TRY_CONVERT(decimal(19,3),i.[").append(c).append("])");
            sb.append(" AS ").append(w);
        }
        sb.append(", ").append(grp != null && grpName != null && grpKey != null && m.table("kagroup")
                ? "COALESCE(" + Sql.txt("g", grpName, 150) + ",N'')" : "N''").append(" AS groupName");
        List<Object> binds = new ArrayList<>();
        binds.add(shkaVal);
        sb.append(" FROM dbo.inventory i");
        if (grp != null && grpKey != null && m.table("kagroup"))
            sb.append(" LEFT JOIN dbo.kagroup g ON TRY_CONVERT(nvarchar(50),g.[").append(grpKey).append("])=TRY_CONVERT(nvarchar(50),i.[").append(grp).append("])");
        sb.append(" WHERE TRY_CONVERT(nvarchar(60),i.[").append(shka).append("])=?");
        return new Queries.Q(sb.toString(), binds);
    }

    /** Product turnover (ka_act): date,op,opLabel,inQty,outQty,qty,desc,doc. Best-effort qty columns. */
    public static Queries.Q productTurnover(Meta m, String shkaVal, Filter f) throws Queries.Missing {
        if (!m.table("ka_act")) throw new Queries.Missing("«گردش کالا» در دیتابیس پیدا نشد");
        String shka = m.must("ka_act", "کد کالا", "shka", "SHKA");
        String date = m.col("ka_act", "date", "DATE", "done_date", "DoneDate");
        String op = m.col("ka_act", "act_id", "ActID", "actid");
        String in = m.colFlex("ka_act", "tedad_vorood", "vorood", "in_qty", "qty_in", "vared", "buy_qty");
        String out = m.colFlex("ka_act", "tedad_khorooj", "khorooj", "out_qty", "qty_out", "sale_qty", "kharej");
        String qty = m.colFlex("ka_act", "tedad", "qty", "meghdar", "TEDVAH", "tedvah");
        String desc = m.colFlex("ka_act", "act_dis", "description", "sharh", "tozihat");
        String doc = m.colFlex("ka_act", "ghno", "shfacfo", "shfackh", "shomare", "sanad", "docnumber");
        String anbar = m.col("ka_act", "RdfAnbar", "rdf_anbar");
        List<Object> binds = new ArrayList<>();
        binds.add(shkaVal);
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(60),h.[" + shka + "])=?");
        if (date != null) {
            String dc = Sql.dateCond(Sql.date10("h", date), f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        if (f.warehouse >= 0 && anbar != null) { conds.add("TRY_CONVERT(int,h.[" + anbar + "])=?"); binds.add(f.warehouse); }
        String order = date == null ? "1 DESC" : "h.[" + date + "] DESC";
        return new Queries.Q("SELECT " + (date == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", date)) + " AS date"
                + ", " + (op == null ? "N''" : "COALESCE(" + Sql.txt("h", op, 40) + ",N'')") + " AS op"
                + ", " + Queries.actLabelExpr(m, "h", op, "an") + " AS opLabel"
                + ", " + (in == null ? "CAST(NULL AS decimal(19,3))" : "TRY_CONVERT(decimal(19,3),h.[" + in + "])") + " AS inQty"
                + ", " + (out == null ? "CAST(NULL AS decimal(19,3))" : "TRY_CONVERT(decimal(19,3),h.[" + out + "])") + " AS outQty"
                + ", " + (qty == null ? "CAST(NULL AS decimal(19,3))" : "TRY_CONVERT(decimal(19,3),h.[" + qty + "])") + " AS qty"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("h", desc, 500) + ",N'')") + " AS descrip"
                + ", " + (doc == null ? "N''" : "COALESCE(" + Sql.txt("h", doc, 80) + ",N'')") + " AS doc"
                + " FROM dbo.ka_act h" + Queries.actLabelJoin(m, "h", op, "an")
                + " WHERE " + Sql.join(conds, " AND ") + " ORDER BY " + order
                + Queries.pageClause(binds, f.page, f.top), binds);
    }

    /** Stock value by kala group: label,value,count. */
    public static Queries.Q stockValueByGroup(Meta m) throws Queries.Missing {
        String vah = m.must("inventory", "موجودی", "mojkavah", "MojKavah");
        String buy = m.col("inventory", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String grp = m.col("inventory", "group_rdf");
        String grpName = m.col("kagroup", "group_name");
        String grpKey = m.col("kagroup", "group_rdf");
        String label = grp != null && grpName != null && grpKey != null && m.table("kagroup")
                ? "COALESCE(" + Sql.txt("g", grpName, 150) + ",N'بدون گروه')" : "N'همه کالاها'";
        String join = grp != null && grpKey != null && m.table("kagroup")
                ? " LEFT JOIN dbo.kagroup g ON TRY_CONVERT(nvarchar(50),g.[" + grpKey + "])=TRY_CONVERT(nvarchar(50),i.[" + grp + "])" : "";
        return new Queries.Q("SELECT " + label + " AS label"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),i.[" + vah + "])*COALESCE(TRY_CONVERT(decimal(19,2)," + (buy == null ? "NULL" : "i.[" + buy + "]") + "),0)),0) AS value"
                + ", COUNT_BIG(1) AS count FROM dbo.inventory i" + join + " GROUP BY " + label + " ORDER BY 2 DESC");
    }

    // =====================================================================================
    // CUSTOMERS — CUSTOMERS / cust_act.
    // =====================================================================================
    /** Summary: total,debtorsN,debtorsSum,creditorsN,creditorsSum,settledN,blockedN. */
    public static Queries.Q customersSummary(Meta m) throws Queries.Missing {
        String bal = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        String blocked = m.col("CUSTOMERS", "black_list");
        String b = "TRY_CONVERT(decimal(19,2),[" + bal + "])";
        return new Queries.Q("SELECT COUNT_BIG(1) AS total"
                + ", SUM(CASE WHEN " + b + ">0 THEN 1 ELSE 0 END) AS debtorsN"
                + ", ISNULL(SUM(CASE WHEN " + b + ">0 THEN " + b + " ELSE 0 END),0) AS debtorsSum"
                + ", SUM(CASE WHEN " + b + "<0 THEN 1 ELSE 0 END) AS creditorsN"
                + ", ISNULL(SUM(CASE WHEN " + b + "<0 THEN -(" + b + ") ELSE 0 END),0) AS creditorsSum"
                + ", SUM(CASE WHEN ABS(COALESCE(" + b + ",0))<=1 THEN 1 ELSE 0 END) AS settledN"
                + ", " + (blocked == null ? "CAST(0 AS bigint)" : "SUM(CASE WHEN " + boolTrue("[" + blocked + "]") + " THEN 1 ELSE 0 END)") + " AS blockedN"
                + " FROM dbo.CUSTOMERS");
    }

    /** Customer list with sales + cheque aggregates + all filters. */
    public static Queries.Q customersList(Meta m, Filter f) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String cell = m.col("CUSTOMERS", "cell", "mobile", "Mobile");
        String tell = m.col("CUSTOMERS", "tell1", "tell2", "phone");
        String addr = m.colFlex("CUSTOMERS", "addre", "address", "addr", "neshani");
        String bal = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        String credit = m.col("CUSTOMERS", "cred", "etebar", "credit");
        String vis = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        String route = m.col("CUSTOMERS", "RDF_masir", "rdf_masir");
        String grp = m.col("CUSTOMERS", "group_rdf");
        String blocked = m.col("CUSTOMERS", "black_list");
        String routeName = m.col("masir", "name");
        String routeKey = m.col("masir", "rdf_masir");
        String grpName = m.col("custgroup", "group_name");
        String grpKey = m.col("custgroup", "group_rdf");
        String visName = m.col("visitors", "vis_name", "name");
        String visKey = m.col("visitors", "vis_rdf", "rdf");
        // Sales aggregates (OUTER APPLY, deduped).
        Set<String> sail = m.columns("sailfact");
        String saleShmo = m.col("sailfact", "shmo", "SHMO");
        String saleAmount = m.col("sailfact", "all");
        String saleNo = m.col("sailfact", "shfacfo");
        String saleDate = m.col("sailfact", "date");
        boolean canSales = saleShmo != null && saleAmount != null && m.table("sailfact");
        String saleInner = canSales ? "WHERE TRY_CONVERT(nvarchar(100),x.[" + saleShmo + "])=TRY_CONVERT(nvarchar(100),c.[" + shmo + "])"
                + Sql.activeAnd(sail, "x") + Sql.softAnd(sail, "x") : "";
        String saleApply = canSales
                ? "OUTER APPLY (SELECT COUNT_BIG(1) cnt, ISNULL(SUM(" + Sql.num("s", saleAmount) + "),0) total"
                + ", " + (saleDate == null ? "CAST(N'' AS nvarchar(10))" : "ISNULL(MAX(" + Sql.date10("s", saleDate) + "),N'')") + " lastDay"
                + " FROM " + Sql.dedupe("sailfact", saleNo, "s", saleInner) + ") sf"
                : "OUTER APPLY (SELECT CAST(0 AS bigint) cnt, CAST(0 AS decimal(19,2)) total, CAST(N'' AS nvarchar(10)) lastDay) sf";
        String chkShmo = m.col("getchk", "shmo");
        String chkAmount = m.col("getchk", "getchkmab");
        boolean canChk = chkShmo != null && chkAmount != null && m.table("getchk");
        String chkApply = canChk
                ? "OUTER APPLY (SELECT ISNULL(SUM(" + Sql.num("g", chkAmount) + "),0) total FROM dbo.getchk g"
                + " WHERE TRY_CONVERT(nvarchar(100),g.[" + chkShmo + "])=TRY_CONVERT(nvarchar(100),c.[" + shmo + "])) ch"
                : "OUTER APPLY (SELECT CAST(0 AS decimal(19,2)) total) ch";
        String b = "TRY_CONVERT(decimal(19,2),c.[" + bal + "])";
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if ("debt".equals(f.status)) conds.add(b + ">0");
        if ("credit".equals(f.status)) conds.add(b + "<0");
        if ("settled".equals(f.status)) conds.add("ABS(COALESCE(" + b + ",0))<=1");
        if ("blocked".equals(f.status) && blocked != null) conds.add(boolTrue("c.[" + blocked + "]"));
        if ("no_buy".equals(f.status) && canSales) conds.add("ISNULL(sf.cnt,0)=0");
        if (f.visitor >= 0 && vis != null) { conds.add("TRY_CONVERT(int,c.[" + vis + "])=?"); binds.add(f.visitor); }
        if (f.route >= 0 && route != null) { conds.add("TRY_CONVERT(int,c.[" + route + "])=?"); binds.add(f.route); }
        if (f.custGroup >= 0 && grp != null) { conds.add("TRY_CONVERT(int,c.[" + grp + "])=?"); binds.add(f.custGroup); }
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            if (name != null) exprs.add(Sql.txt("c", name, 250));
            if (cell != null) exprs.add(Sql.txt("c", cell, 100));
            if (tell != null) exprs.add(Sql.txt("c", tell, 100));
            if (addr != null) exprs.add(Sql.txt("c", addr, 500));
            exprs.add(Sql.txt("c", shmo, 100));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String order = name == null ? "c.[" + shmo + "]" : "c.[" + name + "]";
        if ("top".equals(f.sort) && canSales) order = "sf.total DESC";
        if ("debt".equals(f.sort)) order = b + " DESC";
        if ("balance_asc".equals(f.sort)) order = b + " ASC";
        String join = "";
        if (route != null && routeName != null && routeKey != null && m.table("masir"))
            join += " LEFT JOIN dbo.masir ms ON TRY_CONVERT(nvarchar(50),ms.[" + routeKey + "])=TRY_CONVERT(nvarchar(50),c.[" + route + "])";
        if (grp != null && grpName != null && grpKey != null && m.table("custgroup"))
            join += " LEFT JOIN dbo.custgroup cg ON TRY_CONVERT(nvarchar(50),cg.[" + grpKey + "])=TRY_CONVERT(nvarchar(50),c.[" + grp + "])";
        if (vis != null && visName != null && visKey != null && m.table("visitors"))
            join += " LEFT JOIN dbo.visitors v ON TRY_CONVERT(nvarchar(50),v.[" + visKey + "])=TRY_CONVERT(nvarchar(50),c.[" + vis + "])";
        String sql = "SELECT " + Sql.txt("c", shmo, 100) + " AS code"
                + ", " + (name == null ? Sql.txt("c", shmo, 120) : "COALESCE(" + Sql.txt("c", name, 250) + "," + Sql.txt("c", shmo, 120) + ")") + " AS name"
                + ", " + (cell == null ? "N''" : "COALESCE(" + Sql.txt("c", cell, 100) + ",N'')") + " AS cell"
                + ", " + (tell == null ? "N''" : "COALESCE(" + Sql.txt("c", tell, 100) + ",N'')") + " AS tell"
                + ", " + (addr == null ? "N''" : "COALESCE(" + Sql.txt("c", addr, 500) + ",N'')") + " AS addr"
                + ", COALESCE(" + b + ",0) AS balance"
                + ", " + (credit == null ? "CAST(NULL AS decimal(19,2))" : "TRY_CONVERT(decimal(19,2),c.[" + credit + "])") + " AS credit"
                + ", " + (vis == null || visName == null ? "N''" : "COALESCE(" + Sql.txt("v", visName, 150) + ",N'')") + " AS visitor"
                + ", " + (route == null || routeName == null ? "N''" : "COALESCE(" + Sql.txt("ms", routeName, 120) + ",N'')") + " AS route"
                + ", " + (grp == null || grpName == null ? "N''" : "COALESCE(" + Sql.txt("cg", grpName, 120) + ",N'')") + " AS groupName"
                + ", ISNULL(sf.total,0) AS salesTotal, ISNULL(sf.cnt,0) AS salesCount, ISNULL(sf.lastDay,N'') AS lastSale"
                + ", ISNULL(ch.total,0) AS chequeTotal"
                + " FROM dbo.CUSTOMERS c " + saleApply + " " + chkApply + join + where
                + " ORDER BY " + order + Queries.pageClause(binds, f.page, f.top);
        return new Queries.Q(sql, binds);
    }

    /** Customer header: contact + balance + credit + joined names. */
    public static Queries.Q customerHeader(Meta m, String shmoVal) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String bal = m.must("CUSTOMERS", "مانده", "man", "Balance", "Mandeh");
        // NOTE: "code" is intentionally absent — shmo is already aliased AS code above.
        String[] flex = {"cell", "tell1", "tell2", "addre", "cred", "c_mel", "c_egh", "date", "sharh",
                "vis_rdf", "RDF_masir", "group_rdf", "hesab_status", "black_list", "check_eteb", "MaxManFactor"};
        StringBuilder sb = new StringBuilder("SELECT TOP (1) ");
        sb.append(Sql.txt("c", shmo, 100)).append(" AS code, ");
        sb.append(name == null ? Sql.txt("c", shmo, 120) : "COALESCE(" + Sql.txt("c", name, 250) + "," + Sql.txt("c", shmo, 120) + ")").append(" AS name, ");
        sb.append("COALESCE(TRY_CONVERT(decimal(19,2),c.[").append(bal).append("]),0) AS balance");
        for (String w : flex) {
            String c = w.equals("addre") ? m.colFlex("CUSTOMERS", "addre", "address", "addr") : m.col("CUSTOMERS", w);
            sb.append(", ");
            if (c == null) sb.append("CAST(NULL AS nvarchar(400))");
            else if (w.equals("cred")) sb.append("TRY_CONVERT(decimal(19,2),c.[").append(c).append("])");
            else if (w.equals("date")) sb.append(Sql.date10("c", c));
            else sb.append(Sql.txt("c", c, 400));
            sb.append(" AS ").append(w);
        }
        String vis = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        String route = m.col("CUSTOMERS", "RDF_masir", "rdf_masir");
        String grp = m.col("CUSTOMERS", "group_rdf");
        String visName = m.col("visitors", "vis_name", "name");
        String visKey = m.col("visitors", "vis_rdf", "rdf");
        String routeName = m.col("masir", "name");
        String routeKey = m.col("masir", "rdf_masir");
        String grpName = m.col("custgroup", "group_name");
        String grpKey = m.col("custgroup", "group_rdf");
        sb.append(", ").append(vis != null && visName != null && visKey != null && m.table("visitors")
                ? "COALESCE(" + Sql.txt("v", visName, 150) + ",N'')" : "N''").append(" AS visitorName");
        sb.append(", ").append(route != null && routeName != null && routeKey != null && m.table("masir")
                ? "COALESCE(" + Sql.txt("ms", routeName, 120) + ",N'')" : "N''").append(" AS routeName");
        sb.append(", ").append(grp != null && grpName != null && grpKey != null && m.table("custgroup")
                ? "COALESCE(" + Sql.txt("cg", grpName, 120) + ",N'')" : "N''").append(" AS groupName");
        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        sb.append(" FROM dbo.CUSTOMERS c");
        if (vis != null && visKey != null && m.table("visitors"))
            sb.append(" LEFT JOIN dbo.visitors v ON TRY_CONVERT(nvarchar(50),v.[").append(visKey).append("])=TRY_CONVERT(nvarchar(50),c.[").append(vis).append("])");
        if (route != null && routeKey != null && m.table("masir"))
            sb.append(" LEFT JOIN dbo.masir ms ON TRY_CONVERT(nvarchar(50),ms.[").append(routeKey).append("])=TRY_CONVERT(nvarchar(50),c.[").append(route).append("])");
        if (grp != null && grpKey != null && m.table("custgroup"))
            sb.append(" LEFT JOIN dbo.custgroup cg ON TRY_CONVERT(nvarchar(50),cg.[").append(grpKey).append("])=TRY_CONVERT(nvarchar(50),c.[").append(grp).append("])");
        sb.append(" WHERE TRY_CONVERT(nvarchar(100),c.[").append(shmo).append("])=?");
        return new Queries.Q(sb.toString(), binds);
    }

    /** Customer account turnover (cust_act): date,op,opLabel,bed,bes,desc,ghno,doneDate. */
    public static Queries.Q customerTurnover(Meta m, String shmoVal, Filter f) throws Queries.Missing {
        if (!m.table("cust_act")) throw new Queries.Missing("«گردش حساب مشتری» در دیتابیس پیدا نشد");
        String shmo = m.must("cust_act", "کد مشتری", "shmo");
        String date = m.must("cust_act", "تاریخ", "date", "DATE");
        String bed = m.must("cust_act", "بدهکار", "act_bed", "bed");
        String bes = m.must("cust_act", "بستانکار", "act_bes", "bes");
        String op = m.col("cust_act", "act_id", "ActID");
        String desc = m.col("cust_act", "act_dis", "description");
        String ghno = m.col("cust_act", "ghno");
        String done = m.col("cust_act", "done_date", "DoneDate");
        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(100),h.[" + shmo + "])=?");
        String dc = Sql.dateCond(Sql.date10("h", date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        return new Queries.Q("SELECT " + Sql.date10("h", date) + " AS date"
                + ", " + (op == null ? "N''" : "COALESCE(" + Sql.txt("h", op, 40) + ",N'')") + " AS op"
                + ", " + Queries.actLabelExpr(m, "h", op, "an") + " AS opLabel"
                + ", " + Sql.num("h", bed) + " AS bed, " + Sql.num("h", bes) + " AS bes"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("h", desc, 500) + ",N'')") + " AS descrip"
                + ", " + (ghno == null ? "N''" : "COALESCE(" + Sql.txt("h", ghno, 60) + ",N'')") + " AS ghno"
                + ", " + (done == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", done)) + " AS doneDate"
                + " FROM dbo.cust_act h" + Queries.actLabelJoin(m, "h", op, "an")
                + " WHERE " + Sql.join(conds, " AND ") + " ORDER BY h.[" + date + "] DESC"
                + Queries.pageClause(binds, f.page, f.top), binds);
    }

    /** Customer's receipts/payment vouchers: ghno,date,total,desc. */
    public static Queries.Q customerDars(Meta m, String shmoVal, int p, Filter f) throws Queries.Missing {
        if (!m.table("dar")) throw new Queries.Missing("«قبوض دریافت و پرداخت» در دیتابیس پیدا نشد");
        String ghno = m.must("dar", "شماره قبض", "ghno", "GHNO");
        String shmo = m.must("dar", "کد مشتری", "shmo", "SHMO");
        String date = m.must("dar", "تاریخ", "date", "DATE");
        String cash = m.col("dar", "MabNaghdi", "mabNaghdi");
        String pos = m.col("dar", "MabPos", "mabPos");
        String hav = m.col("dar", "MabHavaleh", "mabHavaleh");
        String chq = m.col("dar", "SumMabcheck", "sumMabcheck");
        String desc = m.col("dar", "DarDesc", "darDesc", "description");
        List<Object> binds = new ArrayList<>();
        binds.add(p);
        binds.add(shmoVal);
        List<String> conds = new ArrayList<>();
        conds.add("h.[p]=?");
        conds.add("TRY_CONVERT(nvarchar(100),h.[" + shmo + "])=?");
        String dc = Sql.dateCond(Sql.date10("h", date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        return new Queries.Q("SELECT " + Sql.txt("h", ghno, 60) + " AS ghno"
                + ", " + Sql.date10("h", date) + " AS date"
                + ", (" + Sql.num("h", cash) + "+" + Sql.num("h", pos) + "+" + Sql.num("h", hav) + "+" + Sql.num("h", chq) + ") AS total"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("h", desc, 500) + ",N'')") + " AS descrip"
                + " FROM dbo.dar h WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY h.[" + date + "] DESC" + Queries.pageClause(binds, f.page, f.top), binds);
    }

    /** Customer's cheques: num,bank,amount,sardate,st. */
    public static Queries.Q customerCheques(Meta m, String shmoVal, boolean incoming) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, incoming);
        if (c.shmo == null) throw new Queries.Missing("ستون مشتری در «" + AtiranSchema.faTitle(c.table) + "» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        String order = c.sardate == null ? "1" : "h.[" + c.sardate + "] DESC";
        return new Queries.Q("SELECT " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + (c.bank == null ? "N''" : "COALESCE(" + Sql.txt("h", c.bank, 150) + ",N'')") + " AS bank"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + (c.sardate == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", c.sardate)) + " AS sardate"
                + ", COALESCE(" + Sql.txt("h", c.st, 40) + ",N'') AS st"
                + ", " + (c.back == null ? "N''" : "COALESCE(" + Sql.txt("h", c.back, 10) + ",N'')") + " AS back"
                + " FROM dbo.[" + c.table + "] h WHERE TRY_CONVERT(nvarchar(100),h.[" + c.shmo + "])=?"
                + " ORDER BY " + order, binds);
    }

    /** New customers in range: code,name,date,visitor. */
    public static Queries.Q newCustomers(Meta m, Filter f) throws Queries.Missing {
        String shmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String name = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String date = m.must("CUSTOMERS", "تاریخ ثبت", "date", "DATE");
        String vis = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("c", date), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT " + Sql.txt("c", shmo, 100) + " AS code"
                + ", " + (name == null ? Sql.txt("c", shmo, 120) : "COALESCE(" + Sql.txt("c", name, 250) + "," + Sql.txt("c", shmo, 120) + ")") + " AS name"
                + ", " + Sql.date10("c", date) + " AS date"
                + ", " + (vis == null ? "N''" : Queries.visNameExpr(m, "c", vis)) + " AS visitor"
                + " FROM dbo.CUSTOMERS c" + (vis == null ? "" : Queries.visJoin(m, "c", vis))
                + where + " ORDER BY c.[" + date + "] DESC" + Queries.pageClause(binds, f.page, f.top), binds);
    }

    // =====================================================================================
    // VISITORS
    // =====================================================================================
    /** Performance per visitor: id,name,active,phone,invoices,sales,collected,customers,allCustomers,goals. */
    public static Queries.Q visitorsPerf(Meta m, Filter f) throws Queries.Missing {
        String id = m.must("visitors", "کد ویزیتور", "vis_rdf", "rdf");
        String name = m.col("visitors", "vis_name", "name");
        String active = m.col("visitors", "active", "Active");
        String phone = m.col("visitors", "vis_cell", "vis_tell1", "cell");
        String saleVis = m.col("sailfact", "vis_rdf", "VisitorID");
        String saleAmount = m.col("sailfact", "all");
        String saleNo = m.col("sailfact", "shfacfo");
        String saleDate = m.col("sailfact", "date");
        String saleShmo = m.col("sailfact", "shmo", "SHMO");
        String salePaid = m.col("sailfact", "MabDaryaftFactor", "Daryaft");
        boolean canSales = saleVis != null && saleAmount != null && m.table("sailfact");
        String innerConds = "";
        List<Object> binds = new ArrayList<>();
        if (canSales && saleDate != null) {
            String dc = Sql.dateCond(Sql.date10("x", saleDate), f.from, f.to, binds);
            innerConds = " AND " + (dc.isEmpty() ? "1=1" : dc);
        }
        String saleApply = canSales
                ? "OUTER APPLY (SELECT COUNT_BIG(1) docs, ISNULL(SUM(" + Sql.num("s", saleAmount) + "),0) total"
                + ", ISNULL(SUM(" + Sql.num("s", salePaid) + "),0) collected"
                + ", " + (saleShmo == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT s.[" + saleShmo + "])") + " custs"
                + " FROM " + Sql.dedupe("sailfact", saleNo, "s",
                "WHERE TRY_CONVERT(nvarchar(50),x.[" + saleVis + "])=TRY_CONVERT(nvarchar(50),t.[" + id + "])" + innerConds
                        + Sql.activeAnd(m.columns("sailfact"), "x")) + ") sf"
                : "OUTER APPLY (SELECT CAST(0 AS bigint) docs, CAST(0 AS decimal(19,2)) total, CAST(0 AS decimal(19,2)) collected, CAST(0 AS bigint) custs) sf";
        String custVis = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        String custApply = custVis != null && m.table("CUSTOMERS")
                ? "OUTER APPLY (SELECT COUNT_BIG(1) allCusts FROM dbo.CUSTOMERS c"
                + " WHERE TRY_CONVERT(nvarchar(50),c.[" + custVis + "])=TRY_CONVERT(nvarchar(50),t.[" + id + "])) cc"
                : "OUTER APPLY (SELECT CAST(0 AS bigint) allCusts) cc";
        String goalVis = m.col("vis_goals", "vis_rdf");
        String goalMab = m.col("vis_goals", "mab");
        String goalApply = goalVis != null && goalMab != null && m.table("vis_goals")
                ? "OUTER APPLY (SELECT ISNULL(SUM(" + Sql.num("g", goalMab) + "),0) goals FROM dbo.vis_goals g"
                + " WHERE TRY_CONVERT(nvarchar(50),g.[" + goalVis + "])=TRY_CONVERT(nvarchar(50),t.[" + id + "])) gl"
                : "OUTER APPLY (SELECT CAST(0 AS decimal(19,2)) goals) gl";
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id"
                + ", " + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + ", " + (active == null ? "N''" : "COALESCE(" + Sql.txt("t", active, 10) + ",N'')") + " AS active"
                + ", " + (phone == null ? "N''" : "COALESCE(" + Sql.txt("t", phone, 100) + ",N'')") + " AS phone"
                + ", ISNULL(sf.docs,0) AS invoices, ISNULL(sf.total,0) AS sales, ISNULL(sf.collected,0) AS collected"
                + ", ISNULL(sf.custs,0) AS customers, ISNULL(cc.allCusts,0) AS allCustomers, ISNULL(gl.goals,0) AS goals"
                + " FROM dbo.visitors t " + saleApply + " " + custApply + " " + goalApply
                + " ORDER BY ISNULL(sf.total,0) DESC", binds);
    }

    /** Visitor daily sales: day,total,docs. */
    public static Queries.Q visitorTrend(Meta m, int visId, Filter f) throws Queries.Missing {
        String saleVis = m.must("sailfact", "ویزیتور", "vis_rdf", "VisitorID");
        String saleAmount = m.must("sailfact", "مبلغ", "all");
        String saleDate = m.must("sailfact", "تاریخ", "date");
        String saleNo = m.col("sailfact", "shfacfo");
        List<Object> binds = new ArrayList<>();
        binds.add(visId);
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(int,x.[" + saleVis + "])=?");
        String dc = Sql.dateCond(Sql.date10("x", saleDate), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        Set<String> cols = m.columns("sailfact");
        String src = Sql.dedupe("sailfact", saleNo, "h", "WHERE " + Sql.join(conds, " AND ") + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x"));
        String day = Sql.date10("h", saleDate);
        return new Queries.Q("SELECT " + day + " AS day, ISNULL(SUM(" + Sql.num("h", saleAmount) + "),0) AS total"
                + ", COUNT_BIG(1) AS docs FROM " + src + " GROUP BY " + day + " ORDER BY " + day, binds);
    }

    /** Visitor's customers: code,name,sales,docs,lastDay. */
    public static Queries.Q visitorCustomers(Meta m, int visId, Filter f) throws Queries.Missing {
        String saleVis = m.must("sailfact", "ویزیتور", "vis_rdf", "VisitorID");
        String saleAmount = m.must("sailfact", "مبلغ", "all");
        String saleDate = m.col("sailfact", "date");
        String saleNo = m.col("sailfact", "shfacfo");
        String saleShmo = m.must("sailfact", "کد مشتری", "shmo", "SHMO");
        List<Object> binds = new ArrayList<>();
        binds.add(visId);
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(int,x.[" + saleVis + "])=?");
        if (saleDate != null) {
            String dc = Sql.dateCond(Sql.date10("x", saleDate), f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        Set<String> cols = m.columns("sailfact");
        String src = Sql.dedupe("sailfact", saleNo, "h", "WHERE " + Sql.join(conds, " AND ") + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x"));
        String label = Queries.custNameExpr(m, "h", saleShmo);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(f.top) + ") " + Sql.txt("h", saleShmo, 100) + " AS code"
                + ", " + label + " AS name"
                + ", ISNULL(SUM(" + Sql.num("h", saleAmount) + "),0) AS sales, COUNT_BIG(1) AS docs"
                + ", " + (saleDate == null ? "N''" : "ISNULL(MAX(" + Sql.date10("h", saleDate) + "),N'')") + " AS lastDay"
                + " FROM " + src + Queries.custJoin(m, "h", saleShmo)
                + " GROUP BY " + Sql.txt("h", saleShmo, 100) + ", " + label + " ORDER BY 3 DESC", binds);
    }

    // =====================================================================================
    // USERS — sys_users / LoginDetails / Log.
    // =====================================================================================
    /** User list: id,name,role,active,phone,lastLogin,todayN. */
    public static Queries.Q usersList(Meta m) throws Queries.Missing {
        String id = m.must("sys_users", "کد کاربر", "user_id", "UserID", "userid");
        String uname = m.col("sys_users", "user_name", "Username", "username");
        String lname = m.col("sys_users", "user_lname");
        String fname = m.col("sys_users", "user_fname");
        String role = m.col("sys_users", "role_id", "RoleID");
        String active = m.col("sys_users", "active", "Active");
        String phone = m.col("sys_users", "phone");
        StringBuilder nm = new StringBuilder("COALESCE(");
        boolean first = true;
        if (lname != null && fname != null) {
            nm.append("NULLIF(LTRIM(RTRIM(COALESCE(").append(Sql.txt("u", fname, 100)).append(",N'')+N' '+COALESCE(")
                    .append(Sql.txt("u", lname, 100)).append(",N''))),N'')");
            first = false;
        }
        if (lname != null) { if (!first) nm.append(","); nm.append(Sql.txt("u", lname, 120)); first = false; }
        if (uname != null) { if (!first) nm.append(","); nm.append(Sql.txt("u", uname, 120)); first = false; }
        if (!first) nm.append(",");
        nm.append(Sql.txt("u", id, 60)).append(")");
        // Role name (best-effort over role / Roles).
        String roleExpr = role == null ? "N''" : "COALESCE(" + Sql.txt("u", role, 80) + ",N'')";
        String roleJoin = "";
        for (String rt : new String[]{"role", "Roles"}) {
            if (!m.table(rt)) continue;
            String rid = m.colFlex(rt, "role_id", "roleid", "id");
            String rnm = m.colFlex(rt, "name", "role_name", "title", "onvan");
            if (rid != null && rnm != null && role != null) {
                roleJoin = " LEFT JOIN dbo.[" + rt + "] r ON TRY_CONVERT(nvarchar(60),r.[" + rid + "])=TRY_CONVERT(nvarchar(60),u.[" + role + "])";
                roleExpr = "COALESCE(" + Sql.txt("r", rnm, 150) + "," + Sql.txt("u", role, 80) + ",N'')";
                break;
            }
        }
        // Last login (best-effort).
        String loginUser = m.colFlex("LoginDetails", "user_id", "userid", "username", "user_name");
        String loginDate = m.colFlex("LoginDetails", "login_date", "logindate", "date", "event_time", "time");
        String loginApply = loginUser != null && loginDate != null && m.table("LoginDetails")
                ? "OUTER APPLY (SELECT MAX(" + Sql.date10("lg", loginDate) + ") lastDay FROM dbo.LoginDetails lg"
                + " WHERE TRY_CONVERT(nvarchar(100),lg.[" + loginUser + "]) IN (TRY_CONVERT(nvarchar(100),u.[" + id + "])"
                + (uname == null ? "" : ",TRY_CONVERT(nvarchar(100),u.[" + uname + "])") + ")) ll"
                : "OUTER APPLY (SELECT CAST(NULL AS nvarchar(10)) lastDay) ll";
        // Today's actions (best-effort).
        String logUser = m.colFlex("Log", "user_id", "userid", "username", "user_name");
        String logDate = m.colFlex("Log", "date", "event_time", "time", "logdate");
        // Log dates may be Jalali text or Gregorian datetime — accept today's date in both.
        String todayBoth = logDate == null ? "" : "(" + Sql.date10("lo", logDate) + " IN (" + Sql.lit(Jalali.todayStr()) + "," + Sql.lit(Jalali.todayGregorian()) + "))";
        String logApply = logUser != null && logDate != null && m.table("Log")
                ? "OUTER APPLY (SELECT COUNT_BIG(1) n FROM dbo.Log lo WHERE " + todayBoth
                + " AND TRY_CONVERT(nvarchar(100),lo.[" + logUser + "]) IN (TRY_CONVERT(nvarchar(100),u.[" + id + "])"
                + (uname == null ? "" : ",TRY_CONVERT(nvarchar(100),u.[" + uname + "])") + ")) la"
                : "OUTER APPLY (SELECT CAST(0 AS bigint) n) la";
        return new Queries.Q("SELECT " + Sql.txt("u", id, 60) + " AS id, " + nm + " AS name"
                + ", " + roleExpr + " AS role"
                + ", " + (active == null ? "N''" : "CASE WHEN " + boolTrue("u.[" + active + "]") + " THEN N'✓ فعال' ELSE N'✕ غیرفعال' END") + " AS active"
                + ", " + (phone == null ? "N''" : "COALESCE(" + Sql.txt("u", phone, 100) + ",N'')") + " AS phone"
                + ", ll.lastDay AS lastLogin, ISNULL(la.n,0) AS todayN"
                + " FROM dbo.sys_users u" + roleJoin + " " + loginApply + " " + logApply + " ORDER BY 2");
    }

    /** A user's recent logins (best-effort columns). */
    public static Queries.Q userLogins(Meta m, String userId) throws Queries.Missing {
        if (!m.table("LoginDetails")) throw new Queries.Missing("«ورودهای کاربران» در دیتابیس پیدا نشد");
        String user = m.must("LoginDetails", "کاربر", "user_id", "UserID", "userid", "username", "user_name");
        String date = m.colFlex("LoginDetails", "login_date", "logindate", "date", "event_time", "time", "datetime");
        String ip = m.colFlex("LoginDetails", "ip", "ipaddress", "host", "computer", "system");
        String desc = m.colFlex("LoginDetails", "description", "desc", "detail", "sharh");
        if (date == null && desc == null) throw new Queries.Missing("ساختار «ورودهای کاربران» قابل تشخیص نیست");
        List<Object> binds = new ArrayList<>();
        binds.add(userId);
        return new Queries.Q("SELECT TOP (100) " + (date == null ? "CAST(NULL AS nvarchar(16))" : "LEFT(TRY_CONVERT(nvarchar(30),[" + date + "]),16)") + " AS at"
                + ", " + (ip == null ? "N''" : "COALESCE(" + Sql.txt(null, ip, 120) + ",N'')") + " AS ip"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt(null, desc, 400) + ",N'')") + " AS descrip"
                + " FROM dbo.LoginDetails WHERE TRY_CONVERT(nvarchar(100),[" + user + "])=?"
                + (date == null ? "" : " ORDER BY [" + date + "] DESC"), binds);
    }

    // =====================================================================================
    // PROFIT — sales − COGS(buy_price basis) − discounts − costs. Basis is labelled in UI.
    // =====================================================================================
    private static String cogsBuyCol(Meta m) throws Queries.Missing {
        String buy = m.col("inventory", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        if (buy == null) throw new Queries.Missing("ستون قیمت خرید در «کالاها» پیدا نشد؛ بهای تمام‌شده قابل محاسبه نیست");
        return buy;
    }

    /** Resolved inventory key for COGS joins (never a hardcoded column name). */
    private static String cogsShka(Meta m) throws Queries.Missing {
        String shka = m.col("inventory", "SHKA", "shka");
        if (shka == null) throw new Queries.Missing("ستون کد کالا در «کالاها» پیدا نشد؛ بهای تمام‌شده قابل محاسبه نیست");
        return shka;
    }

    /** COGS + qty in range: cogs,qty. */
    public static Queries.Q profitCogs(Meta m, Filter f) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String buy = cogsBuyCol(m);
        String invShka = cogsShka(m);
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND ")) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        return new Queries.Q("SELECT ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + buy + "]),0)),0) AS cogs"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])),0) AS qty"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])", binds);
    }

    /** Daily sales vs COGS: day,sales,cogs. */
    public static Queries.Q profitDaily(Meta m, Filter f) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String buy = cogsBuyCol(m);
        String invShka = cogsShka(m);
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND ")) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String day = Sql.date10("h", dateCol);
        // Sales per day from the header; COGS per day from the lines (avoid fan-out by aggregating separately).
        String sql = "SELECT day AS day, ISNULL(SUM(sales),0) AS sales, ISNULL(SUM(cogs),0) AS cogs"
                + ", ISNULL(SUM(sales),0)-ISNULL(SUM(cogs),0) AS profit FROM ("
                + "SELECT " + day + " AS day, " + Sql.num("h", amountCol) + " AS sales, CAST(0 AS decimal(19,2)) AS cogs"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " UNION ALL SELECT " + day + " AS day, CAST(0 AS decimal(19,2)) AS sales"
                + ", TRY_CONVERT(decimal(19,3),d.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + buy + "]),0) AS cogs"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])"
                + ") u GROUP BY day ORDER BY day";
        List<Object> binds2 = new ArrayList<>(binds);
        binds2.addAll(binds);
        return new Queries.Q(sql, binds2);
    }

    /** Profit by product: label,qty,sales,cogs,profit. */
    public static Queries.Q profitByProduct(Meta m, Filter f, int top) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String sum = m.must("subsailfact", "جمع ردیف", "LINESUM", "linesum");
        String buy = cogsBuyCol(m);
        String invShka = cogsShka(m);
        String invName = m.col("inventory", "naka", "NAKA");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND ")) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String label = "COALESCE(" + (invName == null ? "" : Sql.txt("i", invName, 250) + ",") + Sql.txt("d", shka, 120) + ",N'—')";
        String sales = "ISNULL(SUM(" + Sql.num("d", sum) + "),0)";
        String cogs = "ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + buy + "]),0)),0)";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])),0) AS qty"
                + ", " + sales + " AS sales, " + cogs + " AS cogs, (" + sales + "-" + cogs + ") AS profit"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])"
                + " GROUP BY " + label + " ORDER BY 5 DESC", binds);
    }

    /** Other costs (UNION of resolvable sources): label,total. */
    public static Queries.Q profitCosts(Meta m, Filter f) throws Queries.Missing {
        String[][] sources = {
                {"ExternalCosts", "هزینه‌های جانبی"},
                {"IndirectCost", "هزینه‌های غیرمستقیم"},
                {"kasr_e_sanad", "کسری انبار"},
        };
        List<String> legs = new ArrayList<>();
        List<Object> binds = new ArrayList<>();
        for (String[] s : sources) {
            if (!m.table(s[0])) continue;
            String amount = m.colFlex(s[0], "mablagh", "mab", "all", "total", "price", "cost", "amount", "value");
            if (amount == null) continue;
            String date = m.colFlex(s[0], "date", "tarikh", "done_date", "donedate");
            List<String> conds = new ArrayList<>();
            if (date != null) {
                String dc = Sql.dateCond(Sql.date10("h", date), f.from, f.to, binds);
                if (!dc.isEmpty()) conds.add(dc);
            }
            legs.add("SELECT " + Sql.lit(s[1]) + " AS label, ISNULL(SUM(" + Sql.num("h", amount) + "),0) AS total"
                    + " FROM dbo.[" + s[0] + "] h" + (conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ")));
        }
        if (legs.isEmpty()) throw new Queries.Missing("هیچ جدول هزینه‌ای با ساختار شناخته‌شده پیدا نشد");
        return new Queries.Q(Sql.join(legs, " UNION ALL "), binds);
    }

    // =====================================================================================
    // LOOKUPS for the filter sheet: id + name.
    // =====================================================================================
    public static Queries.Q lookupVisitors(Meta m) throws Queries.Missing {
        String id = m.must("visitors", "کد ویزیتور", "vis_rdf", "rdf");
        String name = m.col("visitors", "vis_name", "name");
        String active = m.col("visitors", "active", "Active");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.visitors t ORDER BY "
                + (active == null ? "" : "CASE WHEN UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20),[" + active + "])))) IN (N'T',N'1',N'TRUE',N'Y') THEN 0 ELSE 1 END, ")
                + (name == null ? "1" : "2"));
    }

    public static Queries.Q lookupRoutes(Meta m) throws Queries.Missing {
        String id = m.must("masir", "کد مسیر", "rdf_masir");
        String name = m.col("masir", "name");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.masir t ORDER BY 2");
    }

    public static Queries.Q lookupCustGroups(Meta m) throws Queries.Missing {
        String id = m.must("custgroup", "کد گروه", "group_rdf");
        String name = m.col("custgroup", "group_name");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.custgroup t ORDER BY 2");
    }

    public static Queries.Q lookupKalaGroups(Meta m) throws Queries.Missing {
        String id = m.must("kagroup", "کد گروه", "group_rdf");
        String name = m.col("kagroup", "group_name");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.kagroup t ORDER BY 2");
    }

    public static Queries.Q lookupWarehouses(Meta m) throws Queries.Missing {
        String id = m.must("anbars", "کد انبار", "rdf_anbar");
        String name = m.col("anbars", "name");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.anbars t ORDER BY 2");
    }

    public static Queries.Q lookupBanks(Meta m) throws Queries.Missing {
        String id = m.must("BANK", "کد بانک", "RDF", "rdf");
        String name = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, "
                + (name == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")") + " AS name"
                + " FROM dbo.BANK t ORDER BY 2");
    }

    public static Queries.Q lookupUsers(Meta m) throws Queries.Missing {
        String id = m.must("sys_users", "کد کاربر", "user_id", "UserID", "userid");
        String uname = m.col("sys_users", "user_name", "Username", "username");
        String lname = m.col("sys_users", "user_lname");
        String nm = lname != null ? "COALESCE(" + Sql.txt("t", lname, 120) + "," + (uname == null ? Sql.txt("t", id, 60) : Sql.txt("t", uname, 120)) + ")"
                : (uname == null ? Sql.txt("t", id, 60) : "COALESCE(" + Sql.txt("t", uname, 120) + "," + Sql.txt("t", id, 60) + ")");
        return new Queries.Q("SELECT " + Sql.txt("t", id, 60) + " AS id, " + nm + " AS name FROM dbo.sys_users t ORDER BY 2");
    }

    // =====================================================================================
    // MISC REPORTS
    // =====================================================================================
    /** Monthly sales/purchases (latest months first): month,total,docs. */
    public static Queries.Q monthly(Meta m, boolean sales, int months) throws Queries.Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = m.col(table, sales ? "shfacfo" : "shfackh");
        String src = Sql.dedupe(table, numberCol, "h", "WHERE 1=1" + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x"));
        String mon = Sql.month7("h", dateCol);
        return new Queries.Q("SELECT TOP (" + Math.max(1, Math.min(36, months)) + ") " + mon + " AS month"
                + ", ISNULL(SUM(" + Sql.num("h", amountCol) + "),0) AS total, COUNT_BIG(1) AS docs"
                + " FROM " + src + " GROUP BY " + mon + " ORDER BY " + mon + " DESC");
    }

    /** Bank turnover (best-effort): bank,in,out,count. */
    public static Queries.Q bankTurnover(Meta m, Filter f) throws Queries.Missing {
        if (!m.table("ban_act")) throw new Queries.Missing("«گردش بانک» در دیتابیس پیدا نشد");
        String bank = m.must("ban_act", "بانک", "bank_rdf", "BankRdf", "bankrdf");
        String date = m.colFlex("ban_act", "date", "tarikh", "done_date", "donedate");
        String bed = m.colFlex("ban_act", "act_bed", "bed", "bedehkar");
        String bes = m.colFlex("ban_act", "act_bes", "bes", "bestankar");
        String single = m.colFlex("ban_act", "mablagh", "mab", "all", "total", "amount");
        String bankKey = m.col("BANK", "RDF", "rdf");
        String bankName = m.colFlex("BANK", "name", "bankname", "title", "onvan");
        String label = bankKey != null && bankName != null && m.table("BANK")
                ? "COALESCE(" + Sql.txt("b", bankName, 150) + "," + Sql.txt("h", bank, 60) + ",N'—')"
                : "COALESCE(" + Sql.txt("h", bank, 60) + ",N'—')";
        String join = bankKey != null && m.table("BANK")
                ? " LEFT JOIN dbo.BANK b ON TRY_CONVERT(nvarchar(60),b.[" + bankKey + "])=TRY_CONVERT(nvarchar(60),h.[" + bank + "])" : "";
        String inExpr = bes != null ? "ISNULL(SUM(" + Sql.num("h", bes) + "),0)"
                : (single != null ? "ISNULL(SUM(" + Sql.num("h", single) + "),0)" : "CAST(0 AS decimal(19,2))");
        String outExpr = bed != null ? "ISNULL(SUM(" + Sql.num("h", bed) + "),0)" : "CAST(0 AS decimal(19,2))";
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (date != null) {
            String dc = Sql.dateCond(Sql.date10("h", date), f.from, f.to, binds);
            if (!dc.isEmpty()) conds.add(dc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT " + label + " AS bank, " + inExpr + " AS in, " + outExpr + " AS out"
                + ", COUNT_BIG(1) AS count FROM dbo.ban_act h" + join + where
                + " GROUP BY " + label + " ORDER BY 2 DESC", binds);
    }

    /** Cash boxes (best-effort): name,balance. */
    public static Queries.Q cowList(Meta m) throws Queries.Missing {
        if (!m.table("COW")) throw new Queries.Missing("«صندوق‌ها» در دیتابیس پیدا نشد");
        String name = m.colFlex("COW", "name", "title", "onvan", "cow_name");
        String bal = m.colFlex("COW", "man", "balance", "mande", "mojodi", "mablagh");
        String id = m.colFlex("COW", "rdf", "id", "rowid");
        String label = name != null ? "COALESCE(" + Sql.txt("t", name, 200) + "," + (id == null ? "N'—'" : Sql.txt("t", id, 60)) + ")"
                : (id == null ? "N'صندوق'" : Sql.txt("t", id, 60));
        return new Queries.Q("SELECT " + label + " AS name, "
                + (bal == null ? "CAST(NULL AS decimal(19,2))" : "TRY_CONVERT(decimal(19,2),[" + bal + "])") + " AS balance"
                + " FROM dbo.COW ORDER BY 1");
    }

    /** Recorded changes audit (best-effort): at,tableName,username,descrip. */
    public static Queries.Q tableChanges(Meta m, Filter f) throws Queries.Missing {
        if (!m.table("TableChanges")) throw new Queries.Missing("«تغییرات ثبت‌شده» در دیتابیس پیدا نشد");
        String date = m.colFlex("TableChanges", "date", "tarikh", "event_time", "time", "datetime", "logdate");
        String table = m.colFlex("TableChanges", "table", "tablename", "table_name", "form", "entity");
        String user = m.colFlex("TableChanges", "username", "user_name", "user", "operator");
        String desc = m.colFlex("TableChanges", "description", "desc", "detail", "sharh", "action");
        if (date == null && table == null) throw new Queries.Missing("ساختار «تغییرات ثبت‌شده» قابل تشخیص نیست");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (date != null) {
            // The change log may store Jalali text or Gregorian datetime — match the range in both.
            String dc = Sql.dateCond(Sql.date10("t", date), f.from, f.to, binds);
            String dg = Sql.dateCond(Sql.date10("t", date), Jalali.toGregorian(f.from), Jalali.toGregorian(f.to), binds);
            if (!dc.isEmpty() && !dg.isEmpty()) conds.add("(" + dc + " OR " + dg + ")");
            else if (!dc.isEmpty()) conds.add(dc);
            else if (!dg.isEmpty()) conds.add(dg);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String order = date == null ? "1" : "t.[" + date + "] DESC";
        return new Queries.Q("SELECT " + (date == null ? "CAST(NULL AS nvarchar(16))" : "LEFT(TRY_CONVERT(nvarchar(30),t.[" + date + "]),16)") + " AS at"
                + ", " + (table == null ? "N''" : "COALESCE(" + Sql.txt("t", table, 150) + ",N'')") + " AS tableName"
                + ", " + (user == null ? "N''" : "COALESCE(" + Sql.txt("t", user, 120) + ",N'')") + " AS username"
                + ", " + (desc == null ? "N''" : "COALESCE(" + Sql.txt("t", desc, 400) + ",N'')") + " AS descrip"
                + " FROM dbo.TableChanges t" + where + " ORDER BY " + order
                + Queries.pageClause(binds, f.page, f.top), binds);
    }

    // =====================================================================================
    // Filter.party support: extend factor queries with a party condition when set.
    // (Kept here so Queries.java stays untouched for the common path.)
    // =====================================================================================
    /** Invoices of one party: no,date,total,paid,remain,tasvieh,dueDate,descrip. */
    public static Queries.Q partyInvoices(Meta m, boolean sales, String shmoVal, Filter f) throws Queries.Missing {
        String table = sales ? "sailfact" : "buyfact";
        Set<String> cols = m.columns(table);
        String dateCol = sales ? m.must(table, "تاریخ", "date") : m.must(table, "تاریخ", "DATE", "date");
        String amountCol = m.must(table, "مبلغ", "all");
        String numberCol = sales ? m.must(table, "شماره فاکتور", "shfacfo") : m.must(table, "شماره فاکتور", "shfackh", "shfac");
        String partyCol = m.must(table, "طرف‌حساب", "shmo", "SHMO");
        String paidCol = sales ? m.col(table, "MabDaryaftFactor", "Daryaft") : m.col(table, "MablaghPardakht", "Pardakht");
        String tasviehCol = m.col(table, "tasvieh");
        String dueCol = m.col(table, "t_date");
        String descCol = m.col(table, "description", "Explain");
        List<Object> binds = new ArrayList<>();
        binds.add(shmoVal);
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(100),x.[" + partyCol + "])=?");
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String src = Sql.dedupe(table, numberCol, "h", "WHERE " + Sql.join(conds, " AND ") + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x"));
        String total = Sql.num("h", amountCol);
        String paid = Sql.num("h", paidCol);
        return new Queries.Q("SELECT " + Sql.txt("h", numberCol, 80) + " AS no"
                + ", " + Sql.date10("h", dateCol) + " AS date"
                + ", " + total + " AS total, " + paid + " AS paid, ((" + total + ")-(" + paid + ")) AS remain"
                + ", " + (tasviehCol == null ? "N''" : "COALESCE(" + Sql.txt("h", tasviehCol, 10) + ",N'')") + " AS tasvieh"
                + ", " + (dueCol == null ? "CAST(NULL AS nvarchar(10))" : Sql.date10("h", dueCol)) + " AS dueDate"
                + ", " + (descCol == null ? "N''" : "COALESCE(" + Sql.txt("h", descCol, 500) + ",N'')") + " AS descrip"
                + " FROM " + src + " ORDER BY h.[" + dateCol + "] DESC"
                + Queries.pageClause(binds, f.page, f.top), binds);
    }

    /** Dead stock: stocked active items with no sale for `days` (or never sold): label,shka,vah,buyValue,lastSale. */
    public static Queries.Q deadStock(Meta m, int days, int top) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String naka = m.must("inventory", "نام کالا", "naka", "NAKA");
        String vah = m.must("inventory", "موجودی", "mojkavah", "MojKavah");
        String buy = m.col("inventory", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String active = m.col("inventory", "active", "Active");
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String lineShka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String cutoff = Jalali.addDays(Jalali.todayStr(), -Math.max(1, days));
        String inner = "WHERE 1=1" + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String agg = "(SELECT TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "]) AS k, MAX(" + Sql.date10("h2", dateCol) + ") AS lastSale"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h2", inner)
                + " JOIN dbo.subsailfact d2 ON TRY_CONVERT(nvarchar(100),d2.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h2.[" + numberCol + "])"
                + " GROUP BY TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "])) s";
        List<Object> binds = new ArrayList<>();
        binds.add(cutoff);
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + Sql.txt("i", naka, 250) + " AS label"
                + ", " + Sql.txt("i", shka, 60) + " AS shka"
                + ", TRY_CONVERT(decimal(19,3),i.[" + vah + "]) AS vah"
                + ", TRY_CONVERT(decimal(19,3),i.[" + vah + "])*COALESCE(TRY_CONVERT(decimal(19,2)," + (buy == null ? "NULL" : "i.[" + buy + "]") + "),0) AS buyValue"
                + ", s.lastSale AS lastSale"
                + " FROM dbo.inventory i LEFT JOIN " + agg
                + " ON s.k=TRY_CONVERT(nvarchar(100),i.[" + shka + "])"
                + " WHERE TRY_CONVERT(decimal(19,3),i.[" + vah + "])>0"
                + (active == null ? "" : " AND " + boolTrue("i.[" + active + "]"))
                + " AND (s.lastSale IS NULL OR s.lastSale<?)"
                + " ORDER BY 4 DESC", binds);
    }

    /** Profit by customer: label,code,docs,sales,cogs,profit (buy-price basis, like profitByProduct). */
    public static Queries.Q profitByCustomer(Meta m, Filter f, int top) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String partyCol = m.must("sailfact", "طرف‌حساب", "shmo", "SHMO");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String sum = m.must("subsailfact", "جمع ردیف", "LINESUM", "linesum");
        String buy = cogsBuyCol(m);
        String invShka = cogsShka(m);
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        if (!dc.isEmpty()) conds.add(dc);
        String inner = "WHERE " + (conds.isEmpty() ? "1=1" : Sql.join(conds, " AND ")) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String label = Queries.custNameExpr(m, "h", partyCol);
        String code = "TRY_CONVERT(nvarchar(100),h.[" + partyCol + "])";
        String join = Queries.custJoin(m, "h", partyCol);
        String sales = "ISNULL(SUM(" + Sql.num("d", sum) + "),0)";
        String cogs = "ISNULL(SUM(TRY_CONVERT(decimal(19,3),d.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + buy + "]),0)),0)";
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(top) + ") " + label + " AS label"
                + ", MIN(" + code + ") AS code, COUNT(DISTINCT h.[" + numberCol + "]) AS docs"
                + ", " + sales + " AS sales, " + cogs + " AS cogs, (" + sales + "-" + cogs + ") AS profit"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " JOIN dbo.subsailfact d ON TRY_CONVERT(nvarchar(100),d.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h.[" + numberCol + "])"
                + " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100),i.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d.[" + shka + "])"
                + join
                + " GROUP BY " + label + " ORDER BY 6 DESC", binds);
    }

    // =====================================================================================
    // SMART REPORTS (v13 «هوشمند»): fleeing customers, lost basket, cheque reliability,
    // visitor yield, golden hours, stock forecast, real invoice profit.
    // =====================================================================================
    /** Fleeing customers: regulars (3+ invoices/year) whose last buy is 45+ days old. */
    public static Queries.Q fleeingCustomers(Meta m, Filter f) throws Queries.Missing {
        String custShmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String cell = m.col("CUSTOMERS", "cell", "mobile", "Mobile");
        String bal = m.col("CUSTOMERS", "man", "Balance", "Mandeh");
        Set<String> sail = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String partyCol = m.must("sailfact", "طرف‌حساب", "shmo", "SHMO");
        List<Object> binds = new ArrayList<>();
        binds.add(Jalali.addDays(Jalali.todayStr(), -365));
        binds.add(Jalali.addDays(Jalali.todayStr(), -45));
        String inner = "WHERE " + Sql.date10("x", dateCol) + ">=?" + Sql.activeAnd(sail, "x") + Sql.softAnd(sail, "x");
        String agg = "(SELECT TRY_CONVERT(nvarchar(100),h2.[" + partyCol + "]) AS k"
                + ", MAX(" + Sql.date10("h2", dateCol) + ") AS lastBuy, COUNT_BIG(1) AS cnt"
                + ", AVG(TRY_CONVERT(decimal(19,2),h2.[" + amountCol + "])) AS avgBuy"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h2", inner)
                + " GROUP BY TRY_CONVERT(nvarchar(100),h2.[" + partyCol + "])) g";
        String label = custName == null ? Sql.txt("c", custShmo, 120)
                : "COALESCE(" + Sql.txt("c", custName, 250) + "," + Sql.txt("c", custShmo, 120) + ")";
        List<String> conds = new ArrayList<>();
        conds.add("g.cnt>=3");
        conds.add("g.lastBuy<?");
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(Sql.txt("c", custName == null ? custShmo : custName, 250));
            if (cell != null) exprs.add(Sql.txt("c", cell, 100));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        return new Queries.Q("SELECT TOP (200) " + label + " AS name"
                + ", " + (cell == null ? "N''" : "COALESCE(" + Sql.txt("c", cell, 100) + ",N'')") + " AS cell"
                + ", " + (bal == null ? "CAST(0 AS decimal(19,2))" : Sql.num("c", bal)) + " AS balance"
                + ", g.lastBuy AS lastBuy, CAST(-1 AS bigint) AS daysAway"
                + ", g.cnt AS invoices, ISNULL(g.avgBuy,0) AS avgBuy, ISNULL(g.avgBuy,0) AS lostEst"
                + " FROM dbo.CUSTOMERS c JOIN " + agg
                + " ON g.k=TRY_CONVERT(nvarchar(100),c.[" + custShmo + "])"
                + " WHERE " + Sql.join(conds, " AND ") + " ORDER BY ISNULL(g.avgBuy,0) DESC", binds);
    }

    /** Lost basket: customers who bought A but never its usual companion B (top-40 pairs). */
    public static Queries.Q lostBasket(Meta m, Filter f) throws Queries.Missing {
        String link = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String shka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String sum = m.col("subsailfact", "LINESUM", "linesum");
        String headNo = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String partyCol = m.must("sailfact", "طرف‌حساب", "shmo", "SHMO");
        String custShmo = m.must("CUSTOMERS", "کد", "SHMO", "shmo");
        String custName = m.col("CUSTOMERS", "MONAME", "Name", "CusName");
        String invName = m.col("inventory", "naka", "NAKA");
        String invShka = m.col("inventory", "SHKA", "shka");
        String ka = "TRY_CONVERT(nvarchar(100),a.[" + shka + "])";
        String kb = "TRY_CONVERT(nvarchar(100),b.[" + shka + "])";
        String lk = "TRY_CONVERT(nvarchar(80),a.[" + link + "])=TRY_CONVERT(nvarchar(80),b.[" + link + "])";
        String pairs = "(SELECT TOP (40) " + ka + " AS ka, " + kb + " AS kb, COUNT_BIG(1) AS n"
                + " FROM dbo.subsailfact a JOIN dbo.subsailfact b ON " + lk + " AND " + kb + ">" + ka
                + " GROUP BY " + ka + "," + kb + " HAVING COUNT_BIG(1)>=3 ORDER BY COUNT_BIG(1) DESC) p";
        String bought = "(SELECT DISTINCT TRY_CONVERT(nvarchar(100),h9.[" + partyCol + "]) AS cust"
                + ", TRY_CONVERT(nvarchar(100),d9.[" + shka + "]) AS k FROM dbo.subsailfact d9"
                + " JOIN dbo.sailfact h9 ON TRY_CONVERT(nvarchar(80),d9.[" + link + "])=TRY_CONVERT(nvarchar(80),h9.[" + headNo + "]))";
        String custLabel = custName == null ? "ba.cust"
                : "COALESCE(" + Sql.txt("cu", custName, 250) + ",ba.cust)";
        boolean useInv = invName != null && invShka != null;
        String naExpr = useInv ? "COALESCE(" + Sql.txt("ia", invName, 250) + ",p.ka)" : "p.ka";
        String nbExpr = useInv ? "COALESCE(" + Sql.txt("ib", invName, 250) + ",p.kb)" : "p.kb";
        String avgJoin = "";
        String valExpr = "CAST(0 AS decimal(19,2))";
        if (sum != null) {
            avgJoin = " LEFT JOIN (SELECT TRY_CONVERT(nvarchar(100),[" + shka + "]) AS k"
                    + ", AVG(TRY_CONVERT(decimal(19,2),[" + sum + "])) AS v FROM dbo.subsailfact"
                    + " GROUP BY TRY_CONVERT(nvarchar(100),[" + shka + "])) ab ON ab.k=p.kb";
            valExpr = "ISNULL(ab.v,0)";
        }
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("bb.cust IS NULL");
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(custLabel);
            exprs.add(naExpr);
            exprs.add(nbExpr);
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        return new Queries.Q("SELECT TOP (200) " + custLabel + " AS customer, " + naExpr + " AS bought"
                + ", " + nbExpr + " AS missed, p.n AS together, " + valExpr + " AS missedValue"
                + " FROM " + pairs
                + " JOIN " + bought + " ba ON ba.k=p.ka"
                + " LEFT JOIN " + bought + " bb ON bb.cust=ba.cust AND bb.k=p.kb"
                + " LEFT JOIN dbo.CUSTOMERS cu ON TRY_CONVERT(nvarchar(100),cu.[" + custShmo + "])=ba.cust"
                + (useInv ? " LEFT JOIN dbo.inventory ia ON TRY_CONVERT(nvarchar(100),ia.[" + invShka + "])=p.ka"
                + " LEFT JOIN dbo.inventory ib ON TRY_CONVERT(nvarchar(100),ib.[" + invShka + "])=p.kb" : "")
                + avgJoin
                + " WHERE " + Sql.join(conds, " AND ") + " ORDER BY p.n DESC, " + custLabel, binds);
    }

    /** Cheque reliability: per-customer bounce count/amount/rate + risk grade. */
    public static Queries.Q chequeReliability(Meta m, Filter f) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, true);
        if (c.amount == null) throw new Queries.Missing("ستون مبلغ در «چک‌های دریافتی» پیدا نشد");
        if (c.back == null) throw new Queries.Missing("ستون برگشتی در «چک‌های دریافتی» پیدا نشد");
        if (c.shmo == null && c.custName == null) throw new Queries.Missing("ارتباط چک با مشتری قابل تشخیص نیست");
        String backU = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "]))))";
        String isB = backU + " IN (N'T',N'1',N'TRUE')";
        String amt = "TRY_CONVERT(decimal(19,2),h.[" + c.amount + "])";
        String join = (!c.isView && c.shmo != null) ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String cellCol = (!c.isView && c.shmo != null) ? m.col("CUSTOMERS", "cell", "mobile", "Mobile") : null;
        String cellExpr = cellCol == null ? "N''" : "COALESCE(" + Sql.txt("cu", cellCol, 100) + ",N'')";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (f.search != null && !f.search.trim().isEmpty()) {
            String sc = Queries.searchCond(binds, f.search, custExpr);
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        String nBounced = "SUM(CASE WHEN " + isB + " THEN 1 ELSE 0 END)";
        return new Queries.Q("SELECT TOP (200) " + custExpr + " AS customer"
                + ", MAX(" + cellExpr + ") AS cell"
                + ", COUNT_BIG(1) AS total, " + nBounced + " AS bounced"
                + ", ISNULL(SUM(CASE WHEN " + isB + " THEN " + amt + " ELSE 0 END),0) AS bouncedAmt"
                + ", ISNULL(SUM(" + amt + "),0) AS totalAmt"
                + ", CASE WHEN COUNT_BIG(1)=0 THEN 0 ELSE ROUND(SUM(CASE WHEN " + isB + " THEN 1.0 ELSE 0 END)*100.0/COUNT_BIG(1),1) END AS rate"
                + ", CASE WHEN COUNT_BIG(1)<2 THEN N'سابقه کم' WHEN " + nBounced + "=0 THEN N'★ خوش‌قول'"
                + " WHEN SUM(CASE WHEN " + isB + " THEN 1.0 ELSE 0 END)/COUNT_BIG(1)<=0.2 THEN N'متوسط' ELSE N'⚠ پرخطر' END AS grade"
                + " FROM dbo.[" + c.table + "] h" + join + where
                + " GROUP BY " + custExpr + " ORDER BY 7 DESC, 5 DESC", binds);
    }

    /** Visitor yield: assigned / range-active / 90-day-churned customers + invoices per customer. */
    public static Queries.Q visitorYield(Meta m, Filter f) throws Queries.Missing {
        String id = m.must("visitors", "کد ویزیتور", "vis_rdf", "rdf");
        String name = m.col("visitors", "vis_name", "name");
        String custVis = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        String custShmo = m.col("CUSTOMERS", "SHMO", "shmo");
        if (custVis == null || custShmo == null) throw new Queries.Missing("ارتباط مشتری با ویزیتور در «مشتریان» پیدا نشد");
        String saleVis = m.col("sailfact", "vis_rdf", "VisitorID");
        String saleAmount = m.col("sailfact", "all");
        String saleNo = m.col("sailfact", "shfacfo");
        String saleDate = m.col("sailfact", "date");
        String saleShmo = m.col("sailfact", "shmo", "SHMO");
        boolean canSales = saleVis != null && saleAmount != null && m.table("sailfact");
        List<Object> binds = new ArrayList<>();
        String dc = "";
        if (canSales && saleDate != null) dc = Sql.dateCond(Sql.date10("x", saleDate), f.from, f.to, binds);
        String sInner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(m.columns("sailfact"), "x");
        String salesAgg = null;
        if (canSales) {
            salesAgg = "(SELECT TRY_CONVERT(nvarchar(50),h2.[" + saleVis + "]) AS v, COUNT_BIG(1) AS docs"
                    + ", ISNULL(SUM(" + Sql.num("h2", saleAmount) + "),0) AS total"
                    + (saleShmo == null ? "" : ", COUNT(DISTINCT h2.[" + saleShmo + "]) AS act")
                    + " FROM " + Sql.dedupe("sailfact", saleNo, "h2", sInner)
                    + " GROUP BY TRY_CONVERT(nvarchar(50),h2.[" + saleVis + "])) sf";
        }
        boolean canChurn = saleShmo != null && saleDate != null && m.table("sailfact");
        String churned = "CAST(0 AS bigint)";
        if (canChurn) {
            // The cutoff bind belongs to the SELECT-list subquery, so it goes FIRST.
            binds.add(0, Jalali.addDays(Jalali.todayStr(), -90));
            churned = "(SELECT COUNT_BIG(1) FROM dbo.CUSTOMERS c2"
                    + " LEFT JOIN (SELECT TRY_CONVERT(nvarchar(100),h3.[" + saleShmo + "]) AS k"
                    + ", MAX(" + Sql.date10("h3", saleDate) + ") AS lastBuy FROM dbo.sailfact h3"
                    + " GROUP BY TRY_CONVERT(nvarchar(100),h3.[" + saleShmo + "])) lb"
                    + " ON lb.k=TRY_CONVERT(nvarchar(100),c2.[" + custShmo + "])"
                    + " WHERE TRY_CONVERT(nvarchar(50),c2.[" + custVis + "])=TRY_CONVERT(nvarchar(50),t.[" + id + "])"
                    + " AND (lb.lastBuy IS NULL OR lb.lastBuy<?))";
        }
        String label = name == null ? Sql.txt("t", id, 60)
                : "COALESCE(" + Sql.txt("t", name, 150) + "," + Sql.txt("t", id, 60) + ")";
        String selDocs = salesAgg == null ? "CAST(0 AS bigint)" : "ISNULL(sf.docs,0)";
        String selSales = salesAgg == null ? "CAST(0 AS decimal(19,2))" : "ISNULL(sf.total,0)";
        String selAct = (salesAgg == null || saleShmo == null) ? "CAST(0 AS bigint)" : "ISNULL(sf.act,0)";
        return new Queries.Q("SELECT " + label + " AS name, ISNULL(ca.n,0) AS assigned"
                + ", " + selAct + " AS active, " + churned + " AS churned"
                + ", " + selDocs + " AS invoices, " + selSales + " AS sales"
                + ", CASE WHEN ISNULL(ca.n,0)=0 THEN 0 ELSE ROUND(" + selDocs + "*1.0/ISNULL(ca.n,0),2) END AS perCust"
                + " FROM dbo.visitors t"
                + " LEFT JOIN (SELECT TRY_CONVERT(nvarchar(50),[" + custVis + "]) AS v, COUNT_BIG(1) AS n"
                + " FROM dbo.CUSTOMERS GROUP BY TRY_CONVERT(nvarchar(50),[" + custVis + "])) ca"
                + " ON ca.v=TRY_CONVERT(nvarchar(50),t.[" + id + "])"
                + (salesAgg == null ? "" : " LEFT JOIN " + salesAgg + " ON sf.v=TRY_CONVERT(nvarchar(50),t.[" + id + "])")
                + (salesAgg == null ? " ORDER BY " + label : " ORDER BY ISNULL(sf.total,0) DESC"), binds);
    }

    /** Golden hours: sales by hour of time_ (falls back to weekday buckets via dif_date_alan). */
    public static Queries.Q goldenHours(Meta m, Filter f) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        String numberCol = Sql.pick(cols, "shfacfo");
        String timeCol = Sql.pick(cols, "time_");
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String src = Sql.dedupe("sailfact", numberCol, "h", inner);
        String total = "ISNULL(SUM(" + Sql.num("h", amountCol) + "),0)";
        if (timeCol != null) {
            String hh = "TRY_CONVERT(int,LEFT(LTRIM(TRY_CONVERT(nvarchar(30),h.[" + timeCol + "])),2))";
            return new Queries.Q("SELECT N'ساعت '+TRY_CONVERT(nvarchar(2)," + hh + ") AS slot"
                    + ", " + total + " AS total, COUNT_BIG(1) AS docs FROM " + src
                    + " WHERE " + hh + " BETWEEN 0 AND 23 GROUP BY " + hh + " ORDER BY " + hh, binds);
        }
        if (!m.function("dif_date_alan")) throw new Queries.Missing("ستون ساعت در فاکتور ثبت نشده و تابع تبدیل تاریخ هم در دیتابیس نیست");
        // 2024-01-06 was a Saturday: deterministic weekday without depending on DATEFIRST.
        String gdate = "DATEADD(day,dbo.dif_date_alan(" + Sql.date10("h", dateCol) + "),CAST(GETDATE() AS date))";
        String wd = "(DATEDIFF(day,'2024-01-06'," + gdate + ")%7+7)%7";
        String slot = "CASE " + wd + " WHEN 0 THEN N'شنبه' WHEN 1 THEN N'یکشنبه' WHEN 2 THEN N'دوشنبه'"
                + " WHEN 3 THEN N'سه‌شنبه' WHEN 4 THEN N'چهارشنبه' WHEN 5 THEN N'پنجشنبه' ELSE N'جمعه' END";
        return new Queries.Q("SELECT " + slot + " AS slot, " + total + " AS total, COUNT_BIG(1) AS docs"
                + " FROM " + src + " GROUP BY " + wd + "," + slot + " ORDER BY " + wd, binds);
    }

    /** Stock forecast: days until out-of-stock from 60-day average daily sales. */
    public static Queries.Q stockForecast(Meta m, Filter f) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String naka = m.must("inventory", "نام کالا", "naka", "NAKA");
        String vah = m.must("inventory", "موجودی", "mojkavah", "MojKavah");
        String active = m.col("inventory", "active", "Active");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String lineShka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        Set<String> sail = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        List<Object> binds = new ArrayList<>();
        binds.add(Jalali.addDays(Jalali.todayStr(), -60));
        String inner = "WHERE " + Sql.date10("x", dateCol) + ">=?" + Sql.activeAnd(sail, "x") + Sql.softAnd(sail, "x");
        String agg = "(SELECT TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "]) AS k"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d2.[" + qty + "])),0)/60.0 AS daily"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h2", inner)
                + " JOIN dbo.subsailfact d2 ON TRY_CONVERT(nvarchar(100),d2.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h2.[" + numberCol + "])"
                + " GROUP BY TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "])) s";
        String stock = "TRY_CONVERT(decimal(19,3),i.[" + vah + "])";
        String days = "CASE WHEN ISNULL(s.daily,0)>0 THEN " + stock + "/s.daily ELSE NULL END";
        String status = "CASE WHEN ISNULL(s.daily,0)<=0 THEN N'بدون فروش' WHEN " + stock + "/s.daily<=7 THEN N'⚠ سفارش فوری'"
                + " WHEN " + stock + "/s.daily<=21 THEN N'هشدار سفارش' ELSE N'عادی' END";
        List<String> conds = new ArrayList<>();
        conds.add(stock + ">0");
        if (active != null) conds.add(boolTrue("i.[" + active + "]"));
        if (f.search != null && !f.search.trim().isEmpty()) {
            String sc = Queries.searchCond(binds, f.search, Sql.txt("i", naka, 250));
            if (!sc.isEmpty()) conds.add(sc);
        }
        return new Queries.Q("SELECT TOP (200) " + Sql.txt("i", naka, 250) + " AS label"
                + ", " + stock + " AS vah, ISNULL(s.daily,0) AS daily, " + days + " AS daysLeft"
                + ", " + status + " AS status"
                + " FROM dbo.inventory i LEFT JOIN " + agg + " ON s.k=TRY_CONVERT(nvarchar(100),i.[" + shka + "])"
                + " WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY CASE WHEN ISNULL(s.daily,0)>0 THEN (" + stock + "/s.daily) ELSE 999999 END", binds);
    }

    /** Real profit per invoice: amount − discount − COGS(buy-price basis) + margin %. */
    public static Queries.Q invoiceProfit(Meta m, Filter f) throws Queries.Missing {
        Set<String> cols = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String partyCol = Sql.pick(cols, "shmo", "SHMO");
        String discCol = Sql.pick(cols, "tafif", "takhfif");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String lineShka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        String buy = cogsBuyCol(m);
        String invShka = cogsShka(m);
        List<Object> binds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("x", dateCol), f.from, f.to, binds);
        String inner = "WHERE " + (dc.isEmpty() ? "1=1" : dc) + Sql.activeAnd(cols, "x") + Sql.softAnd(cols, "x");
        String cogsAgg = "(SELECT TRY_CONVERT(nvarchar(80),d2.[" + linkCol + "]) AS no"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d2.[" + qty + "])*COALESCE(TRY_CONVERT(decimal(19,2),i2.[" + buy + "]),0)),0) AS cogs"
                + " FROM dbo.subsailfact d2"
                + " LEFT JOIN dbo.inventory i2 ON TRY_CONVERT(nvarchar(100),i2.[" + invShka + "])=TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "])"
                + " GROUP BY TRY_CONVERT(nvarchar(80),d2.[" + linkCol + "])) cg";
        String no = "TRY_CONVERT(nvarchar(80),h.[" + numberCol + "])";
        String amt = Sql.num("h", amountCol);
        String disc = Sql.num("h", discCol);
        String cogs = "ISNULL(cg.cogs,0)";
        String profit = "((" + amt + ")-(" + disc + ")-(" + cogs + "))";
        String margin = "CASE WHEN (" + amt + ")<>0 THEN ROUND((" + profit + ")*100.0/(" + amt + "),1) ELSE 0 END";
        List<String> conds = new ArrayList<>();
        if (f.search != null && !f.search.trim().isEmpty()) {
            List<String> exprs = new ArrayList<>();
            exprs.add(no);
            exprs.add(Queries.custNameExpr(m, "h", partyCol, "cu"));
            String sc = Queries.searchCond(binds, f.search, exprs.toArray(new String[0]));
            if (!sc.isEmpty()) conds.add(sc);
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT TOP (" + Queries.clampTop(f.top) + ") " + no + " AS no"
                + ", " + Queries.custNameExpr(m, "h", partyCol, "cu") + " AS customer"
                + ", " + Sql.date10("h", dateCol) + " AS date, " + amt + " AS amount"
                + ", " + disc + " AS discount, " + cogs + " AS cogs"
                + ", " + profit + " AS profit, " + margin + " AS margin"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h", inner)
                + " LEFT JOIN " + cogsAgg + " ON cg.no=" + no
                + Queries.custJoin(m, "h", partyCol, "cu")
                + where + " ORDER BY h.[" + dateCol + "] DESC, h.[" + numberCol + "] DESC", binds);
    }

    // =====================================================================================
    // COMPANY (v14): profile + logo bytes from dbo.company (the «تغییر نام شرکت» section).
    // =====================================================================================
    /** Company profile text: name,director,addr,tell1,tell2,cell,fax,meli,egh,pos. */
    public static Queries.Q companyProfile(Meta m) throws Queries.Missing {
        if (!m.table("company")) throw new Queries.Missing("جدول مشخصات شرکت در دیتابیس پیدا نشد");
        String[][] cols = {
                {"name", "نام شرکت", "name", "Name", "moname", "company_name"},
                {"director", "مدیرعامل", "modir_amel", "modir", "manager"},
                {"addr", "آدرس", "addre", "address", "addr"},
                {"tell1", "تلفن", "tell1", "tell", "phone"},
                {"tell2", "تلفن ۲", "tell2", "phone2"},
                {"cell", "همراه", "cell", "mobile", "Mobile"},
                {"fax", "فکس", "fax", "Fax"},
                {"meli", "شناسه ملی", "C_meli", "meli", "nationalcode"},
                {"egh", "کد اقتصادی", "C_egh", "egh", "economiccode"},
                {"pos", "کد پستی", "C_pos", "pos", "postalcode"},
        };
        StringBuilder sb = new StringBuilder("SELECT TOP (1) ");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(", ");
            String c = m.col("company", java.util.Arrays.copyOfRange(cols[i], 2, cols[i].length));
            sb.append(c == null ? "CAST(NULL AS nvarchar(400))" : Sql.txt("c", c, 400)).append(" AS ").append(cols[i][0]);
        }
        sb.append(" FROM dbo.company c");
        return new Queries.Q(sb.toString());
    }

    /** Logo byte-size (cheap change detection): len. Prefers logo, falls back to arm. */
    public static Queries.Q companyLogoLen(Meta m) throws Queries.Missing {
        String logo = companyLogoCol(m);
        return new Queries.Q("SELECT TOP (1) ISNULL(DATALENGTH([" + logo + "]),0) AS len FROM dbo.company");
    }

    /** Logo bytes: logo. */
    public static Queries.Q companyLogo(Meta m) throws Queries.Missing {
        String logo = companyLogoCol(m);
        return new Queries.Q("SELECT TOP (1) [" + logo + "] AS logo FROM dbo.company");
    }

    private static String companyLogoCol(Meta m) throws Queries.Missing {
        if (!m.table("company")) throw new Queries.Missing("جدول مشخصات شرکت در دیتابیس پیدا نشد");
        String logo = m.col("company", "logo", "Logo", "pic", "tasvir");
        if (logo == null) logo = m.col("company", "arm", "Arm");
        if (logo == null) throw new Queries.Missing("ستون لوگو در جدول شرکت پیدا نشد");
        return logo;
    }

    // =====================================================================================
    // DUES CALENDAR (v14): raw outstanding dues in a window; the screen buckets by day.
    // =====================================================================================
    /** Outstanding cheque dues (raw rows): kind,sardate,amount,num,party. */
    public static Queries.Q duesRaw(Meta m, boolean incoming, String from, String to) throws Queries.Missing {
        MoneyQueries.Chq c = MoneyQueries.chq(m, incoming);
        if (c.sardate == null) throw new Queries.Missing("ستون سررسید در «چک‌ها» پیدا نشد");
        if (c.amount == null) throw new Queries.Missing("ستون مبلغ در «چک‌ها» پیدا نشد");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        String dc = Sql.dateCond(Sql.date10("h", c.sardate), from, to, binds);
        String dg = Sql.dateCond(Sql.date10("h", c.sardate), Jalali.toGregorian(from), Jalali.toGregorian(to), binds);
        if (!dc.isEmpty() && !dg.isEmpty()) conds.add("(" + dc + " OR " + dg + ")");
        else if (!dc.isEmpty()) conds.add(dc);
        else if (!dg.isEmpty()) conds.add(dg);
        else throw new Queries.Missing("بازه سررسید نامعتبر است");
        String st = "LTRIM(RTRIM(TRY_CONVERT(nvarchar(40),h.[" + c.st + "])))";
        if (incoming) {
            conds.add(st + " IN (N'1',N'2')");
            if (c.back != null) {
                String back = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(10),h.[" + c.back + "]))))";
                conds.add("(" + back + " NOT IN (N'T',N'1',N'TRUE') OR " + back + " IS NULL)");
            }
        } else {
            conds.add(st + "=N'1'");
        }
        String join = (!c.isView && c.shmo != null) ? Queries.custJoin(m, "h", c.shmo, "cu") : "";
        String custExpr = c.custName != null ? "COALESCE(" + Sql.txt("h", c.custName, 250) + ",N'—')"
                : Queries.custNameExpr(m, "h", c.shmo, "cu");
        return new Queries.Q("SELECT " + Sql.lit(incoming ? "in" : "out") + " AS kind"
                + ", TRY_CONVERT(nvarchar(30),h.[" + c.sardate + "]) AS sardate"
                + ", " + Sql.num("h", c.amount) + " AS amount"
                + ", " + (c.num == null ? "N'—'" : "COALESCE(" + Sql.txt("h", c.num, 80) + ",N'—')") + " AS num"
                + ", " + custExpr + " AS party"
                + " FROM dbo.[" + c.table + "] h" + join + " WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY h.[" + c.sardate + "]", binds);
    }

    /** Unsettled-invoice dues (raw rows): kind,sardate,amount,num,party. */
    public static Queries.Q duesInvRaw(Meta m, String from, String to) throws Queries.Missing {
        m.must("sailfact", "وضعیت تسویه", "tasvieh");
        m.must("sailfact", "تاریخ تسویه", "t_date");
        String amountCol = m.must("sailfact", "مبلغ", "all");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String partyCol = Sql.pick(m.columns("sailfact"), "shmo", "SHMO");
        List<Object> binds = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("s.[tasvieh]='f'");
        conds.add("NULLIF(s.[t_date],'') IS NOT NULL");
        String dc = Sql.dateCond(Sql.date10("s", "t_date"), from, to, binds);
        if (dc.isEmpty()) throw new Queries.Missing("بازه سررسید نامعتبر است");
        conds.add(dc);
        String numExpr = numberCol == null ? "N'—'" : "COALESCE(" + Sql.txt("s", numberCol, 80) + ",N'—')";
        return new Queries.Q("SELECT " + Sql.lit("inv") + " AS kind"
                + ", TRY_CONVERT(nvarchar(30),s.[t_date]) AS sardate"
                + ", " + Sql.num("s", amountCol) + " AS amount, " + numExpr + " AS num"
                + ", " + Queries.custNameExpr(m, "s", partyCol, "cu") + " AS party"
                + " FROM dbo.sailfact s" + Queries.custJoin(m, "s", partyCol, "cu")
                + " WHERE " + Sql.join(conds, " AND ") + " ORDER BY s.[t_date]", binds);
    }

    // =====================================================================================
    // ORDER SUGGESTIONS (v14): 30-day cover qty per product.
    // =====================================================================================
    /** Suggested purchase list: label,vah,daily,daysLeft,suggest,status. */
    public static Queries.Q orderSuggest(Meta m, Filter f) throws Queries.Missing {
        String shka = m.must("inventory", "کد کالا", "shka");
        String naka = m.must("inventory", "نام کالا", "naka", "NAKA");
        String vah = m.must("inventory", "موجودی", "mojkavah", "MojKavah");
        String active = m.col("inventory", "active", "Active");
        String linkCol = m.must("subsailfact", "شماره فاکتور", "shfacfo");
        String lineShka = m.must("subsailfact", "کد کالا", "SHKA", "shka");
        String qty = m.must("subsailfact", "تعداد", "TEDVAH", "tedvah");
        Set<String> sail = m.columns("sailfact");
        String dateCol = m.must("sailfact", "تاریخ", "date");
        String numberCol = m.must("sailfact", "شماره فاکتور", "shfacfo");
        List<Object> binds = new ArrayList<>();
        binds.add(Jalali.addDays(Jalali.todayStr(), -60));
        String inner = "WHERE " + Sql.date10("x", dateCol) + ">=?" + Sql.activeAnd(sail, "x") + Sql.softAnd(sail, "x");
        String agg = "(SELECT TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "]) AS k"
                + ", ISNULL(SUM(TRY_CONVERT(decimal(19,3),d2.[" + qty + "])),0)/60.0 AS daily"
                + " FROM " + Sql.dedupe("sailfact", numberCol, "h2", inner)
                + " JOIN dbo.subsailfact d2 ON TRY_CONVERT(nvarchar(100),d2.[" + linkCol + "])=TRY_CONVERT(nvarchar(100),h2.[" + numberCol + "])"
                + " GROUP BY TRY_CONVERT(nvarchar(100),d2.[" + lineShka + "])) s";
        String stock = "TRY_CONVERT(decimal(19,3),i.[" + vah + "])";
        String days = "CASE WHEN ISNULL(s.daily,0)>0 THEN " + stock + "/s.daily ELSE NULL END";
        String suggest = "CASE WHEN ISNULL(s.daily,0)>0 AND s.daily*30>" + stock
                + " THEN s.daily*30-" + stock + " ELSE 0 END";
        String status = "CASE WHEN ISNULL(s.daily,0)<=0 THEN N'بدون فروش' WHEN " + stock + "/s.daily<=7 THEN N'⚠ سفارش فوری'"
                + " WHEN " + stock + "/s.daily<=21 THEN N'هشدار سفارش' ELSE N'عادی' END";
        List<String> conds = new ArrayList<>();
        conds.add(suggest + ">0");
        if (active != null) conds.add(boolTrue("i.[" + active + "]"));
        if (f.search != null && !f.search.trim().isEmpty()) {
            String sc = Queries.searchCond(binds, f.search, Sql.txt("i", naka, 250));
            if (!sc.isEmpty()) conds.add(sc);
        }
        return new Queries.Q("SELECT TOP (300) " + Sql.txt("i", naka, 250) + " AS label"
                + ", " + stock + " AS vah, ISNULL(s.daily,0) AS daily, " + days + " AS daysLeft"
                + ", " + suggest + " AS suggest, " + status + " AS status"
                + " FROM dbo.inventory i LEFT JOIN " + agg + " ON s.k=TRY_CONVERT(nvarchar(100),i.[" + shka + "])"
                + " WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY CASE WHEN ISNULL(s.daily,0)>0 THEN (" + stock + "/s.daily) ELSE 999999 END", binds);
    }
}
