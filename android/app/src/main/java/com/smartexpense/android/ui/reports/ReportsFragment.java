package com.smartexpense.android.ui.reports;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.smartexpense.android.R;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.FragmentReportsBinding;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.ui.expenses.ExpenseAdapter;
import com.smartexpense.android.util.TokenManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reports Fragment — displays Monthly Spending bar chart calculated from actual
 * signed-in user expense data and generates comprehensive, account-isolated PDF reports.
 */
public class ReportsFragment extends Fragment {

    private FragmentReportsBinding binding;
    private int currentMonth, currentYear;
    private final NumberFormat currencyFormat = NumberFormat.getInstance(new Locale("en", "IN"));

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LocalDateTime now = LocalDateTime.now();
        currentMonth = now.getMonthValue();
        currentYear = now.getYear();

        setupBarChart();
        loadReportData();

        binding.btnExportPdf.setOnClickListener(v -> generateAndOpenPdfReport());
    }

    private void setupBarChart() {
        if (binding == null) return;
        BarChart barChart = binding.barChart;
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setTouchEnabled(true);
        barChart.setDragEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setFitBars(true);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        xAxis.setTextSize(11f);

        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.divider));
        barChart.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        barChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "₹" + currencyFormat.format((long) value);
            }
        });
    }

    private void loadReportData() {
        if (!isAdded() || binding == null) return;
        showLoading(true);

        // Load strictly signed-in user's actual expenses
        List<Expense> allUserExpenses = OfflineMockStorage.getExpenses();

        // Calculate actual spending for the last 6 months
        List<BarEntry> entries = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();
        DateTimeFormatter monthNameFmt = DateTimeFormatter.ofPattern("MMM", Locale.US);

        double thisMonthTotal = 0;
        int thisMonthCount = 0;
        Map<String, Double> thisMonthCatTotals = new HashMap<>();

        for (int i = 5; i >= 0; i--) {
            LocalDate dt = LocalDate.now().minusMonths(i);
            int m = dt.getMonthValue();
            int y = dt.getYear();
            int chartIndex = 5 - i;

            monthLabels.add(dt.format(monthNameFmt));

            double monthSum = 0;
            for (Expense e : allUserExpenses) {
                if (e != null && !"CREDIT".equalsIgnoreCase(e.getTransactionType())) {
                    if (e.getTransactionDate() != null && e.getTransactionDate().length() >= 7) {
                        try {
                            int expYear = Integer.parseInt(e.getTransactionDate().substring(0, 4));
                            int expMonth = Integer.parseInt(e.getTransactionDate().substring(5, 7));
                            if (expYear == y && expMonth == m) {
                                monthSum += e.getAmount();
                                if (i == 0) { // Current month
                                    thisMonthTotal += e.getAmount();
                                    thisMonthCount++;
                                    String cat = e.getCategory() != null ? e.getCategory() : "Others";
                                    thisMonthCatTotals.put(cat, thisMonthCatTotals.getOrDefault(cat, 0.0) + e.getAmount());
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            entries.add(new BarEntry(chartIndex, (float) monthSum));
        }

        // Find top category for current month
        String topCat = "None";
        double maxCat = 0;
        for (Map.Entry<String, Double> e : thisMonthCatTotals.entrySet()) {
            if (e.getValue() > maxCat) {
                maxCat = e.getValue();
                topCat = e.getKey();
            }
        }

        final double finalTotal = thisMonthTotal;
        final int finalCount = thisMonthCount;
        final String finalTopCat = topCat;
        final double finalMaxCat = maxCat;

        BarDataSet dataSet = new BarDataSet(entries, "Monthly Spending");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
        dataSet.setValueTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value > 0 ? "₹" + currencyFormat.format((long) value) : "";
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.55f);

        binding.barChart.post(() -> {
            if (binding != null && binding.barChart != null) {
                binding.barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                binding.barChart.getXAxis().setLabelCount(monthLabels.size());
                binding.barChart.setData(barData);
                binding.barChart.animateY(800);
                binding.barChart.invalidate();

                binding.tvSummaryTotal.setText("₹" + currencyFormat.format(finalTotal));
                binding.tvSummaryCount.setText(finalCount + " txns");
                binding.tvSummaryTopCategory.setText(finalTopCat + (finalMaxCat > 0 ? " (₹" + currencyFormat.format(finalMaxCat) + ")" : ""));

                showLoading(false);
            }
        });
    }

    /**
     * Generates a PDF report containing real user data, category breakdowns,
     * and transaction details, strictly adhering to account isolation.
     */
    private void generateAndOpenPdfReport() {
        Context ctx = getContext();
        if (ctx == null || binding == null) return;

        binding.progressExport.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                // Fetch strictly current user's expenses
                List<Expense> userExpenses = OfflineMockStorage.getExpenses();
                List<Expense> monthExpenses = new ArrayList<>();
                Map<String, Double> categoryTotals = new HashMap<>();
                double totalSpent = 0;

                for (Expense e : userExpenses) {
                    if (e != null && !"CREDIT".equalsIgnoreCase(e.getTransactionType())) {
                        if (e.getTransactionDate() != null && e.getTransactionDate().length() >= 7) {
                            try {
                                int expYear = Integer.parseInt(e.getTransactionDate().substring(0, 4));
                                int expMonth = Integer.parseInt(e.getTransactionDate().substring(5, 7));
                                if (expYear == currentYear && expMonth == currentMonth) {
                                    monthExpenses.add(e);
                                    totalSpent += e.getAmount();
                                    String cat = e.getCategory() != null ? e.getCategory() : "Others";
                                    categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + e.getAmount());
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Create PDF Document using native Android PdfDocument
                PdfDocument pdfDoc = new PdfDocument();
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 format
                PdfDocument.Page page = pdfDoc.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                Paint paint = new Paint();
                paint.setAntiAlias(true);

                // Document Header Banner
                paint.setColor(Color.parseColor("#087E72")); // Brand Teal
                canvas.drawRect(0, 0, 595, 90, paint);

                paint.setColor(Color.WHITE);
                paint.setTextSize(20);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Smart Expense Tracker", 36, 42, paint);

                paint.setTextSize(12);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas.drawText("Official Financial Report · Monthly Summary", 36, 64, paint);

                // User details & metadata
                String userName = TokenManager.getUserName();
                String userId = OfflineMockStorage.resolveCurrentUserId();
                String periodStr = LocalDate.of(currentYear, currentMonth, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US));

                int y = 120;
                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTextSize(12);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Report Period:", 36, y, paint);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas.drawText(periodStr, 130, y, paint);

                y += 20;
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("User Account:", 36, y, paint);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas.drawText((userName != null ? userName : "User") + " (" + userId + ")", 130, y, paint);

                // Summary KPI box
                y += 30;
                paint.setColor(Color.parseColor("#F1F5F9"));
                canvas.drawRoundRect(36, y, 559, y + 60, 8, 8, paint);

                paint.setColor(Color.parseColor("#475569"));
                paint.setTextSize(10);
                canvas.drawText("TOTAL MONTHLY EXPENSES", 54, y + 24, paint);
                canvas.drawText("TRANSACTIONS", 260, y + 24, paint);
                canvas.drawText("GENERATED ON", 420, y + 24, paint);

                paint.setColor(Color.parseColor("#087E72"));
                paint.setTextSize(18);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("₹" + currencyFormat.format(totalSpent), 54, y + 48, paint);

                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTextSize(16);
                canvas.drawText(String.valueOf(monthExpenses.size()), 260, y + 48, paint);

                paint.setTextSize(11);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                canvas.drawText(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)), 420, y + 48, paint);

                // Section 1: Category Breakdown Table
                y += 90;
                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTextSize(13);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Category Breakdown", 36, y, paint);

                y += 12;
                paint.setColor(Color.parseColor("#E2E8F0"));
                canvas.drawRect(36, y, 559, y + 22, paint);

                paint.setColor(Color.parseColor("#334155"));
                paint.setTextSize(10);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("CATEGORY", 46, y + 15, paint);
                canvas.drawText("SPENT (₹)", 260, y + 15, paint);
                canvas.drawText("% OF TOTAL", 400, y + 15, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                y += 24;
                for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
                    double catSpent = entry.getValue();
                    double pct = totalSpent > 0 ? (catSpent / totalSpent) * 100 : 0;
                    String emoji = ExpenseAdapter.getCategoryEmoji(entry.getKey());

                    paint.setColor(Color.parseColor("#1E293B"));
                    canvas.drawText(emoji + " " + entry.getKey(), 46, y + 14, paint);
                    canvas.drawText("₹" + currencyFormat.format(catSpent), 260, y + 14, paint);
                    canvas.drawText(String.format(Locale.US, "%.1f%%", pct), 400, y + 14, paint);

                    paint.setColor(Color.parseColor("#F1F5F9"));
                    canvas.drawLine(36, y + 20, 559, y + 20, paint);
                    y += 22;
                }

                // Section 2: Recent Transactions Table
                y += 20;
                paint.setColor(Color.parseColor("#0F172A"));
                paint.setTextSize(13);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("Transaction Details (" + monthExpenses.size() + ")", 36, y, paint);

                y += 12;
                paint.setColor(Color.parseColor("#E2E8F0"));
                canvas.drawRect(36, y, 559, y + 22, paint);

                paint.setColor(Color.parseColor("#334155"));
                paint.setTextSize(10);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText("DATE", 46, y + 15, paint);
                canvas.drawText("MERCHANT / PAYEE", 120, y + 15, paint);
                canvas.drawText("CATEGORY", 320, y + 15, paint);
                canvas.drawText("AMOUNT", 480, y + 15, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                y += 24;

                int maxLines = Math.min(monthExpenses.size(), 18);
                for (int i = 0; i < maxLines; i++) {
                    Expense exp = monthExpenses.get(i);
                    String date = exp.getTransactionDate() != null && exp.getTransactionDate().length() >= 10
                            ? exp.getTransactionDate().substring(0, 10) : "";
                    String merch = exp.getMerchantName() != null ? exp.getMerchantName() : "Unknown";
                    if (merch.length() > 22) merch = merch.substring(0, 20) + "..";
                    String cat = exp.getCategory() != null ? exp.getCategory() : "Others";
                    String amt = "₹" + currencyFormat.format(exp.getAmount());

                    paint.setColor(Color.parseColor("#1E293B"));
                    canvas.drawText(date, 46, y + 14, paint);
                    canvas.drawText(merch, 120, y + 14, paint);
                    canvas.drawText(cat, 320, y + 14, paint);
                    canvas.drawText(amt, 480, y + 14, paint);

                    paint.setColor(Color.parseColor("#F1F5F9"));
                    canvas.drawLine(36, y + 20, 559, y + 20, paint);
                    y += 22;
                }

                // Footer
                paint.setColor(Color.parseColor("#94A3B8"));
                paint.setTextSize(9);
                canvas.drawText("Generated automatically by Smart Expense Tracker Android App · Private & Confidential", 36, 810, paint);

                pdfDoc.finishPage(page);

                // Save to file
                String filename = "SmartExpense_Report_" + currentYear + "_" + currentMonth + ".pdf";
                File file = new File(ctx.getExternalFilesDir(null), filename);
                FileOutputStream fos = new FileOutputStream(file);
                pdfDoc.writeTo(fos);
                pdfDoc.close();
                fos.close();

                // Launch Intent to open PDF
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) binding.progressExport.setVisibility(View.GONE);
                    Toast.makeText(ctx, "PDF downloaded: " + file.getName(), Toast.LENGTH_SHORT).show();

                    try {
                        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "application/pdf");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, "Open Financial Report"));
                    } catch (Exception e) {
                        Toast.makeText(ctx, "Saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) binding.progressExport.setVisibility(View.GONE);
                    Toast.makeText(ctx, "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
        loadReportData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
