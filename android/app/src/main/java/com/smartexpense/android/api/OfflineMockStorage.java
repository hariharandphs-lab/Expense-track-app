package com.smartexpense.android.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smartexpense.android.SmartExpenseApp;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.TokenManager;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local offline data storage engine for Smart Expense Tracker with
 * strict multi-user data isolation.
 *
 * Each authenticated user and Demo Mode has isolated expenses, budgets,
 * summaries, and analytics, while preserving offline persistence and SMS tracking.
 */
public class OfflineMockStorage {

    private static final String PREF_NAME = "smart_expense_offline_db";
    private static final String KEY_ALL_EXPENSES = "offline_expenses_master_list";
    private static final String KEY_LEGACY_EXPENSES = "offline_expenses_list";
    private static final String KEY_LEGACY_MIGRATED = "offline_legacy_migrated";
    private static final String KEY_BUDGET_PREFIX = "offline_budget_total_";
    private static final String KEY_LEGACY_BUDGET = "offline_budget_total";

    private static final Gson gson = new Gson();
    private static SharedPreferences mockPrefsForTesting = null;

    public static void setMockPrefsForTesting(SharedPreferences prefs) {
        mockPrefsForTesting = prefs;
    }

    private static SharedPreferences getPrefs() {
        if (mockPrefsForTesting != null) {
            return mockPrefsForTesting;
        }
        Context context = SmartExpenseApp.getInstance();
        if (context == null) {
            context = SmartExpenseApp.getAppContext();
        }
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Resolves the active user ID for data scoping.
     * Prioritizes TokenManager, then FirebaseAuth, falling back to "demo-user".
     */
    public static String resolveCurrentUserId() {
        String userId = TokenManager.getUserId();
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        try {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null && firebaseUser.getUid() != null && !firebaseUser.getUid().trim().isEmpty()) {
                return firebaseUser.getUid().trim();
            }
        } catch (Exception ignored) {}
        return "demo-user";
    }

    /**
     * Loads the master list of all expenses from persistent storage,
     * safely migrating any unassigned legacy records without deleting data.
     */
    private static synchronized List<Expense> getAllExpensesFromDb() {
        SharedPreferences prefs = getPrefs();
        List<Expense> masterList = new ArrayList<>();
        if (prefs == null) {
            return masterList;
        }
        String json = prefs.getString(KEY_ALL_EXPENSES, null);

        if (json != null && !json.trim().isEmpty()) {
            try {
                Type type = new TypeToken<List<Expense>>() {}.getType();
                List<Expense> parsed = gson.fromJson(json, type);
                if (parsed != null) {
                    masterList.addAll(parsed);
                }
            } catch (Exception ignored) {}
        }

        // Migrate legacy list if present and not yet migrated
        if (!prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            String legacyJson = prefs.getString(KEY_LEGACY_EXPENSES, null);
            if (legacyJson != null && !legacyJson.trim().isEmpty()) {
                try {
                    Type type = new TypeToken<List<Expense>>() {}.getType();
                    List<Expense> legacyList = gson.fromJson(legacyJson, type);
                    if (legacyList != null && !legacyList.isEmpty()) {
                        String activeUser = resolveCurrentUserId();
                        for (Expense e : legacyList) {
                            if (e.getUserId() == null || e.getUserId().trim().isEmpty()) {
                                e.setUserId(activeUser);
                            }
                            boolean exists = false;
                            for (Expense m : masterList) {
                                if (e.getId() != null && e.getId().equals(m.getId())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                masterList.add(e);
                            }
                        }
                        saveAllExpensesToDb(masterList);
                    }
                } catch (Exception ignored) {}
            }
            prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply();
        }

        return masterList;
    }

    private static synchronized void saveAllExpensesToDb(List<Expense> all) {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putString(KEY_ALL_EXPENSES, gson.toJson(all)).apply();
    }

    /**
     * Returns expenses filtered strictly for the currently authenticated user or demo mode.
     */
    public static synchronized List<Expense> getExpenses() {
        return getExpenses(resolveCurrentUserId());
    }

    /**
     * Returns expenses for a specific user ID.
     */
    public static synchronized List<Expense> getExpenses(String targetUserId) {
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            targetUserId = resolveCurrentUserId();
        }
        List<Expense> all = getAllExpensesFromDb();
        List<Expense> userExpenses = new ArrayList<>();
        boolean modified = false;
        for (Expense e : all) {
            // Claim unassigned, legacy, or default_user expenses for the active user so no transactions are lost
            if (e.getUserId() == null || e.getUserId().trim().isEmpty() || "default_user".equals(e.getUserId())) {
                e.setUserId(targetUserId);
                modified = true;
            }
            if (targetUserId.equals(e.getUserId())) {
                userExpenses.add(e);
            }
        }
        if (modified) {
            saveAllExpensesToDb(all);
        }
        return userExpenses;
    }

    /**
     * Saves/updates expenses for the current user while preserving all other users' data.
     */
    public static synchronized void saveExpenses(List<Expense> list) {
        saveUserExpenses(resolveCurrentUserId(), list);
    }

    /**
     * Saves/updates expenses for a specific user ID while preserving other users' data.
     */
    public static synchronized void saveUserExpenses(String userId, List<Expense> userExpenses) {
        if (userId == null || userId.trim().isEmpty()) {
            userId = resolveCurrentUserId();
        }
        List<Expense> all = getAllExpensesFromDb();
        final String uid = userId;
        all.removeIf(e -> uid.equals(e.getUserId()));

        if (userExpenses != null) {
            for (Expense e : userExpenses) {
                if (e.getUserId() == null || e.getUserId().trim().isEmpty()) {
                    e.setUserId(uid);
                }
                all.add(e);
            }
        }
        saveAllExpensesToDb(all);
    }

    /**
     * Finds a single expense by ID belonging to the currently authenticated user.
     */
    public static synchronized Expense getExpense(String id) {
        if (id == null) return null;
        for (Expense e : getExpenses()) {
            if (id.equals(e.getId())) return e;
        }
        return null;
    }

    /**
     * Adds an expense associated with the current user UID (or expense's pre-assigned UID).
     */
    public static synchronized Expense addExpense(Expense expense) {
        if (expense == null) return null;

        if (expense.getUserId() == null || expense.getUserId().trim().isEmpty() || "default_user".equals(expense.getUserId())) {
            expense.setUserId(resolveCurrentUserId());
        }
        String currentUserId = expense.getUserId();

        List<Expense> all = getAllExpensesFromDb();

        // Deduplication strictly within this user's records
        for (Expense existing : all) {
            if (existing.getUserId() == null || existing.getUserId().trim().isEmpty() || "default_user".equals(existing.getUserId())) {
                existing.setUserId(currentUserId);
            }
            if (!currentUserId.equals(existing.getUserId())) continue;

            if (expense.getClientTransactionId() != null && !expense.getClientTransactionId().isEmpty()
                    && expense.getClientTransactionId().equals(existing.getClientTransactionId())) {
                return existing;
            }

            if (expense.getReferenceNumber() != null && !expense.getReferenceNumber().isEmpty()
                    && expense.getReferenceNumber().equals(existing.getReferenceNumber())) {
                return existing;
            }

            if ("SMS".equalsIgnoreCase(expense.getSource()) && expense.getMerchantName() != null
                    && "SMS".equalsIgnoreCase(existing.getSource())
                    && Math.abs(expense.getAmount() - existing.getAmount()) < 0.001
                    && expense.getMerchantName().equalsIgnoreCase(existing.getMerchantName())
                    && java.util.Objects.equals(expense.getTransactionDate(), existing.getTransactionDate())) {
                return existing;
            }
        }

        if (expense.getId() == null || expense.getId().isEmpty()) {
            expense.setId("exp-" + System.currentTimeMillis());
        }
        if (expense.getCreatedAt() == null) {
            expense.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (expense.getTransactionDate() == null) {
            expense.setTransactionDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (expense.getStatus() == null) {
            expense.setStatus("CONFIRMED");
        }
        if (expense.getTransactionType() == null) {
            expense.setTransactionType("DEBIT");
        }

        all.add(0, expense);
        saveAllExpensesToDb(all);
        return expense;
    }

    public static synchronized Expense updateExpense(String id, Expense updated) {
        String currentUserId = resolveCurrentUserId();
        List<Expense> all = getAllExpensesFromDb();
        for (int i = 0; i < all.size(); i++) {
            Expense existing = all.get(i);
            if (id.equals(existing.getId()) && currentUserId.equals(existing.getUserId())) {
                updated.setId(id);
                updated.setUserId(currentUserId);
                all.set(i, updated);
                saveAllExpensesToDb(all);
                return updated;
            }
        }
        return updated;
    }

    public static synchronized Expense updateExpenseCategory(String id, String category) {
        String currentUserId = resolveCurrentUserId();
        List<Expense> all = getAllExpensesFromDb();
        for (int i = 0; i < all.size(); i++) {
            Expense e = all.get(i);
            if (id.equals(e.getId()) && currentUserId.equals(e.getUserId())) {
                e.setCategory(category);
                e.setStatus("CONFIRMED");
                all.set(i, e);
                saveAllExpensesToDb(all);

                // Teach SmartCategorizer for future transactions from this merchant
                if (e.getMerchantName() != null && !e.getMerchantName().trim().isEmpty()) {
                    Context ctx = SmartExpenseApp.getInstance() != null ? SmartExpenseApp.getInstance() : SmartExpenseApp.getAppContext();
                    com.smartexpense.android.util.SmartCategorizer.saveUserCorrection(
                            ctx, e.getMerchantName(), category, "Transfers".equalsIgnoreCase(category));
                }
                return e;
            }
        }
        return null;
    }

    /**
     * Intelligently categorizes existing uncategorized ("Others", null, or empty) expenses
     * without modifying specific confirmed categories, transaction amounts, dates, or IDs.
     */
    public static synchronized int categorizeUncategorizedExpenses(Context context) {
        String currentUserId = resolveCurrentUserId();
        List<Expense> all = getAllExpensesFromDb();
        int updated = 0;
        boolean changed = false;

        for (Expense e : all) {
            if (!currentUserId.equals(e.getUserId())) continue;
            String cat = e.getCategory();
            if (cat == null || cat.trim().isEmpty() || "Others".equalsIgnoreCase(cat.trim()) || "Uncategorized".equalsIgnoreCase(cat.trim())) {
                com.smartexpense.android.util.SmartCategorizer.CategoryResult res =
                        com.smartexpense.android.util.SmartCategorizer.categorize(context, e.getMerchantName(), null, e.getDescription());
                if (res != null && res.category != null && !"Others".equalsIgnoreCase(res.category)) {
                    e.setCategory(res.category);
                    updated++;
                    changed = true;
                }
            }
        }

        if (changed) {
            saveAllExpensesToDb(all);
        }
        return updated;
    }

    public static synchronized boolean deleteExpense(String id) {
        String currentUserId = resolveCurrentUserId();
        List<Expense> all = getAllExpensesFromDb();
        boolean removed = all.removeIf(e -> id.equals(e.getId()) && currentUserId.equals(e.getUserId()));
        if (removed) {
            saveAllExpensesToDb(all);
        }
        return removed;
    }

    /**
     * Calculates monthly summary using only the current user's expenses.
     */
    public static synchronized Map<String, Object> getMonthlySummary(int month, int year) {
        List<Expense> expenses = getExpenses();
        double total = 0.0;
        int count = 0;
        Map<String, Double> categoryTotals = new HashMap<>();

        for (Expense e : expenses) {
            if (!"CREDIT".equalsIgnoreCase(e.getTransactionType())) {
                double amt = e.getAmount();
                total += amt;
                count++;
                String cat = e.getCategory() != null ? e.getCategory() : "Others";
                categoryTotals.put(cat, categoryTotals.getOrDefault(cat, 0.0) + amt);
            }
        }

        String topCategory = "Others";
        double maxCat = 0.0;
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            if (entry.getValue() > maxCat) {
                maxCat = entry.getValue();
                topCategory = entry.getKey();
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("count", count);
        summary.put("topCategory", topCategory);
        summary.put("categoryTotals", categoryTotals);
        summary.put("month", month);
        summary.put("year", year);
        return summary;
    }

    /**
     * Returns the budget isolated for the currently authenticated user.
     */
    public static synchronized Budget getBudget(int month, int year) {
        String userId = resolveCurrentUserId();
        SharedPreferences prefs = getPrefs();
        double totalBudget = prefs.getFloat(KEY_BUDGET_PREFIX + userId,
                prefs.getFloat(KEY_LEGACY_BUDGET, 25000.0f));

        Map<String, Object> summary = getMonthlySummary(month, year);
        double totalSpent = ((Number) summary.get("total")).doubleValue();
        double remaining = Math.max(0.0, totalBudget - totalSpent);
        double percentage = totalBudget > 0 ? (totalSpent / totalBudget) * 100 : 0.0;

        Budget b = new Budget();
        b.setId("budget-" + userId + "-" + month + "-" + year);
        b.setUserId(userId);
        b.setMonth(month);
        b.setYear(year);
        b.setTotalBudget(totalBudget);
        b.setTotalSpent(totalSpent);
        b.setRemaining(remaining);
        b.setPercentageUsed(percentage);

        Map<String, Double> categoryBudgets = getCategoryLimits(userId, totalBudget);
        b.setCategoryBudgets(categoryBudgets);

        return b;
    }

    private static final String KEY_CAT_LIMITS_PREFIX = "offline_category_limits_";

    public static synchronized Map<String, Double> getCategoryLimits(String userId, double totalBudget) {
        SharedPreferences prefs = getPrefs();
        Map<String, Double> limits = new HashMap<>();
        String json = prefs != null ? prefs.getString(KEY_CAT_LIMITS_PREFIX + userId, null) : null;
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type type = new TypeToken<Map<String, Double>>() {}.getType();
                Map<String, Double> saved = gson.fromJson(json, type);
                if (saved != null) limits.putAll(saved);
            } catch (Exception ignored) {}
        }
        if (limits.isEmpty()) {
            limits.put("Food", (double) Math.round(totalBudget * 0.25));
            limits.put("Grocery", (double) Math.round(totalBudget * 0.20));
            limits.put("Medical", (double) Math.round(totalBudget * 0.15));
            limits.put("Shopping", (double) Math.round(totalBudget * 0.15));
            limits.put("Travel", (double) Math.round(totalBudget * 0.10));
            limits.put("Bills", (double) Math.round(totalBudget * 0.10));
            limits.put("Fuel", (double) Math.round(totalBudget * 0.05));
        }
        return limits;
    }

    public static synchronized Budget saveCategoryLimit(String category, double limit, int month, int year) {
        String userId = resolveCurrentUserId();
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            double currentTotal = prefs.getFloat(KEY_BUDGET_PREFIX + userId, 25000.0f);
            Map<String, Double> limits = getCategoryLimits(userId, currentTotal);
            limits.put(category, limit);
            double sumLimits = 0;
            for (double l : limits.values()) sumLimits += l;
            double newTotal = Math.max(sumLimits, currentTotal);
            prefs.edit()
                    .putString(KEY_CAT_LIMITS_PREFIX + userId, gson.toJson(limits))
                    .putFloat(KEY_BUDGET_PREFIX + userId, (float) newTotal)
                    .apply();
        }
        return getBudget(month, year);
    }

    /**
     * Saves the budget for the currently authenticated user.
     */
    public static synchronized Budget saveBudget(double totalBudget, int month, int year) {
        String userId = resolveCurrentUserId();
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putFloat(KEY_BUDGET_PREFIX + userId, (float) totalBudget).apply();
        }
        return getBudget(month, year);
    }

    /**
     * Generates insights based strictly on the current user's expenses.
     */
    public static synchronized Map<String, Object> getInsights(int month, int year) {
        Map<String, Object> summary = getMonthlySummary(month, year);
        double total = ((Number) summary.get("total")).doubleValue();
        String topCat = (String) summary.get("topCategory");

        List<String> insights = new ArrayList<>();
        insights.add(String.format(Locale.getDefault(),
                "Your highest spending category this month is %s.", topCat));
        insights.add(String.format(Locale.getDefault(),
                "Total debit spending so far is ₹%.0f across %d transactions.",
                total, summary.get("count")));
        if (total < 20000) {
            insights.add("Awesome! You are well within your monthly budget limits.");
        } else {
            insights.add("Consider reviewing your non-essential shopping to increase savings.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("insights", insights);
        result.put("totalSpent", total);
        result.put("topCategory", topCat);
        return result;
    }

    /**
     * Generates expense predictions based strictly on the current user's expenses.
     */
    public static synchronized Map<String, Object> getPrediction() {
        Map<String, Object> summary = getMonthlySummary(LocalDateTime.now().getMonthValue(), LocalDateTime.now().getYear());
        double total = ((Number) summary.get("total")).doubleValue();
        double predicted = total * 1.08;

        Map<String, Object> result = new HashMap<>();
        result.put("predictedAmount", predicted);
        result.put("currency", "INR");
        result.put("trend", "STABLE");
        result.put("riskLevel", "LOW");
        result.put("recommendation", "Your monthly spend is on pace. Maintain current budget targets.");
        return result;
    }

    /**
     * Called on logout to clear any in-memory state or cached session references.
     */
    public static synchronized void clearInMemoryCache() {
        // SharedPreferences persists user data; in-memory references reset
    }

    public static synchronized void seedDemoDataIfEmpty() {
        seedUserDataIfEmpty("demo-user");
    }

    public static synchronized void seedUserDataIfEmpty(String userId) {
        if (userId == null || userId.trim().isEmpty()) userId = resolveCurrentUserId();
        List<Expense> current = getExpenses(userId);
        if (current.isEmpty()) {
            List<Expense> seed = createSampleExpenses(userId);
            for (Expense e : seed) {
                addExpense(e);
            }
        }
    }

    public static List<Expense> createSampleExpenses(String userId) {
        List<Expense> list = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Expense e1 = new Expense();
        e1.setUserId(userId);
        e1.setMerchantName("TANGEDCO");
        e1.setAmount(1640.0);
        e1.setCategory("Bills");
        e1.setPaymentMethod("UPI");
        e1.setTransactionType("EXPENSE");
        e1.setDescription("TANGEDCO Electricity Board power consumption bill");
        e1.setTransactionDate(now);
        list.add(e1);

        Expense e2 = new Expense();
        e2.setUserId(userId);
        e2.setMerchantName("CUMTA CMRL");
        e2.setAmount(45.0);
        e2.setCategory("Travel");
        e2.setPaymentMethod("UPI");
        e2.setTransactionType("EXPENSE");
        e2.setDescription("CUMTA Chennai Metro integrated transit fare");
        e2.setTransactionDate(now);
        list.add(e2);

        Expense e3 = new Expense();
        e3.setUserId(userId);
        e3.setMerchantName("MTC Bus");
        e3.setAmount(25.0);
        e3.setCategory("Travel");
        e3.setPaymentMethod("UPI");
        e3.setTransactionType("EXPENSE");
        e3.setDescription("MTC City bus ordinary ticket");
        e3.setTransactionDate(now);
        list.add(e3);

        Expense e4 = new Expense();
        e4.setUserId(userId);
        e4.setMerchantName("Aavin Milk");
        e4.setAmount(180.0);
        e4.setCategory("Grocery");
        e4.setPaymentMethod("UPI");
        e4.setTransactionType("EXPENSE");
        e4.setDescription("Aavin green magic milk packets and curd");
        e4.setTransactionDate(now);
        list.add(e4);

        Expense e5 = new Expense();
        e5.setUserId(userId);
        e5.setMerchantName("CMWSSB MetroWater");
        e5.setAmount(350.0);
        e5.setCategory("Bills");
        e5.setPaymentMethod("UPI");
        e5.setTransactionType("EXPENSE");
        e5.setDescription("Chennai MetroWater and sewerage quarterly charges");
        e5.setTransactionDate(now);
        list.add(e5);

        Expense e6 = new Expense();
        e6.setUserId(userId);
        e6.setMerchantName("Swiggy");
        e6.setAmount(450.0);
        e6.setCategory("Food");
        e6.setPaymentMethod("UPI");
        e6.setTransactionType("EXPENSE");
        e6.setDescription("Dinner order from restaurant");
        e6.setTransactionDate(now);
        list.add(e6);

        Expense e7 = new Expense();
        e7.setUserId(userId);
        e7.setMerchantName("Apollo Pharmacy");
        e7.setAmount(320.0);
        e7.setCategory("Medical");
        e7.setPaymentMethod("UPI");
        e7.setTransactionType("EXPENSE");
        e7.setDescription("Prescription medicine and first aid supplies");
        e7.setTransactionDate(now);
        list.add(e7);

        Expense e8 = new Expense();
        e8.setUserId(userId);
        e8.setMerchantName("DMart Supermarket");
        e8.setAmount(1850.0);
        e8.setCategory("Grocery");
        e8.setPaymentMethod("UPI");
        e8.setTransactionType("EXPENSE");
        e8.setDescription("Monthly household provision groceries");
        e8.setTransactionDate(now);
        list.add(e8);

        return list;
    }

    public static String categorizeMerchant(String merchant) {
        if (merchant == null) return "Others";
        com.smartexpense.android.util.SmartCategorizer.CategoryResult cr =
                com.smartexpense.android.util.SmartCategorizer.categorize(merchant, null, null);
        if (cr != null && cr.category != null && !"Others".equals(cr.category)) {
            return cr.category;
        }
        String m = merchant.toLowerCase().trim();
        if (m.contains("swiggy") || m.contains("zomato") || m.contains("food") || m.contains("restaurant")
                || m.contains("cafe") || m.contains("tea") || m.contains("coffee") || m.contains("kfc")
                || m.contains("mcdonald")) return "Food";
        if (m.contains("dmart") || m.contains("grocery") || m.contains("blinkit") || m.contains("zepto")
                || m.contains("instamart") || m.contains("supermarket") || m.contains("kirana")
                || m.contains("aavin") || m.contains("hatsun") || m.contains("nandini") || m.contains("amul")) return "Grocery";
        if (m.contains("amazon") || m.contains("flipkart") || m.contains("myntra") || m.contains("ajio")
                || m.contains("shopping") || m.contains("clothing") || m.contains("zara")) return "Shopping";
        if (m.contains("uber") || m.contains("ola") || m.contains("rapido") || m.contains("flight")
                || m.contains("metro") || m.contains("irctc") || m.contains("train") || m.contains("bus")
                || m.contains("cumta") || m.contains("cmrl") || m.contains("mtc") || m.contains("tnstc")
                || m.contains("setc") || m.contains("chalo")) return "Travel";
        if (m.contains("apollo") || m.contains("pharmacy") || m.contains("med") || m.contains("hospital")
                || m.contains("doctor") || m.contains("clinic")) return "Medical";
        if (m.contains("petrol") || m.contains("diesel") || m.contains("fuel") || m.contains("indian oil")
                || m.contains("bharat petroleum") || m.contains("hp")) return "Fuel";
        if (m.contains("bill") || m.contains("electricity") || m.contains("airtel") || m.contains("jio")
                || m.contains("wifi") || m.contains("bescom") || m.contains("tneb") || m.contains("tangedco")
                || m.contains("tantransco") || m.contains("cmwssb") || m.contains("metrowater")
                || m.contains("corporation") || m.contains("gcc")) return "Bills";
        if (m.contains("cinema") || m.contains("movie") || m.contains("pvr") || m.contains("netflix")
                || m.contains("hotstar") || m.contains("bookmyshow")) return "Entertainment";
        return "Others";
    }
}
