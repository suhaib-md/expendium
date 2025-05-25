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
        if (accountId != null && accountId != -1L) { // -1L could be a convention for new
            accountViewModel.loadAccountForEditing(accountId)
        } else {
            accountViewModel.prepareNewAccount()
        }
    }

    LaunchedEffect(key1 = uiState.isLoading, key2 = uiState.errorMessage) {
        // If save was successful (isLoading is false and no error message specifically from save)
        // and we are no longer in edit mode or creating a new account.
        // This logic might need refinement based on how you want navigation to behave post-save.
        if (!uiState.isLoading && uiState.errorMessage == null && (uiState.accountToEdit != null || accountId == null)) {
            // Check if the account name is empty, implying a successful reset/save for a new item, or successful update.
            // A more robust check might be a dedicated "saveSuccess" flag in UiState.
            if (uiState.accountName.isBlank() && !uiState.isEditMode && accountId == null) { // After successful new account save
                // navController.popBackStack() // Navigate back after successful save
            } else if (uiState.isEditMode && uiState.accountToEdit != null && uiState.accountName == uiState.accountToEdit?.name) {
                // Potentially navigate back after successful edit if fields match (or use a success flag)
            }
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
                        // Consider navigating back only if save is successful.
                        // You might observe a success state from the ViewModel.
                        // For now, it will attempt to save, and error messages will show.
                        // If successful, and ViewModel clears fields, it feels like a new entry.
                        // If save successful and it was an edit, it stays on screen.
                        // To navigate back on success:
                        // Observe a specific "saveSuccess" flag in your UiState.
                        // if (saveSuccessful) navController.popBackStack()

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
            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = { accountViewModel.onAccountNameChange(it) },
                label = { Text("Account Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("name", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = uiState.accountType,
                onValueChange = { accountViewModel.onAccountTypeChange(it) },
                label = { Text("Account Type (e.g., Bank, Cash)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("type", ignoreCase = true) == true
            )

            OutlinedTextField(
                value = uiState.initialBalance,
                onValueChange = { accountViewModel.onInitialBalanceChange(it) },
                label = { Text("Initial Balance") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("balance", ignoreCase = true) == true
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