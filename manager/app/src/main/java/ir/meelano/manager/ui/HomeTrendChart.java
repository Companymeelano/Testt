package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MonotoneCubic;

import java.util.ArrayList;
import java.util.List;

/** Shared, accessible Home trend chart for sales, purchases, returns and cancellations. */
public final class HomeTrendChart {
    private HomeTrendChart() { }

    /** A real daily aggregate. Values remain raw Rials until they reach the formatter. */
    public static final class Point {
        public final String date;
        public final double valueRials;
        public final long documents;

        public Point(String date, double valueRials, long documents) {
            this.date = Jalali.disp(date);
            this.valueRials = valueRials;
            this.documents = Math.max(0L, documents);
        }
    }

    public interface SelectionListener { void onSelect(Point point); }

    /** Canvas-only 3D-area chart; source order is oldest-to-newest, newest is shown on the left. */
    public static final class Area extends View {
        private final Paint axisPaint = paint(Paint.Style.STROKE);
        private final Paint textPaint = paint(Paint.Style.FILL);
        private final Paint areaPaint = paint(Paint.Style.FILL);
        private final Paint extrusionPaint = paint(Paint.Style.FILL);
        private final Paint lineShadowPaint = paint(Paint.Style.STROKE);
        private final Paint linePaint = paint(Paint.Style.STROKE);
        private final Paint highlightPaint = paint(Paint.Style.STROKE);
        private final Paint markerPaint = paint(Paint.Style.FILL);
        private final Paint bubblePaint = paint(Paint.Style.FILL);
        private final Paint bubbleText = paint(Paint.Style.FILL);
        private final Path areaPath = new Path();
        private final Path linePath = new Path();
        private final Path guidePath = new Path();
        private final RectF bubble = new RectF();
        private final RectF plot = new RectF();
        private final List<Point> points = new ArrayList<>();

        private float[] pointX = new float[0];
        private float[] pointY = new float[0];
        private float[] displayX = new float[0];
        private float[] displayY = new float[0];
        private double plotLeft, plotRight, plotTop, plotBottom, spacing;
        private double axisMinimum, axisMaximum = 1d, axisStep = 1d;
        private int accent = Theme.SECTION_SALES;
        private int selectedIndex = -1;
        private int touchSlop;
        private float downX, downY;
        private boolean available;
        private boolean touched;
        private boolean scrubbing;
        private boolean geometryDirty = true;
        private String seriesLabel = "روند";
        private String windowLabel = "بازهٔ انتخاب‌شده";
        private String unavailableMessage = "دادهٔ این نمودار در دسترس نیست";
        private SelectionListener selectionListener;

        public Area(Context context) {
            super(context);
            textPaint.setTypeface(Theme.face(false));
            textPaint.setFontFeatureSettings("tnum");
            bubbleText.setTypeface(Theme.face(true));
            bubbleText.setFontFeatureSettings("tnum");
            bubbleText.setTextAlign(Paint.Align.CENTER);
            try { touchSlop = ViewConfiguration.get(context).getScaledTouchSlop(); }
            catch (Exception ignored) { touchSlop = Theme.dp(8); }
            setFocusable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setMinimumHeight(Theme.dp(148));
        }

        private static Paint paint(Paint.Style style) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setStyle(style);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            return p;
        }

        private float sp(float size) {
            float density = getResources().getDisplayMetrics().scaledDensity;
            return size * density * Theme.fontScale() * Theme.typeScale();
        }

        public void setSelectionListener(SelectionListener listener) {
            selectionListener = listener;
        }

        /** Replace the chart with a complete successful daily series. */
        public void setData(List<Point> values, int accent, String series, String window) {
            points.clear();
            if (values != null) {
                for (Point point : values) {
                    if (point != null && !point.date.isEmpty()) points.add(point);
                }
            }
            this.accent = accent;
            this.seriesLabel = nonEmpty(series, "روند");
            this.windowLabel = nonEmpty(window, "بازهٔ انتخاب‌شده");
            this.unavailableMessage = "دادهٔ این نمودار در این بازه موجود نیست";
            this.available = values != null;
            this.selectedIndex = points.isEmpty() ? -1 : points.size() - 1;
            this.touched = false;
            this.geometryDirty = true;
            setClickable(available && !points.isEmpty());
            setContentDescription(description());
            invalidate();
        }

        /** No source, failed query, or incomplete monetary data: never render it as zero. */
        public void setUnavailable(String message, int accent, String series, String window) {
            points.clear();
            this.accent = accent;
            this.seriesLabel = nonEmpty(series, "روند");
            this.windowLabel = nonEmpty(window, "بازهٔ انتخاب‌شده");
            this.unavailableMessage = nonEmpty(message, "دادهٔ این نمودار در دسترس نیست");
            this.available = false;
            this.selectedIndex = -1;
            this.touched = false;
            this.geometryDirty = true;
            setClickable(false);
            setContentDescription(description());
            invalidate();
        }

        /** Keep a selected day when a user changes the time-window chips. */
        public void setSelectedDate(String date) {
            if (!available || points.isEmpty()) return;
            String wanted = Jalali.disp(date);
            if (wanted.isEmpty()) return;
            for (int i = 0; i < points.size(); i++) {
                if (wanted.equals(points.get(i).date)) {
                    select(i, false);
                    return;
                }
            }
        }

        public Point selectedPoint() {
            return selectedIndex < 0 || selectedIndex >= points.size()
                    ? null : points.get(selectedIndex);
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            geometryDirty = true;
        }

        private String description() {
            if (!available) return "نمودار " + seriesLabel + "، " + windowLabel
                    + "؛ داده در دسترس نیست: " + unavailableMessage;
            if (points.isEmpty()) return "نمودار " + seriesLabel + "، " + windowLabel
                    + "؛ در این بازه سندی ثبت نشده است";
            Point latest = points.get(points.size() - 1);
            return "نمودار " + seriesLabel + "، " + windowLabel
                    + "؛ مبلغ‌ها بر حسب " + MoneyUnit.name()
                    + "؛ روزهای جدیدتر در چپ و قدیمی‌تر در راست؛ آخرین روز "
                    + Jalali.dispFa(latest.date) + "، " + HomeNumberFormatter.money(latest.valueRials)
                    + "، " + HomeNumberFormatter.integer(latest.documents) + " " + documentNoun();
        }

        private String documentNoun() {
            if (seriesLabel.contains("خرید") || seriesLabel.contains("فروش")) return "فاکتور";
            return "سند";
        }

        private static String nonEmpty(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }

        private void ensureGeometry() {
            if (!geometryDirty || getWidth() <= 0 || getHeight() <= 0) return;
            geometryDirty = false;
            float width = getWidth();
            float height = getHeight();
            float textSize = sp(8.5f);
            textPaint.setTextSize(textSize);
            double minimum = 0d;
            double maximum = 0d;
            boolean haveValue = false;
            for (Point point : points) {
                if (!finite(point.valueRials)) continue;
                minimum = haveValue ? Math.min(minimum, point.valueRials) : point.valueRials;
                maximum = haveValue ? Math.max(maximum, point.valueRials) : point.valueRials;
                haveValue = true;
            }
            if (!haveValue) { minimum = 0d; maximum = 1d; }
            double span = maximum - minimum;
            if (!(span > 0d) || !finite(span)) span = Math.max(1d, Math.abs(maximum) * 0.2d);
            axisStep = niceStep(span / 4d);
            axisMinimum = Math.floor(minimum / axisStep) * axisStep;
            axisMaximum = Math.ceil(maximum / axisStep) * axisStep;
            if (!(axisMaximum > axisMinimum)) axisMaximum = axisMinimum + axisStep;
            if (axisMaximum - axisMinimum < axisStep * 3d) {
                double pad = Math.max(axisStep, (axisMaximum - axisMinimum) * 0.12d);
                axisMinimum = Math.floor((axisMinimum - pad) / axisStep) * axisStep;
                axisMaximum = Math.ceil((axisMaximum + pad) / axisStep) * axisStep;
            }

            int ticks = Math.max(1, Math.min(5, (int) Math.ceil((axisMaximum - axisMinimum) / axisStep)));
            float labelWidth = 0f;
            for (int i = 0; i <= ticks; i++) {
                double value = axisMinimum + i * axisStep;
                labelWidth = Math.max(labelWidth,
                        textPaint.measureText(HomeNumberFormatter.chartMoney(value)));
            }
            plotLeft = Math.min(width * 0.42f,
                    Math.max(Theme.dp(44), labelWidth + Theme.dp(14)));
            plotRight = Math.max(plotLeft + Theme.dp(44), width - Theme.dp(10));
            plotTop = Theme.dp(24);
            plotBottom = Math.max(plotTop + Theme.dp(42), height - Theme.dp(34));
            spacing = points.size() <= 1 ? 0d : (plotRight - plotLeft) / (points.size() - 1);
            pointX = new float[points.size()];
            pointY = new float[points.size()];
            displayX = new float[points.size()];
            displayY = new float[points.size()];
            double[] xValues = new double[points.size()];
            double[] yValues = new double[points.size()];

            for (int displayIndex = 0; displayIndex < points.size(); displayIndex++) {
                int sourceIndex = points.size() - 1 - displayIndex;
                float x = (float) (plotLeft + spacing * displayIndex);
                float y = valueY(points.get(sourceIndex).valueRials);
                pointX[sourceIndex] = x;
                pointY[sourceIndex] = y;
                displayX[displayIndex] = x;
                displayY[displayIndex] = y;
                xValues[displayIndex] = x;
                yValues[displayIndex] = y;
            }
            buildPaths(xValues, yValues);
            plot.set((float) plotLeft, (float) plotTop, (float) plotRight, (float) plotBottom);
            axisPaint.setStrokeWidth(Theme.dp(0.8f));
            lineShadowPaint.setStrokeWidth(Theme.dp(5f));
            linePaint.setStrokeWidth(Theme.dp(2.6f));
            highlightPaint.setStrokeWidth(Theme.dp(0.85f));
        }

        private void buildPaths(double[] x, double[] y) {
            linePath.reset();
            areaPath.reset();
            int n = x.length;
            if (n == 0) return;
            linePath.moveTo((float) x[0], (float) y[0]);
            areaPath.moveTo((float) x[0], (float) plotBottom);
            areaPath.lineTo((float) x[0], (float) y[0]);
            if (n > 1) {
                double[] tangents = MonotoneCubic.tangents(x, y);
                for (int i = 0; i < n - 1; i++) {
                    float x0 = (float) x[i];
                    float x1 = (float) x[i + 1];
                    float y0 = (float) y[i];
                    float y1 = (float) y[i + 1];
                    float dx = x1 - x0;
                    float c1y = y0 + (float) tangents[i] * dx / 3f;
                    float c2y = y1 - (float) tangents[i + 1] * dx / 3f;
                    linePath.cubicTo(x0 + dx / 3f, c1y, x1 - dx / 3f, c2y, x1, y1);
                    areaPath.cubicTo(x0 + dx / 3f, c1y, x1 - dx / 3f, c2y, x1, y1);
                }
            }
            areaPath.lineTo((float) x[n - 1], (float) plotBottom);
            areaPath.close();
        }

        private float valueY(double value) {
            double safe = finite(value) ? Math.max(axisMinimum, Math.min(axisMaximum, value)) : axisMinimum;
            double range = Math.max(1d, axisMaximum - axisMinimum);
            return (float) (plotBottom - ((safe - axisMinimum) / range) * (plotBottom - plotTop));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            ensureGeometry();
            if (!available || points.isEmpty()) {
                drawUnavailable(canvas);
                return;
            }
            drawAxes(canvas);
            drawArea(canvas);
            drawMarkers(canvas);
            drawDateLabels(canvas);
            if (touched && selectedIndex >= 0) drawTooltip(canvas);
        }

        private void drawUnavailable(Canvas canvas) {
            float inset = Theme.dp(5);
            RectF frame = new RectF(inset, Theme.dp(10), getWidth() - inset,
                    Math.max(Theme.dp(56), getHeight() - Theme.dp(7)));
            areaPaint.setShader(null);
            areaPaint.setColor(Theme.alpha(accent, Theme.isLight() ? 12 : 22));
            canvas.drawRoundRect(frame, Theme.dp(14), Theme.dp(14), areaPaint);
            axisPaint.setColor(Theme.alpha(accent, Theme.isLight() ? 65 : 85));
            axisPaint.setStrokeWidth(Theme.dp(1));
            canvas.drawRoundRect(frame, Theme.dp(14), Theme.dp(14), axisPaint);
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextSize(sp(10.5f));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Theme.face(false));
            String message = Charts.fitText(HomeNumberFormatter.localizeText(unavailableMessage),
                    textPaint, frame.width() - Theme.dp(28));
            canvas.drawText(message, frame.centerX(), frame.centerY() + textPaint.getTextSize() * 0.36f,
                    textPaint);
        }

        private void drawAxes(Canvas canvas) {
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextSize(sp(8.25f));
            textPaint.setTypeface(Theme.face(false));
            textPaint.setTextAlign(Paint.Align.RIGHT);
            int ticks = Math.max(1, Math.min(5,
                    (int) Math.ceil((axisMaximum - axisMinimum) / axisStep)));
            for (int i = 0; i <= ticks; i++) {
                double value = Math.min(axisMaximum, axisMinimum + i * axisStep);
                float y = valueY(value);
                axisPaint.setPathEffect(null);
                axisPaint.setColor(Theme.alpha(Theme.MUTED, i == 0 ? 78 : 36));
                canvas.drawLine((float) plotLeft, y, (float) plotRight, y, axisPaint);
                canvas.drawText(HomeNumberFormatter.chartMoney(value), (float) plotLeft - Theme.dp(7),
                        y + textPaint.getTextSize() * 0.34f, textPaint);
            }
            textPaint.setTextSize(sp(8f));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("جدیدتر", (float) plotLeft, Theme.dp(11) + textPaint.getTextSize(), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("قدیمی‌تر", (float) plotRight, Theme.dp(11) + textPaint.getTextSize(), textPaint);
        }

        /** A translucent extruded underside plus a gradient surface gives the line its 3D depth. */
        private void drawArea(Canvas canvas) {
            if (points.isEmpty()) return;
            canvas.save();
            canvas.clipRect((float) plotLeft, (float) plotTop,
                    (float) plotRight + Theme.dp(5), (float) plotBottom + Theme.dp(7));
            canvas.translate(Theme.dp(4), Theme.dp(5));
            extrusionPaint.setShader(null);
            extrusionPaint.setColor(Theme.alpha(DesignTokens.mix(accent, Color.BLACK, 0.42f),
                    Theme.isLight() ? 90 : 135));
            canvas.drawPath(areaPath, extrusionPaint);
            canvas.restore();

            int top = Theme.alpha(accent, Theme.isLight() ? 72 : 88);
            int bottom = Theme.alpha(accent, Theme.isLight() ? 3 : 8);
            areaPaint.setShader(new LinearGradient(0f, (float) plotTop, 0f, (float) plotBottom,
                    top, bottom, Shader.TileMode.CLAMP));
            canvas.drawPath(areaPath, areaPaint);
            areaPaint.setShader(null);

            lineShadowPaint.setShader(null);
            lineShadowPaint.setColor(Theme.alpha(DesignTokens.mix(accent, Color.BLACK, 0.26f),
                    Theme.isLight() ? 105 : 155));
            canvas.drawPath(linePath, lineShadowPaint);
            linePaint.setShader(new LinearGradient((float) plotLeft, (float) plotTop,
                    (float) plotRight, (float) plotBottom,
                    DesignTokens.mix(accent, Color.WHITE, Theme.isLight() ? 0.12f : 0.24f),
                    accent, Shader.TileMode.CLAMP));
            canvas.drawPath(linePath, linePaint);
            linePaint.setShader(null);
            highlightPaint.setShader(null);
            highlightPaint.setColor(Theme.alpha(Theme.SURFACE, Theme.isLight() ? 155 : 110));
            canvas.drawPath(linePath, highlightPaint);
        }

        private void drawMarkers(Canvas canvas) {
            if (points.isEmpty()) return;
            int step = Math.max(1, points.size() / 10);
            for (int i = 0; i < points.size(); i++) {
                boolean selected = touched && i == selectedIndex;
                boolean latest = i == points.size() - 1;
                if (!selected && !latest && i % step != 0) continue;
                float x = pointX[i];
                float y = pointY[i];
                markerPaint.setStyle(Paint.Style.FILL);
                markerPaint.setColor(Theme.alpha(DesignTokens.mix(accent, Color.BLACK, 0.28f),
                        Theme.isLight() ? 90 : 130));
                canvas.drawCircle(x + Theme.dp(1), y + Theme.dp(2), Theme.dp(selected ? 5.5f : 3.5f), markerPaint);
                markerPaint.setColor(Theme.BG);
                canvas.drawCircle(x, y, Theme.dp(selected ? 5.2f : 3.3f), markerPaint);
                markerPaint.setStyle(Paint.Style.STROKE);
                markerPaint.setStrokeWidth(Theme.dp(selected ? 2f : 1.35f));
                markerPaint.setColor(accent);
                canvas.drawCircle(x, y, Theme.dp(selected ? 5.2f : 3.3f), markerPaint);
                markerPaint.setStyle(Paint.Style.FILL);
                markerPaint.setColor(accent);
                canvas.drawCircle(x, y, Theme.dp(selected ? 2.0f : 1.45f), markerPaint);
            }
            if (touched && selectedIndex >= 0 && selectedIndex < points.size()) {
                float x = pointX[selectedIndex];
                guidePath.reset();
                guidePath.moveTo(x, (float) plotTop);
                guidePath.lineTo(x, (float) plotBottom);
                axisPaint.setColor(Theme.alpha(Theme.MUTED, 140));
                axisPaint.setStrokeWidth(Theme.dp(0.9f));
                axisPaint.setPathEffect(new android.graphics.DashPathEffect(
                        new float[]{Theme.dp(3), Theme.dp(4)}, 0));
                canvas.drawPath(guidePath, axisPaint);
                axisPaint.setPathEffect(null);
            }
        }

        private void drawDateLabels(Canvas canvas) {
            if (points.isEmpty()) return;
            textPaint.setTypeface(Theme.face(false));
            textPaint.setTextSize(sp(8f));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.CENTER);
            int n = points.size();
            int[] ticks = {0, Math.max(0, (n - 1) / 4), Math.max(0, (n - 1) / 2),
                    Math.max(0, (3 * (n - 1)) / 4), n - 1};
            int previous = -1;
            for (int displayIndex : ticks) {
                if (displayIndex == previous) continue;
                previous = displayIndex;
                int sourceIndex = n - 1 - displayIndex;
                String label = HomeNumberFormatter.localizeText(Jalali.shortLabel(points.get(sourceIndex).date));
                canvas.drawText(label, displayX[displayIndex], getHeight() - Theme.dp(5), textPaint);
            }
        }

        private void drawTooltip(Canvas canvas) {
            Point point = selectedPoint();
            if (point == null) return;
            float x = pointX[selectedIndex];
            float y = pointY[selectedIndex];
            float availableWidth = Math.max(Theme.dp(1), getWidth() - Theme.dp(12));
            float width = Math.min(Theme.dp(202), availableWidth);
            float height = Theme.dp(48);
            float left = Math.max(0f, Math.min(Math.max(0f, getWidth() - width), x - width / 2f));
            float top = y - height - Theme.dp(9);
            if (top < plotTop) top = Math.min((float) plotBottom - height, y + Theme.dp(9));
            bubble.set(left, Math.max((float) plotTop, top), left + width,
                    Math.max((float) plotTop, top) + height);
            bubblePaint.setShader(null);
            bubblePaint.setColor(Theme.alpha(Theme.SURFACE, Theme.isLight() ? 250 : 244));
            canvas.drawRoundRect(bubble, Theme.dp(12), Theme.dp(12), bubblePaint);
            bubblePaint.setStyle(Paint.Style.STROKE);
            bubblePaint.setStrokeWidth(Theme.dp(1f));
            bubblePaint.setColor(Theme.alpha(accent, Theme.isLight() ? 120 : 160));
            canvas.drawRoundRect(bubble, Theme.dp(12), Theme.dp(12), bubblePaint);
            bubblePaint.setStyle(Paint.Style.FILL);

            String value = HomeNumberFormatter.money(point.valueRials);
            String primary = Jalali.dispFa(point.date) + "  ·  " + value;
            bubbleText.setTextSize(sp(9.25f));
            bubbleText.setColor(Theme.TEXT);
            canvas.drawText(Charts.fitText(primary, bubbleText, width - Theme.dp(16)),
                    bubble.centerX(), bubble.top + Theme.dp(18), bubbleText);
            bubbleText.setTextSize(sp(8.5f));
            bubbleText.setColor(Theme.MUTED);
            canvas.drawText(HomeNumberFormatter.integer(point.documents) + " " + documentNoun()
                            + " · " + seriesLabel,
                    bubble.centerX(), bubble.top + Theme.dp(37), bubbleText);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!available || points.isEmpty()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    scrubbing = false;
                    touched = true;
                    selectFromX(event.getX());
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (!scrubbing && dx > touchSlop && dx > dy) {
                        scrubbing = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (!scrubbing && dy > touchSlop && dy >= dx) {
                        // Let the surrounding ScrollView take vertical gestures; only horizontal
                        // movement is reserved for chart scrubbing.
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (scrubbing) selectFromX(event.getX());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    selectFromX(event.getX());
                    touched = true;
                    performClick();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    scrubbing = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        private void selectFromX(float x) {
            if (points.isEmpty()) return;
            int displayIndex = spacing <= 0d ? 0
                    : Math.round((float) ((Math.max(plotLeft, Math.min(plotRight, x)) - plotLeft) / spacing));
            int sourceIndex = points.size() - 1 - displayIndex;
            select(Math.max(0, Math.min(points.size() - 1, sourceIndex)), true);
        }

        private void select(int index, boolean haptic) {
            if (index < 0 || index >= points.size()) return;
            boolean changed = selectedIndex != index;
            selectedIndex = index;
            if (changed && haptic) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (selectionListener != null) selectionListener.onSelect(points.get(index));
            }
            setContentDescription(description());
            invalidate();
        }

        @Override public boolean performClick() {
            super.performClick();
            Point point = selectedPoint();
            if (point != null) {
                announceForAccessibility(Jalali.dispFa(point.date) + "، "
                        + HomeNumberFormatter.money(point.valueRials) + "، "
                        + HomeNumberFormatter.integer(point.documents) + " " + documentNoun());
            }
            return true;
        }

        private static double niceStep(double raw) {
            if (!(raw > 0d) || !finite(raw)) return 1d;
            double power = Math.pow(10d, Math.floor(Math.log10(raw)));
            double fraction = raw / power;
            double unit = fraction <= 1d ? 1d : fraction <= 2d ? 2d : fraction <= 5d ? 5d : 10d;
            return unit * power;
        }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    /** Avoid a dependency cycle: chart copy uses the app's existing selected money unit. */
    private static final class MoneyUnit {
        static String name() {
            return ir.meelano.manager.core.Money.unitName();
        }
    }
}
