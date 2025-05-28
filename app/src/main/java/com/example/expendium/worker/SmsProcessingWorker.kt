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
import com.example.expendium.data.repository.CategoryRepository
import com.example.expendium.utils.DuplicateDetectionManager
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
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val duplicateDetectionManager: DuplicateDetectionManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsProcessingWorker"
        const val KEY_SENDER = "SENDER"
        const val KEY_BODY = "BODY"
        const val KEY_TIMESTAMP = "TIMESTAMP"

        // Enhanced bank sender patterns for better recognition
        private val TRUSTED_BANK_SENDERS = listOf(
            "vm-hdfcbk", "bp-hdfcbk", "hdfcbk", "hdfc bank",
            "vm-icicib", "bp-icicib", "icicibk", "icici bank",
            "vm-axisbk", "bp-axisbk", "axisbk", "axis bank",
            "vm-kotakbk", "bp-kotakbk", "kotakbk", "kotak bank",
            "vm-sbimb", "bp-sbimb", "sbimb", "sbi bank", "sbiinb",
            "vm-pnbsms", "bp-pnbsms", "pnbsms", "pnb bank",
            "vm-unionbk", "bp-unionbk", "unionbk", "union bank",
            "vm-canarabk", "bp-canarabk", "canarabk", "canara bank",
            "vm-bobsms", "bp-bobsms", "bobsms", "bank of baroda",
            "vm-idbibank", "bp-idbibank", "idbibank", "idbi bank",
            "vm-yesbank", "bp-yesbank", "yesbank", "yes bank",
            "vm-rblbank", "bp-rblbank", "rblbank", "rbl bank",
            "vm-indusbk", "bp-indusbk", "indusbk", "indusind bank",
            "vm-federal", "bp-federal", "federal", "federal bank",
            "majeed bhaiya"
        )

        // UPI and payment app senders
        private val TRUSTED_PAYMENT_SENDERS = listOf(
            "paytm", "phonepe", "gpay", "googlepay", "bhim", "amazonpay",
            "mobikwik", "freecharge", "payzapp", "airtel money"
        )

        // Promotional keywords that indicate non-transactional messages
        private val PROMOTIONAL_KEYWORDS = listOf(
            "offer", "discount", "cashback offer", "reward points", "scheme",
            "free", "special offer", "limited time", "hurry", "expires",
            "activate now", "click here", "visit", "download", "install",
            "upgrade", "new feature", "congratulations", "winner", "prize",
            "lottery", "lucky", "bonus offer", "gift", "voucher available",
            "promocode", "coupon", "deal", "sale", "% off", "₹ off",
            "minimum transaction", "cashback upto", "get upto", "earn upto",
            "validity", "t&c apply", "terms and conditions", "promo",
            "advertisement", "marketing", "campaign", "festive offer",
            "holiday offer", "season sale", "mega sale", "flash sale",
            "exclusive offer", "member offer", "invitation", "join now",
            "register now", "sign up", "subscription", "plan", "package",
            "service update", "maintenance", "scheduled", "temporarily",
            "unavailable", "disruption", "notice", "announcement",
            "reminder", "due date", "expiry", "renewal", "auto renewal",
            "setup autopay", "enable autopay", "upi autopay", "mandate",
            "standing instruction", "si", "ecs", "nach", "auto debit",
            "bill reminder", "payment reminder", "overdue", "late fee",
            "penalty", "charges applicable"
        )

        // Spam/promotional patterns in sender names
        private val SPAM_SENDER_PATTERNS = listOf(
            "promo", "offer", "deals", "sale", "marketing", "ads",
            "notify", "alert" // Generic alert senders are often promotional
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

        // FIRST: Check if this is a promotional/spam message
        if (isPromotionalMessage(sender, messageBody)) {
            Log.i(TAG, "🚫 Promotional/spam message detected. Skipping processing.")
            return@withContext Result.success()
        }

        // SECOND: Validate if sender is from trusted source (UPDATED)
        if (!isTrustedSender(sender, messageBody)) {
            Log.i(TAG, "🚫 Untrusted sender detected. Skipping processing.")
            return@withContext Result.success()
        }

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

                // THIRD: Additional validation - check if this looks like a real transaction
                if (!isValidTransaction(messageBody, amount, type)) {
                    Log.i(TAG, "🚫 Invalid transaction pattern detected. Skipping.")
                    return@withContext Result.success()
                }

                // Enhanced duplicate detection using DuplicateDetectionManager
                if (duplicateDetectionManager.isDuplicateTransaction(amount, type, timestamp, sender, messageBody)) {
                    Log.i(TAG, "⚠️ Duplicate transaction detected by DuplicateDetectionManager. Skipping to avoid double entry.")
                    return@withContext Result.success()
                }

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

                // Record transaction for duplicate detection using DuplicateDetectionManager
                duplicateDetectionManager.recordTransaction(amount, type, timestamp, sender, messageBody)

                // Mark as processed
                markAsProcessed(smsHash)

                // Cleanup old entries periodically
                duplicateDetectionManager.cleanupOldEntries()

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

    /**
     * Enhanced promotional message detection
     */
    private fun isPromotionalMessage(sender: String, messageBody: String): Boolean {
        val lowerSender = sender.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Check for spam sender patterns
        if (SPAM_SENDER_PATTERNS.any { lowerSender.contains(it) }) {
            Log.d(TAG, "Spam sender pattern detected in: $sender")
            return true
        }

        // Count promotional keywords
        val promoKeywordCount = PROMOTIONAL_KEYWORDS.count { keyword ->
            lowerBody.contains(keyword)
        }

        // If message contains multiple promotional keywords, it's likely promotional
        if (promoKeywordCount >= 2) {
            Log.d(TAG, "Multiple promotional keywords detected ($promoKeywordCount): $messageBody")
            return true
        }

        // Specific patterns that indicate promotional content
        val promoPatterns = listOf(
            // URLs and links
            """https?://[^\s]+""".toRegex(),
            """www\.[^\s]+""".toRegex(),
            """[a-z0-9]+\.in/[^\s]+""".toRegex(),

            // Click/action prompts
            """click here""".toRegex(RegexOption.IGNORE_CASE),
            """tap here""".toRegex(RegexOption.IGNORE_CASE),
            """visit [^\s]+""".toRegex(RegexOption.IGNORE_CASE),
            """download [^\s]+""".toRegex(RegexOption.IGNORE_CASE),

            // Promotional language
            """get \d+% (off|cashback)""".toRegex(RegexOption.IGNORE_CASE),
            """upto .*% off""".toRegex(RegexOption.IGNORE_CASE),
            """minimum .*₹\d+""".toRegex(RegexOption.IGNORE_CASE),
            """valid (till|until)""".toRegex(RegexOption.IGNORE_CASE),
            """offer expires""".toRegex(RegexOption.IGNORE_CASE),

            // Setup/activation prompts (like your Hathway example)
            """set up .* in \d+ click""".toRegex(RegexOption.IGNORE_CASE),
            """activate now""".toRegex(RegexOption.IGNORE_CASE),
            """ac no \d+""".toRegex(RegexOption.IGNORE_CASE), // Account number for service activation

            // Service-related promotional messages
            """don't let .* stop""".toRegex(RegexOption.IGNORE_CASE),
            """enjoy uninterrupted""".toRegex(RegexOption.IGNORE_CASE),
            """prepaid pack at just""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in promoPatterns) {
            if (pattern.containsMatchIn(lowerBody)) {
                Log.d(TAG, "Promotional pattern detected: ${pattern.pattern}")
                return true
            }
        }

        // Check for service activation messages (like Jio prepaid pack)
        if (lowerBody.contains("prepaid pack") && lowerBody.contains("just ₹")) {
            Log.d(TAG, "Service promotional message detected")
            return true
        }

        return false
    }

    /**
     * Check if sender is from trusted source
     */
    private fun isTrustedSender(sender: String, messageBody: String): Boolean {
        val lowerSender = sender.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Check against trusted bank senders (original logic)
        val isTrustedBank = TRUSTED_BANK_SENDERS.any { trustedSender ->
            lowerSender.contains(trustedSender) ||
                    lowerSender == trustedSender ||
                    lowerSender.startsWith(trustedSender)
        }

        // Check against trusted payment app senders (original logic)
        val isTrustedPayment = TRUSTED_PAYMENT_SENDERS.any { trustedSender ->
            lowerSender.contains(trustedSender) ||
                    lowerSender == trustedSender
        }

        // NEW: Content-based trust detection for forwarded/contact SMS
        val hasValidBankContent = hasValidBankTransactionContent(lowerBody)

        val isTrusted = isTrustedBank || isTrustedPayment || hasValidBankContent

        Log.d(TAG, "Sender trust check - $sender: ${if (isTrusted) "TRUSTED" else "UNTRUSTED"}")
        if (hasValidBankContent && !isTrustedBank && !isTrustedPayment) {
            Log.d(TAG, "Trusted based on valid bank content in message body")
        }

        return isTrusted
    }

    /**
     * Check if message content indicates a legitimate bank transaction
     */
    private fun hasValidBankTransactionContent(messageBody: String): Boolean {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Must have bank identifier in content
        val bankContentPatterns = listOf(
            "from hdfc bank", "from icici bank", "from axis bank", "from sbi bank",
            "from kotak bank", "from yes bank", "from rbl bank", "from pnb bank",
            "hdfc bank a/c", "icici bank a/c", "axis bank a/c", "sbi a/c",
            "kotak a/c", "yes bank a/c", "rbl bank a/c", "pnb a/c",
            "state bank of india", "punjab national bank", "canara bank",
            "bank of baroda", "union bank", "federal bank", "indusind bank"
        )

        val hasBankIdentifier = bankContentPatterns.any { pattern ->
            lowerBody.contains(pattern)
        }

        if (!hasBankIdentifier) {
            return false
        }

        // Must have transaction indicators
        val transactionIndicators = listOf(
            "sent rs", "sent inr", "sent ₹", "debited", "credited",
            "payment to", "payment from", "transferred to", "transferred from",
            "upi", "imps", "neft", "rtgs", "transaction", "txn"
        )

        val hasTransactionIndicator = transactionIndicators.any { indicator ->
            lowerBody.contains(indicator)
        }

        if (!hasTransactionIndicator) {
            return false
        }

        // Must have reference number (indicates genuine bank SMS)
        val hasRefNumber = lowerBody.contains("ref") &&
                Regex("""ref\s*\d+""").containsMatchIn(lowerBody)

        // Must have account number pattern
        val hasAccountPattern = Regex("""a/c\s*[*x]*\d{3,6}""").containsMatchIn(lowerBody)

        // Must have amount pattern
        val hasAmountPattern = Regex("""rs\.?\s*\d+(\.\d{2})?""").containsMatchIn(lowerBody)

        val isValidBankTransaction = hasRefNumber && hasAccountPattern && hasAmountPattern

        Log.d(TAG, "Bank content validation - Bank ID: $hasBankIdentifier, " +
                "Transaction: $hasTransactionIndicator, Ref: $hasRefNumber, " +
                "Account: $hasAccountPattern, Amount: $hasAmountPattern")

        return isValidBankTransaction
    }

    /**
     * Additional validation to ensure this is a real transaction
     */

    private fun isValidTransaction(messageBody: String, amount: Double, type: TransactionType): Boolean {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Must contain clear transaction indicators
        val transactionIndicators = listOf(
            // Debit indicators
            "debited", "debit alert", "spent", "paid", "withdrawal",
            "purchase", "txn at", "transaction at", "transferred to",
            "payment to", "sent to", "dr ", "dr.", "sent",

            // Credit indicators
            "credited", "credit alert", "received", "deposited",
            "payment from", "transferred from", "refund", "cashback",
            "cr ", "cr.", "salary"
        )

        val hasTransactionIndicator = transactionIndicators.any { indicator ->
            lowerBody.contains(indicator)
        }

        if (!hasTransactionIndicator) {
            Log.d(TAG, "No clear transaction indicator found")
            return false
        }

        // Amount should be reasonable (not too small for promotional offers)
        if (amount < 1.0) {
            Log.d(TAG, "Amount too small: $amount")
            return false
        }

        // Should not contain balance information without transaction
        if (lowerBody.contains("balance") && !lowerBody.contains("debited") && !lowerBody.contains("credited")) {
            // This might be just a balance inquiry
            if (!lowerBody.contains("transaction") && !lowerBody.contains("txn")) {
                Log.d(TAG, "Appears to be balance inquiry, not transaction")
                return false
            }
        }

        // Should not be OTP or verification messages
        if (lowerBody.contains("otp") || lowerBody.contains("verification") ||
            lowerBody.contains("verify") || lowerBody.contains("code")) {
            Log.d(TAG, "Appears to be OTP/verification message")
            return false
        }

        return true
    }


    private data class ParsedDetails(
        val amount: Double,
        val type: TransactionType,
        val merchant: String,
        val notes: String,
        val paymentMode: String,
        val accountHint: String? = null
    )

    // Enhanced parseTransactionDetails method to handle credit alerts
    private fun parseTransactionDetails(messageBody: String, sender: String): ParsedDetails? {
        val lowerBody = messageBody.lowercase(Locale.ROOT)
        val originalBody = messageBody

        // Enhanced transaction type detection with credit alert patterns
        val type: TransactionType = when {
            // DEBIT/EXPENSE patterns - comprehensive
            listOf(
                "debit alert", "debit:", "debited", "spent", "paid", "payment to",
                "withdrawal", "purchase", "txn at", "transaction at", "bought",
                "transferred to", "sent to", "sent rs", "sent inr", "sent ₹",
                "dr ", "dr.", "withdrawal from", "paid to", "outgoing", "deducted",
                "charged", "bill payment", "emi", "autopay", "money sent",
                "amount sent", "transferred", "send money"
            ).any { lowerBody.contains(it) } -> TransactionType.EXPENSE

            // CREDIT/INCOME patterns - comprehensive (including credit alert)
            listOf(
                "credit alert", "credit:", "credited", "received", "deposited",
                "payment from", "refund", "cashback", "interest", "salary",
                "cr ", "cr.", "received from", "transferred from", "deposit",
                "incoming", "added", "bonus", "reward", "dividend", "commission",
                "money received", "amount received", "credited to", "credit to"
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

    // Enhanced amount extraction for credit alert format
    private fun extractAmount(messageBody: String): Double? {
        // Multiple regex patterns for amount extraction - enhanced for credit alerts
        val amountRegexes = listOf(
            // Pattern for Credit Alert format: "Rs.5.00 credited"
            """rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+credited""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 1: Currency symbols with amount
            """(?:rs\.?\s*|inr\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 2: Amount with currency symbols
            """([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 3: Transaction type with amount
            """(?:debit|credit|dr|cr):\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 4: Credit Alert specific
            """credit alert!\s*rs\.?\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 5: Amount keywords
            """(?:amount|amt|sum)\s*(?:of)?\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 6: UPI format amounts
            """(?:upi|imps|neft)\s*(?:.*?)\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 7: Balance format (Bal: Rs 1000.00)
            """bal:\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 8: Generic number before transaction words
            """([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|sent|received|spent|paid|purchase|withdraw|transfer)""".toRegex(RegexOption.IGNORE_CASE),

            // Pattern 9: Standalone amounts in transaction context - but avoid promotional amounts
            """\b([\d,]+\.\d{2})\b""".toRegex(),

            // Pattern 10: Just numbers with proper validation
            """\b([\d,]+)\b""".toRegex()
        )

        for (regex in amountRegexes) {
            val match = regex.find(messageBody)
            if (match != null) {
                val amountString = match.groups[1]?.value?.replace(",", "")
                val amount = amountString?.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    // Additional validation - avoid account numbers being treated as amounts
                    if (amount > 1000000) { // Amounts over 10 lakh are suspicious
                        continue
                    }

                    Log.d(TAG, "Amount extracted: $amount using pattern: ${regex.pattern}")
                    return amount
                }
            }
        }

        Log.d(TAG, "No amount found in message: $messageBody")
        return null
    }

    // Enhanced merchant extraction method for SmsProcessingWorker.kt
    private fun extractMerchant(messageBody: String, sender: String, type: TransactionType): String {
        var merchant = "Unknown"
        val lowerBody = messageBody.lowercase(Locale.ROOT)
        val originalBody = messageBody

        Log.d(TAG, "Extracting merchant from: $messageBody")
        Log.d(TAG, "Transaction type: $type")

        // Enhanced merchant patterns - ORDER MATTERS (most specific first)
        val merchantPatterns = when (type) {
            TransactionType.EXPENSE -> listOf(
                // Pattern 1: "To MERCHANT_NAME" (most common in UPI debit/expense transactions)
                """(?:to|paid to|sent to|payment to|transferred to)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|upi|ref|txn|ac|a/c|\d{2}/\d{2}/\d{2,4}|$))""".toRegex(),

                // Pattern 2: UPI transaction format "UPI/REF_NUM/MERCHANT/..."
                """UPI/[^/]+/([A-Z][A-Z\s&.'-]+?)(?:/|\s|$)""".toRegex(),

                // Pattern 3: Transaction at merchant
                """(?:txn at|transaction at|spent at|purchase at|bought at)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|\d{2}/\d{2}|$))""".toRegex(),

                // Pattern 4: Debited for/charged for
                """(?:debited for|charged for|spent on|paid for)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|at|$))""".toRegex(RegexOption.IGNORE_CASE),

                // Pattern 5: VPA pattern for expenses - extract name from recipient VPA
                """(?:to VPA|sent to)\s+([A-Za-z][A-Za-z0-9._-]*?)@[A-Za-z0-9.-]+""".toRegex(RegexOption.IGNORE_CASE),

                // Pattern 6: Info: MERCHANT format (but avoid bank names)
                """Info\s*:\s*([A-Z][A-Z\s&.'-]+?)(?:\s|$)""".toRegex(RegexOption.IGNORE_CASE)
            )

            TransactionType.INCOME -> listOf(
                // Pattern 1: "From MERCHANT_NAME" (for credit/income transactions)
                """(?:from|received from|credited from|payment from|transferred from)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|upi|ref|txn|ac|a/c|to|$))""".toRegex(),

                // Pattern 2: VPA pattern for income - extract name from sender VPA
                """(?:from VPA|received from)\s+([A-Za-z][A-Za-z0-9._-]*?)@[A-Za-z0-9.-]+""".toRegex(RegexOption.IGNORE_CASE),

                // Pattern 3: Credited by/for
                """(?:credited by|credited for|received for)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|$))""".toRegex(RegexOption.IGNORE_CASE),

                // Pattern 4: Salary/wage patterns
                """(?:salary from|wage from|payment from)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|$))""".toRegex(RegexOption.IGNORE_CASE)
            )
        }

        // Try each pattern in order
        for (pattern in merchantPatterns) {
            val merchantMatch = pattern.find(originalBody)
            if (merchantMatch != null) {
                val extractedMerchant = merchantMatch.groups[1]?.value?.trim()

                if (!extractedMerchant.isNullOrBlank()) {
                    val cleaned = cleanMerchantName(extractedMerchant)

                    // Additional validation: Skip if it's a bank name (for expense transactions)
                    if (type == TransactionType.EXPENSE && isBankName(cleaned)) {
                        Log.d(TAG, "Skipping bank name as merchant for expense: '$cleaned'")
                        continue
                    }

                    if (isValidMerchantName(cleaned)) {
                        merchant = cleaned
                        Log.d(TAG, "Merchant extracted using pattern ${pattern.pattern}: '$merchant'")
                        break
                    }
                }
            }
        }

        // Special handling for multi-line SMS with clear "To" indicators
        if (merchant == "Unknown" && type == TransactionType.EXPENSE) {
            // Look for "To" followed by merchant name on separate lines or with clear formatting
            val toPatterns = listOf(
                """(?:^|\n)\s*To\s+([A-Z][A-Z\s&.'-]+?)(?:\s*$|\n)""".toRegex(RegexOption.MULTILINE),
                """To\s+([A-Z][A-Z\s&.'-]{3,50}?)(?:\s+On|\s+Ref|\s*$)""".toRegex()
            )

            for (pattern in toPatterns) {
                val match = pattern.find(originalBody)
                if (match != null) {
                    val extractedMerchant = match.groups[1]?.value?.trim()
                    if (!extractedMerchant.isNullOrBlank()) {
                        val cleaned = cleanMerchantName(extractedMerchant)
                        if (isValidMerchantName(cleaned) && !isBankName(cleaned)) {
                            merchant = cleaned
                            Log.d(TAG, "Merchant extracted using 'To' pattern: '$merchant'")
                            break
                        }
                    }
                }
            }
        }

        // Generic VPA pattern as fallback (but not for bank senders)
        if (merchant == "Unknown" && !isGenericBankSender(sender.lowercase(Locale.ROOT))) {
            val vpaPattern = """([A-Za-z][A-Za-z0-9._-]{2,})@[A-Za-z0-9.-]+""".toRegex()
            val vpaMatch = vpaPattern.find(originalBody)
            if (vpaMatch != null) {
                val vpaUsername = vpaMatch.groups[1]?.value
                if (!vpaUsername.isNullOrBlank()) {
                    val formatted = formatVpaUsername(vpaUsername)
                    if (isValidMerchantName(formatted)) {
                        merchant = formatted
                        Log.d(TAG, "Merchant extracted from VPA: '$merchant'")
                    }
                }
            }
        }

        // Fallback: If no merchant found and sender is not generic bank, use cleaned sender
        if (merchant == "Unknown" && !isGenericBankSender(sender.lowercase(Locale.ROOT))) {
            val cleanedSender = cleanSenderName(sender)
            if (isValidMerchantName(cleanedSender) && !isBankName(cleanedSender)) {
                merchant = cleanedSender
                Log.d(TAG, "Using cleaned sender as merchant: '$merchant'")
            }
        }

        Log.d(TAG, "Final merchant: '$merchant'")
        return merchant
    }

    /**
     * Check if the given name is a bank name (to avoid using bank as merchant for expenses)
     */
    private fun isBankName(name: String): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val bankKeywords = listOf(
            "bank", "hdfc", "icici", "axis", "sbi", "kotak", "yes bank", "rbl",
            "pnb", "union bank", "canara", "baroda", "idbi", "indusind", "federal",
            "state bank", "punjab national", "bank of baroda", "hdfc bank",
            "icici bank", "axis bank", "kotak bank"
        )

        return bankKeywords.any { keyword ->
            lowerName.contains(keyword) && lowerName.length < 25 // Avoid false positives with longer names
        }
    }

    /**
     * Enhanced merchant name cleaning with better bank name filtering
     */
    private fun cleanMerchantName(rawMerchant: String): String {
        var cleaned = rawMerchant.trim()

        Log.d(TAG, "Cleaning merchant name: '$rawMerchant'")

        // Remove common suffixes and patterns first
        cleaned = cleaned.removeSuffix(".").removeSuffix(",").trim()

        // Remove "via" patterns
        cleaned = cleaned.replace(Regex("""\s+via\s+.*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove date patterns (19/05/25, 19-05-2025, etc.)
        cleaned = cleaned.replace(Regex("""\s+(?:on\s+)?\d{1,2}[-/]\d{1,2}[-/](?:\d{2}|\d{4}).*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove reference patterns
        cleaned = cleaned.replace(Regex("""\s+(?:ref|reference|txn|transaction)\s*(?:no\.?|id|num)?\s*:?\s*\d+.*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove balance info
        cleaned = cleaned.replace(Regex("""\s+(?:avbl|avl|available)\s+bal.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+bal\s*:.*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove account info patterns (but be more careful with A/C)
        cleaned = cleaned.replace(Regex("""\s+a/c\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+account\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()

        // Remove card info
        cleaned = cleaned.replace(Regex("""\s+card\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()

        // Remove phone number patterns
        cleaned = cleaned.replace(Regex("""\s+\d{10}"""), "").trim()
        cleaned = cleaned.replace(Regex("""\s+\+\d{1,3}\s*\d{10}"""), "").trim()

        // Remove UPI reference numbers in parentheses
        cleaned = cleaned.replace(Regex("""\s*\(UPI\s+\d+\).*""", RegexOption.IGNORE_CASE), "").trim()

        // Remove "Not You?" and similar security messages
        cleaned = cleaned.replace(Regex("""\s+not you\?.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+call\s+\d+.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+sms\s+block.*""", RegexOption.IGNORE_CASE), "").trim()

        // Handle VPA usernames (like "stanleyxavier2003" -> "Stanley Xavier")
        if (cleaned.matches(Regex("^[a-z]+[a-z0-9]*\\d*$", RegexOption.IGNORE_CASE))) {
            cleaned = formatVpaUsername(cleaned)
        }

        // Clean up multiple spaces and special characters
        cleaned = cleaned.replace(Regex("""\s{2,}"""), " ").trim()
        cleaned = cleaned.replace(Regex("""[*]{2,}"""), " ").trim()

        // Proper case formatting for merchant names
        if (cleaned.isNotBlank() && cleaned.length > 1) {
            // Don't change case if it's already properly formatted (mixed case)
            if (cleaned == cleaned.uppercase() || cleaned == cleaned.lowercase()) {
                cleaned = cleaned.split(" ")
                    .joinToString(" ") { word ->
                        if (word.length > 1) {
                            // Handle abbreviations (like "LTD", "PVT", "CO")
                            if (word.uppercase() in listOf("LTD", "PVT", "CO", "INC", "LLC", "CORP", "PTE", "ACCT")) {
                                word.uppercase()
                            } else {
                                word.lowercase().replaceFirstChar { it.uppercase() }
                            }
                        } else {
                            word.uppercase()
                        }
                    }
            }
        }

        // Truncate if too long
        if (cleaned.length > 50) {
            cleaned = cleaned.substring(0, 50).trim()
            if (cleaned.endsWith(" ")) {
                cleaned = cleaned.trim() + "..."
            }
        }

        Log.d(TAG, "Cleaned merchant name: '$rawMerchant' -> '$cleaned'")
        return cleaned.ifBlank { "Unknown" }
    }

    /**
     * Validate if the extracted text is a valid merchant name
     */
    private fun isValidMerchantName(merchantName: String): Boolean {
        if (merchantName.isBlank() || merchantName == "Unknown") {
            return false
        }

        // Should not be purely numeric (likely account number or phone number)
        if (merchantName.matches(Regex("^\\d+$"))) {
            Log.d(TAG, "Rejected merchant (purely numeric): '$merchantName'")
            return false
        }

        // Should not be too short (less than 2 characters)
        if (merchantName.length < 2) {
            Log.d(TAG, "Rejected merchant (too short): '$merchantName'")
            return false
        }

        // Should not contain only special characters
        if (!merchantName.matches(Regex(".*[A-Za-z].*"))) {
            Log.d(TAG, "Rejected merchant (no letters): '$merchantName'")
            return false
        }

        // Should not be common non-merchant patterns
        val invalidPatterns = listOf(
            "ref", "reference", "txn", "transaction", "upi", "imps", "neft", "rtgs",
            "debit", "credit", "balance", "bal", "avbl", "avl", "account", "a/c",
            "card", "ending", "xxxx", "****"
        )

        if (invalidPatterns.any { merchantName.lowercase().contains(it) } &&
            merchantName.length < 10) { // Allow longer names that might contain these words
            Log.d(TAG, "Rejected merchant (invalid pattern): '$merchantName'")
            return false
        }

        return true
    }

    /**
     * Format VPA username to proper name
     */
    private fun formatVpaUsername(username: String): String {
        // Handle common patterns like "stanleyxavier2003" or "john.doe123"
        val cleaned = username.replace(Regex("\\d+$"), "") // Remove trailing numbers
            .replace(".", " ") // Replace dots with spaces
            .replace("_", " ") // Replace underscores with spaces

        // Try to split camelCase or combined names
        val namePattern = """([a-z]+)([A-Z][a-z]+)""".toRegex()
        val match = namePattern.find(cleaned)

        return if (match != null) {
            val firstName = match.groups[1]?.value?.replaceFirstChar { it.uppercase() } ?: ""
            val lastName = match.groups[2]?.value ?: ""
            "$firstName $lastName"
        } else {
            // Simple word splitting and capitalization
            cleaned.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
        }
    }

    /**
     * Enhanced sender name cleaning
     */
    private fun cleanSenderName(sender: String): String {
        var cleaned = sender.trim()

        // Remove common prefixes
        cleaned = cleaned.replace(Regex("""^(?:VM-|BP-|AX-|AD-|TM-|JK-|SB-|HD-)""", RegexOption.IGNORE_CASE), "")

        // Bank name mappings
        val bankNameMap = mapOf(
            "HDFCBK" to "HDFC Bank",
            "ICICIBK" to "ICICI Bank",
            "AXISBK" to "Axis Bank",
            "SBIMB" to "SBI Bank",
            "KOTAKBK" to "Kotak Bank",
            "YESBANK" to "Yes Bank",
            "RBLBANK" to "RBL Bank",
            "PNBSMS" to "PNB Bank",
            "UNIONBK" to "Union Bank",
            "CANARABK" to "Canara Bank",
            "BOBSMS" to "Bank of Baroda",
            "IDBIBANK" to "IDBI Bank",
            "INDUSBK" to "IndusInd Bank",
            "FEDERAL" to "Federal Bank"
        )

        // Apply bank name mapping
        val upperCleaned = cleaned.uppercase()
        bankNameMap.forEach { (key, value) ->
            if (upperCleaned.contains(key)) {
                cleaned = value
                return@forEach
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

    // Enhanced account hint extraction
    private fun extractAccountHint(messageBody: String): String? {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Enhanced patterns for account hint extraction
        val accPatterns = listOf(
            // Standard account patterns
            """a/c\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """account\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{4})""".toRegex(RegexOption.IGNORE_CASE),

            // Additional patterns for different formats
            """a/c\s*x+(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """ac\s*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """account\s*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),

            // Credit card patterns
            """card\s*ending\s*(\d{4})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*x+(\d{4})""".toRegex(RegexOption.IGNORE_CASE),

            // Bank-specific patterns
            """(?:hdfc|icici|axis|sbi|kotak)\s*a/c\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in accPatterns) {
            val match = pattern.find(lowerBody)
            if (match != null) {
                val hint = match.groups[1]?.value
                if (!hint.isNullOrBlank()) {
                    Log.d(TAG, "Extracted account hint: $hint using pattern: ${pattern.pattern}")
                    return hint
                }
            }
        }

        Log.d(TAG, "No account hint found in message")
        return null
    }

    // Enhanced determineAccount function for SmsProcessingWorker.kt
    private suspend fun determineAccount(messageBody: String, sender: String, accountHint: String?): Account? {
        val accounts = accountRepository.getAllAccounts().firstOrNull() ?: emptyList()

        if (accounts.isEmpty()) {
            Log.w(TAG, "No accounts found in the system")
            return null
        }

        Log.d(TAG, "Determining account for sender: $sender, hint: $accountHint")
        Log.d(TAG, "Available accounts: ${accounts.map { "${it.name} (${it.accountNumber})" }}")

        // Strategy 1: Use account hint if available (most reliable)
        if (!accountHint.isNullOrBlank()) {
            Log.d(TAG, "Trying to match account hint: $accountHint")

            // Try exact match first
            var foundAccount = accounts.find { account ->
                account.accountNumber?.endsWith(accountHint) == true
            }

            // Try partial match
            if (foundAccount == null) {
                foundAccount = accounts.find { account ->
                    account.accountNumber?.contains(accountHint, ignoreCase = true) == true ||
                            account.name.contains(accountHint, ignoreCase = true)
                }
            }

            if (foundAccount != null) {
                Log.i(TAG, "✅ Matched account by hint: ${foundAccount.name} (${foundAccount.accountNumber})")
                return foundAccount
            }
            Log.d(TAG, "No account matched the hint: $accountHint")
        }

        // Strategy 2: Enhanced sender-based mapping
        val senderUpper = sender.uppercase()
        Log.d(TAG, "Trying sender-based mapping for: $senderUpper")

        // Enhanced sender mappings with more variations
        val senderMappings = mapOf(
            "HDFCBK" to listOf("hdfc", "hdfc bank", "hdfc savings", "hdfc current", "hd"),
            "ICICIBK" to listOf("icici", "icici bank", "icici savings", "icici current", "ic"),
            "AXISBK" to listOf("axis", "axis bank", "axis savings", "axis current", "ax"),
            "SBIMB" to listOf("sbi", "sbi bank", "state bank", "state bank of india", "sb"),
            "KOTAKBK" to listOf("kotak", "kotak bank", "kotak mahindra", "kt"),
            "YESBANK" to listOf("yes", "yes bank", "yb"),
            "RBLBANK" to listOf("rbl", "rbl bank"),
            "PNBSMS" to listOf("pnb", "punjab national bank", "punjab"),
            "UNIONBK" to listOf("union", "union bank"),
            "CANARABK" to listOf("canara", "canara bank"),
            "BOBSMS" to listOf("bob", "bank of baroda", "baroda"),
            "IDBIBANK" to listOf("idbi", "idbi bank"),
            "INDUSBK" to listOf("indus", "indusind", "indusind bank"),
            "FEDERAL" to listOf("federal", "federal bank"),
            "PAYTM" to listOf("paytm", "paytm wallet", "paytm payments bank"),
            "PHONEPE" to listOf("phonepe", "phone pe", "pp"),
            "GPAY" to listOf("gpay", "google pay", "googlepay"),
            "AMAZON" to listOf("amazon", "amazon pay", "amazonpay"),
            "BHIM" to listOf("bhim", "bhim upi"),
            "MOBIKWIK" to listOf("mobikwik", "mobikwik wallet")
        )

        // Try exact sender key match first
        senderMappings.forEach { (senderKey, accountKeywords) ->
            if (senderUpper.contains(senderKey) || senderUpper == senderKey) {
                val matchedAccount = accounts.find { account ->
                    accountKeywords.any { keyword ->
                        account.name.contains(keyword, ignoreCase = true) ||
                                account.type.contains(keyword, ignoreCase = true)
                    }
                }
                if (matchedAccount != null) {
                    Log.i(TAG, "✅ Matched account by sender mapping: ${matchedAccount.name} for sender key: $senderKey")
                    return matchedAccount
                }
            }
        }

        // Try fuzzy matching for sender prefixes (VM-, BP-, etc.)
        val cleanSender = senderUpper.replace(Regex("^(VM-|BP-|AX-|AD-|TM-|JK-|SB-|HD-)"), "")
        if (cleanSender != senderUpper) {
            Log.d(TAG, "Trying fuzzy match with cleaned sender: $cleanSender")
            senderMappings.forEach { (senderKey, accountKeywords) ->
                if (cleanSender.contains(senderKey)) {
                    val matchedAccount = accounts.find { account ->
                        accountKeywords.any { keyword ->
                            account.name.contains(keyword, ignoreCase = true)
                        }
                    }
                    if (matchedAccount != null) {
                        Log.i(TAG, "✅ Matched account by fuzzy sender mapping: ${matchedAccount.name}")
                        return matchedAccount
                    }
                }
            }
        }

        // Strategy 3: Check message body for account clues
        Log.d(TAG, "Checking message body for account clues")
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        accounts.forEach { account ->
            // Check if account name appears in message
            if (account.name.isNotBlank() && lowerBody.contains(account.name.lowercase())) {
                Log.i(TAG, "✅ Matched account by name in message: ${account.name}")
                return account
            }

            // Check if account number appears in message
            if (!account.accountNumber.isNullOrBlank() &&
                (lowerBody.contains(account.accountNumber!!) ||
                        lowerBody.contains("x${account.accountNumber!!.takeLast(4)}"))) {
                Log.i(TAG, "✅ Matched account by number in message: ${account.name}")
                return account
            }
        }

        // Strategy 4: Default SMS account from preferences
        val sharedPrefs = applicationContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val defaultAccountId = sharedPrefs.getLong("defaultSmsAccountId", -1L)

        if (defaultAccountId != -1L) {
            val defaultAccount = accounts.find { it.accountId == defaultAccountId }
            if (defaultAccount != null) {
                Log.i(TAG, "✅ Using default SMS account: ${defaultAccount.name}")
                return defaultAccount
            } else {
                Log.w(TAG, "Default SMS account ID $defaultAccountId not found in current accounts")
            }
        }

        // Strategy 5: If only one account exists, use it
        if (accounts.size == 1) {
            Log.i(TAG, "✅ Only one account exists, using: ${accounts[0].name}")
            return accounts[0]
        }

        // Strategy 6: Try to find the most recently used account for similar transactions
        Log.d(TAG, "Trying to find most recently used account for similar sender")
        try {
            val recentTransactions = transactionRepository.getRecentTransactionsBySender(sender, 10)
            val recentAccountIds = recentTransactions.mapNotNull { it.accountId }.distinct()

            if (recentAccountIds.isNotEmpty()) {
                val recentAccount = accounts.find { it.accountId == recentAccountIds.first() }
                if (recentAccount != null) {
                    Log.i(TAG, "✅ Using most recently used account for this sender: ${recentAccount.name}")
                    return recentAccount
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching recent transactions: ${e.message}")
        }

        Log.w(TAG, "❌ Could not determine account for sender: $sender")
        Log.w(TAG, "Available accounts: ${accounts.joinToString(", ") { "${it.name} (${it.accountNumber ?: "no number"})" }}")

        return null
    }

    // Enhanced category determination with proper ID mapping
    private suspend fun determineCategory(merchant: String, messageBody: String, type: TransactionType): Long? {
        val lowerMerchant = merchant.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        // Get all categories from repository
        val categories = categoryRepository.getAllCategories().firstOrNull() ?: return null

        if (type == TransactionType.EXPENSE) {
            when {
                // Food & Dining
                listOf("zomato", "swiggy", "ubereats", "dominos", "pizza", "restaurant",
                    "food", "cafe", "starbucks", "kfc", "mcdonald", "burger", "biryani",
                    "dining", "eatery", "kitchen", "cook", "meal", "lunch", "dinner",
                    "breakfast", "snack", "bakery", "sweet", "juice", "coffee", "tea").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Food & Dining" }?.categoryId

                // Groceries (separate from general shopping)
                listOf("grocery", "supermarket", "vegetables", "fruits", "milk", "bread",
                    "rice", "dal", "oil", "spices", "provisions", "kirana", "general store",
                    "bigbasket", "grofers", "blinkit", "dunzo", "zepto", "instamart").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Groceries" }?.categoryId

                // Transportation
                listOf("ola", "uber", "taxi", "fuel", "petrol", "diesel", "metro",
                    "bus", "auto", "rickshaw", "rapido", "bmtc", "delhi metro", "mumbai local",
                    "cab", "transport", "parking", "toll", "fastag", "travel card").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Transportation" }?.categoryId

                // Shopping (general merchandise)
                listOf("amazon", "flipkart", "myntra", "ajio", "shopping", "mall",
                    "store", "mart", "bazaar", "online", "ecommerce", "fashion",
                    "clothing", "electronics", "mobile", "laptop", "gadget").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Shopping" }?.categoryId

                // Bills & Utilities
                listOf("bill", "recharge", "electricity", "gas", "water", "broadband",
                    "internet", "mobile", "jio", "airtel", "vi", "bsnl", "utility",
                    "power", "energy", "telephone", "landline", "dth", "cable tv",
                    "wifi", "postpaid", "prepaid").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Bills & Utilities" }?.categoryId

                // Entertainment
                listOf("netflix", "amazon prime", "hotstar", "spotify", "movie",
                    "cinema", "ticket", "booking", "entertainment", "music", "video",
                    "streaming", "youtube", "gaming", "games", "concert", "show").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Entertainment" }?.categoryId

                // Healthcare
                listOf("hospital", "clinic", "doctor", "medical", "pharmacy", "medicine",
                    "health", "apollo", "fortis", "max", "medplus", "1mg", "pharmeasy",
                    "netmeds", "dental", "eye care", "diagnostic", "lab test").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Healthcare" }?.categoryId

                // Education
                listOf("school", "college", "university", "education", "course", "tuition",
                    "fees", "book", "library", "training", "certification", "exam",
                    "study", "learning", "coaching", "tutorial").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Education" }?.categoryId

                // Travel
                listOf("flight", "hotel", "booking", "travel", "trip", "vacation",
                    "holiday", "irctc", "train", "railway", "makemytrip", "goibibo",
                    "yatra", "cleartrip", "resort", "tourism").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Travel" }?.categoryId

                // Personal Care
                listOf("salon", "spa", "beauty", "cosmetic", "hair", "skin care",
                    "personal care", "grooming", "barber", "parlour", "wellness",
                    "massage", "facial", "manicure", "pedicure").any {
                    lowerMerchant.contains(it) || lowerBody.contains(it)
                } -> return categories.find { it.name == "Personal Care" }?.categoryId
            }
        } else if (type == TransactionType.INCOME) {
            when {
                // Salary
                listOf("salary", "wage", "pay", "employer", "payroll", "increment",
                    "bonus", "allowance", "compensation").any {
                    lowerBody.contains(it) || lowerMerchant.contains(it)
                } -> return categories.find { it.name == "Salary" }?.categoryId

                // Other Income (Interest, Cashback, etc.)
                listOf("interest", "dividend", "return", "cashback", "reward", "refund",
                    "refunded", "reversal", "credit", "earning", "commission",
                    "freelance", "consulting", "investment").any {
                    lowerBody.contains(it) || lowerMerchant.contains(it)
                } -> return categories.find { it.name == "Other Income" }?.categoryId
            }
        }

        // If no category is determined, return "Other" category
        return categories.find { it.name == "Other" }?.categoryId
    }

    // Alternative approach: Create a category mapping cache for better performance
    private var categoryCache: Map<String, Long>? = null

    private suspend fun initializeCategoryCache() {
        if (categoryCache == null) {
            val categories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()
            categoryCache = categories.associate { it.name to it.categoryId }
        }
    }

    private suspend fun determineCategoryOptimized(merchant: String, messageBody: String, type: TransactionType): Long? {
        initializeCategoryCache()
        val cache = categoryCache ?: return null

        val lowerMerchant = merchant.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        if (type == TransactionType.EXPENSE) {
            // Define keyword-to-category mappings
            val expenseKeywords = mapOf(
                "Food & Dining" to listOf("zomato", "swiggy", "ubereats", "dominos", "pizza",
                    "restaurant", "food", "cafe", "starbucks", "kfc", "mcdonald", "burger"),
                "Groceries" to listOf("grocery", "supermarket", "vegetables", "fruits", "milk",
                    "bigbasket", "grofers", "blinkit", "dunzo", "zepto", "instamart"),
                "Transportation" to listOf("ola", "uber", "taxi", "fuel", "petrol", "diesel",
                    "metro", "bus", "auto", "rickshaw", "rapido"),
                "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "shopping",
                    "mall", "store", "electronics", "fashion"),
                "Bills & Utilities" to listOf("bill", "recharge", "electricity", "gas", "water",
                    "broadband", "internet", "mobile", "jio", "airtel", "vi"),
                "Entertainment" to listOf("netflix", "amazon prime", "hotstar", "spotify",
                    "movie", "cinema", "ticket", "booking"),
                "Healthcare" to listOf("hospital", "clinic", "doctor", "medical", "pharmacy",
                    "medicine", "health", "apollo", "1mg", "pharmeasy"),
                "Education" to listOf("school", "college", "university", "education", "course",
                    "tuition", "fees", "book"),
                "Travel" to listOf("flight", "hotel", "travel", "trip", "irctc", "train",
                    "makemytrip", "goibibo"),
                "Personal Care" to listOf("salon", "spa", "beauty", "cosmetic", "hair",
                    "personal care", "grooming")
            )

            for ((categoryName, keywords) in expenseKeywords) {
                if (keywords.any { lowerMerchant.contains(it) || lowerBody.contains(it) }) {
                    return cache[categoryName]
                }
            }
        } else if (type == TransactionType.INCOME) {
            val incomeKeywords = mapOf(
                "Salary" to listOf("salary", "wage", "pay", "employer", "payroll", "bonus"),
                "Other Income" to listOf("interest", "dividend", "return", "cashback", "reward",
                    "refund", "credit", "commission", "freelance")
            )

            for ((categoryName, keywords) in incomeKeywords) {
                if (keywords.any { lowerBody.contains(it) || lowerMerchant.contains(it) }) {
                    return cache[categoryName]
                }
            }
        }

        return cache["Other"]
    }

    // Method to get category by name (helper function)
    private suspend fun getCategoryIdByName(name: String): Long? {
        return categoryRepository.getAllCategories().firstOrNull()
            ?.find { it.name == name }?.categoryId
    }

    private fun isGenericBankSender(lowerSender: String): Boolean {
        val genericPatterns = listOf("bank", "alerts", "update", "notify", "verify", "otp", "promo", "info")
        return genericPatterns.any { lowerSender.contains(it) } ||
                lowerSender.matches(Regex("^[a-z0-9]{2}-[a-z0-9]{5,10}$"))
    }

    // SMS duplicate detection methods (simplified since main duplicate detection is handled by DuplicateDetectionManager)
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