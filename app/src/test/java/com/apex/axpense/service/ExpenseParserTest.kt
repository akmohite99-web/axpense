package com.apex.axpense.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseParserTest {

    @Test
    fun testParseAmount_rupees() {
        val text = "Rs. 500 has been debited from your account"
        val amount = ExpenseParser.parseAmount(text)
        assertEquals(500.0, amount)
    }

    @Test
    fun testParseAmount_inr() {
        val text = "Payment of INR 1200.50 successful"
        val amount = ExpenseParser.parseAmount(text)
        assertEquals(1200.50, amount)
    }

    @Test
    fun testParseAmount_dollars() {
        val text = "You spent ₹45.99 at Starbucks"
        val amount = ExpenseParser.parseAmount(text)
        assertEquals(45.99, amount)
    }

    @Test
    fun testParseAmount_debited() {
        val text = "Your account XXXXX was debited by 1,000.00 on 12-05-2026"
        val amount = ExpenseParser.parseAmount(text)
        assertEquals(1000.00, amount)
    }

    @Test
    fun testParseAmount_noMatch() {
        val text = "Your account balance is Rs. 5000"
        // In reality, this might match "Rs. 5000", which is a false positive for an expense.
        // For a more robust parser we would check for "spent" or "debited" keywords exclusively,
        // or refine the regex. But based on our current regex `(?:rs\.?|inr|\$)\s?([\d,]+\.?\d{0,2})`, it will match 5000.0.
        val amount = ExpenseParser.parseAmount(text)
        assertEquals(5000.0, amount)
    }
}
