package com.smartexpense.android.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SmartCategorizer — Intelligent multi-signal transaction categorizer and P2P detector.
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Distinguishes P2P transfers from seller purchases, including Indian sellers using personal names.</li>
 *   <li>Identifies optical and eyewear shops (e.g. "PINKY OPTICS", "Lenskart", "Specsmakers") as Medical/Healthcare.</li>
 *   <li>Understands Indian bank SMS formats, UPI transactions, PhonePe, Google Pay, Paytm, cards, and net banking.</li>
 *   <li>Persists user corrections so future transactions from the same merchant/seller are automatically categorized.</li>
 * </ul>
 */
public final class SmartCategorizer {

    private static final String PREF_MAPPINGS = "smart_merchant_mappings";
    private static final String KEY_CAT_PREFIX = "cat_";
    private static final String KEY_P2P_PREFIX = "p2p_";

    private SmartCategorizer() {}

    // ─── Result ──────────────────────────────────────────────────────────────
    public static class CategoryResult {
        public final String category;
        /** true when the transaction is identified or suspected as person-to-person transfer */
        public final boolean isProbablyP2P;
        /** true when confirmation is recommended (e.g. ambiguous personal name seller) */
        public final boolean needsUserConfirmation;
        /** Explanation for logging and UI display */
        public final String reason;

        public CategoryResult(String category, boolean isProbablyP2P,
                              boolean needsUserConfirmation, String reason) {
            this.category = category;
            this.isProbablyP2P = isProbablyP2P;
            this.needsUserConfirmation = needsUserConfirmation;
            this.reason = reason;
        }

        public String getCategory() { return category; }
        public boolean isProbablyP2P() { return isProbablyP2P; }
        public boolean needsUserConfirmation() { return needsUserConfirmation; }
        public String getReason() { return reason; }
    }

    // ─── User Correction Learning ──────────────────────────────────────────

    /**
     * Saves user's correction for a merchant, seller name, or UPI ID.
     * Future transactions from this identifier will automatically use this category.
     */
    public static void saveUserCorrection(Context context, String identifier, String category, boolean isP2P) {
        if (context == null || identifier == null || identifier.trim().isEmpty()) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_MAPPINGS, Context.MODE_PRIVATE);
            String key = normalizeKey(identifier);
            prefs.edit()
                    .putString(KEY_CAT_PREFIX + key, category)
                    .putBoolean(KEY_P2P_PREFIX + key, isP2P)
                    .apply();
        } catch (Exception ignored) {}
    }

    /**
     * Checks if user has previously corrected or categorized this merchant/UPI ID.
     */
    public static CategoryResult getUserCorrection(Context context, String merchant, String vpa) {
        if (context == null) return null;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_MAPPINGS, Context.MODE_PRIVATE);
            if (merchant != null) {
                String key = normalizeKey(merchant);
                if (prefs.contains(KEY_CAT_PREFIX + key)) {
                    String cat = prefs.getString(KEY_CAT_PREFIX + key, "Others");
                    boolean p2p = prefs.getBoolean(KEY_P2P_PREFIX + key, false);
                    return new CategoryResult(cat, p2p, false, "Learned user preference");
                }
            }
            if (vpa != null) {
                String key = normalizeKey(vpa);
                if (prefs.contains(KEY_CAT_PREFIX + key)) {
                    String cat = prefs.getString(KEY_CAT_PREFIX + key, "Others");
                    boolean p2p = prefs.getBoolean(KEY_P2P_PREFIX + key, false);
                    return new CategoryResult(cat, p2p, false, "Learned user preference");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String normalizeKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ─── Known Merchants ───────────────────────────────────────────────────
    private static final Object[][] KNOWN_MERCHANTS = {
        // Food & Dining
        {"swiggy", "Food"}, {"zomato", "Food"}, {"dominos", "Food"}, {"pizza hut", "Food"},
        {"mcdonalds", "Food"}, {"kfc", "Food"}, {"subway", "Food"}, {"burger king", "Food"},
        {"starbucks", "Food"}, {"cafe coffee", "Food"}, {"ccd", "Food"}, {"chai point", "Food"},
        {"haldirams", "Food"}, {"barbeque nation", "Food"}, {"box8", "Food"}, {"faasos", "Food"},
        {"freshmenu", "Food"}, {"dunzo", "Food"}, {"eatsure", "Food"}, {"eatclub", "Food"},

        // Grocery
        {"dmart", "Grocery"}, {"big bazaar", "Grocery"}, {"more supermarket", "Grocery"},
        {"reliance fresh", "Grocery"}, {"jiomart", "Grocery"}, {"blinkit", "Grocery"},
        {"zepto", "Grocery"}, {"instamart", "Grocery"}, {"bigbasket", "Grocery"},
        {"grofers", "Grocery"}, {"nature basket", "Grocery"}, {"spar", "Grocery"},
        {"aavin", "Grocery"}, {"aavin milk", "Grocery"}, {"aavin parlour", "Grocery"},
        {"hatsun", "Grocery"}, {"milky mist", "Grocery"}, {"nandini", "Grocery"},
        {"amul", "Grocery"}, {"mother dairy", "Grocery"}, {"heritage foods", "Grocery"},

        // Medical & Healthcare & Optical
        {"pinky optics", "Medical"}, {"lenskart", "Medical"}, {"specsmakers", "Medical"},
        {"vision express", "Medical"}, {"vasan eye", "Medical"}, {"sankara netralaya", "Medical"},
        {"aravind eye", "Medical"}, {"titan eye", "Medical"}, {"apollo pharmacy", "Medical"},
        {"medplus", "Medical"}, {"netmeds", "Medical"}, {"1mg", "Medical"}, {"tata 1mg", "Medical"},
        {"pharmeasy", "Medical"}, {"practo", "Medical"}, {"healthkart", "Medical"},
        {"apollo hospital", "Medical"}, {"fortis", "Medical"}, {"manipal", "Medical"},
        {"care hospital", "Medical"}, {"narayana hrudayalaya", "Medical"}, {"max hospital", "Medical"},
        {"thyrocare", "Medical"}, {"dr lal", "Medical"}, {"lal path", "Medical"}, {"medicover", "Medical"},

        // Transport & Travel
        {"uber", "Travel"}, {"ola", "Travel"}, {"rapido", "Travel"}, {"redbus", "Travel"},
        {"irctc", "Travel"}, {"indigo", "Travel"}, {"air india", "Travel"}, {"spicejet", "Travel"},
        {"vistara", "Travel"}, {"goibibo", "Travel"}, {"makemytrip", "Travel"}, {"yatra", "Travel"},
        {"cleartrip", "Travel"}, {"cumta", "Travel"}, {"cmrl", "Travel"}, {"chennai metro", "Travel"},
        {"mtc", "Travel"}, {"mtc bus", "Travel"}, {"tnstc", "Travel"}, {"setc", "Travel"},
        {"mrts", "Travel"}, {"southern railway", "Travel"}, {"singara chennai", "Travel"},
        {"chalo", "Travel"}, {"bmtc", "Travel"}, {"ksrtc", "Travel"}, {"apsrtc", "Travel"},
        {"tsrtc", "Travel"}, {"dmrc", "Travel"}, {"delhi metro", "Travel"}, {"best bus", "Travel"},
        {"metro", "Travel"}, {"fastag", "Travel"},

        // Shopping
        {"amazon", "Shopping"}, {"flipkart", "Shopping"}, {"myntra", "Shopping"}, {"ajio", "Shopping"},
        {"nykaa", "Shopping"}, {"meesho", "Shopping"}, {"snapdeal", "Shopping"}, {"shopsy", "Shopping"},
        {"westside", "Shopping"}, {"lifestyle", "Shopping"}, {"pantaloons", "Shopping"},
        {"max fashion", "Shopping"}, {"h&m", "Shopping"}, {"zara", "Shopping"}, {"croma", "Shopping"},
        {"reliance digital", "Shopping"},

        // Fuel
        {"indian oil", "Fuel"}, {"iocl", "Fuel"}, {"hpcl", "Fuel"}, {"bpcl", "Fuel"},
        {"bharat petroleum", "Fuel"}, {"shell", "Fuel"}, {"nayara", "Fuel"}, {"petrol", "Fuel"},

        // Bills & Utilities
        {"tangedco", "Bills"}, {"tneb", "Bills"}, {"tantransco", "Bills"}, {"bescom", "Bills"},
        {"msedcl", "Bills"}, {"kseb", "Bills"}, {"tsspdcl", "Bills"}, {"tsnpdcl", "Bills"},
        {"uppcl", "Bills"}, {"tpddl", "Bills"}, {"bses", "Bills"}, {"adani electricity", "Bills"},
        {"tata power", "Bills"}, {"torrent power", "Bills"}, {"cmwssb", "Bills"},
        {"metrowater", "Bills"}, {"chennai metrowater", "Bills"}, {"greater chennai corporation", "Bills"},
        {"chennai corporation", "Bills"}, {"gcc tax", "Bills"}, {"bsnl", "Bills"},
        {"airtel", "Bills"}, {"jio", "Bills"}, {"vi ", "Bills"}, {"vodafone", "Bills"},
        {"act broadband", "Bills"}, {"hathway", "Bills"}, {"tata play", "Bills"},
        {"dish tv", "Bills"}, {"sun direct", "Bills"}, {"indane", "Bills"}, {"bharat gas", "Bills"},

        // Entertainment
        {"pvr", "Entertainment"}, {"inox", "Entertainment"}, {"cinepolis", "Entertainment"},
        {"bookmyshow", "Entertainment"}, {"netflix", "Entertainment"}, {"hotstar", "Entertainment"},
        {"prime video", "Entertainment"}, {"spotify", "Entertainment"}, {"youtube", "Entertainment"}
    };

    // ─── Keyword Rules ─────────────────────────────────────────────────────
    private static final Object[][] KEYWORD_RULES = {
        // Medical / Healthcare / Optical / Eyewear
        {"Medical", "optics", 5}, {"Medical", "optical", 5}, {"Medical", "opticals", 5},
        {"Medical", "optician", 5}, {"Medical", "eyewear", 5}, {"Medical", "eye care", 5},
        {"Medical", "eyecare", 5}, {"Medical", "spectacles", 5}, {"Medical", "spectacle", 5},
        {"Medical", "lens", 4}, {"Medical", "lenses", 4}, {"Medical", "glasses", 4},
        {"Medical", "frames", 3}, {"Medical", "vision", 3},
        {"Medical", "pharmacy", 5}, {"Medical", "chemist", 5}, {"Medical", "medical store", 5},
        {"Medical", "medicals", 5}, {"Medical", "medicine", 5}, {"Medical", "medicines", 5},
        {"Medical", "tablet", 4}, {"Medical", "tablets", 4}, {"Medical", "capsule", 4},
        {"Medical", "syrup", 4}, {"Medical", "injection", 4}, {"Medical", "doctor", 4},
        {"Medical", "clinic", 4}, {"Medical", "hospital", 4}, {"Medical", "diagnostic", 4},
        {"Medical", "pathology", 4}, {"Medical", "lab", 3}, {"Medical", "scan", 3},
        {"Medical", "xray", 4}, {"Medical", "blood test", 4},
        {"Medical", "marunthu", 5}, {"Medical", "மருந்து", 5},
        {"Medical", "marunthagam", 5}, {"Medical", "மருந்தகம்", 5},
        {"Medical", "kannadi", 4}, {"Medical", "கண்ணாடி", 4},
        {"Medical", "maathirai", 4}, {"Medical", "மாத்திரை", 4},

        // Food & Dining
        {"Food", "restaurant", 4}, {"Food", "biryani", 4}, {"Food", "hotel", 3},
        {"Food", "tiffin", 4}, {"Food", "lunch", 4}, {"Food", "dinner", 4},
        {"Food", "breakfast", 4}, {"Food", "snacks", 4}, {"Food", "bakery", 4},
        {"Food", "sweets", 4}, {"Food", "tea", 3}, {"Food", "chai", 3},
        {"Food", "coffee", 3}, {"Food", "kaapi", 3}, {"Food", "mess", 4},
        {"Food", "canteen", 4}, {"Food", "dhaba", 4}, {"Food", "saapadu", 4},
        {"Food", "சாப்பாடு", 4}, {"Food", "உணவு", 4}, {"Food", "தேநீர்", 4},

        // Grocery & Dairy
        {"Grocery", "grocery", 4}, {"Grocery", "groceries", 4}, {"Grocery", "supermarket", 4},
        {"Grocery", "kirana", 4}, {"Grocery", "provisions", 4}, {"Grocery", "vegetables", 4},
        {"Grocery", "fruits", 4}, {"Grocery", "milk", 4}, {"Grocery", "dairy", 3},
        {"Grocery", "aavin", 5}, {"Grocery", "aavin milk", 5}, {"Grocery", "ஆவின்", 5},
        {"Grocery", "egg", 3}, {"Grocery", "rice", 3}, {"Grocery", "dal", 3},
        {"Grocery", "oil", 3}, {"Grocery", "maligai", 4}, {"Grocery", "மளிகை", 4},
        {"Grocery", "காய்கறி", 4}, {"Grocery", "பால்", 4},

        // Travel & Transport & Transit Authorities
        {"Travel", "cumta", 5}, {"Travel", "cmrl", 5}, {"Travel", "chennai metro", 5},
        {"Travel", "mtc", 5}, {"Travel", "mtc bus", 5}, {"Travel", "tnstc", 5},
        {"Travel", "setc", 5}, {"Travel", "mrts", 5}, {"Travel", "singara chennai", 5},
        {"Travel", "suburban train", 5}, {"Travel", "metro rail", 5}, {"Travel", "chalo", 4},
        {"Travel", "cab", 4}, {"Travel", "taxi", 4}, {"Travel", "auto", 3},
        {"Travel", "bus", 3}, {"Travel", "train", 4}, {"Travel", "flight", 5},
        {"Travel", "toll", 4}, {"Travel", "parking", 3}, {"Travel", "fare", 3},
        {"Travel", "payanam", 4}, {"Travel", "பயணம்", 4}, {"Travel", "பேருந்து", 4},
        {"Travel", "மெட்ரோ", 5}, {"Travel", "ரயில்", 4},

        // Shopping
        {"Shopping", "clothes", 4}, {"Shopping", "clothing", 4}, {"Shopping", "dress", 4},
        {"Shopping", "saree", 4}, {"Shopping", "shirt", 4}, {"Shopping", "shoes", 4},
        {"Shopping", "footwear", 4}, {"Shopping", "garments", 4}, {"Shopping", "textiles", 4},
        {"Shopping", "thuni", 4}, {"Shopping", "துணி", 4}, {"Shopping", "ஷாப்பிங்", 4},

        // Fuel
        {"Fuel", "petrol", 5}, {"Fuel", "diesel", 5}, {"Fuel", "fuel", 5},
        {"Fuel", "bunk", 4}, {"Fuel", "cng", 4}, {"Fuel", "பெட்ரோல்", 4},

        // Bills & Utilities & Discoms
        {"Bills", "tangedco", 5}, {"Bills", "tneb", 5}, {"Bills", "tantransco", 5},
        {"Bills", "electricity", 5}, {"Bills", "eb bill", 5}, {"Bills", "current bill", 5},
        {"Bills", "power bill", 5}, {"Bills", "cmwssb", 5}, {"Bills", "metrowater", 5},
        {"Bills", "metro water", 5}, {"Bills", "water bill", 5}, {"Bills", "water tax", 5},
        {"Bills", "sewerage tax", 5}, {"Bills", "property tax", 5}, {"Bills", "corporation tax", 5},
        {"Bills", "recharge", 4}, {"Bills", "broadband", 5}, {"Bills", "wifi", 4},
        {"Bills", "rent", 4}, {"Bills", "lpg", 5}, {"Bills", "gas cylinder", 5},
        {"Bills", "maintenance", 4}, {"Bills", "emi", 4}, {"Bills", "insurance", 4},
        {"Bills", "மின்சாரம்", 4}, {"Bills", "மின்கட்டணம்", 5}, {"Bills", "மின்வாரியம்", 5},
        {"Bills", "குடிநீர்", 5}, {"Bills", "சொத்துவரி", 5}, {"Bills", "வாடகை", 4},

        // Education
        {"Education", "school", 4}, {"Education", "college", 4}, {"Education", "tuition", 4},
        {"Education", "coaching", 4}, {"Education", "books", 4}, {"Education", "stationery", 4},
        {"Education", "fees", 4}, {"Education", "exam", 3}, {"Education", "படிப்பு", 4},

        // Entertainment
        {"Entertainment", "movie", 4}, {"Entertainment", "cinema", 4},
        {"Entertainment", "theatre", 4}, {"Entertainment", "theater", 4},
        {"Entertainment", "ticket", 3}, {"Entertainment", "game", 3},
        {"Entertainment", "resort", 4}, {"Entertainment", "திரைப்படம்", 4},

        // Transfers
        {"Transfers", "self transfer", 5}, {"Transfers", "wallet topup", 5},
        {"Transfers", "load wallet", 5}, {"Transfers", "own account", 5},
        {"Transfers", "send money", 4}, {"Transfers", "p2p", 4}
    };

    private static final Pattern VPA_PATTERN =
            Pattern.compile("\\b([A-Za-z0-9._-]{2,30})@([A-Za-z0-9.-]+)\\b");

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Categorize using merchant name only.
     */
    public static String categorize(String merchant) {
        return categorize(null, merchant, null, null).category;
    }

    /**
     * Categorize without Context.
     */
    public static CategoryResult categorize(String merchant, String vpa, String smsBody) {
        return categorize(null, merchant, vpa, smsBody);
    }

    /**
     * Categorize with Context to leverage learned user corrections and preference rules.
     */
    public static CategoryResult categorize(Context context, String merchant, String vpa, String smsBody) {
        // 1. Check if user already corrected/taught this merchant or VPA
        if (context != null) {
            CategoryResult userPreference = getUserCorrection(context, merchant, vpa);
            if (userPreference != null) {
                return userPreference;
            }
        }

        String combined = buildSignal(merchant, vpa, smsBody);

        // 2. Exact known-merchant lookup
        String knownCat = lookupKnownMerchant(combined);
        if (knownCat != null) {
            return new CategoryResult(knownCat, false, false, "Known merchant");
        }

        // 3. VPA handle analysis (e.g. pinkyoptics@ybl, kumarmedical@okaxis)
        if (vpa != null && !vpa.isEmpty()) {
            String vpaCat = categorizeByVpa(vpa.toLowerCase(Locale.ROOT));
            if (vpaCat != null) {
                return new CategoryResult(vpaCat, false, false, "VPA identifier match");
            }
        }
        if (smsBody != null) {
            Matcher m = VPA_PATTERN.matcher(smsBody);
            if (m.find()) {
                String vpaCat = categorizeByVpa(m.group(1).toLowerCase(Locale.ROOT));
                if (vpaCat != null) {
                    return new CategoryResult(vpaCat, false, false, "Embedded VPA match");
                }
            }
        }

        // 4. Strong self-transfer / P2P keywords
        if (combined.contains("self transfer") || combined.contains("own account")
                || combined.contains("wallet topup") || combined.contains("load wallet")) {
            return new CategoryResult("Transfers", true, false, "Self-transfer alert");
        }

        // 5. Keyword scoring across all signals (merchant, body, notes)
        Map<String, Integer> scores = new java.util.HashMap<>();
        for (Object[] rule : KEYWORD_RULES) {
            String cat = (String) rule[0];
            String kw  = (String) rule[1];
            int    wt  = (int)    rule[2];
            if (combined.contains(kw.toLowerCase(Locale.ROOT))) {
                scores.put(cat, scores.getOrDefault(cat, 0) + wt);
            }
        }

        if (!scores.isEmpty()) {
            String best = null;
            int maxScore = 0;
            for (Map.Entry<String, Integer> e : scores.entrySet()) {
                if (e.getValue() > maxScore) {
                    maxScore = e.getValue();
                    best = e.getKey();
                }
            }
            if (maxScore >= 3) {
                boolean isP2P = "Transfers".equals(best);
                return new CategoryResult(best, isP2P, false, "Keyword score=" + maxScore);
            }
        }

        // 6. Indian personal-name vs Seller detection
        // Many Indian small merchants use personal bank/UPI accounts (e.g. "Ramesh", "Suresh Kumar")
        if (looksLikePersonalName(merchant)) {
            // Check if transaction has item or business indicators
            if (hasItemOrPurchaseContext(smsBody)) {
                // Seller using personal name with item/business context -> ask confirmation
                return new CategoryResult("Others", false, true, "Personal-name seller purchase");
            }
            // No item/purchase context -> likely P2P money transfer
            return new CategoryResult("Transfers", true, true, "Personal name transfer");
        }

        // 7. Fallback for unclassified merchants
        return new CategoryResult("Others", false, false, "Unclassified transaction");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String buildSignal(String merchant, String vpa, String body) {
        StringBuilder sb = new StringBuilder();
        if (merchant != null) sb.append(merchant.toLowerCase(Locale.ROOT)).append(" ");
        if (vpa != null) sb.append(vpa.toLowerCase(Locale.ROOT)).append(" ");
        if (body != null) sb.append(body.toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    private static boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        int index = text.indexOf(word);
        while (index >= 0) {
            boolean startOk = (index == 0) || !Character.isLetterOrDigit(text.charAt(index - 1));
            int endIndex = index + word.length();
            boolean endOk = (endIndex == text.length()) || !Character.isLetterOrDigit(text.charAt(endIndex));
            if (startOk && endOk) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    private static String lookupKnownMerchant(String combined) {
        if (combined == null || combined.isEmpty()) return null;
        String lower = combined.toLowerCase(Locale.ROOT);
        String bestCat = null;
        int longestMatch = 0;
        for (Object[] row : KNOWN_MERCHANTS) {
            String m = ((String) row[0]).toLowerCase(Locale.ROOT);
            if (containsWord(lower, m)) {
                if (m.length() > longestMatch) {
                    longestMatch = m.length();
                    bestCat = (String) row[1];
                }
            }
        }
        return bestCat;
    }

    private static String categorizeByVpa(String vpaLocal) {
        if (vpaLocal == null || vpaLocal.isEmpty()) return null;
        String lower = vpaLocal.toLowerCase(Locale.ROOT);
        String bestCat = null;
        int longestMatch = 0;
        for (Object[] row : KNOWN_MERCHANTS) {
            String m = ((String) row[0]).toLowerCase(Locale.ROOT);
            if (lower.contains(m)) {
                if (m.length() > longestMatch) {
                    longestMatch = m.length();
                    bestCat = (String) row[1];
                }
            }
        }
        if (bestCat != null) return bestCat;
        for (Object[] rule : KEYWORD_RULES) {
            String kw = ((String) rule[1]).toLowerCase(Locale.ROOT);
            if (lower.contains(kw)) {
                if (kw.length() > longestMatch) {
                    longestMatch = kw.length();
                    bestCat = (String) rule[0];
                }
            }
        }
        return bestCat;
    }

    private static boolean looksLikePersonalName(String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) return false;
        String m = merchant.trim();
        String lower = m.toLowerCase(Locale.ROOT);

        // Reject if it contains commercial/store/utility/government keywords
        if (lower.matches(".*\\b(shop|store|mart|market|pharmacy|medicals|medical|clinic|hospital|hotel|restaurant|optics|optical|opticians|bakery|sweets|textiles|enterprises|traders|agency|centre|center|motors|services|kitchen|bhojanalaya|canteen|mess|salon|spa|hardware|electricals|ltd|pvt|corp|inc|co|associates|foods|jewellers|jewelers|stationery|book house|travels|tangedco|tneb|tantransco|cumta|cmrl|mtc|tnstc|setc|cmwssb|metrowater|aavin|board|corporation|authority|transport|metro|railway|discom)\\b.*")) {
            return false;
        }

        String[] words = m.split("\\s+");
        if (words.length < 1 || words.length > 4) return false;

        // Check if words resemble human name capitalizations
        int capCount = 0;
        for (String w : words) {
            if (!w.isEmpty() && Character.isUpperCase(w.charAt(0))) capCount++;
        }
        return capCount >= 1 && words.length <= 3;
    }

    private static boolean hasItemOrPurchaseContext(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("purchase") || lower.contains("bought")
                || lower.contains("bill") || lower.contains("invoice")
                || lower.contains("receipt") || lower.contains("item")
                || lower.contains("product") || lower.contains("order")
                || lower.contains("groceries") || lower.contains("medicine")
                || lower.contains("tea") || lower.contains("snacks")
                || lower.contains("vegetables") || lower.contains("milk")
                || lower.contains("paid at") || lower.contains("payment at");
    }
}
