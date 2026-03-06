package com.example.vapecrib;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vapecrib.databinding.ActivityLoginBinding;
import com.example.vapecrib.network.LoginRequest;
import com.example.vapecrib.network.LoginResponse;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.TokenManager;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private TokenManager tokenManager;
    // Handler used to show a "server is waking up" hint after 8s of waiting
    private final Handler coldStartHandler = new Handler(Looper.getMainLooper());
    private Runnable coldStartRunnable;

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

        // Fire a lightweight ping to wake up the Render dyno while the user
        // types their credentials — keeps perceived login latency low.
        // Show a subtle hint so users know the server is being contacted.
        showError("Connecting to server\u2026");
        pingServer();
    }

    /** Sends a no-auth GET /api/mobile/ping to warm up the server. */
    private void pingServer() {
        RetrofitClient.getInstance(this)
                .getApi()
                .ping()
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        runOnUiThread(() -> {
                            if (binding != null) binding.tvLoginError.setVisibility(View.GONE);
                        });
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        // Server still waking — login attempt (60 s timeout) will handle it
                        runOnUiThread(() -> {
                            if (binding != null) binding.tvLoginError.setVisibility(View.GONE);
                        });
                    }
                });
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

        // If still waiting after 8 s, hint that Render may be cold-starting.
        // The handler is removed immediately if login succeeds/fails before then.
        coldStartRunnable = () -> {
            if (binding != null) {
                binding.loginbtn.setText("Server starting up\u2026");
                binding.tvLoginError.setText("Server is waking up — this can take up to 30 s.");
                binding.tvLoginError.setVisibility(View.VISIBLE);
            }
        };
        coldStartHandler.postDelayed(coldStartRunnable, 8000);

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
                            if (response.body().getRefreshToken() != null) {
                                tokenManager.saveRefreshToken(response.body().getRefreshToken());
                            }
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

    private void setLoading(boolean loading) {        if (!loading && coldStartRunnable != null) {
            coldStartHandler.removeCallbacks(coldStartRunnable);
            coldStartRunnable = null;
        }        binding.loginbtn.setEnabled(!loading);
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

