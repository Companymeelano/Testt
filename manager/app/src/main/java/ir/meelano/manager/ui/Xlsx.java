package ir.meelano.manager.ui;

import android.content.Context;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Dependency-free REAL .xlsx exporter (mirrors {@link Pdf#build}): gold-on-dark
 * header, zebra rows, thousand separators, totals row, RTL sheet, merged title.
 * Built straight from the Open XML spec (zip + xml) — no libraries needed.
 */
public final class Xlsx {
    private Xlsx() { }

    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static File build(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                             List<Row> rows, int maxRows) throws Exception {
        int n = Math.min(rows.size(), Math.max(1, maxRows));
        int nc = cols.length + 1; // + row-number column
        Shared sh = new Shared();
        StringBuilder sheet = new StringBuilder(65536);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews>")
                .append("<cols>");
        sheet.append(col(1, 7));
        for (int i = 0; i < cols.length; i++) sheet.append(col(i + 2, widthOf(cols[i])));
        sheet.append("</cols><sheetData>");
        String last = colName(nc);
        // Title (merged) + subtitle + date.
        int r = 1;
        rowOpen(sheet, r, 30);
        sheet.append(cellStr("A" + r, sh.add(safe(title)), 1));
        rowClose(sheet);
        r++;
        rowOpen(sheet, r, 20);
        String sub = safe(subtitle);
        if (!sub.isEmpty()) sub += "  •  ";
        sub += LicenseStore.brandName(c) + " • " + Jalali.todayStr();
        sheet.append(cellStr("A" + r, sh.add(sub), 2));
        rowClose(sheet);
        // Header.
        r++;
        rowOpen(sheet, r, 22);
        sheet.append(cellStr(colName(1) + r, sh.add("ردیف"), 3));
        for (int i = 0; i < cols.length; i++)
            sheet.append(cellStr(colName(i + 2) + r, sh.add(safe(cols[i].title)), 3));
        rowClose(sheet);
        // Data.
        double[] sums = new double[cols.length];
        for (int i = 0; i < n; i++) {
            r++;
            Row row = rows.get(i);
            boolean zebra = (i % 2 == 1);
            rowOpen(sheet, r, 18);
            sheet.append(cellNum(colName(1) + r, i + 1, zebra ? 6 : 4));
            for (int j = 0; j < cols.length; j++) {
                String ref = colName(j + 2) + r;
                if (cols[j].type == ReportCatalog.T_MONEY) {
                    double v = row.d(cols[j].key);
                    sums[j] += v;
                    sheet.append(cellNum(ref, v, zebra ? 7 : 5));
                } else if (cols[j].type == ReportCatalog.T_NUM) {
                    sheet.append(cellNum(ref, row.d(cols[j].key), zebra ? 6 : 4));
                } else if (cols[j].type == ReportCatalog.T_DATE) {
                    sheet.append(cellStr(ref, sh.add(Jalali.disp(row.s(cols[j].key))), zebra ? 6 : 4));
                } else {
                    String t = row.s(cols[j].key, "—");
                    if (!"—".equals(t)) t = Jalali.faDate(t);
                    sheet.append(cellStr(ref, sh.add(t), zebra ? 6 : 4));
                }
            }
            rowClose(sheet);
        }
        // Totals.
        r++;
        rowOpen(sheet, r, 20);
        sheet.append(cellStr(colName(1) + r, sh.add("جمع"), 8));
        for (int j = 0; j < cols.length; j++) {
            String ref = colName(j + 2) + r;
            if (cols[j].type == ReportCatalog.T_MONEY) sheet.append(cellNum(ref, sums[j], 9));
            else sheet.append(cellStr(ref, sh.add(""), 8));
        }
        rowClose(sheet);
        sheet.append("</sheetData>");
        sheet.append("<mergeCells count=\"2\"><mergeCell ref=\"A1:").append(last).append("1\"/>")
                .append("<mergeCell ref=\"A2:").append(last).append("2\"/></mergeCells>");
        sheet.append("<pageMargins left=\"0.4\" right=\"0.4\" top=\"0.5\" bottom=\"0.5\" header=\"0.3\" footer=\"0.3\"/>");
        sheet.append("</worksheet>");

        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(out);
             ZipOutputStream z = new ZipOutputStream(fos)) {
            put(z, "[Content_Types].xml", contentTypes());
            put(z, "_rels/.rels", rels());
            put(z, "xl/workbook.xml", workbook());
            put(z, "xl/_rels/workbook.xml.rels", workbookRels());
            put(z, "xl/styles.xml", styles());
            put(z, "xl/sharedStrings.xml", sh.xml());
            put(z, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return out;
    }

    // ---------------- sheet helpers ----------------

    private static int widthOf(ReportCatalog.Col col) {
        if (col.type == ReportCatalog.T_MONEY) return 20;
        if (col.type == ReportCatalog.T_TEXT) return 26;
        if (col.type == ReportCatalog.T_DATE) return 15;
        return 12;
    }

    private static String col(int idx, int w) {
        return "<col min=\"" + idx + "\" max=\"" + idx + "\" width=\"" + w + "\" customWidth=\"1\"/>";
    }

    private static String colName(int idx) {
        StringBuilder b = new StringBuilder();
        while (idx > 0) {
            int m = (idx - 1) % 26;
            b.insert(0, (char) ('A' + m));
            idx = (idx - 1) / 26;
        }
        return b.toString();
    }

    private static void rowOpen(StringBuilder b, int r, int h) {
        b.append("<row r=\"").append(r).append("\" ht=\"").append(h).append("\" customHeight=\"1\">");
    }

    private static void rowClose(StringBuilder b) {
        b.append("</row>");
    }

    private static String cellStr(String ref, int si, int style) {
        return "<c r=\"" + ref + "\" s=\"" + style + "\" t=\"s\"><v>" + si + "</v></c>";
    }

    private static String cellNum(String ref, double v, int style) {
        String t;
        if (Double.isNaN(v) || Double.isInfinite(v)) t = "0";
        else if (Math.abs(v - Math.round(v)) < 0.0001) t = String.valueOf(Math.round(v));
        else t = String.format(Locale.US, "%.2f", v);
        return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + t + "</v></c>";
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

    /** Deduplicated shared-strings table. */
    private static final class Shared {
        final List<String> list = new ArrayList<>();
        final Map<String, Integer> idx = new HashMap<>();

        int add(String s) {
            if (s == null) s = "";
            Integer i = idx.get(s);
            if (i != null) return i;
            int n = list.size();
            list.add(s);
            idx.put(s, n);
            return n;
        }

        String xml() {
            StringBuilder b = new StringBuilder(4096 + list.size() * 24);
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    .append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
                    .append(" count=\"").append(list.size()).append("\" uniqueCount=\"").append(list.size()).append("\">");
            for (String s : list) b.append("<si><t xml:space=\"preserve\">").append(esc(s)).append("</t></si>");
            b.append("</sst>");
            return b.toString();
        }
    }

    // ---------------- package parts ----------------

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
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                + "</Types>";
    }

    private static String rels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"گزارش\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"../styles.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"../sharedStrings.xml\"/>"
                + "</Relationships>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0\"/></numFmts>"
                + "<fonts count=\"4\">"
                + "<font><sz val=\"11\"/><name val=\"Vazirmatn\"/><family val=\"2\"/></font>"
                + "<font><b/><sz val=\"16\"/><color rgb=\"FF101828\"/><name val=\"Vazirmatn\"/><family val=\"2\"/></font>"
                + "<font><b/><sz val=\"12\"/><color rgb=\"FFF1D493\"/><name val=\"Vazirmatn\"/><family val=\"2\"/></font>"
                + "<font><b/><sz val=\"12\"/><color rgb=\"FF101828\"/><name val=\"Vazirmatn\"/><family val=\"2\"/></font>"
                + "</fonts>"
                + "<fills count=\"5\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF101828\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF4F1E8\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF6E7C6\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FFD9D2C0\"/></left>"
                + "<right style=\"thin\"><color rgb=\"FFD9D2C0\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFD9D2C0\"/></top>"
                + "<bottom style=\"thin\"><color rgb=\"FFD9D2C0\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"10\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"1\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"4\" borderId=\"1\" xfId=\"0\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

}
