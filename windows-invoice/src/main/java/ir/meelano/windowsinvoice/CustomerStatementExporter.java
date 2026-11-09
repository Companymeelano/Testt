package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.CustomerModels.CustomerLedgerReport;
import ir.meelano.windowsinvoice.CustomerModels.LedgerEntry;
import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dependency-free OOXML, PNG and raster-PDF export for a read-only customer statement. */
public final class CustomerStatementExporter {
    private static final int PAGE_WIDTH = 1240;
    private static final int PAGE_HEIGHT = 1754;
    private static final int PDF_ROWS_PER_PAGE = 28;
    private static final int IMAGE_ROWS_LIMIT = 180;
    private static final Locale PERSIAN = Locale.forLanguageTag("fa-IR");

    private CustomerStatementExporter() { }

    public static void writeXlsx(Path target, CustomerLedgerReport report,
                                 List<LedgerEntry> entries, boolean capped) throws IOException {
        List<LedgerEntry> rows = entries == null ? List.of() : entries;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <bookViews><workbookView/></bookViews>
                      <sheets><sheet name="گردش مشتری" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="2"><font><sz val="11"/><name val="Aptos"/></font><font><b/><sz val="15"/><name val="Aptos"/></font></fonts>
                      <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE5EEE5"/><bgColor indexed="64"/></patternFill></fill></fills>
                      <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                      <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                      <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="0" fillId="1" borderId="0" xfId="0" applyFill="1"/></cellXfs>
                      <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                    </styleSheet>
                    """);
            put(zip, "xl/worksheets/sheet1.xml", buildSheet(report, rows, capped));
        }
    }

    public static void writeImage(Path target, CustomerLedgerReport report,
                                  List<LedgerEntry> entries, boolean capped) throws IOException {
        List<LedgerEntry> rows = entries == null ? List.of() : entries;
        int count = Math.min(rows.size(), IMAGE_ROWS_LIMIT);
        int height = 380 + count * 38 + (capped || rows.size() > count ? 72 : 0);
        WritableImage image = renderOnFx(report, rows.subList(0, count), 0, 1,
                capped || rows.size() > count, PAGE_WIDTH, height, true);
        try (OutputStream out = Files.newOutputStream(target)) {
            writePng(out, image);
        }
    }

    public static void writePdf(Path target, CustomerLedgerReport report,
                                List<LedgerEntry> entries, boolean capped) throws IOException {
        List<LedgerEntry> rows = entries == null ? List.of() : entries;
        int pageCount = Math.max(1, (rows.size() + PDF_ROWS_PER_PAGE - 1) / PDF_ROWS_PER_PAGE);
        if (pageCount > 120) throw new IOException("برای جلوگیری از ساخت PDF بسیار بزرگ، حداکثر ۳۳۶۰ ردیف در هر خروجی پشتیبانی می‌شود.");
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        writeAscii(pdf, "%PDF-1.4\n%MEELANO\n");
        int totalObjects = 2 + pageCount * 3;
        offsets.add(0);
        writeObject(pdf, offsets, 1, ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder("<< /Type /Pages /Count ").append(pageCount).append(" /Kids [");
        for (int i = 0; i < pageCount; i++) kids.append(3 + i * 3).append(" 0 R ");
        kids.append("] >>");
        writeObject(pdf, offsets, 2, ascii(kids.toString()));

        for (int page = 0; page < pageCount; page++) {
            int first = page * PDF_ROWS_PER_PAGE;
            int last = Math.min(rows.size(), first + PDF_ROWS_PER_PAGE);
            List<LedgerEntry> pageRows = rows.subList(first, last);
            boolean showSummary = page == 0;
            WritableImage image = renderOnFx(report, pageRows, page, pageCount, capped, PAGE_WIDTH, PAGE_HEIGHT, showSummary);
            byte[] compressed = compressRgb(image);
            int pageObject = 3 + page * 3;
            int imageObject = pageObject + 1;
            int contentObject = pageObject + 2;
            String pageDictionary = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /XObject << /Im0 " + imageObject + " 0 R >> >> /Contents " + contentObject + " 0 R >>";
            writeObject(pdf, offsets, pageObject, ascii(pageDictionary));
            writeImageObject(pdf, offsets, imageObject, compressed, PAGE_WIDTH, PAGE_HEIGHT);
            byte[] content = ascii("q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n");
            writeStreamObject(pdf, offsets, contentObject, "<< /Length " + content.length + " >>", content);
        }
        int xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 " + (totalObjects + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= totalObjects; i++) writeAscii(pdf, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        writeAscii(pdf, "trailer\n<< /Size " + (totalObjects + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n");
        Files.write(target, pdf.toByteArray());
    }

    private static String buildSheet(CustomerLedgerReport report, List<LedgerEntry> entries, boolean capped) {
        StringBuilder sheet = new StringBuilder(32_000 + entries.size() * 500);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>")
                .append("<cols><col min=\"1\" max=\"1\" width=\"15\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"18\" customWidth=\"1\"/><col min=\"3\" max=\"4\" width=\"28\" customWidth=\"1\"/><col min=\"5\" max=\"7\" width=\"20\" customWidth=\"1\"/></cols><sheetData>");
        rowStart(sheet, 1); inline(sheet, "A1", "گردش حساب مشتری", 1); rowEnd(sheet);
        rowStart(sheet, 2); inline(sheet, "A2", "نام", 2); inline(sheet, "B2", report.customer().customer().name(), 0);
        inline(sheet, "C2", "کد مشتری", 2); inline(sheet, "D2", Long.toString(report.customer().customer().code()), 0);
        inline(sheet, "E2", "محدوده", 2); inline(sheet, "F2", rangeText(report), 0); rowEnd(sheet);
        rowStart(sheet, 3); inline(sheet, "A3", "ماندهٔ ابتدای دوره", 2); number(sheet, "B3", report.openingBalance());
        inline(sheet, "C3", "جمع بدهکار", 2); number(sheet, "D3", report.debitTotal());
        inline(sheet, "E3", "جمع بستانکار", 2); number(sheet, "F3", report.creditTotal());
        inline(sheet, "G3", "ماندهٔ پایان دوره", 2); number(sheet, "H3", report.closingBalance()); rowEnd(sheet);
        if (capped || report.truncated()) {
            rowStart(sheet, 4); inline(sheet, "A4", "توجه", 2); inline(sheet, "B4", "خروجی به علت سقف ایمنی ممکن است همهٔ گردش‌ها را شامل نشود.", 0); rowEnd(sheet);
        }
        int headerRow = 5;
        rowStart(sheet, headerRow);
        String[] headers = {"تاریخ شمسی", "شماره سند", "نوع عملیات", "شرح", "بدهکار", "بستانکار", "مانده پس از سند"};
        String[] columns = {"A", "B", "C", "D", "E", "F", "G"};
        for (int i = 0; i < headers.length; i++) inline(sheet, columns[i] + headerRow, headers[i], 2);
        rowEnd(sheet);
        int line = headerRow + 1;
        for (LedgerEntry entry : entries) {
            rowStart(sheet, line);
            inline(sheet, "A" + line, entry.date(), 0);
            inline(sheet, "B" + line, entry.documentNo(), 0);
            inline(sheet, "C" + line, entry.operation(), 0);
            inline(sheet, "D" + line, entry.description(), 0);
            number(sheet, "E" + line, entry.debit());
            number(sheet, "F" + line, entry.credit());
            number(sheet, "G" + line, entry.balance());
            rowEnd(sheet);
            line++;
        }
        sheet.append("</sheetData><autoFilter ref=\"A5:G").append(Math.max(5, line - 1)).append("\"/><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/></worksheet>");
        return sheet.toString();
    }

    private static WritableImage renderOnFx(CustomerLedgerReport report, List<LedgerEntry> entries,
                                           int pageIndex, int pageCount, boolean capped,
                                           int width, int height, boolean summary) throws IOException {
        if (Platform.isFxApplicationThread())
            return render(report, entries, pageIndex, pageCount, capped, width, height, summary);
        FutureTask<WritableImage> task = new FutureTask<>(
                () -> render(report, entries, pageIndex, pageCount, capped, width, height, summary));
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("ساخت تصویر گزارش متوقف شد.", ex);
        } catch (ExecutionException ex) {
            throw new IOException("رندر گزارش در JavaFX ناموفق بود.", ex.getCause());
        }
    }

    private static WritableImage render(CustomerLedgerReport report, List<LedgerEntry> entries,
                                        int pageIndex, int pageCount, boolean capped,
                                        int width, int height, boolean summary) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(Color.web("#173f32"));
        g.setFont(Font.font("Vazirmatn", FontWeight.BOLD, 30));
        g.fillText("مرکز مشتریان  ·  گردش حساب", width - 48, 54);
        g.setFill(Color.web("#738277"));
        g.setFont(Font.font("Vazirmatn", 15));
        g.fillText("گزارش فقط‌خواندنی  ·  " + (pageCount > 1 ? "صفحهٔ " + (pageIndex + 1) + " از " + pageCount : ""), width - 48, 91);

        int y;
        if (summary) {
            g.setFill(Color.web("#f1f5ef"));
            g.fillRoundRect(38, 120, width - 76, 155, 18, 18);
            g.setFill(Color.web("#22372c"));
            g.setFont(Font.font("Vazirmatn", FontWeight.BOLD, 20));
            g.fillText(report.customer().customer().name(), width - 60, 151);
            g.setFont(Font.font("Vazirmatn", 15));
            g.setFill(Color.web("#576b5e"));
            g.fillText("کد " + report.customer().customer().code() + "   ·   " + rangeText(report), width - 60, 185);
            g.fillText("ماندهٔ ابتدا: " + money(report.openingBalance()) + "   |   بدهکار: " + money(report.debitTotal()), width - 60, 219);
            g.fillText("بستانکار: " + money(report.creditTotal()) + "   |   ماندهٔ پایان: " + money(report.closingBalance()), width - 60, 249);
            y = 310;
        } else {
            y = 126;
        }
        int rowHeight = height == PAGE_HEIGHT ? 45 : 38;
        int bottomMargin = height == PAGE_HEIGHT ? 70 : 22;
        int headerHeight = 44;
        int margin = 34;
        g.setFill(Color.web("#e8efe7"));
        g.fillRoundRect(margin, y, width - margin * 2, headerHeight, 10, 10);
        g.setFont(Font.font("Vazirmatn", FontWeight.BOLD, 14));
        g.setFill(Color.web("#40584a"));
        String[] headers = {"تاریخ", "سند", "عملیات", "شرح", "بدهکار", "بستانکار", "مانده"};
        int[] widths = {136, 112, 190, 370, 142, 142, 132};
        int right = width - margin - 10;
        for (int i = 0; i < headers.length; i++) {
            g.fillText(headers[i], right, y + headerHeight / 2, widths[i] - 10);
            right -= widths[i];
        }
        y += headerHeight;
        g.setFont(Font.font("Vazirmatn", 13));
        for (int i = 0; i < entries.size(); i++) {
            LedgerEntry entry = entries.get(i);
            if (y + rowHeight > height - bottomMargin) break;
            if ((i & 1) == 1) {
                g.setFill(Color.web("#f9fbf8"));
                g.fillRect(margin, y, width - margin * 2, rowHeight);
            }
            g.setStroke(Color.web("#edf1eb"));
            g.strokeLine(margin, y + rowHeight, width - margin, y + rowHeight);
            String[] values = {entry.date(), entry.documentNo(), entry.operation(), entry.description(),
                    money(entry.debit()), money(entry.credit()), money(entry.balance())};
            right = width - margin - 10;
            for (int col = 0; col < values.length; col++) {
                g.setFill(col == 4 && entry.debit().signum() > 0 ? Color.web("#a64c3f")
                        : col == 5 && entry.credit().signum() > 0 ? Color.web("#277251") : Color.web("#283a30"));
                g.fillText(values[col], right, y + rowHeight / 2, widths[col] - 10);
                right -= widths[col];
            }
            y += rowHeight;
        }
        if (capped || report.truncated()) {
            g.setFill(Color.web("#8a5b1e"));
            g.setFont(Font.font("Vazirmatn", 14));
            g.fillText("خروجی محدود شده است؛ برای مشاهدهٔ ادامه، فایل Excel یا PDF کامل‌تری بسازید.", width - 48, height - 30);
        }
        WritableImage image = new WritableImage(width, height);
        canvas.snapshot(null, image);
        return image;
    }

    private static String rangeText(CustomerLedgerReport report) {
        String from = report.fromJalali().isBlank() ? "ابتدای سوابق" : report.fromJalali();
        String to = report.toJalali().isBlank() ? "اکنون" : report.toJalali();
        return from + " تا " + to + "  ·  " + report.category().label();
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(PERSIAN);
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static void rowStart(StringBuilder out, int index) { out.append("<row r=\"").append(index).append("\">"); }
    private static void rowEnd(StringBuilder out) { out.append("</row>"); }

    private static void inline(StringBuilder out, String ref, String text, int style) {
        out.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
        if (style != 0) out.append(" s=\"").append(style).append("\"");
        out.append("><is><t xml:space=\"preserve\">").append(xml(text)).append("</t></is></c>");
    }

    private static void number(StringBuilder out, String ref, BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        out.append("<c r=\"").append(ref).append("\"><v>").append(safe.toPlainString()).append("</v></c>");
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static byte[] compressRgb(WritableImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) { writeRgbRows(deflater, image); }
        return bytes.toByteArray();
    }

    private static void writePng(OutputStream output, WritableImage image) throws IOException {
        DataOutputStream out = new DataOutputStream(output);
        out.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeInt((int) image.getWidth());
        h.writeInt((int) image.getHeight());
        h.writeByte(8); h.writeByte(2); h.writeByte(0); h.writeByte(0); h.writeByte(0);
        chunk(out, "IHDR", header.toByteArray());
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { writeRgbRows(deflater, image); }
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        out.flush();
    }

    private static void writeRgbRows(OutputStream output, WritableImage image) throws IOException {
        PixelReader reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        byte[] row = new byte[1 + width * 3];
        row[0] = 0;
        for (int y = 0; y < height; y++) {
            int index = 1;
            for (int x = 0; x < width; x++) {
                int argb = reader.getArgb(x, y);
                row[index++] = (byte) (argb >>> 16);
                row[index++] = (byte) (argb >>> 8);
                row[index++] = (byte) argb;
            }
            output.write(row);
        }
    }

    private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }

    private static void writeObject(ByteArrayOutputStream pdf, List<Integer> offsets, int id, byte[] body) throws IOException {
        offsets.add(pdf.size());
        writeAscii(pdf, id + " 0 obj\n");
        pdf.write(body);
        writeAscii(pdf, "\nendobj\n");
    }

    private static void writeImageObject(ByteArrayOutputStream pdf, List<Integer> offsets, int id,
                                         byte[] image, int width, int height) throws IOException {
        offsets.add(pdf.size());
        writeAscii(pdf, id + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + width
                + " /Height " + height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode "
                + "/DecodeParms << /Predictor 15 /Colors 3 /BitsPerComponent 8 /Columns " + width + " >> /Length " + image.length + " >>\nstream\n");
        pdf.write(image);
        writeAscii(pdf, "\nendstream\nendobj\n");
    }

    private static void writeStreamObject(ByteArrayOutputStream pdf, List<Integer> offsets, int id,
                                          String dictionary, byte[] data) throws IOException {
        offsets.add(pdf.size());
        writeAscii(pdf, id + " 0 obj\n" + dictionary + "\nstream\n");
        pdf.write(data);
        writeAscii(pdf, "\nendstream\nendobj\n");
    }

    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static void writeAscii(OutputStream output, String text) throws IOException { output.write(ascii(text)); }
}
