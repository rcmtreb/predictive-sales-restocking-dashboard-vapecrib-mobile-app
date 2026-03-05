package com.example.vapecrib.network;

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
 *   1. Use stored credentials to call POST /api/mobile/auth/login synchronously.
 *   2. If login succeeds → save new token, retry original request.
 *   3. If login fails    → return null (OkHttp will propagate the 401 to the caller).
 */
public class TokenAuthenticator implements okhttp3.Authenticator {

    private static final String LOGIN_URL =
            RetrofitClient.BASE_URL + "auth/login";
    private static final int MAX_RETRIES = 1;

    private final TokenManager tokenManager;
    private final Gson gson = new Gson();

    public TokenAuthenticator(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, okhttp3.Response response) {
        // Give up after MAX_RETRIES to avoid infinite refresh loops
        if (responseCount(response) >= MAX_RETRIES) return null;

        String username = tokenManager.getUsername();
        String password = tokenManager.getPassword();
        if (username == null || password == null) return null;

        // Synchronous re-login using a plain OkHttpClient (no auth interceptor)
        String newToken = refreshToken(username, password);
        if (newToken == null) return null;

        tokenManager.saveToken(newToken);

        return response.request().newBuilder()
                .header("Authorization", "Bearer " + newToken)
                .build();
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
