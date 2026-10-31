package ir.meelano.manager.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.MonotoneCubic;
import ir.meelano.manager.core.SalesComparison;
import ir.meelano.manager.core.SalesSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Canvas-only Home visualizations; no charting dependency or view-tree work per frame. */
public final class HomeSalesChart {
    private HomeSalesChart() { }

    /** Interactive 14-calendar-day area chart. Chronology is fixed: older days are on the right. */
    public static final class Area extends View {
        public interface SelectionListener { void onSelect(SalesSnapshot.Day day); }

        private final Paint linePaint = paint(Paint.Style.STROKE);
        private final Paint fillPaint = paint(Paint.Style.FILL);
        private final Paint gridPaint = paint(Paint.Style.STROKE);
        private final Paint textPaint = paint(Paint.Style.FILL);
        private final Paint markerPaint = paint(Paint.Style.FILL);
        private final Paint bubblePaint = paint(Paint.Style.FILL);
        private final Paint bubbleText = paint(Paint.Style.FILL);
        private final Path linePath = new Path();
        private final Path areaPath = new Path();
        private final Path guidePath = new Path();
        private final RectF bubble = new RectF();
        private final List<SalesSnapshot.Day> days = new ArrayList<>();
        private double[] xValues = new double[0];
        private double[] yValues = new double[0];
        private double[] tangents = new double[0];
        private float[] pointX = new float[0];
        private float[] pointY = new float[0];
        private float plotLeft, plotRight, plotTop, plotBottom, spacing;
        private double axisMaximum = 1d;
        private double axisStep = 1d;
        private double average;
        private float drawProgress = 1f;
        private float pulse = 0.5f;
        private int selectedIndex = -1;
        private int touchSlop;
        private float downX, downY;
        private boolean scrubbing;
        private boolean touched;
        private boolean geometryDirty = true;
        private SelectionListener selectionListener;
        private ValueAnimator drawAnimator;
        private ValueAnimator pulseAnimator;
        private final DashPathEffect dotted = new DashPathEffect(new float[]{Theme.dp(3), Theme.dp(4)}, 0);

        public Area(Context context) {
            super(context);
            textPaint.setTextSize(sp(9));
            bubbleText.setTextSize(sp(10));
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setTypeface(Theme.face(false));
            bubbleText.setTypeface(Theme.face(true));
            textPaint.setFontFeatureSettings("tnum");
            bubbleText.setFontFeatureSettings("tnum");
            try { touchSlop = ViewConfiguration.get(context).getScaledTouchSlop(); }
            catch (Exception ignored) { touchSlop = Theme.dp(8); }
            setFocusable(true);
            setClickable(true);
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

        public void setData(List<SalesSnapshot.Day> values, double averageRials) {
            days.clear();
            if (values != null) days.addAll(values);
            average = finite(averageRials) ? Math.max(0, averageRials) : 0;
            selectedIndex = days.isEmpty() ? -1 : days.size() - 1;
            touched = false;
            geometryDirty = true;
            String latest = days.isEmpty() ? "" : Jalali.dispFa(days.get(days.size() - 1).date);
            setContentDescription("نمودار فروش ۱۴ روزه. زمان از راست، روزهای قدیمی‌تر، به چپ، روزهای جدیدتر است. آخرین روز " + latest);
            invalidate();
            if (isAttachedToWindow()) startMotion();
        }

        public void setSelectionListener(SelectionListener listener) { selectionListener = listener; }

        /** Set the highlighted calendar day when a range chip is changed. */
        public void setSelectedDate(String date) {
            if (days.isEmpty()) return;
            String wanted = Jalali.disp(date);
            for (int i = 0; i < days.size(); i++) {
                if (wanted.equals(days.get(i).date)) {
                    touched = false;
                    select(i, false);
                    return;
                }
            }
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            geometryDirty = true;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startMotion();
        }

        @Override protected void onDetachedFromWindow() {
            cancel(drawAnimator);
            cancel(pulseAnimator);
            drawAnimator = pulseAnimator = null;
            super.onDetachedFromWindow();
        }

        private void startMotion() {
            cancel(drawAnimator);
            cancel(pulseAnimator);
            if (days.size() < 2 || !Ui.motionOk(getContext())) {
                drawProgress = 1f;
                pulse = 0.5f;
                invalidate();
                return;
            }
            drawProgress = 0f;
            drawAnimator = ValueAnimator.ofFloat(0f, 1f);
            drawAnimator.setDuration(600);
            drawAnimator.setInterpolator(new DecelerateInterpolator());
            drawAnimator.addUpdateListener(a -> {
                drawProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            drawAnimator.start();
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(1700);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.addUpdateListener(a -> {
                pulse = (float) a.getAnimatedValue();
                invalidate();
            });
            pulseAnimator.start();
        }

        private static void cancel(ValueAnimator animator) {
            if (animator != null) animator.cancel();
        }

        private void ensureGeometry() {
            if (!geometryDirty || getWidth() <= 0 || getHeight() <= 0) return;
            geometryDirty = false;
            float width = getWidth();
            float height = getHeight();
            float textSize = sp(9);
            textPaint.setTextSize(textSize);
            double maximum = 0;
            for (SalesSnapshot.Day day : days) maximum = Math.max(maximum, finite(day.salesRials) ? day.salesRials : 0);
            axisStep = niceStep(Math.max(1d, maximum / 5d));
            axisMaximum = Math.max(axisStep, Math.ceil(maximum / axisStep) * axisStep);
            int tickCount = Math.max(1, Math.min(6, (int) Math.ceil(axisMaximum / axisStep)));
            float labelWidth = 0;
            for (int i = 0; i <= tickCount; i++)
                labelWidth = Math.max(labelWidth, textPaint.measureText(HomeNumberFormatter.chartMoney(i * axisStep)));
            plotLeft = Math.min(width * 0.39f, Math.max(Theme.dp(42), labelWidth + Theme.dp(14)));
            plotRight = Math.max(plotLeft + Theme.dp(40), width - Theme.dp(8));
            plotTop = Theme.dp(26);
            plotBottom = Math.max(plotTop + Theme.dp(36), height - Theme.dp(34));
            int n = days.size();
            pointX = new float[n];
            pointY = new float[n];
            xValues = new double[n];
            yValues = new double[n];
            spacing = n <= 1 ? 0 : (plotRight - plotLeft) / (n - 1);
            for (int i = 0; i < n; i++) {
                // Graph order is newest→oldest left-to-right; source order stays oldest→newest.
                int source = n - 1 - i;
                pointX[i] = plotLeft + spacing * i;
                pointY[i] = valueY(days.get(source).salesRials);
                xValues[i] = pointX[i];
                yValues[i] = pointY[i];
            }
            tangents = n > 1 ? MonotoneCubic.tangents(xValues, yValues) : new double[n];
            linePath.reset();
            areaPath.reset();
            if (n > 0) {
                linePath.moveTo(pointX[0], pointY[0]);
                areaPath.moveTo(pointX[0], plotBottom);
                areaPath.lineTo(pointX[0], pointY[0]);
                for (int i = 0; i < n - 1; i++) {
                    float x0 = pointX[i], x1 = pointX[i + 1];
                    float y0 = pointY[i], y1 = pointY[i + 1];
                    float dx = x1 - x0;
                    float c1y = y0 + (float) tangents[i] * dx / 3f;
                    float c2y = y1 - (float) tangents[i + 1] * dx / 3f;
                    linePath.cubicTo(x0 + dx / 3f, c1y, x1 - dx / 3f, c2y, x1, y1);
                    areaPath.cubicTo(x0 + dx / 3f, c1y, x1 - dx / 3f, c2y, x1, y1);
                }
                areaPath.lineTo(pointX[n - 1], plotBottom);
                areaPath.close();
            }
            linePaint.setStrokeWidth(Theme.dp(2));
            linePaint.setShader(new LinearGradient(plotLeft, 0, plotRight, 0,
                    Theme.GOLD, Theme.SECTION_SALES, Shader.TileMode.CLAMP));
            fillPaint.setShader(new LinearGradient(0, plotTop, 0, plotBottom,
                    Theme.alpha(Theme.SECTION_SALES, Theme.isLight() ? 55 : 70),
                    Theme.alpha(Theme.SECTION_SALES, 0), Shader.TileMode.CLAMP));
            gridPaint.setColor(Theme.alpha(Theme.MUTED, Theme.isLight() ? 44 : 52));
            gridPaint.setStrokeWidth(Theme.dp(0.7f));
        }

        private float valueY(double value) {
            double safe = finite(value) ? Math.max(0, Math.min(axisMaximum, value)) : 0;
            return plotBottom - (float) (safe / axisMaximum) * (plotBottom - plotTop);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ensureGeometry();
            if (getWidth() <= 0 || getHeight() <= 0) return;
            drawAxes(canvas);
            if (days.isEmpty()) {
                textPaint.setColor(Theme.MUTED);
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("روند فروش در دسترس نیست", getWidth() / 2f, getHeight() / 2f, textPaint);
                return;
            }
            drawFridayBands(canvas);
            if (average > 0) {
                float avgY = valueY(average);
                gridPaint.setColor(Theme.alpha(Theme.GOLD, Theme.isLight() ? 170 : 195));
                gridPaint.setPathEffect(dotted);
                canvas.drawLine(plotLeft, avgY, plotRight, avgY, gridPaint);
                gridPaint.setPathEffect(null);
            }
            if (days.size() > 1) {
                int save = canvas.save();
                canvas.clipRect(plotLeft, plotTop, plotLeft + (plotRight - plotLeft) * drawProgress, plotBottom);
                fillPaint.setStyle(Paint.Style.FILL);
                canvas.drawPath(areaPath, fillPaint);
                linePaint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(linePath, linePaint);
                canvas.restoreToCount(save);
            } else {
                drawSinglePoint(canvas);
            }
            drawMarkers(canvas);
            drawXLabels(canvas);
            if (touched && selectedIndex >= 0) drawTooltip(canvas);
        }

        private void drawAxes(Canvas canvas) {
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextSize(sp(8.5f));
            textPaint.setTypeface(Theme.face(false));
            textPaint.setTextAlign(Paint.Align.RIGHT);
            int ticks = Math.max(1, Math.min(6, (int) Math.ceil(axisMaximum / axisStep)));
            for (int i = 0; i <= ticks; i++) {
                double value = Math.min(axisMaximum, i * axisStep);
                float y = valueY(value);
                gridPaint.setPathEffect(null);
                gridPaint.setColor(Theme.alpha(Theme.MUTED, i == 0 ? 80 : 38));
                canvas.drawLine(plotLeft, y, plotRight, y, gridPaint);
                canvas.drawText(HomeNumberFormatter.chartMoney(value), plotLeft - Theme.dp(7),
                        y + textPaint.getTextSize() * 0.34f, textPaint);
            }
            textPaint.setTextSize(sp(8));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("جدیدتر", plotLeft, Theme.dp(11) + textPaint.getTextSize(), textPaint);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("قدیمی‌تر", plotRight, Theme.dp(11) + textPaint.getTextSize(), textPaint);
        }

        private void drawFridayBands(Canvas canvas) {
            int n = days.size();
            if (n < 2 || spacing <= 0) return;
            float half = spacing * 0.43f;
            fillPaint.setShader(null);
            fillPaint.setColor(Theme.alpha(Theme.SECTION_SALES, Theme.isLight() ? 12 : 20));
            for (int i = 0; i < n; i++) {
                if (!SalesComparison.isWeeklyHoliday(days.get(i).date)) continue;
                float x = plotRight - spacing * i;
                canvas.drawRect(Math.max(plotLeft, x - half), plotTop,
                        Math.min(plotRight, x + half), plotBottom, fillPaint);
            }
            fillPaint.setShader(new LinearGradient(0, plotTop, 0, plotBottom,
                    Theme.alpha(Theme.SECTION_SALES, Theme.isLight() ? 55 : 70),
                    Theme.alpha(Theme.SECTION_SALES, 0), Shader.TileMode.CLAMP));
        }

        private void drawSinglePoint(Canvas canvas) {
            if (pointX.length == 0) return;
            markerPaint.setColor(Theme.SECTION_SALES);
            canvas.drawCircle(pointX[0], pointY[0], Theme.dp(3), markerPaint);
        }

        private void drawMarkers(Canvas canvas) {
            int latest = days.size() - 1;
            if (latest < 0) return;
            float todayX = plotLeft;
            float todayY = pointY[0];
            if (Jalali.todayStr().equals(days.get(latest).date)) {
                markerPaint.setColor(Theme.alpha(Theme.SECTION_SALES, Theme.isLight() ? 26 : 38));
                canvas.drawCircle(todayX, todayY, Theme.dp(7) + Theme.dp(5) * pulse, markerPaint);
                markerPaint.setStyle(Paint.Style.STROKE);
                markerPaint.setStrokeWidth(Theme.dp(1.5f));
                markerPaint.setColor(Theme.SECTION_SALES);
                canvas.drawCircle(todayX, todayY, Theme.dp(5), markerPaint);
                markerPaint.setStyle(Paint.Style.FILL);
                markerPaint.setColor(Theme.GOLD);
                canvas.drawCircle(todayX, todayY, Theme.dp(2.2f), markerPaint);
            }
            if (touched && selectedIndex >= 0) {
                float x = plotRight - spacing * selectedIndex;
                float y = pointY[days.size() - 1 - selectedIndex];
                guidePath.reset();
                guidePath.moveTo(x, plotTop);
                guidePath.lineTo(x, plotBottom);
                gridPaint.setColor(Theme.alpha(Theme.MUTED, 150));
                gridPaint.setPathEffect(dotted);
                canvas.drawPath(guidePath, gridPaint);
                gridPaint.setPathEffect(null);
                markerPaint.setStyle(Paint.Style.FILL);
                markerPaint.setColor(Theme.BG);
                canvas.drawCircle(x, y, Theme.dp(5), markerPaint);
                markerPaint.setStyle(Paint.Style.STROKE);
                markerPaint.setStrokeWidth(Theme.dp(2));
                markerPaint.setColor(Theme.SECTION_SALES);
                canvas.drawCircle(x, y, Theme.dp(5), markerPaint);
                markerPaint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawXLabels(Canvas canvas) {
            if (days.isEmpty()) return;
            textPaint.setTypeface(Theme.face(false));
            textPaint.setTextSize(sp(8));
            textPaint.setColor(Theme.MUTED);
            textPaint.setTextAlign(Paint.Align.CENTER);
            int[] ticks = {0, 3, 6, 10, days.size() - 1};
            int previous = -1;
            for (int tick : ticks) {
                int index = Math.max(0, Math.min(days.size() - 1, tick));
                if (index == previous) continue;
                previous = index;
                float x = plotRight - spacing * index;
                String label = HomeNumberFormatter.localizeText(Jalali.shortLabel(days.get(index).date));
                canvas.drawText(label, x, getHeight() - Theme.dp(5), textPaint);
            }
        }

        private void drawTooltip(Canvas canvas) {
            int index = Math.max(0, Math.min(days.size() - 1, selectedIndex));
            SalesSnapshot.Day day = days.get(index);
            float x = plotRight - spacing * index;
            float y = pointY[days.size() - 1 - index];
            float width = Math.min(Theme.dp(184), plotRight - plotLeft - Theme.dp(8));
            float height = Theme.dp(47);
            float left = Math.max(plotLeft + Theme.dp(2), Math.min(plotRight - width, x - width / 2f));
            float top = y - height - Theme.dp(9);
            if (top < plotTop) top = Math.min(plotBottom - height, y + Theme.dp(9));
            bubble.set(left, Math.max(plotTop, top), left + width, Math.max(plotTop, top) + height);
            bubblePaint.setColor(Theme.alpha(Theme.SURFACE, Theme.isLight() ? 248 : 242));
            canvas.drawRoundRect(bubble, Theme.dp(12), Theme.dp(12), bubblePaint);
            bubblePaint.setStyle(Paint.Style.STROKE);
            bubblePaint.setStrokeWidth(Theme.dp(1));
            bubblePaint.setColor(Theme.alpha(Theme.SECTION_SALES, Theme.isLight() ? 110 : 140));
            canvas.drawRoundRect(bubble, Theme.dp(12), Theme.dp(12), bubblePaint);
            bubblePaint.setStyle(Paint.Style.FILL);
            bubbleText.setColor(Theme.TEXT);
            bubbleText.setTextSize(sp(9));
            bubbleText.setTextAlign(Paint.Align.CENTER);
            String primary = Jalali.dispFa(day.date) + "  ·  " + HomeNumberFormatter.money(day.salesRials);
            canvas.drawText(Charts.fitText(primary, bubbleText, width - Theme.dp(16)),
                    bubble.centerX(), bubble.top + Theme.dp(18), bubbleText);
            bubbleText.setTextSize(sp(8.5f));
            bubbleText.setColor(Theme.MUTED);
            canvas.drawText(HomeNumberFormatter.integer(day.documents) + " فاکتور",
                    bubble.centerX(), bubble.top + Theme.dp(36), bubbleText);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (days.isEmpty()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    scrubbing = false;
                    selectFromX(event.getX());
                    touched = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - downX) > touchSlop
                            && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY)) {
                        scrubbing = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (scrubbing) selectFromX(event.getX());
                    return true;
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
            if (days.isEmpty()) return;
            int i = spacing <= 0 ? days.size() - 1
                    : Math.round((plotRight - Math.max(plotLeft, Math.min(plotRight, x))) / spacing);
            select(Math.max(0, Math.min(days.size() - 1, i)), true);
        }

        private void select(int index, boolean haptic) {
            if (index < 0 || index >= days.size()) return;
            boolean changed = selectedIndex != index;
            selectedIndex = index;
            if (haptic) touched = true;
            if (changed && haptic) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (selectionListener != null) selectionListener.onSelect(days.get(index));
            }
            invalidate();
        }

        @Override public boolean performClick() {
            super.performClick();
            if (selectedIndex >= 0 && selectedIndex < days.size()) {
                SalesSnapshot.Day day = days.get(selectedIndex);
                announceForAccessibility(Jalali.dispFa(day.date) + "، "
                        + HomeNumberFormatter.money(day.salesRials) + "، "
                        + HomeNumberFormatter.integer(day.documents) + " فاکتور");
            }
            return true;
        }

        private static double niceStep(double raw) {
            if (!(raw > 0) || Double.isNaN(raw) || Double.isInfinite(raw)) return 1;
            double power = Math.pow(10, Math.floor(Math.log10(raw)));
            double fraction = raw / power;
            double unit = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
            return unit * power;
        }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    /** Tiny no-axis sparkline for a KPI; reports its endpoints to accessibility services. */
    public static final class Sparkline extends View {
        private final Paint line = paint(Paint.Style.STROKE);
        private final Paint marker = paint(Paint.Style.FILL);
        private final Path path = new Path();
        private double[] values = new double[0];
        private int accent;

        public Sparkline(Context context) {
            super(context);
            line.setStrokeWidth(Theme.dp(1.6f));
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        public void setValues(double[] values, int accent) {
            this.values = values == null ? new double[0] : values.clone();
            this.accent = accent;
            setContentDescription("روند هفت‌روزه");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (values.length < 2 || getWidth() <= 0 || getHeight() <= 0) return;
            double min = values[0], max = values[0];
            for (double value : values) {
                if (Double.isNaN(value) || Double.isInfinite(value)) continue;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            float inset = Theme.dp(3);
            float span = Math.max(Theme.dp(5), getHeight() - inset * 2);
            float width = Math.max(Theme.dp(8), getWidth() - inset * 2);
            path.reset();
            float lastX = inset, lastY = inset + span / 2f;
            for (int i = 0; i < values.length; i++) {
                float x = inset + (values.length == 1 ? 0 : width * i / (values.length - 1));
                double ratio = max == min ? 0.5 : (values[i] - min) / (max - min);
                float y = inset + span - (float) ratio * span;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                lastX = x;
                lastY = y;
            }
            line.setShader(new LinearGradient(inset, 0, getWidth() - inset, 0,
                    Theme.GOLD, accent, Shader.TileMode.CLAMP));
            canvas.drawPath(path, line);
            line.setShader(null);
            marker.setColor(Theme.alpha(accent, Theme.isLight() ? 42 : 60));
            canvas.drawCircle(lastX, lastY, Theme.dp(4), marker);
            marker.setColor(accent);
            canvas.drawCircle(lastX, lastY, Theme.dp(2), marker);
        }
    }
}
