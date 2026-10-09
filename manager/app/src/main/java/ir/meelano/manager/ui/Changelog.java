package ir.meelano.manager.ui;

/**
 * Customer-facing «What's New» notes, newest version first. Shown once
 * after every upgrade (and on demand from Settings » تازه‌های نسخه).
 *
 * Copy rules: brief = ONE short line (never annoying), detail = the full
 * story, where = exactly where to find it in the app. Internal / seller-side
 * changes are deliberately never mentioned here.
 */
public final class Changelog {
    private Changelog() { }

    public static final int NEW = 0;
    public static final int FIX = 1;
    public static final int UP = 2;

    public static final class Item {
        public final int kind;
        public final String glyph;
        public final String title;
        public final String brief;
        public final String detail;
        public final String where;

        public Item(int kind, String glyph, String title, String brief, String detail, String where) {
            this.kind = kind;
            this.glyph = glyph;
            this.title = title;
            this.brief = brief;
            this.detail = detail;
            this.where = where;
        }
    }

    public static final class Ver {
        public final int code;
        public final String name;
        public final String dateFa;
        public final String headline;
        public final Item[] items;

        public Ver(int code, String name, String dateFa, String headline, Item[] items) {
            this.code = code;
            this.name = name;
            this.dateFa = dateFa;
            this.headline = headline;
            this.items = items;
        }
    }

    public static final Ver[] ALL = {
            new Ver(17, "24.0.0", "مهر ۱۴۰۵", "بروزرسانی خودکار، بدون دردسر", new Item[]{
                    new Item(NEW, "⬆", "بروزرسانی خودکار برنامه",
                            "نسخه جدید که بیاید، خود برنامه خبرتان می‌کند؛ با یک لمس نصب می‌شود.",
                            "دیگر لازم نیست دنبال فایل نصب بگردید. هر بار که برنامه را باز می‌کنید، اگر نسخه جدیدی منتشر شده باشد یک اعلان شیک می‌بینید؛ با زدن «دریافت و نصب»، دانلود امن، بررسی صحت فایل و نصب، همه خودکار انجام می‌شود و مستقیم وارد برنامه می‌شوید. این امکان فقط برای دارندگان لایسنس فعال (خرید یا آزمایشی) نمایش داده می‌شود.",
                            "خودکار هنگام ورود به برنامه • تنظیمات » بررسی بروزرسانی"),
                    new Item(NEW, "✦", "صفحه «تازه‌های نسخه»",
                            "بعد از هر نصب، تغییرات مهم را کوتاه و شیک همین‌جا می‌بینید.",
                            "از این به بعد هر نسخه جدید که نصب کنید، اول همین صفحه زیبا باز می‌شود: مهم‌ترین تغییرات در چند خط کوتاه، و برای هر مورد یک دکمه «نمایش جزئیات» که توضیح کامل و محل دقیق آن بخش در برنامه را می‌گوید. اگر حوصله خواندن نداشتید، «ورود به برنامه» شما را مستقیم می‌برد داخل و بعداً از تنظیمات می‌توانید برگردید.",
                            "خودکار بعد از هر نصب • تنظیمات » تازه‌های نسخه"),
            }),
            new Ver(16, "23.0.0", "مهر ۱۴۰۵", "فعال‌سازی ساده‌تر، خروجی‌های تازه", new Item[]{
                    new Item(NEW, "📲", "فعال‌سازی در دو قدم ساده",
                            "قدم اول: درخواست کد. قدم دوم: وصل شدن به آتیران. تمام.",
                            "صفحه فعال‌سازی از نو طراحی شد: اول نام پخش و شماره موبایل را می‌زنید و «دریافت کد فعال‌سازی» را می‌زنید؛ کد فروشنده خودکار اعمال می‌شود. بعد هم با راهنمای قدم‌به‌قدم به سرور آتیران وصل می‌شوید و مشخصات اتصال، خودکار برای فروشنده ارسال می‌شود تا پرونده‌تان کامل بماند.",
                            "صفحه فعال‌سازی (اولین ورود به برنامه)"),
                    new Item(NEW, "📄", "خروجی Word و عکس از گزارش‌ها",
                            "هر گزارش را علاوه بر PDF و اکسل، به Word و عکس هم تبدیل کنید.",
                            "نوار خروجی پایین هر گزارش حالا پنج حالت دارد: PDF، اکسل، ورد (فایل واقعی docx با جدول راست‌به‌چپ و سربرگ فروشگاه شما)، عکس PNG از جدول برای ارسال سریع در پیام‌رسان‌ها، و چاپ مستقیم.",
                            "هر گزارش » نوار خروجی پایین صفحه"),
                    new Item(UP, "🏷", "نام فروشگاه شما در سربرگ گزارش‌ها",
                            "به‌جای نام عمومی، نام پخش شما روی همه گزارش‌ها می‌آید.",
                            "نامی که هنگام فعال‌سازی وارد می‌کنید، در سربرگ PDF، اکسل، ورد و عکس گزارش‌ها چاپ می‌شود؛ گزارش‌هایتان رسمی‌تر و مخصوص فروشگاه شماست. از صفحه فعال‌سازی هم می‌توانید آن را ویرایش کنید.",
                            "همه گزارش‌ها (خودکار)"),
                    new Item(UP, "💾", "حفظ اطلاعات فعال‌سازی",
                            "با تعویض گوشی، اطلاعات فروشنده و پخش شما حفظ می‌شود.",
                            "از این نسخه اطلاعات فعال‌سازی و تنظیمات در پشتیبان امن اندروید ذخیره می‌شود تا فعال‌سازی مجدد روی گوشی تازه، سریع‌تر انجام شود.",
                            "خودکار (پشتیبان گوگل)"),
            }),
            new Ver(15, "22.0.0", "مهر ۱۴۰۵", "گزارش‌های عمیق‌تر، اکسل واقعی", new Item[]{
                    new Item(NEW, "👆", "گردش حساب کلیک‌پذیر",
                            "روی هر سطر گردش حساب بزنید تا سندش را ببینید.",
                            "گردش حساب دیگر فقط یک لیست نیست: با لمس هر سطر، جزئیات کامل همان سند باز می‌شود؛ پیگیری حساب‌ها خیلی سریع‌تر شد.",
                            "گزارش‌ها » گردش حساب"),
                    new Item(NEW, "📊", "فایل اکسل واقعی",
                            "خروجی اکسل حالا فایل xlsx استاندارد است.",
                            "دکمه اکسل در نوار خروجی، یک فایل واقعی اکسل می‌سازد که در همه برنامه‌ها (Excel، گوگل‌شیت و…) درست باز می‌شود؛ با سربرگ فارسی و جدول تمیز.",
                            "هر گزارش » نوار خروجی » اکسل"),
                    new Item(NEW, "🖨", "چاپ مستقیم",
                            "گزارش‌ها را مستقیم از برنامه چاپ کنید.",
                            "با دکمه چاپ در نوار خروجی، گزارش همان لحظه برای چاپگر ارسال می‌شود؛ بدون ذخیره و ارسال فایل.",
                            "هر گزارش » نوار خروجی » چاپ"),
                    new Item(UP, "🗂", "دسته‌بندی گزارش‌ها",
                            "گزارش‌ها مرتب و دسته‌بندی شدند تا سریع‌تر پیدا شوند.",
                            "بخش گزارش‌ها دسته‌بندی شد و هر گزارش سر جای خودش ایستاد؛ به‌علاوه یک بخش «ویژه مدیر» برای مهم‌ترین نگاه‌ها اضافه شد.",
                            "بخش گزارش‌ها"),
            }),
            new Ver(14, "21.0.0", "مهر ۱۴۰۵", "دستیار صوتی، پیامک بدهکاران، کار آفلاین", new Item[]{
                    new Item(NEW, "🎙", "دستیار صوتی",
                            "با صدا جستجو کنید و فرمان بدهید.",
                            "دکمه میکروفون را بزنید و حرف بزنید: جستجوی مشتری، کالا و گزارش با صدای شما انجام می‌شود؛ برای وقتی که دست‌تان بند است.",
                            "دکمه میکروفون در جستجو"),
                    new Item(NEW, "💬", "پیامک به بدهکاران",
                            "از داخل برنامه به بدهکاران پیامک بزنید.",
                            "در لیست مشتریان، روی سطر بدهکار بزنید و مستقیم پیامک پیگیری بفرستید؛ بدون خروج از برنامه و بدون تایپ شماره.",
                            "مشتریان » سطر بدهکار"),
                    new Item(UP, "⚡", "کار بدون اینترنت",
                            "با حافظه آفلاین، قطعی اینترنت شما را متوقف نمی‌کند.",
                            "اطلاعات مهم به‌صورت خودکار ذخیره می‌شود تا اگر اینترنت یا اتصال به سرور لحظه‌ای قطع شد، همچنان بتوانید کار کنید و بعداً همگام شوید.",
                            "خودکار در همه بخش‌ها"),
            }),
    };

    /** Versions with notes newer than lastCode (fresh install → current only), newest first. */
    public static java.util.List<Ver> since(int lastCode, int curCode) {
        java.util.List<Ver> out = new java.util.ArrayList<>();
        int from = lastCode <= 0 ? curCode : lastCode + 1;
        for (Ver v : ALL) {
            if (v.code >= from && v.code <= curCode) out.add(v);
            if (out.size() >= 4) break;
        }
        return out;
    }

    public static String kindFa(int kind) {
        if (kind == FIX) return "🛠 رفع اشکال";
        if (kind == UP) return "⬆ بهبود";
        return "✦ جدید";
    }

    public static int kindColor(int kind) {
        if (kind == FIX) return Theme.INFO;
        if (kind == UP) return Theme.SUCCESS;
        return Theme.GOLD;
    }
}
