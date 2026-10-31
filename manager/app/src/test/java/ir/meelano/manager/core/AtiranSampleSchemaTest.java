package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Integration-style unit tests using only schema metadata from the supplied backup. */
public class AtiranSampleSchemaTest {
    @Test public void corePreflightMatchesTheRestoredAtiranSample() throws Exception {
        JSONObject manifest = loadManifest();
        AtiranPreflight.Report report = AtiranPreflight.inspect(connection(manifest));
        assertTrue(report.persianSummary(), report.complete());
        assertEquals(12, report.readySections);
        assertEquals(12, report.totalSections);
    }

    /** Purchase unit price comes from JOZPRICE: subbuyfact has no LINESUM in the sample schema. */
    @Test public void purchasePriceUsesJozPriceNotLineSum() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        String sql = Queries.buyPriceTrend(meta, new Filter(), 50).sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("jozprice"));
        assertFalse(sql, sql.contains("linesum"));
    }

    /** Stock value counts loose pieces (mojkajoz) at inventory_price for active items only. */
    @Test public void stockValueCountsPiecesAtInventoryPrice() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        String sql = MasterQueries.stockValueByGroup(meta).sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("mojkajoz"));
        assertTrue(sql, sql.contains("inventory_price"));
        assertTrue(sql, sql.contains(" where "));
    }

    @Test public void visitorScorecardUsesOnlyActiveOverlappingGenericGoals() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        Queries.Q q = VisitorScorecard.query(meta, new Filter());
        String sql = q.sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("join dbo.baze"));
        assertTrue(sql, sql.contains("from dbo.vis_goals g"));
        assertTrue(sql, sql.contains("customergrouprdf"));
        assertTrue(sql, sql.contains("quarterid"));
        assertTrue(sql, sql.contains("group by b.[rdf]"));
        assertTrue(sql, sql.contains("goalfrom"));
        assertTrue(sql, sql.contains("previoussales"));
    }

    @Test public void expiryReportValuesRecordedInventoryShelfExposure() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        String sql = AtiranSpecialReports.expiryWatch(meta, new Filter()).sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("expirationdate"));
        assertTrue(sql, sql.contains("inventory_price"));
        assertTrue(sql, sql.contains("mojkavah"));
        assertTrue(sql, sql.contains("mojkajoz"));
    }

    @Test public void attendanceReportConvertsJalaliBoundsAndIncludesAuditSignals() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        Filter filter = new Filter();
        filter.from = "1405/01/01";
        filter.to = "1405/01/30";
        Queries.Q q = AtiranSpecialReports.attendanceAudit(meta, filter);
        String sql = q.sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("a.[event_time]>=?"));
        assertTrue(sql, sql.contains("a.[event_time]<dateadd(day,1,?)"));
        assertTrue(sql, sql.contains("edited_by"));
        assertTrue(sql, sql.contains("voided"));
        assertEquals(Jalali.toGregorian(filter.from), q.binds.get(0));
        assertEquals(Jalali.toGregorian(filter.to), q.binds.get(1));
    }

    @Test public void metadataResolverUsesTheActualNonDboSchema() throws Exception {
        JSONObject manifest = loadManifest();
        Meta meta = new Meta(connection(manifest));
        assertTrue(meta.table("LoginDetails"));
        assertEquals("[security].[LoginDetails]", meta.qTable("LoginDetails"));
        assertEquals("UserID", meta.must("LoginDetails", "کاربر", "user_id", "userid"));
        assertEquals("DateServer", meta.colFlex("LoginDetails", "login_date", "DateServer", "DateClient"));
        assertTrue(meta.canSelect("LoginDetails"));
        assertEquals("[dbo].[dif_date_alan]", meta.qFunction("dif_date_alan"));
    }

    @Test public void productsSummaryMarksActiveAndReorderKpisAsKnownAndScopesStockAlerts() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        String sql = MasterQueries.productsSummary(meta).sql.toLowerCase(Locale.ROOT);
        assertTrue(sql, sql.contains("as activeknown"));
        assertTrue(sql, sql.contains("as reorderknown"));
        assertTrue(sql, sql.contains("as reordern"));
        assertTrue(sql, sql.contains("try_convert(int,[active])=1"));
        assertTrue(sql, sql.contains("mojkavah"));
        assertTrue(sql, sql.contains("reopoint"));
    }

    @Test public void productListAndCountShareNormalizedReadOnlyFiltersAndExactPaging() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        Filter filter = new Filter();
        filter.status = "low";
        filter.search = "علي کالا* ۱۲۳";
        filter.kalaGroup = 4;
        filter.warehouse = 5;
        filter.top = 30;
        filter.page = 2;

        Queries.Q list = MasterQueries.productsList(meta, filter);
        Queries.Q count = MasterQueries.productsCount(meta, filter);
        String listSql = list.sql.toLowerCase(Locale.ROOT);
        String countSql = count.sql.toLowerCase(Locale.ROOT);
        assertTrue(listSql, listSql.contains("offset ? rows fetch next ? rows only"));
        assertTrue(listSql, listSql.contains("nchar(8204)"));
        assertTrue(listSql, listSql.contains("as stockpieces"));
        assertTrue(listSql, listSql.contains("as reorderpoint"));
        assertTrue(listSql, listSql.contains("as anbarpieces"));
        assertTrue(listSql, listSql.contains("active"));
        assertTrue(countSql, countSql.startsWith("select count_big(distinct i.[shka]) as total"));
        assertFalse(countSql, countSql.contains("offset"));
        assertEquals(count.binds.size() + 2, list.binds.size());
        for (int i = 0; i < count.binds.size(); i++) assertEquals(count.binds.get(i), list.binds.get(i));
        assertEquals(60L, ((Number) list.binds.get(list.binds.size() - 2)).longValue());
        assertEquals(30, ((Number) list.binds.get(list.binds.size() - 1)).intValue());
        assertTrue(list.binds.contains("علی"));
        assertTrue(list.binds.contains("کالا"));
        assertTrue(list.binds.contains("123"));
    }

    @Test public void userQueriesUseTheSampleSchemasForRolesAndAuditTables() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        Queries.Q users = MasterQueries.usersList(meta);
        assertTrue(users.sql.contains("FROM [dbo].[sys_users] u"));
        assertTrue(users.sql.contains("LEFT JOIN [dbo].[Roles] r"));
        assertTrue(users.sql.contains("FROM [security].[LoginDetails] lg"));
        assertTrue(users.sql.contains("FROM [security].[Log] lo"));

        Queries.Q logins = MasterQueries.userLogins(meta, "7");
        assertTrue(logins.sql.contains("FROM [security].[LoginDetails]"));
        Queries.Q lookup = MasterQueries.lookupUsers(meta);
        assertTrue(lookup.sql.contains("FROM [dbo].[sys_users] t"));
    }

    private static JSONObject loadManifest() throws Exception {
        try (InputStream in = AtiranSampleSchemaTest.class.getResourceAsStream("/atiran/sample-schema.json")) {
            assertNotNull("metadata-only sample schema resource", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static Connection connection(JSONObject manifest) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()))
                return statement(manifest, (String) args[0]);
            return defaultValue(method.getReturnType());
        });
    }

    private static PreparedStatement statement(JSONObject manifest, String sql) {
        Map<Integer, Object> binds = new HashMap<>();
        return proxy(PreparedStatement.class, (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                binds.put((Integer) args[0], args[1]);
                return null;
            }
            if ("executeQuery".equals(name)) return resultSet(rowsFor(manifest, sql, binds));
            return defaultValue(method.getReturnType());
        });
    }

    private static List<Object[]> rowsFor(JSONObject manifest, String sql, Map<Integer, Object> binds) throws Exception {
        JSONArray objects = manifest.getJSONArray("objects");
        List<JSONObject> matches = new ArrayList<>();
        String lowerSql = sql.toLowerCase(Locale.US);

        if (lowerSql.contains("has_perms_by_name") && !lowerSql.contains("lower(o.name) in ("))
            return java.util.Collections.singletonList(new Object[]{1});

        if (lowerSql.contains("from sys.columns c")) {
            long id = ((Number) binds.get(1)).longValue();
            JSONObject obj = objects.getJSONObject((int) id - 1);
            JSONArray columns = obj.getJSONArray("columns");
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < columns.length(); i++)
                rows.add(new Object[]{columns.getJSONObject(i).getString("name")});
            return rows;
        }

        Set<String> wanted = new HashSet<>();
        for (Object value : binds.values())
            if (value instanceof String) wanted.add(((String) value).toLowerCase(Locale.US));
        for (int i = 0; i < objects.length(); i++) {
            JSONObject obj = objects.getJSONObject(i);
            if (wanted.contains(obj.getString("name").toLowerCase(Locale.US))) matches.add(obj);
        }
        matches.sort(Comparator
                .comparing((JSONObject obj) -> !"dbo".equalsIgnoreCase(obj.optString("schema")))
                .thenComparing(obj -> obj.optString("schema").toLowerCase(Locale.US))
                .thenComparing(obj -> obj.optString("name").toLowerCase(Locale.US)));

        List<Object[]> rows = new ArrayList<>();
        if (lowerSql.contains("lower(o.name) in (")) {
            for (JSONObject obj : matches) {
                JSONArray columns = obj.getJSONArray("columns");
                for (int i = 0; i < columns.length(); i++)
                    rows.add(new Object[]{obj.getString("schema"), obj.getString("name"),
                            columns.getJSONObject(i).getString("name"), 1});
            }
        } else if (lowerSql.contains("n'fn'")) {
            // The current tests do not require a sample UDF; return the empty set if absent.
            JSONArray functions = manifest.optJSONArray("functions");
            if (functions != null) {
                for (int i = 0; i < functions.length(); i++) {
                    JSONObject fn = functions.getJSONObject(i);
                    if (wanted.contains(fn.getString("name").toLowerCase(Locale.US)))
                        rows.add(new Object[]{fn.getString("schema"), fn.getString("name"), (long) i + 1});
                }
            }
            rows.sort(Comparator.comparing((Object[] row) -> !"dbo".equalsIgnoreCase(String.valueOf(row[0])))
                    .thenComparing(row -> String.valueOf(row[0]).toLowerCase(Locale.US)));
        } else {
            for (int i = 0; i < matches.size(); i++) {
                JSONObject obj = matches.get(i);
                rows.add(new Object[]{obj.getString("schema"), obj.getString("name"), (long) objectId(objects, obj) + 1});
                break; // Meta resolves one matching table/view, preferring dbo.
            }
        }
        return rows;
    }

    private static int objectId(JSONArray objects, JSONObject wanted) throws Exception {
        for (int i = 0; i < objects.length(); i++)
            if (objects.getJSONObject(i) == wanted) return i;
        // JSONObject instances are stable in this test fixture, but compare identity fields too.
        for (int i = 0; i < objects.length(); i++) {
            JSONObject obj = objects.getJSONObject(i);
            if (obj.getString("schema").equals(wanted.getString("schema"))
                    && obj.getString("name").equals(wanted.getString("name"))) return i;
        }
        throw new IllegalStateException("manifest object not found");
    }

    private static ResultSet resultSet(List<Object[]> rows) {
        final int[] cursor = {-1};
        return proxy(ResultSet.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("next".equals(name)) return ++cursor[0] < rows.size();
            if ("getString".equals(name) || "getLong".equals(name) || "getInt".equals(name)
                    || "getObject".equals(name)) {
                int column = ((Number) args[0]).intValue() - 1;
                Object value = rows.get(cursor[0])[column];
                if ("getLong".equals(name)) return value == null ? 0L : ((Number) value).longValue();
                if ("getInt".equals(name)) return value == null ? 0 : ((Number) value).intValue();
                return value == null ? null : String.valueOf(value);
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
