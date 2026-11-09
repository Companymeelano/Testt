package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.PersianDate;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports one rendered section report as CSV.
 *
 * The file is written with a UTF-8 BOM so Excel opens Persian text correctly, amounts are exported as
 * plain Latin-digit numbers (never as display text) so they stay usable in a spreadsheet, and every
 * cell is neutralized against formula injection: a leading =, +, -, @, tab or carriage return is
 * prefixed with a single quote, so an exported report can never execute in the operator's workbook.
 */
public final class SectionCsv {
    private SectionCsv() { }

    private static final char DELIMITER = ',';
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** UTF-8 bytes (with BOM) of the report: title block, KPI chips, header row and the visible rows. */
    public static byte[] exportBytes(SectionReport report) {
        return export(report).getBytes(StandardCharsets.UTF_8);
    }

    public static String export(SectionReport report) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append(line("گزارش", report.title() + " · " + report.variantLabel()));
        out.append(line("منبع داده", report.demo() ? "پیش‌نمایش محلی (دادهٔ نمونه)" : "آتیران · فقط‌خواندنی"));
        if (!report.fromJalali().isEmpty() || !report.toJalali().isEmpty()) {
            out.append(line("بازه", (report.fromJalali().isEmpty() ? "ابتدا" : report.fromJalali())
                    + " تا " + (report.toJalali().isEmpty() ? "امروز" : report.toJalali())));
        }
        out.append(line("تعداد ردیف", Long.toString(report.totalRows())));
        if (!report.note().isEmpty()) out.append(line("یادداشت", report.note()));
        for (SectionKpi kpi : report.kpis()) {
            out.append(line(kpi.label(), SectionMath.latinDigits(kpi.value())));
        }
        out.append('\n');
        List<String> header = new ArrayList<>();
        for (SectionColumn column : report.columns()) header.add(column.label());
        out.append(join(header)).append("\r\n");
        for (SectionRow row : report.rows()) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < report.columns().size(); i++) {
                SectionColumn column = report.columns().get(i);
                SectionModels.SectionValue value = row.at(i);
                if (column.numeric() && value.number() != null) cells.add(value.number().toPlainString());
                else cells.add(SectionMath.csvSafe(SectionMath.latinDigits(value.text())));
            }
            out.append(join(cells)).append("\r\n");
        }
        return out.toString();
    }

    /** Suggested file name for the save dialog, e.g. صندوق-خلاصه-روزانه-1405-08-03.csv. */
    public static String fileName(SectionReport report) {
        String section = sanitize(report.title());
        String variant = sanitize(report.variantLabel());
        String date = PersianDate.today().replace('/', '-');
        return section + "-" + variant + "-" + date + ".csv";
    }

    /** Plain-text summary of the report, used by the print/handoff path and the status line. */
    public static String summary(SectionReport report) {
        StringBuilder out = new StringBuilder();
        out.append(report.title()).append(" · ").append(report.variantLabel()).append('\n');
        for (SectionKpi kpi : report.kpis()) out.append("  ").append(kpi.label()).append(": ").append(kpi.value()).append('\n');
        out.append("  ردیف‌ها: ").append(SectionMath.count(report.totalRows()));
        if (report.truncated()) out.append(" (خواندن محدود شد)");
        out.append('\n');
        if (!report.note().isEmpty()) out.append("  ").append(report.note()).append('\n');
        return out.toString();
    }

    private static String line(String label, String value) {
        return quote(label) + DELIMITER + quote(SectionMath.csvSafe(value)) + "\r\n";
    }

    private static String join(List<String> cells) {
        List<String> quoted = new ArrayList<>(cells.size());
        for (String cell : cells) quoted.add(quote(SectionMath.csvSafe(cell)));
        return String.join(String.valueOf(DELIMITER), quoted);
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(DELIMITER) < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0
                && text.indexOf('\r') < 0) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static String sanitize(String value) {
        String text = value == null ? "" : value.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean safe = Character.isLetterOrDigit(ch) || ch == '-' || ch == '_'
                    || Character.UnicodeScript.of(ch) == Character.UnicodeScript.ARABIC;
            if (safe) out.append(ch);
        }
        return out.length() == 0 ? "گزارش" : out.toString();
    }
}
