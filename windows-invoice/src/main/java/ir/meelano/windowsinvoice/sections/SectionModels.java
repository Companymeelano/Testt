package ir.meelano.windowsinvoice.sections;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Read-only models for the Manager sections that the Windows client opens on the desktop:
 * cash box, cheques, stock ledger, sales/finance reports, profit, production, personnel and users.
 *
 * Everything here is plain Java (no JavaFX, no JDBC driver types) so the section logic can be
 * compiled and exercised without a display or a live database. Values keep three projections:
 * the Persian display text, a Latin-digit sort/filter key and the exact numeric amount.
 */
public final class SectionModels {
    private SectionModels() { }

    /** An empty cell; used whenever a row is shorter than the column list. */
    public static final SectionValue EMPTY = new SectionValue("", "", null);

    public enum Kind {
        TEXT, MONEY, NUMBER, DATE, STATUS;

        public boolean numeric() { return this == MONEY || this == NUMBER; }
    }

    /** One report column: stable key (CSV/KPI reference), Persian label, kind, relative width, value mapping. */
    public record SectionColumn(String key, String label, Kind kind, double width, String mapping) {
        public SectionColumn {
            key = clean(key);
            label = clean(label);
            mapping = clean(mapping);
            kind = Objects.requireNonNullElse(kind, Kind.TEXT);
            if (key.isEmpty()) throw new IllegalArgumentException("ستون گزارش باید کلید داشته باشد.");
            if (label.isEmpty()) label = key;
            width = Math.max(0.4, Math.min(4.0, width));
        }

        public static SectionColumn text(String key, String label, double width) {
            return new SectionColumn(key, label, Kind.TEXT, width, "");
        }

        public static SectionColumn money(String key, String label, double width) {
            return new SectionColumn(key, label, Kind.MONEY, width, "");
        }

        public static SectionColumn number(String key, String label, double width) {
            return new SectionColumn(key, label, Kind.NUMBER, width, "");
        }

        public static SectionColumn date(String key, String label, double width) {
            return new SectionColumn(key, label, Kind.DATE, width, "");
        }

        public static SectionColumn status(String key, String label, double width, String mapping) {
            return new SectionColumn(key, label, Kind.STATUS, width, mapping);
        }

        public boolean numeric() { return kind.numeric(); }
    }

    /**
     * A finalized cell. {@code text} is what the operator sees (Persian digits), {@code key} is the
     * Latin-digit value used for date/text filtering and sorting, {@code number} is the exact amount.
     */
    public record SectionValue(String text, String key, BigDecimal number) {
        public SectionValue {
            text = clean(text);
            key = clean(key);
        }

        /** True when the cell carries no data at all; the "—" placeholder counts as empty. */
        public boolean isBlank() {
            return (text.isEmpty() || text.equals("—")) && key.isEmpty()
                    && (number == null || number.signum() == 0);
        }

        /** Numeric value or zero, so sums never need a null check. */
        public BigDecimal amount() { return number == null ? BigDecimal.ZERO : number; }
    }

    /** One report row; values are positional and align with {@link SectionReport#columns()}. */
    public record SectionRow(String id, List<SectionValue> values) {
        public SectionRow {
            id = clean(id);
            values = values == null ? List.of() : List.copyOf(values);
        }

        public SectionValue at(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : EMPTY;
        }

        public String textAt(int index) { return at(index).text(); }

        public String keyAt(int index) {
            SectionValue value = at(index);
            return value.key().isEmpty() ? value.text() : value.key();
        }

        public BigDecimal numberAt(int index) { return at(index).amount(); }
    }

    /** A summary chip above the table (label, formatted value, optional explanatory hint). */
    public record SectionKpi(String label, String value, String hint) {
        public SectionKpi {
            label = clean(label);
            value = clean(value);
            hint = clean(hint);
        }
    }

    /** One selectable report inside a section, e.g. "چک‌های دریافتی" inside the cheque section. */
    public record SectionVariant(String id, String label, String hint) {
        public SectionVariant {
            id = clean(id);
            label = clean(label);
            hint = clean(hint);
            if (id.isEmpty()) throw new IllegalArgumentException("گزارش باید شناسه داشته باشد.");
            if (label.isEmpty()) label = id;
        }
    }

    /**
     * Section filters. Jalali bounds are inclusive and an empty bound means open-ended; {@code context}
     * is the variant-specific second key (bank, warehouse, visitor or user); {@code threshold} is a
     * percent for margin reports and {@code days} a look-back window, zero meaning "variant default".
     */
    public record SectionFilter(String query, String context, String fromJalali, String toJalali,
                                BigDecimal threshold, int days, int page, int pageSize) {
        public SectionFilter {
            query = clean(query);
            context = clean(context);
            fromJalali = SectionMath.normalizeJalali(fromJalali);
            toJalali = SectionMath.normalizeJalali(toJalali);
            if (!fromJalali.isEmpty() && !ir.meelano.windowsinvoice.PersianDate.isValidJalaliDate(fromJalali))
                throw new IllegalArgumentException("تاریخ شروع را به شکل ۱۴۰۵/۰۸/۰۱ وارد کنید.");
            if (!toJalali.isEmpty() && !ir.meelano.windowsinvoice.PersianDate.isValidJalaliDate(toJalali))
                throw new IllegalArgumentException("تاریخ پایان را به شکل ۱۴۰۵/۰۸/۳۰ وارد کنید.");
            if (!fromJalali.isEmpty() && !toJalali.isEmpty() && fromJalali.compareTo(toJalali) > 0)
                throw new IllegalArgumentException("تاریخ شروع نمی‌تواند بعد از تاریخ پایان باشد.");
            if (threshold != null && (threshold.compareTo(BigDecimal.ZERO) < 0
                    || threshold.compareTo(new BigDecimal("100")) > 0))
                throw new IllegalArgumentException("درصد آستانه باید بین ۰ و ۱۰۰ باشد.");
            days = Math.max(0, Math.min(3650, days));
            page = Math.max(0, page);
            pageSize = Math.max(20, Math.min(500, pageSize));
        }

        public static SectionFilter defaults() {
            return new SectionFilter("", "", "", "", null, 0, 0, 200);
        }

        public SectionFilter onPage(int value) {
            return new SectionFilter(query, context, fromJalali, toJalali, threshold, days,
                    Math.max(0, value), pageSize);
        }

        public boolean hasDateRange() { return !fromJalali.isEmpty() || !toJalali.isEmpty(); }
    }

    /** What the UI asks the gateway for: one section, one report, the caller's role and the filters. */
    public record SectionRequest(String sectionId, String variantId, String role, SectionFilter filter) {
        public SectionRequest {
            sectionId = clean(sectionId);
            variantId = clean(variantId);
            role = clean(role);
            filter = Objects.requireNonNullElseGet(filter, SectionFilter::defaults);
            if (sectionId.isEmpty()) throw new IllegalArgumentException("بخش مورد نظر را انتخاب کنید.");
        }

        public SectionRequest withFilter(SectionFilter value) {
            return new SectionRequest(sectionId, variantId, role, value);
        }

        public SectionRequest withVariant(String value) {
            return new SectionRequest(sectionId, value, role, filter);
        }
    }

    /** One rendered report page: columns, the rows of this page, totals, KPI chips and provenance. */
    public record SectionReport(String sectionId, String variantId, String title, String variantLabel,
                                List<SectionColumn> columns, List<SectionRow> rows, long totalRows,
                                List<SectionKpi> kpis, String fromJalali, String toJalali,
                                int page, int pageSize, boolean truncated, boolean demo, String note) {
        public SectionReport {
            sectionId = clean(sectionId);
            variantId = clean(variantId);
            title = clean(title);
            variantLabel = clean(variantLabel);
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
            kpis = kpis == null ? List.of() : List.copyOf(kpis);
            totalRows = Math.max(0, totalRows);
            fromJalali = clean(fromJalali);
            toJalali = clean(toJalali);
            page = Math.max(0, page);
            pageSize = Math.max(1, pageSize);
            note = clean(note);
        }

        public int pageCount() { return (int) Math.max(1, (totalRows + pageSize - 1) / pageSize); }

        public int indexOf(String columnKey) {
            for (int i = 0; i < columns.size(); i++) if (columns.get(i).key().equals(columnKey)) return i;
            return -1;
        }

        public boolean isEmpty() { return rows.isEmpty(); }

        /** The same report with another page of rows; totals, KPIs and provenance stay untouched. */
        public SectionReport withRows(List<SectionRow> pageRows, long total, int pageIndex) {
            return new SectionReport(sectionId, variantId, title, variantLabel, columns, pageRows, total,
                    kpis, fromJalali, toJalali, pageIndex, pageSize, truncated, demo, note);
        }

        public SectionReport withNote(String value) {
            return new SectionReport(sectionId, variantId, title, variantLabel, columns, rows, totalRows,
                    kpis, fromJalali, toJalali, page, pageSize, truncated, demo, value);
        }
    }

    /** Raised when Atiran's structure does not carry the table/column a report needs. */
    public static final class SectionUnavailable extends Exception {
        private static final long serialVersionUID = 1L;

        public SectionUnavailable(String message) { super(message); }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
