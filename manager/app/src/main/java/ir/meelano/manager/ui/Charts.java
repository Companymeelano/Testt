package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
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
    /**
     * Money axis in the DISPLAY unit (Toman by default): the rest of the app shows
     * Toman, so an axis of raw Rial magnitudes used to read ten times too large.
     */
    public static final Formatter MONEY = v -> Money.compact(Money.toUnit(v));

    /** 1 / 2 / 2.5 / 5 × 10ⁿ — the friendliest step that covers the raw span. */
    private static double niceStep(double raw) {
        if (!(raw > 0) || Double.isNaN(raw)) return 1;
        double exp = Math.floor(Math.log10(raw));
        double base = Math.pow(10, exp);
        double f = raw / base;
        double n = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return n * base;
    }

    private static final int[] PALETTE = {
            0xFFD9AE5A, 0xFF4FA3FF, 0xFF35C26E, 0xFF9A7BFF, 0xFFF5A524,
            0xFFE5484D, 0xFF3ED6C5, 0xFFF27BB5, 0xFF8BC34A, 0xFF7C8DA6
    };
    /** Deeper jewel tones for the light theme (contrast on white paper). */
    private static final int[] PALETTE_LIGHT = {
            0xFFB07A1E, 0xFF2B7FFF, 0xFF1E9E57, 0xFF7C5CFC, 0xFFE08A00,
            0xFFD92D20, 0xFF0E9384, 0xFFD6508B, 0xFF669F2A, 0xFF667085
    };

    /** Gold tones for accounting/chart-of-accounts charts: deep gold, light gold, bronze, champagne. */
    public static int goldTone(int i) {
        int[] t = {Theme.GOLD, Theme.GOLD_SOFT, mix(Theme.GOLD, 0xFF000000, 0.28f), mix(Theme.GOLD, 0xFFFFFFFF, 0.30f)};
        return t[Math.abs(i) % t.length];
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Short label for tight axes: at most n characters, ellipsis when cut. */
    public static String trim(String s, int n) {
        return ellipsize(s == null ? "" : s.trim(), n);
    }

    public static int palette(int i) {
        int[] p = Theme.DARK ? PALETTE : PALETTE_LIGHT;
        return p[Math.abs(i) % p.length];
    }


    /** Pseudo-3D block: front face with gradient, lighter top face, darker side face (light from upper-left). */
    static void drawBar3D(Canvas g, RectF r, int col, float radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float d = Theme.dp(5);
        Path side = new Path();
        side.moveTo(r.right, r.top + radius);
        side.lineTo(r.right + d, r.top + radius - d);
        side.lineTo(r.right + d, r.bottom - d);
        side.lineTo(r.right, r.bottom);
        side.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.shade(col, -0.38f));
        g.drawPath(side, paint);
        Path top = new Path();
        top.moveTo(r.left + radius, r.top);
        top.lineTo(r.left + radius + d, r.top - d);
        top.lineTo(r.right + d - radius, r.top - d);
        top.lineTo(r.right - radius, r.top);
        top.close();
        paint.setColor(Theme.shade(col, 0.28f));
        g.drawPath(top, paint);
        paint.setShader(new LinearGradient(0, r.top, 0, r.bottom, Theme.shade(col, 0.08f), Theme.shade(col, -0.22f), Shader.TileMode.CLAMP));
        g.drawRoundRect(r, radius, radius, paint);
        paint.setShader(null);
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

        float downX, downY;
        boolean tracking;
        int touchSlop;

        Base(Context c) {
            super(c);
            text.setTypeface(Theme.face(false));
            text.setTextSize(chartTextPx(10));
            setMinimumHeight(Theme.dp(120));
            try {
                touchSlop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
            } catch (Exception e) {
                touchSlop = Theme.dp(12);
            }
        }

        /** Canvas text must follow both Android's font scale and the app's zoom/type scale. */
        float chartTextPx(float sp) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float scaledDensity = dm.scaledDensity > 0 ? dm.scaledDensity : dm.density;
            return sp * scaledDensity * Theme.fontScale() * Theme.typeScale();
        }

        float axisLabelPad(double minValue, double maxValue, int widthPx) {
            text.setTypeface(Theme.face(false));
            text.setTextSize(chartTextPx(10));
            float label = Math.max(text.measureText(Money.fa(fmt.format(minValue))),
                    text.measureText(Money.fa(fmt.format(maxValue))));
            float preferred = Math.max(Theme.dp(46), label + Theme.dp(8));
            return Math.min(Math.max(Theme.dp(40), widthPx * 0.42f), preferred);
        }

        /** True when the gesture is a horizontal scrub (selection); vertical drags scroll. */
        boolean scrubbing(float x, float y) {
            float dx = Math.abs(x - downX), dy = Math.abs(y - downY);
            return dx > touchSlop && dx > dy;
        }

        boolean tapped(float x, float y) {
            return Math.abs(x - downX) <= touchSlop && Math.abs(y - downY) <= touchSlop;
        }

        void holdParent() {
            try {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            } catch (Exception ignored) { Diag.ignored("Charts:133", ignored); }
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
            setClickable(l != null);
            setFocusable(l != null);
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

    static String fitText(String s, Paint paint, float maxWidth) {
        String value = s == null ? "" : s;
        if (paint.measureText(value) <= maxWidth) return value;
        String prefix = value;
        while (prefix.codePointCount(0, prefix.length()) > 1 && paint.measureText(prefix + "…") > maxWidth) {
            int lastCodePoint = prefix.offsetByCodePoints(0, prefix.codePointCount(0, prefix.length()) - 1);
            prefix = prefix.substring(0, lastCodePoint);
        }
        return prefix.codePointCount(0, prefix.length()) <= 1
                && paint.measureText(prefix + "…") > maxWidth ? "…" : prefix + "…";
    }

    /** Gold tooltip bubble with theme-aware text, clamped inside [0, maxW]. */
    static void bubble(Canvas g, Paint paint, Paint text, String s, float cx, float yText,
                       float maxW, float fontSize) {
        text.setTextSize(fontSize);
        text.setTypeface(Theme.face(true));
        text.setTextAlign(Paint.Align.CENTER);
        float innerMax = Math.max(Theme.dp(24), maxW - Theme.dp(28));
        String shown = fitText(s, text, innerMax);
        float tw = Math.min(text.measureText(shown), innerMax);
        float bx = Math.max(tw / 2f + Theme.dp(14), Math.min(maxW - tw / 2f - Theme.dp(14), cx));
        float padV = Theme.dp(4);
        float by = Math.max(yText + fontSize * 0.38f, fontSize * 0.9f + padV);
        RectF bb = new RectF(bx - tw / 2f - Theme.dp(10), by - fontSize * 0.9f - padV,
                bx + tw / 2f + Theme.dp(10), by + fontSize * 0.35f + padV);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Theme.GOLD);
        g.drawRoundRect(bb, Theme.dp(10), Theme.dp(10), paint);
        int oldC = text.getColor();
        text.setColor(Theme.onAccent());
        g.drawText(shown, bx, by, text);
        text.setColor(oldC);
        text.setTypeface(Theme.face(false));
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
            float padR = Theme.dp(10), padT = Theme.dp(12), padB = Theme.dp(24);
            if (data.isEmpty()) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(chartTextPx(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", w / 2f, h / 2f, paint);
                return;
            }
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            float padL = axisLabelPad(mn, mx, w);
            plot.set(padL, padT, w - padR, h - padB);
            int n = data.size();
            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                // RTL: first (oldest) point at the right, time flows right → left.
                xs[i] = n == 1 ? plot.centerX() : plot.right - plot.width() * i / (n - 1);
                ys[i] = (float) (plot.bottom - plot.height() * ((data.get(i).value - mn) / (mx - mn)) * progress);
            }
            float zeroY = (float) (plot.bottom - plot.height() * ((0 - mn) / (mx - mn)) * progress);
            // grid + y labels — round steps read better than thirds of the max.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(Theme.alpha(Theme.TEXT, 22));
            text.setColor(Theme.MUTED);
            text.setTextAlign(Paint.Align.RIGHT);
            double valStep = niceStep((mx - mn) / 3.0);
            double lo = Math.floor(mn / valStep) * valStep;
            int guard = 0;
            for (double v = lo; v <= mx + valStep * 0.5 && guard < 8; v += valStep, guard++) {
                float y = (float) (plot.bottom - plot.height() * ((v - mn) / (mx - mn)));
                g.drawLine(plot.left, y, plot.right, y, paint);
                g.drawText(Money.fa(fmt.format(v)), plot.left - Theme.dp(4), y + Theme.dp(4), text);
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
            int xLabelCount = Math.max(1, (n + step - 1) / step);
            float xLabelWidth = Math.max(Theme.dp(24), plot.width() / xLabelCount - Theme.dp(4));
            for (int i = 0; i < n; i += step) {
                String label = fitText(data.get(i).label, text, xLabelWidth);
                g.drawText(Money.fa(label), xs[i], plot.bottom + Theme.dp(16), text);
            }
            // selected dot + tooltip bubble
            if (selected >= 0 && selected < n) {
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(null);
                paint.setColor(Theme.TEXT);
                g.drawCircle(xs[selected], ys[selected], Theme.dp(4.5f), paint);
                paint.setColor(accent);
                g.drawCircle(xs[selected], ys[selected], Theme.dp(2.6f), paint);
                Point sp = data.get(selected);
                bubble(g, paint, text, Money.fa(sp.label + "  •  " + fmt.format(sp.value)),
                        xs[selected], ys[selected] - Theme.dp(26), w, chartTextPx(10.5f));
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getAction();
            if (act == MotionEvent.ACTION_DOWN && !data.isEmpty()) {
                downX = e.getX();
                downY = e.getY();
                tracking = true;
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE && tracking && !data.isEmpty()) {
                if (scrubbing(e.getX(), e.getY())) {
                    holdParent();
                    selectNear(e.getX());
                }
                return true;
            }
            if (act == MotionEvent.ACTION_UP && tracking && !data.isEmpty() && tapped(e.getX(), e.getY())) {
                selectNear(e.getX());
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) tracking = false;
            return super.onTouchEvent(e);
        }

        private void selectNear(float x) {
            int n = data.size();
            if (n == 0 || plot.width() <= 0) return;
            float best = Float.MAX_VALUE;
            int idx = 0;
            for (int i = 0; i < n; i++) {
                float px = n == 1 ? plot.centerX() : plot.right - plot.width() * i / (n - 1);
                float d = Math.abs(px - x);
                if (d < best) { best = d; idx = i; }
            }
            if (idx != selected) {
                selected = idx;
                invalidate();
                fire(idx);
            }
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
            float padR = Theme.dp(10), padT = Theme.dp(12), padB = Theme.dp(24);
            if (data.isEmpty()) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(chartTextPx(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", w / 2f, h / 2f, paint);
                return;
            }
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            float padL = axisLabelPad(mn, mx, w);
            plot.set(padL, padT, w - padR, h - padB);
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
                drawBar3D(g, r, col, Theme.dp(6));
                if (i % step == 0) {
                    text.setColor(Theme.MUTED);
                    // Label width follows the bar slot and scales with accessibility text.
                    float maxLabelWidth = Math.max(Theme.dp(24), slot - Theme.dp(6));
                    g.drawText(Money.fa(fitText(p.label, text, maxLabelWidth)),
                            cx, plot.bottom + Theme.dp(16), text);
                }
            }
            // tooltip bubble over the selected bar
            if (selected >= 0 && selected < n) {
                Point sp = data.get(selected);
                float cx = plot.right - slot * selected - slot / 2f;
                float yv = (float) (plot.bottom - plot.height() * ((sp.value - mn) / (mx - mn)) * progress);
                bubble(g, paint, text, Money.fa(sp.label + "  •  " + fmt.format(sp.value)),
                        cx, Math.min(yv, zeroY) - Theme.dp(12), w, chartTextPx(10.5f));
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int act = e.getAction();
            if (act == MotionEvent.ACTION_DOWN && !data.isEmpty()) {
                downX = e.getX();
                downY = e.getY();
                tracking = true;
                return true;
            }
            if (act == MotionEvent.ACTION_MOVE && tracking && !data.isEmpty()) {
                if (scrubbing(e.getX(), e.getY())) {
                    holdParent();
                    selectSlot(e.getX());
                }
                return true;
            }
            if (act == MotionEvent.ACTION_UP && tracking && !data.isEmpty() && tapped(e.getX(), e.getY())) {
                selectSlot(e.getX());
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) tracking = false;
            return super.onTouchEvent(e);
        }

        private void selectSlot(float x) {
            int n = data.size();
            if (n == 0 || plot.width() <= 0) return;
            float slot = plot.width() / n;
            int idx = (int) ((plot.right - x) / slot);
            if (idx < 0) idx = 0;
            if (idx >= n) idx = n - 1;
            if (idx != selected) {
                selected = idx;
                invalidate();
                fire(idx);
            }
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

        /** Ring radius depends on width only, so measurement and drawing agree. */
        private static float ringRadius(int w) {
            return Math.min(w - Theme.dp(24), Theme.dp(170)) / 2f - Theme.dp(8);
        }

        private float legendRowHeight() {
            return Math.max(Theme.dp(18), chartTextPx(10) + Theme.dp(6));
        }

        /** Height must fit the ring AND every font-scaled legend row. */
        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int shown = Math.max(1, Math.min(8, data.size()));
            int rows = (shown + 1) / 2;
            float radius = ringRadius(w);
            int want = (int) Math.ceil(radius * 2 + Theme.dp(10) + Theme.dp(20)
                    + rows * legendRowHeight() + Theme.dp(10));
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            double sum = 0;
            for (Point p : data) sum += Math.max(0, p.value);
            float cx = w / 2f;
            float radius = ringRadius(w);
            float cy = radius + Theme.dp(10);
            if (data.isEmpty() || sum <= 0) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(chartTextPx(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", cx, h / 2f, paint);
                return;
            }
            RectF o = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(radius * 0.34f);
            paint.setStrokeCap(Paint.Cap.BUTT);
            // extruded depth: darker copy of the ring offset downward, then the lit top ring
            float depth = Theme.dp(6);
            RectF od = new RectF(o.left, o.top + depth, o.right, o.bottom + depth);
            float start = -90f;
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                float sweep = (float) (360.0 * Math.max(0, p.value) / sum * progress);
                int col = p.color != 0 ? p.color : palette(i);
                if (sweep > 1f) {
                    paint.setColor(Theme.shade(col, -0.4f));
                    g.drawArc(od, start + 1.2f, sweep - 2.4f, false, paint);
                }
                start += (float) (360.0 * Math.max(0, p.value) / sum);
            }
            start = -90f;
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                float sweep = (float) (360.0 * Math.max(0, p.value) / sum * progress);
                int col = p.color != 0 ? p.color : palette(i);
                paint.setColor(col);
                if (sweep > 1f) g.drawArc(o, start + 1.2f, sweep - 2.4f, false, paint);
                start += (float) (360.0 * Math.max(0, p.value) / sum);
            }
            // center text follows large/system fonts without overlapping in the ring's hole.
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            String cb = Money.fa(centerBottom);
            float holeW = radius * 1.15f;
            float topSize = chartTextPx(10);
            float bottomSize = chartTextPx(15);
            paint.setTypeface(Theme.face(true));
            paint.setTextSize(bottomSize);
            while (bottomSize > chartTextPx(9) && paint.measureText(cb) > holeW) {
                bottomSize = Math.max(chartTextPx(9), bottomSize - chartTextPx(1));
                paint.setTextSize(bottomSize);
            }
            float centerGap = (topSize + bottomSize) * 0.45f;
            paint.setColor(Theme.MUTED);
            paint.setTypeface(Theme.face(false));
            paint.setTextSize(topSize);
            g.drawText(fitText(Money.fa(centerTop), paint, holeW), cx, cy - centerGap, paint);
            paint.setColor(Theme.TEXT);
            paint.setTypeface(Theme.face(true));
            paint.setTextSize(bottomSize);
            g.drawText(cb, cx, cy + centerGap, paint);
            paint.setTypeface(Theme.face(false));
            // legend (two columns, up to 8); fit text to the actual column at the user's zoom.
            float ly = cy + radius + Theme.dp(20);
            float rowHeight = legendRowHeight();
            paint.setTextSize(chartTextPx(10));
            int shown = Math.min(8, data.size());
            float columnWidth = Math.max(Theme.dp(54), w / 2f - Theme.dp(30));
            for (int i = 0; i < shown; i++) {
                Point p = data.get(i);
                int col = p.color != 0 ? p.color : palette(i);
                boolean right = i % 2 == 0;
                float lx = right ? w - Theme.dp(14) : w / 2f - Theme.dp(8);
                float row = ly + (i / 2) * rowHeight;
                if (row > h - Theme.dp(2)) break;
                paint.setColor(col);
                g.drawCircle(lx - Theme.dp(5), row - paint.getTextSize() * 0.35f, Theme.dp(4), paint);
                paint.setColor(Theme.TEXT);
                paint.setTextAlign(Paint.Align.RIGHT);
                String percentage = Money.pct(sum > 0 ? Math.max(0, p.value) * 100.0 / sum : 0);
                float labelWidth = Math.max(Theme.dp(24), columnWidth - paint.measureText(percentage) - Theme.dp(8));
                String label = fitText(p.label, paint, labelWidth);
                g.drawText(Money.fa(label + "  " + percentage), lx - Theme.dp(13), row, paint);
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
            selected = -1;
            animateIn();
        }

        private float labelBaselineOffset() {
            return Math.max(Theme.dp(12), chartTextPx(11) * 0.9f);
        }

        private float trackTopOffset() {
            return labelBaselineOffset() + chartTextPx(11) * 0.25f + Theme.dp(4);
        }

        private float rowHeightPx() {
            return Math.max(Theme.dp(56), trackTopOffset() + Theme.dp(15) + Theme.dp(10));
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int rows = Math.max(1, data.size());
            int want = (int) Math.ceil(rows * rowHeightPx() + Theme.dp(10));
            super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            int w = getWidth();
            if (w <= 0) return;
            if (data.isEmpty()) {
                paint.setColor(Theme.MUTED);
                paint.setTextSize(chartTextPx(11));
                paint.setTextAlign(Paint.Align.CENTER);
                g.drawText("داده‌ای برای نمایش نیست", w / 2f, getHeight() / 2f, paint);
                return;
            }
            double mx = Math.max(0, max());
            double mn = Math.min(0, min());
            if (mx - mn <= 0) mx = mn + 1;
            float rowHeight = rowHeightPx();
            float labelBaseline = labelBaselineOffset();
            float barH = Theme.dp(15);
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                float top = Theme.dp(8) + i * rowHeight;
                if (selected == i) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setShader(null);
                    paint.setColor(Theme.alpha(Theme.GOLD, Theme.DARK ? 28 : 18));
                    g.drawRoundRect(new RectF(Theme.dp(2), top - Theme.dp(4),
                            w - Theme.dp(2), top + rowHeight - Theme.dp(2)), Theme.dp(8), Theme.dp(8), paint);
                }
                // rank medal (gold / silver / bronze / outline)
                float mr = Theme.dp(9);
                float mCx = w - Theme.dp(8) - mr;
                float mCy = top + Math.max(Theme.dp(7), labelBaseline - chartTextPx(11) * 0.45f);
                int mCol = i == 0 ? 0xFFFFD166 : i == 1 ? 0xFFC9D2DE : i == 2 ? 0xFFE0A066 : 0;
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(null);
                if (mCol != 0) {
                    paint.setColor(mCol);
                    g.drawCircle(mCx, mCy, mr, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Theme.dp(1.2f));
                    paint.setColor(Theme.alpha(Theme.MUTED, 170));
                    g.drawCircle(mCx, mCy, mr, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                text.setTextSize(chartTextPx(10));
                text.setTypeface(Theme.face(true));
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(mCol != 0 ? 0xFF232323 : Theme.MUTED);
                g.drawText(Money.fa(String.valueOf(i + 1)), mCx, mCy + chartTextPx(10) * 0.34f, text);
                // line 1: label (right of medal) + value (left), both fitted to the available width
                text.setTextSize(chartTextPx(11));
                String valueLabel = Money.fa(fmt.format(p.value));
                valueLabel = fitText(valueLabel, text, Math.max(Theme.dp(28), w * 0.34f));
                float valueWidth = text.measureText(valueLabel);
                float labelRight = mCx - mr - Theme.dp(6);
                float labelLeft = Theme.dp(8) + valueWidth + Theme.dp(12);
                float labelWidth = Math.max(Theme.dp(24), labelRight - labelLeft);
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(selected == i ? Theme.GOLD_SOFT : Theme.TEXT);
                g.drawText(Money.fa(fitText(p.label, text, labelWidth)), labelRight, top + labelBaseline, text);
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(Theme.GOLD_SOFT);
                g.drawText(valueLabel, Theme.dp(8), top + labelBaseline, text);
                text.setTypeface(Theme.face(false));
                // line 2: full-width track + fill
                float bTop = top + trackTopOffset();
                RectF track = new RectF(Theme.dp(8), bTop, w - Theme.dp(8), bTop + barH);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.alpha(Theme.TEXT, 46));
                g.drawRoundRect(track, Theme.dp(7), Theme.dp(7), paint);
                // fill from the zero line (right edge when all values are >= 0, as before)
                float zx = track.right - track.width() * (float) ((0 - mn) / (mx - mn));
                float xv = track.right - track.width() * (float) ((p.value - mn) / (mx - mn));
                xv = zx + (xv - zx) * progress;
                int col = p.color != 0 ? p.color : (p.value < 0 ? Theme.DANGER : palette(i));
                if (selected >= 0 && selected != i) col = Theme.alpha(col, 100);
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

        @Override
        public boolean performClick() {
            if (listener == null || data.isEmpty()) return super.performClick();
            if (selected < 0 || selected >= data.size()) selected = 0;
            invalidate();
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            fire(selected);
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (listener == null || data.isEmpty()) return super.onTouchEvent(e);
            int action = e.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                tracking = true;
                return true;
            }
            if (action == MotionEvent.ACTION_UP && tracking && tapped(e.getX(), e.getY())) {
                float rowY = e.getY() - Theme.dp(8);
                int index = (int) (rowY / rowHeightPx());
                if (rowY >= 0 && index >= 0 && index < data.size()) {
                    selected = index;
                    invalidate();
                    performClick();
                }
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) tracking = false;
            return true;
        }
    }

    // ================= gauge (goal achievement) =================
    public static final class Gauge extends Base {
        double frac = 0;
        String center = "";
        Integer arcColor = null;
        String caption = "تحقق هدف";

        public Gauge(Context c) {
            super(c);
            setMinimumHeight(Theme.dp(140));
        }

        public void set(double fraction, String centerText) {
            set(fraction, centerText, 0, null);
        }

        public void set(double fraction, String centerText, int color, String captionText) {
            frac = Math.max(0, Math.min(1.2, fraction));
            center = centerText == null ? "" : centerText;
            if (color != 0) arcColor = color;
            if (captionText != null) caption = captionText;
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
            // tick ring
            paint.setStrokeWidth(Theme.dp(2));
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(Theme.alpha(Theme.MUTED, 160));
            for (int i = 0; i <= 10; i++) {
                double a = Math.toRadians(180 + i * 18);
                float r1 = radius - Theme.dp(11), r2 = radius - Theme.dp(16);
                g.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                        cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, paint);
            }
            paint.setStrokeWidth(Theme.dp(13));
            paint.setStrokeCap(Paint.Cap.ROUND);
            int col = arcColor != null ? arcColor : (frac >= 1 ? Theme.SUCCESS : (frac >= 0.7 ? Theme.GOLD : Theme.WARNING));
            paint.setColor(col);
            g.drawArc(o, 180, (float) (180 * Math.min(1, frac) * progress), false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Theme.face(true));
            paint.setColor(Theme.TEXT);
            float centerSize = chartTextPx(17);
            paint.setTextSize(centerSize);
            g.drawText(fitText(Money.fa(center), paint, Math.max(Theme.dp(30), w - Theme.dp(32))),
                    cx, cy - Math.max(Theme.dp(20), centerSize * 1.2f), paint);
            paint.setTypeface(Theme.face(false));
            paint.setColor(Theme.MUTED);
            paint.setTextSize(chartTextPx(10.5f));
            g.drawText(fitText(Money.fa(caption), paint, Math.max(Theme.dp(30), w - Theme.dp(32))),
                    cx, cy - Theme.dp(4), paint);
        }
    }
}
