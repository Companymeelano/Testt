package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;


import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.LuxChartPlan;

/**
 * C12 LuxChart: gradient area under a fully-rounded 2.5px line with a colored drop shadow,
 * three dashed gridlines, a golden dashed 7-day moving average, three point states
 * (plain / touched with ring / glowing peak with a permanent label), a glass tooltip with
 * the weekday-average delta, and a broken axis with an explicit «قله خارج از قاب» badge
 * when a single outlier would flatten the whole series.
 */
public class LuxChartView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ma = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final DerakhshanTokens.Surface s;
    private double[] values = new double[0];
    private String[] labels = new String[0];
    private LuxChartPlan.Plan plan;
    private int peakIndex = -1;
    private int touched = -1;

    public LuxChartView(Context c, DerakhshanTokens.Surface s) {
        super(c);
        this.s = s;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Theme.dp(DerakhshanTokens.CHART_LINE_DP));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(s.dark ? DerakhshanTokens.EMERALD_300 : DerakhshanTokens.EMERALD_500);
        line.setShadowLayer(Theme.dp(6), 0, Theme.dp(2),
                DerakhshanTokens.blend(line.getColor(), 0x00000000, 0.45f));
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1);
        grid.setColor(DerakhshanTokens.blend(s.ink, s.stone[0], 1f - DerakhshanTokens.CHART_GRID_OPACITY));
        grid.setPathEffect(new DashPathEffect(new float[]{Theme.dp(4), Theme.dp(6)}, 0));
        ma.setStyle(Paint.Style.STROKE);
        ma.setStrokeWidth(Theme.dp(1.5f));
        ma.setColor(s.goldText);
        ma.setPathEffect(new DashPathEffect(new float[]{Theme.dp(6), Theme.dp(5)}, 0));
        point.setColor(s.dark ? DerakhshanTokens.EMERALD_300 : DerakhshanTokens.EMERALD_700);
        glow.setColor(DerakhshanTokens.GOLD_300);
        glow.setShadowLayer(Theme.dp(8), 0, 0, DerakhshanTokens.GOLD_500);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Theme.dp(2));
        ring.setColor(DerakhshanTokens.blend(s.dark ? DerakhshanTokens.EMERALD_300
                : DerakhshanTokens.EMERALD_500, 0xFFFFFFFF, 0.35f));
        bubbleStroke.setStyle(Paint.Style.STROKE);
        bubbleStroke.setStrokeWidth(Theme.dp(1));
        bubbleStroke.setColor(s.glassBorder);
        text.setColor(s.ink);
        text.setTextSize(Theme.dp(11));
        text.setTypeface(Theme.face(true));
        subText.setColor(s.inkSecondary);
        subText.setTextSize(Theme.dp(9.5f));
        subText.setTypeface(Theme.face(false));
        setMinimumHeight(Theme.dp(180));
    }

    public void setData(double[] values, String[] labels) {
        this.values = values == null ? new double[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        this.plan = LuxChartPlan.plan(this.values, DerakhshanTokens.CHART_MA_WINDOW_DAYS);
        this.peakIndex = -1;
        double best = -1d;
        for (int i = 0; i < this.values.length; i++) {
            if (Math.abs(this.values[i]) > best) {
                best = Math.abs(this.values[i]);
                this.peakIndex = i;
            }
        }
        this.touched = -1;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (values.length < 2) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touched = nearestIndex(event.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touched = -1;
                invalidate();
                return true;
            default:
                return false;
        }
    }

    private int nearestIndex(float x) {
        int w = getWidth();
        float pad = Theme.dp(10);
        if (w <= pad * 2 || values.length < 2) return 0;
        float t = (x - pad) / (w - pad * 2);
        int i = Math.round(t * (values.length - 1));
        return Math.max(0, Math.min(values.length - 1, i));
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || values.length < 2 || plan == null) return;
        float pad = Theme.dp(10);
        float top = pad + Theme.dp(14);
        float bottom = h - pad - Theme.dp(16);
        // three dashed horizontal gridlines
        for (int i = 1; i <= DerakhshanTokens.CHART_GRID_LINES; i++) {
            float y = bottom - (bottom - top) * (i / (float) (DerakhshanTokens.CHART_GRID_LINES + 1));
            canvas.drawLine(pad, y, w - pad, y, grid);
        }
        float[] xs = new float[values.length];
        float[] ys = new float[values.length];
        double axis = plan.axisMax;
        for (int i = 0; i < values.length; i++) {
            xs[i] = pad + (w - pad * 2) * (i / (float) (values.length - 1));
            double v = Math.min(Math.abs(values[i]), axis);
            ys[i] = bottom - (float) (v / axis) * (bottom - top);
        }
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < values.length; i++) path.lineTo(xs[i], ys[i]);
        area.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{DerakhshanTokens.blend(s.stone[0], line.getColor(), s.dark ? 0.30f : 0.22f),
                        DerakhshanTokens.blend(s.stone[0], line.getColor(), 0.02f)},
                null, Shader.TileMode.CLAMP));
        Path fill = new Path(path);
        fill.lineTo(xs[values.length - 1], bottom);
        fill.lineTo(xs[0], bottom);
        fill.close();
        canvas.drawPath(fill, area);
        canvas.drawPath(path, line);
        // golden dashed 7-day moving average
        if (plan.movingAverage != null && plan.movingAverage.length == values.length) {
            Path maPath = new Path();
            boolean started = false;
            for (int i = 0; i < values.length; i++) {
                double m = plan.movingAverage[i];
                if (Double.isNaN(m)) continue;
                float y = bottom - (float) (Math.min(m, axis) / axis) * (bottom - top);
                if (!started) {
                    maPath.moveTo(xs[i], y);
                    started = true;
                } else maPath.lineTo(xs[i], y);
            }
            if (started) canvas.drawPath(maPath, ma);
        }
        // points: plain / touched+ring / glowing peak with permanent label
        for (int i = 0; i < values.length; i++) {
            float r = Theme.dp(DerakhshanTokens.CHART_POINT_DP) / 2f;
            if (i == peakIndex) {
                canvas.drawCircle(xs[i], ys[i], Theme.dp(DerakhshanTokens.CHART_POINT_PEAK_DP) / 2f, glow);
                String peakLabel = labelOf(i);
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(peakLabel, xs[i], ys[i] - Theme.dp(10), text);
            } else if (i == touched) {
                canvas.drawCircle(xs[i], ys[i],
                        Theme.dp(DerakhshanTokens.CHART_POINT_HOVER_DP) / 2f, point);
                canvas.drawCircle(xs[i], ys[i],
                        Theme.dp(DerakhshanTokens.CHART_POINT_HOVER_RING_DP) / 2f, ring);
            } else {
                canvas.drawCircle(xs[i], ys[i], r, point);
            }
        }
        // broken axis: zigzag + glass badge beside the chart, never over the title
        if (plan.broken) {
            float bx = w - pad - Theme.dp(96);
            float by = top - Theme.dp(4);
            RectF badge = new RectF(bx, by, w - pad, by + Theme.dp(20));
            bubble.setColor(s.glassFill);
            canvas.drawRoundRect(badge, Theme.dp(8), Theme.dp(8), bubble);
            canvas.drawRoundRect(badge, Theme.dp(8), Theme.dp(8), bubbleStroke);
            subText.setTextAlign(Paint.Align.CENTER);
            subText.setColor(s.goldText);
            canvas.drawText("قله خارج از قاب", badge.centerX(), badge.centerY() + Theme.dp(3.5f), subText);
            Paint brk = new Paint(Paint.ANTI_ALIAS_FLAG);
            brk.setStyle(Paint.Style.STROKE);
            brk.setStrokeWidth(Theme.dp(1.5f));
            brk.setColor(s.inkSecondary);
            float zy = top + Theme.dp(6);
            Path zig = new Path();
            zig.moveTo(pad, zy + Theme.dp(3));
            zig.lineTo(pad + Theme.dp(4), zy - Theme.dp(3));
            zig.lineTo(pad + Theme.dp(8), zy + Theme.dp(3));
            zig.lineTo(pad + Theme.dp(12), zy - Theme.dp(3));
            canvas.drawPath(zig, brk);
        }
        // glass tooltip for the touched point
        if (touched >= 0 && touched < values.length) {
            double base = LuxChartPlan.weekdayAverage(values, touched);
            String pct = "";
            if (!Double.isNaN(base) && base > 0d) {
                double delta = (values[touched] - base) * 100d / base;
                pct = " • " + HomeNumberFormatter.changeValue(values[touched], base, delta);
            }
            String first = labelOf(touched);
            String second = HomeNumberFormatter.compactMoney(values[touched]) + pct;
            float tw = Math.max(text.measureText(first), subText.measureText(second)) + Theme.dp(20);
            float th = Theme.dp(38);
            float left = Math.max(pad, Math.min(w - pad - tw, xs[touched] - tw / 2f));
            float ttop = Math.max(top, ys[touched] - th - Theme.dp(12));
            RectF box = new RectF(left, ttop, left + tw, ttop + th);
            bubble.setColor(s.dark ? 0xE604120D : 0xF2FFFFFF);
            canvas.drawRoundRect(box, Theme.dp(12), Theme.dp(12), bubble);
            canvas.drawRoundRect(box, Theme.dp(12), Theme.dp(12), bubbleStroke);
            text.setTextAlign(Paint.Align.CENTER);
            subText.setTextAlign(Paint.Align.CENTER);
            subText.setColor(s.inkSecondary);
            canvas.drawText(first, box.centerX(), box.top + Theme.dp(15), text);
            canvas.drawText(second, box.centerX(), box.top + Theme.dp(29), subText);
        }
    }

    private String labelOf(int i) {
        if (labels != null && i < labels.length && labels[i] != null && !labels[i].isEmpty())
            return labels[i];
        return HomeNumberFormatter.integer(i + 1);
    }
}
