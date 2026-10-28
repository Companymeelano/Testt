package ir.meelano.manager.core;

/**
 * Legacy identity labels used only to reject old synthetic sessions safely.
 * This class intentionally contains no password and cannot create a login.
 * Every new session must be verified against Atiran and carry a positive
 * sys_users.user_id.
 */
public final class LocalAccounts {
    private LocalAccounts() { }

    /** Legacy synthetic identity retained solely for session migration checks. */
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

    private static final Acc[] LEGACY = {
            new Acc("Modir", "مدیر", RoleStore.ADMIN, -9001),
            new Acc("Anbar", "انباردار", RoleStore.WAREHOUSE, -9002),
    };

    /** True when this was one of the old fixed local account names. */
    public static boolean isLocal(String user) {
        if (user == null) return false;
        String u = Money.en(user).trim();
        for (Acc a : LEGACY) if (a.user.equalsIgnoreCase(u)) return true;
        return false;
    }

    /** True only for a legacy synthetic session (never a real Atiran user). */
    public static boolean isLocalSession(String user, int uid, String role) {
        if (user == null || role == null) return false;
        String u = Money.en(user).trim();
        for (Acc a : LEGACY) {
            if (a.uid == uid && a.user.equalsIgnoreCase(u) && a.role.equals(role)) return true;
        }
        return false;
    }
}
