package ir.meelano.manager.core;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN verifier hashing for the app-lock PIN and the per-user app PINs.
 *
 * <p>v2 format: {@code "v2:" + b64(salt) + ":" + b64(PBKDF2-HmacSHA256(pin, salt, 12000, 32))}.
 * Each PIN gets a fresh random salt and 12k PBKDF2 iterations, so an extracted
 * verifier cannot be cracked with a precomputed table and each guess is expensive.</p>
 *
 * <p>Legacy format: {@code hex(SHA-256(staticPrefix + pin))} — one iteration with a
 * fixed prefix. It is still accepted on verify so existing PINs keep working, and it
 * is upgraded in place to v2 on the next successful unlock. All comparisons are
 * constant-time; hashing failures fail closed (nothing derived from the PIN is ever
 * stored or echoed).</p>
 */
public final class PinHash {
    private PinHash() { }

    private static final String V2 = "v2:";
    private static final int ITER = 12000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;

    /**
     * Hashes a PIN into the v2 verifier string. Returns "" when the platform cannot
     * hash (fail closed: the caller must not store a PIN it cannot verify).
     */
    public static String hash(String pin) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] dk = pbkdf2(pin, salt, ITER);
            return V2 + Base64.encodeToString(salt, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(dk, Base64.NO_WRAP);
        } catch (Exception e) {
            Diag.ignored("PinHash:hash", e);
            return "";
        }
    }

    /**
     * Verifies a PIN against a v2 or legacy verifier. {@code legacyPrefix} must be the
     * exact prefix the legacy verifier was written with (it namespaces each PIN store).
     */
    public static boolean verify(String pin, String stored, String legacyPrefix) {
        try {
            if (pin == null || stored == null || stored.isEmpty()) return false;
            if (stored.startsWith(V2)) {
                String[] parts = stored.substring(V2.length()).split(":", 2);
                if (parts.length != 2) return false;
                byte[] salt = Base64.decode(parts[0], Base64.NO_WRAP);
                byte[] want = Base64.decode(parts[1], Base64.NO_WRAP);
                byte[] got = pbkdf2(pin, salt, ITER);
                return MessageDigest.isEqual(want, got);
            }
            byte[] want = hex(stored);
            byte[] got = legacyPrefix == null ? null : legacyHash(legacyPrefix, pin);
            return want != null && got != null && MessageDigest.isEqual(want, got);
        } catch (Exception e) {
            Diag.ignored("PinHash:verify", e);
            return false;
        }
    }

    /** True when the stored verifier is legacy (upgrade it after a successful {@link #verify}). */
    public static boolean legacy(String stored) {
        return stored != null && !stored.isEmpty() && !stored.startsWith(V2);
    }

    private static byte[] pbkdf2(String pin, byte[] salt, int iter) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iter, KEY_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] legacyHash(String prefix, String pin) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((prefix + pin).getBytes("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hex(String s) {
        int n = s.length();
        if (n != 64) return null;
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
