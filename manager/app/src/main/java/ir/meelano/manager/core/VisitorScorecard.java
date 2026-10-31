package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Period-based visitor scorecard. The score is deliberately based only on sales-goal attainment;
 * portfolio coverage and sales trend are shown as coaching signals, not mixed into an opaque score.
 */
public final class VisitorScorecard {
    private VisitorScorecard() { }

    private static String boolTrue(String field) {
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + "))))";
        return "(" + n + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
    }

    /** Build one row per visitor/goal-period. Java merges periods and prorates overlapping goals. */
    public static Queries.Q query(Meta m, Filter f) throws Queries.Missing {
        String visId = m.must("visitors", "کد ویزیتور", "vis_rdf", "rdf");
        String visName = m.col("visitors", "vis_name", "name", "Name", "VisitorName");
        String visActive = m.col("visitors", "active", "Active");

        String saleVis = m.must("sailfact", "ویزیتور", "vis_rdf", "VisitorID");
        String saleDate = m.must("sailfact", "تاریخ", "date");
        String saleAmount = m.must("sailfact", "مبلغ", "all_fel", "all");
        String saleNo = m.must("sailfact", "شماره فاکتور", "shfacfo");
        String saleCustomer = m.col("sailfact", "طرف‌حساب", "shmo", "SHMO");
        Set<String> saleCols = m.columns("sailfact");

        List<Object> binds = new ArrayList<>();
        String currentDate = Sql.dateCond(Sql.date10("x", saleDate), f.from, f.to, binds);
        String currentWhere = "TRY_CONVERT(nvarchar(50),x.[" + saleVis + "])=TRY_CONVERT(nvarchar(50),t.[" + visId + "])"
                + (currentDate.isEmpty() ? "" : " AND " + currentDate)
                + Sql.activeAnd(saleCols, "x") + Sql.softAnd(saleCols, "x");
        String salesApply = "OUTER APPLY (SELECT COUNT_BIG(1) AS invoices"
                + ", ISNULL(SUM(" + Sql.num("s", saleAmount) + "),0) AS sales"
                + ", " + (saleCustomer == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT s.[" + saleCustomer + "])") + " AS soldCustomers"
                + " FROM " + Sql.dedupe("sailfact", saleNo, "s", "WHERE " + currentWhere) + ") sf";

        String previousApply;
        String from = f.from == null ? "" : f.from.trim();
        String to = f.to == null ? "" : f.to.trim();
        int span = from.isEmpty() || to.isEmpty() ? -1 : Jalali.diffDays(from, to) + 1;
        if (span > 0 && span <= 3660) {
            String prevFrom = Jalali.addDays(from, -span);
            String prevTo = Jalali.addDays(from, -1);
            String prevDate = Sql.dateCond(Sql.date10("x", saleDate), prevFrom, prevTo, binds);
            String prevWhere = "TRY_CONVERT(nvarchar(50),x.[" + saleVis + "])=TRY_CONVERT(nvarchar(50),t.[" + visId + "])"
                    + (prevDate.isEmpty() ? "" : " AND " + prevDate)
                    + Sql.activeAnd(saleCols, "x") + Sql.softAnd(saleCols, "x");
            previousApply = "OUTER APPLY (SELECT ISNULL(SUM(" + Sql.num("p", saleAmount) + "),0) AS previousSales"
                    + " FROM " + Sql.dedupe("sailfact", saleNo, "p", "WHERE " + prevWhere) + ") ps";
        } else {
            previousApply = "OUTER APPLY (SELECT CAST(NULL AS decimal(19,2)) AS previousSales) ps";
        }

        String customerVisitor = m.col("CUSTOMERS", "vis_rdf", "VisitorID");
        String customerId = m.col("CUSTOMERS", "SHMO", "shmo");
        String portfolioApply;
        if (customerVisitor != null && customerId != null && m.table("CUSTOMERS")) {
            portfolioApply = "OUTER APPLY (SELECT COUNT_BIG(1) AS assignedCustomers FROM dbo.CUSTOMERS c"
                    + " WHERE TRY_CONVERT(nvarchar(50),c.[" + customerVisitor + "])=TRY_CONVERT(nvarchar(50),t.[" + visId + "])) pc";
        } else {
            portfolioApply = "OUTER APPLY (SELECT CAST(0 AS bigint) AS assignedCustomers) pc";
        }

        List<Object> goalBinds = new ArrayList<>();
        String goalApply = goalApply(m, visId, f, goalBinds);
        binds.addAll(goalBinds);

        List<String> filters = new ArrayList<>();
        if (f.visitor >= 0) {
            filters.add("TRY_CONVERT(int,t.[" + visId + "])=?");
            binds.add(f.visitor);
        }
        if (f.search != null && !f.search.trim().isEmpty() && visName != null) {
            filters.add("TRY_CONVERT(nvarchar(150),t.[" + visName + "]) LIKE ?");
            binds.add("%" + f.search.trim() + "%");
        }
        String where = filters.isEmpty() ? "" : " WHERE " + Sql.join(filters, " AND ");
        String nameExpr = visName == null ? Sql.txt("t", visId, 100)
                : "COALESCE(" + Sql.txt("t", visName, 150) + "," + Sql.txt("t", visId, 100) + ")";
        String activeExpr = visActive == null ? "CAST(NULL AS nvarchar(20))" : Sql.txt("t", visActive, 20);

        return new Queries.Q("SELECT TRY_CONVERT(nvarchar(60),t.[" + visId + "]) AS id"
                + ", " + nameExpr + " AS name, " + activeExpr + " AS active"
                + ", ISNULL(sf.sales,0) AS sales, ISNULL(sf.invoices,0) AS invoices"
                + ", ISNULL(sf.soldCustomers,0) AS soldCustomers, ISNULL(pc.assignedCustomers,0) AS assignedCustomers"
                + ", ps.previousSales AS previousSales"
                + ", gl.goalAmount AS goalAmount, gl.genericGoals AS genericGoals"
                + ", gl.scopedGoals AS scopedGoals, gl.goalFrom AS goalFrom, gl.goalTo AS goalTo"
                + " FROM dbo.visitors t " + salesApply + " " + previousApply + " " + portfolioApply + " " + goalApply
                + where + " ORDER BY ISNULL(sf.sales,0) DESC, " + nameExpr, binds);
    }

    private static String goalApply(Meta m, String visitorId, Filter f, List<Object> binds) throws Queries.Missing {
        if (!m.table("vis_goals") || !m.table("baze")) {
            return "OUTER APPLY (SELECT CAST(NULL AS decimal(19,2)) goalAmount, CAST(0 AS bigint) genericGoals,"
                    + " CAST(0 AS bigint) scopedGoals, CAST(NULL AS nvarchar(10)) goalFrom, CAST(NULL AS nvarchar(10)) goalTo) gl";
        }
        String goalVisitor = m.col("vis_goals", "vis_rdf");
        String goalPeriod = m.col("vis_goals", "baze_rdf");
        String goalAmount = m.col("vis_goals", "mab");
        String periodId = m.col("baze", "rdf");
        String periodStart = m.col("baze", "sta");
        String periodEnd = m.col("baze", "end_");
        if (goalVisitor == null || goalPeriod == null || goalAmount == null || periodId == null
                || periodStart == null || periodEnd == null) {
            return "OUTER APPLY (SELECT CAST(NULL AS decimal(19,2)) goalAmount, CAST(0 AS bigint) genericGoals,"
                    + " CAST(0 AS bigint) scopedGoals, CAST(NULL AS nvarchar(10)) goalFrom, CAST(NULL AS nvarchar(10)) goalTo) gl";
        }

        List<String> genericParts = new ArrayList<>();
        String[] dimensions = {"CustomerGroupRdf", "KalaGroupRdf", "InventoryID", "ProvinceID", "CityID",
                "RegionID", "PathID", "SysID", "QuarterID"};
        for (String dimension : dimensions) {
            String column = m.col("vis_goals", dimension);
            if (column != null) genericParts.add("(g.[" + column + "] IS NULL OR TRY_CONVERT(decimal(19,0),g.[" + column + "])=0)");
        }
        String generic = genericParts.isEmpty() ? "1=1" : Sql.join(genericParts, " AND ");

        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(50),g.[" + goalVisitor + "])=TRY_CONVERT(nvarchar(50),t.[" + visitorId + "])");
        conds.add("TRY_CONVERT(nvarchar(50),g.[" + goalPeriod + "])=TRY_CONVERT(nvarchar(50),b.[" + periodId + "])");
        String goalActive = m.col("vis_goals", "Active", "active");
        if (goalActive != null) conds.add(boolTrue("g.[" + goalActive + "]"));
        String periodActive = m.col("baze", "Active", "active");
        if (periodActive != null) conds.add(boolTrue("b.[" + periodActive + "]"));
        String startExpr = Sql.date10("b", periodStart);
        String endExpr = Sql.date10("b", periodEnd);
        if (f.from != null && !f.from.trim().isEmpty()) {
            conds.add(endExpr + ">=?");
            binds.add(f.from.trim());
        }
        if (f.to != null && !f.to.trim().isEmpty()) {
            conds.add(startExpr + "<=?");
            binds.add(f.to.trim());
        }
        String goalActiveFilter = " WHERE " + Sql.join(conds, " AND ");
        String num = Sql.num("g", goalAmount);
        String sql = "OUTER APPLY (SELECT SUM(CASE WHEN " + generic + " THEN " + num + " ELSE 0 END) AS goalAmount"
                + ", SUM(CASE WHEN " + generic + " THEN 1 ELSE 0 END) AS genericGoals"
                + ", SUM(CASE WHEN " + generic + " THEN 0 ELSE 1 END) AS scopedGoals"
                + ", " + startExpr + " AS goalFrom, " + endExpr + " AS goalTo"
                + " FROM dbo.vis_goals g JOIN dbo.baze b ON TRY_CONVERT(nvarchar(50),b.[" + periodId + "])"
                + "=TRY_CONVERT(nvarchar(50),g.[" + goalPeriod + "])" + goalActiveFilter
                + " GROUP BY b.[" + periodId + "], " + startExpr + ", " + endExpr + ") gl";
        return sql;
    }

    /** Merge period rows, prorate active generic goals over their Jalali overlap, and assign actions. */
    public static List<Row> score(List<Row> source, Filter f) {
        Map<String, Row> merged = new LinkedHashMap<>();
        if (source != null) {
            for (Row raw : source) {
                String id = raw.s("id");
                if (id.isEmpty()) continue;
                Row row = merged.get(id);
                if (row == null) {
                    row = new Row();
                    row.putAll(raw);
                    row.put("target", 0d);
                    row.put("goalRows", 0L);
                    row.put("scopedRows", 0L);
                    row.put("targetInvalid", false);
                    merged.put(id, row);
                }
                long genericRows = raw.l("genericGoals");
                long scopedRows = raw.l("scopedGoals");
                row.put("goalRows", row.l("goalRows") + genericRows);
                row.put("scopedRows", row.l("scopedRows") + scopedRows);
                if (genericRows > 0) {
                    double contribution = proratedGoal(raw.d("goalAmount"), raw.s("goalFrom"), raw.s("goalTo"), f);
                    if (Double.isNaN(contribution) || Double.isInfinite(contribution)) {
                        row.put("targetInvalid", true);
                    } else {
                        row.put("target", row.d("target") + contribution);
                    }
                }
            }
        }

        List<Row> result = new ArrayList<>(merged.values());
        for (Row row : result) {
            double target = row.d("target");
            double sales = row.d("sales");
            boolean hasWindow = f != null && f.hasRange();
            boolean inactive = isFalse(row.s("active"));
            boolean eligible = hasWindow && !inactive && row.l("goalRows") > 0
                    && !Boolean.TRUE.equals(row.get("targetInvalid")) && target > 0;
            double attainment = eligible ? sales * 100d / target : 0d;
            double score = eligible ? clamp(attainment, 0d, 100d) : -1d;
            double coverage = row.l("assignedCustomers") > 0
                    ? row.l("soldCustomers") * 100d / row.l("assignedCustomers") : -1d;
            double previous = row.d("previousSales");
            boolean hasPrevious = row.has("previousSales");
            double trend = hasPrevious && previous > 0 ? (sales - previous) * 100d / previous : Double.NaN;

            row.put("score", score < 0 ? "—" : percent(score, false));
            row.put("attainment", eligible ? percent(attainment, false) : "—");
            row.put("coverage", coverage < 0 ? "—" : percent(coverage, false));
            String portfolio = Money.fa(String.valueOf(row.l("soldCustomers"))) + "/"
                    + Money.fa(String.valueOf(row.l("assignedCustomers")));
            if (coverage >= 0) portfolio += " • " + percent(coverage, false);
            row.put("portfolio", portfolio);
            row.put("growth", Double.isNaN(trend) || Double.isInfinite(trend) ? "—" : percent(trend, true));
            row.put("action", action(row, eligible, score, inactive, hasWindow));
            row.remove("goalRows");
            row.remove("scopedRows");
            row.remove("targetInvalid");
            row.remove("goalAmount");
            row.remove("genericGoals");
            row.remove("scopedGoals");
            row.remove("goalFrom");
            row.remove("goalTo");
            row.remove("previousSales");
        }
        Collections.sort(result, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) {
                double as = scoreValue(a.s("score"));
                double bs = scoreValue(b.s("score"));
                int byScore = Double.compare(bs, as);
                if (byScore != 0) return byScore;
                return Double.compare(b.d("sales"), a.d("sales"));
            }
        });
        return result;
    }

    private static double proratedGoal(double goal, String periodFrom, String periodTo, Filter f) {
        if (periodFrom == null || periodFrom.isEmpty() || periodTo == null || periodTo.isEmpty()) return Double.NaN;
        int periodDays = Jalali.diffDays(periodFrom, periodTo) + 1;
        if (periodDays <= 0) return Double.NaN;
        String overlapFrom = periodFrom;
        String overlapTo = periodTo;
        if (f != null && f.from != null && !f.from.trim().isEmpty()
                && Jalali.diffDays(overlapFrom, f.from.trim()) >= 0) overlapFrom = f.from.trim();
        if (f != null && f.to != null && !f.to.trim().isEmpty()
                && Jalali.diffDays(f.to.trim(), overlapTo) >= 0) overlapTo = f.to.trim();
        int overlapDays = Jalali.diffDays(overlapFrom, overlapTo) + 1;
        if (overlapDays <= 0) return 0d;
        return goal * overlapDays / periodDays;
    }

    private static String action(Row row, boolean eligible, double score, boolean inactive, boolean hasWindow) {
        if (inactive) return "غیرفعال؛ خارج از گروه اقدام";
        if (!hasWindow) return "بازه نامحدود؛ امتیاز دوره‌ای ندارد";
        if (!eligible) {
            if (Boolean.TRUE.equals(row.get("targetInvalid"))) return "تاریخ هدف نامعتبر؛ بازبینی دوره";
            if (row.l("goalRows") == 0 && row.l("scopedRows") > 0) return "هدف تفکیک‌شده؛ مبنای عمومی ندارد";
            if (row.l("goalRows") == 0) return "هدف فعال عمومی تعریف نشده";
            return "هدف صفر/نامعتبر؛ بازبینی هدف";
        }
        String result;
        if (score >= 100) result = "پیشتاز؛ تثبیت و توسعه";
        else if (score >= 80) result = "نزدیک هدف؛ رفع مانع";
        else if (score >= 60) result = "برنامه جبرانی";
        else result = "مداخله فوری و همراهی";
        if (row.l("assignedCustomers") > 0
                && row.l("soldCustomers") * 100d / row.l("assignedCustomers") < 50d)
            result += " • پوشش مشتری پایین";
        if (row.l("scopedRows") > 0) result += " • هدف تفکیکی لحاظ نشد";
        return result;
    }

    private static boolean isFalse(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        return "f".equals(v) || "false".equals(v) || "0".equals(v) || "n".equals(v) || "no".equals(v);
    }

    private static String percent(double value, boolean showSign) {
        String rounded = String.valueOf(Math.round(value));
        if (showSign && value > 0.5d) rounded = "+" + rounded;
        return Money.fa(rounded) + "٪";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double scoreValue(String visible) {
        if (visible == null || visible.equals("—")) return -1d;
        String value = Money.en(visible).replace("٪", "").replace("+", "").trim();
        try { return Double.parseDouble(value); }
        catch (Exception ignored) { return -1d; }
    }
}
