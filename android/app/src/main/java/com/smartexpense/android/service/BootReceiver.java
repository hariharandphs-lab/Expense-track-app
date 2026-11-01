package com.smartexpense.android.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives BOOT_COMPLETED to re-enable SMS tracking after device restart.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            boolean smsEnabled = context.getSharedPreferences("smart_expense_prefs",
                    Context.MODE_PRIVATE).getBoolean("sms_tracking_enabled", false);
            if (smsEnabled) {
                android.util.Log.d("BootReceiver", "SMS tracking re-enabled after boot");
            }
        }
    }
}
