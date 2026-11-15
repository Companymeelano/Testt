package ir.meelano.android;

import java.nio.charset.Charset;
import java.util.Locale;

/** خروجی‌های حرفه‌ای پیش‌فاکتور خرید نسخهٔ یکپارچه: CSV/اکسل/ورد/متن + دادهٔ PDF. */
final class MeelanoPreinvoiceExport {

    private MeelanoPreinvoiceExport() { }

    static String faNum(long v) {
        StringBuilder sb = new StringBuilder();
        String s = String.valueOf(v);
        for (int i = 0; i < s.length(); i++) sb.append((char) ('۰' + (s.charAt(i) - '0')));
        return sb.toString();
    }

    static String dateFa(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        int jdn = MeelanoJalali.gregorianDay(c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
        int[] j = MeelanoJalali.fromDay(jdn);
        return faNum(j[0]) + "/" + faNum(j[1]) + "/" + faNum(j[2]);
    }

    static String summary(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("پیش‌فاکتور خرید ").append(d.docNo).append('\n');
        sb.append("فروشنده: ").append(d.supplierName).append(" کد ").append(d.supplierCode).append('\n');
        sb.append("خریدار: ").append(d.buyerName).append(" کد ").append(d.buyerCode).append('\n');
        sb.append("تاریخ: ").append(dateFa(d.dateTs)).append(" • سررسید: ").append(faNum(d.dueDays)).append(" روز\n");
        for (MeelanoPreinvoice.Line l : d.lines) {
            sb.append("• ").append(l.name).append(" — ").append(faNum(Math.round(l.qty))).append(' ').append(l.unit)
              .append(" × ").append(faNum(Math.round(l.price))).append(" = ").append(faNum(Math.round(l.total)))
              .append(" ریال");
            if (l.diffAmt != 0) sb.append(" (اختلاف ").append(faNum(Math.round(l.diffAmt))).append(" ریال)");
            sb.append('\n');
        }
        sb.append("جمع اقلام: ").append(faNum(Math.round(t.goods))).append(" ریال\n");
        sb.append("تخفیفات: ").append(faNum(Math.round(t.discSum))).append(" ریال\n");
        sb.append("مالیات: ").append(faNum(Math.round(t.tax))).append(" • عوارض: ").append(faNum(Math.round(t.duty))).append(" ریال\n");
        sb.append("قابل پرداخت: ").append(faNum(Math.round(t.payable))).append(" ریال");
        return sb.toString();
    }

    static byte[] csv(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("کد کالا;نام کالا;واحد;تعداد;قیمت واحد;تخفیف ردیف;تخفیف کل;مالیات;عوارض;جمع قلم;اختلاف با مرجع\n");
        for (MeelanoPreinvoice.Line l : d.lines) {
            sb.append(l.code).append(';').append(l.name).append(';').append(l.unit).append(';')
              .append(Math.round(l.qty)).append(';').append(Math.round(l.price)).append(';')
              .append(Math.round(l.rowDisc)).append(';').append(Math.round(l.invDisc)).append(';')
              .append(Math.round(l.taxAmt)).append(';').append(Math.round(l.dutyAmt)).append(';')
              .append(Math.round(l.total)).append(';').append(Math.round(l.diffAmt)).append('\n');
        }
        sb.append(";;;;;;;;جمع کل;").append(Math.round(t.grand)).append('\n');
        sb.append(";;;;;;;;قابل پرداخت;").append(Math.round(t.payable)).append('\n');
        byte[] body = sb.toString().getBytes(Charset.forName("UTF-8"));
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String table(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1' cellspacing='0' cellpadding='4' style='border-collapse:collapse;font-family:Tahoma;'>");
        sb.append("<tr><th>کد کالا</th><th>نام کالا</th><th>واحد</th><th>تعداد</th><th>قیمت واحد</th><th>تخفیف ردیف</th><th>تخفیف کل</th><th>مالیات</th><th>عوارض</th><th>جمع قلم</th><th>اختلاف</th></tr>");
        for (MeelanoPreinvoice.Line l : d.lines) {
            sb.append("<tr><td>").append(esc(l.code)).append("</td><td>").append(esc(l.name)).append("</td><td>")
              .append(esc(l.unit)).append("</td><td>").append(Math.round(l.qty)).append("</td><td>")
              .append(Math.round(l.price)).append("</td><td>").append(Math.round(l.rowDisc)).append("</td><td>")
              .append(Math.round(l.invDisc)).append("</td><td>").append(Math.round(l.taxAmt)).append("</td><td>")
              .append(Math.round(l.dutyAmt)).append("</td><td>").append(Math.round(l.total)).append("</td><td>")
              .append(Math.round(l.diffAmt)).append("</td></tr>");
        }
        sb.append("<tr><td colspan='9'>جمع کل</td><td colspan='2'>").append(Math.round(t.grand)).append("</td></tr>");
        sb.append("<tr><td colspan='9'>قابل پرداخت</td><td colspan='2'>").append(Math.round(t.payable)).append("</td></tr></table>");
        return sb.toString();
    }

    static byte[] doc(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        String html = "<html dir='rtl'><head><meta charset='utf-8'><title>پیش‌فاکتور خرید " + esc(d.docNo)
                + "</title></head><body><h2>پیش‌فاکتور خرید " + esc(d.docNo) + "</h2>"
                + "<p>فروشنده: " + esc(d.supplierName) + " • شناسه ملی: " + esc(d.supplierNatId)
                + " • تلفن: " + esc(d.supplierPhone) + "<br>نشانی: " + esc(d.supplierAddress) + "</p>"
                + "<p>خریدار: " + esc(d.buyerName) + " • کد: " + esc(d.buyerCode) + " • تاریخ: " + dateFa(d.dateTs)
                + " • سررسید: " + d.dueDays + " روز • یادداشت: " + esc(d.note) + "</p>"
                + table(d, t) + "</body></html>";
        return html.getBytes(Charset.forName("UTF-8"));
    }

    static byte[] xls(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<?mso-application progid=\"Excel.Sheet\"?>\n")
          .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
          .append("<Worksheet ss:Name=\"پیش فاکتور خرید\"><Table>\n")
          .append("<Row><Cell><Data ss:Type=\"String\">پیش‌فاکتور خرید ").append(esc(d.docNo)).append("</Data></Cell></Row>\n")
          .append("<Row><Cell><Data ss:Type=\"String\">فروشنده: ").append(esc(d.supplierName))
          .append(" • خریدار: ").append(esc(d.buyerName)).append(" • تاریخ: ").append(dateFa(d.dateTs))
          .append("</Data></Cell></Row>\n");
        String[] head = {"کد کالا", "نام کالا", "واحد", "تعداد", "قیمت واحد", "تخفیف ردیف", "تخفیف کل", "مالیات", "عوارض", "جمع قلم", "اختلاف"};
        sb.append("<Row>");
        for (String h : head) sb.append("<Cell><Data ss:Type=\"String\">").append(h).append("</Data></Cell>");
        sb.append("</Row>\n");
        for (MeelanoPreinvoice.Line l : d.lines) {
            sb.append("<Row>")
              .append("<Cell><Data ss:Type=\"String\">").append(esc(l.code)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"String\">").append(esc(l.name)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"String\">").append(esc(l.unit)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(l.qty).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.price)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.rowDisc)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.invDisc)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.taxAmt)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.dutyAmt)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.total)).append("</Data></Cell>")
              .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(l.diffAmt)).append("</Data></Cell>")
              .append("</Row>\n");
        }
        sb.append("<Row><Cell><Data ss:Type=\"String\">جمع کل</Data></Cell><Cell><Data ss:Type=\"Number\">")
          .append(Math.round(t.grand)).append("</Data></Cell></Row>\n")
          .append("<Row><Cell><Data ss:Type=\"String\">قابل پرداخت</Data></Cell><Cell><Data ss:Type=\"Number\">")
          .append(Math.round(t.payable)).append("</Data></Cell></Row>\n")
          .append("</Table></Worksheet></Workbook>\n");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }

    /** دادهٔ PDF با موتور داخلی اپ: اقلام در بخش نخست و چهار کاسهٔ جمع‌بندی. */
    static MeelanoDailyReportPdf.Data pdfData(MeelanoPreinvoice.Draft d, MeelanoPreinvoice.Totals t) {
        MeelanoDailyReportPdf.Data data = new MeelanoDailyReportPdf.Data();
        data.title = "پیش‌فاکتور خرید " + d.docNo;
        data.visitor = d.supplierName;
        data.date = dateFa(d.dateTs);
        data.managerBrand = true;
        data.preTitle = "اقلام پیش‌فاکتور";
        data.preHead = new String[]{"کالا", "تعداد", "جمع قلم", "اختلاف"};
        data.preEmpty = "قلمی ثبت نشده است.";
        for (MeelanoPreinvoice.Line l : d.lines) {
            data.prefactors.add(new String[]{l.name + " " + l.code,
                    String.valueOf(Math.round(l.qty)) + " " + l.unit,
                    String.valueOf(Math.round(l.total)),
                    l.refPrice > 0 ? String.valueOf(Math.round(l.diffAmt)) : "—"});
        }
        data.summary.add(new String[]{"جمع کل", String.valueOf(Math.round(t.grand))});
        data.summary.add(new String[]{"تخفیفات", String.valueOf(Math.round(t.discSum))});
        data.summary.add(new String[]{"مالیات و عوارض", String.valueOf(Math.round(t.tax + t.duty))});
        data.summary.add(new String[]{"قابل پرداخت", String.valueOf(Math.round(t.payable))});
        data.note = "تخفیفات " + String.format(Locale.US, "%.1f", t.discPct) + "٪ • اختلاف کل با مرجع "
                + Math.round(t.diff) + " ریال • خریدار: " + d.buyerName;
        return data;
    }
}
