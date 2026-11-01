package com.smartexpense.android.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Expense model matching the backend Expense entity.
 */
public class Expense implements Serializable {

    @SerializedName("id")
    private String id;

    @SerializedName("amount")
    private double amount;

    @SerializedName("currency")
    private String currency;

    @SerializedName("merchantName")
    private String merchantName;

    @SerializedName("description")
    private String description;

    @SerializedName("category")
    private String category;

    @SerializedName("paymentMethod")
    private String paymentMethod;

    @SerializedName("transactionType")
    private String transactionType;  // DEBIT, CREDIT

    @SerializedName("source")
    private String source;           // MANUAL, SMS, OCR, VOICE

    @SerializedName("status")
    private String status;           // CONFIRMED, PENDING_CATEGORY

    @SerializedName("transactionDate")
    private String transactionDate;

    @SerializedName("bankName")
    private String bankName;

    @SerializedName("referenceNumber")
    private String referenceNumber;

    @SerializedName("receiptUrl")
    private String receiptUrl;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("clientTransactionId")
    private String clientTransactionId;

    @SerializedName("userId")
    private String userId;

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getClientTransactionId() { return clientTransactionId; }
    public void setClientTransactionId(String clientTransactionId) { this.clientTransactionId = clientTransactionId; }
}
