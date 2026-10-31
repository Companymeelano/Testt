package ir.meelano.manager.data;

import ir.meelano.manager.core.Diag;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * Direct jTDS connection to the Atiran SQL Server.
 * Defaults match the previous app; every value can be overridden in Settings.
 */
public final class Atiran {
    private Atiran() { }

    private static final int KEY = 91;
    private static final int[] O_HOST = {104, 108, 117, 106, 111, 104, 117, 106, 111, 108, 117, 106, 98};
    private static final int[] O_USER = {26, 63, 54, 50, 53, 26, 53};
    private static final int[] O_PASS = {8, 47, 27, 9, 105, 107, 105, 105, 127};
    private static final int[] O_DB = {26, 47, 50, 41, 58, 53, 105};
    public static final int DEFAULT_PORT = 1433;

    private static String de(int[] d) {
        char[] o = new char[d.length];
        for (int i = 0; i < d.length; i++) o[i] = (char) (d[i] ^ KEY);
        return new String(o);
    }

    public static String defaultHost() { return de(O_HOST); }
    /** One SQL login for every database (the same account the smart backup uses). */
    public static String defaultUser() { return "AdminAn"; }
    public static String defaultPass() { return "St@R2022$"; }
    /** Database name on the Atiran server. */
    public static String defaultDb() { return "sac"; }

    /** Open a validated connection (TCP preflight + one automatic retry). */
    public static Connection open(String host, int port, String db, String user, String pass) throws Exception {
        return open(host, port, db, user, pass, 3000, 8, true);
    }

    /**
     * Same, with tunable speed: the smart link uses a fast profile
     * (no retry — failover to the other profile IS the retry).
     */
    public static Connection open(String host, int port, String db, String user, String pass,
                                  int tcpMs, int loginSec, boolean retry) throws Exception {
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
            return validated(DriverManager.getConnection(url, p));
        } catch (Exception first) {
            // A wrong password will not succeed on retry; do not hammer the login.
            String fm = String.valueOf(first.getMessage()).toLowerCase(java.util.Locale.US);
            if (!retry || fm.contains("login failed") || fm.contains("logon failed")) {
                throw new Exception(diagnose(first));
            }
            Thread.sleep(700);
            try {
                return validated(DriverManager.getConnection(url, p));
            } catch (Exception second) {
                throw new Exception(diagnose(second));
            }
        }
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
        // TLS first: jTDS reports a failed encrypted handshake as "Network error ... SSL handshake aborted".
        // That is not a routing problem, so it must not fall into the network branch below.
        if (l.contains("ssl") || l.contains("tls") || l.contains("handshake") || l.contains("secure connection"))
            return "اتصال رمزنگاری‌شده (TLS) با سرور برقرار نشد؛ نسخه TLS سرور با درایور برنامه سازگار نیست";
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
        if (l.contains("aggregate function") || l.contains("group by") || l.contains("subquery")) {
            // SQL Server rejects this report's calculation shape. Keep the raw text out of the UI.
            return "این محاسبه روی این دیتابیس اجرا نشد؛ دوباره تلاش کنید یا به پشتیبانی اطلاع دهید";
        }
        if (m.length() > 220) m = m.substring(0, 220) + "…";
        return m.isEmpty() ? "خطای اتصال به دیتابیس" : m;
    }
}
