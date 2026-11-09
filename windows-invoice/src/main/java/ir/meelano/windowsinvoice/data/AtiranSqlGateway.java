package ir.meelano.windowsinvoice.data;

import ir.meelano.windowsinvoice.AtiranGateway;
import ir.meelano.windowsinvoice.InvoiceMath;
import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceLine;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;
import ir.meelano.windowsinvoice.CustomerModels;
import ir.meelano.windowsinvoice.CustomerModels.BalanceFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerDirectoryRow;
import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.CustomerListFilter;
import ir.meelano.windowsinvoice.CustomerModels.CustomerPage;
import ir.meelano.windowsinvoice.CustomerModels.CustomerSort;
import ir.meelano.windowsinvoice.CustomerModels.CustomerStatusFilter;
import ir.meelano.windowsinvoice.CustomerModels.DetailField;
import ir.meelano.windowsinvoice.CustomerModels.LedgerCategory;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import ir.meelano.windowsinvoice.CustomerModels.LedgerFilter;
import ir.meelano.windowsinvoice.CustomerModels.TransactionDetails;
import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionJdbc;
import ir.meelano.windowsinvoice.sections.SectionModels;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRequest;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionPost;
import ir.meelano.windowsinvoice.sections.SectionSql;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SQL Server gateway for the existing Atiran invoice procedure contract.
 * Credentials are read from the local process environment and are never persisted or logged.
 * Invoice writes require the explicit ATIRAN_ALLOW_INVOICE_WRITES=true safety switch.
 */
public final class AtiranSqlGateway implements AtiranGateway {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    // Same Atiran stock-out act IDs used by the Android stock-ledger reader.
    private static final String STOCK_OUT_ACTS = "20,22,5,19,18,48,26,85,133";
    /** Section reports aggregate over whole tables; they get a longer budget than a single lookup. */
    private static final int SECTION_QUERY_TIMEOUT_SECONDS = 90;
    private final String jdbcUrl;
    private final String sqlUser;
    private final String sqlPassword;
    private final int systemUserId;
    private final int visitorId;
    private final String atiranUsername;
    private final int warehouseId;
    private final boolean writesAllowed;
    private volatile boolean connectionAvailable;

    private AtiranSqlGateway(String jdbcUrl, String sqlUser, String sqlPassword,
                             int systemUserId, int visitorId, String atiranUsername,
                             int warehouseId, boolean writesAllowed) {
        this.jdbcUrl = jdbcUrl;
        this.sqlUser = sqlUser;
        this.sqlPassword = sqlPassword;
        this.systemUserId = systemUserId;
        this.visitorId = visitorId;
        this.atiranUsername = atiranUsername;
        this.warehouseId = warehouseId;
        this.writesAllowed = writesAllowed;
    }

    /** Empty means the app should visibly start in demo mode; partial config is an error, not a silent fallback. */
    public static Optional<AtiranSqlGateway> fromEnvironment() {
        String url = env("ATIRAN_JDBC_URL");
        if (url.isEmpty()) {
            if (hasAnyAtiranSetting(System.getenv()))
                throw new IllegalStateException("تنظیمات آتیران ناقص است؛ ATIRAN_JDBC_URL را هم تنظیم کن یا همهٔ تنظیمات آتیران را پاک کن.");
            return Optional.empty();
        }
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        if (!normalizedUrl.startsWith("jdbc:sqlserver://")) {
            throw new IllegalStateException("ATIRAN_JDBC_URL باید از درایور رسمی SQL Server استفاده کند.");
        }
        if (normalizedUrl.contains("password=") || normalizedUrl.contains(";pwd=")
                || normalizedUrl.contains(";user=") || normalizedUrl.contains(";username=")) {
            throw new IllegalStateException("نام کاربری و رمز را داخل URL قرار نده؛ از متغیرهای محلی جدا استفاده کن.");
        }
        // Encryption is mandatory only for an internet-facing endpoint; a server inside the local
        // network may be configured without it (the operator's own LAN/test server).
        if (SqlServerUrl.requiresEncryption(hostOf(url))
                && (jdbcPropertyEquals(url, "encrypt", "false")
                    || jdbcPropertyEquals(url, "trustServerCertificate", "true"))) {
            throw new IllegalStateException("برای اتصال از بیرون شبکهٔ محلی، رمزنگاری و اعتبارسنجی گواهی سرور لازم است.");
        }
        String user = requiredEnv("ATIRAN_SQL_USER");
        String password = requiredSecretEnv("ATIRAN_SQL_PASSWORD");
        int sysUser = positiveIntEnv("ATIRAN_SYS_USER_ID");
        int visitor = positiveIntEnv("ATIRAN_VISITOR_ID");
        String auditName = requiredEnv("ATIRAN_USERNAME");
        if (auditName.length() > 100) throw new IllegalStateException("نام ممیزی آتیران حداکثر ۱۰۰ نویسه باشد.");
        int warehouse = nonNegativeIntEnv("ATIRAN_WAREHOUSE_ID", 0);
        boolean writes = "true".equalsIgnoreCase(env("ATIRAN_ALLOW_INVOICE_WRITES"));
        return Optional.of(new AtiranSqlGateway(url, user, password, sysUser, visitor,
                auditName, warehouse, writes));
    }

    /**
     * Builds the live gateway from the connection profile saved on this Windows account
     * (endpoint + database + SQL login) and the signed-in Atiran session that supplies the
     * audit identity, visitor id and warehouse. No credential is taken from the environment.
     */
    public static AtiranSqlGateway fromProfile(WindowsSqlSettings.Endpoint endpoint,
                                              AtiranLogin.Session session,
                                              boolean writesAllowed) {
        if (endpoint == null || !endpoint.complete())
            throw new IllegalStateException("اتصال SQL ذخیره‌شده کامل نیست؛ سرور، پورت، دیتابیس و ورود SQL را تنظیم کن.");
        if (session == null)
            throw new IllegalStateException("برای استفاده از دادهٔ زنده، ابتدا با حساب آتیران وارد شو.");
        String url = SqlServerUrl.build(endpoint.host, endpoint.port, endpoint.database,
                !SqlServerUrl.requiresEncryption(endpoint.host));
        if (!SqlServerUrl.urlIsAcceptable(url, endpoint.host))
            throw new IllegalStateException("تنظیم رمزنگاری برای این endpoint قابل قبول نیست.");
        return new AtiranSqlGateway(url, endpoint.username, endpoint.password,
                Math.max(0, session.userId), Math.max(0, session.visitorId),
                session.username == null ? "" : session.username,
                Math.max(0, session.warehouseId), writesAllowed);
    }

    /** Host part of a JDBC SQL Server URL ("" when it cannot be parsed). */
    static String hostOf(String url) {
        if (url == null) return "";
        String text = url.trim();
        int scheme = text.indexOf("://");
        if (scheme < 0) return "";
        text = text.substring(scheme + 3);
        int semi = text.indexOf(';');
        if (semi >= 0) text = text.substring(0, semi);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            return close > 0 ? text.substring(1, close) : "";
        }
        int colon = text.lastIndexOf(':');
        return colon > 0 && text.indexOf(':') == colon ? text.substring(0, colon).trim() : text.trim();
    }

    static boolean hasAnyAtiranSetting(Map<String, String> environment) {
        if (environment == null) return false;
        for (String name : Set.of("ATIRAN_JDBC_URL", "ATIRAN_SQL_USER", "ATIRAN_SQL_PASSWORD",
                "ATIRAN_SYS_USER_ID", "ATIRAN_VISITOR_ID", "ATIRAN_USERNAME",
                "ATIRAN_WAREHOUSE_ID", "ATIRAN_ALLOW_INVOICE_WRITES")) {
            String value = environment.get(name);
            if (value == null) continue;
            if (name.equals("ATIRAN_SQL_PASSWORD") ? !value.isEmpty() : !value.isBlank()) return true;
        }
        return false;
    }

    @Override public boolean live() { return true; }
    @Override public boolean connected() { return connectionAvailable; }
    @Override public boolean writeEnabled() { return writesAllowed && connectionAvailable; }
    @Override public String connectionStatus() {
        if (!connectionAvailable) return "SQL Server پیکربندی شده · اتصال هنوز آزموده نشده";
        return writesAllowed ? "اتصال آتیران برقرار · ثبت با تأیید کاربر" : "اتصال آتیران برقرار · فقط‌خواندنی";
    }

    @Override public void testConnection() throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1")) {
                ps.setQueryTimeout(10);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) != 1) throw new SQLException("پاسخ معتبر از SQL Server دریافت نشد.");
                }
            }
        } catch (SQLException ex) {
            connectionAvailable = false;
            throw ex;
        }
    }

    @Override public List<Customer> searchCustomers(String query) throws SQLException {
        String term = normalize(query);
        requireSearchTerm(term);
        String pattern = "%" + escapeLike(term) + "%";
        String sql = "SELECT TOP (80) TRY_CONVERT(bigint,SHMO), "
                + "ISNULL(TRY_CONVERT(nvarchar(250),MONAME),N''), "
                + "ISNULL(TRY_CONVERT(nvarchar(100),cell),N''), "
                + "ISNULL(TRY_CONVERT(decimal(19,2),man),0) "
                + "FROM dbo.CUSTOMERS "
                + "WHERE UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(5),active),N'f')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') "
                + "AND ISNULL(TRY_CONVERT(int,black_list),0)=0 "
                + "AND TRY_CONVERT(bigint,SHMO) BETWEEN 1 AND 2147483647 "
                + "AND (TRY_CONVERT(nvarchar(100),SHMO) LIKE ? ESCAPE '\' "
                + "OR TRY_CONVERT(nvarchar(250),MONAME) LIKE ? ESCAPE '\' "
                + "OR TRY_CONVERT(nvarchar(100),cell) LIKE ? ESCAPE '\') "
                + "ORDER BY MONAME";
        List<Customer> result = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(20);
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Customer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)));
            }
        }
        return result;
    }

    @Override public List<Product> searchProducts(String query) throws Exception {
        String term = normalize(query);
        requireSearchTerm(term);
        String pattern = "%" + escapeLike(term) + "%";
        List<Product> result = new ArrayList<>();
        try (Connection c = open()) {
            int priceGroup = readPriceGroup(c);
            String priceColumn = "forosh" + priceGroup; // group is validated as 1..5 before interpolation.
            List<String> barcodeColumns = productBarcodeColumns(c);
            String barcodeExpr = productBarcodeExpression(barcodeColumns);
            StringBuilder barcodeSearch = new StringBuilder();
            for (String column : barcodeColumns) {
                barcodeSearch.append(" OR TRY_CONVERT(nvarchar(100),")
                        .append(quotedProductColumn(column)).append(") LIKE ? ESCAPE '\'");
            }
            String sql = "SELECT TOP (100) TRY_CONVERT(bigint,i.shka), "
                    + "ISNULL(TRY_CONVERT(nvarchar(500),i.naka),N''), "
                    + barcodeExpr + ", "
                    + "ISNULL(TRY_CONVERT(nvarchar(80),i.vahsanj),N'عدد'), "
                    + "ISNULL(TRY_CONVERT(int,i.mohvah),1), "
                    + "ISNULL(TRY_CONVERT(nvarchar(80),i.bastebandi),N''), "
                    + "CASE WHEN px.price_rows=1 THEN ISNULL(px.unit_price,0) ELSE CAST(0 AS decimal(19,2)) END, px.price_rows "
                    + "FROM dbo.inventory i OUTER APPLY (SELECT COUNT_BIG(1) price_rows, "
                    + "MAX(TRY_CONVERT(decimal(19,2),p.[" + priceColumn + "])) unit_price FROM dbo.forosh_price p "
                    + "WHERE TRY_CONVERT(nvarchar(100),p.shka)=TRY_CONVERT(nvarchar(100),i.shka) "
                    + "AND UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(5),p.active),N'f')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES')) px "
                    + "WHERE UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(5),i.active),N'f')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') "
                    + "AND ISNULL(TRY_CONVERT(int,i.black_list),0)=0 "
                    + "AND TRY_CONVERT(bigint,i.shka)>0 "
                    + "AND (TRY_CONVERT(nvarchar(100),i.shka) LIKE ? ESCAPE '\' "
                    + "OR TRY_CONVERT(nvarchar(500),i.naka) LIKE ? ESCAPE '\'" + barcodeSearch + ") "
                    + "ORDER BY i.naka";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(25);
                int parameter = 1;
                ps.setString(parameter++, pattern);
                ps.setString(parameter++, pattern);
                for (int i = 0; i < barcodeColumns.size(); i++) ps.setString(parameter++, pattern);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal rate = rs.getBigDecimal(7);
                        result.add(new Product(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rate == null ? BigDecimal.ZERO : rate, Math.max(1, rs.getInt(5)),
                                rs.getString(6), rs.getInt(8)));
                    }
                }
            }
        }
        return result;
    }

    /** Full, parameterized customer directory. This method contains SELECTs only. */
    @Override public CustomerPage listCustomers(CustomerListFilter filter) throws SQLException {
        CustomerListFilter f = filter == null ? CustomerListFilter.defaults() : filter;
        try (Connection c = open()) {
            List<String> customerColumns = readTableColumns(c, "CUSTOMERS");
            String code = resolveFlexibleColumn(customerColumns, "SHMO", "shmo", "CustomerCode");
            if (code == null) throw new SQLException("ستون کد مشتری در dbo.CUSTOMERS پیدا نشد.");
            String name = resolveFlexibleColumn(customerColumns, "MONAME", "Name", "CusName", "CustomerName");
            String phone = resolveFlexibleColumn(customerColumns, "cell", "mobile", "Mobile", "tell1", "tel1", "phone");
            String balance = resolveFlexibleColumn(customerColumns, "man", "Balance", "Mandeh", "mande", "account_balance", "مانده");
            String credit = resolveFlexibleColumn(customerColumns, "cred", "credit_limit", "Credit", "etebar", "اعتبار");
            String group = resolveFlexibleColumn(customerColumns, "group_rdf", "groupid", "CustomerGroupID");
            String route = resolveFlexibleColumn(customerColumns, "RDF_masir", "masir_rdf", "route_id", "RouteID");
            String visitor = resolveFlexibleColumn(customerColumns, "vis_rdf", "VisitorID", "visid");
            String active = resolveFlexibleColumn(customerColumns, "active", "is_active");
            String blocked = resolveFlexibleColumn(customerColumns, "black_list", "blacklist", "blocked", "is_blocked");
            String debit = resolveFlexibleColumn(customerColumns, "bed", "debit", "bedehkar");
            String creditor = resolveFlexibleColumn(customerColumns, "bes", "creditor", "bestankar");

            String codeExpr = "TRY_CONVERT(bigint," + sqlColumn("c", code) + ")";
            String nameExpr = name == null ? "CAST(N'' AS nvarchar(250))" : "ISNULL(TRY_CONVERT(nvarchar(250)," + sqlColumn("c", name) + "),N'')";
            String phoneExpr = phone == null ? "CAST(N'' AS nvarchar(100))" : "ISNULL(TRY_CONVERT(nvarchar(100)," + sqlColumn("c", phone) + "),N'')";
            String balanceExpr = balance != null ? numericColumn("c", balance)
                    : debit != null && creditor != null ? "(" + numericColumn("c", debit) + "-" + numericColumn("c", creditor) + ")"
                    : "CAST(0 AS decimal(19,2))";
            String balanceApply = "";
            try {
                List<String> actColumns = readTableColumns(c, "cust_act");
                String actCustomer = resolveFlexibleColumn(actColumns, "shmo", "SHMO", "CustomerCode");
                String actDebit = resolveFlexibleColumn(actColumns, "act_bed", "bed", "debit");
                String actCredit = resolveFlexibleColumn(actColumns, "act_bes", "bes", "credit");
                String actActive = resolveFlexibleColumn(actColumns, "isActive", "active", "is_active");
                if (actCustomer != null && actDebit != null && actCredit != null) {
                    String activeCondition = actActive == null ? "" : " AND (UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10)," + sqlColumn("a", actActive) + "),N't')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') OR " + sqlColumn("a", actActive) + " IS NULL)";
                    balanceApply = " OUTER APPLY (SELECT ISNULL(SUM(" + numericColumn("a", actDebit) + "),0)-ISNULL(SUM(" + numericColumn("a", actCredit) + "),0) AS balance FROM dbo.cust_act a WHERE TRY_CONVERT(bigint," + sqlColumn("a", actCustomer) + ")=TRY_CONVERT(bigint," + sqlColumn("c", code) + ")" + activeCondition + ") ca ";
                    balanceExpr = "ISNULL(ca.balance,0)";
                }
            } catch (SQLException ignored) {
                // Keep using CUSTOMERS.man on installations without readable cust_act metadata.
            }
            String creditExpr = credit == null ? "CAST(0 AS decimal(19,2))" : numericColumn("c", credit);
            String groupExpr = textColumnOrEmpty("c", group, 120);
            String routeExpr = textColumnOrEmpty("c", route, 120);
            String visitorExpr = textColumnOrEmpty("c", visitor, 120);
            String activeExpr = active == null ? "CAST(1 AS int)" : "CASE WHEN UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10)," + sqlColumn("c", active) + "),N'f')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') THEN 1 ELSE 0 END";
            String blockedExpr = blocked == null ? "CAST(0 AS int)" : "CASE WHEN UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10)," + sqlColumn("c", blocked) + "),N'0')))) IN (N'1',N'T',N'TRUE',N'Y',N'YES') THEN 1 ELSE 0 END";

            List<Object> params = new ArrayList<>();
            List<String> where = new ArrayList<>();
            where.add(codeExpr + " > 0");
            String search = normalize(f.query());
            if (!search.isEmpty()) {
                String pattern = "%" + escapeLike(search) + "%";
                List<String> terms = new ArrayList<>();
                terms.add("TRY_CONVERT(nvarchar(100)," + sqlColumn("c", code) + ") LIKE ? ESCAPE '\'");
                params.add(pattern);
                if (name != null) { terms.add("TRY_CONVERT(nvarchar(250)," + sqlColumn("c", name) + ") LIKE ? ESCAPE '\'"); params.add(pattern); }
                if (phone != null) { terms.add("TRY_CONVERT(nvarchar(100)," + sqlColumn("c", phone) + ") LIKE ? ESCAPE '\'"); params.add(pattern); }
                where.add("(" + String.join(" OR ", terms) + ")");
            }
            switch (f.balance()) {
                case DEBTORS -> where.add("(" + balanceExpr + ") > 1");
                case CREDITORS -> where.add("(" + balanceExpr + ") < -1");
                case SETTLED -> where.add("ABS(" + balanceExpr + ") <= 1");
                default -> { }
            }
            switch (f.status()) {
                case ACTIVE -> where.add("(" + activeExpr + ")=1 AND (" + blockedExpr + ")=0");
                case BLOCKED -> where.add("(" + blockedExpr + ")=1");
                case INACTIVE -> where.add("(" + activeExpr + ")=0 AND (" + blockedExpr + ")=0");
                default -> { }
            }
            addTextFilter(where, params, groupExpr, stripCodePrefix(f.groupQuery(), "گروه"));
            addTextFilter(where, params, routeExpr, stripCodePrefix(f.routeQuery(), "مسیر"));
            addTextFilter(where, params, visitorExpr, stripCodePrefix(f.visitorQuery(), "ویزیتور"));
            if (f.minBalance() != null) { where.add("(" + balanceExpr + ") >= ?"); params.add(f.minBalance()); }
            if (f.maxBalance() != null) { where.add("(" + balanceExpr + ") <= ?"); params.add(f.maxBalance()); }
            String whereSql = " WHERE " + String.join(" AND ", where);

            long total;
            boolean balanceFiltering = f.balance() != BalanceFilter.ALL || f.minBalance() != null || f.maxBalance() != null;
            String countBalanceApply = balanceFiltering ? balanceApply : "";
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT_BIG(1) FROM dbo.CUSTOMERS c " + countBalanceApply + whereSql)) {
                count.setQueryTimeout(30);
                bindParameters(count, params);
                try (ResultSet rs = count.executeQuery()) { total = rs.next() ? rs.getLong(1) : 0; }
            }

            String salesApply = "OUTER APPLY (SELECT CAST(0 AS decimal(19,2)) AS sales_total, CAST(0 AS bigint) AS sales_count, CAST(N'' AS nvarchar(30)) AS last_sale) sx ";
            try {
                List<String> salesColumns = readTableColumns(c, "sailfact");
                String saleCustomer = resolveFlexibleColumn(salesColumns, "shmo", "CustomerCode", "customer");
                String saleAmount = resolveFlexibleColumn(salesColumns, "all", "all_fel", "amount", "total", "mablagh");
                String saleDate = resolveFlexibleColumn(salesColumns, "date", "t_date", "tarikh", "Date");
                String saleActive = resolveFlexibleColumn(salesColumns, "active", "isActive");
                if (saleCustomer != null && saleAmount != null) {
                    String live = saleActive == null ? "" : " AND UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10),s." + quoteIdentifier(saleActive) + "),N't')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES')";
                    String last = saleDate == null ? "CAST(N'' AS nvarchar(30))" : "ISNULL(MAX(TRY_CONVERT(nvarchar(30),s." + quoteIdentifier(saleDate) + ")),N'')";
                    salesApply = "OUTER APPLY (SELECT ISNULL(SUM(" + numericColumn("s", saleAmount) + "),0) AS sales_total, COUNT_BIG(1) AS sales_count, " + last + " AS last_sale FROM dbo.sailfact s WHERE TRY_CONVERT(nvarchar(100),s." + quoteIdentifier(saleCustomer) + ")=TRY_CONVERT(nvarchar(100)," + sqlColumn("c", code) + ")" + live + ") sx ";
                }
            } catch (SQLException ignored) {
                // Older installations without sailfact still get a complete directory with zero sales metadata.
            }
            String order = switch (f.sort()) {
                case DEBT -> " ORDER BY (" + balanceExpr + ") DESC, " + nameExpr + ", " + codeExpr;
                case SALES -> " ORDER BY sx.sales_total DESC, " + nameExpr + ", " + codeExpr;
                case RECENT -> " ORDER BY sx.last_sale DESC, " + nameExpr + ", " + codeExpr;
                default -> " ORDER BY " + nameExpr + ", " + codeExpr;
            };
            String sql = "SELECT " + codeExpr + ", " + nameExpr + ", " + phoneExpr + ", (" + balanceExpr + "), (" + creditExpr + "), "
                    + groupExpr + ", " + routeExpr + ", " + visitorExpr + ", (" + activeExpr + "), (" + blockedExpr + "), "
                    + "ISNULL(sx.sales_total,0), ISNULL(sx.sales_count,0), ISNULL(sx.last_sale,N'') "
                    + "FROM dbo.CUSTOMERS c " + balanceApply + salesApply + whereSql + order + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<CustomerDirectoryRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(40);
                int parameter = bindParameters(ps, params) + 1;
                ps.setInt(parameter++, f.page() * f.pageSize());
                ps.setInt(parameter, f.pageSize());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long customerCode = rs.getLong(1);
                        if (rs.wasNull() || customerCode <= 0) continue;
                        String groupValue = rs.getString(6);
                        String routeValue = rs.getString(7);
                        String visitorValue = rs.getString(8);
                        BigDecimal customerBalance = rs.getBigDecimal(4);
                        Customer customer = new Customer(customerCode, rs.getString(2), rs.getString(3), customerBalance);
                        rows.add(new CustomerDirectoryRow(customer, rs.getBigDecimal(5), codeLabel("گروه", groupValue),
                                codeLabel("مسیر", routeValue), codeLabel("ویزیتور", visitorValue),
                                rs.getInt(9) != 0, rs.getInt(10) != 0,
                                rs.getBigDecimal(11), rs.getLong(12), toJalaliDate(rs.getString(13))));
                    }
                }
            }
            return new CustomerPage(rows, total, f.page(), f.pageSize());
        }
    }

    private static String stripCodePrefix(String value, String prefix) {
        String text = value == null ? "" : value.trim();
        return text.startsWith(prefix) ? text.substring(prefix.length()).trim() : text;
    }

    private static void addTextFilter(List<String> where, List<Object> params, String expression, String value) {
        String term = normalize(value);
        if (term.isEmpty()) return;
        where.add("TRY_CONVERT(nvarchar(150)," + expression + ") LIKE ? ESCAPE '\'");
        params.add("%" + escapeLike(term) + "%");
    }

    private static int bindParameters(PreparedStatement statement, List<?> parameters) throws SQLException {
        int index = 1;
        for (Object value : parameters) statement.setObject(index++, value);
        return index - 1;
    }

    private static String codeLabel(String prefix, String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || "0".equals(text)) return "بدون " + prefix;
        return prefix + " " + text;
    }

    private static String sqlColumn(String alias, String column) {
        return alias + "." + quoteIdentifier(column);
    }

    private static String quoteIdentifier(String identifier) {
        return "[" + identifier.replace("]", "]]" ) + "]";
    }

    private static String numericColumn(String alias, String column) {
        return "ISNULL(TRY_CONVERT(decimal(19,2)," + sqlColumn(alias, column) + "),0)";
    }

    private static String numericColumn3(String alias, String column) {
        return "ISNULL(TRY_CONVERT(decimal(19,3)," + sqlColumn(alias, column) + "),0)";
    }

    private static String textColumnOrEmpty(String alias, String column, int length) {
        return column == null ? "CAST(N'' AS nvarchar(" + length + "))"
                : "ISNULL(TRY_CONVERT(nvarchar(" + length + ")," + sqlColumn(alias, column) + "),N'')";
    }

    private static List<String> readTableColumns(Connection c, String table) throws SQLException {
        String sql = "SELECT col.name FROM sys.columns col JOIN sys.objects obj ON obj.object_id=col.object_id "
                + "JOIN sys.schemas sch ON sch.schema_id=obj.schema_id WHERE sch.name=N'dbo' "
                + "AND obj.type IN (N'U',N'V') AND obj.name=? ORDER BY col.column_id";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getString(1)); }
        }
        return result;
    }

    /** Read-only account statement backed by dbo.cust_act and Atiran's act-name dictionary. */
    @Override public CustomerLedgerReport customerLedger(CustomerDirectoryRow customer, LedgerFilter filter) throws SQLException {
        if (customer == null) throw new IllegalArgumentException("مشتری را انتخاب کنید.");
        LedgerFilter f = filter == null ? new LedgerFilter("", "", LedgerCategory.ALL, 0, 200) : filter;
        try (Connection c = open()) {
            List<String> actColumns = readTableColumns(c, "cust_act");
            String customerCode = resolveFlexibleColumn(actColumns, "shmo", "SHMO", "CustomerCode");
            String date = resolveFlexibleColumn(actColumns, "date", "t_date", "tarikh");
            String debit = resolveFlexibleColumn(actColumns, "act_bed", "bed", "debit");
            String credit = resolveFlexibleColumn(actColumns, "act_bes", "bes", "credit");
            if (customerCode == null || date == null || debit == null || credit == null)
                throw new SQLException("ساختار لازم برای خواندن گردش مشتری از dbo.cust_act کامل نیست.");
            String rowId = resolveFlexibleColumn(actColumns, "rdf_", "rdf", "id", "row_id");
            String description = resolveFlexibleColumn(actColumns, "act_dis", "description", "discription", "Explain");
            String actId = resolveFlexibleColumn(actColumns, "act_id", "actid");
            String document = resolveFlexibleColumn(actColumns, "ghno", "doc_no", "document_no", "shfacfo");
            String active = resolveFlexibleColumn(actColumns, "isActive", "active", "is_active");

            String activeWhere = active == null ? "" : " AND (UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10)," + sqlColumn("a", active) + "),N't')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') OR " + sqlColumn("a", active) + " IS NULL)";
            String balanceSql = "SELECT COUNT_BIG(1), ISNULL(SUM(" + numericColumn("a", debit) + "),0)-ISNULL(SUM(" + numericColumn("a", credit) + "),0) "
                    + "FROM dbo.cust_act a WHERE TRY_CONVERT(bigint," + sqlColumn("a", customerCode) + ")=?" + activeWhere
                    + " AND (" + numericColumn("a", debit) + "<>0 OR " + numericColumn("a", credit) + "<>0)";
            long totalRows;
            BigDecimal currentBalance;
            try (PreparedStatement ps = c.prepareStatement(balanceSql)) {
                ps.setQueryTimeout(30);
                ps.setLong(1, customer.customer().code());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("گردش مشتری از آتیران خوانده نشد.");
                    totalRows = rs.getLong(1);
                    currentBalance = rs.getBigDecimal(2);
                    if (currentBalance == null) currentBalance = BigDecimal.ZERO;
                }
            }

            List<String> namesColumns = readTableColumns(c, "ActNames");
            String namesKey = resolveFlexibleColumn(namesColumns, "ActID", "act_id", "actid");
            String namesText = resolveFlexibleColumn(namesColumns, "ActName", "act_name", "name");
            String nameJoin = namesKey != null && namesText != null && actId != null
                    ? " LEFT JOIN dbo.ActNames n ON TRY_CONVERT(int," + sqlColumn("n", namesKey) + ")=TRY_CONVERT(int," + sqlColumn("a", actId) + ") "
                    : " ";
            String idExpr = rowId == null ? "CAST(N'' AS nvarchar(80))" : "ISNULL(TRY_CONVERT(nvarchar(80)," + sqlColumn("a", rowId) + "),N'')";
            String dateExpr = "ISNULL(TRY_CONVERT(nvarchar(30)," + sqlColumn("a", date) + "),N'')";
            String descriptionExpr = description == null ? "CAST(N'' AS nvarchar(1000))" : "ISNULL(TRY_CONVERT(nvarchar(1000)," + sqlColumn("a", description) + "),N'')";
            String actExpr = actId == null ? "CAST(0 AS int)" : "ISNULL(TRY_CONVERT(int," + sqlColumn("a", actId) + "),0)";
            String documentExpr = document == null ? "CAST(N'' AS nvarchar(80))" : "ISNULL(TRY_CONVERT(nvarchar(80)," + sqlColumn("a", document) + "),N'')";
            String actNameExpr = namesKey != null && namesText != null && actId != null
                    ? "ISNULL(TRY_CONVERT(nvarchar(200)," + sqlColumn("n", namesText) + "),N'')"
                    : "CAST(N'' AS nvarchar(200))";
            String topRowsSql = "SELECT TOP (10000) " + idExpr + ", " + dateExpr + ", " + numericColumn("a", debit) + ", "
                    + numericColumn("a", credit) + ", " + descriptionExpr + ", " + actExpr + ", " + documentExpr + ", " + actNameExpr
                    + " FROM dbo.cust_act a " + nameJoin + " WHERE TRY_CONVERT(bigint," + sqlColumn("a", customerCode) + ")=?" + activeWhere
                    + " AND (" + numericColumn("a", debit) + "<>0 OR " + numericColumn("a", credit) + "<>0)"
                    + (rowId == null ? " ORDER BY " + dateExpr + " DESC" : " ORDER BY TRY_CONVERT(bigint," + sqlColumn("a", rowId) + ") DESC");
            List<LedgerEntry> all = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(topRowsSql)) {
                ps.setQueryTimeout(45);
                ps.setLong(1, customer.customer().code());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String atiranName = rs.getString(8) == null ? "" : rs.getString(8).trim();
                        String note = rs.getString(5) == null ? "" : rs.getString(5).trim().replaceAll("\\s+", " ");
                        int code = rs.getInt(6);
                        LedgerCategory category = classifyCustomerAct(code, atiranName, note);
                        String operation = customerActTitle(code, atiranName, category);
                        String dateText = toJalaliDate(rs.getString(2));
                        BigDecimal rowDebit = rs.getBigDecimal(3);
                        BigDecimal rowCredit = rs.getBigDecimal(4);
                        String documentNo = rs.getString(7) == null ? "" : rs.getString(7).trim();
                        all.add(new LedgerEntry(rs.getString(1), dateText, "", Integer.toString(code), operation,
                                note, documentNo, rowDebit, rowCredit, BigDecimal.ZERO, category));
                    }
                }
            }
            Comparator<LedgerEntry> chronological = Comparator.comparing(LedgerEntry::date)
                    .thenComparingLong(e -> numericId(e.rowId())).thenComparing(LedgerEntry::rowId);
            all.sort(chronological);

            // Walk backward from Atiran's exact live balance, matching its Σact_bed − Σact_bes rule.
            BigDecimal reverseBalance = currentBalance;
            List<LedgerEntry> balanced = new ArrayList<>(all.size());
            for (int i = all.size() - 1; i >= 0; i--) {
                LedgerEntry item = all.get(i);
                balanced.add(new LedgerEntry(item.rowId(), item.date(), item.time(), item.operationCode(),
                        item.operation(), item.description(), item.documentNo(), item.debit(), item.credit(),
                        reverseBalance, item.category()));
                reverseBalance = reverseBalance.subtract(item.debit()).add(item.credit());
            }
            balanced.sort(chronological);
            BigDecimal opening = reverseBalance;
            BigDecimal closing = opening;
            BigDecimal debitTotal = BigDecimal.ZERO;
            BigDecimal creditTotal = BigDecimal.ZERO;
            List<LedgerEntry> selected = new ArrayList<>();
            String from = f.fromJalali();
            String to = f.toJalali();
            for (LedgerEntry item : balanced) {
                boolean validDate = item.date().matches("[0-9]{4}/[0-9]{2}/[0-9]{2}");
                if (!from.isEmpty() && validDate && item.date().compareTo(from) < 0) {
                    opening = item.balance();
                    closing = item.balance();
                    continue;
                }
                if (!to.isEmpty() && validDate && item.date().compareTo(to) > 0) continue;
                if ((!from.isEmpty() || !to.isEmpty()) && !validDate) continue;
                closing = item.balance();
                if (f.category() == LedgerCategory.ALL || item.category() == f.category()) {
                    selected.add(item);
                    debitTotal = debitTotal.add(item.debit());
                    creditTotal = creditTotal.add(item.credit());
                }
            }
            if (to.isEmpty()) closing = currentBalance;
            else if (to.compareTo(all.isEmpty() ? "0000/00/00" : all.get(all.size() - 1).date()) >= 0) closing = currentBalance;
            selected.sort(chronological.reversed());
            int start = Math.min(selected.size(), f.page() * f.pageSize());
            int end = Math.min(selected.size(), start + f.pageSize());
            return new CustomerLedgerReport(customer, selected.subList(start, end), selected.size(), opening,
                    debitTotal, creditTotal, closing, from, to, f.category(), totalRows > all.size());
        }
    }

    /** Ledger-row details always work; invoice lines are linked when Atiran exposes its normal sales-detail table. */
    @Override public TransactionDetails customerTransactionDetails(CustomerDirectoryRow customer, LedgerEntry entry) throws SQLException {
        if (customer == null || entry == null) throw new IllegalArgumentException("مشتری و ردیف گردش را انتخاب کنید.");
        List<DetailField> fields = new ArrayList<>();
        fields.add(new DetailField("مشتری", customer.customer().name()));
        fields.add(new DetailField("کد مشتری", Long.toString(customer.customer().code())));
        fields.add(new DetailField("تاریخ", entry.date()));
        fields.add(new DetailField("شناسه عملیات", entry.operationCode()));
        fields.add(new DetailField("نوع عملیات", entry.category().label()));
        fields.add(new DetailField("شرح عملیات", entry.operation()));
        fields.add(new DetailField("شماره سند آتیران", entry.documentNo()));
        fields.add(new DetailField("بدهکار", entry.debit().toPlainString()));
        fields.add(new DetailField("بستانکار", entry.credit().toPlainString()));
        fields.add(new DetailField("مانده پس از گردش", entry.balance().toPlainString()));
        fields.add(new DetailField("شرح ثبت‌شده", entry.description()));
        fields.add(new DetailField("منبع", "dbo.cust_act · فقط‌خواندنی"));
        List<CustomerModels.DetailLine> lines = List.of();
        if ((entry.category() == LedgerCategory.SALES || entry.category() == LedgerCategory.PURCHASES)
                && !entry.documentNo().isBlank()) {
            try (Connection c = open()) {
                lines = readDocumentLines(c, entry.documentNo(), entry.category() == LedgerCategory.SALES);
            } catch (SQLException optionalDetailUnavailable) {
                // Missing permissions or version-specific detail tables must not hide the cust_act row.
                lines = List.of();
            }
        }
        if ((entry.category() == LedgerCategory.SALES || entry.category() == LedgerCategory.PURCHASES) && lines.isEmpty())
            fields.add(new DetailField("اقلام سند", "ریز اقلام به این ردیف گردش پیوند نخورد؛ اطلاعات کامل موجود در cust_act در بالا نمایش داده شده است."));
        return new TransactionDetails(entry.operation().isBlank() ? entry.category().label() : entry.operation(), fields, lines);
    }

    private List<CustomerModels.DetailLine> readDocumentLines(Connection c, String documentNo, boolean sales) throws SQLException {
        long documentId;
        try { documentId = Long.parseLong(normalize(documentNo)); }
        catch (NumberFormatException ignored) { return List.of(); }
        String table = sales ? "subsailfact" : "subbuyfact";
        List<String> detailColumns = readTableColumns(c, table);
        String headerNo = sales
                ? resolveFlexibleColumn(detailColumns, "shfacfo", "factor_no", "invoice_no")
                : resolveFlexibleColumn(detailColumns, "shfackh", "buy_no", "factor_no", "invoice_no");
        String itemName = sales
                ? resolveFlexibleColumn(detailColumns, "naka", "name", "Desc_Naka", "item_name")
                : resolveFlexibleColumn(detailColumns, "Desc_Naka", "naka", "name", "item_name");
        String productCode = resolveFlexibleColumn(detailColumns, "SHKA", "shka", "product_code");
        String quantityPack = resolveFlexibleColumn(detailColumns, "TEDVAH", "tedvah", "quantity", "qty");
        String quantityLoose = resolveFlexibleColumn(detailColumns, "TEDJOZ", "tedjoz");
        String lineTotal = sales
                ? resolveFlexibleColumn(detailColumns, "LINESUM", "line_total", "amount")
                : resolveFlexibleColumn(detailColumns, "tamam_joz", "LINESUM", "line_total", "amount");
        String unitPrice = resolveFlexibleColumn(detailColumns, "FI", "fi", "fee", "price", "unit_price");
        String active = resolveFlexibleColumn(detailColumns, "active", "isActive");
        if (headerNo == null || (itemName == null && productCode == null)) return List.of();
        List<String> inventoryColumns = readTableColumns(c, "inventory");
        String inventoryCode = resolveFlexibleColumn(inventoryColumns, "shka", "product_code");
        String inventoryName = resolveFlexibleColumn(inventoryColumns, "naka", "name", "item_name");
        boolean canJoinInventory = itemName == null && productCode != null && inventoryCode != null && inventoryName != null;
        String join = canJoinInventory
                ? " LEFT JOIN dbo.inventory i ON TRY_CONVERT(nvarchar(100)," + sqlColumn("i", inventoryCode) + ")=TRY_CONVERT(nvarchar(100)," + sqlColumn("d", productCode) + ") " : " ";
        String nameExpr = itemName != null ? "ISNULL(TRY_CONVERT(nvarchar(500)," + sqlColumn("d", itemName) + "),N'')"
                : canJoinInventory ? "ISNULL(TRY_CONVERT(nvarchar(500)," + sqlColumn("i", inventoryName) + "),N'')"
                : productCode != null ? "CONCAT(N'کالا ',TRY_CONVERT(nvarchar(100)," + sqlColumn("d", productCode) + "))"
                : "CAST(N'' AS nvarchar(500))";
        String packsExpr = quantityPack == null ? "CAST(0 AS decimal(19,3))" : numericColumn3("d", quantityPack);
        String looseExpr = quantityLoose == null ? "CAST(0 AS decimal(19,3))" : numericColumn3("d", quantityLoose);
        String priceExpr = unitPrice == null ? "CAST(0 AS decimal(19,2))" : numericColumn("d", unitPrice);
        String totalExpr = lineTotal == null ? "CAST(0 AS decimal(19,2))" : numericColumn("d", lineTotal);
        String activeWhere = active == null ? "" : " AND (UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(10)," + sqlColumn("d", active) + "),N't')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES') OR " + sqlColumn("d", active) + " IS NULL)";
        String sql = "SELECT TOP (250) " + nameExpr + ", " + packsExpr + ", " + looseExpr + ", " + priceExpr + ", " + totalExpr
                + " FROM dbo." + quoteIdentifier(table) + " d " + join + " WHERE TRY_CONVERT(bigint," + sqlColumn("d", headerNo) + ")=?" + activeWhere;
        List<CustomerModels.DetailLine> lines = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(25);
            ps.setLong(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal packs = rs.getBigDecimal(2) == null ? BigDecimal.ZERO : rs.getBigDecimal(2);
                    BigDecimal loose = rs.getBigDecimal(3) == null ? BigDecimal.ZERO : rs.getBigDecimal(3);
                    BigDecimal price = rs.getBigDecimal(4) == null ? BigDecimal.ZERO : rs.getBigDecimal(4);
                    BigDecimal amount = rs.getBigDecimal(5) == null ? BigDecimal.ZERO : rs.getBigDecimal(5);
                    lines.add(new CustomerModels.DetailLine(rs.getString(1), packs.toPlainString() + " بسته / " + loose.toPlainString() + " جزء",
                            price.toPlainString(), amount));
                }
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Read-only Manager section report (cash, cheques, stock, sales reports, profit, production,
     * personnel, users). Access follows the caller's Atiran role; a missing Atiran structure becomes a
     * Persian explanation instead of an empty table, and no statement here can write to the database.
     */
    @Override public SectionReport loadSection(SectionRequest request) throws SQLException {
        if (request == null) throw new IllegalArgumentException("گزارش مورد نظر را انتخاب کنید.");
        String sectionId = request.sectionId().toLowerCase(Locale.ROOT);
        if (!SectionCatalog.isDesktopSection(sectionId))
            throw new IllegalArgumentException("بخش «" + WindowsRoleMenus.labelOf(sectionId)
                    + "» در نسخهٔ دسکتاپ پیاده‌سازی نشده است.");
        if (!WindowsRoleMenus.allows(request.role(), sectionId))
            throw new IllegalArgumentException("این گزارش برای نقش «"
                    + (request.role().isEmpty() ? "نامشخص" : request.role()) + "» فعال نیست.");
        VariantSpec spec = SectionCatalog.spec(sectionId, request.variantId());
        String today = PersianDate.today();
        try (Connection c = open()) {
            try {
                SectionSchemaReader schema = new SectionSchemaReader(c);
                SectionSql.PreparedQuery query = SectionSql.build(spec, request.filter(), schema, today);
                List<SectionRow> rows;
                try (PreparedStatement ps = c.prepareStatement(query.sql())) {
                    ps.setQueryTimeout(SECTION_QUERY_TIMEOUT_SECONDS);
                    int index = 1;
                    for (Object value : query.params()) ps.setObject(index++, value);
                    try (ResultSet rs = ps.executeQuery()) {
                        rows = SectionJdbc.read(rs, spec, spec.maxRows());
                    }
                }
                boolean truncated = rows.size() >= spec.maxRows();
                return SectionPost.process(spec, request.filter(), rows, truncated, false, today);
            } catch (SectionModels.SectionUnavailable structure) {
                return SectionPost.unavailable(spec, request.filter(), structure.getMessage(), false);
            }
        }
    }

    @Override public boolean supportsSections() { return true; }

    private static LedgerCategory classifyCustomerAct(int actId, String name, String description) {
        LedgerCategory named = LedgerCategory.classify(name, description);
        if (named != LedgerCategory.OTHER) return named;
        if (actId == 1 || actId == 3) return LedgerCategory.RECEIPTS;
        if (actId == 2 || actId == 4 || actId == 9 || actId == 44) return LedgerCategory.PAYMENTS;
        if (actId == 20 || actId == 17 || actId == 27 || actId == 11 || actId == 26 || actId == 114 || actId == 118)
            return LedgerCategory.SALES;
        if (actId == 31 || actId == 32) return LedgerCategory.PURCHASES;
        return LedgerCategory.OTHER;
    }

    private static String customerActTitle(int actId, String name, LedgerCategory category) {
        String text = name == null ? "" : name.trim().replace('ي', 'ی').replace('ك', 'ک');
        if (!text.isEmpty()) return text;
        return switch (actId) {
            case 0 -> "مانده حساب قبلی";
            case 1 -> "قبض دریافت";
            case 2 -> "قبض پرداخت";
            case 20 -> "فاکتور فروش";
            default -> category.label();
        };
    }

    private static long numericId(String value) {
        try { return Long.parseLong(value == null ? "" : value.trim()); }
        catch (NumberFormatException ignored) { return Long.MIN_VALUE; }
    }

    private static String toJalaliDate(String raw) {
        String value = normalize(raw).trim().replace('-', '/').replace('.', '/');
        if (value.isEmpty()) return "";
        String token = value.split("\\s+", 2)[0];
        String[] parts = token.split("/");
        if (parts.length == 3) {
            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                if (year >= 1900 && year <= 2100)
                    return PersianDate.fromGregorian(LocalDate.of(year, month, day));
                return String.format(Locale.ROOT, "%04d/%02d/%02d", year, month, day);
            } catch (RuntimeException ignored) { }
        }
        return value.length() > 10 ? value.substring(0, 10) : value;
    }

    private List<String> productBarcodeColumns(Connection c) throws SQLException {
        String sql = "SELECT c.name FROM sys.columns c "
                + "JOIN sys.objects o ON o.object_id=c.object_id "
                + "JOIN sys.schemas s ON s.schema_id=o.schema_id "
                + "WHERE s.name=N'dbo' AND o.type IN (N'U',N'V') AND o.name=? ORDER BY c.column_id";
        List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, "inventory");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) columns.add(rs.getString(1));
            }
        }
        return resolveProductBarcodeColumns(columns);
    }

    static List<String> resolveProductBarcodeColumns(List<String> columns) {
        String primary = resolveFlexibleColumn(columns,
                "StuffCode", "Code", "Barcode", "BarCode", "KalaCode", "ProductCode", "ItemCode", "کد_کالا", "بارکد");
        String legacy = resolveFlexibleColumn(columns, "coka");
        List<String> resolved = new ArrayList<>(2);
        if (primary != null) resolved.add(primary);
        if (legacy != null && resolved.stream().noneMatch(existing -> existing.equalsIgnoreCase(legacy))) resolved.add(legacy);
        return resolved;
    }

    private static String productBarcodeExpression(List<String> columns) {
        if (columns.isEmpty()) return "CAST(N'' AS nvarchar(100))";
        String primary = "TRY_CONVERT(nvarchar(100)," + quotedProductColumn(columns.get(0)) + ")";
        if (columns.size() == 1) return "ISNULL(" + primary + ",N'')";
        String fallback = "TRY_CONVERT(nvarchar(100)," + quotedProductColumn(columns.get(1)) + ")";
        return "ISNULL(NULLIF(" + primary + ",N''),ISNULL(" + fallback + ",N''))";
    }

    private static String quotedProductColumn(String column) {
        return "i.[" + column.replace("]", "]]") + "]";
    }

    private static String resolveFlexibleColumn(List<String> columns, String... candidates) {
        if (columns == null || candidates == null) return null;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            for (String column : columns) {
                if (column != null && column.equalsIgnoreCase(candidate)) return column;
            }
        }
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeColumnName(candidate);
            if (normalizedCandidate.isEmpty()) continue;
            for (String column : columns) {
                if (normalizeColumnName(column).equals(normalizedCandidate)) return column;
            }
        }
        return null;
    }

    private static String normalizeColumnName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('ي', 'ی').replace('ك', 'ک')
                .replace("_", "").replace("-", "").replace(" ", "")
                .replace(".", "").replace("/", "").trim();
    }

    private int readPriceGroup(Connection c) throws SQLException {
        String sql = "SELECT CASE WHEN COUNT_BIG(1)>0 "
                + "AND COUNT_BIG(1)=COUNT_BIG(TRY_CONVERT(int,[DefaultPriceGrp])) "
                + "AND MIN(TRY_CONVERT(int,[DefaultPriceGrp]))=MAX(TRY_CONVERT(int,[DefaultPriceGrp])) "
                + "THEN MIN(TRY_CONVERT(int,[DefaultPriceGrp])) ELSE 0 END "
                + "FROM [Hamrah].[DeviceSettings] WHERE TRY_CONVERT(int,[VisitorId])=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setInt(1, visitorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("گروه قیمت پیش‌فرض ویزیتور از آتیران خوانده نشد.");
                int group = rs.getInt(1);
                if (group < 1 || group > 5) throw new SQLException("DefaultPriceGrp یکتا و معتبر (۱ تا ۵) نیست؛ قیمت کالا نمایش داده نشد.");
                return group;
            }
        }
    }

    @Override public InvoiceResult submit(InvoiceDraft draft) throws Exception {
        if (!writesAllowed) throw new IllegalStateException("ثبت واقعی خاموش است؛ فقط پس از آزمون روی نسخهٔ بی‌خطر، ATIRAN_ALLOW_INVOICE_WRITES را فعال کن.");
        validateDraft(draft);
        InvoiceMath.Totals totals = InvoiceMath.calculate(draft.lines(), draft.globalDiscount(), draft.freight());
        if (draft.globalDiscount().compareTo(totals.linesGross().subtract(totals.lineDiscounts())) > 0) {
            throw new IllegalArgumentException("تخفیف کل از ماندهٔ جمع ردیف‌ها بیشتر است.");
        }
        String uniqueId = draft.uniqueId().isBlank() ? "MEELANO-WIN-" + UUID.randomUUID().toString().replace("-", "") : draft.uniqueId();
        if (uniqueId.length() > 50) uniqueId = uniqueId.substring(0, 50);
        String sql = loadInvoiceSql();
        try (Connection c = open()) {
            // Replays must return the original invoice even if the customer or item changed after the first send.
            InvoiceResult existing = findExistingInvoice(c, uniqueId);
            if (existing != null) return existing;
            validateInvoiceContract(c);
            validateLiveDraft(c, draft);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setQueryTimeout(120);
                int i = 1;
                ps.setLong(i++, draft.customer().code());
                ps.setInt(i++, visitorId);
                ps.setInt(i++, systemUserId);
                ps.setString(i++, atiranUsername);
                ps.setString(i++, draft.jalaliDate());
                ps.setString(i++, trimTo(draft.notes(), 450));
                ps.setString(i++, uniqueId);
                ps.setString(i++, invoiceLinesXml(draft.lines()));
                ps.setBigDecimal(i++, InvoiceMath.money(draft.globalDiscount()));
                ps.setInt(i++, Math.max(0, Math.min(720, draft.settlementDays())));
                ps.setBigDecimal(i++, InvoiceMath.money(draft.freight()));
                ps.setInt(i, Math.max(0, draft.warehouseId() > 0 ? draft.warehouseId() : warehouseId));

                boolean resultSet = ps.execute();
                for (int guard = 0; guard < 256; guard++) {
                    if (resultSet) {
                        try (ResultSet rs = ps.getResultSet()) {
                            if (rs != null && isInvoiceResult(rs.getMetaData()) && rs.next()) {
                                return new InvoiceResult(rs.getLong(1), rs.getInt(2) == 1,
                                        rs.getInt(3) == 1, rs.getBigDecimal(6), rs.getBigDecimal(4), rs.getString(5));
                            }
                        }
                    } else if (ps.getUpdateCount() == -1) {
                        break;
                    }
                    resultSet = ps.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
            }
        }
        throw new SQLException("آتیران شمارهٔ فاکتور را برنگرداند؛ نتیجه را در فهرست فاکتورها بررسی کن و قبل از ارسال دوباره مطمئن شو.");
    }

    private InvoiceResult findExistingInvoice(Connection c, String uniqueId) throws SQLException {
        String sql = "SELECT TOP (1) f.shfacfo, ISNULL(f.[Status],0), ISNULL(c.man,0), ISNULL(f.[all],0) "
                + "FROM dbo.sailfact f LEFT JOIN dbo.CUSTOMERS c ON c.SHMO=f.shmo "
                + "WHERE f.UniqueID=? AND f.active='t' ORDER BY f.shfacfo DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(20);
            ps.setString(1, uniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new InvoiceResult(rs.getLong(1), true, rs.getInt(2) == 1,
                        rs.getBigDecimal(4), rs.getBigDecimal(3), "");
            }
        }
    }

    /** Fail closed before the transaction if Atiran's required procedures, trigger, or insert contract are absent. */
    private void validateInvoiceContract(Connection c) throws SQLException {
        String preflight = "SELECT CASE WHEN OBJECT_ID(N'dbo.AddInvoice',N'P') IS NOT NULL "
                + "AND OBJECT_ID(N'dbo.UpdateMojodiInventory',N'P') IS NOT NULL "
                + "AND OBJECT_ID(N'dbo.UpdateMojodiInventoryAnbars',N'P') IS NOT NULL "
                + "AND OBJECT_ID(N'dbo.subsailtemp',N'U') IS NOT NULL "
                + "AND OBJECT_ID(N'dbo.FactorConfirmation',N'U') IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM sys.triggers t WHERE t.parent_id=OBJECT_ID(N'dbo.subsailtemp') "
                + "AND t.name=N'InvoiceTrigger' AND t.is_disabled=0) "
                + "THEN 1 ELSE 0 END";
        try (PreparedStatement ps = c.prepareStatement(preflight)) {
            ps.setQueryTimeout(20);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1)
                    throw new SQLException("پروسیجرهای آتیران یا trigger فعال InvoiceTrigger روی dbo.subsailtemp پیدا نشد.");
            }
        }

        Map<String, Set<String>> columnsByTable = new HashMap<>();
        String columnsSql = "SELECT LOWER(o.name),LOWER(c.name) FROM sys.columns c "
                + "JOIN sys.objects o ON o.object_id=c.object_id "
                + "JOIN sys.schemas s ON s.schema_id=o.schema_id "
                + "WHERE s.name=N'dbo' AND o.name IN (N'subsailtemp',N'FactorConfirmation')";
        try (PreparedStatement ps = c.prepareStatement(columnsSql)) {
            ps.setQueryTimeout(20);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) columnsByTable.computeIfAbsent(rs.getString(1), ignored -> new HashSet<>()).add(rs.getString(2));
            }
        }
        Set<String> tempRequired = Set.of("rdf__", "shfacfo", "shka", "rdf_anbar", "tedvah", "tedjoz", "vahprice",
                "jozprice", "bastebandi", "tedbastebandi", "linesum", "pertafif", "rdf", "pervis", "litakhma",
                "active", "naka", "ptax", "tax", "avarez", "pavarez", "gift", "date", "done_date", "userid",
                "vis_rdf", "sysid", "tafifaghlam", "tafifline", "productionseriesid", "tedvahmain",
                "tedjozmain", "perpromotion", "promotionvalue", "multipishfactor", "tafifpos", "tafifnaghd", "varietyid");
        Set<String> confirmationRequired = Set.of("date", "username", "shfacfo", "sysid", "userid");
        if (!columnsByTable.getOrDefault("subsailtemp", Set.of()).containsAll(tempRequired)
                || !columnsByTable.getOrDefault("factorconfirmation", Set.of()).containsAll(confirmationRequired))
            throw new SQLException("ستون‌های جدول موقت یا قطعی‌سازی با قرارداد فاکتور آتیران سازگار نیست.");
    }

    /** Live checks immediately before the canonical invoice batch; no row is written by these checks. */
    private void validateLiveDraft(Connection c, InvoiceDraft draft) throws SQLException {
        String customerSql = "SELECT TRY_CONVERT(nvarchar(5),active), ISNULL(TRY_CONVERT(int,black_list),0) "
                + "FROM dbo.CUSTOMERS WHERE SHMO=?";
        try (PreparedStatement ps = c.prepareStatement(customerSql)) {
            ps.setQueryTimeout(20);
            ps.setLong(1, draft.customer().code());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("مشتری در آتیران پیدا نشد؛ ثبت متوقف شد.");
                if (!isActive(rs.getString(1)) || rs.getInt(2) != 0)
                    throw new IllegalArgumentException("مشتری غیرفعال یا مسدود است؛ ثبت متوقف شد.");
            }
        }

        int requestedWarehouse = draft.warehouseId() > 0 ? draft.warehouseId() : warehouseId;
        int targetWarehouse = resolveWarehouse(c, requestedWarehouse);
        int priceGroup = readPriceGroup(c);
        String priceColumn = "forosh" + priceGroup;
        String productSql = "SELECT ISNULL(NULLIF(TRY_CONVERT(bigint,i.mohvah),0),1), "
                + "TRY_CONVERT(nvarchar(5),i.active), ISNULL(TRY_CONVERT(int,i.black_list),0), "
                + "px.price_rows, ISNULL(px.unit_price,0) FROM dbo.inventory i "
                + "OUTER APPLY (SELECT COUNT_BIG(1) price_rows, MAX(TRY_CONVERT(decimal(19,2),p.[" + priceColumn + "])) unit_price "
                + "FROM dbo.forosh_price p WHERE p.shka=i.shka "
                + "AND UPPER(LTRIM(RTRIM(ISNULL(TRY_CONVERT(nvarchar(5),p.active),N'f')))) IN (N'T',N'1',N'TRUE',N'Y',N'YES')) px WHERE i.shka=?";
        Map<Long, BigDecimal> requestedByProduct = new HashMap<>();
        Map<Long, Product> selectedProducts = new HashMap<>();
        for (InvoiceLine line : draft.lines()) {
            requestedByProduct.merge(line.product().code(), line.quantity(), BigDecimal::add);
            selectedProducts.put(line.product().code(), line.product());
        }
        try (PreparedStatement ps = c.prepareStatement(productSql)) {
            ps.setQueryTimeout(20);
            for (Map.Entry<Long, BigDecimal> requested : requestedByProduct.entrySet()) {
                Product selected = selectedProducts.get(requested.getKey());
                ps.setLong(1, requested.getKey());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("کالای «" + selected.name() + "» در آتیران پیدا نشد.");
                    long unitsPerPack = rs.getLong(1);
                    String active = rs.getString(2);
                    int blacklisted = rs.getInt(3);
                    long priceRows = rs.getLong(4);
                    BigDecimal currentPrice = rs.getBigDecimal(5);
                    if (!isActive(active) || blacklisted != 0)
                        throw new IllegalArgumentException("کالای «" + selected.name() + "» غیرفعال یا مسدود است.");
                    if (unitsPerPack < 1 || unitsPerPack > Integer.MAX_VALUE || unitsPerPack != selected.unitsPerPack())
                        throw new IllegalArgumentException("واحد بسته‌بندی «" + selected.name() + "» تغییر کرده؛ کالا را دوباره جست‌وجو کن.");
                    if (priceRows != 1 || currentPrice == null || currentPrice.signum() <= 0
                            || currentPrice.compareTo(selected.unitPrice()) != 0)
                        throw new IllegalArgumentException("نرخ یکتای «" + selected.name() + "» تغییر کرده یا مبهم است؛ کالا را دوباره جست‌وجو کن.");
                    BigDecimal stock = readWarehouseStock(c, requested.getKey(), targetWarehouse, unitsPerPack);
                    if (stock.compareTo(requested.getValue()) < 0)
                        throw new IllegalArgumentException("موجودی انبار انتخاب‌شدهٔ «" + selected.name() + "» برای این مقدار کافی نیست.");
                }
            }
        }
    }

    private int resolveWarehouse(Connection c, int requestedWarehouse) throws SQLException {
        String sql;
        if (requestedWarehouse > 0) {
            sql = "SELECT TOP (1) rdf_anbar FROM dbo.anbars "
                    + "WHERE rdf_anbar=? AND ISNULL(Active,1)=1";
        } else {
            sql = "SELECT TOP (1) rdf_anbar FROM dbo.anbars "
                    + "WHERE ISNULL(Active,1)=1 "
                    + "ORDER BY CASE WHEN ISNULL(Base,0)=1 THEN 0 ELSE 1 END, rdf_anbar";
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            if (requestedWarehouse > 0) ps.setInt(1, requestedWarehouse);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) <= 0)
                    throw new IllegalArgumentException(requestedWarehouse > 0
                            ? "انبار انتخاب‌شده در آتیران فعال نیست؛ ثبت متوقف شد."
                            : "انبار پیش‌فرض فعال آتیران پیدا نشد؛ ثبت متوقف شد.");
                return rs.getInt(1);
            }
        }
    }

    private BigDecimal readWarehouseStock(Connection c, long productCode, int warehouse, long unitsPerPack) throws SQLException {
        String sql = "SELECT ISNULL(SUM((ISNULL(TRY_CONVERT(decimal(19,3),k.tedvah),0)*? "
                + "+ISNULL(TRY_CONVERT(decimal(19,3),k.tedjoz),0))*"
                + "CASE WHEN TRY_CONVERT(int,k.act_id) IN (" + STOCK_OUT_ACTS + ") THEN -1 ELSE 1 END),0) "
                + "FROM dbo.ka_act k WHERE k.shka=? AND k.RdfAnbar=? AND k.active='t'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setQueryTimeout(20);
            ps.setLong(1, unitsPerPack);
            ps.setLong(2, productCode);
            ps.setInt(3, warehouse);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return BigDecimal.ZERO;
                BigDecimal stock = rs.getBigDecimal(1);
                return stock == null ? BigDecimal.ZERO : stock;
            }
        }
    }

    private static boolean isActive(String value) {
        if (value == null) return false;
        String normalized = value.trim();
        return normalized.equalsIgnoreCase("t") || normalized.equals("1")
                || normalized.equalsIgnoreCase("true") || normalized.equalsIgnoreCase("y")
                || normalized.equalsIgnoreCase("yes");
    }

    private static boolean isInvoiceResult(ResultSetMetaData md) throws SQLException {
        return md.getColumnCount() >= 6 && "shfacfo".equalsIgnoreCase(md.getColumnLabel(1));
    }

    private static void validateDraft(InvoiceDraft draft) {
        if (draft == null || draft.customer() == null) throw new IllegalArgumentException("مشتری را انتخاب کن.");
        if (draft.customer().code() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("کد مشتری خارج از محدودهٔ قرارداد فاکتور آتیران است.");
        if (draft.lines().isEmpty() || draft.lines().size() > 250) throw new IllegalArgumentException("تعداد ردیف‌های فاکتور معتبر نیست.");
        if (!validJalaliDate(draft.jalaliDate())) throw new IllegalArgumentException("تاریخ معتبر را به شکل 1405/08/16 وارد کن.");
        if (draft.settlementDays() < 0 || draft.settlementDays() > 720 || draft.warehouseId() < 0)
            throw new IllegalArgumentException("مدت تسویه یا کد انبار معتبر نیست.");
        for (InvoiceLine line : draft.lines()) {
            if (!line.product().hasUnambiguousPrice()) throw new IllegalArgumentException("نرخ یکتای آتیران برای «" + line.product().name() + "» موجود نیست.");
            if (line.quantity().signum() <= 0 || line.quantity().compareTo(new BigDecimal("100000")) > 0
                    || line.quantity().stripTrailingZeros().scale() > 3)
                throw new IllegalArgumentException("مقدار کالا باید مثبت و حداکثر سه رقم اعشار داشته باشد.");
            InvoiceMath.toAtiranQuantity(line.quantity(), line.product().unitsPerPack());
            if (line.product().unitPrice().signum() <= 0) throw new IllegalArgumentException("نرخ کالای انتخاب‌شده معتبر نیست.");
            if (!validPercent(line.discountPercent()) || !validPercent(line.taxPercent()))
                throw new IllegalArgumentException("درصد تخفیف و مالیات باید بین صفر تا صد و حداکثر دو رقم اعشار باشد.");
        }
        if (draft.globalDiscount().signum() < 0 || draft.freight().signum() < 0)
            throw new IllegalArgumentException("تخفیف و باربری نمی‌توانند منفی باشند.");
    }

    private static boolean validPercent(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.compareTo(HUNDRED) <= 0
                && value.stripTrailingZeros().scale() <= 2;
    }

    private static boolean validJalaliDate(String value) {
        return PersianDate.isValidJalaliDate(value);
    }

    private static String invoiceLinesXml(List<InvoiceLine> lines) {
        StringBuilder xml = new StringBuilder("<i>");
        for (InvoiceLine line : lines) {
            Product p = line.product();
            BigDecimal qty = line.quantity();
            int unitsPerPack = Math.max(1, p.unitsPerPack());
            InvoiceMath.PackedQuantity encodedQuantity = InvoiceMath.toAtiranQuantity(qty, unitsPerPack);
            BigDecimal packs = encodedQuantity.packs();
            int remainder = encodedQuantity.looseUnits();
            BigDecimal gross = InvoiceMath.gross(line);
            BigDecimal discount = InvoiceMath.lineDiscount(line);
            BigDecimal vat = InvoiceMath.lineTax(line);
            BigDecimal packPrice = p.unitPrice().multiply(BigDecimal.valueOf(unitsPerPack));
            xml.append("<l s=\"").append(p.code()).append("\" tv=\"").append(decimal(packs))
                    .append("\" tj=\"").append(remainder).append("\" vp=\"").append(decimal(packPrice))
                    .append("\" jp=\"").append(decimal(p.unitPrice())).append("\" pk=\"").append(xmlAttr(trimTo(p.packageName(), 25)))
                    .append("\" ls=\"").append(moneyText(gross)).append("\" pt=\"").append(decimal(line.discountPercent()))
                    .append("\" lt=\"").append(moneyText(discount)).append("\" ptx=\"").append(decimal(line.taxPercent()))
                    .append("\" tx=\"").append(moneyText(vat)).append("\"/>");
        }
        return xml.append("</i>").toString();
    }

    private static String loadInvoiceSql() throws IOException {
        try (InputStream in = AtiranSqlGateway.class.getResourceAsStream("/sql/atiran_sale_invoice.sql")) {
            if (in == null) throw new IOException("قرارداد ثبت فاکتور آتیران در بستهٔ ویندوز پیدا نشد.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Connection open() throws SQLException {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl, sqlUser, sqlPassword);
            connectionAvailable = true;
            return connection;
        } catch (SQLException ex) {
            connectionAvailable = false;
            throw ex;
        }
    }

    private static String decimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private static String moneyText(BigDecimal value) {
        return InvoiceMath.money(value).toPlainString();
    }

    private static String xmlAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String trimTo(String value, int max) {
        String s = value == null ? "" : value.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String escapeLike(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
    }

    private static void requireSearchTerm(String term) {
        if (term == null || term.codePointCount(0, term.length()) < 2)
            throw new IllegalArgumentException("برای جست‌وجوی امن، دست‌کم دو نویسه وارد کن.");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String s = value.trim().replace('ي','ی').replace('ك','ک');
        return s.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3')
                .replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7')
                .replace('۸','8').replace('۹','9').replace('٠','0').replace('١','1')
                .replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5')
                .replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
    }

    private static boolean jdbcPropertyEquals(String url, String expectedName, String expectedValue) {
        for (String property : url.split(";")) {
            int equals = property.indexOf('=');
            if (equals > 0 && property.substring(0, equals).trim().equalsIgnoreCase(expectedName)
                    && property.substring(equals + 1).trim().equalsIgnoreCase(expectedValue)) return true;
        }
        return false;
    }

    private static String env(String name) { return System.getenv(name) == null ? "" : System.getenv(name).trim(); }
    private static String requiredSecretEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty())
            throw new IllegalStateException("متغیر محلی " + name + " تنظیم نشده است؛ مقدار محرمانه را در مخزن یا گفتگو قرار نده.");
        return value;
    }
    private static String requiredEnv(String name) {
        String value = env(name);
        if (value.isEmpty()) throw new IllegalStateException("متغیر محلی " + name + " تنظیم نشده است؛ مقدار محرمانه را در مخزن یا گفتگو قرار نده.");
        return value;
    }
    private static int positiveIntEnv(String name) {
        int value = nonNegativeIntEnv(name, -1);
        if (value <= 0) throw new IllegalStateException("شناسهٔ محلی " + name + " باید عدد مثبت باشد.");
        return value;
    }
    private static int nonNegativeIntEnv(String name, int defaultValue) {
        String value = env(name);
        if (value.isEmpty()) return defaultValue;
        try { int parsed = Integer.parseInt(value); if (parsed < 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException ex) { throw new IllegalStateException("مقدار محلی " + name + " معتبر نیست."); }
    }
}
