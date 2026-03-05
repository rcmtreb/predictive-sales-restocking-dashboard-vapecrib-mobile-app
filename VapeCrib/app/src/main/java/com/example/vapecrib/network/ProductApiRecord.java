package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/**
 * Mirrors one row from GET /api/mobile/products.
 * Adjust @SerializedName values to match your actual Flask JSON field names.
 */
public class ProductApiRecord {

    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public String name;

    @SerializedName("category")
    public String category;

    @SerializedName("current_stock")
    public int currentStock;

    /** Flask field name is unit_cost (not cost_price) */
    @SerializedName("unit_cost")
    public float costPrice;

    @SerializedName("created_at")
    public String createdAt;

    // ── Fields NOT returned by Flask to_dict() ────────────────────────────────
    // Gson will silently default these to 0 / false — they are unused for now.
    // Add them to Product.to_dict() on the Flask side if you need them later.
    @SerializedName("min_stock_level")  public int minStockLevel;
    @SerializedName("max_stock_level")  public int maxStockLevel;
    @SerializedName("alert_level")      public String alertLevel;
    @SerializedName("is_expiring_soon") public boolean isExpiringSoon;
    @SerializedName("selling_price")    public float sellingPrice;
}
