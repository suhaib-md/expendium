// data/model/Transaction.kt
package com.example.expendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["categoryId"]),Index(value = ["accountId"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val transactionId: Long = 0,
    val amount: Double,
    val transactionDate: Long, // Timestamp in milliseconds
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: Long?,
    val merchantOrPayee: String,
    val notes: String? = null,
    val paymentMode: String,
    val isManual: Boolean = true,
    val accountId: Long?,
    val originalSmsId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TransactionType {
    EXPENSE, INCOME
}