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
            s("visitor_yield", "بازده ویزیتور", "هوشمند", "مشتریان منتسب، فعال بازه و ریزش ۹۰روزه هر ویزیتور + فاکتور به‌ازای مشتری", true,
                    new Col[]{c("name", "ویزیتور", T_TEXT), c("assigned", "منتسب", T_NUM), c("active", "فعال بازه", T_NUM),
                            c("churned", "ریزش", T_NUM), c("invoices", "فاکتور", T_NUM), c("sales", "فروش", T_MONEY),
                            c("perCust", "فاکتور/مشتری", T_NUM)},
                    C_BARS, "name", "sales"),
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
            s("sales_by_visitor", "فروش ویزیتورها", "فروش", "سهم هر ویزیتور از فروش بازه", true,
                    new Col[]{c("label", "ویزیتور", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_by_route", "فروش مسیرها", "فروش", "سهم هر مسیر از فروش بازه", true,
                    new Col[]{c("label", "مسیر", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            s("sales_by_group", "فروش گروه‌های مشتری", "فروش", "سهم هر گروه مشتری از فروش", true,
                    new Col[]{c("label", "گروه", T_TEXT), c("total", "جمع فروش", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_DONUT, "label", "total"),
            s("unsettled", "فاکتورهای معوق", "فروش", "فاکتورهای تسویه‌نشده با سررسید گذشته", false,
                    new Col[]{c("invoice", "فاکتور", T_TEXT), c("party", "مشتری", T_TEXT), c("amount", "مبلغ", T_MONEY),
                            c("dueDate", "سررسید", T_DATE), c("days", "روز تأخیر", T_NUM), c("visitor", "ویزیتور", T_TEXT)},
                    C_NONE, "", ""),
            s("aging", "سابقه مطالبات", "فروش", "بازه‌بندی مانده فاکتورهای تسویه‌نشده", false,
                    new Col[]{c("bucket", "بازه", T_TEXT), c("amount", "مبلغ", T_MONEY), c("count", "تعداد", T_NUM)},
                    C_BARS, "bucket", "amount"),
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
            s("buy_by_party", "خرید از طرف‌حساب‌ها", "خرید", "برترین فروشندگان/تأمین‌کنندگان", true,
                    new Col[]{c("label", "طرف‌حساب", T_TEXT), c("total", "جمع خرید", T_MONEY), c("docs", "اسناد", T_NUM)},
                    C_BARS, "label", "total"),
            // ---- دریافت و پرداخت ----
            s("in_daily", "دریافت روزانه", "دریافت و پرداخت", "جمع قبوض دریافت هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع دریافت", T_MONEY), c("count", "قبوض", T_NUM)},
                    C_LINE, "day", "total"),
            s("out_daily", "پرداخت روزانه", "دریافت و پرداخت", "جمع قبوض پرداخت هر روز", true,
                    new Col[]{c("day", "روز", T_DATE), c("total", "جمع پرداخت", T_MONEY), c("count", "قبوض", T_NUM)},
                    C_LINE, "day", "total"),
            s("pos_by_bank", "گردش پوز بانک‌ها", "دریافت و پرداخت", "تفکیک مبالغ کارت‌خوان به بانک", true,
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
            s("visitor_perf", "عملکرد ویزیتورها", "ویزیتورها", "فروش، وصول، مشتریان و اهداف هر ویزیتور", true,
                    new Col[]{c("name", "ویزیتور", T_TEXT), c("sales", "فروش", T_MONEY), c("collected", "وصول فاکتور", T_MONEY),
                            c("invoices", "فاکتور", T_NUM), c("customers", "مشتری فعال", T_NUM), c("goals", "هدف", T_MONEY)},
                    C_BARS, "name", "sales"),
            // ---- کاربران ----
            s("users", "کاربران و آخرین ورود", "کاربران و نظارت", "کاربران سیستم، نقش و آخرین ورود", false,
                    new Col[]{c("name", "کاربر", T_TEXT), c("role", "نقش", T_TEXT), c("active", "فعال", T_TEXT),
                            c("lastLogin", "آخرین ورود", T_TEXT), c("todayN", "امروز", T_NUM)},
                    C_NONE, "", ""),
            s("changes", "تغییرات ثبت‌شده", "کاربران و نظارت", "آخرین تغییرات ثبت‌شده در سیستم", true,
                    new Col[]{c("at", "زمان", T_TEXT), c("tableName", "جدول", T_TEXT), c("username", "کاربر", T_TEXT), c("descrip", "شرح", T_TEXT)},
                    C_NONE, "", ""),
            // ---- سود ----
            s("profit_daily", "سود روزانه", "سود و زیان", "فروش و بهای تمام‌شده هر روز (مبنای قیمت خرید)", true,
                    new Col[]{c("day", "روز", T_DATE), c("sales", "فروش", T_MONEY), c("cogs", "بهای تمام‌شده", T_MONEY),
                            c("profit", "سود", T_MONEY)},
                    C_LINE, "day", "profit"),
            s("profit_by_product", "سود کالاها", "سود و زیان", "حاشیه سود هر کالا (مبنای قیمت خرید)", true,
                    new Col[]{c("label", "کالا", T_TEXT), c("qty", "مقدار", T_NUM), c("sales", "فروش", T_MONEY),
                            c("cogs", "بهای تمام‌شده", T_MONEY), c("profit", "سود", T_MONEY)},
                    C_BARS, "label", "profit"),
            s("profit_costs", "سایر هزینه‌ها", "سود و زیان", "هزینه‌های جانبی، غیرمستقیم و کسری انبار", true,
                    new Col[]{c("label", "سرفصل", T_TEXT), c("total", "جمع", T_MONEY)},
                    C_DONUT, "label", "total"),
            // ---- بانک ----
            s("bank_turnover", "گردش بانک‌ها", "بانک و صندوق", "ورودی/خروجی هر حساب بانکی در بازه", true,
                    new Col[]{c("bank", "بانک", T_TEXT), c("in", "ورودی", T_MONEY), c("out", "خروجی", T_MONEY), c("count", "سند", T_NUM)},
                    C_BARS, "bank", "in"),
            s("cows", "صندوق‌ها", "بانک و صندوق", "فهرست صندوق‌ها و مانده", false,
                    new Col[]{c("name", "صندوق", T_TEXT), c("balance", "مانده", T_MONEY)},
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
        if ("sales_by_visitor".equals(id)) return Queries.salesByVisitor(m, ff, 50);
        if ("sales_by_customer".equals(id)) return Queries.factorByCustomer(m, true, ff, 50);
        if ("sales_by_route".equals(id)) return Queries.factorByRoute(m, true, ff, 50);
        if ("sales_by_group".equals(id)) return Queries.factorByCustGroup(m, true, ff, 50);
        if ("sales_top_products".equals(id)) return Queries.factorTopProducts(m, true, ff, 50);
        if ("unsettled".equals(id)) { ff.top = Math.max(ff.top, 100); return Queries.overdueInvoices(m, ff.top); }
        if ("aging".equals(id)) {
            if (m.function("dif_date_alan")) return Queries.agingBuckets(m);
            return Queries.unsettledByMonth(m);
        }
        if ("buy_daily".equals(id)) return Queries.factorDaily(m, false, ff.from, ff.to);
        if ("buy_monthly".equals(id)) return MasterQueries.monthly(m, false, 12);
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
        if ("visitor_perf".equals(id)) return MasterQueries.visitorsPerf(m, ff);
        if ("users".equals(id)) return MasterQueries.usersList(m);
        if ("changes".equals(id)) return MasterQueries.tableChanges(m, ff);
        if ("profit_daily".equals(id)) return MasterQueries.profitDaily(m, ff);
        if ("profit_by_product".equals(id)) return MasterQueries.profitByProduct(m, ff, 50);
        if ("profit_costs".equals(id)) return MasterQueries.profitCosts(m, ff);
        if ("bank_turnover".equals(id)) return MasterQueries.bankTurnover(m, ff);
        if ("cows".equals(id)) return MasterQueries.cowList(m);
        if ("fleeing_customers".equals(id)) return MasterQueries.fleeingCustomers(m, ff);
        if ("lost_basket".equals(id)) return MasterQueries.lostBasket(m, ff);
        if ("cheque_reliability".equals(id)) return MasterQueries.chequeReliability(m, ff);
        if ("visitor_yield".equals(id)) return MasterQueries.visitorYield(m, ff);
        if ("golden_hours".equals(id)) return MasterQueries.goldenHours(m, ff);
        if ("sleeping_capital".equals(id)) return MasterQueries.deadStock(m, 90, 200);
        if ("stock_forecast".equals(id)) return MasterQueries.stockForecast(m, ff);
        if ("invoice_profit".equals(id)) return MasterQueries.invoiceProfit(m, ff);
        if ("order_suggest".equals(id)) return MasterQueries.orderSuggest(m, ff);
        throw new Queries.Missing("گزارش ناشناخته است");
    }
}
