package com.smartexpense.android.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class SmsParserServiceTest {

    @Test
    public void testHdfcDebitSms() {
        String sms = "Rs.450.00 debited from HDFC Bank A/c ending 1234 on 09-10-24 to Swiggy. Avl bal: Rs.12,450.00.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "VM-HDFCBK");
        assertNotNull(tx);
        assertEquals(450.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertEquals("HDFC Bank", tx.bankName);
        assertTrue(tx.merchantName.toLowerCase().contains("swiggy"));
        assertNotNull(tx.clientTransactionId);
    }

    @Test
    public void testSbiUpiDebitSms() {
        String sms = "Dear SBI User, your A/c ending 5678 is debited by Rs 1,200.00 on 09-OCT-24 by transfer to ZOMATO UPI Ref 4284920492.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "VK-SBIINB");
        assertNotNull(tx);
        assertEquals(1200.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertEquals("SBI", tx.bankName);
        assertEquals("UPI", tx.paymentMethod);
        assertTrue(tx.merchantName.toLowerCase().contains("zomato"));
        assertNotNull(tx.clientTransactionId);
    }

    @Test
    public void testIciciCreditSms() {
        String sms = "Your Acct XX1234 has been credited with Rs 50,000.00 on 09-10-24 by SALARY. Avl Bal Rs 75,000.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "AX-ICICIB");
        assertNotNull(tx);
        assertEquals(50000.0, tx.amount, 0.001);
        assertEquals("CREDIT", tx.transactionType);
        assertEquals("ICICI Bank", tx.bankName);
    }

    @Test
    public void testAxisCardSpendSms() {
        String sms = "INR 799.00 spent on Axis Bank Card ending 4321 at AMAZON on 09-10-2024. Available limit: INR 45,000.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "AD-AXISBK");
        assertNotNull(tx);
        assertEquals(799.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertEquals("Axis Bank", tx.bankName);
        assertEquals("CARD", tx.paymentMethod);
        assertTrue(tx.merchantName.toLowerCase().contains("amazon"));
    }

    @Test
    public void testOtpIgnored() {
        String sms = "Your OTP for transaction of Rs 500 at Flipkart is 492019. Do not share with anyone.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "VK-HDFCBK");
        assertNull("OTPs should not be parsed as transactions", tx);
    }

    @Test
    public void testDeduplicationFingerprintConsistency() {
        String sms = "Rs 250 paid to Uber via UPI ref 123456789 on 09-10-2024.";
        SmsParserService.ParsedTransaction tx1 = SmsParserService.parse(sms, "HDFCBK", 1728460000000L);
        SmsParserService.ParsedTransaction tx2 = SmsParserService.parse(sms, "HDFCBK", 1728460000000L);
        assertNotNull(tx1);
        assertNotNull(tx2);
        assertEquals("Fingerprint must match for deduplication", tx1.clientTransactionId, tx2.clientTransactionId);
    }

    @Test
    public void testPhonePeUpiSms() {
        String sms = "Paid Rs.120 to Chai Point using PhonePe on 20 Oct 2026. Txn ID: T261020182910.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "PHONEPE");
        assertNotNull(tx);
        assertEquals(120.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertEquals("UPI", tx.paymentMethod);
        assertTrue(tx.merchantName.toLowerCase().contains("chai point"));
    }

    @Test
    public void testPaytmPaymentsBankSms() {
        String sms = "Sent Rs. 85.00 from Paytm Payments Bank A/C ending 1234 to Reliance Fresh on 20-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "PYTM");
        assertNotNull(tx);
        assertEquals(85.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertEquals("Paytm Payments Bank", tx.bankName);
        assertTrue(tx.merchantName.toLowerCase().contains("reliance fresh"));
    }

    @Test
    public void testPromotionalOfferIgnored() {
        String sms = "Congratulations! You have pre-approved loan of Rs 5,00,000 at 10.5%. Apply now at bank.com/loan.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "AXISBK");
        assertNull("Promotional pre-approved loan alerts must be ignored", tx);
    }

    @Test
    public void testVpaMerchantSms() {
        String sms = "Rs 350.00 debited from A/c XX4321 on 20-10-26 transfer to swiggy@icici Ref 987654.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "ICICIB");
        assertNotNull(tx);
        assertEquals(350.0, tx.amount, 0.001);
        assertEquals("DEBIT", tx.transactionType);
        assertTrue(tx.merchantName.toLowerCase().contains("swiggy"));
        assertEquals("Food", tx.category);
    }

    @Test
    public void testPinkyOpticsCategorizedAsMedical() {
        String sms = "Dear SBI User, your A/c ending 1234 is debited by Rs 850.00 on 24-OCT-26 by transfer to PINKY OPTICS UPI Ref 928301.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "VK-SBIINB");
        assertNotNull(tx);
        assertEquals(850.0, tx.amount, 0.001);
        assertTrue(tx.merchantName.toLowerCase().contains("pinky optics"));
        assertEquals("Medical", tx.category);
        assertFalse("Pinky Optics is an optical business, not P2P", tx.isProbablyP2P);
    }

    @Test
    public void testPersonalNameSellerWithItemContext() {
        String sms = "Paid Rs 240.00 to Ramesh for vegetables via PhonePe on 24-10-26. Txn ID: T261024.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "PHONEPE");
        assertNotNull(tx);
        assertEquals(240.0, tx.amount, 0.001);
        assertEquals("Grocery", tx.category);
        assertFalse("Vendor using personal name selling vegetables should be categorized as Grocery, not P2P", tx.isProbablyP2P);
    }

    @Test
    public void testPersonalNameP2PTransfer() {
        String sms = "Sent Rs 500.00 to Anand Kumar on 24-10-26 via UPI Ref 778899.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "VK-HDFCBK");
        assertNotNull(tx);
        assertEquals(500.0, tx.amount, 0.001);
        assertTrue("Pure personal name transfer without merchant cues should be detected as P2P", tx.isProbablyP2P);
        assertEquals("Transfers", tx.category);
        assertTrue("Ambiguous personal transfer should recommend user confirmation", tx.needsUserConfirmation);
    }

    @Test
    public void testSpectaclesEyewearKeyword() {
        String sms = "INR 1,499.00 spent on Axis Bank Card ending 4321 towards Spectacles at Lenskart on 24-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "AD-AXISBK");
        assertNotNull(tx);
        assertEquals(1499.0, tx.amount, 0.001);
        assertEquals("Medical", tx.category);
    }

    @Test
    public void testTangedcoElectricityBill() {
        String sms = "Dear Consumer, Rs 1,640.00 debited from A/c XX4321 for TANGEDCO electricity bill on 24-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "TNEB-TANGEDCO");
        assertNotNull(tx);
        assertEquals(1640.0, tx.amount, 0.001);
        assertEquals("Bills", tx.category);
        assertFalse(tx.isProbablyP2P);
    }

    @Test
    public void testCumtaMetroTravel() {
        String sms = "Rs 45.00 debited from A/c XX1234 to CUMTA CMRL Chennai Metro via UPI on 25-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "CUMTA-TRANSIT");
        assertNotNull(tx);
        assertEquals(45.0, tx.amount, 0.001);
        assertEquals("Travel", tx.category);
        assertFalse(tx.isProbablyP2P);
    }

    @Test
    public void testMtcBusTravel() {
        String sms = "Paid Rs 25.00 to MTC BUS CHENNAI via UPI Ref 445566 on 25-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "UPI-PAY");
        assertNotNull(tx);
        assertEquals(25.0, tx.amount, 0.001);
        assertEquals("Travel", tx.category);
        assertFalse(tx.isProbablyP2P);
    }

    @Test
    public void testAavinMilkGrocery() {
        String sms = "Rs 180.00 debited from A/c ending 9876 to AAVIN MILK BOOTH Ref 332211 on 25-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "HDFCBK");
        assertNotNull(tx);
        assertEquals(180.0, tx.amount, 0.001);
        assertEquals("Grocery", tx.category);
        assertFalse(tx.isProbablyP2P);
    }

    @Test
    public void testCmwssbMetroWater() {
        String sms = "Dear Consumer, Rs 350.00 paid towards CMWSSB MetroWater quarterly charges on 25-10-26.";
        SmsParserService.ParsedTransaction tx = SmsParserService.parse(sms, "CMWSSB");
        assertNotNull(tx);
        assertEquals(350.0, tx.amount, 0.001);
        assertEquals("Bills", tx.category);
        assertFalse(tx.isProbablyP2P);
    }
}
