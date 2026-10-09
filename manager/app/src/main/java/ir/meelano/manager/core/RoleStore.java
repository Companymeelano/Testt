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
    public static final String SELLER = "seller";
    public static final String ACCOUNTANT = "accountant";
    public static final String VISITOR = "visitor";
    public static final String DISTRIBUTOR = "distributor";
    public static final String WAREHOUSE = "warehouse";
    public static final String MOADIAN = "moadian";

    public static final String[] ALL = {
            ADMIN, SELLER, ACCOUNTANT, VISITOR, DISTRIBUTOR, WAREHOUSE, MOADIAN};

    /** Seller side: everything except profit / reports / users / settings. */
    private static final String[] SELLER_OK = {
            "home", "sales", "buy", "customers", "products", "visitors",
            "cheques", "dues", "cash", "dar_in", "dar_out", "search", "voice", "more"};
    /** Money side: everything except trade / profit / users / settings. */
    private static final String[] ACCOUNTANT_OK = {
            "home", "customers", "cheques", "dues", "cash", "dar_in", "dar_out",
            "reports", "search", "voice", "more"};
    /** Visitor side: showcase + his trade world. */
    private static final String[] VISITOR_OK = {
            "home", "customers", "products", "sales", "dues", "search", "voice", "more"};
    /** Distributor side: delivery + collection on the road. */
    private static final String[] DISTRIBUTOR_OK = {
            "home", "sales", "customers", "products", "cheques", "dues", "cash",
            "search", "voice", "more"};
    /** Warehouse side: his own build (phase 2) + stock views. */
    private static final String[] WAREHOUSE_OK = {
            "anbar", "tahvil", "resid", "home", "products", "customers",
            "search", "voice", "more"};
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
        } catch (Exception ignored) { }
    }

    /** Current role id (always a valid role, defaults to admin). */
    public static String current(Context c) {
        try {
            String r = prefs(c).getString("role_current", ADMIN);
            if (isValid(r)) return r;
        } catch (Exception ignored) { }
        return ADMIN;
    }

    public static void setCurrent(Context c, String role) {
        try {
            if (!isValid(role)) role = ADMIN;
            prefs(c).edit().putString("role_current", role).apply();
        } catch (Exception ignored) { }
    }

    public static boolean isValid(String role) {
        for (String r : ALL) if (r.equals(role)) return true;
        return false;
    }

    public static String faName(String role) {
        if (SELLER.equals(role)) return "فروشنده";
        if (ACCOUNTANT.equals(role)) return "حسابدار";
        if (VISITOR.equals(role)) return "ویزیتور";
        if (DISTRIBUTOR.equals(role)) return "موزع";
        if (WAREHOUSE.equals(role)) return "انباردار";
        if (MOADIAN.equals(role)) return "مودیان";
        return "مدیر";
    }

    /** Short Persian description of what the role sees. */
    public static String faDesc(String role) {
        if (SELLER.equals(role)) return "فروش، مشتریان، کالاها و چک‌ها";
        if (ACCOUNTANT.equals(role)) return "چک‌ها، مطالبات و گزارش‌های مالی";
        if (VISITOR.equals(role)) return "مشتریان، کالاها و فروش";
        if (DISTRIBUTOR.equals(role)) return "تحویل، مشتریان و مطالبات";
        if (WAREHOUSE.equals(role)) return "کالاها و خرید / رسید انبار";
        if (MOADIAN.equals(role)) return "فاکتورها و گزارش مالیاتی";
        return "دسترسی کامل به همه بخش‌ها";
    }

    /** Landing screen per role (fresh launch with an Atiran session). */
    public static String homeFor(String role) {
        if (VISITOR.equals(role)) return "customers";
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
            return pin != null && sha(Money.en(pin).trim()).equals(saved);
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
            else e.putString(pinKey(role), sha(pin));
            e.apply();
        } catch (Exception ignored) { }
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
        if (ADMIN.equals(role)) return true;
        String[] ok = listFor(role);
        if (ok == null) return true;
        for (String s : ok) if (s.equals(screenId)) return true;
        return false;
    }

    private static String[] listFor(String role) {
        if (SELLER.equals(role)) return SELLER_OK;
        if (ACCOUNTANT.equals(role)) return ACCOUNTANT_OK;
        if (VISITOR.equals(role)) return VISITOR_OK;
        if (DISTRIBUTOR.equals(role)) return DISTRIBUTOR_OK;
        if (WAREHOUSE.equals(role)) return WAREHOUSE_OK;
        if (MOADIAN.equals(role)) return MOADIAN_OK;
        return null; // admin + unknown = full
    }

    private static String pinKey(String role) {
        if (SELLER.equals(role)) return "pin_seller";
        if (ACCOUNTANT.equals(role)) return "pin_accountant";
        if (VISITOR.equals(role)) return "pin_visitor";
        if (DISTRIBUTOR.equals(role)) return "pin_distributor";
        if (WAREHOUSE.equals(role)) return "pin_warehouse";
        if (MOADIAN.equals(role)) return "pin_moadian";
        return "pin_admin";
    }

    private static String sha(String s) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(("meelano_role:" + s).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "x" + s;
        }
    }
}
