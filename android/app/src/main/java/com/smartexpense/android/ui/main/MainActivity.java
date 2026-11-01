package com.smartexpense.android.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.smartexpense.android.R;
import com.smartexpense.android.databinding.ActivityMainBinding;
import com.smartexpense.android.ui.expenses.AddExpenseActivity;
import com.smartexpense.android.util.NotificationHelper;

/**
 * Main activity — hosts WhatsApp-style horizontal swipe navigation
 * across 5 main pages: Dashboard, Expenses, Budget, Reports, Settings.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Create notification channels
        NotificationHelper.createChannels(this);

        setupNavigation();
        setupFab();
        setupBackNavigation();
        handleDeepLinkIntent(getIntent());
        checkAndRequestSmsPermissions();
    }

    private void checkAndRequestSmsPermissions() {
        androidx.activity.result.ActivityResultLauncher<String[]> smsPermissionLauncher =
                registerForActivityResult(
                        new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                        result -> {
                            boolean receiveGranted = Boolean.TRUE.equals(result.get(android.Manifest.permission.RECEIVE_SMS));
                            if (receiveGranted) {
                                getSharedPreferences("smart_expense_prefs", MODE_PRIVATE)
                                        .edit().putBoolean("sms_tracking_enabled", true).apply();
                            }
                        });

        boolean hasReceiveSms = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        java.util.List<String> neededPermissions = new java.util.ArrayList<>();
        if (!hasReceiveSms) {
            neededPermissions.add(android.Manifest.permission.RECEIVE_SMS);
            neededPermissions.add(android.Manifest.permission.READ_SMS);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            boolean hasPostNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (!hasPostNotif) {
                neededPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!neededPermissions.isEmpty()) {
            smsPermissionLauncher.launch(neededPermissions.toArray(new String[0]));
        } else {
            android.content.SharedPreferences prefs = getSharedPreferences("smart_expense_prefs", MODE_PRIVATE);
            if (!prefs.contains("sms_tracking_enabled")) {
                prefs.edit().putBoolean("sms_tracking_enabled", true).apply();
            }
        }
    }

    private void setupNavigation() {
        pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        // Preserve all 5 pages in memory to maintain state, scroll offsets, filters, and forms
        binding.viewPager.setOffscreenPageLimit(MainPagerAdapter.NUM_PAGES - 1);

        // 1. Swiping ViewPager2 updates the selected BottomNavigationView item
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                int targetMenuId = getMenuItemIdForPosition(position);
                if (targetMenuId != -1 && binding.bottomNavigationView.getSelectedItemId() != targetMenuId) {
                    binding.bottomNavigationView.setSelectedItemId(targetMenuId);
                }
            }
        });

        // 2. Tapping BottomNavigationView item smoothly slides ViewPager2 to that page
        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int targetPosition = getPositionForMenuItemId(item.getItemId());
            if (targetPosition != -1 && binding.viewPager.getCurrentItem() != targetPosition) {
                binding.viewPager.setCurrentItem(targetPosition, true);
            }
            return true;
        });

        binding.bottomNavigationView.setOnItemReselectedListener(item -> {
            // Keep current page without reloading or destroying user state
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If on any tab other than Dashboard, back returns to Dashboard (like WhatsApp)
                if (binding != null && binding.viewPager != null
                        && binding.viewPager.getCurrentItem() != MainPagerAdapter.POSITION_DASHBOARD) {
                    binding.viewPager.setCurrentItem(MainPagerAdapter.POSITION_DASHBOARD, true);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    public void navigateToDestination(int destinationId) {
        int pos = getPositionForMenuItemId(destinationId);
        if (pos != -1 && binding != null && binding.viewPager != null) {
            binding.viewPager.setCurrentItem(pos, true);
        }
    }

    public void navigateToTab(int position) {
        if (position >= 0 && position < MainPagerAdapter.NUM_PAGES && binding != null && binding.viewPager != null) {
            binding.viewPager.setCurrentItem(position, true);
        }
    }

    public static int getMenuItemIdForPosition(int position) {
        switch (position) {
            case MainPagerAdapter.POSITION_DASHBOARD: return R.id.dashboardFragment;
            case MainPagerAdapter.POSITION_EXPENSES:  return R.id.expensesFragment;
            case MainPagerAdapter.POSITION_BUDGET:    return R.id.budgetFragment;
            case MainPagerAdapter.POSITION_REPORTS:   return R.id.reportsFragment;
            case MainPagerAdapter.POSITION_SETTINGS:  return R.id.settingsFragment;
            default: return -1;
        }
    }

    public static int getPositionForMenuItemId(int itemId) {
        if (itemId == R.id.dashboardFragment) return MainPagerAdapter.POSITION_DASHBOARD;
        if (itemId == R.id.expensesFragment)  return MainPagerAdapter.POSITION_EXPENSES;
        if (itemId == R.id.budgetFragment)    return MainPagerAdapter.POSITION_BUDGET;
        if (itemId == R.id.reportsFragment)   return MainPagerAdapter.POSITION_REPORTS;
        if (itemId == R.id.settingsFragment)  return MainPagerAdapter.POSITION_SETTINGS;
        return -1;
    }

    private void setupFab() {
        binding.fabAddExpense.setOnClickListener(v ->
                startActivity(new Intent(this, AddExpenseActivity.class)));

        binding.companionShorekeeper.setOnCompanionClickListener(() ->
                com.smartexpense.android.ui.chat.AiChatbotBottomSheet.show(getSupportFragmentManager()));
    }

    private void handleDeepLinkIntent(Intent intent) {
        if (intent == null) return;
        String navigate = intent.getStringExtra("navigate");
        if ("budget".equals(navigate)) {
            navigateToDestination(R.id.budgetFragment);
        } else if ("expenses".equals(navigate)) {
            navigateToDestination(R.id.expensesFragment);
        } else if ("reports".equals(navigate)) {
            navigateToDestination(R.id.reportsFragment);
        } else if ("settings".equals(navigate)) {
            navigateToDestination(R.id.settingsFragment);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLinkIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (binding != null && binding.companionShorekeeper != null) {
            binding.companionShorekeeper.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && binding.companionShorekeeper != null) {
            binding.companionShorekeeper.resume();
        }
    }
}
