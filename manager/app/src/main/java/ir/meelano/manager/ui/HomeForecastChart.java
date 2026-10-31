package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.SalesForecastEngine;

import java.util.ArrayList;
import java.util.List;

/** Canvas-only seven-day forecast with an observed-spread uncertainty band. */
public final class HomeForecastChart extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint estimate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bandPath = new Path();
    private final Path linePath = new Path();
    private static final String[] WEEKDAY_INITIALS = {"ش", "ی", "د", "س", "چ", "پ", "ج"};
    private final List<SalesForecastEngine.DayForecast> days = new ArrayList<>();

    public HomeForecastChart(Context context) {
        super(context);
        text.setTypeface(Theme.face(false));
        text.setFontFeatureSettings("tnum");
        text.setTextAlign(Paint.Align.RIGHT);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Theme.dp(0.7f));
        estimate.setStyle(Paint.Style.STROKE);
        estimate.setStrokeCap(Paint.Cap.ROUND);
        estimate.setStrokeJoin(Paint.Join.ROUND);
        point.setStyle(Paint.Style.FILL);
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setMinimumHeight(Theme.dp(190));
    }

    public void setData(List<SalesForecastEngine.DayForecast> values, int sectionAccent) {
        days.clear();
        if (values != null) days.addAll(values);
        band.setColor(Theme.alpha(sectionAccent, Theme.isLight() ? 40 : 55));
        estimate.setColor(sectionAccent);
        estimate.setStrokeWidth(Theme.dp(2));
        point.setColor(accent);
        StringBuilder description = new StringBuilder("پیش‌بینی فروش هفت روز آینده؛ تاریخ‌ها از راست به چپ. نوار رنگی دامنهٔ نوسان تاریخی است.");
        for (SalesForecastEngine.DayForecast day : days) {
            description.append(" ").append(Jalali.weekday(day.date)).append(" ")
                    .append(Jalali.dispFa(day.date)).append("؛ برآورد ")
                    .append(HomeNumberFormatter.money(day.expectedRials)).append("؛ دامنهٔ ")
                    .append(HomeNumberFormatter.money(day.lowerRials)).append(" تا ")
                    .append(HomeNumberFormatter.money(day.upperRials)).append(".");
        }
        setContentDescription(description.toString());
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (days.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().scaledDensity;
        float scale = Theme.fontScale() * Theme.typeScale();
        text.setTextSize(8f * density * scale);
        float width = getWidth();
        float height = getHeight();
        Paint.FontMetrics metrics = text.getFontMetrics();
        float lineHeight = Math.max(Theme.dp(9), metrics.descent - metrics.ascent);
        float dateBaseline = height - Theme.dp(4) - metrics.descent;
        float weekdayBaseline = dateBaseline - lineHeight - Theme.dp(2);
        text.setTextAlign(Paint.Align.RIGHT);

        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (SalesForecastEngine.DayForecast day : days) {
            low = Math.min(low, day.lowerRials);
            high = Math.max(high, day.upperRials);
        }
        if (!finite(low) || !finite(high)) return;
        double range = high - low;
        if (!(range > 0d)) range = Math.max(1d, Math.abs(high) * 0.1d);
        double step = niceStep(range / 4d);
        double axisMin = Math.floor(low / step) * step;
        double axisMax = Math.ceil(high / step) * step;
        if (!(axisMax > axisMin)) {
            axisMin -= step;
            axisMax += step;
        }
        float widestTick = 0f;
        for (double value = axisMin; value <= axisMax + step * 0.25d; value += step)
            widestTick = Math.max(widestTick, text.measureText(HomeNumberFormatter.chartMoney(value)));
        float plotLeft = Math.min(width * 0.40f, Math.max(Theme.dp(42), widestTick + Theme.dp(9)));
        float plotRight = Math.max(plotLeft + Theme.dp(40), width - Theme.dp(8));
        float plotTop = Theme.dp(14);
        float plotBottom = Math.max(plotTop + Theme.dp(44),
                weekdayBaseline - lineHeight * 0.45f - Theme.dp(4));
        float plotHeight = plotBottom - plotTop;
        grid.setColor(Theme.alpha(Theme.MUTED, Theme.isLight() ? 45 : 58));
        pointHalo.setColor(Theme.SURFACE);
        text.setColor(Theme.MUTED);
        for (double value = axisMin; value <= axisMax + step * 0.25d; value += step) {
            float y = plotBottom - (float) ((value - axisMin) / (axisMax - axisMin)) * plotHeight;
            canvas.drawLine(plotLeft, y, plotRight, y, grid);
            canvas.drawText(HomeNumberFormatter.chartMoney(value), plotLeft - Theme.dp(5),
                    y - (metrics.ascent + metrics.descent) / 2f, text);
        }

        int count = days.size();
        float spacing = count <= 1 ? 0f : (plotRight - plotLeft) / (count - 1);
        bandPath.reset();
        linePath.reset();
        for (int i = 0; i < count; i++) {
            SalesForecastEngine.DayForecast day = days.get(i);
            float x = plotRight - spacing * i; // nearest future day is on the RTL right edge.
            float yHigh = y(day.upperRials, axisMin, axisMax, plotTop, plotBottom);
            if (i == 0) bandPath.moveTo(x, yHigh); else bandPath.lineTo(x, yHigh);
            float yEstimate = y(day.expectedRials, axisMin, axisMax, plotTop, plotBottom);
            if (i == 0) linePath.moveTo(x, yEstimate); else linePath.lineTo(x, yEstimate);
        }
        for (int i = count - 1; i >= 0; i--) {
            SalesForecastEngine.DayForecast day = days.get(i);
            float x = plotRight - spacing * i;
            bandPath.lineTo(x, y(day.lowerRials, axisMin, axisMax, plotTop, plotBottom));
        }
        bandPath.close();
        canvas.drawPath(bandPath, band);
        canvas.drawPath(linePath, estimate);
        for (int i = 0; i < count; i++) {
            SalesForecastEngine.DayForecast day = days.get(i);
            float x = plotRight - spacing * i;
            float centerY = y(day.expectedRials, axisMin, axisMax, plotTop, plotBottom);
            float radius = Theme.dp(3);
            canvas.drawCircle(x, centerY, radius + Theme.dp(1), pointHalo);
            canvas.drawCircle(x, centerY, radius, point);
            String date = Jalali.disp(day.date);
            String dayNumber = date.length() >= 10 ? HomeNumberFormatter.localizeText(date.substring(8, 10)) : "";
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Theme.MUTED);
            if (day.weekdayIndex >= 0 && day.weekdayIndex < WEEKDAY_INITIALS.length)
                canvas.drawText(WEEKDAY_INITIALS[day.weekdayIndex], x, weekdayBaseline, text);
            text.setColor(Theme.TEXT);
            canvas.drawText(dayNumber, x, dateBaseline, text);
        }
    }

    private static float y(double value, double min, double max, float top, float bottom) {
        return bottom - (float) ((value - min) / (max - min)) * (bottom - top);
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
