package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SalesReportResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public Data data;

    public static class Data {
        @SerializedName("period")
        public String period;

        @SerializedName("period_label")
        public String periodLabel;

        @SerializedName("sales")
        public List<SaleItem> sales;

        @SerializedName("total_sales")
        public int totalSales;

        @SerializedName("total_revenue")
        public double totalRevenue;
    }

    public static class SaleItem {
        @SerializedName("date")
        public String date;

        @SerializedName("product_name")
        public String productName;

        @SerializedName("quantity")
        public int quantity;

        @SerializedName("price")
        public double price;

        @SerializedName("revenue")
        public double revenue;
    }
}
