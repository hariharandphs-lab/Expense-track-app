package com.smartexpense.android.util;

import com.smartexpense.android.api.OfflineMockStorage;
import com.smartexpense.android.model.Budget;
import com.smartexpense.android.model.Expense;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural language query & voice command processor for Expense Data in Android.
 * Supports English, Tamil (தமிழ்), and Tanglish automatically based on user query language.
 * Grounded strictly in real stored transactions — never invents or hallucinates data.
 */
public class ExpenseAiEngine {

    public static class AiResponse {
        private final String text;
        private final boolean isAddExpenseAction;
        private final Expense expenseToAdd;

        public AiResponse(String text) {
            this(text, false, null);
        }

        public AiResponse(String text, boolean isAddExpenseAction, Expense expenseToAdd) {
            this.text = text;
            this.isAddExpenseAction = isAddExpenseAction;
            this.expenseToAdd = expenseToAdd;
        }

        public String getText() { return text; }
        public boolean isAddExpenseAction() { return isAddExpenseAction; }
        public Expense getExpenseToAdd() { return expenseToAdd; }
    }

    private static final String[] MONTHS = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    };

    private static final String[] MONTHS_ABBR = {
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec"
    };

    // The exactly 11 supported expense categories
    private static final Map<String, String[]> CATEGORY_SYNONYMS = new HashMap<>();
    static {
        CATEGORY_SYNONYMS.put("Food", new String[]{
                "food", "lunch", "dinner", "breakfast", "swiggy", "zomato", "restaurant",
                "cafe", "coffee", "tea", "chai", "biryani", "tiffin", "mess", "canteen",
                "dhaba", "snacks", "bakery", "sweets", "juice", "pizza", "burger",
                "சாப்பாடு", "உணவு", "தேநீர்", "டீ", "சாயா", "காபி", "சிற்றுண்டி", "kaapi", "saapadu"
        });
        CATEGORY_SYNONYMS.put("Grocery", new String[]{
                "grocery", "groceries", "supermarket", "mart", "dmart", "blinkit", "zepto",
                "instamart", "bigbasket", "provisions", "kirana", "milk", "vegetables",
                "fruits", "dairy", "egg", "rice", "dal", "oil", "aavin", "aavin milk",
                "hatsun", "nandini", "amul", "மளிகை", "காய்கறி", "பால்", "ஆவின்",
                "maligai", "kaaikari", "paal"
        });
        CATEGORY_SYNONYMS.put("Medical", new String[]{
                "medical", "medicine", "medicines", "pharmacy", "chemist", "doctor",
                "hospital", "clinic", "apollo", "medplus", "netmeds", "1mg", "pharmeasy",
                "tablet", "tablets", "capsule", "syrup", "injection", "lab", "diagnostic",
                "scan", "xray", "மருந்து", "மாத்திரை", "மருந்தகம்", "மருத்துவமனை",
                "marunthu", "marunthagam", "maathirai"
        });
        CATEGORY_SYNONYMS.put("Shopping", new String[]{
                "shopping", "clothes", "clothing", "amazon", "flipkart", "myntra", "ajio",
                "meesho", "nykaa", "shoes", "footwear", "dress", "saree", "shirt", "fashion",
                "garments", "textiles", "mall", "electronics", "croma", "ஷாப்பிங்", "துணி",
                "ஆடை", "thuni"
        });
        CATEGORY_SYNONYMS.put("Travel", new String[]{
                "travel", "uber", "ola", "rapido", "cab", "taxi", "bus", "flight", "airline",
                "train", "railway", "irctc", "metro", "auto", "toll", "fastag", "parking",
                "fare", "ticket", "cumta", "cmrl", "chennai metro", "mtc", "mtc bus",
                "tnstc", "setc", "mrts", "singara chennai", "chalo", "பயணம்", "வாடகை",
                "வண்டி", "மெட்ரோ", "பேருந்து", "payanam"
        });
        CATEGORY_SYNONYMS.put("Fuel", new String[]{
                "fuel", "petrol", "diesel", "bunk", "gas", "cng", "indian oil", "iocl",
                "hp", "hpcl", "bpcl", "bharat petroleum", "shell", "பெட்ரோல்", "டீசல்"
        });
        CATEGORY_SYNONYMS.put("Bills", new String[]{
                "bill", "bills", "electricity", "eb bill", "water bill", "recharge", "wifi",
                "broadband", "rent", "utility", "lpg", "cylinder", "maintenance",
                "emi", "loan", "insurance", "tangedco", "tneb", "tantransco", "cmwssb",
                "metrowater", "greater chennai corporation", "gcc tax", "current bill",
                "மின்சாரம்", "மின்கட்டணம்", "மின்வாரியம்", "குடிநீர்", "சொத்துவரி",
                "ரீசார்ஜ்", "vadagai"
        });
        CATEGORY_SYNONYMS.put("Entertainment", new String[]{
                "entertainment", "movie", "cinema", "theatre", "theater", "pvr", "inox",
                "cinepolis", "netflix", "hotstar", "prime video", "spotify", "bookmyshow",
                "game", "gaming", "resort", "திரைப்படம்", "சினிமா", "padam"
        });
        CATEGORY_SYNONYMS.put("Education", new String[]{
                "education", "course", "tuition", "books", "fees", "college", "school",
                "academy", "coaching", "stationery", "exam", "கல்வி", "படிப்பு", "பள்ளி",
                "கல்லூரி", "padipu"
        });
        CATEGORY_SYNONYMS.put("Transfers", new String[]{
                "transfer", "transfers", "p2p", "sent", "send money", "self transfer",
                "wallet", "anuppinen", "பரிமாற்றம்", "அனுப்புதல்", "parimaatram"
        });
    }

    // Explicit dedicated keyword sets for precise medical vs optical matching
    private static final String[] OPTICAL_KEYWORDS = {
            "spectacle", "spectacles", "glasses", "optical", "opticals", "optics",
            "optician", "eyewear", "eye care", "eyecare", "lenskart", "specsmakers",
            "vision", "lens", "lenses", "pinky optics", "titan eyeplus", "titan eye+",
            "கண்ணாடி", "கண் பார்வை", "பார்வை கண்ணாடி", "kannadi"
    };

    private static final String[] MEDICINE_KEYWORDS = {
            "medicine", "medicines", "pharmacy", "chemist", "tablet", "tablets",
            "capsule", "syrup", "injection", "pill", "pills", "drugs", "apollo",
            "medplus", "netmeds", "1mg", "pharmeasy", "மருந்து", "மாத்திரை",
            "மருந்தகம்", "marunthu", "marunthagam", "maathirai"
    };

    /**
     * Automatic language detection supporting Tamil (script), Tanglish, and English.
     */
    public static String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) return "en";

        // Check for Tamil Unicode range \u0B80 - \u0BFF
        for (char c : text.toCharArray()) {
            if (c >= '\u0B80' && c <= '\u0BFF') {
                return "ta";
            }
        }

        String lower = text.toLowerCase(Locale.ROOT).trim();
        String[] tanglishMarkers = {
                "pannen", "pannu", "pannunga", "pannren", "panna", "pannalaam",
                "aachu", "aayiduchu", "aagudhu", "evlo", "yevlo", "ethana", "ethanai",
                "innaiku", "inniku", "naalaiku", "nethu", "netru", "selavu", "kaattu",
                "kaminga", "sollu", "sollunga", "kuduthen", "vaanginen", "vaanga",
                "eduthen", "marunthu", "kannadi", "saapadu", "thuni", "vadagai",
                "pathi", "paththi", "enga", "adhigam", "romba", "yen", "epdi", "yedhu",
                "yaar", " ku ", " la ", " ku?", " la?", " dhaan", " than", " illa",
                " illai", "selavugal"
        };
        for (String marker : tanglishMarkers) {
            if (lower.contains(marker) || lower.endsWith(marker.trim()) || lower.startsWith(marker.trim() + " ")) {
                return "tanglish";
            }
        }

        return "en";
    }

    public static String inferCategory(String merchant, String description) {
        SmartCategorizer.CategoryResult res = SmartCategorizer.categorize(merchant, null, description);
        return res != null && res.category != null ? res.category : "Others";
    }

    public static String query(String prompt, List<Expense> expenses) {
        String lang = detectLanguage(prompt);
        return process(prompt, expenses, lang, Collections.emptyList()).getText();
    }

    public static AiResponse process(String prompt, List<Expense> expenses, String language) {
        return process(prompt, expenses, language, Collections.emptyList());
    }

    public static AiResponse process(String prompt, List<Expense> expenses, String language,
                                     List<Map<String, String>> history) {
        if (prompt == null || prompt.trim().isEmpty()) {
            String lang = language != null ? language : "en";
            if ("ta".equalsIgnoreCase(lang)) {
                return new AiResponse("வணக்கம்! நான் ஷோர்கீப்பர் (Shorekeeper). உங்கள் செலவுகள் குறித்து கேட்கலாம் (எ.கா: \"மருந்து செலவு எவ்வளவு?\", \"இன்றைய செலவு\").");
            } else if ("tanglish".equalsIgnoreCase(lang)) {
                return new AiResponse("Vanakkam! Naan Shorekeeper. Unga expenses pathi kelunga (e.g. \"Medicine ku evlo aachu?\", \"Swiggy expenses kaattu\").");
            }
            return new AiResponse("Hi! I'm Shorekeeper. How can I help with your expenses today?");
        }

        // Always prioritize auto-detected language of the user's current question
        String detected = detectLanguage(prompt);
        String lang = detected;
        if ("en".equals(detected) && language != null && !language.isEmpty() && !"auto".equalsIgnoreCase(language)) {
            // Keep user preference if English was detected and explicit lang provided
            lang = language.toLowerCase(Locale.ROOT);
        }

        String p = prompt.toLowerCase(Locale.ROOT).trim();

        // ── 0. CONVERSATIONAL GREETINGS & INTRODUCTIONS ──
        if (isGreetingOrSmallTalk(p)) {
            return generateGreetingResponse(p, lang);
        }

        // ── 1. ADD EXPENSE COMMAND (VOICE OR TEXT) ──
        VoiceExpenseParser.ParsedVoiceExpense parsed = VoiceExpenseParser.parse(prompt);
        boolean isAddIntent = parsed.isAddCommand() || p.startsWith("add") || p.startsWith("spent")
                || p.contains("சேர்") || p.contains("போடு") || p.contains("add pannu") || p.contains("kuduthen");

        if (parsed.getAmount() != null && parsed.getAmount() > 0 && isAddIntent) {
            Expense newExpense = new Expense();
            newExpense.setId(UUID.randomUUID().toString());
            newExpense.setAmount(parsed.getAmount());
            newExpense.setCurrency("INR");
            newExpense.setMerchantName(parsed.getMerchantName() != null ? parsed.getMerchantName() : "General Expense");
            newExpense.setCategory(parsed.getCategory() != null ? parsed.getCategory() : "Others");
            newExpense.setPaymentMethod("UPI");
            newExpense.setTransactionType("EXPENSE");
            newExpense.setSource("VOICE_AI");
            newExpense.setTransactionDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            newExpense.setDescription("Added via Shorekeeper AI: ₹" + (int)(double)parsed.getAmount() + " at " + newExpense.getMerchantName());

            String reply;
            if ("ta".equals(lang)) {
                reply = "✅ செலவு வெற்றிகரமாக சேர்க்கப்பட்டது!\n\n"
                        + "💰 தொகை: ₹" + formatDouble(newExpense.getAmount()) + "\n"
                        + "🏬 கடை/இடம்: " + newExpense.getMerchantName() + "\n"
                        + "🏷️ பிரிவு: " + newExpense.getCategory();
            } else if ("tanglish".equals(lang)) {
                reply = "✅ Expense successfully add aayiduchu!\n\n"
                        + "💰 Amount: ₹" + formatDouble(newExpense.getAmount()) + "\n"
                        + "🏬 Merchant: " + newExpense.getMerchantName() + "\n"
                        + "🏷️ Category: " + newExpense.getCategory();
            } else {
                reply = "✅ Expense successfully added!\n\n"
                        + "💰 Amount: ₹" + formatDouble(newExpense.getAmount()) + "\n"
                        + "🏬 Merchant: " + newExpense.getMerchantName() + "\n"
                        + "🏷️ Category: " + newExpense.getCategory();
            }
            return new AiResponse(reply, true, newExpense);
        }

        // Filter out credit transactions for analytical calculations
        List<Expense> debits = new ArrayList<>();
        if (expenses != null) {
            for (Expense e : expenses) {
                if (e != null && !"CREDIT".equalsIgnoreCase(e.getTransactionType())) {
                    debits.add(e);
                }
            }
        }

        // ── 2. BUDGET STATUS QUERIES ──
        if (p.contains("budget") || p.contains("பட்ஜெட்") || p.contains("remaining") || p.contains("limit")) {
            Calendar now = Calendar.getInstance();
            int month = now.get(Calendar.MONTH) + 1;
            int year = now.get(Calendar.YEAR);
            try {
                Budget b = OfflineMockStorage.getBudget(month, year);
                if (b != null) {
                    StringBuilder sb = new StringBuilder();
                    if ("ta".equals(lang)) {
                        sb.append("📊 இந்த மாத பட்ஜெட் விவரம்:\n\n")
                          .append("🎯 மொத்த பட்ஜெட்: ₹").append(formatDouble(b.getTotalBudget())).append("\n")
                          .append("💸 செலவழித்தது: ₹").append(formatDouble(b.getTotalSpent()))
                          .append(" (").append((int)Math.round(b.getPercentageUsed())).append("%)\n")
                          .append("💰 மீதமுள்ள தொகை: ₹").append(formatDouble(b.getRemaining())).append("\n\n");
                        if (b.getPercentageUsed() >= 100) {
                            sb.append("⚠️ எச்சரிக்கை: பட்ஜெட் வரம்பை தாண்டிவிட்டீர்கள்!");
                        } else if (b.getPercentageUsed() >= 80) {
                            sb.append("⚠️ கவனம்: பட்ஜெட்டில் 80%-க்கு மேல் செலவாகிவிட்டது.");
                        } else {
                            sb.append("✅ அருமை! பட்ஜெட் கட்டுப்பாட்டில் உள்ளது.");
                        }
                    } else if ("tanglish".equals(lang)) {
                        sb.append("📊 This month budget details:\n\n")
                          .append("🎯 Total Budget: ₹").append(formatDouble(b.getTotalBudget())).append("\n")
                          .append("💸 Spent: ₹").append(formatDouble(b.getTotalSpent()))
                          .append(" (").append((int)Math.round(b.getPercentageUsed())).append("%)\n")
                          .append("💰 Remaining: ₹").append(formatDouble(b.getRemaining())).append("\n\n");
                        if (b.getPercentageUsed() >= 100) {
                            sb.append("⚠️ Alert: Budget limit reach aayiduchu!");
                        } else if (b.getPercentageUsed() >= 80) {
                            sb.append("⚠️ Careful: 80% mela spend panniteenga.");
                        } else {
                            sb.append("✅ Super! Budget safe control la irukku.");
                        }
                    } else {
                        sb.append("📊 Budget Status for this month:\n\n")
                          .append("🎯 Total Budget: ₹").append(formatDouble(b.getTotalBudget())).append("\n")
                          .append("💸 Total Spent: ₹").append(formatDouble(b.getTotalSpent()))
                          .append(" (").append((int)Math.round(b.getPercentageUsed())).append("% used)\n")
                          .append("💰 Remaining: ₹").append(formatDouble(b.getRemaining())).append("\n\n");
                        if (b.getPercentageUsed() >= 100) {
                            sb.append("⚠️ Warning: You have exceeded your monthly budget!");
                        } else if (b.getPercentageUsed() >= 80) {
                            sb.append("⚠️ Attention: You have used over 80% of your allocated budget.");
                        } else {
                            sb.append("✅ Great job! Your spending is well within your budget.");
                        }
                    }
                    return new AiResponse(sb.toString().trim());
                }
            } catch (Exception ignored) {}
        }

        if (debits.isEmpty()) {
            if ("ta".equals(lang)) {
                return new AiResponse("தங்களிடம் இதுவரை எந்த செலவுப் பதிவுகளும் இல்லை.");
            } else if ("tanglish".equals(lang)) {
                return new AiResponse("Ungalukku endha expense records-um illa.");
            }
            return new AiResponse("You have no recorded expenses yet, so there is no data to analyze.");
        }

        // ── 3. SUPERLATIVE QUERIES (Highest, Most, Biggest) ──
        boolean isSuperlative = p.contains("most") || p.contains("highest") || p.contains("biggest") ||
                p.contains("max") || p.contains("largest") || p.contains("அதிக") || p.contains("adhigam") ||
                p.contains("periya") || p.contains("top expense");

        if (isSuperlative && !p.contains("how much") && !p.contains("evlo") && !p.contains("எவ்வளவு")) {
            Map<String, Double> catTotals = new HashMap<>();
            Map<String, Double> merchantTotals = new HashMap<>();
            Expense highest = null;
            double grandTotal = 0;

            for (Expense e : debits) {
                double amt = e.getAmount();
                grandTotal += amt;
                String cat = e.getCategory() != null ? e.getCategory() : "Others";
                String merch = e.getMerchantName() != null ? e.getMerchantName() : "Unknown";

                catTotals.put(cat, catTotals.getOrDefault(cat, 0.0) + amt);
                merchantTotals.put(merch, merchantTotals.getOrDefault(merch, 0.0) + amt);

                if (highest == null || amt > highest.getAmount()) {
                    highest = e;
                }
            }

            String topCat = getTopKey(catTotals);
            String topMerch = getTopKey(merchantTotals);
            double topCatAmt = catTotals.getOrDefault(topCat, 0.0);
            double topMerchAmt = merchantTotals.getOrDefault(topMerch, 0.0);
            int catPct = grandTotal > 0 ? (int) Math.round((topCatAmt / grandTotal) * 100) : 0;

            StringBuilder sb = new StringBuilder();
            if ("ta".equals(lang)) {
                sb.append("🏆 நீங்கள் அதிகம் செலவு செய்த விவரம்:\n\n")
                  .append("🏷️ முதன்மை பிரிவு: ").append(topCat).append(" - ₹").append(formatDouble(topCatAmt))
                  .append(" (மொத்தத்தில் ").append(catPct).append("%)\n")
                  .append("🏬 முதன்மை கடை/விற்பனையாளர்: ").append(topMerch).append(" - ₹").append(formatDouble(topMerchAmt)).append("\n");
                if (highest != null) {
                    sb.append("💳 மிகப்பெரிய ஒற்றைச் செலவு: ₹").append(formatDouble(highest.getAmount()))
                      .append(" (").append(highest.getMerchantName()).append(", ")
                      .append(formatDate(highest.getTransactionDate())).append(")");
                }
            } else if ("tanglish".equals(lang)) {
                sb.append("🏆 Neenga adhigama spend panna details:\n\n")
                  .append("🏷️ Top Category: ").append(topCat).append(" - ₹").append(formatDouble(topCatAmt))
                  .append(" (").append(catPct).append("% of total)\n")
                  .append("🏬 Top Merchant: ").append(topMerch).append(" - ₹").append(formatDouble(topMerchAmt)).append("\n");
                if (highest != null) {
                    sb.append("💳 Biggest Single Expense: ₹").append(formatDouble(highest.getAmount()))
                      .append(" at ").append(highest.getMerchantName()).append(" (")
                      .append(formatDate(highest.getTransactionDate())).append(")");
                }
            } else {
                sb.append("🏆 Here is where you spent the most:\n\n")
                  .append("🏷️ Top Category: ").append(topCat).append(" with ₹").append(formatDouble(topCatAmt))
                  .append(" (").append(catPct).append("% of total)\n")
                  .append("🏬 Top Merchant: ").append(topMerch).append(" with ₹").append(formatDouble(topMerchAmt)).append("\n");
                if (highest != null) {
                    sb.append("💳 Largest Single Expense: ₹").append(formatDouble(highest.getAmount()))
                      .append(" at ").append(highest.getMerchantName()).append(" (")
                      .append(formatDate(highest.getTransactionDate())).append(")");
                }
            }
            return new AiResponse(sb.toString().trim());
        }

        // ── 4. RESOLVE CONTEXT FOR FOLLOW-UP QUESTIONS ──
        String resolvedTarget = resolveContextTarget(p, history);

        // ── 5. SPECIFIC SUB-INTENTS: MEDICINE VS OPTICAL/SPECTACLES ──
        boolean isMedicineQuery = isMedicineSpecific(p) || isMedicineSpecific(resolvedTarget);
        boolean isOpticalQuery = isOpticalSpecific(p) || isOpticalSpecific(resolvedTarget);

        if (isMedicineQuery || isOpticalQuery) {
            List<Expense> matches = new ArrayList<>();
            double sum = 0;

            for (Expense e : debits) {
                String merch = e.getMerchantName() != null ? e.getMerchantName().toLowerCase(Locale.ROOT) : "";
                String desc = e.getDescription() != null ? e.getDescription().toLowerCase(Locale.ROOT) : "";

                if (isMedicineQuery) {
                    // Match pharmacies, doctors, hospitals, medicines
                    // Strictly EXCLUDE optical/spectacles/glasses/lenskart
                    boolean containsMedicine = containsAnyKeyword(merch, MEDICINE_KEYWORDS) || containsAnyKeyword(desc, MEDICINE_KEYWORDS);
                    boolean isOpticalShop = containsAnyKeyword(merch, OPTICAL_KEYWORDS) || containsAnyKeyword(desc, OPTICAL_KEYWORDS);

                    if (containsMedicine && !isOpticalShop) {
                        matches.add(e);
                        sum += e.getAmount();
                    } else if ("Medical".equalsIgnoreCase(e.getCategory()) && !isOpticalShop && !merch.contains("optics") && !merch.contains("lenskart") && !merch.contains("glasses")) {
                        matches.add(e);
                        sum += e.getAmount();
                    }
                } else {
                    // Optical query: Match eyewear, glasses, spectacles, Lenskart, Pinky Optics, etc.
                    // Strictly EXCLUDE pharmacies, medicines
                    boolean isOptical = containsAnyKeyword(merch, OPTICAL_KEYWORDS) || containsAnyKeyword(desc, OPTICAL_KEYWORDS);
                    if (isOptical) {
                        matches.add(e);
                        sum += e.getAmount();
                    }
                }
            }

            String queryTitle = isMedicineQuery ? (("ta".equals(lang)) ? "மருந்து" : ("tanglish".equals(lang) ? "Medicine" : "Medicine"))
                                                : (("ta".equals(lang)) ? "கண்ணாடி / ஆப்டிகல்" : ("tanglish".equals(lang) ? "Spectacles / Optics" : "Spectacles / Optical"));

            if (matches.isEmpty()) {
                if ("ta".equals(lang)) {
                    return new AiResponse("🔍 " + queryTitle + " தொடர்பான எந்த செலவுகளும் காணப்படவில்லை. மொத்தம்: ₹0.");
                } else if ("tanglish".equals(lang)) {
                    return new AiResponse("🔍 " + queryTitle + " ku endha recorded expenses-um kidaikkala. Total: ₹0 across 0 transactions.");
                }
                return new AiResponse("🔍 No recorded expenses found for " + queryTitle + ". Total spending: ₹0 across 0 transactions.");
            }

            return formatDetailedExpenseList(queryTitle, matches, sum, lang);
        }

        // ── 6. ITEM, MERCHANT, OR CATEGORY SEARCH ──
        String searchTarget = extractSearchTarget(p);
        String categoryMatch = extractCategory(p);

        // Fall back to resolved context if this is a follow-up query
        if (searchTarget == null && categoryMatch == null && resolvedTarget != null) {
            categoryMatch = extractCategory(resolvedTarget);
            if (categoryMatch == null) searchTarget = resolvedTarget;
        }

        if (searchTarget != null || categoryMatch != null) {
            String queryLabel = searchTarget != null ? searchTarget : categoryMatch;
            List<Expense> matches = new ArrayList<>();
            double sum = 0;

            Calendar now = Calendar.getInstance();
            int curMonth = now.get(Calendar.MONTH) + 1;
            boolean filterThisMonth = p.contains("month") || p.contains("மாதம்") || p.contains("maasam");

            for (Expense e : debits) {
                boolean isMatch = false;

                // Match by exact resolved category
                if (categoryMatch != null && categoryMatch.equalsIgnoreCase(e.getCategory())) {
                    isMatch = true;
                }

                // Match by search keyword in merchant name, description, or category
                if (!isMatch && searchTarget != null) {
                    String merch = e.getMerchantName() != null ? e.getMerchantName().toLowerCase(Locale.ROOT) : "";
                    String desc = e.getDescription() != null ? e.getDescription().toLowerCase(Locale.ROOT) : "";
                    String cat = e.getCategory() != null ? e.getCategory().toLowerCase(Locale.ROOT) : "";

                    if (merch.contains(searchTarget) || desc.contains(searchTarget) || cat.contains(searchTarget)) {
                        isMatch = true;
                    }
                }

                if (isMatch) {
                    if (filterThisMonth) {
                        Calendar c = parseCalendar(e.getTransactionDate());
                        if (c != null && (c.get(Calendar.MONTH) + 1) == curMonth) {
                            matches.add(e);
                            sum += e.getAmount();
                        }
                    } else {
                        matches.add(e);
                        sum += e.getAmount();
                    }
                }
            }

            if (matches.isEmpty()) {
                if ("ta".equals(lang)) {
                    return new AiResponse("🔍 \"" + queryLabel + "\" தொடர்பான எந்த செலவுகளும் காணப்படவில்லை. மொத்தம்: ₹0.");
                } else if ("tanglish".equals(lang)) {
                    return new AiResponse("🔍 \"" + queryLabel + "\" ku endha recorded expenses-um kidaikkala. Total: ₹0 across 0 transactions.");
                }
                return new AiResponse("🔍 No recorded expenses found for \"" + queryLabel + "\". Total spending: ₹0 across 0 transactions.");
            }

            return formatDetailedExpenseList(queryLabel, matches, sum, lang);
        }

        // ── 7. DATE QUERIES (Today, Yesterday, Specific Date) ──
        if (p.contains("today") || p.contains("இன்று") || p.contains("innaiku") || p.contains("inniku") || p.contains("இன்றைய")) {
            List<Expense> matches = new ArrayList<>();
            double sum = 0;
            Calendar now = Calendar.getInstance();

            for (Expense e : debits) {
                Calendar c = parseCalendar(e.getTransactionDate());
                if (c != null && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                        c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                    matches.add(e);
                    sum += e.getAmount();
                }
            }

            if (matches.isEmpty()) {
                if ("ta".equals(lang)) {
                    return new AiResponse("இன்று எந்த செலவும் பதிவு செய்யப்படவில்லை. இன்றைய செலவு: ₹0.");
                } else if ("tanglish".equals(lang)) {
                    return new AiResponse("Innaiku endha expense-um record aagala. Today total: ₹0.");
                }
                return new AiResponse("You haven't recorded any expenses today. Your total spending today is ₹0 across 0 transactions.");
            }

            return formatDetailedExpenseList(("ta".equals(lang) ? "இன்றைய செலவுகள்" : ("tanglish".equals(lang) ? "Today's Expenses" : "Today's Expenses")), matches, sum, lang);
        }

        if (p.contains("yesterday") || p.contains("நேற்று") || p.contains("nethu") || p.contains("netru")) {
            List<Expense> matches = new ArrayList<>();
            double sum = 0;
            Calendar yest = Calendar.getInstance();
            yest.add(Calendar.DAY_OF_YEAR, -1);

            for (Expense e : debits) {
                Calendar c = parseCalendar(e.getTransactionDate());
                if (c != null && c.get(Calendar.DAY_OF_YEAR) == yest.get(Calendar.DAY_OF_YEAR) &&
                        c.get(Calendar.YEAR) == yest.get(Calendar.YEAR)) {
                    matches.add(e);
                    sum += e.getAmount();
                }
            }

            if (matches.isEmpty()) {
                if ("ta".equals(lang)) {
                    return new AiResponse("நேற்று எந்த செலவும் பதிவு செய்யப்படவில்லை. நேற்றைய செலவு: ₹0.");
                } else if ("tanglish".equals(lang)) {
                    return new AiResponse("Nethu endha expense-um illa. Yesterday total: ₹0.");
                }
                return new AiResponse("You have no expenses recorded for yesterday. Total spending: ₹0.");
            }

            return formatDetailedExpenseList(("ta".equals(lang) ? "நேற்றைய செலவுகள்" : ("tanglish".equals(lang) ? "Yesterday's Expenses" : "Yesterday's Expenses")), matches, sum, lang);
        }

        // ── 8. OVERALL SPENDING BREAKDOWN & TOTAL ──
        double total = 0;
        Map<String, Double> catTotals = new HashMap<>();
        for (Expense e : debits) {
            double amt = e.getAmount();
            total += amt;
            String cat = e.getCategory() != null ? e.getCategory() : "Others";
            catTotals.put(cat, catTotals.getOrDefault(cat, 0.0) + amt);
        }

        StringBuilder sb = new StringBuilder();
        if ("ta".equals(lang)) {
            sb.append("📊 தங்களுடைய மொத்த செலவு ₹").append(formatDouble(total))
              .append(" (மொத்தம் ").append(debits.size()).append(" பரிவர்த்தனைகள்):\n\n");
            for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                int pct = total > 0 ? (int) Math.round((entry.getValue() / total) * 100) : 0;
                sb.append("• ").append(entry.getKey()).append(": ₹").append(formatDouble(entry.getValue()))
                  .append(" (").append(pct).append("%)\n");
            }
        } else if ("tanglish".equals(lang)) {
            sb.append("📊 Unga total recorded spending ₹").append(formatDouble(total))
              .append(" across ").append(debits.size()).append(" transactions:\n\n");
            for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                int pct = total > 0 ? (int) Math.round((entry.getValue() / total) * 100) : 0;
                sb.append("• ").append(entry.getKey()).append(": ₹").append(formatDouble(entry.getValue()))
                  .append(" (").append(pct).append("%)\n");
            }
        } else {
            sb.append("📊 Your total recorded spending is ₹").append(formatDouble(total))
              .append(" across ").append(debits.size()).append(" transactions:\n\n");
            for (Map.Entry<String, Double> entry : catTotals.entrySet()) {
                int pct = total > 0 ? (int) Math.round((entry.getValue() / total) * 100) : 0;
                sb.append("• ").append(entry.getKey()).append(": ₹").append(formatDouble(entry.getValue()))
                  .append(" (").append(pct).append("%)\n");
            }
        }

        return new AiResponse(sb.toString().trim());
    }

    private static boolean isGreetingOrSmallTalk(String lower) {
        return lower.equals("hi") || lower.equals("hello") || lower.equals("hey") || lower.equals("வணக்கம்")
                || lower.equals("vanakkam") || lower.contains("who are you") || lower.contains("who r u")
                || lower.contains("yaar neenga") || lower.contains("enna panra") || lower.equals("help")
                || lower.equals("thanks") || lower.equals("thank you") || lower.equals("nandri") || lower.equals("ok");
    }

    private static AiResponse generateGreetingResponse(String lower, String lang) {
        if (lower.contains("thank") || lower.contains("nandri")) {
            if ("ta".equals(lang)) return new AiResponse("மகிழ்ச்சி! வேறு ஏதேனும் செலவுகள் குறித்து அறிய வேண்டுமா?");
            if ("tanglish".equals(lang)) return new AiResponse("Welcome! Vera edhavadhu expense pathi therinjukanuma?");
            return new AiResponse("You're very welcome! Let me know if you have any other questions about your expenses.");
        }

        if ("ta".equals(lang)) {
            return new AiResponse("வணக்கம்! நான் ஷோர்கீப்பர் (Shorekeeper), உங்கள் தனிப்பட்ட நிதி உதவியாளர்.\n\n"
                    + "என்னிடம் கேட்கலாம்:\n"
                    + "• \"மருந்து செலவு எவ்வளவு?\"\n"
                    + "• \"கண்ணாடி வாங்கியதை காட்டு\"\n"
                    + "• \"இன்றைய செலவு\"\n"
                    + "• \"எதில் அதிகம் செலவு செய்தேன்?\"\n"
                    + "• அல்லது \"டீ 20 சேர்\" என்று கூறி புதிய செலவை சேர்க்கலாம்.");
        } else if ("tanglish".equals(lang)) {
            return new AiResponse("Vanakkam! Naan Shorekeeper, unga personal financial assistant.\n\n"
                    + "Neenga en kitta kekkalam:\n"
                    + "• \"Medicine ku evlo spend pannen?\"\n"
                    + "• \"Find my spectacles purchase\"\n"
                    + "• \"Innaiku evlo aachu?\"\n"
                    + "• \"Enga adhigam spend pannen?\"\n"
                    + "• Illa \"Swiggy ku 450 add pannu\" nu solli expense add pannalaam.");
        } else {
            return new AiResponse("Hi! I'm Shorekeeper, your financial companion.\n\n"
                    + "You can ask me questions like:\n"
                    + "• \"How much did I spend on medicine?\"\n"
                    + "• \"Find my spectacles purchase\"\n"
                    + "• \"Show my expenses today\"\n"
                    + "• \"Where did I spend the most?\"\n"
                    + "• Or say \"Add coffee 40\" to record an expense.");
        }
    }

    private static AiResponse formatDetailedExpenseList(String queryLabel, List<Expense> matches, double sum, String lang) {
        StringBuilder sb = new StringBuilder();
        if ("ta".equals(lang)) {
            sb.append("🔍 \"").append(queryLabel).append("\" தொடர்பான ").append(matches.size())
              .append(" பரிவர்த்தனைகள் (மொத்தம்: ₹").append(formatDouble(sum)).append("):\n\n");
        } else if ("tanglish".equals(lang)) {
            sb.append("🔍 \"").append(queryLabel).append("\" ku total-ah ").append(matches.size())
              .append(" transactions irukku (Total: ₹").append(formatDouble(sum)).append("):\n\n");
        } else {
            sb.append("🔍 Found ").append(matches.size()).append(" transaction(s) for \"")
              .append(queryLabel).append("\" (Total: ₹").append(formatDouble(sum)).append("):\n\n");
        }

        int maxToShow = Math.min(matches.size(), 8);
        for (int i = 0; i < maxToShow; i++) {
            Expense e = matches.get(i);
            sb.append("• ").append(e.getMerchantName()).append(" · ₹").append(formatDouble(e.getAmount()))
              .append(" (").append(e.getCategory()).append(", ").append(formatDate(e.getTransactionDate())).append(")\n");
        }

        if (matches.size() > maxToShow) {
            int remaining = matches.size() - maxToShow;
            if ("ta".equals(lang)) {
                sb.append("\n...மற்றும் மேலும் ").append(remaining).append(" செலவுகள் உள்ளன.");
            } else if ("tanglish".equals(lang)) {
                sb.append("\n...and innum ").append(remaining).append(" transactions irukku.");
            } else {
                sb.append("\n...and ").append(remaining).append(" more transaction(s).");
            }
        }

        return new AiResponse(sb.toString().trim());
    }

    private static boolean isMedicineSpecific(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAnyKeyword(lower, MEDICINE_KEYWORDS);
    }

    private static boolean isOpticalSpecific(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAnyKeyword(lower, OPTICAL_KEYWORDS);
    }

    private static boolean containsAnyKeyword(String text, String[] keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static String resolveContextTarget(String currentQuery, List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return null;

        String lower = currentQuery.toLowerCase(Locale.ROOT);
        boolean isFollowUp = lower.startsWith("and ") || lower.startsWith("what about") || lower.startsWith("how about")
                || lower.contains("transactions") || lower.contains("list them") || lower.contains("details")
                || lower.contains("highest") || lower.contains("biggest") || lower.contains("adhigam")
                || lower.contains("edhu") || lower.contains("kaattu") || lower.contains("விவரங்கள்");

        if (!isFollowUp) return null;

        // Inspect previous user turns in reverse
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> turn = history.get(i);
            if (turn != null && "user".equalsIgnoreCase(turn.get("role"))) {
                String prevText = turn.get("text");
                if (prevText != null) {
                    String prevLower = prevText.toLowerCase(Locale.ROOT);
                    String prevCat = extractCategory(prevLower);
                    if (prevCat != null) return prevCat;
                    String prevTarget = extractSearchTarget(prevLower);
                    if (prevTarget != null) return prevTarget;
                }
            }
        }
        return null;
    }

    private static String extractSearchTarget(String lower) {
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?:how much(?: did i spend)? on|spend on|spent on)\\s+([a-zA-Z0-9 &._-]{2,30})(?:\\?|$| this| in)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:show my|show|view|find my|find|search for|search|did i buy)\\s+([a-zA-Z0-9 &._-]{2,30})(?:\\s+(?:expenses|purchase|transactions|bills))?", Pattern.CASE_INSENSITIVE),
                Pattern.compile("([a-zA-Z0-9 &._-]{2,30})\\s+(?:expenses|transactions|purchase)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("([a-zA-Z0-9 &._-]{2,30})\\s*(?:ku evlo|la evlo|expenses kaattu|vaanginen)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("([a-zA-Z0-9 &._-]{2,30})\\s*(?:செலவு|செலவை|வாங்கினேனா)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern p : patterns) {
            Matcher m = p.matcher(lower);
            if (m.find()) {
                String candidate = m.group(1).trim();
                candidate = candidate.replaceAll("(?i)\\b(my|the|a|an|all|recorded)\\b", "").trim();
                if (candidate.length() >= 2 && !candidate.equalsIgnoreCase("today")
                        && !candidate.equalsIgnoreCase("yesterday") && !candidate.equalsIgnoreCase("month")) {
                    return candidate;
                }
            }
        }

        // Direct item keyword checks
        String[] directKeywords = {
                "spectacles", "spectacle", "optical", "optics", "glasses", "eyewear", "eye care",
                "pinky optics", "lenskart", "specsmakers", "medicine", "medicines", "pharmacy",
                "apollo", "medplus", "swiggy", "zomato", "dmart", "blinkit", "zepto", "uber",
                "ola", "petrol", "diesel", "groceries", "grocery", "coffee", "tea", "milk",
                "recharge", "electricity", "rent", "clothes", "movie"
        };
        for (String kw : directKeywords) {
            if (lower.contains(kw)) {
                return kw;
            }
        }
        return null;
    }

    private static String extractCategory(String lower) {
        for (Map.Entry<String, String[]> entry : CATEGORY_SYNONYMS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static String getTopKey(Map<String, Double> map) {
        String top = "Unknown";
        double max = -1;
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                top = entry.getKey();
            }
        }
        return top;
    }

    private static String formatDouble(double val) {
        return String.format(Locale.US, "%,.0f", val);
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null) return "";
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date d = inFormat.parse(isoDate);
            return new SimpleDateFormat("d MMM yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return isoDate.split("T")[0];
        }
    }

    private static Calendar parseCalendar(String isoDate) {
        if (isoDate == null) return null;
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = inFormat.parse(isoDate.substring(0, 10));
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
