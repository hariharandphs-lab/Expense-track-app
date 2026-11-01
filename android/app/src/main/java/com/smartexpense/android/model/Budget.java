package com.smartexpense.android.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

/**
 * Budget model for monthly budget tracking.
 */
public class Budget implements Serializable {

    @SerializedName("id")
    private String id;

    @SerializedName("totalBudget")
    private double totalBudget;

    @SerializedName("month")
    private int month;

    @SerializedName("year")
    private int year;

    @SerializedName("categoryBudgets")
    private Map<String, Double> categoryBudgets;

    @SerializedName("totalSpent")
    private double totalSpent;

    @SerializedName("remaining")
    private double remaining;

    @SerializedName("percentageUsed")
    private double percentageUsed;

    @SerializedName("userId")
    private String userId;

    // Getters & Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getTotalBudget() { return totalBudget; }
    public void setTotalBudget(double totalBudget) { this.totalBudget = totalBudget; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public Map<String, Double> getCategoryBudgets() { return categoryBudgets; }
    public void setCategoryBudgets(Map<String, Double> categoryBudgets) { this.categoryBudgets = categoryBudgets; }
    public double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(double totalSpent) { this.totalSpent = totalSpent; }
    public double getRemaining() { return remaining; }
    public void setRemaining(double remaining) { this.remaining = remaining; }
    public double getPercentageUsed() { return percentageUsed; }
    public void setPercentageUsed(double percentageUsed) { this.percentageUsed = percentageUsed; }
}
