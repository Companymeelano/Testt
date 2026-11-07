package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Tiny, flat trend line for KPI tiles. Values are plotted relative to their own min/max,
 * so a flat week still reads as flat; source values are never modified.
 */
public final class Sparkline extends View {
    private final double[] values;
    private final int color;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();

    public Sparkline(Context c, double[] values, int color) {
        super(c);
        this.values = values == null ? new double[0] : values.clone();
        this.color = color;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Theme.dp(1.6f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(color);
        fill.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n < 2) return;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double span = max - min;
        float w = getWidth();
        float h = getHeight();
        float pad = Theme.dp(2);
        float top = pad;
        float bottom = h - pad;
        path.reset();
        area.reset();
        for (int i = 0; i < n; i++) {
            // RTL chronology: the oldest sample sits at the right, the newest at the left.
            float x = w - w * i / (float) (n - 1);
            double norm = span <= 0 ? 0.5 : (values[i] - min) / span;
            float y = (float) (bottom - norm * (bottom - top));
            if (i == 0) {
                path.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                area.lineTo(x, y);
            }
        }
        area.lineTo(0, h);
        area.close();
        fill.setColor(Theme.alpha(color, Theme.isLight() ? 18 : 24));
        canvas.drawPath(area, fill);
        canvas.drawPath(path, line);
    }
}
