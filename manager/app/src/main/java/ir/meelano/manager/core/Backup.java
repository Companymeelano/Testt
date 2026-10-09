package ir.meelano.manager.core;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import ir.meelano.manager.data.Atiran;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.NetRoute;
import ir.meelano.manager.data.Settings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Smart backup: dumps the key Atiran tables to CSV, zips them on the phone
 * (Downloads + shareable copy). Binary columns (photos, logos) are recorded
 * as placeholders so the backup stays small and fast.
 */
public final class Backup {
    private Backup() { }

    public interface Progress {
        void onTable(String faName, int done, int total);
    }

    public interface Done {
        void onDone(File zip, long bytes, String notes);

        void onFail(String faError);
    }

    /**
     * Table → Persian title, derived from the single source of truth
     * ({@link AtiranSchema#TABLES}). Order = zip order. The two product/customer
     * photo tables are skipped — their blobs would balloon a phone backup.
     */
    public static final String[][] TABLES = buildTables();

    private static String[][] buildTables() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] t : AtiranSchema.TABLES) {
            if (t == null || t.length < 2) continue;
            if ("ka_image".equalsIgnoreCase(t[0]) || "cus_image".equalsIgnoreCase(t[0])) continue;
            out.add(t);
        }
        return out.toArray(new String[0][]);
    }

    private static final int MAX_ROWS = 20000;

    public static void export(final Context ctx, final Settings s, final Progress pg, final Done done) {
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean bound = s.directConn() && NetRoute.bindDirect(app);
            try {
                try (Connection c = Atiran.open(s.effHost(), s.effPort(), s.effDb(), s.effUser(), s.effPass())) {
                    Meta m = new Meta(c);
                    String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
                    File dir = new File(app.getCacheDir(), "backup");
                    if (!dir.exists()) dir.mkdirs();
                    File zip = new File(dir, "meelano-backup-" + stamp + ".zip");
                    List<String> notes = new ArrayList<>();
                    int ok = 0;
                    try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
                        z.putNextEntry(new ZipEntry("_README.txt"));
                        z.write(readme(stamp, s.effDb()).getBytes("UTF-8"));
                        z.closeEntry();
                        for (int i = 0; i < TABLES.length; i++) {
                            final String fa = TABLES[i][1];
                            final int di = i + 1;
                            main.post(() -> {
                                try {
                                    pg.onTable(fa, di, TABLES.length);
                                } catch (Exception ignored) { }
                            });
                            String t = TABLES[i][0];
                            if (!m.table(t)) {
                                notes.add(fa + ": در دیتابیس نیست");
                                continue;
                            }
                            try {
                                int n = dumpTable(c, z, t);
                                ok++;
                                if (n >= MAX_ROWS) notes.add(fa + ": بیش از حد مجاز، " + MAX_ROWS + " ردیف ذخیره شد");
                            } catch (Exception e) {
                                notes.add(fa + ": خطا در خواندن");
                            }
                        }
                    }
                    copyToDownloads(app, zip);
                    final File fzip = zip;
                    final long bytes = zip.length();
                    final String noteStr = ok + " جدول ذخیره شد" + (notes.isEmpty() ? "" : " • " + joinFa(notes));
                    main.post(() -> done.onDone(fzip, bytes, noteStr));
                }
            } catch (Exception e) {
                final String err = Atiran.diagnose(e);
                main.post(() -> done.onFail(err));
            } finally {
                if (bound) NetRoute.unbind(app);
            }
        }).start();
    }

    private static String joinFa(List<String> notes) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, notes.size()); i++) {
            if (b.length() > 0) b.append("؛ ");
            b.append(notes.get(i));
        }
        if (notes.size() > 3) b.append("…");
        return b.toString();
    }

    private static String readme(String stamp, String db) {
        return "بکاپ هوشمند میلانو\n"
                + "تاریخ ساخت (میلادی): " + stamp + "\n"
                + "دیتابیس: " + db + "\n"
                + "هر جدول یک فایل CSV با سرستون است (UTF-8).\n"
                + "ستون‌های عکس/باینری به‌صورت <binary> ثبت شده‌اند.\n";
    }

    /** Dump one table to a CSV zip entry. Returns the row count. */
    private static int dumpTable(Connection c, ZipOutputStream z, String table) throws Exception {
        z.putNextEntry(new ZipEntry(table + ".csv"));
        // BOM so Excel opens Persian CSV correctly.
        z.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        StringBuilder sb = new StringBuilder();
        int rows = 0;
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(120);
            try (ResultSet r = st.executeQuery("SELECT * FROM dbo.[" + table.replace("]", "") + "]")) {
                ResultSetMetaData md = r.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) sb.append(',');
                    sb.append(csv(md.getColumnLabel(i)));
                }
                sb.append("\r\n");
                while (r.next() && rows < MAX_ROWS) {
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) sb.append(',');
                        Object o;
                        try {
                            o = r.getObject(i);
                        } catch (Exception e) {
                            o = null;
                        }
                        sb.append(csv(cell(o)));
                    }
                    sb.append("\r\n");
                    rows++;
                    if (sb.length() > 256 * 1024) {
                        z.write(sb.toString().getBytes("UTF-8"));
                        sb.setLength(0);
                    }
                }
            }
        }
        if (sb.length() > 0) z.write(sb.toString().getBytes("UTF-8"));
        z.closeEntry();
        return rows;
    }

    private static String cell(Object o) {
        if (o == null) return "";
        if (o instanceof byte[]) return "<binary " + ((byte[]) o).length + ">";
        try {
            if (o instanceof java.sql.Blob) return "<binary>";
            if (o instanceof java.sql.Clob) {
                java.sql.Clob cl = (java.sql.Clob) o;
                long len = Math.min(cl.length(), 20000);
                return cl.getSubString(1, (int) len);
            }
        } catch (Exception ignored) {
            return "";
        }
        String s = o.toString().replace("\r", " ").replace("\n", " ");
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Copy the zip into Downloads (no permission needed on Android 10+). */
    private static void copyToDownloads(Context app, File zip) {
        InputStream in = null;
        OutputStream out = null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, zip.getName());
                v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                android.net.Uri uri = app.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return;
                out = app.getContentResolver().openOutputStream(uri);
                in = new FileInputStream(zip);
                copy(in, out);
            } else {
                File docs = app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (docs == null) return;
                if (!docs.exists()) docs.mkdirs();
                in = new FileInputStream(zip);
                out = new FileOutputStream(new File(docs, zip.getName()));
                copy(in, out);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) { }
            try {
                if (out != null) out.close();
            } catch (Exception ignored) { }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (in == null || out == null) return;
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    /** Human size: «۲٫۴ مگ». */
    public static String sizeFa(long bytes) {
        try {
            if (bytes < 1024) return Money.fa(String.valueOf(bytes)) + " بایت";
            double kb = bytes / 1024.0;
            if (kb < 1024) return Money.fa(trim(kb)) + " کیلو";
            double mb = kb / 1024.0;
            return Money.fa(trim(mb)) + " مگ";
        } catch (Exception e) {
            return "";
        }
    }

    private static String trim(double v) {
        long r = Math.round(v * 10);
        if (r % 10 == 0) return String.valueOf(r / 10);
        return (r / 10) + "." + Math.abs(r % 10);
    }

    /** Copy a file into the share dir so ShareProvider can send it. Returns the copy or null. */
    public static File toShareDir(Context c, File src) {
        try {
            File dir = new File(c.getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, src.getName());
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(dst);
                copy(in, out);
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) { }
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) { }
            }
            return dst;
        } catch (Exception e) {
            return null;
        }
    }

}
