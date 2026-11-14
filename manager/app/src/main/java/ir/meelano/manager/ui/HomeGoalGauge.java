package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A quiet Canvas ring for monthly goal progress; semantic labels remain real TextViews. */
public final class HomeGoalGauge extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float progress;
    private int accent = Theme.SECTION_SALES;

    public HomeGoalGauge(Context context) {
        super(context);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setProgress(double percent, int color) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) percent = 0d;
        progress = (float) Math.max(0d, Math.min(100d, percent));
        accent = color;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = Theme.dp(13);
        float inset = stroke / 2f + Theme.dp(3);
        arc.set(inset, inset, getWidth() - inset, getHeight() - inset);
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);
        trackPaint.setColor(Theme.isLight()
                ? Theme.alpha(Theme.TEXT, 18) : Theme.alpha(Theme.TEXT, 28));
        progressPaint.setColor(Theme.sectionText(accent));
        canvas.drawArc(arc, -90f, 360f, false, trackPaint);
        float sweep = progress >= 100f ? 360f : 360f * progress / 100f;
        if (sweep > 0f) canvas.drawArc(arc, -90f, sweep, false, progressPaint);
    }
}
