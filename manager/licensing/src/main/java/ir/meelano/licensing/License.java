package ir.meelano.licensing;

import java.nio.charset.Charset;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Offline license codec shared by the client app and the seller's admin app.
 * Pure Java (no Android classes) so both modules use the exact same math.
 *
 * Pack layout (39 chars, shown in groups of 4):
 *   M1 + DEVICE(8) + PLAN(1) + EXP(5) + IAT(5) + RND(2) + SIG(16)
 * DEVICE = Crockford-Base32 device code • PLAN = T/W/M/Y/P • EXP/IAT = UTC day
 * numbers (permanent = 99999) • SIG = first 80 bits of HMAC-SHA256(secret,…).
 *
 * SECURITY NOTE FOR THE SELLER: change SECRET_* below BEFORE your first real
 * sale, then rebuild BOTH apps. Anyone with the secret (or your admin APK,
 * which contains it) can mint licenses — guard the admin APK like cash.
 */
public final class License {
    private License() { }

    // ---------------- shared secret (seller: change before production!) ----------------
    private static final String S1 = "Mln7#9f2K-q8ZmT-4VxLp3$";
    private static final String S2 = "wQ6s-Dn8@bR1xH5!kF0$mZ";
    private static final String S3 = "7&cN9pL2*eT4Ks8@Vb6#Qw1";

    private static String secret() {
        return S1 + S2 + S3;
    }

    // ---------------- time ----------------
    public static final long DAY_MS = 86400000L;

    /** UTC day number (timezone-proof expiry math). */
    public static long today() {
        return System.currentTimeMillis() / DAY_MS;
    }

    // ---------------- plans ----------------
    public static final char P_TRIAL = 'T';
    public static final char P_WEEKLY = 'W';
    public static final char P_MONTHLY = 'M';
    public static final char P_YEARLY = 'Y';
    public static final char P_PERM = 'P';

    public static boolean isPlan(char p) {
        return p == P_TRIAL || p == P_WEEKLY || p == P_MONTHLY || p == P_YEARLY || p == P_PERM;
    }

    /** Fixed duration in days (trial/permanent are decided by the caller). */
    public static int planDays(char p) {
        if (p == P_WEEKLY) return 7;
        if (p == P_MONTHLY) return 30;
        if (p == P_YEARLY) return 365;
        return 0;
    }

    public static String planFa(char p) {
        if (p == P_TRIAL) return "آزمایشی";
        if (p == P_WEEKLY) return "هفتگی";
        if (p == P_MONTHLY) return "ماهانه";
        if (p == P_YEARLY) return "سالانه";
        if (p == P_PERM) return "دائمی";
        return "نامشخص";
    }

    // ---------------- codec ----------------
    private static final String CROCK = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int PACK_LEN = 39;

    /** Normalize user input: uppercase, strip separators, Crockford typo fixes (I/L→1, O→0). */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        String u = s.toUpperCase(java.util.Locale.US);
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c == 'I' || c == 'L') c = '1';
            else if (c == 'O') c = '0';
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')) b.append(c);
        }
        return b.toString();
    }

    /** 8-char device code from a raw device fingerprint. */
    public static String deviceCode(String fingerprint) {
        try {
            byte[] h = sha256(fingerprint == null ? "" : fingerprint);
            return crock(h, 8);
        } catch (Exception e) {
            return "00000000";
        }
    }

    /**
     * Mint a license pack. days is used for trial (1–90); weekly/monthly/yearly
     * use their fixed durations; permanent ignores days.
     */
    public static String generate(String device8, char plan, int days) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        if (!isPlan(plan)) plan = P_TRIAL;
        long iat = today();
        long exp;
        if (plan == P_PERM) exp = 99999;
        else if (plan == P_TRIAL) exp = iat + Math.max(1, Math.min(90, days));
        else exp = iat + planDays(plan);
        String rnd = crock(randomBytes(2), 2);
        String payload = "M1" + dev + plan + pad5(exp) + pad5(iat) + rnd;
        return payload + sig(payload);
    }

    /** Pretty form: XXXX-XXXX-… */
    public static String display(String pack) {
        String n = normalize(pack);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(n.charAt(i));
        }
        return b.toString();
    }

    /** Parse + verify the signature (device + date checks are the caller's job). */
    public static Result parse(String pack) {
        Result r = new Result();
        String n = normalize(pack);
        if (n.length() != PACK_LEN || !n.startsWith("M1")) {
            r.errFa = "فرمت کد فعال‌سازی درست نیست";
            return r;
        }
        String payload = n.substring(0, 23);
        String sig = n.substring(23);
        if (!constantEq(sig(payload), sig)) {
            r.errFa = "کد فعال‌سازی معتبر نیست";
            return r;
        }
        try {
            r.dev = payload.substring(2, 10);
            r.plan = payload.charAt(10);
            r.expDay = Long.parseLong(payload.substring(11, 16));
            r.iatDay = Long.parseLong(payload.substring(16, 21));
        } catch (Exception e) {
            r.errFa = "فرمت کد فعال‌سازی درست نیست";
            return r;
        }
        if (!isPlan(r.plan)) {
            r.errFa = "نوع لایسنس نامعتبر است";
            return r;
        }
        r.ok = true;
        r.pack = n;
        return r;
    }

    public static final class Result {
        public boolean ok;
        public String errFa = "";
        public String pack = "";
        public String dev = "";
        public char plan;
        public long expDay;
        public long iatDay;
    }

    // ---------------- internals ----------------
    private static String pad5(long v) {
        String s = String.valueOf(Math.max(0, Math.min(99999, v)));
        while (s.length() < 5) s = "0" + s;
        return s;
    }

    private static String sig(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(Charset.forName("UTF-8")), "HmacSHA256"));
            return crock(mac.doFinal(payload.getBytes(Charset.forName("UTF-8"))), 16);
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    private static boolean constantEq(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    /** First n Crockford chars (5 bits each) of a hash. */
    private static String crock(byte[] h, int n) {
        StringBuilder b = new StringBuilder();
        int bits = 0, acc = 0, pos = 0;
        while (b.length() < n && pos < h.length) {
            acc = (acc << 8) | (h[pos++] & 0xFF);
            bits += 8;
            while (bits >= 5 && b.length() < n) {
                bits -= 5;
                b.append(CROCK.charAt((acc >> bits) & 31));
            }
        }
        while (b.length() < n) b.append('0');
        return b.toString();
    }

    private static byte[] sha256(String s) throws Exception {
        java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
        return d.digest(s.getBytes(Charset.forName("UTF-8")));
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        try {
            new SecureRandom().nextBytes(b);
        } catch (Exception ignored) {
            for (int i = 0; i < n; i++) b[i] = (byte) (System.nanoTime() + i * 31);
        }
        return b;
    }

    // ---------------- customer request ----------------
    /**
     * Request line the client app builds for the customer to send the seller:
     * {@code MILANO-REQ1|DEV8|name|family|shop|phone|city}.
     * One line, pipe-separated, embedded in a human-readable message; the admin
     * app scans pasted text for it. Both apps share this exact format.
     */
    public static final String REQ_PREFIX = "MILANO-REQ1";

    public static final class Req {
        public String dev = "";
        public String name = "";
        public String family = "";
        public String shop = "";
        public String phone = "";
        public String city = "";
    }

    public static String requestLine(String dev, String name, String family,
                                     String shop, String phone, String city) {
        return REQ_PREFIX + "|" + clean(dev) + "|" + clean(name) + "|"
                + clean(family) + "|" + clean(shop) + "|" + clean(phone)
                + "|" + clean(city);
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.trim().replace('|', '/').replace('\n', ' ');
    }

    /**
     * Find and parse the request line inside pasted text (extra chat text
     * around it is fine). Returns null when no valid line is found.
     */
    public static Req parseRequest(String text) {
        if (text == null) return null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith(REQ_PREFIX + "|")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 7) return null;
            Req r = new Req();
            r.dev = normalize(p[1]);
            if (r.dev.length() != 8) return null;
            r.name = p[2].trim();
            r.family = p[3].trim();
            r.shop = p[4].trim();
            r.phone = p[5].trim();
            r.city = p[6].trim();
            return r;
        }
        return null;
    }

    /** Self-test (wired to nothing; run from a unit check when paranoid). */
    public static boolean selfTest() {
        try {
            String dev = deviceCode("test-fingerprint");
            String p = generate(dev, P_MONTHLY, 0);
            Result r = parse(p);
            return r.ok && r.dev.equals(dev) && r.plan == P_MONTHLY && r.expDay == r.iatDay + 30
                    && !parse(p.substring(0, 38) + (p.charAt(38) == '0' ? '1' : '0')).ok;
        } catch (Exception e) {
            return false;
        }
    }
}
