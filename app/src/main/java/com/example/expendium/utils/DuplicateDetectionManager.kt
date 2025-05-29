// utils/DuplicateDetectionManager.kt
package com.example.expendium.utils

import android.content.Context
import android.util.Log
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private const val DUPLICATE_TIME_WINDOW_MS = 10 * 60 * 1000L // Increased to 10 minutes
        private const val AMOUNT_TOLERANCE = 0.01 // 1 paisa
        private const val RECENT_TRANSACTIONS_PREF = "RecentTransactions"
        private const val PROCESSED_TRANSACTIONS_PREF = "ProcessedTransactions"
        private const val SMS_HASH_PREF = "ProcessedSmsHashes"
    }

    // Mutex to prevent race conditions during duplicate checking
    private val duplicateCheckMutex = Mutex()

    // In-memory cache for very recent transactions (last few minutes)
    private val recentTransactionsCache = mutableMapOf<String, Long>()
    private val cacheCleanupMutex = Mutex()

    /**
     * Comprehensive duplicate detection with race condition protection
     */
    suspend fun isDuplicateTransaction(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ): Boolean = duplicateCheckMutex.withLock {

        Log.d(TAG, "Checking duplicate for: amount=$amount, type=$type, sender=$sender")

        // 1. Generate consistent hash for this SMS
        val smsHash = generateConsistentSmsHash(sender, messageBody, timestamp, amount, type)

        // 2. Check if this exact SMS has been processed before
        if (hasSmsBeenProcessed(smsHash)) {
            Log.d(TAG, "SMS hash duplicate found: $smsHash")
            return@withLock true
        }

        // 3. Check in-memory cache first (fastest check)
        if (hasInMemoryCacheDuplicate(amount, type, timestamp)) {
            Log.d(TAG, "In-memory cache duplicate found")
            return@withLock true
        }

        // 4. Check database for recent similar transactions
        if (hasDatabaseDuplicate(amount, type, timestamp)) {
            Log.d(TAG, "Database duplicate found for amount: $amount, type: $type")
            return@withLock true
        }

        // 5. Check persistent SharedPreferences cache
        if (hasSharedPrefsDuplicate(amount, type, timestamp)) {
            Log.d(TAG, "SharedPreferences duplicate found")
            return@withLock true
        }

        return@withLock false
    }

    /**
     * Record transaction to prevent future duplicates
     */
    suspend fun recordTransaction(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        sender: String,
        messageBody: String
    ) = duplicateCheckMutex.withLock {
        val currentTime = System.currentTimeMillis()

        // 1. Record SMS hash
        val smsHash = generateConsistentSmsHash(sender, messageBody, timestamp, amount, type)
        recordSmsHash(smsHash)

        // 2. Record in in-memory cache
        val transactionKey = generateTransactionKey(amount, type, timestamp)
        recentTransactionsCache[transactionKey] = currentTime

        // 3. Record in SharedPreferences
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        recentPrefs.edit().putLong(transactionKey, currentTime).apply()

        // 4. Record hash-based duplicate detection
        val transactionHash = generateTransactionHash(amount, type, timestamp, sender, messageBody)
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        processedPrefs.edit().putLong(transactionHash, currentTime).apply()

        Log.d(TAG, "Recorded transaction - SMS Hash: $smsHash, Key: $transactionKey")
    }

    /**
     * Check if SMS has been processed before using hash
     */
    private fun hasSmsBeenProcessed(smsHash: String): Boolean {
        val smsPrefs = context.getSharedPreferences(SMS_HASH_PREF, Context.MODE_PRIVATE)
        return smsPrefs.contains(smsHash)
    }

    /**
     * Record SMS hash to prevent reprocessing
     */
    private fun recordSmsHash(smsHash: String) {
        val smsPrefs = context.getSharedPreferences(SMS_HASH_PREF, Context.MODE_PRIVATE)
        smsPrefs.edit().putLong(smsHash, System.currentTimeMillis()).apply()
    }

    /**
     * Generate consistent SMS hash
     */
    private fun generateConsistentSmsHash(
        sender: String,
        messageBody: String,
        timestamp: Long,
        amount: Double,
        type: TransactionType
    ): String {
        // Normalize inputs to ensure consistency
        val normalizedSender = normalizeSender(sender)
        val normalizedBody = normalizeMessageBody(messageBody)

        // Round timestamp to nearest 30 seconds to handle delivery delays
        val roundedTimestamp = ((timestamp + 15000) / 30000) * 30000

        val content = "${normalizedSender}_${normalizedBody}_${roundedTimestamp}_${amount}_${type}"

        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // Use first 16 characters for efficiency
    }

    /**
     * Normalize message body for consistent hashing
     */
    private fun normalizeMessageBody(messageBody: String): String {
        return messageBody
            .lowercase()
            .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove special characters
            .trim()
            .take(100) // Take first 100 characters to avoid very long strings
    }

    /**
     * Check in-memory cache for duplicates (fastest check)
     */
    private suspend fun hasInMemoryCacheDuplicate(amount: Double, type: TransactionType, timestamp: Long): Boolean {
        // Clean cache periodically
        cacheCleanupMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val cutoffTime = currentTime - DUPLICATE_TIME_WINDOW_MS

            val iterator = recentTransactionsCache.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < cutoffTime) {
                    iterator.remove()
                }
            }
        }

        val transactionKey = generateTransactionKey(amount, type, timestamp)
        val recordedTime = recentTransactionsCache[transactionKey]

        if (recordedTime == null) return false

        val currentTime = System.currentTimeMillis()
        return (currentTime - recordedTime) <= DUPLICATE_TIME_WINDOW_MS
    }

    /**
     * Check database for duplicate transactions with improved logic
     */
    private suspend fun hasDatabaseDuplicate(amount: Double, type: TransactionType, timestamp: Long): Boolean {
        return try {
            // Check for transactions with same amount and type within time window
            val startTime = timestamp - DUPLICATE_TIME_WINDOW_MS
            val endTime = timestamp + DUPLICATE_TIME_WINDOW_MS

            val similarTransactions = transactionRepository.getTransactionsByAmountAndTypeInTimeRange(
                amount = amount,
                type = type,
                startTime = startTime,
                endTime = endTime
            )

            // Check if any transaction is within the tolerance
            similarTransactions.any { transaction ->
                val amountDiff = abs(transaction.amount - amount)
                val timeDiff = abs(transaction.transactionDate - timestamp)

                amountDiff <= AMOUNT_TOLERANCE && timeDiff <= DUPLICATE_TIME_WINDOW_MS
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database duplicates", e)
            false
        }
    }

    /**
     * Check SharedPreferences for duplicates
     */
    private fun hasSharedPrefsDuplicate(amount: Double, type: TransactionType, timestamp: Long): Boolean {
        val transactionKey = generateTransactionKey(amount, type, timestamp)
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val recordedTime = recentPrefs.getLong(transactionKey, 0L)

        if (recordedTime == 0L) return false

        val currentTime = System.currentTimeMillis()
        return (currentTime - recordedTime) <= DUPLICATE_TIME_WINDOW_MS
    }

    /**
     * Generate transaction key with improved time rounding
     */
    private fun generateTransactionKey(amount: Double, type: TransactionType, timestamp: Long): String {
        // Round timestamp to nearest 30 seconds to handle SMS delivery variations
        val roundedTimestamp = ((timestamp + 15000) / 30000) * 30000
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
        val roundedTimestamp = ((timestamp + 15000) / 30000) * 30000
        val cleanedBody = normalizeMessageBody(messageBody)
        val normalizedSender = normalizeSender(sender)

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

        return when {
            normalized.contains("hdfcbank") || normalized.contains("hdfcbk") || normalized.contains("vm-hdfcbk") || normalized.contains("bp-hdfcbk") -> "hdfc"
            normalized.contains("icicibank") || normalized.contains("icicibk") || normalized.contains("vm-icicib") || normalized.contains("bp-icicib") -> "icici"
            normalized.contains("axisbank") || normalized.contains("axisbk") || normalized.contains("vm-axisbk") || normalized.contains("bp-axisbk") -> "axis"
            normalized.contains("sbibank") || normalized.contains("sbimb") || normalized.contains("vm-sbimb") || normalized.contains("bp-sbimb") -> "sbi"
            normalized.contains("kotakbank") || normalized.contains("kotakbk") || normalized.contains("vm-kotakbk") || normalized.contains("bp-kotakbk") -> "kotak"
            normalized.contains("phonepe") -> "phonepe"
            normalized.contains("googlepay") || normalized.contains("gpay") -> "gpay"
            normalized.contains("paytm") -> "paytm"
            else -> normalized.replace(Regex("[^a-z0-9]"), "")
        }
    }

    /**
     * Clean up old entries with improved logic
     */
    suspend fun cleanupOldEntries() = duplicateCheckMutex.withLock {
        val currentTime = System.currentTimeMillis()
        val recentCutoffTime = currentTime - (DUPLICATE_TIME_WINDOW_MS * 3) // Keep for 3x the window
        val processedCutoffTime = currentTime - (24 * 60 * 60 * 1000L) // Keep for 24 hours
        val smsCutoffTime = currentTime - (7 * 24 * 60 * 60 * 1000L) // Keep SMS hashes for 7 days

        // Clean in-memory cache
        cacheCleanupMutex.withLock {
            val iterator = recentTransactionsCache.iterator()
            var memoryCleaned = 0
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value < recentCutoffTime) {
                    iterator.remove()
                    memoryCleaned++
                }
            }
            if (memoryCleaned > 0) {
                Log.d(TAG, "Cleaned $memoryCleaned entries from in-memory cache")
            }
        }

        // Clean recent transactions SharedPreferences
        cleanSharedPrefs(RECENT_TRANSACTIONS_PREF, recentCutoffTime, "recent transactions")

        // Clean processed transactions SharedPreferences
        cleanSharedPrefs(PROCESSED_TRANSACTIONS_PREF, processedCutoffTime, "processed transactions")

        // Clean SMS hashes SharedPreferences
        cleanSharedPrefs(SMS_HASH_PREF, smsCutoffTime, "SMS hashes")
    }

    /**
     * Helper method to clean SharedPreferences
     */
    private fun cleanSharedPrefs(prefName: String, cutoffTime: Long, description: String) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var cleaned = 0

        prefs.all.forEach { (key, value) ->
            if (value is Long && value < cutoffTime) {
                editor.remove(key)
                cleaned++
            }
        }

        if (cleaned > 0) {
            editor.apply()
            Log.d(TAG, "Cleaned $cleaned old entries from $description cache")
        }
    }

    /**
     * Get comprehensive duplicate detection statistics
     */
    fun getDuplicateDetectionStats(): Map<String, Int> {
        val recentPrefs = context.getSharedPreferences(RECENT_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val processedPrefs = context.getSharedPreferences(PROCESSED_TRANSACTIONS_PREF, Context.MODE_PRIVATE)
        val smsPrefs = context.getSharedPreferences(SMS_HASH_PREF, Context.MODE_PRIVATE)

        return mapOf(
            "inMemoryCache" to recentTransactionsCache.size,
            "recentTransactions" to recentPrefs.all.size,
            "processedTransactions" to processedPrefs.all.size,
            "smsHashes" to smsPrefs.all.size
        )
    }

    /**
     * Force cleanup for testing/debugging
     */
    suspend fun forceCleanup() {
        cleanupOldEntries()
    }

    /**
     * Clear all caches (use with caution - for testing only)
     */
    suspend fun clearAllCaches() = duplicateCheckMutex.withLock {
        recentTransactionsCache.clear()

        val prefNames = listOf(RECENT_TRANSACTIONS_PREF, PROCESSED_TRANSACTIONS_PREF, SMS_HASH_PREF)
        prefNames.forEach { prefName ->
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }

        Log.d(TAG, "All duplicate detection caches cleared")
    }
}