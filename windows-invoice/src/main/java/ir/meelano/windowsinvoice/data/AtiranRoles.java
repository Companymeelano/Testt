package ir.meelano.windowsinvoice.data;

import java.util.Locale;

/**
 * Maps an Atiran role (id + Persian/English role name, falling back to the account name)
 * to one desktop application role. The keyword rules mirror the Android Manager app so a
 * person sees the same scope on the phone and on Windows.
 */
public final class AtiranRoles {
    private AtiranRoles() { }

    public static final String ADMIN = "admin";
    public static final String SALES_MANAGER = "sales_manager";
    public static final String SELLER = "seller";
    public static final String ACCOUNTANT = "accountant";
    public static final String VISITOR = "visitor";
    public static final String DISTRIBUTOR = "distributor";
    public static final String WAREHOUSE = "warehouse";
    public static final String MOADIAN = "moadian";
    public static final String UNKNOWN = "";

    /** Role from an informative role name, else from a canonical manager account name, else seller. */
    public static String resolve(int roleId, String roleName, String userName) {
        String mapped = mapRoleName(roleId, roleName);
        if (!mapped.isEmpty()) return mapped;
        String byUser = managerRoleForUserName(userName);
        return byUser.isEmpty() ? SELLER : byUser;
    }

    /** Empty when the role name carries no recognizable information. */
    public static String mapRoleName(int roleId, String roleName) {
        String name = normalize(roleName);
        String lower = name.toLowerCase(Locale.ROOT);
        boolean salesLead = (has(name, "مدیر") || has(name, "سرپرست") || has(name, "مسئول")
                || lower.contains("manager") || lower.contains("supervisor"))
                && (has(name, "فروش") || lower.contains("sale") || lower.contains("sales"));
        if (salesLead || lower.contains("sales manager") || lower.contains("sale manager"))
            return SALES_MANAGER;
        if (has(name, "مدیرعامل") || has(name, "مدیر عامل") || has(name, "مدیرکل") || has(name, "مدیر کل")
                || has(name, "مدیر") || lower.contains("admin") || lower.contains("sysadmin")
                || lower.contains("administrator") || lower.contains("modir") || lower.contains("owner"))
            return ADMIN;
        if (has(name, "حسابدار") || has(name, "حسابداری") || has(name, "مالی")
                || lower.contains("account") || lower.contains("finance")) return ACCOUNTANT;
        if (has(name, "انبار") || has(name, "انباردار") || has(name, "warehouse")
                || lower.contains("warehouse") || lower.contains("store keeper")) return WAREHOUSE;
        if (has(name, "ویزیتور") || has(name, "بازاریاب") || lower.contains("visitor")
                || lower.contains("marketer")) return VISITOR;
        if (has(name, "پخش") || has(name, "نماینده") || has(name, "عامل فروش") || has(name, "موزع")
                || lower.contains("distributor") || lower.contains("dealer")
                || lower.contains("agent")) return DISTRIBUTOR;
        if (has(name, "مودیان") || has(name, "مالیاتی") || has(name, "مؤدی") || has(name, "مودی")
                || lower.contains("moadian") || lower.contains("tax")) return MOADIAN;
        if (has(name, "فروشنده") || has(name, "صندوقدار") || lower.contains("seller")
                || lower.contains("cashier")) return SELLER;
        return UNKNOWN;
    }

    /** Canonical manager account names, matched as whole names (never substrings). */
    public static String managerRoleForUserName(String userName) {
        if (userName == null) return UNKNOWN;
        String user = normalize(userName).toLowerCase(Locale.ROOT).trim();
        if (user.isEmpty()) return UNKNOWN;
        if (user.equals("admin") || user.equals("modir") || user.equals("modiramel")
                || user.equals("modir_amel") || user.equals("administrator")
                || user.equals("sysadmin") || user.equals("sys_admin")) return ADMIN;
        if (user.matches("(admin|modir|modiramel|administrator|sysadmin)([_\\-]?\\d{1,3})?")) return ADMIN;
        return UNKNOWN;
    }

    public static boolean isKnown(String role) {
        switch (role == null ? "" : role) {
            case ADMIN: case SALES_MANAGER: case SELLER: case ACCOUNTANT:
            case VISITOR: case DISTRIBUTOR: case WAREHOUSE: case MOADIAN:
                return true;
            default:
                return false;
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace('\u064A', '\u06CC').replace('\u0643', '\u06A9')
                .replace('\u200C', ' ').replace('\u200E', ' ').replace('\u200F', ' ')
                .trim().replaceAll("\\s+", " ");
    }

    private static boolean has(String value, String needle) {
        return value != null && value.contains(needle);
    }
}
