package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * One-shot golden burst (v33): celebration confetti with gravity.
 * Fires on first draw, settles by itself (~2.2s), then goes idle.
 */
public class ConfettiView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private float[] x, y, vx, vy, s, rot, vr;
    private int n;
    private boolean fired;
    private int frames;
    private static final int[] COLORS = {
            0xFFFFF6DE, 0xFFE9C37C, 0xFFD9AE5A, 0xFF2ED3A3, 0xFFFFFFFF};

    public ConfettiView(Context c) {
        super(c);
    }

    public ConfettiView(Context c, AttributeSet a) {
        super(c, a);
    }

    private void fire(int w, int h) {
        fired = true;
        frames = 0;
        n = 90;
        x = new float[n];
        y = new float[n];
        vx = new float[n];
        vy = new float[n];
        s = new float[n];
        rot = new float[n];
        vr = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = w / 2f + (rnd.nextFloat() - 0.5f) * w * 0.3f;
            y[i] = h * 0.42f + (rnd.nextFloat() - 0.5f) * h * 0.08f;
            double a = -Math.PI / 2 + (rnd.nextFloat() - 0.5) * 2.2;
            float sp = h * (0.008f + rnd.nextFloat() * 0.014f);
            vx[i] = (float) Math.cos(a) * sp;
            vy[i] = (float) Math.sin(a) * sp;
            s[i] = 4 + rnd.nextFloat() * 7;
            rot[i] = rnd.nextFloat() * 360;
            vr[i] = (rnd.nextFloat() - 0.5f) * 24;
        }
        post(step);
    }

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            frames++;
            int h = getHeight();
            float g = h > 0 ? h * 0.0009f : 0.6f;
            for (int i = 0; i < n; i++) {
                vy[i] += g;
                x[i] += vx[i];
                y[i] += vy[i];
                rot[i] += vr[i];
            }
            invalidate();
            if (frames < 70) postDelayed(step, 30);
        }
    };

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (!fired) fire(w, h);
        if (x == null) return;
        float fade = frames > 50 ? Math.max(0, 1f - (frames - 50) / 20f) : 1f;
        for (int i = 0; i < n; i++) {
            paint.setColor(COLORS[i % COLORS.length]);
            paint.setAlpha((int) (fade * 255));
            c.save();
            c.translate(x[i], y[i]);
            c.rotate(rot[i]);
            c.drawRect(-s[i] / 2, -s[i] / 3, s[i] / 2, s[i] / 3, paint);
            c.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(step);
        super.onDetachedFromWindow();
    }
}
