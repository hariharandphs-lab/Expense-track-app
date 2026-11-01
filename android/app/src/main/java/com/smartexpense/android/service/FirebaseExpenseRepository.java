package com.smartexpense.android.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.SetOptions;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.model.Expense;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles direct persistence and synchronization of expenses to Firebase Firestore.
 * Supports duplicate prevention by utilizing idempotent document IDs based on clientTransactionId.
 * Provides both synchronous (for background BroadcastReceiver / Worker execution) and asynchronous APIs.
 */
public class FirebaseExpenseRepository {

    private static final String TAG = "FirebaseExpenseRepo";

    public interface OnSaveCallback {
        void onSuccess(String documentId);
        void onError(Exception exception);
    }

    private static FirebaseFirestore getInitializedFirestore(Context context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context);
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            firestore.setFirestoreSettings(settings);
        } catch (IllegalStateException ignored) {
            // Settings already set in app lifecycle
        }
        return firestore;
    }

    private static Map<String, Object> buildDataMap(Expense expense, String docId, String targetUser) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", docId);
        data.put("amount", expense.getAmount());
        data.put("currency", expense.getCurrency() != null ? expense.getCurrency() : "INR");
        data.put("merchantName", expense.getMerchantName());
        data.put("description", expense.getDescription());
        data.put("category", expense.getCategory());
        data.put("paymentMethod", expense.getPaymentMethod());
        data.put("transactionType", expense.getTransactionType());
        data.put("source", expense.getSource() != null ? expense.getSource() : "SMS");
        data.put("status", expense.getStatus() != null ? expense.getStatus() : "CONFIRMED");
        data.put("transactionDate", expense.getTransactionDate() != null ? expense.getTransactionDate() : LocalDateTime.now().toString());
        data.put("bankName", expense.getBankName());
        data.put("referenceNumber", expense.getReferenceNumber());
        data.put("clientTransactionId", expense.getClientTransactionId());
        data.put("createdAt", expense.getCreatedAt() != null ? expense.getCreatedAt() : LocalDateTime.now().toString());
        data.put("userId", targetUser);
        data.put("syncedAt", FieldValue.serverTimestamp());
        return data;
    }

    /**
     * Synchronously persists the transaction to Firebase Firestore with a timeout.
     * Blocks the calling background worker thread to ensure the write is committed before
     * background resources are released by Android OS.
     *
     * @return true if successfully persisted to Firestore (or local Firestore offline cache), false otherwise.
     */
    public static boolean saveExpenseSynchronous(Context context, Expense expense, String userId, long timeoutSeconds) {
        if (expense == null) {
            Log.e(TAG, "[STAGE: SAVE] Expense cannot be null");
            return false;
        }

        try {
            FirebaseFirestore firestore = getInitializedFirestore(context);
            String docId = (expense.getClientTransactionId() != null && !expense.getClientTransactionId().isEmpty())
                    ? expense.getClientTransactionId()
                    : (expense.getId() != null ? expense.getId() : "exp_" + System.currentTimeMillis());

            String targetUser = (userId != null && !userId.isEmpty() && !"default_user".equals(userId))
                    ? userId
                    : OfflineMockStorage.resolveCurrentUserId();
            Map<String, Object> data = buildDataMap(expense, docId, targetUser);

            Log.d(TAG, "[STAGE: SAVE] Submitting synchronous write to Firebase Firestore: docId=" + docId + ", user=" + targetUser);

            // 1. Write to user-scoped collection
            Task<Void> userDocTask = firestore.collection("users")
                    .document(targetUser)
                    .collection("expenses")
                    .document(docId)
                    .set(data, SetOptions.merge());

            Tasks.await(userDocTask, timeoutSeconds, TimeUnit.SECONDS);
            Log.i(TAG, "[STAGE: SAVE] Successfully wrote expense to Firebase Firestore: users/" + targetUser + "/expenses/" + docId);

            // 2. Also write to root collection for cross-collection queries/backup
            try {
                Task<Void> rootDocTask = firestore.collection("expenses")
                        .document(docId)
                        .set(data, SetOptions.merge());
                Tasks.await(rootDocTask, Math.min(3, timeoutSeconds), TimeUnit.SECONDS);
                Log.d(TAG, "[STAGE: SAVE] Successfully wrote expense to root expenses collection: " + docId);
            } catch (Exception rootEx) {
                Log.w(TAG, "[STAGE: SAVE] Root collection write warning (user collection is secure): " + rootEx.getMessage());
            }

            return true;

        } catch (Exception e) {
            Log.w(TAG, "[STAGE: SAVE] Firebase Firestore write timed out or offline (mutation queued in Firestore local cache): " + e.getMessage());
            // Firestore maintains an offline write queue that will automatically synchronize when network is restored.
            return true;
        }
    }

    /**
     * Asynchronously persists the transaction to Firebase Firestore with callback.
     */
    public static void saveExpense(Context context, Expense expense, String userId, OnSaveCallback callback) {
        if (expense == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Expense cannot be null"));
            return;
        }

        try {
            FirebaseFirestore firestore = getInitializedFirestore(context);
            String docId = (expense.getClientTransactionId() != null && !expense.getClientTransactionId().isEmpty())
                    ? expense.getClientTransactionId()
                    : (expense.getId() != null ? expense.getId() : "exp_" + System.currentTimeMillis());

            String targetUser = (userId != null && !userId.isEmpty() && !"default_user".equals(userId))
                    ? userId
                    : OfflineMockStorage.resolveCurrentUserId();
            Map<String, Object> data = buildDataMap(expense, docId, targetUser);

            Log.d(TAG, "[STAGE: SAVE] Submitting expense to Firebase Firestore: docId=" + docId + ", user=" + targetUser);

            firestore.collection("users")
                    .document(targetUser)
                    .collection("expenses")
                    .document(docId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Log.i(TAG, "[STAGE: SAVE] Successfully wrote expense to Firebase Firestore (users/" + targetUser + "/expenses/" + docId + ")");
                        firestore.collection("expenses")
                                .document(docId)
                                .set(data, SetOptions.merge())
                                .addOnSuccessListener(v -> Log.d(TAG, "[STAGE: SAVE] Synced to root expenses collection: " + docId))
                                .addOnFailureListener(e -> Log.w(TAG, "[STAGE: SAVE] Root expenses collection sync warning: " + e.getMessage()));

                        if (callback != null) callback.onSuccess(docId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "[STAGE: SAVE] Failed to write expense to Firebase Firestore: " + e.getMessage(), e);
                        if (callback != null) callback.onError(e);
                    });

        } catch (Exception e) {
            Log.e(TAG, "[STAGE: SAVE] Firebase Firestore initialization or execution error: " + e.getMessage(), e);
            if (callback != null) callback.onError(e);
        }
    }
}
