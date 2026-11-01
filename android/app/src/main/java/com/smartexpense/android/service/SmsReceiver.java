package com.smartexpense.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.ExpenseAiEngine;
import com.smartexpense.android.util.NotificationHelper;
import com.smartexpense.android.util.TokenManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

/**
 * Native Android SMS BroadcastReceiver for detecting bank and UPI transaction messages.
 * Automatically saves transactions to Firebase Firestore and local offline storage immediately upon receipt.
 * Operates reliably in the background when the app is minimized, removed from recents, or screen is locked.
 * Notifications are purely informational and displayed only after auto-saving has completed.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "[STAGE: RECEIVE] Received null intent");
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "[STAGE: RECEIVE] SMS broadcast triggered with action: " + action);

        if (!SMS_RECEIVED.equals(action)) {
            Log.d(TAG, "[STAGE: RECEIVE] Ignoring non-SMS intent action: " + action);
            return;
        }

        Context appContext = context.getApplicationContext();
        TokenManager.init(appContext);

        // Check if SMS tracking toggle is enabled and permission is granted
        boolean isTrackingActive = isSmsTrackingEnabled(appContext);
        Log.i(TAG, "[STAGE: RECEIVE] Status check: isSmsTrackingEnabled = " + isTrackingActive);

        if (!isTrackingActive) {
            Log.w(TAG, "[STAGE: RECEIVE] Tracking blocked: Toggle is OFF or RECEIVE_SMS permission is not granted. Discarding broadcast.");
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "[STAGE: RECEIVE] Discarding: Intent extras bundle is null");
            return;
        }

        Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null || pdus.length == 0) {
            Log.w(TAG, "[STAGE: RECEIVE] Discarding: No PDUs found in SMS intent");
            return;
        }

        String format = extras.getString("format");
        Log.d(TAG, "[STAGE: RECEIVE] Decoding " + pdus.length + " PDU(s) in format: " + format);

        StringBuilder body = new StringBuilder();
        String sender = null;
        long receivedAt = 0L;

        try {
            for (Object pdu : pdus) {
                SmsMessage message = format == null
                        ? SmsMessage.createFromPdu((byte[]) pdu)
                        : SmsMessage.createFromPdu((byte[]) pdu, format);
                if (message == null) continue;
                if (sender == null) sender = message.getDisplayOriginatingAddress();
                if (receivedAt == 0L) receivedAt = message.getTimestampMillis();
                if (body.length() > 0) body.append(' ');
                body.append(message.getDisplayMessageBody());
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "[STAGE: RECEIVE] Error decoding SMS PDUs: " + e.getMessage(), e);
            return;
        }

        if (body.length() == 0) {
            Log.w(TAG, "[STAGE: RECEIVE] Decoded SMS body is empty. Discarding.");
            return;
        }

        Log.i(TAG, "[STAGE: RECEIVE] SMS successfully decoded. Originating Address: [" + sender
                + "], Timestamp: " + receivedAt + ", Body length: " + body.length());

        // ═════════════════════════════════════════════════════════════════════
        // STAGE 2: PARSE
        // ═════════════════════════════════════════════════════════════════════
        Log.d(TAG, "[STAGE: PARSE] Beginning parsing for sender: [" + sender + "]");
        long eventTime = receivedAt == 0L ? System.currentTimeMillis() : receivedAt;
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(body.toString(), sender, eventTime);

        if (tx == null) {
            Log.i(TAG, "[STAGE: PARSE] Message discarded: Not a completed bank/UPI transaction, or rejected as OTP/promotional.");
            return;
        }

        Log.i(TAG, "[STAGE: PARSE] PARSING SUCCESS -> Amount: ₹" + tx.amount
                + ", Type: " + tx.transactionType
                + ", Merchant: " + tx.merchantName
                + ", Bank: " + tx.bankName
                + ", Method: " + tx.paymentMethod
                + ", Ref: " + tx.referenceNumber
                + ", TxId: " + tx.clientTransactionId);

        // ═════════════════════════════════════════════════════════════════════
        // STAGE 3: SAVE (AUTO-SAVE IMMEDIATELY)
        // ═════════════════════════════════════════════════════════════════════
        Log.i(TAG, "[STAGE: SAVE] Initiating immediate auto-save pipeline for TxId: " + tx.clientTransactionId);

        // Prepare domain expense model using context-aware SmartCategorizer
        String inferredCategory = tx.category;
        if (inferredCategory == null || inferredCategory.isEmpty() || "Others".equals(inferredCategory)) {
            com.smartexpense.android.util.SmartCategorizer.CategoryResult cat =
                    com.smartexpense.android.util.SmartCategorizer.categorize(appContext, tx.merchantName, tx.upiId, body.toString());
            inferredCategory = cat.category;
        }
        Expense expense = new Expense();
        expense.setId(tx.clientTransactionId);
        expense.setAmount(tx.amount);
        expense.setCurrency("INR");
        expense.setMerchantName(tx.merchantName);
        expense.setCategory(inferredCategory);
        expense.setPaymentMethod(tx.paymentMethod);
        expense.setTransactionType(tx.transactionType);
        expense.setSource("SMS");
        expense.setStatus("CONFIRMED");
        expense.setBankName(tx.bankName);
        expense.setReferenceNumber(tx.referenceNumber);
        expense.setTransactionDate(tx.transactionDate);
        expense.setClientTransactionId(tx.clientTransactionId);
        expense.setCreatedAt(LocalDateTime.now().toString());
        expense.setDescription("SMS Transaction: ₹" + tx.amount + " "
                + ("CREDIT".equals(tx.transactionType) ? "received from " : "paid to ") + tx.merchantName);

        // API payload for remote backend
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", tx.amount);
        payload.put("currency", "INR");
        payload.put("merchantName", tx.merchantName);
        payload.put("category", inferredCategory);
        payload.put("paymentMethod", tx.paymentMethod);
        payload.put("transactionType", tx.transactionType);
        payload.put("source", "SMS");
        payload.put("bankName", tx.bankName);
        payload.put("transactionDate", tx.transactionDate);
        payload.put("clientTransactionId", tx.clientTransactionId);
        payload.put("referenceNumber", tx.referenceNumber);
        payload.put("description", expense.getDescription());

        // Resolve active user ID consistently across storage and UI
        final String effectiveUserId = OfflineMockStorage.resolveCurrentUserId();
        expense.setUserId(effectiveUserId);
        payload.put("userId", effectiveUserId);

        // Acquire WakeLock to guarantee CPU execution when phone screen is locked or app is in background
        PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartExpense:SmsProcessingWakeLock");
            wakeLock.acquire(15000); // 15 seconds max
        }
        final PowerManager.WakeLock activeWakeLock = wakeLock;

        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                // 3A: Save directly to Firebase Firestore synchronously with duplicate prevention
                Log.d(TAG, "[STAGE: SAVE] Auto-saving directly to Firebase Firestore synchronously (TxId: " + tx.clientTransactionId + ")...");
                boolean firestoreOk = FirebaseExpenseRepository.saveExpenseSynchronous(appContext, expense, effectiveUserId, 8);
                Log.i(TAG, "[STAGE: SAVE] Firebase Firestore auto-save complete: success=" + firestoreOk);

                // 3B: Save directly to local offline storage engine (ensures immediate UI visibility in app)
                Expense savedLocal = OfflineMockStorage.addExpense(expense);
                Log.i(TAG, "[STAGE: SAVE] Local storage auto-save updated: ID=" + savedLocal.getId()
                        + ", Total in memory/storage: " + OfflineMockStorage.getExpenses().size());

                // 3C: Sync with remote Spring Boot backend via ApiClient
                try {
                    Response<ApiResponse<Expense>> apiResponse = ApiClient
                            .createService(ApiService.class)
                            .createExpense(payload)
                            .execute();

                    if (apiResponse.isSuccessful() && apiResponse.body() != null && apiResponse.body().getData() != null) {
                        Log.i(TAG, "[STAGE: SAVE] Backend API sync confirmed: " + apiResponse.body().getData().getId());
                    } else {
                        Log.d(TAG, "[STAGE: SAVE] API sync queued for background WorkManager.");
                        SmsExpenseSyncWorker.enqueue(appContext, payload, tx.clientTransactionId, effectiveUserId);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "[STAGE: SAVE] Network unreachable; queuing to WorkManager: " + e.getMessage());
                    try {
                        SmsExpenseSyncWorker.enqueue(appContext, payload, tx.clientTransactionId, effectiveUserId);
                    } catch (Exception ex) {
                        Log.w(TAG, "[STAGE: SAVE] WorkManager enqueue warning: " + ex.getMessage());
                    }
                }

                // 3D: Show Android status notification ONLY AFTER auto-save has completed!
                // If the transaction needs user confirmation (personal-name seller or ambiguous P2P), provide quick categorization action.
                try {
                    String pendingId = (tx.needsUserConfirmation || tx.isProbablyP2P) ? expense.getId() : null;
                    NotificationHelper.showTransactionNotification(
                            appContext, expense.getAmount(), expense.getMerchantName(),
                            expense.getCategory(), expense.getTransactionType(), pendingId);
                    Log.i(TAG, "[STAGE: SAVE] Informational auto-save notification posted: ₹" + expense.getAmount()
                            + " at " + expense.getMerchantName() + " (PendingConfirm=" + (pendingId != null) + ")");
                } catch (Exception notifEx) {
                    Log.w(TAG, "[STAGE: SAVE] Could not display notification: " + notifEx.getMessage());
                }

                // 3E: Broadcast local intent for active UI screens to refresh immediately
                try {
                    Intent refreshIntent = new Intent("com.smartexpense.android.ACTION_EXPENSE_SAVED");
                    refreshIntent.putExtra("expenseId", expense.getId());
                    appContext.sendBroadcast(refreshIntent);
                } catch (Exception ignored) {}

            } catch (Exception e) {
                Log.e(TAG, "[STAGE: SAVE] Unexpected error in save pipeline: " + e.getMessage(), e);
            } finally {
                if (activeWakeLock != null && activeWakeLock.isHeld()) {
                    try {
                        activeWakeLock.release();
                    } catch (Exception ignored) {}
                }
                pendingResult.finish();
                Log.i(TAG, "[STAGE: SAVE] Pipeline execution completed for TxId: " + tx.clientTransactionId);
            }
        }).start();
    }

    /**
     * Determines whether SMS transaction tracking is enabled.
     * Evaluates both the SharedPreferences toggle and system runtime permissions.
     */
    public static boolean isSmsTrackingEnabled(Context context) {
        if (context == null) return false;
        boolean hasPermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) return false;

        try {
            SharedPreferences prefs = context.getSharedPreferences("smart_expense_prefs", Context.MODE_PRIVATE);
            // Default to enabled if permission is granted
            return prefs.getBoolean("sms_tracking_enabled", true);
        } catch (Exception e) {
            Log.w(TAG, "Error reading sms tracking preference: " + e.getMessage());
            return true;
        }
    }
}
