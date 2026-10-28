package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/** Server-side authorization and rate checks shared by all visitor write paths. */
final class VisitorAtiranRules {
    private VisitorAtiranRules() { }

    /** The customer must belong to one of the visitors the signed-in account owns. */
    static void requireCustomerScope(Connection c, VisitorIdentity.Identity visitor, String customerCode) throws Exception {
        Meta m = new Meta(c);
        if (!m.table("CUSTOMERS")) throw new Exception("جدول مشتریان آتیران پیدا نشد.");
        String key = m.col("CUSTOMERS", "SHMO", "shmo");
        String vis = m.col("CUSTOMERS", "vis_rdf", "VisitorID", "visitor_id");
        String active = m.col("CUSTOMERS", "active", "Active");
        if (key == null || vis == null || active == null)
            throw new Exception("پیوند مشتری به ویزیتور یا وضعیت فعال‌بودن آن در آتیران قابل بررسی نیست.");
        VisitorData.Owner owner = VisitorData.owner(null, vis, m.columnType("CUSTOMERS", vis), VisitorData.ids(visitor));
        String sql = "SELECT COUNT_BIG(1) FROM " + m.qTable("CUSTOMERS")
                + " WHERE TRY_CONVERT(nvarchar(100)," + q(key) + ")=? AND " + owner.predicate
                + " AND " + VisitorData.activePredicate(null, active);
        try (PreparedStatement ps = VisitorData.read(c, sql)) {
            ps.setString(1, customerCode.trim());
            owner.bind(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong(1) != 1)
                    throw new Exception("این مشتری به مسیر ویزیتور فعلی تخصیص ندارد یا پیوند آن یکتا نیست.");
            }
        }
    }

    /** The one price group Atiran stores for this visitor. */
    static int defaultPriceGroup(Connection c, int visitorId) throws Exception {
        return VisitorData.defaultPriceGroup(c, new int[]{visitorId});
    }

    static void requirePrice(Connection c, int visitorId, VisitorPrefactorWriter.Line line) throws Exception {
        Meta m = new Meta(c);
        int group = defaultPriceGroup(c, visitorId);
        if (group != line.priceGroup)
            throw new Exception("گروه قیمت ویزیتور تغییر کرده است؛ پیش‌فاکتور را با قیمت تازه بازسازی کنید.");
        if (!m.table("forosh_price")) throw new Exception("جدول قیمت فروش آتیران پیدا نشد.");
        String key = m.col("forosh_price", "shka", "SHKA", "ProductID", "KalaID");
        String price = m.col("forosh_price", VisitorPricePolicy.column(group));
        String active = m.col("forosh_price", "active", "Active");
        if (key == null || price == null || active == null)
            throw new Exception("ستون نرخ یا وضعیت فعال‌بودن آن در forosh_price پیدا نشد.");
        String sql = "SELECT COUNT_BIG(1),MIN(TRY_CONVERT(decimal(19,2)," + q(price) + ")) FROM "
                + m.qTable("forosh_price") + " WHERE TRY_CONVERT(nvarchar(100)," + q(key) + ")=?"
                + (active == null ? "" : " AND " + VisitorData.activePredicate(null, active));
        try (PreparedStatement ps = VisitorData.read(c, sql)) {
            ps.setString(1, line.code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !VisitorPricePolicy.isUnambiguous(group, rs.getLong(1), rs.getDouble(2)))
                    throw new Exception("نرخ کالا «" + line.name + "» در آتیران مبهم، غیرفعال یا ناموجود است.");
                BigDecimal actual = rs.getBigDecimal(2);
                if (actual == null || actual.subtract(line.price).abs().compareTo(new BigDecimal("0.01")) > 0)
                    throw new Exception("قیمت «" + line.name + "» تغییر کرده است؛ سبد را تازه‌سازی کنید.");
            }
        }
        String productId = m.col("inventory", "shka", "SHKA", "ProductID");
        if (productId == null) throw new Exception("شناسهٔ کالا در inventory آتیران پیدا نشد.");
        String[] flags = VisitorData.fieldSalesColumns(m);
        String invActive = m.col("inventory", "active", "Active");
        String check = "SELECT COUNT_BIG(1) FROM " + m.qTable("inventory")
                + " WHERE TRY_CONVERT(nvarchar(100)," + q(productId) + ")=?"
                + (invActive == null ? "" : " AND " + VisitorData.activePredicate(null, invActive))
                + (flags.length == 0 ? "" : " AND " + VisitorData.fieldSalesPredicate(null, flags));
        try (PreparedStatement ps = VisitorData.read(c, check)) {
            ps.setString(1, line.code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong(1) != 1)
                    throw new Exception("کالا «" + line.name + "» در فروش میدانی فعال نیست.");
            }
        }
    }

    static void requirePrices(Connection c, VisitorIdentity.Identity visitor, List<VisitorPrefactorWriter.Line> lines) throws Exception {
        int group = VisitorData.defaultPriceGroup(c, VisitorData.ids(visitor));
        for (VisitorPrefactorWriter.Line line : lines) {
            if (line.priceGroup != group) throw new Exception("گروه قیمت ویزیتور تغییر کرده است.");
            requirePrice(c, visitor.visitorId, line);
        }
    }

    static String q(String s) { return "[" + s.replace("]", "]]") + "]"; }
}
