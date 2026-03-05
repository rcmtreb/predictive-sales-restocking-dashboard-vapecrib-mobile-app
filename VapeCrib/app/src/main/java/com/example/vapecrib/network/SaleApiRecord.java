package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/**
 * Mirrors one row from GET /api/mobile/sales.
 * Adjust @SerializedName values to match your actual Flask JSON field names.
 */
public class SaleApiRecord {

    @SerializedName("id")
    public int id;

    /** ISO date string e.g. "2025-03-01" — Flask field: sale_date */
    @SerializedName("sale_date")
    public String date;

    /** Units sold in this transaction — Flask field: quantity */
    @SerializedName("quantity")
    public float actualSales;

    /** Flask does not send forecasted_sales; Gson will default this to 0f */
    @SerializedName("forecasted_sales")
    public float forecastedSales;

    /** Revenue for this transaction (quantity * price) — Flask field: total */
    @SerializedName("total")
    public float revenue;

    /** Unit price of the product */
    @SerializedName("price")
    public float price;

    /** Flask does not send products_count; Gson will default this to 0 */
    @SerializedName("products_count")
    public int productsCount;

    @SerializedName("product_id")
    public int productId;

    @SerializedName("product_name")
    public String productName;
}
