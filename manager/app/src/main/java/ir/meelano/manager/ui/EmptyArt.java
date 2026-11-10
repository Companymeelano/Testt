package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Small vector illustration for empty and error states. Drawn in code, so it needs no image
 * assets and follows the selected theme: gold for "nothing here yet", the danger hue for errors.
 */
public final class EmptyArt extends View {
    public static final int EMPTY = 0;
    public static final int ERROR = 1;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path star = new Path();

    public EmptyArt(Context c, int kind) {
        super(c);
        this.kind = kind;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = Theme.dp(96);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas g) {
        super.onDraw(g);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int tone = kind == ERROR ? Theme.DANGER : Theme.GOLD;
        int soft = kind == ERROR ? Theme.DANGER : Theme.GOLD_SOFT;
        float cx = w / 2f, cy = h / 2f;

        // Soft halo behind the figure.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, w * 0.5f,
                Theme.alpha(tone, Theme.isLight() ? 70 : 90), Theme.alpha(tone, 0), Shader.TileMode.CLAMP));
        g.drawCircle(cx, cy, w * 0.5f, paint);
        paint.setShader(null);

        if (kind == ERROR) {
            drawError(g, w, h, tone, soft);
        } else {
            drawEmpty(g, w, h, tone, soft);
        }
    }

    /** An open tray with a page resting in it, and a sparkle: «nothing here yet». */
    private void drawEmpty(Canvas g, float w, float h, int tone, int soft) {
        // Page (back).
        rect.set(w * 0.30f, h * 0.18f, w * 0.70f, h * 0.60f);
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                Theme.alpha(soft, 210), Theme.alpha(tone, 150), Shader.TileMode.CLAMP));
        g.drawRoundRect(rect, Theme.dp(6), Theme.dp(6), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(1.5f));
        paint.setColor(Theme.alpha(Theme.TEXT, 60));
        for (int i = 0; i < 3; i++) {
            float y = rect.top + Theme.dp(10) + i * Theme.dp(7);
            g.drawLine(rect.left + Theme.dp(7), y, rect.right - Theme.dp(7), y, paint);
        }

        // Tray (front), a rounded bowl.
        paint.setStyle(Paint.Style.FILL);
        rect.set(w * 0.14f, h * 0.56f, w * 0.86f, h * 0.80f);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                Theme.alpha(soft, 255), Theme.alpha(tone, 255), Shader.TileMode.CLAMP));
        g.drawRoundRect(rect, Theme.dp(12), Theme.dp(12), paint);
        paint.setShader(null);
        paint.setColor(Theme.alpha(Theme.BG, 230));
        rect.set(w * 0.24f, h * 0.60f, w * 0.76f, h * 0.70f);
        g.drawRoundRect(rect, Theme.dp(6), Theme.dp(6), paint);

        // Sparkle.
        paint.setColor(Theme.alpha(soft, 240));
        drawStar(g, w * 0.80f, h * 0.14f, Theme.dp(7));
        drawStar(g, w * 0.16f, h * 0.30f, Theme.dp(4.5f));
    }

    /** A rounded alert triangle with an exclamation mark: «something failed». */
    private void drawError(Canvas g, float w, float h, int tone, int soft) {
        float cx = w / 2f, cy = h / 2f;
        float r = w * 0.30f;
        paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                Theme.alpha(soft, 255), Theme.alpha(tone, 200), Shader.TileMode.CLAMP));
        g.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dp(2));
        paint.setColor(Theme.alpha(Theme.TEXT, 90));
        g.drawCircle(cx, cy, r + Theme.dp(5), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.TEXT);
        rect.set(cx - Theme.dp(3), cy - r * 0.55f, cx + Theme.dp(3), cy + r * 0.18f);
        g.drawRoundRect(rect, Theme.dp(3), Theme.dp(3), paint);
        g.drawCircle(cx, cy + r * 0.40f, Theme.dp(3.2f), paint);
    }

    private void drawStar(Canvas g, float x, float y, float r) {
        star.reset();
        star.moveTo(x, y - r);
        star.quadTo(x, y, x + r, y);
        star.quadTo(x, y, x, y + r);
        star.quadTo(x, y, x - r, y);
        star.quadTo(x, y, x, y - r);
        star.close();
        g.drawPath(star, paint);
    }
}
