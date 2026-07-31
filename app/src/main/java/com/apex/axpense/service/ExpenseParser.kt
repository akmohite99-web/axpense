package com.apex.axpense.service

import java.util.regex.Pattern

object ExpenseParser {
    // Basic regex to match currency amounts like "Rs. 500", "INR 1200", "$45.99", "debited by 500.00"
    private val amountRegex = Pattern.compile("(?i)(?:rs\\.?|inr|\\$)\\s?([\\d,]+\\.?\\d{0,2})|debited(?:\\s+by)?\\s?([\\d,]+\\.?\\d{0,2})|spent(?:\\s+)?([\\d,]+\\.?\\d{0,2})")

    fun parseAmount(text: String): Double? {
        val matcher = amountRegex.matcher(text)
        if (matcher.find()) {
            for (i in 1..matcher.groupCount()) {
                val group = matcher.group(i)
                if (group != null) {
                    val cleanAmount = group.replace(",", "")
                    return cleanAmount.toDoubleOrNull()
                }
            }
        }
        return null
    }
}
