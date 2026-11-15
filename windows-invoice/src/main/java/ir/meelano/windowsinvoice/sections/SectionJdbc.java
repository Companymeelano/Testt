package ir.meelano.windowsinvoice.sections;

import ir.meelano.windowsinvoice.sections.SectionCatalog.VariantSpec;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionColumn;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionRow;
import ir.meelano.windowsinvoice.sections.SectionModels.SectionValue;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a section {@link ResultSet} into report rows using the catalog definition.
 *
 * Only the columns the SQL actually selects are read; columns derived in Java stay empty here and are
 * filled by {@link SectionPost}. A result set that does not match the report definition is refused
 * with a Persian message instead of being shifted into the wrong columns.
 */
public final class SectionJdbc {
    private SectionJdbc() { }

    /** Reads at most {@code maxRows} rows; reaching the cap is reported by the caller as truncation. */
    public static List<SectionRow> read(ResultSet rs, VariantSpec spec, int maxRows) throws SQLException {
        List<SectionColumn> columns = spec.columns();
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            if (!spec.isDerived(columns.get(i).key())) sourceIndexes.add(sourceIndexes.size() + 1);
        }
        ResultSetMetaData metadata = rs.getMetaData();
        int available = metadata.getColumnCount();
        if (available < sourceIndexes.size())
            throw new SQLException("پرس‌وجوی گزارش «" + spec.label() + "» " + sourceIndexes.size()
                    + " ستون برمی‌گرداند اما " + available + " ستون دریافت شد.");
        List<SectionRow> rows = new ArrayList<>();
        int cap = Math.max(1, maxRows);
        while (rs.next() && rows.size() < cap) {
            List<SectionValue> values = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                SectionColumn column = columns.get(i);
                if (spec.isDerived(column.key())) {
                    values.add(SectionModels.EMPTY);
                    continue;
                }
                int index = sourceIndexes.get(numberOfSource(columns, spec, i));
                if (column.numeric()) values.add(SectionMath.value(column, null, decimal(rs, index)));
                else values.add(SectionMath.value(column, text(rs, index), null));
            }
            rows.add(new SectionRow(rowId(values), values));
        }
        return rows;
    }

    /** Position of a column inside the SQL result set (derived columns are not selected). */
    private static int numberOfSource(List<SectionColumn> columns, VariantSpec spec, int index) {
        int source = 0;
        for (int i = 0; i < index; i++) if (!spec.isDerived(columns.get(i).key())) source++;
        return source;
    }

    private static String text(ResultSet rs, int index) throws SQLException {
        String value = rs.getString(index);
        return value == null ? "" : value;
    }

    private static BigDecimal decimal(ResultSet rs, int index) throws SQLException {
        BigDecimal value = rs.getBigDecimal(index);
        if (value != null) return value;
        // A numeric column that holds text (Atiran keeps quantities as char on some installs).
        String raw = SectionMath.latinDigits(text(rs, index)).replace(",", "").replace("٬", "").trim();
        if (raw.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(raw); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }

    private static String rowId(List<SectionValue> values) {
        for (SectionValue value : values) {
            if (!value.key().isEmpty()) return value.key();
        }
        return "";
    }
}
