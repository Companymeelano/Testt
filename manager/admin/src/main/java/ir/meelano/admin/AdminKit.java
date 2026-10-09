package ir.meelano.admin;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Tiny self-contained UI kit for the seller app (dark-gold luxury theme). */
public final class AdminKit {

    public static final int BG = 0xFF0C1220;
    public static final int SURFACE = 0xFF151D31;
    public static final int SURFACE2 = 0xFF1C2540;
    public static final int GOLD = 0xFFD9AE5A;
    public static final int GOLD_SOFT = 0xFFF1D493;
    public static final int TEXT = 0xFFF4EFE4;
    public static final int MUTED = 0xFF9AA3B5;
    public static final int GREEN = 0xFF4CAF7D;
    public static final int RED = 0xFFE06C5B;
    public static final int LINE = 0xFF2A3350;

    private static final String FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

    private AdminKit() { }

    // ---------- type ----------

    private static Typeface regular, bold, mono;

    public static Typeface reg(Context c) {
        if (regular == null) {
            try {
                regular = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Regular.ttf");
            } catch (Exception e) {
                regular = Typeface.DEFAULT;
            }
        }
        return regular;
    }

    public static Typeface bld(Context c) {
        if (bold == null) {
            try {
                bold = Typeface.createFromAsset(c.getAssets(), "fonts/Vazirmatn-Bold.ttf");
            } catch (Exception e) {
                bold = Typeface.DEFAULT_BOLD;
            }
        }
        return bold;
    }

    public static Typeface mon(Context c) {
        if (mono == null) mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
        return mono;
    }

    public static String fa(Object o) {
        String s = String.valueOf(o);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append((ch >= '0' && ch <= '9') ? FA_DIGITS.charAt(ch - '0') : ch);
        }
        return b.toString();
    }

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- widgets ----------

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static ScrollView scroll(Context c, View child, int padDp) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        int p = dp(c, padDp);
        child.setPadding(p, p, p, p);
        s.addView(child);
        return s;
    }

    public static TextView text(Context c, String t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t == null ? "" : t);
        // Design system: nothing in the app is rendered below 11sp, and every known glyph
        // becomes a vector icon instead of an emoji that depends on the phone's fonts.
        float base = sp < 11f ? 11f : sp;
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, base);
        v.setTextColor(color);
        v.setTypeface(bold ? bld(c) : reg(c));
        v.setLineSpacing(dp(c, 1.5f), 1.0f);
        AdminIcons.iconize(v);
        return v;
    }

    /** Vertical spacer snapped to one rhythm, so spacing is consistent everywhere. */
    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, gapDp(c, dp)));
        return v;
    }

    private static int gapDp(Context c, int want) {
        if (want <= 0) return 0;
        if (want > 24) return dp(c, Math.round(want / 8f) * 8);
        int[] scale = {2, 4, 6, 8, 12, 16, 20, 24};
        int best = scale[0];
        for (int v : scale) if (Math.abs(v - want) < Math.abs(best - want)) best = v;
        return dp(c, best);
    }

    public static TextView header(Activity a, String title) {
        LinearLayout wrap = vbox(a);
        TextView v = text(a, title, 21, TEXT, true);
        wrap.addView(v);
        View line = new View(a);
        line.setBackgroundColor(GOLD);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(a, 64), dp(a, 3));
        lp.topMargin = dp(a, 6);
        lp.bottomMargin = dp(a, 12);
        wrap.addView(line, lp);
        // Return the title view but keep the wrapper reachable via tag.
        v.setTag(wrap);
        return v;
    }

    /** Wraps header() so callers can add one view. */
    public static View titleBar(Activity a, String title, Runnable onBack) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (onBack != null) {
            TextView back = text(a, "‹", 30, GOLD, true);
            back.setPadding(dp(a, 4), 0, dp(a, 12), 0);
            back.setOnClickListener(v -> {
                try {
                    onBack.run();
                } catch (Exception ignored) { }
            });
            row.addView(back);
        }
        TextView t = text(a, title, 20, TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(t, lp);
        row.setPadding(0, 0, 0, dp(a, 12));
        return row;
    }

    public static View card(Context c) {
        LinearLayout l = vbox(c);
        GradientDrawable d = new GradientDrawable();
        d.setColor(SURFACE);
        d.setCornerRadius(dp(c, 20));
        d.setStroke(dp(c, 1), LINE);
        l.setBackground(d);
        // One controlled lift: depth comes from spacing as much as from shadow.
        l.setElevation(dp(c, 3));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static void cardMargin(View v, Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
    }

    public static Button btn(Context c, String t, boolean primary) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(bld(c));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(c, 16));
        if (primary) {
            d.setColor(GOLD);
            b.setTextColor(0xFF1A1408);
        } else {
            d.setColor(SURFACE2);
            d.setStroke(dp(c, 1), LINE);
            b.setTextColor(TEXT);
        }
        b.setBackground(d);
        // Design system: a button you can actually hit, and one icon family everywhere.
        int h = dp(c, primary ? 52 : 48);
        b.setMinHeight(h);
        b.setMinimumHeight(h);
        if (t != null && t.trim().length() <= 2) b.setMinWidth(h);
        AdminIcons.iconize(b);
        int v = dp(c, 12);
        b.setPadding(dp(c, 16), v, dp(c, 16), v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        b.setLayoutParams(lp);
        return b;
    }

    public static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTypeface(reg(c));
        e.setBackground(fieldBg(c));
        e.setMinHeight(dp(c, 48));
        int p = dp(c, 14);
        e.setPadding(p, dp(c, 12), p, dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        e.setLayoutParams(lp);
        return e;
    }

    /** Field shell: themed fill with a visible focus ring. */
    public static android.graphics.drawable.StateListDrawable fieldBg(Context c) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(SURFACE2);
        normal.setCornerRadius(dp(c, 16));
        normal.setStroke(dp(c, 1), LINE);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(SURFACE2);
        focused.setCornerRadius(dp(c, 16));
        focused.setStroke(dp(c, 2), GOLD);
        android.graphics.drawable.StateListDrawable s =
                new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static void rowTap(View v, Runnable r) {
        v.setOnClickListener(x -> {
            try {
                r.run();
            } catch (Exception ignored) { }
        });
    }

    public static void infoRow(LinearLayout box, Context c, String k, String v, boolean mono) {
        TextView kk = text(c, k, 12.5f, MUTED, false);
        box.addView(kk);
        TextView vv = text(c, (v == null || v.isEmpty()) ? "—" : v, 15, TEXT, false);
        if (mono) {
            vv.setTypeface(mon(c));
            // Licence packs are Latin/digit strings; keep them LTR so the order and the
            // dashes never get re-arranged by the bidirectional algorithm.
            vv.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 8);
        box.addView(vv, lp);
    }

    public static ImageView icon(Context c, int res, int color) {
        ImageView v = new ImageView(c);
        try {
            v.setImageResource(res);
            v.setColorFilter(color);
        } catch (Exception ignored) { }
        int s = dp(c, 22);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(s, s);
        lp.leftMargin = dp(c, 10);
        lp.gravity = Gravity.CENTER_VERTICAL;
        v.setLayoutParams(lp);
        return v;
    }

    // ---------- actions ----------

    public static void toast(Context c, String m) {
        try {
            Toast.makeText(c, m, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) { }
    }

    public static void copy(Context c, String label, String text) {
        try {
            ClipboardManager cm =
                    (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
        } catch (Exception ignored) { }
    }

    public static void share(Context c, String title, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            c.startActivity(Intent.createChooser(i, title));
        } catch (Exception e) {
            copy(c, title, text);
            toast(c, "در حافظه کپی شد");
        }
    }

    public static String txt(EditText e) {
        try {
            return e.getText().toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String prettifyPack(String pack) {
        if (pack == null) return "";
        String p = pack.trim();
        if (p.length() != 39) return p;
        // M1-DEV8-PLAN-EXP5-IAT5-RND2-SIG(4-4-4-4) for easy phone reading.
        return p.substring(0, 2) + "-" + p.substring(2, 10) + "-" + p.charAt(10)
                + "-" + p.substring(11, 16) + "-" + p.substring(16, 21)
                + "-" + p.substring(21, 23) + "-" + p.substring(23, 27)
                + "-" + p.substring(27, 31) + "-" + p.substring(31, 35)
                + "-" + p.substring(35, 39);
    }
}
