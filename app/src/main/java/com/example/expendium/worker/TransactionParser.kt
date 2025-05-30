package com.example.expendium.worker

import android.util.Log
import com.example.expendium.data.model.TransactionType
import java.util.Locale

data class ParsedDetails(
    val amount: Double,
    val type: TransactionType,
    val merchant: String,
    val notes: String,
    val paymentMode: String,
    val accountHint: String? = null
)

class TransactionParser {

    companion object {
        private const val TAG = "TransactionParser"
    }

    fun parseTransactionDetails(messageBody: String, sender: String): ParsedDetails? {
        val lowerBody = messageBody.lowercase(Locale.ROOT)
        val originalBody = messageBody

        val type: TransactionType = when {
            listOf(
                "debit alert", "debit:", "debited", "spent", "paid", "payment to",
                "withdrawal", "purchase", "txn at", "transaction at", "bought",
                "transferred to", "sent to", "sent rs", "sent inr", "sent ₹",
                "dr ", "dr.", "withdrawal from", "paid to", "outgoing", "deducted",
                "charged", "bill payment", "emi", "autopay", "money sent",
                "amount sent", "transferred", "send money", "debited for",
                "debited for payee", "debit:rs"
            ).any { lowerBody.contains(it) } -> TransactionType.EXPENSE

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

        val amount = extractAmount(originalBody) ?: run {
            Log.d(TAG, "Could not extract valid amount from: $messageBody")
            return null
        }

        val merchant = extractMerchant(originalBody, sender, type)
        val paymentMode = detectPaymentMode(originalBody, sender)
        val accountHint = extractAccountHint(originalBody)
        val notes = messageBody

        Log.d(TAG, "Parsed: Amount=₹$amount, Type=$type, Merchant='$merchant', PaymentMode='$paymentMode'")
        return ParsedDetails(amount, type, merchant, notes, paymentMode, accountHint)
    }

    private fun extractAmount(messageBody: String): Double? {
        val amountRegexes = listOf(
            """for\s+rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s""".toRegex(RegexOption.IGNORE_CASE),
            """debit:rs\.?\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+credited""".toRegex(RegexOption.IGNORE_CASE),
            """(?:rs\.?\s*|inr\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)""".toRegex(RegexOption.IGNORE_CASE),
            """(?:debit|credit|dr|cr):\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """credit alert!\s*rs\.?\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """(?:amount|amt|sum)\s*(?:of)?\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """(?:upi|imps|neft)\s*(?:.*?)\s*([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """bal:\s*(?:rs\.?\s*)?([\d,]+(?:\.\d{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
            """([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|sent|received|spent|paid|purchase|withdraw|transfer)""".toRegex(RegexOption.IGNORE_CASE),
            """\b([\d,]+\.\d{2})\b""".toRegex(),
            """\b([\d,]+)\b""".toRegex()
        )

        for ((index, regex) in amountRegexes.withIndex()) {
            val match = regex.find(messageBody)
            if (match != null) {
                val amountString = match.groups[1]?.value?.replace(",", "")
                val amount = amountString?.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    if (amount > 1000000) continue
                    if (index >= amountRegexes.size - 2 && amount > 10000 && amount.toString().length >= 8) continue
                    Log.d(TAG, "Amount extracted: $amount using pattern ${index + 1}: ${regex.pattern}")
                    return amount
                }
            }
        }

        Log.d(TAG, "No amount found in message: $messageBody")
        return null
    }

    private fun extractMerchant(messageBody: String, sender: String, type: TransactionType): String {
        var merchant = "Unknown"
        val originalBody = messageBody

        val merchantPatterns = when (type) {
            TransactionType.EXPENSE -> listOf(
                """debited for payee\s+([A-Z][A-Z\s&.'-]+?)(?:\s+for\s+rs|\s+on|\s+ref|$)""".toRegex(RegexOption.IGNORE_CASE),
                """UPI/[^/]*?/[^/]*?/([A-Z][A-Z\s&.'-]+?)(?:\s+|$)""".toRegex(),
                """(?:to|paid to|sent to|payment to|transferred to)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|upi|ref|txn|ac|a/c|\d{2}/\d{2}/\d{2,4}|for\s+rs|$))""".toRegex(),
                """UPI/[^/]+/([A-Z][A-Z\s&.'-]+?)(?:/|\s|$)""".toRegex(),
                """(?:txn at|transaction at|spent at|purchase at|bought at)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|\d{2}/\d{2}|$))""".toRegex(),
                """(?:debited for|charged for|spent on|paid for)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|at|for\s+rs|$))""".toRegex(RegexOption.IGNORE_CASE),
                """(?:to VPA|sent to)\s+([A-Za-z][A-Za-z0-9._-]*?)@[A-Za-z0-9.-]+""".toRegex(RegexOption.IGNORE_CASE),
                """Info\s*:\s*([A-Z][A-Z\s&.'-]+?)(?:\s|$)""".toRegex(RegexOption.IGNORE_CASE)
            )

            TransactionType.INCOME -> listOf(
                """(?:from|received from|credited from|payment from|transferred from)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|upi|ref|txn|ac|a/c|to|$))""".toRegex(),
                """(?:from VPA|received from)\s+([A-Za-z][A-Za-z0-9._-]*?)@[A-Za-z0-9.-]+""".toRegex(RegexOption.IGNORE_CASE),
                """(?:credited by|credited for|received for)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|$))""".toRegex(RegexOption.IGNORE_CASE),
                """(?:salary from|wage from|payment from)\s+([A-Z][A-Z\s&.'-]+?)(?:\s+(?:on|ref|txn|$))""".toRegex(RegexOption.IGNORE_CASE)
            )
        }

        for ((index, pattern) in merchantPatterns.withIndex()) {
            val merchantMatch = pattern.find(originalBody)
            if (merchantMatch != null) {
                val extractedMerchant = merchantMatch.groups[1]?.value?.trim()
                if (!extractedMerchant.isNullOrBlank()) {
                    val cleaned = cleanMerchantName(extractedMerchant)
                    if (type == TransactionType.EXPENSE && isBankName(cleaned)) continue
                    if (isValidMerchantName(cleaned)) {
                        merchant = cleaned
                        Log.d(TAG, "Merchant extracted using pattern ${index + 1}: '$merchant'")
                        break
                    }
                }
            }
        }

        if (merchant == "Unknown" && type == TransactionType.EXPENSE) {
            val abbreviatedPattern = """([A-Z]{3,}(?:\s+[A-Z]{3,})*)\s+Bal:""".toRegex()
            val abbrMatch = abbreviatedPattern.find(originalBody)
            if (abbrMatch != null) {
                val extractedMerchant = abbrMatch.groups[1]?.value?.trim()
                if (!extractedMerchant.isNullOrBlank()) {
                    val cleaned = cleanMerchantName(extractedMerchant)
                    if (isValidMerchantName(cleaned)) {
                        merchant = cleaned
                        Log.d(TAG, "Merchant extracted from abbreviated pattern: '$merchant'")
                    }
                }
            }
        }

        if (merchant == "Unknown" && type == TransactionType.EXPENSE) {
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

        val accPatterns = listOf(
            """a/c\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """account\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*(?:no\.?|number)?\s*(?:ending\s*(?:with)?|ending)?\s*x*(\d{4})""".toRegex(RegexOption.IGNORE_CASE),
            """a/c\s*x+(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """ac\s*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """account\s*(\d{3,6})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*ending\s*(\d{4})""".toRegex(RegexOption.IGNORE_CASE),
            """card\s*x+(\d{4})""".toRegex(RegexOption.IGNORE_CASE),
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

    private fun cleanMerchantName(rawMerchant: String): String {
        var cleaned = rawMerchant.trim()

        cleaned = cleaned.removeSuffix(".").removeSuffix(",").trim()
        cleaned = cleaned.replace(Regex("""\s+via\s+.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+(?:on\s+)?\d{1,2}[-/]\d{1,2}[-/](?:\d{2}|\d{4}).*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+(?:ref|reference|txn|transaction)\s*(?:no\.?|id|num)?\s*:?\s*\d+.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+(?:avbl|avl|available)\s+bal.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+bal\s*:.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+a/c\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+account\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+card\s*(?:no\.?)?\s*[x*]*\d+""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+\d{10}"""), "").trim()
        cleaned = cleaned.replace(Regex("""\s+\+\d{1,3}\s*\d{10}"""), "").trim()
        cleaned = cleaned.replace(Regex("""\s*\(UPI\s+\d+\).*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+not you\?.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+call\s+\d+.*""", RegexOption.IGNORE_CASE), "").trim()
        cleaned = cleaned.replace(Regex("""\s+sms\s+block.*""", RegexOption.IGNORE_CASE), "").trim()

        if (cleaned.matches(Regex("^[a-z]+[a-z0-9]*\\d*$", RegexOption.IGNORE_CASE))) {
            cleaned = formatVpaUsername(cleaned)
        }

        cleaned = cleaned.replace(Regex("""\s{2,}"""), " ").trim()
        cleaned = cleaned.replace(Regex("""[*]{2,}"""), " ").trim()

        if (cleaned.isNotBlank() && cleaned.length > 1) {
            if (cleaned == cleaned.uppercase() || cleaned == cleaned.lowercase()) {
                cleaned = cleaned.split(" ")
                    .joinToString(" ") { word ->
                        if (word.length > 1) {
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

        if (cleaned.length > 50) {
            cleaned = cleaned.substring(0, 50).trim()
            if (cleaned.endsWith(" ")) {
                cleaned = cleaned.trim() + "..."
            }
        }

        Log.d(TAG, "Cleaned merchant name: '$rawMerchant' -> '$cleaned'")
        return cleaned.ifBlank { "Unknown" }
    }

    private fun isValidMerchantName(merchantName: String): Boolean {
        if (merchantName.isBlank() || merchantName == "Unknown") {
            return false
        }

        if (merchantName.matches(Regex("^\\d+$"))) {
            Log.d(TAG, "Rejected merchant (purely numeric): '$merchantName'")
            return false
        }

        if (merchantName.length < 2) {
            Log.d(TAG, "Rejected merchant (too short): '$merchantName'")
            return false
        }

        if (!merchantName.matches(Regex(".*[A-Za-z].*"))) {
            Log.d(TAG, "Rejected merchant (no letters): '$merchantName'")
            return false
        }

        val invalidPatterns = listOf(
            "ref", "reference", "txn", "transaction", "upi", "imps", "neft", "rtgs",
            "debit", "credit", "balance", "bal", "avbl", "avl", "account", "a/c",
            "card", "ending", "xxxx", "****"
        )

        if (invalidPatterns.any { merchantName.lowercase().contains(it) } &&
            merchantName.length < 10) {
            Log.d(TAG, "Rejected merchant (invalid pattern): '$merchantName'")
            return false
        }

        return true
    }

    private fun isBankName(name: String): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val bankKeywords = listOf(
            "bank", "hdfc", "icici", "axis", "sbi", "kotak", "yes bank", "rbl",
            "pnb", "union bank", "canara", "baroda", "idbi", "indusind", "federal",
            "state bank", "punjab national", "bank of baroda", "hdfc bank",
            "icici bank", "axis bank", "kotak bank"
        )

        return bankKeywords.any { keyword ->
            lowerName.contains(keyword) && lowerName.length < 25
        }
    }

    private fun formatVpaUsername(username: String): String {
        val cleaned = username.replace(Regex("\\d+$"), "")
            .replace(".", " ")
            .replace("_", " ")

        val namePattern = """([a-z]+)([A-Z][a-z]+)""".toRegex()
        val match = namePattern.find(cleaned)

        return if (match != null) {
            val firstName = match.groups[1]?.value?.replaceFirstChar { it.uppercase() } ?: ""
            val lastName = match.groups[2]?.value ?: ""
            "$firstName $lastName"
        } else {
            cleaned.split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
        }
    }

    private fun cleanSenderName(sender: String): String {
        var cleaned = sender.trim()

        cleaned = cleaned.replace(Regex("""^(?:VM-|BP-|AX-|AD-|TM-|JK-|SB-|HD-)""", RegexOption.IGNORE_CASE), "")

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

        val upperCleaned = cleaned.uppercase()
        bankNameMap.forEach { (key, value) ->
            if (upperCleaned.contains(key)) {
                cleaned = value
                return@forEach
            }
        }

        return cleaned
    }

    private fun isGenericBankSender(lowerSender: String): Boolean {
        val genericPatterns = listOf("bank", "alerts", "update", "notify", "verify", "otp", "promo", "info")
        return genericPatterns.any { lowerSender.contains(it) } ||
                lowerSender.matches(Regex("^[a-z0-9]{2}-[a-z0-9]{5,10}$"))
    }
}