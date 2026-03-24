package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Mirrors the JSON response from GET /api/mobile/forecast/daily — the same
 * structure the web's /api/forecast/daily returns.  Using a single endpoint
 * guarantees the mobile chart matches the web chart exactly.
 */
public class DailyForecastResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("product_id")
    public Object productId;            // int or "all"

    @SerializedName("aggregate_all")
    public boolean aggregateAll;

    @SerializedName("week_start")
    public String weekStart;

    @SerializedName("week_end")
    public String weekEnd;

    @SerializedName("week_number")
    public int weekNumber;

    @SerializedName("current_day")
    public String currentDay;

    @SerializedName("actual")
    public List<ActualPoint> actual;

    @SerializedName("forecast")
    public List<ForecastPoint> forecast;

    @SerializedName("accuracy")
    public Float accuracy;              // nullable

    public static class ActualPoint {
        @SerializedName("date")
        public String date;

        @SerializedName("sales")
        public float sales;

        @SerializedName("day_name")
        public String dayName;
    }

    public static class ForecastPoint {
        @SerializedName("date")
        public String date;

        @SerializedName("sales")
        public float sales;

        @SerializedName("confidence_lower")
        public float confidenceLower;

        @SerializedName("confidence_upper")
        public float confidenceUpper;

        @SerializedName("day_name")
        public String dayName;
    }
}
