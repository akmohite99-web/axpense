package com.apex.axpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.axpense.container
import com.apex.axpense.data.Category
import com.apex.axpense.data.DataRepository
import com.apex.axpense.data.Expense
import com.apex.axpense.util.Period
import com.apex.axpense.util.yearsToOffer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.container.repository
    private val auth = application.container.auth

    /** The signed-in user, kept up to date by Firebase (also reflects sign-out and expired accounts). */
    val user: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-off notices for the UI (snackbar), e.g. the outcome of a cloud restore. */
    val messages: SharedFlow<String> = _messages

    init {
        // Pull the user's cloud data after each sign-in (and retry on launch if it failed before).
        viewModelScope.launch {
            user.map { it?.uid }.distinctUntilChanged().filterNotNull().collect {
                report(repository.restoreFromCloudIfNeeded(), always = false)
            }
        }
    }

    private fun report(result: DataRepository.RestoreResult?, always: Boolean) {
        val text = when {
            result == null -> if (always) "Sign in to restore your data." else null
            result.error != null -> "Couldn't restore from your account (${result.error}). Check your connection and try again."
            result.added > 0 -> "Restored ${result.added} expense(s) from your account."
            !always -> null
            result.cloudExpenses == 0 -> "Your account has no expenses in the cloud backup."
            else -> "Everything in the cloud (${result.cloudExpenses}) is already on this device."
        }
        text?.let { _messages.tryEmit(it) }
    }

    /** Manual backup + restore from the Home screen; always reports what happened. */
    fun syncNow() {
        viewModelScope.launch {
            val waiting = repository.unsyncedCount()
            val failure = runCatching { repository.backupAllData() }.exceptionOrNull()
            if (failure != null) {
                val code = (failure as? FirebaseFirestoreException)?.code?.name ?: failure.javaClass.simpleName
                _messages.tryEmit("Backup failed ($code). $waiting expense(s) are not in the cloud yet.")
            } else {
                if (waiting > 0) _messages.tryEmit("Backed up $waiting expense(s) to your account.")
                report(repository.restoreFromCloudIfNeeded(force = true), always = true)
            }
        }
    }

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Expenses found in SMS that are waiting for the user to confirm or dismiss them. */
    val pendingExpenses: StateFlow<List<Expense>> = repository.pendingExpenses
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ---- Home screen: filter + numbers computed by the database ----

    private val _period = MutableStateFlow(Period.current())
    val period: StateFlow<Period> = _period.asStateFlow()

    fun setPeriod(period: Period) {
        _period.value = period
    }

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun selectCategory(name: String?) {
        _selectedCategory.value = name
    }

    private val range = _period.map { it.range() }

    val availableYears: StateFlow<List<Int>> = repository.earliestTimestamp
        .map { yearsToOffer(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), yearsToOffer(null))

    val periodExpenses: StateFlow<List<Expense>> = range
        .flatMapLatest { (start, end) -> repository.expensesBetween(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val periodTotal: StateFlow<Double> = range
        .flatMapLatest { (start, end) -> repository.totalBetween(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0.0)

    val categoryTotals: StateFlow<List<Pair<String, Double>>> = range
        .flatMapLatest { (start, end) ->
            repository.categoryTotals(start, end).map { rows -> rows.map { it.name to it.total } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val subCategoryTotals: StateFlow<List<Pair<String, Double>>> = combine(range, _selectedCategory) { r, c -> r to c }
        .flatMapLatest { (r, category) ->
            if (category == null) flowOf(emptyList())
            else repository.subCategoryTotals(category, r.first, r.second)
                .map { rows -> rows.map { it.name to it.total } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // ---- Changing data ----

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
            auth.signOut() // the auth listener above moves the app to the login screen
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
