package com.apex.axpense.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.apex.axpense.data.Category
import com.apex.axpense.ui.ExpenseViewModel
import com.apex.axpense.util.isAllowedAmountInput
import com.apex.axpense.util.parseAmountInput
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Values carried over from a detected SMS, a pending expense, or an expense being edited. */
data class ExpensePrefill(
    val amount: Double,
    val description: String?,
    val timestamp: Long?,
    val sourceApp: String?,
    /** Set when this confirms a pending expense or edits a saved one, so it isn't added twice. */
    val remoteId: String? = null,
    val category: String? = null,
    val subCategory: String? = null,
    /** True when editing an expense that is already saved (shows Delete, "Save Changes"). */
    val editing: Boolean = false
)

private fun formatAmount(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** The date picker works in UTC midnight; this gives the picked day with [existing]'s time of day. */
private fun withPickedDate(existing: Long, pickedUtcMidnight: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMidnight }
    return Calendar.getInstance().apply {
        timeInMillis = existing
        set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** UTC midnight of [millis]'s local calendar day, which is what the date picker expects. */
private fun toPickerUtc(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private data class PendingCategoryDelete(val name: String, val parent: String?, val expenseCount: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    viewModel: ExpenseViewModel,
    prefill: ExpensePrefill? = null,
    onNavigateBack: () -> Unit
) {
    val allCategories by viewModel.categories.collectAsState()
    val scope = rememberCoroutineScope()

    val mainCategories = remember(allCategories) {
        val cats = allCategories.filter { it.parentCategory == null }.map { it.name }
        cats.ifEmpty { Category.DEFAULTS }
    }

    var amount by remember { mutableStateOf(prefill?.amount?.let(::formatAmount) ?: "") }
    var description by remember { mutableStateOf(prefill?.description ?: "") }
    var timestamp by remember { mutableStateOf(prefill?.timestamp ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // stops a double tap from adding two expenses
    var showDeleteExpenseDialog by remember { mutableStateOf(false) }
    val editing = prefill?.editing == true && prefill.remoteId != null
    val amountValue = parseAmountInput(amount)
    var pendingDelete by remember { mutableStateOf<PendingCategoryDelete?>(null) }

    fun askToDelete(name: String, parent: String?) {
        scope.launch {
            pendingDelete = PendingCategoryDelete(name, parent, viewModel.countExpensesIn(name, parent))
        }
    }
    var category by remember { mutableStateOf(prefill?.category ?: mainCategories.firstOrNull() ?: "Food") }

    val subCategories = remember(allCategories, category) {
        allCategories.filter { it.parentCategory == category }.map { it.name }
    }

    var subCategory by remember { mutableStateOf(prefill?.subCategory) }

    // Reset subCategory when the user changes the main category (not on first show,
    // which would throw away the sub-category suggested for a detected expense).
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(category) {
        if (firstComposition) firstComposition = false else subCategory = null
    }

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedSubCategory by remember { mutableStateOf(false) }
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { if (isAllowedAmountInput(it)) amount = it },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                isError = showErrors && amountValue == null,
                supportingText = {
                    if (showErrors && amountValue == null) Text("Enter an amount greater than 0")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Date: tapping anywhere on the field opens the picker.
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = remember(timestamp) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Pick date") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
            }

            // Main Category Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = !expandedCategory }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    mainCategories.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                category = selectionOption
                                expandedCategory = false
                            },
                            trailingIcon = if (selectionOption == Category.FALLBACK) null else {
                                {
                                    IconButton(onClick = {
                                        expandedCategory = false
                                        askToDelete(selectionOption, null)
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Category")
                                    }
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("➕ Add New Category...") },
                        onClick = {
                            expandedCategory = false
                            showAddCategoryDialog = true
                        }
                    )
                }
            }
            
            // Sub Category Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedSubCategory,
                onExpandedChange = { expandedSubCategory = !expandedSubCategory }
            ) {
                OutlinedTextField(
                    value = subCategory ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sub-Category (Optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSubCategory) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedSubCategory,
                    onDismissRequest = { expandedSubCategory = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            subCategory = null
                            expandedSubCategory = false
                        }
                    )
                    subCategories.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                subCategory = selectionOption
                                expandedSubCategory = false
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    expandedSubCategory = false
                                    askToDelete(selectionOption, category)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Sub-Category")
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("➕ Add New Sub-Category...") },
                        onClick = {
                            expandedSubCategory = false
                            showAddSubCategoryDialog = true
                        }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    showErrors = true
                    if (amountValue != null && !saved) {
                        saved = true
                        viewModel.addExpense(
                            amount = amountValue,
                            category = category,
                            subCategory = subCategory,
                            description = description.trim(),
                            timestamp = timestamp,
                            sourceApp = prefill?.sourceApp ?: "manual",
                            remoteId = prefill?.remoteId
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (editing) "Save Changes" else "Save Expense")
            }

            if (editing) {
                OutlinedButton(
                    onClick = { showDeleteExpenseDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Delete Expense")
                }
            }
        }

        if (showDatePicker) {
            val todayUtc = toPickerUtc(System.currentTimeMillis())
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = toPickerUtc(timestamp),
                selectableDates = object : SelectableDates {
                    // No future dates.
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { timestamp = withPickedDate(timestamp, it) }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = pickerState)
            }
        }

        if (showDeleteExpenseDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteExpenseDialog = false },
                title = { Text("Delete this expense?") },
                text = { Text("It will also be removed from your backup. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteExpenseDialog = false
                        prefill?.remoteId?.let { viewModel.deleteExpense(it) }
                        onNavigateBack()
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showDeleteExpenseDialog = false }) { Text("Cancel") } }
            )
        }

        // Delete Category confirmation
        pendingDelete?.let { pd ->
            val isSub = pd.parent != null
            val effect = when {
                pd.expenseCount == 0 -> "No expenses use it."
                isSub -> "${pd.expenseCount} expense(s) will lose this sub-category."
                else -> "${pd.expenseCount} expense(s) will move to \"${Category.FALLBACK}\"" +
                    " and its sub-categories will be removed."
            }
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete \"${pd.name}\"?") },
                text = { Text(effect) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteCategory(pd.name, pd.parent)
                        if (isSub) {
                            if (subCategory == pd.name) subCategory = null
                        } else if (category == pd.name) {
                            category = Category.FALLBACK
                        }
                        pendingDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        }

        // Add Category Dialog
        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false; newCategoryName = "" },
                title = { Text("Add New Category") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName, null)
                            category = newCategoryName.trim()
                        }
                        showAddCategoryDialog = false
                        newCategoryName = ""
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false; newCategoryName = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Add Sub-Category Dialog
        if (showAddSubCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddSubCategoryDialog = false; newCategoryName = "" },
                title = { Text("Add Sub-Category under '$category'") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Sub-Category Name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName, category)
                            subCategory = newCategoryName.trim()
                        }
                        showAddSubCategoryDialog = false
                        newCategoryName = ""
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSubCategoryDialog = false; newCategoryName = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
