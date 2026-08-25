package com.apex.axpense.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class DataRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val totalSpent: Flow<Double?> = expenseDao.getTotalSpent()
    
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun addExpense(expense: Expense) {
        val currentUser = auth.currentUser
        val expenseWithUser = if (currentUser != null) {
            expense.copy(userId = currentUser.uid)
        } else {
            expense
        }
        
        // Save locally
        expenseDao.insert(expenseWithUser)
        
        // Sync to cloud if user is logged in
        if (currentUser != null) {
            try {
                firestore.collection("expenses")
                    .document()
                    .set(expenseWithUser)
                    .await()
            } catch (e: Exception) {
                // If cloud sync fails, it will just stay local for now
            }
        }
    }

    suspend fun addCategory(category: Category) {
        val currentUser = auth.currentUser
        val categoryWithUser = if (currentUser != null) {
            category.copy(userId = currentUser.uid)
        } else {
            category
        }
        
        // Save locally
        categoryDao.insert(categoryWithUser)
        
        // Sync to cloud if user is logged in
        if (currentUser != null) {
            try {
                firestore.collection("categories")
                    .document()
                    .set(categoryWithUser)
                    .await()
            } catch (e: Exception) {
                // Ignore sync errors
            }
        }
    }

    suspend fun deleteCategory(name: String) {
        // Reassign expenses before deleting
        expenseDao.updateCategoryToOthers(name)
        expenseDao.updateSubCategoryToOthers(name)

        categoryDao.deleteCategoryByName(name)
        
        val currentUser = auth.currentUser ?: return
        try {
            // Update cloud expenses
            val expensesSnapshotCat = firestore.collection("expenses")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("category", name)
                .get()
                .await()
            for (doc in expensesSnapshotCat.documents) {
                doc.reference.update(
                    "category", "Others",
                    "subCategory", null
                )
            }
            
            val expensesSnapshotSubCat = firestore.collection("expenses")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("subCategory", name)
                .get()
                .await()
            for (doc in expensesSnapshotSubCat.documents) {
                doc.reference.update("subCategory", "Others")
            }

            // Delete cloud categories
            val snapshot = firestore.collection("categories")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("name", name)
                .get()
                .await()
            for (doc in snapshot.documents) doc.reference.delete()
            
            val subSnapshot = firestore.collection("categories")
                .whereEqualTo("userId", currentUser.uid)
                .whereEqualTo("parentCategory", name)
                .get()
                .await()
            for (doc in subSnapshot.documents) doc.reference.delete()
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    suspend fun backupAllData() {
        val currentUser = auth.currentUser ?: return
        try {
            // Fetch all local data
            val localExpenses = expenseDao.getAllExpensesOnce()
            val localCategories = categoryDao.getAllCategoriesOnce()

            // Use batch write for atomicity
            val batch = firestore.batch()

            // Clear existing collections for the user (optional – here we overwrite existing docs)
            // Upload categories
            localCategories.forEach { category ->
                val docRef = firestore.collection("categories").document()
                batch.set(docRef, category.copy(userId = currentUser.uid))
            }

            // Upload expenses
            localExpenses.forEach { expense ->
                val docRef = firestore.collection("expenses").document()
                batch.set(docRef, expense.copy(userId = currentUser.uid))
            }

            // Commit batch
            batch.commit().await()
        } catch (e: Exception) {
            // Log or rethrow as needed
            e.printStackTrace()
        }
    }

    // Sync data from Firestore to local DB
    suspend fun syncFromCloud() {
        val currentUser = auth.currentUser ?: return
        try {
            // Sync expenses
            val expenseSnapshot = firestore.collection("expenses")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()
            
            val cloudExpenses = expenseSnapshot.toObjects<Expense>()
            cloudExpenses.forEach { expenseDao.insert(it) }

            // Sync categories
            val categorySnapshot = firestore.collection("categories")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()
            
            val cloudCategories = categorySnapshot.toObjects<Category>()
            cloudCategories.forEach { categoryDao.insert(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
