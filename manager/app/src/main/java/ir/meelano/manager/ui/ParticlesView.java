package ir.meelano.manager.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/** Optional, low-motion accent dots for legacy activation surfaces; no texture or gradient. */
public class ParticlesView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final java.util.Random rnd = new java.util.Random();
    private float[] x, y, radius, velocity, phase;
    private int count;
    private boolean live;

    public ParticlesView(Context context) {
        super(context);
        init();
    }

    public ParticlesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setCount(10);
    }

    /** How many subtle accent dots to draw. Screens may request fewer. */
    public void setCount(int value) {
        count = Math.max(0, value);
        x = new float[count];
        y = new float[count];
        radius = new float[count];
        velocity = new float[count];
        phase = new float[count];
        for (int i = 0; i < count; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            radius[i] = 1f + rnd.nextFloat();
            velocity[i] = 0.00025f + rnd.nextFloat() * 0.0004f;
            phase[i] = rnd.nextFloat() * 6.28f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        live = Ui.motionOk(getContext());
        if (live) post(step);
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
            for (int i = 0; i < count; i++) {
                y[i] -= velocity[i];
                phase[i] += 0.06f;
                if (y[i] < -0.02f) {
                    y[i] = 1.02f;
                    x[i] = rnd.nextFloat();
                }
            }
            invalidate();
            postDelayed(this, 48L);
        }
    };

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        canvas.drawColor(Theme.BG);
        int accent = Theme.isNuts() ? Theme.GOLD : Theme.PRIMARY;
        paint.setColor(accent);
        for (int i = 0; i < count; i++) {
            float opacity = 0.035f + 0.055f * (0.5f + 0.5f * (float) Math.sin(phase[i]));
            paint.setAlpha(Math.round(opacity * 255));
            canvas.drawCircle(x[i] * width, y[i] * height,
                    radius[i] * (width / 700f + 0.5f), paint);
        }
    }
}
