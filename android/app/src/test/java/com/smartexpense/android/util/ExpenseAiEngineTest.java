package com.smartexpense.android.util;

import com.smartexpense.android.model.Expense;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExpenseAiEngineTest {

    private List<Expense> mockExpenses;

    @Before
    public void setup() {
        mockExpenses = new ArrayList<>();

        Expense e1 = new Expense();
        e1.setId("e1");
        e1.setAmount(450.0);
        e1.setMerchantName("Apollo Pharmacy");
        e1.setCategory("Medical");
        e1.setTransactionDate("2026-10-23T14:30:00");
        e1.setTransactionType("EXPENSE");
        e1.setDescription("Cough syrup and paracetamol");
        mockExpenses.add(e1);

        Expense e2 = new Expense();
        e2.setId("e2");
        e2.setAmount(2500.0);
        e2.setMerchantName("Pinky Optics");
        e2.setCategory("Medical");
        e2.setTransactionDate("2026-10-18T11:00:00");
        e2.setTransactionType("EXPENSE");
        e2.setDescription("New reading spectacles and frames");
        mockExpenses.add(e2);

        Expense e3 = new Expense();
        e3.setId("e3");
        e3.setAmount(320.0);
        e3.setMerchantName("MedPlus");
        e3.setCategory("Medical");
        e3.setTransactionDate("2026-10-15T18:20:00");
        e3.setTransactionType("EXPENSE");
        e3.setDescription("Vitamins and prescription medicine");
        mockExpenses.add(e3);

        Expense e4 = new Expense();
        e4.setId("e4");
        e4.setAmount(380.0);
        e4.setMerchantName("Swiggy");
        e4.setCategory("Food");
        e4.setTransactionDate("2026-10-24T19:45:00");
        e4.setTransactionType("EXPENSE");
        e4.setDescription("Dinner order");
        mockExpenses.add(e4);

        Expense e5 = new Expense();
        e5.setId("e5");
        e5.setAmount(180.0);
        e5.setMerchantName("Uber");
        e5.setCategory("Travel");
        e5.setTransactionDate("2026-10-22T09:15:00");
        e5.setTransactionType("EXPENSE");
        e5.setDescription("Office commute ride");
        mockExpenses.add(e5);
    }

    @Test
    public void testLanguageDetection_english() {
        assertEquals("en", ExpenseAiEngine.detectLanguage("How much did I spend on food?"));
        assertEquals("en", ExpenseAiEngine.detectLanguage("Show my total spending"));
    }

    @Test
    public void testLanguageDetection_tamilScript() {
        assertEquals("ta", ExpenseAiEngine.detectLanguage("மருந்து செலவு எவ்வளவு?"));
        assertEquals("ta", ExpenseAiEngine.detectLanguage("இன்றைய செலவு காட்டு"));
        assertEquals("ta", ExpenseAiEngine.detectLanguage("வணக்கம்"));
    }

    @Test
    public void testLanguageDetection_tanglish() {
        assertEquals("tanglish", ExpenseAiEngine.detectLanguage("Medicine ku evlo spend pannen?"));
        assertEquals("tanglish", ExpenseAiEngine.detectLanguage("Swiggy expenses kaattu"));
        assertEquals("tanglish", ExpenseAiEngine.detectLanguage("Innaiku evlo aachu?"));
        assertEquals("tanglish", ExpenseAiEngine.detectLanguage("Tea ku 20 add pannu"));
    }

    @Test
    public void testMedicineQuery_strictlyExcludesOpticalShop() {
        // Query specifically for medicine
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "How much did I spend on medicine?", mockExpenses, "en");
        String text = res.getText();

        assertNotNull(text);
        // Apollo Pharmacy and MedPlus should be present
        assertTrue("Apollo Pharmacy should be included", text.contains("Apollo Pharmacy"));
        assertTrue("MedPlus should be included", text.contains("MedPlus"));
        // Total should be 450 + 320 = 770
        assertTrue("Sum should be 770", text.contains("770"));

        // Pinky Optics MUST NOT be included
        assertFalse("Pinky Optics should NOT match a medicine query", text.contains("Pinky Optics"));
        assertFalse("Spectacles should NOT match a medicine query", text.contains("spectacles"));
    }

    @Test
    public void testOpticalQuery_strictlyExcludesMedicine() {
        // Query specifically for spectacles / glasses / optical
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "Find my spectacles purchase", mockExpenses, "en");
        String text = res.getText();

        assertNotNull(text);
        assertTrue("Pinky Optics should be included", text.contains("Pinky Optics"));
        assertTrue("Sum should be 2,500", text.contains("2,500"));

        // Apollo and MedPlus MUST NOT be included
        assertFalse("Apollo Pharmacy should NOT match an optical query", text.contains("Apollo Pharmacy"));
        assertFalse("MedPlus should NOT match an optical query", text.contains("MedPlus"));
    }

    @Test
    public void testMultilingualResponse_tamil() {
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "மருந்து செலவு எவ்வளவு?", mockExpenses, "ta");
        String text = res.getText();

        assertNotNull(text);
        assertTrue("Should contain Tamil text", text.contains("மருந்து") || text.contains("செலவுகள்"));
        assertTrue("Should include Apollo Pharmacy", text.contains("Apollo Pharmacy"));
        assertTrue("Should include MedPlus", text.contains("MedPlus"));
        assertFalse("Pinky Optics should not be included", text.contains("Pinky Optics"));
    }

    @Test
    public void testMultilingualResponse_tanglish() {
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "Medicine ku evlo aachu?", mockExpenses, "tanglish");
        String text = res.getText();

        assertNotNull(text);
        assertTrue("Should contain Tanglish text", text.contains("Medicine") || text.contains("transactions irukku"));
        assertTrue("Should include Apollo Pharmacy", text.contains("Apollo Pharmacy"));
        assertTrue("Should include MedPlus", text.contains("MedPlus"));
        assertFalse("Pinky Optics should not be included", text.contains("Pinky Optics"));
    }

    @Test
    public void testConversationalFollowUp() {
        List<Map<String, String>> history = new ArrayList<>();
        history.add(Map.of("role", "user", "text", "How much did I spend on food?"));
        history.add(Map.of("role", "model", "text", "Found 1 transaction for Food totaling ₹380"));

        // Follow up question
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "And on travel?", mockExpenses, "en", history);
        String text = res.getText();

        assertNotNull(text);
        assertTrue("Should detect travel follow-up", text.contains("Uber"));
        assertTrue("Should show ₹180", text.contains("180"));
    }

    @Test
    public void testGreeting_friendlyShorekeeperIntro() {
        ExpenseAiEngine.AiResponse en = ExpenseAiEngine.process("hello", mockExpenses, "en");
        assertTrue(en.getText().contains("Shorekeeper"));

        ExpenseAiEngine.AiResponse ta = ExpenseAiEngine.process("வணக்கம்", mockExpenses, "ta");
        assertTrue(ta.getText().contains("ஷோர்கீப்பர்") || ta.getText().contains("Shorekeeper"));

        ExpenseAiEngine.AiResponse tanglish = ExpenseAiEngine.process("vanakkam", mockExpenses, "tanglish");
        assertTrue(tanglish.getText().contains("Shorekeeper"));
    }

    @Test
    public void testZeroMatchingExpenses_doesNotHallucinate() {
        ExpenseAiEngine.AiResponse res = ExpenseAiEngine.process(
                "How much did I spend on gym?", mockExpenses, "en");
        String text = res.getText();

        assertNotNull(text);
        assertTrue("Should report 0 transactions", text.contains("0 transactions") || text.contains("₹0"));
    }
}
