package app.hack.eightballpool;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the Groq key encrypted with an Android Keystore AES-256-GCM key. */
public final class SecureKeyStore {
    private static final String STORE = "groq_secure_store";
    private static final String KEY_ALIAS = "groq_api_key_aes";
    private static final String PREF_CIPHER = "ciphertext";
    private static final String PREF_IV = "iv";

    private SecureKeyStore() {}

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    public static void save(Context context, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    public static String read(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        String encoded = prefs.getString(PREF_CIPHER, null);
        String ivEncoded = prefs.getString(PREF_IV, null);
        if (encoded == null || ivEncoded == null) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                new GCMParameterSpec(128, Base64.decode(ivEncoded, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
