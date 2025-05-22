// data/database/Converters.kt
package com.example.expendium.data.database

import androidx.room.TypeConverter
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.model.BudgetPeriod

class Converters {

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String {
        return type.name
    }

    @TypeConverter
    fun toTransactionType(type: String): TransactionType {
        return TransactionType.valueOf(type)
    }

    @TypeConverter
    fun fromBudgetPeriod(period: BudgetPeriod): String {
        return period.name
    }

    @TypeConverter
    fun toBudgetPeriod(period: String): BudgetPeriod {
        return BudgetPeriod.valueOf(period)
    }
}