// In ui/viewmodel/AccountViewModel.kt
package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expendium.data.model.Account
import com.example.expendium.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val isLoading: Boolean = false,
    val saveSuccess: Boolean = false, // Added to signal successful save for navigation
    val errorMessage: String? = null,
    val accountName: String = "",
    val accountType: String = "", // e.g., "Bank", "Cash", "Credit Card"
    val accountNumber: String = "", // <-- ADDED for TextField (nullable in model)
    val initialBalance: String = "0.0", // Store as String for TextField
    val isEditMode: Boolean = false,
    val accountToEdit: Account? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    val allAccounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onAccountNameChange(name: String) {
        _uiState.update { it.copy(accountName = name, errorMessage = null, saveSuccess = false) }
    }

    fun onAccountTypeChange(type: String) {
        _uiState.update { it.copy(accountType = type, errorMessage = null, saveSuccess = false) }
    }

    fun onAccountNumberChange(number: String) { // <-- ADDED
        _uiState.update { it.copy(accountNumber = number, errorMessage = null, saveSuccess = false) }
    }

    fun onInitialBalanceChange(balance: String) {
        _uiState.update { it.copy(initialBalance = balance, errorMessage = null, saveSuccess = false) }
    }

    fun prepareNewAccount() {
        _uiState.value = AccountUiState() // Reset to default, ensures saveSuccess is false
    }

    fun loadAccountForEditing(accountId: Long) {
        _uiState.update { it.copy(isLoading = true, saveSuccess = false) }
        viewModelScope.launch {
            accountRepository.getAccountById(accountId).collect { account ->
                if (account != null) {
                    _uiState.update {
                        it.copy(
                            accountName = account.name,
                            accountType = account.type,
                            accountNumber = account.accountNumber ?: "", // <-- ADDED (handle null)
                            initialBalance = account.currentBalance.toString(),
                            isEditMode = true,
                            accountToEdit = account,
                            isLoading = false,
                            errorMessage = null,
                            saveSuccess = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Account not found.", saveSuccess = false) }
                }
            }
        }
    }

    fun saveAccount() {
        val currentUiState = _uiState.value
        val name = currentUiState.accountName.trim()
        val type = currentUiState.accountType.trim()
        val accountNumber = currentUiState.accountNumber.trim().ifBlank { null } // <-- Store as null if blank
        val balanceString = currentUiState.initialBalance.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Account name cannot be empty.", saveSuccess = false) }
            return
        }
        if (type.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Account type cannot be empty.", saveSuccess = false) }
            return
        }
        // Account number can be optional, so no validation here unless you want it to be mandatory

        val balance = balanceString.toDoubleOrNull()
        if (balance == null) {
            _uiState.update { it.copy(errorMessage = "Initial balance must be a valid number.", saveSuccess = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, saveSuccess = false) }

        viewModelScope.launch {
            try {
                if (currentUiState.isEditMode && currentUiState.accountToEdit != null) {
                    // Update existing account
                    val updatedAccount = currentUiState.accountToEdit.copy(
                        name = name,
                        type = type,
                        accountNumber = accountNumber, // <-- ADDED
                        currentBalance = balance,
                        updatedAt = System.currentTimeMillis()
                    )
                    accountRepository.updateAccount(updatedAccount)
                } else {
                    // Create new account
                    val newAccount = Account(
                        name = name,
                        type = type,
                        accountNumber = accountNumber, // <-- ADDED
                        currentBalance = balance
                        // createdAt and updatedAt will be set by default in the Account model
                    )
                    accountRepository.insertAccount(newAccount)
                }
                // Reset state after successful save and signal success
                _uiState.value = AccountUiState(isLoading = false, saveSuccess = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to save account.", saveSuccess = false)
                }
            }
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, saveSuccess = false) }
            try {
                val accountToDelete = allAccounts.value.firstOrNull { it.accountId == accountId }
                accountToDelete?.let {
                    accountRepository.deleteAccount(it)
                    _uiState.update { state -> state.copy(isLoading = false) } // saveSuccess remains false
                } ?: _uiState.update { state -> state.copy(isLoading = false, errorMessage = "Account not found for deletion.") }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete account.")
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Call this after navigation to reset the flag
    fun navigationCompleted() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}