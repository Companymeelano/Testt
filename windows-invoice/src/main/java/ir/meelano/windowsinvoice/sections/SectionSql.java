package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionUnavailable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the SELECT statements behind every desktop Manager section.
 *
 * Rules this class never breaks:
 * <ul>
 *   <li>SELECT only — no INSERT, UPDATE, DELETE, EXEC or DDL is ever produced;</li>
 *   <li>every user-supplied value is bound as a JDBC parameter, never concatenated;</li>
 *   <li>table and column names are resolved from live sys.columns metadata, so a renamed Atiran
 *       column degrades to a clear Persian message instead of a crash or a guessed name;</li>
 *   <li>conversions are TRY_CONVERT-based, so one dirty row cannot fail the whole report;</li>
 *   <li>every query is capped with TOP, and the cap is reported to the operator as truncation.</li>
 * </ul>
 */
public final class SectionSql {
    private SectionSql() { }

    /** Atiran's stock-out act ids; the same list the Android stock ledger and the invoice desk use. */
    static final String STOCK_OUT_ACTS = "20,22,5,19,18,48,26,85,133";
    /** Cheque statuses Atiran's own views treat as void/deleted. */
    private static final String VOID_CHEQUE_STATUSES = "N'6',N'7',N'8'";

    /** Live metadata access; implemented over a JDBC connection by the SQL gateway. */
    public interface SchemaReader {
        /** Safely quoted, schema-qualified table reference (for example {@code dbo.[dar]}), or null. */
        String tableRef(String table);

        /** Column names of a table in ordinal order; empty when the table does not exist. */
        List<String> columns(String table);
    }

    /** One built statement plus its bind values, in the order the placeholders appear. */
    public record PreparedQuery(String sql, List<Object> params) {
        public PreparedQuery {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    /** Builds the statement of one report. */
    public static PreparedQuery build(VariantSpec spec, SectionFilter filter, SchemaReader schema,
                                      String todayJalali) throws SectionUnavailable {
        SectionFilter f = filter == null ? SectionFilter.defaults() : filter;
        String today = SectionMath.normalizeJalali(todayJalali);
        String section = spec.sectionId();
        String variant = spec.id();
        if (section.equals(WindowsRoleMenus.CASH)) return cash(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.CHEQUES)) return cheques(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.STOCK_LEDGER)) return stock(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.REPORTS)) return reports(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.PROFIT)) return profit(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.PRODUCTION)) return production(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.PERSONNEL)) return personnel(variant, spec, f, schema, today);
        if (section.equals(WindowsRoleMenus.USERS)) return users(variant, spec, f, schema, today);
        throw new SectionUnavailable("بخش «" + SectionCatalog.titleOf(section) + "» در نسخهٔ دسکتاپ پیاده‌سازی نشده است.");
    }

    // =====================================================================================
    // صندوق و دریافتی‌ها — dbo.dar (p=0 receipt, p=1 payment), dbo.ban_act, dbo.PosDetails
    // =====================================================================================

    private static PreparedQuery cash(String variant, VariantSpec spec, SectionFilter f,
                                      SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "daily": return cashDaily(spec, f, schema, today);
            case "receipts": return cashVouchers(spec, f, schema, today, 0);
            case "payments": return cashVouchers(spec, f, schema, today, 1);
            case "banks": return cashBanks(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    private static PreparedQuery cashDaily(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                           String today) throws SectionUnavailable {
        String dar = needTable(schema, "dar");
        String direction = need(schema, "dar", "نوع قبض (دریافت/پرداخت)", "p", "P");
        String date = need(schema, "dar", "تاریخ قبض", "date", "DATE", "tarikh");
        String day = dateKey(date10("h", date));
        String total = darTotal(schema, "h");
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(int," + ref("h", direction) + ") IN (0,1)");
        addActive(schema, "dar", "h", conds);
        addSoft(schema, "dar", "h", conds);
        conds.add(dateCondition(day, SectionMath.window(spec, f, today), params));
        String sql = "SELECT " + top(spec.maxRows()) + " x.day, x.receipts_count, x.receipts, x.payments_count, x.payments FROM ("
                + "SELECT " + day + " AS day"
                + ", SUM(CASE WHEN TRY_CONVERT(int," + ref("h", direction) + ")=0 THEN 1 ELSE 0 END) AS receipts_count"
                + ", ISNULL(SUM(CASE WHEN TRY_CONVERT(int," + ref("h", direction) + ")=0 THEN " + total + " ELSE 0 END),0) AS receipts"
                + ", SUM(CASE WHEN TRY_CONVERT(int," + ref("h", direction) + ")=1 THEN 1 ELSE 0 END) AS payments_count"
                + ", ISNULL(SUM(CASE WHEN TRY_CONVERT(int," + ref("h", direction) + ")=1 THEN " + total + " ELSE 0 END),0) AS payments"
                + " FROM " + dar + " h" + where(conds)
                + " GROUP BY " + day + ") x WHERE x.day IS NOT NULL ORDER BY x.day DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery cashVouchers(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                              String today, int direction) throws SectionUnavailable {
        String dar = needTable(schema, "dar");
        String side = need(schema, "dar", "نوع قبض (دریافت/پرداخت)", "p", "P");
        String date = need(schema, "dar", "تاریخ قبض", "date", "DATE", "tarikh");
        String voucherNo = need(schema, "dar", "شمارهٔ قبض", "ghno", "GHNO");
        String customerCode = col(schema, "dar", "shmo", "SHMO");
        String cashColumn = col(schema, "dar", "naghd", "cash");
        String chequeColumn = col(schema, "dar", "mabcheck", "cheque");
        String kindColumn = col(schema, "dar", "darDescriptionTypeID", "DescriptionReceiveId");
        String invoiceColumn = col(schema, "dar", "shfacfo", "shfac");
        String total = darTotal(schema, "h");

        String party = "N'—'";
        String customerJoin = "";
        String customerTable = optionalTable(schema, "CUSTOMERS");
        String customerName = customerTable == null ? null : col(schema, "CUSTOMERS", "MONAME", "Name", "CusName", "CustomerName");
        String customerKey = customerTable == null ? null : col(schema, "CUSTOMERS", "SHMO", "shmo", "CustomerCode");
        if (customerTable != null && customerKey != null && customerCode != null) {
            customerJoin = " LEFT JOIN " + customerTable + " cu ON TRY_CONVERT(nvarchar(100),"
                    + ref("cu", customerKey) + ")=TRY_CONVERT(nvarchar(100)," + ref("h", customerCode) + ") ";
            party = customerName == null
                    ? "ISNULL(NULLIF(" + txt("h", customerCode, 100) + ",N''),N'—')"
                    : "COALESCE(NULLIF(" + txt("cu", customerName, 250) + ",N''),NULLIF("
                        + txt("h", customerCode, 100) + ",N''),N'—')";
        } else if (customerCode != null) {
            party = "ISNULL(NULLIF(" + txt("h", customerCode, 100) + ",N''),N'—')";
        }

        String posExists = posExistsCondition(schema, "h");
        String method;
        if (cashColumn == null && chequeColumn == null && posExists.isEmpty()) {
            method = "CAST(N'0' AS nvarchar(10))";
        } else {
            String cashValue = cashColumn == null ? "CAST(0 AS decimal(19,2))" : num("h", cashColumn);
            String chequeValue = chequeColumn == null ? "CAST(0 AS decimal(19,2))" : num("h", chequeColumn);
            method = "CAST(CASE";
            if (!posExists.isEmpty()) method += " WHEN " + posExists + " THEN N'4'";
            method += " WHEN " + cashValue + "<>0 AND " + chequeValue + "<>0 THEN N'5'"
                    + " WHEN " + chequeValue + "<>0 THEN N'3'"
                    + " WHEN " + cashValue + "<>0 THEN N'1'"
                    + " ELSE N'0' END AS nvarchar(10))";
        }

        String note = voucherNote(schema, kindColumn, invoiceColumn);

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(int," + ref("h", side) + ")=?");
        params.add(direction);
        addActive(schema, "dar", "h", conds);
        addSoft(schema, "dar", "h", conds);
        conds.add(dateCondition(dateKey(date10("h", date)), SectionMath.window(spec, f, today), params));
        addSearch(conds, params, f.query(), txt("h", voucherNo, 60), party, note);

        String sql = "SELECT " + top(spec.maxRows()) + txt("h", voucherNo, 60) + ", " + date10("h", date)
                + ", " + party + ", " + method + ", " + total + ", " + note
                + " FROM " + dar + " h" + customerJoin + where(conds)
                + " ORDER BY " + date10("h", date) + " DESC, " + txt("h", voucherNo, 60) + " DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery cashBanks(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                           String today) throws SectionUnavailable {
        String bankAct = needTable(schema, "ban_act");
        String bank = need(schema, "ban_act", "شناسهٔ حساب بانکی", "bank_rdf", "BankRdf", "bankrdf");
        String date = col(schema, "ban_act", "act_date", "done_act", "date");
        String credit = col(schema, "ban_act", "act_bes", "bes", "bestankar");
        String debit = col(schema, "ban_act", "act_bed", "bed", "bedehkar");
        if (credit == null && debit == null)
            throw new SectionUnavailable("ستون واریز یا برداشت در «گردش بانک» پیدا نشد؛ گردش دوطرفه قابل خواندن نیست.");
        String deposits = credit == null ? "CAST(0 AS decimal(19,2))" : num("h", credit);
        String withdrawals = debit == null ? "CAST(0 AS decimal(19,2))" : num("h", debit);

        String bankTable = optionalTable(schema, "BANK");
        String bankKey = bankTable == null ? null : col(schema, "BANK", "RDF", "rdf");
        String bankName = bankTable == null ? null : col(schema, "BANK", "name", "bankname", "title", "onvan");
        String bankJoin = bankTable != null && bankKey != null
                ? " LEFT JOIN " + bankTable + " b ON TRY_CONVERT(nvarchar(60)," + ref("b", bankKey)
                        + ")=TRY_CONVERT(nvarchar(60)," + ref("h", bank) + ") " : " ";
        String label = bankTable != null && bankKey != null && bankName != null
                ? "COALESCE(NULLIF(" + txt("b", bankName, 150) + ",N'')," + txt("h", bank, 60) + ",N'—')"
                : "COALESCE(" + txt("h", bank, 60) + ",N'—')";
        String bankId = txt("h", bank, 60);

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (date != null)
            conds.add(dateCondition(dateKey(date10("h", date)), SectionMath.window(spec, f, today), params));
        addActive(schema, "ban_act", "h", conds);
        addSoft(schema, "ban_act", "h", conds);
        addSearch(conds, params, f.query(), label, bankId);

        String sql = "SELECT " + top(spec.maxRows()) + label + ", COUNT(CASE WHEN " + deposits + "<>0 THEN 1 END)"
                + ", ISNULL(SUM(" + deposits + "),0), COUNT(CASE WHEN " + withdrawals + "<>0 THEN 1 END)"
                + ", ISNULL(SUM(" + withdrawals + "),0)"
                + " FROM " + bankAct + " h" + bankJoin + where(conds)
                + " GROUP BY " + label + ", " + bankId + " ORDER BY 3 DESC";
        return new PreparedQuery(sql, params);
    }

    /** Atiran books the voucher total in dar.mab; the cash + cheque parts are only a fallback. */
    private static String darTotal(SchemaReader schema, String alias) {
        String booked = col(schema, "dar", "mab", "Mab", "MAB");
        if (booked != null) return "ISNULL(TRY_CONVERT(decimal(19,2)," + ref(alias, booked) + ")," + darParts(schema, alias) + ")";
        return darParts(schema, alias);
    }

    private static String darParts(SchemaReader schema, String alias) {
        String cash = col(schema, "dar", "naghd", "cash");
        String cheque = col(schema, "dar", "mabcheck", "cheque");
        return "(" + (cash == null ? "CAST(0 AS decimal(19,2))" : num(alias, cash)) + "+"
                + (cheque == null ? "CAST(0 AS decimal(19,2))" : num(alias, cheque)) + ")";
    }

    /** True when the voucher has card/transfer detail rows in Atiran's PosDetails. */
    private static String posExistsCondition(SchemaReader schema, String parentAlias) {
        String details = optionalTable(schema, "PosDetails");
        if (details == null) return "";
        String voucher = col(schema, "PosDetails", "ghno", "GHNO");
        String side = col(schema, "PosDetails", "p");
        if (voucher == null || side == null) return "";
        String darVoucher = col(schema, "dar", "ghno", "GHNO");
        String darSide = col(schema, "dar", "p", "P");
        if (darVoucher == null || darSide == null) return "";
        return "EXISTS(SELECT 1 FROM " + details + " pd WHERE TRY_CONVERT(nvarchar(60)," + ref("pd", voucher)
                + ")=TRY_CONVERT(nvarchar(60)," + ref(parentAlias, darVoucher) + ") AND TRY_CONVERT(int,"
                + ref("pd", side) + ")=TRY_CONVERT(int," + ref(parentAlias, darSide) + "))";
    }

    /** Voucher description: Atiran's voucher-type dictionary first, then the linked invoice number. */
    private static String voucherNote(SchemaReader schema, String kindColumn, String invoiceColumn) {
        String kindTable = optionalTable(schema, "darDescriptionType");
        String kindId = kindTable == null ? null : col(schema, "darDescriptionType", "rowId", "RowId", "ID", "id");
        String kindName = kindTable == null ? null : col(schema, "darDescriptionType", "name", "title", "description", "onvan");
        String kind = kindTable != null && kindId != null && kindName != null && kindColumn != null
                ? "(SELECT TOP (1) " + txt("k", kindName, 200) + " FROM " + kindTable + " k WHERE TRY_CONVERT(nvarchar(50),"
                        + ref("k", kindId) + ")=TRY_CONVERT(nvarchar(50),h.[" + kindColumn.replace("]", "]]") + "]))"
                : null;
        String invoice = invoiceColumn == null ? null
                : "(N'فاکتور '+NULLIF(" + txt("h", invoiceColumn, 60) + ",N''))";
        if (kind != null && invoice != null) return "ISNULL(NULLIF(" + kind + ",N''),ISNULL(" + invoice + ",N''))";
        if (kind != null) return "ISNULL(" + kind + ",N'')";
        if (invoice != null) return "ISNULL(" + invoice + ",N'')";
        return "CAST(N'' AS nvarchar(400))";
    }

    // =====================================================================================
    // چک‌ها — getchk/putchk plus the prior-years archives NGetchk/NPutchk
    // =====================================================================================

    private static PreparedQuery cheques(String variant, VariantSpec spec, SectionFilter f,
                                         SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "incoming": return chequeList(spec, f, schema, today, true, false, false);
            case "outgoing": return chequeList(spec, f, schema, today, false, false, false);
            case "due": return chequeList(spec, f, schema, today, true, true, false);
            case "bounced": return chequeList(spec, f, schema, today, true, false, true);
            default: throw unknownReport(spec);
        }
    }

    private static PreparedQuery chequeList(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                            String today, boolean incoming, boolean dueOnly,
                                            boolean bouncedOnly) throws SectionUnavailable {
        String liveTable = incoming ? "getchk" : "putchk";
        String archiveTable = incoming ? "NGetchk" : "NPutchk";
        String live = needTable(schema, liveTable);
        String liveLeg = chequeLeg(schema, live, liveTable, incoming);
        String archive = optionalTable(schema, archiveTable);
        String source = liveLeg;
        if (archive != null) {
            String archiveLeg = chequeLeg(schema, archive, archiveTable, incoming);
            if (!archiveLeg.isEmpty()) source = liveLeg + " UNION ALL " + archiveLeg;
        }
        String from = "(" + source + ") h";

        String party = "ISNULL(NULLIF(" + txt("h", "shmo", 120) + ",N''),N'—')";
        String customerJoin = "";
        String customerTable = optionalTable(schema, "CUSTOMERS");
        String customerName = customerTable == null ? null : col(schema, "CUSTOMERS", "MONAME", "Name", "CusName", "CustomerName");
        String customerKey = customerTable == null ? null : col(schema, "CUSTOMERS", "SHMO", "shmo", "CustomerCode");
        if (customerTable != null && customerKey != null && customerName != null) {
            customerJoin = " LEFT JOIN " + customerTable + " cu ON TRY_CONVERT(nvarchar(100),"
                    + ref("cu", customerKey) + ")=TRY_CONVERT(nvarchar(100),h.[shmo]) ";
            party = "COALESCE(NULLIF(" + txt("cu", customerName, 250) + ",N''),NULLIF("
                    + txt("h", "shmo", 120) + ",N''),N'—')";
        }

        String status = "ISNULL(h.[status],N'')";
        String back = "UPPER(LTRIM(RTRIM(ISNULL(h.[back],N''))))";
        String dueDate = "ISNULL(NULLIF(h.[sardate],N''),ISNULL(h.[getdate],N''))";

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (bouncedOnly) {
            conds.add(back + " IN (N'T',N'1',N'TRUE')");
        } else if (dueOnly) {
            conds.add("(" + back + " NOT IN (N'T',N'1',N'TRUE') OR " + back + " IS NULL)");
            conds.add("(" + status + " NOT IN (N'3',N'4'))");
            SectionMath.DateWindow window = SectionMath.window(spec, f, today);
            conds.add(dateCondition(dateKey(dueDate), window, params));
        } else {
            SectionMath.DateWindow window = SectionMath.window(spec, f, today);
            if (!window.isEmpty())
                conds.add(dateCondition(dateKey(date10(null, "getdate")), window, params));
        }
        addSearch(conds, params, f.query(), txt("h", "num", 120), txt("h", "sayad", 120),
                txt("h", "bank", 200), party);

        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "num" -> select.add(txt("h", "num", 120));
                case "sayad" -> select.add(txt("h", "sayad", 120));
                case "party" -> select.add(party);
                case "bank" -> select.add("ISNULL(NULLIF(" + txt("h", "bank", 200) + ",N''),N'—')");
                case "amount" -> select.add("ISNULL(h.[amount],0)");
                case "issue_date" -> select.add("ISNULL(h.[getdate],N'')");
                case "due_date" -> select.add(dueDate);
                case "status" -> select.add(status);
                case "bounced" -> select.add(back);
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش چک‌ها شناخته نشد.");
            }
        }
        String order = dueOnly || !spec.sortKey().equals("amount")
                ? " ORDER BY " + dateKey(dueDate) + (spec.sortDescending() ? " DESC" : " ASC")
                : " ORDER BY ISNULL(h.[amount],0) DESC";
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + from + customerJoin
                + where(conds) + order;
        return new PreparedQuery(sql, params);
    }

    /** One leg of the cheque source; empty when the table cannot serve amount and status. */
    private static String chequeLeg(SchemaReader schema, String tableRef, String tableName, boolean incoming)
            throws SectionUnavailable {
        String amount = col(schema, tableName, incoming ? "getchkmab" : "putchkmab", "mablagh", "Mablagh", "amount");
        String status = col(schema, tableName, incoming ? "chk_satus" : "putchk_status", "status", "vaziat");
        if (amount == null || status == null) {
            if (tableName.equals("getchk") || tableName.equals("putchk"))
                throw new SectionUnavailable("ستون مبلغ یا وضعیت در «" + title(tableName) + "» پیدا نشد.");
            return "";
        }
        String[][] fields = {
                {"amount", amount, "decimal(19,2)"},
                {"status", status, "nvarchar(80)"},
                {"num", col(schema, tableName, incoming ? "shgetchk" : "shputchk", "serial", "number", "shomare"), "nvarchar(120)"},
                {"bank", col(schema, tableName, incoming ? "getchbank" : "bankrdf", "girande", "bank"), "nvarchar(200)"},
                {"sardate", col(schema, tableName, "sardate", "sarresid", "due_date", "duedate"), "nvarchar(40)"},
                {"getdate", col(schema, tableName, incoming ? "getdate" : "putdate", "DateOfReceipt", "done_date", "date"), "nvarchar(40)"},
                {"back", col(schema, tableName, "back"), "nvarchar(20)"},
                {"shmo", col(schema, tableName, "shmo", "SHMO"), "nvarchar(120)"},
                {"sayad", col(schema, tableName, "ShenaseSayad", "sayad"), "nvarchar(120)"},
        };
        List<String> select = new ArrayList<>();
        for (String[] field : fields) {
            select.add(field[1] == null
                    ? "CAST(NULL AS " + field[2] + ") AS [" + field[0] + "]"
                    : "TRY_CAST(" + q(field[1]) + " AS " + field[2] + ") AS [" + field[0] + "]");
        }
        String validStatus = "(LTRIM(RTRIM(TRY_CONVERT(nvarchar(40)," + q(status) + "))) NOT IN ("
                + VOID_CHEQUE_STATUSES + ") OR " + q(status) + " IS NULL)";
        return "SELECT " + join(select, ", ") + " FROM " + tableRef + " WHERE " + validStatus;
    }

    // =====================================================================================
    // انبار — dbo.inventory, dbo.ka_act, dbo.inventory_anbars, dbo.anbars
    // =====================================================================================

    private static PreparedQuery stock(String variant, VariantSpec spec, SectionFilter f,
                                       SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "stock": return stockList(spec, f, schema, today);
            case "kardex": return stockKardex(spec, f, schema, today);
            case "alerts": return stockAlerts(spec, f, schema, today);
            case "dead": return stockDead(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    /** (mojkavah × mohvah) + mojkajoz — Atiran's own counting unit for stock and cost. */
    private static String stockPieces(SchemaReader schema, String alias) throws SectionUnavailable {
        String packs = need(schema, "inventory", "موجودی کالا", "mojkavah", "MojKavah");
        String loose = col(schema, "inventory", "mojkajoz", "MojKajoz");
        String perPack = col(schema, "inventory", "mohvah");
        String base = "TRY_CONVERT(decimal(19,3)," + ref(alias, packs) + ")";
        if (perPack != null)
            base = "(" + base + "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref(alias, perPack) + "),0),1))";
        if (loose != null) base += "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref(alias, loose) + "),0)";
        return "(" + base + ")";
    }

    private static String stockCost(SchemaReader schema, String alias) throws SectionUnavailable {
        String cost = need(schema, "inventory", "بهای خرید کالا",
                "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice", "ProductionPrice");
        return "COALESCE(TRY_CONVERT(decimal(19,2)," + ref(alias, cost) + "),0)";
    }

    private static String productGroupJoin(SchemaReader schema) {
        String groupColumn = col(schema, "inventory", "group_rdf");
        String groupTable = optionalTable(schema, "kagroup");
        String groupKey = groupTable == null ? null : col(schema, "kagroup", "group_rdf");
        String groupName = groupTable == null ? null : col(schema, "kagroup", "group_name", "name");
        if (groupColumn == null || groupTable == null || groupKey == null || groupName == null) return "";
        return " LEFT JOIN " + groupTable + " g ON TRY_CONVERT(nvarchar(50)," + ref("g", groupKey)
                + ")=TRY_CONVERT(nvarchar(50)," + ref("i", groupColumn) + ") ";
    }

    private static String productGroupLabel(SchemaReader schema) {
        String groupName = optionalTable(schema, "kagroup") == null ? null : col(schema, "kagroup", "group_name", "name");
        return groupName == null || productGroupJoin(schema).isEmpty()
                ? "CAST(N'—' AS nvarchar(150))"
                : "COALESCE(NULLIF(" + txt("g", groupName, 150) + ",N''),N'بدون گروه')";
    }

    private static PreparedQuery stockList(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                           String today) throws SectionUnavailable {
        String inventory = needTable(schema, "inventory");
        String code = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String name = need(schema, "inventory", "نام کالا", "naka", "NAKA");
        String packs = col(schema, "inventory", "mojkavah", "MojKavah");
        String loose = col(schema, "inventory", "mojkajoz", "MojKajoz");
        String unit = col(schema, "inventory", "vahsanj", "unit");
        String pieces = stockPieces(schema, "i");
        String cost = stockCost(schema, "i");
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "inventory", "i", conds);
        String blocked = col(schema, "inventory", "black_list", "blacklist");
        if (blocked != null) conds.add("ISNULL(TRY_CONVERT(int," + ref("i", blocked) + "),0)=0");
        conds.add("TRY_CONVERT(bigint," + ref("i", code) + ") IS NOT NULL");
        addSearch(conds, params, f.query(), txt("i", code, 100), txt("i", name, 250), barcodeExpression(schema));
        String value = pieces + "*" + cost;
        String sql = "SELECT " + top(spec.maxRows()) + txt("i", code, 100) + ", " + txt("i", name, 250)
                + ", " + productGroupLabel(schema) + ", " + txtOr(unit == null ? null : "i", unit, 60, "—")
                + ", " + num3("i", packs) + ", " + num3("i", loose) + ", " + pieces + ", " + cost
                + ", " + value
                + " FROM " + inventory + " i" + productGroupJoin(schema) + where(conds)
                + " ORDER BY " + value + " DESC";
        return new PreparedQuery(sql, params);
    }

    /** Barcode columns of inventory, when the installation keeps them; empty means no barcode search. */
    private static String barcodeExpression(SchemaReader schema) {
        String barcode = col(schema, "inventory", "StuffCode", "Code", "Barcode", "BarCode", "KalaCode", "coka");
        return barcode == null ? null : txt("i", barcode, 100);
    }

    private static PreparedQuery stockKardex(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                             String today) throws SectionUnavailable {
        String kardex = needTable(schema, "ka_act");
        String code = need(schema, "ka_act", "کد کالا", "shka", "SHKA");
        String date = col(schema, "ka_act", "act_date", "done_date", "date");
        String actId = col(schema, "ka_act", "act_id", "ActID", "actid");
        String packs = need(schema, "ka_act", "تعداد گردش", "tedvah", "TEDVAH");
        String loose = col(schema, "ka_act", "tedjoz", "TEDJOZ");
        String doc = col(schema, "ka_act", "shfacfo", "shfackh", "ghno", "shfac", "sanad", "docnumber");
        String warehouseColumn = col(schema, "ka_act", "RdfAnbar", "rdf_anbar");
        String perPack = col(schema, "inventory", "mohvah");
        String inventory = optionalTable(schema, "inventory");
        String inventoryCode = inventory == null ? null : col(schema, "inventory", "shka", "SHKA");
        String inventoryName = inventory == null ? null : col(schema, "inventory", "naka", "NAKA");
        String inventoryJoin = inventory != null && inventoryCode != null
                ? " LEFT JOIN " + inventory + " i ON TRY_CONVERT(nvarchar(100)," + ref("i", inventoryCode)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("h", code) + ") " : " ";
        String quantity = "TRY_CONVERT(decimal(19,3)," + ref("h", packs) + ")"
                + (perPack != null && inventoryJoin.trim().length() > 0
                        ? "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref("i", perPack) + "),0),1)" : "")
                + (loose == null ? "" : "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref("h", loose) + "),0)");
        quantity = "(" + quantity + ")";
        String outFlag = actId == null ? "0" : "CASE WHEN TRY_CONVERT(int," + ref("h", actId) + ") IN ("
                + STOCK_OUT_ACTS + ") THEN 1 ELSE 0 END";

        String namesTable = optionalTable(schema, "ActNames");
        String namesKey = namesTable == null ? null : col(schema, "ActNames", "ActID", "act_id", "actid");
        String namesText = namesTable == null ? null : col(schema, "ActNames", "ActName", "act_name", "name");
        String namesJoin = namesTable != null && namesKey != null && namesText != null && actId != null
                ? " LEFT JOIN " + namesTable + " an ON TRY_CONVERT(int," + ref("an", namesKey)
                        + ")=TRY_CONVERT(int," + ref("h", actId) + ") " : " ";
        String operation = namesJoin.trim().isEmpty()
                ? (actId == null ? "CAST(N'گردش کالا' AS nvarchar(200))"
                        : "COALESCE(N'عملیات '+NULLIF(" + txt("h", actId, 20) + ",N''),N'گردش کالا')")
                : "COALESCE(NULLIF(" + txt("an", namesText, 200) + ",N''),N'گردش کالا')";

        String warehouses = optionalTable(schema, "anbars");
        String warehouseKey = warehouses == null ? null : col(schema, "anbars", "rdf_anbar");
        String warehouseName = warehouses == null ? null : col(schema, "anbars", "name");
        String warehouseJoin = warehouses != null && warehouseKey != null && warehouseColumn != null
                ? " LEFT JOIN " + warehouses + " w ON TRY_CONVERT(nvarchar(60)," + ref("w", warehouseKey)
                        + ")=TRY_CONVERT(nvarchar(60)," + ref("h", warehouseColumn) + ") " : " ";
        String warehouse = warehouseColumn == null ? "CAST(N'—' AS nvarchar(120))"
                : warehouses != null && warehouseName != null && !warehouseJoin.trim().isEmpty()
                        ? "COALESCE(NULLIF(" + txt("w", warehouseName, 120) + ",N'')," + txt("h", warehouseColumn, 60) + ",N'—')"
                        : "COALESCE(NULLIF(" + txt("h", warehouseColumn, 60) + ",N''),N'—')";
        String productName = inventoryName == null || inventoryJoin.trim().isEmpty()
                ? txt("h", code, 100)
                : "COALESCE(NULLIF(" + txt("i", inventoryName, 250) + ",N'')," + txt("h", code, 100) + ")";

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "ka_act", "h", conds);
        if (date != null)
            conds.add(dateCondition(dateKey(date10("h", date)), SectionMath.window(spec, f, today), params));
        addSearch(conds, params, f.query(), txt("h", code, 100), productName);

        String day = date == null ? "CAST(N'' AS nvarchar(30))" : date10("h", date);
        String sql = "SELECT " + top(spec.maxRows()) + day + ", " + txt("h", code, 100) + ", " + productName
                + ", " + (doc == null ? "CAST(N'' AS nvarchar(80))" : txt("h", doc, 80)) + ", " + operation
                + ", " + warehouse
                + ", CASE WHEN " + outFlag + "=1 THEN CAST(0 AS decimal(19,3)) ELSE " + quantity + " END"
                + ", CASE WHEN " + outFlag + "=1 THEN " + quantity + " ELSE CAST(0 AS decimal(19,3)) END"
                + " FROM " + kardex + " h" + inventoryJoin + namesJoin + warehouseJoin + where(conds)
                + (date == null ? "" : " ORDER BY " + dateKey(date10("h", date)) + " DESC");
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery stockAlerts(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                             String today) throws SectionUnavailable {
        String inventory = needTable(schema, "inventory");
        String code = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String name = need(schema, "inventory", "نام کالا", "naka", "NAKA");
        String reorder = col(schema, "inventory", "reopoint", "reorder_point", "reorderpoint");
        String pieces = stockPieces(schema, "i");
        String point = reorder == null ? "CAST(0 AS decimal(19,3))" : num3("i", reorder);
        String needed = "CASE WHEN " + point + ">0 THEN (CASE WHEN " + pieces + "<=" + point + " THEN "
                + point + "-" + pieces + " ELSE CAST(0 AS decimal(19,3)) END)"
                + " ELSE (CASE WHEN " + pieces + "<=0 THEN CAST(1 AS decimal(19,3)) ELSE CAST(0 AS decimal(19,3)) END) END";
        String condition = reorder == null
                ? pieces + "<=0"
                : "(" + pieces + "<=0 OR (" + point + ">0 AND " + pieces + "<=" + point + "))";
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "inventory", "i", conds);
        conds.add(condition);
        addSearch(conds, params, f.query(), txt("i", code, 100), txt("i", name, 250));
        String sql = "SELECT " + top(spec.maxRows()) + txt("i", code, 100) + ", " + txt("i", name, 250)
                + ", " + productGroupLabel(schema) + ", " + pieces + ", " + point + ", " + needed
                + ", ISNULL(ls.last_day,N'')"
                + " FROM " + inventory + " i" + productGroupJoin(schema) + lastSaleApply(schema, "i", code)
                + where(conds) + " ORDER BY " + needed + " DESC, " + pieces + " ASC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery stockDead(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                           String today) throws SectionUnavailable {
        String inventory = needTable(schema, "inventory");
        String code = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String name = need(schema, "inventory", "نام کالا", "naka", "NAKA");
        String pieces = stockPieces(schema, "i");
        String cost = stockCost(schema, "i");
        String value = pieces + "*" + cost;
        String cutoff = SectionMath.idleCutoff(spec, f, today);
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "inventory", "i", conds);
        if (!cutoff.isEmpty()) {
            conds.add("(ls.last_day IS NULL OR LTRIM(RTRIM(ls.last_day))=N'' OR " + dateKey("ls.last_day") + "<?)");
            params.add(cutoff);
        }
        addSearch(conds, params, f.query(), txt("i", code, 100), txt("i", name, 250));
        String sql = "SELECT " + top(spec.maxRows()) + txt("i", code, 100) + ", " + txt("i", name, 250)
                + ", " + productGroupLabel(schema) + ", " + pieces + ", " + value
                + ", ISNULL(ls.last_day,N'')"
                + " FROM " + inventory + " i" + productGroupJoin(schema) + lastSaleApply(schema, "i", code)
                + where(conds) + " ORDER BY " + value + " DESC";
        return new PreparedQuery(sql, params);
    }

    /** Last sale date per product, from Atiran's sales header and detail tables when both exist. */
    private static String lastSaleApply(SchemaReader schema, String inventoryAlias, String inventoryCode) {
        String sales = optionalTable(schema, "sailfact");
        String detailTable = optionalTable(schema, "subsailfact");
        if (sales == null || detailTable == null) return " OUTER APPLY (SELECT CAST(N'' AS nvarchar(30)) AS last_day) ls ";
        String salesNo = col(schema, "sailfact", "shfacfo", "factor_no", "invoice_no");
        String salesDate = col(schema, "sailfact", "date", "t_date", "tarikh");
        String linkNo = col(schema, "subsailfact", "shfacfo", "factor_no", "invoice_no");
        String detailCode = col(schema, "subsailfact", "SHKA", "shka", "product_code");
        if (salesNo == null || salesDate == null || linkNo == null || detailCode == null)
            return " OUTER APPLY (SELECT CAST(N'' AS nvarchar(30)) AS last_day) ls ";
        String active = activeCondition(schema, "sailfact", "s");
        return " OUTER APPLY (SELECT MAX(" + dateKey(date10("s", salesDate)) + ") AS last_day FROM " + sales
                + " s JOIN " + detailTable + " d ON TRY_CONVERT(nvarchar(100)," + ref("d", linkNo)
                + ")=TRY_CONVERT(nvarchar(100)," + ref("s", salesNo) + ")"
                + " WHERE TRY_CONVERT(nvarchar(100)," + ref("d", detailCode) + ")=TRY_CONVERT(nvarchar(100),"
                + ref(inventoryAlias, inventoryCode) + ")" + active + ") ls ";
    }

    // =====================================================================================
    // گزارش‌های فروش — dbo.sailfact, dbo.subsailfact, dbo.CUSTOMERS, dbo.cust_act
    // =====================================================================================

    private static PreparedQuery reports(String variant, VariantSpec spec, SectionFilter f,
                                         SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "sales_daily": return salesGrouped(spec, f, schema, today, false);
            case "sales_monthly": return salesGrouped(spec, f, schema, today, true);
            case "top_customers": return topCustomers(spec, f, schema, today);
            case "top_products": return topProducts(spec, f, schema, today);
            case "aging": return aging(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    /** Daily or monthly sales with discount, tax and collected amounts. */
    private static PreparedQuery salesGrouped(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                              String today, boolean monthly) throws SectionUnavailable {
        String sales = needTable(schema, "sailfact");
        String date = need(schema, "sailfact", "تاریخ فاکتور", "date", "t_date", "tarikh");
        String amount = need(schema, "sailfact", "مبلغ فاکتور", "all_fel", "all", "amount", "total", "mablagh");
        String discount = col(schema, "sailfact", "tafif", "discount");
        String tax = col(schema, "sailfact", "tax", "ptax", "avarez");
        String collected = col(schema, "sailfact", "MabDaryaftFactor", "Daryaft", "daryaft");
        String bucket = monthly ? month7("x", date) : dateKey(date10("x", date));
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "sailfact", "x", conds);
        addSoft(schema, "sailfact", "x", conds);
        conds.add(dateCondition(bucket, SectionMath.window(spec, f, today), params));
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "date", "month" -> select.add("h.bucket");
                case "docs" -> select.add("ISNULL(SUM(h.docs),0)");
                case "gross" -> select.add("ISNULL(SUM(h.gross),0)");
                case "discount" -> select.add("ISNULL(SUM(h.discount),0)");
                case "tax" -> select.add("ISNULL(SUM(h.tax),0)");
                case "collected" -> select.add("ISNULL(SUM(h.collected),0)");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش فروش شناخته نشد.");
            }
        }
        String net = discount == null ? num("x", amount) : "(" + num("x", amount) + "-" + num("x", discount) + ")";
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM (SELECT " + bucket
                + " AS bucket, CAST(1 AS bigint) AS docs, " + net + " AS gross, "
                + (discount == null ? "CAST(0 AS decimal(19,2))" : num("x", discount)) + " AS discount, "
                + (tax == null ? "CAST(0 AS decimal(19,2))" : num("x", tax)) + " AS tax, "
                + (collected == null ? "CAST(0 AS decimal(19,2))" : num("x", collected)) + " AS collected"
                + " FROM " + sales + " x" + where(conds) + ") h"
                + " WHERE LTRIM(RTRIM(ISNULL(h.bucket,N'')))<>N''"
                + " GROUP BY h.bucket ORDER BY h.bucket DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery topCustomers(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                              String today) throws SectionUnavailable {
        String customers = needTable(schema, "CUSTOMERS");
        String code = need(schema, "CUSTOMERS", "کد مشتری", "SHMO", "shmo", "CustomerCode");
        String name = col(schema, "CUSTOMERS", "MONAME", "Name", "CusName", "CustomerName");
        Fragment salesApply = customerSalesApply(schema, "c", code, today, spec, f);
        Balance balance = customerBalance(schema, "c", code);
        List<Object> params = new ArrayList<>(salesApply.params());
        List<String> conds = new ArrayList<>();
        conds.add("ISNULL(sx.gross,0)<>0");
        addSearch(conds, params, f.query(), txt("c", code, 100), name == null ? null : txt("c", name, 250));
        String party = name == null ? txt("c", code, 100)
                : "COALESCE(NULLIF(" + txt("c", name, 250) + ",N'')," + txt("c", code, 100) + ")";
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> select.add(txt("c", code, 100));
                case "name" -> select.add(party);
                case "docs" -> select.add("ISNULL(sx.docs,0)");
                case "gross" -> select.add("ISNULL(sx.gross,0)");
                case "collected" -> select.add("ISNULL(sx.collected,0)");
                case "balance" -> select.add(balance.expression());
                case "last" -> select.add("ISNULL(sx.last_day,N'')");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش مشتریان برتر شناخته نشد.");
            }
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + customers + " c "
                + balance.apply() + salesApply.sql() + where(conds) + " ORDER BY ISNULL(sx.gross,0) DESC";
        return new PreparedQuery(sql, ordered(balance.params(), params, sql));
    }

    private static PreparedQuery topProducts(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                             String today) throws SectionUnavailable {
        String sales = needTable(schema, "sailfact");
        String detailTable = needTable(schema, "subsailfact");
        String salesNo = need(schema, "sailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String salesDate = need(schema, "sailfact", "تاریخ فاکتور", "date", "t_date", "tarikh");
        String linkNo = need(schema, "subsailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String detailCode = need(schema, "subsailfact", "کد کالا", "SHKA", "shka", "product_code");
        String quantity = col(schema, "subsailfact", "tedvah_fel", "TEDVAH", "tedvah", "quantity", "qty");
        String lineTotal = col(schema, "subsailfact", "linesum_fel", "LINESUM", "linesum", "line_total", "amount");
        String detailName = col(schema, "subsailfact", "naka", "Desc_Naka", "name", "item_name");
        String inventory = optionalTable(schema, "inventory");
        String inventoryCode = inventory == null ? null : col(schema, "inventory", "shka", "SHKA");
        String inventoryName = inventory == null ? null : col(schema, "inventory", "naka", "NAKA");
        String costColumn = inventory == null ? null : col(schema, "inventory",
                "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String perPack = inventory == null ? null : col(schema, "inventory", "mohvah");
        String loose = col(schema, "subsailfact", "tedjoz_fel", "TEDJOZ", "tedjoz");

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "sailfact", "x", conds);
        addSoft(schema, "sailfact", "x", conds);
        conds.add(dateCondition(dateKey(date10("x", salesDate)), SectionMath.window(spec, f, today), params));
        String source = dedupe(sales, "x", conds);
        String inventoryJoin = inventory != null && inventoryCode != null
                ? " LEFT JOIN " + inventory + " i ON TRY_CONVERT(nvarchar(100)," + ref("i", inventoryCode)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("d", detailCode) + ") " : " ";
        String productLabel = "COALESCE("
                + (inventoryName != null && !inventoryJoin.trim().isEmpty()
                        ? "NULLIF(" + txt("i", inventoryName, 250) + ",N'')," : "")
                + (detailName == null ? "" : "NULLIF(" + txt("d", detailName, 250) + ",N''),")
                + txt("d", detailCode, 120) + ",N'—')";
        String pieces = quantity == null ? "CAST(0 AS decimal(19,3))"
                : "(TRY_CONVERT(decimal(19,3)," + ref("d", quantity) + ")"
                        + (perPack == null || inventoryJoin.trim().isEmpty() ? ""
                                : "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref("i", perPack) + "),0),1)")
                        + (loose == null ? "" : "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref("d", loose) + "),0)") + ")";
        String turnover = lineTotal == null ? "CAST(0 AS decimal(19,2))" : num("d", lineTotal);
        String cogs = costColumn == null || inventoryJoin.trim().isEmpty() ? "CAST(0 AS decimal(19,2))"
                : "(" + pieces + "*COALESCE(TRY_CONVERT(decimal(19,2)," + ref("i", costColumn) + "),0))";

        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> { select.add(txt("d", detailCode, 120)); groupBy.add(txt("d", detailCode, 120)); }
                case "name" -> { select.add(productLabel); groupBy.add(productLabel); }
                case "group" -> { select.add(productGroupLabel(schema)); groupBy.add(productGroupLabel(schema)); }
                case "qty" -> select.add("ISNULL(SUM(" + pieces + "),0)");
                case "docs" -> select.add("COUNT(DISTINCT " + txt("d", linkNo, 100) + ")");
                case "gross", "net" -> select.add("ISNULL(SUM(" + turnover + "),0)");
                case "cogs" -> select.add("ISNULL(SUM(" + cogs + "),0)");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش کالاها شناخته نشد.");
            }
        }
        // Product terms can only be tested after the join, so the search is a HAVING condition.
        String having = "";
        if (!f.query().trim().isEmpty()) {
            List<String> terms = new ArrayList<>();
            String pattern = "%" + escapeLike(f.query()) + "%";
            List<String> searchable = new ArrayList<>();
            searchable.add(productLabel);
            searchable.add(txt("d", detailCode, 120));
            for (String expression : searchable) {
                terms.add("TRY_CONVERT(nvarchar(300)," + expression + ") LIKE ? ESCAPE '\'");
                params.add(pattern);
            }
            having = " HAVING " + join(terms, " OR ");
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + source
                + " JOIN " + detailTable + " d ON TRY_CONVERT(nvarchar(100)," + ref("d", linkNo)
                + ")=TRY_CONVERT(nvarchar(100)," + ref("h", salesNo) + ")"
                + inventoryJoin + productGroupJoin(schema)
                + " GROUP BY " + join(groupBy, ", ") + having
                + " ORDER BY ISNULL(SUM(" + turnover + "),0) DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery aging(VariantSpec spec, SectionFilter f, SchemaReader schema, String today)
            throws SectionUnavailable {
        String customers = needTable(schema, "CUSTOMERS");
        String code = need(schema, "CUSTOMERS", "کد مشتری", "SHMO", "shmo", "CustomerCode");
        String name = col(schema, "CUSTOMERS", "MONAME", "Name", "CusName", "CustomerName");
        Balance balance = customerBalance(schema, "c", code);
        String activity = customerActivityApply(schema, code);
        List<Object> params = new ArrayList<>(balance.params());
        List<String> conds = new ArrayList<>();
        conds.add(balance.expression() + " > 1");
        addSearch(conds, params, f.query(), txt("c", code, 100), name == null ? null : txt("c", name, 250));
        String party = name == null ? txt("c", code, 100)
                : "COALESCE(NULLIF(" + txt("c", name, 250) + ",N'')," + txt("c", code, 100) + ")";
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> select.add(txt("c", code, 100));
                case "name" -> select.add(party);
                case "balance" -> select.add(balance.expression());
                case "docs" -> select.add(activity.isEmpty() ? "CAST(0 AS bigint)" : "ISNULL(ax.docs,0)");
                case "last" -> select.add(activity.isEmpty() ? "CAST(N'' AS nvarchar(30))" : "ISNULL(ax.last_day,N'')");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش سن مطالبات شناخته نشد.");
            }
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + customers + " c "
                + balance.apply() + activity + where(conds) + " ORDER BY " + balance.expression() + " DESC";
        return new PreparedQuery(sql, ordered(balance.params(), params, sql));
    }

    // =====================================================================================
    // سود — sales minus COGS on Atiran's own purchase-price basis
    // =====================================================================================

    private static PreparedQuery profit(String variant, VariantSpec spec, SectionFilter f,
                                        SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "daily": return profitGrouped(spec, f, schema, today, false);
            case "monthly": return profitGrouped(spec, f, schema, today, true);
            case "by_product": return profitByProduct(spec, f, schema, today, false);
            case "low_margin": return profitByProduct(spec, f, schema, today, true);
            case "costs": return profitCosts(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    /**
     * Sales and COGS are aggregated in separate legs and summed per bucket, exactly like the Android
     * profit report: joining header and lines first would multiply the header amount per line.
     */
    private static PreparedQuery profitGrouped(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                               String today, boolean monthly) throws SectionUnavailable {
        String sales = needTable(schema, "sailfact");
        String detailTable = needTable(schema, "subsailfact");
        String date = need(schema, "sailfact", "تاریخ فاکتور", "date", "t_date", "tarikh");
        String salesNo = need(schema, "sailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String amount = need(schema, "sailfact", "مبلغ فاکتور", "all_fel", "all", "amount", "total", "mablagh");
        String discount = col(schema, "sailfact", "tafif", "discount");
        String linkNo = need(schema, "subsailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String detailCode = need(schema, "subsailfact", "کد کالا", "SHKA", "shka", "product_code");
        String quantity = need(schema, "subsailfact", "تعداد", "tedvah_fel", "TEDVAH", "tedvah");
        String loose = col(schema, "subsailfact", "tedjoz_fel", "TEDJOZ", "tedjoz");
        String inventory = needTable(schema, "inventory");
        String inventoryCode = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String costColumn = need(schema, "inventory", "قیمت خرید کالا",
                "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String perPack = col(schema, "inventory", "mohvah");

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "sailfact", "x", conds);
        addSoft(schema, "sailfact", "x", conds);
        conds.add(dateCondition(dateKey(date10("x", date)), SectionMath.window(spec, f, today), params));
        String source = dedupe(sales, "x", conds);
        String bucket = monthly ? month7("h", date) : dateKey(date10("h", date));
        String net = discount == null ? num("h", amount) : "(" + num("h", amount) + "-" + num("h", discount) + ")";
        String pieces = "(TRY_CONVERT(decimal(19,3)," + ref("d", quantity) + ")"
                + (perPack == null ? "" : "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref("i", perPack) + "),0),1)")
                + (loose == null ? "" : "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref("d", loose) + "),0)") + ")";
        String cogs = "(" + pieces + "*COALESCE(TRY_CONVERT(decimal(19,2)," + ref("i", costColumn) + "),0))";
        String detailJoin = " JOIN " + detailTable + " d ON TRY_CONVERT(nvarchar(100)," + ref("d", linkNo)
                + ")=TRY_CONVERT(nvarchar(100)," + ref("h", salesNo) + ")";
        String inventoryJoin = " LEFT JOIN " + inventory + " i ON TRY_CONVERT(nvarchar(100),"
                + ref("i", inventoryCode) + ")=TRY_CONVERT(nvarchar(100)," + ref("d", detailCode) + ") ";

        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "date", "month" -> select.add("u.bucket");
                case "docs" -> select.add("ISNULL(SUM(u.docs),0)");
                case "net", "gross" -> select.add("ISNULL(SUM(u.net),0)");
                case "cogs" -> select.add("ISNULL(SUM(u.cogs),0)");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش سود شناخته نشد.");
            }
        }
        List<Object> all = new ArrayList<>(params);
        all.addAll(params);
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM ("
                + "SELECT " + bucket + " AS bucket, CAST(1 AS bigint) AS docs, " + net + " AS net, CAST(0 AS decimal(19,2)) AS cogs"
                + " FROM " + source
                + " UNION ALL SELECT " + bucket + ", CAST(0 AS bigint), CAST(0 AS decimal(19,2)), " + cogs
                + " FROM " + source + detailJoin + inventoryJoin + ") u"
                + " WHERE LTRIM(RTRIM(ISNULL(u.bucket,N'')))<>N''"
                + " GROUP BY u.bucket ORDER BY u.bucket DESC";
        return new PreparedQuery(sql, all);
    }

    private static PreparedQuery profitByProduct(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                 String today, boolean lowMarginOnly) throws SectionUnavailable {
        String sales = needTable(schema, "sailfact");
        String detailTable = needTable(schema, "subsailfact");
        String date = need(schema, "sailfact", "تاریخ فاکتور", "date", "t_date", "tarikh");
        String salesNo = need(schema, "sailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String linkNo = need(schema, "subsailfact", "شمارهٔ فاکتور", "shfacfo", "factor_no", "invoice_no");
        String detailCode = need(schema, "subsailfact", "کد کالا", "SHKA", "shka", "product_code");
        String quantity = need(schema, "subsailfact", "تعداد", "tedvah_fel", "TEDVAH", "tedvah");
        String lineTotal = col(schema, "subsailfact", "linesum_fel", "LINESUM", "linesum", "line_total", "amount");
        String detailName = col(schema, "subsailfact", "naka", "Desc_Naka", "name", "item_name");
        String inventory = needTable(schema, "inventory");
        String inventoryCode = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String inventoryName = col(schema, "inventory", "naka", "NAKA");
        String costColumn = need(schema, "inventory", "قیمت خرید کالا",
                "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String perPack = col(schema, "inventory", "mohvah");
        String loose = col(schema, "subsailfact", "tedjoz_fel", "TEDJOZ", "tedjoz");

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "sailfact", "x", conds);
        addSoft(schema, "sailfact", "x", conds);
        conds.add(dateCondition(dateKey(date10("x", date)), SectionMath.window(spec, f, today), params));
        String source = dedupe(sales, "x", conds);
        String turnover = lineTotal == null ? "CAST(0 AS decimal(19,2))" : num("d", lineTotal);
        String pieces = "(TRY_CONVERT(decimal(19,3)," + ref("d", quantity) + ")"
                + (perPack == null ? "" : "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref("i", perPack) + "),0),1)")
                + (loose == null ? "" : "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref("d", loose) + "),0)") + ")";
        String cogs = "(" + pieces + "*COALESCE(TRY_CONVERT(decimal(19,2)," + ref("i", costColumn) + "),0))";
        String productLabel = "COALESCE(" + (inventoryName == null ? "" : "NULLIF(" + txt("i", inventoryName, 250) + ",N''),")
                + (detailName == null ? "" : "NULLIF(" + txt("d", detailName, 250) + ",N''),")
                + txt("d", detailCode, 120) + ",N'—')";
        String salesTotal = "ISNULL(SUM(" + turnover + "),0)";
        String cogsTotal = "ISNULL(SUM(" + cogs + "),0)";
        String margin = "CASE WHEN " + salesTotal + "<>0 THEN ROUND((" + salesTotal + "-" + cogsTotal
                + ")*100.0/" + salesTotal + ",2) ELSE CAST(0 AS decimal(19,2)) END";

        List<String> select = new ArrayList<>();
        List<String> groupBy = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> { select.add(txt("d", detailCode, 120)); groupBy.add(txt("d", detailCode, 120)); }
                case "name" -> { select.add(productLabel); groupBy.add(productLabel); }
                case "group" -> { select.add(productGroupLabel(schema)); groupBy.add(productGroupLabel(schema)); }
                case "qty" -> select.add("ISNULL(SUM(" + pieces + "),0)");
                case "net", "gross" -> select.add(salesTotal);
                case "cogs" -> select.add(cogsTotal);
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش سود شناخته نشد.");
            }
        }
        List<String> having = new ArrayList<>();
        having.add(salesTotal + "<>0");
        if (lowMarginOnly) {
            BigDecimal threshold = SectionCatalog.thresholdOf(spec, f.threshold());
            having.add(margin + "<=?");
            params.add(threshold);
        }
        if (!f.query().trim().isEmpty()) {
            having.add("(TRY_CONVERT(nvarchar(250)," + productLabel + ") LIKE ? ESCAPE '\\'"
                    + " OR TRY_CONVERT(nvarchar(120)," + txt("d", detailCode, 120) + ") LIKE ? ESCAPE '\\')");
            String pattern = "%" + escapeLike(f.query()) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + source
                + " JOIN " + detailTable + " d ON TRY_CONVERT(nvarchar(100)," + ref("d", linkNo)
                + ")=TRY_CONVERT(nvarchar(100)," + ref("h", salesNo) + ") "
                + "LEFT JOIN " + inventory + " i ON TRY_CONVERT(nvarchar(100)," + ref("i", inventoryCode)
                + ")=TRY_CONVERT(nvarchar(100)," + ref("d", detailCode) + ") "
                + productGroupJoin(schema)
                + " GROUP BY " + join(groupBy, ", ") + " HAVING " + join(having, " AND ")
                + (lowMarginOnly ? " ORDER BY " + margin + " ASC" : " ORDER BY (" + salesTotal + "-" + cogsTotal + ") DESC");
        return new PreparedQuery(sql, params);
    }

    /** Side and indirect costs registered in Atiran, unioned over whichever sources exist. */
    private static PreparedQuery profitCosts(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                             String today) throws SectionUnavailable {
        String[][] sources = {
                {"ExternalCosts", "هزینهٔ جانبی"},
                {"IndirectCost", "هزینهٔ غیرمستقیم"},
                {"kasr_e_sanad", "کسری انبار"},
        };
        List<String> legs = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        SectionMath.DateWindow window = SectionMath.window(spec, f, today);
        String search = f.query().trim();
        for (String[] source : sources) {
            String tableRef = optionalTable(schema, source[0]);
            if (tableRef == null) continue;
            String amount = col(schema, source[0], "mablagh", "mab", "all", "total", "price", "Price", "cost", "amount", "value");
            if (amount == null) continue;
            String date = col(schema, source[0], "date", "tarikh", "done_date", "donedate", "DoneDate");
            String description = col(schema, source[0], "description", "dis", "desc", "sharh", "tozihat", "Description", "CostName");
            String voucher = col(schema, source[0], "ghno", "shomare", "RowId", "ID", "id");
            List<String> conds = new ArrayList<>();
            if (date != null) conds.add(dateCondition(dateKey(date10("h", date)), window, params));
            if (!search.isEmpty()) {
                conds.add("(TRY_CONVERT(nvarchar(400)," + (description == null ? lit(source[1]) : txt("h", description, 400))
                        + ") LIKE ? ESCAPE '\\')");
                params.add("%" + escapeLike(search) + "%");
            }
            legs.add("SELECT " + (date == null ? "CAST(N'' AS nvarchar(30))" : date10("h", date))
                    + ", " + lit(source[1])
                    + ", ISNULL(NULLIF(" + (description == null ? "CAST(N'' AS nvarchar(400))" : txt("h", description, 400)) + ",N'')," + lit(source[1]) + ")"
                    + ", " + (voucher == null ? "CAST(N'' AS nvarchar(80))" : txt("h", voucher, 80))
                    + ", " + num("h", amount)
                    + " FROM " + tableRef + " h" + where(conds));
        }
        if (legs.isEmpty())
            throw new SectionUnavailable("هیچ جدول هزینه‌ای با ساختار شناخته‌شده در پایگاه داده پیدا نشد.");
        String sql = "SELECT " + top(spec.maxRows()) + " * FROM (" + join(legs, " UNION ALL ")
                + ") c ORDER BY 1 DESC, 5 DESC";
        return new PreparedQuery(sql, params);
    }

    // =====================================================================================
    // تولید و شمارش — produce.Production, produce.Formulation*, produce.ProductionCost
    // =====================================================================================

    private static PreparedQuery production(String variant, VariantSpec spec, SectionFilter f,
                                            SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "documents": return productionDocuments(spec, f, schema, today);
            case "formulation": return productionFormulation(spec, f, schema, today);
            case "costs": return productionCosts(spec, f, schema, today);
            case "count_diff": return productionCountDifference(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    private static PreparedQuery productionDocuments(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                     String today) throws SectionUnavailable {
        String production = needTable(schema, "produce.Production");
        String voucher = need(schema, "produce.Production", "شناسهٔ سند تولید", "ID", "id", "Rdf_");
        String date = col(schema, "produce.Production", "dateProduction", "DoneDate", "done_date", "date");
        String productCode = col(schema, "produce.Production", "shka", "SHKA");
        String quantity = col(schema, "produce.Production", "TedadVahed", "tedvah", "TedBastebandi");
        String description = col(schema, "produce.Production", "Description", "description", "desc");
        String active = col(schema, "produce.Production", "Active", "active");
        String costs = optionalTable(schema, "produce.ProductionCost");
        String costLink = costs == null ? null : col(schema, "produce.ProductionCost", "ProductionID", "productionId");
        String costPrice = costs == null ? null : col(schema, "produce.ProductionCost", "Price", "price", "mablagh");
        String costActive = costs == null ? null : col(schema, "produce.ProductionCost", "Active", "active");
        String costApply = costs != null && costLink != null && costPrice != null
                ? " OUTER APPLY (SELECT ISNULL(SUM(" + num("pc", costPrice) + "),0) AS total FROM " + costs
                        + " pc WHERE TRY_CONVERT(bigint," + ref("pc", costLink) + ")=TRY_CONVERT(bigint,"
                        + ref("p", voucher) + ")"
                        + (costActive == null ? "" : " AND " + boolTrue(ref("pc", costActive))) + ") cst "
                : " OUTER APPLY (SELECT CAST(0 AS decimal(19,2)) AS total) cst ";
        String inventory = optionalTable(schema, "inventory");
        String inventoryCode = inventory == null ? null : col(schema, "inventory", "shka", "SHKA");
        String inventoryName = inventory == null ? null : col(schema, "inventory", "naka", "NAKA");
        String inventoryJoin = inventory != null && inventoryCode != null && productCode != null
                ? " LEFT JOIN " + inventory + " i ON TRY_CONVERT(nvarchar(100)," + ref("i", inventoryCode)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("p", productCode) + ") " : " ";
        String product = productCode == null ? "CAST(N'—' AS nvarchar(250))"
                : inventoryName != null && !inventoryJoin.trim().isEmpty()
                        ? "COALESCE(NULLIF(" + txt("i", inventoryName, 250) + ",N'')," + txt("p", productCode, 100) + ",N'—')"
                        : "COALESCE(NULLIF(" + txt("p", productCode, 100) + ",N''),N'—')";
        String qty = quantity == null ? "CAST(0 AS decimal(19,3))" : num3("p", quantity);
        String cost = "ISNULL(cst.total,0)";
        String unitCost = "CASE WHEN " + qty + "<>0 THEN ROUND(" + cost + "/" + qty + ",2) ELSE CAST(0 AS decimal(19,2)) END";

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (active != null) conds.add(boolTrue(ref("p", active)));
        if (date != null)
            conds.add(dateCondition(dateKey(date10("p", date)), SectionMath.window(spec, f, today), params));
        addSearch(conds, params, f.query(), txt("p", voucher, 80), product,
                description == null ? null : txt("p", description, 400));
        String sql = "SELECT " + top(spec.maxRows()) + txt("p", voucher, 80)
                + ", " + (date == null ? "CAST(N'' AS nvarchar(30))" : date10("p", date))
                + ", " + product + ", " + qty + ", " + cost + ", " + unitCost
                + ", ISNULL(" + (description == null ? "CAST(N'' AS nvarchar(400))" : txt("p", description, 400)) + ",N'')"
                + " FROM " + production + " p" + inventoryJoin + costApply + where(conds)
                + (date == null ? "" : " ORDER BY " + dateKey(date10("p", date)) + " DESC");
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery productionFormulation(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                       String today) throws SectionUnavailable {
        String formulation = needTable(schema, "produce.Formulation");
        String items = needTable(schema, "produce.FormulationItems");
        String formulationId = need(schema, "produce.Formulation", "شناسهٔ فرمول", "ID", "id");
        String itemLink = need(schema, "produce.FormulationItems", "پیوند فرمول", "FormulID", "formulId");
        String itemCode = need(schema, "produce.FormulationItems", "کد مادهٔ اولیه", "shka", "SHKA");
        String itemQuantity = col(schema, "produce.FormulationItems", "TedadVahed", "tedvah");
        String itemLoose = col(schema, "produce.FormulationItems", "TedadJoz", "tedjoz");
        String productName = col(schema, "produce.Formulation", "Name", "name");
        String productCode = col(schema, "produce.Formulation", "shka", "SHKA");
        String formulationActive = col(schema, "produce.Formulation", "Active", "active");
        String itemActive = col(schema, "produce.FormulationItems", "Active", "active");
        String inventory = optionalTable(schema, "inventory");
        String inventoryCode = inventory == null ? null : col(schema, "inventory", "shka", "SHKA");
        String inventoryName = inventory == null ? null : col(schema, "inventory", "naka", "NAKA");
        String inventoryUnit = inventory == null ? null : col(schema, "inventory", "vahsanj", "unit");
        String inventoryCost = inventory == null ? null : col(schema, "inventory",
                "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String productJoin = inventory != null && inventoryCode != null && productCode != null
                ? " LEFT JOIN " + inventory + " ip ON TRY_CONVERT(nvarchar(100)," + ref("ip", inventoryCode)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("f", productCode) + ") " : " ";
        String componentJoin = inventory != null && inventoryCode != null
                ? " LEFT JOIN " + inventory + " ic ON TRY_CONVERT(nvarchar(100)," + ref("ic", inventoryCode)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("fi", itemCode) + ") " : " ";
        String product = productName != null
                ? "COALESCE(NULLIF(" + txt("f", productName, 250) + ",N''),"
                        + (inventoryName == null || productJoin.trim().isEmpty() ? "N'—'"
                                : "NULLIF(" + txt("ip", inventoryName, 250) + ",N'')") + ",N'—')"
                : (inventoryName == null || productJoin.trim().isEmpty() ? "CAST(N'—' AS nvarchar(250))"
                        : "COALESCE(NULLIF(" + txt("ip", inventoryName, 250) + ",N''),N'—')");
        String component = inventoryName == null || componentJoin.trim().isEmpty()
                ? "COALESCE(" + txt("fi", itemCode, 120) + ",N'—')"
                : "COALESCE(NULLIF(" + txt("ic", inventoryName, 250) + ",N'')," + txt("fi", itemCode, 120) + ",N'—')";
        String qty = itemQuantity == null ? "CAST(0 AS decimal(19,3))"
                : "(" + num3("fi", itemQuantity) + (itemLoose == null ? "" : "+" + num3("fi", itemLoose)) + ")";
        String unit = inventoryUnit == null || componentJoin.trim().isEmpty()
                ? "CAST(N'—' AS nvarchar(60))" : "COALESCE(NULLIF(" + txt("ic", inventoryUnit, 60) + ",N''),N'—')";
        String cost = inventoryCost == null || componentJoin.trim().isEmpty()
                ? "CAST(0 AS decimal(19,2))" : "COALESCE(TRY_CONVERT(decimal(19,2)," + ref("ic", inventoryCost) + "),0)";
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (formulationActive != null) conds.add(boolTrue(ref("f", formulationActive)));
        if (itemActive != null) conds.add(boolTrue(ref("fi", itemActive)));
        addSearch(conds, params, f.query(), product, component);
        String sql = "SELECT " + top(spec.maxRows()) + product + ", " + component + ", " + qty + ", " + unit
                + ", " + cost + ", (" + qty + "*" + cost + ")"
                + " FROM " + formulation + " f JOIN " + items + " fi ON TRY_CONVERT(bigint," + ref("fi", itemLink)
                + ")=TRY_CONVERT(bigint," + ref("f", formulationId) + ")" + productJoin + componentJoin
                + where(conds) + " ORDER BY 1, 2";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery productionCosts(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                 String today) throws SectionUnavailable {
        String costs = needTable(schema, "produce.ProductionCost");
        String production = needTable(schema, "produce.Production");
        String costLink = need(schema, "produce.ProductionCost", "پیوند سند تولید", "ProductionID", "productionId");
        String costId = col(schema, "produce.ProductionCost", "CostID", "costId");
        String price = need(schema, "produce.ProductionCost", "مبلغ هزینه", "Price", "price", "mablagh");
        String costActive = col(schema, "produce.ProductionCost", "Active", "active");
        String voucher = need(schema, "produce.Production", "شناسهٔ سند تولید", "ID", "id", "Rdf_");
        String date = col(schema, "produce.Production", "dateProduction", "DoneDate", "done_date", "date");
        String description = col(schema, "produce.Production", "Description", "description");
        String titles = optionalTable(schema, "produce.IndirectCost");
        String titleKey = titles == null ? null : col(schema, "produce.IndirectCost", "RowId", "CostID", "id");
        String titleName = titles == null ? null : col(schema, "produce.IndirectCost", "CostName", "name", "title");
        String titleJoin = titles != null && titleKey != null && titleName != null && costId != null
                ? " LEFT JOIN " + titles + " ic ON TRY_CONVERT(nvarchar(60)," + ref("ic", titleKey)
                        + ")=TRY_CONVERT(nvarchar(60)," + ref("pc", costId) + ") " : " ";
        String title = titleJoin.trim().isEmpty()
                ? (costId == null ? "CAST(N'هزینهٔ تولید' AS nvarchar(200))"
                        : "COALESCE(N'سرفصل '+NULLIF(" + txt("pc", costId, 60) + ",N''),N'هزینهٔ تولید')")
                : "COALESCE(NULLIF(" + txt("ic", titleName, 200) + ",N''),N'هزینهٔ تولید')";
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (costActive != null) conds.add(boolTrue(ref("pc", costActive)));
        if (date != null)
            conds.add(dateCondition(dateKey(date10("p", date)), SectionMath.window(spec, f, today), params));
        addSearch(conds, params, f.query(), title, txt("p", voucher, 80),
                description == null ? null : txt("p", description, 400));
        String sql = "SELECT " + top(spec.maxRows()) + txt("p", voucher, 80)
                + ", " + (date == null ? "CAST(N'' AS nvarchar(30))" : date10("p", date))
                + ", " + title + ", " + num("pc", price)
                + ", ISNULL(" + (description == null ? "CAST(N'' AS nvarchar(400))" : txt("p", description, 400)) + ",N'')"
                + " FROM " + costs + " pc JOIN " + production + " p ON TRY_CONVERT(bigint," + ref("pc", costLink)
                + ")=TRY_CONVERT(bigint," + ref("p", voucher) + ")"
                + titleJoin + where(conds)
                + (date == null ? "" : " ORDER BY " + dateKey(date10("p", date)) + " DESC");
        return new PreparedQuery(sql, params);
    }

    /** Difference between Atiran's system stock and the per-warehouse stock table. */
    private static PreparedQuery productionCountDifference(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                           String today) throws SectionUnavailable {
        String inventory = needTable(schema, "inventory");
        String warehouseStock = needTable(schema, "inventory_anbars");
        String code = need(schema, "inventory", "کد کالا", "shka", "SHKA");
        String name = need(schema, "inventory", "نام کالا", "naka", "NAKA");
        String warehouseCode = col(schema, "inventory_anbars", "shka", "SHKA", "kala");
        if (warehouseCode == null)
            throw new SectionUnavailable("ستون کد کالا در «موجودی کالا در انبار» پیدا نشد.");
        String warehousePacks = col(schema, "inventory_anbars", "mojkavah", "mojodi", "tedad", "qty", "meghdar");
        String warehouseLoose = col(schema, "inventory_anbars", "mojkajoz", "MojKajoz");
        if (warehousePacks == null)
            throw new SectionUnavailable("ستون موجودی در «موجودی کالا در انبار» پیدا نشد.");
        String warehouseColumn = col(schema, "inventory_anbars", "rdf_anbars", "rdf_anbar", "anbar", "anbar_rdf");
        String perPack = col(schema, "inventory", "mohvah");
        String systemPieces = stockPieces(schema, "i");
        String warehousePieces = "(TRY_CONVERT(decimal(19,3)," + ref("ia", warehousePacks) + ")"
                + (perPack == null ? "" : "*COALESCE(NULLIF(TRY_CONVERT(decimal(19,3)," + ref("i", perPack) + "),0),1)")
                + (warehouseLoose == null ? "" : "+COALESCE(TRY_CONVERT(decimal(19,3)," + ref("ia", warehouseLoose) + "),0)") + ")";
        String cost = stockCost(schema, "i");
        String warehouses = optionalTable(schema, "anbars");
        String warehouseKey = warehouses == null ? null : col(schema, "anbars", "rdf_anbar");
        String warehouseName = warehouses == null ? null : col(schema, "anbars", "name");
        String warehouseJoin = warehouses != null && warehouseKey != null && warehouseColumn != null
                ? " LEFT JOIN " + warehouses + " w ON TRY_CONVERT(nvarchar(60)," + ref("w", warehouseKey)
                        + ")=TRY_CONVERT(nvarchar(60)," + ref("ia", warehouseColumn) + ") " : " ";
        String warehouse = warehouseColumn == null ? "CAST(N'—' AS nvarchar(120))"
                : warehouseJoin.trim().isEmpty()
                        ? "COALESCE(NULLIF(" + txt("ia", warehouseColumn, 60) + ",N''),N'—')"
                        : "COALESCE(NULLIF(" + txt("w", warehouseName, 120) + ",N''),"
                                + txt("ia", warehouseColumn, 60) + ",N'—')";
        String difference = "(" + warehousePieces + "-" + systemPieces + ")";
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "inventory", "i", conds);
        conds.add("ABS(" + difference + ")>0.001");
        addSearch(conds, params, f.query(), txt("i", code, 100), txt("i", name, 250));
        String sql = "SELECT " + top(spec.maxRows()) + txt("i", code, 100) + ", " + txt("i", name, 250)
                + ", " + warehouse + ", " + systemPieces + ", " + warehousePieces
                + ", ABS(" + difference + ")*" + cost
                + " FROM " + inventory + " i JOIN " + warehouseStock + " ia ON TRY_CONVERT(nvarchar(100),"
                + ref("ia", warehouseCode) + ")=TRY_CONVERT(nvarchar(100)," + ref("i", code) + ")"
                + warehouseJoin + where(conds)
                + " ORDER BY ABS(" + difference + ")*" + cost + " DESC";
        return new PreparedQuery(sql, params);
    }

    // =====================================================================================
    // پرسنل و مأموریت‌ها — dbo.visitors, dbo.vis_goals, dbo.masir, dbo.sailfact
    // =====================================================================================

    private static PreparedQuery personnel(String variant, VariantSpec spec, SectionFilter f,
                                           SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "visitors": return personnelVisitors(spec, f, schema, today);
            case "daily": return personnelDaily(spec, f, schema, today);
            case "customers": return personnelCustomers(spec, f, schema, today);
            case "routes": return personnelRoutes(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    private static PreparedQuery personnelVisitors(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                   String today) throws SectionUnavailable {
        String visitors = needTable(schema, "visitors");
        String visitorId = need(schema, "visitors", "کد ویزیتور", "vis_rdf", "rdf");
        String visitorName = col(schema, "visitors", "vis_name", "name");
        String visitorPhone = col(schema, "visitors", "vis_cell", "vis_tell1", "cell", "phone");
        String visitorActive = col(schema, "visitors", "active", "Active");
        Fragment salesApply = visitorSalesApply(schema, visitorId, spec, f, today);
        String goals = optionalTable(schema, "vis_goals");
        String goalVisitor = goals == null ? null : col(schema, "vis_goals", "vis_rdf");
        String goalAmount = goals == null ? null : col(schema, "vis_goals", "mab", "mablagh");
        String goalApply = goals != null && goalVisitor != null && goalAmount != null
                ? " OUTER APPLY (SELECT ISNULL(SUM(" + num("g", goalAmount) + "),0) AS goal FROM " + goals
                        + " g WHERE TRY_CONVERT(nvarchar(50)," + ref("g", goalVisitor) + ")=TRY_CONVERT(nvarchar(50),"
                        + ref("t", visitorId) + ")) gl "
                : " OUTER APPLY (SELECT CAST(0 AS decimal(19,2)) AS goal) gl ";
        String label = visitorName == null ? txt("t", visitorId, 60)
                : "COALESCE(NULLIF(" + txt("t", visitorName, 150) + ",N'')," + txt("t", visitorId, 60) + ")";
        List<Object> params = new ArrayList<>(salesApply.params());
        List<String> conds = new ArrayList<>();
        addSearch(conds, params, f.query(), label, txt("t", visitorId, 60),
                visitorPhone == null ? null : txt("t", visitorPhone, 100));
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> select.add(txt("t", visitorId, 60));
                case "name" -> select.add(label);
                case "phone" -> select.add(visitorPhone == null ? "CAST(N'' AS nvarchar(100))" : txt("t", visitorPhone, 100));
                case "active" -> select.add(visitorActive == null ? "CAST(N'1' AS nvarchar(10))" : txt("t", visitorActive, 10));
                case "docs" -> select.add("ISNULL(sf.docs,0)");
                case "sales" -> select.add("ISNULL(sf.total,0)");
                case "collected" -> select.add("ISNULL(sf.collected,0)");
                case "customers" -> select.add("ISNULL(sf.customers,0)");
                case "goal" -> select.add("ISNULL(gl.goal,0)");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش پرسنل شناخته نشد.");
            }
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + visitors + " t "
                + salesApply.sql() + goalApply + where(conds) + " ORDER BY ISNULL(sf.total,0) DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery personnelDaily(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                String today) throws SectionUnavailable {
        String sales = needTable(schema, "sailfact");
        String date = need(schema, "sailfact", "تاریخ فاکتور", "date", "t_date", "tarikh");
        String amount = need(schema, "sailfact", "مبلغ فاکتور", "all_fel", "all", "amount", "total", "mablagh");
        String visitor = col(schema, "sailfact", "vis_rdf", "VisitorID", "visid");
        String customer = col(schema, "sailfact", "shmo", "SHMO", "CustomerCode");
        String collected = col(schema, "sailfact", "MabDaryaftFactor", "Daryaft");
        if (visitor == null)
            throw new SectionUnavailable("ستون ویزیتور در «فاکتور فروش» پیدا نشد؛ روند روزانه قابل تفکیک نیست.");
        String visitors = optionalTable(schema, "visitors");
        String visitorId = visitors == null ? null : col(schema, "visitors", "vis_rdf", "rdf");
        String visitorName = visitors == null ? null : col(schema, "visitors", "vis_name", "name");
        String visitorJoin = visitors != null && visitorId != null
                ? " LEFT JOIN " + visitors + " v ON TRY_CONVERT(nvarchar(50)," + ref("v", visitorId)
                        + ")=TRY_CONVERT(nvarchar(50)," + ref("h", visitor) + ") " : " ";
        String label = visitorName != null && !visitorJoin.trim().isEmpty()
                ? "COALESCE(NULLIF(" + txt("v", visitorName, 150) + ",N''),"
                        + txt("h", visitor, 60) + ",N'—')"
                : "COALESCE(NULLIF(" + txt("h", visitor, 60) + ",N''),N'—')";

        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addActive(schema, "sailfact", "x", conds);
        addSoft(schema, "sailfact", "x", conds);
        conds.add(dateCondition(dateKey(date10("x", date)), SectionMath.window(spec, f, today), params));
        String source = dedupe(sales, "x", conds);
        String day = dateKey(date10("h", date));

        List<String> outer = new ArrayList<>();
        outer.add(day + " IS NOT NULL");
        if (!f.context().trim().isEmpty()) {
            String pattern = "%" + escapeLike(f.context()) + "%";
            outer.add("(TRY_CONVERT(nvarchar(200)," + label + ") LIKE ? ESCAPE '\'"
                    + " OR TRY_CONVERT(nvarchar(60)," + txt("h", visitor, 60) + ") LIKE ? ESCAPE '\')");
            params.add(pattern);
            params.add(pattern);
        }
        String sql = "SELECT " + top(spec.maxRows()) + day + ", " + label
                + ", COUNT_BIG(1), ISNULL(SUM(" + num("h", amount) + "),0)"
                + ", ISNULL(SUM(" + (collected == null ? "CAST(0 AS decimal(19,2))" : num("h", collected)) + "),0)"
                + ", " + (customer == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT " + txt("h", customer, 100) + ")")
                + " FROM " + source + visitorJoin
                + " WHERE " + join(outer, " AND ")
                + " GROUP BY " + day + ", " + label + " ORDER BY " + day + " DESC, 4 DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery personnelCustomers(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                    String today) throws SectionUnavailable {
        String customers = needTable(schema, "CUSTOMERS");
        String code = need(schema, "CUSTOMERS", "کد مشتری", "SHMO", "shmo", "CustomerCode");
        String name = col(schema, "CUSTOMERS", "MONAME", "Name", "CusName", "CustomerName");
        String visitor = col(schema, "CUSTOMERS", "vis_rdf", "VisitorID", "visid");
        Fragment salesApply = customerSalesApply(schema, "c", code, today, spec, f);
        Balance balance = customerBalance(schema, "c", code);
        String visitors = optionalTable(schema, "visitors");
        String visitorId = visitors == null ? null : col(schema, "visitors", "vis_rdf", "rdf");
        String visitorName = visitors == null ? null : col(schema, "visitors", "vis_name", "name");
        String visitorJoin = visitors != null && visitorId != null && visitor != null
                ? " LEFT JOIN " + visitors + " v ON TRY_CONVERT(nvarchar(50)," + ref("v", visitorId)
                        + ")=TRY_CONVERT(nvarchar(50)," + ref("c", visitor) + ") " : " ";
        String visitorLabel = visitor == null ? "CAST(N'—' AS nvarchar(150))"
                : visitorName != null && !visitorJoin.trim().isEmpty()
                        ? "COALESCE(NULLIF(" + txt("v", visitorName, 150) + ",N'')," + txt("c", visitor, 60) + ",N'—')"
                        : "COALESCE(NULLIF(" + txt("c", visitor, 60) + ",N''),N'—')";
        List<Object> params = new ArrayList<>(balance.params());
        params.addAll(salesApply.params());
        List<String> conds = new ArrayList<>();
        if (!f.context().trim().isEmpty()) {
            conds.add("(TRY_CONVERT(nvarchar(150)," + visitorLabel + ") LIKE ? ESCAPE '\\'"
                    + " OR TRY_CONVERT(nvarchar(60)," + (visitor == null ? "CAST(N'' AS nvarchar(60))" : txt("c", visitor, 60))
                    + ") LIKE ? ESCAPE '\\')");
            String pattern = "%" + escapeLike(f.context()) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        addSearch(conds, params, f.query(), txt("c", code, 100), name == null ? null : txt("c", name, 250));
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> select.add(txt("c", code, 100));
                case "name" -> select.add(name == null ? txt("c", code, 100)
                        : "COALESCE(NULLIF(" + txt("c", name, 250) + ",N'')," + txt("c", code, 100) + ")");
                case "visitor" -> select.add(visitorLabel);
                case "docs" -> select.add("ISNULL(sx.docs,0)");
                case "sales" -> select.add("ISNULL(sx.gross,0)");
                case "balance" -> select.add(balance.expression());
                case "last" -> select.add("ISNULL(sx.last_day,N'')");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش مشتریان ویزیتور شناخته نشد.");
            }
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + customers + " c "
                + balance.apply() + salesApply.sql() + visitorJoin + where(conds)
                + " ORDER BY ISNULL(sx.gross,0) DESC";
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery personnelRoutes(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                                 String today) throws SectionUnavailable {
        String routes = needTable(schema, "masir");
        String routeId = need(schema, "masir", "کد مسیر", "rdf_masir", "RDF_masir", "id");
        String routeName = col(schema, "masir", "name", "title");
        String customers = optionalTable(schema, "CUSTOMERS");
        String customerRoute = customers == null ? null : col(schema, "CUSTOMERS", "RDF_masir", "masir_rdf", "route_id", "RouteID");
        String customerCode = customers == null ? null : col(schema, "CUSTOMERS", "SHMO", "shmo", "CustomerCode");
        String customerActive = customers == null ? null : col(schema, "CUSTOMERS", "active", "is_active");
        String regionColumn = col(schema, "masir", "region_rdf", "regions_rdf", "region");
        String regions = optionalTable(schema, "regions");
        String regionKey = regions == null ? null : col(schema, "regions", "rdf", "id", "region_rdf");
        String regionName = regions == null ? null : col(schema, "regions", "name", "title");
        String regionJoin = regions != null && regionKey != null && regionName != null && regionColumn != null
                ? " LEFT JOIN " + regions + " rg ON TRY_CONVERT(nvarchar(60)," + ref("rg", regionKey)
                        + ")=TRY_CONVERT(nvarchar(60)," + ref("r", regionColumn) + ") " : " ";
        String region = regionJoin.trim().isEmpty() ? "CAST(N'—' AS nvarchar(150))"
                : "COALESCE(NULLIF(" + txt("rg", regionName, 150) + ",N''),N'—')";
        String route = routeName == null ? txt("r", routeId, 60)
                : "COALESCE(NULLIF(" + txt("r", routeName, 150) + ",N'')," + txt("r", routeId, 60) + ",N'—')";
        String customerApply = customers == null || customerRoute == null
                ? " OUTER APPLY (SELECT CAST(0 AS bigint) AS customers, CAST(0 AS bigint) AS active) cc "
                : " OUTER APPLY (SELECT COUNT_BIG(1) AS customers, SUM(CASE WHEN "
                        + (customerActive == null ? "1=1" : boolTrue(ref("cx", customerActive)))
                        + " THEN 1 ELSE 0 END) AS active FROM " + customers + " cx WHERE TRY_CONVERT(nvarchar(60),"
                        + ref("cx", customerRoute) + ")=TRY_CONVERT(nvarchar(60)," + ref("r", routeId) + ")) cc ";
        Fragment salesApply = customers == null || customerRoute == null || customerCode == null
                ? new Fragment(" OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(0 AS decimal(19,2)) AS gross) sx ", List.of())
                : routeSalesApply(schema, customers, customerRoute, customerCode, routeId, spec, f, today);
        List<Object> params = new ArrayList<>(salesApply.params());
        List<String> conds = new ArrayList<>();
        addSearch(conds, params, f.query(), route, region);
        String sql = "SELECT " + top(spec.maxRows()) + route + ", " + region
                + ", ISNULL(cc.customers,0), ISNULL(cc.active,0), ISNULL(sx.docs,0), ISNULL(sx.gross,0)"
                + " FROM " + routes + " r" + regionJoin + customerApply + salesApply.sql() + where(conds)
                + " ORDER BY ISNULL(sx.gross,0) DESC";
        return new PreparedQuery(sql, params);
    }

    private static Fragment routeSalesApply(SchemaReader schema, String customers, String customerRoute,
                                            String customerCode, String routeId, VariantSpec spec,
                                            SectionFilter f, String today) {
        String sales = optionalTable(schema, "sailfact");
        if (sales == null) return new Fragment(" OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(0 AS decimal(19,2)) AS gross) sx ", List.of());
        String salesCustomer = col(schema, "sailfact", "shmo", "SHMO", "CustomerCode");
        String amount = col(schema, "sailfact", "all_fel", "all", "amount", "total", "mablagh");
        String date = col(schema, "sailfact", "date", "t_date", "tarikh");
        if (salesCustomer == null || amount == null)
            return new Fragment(" OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(0 AS decimal(19,2)) AS gross) sx ", List.of());
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(100)," + ref("s", salesCustomer) + ")=TRY_CONVERT(nvarchar(100),"
                + ref("cx", customerCode) + ")");
        if (date != null)
            conds.add(dateCondition(dateKey(date10("s", date)), SectionMath.window(spec, f, today), params));
        String active = activeCondition(schema, "sailfact", "s");
        return new Fragment(" OUTER APPLY (SELECT COUNT_BIG(1) AS docs, ISNULL(SUM(" + num("s", amount)
                + "),0) AS gross FROM " + sales + " s JOIN " + customers + " cx ON TRY_CONVERT(nvarchar(60),"
                + ref("cx", customerRoute) + ")=TRY_CONVERT(nvarchar(60)," + ref("r", routeId) + ")"
                + " WHERE " + join(conds, " AND ") + active + ") sx ", params);
    }

    /** Visitor performance over the selected window: docs, turnover, collected and distinct customers. */
    private static Fragment visitorSalesApply(SchemaReader schema, String visitorId, VariantSpec spec,
                                              SectionFilter f, String today) throws SectionUnavailable {
        String sales = optionalTable(schema, "sailfact");
        String visitor = sales == null ? null : col(schema, "sailfact", "vis_rdf", "VisitorID", "visid");
        String amount = sales == null ? null : col(schema, "sailfact", "all_fel", "all", "amount", "total", "mablagh");
        if (sales == null || visitor == null || amount == null)
            return new Fragment(" OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(0 AS decimal(19,2)) AS total,"
                    + " CAST(0 AS decimal(19,2)) AS collected, CAST(0 AS bigint) AS customers) sf ", List.of());
        String date = col(schema, "sailfact", "date", "t_date", "tarikh");
        String collected = col(schema, "sailfact", "MabDaryaftFactor", "Daryaft");
        String customer = col(schema, "sailfact", "shmo", "SHMO", "CustomerCode");
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(50)," + ref("s", visitor) + ")=TRY_CONVERT(nvarchar(50),"
                + ref("t", visitorId) + ")");
        if (date != null)
            conds.add(dateCondition(dateKey(date10("s", date)), SectionMath.window(spec, f, today), params));
        String active = activeCondition(schema, "sailfact", "s");
        String sql = " OUTER APPLY (SELECT COUNT_BIG(1) AS docs, ISNULL(SUM(" + num("s", amount) + "),0) AS total"
                + ", ISNULL(SUM(" + (collected == null ? "CAST(0 AS decimal(19,2))" : num("s", collected)) + "),0) AS collected"
                + ", " + (customer == null ? "CAST(0 AS bigint)" : "COUNT(DISTINCT " + txt("s", customer, 100) + ")")
                + " AS customers FROM " + sales + " s WHERE " + join(conds, " AND ") + active + ") sf ";
        return new Fragment(sql, params);
    }

    // =====================================================================================
    // کاربران و دسترسی — dbo.sys_users, dbo.Roles, dbo.LoginDetails, dbo.Log
    // =====================================================================================

    private static PreparedQuery users(String variant, VariantSpec spec, SectionFilter f,
                                       SchemaReader schema, String today) throws SectionUnavailable {
        switch (variant) {
            case "list": return usersList(spec, f, schema, today);
            case "roles": return usersRoles(spec, f, schema, today);
            case "logins": return usersLogins(spec, f, schema, today);
            default: throw unknownReport(spec);
        }
    }

    private static PreparedQuery usersList(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                           String today) throws SectionUnavailable {
        String users = needTable(schema, "sys_users");
        String id = need(schema, "sys_users", "کد کاربر", "user_id", "UserID", "userid");
        String userName = col(schema, "sys_users", "user_name", "Username", "username");
        String firstName = col(schema, "sys_users", "user_fname");
        String lastName = col(schema, "sys_users", "user_lname");
        String roleColumn = col(schema, "sys_users", "role_id", "RoleID");
        String active = col(schema, "sys_users", "active", "Active");
        String phone = col(schema, "sys_users", "phone", "cell", "mobile");
        String fullName = userNameExpr(userName, firstName, lastName, id);
        String role = roleNameExpr(schema, "u", roleColumn);
        String logins = optionalTable(schema, "LoginDetails");
        String loginUser = logins == null ? null : col(schema, "LoginDetails", "user_id", "userid", "username", "user_name");
        String loginDate = logins == null ? null : col(schema, "LoginDetails",
                "DateServer", "DateClient", "login_date", "logindate", "date", "event_time", "time");
        String loginApply = logins != null && loginUser != null && loginDate != null
                ? " OUTER APPLY (SELECT MAX(" + txt("lg", loginDate, 40) + ") AS last_login FROM " + logins
                        + " lg WHERE TRY_CONVERT(nvarchar(100)," + ref("lg", loginUser) + ") IN (TRY_CONVERT(nvarchar(100),"
                        + ref("u", id) + ")" + (userName == null ? "" : ",TRY_CONVERT(nvarchar(100)," + ref("u", userName) + ")")
                        + ")) ll "
                : " OUTER APPLY (SELECT CAST(N'' AS nvarchar(40)) AS last_login) ll ";
        String events = optionalTable(schema, "Log");
        String eventUser = events == null ? null : col(schema, "Log", "user_id", "userid", "username", "user_name");
        String eventDate = events == null ? null : col(schema, "Log", "date", "event_time", "time", "logdate");
        boolean countsToday = events != null && eventUser != null && eventDate != null && !today.isEmpty();
        String eventApply = countsToday
                ? " OUTER APPLY (SELECT COUNT_BIG(1) AS today_events FROM " + events + " lo WHERE "
                        + dateKey(date10("lo", eventDate)) + "=? AND TRY_CONVERT(nvarchar(100),"
                        + ref("lo", eventUser) + ") IN (TRY_CONVERT(nvarchar(100)," + ref("u", id) + ")"
                        + (userName == null ? "" : ",TRY_CONVERT(nvarchar(100)," + ref("u", userName) + ")") + ")) la "
                : " OUTER APPLY (SELECT CAST(0 AS bigint) AS today_events) la ";
        List<Object> params = new ArrayList<>();
        if (countsToday) params.add(today);
        List<String> conds = new ArrayList<>();
        addSearch(conds, params, f.query(), fullName, role, txt("u", id, 60));
        List<String> select = new ArrayList<>();
        for (String key : outputColumns(spec)) {
            switch (key) {
                case "code" -> select.add(txt("u", id, 60));
                case "name" -> select.add(fullName);
                case "role" -> select.add(role);
                case "active" -> select.add(active == null ? "CAST(N'1' AS nvarchar(10))" : txt("u", active, 10));
                case "phone" -> select.add(phone == null ? "CAST(N'' AS nvarchar(100))" : txt("u", phone, 100));
                case "last_login" -> select.add("ISNULL(ll.last_login,N'')");
                case "today" -> select.add("ISNULL(la.today_events,0)");
                default -> throw new SectionUnavailable("ستون «" + key + "» در گزارش کاربران شناخته نشد.");
            }
        }
        String sql = "SELECT " + top(spec.maxRows()) + join(select, ", ") + " FROM " + users + " u"
                + loginApply + eventApply + where(conds) + " ORDER BY " + fullName;
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery usersRoles(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                            String today) throws SectionUnavailable {
        String roleTable = optionalTable(schema, "Roles");
        if (roleTable == null) roleTable = optionalTable(schema, "role");
        if (roleTable == null) throw new SectionUnavailable("جدول «نقش‌ها» در پایگاه داده پیدا نشد.");
        String roleName = roleTable.contains("Roles")
                ? col(schema, "Roles", "name", "role_name", "title", "onvan", "RollName")
                : col(schema, "role", "name", "role_name", "title", "onvan", "RollName");
        String roleId = roleTable.contains("Roles")
                ? col(schema, "Roles", "role_id", "roleid", "id", "RollID")
                : col(schema, "role", "role_id", "roleid", "id", "RollID");
        if (roleName == null || roleId == null)
            throw new SectionUnavailable("ستون شناسه یا نام نقش در «نقش‌ها» پیدا نشد.");
        String users = optionalTable(schema, "sys_users");
        String userRole = users == null ? null : col(schema, "sys_users", "role_id", "RoleID");
        String userApply = users != null && userRole != null
                ? " OUTER APPLY (SELECT COUNT_BIG(1) AS users FROM " + users + " su WHERE TRY_CONVERT(nvarchar(60),"
                        + ref("su", userRole) + ")=TRY_CONVERT(nvarchar(60)," + ref("r", roleId) + ")) uc "
                : " OUTER APPLY (SELECT CAST(0 AS bigint) AS users) uc ";
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addSearch(conds, params, f.query(), txt("r", roleName, 150), txt("r", roleId, 60));
        String sql = "SELECT " + top(spec.maxRows()) + txt("r", roleId, 60) + ", " + txt("r", roleName, 150)
                + ", ISNULL(uc.users,0) FROM " + roleTable + " r" + userApply + where(conds)
                + " ORDER BY ISNULL(uc.users,0) DESC, " + txt("r", roleName, 150);
        return new PreparedQuery(sql, params);
    }

    private static PreparedQuery usersLogins(VariantSpec spec, SectionFilter f, SchemaReader schema,
                                             String today) throws SectionUnavailable {
        String logins = needTable(schema, "LoginDetails");
        String user = need(schema, "LoginDetails", "کاربر", "user_id", "userid", "username", "user_name");
        String date = col(schema, "LoginDetails", "DateServer", "DateClient", "login_date", "logindate",
                "date", "event_time", "time", "datetime");
        String ip = col(schema, "LoginDetails", "ComputerIP", "ip", "ipaddress", "host");
        String computer = col(schema, "LoginDetails", "ComputerName", "computer", "system", "host_name");
        String description = col(schema, "LoginDetails", "description", "desc", "detail", "sharh");
        String users = optionalTable(schema, "sys_users");
        String userId = users == null ? null : col(schema, "sys_users", "user_id", "UserID", "userid");
        String userName = users == null ? null : col(schema, "sys_users", "user_name", "Username", "username");
        String userJoin = users != null && userId != null
                ? " LEFT JOIN " + users + " su ON TRY_CONVERT(nvarchar(100)," + ref("su", userId)
                        + ")=TRY_CONVERT(nvarchar(100)," + ref("l", user) + ") " : " ";
        String label = users != null && userName != null && !userJoin.trim().isEmpty()
                ? "COALESCE(NULLIF(" + txt("su", userName, 120) + ",N'')," + txt("l", user, 100) + ",N'—')"
                : "COALESCE(NULLIF(" + txt("l", user, 100) + ",N''),N'—')";
        String at = date == null ? "CAST(N'' AS nvarchar(40))" : dateTimeText("l", date);
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        addSearch(conds, params, f.query(), label,
                computer == null ? null : txt("l", computer, 200),
                ip == null ? null : txt("l", ip, 120));
        String sql = "SELECT " + top(spec.maxRows()) + label + ", " + at
                + ", ISNULL(" + (computer == null ? "CAST(N'' AS nvarchar(200))" : txt("l", computer, 200)) + ",N'')"
                + ", ISNULL(" + (ip == null ? "CAST(N'' AS nvarchar(120))" : txt("l", ip, 120)) + ",N'')"
                + ", ISNULL(" + (description == null ? "CAST(N'' AS nvarchar(400))" : txt("l", description, 400)) + ",N'')"
                + " FROM " + logins + " l" + userJoin + where(conds)
                + (date == null ? "" : " ORDER BY " + dateTimeText("l", date) + " DESC");
        return new PreparedQuery(sql, params);
    }

    private static String userNameExpr(String userName, String firstName, String lastName, String id) {
        String first = firstName == null ? null : txt("u", firstName, 100);
        String last = lastName == null ? null : txt("u", lastName, 100);
        String full = first != null && last != null
                ? "NULLIF(LTRIM(RTRIM(ISNULL(" + first + ",N'')+N' '+ISNULL(" + last + ",N''))),N'')" : null;
        List<String> candidates = new ArrayList<>();
        if (full != null) candidates.add(full);
        if (last != null) candidates.add("NULLIF(" + last + ",N'')");
        if (userName != null) candidates.add("NULLIF(" + txt("u", userName, 120) + ",N'')");
        candidates.add(txt("u", id, 60));
        return "COALESCE(" + join(candidates, ",") + ",N'—')";
    }

    /** Role display name: the role table when it can be joined, else the raw role id. */
    private static String roleNameExpr(SchemaReader schema, String userAlias, String roleColumn) {
        if (roleColumn == null) return "CAST(N'' AS nvarchar(150))";
        String roleTable = optionalTable(schema, "Roles");
        String table = roleTable;
        String roleId = roleTable == null ? null : col(schema, "Roles", "role_id", "roleid", "id", "RollID");
        String roleName = roleTable == null ? null : col(schema, "Roles", "name", "role_name", "title", "onvan", "RollName");
        if (table == null) {
            table = optionalTable(schema, "role");
            roleId = table == null ? null : col(schema, "role", "role_id", "roleid", "id", "RollID");
            roleName = table == null ? null : col(schema, "role", "name", "role_name", "title", "onvan", "RollName");
        }
        if (table == null || roleId == null || roleName == null)
            return "ISNULL(" + txt(userAlias, roleColumn, 80) + ",N'')";
        return "ISNULL((SELECT TOP (1) " + txt("r", roleName, 150) + " FROM " + table + " r WHERE TRY_CONVERT(nvarchar(60),"
                + ref("r", roleId) + ")=TRY_CONVERT(nvarchar(60)," + ref(userAlias, roleColumn)
                + "))," + txt(userAlias, roleColumn, 80) + ",N'')";
    }

    // =====================================================================================
    // shared fragments: customer balance, customer sales, dedupe, conditions
    // =====================================================================================

    /** A SQL fragment plus the bind values it contributes, in placeholder order. */
    record Fragment(String sql, List<Object> params) {
        Fragment {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    /** Balance expression plus the OUTER APPLY that computes it from cust_act when readable. */
    record Balance(String apply, String expression, List<Object> params) {
        Balance {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    /**
     * Customer balance: Atiran's live account ledger (Σact_bed − Σact_bes) when cust_act is readable,
     * otherwise the stored CUSTOMERS.man column. Never a guess between the two.
     */
    private static Balance customerBalance(SchemaReader schema, String alias, String customerCode) {
        String ledger = optionalTable(schema, "cust_act");
        String ledgerCustomer = ledger == null ? null : col(schema, "cust_act", "shmo", "SHMO", "CustomerCode");
        String debit = ledger == null ? null : col(schema, "cust_act", "act_bed", "bed", "debit");
        String credit = ledger == null ? null : col(schema, "cust_act", "act_bes", "bes", "credit");
        if (ledger != null && ledgerCustomer != null && debit != null && credit != null) {
            String active = activeCondition(schema, "cust_act", "a");
            String apply = " OUTER APPLY (SELECT ISNULL(SUM(" + num("a", debit) + "),0)-ISNULL(SUM("
                    + num("a", credit) + "),0) AS balance FROM " + ledger + " a WHERE TRY_CONVERT(bigint,"
                    + ref("a", ledgerCustomer) + ")=TRY_CONVERT(bigint," + ref(alias, customerCode) + ")"
                    + active + ") ca ";
            return new Balance(apply, "ISNULL(ca.balance,0)", List.of());
        }
        String stored = col(schema, "CUSTOMERS", "man", "Balance", "Mandeh", "mande", "account_balance");
        return new Balance("", stored == null ? "CAST(0 AS decimal(19,2))" : num(alias, stored), List.of());
    }

    /** Sales aggregate per customer over the report window. */
    private static Fragment customerSalesApply(SchemaReader schema, String alias, String customerCode,
                                               String today, VariantSpec spec, SectionFilter f) {
        String empty = " OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(0 AS decimal(19,2)) AS gross,"
                + " CAST(0 AS decimal(19,2)) AS collected, CAST(N'' AS nvarchar(30)) AS last_day) sx ";
        String sales = optionalTable(schema, "sailfact");
        String salesCustomer = sales == null ? null : col(schema, "sailfact", "shmo", "SHMO", "CustomerCode");
        String amount = sales == null ? null : col(schema, "sailfact", "all_fel", "all", "amount", "total", "mablagh");
        if (sales == null || salesCustomer == null || amount == null) return new Fragment(empty, List.of());
        String date = col(schema, "sailfact", "date", "t_date", "tarikh");
        String collected = col(schema, "sailfact", "MabDaryaftFactor", "Daryaft");
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        conds.add("TRY_CONVERT(nvarchar(100)," + ref("s", salesCustomer) + ")=TRY_CONVERT(nvarchar(100),"
                + ref(alias, customerCode) + ")");
        if (date != null)
            conds.add(dateCondition(dateKey(date10("s", date)), SectionMath.window(spec, f, today), params));
        String active = activeCondition(schema, "sailfact", "s");
        String sql = " OUTER APPLY (SELECT COUNT_BIG(1) AS docs, ISNULL(SUM(" + num("s", amount) + "),0) AS gross"
                + ", ISNULL(SUM(" + (collected == null ? "CAST(0 AS decimal(19,2))" : num("s", collected)) + "),0) AS collected"
                + ", " + (date == null ? "CAST(N'' AS nvarchar(30))" : "ISNULL(MAX(" + dateKey(date10("s", date)) + "),N'')")
                + " AS last_day FROM " + sales + " s WHERE " + join(conds, " AND ") + active + ") sx ";
        return new Fragment(sql, params);
    }

    /** Activity aggregate per customer from cust_act (document count + last movement date). */
    private static String customerActivityApply(SchemaReader schema, String customerCode) {
        String ledger = optionalTable(schema, "cust_act");
        String ledgerCustomer = ledger == null ? null : col(schema, "cust_act", "shmo", "SHMO", "CustomerCode");
        String date = ledger == null ? null : col(schema, "cust_act", "date", "t_date", "tarikh");
        if (ledger == null || ledgerCustomer == null)
            return " OUTER APPLY (SELECT CAST(0 AS bigint) AS docs, CAST(N'' AS nvarchar(30)) AS last_day) ax ";
        String active = activeCondition(schema, "cust_act", "a");
        return " OUTER APPLY (SELECT COUNT_BIG(1) AS docs, "
                + (date == null ? "CAST(N'' AS nvarchar(30))" : "ISNULL(MAX(" + dateKey(date10("a", date)) + "),N'')")
                + " AS last_day FROM " + ledger + " a WHERE TRY_CONVERT(nvarchar(100)," + ref("a", ledgerCustomer)
                + ")=TRY_CONVERT(nvarchar(100),c.[" + customerCode.replace("]", "]]") + "])" + active + ") ax ";
    }

    /** De-duplicated header source; inner conditions must reference the x. alias. */
    private static String dedupe(String tableRef, String alias, List<String> conditions) {
        return "(SELECT DISTINCT x.* FROM " + tableRef + " x" + where(conditions) + ") " + alias;
    }

    private static void addActive(SchemaReader schema, String table, String alias, List<String> conditions) {
        String condition = activeCondition(schema, table, alias);
        if (!condition.isEmpty()) conditions.add(condition.substring(" AND ".length()));
    }

    private static String activeCondition(SchemaReader schema, String table, String alias) {
        String active = col(schema, table, "active", "Active", "isActive", "is_active");
        if (active == null) return "";
        String field = ref(alias, active);
        return " AND (" + boolTrue(field) + " OR " + field + " IS NULL)";
    }

    private static void addSoft(SchemaReader schema, String table, String alias, List<String> conditions) {
        String deleted = col(schema, table, "deleted", "is_deleted", "isdeleted");
        if (deleted != null) conditions.add(falseLike(ref(alias, deleted)));
        String cancelled = col(schema, table, "cancel", "canceled", "cancelled", "is_cancel", "void");
        if (cancelled != null) conditions.add(falseLike(ref(alias, cancelled)));
    }

    private static String boolTrue(String field) {
        String normalized = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + "))))";
        return "(" + normalized + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
    }

    private static String falseLike(String field) {
        String normalized = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(40)," + field + "))))";
        return "(" + normalized + " IS NULL OR " + normalized
                + " IN (N'',N'0',N'F',N'FALSE',N'N',N'NO') OR TRY_CONVERT(int," + field + ")=0)";
    }

    /** Inclusive Jalali range condition on a normalized date expression; empty for an open window. */
    private static String dateCondition(String dateExpression, SectionMath.DateWindow window, List<Object> params) {
        if (window == null || window.isEmpty()) return "1=1";
        if (!window.from().isEmpty() && !window.to().isEmpty()) {
            params.add(window.from());
            params.add(window.to());
            return "(" + dateExpression + ">=? AND " + dateExpression + "<=?)";
        }
        if (!window.from().isEmpty()) {
            params.add(window.from());
            return "(" + dateExpression + ">=?)";
        }
        params.add(window.to());
        return "(" + dateExpression + "<=?)";
    }

    private static void addSearch(List<String> conditions, List<Object> params, String term, String... expressions) {
        String search = term == null ? "" : term.trim();
        if (search.isEmpty()) return;
        List<String> terms = new ArrayList<>();
        String pattern = "%" + escapeLike(search) + "%";
        for (String expression : expressions) {
            if (expression == null || expression.trim().isEmpty()) continue;
            terms.add("TRY_CONVERT(nvarchar(300)," + expression + ") LIKE ? ESCAPE '\\'");
            params.add(pattern);
        }
        if (!terms.isEmpty()) conditions.add("(" + join(terms, " OR ") + ")");
    }

    static String escapeLike(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                .replace("[", "\\[").replace("]", "\\]");
    }

    // ------------------------------------------------------------------ SQL fragment helpers

    private static String q(String column) {
        return column == null ? "NULL" : "[" + column.replace("]", "]]") + "]";
    }

    private static String ref(String alias, String column) {
        if (column == null || column.trim().isEmpty()) return "NULL";
        return (alias == null || alias.trim().isEmpty() ? "" : alias + ".") + q(column);
    }

    private static String num(String alias, String column) {
        if (column == null) return "CAST(0 AS decimal(19,2))";
        return "ISNULL(TRY_CONVERT(decimal(19,2)," + ref(alias, column) + "),0)";
    }

    private static String num3(String alias, String column) {
        if (column == null) return "CAST(0 AS decimal(19,3))";
        return "ISNULL(TRY_CONVERT(decimal(19,3)," + ref(alias, column) + "),0)";
    }

    private static String txt(String alias, String column, int length) {
        if (column == null) return "CAST(N'' AS nvarchar(" + length + "))";
        return "ISNULL(TRY_CONVERT(nvarchar(" + length + ")," + ref(alias, column) + "),N'')";
    }

    private static String txtOr(String alias, String column, int length, String fallback) {
        if (column == null) return lit(fallback);
        return "ISNULL(NULLIF(" + txt(alias, column, length) + ",N'')," + lit(fallback) + ")";
    }

    private static String date10(String alias, String column) {
        if (column == null) return "CAST(N'' AS nvarchar(30))";
        return "LEFT(TRY_CONVERT(nvarchar(30)," + ref(alias, column) + "),10)";
    }

    /**
     * A stored timestamp as sortable ISO text (yyyy-MM-dd HH:mm). Text columns that already hold a
     * Jalali date survive unchanged, so an installation that keeps Jalali strings is not blanked out.
     */
    private static String dateTimeText(String alias, String column) {
        if (column == null) return "CAST(N'' AS nvarchar(40))";
        return "COALESCE(CONVERT(nvarchar(30),TRY_CONVERT(datetime2(0)," + ref(alias, column)
                + "),121)," + txt(alias, column, 40) + ")";
    }

    private static String month7(String alias, String column) {
        if (column == null) return "CAST(N'' AS nvarchar(7))";
        return "LEFT(TRY_CONVERT(nvarchar(30)," + ref(alias, column) + "),7)";
    }

    /**
     * Normalizes Atiran's date text to a sortable yyyy/MM/dd. Rows are stored zero-padded, but some
     * installations keep one-digit components or bare yyyymmdd, which would sort outside their month.
     */
    private static String dateKey(String dateExpression) {
        String d = "LTRIM(RTRIM(" + dateExpression + "))";
        return "(CASE"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'"
                + " THEN LEFT(" + d + ",4)+N'/'+SUBSTRING(" + d + ",5,2)+N'/'+RIGHT(" + d + ",2)"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9]/[0-9]'"
                + " THEN LEFT(" + d + ",5)+N'0'+SUBSTRING(" + d + ",6,1)+N'/0'+SUBSTRING(" + d + ",8,1)"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9]/[0-9][0-9]'"
                + " THEN LEFT(" + d + ",5)+N'0'+SUBSTRING(" + d + ",6,4)"
                + " WHEN " + d + " LIKE N'[0-9][0-9][0-9][0-9]/[0-9][0-9]/[0-9]'"
                + " THEN LEFT(" + d + ",8)+N'0'+SUBSTRING(" + d + ",9,1)"
                + " ELSE " + d + " END)";
    }

    private static String lit(String text) {
        return "N'" + (text == null ? "" : text.replace("'", "''")) + "'";
    }

    private static String top(int rows) {
        return "TOP (" + Math.max(1, rows) + ") ";
    }

    private static String join(List<String> parts, String separator) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) if (part != null && !part.trim().isEmpty()) kept.add(part);
        return String.join(separator, kept);
    }

    private static String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : " WHERE " + join(conditions, " AND ");
    }

    // ------------------------------------------------------------------ schema helpers

    private static String needTable(SchemaReader schema, String table) throws SectionUnavailable {
        String reference = schema.tableRef(table);
        if (reference == null)
            throw new SectionUnavailable("«" + title(table) + "» در این پایگاه داده پیدا نشد یا مجوز خواندن ندارد.");
        return reference;
    }

    private static String optionalTable(SchemaReader schema, String table) {
        return schema.tableRef(table);
    }

    private static String col(SchemaReader schema, String table, String... candidates) {
        List<String> columns = schema.columns(table);
        if (columns == null || columns.isEmpty()) return null;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            for (String column : columns) if (column != null && column.equalsIgnoreCase(candidate)) return column;
        }
        for (String candidate : candidates) {
            String normalized = normalizeName(candidate);
            if (normalized.isEmpty()) continue;
            for (String column : columns) if (normalizeName(column).equals(normalized)) return column;
        }
        return null;
    }

    private static String need(SchemaReader schema, String table, String label, String... candidates)
            throws SectionUnavailable {
        String column = col(schema, table, candidates);
        if (column == null)
            throw new SectionUnavailable("ستون «" + label + "» در «" + title(table) + "» پیدا نشد.");
        return column;
    }

    private static String normalizeName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('ي', 'ی').replace('ك', 'ک')
                .replace("_", "").replace("-", "").replace(" ", "")
                .replace(".", "").replace("/", "").trim();
    }

    /** Persian name of an Atiran table, used in the "structure is missing" messages. */
    static String title(String table) {
        if (table == null) return "";
        String name = table.contains(".") ? table.substring(table.indexOf('.') + 1) : table;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "dar": return "قبوض دریافت و پرداخت";
            case "ban_act": return "گردش بانک";
            case "bank": return "حساب‌های بانکی";
            case "posdetails": return "جزئیات کارت و حواله";
            case "getchk": return "چک‌های دریافتی";
            case "putchk": return "چک‌های پرداختی";
            case "ngetchk": return "چک‌های دریافتی سنوات قبل";
            case "nputchk": return "چک‌های پرداختی سنوات قبل";
            case "customers": return "مشتریان";
            case "cust_act": return "گردش حساب مشتری";
            case "inventory": return "کالاها";
            case "inventory_anbars": return "موجودی کالا در انبار";
            case "ka_act": return "گردش کالا";
            case "kagroup": return "گروه کالا";
            case "anbars": return "انبارها";
            case "sailfact": return "فاکتور فروش";
            case "subsailfact": return "اقلام فاکتور فروش";
            case "visitors": return "ویزیتورها";
            case "vis_goals": return "اهداف ویزیتور";
            case "masir": return "مسیرها";
            case "regions": return "منطقه‌ها";
            case "sys_users": return "کاربران";
            case "roles": case "role": return "نقش‌ها";
            case "logindetails": return "ورودهای کاربران";
            case "log": return "رویدادهای سیستم";
            case "actnames": return "نام عملیات گردش";
            case "production": return "تولید";
            case "productioncost": return "بهای تولید";
            case "formulation": return "فرمولاسیون";
            case "formulationitems": return "اقلام فرمولاسیون";
            case "indirectcost": return "هزینه‌های غیرمستقیم";
            case "externalcosts": return "هزینه‌های جانبی";
            case "kasr_e_sanad": return "اسناد کسری انبار";
            case "dardescriptiontype": return "انواع شرح قبض";
            default: return name;
        }
    }

    // ------------------------------------------------------------------ catalog agreement

    /** Column keys the SQL selects, i.e. every catalog column except the ones derived in Java. */
    static List<String> outputColumns(VariantSpec spec) {
        List<String> keys = new ArrayList<>();
        for (SectionModels.SectionColumn column : spec.columns())
            if (!spec.isDerived(column.key())) keys.add(column.key());
        return keys;
    }

    /**
     * Joins two parameter groups in placeholder order and verifies the count against the statement,
     * so a catalog or SQL change can never silently bind a value to the wrong placeholder.
     */
    private static List<Object> ordered(List<Object> first, List<Object> second, String sql) {
        List<Object> params = new ArrayList<>(first);
        params.addAll(second);
        long placeholders = sql.chars().filter(ch -> ch == '?').count();
        if (placeholders != params.size())
            throw new IllegalStateException("تعداد پارامترهای گزارش (" + params.size()
                    + ") با جای‌نشان‌های پرس‌وجو (" + placeholders + ") برابر نیست.");
        return params;
    }

    private static SectionUnavailable unknownReport(VariantSpec spec) {
        return new SectionUnavailable("گزارش «" + spec.label() + "» در بخش «"
                + SectionCatalog.titleOf(spec.sectionId()) + "» پیاده‌سازی نشده است.");
    }
}
