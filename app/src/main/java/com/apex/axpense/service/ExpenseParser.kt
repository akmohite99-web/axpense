package com.apex.axpense.service

import java.util.regex.Pattern

object ExpenseParser {
    // Basic regex to match currency amounts like "Rs. 500", "AED 1200", "$45.99", "debited by 500.00"
    private val amountRegex = Pattern.compile("(?i)(?:rs\\.?|inr|usd|eur|gbp|aed|sar|qar|omr|bhd|kwd|jod|egp|dirham|riyal|dinar|\\$|€|£|₹)\\s?([\\d,]+\\.?\\d{0,2})|debited(?:\\s+by)?\\s?([\\d,]+\\.?\\d{0,2})|spent(?:\\s+)?([\\d,]+\\.?\\d{0,2})")

    fun parseAmount(text: String): Double? {
        val lowerText = text.lowercase()
        val hasCurrency = lowerText.contains("rs") || 
                          lowerText.contains("rupees") || 
                          lowerText.contains("inr") || 
                          lowerText.contains("₹") || 
                          lowerText.contains("$") || 
                          lowerText.contains("usd") ||
                          lowerText.contains("€") ||
                          lowerText.contains("eur") ||
                          lowerText.contains("£") ||
                          lowerText.contains("gbp") ||
                          lowerText.contains("aed") ||
                          lowerText.contains("sar") ||
                          lowerText.contains("qar") ||
                          lowerText.contains("omr") ||
                          lowerText.contains("bhd") ||
                          lowerText.contains("kwd") ||
                          lowerText.contains("jod") ||
                          lowerText.contains("egp") ||
                          lowerText.contains("dirham") ||
                          lowerText.contains("riyal") ||
                          lowerText.contains("dinar")

        if (!hasCurrency) {
            return null
        }

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
