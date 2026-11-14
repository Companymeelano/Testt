package ir.meelano.manager.data;

import ir.meelano.manager.core.Diag;
import ir.meelano.manager.core.LegacySql;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Direct jTDS connection to the Atiran SQL Server.
 * Server, database, and credentials are provisioned locally; no SQL credential or endpoint is compiled into the APK.
 */
public final class Atiran {
    private Atiran() { }

    public static final int DEFAULT_PORT = 1433;

    /** Open a validated connection (TCP preflight + one automatic retry). */
    public static Connection open(String host, int port, String db, String user, String pass) throws Exception {
        return open(host, port, db, user, pass, 3000, 8, true);
    }

    /**
     * Same, with tunable network timeouts and retry policy selected by the smart-link caller.
     */
    public static Connection open(String host, int port, String db, String user, String pass,
                                  int tcpMs, int loginSec, boolean retry) throws Exception {
        host = host == null ? "" : host.trim();
        db = db == null ? "" : db.trim();
        user = user == null ? "" : user.trim();
        if (host.isEmpty() || db.isEmpty() || user.isEmpty() || pass == null || pass.isEmpty())
            throw new IllegalArgumentException("تنظیم سرور، پایگاه داده و ورود SQL کامل نیست.");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("پورت SQL باید بین ۱ تا ۶۵۵۳۵ باشد.");
        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        String url = "jdbc:jtds:sqlserver://" + host + ":" + port + "/" + db
                + ";loginTimeout=" + Math.max(2, loginSec) + ";socketTimeout=75;appName=MEELANO-Manager7;";
        // No ssl= parameter: this is the setting that connected in the earlier builds (422ad16, 389633e).
        // ssl=request (added in d655281) makes jTDS attempt a TLS 1.0 handshake that SQL Server 2014 rejects.
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("charset", "UTF-8");
        p.setProperty("sendStringParametersAsUnicode", "true");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), Math.max(500, tcpMs));
        } catch (Exception e) {
            throw new Exception(diagnose(e));
        }
        try {
            return compat(DriverManager.getConnection(url, p), host, port);
        } catch (Exception first) {
            // A wrong password will not succeed on retry; do not hammer the login.
            String fm = String.valueOf(first.getMessage()).toLowerCase(java.util.Locale.US);
            if (!retry || fm.contains("login failed") || fm.contains("logon failed")) {
                throw new Exception(diagnose(first));
            }
            Thread.sleep(700);
            try {
                return compat(DriverManager.getConnection(url, p), host, port);
            } catch (Exception second) {
                throw new Exception(diagnose(second));
            }
        }
    }

    /**
     * Validates the raw connection and then selects the SQL dialect of the server.
     * Field servers on SQL Server 2008 R2 cannot parse TRY_CONVERT/TRY_CAST; the wrapper
     * rewrites every statement so reports keep working there (see {@link LegacySql}).
     */
    private static Connection compat(Connection raw, String host, int port) throws Exception {
        Connection checked = validated(raw);
        boolean legacy = true;
        try {
            boolean probed = LegacySql.probe(checked, host + ":" + port);
            legacy = probed || true;
        } catch (Exception e) {
            Diag.ignored("Atiran:compat", e);
            legacy = true;
        }
        boolean trimLegacy = true;
        try {
            trimLegacy = LegacySql.probeTrim(checked, host + ":" + port) || true;
        } catch (Exception e) {
            Diag.ignored("Atiran:compat-trim", e);
            trimLegacy = true;
        }
        return LegacySql.wrap(checked, legacy, trimLegacy);
    }

    private static Connection validated(Connection c) throws Exception {
        try (java.sql.Statement st = c.createStatement()) {
            st.setQueryTimeout(5);
            st.execute("SELECT 1");
        } catch (Exception e) {
            try { c.close(); } catch (Exception ignored) { Diag.ignored("Atiran:76", ignored); }
            throw e;
        }
        return c;
    }

    /** Technical exception → short Persian message. */
    public static String diagnose(Exception e) {
        if (e == null) return "خطای نامشخص اتصال";
        String m = String.valueOf(e.getMessage());
        String l = m.toLowerCase(java.util.Locale.US);
        if (l.contains("lock timeout"))
            return "پایگاه‌دادهٔ آتیران مشغول است؛ چند لحظه بعد دوباره تلاش کنید";
        if (l.contains("timeout") || l.contains("timed out")) return "سرور پاسخ نداد؛ اینترنت و آدرس سرور را بررسی کنید";
        if (l.contains("refused") || l.contains("unreachable") || l.contains("no route"))
            return "مسیر شبکه به سرور در دسترس نیست";
        if (l.contains("login failed") || l.contains("logon failed")) return "نام کاربری یا رمز دیتابیس اشتباه است";
        if (l.contains("cannot open database")) return "دیتابیس روی سرور پیدا نشد؛ نام دیتابیس را بررسی کنید";
        if (l.contains("unknown server host") || l.contains("unknown host") || l.contains("no such host")) return "آدرس سرور اشتباه است یا در شبکه یافت نشد";
        if (l.contains("invalid object name")) {
            String o = m.replaceAll("(?i).*invalid object name\\s*'?dbo\\.?\\.?", "").replace("'", "").trim();
            if (o.length() > 40) o = o.substring(0, 40);
            return "جدول «" + o + "» در دیتابیس پیدا نشد";
        }
        if (l.contains("invalid column name")) {
            String o = m.replaceAll("(?i).*invalid column name\\s*'?", "").replace("'", "").trim();
            if (o.length() > 40) o = o.substring(0, 40);
            return "ستون «" + o + "» در دیتابیس پیدا نشد";
        }
        if (l.contains("failed to connect") || l.contains("connect timed out") || l.contains("etimedout")) {
            return "اتصال به سرور در مهلت تعیین‌شده برقرار نشد؛ اتصال شبکه، آدرس و پورت سرور را بررسی کنید";
        }
        if (l.contains("socket closed") || l.contains("i/o error") || l.contains("connection reset")) {
            return "ارتباط با سرور قطع شد؛ اتصال شبکه را بررسی و دوباره تلاش کنید";
        }
        if (l.contains("incorrect syntax")) {
            return "ساختار این گزارش با دیتابیس سازگار نیست؛ به پشتیبانی اطلاع دهید";
        }
        if (l.contains("is not a recognized built-in function name")) {
            // Pre-2012 SQL Server parses TRY_CONVERT's type argument as a call, so the message
            // names «nvarchar»/«decimal» instead of the function. The compatibility wrapper
            // normally prevents reaching here; if it does, say what to check in plain Persian.
            return "نسخهٔ SQL Server سرور قدیمی‌تر از پشتیبانی این گزارش است؛ به پشتیبانی اطلاع دهید تا حالت سازگاری بررسی شود";
        }
        if (l.contains("aggregate function") || l.contains("group by") || l.contains("subquery")) {
            // SQL Server rejects this report's calculation shape. Keep the raw text out of the UI.
            return "این محاسبه روی این دیتابیس اجرا نشد؛ دوباره تلاش کنید یا به پشتیبانی اطلاع دهید";
        }
        if (m.length() > 220) m = m.substring(0, 220) + "…";
        return m.isEmpty() ? "خطای اتصال به دیتابیس" : m;
    }
}
