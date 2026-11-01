package com.smartexpense.android.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.smartexpense.android.R;
import com.smartexpense.android.ui.expenses.AddExpenseActivity;
import com.smartexpense.android.ui.main.MainActivity;

/**
 * Helper class for building and showing app notifications.
 */
public class NotificationHelper {

    private static final String CHANNEL_TRANSACTIONS = "transactions";
    private static final String CHANNEL_BUDGET = "budget_alerts";
    private static final String CHANNEL_INSIGHTS = "ai_insights";

    public static void createChannels(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.createNotificationChannel(new NotificationChannel(
                            CHANNEL_TRANSACTIONS, "Transactions",
                            NotificationManager.IMPORTANCE_DEFAULT));

                    nm.createNotificationChannel(new NotificationChannel(
                            CHANNEL_BUDGET, "Budget Alerts",
                            NotificationManager.IMPORTANCE_HIGH));

                    nm.createNotificationChannel(new NotificationChannel(
                            CHANNEL_INSIGHTS, "AI Insights",
                            NotificationManager.IMPORTANCE_LOW));
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Show a notification for a newly detected SMS transaction.
     * If the category is pending, the action button opens AddExpenseActivity for categorization.
     */
    public static void showTransactionNotification(Context context, double amount,
            String merchant, String category, String type, String pendingExpenseId) {

        if (context == null) return;
        createChannels(context);
        boolean isCredit = "CREDIT".equalsIgnoreCase(type);
        String title = isCredit ?
                "💰 Money Received · Auto-saved" : "💳 Transaction Detected · Auto-saved";
        String body = String.format("₹%.0f %s %s (Saved to Firebase)",
                amount,
                isCredit ? "received from" : "paid to",
                merchant != null ? merchant : "Unknown Merchant");

        Intent tapIntent = new Intent(context, MainActivity.class);
        PendingIntent tapPending = PendingIntent.getActivity(context, 0,
                tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CHANNEL_TRANSACTIONS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(tapPending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // If category is unknown, add action to categorize
        if (pendingExpenseId != null) {
            Intent actionIntent = new Intent(context, AddExpenseActivity.class);
            actionIntent.putExtra("expenseId", pendingExpenseId);
            actionIntent.putExtra("mode", "CATEGORIZE");
            PendingIntent actionPending = PendingIntent.getActivity(context, 1,
                    actionIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_category, "Categorize", actionPending);
        }

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception e) {
            // Permission not granted or notification error — silently ignore
        }
    }

    public static void showBudgetAlert(Context context, String category, double percent) {
        if (context == null) return;
        createChannels(context);
        String title = "⚠️ Budget Alert";
        String body = String.format("You've used %.0f%% of your %s budget", percent, category != null ? category : "monthly");

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.putExtra("navigate", "budget");
        PendingIntent pending = PendingIntent.getActivity(context, 2,
                tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CHANNEL_BUDGET)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception e) {
            // ignore
        }
    }
}
