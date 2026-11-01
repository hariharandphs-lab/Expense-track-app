package com.smartexpense.android.ui.chat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.smartexpense.android.R;
import com.smartexpense.android.SmartExpenseApp;
import com.smartexpense.android.api.ApiClient;
import com.smartexpense.android.api.ApiService;
import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.databinding.BottomSheetAiChatbotBinding;
import com.smartexpense.android.model.ApiResponse;
import com.smartexpense.android.model.Expense;
import com.smartexpense.android.service.FirebaseExpenseRepository;
import com.smartexpense.android.util.ExpenseAiEngine;
import com.smartexpense.android.util.TokenManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiChatbotBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "AiChatbotBottomSheet";
    private BottomSheetAiChatbotBinding binding;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private TextToSpeech tts;
    private boolean isTtsEnabled = true;

    private String selectedChatLang = "en"; // "en", "ta", "tanglish", "hi"
    private String selectedVoiceLocale = "en-IN"; // "en-IN", "ta-IN", "hi-IN"

    private ActivityResultLauncher<Intent> speechLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;

    public static void show(FragmentManager fragmentManager) {
        AiChatbotBottomSheet dialog = new AiChatbotBottomSheet();
        dialog.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_SmartExpenseTracker);

        // Microphone runtime permission launcher
        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchVoiceRecognitionIntent();
                    } else {
                        Toast.makeText(getContext(), "Microphone permission is required for voice assistant.", Toast.LENGTH_SHORT).show();
                    }
                });

        // Voice Input Result Launcher
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            handleUserQuery(spokenText);
                        }
                    }
                });

        // Initialize Text-To-Speech with multilingual fallback
        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                updateTtsLanguage();
            }
        });
    }

    private void updateTtsLanguage() {
        if (tts == null) return;
        try {
            Locale targetLocale;
            if ("ta".equals(selectedChatLang)) {
                targetLocale = new Locale("ta", "IN");
            } else if ("hi".equals(selectedChatLang)) {
                targetLocale = new Locale("hi", "IN");
            } else {
                targetLocale = new Locale("en", "IN");
            }

            int result = tts.setLanguage(targetLocale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to Indian English
                tts.setLanguage(new Locale("en", "IN"));
            }
        } catch (Exception e) {
            Log.w(TAG, "TTS setLanguage warning: " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAiChatbotBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private static final String SHOREKEEPER_GREETING = "Hi! I'm Shorekeeper, your expense assistant. How can I help you today?";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupClickListeners();

        // Restore user chat history or show welcome message
        List<ChatMessage> saved = com.smartexpense.android.util.ChatHistoryManager.loadMessages(
                getContext(), TokenManager.getUserId());
        messageList.clear();
        if (saved != null && !saved.isEmpty()) {
            // If the initial message was an older generic bot welcome, update it to Shorekeeper's greeting
            if (!saved.get(0).isUser && saved.get(0).text != null &&
                    (saved.get(0).text.contains("Expense AI Assistant") || saved.get(0).text.contains("AI செலவு உதவியாளர்") || saved.get(0).text.startsWith("👋"))) {
                saved.get(0).text = SHOREKEEPER_GREETING;
                com.smartexpense.android.util.ChatHistoryManager.saveMessages(getContext(), TokenManager.getUserId(), saved);
            }
            messageList.addAll(saved);
            chatAdapter.notifyDataSetChanged();
            if (messageList.size() > 0) {
                binding.rvChatMessages.scrollToPosition(messageList.size() - 1);
            }
        } else {
            showWelcomeMessage();
        }
    }

    private void showWelcomeMessage() {
        addBotMessage(SHOREKEEPER_GREETING);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(messageList);
        binding.rvChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatMessages.setAdapter(chatAdapter);
    }

    private void setupClickListeners() {
        binding.btnCloseChat.setOnClickListener(v -> dismiss());

        // Audio TTS speech feedback toggle
        binding.btnTtsToggle.setOnClickListener(v -> {
            isTtsEnabled = !isTtsEnabled;
            if (isTtsEnabled) {
                binding.btnTtsToggle.setImageResource(R.drawable.ic_volume_up);
                binding.btnTtsToggle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.chat_tts_btn_tint));
                Toast.makeText(getContext(), "Voice speech reply enabled", Toast.LENGTH_SHORT).show();
            } else {
                if (tts != null) tts.stop();
                binding.btnTtsToggle.setImageResource(R.drawable.ic_volume_off);
                binding.btnTtsToggle.setColorFilter(ContextCompat.getColor(requireContext(), R.color.chat_close_btn_tint));
                Toast.makeText(getContext(), "Voice speech reply muted", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnSendMessage.setOnClickListener(v -> {
            String text = binding.etChatInput.getText().toString().trim();
            if (!text.isEmpty()) {
                binding.etChatInput.setText("");
                handleUserQuery(text);
            }
        });

        binding.btnVoiceInput.setOnClickListener(v -> checkPermissionAndStartVoice());
    }

    private void checkPermissionAndStartVoice() {
        Context context = getContext();
        if (context == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            launchVoiceRecognitionIntent();
        }
    }

    private void launchVoiceRecognitionIntent() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ta-IN", "en-IN"});
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Shorekeeper in English, தமிழ், or Tanglish...");
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleUserQuery(String query) {
        addUserMessage(query);

        // Auto-detect language (English, Tamil, or Tanglish) and adjust voice TTS
        selectedChatLang = ExpenseAiEngine.detectLanguage(query);
        updateTtsLanguage();

        // Build recent conversational history for context-aware questions
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = Math.max(0, messageList.size() - 8); i < messageList.size() - 1; i++) {
            ChatMessage prior = messageList.get(i);
            if (prior == null || "Thinking…".equals(prior.text)) continue;
            Map<String, String> m = new HashMap<>();
            m.put("role", prior.isUser ? "user" : "model");
            m.put("text", prior.text);
            history.add(m);
        }

        // 1. Process with local intelligent engine first to check for ACTION INTENTS (e.g. Add Expense via voice)
        List<Expense> localExpenses = OfflineMockStorage.getExpenses();
        ExpenseAiEngine.AiResponse localRes = ExpenseAiEngine.process(query, localExpenses, selectedChatLang, history);

        if (localRes.isAddExpenseAction() && localRes.getExpenseToAdd() != null) {
            Expense toAdd = localRes.getExpenseToAdd();
            String userId = OfflineMockStorage.resolveCurrentUserId();
            toAdd.setUserId(userId);

            // Save to offline storage immediately
            OfflineMockStorage.addExpense(toAdd);

            // Sync to Firestore & remote API in background
            Context appContext = getContext() != null ? getContext().getApplicationContext() : SmartExpenseApp.getAppContext();
            new Thread(() -> {
                try {
                    FirebaseExpenseRepository.saveExpenseSynchronous(appContext, toAdd, userId, 5);
                } catch (Exception ignored) {}
                try {
                    Map<String, Object> body = new HashMap<>();
                    body.put("amount", toAdd.getAmount());
                    body.put("currency", "INR");
                    body.put("merchantName", toAdd.getMerchantName());
                    body.put("category", toAdd.getCategory());
                    body.put("paymentMethod", toAdd.getPaymentMethod());
                    body.put("description", toAdd.getDescription());
                    body.put("source", "VOICE_AI");
                    body.put("userId", userId);
                    body.put("transactionDate", toAdd.getTransactionDate());
                    ApiClient.createService(ApiService.class).createExpense(body).execute();
                } catch (Exception ignored) {}
            }).start();

            // Broadcast refresh for active screens
            try {
                Intent refreshIntent = new Intent("com.smartexpense.android.ACTION_EXPENSE_SAVED");
                refreshIntent.putExtra("expenseId", toAdd.getId());
                appContext.sendBroadcast(refreshIntent);
            } catch (Exception ignored) {}

            // Display confirmation and speak via TTS
            addBotMessage(localRes.getText());
            speakIfEnabled(localRes.getText());
            return;
        }

        // 2. Analytical Question Handling
        ChatMessage pending = new ChatMessage("Thinking…", false);
        messageList.add(pending);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        binding.rvChatMessages.smoothScrollToPosition(messageList.size() - 1);

        Map<String, Object> body = new HashMap<>();
        body.put("question", query);
        body.put("language", selectedChatLang);
        body.put("history", history);

        ApiClient.createService(ApiService.class).askAi(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                if (binding == null) return;
                String remoteAnswer = null;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    Object val = response.body().getData().get("answer");
                    if (val instanceof String && !((String) val).trim().isEmpty()) {
                        remoteAnswer = (String) val;
                    }
                }
                // If remote backend returned a valid answer, use it; otherwise use local offline engine grounded in real database
                String finalAnswer = (remoteAnswer != null && !remoteAnswer.contains("AI service is not configured yet"))
                        ? remoteAnswer : localRes.getText();
                finishAnswer(pending, finalAnswer);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                  @NonNull Throwable error) {
                if (binding == null) return;
                // Seamlessly fall back to local AI engine grounded in real database
                finishAnswer(pending, localRes.getText());
            }
        });
    }

    private void finishAnswer(ChatMessage pending, String answer) {
        pending.text = answer;
        int index = messageList.indexOf(pending);
        if (index >= 0) chatAdapter.notifyItemChanged(index);
        if (binding != null) {
            binding.rvChatMessages.smoothScrollToPosition(Math.max(0, messageList.size() - 1));
        }
        com.smartexpense.android.util.ChatHistoryManager.saveMessages(
                getContext(), TokenManager.getUserId(), messageList);
        speakIfEnabled(answer);
    }

    private void speakIfEnabled(String text) {
        if (tts != null && isTtsEnabled && text != null) {
            String cleanForSpeech = text.replaceAll("[*#•🏷️🏬💳📊💰🔍🏆🍔💸✅]", "")
                    .replace("₹", " rupees ");
            tts.speak(cleanForSpeech, TextToSpeech.QUEUE_FLUSH, null, "AI_REPLY");
        }
    }

    private void addUserMessage(String text) {
        messageList.add(new ChatMessage(text, true));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        if (binding != null) {
            binding.rvChatMessages.smoothScrollToPosition(messageList.size() - 1);
        }
        com.smartexpense.android.util.ChatHistoryManager.saveMessages(
                getContext(), TokenManager.getUserId(), messageList);
    }

    private void addBotMessage(String text) {
        messageList.add(new ChatMessage(text, false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        if (binding != null) {
            binding.rvChatMessages.smoothScrollToPosition(messageList.size() - 1);
        }
        com.smartexpense.android.util.ChatHistoryManager.saveMessages(
                getContext(), TokenManager.getUserId(), messageList);
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ─── Chat Data & Adapter ──────────────────────────────────────────────────
    public static class ChatMessage {
        public String text;
        public final boolean isUser;

        public ChatMessage(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> list;

        ChatAdapter(List<ChatMessage> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = list.get(position);
            if (msg.isUser) {
                holder.layoutUser.setVisibility(View.VISIBLE);
                holder.layoutBot.setVisibility(View.GONE);
                holder.tvUser.setText(msg.text);
            } else {
                holder.layoutUser.setVisibility(View.GONE);
                holder.layoutBot.setVisibility(View.VISIBLE);
                holder.tvBot.setText(msg.text);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View layoutUser, layoutBot;
            TextView tvUser, tvBot;

            ViewHolder(View itemView) {
                super(itemView);
                layoutUser = itemView.findViewById(R.id.layoutUserMessage);
                layoutBot = itemView.findViewById(R.id.layoutBotMessage);
                tvUser = itemView.findViewById(R.id.tvUserText);
                tvBot = itemView.findViewById(R.id.tvBotText);
            }
        }
    }
}
