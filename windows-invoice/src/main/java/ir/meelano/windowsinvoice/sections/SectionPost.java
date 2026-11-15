package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionFilter;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionKpi;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionReport;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw rows a gateway read into the report the desktop shows: derived columns are computed
 * here, then the rows are date-filtered, sorted, summarized into KPI chips and paged.
 *
 * Both gateways use this same step, so a live Atiran database and the offline preview cannot drift
 * apart in how a margin, an ageing bucket or a total is calculated.
 */
public final class SectionPost {
    private SectionPost() { }

    /** Processes rows in the column order of the report definition. */
    public static SectionReport process(VariantSpec spec, SectionFilter filter, List<SectionRow> rawRows,
                                        boolean truncated, boolean demo, String todayJalali) {
        SectionFilter f = filter == null ? SectionFilter.defaults() : filter;
        List<SectionColumn> columns = spec.columns();
        List<Map<String, SectionValue>> prepared = new ArrayList<>();
        List<SectionRow> rows = new ArrayList<>();
        for (SectionRow raw : rawRows == null ? List.<SectionRow>of() : rawRows) {
            Map<String, SectionValue> byKey = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                SectionColumn column = columns.get(i);
                byKey.put(column.key(), spec.isDerived(column.key()) ? SectionModels.EMPTY : raw.at(i));
            }
            for (SectionCatalog.Derived derived : spec.derived()) {
                SectionValue value = SectionMath.derive(derived.kind(), byKey, derived.sources(),
                        derived.threshold(), todayJalali);
                byKey.put(derived.columnKey(), value);
            }
            List<SectionValue> values = new ArrayList<>(columns.size());
            for (SectionColumn column : columns) {
                SectionValue value = byKey.get(column.key());
                values.add(value == null ? SectionModels.EMPTY : value);
            }
            prepared.add(byKey);
            rows.add(new SectionRow(rowId(raw, values), values));
        }

        List<SectionRow> filtered = new ArrayList<>();
        List<Map<String, SectionValue>> filteredKeys = new ArrayList<>();
        int dateIndex = spec.dateKey().isEmpty() ? -1 : spec.columnIndex(spec.dateKey());
        for (int i = 0; i < rows.size(); i++) {
            SectionRow row = rows.get(i);
            if (dateIndex >= 0 && f.hasDateRange()) {
                String dateKey = row.keyAt(dateIndex);
                if (!SectionMath.withinRange(dateKey, f.fromJalali(), f.toJalali())) continue;
            }
            filtered.add(row);
            filteredKeys.add(prepared.get(i));
        }

        sort(spec, filtered, filteredKeys);

        BigDecimal threshold = SectionCatalog.thresholdOf(spec, f.threshold());
        List<SectionKpi> kpis = SectionMath.kpis(spec.kpis(), filteredKeys, filtered.size(), threshold);

        int page = Math.max(0, f.page());
        int pageSize = f.pageSize();
        int start = Math.min(filtered.size(), page * pageSize);
        int end = Math.min(filtered.size(), start + pageSize);
        List<SectionRow> pageRows = new ArrayList<>(filtered.subList(start, end));
        if (start > 0 && pageRows.isEmpty() && !filtered.isEmpty()) {
            // A page beyond the last row shows the final page instead of an empty table.
            int lastPage = Math.max(0, (filtered.size() + pageSize - 1) / pageSize - 1);
            start = Math.min(filtered.size(), lastPage * pageSize);
            end = Math.min(filtered.size(), start + pageSize);
            pageRows = new ArrayList<>(filtered.subList(start, end));
            page = lastPage;
        }

        return new SectionReport(spec.sectionId(), spec.id(), SectionCatalog.titleOf(spec.sectionId()),
                spec.label(), columns, pageRows, filtered.size(), kpis, f.fromJalali(), f.toJalali(),
                page, pageSize, truncated, demo, note(spec, f, todayJalali, truncated, demo, filtered.size()));
    }

    /** A report with no rows at all, used when Atiran's structure cannot serve this report. */
    public static SectionReport unavailable(VariantSpec spec, SectionFilter filter, String reason, boolean demo) {
        SectionFilter f = filter == null ? SectionFilter.defaults() : filter;
        return new SectionReport(spec.sectionId(), spec.id(), SectionCatalog.titleOf(spec.sectionId()),
                spec.label(), spec.columns(), List.of(), 0, List.of(), f.fromJalali(), f.toJalali(),
                0, f.pageSize(), false, demo, reason);
    }

    private static void sort(VariantSpec spec, List<SectionRow> rows, List<Map<String, SectionValue>> keys) {
        String sortKey = spec.sortKey();
        if (sortKey.isEmpty()) return;
        int index = spec.columnIndex(sortKey);
        if (index < 0) return;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) order.add(i);
        Comparator<Integer> comparator = (a, b) -> compare(rows.get(a).at(index), rows.get(b).at(index));
        if (spec.sortDescending()) comparator = comparator.reversed();
        // A stable secondary key keeps equal rows in the order Atiran returned them.
        comparator = comparator.thenComparingInt(Integer::intValue);
        order.sort(comparator);
        List<SectionRow> sortedRows = new ArrayList<>(rows.size());
        List<Map<String, SectionValue>> sortedKeys = new ArrayList<>(keys.size());
        for (int position : order) {
            sortedRows.add(rows.get(position));
            sortedKeys.add(keys.get(position));
        }
        rows.clear();
        rows.addAll(sortedRows);
        keys.clear();
        keys.addAll(sortedKeys);
    }

    /** Numeric cells compare by amount, everything else by its Latin-digit sort key. */
    static int compare(SectionValue left, SectionValue right) {
        BigDecimal leftNumber = left.number();
        BigDecimal rightNumber = right.number();
        if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber);
        if (leftNumber != null) return 1;
        if (rightNumber != null) return -1;
        String leftKey = left.key().isEmpty() ? left.text() : left.key();
        String rightKey = right.key().isEmpty() ? right.text() : right.key();
        return leftKey.compareTo(rightKey);
    }

    private static String rowId(SectionRow raw, List<SectionValue> values) {
        if (!raw.id().isEmpty()) return raw.id();
        for (SectionValue value : values) if (!value.key().isEmpty()) return value.key();
        return "";
    }

    /**
     * Provenance the operator must always be able to see: preview data, the effective date window,
     * the idle cut-off of "no activity since" reports and any read cap.
     */
    static String note(VariantSpec spec, SectionFilter filter, String today, boolean truncated,
                       boolean demo, int rowCount) {
        List<String> parts = new ArrayList<>();
        if (demo) parts.add("دادهٔ نمونهٔ نمایشی است و سند واقعی آتیران نیست.");
        String window = SectionMath.windowLabel(SectionMath.window(spec, filter, today));
        if (!window.isEmpty()) parts.add(window + ".");
        if (spec.dateKey().isEmpty() && spec.supportsDays()) {
            String cutoff = SectionMath.idleCutoff(spec, filter, today);
            if (!cutoff.isEmpty())
                parts.add("مبنای رکود: آخرین فعالیت پیش از " + SectionMath.digits(cutoff) + ".");
        }
        if (truncated) parts.add("خواندن در " + SectionMath.count(spec.maxRows()) + " ردیف متوقف شد؛"
                + " برای دیدن بقیه، بازهٔ تاریخ یا جست‌وجو را محدود کن.");
        if (rowCount == 0 && parts.isEmpty()) parts.add("در این بازه موردی برای نمایش وجود ندارد.");
        return String.join(" ", parts);
    }
}
