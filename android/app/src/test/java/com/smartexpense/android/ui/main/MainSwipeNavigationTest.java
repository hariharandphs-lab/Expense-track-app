package com.smartexpense.android.ui.main;

import com.smartexpense.android.R;
import com.smartexpense.android.ui.budget.BudgetFragment;
import com.smartexpense.android.ui.dashboard.DashboardFragment;
import com.smartexpense.android.ui.expenses.ExpensesFragment;
import com.smartexpense.android.ui.reports.ReportsFragment;
import com.smartexpense.android.ui.settings.SettingsFragment;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying WhatsApp-style 5-page horizontal navigation configuration,
 * page order sequence, and bottom navigation menu synchronization mappings.
 */
public class MainSwipeNavigationTest {

    @Test
    public void testPageOrderSequenceAndCount() {
        assertEquals(5, MainPagerAdapter.NUM_PAGES);
        assertEquals(0, MainPagerAdapter.POSITION_DASHBOARD);
        assertEquals(1, MainPagerAdapter.POSITION_EXPENSES);
        assertEquals(2, MainPagerAdapter.POSITION_BUDGET);
        assertEquals(3, MainPagerAdapter.POSITION_REPORTS);
        assertEquals(4, MainPagerAdapter.POSITION_SETTINGS);
    }

    @Test
    public void testPositionToMenuItemMapping() {
        assertEquals(R.id.dashboardFragment, MainActivity.getMenuItemIdForPosition(0));
        assertEquals(R.id.expensesFragment, MainActivity.getMenuItemIdForPosition(1));
        assertEquals(R.id.budgetFragment, MainActivity.getMenuItemIdForPosition(2));
        assertEquals(R.id.reportsFragment, MainActivity.getMenuItemIdForPosition(3));
        assertEquals(R.id.settingsFragment, MainActivity.getMenuItemIdForPosition(4));

        // Edge bounds check
        assertEquals(-1, MainActivity.getMenuItemIdForPosition(-1));
        assertEquals(-1, MainActivity.getMenuItemIdForPosition(5));
    }

    @Test
    public void testMenuItemToPositionMapping() {
        assertEquals(0, MainActivity.getPositionForMenuItemId(R.id.dashboardFragment));
        assertEquals(1, MainActivity.getPositionForMenuItemId(R.id.expensesFragment));
        assertEquals(2, MainActivity.getPositionForMenuItemId(R.id.budgetFragment));
        assertEquals(3, MainActivity.getPositionForMenuItemId(R.id.reportsFragment));
        assertEquals(4, MainActivity.getPositionForMenuItemId(R.id.settingsFragment));

        // Unknown ID check
        assertEquals(-1, MainActivity.getPositionForMenuItemId(999999));
    }
}
