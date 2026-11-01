package com.smartexpense.android.ui.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.smartexpense.android.R;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.ActivityVoiceEntryBinding;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.service.FirebaseExpenseRepository;
import com.smartexpense.android.ui.expenses.AddExpenseActivity;
import com.smartexpense.android.util.VoiceExpenseParser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Voice Entry Activity — converts natural speech in English, Tamil, and Tanglish
 * into expense data with instant quick-save or review.
 */
public class VoiceEntryActivity extends AppCompatActivity {

    private static final String TAG = "VoiceEntryActivity";

    private ActivityVoiceEntryBinding binding;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String selectedLanguageCode = "en-IN";

    // Permission launcher for RECORD_AUDIO
    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startListeningInternal();
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice recognition.", Toast.LENGTH_LONG).show();
                    resetUI();
                }
            });

    // Fallback system Speech Dialog launcher (handles offline Google voice typing or when SpeechRecognizer service fails)
    private final ActivityResultLauncher<Intent> speechDialogLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        processVoiceInput(matches.get(0));
                    }
                }
                resetUI();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceEntryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupLanguageChips();
        setupSpeechRecognizer();
        setupClickListeners();
    }

    private void setupLanguageChips() {
        binding.chipGroupLanguages.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipTamil) {
                selectedLanguageCode = "ta-IN";
                binding.tvHint.setText("கூறுங்கள்: \"மளிகை 1200 ரூபாய்\" அல்லது \"டீ 20 ரூபாய்\"");
            } else if (id == R.id.chipTanglish) {
                selectedLanguageCode = "en-IN";
                binding.tvHint.setText("Say: \"Tea ku 20 roobai\" or \"Swiggy la 450 add pannu\"");
            } else if (id == R.id.chipHindi) {
                selectedLanguageCode = "hi-IN";
                binding.tvHint.setText("बोलें: \"चाय के 20 रुपये\" या \"राशन 500\"");
            } else {
                selectedLanguageCode = "en-IN";
                binding.tvHint.setText("Say: \"Swiggy 450 rupees\" or \"Spent 500 on petrol\"");
            }
        });
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer is not available natively on this device; fallback dialog will be used.");
            return;
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    binding.tvStatus.setText(getString(R.string.listening));
                    binding.animLottie.setVisibility(View.VISIBLE);
                    binding.animLottie.playAnimation();
                }

                @Override public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {
                    float scale = 1f + Math.max(0f, (rmsdB / 30f)) * 0.25f;
                    binding.btnMic.setScaleX(scale);
                    binding.btnMic.setScaleY(scale);
                }

                @Override public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    binding.tvStatus.setText(getString(R.string.processing_voice));
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    Log.e(TAG, "SpeechRecognizer error code: " + error);
                    handleError(error);
                    resetUI();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processVoiceInput(matches.get(0));
                    } else {
                        Toast.makeText(VoiceEntryActivity.this, "No voice input detected. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                    resetUI();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial =
                            partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        binding.tvRecognized.setText(partial.get(0));
                    }
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SpeechRecognizer: " + e.getMessage());
        }
    }

    private void setupClickListeners() {
        binding.btnMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                checkPermissionAndStartListening();
            }
        });

        binding.btnBack.setOnClickListener(v -> finish());

        // Quick Save directly without manual forms
        binding.btnQuickSave.setOnClickListener(v -> saveExpenseDirectly());

        // Review & Edit details in full AddExpenseActivity
        binding.btnUseDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra("amount", binding.etAmount.getText() != null ? binding.etAmount.getText().toString() : "");
            intent.putExtra("merchant", binding.etMerchant.getText() != null ? binding.etMerchant.getText().toString() : "");
            intent.putExtra("category", binding.tvCategory.getText() != null ? binding.tvCategory.getText().toString() : "Others");
            intent.putExtra("source", "VOICE");
            startActivity(intent);
            finish();
        });
    }

    private void checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startListeningInternal();
        }
    }

    private void startListeningInternal() {
        if (speechRecognizer == null) {
            launchSystemSpeechDialog();
            return;
        }

        try {
            isListening = true;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your expense...");

            speechRecognizer.startListening(intent);
            binding.btnMic.setImageResource(R.drawable.ic_mic_active);
            binding.tvStatus.setText("Listening...");
        } catch (Exception e) {
            Log.e(TAG, "startListening error, falling back to system dialog: " + e.getMessage());
            launchSystemSpeechDialog();
        }
    }

    private void launchSystemSpeechDialog() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguageCode);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak expense (e.g. 'Swiggy 450' or 'Tea 20')");
            speechDialogLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition is not supported or enabled on this device.", Toast.LENGTH_LONG).show();
            resetUI();
        }
    }

    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {}
        }
        resetUI();
    }

    private void processVoiceInput(String text) {
        if (text == null || text.trim().isEmpty()) return;
        binding.tvRecognized.setText("\"" + text + "\"");

        // Parse with multilingual parser (English, Tamil, Tanglish)
        VoiceExpenseParser.ParsedVoiceExpense parsed = VoiceExpenseParser.parse(text);

        if (parsed.getAmount() != null) {
            binding.etAmount.setText(parsed.getFormattedAmount());
        }

        if (parsed.getMerchantName() != null) {
            binding.etMerchant.setText(parsed.getMerchantName());
        }

        binding.tvCategory.setText(parsed.getCategory() != null ? parsed.getCategory() : "Others");
        binding.cardResult.setVisibility(View.VISIBLE);
    }

    private void saveExpenseDirectly() {
        String amountStr = binding.etAmount.getText() != null ? binding.etAmount.getText().toString().trim() : "";
        String merchant = binding.etMerchant.getText() != null ? binding.etMerchant.getText().toString().trim() : "";
        String category = binding.tvCategory.getText() != null ? binding.tvCategory.getText().toString().trim() : "Others";

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter or speak a valid expense amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(this, "Amount must be greater than zero.", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount number format.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (merchant.isEmpty()) {
            merchant = "General Expense";
        }

        String userId = OfflineMockStorage.resolveCurrentUserId();

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID().toString());
        expense.setUserId(userId);
        expense.setAmount(amount);
        expense.setCurrency("INR");
        expense.setMerchantName(merchant);
        expense.setCategory(category.isEmpty() ? "Others" : category);
        expense.setPaymentMethod("UPI");
        expense.setTransactionType("EXPENSE");
        expense.setSource("VOICE");
        expense.setTransactionDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        expense.setDescription("Voice Entry: ₹" + (int)amount + " at " + merchant);

        // 1. Save directly into local offline/demo storage engine
        OfflineMockStorage.addExpense(expense);

        // 2. Background sync to Firestore and remote API
        final Expense finalExpense = expense;
        final String finalUserId = userId;
        new Thread(() -> {
            try {
                FirebaseExpenseRepository.saveExpenseSynchronous(getApplicationContext(), finalExpense, finalUserId, 5);
            } catch (Exception e) {
                Log.w(TAG, "Firestore save warning: " + e.getMessage());
            }

            try {
                Map<String, Object> body = new HashMap<>();
                body.put("amount", finalExpense.getAmount());
                body.put("currency", "INR");
                body.put("merchantName", finalExpense.getMerchantName());
                body.put("category", finalExpense.getCategory());
                body.put("paymentMethod", finalExpense.getPaymentMethod());
                body.put("description", finalExpense.getDescription());
                body.put("source", "VOICE");
                body.put("userId", finalUserId);
                body.put("transactionDate", finalExpense.getTransactionDate());
                ApiClient.createService(ApiService.class).createExpense(body).execute();
            } catch (Exception e) {
                Log.w(TAG, "Backend sync warning: " + e.getMessage());
            }
        }).start();

        // 3. Broadcast to immediately refresh dashboard and expense list screens
        Intent refreshIntent = new Intent("com.smartexpense.android.ACTION_EXPENSE_SAVED");
        refreshIntent.putExtra("expenseId", expense.getId());
        sendBroadcast(refreshIntent);

        Toast.makeText(this, "⚡ Quick Saved: ₹" + (int)amount + " for " + merchant + " (" + category + ")", Toast.LENGTH_LONG).show();
        finish();
    }

    private void resetUI() {
        isListening = false;
        binding.btnMic.setImageResource(R.drawable.ic_mic);
        binding.btnMic.setScaleX(1f);
        binding.btnMic.setScaleY(1f);
        binding.animLottie.setVisibility(View.GONE);
        binding.tvStatus.setText(getString(R.string.tap_to_speak));
    }

    private void handleError(int error) {
        String message;
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Microphone audio error. Please try again.";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                Log.w(TAG, "SpeechRecognizer network/client error (" + error + "). Launching fallback speech dialog...");
                launchSystemSpeechDialog();
                return;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Microphone permission required.";
                checkPermissionAndStartListening();
                return;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No speech detected. Please speak clearly.";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No voice heard. Tap mic to try again.";
                break;
            default:
                message = "Speech error (" + error + "). Tap mic to try again.";
                break;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
        }
    }
}
