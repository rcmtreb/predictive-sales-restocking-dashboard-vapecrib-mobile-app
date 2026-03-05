package com.example.vapecrib.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;

/**
 * Manages JWT token + login credentials in EncryptedSharedPreferences.
 *
 * Keys stored:
 *   jwt_token   — the current Bearer token
 *   username    — stored for silent re-login on token expiry
 *   password    — stored for silent re-login on token expiry
 */
public class TokenManager {

    private static final String PREFS_FILE   = "vapecrib_secure";
    private static final String KEY_TOKEN    = "jwt_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    private static TokenManager instance;
    private final SharedPreferences prefs;

    private TokenManager(Context context) {
        SharedPreferences p;
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            p = EncryptedSharedPreferences.create(
                    PREFS_FILE,
                    masterKeyAlias,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to plain prefs if encryption unavailable (e.g. unit tests)
            p = context.getApplicationContext()
                    .getSharedPreferences(PREFS_FILE + "_plain", Context.MODE_PRIVATE);
        }
        this.prefs = p;
    }

    public static synchronized TokenManager getInstance(Context context) {
        if (instance == null) instance = new TokenManager(context);
        return instance;
    }

    // ── Token ────────────────────────────────────────────────────────────────

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean hasToken() {
        return getToken() != null;
    }

    // ── Credentials (for silent re-login) ────────────────────────────────────

    public void saveCredentials(String username, String password) {
        prefs.edit()
             .putString(KEY_USERNAME, username)
             .putString(KEY_PASSWORD, password)
             .apply();
    }

    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public String getPassword() { return prefs.getString(KEY_PASSWORD, null); }

    // ── Clear all (on logout) ─────────────────────────────────────────────────

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
