package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * High-quality table image (PNG) for instant sharing: title + gold-on-dark
 * header + zebra rows + totals, right-to-left, Vazirmatn throughout.
 */
public final class TableShot {
    private TableShot() { }

    public static File build(Context c, String title, String subtitle, ReportCatalog.Col[] cols,
                             List<Row> rows, int maxRows) throws Exception {
        int n = Math.min(rows.size(), Math.max(1, maxRows));
        String brand = LicenseStore.brandName(c);
        Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
        cell.setTextSize(30);
        cell.setTypeface(Theme.face(false));
        Paint moneyP = new Paint(Paint.ANTI_ALIAS_FLAG);
        moneyP.setTextSize(30);
        moneyP.setTypeface(Theme.face(true));
        // Measure columns (cap so one giant cell can't eat the image).
        int nc = cols.length + 1;
        float[] w = new float[nc];
        w[0] = 90;
        for (int j = 0; j < cols.length; j++) {
            float mw = cell.measureText(safe(cols[j].title)) + 60;
            Paint p = cols[j].type == ReportCatalog.T_MONEY ? moneyP : cell;
            for (int i = 0; i < n; i++) {
                float m = p.measureText(fmt(cols[j], rows.get(i))) + 60;
                if (m > mw) mw = m;
            }
            w[j + 1] = Math.min(mw, 640);
        }
        float totalW = 0;
        for (float v : w) totalW += v;
        int margin = 48;
        int W = Math.round(totalW) + margin * 2;
        int rowH = 58;
        int headH = 66;
        int H = margin + 90 + 52 + 24 + headH + n * rowH + rowH + 70 + margin;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(bmp);
        g.drawColor(0xFFFFFFFF);
        Paint fill = new Paint();
        fill.setColor(Theme.GOLD);
        g.drawRect(0, 0, W, 14, fill);
        Paint t1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        t1.setColor(0xFF101828);
        t1.setTextSize(46);
        t1.setTypeface(Theme.face(true));
        t1.setTextAlign(Paint.Align.RIGHT);
        g.drawText(safe(title), W - margin, margin + 62, t1);
        Paint t2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        t2.setColor(0xFF5A6478);
        t2.setTextSize(30);
        t2.setTypeface(Theme.face(false));
        t2.setTextAlign(Paint.Align.RIGHT);
        String sub = safe(subtitle);
        if (!sub.isEmpty()) sub += "  •  ";
        g.drawText(sub + brand + " • " + Money.fa(Jalali.todayStr()), W - margin, margin + 118, t2);
        // Header.
        float y = margin + 90 + 52 + 24;
        fill.setColor(0xFF101828);
        g.drawRect(margin, y, W - margin, y + headH, fill);
        Paint hh = new Paint(Paint.ANTI_ALIAS_FLAG);
        hh.setColor(0xFFF1D493);
        hh.setTextSize(31);
        hh.setTypeface(Theme.face(true));
        hh.setTextAlign(Paint.Align.CENTER);
        float x = W - margin;
        x -= w[0];
        g.drawText("ردیف", x + w[0] / 2f, y + 44, hh);
        for (int j = 0; j < cols.length; j++) {
            x -= w[j + 1];
            g.drawText(fit(safe(cols[j].title), w[j + 1] - 20, hh), x + w[j + 1] / 2f, y + 44, hh);
        }
        y += headH;
        // Rows.
        Paint zebra = new Paint();
        zebra.setColor(0xFFF4F1E8);
        Paint line = new Paint();
        line.setColor(0xFFE3DCC8);
        line.setStrokeWidth(2);
        cell.setTextAlign(Paint.Align.CENTER);
        moneyP.setTextAlign(Paint.Align.CENTER);
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setTextSize(29);
        num.setTypeface(Theme.face(false));
        num.setColor(0xFF5A6478);
        num.setTextAlign(Paint.Align.CENTER);
        double[] sums = new double[cols.length];
        for (int i = 0; i < n; i++) {
            if (i % 2 == 1) g.drawRect(margin, y, W - margin, y + rowH, zebra);
            Row r = rows.get(i);
            x = W - margin;
            x -= w[0];
            g.drawText(Money.fa(String.valueOf(i + 1)), x + w[0] / 2f, y + 39, num);
            for (int j = 0; j < cols.length; j++) {
                x -= w[j + 1];
                boolean isMoney = cols[j].type == ReportCatalog.T_MONEY;
                if (isMoney) sums[j] += r.d(cols[j].key);
                Paint p = isMoney ? moneyP : cell;
                p.setColor(isMoney ? 0xFF101828 : 0xFF333C4E);
                g.drawText(fit(fmt(cols[j], r), w[j + 1] - 20, p), x + w[j + 1] / 2f, y + 39, p);
            }
            g.drawLine(margin, y + rowH, W - margin, y + rowH, line);
            y += rowH;
        }
        // Totals.
        fill.setColor(0xFFF6E7C6);
        g.drawRect(margin, y, W - margin, y + rowH, fill);
        x = W - margin;
        x -= w[0];
        g.drawText("جمع", x + w[0] / 2f, y + 39, moneyP);
        for (int j = 0; j < cols.length; j++) {
            x -= w[j + 1];
            moneyP.setColor(0xFF101828);
            String t = cols[j].type == ReportCatalog.T_MONEY ? Money.rial(sums[j]) : "";
            g.drawText(fit(t, w[j + 1] - 20, moneyP), x + w[j + 1] / 2f, y + 39, moneyP);
        }
        y += rowH;
        Paint ft = new Paint(Paint.ANTI_ALIAS_FLAG);
        ft.setColor(0xFF9AA3B5);
        ft.setTextSize(26);
        ft.setTypeface(Theme.face(false));
        ft.setTextAlign(Paint.Align.CENTER);
        g.drawText(brand + " • " + Money.fa(Jalali.todayStr()), W / 2f, y + 46, ft);

        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".png");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) { }
            try {
                bmp.recycle();
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static String fmt(ReportCatalog.Col col, Row r) {
        if (col.type == ReportCatalog.T_MONEY) return Money.rial(r.d(col.key));
        if (col.type == ReportCatalog.T_NUM) {
            double v = r.d(col.key);
            if (Math.abs(v - Math.round(v)) < 0.001) return Money.num(Math.round(v));
            return Money.fa(String.format(java.util.Locale.US, "%.1f", v).replace('.', '٫').replace('-', '−'));
        }
        if (col.type == ReportCatalog.T_DATE) return Jalali.dispFa(r.s(col.key));
        String t = r.s(col.key, "—");
        return "—".equals(t) ? t : Money.fa(Jalali.faDate(t));
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
