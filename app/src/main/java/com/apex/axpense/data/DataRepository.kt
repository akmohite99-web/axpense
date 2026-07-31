package com.apex.axpense.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class DataRepository(private val dao: ExpenseDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val allExpenses: Flow<List<Expense>> = dao.getAllExpenses()
    val totalSpent: Flow<Double?> = dao.getTotalSpent()

    suspend fun addExpense(expense: Expense) {
        val currentUser = auth.currentUser
        val expenseWithUser = if (currentUser != null) {
            expense.copy(userId = currentUser.uid)
        } else {
            expense
        }
        
        // Save locally
        dao.insert(expenseWithUser)
        
        // Sync to cloud if user is logged in
        if (currentUser != null) {
            try {
                firestore.collection("expenses")
                    .document()
                    .set(expenseWithUser)
                    .await()
            } catch (e: Exception) {
                // If cloud sync fails, it will just stay local for now
                // In a production app, you'd implement a sync worker
            }
        }
    }

    suspend fun syncFromCloud() {
        val currentUser = auth.currentUser ?: return
        try {
            val snapshot = firestore.collection("expenses")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()
            
            val cloudExpenses = snapshot.toObjects<Expense>()
            // Update local DB with cloud data
            cloudExpenses.forEach { dao.insert(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
