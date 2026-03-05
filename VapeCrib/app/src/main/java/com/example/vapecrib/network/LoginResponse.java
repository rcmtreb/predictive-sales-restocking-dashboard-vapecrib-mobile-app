package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    /** Flask-JWT-Extended returns { "access_token": "eyJ..." } */
    @SerializedName("access_token")
    private String accessToken;

    public String getAccessToken() { return accessToken; }
}
