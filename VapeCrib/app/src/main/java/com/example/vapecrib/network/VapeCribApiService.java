package com.example.vapecrib.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit interface matching your Flask blueprint:
 *   POST  /api/mobile/auth/login
 *   GET   /api/mobile/sales
 *   GET   /api/mobile/products
 *
 * All GET endpoints are JWT-protected (@jwt_required).
 * The Bearer token is injected automatically by AuthInterceptor.
 */
public interface VapeCribApiService {

    /** Login — returns JWT access_token */
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest body);

    /**
     * Sales with optional date filter and pagination.
     * Matches: GET /api/mobile/sales?from_date=&to_date=&page=&limit=
     */
    @GET("sales")
    Call<PagedSalesResponse> getSales(
            @Query("from_date") String fromDate,   // e.g. "2025-01-01"
            @Query("to_date")   String toDate,     // e.g. "2025-12-31"
            @Query("page")      int    page,       // 1-based
            @Query("limit")     int    limit       // max 100
    );

    /**
     * Product catalogue / inventory with pagination and search.
     * Matches: GET /api/mobile/products?page=&limit=&search=&sort=
     */
    @GET("products")
    Call<PagedProductsResponse> getProducts(
            @Query("page")   int    page,
            @Query("limit")  int    limit,
            @Query("search") String search,        // nullable
            @Query("sort")   String sort           // "name"|"stock"|"cost"
    );

    /**
     * Daily forecasts from the Forecast model.
     * Matches: GET /api/mobile/forecast?from_date=&to_date=&page=&limit=
     */
    @GET("forecast")
    Call<PagedForecastResponse> getForecast(
            @Query("from_date") String fromDate,
            @Query("to_date")   String toDate,
            @Query("page")      int    page,
            @Query("limit")     int    limit
    );
}
