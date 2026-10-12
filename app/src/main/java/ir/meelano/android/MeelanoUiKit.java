package ir.meelano.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * کیت رابط کاربری نسل جدید «پخش درخشان» — یک زبان بصری واحد برای همهٔ بخش‌ها.
 *
 * <p>سه اصل طراحی که در این کیت پیاده شده است:</p>
 * <ol>
 *   <li><b>سلسله‌مراتب روشن:</b> هر کارت یک عنوان کوچک، یک عدد بزرگ و یک توضیح کوتاه دارد؛
 *       کاربر در نگاه اول می‌فهمد عدد مهم کدام است.</li>
 *   <li><b>آیکون معنادار به‌جای متن خالی:</b> هر عدد یک آیکون وکتور و یک رنگ معنایی دارد
 *       (سبز = سالم، نارنجی = هشدار، قرمز = خطر) تا صفحه در نگاه اول خوانده شود.</li>
 *   <li><b>حرکت محترمانه:</b> انیمیشن فقط جایی که معنا دارد (ورود کارت، فشردن دکمه) و در
 *       حالت «کاهش حرکت» کاربر کامل خاموش می‌شود.</li>
 * </ol>
 *
 * <p>همهٔ اجزا بدون وابستگی بیرونی ساخته شده‌اند (فقط Android SDK) و رنگ/فونت را از
 * {@link MeelanoPalette} و {@link MeelanoUiHost} می‌گیرند؛ پس در هر چهار نسخهٔ برنامه
 * (ویزیتور، مدیر، پرسنل، فروشگاه) و با هر ۱۴ تم، یکسان و خوانا هستند.</p>
 */
final class MeelanoUiKit {
    private MeelanoUiKit() { }

    // ================================================================ پایه‌ها
    /** رنگ شیشه‌ای/سطح کارت با گرادیان ملایم و حاشیهٔ مویی. */
    static GradientDrawable cardBg(MeelanoPalette p, int accent, float radius) {
        int soft = p.mix(accent, p.accent2, p.light ? 0.16f : 0.20f);
        int top = p.light ? p.mix(p.surface, soft, 0.035f) : p.mix(p.surface, soft, 0.10f);
        int bottom = p.light ? p.mix(p.surface2, soft, 0.07f) : p.mix(p.surface2, soft, 0.15f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(top, 253), p.alpha(bottom, 250)});
        d.setCornerRadius(radius);
        d.setStroke(1, p.alpha(p.mix(soft, Color.WHITE, p.light ? 0.35f : 0.22f), p.light ? 112 : 88));
        return d;
    }

    static GradientDrawable solid(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable stroke(int color, float radius, int strokeColor) {
        GradientDrawable d = solid(color, radius);
        d.setStroke(1, strokeColor);
        return d;
    }

    /** فشردن نرم با بازخورد لمسی — در حالت «کاهش حرکت» غیرفعال است. */
    static void press(View v, MeelanoUiHost host) {
        if (v == null) return;
        v.setClickable(true);
        if (host != null && host.motionAllowed()) {
            v.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        view.animate().scaleX(0.972f).scaleY(0.972f).setDuration(90).start();
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    }
                    return false;
                }
            });
            v.setOnHoverListener(null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) v.setElevation(host == null ? 4f : host.dp(3));
    }

    /** متن با فونت برنامه و ارقام فارسی. */
    static TextView text(MeelanoUiHost h, CharSequence value, float sp, int color, boolean bold) {
        TextView t = new TextView(h.ctx());
        t.setText(h.iconize(value));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(h.font(bold));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        return t;
    }

    static LinearLayout column(MeelanoUiHost h) {
        LinearLayout l = new LinearLayout(h.ctx());
        l.setOrientation(LinearLayout.VERTICAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    static LinearLayout row(MeelanoUiHost h) {
        LinearLayout l = new LinearLayout(h.ctx());
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int hgt) { return new LinearLayout.LayoutParams(w, hgt); }

    /** همان lp با وزن — برای چیدمان ستون‌های هم‌عرض در یک ردیف. */
    static LinearLayout.LayoutParams lp(int w, int hgt, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, hgt);
        params.weight = weight;
        return params;
    }

    static void gap(MeelanoUiHost h, LinearLayout parent, float dp) {
        View v = new View(h.ctx());
        parent.addView(v, lp(-1, Math.max(1, h.dp(dp))));
    }

    /** کارت جدید با شعاع و فاصله‌های استاندارد. */
    static LinearLayout card(MeelanoUiHost h, int accent) {
        LinearLayout c = column(h);
        float radius = h.dp(20);
        c.setBackground(cardBg(h.colors(), accent, radius));
        c.setPadding(h.dp(12), h.dp(11), h.dp(12), h.dp(11));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) c.setElevation(h.dp(4));
        return c;
    }

    static TextView pill(MeelanoUiHost h, String label, int accent, boolean filled) {
        TextView chip = text(h, label, 9.4f, filled ? h.colors().onColorFor(accent) : accent, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(h.dp(9), h.dp(4), h.dp(9), h.dp(4));
        chip.setBackground(solid(filled ? accent : h.colors().alpha(accent, h.colors().light ? 26 : 46), h.dp(999)));
        return chip;
    }

    // ================================================================ هدر و عنوان
    /** عنوان بخش با آیکون، خط تزئینی و زیرعنوان. */
    static LinearLayout sectionHeader(MeelanoUiHost h, String title, String subtitle, String glyph, int accent) {
        LinearLayout box = card(h, accent);
        LinearLayout head = row(h);
        TextView icon = text(h, glyph, 17f, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(stroke(h.colors().alpha(accent, h.colors().light ? 30 : 46), h.dp(14), h.colors().alpha(accent, 90)));
        head.addView(icon, lp(h.dp(40), h.dp(40)));
        LinearLayout copy = column(h);
        copy.setPadding(h.dp(9), 0, h.dp(4), 0);
        TextView t = text(h, title, 14.6f, h.colors().text, true);
        copy.addView(t, lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(h, subtitle, 9.7f, h.colors().muted, false);
            s.setMaxLines(3);
            copy.addView(s, lp(-1, -2));
        }
        head.addView(copy, lp(0, -2, 1f));
        box.addView(head, lp(-1, -2));
        return box;
    }

    /** هدر قهرمان با گرادیان برند — برای بالای صفحه‌های کلیدی. */
    static LinearLayout hero(MeelanoUiHost h, String title, String subtitle, String glyph, int accent, String badge) {
        MeelanoPalette p = h.colors();
        LinearLayout box = column(h);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(p.mix(p.bg, accent, p.light ? 0.12f : 0.26f), 255),
                p.alpha(p.mix(p.surface2, p.accent2, p.light ? 0.18f : 0.22f), 250),
                p.alpha(p.mix(p.bg, p.accent2, p.light ? 0.08f : 0.18f), 255)});
        bg.setCornerRadius(h.dp(24));
        bg.setStroke(1, p.alpha(p.mix(accent, Color.WHITE, 0.30f), p.light ? 120 : 90));
        box.setBackground(bg);
        box.setPadding(h.dp(14), h.dp(13), h.dp(14), h.dp(13));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) box.setElevation(h.dp(7));

        LinearLayout head = row(h);
        TextView icon = text(h, glyph, 19f, p.onColorFor(accent), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(solid(accent, h.dp(16)));
        head.addView(icon, lp(h.dp(46), h.dp(46)));
        LinearLayout copy = column(h);
        copy.setPadding(h.dp(10), 0, 0, 0);
        copy.addView(text(h, title, 17.2f, p.text, true), lp(-1, -2));
        TextView sub = text(h, subtitle == null ? "" : subtitle, 9.9f, p.muted, true);
        sub.setMaxLines(3);
        copy.addView(sub, lp(-1, -2));
        head.addView(copy, lp(0, -2, 1f));
        if (badge != null && !badge.isEmpty()) head.addView(pill(h, badge, accent, true), lp(-2, h.dp(26)));
        box.addView(head, lp(-1, -2));
        return box;
    }

    // ================================================================ کارت عددی (KPI)
    /** یک شاخص کلیدی: عدد بزرگ، عنوان، توضیح و آیکون رنگی. */
    static LinearLayout kpi(MeelanoUiHost h, String label, String value, String hint, String glyph, int accent, final Runnable onClick) {
        MeelanoPalette p = h.colors();
        LinearLayout c = column(h);
        c.setBackground(cardBg(p, accent, h.dp(18)));
        c.setPadding(h.dp(11), h.dp(10), h.dp(11), h.dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) c.setElevation(h.dp(5));
        LinearLayout top = row(h);
        TextView icon = text(h, glyph, 13.5f, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(solid(p.alpha(accent, p.light ? 28 : 48), h.dp(12)));
        top.addView(icon, lp(h.dp(30), h.dp(30)));
        TextView lab = text(h, label, 9.5f, p.muted, true);
        lab.setSingleLine(false);
        lab.setMaxLines(2);
        lab.setPadding(h.dp(7), 0, 0, 0);
        top.addView(lab, lp(0, -2, 1f));
        c.addView(top, lp(-1, -2));
        TextView val = text(h, value, 15.4f, p.text, true);
        val.setPadding(0, h.dp(6), 0, 0);
        val.setSingleLine(false);
        c.addView(val, lp(-1, -2));
        if (hint != null && !hint.isEmpty()) {
            TextView s = text(h, hint, 8.9f, p.muted, false);
            s.setMaxLines(2);
            c.addView(s, lp(-1, -2));
        }
        if (onClick != null) {
            press(c, h);
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        }
        return c;
    }

    /** شبکهٔ شاخص‌ها؛ هر ردیف دو کارت با فاصلهٔ یکسان. */
    static void kpiGrid(MeelanoUiHost h, LinearLayout parent, List<View> tiles, int perRow, int rowGapDp) {
        if (tiles == null || tiles.isEmpty()) return;
        int cols = Math.max(1, perRow);
        LinearLayout current = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                current = row(h);
                current.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rowLp = lp(-1, -2);
                if (i > 0) rowLp.topMargin = h.dp(rowGapDp);
                parent.addView(current, rowLp);
            }
            LinearLayout.LayoutParams itemLp = lp(0, -2, 1f);
            if (i % cols != 0) itemLp.leftMargin = h.dp(7);
            current.addView(tiles.get(i), itemLp);
        }
    }

    // ================================================================ دکمه‌ها
    static Button button(MeelanoUiHost h, String label, int accent, boolean primary, final Runnable onClick) {
        Button b = new Button(h.ctx());
        MeelanoPalette p = h.colors();
        b.setText(h.iconize(label));
        b.setAllCaps(false);
        b.setTextSize(10.6f);
        b.setTypeface(h.font(true));
        b.setTextColor(primary ? p.onColorFor(accent) : accent);
        b.setPadding(h.dp(12), h.dp(6), h.dp(12), h.dp(6));
        b.setBackground(primary ? solid(accent, h.dp(14)) : stroke(p.alpha(accent, p.light ? 26 : 44), h.dp(14), p.alpha(accent, 110)));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) b.setStateListAnimator(null);
        if (onClick != null) {
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
            press(b, h);
        }
        return b;
    }

    /** ردیف دکمه‌ها با تقسیم مساوی. */
    static LinearLayout buttonRow(MeelanoUiHost h, String[][] items, int[] accents, boolean[] primary, Runnable[] actions) {
        LinearLayout r = row(h);
        for (int i = 0; i < items.length; i++) {
            Button b = button(h, items[i][1], accents[Math.min(i, accents.length - 1)], primary[Math.min(i, primary.length - 1)], actions[Math.min(i, actions.length - 1)]);
            LinearLayout.LayoutParams l = lp(0, h.dp(42), 1f);
            if (i > 0) l.leftMargin = h.dp(6);
            r.addView(b, l);
        }
        return r;
    }

    /** نوار تب/فیلتر افقی: گزینهٔ فعال رنگی و بقیه آرام. */
    static LinearLayout chipBar(MeelanoUiHost h, String[][] items, String activeKey, final Pick pick) {
        MeelanoPalette p = h.colors();
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(h.ctx());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout rail = row(h);
        rail.setPadding(0, h.dp(2), 0, h.dp(2));
        for (final String[] item : items) {
            boolean active = item[0].equals(activeKey);
            String label = item.length > 2 ? item[2] + "  " + item[1] : item[1];
            TextView chip = text(h, label, 9.8f, active ? p.onColorFor(p.accent) : p.muted, active);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(h.dp(11), h.dp(6), h.dp(11), h.dp(6));
            chip.setBackground(active
                    ? solid(p.accent, h.dp(999))
                    : stroke(p.alpha(p.surface2, p.light ? 255 : 200), h.dp(999), p.alpha(p.muted, 70)));
            if (!active) chip.setTextColor(p.text);
            press(chip, h);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (pick != null) pick.pick(item[0]); }
            });
            LinearLayout.LayoutParams cl = lp(-2, h.dp(34));
            cl.leftMargin = h.dp(5);
            rail.addView(chip, cl);
        }
        scroll.addView(rail, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout wrapper = column(h);
        wrapper.addView(scroll, lp(-1, h.dp(38)));
        return wrapper;
    }

    /** رفتار انتخاب تب. */
    interface Pick { void pick(String key); }

    // ================================================================ هشدار، وضعیت خالی، جدول
    static LinearLayout alert(MeelanoUiHost h, String level, String title, String text) {
        MeelanoPalette p = h.colors();
        int tone = p.tone(level);
        LinearLayout c = row(h);
        c.setBackground(stroke(p.alpha(tone, p.light ? 20 : 40), h.dp(16), p.alpha(tone, 110)));
        c.setPadding(h.dp(10), h.dp(9), h.dp(10), h.dp(9));
        TextView icon = text(h, "danger".equals(level) ? "⚠" : "warn".equals(level) ? "⚠" : level.equals("success") ? "✓" : "●", 12.5f, tone, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(solid(p.alpha(tone, p.light ? 26 : 48), h.dp(11)));
        c.addView(icon, lp(h.dp(28), h.dp(28)));
        LinearLayout copy = column(h);
        copy.setPadding(h.dp(8), 0, 0, 0);
        copy.addView(text(h, title, 10.8f, tone, true), lp(-1, -2));
        if (text != null && !text.isEmpty()) {
            TextView t = text(h, text, 9.4f, p.text, false);
            t.setMaxLines(4);
            copy.addView(t, lp(-1, -2));
        }
        c.addView(copy, lp(0, -2, 1f));
        return c;
    }

    /** پیام «چیزی برای نمایش نیست» + دکمهٔ اقدام. */
    static LinearLayout emptyState(MeelanoUiHost h, String glyph, String title, String text2, String actionLabel, final Runnable action) {
        MeelanoPalette p = h.colors();
        LinearLayout c = column(h);
        c.setGravity(Gravity.CENTER);
        c.setPadding(h.dp(14), h.dp(22), h.dp(14), h.dp(22));
        c.setBackground(stroke(p.alpha(p.surface, 240), h.dp(20), p.alpha(p.muted, 60)));
        TextView icon = text(h, glyph, 22f, p.accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(solid(p.alpha(p.accent, p.light ? 24 : 46), h.dp(999)));
        LinearLayout iconWrap = row(h);
        iconWrap.setGravity(Gravity.CENTER);
        iconWrap.addView(icon, lp(h.dp(52), h.dp(52)));
        c.addView(iconWrap, lp(-1, -2));
        c.addView(text(h, title, 12.4f, p.text, true), lp(-1, -2));
        TextView sub = text(h, text2 == null ? "" : text2, 9.6f, p.muted, false);
        sub.setGravity(Gravity.CENTER);
        c.addView(sub, lp(-1, -2));
        if (actionLabel != null && action != null) {
            gap(h, c, 8);
            c.addView(button(h, actionLabel, p.accent, true, action), lp(-1, h.dp(42)));
        }
        return c;
    }

    /** جدول داده با سطر راه‌راه، سرسطر رنگی و پشتیبانی کلیک روی سطر. */
    static LinearLayout table(MeelanoUiHost h, String title, String[] headers, List<String[]> rows, int accent, final RowTap tap) {
        MeelanoPalette p = h.colors();
        LinearLayout box = card(h, accent);
        if (title != null && !title.isEmpty()) {
            LinearLayout head = row(h);
            head.addView(text(h, title, 12.2f, p.text, true), lp(0, -2, 1f));
            head.addView(pill(h, h.fa(rows == null ? "0" : String.valueOf(rows.size())) + " ردیف", accent, false), lp(-2, h.dp(24)));
            box.addView(head, lp(-1, -2));
            gap(h, box, 6);
        }
        if (rows == null || rows.isEmpty()) {
            box.addView(text(h, "داده‌ای برای نمایش نیست.", 9.8f, p.muted, false), lp(-1, -2));
            return box;
        }
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(h.ctx());
        scroll.setHorizontalScrollBarEnabled(true);
        LinearLayout grid = column(h);
        LinearLayout headerRow = row(h);
        headerRow.setBackground(solid(p.alpha(accent, p.light ? 22 : 40), h.dp(10)));
        for (String hd : headers) {
            TextView t = text(h, hd, 9.3f, p.mix(accent, p.text, 0.35f), true);
            t.setPadding(h.dp(8), h.dp(6), h.dp(8), h.dp(6));
            t.setSingleLine(true);
            headerRow.addView(t, lp(h.dp(112), -2));
        }
        grid.addView(headerRow, lp(-2, -2));
        for (int i = 0; i < rows.size(); i++) {
            final String[] r = rows.get(i);
            LinearLayout line = row(h);
            line.setBackground(solid(p.alpha(i % 2 == 0 ? p.surface : p.surface2, p.light ? 255 : 210), 0));
            for (int c = 0; c < headers.length; c++) {
                String v = c < r.length ? r[c] : "";
                TextView t = text(h, v == null ? "—" : v, 9.4f, c == 0 ? p.text : p.mix(p.text, p.muted, 0.25f), c == 0);
                t.setPadding(h.dp(8), h.dp(7), h.dp(8), h.dp(7));
                t.setSingleLine(true);
                line.addView(t, lp(h.dp(112), -2));
            }
            if (tap != null) {
                final int index = i;
                line.setClickable(true);
                line.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { tap.tap(index, r); }
                });
                press(line, h);
            }
            grid.addView(line, lp(-2, -2));
            if (i < rows.size() - 1) {
                View sep = new View(h.ctx());
                sep.setBackgroundColor(p.alpha(p.muted, 40));
                grid.addView(sep, lp(-2, Math.max(1, h.dp(0.6f))));
            }
        }
        scroll.addView(grid, new FrameLayout.LayoutParams(-2, -2));
        box.addView(scroll, lp(-1, -2));
        return box;
    }

    interface RowTap { void tap(int index, String[] row); }

    /** سطر «کلید: مقدار» با تراز راست‌به‌چپ. */
    static LinearLayout kv(MeelanoUiHost h, String key, String value, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout r = row(h);
        r.setPadding(0, h.dp(3), 0, h.dp(3));
        r.addView(text(h, key, 9.6f, p.muted, false), lp(0, -2, 1f));
        TextView v = text(h, value, 10.4f, accent == 0 ? p.text : accent, true);
        v.setGravity(Gravity.RIGHT);
        r.addView(v, lp(-2, -2));
        return r;
    }

    /** نوار پیشرفت افقی با برچسب و درصد. */
    static LinearLayout progress(MeelanoUiHost h, String label, double ratio, String valueText, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout box = column(h);
        LinearLayout top = row(h);
        top.addView(text(h, label, 9.5f, p.muted, true), lp(0, -2, 1f));
        top.addView(text(h, valueText == null ? "" : valueText, 9.5f, accent, true), lp(-2, -2));
        box.addView(top, lp(-1, -2));
        FrameLayout track = new FrameLayout(h.ctx());
        track.setBackground(solid(p.alpha(p.muted, p.light ? 40 : 60), h.dp(999)));
        View fill = new View(h.ctx());
        fill.setBackground(solid(accent, h.dp(999)));
        double r = Math.max(0, Math.min(1, ratio));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(0, h.dp(8));
        flp.gravity = Gravity.RIGHT;
        fill.setLayoutParams(flp);
        track.addView(fill);
        LinearLayout.LayoutParams tlp = lp(-1, h.dp(8));
        tlp.topMargin = h.dp(4);
        box.addView(track, tlp);
        if (h.motionAllowed()) {
            fill.setScaleX(0f);
            fill.setPivotX(1f);
            fill.animate().scaleX(1f).setDuration(420).start();
            final double rr = r;
            fill.post(new Runnable() {
                @Override public void run() {
                    ViewGroup.LayoutParams params = fill.getLayoutParams();
                    params.width = Math.max(2, (int) (track.getWidth() * rr));
                    fill.setLayoutParams(params);
                    fill.setScaleX(1f);
                }
            });
        } else {
            fill.post(new Runnable() {
                @Override public void run() {
                    ViewGroup.LayoutParams params = fill.getLayoutParams();
                    params.width = Math.max(2, (int) (track.getWidth() * r));
                    fill.setLayoutParams(params);
                }
            });
        }
        return box;
    }

    /** اسکلت بارگذاری (به‌جای چرخندهٔ ساده) با پالس ملایم. */
    static LinearLayout skeleton(MeelanoUiHost h, int lines) {
        MeelanoPalette p = h.colors();
        LinearLayout box = column(h);
        for (int i = 0; i < Math.max(1, lines); i++) {
            View bar = new View(h.ctx());
            bar.setBackground(solid(p.alpha(p.muted, p.light ? 38 : 54), h.dp(10)));
            LinearLayout.LayoutParams l = lp(i % 3 == 0 ? -1 : (i % 3 == 1 ? -1 : -1), h.dp(i % 2 == 0 ? 16 : 11));
            l.topMargin = h.dp(7);
            box.addView(bar, l);
            if (h.motionAllowed()) {
                bar.setAlpha(0.55f);
                bar.animate().alpha(1f).setDuration(700).setStartDelay(i * 90L).withEndAction(new Runnable() {
                    @Override public void run() {
                        bar.animate().alpha(0.55f).setDuration(700).withEndAction(this).start();
                    }
                }).start();
            }
        }
        return box;
    }

    // ================================================================ فرم‌ها
    /** کادر ورودی هم‌سبک با تم، برای شیت‌های تنظیمات ماژول‌ها. */
    static EditText field(MeelanoUiHost h, String hint, String value, boolean numeric) {
        MeelanoPalette p = h.colors();
        EditText e = new EditText(h.ctx());
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(12.4f);
        e.setTypeface(h.font(false));
        e.setTextColor(p.text);
        e.setHintTextColor(p.muted);
        e.setBackground(stroke(p.alpha(p.surface2, 235), h.dp(12), p.alpha(p.border, 90)));
        e.setPadding(h.dp(10), h.dp(8), h.dp(10), h.dp(8));
        e.setSingleLine(true);
        if (numeric) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            e.setTextDirection(View.TEXT_DIRECTION_RTL);
            e.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        }
        return e;
    }

    /** یک ردیف «برچسب + کادر ورودی» با فاصله‌های یکسان. */
    static View fieldRow(MeelanoUiHost h, String label, EditText input) {
        LinearLayout box = column(h);
        box.addView(text(h, label, 9.6f, h.colors().muted, true), lp(-1, -2));
        gap(h, box, 4);
        box.addView(input, lp(-1, h.dp(42)));
        return box;
    }

    /** رقم‌های فارسی/عربی و جداکننده‌ها را به عدد لاتین تبدیل می‌کند (برای خواندن ورودی کاربر). */
    static String enDigits(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') b.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') b.append((char) ('0' + (ch - '٠')));
            else if (ch == '٫' || ch == ',') b.append('.');
            else b.append(ch);
        }
        return b.toString().trim();
    }

    /** خواندن یک عدد از کادر ورودی با مقدار پیش‌فرض در صورت خطا. */
    static double number(EditText input, double fallback) {
        try {
            String v = enDigits(input == null ? "" : String.valueOf(input.getText())).replace(" ", "");
            if (v.isEmpty()) return fallback;
            return Double.parseDouble(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ================================================================ شیت پایین‌کشی
    /** پنل پایین‌کشی مدرن برای جزئیات و فرم‌های کوتاه (جایگزین دیالوگ‌های وسط‌صفحه). */
    static Dialog sheet(MeelanoUiHost h, String title, String glyph, int accent, View body, String positive, final Runnable onPositive, String negative) {
        MeelanoPalette p = h.colors();
        final Dialog dialog = new Dialog(h.ctx());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column(h);
        root.setBackground(cardBg(p, accent, h.dp(26)));
        root.setPadding(h.dp(14), h.dp(12), h.dp(14), h.dp(14));
        LinearLayout head = row(h);
        TextView icon = text(h, glyph == null ? "◆" : glyph, 14f, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(solid(p.alpha(accent, p.light ? 28 : 48), h.dp(12)));
        head.addView(icon, lp(h.dp(32), h.dp(32)));
        head.addView(text(h, title == null ? "" : title, 13.4f, p.text, true), lp(0, -2, 1f));
        TextView close = text(h, "✕", 13f, p.muted, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(solid(p.alpha(p.muted, p.light ? 26 : 44), h.dp(999)));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        head.addView(close, lp(h.dp(30), h.dp(30)));
        root.addView(head, lp(-1, -2));
        gap(h, root, 8);
        ScrollView scroll = new ScrollView(h.ctx());
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, lp(-1, 0, 1f));
        if (positive != null) {
            gap(h, root, 8);
            root.addView(button(h, positive, accent, true, new Runnable() {
                @Override public void run() { if (onPositive != null) onPositive.run(); dialog.dismiss(); }
            }), lp(-1, h.dp(44)));
        }
        if (negative != null) {
            gap(h, root, 6);
            root.addView(button(h, negative, p.muted, false, new Runnable() {
                @Override public void run() { dialog.dismiss(); }
            }), lp(-1, h.dp(40)));
        }
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.45f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        return dialog;
    }

    /** موتور مشترک: پاک‌کردن صفحه و نوشتن عنوان بخش با آیکون. */
    static void clearAndTitle(MeelanoUiHost h, LinearLayout page, String title, String subtitle, String glyph, int accent) {
        page.removeAllViews();
        page.addView(sectionHeader(h, title, subtitle, glyph, accent), lp(-1, -2));
        gap(h, page, 9);
    }

    static Context context(MeelanoUiHost h) { return h.ctx(); }

    static Typeface bold(MeelanoUiHost h) { return h.font(true); }
}
