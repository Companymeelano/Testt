package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Server-hunt radar (v33): rings + a rotating sweep + blips.
 * Animates only while attached AND visible.
 */
public class RadarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle;
    private boolean live;

    public RadarView(Context c) {
        super(c);
    }

    public RadarView(Context c, AttributeSet a) {
        super(c, a);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        live = true;
        post(step);
    }

    @Override
    protected void onDetachedFromWindow() {
        live = false;
        removeCallbacks(step);
        super.onDetachedFromWindow();
    }

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!live) return;
            if (getVisibility() == VISIBLE && getWidth() > 0) {
                angle = (angle + 9) % 360;
                invalidate();
            }
            postDelayed(step, 50);
        }
    };

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - 4;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(0xFF2ED3A3);
        paint.setAlpha(220);
        c.drawCircle(cx, cy, r, paint);
        paint.setAlpha(120);
        c.drawCircle(cx, cy, r * 0.66f, paint);
        paint.setAlpha(70);
        c.drawCircle(cx, cy, r * 0.33f, paint);
        // sweep
        paint.setStyle(Paint.Style.FILL);
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        for (int i = 0; i < 5; i++) {
            paint.setColor(0xFF2ED3A3);
            paint.setAlpha(46 - i * 8);
            c.drawArc(o, angle - 12 - i * 9, 9, true, paint);
        }
        // blips
        paint.setColor(0xFFE9C37C);
        paint.setAlpha(255);
        float b1 = (angle * 3.1f) % 360;
        c.drawCircle(cx + (float) Math.cos(Math.toRadians(b1)) * r * 0.55f,
                cy + (float) Math.sin(Math.toRadians(b1)) * r * 0.55f, 5, paint);
        float b2 = (angle * 1.7f + 140) % 360;
        paint.setAlpha(170);
        c.drawCircle(cx + (float) Math.cos(Math.toRadians(b2)) * r * 0.8f,
                cy + (float) Math.sin(Math.toRadians(b2)) * r * 0.8f, 4, paint);
        // hub
        paint.setAlpha(255);
        c.drawCircle(cx, cy, 5, paint);
    }
}
