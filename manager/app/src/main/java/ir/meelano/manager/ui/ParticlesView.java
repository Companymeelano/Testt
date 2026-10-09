package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Living activation background (v33): a deep vertical gradient with slow
 * golden dust drifting upward. Runs only while attached to a window.
 */
public class ParticlesView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private float[] x, y, r, v, tw;
    private int n;
    private boolean live;
    private Shader shader;
    private int lastH;

    public ParticlesView(Context c) {
        super(c);
        init();
    }

    public ParticlesView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        n = 26;
        x = new float[n];
        y = new float[n];
        r = new float[n];
        v = new float[n];
        tw = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            r[i] = 1.5f + rnd.nextFloat() * 3.5f;
            v[i] = 0.0006f + rnd.nextFloat() * 0.0016f;
            tw[i] = rnd.nextFloat() * 6.28f;
        }
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
            for (int i = 0; i < n; i++) {
                y[i] -= v[i];
                tw[i] += 0.06f;
                if (y[i] < -0.02f) {
                    y[i] = 1.02f;
                    x[i] = rnd.nextFloat();
                }
            }
            invalidate();
            postDelayed(step, 33);
        }
    };

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (shader == null || h != lastH) {
            lastH = h;
            shader = new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF0B0E12, 0xFF101319, 0xFF151009},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        }
        paint.setShader(shader);
        paint.setAlpha(255);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        for (int i = 0; i < n; i++) {
            float a = 0.25f + 0.55f * (0.5f + 0.5f * (float) Math.sin(tw[i]));
            paint.setAlpha((int) (a * 255));
            paint.setColor(0xFFD9AE5A);
            c.drawCircle(x[i] * w, y[i] * h, r[i] * (w / 700f + 0.5f), paint);
        }
    }
}
