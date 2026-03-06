package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    /** Flask-JWT-Extended returns { "access_token": "eyJ..." } */
    @SerializedName("access_token")
    private String accessToken;

    /** Flask also returns a 30-day refresh token */
    @SerializedName("refresh_token")
    private String refreshToken;

    public String getAccessToken()  { return accessToken;  }
    public String getRefreshToken() { return refreshToken; }
}
