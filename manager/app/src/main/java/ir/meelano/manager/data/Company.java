package ir.meelano.manager.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import ir.meelano.manager.core.MasterQueries;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Company profile from dbo.company (Atiran's «تغییر نام شرکت» section):
 * name, director, phones, address, codes + the logo bytes.
 * Cached on the phone (prefs + PNG file) so the header paints instantly;
 * refreshed in the background on every launch.
 */
public final class Company {
    private Company() { }

    public static final class Info {
        public String name = "";
        public String director = "";
        public String addr = "";
        public String tell1 = "";
        public String tell2 = "";
        public String cell = "";
        public String fax = "";
        public String meli = "";
        public String egh = "";
        public String pos = "";

        /** Best display name: DB name → manual shop name → default. */
        public String displayName(Context c) {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            try {
                String m = new Settings(c).shopName();
                if (!m.isEmpty()) return m;
            } catch (Exception ignored) { }
            return "فروشگاه میلانو";
        }

        /** Best contact phone: mobile → tel1 → tel2 → fax → manual. */
        public String bestPhone(Context c) {
            for (String p : new String[]{cell, tell1, tell2, fax})
                if (p != null && !p.trim().isEmpty()) return p.trim();
            try {
                String m = new Settings(c).shopPhone();
                if (!m.isEmpty()) return m;
            } catch (Exception ignored) { }
            return "";
        }

        /** All phones joined for cards: «cell • tell1 • tell2». */
        public String phonesLine(Context c) {
            StringBuilder b = new StringBuilder();
            for (String p : new String[]{cell, tell1, tell2}) {
                if (p == null || p.trim().isEmpty()) continue;
                if (b.length() > 0) b.append(" • ");
                b.append(p.trim());
            }
            if (b.length() == 0) {
                String m = bestPhone(c);
                if (!m.isEmpty()) return m;
            }
            return b.toString();
        }

        public String displayAddr(Context c) {
            if (addr != null && !addr.trim().isEmpty()) return addr.trim();
            try {
                return new Settings(c).shopAddr();
            } catch (Exception ignored) { }
            return "";
        }
    }

    private static Info mem;
    private static Bitmap logoMem;
    private static boolean logoMemLoaded;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("meelano_company", Context.MODE_PRIVATE);
    }

    private static File logoFile(Context c) {
        return new File(c.getFilesDir(), "company_logo.png");
    }

    /** Cached profile (empty fields when never synced). Never null. */
    public static synchronized Info get(Context c) {
        if (mem != null) return mem;
        Info i = new Info();
        try {
            SharedPreferences p = prefs(c);
            i.name = p.getString("name", "");
            i.director = p.getString("director", "");
            i.addr = p.getString("addr", "");
            i.tell1 = p.getString("tell1", "");
            i.tell2 = p.getString("tell2", "");
            i.cell = p.getString("cell", "");
            i.fax = p.getString("fax", "");
            i.meli = p.getString("meli", "");
            i.egh = p.getString("egh", "");
            i.pos = p.getString("pos", "");
        } catch (Exception ignored) { }
        mem = i;
        return i;
    }

    /** Cached logo bitmap (sampled ≤256px) or null. Never throws. */
    public static synchronized Bitmap logo(Context c) {
        if (logoMemLoaded) return logoMem;
        logoMemLoaded = true;
        try {
            File f = logoFile(c);
            if (!f.exists() || f.length() < 100) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while ((o.outWidth / s > 256 || o.outHeight / s > 256) && s < 8) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            logoMem = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception ignored) {
            logoMem = null;
        }
        return logoMem;
    }

    private static synchronized void invalidate() {
        mem = null;
        logoMem = null;
        logoMemLoaded = false;
    }

    /**
     * Refresh profile + logo from Atiran in the background. The logo bytes are
     * re-downloaded only when their size changed (or the cache is missing).
     * done runs on the UI thread, always (even on failure — cache is kept).
     */
    public static void refresh(final Context c, Settings s, Repo repo, final Runnable done) {
        final Context app = c.getApplicationContext();
        try {
            repo.run(conn -> {
                Meta m = new Meta(conn);
                Row p = Repo.one(conn, MasterQueries.companyProfile(m));
                SharedPreferences.Editor e = prefs(app).edit();
                e.putString("name", p.s("name")).putString("director", p.s("director"))
                        .putString("addr", p.s("addr")).putString("tell1", p.s("tell1"))
                        .putString("tell2", p.s("tell2")).putString("cell", p.s("cell"))
                        .putString("fax", p.s("fax")).putString("meli", p.s("meli"))
                        .putString("egh", p.s("egh")).putString("pos", p.s("pos"));
                e.apply();
                long len = 0;
                try {
                    len = Repo.one(conn, MasterQueries.companyLogoLen(m)).l("len");
                } catch (Exception ignored) { }
                long saved = prefs(app).getLong("logo_len", -1);
                File f = logoFile(app);
                if (len > 100 && (len != saved || !f.exists())) {
                    byte[] blob = null;
                    try {
                        blob = Repo.one(conn, MasterQueries.companyLogo(m)).bytes("logo");
                    } catch (Exception ignored) { }
                    if (blob != null && blob.length > 100) {
                        try (FileOutputStream out = new FileOutputStream(f)) {
                            out.write(blob);
                        }
                        prefs(app).edit().putLong("logo_len", len).apply();
                    }
                }
                return null;
            }, new Repo.Cb<Object>() {
                @Override
                public void ok(Object v) {
                    invalidate();
                    runDone(done);
                }

                @Override
                public void fail(String faError) {
                    runDone(done);
                }
            });
        } catch (Exception e) {
            runDone(done);
        }
    }

    private static void runDone(Runnable done) {
        try {
            if (done != null) done.run();
        } catch (Exception ignored) { }
    }
}
