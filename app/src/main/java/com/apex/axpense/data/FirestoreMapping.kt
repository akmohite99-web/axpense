package com.apex.axpense.data

import com.google.firebase.firestore.DocumentSnapshot

// Explicit mapping instead of toObject(): Kotlin data classes with `val`s and
// required constructor params can't be deserialized by Firestore's reflection mapper,
// which is why restore never worked before.

fun Expense.toFirestore(uid: String): Map<String, Any?> = mapOf(
    "remoteId" to remoteId,
    "amount" to amount,
    "description" to description,
    "category" to category,
    "subCategory" to subCategory,
    "timestamp" to timestamp,
    "sourceApp" to sourceApp,
    "userId" to uid
)

fun Category.toFirestore(uid: String): Map<String, Any?> = mapOf(
    "remoteId" to remoteId,
    "name" to name,
    "parentCategory" to parentCategory,
    "userId" to uid
)

/** Docs written before remoteId existed used random IDs (and were duplicated by every backup). */
fun DocumentSnapshot.isLegacyDoc(): Boolean = getString("remoteId") != id

private fun normalizeLegacyName(name: String?): String? =
    if (name == "Other") Category.FALLBACK else name

fun DocumentSnapshot.toExpense(): Expense? {
    val amount = getDouble("amount") ?: return null
    val timestamp = getLong("timestamp") ?: return null
    val legacy = isLegacyDoc()
    return Expense(
        amount = amount,
        description = getString("description") ?: "",
        category = normalizeLegacyName(getString("category")) ?: Category.FALLBACK,
        subCategory = getString("subCategory"),
        timestamp = timestamp,
        sourceApp = getString("sourceApp") ?: "cloud",
        userId = getString("userId") ?: "",
        remoteId = if (legacy) Expense.legacyKey(timestamp, amount) else id,
        // Legacy copies get re-uploaded under their stable ID before the old docs are removed.
        isSynced = !legacy
    )
}

fun DocumentSnapshot.toCategory(): Category? {
    val name = normalizeLegacyName(getString("name")) ?: return null
    val parent = normalizeLegacyName(getString("parentCategory"))
    return Category(
        name = name,
        parentCategory = parent,
        userId = getString("userId") ?: "",
        remoteId = Category.key(name, parent),
        isSynced = !isLegacyDoc()
    )
}
