package ir.meelano.manager.data;

import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.Sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Live SQL Server metadata with per-connection caching. Atiran installations
 * do not put every object in dbo, so resolution prefers dbo and then safely
 * uses the actual schema/name discovered from the selected database.
 */
public final class Meta {
    private static final class ObjectRef {
        final String schema;
        final String name;
        final long id;

        ObjectRef(String schema, String name, long id) {
            this.schema = schema;
            this.name = name;
            this.id = id;
        }

        String qualified() {
            return quote(schema) + "." + quote(name);
        }
    }

    /** Raised instead of silently treating a metadata permission/query error as a missing table. */
    public static final class MetadataException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MetadataException(Exception cause) {
            super("خواندن ساختار دیتابیس از SQL Server ممکن نشد؛ دیتابیس انتخاب‌شده و مجوز مشاهدهٔ ساختار (VIEW DEFINITION) را بررسی کنید"
                    + sqlState(cause), cause);
        }

        private static String sqlState(Exception e) {
            if (e instanceof SQLException) {
                String state = ((SQLException) e).getSQLState();
                if (state != null && !state.trim().isEmpty()) return " (SQLState " + state + ")";
            }
            return "";
        }
    }

    private final Connection c;
    private final Map<String, ObjectRef> tables = new HashMap<>();
    private final Map<String, Set<String>> cols = new HashMap<>();
    private final Map<String, ObjectRef> functions = new HashMap<>();
    private final Map<String, Boolean> executableFunctions = new HashMap<>();

    public Meta(Connection c) {
        this.c = c;
    }

    /** True when a table/view exists in any schema (dbo is preferred). */
    public boolean table(String name) {
        return resolveTable(name) != null;
    }

    /** Actual SQL-safe two-part name, e.g. [security].[LoginDetails], or null. */
    public String qTable(String name) {
        ObjectRef ref = resolveTable(name);
        return ref == null ? null : ref.qualified();
    }

    private ObjectRef resolveTable(String name) {
        String k = key(name);
        if (tables.containsKey(k)) return tables.get(k);
        String query = "SELECT TOP (1) s.name,o.name,o.object_id "
                + "FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id "
                + "WHERE o.type IN (N'U',N'V') AND LOWER(o.name)=LOWER(?) "
                + "ORDER BY CASE WHEN LOWER(s.name)=N'dbo' THEN 0 ELSE 1 END,s.name,o.name";
        try (PreparedStatement ps = c.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) {
                ObjectRef ref = r.next() ? new ObjectRef(r.getString(1), r.getString(2), r.getLong(3)) : null;
                tables.put(k, ref);
                return ref;
            }
        } catch (Exception e) {
            throw new MetadataException(e);
        }
    }

    /** Column names of a table/view (empty set when the object is absent). */
    public Set<String> columns(String table) {
        String k = key(table);
        Set<String> hit = cols.get(k);
        if (hit != null) return hit;
        ObjectRef ref = resolveTable(table);
        if (ref == null) {
            Set<String> empty = Collections.emptySet();
            cols.put(k, empty);
            return empty;
        }
        Set<String> set = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT c.name FROM sys.columns c WHERE c.object_id=? ORDER BY c.column_id")) {
            ps.setLong(1, ref.id);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) set.add(r.getString(1));
            }
        } catch (Exception e) {
            throw new MetadataException(e);
        }
        cols.put(k, set);
        return set;
    }

    /** True when this SQL principal can SELECT from the resolved object. */
    public boolean canSelect(String name) {
        ObjectRef ref = resolveTable(name);
        if (ref == null) return false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT CONVERT(int,COALESCE(HAS_PERMS_BY_NAME(?,N'OBJECT',N'SELECT'),0))")) {
            ps.setString(1, ref.qualified());
            try (ResultSet r = ps.executeQuery()) {
                return r.next() && r.getInt(1) == 1;
            }
        } catch (Exception e) {
            throw new MetadataException(e);
        }
    }

    /** True when a scalar or table-valued user function exists and is executable. */
    public boolean function(String name) {
        return qFunction(name) != null;
    }

    /** Actual SQL-safe, executable two-part function name, e.g. [dbo].[dif_date_alan], or null. */
    public String qFunction(String name) {
        ObjectRef ref = resolveFunction(name);
        if (ref == null) return null;
        String k = key(name);
        Boolean allowed = executableFunctions.get(k);
        if (allowed == null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT CONVERT(int,COALESCE(HAS_PERMS_BY_NAME(?,N'OBJECT',N'EXECUTE'),0))")) {
                ps.setString(1, ref.qualified());
                try (ResultSet r = ps.executeQuery()) {
                    allowed = r.next() && r.getInt(1) == 1;
                    executableFunctions.put(k, allowed);
                }
            } catch (Exception e) {
                throw new MetadataException(e);
            }
        }
        return allowed ? ref.qualified() : null;
    }

    private ObjectRef resolveFunction(String name) {
        String k = key(name);
        if (functions.containsKey(k)) return functions.get(k);
        String query = "SELECT TOP (1) s.name,o.name,o.object_id "
                + "FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id "
                + "WHERE o.type IN (N'FN',N'IF',N'TF',N'FS',N'FT') AND LOWER(o.name)=LOWER(?) "
                + "ORDER BY CASE WHEN LOWER(s.name)=N'dbo' THEN 0 ELSE 1 END,s.name,o.name";
        try (PreparedStatement ps = c.prepareStatement(query)) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) {
                ObjectRef ref = r.next() ? new ObjectRef(r.getString(1), r.getString(2), r.getLong(3)) : null;
                functions.put(k, ref);
                return ref;
            }
        } catch (Exception e) {
            throw new MetadataException(e);
        }
    }

    /** Resolve a column (case-insensitive) or null. Requires the table to exist. */
    public String col(String table, String... candidates) {
        if (!table(table)) return null;
        return Sql.pick(columns(table), candidates);
    }

    /** Flexible resolve (ignores case/underscores) or null. */
    public String colFlex(String table, String... candidates) {
        if (!table(table)) return null;
        return Sql.pickFlex(columns(table), candidates);
    }

    /** Resolve or throw a Persian Missing error naming the table + expected field. */
    public String must(String table, String faWhat, String... candidates) throws Queries.Missing {
        if (!table(table))
            throw new Queries.Missing("«" + AtiranSchema.faTitle(table) + "» در دیتابیس پیدا نشد");
        String c = Sql.pick(columns(table), candidates);
        if (c == null) c = Sql.pickFlex(columns(table), candidates);
        if (c == null)
            throw new Queries.Missing("ستون «" + faWhat + "» در جدول «" + AtiranSchema.faTitle(table) + "» پیدا نشد");
        return c;
    }

    /** Latest (max) LEFT-10 of a date column, or "" when none. */
    public String latestDate(String table, String dateCol) {
        if (dateCol == null) return "";
        String tableSql = qTable(table);
        if (tableSql == null) return "";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT MAX(LEFT(TRY_CONVERT(nvarchar(30)," + quote(dateCol) + "),10)) FROM " + tableSql)) {
            try (ResultSet r = ps.executeQuery()) {
                if (r.next()) {
                    String v = r.getString(1);
                    return v == null ? "" : v.trim();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.US);
    }

    private static String quote(String identifier) {
        return "[" + (identifier == null ? "" : identifier.replace("]", "]]")) + "]";
    }

    public Connection connection() {
        return c;
    }
}
