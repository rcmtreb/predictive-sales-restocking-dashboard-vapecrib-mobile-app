package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Maps GET /api/mobile/alerts response. */
public class AlertsResponse {
    @SerializedName("success") public boolean success;
    @SerializedName("data")    public List<AlertItem> data;

    public static class AlertItem {
        @SerializedName("product_id") public int    productId;
        @SerializedName("severity")   public String severity;   // "CRITICAL" | "HIGH" | "MEDIUM"
        @SerializedName("is_active")  public boolean isActive;
    }
}
