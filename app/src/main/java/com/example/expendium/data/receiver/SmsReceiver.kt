// data/receiver/SmsReceiver.kt
package com.example.expendium.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.expendium.worker.SmsProcessingWorker // Import your worker
import java.util.Locale // For lowercase

// Hilt entry point for BroadcastReceivers (if you needed to inject something directly into the receiver)
// However, for starting a Hilt Worker, this isn't strictly necessary here.
// import dagger.hilt.android.AndroidEntryPoint
// @AndroidEntryPoint // Not strictly needed if only launching a worker

class SmsReceiver : BroadcastReceiver() {

    // If you needed to inject something into the Receiver itself (not common for this pattern)
    // @Inject lateinit var someDependency: SomeType

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "=== SMS RECEIVER TRIGGERED ===")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Processing SMS_RECEIVED_ACTION")

            // Check a preference if SMS parsing is enabled by the user
            val sharedPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val smsParsingEnabled = sharedPrefs.getBoolean("isSmsParsingEnabled", true) // Default to true or false based on your app's initial state

            if (!smsParsingEnabled) {
                Log.i(TAG, "SMS parsing is disabled by user settings. Skipping.")
                Log.d(TAG, "=== SMS RECEIVER FINISHED (parsing disabled) ===")
                return
            }

            handleSmsReceived(context, intent)
        } else {
            Log.d(TAG, "Received unhandled action: ${intent.action}")
        }
        Log.d(TAG, "=== SMS RECEIVER FINISHED ===")
    }

    private fun handleSmsReceived(context: Context, intent: Intent) {
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d(TAG, "Number of messages: ${messages.size}")

            for (message in messages) {
                val sender = message.displayOriginatingAddress
                val messageBody = message.messageBody
                val timestamp = message.timestampMillis // Get the timestamp

                if (sender.isNullOrBlank() || messageBody.isNullOrBlank()) {
                    Log.w(TAG, "Sender or message body is null/blank in received SMS. Skipping.")
                    continue
                }

                Log.d(TAG, "SMS Details:")
                Log.d(TAG, "  Sender: $sender")
                Log.d(TAG, "  Body: $messageBody")
                Log.d(TAG, "  Timestamp: $timestamp")

                // Call the existing isTransactionSms before deciding to enqueue work
                if (isTransactionSms(messageBody, sender)) {
                    Log.i(TAG, "🏦 TRANSACTION SMS DETECTED! Enqueuing for processing.")
                    Log.i(TAG, "Bank/Sender: $sender")
                    Log.i(TAG, "Transaction Message: $messageBody")

                    val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
                        .setInputData(
                            workDataOf(
                                SmsProcessingWorker.KEY_SENDER to sender,
                                SmsProcessingWorker.KEY_BODY to messageBody,
                                SmsProcessingWorker.KEY_TIMESTAMP to timestamp // Pass timestamp
                            )
                        )
                        // Optional: Add constraints, backoff policy, tags
                        // .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .addTag("sms_processing_work")
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                    Log.d(TAG, "SmsProcessingWorker enqueued for: $sender")

                } else {
                    Log.d(TAG, "Not a transaction SMS (checked in receiver). Skipping enqueue.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSmsReceived", e)
        }
    }

    // isTransactionSms remains the same as you had it,
    // it acts as a preliminary filter before enqueuing the worker.
    private fun isTransactionSms(messageBody: String, sender: String): Boolean {
        val transactionKeywords = listOf(
            "debited", "credited", "transaction", "payment", "purchase",
            "withdrawn", "deposited", "balance", "amount", "rs.", "rs ", "inr",
            "upi", "card", "atm", "transfer", "spent", "received", "paid",
            "bank", "account", "debit", "credit", "rupees", "₹", "txn"
        )

        val bankSenders = listOf(
            // Consider more specific sender IDs if possible, or use more advanced sender validation.
            // These are broad and might catch non-transactional SMS from these entities.
            "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara",
            "union", "idbi", "paytm", "phonepe", "gpay", "bhim", "amazonpay", // More specific for Amazon Pay
            "slice", "uni", "cred", // Fintech apps
            // Generic bank identifiers (often prefixes of short codes like VM-HDFCBK)
            "bank", "bk", "bofm", "cbin", "corp", "hdfc", "icic", "idbi",
            "indbnk", "kbank", "kvb", "mahb", "synd", "ubin", "yesb", "payzapp",
            "airtelpaymentsbank", "jio"
        )

        val messageBodyLower = messageBody.lowercase(Locale.ROOT)
        val senderLower = sender.lowercase(Locale.ROOT)

        val hasTransactionKeyword = transactionKeywords.any { keyword ->
            messageBodyLower.contains(keyword)
        }

        // For sender matching, it's often better to check if the sender ID *starts with* or *contains*
        // a known bank identifier, as sender IDs can vary (e.g., VM-HDFCBK, AD-AXISBK).
        val isFromBank = bankSenders.any { bankKeyword ->
            senderLower.contains(bankKeyword.lowercase(Locale.ROOT))
        }

        // --- Refined Logic: Prioritize keyword, but also consider sender for some cases ---
        // A common pattern: if it has strong keywords, it's likely a transaction.
        // If it's from a known bank sender AND contains at least some weaker monetary indicator, also consider it.
        val hasMonetaryIndicator = listOf("rs", "inr", "₹", "amount", "balance").any { messageBodyLower.contains(it) }

        val isLikelyTransaction = hasTransactionKeyword || (isFromBank && hasMonetaryIndicator)


        Log.d(TAG, "isTransactionSms Check: Keywords: $hasTransactionKeyword, Bank: $isFromBank, Monetary: $hasMonetaryIndicator -> Likely: $isLikelyTransaction")

        return isLikelyTransaction // Adjusted logic
    }

    // parseAndSaveTransaction is now handled by the SmsProcessingWorker
    // You can remove this method from SmsReceiver or keep it empty.
    // private fun parseAndSaveTransaction(sender: String, messageBody: String) { /* No longer needed here */ }
}