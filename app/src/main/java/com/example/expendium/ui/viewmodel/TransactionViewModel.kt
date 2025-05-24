package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.repository.CategoryRepository
import com.example.expendium.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data class to combine Transaction with its Category Name for easier UI display
data class TransactionWithCategory(
    val transaction: Transaction,
    val categoryName: String?
)

// Define TransactionUiState here or in a separate file if preferred
data class TransactionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategoryFilter: Long? = null, // For filtering by category in the list
    val searchQuery: String = "", // For searching in the list
    val isEditing: Boolean = false // To know if we are in edit mode
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // Flow for all categories, used to map category IDs to names
    val categoriesMap: StateFlow<Map<Long, String>> = // Made public for AddTransactionScreen
        categoryRepository.getAllCategories()
            .map { list -> list.associateBy({ it.categoryId }, { it.name }) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    // Filtered and searched transactions combined with their category names
    val transactionsWithCategoryNames: StateFlow<List<TransactionWithCategory>> =
        combine(
            _uiState,
            transactionRepository.getAllTransactions(), // Base flow of all transactions
            categoriesMap
        ) { currentUiState, allTransactions, catMap ->
            val filteredByCategory = if (currentUiState.selectedCategoryFilter != null) {
                allTransactions.filter { it.categoryId == currentUiState.selectedCategoryFilter }
            } else {
                allTransactions
            }
            val searchedTransactions = if (currentUiState.searchQuery.isBlank()) {
                filteredByCategory
            } else {
                val query = currentUiState.searchQuery.lowercase()
                filteredByCategory.filter { transaction ->
                    transaction.merchantOrPayee.lowercase().contains(query) ||
                            (transaction.notes?.lowercase()?.contains(query) == true)
                }
            }
            searchedTransactions.map { transaction ->
                TransactionWithCategory(transaction, catMap[transaction.categoryId])
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

    // --- State for Add/Edit Transaction ---
    private val _transactionToEdit = MutableStateFlow<Transaction?>(null)
    val transactionToEdit: StateFlow<Transaction?> = _transactionToEdit.asStateFlow()
    // --- End State for Add/Edit Transaction ---

    // --- State for Transaction Detail Screen ---
    private val _selectedTransactionDetail = MutableStateFlow<Transaction?>(null)
    val selectedTransactionDetail: StateFlow<Transaction?> = _selectedTransactionDetail.asStateFlow()

    private val _selectedTransactionCategoryName = MutableStateFlow<String?>(null)
    val selectedTransactionCategoryName: StateFlow<String?> = _selectedTransactionCategoryName.asStateFlow()
    // --- End State for Transaction Detail Screen ---

    fun prepareForNewTransaction() {
        _transactionToEdit.value = null
        _uiState.update { it.copy(isEditing = false, errorMessage = null) }
        // Potentially reset other viewmodel states related to the add/edit form if needed
    }

    fun loadTransactionForEditing(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            transactionRepository.getTransactionById(transactionId).collectLatest { transaction ->
                _transactionToEdit.value = transaction
                _uiState.update { it.copy(isLoading = false, isEditing = transaction != null) }
            }
        }
    }

    fun saveTransaction(
        transactionIdToUpdate: Long?, // Null if adding new
        amount: Double,
        merchantOrPayee: String,
        categoryId: Long?,
        paymentMode: String,
        notes: String?,
        transactionDate: Long,
        type: TransactionType
    ): Boolean { // Return true if successful, false otherwise
        if (merchantOrPayee.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Merchant/Payee cannot be empty.") }
            return false
        }
        if (categoryId == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Category is required.") }
            return false
        }
        if (amount <= 0) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Amount must be greater than zero.") }
            return false
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (transactionIdToUpdate != null && transactionIdToUpdate != -1L) {
                    // Update existing transaction
                    val existingTransaction = _transactionToEdit.value
                    if (existingTransaction != null && existingTransaction.transactionId == transactionIdToUpdate) {
                        val updatedTransaction = existingTransaction.copy(
                            amount = amount,
                            merchantOrPayee = merchantOrPayee,
                            categoryId = categoryId,
                            paymentMode = paymentMode,
                            notes = notes,
                            transactionDate = transactionDate,
                            type = type,
                            updatedAt = System.currentTimeMillis()
                        )
                        transactionRepository.updateTransaction(updatedTransaction)
                    } else {
                        // This case should ideally not happen if logic is correct
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Error: Transaction to update not found.") }
                        return@launch
                    }
                } else {
                    // Add new transaction
                    val transaction = Transaction(
                        amount = amount,
                        transactionDate = transactionDate,
                        type = type,
                        categoryId = categoryId,
                        merchantOrPayee = merchantOrPayee,
                        notes = notes,
                        paymentMode = paymentMode,
                        isManual = true // Assuming manual entry
                    )
                    transactionRepository.insertTransaction(transaction)
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = null, isEditing = false) }
                _transactionToEdit.value = null // Clear after saving
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to save transaction")
                }
            }
        }
        return _uiState.value.errorMessage == null // Success if no error was set
    }


    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                transactionRepository.deleteTransactionById(transactionId)
                if (_selectedTransactionDetail.value?.transactionId == transactionId) {
                    clearSelectedTransactionDetails()
                }
                if (_transactionToEdit.value?.transactionId == transactionId) { // Also clear if it was being edited
                    _transactionToEdit.value = null
                    _uiState.update { it.copy(isEditing = false) }
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete transaction")
                }
            }
        }
    }

    fun filterTransactionsByCategory(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryFilter = categoryId) }
    }

    fun searchTransactions(query: String) {
        _uiState.update { it.copy(searchQuery = query.trim()) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String) { // Keep this for direct error setting from UI if needed
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun loadTransactionDetails(transactionId: Long) { // For TransactionDetailScreen
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            _selectedTransactionDetail.value = null
            _selectedTransactionCategoryName.value = null

            transactionRepository.getTransactionById(transactionId).firstOrNull().let { transaction ->
                _selectedTransactionDetail.value = transaction
                if (transaction != null) {
                    transaction.categoryId?.let { catId ->
                        val categoryName = categoriesMap.value[catId]
                            ?: categoryRepository.getCategoryById(catId)?.name
                            ?: "N/A (Category unknown)"
                        _selectedTransactionCategoryName.value = categoryName
                    } ?: run {
                        _selectedTransactionCategoryName.value = "N/A (No Category ID)"
                    }
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Transaction not found.") }
                }
            }
        }
    }

    fun clearSelectedTransactionDetails() {
        _selectedTransactionDetail.value = null
        _selectedTransactionCategoryName.value = null
    }

    // No longer directly returning Flow from here for editing, use _transactionToEdit
    // fun getTransactionForEditing(transactionId: Long): Flow<Transaction?> { ... }
}