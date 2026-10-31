package ir.meelano.android;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small SQL Server metadata resolver used by the production-only module. */
final class ProductionMeta {
    private final Connection connection;
    private final Map<String, String> tables = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> columns = new LinkedHashMap<>();

    ProductionMeta(Connection connection) { this.connection = connection; }

    boolean table(String name) throws Exception { return qTable(name) != null; }

    String qTable(String name) throws Exception {
        String key = key(name);
        if (tables.containsKey(key)) return tables.get(key);
        String found = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT TOP (1) s.name,o.name FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id " +
                        "WHERE o.type IN (N'U',N'V') AND LOWER(o.name)=LOWER(?) " +
                        "ORDER BY CASE WHEN LOWER(s.name)=N'dbo' THEN 0 ELSE 1 END,s.name,o.name")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) found = quote(rs.getString(1)) + "." + quote(rs.getString(2));
            }
        }
        tables.put(key, found);
        return found;
    }

    String col(String table, String... candidates) throws Exception {
        Map<String, String> found = columns(table);
        for (String candidate : candidates) {
            String exact = found.get(key(candidate));
            if (exact != null) return exact;
        }
        return null;
    }

    private Map<String, String> columns(String table) throws Exception {
        String key = key(table);
        Map<String, String> cached = columns.get(key);
        if (cached != null) return cached;
        Map<String, String> out = new LinkedHashMap<>();
        String full = qTable(table);
        if (full == null) { columns.put(key, out); return out; }
        String[] parts = full.split("\\.", 2);
        String schema = parts[0].substring(1, parts[0].length()-1).replace("]]", "]");
        String object = parts[1].substring(1, parts[1].length()-1).replace("]]", "]");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT c.name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id " +
                        "JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=? AND o.name=?")) {
            ps.setString(1, schema); ps.setString(2, object);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { String name=rs.getString(1); if(name!=null)out.put(key(name),name); } }
        }
        columns.put(key, out);
        return out;
    }

    static String quote(String identifier) {
        return "[" + (identifier == null ? "" : identifier.replace("]", "]]")) + "]";
    }

    static String key(String value) { return value == null ? "" : value.toLowerCase(Locale.US); }
}
