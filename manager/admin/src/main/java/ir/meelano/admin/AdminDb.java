package ir.meelano.admin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local seller database: customers + every pack ever minted (incl. revoked). */
public class AdminDb extends SQLiteOpenHelper {

    private static final String NAME = "meelano_admin.db";
    private static final int VERSION = 1;

    public static final class Customer {
        public long id;
        public String name = "", family = "", shop = "", phone = "", city = "", dev = "";
        public long created;
        public String full() {
            String n = (name + " " + family).trim();
            return n.isEmpty() ? "(بی‌نام)" : n;
        }
    }

    public static final class Lic {
        public long id, customerId;
        public String dev = "", plan = "T", pack = "", note = "";
        public long exp, iat, created;
        public boolean revoked;
        public String customer = "";
    }

    public AdminDb(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT DEFAULT '',family TEXT DEFAULT '',shop TEXT DEFAULT '',"
                + "phone TEXT DEFAULT '',city TEXT DEFAULT '',"
                + "dev TEXT UNIQUE NOT NULL,created INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE licenses(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "customer_id INTEGER NOT NULL DEFAULT 0,dev TEXT DEFAULT '',"
                + "plan TEXT DEFAULT 'T',exp INTEGER DEFAULT 0,iat INTEGER DEFAULT 0,"
                + "pack TEXT DEFAULT '',revoked INTEGER DEFAULT 0,note TEXT DEFAULT '',"
                + "created INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_lic_dev ON licenses(dev)");
        db.execSQL("CREATE INDEX idx_lic_cust ON licenses(customer_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1: nothing to migrate yet.
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
                db.update("customers", v, "dev=?", new String[]{s(dev)});
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
