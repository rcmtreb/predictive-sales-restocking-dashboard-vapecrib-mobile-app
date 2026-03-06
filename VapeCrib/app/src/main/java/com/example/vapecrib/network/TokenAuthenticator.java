package com.example.vapecrib.network;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * OkHttp Authenticator — called automatically on 401 Unauthorized.
 *
 * Strategy:
 *   1. Use the stored refresh_token to call POST /api/mobile/auth/refresh (silent, no
 *      credentials needed, token valid for 30 days).
 *   2. If refresh succeeds  → save new access_token, retry original request.
 *   3. If refresh_token is missing or refresh fails → fall back to full credential
 *      re-login via POST /api/mobile/auth/login.
 *   4. If both fail → return null (OkHttp will propagate the 401 to the caller).
 */
public class TokenAuthenticator implements okhttp3.Authenticator {

    private static final String LOGIN_URL   = RetrofitClient.BASE_URL + "auth/login";
    private static final String REFRESH_URL = RetrofitClient.BASE_URL + "auth/refresh";
    private static final int MAX_RETRIES = 1;

    private final TokenManager tokenManager;
    private final Gson gson = new Gson();

    public TokenAuthenticator(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, okhttp3.Response response) {
        Log.d("TokenAuthenticator", "401 received, attempting token refresh");
        // Give up after MAX_RETRIES to avoid infinite refresh loops
        if (responseCount(response) >= MAX_RETRIES) {
            Log.w("TokenAuthenticator", "Max retries exceeded, giving up");
            return null;
        }

        // 1️⃣ Try refresh_token first (30-day lifetime, no credentials needed)
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken != null) {
            Log.d("TokenAuthenticator", "Attempting refresh token authentication");
            String newToken = useRefreshToken(refreshToken);
            if (newToken != null) {
                Log.i("TokenAuthenticator", "Refresh token successful, retrying request");
                tokenManager.saveToken(newToken);
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newToken)
                        .build();
            } else {
                Log.w("TokenAuthenticator", "Refresh token failed");
            }
        } else {
            Log.w("TokenAuthenticator", "No refresh token available");
        }

        // 2️⃣ Fall back to full credential re-login
        Log.d("TokenAuthenticator", "Attempting credential re-login");
        String username = tokenManager.getUsername();
        String password = tokenManager.getPassword();
        if (username == null || password == null) {
            Log.w("TokenAuthenticator", "No credentials available for re-login");
            return null;
        }

        String newToken = refreshToken(username, password);
        if (newToken == null) {
            Log.e("TokenAuthenticator", "Credential re-login failed");
            return null;
        }

        Log.i("TokenAuthenticator", "Credential re-login successful");
        tokenManager.saveToken(newToken);

        return response.request().newBuilder()
                .header("Authorization", "Bearer " + newToken)
                .build();
    }

    /** Exchanges a valid refresh token for a new access token via /auth/refresh. */
    @Nullable
    private String useRefreshToken(String refreshToken) {
        try {
            OkHttpClient plainClient = new OkHttpClient();
            Request req = new Request.Builder()
                    .url(REFRESH_URL)
                    .post(RequestBody.create(new byte[0], null))
                    .header("Authorization", "Bearer " + refreshToken)
                    .build();
            try (Response res = plainClient.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    LoginResponse lr = gson.fromJson(res.body().string(), LoginResponse.class);
                    return lr != null ? lr.getAccessToken() : null;
                }
            }
        } catch (IOException e) {
            // Fall through to credential re-login
        }
        return null;
    }

    @Nullable
    private String refreshToken(String username, String password) {
        try {
            OkHttpClient plainClient = new OkHttpClient();
            String json = gson.toJson(new LoginRequest(username, password));
            RequestBody body = RequestBody.create(
                    json, MediaType.parse("application/json; charset=utf-8"));
            Request req = new Request.Builder()
                    .url(LOGIN_URL)
                    .post(body)
                    .build();
            try (Response res = plainClient.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    LoginResponse lr = gson.fromJson(res.body().string(), LoginResponse.class);
                    return lr != null ? lr.getAccessToken() : null;
                }
            }
        } catch (IOException e) {
            // network error — let callers handle it
        }
        return null;
    }

    private int responseCount(okhttp3.Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }
}
