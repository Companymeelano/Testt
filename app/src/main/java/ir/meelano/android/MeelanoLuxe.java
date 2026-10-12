package ir.meelano.android;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * «لایهٔ لوکس» رابط کاربری Meelano — زبان بصری تازهٔ بخش مدیریت.
 *
 * <p>سه چیزی که این لایه را از کیت پیشین ({@link MeelanoUiKit}) جدا می‌کند:</p>
 * <ol>
 *   <li><b>آیکون‌های وکتوری کشیده‌شده روی بوم:</b> هر آیکون با {@link Path} + قلم گرادیانی +
 *       درخشش نرم کشیده می‌شود؛ نه فایل XML لازم دارد و نه گلیف فونت، و در هر اندازه‌ای تیز است.</li>
 *   <li><b>سطح شیشه‌ای سه‌لایه:</b> هر کارت گرادیان بالا/میانه/پایین + خط مویی روشن دارد؛
 *       نتیجه، عمق و حس «گران‌قیمت» بدون هیچ تصویر و کتابخانهٔ بیرونی.</li>
 *   <li><b>اجزای داده‌محور:</b> جرقهٔ خطی، عقربهٔ حلقه‌ای و میله‌های کوچک مستقیماً از دادهٔ
 *       آتیران رسم می‌شوند تا مدیر در یک نگاه وضعیت را بخواند.</li>
 * </ol>
 *
 * <p>همه‌چیز با Android SDK خالص است (بدون AndroidX)، رنگ را از {@link MeelanoPalette} تم فعال
 * می‌گیرد و در حالت «کاهش حرکت» انیمیشن‌ها خاموش می‌شوند.</p>
 */
final class MeelanoLuxe {
    private MeelanoLuxe() { }

    // ================================================================ ۱) سطوح لوکس

    /** سطح شیشه‌ای سه‌لایه با خط مویی — کارت پایهٔ این لایه. */
    static GradientDrawable glass(MeelanoPalette p, int accent, float radius) {
        int hero = p.mix(accent, p.accent2, p.light ? 0.14f : 0.20f);
        int top = p.mix(p.surface, hero, p.light ? 0.055f : 0.135f);
        int mid = p.mix(p.surface2, hero, p.light ? 0.10f : 0.20f);
        int bot = p.mix(p.bg, hero, p.light ? 0.035f : 0.13f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(p.mix(top, Color.WHITE, p.light ? 0.26f : 0.09f), 255),
                p.alpha(mid, 252),
                p.alpha(bot, 254)});
        d.setCornerRadius(radius);
        d.setStroke(1, p.alpha(p.mix(hero, Color.WHITE, p.light ? 0.48f : 0.32f), p.light ? 130 : 100));
        return d;
    }

    /** سطح تودرتو (داخل کارت) — کمی نازک‌تر و روشن‌تر. */
    static GradientDrawable glassInner(MeelanoPalette p, int accent, float radius) {
        int soft = p.mix(accent, p.accent2, p.light ? 0.10f : 0.16f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(p.mix(p.surface, soft, p.light ? 0.04f : 0.11f), 244),
                p.alpha(p.mix(p.surface2, soft, p.light ? 0.075f : 0.16f), 240)});
        d.setCornerRadius(radius);
        d.setStroke(1, p.alpha(p.mix(soft, Color.WHITE, 0.30f), p.light ? 96 : 78));
        return d;
    }

    /** قرص طلایی (مدالیون) — پس‌زمینهٔ نشان و آیکون. */
    static GradientDrawable coin(MeelanoPalette p, int accent, float radius) {
        int gold = p.mix(accent, p.accent2, 0.55f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(p.mix(gold, Color.WHITE, p.light ? 0.10f : 0.22f), 255),
                p.alpha(gold, 250),
                p.alpha(p.mix(gold, Color.BLACK, p.light ? 0f : 0.28f), 248)});
        d.setCornerRadius(radius);
        d.setStroke(1, p.alpha(p.mix(gold, Color.WHITE, 0.60f), 150));
        return d;
    }

    /** نشانهٔ کوچک (بج) — پُر یا خطی. */
    static GradientDrawable badge(MeelanoPalette p, int accent, boolean filled) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(filled ? p.alpha(p.mix(accent, p.accent2, 0.22f), p.light ? 34 : 48)
                          : p.alpha(accent, p.light ? 18 : 28));
        d.setCornerRadius(999);
        d.setStroke(1, p.alpha(p.mix(accent, Color.WHITE, 0.35f), filled ? 150 : 110));
        return d;
    }

    // ================================================================ ۲) آیکون‌های وکتوری

    /** فهرست آیکون‌های مدیریتی — همه در قاب استاندارد ۲۴×۲۴. */
    enum I {
        GAUGE, CROWN, TREND, WALLET, RECEIPT, USERS, BOX, BOXES, TRUCK, PIE, BARS, TARGET,
        STAR, SPARKLE, SHIELD, BELL, CALENDAR, DOWNLOAD, SLIDERS, COIN, CLOCK, ROUTE, CART,
        LAYERS, PULSE, FLAG, ROCKET, GEM, REFRESH, SEARCH, CHECK, ALERT, TAG, PLUG, SETTINGS,
        CHEVRON, PRINTER, SHARE, MAP
    }

    /**
     * انتخاب آیکون از روی «معنای عنوان/برچسب» فارسی.
     *
     * <p>یک منبع حقیقت برای همهٔ ماژول‌ها: هر صفحهٔ تازه که عنوانش یکی از این کلیدواژه‌ها را
     * داشته باشد، خودکار آیکون معنادار می‌گیرد و لازم نیست فراخوان، آیکون را بشناسد.</p>
     */
    static I iconFor(String text) {
        String t = text == null ? "" : text;
        if (t.contains("وصول") || t.contains("مطالبات") || t.contains("چک") || t.contains("بدهکار") || t.contains("اعتبار")) return I.WALLET;
        if (t.contains("ویزیتور") || t.contains("مسیر") || t.contains("میدان") || t.contains("پخش")) return I.ROUTE;
        if (t.contains("حضور") || t.contains("پرسنل") || t.contains("کارکنان") || t.contains("مرخصی") || t.contains("حقوق")) return I.USERS;
        if (t.contains("انبار") || t.contains("موجودی") || t.contains("شمارش") || t.contains("انتقال")) return I.BOXES;
        if (t.contains("برچسب") || t.contains("بارکد") || t.contains("QR")) return I.PRINTER;
        if (t.contains("کالا") || t.contains("محصول") || t.contains("قلم")) return I.TAG;
        if (t.contains("مالیات") || t.contains("مودیان") || t.contains("فاکتور") || t.contains("صورت‌حساب")) return I.RECEIPT;
        if (t.contains("سود و زیان") || t.contains("حاشیه") || t.contains("سربه‌سر") || t.contains("نقدی") || t.contains("قیمت‌گذاری")) return I.COIN;
        if (t.contains("مالی") || t.contains("حساب")) return I.COIN;
        if (t.contains("فروش") || t.contains("روند")) return I.TREND;
        if (t.contains("مشتری")) return I.USERS;
        if (t.contains("گزارش") || t.contains("تحلیل") || t.contains("پارتو") || t.contains("ABC")) return I.BARS;
        if (t.contains("امنیت") || t.contains("دزدگیر") || t.contains("دوربین")) return I.SHIELD;
        if (t.contains("پیام") || t.contains("تماس") || t.contains("هشدار")) return I.BELL;
        if (t.contains("ارسال") || t.contains("خودرو") || t.contains("حمل")) return I.TRUCK;
        if (t.contains("هدف")) return I.TARGET;
        if (t.contains("تنظیم") || t.contains("پارامتر")) return I.SLIDERS;
        if (t.contains("تاریخ") || t.contains("سررسید") || t.contains("بازه")) return I.CALENDAR;
        if (t.contains("خروجی") || t.contains("دانلود") || t.contains("CSV")) return I.DOWNLOAD;
        if (t.contains("اتصال") || t.contains("سرور") || t.contains("دیتابیس")) return I.PLUG;
        if (t.contains("خانه") || t.contains("داشبورد") || t.contains("مدیریت")) return I.CROWN;
        if (t.contains("کارت کالا") || t.contains("گردش")) return I.LAYERS;
        if (t.contains("سرمایه") || t.contains("راکد")) return I.GEM;
        return I.SPARKLE;
    }

    static IconView icon(MeelanoUiHost h, I kind, int accent, float sizeDp) {
        return new IconView(h.ctx(), h.colors(), kind, accent, h.dp(sizeDp));
    }

    /** آیکون وکتوری با قلم گرادیانی، درخشش نرم و نوک گرد. */
    static final class IconView extends View {
        private final MeelanoPalette pal;
        private final I kind;
        private final int accent;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int sizePx;          // پیکسل پیش‌فرض آیکون (setMinimumWidth عدد صحیح می‌خواهد)

        IconView(Context ctx, MeelanoPalette pal, I kind, int accent, int sizePx) {
            super(ctx);
            this.pal = pal;
            this.kind = kind;
            this.accent = accent;
            this.sizePx = Math.max(8, sizePx);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setStrokeJoin(Paint.Join.ROUND);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setMinimumWidth(this.sizePx);
            setMinimumHeight(this.sizePx);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = resolve(wSpec, getMinimumWidth());
            int h = resolve(hSpec, getMinimumHeight());
            int size = Math.min(w, h);
            if (size <= 0) size = Math.max(getMinimumWidth(), getMinimumHeight());
            setMeasuredDimension(size, size);
        }

        private int resolve(int spec, int fallback) {
            int mode = MeasureSpec.getMode(spec);
            int size = MeasureSpec.getSize(spec);
            if (mode == MeasureSpec.UNSPECIFIED) return fallback;
            if (mode == MeasureSpec.AT_MOST) return Math.min(size, fallback <= 0 ? size : fallback);
            return size;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float size = Math.min(w, h);
            if (size <= 0) return;
            float scale = size / 24f;
            path.reset();
            buildIcon(kind, path);
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            path.transform(m);
            float sw = Math.max(1.3f, 1.95f * scale);
            stroke.setStrokeWidth(sw);
            glow.setStrokeWidth(sw * 3.0f);
            int c1 = pal.mix(accent, pal.accent2, 0.45f);
            int c2 = pal.mix(accent, Color.WHITE, pal.light ? 0f : 0.30f);
            Shader sh = new LinearGradient(0, 0, size * 0.35f, size, new int[]{
                    pal.alpha(pal.mix(c1, Color.WHITE, 0.28f), 255),
                    pal.alpha(c2, 255)}, null, Shader.TileMode.CLAMP);
            stroke.setShader(sh);
            glow.setShader(sh);
            glow.setAlpha(44);
            glow.setMaskFilter(new BlurMaskFilter(Math.max(2f, scale * 3.2f), BlurMaskFilter.Blur.NORMAL));
        }

        @Override protected void onDraw(Canvas cv) {
            if (path.isEmpty()) return;
            cv.drawPath(path, glow);
            cv.drawPath(path, stroke);
        }
    }

    /** ساخت مسیر هر آیکون — قاب مرجع ۲۴×۲۴. */
    private static void buildIcon(I kind, Path p) {
        RectF r = new RectF();
        switch (kind) {
            case GAUGE:
                r.set(3.2f, 5.4f, 20.8f, 23f);
                p.arcTo(r, 195f, 150f, true);
                p.moveTo(12f, 14.4f);
                p.lineTo(16.6f, 10.4f);
                p.addCircle(12f, 14.4f, 1.5f, Path.Direction.CW);
                p.moveTo(4.6f, 19.4f);
                p.lineTo(6.6f, 19.4f);
                p.moveTo(19.4f, 19.4f);
                p.lineTo(17.4f, 19.4f);
                break;
            case CROWN:
                p.moveTo(3.4f, 18.6f);
                p.lineTo(3.4f, 8.2f);
                p.lineTo(7.6f, 11.6f);
                p.lineTo(12f, 5.2f);
                p.lineTo(16.4f, 11.6f);
                p.lineTo(20.6f, 8.2f);
                p.lineTo(20.6f, 18.6f);
                p.close();
                p.moveTo(5.4f, 21.2f);
                p.lineTo(18.6f, 21.2f);
                break;
            case TREND:
                p.moveTo(3.2f, 18.2f);
                p.lineTo(8.6f, 12.2f);
                p.lineTo(12.4f, 15.4f);
                p.lineTo(20.8f, 6.4f);
                p.moveTo(15.6f, 6.4f);
                p.lineTo(20.8f, 6.4f);
                p.lineTo(20.8f, 11.6f);
                p.moveTo(3.2f, 21.4f);
                p.lineTo(20.8f, 21.4f);
                break;
            case WALLET:
                r.set(3f, 6.4f, 21f, 19.4f);
                p.addRoundRect(r, 3.2f, 3.2f, Path.Direction.CW);
                p.moveTo(3f, 10.6f);
                p.lineTo(21f, 10.6f);
                p.moveTo(16.4f, 15f);
                p.lineTo(17.8f, 15f);
                break;
            case RECEIPT:
                p.moveTo(5.2f, 3.4f);
                p.lineTo(18.8f, 3.4f);
                p.lineTo(18.8f, 20.6f);
                p.lineTo(16.4f, 18.8f);
                p.lineTo(14f, 20.6f);
                p.lineTo(12f, 18.8f);
                p.lineTo(10f, 20.6f);
                p.lineTo(7.6f, 18.8f);
                p.lineTo(5.2f, 20.6f);
                p.close();
                p.moveTo(8.4f, 8.2f);
                p.lineTo(15.6f, 8.2f);
                p.moveTo(8.4f, 12.2f);
                p.lineTo(15.6f, 12.2f);
                break;
            case USERS:
                p.addCircle(9.2f, 8.4f, 3.4f, Path.Direction.CW);
                r.set(3.6f, 14.2f, 14.8f, 21.6f);
                p.addRoundRect(r, 4.6f, 4.6f, Path.Direction.CW);
                p.addCircle(17.2f, 9.6f, 2.6f, Path.Direction.CW);
                p.moveTo(15.4f, 21.4f);
                p.lineTo(21f, 21.4f);
                break;
            case BOX:
                p.moveTo(3.4f, 7.6f);
                p.lineTo(12f, 3.4f);
                p.lineTo(20.6f, 7.6f);
                p.lineTo(20.6f, 16.4f);
                p.lineTo(12f, 20.6f);
                p.lineTo(3.4f, 16.4f);
                p.close();
                p.moveTo(3.4f, 7.6f);
                p.lineTo(12f, 11.8f);
                p.lineTo(20.6f, 7.6f);
                p.moveTo(12f, 11.8f);
                p.lineTo(12f, 20.6f);
                break;
            case BOXES:
                r.set(2.8f, 12.6f, 11f, 21.2f);
                p.addRoundRect(r, 1.6f, 1.6f, Path.Direction.CW);
                r.set(13f, 12.6f, 21.2f, 21.2f);
                p.addRoundRect(r, 1.6f, 1.6f, Path.Direction.CW);
                r.set(7.9f, 3f, 16.1f, 11.6f);
                p.addRoundRect(r, 1.6f, 1.6f, Path.Direction.CW);
                break;
            case TRUCK:
                p.moveTo(2.8f, 6.6f);
                p.lineTo(14.2f, 6.6f);
                p.lineTo(14.2f, 17f);
                p.moveTo(14.2f, 9.6f);
                p.lineTo(18.2f, 9.6f);
                p.lineTo(21.2f, 13.2f);
                p.lineTo(21.2f, 17f);
                p.lineTo(2.8f, 17f);
                p.moveTo(6.6f, 17f);
                p.addCircle(6.6f, 18.9f, 1.9f, Path.Direction.CW);
                p.addCircle(17.2f, 18.9f, 1.9f, Path.Direction.CW);
                break;
            case PIE:
                p.addCircle(12f, 12f, 8.6f, Path.Direction.CW);
                p.moveTo(12f, 12f);
                p.lineTo(12f, 3.4f);
                p.moveTo(12f, 12f);
                p.lineTo(19.4f, 15.6f);
                break;
            case BARS:
                p.moveTo(4.6f, 20.4f);
                p.lineTo(4.6f, 13.4f);
                p.moveTo(12f, 20.4f);
                p.lineTo(12f, 4.2f);
                p.moveTo(19.4f, 20.4f);
                p.lineTo(19.4f, 9.4f);
                p.moveTo(2.4f, 22.2f);
                p.lineTo(21.6f, 22.2f);
                break;
            case TARGET:
                p.addCircle(12f, 12f, 8.6f, Path.Direction.CW);
                p.addCircle(12f, 12f, 5.2f, Path.Direction.CW);
                p.addCircle(12f, 12f, 1.9f, Path.Direction.CW);
                break;
            case STAR:
                p.moveTo(12f, 3.2f);
                p.lineTo(14.7f, 9.1f);
                p.lineTo(21f, 9.9f);
                p.lineTo(16.4f, 14.2f);
                p.lineTo(17.6f, 20.6f);
                p.lineTo(12f, 17.5f);
                p.lineTo(6.4f, 20.6f);
                p.lineTo(7.6f, 14.2f);
                p.lineTo(3f, 9.9f);
                p.lineTo(9.3f, 9.1f);
                p.close();
                break;
            case SPARKLE:
                p.moveTo(9.6f, 2.8f);
                p.lineTo(11.1f, 7.4f);
                p.lineTo(15.7f, 8.9f);
                p.lineTo(11.1f, 10.4f);
                p.lineTo(9.6f, 15f);
                p.lineTo(8.1f, 10.4f);
                p.lineTo(3.5f, 8.9f);
                p.lineTo(8.1f, 7.4f);
                p.close();
                p.moveTo(17f, 13.4f);
                p.lineTo(17.9f, 16.1f);
                p.lineTo(20.6f, 17f);
                p.lineTo(17.9f, 17.9f);
                p.lineTo(17f, 20.6f);
                p.lineTo(16.1f, 17.9f);
                p.lineTo(13.4f, 17f);
                p.lineTo(16.1f, 16.1f);
                p.close();
                break;
            case SHIELD:
                p.moveTo(12f, 2.8f);
                p.lineTo(20.4f, 6.2f);
                p.lineTo(20.4f, 12.4f);
                p.lineTo(12f, 21.2f);
                p.lineTo(3.6f, 12.4f);
                p.lineTo(3.6f, 6.2f);
                p.close();
                p.moveTo(8.6f, 12.2f);
                p.lineTo(11.2f, 14.8f);
                p.lineTo(15.8f, 9.8f);
                break;
            case BELL:
                p.moveTo(12f, 3f);
                p.lineTo(12f, 4.9f);
                p.moveTo(5.4f, 17.4f);
                p.lineTo(5.4f, 12.4f);
                p.cubicTo(5.4f, 8.4f, 8.4f, 5.4f, 12f, 5.4f);
                p.cubicTo(15.6f, 5.4f, 18.6f, 8.4f, 18.6f, 12.4f);
                p.lineTo(18.6f, 17.4f);
                p.lineTo(4f, 17.4f);
                p.close();
                p.moveTo(9.8f, 19.8f);
                p.cubicTo(10.4f, 21.2f, 13.6f, 21.2f, 14.2f, 19.8f);
                break;
            case CALENDAR:
                r.set(3.2f, 5.4f, 20.8f, 21f);
                p.addRoundRect(r, 3f, 3f, Path.Direction.CW);
                p.moveTo(3.2f, 10.4f);
                p.lineTo(20.8f, 10.4f);
                p.moveTo(8f, 2.8f);
                p.lineTo(8f, 6.6f);
                p.moveTo(16f, 2.8f);
                p.lineTo(16f, 6.6f);
                p.moveTo(7.4f, 14.4f);
                p.lineTo(8.8f, 14.4f);
                p.moveTo(11.4f, 14.4f);
                p.lineTo(12.8f, 14.4f);
                p.moveTo(15.4f, 14.4f);
                p.lineTo(16.8f, 14.4f);
                p.moveTo(7.4f, 18f);
                p.lineTo(8.8f, 18f);
                p.moveTo(11.4f, 18f);
                p.lineTo(12.8f, 18f);
                break;
            case DOWNLOAD:
                p.moveTo(12f, 3f);
                p.lineTo(12f, 15.2f);
                p.moveTo(7.4f, 10.6f);
                p.lineTo(12f, 15.2f);
                p.lineTo(16.6f, 10.6f);
                p.moveTo(4.2f, 18.6f);
                p.lineTo(4.2f, 21f);
                p.lineTo(19.8f, 21f);
                p.lineTo(19.8f, 18.6f);
                break;
            case SLIDERS:
                p.moveTo(3.2f, 8.2f);
                p.lineTo(20.8f, 8.2f);
                p.moveTo(3.2f, 15.8f);
                p.lineTo(20.8f, 15.8f);
                p.addCircle(9f, 8.2f, 2.4f, Path.Direction.CW);
                p.addCircle(15.4f, 15.8f, 2.4f, Path.Direction.CW);
                break;
            case COIN:
                p.addCircle(12f, 12f, 8.6f, Path.Direction.CW);
                p.addCircle(12f, 12f, 4.6f, Path.Direction.CW);
                p.moveTo(12f, 3.4f);
                p.lineTo(12f, 6.2f);
                p.moveTo(12f, 17.8f);
                p.lineTo(12f, 20.6f);
                break;
            case CLOCK:
                p.addCircle(12f, 12f, 8.8f, Path.Direction.CW);
                p.moveTo(12f, 6.8f);
                p.lineTo(12f, 12.2f);
                p.lineTo(16.4f, 14.6f);
                break;
            case ROUTE:
                p.addCircle(6f, 6.2f, 2.8f, Path.Direction.CW);
                p.addCircle(18f, 17.6f, 2.8f, Path.Direction.CW);
                p.moveTo(6f, 9.2f);
                p.lineTo(6f, 13.4f);
                p.cubicTo(6f, 17.6f, 11f, 16.4f, 13.6f, 17.2f);
                break;
            case CART:
                p.moveTo(2.8f, 4.2f);
                p.lineTo(6f, 4.2f);
                p.lineTo(8.6f, 15.2f);
                p.lineTo(19.2f, 15.2f);
                p.lineTo(21.2f, 7.2f);
                p.lineTo(6.9f, 7.2f);
                p.addCircle(10.2f, 19.6f, 1.8f, Path.Direction.CW);
                p.addCircle(17.6f, 19.6f, 1.8f, Path.Direction.CW);
                break;
            case LAYERS:
                p.moveTo(12f, 2.8f);
                p.lineTo(21.2f, 7.6f);
                p.lineTo(12f, 12.4f);
                p.lineTo(2.8f, 7.6f);
                p.close();
                p.moveTo(2.8f, 12.4f);
                p.lineTo(12f, 17.2f);
                p.lineTo(21.2f, 12.4f);
                p.moveTo(2.8f, 17f);
                p.lineTo(12f, 21.6f);
                p.lineTo(21.2f, 17f);
                break;
            case PULSE:
                p.moveTo(2.6f, 12.4f);
                p.lineTo(7f, 12.4f);
                p.lineTo(9.4f, 6.6f);
                p.lineTo(13f, 18.4f);
                p.lineTo(15.6f, 12.4f);
                p.lineTo(21.4f, 12.4f);
                break;
            case FLAG:
                p.moveTo(5.4f, 3.2f);
                p.lineTo(5.4f, 21.2f);
                p.moveTo(5.4f, 4.6f);
                p.lineTo(18.4f, 4.6f);
                p.lineTo(15.6f, 9f);
                p.lineTo(18.4f, 13.4f);
                p.lineTo(5.4f, 13.4f);
                break;
            case ROCKET:
                p.moveTo(12f, 2.8f);
                p.cubicTo(15.8f, 6f, 17.2f, 10.4f, 17.2f, 15f);
                p.lineTo(6.8f, 15f);
                p.cubicTo(6.8f, 10.4f, 8.2f, 6f, 12f, 2.8f);
                p.close();
                p.addCircle(12f, 9.4f, 2.1f, Path.Direction.CW);
                p.moveTo(6.8f, 15f);
                p.lineTo(4.6f, 18.6f);
                p.lineTo(9.4f, 17.6f);
                p.moveTo(17.2f, 15f);
                p.lineTo(19.4f, 18.6f);
                p.lineTo(14.6f, 17.6f);
                p.moveTo(10.4f, 19.2f);
                p.cubicTo(11f, 21.2f, 13f, 21.2f, 13.6f, 19.2f);
                break;
            case GEM:
                p.moveTo(7.6f, 3.4f);
                p.lineTo(16.4f, 3.4f);
                p.lineTo(21.2f, 9.2f);
                p.lineTo(12f, 21f);
                p.lineTo(2.8f, 9.2f);
                p.close();
                p.moveTo(2.8f, 9.2f);
                p.lineTo(21.2f, 9.2f);
                p.moveTo(7.6f, 3.4f);
                p.lineTo(9.6f, 9.2f);
                p.lineTo(12f, 21f);
                p.moveTo(16.4f, 3.4f);
                p.lineTo(14.4f, 9.2f);
                p.lineTo(12f, 21f);
                break;
            case REFRESH:
                r.set(3.6f, 3.6f, 20.4f, 20.4f);
                p.arcTo(r, 40f, 250f, true);
                p.moveTo(17.4f, 2.6f);
                p.lineTo(20.2f, 6.4f);
                p.lineTo(16f, 7.6f);
                break;
            case SEARCH:
                p.addCircle(10.4f, 10.4f, 6.6f, Path.Direction.CW);
                p.moveTo(15.4f, 15.4f);
                p.lineTo(21f, 21f);
                break;
            case CHECK:
                p.addCircle(12f, 12f, 8.8f, Path.Direction.CW);
                p.moveTo(7.8f, 12.4f);
                p.lineTo(10.8f, 15.4f);
                p.lineTo(16.4f, 9.2f);
                break;
            case ALERT:
                p.moveTo(12f, 3.2f);
                p.lineTo(21.6f, 20.4f);
                p.lineTo(2.4f, 20.4f);
                p.close();
                p.moveTo(12f, 9f);
                p.lineTo(12f, 14.2f);
                p.moveTo(11.8f, 17.2f);
                p.lineTo(12.4f, 17.2f);
                break;
            case TAG:
                p.moveTo(11.2f, 2.8f);
                p.lineTo(21.2f, 2.8f);
                p.lineTo(21.2f, 12.8f);
                p.lineTo(11.6f, 21.4f);
                p.lineTo(2.6f, 12.4f);
                p.close();
                p.addCircle(17f, 7f, 1.7f, Path.Direction.CW);
                break;
            case PLUG:
                p.moveTo(9.4f, 2.8f);
                p.lineTo(9.4f, 7.4f);
                p.moveTo(14.6f, 2.8f);
                p.lineTo(14.6f, 7.4f);
                r.set(6f, 7.4f, 18f, 14.6f);
                p.addRoundRect(r, 3f, 3f, Path.Direction.CW);
                p.moveTo(12f, 14.6f);
                p.lineTo(12f, 21.2f);
                break;
            case SETTINGS:
                p.addCircle(12f, 12f, 3.4f, Path.Direction.CW);
                p.addCircle(12f, 12f, 8.2f, Path.Direction.CW);
                p.moveTo(12f, 2.6f);
                p.lineTo(12f, 5f);
                p.moveTo(12f, 19f);
                p.lineTo(12f, 21.4f);
                p.moveTo(2.6f, 12f);
                p.lineTo(5f, 12f);
                p.moveTo(19f, 12f);
                p.lineTo(21.4f, 12f);
                break;
            case CHEVRON:
                p.moveTo(15.4f, 4.6f);
                p.lineTo(8.6f, 12f);
                p.lineTo(15.4f, 19.4f);
                break;
            case PRINTER:
                r.set(3.2f, 8.2f, 20.8f, 17.4f);
                p.addRoundRect(r, 2.6f, 2.6f, Path.Direction.CW);
                p.moveTo(6.4f, 8.2f);
                p.lineTo(6.4f, 3.4f);
                p.lineTo(17.6f, 3.4f);
                p.lineTo(17.6f, 8.2f);
                p.moveTo(6.4f, 17.4f);
                p.lineTo(6.4f, 21f);
                p.lineTo(17.6f, 21f);
                p.lineTo(17.6f, 17.4f);
                break;
            case SHARE:
                p.addCircle(17.6f, 5.6f, 2.6f, Path.Direction.CW);
                p.addCircle(6.4f, 12f, 2.6f, Path.Direction.CW);
                p.addCircle(17.6f, 18.4f, 2.6f, Path.Direction.CW);
                p.moveTo(8.8f, 10.6f);
                p.lineTo(15.2f, 7f);
                p.moveTo(8.8f, 13.4f);
                p.lineTo(15.2f, 17f);
                break;
            case MAP:
                p.moveTo(2.8f, 6.2f);
                p.lineTo(9f, 3.4f);
                p.lineTo(15f, 6.2f);
                p.lineTo(21.2f, 3.4f);
                p.lineTo(21.2f, 17.8f);
                p.lineTo(15f, 20.6f);
                p.lineTo(9f, 17.8f);
                p.lineTo(2.8f, 20.6f);
                p.close();
                p.moveTo(9f, 3.4f);
                p.lineTo(9f, 17.8f);
                p.moveTo(15f, 6.2f);
                p.lineTo(15f, 20.6f);
                break;
            default:
                p.addCircle(12f, 12f, 8.4f, Path.Direction.CW);
                break;
        }
    }

    // ================================================================ ۳) نمودارهای کوچک

    /** جرقهٔ خطی — روند چند نقطهٔ اخیر با پُرِ گرادیانی و نقطهٔ پایانی درخشان. */
    static final class Spark extends View {
        private final MeelanoPalette pal;
        private final int accent;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path fillPath = new Path();
        private double[] values = new double[0];

        Spark(Context ctx, MeelanoPalette pal, int accent) {
            super(ctx);
            this.pal = pal;
            this.accent = accent;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setValues(double[] v) { values = v == null ? new double[0] : v; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            int n = values.length;
            if (n < 2 || getWidth() <= 0) return;
            float w = getWidth(), hgt = getHeight();
            float pad = hgt * 0.18f;
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) { min = Math.min(min, values[i]); max = Math.max(max, values[i]); }
            if (max - min < 1e-9) max = min + 1;
            float stepX = (w - pad * 2) / (float) (n - 1);
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pad + stepX * i;
                ys[i] = (float) (hgt - pad - (values[i] - min) / (max - min) * (hgt - pad * 2));
            }
            path.reset();
            path.moveTo(xs[0], ys[0]);
            for (int i = 0; i < n - 1; i++) {
                float cx1 = xs[i] + stepX * 0.45f, cx2 = xs[i + 1] - stepX * 0.45f;
                path.cubicTo(cx1, ys[i], cx2, ys[i + 1], xs[i + 1], ys[i + 1]);
            }
            fillPath.reset();
            fillPath.addPath(path);
            fillPath.lineTo(xs[n - 1], hgt);
            fillPath.lineTo(xs[0], hgt);
            fillPath.close();
            area.setStyle(Paint.Style.FILL);
            area.setShader(new LinearGradient(0, 0, 0, hgt, new int[]{
                    pal.alpha(pal.mix(accent, pal.accent2, 0.30f), 106),
                    pal.alpha(accent, 14)}, null, Shader.TileMode.CLAMP));
            cv.drawPath(fillPath, area);
            line.setStrokeWidth(Math.max(2f, hgt * 0.075f));
            line.setShader(new LinearGradient(0, 0, w, 0, new int[]{
                    pal.alpha(pal.mix(accent, Color.WHITE, 0.35f), 255),
                    pal.alpha(pal.mix(accent, pal.accent2, 0.65f), 255)}, null, Shader.TileMode.CLAMP));
            cv.drawPath(path, line);
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(pal.mix(accent, Color.WHITE, 0.45f));
            cv.drawCircle(xs[n - 1], ys[n - 1], Math.max(2.2f, hgt * 0.085f), dot);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(Math.max(1f, hgt * 0.03f));
            dot.setColor(pal.alpha(pal.mix(accent, Color.WHITE, 0.75f), 120));
            cv.drawCircle(xs[n - 1], ys[n - 1], Math.max(4.2f, hgt * 0.16f), dot);
        }
    }

    /** عقربهٔ حلقه‌ای — درصد تحقق با کمان گرادیانی و انیمیشن نرم. */
    static final class Ring extends View {
        private final MeelanoPalette pal;
        private final int accent;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float shown;

        Ring(Context ctx, MeelanoPalette pal, int accent) {
            super(ctx);
            this.pal = pal;
            this.accent = accent;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeCap(Paint.Cap.ROUND);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void setRatio(double ratio, boolean animate) {
            final float target = (float) Math.max(0, Math.min(1, ratio));
            if (!animate) { shown = target; invalidate(); return; }
            ValueAnimator a = ValueAnimator.ofFloat(0f, target);
            a.setDuration(780);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(animation -> { shown = (Float) animation.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float size = Math.min(w, h);
            if (size <= 0) return;
            float pad = size * 0.13f;
            oval.set(pad, pad, w - pad, h - pad);
            track.setStrokeWidth(size * 0.105f);
            track.setColor(pal.alpha(pal.muted, pal.light ? 54 : 62));
            arc.setStrokeWidth(size * 0.105f);
            arc.setShader(new SweepGradient(w / 2f, h / 2f, new int[]{
                    pal.alpha(pal.mix(accent, pal.accent2, 0.85f), 255),
                    pal.alpha(pal.mix(accent, Color.WHITE, 0.45f), 255),
                    pal.alpha(pal.mix(accent, pal.accent2, 0.85f), 255)}, null));
            arc.setMaskFilter(new BlurMaskFilter(Math.max(2f, size * 0.026f), BlurMaskFilter.Blur.NORMAL));
        }

        @Override protected void onDraw(Canvas cv) {
            if (oval.width() <= 0) return;
            cv.drawArc(oval, 0, 360, false, track);
            arc.setAlpha(110);
            cv.drawArc(oval, -90, 360f * shown, false, arc);
            arc.setAlpha(255);
            cv.drawArc(oval, -90, 360f * shown, false, arc);
        }
    }

    /** میله‌های کوچک — مقایسهٔ سریع چند عدد (مثل فروش چند روز). */
    static final class MiniBars extends View {
        private final MeelanoPalette pal;
        private final int accent;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private double[] values = new double[0];

        MiniBars(Context ctx, MeelanoPalette pal, int accent) {
            super(ctx);
            this.pal = pal;
            this.accent = accent;
        }

        void setValues(double[] v) { values = v == null ? new double[0] : v; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            int n = values.length;
            if (n == 0 || getWidth() <= 0) return;
            float w = getWidth(), hgt = getHeight();
            double max = 1e-9;
            for (int i = 0; i < n; i++) max = Math.max(max, values[i]);
            float gap = w * 0.02f;
            float bw = (w - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                float bh = Math.max(hgt * 0.08f, (float) (hgt * (values[i] / max)));
                float left = (gap + bw) * i;
                int c = i == n - 1 ? pal.mix(accent, pal.accent2, 0.5f) : accent;
                bar.setColor(pal.alpha(c, i == n - 1 ? 255 : (pal.light ? 170 : 150)));
                RectF rr = new RectF(left, hgt - bh, left + bw, hgt);
                float rad = Math.min(bw / 2f, hgt * 0.16f);
                cv.drawRoundRect(rr, rad, rad, bar);
            }
        }
    }

    /** گوشه‌های طلایی تشریفاتی — قاب کارت‌های مهم. */
    static final class Corners extends View {
        private final MeelanoPalette pal;
        private final int accent;
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);

        Corners(Context ctx, MeelanoPalette pal, int accent) {
            super(ctx);
            this.pal = pal;
            this.accent = accent;
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float len = Math.min(w, h) * 0.13f;
            float off = Math.max(4f, Math.min(w, h) * 0.055f);
            pen.setStrokeWidth(Math.max(1.2f, Math.min(w, h) * 0.011f));
            pen.setColor(pal.alpha(pal.mix(accent, pal.accent2, 0.6f), pal.light ? 130 : 158));
            cv.drawLine(off, off, off + len, off, pen);
            cv.drawLine(off, off, off, off + len, pen);
            cv.drawLine(w - off, off, w - off - len, off, pen);
            cv.drawLine(w - off, off, w - off, off + len, pen);
            cv.drawLine(off, h - off, off + len, h - off, pen);
            cv.drawLine(off, h - off, off, h - off - len, pen);
            cv.drawLine(w - off, h - off, w - off - len, h - off, pen);
            cv.drawLine(w - off, h - off, w - off, h - off - len, pen);
        }
    }

    /** نوار نور مورب که آرام روی هدر حرکت می‌کند — حس «زنده بودن». */
    static final class Shine extends View {
        private final MeelanoPalette pal;
        private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator anim;
        private float phase = -0.45f;

        Shine(Context ctx, MeelanoPalette pal) {
            super(ctx);
            this.pal = pal;
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            anim = ValueAnimator.ofFloat(-0.45f, 1.50f);
            anim.setDuration(5200);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setStartDelay(1100);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(animation -> {
                if (!isAttachedToWindow()) return;      // جلوگیری از کار بی‌فایده پس از ترک صفحه
                float v = (Float) animation.getAnimatedValue();
                // وقتی نوار از کادر بیرون رفته، بازترسیم لازم نیست (صرفه‌جویی در باتری و پردازنده).
                if (v > 1.42f) return;
                phase = v;
                invalidate();
            });
            anim.start();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (anim != null && !anim.isStarted()) anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            // صفحه‌ها مکرر باز/بسته می‌شوند؛ بدون این کار، انیمیشن‌ها در حافظه زنده می‌ماندند.
            if (anim != null) anim.cancel();
            if (getAnimation() != null) getAnimation().cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (w <= 0) return;
            float bandW = Math.max(w * 0.20f, h * 0.45f);
            float cx = phase * (w + bandW * 2) - bandW;
            brush.setShader(new LinearGradient(cx - bandW, 0, cx + bandW, h, new int[]{
                    pal.alpha(Color.WHITE, 0), pal.alpha(Color.WHITE, pal.light ? 32 : 24),
                    pal.alpha(Color.WHITE, 0)}, null, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, brush);
        }
    }

    // ================================================================ ۴) ابزارهای کوچک

    static void stagger(MeelanoUiHost h, View v, int index) {
        if (h == null || v == null || !h.motionAllowed()) return;
        v.setAlpha(0f);
        v.setTranslationY(h.dp(14));
        v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(400, index * 55L))
                .setDuration(330).setInterpolator(new DecelerateInterpolator()).start();
    }

    static void sheen(MeelanoUiHost h, FrameLayout wrap) {
        if (h == null || wrap == null || !h.motionAllowed()) return;
        Shine shine = new Shine(h.ctx(), h.colors());
        shine.setAlpha(0f);
        wrap.addView(shine, new FrameLayout.LayoutParams(-1, -1));
        shine.animate().alpha(1f).setDuration(700).start();
    }

    /** متن با کوتاه‌سازی: کلاس {@link MeelanoFitText} خودش اندازه را کم می‌کند. */
    static TextView value(MeelanoUiHost h, String text, float maxSp, float minSp, int color, boolean bold) {
        MeelanoFitText t = new MeelanoFitText(h.ctx(), maxSp, minSp);
        t.setText(h.iconize(text == null ? "—" : text));
        t.setTextColor(color);
        t.setTypeface(h.font(bold));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        return t;
    }

    private static View dot(Context ctx, MeelanoPalette p, int color) {
        View v = new View(ctx);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(p.alpha(color, 235));
        d.setStroke(1, p.alpha(p.mix(color, Color.WHITE, 0.5f), 170));
        v.setBackground(d);
        return v;
    }

    static String percentText(double ratio) {
        long pct = Math.round(Math.max(0, Math.min(1.4, ratio)) * 100);
        return MeelanoCharts.fa(String.valueOf(pct)) + "٪";
    }

    // ================================================================ ۵) اجزای آماده

    /** مدالیون: دیسک طلایی + حلقه‌های روشن + درخشش مرکزی + آیکون. */
    static final class Medallion extends FrameLayout {
        private final MeelanoPalette pal;
        private final int glowColor;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

        Medallion(Context ctx, MeelanoPalette pal, int accent) {
            super(ctx);
            this.pal = pal;
            this.glowColor = pal.mix(accent, pal.accent2, 0.45f);
            ring.setStyle(Paint.Style.STROKE);
            halo.setStyle(Paint.Style.FILL);
            setWillNotDraw(false);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
            halo.setShader(new RadialGradient(cx, cy, r, new int[]{
                    withAlpha(glowColor, 96), withAlpha(glowColor, 0)}, null, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, halo);
            halo.setShader(null);
            ring.setStrokeWidth(Math.max(1.1f, r * 0.075f));
            ring.setColor(withAlpha(glowColor, 155));
            cv.drawCircle(cx, cy, r * 0.79f, ring);
            ring.setStrokeWidth(Math.max(1f, r * 0.045f));
            ring.setColor(withAlpha(glowColor, pal.light ? 70 : 82));
            cv.drawCircle(cx, cy, r * 0.61f, ring);
        }

        private static int withAlpha(int color, int a) {
            return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a)) & 0xFF) << 24);
        }
    }

    /** مدالیون آماده با آیکون در مرکز. */
    static FrameLayout medallion(MeelanoUiHost h, I kind, int accent, float sizeDp) {
        MeelanoPalette p = h.colors();
        int size = h.dp(sizeDp);
        Medallion holder = new Medallion(h.ctx(), p, accent);
        holder.setBackground(coin(p, accent, size / 2f));
        IconView ic = icon(h, kind, accent, sizeDp * 0.54f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(h.dp(sizeDp * 0.56f), h.dp(sizeDp * 0.56f));
        ilp.gravity = Gravity.CENTER;
        holder.addView(ic, ilp);
        holder.setMinimumWidth(size);
        holder.setMinimumHeight(size);
        return holder;
    }

    /** هدر تشریفاتی: عنوان، زیرعنوان، نشان، مدالیون، چیپ‌ها، گوشه‌های طلایی و درخشش. */
    static LinearLayout hero(MeelanoUiHost h, String title, String subtitle, String badgeText, I kind,
                             int accent, String[] chips) {
        MeelanoPalette p = h.colors();
        LinearLayout card = MeelanoUiKit.column(h);
        card.setPadding(h.dp(15), h.dp(14), h.dp(15), h.dp(13));
        int soft = p.mix(accent, p.accent2, p.light ? 0.16f : 0.22f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                p.alpha(p.mix(p.surface, soft, p.light ? 0.10f : 0.22f), 255),
                p.alpha(p.mix(p.surface2, soft, p.light ? 0.16f : 0.30f), 250),
                p.alpha(p.mix(p.bg, soft, p.light ? 0.07f : 0.20f), 252)});
        bg.setCornerRadius(h.dp(26));
        bg.setStroke(1, p.alpha(p.mix(soft, Color.WHITE, p.light ? 0.5f : 0.34f), p.light ? 150 : 118));
        card.setBackground(bg);

        LinearLayout top = MeelanoUiKit.row(h);
        top.addView(medallion(h, kind, accent, 62), new LinearLayout.LayoutParams(h.dp(62), h.dp(62)));
        LinearLayout copy = MeelanoUiKit.column(h);
        copy.setPadding(h.dp(12), 0, h.dp(6), 0);
        if (badgeText != null && !badgeText.isEmpty()) {
            TextView b = MeelanoUiKit.text(h, badgeText, 9.4f, p.text, true);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setPadding(h.dp(10), h.dp(3), h.dp(10), h.dp(3));
            b.setBackground(badge(p, accent, true));
            copy.addView(b, new LinearLayout.LayoutParams(-2, h.dp(26)));
        }
        TextView t = MeelanoUiKit.text(h, title, 21.5f, p.text, true);
        t.setMaxLines(2);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(0, h.dp(5), 0, 0);
        copy.addView(t, tlp);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = MeelanoUiKit.text(h, subtitle, 11.4f, p.alpha(p.text, 214), false);
            s.setLineSpacing(h.dp(3), 1.06f);
            s.setMaxLines(4);
            copy.addView(s, new LinearLayout.LayoutParams(-1, -2));
        }
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));

        if (chips != null && chips.length > 0) {
            LinearLayout rail = MeelanoUiKit.row(h);
            for (int i = 0; i < chips.length; i++) {
                if (chips[i] == null || chips[i].isEmpty()) continue;
                TextView c = chip(h, chips[i], i == 0 ? accent : p.mix(accent, p.info, Math.min(0.5f, i * 0.25f)), false);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, h.dp(28));
                clp.setMargins(0, 0, h.dp(6), 0);
                rail.addView(c, clp);
            }
            // چیپ‌ها در قاب افقی می‌نشینند تا روی گوشی‌های باریک هم چیزی بریده نشود.
            android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(h.ctx());
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.addView(rail, new android.widget.FrameLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.setMargins(0, h.dp(11), 0, 0);
            card.addView(scroller, rlp);
        }

        FrameLayout wrap = new FrameLayout(h.ctx());
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        wrap.addView(new Corners(h.ctx(), p, accent), new FrameLayout.LayoutParams(-1, -1));
        sheen(h, wrap);
        LinearLayout holder = MeelanoUiKit.column(h);
        holder.addView(wrap, new LinearLayout.LayoutParams(-1, -2));
        return holder;
    }

    /** چیپ کوچک با قاب گرد. */
    static TextView chip(MeelanoUiHost h, String label, int accent, boolean filled) {
        MeelanoPalette p = h.colors();
        TextView c = MeelanoUiKit.text(h, label, 9.2f, p.text, true);
        c.setGravity(Gravity.CENTER);
        c.setSingleLine(true);
        c.setPadding(h.dp(9), 0, h.dp(9), 0);
        c.setBackground(badge(p, accent, filled));
        return c;
    }

    /** سرتیتر بخش: مدالیون آیکون، شماره، عنوان، توضیح و خط طلایی. */
    static LinearLayout section(MeelanoUiHost h, String number, String title, String subtitle, I kind, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout col = MeelanoUiKit.column(h);
        LinearLayout head = MeelanoUiKit.row(h);
        head.addView(medallion(h, kind, accent, 40), new LinearLayout.LayoutParams(h.dp(40), h.dp(40)));
        LinearLayout copy = MeelanoUiKit.column(h);
        copy.setPadding(h.dp(10), 0, h.dp(6), 0);
        LinearLayout titleRow = MeelanoUiKit.row(h);
        if (number != null && !number.isEmpty()) {
            TextView n = MeelanoUiKit.text(h, number, 9.2f, p.onAccent, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(coin(p, accent, h.dp(9)));
            n.setPadding(h.dp(7), h.dp(2), h.dp(7), h.dp(2));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
            nlp.setMargins(0, 0, h.dp(7), 0);
            titleRow.addView(n, nlp);
        }
        titleRow.addView(MeelanoUiKit.text(h, title, 14.4f, p.text, true), new LinearLayout.LayoutParams(-2, -2));
        copy.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = MeelanoUiKit.text(h, subtitle, 10.2f, p.muted, false);
            s.setLineSpacing(h.dp(2), 1.05f);
            copy.addView(s, new LinearLayout.LayoutParams(-1, -2));
        }
        head.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        View rule = new View(h.ctx());
        GradientDrawable rg = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{
                p.alpha(p.mix(accent, p.accent2, 0.5f), 190), p.alpha(accent, 40), p.alpha(accent, 0)});
        rg.setCornerRadius(h.dp(2));
        rule.setBackground(rg);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, h.dp(3));
        rlp.setMargins(h.dp(4), h.dp(9), h.dp(4), h.dp(2));
        col.addView(rule, rlp);
        return col;
    }

    /** کاشی فرمان — دکمهٔ بزرگ لوکس با مدالیون، عنوان، برچسب، توضیح و فلش. */
    static LinearLayout tile(MeelanoUiHost h, I kind, String title, String caption, String tag,
                             int accent, final Runnable onClick) {
        MeelanoPalette p = h.colors();
        LinearLayout card = MeelanoUiKit.column(h);
        card.setPadding(h.dp(13), h.dp(12), h.dp(13), h.dp(12));
        card.setBackground(glass(p, accent, h.dp(22)));

        LinearLayout head = MeelanoUiKit.row(h);
        head.addView(medallion(h, kind, accent, 46), new LinearLayout.LayoutParams(h.dp(46), h.dp(46)));
        LinearLayout copy = MeelanoUiKit.column(h);
        copy.setPadding(h.dp(10), 0, 0, 0);
        copy.addView(MeelanoUiKit.text(h, title, 13.2f, p.text, true), new LinearLayout.LayoutParams(-1, -2));
        if (tag != null && !tag.isEmpty()) {
            TextView tg = tagStyle(h, tag, accent);
            LinearLayout.LayoutParams tgp = new LinearLayout.LayoutParams(-2, h.dp(20));
            tgp.setMargins(0, h.dp(4), 0, 0);
            copy.addView(tg, tgp);
        }
        head.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));
        if (caption != null && !caption.isEmpty()) {
            TextView c = MeelanoUiKit.text(h, caption, 10.0f, p.muted, false);
            c.setLineSpacing(h.dp(2), 1.06f);
            c.setMaxLines(3);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.setMargins(0, h.dp(8), 0, 0);
            card.addView(c, clp);
        }
        LinearLayout foot = MeelanoUiKit.row(h);
        foot.addView(MeelanoUiKit.text(h, "ورود به بخش", 9.6f, p.mix(accent, p.accent2, 0.35f), true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        foot.addView(icon(h, I.CHEVRON, accent, 16f), new LinearLayout.LayoutParams(h.dp(16), h.dp(16)));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.setMargins(0, h.dp(9), 0, 0);
        card.addView(foot, flp);

        card.setClickable(true);
        card.setFocusable(true);
        MeelanoUiKit.press(card, h);
        card.setContentDescription(title + (caption == null ? "" : " — " + caption));
        if (onClick != null) card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private static TextView tagStyle(MeelanoUiHost h, String label, int accent) {
        MeelanoPalette p = h.colors();
        TextView t = MeelanoUiKit.text(h, label, 8.8f, p.text, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(h.dp(7), 0, h.dp(7), 0);
        t.setBackground(badge(p, accent, false));
        return t;
    }

    /** جدول کاشی‌ها با ۲ یا ۳ ستون و فاصلهٔ یکنواخت. */
    static LinearLayout tileGrid(MeelanoUiHost h, List<View> tiles, int perRow, float gapDp) {
        LinearLayout grid = MeelanoUiKit.column(h);
        int per = Math.max(1, perRow);
        for (int i = 0; i < tiles.size(); i += per) {
            LinearLayout row = MeelanoUiKit.row(h);
            for (int j = 0; j < per; j++) {
                int idx = i + j;
                if (idx < tiles.size()) {
                    View v = tiles.get(idx);
                    row.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
                    stagger(h, v, idx);
                } else {
                    row.addView(new View(h.ctx()), new LinearLayout.LayoutParams(0, -2, 1f));
                }
                if (j < per - 1) row.addView(new View(h.ctx()), new LinearLayout.LayoutParams(h.dp(gapDp), h.dp(1)));
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i + per < tiles.size()) grid.addView(gap(h, gapDp));
        }
        return grid;
    }

    /** کارت شاخص: برچسب، عدد، تغییر، جرقهٔ خطی و مدالیون. */
    static LinearLayout stat(MeelanoUiHost h, I kind, String label, String valueText, String delta,
                             double[] trend, int accent, final Runnable onClick) {
        MeelanoPalette p = h.colors();
        boolean up = delta != null && delta.indexOf('▲') >= 0;
        LinearLayout card = MeelanoUiKit.column(h);
        card.setPadding(h.dp(13), h.dp(12), h.dp(13), h.dp(11));
        card.setBackground(glass(p, accent, h.dp(20)));

        LinearLayout head = MeelanoUiKit.row(h);
        TextView lb = MeelanoUiKit.text(h, label, 10.4f, p.muted, true);
        lb.setSingleLine(true);
        lb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(lb, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(medallion(h, kind, accent, 32), new LinearLayout.LayoutParams(h.dp(32), h.dp(32)));
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, h.dp(7), 0, 0);
        card.addView(value(h, valueText, 18.5f, 12.5f, p.text, true), vlp);
        if (delta != null && !delta.isEmpty()) {
            card.addView(value(h, delta, 10.2f, 8.6f, up ? p.success : p.danger, true),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        if (trend != null && trend.length > 1) {
            Spark sp = new Spark(h.ctx(), p, accent);
            sp.setValues(trend);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, h.dp(38));
            slp.setMargins(0, h.dp(7), 0, 0);
            card.addView(sp, slp);
        }
        if (onClick != null) {
            card.setClickable(true);
            card.setFocusable(true);
            MeelanoUiKit.press(card, h);
            card.setOnClickListener(v -> onClick.run());
            card.setContentDescription(label + " — " + valueText + " — نمایش جزئیات");
        }
        return card;
    }

    /** کارت عقربه: درصد تحقق + عنوان + چند عدد کنار آن. */
    static LinearLayout ringCard(MeelanoUiHost h, String title, String caption, double ratio,
                                 String centerValue, String[][] facts, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout card = MeelanoUiKit.column(h);
        card.setPadding(h.dp(14), h.dp(13), h.dp(14), h.dp(13));
        card.setBackground(glass(p, accent, h.dp(24)));

        LinearLayout row = MeelanoUiKit.row(h);
        int ringSize = h.dp(112);
        FrameLayout wrap = new FrameLayout(h.ctx());
        Ring ring = new Ring(h.ctx(), p, accent);
        wrap.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout center = MeelanoUiKit.column(h);
        center.setGravity(Gravity.CENTER);
        TextView cv = value(h, centerValue, 20f, 12f, p.text, true);
        cv.setGravity(Gravity.CENTER);
        TextView cp = MeelanoUiKit.text(h, percentText(ratio), 10f, p.muted, true);
        cp.setGravity(Gravity.CENTER);
        center.addView(cv, new LinearLayout.LayoutParams(-1, -2));
        center.addView(cp, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.gravity = Gravity.CENTER;
        wrap.addView(center, clp);
        row.addView(wrap, new LinearLayout.LayoutParams(ringSize, ringSize));
        ring.setRatio(ratio, h.motionAllowed());

        LinearLayout factsCol = MeelanoUiKit.column(h);
        factsCol.setPadding(h.dp(12), 0, 0, 0);
        factsCol.addView(MeelanoUiKit.text(h, title, 13.4f, p.text, true), new LinearLayout.LayoutParams(-1, -2));
        if (caption != null && !caption.isEmpty()) {
            TextView c = MeelanoUiKit.text(h, caption, 10f, p.muted, false);
            c.setLineSpacing(h.dp(2), 1.05f);
            factsCol.addView(c, new LinearLayout.LayoutParams(-1, -2));
        }
        if (facts != null) {
            for (int i = 0; i < facts.length; i++) {
                if (facts[i] == null || facts[i].length < 2) continue;
                LinearLayout f = MeelanoUiKit.row(h);
                f.addView(dot(h.ctx(), p, i == 0 ? accent : (i == 1 ? p.info : p.warning)),
                        new LinearLayout.LayoutParams(h.dp(8), h.dp(8)));
                TextView k = MeelanoUiKit.text(h, facts[i][0], 9.8f, p.muted, false);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, -2, 1f);
                klp.setMargins(h.dp(6), 0, 0, 0);
                f.addView(k, klp);
                f.addView(value(h, facts[i][1], 11f, 9f, p.text, true), new LinearLayout.LayoutParams(-2, -2));
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
                flp.setMargins(0, h.dp(6), 0, 0);
                factsCol.addView(f, flp);
            }
        }
        row.addView(factsCol, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    /** نوار شاخص‌های کلیدی با جداکنندهٔ گرادیانی — مثل صفحهٔ ساعت لوکس. */
    static LinearLayout ribbon(MeelanoUiHost h, String[][] items, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout card = MeelanoUiKit.row(h);
        card.setPadding(h.dp(6), h.dp(11), h.dp(6), h.dp(11));
        card.setBackground(glass(p, accent, h.dp(20)));
        if (items == null) return card;
        int shown = 0;
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null || items[i].length < 2) continue;
            if (shown > 0) {
                View div = new View(h.ctx());
                div.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                        p.alpha(accent, 0), p.alpha(p.mix(accent, p.accent2, 0.4f), 130), p.alpha(accent, 0)}));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(h.dp(2), h.dp(30));
                dlp.setMargins(h.dp(4), 0, h.dp(4), 0);
                card.addView(div, dlp);
            }
            LinearLayout cell = MeelanoUiKit.column(h);
            cell.setGravity(Gravity.CENTER);
            TextView k = MeelanoUiKit.text(h, items[i][0], 9.2f, p.muted, false);
            k.setGravity(Gravity.CENTER);
            k.setSingleLine(true);
            k.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView v = value(h, items[i][1], 13.2f, 9.5f, p.text, true);
            v.setGravity(Gravity.CENTER);
            cell.addView(k, new LinearLayout.LayoutParams(-1, -2));
            cell.addView(v, new LinearLayout.LayoutParams(-1, -2));
            card.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            shown++;
        }
        return card;
    }

    /** نوار هشدار لوکس با مدالیون، متن و دکمهٔ اقدام. */
    static LinearLayout alert(MeelanoUiHost h, I kind, String title, String body, String actionLabel,
                              int accent, final Runnable action) {
        MeelanoPalette p = h.colors();
        LinearLayout card = MeelanoUiKit.row(h);
        card.setPadding(h.dp(12), h.dp(11), h.dp(12), h.dp(11));
        card.setBackground(glass(p, accent, h.dp(20)));
        card.addView(medallion(h, kind, accent, 42), new LinearLayout.LayoutParams(h.dp(42), h.dp(42)));
        LinearLayout copy = MeelanoUiKit.column(h);
        copy.setPadding(h.dp(10), 0, h.dp(8), 0);
        copy.addView(MeelanoUiKit.text(h, title, 11.8f, p.text, true), new LinearLayout.LayoutParams(-1, -2));
        if (body != null && !body.isEmpty()) {
            TextView b = MeelanoUiKit.text(h, body, 9.8f, p.muted, false);
            b.setLineSpacing(h.dp(2), 1.05f);
            copy.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        if (actionLabel != null && action != null) {
            TextView go = value(h, actionLabel, 10f, 8.6f, p.text, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(h.dp(12), 0, h.dp(12), 0);
            go.setBackground(coin(p, accent, h.dp(14)));
            card.addView(go, new LinearLayout.LayoutParams(-2, h.dp(36)));
            card.setClickable(true);
            MeelanoUiKit.press(card, h);
            card.setOnClickListener(v -> action.run());
            card.setContentDescription(title + " — " + actionLabel);
        }
        return card;
    }

    /** سطر بینش: مدالیون کوچک + متن چندخطی خوانا (برای «بینش‌های مدیریتی»). */
    static LinearLayout insight(MeelanoUiHost h, String text, I kind, int accent) {
        MeelanoPalette p = h.colors();
        LinearLayout row = MeelanoUiKit.row(h);
        row.setGravity(Gravity.TOP);
        row.setPadding(h.dp(2), h.dp(7), h.dp(2), h.dp(7));
        row.addView(medallion(h, kind, accent, 30), new LinearLayout.LayoutParams(h.dp(30), h.dp(30)));
        TextView t = MeelanoUiKit.text(h, text, 10.8f, p.text, false);
        t.setLineSpacing(h.dp(3), 1.08f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMargins(h.dp(10), h.dp(4), 0, 0);
        row.addView(t, tlp);
        return row;
    }

    /** فاصله‌گذار عمودی. */
    static View gap(MeelanoUiHost h, float dp) {
        View v = new View(h.ctx());
        v.setLayoutParams(new LinearLayout.LayoutParams(1, h.dp(dp)));
        return v;
    }
}
