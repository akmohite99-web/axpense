package com.apex.axpense.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.apex.axpense.ui.ExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    viewModel: ExpenseViewModel,
    initialAmount: Double? = null,
    initialDesc: String? = null,
    onNavigateBack: () -> Unit
) {
    val allCategories by viewModel.categories.collectAsState()
    
    val mainCategories = remember(allCategories) {
        val cats = allCategories.filter { it.parentCategory == null }.map { it.name }
        if (cats.isEmpty()) listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Other") else cats
    }
    
    var amount by remember { mutableStateOf(initialAmount?.toString() ?: "") }
    var description by remember { mutableStateOf(initialDesc ?: "") }
    var category by remember { mutableStateOf(mainCategories.firstOrNull() ?: "Food") }
    
    val subCategories = remember(allCategories, category) {
        allCategories.filter { it.parentCategory == category }.map { it.name }
    }
    
    var subCategory by remember { mutableStateOf<String?>(null) }
    
    // Reset subCategory when main category changes
    LaunchedEffect(category) {
        subCategory = null
    }

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedSubCategory by remember { mutableStateOf(false) }
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddSubCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
                            }
                        )
                    }
                    Divider()
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
                    modifier = Modifier.menuAnchor().fillMaxWidth()
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
                            }
                        )
                    }
                    Divider()
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
                    val parsedAmount = amount.toDoubleOrNull()
                    if (parsedAmount != null && parsedAmount > 0) {
                        viewModel.addExpense(parsedAmount, category, subCategory, description)
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save Expense")
            }
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
                            category = newCategoryName
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
                            subCategory = newCategoryName
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
