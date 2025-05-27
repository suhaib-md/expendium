// services/TransactionNotificationListener.kt
package com.example.expendium.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.expendium.worker.SmsProcessingWorker
import java.util.Locale

class TransactionNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "TransactionNotificationListener"

        // Known messaging apps that handle RCS
        private val MESSAGING_APPS = setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging",      // Samsung Messages
            "com.android.mms",                   // Default Android Messages
            "com.microsoft.android.sms"          // Microsoft SMS Organizer
        )

        // Known bank notification channels/apps
        private val BANK_APPS = setOf(
            "com.phonepe.app",
            "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "in.org.npci.upiapp", // BHIM
            "com.amazon.mShop.android.shopping"
        )

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return !flat.isNullOrEmpty() && flat.contains(packageName)
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (sbn == null) return

        try {
            val packageName = sbn.packageName
            val notification = sbn.notification

            Log.d(TAG, "Notification received from: $packageName")

            // Check if SMS parsing is enabled
            val sharedPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val smsParsingEnabled = sharedPrefs.getBoolean("isSmsParsingEnabled", true)

            if (!smsParsingEnabled) {
                Log.d(TAG, "SMS parsing disabled, ignoring notification")
                return
            }

            // Extract notification content
            val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

            // Use the most complete text available
            val messageBody = when {
                bigText.isNotBlank() -> bigText
                text.isNotBlank() -> text
                else -> title
            }

            Log.d(TAG, "Notification content:")
            Log.d(TAG, "  Title: $title")
            Log.d(TAG, "  Text: $text")
            Log.d(TAG, "  BigText: $bigText")
            Log.d(TAG, "  Final message: $messageBody")

            // Determine sender
            val sender = determineSender(packageName, title, notification)

            Log.d(TAG, "Determined sender: $sender")

            // Check if this looks like a transaction notification
            if (isTransactionNotification(messageBody, sender, packageName)) {
                Log.i(TAG, "🏦 TRANSACTION NOTIFICATION DETECTED!")
                Log.i(TAG, "App: $packageName")
                Log.i(TAG, "Sender: $sender")
                Log.i(TAG, "Message: $messageBody")

                // Enqueue work to process the transaction
                val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
                    .setInputData(
                        workDataOf(
                            SmsProcessingWorker.KEY_SENDER to sender,
                            SmsProcessingWorker.KEY_BODY to messageBody,
                            SmsProcessingWorker.KEY_TIMESTAMP to sbn.postTime
                        )
                    )
                    .addTag("notification_processing_work")
                    .build()

                WorkManager.getInstance(this).enqueue(workRequest)
                Log.d(TAG, "SmsProcessingWorker enqueued for notification from: $sender")
            } else {
                Log.d(TAG, "Not a transaction notification, ignoring")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    private fun determineSender(packageName: String, title: String, notification: Notification): String {
        // Try to extract sender from different sources

        // 1. Check if it's from a known bank app
        if (BANK_APPS.contains(packageName)) {
            return when (packageName) {
                "com.phonepe.app" -> "PHONEPE"
                "net.one97.paytm" -> "PAYTM"
                "com.google.android.apps.nbu.paisa.user" -> "GPAY"
                "in.org.npci.upiapp" -> "BHIM"
                "com.amazon.mShop.android.shopping" -> "AMAZONPAY"
                else -> packageName.substringAfterLast(".")
            }
        }

        // 2. For messaging apps, try to extract from title
        if (MESSAGING_APPS.contains(packageName)) {
            // Title often contains sender name in messaging apps
            val cleanTitle = title.trim()
            if (cleanTitle.isNotBlank() && !cleanTitle.equals("Messages", ignoreCase = true)) {
                return cleanTitle
            }
        }

        // 3. Try to extract from notification extras
        val extras = notification.extras
        val conversationTitle = extras?.getCharSequence("android.conversationTitle")?.toString()
        if (!conversationTitle.isNullOrBlank()) {
            return conversationTitle
        }

        // 4. Check for sender info in various extras
        val senderPerson = extras?.getString("android.messagingStyleUser")
        if (!senderPerson.isNullOrBlank()) {
            return senderPerson
        }

        // 5. Fallback to app name or package name
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun isTransactionNotification(messageBody: String, sender: String, packageName: String): Boolean {
        val messageBodyLower = messageBody.lowercase(Locale.ROOT)
        val senderLower = sender.lowercase(Locale.ROOT)

        // Enhanced transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "transaction", "payment", "purchase",
            "withdrawn", "deposited", "balance", "amount", "rs.", "rs ", "inr",
            "upi", "card", "atm", "transfer", "spent", "received", "paid",
            "bank", "account", "debit", "credit", "rupees", "₹", "txn",
            "refund", "cashback", "bill payment", "emi", "autopay",
            "money", "fund", "wallet", "successful", "failed", "declined"
        )

        // Bank/financial app identifiers
        val bankIdentifiers = listOf(
            "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara",
            "union", "idbi", "paytm", "phonepe", "gpay", "bhim", "amazonpay",
            "slice", "uni", "cred", "bank", "bk", "financial", "fintech"
        )

        // Check for transaction keywords
        val hasTransactionKeyword = transactionKeywords.any { keyword ->
            messageBodyLower.contains(keyword)
        }

        // Check if sender looks like a bank/financial service
        val isFromFinancialService = bankIdentifiers.any { identifier ->
            senderLower.contains(identifier)
        } || BANK_APPS.contains(packageName)

        // Check for monetary indicators
        val hasMonetaryIndicator = listOf("rs", "inr", "₹", "amount", "balance", "\\d+\\.\\d{2}".toRegex()).any {
            when (it) {
                is String -> messageBodyLower.contains(it)
                is Regex -> it.containsMatchIn(messageBodyLower)
                else -> false
            }
        }

        // More sophisticated logic
        val isLikelyTransaction = when {
            // Strong indicators - definitely a transaction
            hasTransactionKeyword && hasMonetaryIndicator -> true

            // From known financial apps with monetary indicator
            isFromFinancialService && hasMonetaryIndicator -> true

            // From messaging apps but with strong transaction keywords
            MESSAGING_APPS.contains(packageName) && hasTransactionKeyword -> true

            // Fallback
            else -> false
        }

        Log.d(TAG, "Transaction check - Keywords: $hasTransactionKeyword, Financial: $isFromFinancialService, Monetary: $hasMonetaryIndicator -> Result: $isLikelyTransaction")

        return isLikelyTransaction
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optional: Handle notification removal if needed
    }
}