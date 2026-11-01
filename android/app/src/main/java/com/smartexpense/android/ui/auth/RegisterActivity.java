package com.smartexpense.android.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.ActivityRegisterBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.ui.main.MainActivity;
import com.smartexpense.android.util.TokenManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Register screen with Firebase Authentication support, proper validation,
 * error handling, and demo mode access.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.createService(ApiService.class);

        binding.btnRegister.setOnClickListener(v -> performRegistration());
        binding.btnDemoMode.setOnClickListener(v -> enterDemoMode());
        binding.tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        binding.btnBack.setOnClickListener(v -> finish());

        TextWatcher clearErrors = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearFieldErrors();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        binding.etFullName.addTextChangedListener(clearErrors);
        binding.etEmail.addTextChangedListener(clearErrors);
        binding.etPassword.addTextChangedListener(clearErrors);
        binding.etConfirmPassword.addTextChangedListener(clearErrors);
    }

    private void clearFieldErrors() {
        binding.tilFullName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
    }

    private void enterDemoMode() {
        TokenManager.saveToken("demo-token-" + System.currentTimeMillis(), "demo-refresh-token");
        TokenManager.saveUserInfo("demo-user", "demo@smartexpense.app", "Demo User");
        OfflineMockStorage.seedDemoDataIfEmpty();
        Toast.makeText(this, "Welcome to Demo Mode! 🎉", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finishAffinity();
    }

    private void performRegistration() {
        clearFieldErrors();
        String name = binding.etFullName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirm = binding.etConfirmPassword.getText().toString().trim();

        boolean valid = true;
        if (name.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.error_required));
            valid = false;
        }
        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.error_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            valid = false;
        }
        if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_length));
            valid = false;
        }
        if (!password.equals(confirm)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());
                            user.getIdToken(false).addOnSuccessListener(res -> {
                                TokenManager.saveToken(res.getToken(), "firebase-refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), name);
                                syncBackendRegister(name, email, password);
                                completeRegistration(name);
                            }).addOnFailureListener(e -> {
                                TokenManager.saveToken("token-" + System.currentTimeMillis(), "refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), name);
                                completeRegistration(name);
                            });
                        }
                    } else {
                        handleRegistrationError(task.getException(), name, email, password);
                    }
                });
    }

    private void handleRegistrationError(Exception exception, String name, String email, String password) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            binding.tilEmail.setError("An account already exists with this email. Please log in.");
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            binding.tilPassword.setError(exception.getMessage() != null ? exception.getMessage() : "Password is too weak");
        } else if (exception instanceof FirebaseNetworkException) {
            attemptBackendFallback(name, email, password);
        } else {
            String msg = exception != null && exception.getMessage() != null
                    ? exception.getMessage() : "Registration failed. Please try again.";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void attemptBackendFallback(String name, String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("displayName", name);
        body.put("email", email);
        body.put("password", password);

        apiService.register(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> data = response.body().getData();
                    if (data != null) {
                        TokenManager.saveToken((String) data.get("accessToken"), (String) data.get("refreshToken"));
                        TokenManager.saveUserInfo((String) data.get("userId"), (String) data.get("email"), (String) data.get("displayName"));
                        completeRegistration(name);
                    }
                } else {
                    Toast.makeText(RegisterActivity.this, "Registration failed. Email may already be registered.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(RegisterActivity.this, "Network connection error. Please check your internet connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void completeRegistration(String name) {
        Toast.makeText(this, "Welcome " + name + "! Account created 🎉", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finishAffinity();
    }

    private void syncBackendRegister(String name, String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("displayName", name);
        body.put("email", email);
        body.put("password", password);
        apiService.register(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {}
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
        binding.btnDemoMode.setEnabled(!loading);
    }
}
