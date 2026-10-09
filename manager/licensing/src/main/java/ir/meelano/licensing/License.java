package ir.meelano.licensing;

import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Locale;

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
    /** Custom plan (seller-typed expiry date, 1–3650 days). */
    public static final char P_CUSTOM = 'C';

    public static boolean isPlan(char p) {
        return p == P_TRIAL || p == P_WEEKLY || p == P_MONTHLY || p == P_YEARLY || p == P_PERM
                || p == P_CUSTOM;
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
        if (p == P_CUSTOM) return "سفارشی";
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
     * Mint a license pack. days is used for trial (1–90) and custom (1–3650);
     * weekly/monthly/yearly use their fixed durations; permanent ignores days.
     */
    public static String generate(String device8, char plan, int days) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        if (!isPlan(plan)) plan = P_TRIAL;
        long iat = today();
        long exp;
        if (plan == P_PERM) exp = 99999;
        else if (plan == P_TRIAL) exp = iat + Math.max(1, Math.min(90, days));
        else if (plan == P_CUSTOM) exp = iat + Math.max(1, Math.min(3650, days));
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
            if (p.length < 7) continue;
            Req r = new Req();
            r.dev = normalize(p[1]);
            if (r.dev.length() != 8) continue;
            r.name = p[2].trim();
            r.family = p[3].trim();
            r.shop = p[4].trim();
            r.phone = p[5].trim();
            r.city = p[6].trim();
            return r;
        }
        return null;
    }

    // ---------------- encrypted DB connection card ----------------
    /**
     * Connection card: the seller's admin app encrypts the customer's SQL Server
     * profile (host/port/db/user/pass) into one shareable line; the client app
     * decrypts it on the customer's own phone. Device-bound: a card minted for
     * device A never opens on device B. Format:
     * {@code MILANO-DB1|<base64url IV+ciphertext, AES-128-CBC, key = SHA256(secret|db|dev)>}.
     * The customer never types (or sees) server addresses.
     */
    public static final String DB_PREFIX = "MILANO-DB1";

    public static final class DbProfile {
        public String host = "";
        public String port = "";
        public String db = "";
        public String user = "";
        public String pass = "";

        public boolean complete() {
            return !host.isEmpty() && !port.isEmpty() && !db.isEmpty() && !user.isEmpty();
        }
    }

    /** Mint a connection card for one device. Throws IllegalArgumentException on bad input. */
    public static String dbCard(String device8, DbProfile p) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        if (p == null || !p.complete()) throw new IllegalArgumentException("profile");
        if (!validPort(p.port)) throw new IllegalArgumentException("port");
        String payload = esc(dev) + "|" + esc(p.host) + "|" + esc(p.port) + "|"
                + esc(p.db) + "|" + esc(p.user) + "|" + esc(p.pass);
        try {
            byte[] key = sha256(secret() + "|db|" + dev);
            byte[] iv = randomBytes(16);
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, 0, 16, "AES"),
                    new javax.crypto.spec.IvParameterSpec(iv));
            byte[] ct = c.doFinal(payload.getBytes(Charset.forName("UTF-8")));
            byte[] both = new byte[16 + ct.length];
            System.arraycopy(iv, 0, both, 0, 16);
            System.arraycopy(ct, 0, both, 16, ct.length);
            return DB_PREFIX + "|" + java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(both);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("crypto");
        }
    }

    /**
     * Open a connection card on this device. Scans pasted text for the card line,
     * decrypts it, and returns the profile — or null when the card is missing,
     * corrupt, or minted for another device.
     */
    public static DbProfile parseDbCard(String text, String expectDevice8) {
        String want = normalize(expectDevice8);
        if (text == null || want.length() != 8) return null;
        String blob = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(DB_PREFIX + "|")) {
                blob = line.substring(DB_PREFIX.length() + 1).trim();
                break;
            }
        }
        if (blob == null || blob.isEmpty()) return null;
        try {
            byte[] both = java.util.Base64.getUrlDecoder().decode(blob);
            if (both.length < 33 || (both.length - 16) % 16 != 0) return null;
            byte[] key = sha256(secret() + "|db|" + want);
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, 0, 16, "AES"),
                    new javax.crypto.spec.IvParameterSpec(both, 0, 16));
            byte[] pt = c.doFinal(both, 16, both.length - 16);
            String[] f = unescSplit(new String(pt, Charset.forName("UTF-8")));
            if (f.length != 6 || !normalize(f[0]).equals(want)) return null;
            DbProfile p = new DbProfile();
            p.host = f[1];
            p.port = f[2];
            p.db = f[3];
            p.user = f[4];
            p.pass = f[5];
            if (!p.complete() || !validPort(p.port)) return null;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- fixed SQL login + mini connection line ----------------
    /**
     * Fixed SQL Server login shared by every customer (SQL Server 2014).
     * Per-customer connection data is therefore only host + port + db name,
     * carried by the short {@code MILANO-NET1} line below. The legacy AES
     * {@code MILANO-DB1} card keeps parsing for cards minted earlier.
     */
    public static final String SQL_USER = "AdminAn";
    public static final String SQL_PASS = "St@R2022$";

    public static final String NET_PREFIX = "MILANO-NET1";

    public static final class NetProfile {
        public String host = "";
        public String port = "";
        public String db = "";

        public boolean complete() {
            return !host.isEmpty() && !port.isEmpty() && !db.isEmpty();
        }

        /** Full profile with the fixed SQL login filled in. */
        public DbProfile withFixedLogin() {
            DbProfile p = new DbProfile();
            p.host = host;
            p.port = port;
            p.db = db;
            p.user = SQL_USER;
            p.pass = SQL_PASS;
            return p;
        }
    }

    /** Mint a short connection line for one device. Throws IllegalArgumentException on bad input. */
    public static String netLine(String device8, NetProfile p) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        if (p == null || !p.complete()) throw new IllegalArgumentException("profile");
        String host = p.host.trim();
        String port = p.port.trim();
        String db = p.db.trim();
        if (host.isEmpty() || db.isEmpty()) throw new IllegalArgumentException("profile");
        if (!validPort(port)) throw new IllegalArgumentException("port");
        String mac = sig("NET1|" + dev + "|" + host + "|" + port + "|" + db).substring(0, 8);
        return NET_PREFIX + "|" + dev + "|" + esc(host) + "|" + port + "|" + esc(db) + "|" + mac;
    }

    /**
     * Open a mini connection line on this device. Scans pasted text for the
     * line and returns the profile — or null when missing, corrupt, or minted
     * for another device.
     */
    public static NetProfile parseNetLine(String text, String expectDevice8) {
        String want = normalize(expectDevice8);
        if (text == null || want.length() != 8) return null;
        String found = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(NET_PREFIX + "|")) {
                found = line;
                break;
            }
        }
        if (found == null) return null;
        String[] f = unescSplit(found);
        if (f.length != 6 || !NET_PREFIX.equals(f[0])) return null;
        if (!normalize(f[1]).equals(want)) return null;
        NetProfile np = new NetProfile();
        np.host = f[2].trim();
        np.port = f[3].trim();
        np.db = f[4].trim();
        if (!np.complete() || !validPort(np.port)) return null;
        String mac = sig("NET1|" + want + "|" + np.host + "|" + np.port + "|" + np.db).substring(0, 8);
        if (!constantEq(mac, normalize(f[5]))) return null;
        return np;
    }

    /**
     * Either connection line (NET1 mini or legacy DB1 card) inside pasted text,
     * as a full DbProfile (NET1 carries the fixed SQL login). Null when none.
     */
    public static DbProfile parseAnyCard(String text, String expectDevice8) {
        NetProfile n = parseNetLine(text, expectDevice8);
        if (n != null) return n.withFixedLogin();
        return parseDbCard(text, expectDevice8);
    }

    private static boolean validPort(String port) {
        try {
            int p = Integer.parseInt(port.trim());
            return p >= 1 && p <= 65535;
        } catch (Exception e) {
            return false;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ");
    }

    private static String[] unescSplit(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                cur.append(n == 'p' ? '|' : n);
            } else if (c == '|') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // ---------------- usage report ----------------
    /**
     * Compact usage report the client attaches to every license request
     * (and can share on demand):
     * {@code MILANO-USE1|DEV8|totalMin|opens|lastDay}.
     * The admin app stores it per customer («last seen», total hours, opens).
     * Honest note: with no server there is no live «online» flag — the seller
     * always sees the freshest report the customer has sent (every request
     * and renewal refreshes it).
     */
    // ---------------- remote control (revoke / block / unblock / site sync) ----------------
    /**
     * Signed one-line commands, all device-bound and MAC'd with the shared
     * secret (same HMAC as packs, truncated to 8 chars). The seller sends them
     * by SMS (or QR); the client verifies before obeying anything.
     */
    public static final String REVOKE_PREFIX = "MILANO-REVOKE1";
    public static final String BLOCK_PREFIX = "MILANO-BLOCK1";
    public static final String UNBLOCK_PREFIX = "MILANO-UNBLOCK1";
    public static final String SITE_PREFIX = "MILANO-SITE1";

    private static String mac8(String s) {
        String m = sig(s);
        return m.length() >= 8 ? m.substring(0, 8) : m;
    }

    private static String findLine(String text, String prefix) {
        if (text == null) return null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith(prefix + "|")) return line;
        }
        return null;
    }

    /** Signed remote-revoke line for a device (seller → customer SMS). */
    public static String revokeLine(String device8) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        return REVOKE_PREFIX + "|" + dev + "|" + mac8("REVOKE1|" + dev);
    }

    public static boolean parseRevoke(String text, String expectDevice8) {
        String line = findLine(text, REVOKE_PREFIX);
        if (line == null) return false;
        String[] p = line.split("\\|", -1);
        if (p.length != 3) return false;
        String dev = normalize(p[1]);
        if (!dev.equals(normalize(expectDevice8))) return false;
        return constantEq(mac8("REVOKE1|" + dev), normalize(p[2]));
    }

    /** Signed unblock line (lifts a block; the license itself stays revoked). */
    public static String unblockLine(String device8) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        return UNBLOCK_PREFIX + "|" + dev + "|" + mac8("UNBLOCK1|" + dev);
    }

    public static boolean parseUnblock(String text, String expectDevice8) {
        String line = findLine(text, UNBLOCK_PREFIX);
        if (line == null) return false;
        String[] p = line.split("\\|", -1);
        if (p.length != 3) return false;
        String dev = normalize(p[1]);
        if (!dev.equals(normalize(expectDevice8))) return false;
        return constantEq(mac8("UNBLOCK1|" + dev), normalize(p[2]));
    }

    /** Signed block line with a human reason (shown on the customer's phone). */
    public static String blockLine(String device8, String reason) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        String r = reason == null ? "" : reason.trim().replace("\n", " ");
        if (r.isEmpty()) r = "تخلف از قوانین استفاده";
        if (r.length() > 80) r = r.substring(0, 80);
        return BLOCK_PREFIX + "|" + dev + "|" + esc(r) + "|" + mac8("BLOCK1|" + dev + "|" + r);
    }

    /** The block reason when the line is valid for this device, else null. */
    public static String parseBlock(String text, String expectDevice8) {
        String line = findLine(text, BLOCK_PREFIX);
        if (line == null) return null;
        String[] p = unescSplit(line);
        if (p.length != 4) return null;
        String dev = normalize(p[1]);
        if (!dev.equals(normalize(expectDevice8))) return null;
        if (!constantEq(mac8("BLOCK1|" + dev + "|" + p[2]), normalize(p[3]))) return null;
        return p[2];
    }

    /**
     * Customer → seller site report: the IP + DB the customer typed in step 2,
     * so the seller keeps a full customer record. Verified by MAC on arrival.
     */
    public static String siteLine(String device8, String host, String port, String db) {
        String dev = normalize(device8);
        if (dev.length() != 8) throw new IllegalArgumentException("device");
        String h = host == null ? "" : host.trim();
        String pt = port == null ? "" : port.trim();
        String d = db == null ? "" : db.trim();
        if (h.isEmpty() || pt.isEmpty() || d.isEmpty()) throw new IllegalArgumentException("site");
        try {
            int pn = Integer.parseInt(pt);
            if (pn < 1 || pn > 65535) throw new IllegalArgumentException("port");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port");
        }
        return SITE_PREFIX + "|" + dev + "|" + esc(h) + "|" + esc(pt) + "|" + esc(d)
                + "|" + mac8("SITE1|" + dev + "|" + h + "|" + pt + "|" + d);
    }

    /** The reported site when the line verifies, else null. */
    public static NetProfile parseSite(String text) {
        String line = findLine(text, SITE_PREFIX);
        if (line == null) return null;
        String[] p = unescSplit(line);
        if (p.length != 6) return null;
        String dev = normalize(p[1]);
        if (dev.length() != 8) return null;
        if (!constantEq(mac8("SITE1|" + dev + "|" + p[2] + "|" + p[3] + "|" + p[4]),
                normalize(p[5]))) return null;
        NetProfile np = new NetProfile();
        np.host = p[2];
        np.port = p[3];
        np.db = p[4];
        return np;
    }

    // ---------------- in-app update channel ----------------
    /**
     * Domain tag for the signed update descriptor (see {@code version.json}
     * on the release page): the app trusts a descriptor only when its MAC
     * verifies with the same license secret, binding versionCode to the
     * APK's SHA-256 so a descriptor from one release can never be mixed
     * with another release's APK.
     */
    public static final String UPDATE_PREFIX = "MILANO-UPDATE1";

    /** MAC binding a release (versionCode + APK SHA-256) to the license secret. */
    public static String updateMac(int versionCode, String sha256hex) {
        String h = sha256hex == null ? "" : sha256hex.trim().toLowerCase(Locale.US);
        return mac8("UPDATE1|" + versionCode + "|" + h);
    }

    /** True when the update descriptor's MAC is genuine. Never throws. */
    public static boolean updateVerify(int versionCode, String sha256hex, String mac) {
        try {
            if (versionCode <= 0 || sha256hex == null || mac == null) return false;
            if (!sha256hex.trim().matches("(?i)[0-9a-f]{64}")) return false;
            return constantEq(updateMac(versionCode, sha256hex), normalize(mac));
        } catch (Exception e) {
            return false;
        }
    }

    /** The device a SITE1 line belongs to ("" when the line is invalid). */
    public static String parseSiteDev(String text) {
        String line = findLine(text, SITE_PREFIX);
        if (line == null) return "";
        String[] p = unescSplit(line);
        if (p.length != 6) return "";
        NetProfile np = parseSite(text);
        if (np == null) return "";
        return normalize(p[1]);
    }

    public static final String USE_PREFIX = "MILANO-USE1";

    public static final class Use {
        public String dev = "";
        public long totalMin;
        public int opens;
        public long lastDay;
    }

    public static String useLine(String dev8, long totalMin, int opens, long lastDay) {
        String dev = normalize(dev8);
        if (dev.length() != 8) dev = "00000000";
        return USE_PREFIX + "|" + dev + "|" + Math.max(0, totalMin) + "|"
                + Math.max(0, opens) + "|" + Math.max(0, lastDay);
    }

    /** Find and parse the usage line inside pasted text; null when absent/invalid. */
    public static Use parseUse(String text) {
        if (text == null) return null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith(USE_PREFIX + "|")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 5) continue;
            Use u = new Use();
            u.dev = normalize(p[1]);
            if (u.dev.length() != 8) continue;
            try {
                u.totalMin = Long.parseLong(p[2].trim());
                u.opens = Integer.parseInt(p[3].trim());
                u.lastDay = Long.parseLong(p[4].trim());
            } catch (Exception e) {
                continue;
            }
            if (u.totalMin < 0 || u.opens < 0 || u.lastDay < 0) continue;
            return u;
        }
        return null;
    }

    /**
     * Full self-test: pack round-trip + tamper rejection, request round-trip,
     * usage round-trip, and connection-card round-trip incl. special chars,
     * device binding and port validation. The admin app runs it on launch.
     */
    public static boolean selfTest() {
        try {
            String dev = deviceCode("test-fingerprint");
            String p = generate(dev, P_MONTHLY, 0);
            Result r = parse(p);
            if (!r.ok || !r.dev.equals(dev) || r.plan != P_MONTHLY
                    || r.expDay != r.iatDay + 30) return false;
            if (parse(p.substring(0, 38) + (p.charAt(38) == '0' ? '1' : '0')).ok) return false;
            Req rq = parseRequest("hi\n"
                    + requestLine(dev, "نام", "فامیل", "ش|ا\\پ", "0912", "تهران") + "\nbye");
            if (rq == null || !rq.dev.equals(dev) || !"نام".equals(rq.name)
                    || !"ش/ا\\پ".equals(rq.shop)) return false;
            Use u = parseUse("x\n" + useLine(dev, 42, 7, today()) + "\ny");
            if (u == null || !u.dev.equals(dev) || u.totalMin != 42 || u.opens != 7
                    || u.lastDay != today()) return false;
            DbProfile prof = new DbProfile();
            prof.host = "h|o\\st";
            prof.port = "1433";
            prof.db = "d";
            prof.user = "u";
            prof.pass = "p|a\\ss";
            String card = dbCard(dev, prof);
            DbProfile back = parseDbCard("x\n" + card + "\ny", dev);
            if (back == null || !back.host.equals("h|o\\st")
                    || !back.pass.equals("p|a\\ss") || !"1433".equals(back.port)) return false;
            if (parseDbCard(card, "ZZZZZZZZ") != null) return false;
            if (parseDbCard(card + "X", dev) != null) return false;
            try {
                DbProfile bad = new DbProfile();
                bad.host = "h";
                bad.port = "99999";
                bad.db = "d";
                bad.user = "u";
                dbCard(dev, bad);
                return false;
            } catch (IllegalArgumentException expected) {
            }
            NetProfile np = new NetProfile();
            np.host = "192.168.1.10";
            np.port = "1433";
            np.db = "AtiranDb";
            String net = netLine(dev, np);
            DbProfile nb = parseAnyCard("x\n" + net + "\ny", dev);
            if (nb == null || !"192.168.1.10".equals(nb.host) || !"AtiranDb".equals(nb.db)
                    || !SQL_USER.equals(nb.user) || !SQL_PASS.equals(nb.pass)) return false;
            if (parseAnyCard(net, "ZZZZZZZZ") != null) return false;
            char last = net.charAt(net.length() - 1);
            String cut = net.substring(0, net.length() - 1) + (last == '0' ? '1' : '0');
            if (parseNetLine(cut, dev) != null) return false;
            try {
                NetProfile badN = new NetProfile();
                badN.host = "h";
                badN.port = "abc";
                badN.db = "d";
                netLine(dev, badN);
                return false;
            } catch (IllegalArgumentException expected) {
            }
            String pc = generate(dev, P_CUSTOM, 45);
            Result rc = parse(pc);
            if (!rc.ok || rc.plan != P_CUSTOM || rc.expDay != rc.iatDay + 45) return false;
            if (!parseRevoke("x\n" + revokeLine(dev) + "\ny", dev)) return false;
            if (parseRevoke(revokeLine(dev), "ZZZZZZZZ")) return false;
            String blk = blockLine(dev, "عدم تسویه | تست");
            if (!"عدم تسویه | تست".equals(parseBlock("x\n" + blk, dev))) return false;
            if (parseBlock(blk, "ZZZZZZZZ") != null) return false;
            if (!parseUnblock(unblockLine(dev), dev)) return false;
            NetProfile sp = parseSite("x\n" + siteLine(dev, "1.2.3.4", "1433", "AtiranDb") + "\ny");
            if (sp == null || !"1.2.3.4".equals(sp.host) || !"AtiranDb".equals(sp.db)) return false;
            if (!dev.equals(parseSiteDev(siteLine(dev, "1.2.3.4", "1433", "AtiranDb")))) return false;
            String uh = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
            String um = updateMac(17, uh);
            if (!updateVerify(17, uh, um)) return false;
            if (!updateVerify(17, uh.toUpperCase(Locale.US), um)) return false;
            if (updateVerify(18, uh, um)) return false;
            if (updateVerify(17, uh, um + "X")) return false;
            if (updateVerify(17, "xyz", um)) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
