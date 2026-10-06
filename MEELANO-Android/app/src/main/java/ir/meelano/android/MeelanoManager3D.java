package ir.meelano.android;

import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;

/**
 * «Executive» three-dimensional visual language of the manager edition — pure Canvas, no dependency
 * and no AndroidX, exactly like the rest of the project.
 *
 * <p>Building blocks:</p>
 * <ul>
 *   <li>{@link IsoBars} — isometric cuboid bar chart with lit top, shaded side, ground shadow and an
 *       entrance grow animation.</li>
 *   <li>{@link IsoArea} — isometric extruded trend ribbon (a 3D mountain range) for time series.</li>
 *   <li>{@link Donut3D} — a tilted, depth-extruded donut for share/structure breakdowns.</li>
 *   <li>{@link #metalPanel}/{@link #lit}/{@link #shade} — reusable brushed-metal and lighting helpers.</li>
 * </ul>
 *
 * Every view is deliberately dumb and defensive: the activity feeds ready-to-print strings, and each
 * onDraw is wrapped so a drawing fault can never crash the app.
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

    /** Shared 0→1 entrance animation so every 3D chart grows in instead of popping. */
    private static float growIn(final View v, final java.util.concurrent.atomic.AtomicReference<float[]> holder) {
        final float[] p = {0f};
        holder.set(p);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(850);
        a.setInterpolator(new DecelerateInterpolator(1.5f));
        a.addUpdateListener(an -> { p[0] = (float) an.getAnimatedValue(); v.invalidate(); });
        a.start();
        return 0f;
    }

    /** Isometric 3D bar chart with lit top, shaded side, ground shadow, value pills and grow-in. */
    static final class IsoBars extends View {
        private String[] labels = new String[0];
        private String[] values = new String[0];
        private float[] norm = new float[0];
        private int[] colors = new int[0];
        private final java.util.concurrent.atomic.AtomicReference<float[]> prog = new java.util.concurrent.atomic.AtomicReference<>(new float[]{0f});
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path face = new Path();
        private int picked = -1; // Phase-7C: tapped bar index

        IsoBars(Context ctx) {
            super(ctx);
            line.setStyle(Paint.Style.STROKE);
            txt.setTextAlign(Paint.Align.CENTER);
            small.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            int n = norm.length;
            if (n == 0 || getWidth() == 0) return super.onTouchEvent(ev);
            int act = ev.getAction();
            if (act == android.view.MotionEvent.ACTION_DOWN || act == android.view.MotionEvent.ACTION_UP) {
                float pad = getWidth() * 0.05f;
                float slot = (getWidth() - pad * 2) / n;
                int idx = (int) ((ev.getX() - pad) / slot);
                if (idx >= 0 && idx < n) { picked = idx; invalidate(); return true; }
            }
            return super.onTouchEvent(ev);
        }

        void setData(String[] labels, String[] values, float[] norm, int[] colors) {
            this.labels = labels == null ? new String[0] : labels;
            this.values = values == null ? new String[0] : values;
            this.norm = norm == null ? new float[0] : norm;
            this.colors = colors == null ? new int[0] : colors;
            growIn(this, prog);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            try { paint(cv); } catch (Throwable ignored) { }
        }

        private void paint(Canvas cv) {
            int n = norm.length;
            if (n == 0 || getWidth() == 0 || labels.length < n || values.length < n || colors.length == 0) return;
            float pr = prog.get()[0];
            float w = getWidth(), h = getHeight();
            float pad = w * 0.05f;
            float base = h * 0.78f;
            float depth = Math.min(w, h) * 0.06f;
            float maxBar = h * 0.52f;
            float slot = (w - pad * 2) / n;
            float bw = slot * 0.52f;
            txt.setTextSize(h * 0.07f);
            small.setTextSize(h * 0.058f);

            // isometric floor grid for a sense of ground
            line.setColor(Color.argb(22, 255, 255, 255));
            line.setStrokeWidth(1f);
            for (int i = 0; i <= n; i++) {
                float gx = pad + slot * i;
                cv.drawLine(gx, base + depth * 0.5f, gx + depth, base - depth * 0.5f, line);
            }
            cv.drawLine(pad, base, pad + slot * n, base, line);

            for (int i = 0; i < n; i++) {
                int col = colors[i % colors.length];
                float cx = pad + slot * i + slot / 2f;
                float bh = Math.max(maxBar * 0.08f, maxBar * Math.max(0f, Math.min(1f, norm[i])) * pr);
                float x0 = cx - bw / 2f;
                float top = base - bh;

                Paint sh = fill;
                sh.setStyle(Paint.Style.FILL);
                sh.setShader(new RadialGradient(cx, base + depth * 0.4f, bw * 1.4f,
                        Color.argb(70, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                cv.drawOval(new RectF(cx - bw * 1.3f, base - bw * 0.18f, cx + bw * 1.3f, base + bw * 0.42f), sh);
                sh.setShader(null);

                face.reset();
                face.moveTo(x0 + bw, top);
                face.lineTo(x0 + bw + depth, top - depth);
                face.lineTo(x0 + bw + depth, base - depth);
                face.lineTo(x0 + bw, base);
                face.close();
                fill.setShader(new LinearGradient(x0 + bw, top, x0 + bw + depth, base,
                        shade(col, 0.42f), shade(col, 0.58f), Shader.TileMode.CLAMP));
                cv.drawPath(face, fill);

                face.reset();
                face.moveTo(x0, top);
                face.lineTo(x0 + depth, top - depth);
                face.lineTo(x0 + bw + depth, top - depth);
                face.lineTo(x0 + bw, top);
                face.close();
                fill.setShader(new LinearGradient(x0, top, x0 + bw, top - depth,
                        lit(col, 0.55f), lit(col, 0.25f), Shader.TileMode.CLAMP));
                cv.drawPath(face, fill);

                fill.setShader(new LinearGradient(x0, top, x0, base,
                        lit(col, 0.18f), shade(col, 0.18f), Shader.TileMode.CLAMP));
                cv.drawRect(new RectF(x0, top, x0 + bw, base), fill);
                fill.setShader(null);

                // Phase-7C: showroom reflection — a faded mirror of the bar under the floor line
                float rh = Math.min(bh * 0.30f, h * 0.09f);
                if (rh > 2) {
                    fill.setShader(new LinearGradient(x0, base, x0, base + rh,
                            Color.argb(55, Color.red(col), Color.green(col), Color.blue(col)), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    cv.drawRect(new RectF(x0, base + 1f, x0 + bw, base + rh), fill);
                    fill.setShader(null);
                }

                line.setColor(Color.argb(60, 255, 255, 255));
                line.setStrokeWidth(1.5f);
                cv.drawRect(new RectF(x0, top, x0 + bw, base), line);
                if (i == picked) { // tapped bar gets a bright outline
                    line.setColor(Color.argb(210, 255, 255, 255));
                    line.setStrokeWidth(3f);
                    cv.drawRect(new RectF(x0 - 2f, top - 2f, x0 + bw + 2f, base + 2f), line);
                }

                // value pill above the bar
                String vv = values[i];
                float tw = txt.measureText(vv);
                float py = top - depth - h * 0.06f;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Color.argb(120, 0, 0, 0));
                cv.drawRoundRect(new RectF(cx - tw / 2 - h * 0.03f, py - h * 0.05f, cx + tw / 2 + h * 0.03f, py + h * 0.045f), h * 0.04f, h * 0.04f, fill);
                txt.setColor(Color.WHITE);
                cv.drawText(vv, cx, py + h * 0.015f, txt);

                if (i == picked) { // Phase-7C: tooltip bubble with label + exact value on tap
                    String tip = labels[i] + " • " + values[i];
                    float tw2 = txt.measureText(tip);
                    float ty = Math.max(h * 0.10f, py - h * 0.12f);
                    float half = Math.min(tw2 / 2 + h * 0.04f, w / 2 - 2);
                    float tcx = Math.max(half + 2, Math.min(w - half - 2, cx));
                    fill.setColor(Color.argb(215, 18, 22, 32));
                    cv.drawRoundRect(new RectF(tcx - half, ty - h * 0.062f, tcx + half, ty + h * 0.052f), h * 0.045f, h * 0.045f, fill);
                    line.setColor(Color.argb(140, 231, 177, 90));
                    line.setStrokeWidth(1.5f);
                    cv.drawRoundRect(new RectF(tcx - half, ty - h * 0.062f, tcx + half, ty + h * 0.052f), h * 0.045f, h * 0.045f, line);
                    txt.setColor(Color.WHITE);
                    cv.drawText(tip, tcx, ty + h * 0.016f, txt);
                }

                small.setColor(Color.argb(200, 255, 255, 255));
                cv.drawText(labels[i], cx, base + h * 0.12f, small);
            }
        }
    }

    /** Isometric extruded trend ribbon — a 3D mountain range for a time series. */
    static final class IsoArea extends View {
        private String[] labels = new String[0];
        private String[] values = new String[0];
        private float[] norm = new float[0];
        private int color = 0xFF3060B0;
        private final java.util.concurrent.atomic.AtomicReference<float[]> prog = new java.util.concurrent.atomic.AtomicReference<>(new float[]{0f});
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path front = new Path();
        private final Path top = new Path();

        IsoArea(Context ctx) {
            super(ctx);
            stroke.setStyle(Paint.Style.STROKE);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void setData(String[] labels, String[] values, float[] norm, int color) {
            this.labels = labels == null ? new String[0] : labels;
            this.values = values == null ? new String[0] : values;
            this.norm = norm == null ? new float[0] : norm;
            this.color = color;
            growIn(this, prog);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            try { paint(cv); } catch (Throwable ignored) { }
        }

        private void paint(Canvas cv) {
            int n = norm.length;
            if (n < 2 || getWidth() == 0 || values.length < n) return;
            float pr = prog.get()[0];
            float w = getWidth(), h = getHeight();
            float pad = w * 0.06f;
            float base = h * 0.8f;
            float depth = Math.min(w, h) * 0.07f;
            float maxBar = h * 0.55f;
            txt.setTextSize(h * 0.06f);

            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                // RTL: newest on the right
                xs[i] = w - pad - (w - pad * 2) * i / (n - 1f);
                ys[i] = base - maxBar * Math.max(0.04f, Math.min(1f, norm[i])) * pr;
            }

            // ground shadow
            fill.setStyle(Paint.Style.FILL);
            fill.setShader(new RadialGradient(w / 2f, base, w * 0.6f, Color.argb(60, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            cv.drawOval(new RectF(pad * 0.5f, base - h * 0.03f, w - pad * 0.5f, base + h * 0.07f), fill);
            fill.setShader(null);

            // top (lit) ribbon: the silhouette shifted by the iso depth
            top.reset();
            top.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) top.lineTo(xs[i], ys[i]);
            top.lineTo(xs[n - 1] + depth, ys[n - 1] - depth);
            for (int i = n - 1; i >= 0; i--) top.lineTo(xs[i] + depth, ys[i] - depth);
            top.close();
            fill.setShader(new LinearGradient(0, base - maxBar, 0, base, lit(color, 0.5f), lit(color, 0.2f), Shader.TileMode.CLAMP));
            cv.drawPath(top, fill);
            fill.setShader(null);

            // front face with vertical sheen down to the ground
            front.reset();
            front.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) front.lineTo(xs[i], ys[i]);
            front.lineTo(xs[n - 1], base);
            front.lineTo(xs[0], base);
            front.close();
            fill.setShader(new LinearGradient(0, base - maxBar, 0, base, lit(color, 0.15f), shade(color, 0.35f), Shader.TileMode.CLAMP));
            cv.drawPath(front, fill);
            fill.setShader(null);

            // crisp lit crest line
            stroke.setColor(lit(color, 0.6f));
            stroke.setStrokeWidth(2f);
            Path crest = new Path();
            crest.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) crest.lineTo(xs[i], ys[i]);
            cv.drawPath(crest, stroke);

            // value dots + labels
            for (int i = 0; i < n; i++) {
                fill.setColor(Color.WHITE);
                cv.drawCircle(xs[i], ys[i], h * 0.014f, fill);
                if (i % Math.max(1, n / 6) == 0 || i == n - 1) {
                    txt.setColor(Color.argb(200, 255, 255, 255));
                    cv.drawText(labels[i], Math.max(pad, Math.min(w - pad, xs[i])), base + h * 0.12f, txt);
                }
            }
            // peak value pill on the last (newest) point
            String pv = values[n - 1];
            float tw = txt.measureText(pv);
            fill.setColor(Color.argb(130, 0, 0, 0));
            cv.drawRoundRect(new RectF(xs[n - 1] - tw / 2 - h * 0.03f, ys[n - 1] - h * 0.14f, xs[n - 1] + tw / 2 + h * 0.03f, ys[n - 1] - h * 0.05f), h * 0.04f, h * 0.04f, fill);
            txt.setColor(Color.WHITE);
            cv.drawText(pv, xs[n - 1], ys[n - 1] - h * 0.08f, txt);
        }
    }

    /** Tilted, depth-extruded donut for share/structure breakdowns, total printed in the hub. */
    static final class Donut3D extends View {
        private String[] labels = new String[0];
        private float[] norm = new float[0];
        private int[] colors = new int[0];
        private String center = "";
        private final java.util.concurrent.atomic.AtomicReference<float[]> prog = new java.util.concurrent.atomic.AtomicReference<>(new float[]{0f});
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);

        Donut3D(Context ctx) {
            super(ctx);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        void setData(String[] labels, float[] norm, int[] colors, String center) {
            this.labels = labels == null ? new String[0] : labels;
            this.norm = norm == null ? new float[0] : norm;
            this.colors = colors == null ? new int[0] : colors;
            this.center = center == null ? "" : center;
            growIn(this, prog);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            try { paint(cv); } catch (Throwable ignored) { }
        }

        private void paint(Canvas cv) {
            int n = norm.length;
            if (n == 0 || getWidth() == 0 || colors.length == 0) return;
            float pr = prog.get()[0];
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float rx = Math.min(w, h) * 0.42f;
            float ry = rx * 0.62f;              // vertical squash = the 3D tilt
            float th = rx * 0.34f;             // ring thickness
            float depth = ry * 0.28f;          // extrusion height

            double total = 0; for (float f : norm) total += Math.max(0, f);
            if (total <= 0) total = 1;

            // extruded side (draw the ring twice: darker underneath, brighter on top)
            for (int pass = 0; pass < 2; pass++) {
                float dy = pass == 0 ? depth : 0;
                float start = -90f;
                for (int i = 0; i < n; i++) {
                    float sweep = (float) (Math.max(0, norm[i]) / total * 360f) * pr;
                    int col = colors[i % colors.length];
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(th);
                    fill.setColor(pass == 0 ? shade(col, 0.45f) : col);
                    RectF box = new RectF(cx - rx, cy - ry + dy, cx + rx, cy + ry + dy);
                    cv.drawArc(box, start, Math.max(0.5f, sweep - 1.5f), false, fill);
                    start += sweep;
                }
            }

            // lit rim highlight
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(2f);
            fill.setColor(Color.argb(90, 255, 255, 255));
            cv.drawOval(new RectF(cx - rx + th / 2, cy - ry + th / 2, cx + rx - th / 2, cy + ry - th / 2), fill);

            // hub
            txt.setColor(Color.WHITE);
            float ts = rx * 0.22f;
            txt.setTextSize(ts);
            cv.drawText(center, cx, cy + ts * 0.2f, txt);
            txt.setTextSize(Math.max(8f, rx * 0.11f));
            txt.setColor(Color.argb(200, 255, 255, 255));
            cv.drawText("جمع", cx, cy + ts * 0.2f + rx * 0.16f, txt);
        }
    }
}
