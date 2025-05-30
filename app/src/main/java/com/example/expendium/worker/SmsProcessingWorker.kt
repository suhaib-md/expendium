package com.example.expendium.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expendium.data.model.Account
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.repository.AccountRepository
import com.example.expendium.data.repository.TransactionRepository
import com.example.expendium.utils.DuplicateDetectionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SmsProcessingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val smsValidator: SmsValidator,
    private val transactionParser: TransactionParser,
    private val accountResolver: AccountResolver,
    private val categoryResolver: CategoryResolver,
    private val duplicateDetector: DuplicateDetector,
    private val duplicateDetectionManager: DuplicateDetectionManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsProcessingWorker"
        const val KEY_SENDER = "SENDER"
        const val KEY_BODY = "BODY"
        const val KEY_TIMESTAMP = "TIMESTAMP"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sender = inputData.getString(KEY_SENDER)
        val messageBody = inputData.getString(KEY_BODY)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        if (sender.isNullOrBlank() || messageBody.isNullOrBlank()) {
            Log.e(TAG, "Sender or message body is null or blank in worker.")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Worker processing SMS from $sender (Timestamp: $timestamp): $messageBody")

        // Step 1: Validate SMS
        if (smsValidator.isPromotionalMessage(sender, messageBody)) {
            Log.i(TAG, "🚫 Promotional/spam message detected. Skipping processing.")
            return@withContext Result.success()
        }

        if (!smsValidator.isTrustedSender(sender, messageBody)) {
            Log.i(TAG, "🚫 Untrusted sender detected. Skipping processing.")
            return@withContext Result.success()
        }

        // Step 2: Check for duplicates
        val smsHash = duplicateDetector.generateSmsHash(sender, messageBody, timestamp)
        if (duplicateDetector.isAlreadyProcessed(smsHash)) {
            Log.i(TAG, "SMS already processed. Skipping.")
            return@withContext Result.success()
        }

        try {
            // Step 3: Parse transaction details
            val parsedDetails = transactionParser.parseTransactionDetails(messageBody, sender)
            if (parsedDetails == null) {
                Log.w(TAG, "ℹ️ Could not parse required transaction details from SMS. Not saving.")
                return@withContext Result.failure()
            }

            val (amount, type, merchant, notesFromParser, paymentMode, accountHint) = parsedDetails

            // Step 4: Validate transaction
            if (!smsValidator.isValidTransaction(messageBody, amount, type)) {
                Log.i(TAG, "🚫 Invalid transaction pattern detected. Skipping.")
                return@withContext Result.success()
            }

            // Step 5: Check for duplicates using DuplicateDetectionManager
            if (duplicateDetectionManager.isDuplicateTransaction(amount, type, timestamp, sender, messageBody)) {
                Log.i(TAG, "⚠️ Duplicate transaction detected. Skipping.")
                return@withContext Result.success()
            }

            // Step 6: Determine account
            val targetAccount = accountResolver.determineAccount(messageBody, sender, accountHint)

            // Step 7: Determine category
            val categoryId = categoryResolver.determineCategory(merchant, messageBody, type)

            // Step 8: Create and save transaction
            val transaction = Transaction(
                amount = amount,
                transactionDate = timestamp,
                type = type,
                categoryId = categoryId,
                merchantOrPayee = merchant,
                notes = "SMS ($sender): $notesFromParser",
                paymentMode = paymentMode,
                isManual = false,
                accountId = targetAccount?.accountId,
                originalSmsId = smsHash
            )

            if (targetAccount != null) {
                transactionRepository.insertTransactionAndUpdateAccountBalance(transaction, targetAccount)
                Log.i(TAG, "✅ Transaction saved and account '${targetAccount.name}' balance updated.")
            } else {
                transactionRepository.insertTransaction(transaction)
                Log.i(TAG, "✅ Transaction saved (no account link). Merchant: $merchant")
            }

            // Step 9: Record transaction for duplicate detection
            duplicateDetectionManager.recordTransaction(amount, type, timestamp, sender, messageBody)
            duplicateDetector.markAsProcessed(smsHash)
            duplicateDetectionManager.cleanupOldEntries()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing or saving transaction", e)
            Result.failure()
        }
    }
}