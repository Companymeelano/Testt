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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The whole report catalog must stay executable: every spec id must have a branch in
 * {@link ReportCatalog#query} (otherwise the screen throws «گزارش ناشناخته است»), and the
 * built SQL must resolve every table/column against the sample Atiran schema - {@link
 * Meta#must} throws {@link Queries.Missing} for anything the schema does not have. This
 * exercises the dynamic column lookups that the static schema audit cannot see.
 *
 * <p>The schema fixture below mirrors {@link AtiranSampleSchemaTest}: a metadata-only
 * JDBC proxy backed by {@code /atiran/sample-schema.json}.</p>
 */
public class ReportCatalogIntegrityTest {

    @Test
    public void everyReportBuildsItsSqlAgainstTheSampleSchema() throws Exception {
        Meta meta = new Meta(connection(loadManifest()));
        Filter f = new Filter();
        f.from = "1405/01/01";
        f.to = "1405/12/29";
        int built = 0;
        for (ReportCatalog.Spec spec : ReportCatalog.ALL) {
            Queries.Q q;
            try {
                q = ReportCatalog.query(meta, spec.id, f);
            } catch (Queries.Missing e) {
                throw new AssertionError("report does not build: " + spec.id + " — " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new AssertionError("report crashed while building: " + spec.id, e);
            }
            assertNotNull(spec.id, q);
            assertTrue(spec.id + ": empty SQL", q.sql != null && q.sql.trim().length() > 10);
            built++;
        }
        assertTrue("the catalog lost reports: " + built, built >= 100);
    }

    @Test
    public void everyReportIsResolvableByIdAndUnique() {
        Set<String> seen = new HashSet<>();
        for (ReportCatalog.Spec spec : ReportCatalog.ALL) {
            assertTrue("duplicate report id: " + spec.id, seen.add(spec.id));
            assertSame(spec.id, spec, ReportCatalog.byId(spec.id));
        }
    }

    @Test
    public void reportsCarryTitleSectionAndColumns() {
        for (ReportCatalog.Spec spec : ReportCatalog.ALL) {
            assertTrue(spec.id + " title", spec.title != null && !spec.title.isEmpty());
            assertTrue(spec.id + " section", spec.section != null && !spec.section.isEmpty());
            assertNotNull(spec.id + " cols", spec.cols);
            assertTrue(spec.id + " cols", spec.cols.length > 0);
        }
    }

    // ================= schema fixture (mirrors AtiranSampleSchemaTest) =================

    private static JSONObject loadManifest() throws Exception {
        try (InputStream in = ReportCatalogIntegrityTest.class.getResourceAsStream("/atiran/sample-schema.json")) {
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
