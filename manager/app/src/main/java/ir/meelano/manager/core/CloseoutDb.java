package ir.meelano.manager.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

/**
 * Additive persistence for the manager's day-close snapshot and append-only audit history.
 * These tables are Meelano side tables; no Atiran sales, money, cheque, return, or stock row
 * is changed by a closeout.
 */
public final class CloseoutDb {
    private CloseoutDb() { }

    public static final String TABLE = "MeelanoDailyClose";
    public static final String HISTORY_TABLE = "MeelanoDailyCloseHistory";

    /** Create only Meelano-owned tables. If the account lacks DDL permission, the UI stays read-only. */
    public static void ensure(Connection c) {
        ensureCurrent(c);
        ensureHistory(c);
    }

    private static void ensureCurrent(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='" + TABLE
                    + "' AND xtype='U') CREATE TABLE dbo." + TABLE + " ("
                    + "[day] char(10) NOT NULL PRIMARY KEY, "
                    + "[username] nvarchar(120) NULL, "
                    + "[note] nvarchar(1000) NULL, "
                    + "[snapshot] nvarchar(max) NOT NULL, "
                    + "[closedAt] bigint NOT NULL)");
        } catch (Exception ignored) { Diag.ignored("CloseoutDb:39", ignored); }
    }

    private static void ensureHistory(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='" + HISTORY_TABLE
                    + "' AND xtype='U') CREATE TABLE dbo." + HISTORY_TABLE + " ("
                    + "[historyId] bigint IDENTITY(1,1) NOT NULL PRIMARY KEY, "
                    + "[day] char(10) NOT NULL, "
                    + "[username] nvarchar(120) NULL, "
                    + "[note] nvarchar(1000) NULL, "
                    + "[action] varchar(16) NOT NULL, "
                    + "[snapshot] nvarchar(max) NOT NULL, "
                    + "[closedAt] bigint NOT NULL)");
        } catch (Exception ignored) { Diag.ignored("CloseoutDb:53", ignored); }
    }

    public static boolean available(Connection c) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (1) [day] FROM dbo." + TABLE)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean historyAvailable(Connection c) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (1) [historyId] FROM dbo." + HISTORY_TABLE)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Current day-close record. */
    public static JSONObject find(Connection c, String day) {
        try {
            return findOrThrow(c, Jalali.normalizeDate(day));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject findOrThrow(Connection c, String day) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) [username],[note],[snapshot],[closedAt]"
                + " FROM dbo." + TABLE + " WHERE [day]=?")) {
            ps.setString(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                JSONObject o = new JSONObject();
                o.put("username", safe(rs.getString("username")));
                o.put("note", safe(rs.getString("note")));
                o.put("snapshot", safe(rs.getString("snapshot")));
                o.put("closedAt", rs.getLong("closedAt"));
                return o;
            }
        }
    }

    /** Most recent immutable snapshots for one Jalali day, newest first. */
    public static List<JSONObject> history(Connection c, String day, int limit) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        String d = Jalali.normalizeDate(day);
        int top = Math.max(1, Math.min(30, limit));
        String sql = "SELECT TOP (" + top + ") [historyId],[day],[username],[note],[action],[snapshot],[closedAt]"
                + " FROM dbo." + HISTORY_TABLE + " WHERE [day]=? ORDER BY [historyId] DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("historyId", rs.getLong("historyId"));
                    o.put("day", safe(rs.getString("day")));
                    o.put("username", safe(rs.getString("username")));
                    o.put("note", safe(rs.getString("note")));
                    o.put("action", safe(rs.getString("action")));
                    o.put("snapshot", safe(rs.getString("snapshot")));
                    o.put("closedAt", rs.getLong("closedAt"));
                    out.add(o);
                }
            }
        }
        return out;
    }

    /**
     * Insert the first close or an audited amendment. With history-table access, the history
     * insert and current snapshot replacement commit as one SQL transaction. If the history
     * table cannot be used, only a first close is allowed; an existing close is never silently
     * overwritten without preserving its prior snapshot.
     */
    public static boolean close(Connection c, String day, String username,
            String note, JSONObject snapshot) {
        if (c == null || snapshot == null) return false;
        String d = Jalali.normalizeDate(day);
        String who = safe(username);
        String memo = safe(note);
        String json = snapshot.toString();
        try {
            ensure(c);
            if (!available(c)) return false;
            if (!historyAvailable(c)) return insertFirstWithoutHistory(c, d, who, memo, json);

            boolean originalAutoCommit = c.getAutoCommit();
            Savepoint savepoint = null;
            boolean transactionStarted = false;
            try {
                if (originalAutoCommit) c.setAutoCommit(false);
                else savepoint = c.setSavepoint("meelano_closeout");
                transactionStarted = true;

                JSONObject previous = findOrThrow(c, d);
                if (previous != null && !hasHistory(c, d)) {
                    // Preserve a close created by older app versions before writing its amendment.
                    insertHistory(c, d, previous.optString("username", ""),
                            previous.optString("note", ""), "legacy",
                            previous.optString("snapshot", "{}"), previous.optLong("closedAt", 0));
                }

                long now = System.currentTimeMillis();
                insertHistory(c, d, who, memo, previous == null ? "close" : "amend", json, now);
                upsertCurrent(c, d, who, memo, json, now);

                JSONObject saved = findOrThrow(c, d);
                if (!matches(saved, who, memo, json, now) || !latestHistoryMatches(c, d, who, memo, json, now))
                    throw new java.sql.SQLException("ثبت snapshot یا تاریخچه تأیید نشد");

                if (originalAutoCommit) c.commit();
                return true;
            } catch (Exception e) {
                if (transactionStarted) {
                    try {
                        if (originalAutoCommit) c.rollback();
                        else if (savepoint != null) c.rollback(savepoint);
                    } catch (Exception ignored) { Diag.ignored("CloseoutDb:174", ignored); }
                }
                return false;
            } finally {
                if (originalAutoCommit) {
                    try { c.setAutoCommit(true); } catch (Exception ignored) { Diag.ignored("CloseoutDb:179", ignored); }
                }
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean insertFirstWithoutHistory(Connection c, String day, String username,
            String note, String snapshot) {
        try {
            // Compatibility for restricted Atiran logins: allow an initial close, but do not
            // permit replacing a pre-existing snapshot until audit history is available.
            if (findOrThrow(c, day) != null) return false;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO dbo." + TABLE
                    + " ([day],[username],[note],[snapshot],[closedAt]) VALUES (?,?,?,?,?)")) {
                ps.setString(1, day);
                ps.setString(2, username);
                ps.setString(3, note);
                ps.setString(4, snapshot);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return findOrThrow(c, day) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void insertHistory(Connection c, String day, String username, String note,
            String action, String snapshot, long closedAt) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO dbo." + HISTORY_TABLE
                + " ([day],[username],[note],[action],[snapshot],[closedAt]) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, day);
            ps.setString(2, safe(username));
            ps.setString(3, safe(note));
            ps.setString(4, action);
            ps.setString(5, snapshot == null ? "{}" : snapshot);
            ps.setLong(6, Math.max(0L, closedAt));
            ps.executeUpdate();
        }
    }

    private static boolean hasHistory(Connection c, String day) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) [historyId] FROM dbo." + HISTORY_TABLE
                + " WHERE [day]=?")) {
            ps.setString(1, day);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void upsertCurrent(Connection c, String day, String username,
            String note, String snapshot, long closedAt) throws Exception {
        String sql = "UPDATE dbo." + TABLE + " WITH (UPDLOCK,HOLDLOCK)"
                + " SET [username]=?,[note]=?,[snapshot]=?,[closedAt]=? WHERE [day]=?;"
                + " IF @@ROWCOUNT=0 INSERT INTO dbo." + TABLE
                + " ([day],[username],[note],[snapshot],[closedAt]) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, note);
            ps.setString(3, snapshot);
            ps.setLong(4, closedAt);
            ps.setString(5, day);
            ps.setString(6, day);
            ps.setString(7, username);
            ps.setString(8, note);
            ps.setString(9, snapshot);
            ps.setLong(10, closedAt);
            ps.executeUpdate();
        }
    }

    private static boolean latestHistoryMatches(Connection c, String day, String username,
            String note, String snapshot, long closedAt) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT TOP (1) [username],[note],[snapshot],[closedAt]"
                + " FROM dbo." + HISTORY_TABLE + " WHERE [day]=? ORDER BY [historyId] DESC")) {
            ps.setString(1, day);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && safe(username).equals(safe(rs.getString("username")))
                        && safe(note).equals(safe(rs.getString("note")))
                        && snapshot.equals(safe(rs.getString("snapshot")))
                        && closedAt == rs.getLong("closedAt");
            }
        }
    }

    private static boolean matches(JSONObject saved, String username,
            String note, String snapshot, long closedAt) {
        return saved != null && safe(username).equals(safe(saved.optString("username", "")))
                && safe(note).equals(safe(saved.optString("note", "")))
                && snapshot.equals(saved.optString("snapshot", ""))
                && closedAt == saved.optLong("closedAt", 0);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
