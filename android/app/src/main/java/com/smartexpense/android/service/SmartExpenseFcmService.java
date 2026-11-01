package com.smartexpense.android.service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.smartexpense.android.util.NotificationHelper;

/**
 * Firebase Cloud Messaging service for push notifications.
 */
public class SmartExpenseFcmService extends FirebaseMessagingService {

    private static final String TAG = "SmartExpenseFCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // TODO: Send token to backend for targeted push notifications
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "FCM message received from: " + message.getFrom());

        String type = message.getData().get("type");
        String title = message.getData().get("title");
        String body = message.getData().get("body");

        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }

        if ("BUDGET_ALERT".equals(type)) {
            String category = message.getData().getOrDefault("category", "Overall");
            double percent = 80.0;
            try {
                percent = Double.parseDouble(
                        message.getData().getOrDefault("percent", "80"));
            } catch (NumberFormatException ignored) {}
            NotificationHelper.showBudgetAlert(this, category, percent);
        } else {
            // Generic notification
            NotificationHelper.showTransactionNotification(
                    this, 0, title, null, "DEBIT", null);
        }
    }
}
