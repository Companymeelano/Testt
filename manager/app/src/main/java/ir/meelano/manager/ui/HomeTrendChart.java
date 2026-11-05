package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;

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

    /** Canvas-only, zero-based area chart; source order is oldest-to-newest, newest is left. */
    public static final class Area extends View {
        private final Paint axisPaint = paint(Paint.Style.STROKE);
        private final Paint textPaint = paint(Paint.Style.FILL);
        private final Paint areaPaint = paint(Paint.Style.FILL);
        private final Paint linePaint = paint(Paint.Style.STROKE);
        private final Paint markerPaint = paint(Paint.Style.FILL);
        private final Path areaPath = new Path();
        private final Path linePath = new Path();
        private final Path guidePath = new Path();
        private final Path peakPath = new Path();
        private final List<Point> points = new ArrayList<>();

        private float[] pointX = new float[0];
        private float[] pointY = new float[0];
        private float[] displayX = new float[0];
        private float[] displayY = new float[0];
        private double plotLeft, plotRight, plotTop, plotBottom, spacing;
        private double axisMinimum, axisMaximum = 1d, axisStep = 1d, axisScale = 1d;
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
            try { touchSlop = ViewConfiguration.get(context).getScaledTouchSlop(); }
            catch (Exception ignored) { touchSlop = Theme.dp(8); }
            setFocusable(true);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setMinimumHeight(Theme.dp(166));
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
            float textSize = sp(9.25f);
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
            // Monetary trends start at zero so the visual height does not exaggerate a small
            // day-to-day difference. Negative values, if a source genuinely has them, remain visible.
            double floor = Math.min(0d, minimum);
            double span = maximum - floor;
            if (!(span > 0d) || !finite(span)) span = Math.max(1d, Math.abs(maximum) * 0.2d);
            axisStep = niceStep(span / 4d);
            axisMinimum = Math.floor(floor / axisStep) * axisStep;
            axisMaximum = Math.ceil(maximum / axisStep) * axisStep;
            if (!(axisMaximum > axisMinimum)) axisMaximum = axisMinimum + axisStep;
            axisScale = HomeNumberFormatter.chartScale(axisMaximum);

            int ticks = Math.max(1, (int) Math.ceil((axisMaximum - axisMinimum) / axisStep));
            float labelWidth = 0f;
            for (int i = 0; i <= ticks; i++) {
                double value = Math.min(axisMaximum, axisMinimum + i * axisStep);
                labelWidth = Math.max(labelWidth,
                        textPaint.measureText(HomeNumberFormatter.chartTick(value, axisScale)));
            }
            plotLeft = Math.min(width * 0.42f,
                    Math.max(Theme.dp(42), labelWidth + Theme.dp(13)));
            // Keep the newest point and its date label away from the clipped right edge.
            plotRight = Math.max(plotLeft + Theme.dp(44), width - Theme.dp(31));
            plotTop = Theme.dp(31);
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
            axisPaint.setStrokeWidth(Theme.dp(0.8f));
            linePaint.setStrokeWidth(Theme.dp(2.2f));
        }

        private void buildPaths(double[] x, double[] y) {
            linePath.reset();
            areaPath.reset();
            int n = x.length;
            if (n == 0) return;
            linePath.moveTo((float) x[0], (float) y[0]);
            areaPath.moveTo((float) x[0], (float) plotBottom);
            areaPath.lineTo((float) x[0], (float) y[0]);
            for (int i = 1; i < n; i++) {
                linePath.lineTo((float) x[i], (float) y[i]);
                areaPath.lineTo((float) x[i], (float) y[i]);
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
            // The selected value is shown in the label above the chart by HomeScreen; keeping
            // overlays off the canvas leaves the axis and data points unobscured.
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
            textPaint.setTextSize(sp(9.25f));
            textPaint.setTypeface(Theme.face(false));
            textPaint.setTextAlign(Paint.Align.RIGHT);
            int ticks = Math.max(1, (int) Math.ceil((axisMaximum - axisMinimum) / axisStep));
            for (int i = 0; i <= ticks; i++) {
                double value = Math.min(axisMaximum, axisMinimum + i * axisStep);
                float y = valueY(value);
                axisPaint.setPathEffect(null);
                axisPaint.setColor(Theme.alpha(Theme.MUTED, Math.abs(value) < 0.000001d ? 78 : 36));
                canvas.drawLine((float) plotLeft, y, (float) plotRight, y, axisPaint);
                canvas.drawText(HomeNumberFormatter.chartTick(value, axisScale),
                        (float) plotLeft - Theme.dp(7), y + textPaint.getTextSize() * 0.34f, textPaint);
            }
            textPaint.setTextSize(sp(9f));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("جدیدتر", (float) plotLeft, Theme.dp(13) + textPaint.getTextSize(), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("قدیمی‌تر", (float) plotRight, Theme.dp(13) + textPaint.getTextSize(), textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("مقیاس: " + HomeNumberFormatter.chartScaleLabel(axisScale),
                    (float) ((plotLeft + plotRight) / 2d), Theme.dp(13) + textPaint.getTextSize(), textPaint);
        }

        /** A quiet, transparent fill and a single straight line keep the values easy to read. */
        private void drawArea(Canvas canvas) {
            if (points.isEmpty()) return;
            canvas.save();
            canvas.clipRect((float) plotLeft - Theme.dp(1), (float) plotTop,
                    (float) plotRight + Theme.dp(1), (float) plotBottom + Theme.dp(1));
            areaPaint.setShader(null);
            areaPaint.setColor(Theme.alpha(accent, Theme.isLight() ? 13 : 22));
            canvas.drawPath(areaPath, areaPaint);
            linePaint.setShader(null);
            linePaint.setColor(accent);
            linePaint.setStrokeWidth(Theme.dp(2.2f));
            canvas.drawPath(linePath, linePaint);
            canvas.restore();
        }

        private void drawMarkers(Canvas canvas) {
            if (points.isEmpty()) return;
            int peakIndex = 0;
            for (int i = 1; i < points.size(); i++) {
                if (points.get(i).valueRials > points.get(peakIndex).valueRials) peakIndex = i;
            }
            int step = Math.max(1, points.size() / 10);
            for (int i = 0; i < points.size(); i++) {
                boolean selected = touched && i == selectedIndex;
                boolean latest = i == points.size() - 1;
                boolean peak = i == peakIndex;
                if (!selected && !latest && !peak && i % step != 0) continue;
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
                if (peak) {
                    float r = Theme.dp(4.2f);
                    peakPath.reset();
                    peakPath.moveTo(x, y - r);
                    peakPath.lineTo(x + r, y);
                    peakPath.lineTo(x, y + r);
                    peakPath.lineTo(x - r, y);
                    peakPath.close();
                    markerPaint.setColor(Theme.BG);
                    canvas.drawCircle(x, y, r + Theme.dp(1.5f), markerPaint);
                    markerPaint.setColor(accent);
                    canvas.drawPath(peakPath, markerPaint);
                }
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
            textPaint.setTextSize(sp(9f));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.CENTER);
            int n = points.size();
            int interval = Math.max(1, (int) Math.ceil((n - 1) / 4d));
            int previous = -1;
            for (int displayIndex = 0; displayIndex < n; displayIndex += interval) {
                previous = drawDateLabel(canvas, displayIndex, previous);
            }
            if (n > 1 && previous != n - 1) drawDateLabel(canvas, n - 1, previous);
        }

        private int drawDateLabel(Canvas canvas, int displayIndex, int previous) {
            int n = points.size();
            if (displayIndex == previous || displayIndex < 0 || displayIndex >= n) return previous;
            int sourceIndex = n - 1 - displayIndex;
            String label = HomeNumberFormatter.dateShortLabel(points.get(sourceIndex).date);
            if (n > 1 && displayIndex == 0) {
                textPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(label, displayX[displayIndex] + Theme.dp(2), getHeight() - Theme.dp(5), textPaint);
            } else if (n > 1 && displayIndex == n - 1) {
                textPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(label, displayX[displayIndex] - Theme.dp(2), getHeight() - Theme.dp(5), textPaint);
            } else {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, displayX[displayIndex], getHeight() - Theme.dp(5), textPaint);
            }
            return displayIndex;
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
