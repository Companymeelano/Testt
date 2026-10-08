package ir.meelano.admin;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * QR display for offline pairing: the seller shows, the customer scans.
 * Null-safe: any failure returns null (the text copy/share path remains).
 */
public final class QrShow {
    private QrShow() { }

    /** Render text as a black-on-white QR bitmap, or null when it cannot be built. */
    public static Bitmap make(String text, int px) {
        try {
            if (text == null || text.trim().isEmpty() || px < 64) return null;
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            com.google.zxing.common.BitMatrix m =
                    new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, hints);
            int w = m.getWidth(), h = m.getHeight();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) {
            return null;
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }
}
