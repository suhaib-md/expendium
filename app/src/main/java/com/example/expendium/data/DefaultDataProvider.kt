package com.example.expendium.data

import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType

object DefaultDataProvider {

    fun getDefaultCategories(): List<Category> {
        return listOf(
            Category(name = "Food & Dining", iconName = "restaurant", colorHex = "#FF6B6B", type = TransactionType.EXPENSE),
            Category(name = "Transportation", iconName = "directions_car", colorHex = "#4ECDC4", type = TransactionType.EXPENSE),
            Category(name = "Shopping", iconName = "shopping_bag", colorHex = "#45B7D1", type = TransactionType.EXPENSE),
            Category(name = "Bills & Utilities", iconName = "receipt", colorHex = "#FFA07A", type = TransactionType.EXPENSE),
            Category(name = "Entertainment", iconName = "movie", colorHex = "#98D8C8", type = TransactionType.EXPENSE),
            Category(name = "Healthcare", iconName = "local_hospital", colorHex = "#F7DC6F", type = TransactionType.EXPENSE),
            Category(name = "Education", iconName = "school", colorHex = "#BB8FCE", type = TransactionType.EXPENSE),
            Category(name = "Travel", iconName = "flight", colorHex = "#85C1E9", type = TransactionType.EXPENSE),
            Category(name = "Personal Care", iconName = "face", colorHex = "#F8C471", type = TransactionType.EXPENSE),
            Category(name = "Groceries", iconName = "local_grocery_store", colorHex = "#82E0AA", type = TransactionType.EXPENSE),
            Category(name = "Salary", iconName = "work", colorHex = "#28B463", type = TransactionType.INCOME),
            Category(name = "Other Income", iconName = "attach_money", colorHex = "#58D68D", type = TransactionType.INCOME),
            Category(name = "Other", iconName = "more_horiz", colorHex = "#BDC3C7", type = TransactionType.EXPENSE)
        )
    }

    fun getDefaultPaymentModes(): List<String> {
        return listOf(
            "Cash",
            "Credit Card",
            "Debit Card",
            "GPay",
            "PhonePe",
            "Paytm",
            "Bank Transfer",
            "UPI",
            "Other"
        )
    }
}