package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import ir.meelano.manager.core.CashWaterfallEngine;
import ir.meelano.manager.core.HomeNumberFormatter;

import java.util.Collections;
import java.util.List;

/** Native Canvas waterfall for the local till position; no chart library or Compose dependency. */
public final class HomeCashWaterfall extends View {
    private List<CashWaterfallEngine.Step> steps = Collections.emptyList();
    private String description = "نمودار آبشاری موجودی نقد";
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connector = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labels = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint values = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();

    public HomeCashWaterfall(Context context) {
        super(context);
        labels.setTypeface(Theme.face(false));
        labels.setTextSize(9.5f * getResources().getDisplayMetrics().scaledDensity);
        labels.setTextAlign(Paint.Align.CENTER);
        labels.setColor(Theme.MUTED);
        values.setTypeface(Theme.face(true));
        values.setTextSize(8.5f * getResources().getDisplayMetrics().scaledDensity);
        values.setTextAlign(Paint.Align.CENTER);
        values.setColor(Theme.TEXT);
        connector.setStyle(Paint.Style.STROKE);
        connector.setStrokeWidth(Theme.dp(1));
        connector.setColor(Theme.alpha(Theme.MUTED, 110));
        baseline.setStyle(Paint.Style.STROKE);
        baseline.setStrokeWidth(Theme.dp(1));
        baseline.setColor(Theme.alpha(Theme.MUTED, 95));
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setSteps(List<CashWaterfallEngine.Step> value) {
        steps = value == null ? Collections.emptyList() : value;
        description = describe(steps);
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (steps == null || steps.isEmpty()) {
            labels.setColor(Theme.MUTED);
            canvas.drawText("موجودی آغاز ثبت نشده است",
                    getWidth() / 2f, getHeight() / 2f, labels);
            return;
        }
        float width = getWidth();
        float height = getHeight();
        float pad = Theme.dp(16);
        float chartTop = Theme.dp(31);
        float labelSpace = Theme.dp(34);
        float chartBottom = Math.max(chartTop + Theme.dp(30), height - labelSpace);
        float chartHeight = chartBottom - chartTop;
        double min = 0, max = 0;
        for (CashWaterfallEngine.Step step : steps) {
            min = Math.min(min, step.lowRials);
            max = Math.max(max, step.highRials);
        }
        double span = max - min;
        if (!(span > 0)) span = Math.max(1d, Math.abs(max) * 0.1d);
        double extra = span * 0.10d;
        min -= extra;
        max += extra;
        final double rangeMin = min;
        final double range = Math.max(1d, max - min);
        float zeroY = yFor(0d, rangeMin, range, chartBottom, chartHeight);
        canvas.drawLine(pad, zeroY, width - pad, zeroY, baseline);

        int count = steps.size();
        float barWidth = Math.min(Theme.dp(50), (width - 2 * pad) / (count * 2.4f));
        float gap = (width - 2 * pad - count * barWidth) / Math.max(1f, count - 1f);
        float previousX = -1;
        double previousEnd = 0;
        for (int i = 0; i < count; i++) {
            CashWaterfallEngine.Step step = steps.get(i);
            // The first/opening step appears at the right and later movements progress leftward in RTL.
            float x = width - pad - barWidth - i * (barWidth + gap);
            if (i > 0) {
                float connectorY = yFor(previousEnd, rangeMin, range, chartBottom, chartHeight);
                canvas.drawLine(previousX, connectorY, x + barWidth, connectorY, connector);
            }
            float top = yFor(step.highRials, rangeMin, range, chartBottom, chartHeight);
            float bottom = yFor(step.lowRials, rangeMin, range, chartBottom, chartHeight);
            if (bottom - top < Theme.dp(2)) bottom = top + Theme.dp(2);
            rect.set(x, top, x + barWidth, bottom);
            int color = i == 1 ? Theme.SECTION_RECEIVE
                    : i == 2 ? Theme.SECTION_PAY : Theme.SECTION_CLOSEOUT;
            bars.setColor(color);
            canvas.drawRoundRect(rect, Theme.dp(5), Theme.dp(5), bars);

            double shown = step.total ? step.endRials : step.deltaRials();
            String amount = HomeNumberFormatter.chartMoney(Math.abs(shown));
            float amountY = Math.max(11f * getResources().getDisplayMetrics().scaledDensity,
                    top - Theme.dp(6));
            canvas.drawText(amount, x + barWidth / 2f, amountY, values);
            String shortLabel = i == 0 ? "آغاز" : i == 1 ? "ورودی" : i == 2 ? "خروجی" : "پایان";
            canvas.drawText(shortLabel, x + barWidth / 2f, height - Theme.dp(8), labels);
            previousX = x;
            previousEnd = step.endRials;
        }
    }

    private static float yFor(double value, double rangeMin, double range,
                              float chartBottom, float chartHeight) {
        return chartBottom - (float) ((value - rangeMin) / range) * chartHeight;
    }

    private static String describe(List<CashWaterfallEngine.Step> values) {
        if (values == null || values.isEmpty())
            return "نمودار آبشاری موجودی نقد؛ ماندهٔ آغاز روز ثبت نشده است و ماندهٔ پایان از گردش به‌تنهایی استنباط نمی‌شود.";
        StringBuilder result = new StringBuilder("نمودار آبشاری نقد به ترتیب زمانی: ");
        for (int i = 0; i < values.size(); i++) {
            CashWaterfallEngine.Step step = values.get(i);
            if (i > 0) result.append("؛ ");
            result.append(step.label).append(' ')
                    .append(HomeNumberFormatter.money(step.total ? step.endRials : step.deltaRials()));
        }
        result.append(". ماندهٔ پایان، مقدار مورد انتظار از ماندهٔ آغاز و جریان نقد است؛ موجودی بانک را نشان نمی‌دهد.");
        return result.toString();
    }
}
