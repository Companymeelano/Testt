package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.LicenseStore;

import java.util.ArrayList;
import java.util.List;

/** Branded, RTL-safe image export for the Home smart report. */
public final class SmartReportImage {
    private SmartReportImage() { }

    public static Bitmap create(Context context, String date, List<String> sentences) {
        final int width = 1080;
        final int margin = 64;
        final int cardPadding = 34;
        final int cardWidth = width - margin * 2;
        final float textSize = 33f * Theme.fontScale();
        TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        body.setTypeface(Theme.face(true));
        body.setTextSize(textSize);
        body.setColor(Theme.TEXT);

        List<StaticLayout> layouts = new ArrayList<>();
        int cardsHeight = 0;
        if (sentences != null) {
            for (String sentence : sentences) {
                CharSequence text = HomeNumberFormatter.localizeText(sentence == null ? "" : sentence);
                StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), body,
                                cardWidth - cardPadding * 2)
                        .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                        .setTextDirection(TextDirectionHeuristics.RTL)
                        .setIncludePad(false)
                        .setLineSpacing(10f, 1.18f)
                        .build();
                layouts.add(layout);
                cardsHeight += Math.max(118, layout.getHeight() + cardPadding * 2) + 22;
            }
        }
        if (layouts.isEmpty()) cardsHeight = 150;
        final int headerHeight = 270;
        final int footerHeight = 100;
        int height = headerHeight + cardsHeight + footerHeight;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Theme.BG);

        Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
        shape.setColor(Theme.GOLD);
        canvas.drawRect(0, 0, width, 14, shape);
        shape.setColor(Theme.SURFACE);
        canvas.drawRoundRect(new RectF(margin, 48, width - margin, 232), 30, 30, shape);

        TextPaint heading = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        heading.setTypeface(Theme.face(true));
        heading.setColor(Theme.TEXT);
        heading.setTextAlign(Paint.Align.RIGHT);
        heading.setTextSize(45f * Theme.fontScale());
        String brand = LicenseStore.brandName(context);
        canvas.drawText(HomeNumberFormatter.localizeText("گزارش هوشمند امروز"),
                width - margin - 8, 116, heading);
        heading.setColor(Theme.GOLD);
        heading.setTextSize(27f * Theme.fontScale());
        canvas.drawText(HomeNumberFormatter.localizeText(brand == null || brand.trim().isEmpty()
                        ? "میلانو" : brand.trim()), width - margin - 8, 163, heading);
        heading.setColor(Theme.MUTED);
        heading.setTextSize(25f * Theme.fontScale());
        String normalizedDate = Jalali.disp(date);
        canvas.drawText("✦  " + (normalizedDate.isEmpty() ? "امروز" : Jalali.dispFa(normalizedDate)),
                width - margin - 8, 207, heading);

        try {
            Drawable logo = context.getDrawable(Theme.logoRes());
            if (logo != null) {
                logo.setBounds(margin + 20, 74, margin + 150, 204);
                logo.draw(canvas);
            }
        } catch (Exception ignored) { }

        int y = headerHeight;
        for (int i = 0; i < layouts.size(); i++) {
            StaticLayout layout = layouts.get(i);
            int cardHeight = Math.max(118, layout.getHeight() + cardPadding * 2);
            shape.setColor(Theme.SURFACE);
            canvas.drawRoundRect(new RectF(margin, y, width - margin, y + cardHeight), 25, 25, shape);
            shape.setColor(Theme.GOLD);
            canvas.drawRoundRect(new RectF(width - margin - 12, y + 24, width - margin - 5,
                    y + cardHeight - 24), 4, 4, shape);
            canvas.save();
            canvas.translate(margin + cardPadding, y + (cardHeight - layout.getHeight()) / 2f);
            layout.draw(canvas);
            canvas.restore();
            y += cardHeight + 22;
        }

        heading.setColor(Theme.MUTED);
        heading.setTextSize(20f * Theme.fontScale());
        heading.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("خلاصهٔ خودکار بر پایهٔ داده‌های ثبت‌شدهٔ فروشگاه",
                width / 2f, height - 40, heading);
        return bitmap;
    }
}
