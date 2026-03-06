package com.example.vapecrib.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Headers;

/**
 * Retrofit interface matching your Flask blueprint:
 *   POST  /api/mobile/auth/login
 *   GET   /api/mobile/dashboard
 *   GET   /api/mobile/sales
 *   GET   /api/mobile/products
 *   GET   /api/mobile/forecasts
 *
 * All GET endpoints are JWT-protected (@jwt_required).
 * The Bearer token is injected automatically by AuthInterceptor.
 */
public interface VapeCribApiService {

    /** Login — returns JWT access_token */
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest body);

    /** Register — creates a new user account (role='user'; first ever user becomes admin) */
    @POST("auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest body);

    /**
     * Dashboard summary: revenue today/week/month, product counts, alert counts,
     * inventory value, expiring batches, top 5 products.
     * Matches: GET /api/mobile/dashboard
     */
    @GET("dashboard")
    Call<DashboardResponse> getDashboard();

    /**
     * Sales with optional date filter, product filter, and pagination.
     * Matches: GET /api/mobile/sales?from_date=&to_date=&product_id=&page=&limit=
     */
    @GET("sales")
    Call<PagedSalesResponse> getSales(
            @Query("from_date")  String fromDate,
            @Query("to_date")    String toDate,
            @Query("product_id") Integer productId,  // nullable — omitted when null
            @Query("page")       int    page,
            @Query("limit")      int    limit
    );

    /**
     * Pre-aggregated daily + monthly sales totals for the chart.
     * ONE request replaces thousands of paginated /sales calls.
     * Matches: GET /api/mobile/sales/chart?from_date=&to_date=&product_id=
     */
    @GET("sales/chart")
    Call<SalesChartResponse> getSalesChart(
            @Query("from_date")  String  fromDate,
            @Query("to_date")    String  toDate,
            @Query("product_id") Integer productId   // nullable
    );

    /**
     * Product catalogue / inventory with pagination and search.
     * Matches: GET /api/mobile/products?page=&limit=&search=&sort=
     */
    @GET("products")
    Call<PagedProductsResponse> getProducts(
            @Query("page")   int    page,
            @Query("limit")  int    limit,
            @Query("search") String search,   // nullable
            @Query("sort")   String sort      // "name"|"stock"|"cost"
    );

    /**
     * Daily forecasts with optional product filter and date range.
     * Matches: GET /api/mobile/forecasts?product_id=&from_date=&to_date=&page=&limit=
     */
    @GET("forecasts")
    Call<PagedForecastResponse> getForecast(
            @Query("from_date")  String  fromDate,
            @Query("to_date")    String  toDate,
            @Query("product_id") Integer productId, // nullable — omitted when null
            @Query("page")       int     page,
            @Query("limit")      int     limit
    );

    /**
     * No-auth lightweight health check used to wake up the Render dyno
     * before the user presses Sign In.
     * Matches: GET /api/mobile/ping
     */
    @GET("ping")
    Call<okhttp3.ResponseBody> ping();

    /**
     * Create a new product. Admin / Manager accounts only.
     * Matches: POST /api/mobile/products
     */
    @Headers("Content-Type: application/json")
    @POST("products")
    Call<AddProductResponse> createProduct(@Body AddProductRequest body);

    /**
     * MAPE-based forecast accuracy — same calculation method as the web dashboard.
     * Matches: GET /api/mobile/forecast-accuracy?days_back=90
     */
    @GET("forecast-accuracy")
    Call<ForecastAccuracyResponse> getForecastAccuracy(
            @Query("days_back") int daysBack
    );
}

