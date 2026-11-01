package com.smartexpense.android.ui.expenses;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.smartexpense.android.R;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.SmartCategorizer;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for displaying expense list items.
 * Uses small category-specific icons/emojis (🍔 Food, 🛒 Grocery, 💊 Medical, 🚗 Travel, 🛍️ Shopping, ⚡ Bills).
 */
public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseVH> {

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
    }

    private List<Expense> expenses;
    private final OnExpenseClickListener listener;
    private final NumberFormat currencyFormat;

    // Category → emoji mapping
    public static String getCategoryEmoji(String category) {
        if (category == null) return "📦";
        switch (category.trim()) {
            case "Food": return "🍔";
            case "Grocery": return "🛒";
            case "Medical": return "💊";
            case "Travel": return "🚗";
            case "Shopping": return "🛍️";
            case "Bills": return "⚡";
            case "Fuel": return "⛽";
            case "Education": return "🎓";
            case "Entertainment": return "🎬";
            case "Transfers": return "💸";
            default: return "📦";
        }
    }

    // Category → color mapping
    private static final Map<String, String> CATEGORY_COLORS = new HashMap<String, String>() {{
        put("Food", "#FF5722"); put("Grocery", "#4CAF50"); put("Shopping", "#9C27B0");
        put("Travel", "#2196F3"); put("Medical", "#F44336"); put("Education", "#FF9800");
        put("Entertainment", "#E91E63"); put("Bills", "#607D8B"); put("Fuel", "#795548");
        put("Transfers", "#00897B"); put("Others", "#9E9E9E");
    }};

    public ExpenseAdapter(List<Expense> expenses, OnExpenseClickListener listener) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
        this.listener = listener;
        this.currencyFormat = NumberFormat.getInstance(new Locale("en", "IN"));
    }

    @NonNull
    @Override
    public ExpenseVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseVH holder, int position) {
        Expense expense = expenses.get(position);
        Context context = holder.itemView.getContext();

        // Merchant name
        holder.tvMerchant.setText(expense.getMerchantName() != null ?
                expense.getMerchantName() : "Unknown Merchant");

        // Category resolution — if category is null or "Others", use SmartCategorizer for intelligent detection
        String category = expense.getCategory();
        if (category == null || category.trim().isEmpty() || "Others".equalsIgnoreCase(category.trim())) {
            SmartCategorizer.CategoryResult smartCat =
                    SmartCategorizer.categorize(context, expense.getMerchantName(), null, expense.getDescription());
            if (smartCat != null && smartCat.category != null && !"Others".equalsIgnoreCase(smartCat.category)) {
                category = smartCat.category;
            } else {
                category = "Others";
            }
        }
        holder.tvCategory.setText(category);

        // Small category-specific icon / emoji
        holder.tvCategoryEmoji.setText(getCategoryEmoji(category));

        // Amount — error for debit, success for credit
        String amountStr = "₹" + currencyFormat.format(expense.getAmount());
        boolean isCredit = "CREDIT".equalsIgnoreCase(expense.getTransactionType());
        holder.tvAmount.setText(isCredit ? "+" + amountStr : "-" + amountStr);
        holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                isCredit ? R.color.success : R.color.error));

        // Date
        if (expense.getTransactionDate() != null && !expense.getTransactionDate().isEmpty()) {
            String date = expense.getTransactionDate();
            if (date.length() >= 10) {
                holder.tvDate.setText(date.substring(0, 10));
            }
        }

        // Category pill styling
        String colorHex = CATEGORY_COLORS.getOrDefault(category, "#898379");
        holder.tvCategory.setTextColor(Color.parseColor(colorHex));
        try {
            holder.tvCategory.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor(colorHex + "20")));
        } catch (Exception ignored) {}

        // Source badge (SMS, OCR, Voice, Manual)
        String source = expense.getSource();
        if ("SMS".equalsIgnoreCase(source)) {
            holder.tvSourceBadge.setText("SMS");
            holder.tvSourceBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.info));
            holder.tvSourceBadge.setVisibility(View.VISIBLE);
        } else if ("OCR".equalsIgnoreCase(source)) {
            holder.tvSourceBadge.setText("📷 OCR");
            holder.tvSourceBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.warning));
            holder.tvSourceBadge.setVisibility(View.VISIBLE);
        } else if ("VOICE".equalsIgnoreCase(source) || "VOICE_AI".equalsIgnoreCase(source)) {
            holder.tvSourceBadge.setText("🎤 Voice");
            holder.tvSourceBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary));
            holder.tvSourceBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvSourceBadge.setVisibility(View.GONE);
        }

        // Pending category indicator
        holder.ivPending.setVisibility(
                "PENDING_CATEGORY".equals(expense.getStatus()) ? View.VISIBLE : View.GONE);

        final String finalCategory = category;
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onExpenseClick(expense);
            }
            showTransactionDetailDialog(context, expense, finalCategory);
        });
    }

    private void showTransactionDetailDialog(Context context, Expense expense, String displayCategory) {
        if (context == null) return;
        boolean isCredit = "CREDIT".equalsIgnoreCase(expense.getTransactionType());
        String amountText = (isCredit ? "+" : "-") + "₹" + currencyFormat.format(expense.getAmount());

        StringBuilder details = new StringBuilder();
        details.append("💰 Amount: ").append(amountText).append("\n");
        details.append("🏷️ Category: ").append(getCategoryEmoji(displayCategory)).append(" ").append(displayCategory).append("\n");
        details.append("💳 Payment Method: ").append(expense.getPaymentMethod() != null ? expense.getPaymentMethod() : "UPI").append("\n");
        details.append("📅 Date: ").append(expense.getTransactionDate() != null ? expense.getTransactionDate().replace("T", " ") : "Recent").append("\n");
        details.append("📱 Source: ").append(expense.getSource() != null ? expense.getSource() : "Manual").append("\n");

        if (expense.getBankName() != null && !expense.getBankName().isEmpty()) {
            details.append("🏦 Bank: ").append(expense.getBankName()).append("\n");
        }
        if (expense.getReferenceNumber() != null && !expense.getReferenceNumber().isEmpty()) {
            details.append("🔢 Ref / Txn ID: ").append(expense.getReferenceNumber()).append("\n");
        }
        if (expense.getDescription() != null && !expense.getDescription().isEmpty()) {
            details.append("📝 Description: ").append(expense.getDescription());
        }

        new AlertDialog.Builder(context)
                .setTitle(getCategoryEmoji(displayCategory) + " " + (expense.getMerchantName() != null ? expense.getMerchantName() : "Transaction Details"))
                .setMessage(details.toString().trim())
                .setPositiveButton("Edit / Categorize", (dialog, which) -> {
                    Intent intent = new Intent(context, AddExpenseActivity.class);
                    intent.putExtra("expenseId", expense.getId());
                    intent.putExtra("amount", String.valueOf(expense.getAmount()));
                    intent.putExtra("merchant", expense.getMerchantName());
                    intent.putExtra("category", displayCategory);
                    intent.putExtra("source", expense.getSource());
                    context.startActivity(intent);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public int getItemCount() { return expenses.size(); }

    public void updateExpenses(List<Expense> newExpenses) {
        this.expenses = newExpenses != null ? newExpenses : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void prependExpense(Expense expense) {
        expenses.add(0, expense);
        notifyItemInserted(0);
    }

    static class ExpenseVH extends RecyclerView.ViewHolder {
        TextView tvCategoryEmoji, tvMerchant, tvCategory, tvAmount, tvDate, tvSourceBadge;
        ImageView ivPending;

        ExpenseVH(@NonNull View itemView) {
            super(itemView);
            tvCategoryEmoji = itemView.findViewById(R.id.tv_category_emoji);
            ivPending = itemView.findViewById(R.id.iv_pending);
            tvMerchant = itemView.findViewById(R.id.tv_merchant);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvSourceBadge = itemView.findViewById(R.id.tv_source_badge);
        }
    }
}
