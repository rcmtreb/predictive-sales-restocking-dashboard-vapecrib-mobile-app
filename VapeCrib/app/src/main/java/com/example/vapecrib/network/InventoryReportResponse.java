package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class InventoryReportResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("report_type")
        public String reportType;

        @SerializedName("report_label")
        public String reportLabel;

        @SerializedName("inventory")
        public List<InventoryItem> inventory;

        @SerializedName("total_items")
        public int totalItems;

        @SerializedName("total_value")
        public double totalValue;
    }

    public static class InventoryItem {
        @SerializedName("product_name")
        public String productName;

        @SerializedName("category")
        public String category;

        @SerializedName("current_stock")
        public int currentStock;

        @SerializedName("unit_cost")
        public double unitCost;

        @SerializedName("total_value")
        public double totalValue;
    }
}
