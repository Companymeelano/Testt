package ir.meelano.admin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * One icon language for the seller app.
 *
 * Labels here were decorated with Unicode glyphs and emoji (✓, 📥, ⚠ …) whose look depended on the
 * phone's fonts. Instead of rewriting every call site, this class overlays each known glyph with a
 * vector icon from the shared Material Symbols set in {@code res/drawable/mi_*.xml}. The glyph
 * characters stay in the text, so {@code getText().toString()} keeps working for code that reads
 * labels back; only the rendering changes. Icons take the colour and size of their TextView.
 */
public final class AdminIcons {

    private static final float SCALE = 1.22f;
    private static final Map<String, Integer> MAP = new HashMap<>();

    static {
        put("✓", R.drawable.mi_check);
        put("✦", R.drawable.mi_star_shine);
        put("📥", R.drawable.mi_download);
        put("⚠", R.drawable.mi_warning);
        put("＋", R.drawable.mi_add);
        put("→", R.drawable.mi_arrow_forward);
        put("📊", R.drawable.mi_bar_chart);
        put("✕", R.drawable.mi_close);
        put("⛔", R.drawable.mi_block);
        put("↻", R.drawable.mi_refresh);
        put("🔄", R.drawable.mi_sync);
        put("♾", R.drawable.mi_verified_user);
        put("📵", R.drawable.mi_wifi_off);
        put("🔓", R.drawable.mi_lock);
        put("⟵", R.drawable.mi_undo);
        put("‹", R.drawable.mi_chevron_left);
    }

    private AdminIcons() {
    }

    private static void put(String glyph, int res) {
        MAP.put(glyph, res);
    }

    /** Vector drawable for a glyph, or 0 when it is unknown. */
    public static int iconFor(String glyph) {
        if (glyph == null) return 0;
        Integer r = MAP.get(glyph);
        return r == null ? 0 : r;
    }

    private static boolean hasGlyph(CharSequence text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = Character.codePointAt(text, i);
            int n = Character.charCount(cp);
            if (cp >= 0x2000 && MAP.containsKey(new String(Character.toChars(cp)))) return true;
            i += n;
        }
        return false;
    }

    /** Builds a copy of {@code text} in which every known glyph is drawn as a vector icon. */
    public static CharSequence iconize(Context ctx, CharSequence text) {
        if (ctx == null || !hasGlyph(text)) return text;
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        for (int i = 0; i < sb.length(); ) {
            int cp = Character.codePointAt(sb, i);
            int n = Character.charCount(cp);
            String g = new String(Character.toChars(cp));
            Integer res = MAP.get(g);
            if (res == null) {
                i += n;
                continue;
            }
            int end = i + n;
            // Swallow emoji presentation selectors so one icon covers the whole glyph.
            while (end < sb.length()) {
                char c = sb.charAt(end);
                if (c == '️' || c == '︎') {
                    end++;
                    continue;
                }
                break;
            }
            if (sb.getSpans(i, end, IconSpan.class).length == 0) {
                sb.setSpan(new IconSpan(ctx, res), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            i = end;
        }
        return sb;
    }

    /** Applies {@link #iconize} to one TextView (EditTexts are left alone so typing is never disturbed). */
    public static void iconize(TextView tv) {
        if (tv == null || tv instanceof EditText) return;
        CharSequence t = tv.getText();
        if (t == null || t.length() == 0) return;
        if (t instanceof Spanned && ((Spanned) t).getSpans(0, t.length(), IconSpan.class).length > 0) return;
        if (hasGlyph(t)) {
            try {
                tv.setText(iconize(tv.getContext(), t));
            } catch (Exception ignored) {
            }
        }
    }

    /** setText() that keeps the vector icons, for labels changed after creation. */
    public static void set(TextView tv, CharSequence text) {
        if (tv == null) return;
        try {
            tv.setText(text);
        } catch (Exception ignored) {
            return;
        }
        iconize(tv);
    }

    static final class IconSpan extends ReplacementSpan {
        private final Drawable icon;

        IconSpan(Context ctx, int res) {
            Drawable d = null;
            try {
                d = ctx.getResources().getDrawable(res, ctx.getTheme());
                if (d != null) d = d.mutate();
            } catch (Exception ignored) {
            }
            icon = d;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm != null) {
                Paint.FontMetricsInt p = paint.getFontMetricsInt();
                fm.ascent = p.ascent;
                fm.descent = p.descent;
                fm.top = p.top;
                fm.bottom = p.bottom;
                fm.leading = p.leading;
            }
            if (icon == null) return Math.round(paint.measureText(text, start, end));
            return Math.round(paint.getTextSize() * SCALE);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            if (icon == null) {
                canvas.drawText(text, start, end, x, y, paint);
                return;
            }
            int size = Math.round(paint.getTextSize() * SCALE);
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            int centerY = y + (fm.ascent + fm.descent) / 2;
            int t = centerY - size / 2;
            icon.setBounds(0, 0, size, size);
            try {
                icon.setTint(paint.getColor());
                icon.setAlpha(paint.getAlpha());
            } catch (Exception ignored) {
            }
            canvas.save();
            canvas.translate(x, t);
            icon.draw(canvas);
            canvas.restore();
        }
    }
}
