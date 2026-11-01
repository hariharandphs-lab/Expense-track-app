package com.smartexpense.android.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.ExpenseAiEngine;

import com.smartexpense.android.util.TokenManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * OkHttp Interceptor that detects network failure or backend downtime
 * and automatically switches to the offline storage engine.
 * Ensures the app works 100% of the time, never hanging or showing
 * "No internet connection" errors to the user.
 */
public class OfflineMockInterceptor implements Interceptor {

    private static final String TAG = "OfflineMockInterceptor";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();

        // If user logged in via Demo Mode or Offline fallback, bypass network entirely
        // to prevent long timeouts and instant UI rendering.
        String token = TokenManager.getToken();
        if (token != null && (token.startsWith("demo-token-") || token.startsWith("offline-token-"))) {
            return handleOfflineRequest(request);
        }

        try {
            Response response = chain.proceed(request);
            // If the server answered with an error like 502/503/504 (bad gateway / unavailable)
            if (response.code() >= 500) {
                response.close();
                Log.w(TAG, "Backend returned server error (" + response.code() + "). Serving offline mock response for " + request.url());
                return handleOfflineRequest(request);
            }
            return response;
        } catch (IOException e) {
            // Connection refused, timeout, unknown host, etc.
            Log.w(TAG, "Backend unreachable (" + e.getMessage() + "). Serving offline mock response for " + request.url());
            return handleOfflineRequest(request);
        }
    }

    private Response handleOfflineRequest(Request request) {
        String path = request.url().encodedPath();
        String method = request.method();
        String jsonResponse;

        try {
            if (path.contains("/auth/register")) {
                jsonResponse = handleRegister(request);
            } else if (path.contains("/auth/login")) {
                jsonResponse = handleLogin(request);
            } else if (path.contains("/auth/google")) {
                jsonResponse = handleGoogleAuth();
            } else if (path.contains("/expenses/summary")) {
                jsonResponse = handleSummary(request);
            } else if (path.contains("/expenses/monthly")) {
                jsonResponse = handleMonthlyExpenses();
            } else if (path.contains("/expenses/search")) {
                jsonResponse = handleSearch(request);
            } else if (path.contains("/expenses/") && path.endsWith("/category") && "PUT".equalsIgnoreCase(method)) {
                jsonResponse = handleUpdateCategory(request);
            } else if (path.contains("/expenses/") && "PUT".equalsIgnoreCase(method)) {
                jsonResponse = handleUpdateExpense(request);
            } else if (path.contains("/expenses/") && "DELETE".equalsIgnoreCase(method)) {
                jsonResponse = handleDeleteExpense(request);
            } else if (path.contains("/expenses/") && "GET".equalsIgnoreCase(method)) {
                jsonResponse = handleGetSingleExpense(request);
            } else if (path.endsWith("/expenses") && "POST".equalsIgnoreCase(method)) {
                jsonResponse = handleCreateExpense(request);
            } else if (path.endsWith("/expenses") && "GET".equalsIgnoreCase(method)) {
                jsonResponse = handleGetExpenses(request);
            } else if (path.contains("/budgets") && "POST".equalsIgnoreCase(method)) {
                jsonResponse = handleSaveBudget(request);
            } else if (path.contains("/budgets") && "GET".equalsIgnoreCase(method)) {
                jsonResponse = handleGetBudget(request);
            } else if (path.contains("/ai/insights")) {
                jsonResponse = handleInsights();
            } else if (path.contains("/ai/predict")) {
                jsonResponse = handlePrediction();
            } else if (path.contains("/ai/categorize")) {
                jsonResponse = handleCategorize(request);
            } else if (path.contains("/ai/chat")) {
                jsonResponse = handleChat(request);
            } else if (path.contains("/merchants/learn")) {
                jsonResponse = "{\"success\":true,\"message\":\"Learned\",\"data\":null}";
            } else {
                jsonResponse = "{\"success\":true,\"message\":\"Offline OK\",\"data\":{}}";
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error generating offline response", ex);
            jsonResponse = "{\"success\":true,\"message\":\"Offline fallback\",\"data\":{}}";
        }

        return new Response.Builder()
                .code(200)
                .message("OK (Offline Mock)")
                .protocol(Protocol.HTTP_1_1)
                .request(request)
                .body(ResponseBody.create(jsonResponse, JSON_MEDIA))
                .addHeader("content-type", "application/json")
                .build();
    }

    private String handleRegister(Request request) {
        String body = readRequestBody(request);
        String email = "user@smartexpense.app";
        String name = "User";

        if (body != null) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("email")) email = json.get("email").getAsString();
                if (json.has("displayName")) name = json.get("displayName").getAsString();
            } catch (Exception ignored) {}
        }

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", "offline-token-" + System.currentTimeMillis());
        data.put("refreshToken", "offline-refresh-" + System.currentTimeMillis());
        data.put("userId", "offline-" + System.currentTimeMillis());
        data.put("email", email);
        data.put("displayName", name);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "User registered successfully");
        resp.put("data", data);
        return gson.toJson(resp);
    }

    private String handleLogin(Request request) {
        String body = readRequestBody(request);
        String email = "demo@smartexpense.app";
        String name = "Demo User";

        if (body != null) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("email")) {
                    email = json.get("email").getAsString();
                    name = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", "offline-token-" + System.currentTimeMillis());
        data.put("refreshToken", "offline-refresh-" + System.currentTimeMillis());
        data.put("userId", "offline-" + System.currentTimeMillis());
        data.put("email", email);
        data.put("displayName", name);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Login successful");
        resp.put("data", data);
        return gson.toJson(resp);
    }

    private String handleGoogleAuth() {
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", "offline-google-token-" + System.currentTimeMillis());
        data.put("refreshToken", "offline-google-refresh-" + System.currentTimeMillis());
        data.put("userId", "offline-google-user");
        data.put("email", "google.user@smartexpense.app");
        data.put("displayName", "Google User");

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Google sign-in successful");
        resp.put("data", data);
        return gson.toJson(resp);
    }

    private String handleGetExpenses(Request request) {
        List<Expense> expenses = OfflineMockStorage.getExpenses();
        String limitStr = request.url().queryParameter("limit");
        if (limitStr != null) {
            try {
                int limit = Integer.parseInt(limitStr);
                if (limit > 0 && limit < expenses.size()) {
                    expenses = expenses.subList(0, limit);
                }
            } catch (Exception ignored) {}
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Expenses retrieved");
        resp.put("data", expenses);
        return gson.toJson(resp);
    }

    private String handleMonthlyExpenses() {
        List<Expense> expenses = OfflineMockStorage.getExpenses();
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Monthly expenses retrieved");
        resp.put("data", expenses);
        return gson.toJson(resp);
    }

    private String handleGetSingleExpense(Request request) {
        String path = request.url().encodedPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        Expense exp = OfflineMockStorage.getExpense(id);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", exp != null);
        resp.put("message", exp != null ? "Expense found" : "Not found");
        resp.put("data", exp);
        return gson.toJson(resp);
    }

    private String handleCreateExpense(Request request) {
        String body = readRequestBody(request);
        Expense exp;
        try {
            exp = gson.fromJson(body, Expense.class);
        } catch (Exception e) {
            exp = new Expense();
        }
        if (exp.getUserId() == null || exp.getUserId().trim().isEmpty()) {
            exp.setUserId(OfflineMockStorage.resolveCurrentUserId());
        }
        if (exp.getCategory() == null || exp.getCategory().isEmpty() || "Others".equalsIgnoreCase(exp.getCategory())) {
            String inferred = ExpenseAiEngine.inferCategory(exp.getMerchantName(), exp.getDescription());
            exp.setCategory(inferred);
        }
        Expense created = OfflineMockStorage.addExpense(exp);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Expense created successfully");
        resp.put("data", created);
        return gson.toJson(resp);
    }

    private String handleUpdateExpense(Request request) {
        String path = request.url().encodedPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        String body = readRequestBody(request);
        Expense exp;
        try {
            exp = gson.fromJson(body, Expense.class);
        } catch (Exception e) {
            exp = new Expense();
        }
        if (exp.getUserId() == null || exp.getUserId().trim().isEmpty()) {
            exp.setUserId(OfflineMockStorage.resolveCurrentUserId());
        }
        Expense updated = OfflineMockStorage.updateExpense(id, exp);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Expense updated successfully");
        resp.put("data", updated);
        return gson.toJson(resp);
    }

    private String handleUpdateCategory(Request request) {
        String path = request.url().encodedPath();
        // format: /expenses/{id}/category
        String[] parts = path.split("/");
        String id = parts.length >= 3 ? parts[parts.length - 2] : "";
        String body = readRequestBody(request);
        String category = "Others";
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("category")) category = json.get("category").getAsString();
        } catch (Exception ignored) {}

        Expense updated = OfflineMockStorage.updateExpenseCategory(id, category);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Category updated");
        resp.put("data", updated);
        return gson.toJson(resp);
    }

    private String handleDeleteExpense(Request request) {
        String path = request.url().encodedPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        boolean deleted = OfflineMockStorage.deleteExpense(id);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", deleted);
        resp.put("message", deleted ? "Deleted" : "Not found");
        resp.put("data", null);
        return gson.toJson(resp);
    }

    private String handleSearch(Request request) {
        String q = request.url().queryParameter("q");
        String cat = request.url().queryParameter("category");
        List<Expense> all = OfflineMockStorage.getExpenses();
        List<Expense> matched = new ArrayList<>();

        for (Expense e : all) {
            boolean matchesQ = (q == null || q.isEmpty()
                    || (e.getMerchantName() != null && e.getMerchantName().toLowerCase().contains(q.toLowerCase()))
                    || (e.getDescription() != null && e.getDescription().toLowerCase().contains(q.toLowerCase())));
            boolean matchesCat = (cat == null || cat.isEmpty() || cat.equalsIgnoreCase(e.getCategory()));

            if (matchesQ && matchesCat) {
                matched.add(e);
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Search results");
        resp.put("data", matched);
        return gson.toJson(resp);
    }

    private String handleSummary(Request request) {
        int month = LocalDateTime.now().getMonthValue();
        int year = LocalDateTime.now().getYear();
        try {
            String m = request.url().queryParameter("month");
            String y = request.url().queryParameter("year");
            if (m != null) month = Integer.parseInt(m);
            if (y != null) year = Integer.parseInt(y);
        } catch (Exception ignored) {}

        Map<String, Object> summary = OfflineMockStorage.getMonthlySummary(month, year);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Monthly summary");
        resp.put("data", summary);
        return gson.toJson(resp);
    }

    private String handleGetBudget(Request request) {
        int month = LocalDateTime.now().getMonthValue();
        int year = LocalDateTime.now().getYear();
        Budget b = OfflineMockStorage.getBudget(month, year);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Budget retrieved");
        resp.put("data", b);
        return gson.toJson(resp);
    }

    private String handleSaveBudget(Request request) {
        String body = readRequestBody(request);
        double total = 25000.0;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("totalBudget")) total = json.get("totalBudget").getAsDouble();
        } catch (Exception ignored) {}

        Budget saved = OfflineMockStorage.saveBudget(total,
                LocalDateTime.now().getMonthValue(), LocalDateTime.now().getYear());

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Budget saved");
        resp.put("data", saved);
        return gson.toJson(resp);
    }

    private String handleInsights() {
        int month = LocalDateTime.now().getMonthValue();
        int year = LocalDateTime.now().getYear();
        Map<String, Object> insights = OfflineMockStorage.getInsights(month, year);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Insights generated");
        resp.put("data", insights);
        return gson.toJson(resp);
    }

    private String handlePrediction() {
        Map<String, Object> pred = OfflineMockStorage.getPrediction();
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Prediction generated");
        resp.put("data", pred);
        return gson.toJson(resp);
    }

    private String handleCategorize(Request request) {
        String body = readRequestBody(request);
        String merchant = "";
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("merchantName")) merchant = json.get("merchantName").getAsString();
        } catch (Exception ignored) {}

        String cat = OfflineMockStorage.categorizeMerchant(merchant);
        Map<String, Object> data = new HashMap<>();
        data.put("category", cat);
        data.put("confidence", 0.95);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return gson.toJson(resp);
    }

    private String handleChat(Request request) {
        String body = readRequestBody(request);
        String question = "";
        String language = "en";
        List<Map<String, String>> history = new ArrayList<>();
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("question")) question = json.get("question").getAsString();
            if (json.has("language")) language = json.get("language").getAsString();
            if (json.has("history") && json.get("history").isJsonArray()) {
                com.google.gson.JsonArray arr = json.getAsJsonArray("history");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject item = arr.get(i).getAsJsonObject();
                    String role = item.has("role") ? item.get("role").getAsString() : "user";
                    String text = item.has("text") ? item.get("text").getAsString() : "";
                    Map<String, String> m = new HashMap<>();
                    m.put("role", role);
                    m.put("text", text);
                    history.add(m);
                }
            }
        } catch (Exception ignored) {}

        List<Expense> expenses = OfflineMockStorage.getExpenses();
        String answer = ExpenseAiEngine.process(question, expenses, language, history).getText();

        Map<String, Object> data = new HashMap<>();
        data.put("answer", answer);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("data", data);
        return gson.toJson(resp);
    }

    private String readRequestBody(Request request) {
        try {
            RequestBody body = request.body();
            if (body == null) return null;
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
