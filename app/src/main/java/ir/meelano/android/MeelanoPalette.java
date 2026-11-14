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

    static double relativeLuminance(int color) {
        double r = channel(Color.red(color)), g = channel(Color.green(color)), b = channel(Color.blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
