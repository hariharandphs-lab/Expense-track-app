package com.smartexpense.android;

import android.content.SharedPreferences;

import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.TokenManager;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * End-to-end verification of multi-user data isolation:
 * 1. Account A adds expenses, logs out.
 * 2. Account B logs in -> Account A's expenses are NOT visible.
 * 3. Account B adds expenses.
 * 4. Account B logs out -> Account A logs back in.
 * 5. Account A sees only Account A's expenses intact.
 * 6. Demo Mode is completely isolated from authenticated accounts.
 * 7. Budgets and analytics are isolated per user.
 */
public class MultiUserDataIsolationTest {

    private FakeSharedPreferences fakePrefs;

    @Before
    public void setUp() {
        fakePrefs = new FakeSharedPreferences();
        TokenManager.setMockPrefsForTesting(fakePrefs);
        OfflineMockStorage.setMockPrefsForTesting(fakePrefs);
    }

    @Test
    public void testTwoAccountsExpenseIsolation() {
        // --- STEP 1: Log in as Account A ---
        TokenManager.saveUserInfo("user_A_123", "alice@example.com", "Alice");
        TokenManager.saveToken("mock_jwt_token_A", "mock_refresh_A");
        assertEquals("user_A_123", OfflineMockStorage.resolveCurrentUserId());

        // Account A adds an expense (Swiggy, ₹450, Food)
        Expense expA = new Expense();
        expA.setMerchantName("Swiggy");
        expA.setAmount(450.0);
        expA.setCategory("Food");
        expA.setDescription("Lunch order");
        OfflineMockStorage.addExpense(expA);

        List<Expense> expensesA = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesA.size());
        assertEquals("Swiggy", expensesA.get(0).getMerchantName());
        assertEquals(450.0, expensesA.get(0).getAmount(), 0.001);
        assertEquals("user_A_123", expensesA.get(0).getUserId());

        // --- STEP 2: Account A logs out ---
        TokenManager.clear();
        OfflineMockStorage.clearInMemoryCache();
        assertNull(TokenManager.getUserId());

        // --- STEP 3: Log in as Account B ---
        TokenManager.saveUserInfo("user_B_456", "bob@example.com", "Bob");
        TokenManager.saveToken("mock_jwt_token_B", "mock_refresh_B");
        assertEquals("user_B_456", OfflineMockStorage.resolveCurrentUserId());

        // Account B must NOT see Account A's expenses
        List<Expense> expensesB = OfflineMockStorage.getExpenses();
        assertTrue("Account B must not see Account A's expenses", expensesB.isEmpty());

        // Account B's summary should be 0
        Map<String, Object> summaryBInitial = OfflineMockStorage.getMonthlySummary(10, 2026);
        assertEquals(0.0, ((Number) summaryBInitial.get("total")).doubleValue(), 0.001);

        // Account B adds an expense (DMart, ₹3,200, Grocery)
        Expense expB = new Expense();
        expB.setMerchantName("DMart");
        expB.setAmount(3200.0);
        expB.setCategory("Grocery");
        expB.setDescription("Monthly groceries");
        OfflineMockStorage.addExpense(expB);

        expensesB = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesB.size());
        assertEquals("DMart", expensesB.get(0).getMerchantName());
        assertEquals(3200.0, expensesB.get(0).getAmount(), 0.001);
        assertEquals("user_B_456", expensesB.get(0).getUserId());

        // --- STEP 4: Account B logs out ---
        TokenManager.clear();
        OfflineMockStorage.clearInMemoryCache();

        // --- STEP 5: Account A logs back in ---
        TokenManager.saveUserInfo("user_A_123", "alice@example.com", "Alice");
        TokenManager.saveToken("mock_jwt_token_A_again", "mock_refresh_A_again");
        assertEquals("user_A_123", OfflineMockStorage.resolveCurrentUserId());

        // Verify Account A sees ONLY Account A's original expense (Swiggy), NOT DMart
        List<Expense> expensesAReloaded = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesAReloaded.size());
        assertEquals("Swiggy", expensesAReloaded.get(0).getMerchantName());
        assertEquals(450.0, expensesAReloaded.get(0).getAmount(), 0.001);
        assertEquals("user_A_123", expensesAReloaded.get(0).getUserId());

        // Account A's monthly total must be ₹450
        Map<String, Object> summaryAReloaded = OfflineMockStorage.getMonthlySummary(10, 2026);
        assertEquals(450.0, ((Number) summaryAReloaded.get("total")).doubleValue(), 0.001);
    }

    @Test
    public void testDemoModeSeparation() {
        // Authenticated user adds an expense
        TokenManager.saveUserInfo("user_real_999", "real@example.com", "Real User");
        Expense realExp = new Expense();
        realExp.setMerchantName("Apple Store");
        realExp.setAmount(120000.0);
        realExp.setCategory("Shopping");
        OfflineMockStorage.addExpense(realExp);

        // Logout
        TokenManager.clear();
        OfflineMockStorage.clearInMemoryCache();

        // Demo Mode (unauthenticated)
        assertEquals("demo-user", OfflineMockStorage.resolveCurrentUserId());
        List<Expense> demoExpenses = OfflineMockStorage.getExpenses();
        assertTrue("Demo mode must not see authenticated user expenses", demoExpenses.isEmpty());

        // Add expense in Demo Mode
        Expense demoExp = new Expense();
        demoExp.setMerchantName("Demo Coffee");
        demoExp.setAmount(150.0);
        demoExp.setCategory("Food");
        OfflineMockStorage.addExpense(demoExp);

        demoExpenses = OfflineMockStorage.getExpenses();
        assertEquals(1, demoExpenses.size());
        assertEquals("demo-user", demoExpenses.get(0).getUserId());

        // Switch back to authenticated user
        TokenManager.saveUserInfo("user_real_999", "real@example.com", "Real User");
        List<Expense> realReloaded = OfflineMockStorage.getExpenses();
        assertEquals(1, realReloaded.size());
        assertEquals("Apple Store", realReloaded.get(0).getMerchantName());
        assertEquals("user_real_999", realReloaded.get(0).getUserId());
    }

    @Test
    public void testBudgetIsolationBetweenAccounts() {
        // User A sets budget to 50,000
        TokenManager.saveUserInfo("user_A", "a@test.com", "A");
        OfflineMockStorage.saveBudget(50000.0, 10, 2026);
        Budget budgetA = OfflineMockStorage.getBudget(10, 2026);
        assertEquals(50000.0, budgetA.getTotalBudget(), 0.001);

        // Switch to User B
        TokenManager.clear();
        TokenManager.saveUserInfo("user_B", "b@test.com", "B");

        // User B sets budget to 15,000
        OfflineMockStorage.saveBudget(15000.0, 10, 2026);
        Budget budgetB = OfflineMockStorage.getBudget(10, 2026);
        assertEquals(15000.0, budgetB.getTotalBudget(), 0.001);

        // Switch back to User A
        TokenManager.clear();
        TokenManager.saveUserInfo("user_A", "a@test.com", "A");
        Budget budgetARetrieved = OfflineMockStorage.getBudget(10, 2026);
        assertEquals(50000.0, budgetARetrieved.getTotalBudget(), 0.001);
    }

    @Test
    public void testSmsTransactionAutoSaveAndIsolation() {
        // User A logs in
        TokenManager.saveUserInfo("user_A_sms", "a@sms.com", "User A");

        // Simulate incoming SMS parse & save
        Expense smsExpA = new Expense();
        smsExpA.setId("sms_tx_001");
        smsExpA.setMerchantName("Swiggy");
        smsExpA.setAmount(450.0);
        smsExpA.setCategory("Food");
        smsExpA.setSource("SMS");
        smsExpA.setBankName("HDFC Bank");
        smsExpA.setPaymentMethod("UPI");
        smsExpA.setTransactionType("DEBIT");
        smsExpA.setClientTransactionId("sms_tx_001");

        // Save via OfflineMockStorage
        OfflineMockStorage.addExpense(smsExpA);

        List<Expense> expensesA = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesA.size());
        assertEquals("Swiggy", expensesA.get(0).getMerchantName());
        assertEquals("user_A_sms", expensesA.get(0).getUserId());
        assertEquals("SMS", expensesA.get(0).getSource());

        // Logout User A
        TokenManager.clear();
        OfflineMockStorage.clearInMemoryCache();

        // User B logs in
        TokenManager.saveUserInfo("user_B_sms", "b@sms.com", "User B");

        // User B must NOT see User A's SMS transaction
        List<Expense> expensesB = OfflineMockStorage.getExpenses();
        assertTrue("User B must not see User A's SMS transaction", expensesB.isEmpty());

        // User B receives an SMS
        Expense smsExpB = new Expense();
        smsExpB.setId("sms_tx_002");
        smsExpB.setMerchantName("Zomato");
        smsExpB.setAmount(1200.0);
        smsExpB.setCategory("Food");
        smsExpB.setSource("SMS");
        smsExpB.setBankName("SBI");
        smsExpB.setPaymentMethod("UPI");
        smsExpB.setTransactionType("DEBIT");
        smsExpB.setClientTransactionId("sms_tx_002");

        OfflineMockStorage.addExpense(smsExpB);

        expensesB = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesB.size());
        assertEquals("Zomato", expensesB.get(0).getMerchantName());
        assertEquals("user_B_sms", expensesB.get(0).getUserId());

        // Logout User B -> User A logs back in
        TokenManager.clear();
        OfflineMockStorage.clearInMemoryCache();
        TokenManager.saveUserInfo("user_A_sms", "a@sms.com", "User A");

        List<Expense> expensesAReloaded = OfflineMockStorage.getExpenses();
        assertEquals(1, expensesAReloaded.size());
        assertEquals("Swiggy", expensesAReloaded.get(0).getMerchantName());
        assertEquals("user_A_sms", expensesAReloaded.get(0).getUserId());
    }

    @Test
    public void testRecoveryOfDefaultUserAndUnassignedSmsExpenses() {
        // Simulates an SMS transaction that was saved directly with default_user or null UID during regression
        List<Expense> legacyList = new java.util.ArrayList<>();
        Expense unassignedExp = new Expense();
        unassignedExp.setId("legacy_sms_101");
        unassignedExp.setMerchantName("Uber");
        unassignedExp.setAmount(280.0);
        unassignedExp.setCategory("Travel");
        unassignedExp.setSource("SMS");
        unassignedExp.setUserId("default_user"); // The regression state
        legacyList.add(unassignedExp);

        fakePrefs.edit().putString("offline_expenses_master_list",
                new com.google.gson.Gson().toJson(legacyList)).apply();

        // Active user logs in
        TokenManager.saveUserInfo("user_recovered", "rec@test.com", "Recovered User");

        // getExpenses() must automatically recover/claim the transaction for the active user
        List<Expense> expenses = OfflineMockStorage.getExpenses();
        assertEquals(1, expenses.size());
        assertEquals("Uber", expenses.get(0).getMerchantName());
        assertEquals(280.0, expenses.get(0).getAmount(), 0.001);
        assertEquals("user_recovered", expenses.get(0).getUserId());
    }

    @Test
    public void testSmsTrackingInDemoMode() {
        // Ensure unauthenticated / demo mode
        TokenManager.clear();
        assertEquals("demo-user", OfflineMockStorage.resolveCurrentUserId());

        Expense demoSms = new Expense();
        demoSms.setId("demo_sms_99");
        demoSms.setMerchantName("Tea Post");
        demoSms.setAmount(40.0);
        demoSms.setCategory("Food");
        demoSms.setSource("SMS");

        OfflineMockStorage.addExpense(demoSms);

        List<Expense> demoExpenses = OfflineMockStorage.getExpenses();
        assertEquals(1, demoExpenses.size());
        assertEquals("Tea Post", demoExpenses.get(0).getMerchantName());
        assertEquals("demo-user", demoExpenses.get(0).getUserId());
    }

    /**
     * In-memory implementation of SharedPreferences for robust unit testing without Robolectric.
     */
    public static class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, Object> storage = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(storage);
        }

        @Override
        public String getString(String key, String defValue) {
            Object val = storage.get(key);
            return (val instanceof String) ? (String) val : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object val = storage.get(key);
            return (val instanceof Set) ? (Set<String>) val : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object val = storage.get(key);
            return (val instanceof Integer) ? (Integer) val : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object val = storage.get(key);
            return (val instanceof Long) ? (Long) val : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object val = storage.get(key);
            return (val instanceof Float) ? (Float) val : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object val = storage.get(key);
            return (val instanceof Boolean) ? (Boolean) val : defValue;
        }

        @Override
        public boolean contains(String key) {
            return storage.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private class FakeEditor implements Editor {
            private final Map<String, Object> temp = new HashMap<>();
            private final Set<String> toRemove = new HashSet<>();
            private boolean clearAll = false;

            @Override
            public Editor putString(String key, String value) {
                temp.put(key, value);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                temp.put(key, values);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                temp.put(key, value);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                temp.put(key, value);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                temp.put(key, value);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                temp.put(key, value);
                toRemove.remove(key);
                return this;
            }

            @Override
            public Editor remove(String key) {
                toRemove.add(key);
                temp.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                clearAll = true;
                temp.clear();
                toRemove.clear();
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clearAll) {
                    storage.clear();
                }
                for (String key : toRemove) {
                    storage.remove(key);
                }
                storage.putAll(temp);
            }
        }
    }
}
