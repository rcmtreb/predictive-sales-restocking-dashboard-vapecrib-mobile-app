package com.example.vapecrib;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vapecrib.databinding.ActivityLoginBinding;
import com.example.vapecrib.network.LoginRequest;
import com.example.vapecrib.network.LoginResponse;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.TokenManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tokenManager = TokenManager.getInstance(this);

        // If we already have a valid token, skip the login screen
        if (tokenManager.hasToken()) {
            launchMain();
            return;
        }

        binding.loginbtn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String username = binding.email.getText() != null
                ? binding.email.getText().toString().trim() : "";
        String password = binding.password.getText() != null
                ? binding.password.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter your username and password.");
            return;
        }

        setLoading(true);

        RetrofitClient.getInstance(this)
                .getApi()
                .login(new LoginRequest(username, password))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call,
                                           Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getAccessToken() != null) {

                            // Save token + credentials for silent re-login
                            tokenManager.saveToken(response.body().getAccessToken());
                            tokenManager.saveCredentials(username, password);

                            launchMain();
                        } else {
                            // 401 or empty body
                            int code = response.code();
                            if (code == 401) {
                                showError("Invalid username or password.");
                            } else {
                                showError("Login failed (server error " + code + "). Try again.");
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        // Render free-tier can take ~30s to wake up
                        showError("Cannot reach server. Check your connection.\n"
                                + "(Render may be waking up — wait 30s and retry)");
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.loginbtn.setEnabled(!loading);
        binding.loginbtn.setText(loading ? "Signing in…" : "SIGN IN");
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.tvLoginError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        binding.tvLoginError.setText(message);
        binding.tvLoginError.setVisibility(View.VISIBLE);
    }

    private void launchMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish(); // remove LoginActivity from back stack
    }
}

