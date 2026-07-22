package com.example.axpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.axpense.data.AxpenseDatabase
import com.example.axpense.data.Expense
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AxpenseDatabase.getDatabase(application).expenseDao()

    val expenses: StateFlow<List<Expense>> = dao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalSpent: StateFlow<Double?> = dao.getTotalSpent()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    fun addExpense(amount: Double, category: String, description: String) {
        viewModelScope.launch {
            dao.insert(
                Expense(
                    amount = amount,
                    category = category,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    sourceApp = "manual"
                )
            )
        }
    }
}
