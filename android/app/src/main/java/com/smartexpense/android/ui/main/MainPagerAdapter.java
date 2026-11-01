package com.smartexpense.android.ui.main;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.smartexpense.android.ui.budget.BudgetFragment;
import com.smartexpense.android.ui.dashboard.DashboardFragment;
import com.smartexpense.android.ui.expenses.ExpensesFragment;
import com.smartexpense.android.ui.reports.ReportsFragment;
import com.smartexpense.android.ui.settings.SettingsFragment;

/**
 * Adapter providing WhatsApp-style horizontal swipe navigation
 * across the 5 main pages:
 * 1. Dashboard
 * 2. Expenses
 * 3. Budget
 * 4. Reports
 * 5. Settings
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int NUM_PAGES = 5;

    public static final int POSITION_DASHBOARD = 0;
    public static final int POSITION_EXPENSES  = 1;
    public static final int POSITION_BUDGET    = 2;
    public static final int POSITION_REPORTS   = 3;
    public static final int POSITION_SETTINGS  = 4;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case POSITION_DASHBOARD:
                return new DashboardFragment();
            case POSITION_EXPENSES:
                return new ExpensesFragment();
            case POSITION_BUDGET:
                return new BudgetFragment();
            case POSITION_REPORTS:
                return new ReportsFragment();
            case POSITION_SETTINGS:
                return new SettingsFragment();
            default:
                throw new IllegalArgumentException("Invalid page position for MainPagerAdapter: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return NUM_PAGES;
    }
}
