package com.example.expendium.worker

import android.util.Log
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.repository.CategoryRepository
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class CategoryResolver(
    private val categoryRepository: CategoryRepository
) {

    companion object {
        private const val TAG = "CategoryResolver"
    }

    private var categoryCache: Map<String, Long>? = null

    private suspend fun initializeCategoryCache() {
        if (categoryCache == null) {
            val categories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()
            categoryCache = categories.associate { it.name to it.categoryId }
        }
    }

    suspend fun determineCategory(merchant: String, messageBody: String, type: TransactionType): Long? {
        initializeCategoryCache()
        val cache = categoryCache ?: return null

        val lowerMerchant = merchant.lowercase(Locale.ROOT)
        val lowerBody = messageBody.lowercase(Locale.ROOT)

        if (type == TransactionType.EXPENSE) {
            val expenseKeywords = mapOf(
                "Food & Dining" to listOf("zomato", "swiggy", "ubereats", "dominos", "pizza",
                    "restaurant", "food", "cafe", "starbucks", "kfc", "mcdonald", "burger"),
                "Groceries" to listOf("grocery", "supermarket", "vegetables", "fruits", "milk",
                    "bigbasket", "grofers", "blinkit", "dunzo", "zepto", "instamart"),
                "Transportation" to listOf("ola", "uber", "taxi", "fuel", "petrol", "diesel",
                    "metro", "bus", "auto", "rickshaw", "rapido"),
                "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "shopping",
                    "mall", "store", "electronics", "fashion"),
                "Bills & Utilities" to listOf("bill", "recharge", "electricity", "gas", "water",
                    "broadband", "internet", "mobile", "jio", "airtel", "vi"),
                "Entertainment" to listOf("netflix", "amazon prime", "hotstar", "spotify",
                    "movie", "cinema", "ticket", "booking"),
                "Healthcare" to listOf("hospital", "clinic", "doctor", "medical", "pharmacy",
                    "medicine", "health", "apollo", "1mg", "pharmeasy"),
                "Education" to listOf("school", "college", "university", "education", "course",
                    "tuition", "fees", "book"),
                "Travel" to listOf("flight", "hotel", "travel", "trip", "irctc", "train",
                    "makemytrip", "goibibo"),
                "Personal Care" to listOf("salon", "spa", "beauty", "cosmetic", "hair",
                    "personal care", "grooming")
            )

            for ((categoryName, keywords) in expenseKeywords) {
                if (keywords.any { lowerMerchant.contains(it) || lowerBody.contains(it) }) {
                    Log.d(TAG, "Category matched: $categoryName")
                    return cache[categoryName]
                }
            }
        } else if (type == TransactionType.INCOME) {
            val incomeKeywords = mapOf(
                "Salary" to listOf("salary", "wage", "pay", "employer", "payroll", "bonus"),
                "Other Income" to listOf("interest", "dividend", "return", "cashback", "reward",
                    "refund", "credit", "commission", "freelance")
            )

            for ((categoryName, keywords) in incomeKeywords) {
                if (keywords.any { lowerBody.contains(it) || lowerMerchant.contains(it) }) {
                    Log.d(TAG, "Category matched: $categoryName")
                    return cache[categoryName]
                }
            }
        }

        Log.d(TAG, "Defaulting to 'Other' category")
        return cache["Other"]
    }
}