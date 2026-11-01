package com.smartexpense.android.ui.dashboard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.databinding.FragmentDashboardBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.ui.expenses.AddExpenseActivity;
import com.smartexpense.android.ui.expenses.ExpenseAdapter;
import com.smartexpense.android.util.TokenManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dashboard Fragment — main screen showing:
 * - Total monthly expenses with animated budget ring
 * - Recent transactions list
 * - Category pie chart
 * - AI financial insights
 * - Budget progress
 * - Expense prediction card
 */
public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private ApiService apiService;
    private ExpenseAdapter recentAdapter;

    // Category colors matching our design system
    private static final int[] CATEGORY_COLORS = {
            Color.parseColor("#FF5722"), // Food
            Color.parseColor("#4CAF50"), // Grocery
            Color.parseColor("#9C27B0"), // Shopping
            Color.parseColor("#2196F3"), // Travel
            Color.parseColor("#F44336"), // Medical
            Color.parseColor("#FF9800"), // Education
            Color.parseColor("#E91E63"), // Entertainment
            Color.parseColor("#607D8B"), // Bills
            Color.parseColor("#795548"), // Fuel
            Color.parseColor("#9E9E9E")  // Others
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        apiService = ApiClient.createService(ApiService.class);
        setupRecyclerView();
        setupActions();
        setupPieChart();
        setupHeaderGreeting();
        loadDashboardData();
    }

    private void setupHeaderGreeting() {
        if (binding == null) return;
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        String rawName = null;
        try {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                if (firebaseUser.getDisplayName() != null && !firebaseUser.getDisplayName().trim().isEmpty()) {
                    rawName = firebaseUser.getDisplayName();
                } else if (firebaseUser.getEmail() != null && !firebaseUser.getEmail().trim().isEmpty()) {
                    rawName = firebaseUser.getEmail();
                }
            }
        } catch (Exception ignored) {}

        if (rawName == null || rawName.trim().isEmpty() || "null".equalsIgnoreCase(rawName)) {
            String tokenName = TokenManager.getUserName();
            if (tokenName != null && !tokenName.trim().isEmpty() && !"null".equalsIgnoreCase(tokenName) && !"User".equalsIgnoreCase(tokenName)) {
                rawName = tokenName;
            } else {
                String tokenEmail = TokenManager.getUserEmail();
                if (tokenEmail != null && !tokenEmail.trim().isEmpty()) {
                    rawName = tokenEmail;
                }
            }
        }

        String greeting = buildPersonalizedGreeting(hour, rawName);
        binding.tvGreeting.setText(greeting);
        if (binding.tvUserName != null && rawName != null) {
            binding.tvUserName.setText(rawName);
        }

        java.time.format.DateTimeFormatter monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy");
        binding.tvMonthSubtitle.setText(now.format(monthFmt) + " spending summary");
    }

    public static String buildPersonalizedGreeting(int hour, String rawName) {
        String timePrefix;
        if (hour < 12) {
            timePrefix = "Good Morning";
        } else if (hour < 18) {
            timePrefix = "Good Afternoon";
        } else {
            timePrefix = "Good Evening";
        }

        String displayName = formatDisplayName(rawName);
        if (displayName != null && !displayName.isEmpty()) {
            return timePrefix + ", " + displayName + " 👋";
        } else {
            return timePrefix + " 👋";
        }
    }

    public static String formatDisplayName(String rawName) {
        if (rawName == null) return null;
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "user".equalsIgnoreCase(trimmed) || "demo-user".equalsIgnoreCase(trimmed)) {
            return null;
        }

        // If it's an email address, extract username prefix
        if (trimmed.contains("@")) {
            trimmed = trimmed.substring(0, trimmed.indexOf("@")).trim();
        }

        // Extract first name (e.g. "Hariharan S" -> "Hariharan")
        String[] parts = trimmed.split("[\\s._-]+");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            trimmed = parts[0];
        }

        if (trimmed.isEmpty()) return null;

        // Capitalize first letter if all lowercase
        return Character.toUpperCase(trimmed.charAt(0)) + (trimmed.length() > 1 ? trimmed.substring(1) : "");
    }

    private void setupActions() {
        binding.fabAddExpense.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddExpenseActivity.class)));

        binding.btnAddExpenseTop.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddExpenseActivity.class)));

        binding.btnVoiceEntry.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.smartexpense.android.ui.voice.VoiceEntryActivity.class)));

        binding.btnScanReceipt.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.smartexpense.android.ui.scanner.OcrScannerActivity.class)));

        binding.tvViewAll.setOnClickListener(v -> {
            if (getActivity() instanceof com.smartexpense.android.ui.main.MainActivity) {
                ((com.smartexpense.android.ui.main.MainActivity) getActivity())
                        .navigateToDestination(R.id.expensesFragment);
            } else {
                try {
                    androidx.navigation.fragment.NavHostFragment.findNavController(this)
                            .navigate(R.id.expensesFragment);
                } catch (Exception ignored) {}
            }
        });
    }

    private void setupRecyclerView() {
        recentAdapter = new ExpenseAdapter(new ArrayList<>(), expense -> {
            // Open expense detail
        });
        binding.rvRecentTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentTransactions.setAdapter(recentAdapter);
    }

    private void setupPieChart() {
        if (binding == null) return;
        PieChart chart = binding.pieChart;
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(60f);
        chart.setTransparentCircleRadius(65f);
        chart.setDrawCenterText(true);
        chart.setCenterText("Spending\nBreakdown");
        chart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        chart.setCenterTextSize(12f);
        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        chart.post(() -> {
            if (binding != null && binding.pieChart != null) {
                binding.pieChart.animateY(1200);
            }
        });
    }

    private void loadDashboardData() {
        if (!isAdded() || binding == null) return;
        showLoading(true);

        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        int year = now.getYear();

        // Load monthly summary
        apiService.getMonthlySummary(month, year).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    updateSummaryUI(response.body().getData());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                showError();
            }
        });

        // Load recent transactions
        apiService.getExpenses(5).enqueue(new Callback<ApiResponse<List<Expense>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Expense>>> call,
                                   Response<ApiResponse<List<Expense>>> response) {
                if (!isAdded() || binding == null) return;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    List<Expense> expenses = response.body().getData();
                    if (expenses != null) {
                        recentAdapter.updateExpenses(expenses);
                        if (binding != null && binding.tvNoExpenses != null) {
                            binding.tvNoExpenses.setVisibility(
                                    expenses.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Expense>>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                showLoading(false);
            }
        });

        // Load AI insights
        apiService.getInsights(month, year).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    updateInsightsUI(response.body().getData());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {}
        });
    }

    @SuppressWarnings("unchecked")
    private void updateSummaryUI(Map<String, Object> data) {
        if (data == null || !isAdded() || binding == null) return;

        double total = data.containsKey("total") ?
                ((Number) data.get("total")).doubleValue() : 0.0;
        binding.tvTotalExpenses.setText(String.format("₹%.0f", total));

        int count = data.containsKey("count") ?
                ((Number) data.get("count")).intValue() : 0;
        binding.tvTransactionCount.setText(String.valueOf(count));

        double avgPerDay = total / 30.0;
        binding.tvAvgPerDay.setText(String.format("₹%.0f", avgPerDay));

        String topCat = data.containsKey("topCategory") && data.get("topCategory") != null ?
                String.valueOf(data.get("topCategory")) : "—";
        binding.tvTopCategory.setText(topCat);

        // Update pie chart with category data
        Map<String, Object> categoryTotals =
                (Map<String, Object>) data.get("categoryTotals");
        if (categoryTotals != null && !categoryTotals.isEmpty()) {
            updatePieChart(categoryTotals, total);
        }
    }

    @SuppressWarnings("unchecked")
    private void updatePieChart(Map<String, Object> categoryTotals, double total) {
        if (!isAdded() || binding == null) return;
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int colorIdx = 0;

        for (Map.Entry<String, Object> entry : categoryTotals.entrySet()) {
            double amount = ((Number) entry.getValue()).doubleValue();
            if (amount > 0) {
                entries.add(new PieEntry((float) (amount / total * 100), entry.getKey()));
                colors.add(CATEGORY_COLORS[colorIdx % CATEGORY_COLORS.length]);
                colorIdx++;
            }
        }

        if (entries.isEmpty()) return;

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData pieData = new PieData(dataSet);
        binding.pieChart.post(() -> {
            if (binding != null && binding.pieChart != null) {
                binding.pieChart.setData(pieData);
                binding.pieChart.invalidate();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void updateInsightsUI(Map<String, Object> data) {
        if (data == null || !isAdded() || binding == null) return;

        List<String> suggestions = (List<String>) data.get("suggestions");
        List<String> anomalies = (List<String>) data.get("anomalies");

        StringBuilder insightText = new StringBuilder();
        if (anomalies != null && !anomalies.isEmpty()) {
            insightText.append(anomalies.get(0)).append("\n\n");
        }
        if (suggestions != null && !suggestions.isEmpty()) {
            insightText.append(suggestions.get(0));
        }

        if (insightText.length() > 0) {
            binding.tvAiInsight.setText(insightText.toString());
            binding.cardAiInsights.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean loading) {
        if (!isAdded() || binding == null) return;
        binding.shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.contentLayout.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading) binding.shimmerLayout.startShimmer();
        else binding.shimmerLayout.stopShimmer();
    }

    private void showError() {
        if (!isAdded() || binding == null) return;
        showLoading(false);
    }

    private final android.content.BroadcastReceiver expenseSavedReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            loadDashboardData();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        setupHeaderGreeting();
        loadDashboardData();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            androidx.core.content.ContextCompat.registerReceiver(
                    requireContext(), expenseSavedReceiver,
                    new android.content.IntentFilter("com.smartexpense.android.ACTION_EXPENSE_SAVED"),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getContext() != null) {
            try {
                requireContext().unregisterReceiver(expenseSavedReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
