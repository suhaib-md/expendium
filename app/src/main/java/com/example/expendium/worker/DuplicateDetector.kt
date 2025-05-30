package com.example.expendium.worker

import android.content.Context
import android.util.Log
import java.security.MessageDigest

class DuplicateDetector(
    private val context: Context
) {

    companion object {
        private const val TAG = "DuplicateDetector"
    }

    fun generateSmsHash(sender: String, body: String, timestamp: Long): String {
        val content = "$sender|$body|${timestamp / 60000}"
        return MessageDigest.getInstance("MD5")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun isAlreadyProcessed(smsHash: String): Boolean {
        val sharedPrefs = context.getSharedPreferences("ProcessedSMS", Context.MODE_PRIVATE)
        return sharedPrefs.contains(smsHash)
    }

    fun markAsProcessed(smsHash: String) {
        val sharedPrefs = context.getSharedPreferences("ProcessedSMS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(smsHash, true).apply()

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