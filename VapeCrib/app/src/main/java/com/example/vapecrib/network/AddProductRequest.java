package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/** Request body for POST /api/mobile/products */
public class AddProductRequest {

    @SerializedName("name")
    public String name;

    @SerializedName("category")
    public String category;

    @SerializedName("unit_cost")
    public float unitCost;

    @SerializedName("current_stock")
    public int currentStock;

    public AddProductRequest(String name, String category, float unitCost, int currentStock) {
        this.name         = name;
        this.category     = category;
        this.unitCost     = unitCost;
        this.currentStock = currentStock;
    }
}
