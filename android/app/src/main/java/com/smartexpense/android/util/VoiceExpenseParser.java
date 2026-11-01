package com.smartexpense.android.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust Multilingual Voice Expense Parser supporting English, Tamil, and Tanglish.
 * Extracts amount, merchant, category, and action intent from voice speech input.
 */
public class VoiceExpenseParser {

    public static class ParsedVoiceExpense {
        private Double amount;
        private String merchantName;
        private String category = "Others";
        private String description;
        private boolean isAddCommand = false;
        private String rawText;

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isAddCommand() { return isAddCommand; }
        public void setAddCommand(boolean addCommand) { isAddCommand = addCommand; }

        public String getRawText() { return rawText; }
        public void setRawText(String rawText) { this.rawText = rawText; }

        public String getFormattedAmount() {
            if (amount == null) return "";
            if (amount == Math.floor(amount)) {
                return String.valueOf(amount.intValue());
            }
            return String.format(Locale.US, "%.2f", amount);
        }
    }

    // Number mapping for Tamil / Tanglish / English number words
    private static final Map<String, Double> WORD_TO_NUMBER = new HashMap<>();
    static {
        WORD_TO_NUMBER.put("one", 1.0); WORD_TO_NUMBER.put("ஒன்று", 1.0); WORD_TO_NUMBER.put("onnu", 1.0);
        WORD_TO_NUMBER.put("two", 2.0); WORD_TO_NUMBER.put("இரண்டு", 2.0); WORD_TO_NUMBER.put("rendu", 2.0);
        WORD_TO_NUMBER.put("five", 5.0); WORD_TO_NUMBER.put("ஐந்து", 5.0); WORD_TO_NUMBER.put("anju", 5.0);
        WORD_TO_NUMBER.put("ten", 10.0); WORD_TO_NUMBER.put("பத்து", 10.0); WORD_TO_NUMBER.put("pathu", 10.0);
        WORD_TO_NUMBER.put("twenty", 20.0); WORD_TO_NUMBER.put("இருபது", 20.0); WORD_TO_NUMBER.put("irubadhu", 20.0); WORD_TO_NUMBER.put("irupathu", 20.0);
        WORD_TO_NUMBER.put("thirty", 30.0); WORD_TO_NUMBER.put("முப்பது", 30.0); WORD_TO_NUMBER.put("muppadhu", 30.0);
        WORD_TO_NUMBER.put("forty", 40.0); WORD_TO_NUMBER.put("நாற்பது", 40.0); WORD_TO_NUMBER.put("naappadhu", 40.0);
        WORD_TO_NUMBER.put("fifty", 50.0); WORD_TO_NUMBER.put("ஐம்பது", 50.0); WORD_TO_NUMBER.put("aimbadhu", 50.0); WORD_TO_NUMBER.put("ambadhu", 50.0);
        WORD_TO_NUMBER.put("hundred", 100.0); WORD_TO_NUMBER.put("நூறு", 100.0); WORD_TO_NUMBER.put("nooru", 100.0);
        WORD_TO_NUMBER.put("two hundred", 200.0); WORD_TO_NUMBER.put("இருநூறு", 200.0); WORD_TO_NUMBER.put("rennooru", 200.0);
        WORD_TO_NUMBER.put("five hundred", 500.0); WORD_TO_NUMBER.put("ஐநூறு", 500.0); WORD_TO_NUMBER.put("ainooru", 500.0);
        WORD_TO_NUMBER.put("thousand", 1000.0); WORD_TO_NUMBER.put("ஆயிரம்", 1000.0); WORD_TO_NUMBER.put("aayiram" , 1000.0);
        WORD_TO_NUMBER.put("two thousand", 2000.0); WORD_TO_NUMBER.put("இரண்டாயிரம்", 2000.0); WORD_TO_NUMBER.put("rendaayiram", 2000.0);
        WORD_TO_NUMBER.put("five thousand", 5000.0); WORD_TO_NUMBER.put("ஐந்தாயிரம்", 5000.0); WORD_TO_NUMBER.put("anjaayiram", 5000.0);
        WORD_TO_NUMBER.put("ten thousand", 10000.0); WORD_TO_NUMBER.put("பத்தாயிரம்", 10000.0); WORD_TO_NUMBER.put("pathaayiram", 10000.0);
    }

    // Category detection rules
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new HashMap<>();
    static {
        CATEGORY_KEYWORDS.put("Food", new String[]{
                "food", "lunch", "dinner", "breakfast", "snacks", "coffee", "tea", "chai", "kaapi", "tiffin",
                "meal", "restaurant", "cafe", "hotel", "swiggy", "zomato", "biryani", "dosai", "idli", "parotta",
                "சாப்பாடு", "உணவு", "காபி", "தேநீர்", "டீ", "சாயா", "சிற்றுண்டி", "ஹோட்டல்", "பிரியாணி"
        });
        CATEGORY_KEYWORDS.put("Grocery", new String[]{
                "grocery", "groceries", "supermarket", "mart", "dmart", "blinkit", "zepto", "instamart", "kirana",
                "milk", "vegetables", "fruits", "provisions", "maligai", "kaaikari", "paal", "aavin", "ஆவின்", "aavin milk", "aavin paal",
                "மளிகை", "காய்கறி", "பால்"
        });
        CATEGORY_KEYWORDS.put("Travel", new String[]{
                "travel", "uber", "ola", "rapido", "cab", "taxi", "bus", "train", "metro", "auto", "flight", "trip",
                "cumta", "cmrl", "mtc", "tnstc", "setc", "singara chennai", "chalo", "ticket", "payanam", "பயணம்",
                "வாடகைக்கார்", "பேருந்து", "ரயில்", "மெட்ரோ"
        });
        CATEGORY_KEYWORDS.put("Fuel", new String[]{
                "fuel", "petrol", "diesel", "gas", "bunk", "shell", "hp", "indian oil", "bharat petroleum",
                "பெட்ரோல்", "டீசல்", "எரிபொருள்"
        });
        CATEGORY_KEYWORDS.put("Shopping", new String[]{
                "shopping", "amazon", "flipkart", "myntra", "ajio", "meesho", "zara", "clothes", "dress", "shirt",
                "shoes", "thuni", "ஷாப்பிங்", "ஆடை", "துணி"
        });
        CATEGORY_KEYWORDS.put("Medical", new String[]{
                "medical", "medicine", "medicines", "pharmacy", "doctor", "hospital", "clinic", "apollo", "netmeds", "pharmeasy",
                "optics", "optical", "spectacles", "glasses", "lenskart", "specsmakers", "eyewear", "eye care", "lens",
                "tablet", "marunthu", "maruthuvam", "மருந்து", "மருத்துவம்", "மருத்துவமனை", "மாத்திரை", "கண்ணாடி", "kannadi"
        });
        CATEGORY_KEYWORDS.put("Bills", new String[]{
                "bill", "bills", "electricity", "eb bill", "current bill", "tangedco", "tneb", "tantransco",
                "cmwssb", "metrowater", "recharge", "wifi", "rent", "vadagai", "maintenance",
                "மின்சாரம்", "மின்கட்டணம்", "மின்வாரியம்", "குடிநீர்", "சொத்துவரி", "வாடகை", "ரீசார்ஜ்", "கட்டணம்"
        });
        CATEGORY_KEYWORDS.put("Entertainment", new String[]{
                "entertainment", "movie", "cinema", "pvr", "inox", "netflix", "prime", "hotstar", "bookmyshow",
                "padam", "திரைப்படம்", "சினிமா", "பொழுதுபோக்கு"
        });
        CATEGORY_KEYWORDS.put("Education", new String[]{
                "education", "course", "tuition", "books", "fees", "school", "college", "padipu", "கல்வி", "கட்டணம்", "புத்தகம்"
        });
        CATEGORY_KEYWORDS.put("Transfers", new String[]{
                "transfer", "sent money", "p2p", "anuppinen", "அனுப்பினேன்", "பரிமாற்றம்"
        });
    }

    // Well-known Indian brand/merchant names
    private static final String[] KNOWN_MERCHANTS = {
            "Swiggy", "Zomato", "DMart", "Blinkit", "Zepto", "Instamart", "Uber", "Ola", "Rapido",
            "Amazon", "Flipkart", "Myntra", "Ajio", "Meesho", "Apollo", "Netmeds", "PharmEasy",
            "Chai Point", "Starbucks", "McDonalds", "KFC", "Dominos", "Pizza Hut", "Saravana Bhavan",
            "Reliance Fresh", "BigBasket", "Indian Oil", "Bharat Petroleum", "HP Petrol", "Shell",
            "Airtel", "Jio", "Bescom", "TNEB", "TANGEDCO", "CUMTA", "CMRL", "MTC", "TNSTC", "SETC",
            "CMWSSB", "MetroWater", "Aavin", "PVR", "INOX", "BookMyShow", "Netflix"
    };

    /**
     * Parses spoken or typed input in English, Tamil, or Tanglish.
     */
    public static ParsedVoiceExpense parse(String rawText) {
        ParsedVoiceExpense result = new ParsedVoiceExpense();
        if (rawText == null || rawText.trim().isEmpty()) {
            return result;
        }

        result.setRawText(rawText.trim());
        String clean = rawText.trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        // 1. Detect if this is an explicit add command
        boolean isAdd = lower.contains("add") || lower.contains("spent") || lower.contains("paid")
                || lower.contains("சேர்") || lower.contains("போடு") || lower.contains("பதிவு")
                || lower.contains("kuduthen") || lower.contains("pannen") || lower.contains("selavu")
                || lower.contains("add pannu");
        result.setAddCommand(isAdd);

        // 2. Extract numeric or word amount
        Double extractedAmount = extractAmount(clean, lower);
        result.setAmount(extractedAmount);

        // 3. Extract Category
        String category = extractCategory(lower);
        result.setCategory(category != null ? category : "Others");

        // 4. Extract Merchant Name
        String merchant = extractMerchant(clean, lower, result.getCategory());
        result.setMerchantName(merchant);

        // 5. Build clean description
        if (result.getAmount() != null) {
            result.setDescription(String.format(Locale.US, "Voice Entry: ₹%.0f at %s (%s)",
                    result.getAmount(),
                    result.getMerchantName() != null ? result.getMerchantName() : "General",
                    result.getCategory()));
        } else {
            result.setDescription(clean);
        }

        return result;
    }

    private static Double extractAmount(String clean, String lower) {
        // Pattern 1: Digits with currency symbol, Rs, INR, ரூபாய், roobai, rubai
        // Examples: "₹450", "450 rupees", "250 roobai", "1200 ரூபாய்", "20 rs", "500.50"
        Pattern p1 = Pattern.compile(
                "(?:₹|rs\\.?|inr|ரூபாய்|ரூ|roobai|rubai|ruba)?\\s*([0-9]+(?:[,.][0-9]{1,2})?)\\s*(?:₹|rs\\.?|inr|rupees?|bucks?|roobai|rubai|ruba|ரூபாய்|ரூ)?",
                Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(clean);
        Double foundAmount = null;

        while (m1.find()) {
            String numStr = m1.group(1);
            if (numStr != null && !numStr.isEmpty()) {
                try {
                    double val = Double.parseDouble(numStr.replace(",", ""));
                    if (val > 0) {
                        foundAmount = val;
                        break;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (foundAmount != null) {
            return foundAmount;
        }

        // Pattern 2: Word-based numbers in Tamil / Tanglish / English
        for (Map.Entry<String, Double> entry : WORD_TO_NUMBER.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static String extractCategory(String lower) {
        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return "Others";
    }

    private static String extractMerchant(String clean, String lower, String inferredCategory) {
        // Step A: Check well-known merchants directly
        for (String m : KNOWN_MERCHANTS) {
            if (lower.contains(m.toLowerCase(Locale.ROOT))) {
                return m;
            }
        }

        // Step B: Tanglish suffix markers: "<Merchant> ku", "<Merchant> la"
        // E.g. "tea ku 20", "swiggy la 450", "petrol ku 500", "doctor kitta 300"
        Pattern pTanglish = Pattern.compile(
                "([A-Za-z0-9&'._-]{2,25})\\s+(?:ku|la|kitta|la\\s+irunthu)",
                Pattern.CASE_INSENSITIVE);
        Matcher mTanglish = pTanglish.matcher(clean);
        if (mTanglish.find()) {
            String candidate = mTanglish.group(1).trim();
            if (!isStopWord(candidate)) {
                return capitalize(candidate);
            }
        }

        // Step C: English prepositions: "at <Merchant>", "to <Merchant>", "from <Merchant>"
        Pattern pEng = Pattern.compile(
                "(?:at|to|from|for|in)\\s+([A-Za-z0-9&'._-]{2,25})",
                Pattern.CASE_INSENSITIVE);
        Matcher mEng = pEng.matcher(clean);
        if (mEng.find()) {
            String candidate = mEng.group(1).trim();
            if (!isStopWord(candidate)) {
                return capitalize(candidate);
            }
        }

        // Fallback: If category is known, use category as default merchant label
        if (!"Others".equalsIgnoreCase(inferredCategory)) {
            return inferredCategory;
        }

        return "General Store";
    }

    private static boolean isStopWord(String word) {
        if (word == null || word.length() < 2) return true;
        String w = word.toLowerCase(Locale.ROOT);
        return w.equals("rs") || w.equals("inr") || w.equals("rupees") || w.equals("rupee")
                || w.equals("roobai") || w.equals("rubai") || w.equals("the") || w.equals("my")
                || w.equals("for") || w.equals("add") || w.equals("spent") || w.equals("paid");
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }
}
