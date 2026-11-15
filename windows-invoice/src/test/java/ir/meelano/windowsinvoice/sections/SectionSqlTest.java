package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.WindowsRoleMenus;
import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionUnavailable;
import ir.meelano.windowsinvoice.sections.SectionSql.PreparedQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every desktop Manager report is built here against a metadata reader that mirrors a real Atiran
 * database. The rules under test are the ones that keep a read-only client safe and honest: SELECT
 * only, user input always bound, a row cap on every query, resolved column names only, and a clear
 * Persian explanation when the installation's structure cannot serve a report.
 */
class SectionSqlTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 10, 25);
    private static final String TODAY = PersianDate.fromGregorian(ANCHOR);

    /** Statements that must never appear in a report query, in any case or spelling. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|EXECUTE|MERGE|TRUNCATE|GRANT|REVOKE|SHUTDOWN"
                    + "|XP_[A-Z_]+|SP_[A-Z_]+)\\b");

    static final Map<String, List<String>> ATIRAN = atiranSchema();
    static final SectionSql.SchemaReader SCHEMA = new FakeSchema(ATIRAN);

    @Test void everyReportBuildsASelectOnlyStatement() throws Exception {
        int built = 0;
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                PreparedQuery query = SectionSql.build(spec, SectionFilter.defaults(), SCHEMA, TODAY);
                String sql = query.sql();
                String where = spec.sectionId() + "/" + spec.id();
                assertTrue(sql.trim().regionMatches(true, 0, "SELECT ", 0, 7), where + " is not a SELECT");
                assertFalse(FORBIDDEN.matcher(sql.toUpperCase(Locale.ROOT)).find(), where + " is not read-only");
                assertFalse(sql.contains(";"), where + " must not chain statements");
                assertFalse(sql.contains("--"), where + " must not carry SQL comments");
                assertFalse(sql.contains("/*"), where + " must not carry SQL comments");
                assertEquals(placeholders(sql), query.params().size(), where + " binds the wrong parameter count");
                assertTrue(sql.contains("TOP (" + spec.maxRows() + ")"), where + " has no row cap");
                assertTrue(sql.contains("[") && sql.contains("]"), where + " does not quote identifiers");
                built++;
            }
        }
        assertEquals(33, built, "every declared report must build");
    }

    @Test void userInputIsBoundAndNeverConcatenatedIntoSql() throws Exception {
        String hostile = "1'); DROP TABLE dar; -- /* % _ [ ]";
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                SectionFilter filter = new SectionFilter(hostile, hostile, "", "", null, 0, 0, 200);
                PreparedQuery query = SectionSql.build(spec, filter, SCHEMA, TODAY);
                String where = spec.sectionId() + "/" + spec.id();
                assertFalse(query.sql().contains(hostile), where + " concatenated the search text");
                assertFalse(FORBIDDEN.matcher(query.sql().toUpperCase(Locale.ROOT)).find(),
                        where + " let the search text inject a statement");
                assertEquals(placeholders(query.sql()), query.params().size(),
                        where + " binds the wrong parameter count with a search");
                for (Object param : query.params()) {
                    if (param instanceof String text) {
                        // A search value is only ever a LIKE pattern; wildcards are escaped first.
                        assertTrue(text.isEmpty() || text.startsWith("%") || text.matches("[0-9/]{4,10}"),
                                where + " passes an unexpected literal: " + text);
                    }
                }
            }
        }
    }

    @Test void likeWildcardsInTheSearchTextAreEscaped() throws Exception {
        PreparedQuery query = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CASH, "receipts"),
                new SectionFilter("50%_off", "", "", "", null, 0, 0, 200), SCHEMA, TODAY);
        assertTrue(query.sql().contains("ESCAPE '\\'"), "the LIKE pattern is not escaped");
        assertTrue(query.params().contains("%50\\%\\_off%"), "wildcards were not escaped: " + query.params());
        assertEquals("%a\\[b\\]%", "%" + SectionSql.escapeLike("a[b]") + "%");
        assertEquals("", SectionSql.escapeLike(null));
        assertEquals("plain", SectionSql.escapeLike("plain"));
    }

    @Test void theDefaultWindowIsBoundAsJalaliBounds() throws Exception {
        VariantSpec daily = SectionCatalog.spec(WindowsRoleMenus.CASH, "daily");
        PreparedQuery query = SectionSql.build(daily, SectionFilter.defaults(), SCHEMA, TODAY);
        String from = PersianDate.fromGregorian(ANCHOR.minusDays(62));
        assertEquals(List.of(from, TODAY), query.params());
        assertTrue(query.sql().contains(">=") && query.sql().contains("<="));

        // A typed window replaces the default one.
        PreparedQuery typed = SectionSql.build(daily,
                new SectionFilter("", "", "1405/07/01", "1405/07/30", null, 0, 0, 200), SCHEMA, TODAY);
        assertEquals(List.of("1405/07/01", "1405/07/30"), typed.params());

        // The cheque collection list looks forward from today instead of back.
        PreparedQuery due = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "due"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertEquals(List.of(TODAY, PersianDate.fromGregorian(ANCHOR.plusDays(30))), due.params());
        assertTrue(due.sql().contains("NOT IN (N'3',N'4')"), "collected and returned cheques must stay out");

        // Dead stock binds its idle cut-off, not a date range.
        PreparedQuery dead = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "dead"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(dead.params().contains(PersianDate.fromGregorian(ANCHOR.minusDays(180))),
                "dead stock has no idle cut-off: " + dead.params());
    }

    @Test void reportsWithNoWindowReadEverythingTheyAreAllowedTo() throws Exception {
        PreparedQuery stock = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.STOCK_LEDGER, "stock"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertEquals(List.of(), stock.params());
        assertFalse(stock.sql().contains(">="));

        PreparedQuery users = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.USERS, "list"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(users.params().contains(TODAY), "today's event count must be bound");
    }

    @Test void chequeReportsUnionThePriorYearsArchiveOnlyWhenItIsReadable() throws Exception {
        PreparedQuery withArchive = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "incoming"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(withArchive.sql().contains("UNION ALL"), "the archive years are missing");
        assertTrue(withArchive.sql().contains("[NGetchk]"));
        // Atiran's own views treat these statuses as void; they must never be listed.
        assertTrue(withArchive.sql().contains("NOT IN (N'6',N'7',N'8')"));

        Map<String, List<String>> withoutArchive = new LinkedHashMap<>(ATIRAN);
        withoutArchive.remove("NGetchk");
        PreparedQuery liveOnly = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "incoming"),
                SectionFilter.defaults(), new FakeSchema(withoutArchive), TODAY);
        assertFalse(liveOnly.sql().contains("UNION ALL"));
        assertTrue(liveOnly.sql().contains("[getchk]"));

        PreparedQuery outgoing = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "outgoing"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(outgoing.sql().contains("[putchk]") && outgoing.sql().contains("[NPutchk]"));
        // A cheque table without an amount or a status column cannot be reported on at all.
        Map<String, List<String>> broken = new LinkedHashMap<>(ATIRAN);
        broken.put("getchk", List.of("shgetchk", "sardate"));
        SectionUnavailable missing = assertThrows(SectionUnavailable.class,
                () -> SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CHEQUES, "incoming"),
                        SectionFilter.defaults(), new FakeSchema(broken), TODAY));
        assertTrue(missing.getMessage().contains("پیدا نشد"), missing.getMessage());
    }

    @Test void profitIsAggregatedWithoutHeaderFanOut() throws Exception {
        PreparedQuery daily = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PROFIT, "daily"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(daily.sql().contains("UNION ALL"), "sales and COGS must be aggregated separately");
        assertTrue(daily.sql().contains("SELECT DISTINCT x.*"), "the sales header must be de-duplicated");
        assertEquals(placeholders(daily.sql()), daily.params().size());
        // The same window is bound once per leg of the union.
        String from = PersianDate.fromGregorian(ANCHOR.minusDays(62));
        assertEquals(2, daily.params().stream().filter(from::equals).count());
        assertEquals(2, daily.params().stream().filter(TODAY::equals).count());
    }

    @Test void lowMarginUsesTheTypedThresholdAsABoundValue() throws Exception {
        VariantSpec spec = SectionCatalog.spec(WindowsRoleMenus.PROFIT, "low_margin");
        PreparedQuery query = SectionSql.build(spec, SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(query.params().contains(new java.math.BigDecimal("10")), "the catalog default is not bound");
        assertTrue(query.sql().contains("HAVING"));

        PreparedQuery typed = SectionSql.build(spec,
                new SectionFilter("", "", "", "", new java.math.BigDecimal("25"), 0, 0, 200), SCHEMA, TODAY);
        assertTrue(typed.params().contains(new java.math.BigDecimal("25")));
    }

    @Test void reportsAreOrderedTheWayTheCatalogDeclares() throws Exception {
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                if (spec.sortKey().isEmpty()) continue;
                String sql = SectionSql.build(spec, SectionFilter.defaults(), SCHEMA, TODAY).sql();
                assertTrue(sql.toUpperCase(Locale.ROOT).contains("ORDER BY"),
                        spec.id() + " declares a sort but the query has no ORDER BY");
            }
        }
    }

    @Test void productionReadsFromTheProduceSchema() throws Exception {
        PreparedQuery documents = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PRODUCTION, "documents"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(documents.sql().contains("produce.[Production]"), documents.sql());
        PreparedQuery formulation = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PRODUCTION, "formulation"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(formulation.sql().contains("produce.[FormulationItems]"));
        PreparedQuery costs = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PRODUCTION, "costs"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(costs.sql().contains("produce.[ProductionCost]"));
        assertTrue(costs.sql().contains("produce.[IndirectCost]"));
    }

    @Test void costReportsUnionEveryReadableSource() throws Exception {
        PreparedQuery costs = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PROFIT, "costs"),
                SectionFilter.defaults(), SCHEMA, TODAY);
        assertTrue(costs.sql().contains("UNION ALL"));
        // Atiran's produce.IndirectCost is a cost-title dictionary with no amount, so it is skipped
        // here; the readable money sources are the side-cost and warehouse-shortage tables.
        assertTrue(costs.sql().contains("هزینهٔ جانبی"), costs.sql());
        assertTrue(costs.sql().contains("کسری انبار"), costs.sql());
        assertFalse(costs.sql().contains("[IndirectCost]"));

        Map<String, List<String>> onlyIndirect = new LinkedHashMap<>();
        onlyIndirect.put("IndirectCost", List.of("RowId", "CostName", "mablagh", "date", "Active"));
        PreparedQuery single = SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PROFIT, "costs"),
                SectionFilter.defaults(), new FakeSchema(onlyIndirect), TODAY);
        assertFalse(single.sql().contains("UNION ALL"));
        assertTrue(single.sql().contains("هزینهٔ غیرمستقیم"), single.sql());

        SectionUnavailable none = assertThrows(SectionUnavailable.class,
                () -> SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.PROFIT, "costs"),
                        SectionFilter.defaults(), new FakeSchema(Map.of()), TODAY));
        assertTrue(none.getMessage().contains("پیدا نشد"), none.getMessage());
    }

    @Test void aMissingStructureIsExplainedInPersianForEveryReport() {
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                SectionUnavailable error = assertThrows(SectionUnavailable.class,
                        () -> SectionSql.build(spec, SectionFilter.defaults(), new FakeSchema(Map.of()), TODAY));
                String message = error.getMessage();
                assertTrue(message != null && !message.isBlank(), spec.id() + " has no explanation");
                assertTrue(message.contains("پیدا نشد"), spec.id() + ": " + message);
                assertFalse(message.contains("null"), spec.id() + ": " + message);
            }
        }
    }

    @Test void optionalColumnsDegradeInsteadOfFailingTheReport() throws Exception {
        // An Atiran without the POS detail, group, warehouse or role tables still reports.
        Map<String, List<String>> minimal = new LinkedHashMap<>();
        minimal.put("dar", List.of("ghno", "p", "date", "shmo", "mab", "naghd", "mabcheck", "active"));
        minimal.put("CUSTOMERS", List.of("SHMO", "MONAME", "man"));
        minimal.put("inventory", List.of("shka", "naka", "mojkavah", "buy_price"));
        minimal.put("ka_act", List.of("shka", "act_id", "act_date", "tedvah"));
        minimal.put("sailfact", List.of("shfacfo", "date", "shmo", "all", "active"));
        minimal.put("subsailfact", List.of("shfacfo", "SHKA", "TEDVAH", "LINESUM"));
        minimal.put("sys_users", List.of("user_id", "user_name", "role_id", "active"));
        SectionSql.SchemaReader reader = new FakeSchema(minimal);
        for (String id : List.of("cash/receipts", "stock_ledger/stock", "stock_ledger/kardex",
                "reports/sales_daily", "users/list")) {
            VariantSpec spec = SectionCatalog.spec(id.substring(0, id.indexOf('/')), id.substring(id.indexOf('/') + 1));
            PreparedQuery query = SectionSql.build(spec, SectionFilter.defaults(), reader, TODAY);
            assertTrue(query.sql().trim().regionMatches(true, 0, "SELECT ", 0, 7), id);
            assertEquals(placeholders(query.sql()), query.params().size(), id);
        }
        // A database without the sales header cannot compute a top-products report at all.
        Map<String, List<String>> noSales = new LinkedHashMap<>(minimal);
        noSales.remove("sailfact");
        assertThrows(SectionUnavailable.class, () -> SectionSql.build(
                SectionCatalog.spec(WindowsRoleMenus.REPORTS, "top_products"),
                SectionFilter.defaults(), new FakeSchema(noSales), TODAY));
    }

    @Test void contextFiltersBindTheirOwnParameter() throws Exception {
        VariantSpec daily = SectionCatalog.spec(WindowsRoleMenus.PERSONNEL, "daily");
        PreparedQuery open = SectionSql.build(daily, SectionFilter.defaults(), SCHEMA, TODAY);
        PreparedQuery filtered = SectionSql.build(daily,
                new SectionFilter("", "ویزیتور شمال", "", "", null, 0, 0, 200), SCHEMA, TODAY);
        assertEquals(open.params().size() + 2, filtered.params().size());
        assertTrue(filtered.params().contains("%ویزیتور شمال%"));

        VariantSpec customers = SectionCatalog.spec(WindowsRoleMenus.PERSONNEL, "customers");
        PreparedQuery scoped = SectionSql.build(customers,
                new SectionFilter("", "ویزیتور شمال", "", "", null, 0, 0, 200), SCHEMA, TODAY);
        assertTrue(scoped.params().contains("%ویزیتور شمال%"));
        assertEquals(placeholders(scoped.sql()), scoped.params().size());
    }

    @Test void reportsOutsideTheDesktopCatalogAreRefusedBeforeAnyQueryIsBuilt() throws Exception {
        // Sections without a desktop implementation never reach the SQL builder.
        assertThrows(IllegalArgumentException.class, () -> SectionCatalog.spec("moadian", "list"));
        assertThrows(IllegalArgumentException.class, () -> SectionCatalog.spec("invoice", "daily"));
        assertFalse(SectionCatalog.isDesktopSection("moadian"));
        // A report that does exist always yields a statement.
        assertTrue(SectionSql.build(SectionCatalog.spec(WindowsRoleMenus.CASH, "daily"),
                SectionFilter.defaults(), SCHEMA, TODAY).sql().length() > 40);
    }

    // ------------------------------------------------------------------ fixtures

    private static long placeholders(String sql) {
        return sql.chars().filter(ch -> ch == '?').count();
    }

    /** Metadata reader over a fixed table map; mirrors what the gateway reads from sys.columns. */
    static final class FakeSchema implements SectionSql.SchemaReader {
        private final Map<String, List<String>> tables;

        FakeSchema(Map<String, List<String>> tables) {
            Map<String, List<String>> normalized = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : tables.entrySet()) {
                // Bare names are dbo objects, exactly like an unqualified lookup against Atiran.
                String key = entry.getKey().contains(".") ? entry.getKey() : "dbo." + entry.getKey();
                normalized.put(key.toLowerCase(Locale.ROOT), entry.getValue());
            }
            this.tables = normalized;
        }

        @Override public String tableRef(String table) {
            String name = qualified(table);
            if (name == null) return null;
            int dot = name.indexOf('.');
            String schema = dot < 0 ? "dbo" : name.substring(0, dot);
            String object = dot < 0 ? name : name.substring(dot + 1);
            return schema + ".[" + object.replace("]", "]]") + "]";
        }

        @Override public List<String> columns(String table) {
            String name = qualified(table);
            if (name == null) return List.of();
            return tables.get(name.toLowerCase(Locale.ROOT));
        }

        /** Resolves a bare name against the preferred schemas, exactly like the JDBC reader does. */
        private String qualified(String table) {
            String wanted = table == null ? "" : table.trim();
            if (wanted.isEmpty()) return null;
            if (wanted.contains(".")) {
                return tables.containsKey(wanted.toLowerCase(Locale.ROOT)) ? wanted : null;
            }
            for (String schema : List.of("dbo", "produce")) {
                if (tables.containsKey((schema + "." + wanted).toLowerCase(Locale.ROOT)))
                    return schema + "." + wanted;
            }
            return null;
        }
    }

    /** Column names as a real Atiran installation keeps them, including its mixed casing. */
    static Map<String, List<String>> atiranSchema() {
        Map<String, List<String>> schema = new LinkedHashMap<>();
        schema.put("dar", List.of("Rdf_", "ghno", "p", "date", "ClockTime", "shmo", "naghd", "mabcheck",
                "mab", "ted_chk", "shfacfo", "darDescriptionTypeID", "UserID", "IsFinal", "active"));
        schema.put("darDescriptionType", List.of("rowId", "name"));
        schema.put("PosDetails", List.of("ghno", "p", "MabPos", "IsHavaleh", "PosBankRdf", "Rdf_"));
        schema.put("ban_act", List.of("bank_rdf", "act_date", "act_bed", "act_bes", "isActive"));
        schema.put("BANK", List.of("RDF", "name"));
        schema.put("getchk", List.of("shgetchk", "getchkmab", "chk_satus", "getchbank", "getchkshobe",
                "getchkshhes", "sardate", "getdate", "back", "shmo", "ShenaseSayad", "getchkdis",
                "GhnoPardakht", "p", "naghddate", "CheckTypeID"));
        schema.put("putchk", List.of("shputchk", "putchkmab", "putchk_status", "bankrdf", "girande",
                "putdate", "sardate", "back", "shmo", "ShenaseSayad", "putchkdis", "ghno", "p"));
        schema.put("NGetchk", List.of("shgetchk", "getchkmab", "chk_satus", "getchbank", "sardate",
                "getdate", "back", "shmo", "ShenaseSayad"));
        schema.put("NPutchk", List.of("shputchk", "putchkmab", "putchk_status", "bankrdf", "sardate",
                "putdate", "back", "shmo", "ShenaseSayad"));
        schema.put("CheckTypes", List.of("CheckTypeID", "CheckTypeName"));
        schema.put("CUSTOMERS", List.of("SHMO", "MONAME", "man", "cred", "vis_rdf", "RDF_masir",
                "group_rdf", "active", "black_list", "StuffCode"));
        schema.put("cust_act", List.of("shmo", "date", "act_bed", "act_bes", "act_id", "ghno",
                "act_dis", "isActive"));
        schema.put("inventory", List.of("shka", "naka", "group_rdf", "mojkavah", "mojkajoz", "mohvah",
                "vahsanj", "buy_price", "inventory_price", "reopoint", "active", "black_list",
                "StuffCode", "WithProductionSerial", "ProductionPrice", "bastebandi"));
        schema.put("kagroup", List.of("group_rdf", "group_name"));
        schema.put("inventory_anbars", List.of("shka", "rdf_anbars", "mojkavah", "mojkajoz"));
        schema.put("anbars", List.of("rdf_anbar", "name"));
        schema.put("ka_act", List.of("shka", "act_id", "act_date", "done_date", "tedvah", "tedjoz",
                "price", "act_dis", "shfacfo", "RdfAnbar", "active", "shmo", "vis_rdf"));
        schema.put("ActNames", List.of("ActID", "ActName"));
        schema.put("sailfact", List.of("shfacfo", "date", "shmo", "vis_rdf", "all", "all_fel", "tafif",
                "tax", "MabDaryaftFactor", "tdf", "tasvieh", "Status", "active", "deleted"));
        schema.put("subsailfact", List.of("shfacfo", "SHKA", "naka", "TEDVAH", "TEDJOZ", "tedvah_fel",
                "tedjoz_fel", "VAHPRICE", "JOZPRICE", "LINESUM", "linesum_fel", "active"));
        schema.put("visitors", List.of("vis_rdf", "vis_name", "vis_cell", "vis_tell1", "active"));
        schema.put("vis_goals", List.of("vis_rdf", "mab", "ted", "baze_rdf"));
        schema.put("masir", List.of("rdf_masir", "name", "region_rdf"));
        schema.put("regions", List.of("rdf", "name"));
        schema.put("sys_users", List.of("user_id", "user_name", "user_fname", "user_lname", "role_id",
                "active", "phone"));
        schema.put("Roles", List.of("role_id", "name"));
        schema.put("LoginDetails", List.of("user_id", "DateServer", "DateClient", "ComputerIP",
                "ComputerName", "description"));
        schema.put("Log", List.of("user_id", "date"));
        schema.put("ExternalCosts", List.of("ghno", "date", "mablagh", "description", "active"));
        schema.put("kasr_e_sanad", List.of("ghno", "date", "mab", "sharh"));
        schema.put("produce.Production", List.of("ID", "Rdf_", "FormulID", "shka", "TedadVahed",
                "TedBastebandi", "DoneDate", "DoneTime", "Description", "UserId", "Active",
                "rdf_anbar", "dateProduction", "IsManual", "IsConfirm", "IsConfirmAcc"));
        schema.put("produce.ProductionCost", List.of("ID", "Rdf_", "ProductionID", "CostID", "Price", "Active"));
        schema.put("produce.Formulation", List.of("ID", "Name", "shka", "Description", "UserId",
                "Active", "DoneDate", "DoneTime"));
        schema.put("produce.FormulationItems", List.of("ID", "FormulID", "shka", "TedadVahed",
                "TedadJoz", "Active"));
        schema.put("produce.FormulationCost", List.of("ID", "FormulID", "CostID", "Price", "Active"));
        schema.put("produce.IndirectCost", List.of("RowId", "CostName", "Active", "MoeinID", "TafsilID"));
        return schema;
    }

    /**
     * Every Atiran table a desktop report depends on is actually read by at least one report, so the
     * fixture cannot drift away from the queries. CheckTypes (a cheque *type*, never a status) and
     * produce.FormulationCost (formulation overhead, reported under production costs) are intentionally
     * not read by any desktop report.
     */
    @Test void theFixtureTablesTheReportsDependOnAreAllRead() throws Exception {
        List<String> required = new ArrayList<>(ATIRAN.keySet());
        required.remove("CheckTypes");
        required.remove("produce.FormulationCost");
        List<String> unread = new ArrayList<>(required);
        for (String sectionId : SectionCatalog.DESKTOP_SECTIONS) {
            for (VariantSpec spec : SectionCatalog.specsFor(sectionId)) {
                PreparedQuery query = SectionSql.build(spec, SectionFilter.defaults(), SCHEMA, TODAY);
                for (String table : new ArrayList<>(unread)) {
                    String name = table.contains(".") ? table.substring(table.indexOf('.') + 1) : table;
                    if (query.sql().contains("[" + name + "]")) unread.remove(table);
                }
            }
        }
        assertTrue(unread.isEmpty(), "fixture tables no report reads: " + unread);
    }
}
