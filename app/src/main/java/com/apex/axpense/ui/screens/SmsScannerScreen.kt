package com.apex.axpense.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.apex.axpense.service.ExpenseParser
import com.apex.axpense.ui.ExpenseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class ScannedExpense(
    val amount: Double,
    val description: String,
    val timestamp: Long,
    var category: String = "Uncategorized",
    var subCategory: String? = null,
    val id: String = UUID.randomUUID().toString()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScannerScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "SMS permission is required to scan messages.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val availableYears = (currentYear - 5..currentYear).toList().reversed()
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    var scannedExpenses by remember { mutableStateOf<List<ScannedExpense>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    
    val allExpenses by viewModel.expenses.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan SMS for Expenses") },
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
                .padding(16.dp)
        ) {
            if (!hasPermission) {
                Text("SMS permission is required to scan messages.")
                Button(onClick = { requestPermissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                    Text("Grant Permission")
                }
                return@Column
            }

            // Year and Month Selectors
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
                        value = selectedYear.toString(),
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
                    onExpandedChange = { monthDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = months[selectedMonth],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Month") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = monthDropdownExpanded,
                        onDismissRequest = { monthDropdownExpanded = false }
                    ) {
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

            Button(
                onClick = {
                    isScanning = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val calStart = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val startMillis = calStart.timeInMillis
                        
                        val calEnd = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        val endMillis = calEnd.timeInMillis

                        val foundExpenses = mutableListOf<ScannedExpense>()
                        
                        val uri = Uri.parse("content://sms/inbox")
                        val projection = arrayOf("address", "body", "date")
                        val selection = "date >= ? AND date <= ?"
                        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())

                        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, "date DESC")
                        
                        cursor?.use {
                            val bodyIndex = it.getColumnIndexOrThrow("body")
                            val dateIndex = it.getColumnIndexOrThrow("date")
                            val addressIndex = it.getColumnIndexOrThrow("address")

                            while (it.moveToNext()) {
                                val body = it.getString(bodyIndex)
                                val date = it.getLong(dateIndex)
                                val address = it.getString(addressIndex)

                                val amount = ExpenseParser.parseAmount(body)
                                if (amount != null) {
                                    var guessedCategory = "Uncategorized"
                                    var guessedSub = null as String?
                                    
                                    val similarPast = allExpenses.firstOrNull { pastExp -> 
                                        pastExp.amount == amount 
                                    }
                                    if (similarPast != null) {
                                        guessedCategory = similarPast.category
                                        guessedSub = similarPast.subCategory
                                    }
                                    
                                    val desc = body.take(50).replace("\n", " ") + "..."

                                    foundExpenses.add(ScannedExpense(amount, desc, date, guessedCategory, guessedSub))
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            scannedExpenses = foundExpenses
                            isScanning = false
                            if (foundExpenses.isEmpty()) {
                                Toast.makeText(context, "No expenses found for this month.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning
            ) {
                Text(if (isScanning) "Scanning..." else "Scan SMS")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scannedExpenses, key = { it.id }) { expense ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Amount: ₹${expense.amount}", style = MaterialTheme.typography.titleMedium)
                            Text("Desc: ${expense.description}", style = MaterialTheme.typography.bodyMedium)
                            Text("Category: ${expense.category}", style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(onClick = {
                                viewModel.addExpense(
                                    amount = expense.amount,
                                    category = expense.category,
                                    subCategory = expense.subCategory,
                                    description = expense.description
                                )
                                scannedExpenses = scannedExpenses.filter { it.id != expense.id }
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Save Transaction")
                            }
                        }
                    }
                }
            }
        }
    }
}
