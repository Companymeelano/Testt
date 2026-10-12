package ir.meelano.manager.data;

import ir.meelano.manager.core.AtiranSchema;
import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.Sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Live database metadata with caching. Every query resolves the columns it
 * needs through here, so the app adapts to the real Atiran database instead
 * of assuming a fixed schema.
 */
public final class Meta {
    private final Connection c;
    private final Map<String, Set<String>> cols = new HashMap<>();
    private final Map<String, Boolean> exists = new HashMap<>();
    private final Map<String, Boolean> functions = new HashMap<>();

    public Meta(Connection c) {
        this.c = c;
    }

    /** True when dbo.name is a table OR a view. */
    public boolean table(String name) {
        Boolean hit = exists.get(name.toLowerCase(java.util.Locale.US));
        if (hit != null) return hit;
        boolean ok = false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id " +
                        "WHERE s.name=N'dbo' AND o.type IN (N'U',N'V') AND o.name=?")) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) { ok = r.next(); }
        } catch (Exception ignored) { }
        exists.put(name.toLowerCase(java.util.Locale.US), ok);
        return ok;
    }

    /** Column names of a table/view (empty set when absent). */
    public Set<String> columns(String table) {
        String k = table.toLowerCase(java.util.Locale.US);
        Set<String> hit = cols.get(k);
        if (hit != null) return hit;
        Set<String> set = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT c.name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id " +
                        "JOIN sys.schemas s ON s.schema_id=o.schema_id " +
                        "WHERE s.name=N'dbo' AND o.type IN (N'U',N'V') AND o.name=? ORDER BY c.column_id")) {
            ps.setString(1, table);
            try (ResultSet r = ps.executeQuery()) { while (r.next()) set.add(r.getString(1)); }
        } catch (Exception ignored) { }
        cols.put(k, set);
        return set;
    }

    public boolean function(String name) {
        Boolean hit = functions.get(name.toLowerCase(java.util.Locale.US));
        if (hit != null) return hit;
        boolean ok = false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM sys.objects WHERE name=? AND type IN (N'FN',N'TF',N'IF')")) {
            ps.setString(1, name);
            try (ResultSet r = ps.executeQuery()) { ok = r.next(); }
        } catch (Exception ignored) { }
        functions.put(name.toLowerCase(java.util.Locale.US), ok);
        return ok;
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
        if (dateCol == null || !table(table)) return "";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT MAX(LEFT(TRY_CONVERT(nvarchar(30),[" + dateCol.replace("]", "") + "]),10)) FROM dbo.[" + table + "]")) {
            try (ResultSet r = ps.executeQuery()) {
                if (r.next()) {
                    String v = r.getString(1);
                    return v == null ? "" : v.trim();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    public Connection connection() {
        return c;
    }
}
