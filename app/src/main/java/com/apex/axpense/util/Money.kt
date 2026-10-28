package com.apex.axpense.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val INDIA: Locale = Locale.forLanguageTag("en-IN")

/**
 * Formats money for display, e.g. "₹1,20,000.50". Uses Indian digit grouping.
 * Falls back to "CODE 12.50" for currency codes Android doesn't know.
 */
fun formatMoney(amount: Double, currencyCode: String? = "INR"): String {
    val code = currencyCode ?: "INR"
    return try {
        val format = NumberFormat.getCurrencyInstance(INDIA)
        format.currency = Currency.getInstance(code)
        format.format(amount)
    } catch (e: IllegalArgumentException) {
        "$code ${NumberFormat.getNumberInstance(INDIA).format(amount)}"
    }
}

private val AMOUNT_INPUT = Regex("""\d{0,9}(\.\d{0,2})?""")

/** True while the user is typing a valid partial amount: digits and at most two decimals. */
fun isAllowedAmountInput(text: String): Boolean = AMOUNT_INPUT.matches(text)

/** The typed amount, or null if it is empty, not a number, or not above zero. */
fun parseAmountInput(text: String): Double? = text.toDoubleOrNull()?.takeIf { it > 0 }
