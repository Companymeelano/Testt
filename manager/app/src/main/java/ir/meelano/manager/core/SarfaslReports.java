package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sarfasl (سرفصل) retrieval reports. Atiran's accounting module is not in use here; the
 * سرفصل / زیرسرفصل tables and their reporting view are the source. Read-only: nothing is written.
 */
public final class SarfaslReports {
    private SarfaslReports() { }

    /** Columns of the سرفصل balance report. */
    public static final ReportCatalog.Col[] SARFASL_COLS = {
            new ReportCatalog.Col("sarfasl", "سرفصل", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("debit", "بدهکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("credit", "بستانکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("balance", "مانده", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("docs", "ردیف", ReportCatalog.T_NUM),
    };

    /** Columns of the زیرسرفصل balance report. */
    public static final ReportCatalog.Col[] BALANCE_COLS = {
            new ReportCatalog.Col("sarfasl", "سرفصل", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("zir", "زیرسرفصل", ReportCatalog.T_TEXT),
            new ReportCatalog.Col("debit", "بدهکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("credit", "بستانکار", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("balance", "مانده", ReportCatalog.T_MONEY),
            new ReportCatalog.Col("docs", "ردیف", ReportCatalog.T_NUM),
    };

    private static final String VIEW = "VW_ReportGardeshAmaliatZirSarFasl";

    /** Resolves the reporting view and its columns, or throws a Persian Missing error. */
    private static final class Src {
        final String view;
        final String date, bed, bes, sar, zir, descr, user, rdfSar;

        Src(Meta m) throws Queries.Missing {
            view = m.qTable(VIEW);
            if (view == null) throw new Queries.Missing("«گردش سرفصل‌ها» در دیتابیس پیدا نشد");
            Set<String> c = m.columns(VIEW);
            date = need(c, "date", "تاریخ");
            bed = need(c, "bed", "بدهکار");
            bes = need(c, "bes", "بستانکار");
            sar = need(c, "NameSarfasl", "نام سرفصل");
            zir = need(c, "NameZirsarfasl", "نام زیرسرفصل");
            rdfSar = need(c, "rdfsarfasl", "شناسه سرفصل");
            descr = Sql.pick(c, "dis");
            user = Sql.pick(c, "user");
        }
    }

    private static String need(Set<String> cols, String name, String fa) throws Queries.Missing {
        String c = Sql.pick(cols, name);
        if (c == null) throw new Queries.Missing("ستون «" + fa + "» پیدا نشد");
        return c;
    }

    private static String dateWhere(String dateExpr, Filter f, List<Object> binds) {
        String dc = Sql.dateCond(dateExpr, f.from, f.to, binds);
        return dc == null || dc.isEmpty() ? "" : " AND " + dc;
    }

    private static String searchWhere(Filter f, List<Object> binds, String... exprs) {
        String s = f.search == null ? "" : f.search.trim();
        if (s.isEmpty()) return "";
        StringBuilder b = new StringBuilder(" AND (");
        for (int i = 0; i < exprs.length; i++) {
            if (i > 0) b.append(" OR ");
            b.append(exprs[i]).append(" LIKE ?");
            binds.add("%" + s + "%");
        }
        return b.append(")").toString();
    }

    /** Turnover and balance per سرفصل (all زیرسرفصل combined). */
    public static Queries.Q sarfaslBalances(Meta m, Filter f) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        String where = " WHERE 1=1" + dateWhere(Sql.date10("v", s.date), f, binds)
                + searchWhere(f, binds, "v." + Sql.q(s.sar));
        String sql = "SELECT v." + Sql.q(s.sar) + " AS sarfasl,"
                + " SUM(ISNULL(v." + Sql.q(s.bed) + ",0)) AS debit,"
                + " SUM(ISNULL(v." + Sql.q(s.bes) + ",0)) AS credit,"
                + " SUM(ISNULL(v." + Sql.q(s.bed) + ",0)-ISNULL(v." + Sql.q(s.bes) + ",0)) AS balance,"
                + " COUNT_BIG(1) AS docs"
                + " FROM " + s.view + " v" + where
                + " GROUP BY v." + Sql.q(s.rdfSar) + ", v." + Sql.q(s.sar)
                + " ORDER BY v." + Sql.q(s.sar);
        return new Queries.Q(sql, binds);
    }

    /** Turnover and balance per زیرسرفصل. */
    public static Queries.Q zirBalances(Meta m, Filter f) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        String where = " WHERE 1=1" + dateWhere(Sql.date10("v", s.date), f, binds)
                + searchWhere(f, binds, "v." + Sql.q(s.sar), "v." + Sql.q(s.zir));
        String sql = "SELECT v." + Sql.q(s.sar) + " AS sarfasl,"
                + " v." + Sql.q(s.zir) + " AS zir,"
                + " SUM(ISNULL(v." + Sql.q(s.bed) + ",0)) AS debit,"
                + " SUM(ISNULL(v." + Sql.q(s.bes) + ",0)) AS credit,"
                + " SUM(ISNULL(v." + Sql.q(s.bed) + ",0)-ISNULL(v." + Sql.q(s.bes) + ",0)) AS balance,"
                + " COUNT_BIG(1) AS docs"
                + " FROM " + s.view + " v" + where
                + " GROUP BY v." + Sql.q(s.rdfSar) + ", v." + Sql.q(s.sar) + ", v." + Sql.q(s.zir)
                + " ORDER BY v." + Sql.q(s.sar) + ", v." + Sql.q(s.zir);
        return new Queries.Q(sql, binds);
    }

    /** Individual سرفصل entries (ریز ثبت‌ها), newest first. */
    public static Queries.Q entries(Meta m, Filter f) throws Queries.Missing {
        Src s = new Src(m);
        List<Object> binds = new ArrayList<>();
        String desc = s.descr == null ? "N''" : "v." + Sql.q(s.descr);
        String user = s.user == null ? "N''" : "v." + Sql.q(s.user);
        String where = " WHERE 1=1" + dateWhere(Sql.date10("v", s.date), f, binds)
                + searchWhere(f, binds, "v." + Sql.q(s.sar), "v." + Sql.q(s.zir), desc);
        String sql = "SELECT TOP (500) " + Sql.date10("v", s.date) + " AS date,"
                + " v." + Sql.q(s.sar) + " AS sarfasl,"
                + " v." + Sql.q(s.zir) + " AS zir,"
                + " COALESCE(CAST(" + desc + " AS nvarchar(300)),N'') AS descr,"
                + " ISNULL(v." + Sql.q(s.bed) + ",0) AS debit,"
                + " ISNULL(v." + Sql.q(s.bes) + ",0) AS credit,"
                + " COALESCE(CAST(" + user + " AS nvarchar(60)),N'') AS user"
                + " FROM " + s.view + " v" + where
                + " ORDER BY " + Sql.date10("v", s.date) + " DESC";
        return new Queries.Q(sql, binds);
    }
}
