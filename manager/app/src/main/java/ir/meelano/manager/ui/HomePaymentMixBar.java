package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Slim, rounded and gapped RTL stacked bar for the Home receipt mix. */
public final class HomePaymentMixBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segment = new RectF();
    private double[] values = new double[0];
    private int[] colors = new int[0];

    public HomePaymentMixBar(Context context) {
        super(context);
        setMinimumHeight(Theme.dp(8));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setSegments(double[] amounts, int[] accents, String description) {
        values = amounts == null ? new double[0] : amounts.clone();
        colors = accents == null ? new int[0] : accents.clone();
        setContentDescription(description == null ? "ترکیب روش‌های دریافت" : description);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = height / 2f;
        paint.setColor(Theme.alpha(Theme.MUTED, Theme.isLight() ? 25 : 34));
        canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);

        double total = 0;
        int count = Math.min(values.length, colors.length);
        for (int i = 0; i < count; i++) {
            double value = values[i];
            if (!Double.isNaN(value) && !Double.isInfinite(value) && value > 0) total += value;
        }
        if (total <= 0 || count == 0) return;

        int visible = 0;
        for (int i = 0; i < count; i++)
            if (!Double.isNaN(values[i]) && !Double.isInfinite(values[i]) && values[i] > 0) visible++;
        if (visible == 0) return;
        float gap = Math.min(Theme.dp(3), width / Math.max(1, visible * 2));
        float usable = Math.max(0, width - gap * (visible - 1));
        float cursor = width;
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            double value = values[i];
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) continue;
            drawn++;
            float segmentWidth = drawn == visible
                    ? Math.max(0, cursor)
                    : (float) (usable * value / total);
            if (drawn < visible) segmentWidth = Math.max(Theme.dp(1), segmentWidth);
            float left = Math.max(0, cursor - segmentWidth);
            segment.set(left, 0, cursor, height);
            paint.setColor(colors[i]);
            canvas.drawRoundRect(segment, radius, radius, paint);
            cursor = left;
            if (drawn < visible) cursor = Math.max(0, cursor - gap);
        }
    }
}
