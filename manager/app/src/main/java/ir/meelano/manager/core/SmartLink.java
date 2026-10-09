package ir.meelano.manager.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import ir.meelano.manager.data.Atiran;
import ir.meelano.manager.data.Meta;
import ir.meelano.manager.data.Settings;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Smart dual-path link to the Atiran server: an INSIDE profile (shop Wi-Fi /
 * cable — the server's LAN address) and an OUTSIDE profile (mobile data /
 * public address). On every connect the engine detects the phone's transport
 * (Wi-Fi, mobile data, cable/USB, …), tries the matching profile first with
 * a fast timeout, and silently fails over to the other one — on ANY
 * transport, including USB sharing and VPNs, because the final word is a
 * real TCP + SQL probe, not a guess.
 *
 * When nothing works, {@link #diagnose} pinpoints the broken stage per
 * profile (address → phone network → TCP port → SQL login → read) with a
 * Persian explanation AND the fix, for the user and for the seller.
 */
public final class SmartLink {
    private SmartLink() { }

    public static final String LAN = "lan";
    public static final String WAN = "wan";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ================= address helpers =================

    /**
     * True = private/LAN address, false = public, null = cannot tell
     * (a dotted hostname — ordered by the transport hint instead).
     */
    public static Boolean isPrivate(String host) {
        try {
            String h = clean(host).toLowerCase(Locale.US);
            if (h.isEmpty()) return null;
            if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
            if (h.equals("::1") || h.equals("localhost")) return true;
            if (h.endsWith(".local") || h.indexOf('.') < 0) return true;
            String[] p = h.split("\\.");
            if (p.length == 4) {
                int[] o = new int[4];
                for (int i = 0; i < 4; i++) {
                    o[i] = Integer.parseInt(p[i]);
                    if (o[i] < 0 || o[i] > 255) return null;
                }
                if (o[0] == 10) return true;
                if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;
                if (o[0] == 192 && o[1] == 168) return true;
                if (o[0] == 127) return true;
                if (o[0] == 0) return null;
                return false;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Strip scheme / path / port clutter for classification. */
    private static String clean(String host) {
        if (host == null) return "";
        String h = host.trim();
        int scheme = h.indexOf("://");
        if (scheme >= 0) h = h.substring(scheme + 3);
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        // Trailing :port (but not IPv6).
        if (h.indexOf(':') == h.lastIndexOf(':') && h.lastIndexOf(':') > h.lastIndexOf(']'))
            h = h.substring(0, h.lastIndexOf(':'));
        return h.trim();
    }

    /** Mask everything but the last segment (safe to display anywhere). */
    public static String maskHost(String host) {
        String v = clean(host);
        if (v.isEmpty()) return "";
        String[] parts = v.split("\\.");
        if (parts.length > 1) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) b.append('.');
                b.append("•••");
            }
            return b.append('.').append(parts[parts.length - 1]).toString();
        }
        if (v.length() <= 3) return "•••";
        return "•••" + v.substring(v.length() - 2);
    }

    public static String kindFa(String kind) {
        return WAN.equals(kind) ? "خارج شبکه (اینترنت)" : "داخل شبکه (فروشگاه)";
    }

    public static String kindShort(String kind) {
        return WAN.equals(kind) ? "خارج شبکه" : "داخل شبکه";
    }

    // ================= candidates =================

    public static final class Cand {
        public final String kind;
        public final String host;

        Cand(String kind, String host) {
            this.kind = kind;
            this.host = host;
        }
    }

    /** Saved profiles in smart try-order (mode + transport + last-working). */
    public static List<Cand> order(Context c, Settings s) {
        List<Cand> out = allSaved(s);
        String mode = s.linkMode();
        if (LAN.equals(mode) || WAN.equals(mode)) {
            List<Cand> f = new ArrayList<>();
            for (Cand k : out) if (k.kind.equals(mode)) f.add(k);
            return f;
        }
        String t = Net.transport(c);
        final boolean lanFirst = !"cell".equals(t);
        out.sort((a, b) -> {
            String last = s.linkLast();
            if (!last.isEmpty() && !a.kind.equals(b.kind)) {
                if (a.kind.equals(last)) return -1;
                if (b.kind.equals(last)) return 1;
            }
            if (!a.kind.equals(b.kind))
                return (LAN.equals(a.kind) == lanFirst) ? -1 : 1;
            return 0;
        });
        return out;
    }

    /** Every saved profile + legacy/default fallback (for diagnosis). */
    public static List<Cand> allSaved(Settings s) {
        try {
            s.migrateLegacyHost();
        } catch (Exception ignored) { }
        List<Cand> out = new ArrayList<>();
        if (!s.lan().isEmpty()) out.add(new Cand(LAN, s.lan()));
        if (!s.wan().isEmpty() && !sameHost(s.wan(), s.lan())) out.add(new Cand(WAN, s.wan()));
        if (out.isEmpty()) {
            String fb = s.host().isEmpty() ? Atiran.defaultHost() : s.host();
            if (fb != null && !fb.trim().isEmpty()) {
                Boolean priv = isPrivate(fb);
                out.add(new Cand(priv != null && !priv ? WAN : LAN, fb.trim()));
            }
        }
        return out;
    }

    private static boolean sameHost(String a, String b) {
        return a != null && b != null && clean(a).equalsIgnoreCase(clean(b));
    }

    // ================= connect =================

    /**
     * Open a validated connection through the smart order. Remembers the
     * winning profile. Throws a short Persian verdict when all fail.
     */
    public static Connection open(Context c, Settings s) throws Exception {
        List<Cand> cands = order(c, s);
        if (cands.isEmpty()) {
            if (!LAN.equals(s.linkMode()) && !WAN.equals(s.linkMode()))
                throw new Exception("آدرس سرور ثبت نشده؛ از فروشنده کارت اتصال بگیرید");
            throw new Exception("آدرس «" + kindShort(s.linkMode())
                    + "» ثبت نشده؛ حالت را روی «خودکار» بگذارید یا آدرس را کامل کنید");
        }
        List<String> fails = new ArrayList<>();
        for (Cand k : cands) {
            try {
                Connection conn = Atiran.open(k.host, s.effPort(), s.effDb(),
                        s.effUser(), s.effPass(), 1500, 5, false);
                try {
                    s.setLinkLast(k.kind);
                } catch (Exception ignored) { }
                return conn;
            } catch (Exception e) {
                fails.add(kindShort(k.kind) + ": " + Atiran.diagnose(e));
            }
        }
        StringBuilder v = new StringBuilder();
        for (int i = 0; i < fails.size(); i++) {
            if (i > 0) v.append(" • ");
            v.append(fails.get(i));
        }
        throw new Exception(v.toString());
    }

    /** Raw TCP probe: null = port is open, else the failure. Never throws. */
    public static Exception probeTcp(String host, int port, int ms) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host.trim(), port), Math.max(300, ms));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    /** Full login + count of core Atiran tables (activation tests). */
    public static int probeTables(String host, int port, String db, String user, String pass) throws Exception {
        try (Connection c = Atiran.open(host, port, db, user, pass, 3000, 6, false)) {
            Meta m = new Meta(c);
            int n = 0;
            for (String t : new String[]{"sailfact", "buyfact", "dar", "getchk",
                    "putchk", "CUSTOMERS", "inventory", "visitors"})
                if (m.table(t)) n++;
            return n;
        }
    }

    // ================= diagnostics =================

    public static final class Stage {
        public final String name;
        public final boolean ok;
        /** true = not attempted (an earlier stage blocked it). */
        public final boolean skipped;
        public final String detail;
        public final String fix;

        Stage(String name, boolean ok, boolean skipped, String detail, String fix) {
            this.name = name;
            this.ok = ok;
            this.skipped = skipped;
            this.detail = detail == null ? "" : detail;
            this.fix = fix == null ? "" : fix;
        }
    }

    public static final class Prof {
        public final String kind;
        public final String host;
        public final List<Stage> stages = new ArrayList<>();
        public boolean ok;

        Prof(String kind, String host) {
            this.kind = kind;
            this.host = host;
        }
    }

    public static final class Report {
        public String transportFa = "";
        public boolean vpnOn;
        public String portDb = "";
        public final List<Prof> profs = new ArrayList<>();
        public boolean anyOk;
        public String activeKind = "";
        /** Short verdict for toasts. */
        public String verdict = "";
        /** Seller-ready full text (contains REAL addresses — clipboard only). */
        public String copyText = "";
    }

    /** Callback on the main thread. */
    public interface Cb {
        void done(Report r);
    }

    /** Deep troubleshoot of saved profiles (or explicit values when given). */
    public static void diagnose(Context ctx, Settings s, boolean vpnOn, Cb cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Report r = build(app, s, vpnOn, null);
            MAIN.post(() -> {
                try {
                    cb.done(r);
                } catch (Exception ignored) { }
            });
        }).start();
    }

    /** Explicit values (activation screen): lan/wan may each be empty. */
    public static void diagnoseExplicit(Context ctx, String lan, String wan, int port, String db,
                                        String user, String pass, boolean vpnOn, Cb cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Exp e = new Exp();
            e.lan = lan == null ? "" : lan.trim();
            e.wan = wan == null ? "" : wan.trim();
            e.port = port;
            e.db = db == null ? "" : db.trim();
            e.user = user;
            e.pass = pass;
            Report r = build(app, null, vpnOn, e);
            MAIN.post(() -> {
                try {
                    cb.done(r);
                } catch (Exception ignored) { }
            });
        }).start();
    }

    private static final class Exp {
        String lan = "", wan = "";
        int port = 1433;
        String db = "", user = "", pass = "";
    }

    private static Report build(Context c, Settings s, boolean vpnOn, Exp e) {
        Report r = new Report();
        r.transportFa = Net.transportFa(c);
        r.vpnOn = vpnOn;
        List<Cand> cands = new ArrayList<>();
        int port;
        String db, user, pass, mode = "auto";
        if (e != null) {
            if (!e.lan.isEmpty()) cands.add(new Cand(LAN, e.lan));
            if (!e.wan.isEmpty() && !sameHost(e.wan, e.lan)) cands.add(new Cand(WAN, e.wan));
            port = e.port;
            db = e.db;
            user = e.user;
            pass = e.pass;
        } else {
            cands = allSaved(s);
            port = s.effPort();
            db = s.effDb();
            user = s.effUser();
            pass = s.effPass();
            mode = s.linkMode();
        }
        r.portDb = "پورت " + Money.fa(String.valueOf(port)) + " • دیتابیس " + (db.isEmpty() ? "—" : db);
        boolean online = Net.online(c);
        for (Cand k : cands) r.profs.add(testProfile(c, k, port, db, user, pass, online));
        for (Prof p : r.profs) if (p.ok) r.anyOk = true;
        if (r.anyOk) {
            for (Prof p : r.profs)
                if (p.ok) {
                    r.activeKind = p.kind;
                    break;
                }
            r.verdict = "✓ مسیر فعال: " + kindShort(r.activeKind) + " — همه‌چیز سالم است";
        } else if (cands.isEmpty()) {
            r.verdict = "هیچ آدرسی ثبت نشده؛ از فروشنده کارت اتصال بگیرید یا در فعال‌سازی » مرحله ۲ وارد کنید";
        } else if (!online) {
            r.verdict = "شبکه گوشی قطع است؛ وای‌فای یا دیتای موبایل را روشن کنید";
        } else {
            StringBuilder v = new StringBuilder();
            for (Prof p : r.profs) {
                if (v.length() > 0) v.append(" • ");
                v.append(kindShort(p.kind)).append(": ").append(lastFail(p));
            }
            r.verdict = v.toString();
        }
        if (!"auto".equals(mode))
            r.verdict += " (حالت: فقط " + kindShort(mode) + ")";
        r.copyText = copyFa(r, port, db);
        return r;
    }

    private static String lastFail(Prof p) {
        for (int i = p.stages.size() - 1; i >= 0; i--) {
            Stage st = p.stages.get(i);
            if (!st.skipped && !st.ok && !st.detail.isEmpty()) return st.detail;
        }
        return "ناموفق";
    }

    private static Prof testProfile(Context c, Cand k, int port, String db,
                                    String user, String pass, boolean online) {
        Prof p = new Prof(k.kind, k.host);
        p.stages.add(new Stage("ثبت آدرس", true, false, maskHost(k.host), ""));
        if (!online) {
            p.stages.add(new Stage("شبکه گوشی", false, false,
                    "اینترنت/وای‌فای گوشی قطع است",
                    "وای‌فای یا دیتای موبایل را روشن کنید و دوباره تلاش کنید"));
            p.stages.add(skip("پورت شبکه"));
            p.stages.add(skip("ورود به SQL"));
            p.stages.add(skip("خواندن دیتابیس"));
            p.ok = false;
            return p;
        }
        p.stages.add(new Stage("شبکه گوشی", true, false, "متصل: " + Net.transportFa(c), ""));
        // ---- TCP ----
        Exception tcp = probeTcp(k.host, port, 3000);
        if (tcp != null) {
            TcpInfo ti = classifyTcp(tcp, k.kind, port);
            p.stages.add(new Stage("پورت شبکه", false, false, ti.detail, ti.fix));
            p.stages.add(skip("ورود به SQL"));
            p.stages.add(skip("خواندن دیتابیس"));
            p.ok = false;
            return p;
        }
        p.stages.add(new Stage("پورت شبکه", true, false,
                "پورت " + Money.fa(String.valueOf(port)) + " باز و پاسخ‌گوست", ""));
        // ---- SQL login + read ----
        Connection conn = null;
        try {
            conn = Atiran.open(k.host, port, db, user, pass, 2000, 6, false);
        } catch (Exception e) {
            LoginInfo li = classifyLogin(e, db);
            p.stages.add(new Stage("ورود به SQL", false, false, li.detail, li.fix));
            p.stages.add(skip("خواندن دیتابیس"));
            p.ok = false;
            return p;
        }
        p.stages.add(new Stage("ورود به SQL", true, false, "نام کاربری و رمز پذیرفته شد", ""));
        try {
            try (java.sql.Statement st = conn.createStatement()) {
                st.setQueryTimeout(8);
                st.execute("SELECT 1");
            }
            p.stages.add(new Stage("خواندن دیتابیس", true, false,
                    "دیتابیس «" + db + "» خوانده شد", ""));
            p.ok = true;
        } catch (Exception e) {
            p.stages.add(new Stage("خواندن دیتابیس", false, false,
                    Atiran.diagnose(e), "جزئیات را با «کپی گزارش» برای فروشنده بفرستید"));
            p.ok = false;
        } finally {
            try {
                conn.close();
            } catch (Exception ignored) { }
        }
        return p;
    }

    private static Stage skip(String name) {
        return new Stage(name, false, true, "—", "");
    }

    private static final class TcpInfo {
        String detail = "", fix = "";
    }

    private static TcpInfo classifyTcp(Exception e, String kind, int port) {
        TcpInfo t = new TcpInfo();
        String l = String.valueOf(e.getMessage()).toLowerCase(Locale.US);
        String pFa = Money.fa(String.valueOf(port));
        boolean lan = LAN.equals(kind);
        if (l.contains("unknownhost") || l.contains("unknown host") || l.contains("no such host")) {
            t.detail = "این آدرس در شبکه یافت نشد";
            t.fix = lan ? "آی‌پی داخل فروشگاه را از فروشنده بگیرید و در فعال‌سازی » مرحله ۲ اصلاح کنید"
                    : "آدرس اینترنتی سرور را بررسی کنید؛ اگر عوض شده، آدرس تازه را از فروشنده بگیرید";
        } else if (l.contains("timed out") || l.contains("timeout")) {
            t.detail = "سرور پاسخ نداد (تایم‌اوت)";
            t.fix = lan ? "۱) به وای‌فای فروشگاه وصل شوید ۲) کامپیوتر سرور روشن باشد ۳) فایروال ویندوز پورت " + pFa + " را نبسته باشد"
                    : "۱) مودم و سرور فروشگاه روشن باشند ۲) اینترنت سرور وصل باشد ۳) آدرس عمومی عوض نشده باشد";
        } else if (l.contains("refused")) {
            t.detail = "سرور هست ولی SQL روی این پورت گوش نمی‌دهد";
            t.fix = "روی کامپیوتر سرور: سرویس SQL Server روشن باشد، پروتکل TCP فعال و پورت " + pFa + " باز باشد (SQL Configuration Manager)";
        } else if (l.contains("unreachable") || l.contains("no route") || l.contains("network")) {
            t.detail = "مسیری به این آدرس وجود ندارد";
            t.fix = lan ? "گوشی و سرور باید در یک شبکه باشند — به همان وای‌فای فروشگاه وصل شوید"
                    : "فیلترشکن را خاموش کنید، یا در تنظیمات «اتصال مستقیم» را روشن کنید";
        } else {
            t.detail = Atiran.diagnose(e);
            t.fix = "جزئیات را با «کپی گزارش» برای فروشنده بفرستید";
        }
        return t;
    }

    private static final class LoginInfo {
        String detail = "", fix = "";
    }

    private static LoginInfo classifyLogin(Exception e, String db) {
        LoginInfo t = new LoginInfo();
        String m = String.valueOf(e.getMessage());
        String l = m.toLowerCase(Locale.US);
        if (l.contains("login failed") || l.contains("logon failed")) {
            t.detail = "نام کاربری یا رمز دیتابیس اشتباه است";
            t.fix = "کارت اتصال تازه از فروشنده بگیرید و دوباره ثبت کنید";
        } else if (l.contains("cannot open database")) {
            t.detail = "دیتابیس «" + db + "» روی سرور نیست";
            t.fix = "نام دیتابیس را در فعال‌سازی » مرحله ۲ اصلاح کنید";
        } else if (l.contains("timeout") || l.contains("timed out")) {
            t.detail = "ارتباط هنگام ورود قطع شد";
            t.fix = "دوباره تلاش کنید؛ اگر تکرار شد اینترنت/وای‌فای را بررسی کنید";
        } else {
            t.detail = Atiran.diagnose(e);
            t.fix = "جزئیات را با «کپی گزارش» برای فروشنده بفرستید";
        }
        return t;
    }

    private static String copyFa(Report r, int port, String db) {
        StringBuilder b = new StringBuilder();
        b.append("گزارش اتصال میلانو\n");
        b.append("شبکه گوشی: ").append(r.transportFa);
        if (r.vpnOn) b.append(" + فیلترشکن");
        b.append("\nپورت: ").append(port).append(" • دیتابیس: ").append(db).append("\n");
        for (Prof p : r.profs) {
            b.append("— ").append(kindShort(p.kind)).append(": ").append(p.host);
            b.append(p.ok ? " ✓\n" : " ✕\n");
            for (Stage st : p.stages) {
                if (st.skipped) continue;
                b.append("  ").append(st.ok ? "✓" : "✕").append(" ").append(st.name);
                if (!st.detail.isEmpty()) b.append(": ").append(st.detail);
                b.append("\n");
                if (!st.ok && !st.fix.isEmpty()) b.append("    راه‌حل: ").append(st.fix).append("\n");
            }
        }
        b.append("نتیجه: ").append(r.verdict);
        return b.toString();
    }
}
