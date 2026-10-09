package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Finger-signature pad: draw, clear, export a tight bitmap for receipts. */
public class SignView extends View {
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean empty = true;

    public SignView(Context c) {
        super(c);
        init();
    }

    public SignView(Context c, AttributeSet attrs) {
        super(c, attrs);
        init();
    }

    private void init() {
        paint.setColor(0xFF101828);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(0xFFFFFFFF);
    }

    public boolean isEmpty() {
        return empty;
    }

    public void clear() {
        path.reset();
        empty = true;
        invalidate();
    }

    /** White-background bitmap of the signature, or null when empty. */
    public Bitmap bitmap() {
        if (empty) return null;
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFFFFFFF);
        c.drawPath(path, paint);
        return b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                empty = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }
}
