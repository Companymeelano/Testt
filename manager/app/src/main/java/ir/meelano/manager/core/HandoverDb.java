package ir.meelano.manager.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared hand-over state: which sales invoices the warehouse has handed over.
 *
 * Atiran's own schema has no delivery flag on sailfact — حواله is a money
 * transfer, not a warehouse exit — so there is nowhere to record this inside the
 * existing tables. Until now the marks lived only in each device's own storage
 * (see WarehouseWriter), which is fine on the keeper's phone but useless on the
 * shop TV: a second device sees nothing and every invoice reads as outstanding.
 *
 * So we keep our own side table. It is additive — it is created if absent and
 * nothing in Atiran is read, altered or triggered by it — and dropping it loses
 * nothing but the tick history. The local marks stay as the source of truth for
 * offline work and as the fallback whenever this table cannot be reached, so a
 * device with no CREATE right keeps behaving exactly as it does today.
 */
public final class HandoverDb {
    private HandoverDb() { }

    /** Our own table, clearly named so nobody mistakes it for Atiran's. */
    public static final String TABLE = "MeelanoHandover";

    /**
     * Create the table if it is missing. Safe to call repeatedly; does nothing
     * once it exists. Swallows every failure — a device that may not create
     * tables simply carries on with its local marks.
     */
    public static void ensure(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='"
                    + TABLE + "' AND xtype='U') CREATE TABLE dbo." + TABLE + " ("
                    + "[day] char(10) NOT NULL, "
                    + "[no] nvarchar(50) NOT NULL, "
                    + "[cust] nvarchar(250) NULL, "
                    + "[receiver] nvarchar(250) NULL, "
                    + "[worker] nvarchar(250) NULL, "
                    + "[items] nvarchar(max) NULL, "
                    + "[username] nvarchar(100) NULL, "
                    + "[device] nvarchar(100) NULL, "
                    + "[ts] bigint NOT NULL, "
                    + "CONSTRAINT PK_" + TABLE + " PRIMARY KEY ([day],[no]))");
        } catch (Exception ignored) { }
    }

    /** True when the table is present and readable. */
    public static boolean available(Connection c) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TOP (1) [no] FROM dbo." + TABLE)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Record one hand-over. The first record for a day+invoice wins, so a retry
     * or a second device cannot overwrite who actually received the goods.
     */
    public static void mark(Connection c, String day, String no, String cust,
            String receiver, String worker, String items, String user, String device) {
        try {
            ensure(c);
            try (PreparedStatement ps = c.prepareStatement(
                    "IF NOT EXISTS (SELECT 1 FROM dbo." + TABLE + " WHERE [day]=? AND [no]=?) "
                            + "INSERT INTO dbo." + TABLE
                            + " ([day],[no],[cust],[receiver],[worker],[items],[username],[device],[ts])"
                            + " VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, day);
                ps.setString(2, no);
                ps.setString(3, day);
                ps.setString(4, no);
                ps.setString(5, str(cust));
                ps.setString(6, str(receiver));
                ps.setString(7, str(worker));
                ps.setString(8, str(items));
                ps.setString(9, str(user));
                ps.setString(10, str(device));
                ps.setLong(11, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception ignored) { }
    }

    /**
     * Invoice numbers handed over on this day, or null when the table is
     * unreachable — the caller then falls back to the device's own marks.
     */
    public static Set<String> markedSet(Connection c, String day) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT [no] FROM dbo." + TABLE + " WHERE [day]=?")) {
            ps.setString(1, day);
            Set<String> out = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null) out.add(v.trim());
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
