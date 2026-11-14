package ir.meelano.android;

import android.content.Context;
import android.graphics.Typeface;

/**
 * پل ارتباطی ماژول‌های تازهٔ رابط کاربری (انبار، مالی، برچسب‌ها) با {@code MainActivity}.
 *
 * <p>هدف معماری: از این پس هر بخش جدید برنامه می‌تواند به‌صورت یک کلاس مستقل نوشته شود و فقط
 * از طریق این واسط به «تم، فونت، اتصال پایگاه داده، ناوبری و پیام‌ها» دسترسی داشته باشد. این کار
 * فایل ۱۷٬۰۰۰+ خطی را کوچک‌تر نمی‌کند، اما جلوی رشد بیشتر آن را می‌گیرد و امکان آزمون
 * واحد ماژول‌های تازه را (بدون اندروید) فراهم می‌کند.</p>
 *
 * <p>پیاده‌سازی این واسط در {@code MainActivity} است (متدهای کوتاه انتهای کلاس).</p>
 */
interface MeelanoUiHost {

    /** Context برای ساخت Viewها. */
    Context ctx();

    /** تبدیل dp به پیکسل (همان فرمول سراسری برنامه). */
    int dp(float value);

    /** رنگ‌های تم فعال برنامه. */
    MeelanoPalette colors();

    /** فونت Meelano (وزیرمتن) با وزن خواسته‌شده. */
    Typeface font(boolean bold);

    boolean lightTheme();

    boolean motionAllowed();

    boolean compactUi();

    /** رفتن به یک صفحهٔ برنامه (مثل «products» یا «warehouse»). */
    void goTo(String page);

    /** پیام کوتاه (Snackbar/Toast استایل‌شدهٔ برنامه). */
    void notify(String message);

    void notify(String message, boolean longDuration);

    /** اجرای کار پایگاه داده در پس‌زمینه با نمایش وضعیت اتصال. */
    void runDb(MeelanoTask task, MeelanoResult result);

    /** تبدیل گلیف‌ها به آیکون وکتور (همان موتور MeelanoIcons). */
    CharSequence iconize(CharSequence text);

    /** رقم‌های فارسی. */
    String fa(String raw);

    /** عدد با جداکنندهٔ هزارگان فارسی. */
    String grouped(double value);

    /** مبلغ ریالی خوانا. */
    String money(double value);

    /** زمان آخرین تازه‌سازی یک صفحه. */
    String lastRefresh(String page);

    void markRefresh(String page);

    /** ساخت و اشتراک‌گذاری فایل (PDF/CSV) از حافظه. */
    void shareGeneratedFile(String fileName, String mimeType, byte[] data, String title);

    /** ذخیره/خواندن تنظیمات کوچک ماژول‌ها در SharedPreferences خود برنامه. */
    String setting(String key, String fallback);

    void putSetting(String key, String value);

    /** کار پس‌زمینه که رشته برمی‌گرداند. */
    interface MeelanoTask { String run() throws Exception; }

    /** نتیجهٔ کار پس‌زمینه. */
    interface MeelanoResult { void ok(String body); void fail(Exception e); }
}
