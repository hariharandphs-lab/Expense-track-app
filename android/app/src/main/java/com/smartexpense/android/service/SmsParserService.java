package com.smartexpense.android.service;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust parser for Indian bank and UPI transaction alerts.
 * Raw message text is never persisted to ensure privacy compliance.
 */
public final class SmsParserService {

    private static final String TAG = "SmsParserService";

    private static final Pattern OTP = Pattern.compile(
            "\\b(?:otp|one[ -]?time password|verification code|security code)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PROMOTION = Pattern.compile(
            "\\b(?:offer|discount|deal|cashback|reward points|click here|limited time|won a prize|apply now|pre[- ]?approved|congratulations|claim offer|apply for|unsubscribe)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBIT_ACTION = Pattern.compile(
            "\\b(?:debited|spent|paid|sent|withdrawn|deducted|charged|purchase(?:d)?|transferred|transfer(?:red)?\\s+to|dr\\b|dr\\.)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CREDIT_ACTION = Pattern.compile(
            "\\b(?:credited|received|deposited|refunded|cr\\b|cr\\.)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPLETION = Pattern.compile(
            "\\b(?:successful|successfully|completed|processed|debited|credited|paid|spent|sent|received|deposited|refunded|transferred|dr\\b|cr\\b)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String NUMBER = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";

    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?:₹|\\bINR\\b|\\bRs\\.?\\s*)\\s*" + NUMBER,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LABELED_AMOUNT = Pattern.compile(
            "\\b(?:amount|txn amount|transaction amount|sum of)\\s*[:= -]*\\s*(?:₹|INR|Rs\\.?\\s*)?" + NUMBER,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MERCHANT = Pattern.compile(
            "\\b(?:merchant(?: name)?|payee|towards|at|to|transfer(?:red)?\\s+to)\\s*[:=-]?\\s*" +
                    "([A-Za-z0-9][A-Za-z0-9 &'._-]{1,48}?)" +
                    "(?=\\s+(?:on|via|using|from|ref(?:erence)?|txn|transaction|utr|a/c|account|avl|available|bal|balance)\\b|[;|.]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FALLBACK_MERCHANT = Pattern.compile(
            "\\b(?:from|info:?|for)\\s*[:=-]?\\s*" +
                    "([A-Za-z0-9][A-Za-z0-9 &'._-]{1,48}?)" +
                    "(?=\\s+(?:on|via|using|from|ref(?:erence)?|txn|transaction|utr|a/c|account|avl|available|bal|balance)\\b|[;|.]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UPI_INFO = Pattern.compile(
            "UPI[/:-]([A-Za-z0-9._-]+)(?:[/:-]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VPA = Pattern.compile("\\b([A-Za-z0-9._-]{2,64})@[A-Za-z0-9.-]+\\b");

    private static final Pattern REFERENCE = Pattern.compile(
            "\\b(?:ref(?:erence)?(?: no)?|txn(?: id| no)?|utr(?: no)?|rrn)\\s*[:#=-]?\\s*([A-Z0-9]{6,24})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACCOUNT_LAST_FOUR = Pattern.compile(
            "\\b(?:a/c|account|acct)\\s*(?:no\\.?|number|ending)?\\s*[*xX-]*([0-9]{4})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAYMENT_UPI = Pattern.compile("\\b(?:upi|bhim|gpay|phonepe|paytm)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYMENT_CARD = Pattern.compile("\\b(?:credit|debit) card\\b|\\bcard ending\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYMENT_BANK = Pattern.compile("\\b(?:neft|rtgs|imps)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SMS_DATE = Pattern.compile(
            "\\b(?:on|dated|date)?\\s*:?\\s*([0-9]{1,2})[-/ ]?([0-9]{1,2}|[A-Za-z]{3,9})(?:[-/ ]([0-9]{2,4}))?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> SENDER_TO_BANK = new HashMap<>();
    static {
        SENDER_TO_BANK.put("SBI", "SBI"); SENDER_TO_BANK.put("SBIINB", "SBI"); SENDER_TO_BANK.put("SBICRD", "SBI");
        SENDER_TO_BANK.put("HDFC", "HDFC Bank"); SENDER_TO_BANK.put("HDFCBK", "HDFC Bank"); SENDER_TO_BANK.put("HDFCCC", "HDFC Bank");
        SENDER_TO_BANK.put("ICICI", "ICICI Bank"); SENDER_TO_BANK.put("ICICIB", "ICICI Bank"); SENDER_TO_BANK.put("ICICIT", "ICICI Bank");
        SENDER_TO_BANK.put("AXIS", "Axis Bank"); SENDER_TO_BANK.put("AXISBK", "Axis Bank");
        SENDER_TO_BANK.put("KOTAK", "Kotak Bank"); SENDER_TO_BANK.put("KOTAKB", "Kotak Bank");
        SENDER_TO_BANK.put("PNB", "PNB"); SENDER_TO_BANK.put("PNBSMS", "PNB");
        SENDER_TO_BANK.put("BOB", "Bank of Baroda"); SENDER_TO_BANK.put("BOBTXN", "Bank of Baroda"); SENDER_TO_BANK.put("BARODA", "Bank of Baroda");
        SENDER_TO_BANK.put("CANBNK", "Canara Bank"); SENDER_TO_BANK.put("CANARA", "Canara Bank");
        SENDER_TO_BANK.put("YESBNK", "Yes Bank"); SENDER_TO_BANK.put("YESB", "Yes Bank");
        SENDER_TO_BANK.put("IDFC", "IDFC First Bank"); SENDER_TO_BANK.put("IDFCFB", "IDFC First Bank");
        SENDER_TO_BANK.put("INDUS", "IndusInd Bank"); SENDER_TO_BANK.put("INDUSB", "IndusInd Bank");
        SENDER_TO_BANK.put("UNION", "Union Bank"); SENDER_TO_BANK.put("UNIONB", "Union Bank");
        SENDER_TO_BANK.put("BOI", "Bank of India"); SENDER_TO_BANK.put("BOITXN", "Bank of India");
        SENDER_TO_BANK.put("PAYTM", "Paytm Payments Bank"); SENDER_TO_BANK.put("PYTM", "Paytm Payments Bank");
        SENDER_TO_BANK.put("PHONEPE", "PhonePe"); SENDER_TO_BANK.put("GPAY", "Google Pay");
        SENDER_TO_BANK.put("FEDERAL", "Federal Bank"); SENDER_TO_BANK.put("FEDBNK", "Federal Bank");
        SENDER_TO_BANK.put("RBL", "RBL Bank"); SENDER_TO_BANK.put("RBLBNK", "RBL Bank");
    }

    private SmsParserService() {}

    public static final class ParsedTransaction {
        public double amount;
        public String transactionType;
        public String merchantName;
        public String paymentMethod;
        public String referenceNumber;
        public String bankName;
        public String accountLastFour;
        public String transactionDate;
        public String clientTransactionId;
        public String upiId;
        public String category;
        public boolean isProbablyP2P;
        public boolean needsUserConfirmation;
        public String categorizationReason;
    }

    public static ParsedTransaction parse(String body, String sender) {
        return parse(body, sender, System.currentTimeMillis());
    }

    public static ParsedTransaction parse(String body, String sender, long receivedAtMillis) {
        if (body == null || body.trim().isEmpty()) {
            logD("[STAGE: PARSE] Discarded: Empty SMS body from " + sender);
            return null;
        }

        if (OTP.matcher(body).find()) {
            logI("[STAGE: PARSE] Discarded: OTP / verification code detected from " + sender);
            return null;
        }

        boolean debit = DEBIT_ACTION.matcher(body).find();
        boolean credit = CREDIT_ACTION.matcher(body).find();
        boolean promotional = PROMOTION.matcher(body).find();

        if (promotional && !debit && !credit) {
            logI("[STAGE: PARSE] Discarded: Promotional SMS without transaction alert from " + sender);
            return null;
        }

        if (!debit && !credit) {
            logD("[STAGE: PARSE] Discarded: No debit or credit action detected from " + sender);
            return null;
        }

        if (!COMPLETION.matcher(body).find()) {
            logD("[STAGE: PARSE] Discarded: Not a completed transaction alert from " + sender);
            return null;
        }

        Double amount = extractAmount(body);
        if (amount == null || amount <= 0 || amount > 100_000_000d) {
            logW("[STAGE: PARSE] Discarded: Unable to extract valid amount from " + sender);
            return null;
        }

        ParsedTransaction tx = new ParsedTransaction();
        tx.amount = amount;
        tx.transactionType = credit && !debit ? "CREDIT" : "DEBIT";
        tx.merchantName = extractMerchant(body);
        tx.paymentMethod = PAYMENT_UPI.matcher(body).find() ? "UPI"
                : PAYMENT_CARD.matcher(body).find() ? "CARD"
                : PAYMENT_BANK.matcher(body).find() ? "NET_BANKING"
                : "UNKNOWN";
        tx.referenceNumber = matchGroup(REFERENCE, body, 1);
        tx.accountLastFour = matchGroup(ACCOUNT_LAST_FOUR, body, 1);
        tx.bankName = findBank(sender, body);
        tx.transactionDate = extractDate(body, receivedAtMillis);
        tx.clientTransactionId = fingerprint(sender, tx.amount, tx.merchantName,
                tx.transactionDate, tx.transactionType, tx.referenceNumber);

        // Extract VPA/UPI ID if present
        Matcher vpaMatch = VPA.matcher(body);
        if (vpaMatch.find()) {
            tx.upiId = vpaMatch.group(0);
        }

        // Multi-signal intelligent categorization & P2P detection
        com.smartexpense.android.util.SmartCategorizer.CategoryResult cat =
                com.smartexpense.android.util.SmartCategorizer.categorize(tx.merchantName, tx.upiId, body);
        tx.category = cat.category;
        tx.isProbablyP2P = cat.isProbablyP2P;
        tx.needsUserConfirmation = cat.needsUserConfirmation;
        tx.categorizationReason = cat.reason;

        logI("[STAGE: PARSE] Parsed successfully: ₹" + tx.amount + " (" + tx.transactionType + ") at "
                + tx.merchantName + " via " + tx.paymentMethod + " [Bank: " + tx.bankName
                + ", Date: " + tx.transactionDate + ", Cat: " + tx.category + " (P2P=" + tx.isProbablyP2P + "), TxId: " + tx.clientTransactionId + "]");
        return tx;
    }

    private static void logD(String msg) {
        try { Log.d(TAG, msg); } catch (Throwable ignored) {}
    }

    private static void logI(String msg) {
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    private static void logW(String msg) {
        try { Log.w(TAG, msg); } catch (Throwable ignored) {}
    }

    private static Double extractAmount(String body) {
        Matcher labeled = LABELED_AMOUNT.matcher(body);
        if (labeled.find()) return parseNumber(labeled.group(1));

        Matcher currency = CURRENCY_AMOUNT.matcher(body);
        Double selected = null;
        while (currency.find()) {
            Double candidate = parseNumber(currency.group(1));
            if (candidate == null) continue;

            // Check if this currency mention is just the balance or limit
            int prefixStart = Math.max(0, currency.start() - 32);
            String beforeCandidate = body.substring(prefixStart, currency.start()).toLowerCase(Locale.ROOT);
            if (beforeCandidate.contains("bal") || beforeCandidate.contains("balance")
                    || beforeCandidate.contains("limit") || beforeCandidate.contains("avl")
                    || beforeCandidate.contains("avail")) {
                continue;
            }

            int start = Math.max(0, currency.start() - 50);
            int end = Math.min(body.length(), currency.end() + 50);
            String context = body.substring(start, end);
            if (DEBIT_ACTION.matcher(context).find() || CREDIT_ACTION.matcher(context).find()) return candidate;
            if (selected == null) selected = candidate;
        }
        return selected;
    }

    private static Double parseNumber(String raw) {
        try { return Double.parseDouble(raw.replace(",", "")); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String extractMerchant(String body) {
        // Priority 1: UPI transaction pattern like UPI/swiggy/12345
        Matcher upiMatch = UPI_INFO.matcher(body);
        if (upiMatch.find()) {
            String val = upiMatch.group(1).trim().replaceAll("[._-]+", " ").trim();
            if (val.length() >= 2 && !val.matches("(?i)[0-9]+")) return cleanMerchant(val);
        }

        // Priority 2: VPA handle like swiggy@icici
        Matcher vpa = VPA.matcher(body);
        if (vpa.find()) return cleanMerchant(vpa.group(1).replaceAll("[._-]+", " ").trim());

        // Priority 3: Payee / Merchant indicators (to, at, payee, towards, etc.)
        Matcher matcher = MERCHANT.matcher(body);
        while (matcher.find()) {
            String value = cleanMerchant(matcher.group(1));
            if (isValidMerchant(value)) {
                return value;
            }
        }

        // Priority 4: Fallback info/from if not account/bank
        Matcher fallback = FALLBACK_MERCHANT.matcher(body);
        while (fallback.find()) {
            String value = cleanMerchant(fallback.group(1));
            if (isValidMerchant(value)) {
                return value;
            }
        }

        return "Unknown merchant";
    }

    private static String cleanMerchant(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .replaceAll("(?i)\\s+(?:pvt|ltd|limited|inc|corp)\\.?$", "")
                .replaceAll("(?i)\\s+(?:upi|vpa|ref|txn)$", "")
                .trim();
    }

    private static boolean isValidMerchant(String value) {
        if (value == null || value.length() < 2) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return !lower.matches("(?i)^(?:your account|a/c|account|card|upi|bank|hdfc|sbi|icici|axis|kotak|pnb|bob)$");
    }

    private static String extractDate(String body, long receivedAtMillis) {
        Matcher matcher = SMS_DATE.matcher(body);
        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                String monthPart = matcher.group(2);
                int month;
                if (monthPart.matches("[0-9]+")) {
                    month = Integer.parseInt(monthPart);
                } else {
                    month = parseMonthName(monthPart);
                }

                if (month > 12 && day <= 12) {
                    int tmp = day;
                    day = month;
                    month = tmp;
                }

                int year = matcher.group(3) == null ?
                        Instant.ofEpochMilli(receivedAtMillis).atZone(ZoneId.systemDefault()).getYear()
                        : Integer.parseInt(matcher.group(3));
                if (year < 100) year += 2000;

                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    return java.time.LocalDate.of(year, month, day).atStartOfDay()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } catch (RuntimeException ignored) { }
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(receivedAtMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static int parseMonthName(String m) {
        String lower = m.toLowerCase(Locale.ROOT);
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < months.length; i++) {
            if (lower.startsWith(months[i])) return i + 1;
        }
        return 1;
    }

    private static String findBank(String sender, String body) {
        if (sender != null) {
            String normalized = sender.toUpperCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : SENDER_TO_BANK.entrySet()) {
                if (normalized.contains(entry.getKey())) return entry.getValue();
            }
        }
        if (body != null) {
            String normBody = body.toUpperCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : SENDER_TO_BANK.entrySet()) {
                if (normBody.contains(entry.getKey())) return entry.getValue();
            }
        }
        return "Bank";
    }

    private static String matchGroup(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).toUpperCase(Locale.ROOT) : null;
    }

    private static String fingerprint(String sender, double amount, String merchant,
                                      String date, String type, String reference) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Hash extracted transaction identity only; never derive a stored key from raw message text.
            String identity = (sender == null ? "" : sender) + "|" + amount + "|"
                    + (merchant == null ? "" : merchant.toLowerCase(Locale.ROOT)) + "|"
                    + date + "|" + type + "|" + (reference == null ? "" : reference);
            byte[] hash = digest.digest(identity
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception e) {
            return Long.toHexString(System.nanoTime());
        }
    }
}
