package com.apex.axpense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apex.axpense.theme.AxpenseTheme
import com.apex.axpense.ui.ExpenseViewModel
import com.apex.axpense.ui.screens.ExpenseEntryScreen
import com.apex.axpense.ui.screens.ExpensePrefill
import com.apex.axpense.ui.screens.HomeScreen
import com.apex.axpense.ui.screens.LoginScreen
import com.apex.axpense.ui.screens.SmsScannerScreen

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    /** Set when the app is opened from an "expense detected" notification. */
    private var pendingPrefill by mutableStateOf<ExpensePrefill?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.updateCurrentUser()
        // On recreation (e.g. rotation) the intent is the same one we already handled.
        if (savedInstanceState == null) consumePrefill(intent)
        requestPostNotificationsIfNeeded()

        setContent {
            AxpenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val user by viewModel.user.collectAsState()
                    if (user == null) {
                        LoginScreen(onLoginSuccess = { viewModel.updateCurrentUser() })
                    } else {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumePrefill(intent)
    }

    private fun consumePrefill(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_AMOUNT) != true) return
        pendingPrefill = ExpensePrefill(
            amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0),
            description = intent.getStringExtra(EXTRA_DESC),
            timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L).takeIf { it > 0 },
            sourceApp = intent.getStringExtra(EXTRA_SOURCE),
            remoteId = intent.getStringExtra(EXTRA_REMOTE_ID),
            category = intent.getStringExtra(EXTRA_CATEGORY),
            subCategory = intent.getStringExtra(EXTRA_SUB_CATEGORY)
        )
        intent.removeExtra(EXTRA_AMOUNT)
    }

    @Composable
    private fun AppNavHost() {
        val navController = rememberNavController()
        var entryPrefill by remember { mutableStateOf<ExpensePrefill?>(null) }

        // Re-check whenever the user comes back (e.g. from the system settings screen).
        var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled()) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            listenerEnabled = isNotificationListenerEnabled()
        }

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onAddExpense = {
                        entryPrefill = null
                        navController.navigate("add_expense")
                    },
                    onScanClicked = { navController.navigate("scan_sms") },
                    onReviewPending = { pending ->
                        entryPrefill = ExpensePrefill(
                            amount = pending.amount,
                            description = pending.description,
                            timestamp = pending.timestamp,
                            sourceApp = pending.sourceApp,
                            remoteId = pending.remoteId,
                            category = pending.category,
                            subCategory = pending.subCategory
                        )
                        navController.navigate("add_expense")
                    },
                    onEditExpense = { saved ->
                        entryPrefill = ExpensePrefill(
                            amount = saved.amount,
                            description = saved.description,
                            timestamp = saved.timestamp,
                            sourceApp = saved.sourceApp,
                            remoteId = saved.remoteId,
                            category = saved.category,
                            subCategory = saved.subCategory,
                            editing = true
                        )
                        navController.navigate("add_expense")
                    },
                    notificationAccessEnabled = listenerEnabled,
                    onEnableNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
            composable("scan_sms") {
                SmsScannerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("add_expense") {
                // A new notification while this screen is open resets the form.
                key(entryPrefill) {
                    ExpenseEntryScreen(
                        viewModel = viewModel,
                        prefill = entryPrefill,
                        onNavigateBack = {
                            entryPrefill = null
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // Placed after NavHost so the graph is set before we navigate.
        val prefill = pendingPrefill
        LaunchedEffect(prefill) {
            if (prefill != null) {
                entryPrefill = prefill
                pendingPrefill = null
                navController.navigate("add_expense") { launchSingleTop = true }
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    companion object {
        const val EXTRA_AMOUNT = "EXTRA_AMOUNT"
        const val EXTRA_DESC = "EXTRA_DESC"
        const val EXTRA_TIMESTAMP = "EXTRA_TIMESTAMP"
        const val EXTRA_SOURCE = "EXTRA_SOURCE"
        const val EXTRA_REMOTE_ID = "EXTRA_REMOTE_ID"
        const val EXTRA_CATEGORY = "EXTRA_CATEGORY"
        const val EXTRA_SUB_CATEGORY = "EXTRA_SUB_CATEGORY"
    }
}
