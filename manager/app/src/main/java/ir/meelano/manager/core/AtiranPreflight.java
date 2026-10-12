package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only structural preflight for the manager's core Atiran sections. */
public final class AtiranPreflight {
    private AtiranPreflight() { }

    private static final class Need {
        final String table;
        final String title;
        final String[][] fields;

        Need(String table, String title, String[][] fields) {
            this.table = table;
            this.title = title;
            this.fields = fields;
        }
    }

    private static final class Shape {
        final String schema;
        final String name;
        final Set<String> columns = new HashSet<>();
        boolean canSelect;

        Shape(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }
    }

    // Each field entry is: Persian label, then accepted Atiran column names.
    // These are the minimum inputs used by the matching query builders; absence
    // is reported, not treated as a failed connection or a reason to write data.
    private static final Need[] CORE = {
            new Need("sailfact", "فروش", new String[][]{
                    {"تاریخ", "date"}, {"شماره فاکتور", "shfacfo"}, {"طرف‌حساب", "shmo", "SHMO"},
                    {"مبلغ", "all"}, {"وضعیت تسویه", "tasvieh"}, {"تاریخ تسویه", "t_date"}}),
            new Need("subsailfact", "اقلام فروش", new String[][]{
                    {"شماره فاکتور", "shfacfo"}, {"کد کالا", "SHKA", "shka"},
                    {"تعداد", "TEDVAH", "tedvah"}, {"جمع قلم", "LINESUM", "linesum"}}),
            new Need("buyfact", "خرید", new String[][]{
                    {"تاریخ", "DATE", "date"}, {"شماره فاکتور", "shfackh"},
                    {"طرف‌حساب", "shmo", "SHMO"}, {"مبلغ", "all"}}),
            new Need("dar", "دریافت و پرداخت", new String[][]{
                    {"شماره قبض", "ghno"}, {"تاریخ", "date"}, {"طرف‌حساب", "shmo", "SHMO"},
                    {"نوع قبض", "p"}, {"مبلغ", "mab"}}),
            new Need("PosDetails", "کارت‌خوان و حواله", new String[][]{
                    {"شماره قبض", "ghno"}, {"مبلغ کارت", "MabPos", "mabPos"},
                    {"شناسه بانک", "PosBankRdf", "BankRdf"}}),
            new Need("getchk", "چک‌های دریافتی", new String[][]{
                    {"شناسه چک", "rdf"}, {"شماره چک", "getchkshhes"},
                    {"وضعیت", "chk_satus"}, {"مبلغ", "getchkmab"}}),
            new Need("putchk", "چک‌های پرداختی", new String[][]{
                    {"شناسه چک", "rdf"}, {"شماره چک", "shputchk"},
                    {"وضعیت", "putchk_status"}, {"مبلغ", "putchkmab"}}),
            new Need("CUSTOMERS", "مشتریان", new String[][]{
                    {"کد مشتری", "SHMO", "shmo"}, {"نام مشتری", "MONAME", "name"}}),
            new Need("inventory", "کالاها", new String[][]{
                    {"کد کالا", "shka", "SHKA"}, {"نام کالا", "naka", "NAKA"},
                    {"موجودی", "mojkavah", "MojKavah"}}),
            new Need("inventory_anbars", "موجودی انبار", new String[][]{
                    {"کد کالا", "shka", "SHKA"}, {"انبار", "rdf_anbars", "rdf_anbar"}}),
            new Need("visitors", "ویزیتورها", new String[][]{
                    {"کد ویزیتور", "vis_rdf", "rdf"}, {"نام ویزیتور", "vis_name", "name"}}),
            new Need("sys_users", "کاربران آتیران", new String[][]{
                    {"شناسه کاربر", "user_id", "UserID"}, {"نام کاربری", "user_name", "username"}}),
    };

    /** Immutable result suitable for the connection-test UI. */
    public static final class Report {
        public final int readySections;
        public final int totalSections;
        public final List<String> issues;

        private Report(int readySections, int totalSections, List<String> issues) {
            this.readySections = readySections;
            this.totalSections = totalSections;
            this.issues = new ArrayList<>(issues);
        }

        public boolean complete() {
            return issues.isEmpty();
        }

        public String persianSummary() {
            if (complete())
                return "اتصال برقرار شد • ساختار پایه " + Money.fa(readySections + "/" + totalSections) + " بخش آماده است";
            StringBuilder out = new StringBuilder("اتصال برقرار شد • ساختار پایه ")
                    .append(Money.fa(readySections + "/" + totalSections)).append(" بخش آماده است؛ بررسی: ");
            int show = Math.min(3, issues.size());
            for (int i = 0; i < show; i++) {
                if (i > 0) out.append("، ");
                out.append(issues.get(i));
            }
            if (issues.size() > show) out.append(" و ").append(Money.fa(String.valueOf(issues.size() - show))).append(" مورد دیگر");
            out.append("؛ نام دیتابیس و مجوزهای مشاهده ساختار (VIEW DEFINITION) و خواندن (SELECT) را بررسی کنید");
            return out.toString();
        }
    }

    /**
     * One narrow, read-only catalog query checks all core objects and fields.
     * No COUNT(*) or other business-row query is executed.
     */
    public static Report inspect(Connection connection) {
        Map<String, Shape> shapes = new HashMap<>();
        StringBuilder query = new StringBuilder(
                "SELECT s.name,o.name,c.name,CONVERT(int,COALESCE(HAS_PERMS_BY_NAME(QUOTENAME(s.name)+N'.'+QUOTENAME(o.name),N'OBJECT',N'SELECT'),0)) FROM sys.objects o "
                        + "JOIN sys.schemas s ON s.schema_id=o.schema_id "
                        + "LEFT JOIN sys.columns c ON c.object_id=o.object_id "
                        + "WHERE o.type IN (N'U',N'V') AND o.is_ms_shipped=0 AND LOWER(o.name) IN (");
        for (int i = 0; i < CORE.length; i++) {
            if (i > 0) query.append(',');
            query.append('?');
        }
        query.append(") ORDER BY CASE WHEN LOWER(s.name)=N'dbo' THEN 0 ELSE 1 END,s.name,o.name,c.column_id");

        try (PreparedStatement ps = connection.prepareStatement(query.toString())) {
            for (int i = 0; i < CORE.length; i++)
                ps.setString(i + 1, CORE[i].table.toLowerCase(Locale.US));
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    String schema = rows.getString(1);
                    String name = rows.getString(2);
                    String key = name.toLowerCase(Locale.US);
                    Shape shape = shapes.get(key);
                    if (shape == null) {
                        shape = new Shape(schema, name);
                        shapes.put(key, shape); // dbo comes first; otherwise first matching schema wins.
                    }
                    if (shape.schema.equalsIgnoreCase(schema)) {
                        shape.canSelect = rows.getInt(4) == 1;
                        if (rows.getString(3) != null) shape.columns.add(rows.getString(3));
                    }
                }
            }
        } catch (Exception e) {
            throw new Meta.MetadataException(e);
        }

        List<String> issues = new ArrayList<>();
        int ready = 0;
        for (Need need : CORE) {
            Shape shape = shapes.get(need.table.toLowerCase(Locale.US));
            if (shape == null) {
                issues.add(need.title + ": جدول موجود نیست");
                continue;
            }
            if (!shape.canSelect) {
                issues.add(need.title + ": مجوز خواندن SELECT ندارد");
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String[] field : need.fields) {
                String[] candidates = Arrays.copyOfRange(field, 1, field.length);
                if (Sql.pickFlex(shape.columns, candidates) == null) missing.add(field[0]);
            }
            if (missing.isEmpty()) {
                ready++;
            } else {
                issues.add(need.title + ": ستون " + join(missing));
            }
        }
        return new Report(ready, CORE.length, issues);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("/ ");
            out.append(value);
        }
        return out.toString();
    }
}
