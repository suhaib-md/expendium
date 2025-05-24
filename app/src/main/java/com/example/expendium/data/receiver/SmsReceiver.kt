// data/receiver/SmsReceiver.kt
package com.example.expendium.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "=== SMS RECEIVER TRIGGERED ===")
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Processing SMS_RECEIVED_ACTION")
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
                val timestamp = message.timestampMillis

                Log.d(TAG, "SMS Details:")
                Log.d(TAG, "  Sender: $sender")
                Log.d(TAG, "  Body: $messageBody")
                Log.d(TAG, "  Timestamp: $timestamp")

                processMessage(context, sender, messageBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSmsReceived", e)
        }
    }

    private fun handleSmsReceivedLegacy(context: Context, intent: Intent) {
        try {
            val bundle = intent.extras
            if (bundle != null) {
                Log.d(TAG, "Bundle contents: ${bundle.keySet()}")

                val pdus = bundle.get("pdus") as? Array<*>
                Log.d(TAG, "PDUs count: ${pdus?.size}")

                pdus?.let { pduArray ->
                    for (pdu in pduArray) {
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                        val sender = smsMessage.displayOriginatingAddress
                        val messageBody = smsMessage.messageBody

                        Log.d(TAG, "Legacy SMS Details:")
                        Log.d(TAG, "  Sender: $sender")
                        Log.d(TAG, "  Body: $messageBody")

                        processMessage(context, sender, messageBody)
                    }
                }
            } else {
                Log.d(TAG, "No bundle in intent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSmsReceivedLegacy", e)
        }
    }

    private fun processMessage(context: Context, sender: String?, messageBody: String?) {
        if (sender == null || messageBody == null) {
            Log.w(TAG, "Sender or message body is null")
            return
        }

        Log.i(TAG, "Processing message from: $sender")
        Log.i(TAG, "Message: $messageBody")

        if (isTransactionSms(messageBody, sender)) {
            Log.i(TAG, "🏦 TRANSACTION SMS DETECTED!")
            Log.i(TAG, "Bank/Sender: $sender")
            Log.i(TAG, "Transaction Message: $messageBody")

            // TODO: Parse transaction details and save to database
            parseAndSaveTransaction(sender, messageBody)
        } else {
            Log.d(TAG, "Not a transaction SMS")
        }
    }

    private fun isTransactionSms(messageBody: String, sender: String): Boolean {
        val transactionKeywords = listOf(
            "debited", "credited", "transaction", "payment", "purchase",
            "withdrawn", "deposited", "balance", "amount", "rs.", "rs ", "inr",
            "upi", "card", "atm", "transfer", "spent", "received", "paid",
            "bank", "account", "debit", "credit", "rupees", "₹"
        )

        val bankSenders = listOf(
            "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara",
            "union", "idbi", "paytm", "phonepe", "gpay", "bhim", "amazon",
            "flipkart", "bank", "visa", "master", "rupay"
        )

        val messageBodyLower = messageBody.lowercase()
        val senderLower = sender.lowercase()

        val hasTransactionKeyword = transactionKeywords.any { keyword ->
            messageBodyLower.contains(keyword)
        }

        val isFromBank = bankSenders.any { bank ->
            senderLower.contains(bank)
        }

        Log.d(TAG, "Transaction check - Keywords: $hasTransactionKeyword, Bank: $isFromBank")

        return hasTransactionKeyword || isFromBank
    }

    private fun parseAndSaveTransaction(sender: String, messageBody: String) {
        // TODO: Implement actual parsing logic
        Log.i(TAG, "Saving transaction from $sender: $messageBody")

        // Example parsing for amount (you'll need to implement proper parsing)
        val amountRegex = """(?:rs\.?|₹|inr)\s*(\d+(?:,\d+)*(?:\.\d{2})?)""".toRegex(RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(messageBody)
        val amount = amountMatch?.groupValues?.get(1)

        if (amount != null) {
            Log.i(TAG, "Extracted amount: ₹$amount")
        }
    }
}