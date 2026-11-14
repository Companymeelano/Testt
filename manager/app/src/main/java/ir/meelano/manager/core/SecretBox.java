package ir.meelano.manager.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts small secrets (the AI API key) with an AES-GCM key that lives in the
 * Android Keystore. Plain-text values never touch SharedPreferences.
 */
public final class SecretBox {
    private SecretBox() { }

    private static final String ALIAS = "meelano_secret_v1";
    private static final String PREFS = "meelano_secrets";
    private static final String PREFIX = "g1:";

    private static SecretKey key(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        if (!create) return null;
        KeyGenerator gen = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                        | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }

    /** Stores the value encrypted under the given name. Empty value removes it. */
    public static boolean put(Context c, String name, String value) {
        try {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (value == null || value.isEmpty()) {
                p.edit().remove(name).apply();
                return true;
            }
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key(true));
            byte[] iv = ci.getIV();
            byte[] ct = ci.doFinal(value.getBytes("UTF-8"));
            String v = PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
            p.edit().putString(name, v).apply();
            return true;
        } catch (Exception e) {
            Diag.ignored("SecretBox:put", e);
            return false;
        }
    }

    /** Returns the decrypted value, or an empty string when absent or unreadable. */
    public static String get(Context c, String name) {
        try {
            String v = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, "");
            if (v == null || !v.startsWith(PREFIX)) return "";
            String[] parts = v.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
            return new String(ci.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            Diag.ignored("SecretBox:get", e);
            return "";
        }
    }
}
