package com.example.expendium.worker

import android.util.Log
import com.example.expendium.data.model.TransactionType
import java.util.Locale

class SmsValidator {

    companion object {
        private const val TAG = "SmsValidator"

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
            "vm-sibsms-s", "va-sibsms-s", "vd-sibsms-s", "south indian bank", "sib",
            "iob", "indian overseas bank", "cp-iobchn-s", "cp-iobchn",
            "majeed bhaiya"
        )

        private val TRUSTED_PAYMENT_SENDERS = listOf(
            "paytm", "phonepe", "gpay", "googlepay", "bhim", "amazonpay",
            "mobikwik", "freecharge", "payzapp", "airtel money"
        )

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

        private val SPAM_SENDER_PATTERNS = listOf(
            "promo", "offer", "deals", "sale", "marketing", "ads",
            "notify", "alert"
        )
    }

    fun isPromotionalMessage(sender: String, messageBody: String): Boolean {
        val lowerSender = sender.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        if (SPAM_SENDER_PATTERNS.any { lowerSender.contains(it) }) {
            Log.d(TAG, "Spam sender pattern detected in: $sender")
            return true
        }

        val promoKeywordCount = PROMOTIONAL_KEYWORDS.count { keyword ->
            lowerBody.contains(keyword)
        }

        if (promoKeywordCount >= 2) {
            Log.d(TAG, "Multiple promotional keywords detected ($promoKeywordCount): $messageBody")
            return true
        }

        val promoPatterns = listOf(
            """https?://[^\s]+""".toRegex(),
            """www\.[^\s]+""".toRegex(),
            """[a-z0-9]+\.in/[^\s]+""".toRegex(),
            """click here""".toRegex(RegexOption.IGNORE_CASE),
            """tap here""".toRegex(RegexOption.IGNORE_CASE),
            """visit [^\s]+""".toRegex(RegexOption.IGNORE_CASE),
            """download [^\s]+""".toRegex(RegexOption.IGNORE_CASE),
            """get \d+% (off|cashback)""".toRegex(RegexOption.IGNORE_CASE),
            """upto .*% off""".toRegex(RegexOption.IGNORE_CASE),
            """minimum .*₹\d+""".toRegex(RegexOption.IGNORE_CASE),
            """valid (till|until)""".toRegex(RegexOption.IGNORE_CASE),
            """offer expires""".toRegex(RegexOption.IGNORE_CASE),
            """set up .* in \d+ click""".toRegex(RegexOption.IGNORE_CASE),
            """activate now""".toRegex(RegexOption.IGNORE_CASE),
            """ac no \d+""".toRegex(RegexOption.IGNORE_CASE),
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

        if (lowerBody.contains("prepaid pack") && lowerBody.contains("just ₹")) {
            Log.d(TAG, "Service promotional message detected")
            return true
        }

        return false
    }

    fun isTrustedSender(sender: String, messageBody: String): Boolean {
        val lowerSender = sender.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        val isTrustedBank = TRUSTED_BANK_SENDERS.any { trustedSender ->
            lowerSender.contains(trustedSender) ||
                    lowerSender == trustedSender ||
                    lowerSender.startsWith(trustedSender)
        }

        val isTrustedPayment = TRUSTED_PAYMENT_SENDERS.any { trustedSender ->
            lowerSender.contains(trustedSender) ||
                    lowerSender == trustedSender
        }

        val hasValidBankContent = hasValidBankTransactionContent(lowerBody)

        val isTrusted = isTrustedBank || isTrustedPayment || hasValidBankContent

        Log.d(TAG, "Sender trust check - $sender: ${if (isTrusted) "TRUSTED" else "UNTRUSTED"}")
        if (hasValidBankContent && !isTrustedBank && !isTrustedPayment) {
            Log.d(TAG, "Trusted based on valid bank content in message body")
        }

        return isTrusted
    }

    private fun hasValidBankTransactionContent(messageBody: String): Boolean {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

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

        val hasRefNumber = lowerBody.contains("ref") &&
                Regex("""ref\s*\d+""").containsMatchIn(lowerBody)

        val hasAccountPattern = Regex("""a/c\s*[*x]*\d{3,6}""").containsMatchIn(lowerBody)

        val hasAmountPattern = Regex("""rs\.?\s*\d+(\.\d{2})?""").containsMatchIn(lowerBody)

        val isValidBankTransaction = hasRefNumber && hasAccountPattern && hasAmountPattern

        Log.d(TAG, "Bank content validation - Bank ID: $hasBankIdentifier, " +
                "Transaction: $hasTransactionIndicator, Ref: $hasRefNumber, " +
                "Account: $hasAccountPattern, Amount: $hasAmountPattern")

        return isValidBankTransaction
    }

    fun isValidTransaction(messageBody: String, amount: Double, type: TransactionType): Boolean {
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        val transactionIndicators = listOf(
            "debited", "debit alert", "spent", "paid", "withdrawal",
            "purchase", "txn at", "transaction at", "transferred to",
            "payment to", "sent to", "dr ", "dr.", "sent",
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

        if (amount < 1.0) {
            Log.d(TAG, "Amount too small: $amount")
            return false
        }

        if (lowerBody.contains("balance") && !lowerBody.contains("debited") && !lowerBody.contains("credited")) {
            if (!lowerBody.contains("transaction") && !lowerBody.contains("txn")) {
                Log.d(TAG, "Appears to be balance inquiry, not transaction")
                return false
            }
        }

        if (lowerBody.contains("otp") || lowerBody.contains("verification") ||
            lowerBody.contains("verify") || lowerBody.contains("code")) {
            Log.d(TAG, "Appears to be OTP/verification message")
            return false
        }

        return true
    }
}