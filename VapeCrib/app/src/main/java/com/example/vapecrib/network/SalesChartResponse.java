package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Mirrors GET /api/mobile/sales/chart response.
 *
 * {
 *   "success": true,
 *   "data": {
 *     "daily":   [ {"date":"2025-01-01","revenue":1234.5,"quantity":20}, ... ],
 *     "monthly": [ {"month":"2025-01","revenue":12345.0,"quantity":200}, ... ],
 *     "total_revenue": 123456.0,
 *     "total_quantity": 2000
 *   }
 * }
 */
public class SalesChartResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("daily")
        public List<DailyPoint> daily;

        @SerializedName("monthly")
        public List<MonthlyPoint> monthly;

        @SerializedName("total_revenue")
        public float totalRevenue;

        @SerializedName("total_quantity")
        public int totalQuantity;
    }

    /** One data point per calendar day. */
    public static class DailyPoint {
        /** "YYYY-MM-DD" */
        @SerializedName("date")
        public String date;

        @SerializedName("revenue")
        public float revenue;

        @SerializedName("quantity")
        public int quantity;
    }

    /** One data point per calendar month. */
    public static class MonthlyPoint {
        /** "YYYY-MM" */
        @SerializedName("month")
        public String month;

        @SerializedName("revenue")
        public float revenue;

        @SerializedName("quantity")
        public int quantity;
    }
}
