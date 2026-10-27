package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Row;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** High-priority reports that require careful interpretation of Atiran's stock and HR fields. */
public final class AtiranSpecialReports {
    private AtiranSpecialReports() { }

    private static String boolTrue(String field) {
        String n = "UPPER(LTRIM(RTRIM(TRY_CONVERT(nvarchar(20)," + field + "))))";
        return "(" + n + " IN (N'T',N'TRUE',N'Y',N'YES',N'1') OR TRY_CONVERT(int," + field + ")=1)";
    }

    /** Expiring stock is based on the product-level ExpirationDate recorded in inventory. */
    public static Queries.Q expiryWatch(Meta m, Filter f) throws Queries.Missing {
        String code = m.must("inventory", "کد کالا", "shka", "SHKA");
        String name = m.must("inventory", "نام کالا", "naka", "NAKA");
        String expiration = m.must("inventory", "تاریخ انقضا", "ExpirationDate", "expiration_date");
        String cost = m.must("inventory", "بهای موجودی", "inventory_price", "buy_price", "pure_buy_price", "ImPureBuyPrice");
        String active = m.col("inventory", "active", "Active");
        String pieces = MasterQueries.stockPieces(m, "i.");
        String expiry = Sql.date10("i", expiration);

        List<Object> binds = new java.util.ArrayList<>();
        List<String> conds = new java.util.ArrayList<>();
        conds.add("NULLIF(LTRIM(RTRIM(TRY_CONVERT(nvarchar(30),i.[" + expiration + "]))),N'') IS NOT NULL");
        conds.add(pieces + ">0");
        if (active != null) conds.add(boolTrue("i.[" + active + "]"));
        if (f.search != null && !f.search.trim().isEmpty()) {
            conds.add("(TRY_CONVERT(nvarchar(250),i.[" + name + "]) LIKE ? OR TRY_CONVERT(nvarchar(60),i.[" + code + "]) LIKE ?)");
            String query = "%" + f.search.trim() + "%";
            binds.add(query);
            binds.add(query);
        }
        String value = pieces + "*COALESCE(TRY_CONVERT(decimal(19,2),i.[" + cost + "]),0)";
        return new Queries.Q("SELECT TOP (1000) TRY_CONVERT(nvarchar(60),i.[" + code + "]) AS code"
                + ", COALESCE(TRY_CONVERT(nvarchar(250),i.[" + name + "]),N'') AS name"
                + ", " + expiry + " AS expiry, " + pieces + " AS qty"
                + ", " + value + " AS exposureValue"
                + " FROM dbo.inventory i WHERE " + Sql.join(conds, " AND ")
                + " ORDER BY " + expiry + " ASC", binds);
    }

    /** Read-only audit of attendance events; dates are converted from Jalali filter bounds. */
    public static Queries.Q attendanceAudit(Meta m, Filter f) throws Queries.Missing {
        if (!m.table("meelano_attendance"))
            throw new Queries.Missing("جدول ثبت حضور پرسنل در این دیتابیس موجود نیست");
        String user = m.must("meelano_attendance", "نام کاربری", "username");
        String eventTime = m.must("meelano_attendance", "زمان رویداد", "event_time");
        String eventType = m.must("meelano_attendance", "نوع رویداد", "event_type");
        String displayName = m.col("meelano_attendance", "display_name");
        String source = m.col("meelano_attendance", "source");
        String biometric = m.col("meelano_attendance", "biometric");
        String voided = m.col("meelano_attendance", "voided");
        String distance = m.col("meelano_attendance", "distance_m");
        String accuracy = m.col("meelano_attendance", "accuracy_m");
        String editedBy = m.col("meelano_attendance", "edited_by");
        String editedAt = m.col("meelano_attendance", "edited_at");
        String note = m.col("meelano_attendance", "manager_note", "note");

        List<Object> binds = new java.util.ArrayList<>();
        List<String> conds = new java.util.ArrayList<>();
        if (f.from != null && !f.from.trim().isEmpty()) {
            String gregorian = Jalali.toGregorian(f.from.trim());
            if (gregorian.isEmpty()) throw new Queries.Missing("تاریخ شروع گزارش معتبر نیست");
            conds.add("a.[" + eventTime + "]>=?");
            binds.add(gregorian);
        }
        if (f.to != null && !f.to.trim().isEmpty()) {
            String gregorian = Jalali.toGregorian(f.to.trim());
            if (gregorian.isEmpty()) throw new Queries.Missing("تاریخ پایان گزارش معتبر نیست");
            conds.add("a.[" + eventTime + "]<DATEADD(day,1,?)");
            binds.add(gregorian);
        }
        if (f.search != null && !f.search.trim().isEmpty()) {
            String search = "(TRY_CONVERT(nvarchar(150),a.[" + user + "]) LIKE ?";
            binds.add("%" + f.search.trim() + "%");
            if (displayName != null) {
                search += " OR TRY_CONVERT(nvarchar(220),a.[" + displayName + "]) LIKE ?";
                binds.add("%" + f.search.trim() + "%");
            }
            conds.add(search + ")");
        }
        String where = conds.isEmpty() ? "" : " WHERE " + Sql.join(conds, " AND ");
        return new Queries.Q("SELECT TOP (500) COALESCE(TRY_CONVERT(nvarchar(220),"
                + (displayName == null ? "a.[" + user + "]" : "a.[" + displayName + "]") + "),N'') AS person"
                + ", TRY_CONVERT(nvarchar(150),a.[" + user + "]) AS username"
                + ", TRY_CONVERT(nvarchar(20),a.[" + eventType + "]) AS event"
                + ", CONVERT(nvarchar(19),a.[" + eventTime + "],120) AS eventTime"
                + ", " + textOrEmpty("a", source, 60) + " AS source"
                + ", " + numOrNull("a", distance) + " AS distance"
                + ", " + numOrNull("a", accuracy) + " AS accuracy"
                + ", " + (biometric == null ? "CAST(NULL AS nvarchar(10))" : "TRY_CONVERT(nvarchar(10),a.[" + biometric + "])") + " AS biometric"
                + ", " + (voided == null ? "CAST(0 AS int)" : "COALESCE(TRY_CONVERT(int,a.[" + voided + "]),0)") + " AS voided"
                + ", " + textOrEmpty("a", editedBy, 150) + " AS editedBy"
                + ", " + (editedAt == null ? "CAST(NULL AS nvarchar(19))" : "CONVERT(nvarchar(19),a.[" + editedAt + "],120)") + " AS editedAt"
                + ", " + textOrEmpty("a", note, 500) + " AS note"
                + " FROM dbo.meelano_attendance a" + where
                + " ORDER BY a.[" + eventTime + "] DESC", binds);
    }

    private static String textOrEmpty(String alias, String column, int len) {
        return column == null ? "CAST(N'' AS nvarchar(" + len + "))"
                : "COALESCE(TRY_CONVERT(nvarchar(" + len + ")," + alias + ".[" + column + "]),N'')";
    }

    private static String numOrNull(String alias, String column) {
        return column == null ? "CAST(NULL AS decimal(19,2))"
                : "TRY_CONVERT(decimal(19,2)," + alias + ".[" + column + "])";
    }

    /** Keep expired stock and the next 90 days; oldest-risk products appear first. */
    public static List<Row> mapExpiry(List<Row> rows) {
        if (rows == null) return rows;
        String today = Jalali.todayStr();
        java.util.Iterator<Row> it = rows.iterator();
        while (it.hasNext()) {
            Row row = it.next();
            String expiry = Jalali.disp(row.s("expiry"));
            int days = expiry.isEmpty() ? Integer.MAX_VALUE : Jalali.diffDays(today, expiry);
            if (days == Integer.MAX_VALUE || days > 90) {
                it.remove();
                continue;
            }
            row.put("daysLeft", days);
            row.put("status", days < 0 ? "منقضی‌شده" : (days == 0 ? "انقضا امروز"
                    : days <= 7 ? "فوری؛ حداکثر ۷ روز" : days <= 30 ? "هشدار؛ حداکثر ۳۰ روز" : "پایش؛ حداکثر ۹۰ روز"));
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) { return Long.compare(a.l("daysLeft"), b.l("daysLeft")); }
        });
        return rows;
    }

    /** Translate attendance flags without asserting lateness or absence absent a shift schedule. */
    public static List<Row> mapAttendance(List<Row> rows) {
        if (rows == null) return rows;
        for (Row row : rows) {
            String event = row.s("event").trim().toLowerCase(java.util.Locale.ROOT);
            if ("in".equals(event) || "ورود".equals(event)) row.put("event", "ورود");
            else if ("out".equals(event) || "خروج".equals(event)) row.put("event", "خروج");

            String source = row.s("source");
            if ("app".equalsIgnoreCase(source)) row.put("source", "برنامه پرسنلی");
            else if ("manager".equalsIgnoreCase(source)) row.put("source", "ثبت/اصلاح مدیر");
            else if (source.isEmpty()) row.put("source", "نامشخص");

            String bio = row.s("biometric");
            row.put("bio", bio.isEmpty() ? "نامشخص" : isTrue(bio) ? "تأیید زیستی" : "بدون تأیید زیستی");
            boolean isVoided = isTrue(row.s("voided"));
            String editor = row.s("editedBy");
            String note = row.s("note");
            String audit = isVoided ? "باطل‌شده" : (editor.isEmpty() ? "ثبت اولیه" : "اصلاح: " + editor);
            if (!note.isEmpty()) audit += " • " + note;
            if (!row.s("editedAt").isEmpty()) audit += " • " + Jalali.faDate(row.s("editedAt"));
            row.put("audit", audit);
        }
        return rows;
    }

    private static boolean isTrue(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "t".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value);
    }
}
