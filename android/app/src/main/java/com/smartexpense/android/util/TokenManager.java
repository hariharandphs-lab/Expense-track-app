package com.smartexpense.android.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.smartexpense.android.SmartExpenseApp;

/**
 * Manages JWT token storage in SharedPreferences.
 * Fully thread-safe and resilient against Android process death and background recreation.
 */
public class TokenManager {

    private static final String PREFS_NAME = "smart_expense_prefs";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";

    private static volatile SharedPreferences prefs;

    public static synchronized void init(Context context) {
        if (prefs == null && context != null) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void setMockPrefsForTesting(SharedPreferences mock) {
        prefs = mock;
    }

    private static SharedPreferences getPrefs() {
        if (prefs == null) {
            Context context = SmartExpenseApp.getAppContext();
            if (context != null) {
                init(context);
            }
        }
        return prefs;
    }

    public static void saveToken(String token, String refreshToken) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .apply();
        }
    }

    public static String getToken() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_TOKEN, null) : null;
    }

    public static String getRefreshToken() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_REFRESH_TOKEN, null) : null;
    }

    public static void saveUserInfo(String userId, String email, String displayName) {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit()
                    .putString(KEY_USER_ID, userId)
                    .putString(KEY_USER_EMAIL, email)
                    .putString(KEY_USER_NAME, displayName)
                    .apply();
        }
    }

    public static String getUserId() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_USER_ID, null) : null;
    }

    public static String getUserEmail() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_USER_EMAIL, null) : null;
    }

    public static String getUserName() {
        SharedPreferences p = getPrefs();
        return p != null ? p.getString(KEY_USER_NAME, null) : null;
    }

    public static boolean isLoggedIn() {
        return getToken() != null;
    }

    public static void clear() {
        SharedPreferences p = getPrefs();
        if (p != null) {
            p.edit()
                    .remove(KEY_TOKEN)
                    .remove(KEY_REFRESH_TOKEN)
                    .remove(KEY_USER_ID)
                    .remove(KEY_USER_EMAIL)
                    .remove(KEY_USER_NAME)
                    .apply();
        }
    }
}
