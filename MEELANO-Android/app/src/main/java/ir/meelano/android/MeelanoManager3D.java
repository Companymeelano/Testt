package ir.meelano.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/**
 * «Executive» three-dimensional visual language of the manager edition — pure Canvas, no
 * dependency and no AndroidX, exactly like the rest of the project.
 *
 * <p>Two building blocks live here:</p>
 * <ul>
 *   <li>{@link IsoBars} — an isometric bar chart with real depth: every bar is a small cuboid
 *       (lit top face, mid front face, shaded side face) standing on a soft elliptical shadow,
 *       so the figure reads as 3D instead of a flat rectangle.</li>
 *   <li>{@link metalPanel} / {@link orb} — reusable gradient helpers that give tiles and KPI orbs
 *       their brushed-metal sheen and inner glow, matching the pearl/gold manager theme.</li>
 * </ul>
 *
 * The view is deliberately dumb: the activity feeds it ready-to-print value strings and labels so
 * all Persian digit / money formatting stays in {@code MainActivity} where it already lives.
 */
final class MeelanoManager3D {
    private MeelanoManager3D() { }

    /** Brushed-metal rounded panel used as a card base for executive tiles. */
    static android.graphics.drawable.GradientDrawable metalPanel(int from, int to, float radius) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{from, to});
        g.setCornerRadius(radius);
        g.setStroke(2, Color.argb(36, 255, 255, 255));
        return g;
    }

    /** A radial glow (highlight) used behind an emblem or at a bar's lit top. */
    static int lit(int base, float amount) {
        int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return Color.rgb(r, g, b);
    }

    /** A darker shade of a base colour for the shadowed side of a cuboid. */
    static int shade(int base, float amount) {
        int r = (int) (Color.red(base) * (1 - amount));
        int g = (int) (Color.green(base) * (1 - amount));
        int b = (int) (Color.blue(base) * (1 - amount));
        return Color.rgb(r, g, b);
    }

    /**
     * Isometric 3D bar chart. Bars are cuboids drawn back-to-front so nearer bars overlap farther
     * ones; each casts a soft elliptical shadow. Values are normalised by the caller (0..1) and
     * preformatted labels are printed under and above each bar.
     */
    static final class IsoBars extends View {
        private String[] labels = new String[0];
        private String[] values = new String[0];
        private float[] norm = new float[0];
        private int[] colors = new int[0];
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path face = new Path();

        IsoBars(Context ctx) {
            super(ctx);
            line.setStyle(Paint.Style.STROKE);
            txt.setTextAlign(Paint.Align.CENTER);
            small.setTextAlign(Paint.Align.CENTER);
        }

        void setData(String[] labels, String[] values, float[] norm, int[] colors) {
            this.labels = labels; this.values = values; this.norm = norm; this.colors = colors;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            int n = norm.length;
            if (n == 0 || getWidth() == 0) return;
            float w = getWidth(), h = getHeight();
            float pad = w * 0.05f;
            float base = h * 0.78f;              // ground line for the cuboid fronts
            float depth = Math.min(w, h) * 0.06f; // isometric depth (dx, dy)
            float maxBar = h * 0.52f;
            float slot = (w - pad * 2) / n;
            float bw = slot * 0.52f;
            txt.setTextSize(h * 0.075f);
            small.setTextSize(h * 0.06f);

            for (int i = 0; i < n; i++) {
                int col = colors[i % colors.length];
                float cx = pad + slot * i + slot / 2f;
                float bh = Math.max(maxBar * 0.08f, maxBar * norm[i]);
                float x0 = cx - bw / 2f;
                float top = base - bh;

                // ground shadow
                Paint sh = fill;
                sh.setStyle(Paint.Style.FILL);
                sh.setShader(new RadialGradient(cx, base + depth * 0.4f, bw * 1.4f,
                        Color.argb(70, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                cv.drawOval(new RectF(cx - bw * 1.3f, base - bw * 0.18f, cx + bw * 1.3f, base + bw * 0.42f), sh);
                sh.setShader(null);

                // side face (right, shaded)
                face.reset();
                face.moveTo(x0 + bw, top);
                face.lineTo(x0 + bw + depth, top - depth);
                face.lineTo(x0 + bw + depth, base - depth);
                face.lineTo(x0 + bw, base);
                face.close();
                fill.setShader(new LinearGradient(x0 + bw, top, x0 + bw + depth, base,
                        shade(col, 0.42f), shade(col, 0.58f), Shader.TileMode.CLAMP));
                cv.drawPath(face, fill);

                // top face (lit)
                face.reset();
                face.moveTo(x0, top);
                face.lineTo(x0 + depth, top - depth);
                face.lineTo(x0 + bw + depth, top - depth);
                face.lineTo(x0 + bw, top);
                face.close();
                fill.setShader(new LinearGradient(x0, top, x0 + bw, top - depth,
                        lit(col, 0.55f), lit(col, 0.25f), Shader.TileMode.CLAMP));
                cv.drawPath(face, fill);

                // front face (mid, vertical sheen)
                fill.setShader(new LinearGradient(x0, top, x0, base,
                        lit(col, 0.18f), shade(col, 0.18f), Shader.TileMode.CLAMP));
                cv.drawRect(new RectF(x0, top, x0 + bw, base), fill);
                fill.setShader(null);
                line.setColor(Color.argb(60, 255, 255, 255));
                line.setStrokeWidth(1.5f);
                cv.drawRect(new RectF(x0, top, x0 + bw, base), line);

                // value above, label below
                txt.setColor(Color.WHITE);
                cv.drawText(values[i], cx, top - depth - h * 0.03f, txt);
                small.setColor(Color.argb(200, 255, 255, 255));
                cv.drawText(labels[i], cx, base + h * 0.12f, small);
            }
        }
    }
}
