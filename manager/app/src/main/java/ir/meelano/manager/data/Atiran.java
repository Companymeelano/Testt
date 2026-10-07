package ir.meelano.manager.data;

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
    public static String defaultUser() { return de(O_USER); }
    public static String defaultPass() { return de(O_PASS); }
    public static String defaultDb() { return de(O_DB); }

    /** Open a validated connection (TCP preflight + one automatic retry). */
    public static Connection open(String host, int port, String db, String user, String pass) throws Exception {
        Class.forName("net.sourceforge.jtds.jdbc.Driver");
        String url = "jdbc:jtds:sqlserver://" + host + ":" + port + "/" + db
                + ";loginTimeout=8;socketTimeout=30;appName=MEELANO-Manager7;";
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("charset", "UTF-8");
        p.setProperty("sendStringParametersAsUnicode", "true");
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
        } catch (Exception e) {
            throw new Exception(diagnose(e));
        }
        try {
            return validated(DriverManager.getConnection(url, p));
        } catch (Exception first) {
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
            try { c.close(); } catch (Exception ignored) { }
            throw e;
        }
        return c;
    }

    /** Technical exception → short Persian message. */
    public static String diagnose(Exception e) {
        if (e == null) return "خطای نامشخص اتصال";
        String m = String.valueOf(e.getMessage());
        String l = m.toLowerCase(java.util.Locale.US);
        if (l.contains("timeout") || l.contains("timed out")) return "سرور پاسخ نداد؛ اینترنت و آدرس سرور را بررسی کنید";
        if (l.contains("refused") || l.contains("unreachable") || l.contains("no route") || l.contains("network"))
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
        if (m.length() > 220) m = m.substring(0, 220) + "…";
        return m.isEmpty() ? "خطای اتصال به دیتابیس" : m;
    }
}
