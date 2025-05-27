// utils/DuplicateDetectionManager.kt
package com.example.expendium.utils

import android.content.Context
import android.util.Log
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DuplicateDetectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private const val TAG = "DuplicateDetectionManager"
        private const val DUPLICATE_TIME_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
        private const val AMOUNT_TOLERANCE = 0.01 // 1 paisa
        private const val RECENT_TRANSACTIONS_PREF = "RecentTransactions"
        private const val PROCESSED_TRANSACTIONS_PREF = "ProcessedTransactions"
    }

    /**
     * Comprehensive duplicate detection
     */
    suspend fun isDuplicateTransaction(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ): Boolean {

        // 1. Check database for recent similar transactions
        if (hasDatabaseDuplicate(amount, type, timestamp)) {
            Log.d(TAG, "Database duplicate found for amount: $amount, type: $type")
            return true
        }

        // 2. Check in-memory cache for recent processing
        if (hasRecentProcessingDuplicate(amount, type, timestamp)) {
            Log.d(TAG, "Recent processing duplicate found")
            return true
        }

        // 3. Check persistent hash-based duplicate detection
        if (hasHashBasedDuplicate(amount, type, timestamp, sender, messageBody)) {
            Log.d(TAG, "Hash-based duplicate found")
            return true
        }

        return false
    }

    /**
     * Record transaction to prevent future duplicates
     */
    fun recordTransaction(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ) {
        val currentTime = System.currentTimeMillis()

        // Record in recent transactions cache
        val transactionKey = generateTransactionKey(amount, type, timestamp)
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        recentPrefs.edit().putLong(transactionKey, currentTime).apply()

        // Record hash-based duplicate detection
        val transactionHash = generateTransactionHash(amount, type, timestamp, sender, messageBody)
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        processedPrefs.edit().putLong(transactionHash, currentTime).apply()

        Log.d(TAG, "Recorded transaction for duplicate detection - Key: $transactionKey, Hash: $transactionHash")
    }

    /**
     * Check database for duplicate transactions
     */
    private suspend fun hasDatabaseDuplicate(amount: Double, type: TransactionType, timestamp: Long): Boolean {
        val similarTransaction = transactionRepository.findSimilarTransaction(
            amount = amount,
            type = type,
            timeWindow = DUPLICATE_TIME_WINDOW_MS,
            amountTolerance = AMOUNT_TOLERANCE
        )

        return similarTransaction != null
    }

    /**
     * Check recent processing cache for duplicates
     */
    private fun hasRecentProcessingDuplicate(amount: Double, type: TransactionType, timestamp: Long): Boolean {
        val transactionKey = generateTransactionKey(amount, type, timestamp)
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val recordedTime = recentPrefs.getLong(transactionKey, 0L)

        if (recordedTime == 0L) return false

        val currentTime = System.currentTimeMillis()
        return (currentTime - recordedTime) <= DUPLICATE_TIME_WINDOW_MS
    }

    /**
     * Check hash-based duplicate detection
     */
    private fun hasHashBasedDuplicate(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ): Boolean {
        val transactionHash = generateTransactionHash(amount, type, timestamp, sender, messageBody)
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)

        return processedPrefs.contains(transactionHash)
    }

    /**
     * Generate transaction key for in-memory duplicate detection
     */
    private fun generateTransactionKey(amount: Double, type: TransactionType, timestamp: Long): String {
        // Round timestamp to nearest minute to handle slight timing differences
        val roundedTimestamp = (timestamp / 60000) * 60000
        return "${amount}_${type}_${roundedTimestamp}"
    }

    /**
     * Generate hash for persistent duplicate detection
     */
    private fun generateTransactionHash(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ): String {
        // Round timestamp to minute and clean message body
        val roundedTimestamp = (timestamp / 60000) * 60000
        val cleanedBody = messageBody.replace(Regex("\\s+"), " ").trim().lowercase()
        val normalizedSender = normalizeSender(sender)

        // Create content string for hashing
        val content = "${amount}_${type}_${roundedTimestamp}_${normalizedSender}_${cleanedBody.take(50)}"

        return MessageDigest.getInstance("MD5")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Normalize sender names to handle variations
     */
    private fun normalizeSender(sender: String): String {
        val normalized = sender.lowercase().trim()

        // Map common sender variations
        return when {
            normalized.contains("hdfcbank") || normalized.contains("hdfcbk") -> "hdfc"
            normalized.contains("icicibank") || normalized.contains("icicibk") -> "icici"
            normalized.contains("axisbank") || normalized.contains("axisbk") -> "axis"
            normalized.contains("sbibank") || normalized.contains("sbimb") -> "sbi"
            normalized.contains("kotakbank") || normalized.contains("kotakbk") -> "kotak"
            normalized.contains("phonepe") || normalized.contains("phonepe") -> "phonepe"
            normalized.contains("googlepay") || normalized.contains("gpay") -> "gpay"
            normalized.contains("paytm") -> "paytm"
            else -> normalized
        }
    }

    /**
     * Clean up old entries to prevent unlimited growth
     */
    fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val recentCutoffTime = currentTime - DUPLICATE_TIME_WINDOW_MS * 2 // Keep for double the window
        val processedCutoffTime = currentTime - (7 * 24 * 60 * 60 * 1000L) // Keep for 7 days

        // Clean recent transactions
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val recentEditor = recentPrefs.edit()
        var recentCleaned = 0

        recentPrefs.all.forEach { (key, value) ->
            if (value is Long && value < recentCutoffTime) {
                recentEditor.remove(key)
                recentCleaned++
            }
        }
        if (recentCleaned > 0) {
            recentEditor.apply()
            Log.d(TAG, "Cleaned $recentCleaned old entries from recent transactions cache")
        }

        // Clean processed transactions
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val processedEditor = processedPrefs.edit()
        var processedCleaned = 0

        processedPrefs.all.forEach { (key, value) ->
            if (value is Long && value < processedCutoffTime) {
                processedEditor.remove(key)
                processedCleaned++
            }
        }
        if (processedCleaned > 0) {
            processedEditor.apply()
            Log.d(TAG, "Cleaned $processedCleaned old entries from processed transactions cache")
        }
    }

    /**
     * Get duplicate detection statistics for debugging
     */
    fun getDuplicateDetectionStats(): Map<String, Int> {
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)

        return mapOf(
            "recentTransactions" to recentPrefs.all.size,
            "processedTransactions" to processedPrefs.all.size
        )
    }
}