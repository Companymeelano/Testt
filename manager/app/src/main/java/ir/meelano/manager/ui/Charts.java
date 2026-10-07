package ir.meelano.manager.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import ir.meelano.manager.core.Money;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free luxury charts: area, bars, donut, ranking bars, gauge. */
public final class Charts {
    private Charts() { }

    public static final class Point {
        public final String label;
        public final double value;
        public final int color;

        public Point(String label, double value) {
            this(label, value, 0);
        }

        public Point(String label, double value, int color) {
            this.label = label == null ? "" : label;
            this.value = value;
            this.color = color;
        }
    }

    public interface Formatter {
        String format(double v);
    }

    public interface PickListener {
        void onPick(int idx, Point p);
    }

    public static final Formatter COMPACT = Money::compact;
    public static final Formatter FULL = Money::rial;

    private static final int[] PALETTE = {
            0xFFD9AE5A, 0xFF4FA3FF, 0xFF35C26E, 0xFF9A7BFF, 0xFFF5A524,
            0xFFE5484D, 0xFF3ED6C5, 0xFFF27BB5, 0xFF8BC34A, 0xFF7C8DA6
    };

    public static int palette(int i) {
        return PALETTE[Math.abs(i) % PALETTE.length];
    }

    // ================= base =================
    abstract static class Base extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<Point> data = new ArrayList<>();
        float progress = 0f;
        int selected = -1;
        Formatter fmt = COMPACT;
        PickListener listener;

        Base(Context c) {
            super(c);
            text.setTypeface(Theme.face(false));
            text.setTextSize(Theme.dp(10));
            setMinimumHeight(Theme.dp(120));
        }

        void animateIn() {
            ValueAnimator v = ValueAnimator.ofFloat(0f, 1f);
            v.setDuration(750);
            v.setInterpolator(new DecelerateInterpolator());
            v.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            v.start();
        }

        public void setListener(PickListener l) {
            listener = l;
        }

        void fire(int idx) {
            if (listener != null && idx >= 0 && idx < data.size()) listener.onPick(idx, data.get(idx));
        }

        double max() {
            double m = 0;
            for (Point p : data) m = Math.max(m, p.value);
            return m <= 0 ? 1 : m;
        }

        double min() {
            double m = 0;
            for (Point p : data) m = Math.min(m, p.value);
            return m;
        }
    }

    static String ellipsize(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    // ================= area =================
    public static final class Area extends Base {
        private final RectF plot = new RectF();
        private int accent = Theme.GOLD;

        public Area(Context c) {
            super(c);
        }

        public void setData(List<Point> pts, int accentColor, Formatter f) {
            data.clear();
            if (pts != null) data.addAll(pts);
            if (accentColor != 0) accent = accentColor;
            if (f != null) fmt = f;
            selected = -1;
            animateIn();
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float padL = Theme.dp(46), padR = Theme.dp(10), padT = Theme.dp(12), padB = Theme.dp(24);
            plot.set(padL, padT, w - padR, h - padB);
            if (data.isEmpty()) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(Theme.dp(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", w / 2f, h / 2f, paint);
                return;
            }
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            int n = data.size();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = n == 1 ? plot.centerX() : plot.left + plot.width() * i / (n - 1);
                ys[i] = (float) (plot.bottom - plot.height() * ((data.get(i).value - mn) / (mx - mn)) * progress);
            }
            float zeroY = (float) (plot.bottom - plot.height() * ((0 - mn) / (mx - mn)) * progress);
            // grid + y labels
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Theme.alpha(Theme.TEXT, 22));
            text.setColor(Theme.MUTED);
            text.setTextAlign(Paint.Align.RIGHT);
            for (int k = 0; k <= 3; k++) {
                float y = plot.bottom - plot.height() * k / 3f;
                g.drawLine(plot.left, y, plot.right, y, paint);
                g.drawText(Money.fa(fmt.format(mn + (mx - mn) * k / 3.0)), plot.left - Theme.dp(4), y + Theme.dp(4), text);
            }
            if (mn < 0) {
                paint.setColor(Theme.alpha(Theme.TEXT, 80));
                g.drawLine(plot.left, zeroY, plot.right, zeroY, paint);
                paint.setColor(Theme.alpha(Theme.TEXT, 22));
            }
            // area
            Path area = new Path();
            area.moveTo(xs[0], zeroY);
            for (int i = 0; i < n; i++) area.lineTo(xs[i], ys[i]);
            area.lineTo(xs[n - 1], zeroY);
            area.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, plot.top, 0, plot.bottom,
                    Theme.alpha(accent, 120), Theme.alpha(accent, 8), Shader.TileMode.CLAMP));
            g.drawPath(area, paint);
            paint.setShader(null);
            // line
            Path line = new Path();
            line.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                float cx = (xs[i - 1] + xs[i]) / 2f;
                line.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dp(2.4f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(accent);
            g.drawPath(line, paint);
            // x labels (max ~6)
            text.setColor(Theme.MUTED);
            text.setTextAlign(Paint.Align.CENTER);
            int step = Math.max(1, (n + 5) / 6);
            for (int i = 0; i < n; i += step) {
                g.drawText(Money.fa(ellipsize(data.get(i).label, 8)), xs[i], plot.bottom + Theme.dp(16), text);
            }
            // selected
            if (selected >= 0 && selected < n) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.TEXT);
                g.drawCircle(xs[selected], ys[selected], Theme.dp(4.5f), paint);
                paint.setColor(accent);
                g.drawCircle(xs[selected], ys[selected], Theme.dp(2.6f), paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if ((e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) && !data.isEmpty()) {
                int n = data.size();
                float best = Float.MAX_VALUE;
                int idx = 0;
                for (int i = 0; i < n; i++) {
                    float x = n == 1 ? plot.centerX() : plot.right - plot.width() * i / (n - 1);
                    float d = Math.abs(x - e.getX());
                    if (d < best) { best = d; idx = i; }
                }
                if (idx != selected) {
                    selected = idx;
                    invalidate();
                    fire(idx);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    // ================= vertical bars =================
    public static final class Bars extends Base {
        private final RectF plot = new RectF();

        public Bars(Context c) {
            super(c);
        }

        public void setData(List<Point> pts, Formatter f) {
            data.clear();
            if (pts != null) data.addAll(pts);
            if (f != null) fmt = f;
            selected = -1;
            animateIn();
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float padL = Theme.dp(46), padR = Theme.dp(10), padT = Theme.dp(12), padB = Theme.dp(24);
            plot.set(padL, padT, w - padR, h - padB);
            if (data.isEmpty()) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(Theme.dp(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", w / 2f, h / 2f, paint);
                return;
            }
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            int n = data.size();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Theme.alpha(Theme.TEXT, 22));
            text.setColor(Theme.MUTED);
            text.setTextAlign(Paint.Align.RIGHT);
            for (int k = 0; k <= 3; k++) {
                float y = plot.bottom - plot.height() * k / 3f;
                g.drawLine(plot.left, y, plot.right, y, paint);
                g.drawText(Money.fa(fmt.format(mn + (mx - mn) * k / 3.0)), plot.left - Theme.dp(4), y + Theme.dp(4), text);
            }
            float zeroY = (float) (plot.bottom - plot.height() * ((0 - mn) / (mx - mn)) * progress);
            if (mn < 0) {
                paint.setColor(Theme.alpha(Theme.TEXT, 80));
                g.drawLine(plot.left, zeroY, plot.right, zeroY, paint);
            }
            float slot = plot.width() / n;
            float bw = Math.min(slot * 0.52f, Theme.dp(34));
            paint.setStyle(Paint.Style.FILL);
            text.setTextAlign(Paint.Align.CENTER);
            int step = Math.max(1, (n + 5) / 6);
            for (int i = 0; i < n; i++) {
                Point p = data.get(i);
                int col = p.color != 0 ? p.color : (p.value < 0 ? Theme.DANGER : palette(i));
                if (selected >= 0 && i != selected) col = Theme.alpha(col, 90);
                // RTL: first (top-ranked) bar at the right.
                float cx = plot.right - slot * i - slot / 2f;
                float yv = (float) (plot.bottom - plot.height() * ((p.value - mn) / (mx - mn)) * progress);
                float top = Math.min(yv, zeroY);
                float bot = Math.max(yv, zeroY);
                if (bot - top < Theme.dp(3)) bot = top + Theme.dp(3);
                RectF r = new RectF(cx - bw / 2f, top, cx + bw / 2f, bot);
                paint.setShader(new LinearGradient(0, top, 0, bot, col, Theme.alpha(col, 120), Shader.TileMode.CLAMP));
                g.drawRoundRect(r, Theme.dp(6), Theme.dp(6), paint);
                paint.setShader(null);
                if (i % step == 0) {
                    text.setColor(Theme.MUTED);
                    g.drawText(Money.fa(ellipsize(p.label, 7)), cx, plot.bottom + Theme.dp(16), text);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if ((e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) && !data.isEmpty()) {
                int n = data.size();
                float slot = plot.width() / n;
                int idx = (int) ((plot.right - e.getX()) / slot);
                if (idx < 0) idx = 0;
                if (idx >= n) idx = n - 1;
                if (idx != selected) {
                    selected = idx;
                    invalidate();
                    fire(idx);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    // ================= donut =================
    public static final class Donut extends Base {
        String centerTop = "";
        String centerBottom = "";

        public Donut(Context c) {
            super(c);
            setMinimumHeight(Theme.dp(150));
        }

        public void setData(List<Point> pts, String top, String bottom) {
            data.clear();
            if (pts != null) {
                // Top 7 + سایر.
                List<Point> sorted = new ArrayList<>(pts);
                java.util.Collections.sort(sorted, (x, y) -> Double.compare(y.value, x.value));
                double rest = 0;
                for (int i = 0; i < sorted.size(); i++) {
                    if (i < 7) data.add(sorted.get(i));
                    else rest += sorted.get(i).value;
                }
                if (rest > 0.5) data.add(new Point("سایر", rest, Theme.alpha(Theme.MUTED, 200)));
            }
            centerTop = top == null ? "" : top;
            centerBottom = bottom == null ? "" : bottom;
            animateIn();
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            double sum = 0;
            for (Point p : data) sum += Math.max(0, p.value);
            float cx = w / 2f;
            float radius = Math.min(w, h - Theme.dp(46)) / 2f - Theme.dp(8);
            float cy = radius + Theme.dp(10);
            if (data.isEmpty() || sum <= 0) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(Theme.dp(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", cx, h / 2f, paint);
                return;
            }
            RectF o = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(radius * 0.34f);
            paint.setStrokeCap(Paint.Cap.BUTT);
            float start = -90f;
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                float sweep = (float) (360.0 * Math.max(0, p.value) / sum * progress);
                int col = p.color != 0 ? p.color : palette(i);
                paint.setColor(col);
                if (sweep > 1f) g.drawArc(o, start + 1.2f, sweep - 2.4f, false, paint);
                start += (float) (360.0 * Math.max(0, p.value) / sum);
            }
            // center
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Theme.MUTED);
            paint.setTextSize(Theme.dp(10));
            paint.setTypeface(Theme.face(false));
            g.drawText(Money.fa(centerTop), cx, cy - Theme.dp(2), paint);
            paint.setColor(Theme.TEXT);
            paint.setTypeface(Theme.face(true));
            String cb = Money.fa(centerBottom);
            float holeW = radius * 1.15f;
            float ts = Theme.dp(15);
            paint.setTextSize(ts);
            while (ts > Theme.dp(9) && paint.measureText(cb) > holeW) { ts -= 1; paint.setTextSize(ts); }
            g.drawText(cb, cx, cy + Theme.dp(17), paint);
            paint.setTypeface(Theme.face(false));
            // legend (two columns, up to 8)
            float ly = cy + radius + Theme.dp(20);
            paint.setTextSize(Theme.dp(10));
            int shown = Math.min(8, data.size());
            for (int i = 0; i < shown; i++) {
                Point p = data.get(i);
                int col = p.color != 0 ? p.color : palette(i);
                boolean right = i % 2 == 0;
                float lx = right ? w - Theme.dp(14) : w / 2f - Theme.dp(8);
                float row = ly + (i / 2) * Theme.dp(18);
                if (row > h - Theme.dp(2)) break;
                paint.setColor(col);
                g.drawCircle(lx - Theme.dp(5), row - Theme.dp(3.5f), Theme.dp(4), paint);
                paint.setColor(Theme.TEXT);
                paint.setTextAlign(Paint.Align.RIGHT);
                String leg = ellipsize(p.label, 12) + "  " + Money.pct(sum > 0 ? Math.max(0, p.value) * 100.0 / sum : 0);
                g.drawText(Money.fa(leg), lx - Theme.dp(13), row, paint);
            }
        }
    }

    // ================= horizontal ranking bars =================
    public static final class HBars extends Base {
        public HBars(Context c) {
            super(c);
        }

        public void setData(List<Point> pts, Formatter f) {
            data.clear();
            if (pts != null) data.addAll(pts.subList(0, Math.min(10, pts.size())));
            if (f != null) fmt = f;
            animateIn();
        }

        private static final int ROW_H = 56;

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int rows = Math.max(1, data.size());
            int want = rows * Theme.dp(ROW_H) + Theme.dp(10);
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth();
            if (w <= 0 || data.isEmpty()) return;
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            float barH = Theme.dp(15);
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                float top = Theme.dp(8) + i * Theme.dp(ROW_H);
                // line 1: label (right) + value (left) — never overlapping the bar
                text.setTextSize(Theme.dp(11));
                text.setTypeface(Theme.face(true));
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(Theme.TEXT);
                g.drawText(Money.fa(ellipsize(p.label, 24)), w - Theme.dp(8), top + Theme.dp(12), text);
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(Theme.GOLD_SOFT);
                g.drawText(Money.fa(fmt.format(p.value)), Theme.dp(8), top + Theme.dp(12), text);
                text.setTypeface(Theme.face(false));
                // line 2: full-width track + fill
                float bTop = top + Theme.dp(19);
                RectF track = new RectF(Theme.dp(8), bTop, w - Theme.dp(8), bTop + barH);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.alpha(Theme.TEXT, 46));
                g.drawRoundRect(track, Theme.dp(7), Theme.dp(7), paint);
                // fill from the zero line (right edge when all values are >= 0, as before)
                float zx = track.right - track.width() * (float) ((0 - mn) / (mx - mn));
                float xv = track.right - track.width() * (float) ((p.value - mn) / (mx - mn));
                xv = zx + (xv - zx) * progress;
                int col = p.color != 0 ? p.color : (p.value < 0 ? Theme.DANGER : palette(i));
                float fl = Math.max(track.left, Math.min(xv, zx));
                float fr = Math.min(track.right, Math.max(xv, zx));
                if (p.value != 0 && fr - fl < Theme.dp(8)) {
                    float mid = (fl + fr) / 2f;
                    fl = Math.max(track.left, mid - Theme.dp(4));
                    fr = Math.min(track.right, mid + Theme.dp(4));
                }
                RectF fill = new RectF(fl, bTop, fr, bTop + barH);
                paint.setShader(new LinearGradient(track.right, 0, track.left, 0, col, Theme.alpha(col, 140), Shader.TileMode.CLAMP));
                g.drawRoundRect(fill, Theme.dp(7), Theme.dp(7), paint);
                paint.setShader(null);
            }
        }
    }

    // ================= gauge (goal achievement) =================
    public static final class Gauge extends Base {
        double frac = 0;
        String center = "";

        public Gauge(Context c) {
            super(c);
            setMinimumHeight(Theme.dp(140));
        }

        public void set(double fraction, String centerText) {
            frac = Math.max(0, Math.min(1.2, fraction));
            center = centerText == null ? "" : centerText;
            animateIn();
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float radius = Math.min(w / 2f, h) - Theme.dp(22);
            float cy = h - Theme.dp(14);
            RectF o = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dp(13));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Theme.alpha(Theme.TEXT, 24));
            g.drawArc(o, 180, 180, false, paint);
            int col = frac >= 1 ? Theme.SUCCESS : (frac >= 0.7 ? Theme.GOLD : Theme.WARNING);
            paint.setColor(col);
            g.drawArc(o, 180, (float) (180 * Math.min(1, frac) * progress), false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Theme.face(true));
            paint.setColor(Theme.TEXT);
            paint.setTextSize(Theme.dp(17));
            g.drawText(Money.fa(center), cx, cy - Theme.dp(20), paint);
            paint.setTypeface(Theme.face(false));
            paint.setColor(Theme.MUTED);
            paint.setTextSize(Theme.dp(10.5f));
            g.drawText("تحقق هدف", cx, cy - Theme.dp(4), paint);
        }
    }
}
        g.drawText("تحقق هدف", cx, cy - Theme.dp(4), paint);
        }
    }
}
