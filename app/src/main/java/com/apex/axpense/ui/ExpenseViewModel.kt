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

    /** Expenses found in SMS that are waiting for the user to confirm or dismiss them. */
    val pendingExpenses: StateFlow<List<Expense>> = repository.pendingExpenses
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Saves a confirmed expense. Pass [remoteId] to confirm an existing pending one
     * (or to give an SMS-based expense its stable ID); otherwise a new ID is created.
     */
    fun addExpense(
        amount: Double,
        category: String,
        subCategory: String?,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
        sourceApp: String = "manual",
        remoteId: String? = null
    ) {
        viewModelScope.launch {
            val base = Expense(
                amount = amount,
                category = category,
                subCategory = subCategory,
                description = description,
                timestamp = timestamp,
                sourceApp = sourceApp,
                userId = auth.currentUser?.uid ?: ""
            )
            repository.addExpense(if (remoteId != null) base.copy(remoteId = remoteId) else base)
        }
    }

    fun deleteExpense(remoteId: String) {
        viewModelScope.launch { repository.deleteExpense(remoteId) }
    }

    /** Puts back an expense that was just deleted (the Undo action). */
    fun restoreExpense(expense: Expense) {
        viewModelScope.launch { repository.addExpense(expense) }
    }

    fun dismissPending(remoteId: String) {
        viewModelScope.launch { repository.dismissPending(remoteId) }
    }

    suspend fun isKnownSms(body: String, amount: Double, timestamp: Long): Boolean =
        repository.isKnownSms(body, amount, timestamp)

    suspend fun guessCategory(merchant: String?) = repository.guessCategory(merchant)

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
