package ir.meelano.android;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Role defaults and authorization rules for the licensed all-in-one app. */
final class MeelanoAccessPolicy {
    private MeelanoAccessPolicy() { }

    static String canonicalRole(String value) {
        if (value == null) return "user";
        String normalized = value.toLowerCase(Locale.US).replace('ي', 'ی').replace('ك', 'ک')
                .replace('ة', 'ه').replace('\u200c', ' ').trim();
        if (normalized.isEmpty()) return "user";
        String compact = normalized.replaceAll("[\\s_./-]+", "");

        // Roles that receive all-app access must match a known whole role name. Substring
        // matching here would turn values such as "assistant manager" or "non-admin" into
        // privileged accounts.
        if (oneOf(compact, "admin", "administrator", "systemadmin", "systemadministrator", "sysadmin",
                "مدیرکل", "مدیرسیستم")) return "admin";
        if (oneOf(compact, "manager", "generalmanager", "salesmanager", "branchmanager", "storemanager",
                "regionalmanager", "مدیر", "modir", "مدیرفروش", "مدیرمجموعه", "مدیربخش", "مدیرشعبه", "مدیرعامل")) return "manager";
        if (oneOf(compact, "senioraccountant", "حسابدارارشد")) return "senior_accountant";
        if (oneOf(compact, "accountant", "حسابدار")) return "accountant";
        if (oneOf(compact, "warehouse", "warehouseoperator", "انباردار", "انبار")) return "warehouse";
        if (oneOf(compact, "collections", "collection", "مطالبات", "وصول")) return "collections";
        if (oneOf(compact, "salesemployee", "sales", "salesperson", "کارمندبخش‌فروش", "کارمندبخش فروش", "کارمندفروش")) return "sales_employee";
        if (oneOf(compact, "distributor", "distribution", "پخش", "مامورپخش", "مامورپخشگر")) return "distributor";
        if (oneOf(compact, "driver", "راننده")) return "driver";
        if (oneOf(compact, "worker", "کارگر")) return "worker";
        if (oneOf(compact, "visitor", "ویزیتور", "بازاریاب")) return "visitor";
        if (oneOf(compact, "senior", "supervisor", "ارشد", "سرپرست")) return "senior";
        if (oneOf(compact, "employee", "کارمند")) return "employee";
        return "user";
    }

    /** A visitor-table identity is a visitor by default; names alone never grant a role. */
    static String fallbackRole(boolean hasAtiranVisitorId) {
        return hasAtiranVisitorId ? "visitor" : "user";
    }

    /** Shared app shell, role-specific landing page; explicit grants still decide if it can open. */
    static String unifiedHomePage(String rawRole, boolean fullAccess) {
        if (fullAccess) return "dashboard";
        return "warehouse".equals(canonicalRole(rawRole)) ? "warehouse" : "visitor_dashboard";
    }

    static Set<String> defaultPermissions(String rawRole) {
        String role = canonicalRole(rawRole);
        LinkedHashSet<String> p = new LinkedHashSet<>();
        p.add("attendance");
        p.add("attendance_self");
        p.add("leave_balance");
        p.add("leave_request");
        if ("admin".equals(role) || "manager".equals(role)) return Collections.unmodifiableSet(p);
        p.add("dashboard");
        if ("senior".equals(role) || "visitor".equals(role) || "sales_employee".equals(role)) {
            add(p, "visitor_dashboard", "showcase", "cart", "cart_draft", "cart_submit", "cart_signature",
                    "customer_select", "prefactor_list", "visit_route", "cart_discount", "cart_pdf",
                    "offline_queue", "day_report", "visitor_receipt", "assistant", "customers", "customer_detail",
                    "customer_call", "customer_message", "products", "product_detail");
        } else if ("distributor".equals(role)) {
            add(p, "visitor_dashboard", "showcase", "cart", "cart_draft", "customer_select",
                    "prefactor_list", "visit_route", "offline_queue", "day_report", "customers",
                    "customer_detail", "customer_call", "products", "product_detail");
        } else if ("driver".equals(role)) {
            add(p, "visitor_dashboard", "customers", "customer_call");
        } else if ("worker".equals(role)) {
            p.add("products");
        } else if ("warehouse".equals(role)) {
            add(p, "warehouse", "products", "product_detail", "showcase");
        } else if ("senior_accountant".equals(role)) {
            add(p, "customers", "customer_detail", "reports", "command", "taxpayers", "tax_send", "connection_health");
        } else if ("accountant".equals(role)) {
            add(p, "customers", "customer_detail", "reports", "taxpayers");
        } else if ("employee".equals(role)) {
            add(p, "customers", "products");
        } else if ("collections".equals(role)) {
            add(p, "customers", "customer_detail", "customer_call", "customer_message", "reports");
        }
        return Collections.unmodifiableSet(p);
    }

    static boolean allows(String rawRole, Set<String> permissions, String permission) {
        String role = canonicalRole(rawRole);
        // The system administrator is the only unconditional superuser. A manager receives
        // broad defaults from MainActivity, but an explicit permission profile must be honored.
        if ("admin".equals(role)) return true;
        if (permission == null || permission.trim().isEmpty()) return true;
        Set<String> grants = permissions == null ? Collections.emptySet() : permissions;
        return grants.contains(permission.trim());
    }

    private static boolean oneOf(String value, String... accepted) {
        for (String candidate : accepted) {
            String normalized = candidate.toLowerCase(Locale.US).replace('ي', 'ی').replace('ك', 'ک')
                    .replace('ة', 'ه').replace('\u200c', ' ').trim().replaceAll("[\\s_./-]+", "");
            if (normalized.equals(value)) return true;
        }
        return false;
    }

    private static void add(Set<String> values, String... items) {
        Collections.addAll(values, items);
    }
}
