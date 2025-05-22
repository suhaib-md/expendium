// data/model/Budget.kt
package com.example.expendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val budgetId: Long = 0,
    val categoryId: Long? = null, // null for overall budget
    val amount: Double,
    val startDate: Long,
    val endDate: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BudgetPeriod {
    WEEKLY, MONTHLY, CUSTOM
}