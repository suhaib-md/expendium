package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Import your Account model and AccountRepository
import com.example.expendium.data.model.Account // Assuming this is your Account data class
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.model.TransactionWithCategory
import com.example.expendium.data.repository.AccountRepository // Assuming you create this
import com.example.expendium.data.repository.CategoryRepository
import com.example.expendium.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject


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
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository // Inject AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // Flow for all categories, used to map category IDs to names
    val categoriesMap: StateFlow<Map<Long, String>> =
        categoryRepository.getAllCategories()
            .map { list -> list.associateBy({ it.categoryId }, { it.name }) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    // Flow for all accounts (NEW)
    val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts() // Assuming getAllAccounts() returns Flow<List<Account>>
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
                            (transaction.notes?.lowercase()?.contains(query) == true) ||
                            (catMap[transaction.categoryId]?.lowercase()?.contains(query) == true)
                }
            }
            searchedTransactions.map { transaction ->
                TransactionWithCategory(transaction, catMap[transaction.categoryId] ?: "Uncategorized")
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

    fun loadTransactionForEditing(transactionId: Long?) {
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
        type: TransactionType,
        accountId: Long? // This parameter is crucial
    ): Boolean {
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
        // NEW: Validate accountId if it's considered mandatory for new transactions
        if (accountId == null && transactionIdToUpdate == null) { // Only for new transactions, allow null if editing existing without changing account
            _uiState.update { it.copy(isLoading = false, errorMessage = "Account is required.") }
            return false
        }


        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Fetch the CURRENT state of the account. Use the non-Flow version from AccountRepository.
                val currentAccountState = accountRepository.getAccountByIdStatic(accountId) // Use Static version
                if (currentAccountState == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Selected account not found.") }
                    return@launch
                }

                if (transactionIdToUpdate != null && transactionIdToUpdate != -1L) {
                    // --- UPDATE EXISTING TRANSACTION ---
                    // Fetch the original transaction to get its old amount and type for balance adjustment
                    val existingTransaction = transactionRepository.getTransactionByIdStatic(transactionIdToUpdate) // Use Static version

                    if (existingTransaction != null) {
                        // IMPORTANT: Handle if the account itself was changed during the edit
                        if (existingTransaction.accountId != accountId) {
                            // This is the complex case:
                            // 1. Revert from OLD account (existingTransaction.accountId)
                            // 2. Apply to NEW account (accountId)
                            // For now, showing an error or you can implement this detailed logic.
                            _uiState.update { it.copy(isLoading = false, errorMessage = "Changing account during edit requires more complex balance adjustment (not yet fully implemented).") }
                            return@launch // Or proceed with a simpler update if this is acceptable
                        }

                        val oldAmountForUpdate = existingTransaction.amount
                        val oldTypeForUpdate = existingTransaction.type

                        val updatedTransaction = existingTransaction.copy(
                            amount = amount,
                            merchantOrPayee = merchantOrPayee,
                            categoryId = categoryId,
                            paymentMode = paymentMode,
                            notes = notes,
                            transactionDate = transactionDate,
                            type = type,
                            updatedAt = System.currentTimeMillis(),
                            accountId = accountId // Ensure this is the potentially new accountId
                        )
                        transactionRepository.updateTransactionAndUpdateAccountBalance(
                            updatedTransaction,
                            currentAccountState, // Pass the fetched current state of the account
                            oldAmountForUpdate,
                            oldTypeForUpdate
                        )
                        _transactionToEdit.value = updatedTransaction
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Error: Transaction to update not found.") }
                        return@launch
                    }
                } else {
                    // --- ADD NEW TRANSACTION ---
                    val newTransaction = Transaction(
                        // transactionId is auto-generated
                        amount = amount,
                        transactionDate = transactionDate,
                        type = type,
                        categoryId = categoryId,
                        merchantOrPayee = merchantOrPayee,
                        notes = notes,
                        paymentMode = paymentMode,
                        isManual = true, // Assuming
                        accountId = accountId
                        // createdAt and updatedAt are typically set by default in the model or DB
                    )
                    transactionRepository.insertTransactionAndUpdateAccountBalance(
                        newTransaction,
                        currentAccountState // Pass the fetched current state of the account
                    )
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = null, isEditing = false) }
                _transactionToEdit.value = null
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to save transaction.")
                }
            }
        }
        return _uiState.value.errorMessage == null // Check if error was set
    }


    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Fetch the transaction to be deleted
                val transactionToDelete = transactionRepository.getTransactionByIdStatic(transactionId) // Use static version
                if (transactionToDelete == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Transaction not found for deletion.") }
                    return@launch
                }
                if (transactionToDelete.accountId == null) {
                    // If accountId is nullable and can be null, just delete the transaction without balance update
                    transactionRepository.deleteTransaction(transactionToDelete) // Or deleteTransactionById
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Transaction deleted (no account associated).") }
                    return@launch
                }

                // Fetch the current state of the associated account
                val currentAccountState = accountRepository.getAccountByIdStatic(transactionToDelete.accountId)
                if (currentAccountState == null) {
                    // Account associated with transaction not found.
                    // Decide: Delete transaction anyway, or show error?
                    // For now, let's show an error and not delete.
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Associated account not found. Cannot update balance.") }
                    return@launch
                }

                transactionRepository.deleteTransactionAndUpdateAccountBalance(transactionToDelete, currentAccountState)

                // ... (rest of your cleanup logic for _selectedTransactionDetail, _transactionToEdit)
                if (_selectedTransactionDetail.value?.transactionId == transactionId) {
                    clearSelectedTransactionDetails()
                }
                if (_transactionToEdit.value?.transactionId == transactionId) {
                    _transactionToEdit.value = null
                    _uiState.update { it.copy(isEditing = false) }
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete transaction.")
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

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun loadTransactionDetails(transactionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            _selectedTransactionDetail.value = null
            _selectedTransactionCategoryName.value = null

            transactionRepository.getTransactionById(transactionId).firstOrNull().let { transaction ->
                _selectedTransactionDetail.value = transaction
                if (transaction != null) {
                    transaction.categoryId?.let { catId ->
                        val categoryName = categoriesMap.value[catId]
                            ?: categoryRepository.getCategoryById(catId)?.name // Fallback directly to repo
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
}