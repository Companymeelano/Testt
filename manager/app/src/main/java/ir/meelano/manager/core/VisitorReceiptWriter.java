package ir.meelano.manager.core;

import android.content.Context;

import ir.meelano.manager.R;
import ir.meelano.manager.core.Jalali;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/** Uses Atiran's own Daryaft procedure and triggers for a visitor's customer advance/receipt. */
public final class VisitorReceiptWriter {
    private VisitorReceiptWriter() { }

    public static final class Cheque {
        public final String number, dueDate, bankName, branch, account, sayadId, note;
        public final boolean inquired;
        public Cheque(String number,String dueDate,String bankName,String branch,String account,
                      String sayadId,boolean inquired,String note){
            this.number=number==null?"":number.trim();this.dueDate=dueDate==null?"":dueDate.trim();
            this.bankName=bankName==null?"":bankName.trim();this.branch=branch==null?"":branch.trim();
            this.account=account==null?"":account.trim();this.sayadId=sayadId==null?"":sayadId.trim();
            this.inquired=inquired;this.note=note==null?"":note.trim();
        }
        boolean valid(){return !number.isEmpty()&&dueDate.matches("\\d{4}/\\d{2}/\\d{2}")&&!bankName.isEmpty();}
    }

    public static final class Result {
        public final long receiptNo;
        public final boolean duplicate;
        public final BigDecimal amount;
        Result(long receiptNo, boolean duplicate, BigDecimal amount) {
            this.receiptNo = receiptNo; this.duplicate = duplicate; this.amount = amount;
        }
    }

    public static Result submit(Context context, Connection c, VisitorIdentity.Identity visitor,
                                long customerCode, String note, String method, int bankId,
                                BigDecimal amount, String idempotencyKey) throws Exception {
        return submit(context,c,visitor,customerCode,note,method,bankId,amount,null,idempotencyKey);
    }

    /** method is cash, pos, transfer, or cheque; noncash methods write through Atiran's receipt SQL. */
    public static Result submit(Context context, Connection c, VisitorIdentity.Identity visitor,
                                long customerCode, String note, String method, int bankId,
                                BigDecimal amount, Cheque cheque, String idempotencyKey) throws Exception {
        if (visitor == null || visitor.userId <= 0 || visitor.visitorId <= 0)
            throw new Exception("هویت ویزیتور آتیران تأیید نشده است.");
        if (customerCode <= 0) throw new Exception("مشتری معتبر انتخاب نشده است.");
        VisitorAtiranRules.requireCustomerScope(c,visitor,String.valueOf(customerCode));
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw new Exception("مبلغ دریافت باید مثبت و حداکثر دو رقم اعشار داشته باشد.");
        String kind = method == null ? "" : method.trim().toLowerCase(java.util.Locale.US);
        if (!("cash".equals(kind) || "pos".equals(kind) || "transfer".equals(kind) || "cheque".equals(kind)))
            throw new Exception("روش دریافت پشتیبانی نمی‌شود.");
        if (("pos".equals(kind) || "transfer".equals(kind)) && bankId <= 0) throw new Exception("برای دریافت غیرنقدی، حساب بانکی را انتخاب کنید.");
        if ("cheque".equals(kind) && (cheque == null || !cheque.valid()))
            throw new Exception("برای چک، شماره، تاریخ سررسید معتبر و نام بانک لازم است.");
        String uuid = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        if (uuid.length() > 80) throw new Exception("شناسهٔ دریافت معتبر نیست.");
        String description = note == null ? "پیش‌دریافت مشتری" : note.trim();
        if (description.length() > 450) description = description.substring(0, 450);
        String spec = "<r/>";
        BigDecimal cash = amount;
        if ("pos".equals(kind) || "transfer".equals(kind)) {
            cash = BigDecimal.ZERO;
            String rowKind = "pos".equals(kind) ? "pos" : "hav";
            spec = "<r><p k=\"" + rowKind + "\" m=\"" + amount.toPlainString()
                    + "\" b=\"" + bankId + "\" t=\"\" d=\"\"/></r>";
        } else if ("cheque".equals(kind)) {
            cash = BigDecimal.ZERO;
            spec = "<r><c m=\"" + amount.toPlainString() + "\" s=\"" + xml(cheque.number)
                    + "\" sd=\"" + xml(cheque.dueDate) + "\" bn=\"" + xml(cheque.bankName)
                    + "\" br=\"" + xml(cheque.branch) + "\" hs=\"" + xml(cheque.account)
                    + "\" sy=\"" + xml(cheque.sayadId) + "\" ri=\"" + (cheque.inquired?"1":"0")
                    + "\" ct=\"1\" d=\"" + xml(cheque.note) + "\"/></r>";
        }
        String sql = readSql(context);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, customerCode);
            ps.setInt(2, visitor.userId);
            ps.setString(3, visitor.username);
            ps.setString(4, Jalali.todayStr());
            ps.setString(5, description);
            ps.setString(6, uuid);
            ps.setBigDecimal(7, cash);
            ps.setString(8, spec);
            boolean result = ps.execute();
            while (true) {
                if (result) {
                    try (ResultSet rs = ps.getResultSet()) {
                        if (rs != null && rs.next()) {
                            long number = rs.getLong("ghno");
                            boolean duplicate = rs.getInt("duplicate") != 0;
                            BigDecimal total = rs.getBigDecimal("total");
                            if (number <= 0) throw new Exception("آتیران شمارهٔ قبض معتبری برنگرداند.");
                            return new Result(number, duplicate, total == null ? amount : total);
                        }
                    }
                } else if (ps.getUpdateCount() == -1) break;
                result = ps.getMoreResults();
            }
        }
        throw new Exception("پاسخ نهایی قبض از آتیران دریافت نشد.");
    }

    private static String xml(String value){return value==null?"":value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}

    private static String readSql(Context context) throws Exception {
        try (InputStream in = context.getResources().openRawResource(R.raw.atiran_receipt);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
