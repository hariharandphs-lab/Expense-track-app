package com.smartexpense.android.ui.expenses;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.databinding.ActivityAddExpenseBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.ui.scanner.OcrScannerActivity;
import com.smartexpense.android.ui.voice.VoiceEntryActivity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Add/Edit Expense Activity — supports manual, OCR, and voice input modes.
 * Pre-fills fields when launched from SMS, OCR, or Voice activities.
 */
public class AddExpenseActivity extends AppCompatActivity {

    private ActivityAddExpenseBinding binding;
    private ApiService apiService;

    private static final String[] CATEGORIES = {
            "Food", "Grocery", "Shopping", "Travel", "Medical",
            "Education", "Entertainment", "Bills", "Fuel", "Transfers", "Others"
    };

    private static final String[] PAYMENT_METHODS = {
            "UPI", "Card", "Cash", "Net Banking", "Wallet"
    };

    private String expenseId; // non-null when editing
    private boolean categoryOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddExpenseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.createService(ApiService.class);

        setupDropdowns();
        setupToolbar();
        setupInputTabs();
        prefillFromIntent();

        binding.btnSave.setOnClickListener(v -> saveExpense());
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            boolean isEdit = expenseId != null;
            getSupportActionBar().setTitle(isEdit ?
                    getString(R.string.edit_expense) : getString(R.string.add_expense));
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupDropdowns() {
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this, R.layout.item_dropdown, CATEGORIES);
        ((AutoCompleteTextView) binding.tilCategory.getEditText()).setAdapter(categoryAdapter);

        ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(
                this, R.layout.item_dropdown, PAYMENT_METHODS);
        ((AutoCompleteTextView) binding.tilPaymentMethod.getEditText()).setAdapter(paymentAdapter);
    }

    private void setupInputTabs() {
        binding.tabManual.setOnClickListener(v -> {
            binding.tabManual.setSelected(true);
            binding.tabOcr.setSelected(false);
            binding.tabVoice.setSelected(false);
        });

        binding.tabOcr.setOnClickListener(v ->
                startActivity(new Intent(this, OcrScannerActivity.class)));

        binding.tabVoice.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceEntryActivity.class)));
    }

    private void prefillFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        expenseId = intent.getStringExtra("expenseId");
        categoryOnly = "CATEGORIZE".equals(intent.getStringExtra("mode"));
        if (categoryOnly && expenseId != null) loadExpenseForCategorization();

        String amount = intent.getStringExtra("amount");
        if (amount != null && !amount.isEmpty()) binding.etAmount.setText(amount);

        String merchant = intent.getStringExtra("merchant");
        if (merchant != null && !merchant.isEmpty()) binding.etMerchant.setText(merchant);

        String category = intent.getStringExtra("category");
        if (category != null && !category.isEmpty()) {
            ((AutoCompleteTextView) binding.tilCategory.getEditText()).setText(category, false);
        }

        String date = intent.getStringExtra("date");
        if (date != null && !date.isEmpty()) binding.etDate.setText(date);

        String source = intent.getStringExtra("source");
        if (source != null) {
            binding.tvSource.setText("Source: " + source);
            binding.tvSource.setVisibility(View.VISIBLE);
        }
    }

    private void loadExpenseForCategorization() {
        setLoading(true);
        apiService.getExpense(expenseId).enqueue(new Callback<ApiResponse<Expense>>() {
            @Override public void onResponse(Call<ApiResponse<Expense>> call,
                                            Response<ApiResponse<Expense>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                    setLoading(false);
                    Toast.makeText(AddExpenseActivity.this, "Could not load transaction details", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                Expense expense = response.body().getData();
                setLoading(false);
                binding.etAmount.setText(String.valueOf(expense.getAmount()));
                binding.etMerchant.setText(expense.getMerchantName());
                if (expense.getCategory() != null && !"Others".equals(expense.getCategory())) {
                    ((AutoCompleteTextView) binding.tilCategory.getEditText()).setText(expense.getCategory(), false);
                }
                binding.tvSource.setText("Confirm category or edit seller & item details to teach the app");
                binding.tvSource.setVisibility(View.VISIBLE);

                // If launched to clarify P2P transfer vs seller purchase
                boolean isP2PQuestion = getIntent().getBooleanExtra("isP2PQuestion", false)
                        || "Transfers".equalsIgnoreCase(expense.getCategory())
                        || "Others".equalsIgnoreCase(expense.getCategory());
                if (isP2PQuestion) {
                    new androidx.appcompat.app.AlertDialog.Builder(AddExpenseActivity.this)
                            .setTitle("Personal Transfer or Purchase?")
                            .setMessage("Was this payment of ₹" + ((int)expense.getAmount()) + " to \""
                                    + (expense.getMerchantName() != null ? expense.getMerchantName() : "Payee")
                                    + "\" a personal transfer or a purchase from a seller?")
                            .setPositiveButton("Purchase from Seller", (dialog, which) -> {
                                binding.tvSource.setText("Enter seller name, item purchased, and choose category:");
                                binding.etMerchant.requestFocus();
                            })
                            .setNegativeButton("Personal Transfer", (dialog, which) -> {
                                ((AutoCompleteTextView) binding.tilCategory.getEditText()).setText("Transfers", false);
                                saveExpense();
                            })
                            .show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Expense>> call, Throwable error) {
                setLoading(false);
                Toast.makeText(AddExpenseActivity.this, "Could not load transaction. Check your connection.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void saveExpense() {
        String amountStr = binding.etAmount.getText().toString().trim();
        String merchant = binding.etMerchant.getText().toString().trim();
        String category = ((AutoCompleteTextView) binding.tilCategory.getEditText())
                .getText().toString().trim();
        String paymentMethod = ((AutoCompleteTextView) binding.tilPaymentMethod.getEditText())
                .getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();

        if (!categoryOnly && amountStr.isEmpty()) {
            binding.tilAmount.setError(getString(R.string.error_required));
            return;
        }

        double amount = 0;
        if (!categoryOnly) {
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                binding.tilAmount.setError(getString(R.string.error_amount));
                return;
            }
        }

        if (category.isEmpty()) {
            binding.tilCategory.setError(getString(R.string.error_required));
            return;
        }

        // Always teach SmartCategorizer locally from user classification
        if (!merchant.isEmpty()) {
            com.smartexpense.android.util.SmartCategorizer.saveUserCorrection(
                    AddExpenseActivity.this, merchant, category, "Transfers".equalsIgnoreCase(category));
        }

        if (categoryOnly) {
            setLoading(true);
            com.smartexpense.android.api.OfflineMockStorage.updateExpenseCategory(expenseId, category);
            Map<String, String> body = new HashMap<>();
            body.put("category", category);
            apiService.updateExpenseCategory(expenseId, body).enqueue(new Callback<ApiResponse<Expense>>() {
                @Override public void onResponse(Call<ApiResponse<Expense>> call,
                                                Response<ApiResponse<Expense>> response) {
                    setLoading(false);
                    Toast.makeText(AddExpenseActivity.this, "Category saved! Future transactions from " + merchant + " will use " + category + ".", Toast.LENGTH_LONG).show();
                    finish();
                }
                @Override public void onFailure(Call<ApiResponse<Expense>> call, Throwable error) {
                    setLoading(false);
                    // Offline storage already updated
                    Toast.makeText(AddExpenseActivity.this, "Category saved locally for " + merchant + "!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            return;
        }

        setLoading(true);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("currency", "INR");
        body.put("merchantName", merchant.isEmpty() ? "Unknown" : merchant);
        body.put("category", category);
        body.put("paymentMethod", paymentMethod.isEmpty() ? "UPI" : paymentMethod);
        body.put("description", description);
        body.put("source", "MANUAL");
        String userId = com.smartexpense.android.util.TokenManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            userId = com.smartexpense.android.api.OfflineMockStorage.resolveCurrentUserId();
        }
        body.put("userId", userId);
        body.put("transactionDate", LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Call<ApiResponse<Expense>> call = expenseId != null ?
                apiService.updateExpense(expenseId, body) :
                apiService.createExpense(body);

        call.enqueue(new Callback<ApiResponse<Expense>>() {
            @Override
            public void onResponse(Call<ApiResponse<Expense>> c,
                                   Response<ApiResponse<Expense>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Toast.makeText(AddExpenseActivity.this,
                            expenseId != null ?
                                    getString(R.string.expense_updated) :
                                    getString(R.string.expense_added),
                            Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AddExpenseActivity.this,
                            getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Expense>> c, Throwable t) {
                setLoading(false);
                Toast.makeText(AddExpenseActivity.this,
                        getString(R.string.error_network), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }
}
