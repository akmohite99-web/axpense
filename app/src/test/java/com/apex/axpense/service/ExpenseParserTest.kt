package com.apex.axpense.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseParserTest {

    private fun parsed(text: String): ParsedTxn {
        val txn = ExpenseParser.parse(text)
        assertNotNull("Expected an expense in: $text", txn)
        return txn!!
    }

    private fun assertAmount(expected: Double, text: String) =
        assertEquals(expected, parsed(text).amount, 0.001)

    private fun assertNotExpense(text: String) =
        assertNull("Should not be an expense: $text", ExpenseParser.parse(text))

    // ---- Debits that should be detected ----

    @Test
    fun rupees_debited() = assertAmount(500.0, "Rs. 500 has been debited from your account")

    @Test
    fun inr_payment() {
        val txn = parsed("Payment of INR 1200.50 successful")
        assertEquals(1200.50, txn.amount, 0.001)
        assertEquals("INR", txn.currency)
    }

    @Test
    fun spent_with_merchant() {
        val txn = parsed("You spent ₹45.99 at Starbucks")
        assertEquals(45.99, txn.amount, 0.001)
        assertEquals("Starbucks", txn.merchant)
    }

    @Test
    fun debited_by_without_currency() {
        val txn = parsed("Your account XXXXX was debited by 1,000.00 on 12-05-2026")
        assertEquals(1000.0, txn.amount, 0.001)
        assertNull(txn.currency)
    }

    @Test
    fun indian_number_grouping_ignores_balance() =
        assertAmount(100000.0, "Rs.1,00,000.00 debited from A/c XX1234 on 12-05-26. Avl Bal Rs.5,000.00")

    @Test
    fun upi_debit_that_mentions_credited_to_merchant() {
        val txn = parsed("Rs 500 debited from a/c XX1234 on 12-05-26 and credited to VPA swiggy@icici (UPI Ref 1234)")
        assertEquals(500.0, txn.amount, 0.001)
        assertEquals("swiggy@icici", txn.merchant)
    }

    @Test
    fun sent_to_merchant() {
        val txn = parsed("Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY On 12/05/26")
        assertEquals(500.0, txn.amount, 0.001)
        assertEquals("SWIGGY", txn.merchant)
    }

    @Test
    fun dirham_card_spend() {
        val txn = parsed("AED 120.50 spent at Carrefour on card 1234")
        assertEquals(120.5, txn.amount, 0.001)
        assertEquals("AED", txn.currency)
        assertEquals("Carrefour", txn.merchant)
    }

    @Test
    fun dollar_charge() {
        val txn = parsed("$45.99 charged by Netflix")
        assertEquals(45.99, txn.amount, 0.001)
        assertEquals("USD", txn.currency)
    }

    @Test
    fun skips_balance_that_comes_first() =
        assertAmount(500.0, "Avl Bal Rs 5000. Rs 500 debited at ATM")

    @Test
    fun uses_debit_not_trailing_balance() =
        assertAmount(500.0, "Rs 500 was debited. Avl bal Rs 5000")

    // ---- Messages that must NOT be treated as expenses ----

    @Test
    fun balance_alert() = assertNotExpense("Your account balance is Rs. 5000")

    @Test
    fun credit() = assertNotExpense("Rs 5000 credited to your account")

    @Test
    fun received_money() = assertNotExpense("You have received Rs 500 from John")

    @Test
    fun otp() = assertNotExpense("Your OTP for transaction of Rs 500 is 123456")

    @Test
    fun bill_due_reminder() = assertNotExpense("Your bill of Rs 500 is due on 15 May")

    @Test
    fun failed_payment() = assertNotExpense("Payment of Rs 500 failed")

    @Test
    fun upcoming_debit() = assertNotExpense("Rs 500 will be debited on 15 May")

    @Test
    fun promo() = assertNotExpense("Get Rs 500 cashback on your next spend")

    @Test
    fun no_currency_word_in_plain_text() = assertNotExpense("Thank you for your hours of work yesterday")

    @Test
    fun empty() = assertNotExpense("")
}
