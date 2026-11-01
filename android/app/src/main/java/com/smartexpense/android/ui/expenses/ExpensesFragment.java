package com.smartexpense.android.ui.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.databinding.FragmentExpensesBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.ui.scanner.OcrScannerActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Expenses Fragment — paginated expense list with search, filter, and OCR scanning.
 */
public class ExpensesFragment extends Fragment {

    private FragmentExpensesBinding binding;
    private ApiService apiService;
    private ExpenseAdapter adapter;
    private List<Expense> allExpenses = new ArrayList<>();
    private int currentMonth, currentYear;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentExpensesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        apiService = ApiClient.createService(ApiService.class);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        currentMonth = now.getMonthValue();
        currentYear = now.getYear();

        setupRecyclerView();
        setupSearch();
        setupFilterChips();
        loadExpenses();

        binding.btnScanOcr.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OcrScannerActivity.class)));

        binding.btnVoiceEntry.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.smartexpense.android.ui.voice.VoiceEntryActivity.class)));

        binding.btnAddExpense.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddExpenseActivity.class)));
    }

    private void setupRecyclerView() {
        adapter = new ExpenseAdapter(new ArrayList<>(), expense -> {
            // Open Expense Detail
        });
        binding.rvExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvExpenses.setAdapter(adapter);
    }

    private String selectedCategory = null;

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyCurrentFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        // Prevent ViewPager2 from stealing horizontal drags when scrolling the category chips
        if (binding.hsvCategoryChips != null) {
            binding.hsvCategoryChips.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }

        binding.chipAll.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory(null); });
        binding.chipFood.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Food"); });
        binding.chipGrocery.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Grocery"); });
        binding.chipShopping.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Shopping"); });
        binding.chipTravel.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Travel"); });
        binding.chipBills.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Bills"); });
        binding.chipMedical.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Medical"); });
        binding.chipEntertainment.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Entertainment"); });
        binding.chipOthers.setOnCheckedChangeListener((btn, checked) -> { if (checked) filterByCategory("Others"); });
    }

    private void updateCountAndTotal(List<Expense> list) {
        if (!isAdded() || binding == null || list == null) return;
        double total = 0;
        for (Expense e : list) total += e.getAmount();
        binding.tvCount.setText(String.format("Showing %d expenses · Total: ₹%.0f", list.size(), total));
        if (binding.tvNoExpenses != null) {
            binding.tvNoExpenses.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void loadExpenses() {
        if (!isAdded() || binding == null) return;
        showLoading(true);
        apiService.getMonthlyExpenses(currentMonth, currentYear)
                .enqueue(new Callback<ApiResponse<List<Expense>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Expense>>> call,
                                   Response<ApiResponse<List<Expense>>> response) {
                if (!isAdded() || binding == null) return;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    allExpenses = response.body().getData();
                    if (allExpenses == null) allExpenses = new ArrayList<>();
                    applyCurrentFilters();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Expense>>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                showLoading(false);
            }
        });
    }

    private void filterByCategory(String category) {
        this.selectedCategory = category;
        applyCurrentFilters();
    }

    private void applyCurrentFilters() {
        if (adapter == null) return;
        String query = "";
        if (binding != null && binding.etSearch.getText() != null) {
            query = binding.etSearch.getText().toString().trim().toLowerCase();
        }

        List<Expense> filtered = new ArrayList<>();
        if (allExpenses != null) {
            for (Expense e : allExpenses) {
                if (e == null) continue;
                if (selectedCategory != null && !selectedCategory.equals(e.getCategory())) {
                    continue;
                }
                if (!query.isEmpty()) {
                    boolean matches = (e.getMerchantName() != null && e.getMerchantName().toLowerCase().contains(query))
                            || (e.getCategory() != null && e.getCategory().toLowerCase().contains(query))
                            || (e.getDescription() != null && e.getDescription().toLowerCase().contains(query));
                    if (!matches) continue;
                }
                filtered.add(e);
            }
        }
        adapter.updateExpenses(filtered);
        updateCountAndTotal(filtered);
    }

    private void showLoading(boolean loading) {
        if (!isAdded() || binding == null) return;
        binding.shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.rvExpenses.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (loading) binding.shimmerLayout.startShimmer();
        else binding.shimmerLayout.stopShimmer();
    }

    private final android.content.BroadcastReceiver expenseSavedReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            loadExpenses();
        }
    };

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
    public void onResume() {
        super.onResume();
        loadExpenses();
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
