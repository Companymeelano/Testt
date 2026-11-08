package ir.meelano.windowsinvoice.data;

import ir.meelano.windowsinvoice.AtiranGateway;
import ir.meelano.windowsinvoice.InvoiceMath;
import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.InvoiceModels.Customer;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceDraft;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceLine;
import ir.meelano.windowsinvoice.InvoiceModels.InvoiceResult;
import ir.meelano.windowsinvoice.InvoiceModels.Product;

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
import java.util.ArrayList;
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
        if (jdbcPropertyEquals(url, "encrypt", "false")
                || jdbcPropertyEquals(url, "trustServerCertificate", "true")) {
            throw new IllegalStateException("برای اتصال زنده، TLS را روشن نگه دار و گواهی سرور را اعتبارسنجی کن.");
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
                + "AND (TRY_CONVERT(nvarchar(100),SHMO) LIKE ? ESCAPE '\\' "
                + "OR TRY_CONVERT(nvarchar(250),MONAME) LIKE ? ESCAPE '\\' "
                + "OR TRY_CONVERT(nvarchar(100),cell) LIKE ? ESCAPE '\\') "
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
                        .append(quotedProductColumn(column)).append(") LIKE ? ESCAPE '\\'");
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
                    + "AND (TRY_CONVERT(nvarchar(100),i.shka) LIKE ? ESCAPE '\\' "
                    + "OR TRY_CONVERT(nvarchar(500),i.naka) LIKE ? ESCAPE '\\'" + barcodeSearch + ") "
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
