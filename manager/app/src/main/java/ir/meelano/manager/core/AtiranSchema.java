package ir.meelano.manager.core;

/**
 * THE SINGLE SOURCE OF TRUTH for every data call in the app.
 *
 * Every screen, every report and every chart reads ONLY from the Atiran tables
 * and views listed here. Column names below were extracted from:
 *  1. the customer's schema documents (jadavel / jadavel2 / kelidha / options PDFs),
 *  2. Atiran's own procedures (AddInvoice, Daryaft, PosDetails …),
 *  3. the validated query set of the previous MEELANO app.
 *
 * At runtime {@link ir.meelano.manager.data.Meta} resolves each needed column
 * against live sys.columns metadata, so a renamed / missing column degrades to a
 * clear Persian message instead of a crash. Nothing is ever guessed silently:
 * when a table or column is absent the user is told exactly which one.
 */
public final class AtiranSchema {
    private AtiranSchema() { }

    // =====================================================================================
    // Base tables (name → Persian title). Row counts below are from the 1405/07/14 backup.
    // =====================================================================================
    public static final String[][] TABLES = {
            {"sailfact", "فاکتور فروش"},            // 774 — header: shfacfo/date/shmo/vis_rdf/all/tafif/tax/MabDaryaftFactor/tdf/tasvieh/Status
            {"subsailfact", "اقلام فاکتور فروش"},  // 3136 — lines: shfacfo/SHKA/TEDVAH/TEDJOZ/VAHPRICE/JOZPRICE/LINESUM
            {"buyfact", "فاکتور خرید"},             // 35 — header: shfackh/DATE/shmo/all/…
            {"subbuyfact", "اقلام فاکتور خرید"},   // 137
            {"dar", "قبوض دریافت و پرداخت"},       // 685 — PK(ghno,p,Rdf_): p=0 receipt, p=1 payment
            {"PosDetails", "جزئیات کارت/حواله"},   // 682 — ghno/p/MabPos/PosBankRdf/IsHavaleh/…
            {"DaryaftMultiFactor", "تسویه چند فاکتوره"}, // GhnoDar/Rdf_/Shfacfo/Price/IsTasvieh
            {"PardakhtMultiFactor", "تسویه چند فاکتوره پرداخت"},
            {"TemplateDaryaftCheque", "چک‌های داخل قبض دریافت"},
            {"TemplatePardakhtCheque", "چک‌های داخل قبض پرداخت"},
            {"getchk", "چک‌های دریافتی"},          // 76 — chk_satus (از سورس پروسیجرها): 1 صندوق، 2 نزد بانک، 3 وصول، 4 استرداد، 5 خرج‌شده؛ back='t' یعنی برگشتی
            {"putchk", "چک‌های پرداختی"},          // 202 — putchk_status (از سورس پروسیجرها): 0 سفید، 1 جاری، 2 پاس‌شده
            {"NGetchk", "چک دریافتی سنوات قبل"},   // 438
            {"NPutchk", "چک پرداختی سنوات قبل"},   // 1026
            {"CheckTypes", "انواع چک"},            // 2 — ID/Desciption
            {"GetCheckHistoryStatus", "تاریخچه وضعیت چک"}, // 25
            {"chkbatch", "دسته‌چک‌ها"},            // 15
            {"CUSTOMERS", "مشتریان / طرف‌حساب‌ها"}, // 2707 — SHMO/MONAME/man/cred/vis_rdf/RDF_masir/group_rdf/…
            {"cust_act", "گردش حساب مشتری"},       // 4985 — shmo/date/act_bes/act_bed/act_dis/act_id/ghno
            {"custgroup", "گروه مشتریان"},          // 11
            {"inventory", "کالاها"},               // 1795 — shka/naka/group_rdf/mojkavah/mojkajoz/buy_price/…
            {"inventory_anbars", "موجودی کالا در انبار"}, // 1795
            {"ka_act", "گردش کالا"},               // 4688 — shka/RdfAnbar/act_id/…
            {"kagroup", "گروه کالا"},               // 3
            {"ka_image", "تصویر کالا"},            // 1795
            {"cus_image", "تصویر مشتری"},          // 2707
            {"forosh_price", "قیمت‌های فروش"},     // 1795
            {"visitors", "ویزیتورها"},             // 10 — vis_rdf/vis_name/vis_man/active/…
            {"vis_goals", "اهداف ویزیتور"},        // 4 — baze_rdf/vis_rdf/mab/ted/…
            {"sys_vis", "دسترسی ویزیتور به سیستم"}, // 10
            {"masir", "مسیرها"},                   // 4
            {"regions", "منطقه‌ها"},               // 3
            {"CITYS", "شهرها"},                    // 2
            {"Province", "استان‌ها"},              // 2
            {"anbars", "انبارها"},                 // 1 — rdf_anbar/name/Base/…
            {"BANK", "حساب‌های بانکی"},            // 8 — RDF/…
            {"BANK_NAME", "نام بانک‌ها"},          // 26
            {"Sys_Bank", "بانک‌های سیستم"},        // 8
            {"ban_act", "گردش بانک"},              // 757 — bank_rdf/…
            {"COW", "صندوق‌ها"},                   // 55
            {"NCow", "صندوق سنوات قبل"},           // 20
            {"NBank", "بانک سنوات قبل"},           // 132
            {"havaleh", "حواله‌ها"},               // 195 — linked to dar(ghno,p,Rdf_)
            {"GhabzSanad", "قبوض سند"},            // 48 — RowID/…
            {"GhabzSanadKind", "انواع قبض سند"},   // 25
            {"sys_users", "کاربران"},              // 6 — user_id/user_name/role_id/active/…
            {"sys_use", "کاربران سیستم"},          // 6
            {"role", "نقش‌ها"},                    // 8
            {"Roles", "نقش‌های سیستمی"},           // 7
            {"LoginDetails", "ورودهای کاربران"},   // 360
            {"Log", "رویدادهای سیستم"},            // 1124
            {"ConfirmUser", "تأییدکنندگان"},       // 623
            {"ActNames", "نام عملیات گردش"},       // 120 — ActID/… (labels for cust_act/ka_act/ban_act)
            {"ActIDInfo", "شرح عملیات‌ها"},        // 75
            {"FactorConfirmation", "تأیید فاکتور"}, // Status/TaeedUser/…
            {"SaleFactTasvieh", "تسویه فاکتور فروش"}, // 2
            {"FactorType", "انواع فاکتور"},        // 4
            {"back_sanad", "اسناد برگشتی"},        // 58 — kind/shomare/…
            {"back_sanad_kind", "انواع سند برگشتی"}, // 9
            {"b_az_mosh_sanad", "برگشت از مشتری"}, // 39
            {"kasr_e_sanad", "اسناد کسری انبار"},  // 79
            {"tafif", "تخفیف‌ها"},                 // 14
            {"ExternalCosts", "هزینه‌های جانبی"},  // 30
            {"IndirectCost", "هزینه‌های غیرمستقیم"}, // 3
            {"Production", "تولید"},               // 31
            {"ProductionCost", "بهای تولید"},      // 28
            {"Formulation", "فرمولاسیون"},         // 43
            {"naghdinegi", "نقدینگی"},             // 20
            {"sal_mali", "سال مالی"},              // 1
            {"overal_setting", "تنظیمات آتیران"},  // 423
            {"Moein", "حساب‌های معین"},            // 26
            {"Kol", "حساب‌های کل"},                // 30
            {"Tafsil", "تفصیلی‌ها"},               // 2696
            {"Sys_Mandeh_Customer", "مانده مشتریان"}, // 2707
            {"ZirSarFaslDocuments", "اسناد زیرسرفصل"}, // 164
            {"zirsarfasls", "زیرسرفصل‌ها"},        // 35
            {"TableChanges", "تغییرات ثبت‌شده"},   // 1035
            {"DeviceLocation", "موقعیت دستگاه‌ها"}, // 94
            {"TerminalCompanyPos", "پوزهای شرکت"}, // 8
    };

    // =====================================================================================
    // Views (preferred ONLY when their columns are fully known — see below).
    // =====================================================================================
    public static final String[][] VIEWS = {
            {"VW_getchk", "نمای چک دریافتی (با نام مشتری و وضعیت فارسی)"},
            {"VW_Putchk", "نمای چک پرداختی (با نام طرف‌حساب و وضعیت فارسی)"},
            {"vw_customer", "نمای مشتری (با آخرین فاکتور/دریافت)"},
            {"ghabz_e_daryaft", "نمای قبض دریافت"},
            {"ghabz_e_pardakht", "نمای قبض پرداخت"},
            {"VW_InventoryAnbars", "نمای موجودی انبار"},
            {"VWForushKhales", "نمای فروش خالص"},
            {"VWKharidKhalese", "نمای خرید خالص"},
            {"VW_CustomersGain", "نمای سود مشتریان"},
            {"VW_GainDetails", "نمای جزئیات سود"},
    };

    /** Persian title of a table/view, or the name itself when unknown. */
    public static String faTitle(String table) {
        if (table == null) return "";
        for (String[] t : TABLES) if (t[0].equalsIgnoreCase(table)) return t[1];
        for (String[] v : VIEWS) if (v[0].equalsIgnoreCase(table)) return v[1];
        return table;
    }

    // =====================================================================================
    // Status code maps (validated against friendlyCheckStatus + Atiran behaviour).
    // =====================================================================================
    // =====================================================================================
    // Cheque status codes — VALIDATED against Atiran's own stored-procedure source
    // (extracted from the 1405/07/14 backup). The client labels below are ours; the
    // CODE meanings come from the procedures that SET them:
    //   getchk: sabt_chk_bargashti → 1+back='t' (برگشتی) • vosol_chk_bank/sandogh → 3 (وصول)
    //           esterdade_chk_daryafti → 4 (استرداد) • kharj flows → 5 (خرج‌شده)
    //           bank deposit sets our_bankrdf with 2 (نزد بانک) • 1 = موجود نزد ما
    //   putchk: issue → 1 (جاری) • clear/ban_act-75 → 2 (پاس) • void resets to 0 (سفید)
    // Codes 6/7/8 are excluded from EVERY Atiran view (void/deleted) — shown as «سایر».
    // =====================================================================================
    /** True when a getchk «back» flag means bounced (sabt_chk_bargashti sets back='t'). */
    public static boolean chequeInBounced(String back) {
        String b = back == null ? "" : back.trim().toUpperCase(java.util.Locale.US);
        return b.equals("T") || b.equals("1") || b.equals("TRUE") || b.equals("بله");
    }

    /** getchk (chk_satus + back flag) → bucket key used by ChequesScreen. */
    public static String chequeInBucket(String st, String back) {
        if (chequeInBounced(back)) return "bargashti";
        String s = st == null ? "" : st.trim();
        if (s.equals("1")) return "sandogh";
        if (s.equals("2")) return "bank";
        if (s.equals("3")) return "vosool";
        if (s.equals("4")) return "esterdad";
        if (s.equals("5")) return "kharj";
        return "sayer";
    }

    /** putchk (putchk_status) → bucket key used by ChequesScreen. */
    public static String chequeOutBucket(String st) {
        String s = st == null ? "" : st.trim();
        if (s.equals("0")) return "sefid";
        if (s.equals("1")) return "jari";
        if (s.equals("2")) return "pas";
        return "sayer";
    }

    /** getchk.chk_satus (+back flag) → Persian. Single source for every screen. */
    public static String chequeInStatusFa(String st, String back) {
        if (chequeInBounced(back)) return "برگشتی";
        String s = st == null ? "" : st.trim();
        if (s.equals("1")) return "موجود در صندوق";
        if (s.equals("2")) return "نزد بانک";
        if (s.equals("3")) return "وصول‌شده";
        if (s.equals("4")) return "استردادشده";
        if (s.equals("5")) return "خرج‌شده";
        if (s.equals("6")) return "باطل‌شده";
        if (s.equals("8")) return "حذف‌شده";
        String l = s.toLowerCase(java.util.Locale.US);
        if (l.contains("وصول") || l.contains("پاس")) return "وصول‌شده";
        if (l.contains("برگشت")) return "برگشتی";
        if (l.contains("استرد")) return "استردادشده";
        if (l.contains("خرج")) return "خرج‌شده";
        if (l.contains("بانک")) return "نزد بانک";
        if (l.contains("صندوق")) return "موجود در صندوق";
        return s.isEmpty() ? "نامشخص" : s;
    }

    /** getchk.chk_satus → Persian (no back flag available). */
    public static String chequeInStatusFa(String raw) {
        return chequeInStatusFa(raw, "");
    }

    /** putchk.putchk_status → Persian. Single source for every screen. */
    public static String chequeOutStatusFa(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.equals("0")) return "سفید / استفاده‌نشده";
        if (s.equals("1")) return "جاری / در جریان";
        if (s.equals("2")) return "پاس‌شده";
        if (s.equals("6") || s.equals("7")) return "باطل‌شده";
        if (s.equals("8")) return "حذف‌شده";
        return s.isEmpty() ? "نامشخص" : s;
    }

    /** sailfact.tasvieh ('t'/'f') → Persian. */
    public static String tasviehFa(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        if (s.equals("t") || s.equals("1") || s.equals("true") || s.equals("y")) return "تسویه‌شده";
        if (s.equals("f") || s.equals("0") || s.equals("false") || s.equals("n")) return "تسویه‌نشده";
        return raw == null || raw.trim().isEmpty() ? "نامشخص" : raw;
    }

    /** Generic Atiran boolean ('t'/'f', 1/0) → بله/خیر. */
    public static String boolFa(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.US);
        if (s.equals("t") || s.equals("1") || s.equals("true") || s.equals("y") || s.equals("بله")) return "بله";
        if (s.equals("f") || s.equals("0") || s.equals("false") || s.equals("n") || s.equals("خیر")) return "خیر";
        return raw == null ? "—" : raw;
    }

    /**
     * Fallback labels for cust_act / ka_act / ban_act act_id when ActNames
     * cannot be resolved. Proven ids: 0 opening, 1 cash/bank receipt, 3 cheque
     * receipt, 8 bank move, 20 sales invoice.
     */
    public static String actNameFallback(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.equals("0")) return "سند افتتاحیه / حساب قبلی";
        if (s.equals("1")) return "دریافت نقدی / بانکی";
        if (s.equals("3")) return "دریافت چک";
        if (s.equals("8")) return "گردش بانکی";
        if (s.equals("20")) return "فاکتور فروش";
        return s.isEmpty() ? "عملیات" : ("عملیات " + s);
    }

    // =====================================================================================
    // Business thresholds.
    // =====================================================================================
    /** Stock at/below this (in «vah» units) counts as «کم‌موجودی». */
    public static final double LOW_STOCK_VAH = 5;

    /** A receipt/invoice row counts as settled when |remain| ≤ this (rials). */
    public static final double SETTLE_TOLERANCE = 1;
}
