package ir.meelano.android;

/**
 * Central visual language tokens for the native Meelano Android app.
 * The main Activity still owns Android Views, but semantic glyphs, labels and
 * beauty hints live here so every section can share one design vocabulary.
 */
final class MeelanoDesignKit {
    private MeelanoDesignKit() {}

    static String glyph(String key) {
        if ("dashboard".equals(key)) return "⌂";
        if ("customers".equals(key)) return "♙";
        if ("products".equals(key)) return "◍";
        if ("reports".equals(key) || "visitor_reports".equals(key)) return "↗";
        if ("command".equals(key)) return "⌘";
        if ("assistant".equals(key)) return "✦";
        if ("chat".equals(key)) return "✉";
        if ("personnel".equals(key)) return "🪪";
        if ("attendance".equals(key)) return "⏱";
        if ("taxpayers".equals(key)) return "٪";
        if ("cameras".equals(key)) return "▣";
        if ("alarm".equals(key)) return "◬";
        if ("visitor_dashboard".equals(key)) return "◎";
        if ("showcase".equals(key)) return "◈";
        if ("cart".equals(key)) return "⊕";
        if ("settings".equals(key)) return "⚙";
        if ("management".equals(key)) return "♛";
        if ("health".equals(key)) return "◌";
        return "◆";
    }

    static String label(String key) {
        if ("dashboard".equals(key)) return "داشبورد";
        if ("customers".equals(key)) return "مشتریان";
        if ("products".equals(key)) return "کالاها";
        if ("reports".equals(key) || "visitor_reports".equals(key)) return "گزارشات";
        if ("command".equals(key)) return "فرماندهی";
        if ("assistant".equals(key)) return "دستیار";
        if ("chat".equals(key)) return "گفتگو";
        if ("personnel".equals(key)) return "پرسنل";
        if ("attendance".equals(key)) return "حضور";
        if ("taxpayers".equals(key)) return "مودیان";
        if ("cameras".equals(key)) return "دوربین";
        if ("alarm".equals(key)) return "دزدگیر";
        if ("visitor_dashboard".equals(key)) return "ماموریت";
        if ("showcase".equals(key)) return "کالا";
        if ("cart".equals(key)) return "سبد";
        if ("settings".equals(key)) return "تنظیمات";
        if ("management".equals(key)) return "مدیریت";
        if ("health".equals(key)) return "اتصال";
        return key == null || key.trim().isEmpty() ? "بخش" : key;
    }

    static String beautyHint(String key) {
        if ("dashboard".equals(key)) return "نمای مدیریتی glass با KPIهای سریع";
        if ("customers".equals(key)) return "کارت مشتری با ریسک، تماس و اولویت وصول";
        if ("products".equals(key)) return "کارت کالا با تصویر، قیمت و نمودار ریزگردش";
        if ("showcase".equals(key)) return "کالای مشتری‌محور، سریع و آماده ارائه";
        if ("cart".equals(key)) return "سبد ساده پیش‌فاکتور برای ویزیتور";
        if ("visitor_dashboard".equals(key)) return "ماموریت، مسیر، کالا و پیش‌فاکتور بدون بخش اضافه";
        if ("personnel".equals(key)) return "پرونده پرسنلی با خلاصه مالی و حضور";
        if ("reports".equals(key) || "visitor_reports".equals(key)) return "گزارشات طلایی ویزیتور، فاکتورهای من و صف آفلاین";
        if ("taxpayers".equals(key)) return "ارسال سازمانی با وضعیت روشن";
        if ("cameras".equals(key) || "alarm".equals(key)) return "کنترل سخت‌افزار با کارت وضعیت";
        if ("settings".equals(key)) return "آزمایشگاه تم، حرکت و امنیت محلی";
        return "زبان بصری یکپارچه پخش درخشان";
    }

    /** Unified vector icon system (phase 7A/7H): every text/emoji glyph used anywhere in the UI maps
        to a monochrome brand vector so icons render identically on all devices and always carry the
        theme tint. Unmapped strings (initials, «CEO», numbers) return 0 and fall back to text. */
    static int iconRes(String g) {
        if (g == null) return 0;
        switch (g) {
            case "↗": return R.drawable.lux_trending_up;
            case "↘": case "⇩": case "▼": return R.drawable.mi_trending_down;
            case "▲": return R.drawable.mi_trending_up;
            case "✓": case "✅": return R.drawable.lux_check_circle;
            case "★": return R.drawable.lux_star_fill;
            case "✨": case "✦": case "✧": return R.drawable.lux_star_shine;
            case "\ud83c\udfc6": case "\ud83c\udfc5": return R.drawable.mi_military_tech;
            case "⌛": return R.drawable.mi_hourglass_top;
            case "☀": return R.drawable.mi_light_mode;
            case "☾": case "\ud83c\udf19": return R.drawable.mi_bedtime;
            case "⛅": case "\ud83c\udf05": return R.drawable.mi_wb_twilight;
            case "◉": case "◎": return R.drawable.mi_track_changes;
            case "◷": return R.drawable.mi_schedule;
            case "⏱": return R.drawable.mi_timer;
            case "!": case "⚠": case "⚠️": return R.drawable.lux_warning;
            case "♜": case "\ud83d\udc65": return R.drawable.lux_group;
            case "♟": case "\ud83d\udc64": case "\ud83e\uddd1": return R.drawable.lux_person;
            case "♙": return R.drawable.lux_account_balance_wallet;
            case "◆": return R.drawable.lux_diamond;
            case "◈": return R.drawable.mi_inventory_2;
            case "◍": case "▣": return R.drawable.mi_grid_view;
            case "≡": return R.drawable.mi_receipt_long;
            case "↯": case "\ud83d\udd04": return R.drawable.mi_bolt;
            case "⇅": return R.drawable.mi_compare_arrows;
            case "⟳": return R.drawable.lux_refresh;
            case "↩": return R.drawable.mi_assignment_return;
            case "٪": return R.drawable.mi_percent;
            case "\ud83d\udca1": return R.drawable.mi_lightbulb;
            case "\ud83c\udff7": case "\ud83c\udff7\ufe0f": return R.drawable.mi_sell;
            case "⌂": return R.drawable.mi_home;
            case "☰": return R.drawable.mi_apps;
            case "⚙": case "⚙️": return R.drawable.lux_admin_panel_settings;
            case "⌕": return R.drawable.lux_search;
            case "✺": return R.drawable.mi_palette;
            case "♛": return R.drawable.mi_verified_user;
            case "⎋": return R.drawable.mi_logout;
            case "⊕": return R.drawable.mi_add_shopping_cart;
            case "●": return R.drawable.mi_fiber_manual_record_fill;
            case "›": return R.drawable.mi_chevron_left;
            case "‹": return R.drawable.mi_arrow_forward;
            case "➜": return R.drawable.mi_arrow_forward;
            case "☝": return R.drawable.mi_fingerprint;
            case "\ud83d\ude9a": case "\ud83d\ude97": return R.drawable.mi_local_shipping;
            case "\ud83d\udcb5": return R.drawable.lux_payments;
            case "\ud83d\udd10": case "\ud83d\udd12": return R.drawable.mi_lock;
            case "⌖": case "\ud83d\udccd": case "\ud83d\uddfa": return R.drawable.mi_pin_drop;
            case "\ud83d\uddbc": case "\ud83d\uddbc\ufe0f": case "\ud83d\udcf7": return R.drawable.mi_photo_camera;
            case "\ud83d\udcc4": case "\ud83d\uddc0": return R.drawable.mi_description;
            case "☎": case "\ud83d\udcde": return R.drawable.mi_call;
            case "✉": case "\ud83d\udcec": return R.drawable.lux_mail;
            case "☘": return R.drawable.mi_beach_access;
            case "♞": return R.drawable.mi_person_search;
            case "\ud83d\udcca": return R.drawable.mi_bar_chart;
            case "\ud83d\udcac": return R.drawable.lux_chat;
            case "\ud83d\udce6": return R.drawable.mi_inventory_2;
            case "\ud83d\uded2": return R.drawable.mi_shopping_cart;
            case "✍": case "✎": return R.drawable.mi_edit;
            case "\ud83e\uddfe": case "\ud83d\udccb": return R.drawable.mi_receipt_long;
            case "\ud83d\udcb3": return R.drawable.mi_credit_card;
            case "❌": case "✕": case "✗": return R.drawable.mi_close;
            case "♪": case "\ud83d\udd0a": return R.drawable.mi_volume_up;
            case "\ud83c\udfa9": return R.drawable.mi_mic;
            case "\ud83c\udf89": return R.drawable.mi_celebration;
            case "\ud83c\udfe0": return R.drawable.mi_store;
            case "⛔": return R.drawable.mi_block;
            case "⚕": return R.drawable.mi_shield;
            case "\ud83d\udcbc": return R.drawable.mi_assignment;
            case "\ud83d\ude0a": return R.drawable.mi_sentiment_satisfied;
            default: return 0;
        }
    }

    /** A brand vector tinted and bounded for use as a compound drawable. Returns null when unmapped. */
    static android.graphics.drawable.Drawable tinted(android.content.Context ctx, String glyph, int tint, int sizePx) {
        int res = iconRes(glyph);
        if (res == 0) return null;
        android.graphics.drawable.Drawable d = ctx.getDrawable(res);
        if (d == null) return null;
        d = d.mutate();
        d.setTint(tint);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    /** Material shimmer sweep for skeleton bars while data loads. Phase-7H: the animator now dies
        with the view (onDetachedFromWindow) so re-renders never accumulate infinite animators. */
    static class ShimmerBar extends android.view.View {
        private final android.graphics.Paint base = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint glow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float t = 0f;
        private final int glowColor;
        private final android.animation.ValueAnimator va;
        ShimmerBar(android.content.Context ctx, int baseColor, int glowColor) {
            super(ctx);
            base.setColor(baseColor);
            this.glowColor = glowColor;
            va = android.animation.ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1100);
            va.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            va.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); postInvalidateOnAnimation(); });
            va.start();
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            try { va.cancel(); } catch (Exception ignored) { }
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            try {
                float w = getWidth(), h = getHeight();
                if (w == 0 || h == 0) return;
                float r = 12 * getResources().getDisplayMetrics().density;
                c.drawRoundRect(0, 0, w, h, r, r, base);
                float band = w * 0.4f;
                float x = -band + (w + band * 2) * t;
                glow.setShader(new android.graphics.LinearGradient(x - band / 2, 0, x + band / 2, 0,
                        new int[]{glowColor & 0x00FFFFFF, glowColor, glowColor & 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(0, 0, w, h, r, r, glow);
                glow.setShader(null);
            } catch (Exception ignored) { }
        }
    }

    /** Micro sparkline for KPI tiles — a rounded polyline of the 7-day sales trend. */
    static class SparkLine extends android.view.View {
        private final double[] vals;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        SparkLine(android.content.Context ctx, double[] vals, int color) {
            super(ctx);
            this.vals = vals;
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(ctx.getResources().getDisplayMetrics().density * 2f);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setColor(color);
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            try {
                if (vals == null || vals.length < 2) return;
                double mn = vals[0], mx = vals[0];
                for (double x : vals) { mn = Math.min(mn, x); mx = Math.max(mx, x); }
                float pad = p.getStrokeWidth();
                float w = getWidth() - pad * 2, h = getHeight() - pad * 2;
                if (w <= 0 || h <= 0) return;
                android.graphics.Path path = new android.graphics.Path();
                for (int i = 0; i < vals.length; i++) {
                    float x = pad + w * i / (vals.length - 1);
                    double n = mx > mn ? (vals[i] - mn) / (mx - mn) : 0.5;
                    float y = pad + h * (1f - (float) n);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                c.drawPath(path, p);
            } catch (Exception ignored) { }
        }
    }
}
