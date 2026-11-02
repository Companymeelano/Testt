package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Static flat server glyph used beside the live connection status text. */
public class RadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public RadarView(Context context) {
        super(context);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;

        float left = width * 0.20f;
        float right = width * 0.80f;
        float top = height * 0.20f;
        float rowHeight = height * 0.18f;
        float gap = height * 0.06f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(1.5f));
        paint.setColor(Theme.BORDER);
        for (int i = 0; i < 3; i++) {
            float y = top + i * (rowHeight + gap);
            rect.set(left, y, right, y + rowHeight);
            canvas.drawRoundRect(rect, Theme.dp(5), Theme.dp(5), paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.PRIMARY);
        float dotRadius = Math.max(Theme.dp(2), width * 0.025f);
        for (int i = 0; i < 3; i++) {
            float y = top + i * (rowHeight + gap) + rowHeight / 2f;
            canvas.drawCircle(left + width * 0.10f, y, dotRadius, paint);
            paint.setAlpha(160);
            canvas.drawRoundRect(left + width * 0.20f, y - Theme.dp(1),
                    right - width * 0.10f, y + Theme.dp(1), Theme.dp(1), Theme.dp(1), paint);
            paint.setAlpha(255);
        }
    }
}
