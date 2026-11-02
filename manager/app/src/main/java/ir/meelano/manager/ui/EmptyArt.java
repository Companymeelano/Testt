package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Small flat vector illustrations for explicit empty and error states. */
public final class EmptyArt extends View {
    public static final int EMPTY = 0;
    public static final int ERROR = 1;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public EmptyArt(Context c, int kind) {
        super(c);
        this.kind = kind;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = Theme.dp(88);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (kind == ERROR) drawError(canvas, width, height);
        else drawEmpty(canvas, width, height);
    }

    /** A simple page over a tray, with no decorative counts or data. */
    private void drawEmpty(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.ACCENT_SOFT);
        rect.set(width * 0.31f, height * 0.12f, width * 0.69f, height * 0.62f);
        canvas.drawRoundRect(rect, Theme.dp(5), Theme.dp(5), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(1.5f));
        paint.setColor(Theme.BORDER);
        canvas.drawRoundRect(rect, Theme.dp(5), Theme.dp(5), paint);
        paint.setColor(Theme.MUTED);
        for (int i = 0; i < 3; i++) {
            float y = rect.top + Theme.dp(10) + i * Theme.dp(7);
            canvas.drawLine(rect.left + Theme.dp(7), y, rect.right - Theme.dp(7), y, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.SURFACE2);
        rect.set(width * 0.14f, height * 0.56f, width * 0.86f, height * 0.81f);
        canvas.drawRoundRect(rect, Theme.dp(10), Theme.dp(10), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(1.5f));
        paint.setColor(Theme.BORDER);
        canvas.drawRoundRect(rect, Theme.dp(10), Theme.dp(10), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.SURFACE);
        rect.set(width * 0.24f, height * 0.60f, width * 0.76f, height * 0.69f);
        canvas.drawRoundRect(rect, Theme.dp(5), Theme.dp(5), paint);
    }

    /** A quiet, high-contrast error mark with no gradient or alert glow. */
    private void drawError(Canvas canvas, float width, float height) {
        float cx = width / 2f, cy = height / 2f;
        float radius = width * 0.29f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.ACCENT_SOFT);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(1.5f));
        paint.setColor(Theme.DANGER);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.DANGER);
        rect.set(cx - Theme.dp(2.5f), cy - radius * 0.52f,
                cx + Theme.dp(2.5f), cy + radius * 0.16f);
        canvas.drawRoundRect(rect, Theme.dp(2.5f), Theme.dp(2.5f), paint);
        canvas.drawCircle(cx, cy + radius * 0.40f, Theme.dp(2.5f), paint);
    }
}
