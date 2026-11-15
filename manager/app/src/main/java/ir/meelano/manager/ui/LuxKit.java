package ir.meelano.manager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ir.meelano.manager.core.CountUp;

import ir.meelano.manager.core.HomeNumberFormatter;
import ir.meelano.manager.core.Money;

/**
 * «درخشان ۲.۰» component kit — C1…C13 of the design spec as independent, reusable views.
 *
 * Every factory takes an explicit {@link DerakhshanTokens.Surface} so the gallery can show
 * مخمل کرم and شب زمرد side by side; production screens pass
 * {@code DerakhshanTokens.theme(Theme.isLight() == false)}. No data logic lives here.
 */
public final class LuxKit {

    private LuxKit() { }

    /** C5 button kinds. */
    public static final int PRIMARY = 0;
    public static final int GOLD = 1;
    public static final int GHOST_GOLD = 2;
    public static final int QUIET = 3;

    /** C4 chip kinds. */
    public static final int CHIP_SOFT = 0;
    public static final int CHIP_SOLID = 1;
    public static final int CHIP_GOLD = 2;
    public static final int CHIP_GHOST = 3;

    public interface OnPick { void pick(int index); }

    // ---------------------------------------------------------------- C3 MedalIcon
    /** Duotone-ish medal: metallic family frame, 18% family wash behind a solid icon. */
    public static View medalIcon(Context c, DerakhshanTokens.Surface s, int iconRes, int family) {
        return medalIcon(c, s, iconRes, family, 40);
    }

    public static View medalIcon(Context c, DerakhshanTokens.Surface s, int iconRes, int family, int sizeDp) {
        FrameLayout medal = new FrameLayout(c);
        medal.setBackground(LuxDrawables.medal(s, family));
        medal.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView wash = new ImageView(c);
        wash.setBackgroundColor(DerakhshanTokens.blend(family, family, 0f) & 0x00FFFFFF
                | (int) (255 * 0.18f) << 24);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(-1, -1);
        medal.addView(wash, wp);
        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(glyphInk(family));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                Theme.dp(sizeDp * 0.5f), Theme.dp(sizeDp * 0.5f));
        ip.gravity = Gravity.CENTER;
        medal.addView(icon, ip);
        return medal;
    }

    // ---------------------------------------------------------------- C4 StatusChip
    public static TextView statusChip(Context c, DerakhshanTokens.Surface s, String text,
                                      int kind, int family, boolean dot) {
        TextView chip = txt(c, s, text, 10.5f, chipInk(s, kind, family), true);
        chip.setBackground(LuxDrawables.chipBg(s, kind, family));
        chip.setMinHeight(Theme.dp(DerakhshanTokens.CHIP_HEIGHT_DP));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Theme.dp(10), 0, Theme.dp(10), 0);
        if (dot) {
            android.graphics.drawable.GradientDrawable d =
                    new android.graphics.drawable.GradientDrawable();
            d.setColor(family);
            d.setCornerRadius(Theme.dp(3));
            d.setSize(Theme.dp(DerakhshanTokens.CHIP_DOT_DP), Theme.dp(DerakhshanTokens.CHIP_DOT_DP));
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
            chip.setCompoundDrawablePadding(Theme.dp(6));
        }
        return chip;
    }

    private static int chipInk(DerakhshanTokens.Surface s, int kind, int family) {
        // Filled chips pick black/white by measured contrast (white on amber measured 2.7:1).
        if (kind == CHIP_SOLID) return glyphInk(family);
        if (kind == CHIP_GOLD) return 0xFF2A2005;
        if (kind == CHIP_GHOST) return s.inkSecondary;
        // same shifts as Theme.dashboardAccentInk: every family clears 4.5:1 on its own wash
        return s.dark ? DerakhshanTokens.blend(family, 0xFFFFFFFF, 0.50f)
                : DerakhshanTokens.blend(family, 0xFF000000, 0.40f);
    }

    /** Contrast-picked black/white foreground for a saturated family plate (icons, solid chips). */
    public static int glyphInk(int family) {
        return DerakhshanTokens.contrastRatio(0xFF000000, family)
                >= DerakhshanTokens.contrastRatio(0xFFFFFFFF, family) ? 0xFF000000 : 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------- C5 Button
    public static Button luxButton(Context c, DerakhshanTokens.Surface s, String text,
                                   int kind, View.OnClickListener onClick) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTypeface(Theme.face(true));
        b.setTextSize(13.5f);
        b.setBackground(LuxDrawables.buttonBg(s, kind));
        b.setMinHeight(Theme.dp(kind == PRIMARY || kind == GOLD
                ? DerakhshanTokens.BUTTON_PRIMARY_HEIGHT_DP
                : DerakhshanTokens.BUTTON_GHOST_HEIGHT_DP));
        b.setMinimumHeight(b.getMinHeight());
        b.setPadding(Theme.dp(16), Theme.dp(8), Theme.dp(16), Theme.dp(8));
        b.setGravity(Gravity.CENTER);
        if (kind == PRIMARY) b.setTextColor(0xFFFFFFFF);
        else if (kind == GOLD) b.setTextColor(s.dark ? 0xFF2A2005 : 0xFFF7EFC8);
        else if (kind == GHOST_GOLD) b.setTextColor(s.goldText);
        else b.setTextColor(s.inkSecondary);
        b.setOnClickListener(onClick);
        return b;
    }

    /** Loading state: 14dp spinner in the icon slot, the label never moves. */
    public static void setLoading(final Button b, boolean loading) {
        if (loading) {
            SpinnerDrawable spin = new SpinnerDrawable(b);
            spin.setBounds(0, 0, Theme.dp(14), Theme.dp(14));
            b.setCompoundDrawablesRelative(spin, null, null, null);
            b.setCompoundDrawablePadding(Theme.dp(8));
            spin.start();
        } else {
            android.graphics.drawable.Drawable[] ds = b.getCompoundDrawablesRelative();
            if (ds[0] instanceof SpinnerDrawable) ((SpinnerDrawable) ds[0]).stop();
            b.setCompoundDrawablesRelative(null, null, null, null);
        }
    }

    /** 14dp ring spinner that animates through the view's drawable scheduling. */
    public static final class SpinnerDrawable extends android.graphics.drawable.Drawable
            implements Runnable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final View host;
        private float angle = 0f;
        private boolean running = false;

        public SpinnerDrawable(View host) {
            this.host = host;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dp(2));
            paint.setColor(DerakhshanTokens.GOLD_300);
        }

        public void start() {
            if (running) return;
            running = true;
            host.post(this);
        }

        public void stop() {
            running = false;
            host.removeCallbacks(this);
        }

        @Override public void run() {
            angle = (angle + 24f) % 360f;
            invalidateSelf();
            if (running) host.postOnAnimation(this);
        }

        @Override public void draw(Canvas canvas) {
            RectF r = new RectF(getBounds());
            r.inset(Theme.dp(1), Theme.dp(1));
            canvas.drawArc(r, angle, 300f, false, paint);
        }

        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    // ---------------------------------------------------------------- C1 HeroDataCard
    public static View heroDataCard(Context c, final DerakhshanTokens.Surface s,
                                    int icon, int family, String title,
                                    String badgeText, int badgeKind, String meta,
                                    String value, String unit,
                                    String deltaText, int deltaFamily,
                                    double[] spark,
                                    String primaryLabel, View.OnClickListener primaryL,
                                    String ghostLabel, View.OnClickListener ghostL) {
        LinearLayout card = v(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LuxDrawables.stone(s));
        card.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        // Brand gold edge: 2dp, 34% of the width, anchored inline-end, softly glowing.
        GoldEdge edge = new GoldEdge(c, s);
        card.addView(edge, new LinearLayout.LayoutParams(-1, Theme.dp(2)));
        LinearLayout head = h(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(medalIcon(c, s, icon, family), lp(Theme.dp(40), Theme.dp(40)));
        head.addView(space(c, 10));
        TextView t = txt(c, s, title, 18f, s.ink, true);
        head.addView(t, wlp(c, 1f));
        if (badgeText != null && !badgeText.isEmpty())
            head.addView(statusChip(c, s, badgeText, badgeKind, family, true));
        card.addView(head, match(c));
        card.addView(gap(c, 6));
        card.addView(txt(c, s, meta, 11.5f,
                DerakhshanTokens.blend(s.ink, s.stone[0], 1f - DerakhshanTokens.TYPE_META_INK_FRACTION),
                false), match(c));
        card.addView(gap(c, 8));
        LinearLayout valueRow = h(c);
        valueRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        TextView hero = txt(c, s, value, DerakhshanTokens.TYPE_HERO_SP, s.ink, true);
        goldNumber(hero, s);
        valueRow.addView(hero, wlp(c, 1f));
        if (unit != null && !unit.isEmpty())
            valueRow.addView(txt(c, s, unit, DerakhshanTokens.TYPE_UNIT_SP,
                    DerakhshanTokens.blend(s.ink, s.stone[0],
                            1f - DerakhshanTokens.TYPE_UNIT_INK_FRACTION), false), wrap(c));
        if (deltaText != null && !deltaText.isEmpty()) {
            valueRow.addView(space(c, 8));
            valueRow.addView(statusChip(c, s, deltaText, CHIP_SOFT, deltaFamily, false));
        }
        card.addView(valueRow, match(c));
        if (spark != null && spark.length >= 2) {
            LuxSpark sp = new LuxSpark(c, s, spark, family);
            sp.setContentDescription("روند نمونه");
            LinearLayout.LayoutParams spp = new LinearLayout.LayoutParams(-1,
                    Theme.dp(DerakhshanTokens.SPARKLINE_HEIGHT_DP));
            spp.topMargin = Theme.dp(8);
            card.addView(sp, spp);
        }
        LinearLayout actions = h(c);
        if (primaryLabel != null) actions.addView(luxButton(c, s, primaryLabel, PRIMARY, primaryL), wlp(c, 1f));
        if (primaryLabel != null && ghostLabel != null) actions.addView(space(c, 8));
        if (ghostLabel != null) actions.addView(luxButton(c, s, ghostLabel, GHOST_GOLD, ghostL), wlp(c, 1f));
        if (actions.getChildCount() > 0) {
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
            ap.topMargin = Theme.dp(10);
            card.addView(actions, ap);
        }
        return card;
    }

    /** Molten-gold gradient ink for hero numbers (tabular, both themes). */
    public static void goldNumber(final TextView view, final DerakhshanTokens.Surface s) {
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        view.post(new Runnable() {
            @Override public void run() {
                int w = Math.max(1, view.getWidth());
                int[] stops = s.heroGold;
                view.getPaint().setShader(new LinearGradient(0, 0, w, 0, stops,
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                view.invalidate();
            }
        });
        if (android.os.Build.VERSION.SDK_INT >= 26) view.setFontFeatureSettings("tnum");
    }

    /** 2px gold edge, 34% width, inline-end, with the brand glow. */
    public static final class GoldEdge extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final DerakhshanTokens.Surface s;

        public GoldEdge(Context c, DerakhshanTokens.Surface s) {
            super(c);
            this.s = s;
            paint.setShadowLayer(Theme.dp(DerakhshanTokens.GOLD_EDGE_GLOW_DP), 0, 0,
                    DerakhshanTokens.GOLD_EDGE_GLOW);
            // shadowLayer on shapes needs the software renderer; the view is 2dp tall so it is cheap.
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float edgeW = w * DerakhshanTokens.GOLD_EDGE_WIDTH_FRACTION;
            // RTL: inline-end sits on the left.
            paint.setShader(new LinearGradient(0, 0, edgeW, 0,
                    new int[]{DerakhshanTokens.GOLD_300, DerakhshanTokens.GOLD_500},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(0, 0, edgeW, h), h / 2f, h / 2f, paint);
        }
    }

    /** C1 sparkline: gradient area, rounded line, glowing last point. */
    public static final class LuxSpark extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final double[] data;
        private final int family;
        private final DerakhshanTokens.Surface s;

        public LuxSpark(Context c, DerakhshanTokens.Surface s, double[] data, int family) {
            super(c);
            this.s = s;
            this.data = data == null ? new double[0] : data;
            this.family = family;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Theme.dp(2));
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(family);
            glow.setColor(family);
            glow.setShadowLayer(Theme.dp(6), 0, 0, family);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || data.length < 2) return;
            double max = 1d, min = 0d;
            for (double d : data) { max = Math.max(max, d); min = Math.min(min, d); }
            float pad = Theme.dp(4);
            float[] xs = new float[data.length];
            float[] ys = new float[data.length];
            for (int i = 0; i < data.length; i++) {
                xs[i] = pad + (w - pad * 2) * (i / (float) (data.length - 1));
                ys[i] = h - pad - (h - pad * 2) * (float) ((data[i] - min) / (max - min + 1e-9));
            }
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < data.length; i++) path.lineTo(xs[i], ys[i]);
            area.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{DerakhshanTokens.blend(s.stone[0], family, 0.35f),
                            DerakhshanTokens.blend(s.stone[0], family, 0.02f)},
                    null, Shader.TileMode.CLAMP));
            android.graphics.Path fill = new android.graphics.Path(path);
            fill.lineTo(xs[data.length - 1], h);
            fill.lineTo(xs[0], h);
            fill.close();
            canvas.drawPath(fill, area);
            canvas.drawPath(path, line);
            canvas.drawCircle(xs[data.length - 1], ys[data.length - 1], Theme.dp(3.5f), glow);
        }
    }

    // ---------------------------------------------------------------- C2 StatRow
    public static View statRow(Context c, DerakhshanTokens.Surface s,
                               String[] labels, String[] values, String[] deltas, int[] tones) {
        LinearLayout card = h(c);
        card.setBackground(LuxDrawables.stone(s));
        card.setPadding(Theme.dp(4), Theme.dp(12), Theme.dp(4), Theme.dp(12));
        int n = labels == null ? 0 : labels.length;
        for (int i = 0; i < n; i++) {
            LinearLayout col = v(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(txt(c, s, labels[i], DerakhshanTokens.TYPE_LABEL_SP, s.inkSecondary, true), match(c));
            col.addView(gap(c, 4));
            TextView val = txt(c, s, values[i], 20f, s.ink, true);
            val.setGravity(Gravity.CENTER);
            col.addView(val, match(c));
            if (deltas != null && i < deltas.length && deltas[i] != null && !deltas[i].isEmpty()) {
                col.addView(gap(c, 4));
                TextView d = txt(c, s, deltas[i], 10.5f,
                        tones != null && i < tones.length ? tones[i] : s.inkSecondary, true);
                d.setGravity(Gravity.CENTER);
                col.addView(d, match(c));
            }
            card.addView(col, wlp(c, 1f));
            if (i < n - 1) {
                View div = new View(c);
                div.setBackgroundColor(s.hairline);
                card.addView(div, new LinearLayout.LayoutParams(Theme.dp(1), -1));
            }
        }
        return card;
    }

    // ---------------------------------------------------------------- C7 SegmentedControl
    public static View segmented(final Context c, final DerakhshanTokens.Surface s,
                                 final String[] options, int selected, final OnPick onPick) {
        final FrameLayout root = new FrameLayout(c);
        root.setBackground(LuxDrawables.segTrack(s));
        root.setPadding(Theme.dp(4), Theme.dp(4), Theme.dp(4), Theme.dp(4));
        final LinearLayout row = h(c);
        final View indicator = new View(c);
        indicator.setBackground(LuxDrawables.segIndicator(s));
        final TextView[] labels = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            labels[i] = txt(c, s, options[i], 12f, s.inkSecondary, true);
            labels[i].setGravity(Gravity.CENTER);
            labels[i].setMinHeight(Theme.dp(40));
            labels[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    moveIndicator(indicator, row, idx, options.length);
                    paintSegLabels(c, s, labels, idx);
                    if (onPick != null) onPick.pick(idx);
                }
            });
            row.addView(labels[i], wlp(c, 1f));
        }
        root.addView(indicator, new FrameLayout.LayoutParams(0, -1));
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));
        final int sel = Math.max(0, Math.min(options.length - 1, selected));
        paintSegLabels(c, s, labels, sel);
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                moveIndicator(indicator, row, sel, options.length);
            }
        });
        return root;
    }

    private static void paintSegLabels(Context c, DerakhshanTokens.Surface s,
                                       TextView[] labels, int selected) {
        for (int i = 0; i < labels.length; i++) {
            labels[i].setTextColor(i == selected ? 0xFFFFFFFF : s.inkSecondary);
        }
    }

    private static void moveIndicator(final View indicator, final LinearLayout row,
                                      final int index, int count) {
        if (count <= 0 || row.getWidth() <= 0) return;
        float slot = row.getWidth() / (float) count;
        // RTL: first option sits on the right.
        float left = row.getWidth() - slot * (index + 1);
        indicator.animate().cancel();
        indicator.setTranslationX(left);
        indicator.getLayoutParams().width = Math.round(slot);
        indicator.requestLayout();
        if (Ui.motionOk(row.getContext())) {
            indicator.setScaleX(0.96f);
            indicator.animate().scaleX(1f).setDuration(DerakhshanTokens.DOCK_TAB_LIFT_MS).start();
        }
    }

    // ---------------------------------------------------------------- C8 QuickTile
    public static View quickTile(Context c, DerakhshanTokens.Surface s, int icon, int family,
                                 String label, String micro, boolean hero,
                                 View.OnClickListener onClick) {
        LinearLayout tile = v(c);
        tile.setBackground(LuxDrawables.quickTile(s, hero));
        tile.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(12), Theme.dp(12));
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        int medalFamily = hero ? DerakhshanTokens.GOLD_500 : family;
        tile.addView(medalIcon(c, s, icon, medalFamily, 44),
                lp(Theme.dp(44), Theme.dp(44)));
        tile.addView(gap(c, 8));
        TextView l = txt(c, s, label, DerakhshanTokens.TYPE_LABEL_SP,
                hero ? 0xFFFFFFFF : s.ink, true);
        l.setGravity(Gravity.CENTER);
        tile.addView(l, match(c));
        if (micro != null && !micro.isEmpty()) {
            tile.addView(gap(c, 4));
            TextView m = txt(c, s, micro, 10.5f,
                    hero ? DerakhshanTokens.GOLD_100 : s.inkSecondary, false);
            m.setGravity(Gravity.CENTER);
            tile.addView(m, match(c));
        }
        tile.setOnClickListener(onClick);
        tile.setClickable(true);
        return tile;
    }

    // ---------------------------------------------------------------- C9 Gauge
    public static View gauge(Context c, DerakhshanTokens.Surface s, double value, double total,
                             String label) {
        return new LuxGaugeView(c, s, value, total, label);
    }

    /** Golden half-ring with the counted number at its heart. */
    public static final class LuxGaugeView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final DerakhshanTokens.Surface s;
        private final double total;
        private final String label;
        private double shown = 0d;

        public LuxGaugeView(Context c, DerakhshanTokens.Surface s, double value, double total,
                            String label) {
            super(c);
            this.s = s;
            this.total = Math.max(1d, total);
            this.label = label;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(Theme.dp(8));
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(LuxDrawables.gaugeTrackColor(s));
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(Theme.dp(8));
            arc.setStrokeCap(Paint.Cap.ROUND);
            ink.setColor(s.ink);
            ink.setTextSize(Theme.dp(24));
            ink.setTypeface(Theme.face(true));
            ink.setTextAlign(Paint.Align.CENTER);
            sub.setColor(s.inkSecondary);
            sub.setTextSize(Theme.dp(11));
            sub.setTypeface(Theme.face(false));
            sub.setTextAlign(Paint.Align.CENTER);
            setMinimumHeight(Theme.dp(120));
            animateTo(value);
        }

        private void animateTo(final double value) {
            if (!Ui.motionOk(getContext())) {
                shown = value;
                invalidate();
                return;
            }
            android.animation.ValueAnimator an = android.animation.ValueAnimator.ofFloat(0f, 1f);
            an.setDuration(DerakhshanTokens.COUNT_UP_MS);
            an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    shown = value * CountUp.ease((float) a.getAnimatedValue());
                    invalidate();
                }
            });
            an.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float pad = Theme.dp(12);
            RectF box = new RectF(pad, pad, w - pad, h - pad - Theme.dp(18));
            canvas.drawArc(box, 180f, 180f, false, track);
            float fraction = (float) Math.max(0d, Math.min(1d, shown / total));
            if (fraction > 0.001f) {
                arc.setShader(new LinearGradient(box.left, 0, box.right, 0,
                        s.dark ? DerakhshanTokens.GOLD_MATERIAL_DARK
                                : DerakhshanTokens.GOLD_MATERIAL_LIGHT,
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawArc(box, 180f, 180f * fraction, false, arc);
            }
            String number = Money.fa(String.valueOf(Math.round(shown))) + " از "
                    + Money.fa(String.valueOf(Math.round(total)));
            canvas.drawText(number, w / 2f, box.centerY() + Theme.dp(4), ink);
            canvas.drawText(label == null ? "" : label, w / 2f, box.centerY() + Theme.dp(22), sub);
        }
    }

    // ---------------------------------------------------------------- C10 BalanceCard
    public static View balanceCard(Context c, DerakhshanTokens.Surface s,
                                   String recvLabel, String recvValue,
                                   String payLabel, String payValue,
                                   String recvCta, View.OnClickListener recvL,
                                   String payCta, View.OnClickListener payL) {
        LinearLayout card = v(c);
        card.setBackground(LuxDrawables.stone(s));
        card.setPadding(Theme.dp(14), Theme.dp(14), Theme.dp(14), Theme.dp(14));
        LinearLayout pans = h(c);
        pans.addView(balancePan(c, s, recvLabel, recvValue, DerakhshanTokens.SUCCESS, recvCta, recvL),
                wlp(c, 1f));
        View center = new View(c);
        center.setBackgroundColor(DerakhshanTokens.GOLD_500);
        pans.addView(center, new LinearLayout.LayoutParams(Theme.dp(2), -1));
        pans.addView(balancePan(c, s, payLabel, payValue, DerakhshanTokens.WARNING, payCta, payL),
                wlp(c, 1f));
        card.addView(pans, match(c));
        return card;
    }

    private static View balancePan(Context c, DerakhshanTokens.Surface s, String label,
                                   String value, int family, String cta,
                                   View.OnClickListener l) {
        LinearLayout pan = v(c);
        pan.setGravity(Gravity.CENTER_HORIZONTAL);
        pan.setPadding(Theme.dp(8), Theme.dp(4), Theme.dp(8), Theme.dp(4));
        TextView lt = txt(c, s, label, DerakhshanTokens.TYPE_LABEL_SP, s.inkSecondary, true);
        lt.setGravity(Gravity.CENTER);
        pan.addView(lt, match(c));
        pan.addView(gap(c, 6));
        TextView vt = txt(c, s, value, 20f, s.dark ? DerakhshanTokens.blend(family, 0xFFFFFFFF, 0.3f)
                : DerakhshanTokens.blend(family, 0xFF000000, 0.2f), true);
        vt.setGravity(Gravity.CENTER);
        pan.addView(vt, match(c));
        pan.addView(gap(c, 8));
        pan.addView(luxButton(c, s, cta, GHOST_GOLD, l), match(c));
        return pan;
    }

    // ---------------------------------------------------------------- C11 EmptyState
    public static View emptyState(Context c, DerakhshanTokens.Surface s, int icon, int family,
                                  String title, String oneLine, String cta,
                                  View.OnClickListener l) {
        LinearLayout box = v(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Theme.dp(16), Theme.dp(20), Theme.dp(16), Theme.dp(20));
        FrameLayout art = new FrameLayout(c);
        art.setBackground(LuxDrawables.medalArt(s, family));
        ImageView ic = new ImageView(c);
        ic.setImageResource(icon);
        ic.setColorFilter(s.dark ? DerakhshanTokens.blend(family, 0xFFFFFFFF, 0.35f)
                : DerakhshanTokens.blend(family, 0xFF000000, 0.15f));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Theme.dp(52), Theme.dp(52));
        ip.gravity = Gravity.CENTER;
        art.addView(ic, ip);
        box.addView(art, lp(Theme.dp(DerakhshanTokens.EMPTY_STATE_ART_DP),
                Theme.dp(DerakhshanTokens.EMPTY_STATE_ART_DP)));
        box.addView(gap(c, 12));
        TextView t = txt(c, s, title, 15f, s.ink, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, match(c));
        if (oneLine != null && !oneLine.isEmpty()) {
            box.addView(gap(c, 4));
            TextView o = txt(c, s, oneLine, 13f,
                    DerakhshanTokens.blend(s.ink, s.stone[0], 0.35f), false);
            o.setGravity(Gravity.CENTER);
            o.setMaxLines(1);
            box.addView(o, match(c));
        }
        if (cta != null && !cta.isEmpty()) {
            box.addView(gap(c, 12));
            box.addView(luxButton(c, s, cta, PRIMARY, l), wrap(c));
        }
        return box;
    }

    // ---------------------------------------------------------------- C13 AlertStrip
    public static View alertStrip(Context c, DerakhshanTokens.Surface s, String title, String sub,
                                  String action, View.OnClickListener l) {
        LinearLayout strip = h(c);
        strip.setBackground(LuxDrawables.alertBg(s));
        strip.setPadding(0, Theme.dp(10), Theme.dp(10), Theme.dp(10));
        strip.setGravity(Gravity.CENTER_VERTICAL);
        View edge = new View(c);
        edge.setBackgroundColor(DerakhshanTokens.WARNING);
        strip.addView(edge, new LinearLayout.LayoutParams(
                Theme.dp(DerakhshanTokens.ALERT_EDGE_DP), -1));
        strip.addView(space(c, 10));
        strip.addView(medalIcon(c, s, ir.meelano.manager.R.drawable.mi_warning,
                DerakhshanTokens.WARNING, 34), lp(Theme.dp(34), Theme.dp(34)));
        strip.addView(space(c, 10));
        LinearLayout texts = v(c);
        texts.addView(txt(c, s, title, 12.5f, s.ink, true), match(c));
        if (sub != null && !sub.isEmpty()) {
            texts.addView(gap(c, 2));
            TextView st = txt(c, s, sub, 10.5f,
                    DerakhshanTokens.blend(s.ink, s.stone[0],
                            1f - DerakhshanTokens.TYPE_META_INK_FRACTION), false);
            st.setMaxLines(2);
            texts.addView(st, match(c));
        }
        strip.addView(texts, wlp(c, 1f));
        if (action != null && !action.isEmpty()) {
            strip.addView(space(c, 8));
            strip.addView(luxButton(c, s, action, QUIET, l), wrap(c));
        }
        return strip;
    }

    // ---------------------------------------------------------------- C6 CommandDock
    public static View commandDock(Context c, final DerakhshanTokens.Surface s,
                                   int[] icons, String[] labels, int selected,
                                   final OnPick onPick) {
        final LinearLayout dock = h(c);
        dock.setBackground(LuxDrawables.dockGlass(s));
        dock.setPadding(Theme.dp(8), Theme.dp(10), Theme.dp(8), Theme.dp(8));
        dock.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean active = i == selected;
            LinearLayout tab = v(c);
            tab.setGravity(Gravity.CENTER_HORIZONTAL);
            final FrameLayout frame = new FrameLayout(c);
            if (active) {
                frame.setBackground(LuxDrawables.medal(s, DerakhshanTokens.EMERALD_500));
                frame.setTranslationY(-Theme.dp(DerakhshanTokens.DOCK_TAB_LIFT_DP));
            }
            ImageView ic = new ImageView(c);
            ic.setImageResource(icons[i]);
            ic.setColorFilter(active ? DerakhshanTokens.GOLD_100
                    : DerakhshanTokens.blend(s.ink, s.stone[0], 0.32f));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Theme.dp(20), Theme.dp(20));
            ip.gravity = Gravity.CENTER;
            frame.addView(ic, ip);
            tab.addView(frame, lp(Theme.dp(36), Theme.dp(32)));
            TextView l = txt(c, s, labels[i], 10.5f,
                    active ? s.goldText : DerakhshanTokens.blend(s.ink, s.stone[0], 0.32f), true);
            l.setGravity(Gravity.CENTER);
            tab.addView(l, match(c));
            if (active) {
                View underline = new View(c);
                underline.setBackgroundColor(DerakhshanTokens.GOLD_500);
                LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(Theme.dp(18), Theme.dp(2));
                up.topMargin = Theme.dp(2);
                tab.addView(underline, up);
            }
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (onPick != null) onPick.pick(idx);
                }
            });
            dock.addView(tab, wlp(c, 1f));
        }
        return dock;
    }

    // ---------------------------------------------------------------- view helpers
    private static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private static TextView txt(Context c, DerakhshanTokens.Surface s, String text,
                                float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Theme.face(bold));
        return t;
    }

    private static View space(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(dp), Theme.dp(dp)));
        return v;
    }

    private static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Theme.dp(dp)));
        return v;
    }

    private static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private static LinearLayout.LayoutParams match(Context c) {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private static LinearLayout.LayoutParams wrap(Context c) {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private static LinearLayout.LayoutParams wlp(Context c, float weight) {
        return new LinearLayout.LayoutParams(0, -2, weight);
    }

    /**
     * The page's single «فوری» pulse: a decorative marker breathes between 30% and 50%
     * opacity over 2400ms. Reduce-motion keeps it static at full opacity.
     */
    public static void pulseUrgent(final View marker) {
        if (marker == null) return;
        if (!Ui.motionOk(marker.getContext())) return;
        android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(
                marker, "alpha", DerakhshanTokens.URGENT_PULSE_FROM, DerakhshanTokens.URGENT_PULSE_TO);
        an.setDuration(DerakhshanTokens.URGENT_PULSE_MS / 2L);
        an.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        an.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        an.start();
    }

    /** Count-up for any Derakhshan figure, honoring reduce-motion. */
    public static void countUp(final TextView target, final String finalText) {
        if (target == null || !Ui.motionOk(target.getContext())) return;
        if (!CountUp.countable(finalText)) return;
        target.setText(CountUp.sweep(finalText, 0d));
        android.animation.ValueAnimator an = android.animation.ValueAnimator.ofFloat(0f, 1f);
        an.setDuration(DerakhshanTokens.COUNT_UP_MS);
        an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                target.setText(CountUp.sweep(finalText, (float) a.getAnimatedValue()));
            }
        });
        an.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                target.setText(finalText);
            }
        });
        an.start();
    }

    /** Localized, isolated render of a figure for chips and micros. */
    public static String fa(String s) {
        return HomeNumberFormatter.localizeText(Money.fa(s));
    }
}
