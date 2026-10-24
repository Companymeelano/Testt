package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

/**
 * Progress ring for the update download. Every colour is read from {@link Theme} on each frame,
 * so the ring always matches the active skin (ivory, obsidian, nut or cups).
 * Shows the percentage in the centre; while the size is unknown it spins instead.
 */
public final class DownloadRing extends View {
    private volatile float progress = -1f; // 0..1, negative = unknown
    private float spin = 0f;
    private final RectF oval = new RectF();
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix rot = new Matrix();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            spin = (spin + 3f) % 360f;
            invalidate();
            postDelayed(this, 16);
        }
    };

    public DownloadRing(Context c) {
        super(c);
    }

    /** Sets the fraction done (0..1); a negative value means the total size is unknown. */
    public void setProgress(float p) {
        progress = p;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(ticker);
        post(ticker);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.34f;
        float stroke = Theme.dp(10);
        float p = progress;

        // Soft glow behind the ring, in the accent colour.
        glow.setStyle(Paint.Style.FILL);
        glow.setShader(new RadialGradient(cx, cy, r * 1.7f,
                Theme.alpha(Theme.GOLD, 80), Theme.alpha(Theme.GOLD, 0), Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 1.7f, glow);
        glow.setShader(null);

        oval.set(cx - r, cy - r, cx + r, cy + r);

        // Track.
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Theme.alpha(Theme.GOLD, 46));
        cv.drawCircle(cx, cy, r, track);

        // Progress arc with a sweeping two-tone gradient that turns with the ring.
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(stroke);
        SweepGradient sg = new SweepGradient(cx, cy,
                new int[]{Theme.GOLD_SOFT, Theme.GOLD, Theme.GOLD_SOFT}, null);
        rot.setRotate(spin - 90f, cx, cy);
        sg.setLocalMatrix(rot);
        arc.setShader(sg);
        if (p >= 0f) {
            float sweep = Math.max(3f, 360f * Math.min(1f, p));
            cv.drawArc(oval, -90f, sweep, false, arc);
        } else {
            cv.drawArc(oval, spin - 90f, 110f, false, arc);
        }
        arc.setShader(null);

        // Six small dots orbit the ring while it works.
        dot.setStyle(Paint.Style.FILL);
        float orbit = r + stroke * 2.4f;
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(spin * 1.4 + i * 60);
            float x = cx + (float) Math.cos(ang) * orbit;
            float y = cy + (float) Math.sin(ang) * orbit;
            dot.setColor(Theme.alpha(i % 2 == 0 ? Theme.GOLD : Theme.GOLD_SOFT, 200 - i * 14));
            cv.drawCircle(x, y, Theme.dp(2.4f + (i % 3)), dot);
        }

        // Percentage in the centre.
        text.setColor(Theme.TEXT);
        text.setTypeface(Theme.face(true));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(r * 0.5f);
        String label = p >= 0f ? fa(String.valueOf(Math.round(Math.min(1f, p) * 100f))) + "٪" : "…";
        Paint.FontMetrics fm = text.getFontMetrics();
        cv.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    /** Converts Western digits to Persian digits. */
    public static String fa(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return b.toString();
    }
}
