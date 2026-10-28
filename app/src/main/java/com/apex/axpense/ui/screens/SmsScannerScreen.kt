package com.apex.axpense.ui.screens

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.apex.axpense.data.Category
import com.apex.axpense.data.DataRepository
import com.apex.axpense.data.Expense
import com.apex.axpense.service.ExpenseParser
import com.apex.axpense.ui.ExpenseViewModel
import com.apex.axpense.util.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** An expense found in the SMS inbox that hasn't been saved yet. */
data class ScannedExpense(
    val key: String, // Expense.smsKey(body): also the saved expense's ID, so it can't be added twice
    val amount: Double,
    val description: String,
    val timestamp: Long,
    val category: String,
    val subCategory: String?
)

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

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

    var scanned by remember { mutableStateOf<List<ScannedExpense>>(emptyList()) }
    var alreadyTracked by remember { mutableStateOf(0) }
    var hasScanned by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val categories by viewModel.categories.collectAsState()
    val mainCategories = remember(categories) {
        categories.filter { it.parentCategory == null }.map { it.name }.ifEmpty { Category.DEFAULTS }
    }
    val coroutineScope = rememberCoroutineScope()

    fun save(item: ScannedExpense) {
        viewModel.addExpense(
            amount = item.amount,
            category = item.category,
            subCategory = item.subCategory,
            description = item.description,
            timestamp = item.timestamp, // the SMS date, not today
            sourceApp = "sms_scan",
            remoteId = item.key
        )
    }

    fun update(key: String, change: (ScannedExpense) -> ScannedExpense) {
        scanned = scanned.map { if (it.key == key) change(it) else it }
    }

    fun scan() {
        isScanning = true
        coroutineScope.launch {
            val (found, tracked) = withContext(Dispatchers.IO) {
                val (start, end) = monthRange(selectedYear, selectedMonth)
                val seen = HashSet<String>()
                val results = mutableListOf<ScannedExpense>()
                var trackedCount = 0
                for ((body, date) in readInbox(context, start, end)) {
                    val txn = ExpenseParser.parse(body) ?: continue
                    val key = Expense.smsKey(body)
                    if (!seen.add(key)) continue // identical text received twice
                    if (viewModel.isKnownSms(body, txn.amount, date)) {
                        trackedCount++
                        continue
                    }
                    val guess = viewModel.guessCategory(txn.merchant)
                    results += ScannedExpense(
                        key = key,
                        amount = txn.amount,
                        description = txn.merchant ?: DataRepository.snippet(body),
                        timestamp = date,
                        category = guess?.category ?: Category.FALLBACK,
                        subCategory = guess?.subCategory
                    )
                }
                results to trackedCount
            }
            scanned = found
            alreadyTracked = tracked
            hasScanned = true
            isScanning = false
        }
    }

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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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

                ExposedDropdownMenuBox(
                    expanded = monthDropdownExpanded,
                    onExpandedChange = { monthDropdownExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = MONTHS[selectedMonth],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Month") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = monthDropdownExpanded,
                        onDismissRequest = { monthDropdownExpanded = false }
                    ) {
                        MONTHS.forEachIndexed { index, month ->
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
                onClick = ::scan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning
            ) {
                Text(if (isScanning) "Scanning..." else "Scan SMS")
            }

            if (hasScanned) {
                Spacer(modifier = Modifier.height(12.dp))
                val summary = when {
                    scanned.isEmpty() && alreadyTracked == 0 -> "No expenses found for this month."
                    scanned.isEmpty() -> "Nothing new. $alreadyTracked already in your expenses."
                    alreadyTracked == 0 -> "${scanned.size} new expense(s) found."
                    else -> "${scanned.size} new, $alreadyTracked already in your expenses."
                }
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (scanned.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val count = scanned.size
                            scanned.forEach(::save)
                            scanned = emptyList()
                            Toast.makeText(context, "Saved $count expenses", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save all ${scanned.size}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scanned, key = { it.key }) { item ->
                    ScannedExpenseCard(
                        item = item,
                        mainCategories = mainCategories,
                        subCategoriesOf = { parent ->
                            categories.filter { it.parentCategory == parent }.map { it.name }
                        },
                        onCategoryChange = { category -> update(item.key) { it.copy(category = category, subCategory = null) } },
                        onSubCategoryChange = { sub -> update(item.key) { it.copy(subCategory = sub) } },
                        onSkip = { scanned = scanned.filter { it.key != item.key } },
                        onSave = {
                            save(item)
                            scanned = scanned.filter { it.key != item.key }
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedExpenseCard(
    item: ScannedExpense,
    mainCategories: List<String>,
    subCategoriesOf: (String) -> List<String>,
    onCategoryChange: (String) -> Unit,
    onSubCategoryChange: (String?) -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit
) {
    val date = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val subCategories = remember(item.category) { subCategoriesOf(item.category) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.description, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(date, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    formatMoney(item.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerChip(
                    label = "Category",
                    value = item.category,
                    options = mainCategories,
                    onSelect = { it?.let(onCategoryChange) }
                )
                if (subCategories.isNotEmpty()) {
                    PickerChip(
                        label = "Sub",
                        value = item.subCategory ?: "None",
                        options = subCategories,
                        noneLabel = "None",
                        onSelect = onSubCategoryChange
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onSkip) { Text("Skip") }
                Button(onClick = onSave) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PickerChip(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String?) -> Unit,
    noneLabel: String? = null
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text("$label: $value") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); open = false })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

/** First and last millisecond of the given month. */
private fun monthRange(year: Int, month: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(year, month, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = Calendar.getInstance().apply {
        set(year, month, 1, 23, 59, 59)
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.MILLISECOND, 999)
    }
    return start.timeInMillis to end.timeInMillis
}

/** SMS inbox messages (body, date) between [start] and [end], newest first. */
private fun readInbox(context: Context, start: Long, end: Long): List<Pair<String, Long>> {
    val messages = mutableListOf<Pair<String, Long>>()
    try {
        context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("body", "date"),
            "date >= ? AND date <= ?",
            arrayOf(start.toString(), end.toString()),
            "date DESC"
        )?.use { cursor ->
            val bodyIndex = cursor.getColumnIndexOrThrow("body")
            val dateIndex = cursor.getColumnIndexOrThrow("date")
            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex) ?: continue
                messages += body to cursor.getLong(dateIndex)
            }
        }
    } catch (e: SecurityException) {
        // Permission was revoked while the screen was open.
    }
    return messages
}
