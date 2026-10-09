package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import ir.meelano.manager.ShareProvider;
import ir.meelano.manager.core.Money;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Shelf labels with a scannable Code-39 barcode (drawn manually, no
 * dependency): product name + code + unit on a small PDF, ready to share
 * to any printer app.
 */
public final class LabelPrint {
    private LabelPrint() { }

    private static final Map<Character, String> C39 = new HashMap<>();

    static {
        // b = narrow bar/space, B = wide bar/space (9 elements, starts with bar).
        C39.put('0', "bBbBbbBbb");
        C39.put('1', "BbbBbbBbb");
        C39.put('2', "bBbbbBbbB");
        C39.put('3', "BbbBbbbbb");
        C39.put('4', "bBbbBbbbB");
        C39.put('5', "BbbBbbbbB");
        C39.put('6', "bBbBbbbbB");
        C39.put('7', "bBbbBbbBbb");
        C39.put('8', "BbbBbbbBb");
        C39.put('9', "bBbBbbbBb");
        C39.put('*', "bBbbBbbBbb");
        C39.put('-', "bBbbbbbB Bb".replace(" ", ""));
        C39.put('.', "BbbbbbB Bb".replace(" ", ""));
        C39.put(' ', "bBbbbbB Bb".replace(" ", ""));
    }

    /** Build the label PDF and open the share sheet. */
    public static void share(Context c, String code, String name, String unit) {
        try {
            File f = build(c, code, name, unit);
            ShareProvider.share(c, f, "application/pdf", "لیبل " + code);
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "ساخت لیبل ممکن نشد", android.widget.Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static File build(Context c, String code, String name, String unit) throws Exception {
        int W = 560, H = 300;
        PdfDocument doc = new PdfDocument();
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(0xFF101828);
        Paint bold = new Paint(Paint.ANTI_ALIAS_FLAG);
        bold.setColor(0xFF101828);
        try {
            bold.setTypeface(Theme.face(true));
        } catch (Exception ignored) { }
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        android.graphics.Canvas g = page.getCanvas();
        g.drawColor(0xFFFFFFFF);
        bold.setTextSize(25);
        bold.setTextAlign(Paint.Align.CENTER);
        g.drawText(name == null || name.isEmpty() ? "—" : fit(name, W - 60, bold),
                W / 2f, 52, bold);
        ink.setTextSize(17);
        ink.setTextAlign(Paint.Align.CENTER);
        String sub = "کد " + Money.fa(code == null ? "" : code)
                + (unit == null || unit.isEmpty() ? "" : " • " + unit);
        g.drawText(sub, W / 2f, 84, ink);
        drawCode39(g, code == null ? "" : code, 40, 110, W - 80, 110, ink);
        ink.setTextSize(15);
        g.drawText(code == null ? "" : code, W / 2f, 258, ink);
        doc.finishPage(page);
        File dir = ShareProvider.shareDir(c);
        File out = new File(dir, "label-" + System.currentTimeMillis() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        } finally {
            try {
                doc.close();
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static void drawCode39(android.graphics.Canvas g, String data, float x, float y,
            float w, float h, Paint ink) {
        String msg = "*" + data.toUpperCase(java.util.Locale.US).replaceAll("[^*0-9\\-\\. ]", "")
                + "*";
        if (msg.length() <= 2) return;
        float narrow = 2f, wide = 5f;
        float total = 0;
        for (int i = 0; i < msg.length(); i++) {
            String p = C39.get(msg.charAt(i));
            if (p == null) continue;
            for (int k = 0; k < p.length(); k++) total += p.charAt(k) == 'B' ? wide : narrow;
            total += narrow; // inter-character gap
        }
        float s = Math.min(1f, w / Math.max(1, total));
        float cx = x + (w - total * s) / 2f;
        for (int i = 0; i < msg.length(); i++) {
            String p = C39.get(msg.charAt(i));
            if (p == null) continue;
            for (int k = 0; k < p.length(); k++) {
                float ew = (p.charAt(k) == 'B' ? wide : narrow) * s;
                if (k % 2 == 0) g.drawRect(cx, y, cx + ew, y + h, ink);
                cx += ew;
            }
            cx += narrow * s;
        }
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
