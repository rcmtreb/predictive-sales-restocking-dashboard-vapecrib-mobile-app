package com.example.vapecrib.network;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("username")
    private final String username;

    @SerializedName("password")
    private final String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
