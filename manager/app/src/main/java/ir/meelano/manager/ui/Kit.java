package ir.meelano.manager.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import ir.meelano.manager.core.Jalali;
import ir.meelano.manager.core.Money;
import ir.meelano.manager.core.Sql;
import ir.meelano.manager.core.ReportCatalog;
import ir.meelano.manager.data.Row;

import java.util.ArrayList;
import java.util.List;

/** Luxury view builders shared by all screens. */
public final class Kit {
    public final Activity a;

    public Kit(Activity a) {
        this.a = a;
    }

    // ---------------- text ----------------
    public TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s == null ? "" : s);
        // v37: legibility floor, then two multipliers. fontScale is the user's own zoom
        // choice; typeScale grows type with the screen so labels stay readable on the large
        // displays of high-end phones. Only type is scaled - layout metrics stay in dp.
        float base = sp < 12.5f ? 12.5f : sp;
        t.setTextSize(base * Theme.fontScale() * Theme.typeScale());
        t.setTextColor(color);
        t.setTypeface(Theme.face(bold));
        t.setLineSpacing(Theme.dp(1.5f), 1.0f);
        MeelanoIcons.iconize(t);
        return t;
    }

    public TextView title(String s) {
        return text(s, 15f, Theme.TEXT, true);
    }

    public TextView sub(String s) {
        return text(s, 11.5f, Theme.MUTED, false);
    }

    // ---------------- layout ----------------
    public LinearLayout v() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout h() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public LinearLayout.LayoutParams wlp(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    public Space space(int dpW) {
        Space s = new Space(a);
        s.setMinimumWidth(Theme.dp(dpW));
        return s;
    }

    public View gap(int dpH) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(step(dpH))));
        return v;
    }

    /** v35: snap every gap to one rhythm so vertical spacing is consistent in all sections. */
    private static int step(int dp) {
        if (dp <= 0) return 0;
        if (dp > 24) return Math.round(dp / 8f) * 8;
        int[] scale = {2, 4, 6, 8, 12, 16, 20, 24};
        int best = scale[0];
        for (int s : scale) if (Math.abs(s - dp) < Math.abs(best - dp)) best = s;
        return best;
    }

    public LinearLayout card() {
        LinearLayout c = v();
        c.setBackground(Theme.card());
        c.setPadding(Theme.dp(14), Theme.dp(13), Theme.dp(14), Theme.dp(13));
        c.setElevation(Theme.cardElevation());
        return c;
    }

    public LinearLayout card(int accent) {
        LinearLayout c = v();
        c.setBackground(Theme.cardAccent(accent));
        c.setPadding(Theme.dp(14), Theme.dp(13), Theme.dp(14), Theme.dp(13));
        c.setElevation(Theme.cardElevation());
        return c;
    }

    public void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.setMargins(0, 0, 0, Theme.dp(12));
        parent.addView(card, p);
    }

    // ---------------- hero & sections ----------------
    public View hero(String glyph, String title, String subtitle, int accent) {
        LinearLayout c = v();
        c.setBackground(Theme.hero(accent));
        c.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(15));
        LinearLayout head = h();
        TextView g = text(glyph, 30, accent, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Theme.avatar(Theme.alpha(accent, 60)));
        head.addView(g, new LinearLayout.LayoutParams(Theme.dp(58), Theme.dp(58)));
        head.addView(space(10));
        LinearLayout copy = v();
        copy.setPadding(Theme.dp(2), 0, 0, 0);
        copy.addView(title(title), lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(sub(subtitle), lp(-1, -2));
        head.addView(copy, wlp(1f));
        c.addView(head, lp(-1, -2));
        return c;
    }

    public View sectionHead(String title, String actionLabel, View.OnClickListener action) {
        LinearLayout r = h();
        r.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(6));
        TextView t = text(title, 14f, Theme.GOLD_SOFT, true);
        r.addView(t, wlp(1f));
        if (actionLabel != null && action != null) {
            TextView b = text(actionLabel, 11.5f, Theme.INFO, true);
            b.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(6));
            b.setBackground(Theme.ghostButton(Theme.INFO));
            Theme.pressable(b);
            b.setOnClickListener(action);
            r.addView(b, lp(-2, -2));
        }
        return r;
    }

    // ---------------- KPI ----------------
    public static final class Kpi {
        public final String label;
        public final String value;
        public final String sub;
        public final int accent;
        public final Runnable action;
        /** When not NaN, the tile counts up from 0 to this number. */
        public final double countTo;

        public Kpi(String label, String value, String sub, int accent) {
            this(label, value, sub, accent, null);
        }

        public Kpi(String label, String value, String sub, int accent, Runnable action) {
            this(label, Double.NaN, value, sub, accent, action);
        }

        /** Count-up tile: animates 0 → target, then shows the exact formatted value. */
        public Kpi(String label, double countTo, String value, String sub, int accent) {
            this(label, countTo, value, sub, accent, null);
        }

        public Kpi(String label, double countTo, String value, String sub, int accent, Runnable action) {
            this.label = label;
            this.countTo = countTo;
            this.value = value;
            this.sub = sub;
            this.accent = accent;
            this.action = action;
        }
    }

    public View kpiTile(Kpi k) {
        LinearLayout t = v();
        t.setBackground(Theme.kpi(k.accent));
        t.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        TextView l = text(k.label, 10.5f, Theme.MUTED, false);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(l, lp(-1, -2));
        TextView v = text(k.value, 16.5f, Theme.TEXT, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(v, lp(-1, -2));
        if (!Double.isNaN(k.countTo)) {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(900);
            va.setInterpolator(new android.view.animation.DecelerateInterpolator());
            va.addUpdateListener(an -> v.setText(Money.fa(Money.compact(k.countTo * (float) an.getAnimatedValue()))));
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) { v.setText(k.value); }
            });
            va.start();
        } else {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(350).start();
        }
        if (k.sub != null && !k.sub.isEmpty()) {
            TextView s = text(k.sub, 10f, k.accent, true);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(s, lp(-1, -2));
        }
        if (k.action != null) {
            TextView more = text("مشاهده جزئیات ›", 9.5f, Theme.MUTED, true);
            t.addView(more, lp(-1, -2));
            Theme.pressable(t);
            t.setOnClickListener(vw -> k.action.run());
        }
        return t;
    }

    /** True on tablets / wide screens (smallest width 600dp+): grids open up. */
    public boolean isWide() {
        try {
            return a.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        } catch (Exception e) {
            return false;
        }
    }

    public View kpiGrid(List<Kpi> kpis, int cols) {
        int span = isWide() ? Math.max(cols, 4) : cols;
        LinearLayout root = v();
        LinearLayout row = null;
        for (int i = 0; i < kpis.size(); i++) {
            if (i % span == 0) {
                row = h();
                LinearLayout.LayoutParams rp = lp(-1, -2);
                if (i > 0) rp.setMargins(0, Theme.dp(10), 0, 0);
                root.addView(row, rp);
            }
            View tile = kpiTile(kpis.get(i));
            LinearLayout.LayoutParams p = wlp(1f);
            if (i % span != span - 1) p.setMarginEnd(Theme.dp(10));
            row.addView(tile, p);
        }
        return root;
    }

    // ---------------- rows ----------------
    public View kv(String k, String v) {
        return kv(k, v, Theme.TEXT);
    }

    public View kv(String k, String v, int vColor) {
        LinearLayout r = h();
        r.setPadding(0, Theme.dp(6), 0, Theme.dp(6));
        TextView key = text(k, 11.5f, Theme.MUTED, false);
        key.setSingleLine(true);
        key.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // v35: label and value each own a share of the row, so a long product name,
        // address or note wraps instead of squeezing the label out of the row.
        r.addView(key, wlp(0.42f));
        TextView val = text(v == null || v.isEmpty() ? "—" : v, 12.5f, vColor, true);
        val.setGravity(Gravity.END);
        r.addView(val, wlp(0.58f));
        return r;
    }

    public View divider() {
        View v = new View(a);
        v.setBackgroundColor(Theme.alpha(Theme.GOLD, 26));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(1)));
        return v;
    }

    public View pill(String s, int color) {
        TextView t = text(s, 10.5f, color, true);
        t.setPadding(Theme.dp(11), Theme.dp(5), Theme.dp(11), Theme.dp(5));
        t.setBackground(Theme.pill(color));
        t.setSingleLine(true);
        return t;
    }

    public View personRow(String name, String subLine, String value, String pillText, int accent, View.OnClickListener onClick) {
        LinearLayout c = v();
        c.setBackground(Theme.cardAccent(accent));
        c.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        LinearLayout head = h();
        TextView av = text(initials(name), 16, Theme.BG, true);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Theme.avatar(accent));
        head.addView(av, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
        head.addView(space(9));
        LinearLayout copy = v();
        TextView n = text(name, 14.5f, Theme.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(n, lp(-1, -2));
        if (subLine != null && !subLine.isEmpty()) {
            TextView s = text(subLine, 10.5f, Theme.MUTED, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(s, lp(-1, -2));
        }
        head.addView(copy, wlp(1f));
        LinearLayout side = v();
        side.setGravity(Gravity.END);
        if (value != null && !value.isEmpty()) side.addView(text(value, 13.5f, Theme.TEXT, true), lp(-2, -2));
        if (pillText != null && !pillText.isEmpty()) {
            View p = pill(pillText, accent);
            LinearLayout.LayoutParams pp = lp(-2, -2);
            pp.setMargins(0, Theme.dp(4), 0, 0);
            side.addView(p, pp);
        }
        head.addView(side, lp(-2, -2));
        c.addView(head, lp(-1, -2));
        if (onClick != null) {
            Theme.pressable(c);
            c.setOnClickListener(onClick);
        }
        return c;
    }

    public String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "؟";
        String[] parts = name.trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++)
            if (!parts[i].isEmpty()) b.append(parts[i].charAt(0));
        return b.length() == 0 ? "؟" : b.toString();
    }

    public View alertRow(String title, String body, int accent, View.OnClickListener onClick) {
        LinearLayout line = h();
        line.setPadding(Theme.dp(11), Theme.dp(10), Theme.dp(11), Theme.dp(10));
        line.setBackground(Theme.ghostButton(accent));
        TextView dot = text("●", 15, accent, true);
        line.addView(dot, new LinearLayout.LayoutParams(Theme.dp(24), -2));
        LinearLayout copy = v();
        copy.addView(text(title, 12f, Theme.TEXT, true), lp(-1, -2));
        if (body != null && !body.isEmpty()) {
            TextView b = text(body, 10.8f, Theme.MUTED, false);
            copy.addView(b, lp(-1, -2));
        }
        line.addView(copy, wlp(1f));
        if (onClick != null) {
            Theme.pressable(line);
            line.setOnClickListener(onClick);
        }
        return line;
    }

    public View navRow(String glyph, String title, String subLine, int accent, View.OnClickListener onClick) {
        LinearLayout c = h();
        c.setBackground(Theme.card());
        c.setPadding(Theme.dp(13), Theme.dp(12), Theme.dp(13), Theme.dp(12));
        c.setElevation(Theme.cardElevation());
        TextView g = text(glyph, 22, accent, true);
        g.setGravity(Gravity.CENTER);
        g.setBackground(Theme.avatar(Theme.alpha(accent, 52)));
        c.addView(g, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(48)));
        c.addView(space(10));
        LinearLayout copy = v();
        copy.addView(text(title, 14f, Theme.TEXT, true), lp(-1, -2));
        if (subLine != null && !subLine.isEmpty()) copy.addView(sub(subLine), lp(-1, -2));
        c.addView(copy, wlp(1f));
        TextView arrow = text("‹", 24, Theme.MUTED, true);
        c.addView(arrow, lp(-2, -2));
        Theme.pressable(c);
        c.setOnClickListener(onClick);
        return c;
    }

    public View progressLine(String label, String value, double frac, int color) {
        LinearLayout root = v();
        LinearLayout head = h();
        head.addView(text(label, 11.5f, Theme.MUTED, false), wlp(1f));
        head.addView(text(value, 11.5f, color, true), lp(-2, -2));
        root.addView(head, lp(-1, -2));
        FrameLayout track = new FrameLayout(a);
        track.setBackground(Theme.pill(Theme.alpha(color, 60)));
        final View fill = new View(a);
        fill.setBackground(Theme.pill(color));
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        final float fr = (float) Math.max(0, Math.min(1, frac));
        track.post(() -> {
            android.view.ViewGroup.LayoutParams p = fill.getLayoutParams();
            p.width = Math.max(Theme.dp(6), (int) (track.getWidth() * fr));
            fill.setLayoutParams(p);
        });
        LinearLayout.LayoutParams tp = lp(-1, Theme.dp(7));
        tp.setMargins(0, Theme.dp(5), 0, 0);
        root.addView(track, tp);
        return root;
    }

    // ---------------- buttons & chips ----------------
    public Button btnGold(String t, View.OnClickListener onClick) {
        Button b = new Button(a);
        b.setText(t);
        b.setTextSize(13.5f);
        b.setTextColor(Theme.onAccent());
        b.setTypeface(Theme.face(true));
        b.setBackground(Theme.goldButton());
        b.setPadding(Theme.dp(16), Theme.dp(10), Theme.dp(16), Theme.dp(10));
        b.setMinHeight(Theme.dp(52));
        b.setMinimumHeight(Theme.dp(52));
        if (t != null && t.trim().length() <= 2) b.setMinWidth(Theme.dp(52));
        b.setOnClickListener(onClick);
        b.setAllCaps(false);
        MeelanoIcons.iconize(b);
        Theme.pressable(b);
        return b;
    }

    public Button btnGhost(String t, int accent, View.OnClickListener onClick) {
        Button b = new Button(a);
        b.setText(t);
        b.setTextSize(12.5f);
        b.setTextColor(accent);
        b.setTypeface(Theme.face(true));
        b.setBackground(Theme.ghostButton(accent));
        b.setPadding(Theme.dp(14), Theme.dp(9), Theme.dp(14), Theme.dp(9));
        b.setMinHeight(Theme.dp(48));
        b.setMinimumHeight(Theme.dp(48));
        if (t != null && t.trim().length() <= 2) b.setMinWidth(Theme.dp(48));
        b.setOnClickListener(onClick);
        b.setAllCaps(false);
        MeelanoIcons.iconize(b);
        Theme.pressable(b);
        return b;
    }

    public TextView chip(String t, boolean selected, int accent, View.OnClickListener onClick) {
        TextView c = text(t, 11.5f, selected ? Theme.onAccent() : accent, true);
        c.setBackground(Theme.chip(selected, selected ? accent : Theme.alpha(accent, 255)));
        c.setPadding(Theme.dp(14), Theme.dp(8), Theme.dp(14), Theme.dp(8));
        c.setSingleLine(true);
        Theme.pressable(c);
        c.setOnClickListener(onClick);
        return c;
    }

    public View chipsRow(java.util.List<String> items, int selected, int accent, final ChipsListener l) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout r = h();
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            View ch = chip(items.get(i), i == selected, accent, v -> l.onPick(idx));
            LinearLayout.LayoutParams p = lp(-2, -2);
            p.setMarginEnd(Theme.dp(8));
            r.addView(ch, p);
        }
        sv.addView(r, new HorizontalScrollView.LayoutParams(-2, -2));
        return sv;
    }

    public interface ChipsListener {
        void onPick(int idx);
    }

    public interface SearchListener {
        void onGo(String q);
    }

    /** Search field with a go button. */
    public View searchBar(String hint, String initial, final SearchListener l) {
        LinearLayout r = h();
        final EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(initial == null ? "" : initial);
        e.setTextSize(12.5f);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.MUTED);
        e.setTypeface(Theme.face(false));
        e.setBackground(Theme.searchBar());
        e.setPadding(Theme.dp(14), Theme.dp(10), Theme.dp(14), Theme.dp(10));
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        r.addView(e, wlp(1f));
        r.addView(space(8));
        Button mic = btnGhost("🎙", Theme.GOLD, v -> {
            try {
                ((ir.meelano.manager.MainActivity) a).startVoiceSearch(t -> {
                    e.setText(t == null ? "" : t);
                    hideKeyboard(e);
                    l.onGo(t == null ? "" : t);
                });
            } catch (Exception ignored) { }
        });
        r.addView(mic, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(50)));
        r.addView(space(8));
        Button go = btnGold("⌕", v -> {
            hideKeyboard(e);
            l.onGo(e.getText().toString());
        });
        r.addView(go, new LinearLayout.LayoutParams(Theme.dp(52), Theme.dp(50)));
        e.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(e);
                l.onGo(e.getText().toString());
                return true;
            }
            return false;
        });
        return r;
    }

    public void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    /** In-content filter bar: live summary of the section's filters, opens the sheet. */
    public View filterBar(String summary, boolean active, View.OnClickListener onTap) {
        LinearLayout r = h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(active ? Theme.cardAccent(Theme.GOLD) : Theme.card());
        r.setPadding(Theme.dp(12), Theme.dp(9), Theme.dp(12), Theme.dp(9));
        TextView ic = text("◈", 17, active ? Theme.GOLD : Theme.MUTED, true);
        r.addView(ic, lp(-2, -2));
        r.addView(space(8));
        LinearLayout copy = v();
        copy.addView(text("فیلترها" + (active ? " • فعال" : ""), 10f, active ? Theme.GOLD_SOFT : Theme.MUTED, true), lp(-1, -2));
        TextView sm = text(summary == null || summary.isEmpty() ? "همه" : summary, 12f, Theme.TEXT, false);
        sm.setSingleLine(true);
        sm.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(sm, lp(-1, -2));
        r.addView(copy, wlp(1f));
        r.addView(space(8));
        r.addView(text("‹", 20, Theme.GOLD_SOFT, true), lp(-2, -2));
        Theme.pressable(r);
        r.setOnClickListener(onTap);
        return r;
    }

    // ---------------- states ----------------
    /** Fake search field (gold loupe + hint) that opens the filter sheet. */
    public View searchBar(String hint, View.OnClickListener onClick) {
        LinearLayout r = h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Theme.searchBar());
        r.setPadding(Theme.dp(14), Theme.dp(11), Theme.dp(14), Theme.dp(11));
        r.addView(text("⌕", 17, Theme.GOLD, true), lp(-2, -2));
        r.addView(space(8));
        TextView t = text(hint == null || hint.isEmpty() ? "جستجو…" : hint, 12f, Theme.MUTED, false);
        r.addView(t, wlp(1f));
        Theme.pressable(r);
        r.setOnClickListener(onClick);
        return r;
    }

    public View loading(String msg) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER);
        ProgressBar p = new ProgressBar(a);
        try { p.getIndeterminateDrawable().setTint(Theme.GOLD); } catch (Exception ignored) { }
        c.addView(p, new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40)));
        TextView t = text(msg, 12f, Theme.MUTED, false);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = lp(-1, -2);
        tp.setMargins(0, Theme.dp(10), 0, 0);
        c.addView(t, tp);
        return c;
    }

    public View empty(String title, String body) {
        LinearLayout c = card();
        TextView g = text("◇", 40, Theme.MUTED, true);
        g.setGravity(Gravity.CENTER);
        c.addView(g, lp(-1, -2));
        TextView t = text(title, 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, lp(-1, -2));
        if (body != null && !body.isEmpty()) {
            TextView b = text(body, 11.5f, Theme.MUTED, false);
            b.setGravity(Gravity.CENTER);
            c.addView(b, lp(-1, -2));
        }
        return c;
    }

    public View error(String msg, final Runnable retry) {
        LinearLayout c = card(Theme.DANGER);
        TextView g = text("⚠", 34, Theme.DANGER, true);
        g.setGravity(Gravity.CENTER);
        c.addView(g, lp(-1, -2));
        TextView t = text("خطا در دریافت اطلاعات", 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, lp(-1, -2));
        TextView b = text(msg == null || msg.isEmpty() ? "خطای نامشخص" : msg, 11.5f, Theme.MUTED, false);
        b.setGravity(Gravity.CENTER);
        c.addView(b, lp(-1, -2));
        if (retry != null) {
            Button r = btnGold("⟳ تلاش مجدد", v -> retry.run());
            LinearLayout.LayoutParams p = lp(-1, -2);
            p.setMargins(0, Theme.dp(12), 0, 0);
            c.addView(r, p);
        }
        return c;
    }

    public View pager(int page, boolean hasMore, final Runnable prev, final Runnable next) {
        LinearLayout r = h();
        r.setGravity(Gravity.CENTER);
        Button p = btnGhost("‹ قبلی", Theme.GOLD, v -> prev.run());
        p.setEnabled(page > 0);
        p.setAlpha(page > 0 ? 1f : 0.4f);
        r.addView(p, lp(-2, -2));
        r.addView(space(10));
        TextView t = text("صفحه " + Money.fa(String.valueOf(page + 1)), 12f, Theme.MUTED, true);
        r.addView(t, lp(-2, -2));
        r.addView(space(10));
        Button n = btnGhost("بعدی ›", Theme.GOLD, v -> next.run());
        n.setEnabled(hasMore);
        n.setAlpha(hasMore ? 1f : 0.4f);
        r.addView(n, lp(-2, -2));
        return r;
    }

    // ---------------- data table ----------------
    public interface RowClick {
        void onRow(Row r);
    }

    public String fmtCol(ReportCatalog.Col col, Row r) {
        if (col.type == ReportCatalog.T_MONEY) return Money.rial(r.d(col.key));
        if (col.type == ReportCatalog.T_NUM) {
            double v = r.d(col.key);
            if (Math.abs(v - Math.round(v)) < 0.001) return Money.num(Math.round(v));
            return Money.fa(String.format(java.util.Locale.US, "%.1f", v).replace('.', '٫').replace('-', '−'));
        }
        if (col.type == ReportCatalog.T_DATE) {
            return Jalali.dispFa(r.s(col.key));
        }
        String t = r.s(col.key, "—");
        // Gregorian datetimes (logins, change log) → Jalali; anything else passes through.
        return "—".equals(t) ? t : Money.fa(Jalali.faDate(t));
    }

    /** Deluxe scrollable table: row numbers, gold zebra, red negatives, rounded card. */
    public View dataTable(ReportCatalog.Col[] cols, List<Row> rows, final RowClick click) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setBackground(Theme.card());
        sv.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        LinearLayout t = v();
        t.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        LinearLayout head = h();
        head.setBackground(Theme.tableHeader());
        head.setPadding(Theme.dp(6), Theme.dp(9), Theme.dp(6), Theme.dp(9));
        TextView rn0 = text("#", 11.5f, Theme.GOLD_SOFT, true);
        rn0.setGravity(Gravity.CENTER);
        head.addView(rn0, new LinearLayout.LayoutParams(Theme.dp(44), -2));
        for (ReportCatalog.Col c : cols) {
            TextView h = text(c.title, 11.5f, Theme.GOLD_SOFT, true);
            h.setGravity(Gravity.CENTER);
            head.addView(h, new LinearLayout.LayoutParams(Theme.dp(colWidth(c)), -2));
        }
        t.addView(head, lp(-2, -2));
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            LinearLayout row = h();
            row.setPadding(Theme.dp(6), Theme.dp(10), Theme.dp(6), Theme.dp(10));
            if (i % 2 == 1) row.setBackgroundColor(Theme.alpha(Theme.GOLD, Theme.DARK ? 16 : 30));
            TextView rn = text(Money.fa(String.valueOf(i + 1)), 11f, Theme.MUTED, true);
            rn.setGravity(Gravity.CENTER);
            row.addView(rn, new LinearLayout.LayoutParams(Theme.dp(44), -2));
            for (ReportCatalog.Col c : cols) {
                double dv = (c.type == ReportCatalog.T_MONEY || c.type == ReportCatalog.T_NUM) ? r.d(c.key) : 0;
                int col = dv < -0.0001 ? Theme.DANGER
                        : (c.type == ReportCatalog.T_MONEY ? Theme.TEXT : (c.type == ReportCatalog.T_TEXT ? Theme.TEXT : Theme.MUTED));
                TextView cell = text(fmtCol(c, r), 12f, col, c.type == ReportCatalog.T_MONEY);
                cell.setGravity(Gravity.CENTER);
                cell.setSingleLine(true);
                cell.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(cell, new LinearLayout.LayoutParams(Theme.dp(colWidth(c)), -2));
            }
            if (click != null) {
                Theme.pressable(row);
                row.setOnClickListener(v -> click.onRow(r));
            }
            t.addView(row, lp(-2, -2));
            if (i < rows.size() - 1) t.addView(divider(), lp(-1, Theme.dp(1)));
        }
        sv.addView(t, new HorizontalScrollView.LayoutParams(-2, -2));
        return sv;
    }

    private int colWidth(ReportCatalog.Col c) {
        if (c.type == ReportCatalog.T_MONEY) return 150;
        if (c.type == ReportCatalog.T_DATE) return 110;
        if (c.type == ReportCatalog.T_NUM) return 80;
        return 140;
    }

    // ---------------- misc ----------------
    public void toast(String msg) {
        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
    }

    /** Selection callback for {@link #searchPicker}. */
    public interface PickerListener { void onPick(Row r); }

    /**
     * Searchable selection dialog.
     *
     * Several pickers were plain button lists with no way to filter, which is unusable once
     * a list outgrows the screen - the supplier list runs to thousands of rows. Every
     * selection now gets a filter field that narrows the list as you type. Matching uses
     * Sql.searchKey, so the Arabic and Persian spellings of a name are interchangeable,
     * digits typed in either script match, and punctuation is ignored.
     *
     * @param showKeys row fields to display, joined with a separator; every value in the
     *                 row is searched, not only the displayed ones.
     */
    public void searchPicker(String title, final List<Row> rows, final String[] showKeys,
            final PickerListener onPick) {
        final List<Row> all = rows == null ? new ArrayList<Row>() : rows;
        LinearLayout root = v();
        root.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(16), Theme.dp(12));
        final android.widget.EditText q = edit("\u062c\u0633\u062a\u200c\u0648\u062c\u0648\u2026", "");
        try {
            q.setSingleLine(true);
            q.setTextDirection(View.TEXT_DIRECTION_RTL);
        } catch (Exception ignored) { }
        root.addView(q, lp(-1, -2));
        final TextView count = text("", 11.5f, Theme.MUTED, false);
        root.addView(count, lp(-1, -2));
        root.addView(gap(8));
        final LinearLayout list = v();
        final AlertDialog[] box = new AlertDialog[1];
        final Runnable paint = new Runnable() {
            @Override public void run() {
                String needle = Sql.searchKey(q.getText() == null ? "" : q.getText().toString());
                list.removeAllViews();
                int shown = 0;
                for (int i = 0; i < all.size(); i++) {
                    final Row r = all.get(i);
                    if (r == null) continue;
                    if (!needle.isEmpty() && !Sql.searchKey(rowSearchText(r)).contains(needle)) continue;
                    shown++;
                    StringBuilder label = new StringBuilder();
                    if (showKeys != null) {
                        for (String k : showKeys) {
                            if (k == null) continue;
                            String cell = r.s(k);
                            if (cell == null || cell.trim().isEmpty()) continue;
                            if (label.length() > 0) label.append("  \u2022  ");
                            label.append(cell);
                        }
                    }
                    if (label.length() == 0) label.append("\u2014");
                    list.addView(btnGhost(label.toString(), Theme.GOLD, new View.OnClickListener() {
                        @Override public void onClick(View tapped) {
                            try { box[0].dismiss(); } catch (Exception ignored) { }
                            if (onPick != null) onPick.onPick(r);
                        }
                    }), lp(-1, -2));
                }
                count.setText(Money.fa(String.valueOf(shown)) + " \u0627\u0632 "
                        + Money.fa(String.valueOf(all.size())) + " \u0645\u0648\u0631\u062f");
                if (shown == 0) list.addView(empty("\u0645\u0648\u0631\u062f\u06cc \u06cc\u0627\u0641\u062a \u0646\u0634\u062f", null), lp(-1, -2));
            }
        };
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a2, int b2, int c2) { }
            @Override public void onTextChanged(CharSequence s2, int a2, int b2, int c2) { paint.run(); }
            @Override public void afterTextChanged(android.text.Editable e2) { }
        });
        paint.run();
        root.addView(scrollWrap(list, 430), lp(-1, -2));
        box[0] = dialog(title, root, true);
    }

    /** Every value of a row, flattened for search matching. */
    private static String rowSearchText(Row r) {
        StringBuilder b = new StringBuilder();
        for (Object v : r.values()) {
            if (v == null) continue;
            b.append(' ').append(v);
        }
        return b.toString();
    }

    public AlertDialog dialog(String title, View body, boolean cancelable) {
        // Custom title view: the stock dialog title is unreadable in the light theme.
        TextView tv = text(title == null ? "" : title, 15f, Theme.TEXT, true);
        tv.setGravity(Gravity.CENTER);
        int pad = Theme.dp(14);
        tv.setPadding(pad, pad, pad, Theme.dp(4));
        AlertDialog d = new AlertDialog.Builder(a)
                .setCustomTitle(tv)
                .setView(body)
                .setCancelable(cancelable)
                .create();
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(Theme.dialogBg());
        } catch (Exception ignored) { }
        return d;
    }

    public ScrollView scrollWrap(View inner, int maxDp) {
        ScrollView sv = new ScrollView(a);
        sv.addView(inner, new ScrollView.LayoutParams(-1, -2));
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(maxDp)));
        return sv;
    }

    // ---------------- fullscreen charts ----------------
    /** Builds a fresh chart view for the fullscreen viewer. */
    public interface ChartMaker { View make(); }

    /** Card title row with an expand (⤢) button that opens the fullscreen viewer. */
    public View chartHead(String title, final ChartMaker maker) {
        LinearLayout r = h();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(title(title), wlp(1f));
        if (maker != null) {
            Button b = btnGhost("⤢", Theme.GOLD, v -> fullChart(title, maker));
            r.addView(b, new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(44)));
        }
        return r;
    }

    /** Tall dialog hosting a freshly built chart. */
    public void fullChart(String title, ChartMaker maker) {
        if (maker == null) return;
        LinearLayout root = v();
        root.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(10));
        View chart = maker.make();
        int hPx = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.60);
        root.addView(chart, new LinearLayout.LayoutParams(-1, hPx));
        try { dialog(title, root, true).show(); } catch (Exception ignored) { }
    }

    public ImageView logo(int sizeDp) {
        ImageView v = new ImageView(a);
        try {
            v.setImageResource(a.getResources().getIdentifier("meelano_3d", "drawable", a.getPackageName()));
        } catch (Exception ignored) { }
        v.setAdjustViewBounds(true);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Honour the requested size: the argument used to be accepted and then ignored, so a
        // caller that relied on it got an unsized ImageView and the artwork rendered at its
        // raw pixel size instead.
        if (sizeDp > 0) v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(sizeDp), Theme.dp(sizeDp)));
        return v;
    }

    /** «امروز ۱۴ مهر» style date line. */
    public String todayLine() {
        String t = Jalali.todayStr();
        return Jalali.weekday(t) + " " + Jalali.shortLabel(t);
    }

    // ---------------- extra rows & helpers (used by screens) ----------------
    /** Small muted note line. */
    public TextView hint(String s) {
        TextView t = text(s, 10.5f, Theme.MUTED, false);
        t.setPadding(Theme.dp(4), 0, Theme.dp(4), 0);
        return t;
    }

    /** Array-backed chips row (gold). */
    public View chips(String[] items, int selected, ChipsListener l) {
        return chipsRow(java.util.Arrays.asList(items), selected, Theme.GOLD, l);
    }

    /** Primary gold button (short alias). */
    public Button btn(String t, View.OnClickListener onClick) {
        return btnGold(t, onClick);
    }

    /** Bare progress bar without labels. */
    public View progress(double frac, int color) {
        FrameLayout track = new FrameLayout(a);
        track.setBackground(Theme.pill(Theme.alpha(color, 60)));
        final View fill = new View(a);
        fill.setBackground(Theme.pill(color));
        track.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        final float fr = (float) Math.max(0, Math.min(1, frac));
        track.post(() -> {
            android.view.ViewGroup.LayoutParams p = fill.getLayoutParams();
            p.width = Math.max(Theme.dp(6), (int) (track.getWidth() * fr));
            fill.setLayoutParams(p);
        });
        track.setLayoutParams(lp(-1, Theme.dp(7)));
        return track;
    }

    /** Invoice row: title + party + date + total + settlement pill. status: settled/unsettled/other. */
    public View invoiceRow(String title, String party, String date, String total, String status, View.OnClickListener onClick) {
        String pill;
        int accent;
        if ("settled".equals(status)) {
            pill = "✓ تسویه";
            accent = Theme.SUCCESS;
        } else if ("unsettled".equals(status)) {
            pill = "مانده دارد";
            accent = Theme.DANGER;
        } else {
            pill = status == null ? "" : status;
            accent = Theme.MUTED;
        }
        return docRow(title, party, date, total, pill, accent, onClick);
    }

    /** Generic document row with a free pill. */
    public View docRow(String title, String mid, String date, String total, String pillText, View.OnClickListener onClick) {
        return docRow(title, mid, date, total, pillText, Theme.INFO, onClick);
    }

    public View docRow(String title, String mid, String date, String total, String pillText, int accent, View.OnClickListener onClick) {
        LinearLayout c = v();
        c.setBackground(Theme.cardAccent(accent));
        c.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        LinearLayout head = h();
        LinearLayout copy = v();
        TextView n = text(title, 14f, Theme.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(n, lp(-1, -2));
        if (mid != null && !mid.isEmpty()) {
            TextView s = text(mid, 11f, Theme.MUTED, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(s, lp(-1, -2));
        }
        head.addView(copy, wlp(1f));
        LinearLayout side = v();
        side.setGravity(Gravity.END);
        side.addView(text(total, 13.5f, Theme.GOLD_SOFT, true), lp(-2, -2));
        if (date != null && !date.isEmpty()) side.addView(text(date, 10.5f, Theme.MUTED, false), lp(-2, -2));
        if (pillText != null && !pillText.isEmpty()) {
            View p = pill(pillText, accent);
            LinearLayout.LayoutParams pp = lp(-2, -2);
            pp.setMargins(0, Theme.dp(4), 0, 0);
            side.addView(p, pp);
        }
        head.addView(side, lp(-2, -2));
        c.addView(head, lp(-1, -2));
        if (onClick != null) {
            Theme.pressable(c);
            c.setOnClickListener(onClick);
        }
        return c;
    }

    /** Inventory row: name + code/extra + stock + price. */
    public View invRow(String name, String code, String extra, String qty, String price, View.OnClickListener onClick) {
        LinearLayout c = v();
        c.setBackground(Theme.card());
        c.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        LinearLayout head = h();
        LinearLayout copy = v();
        TextView n = text(name, 14f, Theme.TEXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(n, lp(-1, -2));
        String sub = code == null ? "" : code;
        if (extra != null && !extra.isEmpty()) sub += (sub.isEmpty() ? "" : " • ") + extra;
        if (!sub.isEmpty()) {
            TextView s = text(sub, 10.5f, Theme.MUTED, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(s, lp(-1, -2));
        }
        head.addView(copy, wlp(1f));
        LinearLayout side = v();
        side.setGravity(Gravity.END);
        if (qty != null && !qty.isEmpty()) side.addView(text(qty, 12.5f, Theme.TEXT, true), lp(-2, -2));
        if (price != null && !price.isEmpty()) side.addView(text(price, 11f, Theme.GOLD_SOFT, false), lp(-2, -2));
        head.addView(side, lp(-2, -2));
        c.addView(head, lp(-1, -2));
        if (onClick != null) {
            Theme.pressable(c);
            c.setOnClickListener(onClick);
        }
        return c;
    }

    /** Styled input field. */
    public EditText edit(String hint, String value) {
        return edit(hint, value, false);
    }

    public EditText edit(String hint, String value, boolean password) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(14f);
        e.setTextColor(Theme.TEXT);
        e.setHintTextColor(Theme.MUTED);
        e.setTypeface(Theme.face(false));
        e.setBackground(Theme.fieldBg());
        e.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        e.setMinHeight(Theme.dp(48));
        e.setSingleLine(true);
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    /** Luxury field (v34): focus ring, 56dp touch height. */
    public EditText editLux(String hint, String value) {
        EditText e = edit(hint, value, false);
        e.setTextSize(14f);
        e.setBackground(Theme.fieldBg());
        e.setMinHeight(Theme.dp(56));
        e.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        return e;
    }

    /** Static vector icon (v34): one family (Material Symbols Rounded), decorative by default. */
    public ImageView icon(int res, int color, int dp) {
        ImageView v = new ImageView(a);
        try {
            android.graphics.drawable.Drawable d = a.getDrawable(res);
            if (d != null) {
                d = d.mutate();
                d.setTint(color);
                v.setImageDrawable(d);
            } else {
                v.setImageResource(res);
            }
        } catch (Exception ex) {
            try {
                v.setImageResource(res);
            } catch (Exception ignored) { }
        }
        int s = Theme.dp(dp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** Numeric PIN field (the 4-digit lock code). */
    public EditText editPin(String hint, String value) {
        EditText e = edit(hint, value, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return e;
    }

    /** Plain numeric field (port …). */
    public EditText editNum(String hint, String value) {
        EditText e = edit(hint, value, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    // ---------------- Jalali date picker ----------------
    public interface DateCb {
        void onPick(String ymd);
    }

    /** Jalali date picker dialog (year/month/day wheels, current values pre-selected). */
    public AlertDialog dateDialog(String title, String initial, final DateCb cb) {
        String norm = Jalali.normalizeDate(initial == null || initial.isEmpty() ? Jalali.todayStr() : initial);
        int y0, m0, d0;
        try {
            y0 = Integer.parseInt(norm.substring(0, 4));
            m0 = Integer.parseInt(norm.substring(5, 7));
            d0 = Integer.parseInt(norm.substring(8, 10));
        } catch (Exception e) {
            y0 = 1405; m0 = 1; d0 = 1;
        }
        LinearLayout root = v();
        root.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        LinearLayout row = h();
        row.setGravity(Gravity.CENTER);
        final android.widget.NumberPicker y = numPicker(1350, 1500, y0);
        final android.widget.NumberPicker mo = numPicker(1, 12, m0);
        final android.widget.NumberPicker d = numPicker(1, 31, d0);
        row.addView(wrapNumPicker(y, "سال"), wlp(1f));
        row.addView(space(6));
        row.addView(wrapNumPicker(mo, "ماه"), wlp(1f));
        row.addView(space(6));
        row.addView(wrapNumPicker(d, "روز"), wlp(1f));
        root.addView(row, lp(-1, -2));
        root.addView(gap(10));
        final AlertDialog[] box = new AlertDialog[1];
        root.addView(btnGold("✓ ثبت", v -> {
            int yy = y.getValue();
            int mm = mo.getValue();
            int dd = Math.min(d.getValue(), Jalali.daysInMonth(yy, mm));
            if (box[0] != null) box[0].dismiss();
            cb.onPick(String.format(java.util.Locale.US, "%04d/%02d/%02d", yy, mm, dd));
        }), lp(-1, -2));
        box[0] = dialog(title, root, true);
        box[0].show();
        return box[0];
    }

    private android.widget.NumberPicker numPicker(int min, int max, int val) {
        android.widget.NumberPicker p = new android.widget.NumberPicker(a);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(Math.max(min, Math.min(max, val)));
        p.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        try {
            String[] disp = new String[max - min + 1];
            for (int i = 0; i < disp.length; i++) disp[i] = Money.fa(String.valueOf(min + i));
            p.setDisplayedValues(disp);
            for (int i = 0; i < p.getChildCount(); i++) {
                View ch = p.getChildAt(i);
                if (ch instanceof EditText) ((EditText) ch).setTextColor(Theme.TEXT);
            }
        } catch (Exception ignored) { }
        return p;
    }

    private View wrapNumPicker(android.widget.NumberPicker p, String label) {
        LinearLayout c = v();
        c.setGravity(Gravity.CENTER);
        c.addView(text(label, 10f, Theme.MUTED, true), lp(-2, -2));
        c.addView(p, lp(-2, Theme.dp(110)));
        return c;
    }
}
