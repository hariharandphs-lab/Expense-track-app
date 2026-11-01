package com.smartexpense.android.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.ActivityLoginBinding;
import com.smartexpense.android.databinding.BottomSheetForgotPasswordBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.ui.main.MainActivity;
import com.smartexpense.android.util.TokenManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Modern Finance Login & Sign-Up screen with:
 * - Interactive Sign In / Sign Up tab switcher
 * - Firebase Google Sign-In with auto account selection
 * - Firebase Email Login & Sign-Up with live validation
 * - Firebase Password Reset dialog
 * - Preserved Demo Mode with complete offline features
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private GoogleSignInClient googleSignInClient;
    private ApiService apiService;
    private boolean isSignUpMode = false;

    // Google Sign-In modern Activity Result Launcher
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                setLoading(false);
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignInResult(task);
                } else if (result.getResultCode() == RESULT_CANCELED) {
                    // User dismissed Google account chooser
                } else {
                    showError("Google Sign-In was cancelled or failed.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.createService(ApiService.class);
        setupGoogleSignIn();
        setupTabs();
        setupClickListeners();
        setupTextWatchers();
    }

    private void setupGoogleSignIn() {
        try {
            GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile();

            String webClientId = resolveWebClientId();
            if (webClientId != null && !webClientId.trim().isEmpty()) {
                gsoBuilder.requestIdToken(webClientId.trim());
            }

            googleSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build());
        } catch (Exception e) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);
        }
    }

    private String resolveWebClientId() {
        try {
            int resId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (resId != 0) {
                String val = getString(resId);
                if (val != null && !val.trim().isEmpty()) return val;
            }
        } catch (Exception ignored) {}
        try {
            int resId = getResources().getIdentifier("google_web_client_id", "string", getPackageName());
            if (resId != 0) {
                String val = getString(resId);
                if (val != null && !val.trim().isEmpty()) return val;
            }
        } catch (Exception ignored) {}
        return "766795638400-ltredm93jd27quva3n40aoiikrgso76g.apps.googleusercontent.com";
    }

    private void setupTabs() {
        binding.tabSignIn.setOnClickListener(v -> setAuthMode(false));
        binding.tabSignUp.setOnClickListener(v -> setAuthMode(true));
    }

    private void setAuthMode(boolean signUp) {
        isSignUpMode = signUp;
        clearFieldErrors();

        if (isSignUpMode) {
            binding.tabSignUp.setBackgroundResource(R.drawable.bg_auth_tab_active);
            binding.tabSignUp.setTextColor(ContextCompat.getColor(this, R.color.primary));
            binding.tabSignIn.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
            binding.tabSignIn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            binding.tilFullName.setVisibility(View.VISIBLE);
            binding.tilConfirmPassword.setVisibility(View.VISIBLE);
            binding.tvForgotPassword.setVisibility(View.GONE);
            binding.btnLogin.setText("Create Account");
            binding.tvFooterPrompt.setText(R.string.already_have_account);
            binding.tvRegister.setText(R.string.sign_in);
        } else {
            binding.tabSignIn.setBackgroundResource(R.drawable.bg_auth_tab_active);
            binding.tabSignIn.setTextColor(ContextCompat.getColor(this, R.color.primary));
            binding.tabSignUp.setBackgroundColor(ContextCompat.getColor(this, R.color.transparent));
            binding.tabSignUp.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

            binding.tilFullName.setVisibility(View.GONE);
            binding.tilConfirmPassword.setVisibility(View.GONE);
            binding.tvForgotPassword.setVisibility(View.VISIBLE);
            binding.btnLogin.setText(R.string.sign_in);
            binding.tvFooterPrompt.setText(R.string.dont_have_account);
            binding.tvRegister.setText(R.string.sign_up);
        }
    }

    private void setupClickListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            if (isSignUpMode) {
                performEmailSignUp();
            } else {
                performEmailLogin();
            }
        });

        binding.btnGoogleSignIn.setOnClickListener(v -> performGoogleSignIn());
        binding.btnDemoMode.setOnClickListener(v -> enterDemoMode());
        binding.tvRegister.setOnClickListener(v -> setAuthMode(!isSignUpMode));
        binding.tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void setupTextWatchers() {
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

    // ─── Email Login ──────────────────────────────────────────────────────────

    private void performEmailLogin() {
        clearFieldErrors();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        boolean hasError = false;
        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.error_required));
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            hasError = true;
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_required));
            hasError = true;
        } else if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        if (hasError) return;

        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            String name = user.getDisplayName();
                            if (name == null || name.trim().isEmpty()) {
                                name = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
                            }
                            final String finalName = name;
                            user.getIdToken(false).addOnSuccessListener(tokenResult -> {
                                TokenManager.saveToken(tokenResult.getToken(), "firebase-refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), finalName);
                                syncBackendEmailLogin(email, password);
                                onAuthComplete(finalName);
                            }).addOnFailureListener(e -> {
                                TokenManager.saveToken("token-" + System.currentTimeMillis(), "refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), finalName);
                                onAuthComplete(finalName);
                            });
                        }
                    } else {
                        handleLoginError(task.getException(), email, password);
                    }
                });
    }

    private void handleLoginError(Exception exception, String email, String password) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            binding.tilEmail.setError("No account found with this email. Please sign up.");
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            binding.tilPassword.setError("Incorrect password. Please try again or tap Forgot Password.");
        } else if (exception instanceof FirebaseNetworkException) {
            attemptBackendLoginFallback(email, password);
        } else {
            String msg = exception != null && exception.getMessage() != null
                    ? exception.getMessage() : getString(R.string.error_login);
            showError(msg);
        }
    }

    private void attemptBackendLoginFallback(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        apiService.login(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    handleAuthSuccess(response.body().getData());
                } else {
                    showError(getString(R.string.error_network));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                showError("Network connection error. Please check your internet connection.");
            }
        });
    }

    // ─── Email Sign-Up ────────────────────────────────────────────────────────

    private void performEmailSignUp() {
        clearFieldErrors();
        String name = binding.etFullName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        boolean hasError = false;
        if (name.isEmpty()) {
            binding.tilFullName.setError(getString(R.string.error_required));
            hasError = true;
        }

        if (email.isEmpty()) {
            binding.tilEmail.setError(getString(R.string.error_required));
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_invalid_email));
            hasError = true;
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_required));
            hasError = true;
        } else if (password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_length));
            hasError = true;
        }

        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) return;

        setLoading(true);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            user.updateProfile(profileUpdates);
                            user.getIdToken(false).addOnSuccessListener(tokenResult -> {
                                TokenManager.saveToken(tokenResult.getToken(), "firebase-refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), name);
                                syncBackendRegister(name, email, password);
                                onAuthComplete(name);
                            }).addOnFailureListener(e -> {
                                TokenManager.saveToken("token-" + System.currentTimeMillis(), "refresh-token");
                                TokenManager.saveUserInfo(user.getUid(), user.getEmail(), name);
                                onAuthComplete(name);
                            });
                        }
                    } else {
                        handleSignUpError(task.getException(), name, email, password);
                    }
                });
    }

    private void handleSignUpError(Exception exception, String name, String email, String password) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            binding.tilEmail.setError("An account already exists with this email. Please sign in.");
            Toast.makeText(this, "Email already registered. Switching to Sign In...", Toast.LENGTH_LONG).show();
            setAuthMode(false);
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            binding.tilPassword.setError(exception.getMessage() != null ? exception.getMessage() : "Password is too weak");
        } else if (exception instanceof FirebaseNetworkException) {
            attemptBackendRegisterFallback(name, email, password);
        } else {
            String msg = exception != null && exception.getMessage() != null
                    ? exception.getMessage() : "Registration failed. Please try again.";
            showError(msg);
        }
    }

    private void attemptBackendRegisterFallback(String name, String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("displayName", name);
        body.put("email", email);
        body.put("password", password);

        apiService.register(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    handleAuthSuccess(response.body().getData());
                } else {
                    showError(getString(R.string.error_network));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                showError("Network connection error. Please check your internet connection.");
            }
        });
    }

    // ─── Google Sign-In ───────────────────────────────────────────────────────

    private void performGoogleSignIn() {
        if (googleSignInClient == null) {
            setupGoogleSignIn();
        }
        if (googleSignInClient == null) {
            showError("Unable to initialize Google Sign-In client.");
            return;
        }

        setLoading(true);
        // Sign out first to ensure the Google account chooser is always opened for the user
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                String idToken = account.getIdToken();
                if (idToken != null && !idToken.trim().isEmpty()) {
                    authenticateFirebaseWithGoogle(idToken, account);
                } else {
                    completeGoogleAuth(account);
                }
            } else {
                showError("Google account selection failed. Please try again.");
            }
        } catch (ApiException e) {
            showError("Google Sign-In failed (" + e.getStatusCode() + "): " + e.getLocalizedMessage());
        }
    }

    private void authenticateFirebaseWithGoogle(String idToken, GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser firebaseUser = task.getResult().getUser();
                        if (firebaseUser != null) {
                            String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : account.getEmail();
                            String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : account.getDisplayName();
                            String uid = firebaseUser.getUid();

                            firebaseUser.getIdToken(false).addOnSuccessListener(tokenResult -> {
                                TokenManager.saveToken(tokenResult.getToken(), "firebase-refresh-token");
                                TokenManager.saveUserInfo(uid, email, name);
                                syncBackendGoogleSignIn(idToken, uid, email, name);
                                onAuthComplete(name);
                            }).addOnFailureListener(e -> {
                                TokenManager.saveToken("firebase-token-" + System.currentTimeMillis(), "firebase-refresh-token");
                                TokenManager.saveUserInfo(uid, email, name);
                                onAuthComplete(name);
                            });
                            return;
                        }
                    }
                    // Fallback to Google Account profile directly
                    completeGoogleAuth(account);
                });
    }

    private void completeGoogleAuth(GoogleSignInAccount account) {
        String email = account.getEmail();
        String name = account.getDisplayName() != null ? account.getDisplayName() :
                (email != null && email.contains("@") ? email.substring(0, email.indexOf("@")) : "Google User");
        String uid = account.getId() != null ? account.getId() : "google-" + System.currentTimeMillis();

        TokenManager.saveToken("google-token-" + System.currentTimeMillis(), "google-refresh-token");
        TokenManager.saveUserInfo(uid, email, name);
        syncBackendGoogleSignIn(account.getIdToken(), uid, email, name);
        onAuthComplete(name);
    }

    // ─── Forgot Password ──────────────────────────────────────────────────────

    private void showForgotPasswordDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Widget_SmartExpense_Dialog);
        BottomSheetForgotPasswordBinding sheetBinding = BottomSheetForgotPasswordBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        String currentEmail = binding.etEmail.getText().toString().trim();
        if (!currentEmail.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            sheetBinding.etResetEmail.setText(currentEmail);
        }

        sheetBinding.btnCloseForgot.setOnClickListener(v -> dialog.dismiss());

        sheetBinding.etResetEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                sheetBinding.tilResetEmail.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sheetBinding.btnSendReset.setOnClickListener(v -> {
            String email = sheetBinding.etResetEmail.getText().toString().trim();
            if (email.isEmpty()) {
                sheetBinding.tilResetEmail.setError(getString(R.string.error_required));
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sheetBinding.tilResetEmail.setError(getString(R.string.error_invalid_email));
                return;
            }

            sheetBinding.progressForgot.setVisibility(View.VISIBLE);
            sheetBinding.btnSendReset.setEnabled(false);

            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener(this, task -> {
                        sheetBinding.progressForgot.setVisibility(View.GONE);
                        sheetBinding.btnSendReset.setEnabled(true);
                        if (task.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(LoginActivity.this,
                                    "Password reset link sent to " + email + ". Please check your inbox or spam folder.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Exception e = task.getException();
                            if (e instanceof FirebaseAuthInvalidUserException) {
                                sheetBinding.tilResetEmail.setError("No account found with this email.");
                            } else {
                                sheetBinding.tilResetEmail.setError(e != null && e.getMessage() != null
                                        ? e.getMessage() : "Failed to send reset email.");
                            }
                        }
                    });
        });

        dialog.show();
    }

    // ─── Demo Mode ────────────────────────────────────────────────────────────

    private void enterDemoMode() {
        TokenManager.saveToken("demo-token-" + System.currentTimeMillis(), "demo-refresh-token");
        TokenManager.saveUserInfo("demo-user", "demo@smartexpense.app", "Demo User");
        OfflineMockStorage.seedDemoDataIfEmpty();
        Toast.makeText(this, "Welcome to Demo Mode! 🎉", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void handleAuthSuccess(Map<String, Object> data) {
        if (data == null) return;
        String token = (String) data.get("accessToken");
        String refreshToken = (String) data.get("refreshToken");
        String userId = (String) data.get("userId");
        String email = (String) data.get("email");
        String displayName = (String) data.get("displayName");

        TokenManager.saveToken(token, refreshToken);
        TokenManager.saveUserInfo(userId, email, displayName);
        onAuthComplete(displayName);
    }

    private void onAuthComplete(String displayName) {
        Toast.makeText(this, "Welcome " + (displayName != null ? displayName : "") + "! 🎉", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void syncBackendEmailLogin(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        apiService.login(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {}
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
        });
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

    private void syncBackendGoogleSignIn(String idToken, String uid, String email, String name) {
        if (idToken == null || idToken.isEmpty()) return;
        Map<String, String> body = new HashMap<>();
        body.put("idToken", idToken);
        apiService.googleSignIn(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {}
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.btnGoogleSignIn.setEnabled(!loading);
        binding.btnDemoMode.setEnabled(!loading);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
