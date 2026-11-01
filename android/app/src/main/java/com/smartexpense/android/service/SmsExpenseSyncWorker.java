package com.smartexpense.android.service;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.util.NotificationHelper;
import com.smartexpense.android.util.TokenManager;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import retrofit2.Response;

/** Durable, network-aware, encrypted SMS transaction upload queue. */
public class SmsExpenseSyncWorker extends Worker {
    private static final String TAG = "SmsExpenseSyncWorker";
    private static final String KEY_ALIAS = "smart_expense_sms_queue_v1";
    private static final String INPUT_PAYLOAD = "encrypted_payload";
    private static final String INPUT_USER = "owner_user_id";

    public SmsExpenseSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, Map<String, Object> transaction,
                               String transactionId, String ownerUserId) throws Exception {
        JSONObject json = new JSONObject(transaction);
        byte[] encrypted = encrypt(json.toString().getBytes(StandardCharsets.UTF_8));
        String uniqueName = "sms-expense-" + transactionId;
        Data input = new Data.Builder()
                .putString(INPUT_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(INPUT_USER, ownerUserId)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsExpenseSyncWorker.class)
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("sms-expense-sync")
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TokenManager.init(context);
        String owner = getInputData().getString(INPUT_USER);
        String activeUser = com.smartexpense.android.api.OfflineMockStorage.resolveCurrentUserId();
        String token = TokenManager.getToken();
        if (owner == null) owner = activeUser;
        if (!owner.equals(activeUser)) {
            Log.w(TAG, "Dropping queued transaction because the signed-in account changed");
            return Result.failure();
        }
        if (token == null || token.isEmpty()) token = "offline-token";

        try {
            String encoded = getInputData().getString(INPUT_PAYLOAD);
            if (encoded == null) return Result.failure();
            JSONObject json = new JSONObject(new String(
                    decrypt(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8));
            Map<String, Object> body = new HashMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                body.put(key, json.get(key));
            }

            Response<ApiResponse<Expense>> response = ApiClient
                    .createService(ApiService.class).createExpense(body).execute();
            if (response.isSuccessful() && response.body() != null
                    && response.body().getData() != null) {
                Expense expense = response.body().getData();
                FirebaseExpenseRepository.saveExpenseSynchronous(context, expense, owner, 5);
                NotificationHelper.showTransactionNotification(context, expense.getAmount(),
                        expense.getMerchantName(), expense.getCategory(), expense.getTransactionType(),
                        null);
                return Result.success();
            }
            int code = response.code();
            if (code == 401 || code == 408 || code == 429 || code >= 500) return Result.retry();
            Log.e(TAG, "Transaction upload rejected by API (HTTP " + code + ")");
            return Result.failure();
        } catch (java.io.IOException e) {
            Log.w(TAG, "Network unavailable; transaction remains queued", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Unable to process queued bank transaction", e);
            return Result.failure();
        }
    }

    private static byte[] encrypt(byte[] plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                .put((byte) iv.length).put(iv).put(ciphertext).array();
    }

    private static byte[] decrypt(byte[] payload) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int ivLength = buffer.get() & 0xff;
        if (ivLength < 12 || ivLength > 16 || buffer.remaining() <= ivLength) {
            throw new IllegalArgumentException("Invalid encrypted queue entry");
        }
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
