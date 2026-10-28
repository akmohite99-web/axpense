package com.apex.axpense.service

import kotlin.math.max

/** A debit found in an SMS / notification. [currency] is an ISO-style code, or null if the text had none. */
data class ParsedTxn(
    val amount: Double,
    val currency: String?,
    val merchant: String?
)

/**
 * Finds money that was *spent* in a bank/UPI/card message.
 *
 * It must see a debit word (debited, spent, paid, ...). Credits, balance alerts, OTPs,
 * failed payments and upcoming-payment reminders return null.
 */
object ExpenseParser {
    private const val NUM = """\d+(?:,\d+)*(?:\.\d+)?"""
    private const val CUR_WORDS =
        "rs|inr|usd|eur|gbp|aed|sar|qar|omr|bhd|kwd|jod|egp|rupees?|dirhams?|riyals?|dinars?"

    private val IC = RegexOption.IGNORE_CASE

    // Text that is never an expense, even if it mentions an amount.
    private val NOT_EXPENSE = Regex(
        """\b(?:otp|one[- ]time password|verification code|passcode|failed|declined|unsuccessful|insufficient|""" +
            """will be debited|to be debited|is due|overdue|due (?:on|by|date)|reminder|requested|requesting|""" +
            """total (?:amount )?spent|spent so far)\b""", IC
    )
    private val STRONG_DEBIT = Regex("""\b(?:debited|spent|withdrawn|purchased?|charged|paid|sent|deducted)\b""", IC)
    private val WEAK_DEBIT = Regex("""\b(?:payment|transaction|txn|trxn)\b""", IC)
    private val CREDIT = Regex("""\b(?:credited|received|refund(?:ed)?|deposited|cashback|reversed|reversal)\b""", IC)

    // "Rs. 500", "INR500", "₹45.99", "$ 12", "AED 120.50"
    private val AFTER_CURRENCY = Regex(
        """((?<![A-Za-z])(?:$CUR_WORDS)(?![A-Za-z])\.?|₹|\$|€|£)\s*[:\-]?\s*($NUM)""", IC
    )
    // "500 INR", "120 AED"
    private val BEFORE_CURRENCY = Regex("""(?<![\d,.])($NUM)\s*((?:$CUR_WORDS)(?![A-Za-z]))""", IC)
    // "debited by 1,000.00" (no currency written)
    private val AFTER_DEBIT_VERB = Regex(
        """\b(?:debited|spent|paid|withdrawn|sent|charged|deducted)(?:\s+(?:by|for|with|of))?\s*[:\-]?\s*($NUM)""", IC
    )

    // An amount that follows "Avl Bal", "limit", ... within the same sentence is not the spend.
    private val BALANCE_BEFORE = Regex(
        """(?:bal(?:ance)?|avl|available|limit|outstanding)\b(?:(?!\.\s)[\s\S]){0,30}$""", IC
    )

    private val MERCHANT = Regex(
        """\b(?:at|to|towards|info:?|merchant:?)\s+(?:vpa\s+)?([A-Za-z0-9][A-Za-z0-9&@._' \-]{1,38}?)""" +
            """(?=\s+(?:on|via|using|ref|from|for|with|upi|txn|if|avl|bal|a/c|ac|is|has|was|dated|date|through|thru)\b""" +
            """|[,;(\n]|\.\s|\.$|$)""", IC
    )
    private val NOT_A_MERCHANT =
        Regex("""^(?:your|the|a/c|account|ur|my|you|me|bank|card|this|pay|avoid|know|report|stop|block|unblock)\b.*""", IC)

    fun parse(raw: String): ParsedTxn? {
        val text = raw.replace(' ', ' ').trim()
        if (text.isEmpty() || NOT_EXPENSE.containsMatchIn(text)) return null

        if (!STRONG_DEBIT.containsMatchIn(text)) {
            // "Payment of INR 1200 successful" counts; "Rs 500 credited" and "Balance is Rs 5000" don't.
            if (CREDIT.containsMatchIn(text)) return null
            if (!WEAK_DEBIT.containsMatchIn(text)) return null
        }

        val (amount, currency) = findAmount(text) ?: return null
        return ParsedTxn(amount, currency, findMerchant(text))
    }

    fun parseAmount(text: String): Double? = parse(text)?.amount

    private fun findAmount(text: String): Pair<Double, String?>? {
        for (m in AFTER_CURRENCY.findAll(text)) {
            if (isBalanceContext(text, m.range.first)) continue
            val value = toAmount(m.groupValues[2]) ?: continue
            return value to currencyCode(m.groupValues[1])
        }
        for (m in BEFORE_CURRENCY.findAll(text)) {
            if (isBalanceContext(text, m.range.first)) continue
            val value = toAmount(m.groupValues[1]) ?: continue
            return value to currencyCode(m.groupValues[2])
        }
        AFTER_DEBIT_VERB.find(text)?.let { m ->
            toAmount(m.groupValues[1])?.let { return it to null }
        }
        return null
    }

    private fun isBalanceContext(text: String, start: Int): Boolean =
        BALANCE_BEFORE.containsMatchIn(text.substring(max(0, start - 40), start))

    private fun toAmount(number: String): Double? =
        number.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    private fun currencyCode(token: String): String? {
        val t = token.trim().trimEnd('.').lowercase()
        return when (t) {
            "rs", "inr", "₹", "rupee", "rupees" -> "INR"
            "$", "usd" -> "USD"
            "€", "eur" -> "EUR"
            "£", "gbp" -> "GBP"
            "dirham", "dirhams" -> "AED"
            "riyal", "riyals", "dinar", "dinars" -> null // could be several countries
            else -> t.uppercase()
        }
    }

    private fun findMerchant(text: String): String? {
        for (m in MERCHANT.findAll(text)) {
            val name = m.groupValues[1].trim().trimEnd('.', ',', '-', ' ')
            if (name.length < 2 || name.all { it.isDigit() } || NOT_A_MERCHANT.matches(name)) continue
            return name.take(30)
        }
        return null
    }
}
