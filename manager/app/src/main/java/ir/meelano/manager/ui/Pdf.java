package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** One generic PDF exporter: title + subtitle + table + footer. */
public final class Pdf {
    private Pdf() { }

    /** One image for the appendix (signature / photo) with a Persian caption. */
    public static final class Img {
        public final android.graphics.Bitmap bmp;
        public final String cap;

        public Img(android.graphics.Bitmap bmp, String cap) {
            this.bmp = bmp;
            this.cap = cap;
        }
    }

    public static File build(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                             List<Row> rows, int maxRows) throws Exception {
        return buildFull(c, title, subtitle, cols, rows, maxRows, null);
    }

    /** Same report plus an image appendix (each image on its own page). */
    public static File buildFull(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                                 List<Row> rows, int maxRows,
                                 java.util.List<Img> images) throws Exception {
        int margin = 44;
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setTypeface(Theme.face(true));
        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setTypeface(Theme.face(true));
        Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG);
        muted.setColor(DesignTokens.LIGHT_MUTED);
        // ---- content-aware column plan ---------------------------------------------
        // Measure what each column actually needs (heading + widest real cell) instead of
        // guessing by type, then let ReportTablePlan squeeze between minimum and preferred
        // so money digits are never cut mid-number on wide aging tables.
        Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
        measure.setTextSize(ir.meelano.manager.core.ReportTablePlan.FONT_STEPS[0]);
        double[] preferred = new double[cols.length];
        double[] minimum = new double[cols.length];
        double[] needFull = new double[cols.length];
        double[] needCompact = new double[cols.length];
        boolean[] moneyCol = new boolean[cols.length];
        boolean[] useCompact = new boolean[cols.length];
        float[] sizes = new float[cols.length];
        int scan = Math.min(rows.size(), 300);
        for (int i = 0; i < cols.length; i++) {
            moneyCol[i] = cols[i].type == ReportCatalog.T_MONEY;
            boolean numeric = moneyCol[i] || cols[i].type == ReportCatalog.T_NUM;
            measure.setTypeface(numeric ? Theme.face(true) : Theme.face(false));
            bold.setTextSize(12.5f);
            double headW = bold.measureText(cols[i].title) + 14;
            preferred[i] = headW;
            needFull[i] = headW;
            for (int k = 0; k < scan; k++) {
                String full = fmt(cols[i], rows.get(k));
                double wFull = measure.measureText(full) + 14;
                if (wFull > preferred[i]) preferred[i] = wFull;
                if (wFull > needFull[i]) needFull[i] = wFull;
                if (moneyCol[i]) {
                    double wC = measure.measureText(compactText(cols[i], rows.get(k))) + 14;
                    if (wC > needCompact[i]) needCompact[i] = wC;
                }
            }
            if (moneyCol[i] && needCompact[i] <= 0d) needCompact[i] = needFull[i];
            minimum[i] = Math.max(46d, Math.min(headW + 6d, 96d));
        }
        double portraitAvail = 820 - margin * 2d;
        double prefTotal = 0d;
        for (double p : preferred) prefTotal += p;
        boolean wide = ir.meelano.manager.core.ReportTablePlan.landscape(
                cols.length, prefTotal, portraitAvail);
        int W = wide ? 1120 : 820;
        int H = wide ? 820 : 1120;
        double avail = W - margin * 2d;
        double[] plan = ir.meelano.manager.core.ReportTablePlan.widths(preferred, minimum, avail);
        int[] widths = new int[cols.length];
        for (int i = 0; i < cols.length; i++) {
            widths[i] = Math.max(30, (int) Math.round(plan[i]));
            double alloc = plan[i] - 10d;
            float base = ir.meelano.manager.core.ReportTablePlan.FONT_STEPS[0];
            if (!moneyCol[i]) {
                sizes[i] = ir.meelano.manager.core.ReportTablePlan.FONT_STEPS[
                        ir.meelano.manager.core.ReportTablePlan.fontStep(needFull[i], alloc)];
            } else if (needFull[i] <= alloc) {
                sizes[i] = base;
            } else if (needCompact[i] <= alloc) {
                sizes[i] = base;
                useCompact[i] = true;
            } else {
                useCompact[i] = true;
                sizes[i] = ir.meelano.manager.core.ReportTablePlan.FONT_STEPS[
                        ir.meelano.manager.core.ReportTablePlan.fontStep(needCompact[i], alloc)];
            }
        }
        int n = Math.min(rows.size(), Math.max(1, maxRows));
        int rowH = 30;
        int headH = 40;
        int rowsPerPage = Math.max(5, (H - 210 - headH) / rowH);
        int pages = Math.max(1, (n + rowsPerPage - 1) / rowsPerPage);
        for (int pg = 0; pg < pages; pg++) {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, pg + 1).create();
            PdfDocument.Page page = doc.startPage(info);
            android.graphics.Canvas g = page.getCanvas();
            g.drawColor(DesignTokens.IVORY);
            // accent top bar (follows the user's theme color)
            // Premium band: molten gold sweeping across the page head, closed by a
            // dark-gold thread underneath.
            android.graphics.LinearGradient goldBand = new android.graphics.LinearGradient(
                    0, 0, W, 0,
                    new int[]{DesignTokens.GOLD_DARK, DesignTokens.GOLD,
                            DesignTokens.GOLD_LIGHT, DesignTokens.GOLD},
                    new float[]{0f, 0.42f, 0.72f, 1f},
                    android.graphics.Shader.TileMode.CLAMP);
            paint.setShader(goldBand);
            g.drawRect(0, 0, W, 10, paint);
            paint.setShader(null);
            paint.setColor(DesignTokens.GOLD_DARK);
            g.drawRect(0, 10, W, 11.5f, paint);
            // title (right aligned)
            head.setColor(DesignTokens.INK);
            head.setTextSize(21);
            head.setTextAlign(Paint.Align.RIGHT);
            g.drawText(HomeNumberFormatter.rtlText(safe(title)), W - margin, 62, head);
            muted.setTextSize(12);
            muted.setTextAlign(Paint.Align.RIGHT);
            if (subtitle != null && !subtitle.isEmpty()) g.drawText(safe(subtitle), W - margin, 86, muted);
            g.drawText(HomeNumberFormatter.rtlText(LicenseStore.brandName(c) + " • "
                    + HomeNumberFormatter.dateText(Jalali.todayStr())), W - margin, 106, muted);
            // header row
            int y = 130;
            // Deep-emerald header with a metallic sweep and a gold thread below it.
            android.graphics.LinearGradient headBand = new android.graphics.LinearGradient(
                    margin, y, W - margin, y,
                    new int[]{DesignTokens.EMERALD_DEEP, DesignTokens.EMERALD,
                            DesignTokens.EMERALD_DEEP},
                    null, android.graphics.Shader.TileMode.CLAMP);
            paint.setShader(headBand);
            g.drawRect(margin, y, W - margin, y + headH, paint);
            paint.setShader(null);
            paint.setColor(DesignTokens.GOLD);
            g.drawRect(margin, y + headH, W - margin, y + headH + 1.5f, paint);
            bold.setColor(DesignTokens.GOLD_LIGHT);
            bold.setTextSize(cols.length >= 10 ? 10.5f : 12.5f);
            bold.setTextAlign(Paint.Align.CENTER);
            int x = W - margin;
            for (int i = 0; i < cols.length; i++) {
                x -= widths[i];
                g.drawText(HomeNumberFormatter.rtlText(
                        fit(cols[i].title, widths[i] - 10, bold)), x + widths[i] / 2f, y + 26, bold);
            }
            y += headH;
            // rows
            Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
            cell.setTextSize(11.5f);
            cell.setTextAlign(Paint.Align.CENTER);
            Paint zebra = new Paint();
            zebra.setColor(DesignTokens.PAPER);
            int from = pg * rowsPerPage;
            int to = Math.min(n, from + rowsPerPage);
            for (int i = from; i < to; i++) {
                if ((i - from) % 2 == 1) g.drawRect(margin, y, W - margin, y + rowH, zebra);
                Row r = rows.get(i);
                x = W - margin;
                for (int j = 0; j < cols.length; j++) {
                    x -= widths[j];
                    cell.setColor(DesignTokens.INK);
                    boolean numeric = moneyCol[j] || cols[j].type == ReportCatalog.T_NUM;
                    cell.setTypeface(numeric ? Theme.face(true) : Theme.face(false));
                    cell.setTextSize(sizes[j]);
                    // Every cell rides in its own RTL isolate so digits and decimal
                    // separators keep their reading order inside the table.
                    String t = HomeNumberFormatter.rtlText(cellText(cols[j], r, useCompact[j]));
                    // Numbers are never ellipsized; only prose columns may fade into "…".
                    if (cols[j].type == ReportCatalog.T_TEXT) t = fit(t, widths[j] - 10, cell);
                    g.drawText(t, x + widths[j] / 2f, y + 20, cell);
                }
                y += rowH;
            }
            // footer
            // A champagne hairline crowns the footer block before the page mark.
            paint.setColor(DesignTokens.GOLD_LIGHT);
            g.drawRect(margin, H - 62, W - margin, H - 61, paint);
            muted.setTextSize(11);
            muted.setTextAlign(Paint.Align.CENTER);
            g.drawText(HomeNumberFormatter.rtlText("صفحه "
                    + HomeNumberFormatter.integer(pg + 1) + " از "
                    + HomeNumberFormatter.integer(pages)), W / 2f, H - 30, muted);
            muted.setTextSize(10);
            g.drawText("Milad Yaghoobi • طراح و توسعه‌دهنده", W / 2f, H - 48, muted);
            doc.finishPage(page);
        }
        // ---- image appendix ----
        if (images != null) {
            for (Img im : images) {
                if (im == null || im.bmp == null || im.bmp.isRecycled()) continue;
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, 999).create();
                PdfDocument.Page page = doc.startPage(info);
                android.graphics.Canvas g = page.getCanvas();
                g.drawColor(DesignTokens.IVORY);
                android.graphics.LinearGradient appendixBand = new android.graphics.LinearGradient(
                        0, 0, W, 0,
                        new int[]{DesignTokens.GOLD_DARK, DesignTokens.GOLD,
                                DesignTokens.GOLD_LIGHT, DesignTokens.GOLD},
                        new float[]{0f, 0.42f, 0.72f, 1f},
                        android.graphics.Shader.TileMode.CLAMP);
                paint.setShader(appendixBand);
                g.drawRect(0, 0, W, 10, paint);
                paint.setShader(null);
                paint.setColor(DesignTokens.GOLD_DARK);
                g.drawRect(0, 10, W, 11.5f, paint);
                head.setColor(DesignTokens.INK);
                head.setTextSize(19);
                head.setTextAlign(Paint.Align.RIGHT);
                g.drawText(HomeNumberFormatter.rtlText(safe(im.cap)), W - margin, 62, head);
                int iw = im.bmp.getWidth(), ih = im.bmp.getHeight();
                float maxW = W - margin * 2f, maxH = H - 220f;
                float sc = Math.min(maxW / Math.max(1, iw), maxH / Math.max(1, ih));
                sc = Math.min(1f, sc);
                float dw = iw * sc, dh = ih * sc;
                float dx = (W - dw) / 2f, dy = 110 + (maxH - dh) / 2f;
                android.graphics.RectF dst = new android.graphics.RectF(dx, dy, dx + dw, dy + dh);
                Paint imgP = new Paint(Paint.FILTER_BITMAP_FLAG);
                g.drawRect(dst.left - 6, dst.top - 6, dst.right + 6, dst.bottom + 6, muted);
                g.drawBitmap(im.bmp, null, dst, imgP);
                muted.setTextSize(11);
                muted.setTextAlign(Paint.Align.CENTER);
                g.drawText(HomeNumberFormatter.rtlText("پیوست • "
                        + HomeNumberFormatter.dateText(Jalali.todayStr())), W / 2f, H - 30, muted);
                doc.finishPage(page);
            }
        }
        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".pdf");
        try {
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }
        } finally {
            try { doc.close(); } catch (Exception ignored) { Diag.ignored("Pdf:163", ignored); }
        }
        return out;
    }

    private static String fmt(ReportCatalog.Col col, Row r) {
        if (col.type == ReportCatalog.T_MONEY) {
            double v = r.d(col.key);
            // A zero amount reads as a quiet dash; "تومان۰" buried whole columns in noise.
            if (Math.abs(v) < 0.005d) return "—";
            return Money.rial(v);
        }
        if (col.type == ReportCatalog.T_NUM) {
            double v = r.d(col.key);
            if (Math.abs(v - Math.round(v)) < 0.001) return HomeNumberFormatter.rounded(v);
            return HomeNumberFormatter.decimal(v);
        }
        if (col.type == ReportCatalog.T_DATE) {
            return HomeNumberFormatter.dateTimeTable(r.s(col.key));
        }
        String t = r.s(col.key, "—");
        if ("—".equals(t)) return t;
        return Jalali.disp(t).isEmpty() ? HomeNumberFormatter.localizeText(t)
                : HomeNumberFormatter.dateTimeTable(t);
    }

    /** Compact money ("۱٬۸ میلیون تومان") — the fallback when a column runs out of room. */
    private static String compactText(ReportCatalog.Col col, Row r) {
        double v = r.d(col.key);
        if (Math.abs(v) < 0.005d) return "—";
        return HomeNumberFormatter.compactMoney(v);
    }

    private static String cellText(ReportCatalog.Col col, Row r, boolean compact) {
        if (compact && col.type == ReportCatalog.T_MONEY) return compactText(col, r);
        return fmt(col, r);
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
