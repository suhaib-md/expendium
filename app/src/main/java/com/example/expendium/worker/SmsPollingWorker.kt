// worker/SmsPollingWorker.kt
package com.example.expendium.worker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SmsPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsPollingWorker"
        private const val LAST_CHECKED_KEY = "lastSmsCheckTimestamp"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting SMS polling check...")

            val sharedPrefs = applicationContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val smsParsingEnabled = sharedPrefs.getBoolean("isSmsParsingEnabled", true)

            if (!smsParsingEnabled) {
                Log.d(TAG, "SMS parsing disabled, skipping poll")
                return@withContext Result.success()
            }

            val lastChecked = sharedPrefs.getLong(LAST_CHECKED_KEY, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
            val currentTime = System.currentTimeMillis()

            Log.d(TAG, "Checking for SMS since: $lastChecked")

            val newMessages = checkForNewSmsMessages(lastChecked)
            Log.d(TAG, "Found ${newMessages.size} new messages")

            newMessages.forEach { smsData ->
                Log.d(TAG, "Processing SMS from ${smsData.sender}: ${smsData.body}")

                if (isTransactionSms(smsData.body, smsData.sender)) {
                    Log.i(TAG, "🏦 TRANSACTION SMS FOUND via polling!")

                    val workRequest = OneTimeWorkRequestBuilder<SmsProcessingWorker>()
                        .setInputData(
                            workDataOf(
                                SmsProcessingWorker.KEY_SENDER to smsData.sender,
                                SmsProcessingWorker.KEY_BODY to smsData.body,
                                SmsProcessingWorker.KEY_TIMESTAMP to smsData.timestamp
                            )
                        )
                        .addTag("sms_polling_work")
                        .build()

                    WorkManager.getInstance(applicationContext).enqueue(workRequest)
                }
            }

            // Update last checked timestamp
            sharedPrefs.edit().putLong(LAST_CHECKED_KEY, currentTime).apply()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SMS polling", e)
            Result.failure()
        }
    }

    private data class SmsData(
        val sender: String,
        val body: String,
        val timestamp: Long
    )

    private fun checkForNewSmsMessages(sinceTimestamp: Long): List<SmsData> {
        val messages = mutableListOf<SmsData>()

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
            val selectionArgs = arrayOf(
                sinceTimestamp.toString(),
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString()
            )
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            val cursor: Cursor? = applicationContext.contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )

            cursor?.use { c ->
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)

                while (c.moveToNext()) {
                    val address = c.getString(addressIndex) ?: continue
                    val body = c.getString(bodyIndex) ?: continue
                    val date = c.getLong(dateIndex)

                    messages.add(SmsData(address, body, date))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS content provider", e)
        }

        return messages
    }

    private fun isTransactionSms(messageBody: String, sender: String): Boolean {
        val transactionKeywords = listOf(
            "debited", "credited", "transaction", "payment", "purchase",
            "withdrawn", "deposited", "balance", "amount", "rs.", "rs ", "inr",
            "upi", "card", "atm", "transfer", "spent", "received", "paid",
            "bank", "account", "debit", "credit", "rupees", "₹", "txn"
        )

        val bankSenders = listOf(
            "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara",
            "union", "idbi", "paytm", "phonepe", "gpay", "bhim", "amazonpay",
            "slice", "uni", "cred", "bank", "bk"
        )

        val messageBodyLower = messageBody.lowercase()
        val senderLower = sender.lowercase()

        val hasTransactionKeyword = transactionKeywords.any { keyword ->
            messageBodyLower.contains(keyword)
        }

        val isFromBank = bankSenders.any { bankKeyword ->
            senderLower.contains(bankKeyword)
        }

        val hasMonetaryIndicator = listOf("rs", "inr", "₹", "amount", "balance").any {
            messageBodyLower.contains(it)
        }

        return hasTransactionKeyword || (isFromBank && hasMonetaryIndicator)
    }
}