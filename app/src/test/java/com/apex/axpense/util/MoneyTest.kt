package com.apex.axpense.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun formats_rupees_with_two_decimals() {
        assertEquals("₹45.50", formatMoney(45.5))
        assertEquals("₹500.00", formatMoney(500.0))
    }

    @Test
    fun unknown_currency_code_falls_back_to_plain_text() {
        assertEquals("XYZ 12.5", formatMoney(12.5, "XYZ"))
    }

    @Test
    fun amount_input_allows_digits_and_two_decimals() {
        assertTrue(isAllowedAmountInput(""))
        assertTrue(isAllowedAmountInput("12"))
        assertTrue(isAllowedAmountInput("12."))
        assertTrue(isAllowedAmountInput("12.5"))
        assertTrue(isAllowedAmountInput("12.50"))
    }

    @Test
    fun amount_input_rejects_letters_signs_and_extra_decimals() {
        assertFalse(isAllowedAmountInput("12.505"))
        assertFalse(isAllowedAmountInput("1,000"))
        assertFalse(isAllowedAmountInput("-5"))
        assertFalse(isAllowedAmountInput("abc"))
        assertFalse(isAllowedAmountInput("1.2.3"))
    }

    @Test
    fun parse_amount_requires_a_positive_number() {
        assertEquals(12.5, parseAmountInput("12.5")!!, 0.0001)
        assertEquals(5.0, parseAmountInput("5.")!!, 0.0001)
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("."))
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("0.00"))
    }
}
