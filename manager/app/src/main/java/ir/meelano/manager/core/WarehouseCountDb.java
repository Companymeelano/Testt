package ir.meelano.manager.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Additive Meelano review queue for blind warehouse counts. This table is ours; it never
 * guesses or writes to an Atiran inventory-adjustment table. A manager's approval records
 * review only. Posting an inventory adjustment remains a separate, schema-verified step.
 */
public final class WarehouseCountDb {
    private WarehouseCountDb() { }

    public static final String TABLE = "MeelanoWhCount";

    /** Create our own review table if permitted; an offline/no-CREATE device keeps its draft. */
    public static void ensure(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='" + TABLE
                    + "' AND xtype='U') CREATE TABLE dbo." + TABLE + " ("
                    + "[id] nvarchar(120) NOT NULL PRIMARY KEY, "
                    + "[day] char(10) NOT NULL, "
                    + "[anbar] nvarchar(200) NULL, "
                    + "[keeper] nvarchar(120) NULL, "
                    + "[device] nvarchar(40) NULL, "
                    + "[lines] nvarchar(max) NOT NULL, "
                    + "[status] varchar(20) NOT NULL CONSTRAINT DF_" + TABLE
                    + "_status DEFAULT ('pending'), "
                    + "[reviewer] nvarchar(120) NULL, "
                    + "[reviewNote] nvarchar(1000) NULL, "
                    + "[createdAt] bigint NOT NULL, "
                    + "[reviewedAt] bigint NULL)");
        } catch (Exception ignored) { }
    }

    public static boolean available(Connection c) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (1) [id] FROM dbo." + TABLE)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Insert one stable local draft exactly once; true only after the server can read it back. */
    public static boolean submit(Connection c, JSONObject draft) {
        if (draft == null) return false;
        String id = draft.optString("reviewId", "").trim();
        String day = Jalali.normalizeDate(draft.optString(WarehouseWriter.D_DATE, Jalali.todayStr()));
        String keeper = draft.optString(WarehouseWriter.D_USER, "");
        String anbar = draft.optString(WarehouseWriter.D_ANBARNAME, "");
        JSONArray lines = draft.optJSONArray(WarehouseWriter.D_LINES);
        if (id.isEmpty() || lines == null || lines.length() == 0) return false;
        try {
            ensure(c);
            if (!available(c)) return false;
            try (PreparedStatement ps = c.prepareStatement(
                    "IF NOT EXISTS (SELECT 1 FROM dbo." + TABLE + " WHERE [id]=?) "
                            + "INSERT INTO dbo." + TABLE
                            + " ([id],[day],[anbar],[keeper],[device],[lines],[status],[createdAt])"
                            + " VALUES (?,?,?,?,?,?,N'pending',?)")) {
                ps.setString(1, id);
                ps.setString(2, id);
                ps.setString(3, day);
                ps.setString(4, anbar);
                ps.setString(5, keeper);
                ps.setString(6, draft.optString("device", id.substring(0, Math.min(40, id.length()))));
                ps.setString(7, lines.toString());
                ps.setLong(8, draft.optLong(WarehouseWriter.D_CREATED, System.currentTimeMillis()));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT TOP (1) [status] FROM dbo." + TABLE + " WHERE [id]=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Pending manager reviews, oldest first so nobody's count gets buried. */
    public static List<JSONObject> pending(Connection c) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (300) [id],[day],[anbar],[keeper],[device],"
                     + "[lines],[status],[reviewNote],[createdAt] FROM dbo." + TABLE
                     + " WHERE [status]='pending' ORDER BY [createdAt] ASC")) {
            while (rs.next()) {
                JSONObject o = new JSONObject();
                o.put("id", rs.getString("id"));
                o.put("day", rs.getString("day"));
                o.put("anbar", rs.getString("anbar"));
                o.put("keeper", rs.getString("keeper"));
                o.put("device", rs.getString("device"));
                o.put("lines", rs.getString("lines"));
                o.put("status", rs.getString("status"));
                o.put("reviewNote", rs.getString("reviewNote"));
                o.put("createdAt", rs.getLong("createdAt"));
                out.add(o);
            }
        }
        return out;
    }

    /** Recent count submissions from one device, including the manager's decision. */
    public static List<JSONObject> mine(Connection c, String device) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (50) [id],[day],[anbar],[keeper],"
                + "[status],[reviewer],[reviewNote],[createdAt],[reviewedAt] FROM dbo." + TABLE
                + " WHERE [device]=? ORDER BY [createdAt] DESC")) {
            ps.setString(1, device == null ? "" : device);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getString("id"));
                    o.put("day", rs.getString("day"));
                    o.put("anbar", rs.getString("anbar"));
                    o.put("keeper", rs.getString("keeper"));
                    o.put("status", rs.getString("status"));
                    o.put("reviewer", rs.getString("reviewer"));
                    o.put("reviewNote", rs.getString("reviewNote"));
                    o.put("createdAt", rs.getLong("createdAt"));
                    o.put("reviewedAt", rs.getLong("reviewedAt"));
                    out.add(o);
                }
            }
        }
        return out;
    }

    /** Approve or reject the review record; never changes an Atiran stock balance. */
    public static boolean review(Connection c, String id, boolean approve,
            String reviewer, String note) {
        if (id == null || id.trim().isEmpty()) return false;
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE dbo." + TABLE + " SET [status]=?,[reviewer]=?,[reviewNote]=?,[reviewedAt]=?"
                            + " WHERE [id]=? AND [status]='pending'")) {
                ps.setString(1, approve ? "approved" : "rejected");
                ps.setString(2, reviewer == null ? "" : reviewer);
                ps.setString(3, note == null ? "" : note);
                ps.setLong(4, System.currentTimeMillis());
                ps.setString(5, id);
                return ps.executeUpdate() > 0;
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
