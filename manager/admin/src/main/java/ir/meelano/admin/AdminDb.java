package ir.meelano.admin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local seller database: customers + every pack ever minted (incl. revoked).
 * v2 adds the per-customer usage ledger (synced from request messages) and the
 * saved SQL Server profile used to mint connection cards.
 */
public class AdminDb extends SQLiteOpenHelper {

    private static final String NAME = "meelano_admin.db";
    private static final int VERSION = 5;

    public static final class Customer {
        public long id;
        public String name = "", family = "", shop = "", phone = "", city = "", dev = "";
        public long created;
        // v2: usage ledger (from MILANO-USE1 reports)
        public long lastUseDay;
        public long totalMin;
        public int opens;
        // v2: saved connection profile (for connection cards)
        public String dbHost = "", dbWan = "", dbPort = "", dbName = "", dbUser = "", dbPass = "";

        public String full() {
            String n = (name + " " + family).trim();
            return n.isEmpty() ? "(بی‌نام)" : n;
        }

        public boolean hasSite() {
            return (!dbHost.isEmpty() || !dbWan.isEmpty()) && !dbPort.isEmpty()
                    && !dbName.isEmpty() && !dbUser.isEmpty();
        }
    }

    public static final class Lic {
        public long id, customerId;
        public String dev = "", plan = "T", pack = "", note = "";
        public long exp, iat, created;
        public boolean revoked;
        public String customer = "";
    }

    /** License roll-up per customer for the categorized list. */
    public static final class CustLic {
        /** 0 = active & healthy, 1 = expiring soon (≤ 30 d), 2 = expired/none. */
        public int cat = 2;
        public char plan = 'M';
        public long days;
    }

    public AdminDb(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT DEFAULT '',family TEXT DEFAULT '',shop TEXT DEFAULT '',"
                + "phone TEXT DEFAULT '',city TEXT DEFAULT '',"
                + "dev TEXT UNIQUE NOT NULL,created INTEGER DEFAULT 0,"
                + "last_use_day INTEGER DEFAULT 0,total_min INTEGER DEFAULT 0,"
                + "opens INTEGER DEFAULT 0,"
                + "db_host TEXT DEFAULT '',db_wan TEXT DEFAULT '',db_port TEXT DEFAULT '',"
                + "db_name TEXT DEFAULT '',db_user TEXT DEFAULT '',"
                + "db_pass TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE licenses(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL DEFAULT 0,dev TEXT DEFAULT '',"
                + "plan TEXT DEFAULT 'T',exp INTEGER DEFAULT 0,iat INTEGER DEFAULT 0,"
                + "pack TEXT DEFAULT '',revoked INTEGER DEFAULT 0,note TEXT DEFAULT '',"
                + "created INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_lic_dev ON licenses(dev)");
        db.execSQL("CREATE INDEX idx_lic_cust ON licenses(customer_id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS inbox(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "dev TEXT DEFAULT '',name TEXT DEFAULT '',phone TEXT DEFAULT '',"
                + "body TEXT DEFAULT '',created INTEGER DEFAULT 0,shop TEXT DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            String[] cols = {"last_use_day INTEGER DEFAULT 0",
                    "total_min INTEGER DEFAULT 0", "opens INTEGER DEFAULT 0",
                    "db_host TEXT DEFAULT ''", "db_port TEXT DEFAULT ''",
                    "db_name TEXT DEFAULT ''", "db_user TEXT DEFAULT ''",
                    "db_pass TEXT DEFAULT ''"};
            for (String col : cols) {
                try {
                    db.execSQL("ALTER TABLE customers ADD COLUMN " + col);
                } catch (Exception ignored) {
                    // Column already there (interrupted upgrade) — safe to skip.
                }
            }
        }
        if (oldV < 3) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS inbox(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "dev TEXT DEFAULT '',name TEXT DEFAULT '',phone TEXT DEFAULT '',"
                        + "body TEXT DEFAULT '',created INTEGER DEFAULT 0,shop TEXT DEFAULT '')");
            } catch (Exception ignored) { }
        }
        if (oldV < 4) {
            try {
                db.execSQL("ALTER TABLE inbox ADD COLUMN shop TEXT DEFAULT ''");
            } catch (Exception ignored) { }
        }
        if (oldV < 5) {
            try {
                db.execSQL("ALTER TABLE customers ADD COLUMN db_wan TEXT DEFAULT ''");
            } catch (Exception ignored) { }
        }
    }

    /** "lan" for private/LAN addresses, else "wan" (SITE1 placement). */
    public static String siteKind(String host) {
        try {
            String h = host == null ? "" : host.trim().toLowerCase(java.util.Locale.US);
            int scheme = h.indexOf("://");
            if (scheme >= 0) h = h.substring(scheme + 3);
            int slash = h.indexOf('/');
            if (slash >= 0) h = h.substring(0, slash);
            if (h.isEmpty() || h.equals("localhost") || h.equals("::1")
                    || h.endsWith(".local") || h.indexOf('.') < 0) return "lan";
            String[] p = h.split("\\.");
            if (p.length == 4) {
                int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
                if (a == 10 || a == 127) return "lan";
                if (a == 172 && b >= 16 && b <= 31) return "lan";
                if (a == 192 && b == 168) return "lan";
                return "wan";
            }
            return "wan";
        } catch (Exception e) {
            return "wan";
        }
    }

    // ---------- request inbox ----------

    public static final class Inbox {
        public long id;
        public String dev = "", name = "", phone = "", body = "", shop = "";
        public long created;
    }

    public long addInbox(String dev, String name, String phone, String shop, String body) {
        ContentValues v = new ContentValues();
        v.put("dev", s(dev));
        v.put("name", s(name));
        v.put("phone", s(phone));
        v.put("shop", s(shop));
        v.put("body", body == null ? "" : body);
        v.put("created", System.currentTimeMillis());
        try {
            return getWritableDatabase().insert("inbox", null, v);
        } catch (Exception e) {
            return -1;
        }
    }

    /** One row per device: a new request replaces the older one. */
    public void clearInboxForDev(String dev) {
        try {
            getWritableDatabase().delete("inbox", "dev=?", new String[]{s(dev)});
        } catch (Exception ignored) { }
    }

    public List<Inbox> inboxList() {
        List<Inbox> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("inbox", null, null, null,
                null, null, "created DESC", "200")) {
            if (c != null) while (c.moveToNext()) {
                Inbox o = new Inbox();
                o.id = getLong(c, "_id");
                o.dev = getStr(c, "dev");
                o.phone = getStr(c, "phone");
                o.name = getStr(c, "name");
                o.shop = getStr(c, "shop");
                o.body = getStr(c, "body");
                o.created = getLong(c, "created");
                out.add(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public int inboxCount() {
        try (Cursor c = getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM inbox", null)) {
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) { }
        return 0;
    }

    public boolean deleteInbox(long id) {
        try {
            return getWritableDatabase().delete("inbox", "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearInbox() {
        try {
            getWritableDatabase().delete("inbox", null, null);
        } catch (Exception ignored) { }
    }

    // ---------- customers ----------

    public long upsertCustomer(String name, String family, String shop,
                               String phone, String city, String dev) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", s(name));
        v.put("family", s(family));
        v.put("shop", s(shop));
        v.put("phone", s(phone));
        v.put("city", s(city));
        v.put("dev", s(dev));
        v.put("created", System.currentTimeMillis());
        long id = -1;
        try {
            id = db.insertWithOnConflict("customers", null, v,
                    SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception ignored) { }
        if (id < 0) {
            try {
                ContentValues u = new ContentValues(v);
                u.remove("created"); // keep the original registration date on re-upsert
                db.update("customers", u, "dev=?", new String[]{s(dev)});
            } catch (Exception ignored) { }
            Customer c = byDev(dev);
            return c == null ? -1 : c.id;
        }
        return id;
    }

    public Customer byId(long id) {
        try (Cursor c = getReadableDatabase().query("customers", null,
                "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c != null && c.moveToFirst()) return rowCustomer(c);
        } catch (Exception ignored) { }
        return null;
    }

    public Customer byDev(String dev) {
        try (Cursor c = getReadableDatabase().query("customers", null,
                "dev=?", new String[]{s(dev)}, null, null, null)) {
            if (c != null && c.moveToFirst()) return rowCustomer(c);
        } catch (Exception ignored) { }
        return null;
    }

    public List<Customer> searchCustomers(String q) {
        List<Customer> out = new ArrayList<>();
        String like = "%" + s(q) + "%";
        try (Cursor c = getReadableDatabase().query("customers", null,
                "name LIKE ? OR family LIKE ? OR shop LIKE ? OR phone LIKE ? OR city LIKE ? OR dev LIKE ?",
                new String[]{like, like, like, like, like, like},
                null, null, "created DESC", "300")) {
            if (c != null) while (c.moveToNext()) out.add(rowCustomer(c));
        } catch (Exception ignored) { }
        return out;
    }

    public int customerCount() {
        try (Cursor c = getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM customers", null)) {
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) { }
        return 0;
    }

    public boolean deleteCustomer(long id) {
        try {
            return getWritableDatabase().delete("customers", "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Store a usage report (only moves forward — stale pastes can't rewind). */
    public boolean updateUsage(String dev, long totalMin, int opens, long lastDay) {
        try {
            Customer c = byDev(dev);
            if (c == null) return false;
            ContentValues v = new ContentValues();
            v.put("total_min", Math.max(c.totalMin, totalMin));
            v.put("opens", Math.max(c.opens, opens));
            v.put("last_use_day", Math.max(c.lastUseDay, lastDay));
            return getWritableDatabase().update("customers", v, "dev=?",
                    new String[]{s(dev)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean saveSite(long id, String lan, String wan, String port, String db,
                             String user, String pass) {
        try {
            ContentValues v = new ContentValues();
            v.put("db_host", s(lan));
            v.put("db_wan", s(wan));
            v.put("db_port", s(port));
            v.put("db_name", s(db));
            v.put("db_user", s(user));
            v.put("db_pass", pass == null ? "" : pass);
            return getWritableDatabase().update("customers", v, "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Move a customer record to a new device code (phone change). False on clash. */
    public boolean updateDev(long id, String newDev) {
        try {
            ContentValues v = new ContentValues();
            v.put("dev", s(newDev));
            return getWritableDatabase().update("customers", v, "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- licenses ----------

    public long insertLicense(long customerId, String dev, char plan,
                              long exp, long iat, String pack, String note) {
        ContentValues v = new ContentValues();
        v.put("customer_id", customerId);
        v.put("dev", s(dev));
        v.put("plan", String.valueOf(plan));
        v.put("exp", exp);
        v.put("iat", iat);
        v.put("pack", s(pack));
        v.put("revoked", 0);
        v.put("note", s(note));
        v.put("created", System.currentTimeMillis());
        try {
            return getWritableDatabase().insert("licenses", null, v);
        } catch (Exception e) {
            return -1;
        }
    }

    public Lic licById(long id) {
        try (Cursor c = getReadableDatabase().query("licenses", null,
                "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                Lic l = rowLic(c);
                attachCustomer(l);
                return l;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * @param filter 0 = all, 1 = active, 2 = expired, 3 = revoked
     */
    public List<Lic> licenses(int filter, long today) {
        List<Lic> out = new ArrayList<>();
        String sel = null;
        String[] args = null;
        if (filter == 1) {
            sel = "revoked=0 AND exp>=?";
            args = new String[]{String.valueOf(today)};
        } else if (filter == 2) {
            sel = "revoked=0 AND exp<?";
            args = new String[]{String.valueOf(today)};
        } else if (filter == 3) {
            sel = "revoked=1";
        }
        try (Cursor c = getReadableDatabase().query("licenses", null, sel, args,
                null, null, "created DESC", "500")) {
            if (c != null) while (c.moveToNext()) {
                Lic l = rowLic(c);
                attachCustomer(l);
                out.add(l);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public List<Lic> licensesForCustomer(long customerId) {
        List<Lic> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("licenses", null,
                "customer_id=?", new String[]{String.valueOf(customerId)},
                null, null, "created DESC", "200")) {
            if (c != null) while (c.moveToNext()) {
                Lic l = rowLic(c);
                attachCustomer(l);
                out.add(l);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Best active license per customer → category chip for the customer list. */
    public CustLic custState(long customerId, long today) {
        CustLic st = new CustLic();
        boolean any = false;
        long best = -1;
        try {
            for (Lic l : licensesForCustomer(customerId)) {
                any = true;
                if (l.revoked) continue;
                char p = (l.plan == null || l.plan.isEmpty()) ? 'T' : l.plan.charAt(0);
                if (p == 'P') {
                    st.cat = 0;
                    st.plan = 'P';
                    st.days = -1;
                    return st;
                }
                long left = l.exp - today;
                if (left >= 0 && left > best) {
                    best = left;
                    st.plan = p;
                }
            }
        } catch (Exception ignored) { }
        if (best >= 0) {
            st.cat = best <= 30 ? 1 : 0;
            st.days = best;
        } else {
            st.cat = 2;
            st.days = 0;
        }
        return st;
    }

    public int activeCount(long today) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM licenses WHERE revoked=0 AND exp>=?",
                new String[]{String.valueOf(today)})) {
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) { }
        return 0;
    }

    /** Active packs expiring within {@code days} (for the «expiring soon» card). */
    public List<Lic> expiringSoon(long today, long days) {
        List<Lic> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("licenses", null,
                "revoked=0 AND exp>=? AND exp<=?",
                new String[]{String.valueOf(today), String.valueOf(today + days)},
                null, null, "exp ASC", "50")) {
            if (c != null) while (c.moveToNext()) {
                Lic l = rowLic(c);
                attachCustomer(l);
                out.add(l);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public boolean setRevoked(long id, boolean revoked) {
        ContentValues v = new ContentValues();
        v.put("revoked", revoked ? 1 : 0);
        try {
            return getWritableDatabase().update("licenses", v, "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Revoke every live pack of an old device (phone-change transfer) and stamp
     * the note. Returns how many rows were revoked.
     */
    public int revokeActiveForDev(String dev, String stamp) {
        try {
            android.database.sqlite.SQLiteStatement st = getWritableDatabase().compileStatement(
                    "UPDATE licenses SET revoked=1, note=note||? WHERE dev=? AND revoked=0");
            st.bindString(1, stamp == null ? "" : stamp);
            st.bindString(2, s(dev));
            return st.executeUpdateDelete();
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean deleteLicense(long id) {
        try {
            return getWritableDatabase().delete("licenses", "_id=?",
                    new String[]{String.valueOf(id)}) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- helpers ----------

    private void attachCustomer(Lic l) {
        Customer c = byId(l.customerId);
        if (c == null && l.dev != null && !l.dev.isEmpty()) c = byDev(l.dev);
        l.customer = c == null ? "—" : c.full()
                + (c.shop.isEmpty() ? "" : " • " + c.shop);
    }

    private static Customer rowCustomer(Cursor c) {
        Customer o = new Customer();
        o.id = getLong(c, "_id");
        o.name = getStr(c, "name");
        o.family = getStr(c, "family");
        o.shop = getStr(c, "shop");
        o.phone = getStr(c, "phone");
        o.city = getStr(c, "city");
        o.dev = getStr(c, "dev");
        o.created = getLong(c, "created");
        o.lastUseDay = getLong(c, "last_use_day");
        o.totalMin = getLong(c, "total_min");
        o.opens = (int) getLong(c, "opens");
        o.dbHost = getStr(c, "db_host");
        o.dbWan = getStr(c, "db_wan");
        o.dbPort = getStr(c, "db_port");
        o.dbName = getStr(c, "db_name");
        o.dbUser = getStr(c, "db_user");
        o.dbPass = getStr(c, "db_pass");
        return o;
    }

    private static Lic rowLic(Cursor c) {
        Lic o = new Lic();
        o.id = getLong(c, "_id");
        o.customerId = getLong(c, "customer_id");
        o.dev = getStr(c, "dev");
        o.plan = getStr(c, "plan");
        o.exp = getLong(c, "exp");
        o.iat = getLong(c, "iat");
        o.pack = getStr(c, "pack");
        o.revoked = getLong(c, "revoked") != 0;
        o.note = getStr(c, "note");
        o.created = getLong(c, "created");
        return o;
    }

    private static String getStr(Cursor c, String col) {
        try {
            int i = c.getColumnIndex(col);
            if (i < 0) return "";
            String v = c.getString(i);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static long getLong(Cursor c, String col) {
        try {
            int i = c.getColumnIndex(col);
            return i < 0 ? 0 : c.getLong(i);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String s(String v) {
        return v == null ? "" : v.trim();
    }
}
