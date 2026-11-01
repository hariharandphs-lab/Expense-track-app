package com.smartexpense.android.ui.dashboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DashboardGreetingTest {

    @Test
    public void testPersonalizedGreeting_morningWithName() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(9, "Hariharan");
        assertEquals("Good Morning, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_afternoonWithName() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(14, "Hariharan");
        assertEquals("Good Afternoon, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_eveningWithName() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(20, "Hariharan");
        assertEquals("Good Evening, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_fullNameExtractsFirst() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(10, "Hariharan Shanmugam");
        assertEquals("Good Morning, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_emailFallbackFormatted() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(15, "hariharan@example.com");
        assertEquals("Good Afternoon, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_emailWithDotFallbackFormatted() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(19, "hariharan.dev@gmail.com");
        assertEquals("Good Evening, Hariharan 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_nullNameFallbackClean() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(20, null);
        assertEquals("Good Evening 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_emptyNameFallbackClean() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(8, "");
        assertEquals("Good Morning 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_whitespaceNameFallbackClean() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(13, "   ");
        assertEquals("Good Afternoon 👋", greeting);
    }

    @Test
    public void testPersonalizedGreeting_genericPlaceholdersFallbackClean() {
        assertEquals("Good Evening 👋", DashboardFragment.buildPersonalizedGreeting(21, "null"));
        assertEquals("Good Evening 👋", DashboardFragment.buildPersonalizedGreeting(21, "user"));
        assertEquals("Good Evening 👋", DashboardFragment.buildPersonalizedGreeting(21, "demo-user"));
    }

    @Test
    public void testPersonalizedGreeting_differentAccount() {
        String greeting = DashboardFragment.buildPersonalizedGreeting(11, "Sophia");
        assertEquals("Good Morning, Sophia 👋", greeting);
    }
}
