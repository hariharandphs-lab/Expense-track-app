package com.smartexpense.android.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;

import com.bumptech.glide.Glide;
import com.smartexpense.android.R;
import com.smartexpense.android.SmartExpenseApp;
import com.smartexpense.android.databinding.FragmentSettingsBinding;
import com.smartexpense.android.ui.auth.LoginActivity;
import com.smartexpense.android.util.TokenManager;

/**
 * Settings Fragment — theme toggle, language selection, SMS tracking toggle,
 * profile info, and logout.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SharedPreferences prefs;
    private ActivityResultLauncher<String[]> smsPermissionLauncher;

    private static final String[] LANGUAGE_CODES = {
            "en", "hi", "ta", "te", "ml", "kn", "bn", "mr", "gu", "pa", "ur"
    };
    private static final String[] LANGUAGE_NAMES = {
            "English", "हिंदी", "தமிழ்", "తెలుగు", "മലയാളം",
            "ಕನ್ನಡ", "বাংলা", "मराठी", "ગુજરાતી", "ਪੰਜਾਬੀ", "اردو"
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        smsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (binding == null) return;
                    boolean receiveGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECEIVE_SMS));
                    boolean readGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_SMS));
                    boolean granted = receiveGranted || readGranted;

                    if (prefs != null) {
                        prefs.edit().putBoolean("sms_tracking_enabled", true).apply();
                    }
                    updateSmsStatus();

                    Toast.makeText(requireContext(), granted
                                    ? "SMS permission granted. Transaction auto-detection is active."
                                    : "SMS permission was not granted. Automatic bank alert parsing is disabled.",
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = requireContext().getSharedPreferences("smart_expense_prefs",
                android.content.Context.MODE_PRIVATE);

        loadProfile();
        updateSmsStatus();
        setupThemeToggle();
        setupLanguageSelector();
        setupLogout();
        setupAppVersion();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSmsStatus();
    }

    private void loadProfile() {
        String name = TokenManager.getUserName();
        String email = TokenManager.getUserEmail();
        binding.tvUserName.setText(name != null ? name : "User");
        binding.tvUserEmail.setText(email != null ? email : "");
    }

    private void updateSmsStatus() {
        if (binding == null || getContext() == null) return;
        boolean receiveGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean readGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean hasPermission = receiveGranted && readGranted;

        // Keep SMS tracking enabled in prefs since it is a mandatory core app feature
        if (prefs != null) {
            prefs.edit().putBoolean("sms_tracking_enabled", true).apply();
        }

        if (hasPermission) {
            binding.tvSmsStatus.setText("● Active");
            binding.tvSmsStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success));
            binding.tvSmsStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.success_container));
            binding.tvSmsPermissionDesc.setText("SMS permissions granted. All incoming bank debit and UPI transaction alerts are automatically detected and categorized.");
            binding.btnGrantSmsPermission.setVisibility(View.GONE);
        } else {
            binding.tvSmsStatus.setText("● Permission Required");
            binding.tvSmsStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.warning));
            binding.tvSmsStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning_container));
            binding.tvSmsPermissionDesc.setText("Android SMS permissions are missing. Smart Expense Tracker requires SMS permissions (RECEIVE_SMS & READ_SMS) to detect and import your bank transactions automatically.");
            binding.btnGrantSmsPermission.setVisibility(View.VISIBLE);
            binding.btnGrantSmsPermission.setOnClickListener(v -> {
                smsPermissionLauncher.launch(new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                });
            });
        }
    }

    private void setupThemeToggle() {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        binding.switchDarkMode.setChecked(currentMode == AppCompatDelegate.MODE_NIGHT_YES);
        binding.switchDarkMode.setOnCheckedChangeListener((btn, checked) -> {
            AppCompatDelegate.setDefaultNightMode(
                    checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            prefs.edit().putBoolean("dark_mode", checked).apply();
        });
    }

    private void setupLanguageSelector() {
        String currentLang = prefs.getString("language", "en");
        int currentIdx = 0;
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equals(currentLang)) { currentIdx = i; break; }
        }

        binding.spinnerLanguage.setAdapter(
                new android.widget.ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_dropdown_item, LANGUAGE_NAMES));
        binding.spinnerLanguage.setSelection(currentIdx);

        binding.spinnerLanguage.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       View view, int position, long id) {
                String selectedCode = LANGUAGE_CODES[position];
                if (!selectedCode.equals(prefs.getString("language", "en"))) {
                    SmartExpenseApp.setLanguage(requireContext(), selectedCode);
                    Toast.makeText(requireContext(),
                            "Language changed. Restart app to apply.",
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupLogout() {
        binding.btnLogout.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        TokenManager.clear();
                        try {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                        } catch (Exception ignored) {}
                        com.smartexpense.android.api.OfflineMockStorage.clearInMemoryCache();
                        com.smartexpense.android.api.ApiClient.reset();
                        startActivity(new Intent(requireContext(), LoginActivity.class));
                        requireActivity().finishAffinity();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupAppVersion() {
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            binding.tvVersion.setText(getString(R.string.version, version));
        } catch (Exception ignored) {}
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
