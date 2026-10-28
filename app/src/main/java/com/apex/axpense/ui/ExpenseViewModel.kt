package com.apex.axpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.axpense.data.Category
import com.apex.axpense.data.DataRepository
import com.apex.axpense.data.Expense
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DataRepository(application)
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalSpent: StateFlow<Double?> = repository.totalSpent
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun addExpense(
        amount: Double,
        category: String,
        subCategory: String?,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
        sourceApp: String = "manual"
    ) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    category = category,
                    subCategory = subCategory,
                    description = description,
                    timestamp = timestamp,
                    sourceApp = sourceApp,
                    userId = auth.currentUser?.uid ?: ""
                )
            )
        }
    }

    fun addCategory(name: String, parentCategory: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addCategory(
                Category(
                    name = trimmed,
                    parentCategory = parentCategory,
                    userId = auth.currentUser?.uid ?: ""
                )
            )
        }
    }

    suspend fun countExpensesIn(name: String, parentCategory: String?): Int =
        repository.countExpensesIn(name, parentCategory)

    fun deleteCategory(name: String, parentCategory: String?) {
        viewModelScope.launch {
            repository.deleteCategory(name, parentCategory)
        }
    }

    /**
     * Backs up, then wipes local data so the next account doesn't see it.
     * If some expenses couldn't be uploaded, calls [onUnsynced] with the count instead,
     * unless [force] is true.
     */
    fun signOut(force: Boolean = false, onUnsynced: (Int) -> Unit = {}) {
        viewModelScope.launch {
            if (!force) {
                withTimeoutOrNull(15_000) { runCatching { repository.backupAllData() } }
                val pending = repository.unsyncedCount()
                if (pending > 0) {
                    onUnsynced(pending)
                    return@launch
                }
            }
            repository.clearLocalData()
            auth.signOut()
            _user.value = null
        }
    }

    fun updateCurrentUser() {
        _user.value = auth.currentUser
        if (auth.currentUser != null) {
            viewModelScope.launch {
                repository.restoreFromCloudIfNeeded()
            }
        }
    }
}
