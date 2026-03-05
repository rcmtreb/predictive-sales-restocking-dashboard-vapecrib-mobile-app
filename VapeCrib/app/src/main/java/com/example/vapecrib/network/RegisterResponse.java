package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

/**
 * Response for POST /api/mobile/auth/register.
 * Flask _ok() returns { "success": true, "data": {...}, "message": "..." }
 * Flask _error() returns { "success": false, "error": "..." }
 */
public class RegisterResponse {

    @SerializedName("success")
    public boolean success;

    @SerializedName("message")
    public String message;

    @SerializedName("error")
    public String error;
}
