package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * In-app user roles.
 * v18: مدیر (full access), فروشنده (trade side), حسابدار (money side).
 * v26: + ویزیتور، موزع، انباردار، مودیان — the four Atiran field roles.
 * Each role has its own optional 4-digit PIN. Empty PIN = free entry;
 * the admin PIN defaults to 1234 until changed.
 * Gating is enforced centrally in MainActivity.nav().
 *
 * When an Atiran login session is active (AtiranAuth), the session role
 * ALWAYS applies — even if the legacy role picker is switched off.
 */
public final class RoleStore {
    private RoleStore() { }

    public static final String ADMIN = "admin";
    /** Sales manager: selling, customer/visitor performance and the curated sales report set. */
    public static final String SALES_MANAGER = "sales_manager";
    public static final String SELLER = "seller";
    public static final String ACCOUNTANT = "accountant";
    public static final String VISITOR = "visitor";
    public static final String DISTRIBUTOR = "distributor";
    public static final String WAREHOUSE = "warehouse";
    public static final String MOADIAN = "moadian";

    public static final String[] ALL = {
            ADMIN, SALES_MANAGER, SELLER, ACCOUNTANT, VISITOR, DISTRIBUTOR, WAREHOUSE, MOADIAN};

    /** Sales leadership: sales analytics and curated sales reports; no system, profit or ledger access. */
    private static final String[] SALES_MANAGER_OK = {
            "home", "sales", "customers", "products", "visitors", "analytics", "reports",
            "dues", "search", "voice", "more"};
    /** Seller side: everything except profit / reports / users / settings. */
    private static final String[] SELLER_OK = {
            "home", "sales", "buy", "customers", "products", "visitors",
            "cheques", "dues", "cash", "dar_in", "dar_out", "takmil", "search", "voice", "more"};
    /** Money side: everything except trade / profit / users / settings. */
    private static final String[] ACCOUNTANT_OK = {
            "home", "customers", "cheques", "dues", "cash", "dar_in", "dar_out",
            "reports", "takmil", "search", "voice", "more"};
    /** Visitor side: showcase + his trade world. */
    /** Visitor edition is intentionally scoped to its own Atiran identity, assigned customers and orders. */
    private static final String[] VISITOR_OK = {"visitor_work", "more"};
    /** Distributor side: delivery + collection on the road. */
    private static final String[] DISTRIBUTOR_OK = {
            "home", "sales", "customers", "products", "cheques", "dues", "cash",
            "search", "voice", "more"};
    /** Warehouse side: his own build (phase 2) + stock views. */
    private static final String[] WAREHOUSE_OK = {
            "anbar", "tahvil", "resid", "shomarsh", "score", "sync_queue", "pick_wave",
            "home", "products", "customers", "search", "voice", "more", "settings"};
    /** Moadian (tax) side: invoices + tax reports. */
    private static final String[] MOADIAN_OK = {
            "home", "sales", "customers", "reports", "search", "voice", "more"};

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_roles", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        try {
            return prefs(c).getBoolean("roles_on", false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setEnabled(Context c, boolean on) {
        try {
            prefs(c).edit().putBoolean("roles_on", on).apply();
        } catch (Exception ignored) { Diag.ignored("RoleStore:69", ignored); }
    }

    /** Current role id (always a valid role, defaults to admin). */
    public static String current(Context c) {
        try {
            String r = prefs(c).getString("role_current", ADMIN);
            if (isValid(r)) return r;
        } catch (Exception ignored) { Diag.ignored("RoleStore:77", ignored); }
        return ADMIN;
    }

    public static void setCurrent(Context c, String role) {
        try {
            if (!isValid(role)) role = ADMIN;
            prefs(c).edit().putString("role_current", role).apply();
        } catch (Exception ignored) { Diag.ignored("RoleStore:85", ignored); }
    }

    public static boolean isValid(String role) {
        for (String r : ALL) if (r.equals(role)) return true;
        return false;
    }

    public static String faName(String role) {
        if (ADMIN.equals(role)) return "مدیر مجموعه";
        if (SALES_MANAGER.equals(role)) return "مدیر فروش";
        if (SELLER.equals(role)) return "فروشنده";
        if (ACCOUNTANT.equals(role)) return "حسابدار";
        if (VISITOR.equals(role)) return "ویزیتور";
        if (DISTRIBUTOR.equals(role)) return "موزع";
        if (WAREHOUSE.equals(role)) return "انباردار";
        if (MOADIAN.equals(role)) return "مودیان";
        return "مدیر مجموعه";
    }

    /** Short Persian description of what the role sees. */
    public static String faDesc(String role) {
        if (SALES_MANAGER.equals(role)) return "داشبورد فروش، عملکرد ویزیتورها و گزارش‌های فروش";
        if (SELLER.equals(role)) return "فروش، مشتریان، کالاها و چک‌ها";
        if (ACCOUNTANT.equals(role)) return "چک‌ها، مطالبات و گزارش‌های مالی";
        if (VISITOR.equals(role)) return "مشتریان، کالاها و فروش";
        if (DISTRIBUTOR.equals(role)) return "تحویل، مشتریان و مطالبات";
        if (WAREHOUSE.equals(role)) return "کالاها و خرید / رسید انبار";
        if (MOADIAN.equals(role)) return "فاکتورها و گزارش مالیاتی";
        return "دسترسی کامل به همه بخش‌ها";
    }

    /**
     * Bottom tabs per role (v30): ONE app, each role lands in its own
     * edition. Closed phases still define tabs so opening a phase is only
     * a one-line change in AtiranAuth.phaseOpen.
     */
    public static String[] tabsFor(String role) {
        // The warehouse keeper gets the four highest-frequency destinations;
        // counts, supplier score and the rest remain one tap away in «همه بخش‌ها».
        if (WAREHOUSE.equals(role))
            return new String[]{"anbar", "tahvil", "resid", "more"};
        if (SALES_MANAGER.equals(role))
            return new String[]{"home", "sales", "customers", "visitors", "reports", "more"};
        if (SELLER.equals(role))
            return new String[]{"home", "sales", "customers", "products", "dues", "more"};
        if (ACCOUNTANT.equals(role))
            return new String[]{"home", "customers", "cheques", "dues", "reports", "more"};
        if (VISITOR.equals(role))
            return new String[]{"visitor_work", "more"};
        if (DISTRIBUTOR.equals(role))
            return new String[]{"sales", "customers", "cheques", "dues", "more"};
        if (MOADIAN.equals(role))
            return new String[]{"sales", "customers", "reports", "more"};
        // Manager's bar: home, product file, customers, a dedicated assistant tab, and the
        // command room («اتاق فرمان») which holds reports and every section.
        return new String[]{"home", "products", "customers", "assistant", "more"};
    }

    /** Short tab label (some screen titles are too long for a tab). */
    public static String tabLabel(String screenId) {
        if ("tahvil".equals(screenId)) return "تحویل";
        if ("resid".equals(screenId)) return "رسید";
        if ("shomarsh".equals(screenId)) return "شمارش";
        if ("score".equals(screenId)) return "امتیاز";
        if ("assistant".equals(screenId)) return "دستیار";
        if ("more".equals(screenId)) return "اتاق فرمان";
        return null; // fall back to the screen title
    }

    /** Edition name shown in Settings/about: «نسخه مدیر», «نسخه انباردار»… */
    public static String editionFa(String role) {
        return "نسخه " + faName(role);
    }

    /** Landing screen per role (fresh launch with an Atiran session). */
    public static String homeFor(String role) {
        if (VISITOR.equals(role)) return "visitor_work";
        if (DISTRIBUTOR.equals(role)) return "sales";
        if (WAREHOUSE.equals(role)) return "anbar";
        if (MOADIAN.equals(role)) return "sales";
        return "home";
    }

    /** True when the role has a PIN set (admin defaults to 1234). */
    public static boolean pinSet(Context c, String role) {
        try {
            if (ADMIN.equals(role) && prefs(c).getString("pin_admin", "").isEmpty()
                    && !prefs(c).getBoolean("pin_admin_touched", false)) return true;
            return !prefs(c).getString(pinKey(role), "").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Legacy single-iteration verifier namespace (fixed prefix), kept only for verification. */
    private static final String LEGACY_PIN_PREFIX = "meelano_role:";

    public static boolean checkRole(Context c, String role, String pin) {
        try {
            String saved = prefs(c).getString(pinKey(role), "");
            if (saved.isEmpty()) {
                // Untouched admin falls back to 1234; other empty PINs = free entry.
                if (ADMIN.equals(role) && !prefs(c).getBoolean("pin_admin_touched", false)) {
                    return "1234".equals(Money.en(pin == null ? "" : pin).trim());
                }
                return true;
            }
            if (pin == null) return false;
            String norm = Money.en(pin).trim();
            if (!PinHash.verify(norm, saved, LEGACY_PIN_PREFIX)) return false;
            if (PinHash.legacy(saved)) {
                String h = PinHash.hash(norm);
                if (!h.isEmpty()) prefs(c).edit().putString(pinKey(role), h).apply();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Set (or clear with null/short) a role PIN. */
    public static void setRolePin(Context c, String role, String pinOrNull) {
        try {
            String pin = pinOrNull == null ? "" : Money.en(pinOrNull).trim();
            SharedPreferences.Editor e = prefs(c).edit();
            if (ADMIN.equals(role)) e.putBoolean("pin_admin_touched", true);
            if (pin.length() < 4) e.putString(pinKey(role), "");
            else e.putString(pinKey(role), PinHash.hash(pin)); // "" on failure → no PIN stored (fail closed)
            e.apply();
        } catch (Exception ignored) { Diag.ignored("RoleStore:198", ignored); }
    }

    /**
     * Central gate: may the current role open this screen?
     * An active Atiran login session always wins over the legacy toggle.
     */
    public static boolean allowed(Context c, String screenId) {
        try {
            String sess = AtiranAuth.sessionRole(c);
            if (sess != null) return inList(sess, screenId);
            if (!enabled(c)) return true;
            return inList(current(c), screenId);
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean inList(String role, String screenId) {
        return allowedForRole(role,screenId);
    }

    /** Pure role policy, shared by navigation and unit tests. */
    public static boolean allowedForRole(String role,String screenId) {
        if (ADMIN.equals(role)) return true;
        String[] ok=listFor(role);
        if(ok==null)return true;
        for(String s:ok)if(s.equals(screenId))return true;
        return false;
    }

    private static String[] listFor(String role) {
        if (SALES_MANAGER.equals(role)) return SALES_MANAGER_OK;
        if (SELLER.equals(role)) return SELLER_OK;
        if (ACCOUNTANT.equals(role)) return ACCOUNTANT_OK;
        if (VISITOR.equals(role)) return VISITOR_OK;
        if (DISTRIBUTOR.equals(role)) return DISTRIBUTOR_OK;
        if (WAREHOUSE.equals(role)) return WAREHOUSE_OK;
        if (MOADIAN.equals(role)) return MOADIAN_OK;
        return null; // admin + unknown = full
    }

    private static String pinKey(String role) {
        if (SALES_MANAGER.equals(role)) return "pin_sales_manager";
        if (SELLER.equals(role)) return "pin_seller";
        if (ACCOUNTANT.equals(role)) return "pin_accountant";
        if (VISITOR.equals(role)) return "pin_visitor";
        if (DISTRIBUTOR.equals(role)) return "pin_distributor";
        if (WAREHOUSE.equals(role)) return "pin_warehouse";
        if (MOADIAN.equals(role)) return "pin_moadian";
        return "pin_admin";
    }
}
