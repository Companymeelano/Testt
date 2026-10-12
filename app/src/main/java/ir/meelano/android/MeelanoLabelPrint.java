package ir.meelano.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * چاپ برچسب انبار روی کاغذ A4 — با بارکد Code128 و کد QR، بدون چاپگر مخصوص برچسب.
 *
 * <p>هر برگه ۳ ستون × ۸ ردیف = ۲۴ برچسب ۶۳×۳۴ میلی‌متری دارد (اندازهٔ رایج برچسب‌های چسبی
 * A4 در ایران). هر برچسب شامل: نام کالا، کد، بارکد، QR شناسه (کد/سری/تاریخ) و قیمت است.
 * خروجی یک فایل PDF است که مستقیم از برنامه با هر چاپگر یا اپ PDF چاپ/اشتراک می‌شود.</p>
 */
final class MeelanoLabelPrint {
    private MeelanoLabelPrint() { }

    /** اندازهٔ A4 در پوینت (۷۲dpi). */
    private static final int A4_W = 595, A4_H = 842;
    private static final int COLS = 3, ROWS = 8;

    /** یک برچسب در برگه. */
    static final class Label {
        final String code, name;
        final double price;
        final int copies;
        Label(String code, String name, double price, int copies) {
            this.code = code == null ? "" : code.trim();
            this.name = name == null ? "" : name.trim();
            this.price = price;
            this.copies = Math.max(1, copies);
        }
    }

    /** برگهٔ کامل برچسب برای چند کالا. */
    static byte[] sheetPdf(Context ctx, List<Label> labels, MeelanoPalette palette, Typeface regular, Typeface bold) {
        if (labels == null || labels.isEmpty()) return null;
        List<Label> cells = new ArrayList<>();
        for (Label l : labels) for (int i = 0; i < l.copies; i++) cells.add(l);
        PdfDocument doc = new PdfDocument();
        try {
            int pageNo = 1;
            for (int start = 0; start < cells.size(); start += COLS * ROWS) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(A4_W, A4_H, pageNo).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas canvas = page.getCanvas();
                canvas.drawColor(Color.WHITE);
                float cellW = A4_W / (float) COLS;
                float cellH = A4_H / (float) ROWS;
                for (int i = 0; i < COLS * ROWS && start + i < cells.size(); i++) {
                    int col = i % COLS, row = i / COLS;
                    // ترتیب راست‌به‌چپ: ستون اول در سمت راست
                    float left = A4_W - (col + 1) * cellW;
                    float top = row * cellH;
                    drawLabel(canvas, new RectF(left + 6, top + 4, left + cellW - 6, top + cellH - 4), cells.get(start + i), palette, regular, bold);
                }
                doc.finishPage(page);
                pageNo++;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            try { doc.close(); } catch (Exception ignored) { }
        }
    }

    /** یک برگهٔ کامل از یک کالا (چند نسخه). */
    static byte[] singleLabelPdf(Context ctx, String code, String name, int copies, MeelanoPalette palette, Typeface regular, Typeface bold) {
        List<Label> one = new ArrayList<>();
        one.add(new Label(code, name, 0, copies));
        return sheetPdf(ctx, one, palette, regular, bold);
    }

    private static void drawLabel(Canvas canvas, RectF box, Label label, MeelanoPalette palette, Typeface regular, Typeface bold) {
        int ink = Color.rgb(18, 20, 26);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.7f);
        p.setColor(Color.rgb(190, 195, 205));
        canvas.drawRoundRect(box, 4, 4, p);

        // چیدمان: بالا نام کالا + کد | پایین: بارکد در سمت راست، QR در سمت چپ
        // منطقهٔ سکوت (quiet zone) سه ماژول گرفته می‌شود تا اسکنرهای ارزان گوشی هم مطمئن بخوانند.
        float qrSize = Math.min(box.height() * 0.56f, 60f);
        RectF qrArea = new RectF(box.left + 3, box.bottom - qrSize - 3, box.left + 3 + qrSize, box.bottom - 3);
        String qrPayload = label.code.isEmpty() ? label.name : label.code;
        try {
            MeelanoQr.draw(canvas, qrPayload, qrArea, ink, Color.TRANSPARENT, 3);
        } catch (Exception ignored) { }

        float textRight = box.right - 4;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(bold == null ? Typeface.DEFAULT_BOLD : bold);
        t.setColor(ink);
        t.setTextAlign(Paint.Align.RIGHT);
        t.setTextSize(9.4f);
        canvas.drawText(clip(label.name, 30), textRight, box.top + 11.5f, t);
        t.setTypeface(regular == null ? Typeface.DEFAULT : regular);
        t.setTextSize(7.6f);
        t.setColor(Color.rgb(70, 76, 90));
        canvas.drawText("کد: " + MeelanoCharts.fa(label.code), textRight, box.top + 21f, t);
        if (label.price > 0) {
            canvas.drawText("قیمت: " + MeelanoCharts.rial(label.price), textRight, box.top + 30f, t);
        }

        // بارکد: تمام عرض سمت راست تا کنار QR
        float barcodeBottom = box.bottom - 3;
        float barcodeTop = Math.max(box.top + 34f, barcodeBottom - 32f);
        RectF barcodeArea = new RectF(qrArea.right + 4, barcodeTop, box.right - 4, barcodeBottom);
        try {
            String data = label.code.isEmpty() ? label.name : label.code;
            int kind = MeelanoBarcode.kindFor(data);
            MeelanoBarcode.draw(canvas, data, kind == MeelanoBarcode.KIND_EAN13 ? MeelanoBarcode.KIND_CODE128 : kind,
                    barcodeArea, ink, Color.TRANSPARENT, true, 6.4f);
        } catch (Exception ignored) { }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    /** پیش‌نمایش برچسب به‌صورت Bitmap (برای نمایش در دیالوگ قبل از چاپ). */
    static android.graphics.Bitmap preview(Context ctx, Label label, MeelanoPalette palette, Typeface regular, Typeface bold, int widthPx, int heightPx) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(Math.max(120, widthPx), Math.max(70, heightPx), android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        drawLabel(canvas, new RectF(2, 2, bmp.getWidth() - 2, bmp.getHeight() - 2), label, palette, regular, bold);
        return bmp;
    }
}
