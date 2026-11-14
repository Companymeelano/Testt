package ir.meelano.android;

import android.graphics.Color;

/**
 * پالت رنگ فعال برنامه به‌صورت یک شیء قابل‌انتقال.
 *
 * <p>در نسخه‌های قبلی، رنگ‌ها فیلدهای خصوصی {@code MainActivity} بودند و هر کلاس تازه باید
 * از طریق آن‌ها به تم می‌رسید. با این کلاس، ماژول‌های جدید (انبار، مالی، برچسب) مستقل از
 * Activity هستند و در عین حال دقیقاً همان تم فعال کاربر را می‌گیرند.</p>
 */
final class MeelanoPalette {
    final int bg;          // NAVY در کد اصلی: پس‌زمینهٔ صفحه
    final int surface;     // SURFACE: سطح کارت
    final int surface2;    // SURFACE_2: سطح دوم (نوار، جدول)
    final int accent;      // GOLD: رنگ برند/اصلی
    final int accent2;     // GOLD_2: رنگ مکمل برند
    final int success;
    final int info;
    final int warning;
    final int danger;
    final int text;
    final int muted;
    final int border;
    final int onAccent;    // رنگ متن روی رنگ اصلی
    final boolean light;

    MeelanoPalette(int bg, int surface, int surface2, int accent, int accent2, int success, int info,
                   int warning, int danger, int text, int muted, int border, int onAccent, boolean light) {
        this.bg = bg;
        this.surface = surface;
        this.surface2 = surface2;
        this.accent = accent;
        this.accent2 = accent2;
        this.success = success;
        this.info = info;
        this.warning = warning;
        this.danger = danger;
        this.text = text;
        this.muted = muted;
        this.border = border;
        this.onAccent = onAccent;
        this.light = light;
    }

    /** رنگ معنایی بر اساس کلید هشدار. */
    int tone(String level) {
        if (level == null) return accent;
        if ("danger".equals(level)) return danger;
        if ("warn".equals(level) || "warning".equals(level)) return warning;
        if ("success".equals(level) || "ok".equals(level)) return success;
        if ("info".equals(level)) return info;
        return accent;
    }

    /** کمرنگ‌کردن یک رنگ (شفافیت نسبی). */
    int alpha(int color, int amount) { return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, amount)) & 0xFF) << 24); }

    /** ترکیب دو رنگ؛ ratio=۰ خالص from، ۱ خالص to. */
    int mix(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * r);
        int rr = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * r);
        int gg = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * r);
        int bb = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * r);
        return Color.argb(a, rr, gg, bb);
    }

    /** رنگ متن روی یک پس‌زمینهٔ دلخواه با تضمین خوانایی. */
    int onColorFor(int color) {
        double lum = relativeLuminance(color);
        double contrastWhite = 1.05 / (lum + 0.05);
        double contrastBlack = (lum + 0.05) / 0.05;
        return contrastWhite >= contrastBlack ? Color.WHITE : Color.rgb(16, 16, 16);
    }

    /** برنز — تنها گرمِ خنثی باقی‌مانده پس از بازنشستگی بنفش و قهوه‌ای. */
    static final int BRONZE = 0xFFB08968;

    /**
     * هر رنگ بخشِ میراثی را به خانواده‌های خانه تا می‌کند: برند + چهار رنگ معنایی + برنز.
     * بنفش/ارغوانی و قهوه‌ای دیگر به سطح نمی‌رسند؛ رنگ‌های نیمه‌شفاف و مقادیر خودِ پالت دست‌نخور می‌مانند.
     */
    int foldFamily(int accent) {
        if (accent == 0) return this.accent;
        if (((accent >>> 24) & 0xFF) != 0xFF) return accent;
        if (accent == this.accent || accent == accent2 || accent == success || accent == info
                || accent == warning || accent == danger || accent == text || accent == muted
                || accent == BRONZE) return accent;
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        float h = hsv[0], s = hsv[1];
        if (s < 0.14f) return muted;
        if (h >= 235f && h < 340f) return BRONZE;   // violet/purple/magenta retired
        if (h >= 340f || h < 12f) return danger;
        if (h < 45f) return s < 0.55f ? BRONZE : warning;
        if (h < 75f) return accent2;
        if (h < 170f) return success;
        return info;
    }

    /** جوهر رنگی که روی تیره‌ترین سطح کارت تم فعال ≥4.5:1 می‌دهد. */
    int sectionInk(int family) {
        int f = foldFamily(family);
        int ink = light ? mix(f, Color.rgb(16, 14, 8), 0.42f) : mix(f, Color.rgb(255, 252, 240), 0.50f);
        return textSafe(ink, surface2, 4.5);
    }

    /** واش ۱۴٪ خانواده برای چیپ‌ها و سطرهای آرام. */
    int softChip(int family) { return mix(surface, foldFamily(family), light ? 0.14f : 0.16f); }

    /** تا رسیدن به کف کنتراست به سمت قطب جوهر تم مخلوط می‌کند (آینهٔ موتور manager). */
    int textSafe(int preferred, int background, double target) {
        int pole = light ? Color.rgb(8, 10, 14) : Color.WHITE;
        int result = preferred;
        for (int i = 0; i < 24 && contrastRatio(result, background) < target; i++) result = mix(result, pole, 0.08f);
        return contrastRatio(result, background) < target ? pole : result;
    }

    double contrastRatio(int a, int b) {
        double la = relativeLuminance(a), lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static double relativeLuminance(int color) {
        double r = channel(Color.red(color)), g = channel(Color.green(color)), b = channel(Color.blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
