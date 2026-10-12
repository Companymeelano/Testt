package ir.meelano.manager.core;

/**
 * Fixed local accounts — added at the customer's explicit request (2026-10-10).
 *
 * These two names are accepted without touching the Atiran database, because the
 * real password check cannot be completed: Atiran stores `user_password` as a
 * proprietary binary hash whose algorithm is not public, so neither
 * {@code pwdcompare()} nor any standard digest matches it (see AtiranAuth).
 *
 * SECURITY — please read before shipping this build to shops:
 *  - These accounts use a hard-coded password and work against ANY database this
 *    build is pointed at, on every device it is installed on.
 *  - The same APK carries the shared SQL Server login (AdminAn).
 *  - Together that means anyone holding this file can open the books.
 *
 * REMOVING THE BACK DOOR: delete this file and the small block in
 * LoginActivity.doLogin() that calls it. Nothing else depends on this class.
 */
public final class LocalAccounts {

    private LocalAccounts() {
    }

    /** One fixed account: name, password, role, display name, synthetic id. */
    public static final class Acc {
        public final String user;
        public final String name;
        public final String role;
        /** Negative so it can never collide with a real sys_users.user_id. */
        public final int uid;

        Acc(String user, String name, String role, int uid) {
            this.user = user;
            this.name = name;
            this.role = role;
            this.uid = uid;
        }
    }

    private static final Acc[] ACCOUNTS = {
            new Acc("Modir", "مدیر", RoleStore.ADMIN, -9001),
            new Acc("Anbar", "انباردار", RoleStore.WAREHOUSE, -9002),
    };

    /** The matching fixed account, or null when the name/password do not match. */
    public static Acc match(String user, String pass) {
        if (user == null || pass == null) return null;
        String u = Money.en(user).trim();
        String p = Money.en(pass).trim();
        if (u.isEmpty() || p.isEmpty()) return null;
        for (Acc a : ACCOUNTS) {
            if (a.user.equalsIgnoreCase(u) && passOf(a).equals(p)) return a;
        }
        return null;
    }

    /** True when this name is one of the fixed accounts (used for messaging). */
    public static boolean isLocal(String user) {
        if (user == null) return false;
        String u = Money.en(user).trim();
        for (Acc a : ACCOUNTS) if (a.user.equalsIgnoreCase(u)) return true;
        return false;
    }

    private static String passOf(Acc a) {
        // One shared simple password, as requested.
        return "123";
    }
}
