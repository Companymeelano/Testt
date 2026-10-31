package ir.meelano.manager.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The section index of «اتاق فرمان»: every screen the app can open, grouped the way the
 * shop works. It lives in the core package (no Android types) so the unit tests can prove
 * that no registered screen is missing from the index - a screen that is not listed here
 * would be unreachable for the manager.
 */
public final class CommandRoom {
    private CommandRoom() { }

    /** One entry of the index: a screen of the app, or a page of the visitor workspace. */
    public static final class Entry {
        /** Screen id, or the page key of the visitor workspace. */
        public final String id;
        /** Label used when the screen itself cannot be asked for its title. */
        public final String title;
        public final String description;
        /**
         * Name of the drawable that carries the section's own 3D artwork (for example
         * "mi_group"). The theme turns it into the matching 3D set of the chosen skin.
         */
        public final String art;
        /** True when the entry opens a page inside the visitor workspace. */
        public final boolean visitor;

        public Entry(String id, String title, String description, boolean visitor) {
            this(id, title, description, visitor, "");
        }

        public Entry(String id, String title, String description, boolean visitor, String art) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.visitor = visitor;
            this.art = art == null ? "" : art;
        }
    }

    /** A group of sections with its own heading and accent. */
    public static final class Group {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String glyph;
        public final String accent;
        public final Entry[] entries;

        public Group(String id, String title, String subtitle, String glyph, String accent, Entry[] entries) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.glyph = glyph;
            this.accent = accent;
            this.entries = entries == null ? new Entry[0] : entries;
        }
    }

    private static Entry e(String id, String description) {
        return new Entry(id, "", description, false);
    }

    private static Entry e(String id, String description, String art) {
        return new Entry(id, "", description, false, art);
    }

    private static Entry v(String id, String title, String description, String art) {
        return new Entry(id, title, description, true, art);
    }

    /** One source of truth for the manager and role-specific section index. */
    private static final Group[] GROUPS = {
            new Group("operations", "مدیریت روزانه و فروش", "فاکتورها، مشتریان و پیگیری روزانه", "♛", "gold",
                    new Entry[]{
                            e("home", "شاخص‌ها و هشدارهای امروز", "mi_home"),
                            e("closeout", "تطبیق روزانهٔ فروش، دریافت، پرداخت، چک، برگشتی و تحویل باز", "mi_task_alt"),
                            e("manager_alerts", "آستانه‌های هشدار قابل تنظیم با دسترسی مستقیم به اقدام", "mi_warning"),
                            e("production", "فرمول، تولید مدیرمحور و بهای تمام‌شده در انبار آتیران", "mi_warehouse"),
                            e("sales", "فاکتورهای فروش و پیگیری‌ها", "mi_point_of_sale"),
                            e("buy", "فاکتورهای خرید و طرف‌حساب‌ها", "mi_shopping_cart"),
                            e("customers", "پرونده و گردش حساب مشتریان", "mi_group"),
                            e("visitors", "عملکرد و وصول ویزیتورها", "mi_person"),
                            e("visitor_work", "مسیر و بازدید، پیش‌فاکتور، گفتگوی ثبت کالا و دریافت وجه", "mi_directions_car"),
                            e("visitor_orders", "بازبینی و تأیید پیش‌فاکتورهای ارسالی ویزیتورها", "mi_receipt_long"),
                    }),
            new Group("finance", "مالی و خزانه", "دریافت، پرداخت و کنترل نقدینگی", "💳", "success",
                    new Entry[]{
                            e("dar_in", "قبوض دریافت و روش‌های تسویه", "mi_trending_up"),
                            e("dar_out", "قبوض پرداخت و گردش کارت", "mi_payments"),
                            e("cheques", "چک‌ها و وضعیت وصول", "mi_cheque"),
                            e("dues", "سررسید چک‌ها و فاکتورها", "mi_calendar_month"),
                            e("cash", "پیش‌بینی نقدینگی و خالص روزانه", "mi_account_balance_wallet"),
                    }),
            new Group("stock", "کالا و عملیات انبار", "موجودی، پروندهٔ کالا، رسید، تحویل و شمارش", "📦", "info",
                    new Entry[]{
                            e("products", "موجودی، کم‌موجودی و گردش کالا", "mi_grid_view"),
                            e("product_file", "پروندهٔ کالا: مشخصات، بارکد، گروه و مستندات", "mi_sell"),
                            e("anbar", "میزکار و هشدارهای انبار", "mi_inventory_2"),
                            e("tahvil", "تحویل فاکتور و ثبت گیرنده", "mi_local_shipping"),
                            e("pick_wave", "برداشت چند فاکتور، جمع کالا و اسکن بارگیری", "mi_add_shopping_cart"),
                            e("sync_queue", "تحویل و شمارش‌های منتظر همگام‌سازی", "mi_refresh"),
                            e("resid", "رسید کالا و پیش‌نویس‌های انبار", "mi_widgets"),
                            e("takmil", "تکمیل پیش‌فاکتور", "mi_request_quote"),
                            e("shomarsh", "شمارش انبار و مغایرت‌گیری", "mi_zoom"),
                            e("score", "ارزیابی تأمین‌کنندگان", "mi_military_tech"),
                    }),
            new Group("insights", "تحلیل و گزارش", "گزارش‌های مدیریتی و بینش کسب‌وکار", "📊", "teal",
                    new Entry[]{
                            e("reports", "مرکز گزارش‌های مدیریتی", "mi_bar_chart"),
                            e("analytics", "داشبورد تحلیلی: روند فروش، سود و عملکرد", "mi_trending_up"),
                            e("profit", "سود، هزینه و حاشیه سود", "mi_profit"),
                    }),
            new Group("smart", "دستیار و ابزارهای هوشمند", "پرسش، فرمان صوتی و کمک به کار روزانه", "✦", "violet",
                    new Entry[]{
                            e("assistant", "دستیار میلانو: پرسش از داده‌ها و اجرای کارهای پرتکرار", "mi_star_shine"),
                            e("voice", "گزارش صوتی فارسی", "mi_mic"),
                    }),
            new Group("system", "مدیریت سیستم و ابزارها", "کاربران، تنظیمات و ابزارهای کمکی", "⚙", "steel",
                    new Entry[]{
                            e("users", "کاربران و فعالیت‌های سیستم", "mi_admin_panel_settings"),
                            e("settings", "اتصال، ظاهر، اعلان، پشتیبان‌گیری و بروزرسانی", "mi_settings"),
                            e("search", "جست‌وجو در بخش‌های برنامه", "mi_search"),
                            e("tv", "نمایش تمام‌صفحه برای فروشگاه", ""),
                    }),
    };

    /** The pages of the visitor workspace the command room can open. */
    private static final String[] VISITOR_PAGES = {
            "customers", "products", "route", "order", "my_orders", "receipt", "reports"};

    /** The visitor's own workspace: the pages of «فروش میدانی», one tap from the command room. */
    private static final Group[] VISITOR_GROUPS = {
            new Group("visitor_sales", "فروش میدانی", "کارهای روزانهٔ حساب ویزیتور", "🧾", "gold",
                    new Entry[]{
                            v("customers", "مشتریان من", "مشتریان تخصیص‌یافته به مسیر همین حساب آتیران", "mi_group"),
                            v("products", "کالا و نرخ‌ها", "فهرست فروش میدانی با نرخ یکتا و موجودی آتیران", "mi_grid_view"),
                            v("route", "ثبت ویزیت", "ثبت نتیجهٔ بازدید و یادداشت مسیر", "mi_person"),
                            v("order", "پیش‌فاکتور هوشمند", "ثبت کالا با صدا یا متن و ارسال برای تأیید", "mi_add_shopping_cart"),
                            v("my_orders", "سفارش‌های من", "پیگیری وضعیت پیش‌فاکتورهای همین حساب", "mi_point_of_sale"),
                            v("receipt", "دریافت وجه", "ثبت پیش‌دریافت مشتری و صدور رسید", "mi_receipt_long"),
                            v("reports", "گزارش روزانه", "بازدیدها و عملکرد خودتان", "mi_bar_chart"),
                    }),
    };

    /** The manager and staff index. */
    public static Group[] groups() {
        return GROUPS.clone();
    }

    /** The index of the field-sales workspace. */
    public static Group[] visitorGroups() {
        return VISITOR_GROUPS.clone();
    }

    /** Every screen id the index lists (the command room itself is not listed). */
    public static List<String> sectionIds() {
        List<String> ids = new ArrayList<>();
        for (Group g : GROUPS) for (Entry e : g.entries) ids.add(e.id);
        return ids;
    }

    /** Every page of the visitor workspace the index lists. */
    public static List<String> visitorSectionIds() {
        List<String> ids = new ArrayList<>();
        for (Group g : VISITOR_GROUPS) for (Entry e : g.entries) ids.add(e.id);
        return ids;
    }

    /** The entry of a screen id, or null. */
    public static Entry find(String id) {
        if (id == null) return null;
        for (Group g : GROUPS) for (Entry e : g.entries) if (id.equals(e.id)) return e;
        return null;
    }

    /** The visitor page of an id, or null. */
    public static Entry findVisitor(String id) {
        if (id == null) return null;
        for (Group g : VISITOR_GROUPS) for (Entry e : g.entries) if (id.equals(e.id)) return e;
        return null;
    }

    /** True when the key is one of the pages of the visitor workspace. */
    public static boolean isVisitorPage(String id) {
        if (id == null) return false;
        for (String page : VISITOR_PAGES) if (page.equals(id)) return true;
        return false;
    }

    /** Ids that appear twice in the index - a duplicate would show a section twice. */
    public static List<String> duplicates() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> dup = new ArrayList<>();
        for (String id : sectionIds()) if (!seen.add(id)) dup.add(id);
        return dup;
    }
}
