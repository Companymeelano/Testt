package ir.meelano.manager.ui;

import android.content.Context;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dependency-free REAL .docx exporter (mirrors {@link Pdf#build}): title,
 * RTL table with gold-on-dark header, zebra rows and a totals row.
 */
public final class Docx {
    private Docx() { }

    public static final String MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public static File build(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                             List<Row> rows, int maxRows) throws Exception {
        int n = Math.min(rows.size(), Math.max(1, maxRows));
        String brand = LicenseStore.brandName(c);
        StringBuilder doc = new StringBuilder(65536);
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
                .append("<w:body>");
        para(doc, safe(title), 32, true, "101828");
        String sub = safe(subtitle);
        if (!sub.isEmpty()) sub += "  •  ";
        para(doc, sub + brand + " • " + Jalali.todayStr(), 20, false, "5A6478");
        // Table.
        doc.append("<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w=\"5000\" w:type=\"pct\"/>")
                .append("<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("<w:left w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("<w:right w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D9D2C0\"/>")
                .append("</w:tblBorders></w:tblPr><w:tblGrid>");
        doc.append(gridCell(700));
        for (ReportCatalog.Col col : cols) doc.append(gridCell(widthOf(col)));
        doc.append("</w:tblGrid>");
        // Header row.
        rowOpen(doc);
        cell(doc, "ردیف", 700, "101828", "F1D493", true);
        for (ReportCatalog.Col col : cols) cell(doc, safe(col.title), widthOf(col), "101828", "F1D493", true);
        doc.append("</w:tr>");
        // Data.
        double[] sums = new double[cols.length];
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            String shd = (i % 2 == 1) ? "F4F1E8" : "FFFFFF";
            rowOpen(doc);
            cell(doc, String.valueOf(i + 1), 700, shd, "333C4E", false);
            for (int j = 0; j < cols.length; j++) {
                String t;
                if (cols[j].type == ReportCatalog.T_MONEY) {
                    double v = r.d(cols[j].key);
                    sums[j] += v;
                    t = num(v);
                } else if (cols[j].type == ReportCatalog.T_NUM) {
                    t = num(r.d(cols[j].key));
                } else if (cols[j].type == ReportCatalog.T_DATE) {
                    t = Jalali.disp(r.s(cols[j].key));
                } else {
                    t = r.s(cols[j].key, "—");
                    if (!"—".equals(t)) t = Jalali.faDate(t);
                }
                cell(doc, t, widthOf(cols[j]), shd,
                        cols[j].type == ReportCatalog.T_MONEY ? "101828" : "333C4E",
                        cols[j].type == ReportCatalog.T_MONEY);
            }
            doc.append("</w:tr>");
        }
        // Totals.
        rowOpen(doc);
        cell(doc, "جمع", 700, "F6E7C6", "101828", true);
        for (int j = 0; j < cols.length; j++)
            cell(doc, cols[j].type == ReportCatalog.T_MONEY ? num(sums[j]) : "",
                    widthOf(cols[j]), "F6E7C6", "101828", true);
        doc.append("</w:tr>");
        doc.append("</w:tbl>");
        para(doc, brand + " • " + Jalali.todayStr(), 16, false, "9AA3B5");
        doc.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
                .append("<w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr>");
        doc.append("</w:body></w:document>");

        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".docx");
        try (FileOutputStream fos = new FileOutputStream(out);
             ZipOutputStream z = new ZipOutputStream(fos)) {
            put(z, "[Content_Types].xml", contentTypes());
            put(z, "_rels/.rels", rels());
            put(z, "word/document.xml", doc.toString());
            put(z, "word/styles.xml", styles());
        }
        return out;
    }

    private static int widthOf(ReportCatalog.Col col) {
        if (col.type == ReportCatalog.T_MONEY) return 1900;
        if (col.type == ReportCatalog.T_TEXT) return 2600;
        if (col.type == ReportCatalog.T_DATE) return 1500;
        return 1100;
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        if (Math.abs(v - Math.round(v)) < 0.005)
            return String.format(Locale.US, "%,d", Math.round(v));
        return String.format(Locale.US, "%,.2f", v);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&') b.append("&amp;");
            else if (ch == '<') b.append("&lt;");
            else if (ch == '>') b.append("&gt;");
            else if (ch == '"') b.append("&quot;");
            else if (ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') { /* strip */ } else b.append(ch);
        }
        return b.toString();
    }

    private static void para(StringBuilder b, String t, int halfPts, boolean bold, String color) {
        b.append("<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr>");
        if (bold) b.append("<w:b/><w:bCs/>");
        b.append("<w:sz w:val=\"").append(halfPts).append("\"/><w:szCs w:val=\"").append(halfPts).append("\"/>");
        b.append("<w:color w:val=\"").append(color).append("\"/>");
        b.append("<w:rFonts w:ascii=\"Vazirmatn\" w:hAnsi=\"Vazirmatn\" w:cs=\"Vazirmatn\"/>");
        b.append("</w:rPr><w:t xml:space=\"preserve\">").append(esc(t)).append("</w:t></w:r></w:p>");
    }

    private static void rowOpen(StringBuilder b) {
        b.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>");
    }

    private static void cell(StringBuilder b, String t, int w, String shd, String color, boolean bold) {
        b.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(w).append("\" w:type=\"dxa\"/>")
                .append("<w:shd w:val=\"clear\" w:fill=\"").append(shd).append("\"/>")
                .append("<w:vAlign w:val=\"center\"/></w:tcPr>")
                .append("<w:p><w:pPr><w:bidi/><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr>");
        if (bold) b.append("<w:b/><w:bCs/>");
        b.append("<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>");
        b.append("<w:color w:val=\"").append(color).append("\"/>");
        b.append("<w:rFonts w:ascii=\"Vazirmatn\" w:hAnsi=\"Vazirmatn\" w:cs=\"Vazirmatn\"/>");
        b.append("</w:rPr><w:t xml:space=\"preserve\">").append(esc(t)).append("</w:t></w:r></w:p></w:tc>");
    }

    private static String gridCell(int w) {
        return "<w:gridCol w:w=\"" + w + "\"/>";
    }

    private static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        byte[] bytes = content.getBytes("UTF-8");
        z.write(bytes, 0, bytes.length);
        z.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxml-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "</Types>";
    }

    private static String rels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"Vazirmatn\" w:hAnsi=\"Vazirmatn\" w:cs=\"Vazirmatn\"/>"
                + "<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>"
                + "</w:rPr></w:rPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">"
                + "<w:pPr><w:bidi/></w:pPr></w:style>"
                + "</w:styles>";
    }
}
