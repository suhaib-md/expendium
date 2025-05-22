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

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = transactionRepository.getAllTransactions()
        .stateIn(
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
        transactionDate: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                val transaction = Transaction(
                    amount = amount,
                    transactionDate = transactionDate,
                    type = TransactionType.EXPENSE,
                    categoryId = categoryId,
                    merchantOrPayee = merchantOrPayee,
                    notes = notes,
                    paymentMode = paymentMode,
                    isManual = true
                )
                transactionRepository.insertTransaction(transaction)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(
                    transaction.copy(updatedAt = System.currentTimeMillis())
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransactionById(transactionId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun filterTransactionsByCategory(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(selectedCategoryFilter = categoryId)
    }

    fun searchTransactions(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class TransactionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategoryFilter: Long? = null,
    val searchQuery: String = ""
)