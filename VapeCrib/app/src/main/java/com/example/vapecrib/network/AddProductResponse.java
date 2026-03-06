package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/** Response from POST /api/mobile/products */
public class AddProductResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("message")
    public String message;

    /** Populated when success == false */
    @SerializedName("error")
    public String error;

    /** Created product record — nested under "data" by _ok() */
    @SerializedName("data")
    public ProductApiRecord data;
}
