package com.smartexpense.android.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smartexpense.android.ui.chat.AiChatbotBottomSheet.ChatMessage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages persisting and restoring AI assistant chat conversations per-user.
 */
public class ChatHistoryManager {

    private static final String PREFS_NAME = "smart_expense_prefs";
    private static final String KEY_PREFIX = "chat_history_";
    private static final Gson gson = new Gson();

    private static String resolveKey(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            userId = TokenManager.getUserId();
        }
        if (userId == null || userId.trim().isEmpty()) {
            userId = "default_user";
        }
        return KEY_PREFIX + userId;
    }

    public static void saveMessages(Context context, String userId, List<ChatMessage> messages) {
        if (context == null || messages == null) return;
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Cap at 100 messages to keep storage fast & snappy
            List<ChatMessage> toSave = messages;
            if (messages.size() > 100) {
                toSave = new ArrayList<>(messages.subList(messages.size() - 100, messages.size()));
            }
            String json = gson.toJson(toSave);
            prefs.edit().putString(resolveKey(userId), json).apply();
        } catch (Exception e) {
            // log/ignore to prevent crashes
        }
    }

    public static List<ChatMessage> loadMessages(Context context, String userId) {
        if (context == null) return new ArrayList<>();
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(resolveKey(userId), null);
            if (json != null && !json.trim().isEmpty()) {
                Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
                List<ChatMessage> list = gson.fromJson(json, type);
                if (list != null) return list;
            }
        } catch (Exception e) {
            // fallback
        }
        return new ArrayList<>();
    }

    public static void clearHistory(Context context, String userId) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(resolveKey(userId)).apply();
        } catch (Exception ignored) {}
    }
}
