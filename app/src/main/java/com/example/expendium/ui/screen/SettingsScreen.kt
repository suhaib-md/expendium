// ui/screen/SettingsScreen.kt
package com.example.expendium.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.expendium.ui.navigation.navigateToAccountList
import com.example.expendium.services.TransactionNotificationListener
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Refresh notification access status when screen becomes visible and periodically
    LaunchedEffect(lifecycleOwner.lifecycle.currentState) {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            // Check immediately when screen resumes
            notificationAccessGranted = TransactionNotificationListener.isNotificationListenerEnabled(context)

            // Also start a periodic check every 2 seconds while the screen is active
            while (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                delay(2000)
                val currentStatus = TransactionNotificationListener.isNotificationListenerEnabled(context)
                if (currentStatus != notificationAccessGranted) {
                    notificationAccessGranted = currentStatus
                }
            }
        }
    }

    // SMS Permission Dialog
    if (showSmsPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showSmsPermissionDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "SMS Permission Required",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    "To automatically detect transactions from SMS messages, please grant SMS permissions. You can enable them from your device's app settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = { showSmsPermissionDialog = false }) {
                    Text("Got it")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Notification Access Dialog
    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    "Notification Access Required",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    "To read RCS messages and app notifications for transaction detection, please enable notification access in the system settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        TransactionNotificationListener.openNotificationListenerSettings(context)
                        showNotificationPermissionDialog = false
                    }
                ) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Transaction Detection Section
            SettingsSection(
                title = "Transaction Detection",
                subtitle = "Automatically capture transactions from messages and notifications"
            ) {
                // SMS Transaction Parsing
                ModernSettingsCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIcon(
                                    icon = Icons.Filled.Sms,
                                    backgroundColor = Color(0xFF2196F3).copy(alpha = 0.1f),
                                    iconColor = Color(0xFF2196F3)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "SMS Parsing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Detect transactions from SMS messages",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

                        StatusIndicator(
                            when {
                                !systemSmsPermissionGranted -> StatusType.ERROR to "Grant SMS permissions to automatically detect transactions from bank messages."
                                isSmsParsingEffectivelyEnabled -> StatusType.SUCCESS to "SMS parsing is active. New transaction messages will be detected automatically."
                                else -> StatusType.INFO to "SMS parsing is currently disabled. Toggle the switch to enable."
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notification/RCS Parsing
                ModernSettingsCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIcon(
                                    icon = Icons.Filled.Notifications,
                                    backgroundColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                                    iconColor = Color(0xFF9C27B0)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Notification Parsing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Detect transactions from RCS messages and app notifications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                StatusIndicator(
                                    StatusType.ERROR to "Grant notification access to read RCS messages and app notifications for transaction detection."
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        TransactionNotificationListener.openNotificationListenerSettings(context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grant Notification Access")
                                }
                            }
                            isNotificationParsingEffectivelyEnabled -> {
                                StatusIndicator(
                                    StatusType.SUCCESS to "Notification parsing is active. RCS messages and app notifications will be monitored for transactions."
                                )
                            }
                            else -> {
                                StatusIndicator(
                                    StatusType.INFO to "Notification parsing is currently disabled. Toggle the switch to enable."
                                )
                            }
                        }
                    }
                }
            }

            // How it works section
            AnimatedVisibility(
                visible = isSmsParsingEffectivelyEnabled || isNotificationParsingEffectivelyEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ModernSettingsCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "How Transaction Detection Works:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

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
                            add("• Check Logcat for debugging messages during testing")
                        }

                        Text(
                            features.joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                        )
                    }
                }
            }

            // Account Management Section
            SettingsSection(
                title = "Account Management",
                subtitle = "Manage your bank accounts and cards"
            ) {
                ModernSettingsCard(
                    modifier = Modifier.clickable { navController.navigateToAccountList() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(
                                icon = Icons.Filled.AccountBalanceWallet,
                                backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                iconColor = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Manage Accounts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Add, edit, or remove your financial accounts",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go to accounts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
fun ModernSettingsCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun SettingsIcon(
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

enum class StatusType {
    SUCCESS, ERROR, INFO
}

@Composable
fun StatusIndicator(statusPair: Pair<StatusType, String>) {
    val (type, message) = statusPair

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when (type) {
                StatusType.SUCCESS -> Icons.Filled.CheckCircle
                StatusType.ERROR -> Icons.Filled.Error
                StatusType.INFO -> Icons.Filled.Info
            },
            contentDescription = null,
            tint = when (type) {
                StatusType.SUCCESS -> Color(0xFF00C853)
                StatusType.ERROR -> Color(0xFFFF1744)
                StatusType.INFO -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = when (type) {
                StatusType.SUCCESS -> Color(0xFF00C853)
                StatusType.ERROR -> Color(0xFFFF1744)
                StatusType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
        )
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