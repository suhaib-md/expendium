// In ui/screen/AddAccountScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.ui.viewmodel.AccountViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountScreen(
    navController: NavController,
    accountViewModel: AccountViewModel = hiltViewModel(),
    accountId: Long? // Pass null for new account, existing ID for editing
) {
    val uiState by accountViewModel.uiState.collectAsState()

    LaunchedEffect(key1 = accountId) {
        if (accountId != null && accountId != -1L) {
            accountViewModel.loadAccountForEditing(accountId)
        } else {
            accountViewModel.prepareNewAccount()
        }
    }

    // Navigate back on successful save
    LaunchedEffect(key1 = uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.popBackStack()
            accountViewModel.navigationCompleted() // Reset the flag
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Account" else "Add New Account") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        accountViewModel.saveAccount()
                        // Navigation is now handled by the LaunchedEffect observing uiState.saveSuccess
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save Account")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading && !uiState.isEditMode && uiState.accountToEdit == null) { // Show loader only when initially loading, not during field edits
                CircularProgressIndicator()
            }

            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = { accountViewModel.onAccountNameChange(it) },
                label = { Text("Account Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("name", ignoreCase = true) == true,
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = uiState.accountType,
                onValueChange = { accountViewModel.onAccountTypeChange(it) },
                label = { Text("Account Type (e.g., Bank, Cash) *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("type", ignoreCase = true) == true,
                enabled = !uiState.isLoading
            )

            // --- ADDED Account Number Field ---
            OutlinedTextField(
                value = uiState.accountNumber,
                onValueChange = { accountViewModel.onAccountNumberChange(it) },
                label = { Text("Account Number (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                // No specific error for account number unless you add validation
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), // Or KeyboardType.Text if it can have non-digits
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = uiState.initialBalance,
                onValueChange = { accountViewModel.onInitialBalanceChange(it) },
                label = { Text("Current/Initial Balance *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("balance", ignoreCase = true) == true,
                enabled = !uiState.isLoading
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}