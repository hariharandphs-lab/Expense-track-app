package com.smartexpense.android.ui.scanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.smartexpense.android.R;
import com.smartexpense.android.databinding.ActivityOcrScannerBinding;
import com.smartexpense.android.ui.expenses.AddExpenseActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live receipt scanner with on-device ML Kit OCR and editable extracted fields.
 * Pipeline:
 *  1. Image Preprocessing: rotation correction, contrast enhancement, blur/sharpness score
 *  2. ML Kit OCR: Latin and Devanagari scripts
 *  3. Structured Parsing: Merchant, Date, GSTIN, Line Items, TOTAL
 *  4. Total Validation: Line items sum vs extracted total
 *  5. AI Category Classification
 *  6. Per-field Confidence Scoring with Low-Confidence Review Confirmation
 */
public class OcrScannerActivity extends AppCompatActivity {

    private static final String TAG = "OcrScannerActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private ActivityOcrScannerBinding binding;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private volatile TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private final List<TextRecognizer> recognizers = new ArrayList<>();
    private ActivityResultLauncher<String> imagePicker;
    private final AtomicBoolean frameProcessing = new AtomicBoolean(false);
    private volatile long lastFrameAnalysisAt;

    private static final String[] CATEGORIES = {
            "Food", "Grocery", "Shopping", "Travel", "Medical",
            "Education", "Entertainment", "Bills", "Fuel", "Others"
    };

    // Parsing patterns
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:total|amount|grand total|net amount|bill amount|to pay|payable|bal due|due amount)\\s*[:=-]?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+\\.?\\d{0,2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FALLBACK_AMOUNT_PATTERN = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d{0,2})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GST_PATTERN = Pattern.compile(
            "(?:gstin|gst no\\.?|gst number|gstin no\\.?)\\s*[:=-]?\\s*([A-Z0-9]{15})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{2,4}[/-]\\d{1,2}[/-]\\d{1,2}|\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9\\s&.-]{3,30})\\s+.*?(\\d+[.,]\\d{2})$");

    // Category Keyword Dictionary for AI classification
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new HashMap<>();
    static {
        CATEGORY_KEYWORDS.put("Food", new String[]{"restaurant", "cafe", "hotel", "swiggy", "zomato", "biryani", "pizza", "burger", "coffee", "tea", "bakery", "kitchen", "dhaba", "meals", "bar", "beverage"});
        CATEGORY_KEYWORDS.put("Grocery", new String[]{"supermarket", "mart", "store", "provisions", "kirana", "vegetable", "fruits", "dmart", "spencer", "reliance", "milk", "dairy", "bazaar"});
        CATEGORY_KEYWORDS.put("Shopping", new String[]{"retail", "mall", "apparel", "clothing", "trends", "myntra", "amazon", "flipkart", "footwear", "fashion", "lifestyle", "electronics", "croma"});
        CATEGORY_KEYWORDS.put("Fuel", new String[]{"petrol", "diesel", "fuel", "indian oil", "hp", "bharat petroleum", "shell", "cng", "bunk"});
        CATEGORY_KEYWORDS.put("Medical", new String[]{"pharmacy", "chemist", "hospital", "clinic", "healthcare", "apollo", "medplus", "diagnostic", "dr.", "lab", "medicine"});
        CATEGORY_KEYWORDS.put("Travel", new String[]{"uber", "ola", "metro", "irctc", "railway", "flight", "indigo", "toll", "fastag", "taxi", "bus", "travels"});
        CATEGORY_KEYWORDS.put("Bills", new String[]{"electricity", "tneb", "bescom", "airtel", "jio", "vodafone", "broadband", "water", "gas", "utility", "recharge"});
        CATEGORY_KEYWORDS.put("Entertainment", new String[]{"cinema", "multiplex", "pvr", "inox", "bookmyshow", "theatre", "movie", "game", "resort"});
        CATEGORY_KEYWORDS.put("Education", new String[]{"school", "college", "university", "academy", "books", "stationery", "course", "tuition", "institute"});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOcrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        cameraExecutor = Executors.newSingleThreadExecutor();
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) scanPickedImage(uri);
        });

        // Default Latin OCR recognizer
        setupRecognizer("latin");

        setupCategoryDropdown();
        setupScriptSelector();

        if (hasCameraPermission()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        setupClickListeners();
    }

    private void setupRecognizer(String script) {
        if ("devanagari".equals(script)) {
            textRecognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        } else {
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        recognizers.add(textRecognizer);
    }

    private void setupScriptSelector() {
        binding.chipGroupScript.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipDevanagari)) {
                setupRecognizer("devanagari");
                Toast.makeText(this, "हिन्दी OCR Engine Enabled", Toast.LENGTH_SHORT).show();
            } else if (checkedIds.contains(R.id.chipTamil)) {
                setupRecognizer("latin");
                Toast.makeText(this,
                        "ML Kit recognizes Latin and Devanagari scripts. English text and digits can still be scanned.",
                        Toast.LENGTH_LONG).show();
            } else {
                setupRecognizer("latin");
                Toast.makeText(this, "English OCR Engine Enabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        binding.actvCategory.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.btnCapture.setOnClickListener(v -> captureImage());
        binding.btnChooseImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRetake.setOnClickListener(v -> {
            binding.previewGroup.setVisibility(View.VISIBLE);
            binding.reviewGroup.setVisibility(View.GONE);
            binding.btnCapture.setVisibility(View.VISIBLE);
            binding.btnCapture.setEnabled(true);
            binding.progressCapture.setVisibility(View.GONE);
        });

        binding.btnUseDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra("amount", binding.etExtractedAmount.getText().toString().trim());
            intent.putExtra("merchant", binding.etExtractedMerchant.getText().toString().trim());
            intent.putExtra("category", binding.actvCategory.getText().toString().trim());
            intent.putExtra("gstNumber", binding.etExtractedGst.getText().toString().trim());
            intent.putExtra("date", binding.etExtractedDate.getText().toString().trim());
            intent.putExtra("source", "OCR");
            startActivity(intent);
            finish();
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeLiveFrame);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera bind error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeLiveFrame(ImageProxy proxy) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastFrameAnalysisAt < 350 || !frameProcessing.compareAndSet(false, true)) {
            proxy.close();
            return;
        }
        lastFrameAnalysisAt = now;
        android.media.Image mediaImage = proxy.getImage();
        TextRecognizer recognizer = textRecognizer;
        if (mediaImage == null || recognizer == null) {
            proxy.close();
            frameProcessing.set(false);
            return;
        }
        try {
            final int width = proxy.getWidth();
            final int height = proxy.getHeight();
            final int rotation = proxy.getImageInfo().getRotationDegrees();

            InputImage input = InputImage.fromMediaImage(mediaImage, rotation);
            recognizer.process(input)
                    .addOnSuccessListener(result -> {
                        if (binding == null || isFinishing()) return;
                        List<android.graphics.Rect> boxes = new ArrayList<>();
                        for (Text.TextBlock block : result.getTextBlocks()) {
                            if (block.getBoundingBox() != null) boxes.add(block.getBoundingBox());
                        }
                        binding.lensTextOverlay.setTextBoxes(boxes, width, height, rotation);
                    })
                    .addOnCompleteListener(task -> {
                        proxy.close();
                        frameProcessing.set(false);
                    });
        } catch (Exception error) {
            proxy.close();
            frameProcessing.set(false);
        }
    }

    private void scanPickedImage(Uri uri) {
        binding.progressCapture.setVisibility(View.VISIBLE);
        binding.btnChooseImage.setEnabled(false);
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            textRecognizer.process(image)
                    .addOnSuccessListener(text -> extractStructuredDetails(text, -1))
                    .addOnFailureListener(error -> {
                        binding.progressCapture.setVisibility(View.GONE);
                        binding.btnChooseImage.setEnabled(true);
                        Toast.makeText(this, "Could not read text from that image. Try a sharper photo.",
                                Toast.LENGTH_LONG).show();
                    });
        } catch (Exception error) {
            binding.progressCapture.setVisibility(View.GONE);
            binding.btnChooseImage.setEnabled(true);
            Toast.makeText(this, "Could not open that image", Toast.LENGTH_SHORT).show();
        }
    }

    private void captureImage() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null),
                "receipt_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                        Locale.US).format(new Date()) + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        binding.progressCapture.setVisibility(View.VISIBLE);
        binding.btnCapture.setEnabled(false);

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        processImagePipeline(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        binding.progressCapture.setVisibility(View.GONE);
                        binding.btnCapture.setEnabled(true);
                        Toast.makeText(OcrScannerActivity.this,
                                "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Complete Preprocessing & OCR Pipeline
     */
    private void processImagePipeline(File photoFile) {
        new Thread(() -> {
            try {
                // 1. Load original Bitmap
                Bitmap originalBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                if (originalBitmap == null) {
                    runOnUiThread(() -> {
                        binding.progressCapture.setVisibility(View.GONE);
                        binding.btnCapture.setEnabled(true);
                        Toast.makeText(this, "Could not decode photo", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 2. Fix orientation from EXIF
                ExifInterface exif = new ExifInterface(photoFile.getAbsolutePath());
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                Bitmap orientedBitmap = fixOrientation(originalBitmap, orientation);

                // 3. Image enhancement: Contrast & Brightness adjustment
                Bitmap enhancedBitmap = enhanceImage(orientedBitmap);

                // 4. Quality score & blur detection
                int qualityScore = calculateSharpnessScore(enhancedBitmap);

                // Save preprocessed bitmap to file for ML Kit
                File processedFile = new File(getExternalFilesDir(null), "preprocessed_receipt.jpg");
                FileOutputStream fos = new FileOutputStream(processedFile);
                enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                fos.flush();
                fos.close();

                // 5. Send to ML Kit
                InputImage image = InputImage.fromFilePath(this, android.net.Uri.fromFile(processedFile));
                textRecognizer.process(image)
                        .addOnSuccessListener(visionText -> runOnUiThread(() ->
                                extractStructuredDetails(visionText, qualityScore)))
                        .addOnFailureListener(e -> runOnUiThread(() -> {
                            binding.progressCapture.setVisibility(View.GONE);
                            binding.btnCapture.setEnabled(true);
                            Toast.makeText(this, "OCR failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }));

            } catch (Exception e) {
                Log.e(TAG, "Pipeline error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    binding.progressCapture.setVisibility(View.GONE);
                    binding.btnCapture.setEnabled(true);
                    Toast.makeText(this, "Processing error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Bitmap fixOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap enhanceImage(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Canvas canvas = new Canvas(dest);
        Paint paint = new Paint();

        // Increase contrast by 1.2 and brightness by 10
        ColorMatrix cm = new ColorMatrix(new float[]{
                1.25f, 0, 0, 0, 10,
                0, 1.25f, 0, 0, 10,
                0, 0, 1.25f, 0, 10,
                0, 0, 0, 1.0f, 0
        });
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return dest;
    }

    private int calculateSharpnessScore(Bitmap bitmap) {
        // Approximate sharpness by sampling pixel intensity gradients
        int width = Math.min(bitmap.getWidth(), 300);
        int height = Math.min(bitmap.getHeight(), 300);
        Bitmap sample = Bitmap.createScaledBitmap(bitmap, width, height, false);

        long diffSum = 0;
        int count = 0;
        for (int y = 1; y < height - 1; y += 4) {
            for (int x = 1; x < width - 1; x += 4) {
                int p = sample.getPixel(x, y);
                int pr = sample.getPixel(x + 1, y);
                int pb = sample.getPixel(x, y + 1);

                int grayCenter = gray(p);
                int grayRight = gray(pr);
                int grayBottom = gray(pb);

                diffSum += Math.abs(grayCenter - grayRight) + Math.abs(grayCenter - grayBottom);
                count += 2;
            }
        }
        double avgGradient = count > 0 ? (double) diffSum / count : 15;
        // Map average gradient to a rough sharpness estimate, not an OCR confidence score.
        int score = (int) Math.min(100, Math.max(30, (avgGradient / 30.0) * 100));
        return score;
    }

    private int gray(int color) {
        return (77 * Color.red(color) + 150 * Color.green(color) + 29 * Color.blue(color)) >> 8;
    }

    private void extractStructuredDetails(Text visionText, int qualityScore) {
        binding.progressCapture.setVisibility(View.GONE);
        String fullText = visionText.getText();

        List<String> allLines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String text = line.getText().trim();
                if (!text.isEmpty()) {
                    allLines.add(text);
                }
            }
        }

        // 1. EXTRACT TOTAL AMOUNT
        String extractedAmount = "";
        double parsedTotal = 0.0;
        int amountConfidence = 50;

        Matcher amountMatcher = AMOUNT_PATTERN.matcher(fullText);
        if (amountMatcher.find()) {
            extractedAmount = amountMatcher.group(1).replace(",", "").trim();
            amountConfidence = 90;
        } else {
            // Fallback: look for largest currency match
            Matcher fallbackMatcher = FALLBACK_AMOUNT_PATTERN.matcher(fullText);
            double maxVal = 0.0;
            while (fallbackMatcher.find()) {
                try {
                    double val = Double.parseDouble(fallbackMatcher.group(1).replace(",", ""));
                    if (val > maxVal) {
                        maxVal = val;
                        extractedAmount = String.format(Locale.US, "%.2f", maxVal);
                        amountConfidence = 65;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        try {
            if (!extractedAmount.isEmpty()) {
                parsedTotal = Double.parseDouble(extractedAmount);
            }
        } catch (Exception ignored) {}

        // 2. EXTRACT MERCHANT
        String merchant = "Unknown Store";
        int merchantConfidence = 45;
        for (String line : allLines) {
            String lower = line.toLowerCase();
            // Skip common non-merchant header lines
            if (lower.contains("tax invoice") || lower.contains("cash receipt") ||
                lower.contains("bill") || lower.contains("welcome") || lower.contains("gstin") ||
                lower.contains("date") || lower.contains("ph:") || lower.contains("tel:") ||
                line.length() < 3 || line.matches("^[\\d\\W]+$")) {
                continue;
            }
            merchant = line;
            merchantConfidence = 85;
            break;
        }

        // 3. EXTRACT DATE
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        int dateConfidence = 50;
        Matcher dateMatcher = DATE_PATTERN.matcher(fullText);
        if (dateMatcher.find()) {
            date = dateMatcher.group(1);
            dateConfidence = 88;
        }

        // 4. EXTRACT GSTIN
        String gst = "";
        Matcher gstMatcher = GST_PATTERN.matcher(fullText);
        if (gstMatcher.find()) {
            gst = gstMatcher.group(1);
        }

        // 5. EXTRACT LINE ITEMS & VALIDATE TOTAL
        List<String> detectedItems = new ArrayList<>();
        double itemsSum = 0.0;
        for (String line : allLines) {
            Matcher m = LINE_ITEM_PATTERN.matcher(line);
            if (m.find()) {
                String itemName = m.group(1).trim();
                String itemPriceStr = m.group(2).replace(",", ".").trim();
                try {
                    double price = Double.parseDouble(itemPriceStr);
                    if (price > 0 && !itemName.equalsIgnoreCase("total") && !itemName.equalsIgnoreCase("subtotal")) {
                        itemsSum += price;
                        detectedItems.add("• " + itemName + ": ₹" + String.format(Locale.US, "%.2f", price));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 6. TOTAL VALIDATION
        boolean hasValidationMismatch = false;
        if (detectedItems.size() > 1 && parsedTotal > 0) {
            double diff = Math.abs(itemsSum - parsedTotal);
            if (diff > 5.0 && (diff / parsedTotal) > 0.05) {
                hasValidationMismatch = true;
                binding.cardValidationWarning.setVisibility(View.VISIBLE);
                binding.tvValidationWarningMessage.setText(
                        String.format(Locale.US, "Items sum to ₹%.2f, but extracted total is ₹%.2f. Difference: ₹%.2f. Please verify.",
                                itemsSum, parsedTotal, diff));
            } else {
                binding.cardValidationWarning.setVisibility(View.GONE);
            }
        } else {
            binding.cardValidationWarning.setVisibility(View.GONE);
        }

        // 7. AI CATEGORY CLASSIFICATION
        String predictedCategory = classifyCategory(fullText, merchant);

        // 8. POPULATE UI
        binding.etExtractedAmount.setText(extractedAmount);
        binding.etExtractedMerchant.setText(merchant);
        binding.etExtractedDate.setText(date);
        binding.etExtractedGst.setText(gst);
        binding.actvCategory.setText(predictedCategory, false);

        // Quality score badge
        String qualityText = qualityScore < 0
                ? "Gallery image · please verify extracted fields"
                : String.format(Locale.US, "%s Sharpness estimate: %d/100 · contrast enhanced",
                        qualityScore >= 60 ? "✓" : "⚠️", qualityScore);
        binding.tvQualityBadge.setText(qualityText);
        binding.tvQualityBadge.setTextColor(qualityScore < 0 ? 0xFFF59E0B
                : qualityScore >= 60 ? 0xFF10B981 : 0xFFF59E0B);

        // Low confidence warning banner
        boolean hasLowConfidence = (amountConfidence < 70) || (merchantConfidence < 70)
                || (qualityScore >= 0 && qualityScore < 50);
        binding.tvLowConfidenceNotice.setVisibility(hasLowConfidence ? View.VISIBLE : View.GONE);
        if (hasLowConfidence) {
            binding.tvLowConfidenceNotice.setText(
                    "⚠️ Low-confidence detected in Amount or Merchant. Please confirm details before proceeding.");
        }

        // Show line items
        if (!detectedItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String item : detectedItems) {
                sb.append(item).append("\n");
            }
            binding.tvLineItems.setText(sb.toString().trim());
            binding.tvLineItemsHeader.setVisibility(View.VISIBLE);
            binding.tvLineItems.setVisibility(View.VISIBLE);
        } else {
            binding.tvLineItems.setText("No individual line-items detected");
        }

        // Transition to Review Layout
        binding.previewGroup.setVisibility(View.GONE);
        binding.reviewGroup.setVisibility(View.VISIBLE);
        binding.btnCapture.setVisibility(View.GONE);
    }

    private String classifyCategory(String fullText, String merchant) {
        String combined = (merchant + " " + fullText).toLowerCase();
        String bestCategory = "Others";
        int maxMatches = 0;

        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (combined.contains(kw)) {
                    score += 2;
                }
            }
            if (score > maxMatches) {
                maxMatches = score;
                bestCategory = entry.getKey();
            }
        }
        return bestCategory;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera is unavailable. Choose a receipt image instead.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (TextRecognizer recognizer : recognizers) recognizer.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
