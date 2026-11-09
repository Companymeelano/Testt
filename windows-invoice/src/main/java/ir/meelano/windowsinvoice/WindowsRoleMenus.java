package ir.meelano.windowsinvoice;

import ir.meelano.windowsinvoice.data.AtiranRoles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full Manager menu set on Windows, filtered by the signed-in Atiran role.
 * Connection/setup is always available; everything else follows the role, with the
 * documented seller fallback for accounts whose Atiran role cannot be recognized.
 */
public final class WindowsRoleMenus {
    private WindowsRoleMenus() { }

    public static final String INVOICE = "invoice";
    public static final String CUSTOMERS = "customers";
    public static final String CUSTOMER_CENTER = "customer_center";
    public static final String PRODUCTS = "products";
    public static final String STOCK_LEDGER = "stock_ledger";
    public static final String CASH = "cash";
    public static final String CHEQUES = "cheques";
    public static final String REPORTS = "reports";
    public static final String PROFIT = "profit";
    public static final String PRODUCTION = "production";
    public static final String PERSONNEL = "personnel";
    public static final String USERS = "users";
    public static final String MOADIAN = "moadian";
    public static final String HANDOFF = "handoff";
    public static final String CONNECTION = "connection";

    public static final class Item {
        public final String id;
        public final String label;
        public final String shortcut;

        Item(String id, String label, String shortcut) {
            this.id = id;
            this.label = label;
            this.shortcut = shortcut;
        }
    }

    private static final Map<String, Item> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put(INVOICE, new Item(INVOICE, "ثبت فاکتور", "Ctrl+1 · شروع از مشتری"));
        CATALOG.put(CUSTOMERS, new Item(CUSTOMERS, "انتخاب مشتری", "Ctrl+F · جست‌وجوی مشتری"));
        CATALOG.put(CUSTOMER_CENTER, new Item(CUSTOMER_CENTER, "مرکز مشتریان", "F11 · فهرست و گردش حساب"));
        CATALOG.put(PRODUCTS, new Item(PRODUCTS, "جست‌وجوی کالا", "Ctrl+2 · کالا و بارکد"));
        CATALOG.put(STOCK_LEDGER, new Item(STOCK_LEDGER, "گردش و موجودی انبار", "Ctrl+Shift+4 · انبار"));
        CATALOG.put(CASH, new Item(CASH, "صندوق و دریافتی‌ها", "Ctrl+Shift+5 · صندوق"));
        CATALOG.put(CHEQUES, new Item(CHEQUES, "چک‌های دریافتی و پرداختی", "Ctrl+Shift+6 · چک‌ها"));
        CATALOG.put(REPORTS, new Item(REPORTS, "گزارش‌های فروش و مالی", "Ctrl+Shift+7 · گزارش"));
        CATALOG.put(PROFIT, new Item(PROFIT, "سود و بهای تمام‌شده", "Ctrl+Shift+8 · سود"));
        CATALOG.put(PRODUCTION, new Item(PRODUCTION, "تولید و شمارش", "Ctrl+Shift+9 · تولید"));
        CATALOG.put(PERSONNEL, new Item(PERSONNEL, "پرسنل و مأموریت‌ها", "Ctrl+Shift+0 · پرسنل"));
        CATALOG.put(USERS, new Item(USERS, "مدیریت کاربران و دسترسی", "Ctrl+Shift+U · دسترسی"));
        CATALOG.put(MOADIAN, new Item(MOADIAN, "سامانه مودیان", "ارسال مالیاتی"));
        CATALOG.put(HANDOFF, new Item(HANDOFF, "انتقال فایل و اشتراک ویندوز", "اشتراک بومی ویندوز"));
        CATALOG.put(CONNECTION, new Item(CONNECTION, "اتصال و تنظیمات سرور", "تنظیم SQL Server"));
    }

    /** Every menu id in catalog order. */
    public static List<String> allIds() {
        return Collections.unmodifiableList(new ArrayList<>(CATALOG.keySet()));
    }

    /** Menu ids visible to one Atiran application role; unknown roles fall back to seller. */
    public static List<String> idsFor(String role) {
        List<String> out = new ArrayList<>();
        String effective = AtiranRoles.isKnown(role) ? role : AtiranRoles.SELLER;
        if (AtiranRoles.ADMIN.equals(effective)) {
            out.addAll(allIds());
        } else if (AtiranRoles.SALES_MANAGER.equals(effective)) {
            Collections.addAll(out, INVOICE, CUSTOMERS, CUSTOMER_CENTER, PRODUCTS,
                    REPORTS, PROFIT, PERSONNEL, HANDOFF, CONNECTION);
        } else if (AtiranRoles.ACCOUNTANT.equals(effective)) {
            Collections.addAll(out, CUSTOMERS, CUSTOMER_CENTER, CASH, CHEQUES,
                    REPORTS, PROFIT, HANDOFF, CONNECTION);
        } else if (AtiranRoles.WAREHOUSE.equals(effective)) {
            Collections.addAll(out, PRODUCTS, STOCK_LEDGER, PRODUCTION, REPORTS,
                    HANDOFF, CONNECTION);
        } else if (AtiranRoles.VISITOR.equals(effective)) {
            Collections.addAll(out, INVOICE, CUSTOMERS, CUSTOMER_CENTER, PRODUCTS,
                    REPORTS, HANDOFF, CONNECTION);
        } else if (AtiranRoles.DISTRIBUTOR.equals(effective)) {
            Collections.addAll(out, CUSTOMERS, CUSTOMER_CENTER, PRODUCTS, REPORTS,
                    HANDOFF, CONNECTION);
        } else if (AtiranRoles.MOADIAN.equals(effective)) {
            Collections.addAll(out, INVOICE, CUSTOMERS, CUSTOMER_CENTER, MOADIAN,
                    REPORTS, HANDOFF, CONNECTION);
        } else {
            Collections.addAll(out, INVOICE, CUSTOMERS, CUSTOMER_CENTER, PRODUCTS,
                    CASH, REPORTS, HANDOFF, CONNECTION);
        }
        if (!out.contains(CONNECTION)) out.add(CONNECTION);
        return Collections.unmodifiableList(out);
    }

    /** Visible menu entries (label + shortcut) for one role, in catalog order. */
    public static List<Item> itemsFor(String role) {
        List<String> ids = idsFor(role);
        List<Item> out = new ArrayList<>();
        for (String id : allIds()) if (ids.contains(id)) out.add(CATALOG.get(id));
        return Collections.unmodifiableList(out);
    }

    /**
     * Manager section for a Ctrl+Shift+digit shortcut. Plain Ctrl+1..0 stay bound to the invoice fields,
     * so the sections use the Shift variants; unknown digits map to no section.
     */
    public static String sectionForDigit(int digit) {
        switch (digit) {
            case 4: return STOCK_LEDGER;
            case 5: return CASH;
            case 6: return CHEQUES;
            case 7: return REPORTS;
            case 8: return PROFIT;
            case 9: return PRODUCTION;
            case 0: return PERSONNEL;
            default: return "";
        }
    }

    public static boolean allows(String role, String menuId) {
        return menuId != null && idsFor(role).contains(menuId);
    }

    /** True only for roles that may register a real invoice. */
    public static boolean canSubmitInvoice(String role) {
        return allows(role, INVOICE);
    }

    public static String labelOf(String menuId) {
        Item item = CATALOG.get(menuId);
        return item == null ? "" : item.label;
    }
}
