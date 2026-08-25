package com.apex.axpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.axpense.data.AxpenseDatabase
import com.apex.axpense.data.DataRepository
import com.apex.axpense.data.Expense
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AxpenseDatabase.getDatabase(application)
    private val repository = DataRepository(db.expenseDao(), db.categoryDao())
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user.asStateFlow()

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val categories: StateFlow<List<com.apex.axpense.data.Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalSpent: StateFlow<Double?> = repository.totalSpent
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun addExpense(amount: Double, category: String, subCategory: String?, description: String) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    category = category,
                    subCategory = subCategory,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    sourceApp = "manual",
                    userId = auth.currentUser?.uid ?: ""
                )
            )
        }
    }
    
    fun addCategory(name: String, parentCategory: String? = null) {
        viewModelScope.launch {
            repository.addCategory(
                com.apex.axpense.data.Category(
                    name = name,
                    parentCategory = parentCategory,
                    userId = auth.currentUser?.uid ?: ""
                )
            )
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            repository.deleteCategory(name)
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }

    fun updateCurrentUser() {
        _user.value = auth.currentUser
        if (auth.currentUser != null) {
            viewModelScope.launch {
                repository.syncFromCloud()
            }
        }
    }
}
