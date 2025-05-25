// In data/model/Account.kt
package com.example.expendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts") // If using Room
data class Account(
    @PrimaryKey(autoGenerate = true)
    val accountId: Long = 0L,
    val name: String,
    val currentBalance: Double = 0.0, // Example field
    val type: String, // e.g., "Bank", "Cash", "Credit Card"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
    // Add other relevant fields like currency, icon, etc.
)