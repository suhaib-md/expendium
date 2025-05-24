// data/model/Category.kt
package com.example.expendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val categoryId: Long = 0,
    val name: String,
    val iconName: String? = null,
    val colorHex: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val updatedAt: Long = System.currentTimeMillis()
)