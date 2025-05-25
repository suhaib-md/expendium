// ui/screen/SettingsScreen.kt
package com.example.expendium.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet // Example icon for Accounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.expendium.ui.navigation.navigateToAccountList // Import the navigation helper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }

    var systemSmsPermissionGranted by remember {
        mutableStateOf(hasSmsPermissions(context))
    }
    var userPrefSmsParsingEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("isSmsParsingEnabled", true))
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val requestSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        systemSmsPermissionGranted = allGranted

        if (allGranted) {
            userPrefSmsParsingEnabled = true
            sharedPrefs.edit().putBoolean("isSmsParsingEnabled", true).apply()
        } else {
            userPrefSmsParsingEnabled = false
            sharedPrefs.edit().putBoolean("isSmsParsingEnabled", false).apply()
            showPermissionDialog = true
        }
    }

    val isSmsParsingEffectivelyEnabled = systemSmsPermissionGranted && userPrefSmsParsingEnabled

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("SMS Permission Required") },
            text = { Text("To automatically detect transactions from SMS, please grant SMS permissions. If you've denied them, you can grant them from the app settings.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SMS Transaction Parsing Section
            Text(
                "SMS Transaction Parsing",
                style = MaterialTheme.typography.titleMedium
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable SMS Parsing")
                        Switch(
                            checked = isSmsParsingEffectivelyEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (!systemSmsPermissionGranted) {
                                        requestSmsPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.RECEIVE_SMS,
                                                Manifest.permission.READ_SMS
                                            )
                                        )
                                    } else {
                                        userPrefSmsParsingEnabled = true
                                        sharedPrefs.edit().putBoolean("isSmsParsingEnabled", true).apply()
                                    }
                                } else {
                                    userPrefSmsParsingEnabled = false
                                    sharedPrefs.edit().putBoolean("isSmsParsingEnabled", false).apply()
                                }
                            }
                        )
                    }
                    when {
                        !systemSmsPermissionGranted -> {
                            Text(
                                "Grant SMS permissions to automatically detect transactions from bank messages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        isSmsParsingEffectivelyEnabled -> {
                            Text(
                                "✓ SMS parsing is active. New transaction messages will be detected automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            Text(
                                "SMS parsing is currently disabled by you. Toggle the switch to enable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (isSmsParsingEffectivelyEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How it works:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• The app monitors incoming SMS messages.\n" +
                                    "• If SMS parsing is enabled and permissions are granted, potential transaction messages are processed.\n" +
                                    "• Transaction details are extracted and saved to your records.\n" +
                                    "• Check Logcat for 'SmsReceiver' and 'SmsProcessingWorker' messages during testing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Divider or Spacer
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Account Management Section - NEW
            Text(
                "Account Management",
                style = MaterialTheme.typography.titleMedium
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigateToAccountList() }, // Navigate on click
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Optional: add some elevation
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AccountBalanceWallet,
                            contentDescription = "Manage Accounts",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Manage Accounts",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Go to accounts"
                    )
                }
            }

            // ... (any other settings options can go here)
        }
    }
}

// hasSmsPermissions function remains the same
fun hasSmsPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
}