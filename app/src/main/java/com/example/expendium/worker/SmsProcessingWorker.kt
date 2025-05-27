// worker/SmsProcessingWorker.kt
package com.example.expendium.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.expendium.data.model.Account
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.repository.AccountRepository
import com.example.expendium.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@HiltWorker
class SmsProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsProcessingWorker"
        const val KEY_SENDER = "SENDER"
        const val KEY_BODY = "BODY"
        const val KEY_TIMESTAMP = "TIMESTAMP"

        // Bank sender patterns for better recognition
        private val BANK_SENDER_PATTERNS = listOf(
            "vm-", "bp-", "ax-", "ad-", "tm-", "jk-", "sb-", "hdfcbk", "icicib",
            "axisbk", "kotakbk", "sbimb", "pnbsms", "unionbk", "canarabk", "bobsms",
            "idbibank", "yesbank", "rblbank", "indusbk", "denabank", "federal",
            "paytm", "phonepe", "gpay", "bhim", "amazonpay", "mobikwik", "freecharge"
        )
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

        // Generate SMS hash for duplicate detection
        val smsHash = generateSmsHash(sender, messageBody, timestamp)

        // Check for duplicate processing
        if (isAlreadyProcessed(smsHash)) {
            Log.i(TAG, "SMS already processed. Skipping.")
            return@withContext Result.success()
        }

        try {
            val parsedDetails = parseTransactionDetails(messageBody, sender)

            if (parsedDetails != null) {
                val (amount, type, merchant, notesFromParser, paymentMode, accountHint) = parsedDetails

                // Determine the account
                val targetAccount = determineAccount(messageBody, sender, accountHint)

                if (targetAccount == null) {
                    Log.w(TAG, "Could not determine account for SMS. Saving transaction without account link.")
                }

                // Determine category
                val categoryId = determineCategory(merchant, messageBody, type)

                val transactionNotes = "SMS ($sender): $notesFromParser"

                val transaction = Transaction(
                    amount = amount,
                    transactionDate = timestamp,
                    type = type,
                    categoryId = categoryId,
                    merchantOrPayee = merchant,
                    notes = transactionNotes,
                    paymentMode = paymentMode,
                    isManual = false,
                    accountId = targetAccount?.accountId,
                    originalSmsId = smsHash
                )

                // Save transaction and update account balance
                if (targetAccount != null) {
                    transactionRepository.insertTransactionAndUpdateAccountBalance(transaction, targetAccount)
                    Log.i(TAG, "✅ Transaction from SMS saved and account '${targetAccount.name}' balance updated.")
                } else {
                    transactionRepository.insertTransaction(transaction)
                    Log.i(TAG, "✅ Transaction from SMS saved (no account link). Merchant: $merchant")
                }

                // Mark as processed
                markAsProcessed(smsHash)

                Result.success()
            } else {
                Log.w(TAG, "ℹ️ Could not parse required transaction details from SMS. Not saving.")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing or saving transaction in worker", e)
            Result.failure()
        }
    }

    private data class ParsedDetails(
        val amount: Double,
        val type: TransactionType,
        val merchant: String,
        val notes: String,
        val paymentMode: String,
        val accountHint: String? = null
    )

    private fun parseTransactionDetails(messageBody: String, sender: String): ParsedDetails? {
        val lowerBody = messageBody.lowercase(Locale.ROOT)
        val originalBody = messageBody

        // Enhanced transaction type detection with more patterns
        val type: TransactionType = when {
            // DEBIT/EXPENSE patterns - comprehensive (including "sent")
            listOf(
                "debit:", "debited", "spent", "paid", "payment to", "withdrawal",
                "purchase", "txn at", "transaction at", "bought", "transferred to",
                "sent to", "sent rs", "sent inr", "sent ₹", "dr ", "dr.", "withdrawal from",
                "paid to", "outgoing", "deducted", "charged", "bill payment", "emi",
                "autopay", "money sent", "amount sent", "transferred", "send money"
            ).any { lowerBody.contains(it) } -> TransactionType.EXPENSE

            // CREDIT/INCOME patterns - comprehensive
            listOf(
                "credit:", "credited", "received", "deposited", "payment from",
                "refund", "cashback", "interest", "salary", "cr ", "cr.",
                "received from", "transferred from", "deposit", "incoming",
                "added", "bonus", "reward", "dividend", "commission", "money received",
                "amount received"
            ).any { lowerBody.contains(it) } -> TransactionType.INCOME

            else -> {
                Log.d(TAG, "Could not determine transaction type for: $messageBody")
                return null
            }
        }

        // Enhanced amount extraction with multiple sophisticated patterns
        val amount = extractAmount(originalBody) ?: run {
            Log.d(TAG, "Could not extract valid amount from: $messageBody")
            return null
        }

        // Extract merchant with enhanced logic
        val merchant = extractMerchant(originalBody, sender, type)

        // Detect payment mode with enhanced logic
        val paymentMode = detectPaymentMode(originalBody, sender)

        // Extract account hint if present
        val accountHint = extractAccountHint(originalBody)

        val notes = messageBody

        Log.d(TAG, "Parsed: Amount=₹$amount, Type=$type, Merchant='$merchant', PaymentMode='$paymentMode'")
        return ParsedDetails(amount, type, merchant, notes, paymentMode, accountHint)
    }

    private fun extractAmount(messageBody: String): Double? {
        // Multiple regex patterns for amount extraction
        val amountRegexes = listOf(
            // Pattern 1: Currency symbols with amount
            """(?:rs\.?\s*|inr\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 2: Amount with currency symbols
            """([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 3: Transaction type with amount
            """(?:debit|credit|dr|cr):\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 4: Amount keywords
            """(?:amount|amt|sum)\s*(?:of)?\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 5: UPI format amounts
            """(?:upi|imps|neft)\s*(?:.*?)\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 6: Balance format (Bal: Rs 1000.00)
            """bal:\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 7: Generic number before transaction words
            """([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|sent|received|spent|paid|purchase|withdraw|transfer)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 8: Standalone amounts in transaction context
            """\b([\d,]+\.\d{2})\b""".toRegex()
        )

        for (regex in amountRegexes) {
            val match = regex.find(messageBody)
            if (match != null) {
                val amountString = match.groups[1]?.value?.replace(",", "")
                val amount = amountString?.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    Log.d(TAG, "Amount extracted: $amount using pattern: ${regex.pattern}")
                    return amount
                }
            }
        }

        return null
    }

    private fun extractMerchant(messageBody: String, sender: String, type: TransactionType): String {
        var merchant = "Unknown/SMS"

        // Enhanced merchant patterns for RCS/notification messages
        val merchantPatterns = listOf(
            // RCS-specific patterns (for messages like "To IITMRP ACCT FULFILL FOODS")
            """(?:to|paid to|sent to)\s+([A-Z][A-Z0-9\s]+[A-Z])(?:\s*on|\s*ref|\s*$)""".toRegex(RegexOption.IGNORE_CASE),

            // UPI patterns - most common
            """UPI/[^/]+/([^/]+)/([A-Za-z0-9\s.&'@*#-]+?)(?:\s|/|$)""".toRegex(RegexOption.IGNORE_CASE),
            """UPI\s*(?:to|payment to|from)\s*([A-Za-z0-9\s.&'@*#-]+?)(?:\s*(?:ref|txn|/|\s*$))""".toRegex(RegexOption.IGNORE_CASE),

            // Account-based patterns (like "IITMRP ACCT FULFILL FOODS")
            """(?:ACCT|ACCOUNT)\s+([A-Za-z0-9\s.&'@*#-]+?)(?:\s*(?:on|ref|txn|\s*$))""".toRegex(RegexOption.IGNORE_CASE),

            // Direct payment patterns
            """(?:to|paid to|sent to|purchase at|spent at|payment to|debited for|txn at|transaction at)\s+([A-Za-z0-9\s.&'@*#-]+?)(?:\s*(?:\.|on|avbl|avl|bal|for|ref|txnid|upi|vpa|ac|card|ending|-)|\s*$)""".toRegex(RegexOption.IGNORE_CASE),

            // Received from patterns
            """(?:from|received from|credited by|salary from|refund from|transfer from)\s+([A-Za-z0-9\s.&'@*#-]+?)(?:\s*(?:\.|on|avbl|avl|bal|for|ref|txnid|upi|vpa|ac|-)|\s*$)""".toRegex(RegexOption.IGNORE_CASE),

            // Info patterns (common in bank SMS)
            """Info[:\s]*([A-Za-z0-9\s.&*@#-]+?)[\.\s]""".toRegex(RegexOption.IGNORE_CASE),

            // Merchant/vendor patterns
            """(?:merchant|vendor|at)\s+([A-Za-z0-9\s.&'*-]+?)(?:\s*(?:tx|ref|on|\s*$))""".toRegex(RegexOption.IGNORE_CASE),

            // POS patterns
            """POS\s*[:\s]*([A-Za-z0-9\s.&'*-]+?)(?:\s*(?:on|ref|\s*$))""".toRegex(RegexOption.IGNORE_CASE),

            // ATM patterns
            """ATM\s*[:\s]*([A-Za-z0-9\s.&'*-]+?)(?:\s*(?:on|ref|\s*$))""".toRegex(RegexOption.IGNORE_CASE)
        )

        // Try each pattern
        for (pattern in merchantPatterns) {
            val merchantMatch = pattern.find(messageBody)
            if (merchantMatch != null) {
                // Get the last non-null group (handles multiple capture groups)
                val extractedMerchant = merchantMatch.groups.drop(1)
                    .filterNotNull()
                    .lastOrNull()?.value?.trim()

                if (!extractedMerchant.isNullOrBlank()) {
                    val cleaned = cleanMerchantName(extractedMerchant)
                    if (cleaned.isNotBlank() && cleaned.length >= 2) {
                        merchant = cleaned
                        break
                    }
                }
            }
        }

        // Try VPA pattern if no merchant found
        if (merchant == "Unknown/SMS") {
            val vpaPattern = """([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""".toRegex()
            val vpaMatch = vpaPattern.find(messageBody)
            if (vpaMatch != null) {
                merchant = vpaMatch.value.split("@")[0] // Use part before @
            }
        }

        // For RCS/notification messages, don't use sender name as fallback
        // since it's often the contact name, not the actual merchant
        if (merchant == "Unknown/SMS") {
            merchant = "Unknown"
        }

        return merchant
    }

    private fun cleanMerchantName(rawMerchant: String): String {
        var cleaned = rawMerchant.trim()

        // Remove common suffixes and patterns
        cleaned = cleaned.removeSuffix(".").removeSuffix(",").trim()

        // Remove via patterns
        cleaned = cleaned.replace(Regex("""\s+via\s+.*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove date patterns
        cleaned = cleaned.split(Regex("""\s+(on|at)\s+\d{1,2}[-/]\d{1,2}"""))[0].trim()

        // Remove balance info
        cleaned = cleaned.split(Regex("""\s+(?:avbl|avl)\s+bal.*""", RegexOption.IGNORE_CASE))[0].trim()
        cleaned = cleaned.split(Regex("""\s+bal:.*""", RegexOption.IGNORE_CASE))[0].trim()

        // Remove reference info
        cleaned = cleaned.split(Regex("""\s+(?:ref|txn|transaction)\s+(?:no|id).*""", RegexOption.IGNORE_CASE))[0].trim()

        // Remove RE (reference) suffix
        cleaned = cleaned.replace(Regex("""\s+RE\s*$""", RegexOption.IGNORE_CASE), "").trim()

        // Remove VPA prefix
        cleaned = cleaned.replace(Regex("""^VPA\s*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove stuff after " - "
        cleaned = cleaned.split(Regex("""\s*-\s*"""))[0].trim()

        // Remove card info
        cleaned = cleaned.replace(Regex("""\s*card\s*\d+""", RegexOption.IGNORE_CASE), "").trim()

        // Remove account info
        cleaned = cleaned.replace(Regex("""\s*a/c\s*\d+""", RegexOption.IGNORE_CASE), "").trim()

        // Handle special characters and cleanup
        cleaned = cleaned.replace(Regex("""[*]{2,}"""), " ").trim()
        cleaned = cleaned.replace(Regex("""\s{2,}"""), " ").trim()

        // Capitalize properly
        if (cleaned.isNotBlank()) {
            cleaned = cleaned.split(" ")
                .joinToString(" ") { word ->
                    if (word.length > 1) word.lowercase().replaceFirstChar { it.uppercase() }
                    else word.uppercase()
                }
        }

        // Truncate if too long
        if (cleaned.length > 50) {
            cleaned = cleaned.substring(0, 50).trim() + "..."
        }

        return cleaned.ifBlank { "Unknown" }
    }

    private fun cleanSenderName(sender: String): String {
        var cleaned = sender

        // Remove common prefixes
        cleaned = cleaned.replace(Regex("""^(?:VM-|BP-|AX-|AD-|TM-|JK-|SB-)""", RegexOption.IGNORE_CASE), "")

        // Clean up bank names
        val bankNameMap = mapOf(
            "HDFCBK" to "HDFC Bank",
            "ICICIBK" to "ICICI Bank",
            "AXISBK" to "Axis Bank",
            "SBIMB" to "SBI",
            "KOTAKBK" to "Kotak Bank",
            "YESBANK" to "Yes Bank",
            "RBLBANK" to "RBL Bank"
        )

        bankNameMap.forEach { (key, value) ->
            if (cleaned.contains(key, ignoreCase = true)) {
                cleaned = value
            }
        }

        return cleaned
    }

    private fun detectPaymentMode(messageBody: String, sender: String): String {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        return when {
            lowerBody.contains("upi") -> "UPI"
            lowerBody.contains("imps") -> "IMPS"
            lowerBody.contains("neft") -> "NEFT"
            lowerBody.contains("rtgs") -> "RTGS"
            lowerBody.contains("card") && lowerBody.contains("credit") -> "Credit Card"
            lowerBody.contains("card") && lowerBody.contains("debit") -> "Debit Card"
            lowerBody.contains("card") -> "Card"
            lowerBody.contains("netbanking") || lowerBody.contains("net banking") -> "NetBanking"
            lowerBody.contains("wallet") -> "Wallet"
            lowerBody.contains("atm") -> "ATM"
            lowerBody.contains("pos") -> "POS"
            lowerBody.contains("cheque") || lowerBody.contains("check") -> "Cheque"
            lowerBody.contains("cash") -> "Cash"
            lowerBody.contains("auto pay") || lowerBody.contains("autopay") -> "Auto Pay"
            lowerBody.contains("emi") -> "EMI"
            lowerBody.contains("a/c") || lowerBody.contains("account") -> "Bank Transfer"
            else -> "SMS"
        }
    }

    private fun extractAccountHint(messageBody: String): String? {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Look for account number patterns
        val accPatterns = listOf(
            """a/c\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """account\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{4})""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in accPatterns) {
            val match = pattern.find(lowerBody)
            if (match != null) {
                return match.groups[1]?.value
            }
        }

        return null
    }

    private suspend fun determineAccount(messageBody: String, sender: String, accountHint: String?): Account? {
        val accounts = accountRepository.getAllAccounts().firstOrNull() ?: emptyList()

        // Strategy 1: Use account hint if available
        if (!accountHint.isNullOrBlank()) {
            Log.d(TAG, "Found account hint: $accountHint")
            val foundAccount = accounts.find { account ->
                account.accountNumber?.endsWith(accountHint) == true ||
                        account.name.contains(accountHint, ignoreCase = true)
            }
            if (foundAccount != null) {
                Log.i(TAG, "Matched account by hint: ${foundAccount.name}")
                return foundAccount
            }
        }

        // Strategy 2: Map sender to account (can be enhanced with user preferences)
        val senderMappings = mapOf(
            "HDFCBK" to listOf("hdfc", "hdfc bank", "hdfc savings", "hdfc current"),
            "ICICIBK" to listOf("icici", "icici bank", "icici savings", "icici current"),
            "AXISBK" to listOf("axis", "axis bank", "axis savings", "axis current"),
            "SBIMB" to listOf("sbi", "sbi bank", "state bank"),
            "KOTAKBK" to listOf("kotak", "kotak bank"),
            "YESBANK" to listOf("yes", "yes bank"),
            "PAYTM" to listOf("paytm", "paytm wallet"),
            "PHONEPE" to listOf("phonepe", "phone pe"),
            "GPAY" to listOf("gpay", "google pay")
        )

        val senderUpper = sender.uppercase()
        senderMappings.forEach { (senderKey, accountNames) ->
            if (senderUpper.contains(senderKey)) {
                val matchedAccount = accounts.find { account ->
                    accountNames.any { name ->
                        account.name.contains(name, ignoreCase = true)
                    }
                }
                if (matchedAccount != null) {
                    Log.i(TAG, "Matched account by sender mapping: ${matchedAccount.name}")
                    return matchedAccount
                }
            }
        }

        // Strategy 3: Default SMS account from preferences
        val sharedPrefs = applicationContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val defaultAccountId = sharedPrefs.getLong("defaultSmsAccountId", -1L)
        if (defaultAccountId != -1L) {
            val defaultAccount = accounts.find { it.accountId == defaultAccountId }
            if (defaultAccount != null) {
                Log.i(TAG, "Using default SMS account: ${defaultAccount.name}")
                return defaultAccount
            }
        }

        Log.w(TAG, "No account could be determined for sender: $sender")
        return null
    }

    private suspend fun determineCategory(merchant: String, messageBody: String, type: TransactionType): Long? {
        val lowerMerchant = merchant.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Enhanced category mapping (you can expand this based on your categories)
        if (type == TransactionType.EXPENSE) {
            when {
                // Food & Dining
                listOf("zomato", "swiggy", "ubereats", "dominos", "pizza", "restaurant",
                    "food", "cafe", "starbucks", "kfc", "mcdonald", "burger").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return null // Replace with actual food category ID

                // Transportation
                listOf("ola", "uber", "taxi", "fuel", "petrol", "diesel", "metro",
                    "bus", "auto", "rickshaw", "rapido").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return null // Replace with transport category ID

                // Shopping
                listOf("amazon", "flipkart", "myntra", "ajio", "shopping", "mall",
                    "store", "mart", "bazaar", "zepto").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return null // Replace with shopping category ID

                // Bills & Utilities
                listOf("bill", "recharge", "electricity", "gas", "water", "broadband",
                    "internet", "mobile", "jio", "airtel", "vi").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return null // Replace with bills category ID

                // Entertainment
                listOf("netflix", "amazon prime", "hotstar", "spotify", "movie",
                    "cinema", "ticket", "booking").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return null // Replace with entertainment category ID
            }
        } else if (type == TransactionType.INCOME) {
            when {
                // Salary
                listOf("salary", "wage", "pay", "employer").any { lowerBody.contains(it) } ->
                    return null // Replace with salary category ID

                // Interest & Returns
                listOf("interest", "dividend", "return", "cashback", "reward").any { lowerBody.contains(it) } ->
                    return null // Replace with returns category ID

                // Refunds
                listOf("refund", "refunded", "reversal").any { lowerBody.contains(it) } ->
                    return null // Replace with refund category ID
            }
        }

        return null // No category determined
    }

    private fun isGenericBankSender(lowerSender: String): Boolean {
        val genericPatterns = listOf("bank", "alerts", "update", "notify", "verify", "otp", "promo", "info")
        return genericPatterns.any { lowerSender.contains(it) } ||
                lowerSender.matches(Regex("^[a-z0-9]{2}-[a-z0-9]{5,10}$"))
    }

    // Duplicate detection methods
    private fun generateSmsHash(sender: String, body: String, timestamp: Long): String {
        val content = "$sender|$body|${timestamp / 60000}" // Group by minute to handle slight timestamp differences
        return MessageDigest.getInstance("MD5")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun isAlreadyProcessed(smsHash: String): Boolean {
        val sharedPrefs = applicationContext.getSharedPreferences("ProcessedSMS", Context.MODE_PRIVATE)
        return sharedPrefs.contains(smsHash)
    }

    private fun markAsProcessed(smsHash: String) {
        val sharedPrefs = applicationContext.getSharedPreferences("ProcessedSMS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(smsHash, true).apply()

        // Clean up old entries (keep only last 1000)
        val allEntries = sharedPrefs.all
        if (allEntries.size > 1000) {
            val editor = sharedPrefs.edit()
            allEntries.keys.take(allEntries.size - 1000).forEach { key ->
                editor.remove(key)
            }
            editor.apply()
        }
    }
}