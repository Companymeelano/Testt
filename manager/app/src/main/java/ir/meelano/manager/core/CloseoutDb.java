package ir.meelano.manager.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.json.JSONObject;

/**
 * Additive persistence for a manager's day-close snapshot. This is a Meelano side table,
 * separate from every Atiran sales, money, cheque, return, and stock table.
 */
public final class CloseoutDb {
    private CloseoutDb() { }

    public static final String TABLE = "MeelanoDailyClose";

    public static void ensure(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='" + TABLE
                    + "' AND xtype='U') CREATE TABLE dbo." + TABLE + " ("
                    + "[day] char(10) NOT NULL PRIMARY KEY, "
                    + "[username] nvarchar(120) NULL, "
                    + "[note] nvarchar(1000) NULL, "
                    + "[snapshot] nvarchar(max) NOT NULL, "
                    + "[closedAt] bigint NOT NULL)");
        } catch (Exception ignored) { }
    }

    public static boolean available(Connection c) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (1) [day] FROM dbo." + TABLE)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONObject find(Connection c, String day) {
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) [username],[note],[snapshot],[closedAt]"
                + " FROM dbo." + TABLE + " WHERE [day]=?")) {
            ps.setString(1, Jalali.normalizeDate(day));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                JSONObject o = new JSONObject();
                o.put("username", rs.getString("username"));
                o.put("note", rs.getString("note"));
                o.put("snapshot", rs.getString("snapshot"));
                o.put("closedAt", rs.getLong("closedAt"));
                return o;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Insert or refresh the snapshot for the selected day, then verify the write. */
    public static boolean close(Connection c, String day, String username,
            String note, JSONObject snapshot) {
        String d = Jalali.normalizeDate(day);
        try {
            ensure(c);
            if (!available(c) || snapshot == null) return false;
            try (PreparedStatement ps = c.prepareStatement(
                    "IF EXISTS (SELECT 1 FROM dbo." + TABLE + " WHERE [day]=?) "
                            + "UPDATE dbo." + TABLE + " SET [username]=?,[note]=?,[snapshot]=?,[closedAt]=? WHERE [day]=? "
                            + "ELSE INSERT INTO dbo." + TABLE
                            + " ([day],[username],[note],[snapshot],[closedAt]) VALUES (?,?,?,?,?)")) {
                long now = System.currentTimeMillis();
                ps.setString(1, d);
                ps.setString(2, str(username));
                ps.setString(3, str(note));
                ps.setString(4, snapshot.toString());
                ps.setLong(5, now);
                ps.setString(6, d);
                ps.setString(7, d);
                ps.setString(8, str(username));
                ps.setString(9, str(note));
                ps.setString(10, snapshot.toString());
                ps.setLong(11, now);
                ps.executeUpdate();
            }
            return find(c, d) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String str(String s) { return s == null ? "" : s; }
}
