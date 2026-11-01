package com.smartexpense.android.ui.budget;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.FragmentBudgetBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.ui.expenses.ExpenseAdapter;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Budget Fragment — shows monthly overall budget progress and an interactive category
 * budget setup with real-time statistics, warnings, and user-isolated limits.
 */
public class BudgetFragment extends Fragment {

    private FragmentBudgetBinding binding;
    private ApiService apiService;
    private int currentMonth, currentYear;
    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("en", "IN"));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        apiService = ApiClient.createService(ApiService.class);

        LocalDateTime now = LocalDateTime.now();
        currentMonth = now.getMonthValue();
        currentYear = now.getYear();

        loadBudget();

        binding.btnSetBudget.setOnClickListener(v -> showSetOverallBudgetDialog());
    }

    private void loadBudget() {
        if (!isAdded() || binding == null) return;
        showLoading(true);

        // Load isolated budget and monthly expenses from storage
        Budget localBudget = OfflineMockStorage.getBudget(currentMonth, currentYear);
        updateUI(localBudget);

        apiService.getBudget(currentMonth, currentYear)
                .enqueue(new Callback<ApiResponse<Budget>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Budget>> call,
                                           Response<ApiResponse<Budget>> response) {
                        if (!isAdded() || binding == null) return;
                        showLoading(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess() && response.body().getData() != null) {
                            updateUI(response.body().getData());
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Budget>> call, Throwable t) {
                        if (!isAdded() || binding == null) return;
                        showLoading(false);
                    }
                });
    }

    private void updateUI(Budget budget) {
        if (!isAdded() || binding == null || budget == null) return;

        // 1. Overall Progress Card (Kept exactly as requested)
        double total = budget.getTotalBudget();
        double spent = budget.getTotalSpent();
        double remaining = total - spent;
        double pct = total > 0 ? (spent / total) * 100 : 0;

        binding.tvTotalBudget.setText(String.format("₹%s", currencyFormat.format(total)));
        binding.tvSpent.setText(String.format("₹%s", currencyFormat.format(spent)));
        binding.tvRemaining.setText(String.format("₹%s", currencyFormat.format(Math.max(0, remaining))));
        binding.progressBudget.setProgress((int) Math.min(100, Math.round(pct)));
        binding.tvProgressPercent.setText(String.format(Locale.US, "%.0f%% used", pct));

        if (pct >= 90) {
            binding.tvProgressPercent.setTextColor(
                    getResources().getColor(R.color.error, null));
            binding.cardBudgetWarning.setVisibility(View.VISIBLE);
            binding.tvBudgetWarningText.setText(String.format(Locale.US,
                    "Warning: You have used %.0f%% of your monthly budget.", pct));
        } else if (pct >= 70) {
            binding.tvProgressPercent.setTextColor(
                    getResources().getColor(R.color.warning, null));
            binding.cardBudgetWarning.setVisibility(View.GONE);
        } else {
            binding.tvProgressPercent.setTextColor(
                    getResources().getColor(R.color.primary, null));
            binding.cardBudgetWarning.setVisibility(View.GONE);
        }

        // 2. Interactive Category Budgets Setup
        updateCategoryBudgetsUI(budget);
    }

    private void updateCategoryBudgetsUI(Budget budget) {
        if (!isAdded() || binding == null) return;
        binding.layoutCategoryBudgets.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        Map<String, Double> categoryLimits = budget.getCategoryBudgets();
        if (categoryLimits == null || categoryLimits.isEmpty()) {
            categoryLimits = OfflineMockStorage.getCategoryLimits(
                    OfflineMockStorage.resolveCurrentUserId(), budget.getTotalBudget());
        }

        // Get actual user spending per category for this month
        Map<String, Object> summary = OfflineMockStorage.getMonthlySummary(currentMonth, currentYear);
        @SuppressWarnings("unchecked")
        Map<String, Double> actualCategorySpent = (Map<String, Double>) summary.get("categoryTotals");
        if (actualCategorySpent == null) actualCategorySpent = new HashMap<>();

        for (Map.Entry<String, Double> entry : categoryLimits.entrySet()) {
            String category = entry.getKey();
            double limit = entry.getValue();
            double categorySpent = actualCategorySpent.getOrDefault(category, 0.0);
            double categoryRemaining = limit - categorySpent;
            double categoryPct = limit > 0 ? (categorySpent / limit) * 100 : 0;

            View cardView = inflater.inflate(R.layout.item_category_budget, binding.layoutCategoryBudgets, false);

            TextView tvEmoji = cardView.findViewById(R.id.tv_cat_emoji);
            TextView tvName = cardView.findViewById(R.id.tv_cat_name);
            TextView tvLimit = cardView.findViewById(R.id.tv_cat_limit);
            TextView tvSpent = cardView.findViewById(R.id.tv_cat_spent);
            TextView tvRemaining = cardView.findViewById(R.id.tv_cat_remaining);
            TextView tvPercent = cardView.findViewById(R.id.tv_cat_percent);
            TextView tvWarning = cardView.findViewById(R.id.tv_cat_warning);
            ProgressBar pbProgress = cardView.findViewById(R.id.pb_cat_progress);
            MaterialButton btnEdit = cardView.findViewById(R.id.btn_edit_limit);

            tvEmoji.setText(ExpenseAdapter.getCategoryEmoji(category));
            tvName.setText(category);
            tvLimit.setText("₹" + currencyFormat.format(limit));
            tvSpent.setText("₹" + currencyFormat.format(categorySpent));

            if (categoryRemaining >= 0) {
                tvRemaining.setText("₹" + currencyFormat.format(categoryRemaining));
                tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.success));
            } else {
                tvRemaining.setText("-₹" + currencyFormat.format(Math.abs(categoryRemaining)));
                tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));
            }

            pbProgress.setProgress((int) Math.min(100, Math.round(categoryPct)));
            tvPercent.setText(String.format(Locale.US, "%.0f%%", categoryPct));

            // Warning indicators when approaching or exceeding limit
            if (categoryPct >= 100) {
                int errColor = ContextCompat.getColor(requireContext(), R.color.error);
                pbProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(errColor));
                tvPercent.setTextColor(errColor);
                tvWarning.setVisibility(View.VISIBLE);
                tvWarning.setText("🚨 Over limit by ₹" + currencyFormat.format(Math.abs(categoryRemaining)) + "!");
                tvWarning.setTextColor(errColor);
            } else if (categoryPct >= 85) {
                int warnColor = ContextCompat.getColor(requireContext(), R.color.warning);
                pbProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(warnColor));
                tvPercent.setTextColor(warnColor);
                tvWarning.setVisibility(View.VISIBLE);
                tvWarning.setText(String.format(Locale.US, "⚠️ Approaching limit (%.0f%% used)", categoryPct));
                tvWarning.setTextColor(warnColor);
            } else {
                int priColor = ContextCompat.getColor(requireContext(), R.color.primary);
                pbProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(priColor));
                tvPercent.setTextColor(priColor);
                tvWarning.setVisibility(View.GONE);
            }

            // Edit Limit button action
            btnEdit.setOnClickListener(v -> showEditCategoryLimitDialog(category, limit, categorySpent));
            cardView.setOnClickListener(v -> showEditCategoryLimitDialog(category, limit, categorySpent));

            binding.layoutCategoryBudgets.addView(cardView);
        }
    }

    private void showEditCategoryLimitDialog(String category, double currentLimit, double currentSpent) {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(ExpenseAdapter.getCategoryEmoji(category) + " " + category + " Budget Limit");
        builder.setMessage("Current monthly spent: ₹" + currencyFormat.format(currentSpent));

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_budget, null);
        EditText etAmount = dialogView.findViewById(R.id.et_budget_amount);
        etAmount.setText(String.valueOf((int) currentLimit));
        etAmount.setHint("Monthly limit in ₹");

        builder.setView(dialogView);
        builder.setPositiveButton("Save Limit", (dialog, which) -> {
            String amtStr = etAmount.getText().toString().trim();
            if (!amtStr.isEmpty()) {
                try {
                    double newLimit = Double.parseDouble(amtStr);
                    saveCategoryLimit(category, newLimit);
                } catch (NumberFormatException ignored) {}
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveCategoryLimit(String category, double newLimit) {
        // Save isolated to current user account in storage
        Budget updated = OfflineMockStorage.saveCategoryLimit(category, newLimit, currentMonth, currentYear);
        updateUI(updated);
        if (getContext() != null) {
            Toast.makeText(getContext(), category + " limit set to ₹" + currencyFormat.format(newLimit), Toast.LENGTH_SHORT).show();
        }

        // Sync with API
        Map<String, Object> body = new HashMap<>();
        body.put("totalBudget", updated.getTotalBudget());
        body.put("month", currentMonth);
        body.put("year", currentYear);
        apiService.saveBudget(body).enqueue(new Callback<ApiResponse<Budget>>() {
            @Override public void onResponse(Call<ApiResponse<Budget>> call, Response<ApiResponse<Budget>> response) {}
            @Override public void onFailure(Call<ApiResponse<Budget>> call, Throwable t) {}
        });
    }

    private void showSetOverallBudgetDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Set Monthly Total Budget");

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_budget, null);
        EditText etAmount = dialogView.findViewById(R.id.et_budget_amount);
        builder.setView(dialogView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String amtStr = etAmount.getText().toString().trim();
            if (!amtStr.isEmpty()) {
                try {
                    double amount = Double.parseDouble(amtStr);
                    Budget b = OfflineMockStorage.saveBudget(amount, currentMonth, currentYear);
                    updateUI(b);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Total budget saved: ₹" + currencyFormat.format(amount), Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException ignored) {}
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showLoading(boolean loading) {
        if (!isAdded() || binding == null) return;
        binding.shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.contentLayout.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading) binding.shimmerLayout.startShimmer();
        else binding.shimmerLayout.stopShimmer();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBudget();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
