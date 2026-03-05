package com.example.vapecrib;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.example.vapecrib.databinding.ActivityRegisterBinding;
import com.example.vapecrib.network.RegisterRequest;
import com.example.vapecrib.network.RegisterResponse;
import com.example.vapecrib.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.registerbtn.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String username   = binding.username.getText()   != null ? binding.username.getText().toString().trim()   : "";
        String email      = binding.email.getText()      != null ? binding.email.getText().toString().trim()      : "";
        String password   = binding.password.getText()   != null ? binding.password.getText().toString()          : "";
        String repassword = binding.repassword.getText() != null ? binding.repassword.getText().toString()        : "";

        // ── Client-side validation — mirrors server password policy in config.py ──
        // PASSWORD_MIN_LENGTH = 8
        // PASSWORD_REQUIRE_UPPERCASE = True
        // PASSWORD_REQUIRE_LOWERCASE = True
        // PASSWORD_REQUIRE_NUMBERS = True
        if (username.isEmpty())                          { showError("Username is required.");                              return; }
        if (email.isEmpty())                             { showError("Email is required.");                                 return; }
        if (password.isEmpty())                          { showError("Password is required.");                              return; }
        if (password.length() < 8)                       { showError("Password must be at least 8 characters.");           return; }
        if (!password.matches(".*[A-Z].*"))              { showError("Password must contain at least one uppercase letter."); return; }
        if (!password.matches(".*[a-z].*"))              { showError("Password must contain at least one lowercase letter."); return; }
        if (!password.matches(".*[0-9].*"))              { showError("Password must contain at least one number.");         return; }
        if (!password.equals(repassword))                { showError("Passwords do not match.");                           return; }

        setLoading(true);

        RetrofitClient.getInstance(this)
                .getApi()
                .register(new RegisterRequest(username, email, password))
                .enqueue(new Callback<RegisterResponse>() {
                    @Override
                    public void onResponse(Call<RegisterResponse> call,
                                           Response<RegisterResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().success) {
                            Toast.makeText(RegisterActivity.this,
                                    "Registration successful! Please log in.",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            String error = (response.body() != null && response.body().error != null)
                                    ? response.body().error
                                    : "Registration failed (code " + response.code() + "). Try again.";
                            showError(error);
                        }
                    }

                    @Override
                    public void onFailure(Call<RegisterResponse> call, Throwable t) {
                        setLoading(false);
                        showError("Cannot reach server. Check your connection.\n"
                                + "(Render may be waking up — wait 30s and retry)");
                    }
                });
    }

    private void setLoading(boolean loading) {
        binding.registerbtn.setEnabled(!loading);
        binding.registerbtn.setText(loading ? "Registering…" : "REGISTER");
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}

