package com.example.expendium.worker

import android.content.Context
import android.util.Log
import com.example.expendium.data.model.Account
import com.example.expendium.data.repository.AccountRepository
import com.example.expendium.data.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class AccountResolver(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {

    companion object {
        private const val TAG = "AccountResolver"
    }

    suspend fun determineAccount(messageBody: String, sender: String, accountHint: String?): Account? {
        val accounts = accountRepository.getAllAccounts().firstOrNull() ?: emptyList()

        if (accounts.isEmpty()) {
            Log.w(TAG, "No accounts found in the system")
            return null
        }

        Log.d(TAG, "Determining account for sender: $sender, hint: $accountHint")
        Log.d(TAG, "Available accounts: ${accounts.map { "${it.name} (${it.accountNumber})" }}")

        if (!accountHint.isNullOrBlank()) {
            Log.d(TAG, "Trying to match account hint: $accountHint")
            var foundAccount = accounts.find { account ->
                account.accountNumber?.endsWith(accountHint) == true
            }

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

        val senderUpper = sender.uppercase()
        Log.d(TAG, "Trying sender-based mapping for: $senderUpper")

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

        Log.d(TAG, "Checking message body for account clues")
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        accounts.forEach { account ->
            if (account.name.isNotBlank() && lowerBody.contains(account.name.lowercase())) {
                Log.i(TAG, "✅ Matched account by name in message: ${account.name}")
                return account
            }

            if (!account.accountNumber.isNullOrBlank() &&
                (lowerBody.contains(account.accountNumber!!) ||
                        lowerBody.contains("x${account.accountNumber!!.takeLast(4)}"))) {
                Log.i(TAG, "✅ Matched account by number in message: ${account.name}")
                return account
            }
        }

        val sharedPrefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
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

        if (accounts.size == 1) {
            Log.i(TAG, "✅ Only one account exists, using: ${accounts[0].name}")
            return accounts[0]
        }

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
        return null
    }
}