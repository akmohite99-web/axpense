package com.apex.axpense.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.axpense.data.Expense
import com.apex.axpense.ui.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onAddExpense: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    val availableYears = remember(expenses) {
        expenses.map {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.YEAR)
        }.distinct().sortedDescending()
    }

    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val filteredExpenses = remember(expenses, selectedYear, selectedMonth) {
        expenses.filter { expense ->
            val cal = Calendar.getInstance().apply { timeInMillis = expense.timestamp }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            
            val yearMatch = selectedYear == null || year == selectedYear
            val monthMatch = selectedMonth == null || month == selectedMonth
            
            yearMatch && monthMatch
        }
    }

    val filteredTotalSpent = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    val categoryTotals = remember(filteredExpenses) {
        filteredExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }
    
    val maxCategoryTotal = categoryTotals.maxOfOrNull { it.second } ?: 0.0

    val subCategoryTotals = remember(filteredExpenses, selectedCategory) {
        if (selectedCategory == null) emptyList()
        else filteredExpenses.filter { it.category == selectedCategory }
            .groupBy { it.subCategory ?: "Uncategorized" }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }
    
    val maxSubCategoryTotal = subCategoryTotals.maxOfOrNull { it.second } ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Axpense") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Filled.Add, contentDescription = "Add Expense")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Year Dropdown
                    ExposedDropdownMenuBox(
                        expanded = yearDropdownExpanded,
                        onExpandedChange = { yearDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedYear?.toString() ?: "All Time",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Year") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = yearDropdownExpanded,
                            onDismissRequest = { yearDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Time") },
                                onClick = { 
                                    selectedYear = null
                                    selectedMonth = null
                                    yearDropdownExpanded = false 
                                }
                            )
                            availableYears.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year.toString()) },
                                    onClick = {
                                        selectedYear = year
                                        yearDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Month Dropdown
                    ExposedDropdownMenuBox(
                        expanded = monthDropdownExpanded,
                        onExpandedChange = { if (selectedYear != null) monthDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (selectedYear == null) "N/A" else (selectedMonth?.let { months[it] } ?: "All Months"),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            enabled = selectedYear != null
                        )
                        ExposedDropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Months") },
                                onClick = {
                                    selectedMonth = null
                                    monthDropdownExpanded = false
                                }
                            )
                            months.forEachIndexed { index, month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
                                    onClick = {
                                        selectedMonth = index
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Total Spent Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedYear == null && selectedMonth == null) "Total Spent (All Time)" else "Total Spent",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%.2f", filteredTotalSpent)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (categoryTotals.isNotEmpty()) {
                item {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DonutChart(
                                data = categoryTotals,
                                colors = chartColors,
                                modifier = Modifier
                                    .size(200.dp)
                                    .padding(16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            ChartLegendColumn(
                                data = categoryTotals,
                                colors = chartColors,
                                selectedCategory = selectedCategory,
                                onCategorySelected = { 
                                    if (selectedCategory == it) selectedCategory = null 
                                    else selectedCategory = it 
                                }
                            )
                        }
                    }
                }

                if (selectedCategory != null && subCategoryTotals.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sub-categories for $selectedCategory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                DonutChart(
                                    data = subCategoryTotals,
                                    colors = chartColors,
                                    modifier = Modifier
                                        .size(150.dp)
                                        .padding(16.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ChartLegendColumn(
                                    data = subCategoryTotals,
                                    colors = chartColors,
                                    selectedCategory = null,
                                    onCategorySelected = {}
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No expenses tracked yet.")
                    }
                }
            } else {
                items(filteredExpenses) { expense ->
                    ExpenseItem(expense)
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                }
            }
        }
    }
}

val chartColors = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFFFFC107), // Amber
    Color(0xFFF44336), // Red
    Color(0xFF9C27B0), // Purple
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFF9800), // Orange
    Color(0xFF8BC34A), // Light Green
    Color(0xFFE91E63), // Pink
    Color(0xFF3F51B5)  // Indigo
)

@Composable
fun DonutChart(
    data: List<Pair<String, Double>>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second }
    if (total == 0.0) return

    Canvas(modifier = modifier) {
        var startAngle = -90f
        val strokeWidth = size.width * 0.15f
        for ((index, item) in data.withIndex()) {
            val sweepAngle = (item.second / total * 360f).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = size.copy(width = size.width - strokeWidth, height = size.height - strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun ChartLegendColumn(
    data: List<Pair<String, Double>>,
    colors: List<Color>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEachIndexed { index, item ->
            val isSelected = selectedCategory == item.first
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategorySelected(item.first) }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(colors[index % colors.size], shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.first,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "₹${String.format("%.2f", item.second)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ExpenseItem(expense: Expense) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(expense.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = expense.description.ifEmpty { "Manual Entry" }, fontWeight = FontWeight.Bold)
                Text(text = expense.category, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Text(text = dateString, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text(
                text = "₹${expense.amount}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
