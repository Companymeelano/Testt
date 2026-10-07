package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** One generic PDF exporter: title + subtitle + table + footer. */
public final class Pdf {
    private Pdf() { }

    public static File build(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                             List<Row> rows, int maxRows) throws Exception {
        int W = 820;
        int H = 1120;
        int margin = 44;
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setTypeface(Theme.face(true));
        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setTypeface(Theme.face(true));
        Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG);
        muted.setColor(0xFF5A6478);
        // Column widths by type.
        int[] widths = new int[cols.length];
        int totalW = 0;
        for (int i = 0; i < cols.length; i++) {
            int w = cols[i].type == ReportCatalog.T_MONEY ? 150 : (cols[i].type == ReportCatalog.T_TEXT ? 170 : 110);
            widths[i] = w;
            totalW += w;
        }
        float scale = Math.min(1f, (W - margin * 2f) / Math.max(1, totalW));
        for (int i = 0; i < widths.length; i++) widths[i] = Math.round(widths[i] * scale);
        int n = Math.min(rows.size(), Math.max(1, maxRows));
        int rowH = 30;
        int headH = 40;
        int rowsPerPage = Math.max(5, (H - 210 - headH) / rowH);
        int pages = Math.max(1, (n + rowsPerPage - 1) / rowsPerPage);
        for (int pg = 0; pg < pages; pg++) {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, pg + 1).create();
            PdfDocument.Page page = doc.startPage(info);
            android.graphics.Canvas g = page.getCanvas();
            g.drawColor(0xFFFFFFFF);
            // gold top bar
            paint.setColor(0xFFD9AE5A);
            g.drawRect(0, 0, W, 10, paint);
            // title (right aligned)
            head.setColor(0xFF101828);
            head.setTextSize(21);
            head.setTextAlign(Paint.Align.RIGHT);
            g.drawText(safe(title), W - margin, 62, head);
            muted.setTextSize(12);
            muted.setTextAlign(Paint.Align.RIGHT);
            if (subtitle != null && !subtitle.isEmpty()) g.drawText(safe(subtitle), W - margin, 86, muted);
            g.drawText("مدیریت میلانو • " + Money.fa(Jalali.todayStr()), W - margin, 106, muted);
            // header row
            int y = 130;
            paint.setColor(0xFF101828);
            g.drawRect(margin, y, W - margin, y + headH, paint);
            bold.setColor(0xFFF1D493);
            bold.setTextSize(12.5f);
            bold.setTextAlign(Paint.Align.CENTER);
            int x = W - margin;
            for (int i = 0; i < cols.length; i++) {
                x -= widths[i];
                g.drawText(fit(cols[i].title, widths[i] - 10, bold), x + widths[i] / 2f, y + 26, bold);
            }
            y += headH;
            // rows
            Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
            cell.setTextSize(11.5f);
            cell.setTextAlign(Paint.Align.CENTER);
            Paint zebra = new Paint();
            zebra.setColor(0xFFF4F1E8);
            int from = pg * rowsPerPage;
            int to = Math.min(n, from + rowsPerPage);
            for (int i = from; i < to; i++) {
                if ((i - from) % 2 == 1) g.drawRect(margin, y, W - margin, y + rowH, zebra);
                Row r = rows.get(i);
                x = W - margin;
                for (int j = 0; j < cols.length; j++) {
                    x -= widths[j];
                    cell.setColor(cols[j].type == ReportCatalog.T_MONEY ? 0xFF101828 : 0xFF333C4E);
                    cell.setTypeface(cols[j].type == ReportCatalog.T_MONEY ? Theme.face(true) : Theme.face(false));
                    g.drawText(fit(fmt(cols[j], r), widths[j] - 10, cell), x + widths[j] / 2f, y + 20, cell);
                }
                y += rowH;
            }
            // footer
            muted.setTextSize(11);
            muted.setTextAlign(Paint.Align.CENTER);
            g.drawText("صفحه " + Money.fa(String.valueOf(pg + 1)) + " از " + Money.fa(String.valueOf(pages)), W / 2f, H - 30, muted);
            doc.finishPage(page);
        }
        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        }
        try { doc.close(); } catch (Exception ignored) { }
        return out;
    }

    private static String fmt(ReportCatalog.Col col, Row r) {
        if (col.type == ReportCatalog.T_MONEY) return Money.rial(r.d(col.key));
        if (col.type == ReportCatalog.T_NUM) {
            double v = r.d(col.key);
            if (Math.abs(v - Math.round(v)) < 0.001) return Money.num(Math.round(v));
            return Money.fa(String.format(java.util.Locale.US, "%.1f", v).replace('.', '٫'));
        }
        if (col.type == ReportCatalog.T_DATE) {
            String d = r.s(col.key);
            if (d.length() >= 10) d = d.substring(0, 10);
            return Money.fa(d);
        }
        return r.s(col.key, "—");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String fit(String s, float maxW, Paint p) {
        if (s == null) return "";
        if (p.measureText(s) <= maxW) return s;
        String ell = "…";
        int len = s.length();
        while (len > 1 && p.measureText(s.substring(0, len) + ell) > maxW) len--;
        return s.substring(0, len) + ell;
    }
}
