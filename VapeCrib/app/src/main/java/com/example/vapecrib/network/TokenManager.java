package com.example.vapecrib.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

/**
 * Manages JWT token + login credentials.
 *
 * The JWT access token is now persisted in EncryptedSharedPreferences to survive
 * app restarts and provide better user experience. Refresh tokens and credentials
 * are also persisted for automatic re-authentication.
 */
public class TokenManager {

    private static final String PREFS_FILE   = "vapecrib_secure";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REFRESH  = "jwt_refresh_token";
    private static final String KEY_ACCESS   = "jwt_access_token";

    // Token lives only in memory — process death = automatic logout
    private static String sessionToken = null;

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

    // ── Token (now persisted to survive app restarts) ───────────────────────────────────────────────

    public void saveToken(String token) {
        sessionToken = token;
        prefs.edit().putString(KEY_ACCESS, token).apply();
    }

    public String getToken() {
        if (sessionToken == null) {
            // Load from persistent storage on first access
            sessionToken = prefs.getString(KEY_ACCESS, null);
        }
        return sessionToken;
    }

    public boolean hasToken() {
        return sessionToken != null && !sessionToken.isEmpty();
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

    // ── Refresh token (persisted — survives process death) ─────────────────────────

    public void saveRefreshToken(String token) {
        prefs.edit().putString(KEY_REFRESH, token).apply();
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH, null);
    }

    // ── Clear all (on logout) ─────────────────────────────────────────────────

    public void clearAll() {
        sessionToken = null;
        prefs.edit()
             .remove(KEY_ACCESS)
             .remove(KEY_USERNAME)
             .remove(KEY_PASSWORD)
             .remove(KEY_REFRESH)
             .apply();
    }
}
