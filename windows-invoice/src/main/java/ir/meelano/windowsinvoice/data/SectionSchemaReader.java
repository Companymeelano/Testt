package ir.meelano.windowsinvoice.data;

import ir.meelano.windowsinvoice.sections.SectionSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves Atiran table and column names against live sys metadata for the desktop section reports.
 *
 * Atiran installs are not uniform: some keep production data in the {@code produce} schema, some rename
 * columns, and some views are missing entirely. Every report therefore resolves what it needs here
 * first, and a missing structure becomes a clear Persian message instead of a guessed column name.
 * Metadata reads are cached for the lifetime of one connection.
 */
final class SectionSchemaReader implements SectionSql.SchemaReader {
    /** Schema preference when a table name is given without one; dbo first, then Atiran's produce schema. */
    private static final String[] PREFERRED_SCHEMAS = {"dbo", "produce"};

    private final Connection connection;
    private final Map<String, String> tableRefs = new HashMap<>();
    private final Map<String, List<String>> columns = new HashMap<>();
    private String metadataError = "";

    SectionSchemaReader(Connection connection) {
        this.connection = connection;
    }

    @Override public String tableRef(String table) {
        String key = normalize(table);
        if (key.isEmpty()) return null;
        if (tableRefs.containsKey(key)) return tableRefs.get(key);
        String resolved = resolve(table);
        tableRefs.put(key, resolved);
        return resolved;
    }

    @Override public List<String> columns(String table) {
        String key = normalize(table);
        List<String> cached = columns.get(key);
        if (cached != null) return cached;
        List<String> resolved = readColumns(table);
        columns.put(key, resolved);
        return resolved;
    }

    /** Last metadata failure, without connection details; used only to widen a Persian hint. */
    String metadataError() { return metadataError; }

    private String resolve(String table) {
        String wanted = table == null ? "" : table.trim();
        if (wanted.isEmpty()) return null;
        String schemaFilter = wanted.contains(".") ? wanted.substring(0, wanted.indexOf('.')) : "";
        String nameFilter = wanted.contains(".") ? wanted.substring(wanted.indexOf('.') + 1) : wanted;
        String sql = "SELECT sch.name, obj.name FROM sys.objects obj "
                + "JOIN sys.schemas sch ON sch.schema_id=obj.schema_id "
                + "WHERE obj.type IN (N'U',N'V') AND obj.name=? ORDER BY sch.name";
        List<String[]> found = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, nameFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) found.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        } catch (SQLException ex) {
            metadataError = "خواندن ساختار پایگاه داده ممکن نشد.";
            return null;
        }
        if (found.isEmpty()) return null;
        if (!schemaFilter.isEmpty()) {
            for (String[] candidate : found) {
                if (candidate[0].equalsIgnoreCase(schemaFilter)) return quote(candidate[0], candidate[1]);
            }
            return null;
        }
        for (String schema : PREFERRED_SCHEMAS) {
            for (String[] candidate : found) {
                if (candidate[0].equalsIgnoreCase(schema)) return quote(candidate[0], candidate[1]);
            }
        }
        return quote(found.get(0)[0], found.get(0)[1]);
    }

    private List<String> readColumns(String table) {
        String reference = tableRef(table);
        if (reference == null) return List.of();
        String schema = reference.substring(0, reference.indexOf('.'));
        String name = reference.substring(reference.indexOf('.') + 1);
        name = name.substring(1, name.length() - 1).replace("]]", "]");
        String sql = "SELECT col.name FROM sys.columns col "
                + "JOIN sys.objects obj ON obj.object_id=col.object_id "
                + "JOIN sys.schemas sch ON sch.schema_id=obj.schema_id "
                + "WHERE sch.name=? AND obj.type IN (N'U',N'V') AND obj.name=? ORDER BY col.column_id";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setQueryTimeout(15);
            ps.setString(1, schema);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            metadataError = "خواندن ستون‌های «" + name + "» ممکن نشد.";
            return List.of();
        }
        return List.copyOf(result);
    }

    private static String quote(String schema, String name) {
        return schema + ".[" + name.replace("]", "]]") + "]";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
