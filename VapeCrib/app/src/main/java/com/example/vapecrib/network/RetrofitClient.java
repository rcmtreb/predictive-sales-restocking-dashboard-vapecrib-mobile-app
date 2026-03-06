package com.example.vapecrib.network;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client.
 *
 * Base URL:   https://vapecrib.onrender.com/api/mobile/
 * Auth:       JWT Bearer token via AuthInterceptor
 * Token refresh: TokenAuthenticator handles 401 silently
 */
public class RetrofitClient {

    /** Must end with "/" and include the mobile blueprint prefix */
    public static final String BASE_URL = "https://vapecrib.onrender.com/api/mobile/";

    private static RetrofitClient instance;
    private final VapeCribApiService apiService;

    private RetrofitClient(Context context) {
        TokenManager tm = TokenManager.getInstance(context);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // Log full request/response bodies in debug builds only.
        // Release builds use NONE to avoid leaking JWT tokens in logs.
        boolean isDebuggable = (context.getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        logging.setLevel(isDebuggable
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)  // Render free tier cold-start ≤60 s
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(tm))   // attach Bearer token
                .authenticator(new TokenAuthenticator(tm)) // silent re-login on 401
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(VapeCribApiService.class);
    }

    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) instance = new RetrofitClient(context);
        return instance;
    }

    public VapeCribApiService getApi() {
        return apiService;
    }
}
