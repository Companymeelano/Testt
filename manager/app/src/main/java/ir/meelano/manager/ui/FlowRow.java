package ir.meelano.manager.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Wrapping row of chips. Children that do not fit on a line move to the next line,
 * so every option stays visible at its natural size (unlike a horizontal scroller,
 * which clips options at the dialog edge). Honours RTL: the first child sits on the right.
 */
public class FlowRow extends ViewGroup {
    private final int gapH;
    private final int gapV;
    private int[] xs = new int[0];
    private int[] ys = new int[0];

    public FlowRow(Context c) {
        this(c, null);
    }

    public FlowRow(Context c, AttributeSet attrs) {
        super(c, attrs);
        gapH = Theme.dp(8);
        gapV = Theme.dp(8);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthSize = MeasureSpec.getSize(widthSpec);
        int avail = widthSize - getPaddingLeft() - getPaddingRight();
        int count = getChildCount();
        xs = new int[count];
        ys = new int[count];
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, MeasureSpec.makeMeasureSpec(Math.max(avail, 0), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (x > 0 && x + cw > avail) {
                x = 0;
                y += rowH + gapV;
                rowH = 0;
            }
            xs[i] = x;
            ys[i] = y;
            x += cw + gapH;
            rowH = Math.max(rowH, ch);
        }
        int height = y + rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(widthSize, widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int width = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int top = getPaddingTop() + ys[i] + lp.topMargin;
            int left;
            if (rtl) {
                left = width - getPaddingRight() - xs[i] - lp.rightMargin - cw;
            } else {
                left = getPaddingLeft() + xs[i] + lp.leftMargin;
            }
            child.layout(left, top, left + cw, top + ch);
        }
    }
}
