package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Deserialises GET /api/mobile/dashboard.
 *
 * Flask _ok() wraps the payload in a top-level "data" key:
 *   { "success": true, "data": { "revenue": {...}, ... } }
 */
public class DashboardResponse {

    @SerializedName("data")
    public Data data;

    public static class Data {

        @SerializedName("revenue")
        public Revenue revenue;

        @SerializedName("products")
        public Products products;

        /** Total count of active, unacknowledged alerts. */
        @SerializedName("active_alerts")
        public int activeAlerts;

        /** Breakdown by Alert.severity ('CRITICAL', 'WARNING', 'INFO'). */
        @SerializedName("alerts_by_severity")
        public AlertsBySeverity alertsBySeverity;

        /** sum(current_stock * unit_cost) across all real products. */
        @SerializedName("inventory_value")
        public float inventoryValue;

        /** Count of InventoryBatch rows expiring within 7 days with quantity > 0. */
        @SerializedName("expiring_soon")
        public int expiringSoon;

        /** Top 5 best-selling products in the last 30 days. */
        @SerializedName("top_products")
        public List<TopProduct> topProducts;
    }

    public static class Revenue {
        @SerializedName("today") public float today;
        @SerializedName("week")  public float week;
        @SerializedName("month") public float month;
    }

    public static class Products {
        @SerializedName("total")     public int total;
        @SerializedName("low_stock") public int lowStock;  // current_stock <= 10
    }

    public static class AlertsBySeverity {
        @SerializedName("critical") public int critical;
        @SerializedName("warning")  public int warning;
        @SerializedName("info")     public int info;
    }

}
