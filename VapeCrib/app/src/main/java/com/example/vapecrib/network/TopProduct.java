package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/** One entry in the top_products list returned by GET /api/mobile/dashboard. */
public class TopProduct {
    @SerializedName("product_id")   public int    productId;
    @SerializedName("product_name") public String productName;
    @SerializedName("units_sold")   public int    unitsSold;
    @SerializedName("revenue")      public float  revenue;
}
