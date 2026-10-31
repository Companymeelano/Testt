package ir.meelano.manager.ui;

import ir.meelano.manager.core.Diag;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
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

import ir.meelano.manager.R;
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
    private boolean isOutcomeColor(int color) {
        return color == Theme.SUCCESS || color == Theme.DANGER
                || color == Theme.WARNING || color == Theme.INFO;
    }

    private boolean isDeltaCopy(String value) {
        String probe = value == null ? "" : value.trim();
        probe = probe.replace(String.valueOf((char) 0x2066), "")
                .replace(String.valueOf((char) 0x2067), "")
                .replace(String.valueOf((char) 0x2068), "")
                .replace(String.valueOf((char) 0x2069), "").trim();
        return probe.startsWith("▲") || probe.startsWith("▼") || probe.startsWith("↗")
                || probe.startsWith("↘") || probe.contains("تغییر")
                || probe.startsWith("افزایش") || probe.startsWith("کاهش")
                || probe.startsWith("افت ") || probe.startsWith("رشد ");
    }

    public TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s == null ? "" : s);
        // Legibility floor, then two multipliers. fontScale is the user's own zoom
        // choice; typeScale grows type with the screen so labels stay readable on the
        // large displays of high-end phones. Only type is scaled - layout metrics
        // stay in dp.
        //
        // v37.1: micro type (tile labels, captions, badges) is small ON PURPOSE — it
        // sits under a value inside a tile that has a fixed share of the row. The old
        // rule floored everything below 12.5 and then grew it again by typeScale, so a
        // 10sp caption rendered at ~14.8sp: the tiles overflowed, captions collided
        // with their values, and the value/label ratio collapsed from ~1.8x to ~1.3x,
        // which is what made the top of the dashboard read as a jumble. Micro type now
        // keeps its designed size and takes the user's zoom only — never the extra
        // screen-driven growth.
        boolean micro = sp < 12f;
        float base = micro ? (sp < 9f ? 9f : sp) : (sp < 12.5f ? 12.5f : sp);
        float grow = micro ? Theme.fontScale() : Theme.fontScale() * Theme.typeScale();
        t.setTextSize(base * grow);
        t.setTextColor(isOutcomeColor(color) && !isDeltaCopy(s) ? Theme.TEXT : color);
        t.setTypeface(Theme.face(bold));
        t.setFontFeatureSettings("tnum");
        t.setLineSpacing(Theme.dp(1.5f), 1.0f);
        // Lock the paragraph direction to RTL: a pure-digit value («۱۴۰۵/۰۸/۰۳», «۹٫۸»)
        // has no strong directional character, so first-strong heuristics fall back to
        // LTR and the value would slide to the left edge - visible inside dialogs where
        // the stock resolution differs from the activity window.
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        MeelanoIcons.iconize(t);
        return t;
    }

    public TextView title(String s) {
        // Section titles take the gold accent in the dark theme, matching the reference.
        return text(s, 15f, Theme.DARK ? Theme.GOLD_SOFT : Theme.TEXT, true);
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

    /** Shared luxury card surface: flat paper, 20dp corners, a single hairline, and low lift. */
    public LinearLayout luxCard() { return luxCard(0); }

    /** Accent-tinted LuxCard; accents identify a section, never a positive/negative outcome. */
    public LinearLayout luxCard(int accent) {
        LinearLayout c = v();
        c.setBackground(accent == 0 ? Theme.card() : Theme.cardAccent(accent));
        c.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        Theme.lift(c, Theme.cardElevation());
        return c;
    }

    /** Compatibility aliases: existing screens share the same LuxCard implementation. */
    public LinearLayout card() { return luxCard(); }
    public LinearLayout card(int accent) { return luxCard(accent); }

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
        int heroVector = MeelanoIcons.iconFor(glyph);
        if (heroVector != 0) {
            head.addView(iconBadge(heroVector, accent, 44),
                    new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));
        } else {
            TextView g = text(glyph, 24, Theme.sectionText(accent), true);
            g.setGravity(Gravity.CENTER);
            g.setBackground(Theme.avatar(Theme.alpha(accent, 44)));
            head.addView(g, new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));
        }
        head.addView(space(10));
        LinearLayout copy = v();
        copy.setPadding(Theme.dp(2), 0, 0, 0);
        copy.addView(title(title), lp(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(sub(subtitle), lp(-1, -2));
        head.addView(copy, wlp(1f));
        c.addView(head, lp(-1, -2));
        return c;
    }

    /** Shared SectionHeader: duotone mark, title, optional action, and one accent hairline. */
    public View sectionHeader(String title, int iconRes, int accent,
                              String actionLabel, View.OnClickListener action) {
        final int sectionHue = visualAccent(title, accent);
        LinearLayout root = v();
        LinearLayout row = h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setMinimumHeight(Theme.dp(48));

        if (iconRes != 0) {
            row.addView(iconBadge(iconRes, sectionHue, 36),
                    new LinearLayout.LayoutParams(Theme.dp(36), Theme.dp(36)));
            row.addView(space(8));
        }
        TextView heading = text(title, 14f, Theme.sectionText(sectionHue), true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(heading, wlp(1f));
        if (actionLabel != null && action != null) {
            TextView button = text(actionLabel, 10.5f, Theme.sectionText(sectionHue), true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(Theme.dp(9), Theme.dp(4), Theme.dp(9), Theme.dp(4));
            button.setMinHeight(Theme.dp(48));
            button.setMinWidth(Theme.dp(48));
            button.setBackground(Theme.ghostButton(sectionHue));
            button.setContentDescription(actionLabel.replace("›", "").replace("‹", "").trim()
                    + "؛ باز کردن");
            Theme.pressable(button);
            button.setOnClickListener(action);
            row.addView(space(6));
            row.addView(button, lp(-2, -2));
        }
        root.addView(row, lp(-1, -2));
        LinearLayout.LayoutParams rule = lp(-1, Theme.dp(1));
        rule.topMargin = Theme.dp(6);
        root.addView(accentHairline(sectionHue), rule);
        return root;
    }

    public View sectionHead(String title, String actionLabel, View.OnClickListener action) {
        int accent = sectionAccent(title);
        return sectionHeader(title, sectionIcon(title), accent, actionLabel, action);
    }

    private int sectionIcon(String title) {
        String s = title == null ? "" : title.replace('ي', 'ی').replace('ك', 'ک');
        if (s.contains("هشدار")) return R.drawable.mi_warning;
        // Dashboard comparisons and trends are analytics sections, not sale-category labels.
        if (s.contains("روند") || s.contains("مقایسه")) return R.drawable.mi_bar_chart;
        if (s.contains("برگشت")) return R.drawable.mi_assignment_return;
        if (s.contains("ویزیتور")) return R.drawable.mi_group;
        if (s.contains("مشتری") || s.contains("بدهکار") || s.contains("طرف‌حساب")) return R.drawable.mi_group;
        if (s.contains("انبار") || s.contains("موجودی") || s.contains("کالا")) return R.drawable.mi_inventory_2;
        if (s.contains("دریافت")) return R.drawable.mi_receipt_long;
        if (s.contains("پرداخت")) return R.drawable.mi_payments;
        if (s.contains("نقدینگی")) return R.drawable.mi_trending_up;
        if (s.contains("چک")) return R.drawable.mi_cheque;
        if (s.contains("سررسید")) return R.drawable.mi_calendar_month;
        if (s.contains("خرید")) return R.drawable.mi_shopping_cart;
        if (s.contains("فروش")) return R.drawable.mi_sell;
        if (s.contains("فاکتور")) return R.drawable.mi_receipt_long;
        if (s.contains("کاربر")) return R.drawable.mi_person;
        if (s.contains("گزارش") || s.contains("تحلیل") || s.contains("روند") || s.contains("عملکرد")) return R.drawable.mi_bar_chart;
        if (s.contains("تنظیم")) return R.drawable.mi_settings;
        if (s.contains("پیگیری")) return R.drawable.mi_calendar_month;
        if (s.contains("روز")) return R.drawable.mi_calendar_month;
        return R.drawable.mi_star_shine;
    }

    private int sectionAccent(String title) {
        return Theme.sectionAccent(title);
    }

    /** Keep legacy status hues out of section styling; outcome colors are reserved for deltas. */
    public int visualAccent(String title, int requestedAccent) {
        boolean outcome = requestedAccent == Theme.SUCCESS || requestedAccent == Theme.DANGER
                || requestedAccent == Theme.WARNING || requestedAccent == Theme.INFO;
        return outcome ? Theme.sectionAccent(title) : requestedAccent;
    }

    /** Compact shared KPI chip; money units stay smaller and quieter beside the value. */
    public View kpiChip(String label, String value, String unit, int accent) {
        return kpiChip(label, value, unit, accent, 0);
    }

    public View kpiChip(String label, String value, String unit, int accent, int iconRes) {
        int sectionHue = visualAccent(label, accent);
        LinearLayout chip = v();
        chip.setGravity(Gravity.CENTER_HORIZONTAL);
        chip.setBackground(Theme.kpi(sectionHue));
        chip.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        if (iconRes != 0) {
            LinearLayout.LayoutParams ip = lp(Theme.dp(32), Theme.dp(32));
            ip.gravity = Gravity.CENTER_HORIZONTAL;
            ip.bottomMargin = Theme.dp(4);
            chip.addView(iconBadge(iconRes, sectionHue, 32), ip);
        }
        TextView name = text(label, 10.5f, Theme.MUTED, false);
        name.setGravity(Gravity.CENTER);
        name.setMinLines(1);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        chip.addView(name, lp(-1, -2));

        LinearLayout amount = h();
        amount.setGravity(Gravity.CENTER);
        amount.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView figure = text(value, 16f, Theme.TEXT, true);
        figure.setSingleLine(true);
        figure.setGravity(Gravity.CENTER);
        float sizeScale = Theme.fontScale() * Theme.typeScale();
        figure.setAutoSizeTextTypeUniformWithConfiguration(
                Math.max(9, Math.round(10f * sizeScale)),
                Math.max(10, Math.round(16f * sizeScale)), 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        if (unit != null && !unit.trim().isEmpty()) {
            amount.addView(figure, wlp(1f));
            TextView unitView = text(unit, 9.5f, Theme.MUTED, false);
            unitView.setSingleLine(true);
            unitView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            amount.addView(space(4));
            amount.addView(unitView, lp(-2, -2));
        } else {
            amount.addView(figure, lp(-1, -2));
        }
        chip.addView(amount, lp(-1, -2));
        String spoken = (label == null ? "" : label) + ": " + (value == null ? "" : value)
                + (unit == null || unit.trim().isEmpty() ? "" : " " + unit);
        chip.setContentDescription(spoken);
        figure.setContentDescription(spoken);
        return chip;
    }

    /** Formatted monetary amount with a quieter, smaller toman/rial unit beside the figure. */
    public View amountLine(String formattedValue, float numberSp, int gravity) {
        return amountLine(formattedValue, numberSp, gravity, Theme.TEXT);
    }

    public View amountLine(String formattedValue, float numberSp, int gravity, int textColor) {
        String full = formattedValue == null ? "" : formattedValue.trim();
        String unit = "";
        String number = full;
        int split = full.lastIndexOf(' ');
        if (split > 0) {
            String tail = full.substring(split + 1).trim();
            if ("تومان".equals(tail) || "ریال".equals(tail)) {
                unit = tail;
                number = full.substring(0, split).trim();
            }
        }
        if (unit.isEmpty()) {
            TextView value = text(full, numberSp, textColor, true);
            value.setGravity(gravity);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            return value;
        }
        LinearLayout amount = h();
        amount.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        amount.setGravity(gravity | Gravity.CENTER_VERTICAL);
        TextView figure = text(number, numberSp, textColor, true);
        figure.setGravity(gravity);
        figure.setSingleLine(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            float sizeScale = numberSp < 12f
                    ? Theme.fontScale() : Theme.fontScale() * Theme.typeScale();
            int minimum = Math.max(9, Math.round(9f * sizeScale));
            int maximum = Math.max(minimum, Math.round(numberSp * sizeScale));
            figure.setAutoSizeTextTypeUniformWithConfiguration(minimum, maximum, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
        } else {
            figure.setEllipsize(TextUtils.TruncateAt.END);
        }
        amount.addView(figure, wlp(1f));
        amount.addView(space(4));
        TextView unitText = text(unit, 9.5f, Theme.MUTED, false);
        unitText.setSingleLine(true);
        unitText.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        amount.addView(unitText, lp(-2, -2));
        amount.setContentDescription(full);
        figure.setContentDescription(full);
        return amount;
    }

    /** A visual-only directional badge; callers supply direction and semantic tone. */
    public TextView deltaBadge(String label, int direction, int tone, String spokenDescription) {
        String marker = direction > 0 ? "▲ " : direction < 0 ? "▼ " : "";
        TextView badge = text(marker + (label == null ? "" : label), 10.5f,
                Theme.sectionText(tone), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        badge.setMinHeight(Theme.dp(32));
        badge.setBackground(Theme.pill(tone));
        badge.setContentDescription(spokenDescription == null || spokenDescription.trim().isEmpty()
                ? (direction > 0 ? "افزایش " : direction < 0 ? "کاهش " : "تغییر ") + label
                : spokenDescription);
        return badge;
    }

    // ---------------- KPI ----------------
    public static final class Kpi {
        public final String label;
        public final String value;
        public final String sub;
        public final int accent;
        public final Runnable action;
        /** Optional unabridged value announced by TalkBack and shown on long-press. */
        public String fullValue;
        /** Supporting count/document line below the amount. */
        public String detail;
        /** A neutral checked state for zero-risk counts such as no return documents. */
        public boolean neutralStatus;
        /** When not NaN, the tile counts up from 0 to this number. */
        public final double countTo;
        /** Optional daily series (oldest first) drawn as a sparkline under the value. */
        public double[] spark;
        /** Accessibility scope for the sparkline when it represents a non-daily comparison. */
        public String sparkDescription;
        /** Optional drawable shown above the label (0 = none). */
        public int icon = 0;
        /** True when the tile wears the emerald family of the manager's board. Off by default. */
        public boolean emeraldIcon = false;
        /** Optional semantic tone for a directional delta note; zero selects by direction. */
        public int subColor = 0;

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

    private int sectionAccentForKpi(Kpi k) {
        return visualAccent(k.label, k.accent);
    }

    public View kpiTile(Kpi k) {
        final int accent = sectionAccentForKpi(k);
        LinearLayout t = v();
        t.setBackground(Theme.kpi(accent));
        t.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        // Centred composition like the reference: icon, label, value and note share one axis.
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        if (k.icon != 0) {
            View badge = iconBadge(k.icon, accent, 32);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32));
            ip.gravity = Gravity.CENTER_HORIZONTAL;
            ip.bottomMargin = Theme.dp(4);
            t.addView(badge, ip);
        }
        TextView l = text(k.label, 10.5f, Theme.MUTED, false);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(l, lp(-1, -2));
        String fullDisplayValue = k.value == null ? "" : k.value;
        String moneyUnit = "";
        String numberDisplayValue = fullDisplayValue;
        int lastSpace = fullDisplayValue.lastIndexOf(' ');
        if (lastSpace > 0) {
            String possibleUnit = fullDisplayValue.substring(lastSpace + 1).trim();
            if ("تومان".equals(possibleUnit) || "ریال".equals(possibleUnit)) {
                moneyUnit = possibleUnit;
                numberDisplayValue = fullDisplayValue.substring(0, lastSpace).trim();
            }
        }
        final String stableNumberValue = numberDisplayValue;
        LinearLayout amount = h();
        amount.setGravity(Gravity.CENTER);
        amount.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView v = text(numberDisplayValue, 16.5f, Theme.TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        // Large amounts shrink to fit the tile instead of ending in "…".
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            float valueScale = Theme.fontScale() * Theme.typeScale();
            v.setAutoSizeTextTypeUniformWithConfiguration(
                    Math.max(9, Math.round(10f * valueScale)),
                    Math.max(10, Math.round(17f * valueScale)), 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP);
        } else {
            v.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (!moneyUnit.isEmpty()) {
            amount.addView(v, wlp(1f));
            amount.addView(space(4));
            TextView unit = text(moneyUnit, 9.5f, Theme.MUTED, false);
            unit.setSingleLine(true);
            unit.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            amount.addView(unit, lp(-2, -2));
        } else {
            amount.addView(v, lp(-1, -2));
        }
        t.addView(amount, lp(-1, -2));
        if (k.detail != null && !k.detail.trim().isEmpty()) {
            TextView detail = text(k.detail, 9f, Theme.MUTED, false);
            detail.setGravity(Gravity.CENTER_HORIZONTAL);
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            detail.setMinHeight(Theme.dp(20));
            t.addView(detail, lp(-1, -2));
        }
        if (k.spark != null && k.spark.length > 1) {
            LinearLayout.LayoutParams sp = lp(-1, Theme.dp(26));
            sp.setMargins(0, Theme.dp(4), 0, Theme.dp(2));
            Sparkline sparkline = new Sparkline(a, k.spark, accent);
            sparkline.setContentDescription(k.sparkDescription == null
                    ? "روند هفت‌روزهٔ " + k.label : k.sparkDescription);
            sparkline.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            t.addView(sparkline, sp);
        }
        if (!Double.isNaN(k.countTo) && Ui.motionOk(a)) {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(600);
            va.setInterpolator(new android.view.animation.DecelerateInterpolator());
            va.addUpdateListener(an -> v.setText(Money.fa(Money.compact(k.countTo * (float) an.getAnimatedValue()))));
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) { v.setText(stableNumberValue); }
            });
            va.start();
        } else {
            v.setText(stableNumberValue);
            if (Ui.motionOk(a)) {
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(350).start();
            } else v.setAlpha(1f);
        }
        // Every tile reserves equal space; outcome colors are restricted to actual deltas.
        String sub0 = k.sub == null ? "" : k.sub.trim();
        String probe = sub0.replace(String.valueOf((char) 0x2066), "")
                .replace(String.valueOf((char) 0x2067), "")
                .replace(String.valueOf((char) 0x2068), "")
                .replace(String.valueOf((char) 0x2069), "").trim();
        int direction = probe.startsWith("▲") || probe.startsWith("↗") || probe.startsWith("+") ? 1
                : probe.startsWith("▼") || probe.startsWith("↘") || probe.startsWith("-")
                || probe.startsWith("−") ? -1 : 0;
        if (k.neutralStatus) {
            TextView note = text(sub0.isEmpty() ? "✓" : sub0, 9.5f, Theme.MUTED, true);
            note.setGravity(Gravity.CENTER);
            note.setSingleLine(true);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setPadding(Theme.dp(7), Theme.dp(3), Theme.dp(7), Theme.dp(3));
            note.setMinHeight(Theme.dp(32));
            note.setBackground(Theme.pill(Theme.MUTED));
            t.addView(note, lp(-1, Theme.dp(32)));
        } else if (direction != 0) {
            String detail = sub0.replaceFirst("^(▲|▼|↗|↘)\\s*", "").trim();
            if (detail.startsWith("+") || detail.startsWith("−") || detail.startsWith("-"))
                detail = detail.substring(1).trim();
            int tone = k.subColor != 0 ? k.subColor
                    : direction > 0 ? Theme.SUCCESS : Theme.DANGER;
            TextView delta = deltaBadge(detail, direction, tone,
                    k.label + (direction > 0 ? " افزایش " : " کاهش ") + detail);
            delta.setSingleLine(true);
            delta.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(delta, lp(-1, Theme.dp(32)));
        } else {
            TextView note = text(sub0.isEmpty() ? " " : sub0, 10f, Theme.MUTED, false);
            note.setGravity(Gravity.CENTER_HORIZONTAL);
            note.setSingleLine(true);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setMinHeight(Theme.dp(32));
            t.addView(note, lp(-1, -2));
        }
        TextView more = text(k.action != null ? "مشاهده جزئیات ›" : " ", 9.5f, Theme.MUTED, true);
        more.setGravity(Gravity.CENTER_HORIZONTAL);
        more.setSingleLine(true);
        t.addView(more, lp(-1, -2));
        String complete = k.fullValue == null || k.fullValue.trim().isEmpty() ? k.value : k.fullValue;
        String spoken = k.label + ": " + (complete == null ? "" : complete)
                + (k.detail == null || k.detail.trim().isEmpty() ? "" : "؛ " + k.detail)
                + (sub0.isEmpty() ? "" : "؛ " + sub0);
        t.setContentDescription(spoken);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setTooltipText(spoken);
        View.OnLongClickListener reveal = vw -> {
            Toast.makeText(a, spoken, Toast.LENGTH_LONG).show();
            return true;
        };
        t.setOnLongClickListener(reveal);
        v.setContentDescription(complete == null ? "" : complete);
        v.setOnLongClickListener(reveal);
        if (k.action != null) {
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
            // Equal width (weight 1). Height stays wrap: all tiles share the same
            // single-line structure, so they come out equal without MATCH_PARENT pitfalls.
            LinearLayout.LayoutParams p = wlp(1f);
            if (i % span != span - 1) p.setMarginEnd(Theme.dp(10));
            row.addView(tile, p);
            // Value reveal: each KPI tile settles from 94% with a short stagger (skipped when
            // the system animations are off).
            if (Ui.motionOk(a)) {
                tile.setAlpha(0f);
                tile.setScaleX(0.94f);
                tile.setScaleY(0.94f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                        .setStartDelay(i * 60L).setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        }
        return root;
    }

    // ---------------- rows ----------------
    public View kv(String k, String v) {
        return kv(k, v, Theme.TEXT);
    }

    public View kv(String k, String v, int vColor) {
        // Label stacked above value, both full width and right-aligned (RTL start).
        // Side-by-side rows collided when the label and value were both long.
        LinearLayout r = v();
        r.setPadding(0, Theme.dp(7), 0, Theme.dp(7));
        TextView key = text(k, 11f, Theme.MUTED, false);
        key.setGravity(Gravity.RIGHT);
        r.addView(key, lp(-1, -2));
        View val = amountLine(v == null || v.isEmpty() ? "—" : v,
                13f, Gravity.RIGHT, vColor);
        val.setPadding(0, Theme.dp(2), 0, 0);
        r.addView(val, lp(-1, -2));
        return r;
    }

    /** Shared one-dp hairline; decorative only and omitted from the accessibility tree. */
    public View goldHairline() { return accentHairline(Theme.GOLD_METAL); }

    public View accentHairline(int accent) {
        View line = new View(a);
        line.setBackground(Theme.accentHairline(accent));
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(1)));
        return line;
    }

    public View divider() { return goldHairline(); }

    public View pill(String s, int color) {
        // Outcome hue belongs to the quiet surface; its label remains high-contrast neutral text.
        TextView t = text(s, 10.5f, Theme.TEXT, true);
        t.setPadding(Theme.dp(11), Theme.dp(5), Theme.dp(11), Theme.dp(5));
        t.setBackground(Theme.pill(color));
        t.setSingleLine(true);
        return t;
    }

    public View personRow(String name, String subLine, String value, String pillText, int accent, View.OnClickListener onClick) {
        return personRow(name, subLine, value, pillText, accent, onClick, null);
    }

    /** Same row; a real customer picture can replace the initials when a view is supplied. */
    public View personRow(String name, String subLine, String value, String pillText, int accent,
                          View.OnClickListener onClick, View avatar) {
        LinearLayout c = v();
        c.setBackground(Theme.cardAccent(accent));
        c.setPadding(Theme.dp(12), Theme.dp(11), Theme.dp(12), Theme.dp(11));
        LinearLayout head = h();
        View av;
        if (avatar != null) {
            av = avatar;
        } else {
            TextView initialsView = text(initials(name), 16, Theme.onAccent(accent), true);
            initialsView.setGravity(Gravity.CENTER);
            initialsView.setBackground(Theme.accentAvatar(accent));
            av = initialsView;
        }
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
        // Customer names often start with a code («04مهدي …»): take the first LETTER of each word,
        // so a digit never becomes the avatar.
        StringBuilder b = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (b.length() >= 2) break;
            for (int i = 0; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (Character.isLetter(ch)) { b.append(ch); break; }
            }
        }
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
        Theme.lift(c, Theme.cardElevation());
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

    /** Fraction bar: track + fill drawn in onDraw — exact at any size, no layout races. */
    public static final class FracBar extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private float frac;

        public FracBar(Activity a) {
            super(a);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        /** Fill grows smoothly from the right edge (RTL) when motion is allowed. */
        public void set(float target, int color, boolean animate) {
            final float f = Math.max(0f, Math.min(1f, target));
            fillPaint.setColor(color);
            trackPaint.setColor(Theme.alpha(color, 55));
            if (!animate || !ir.meelano.manager.ui.Ui.motionOk((Activity) getContext())) {
                frac = f;
                invalidate();
                return;
            }
            ValueAnimator anim = ValueAnimator.ofFloat(0f, f);
            anim.setDuration(520);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(v -> {
                frac = (float) v.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas g) {
            super.onDraw(g);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            box.set(0, 0, w, h);
            g.drawRoundRect(box, r, r, trackPaint);
            float fw = frac >= 0.999f ? w : w * frac;
            if (fw > 0 && frac > 0f) {
                fw = Math.max(fw, Math.min(w, r)); // keep the rounded cap visible
                box.set(w - fw, 0, w, h);
                g.drawRoundRect(box, r, r, fillPaint);
            }
        }
    }

    public View progressLine(String label, String value, double frac, int color) {
        LinearLayout root = v();
        LinearLayout head = h();
        head.addView(text(label, 11.5f, Theme.MUTED, false), wlp(1f));
        head.addView(text(value, 11.5f, Theme.TEXT, true), lp(-2, -2));
        root.addView(head, lp(-1, -2));
        FracBar bar = new FracBar(a);
        bar.set((float) frac, color, true);
        LinearLayout.LayoutParams tp = lp(-1, Theme.dp(7));
        tp.setMargins(0, Theme.dp(5), 0, 0);
        root.addView(bar, tp);
        return root;
    }

    // ---------------- buttons & chips ----------------
    public Button btnGold(String t, View.OnClickListener onClick) {
        Button b = new Button(a);
        b.setText(t);
        b.setTextSize(13.5f * Theme.fontScale());
        b.setTextColor(Theme.onAccent());
        b.setTypeface(Theme.face(true));
        b.setBackground(Theme.stated(Theme.goldButton()));
        b.setGravity(Gravity.CENTER);
        b.setLetterSpacing(0.015f);
        b.setElevation(Theme.dp(2));
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
        b.setTextSize(12.5f * Theme.fontScale());
        b.setTextColor(isOutcomeColor(accent) ? Theme.TEXT : Theme.sectionText(accent));
        b.setTypeface(Theme.face(true));
        b.setBackground(Theme.stated(Theme.ghostButton(accent)));
        b.setGravity(Gravity.CENTER);
        b.setElevation(Theme.dp(1));
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
        TextView c = text(t, 11.5f, selected ? Theme.onAccent(accent) : accent, true);
        c.setBackground(Theme.chip(selected, selected ? accent : Theme.alpha(accent, 255)));
        c.setPadding(Theme.dp(14), Theme.dp(8), Theme.dp(14), Theme.dp(8));
        c.setSingleLine(true);
        Theme.pressable(c);
        c.setOnClickListener(onClick);
        return c;
    }

    public View chipsRow(java.util.List<String> items, int selected, int accent, final ChipsListener l) {
        return chipsRow(items, selected, accent, null, l);
    }

    /** Icon chips in a horizontally scrollable row for long, open-ended option lists. */
    public View chipsWithIcons(String[] items, int selected, int[] iconRes,
                               int accent, final ChipsListener l) {
        return chipsRow(java.util.Arrays.asList(items), selected, accent, iconRes, l);
    }

    /** Equal-width, accessible segmented selector for a small fixed set of choices. */
    public View segmentedControl(String[] items, int selected, int[] iconRes,
                                 int accent, final ChipsListener listener) {
        LinearLayout group = h();
        group.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        group.setBackground(Theme.card());
        if (items == null) return group;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            final String label = items[i] == null ? "" : items[i];
            boolean chosen = i == selected;
            LinearLayout segment = v();
            segment.setGravity(Gravity.CENTER);
            segment.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            segment.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
            segment.setMinimumHeight(Theme.dp(48));
            segment.setBackground(chosen ? Theme.chip(true, accent)
                    : new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            int vector = iconRes != null && i < iconRes.length ? iconRes[i] : 0;
            if (vector != 0) {
                View mark = iconBadge(vector, accent, 28);
                LinearLayout.LayoutParams ip = lp(Theme.dp(28), Theme.dp(28));
                ip.gravity = Gravity.CENTER_HORIZONTAL;
                segment.addView(mark, ip);
            }
            TextView text = text(label, 10.5f, chosen ? Theme.onAccent(accent) : Theme.TEXT, true);
            text.setGravity(Gravity.CENTER);
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams tp = lp(-1, -2);
            if (vector != 0) tp.topMargin = Theme.dp(3);
            segment.addView(text, tp);
            segment.setContentDescription(label);
            segment.setSelected(chosen);
            segment.setFocusable(true);
            Theme.pressable(segment);
            segment.setOnClickListener(v -> listener.onPick(index));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.setMarginStart(Theme.dp(2));
            group.addView(segment, p);
        }
        return group;
    }

    private View chipsRow(java.util.List<String> items, int selected, int accent,
                          int[] icons, final ChipsListener l) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        // A chip that is cut off by the edge must read as "there is more", not as a broken
        // layout, so the content fades out where it leaves the row.
        sv.setHorizontalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Theme.dp(22));
        sv.setPadding(Theme.dp(2), 0, Theme.dp(2), 0);
        LinearLayout r = h();
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean chosen = i == selected;
            int iconRes = icons != null && i < icons.length ? icons[i] : 0;
            View ch = iconRes != 0
                    ? chipWithIcon(items.get(i), chosen, accent, iconRes, v -> l.onPick(idx))
                    : chip(items.get(i), chosen, accent, v -> l.onPick(idx));
            if (icons != null) ch.setMinimumHeight(Theme.dp(48));
            LinearLayout.LayoutParams p = lp(-2, -2);
            p.setMarginEnd(Theme.dp(8));
            r.addView(ch, p);
        }
        sv.addView(r, new HorizontalScrollView.LayoutParams(-2, -2));
        return sv;
    }

    private View chipWithIcon(String label, boolean selected, int accent, int iconRes,
                              View.OnClickListener onClick) {
        int ink = selected ? Theme.onAccent(accent) : accent;
        LinearLayout c = h();
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setBackground(Theme.chip(selected, selected ? accent : Theme.alpha(accent, 255)));
        c.setPadding(Theme.dp(10), Theme.dp(5), Theme.dp(12), Theme.dp(5));
        ImageView artwork = icon(iconRes, ink, 26);
        c.addView(artwork, lp(Theme.dp(26), Theme.dp(26)));
        c.addView(space(5), lp(Theme.dp(5), 1));
        TextView title = text(label, 11.5f, ink, true);
        title.setSingleLine(true);
        c.addView(title, lp(-2, -2));
        c.setContentDescription(label);
        c.setSelected(selected);
        c.setFocusable(true);
        Theme.pressable(c);
        c.setOnClickListener(onClick);
        return c;
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
        e.setTextSize(12.5f * Theme.fontScale());
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
            } catch (Exception ignored) { Diag.ignored("Kit:579", ignored); }
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
        } catch (Exception ignored) { Diag.ignored("Kit:603", ignored); }
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

    // ---------------- shared states ----------------
    /** Compact SyncPill used by dashboard and the collapsible application header. */
    public View syncPill(int iconRes, String title, String detail, int accent,
                         boolean busy, View.OnClickListener retry) {
        LinearLayout pill = h();
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setMinimumHeight(Theme.dp(48));
        pill.setPadding(Theme.dp(10), Theme.dp(6), Theme.dp(10), Theme.dp(6));
        pill.setBackground(Theme.kpi(accent));

        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 25 : 36)));
        ImageView symbol = icon(iconRes, Theme.sectionText(accent), 24);
        symbol.setContentDescription(null);
        badge.addView(symbol, new FrameLayout.LayoutParams(Theme.dp(24), Theme.dp(24), Gravity.CENTER));
        pill.addView(badge, new LinearLayout.LayoutParams(Theme.dp(34), Theme.dp(34)));
        pill.addView(space(8));

        LinearLayout copy = v();
        TextView heading = text(title, 11.5f, Theme.TEXT, true);
        heading.setMaxLines(1);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading, lp(-1, -2));
        if (detail != null && !detail.trim().isEmpty()) {
            TextView caption = text(detail, 9.5f, Theme.MUTED, false);
            caption.setMaxLines(1);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(caption, lp(-1, -2));
        }
        pill.addView(copy, wlp(1f));
        String spoken = (title == null ? "" : title)
                + (detail == null || detail.trim().isEmpty() ? "" : ". " + detail);
        pill.setContentDescription(spoken + (retry == null ? "" : "؛ لمس کنید برای تلاش دوباره"));
        if (busy && Ui.motionOk(a)) {
            android.animation.ObjectAnimator rotation = android.animation.ObjectAnimator.ofFloat(
                    symbol, View.ROTATION, 0f, 360f);
            rotation.setDuration(1150);
            rotation.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
            rotation.setInterpolator(new android.view.animation.LinearInterpolator());
            rotation.start();
            symbol.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) { rotation.cancel(); }
            });
        }
        if (retry != null) {
            Theme.pressable(pill);
            pill.setOnClickListener(retry);
        }
        return pill;
    }

    /** Calm, tactile one-line empty row for a section with no matching records. */
    public View emptyRow(String message) {
        LinearLayout row = h();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Theme.dp(48));
        row.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
        row.addView(iconBadge(R.drawable.mi_check_circle, Theme.SUCCESS, 32),
                new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32)));
        row.addView(space(8));
        TextView text = text(message == null || message.trim().isEmpty()
                ? "همه‌چیز مرتب است" : message, 11.5f, Theme.MUTED, false);
        text.setMaxLines(2);
        row.addView(text, wlp(1f));
        row.setContentDescription(message == null || message.trim().isEmpty()
                ? "همه‌چیز مرتب است" : message);
        return row;
    }

    /** Skeleton shimmer block. Its width is a fraction of a horizontal row. */
    public View skeletonBlock(int heightDp, float widthFraction) {
        View block = new View(a);
        int base = Theme.SURFACE2;
        int highlight = Theme.alpha(Theme.GOLD_METAL, Theme.isLight() ? 28 : 34);
        android.graphics.drawable.GradientDrawable shimmer = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Theme.alpha(base, 255), highlight, Theme.alpha(base, 255)});
        shimmer.setCornerRadius(Theme.dp(Math.max(6, Math.min(12, heightDp / 2))));
        shimmer.setStroke(Theme.dp(1), Theme.alpha(Theme.GOLD_METAL, Theme.isLight() ? 28 : 32));
        block.setBackground(shimmer);
        block.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (Ui.motionOk(a)) {
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(
                    block, View.ALPHA, 0.58f, 1f);
            pulse.setDuration(1250);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            block.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { pulse.start(); }
                @Override public void onViewDetachedFromWindow(View v) { pulse.cancel(); }
            });
        }
        float width = Math.max(0.1f, Math.min(1f, widthFraction));
        block.setLayoutParams(new LinearLayout.LayoutParams(0, Theme.dp(heightDp), width));
        return block;
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

    /**
     * Loading state: a skeleton that matches the real cards (title line, two text lines
     * and a figure block) with a slow pulse, plus the status text. Replaces the spinner
     * so the layout does not jump when content arrives.
     */
    public View loading(String msg) {
        LinearLayout c = luxCard();
        TextView title = text(msg, 12f, Theme.MUTED, false);
        c.addView(title, lp(-1, -2));
        int[] heights = {16, 12, 12, 44};
        float[] widths = {0.55f, 0.85f, 0.4f, 1f};
        for (int i = 0; i < heights.length; i++) {
            LinearLayout line = h();
            line.addView(skeletonBlock(heights[i], widths[i]));
            if (widths[i] < 1f)
                line.addView(new Space(a), new LinearLayout.LayoutParams(0, 1, 1f - widths[i]));
            LinearLayout.LayoutParams p = lp(-1, -2);
            p.topMargin = Theme.dp(i == 0 ? 12 : 8);
            c.addView(line, p);
        }
        c.setContentDescription(msg == null ? "در حال بارگذاری" : msg);
        return c;
    }

    /** Empty state: a small gold illustration, a title and a one-line guidance text. */
    public View empty(String title, String body) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Theme.dp(16), Theme.dp(18), Theme.dp(16), Theme.dp(18));
        c.addView(new ir.meelano.manager.ui.EmptyArt(a, ir.meelano.manager.ui.EmptyArt.EMPTY),
                new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96)));
        TextView t = text(title, 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = lp(-1, -2);
        tp.setMargins(0, Theme.dp(8), 0, 0);
        c.addView(t, tp);
        if (body != null && !body.isEmpty()) {
            TextView b = text(body, 11.5f, Theme.MUTED, false);
            b.setGravity(Gravity.CENTER);
            c.addView(b, lp(-1, -2));
        }
        return c;
    }

    /** Calm card for reports whose data source is not confirmed in this database (not a crash). */
    public View unavailable(String msg) {
        LinearLayout c = card(Theme.GOLD);
        TextView g = text("◈", 30, Theme.GOLD, true);
        g.setGravity(Gravity.CENTER);
        c.addView(g, lp(-1, -2));
        TextView t = text("این گزارش فعلاً در دسترس نیست", 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        c.addView(t, lp(-1, -2));
        TextView b = text(msg == null || msg.isEmpty() ? "ساختار دیتابیس برای این گزارش تأیید نشده است." : msg, 11.5f, Theme.MUTED, false);
        b.setGravity(Gravity.CENTER);
        c.addView(b, lp(-1, -2));
        TextView n = text("پس از تأیید ساختار دیتابیس فعال می‌شود؛ اعداد نادرست نمایش داده نمی‌شوند.", 11f, Theme.MUTED, false);
        n.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = lp(-1, -2);
        np.setMargins(0, Theme.dp(8), 0, 0);
        c.addView(n, np);
        return c;
    }

    /** Error state: alert illustration, the reason, a hint on what to check, and a retry button. */
    public View error(String msg, final Runnable retry) {
        LinearLayout c = card(Theme.DANGER);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(Theme.dp(16), Theme.dp(18), Theme.dp(16), Theme.dp(18));
        c.addView(new ir.meelano.manager.ui.EmptyArt(a, ir.meelano.manager.ui.EmptyArt.ERROR),
                new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96)));
        TextView t = text("خطا در دریافت اطلاعات", 14f, Theme.TEXT, true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = lp(-1, -2);
        tp.setMargins(0, Theme.dp(8), 0, 0);
        c.addView(t, tp);
        TextView b = text(msg == null || msg.isEmpty() ? "خطای نامشخص" : msg, 11.5f, Theme.MUTED, false);
        b.setGravity(Gravity.CENTER);
        c.addView(b, lp(-1, -2));
        TextView hint = text("اتصال شبکه و تنظیمات سرور را بررسی کنید، سپس دوباره تلاش کنید.", 11f, Theme.GOLD_SOFT, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = lp(-1, -2);
        hp.setMargins(0, Theme.dp(6), 0, 0);
        c.addView(hint, hp);
        if (retry != null) {
            Button r = btnGold("⇅ تلاش مجدد", v -> retry.run());
            LinearLayout.LayoutParams p = lp(-1, -2);
            p.setMargins(0, Theme.dp(12), 0, 0);
            c.addView(r, p);
        }
        return c;
    }

    public View pager(int page, boolean hasMore, final Runnable prev, final Runnable next) {
        return pagerRow(page, "صفحه " + Money.fa(String.valueOf(page + 1)), hasMore, prev, next);
    }

    /** Pager label backed by an exact filtered count when available. */
    public View pager(int page, long total, int pageSize, boolean totalKnown, boolean fallbackHasMore,
                      final Runnable prev, final Runnable next) {
        if (!totalKnown || pageSize <= 0) return pager(page, fallbackHasMore, prev, next);
        long pages = total <= 0 ? 0 : (total + pageSize - 1L) / pageSize;
        boolean hasMore = page + 1L < pages;
        String label = pages == 0 ? "بدون نتیجه"
                : "صفحه " + Money.fa(String.valueOf(page + 1)) + " از " + Money.fa(String.valueOf(pages));
        return pagerRow(page, label, hasMore, prev, next);
    }

    private View pagerRow(int page, String label, boolean hasMore,
                          final Runnable prev, final Runnable next) {
        LinearLayout r = h();
        r.setGravity(Gravity.CENTER);
        Button p = btnGhost("‹ قبلی", Theme.GOLD, v -> prev.run());
        p.setEnabled(page > 0);
        p.setAlpha(page > 0 ? 1f : 0.4f);
        r.addView(p, lp(-2, -2));
        r.addView(space(10));
        TextView t = text(label, 12f, Theme.MUTED, true);
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

    /**
     * Deluxe table that fits the card width. Columns share the width by weight (their
     * former dp widths become relative weights), so nothing is clipped at either edge and
     * no horizontal scroll is needed. Text columns wrap to two lines instead of truncating.
     */
    public View dataTable(ReportCatalog.Col[] cols, List<Row> rows, final RowClick click) {
        LinearLayout sv = v();
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.setBackground(Theme.card());
        sv.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        final int headInk = Theme.sectionText(Theme.GOLD_METAL);
        LinearLayout t = v();
        t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), Theme.dp(2));
        LinearLayout head = h();
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        head.setBackground(Theme.tableHeader());
        head.setPadding(Theme.dp(4), Theme.dp(9), Theme.dp(4), Theme.dp(9));
        TextView rn0 = text("#", 11.5f, headInk, true);
        rn0.setGravity(Gravity.CENTER);
        head.addView(rn0, new LinearLayout.LayoutParams(0, -2, 0.6f));
        for (ReportCatalog.Col c : cols) {
            TextView h = text(c.title, 11f, headInk, true);
            h.setGravity(Gravity.CENTER);
            h.setMaxLines(2);
            head.addView(h, new LinearLayout.LayoutParams(0, -2, colWeight(c)));
        }
        t.addView(head, lp(-1, -2));
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            LinearLayout row = h();
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setPadding(Theme.dp(4), Theme.dp(10), Theme.dp(4), Theme.dp(10));
            if (i % 2 == 1) row.setBackgroundColor(Theme.isNuts() ? 0x2EC9A24A : Theme.alpha(Theme.GOLD, Theme.DARK ? 16 : 30));
            TextView rn = text(Money.fa(String.valueOf(i + 1)), 11f, Theme.MUTED, true);
            rn.setGravity(Gravity.CENTER);
            row.addView(rn, new LinearLayout.LayoutParams(0, -2, 0.6f));
            for (ReportCatalog.Col c : cols) {
                double dv = (c.type == ReportCatalog.T_MONEY || c.type == ReportCatalog.T_NUM) ? r.d(c.key) : 0;
                int col = dv < -0.0001 ? Theme.DANGER
                        : (c.type == ReportCatalog.T_MONEY ? Theme.TEXT : (c.type == ReportCatalog.T_TEXT ? Theme.TEXT : Theme.MUTED));
                TextView cell = text(fmtCol(c, r), c.type == ReportCatalog.T_DATE ? 10.5f : 11.5f, col, c.type == ReportCatalog.T_MONEY);
                cell.setGravity(Gravity.CENTER);
                if (c.type == ReportCatalog.T_TEXT) {
                    cell.setMaxLines(2);
                    cell.setEllipsize(TextUtils.TruncateAt.END);
                } else {
                    cell.setSingleLine(true);
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, -2, colWeight(c)));
            }
            if (click != null) {
                Theme.pressable(row);
                row.setOnClickListener(v -> click.onRow(r));
            }
            t.addView(row, lp(-1, -2));
            if (i < rows.size() - 1) t.addView(divider(), lp(-1, Theme.dp(1)));
        }
        sv.addView(t, lp(-1, -2));
        return sv;
    }

    /** Relative width of a column: wider types get more room, so amounts never clip. */
    private float colWeight(ReportCatalog.Col c) {
        return colWidth(c) / 80f;
    }

    private int colWidth(ReportCatalog.Col c) {
        // Wide enough for «۱۰۲٬۷۵۰٬۰۰۰ تومان» at the largest type scale without clipping.
        if (c.type == ReportCatalog.T_MONEY) return 196;
        if (c.type == ReportCatalog.T_DATE) return 124; // «۱۴۰۵/۰۷/۱۸» must not clip on a phone
        if (c.type == ReportCatalog.T_NUM) return 80;
        return 140;
    }

    // ---------------- misc ----------------
    public void toast(String msg) {
        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show();
        try { Ui.haptic(a.getWindow().getDecorView(), !isFailureText(msg)); } catch (Exception ignored) { Diag.ignored("Kit:toastHaptic", ignored); }
    }

    /** Rough check so error toasts get the reject pattern instead of the success one. */
    private static boolean isFailureText(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        return m.contains("ممکن نشد") || m.contains("خطا") || m.contains("ناموفق")
                || m.contains("نشد") || m.contains("error") || m.contains("fail");
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
    public AlertDialog searchPicker(String title, final List<Row> rows, final String[] showKeys,
            final PickerListener onPick) {
        final List<Row> all = rows == null ? new ArrayList<Row>() : rows;
        LinearLayout root = v();
        root.setPadding(Theme.dp(16), Theme.dp(14), Theme.dp(16), Theme.dp(12));
        final android.widget.EditText q = edit("\u062c\u0633\u062a\u200c\u0648\u062c\u0648\u2026", "");
        try {
            q.setSingleLine(true);
            q.setTextDirection(View.TEXT_DIRECTION_RTL);
        } catch (Exception ignored) { Diag.ignored("Kit:841", ignored); }
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
                            try { box[0].dismiss(); } catch (Exception ignored) { Diag.ignored("Kit:871", ignored); }
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
        return box[0];
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
        if (body != null) body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        AlertDialog d = new AlertDialog.Builder(a)
                .setCustomTitle(tv)
                .setView(body)
                .setCancelable(cancelable)
                .create();
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(Theme.dialogBg());
        } catch (Exception ignored) { Diag.ignored("Kit:915", ignored); }
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
        try { dialog(title, root, true).show(); } catch (Exception ignored) { Diag.ignored("Kit:950", ignored); }
    }

    public ImageView logo(int sizeDp) {
        ImageView v = new ImageView(a);
        try {
            v.setImageResource(Theme.logoRes());
        } catch (Exception ignored) { Diag.ignored("Kit:957", ignored); }
        v.setAdjustViewBounds(true);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Gold ring framing: the artwork sits on a themed halo and is clipped to a circle,
        // so the crest reads as a premium badge on both light and dark palettes.
        try {
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(Theme.alpha(Theme.GOLD, 30));
            ring.setStroke(Theme.dp(2), Theme.alpha(Theme.GOLD, 220));
            v.setBackground(ring);
            v.setPadding(Theme.dp(3), Theme.dp(3), Theme.dp(3), Theme.dp(3));
            v.setClipToOutline(true);
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline o) {
                    o.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        } catch (Exception ignored) { Diag.ignored("Kit:976", ignored); }
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
    /**
     * Icon badges for the payment parts of one receipt (نقد، کارت، حواله، چک). Only parts
     * with a real amount appear, so a cheque-only receipt shows just the cheque badge.
     */
    public View docKinds(Row r) {
        FlowRow w = new FlowRow(a);
        addKind(w, "نقد", R.drawable.mi_payments, Theme.SUCCESS, r.d("cash"));
        addKind(w, "کارت", R.drawable.mi_credit_card, Theme.INFO, r.d("pos"));
        addKind(w, "حواله", R.drawable.mi_swap_horiz, Theme.VIOLET, r.d("havaleh"));
        addKind(w, "چک", R.drawable.mi_cheque, Theme.GOLD, r.d("cheque"));
        return w;
    }

    private void addKind(FlowRow w, String label, int icon, int color, double amount) {
        if (amount < 0.5) return;
        LinearLayout chip = h();
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(Theme.pill(Theme.alpha(color, Theme.isLight() ? 34 : 44)));
        chip.setPadding(Theme.dp(8), Theme.dp(3), Theme.dp(10), Theme.dp(3));
        chip.addView(icon(icon, color, 14), new LinearLayout.LayoutParams(Theme.dp(14), Theme.dp(14)));
        chip.addView(space(5));
        chip.addView(text(label, 10.5f, color, true), lp(-2, -2));
        w.addView(chip, lp(-2, -2));
    }

    public View docRow(String title, String mid, String date, String total, String pillText, View.OnClickListener onClick) {
        return docRow(title, mid, date, total, pillText, Theme.INFO, onClick);
    }

    public View docRow(String title, String mid, String date, String total, String pillText, int accent, View.OnClickListener onClick) {
        return docRow(title, mid, date, total, pillText, accent, null, onClick);
    }

    /** Document card with an optional footer (e.g. {@link #docKinds}) under its header line. */
    public View docRow(String title, String mid, String date, String total, String pillText, int accent,
                       View foot, View.OnClickListener onClick) {
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
        if (foot != null) {
            LinearLayout.LayoutParams fp = lp(-1, -2);
            fp.setMargins(0, Theme.dp(8), 0, 0);
            c.addView(foot, fp);
        }
        if (onClick != null) {
            Theme.pressable(c);
            c.setOnClickListener(onClick);
        }
        return c;
    }

    /** Inventory row: name + code/extra + stock + price. */
    public View invRow(String name, String code, String extra, String qty, String price, View.OnClickListener onClick) {
        return invRowTone(name, code, extra, qty, price, null, 0, onClick);
    }

    /** Neon status chip (موجود / کم‌موجود / ناموجود) in the given tone. */
    public TextView stockChip(String label, int tone) {
        TextView t = text(label, 10f, tone, true);
        t.setSingleLine(true);
        t.setPadding(Theme.dp(8), Theme.dp(2), Theme.dp(8), Theme.dp(2));
        t.setBackground(Theme.pill(tone));
        try {
            t.setShadowLayer(Theme.dp(4), 0, 0, Theme.alpha(tone, 150));
        } catch (Exception ignored) { Diag.ignored("Kit:chipGlow", ignored); }
        return t;
    }

    public View invRowTone(String name, String code, String extra, String qty, String price,
                           String status, int tone, View.OnClickListener onClick) {
        return invRowTone(name, code, extra, qty, price, status, tone, Theme.GOLD_SOFT, onClick);
    }

    public View invRowTone(String name, String code, String extra, String qty, String price,
                           String status, int tone, int priceColor, View.OnClickListener onClick) {
        return invRowTone(name, code, extra, qty, price, status, tone, priceColor, false, onClick);
    }

    /** Compact catalogue row: less padding, while preserving a large-font 48dp touch target. */
    public View invRowTone(String name, String code, String extra, String qty, String price,
                           String status, int tone, int priceColor, boolean compact,
                           View.OnClickListener onClick) {
        LinearLayout c = v();
        c.setBackground(Theme.card());
        c.setMinimumHeight(Theme.dp(48));
        int padV = compact ? 8 : 11;
        c.setPadding(Theme.dp(12), Theme.dp(padV), Theme.dp(12), Theme.dp(padV));
        LinearLayout head = h();
        LinearLayout copy = v();
        TextView n = text(name, compact ? 13.5f : 14f, Theme.TEXT, true);
        n.setMaxLines(compact ? 2 : 1);
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
        if (status != null && !status.isEmpty() && tone != 0) {
            side.addView(stockChip(status, tone), lp(-2, -2));
            side.addView(space(0), lp(-2, Theme.dp(4)));
        }
        if (qty != null && !qty.isEmpty()) side.addView(text(qty, compact ? 12f : 12.5f, Theme.TEXT, true), lp(-2, -2));
        if (price != null && !price.isEmpty()) side.addView(text(price, compact ? 10.5f : 11f, priceColor, false), lp(-2, -2));
        head.addView(side, lp(-2, -2));
        c.addView(head, lp(-1, -2));
        StringBuilder spoken = new StringBuilder(name == null ? "" : name);
        if (code != null && !code.isEmpty()) spoken.append(". ").append(code);
        if (qty != null && !qty.isEmpty()) spoken.append(". ").append(qty);
        if (price != null && !price.isEmpty()) spoken.append(". ").append(price);
        if (status != null && !status.isEmpty()) spoken.append(". ").append(status);
        c.setContentDescription(spoken.toString());
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
        e.setTextSize(14f * Theme.fontScale());
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
        e.setTextSize(14f * Theme.fontScale());
        e.setBackground(Theme.fieldBg());
        e.setMinHeight(Theme.dp(56));
        e.setPadding(Theme.dp(14), Theme.dp(12), Theme.dp(14), Theme.dp(12));
        return e;
    }

    /** One 24dp Material vector icon family; 3D artwork is reserved for hero/empty states. */
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
            } catch (Exception ignored) { Diag.ignored("Kit:icon", ignored); }
        }
        int s = Theme.dp(dp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** Duotone section mark: accent wash + a 24dp vector inside a small outlined circle. */
    public FrameLayout iconBadge(int iconRes, int accent, int sizeDp) {
        FrameLayout badge = new FrameLayout(a);
        badge.setBackground(Theme.avatar(Theme.alpha(accent, Theme.isLight() ? 28 : 42)));
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int inner = Math.min(24, Math.max(16, sizeDp - 8));
        ImageView symbol = icon(iconRes, Theme.sectionText(accent), inner);
        symbol.setContentDescription(null);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                Theme.dp(inner), Theme.dp(inner), Gravity.CENTER);
        badge.addView(symbol, p);
        return badge;
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
        } catch (Exception ignored) { Diag.ignored("Kit:1277", ignored); }
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
