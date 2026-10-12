package ir.meelano.manager.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import ir.meelano.manager.core.Queries;
import ir.meelano.manager.core.SmartLink;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background database runner. Screens describe WHAT they need (a Task using
 * Queries + Meta); Repo opens one connection, runs it off the UI thread and
 * posts the result back. Only SELECT queries are ever executed (read-only app).
 */
public final class Repo {
    public interface Task<T> {
        T run(Connection c) throws Exception;
    }

    public interface Cb<T> {
        void ok(T v);
        void fail(String faError);
    }

    /** Opens one validated connection (smart dual-path or explicit values). */
    public interface Opener {
        Connection open() throws Exception;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Settings settings;
    private final Context appCtx;

    public Repo(Context ctx, Settings settings) {
        this.appCtx = ctx == null ? null : ctx.getApplicationContext();
        this.settings = settings;
    }

    /** Smart run: inside/outside profiles in auto-detected order with failover. */
    public <T> void run(final Task<T> t, final Cb<T> cb) {
        runOpener(() -> SmartLink.open(appCtx, settings), t, cb);
    }

    /** Same as {@link #run}, but against explicit connection values (tests unsaved settings). */
    public <T> void runWith(final String host, final int port, final String db,
                            final String user, final String pass, final Task<T> t, final Cb<T> cb) {
        runOpener(() -> Atiran.open(host, port, db, user, pass), t, cb);
    }

    private <T> void runOpener(final Opener opener, final Task<T> t, final Cb<T> cb) {
        final Runnable job = () -> {
            Object out = null;
            String err = null;
            boolean direct = settings.directConn();
            boolean bound = direct && NetRoute.bindDirect(appCtx);
            try {
                try (Connection c = opener.open()) {
                    out = t.run(c);
                }
            } catch (Queries.Missing m) {
                err = m.getMessage();
            } catch (Exception e) {
                err = Atiran.diagnose(e);
                if (!direct && NetRoute.isVpnActive(appCtx) && NetRoute.looksLikeNetwork(e))
                    err += "؛ فیلترشکن فعال است — «اتصال مستقیم» را در تنظیمات روشن کنید";
            } finally {
                if (bound) NetRoute.unbind(appCtx);
            }
            final Object res = out;
            final String e2 = err;
            main.post(() -> {
                if (e2 != null) cb.fail(e2);
                else {
                    @SuppressWarnings("unchecked")
                    T v = (T) res;
                    cb.ok(v);
                }
            });
        };
        try {
            pool.execute(job);
        } catch (java.util.concurrent.RejectedExecutionException rej) {
            main.post(() -> cb.fail("برنامه در حال بستن است؛ دوباره تلاش کنید"));
        }
    }

    /** Execute one SELECT query → rows. */
    public static List<Row> exec(Connection c, Queries.Q q) throws Exception {
        guard(q.sql);
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(q.sql)) {
            for (int i = 0; i < q.binds.size(); i++) ps.setObject(i + 1, q.binds.get(i));
            ps.setQueryTimeout(60);
            try (ResultSet r = ps.executeQuery()) {
                ResultSetMetaData m = r.getMetaData();
                int n = m.getColumnCount();
                String[] labels = new String[n];
                for (int i = 0; i < n; i++) {
                    String l = m.getColumnLabel(i + 1);
                    labels[i] = l == null || l.isEmpty() ? m.getColumnName(i + 1) : l;
                }
                while (r.next()) {
                    Row row = new Row();
                    for (int i = 0; i < n; i++) row.put(labels[i], r.getObject(i + 1));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Execute one SELECT query → first row or an empty Row. */
    public static Row one(Connection c, Queries.Q q) throws Exception {
        List<Row> rows = exec(c, q);
        return rows.isEmpty() ? new Row() : rows.get(0);
    }

    /** Read-only guard: internal queries must be SELECT (or WITH … SELECT). */
    private static void guard(String sql) throws Exception {
        String s = sql == null ? "" : sql.trim();
        String u = s.length() > 6 ? s.substring(0, 6).toUpperCase(java.util.Locale.US) : s.toUpperCase(java.util.Locale.US);
        if (!u.startsWith("SELECT") && !u.startsWith("WITH")) throw new Exception("فقط خواندن اطلاعات مجاز است");
    }

    public void close() {
        try { pool.shutdownNow(); } catch (Exception ignored) { }
    }
}
