package com.smartexpense.android.util;

import com.smartexpense.android.model.Expense;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying multilingual voice parsing and AI query engine
 * for English, Tamil, and Tanglish phrases.
 */
public class VoiceExpenseParserTest {

    @Test
    public void testEnglishVoiceParsing() {
        VoiceExpenseParser.ParsedVoiceExpense p1 = VoiceExpenseParser.parse("Spent 450 rupees on Swiggy");
        assertNotNull(p1);
        assertEquals(450.0, p1.getAmount(), 0.001);
        assertEquals("Swiggy", p1.getMerchantName());
        assertEquals("Food", p1.getCategory());
        assertTrue(p1.isAddCommand());

        VoiceExpenseParser.ParsedVoiceExpense p2 = VoiceExpenseParser.parse("Paid 1200 at DMart for groceries");
        assertEquals(1200.0, p2.getAmount(), 0.001);
        assertEquals("DMart", p2.getMerchantName());
        assertEquals("Grocery", p2.getCategory());
    }

    @Test
    public void testTanglishVoiceParsing() {
        VoiceExpenseParser.ParsedVoiceExpense p1 = VoiceExpenseParser.parse("Tea ku 20 roobai");
        assertNotNull(p1);
        assertEquals(20.0, p1.getAmount(), 0.001);
        assertEquals("Food", p1.getCategory());

        VoiceExpenseParser.ParsedVoiceExpense p2 = VoiceExpenseParser.parse("Petrol ku 500 pottan");
        assertEquals(500.0, p2.getAmount(), 0.001);
        assertEquals("Fuel", p2.getCategory());

        VoiceExpenseParser.ParsedVoiceExpense p3 = VoiceExpenseParser.parse("Swiggy la 350 add pannu");
        assertEquals(350.0, p3.getAmount(), 0.001);
        assertEquals("Swiggy", p3.getMerchantName());
        assertEquals("Food", p3.getCategory());
        assertTrue(p3.isAddCommand());
    }

    @Test
    public void testTamilScriptVoiceParsing() {
        VoiceExpenseParser.ParsedVoiceExpense p1 = VoiceExpenseParser.parse("மளிகை 1200 ரூபாய்");
        assertNotNull(p1);
        assertEquals(1200.0, p1.getAmount(), 0.001);
        assertEquals("Grocery", p1.getCategory());

        VoiceExpenseParser.ParsedVoiceExpense p2 = VoiceExpenseParser.parse("டீ 20 ரூபாய் சேர்த்துக் கொள்");
        assertEquals(20.0, p2.getAmount(), 0.001);
        assertEquals("Food", p2.getCategory());
        assertTrue(p2.isAddCommand());
    }

    @Test
    public void testExpenseAiEngineMultilingual() {
        List<Expense> expenses = new ArrayList<>();
        Expense e1 = new Expense();
        e1.setId("e1");
        e1.setAmount(450.0);
        e1.setMerchantName("Swiggy");
        e1.setCategory("Food");
        e1.setTransactionType("EXPENSE");
        e1.setTransactionDate("2026-10-15T13:00:00");
        expenses.add(e1);

        Expense e2 = new Expense();
        e2.setId("e2");
        e2.setAmount(1500.0);
        e2.setMerchantName("DMart");
        e2.setCategory("Grocery");
        e2.setTransactionType("EXPENSE");
        e2.setTransactionDate("2026-10-16T18:00:00");
        expenses.add(e2);

        // English query
        ExpenseAiEngine.AiResponse rEng = ExpenseAiEngine.process("Where did I spend the most?", expenses, "en");
        assertNotNull(rEng);
        assertTrue(rEng.getText().contains("DMart") || rEng.getText().contains("Grocery"));

        // Tamil query
        ExpenseAiEngine.AiResponse rTam = ExpenseAiEngine.process("எதில் அதிகம் செலவு செய்தேன்?", expenses, "ta");
        assertNotNull(rTam);
        assertTrue(rTam.getText().contains("முதன்மை"));

        // Tanglish add command
        ExpenseAiEngine.AiResponse rAdd = ExpenseAiEngine.process("Tea ku 20 add pannu", expenses, "tanglish");
        assertNotNull(rAdd);
        assertTrue(rAdd.isAddExpenseAction());
        assertNotNull(rAdd.getExpenseToAdd());
        assertEquals(20.0, rAdd.getExpenseToAdd().getAmount(), 0.001);
    }

    @Test
    public void testAiEngineMedicineAndOpticsQueries() {
        List<Expense> expenses = new ArrayList<>();

        Expense med = new Expense();
        med.setId("m1");
        med.setAmount(620.0);
        med.setMerchantName("Apollo Pharmacy");
        med.setDescription("Tablets and syrup");
        med.setCategory("Medical");
        med.setTransactionDate("2026-10-18T10:00:00");
        expenses.add(med);

        Expense optics = new Expense();
        optics.setId("o1");
        optics.setAmount(1850.0);
        optics.setMerchantName("Pinky Optics");
        optics.setDescription("Spectacles and frame");
        optics.setCategory("Medical");
        optics.setTransactionDate("2026-10-19T16:00:00");
        expenses.add(optics);

        // 1. "How much did I spend on medicine?"
        ExpenseAiEngine.AiResponse rMed = ExpenseAiEngine.process("How much did I spend on medicine?", expenses, "en");
        assertNotNull(rMed);
        assertTrue("Must include medical/pharmacy expenses", rMed.getText().contains("Apollo Pharmacy") || rMed.getText().contains("620") || rMed.getText().contains("2,470"));

        // 2. "Show my pharmacy expenses"
        ExpenseAiEngine.AiResponse rPharm = ExpenseAiEngine.process("Show my pharmacy expenses", expenses, "en");
        assertNotNull(rPharm);
        assertTrue("Must match pharmacy transactions", rPharm.getText().contains("Apollo Pharmacy") || rPharm.getText().contains("620"));

        // 3. "Find my spectacles purchase"
        ExpenseAiEngine.AiResponse rSpec = ExpenseAiEngine.process("Find my spectacles purchase", expenses, "en");
        assertNotNull(rSpec);
        assertTrue("Must find Pinky Optics spectacles purchase", rSpec.getText().contains("Pinky Optics") || rSpec.getText().contains("1,850"));

        // 4. Grounded zero-match test: item not in records must NEVER invent answers
        ExpenseAiEngine.AiResponse rNone = ExpenseAiEngine.process("How much did I spend on gym?", expenses, "en");
        assertNotNull(rNone);
        assertTrue("Must state 0 found without inventing transactions", rNone.getText().contains("0") || rNone.getText().contains("No recorded"));

        // 5. Tamil query: "மருந்து செலவு எவ்வளவு?"
        ExpenseAiEngine.AiResponse rTamMed = ExpenseAiEngine.process("மருந்து செலவு எவ்வளவு?", expenses, "ta");
        assertNotNull(rTamMed);
        assertTrue("Must answer in Tamil with real transaction amount", rTamMed.getText().contains("செலவு") || rTamMed.getText().contains("Apollo"));

        // 6. Tanglish query: "Spectacles vaanginen-ah?"
        ExpenseAiEngine.AiResponse rTang = ExpenseAiEngine.process("Spectacles vaanginen-ah?", expenses, "tanglish");
        assertNotNull(rTang);
        assertTrue("Must match spectacles in Tanglish", rTang.getText().contains("Pinky Optics") || rTang.getText().contains("1,850"));
    }

    @Test
    public void testSmartCategorizerDirect() {
        // Known optical shop
        SmartCategorizer.CategoryResult r1 = SmartCategorizer.categorize("PINKY OPTICS", null, "Paid for eyewear");
        assertEquals("Medical", r1.category);
        org.junit.Assert.assertFalse("Optics store is not P2P", r1.isProbablyP2P);

        // Seller using personal name with item context
        SmartCategorizer.CategoryResult r2 = SmartCategorizer.categorize("Ramesh Kumar", null, "Payment for groceries");
        assertEquals("Grocery", r2.category);
        org.junit.Assert.assertFalse(r2.isProbablyP2P);

        // Ambiguous personal transfer
        SmartCategorizer.CategoryResult r3 = SmartCategorizer.categorize("Suresh Raina", null, "UPI transfer");
        assertEquals("Transfers", r3.category);
        org.junit.Assert.assertTrue(r3.isProbablyP2P);
        org.junit.Assert.assertTrue(r3.needsUserConfirmation);
    }

    @Test
    public void testRegionalEntitiesVoiceAndCategorizer() {
        // TANGEDCO / TNEB EB bill
        VoiceExpenseParser.ParsedVoiceExpense pEb = VoiceExpenseParser.parse("TANGEDCO ku 1640 add pannu");
        assertNotNull(pEb);
        assertEquals(1640.0, pEb.getAmount(), 0.001);
        assertEquals("Bills", pEb.getCategory());
        assertEquals("TANGEDCO", pEb.getMerchantName());

        // CUMTA / CMRL transit
        VoiceExpenseParser.ParsedVoiceExpense pTransit = VoiceExpenseParser.parse("CUMTA metro 45 rupees");
        assertNotNull(pTransit);
        assertEquals(45.0, pTransit.getAmount(), 0.001);
        assertEquals("Travel", pTransit.getCategory());
        assertEquals("CUMTA", pTransit.getMerchantName());

        // Aavin milk & dairy
        VoiceExpenseParser.ParsedVoiceExpense pMilk = VoiceExpenseParser.parse("Aavin paal ku 60 roobai");
        assertNotNull(pMilk);
        assertEquals(60.0, pMilk.getAmount(), 0.001);
        assertEquals("Grocery", pMilk.getCategory());
        assertEquals("Aavin", pMilk.getMerchantName());

        // SmartCategorizer checks
        SmartCategorizer.CategoryResult rEb = SmartCategorizer.categorize("TANGEDCO", null, "EB current bill");
        assertEquals("Bills", rEb.category);
        assertFalse(rEb.isProbablyP2P);

        SmartCategorizer.CategoryResult rCumta = SmartCategorizer.categorize("CUMTA", null, "Chennai metro travel");
        assertEquals("Travel", rCumta.category);
        assertFalse(rCumta.isProbablyP2P);

        SmartCategorizer.CategoryResult rMtc = SmartCategorizer.categorize("MTC BUS", null, "City bus pass");
        assertEquals("Travel", rMtc.category);
        assertFalse(rMtc.isProbablyP2P);

        SmartCategorizer.CategoryResult rAavin = SmartCategorizer.categorize("AAVIN", null, "Milk packet");
        assertEquals("Grocery", rAavin.category);
        assertFalse(rAavin.isProbablyP2P);

        SmartCategorizer.CategoryResult rWater = SmartCategorizer.categorize("CMWSSB", null, "Metro water bill");
        assertEquals("Bills", rWater.category);
        assertFalse(rWater.isProbablyP2P);
    }
}
