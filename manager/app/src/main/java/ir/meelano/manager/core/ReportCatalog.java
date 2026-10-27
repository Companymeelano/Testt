package ir.meelano.manager.core;

import ir.meelano.manager.data.Meta;

/** The complete report list: every report the system offers, in one place. */
public final class ReportCatalog {
    private ReportCatalog() { }

    public static final int T_TEXT = 0;
    public static final int T_MONEY = 1;
    public static final int T_DATE = 2;
    public static final int T_NUM = 3;

    public static final int C_NONE = 0;
    public static final int C_BARS = 1;
    public static final int C_LINE = 2;
    public static final int C_DONUT = 3;

    public static final class Col {
        public final String key;
        public final String title;
        public final int type;

        public Col(String key, String title, int type) {
            this.key = key;
            this.title = title;
            this.type = type;
        }
    }

    public static final class Spec {
        public final String id;
        public final String title;
        public final String section;
        public final String desc;
        public final boolean needsRange;
        public final Col[] cols;
        public final int chart;
        public final String chartX;
        public final String chartY;

        Spec(String id, String title, String section, String desc, boolean needsRange,
             Col[] cols, int chart, String chartX, String chartY) {
            this.id = id;
            this.title = title;
            this.section = section;
            this.desc = desc;
            this.needsRange = needsRange;
            this.cols = cols;
            this.chart = chart;
            this.chartX = chartX;
            this.chartY = chartY;
        }
    }

    private static Spec s(String id, String title, String section, String desc, boolean range,
                          Col[] cols, int chart, String x, String y) {
        return new Spec(id, title, section, desc, range, cols, chart, x, y);
    }

    private static Col c(String k, String t, int type) {
        return new Col(k, t, type);
    }

    /** The group that holds the financial sections (treasury). */
    public static final String TREASURY = "خزانه‌داری";

    /** Sub-sections shown under «خزانه‌داری». */
    public static boolean isTreasurySection(String section) {
        return "دریافتی‌ها و پرداخت".equals(section) || "چک‌ها".equals(section)
                || "عملکرد صندوق".equals(section) || "بانک و صندوق".equals(section)
                || "سرفصل‌ها".equals(section) || "هزینه‌ها".equals(section);
    }

    public static final Spec[] ALL = {
            // ---- ویژه مدیر (v22: the vital few, checked every morning) ----
            s("today_top", "پرفروش‌ترین‌های امروز", "ویژه مدیر", "کالاهای دارای بیشترین فروش ریالی امروز", false,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("total", "جمع فروش", T_MONEY), c("docs", "ردیف", T_NUM)},
                    C_BARS, "label", "total"),
            s("cheques_today", "سررسیدهای امروز و فردا", "ویژه مدیر", "چک‌های دریافتی با سررسید امروز یا فردا — اقدام فوری", false,
                    new Col[]{c("num", "شماره", T_TEXT), c("customer", "مشتری", T_TEXT), c("bank", "بانک", T_TEXT),
                            c("amount", "مبلغ", T_MONEY), c("sardate", "سررسید", T_DATE)},
                    C_NONE, "", ""),
            s("dying_stock", "اتمام موجودی فوری", "ویژه مدیر", "کالاهایی که تا ۷ روز آینده تمام می‌شوند — سفارش بدهید", false,
                    new Col[]{c("label", "کالا", T_TEXT), c("vah", "موجودی", T_NUM), c("daily", "فروش روزانه", T_NUM),
                            c("daysLeft", "روز تا اتمام", T_NUM), c("status", "وضعیت", T_TEXT)},
                    C_NONE, "", ""),
            s("fresh_debtors", "بدهکاران تازه", "ویژه مدیر", "بدهکارانی که ۳۰ روز اخیر خریده‌اند — بهترین هدف وصول", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("cell", "همراه", T_TEXT), c("balance", "مانده", T_MONEY),
                            c("lastSale", "آخرین خرید", T_DATE)},
                    C_NONE, "", ""),
            // ---- هوشمند (v13) ----
            s("fleeing_customers", "مشتریان در حال فرار", "هوشمند", "خریداران منظمی که بیش از ۴۵ روز است نیامده‌اند + زیان تقریبی", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("cell", "همراه", T_TEXT), c("balance", "مانده", T_MONEY),
                            c("lastBuy", "آخرین خرید", T_DATE), c("daysAway", "روز غیبت", T_NUM), c("invoices", "فاکتور سال", T_NUM),
                            c("avgBuy", "میانگین سبد", T_MONEY), c("lostEst", "زیان تقریبی", T_MONEY)},
                    C_BARS, "name", "lostEst"),
            s("lost_basket", "سبد گمشده", "هوشمند", "کالای مکملی که مشتری نخریده ولی مشابه‌خریداران خریده‌اند + ارزش فرصت", false,
                    new Col[]{c("customer", "مشتری", T_TEXT), c("bought", "خریده", T_TEXT), c("missed", "نخریده", T_TEXT),
                            c("together", "همراه‌خرید", T_NUM), c("missedValue", "ارزش فرصت", T_MONEY)},
                    C_NONE, "", ""),
            s("cheque_reliability", "خوش‌قولی چکی", "هوشمند", "رتبه‌بندی مشتریان بر اساس سابقه برگشتی چک (لمس ردیف: تماس)", false,
                    new Col[]{c("customer", "مشتری", T_TEXT), c("cell", "همراه", T_TEXT), c("total", "چک‌ها", T_NUM), c("bounced", "برگشتی", T_NUM),
                            c("bouncedAmt", "مبلغ برگشتی", T_MONEY), c("totalAmt", "جمع چک‌ها", T_MONEY),
                            c("rate", "نرخ برگشتی ٪", T_NUM), c("grade", "رتبه", T_TEXT)},
                    C_BARS, "customer", "bouncedAmt"),
            s("visitor_yield", "بازده ویزیتور", "تحلیل ویزیتور و مسیر", "مشتریان منتسب، فعال بازه و ریزش ۹۰روزه هر ویزیتور + فاکتور به‌ازای مشتری", true,
                    new Col[]{c("name", "ویزیتور", T_TEXT), c("assigned", "منتسب", T_NUM), c("active", "فعال بازه", T_NUM),
                            c("churned", "ریزش", T_NUM), c("invoices", "فاکتور", T_NUM), c("sales", "فروش", T_MONEY),
                            c("perCust", "فاکتور/مشتری", T_NUM)},
                    C_BARS, "name", "sales"),
            s("visitor_scorecard", "امتیاز و اقدام ویزیتورها", "تحلیل ویزیتور و مسیر",
                    "هدف ریالی عمومیِ فعال متناسب با روزهای هم‌پوشان محاسبه می‌شود؛ امتیاز تحقق فروش نسبت به هدف (سقف ۱۰۰): ۱۰۰٪ به بالا پیشتاز، ۸۰–۹۹ نزدیک هدف، ۶۰–۷۹ جبرانی، کمتر از ۶۰ مداخله فوری. اهداف تفکیکی جمع نمی‌شوند؛ پوشش و رشد فروش مکمل‌اند، وصول/سود در امتیاز نیست.", true,
                    new Col[]{c("name", "ویزیتور", T_TEXT), c("action", "گروه اقدام", T_TEXT),
                            c("score", "امتیاز /۱۰۰", T_TEXT), c("attainment", "تحقق هدف", T_TEXT),
                            c("sales", "فروش", T_MONEY), c("target", "هدف متناسب", T_MONEY),
                            c("portfolio", "خریدار/منتسب • پوشش", T_TEXT), c("invoices", "فاکتور", T_NUM),
                            c("growth", "تغییر با دوره قبل", T_TEXT)},
                    C_NONE, "", ""),
            s("golden_hours", "ساعت طلایی فروش", "هوشمند", "پرفروش‌ترین ساعت‌ها (یا روزهای هفته، اگر ساعت ثبت نشده باشد)", true,
                    new Col[]{c("slot", "بازه", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "slot", "total"),
            s("sleeping_capital", "سرمایه خوابیده", "هوشمند", "کالاهای بدون فروش ۹۰ روزه + سرمایه قفل‌شده (پیشنهاد: تسویه پلکانی)", false,
                    new Col[]{c("label", "کالا", T_TEXT), c("shka", "کد", T_TEXT), c("vah", "موجودی", T_NUM),
                            c("buyValue", "سرمایه خوابیده", T_MONEY), c("lastSale", "آخرین فروش", T_DATE)},
                    C_BARS, "label", "buyValue"),
            s("stock_forecast", "پیش‌بینی اتمام موجودی", "هوشمند", "چند روز تا اتمام، بر اساس فروش روزانه ۶۰ روزه + هشدار سفارش", false,
                    new Col[]{c("label", "کالا", T_TEXT), c("vah", "موجودی", T_NUM), c("daily", "فروش روزانه", T_NUM),
                            c("daysLeft", "روز تا اتمام", T_NUM), c("status", "وضعیت", T_TEXT)},
                    C_NONE, "", ""),
            s("order_suggest", "فهرست سفارش پیشنهادی", "هوشمند", "مقدار پیشنهادی سفارش برای پوشش ۳۰ روزه + اشتراک PDF با تأمین‌کننده", false,
                    new Col[]{c("label", "کالا", T_TEXT), c("vah", "موجودی", T_NUM), c("daily", "فروش روزانه", T_NUM),
                            c("daysLeft", "روز تا اتمام", T_NUM), c("suggest", "پیشنهاد سفارش", T_NUM), c("status", "وضعیت", T_TEXT)},
                    C_NONE, "", ""),
            s("invoice_profit", "سود واقعی فاکتور", "هوشمند", "سود هر فاکتور با کسر تخفیف و بهای تمام‌شده (مبنای قیمت خرید)", true,
                    new Col[]{c("no", "فاکتور", T_TEXT), c("customer", "مشتری", T_TEXT), c("date", "تاریخ", T_DATE),
                            c("amount", "مبلغ", T_MONEY), c("discount", "تخفیف", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود", T_MONEY), c("margin", "حاشیه ٪", T_NUM)},
                    C_LINE, "date", "profit"),
            // ---- فروش ----
            s("sales_daily", "فروش روزانه", "فروش", "جمع و تعداد فاکتور فروش هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_LINE, "day", "total"),
            s("sales_top_products", "پرفروش‌ترین کالاها", "فروش", "کالاهای دارای بیشترین فروش ریالی", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("total", "جمع فروش", T_MONEY), c("docs", "ردیف", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_by_customer", "فروش مشتریان", "فروش", "برترین مشتریان از نظر خرید در بازه", true,
                    new Col[]{c("label", "مشتری", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_monthly", "فروش ماهانه", "فروش", "جمع و تعداد فاکتور فروش هر ماه", false,
                    new Col[]{c("month", "ماه", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "month", "total"),
            s("yoy_sales", "مقایسه فروش با پارسال", "فروش", "فروش ماهانه امسال در برابر پارسال + درصد رشد", false,
                    new Col[]{c("mlab", "ماه", T_TEXT), c("thisY", "فروش امسال", T_MONEY), c("lastY", "فروش پارسال", T_MONEY), c("growth", "رشد ٪", T_NUM)},
                    C_BARS, "mlab", "thisY"),
            s("sales_price_spread", "پراکندگی قیمت فروش", "فروش", "اختلاف کمترین و بیشترین بهای واحد فروش هر کالا (نشت تخفیف)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("docs", "اسناد", T_NUM), c("qty", "مقدار", T_NUM),
                            c("minUnit", "کمترین بها", T_MONEY), c("maxUnit", "بیشترین بها", T_MONEY),
                            c("avgUnit", "میانگین بها", T_MONEY), c("spread", "اختلاف ٪", T_NUM)},
                    C_NONE, "", ""),
            s("sales_by_visitor", "فروش ویزیتورها", "تحلیل ویزیتور و مسیر", "سهم هر ویزیتور از فروش بازه", true,
                    new Col[]{c("label", "ویزیتور", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_by_route", "فروش مسیرها", "تحلیل ویزیتور و مسیر", "سهم هر مسیر از فروش بازه", true,
                    new Col[]{c("label", "مسیر", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_by_group", "فروش گروه‌های مشتری", "فروش", "سهم هر گروه مشتری از فروش", true,
                    new Col[]{c("label", "گروه", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_DONUT, "label", "total"),
            s("sales_by_kala_group", "فروش گروه‌های کالا", "فروش", "سهم هر گروه کالا از فروش بازه", true,
                    new Col[]{c("label", "گروه کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_DONUT, "label", "total"),
            s("invoice_unsettled", "فاکتورهای غیر نقدی (تسویه‌نشده)", "فروش", "فاکتورهای فروش که هنوز به‌طور کامل تسویه نشده‌اند — همه فیلترهای بازه و مشتری فعال است", true,
                    new Col[]{c("no", "فاکتور", T_TEXT), c("date", "تاریخ", T_DATE), c("customer", "مشتری", T_TEXT),
                            c("visitor", "ویزیتور", T_TEXT), c("total", "مبلغ فاکتور", T_MONEY), c("paid", "پرداختی", T_MONEY),
                            c("remain", "مانده", T_MONEY), c("dueDate", "سررسید", T_DATE)},
                    C_BARS, "customer", "remain"),
            s("unsettled", "فاکتورهای معوق", "فروش", "فاکتورهای تسویه‌نشده با سررسید گذشته", false,
                    new Col[]{c("invoice", "فاکتور", T_TEXT), c("party", "مشتری", T_TEXT), c("amount", "مبلغ", T_MONEY),
                            c("dueDate", "سررسید", T_DATE), c("days", "روز تأخیر", T_NUM), c("visitor", "ویزیتور", T_TEXT)},
                    C_NONE, "", ""),
            s("aging", "سن بدهی — توزیع مانده", "تحلیل ویزیتور و مسیر", "مانده بدهی فاکتورهای تسویه‌نشده به تفکیک سن بدهی (روز از سررسید)", false,
                    new Col[]{c("bucket", "سن بدهی", T_TEXT), c("amount", "مانده", T_MONEY), c("count", "فاکتور", T_NUM)},
                    C_DONUT, "bucket", "amount"),
            s("debtors_aging", "بدهکاران به تفکیک سن بدهی", "تحلیل ویزیتور و مسیر", "هر مشتری بدهکار با مانده در هر بازه سنی و ویزیتور او", false,
                    new Col[]{c("customer", "مشتری", T_TEXT), c("visitor", "ویزیتور", T_TEXT),
                            c("b0", "جاری", T_MONEY), c("b1", "۱–۳۰", T_MONEY), c("b2", "۳۱–۶۰", T_MONEY),
                            c("b3", "۶۱–۹۰", T_MONEY), c("b4", "۹۱–۱۸۰", T_MONEY), c("b5", "+۱۸۰", T_MONEY),
                            c("total", "جمع مانده", T_MONEY), c("docs", "فاکتور", T_NUM)},
                    C_BARS, "customer", "total"),
            s("visitor_aging", "سن بدهی به تفکیک ویزیتور", "تحلیل ویزیتور و مسیر", "مانده بدهی هر ویزیتور در بازه‌های سنی", false,
                    new Col[]{c("visitor", "ویزیتور", T_TEXT),
                            c("b0", "جاری", T_MONEY), c("b1", "۱–۳۰", T_MONEY), c("b2", "۳۱–۶۰", T_MONEY),
                            c("b3", "۶۱–۹۰", T_MONEY), c("b4", "۹۱–۱۸۰", T_MONEY), c("b5", "+۱۸۰", T_MONEY),
                            c("total", "جمع مانده", T_MONEY), c("customers", "مشتری", T_NUM)},
                    C_BARS, "visitor", "total"),
            // ---- خرید ----
            s("buy_daily", "خرید روزانه", "خرید", "جمع و تعداد فاکتور خرید هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع خرید", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_LINE, "day", "total"),
            s("buy_top_products", "پرخریدترین کالاها", "خرید", "کالاهای دارای بیشترین خرید ریالی", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("total", "جمع خرید", T_MONEY), c("docs", "ردیف", T_NUM)},
                    C_BARS, "label", "total"),
            s("buy_monthly", "خرید ماهانه", "خرید", "جمع و تعداد فاکتور خرید هر ماه", false,
                    new Col[]{c("month", "ماه", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "month", "total"),
            s("buy_price_trend", "روند قیمت خرید", "خرید", "تغییر بهای واحد آخرین خرید نسبت به خرید قبلی هر کالا (درصد)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("prevUnit", "خرید قبلی (واحد)", T_MONEY),
                            c("lastUnit", "خرید اخیر (واحد)", T_MONEY), c("change", "تغییر ٪", T_NUM),
                            c("docs", "تعداد خرید", T_NUM)},
                    C_NONE, "", ""),
            s("supplier_price_gap", "شکاف قیمت تأمین‌کنندگان", "خرید", "بهای واحد هر تأمین‌کننده نسبت به میانگین همان کالا از سایر تأمین‌کنندگان", true,
                    new Col[]{c("label", "تأمین‌کننده", T_TEXT), c("product", "کالا", T_TEXT),
                            c("supUnit", "بهای این تأمین‌کننده", T_MONEY), c("marketUnit", "میانگین بازار", T_MONEY),
                            c("gap", "شکاف ٪", T_NUM), c("qty", "مقدار", T_NUM), c("sups", "تعداد تأمین‌کننده", T_NUM)},
                    C_NONE, "", ""),
            s("buy_by_party", "خرید از طرف‌حساب‌ها", "خرید", "برترین فروشندگان/تأمین‌کنندگان", true,
                    new Col[]{c("label", "طرف‌حساب", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            // ---- دریافتی‌ها و پرداخت (زیرمجموعه خزانه‌داری) ----
            s("in_daily", "دریافتی‌های روزانه", "دریافتی‌ها و پرداخت", "جمع قبوض دریافت هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع دریافت", T_MONEY), c("count", "قبوض", T_NUM)},
                    C_LINE, "day", "total"),
            s("out_daily", "پرداخت روزانه", "دریافتی‌ها و پرداخت", "جمع قبوض پرداخت هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع پرداخت", T_MONEY), c("count", "قبوض", T_NUM)},
                    C_LINE, "day", "total"),
            s("pos_by_bank", "گردش پوز بانک‌ها", "دریافتی‌ها و پرداخت", "تفکیک مبالغ کارت‌خوان به بانک", true,
                    new Col[]{c("bank", "بانک", T_TEXT), c("total", "جمع", T_MONEY), c("count", "تراکنش", T_NUM)},
                    C_DONUT, "bank", "total"),
            // ---- چک ----
            s("cheque_due_in", "سررسیدهای نزدیک (دریافتی)", "چک‌ها", "چک‌های دریافتی با سررسید ۷ روز آینده", false,
                    new Col[]{c("num", "شماره", T_TEXT), c("customer", "مشتری", T_TEXT), c("bank", "بانک", T_TEXT),
                            c("amount", "مبلغ", T_MONEY), c("sardate", "سررسید", T_DATE)},
                    C_NONE, "", ""),
            s("cheque_due_out", "سررسیدهای نزدیک (پرداختی)", "چک‌ها", "چک‌های پرداختی با سررسید ۷ روز آینده", false,
                    new Col[]{c("num", "شماره", T_TEXT), c("customer", "طرف‌حساب", T_TEXT), c("bank", "بانک", T_TEXT),
                            c("amount", "مبلغ", T_MONEY), c("sardate", "سررسید", T_DATE)},
                    C_NONE, "", ""),
            s("cheque_bounced_in", "چک‌های برگشتی دریافتی", "چک‌ها", "چک‌های برگشتی/استرداد شده دریافتی", true,
                    new Col[]{c("num", "شماره", T_TEXT), c("customer", "مشتری", T_TEXT), c("bank", "بانک", T_TEXT),
                            c("amount", "مبلغ", T_MONEY), c("sardate", "سررسید", T_DATE)},
                    C_NONE, "", ""),
            // ---- کالا و انبار ----
            s("out_of_stock", "کالاهای ناموجود", "کالا و انبار", "کالاهای با موجودی صفر یا منفی", false,
                    new Col[]{c("naka", "کالا", T_TEXT), c("code", "کد", T_TEXT), c("unit", "واحد", T_TEXT), c("vah", "موجودی", T_NUM)},
                    C_NONE, "", ""),
            s("low_stock", "کالاهای کم‌موجودی", "کالا و انبار", "کالاهای با موجودی کمتر از حد هشدار", false,
                    new Col[]{c("naka", "کالا", T_TEXT), c("code", "کد", T_TEXT), c("unit", "واحد", T_TEXT), c("vah", "موجودی", T_NUM)},
                    C_NONE, "", ""),
            s("stock_value", "ارزش موجودی گروه‌ها", "کالا و انبار", "ارزش ریالی موجودی به تفکیک گروه کالا", false,
                    new Col[]{c("label", "گروه", T_TEXT), c("value", "ارزش", T_MONEY), c("count", "اقلام", T_NUM)},
                    C_DONUT, "label", "value"),
            s("expiry_watch", "هشدار انقضای موجودی", "کالا و انبار",
                    "موجودی دارای تاریخ انقضای ثبت‌شده در پرونده کالا؛ کالاهای منقضی و حداکثر ۹۰ روز آینده به‌ترتیب فوریت. تاریخ در سطح کالا است، نه بچ/لات.", false,
                    new Col[]{c("name", "کالا", T_TEXT), c("code", "کد", T_TEXT), c("expiry", "تاریخ انقضا", T_DATE),
                            c("daysLeft", "روز مانده", T_NUM), c("qty", "موجودی قطعه‌ای", T_NUM),
                            c("exposureValue", "ارزش بهای در معرض", T_MONEY), c("status", "اقدام", T_TEXT)},
                    C_NONE, "", ""),
            // ---- مشتریان ----
            s("debtors", "بدهکاران", "مشتریان", "مشتریان دارای مانده بدهی، مرتب‌شده", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("cell", "همراه", T_TEXT), c("balance", "مانده", T_MONEY),
                            c("salesTotal", "جمع فروش", T_MONEY), c("lastSale", "آخرین خرید", T_DATE)},
                    C_NONE, "", ""),
            s("creditors", "بستانکاران", "مشتریان", "مشتریان دارای مانده بستانکاری", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("cell", "همراه", T_TEXT), c("balance", "مانده", T_MONEY),
                            c("salesTotal", "جمع فروش", T_MONEY), c("lastSale", "آخرین خرید", T_DATE)},
                    C_NONE, "", ""),
            s("new_customers", "مشتریان جدید", "مشتریان", "مشتریان ثبت‌شده در بازه", true,
                    new Col[]{c("code", "کد", T_TEXT), c("name", "مشتری", T_TEXT), c("date", "تاریخ ثبت", T_DATE), c("visitor", "ویزیتور", T_TEXT)},
                    C_NONE, "", ""),
            s("inactive_customers", "مشتریان بدون خرید", "مشتریان", "مشتریان بدون هیچ فاکتور فروش", false,
                    new Col[]{c("code", "کد", T_TEXT), c("party", "مشتری", T_TEXT)},
                    C_NONE, "", ""),
            // ---- ویزیتور ----
            s("visitor_profit", "سود ویزیتورها", "تحلیل ویزیتور و مسیر", "فروش، بهای تمام‌شده (بر پایه قیمت خرید) و حاشیه سود هر ویزیتور", true,
                    new Col[]{c("label", "ویزیتور", T_TEXT), c("docs", "فاکتور", T_NUM), c("sales", "فروش", T_MONEY),
                            c("cogs", "بهای تمام‌شده", T_MONEY), c("profit", "سود", T_MONEY), c("margin", "حاشیه ٪", T_NUM)},
                    C_NONE, "", ""),
            s("visitor_collection", "وصول ویزیتورها", "تحلیل ویزیتور و مسیر", "مبلغ فاکتور، دریافتی، مانده و نرخ وصول هر ویزیتور", true,
                    new Col[]{c("label", "ویزیتور", T_TEXT), c("docs", "فاکتور", T_NUM), c("total", "فروش", T_MONEY),
                            c("collected", "دریافتی", T_MONEY), c("remain", "مانده", T_MONEY), c("rate", "نرخ وصول ٪", T_NUM)},
                    C_NONE, "", ""),
            s("visitor_perf", "عملکرد ویزیتورها", "تحلیل ویزیتور و مسیر", "فروش، وصول، مشتریان و اهداف هر ویزیتور", true,
                    new Col[]{c("name", "ویزیتور", T_TEXT), c("sales", "فروش", T_MONEY), c("collected", "وصول فاکتور", T_MONEY),
                            c("invoices", "فاکتور", T_NUM), c("customers", "مشتری فعال", T_NUM), c("goals", "هدف", T_MONEY)},
                    C_BARS, "name", "sales"),
            // ---- کاربران ----
            s("users", "کاربران و آخرین ورود", "پرسنل و نظارت", "کاربران سیستم، نقش و آخرین ورود", false,
                    new Col[]{c("name", "کاربر", T_TEXT), c("role", "نقش", T_TEXT), c("active", "فعال", T_TEXT),
                            c("lastLogin", "آخرین ورود", T_TEXT), c("todayN", "امروز", T_NUM)},
                    C_NONE, "", ""),
            s("changes", "تغییرات ثبت‌شده", "پرسنل و نظارت", "آخرین تغییرات ثبت‌شده در سیستم", true,
                    new Col[]{c("at", "زمان", T_TEXT), c("tableName", "جدول", T_TEXT), c("username", "کاربر", T_TEXT), c("descrip", "شرح", T_TEXT)},
                    C_NONE, "", ""),
            s("staff_attendance", "ممیزی حضور و تردد پرسنل", "پرسنل و نظارت",
                    "رویدادهای ورود/خروج، منبع ثبت، تأیید زیستی، فاصله و اصلاح/ابطال‌های مدیر؛ تأخیر یا غیبت بدون برنامه شیفت اعلام نمی‌شود.", true,
                    new Col[]{c("person", "پرسنل", T_TEXT), c("username", "شناسه", T_TEXT), c("event", "رویداد", T_TEXT),
                            c("eventTime", "زمان شمسی", T_TEXT), c("source", "منبع", T_TEXT), c("distance", "فاصله متر", T_NUM),
                            c("accuracy", "دقت متر", T_NUM), c("bio", "تأیید", T_TEXT), c("audit", "ممیزی/یادداشت", T_TEXT)},
                    C_NONE, "", ""),
            // ---- سود ----
            s("profit_daily", "سود روزانه", "سود و زیان", "فروش و بهای تمام‌شده هر روز (مبنای قیمت خرید)", true,
                    new Col[]{c("day", "روز", T_DATE), c("sales", "فروش", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود", T_MONEY)},
                    C_LINE, "day", "profit"),
            s("profit_monthly", "سود ماهانه", "سود و زیان", "فروش، بهای تمام‌شده و سود هر ماه شمسی — برای مقایسهٔ ماه‌ها", true,
                    new Col[]{c("day", "ماه", T_TEXT), c("sales", "فروش", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود", T_MONEY)},
                    C_BARS, "day", "profit"),
            s("profit_group", "سود گروه‌های کالا", "سود و زیان", "فروش، بهای تمام‌شده و سود هر گروه کالا", true,
                    new Col[]{c("label", "گروه", T_TEXT), c("sales", "فروش", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود", T_MONEY)},
                    C_BARS, "label", "profit"),
            s("profit_group_share", "سهم گروه‌ها از سود", "سود و زیان", "درصد سهم هر گروه کالا از کل سود دوره", true,
                    new Col[]{c("label", "گروه", T_TEXT), c("profit", "سود", T_MONEY), c("share", "سهم (٪)", T_NUM)},
                    C_DONUT, "label", "profit"),
            s("profit_loss_products", "کالاهای زیان‌ده", "سود و زیان", "کالاهایی که در دوره زیر بهای خرید فروخته شده‌اند — باید قیمت بازنگری شود", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("sales", "فروش", T_MONEY),
                            c("cogs", "بهای تمام‌شده", T_MONEY), c("profit", "زیان", T_MONEY)},
                    C_NONE, "", ""),
            s("profit_margin_bands", "توزیع حاشیه سود کالاها", "سود و زیان", "تعداد کالاها در هر بازهٔ حاشیه سود (زیان، کمتر از ۱۰٪، …، بالای ۴۰٪)", true,
                    new Col[]{c("band", "بازه حاشیه", T_TEXT), c("products", "تعداد کالا", T_NUM), c("sales", "فروش", T_MONEY),
                            c("profit", "سود", T_MONEY)},
                    C_BARS, "band", "products"),
            s("profit_discount_daily", "تخفیف روزانه و اثر آن", "سود و زیان", "جمع فروش، تخفیف داده‌شده و تعداد فاکتور هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("sales", "فروش", T_MONEY), c("discount", "تخفیف", T_MONEY),
                            c("docs", "فاکتور", T_NUM)},
                    C_LINE, "day", "discount"),
            s("profit_cumulative", "سود تجمعی ماهانه", "سود و زیان", "سود هر ماه به‌همراه جمع تجمعی از اولین ماه دوره", true,
                    new Col[]{c("day", "ماه", T_TEXT), c("sales", "فروش", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود ماه", T_MONEY), c("cum", "سود تجمعی", T_MONEY)},
                    C_LINE, "day", "cum"),
            s("profit_price_realization", "تحقق قیمت فروش", "سود و زیان", "میانگین قیمت فروش هر کالا به‌صورت درصدی از قیمت خرید (۱۰۰٪ = بدون سود)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("avgSell", "میانگین فروش", T_MONEY),
                            c("buy", "قیمت خرید", T_MONEY), c("realization", "تحقق (٪)", T_NUM)},
                    C_NONE, "", ""),
            s("buy_daily_totals", "خرید روزانه با مالیات و تخفیف", "خرید", "جمع خرید، مالیات، تخفیف و تعداد فاکتور هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع خرید", T_MONEY), c("tax", "مالیات", T_MONEY),
                            c("discount", "تخفیف", T_MONEY), c("docs", "فاکتور", T_NUM)},
                    C_LINE, "day", "total"),
            s("buy_monthly_totals", "خرید ماهانه و میانگین فاکتور", "خرید", "جمع خرید، تعداد فاکتور و میانگین مبلغ هر فاکتور در ماه", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "فاکتور", T_NUM),
                            c("avg", "میانگین فاکتور", T_MONEY)},
                    C_BARS, "month", "total"),
            s("buy_top_invoices", "بزرگ‌ترین فاکتورهای خرید", "خرید", "سی فاکتور خرید با بیشترین مبلغ در دوره", true,
                    new Col[]{c("no", "شماره", T_TEXT), c("date", "تاریخ", T_DATE), c("party", "طرف‌حساب", T_TEXT),
                            c("total", "مبلغ", T_MONEY)},
                    C_NONE, "", ""),
            s("buy_supplier_share", "سهم تأمین‌کنندگان از خرید", "خرید", "درصد سهم هر طرف‌حساب از کل خرید دوره", true,
                    new Col[]{c("label", "طرف‌حساب", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "فاکتور", T_NUM),
                            c("share", "سهم (٪)", T_NUM)},
                    C_DONUT, "label", "total"),
            s("buy_discount_by_supplier", "تخفیف گرفته‌شده از تأمین‌کنندگان", "خرید", "جمع تخفیف هر طرف‌حساب در خریدهای دوره", true,
                    new Col[]{c("label", "طرف‌حساب", T_TEXT), c("total", "جمع خرید", T_MONEY), c("discount", "تخفیف", T_MONEY),
                            c("docs", "فاکتور", T_NUM)},
                    C_BARS, "label", "discount"),
            s("buy_product_docs", "کالاهای خریداری‌شده و تعداد فاکتور", "خرید", "تعداد فاکتورهایی که هر کالا در آن خریده شده و مقدار خرید", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("docs", "فاکتور", T_NUM), c("qty", "مقدار", T_NUM)},
                    C_BARS, "label", "qty"),
            s("buy_dormant_stock", "موجودی بدون خرید در دوره", "خرید", "کالاهایی که موجودی دارند اما در این دوره خریداری نشده‌اند", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("stock", "موجودی", T_NUM), c("unit", "واحد", T_TEXT)},
                    C_NONE, "", ""),
            s("buy_party_last", "آخرین خرید از هر تأمین‌کننده", "خرید", "تعداد فاکتور، جمع خرید و تاریخ آخرین خرید از هر طرف‌حساب", true,
                    new Col[]{c("label", "طرف‌حساب", T_TEXT), c("docs", "فاکتور", T_NUM), c("total", "جمع خرید", T_MONEY),
                            c("last", "آخرین خرید", T_DATE)},
                    C_NONE, "", ""),
            s("buy_stock_cover", "پوشش موجودی نسبت به خرید", "خرید", "نسبت موجودی فعلی به مقدار خریداری‌شده در دوره (عدد بالاتر یعنی کندی گردش)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("bought", "خرید دوره", T_NUM), c("stock", "موجودی", T_NUM),
                            c("cover", "نسبت پوشش", T_NUM)},
                    C_NONE, "", ""),
            s("buy_vs_sales_monthly", "خرید در برابر فروش (ماهانه)", "خرید", "مقایسه جمع خرید و جمع فروش هر ماه شمسی", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("buy", "خرید", T_MONEY), c("sales", "فروش", T_MONEY)},
                    C_BARS, "month", "buy"),
            s("cust_by_kind", "فروش به تفکیک نوع مشتری", "مشتریان", "جمع فروش، تعداد مشتری و فاکتور به تفکیک کد نوع مشتری", true,
                    new Col[]{c("label", "نوع مشتری", T_TEXT), c("customers", "مشتری", T_NUM), c("total", "جمع فروش", T_MONEY),
                            c("docs", "فاکتور", T_NUM)},
                    C_BARS, "label", "total"),
            s("cust_by_personality", "فروش به تفکیک شخصیت مشتری", "مشتریان", "جمع فروش، تعداد مشتری و فاکتور به تفکیک نوع شخصیت", true,
                    new Col[]{c("label", "شخصیت", T_TEXT), c("customers", "مشتری", T_NUM), c("total", "جمع فروش", T_MONEY),
                            c("docs", "فاکتور", T_NUM)},
                    C_DONUT, "label", "total"),
            s("cust_no_cell", "مشتریان بدون شماره همراه", "مشتریان", "مشتریانی که شماره همراه ندارند — برای پیامک و تماس باید تکمیل شود", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("addre", "نشانی", T_TEXT)},
                    C_NONE, "", ""),
            s("cust_blacklist", "مشتریان لیست سیاه", "مشتریان", "مشتریان مسدود با مانده حساب", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("cell", "همراه", T_TEXT),
                            c("balance", "مانده", T_MONEY)},
                    C_NONE, "", ""),
            s("cust_no_location", "مشتریان بدون موقعیت روی نقشه", "مشتریان", "مشتریانی که مختصات مکانی آن‌ها ثبت نشده — برای مسیریابی ویزیتور لازم است", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("addre", "نشانی", T_TEXT)},
                    C_NONE, "", ""),
            s("cust_frequency", "تکرار خرید مشتریان", "مشتریان", "تعداد فاکتور، جمع و میانگین خرید هر مشتری با اولین و آخرین تاریخ خرید", true,
                    new Col[]{c("name", "مشتری", T_TEXT), c("docs", "فاکتور", T_NUM), c("total", "جمع فروش", T_MONEY),
                            c("avg", "میانگین فاکتور", T_MONEY), c("first", "اولین خرید", T_DATE), c("last", "آخرین خرید", T_DATE)},
                    C_BARS, "name", "total"),
            s("cust_new_monthly", "مشتریان جدید هر ماه", "مشتریان", "تعداد مشتریانی که در هر ماه برای اولین بار در این دوره خرید کرده‌اند", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("customers", "مشتری جدید", T_NUM)},
                    C_BARS, "month", "customers"),
            s("cust_avg_invoice", "مشتریان با بالاترین میانگین فاکتور", "مشتریان", "مشتریانی که حداقل دو فاکتور دارند، بر اساس میانگین مبلغ هر فاکتور", true,
                    new Col[]{c("name", "مشتری", T_TEXT), c("docs", "فاکتور", T_NUM), c("avg", "میانگین فاکتور", T_MONEY),
                            c("total", "جمع فروش", T_MONEY)},
                    C_NONE, "", ""),
            s("uniq_retention", "ماتریس بازگشت مشتری", "گزارش‌های اختصاصی", "درصد مشتریانی که این ماه خرید کرده‌اند و ماه قبل هم خرید داشته‌اند — ماه اول دوره بدون داده ماه قبل است", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("active", "مشتریان فعال", T_NUM), c("back", "بازگشتی", T_NUM),
                            c("rate", "نرخ بازگشت (٪)", T_NUM)},
                    C_LINE, "month", "rate"),
            s("uniq_basket", "کالاهایی که با هم خریده می‌شوند", "گزارش‌های اختصاصی", "جفت کالاهایی که در یک فاکتور فروش با هم آمده‌اند؛ درصد، سهم فاکتورهای دارای آن جفت است", true,
                    new Col[]{c("first", "کالای اول", T_TEXT), c("second", "کالای دوم", T_TEXT), c("together", "دفعات", T_NUM),
                            c("pct", "سهم فاکتورها (٪)", T_NUM)},
                    C_NONE, "", ""),
            s("uniq_credit_alert", "هشدار نزدیکی به سقف اعتبار", "گزارش‌های اختصاصی", "مشتریانی که مانده‌شان به ۸۰٪ سقف اعتبار یا بیشتر رسیده — قبل از قفل شدن خرید اقدام کنید", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("credit", "سقف اعتبار", T_MONEY),
                            c("balance", "مانده", T_MONEY), c("used", "مصرف اعتبار (٪)", T_NUM)},
                    C_NONE, "", ""),
            s("uniq_customer_record", "کارنامه مالی مشتریان", "گزارش‌های اختصاصی", "مانده، اعتبار، جمع فاکتور، سود و دریافت‌ها به تفکیک روش؛ مقادیر کل عمر مشتری از نمای سیستم", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("balance", "مانده", T_MONEY),
                            c("credit", "اعتبار", T_MONEY), c("invoices", "تعداد فاکتور", T_NUM), c("sales", "جمع فروش", T_MONEY),
                            c("gain", "سود", T_MONEY), c("cash", "دریافت نقد", T_MONEY), c("pos", "دریافت پوز", T_MONEY),
                            c("cheque", "دریافت چک", T_MONEY)},
                    C_BARS, "name", "balance"),
            s("uniq_declining", "افت خرید مشتریان وفادار", "گزارش‌های اختصاصی", "مشتریانی که ۹۰ روز اخیرشان کمتر از نصف ۹۰ روز قبل خرید داشته‌اند — زودتر از ریزش به سراغشان بروید", false,
                    new Col[]{c("name", "مشتری", T_TEXT), c("code", "کد", T_TEXT), c("prevTotal", "۹۰ روز قبل", T_MONEY),
                            c("curTotal", "۹۰ روز اخیر", T_MONEY), c("dropPct", "افت (٪)", T_NUM)},
                    C_NONE, "", ""),
            s("fin_discount_monthly", "تخفیف دریافت و پرداخت ماهانه", "دریافتی‌ها و پرداخت", "جمع دریافت و پرداخت و تخفیف‌های اعطاشده در هر ماه شمسی", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("receipts", "دریافت", T_MONEY), c("payments", "پرداخت", T_MONEY),
                            c("discountIn", "تخفیف دریافتی", T_MONEY), c("discountOut", "تخفیف پرداختی", T_MONEY)},
                    C_BARS, "month", "receipts"),
            s("fin_net_cash", "جریان خالص نقدی ماهانه", "دریافتی‌ها و پرداخت", "تفاوت دریافت و پرداخت هر ماه به‌همراه مانده تجمعی دوره", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("receipts", "دریافت", T_MONEY), c("payments", "پرداخت", T_MONEY),
                            c("net", "خالص ماه", T_MONEY), c("cum", "تجمعی", T_MONEY)},
                    C_LINE, "month", "cum"),
            s("fin_cash_mix", "ترکیب وصول: نقد در برابر چک", "دریافتی‌ها و پرداخت", "سهم وصول نقدی از کل وصول (نقد + چک) در هر ماه", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("cash", "نقد", T_MONEY), c("cheque", "چک", T_MONEY),
                            c("cashShare", "سهم نقد (٪)", T_NUM)},
                    C_BARS, "month", "cash"),
            // ---- عملکرد صندوق (زیرمجموعه خزانه‌داری) ----
            s("cash_daily", "عملکرد صندوق روزانه", "عملکرد صندوق", "مبالغ نقدی دریافتی و پرداختی هر روز و خالص صندوق", true,
                    new Col[]{c("day", "روز", T_DATE), c("receipts", "نقد دریافتی", T_MONEY), c("payments", "نقد پرداختی", T_MONEY),
                            c("net", "خالص صندوق", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_LINE, "day", "net"),
            s("cash_receipts_mix", "دریافتی‌ها: نقد، کارت‌خوان، حواله و چک", "عملکرد صندوق", "نقد، کارت‌خوان، حواله و چک‌های دریافتی هرکدام جداگانه (چک‌ها با سه مورد دیگر جمع نمی‌شوند)", true,
                    new Col[]{c("label", "نوع دریافتی", T_TEXT), c("amount", "مبلغ", T_MONEY), c("docs", "تعداد اسناد", T_NUM)},
                    C_DONUT, "label", "amount"),
            s("fin_overpaid", "اسناد دریافتی ناهماهنگ", "دریافتی‌ها و پرداخت", "قبض‌هایی که جمع نقد و چک آن‌ها بیشتر از مبلغ سند است — کنترل حسابرسی؛ پوز فقط به مبلغ اضافه می‌کند", true,
                    new Col[]{c("no", "شماره قبض", T_TEXT), c("kind", "نوع", T_TEXT), c("party", "طرف‌حساب", T_TEXT),
                            c("date", "تاریخ", T_DATE), c("total", "مبلغ سند", T_MONEY), c("paid", "نقد+چک", T_MONEY),
                            c("excess", "اضافه", T_MONEY)},
                    C_NONE, "", ""),
            s("sf_group_totals", "بدهکار و بستانکار گروه سرفصل", "سرفصل‌ها", "جمع بدهکار، بستانکار و مانده هر گروه سرفصل در دوره", true,
                    new Col[]{c("grp", "گروه سرفصل", T_TEXT), c("debit", "بدهکار", T_MONEY), c("credit", "بستانکار", T_MONEY),
                            c("balance", "مانده", T_MONEY), c("rows", "ردیف", T_NUM)},
                    C_BARS, "grp", "balance"),
            s("sf_monthly_group", "گردش ماهانه گروه‌های سرفصل", "سرفصل‌ها", "بدهکار، بستانکار و خالص هر گروه سرفصل در هر ماه شمسی", true,
                    new Col[]{c("month", "ماه", T_TEXT), c("grp", "گروه", T_TEXT), c("debit", "بدهکار", T_MONEY),
                            c("credit", "بستانکار", T_MONEY), c("net", "خالص", T_MONEY)},
                    C_NONE, "", ""),
            s("sf_hidden", "گردش‌های پنهان‌شده از گزارش", "سرفصل‌ها", "ردیف‌هایی که با «نمایش در گزارش = خیر» ثبت شده‌اند و در گزارش‌های عادی دیده نمی‌شوند", true,
                    new Col[]{c("zir", "زیرسرفصل", T_TEXT), c("rows", "ردیف", T_NUM), c("debit", "بدهکار", T_MONEY),
                            c("credit", "بستانکار", T_MONEY)},
                    C_NONE, "", ""),
            s("sf_dormant", "زیرسرفصل‌های فعال بدون گردش", "سرفصل‌ها", "زیرسرفصل‌های فعال که در این دوره هیچ ثبتی ندارند — برای پاکسازی سرفصل‌ها", true,
                    new Col[]{c("zir", "زیرسرفصل", T_TEXT), c("sarfasl", "سرفصل", T_TEXT), c("grp", "گروه", T_TEXT)},
                    C_NONE, "", ""),
            s("sf_unbalanced", "اسناد حسابداری نتراز", "سرفصل‌ها", "اسنادی که جمع بدهکار و بستانکارشان برابر نیست — باید قبل از بستن دوره اصلاح شوند", true,
                    new Col[]{c("sanad", "شماره سند", T_TEXT), c("date", "تاریخ", T_DATE), c("rows", "ردیف", T_NUM),
                            c("debit", "بدهکار", T_MONEY), c("credit", "بستانکار", T_MONEY), c("diff", "اختلاف", T_MONEY)},
                    C_NONE, "", ""),
            s("sf_biggest", "بزرگ‌ترین اسناد حسابداری", "سرفصل‌ها", "اسناد حسابداری با بیشترین بدهکار در دوره", true,
                    new Col[]{c("sanad", "شماره سند", T_TEXT), c("date", "تاریخ", T_DATE), c("rows", "ردیف", T_NUM),
                            c("debit", "بدهکار", T_MONEY), c("credit", "بستانکار", T_MONEY)},
                    C_NONE, "", ""),
            s("profit_by_product", "سود کالاها", "سود و زیان", "حاشیه سود هر کالا (مبنای قیمت خرید)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("sales", "فروش", T_MONEY),
                            c("cogs", "بهای تمام‌شده", T_MONEY), c("profit", "سود", T_MONEY)},
                    C_BARS, "label", "profit"),
            s("profit_costs", "سرفصل‌ها و هزینه‌ها", "هزینه‌ها",
                    "هزینه‌های جانبی، غیرمستقیم و کسری انبار به تفکیک سرفصل — سهم هر سرفصل در نمودار", true,
                    new Col[]{c("label", "سرفصل", T_TEXT), c("total", "جمع", T_MONEY), c("docs", "تعداد سند", T_NUM)},
                    C_DONUT, "label", "total"),
            s("sales_notconfirmed", "فاکتورهای فروش تأییدنشده / ابطالی", "برگشتی و ابطالی",
                    "فاکتورهای فروشی که تأیید نهایی نشده‌اند (Status غیر از ۱) — باید تعیین تکلیف شوند", true,
                    new Col[]{c("no", "شماره", T_TEXT), c("date", "تاریخ", T_DATE), c("customer", "مشتری", T_TEXT),
                            c("total", "مبلغ", T_MONEY), c("paid", "دریافتی", T_MONEY), c("remain", "مانده", T_MONEY)},
                    C_NONE, null, null),
            s("buy_notconfirmed", "فاکتورهای خرید تأییدنشده / ابطالی", "برگشتی و ابطالی",
                    "فاکتورهای خریدی که تأیید نهایی نشده‌اند (Status غیر از ۱) — باید تعیین تکلیف شوند", true,
                    new Col[]{c("no", "شماره", T_TEXT), c("date", "تاریخ", T_DATE), c("customer", "طرف‌حساب", T_TEXT),
                            c("total", "مبلغ", T_MONEY), c("paid", "پرداختی", T_MONEY), c("remain", "مانده", T_MONEY)},
                    C_NONE, null, null),
            s("returns_by_kind", "برگشتی‌ها بر اساس نوع", "برگشتی و ابطالی",
                    "اسناد برگشتی به تفکیک نوع سند — تعداد و جمع مبلغ هر نوع (خروجیِ انبار، برگشت از مشتری و …)", true,
                    new Col[]{c("label", "نوع برگشت", T_TEXT), c("docs", "تعداد سند", T_NUM),
                            c("total", "جمع مبلغ", T_MONEY)},
                    C_DONUT, "label", "total"),
            s("returns_list", "فهرست اسناد برگشتی", "برگشتی و ابطالی",
                    "ریز اسناد برگشتی با شماره، تاریخ، نوع، مشتری و توضیح", true,
                    new Col[]{c("no", "شماره", T_TEXT), c("date", "تاریخ", T_DATE),
                            c("kind", "نوع", T_TEXT), c("customer", "مشتری", T_TEXT),
                            c("total", "مبلغ", T_MONEY), c("descr", "توضیح", T_TEXT)},
                    C_NONE, null, null),
            // ---- بانک ----
            s("bank_turnover", "گردش بانک‌ها", "بانک و صندوق", "ورودی/خروجی هر حساب بانکی در بازه", true,
                    new Col[]{c("bank", "بانک", T_TEXT), c("in", "ورودی", T_MONEY), c("out", "خروجی", T_MONEY), c("count", "سند", T_NUM)},
                    C_BARS, "bank", "in"),
            s("bank_ops", "عملیات بانکی به تفکیک ماه", "بانک و صندوق", "ورودی، خروجی و خالص عملیات هر حساب بانکی در هر ماه — فقط عملیات بانکی", true,
                    new Col[]{c("bank", "بانک", T_TEXT), c("month", "ماه", T_TEXT), c("in", "ورودی", T_MONEY),
                            c("out", "خروجی", T_MONEY), c("net", "خالص", T_MONEY), c("count", "عملیات", T_NUM)},
                    C_BARS, "bank", "net"),
            s("cows", "صندوق‌ها", "بانک و صندوق", "فهرست صندوق‌ها و مانده", false,
                    new Col[]{c("name", "صندوق", T_TEXT), c("balance", "مانده", T_MONEY)},
                    C_NONE, "", ""),
            // ---- سرفصل‌ها (بازیابی؛ حسابداری غیرفعال است) ----
            s("sf_balances", "مانده سرفصل‌ها", "سرفصل‌ها",
                    "جمع بدهکار، بستانکار و مانده هر سرفصل در بازه — جست‌وجو با نام سرفصل", true,
                    SarfaslReports.SARFASL_COLS, C_NONE, "", ""),
            s("sf_zir", "مانده زیرسرفصل‌ها", "سرفصل‌ها",
                    "جمع بدهکار، بستانکار و مانده هر زیرسرفصل در بازه — جست‌وجو با نام", true,
                    SarfaslReports.BALANCE_COLS, C_NONE, "", ""),
            s("sf_entries", "ریز ثبت‌های سرفصل", "سرفصل‌ها",
                    "ثبت‌های سرفصل و زیرسرفصل با تاریخ، شرح، مبالغ و کاربر — جدیدترین اول", true,
                    new Col[]{c("date", "تاریخ", T_DATE), c("sarfasl", "سرفصل", T_TEXT), c("zir", "زیرسرفصل", T_TEXT),
                            c("descr", "شرح", T_TEXT), c("debit", "بدهکار", T_MONEY), c("credit", "بستانکار", T_MONEY),
                            c("user", "کاربر", T_TEXT)},
                    C_NONE, "", ""),
    };

    public static Spec byId(String id) {
        for (Spec s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    /** Build the query of a report. Filter is copied when the report needs tweaks. */
    public static Queries.Q query(Meta m, String id, Filter f) throws Queries.Missing {
        Filter ff = f.copy();
        ff.page = 0;
        if ("today_top".equals(id)) { ff.from = Jalali.todayStr(); ff.to = ff.from; ff.top = 20; return Queries.factorTopProducts(m, true, ff, 20); }
        if ("cheques_today".equals(id)) return MoneyQueries.chequeDue(m, true, 1);
        if ("dying_stock".equals(id)) return MasterQueries.stockForecast(m, ff);
        if ("fresh_debtors".equals(id)) { ff.status = "debt"; ff.sort = "debt"; ff.top = 200; return MasterQueries.customersList(m, ff); }
        if ("sales_daily".equals(id)) return Queries.factorDaily(m, true, ff.from, ff.to);
        if ("sales_monthly".equals(id)) return MasterQueries.monthly(m, true, 12);
        if ("yoy_sales".equals(id)) return MasterQueries.monthly(m, true, 24);
        if ("sales_price_spread".equals(id)) return AdvancedReports.salePriceSpread(m, ff, 50);
        if ("sales_by_visitor".equals(id)) return Queries.salesByVisitor(m, ff, 50);
        if ("sales_by_customer".equals(id)) return Queries.factorByCustomer(m, true, ff, 50);
        if ("sales_by_route".equals(id)) return Queries.factorByRoute(m, true, ff, 50);
        if ("sales_by_group".equals(id)) return Queries.factorByCustGroup(m, true, ff, 50);
        if ("sales_by_kala_group".equals(id)) return Queries.factorByProductGroup(m, true, ff, 50);
        if ("sales_top_products".equals(id)) return Queries.factorTopProducts(m, true, ff, 50);
        if ("unsettled".equals(id)) { ff.top = Math.max(ff.top, 100); return Queries.overdueInvoices(m, ff.top); }
        if ("invoice_unsettled".equals(id)) { ff.status = "unsettled"; return Queries.factorList(m, true, ff); }
        if ("cash_daily".equals(id)) return MoneyQueries.cashDaily(m, ff);
        if ("cash_receipts_mix".equals(id)) return MoneyQueries.paymentMix(m, ff);
        if ("bank_ops".equals(id)) return MasterQueries.bankOpsMonthly(m, ff);
        if ("aging".equals(id)) {
            if (m.function("dif_date_alan")) return MasterQueries.agingBuckets(m);
            return Queries.unsettledByMonth(m);
        }
        if ("debtors_aging".equals(id)) return MasterQueries.agingCustomers(m, 300);
        if ("visitor_aging".equals(id)) return MasterQueries.agingVisitors(m);
        if ("buy_daily".equals(id)) return Queries.factorDaily(m, false, ff.from, ff.to);
        if ("buy_monthly".equals(id)) return MasterQueries.monthly(m, false, 12);
        if ("buy_price_trend".equals(id)) return Queries.buyPriceTrend(m, ff, 50);
        if ("supplier_price_gap".equals(id)) return AdvancedReports.supplierPriceGap(m, ff, 50);
        if ("buy_by_party".equals(id)) return Queries.factorByCustomer(m, false, ff, 50);
        if ("buy_top_products".equals(id)) return Queries.factorTopProducts(m, false, ff, 50);
        if ("in_daily".equals(id)) return MoneyQueries.darDaily(m, 0, ff.from, ff.to);
        if ("out_daily".equals(id)) return MoneyQueries.darDaily(m, 1, ff.from, ff.to);
        if ("pos_by_bank".equals(id)) return MoneyQueries.posByBank(m, ff, -1);
        if ("cheque_due_in".equals(id)) return MoneyQueries.chequeDue(m, true, 7);
        if ("cheque_due_out".equals(id)) return MoneyQueries.chequeDue(m, false, 7);
        if ("cheque_bounced_in".equals(id)) { ff.top = 200; return MoneyQueries.chequeList(m, true, ff, "bargashti"); }
        if ("out_of_stock".equals(id)) { ff.status = "out"; ff.top = 300; ff.sort = "name"; return MasterQueries.productsList(m, ff); }
        if ("low_stock".equals(id)) { ff.status = "low"; ff.top = 300; ff.sort = "stock_asc"; return MasterQueries.productsList(m, ff); }
        if ("stock_value".equals(id)) return MasterQueries.stockValueByGroup(m);
        if ("debtors".equals(id)) { ff.status = "debt"; ff.sort = "debt"; ff.top = 200; return MasterQueries.customersList(m, ff); }
        if ("creditors".equals(id)) { ff.status = "credit"; ff.sort = "balance_asc"; ff.top = 200; return MasterQueries.customersList(m, ff); }
        if ("new_customers".equals(id)) return MasterQueries.newCustomers(m, ff);
        if ("inactive_customers".equals(id)) return Queries.inactiveCustomers(m, 200);
        if ("visitor_profit".equals(id)) return AdvancedReports.visitorProfit(m, ff, 50);
        if ("visitor_collection".equals(id)) return AdvancedReports.visitorCollection(m, ff, 50);
        if ("visitor_perf".equals(id)) return MasterQueries.visitorsPerf(m, ff);
        if ("users".equals(id)) return MasterQueries.usersList(m);
        if ("changes".equals(id)) return MasterQueries.tableChanges(m, ff);
        if ("profit_daily".equals(id)) return MasterQueries.profitDaily(m, ff);
        if ("profit_monthly".equals(id)) return MasterQueries.profitMonthly(m, ff);
        if ("profit_group".equals(id)) return ProfitReports.byGroup(m, ff, 50);
        if ("profit_group_share".equals(id)) return ProfitReports.groupShare(m, ff, 50);
        if ("profit_loss_products".equals(id)) return ProfitReports.lossProducts(m, ff, 100);
        if ("profit_margin_bands".equals(id)) return ProfitReports.marginBands(m, ff);
        if ("profit_discount_daily".equals(id)) return ProfitReports.discountDaily(m, ff);
        if ("profit_cumulative".equals(id)) return ProfitReports.cumulativeMonthly(m, ff);
        if ("profit_price_realization".equals(id)) return ProfitReports.priceRealization(m, ff, 100);
        if ("fin_discount_monthly".equals(id)) return FinanceReports.discountMonthly(m, ff);
        if ("fin_net_cash".equals(id)) return FinanceReports.netCashMonthly(m, ff);
        if ("fin_cash_mix".equals(id)) return FinanceReports.cashMixMonthly(m, ff);
        if ("fin_overpaid".equals(id)) return FinanceReports.overpaidVouchers(m, ff, 300);
        if ("sf_group_totals".equals(id)) return FinanceReports.groupTotals(m, ff);
        if ("sf_monthly_group".equals(id)) return FinanceReports.monthlyByGroup(m, ff);
        if ("sf_hidden".equals(id)) return FinanceReports.hiddenMovements(m, ff, 300);
        if ("sf_dormant".equals(id)) return FinanceReports.dormantSubAccounts(m, ff, 500);
        if ("sf_unbalanced".equals(id)) return FinanceReports.unbalancedJournals(m, ff, 300);
        if ("sf_biggest".equals(id)) return FinanceReports.biggestJournals(m, ff, 100);
        if ("uniq_retention".equals(id)) return UniqueReports.customerRetention(m, ff);
        if ("uniq_basket".equals(id)) return UniqueReports.marketBasket(m, ff, 50);
        if ("uniq_credit_alert".equals(id)) return UniqueReports.creditAlerts(m, 300);
        if ("uniq_declining".equals(id)) return UniqueReports.decliningCustomers(m, 200);
        if ("uniq_customer_record".equals(id)) return UniqueReports.customerRecord(m, 300);
        if ("buy_daily_totals".equals(id)) return BuyCustomerReports.buyDailyTotals(m, ff);
        if ("buy_monthly_totals".equals(id)) return BuyCustomerReports.buyMonthlyTotals(m, ff);
        if ("buy_top_invoices".equals(id)) return BuyCustomerReports.buyTopInvoices(m, ff, 30);
        if ("buy_supplier_share".equals(id)) return BuyCustomerReports.buySupplierShare(m, ff, 50);
        if ("buy_discount_by_supplier".equals(id)) return BuyCustomerReports.buyDiscountBySupplier(m, ff, 50);
        if ("buy_product_docs".equals(id)) return BuyCustomerReports.buyProductDocs(m, ff, 100);
        if ("buy_dormant_stock".equals(id)) return BuyCustomerReports.buyDormantStock(m, ff, 200);
        if ("buy_party_last".equals(id)) return BuyCustomerReports.buyPartyLast(m, ff, 100);
        if ("buy_stock_cover".equals(id)) return BuyCustomerReports.buyStockCover(m, ff, 100);
        if ("buy_vs_sales_monthly".equals(id)) return BuyCustomerReports.buyVsSalesMonthly(m, ff);
        if ("cust_by_kind".equals(id)) return BuyCustomerReports.custByKind(m, ff);
        if ("cust_by_personality".equals(id)) return BuyCustomerReports.custByPersonality(m, ff);
        if ("cust_no_cell".equals(id)) return BuyCustomerReports.custNoCell(m, 300);
        if ("cust_blacklist".equals(id)) return BuyCustomerReports.custBlacklist(m, 300);
        if ("cust_no_location".equals(id)) return BuyCustomerReports.custNoLocation(m, 300);
        if ("cust_frequency".equals(id)) return BuyCustomerReports.custFrequency(m, ff, 100);
        if ("cust_new_monthly".equals(id)) return BuyCustomerReports.custNewMonthly(m, ff);
        if ("cust_avg_invoice".equals(id)) return BuyCustomerReports.custAvgInvoice(m, ff, 100);
        if ("profit_by_product".equals(id)) return MasterQueries.profitByProduct(m, ff, 50);
        if ("profit_costs".equals(id)) return MasterQueries.profitCosts(m, ff);
        if ("sales_notconfirmed".equals(id)) { ff.status = "not_confirmed"; return Queries.factorList(m, true, ff); }
        if ("buy_notconfirmed".equals(id)) { ff.status = "not_confirmed"; return Queries.factorList(m, false, ff); }
        if ("returns_by_kind".equals(id)) return MasterQueries.returnsByKind(m, ff);
        if ("returns_list".equals(id)) return MasterQueries.returnsList(m, ff);
        if ("bank_turnover".equals(id)) return MasterQueries.bankTurnover(m, ff);
        if ("cows".equals(id)) return MasterQueries.cowList(m);
        if ("fleeing_customers".equals(id)) return MasterQueries.fleeingCustomers(m, ff);
        if ("lost_basket".equals(id)) return MasterQueries.lostBasket(m, ff);
        if ("cheque_reliability".equals(id)) return MasterQueries.chequeReliability(m, ff);
        if ("visitor_yield".equals(id)) return MasterQueries.visitorYield(m, ff);
        if ("visitor_scorecard".equals(id)) return VisitorScorecard.query(m, ff);
        if ("expiry_watch".equals(id)) return AtiranSpecialReports.expiryWatch(m, ff);
        if ("staff_attendance".equals(id)) return AtiranSpecialReports.attendanceAudit(m, ff);
        if ("golden_hours".equals(id)) return MasterQueries.goldenHours(m, ff);
        if ("sleeping_capital".equals(id)) return MasterQueries.deadStock(m, 90, 200);
        if ("stock_forecast".equals(id)) return MasterQueries.stockForecast(m, ff);
        if ("invoice_profit".equals(id)) return MasterQueries.invoiceProfit(m, ff);
        if ("order_suggest".equals(id)) return MasterQueries.orderSuggest(m, ff);
        if ("sf_balances".equals(id)) return SarfaslReports.sarfaslBalances(m, ff);
        if ("sf_zir".equals(id)) return SarfaslReports.zirBalances(m, ff);
        if ("sf_entries".equals(id)) return SarfaslReports.entries(m, ff);
        throw new Queries.Missing("گزارش ناشناخته است");
    }
}
