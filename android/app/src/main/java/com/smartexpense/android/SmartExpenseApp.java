package com.smartexpense.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.firebase.FirebaseApp;
import com.smartexpense.android.util.TokenManager;

import java.util.Locale;

/**
 * Application class — initializes Firebase and safely manages language preferences
 * without breaking OEM contexts (especially Xiaomi HyperOS / MIUI).
 */
public class SmartExpenseApp extends Application {

    private static final String TAG = "SmartExpenseApp";
    private static volatile SmartExpenseApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        try {
            TokenManager.init(this);
        } catch (Exception e) {
            Log.w(TAG, "TokenManager init warning: " + e.getMessage());
        }
        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            Log.w(TAG, "FirebaseApp init warning: " + e.getMessage());
        }
        applyLanguage();

        // Asynchronously apply smart categorization to any existing uncategorized transactions
        new Thread(() -> {
            try {
                com.smartexpense.android.api.OfflineMockStorage.categorizeUncategorizedExpenses(this);
            } catch (Exception ignored) {}
        }).start();
    }

    public static SmartExpenseApp getInstance() {
        return instance;
    }

    /**
     * Resolves an active application context safely, even during early initialization
     * or background process recreation.
     */
    public static Context getAppContext() {
        if (instance != null) {
            return instance.getApplicationContext();
        }
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        // Crucial for Xiaomi HyperOS / MIUI: Do NOT wrap the Application base context
        // with createConfigurationContext. Application must maintain the system ContextImpl.
        super.attachBaseContext(base);
    }

    private void applyLanguage() {
        try {
            SharedPreferences prefs = getSharedPreferences("smart_expense_prefs", Context.MODE_PRIVATE);
            String lang = prefs.getString("language", "en");
            if (lang != null && !lang.isEmpty()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
            }
        } catch (Exception e) {
            Log.w(TAG, "applyLanguage warning: " + e.getMessage());
        }
    }

    public static Context applyLanguageToContext(Context context) {
        if (context == null) return null;
        try {
            SharedPreferences prefs = context.getSharedPreferences("smart_expense_prefs", Context.MODE_PRIVATE);
            String lang = prefs.getString("language", "en");

            Locale locale = new Locale(lang);
            Locale.setDefault(locale);

            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);

            return context.createConfigurationContext(config);
        } catch (Exception e) {
            return context;
        }
    }

    /**
     * Change the app language and persist the preference using the official AndroidX API.
     */
    public static void setLanguage(Context context, String languageCode) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("smart_expense_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("language", languageCode).apply();
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode));
        } catch (Exception e) {
            Log.w(TAG, "setLanguage warning: " + e.getMessage());
        }
    }
}
