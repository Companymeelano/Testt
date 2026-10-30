package ir.meelano.manager.core;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Transactional, idempotent writer for the visitor pre-invoice inbox. */
public final class VisitorPrefactorWriter {
    private VisitorPrefactorWriter() { }

    public static final class Line {
        public final String code, name, unit;
        public final BigDecimal quantity, price;
        public final int priceGroup;
        public final BigDecimal stock;

        public Line(String code, String name, String unit, BigDecimal quantity, BigDecimal price,
                    int priceGroup, BigDecimal stock) {
            this.code = code == null ? "" : code.trim();
            this.name = name == null ? "" : name.trim();
            this.unit = unit == null ? "" : unit.trim();
            this.quantity = quantity;
            this.price = price;
            this.priceGroup = priceGroup;
            this.stock = stock == null ? BigDecimal.ZERO : stock;
        }

        public BigDecimal total() { return quantity.multiply(price).setScale(2, BigDecimal.ROUND_HALF_UP); }
    }

    public static final class Receipt {
        public final long id;
        public final boolean duplicate;
        public final BigDecimal total;
        Receipt(long id, boolean duplicate, BigDecimal total) {
            this.id = id; this.duplicate = duplicate; this.total = total;
        }
    }

    public static Receipt submit(Connection c, VisitorIdentity.Identity visitor, String clientUuid,
                                 String customerCode, String customerName, List<Line> lines,
                                 String note) throws Exception {
        if (visitor == null || visitor.userId <= 0 || visitor.visitorId <= 0)
            throw new Exception("هویت ویزیتور آتیران تأیید نشده است.");
        if (clientUuid == null || clientUuid.trim().isEmpty() || clientUuid.length() > 80)
            throw new Exception("شناسهٔ پیش‌فاکتور معتبر نیست.");
        if (customerCode == null || customerCode.trim().isEmpty() || customerName == null || customerName.trim().isEmpty())
            throw new Exception("مشتری پیش‌فاکتور مشخص نیست.");
        if (lines == null || lines.isEmpty()) throw new Exception("پیش‌فاکتور دست‌کم باید یک قلم داشته باشد.");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Line l : lines) {
            if (l == null || l.code.isEmpty() || l.name.isEmpty() || l.quantity == null
                    || l.quantity.signum() <= 0 || l.quantity.compareTo(new BigDecimal("100000")) > 0
                    || l.price == null || l.price.signum() <= 0 || l.price.scale() > 2)
                throw new Exception("یکی از اقلام یا نرخ آن معتبر نیست؛ پیش‌فاکتور تغییری نکرد.");
            subtotal = subtotal.add(l.total());
        }
        if (subtotal.signum() <= 0) throw new Exception("جمع پیش‌فاکتور باید مثبت باشد.");
        VisitorAtiranRules.requireCustomerScope(c,visitor,customerCode);
        VisitorAtiranRules.requirePrices(c,visitor,lines);

        boolean auto = c.getAutoCommit();
        int isolation = c.getTransactionIsolation();
        try {
            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            c.setAutoCommit(false);
            try (PreparedStatement find = c.prepareStatement(
                    "SELECT TOP (1) id, ISNULL(grand_total,0) FROM dbo.meelano_prefactors WITH (UPDLOCK,HOLDLOCK) WHERE client_uuid=?")) {
                find.setString(1, clientUuid);
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        Receipt existing = new Receipt(rs.getLong(1), true, rs.getBigDecimal(2));
                        c.commit();
                        return existing;
                    }
                }
            }
            long id;
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO dbo.meelano_prefactors(client_uuid,visitor_username,visitor_id,customer_code,customer_name,notes,total_amount,subtotal_amount,global_discount,tax_percent,tax_amount,grand_total,status,ready_for_invoice,created_at) "
                            + "OUTPUT INSERTED.id VALUES(?,?,?,?,?,?,?, ?,0,0,0,?,N'pending_approval',0,SYSDATETIME())")) {
                insert.setString(1, clientUuid);
                insert.setString(2, visitor.username);
                insert.setString(3, String.valueOf(visitor.visitorId));
                insert.setString(4, customerCode.trim());
                insert.setString(5, customerName.trim());
                insert.setString(6, note == null ? "" : note.trim());
                insert.setBigDecimal(7, subtotal);
                insert.setBigDecimal(8, subtotal);
                insert.setBigDecimal(9, subtotal);
                try (ResultSet rs = insert.executeQuery()) {
                    if (!rs.next()) throw new Exception("شمارهٔ پیش‌فاکتور از پایگاه‌داده برنگشت.");
                    id = rs.getLong(1);
                }
            }
            try (PreparedStatement item = c.prepareStatement(
                    "INSERT INTO dbo.meelano_prefactor_items(prefactor_id,product_code,product_name,unit,qty,price,amount,stock_snapshot,pack_count,price_tier,line_discount,line_note) "
                            + "VALUES(?,?,?,?,?,?,?,?,?, ?,0,N'')")) {
                for (Line l : lines) {
                    item.setLong(1, id);
                    item.setString(2, l.code);
                    item.setString(3, l.name);
                    item.setString(4, l.unit);
                    item.setBigDecimal(5, l.quantity);
                    item.setBigDecimal(6, l.price);
                    item.setBigDecimal(7, l.total());
                    item.setBigDecimal(8, l.stock);
                    item.setBigDecimal(9, l.quantity);
                    item.setString(10, String.valueOf(l.priceGroup));
                    item.addBatch();
                }
                int[] counts = item.executeBatch();
                if (counts.length != lines.size()) throw new Exception("تمام اقلام پیش‌فاکتور ثبت نشدند.");
                for (int n : counts) if (n == Statement.EXECUTE_FAILED) throw new Exception("ثبت یکی از اقلام پیش‌فاکتور ناموفق بود.");
            }
            c.commit();
            return new Receipt(id, false, subtotal);
        } catch (Exception e) {
            try { c.rollback(); } catch (Exception ignored) { Diag.ignored("writer:rollback", ignored); }
            throw e;
        } finally {
            try { c.setTransactionIsolation(isolation); } catch (Exception ignored) { Diag.ignored("writer:rollback", ignored); }
            try { c.setAutoCommit(auto); } catch (Exception ignored) { Diag.ignored("writer:rollback", ignored); }
        }
    }

    /** Creates only the two app-owned tables needed by this workflow; never alters Atiran tables. */
    public static void ensureTables(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("IF OBJECT_ID(N'dbo.meelano_prefactors',N'U') IS NULL CREATE TABLE dbo.meelano_prefactors(" +
                    "id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,client_uuid nvarchar(80) NULL,visitor_username nvarchar(160) NULL," +
                    "visitor_id nvarchar(80) NULL,customer_code nvarchar(100) NULL,customer_name nvarchar(250) NULL,notes nvarchar(max) NULL," +
                    "signature_data nvarchar(max) NULL,total_amount decimal(19,2) NULL,subtotal_amount decimal(19,2) NULL," +
                    "global_discount decimal(19,2) NULL,tax_percent decimal(9,3) NULL,tax_amount decimal(19,2) NULL,grand_total decimal(19,2) NULL," +
                    "status nvarchar(30) NOT NULL CONSTRAINT DF_meelano_prefactors_status DEFAULT(N'draft'),approval_reason nvarchar(700) NULL," +
                    "delivery_date nvarchar(40) NULL,delivery_address nvarchar(700) NULL,settlement_type nvarchar(80) NULL,payment_ref nvarchar(160) NULL," +
                    "created_at datetime2 NOT NULL CONSTRAINT DF_meelano_prefactors_created DEFAULT(SYSDATETIME()),updated_at datetime2 NULL," +
                    "ready_for_invoice bit NOT NULL CONSTRAINT DF_meelano_prefactors_ready DEFAULT(0),invoice_status nvarchar(40) NULL," +
                    "invoice_number nvarchar(80) NULL,invoice_at datetime2 NULL,system_convert_note nvarchar(700) NULL," +
                    "native_prefactor_table nvarchar(120) NULL,native_prefactor_no nvarchar(120) NULL,native_sync_at datetime2 NULL)");
            st.execute("IF OBJECT_ID(N'dbo.meelano_prefactor_items',N'U') IS NULL CREATE TABLE dbo.meelano_prefactor_items(" +
                    "id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,prefactor_id bigint NOT NULL,product_code nvarchar(100) NULL," +
                    "product_name nvarchar(500) NULL,unit nvarchar(80) NULL,qty decimal(19,4) NULL,price decimal(19,2) NULL," +
                    "amount decimal(19,2) NULL,stock_snapshot decimal(19,4) NULL,pack_count decimal(19,4) NULL,price_tier nvarchar(10) NULL," +
                    "line_discount decimal(19,2) NULL,line_note nvarchar(700) NULL)");
        }
    }
}
