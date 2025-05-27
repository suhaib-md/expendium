// ui/screen/SettingsScreen.kt
package com.example.expendium.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
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
import com.example.expendium.ui.navigation.navigateToAccountList
import com.example.expendium.services.TransactionNotificationListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }

    // SMS Permission states
    var systemSmsPermissionGranted by remember {
        mutableStateOf(hasSmsPermissions(context))
    }
    var userPrefSmsParsingEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("isSmsParsingEnabled", true))
    }
    var showSmsPermissionDialog by remember { mutableStateOf(false) }

    // Notification Access states
    var notificationAccessGranted by remember {
        mutableStateOf(TransactionNotificationListener.isNotificationListenerEnabled(context))
    }
    var userPrefNotificationParsingEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("isNotificationParsingEnabled", true))
    }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

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
            showSmsPermissionDialog = true
        }
    }

    val isSmsParsingEffectivelyEnabled = systemSmsPermissionGranted && userPrefSmsParsingEnabled
    val isNotificationParsingEffectivelyEnabled = notificationAccessGranted && userPrefNotificationParsingEnabled

    // Refresh notification access status when screen becomes visible
    LaunchedEffect(Unit) {
        notificationAccessGranted = TransactionNotificationListener.isNotificationListenerEnabled(context)
    }

    // SMS Permission Dialog
    if (showSmsPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showSmsPermissionDialog = false },
            title = { Text("SMS Permission Required") },
            text = { Text("To automatically detect transactions from SMS, please grant SMS permissions. If you've denied them, you can grant them from the app settings.") },
            confirmButton = {
                TextButton(onClick = { showSmsPermissionDialog = false }) { Text("OK") }
            }
        )
    }

    // Notification Access Dialog
    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text("Notification Access Required") },
            text = { Text("To read RCS messages and app notifications for transaction detection, please enable notification access in the system settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        TransactionNotificationListener.openNotificationListenerSettings(context)
                        showNotificationPermissionDialog = false
                    }
                ) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) { Text("Cancel") }
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transaction Detection Section
            Text(
                "Transaction Detection",
                style = MaterialTheme.typography.titleMedium
            )

            // SMS Transaction Parsing
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SMS Parsing")
                            Text(
                                "Detect transactions from SMS messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                "SMS parsing is currently disabled. Toggle the switch to enable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Notification/RCS Parsing
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Parsing")
                            Text(
                                "Detect transactions from RCS messages and app notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isNotificationParsingEffectivelyEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (!notificationAccessGranted) {
                                        showNotificationPermissionDialog = true
                                    } else {
                                        userPrefNotificationParsingEnabled = true
                                        sharedPrefs.edit().putBoolean("isNotificationParsingEnabled", true).apply()
                                    }
                                } else {
                                    userPrefNotificationParsingEnabled = false
                                    sharedPrefs.edit().putBoolean("isNotificationParsingEnabled", false).apply()
                                }
                            }
                        )
                    }
                    when {
                        !notificationAccessGranted -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Grant notification access to read RCS messages and app notifications for transaction detection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        TransactionNotificationListener.openNotificationListenerSettings(context)
                                    },
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Grant Access", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        isNotificationParsingEffectivelyEnabled -> {
                            Text(
                                "✓ Notification parsing is active. RCS messages and app notifications will be monitored for transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            Text(
                                "Notification parsing is currently disabled. Toggle the switch to enable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // How it works section
            if (isSmsParsingEffectivelyEnabled || isNotificationParsingEffectivelyEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How Transaction Detection Works:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        val features = buildList {
                            if (isSmsParsingEffectivelyEnabled) {
                                add("• SMS messages are monitored for transaction keywords")
                            }
                            if (isNotificationParsingEffectivelyEnabled) {
                                add("• RCS messages through messaging apps are detected")
                                add("• Banking app notifications are captured")
                                add("• Payment app notifications (PhonePe, GPay, Paytm, etc.) are processed")
                            }
                            add("• Transaction details are automatically extracted and saved")
                            add("• Check Logcat for 'SmsReceiver', 'TransactionNotificationListener', and 'SmsProcessingWorker' messages during testing")
                        }

                        Text(
                            features.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Divider
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Account Management Section
            Text(
                "Account Management",
                style = MaterialTheme.typography.titleMedium
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigateToAccountList() },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
        }
    }
}

// Helper function remains the same
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