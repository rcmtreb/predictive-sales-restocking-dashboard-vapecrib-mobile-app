package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/**
 * Response model for GET /api/mobile/forecast-accuracy
 *
 * JSON shape:
 * {
 *   "success": true,
 *   "data": {
 *     "accuracy": 82.5
 *   }
 * }
 */
public class ForecastAccuracyResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public Data data;

    public static class Data {
        /** Accuracy percentage: 0.0 – 100.0 (100 = perfect, matches web dashboard). */
        @SerializedName("accuracy")
        public float accuracy;
    }
}
