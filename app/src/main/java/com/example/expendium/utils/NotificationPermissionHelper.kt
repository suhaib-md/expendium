// utils/NotificationPermissionHelper.kt
package com.example.expendium.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.expendium.services.TransactionNotificationListener

@Composable
fun NotificationPermissionCard() {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(false) }

    // Check permission status
    LaunchedEffect(Unit) {
        isPermissionGranted = TransactionNotificationListener.isNotificationListenerEnabled(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RCS Message Detection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isPermissionGranted) {
                Text(
                    text = "✅ Notification access enabled",
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Your app can now detect RCS and app-based transaction messages",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "⚠️ Notification access required",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "To detect RCS messages and notifications from banking apps, please enable notification access",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        TransactionNotificationListener.openNotificationListenerSettings(context)
                    }
                ) {
                    Text("Enable Notification Access")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        // Refresh permission status
                        isPermissionGranted = TransactionNotificationListener.isNotificationListenerEnabled(context)
                    }
                ) {
                    Text("Refresh Status")
                }
            }
        }
    }
}