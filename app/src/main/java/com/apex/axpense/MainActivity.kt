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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.apex.axpense.ui.ExpenseViewModel
import com.apex.axpense.ui.screens.ExpenseEntryScreen
import com.apex.axpense.ui.screens.HomeScreen
import com.apex.axpense.theme.AxpenseTheme

import com.apex.axpense.ui.screens.LoginScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Update user state initially
        viewModel.updateCurrentUser()

        setContent {
            AxpenseTheme {
                val navController = rememberNavController()
                
                // Handle intent extras from notification
                var initialAmount by remember { mutableStateOf<Double?>(null) }
                var initialDesc by remember { mutableStateOf<String?>(null) }
                var startDestination by remember { mutableStateOf("home") }

                LaunchedEffect(intent) {
                    if (intent?.hasExtra("EXTRA_AMOUNT") == true) {
                        initialAmount = intent.getDoubleExtra("EXTRA_AMOUNT", 0.0)
                        initialDesc = intent.getStringExtra("EXTRA_DESC")
                        startDestination = "add_expense"
                        intent.removeExtra("EXTRA_AMOUNT") // Prevent reopening on recompose
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val user by viewModel.user.collectAsState()

                    if (user == null) {
                        LoginScreen(onLoginSuccess = { viewModel.updateCurrentUser() })
                    } else if (!isNotificationListenerEnabled()) {
                        NotificationAccessPrompt {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    } else {
                        NavHost(navController = navController, startDestination = startDestination) {
                            composable("home") {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onAddExpense = { navController.navigate("add_expense") }
                                )
                            }
                            composable("add_expense") {
                                ExpenseEntryScreen(
                                    viewModel = viewModel,
                                    initialAmount = initialAmount,
                                    initialDesc = initialDesc,
                                    onNavigateBack = {
                                        initialAmount = null
                                        initialDesc = null
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageNames.contains(packageName)
    }
}

@Composable
fun NotificationAccessPrompt(onGrantAccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Notification Access Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To automatically detect expenses from SMS and emails, Axpense needs access to your notifications.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGrantAccess) {
            Text("Grant Access")
        }
    }
}
