package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** Responsive semicircle layout for a role-filtered four-action Home speed dial. */
public final class HomeQuickActionArc extends FrameLayout {
    private final List<View> actions = new ArrayList<>();
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final int accent;
    private View mainAction;
    private boolean expanded;

    public HomeQuickActionArc(Context context, int accent) {
        super(context);
        this.accent = accent;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(Theme.dp(1));
        guide.setPathEffect(new DashPathEffect(new float[]{Theme.dp(3), Theme.dp(5)}, 0f));
    }

    public void addArcAction(View action) {
        if (action == null) return;
        actions.add(action);
        action.setVisibility(expanded ? View.VISIBLE : View.GONE);
        addView(action, new FrameLayout.LayoutParams(Theme.dp(72), -2));
    }

    public void setMainAction(View action) {
        if (mainAction != null) removeView(mainAction);
        mainAction = action;
        if (action != null) addView(action, new FrameLayout.LayoutParams(
                Theme.dp(58), Theme.dp(58)));
        requestLayout();
    }

    public void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        for (View action : actions) action.setVisibility(expanded ? View.VISIBLE : View.GONE);
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Theme.dp(320);
        int desiredHeight = Theme.dp(expanded ? 196 : 88);
        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
        int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        if (mainAction != null) measureChild(mainAction, childWidth, childHeight);
        for (View action : actions) if (action.getVisibility() != View.GONE)
            measureChild(action, childWidth, childHeight);
    }

    private float arcRadius(float width) {
        float edgeRoom = Math.max(0f, width / 2f - Theme.dp(36));
        float horizontalFit = edgeRoom / 0.9064f; // cos(205°), the widest endpoint of the arc.
        return Math.min(Theme.dp(112), Math.min(width * 0.43f, horizontalFit));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        float width = right - left;
        float height = bottom - top;
        float centerX = width / 2f;
        float centerY = height - Theme.dp(30);
        float radius = arcRadius(width);
        if (mainAction != null) {
            int childWidth = mainAction.getMeasuredWidth();
            int childHeight = mainAction.getMeasuredHeight();
            int x = Math.round(centerX - childWidth / 2f);
            int y = Math.round(centerY - childHeight / 2f);
            mainAction.layout(x, y, x + childWidth, y + childHeight);
        }
        int count = actions.size();
        for (int i = 0; i < count; i++) {
            View action = actions.get(i);
            if (action.getVisibility() == View.GONE) continue;
            double angle = Math.toRadians(count <= 1 ? 270d : 205d + 130d * i / (count - 1));
            float xCenter = centerX + (float) Math.cos(angle) * radius;
            float yCenter = centerY + (float) Math.sin(angle) * radius;
            int childWidth = action.getMeasuredWidth();
            int childHeight = action.getMeasuredHeight();
            int x = Math.round(xCenter - childWidth / 2f);
            int y = Math.round(yCenter - childHeight / 2f);
            action.layout(x, y, x + childWidth, y + childHeight);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!expanded || actions.isEmpty()) return;
        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height - Theme.dp(30);
        float radius = arcRadius(width);
        arc.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        guide.setColor(Theme.alpha(accent, Theme.isLight() ? 125 : 145));
        canvas.drawArc(arc, 205f, 130f, false, guide);
    }
}
