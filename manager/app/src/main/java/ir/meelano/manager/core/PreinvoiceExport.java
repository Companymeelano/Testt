package ir.meelano.manager.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ir.meelano.manager.data.Row;

/**
 * خروجی‌های حرفه‌ای پیش‌فاکتور خرید: ستون‌های گزارش (PDF/تصویر)، CSV با BOM،
 * اکسل (SpreadsheetML که Excel مستقیم باز می‌کند)، ورد (HTML با پسوند doc) و متن خلاصه.
 * همهٔ اعداد با ارقام فارسی و جداکنندهٔ هزارگان در متن‌ها، و خام در فایل‌های داده.
 */
public final class PreinvoiceExport {

    private PreinvoiceExport() { }

    public static ReportCatalog.Col[] cols() {
        return new ReportCatalog.Col[]{
                new ReportCatalog.Col("code", "کد کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("name", "نام کالا", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("unit", "واحد", ReportCatalog.T_TEXT),
                new ReportCatalog.Col("qty", "تعداد", ReportCatalog.T_NUM),
                new ReportCatalog.Col("price", "قیمت واحد (ریال)", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("disc", "تخفیف ردیف (ریال)", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("tax", "مالیات و عوارض (ریال)", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("total", "جمع قلم (ریال)", ReportCatalog.T_MONEY),
                new ReportCatalog.Col("diff", "اختلاف با مرجع (ریال)", ReportCatalog.T_MONEY)};
    }

    public static List<Row> rows(PurchasePreinvoice.Draft d) {
        List<Row> out = new ArrayList<>();
        for (PurchasePreinvoice.Line l : d.lines) {
            Row r = new Row();
            r.put("code", l.code);
            r.put("name", l.name);
            r.put("unit", l.unit);
            r.put("qty", l.qty);
            r.put("price", l.price);
            r.put("disc", l.rowDisc + l.invDisc);
            r.put("tax", l.taxAmt + l.dutyAmt);
            r.put("total", l.total);
            r.put("diff", l.diffAmt);
            out.add(r);
        }
        return out;
    }

    public static String subtitle(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        return "سند " + (d.docNo.isEmpty() ? "—" : d.docNo) + " • " + Jalali.dispFa(String.valueOf(d.dateTs))
                + " • فروشنده: " + (d.supplierName.isEmpty() ? "—" : d.supplierName)
                + " • جمع کل " + Money.fa(String.valueOf(Math.round(t.grand)))
                + " • قابل پرداخت " + Money.fa(String.valueOf(Math.round(t.payable))) + " ریال"
                + " • تخفیفات " + String.format(Locale.US, "%.1f", t.discPct) + "٪";
    }

    public static String summaryText(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("پیش‌فاکتور خرید ").append(d.docNo).append('\n');
        sb.append("فروشنده: ").append(d.supplierName).append(" کد ").append(d.supplierCode).append('\n');
        sb.append("خریدار: ").append(d.buyerName).append(" کد ").append(d.buyerCode).append('\n');
        sb.append("تاریخ: ").append(Jalali.dispFa(String.valueOf(d.dateTs)))
          .append(" • سررسید: ").append(d.dueDays).append(" روز\n");
        for (PurchasePreinvoice.Line l : d.lines) {
            sb.append("• ").append(l.name).append(" — ").append(Money.fa(String.valueOf((long) l.qty)))
              .append(' ').append(l.unit).append(" × ").append(Money.fa(String.valueOf(Math.round(l.price))))
              .append(" = ").append(Money.fa(String.valueOf(Math.round(l.total)))).append(" ریال");
            if (l.diffAmt != 0) sb.append(" (اختلاف ").append(Money.fa(String.valueOf(Math.round(l.diffAmt))))
                    .append(String.format(Locale.US, " %.1f", l.diffPct)).append("٪)");
            sb.append('\n');
        }
        sb.append("جمع اقلام: ").append(Money.fa(String.valueOf(Math.round(t.goods)))).append(" ریال\n");
        sb.append("تخفیفات: ").append(Money.fa(String.valueOf(Math.round(t.discSum)))).append(" ریال (")
          .append(Money.fa(String.format(Locale.US, "%.1f", t.discPct))).append("٪)\n");
        sb.append("مالیات: ").append(Money.fa(String.valueOf(Math.round(t.tax)))).append(" • عوارض: ")
          .append(Money.fa(String.valueOf(Math.round(t.duty)))).append(" ریال\n");
        sb.append("قابل پرداخت: ").append(Money.fa(String.valueOf(Math.round(t.payable)))).append(" ریال");
        return sb.toString();
    }

    public static byte[] csv(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("کد کالا;نام کالا;واحد;تعداد;قیمت واحد;تخفیف ردیف;تخفیف کل;مالیات;عوارض;جمع قلم;اختلاف با مرجع\n");
        for (PurchasePreinvoice.Line l : d.lines) {
            sb.append(l.code).append(';').append(l.name).append(';').append(l.unit).append(';')
              .append((long) l.qty).append(';').append(Math.round(l.price)).append(';')
              .append(Math.round(l.rowDisc)).append(';').append(Math.round(l.invDisc)).append(';')
              .append(Math.round(l.taxAmt)).append(';').append(Math.round(l.dutyAmt)).append(';')
              .append(Math.round(l.total)).append(';').append(Math.round(l.diffAmt)).append('\n');
        }
        sb.append(";;;;;;;;جمع کل;").append(Math.round(t.grand)).append('\n');
        sb.append(";;;;;;;;قابل پرداخت;").append(Math.round(t.payable)).append('\n');
        byte[] body = sb.toString().getBytes(Charset.forName("UTF-8"));
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String tableHtml(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border='1' cellspacing='0' cellpadding='4' style='border-collapse:collapse;font-family:Tahoma;'>");
        sb.append("<tr><th>کد کالا</th><th>نام کالا</th><th>واحد</th><th>تعداد</th><th>قیمت واحد</th>")
          .append("<th>تخفیف ردیف</th><th>تخفیف کل</th><th>مالیات</th><th>عوارض</th><th>جمع قلم</th><th>اختلاف</th></tr>");
        for (PurchasePreinvoice.Line l : d.lines) {
            sb.append("<tr><td>").append(esc(l.code)).append("</td><td>").append(esc(l.name))
              .append("</td><td>").append(esc(l.unit)).append("</td><td>").append((long) l.qty)
              .append("</td><td>").append(Math.round(l.price)).append("</td><td>").append(Math.round(l.rowDisc))
              .append("</td><td>").append(Math.round(l.invDisc)).append("</td><td>").append(Math.round(l.taxAmt))
              .append("</td><td>").append(Math.round(l.dutyAmt)).append("</td><td>").append(Math.round(l.total))
              .append("</td><td>").append(Math.round(l.diffAmt)).append("</td></tr>");
        }
        sb.append("<tr><td colspan='9'>جمع کل</td><td colspan='2'>").append(Math.round(t.grand)).append("</td></tr>");
        sb.append("<tr><td colspan='9'>قابل پرداخت پس از تخفیف نقدی</td><td colspan='2'>")
          .append(Math.round(t.payable)).append("</td></tr></table>");
        return sb.toString();
    }

    public static byte[] doc(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        String html = "<html dir='rtl'><head><meta charset='utf-8'><title>پیش‌فاکتور خرید "
                + esc(d.docNo) + "</title></head><body><h2>پیش‌فاکتور خرید " + esc(d.docNo) + "</h2>"
                + "<p>فروشنده: " + esc(d.supplierName) + " • کد ملی: " + esc(d.supplierNatId)
                + " • تلفن: " + esc(d.supplierPhone) + "<br>نشانی: " + esc(d.supplierAddress) + "</p>"
                + "<p>خریدار: " + esc(d.buyerName) + " • کد: " + esc(d.buyerCode)
                + " • تاریخ: " + Jalali.dispFa(String.valueOf(d.dateTs))
                + " • سررسید: " + d.dueDays + " روز • یادداشت: " + esc(d.note) + "</p>"
                + tableHtml(d, t) + "</body></html>";
        return html.getBytes(Charset.forName("UTF-8"));
    }

    public static byte[] xls(PurchasePreinvoice.Draft d, PurchasePreinvoice.Totals t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<?mso-application progid=\"Excel.Sheet\"?>\n")
          .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
          .append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")
          .append("<Worksheet ss:Name=\"پیش فاکتور خرید\"><Table>\n");
        sb.append("<Row><Cell><Data ss:Type=\"String\">پیش‌فاکتور خرید ").append(esc(d.docNo))
          .append("</Data></Cell></Row>\n");
        sb.append("<Row><Cell><Data ss:Type=\"String\">فروشنده: ").append(esc(d.supplierName))
          .append(" • خریدار: ").append(esc(d.buyerName))
          .append(" • تاریخ: ").append(Jalali.dispFa(String.valueOf(d.dateTs)))
          .append("</Data></Cell></Row>\n");
        String[] head = {"کد کالا", "نام کالا", "واحد", "تعداد", "قیمت واحد", "تخفیف ردیف",
                "تخفیف کل", "مالیات", "عوارض", "جمع قلم", "اختلاف"};
        sb.append("<Row>");
        for (String h : head) sb.append("<Cell><Data ss:Type=\"String\">").append(h).append("</Data></Cell>");
        sb.append("</Row>\n");
        for (PurchasePreinvoice.Line l : d.lines) {
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
        sb.append("<Row><Cell><Data ss:Type=\"String\">جمع کل</Data></Cell>")
          .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(t.grand)).append("</Data></Cell></Row>\n");
        sb.append("<Row><Cell><Data ss:Type=\"String\">قابل پرداخت</Data></Cell>")
          .append("<Cell><Data ss:Type=\"Number\">").append(Math.round(t.payable)).append("</Data></Cell></Row>\n");
        sb.append("</Table></Worksheet></Workbook>\n");
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }
}
