package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * In-app user roles (v18): مدیر (full access), فروشنده (trade side),
 * حسابدار (money side). Each role has its own optional 4-digit PIN.
 * Empty PIN = free entry; the admin PIN defaults to 1234 until changed.
 * Gating is enforced centrally in MainActivity.nav().
 */
public final class RoleStore {
    private RoleStore() { }

    public static final String ADMIN = "admin";
    public static final String SELLER = "seller";
    public static final String ACCOUNTANT = "accountant";

    /** Seller side: everything except profit / reports / users / settings. */
    private static final String[] SELLER_OK = {
            "home", "sales", "buy", "customers", "products", "visitors",
            "cheques", "dues", "cash", "dar_in", "dar_out", "search", "more"};
    /** Money side: everything except trade / profit / users / settings. */
    private static final String[] ACCOUNTANT_OK = {
            "home", "customers", "cheques", "dues", "cash", "dar_in", "dar_out",
            "reports", "search", "more"};

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
            if (SELLER.equals(r) || ACCOUNTANT.equals(r)) return r;
        } catch (Exception ignored) { }
        return ADMIN;
    }

    public static void setCurrent(Context c, String role) {
        try {
            if (!SELLER.equals(role) && !ACCOUNTANT.equals(role)) role = ADMIN;
            prefs(c).edit().putString("role_current", role).apply();
        } catch (Exception ignored) { }
    }

    public static String faName(String role) {
        if (SELLER.equals(role)) return "فروشنده";
        if (ACCOUNTANT.equals(role)) return "حسابدار";
        return "مدیر";
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

    /** Central gate: may the current role open this screen? */
    public static boolean allowed(Context c, String screenId) {
        try {
            if (!enabled(c)) return true;
            String r = current(c);
            if (ADMIN.equals(r)) return true;
            String[] ok = SELLER.equals(r) ? SELLER_OK : ACCOUNTANT_OK;
            for (String s : ok) if (s.equals(screenId)) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static String pinKey(String role) {
        if (SELLER.equals(role)) return "pin_seller";
        if (ACCOUNTANT.equals(role)) return "pin_accountant";
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
