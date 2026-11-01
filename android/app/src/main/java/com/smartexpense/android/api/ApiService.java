package com.smartexpense.android.api;

import com.smartexpense.android.model.Expense;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.model.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit service interface for all backend API endpoints.
 */
public interface ApiService {

    // ─── Auth ────────────────────────────────────────────────────────────────
    @POST("auth/register")
    Call<ApiResponse<Map<String, Object>>> register(@Body Map<String, String> body);

    @POST("auth/login")
    Call<ApiResponse<Map<String, Object>>> login(@Body Map<String, String> body);

    @POST("auth/google")
    Call<ApiResponse<Map<String, Object>>> googleSignIn(@Body Map<String, String> body);

    // ─── Expenses ────────────────────────────────────────────────────────────
    @GET("expenses")
    Call<ApiResponse<List<Expense>>> getExpenses(@Query("limit") int limit);

    @GET("expenses/{id}")
    Call<ApiResponse<Expense>> getExpense(@Path("id") String id);

    @GET("expenses/monthly")
    Call<ApiResponse<List<Expense>>> getMonthlyExpenses(
            @Query("month") int month, @Query("year") int year);

    @POST("expenses")
    Call<ApiResponse<Expense>> createExpense(@Body Map<String, Object> body);

    @PUT("expenses/{id}")
    Call<ApiResponse<Expense>> updateExpense(
            @Path("id") String id, @Body Map<String, Object> body);

    @PUT("expenses/{id}/category")
    Call<ApiResponse<Expense>> updateExpenseCategory(
            @Path("id") String id, @Body Map<String, String> body);

    @DELETE("expenses/{id}")
    Call<ApiResponse<Void>> deleteExpense(@Path("id") String id);

    @GET("expenses/search")
    Call<ApiResponse<List<Expense>>> searchExpenses(
            @Query("q") String query,
            @Query("category") String category,
            @Query("paymentMethod") String paymentMethod,
            @Query("from") String from,
            @Query("to") String to);

    @GET("expenses/summary")
    Call<ApiResponse<Map<String, Object>>> getMonthlySummary(
            @Query("month") int month, @Query("year") int year);

    // ─── Budget ──────────────────────────────────────────────────────────────
    @GET("budgets")
    Call<ApiResponse<Budget>> getBudget(
            @Query("month") int month, @Query("year") int year);

    @POST("budgets")
    Call<ApiResponse<Budget>> saveBudget(@Body Map<String, Object> body);

    // ─── AI ──────────────────────────────────────────────────────────────────
    @POST("ai/categorize")
    Call<ApiResponse<Map<String, Object>>> categorize(@Body Map<String, Object> body);

    @POST("ai/chat")
    Call<ApiResponse<Map<String, Object>>> askAi(@Body Map<String, Object> body);

    @GET("ai/insights")
    Call<ApiResponse<Map<String, Object>>> getInsights(
            @Query("month") int month, @Query("year") int year);

    @GET("ai/predict")
    Call<ApiResponse<Map<String, Object>>> predict();

    // ─── Reports ─────────────────────────────────────────────────────────────
    @GET("reports/export/pdf")
    Call<okhttp3.ResponseBody> exportPdf(
            @Query("month") int month, @Query("year") int year);

    @GET("reports/export/excel")
    Call<okhttp3.ResponseBody> exportExcel(
            @Query("month") int month, @Query("year") int year);

    // ─── Merchants ───────────────────────────────────────────────────────────
    @POST("merchants/learn")
    Call<ApiResponse<Void>> saveMerchantMapping(@Body Map<String, String> body);
}
