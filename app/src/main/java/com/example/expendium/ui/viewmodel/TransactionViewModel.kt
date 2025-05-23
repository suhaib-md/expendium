// ui/viewmodel/TransactionViewModel.kt
package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.expendium.data.repository.TransactionRepository
import com.example.expendium.data.repository.CategoryRepository
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import javax.inject.Inject
// No need for these specific text utility imports if using String extension functions directly
// import kotlin.text.contains
// import kotlin.text.isBlank
// import kotlin.text.lowercase

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState()) // Now TransactionUiState should be resolved
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    val transactions: StateFlow<List<Transaction>> =
        combine(
            _uiState,
            transactionRepository.getAllTransactions()
        ) { currentUiState, allTransactions -> // Renamed 'uiState' parameter to 'currentUiState' for clarity
            // Apply category filter first
            val filteredByCategory = if (currentUiState.selectedCategoryFilter != null) {
                allTransactions.filter { it.categoryId == currentUiState.selectedCategoryFilter }
            } else {
                allTransactions
            }

            // Then apply search query
            if (currentUiState.searchQuery.isBlank()) {
                filteredByCategory // No search query, return category-filtered list
            } else {
                val query = currentUiState.searchQuery.lowercase()
                filteredByCategory.filter { transaction ->
                    transaction.merchantOrPayee.lowercase().contains(query) ||
                            (transaction.notes?.lowercase()?.contains(query) == true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTransaction(
        amount: Double,
        merchantOrPayee: String,
        categoryId: Long?,
        paymentMode: String,
        notes: String? = null,
        transactionDate: Long = System.currentTimeMillis(),
        type: TransactionType
    ) {
        viewModelScope.launch {
            _uiState.update { currentState -> // Explicitly naming 'it' to 'currentState'
                currentState.copy(isLoading = true, errorMessage = null)
            }
            try {
                if (categoryId == null) {
                    _uiState.update { currentState ->
                        currentState.copy(isLoading = false, errorMessage = "Category is required.")
                    }
                    return@launch
                }
                val transaction = Transaction(
                    amount = amount,
                    transactionDate = transactionDate,
                    type = type,
                    categoryId = categoryId,
                    merchantOrPayee = merchantOrPayee,
                    notes = notes,
                    paymentMode = paymentMode,
                    isManual = true // Assuming manual entry for now
                )
                transactionRepository.insertTransaction(transaction)
                _uiState.update { currentState -> currentState.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, errorMessage = e.message ?: "Failed to add transaction")
                }
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            try {
                transactionRepository.updateTransaction(
                    transaction.copy(updatedAt = System.currentTimeMillis())
                )
                _uiState.update { currentState -> currentState.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, errorMessage = e.message ?: "Failed to update transaction")
                }
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            try {
                transactionRepository.deleteTransactionById(transactionId)
                _uiState.update { currentState -> currentState.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete transaction")
                }
            }
        }
    }

    fun filterTransactionsByCategory(categoryId: Long?) {
        _uiState.update { currentState ->
            currentState.copy(selectedCategoryFilter = categoryId)
        }
    }

    fun searchTransactions(query: String) {
        _uiState.update { currentState ->
            currentState.copy(searchQuery = query.trim())
        }
    }

    fun clearError() {
        _uiState.update { currentState ->
            currentState.copy(errorMessage = null)
        }
    }

    fun setError(message: String) {
        _uiState.update { currentState ->
            currentState.copy(errorMessage = message)
        }
    }
}

// Ensure this data class is defined correctly
data class TransactionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategoryFilter: Long? = null,
    val searchQuery: String = ""
)